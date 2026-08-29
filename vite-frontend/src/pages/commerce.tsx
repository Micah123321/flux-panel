import { Button } from "@heroui/button";
import { Card, CardBody, CardHeader } from "@heroui/card";
import { Chip } from "@heroui/chip";
import { Modal, ModalBody, ModalContent, ModalFooter, ModalHeader } from "@heroui/modal";
import { Input } from "@heroui/input";
import { Spinner } from "@heroui/spinner";
import { Switch } from "@heroui/switch";
import { useCallback, useEffect, useRef, useState } from "react";
import toast from "react-hot-toast";

import { createOrder, getInviteInfo, getMyInviteRecords, getMyOrders, getPackagePlans, getPaymentConfigs, redeemCode } from "@/api";
import { InviteInfo, InviteRecordsData, OrderRecord, PackagePlan, PaymentConfig } from "@/types";
import { runTour } from "@/utils/tour";

type TabKey = "plans" | "orders" | "redeem" | "invite";

const tabs: { key: TabKey; label: string }[] = [
  { key: "plans", label: "套餐" },
  { key: "orders", label: "订单" },
  { key: "redeem", label: "兑换" },
  { key: "invite", label: "邀请" },
];

const SHOP_TOUR_KEY = "shop_tour_v1";
const POLL_INTERVAL_MS = 3000;
const POLL_MAX_MS = 5 * 60 * 1000;

const money = (value?: number) => `￥${Number(value || 0).toFixed(2)}`;
const timeText = (value?: number | null) => (value ? new Date(value).toLocaleString("zh-CN") : "-");
const orderStatusText = (status: number) => (status === 1 ? "已完成" : "待支付");

interface PayingOrder {
  order: OrderRecord;
  openedAt: number;
}

