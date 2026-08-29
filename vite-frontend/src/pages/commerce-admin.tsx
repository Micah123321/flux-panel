import { Button } from "@heroui/button";
import { Card, CardBody, CardHeader } from "@heroui/card";
import { Chip } from "@heroui/chip";
import { Input } from "@heroui/input";
import { Spinner } from "@heroui/spinner";
import { useEffect, useState } from "react";
import toast from "react-hot-toast";
import { useNavigate } from "react-router-dom";

import {
  adminBatchCreateRedeemCodes,
  adminBindDeviceGroupTunnels,
  adminBindUserGroupDeviceGroups,
  adminCompleteOrder,
  adminCreateDeviceGroup,
  adminCreatePackagePlan,
  adminCreateUserGroup,
  adminDeleteDeviceGroup,
  adminDeletePackagePlan,
  adminDeleteRedeemCode,
  adminDeleteUserGroup,
  adminGetDeviceGroups,
  adminGetInviteConfig,
  adminGetInviteRecords,
  adminGetOrders,
  adminGetPackagePlans,
  adminGetPaymentConfigs,
  adminGetRedeemCodes,
  adminGetUserGroups,
  adminUpdateDeviceGroup,
  adminUpdateInviteConfig,
  adminUpdatePackagePlan,
  adminUpdatePaymentConfig,
  adminUpdateUserGroup,
  getTunnelList,
} from "@/api";
import {
  DeviceGroup,
  InviteRecordsData,
  OrderRecord,
  PackagePlan,
  PaymentConfig,
  RedeemCode,
  Tunnel,
  UserGroup,
} from "@/types";
import { isAdmin } from "@/utils/auth";

type TabKey = "plans" | "groups" | "redeem" | "orders" | "payment" | "invite";

const tabs: { key: TabKey; label: string }[] = [
  { key: "plans", label: "套餐" },
  { key: "groups", label: "用户组/设备组" },
  { key: "redeem", label: "兑换码" },
  { key: "orders", label: "订单" },
  { key: "payment", label: "支付方式" },
  { key: "invite", label: "邀请返现" },
];

const emptyPlanForm = {
  id: "",
  name: "",
  hidden: "0",
  price: "0",
  type: "1",
  durationMultiplier: "1",
  userGroupId: "",
  flow: "0",
  maxRules: "0",
  speedMbps: "0",
  ipLimit: "0",
  connectionLimit: "0",
  description: "",
  status: "1",
};

const emptyDeviceGroupForm = { id: "", name: "", description: "", tunnelIds: [] as number[], status: "1" };
const emptyUserGroupForm = { id: "", name: "", description: "", deviceGroupIds: [] as number[], status: "1" };
const emptyRedeemForm = { packagePlanId: "", discountRatio: "100", totalTimes: "1", count: "1", codes: "" };

const defaultPaymentConfigs: PaymentConfig[] = [
  { channel: "easypay", displayName: "EasyPay 易支付", enabled: false, payType: "alipay", currency: "CNY", status: 1 },
  { channel: "alipay", displayName: "支付宝官方", enabled: false, currency: "CNY", status: 1 },
  { channel: "wechat", displayName: "微信支付官方", enabled: false, currency: "CNY", status: 1 },
  { channel: "stripe", displayName: "Stripe", enabled: false, currency: "cny", status: 1 },
];

const statusText = (status: number) => (status === 1 ? "正常" : status === 0 ? "待处理" : "停用");
const orderStatusText = (status: number) => (status === 1 ? "已完成" : "待支付");
const money = (value?: number) => `￥${Number(value || 0).toFixed(2)}`;
const timeText = (value?: number | null) => (value ? new Date(value).toLocaleString("zh-CN") : "-");

