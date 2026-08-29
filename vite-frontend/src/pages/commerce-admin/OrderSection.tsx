import { Button } from "@heroui/button";
import { Card, CardBody } from "@heroui/card";
import { Chip } from "@heroui/chip";
import toast from "react-hot-toast";

import { adminCompleteOrder } from "@/api";
import { OrderRecord } from "@/types";
import { money, orderStatusText, timeText } from "./constants";

interface OrderSectionProps {
  orders: OrderRecord[];
  reload: () => void;
}

export default function OrderSection({ orders, reload }: OrderSectionProps) {
  const completeOrderByAdmin = async (id: number) => {
    const res = await adminCompleteOrder(id);
    if (res.code !== 0) return toast.error(res.msg || "完成订单失败");
    toast.success("订单已完成并发放套餐");
    reload();
  };

  return (
    <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">{orders.map((order) => <Card key={order.id} className="border border-gray-200 dark:border-default-200 shadow-sm"><CardBody className="space-y-3"><div className="flex items-start justify-between gap-2"><div><h3 className="font-semibold break-all">{order.orderNo}</h3><p className="text-sm text-default-500">用户 #{order.userId} · {order.packageName}</p></div><Chip color={order.status === 1 ? "success" : "warning"}>{orderStatusText(order.status)}</Chip></div><div className="grid grid-cols-2 gap-2 text-sm"><span>原价 {money(order.originalAmount)}</span><span>应付 {money(order.payableAmount)}</span>{Number(order.inviteDeduction || 0) > 0 && <span className="text-success">余额抵扣 {money(order.inviteDeduction)}</span>}<span>已付 {money(order.paidAmount)}</span><span>支付 {order.paymentChannel || "-"}</span><span>流水 {order.providerTradeNo || "-"}</span><span>完成 {timeText(order.completedTime)}</span></div>{order.status === 0 && <Button size="sm" color="primary" onClick={() => completeOrderByAdmin(order.id)}>标记完成并发放</Button>}</CardBody></Card>)}</div>
  );
}
