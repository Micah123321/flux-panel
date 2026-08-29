import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');

const commerceService = read('springboot-backend/src/main/java/com/admin/service/impl/CommerceServiceImpl.java');
const adminController = read('springboot-backend/src/main/java/com/admin/controller/CommerceAdminController.java');
const userController = read('springboot-backend/src/main/java/com/admin/controller/CommerceController.java');
const userEntity = read('springboot-backend/src/main/java/com/admin/entity/User.java');
const orderEntity = read('springboot-backend/src/main/java/com/admin/entity/OrderRecord.java');
const paymentService = read('springboot-backend/src/main/java/com/admin/service/impl/PaymentServiceImpl.java');
const paymentController = read('springboot-backend/src/main/java/com/admin/controller/PaymentController.java');
const webConfig = read('springboot-backend/src/main/java/com/admin/config/WebMvcConfig.java');
const schema = read('gost.sql');
const installer = read('panel_install.sh');
const api = read('vite-frontend/src/api/index.ts');
const app = read('vite-frontend/src/App.tsx');
const commercePage = read('vite-frontend/src/pages/commerce.tsx');
const commerceAdminPage = read('vite-frontend/src/pages/commerce-admin.tsx');

for (const table of ['package_plan', 'device_group', 'user_group', 'order_record', 'redeem_code', 'invite_record', 'invite_reward_record', 'payment_config']) {
  assert.ok(new RegExp('CREATE TABLE [^\n]*' + table).test(schema), `missing ${table} in gost.sql`);
  assert.ok(new RegExp('CREATE TABLE IF NOT EXISTS [^\n]*' + table).test(installer), `missing ${table} in panel_install.sh`);
}

for (const field of ['packagePlanId', 'userGroupId', 'speedMbps', 'ipLimit', 'connectionLimit', 'inviteCode', 'inviterUserId', 'inviteBalance']) {
  assert.match(userEntity, new RegExp(`private .* ${field};`));
}

for (const field of ['paymentChannel', 'providerTradeNo', 'paymentUrl', 'paidAmount']) {
  assert.match(orderEntity, new RegExp(`private .* ${field};`));
}

assert.match(webConfig, /excludePathPatterns\("\/api\/v1\/commerce\/register"\)/);
assert.match(webConfig, /excludePathPatterns\("\/api\/v1\/payment\/notify\/\*\*"\)/);
assert.match(adminController, /@RequestMapping\("\/api\/v1\/admin\/commerce"\)/);
assert.match(userController, /@RequestMapping\("\/api\/v1\/commerce"\)/);
assert.match(paymentController, /@RequestMapping\("\/api\/v1\/payment"\)/);
assert.match(paymentController, /completePaidOrder\(notifyResult\)/);
assert.match(commerceService, /@Transactional\(rollbackFor = Exception\.class\)/);
assert.match(commerceService, /createInviteReward\(order, buyer\)/);
assert.match(commerceService, /validatePlanActive\(plan\)/);
assert.match(commerceService, /paymentService\.createPayment\(order\)/);
assert.match(commerceService, /completePaidOrder\(PaymentNotifyResult notifyResult\)/);

for (const channel of ['CHANNEL_EASYPAY', 'CHANNEL_ALIPAY', 'CHANNEL_WECHAT', 'CHANNEL_STRIPE']) {
  assert.match(paymentService, new RegExp(channel));
}
assert.match(paymentService, /rsaSha256Verify/);
assert.match(paymentService, /hmacSha256Hex/);
assert.match(paymentService, /aesGcmDecrypt/);
assert.match(paymentService, /trade_state/);

assert.match(api, /adminCreatePackagePlan/);
assert.match(api, /registerUser/);
assert.match(api, /getPaymentConfigs/);
assert.match(api, /adminUpdatePaymentConfig/);
assert.match(commercePage, /paymentChannel: selectedPaymentChannel/);
assert.match(commerceAdminPage, /支付方式/);
assert.doesNotMatch(api, /Network\.post\("\/commerce\/order\/complete"/);
assert.doesNotMatch(userController, /@PostMapping\("\/order\/complete"\)/);
assert.doesNotMatch(commercePage, /完成支付/);
assert.match(app, /path="\/shop"/);
assert.match(app, /path="\/commerce-admin"/);
assert.match(app, /path="\/register"/);

console.log('commerce feature checks passed');
