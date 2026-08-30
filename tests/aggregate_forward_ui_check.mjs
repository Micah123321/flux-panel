import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');

const page = read('vite-frontend/src/pages/aggregate-forward.tsx');
const groupService = read('springboot-backend/src/main/java/com/admin/service/impl/AggregateNodeGroupServiceImpl.java');
const aggregateService = read('springboot-backend/src/main/java/com/admin/service/impl/AggregateForwardServiceImpl.java');

assert.match(groupService, /item\.put\("portSta", node\.getPortSta\(\)\);/, '节点组接口未返回 portSta');
assert.match(groupService, /item\.put\("portEnd", node\.getPortEnd\(\)\);/, '节点组接口未返回 portEnd');
assert.match(page, /buildDefaultForwardForm/, '新增聚合转发表单缺少默认值构造');
assert.match(page, /entryGroupId: entryGroup\?\.id \?\? null/, '新增聚合转发未默认选择入口节点组');
assert.match(page, /exitGroupId: exitGroup\?\.id \?\? null/, '新增聚合转发未默认选择出口节点组');
assert.match(page, /entryAddresses: groupAddresses\(entryNodes\)\.join\("\\n"\)/, '新增聚合转发未默认带入入口地址');
assert.match(page, /entryPortStart: recommended\?\.entry\.start \?\? null/, '新增聚合转发未默认带入推荐入口起始端口');
assert.match(page, /entryPortEnd: recommended\?\.entry\.end \?\? null/, '新增聚合转发未默认带入推荐入口结束端口');
assert.match(page, /targetPortStart: recommended\?\.target\.start \?\? null/, '新增聚合转发未默认带入推荐出口起始端口');
assert.match(page, /targetPortEnd: recommended\?\.target\.end \?\? null/, '新增聚合转发未默认带入推荐出口结束端口');
assert.match(page, /GroupPreview/, '聚合转发表单缺少节点组预览');
assert.match(page, /entryAddressOptions/, '聚合转发表单缺少入口地址候选');
assert.match(page, /fillRecommendedPort/, '聚合转发表单缺少推荐端口操作');
assert.match(page, /getRecommendedPortRanges/, '聚合转发表单缺少推荐端口范围计算');
assert.match(page, /recommendedPortRanges \? `\$\{portRangeText\(recommendedPortRanges\.entry\)\} · \$\{recommendedPortRanges\.span\} 个` : "-"/, '推荐端口按钮未展示推荐数量');
assert.match(page, /const MAX_PORT_SPAN = 10001;/, '前端聚合转发端口上限未覆盖默认 50000-60000 范围');
assert.match(aggregateService, /MAX_PORT_SPAN = 10001;/, '后端聚合转发端口上限未同步覆盖默认 50000-60000 范围');
assert.match(page, /syncTargetPorts/, '聚合转发表单缺少出口端口同步');
assert.match(page, /modeOptions/, '聚合转发表单缺少模式选项定义');
assert.match(page, /textValue=\{option\.label\}/, '模式选项缺少可读文本');

console.log('aggregate-forward-ui check passed');
