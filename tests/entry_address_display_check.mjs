import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';

const frontendRequire = createRequire(new URL('../vite-frontend/package.json', import.meta.url));
const ts = frontendRequire('typescript');
const read = path => readFileSync(new URL('../' + path, import.meta.url), 'utf8');
const moduleUrl = source => {
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
  });
  return 'data:text/javascript;base64,' + Buffer.from(outputText).toString('base64');
};
const addressUrl = moduleUrl(read('vite-frontend/src/utils/address.ts'));
const { splitAddresses, formatAddressPort } = await import(addressUrl);

const readFunctions = (path, names) => {
  const source = ts.createSourceFile(path, read(path), ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  const found = new Map();
  const visit = node => {
    if (ts.isVariableDeclaration(node) && ts.isIdentifier(node.name) && names.includes(node.name.text)) {
      found.set(node.name.text, 'export const ' + node.name.text + ' = ' + node.initializer.getText(source) + ';');
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  return names.map(name => {
    assert.ok(found.has(name), path + ': missing ' + name);
    return found.get(name);
  }).join('\n');
};

const v4 = '203.0.113.10';
const v6 = '2001:db8:100:8f67:3718:683f:a767';
const mixed = ' ' + v4 + ',' + v6 + '\r\n' + v4 + '\nentry.example.com ';
assert.deepEqual(splitAddresses(mixed), [v4, v6, 'entry.example.com']);
assert.deepEqual(splitAddresses(null), []);
assert.deepEqual(splitAddresses(' ,\r\n '), []);
assert.equal(formatAddressPort(v6, 50000), '[' + v6 + ']:50000');
assert.equal(formatAddressPort('[' + v6 + ']', 50000), '[' + v6 + ']:50000');
assert.equal(formatAddressPort('entry.example.com', 50000), 'entry.example.com:50000');

const forward = await import(moduleUrl([
  'import { splitAddresses, formatAddressPort } from ' + JSON.stringify(addressUrl) + ';',
  'export const state = { addresses: [], title: null, open: false, copied: null };',
  'const setAddressList = value => { state.addresses = value; };',
  'const setAddressModalTitle = value => { state.title = value; };',
  'const setAddressModalOpen = value => { state.open = value; };',
  'const copyToClipboard = (text, label) => { state.copied = { text, label }; };',
  readFunctions('vite-frontend/src/pages/forward.tsx', ['formatInAddress', 'showAddressModal']),
].join('\n')));
assert.equal(forward.formatInAddress(mixed, 50000),
  v4 + ':50000\n[' + v6 + ']:50000\nentry.example.com:50000');
assert.equal(forward.formatInAddress('', 50000), '');
forward.showAddressModal(mixed, 50000, 'Entry');
assert.deepEqual(forward.state.addresses.map(item => item.address),
  [v4 + ':50000', '[' + v6 + ']:50000', 'entry.example.com:50000']);
assert.equal(forward.state.open, true);
forward.showAddressModal(v6, 50000, 'Entry');
assert.equal(forward.state.copied.text, '[' + v6 + ']:50000');

const tunnel = await import(moduleUrl([
  'import { splitAddresses } from ' + JSON.stringify(addressUrl) + ';',
  readFunctions('vite-frontend/src/pages/tunnel.tsx', ['getDisplayIp']),
].join('\n')));
assert.equal(tunnel.getDisplayIp(mixed), v4 + '\n' + v6 + '\nentry.example.com');
assert.equal(tunnel.getDisplayIp(v6), v6);
assert.equal(tunnel.getDisplayIp(undefined), '-');
console.log('Entry address display and copy regression passed');
