import { Button } from "@heroui/button";
import { Card, CardBody, CardHeader } from "@heroui/card";
import { Input } from "@heroui/input";
import toast from "react-hot-toast";

import { adminUpdateInviteConfig } from "@/api";
import { InviteRecordsData } from "@/types";
import { money, timeText } from "./constants";

interface InviteSectionProps {
  inviteData: InviteRecordsData;
  inviteConfig: { inviteRatio: string; inviteRenewalRatio: string };
  setInviteConfig: (value: { inviteRatio: string; inviteRenewalRatio: string }) => void;
  reload: () => void;
}

export default function InviteSection({ inviteData, inviteConfig, setInviteConfig, reload }: InviteSectionProps) {
  const saveInviteConfig = async () => {
    const res = await adminUpdateInviteConfig({
      inviteRatio: Number(inviteConfig.inviteRatio),
      inviteRenewalRatio: Number(inviteConfig.inviteRenewalRatio),
    });
    if (res.code !== 0) return toast.error(res.msg || "邀请配置保存失败");
    toast.success("邀请配置已保存");
    reload();
  };

  return (
    <div className="space-y-4"><Card className="border border-gray-200 dark:border-default-200 shadow-sm"><CardHeader><h2 className="text-lg font-semibold">邀请比例</h2></CardHeader><CardBody className="grid grid-cols-1 md:grid-cols-3 gap-4"><Input label="首次购买返现比例（%）" type="number" value={inviteConfig.inviteRatio} onChange={(e) => setInviteConfig({ ...inviteConfig, inviteRatio: e.target.value })} /><Input label="被邀请续费返现比例（%）" type="number" value={inviteConfig.inviteRenewalRatio} onChange={(e) => setInviteConfig({ ...inviteConfig, inviteRenewalRatio: e.target.value })} /><div className="flex items-end"><Button color="primary" onClick={saveInviteConfig}>保存配置</Button></div></CardBody></Card><div className="grid grid-cols-1 xl:grid-cols-2 gap-4"><Card className="border border-gray-200 dark:border-default-200 shadow-sm"><CardHeader><h2 className="text-lg font-semibold">邀请记录</h2></CardHeader><CardBody className="space-y-2">{inviteData.invites.map((item) => <div key={item.id} className="rounded-md border border-default-200 p-3 text-sm">邀请人 #{item.inviterUserId} 邀请用户 #{item.inviteeUserId}<br /><span className="text-default-500">{item.inviteCode} · {timeText(item.createdTime)}</span></div>)}</CardBody></Card><Card className="border border-gray-200 dark:border-default-200 shadow-sm"><CardHeader><h2 className="text-lg font-semibold">返现记录</h2></CardHeader><CardBody className="space-y-2">{inviteData.rewards.map((item) => <div key={item.id} className="rounded-md border border-default-200 p-3 text-sm">邀请人 #{item.inviterUserId} 获得 {money(item.rewardAmount)}<br /><span className="text-default-500">订单 #{item.orderId} · {item.ratio}% · {timeText(item.createdTime)}</span></div>)}</CardBody></Card></div></div>
  );
}
