import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createServer } from 'node:http';
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const bash = process.argv[2] || 'bash';
const payload = Buffer.from('node-download-fixture-'.repeat(16384));
const partialSize = Math.floor(payload.length * 0.74);
const requests = [];
const counts = new Map();
const server = createServer((request, response) => {
  const path = new URL(request.url, 'http://fixture').pathname;
  const range = request.headers.range;
  requests.push({ path, range });
  const count = (counts.get(path) || 0) + 1;
  counts.set(path, count);
  response.setHeader('Connection', 'close');
  if (['/resume', '/no-range', '/range-416', '/proxy'].includes(path) && count === 1) {
    response.writeHead(200, { 'Content-Length': payload.length });
    response.write(payload.subarray(0, partialSize), () => {
      setTimeout(() => response.destroy(), 10);
    });
    return;
  }
  if (path === '/proxy' || path === '/fail') {
    response.writeHead(503);
    response.end('fixture unavailable');
    return;
  }
  if (path === '/empty') {
    response.writeHead(200, { 'Content-Length': 0 });
    response.end();
    return;
  }
  if (path === '/range-416' && range) {
    response.writeHead(416, { 'Content-Range': 'bytes */' + payload.length });
    response.end();
    return;
  }
  if (range && path !== '/no-range') {
    const offset = Number(range.match(/^bytes=(\d+)-$/)?.[1]);
    if (!Number.isInteger(offset) || offset >= payload.length) {
      response.writeHead(416);
      response.end();
      return;
    }
    response.writeHead(206, {
      'Content-Length': payload.length - offset,
      'Content-Range': 'bytes ' + offset + '-' + (payload.length - 1) + '/' + payload.length,
    });
    response.end(payload.subarray(offset));
    return;
  }
  response.writeHead(200, { 'Content-Length': payload.length });
  response.end(payload);
});

await mkdir(join(root, '.cache'), { recursive: true });
const directory = await mkdtemp(join(root, '.cache', 'node-download-'));
await new Promise(resolveListen => server.listen(0, '127.0.0.1', resolveListen));
const baseURL = 'http://127.0.0.1:' + server.address().port;

async function download(path, filename, primary = '') {
  const program = [
    'source "$1"',
    'sleep() { :; }',
    'DOMESTIC_MODE=false',
    'if [[ -n "$4" ]]; then',
    '  fixture_primary="$4"',
    '  resolve_download_url() { echo "$fixture_primary"; }',
    'fi',
    'download_file "$2" "$3"',
  ].join('\n');
  const child = spawn(bash, ['-c', program, 'node-download-test', join(root, 'install.sh'), baseURL + path, filename, primary], {
    cwd: root,
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  let output = '';
  child.stdout.on('data', data => { output += data; });
  child.stderr.on('data', data => { output += data; });
  const code = await new Promise((resolveExit, reject) => {
    child.on('error', reject);
    child.on('exit', resolveExit);
  });
  return { code, output };
}

try {
  for (const path of ['/resume', '/no-range', '/range-416']) {
    const destination = join(directory, path.slice(1));
    const result = await download(path, destination);
    assert.equal(result.code, 0, path + ' did not recover:\n' + result.output);
    const downloaded = await readFile(destination);
    assert.ok(downloaded.equals(payload), path + ' produced corrupt bytes (' + downloaded.length + '/' + payload.length + '):\n' + result.output);
    const calls = requests.filter(request => request.path === path);
    assert.equal(calls[0].range, undefined);
    assert.equal(calls[1].range, 'bytes=' + partialSize + '-', path + ' did not resume the partial file');
    if (path !== '/resume') {
      assert.equal(calls[2].range, undefined, path + ' did not restart after Range rejection');
    }
  }

  const fallbackTarget = join(directory, 'fallback');
  const fallback = await download('/direct', fallbackTarget, baseURL + '/proxy');
  assert.equal(fallback.code, 0, 'source fallback failed:\n' + fallback.output);
  assert.ok((await readFile(fallbackTarget)).equals(payload), 'source fallback produced corrupt bytes');
  assert.equal(counts.get('/proxy'), 4, 'primary source retry bound changed');
  const direct = requests.find(request => request.path === '/direct');
  assert.ok(direct, 'official source fallback was not attempted');
  assert.equal(direct.range, undefined, 'partial bytes from another source were reused');

  for (const path of ['/fail', '/empty']) {
    const destination = join(directory, path.slice(1));
    await writeFile(destination, 'working-node');
    const result = await download(path, destination);
    assert.notEqual(result.code, 0, path + ' incorrectly succeeded');
    assert.equal(await readFile(destination, 'utf8'), 'working-node', path + ' replaced an existing file');
    assert.equal(counts.get(path), 4, path + ' did not stop at the retry bound');
    await assert.rejects(readFile(destination + '.part'), { code: 'ENOENT' });
  }
  console.log('real curl HTTP tests passed: truncated transfer resume, Range 200/416 restart, source fallback, bounded failures, atomic replacement');
} finally {
  await new Promise(resolveClose => server.close(resolveClose));
  await rm(directory, { recursive: true, force: true });
}
