import { PaymentConfig } from "@/types";

export type TabKey = "plans" | "groups" | "redeem" | "orders" | "payment" | "invite";

export const tabs: { key: TabKey; label: string }[] = [
  { key: "plans", label: "套餐" },
  { key: "groups", label: "用户组/设备组" },
  { key: "redeem", label: "兑换码" },
  { key: "orders", label: "订单" },
  { key: "payment", label: "支付方式" },
  { key: "invite", label: "邀请返现" },
];

export const emptyPlanForm = {
  id: "",
  name: "",
  hidden: "0",
  price: "0",
  type: "1",
  durationMultiplier: "1",
  userGroupId: "",
  flow: "0",
  dailyFlow: "0",
  maxRules: "0",
  speedMbps: "0",
  ipLimit: "0",
  connectionLimit: "0",
  description: "",
  status: "1",
};

export const emptyDeviceGroupForm = { id: "", name: "", description: "", tunnelIds: [] as number[], status: "1" };
export const emptyUserGroupForm = { id: "", name: "", description: "", deviceGroupIds: [] as number[], status: "1" };
export const emptyRedeemForm = { packagePlanId: "", discountRatio: "100", totalTimes: "1", count: "1", codes: "" };

export const defaultPaymentConfigs: PaymentConfig[] = [
  { channel: "easypay", displayName: "EasyPay 易支付", enabled: false, payType: "alipay", currency: "CNY", status: 1 },
  { channel: "alipay", displayName: "支付宝官方", enabled: false, currency: "CNY", status: 1 },
  { channel: "wechat", displayName: "微信支付官方", enabled: false, currency: "CNY", status: 1 },
  { channel: "stripe", displayName: "Stripe", enabled: false, currency: "cny", status: 1 },
];

export const statusText = (status: number) => (status === 1 ? "正常" : status === 0 ? "待处理" : "停用");
export const orderStatusText = (status: number) => (status === 1 ? "已完成" : "待支付");
export const money = (value?: number) => `￥${Number(value || 0).toFixed(2)}`;
export const timeText = (value?: number | null) => (value ? new Date(value).toLocaleString("zh-CN") : "-");
export const toggleId = (ids: number[], id: number) => (ids.includes(id) ? ids.filter((item) => item !== id) : [...ids, id]);