export default function CommerceAdminPage() {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<TabKey>("plans");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [plans, setPlans] = useState<PackagePlan[]>([]);
  const [deviceGroups, setDeviceGroups] = useState<DeviceGroup[]>([]);
  const [userGroups, setUserGroups] = useState<UserGroup[]>([]);
  const [redeemCodes, setRedeemCodes] = useState<RedeemCode[]>([]);
  const [orders, setOrders] = useState<OrderRecord[]>([]);
  const [paymentConfigs, setPaymentConfigs] = useState<PaymentConfig[]>(defaultPaymentConfigs);
  const [tunnels, setTunnels] = useState<Tunnel[]>([]);
  const [inviteData, setInviteData] = useState<InviteRecordsData>({ invites: [], rewards: [] });
  const [inviteConfig, setInviteConfig] = useState({ inviteRatio: "0", inviteRenewalRatio: "0" });
  const [planForm, setPlanForm] = useState({ ...emptyPlanForm });
  const [deviceGroupForm, setDeviceGroupForm] = useState({ ...emptyDeviceGroupForm });
  const [userGroupForm, setUserGroupForm] = useState({ ...emptyUserGroupForm });
  const [redeemForm, setRedeemForm] = useState({ ...emptyRedeemForm });

  const loadData = async () => {
    setLoading(true);
    try {
      const [planRes, deviceGroupRes, userGroupRes, redeemRes, orderRes, paymentRes, tunnelRes, inviteConfigRes, inviteRecordsRes] = await Promise.all([
        adminGetPackagePlans(),
        adminGetDeviceGroups(),
        adminGetUserGroups(),
        adminGetRedeemCodes(),
        adminGetOrders(),
        adminGetPaymentConfigs(),
        getTunnelList(),
        adminGetInviteConfig(),
        adminGetInviteRecords(),
      ]);

      if (planRes.code === 0) setPlans((planRes.data || []) as PackagePlan[]);
      if (deviceGroupRes.code === 0) setDeviceGroups((deviceGroupRes.data || []) as DeviceGroup[]);
      if (userGroupRes.code === 0) setUserGroups((userGroupRes.data || []) as UserGroup[]);
      if (redeemRes.code === 0) setRedeemCodes((redeemRes.data || []) as RedeemCode[]);
      if (orderRes.code === 0) setOrders((orderRes.data || []) as OrderRecord[]);
      if (paymentRes.code === 0) setPaymentConfigs((paymentRes.data || defaultPaymentConfigs) as PaymentConfig[]);
      if (tunnelRes.code === 0) setTunnels((tunnelRes.data || []) as Tunnel[]);
      if (inviteConfigRes.code === 0 && inviteConfigRes.data) {
        const data = inviteConfigRes.data as { inviteRatio: number; inviteRenewalRatio: number };
        setInviteConfig({ inviteRatio: String(data.inviteRatio || 0), inviteRenewalRatio: String(data.inviteRenewalRatio || 0) });
      }
      if (inviteRecordsRes.code === 0 && inviteRecordsRes.data) setInviteData(inviteRecordsRes.data as InviteRecordsData);
    } catch (error) {
      toast.error("加载商业管理数据失败");
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!isAdmin()) {
      toast.error("需要管理员权限");
      navigate("/dashboard", { replace: true });
      return;
    }
    loadData();
  }, []);

  const planPayload = () => ({
    ...(planForm.id ? { id: Number(planForm.id) } : {}),
    name: planForm.name.trim(),
    hidden: Number(planForm.hidden),
    price: Number(planForm.price),
    type: Number(planForm.type),
    durationMultiplier: Number(planForm.durationMultiplier),
    userGroupId: planForm.userGroupId ? Number(planForm.userGroupId) : null,
    flow: Number(planForm.flow),
    maxRules: Number(planForm.maxRules),
    speedMbps: Number(planForm.speedMbps),
    ipLimit: Number(planForm.ipLimit),
    connectionLimit: Number(planForm.connectionLimit),
    description: planForm.description,
    status: Number(planForm.status),
  });

  const savePlan = async () => {
    if (!planForm.name.trim()) return toast.error("请输入套餐名称");
    setSaving(true);
    try {
      const res = planForm.id ? await adminUpdatePackagePlan(planPayload()) : await adminCreatePackagePlan(planPayload());
      if (res.code !== 0) return toast.error(res.msg || "套餐保存失败");
      toast.success("套餐已保存");
      setPlanForm({ ...emptyPlanForm });
      await loadData();
    } finally {
      setSaving(false);
    }
  };

  const editPlan = (plan: PackagePlan) => setPlanForm({
    id: String(plan.id),
    name: plan.name || "",
    hidden: String(plan.hidden ?? 0),
    price: String(plan.price ?? 0),
    type: String(plan.type ?? 1),
    durationMultiplier: String(plan.durationMultiplier ?? 1),
    userGroupId: plan.userGroupId ? String(plan.userGroupId) : "",
    flow: String(plan.flow ?? 0),
    maxRules: String(plan.maxRules ?? 0),
    speedMbps: String(plan.speedMbps ?? 0),
    ipLimit: String(plan.ipLimit ?? 0),
    connectionLimit: String(plan.connectionLimit ?? 0),
    description: plan.description || "",
    status: String(plan.status ?? 1),
  });

  const removePlan = async (id: number) => {
    if (!window.confirm("确认删除该套餐？已有订单的套餐无法删除。")) return;
    const res = await adminDeletePackagePlan(id);
    if (res.code !== 0) return toast.error(res.msg || "删除失败");
    toast.success("套餐已删除");
    loadData();
  };

  const toggleId = (ids: number[], id: number) => (ids.includes(id) ? ids.filter((item) => item !== id) : [...ids, id]);

  const saveDeviceGroup = async () => {
    if (!deviceGroupForm.name.trim()) return toast.error("请输入设备组名称");
    setSaving(true);
    try {
      const payload = {
        ...(deviceGroupForm.id ? { id: Number(deviceGroupForm.id) } : {}),
        name: deviceGroupForm.name.trim(),
        description: deviceGroupForm.description,
        tunnelIds: deviceGroupForm.tunnelIds,
        status: Number(deviceGroupForm.status),
      };
      const res = deviceGroupForm.id ? await adminUpdateDeviceGroup(payload) : await adminCreateDeviceGroup(payload);
      if (res.code !== 0) return toast.error(res.msg || "设备组保存失败");
      if (res.data?.id) await adminBindDeviceGroupTunnels({ id: res.data.id, tunnelIds: deviceGroupForm.tunnelIds });
      toast.success("设备组已保存");
      setDeviceGroupForm({ ...emptyDeviceGroupForm });
      await loadData();
    } finally {
      setSaving(false);
    }
  };

  const saveUserGroup = async () => {
    if (!userGroupForm.name.trim()) return toast.error("请输入用户组名称");
    setSaving(true);
    try {
      const payload = {
        ...(userGroupForm.id ? { id: Number(userGroupForm.id) } : {}),
        name: userGroupForm.name.trim(),
        description: userGroupForm.description,
        status: Number(userGroupForm.status),
      };
      const res = userGroupForm.id ? await adminUpdateUserGroup(payload) : await adminCreateUserGroup(payload);
      if (res.code !== 0) return toast.error(res.msg || "用户组保存失败");
      const id = Number(res.data?.id || userGroupForm.id);
      await adminBindUserGroupDeviceGroups({ id, deviceGroupIds: userGroupForm.deviceGroupIds });
      toast.success("用户组已保存");
      setUserGroupForm({ ...emptyUserGroupForm });
      await loadData();
    } finally {
      setSaving(false);
    }
  };

  const createRedeemCodes = async () => {
    if (!redeemForm.packagePlanId) return toast.error("请选择套餐");
    const codes = redeemForm.codes.split(/\r?\n|,/).map((item) => item.trim()).filter(Boolean);
    setSaving(true);
    try {
      const res = await adminBatchCreateRedeemCodes({
        packagePlanId: Number(redeemForm.packagePlanId),
        discountRatio: Number(redeemForm.discountRatio),
        totalTimes: Number(redeemForm.totalTimes),
        count: Number(redeemForm.count),
        codes,
      });
      if (res.code !== 0) return toast.error(res.msg || "兑换码创建失败");
      toast.success("兑换码已创建");
      setRedeemForm({ ...emptyRedeemForm });
      await loadData();
    } finally {
      setSaving(false);
    }
  };

  const completeOrderByAdmin = async (id: number) => {
    const res = await adminCompleteOrder(id);
    if (res.code !== 0) return toast.error(res.msg || "完成订单失败");
    toast.success("订单已完成并发放套餐");
    loadData();
  };

  const updatePaymentConfigState = (channel: string, patch: Partial<PaymentConfig>) => {
    setPaymentConfigs((items) => items.map((item) => (item.channel === channel ? { ...item, ...patch } : item)));
  };

  const savePaymentConfig = async (config: PaymentConfig) => {
    if (!config.displayName?.trim()) return toast.error("请输入支付方式名称");
    setSaving(true);
    try {
      const res = await adminUpdatePaymentConfig({ ...config, status: config.status ?? 1 });
      if (res.code !== 0) return toast.error(res.msg || "支付方式保存失败");
      toast.success("支付方式已保存");
      await loadData();
    } finally {
      setSaving(false);
    }
  };

  const saveInviteConfig = async () => {
    const res = await adminUpdateInviteConfig({
      inviteRatio: Number(inviteConfig.inviteRatio),
      inviteRenewalRatio: Number(inviteConfig.inviteRenewalRatio),
    });
    if (res.code !== 0) return toast.error(res.msg || "邀请配置保存失败");
    toast.success("邀请配置已保存");
    loadData();
  };

  const renderPlanForm = () => (
    <Card className="border border-gray-200 dark:border-default-200 shadow-sm">
      <CardHeader><h2 className="text-lg font-semibold">{planForm.id ? "编辑套餐" : "添加套餐"}</h2></CardHeader>
      <CardBody className="space-y-4">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <Input label="名称" value={planForm.name} onChange={(e) => setPlanForm({ ...planForm, name: e.target.value })} />
          <Input label="价格（元）" type="number" value={planForm.price} onChange={(e) => setPlanForm({ ...planForm, price: e.target.value })} />
          <label className="text-sm text-default-600">隐藏<select className="mt-1 w-full rounded-md border border-default-200 bg-transparent px-3 py-2" value={planForm.hidden} onChange={(e) => setPlanForm({ ...planForm, hidden: e.target.value })}><option value="0">显示</option><option value="1">隐藏</option></select></label>
          <Input label="类型" type="number" value={planForm.type} onChange={(e) => setPlanForm({ ...planForm, type: e.target.value })} />
          <Input label="时长倍数" type="number" value={planForm.durationMultiplier} onChange={(e) => setPlanForm({ ...planForm, durationMultiplier: e.target.value })} />
          <label className="text-sm text-default-600">分配用户组<select className="mt-1 w-full rounded-md border border-default-200 bg-transparent px-3 py-2" value={planForm.userGroupId} onChange={(e) => setPlanForm({ ...planForm, userGroupId: e.target.value })}><option value="">不分配</option>{userGroups.map((group) => <option key={group.id} value={group.id}>{group.name}</option>)}</select></label>
          <Input label="流量（GiB）" type="number" value={planForm.flow} onChange={(e) => setPlanForm({ ...planForm, flow: e.target.value })} />
          <Input label="最大规则数（条）" type="number" value={planForm.maxRules} onChange={(e) => setPlanForm({ ...planForm, maxRules: e.target.value })} />
          <Input label="用户限速（Mbps）" type="number" value={planForm.speedMbps} onChange={(e) => setPlanForm({ ...planForm, speedMbps: e.target.value })} />
          <Input label="用户 IP 限制" type="number" value={planForm.ipLimit} onChange={(e) => setPlanForm({ ...planForm, ipLimit: e.target.value })} />
          <Input label="用户连接数限制" type="number" value={planForm.connectionLimit} onChange={(e) => setPlanForm({ ...planForm, connectionLimit: e.target.value })} />
          <label className="text-sm text-default-600">状态<select className="mt-1 w-full rounded-md border border-default-200 bg-transparent px-3 py-2" value={planForm.status} onChange={(e) => setPlanForm({ ...planForm, status: e.target.value })}><option value="1">启用</option><option value="0">停用</option></select></label>
        </div>
        <textarea className="w-full min-h-24 rounded-md border border-default-200 bg-transparent px-3 py-2 text-sm" placeholder="说明" value={planForm.description} onChange={(e) => setPlanForm({ ...planForm, description: e.target.value })} />
        <div className="flex gap-2"><Button color="primary" isLoading={saving} onClick={savePlan}>保存套餐</Button><Button variant="flat" onClick={() => setPlanForm({ ...emptyPlanForm })}>清空</Button></div>
      </CardBody>
    </Card>
  );

  const renderPlans = () => (
    <div className="space-y-4">
      {renderPlanForm()}
      <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
        {plans.map((plan) => (
          <Card key={plan.id} className="border border-gray-200 dark:border-default-200 shadow-sm">
            <CardBody className="space-y-3">
              <div className="flex items-start justify-between gap-3"><div><h3 className="text-base font-semibold">{plan.name}</h3><p className="text-sm text-default-500">{plan.description || "-"}</p></div><Chip color={plan.status === 1 ? "success" : "default"} size="sm">{statusText(plan.status)}</Chip></div>
              <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-sm"><span>价格 {money(plan.price)}</span><span>{plan.durationMultiplier}个月</span><span>{plan.flow} GiB</span><span>{plan.maxRules} 条规则</span><span>限速 {plan.speedMbps || 0} Mbps</span><span>IP {plan.ipLimit || 0}</span><span>连接 {plan.connectionLimit || 0}</span><span>{plan.hidden ? "已隐藏" : "公开"}</span></div>
              <div className="flex gap-2"><Button size="sm" variant="flat" onClick={() => editPlan(plan)}>编辑</Button><Button size="sm" color="danger" variant="flat" onClick={() => removePlan(plan.id)}>删除</Button></div>
            </CardBody>
          </Card>
        ))}
      </div>
    </div>
  );

  const renderGroups = () => (
    <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
      <Card className="border border-gray-200 dark:border-default-200 shadow-sm"><CardHeader><h2 className="text-lg font-semibold">设备组</h2></CardHeader><CardBody className="space-y-4">
        <Input label="设备组名称" value={deviceGroupForm.name} onChange={(e) => setDeviceGroupForm({ ...deviceGroupForm, name: e.target.value })} />
        <textarea className="w-full min-h-20 rounded-md border border-default-200 bg-transparent px-3 py-2 text-sm" placeholder="说明" value={deviceGroupForm.description} onChange={(e) => setDeviceGroupForm({ ...deviceGroupForm, description: e.target.value })} />
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">{tunnels.map((tunnel) => <label key={tunnel.id} className="flex items-center gap-2 rounded-md border border-default-200 px-3 py-2 text-sm"><input type="checkbox" checked={deviceGroupForm.tunnelIds.includes(tunnel.id)} onChange={() => setDeviceGroupForm({ ...deviceGroupForm, tunnelIds: toggleId(deviceGroupForm.tunnelIds, tunnel.id) })} />{tunnel.name}</label>)}</div>
        <div className="flex gap-2"><Button color="primary" isLoading={saving} onClick={saveDeviceGroup}>保存设备组</Button><Button variant="flat" onClick={() => setDeviceGroupForm({ ...emptyDeviceGroupForm })}>清空</Button></div>
        <div className="space-y-2">{deviceGroups.map((group) => <div key={group.id} className="rounded-md border border-default-200 p-3"><div className="flex items-center justify-between"><b>{group.name}</b><Chip size="sm">{statusText(group.status)}</Chip></div><p className="text-sm text-default-500 mt-1">{group.tunnelNames || "未绑定隧道"}</p><div className="flex gap-2 mt-2"><Button size="sm" variant="flat" onClick={() => setDeviceGroupForm({ id: String(group.id), name: group.name, description: group.description || "", tunnelIds: group.tunnelIdList || [], status: String(group.status ?? 1) })}>编辑</Button><Button size="sm" color="danger" variant="flat" onClick={async () => { if (window.confirm("确认删除设备组？")) { const res = await adminDeleteDeviceGroup(group.id); res.code === 0 ? toast.success("已删除") : toast.error(res.msg || "删除失败"); loadData(); } }}>删除</Button></div></div>)}</div>
      </CardBody></Card>
      <Card className="border border-gray-200 dark:border-default-200 shadow-sm"><CardHeader><h2 className="text-lg font-semibold">用户组</h2></CardHeader><CardBody className="space-y-4">
        <Input label="用户组名称" value={userGroupForm.name} onChange={(e) => setUserGroupForm({ ...userGroupForm, name: e.target.value })} />
        <textarea className="w-full min-h-20 rounded-md border border-default-200 bg-transparent px-3 py-2 text-sm" placeholder="说明" value={userGroupForm.description} onChange={(e) => setUserGroupForm({ ...userGroupForm, description: e.target.value })} />
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">{deviceGroups.map((group) => <label key={group.id} className="flex items-center gap-2 rounded-md border border-default-200 px-3 py-2 text-sm"><input type="checkbox" checked={userGroupForm.deviceGroupIds.includes(group.id)} onChange={() => setUserGroupForm({ ...userGroupForm, deviceGroupIds: toggleId(userGroupForm.deviceGroupIds, group.id) })} />{group.name}</label>)}</div>
        <div className="flex gap-2"><Button color="primary" isLoading={saving} onClick={saveUserGroup}>保存用户组</Button><Button variant="flat" onClick={() => setUserGroupForm({ ...emptyUserGroupForm })}>清空</Button></div>
        <div className="space-y-2">{userGroups.map((group) => <div key={group.id} className="rounded-md border border-default-200 p-3"><div className="flex items-center justify-between"><b>{group.name}</b><Chip size="sm">{statusText(group.status)}</Chip></div><p className="text-sm text-default-500 mt-1">{group.deviceGroupNames || "未绑定设备组"}</p><div className="flex gap-2 mt-2"><Button size="sm" variant="flat" onClick={() => setUserGroupForm({ id: String(group.id), name: group.name, description: group.description || "", deviceGroupIds: group.deviceGroupIds || [], status: String(group.status ?? 1) })}>编辑</Button><Button size="sm" color="danger" variant="flat" onClick={async () => { if (window.confirm("确认删除用户组？")) { const res = await adminDeleteUserGroup(group.id); res.code === 0 ? toast.success("已删除") : toast.error(res.msg || "删除失败"); loadData(); } }}>删除</Button></div></div>)}</div>
      </CardBody></Card>
    </div>
  );

  const renderRedeem = () => (
    <div className="space-y-4"><Card className="border border-gray-200 dark:border-default-200 shadow-sm"><CardHeader><h2 className="text-lg font-semibold">批量添加兑换码</h2></CardHeader><CardBody className="space-y-4">
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4"><label className="text-sm text-default-600">套餐<select className="mt-1 w-full rounded-md border border-default-200 bg-transparent px-3 py-2" value={redeemForm.packagePlanId} onChange={(e) => setRedeemForm({ ...redeemForm, packagePlanId: e.target.value })}><option value="">请选择</option>{plans.map((plan) => <option key={plan.id} value={plan.id}>{plan.name}</option>)}</select></label><Input label="折扣比例（%）" type="number" value={redeemForm.discountRatio} onChange={(e) => setRedeemForm({ ...redeemForm, discountRatio: e.target.value })} /><Input label="可用次数" type="number" value={redeemForm.totalTimes} onChange={(e) => setRedeemForm({ ...redeemForm, totalTimes: e.target.value })} /><Input label="生成数量" type="number" value={redeemForm.count} onChange={(e) => setRedeemForm({ ...redeemForm, count: e.target.value })} /></div>
      <textarea className="w-full min-h-24 rounded-md border border-default-200 bg-transparent px-3 py-2 text-sm" placeholder="兑换代码，可按行或逗号批量输入；留空则自动生成" value={redeemForm.codes} onChange={(e) => setRedeemForm({ ...redeemForm, codes: e.target.value })} />
      <Button color="primary" isLoading={saving} onClick={createRedeemCodes}>创建兑换码</Button>
    </CardBody></Card><div className="grid grid-cols-1 lg:grid-cols-2 gap-4">{redeemCodes.map((code) => <Card key={code.id} className="border border-gray-200 dark:border-default-200 shadow-sm"><CardBody className="space-y-2"><div className="flex items-center justify-between gap-2"><b className="break-all">{code.code}</b><Chip size="sm" color={code.status === 1 ? "success" : "default"}>{statusText(code.status)}</Chip></div><p className="text-sm text-default-500">{code.packageName} · {code.discountRatio}% · {code.usedTimes}/{code.totalTimes}</p><Button size="sm" color="danger" variant="flat" onClick={async () => { if (window.confirm("确认删除兑换码？")) { const res = await adminDeleteRedeemCode(code.id); res.code === 0 ? toast.success("已删除") : toast.error(res.msg || "删除失败"); loadData(); } }}>删除</Button></CardBody></Card>)}</div></div>
  );

  const renderOrders = () => (
    <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">{orders.map((order) => <Card key={order.id} className="border border-gray-200 dark:border-default-200 shadow-sm"><CardBody className="space-y-3"><div className="flex items-start justify-between gap-2"><div><h3 className="font-semibold break-all">{order.orderNo}</h3><p className="text-sm text-default-500">用户 #{order.userId} · {order.packageName}</p></div><Chip color={order.status === 1 ? "success" : "warning"}>{orderStatusText(order.status)}</Chip></div><div className="grid grid-cols-2 gap-2 text-sm"><span>原价 {money(order.originalAmount)}</span><span>应付 {money(order.payableAmount)}</span><span>已付 {money(order.paidAmount)}</span><span>支付 {order.paymentChannel || "-"}</span><span>流水 {order.providerTradeNo || "-"}</span><span>完成 {timeText(order.completedTime)}</span></div>{order.status === 0 && <Button size="sm" color="primary" onClick={() => completeOrderByAdmin(order.id)}>标记完成并发放</Button>}</CardBody></Card>)}</div>
  );

  const renderPayment = () => (
    <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">{paymentConfigs.map((config) => (
      <Card key={config.channel} className="border border-gray-200 dark:border-default-200 shadow-sm"><CardHeader><div className="flex w-full items-center justify-between gap-3"><h2 className="text-lg font-semibold">{config.displayName}</h2><Chip size="sm" color={config.enabled ? "success" : "default"}>{config.enabled ? "启用" : "停用"}</Chip></div></CardHeader><CardBody className="space-y-4">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4"><Input label="名称" value={config.displayName || ""} onChange={(e) => updatePaymentConfigState(config.channel, { displayName: e.target.value })} /><label className="text-sm text-default-600">启用<select className="mt-1 w-full rounded-md border border-default-200 bg-transparent px-3 py-2" value={config.enabled ? "1" : "0"} onChange={(e) => updatePaymentConfigState(config.channel, { enabled: e.target.value === "1" })}><option value="1">启用</option><option value="0">停用</option></select></label><Input label="支付类型" value={config.payType || ""} onChange={(e) => updatePaymentConfigState(config.channel, { payType: e.target.value })} /><Input label="币种" value={config.currency || ""} onChange={(e) => updatePaymentConfigState(config.channel, { currency: e.target.value })} /><Input label="网关地址" value={config.gatewayUrl || ""} onChange={(e) => updatePaymentConfigState(config.channel, { gatewayUrl: e.target.value })} /><Input label="App ID" value={config.appId || ""} onChange={(e) => updatePaymentConfigState(config.channel, { appId: e.target.value })} /><Input label="商户号" value={config.merchantId || ""} onChange={(e) => updatePaymentConfigState(config.channel, { merchantId: e.target.value })} /><Input label="证书序列号" value={config.serialNo || ""} onChange={(e) => updatePaymentConfigState(config.channel, { serialNo: e.target.value })} /><Input label="异步回调 URL" value={config.notifyUrl || ""} onChange={(e) => updatePaymentConfigState(config.channel, { notifyUrl: e.target.value })} /><Input label="成功返回 URL" value={config.returnUrl || ""} onChange={(e) => updatePaymentConfigState(config.channel, { returnUrl: e.target.value })} /><Input label="取消返回 URL" value={config.cancelUrl || ""} onChange={(e) => updatePaymentConfigState(config.channel, { cancelUrl: e.target.value })} /><Input label="Secret/APIv3 Key" value={config.secretKey || ""} onChange={(e) => updatePaymentConfigState(config.channel, { secretKey: e.target.value })} /><Input label="Stripe Secret Key" value={config.apiKey || ""} onChange={(e) => updatePaymentConfigState(config.channel, { apiKey: e.target.value })} /><Input label="Stripe Webhook Secret" value={config.endpointSecret || ""} onChange={(e) => updatePaymentConfigState(config.channel, { endpointSecret: e.target.value })} /></div>
        <textarea className="w-full min-h-24 rounded-md border border-default-200 bg-transparent px-3 py-2 text-sm" placeholder="应用/商户私钥" value={config.privateKey || ""} onChange={(e) => updatePaymentConfigState(config.channel, { privateKey: e.target.value })} />
        <textarea className="w-full min-h-24 rounded-md border border-default-200 bg-transparent px-3 py-2 text-sm" placeholder="平台/支付公钥或证书" value={config.publicKey || ""} onChange={(e) => updatePaymentConfigState(config.channel, { publicKey: e.target.value })} />
        <Button color="primary" isLoading={saving} onClick={() => savePaymentConfig(config)}>保存支付方式</Button>
      </CardBody></Card>
    ))}</div>
  );

  const renderInvite = () => (
    <div className="space-y-4"><Card className="border border-gray-200 dark:border-default-200 shadow-sm"><CardHeader><h2 className="text-lg font-semibold">邀请比例</h2></CardHeader><CardBody className="grid grid-cols-1 md:grid-cols-3 gap-4"><Input label="首次购买返现比例（%）" type="number" value={inviteConfig.inviteRatio} onChange={(e) => setInviteConfig({ ...inviteConfig, inviteRatio: e.target.value })} /><Input label="被邀请续费返现比例（%）" type="number" value={inviteConfig.inviteRenewalRatio} onChange={(e) => setInviteConfig({ ...inviteConfig, inviteRenewalRatio: e.target.value })} /><div className="flex items-end"><Button color="primary" onClick={saveInviteConfig}>保存配置</Button></div></CardBody></Card><div className="grid grid-cols-1 xl:grid-cols-2 gap-4"><Card className="border border-gray-200 dark:border-default-200 shadow-sm"><CardHeader><h2 className="text-lg font-semibold">邀请记录</h2></CardHeader><CardBody className="space-y-2">{inviteData.invites.map((item) => <div key={item.id} className="rounded-md border border-default-200 p-3 text-sm">邀请人 #{item.inviterUserId} 邀请用户 #{item.inviteeUserId}<br /><span className="text-default-500">{item.inviteCode} · {timeText(item.createdTime)}</span></div>)}</CardBody></Card><Card className="border border-gray-200 dark:border-default-200 shadow-sm"><CardHeader><h2 className="text-lg font-semibold">返现记录</h2></CardHeader><CardBody className="space-y-2">{inviteData.rewards.map((item) => <div key={item.id} className="rounded-md border border-default-200 p-3 text-sm">邀请人 #{item.inviterUserId} 获得 {money(item.rewardAmount)}<br /><span className="text-default-500">订单 #{item.orderId} · {item.ratio}% · {timeText(item.createdTime)}</span></div>)}</CardBody></Card></div></div>
  );

  if (loading) return <div className="flex justify-center py-16"><Spinner label="加载中" /></div>;

  return (
    <div className="px-3 lg:px-6 py-6 space-y-4">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between"><div><h1 className="text-2xl font-semibold">商业管理</h1><p className="text-sm text-default-500 mt-1">套餐、订单、兑换码、用户组和邀请返现</p></div><Button variant="flat" onClick={loadData}>刷新</Button></div>
      <div className="flex flex-wrap gap-2">{tabs.map((tab) => <Button key={tab.key} size="sm" color={activeTab === tab.key ? "primary" : "default"} variant={activeTab === tab.key ? "solid" : "flat"} onClick={() => setActiveTab(tab.key)}>{tab.label}</Button>)}</div>
      {activeTab === "plans" && renderPlans()}
      {activeTab === "groups" && renderGroups()}
      {activeTab === "redeem" && renderRedeem()}
      {activeTab === "orders" && renderOrders()}
      {activeTab === "payment" && renderPayment()}
      {activeTab === "invite" && renderInvite()}
    </div>
  );
}
