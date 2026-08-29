import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');

// 每日流量限制全链路静态断言：套餐设置 -> 用户生效 -> 网关执行 -> 每日重置

const schema = read('gost.sql');
const packagePlan = read('springboot-backend/src/main/java/com/admin/entity/PackagePlan.java');
const userEntity = read('springboot-backend/src/main/java/com/admin/entity/User.java');
const userTunnelEntity = read('springboot-backend/src/main/java/com/admin/entity/UserTunnel.java');
const commerceDto = read('springboot-backend/src/main/java/com/admin/common/dto/CommerceDto.java');
const commerceService = read('springboot-backend/src/main/java/com/admin/service/impl/CommerceServiceImpl.java');
const userService = read('springboot-backend/src/main/java/com/admin/service/impl/UserServiceImpl.java');
const flowController = read('springboot-backend/src/main/java/com/admin/controller/FlowController.java');
const resetTask = read('springboot-backend/src/main/java/com/admin/common/task/ResetFlowAsync.java');
const planTypes = read('vite-frontend/src/types/index.ts');
const adminPage = read('vite-frontend/src/pages/commerce-admin.tsx');
const commercePage = read('vite-frontend/src/pages/commerce.tsx');
const dashboard = read('vite-frontend/src/pages/dashboard.tsx');

// 1. 数据库层：package_plan / user / user_tunnel 三表均有日限与日计数字段
assert.match(schema, /CREATE TABLE[\s\S]*?`package_plan`[\s\S]*?`daily_flow`/, 'package_plan 缺少 daily_flow');
assert.equal(schema.match(/`daily_flow` bigint\(20\) NOT NULL DEFAULT '0'/g)?.length, 3, 'daily_flow 应出现在 package_plan/user/user_tunnel 三处');
assert.equal(schema.match(/`daily_in_flow`/g)?.length, 2, 'daily_in_flow 应出现在 user/user_tunnel 两处');
assert.equal(schema.match(/`daily_out_flow`/g)?.length, 2, 'daily_out_flow 应出现在 user/user_tunnel 两处');

// 2. 实体层
assert.match(packagePlan, /private Long dailyFlow;/, 'PackagePlan 缺少 dailyFlow');
for (const field of ['dailyFlow', 'dailyInFlow', 'dailyOutFlow']) {
  assert.match(userEntity, new RegExp(`private Long ${field};`), `User 缺少 ${field}`);
  assert.match(userTunnelEntity, new RegExp(`private Long ${field};`), `UserTunnel 缺少 ${field}`);
}

// 3. DTO 层：套餐请求校验 + 用户套餐信息透出
assert.match(commerceDto, /private Long dailyFlow;/, 'PackagePlanRequest 缺少 dailyFlow');
assert.match(commerceDto, /@Min\(value = 0, message = "每日流量限制不能小于0"\)/, 'dailyFlow 缺少校验');
for (const field of ['dailyFlow', 'dailyInFlow', 'dailyOutFlow']) {
  assert.match(userService, new RegExp(`userInfo.set${field.charAt(0).toUpperCase()}${field.slice(1)}\\(user\\.get${field.charAt(0).toUpperCase()}${field.slice(1)}\\(\\)\\);`), `buildUserInfoDto 未透出 ${field}`);
}

// 4. 套餐发放链路：applyPackage 与 syncUserGroupTunnels 写入日限并清零日计数
assert.match(commerceService, /user\.setDailyFlow\(plan\.getDailyFlow\(\)\);/, 'applyPackage 未写入 dailyFlow');
assert.match(commerceService, /userTunnel\.setDailyFlow\(plan\.getDailyFlow\(\)\);/, 'syncUserGroupTunnels 未写入 dailyFlow');
assert.match(commerceService, /user\.setDailyInFlow\(0L\);/, 'applyPackage 未清零 dailyInFlow');

// 5. 网关执行链路：上报时累加日计数（三处）+ 用户/隧道日限检查（0=不限制）
assert.equal(flowController.match(/daily_in_flow = daily_in_flow \+ /g)?.length, 3, 'FlowController 日计数累加应有 3 处');
assert.equal(flowController.match(/daily_out_flow = daily_out_flow \+ /g)?.length, 3, 'FlowController 日计数累加应有 3 处');
assert.match(flowController, /updatedUser\.getDailyFlow\(\) != null && updatedUser\.getDailyFlow\(\) > 0/, '用户日限检查缺失');
assert.match(flowController, /userTunnel\.getDailyFlow\(\) != null && userTunnel\.getDailyFlow\(\) > 0/, '隧道日限检查缺失');

// 6. 每日重置链路：0 点清零 + 候选集捕获 + 恢复前全条件校验
assert.match(resetTask, /resetDailyFlow\(\);/, '缺少每日清零调用');
assert.match(resetTask, /findDailyOverLimitForwards\(\)/, '缺少恢复候选集捕获');
assert.match(resetTask, /resumeDailyLimitedForwards\(dailyResumeCandidates\)/, '恢复未使用候选集');
assert.match(resetTask, /if \(!isForwardAllowed\(user, userTunnel\)\) continue;/, '恢复前未校验全条件');
assert.match(resetTask, /daily_in_flow = 0, daily_out_flow = 0/, '缺少日计数清零 SQL');

// 7. 前端：类型 + 管理表单 + 购买页 + 用户侧展示
assert.match(planTypes, /dailyFlow: number;/, 'PackagePlan 类型缺少 dailyFlow');
assert.match(adminPage, /每日流量限制（GiB，0为不限制）/, '管理表单缺少每日流量输入');
assert.match(commercePage, /日限 \$\{plan\.dailyFlow\} GiB|不限日流量/, '购买页缺少日限展示');
assert.match(dashboard, /今日流量/, '用户侧缺少今日流量展示');

console.log('daily-flow-limit check: all assertions passed');

