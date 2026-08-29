import { Button } from "@heroui/button";
import { Card, CardBody, CardHeader } from "@heroui/card";
import { Chip } from "@heroui/chip";
import { Input } from "@heroui/input";
import toast from "react-hot-toast";

import { adminUpdatePaymentConfig } from "@/api";
import { PaymentConfig } from "@/types";

interface PaymentSectionProps {
  paymentConfigs: PaymentConfig[];
  updatePaymentConfigState: (channel: string, patch: Partial<PaymentConfig>) => void;
  saving: boolean;
  setSaving: (value: boolean) => void;
  reload: () => void;
}

export default function PaymentSection({ paymentConfigs, updatePaymentConfigState, saving, setSaving, reload }: PaymentSectionProps) {
  const savePaymentConfig = async (config: PaymentConfig) => {
    if (!config.displayName?.trim()) return toast.error("请输入支付方式名称");
    setSaving(true);
    try {
      const res = await adminUpdatePaymentConfig({ ...config, status: config.status ?? 1 });
      if (res.code !== 0) return toast.error(res.msg || "支付方式保存失败");
      toast.success("支付方式已保存");
      reload();
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">{paymentConfigs.map((config) => (
      <Card key={config.channel} className="border border-gray-200 dark:border-default-200 shadow-sm"><CardHeader><div className="flex w-full items-center justify-between gap-3"><h2 className="text-lg font-semibold">{config.displayName}</h2><Chip size="sm" color={config.enabled ? "success" : "default"}>{config.enabled ? "启用" : "停用"}</Chip></div></CardHeader><CardBody className="space-y-4">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4"><Input label="名称" value={config.displayName || ""} onChange={(e) => updatePaymentConfigState(config.channel, { displayName: e.target.value })} /><label className="text-sm text-default-600">启用<select className="mt-1 w-full rounded-md border border-default-200 bg-transparent px-3 py-2" value={config.enabled ? "1" : "0"} onChange={(e) => updatePaymentConfigState(config.channel, { enabled: e.target.value === "1" })}><option value="1">启用</option><option value="0">停用</option></select></label><Input label="支付类型" value={config.payType || ""} onChange={(e) => updatePaymentConfigState(config.channel, { payType: e.target.value })} /><Input label="币种" value={config.currency || ""} onChange={(e) => updatePaymentConfigState(config.channel, { currency: e.target.value })} /><Input label="网关地址" value={config.gatewayUrl || ""} onChange={(e) => updatePaymentConfigState(config.channel, { gatewayUrl: e.target.value })} /><Input label="App ID" value={config.appId || ""} onChange={(e) => updatePaymentConfigState(config.channel, { appId: e.target.value })} /><Input label="商户号" value={config.merchantId || ""} onChange={(e) => updatePaymentConfigState(config.channel, { merchantId: e.target.value })} /><Input label="证书序列号" value={config.serialNo || ""} onChange={(e) => updatePaymentConfigState(config.channel, { serialNo: e.target.value })} /><Input label="异步回调 URL" value={config.notifyUrl || ""} onChange={(e) => updatePaymentConfigState(config.channel, { notifyUrl: e.target.value })} /><Input label="成功返回 URL" value={config.returnUrl || ""} onChange={(e) => updatePaymentConfigState(config.channel, { returnUrl: e.target.value })} /><Input label="取消返回 URL" value={config.cancelUrl || ""} onChange={(e) => updatePaymentConfigState(config.channel, { cancelUrl: e.target.value })} /><Input label="Secret/APIv3 Key" value={config.secretKey || ""} onChange={(e) => updatePaymentConfigState(config.channel, { secretKey: e.target.value })} /><Input label="API Key" value={config.apiKey || ""} onChange={(e) => updatePaymentConfigState(config.channel, { apiKey: e.target.value })} /><Input label="Webhook Signing Secret" value={config.endpointSecret || ""} onChange={(e) => updatePaymentConfigState(config.channel, { endpointSecret: e.target.value })} /></div>
        <textarea className="w-full min-h-24 rounded-md border border-default-200 bg-transparent px-3 py-2 text-sm" placeholder="应用/商户私钥" value={config.privateKey || ""} onChange={(e) => updatePaymentConfigState(config.channel, { privateKey: e.target.value })} />
        <textarea className="w-full min-h-24 rounded-md border border-default-200 bg-transparent px-3 py-2 text-sm" placeholder="平台/支付公钥或证书" value={config.publicKey || ""} onChange={(e) => updatePaymentConfigState(config.channel, { publicKey: e.target.value })} />
        <Button color="primary" isLoading={saving} onClick={() => savePaymentConfig(config)}>保存支付方式</Button>
      </CardBody></Card>
    ))}</div>
  );
}
