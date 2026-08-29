import { Button } from "@heroui/button";
import { Card, CardBody } from "@heroui/card";
import { Chip } from "@heroui/chip";
import { Spinner } from "@heroui/spinner";
import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import toast from "react-hot-toast";

import {
  adminGetDeviceGroups,
  adminGetPackagePlans,
  adminGetPaymentConfigs,
  adminGetRedeemCodes,
  adminGetUserGroups,
  getNodeList,
  getTunnelList,
} from "@/api";

export interface GuideStep {
  key: string;
  title: string;
  description: string;
  path: string;
  check: () => Promise<boolean>;
}

interface AdminListsResponse {
  code: number;
  data?: unknown[];
}

const asList = (res: { code: number; data?: unknown }): unknown[] => (Array.isArray(res.data) ? (res.data as unknown[]) : []);

const adminSteps: GuideStep[] = [
  {
    key: "tunnel",
    title: "创建隧道",
    description: "在隧道管理中连接入口节点与出口节点，建立可用的转发隧道。",
    path: "/tunnel",
    check: async () => {
      const res = (await getTunnelList()) as AdminListsResponse;
      return asList(res).length > 0;
    },
  },
  {
    key: "node",
    title: "接入节点",
    description: "添加转发节点并在服务器上执行安装命令，保持节点在线。",
    path: "/node",
    check: async () => {
      const res = (await getNodeList()) as AdminListsResponse;
      return asList(res).length > 0;
    },
  },
  {
    key: "device-group",
    title: "创建设备组",
    description: "把隧道绑定到设备组，设备组决定套餐可用哪些隧道。",
    path: "/commerce-admin",
    check: async () => {
      const res = (await adminGetDeviceGroups()) as AdminListsResponse;
      return asList(res).length > 0;
    },
  },
  {
    key: "user-group",
    title: "创建用户组",
    description: "用户组绑定设备组，套餐通过用户组向用户发放隧道权限。",
    path: "/commerce-admin",
    check: async () => {
      const res = (await adminGetUserGroups()) as AdminListsResponse;
      return asList(res).length > 0;
    },
  },
  {
    key: "plan",
    title: "上架套餐",
    description: "配置价格、流量、规则数与限速，上架后用户即可在套餐中心购买。",
    path: "/commerce-admin",
    check: async () => {
      const res = (await adminGetPackagePlans()) as AdminListsResponse;
      return asList(res).length > 0;
    },
  },
  {
    key: "redeem",
    title: "生成兑换码（可选）",
    description: "批量生成兑换码，用于发放隐藏套餐或线下销售。",
    path: "/commerce-admin",
    check: async () => {
      const res = (await adminGetRedeemCodes()) as AdminListsResponse;
      return asList(res).length > 0;
    },
  },
  {
    key: "payment",
    title: "配置支付方式",
    description: "启用 EasyPay / 支付宝 / 微信 / Stripe 任一渠道并填写密钥，订单即可自动到账。",
    path: "/commerce-admin",
    check: async () => {
      const res = (await adminGetPaymentConfigs()) as AdminListsResponse;
      const configs = asList(res) as { enabled?: boolean }[];
      return configs.some((item) => item.enabled);
    },
  },
];

const userSteps: GuideStep[] = [
  {
    key: "buy",
    title: "购买套餐",
    description: "在套餐中心选择合适套餐，支持邀请余额抵扣；付款到账后自动开通。",
    path: "/shop",
    check: async () => false,
  },
  {
    key: "forward",
    title: "创建转发",
    description: "在转发管理新建转发规则，选择隧道并设置入口端口即可使用。",
    path: "/forward",
    check: async () => false,
  },
  {
    key: "invite",
    title: "邀请返现",
    description: "在套餐中心-邀请页生成邀请链接，好友消费后自动返现到余额。",
    path: "/shop",
    check: async () => false,
  },
];

interface GuideChecklistProps {
  isAdmin: boolean;
  compact?: boolean;
  onSkip?: () => void;
}

export default function GuideChecklist({ isAdmin, compact = false, onSkip }: GuideChecklistProps) {
  const navigate = useNavigate();
  const [steps] = useState<GuideStep[]>(isAdmin ? adminSteps : userSteps);
  const [done, setDone] = useState<Record<string, boolean>>({});
  const [checking, setChecking] = useState(true);

  const refresh = useCallback(async () => {
    setChecking(true);
    const results: Record<string, boolean> = {};
    for (const step of steps) {
      try {
        results[step.key] = await step.check();
      } catch {
        results[step.key] = false;
      }
    }
    setDone(results);
    setChecking(false);
  }, [steps]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const list = steps as GuideStep[];
  const finished = list.filter((step) => done[step.key]).length;

  const renderStep = (step: GuideStep, index: number) => (
    <div key={step.key} className="flex items-start gap-3 rounded-lg border border-default-200 p-3">
      <div className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full border border-default-300 text-xs">
        {done[step.key] ? <span className="text-success">✓</span> : index + 1}
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <b className="text-sm">{step.title}</b>
          {done[step.key] && <Chip size="sm" color="success" variant="flat">已完成</Chip>}
        </div>
        <p className="mt-1 text-xs text-default-500">{step.description}</p>
      </div>
      {!done[step.key] && (
        <Button size="sm" color="primary" variant="flat" onClick={() => navigate(step.path)}>去完成</Button>
      )}
    </div>
  );

  return (
    <Card id="guide-checklist-anchor" className="border border-default-200 shadow-sm">
      <CardBody className="space-y-3">
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <h3 className="text-base font-semibold">{isAdmin ? "管理员部署向导" : "新手引导"}</h3>
            {checking ? <Spinner size="sm" /> : <Chip size="sm" color="primary" variant="flat">{finished}/{list.length}</Chip>}
          </div>
          <div className="flex gap-2">
            <Button size="sm" variant="flat" onClick={() => { toast.promise(refresh(), { loading: "检测中", success: "已刷新进度", error: "刷新失败" }); }}>刷新进度</Button>
            {onSkip && <Button size="sm" variant="light" onClick={onSkip}>关闭</Button>}
          </div>
        </div>
        {!compact && <p className="text-xs text-default-500">按顺序完成以下步骤，网站即可正式运营。</p>}
        <div className={compact ? "space-y-2" : "space-y-3"}>{list.map(renderStep)}</div>
      </CardBody>
    </Card>
  );
}
