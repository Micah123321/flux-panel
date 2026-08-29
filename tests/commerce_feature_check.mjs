import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');

const commerceService = read('springboot-backend/src/main/java/com/admin/service/impl/CommerceServiceImpl.java');
const adminController = read('springboot-backend/src/main/java/com/admin/controller/CommerceAdminController.java');
const userController = read('springboot-backend/src/main/java/com/admin/controller/CommerceController.java');
const userEntity = read('springboot-backend/src/main/java/com/admin/entity/User.java');
const webConfig = read('springboot-backend/src/main/java/com/admin/config/WebMvcConfig.java');
const schema = read('gost.sql');
const installer = read('panel_install.sh');
const api = read('vite-frontend/src/api/index.ts');
const app = read('vite-frontend/src/App.tsx');

for (const table of ['package_plan', 'device_group', 'user_group', 'order_record', 'redeem_code', 'invite_record', 'invite_reward_record']) {
  assert.ok(schema.includes('CREATE TABLE `' + table + '`'), `missing ${table} in gost.sql`);
  assert.ok(installer.includes('CREATE TABLE IF NOT EXISTS \\`' + table + '\\`'), `missing ${table} in panel_install.sh`);
}

for (const field of ['packagePlanId', 'userGroupId', 'speedMbps', 'ipLimit', 'connectionLimit', 'inviteCode', 'inviterUserId', 'inviteBalance']) {
  assert.match(userEntity, new RegExp(`private .* ${field};`));
}

assert.match(webConfig, /excludePathPatterns\("\/api\/v1\/commerce\/register"\)/);
assert.match(adminController, /@RequestMapping\("\/api\/v1\/admin\/commerce"\)/);
assert.match(userController, /@RequestMapping\("\/api\/v1\/commerce"\)/);
assert.match(commerceService, /@Transactional\(rollbackFor = Exception\.class\)/);
assert.match(commerceService, /createInviteReward\(order, buyer\)/);
assert.match(commerceService, /validatePlanActive\(plan\)/);
assert.match(api, /adminCreatePackagePlan/);
assert.match(api, /registerUser/);
assert.match(app, /path="\/shop"/);
assert.match(app, /path="\/commerce-admin"/);
assert.match(app, /path="\/register"/);

console.log('commerce feature checks passed');
