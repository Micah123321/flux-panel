import { Button } from "@heroui/button";
import { Card, CardBody, CardHeader } from "@heroui/card";
import { Chip } from "@heroui/chip";
import { Input } from "@heroui/input";
import { useState } from "react";
import toast from "react-hot-toast";

import { adminBatchCreateRedeemCodes, adminDeleteRedeemCode } from "@/api";
import { PackagePlan, RedeemCode } from "@/types";
import { emptyRedeemForm, statusText } from "./constants";

interface RedeemSectionProps {
  plans: PackagePlan[];
  redeemCodes: RedeemCode[];
  saving: boolean;
  setSaving: (value: boolean) => void;
  reload: () => void;
}

export default function RedeemSection({ plans, redeemCodes, saving, setSaving, reload }: RedeemSectionProps) {
  const [redeemForm, setRedeemForm] = useState({ ...emptyRedeemForm });

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
      reload();
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-4"><Card className="border border-gray-200 dark:border-default-200 shadow-sm"><CardHeader><h2 className="text-lg font-semibold">批量添加兑换码</h2></CardHeader><CardBody className="space-y-4">
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4"><label className="text-sm text-default-600">套餐<select className="mt-1 w-full rounded-md border border-default-200 bg-transparent px-3 py-2" value={redeemForm.packagePlanId} onChange={(e) => setRedeemForm({ ...redeemForm, packagePlanId: e.target.value })}><option value="">请选择</option>{plans.map((plan) => <option key={plan.id} value={plan.id}>{plan.name}</option>)}</select></label><Input label="折扣比例（%）" type="number" value={redeemForm.discountRatio} onChange={(e) => setRedeemForm({ ...redeemForm, discountRatio: e.target.value })} /><Input label="可用次数" type="number" value={redeemForm.totalTimes} onChange={(e) => setRedeemForm({ ...redeemForm, totalTimes: e.target.value })} /><Input label="生成数量" type="number" value={redeemForm.count} onChange={(e) => setRedeemForm({ ...redeemForm, count: e.target.value })} /></div>
      <textarea className="w-full min-h-24 rounded-md border border-default-200 bg-transparent px-3 py-2 text-sm" placeholder="兑换代码，可按行或逗号批量输入；留空则自动生成" value={redeemForm.codes} onChange={(e) => setRedeemForm({ ...redeemForm, codes: e.target.value })} />
      <Button color="primary" isLoading={saving} onClick={createRedeemCodes}>创建兑换码</Button>
    </CardBody></Card><div className="grid grid-cols-1 lg:grid-cols-2 gap-4">{redeemCodes.map((code) => <Card key={code.id} className="border border-gray-200 dark:border-default-200 shadow-sm"><CardBody className="space-y-2"><div className="flex items-center justify-between gap-2"><b className="break-all">{code.code}</b><Chip size="sm" color={code.status === 1 ? "success" : "default"}>{statusText(code.status)}</Chip></div><p className="text-sm text-default-500">{code.packageName} · {code.discountRatio}% · {code.usedTimes}/{code.totalTimes}</p><Button size="sm" color="danger" variant="flat" onClick={async () => { if (window.confirm("确认删除兑换码？")) { const res = await adminDeleteRedeemCode(code.id); res.code === 0 ? toast.success("已删除") : toast.error(res.msg || "删除失败"); reload(); } }}>删除</Button></CardBody></Card>)}</div></div>
  );
}
