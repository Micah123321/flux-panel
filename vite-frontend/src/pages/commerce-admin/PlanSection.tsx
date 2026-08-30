import { Button } from "@heroui/button";
import { Card, CardBody, CardHeader } from "@heroui/card";
import { Chip } from "@heroui/chip";
import { Input } from "@heroui/input";
import { Select, SelectItem } from "@heroui/select";
import type { Key } from "react";
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

const planTypeOptions = [
  { value: "1", label: "周期套餐" },
] as const;

const visibilityOptions = [
  { value: "0", label: "公开售卖" },
  { value: "1", label: "隐藏套餐" },
] as const;

const statusOptions = [
  { value: "1", label: "上架" },
  { value: "0", label: "下架" },
] as const;

const quotaFields = [
  { key: "flow", label: "总流量（GiB）" },
  { key: "dailyFlow", label: "每日流量限制（GiB，0为不限制）" },
  { key: "maxRules", label: "最大规则数（条）" },
  { key: "speedMbps", label: "用户限速（Mbps）" },
  { key: "ipLimit", label: "用户 IP 限制" },
  { key: "connectionLimit", label: "用户连接数限制" },
] as const;

type PlanForm = typeof emptyPlanForm;
type PlanFormKey = keyof PlanForm;

const selectedKeys = (value: string) => (value ? [value] : []);

const planTypeText = (type?: number) =>
  planTypeOptions.find((item) => Number(item.value) === Number(type || 1))?.label || "周期套餐";

const shortErrorMessage = (message?: string) => {
  if (!message) return "套餐保存失败";
  if (message.includes("daily_flow") || message.includes("Unknown column")) return "数据库缺少 daily_flow 字段，请先执行面板更新";
  return message.length > 80 ? `${message.slice(0, 80)}...` : message;
};