export default function CommercePage() {
  const [activeTab, setActiveTab] = useState<TabKey>("plans");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [orderSaving, setOrderSaving] = useState<number | null>(null);
  const [plans, setPlans] = useState<PackagePlan[]>([]);
  const [orders, setOrders] = useState<OrderRecord[]>([]);
  const [paymentConfigs, setPaymentConfigs] = useState<PaymentConfig[]>([]);
  const [selectedPaymentChannel, setSelectedPaymentChannel] = useState("");
  const [inviteInfo, setInviteInfo] = useState<InviteInfo | null>(null);
  const [inviteRecords, setInviteRecords] = useState<InviteRecordsData>({ invites: [], rewards: [] });
  const [redeemInput, setRedeemInput] = useState("");
  const [useBalance, setUseBalance] = useState<Record<number, boolean>>({});
  const [payingOrder, setPayingOrder] = useState<PayingOrder | null>(null);
  const pollTimer = useRef<ReturnType<typeof setInterval> | null>(null);
  const pollDeadline = useRef<number>(0);

  const balance = Number(inviteInfo?.inviteBalance || 0);

  const stopPolling = useCallback(() => {
    if (pollTimer.current) {
      clearInterval(pollTimer.current);
      pollTimer.current = null;
    }
  }, []);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [planRes, orderRes, paymentRes, inviteInfoRes, inviteRecordsRes] = await Promise.all([
        getPackagePlans(),
        getMyOrders(),
        getPaymentConfigs(),
        getInviteInfo(),
        getMyInviteRecords(),
      ]);
      if (planRes.code === 0) setPlans((planRes.data || []) as PackagePlan[]);
      if (orderRes.code === 0) setOrders((orderRes.data || []) as OrderRecord[]);
      if (paymentRes.code === 0) {
        const configs = (paymentRes.data || []) as PaymentConfig[];
        setPaymentConfigs(configs);
        setSelectedPaymentChannel((current) => configs.some((item) => item.channel === current) ? current : configs[0]?.channel || "");
      }
      if (inviteInfoRes.code === 0 && inviteInfoRes.data) setInviteInfo(inviteInfoRes.data as InviteInfo);
      if (inviteRecordsRes.code === 0 && inviteRecordsRes.data) setInviteRecords(inviteRecordsRes.data as InviteRecordsData);
    } catch (error) {
      toast.error("加载套餐中心失败");
      console.error(error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
    runTour(SHOP_TOUR_KEY, [
      { element: "#shop-plans-anchor", popover: { title: "选择套餐", description: "这里展示所有可购买的套餐，支持兑换码与邀请余额抵扣。" } },
      { element: "#shop-orders-tab-anchor", popover: { title: "订单查询", description: "下单后在这里查看支付状态；付款到账后套餐自动开通。" } },
      { element: "#shop-invite-tab-anchor", popover: { title: "邀请返现", description: "生成邀请链接，好友每次消费你都能拿到返现。" } },
    ]);
    return () => stopPolling();
  }, [loadData, stopPolling]);

  // 支付模态框打开后轮询订单状态，异步回调到账自动完成
  useEffect(() => {
    if (!payingOrder || payingOrder.order.status !== 0) {
      stopPolling();
      return;
    }
    pollDeadline.current = payingOrder.openedAt + POLL_MAX_MS;
    pollTimer.current = setInterval(async () => {
      if (Date.now() > pollDeadline.current) {
        stopPolling();
        return;
      }
      try {
        const res = await getMyOrders();
        if (res.code !== 0) return;
        const list = (res.data || []) as OrderRecord[];
        const matched = list.find((item) => item.id === payingOrder.order.id);
        if (matched && matched.status === 1) {
          stopPolling();
          setPayingOrder((current) => (current ? { ...current, order: matched } : current));
          toast.success("支付成功，套餐已到账");
          await loadData();
        }
      } catch {
        // 轮询失败忽略，下一轮重试
      }
    }, POLL_INTERVAL_MS);
    return stopPolling;
  }, [payingOrder, stopPolling, loadData]);

  const buildPayTarget = (plan: PackagePlan) => {
    const payable = Number(plan.price || 0);
    const deduction = useBalance[plan.id] ? Math.min(balance, payable) : 0;
    return { payable, deduction, payableAfter: Math.max(0, payable - deduction) };
  };

  const createPlanOrder = async (plan: PackagePlan) => {
    if (!selectedPaymentChannel) return toast.error("请选择支付方式");
    setOrderSaving(plan.id);
    try {
      const target = buildPayTarget(plan);
      const res = await createOrder({
        packagePlanId: plan.id,
        redeemCode: undefined,
        paymentChannel: selectedPaymentChannel,
        useInviteBalance: useBalance[plan.id] || undefined,
      });
      if (res.code !== 0) return toast.error(res.msg || "创建订单失败");
      const order = res.data as OrderRecord;
      toast.success(target.payableAfter <= 0 ? "余额抵扣完成，套餐已到账" : "订单已创建，请在支付页完成付款");
      await loadData();
      if (target.payableAfter <= 0) {
        setActiveTab("orders");
      } else if (order.paymentUrl) {
        window.open(order.paymentUrl, "_blank", "noopener,noreferrer");
        setPayingOrder({ order, openedAt: Date.now() });
      } else {
        setPayingOrder({ order, openedAt: Date.now() });
      }
    } finally {
      setOrderSaving(null);
    }
  };

  const redeem = async () => {
    if (!redeemInput.trim()) return toast.error("请输入兑换码");
    setSaving(true);
    try {
      const res = await redeemCode(redeemInput.trim());
      if (res.code !== 0) return toast.error(res.msg || "兑换失败");
      toast.success("兑换成功，套餐已发放");
      setRedeemInput("");
      await loadData();
    } finally {
      setSaving(false);
    }
  };

  const inviteLink = inviteInfo?.inviteCode ? `${window.location.origin}/register?invite=${inviteInfo.inviteCode}` : "";

  const copyInviteLink = async () => {
    if (!inviteLink) return;
    await navigator.clipboard.writeText(inviteLink);
    toast.success("邀请链接已复制");
  };

  const renderPlans = () => (
    <div id="shop-plans-anchor" className="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 gap-4">
      {plans.map((plan) => {
        const target = buildPayTarget(plan);
        const balanceUsable = balance > 0 && useBalance[plan.id];
        return (
          <Card key={plan.id} className="border border-gray-200 dark:border-default-200 shadow-sm">
            <CardBody className="space-y-4">
              <div className="flex items-start justify-between gap-3"><div><h2 className="text-lg font-semibold">{plan.name}</h2><p className="text-sm text-default-500">{plan.description || "-"}</p></div><Chip color="primary" variant="flat">{money(plan.price)}</Chip></div>
              <div className="grid grid-cols-2 gap-2 text-sm"><span>{plan.durationMultiplier}个月</span><span>{plan.flow} GiB</span><span>{plan.dailyFlow ? `日限 ${plan.dailyFlow} GiB` : "不限日流量"}</span><span>{plan.maxRules} 条规则</span><span>{plan.speedMbps || 0} Mbps</span><span>IP {plan.ipLimit || 0}</span><span>连接 {plan.connectionLimit || 0}</span></div>
              {balance > 0 && (
                <Switch size="sm" isSelected={!!useBalance[plan.id]} onValueChange={(value) => setUseBalance({ ...useBalance, [plan.id]: value })}>
                  <span className="text-sm text-default-600">邀请余额抵扣（可用 {money(balance)}）</span>
                </Switch>
              )}
              {balanceUsable && target.deduction > 0 && (
                <p className="text-xs text-success">已抵扣 {money(target.deduction)}，还需支付 {money(target.payableAfter)}</p>
              )}
              <label className="text-sm text-default-600">支付方式<select className="mt-1 w-full rounded-md border border-default-200 bg-transparent px-3 py-2" value={selectedPaymentChannel} onChange={(e) => setSelectedPaymentChannel(e.target.value)}><option value="">请选择</option>{paymentConfigs.map((item) => <option key={item.channel} value={item.channel}>{item.displayName}</option>)}</select></label>
              <Button color="primary" isDisabled={paymentConfigs.length === 0} isLoading={orderSaving === plan.id} onClick={() => createPlanOrder(plan)}>{balanceUsable && target.payableAfter <= 0 ? "余额兑换" : "立即购买"}</Button>
            </CardBody>
          </Card>
        );
      })}
      {plans.length === 0 && <Card className="border border-gray-200 dark:border-default-200 shadow-sm"><CardBody className="text-sm text-default-500">暂无可购买套餐</CardBody></Card>}
    </div>
  );

  const renderOrders = () => (
    <div id="shop-orders-anchor" className="grid grid-cols-1 lg:grid-cols-2 gap-4">
      {orders.map((order) => (
        <Card key={order.id} className="border border-gray-200 dark:border-default-200 shadow-sm">
          <CardBody className="space-y-3">
            <div className="flex items-start justify-between gap-3"><div><h2 className="font-semibold break-all">{order.orderNo}</h2><p className="text-sm text-default-500">{order.packageName}</p></div><Chip color={order.status === 1 ? "success" : "warning"}>{orderStatusText(order.status)}</Chip></div>
            <div className="grid grid-cols-2 gap-2 text-sm"><span>原价 {money(order.originalAmount)}</span><span>实付 {money(order.payableAmount)}</span>{Number(order.inviteDeduction || 0) > 0 && <span className="text-success">余额抵扣 {money(order.inviteDeduction)}</span>}<span>折扣 {order.discountRatio}%</span><span>支付 {order.paymentChannel || "-"}</span><span>返现 {money(order.rewardAmount)}</span><span>完成 {timeText(order.completedTime)}</span></div>
            {order.status === 0 && (
              <div className="flex flex-wrap gap-2">
                <Chip size="sm" variant="flat" color="warning">等待支付到账</Chip>
                {order.paymentUrl && <Button size="sm" variant="flat" onClick={() => window.open(order.paymentUrl, "_blank", "noopener,noreferrer")}>继续支付</Button>}
                <Button size="sm" variant="flat" color="primary" onClick={() => setPayingOrder({ order, openedAt: Date.now() })}>查询支付结果</Button>
              </div>
            )}
          </CardBody>
        </Card>
      ))}
      {orders.length === 0 && <Card className="border border-gray-200 dark:border-default-200 shadow-sm"><CardBody className="text-sm text-default-500">暂无订单</CardBody></Card>}
    </div>
  );

  const renderRedeem = () => (
    <Card className="border border-gray-200 dark:border-default-200 shadow-sm max-w-xl">
      <CardHeader><h2 className="text-lg font-semibold">兑换套餐</h2></CardHeader>
      <CardBody className="space-y-4"><Input label="兑换码" value={redeemInput} onChange={(e) => setRedeemInput(e.target.value)} /><Button color="primary" isLoading={saving} onClick={redeem}>兑换</Button></CardBody>
    </Card>
  );

  const renderInvite = () => (
    <div id="shop-invite-anchor" className="space-y-4"><Card className="border border-gray-200 dark:border-default-200 shadow-sm"><CardHeader><h2 className="text-lg font-semibold">我的邀请</h2></CardHeader><CardBody className="space-y-4"><div className="grid grid-cols-1 md:grid-cols-3 gap-3 text-sm"><span>邀请码 {inviteInfo?.inviteCode || "-"}</span><span>余额 {money(inviteInfo?.inviteBalance)}<br /><span className="text-xs text-default-500">购买套餐时可勾选「邀请余额抵扣」直接使用</span></span><span>首次/续费 {inviteInfo?.inviteRatio || 0}% / {inviteInfo?.inviteRenewalRatio || 0}%</span></div><div className="flex flex-col sm:flex-row gap-2"><Input readOnly value={inviteLink} label="邀请链接" /><Button color="primary" onClick={copyInviteLink}>复制</Button></div></CardBody></Card><div className="grid grid-cols-1 xl:grid-cols-2 gap-4"><Card className="border border-gray-200 dark:border-default-200 shadow-sm"><CardHeader><h3 className="font-semibold">邀请用户</h3></CardHeader><CardBody className="space-y-2">{inviteRecords.invites.map((item) => <div key={item.id} className="rounded-md border border-default-200 p-3 text-sm">用户 #{item.inviteeUserId}<br /><span className="text-default-500">{timeText(item.createdTime)}</span></div>)}</CardBody></Card><Card className="border border-gray-200 dark:border-default-200 shadow-sm"><CardHeader><h3 className="font-semibold">返现记录</h3></CardHeader><CardBody className="space-y-2">{inviteRecords.rewards.map((item) => <div key={item.id} className="rounded-md border border-default-200 p-3 text-sm">{money(item.rewardAmount)}<br /><span className="text-default-500">订单 #{item.orderId} · {item.ratio}% · {timeText(item.createdTime)}</span></div>)}</CardBody></Card></div></div>
  );

  if (loading) return <div className="flex justify-center py-16"><Spinner label="加载中" /></div>;

  return (
    <div className="px-3 lg:px-6 py-6 space-y-4">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between"><div><h1 className="text-2xl font-semibold">套餐中心</h1><p className="text-sm text-default-500 mt-1">购买套餐、兑换代码和管理邀请返现</p></div><Button variant="flat" onClick={loadData}>刷新</Button></div>
      <div className="flex flex-wrap gap-2">{tabs.map((tab) => <Button key={tab.key} id={tab.key === "orders" ? "shop-orders-tab-anchor" : tab.key === "invite" ? "shop-invite-tab-anchor" : undefined} size="sm" color={activeTab === tab.key ? "primary" : "default"} variant={activeTab === tab.key ? "solid" : "flat"} onClick={() => setActiveTab(tab.key)}>{tab.label}</Button>)}</div>
      {activeTab === "plans" && renderPlans()}
      {activeTab === "orders" && renderOrders()}
      {activeTab === "redeem" && renderRedeem()}
      {activeTab === "invite" && renderInvite()}

      <Modal isOpen={!!payingOrder && payingOrder.order.status === 0} onClose={() => setPayingOrder(null)} size="lg">
        <ModalContent>
          <ModalHeader className="flex flex-col gap-1">订单支付</ModalHeader>
          <ModalBody className="pb-4 space-y-3">
            {payingOrder && (
              <>
                <div className="grid grid-cols-2 gap-2 text-sm">
                  <span className="text-default-500">订单号</span><span className="break-all">{payingOrder.order.orderNo}</span>
                  <span className="text-default-500">套餐</span><span>{payingOrder.order.packageName}</span>
                  <span className="text-default-500">应付</span><span>{money(payingOrder.order.payableAmount)}</span>
                  {Number(payingOrder.order.inviteDeduction || 0) > 0 && (<><span className="text-default-500">余额抵扣</span><span className="text-success">-{money(payingOrder.order.inviteDeduction)}</span></>)}
                  <span className="text-default-500">支付渠道</span><span>{payingOrder.order.paymentChannel || "-"}</span>
                </div>
                <p className="text-xs text-default-500">付款完成后本页会自动检测到账（最长 5 分钟）；如未自动跳转可点击下方按钮手动查询。</p>
              </>
            )}
          </ModalBody>
          <ModalFooter>
            <Button variant="light" onClick={() => setPayingOrder(null)}>稍后支付</Button>
            {payingOrder?.order.paymentUrl && <Button variant="flat" onClick={() => window.open(payingOrder.order.paymentUrl, "_blank", "noopener,noreferrer")}>打开支付页</Button>}
            <Button color="primary" onClick={async () => { if (!payingOrder) return; try { const res = await getMyOrders(); const fresh = ((res.data || []) as OrderRecord[]).find((item) => item.id === payingOrder.order.id); if (fresh?.status === 1) { toast.success("支付成功，套餐已到账"); setPayingOrder(null); await loadData(); } else { toast("尚未检测到到账，将继续自动查询"); } } catch { toast.error("查询失败，请稍后重试"); } }}>我已完成支付</Button>
          </ModalFooter>
        </ModalContent>
      </Modal>
    </div>
  );
}
