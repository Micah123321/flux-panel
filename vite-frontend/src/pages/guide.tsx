import { Button } from "@heroui/button";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

import GuideChecklist from "@/components/guide-checklist";
import { startTourManually, markTourSeen } from "@/utils/tour";

export default function GuidePage() {
  const navigate = useNavigate();
  const [isAdmin, setIsAdmin] = useState(false);

  useEffect(() => {
    setIsAdmin(localStorage.getItem("admin") === "true");
  }, []);

  return (
    <div className="mx-auto max-w-3xl px-3 py-6 space-y-4">
      <div className="space-y-1">
        <h1 className="text-2xl font-semibold">使用向导</h1>
        <p className="text-sm text-default-500">{isAdmin ? "从零把网站跑起来：隧道 → 节点 → 设备组/用户组 → 套餐 → 支付。" : "三步上手：购买套餐 → 创建转发 → 邀请返现。"}</p>
      </div>
      <GuideChecklist isAdmin={isAdmin} />
      {isAdmin && (
        <Button
          variant="flat"
          onClick={() => {
            markTourSeen("shop_tour_v1");
            markTourSeen("forward_tour_v1");
            startTourManually("guide_page_tour_v1", [
              { element: "#guide-checklist-anchor", popover: { title: "部署清单", description: "这里会实时检测每一步是否完成，随时回来看进度。" } },
            ]);
          }}
        >
          重看页面引导
        </Button>
      )}
      <Button variant="light" onClick={() => navigate(-1)}>返回</Button>
    </div>
  );
}
