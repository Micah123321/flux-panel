import { Button } from "@heroui/button";
import { Spinner } from "@heroui/spinner";
import { useEffect, useState } from "react";
import toast from "react-hot-toast";
import { useNavigate } from "react-router-dom";

import {
  adminGetDeviceGroups,
  adminGetInviteConfig,
  adminGetInviteRecords,
  adminGetOrders,
  adminGetPackagePlans,
  adminGetPaymentConfigs,
  adminGetRedeemCodes,
  adminGetUserGroups,
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
import { defaultPaymentConfigs, tabs, TabKey } from "./constants";
import InviteSection from "./InviteSection";
import OrderSection from "./OrderSection";
import PaymentSection from "./PaymentSection";
import PlanSection from "./PlanSection";
import RedeemSection from "./RedeemSection";
import GroupSection from "./GroupSection";

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

  const updatePaymentConfigState = (channel: string, patch: Partial<PaymentConfig>) => {
    setPaymentConfigs((items) => items.map((item) => (item.channel === channel ? { ...item, ...patch } : item)));
  };

  if (loading) return <div className="flex justify-center py-16"><Spinner label="加载中" /></div>;

  return (
    <div className="px-3 lg:px-6 py-6 space-y-4">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between"><div><h1 className="text-2xl font-semibold">商业管理</h1><p className="text-sm text-default-500 mt-1">套餐、订单、兑换码、用户组和邀请返现</p></div><Button variant="flat" onClick={loadData}>刷新</Button></div>
      <div className="flex flex-wrap gap-2">{tabs.map((tab) => <Button key={tab.key} size="sm" color={activeTab === tab.key ? "primary" : "default"} variant={activeTab === tab.key ? "solid" : "flat"} onClick={() => setActiveTab(tab.key)}>{tab.label}</Button>)}</div>
      {activeTab === "plans" && <PlanSection plans={plans} userGroups={userGroups} saving={saving} setSaving={setSaving} reload={loadData} />}
      {activeTab === "groups" && <GroupSection deviceGroups={deviceGroups} userGroups={userGroups} tunnels={tunnels} saving={saving} setSaving={setSaving} reload={loadData} />}
      {activeTab === "redeem" && <RedeemSection plans={plans} redeemCodes={redeemCodes} saving={saving} setSaving={setSaving} reload={loadData} />}
      {activeTab === "orders" && <OrderSection orders={orders} reload={loadData} />}
      {activeTab === "payment" && <PaymentSection paymentConfigs={paymentConfigs} updatePaymentConfigState={updatePaymentConfigState} saving={saving} setSaving={setSaving} reload={loadData} />}
      {activeTab === "invite" && <InviteSection inviteData={inviteData} inviteConfig={inviteConfig} setInviteConfig={setInviteConfig} reload={loadData} />}
    </div>
  );
}
