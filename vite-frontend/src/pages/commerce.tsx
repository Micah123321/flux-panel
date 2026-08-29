import { Button } from "@heroui/button";
import { Card, CardBody, CardHeader } from "@heroui/card";
import { Chip } from "@heroui/chip";
import { Input } from "@heroui/input";
import { Spinner } from "@heroui/spinner";
import { useEffect, useState } from "react";
import toast from "react-hot-toast";

import { completeOrder, createOrder, getInviteInfo, getMyInviteRecords, getMyOrders, getPackagePlans, redeemCode } from "@/api";
import { InviteInfo, InviteRecordsData, OrderRecord, PackagePlan } from "@/types";

type TabKey = "plans" | "orders" | "redeem" | "invite";

const tabs: { key: TabKey; label: string }[] = [
  { key: "plans", label: "套餐" },
  { key: "orders", label: "订单" },
  { key: "redeem", label: "兑换" },
  { key: "invite", label: "邀请" },
];

const money = (value?: number) => `￥${Number(value || 0).toFixed(2)}`;
const timeText = (value?: number | null) => (value ? new Date(value).toLocaleString("zh-CN") : "-");
const orderStatusText = (status: number) => (status === 1 ? "已完成" : "待支付");

export default function CommercePage() {
  const [activeTab, setActiveTab] = useState<TabKey>("plans");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [plans, setPlans] = useState<PackagePlan[]>([]);
  const [orders, setOrders] = useState<OrderRecord[]>([]);
  const [inviteInfo, setInviteInfo] = useState<InviteInfo | null>(null);
  const [inviteRecords, setInviteRecords] = useState<InviteRecordsData>({ invites: [], rewards: [] });
  const [redeemInput, setRedeemInput] = useState("");
  const [orderRedeemCode, setOrderRedeemCode] = useState<Record<number, string>>({});

  const loadData = async () => {
    setLoading(true);
    try {
      const [planRes, orderRes, inviteInfoRes, inviteRecordsRes] = await Promise.all([
        getPackagePlans(),
        getMyOrders(),
        getInviteInfo(),
        getMyInviteRecords(),
      ]);
      if (planRes.code === 0) setPlans((planRes.data || []) as PackagePlan[]);
      if (orderRes.code === 0) setOrders((orderRes.data || []) as OrderRecord[]);
      if (inviteInfoRes.code === 0 && inviteInfoRes.data) setInviteInfo(inviteInfoRes.data as InviteInfo);
      if (inviteRecordsRes.code === 0 && inviteRecordsRes.data) setInviteRecords(inviteRecordsRes.data as InviteRecordsData);
    } catch (error) {
      toast.error("加载套餐中心失败");
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const createPlanOrder = async (plan: PackagePlan) => {
    setSaving(true);
    try {
      const res = await createOrder({ packagePlanId: plan.id, redeemCode: orderRedeemCode[plan.id] || undefined });
      if (res.code !== 0) return toast.error(res.msg || "创建订单失败");
      toast.success("订单已创建");
      setActiveTab("orders");
      await loadData();
    } finally {
      setSaving(false);
    }
  };

  const complete = async (id: number) => {
    setSaving(true);
    try {
      const res = await completeOrder(id);
      if (res.code !== 0) return toast.error(res.msg || "完成订单失败");
      toast.success("套餐已发放");
      await loadData();
    } finally {
      setSaving(false);
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
    <div className="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 gap-4">
      {plans.map((plan) => (
        <Card key={plan.id} className="border border-gray-200 dark:border-default-200 shadow-sm">
          <CardBody className="space-y-4">
            <div className="flex items-start justify-between gap-3"><div><h2 className="text-lg font-semibold">{plan.name}</h2><p className="text-sm text-default-500">{plan.description || "-"}</p></div><Chip color="primary" variant="flat">{money(plan.price)}</Chip></div>
            <div className="grid grid-cols-2 gap-2 text-sm"><span>{plan.durationMultiplier}个月</span><span>{plan.flow} GiB</span><span>{plan.maxRules} 条规则</span><span>{plan.speedMbps || 0} Mbps</span><span>IP {plan.ipLimit || 0}</span><span>连接 {plan.connectionLimit || 0}</span></div>
            <Input label="兑换码（可选）" value={orderRedeemCode[plan.id] || ""} onChange={(e) => setOrderRedeemCode({ ...orderRedeemCode, [plan.id]: e.target.value })} />
            <Button color="primary" isLoading={saving} onClick={() => createPlanOrder(plan)}>购买</Button>
          </CardBody>
        </Card>
      ))}
      {plans.length === 0 && <Card className="border border-gray-200 dark:border-default-200 shadow-sm"><CardBody className="text-sm text-default-500">暂无可购买套餐</CardBody></Card>}
    </div>
  );

  const renderOrders = () => (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
      {orders.map((order) => (
        <Card key={order.id} className="border border-gray-200 dark:border-default-200 shadow-sm">
          <CardBody className="space-y-3">
            <div className="flex items-start justify-between gap-3"><div><h2 className="font-semibold break-all">{order.orderNo}</h2><p className="text-sm text-default-500">{order.packageName}</p></div><Chip color={order.status === 1 ? "success" : "warning"}>{orderStatusText(order.status)}</Chip></div>
            <div className="grid grid-cols-2 gap-2 text-sm"><span>原价 {money(order.originalAmount)}</span><span>实付 {money(order.payableAmount)}</span><span>折扣 {order.discountRatio}%</span><span>返现 {money(order.rewardAmount)}</span><span>创建 {timeText(order.createdTime)}</span><span>完成 {timeText(order.completedTime)}</span></div>
            {order.status === 0 && <Button size="sm" color="primary" isLoading={saving} onClick={() => complete(order.id)}>完成支付</Button>}
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
    <div className="space-y-4"><Card className="border border-gray-200 dark:border-default-200 shadow-sm"><CardHeader><h2 className="text-lg font-semibold">我的邀请</h2></CardHeader><CardBody className="space-y-4"><div className="grid grid-cols-1 md:grid-cols-3 gap-3 text-sm"><span>邀请码 {inviteInfo?.inviteCode || "-"}</span><span>余额 {money(inviteInfo?.inviteBalance)}</span><span>首次/续费 {inviteInfo?.inviteRatio || 0}% / {inviteInfo?.inviteRenewalRatio || 0}%</span></div><div className="flex flex-col sm:flex-row gap-2"><Input readOnly value={inviteLink} label="邀请链接" /><Button color="primary" onClick={copyInviteLink}>复制</Button></div></CardBody></Card><div className="grid grid-cols-1 xl:grid-cols-2 gap-4"><Card className="border border-gray-200 dark:border-default-200 shadow-sm"><CardHeader><h3 className="font-semibold">邀请用户</h3></CardHeader><CardBody className="space-y-2">{inviteRecords.invites.map((item) => <div key={item.id} className="rounded-md border border-default-200 p-3 text-sm">用户 #{item.inviteeUserId}<br /><span className="text-default-500">{timeText(item.createdTime)}</span></div>)}</CardBody></Card><Card className="border border-gray-200 dark:border-default-200 shadow-sm"><CardHeader><h3 className="font-semibold">返现记录</h3></CardHeader><CardBody className="space-y-2">{inviteRecords.rewards.map((item) => <div key={item.id} className="rounded-md border border-default-200 p-3 text-sm">{money(item.rewardAmount)}<br /><span className="text-default-500">订单 #{item.orderId} · {item.ratio}% · {timeText(item.createdTime)}</span></div>)}</CardBody></Card></div></div>
  );

  if (loading) return <div className="flex justify-center py-16"><Spinner label="加载中" /></div>;

  return (
    <div className="px-3 lg:px-6 py-6 space-y-4">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between"><div><h1 className="text-2xl font-semibold">套餐中心</h1><p className="text-sm text-default-500 mt-1">购买套餐、兑换代码和管理邀请返现</p></div><Button variant="flat" onClick={loadData}>刷新</Button></div>
      <div className="flex flex-wrap gap-2">{tabs.map((tab) => <Button key={tab.key} size="sm" color={activeTab === tab.key ? "primary" : "default"} variant={activeTab === tab.key ? "solid" : "flat"} onClick={() => setActiveTab(tab.key)}>{tab.label}</Button>)}</div>
      {activeTab === "plans" && renderPlans()}
      {activeTab === "orders" && renderOrders()}
      {activeTab === "redeem" && renderRedeem()}
      {activeTab === "invite" && renderInvite()}
    </div>
  );
}
