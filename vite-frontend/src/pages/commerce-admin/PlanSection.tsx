import { Button } from "@heroui/button";
import { Card, CardBody, CardHeader } from "@heroui/card";
import { Chip } from "@heroui/chip";
import { Input } from "@heroui/input";
import { useState } from "react";
import toast from "react-hot-toast";

import { adminCreatePackagePlan, adminDeletePackagePlan, adminUpdatePackagePlan } from "@/api";
import { PackagePlan, UserGroup } from "@/types";
import { emptyPlanForm, money, statusText } from "./constants";

interface PlanSectionProps {
  plans: PackagePlan[];
  userGroups: UserGroup[];
  saving: boolean;
  setSaving: (value: boolean) => void;
  reload: () => void;
}

export default function PlanSection({ plans, userGroups, saving, setSaving, reload }: PlanSectionProps) {
  const [planForm, setPlanForm] = useState({ ...emptyPlanForm });

  const planPayload = () => ({
    ...(planForm.id ? { id: Number(planForm.id) } : {}),
    name: planForm.name.trim(),
    hidden: Number(planForm.hidden),
    price: Number(planForm.price),
    type: Number(planForm.type),
    durationMultiplier: Number(planForm.durationMultiplier),
    userGroupId: planForm.userGroupId ? Number(planForm.userGroupId) : null,
    flow: Number(planForm.flow),
    dailyFlow: Number(planForm.dailyFlow),
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
      reload();
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
    dailyFlow: String(plan.dailyFlow ?? 0),
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
    reload();
  };

  return (
    <div className="space-y-4">
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
            <Input label="每日流量限制（GiB，0为不限制）" type="number" value={planForm.dailyFlow} onChange={(e) => setPlanForm({ ...planForm, dailyFlow: e.target.value })} />
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
      <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
        {plans.map((plan) => (
          <Card key={plan.id} className="border border-gray-200 dark:border-default-200 shadow-sm">
            <CardBody className="space-y-3">
              <div className="flex items-start justify-between gap-3"><div><h3 className="text-base font-semibold">{plan.name}</h3><p className="text-sm text-default-500">{plan.description || "-"}</p></div><Chip color={plan.status === 1 ? "success" : "default"} size="sm">{statusText(plan.status)}</Chip></div>
              <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-sm"><span>价格 {money(plan.price)}</span><span>{plan.durationMultiplier}个月</span><span>{plan.flow} GiB</span><span>{plan.dailyFlow ? `日限 ${plan.dailyFlow} GiB` : "不限日流量"}</span><span>{plan.maxRules} 条规则</span><span>限速 {plan.speedMbps || 0} Mbps</span><span>IP {plan.ipLimit || 0}</span><span>连接 {plan.connectionLimit || 0}</span><span>{plan.hidden ? "已隐藏" : "公开"}</span></div>
              <div className="flex gap-2"><Button size="sm" variant="flat" onClick={() => editPlan(plan)}>编辑</Button><Button size="sm" color="danger" variant="flat" onClick={() => removePlan(plan.id)}>删除</Button></div>
            </CardBody>
          </Card>
        ))}
      </div>
    </div>
  );
}
