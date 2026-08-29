import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');

const task = read('springboot-backend/src/main/java/com/admin/common/task/CheckGostConfigAsync.java');
const forwardService = read('springboot-backend/src/main/java/com/admin/service/impl/ForwardServiceImpl.java');
const forwardServiceIface = read('springboot-backend/src/main/java/com/admin/service/ForwardService.java');
const goLimiter = read('go-gost/x/socket/limiter.go');

// 1. cleanNodeConfigs must call limiter sync and missing-service sync after orphan cleanup
assert.match(task, /cleanOrphanedLimiters\(gostConfig, node\);[\s\S]*?syncLimiters\(gostConfig, node\);[\s\S]*?syncMissingServices\(gostConfig, node\);/,
  'cleanNodeConfigs should call syncLimiters then syncMissingServices');

// 2. syncLimiters must be public (invoked, not dead code)
assert.match(task, /public void syncLimiters\(GostConfigDto gostConfig, Node node\)/,
  'syncLimiters should be public');

// 3. missing-service sync: tunnels by in_node_id, forwards by tunnel, status filter, absence check, rebuild call
assert.match(task, /new QueryWrapper<Tunnel>\(\)\.eq\("in_node_id", node\.getId\(\)\)/,
  'syncMissingServices should query tunnels whose in_node_id is this node');
assert.match(task, /forward\.getStatus\(\) != 1/,
  'only active forwards should be rebuilt');
assert.match(task, /existingServices\.contains\(serviceName \+ "_tcp"\)/,
  'absence check should look for _tcp service');
assert.match(task, /existingServices\.contains\(serviceName \+ "_tls"\)/,
  'absence check should look for _tls service');
assert.match(task, /forwardService\.updateForwardA\(forward\)/,
  'missing services should be rebuilt via updateForwardA');

// 4. updateForwardA declared and implemented, with failure logging
assert.match(forwardServiceIface, /void updateForwardA\(Forward forward\);/,
  'ForwardService interface should declare updateForwardA');
assert.match(forwardService, /R result = updateGostServices\(forward, tunnel, limiter, nodeInfo, userTunnel\);/,
  'updateForwardA should check the rebuild result');
assert.match(forwardService, /log\.warn\(".{0,20}\u91cd\u5efa\u8f6c\u53d1/,
  'rebuild failure should log a warning');

// 5. node-side guarantee: UpdateLimiters re-registers (fallback target), panel falls back to AddService on not found
assert.match(goLimiter, /Unregister\(name\)[\s\S]*?Register\(name, v\)/,
  'node UpdateLimiter should unregister then register');
assert.match(forwardService, /GOST_NOT_FOUND_MSG\)\) \{[\s\S]{0,200}?AddService/,
  'updateMainService should fall back to AddService when not found');

console.log('node config sync checks passed');