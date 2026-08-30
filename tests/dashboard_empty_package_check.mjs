import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');
const dashboard = read('vite-frontend/src/pages/dashboard.tsx');
const userService = read('springboot-backend/src/main/java/com/admin/service/impl/UserServiceImpl.java');

assert.match(dashboard, /const emptyUserInfo: UserInfo = \{[\s\S]*?flow: 0,[\s\S]*?num: 0,/);
assert.match(dashboard, /const normalizePackageInfo = \(data\?: PackageInfoData \| null\) => \(\{/);
assert.match(dashboard, /userInfo: normalizeUserInfo\(data\?\.userInfo\)/);
assert.match(dashboard, /tunnelPermissions: asArray\(data\?\.tunnelPermissions\)/);
assert.match(dashboard, /applyPackageData\(res\.data as PackageInfoData \| null\)/);
assert.doesNotMatch(dashboard, /else if \(res\.msg === '获取套餐信息失败'\)/);

const guideInitStart = dashboard.indexOf('useEffect(() => {');
const guideInitEnd = dashboard.indexOf('const applyPackageData');
assert.ok(guideInitStart >= 0 && guideInitEnd > guideInitStart, 'guide init block not found');
const guideInitBlock = dashboard.slice(guideInitStart, guideInitEnd);
assert.match(guideInitBlock, /localStorage\.removeItem\(getLegacyGuideSeenKey\(isAdminUser\)\)/);
assert.match(guideInitBlock, /const guideKey = getGuideClosedKey\(isAdminUser\)/);
assert.match(guideInitBlock, /setShowGuideCard\(localStorage\.getItem\(guideKey\) !== '1'\)/);
assert.doesNotMatch(guideInitBlock, /localStorage\.setItem\(guideKey, '1'\)/);
assert.match(dashboard, /const closeGuideCard = \(\) => \{[\s\S]*?localStorage\.setItem\(getGuideClosedKey\(isAdmin\), '1'\);[\s\S]*?setShowGuideCard\(false\);[\s\S]*?\};/);
assert.match(dashboard, /<GuideChecklist isAdmin=\{isAdmin\} onSkip=\{closeGuideCard\} \/>/);

assert.match(userService, /packageDto\.setTunnelPermissions\(listOrEmpty\(tunnelPermissions\)\)/);
assert.match(userService, /packageDto\.setForwards\(listOrEmpty\(forwards\)\)/);
assert.match(userService, /packageDto\.setStatisticsFlows\(listOrEmpty\(statisticsFlows\)\)/);
assert.match(userService, /userInfo\.setFlow\(valueOrZero\(user\.getFlow\(\)\)\)/);
assert.match(userService, /userInfo\.setNum\(valueOrZero\(user\.getNum\(\)\)\)/);
assert.match(userService, /new ArrayList<>\(listOrEmpty\(recentFlows\)\)/);

console.log('dashboard empty package and guide checks passed');
