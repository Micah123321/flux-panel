import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');

const aggregatePage = read('vite-frontend/src/pages/aggregate-forward.tsx');
const tunnelPage = read('vite-frontend/src/pages/tunnel.tsx');
const forwardPage = read('vite-frontend/src/pages/forward.tsx');
const groupService = read('springboot-backend/src/main/java/com/admin/service/impl/AggregateNodeGroupServiceImpl.java');
const aggregateService = read('springboot-backend/src/main/java/com/admin/service/impl/AggregateForwardServiceImpl.java');
const tunnelDto = read('springboot-backend/src/main/java/com/admin/common/dto/TunnelDto.java');
const tunnelEntity = read('springboot-backend/src/main/java/com/admin/entity/Tunnel.java');
const forwardService = read('springboot-backend/src/main/java/com/admin/service/impl/ForwardServiceImpl.java');
const nodeSocket = read('go-gost/x/socket/websocket_reporter.go');
const panelInstall = read('panel_install.sh');
const gostSql = read('gost.sql');

assert.match(groupService, /item\.put\("portSta", node\.getPortSta\(\)\);/, '节点组接口未返回 portSta');
assert.match(groupService, /item\.put\("portEnd", node\.getPortEnd\(\)\);/, '节点组接口未返回 portEnd');
assert.match(groupService, /activeReferenceCount/, '节点组缺少隧道引用保护');
assert.match(groupService, /in_group_id/, '节点组删除保护未检查入口节点组隧道引用');
assert.match(groupService, /out_group_id/, '节点组删除保护未检查出口节点组隧道引用');

assert.doesNotMatch(aggregatePage, /新增聚合转发|转发规则|createAggregateForward|getAggregateForwards|MAX_PORT_SPAN/, '聚合页仍暴露独立聚合转发规则入口');
assert.match(aggregatePage, /新增节点组/, '聚合页应保留节点组创建入口');
assert.match(aggregatePage, /隧道管理可直接选择节点组/, '聚合页缺少节点组用途文案');

assert.match(tunnelPage, /getAggregateNodeGroups/, '隧道页未加载节点组');
assert.match(tunnelPage, /入口节点或节点组/, '隧道页入口选择未支持节点组');
assert.match(tunnelPage, /出口节点或节点组/, '隧道页出口选择未支持节点组');
assert.match(tunnelPage, /inGroupId/, '隧道页未提交 inGroupId');
assert.match(tunnelPage, /outGroupId/, '隧道页未提交 outGroupId');
assert.match(tunnelPage, /label="负载策略"/, '隧道页缺少负载策略配置');
assert.match(tunnelPage, /strategy: 'round'/, '隧道页默认负载策略应为轮询');
assert.doesNotMatch(forwardPage, /label="负载策略"|主备模式 - 自上而下/, '转发页不应再配置负载策略');

assert.match(tunnelDto, /private Long inGroupId;/, 'TunnelDto 缺少 inGroupId');
assert.match(tunnelDto, /private Long outGroupId;/, 'TunnelDto 缺少 outGroupId');
assert.match(tunnelDto, /private String strategy;/, 'TunnelDto 缺少 strategy');
assert.match(tunnelEntity, /private Long inGroupId;/, 'Tunnel 实体缺少 inGroupId');
assert.match(tunnelEntity, /private Long outGroupId;/, 'Tunnel 实体缺少 outGroupId');
assert.match(tunnelEntity, /private String strategy;/, 'Tunnel 实体缺少 strategy');
assert.match(panelInstall, /ADD COLUMN \\`strategy\\` VARCHAR\(100\).*DEFAULT "round"/, '安装脚本缺少 tunnel.strategy 轮询默认迁移');
assert.match(panelInstall, /t\.\\`in_group_id\\` IS NOT NULL OR t\.\\`out_group_id\\` IS NOT NULL/, '安装脚本未将节点组隧道迁移为隧道级策略');
assert.match(gostSql, /`strategy` varchar\(100\) NOT NULL DEFAULT 'round'/, '初始化 SQL 中 tunnel.strategy 默认值应为 round');

assert.match(aggregateService, /DEPRECATED_FORWARD_MSG/, '聚合转发服务缺少下线提示');
assert.match(aggregateService, /return R\.err\(DEPRECATED_FORWARD_MSG\);/, '聚合转发创建/更新未下线');
assert.doesNotMatch(aggregateService, /MAX_PORT_SPAN = 10001/, '后端仍允许 10001 端口聚合规则');
assert.match(aggregateService, /DeleteServices/, 'legacy 聚合转发删除未使用批量删除');
assert.match(nodeSocket, /case \"DeleteServices\":/, '节点端未路由 DeleteServices 批量删除命令');

const syncTask = read('springboot-backend/src/main/java/com/admin/common/task/CheckGostConfigAsync.java');
assert.match(syncTask, /isLegacyAggregateService/, '配置上报清理未识别 agf_ 历史服务');
assert.match(syncTask, /cleanLegacyAggregateService/, '配置上报清理未删除 agf_ 孤立服务');

assert.match(forwardService, /resolveTunnelNodes\(tunnel\.getInGroupId\(\), tunnel\.getInNodeId\(\)\)/, '普通转发入口未展开节点组');
assert.match(forwardService, /resolveTunnelNodes\(tunnel\.getOutGroupId\(\), tunnel\.getOutNodeId\(\)\)/, '普通转发出口未展开节点组');
assert.match(forwardService, /buildNodeRemoteAddr\(nodeInfo\.getOutNodes\(\), forward\.getOutPort\(\)\)/, '隧道转发 chain 未使用出口节点组');
assert.match(forwardService, /String strategy = tunnelStrategy\(tunnel\);/, 'GOST 下发策略应来自隧道');
assert.doesNotMatch(forwardService, /forward\.getStrategy\(\)/, 'GOST 下发不应再读取转发策略');

console.log('aggregate-forward corrected model check passed');
