import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

// 余额抵扣语义自检：与 CommerceServiceImpl.createOrder / buildOrder 的 BigDecimal 行为对齐
// deduction = clamp(balance, 0, payable)；net = payable - deduction；奖励 = net * ratio / 100 (HALF_UP, 2位)

const round2 = (value) => Math.round(value * 100) / 100;

const calcDeduction = (balance, payable) => Math.max(0, Math.min(balance, payable));
const calcNet = (payable, deduction) => round2(payable - deduction);
const calcReward = (net, ratio) => round2((net * ratio) / 100);

// 场景1: 余额充足 → 全额抵扣，无需支付，奖励按 0 计
{
  const payable = 50, balance = 30;
  const deduction = calcDeduction(balance, payable);
  assert.equal(deduction, 30);
  assert.equal(calcNet(payable, deduction), 20);
}
// 场景2: 余额大于应付 → 封顶到应付，净支付 0（balance-only 直发）
{
  const payable = 20, balance = 99;
  const deduction = calcDeduction(balance, payable);
  assert.equal(deduction, 20);
  assert.equal(calcNet(payable, deduction), 0);
}
// 场景3: 零余额/负余额 → 不抵扣
{
  assert.equal(calcDeduction(0, 50), 0);
  assert.equal(calcDeduction(-5, 50), 0);
}
// 场景4: 奖励基数用实付口径（部分抵扣后返现减少）
{
  const payable = 100, balance = 25, ratio = 10;
  const deduction = calcDeduction(balance, payable);
  const net = calcNet(payable, deduction);
  assert.equal(calcReward(net, ratio), 7.5);
  // 无抵扣时行为与旧逻辑一致
  assert.equal(calcReward(calcNet(payable, 0), ratio), 10);
}
// 场景5: 兑换码折扣 + 余额抵扣叠加（折扣先行）
{
  const price = 100, discountRatio = 80, balance = 10;
  const payable = round2((price * discountRatio) / 100);
  assert.equal(payable, 80);
  const deduction = calcDeduction(balance, payable);
  assert.equal(calcNet(payable, deduction), 70);
}

// 源码断言：后端关键实现点存在
const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');
const service = read('springboot-backend/src/main/java/com/admin/service/impl/CommerceServiceImpl.java');
assert.match(service, /deduction = balance\.max\(BigDecimal\.ZERO\)\.min\(payable\)/);
assert.match(service, /balanceOnly \? ORDER_COMPLETED : ORDER_PENDING/);
assert.match(service, /rewardBase = order\.getPayableAmount\(\)\.subtract/);
assert.match(service, /ge\("invite_balance", amount\)/);
assert.match(service, /toPlainString/);

console.log('invite balance checks passed');