export default function PlanSection({ plans, userGroups, saving, setSaving, reload }: PlanSectionProps) {
  const [planForm, setPlanForm] = useState({ ...emptyPlanForm });
  const userGroupOptions = [
    { value: "none", label: "不分配" },
    ...userGroups.map((group) => ({ value: String(group.id), label: group.name })),
  ];

  const setField = (key: PlanFormKey, value: string) => {
    setPlanForm((prev) => ({ ...prev, [key]: value }));
  };

  const selectField = (key: PlanFormKey, fallback = "") => (keys: Set<Key> | "all") => {
    if (keys === "all") return;
    const value = Array.from(keys)[0];
    setField(key, value ? String(value) : fallback);
  };

  const selectUserGroup = (keys: Set<Key> | "all") => {
    if (keys === "all") return;
    const value = Array.from(keys)[0];
    setField("userGroupId", value && String(value) !== "none" ? String(value) : "");
  };

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
      if (res.code !== 0) {
        console.error("套餐保存失败:", res.msg);
        return toast.error(shortErrorMessage(res.msg));
      }
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
    <div className="space-y-5">
      <Card className="border border-default-200 shadow-sm">
        <CardHeader className="flex flex-col items-start gap-1 pb-2">
          <div className="flex w-full flex-col gap-2 md:flex-row md:items-center md:justify-between">
            <div>
              <h2 className="text-lg font-semibold">{planForm.id ? "编辑套餐" : "添加套餐"}</h2>
              <p className="text-sm text-default-500">{planTypeText(Number(planForm.type))} · {Number(planForm.durationMultiplier || 1)}个月 · {money(Number(planForm.price || 0))}</p>
            </div>
            {planForm.id && <Chip size="sm" variant="flat" color="primary">ID {planForm.id}</Chip>}
          </div>
        </CardHeader>
        <CardBody className="space-y-5">
          <div className="grid grid-cols-1 gap-5 xl:grid-cols-[1.05fr_1fr]">
            <section className="rounded-lg border border-default-200 p-4">
              <h3 className="mb-3 text-sm font-semibold text-default-700">基础信息</h3>
              <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                <Input label="套餐名称" variant="bordered" value={planForm.name} onChange={(e) => setField("name", e.target.value)} />
                <Input label="价格（元）" type="number" min={0} step={0.01} variant="bordered" value={planForm.price} onChange={(e) => setField("price", e.target.value)} />
                <Select label="套餐类型" variant="bordered" selectedKeys={selectedKeys(planForm.type)} onSelectionChange={selectField("type", "1")}>
                  {planTypeOptions.map((item) => <SelectItem key={item.value}>{item.label}</SelectItem>)}
                </Select>
                <Input label="时长（月）" type="number" min={1} step={1} variant="bordered" value={planForm.durationMultiplier} onChange={(e) => setField("durationMultiplier", e.target.value)} />
                <Select label="售卖状态" variant="bordered" selectedKeys={selectedKeys(planForm.status)} onSelectionChange={selectField("status", "1")}>
                  {statusOptions.map((item) => <SelectItem key={item.value}>{item.label}</SelectItem>)}
                </Select>
                <Select label="可见性" variant="bordered" selectedKeys={selectedKeys(planForm.hidden)} onSelectionChange={selectField("hidden", "0")}>
                  {visibilityOptions.map((item) => <SelectItem key={item.value}>{item.label}</SelectItem>)}
                </Select>
                <Select className="md:col-span-2" items={userGroupOptions} label="分配用户组" variant="bordered" selectedKeys={selectedKeys(planForm.userGroupId || "none")} onSelectionChange={selectUserGroup}>
                  {(item) => <SelectItem key={item.value}>{item.label}</SelectItem>}
                </Select>
              </div>
            </section>

            <section className="rounded-lg border border-default-200 p-4">
              <h3 className="mb-3 text-sm font-semibold text-default-700">权益限制</h3>
              <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                {quotaFields.map((field) => (
                  <Input
                    key={field.key}
                    label={field.label}
                    type="number"
                    min={0}
                    step={1}
                    variant="bordered"
                    value={planForm[field.key]}
                    onChange={(e) => setField(field.key, e.target.value)}
                  />
                ))}
              </div>
            </section>
          </div>

          <textarea
            className="min-h-24 w-full rounded-lg border border-default-200 bg-transparent px-3 py-2 text-sm outline-none transition-colors focus:border-primary"
            placeholder="套餐说明"
            value={planForm.description}
            onChange={(e) => setField("description", e.target.value)}
          />

          <div className="flex flex-col gap-2 sm:flex-row">
            <Button className="sm:w-auto" color="primary" isLoading={saving} onClick={savePlan}>保存套餐</Button>
            <Button className="sm:w-auto" variant="flat" onClick={() => setPlanForm({ ...emptyPlanForm })}>清空</Button>
          </div>
        </CardBody>
      </Card>

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
        {plans.map((plan) => (
          <Card key={plan.id} className="border border-default-200 shadow-sm">
            <CardBody className="space-y-3">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <h3 className="text-base font-semibold">{plan.name}</h3>
                    <Chip size="sm" variant="flat" color="primary">{planTypeText(plan.type)}</Chip>
                    <Chip size="sm" variant="flat" color={plan.hidden ? "warning" : "success"}>{plan.hidden ? "隐藏" : "公开"}</Chip>
                  </div>
                  <p className="mt-1 text-sm text-default-500 break-words">{plan.description || "-"}</p>
                </div>
                <Chip color={plan.status === 1 ? "success" : "default"} size="sm">{statusText(plan.status)}</Chip>
              </div>
              <div className="grid grid-cols-2 gap-2 text-sm md:grid-cols-4">
                <span>价格 {money(plan.price)}</span>
                <span>{plan.durationMultiplier}个月</span>
                <span>{plan.flow} GiB</span>
                <span>{plan.dailyFlow ? "日限 " + plan.dailyFlow + " GiB" : "不限日流量"}</span>
                <span>{plan.maxRules} 条规则</span>
                <span>限速 {plan.speedMbps || 0} Mbps</span>
                <span>IP {plan.ipLimit || 0}</span>
                <span>连接 {plan.connectionLimit || 0}</span>
              </div>
              <div className="flex flex-wrap gap-2">
                <Button size="sm" variant="flat" onClick={() => editPlan(plan)}>编辑</Button>
                <Button size="sm" color="danger" variant="flat" onClick={() => removePlan(plan.id)}>删除</Button>
              </div>
            </CardBody>
          </Card>
        ))}
      </div>
    </div>
  );
}
