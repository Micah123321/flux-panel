import { useEffect, useState } from "react";
import { Card, CardBody, CardHeader } from "@heroui/card";
import { Button } from "@heroui/button";
import { Input, Textarea } from "@heroui/input";
import { Select, SelectItem } from "@heroui/select";
import { Modal, ModalContent, ModalHeader, ModalBody, ModalFooter } from "@heroui/modal";
import { Chip } from "@heroui/chip";
import { Spinner } from "@heroui/spinner";
import toast from "react-hot-toast";

import {
  createAggregateForward,
  createAggregateNodeGroup,
  deleteAggregateForward,
  deleteAggregateNodeGroup,
  getAggregateForwards,
  getAggregateNodeGroups,
  getNodeList,
  pauseAggregateForward,
  resumeAggregateForward,
  updateAggregateForward,
  updateAggregateNodeGroup,
} from "@/api";

const MAX_PORT_SPAN = 200;

interface NodeItem {
  id: number;
  name: string;
  ip?: string;
  serverIp?: string;
  portSta?: number;
  portEnd?: number;
  status: number;
}

interface AggregateNodeGroup {
  id: number;
  name: string;
  nodeIds: number[];
  nodes: NodeItem[];
  remark?: string;
  status: number;
}

interface AggregateForward {
  id: number;
  name: string;
  entryGroupId: number;
  exitGroupId: number;
  entryGroupName?: string;
  exitGroupName?: string;
  entryAddresses: string;
  entryAddressList?: string[];
  entryPortStart: number;
  entryPortEnd: number;
  targetPortStart: number;
  targetPortEnd: number;
  mode: "load_balance" | "failover";
  trafficRatio: number;
  inFlow?: number;
  outFlow?: number;
  interfaceName?: string;
  remark?: string;
  status: number;
  serviceCount?: number;
  accessAddresses?: string[];
}

interface GroupForm {
  id?: number;
  name: string;
  nodeIds: number[];
  remark: string;
}

interface ForwardForm {
  id?: number;
  name: string;
  entryGroupId: number | null;
  exitGroupId: number | null;
  entryAddresses: string;
  entryPortStart: number | null;
  entryPortEnd: number | null;
  targetPortStart: number | null;
  targetPortEnd: number | null;
  mode: "load_balance" | "failover";
  trafficRatio: number;
  interfaceName: string;
  remark: string;
}

type DeleteTarget = { type: "group"; item: AggregateNodeGroup } | { type: "forward"; item: AggregateForward };

const emptyGroupForm: GroupForm = { name: "", nodeIds: [], remark: "" };
const emptyForwardForm: ForwardForm = {
  name: "",
  entryGroupId: null,
  exitGroupId: null,
  entryAddresses: "",
  entryPortStart: null,
  entryPortEnd: null,
  targetPortStart: null,
  targetPortEnd: null,
  mode: "load_balance",
  trafficRatio: 1,
  interfaceName: "",
  remark: "",
};

const modeLabel = (mode: AggregateForward["mode"]) => mode === "load_balance" ? "负载均衡" : "主备切换";
const modeColor = (mode: AggregateForward["mode"]) => mode === "load_balance" ? "primary" : "warning";
const formatBytes = (bytes?: number) => {
  const value = bytes || 0;
  if (value >= 1024 ** 3) return `${(value / 1024 ** 3).toFixed(2)} GB`;
  if (value >= 1024 ** 2) return `${(value / 1024 ** 2).toFixed(2)} MB`;
  if (value >= 1024) return `${(value / 1024).toFixed(2)} KB`;
  return `${value} B`;
};

export default function AggregateForwardPage() {
  const [loading, setLoading] = useState(true);
  const [submitLoading, setSubmitLoading] = useState(false);
  const [actionLoadingId, setActionLoadingId] = useState<number | null>(null);
  const [nodes, setNodes] = useState<NodeItem[]>([]);
  const [groups, setGroups] = useState<AggregateNodeGroup[]>([]);
  const [forwards, setForwards] = useState<AggregateForward[]>([]);
  const [groupModalOpen, setGroupModalOpen] = useState(false);
  const [forwardModalOpen, setForwardModalOpen] = useState(false);
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);
  const [groupForm, setGroupForm] = useState<GroupForm>(emptyGroupForm);
  const [forwardForm, setForwardForm] = useState<ForwardForm>(emptyForwardForm);
  const [groupErrors, setGroupErrors] = useState<Record<string, string>>({});
  const [forwardErrors, setForwardErrors] = useState<Record<string, string>>({});
  const [deleteTarget, setDeleteTarget] = useState<DeleteTarget | null>(null);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    setLoading(true);
    try {
      const [nodeRes, groupRes, forwardRes] = await Promise.all([
        getNodeList(),
        getAggregateNodeGroups(),
        getAggregateForwards(),
      ]);
      if (nodeRes.code === 0) setNodes(nodeRes.data || []);
      else toast.error(nodeRes.msg || "获取节点失败");
      if (groupRes.code === 0) setGroups(groupRes.data || []);
      else toast.error(groupRes.msg || "获取节点组失败");
      if (forwardRes.code === 0) setForwards(forwardRes.data || []);
      else toast.error(forwardRes.msg || "获取聚合转发失败");
    } catch (error) {
      console.error("加载聚合转发数据失败:", error);
      toast.error("加载数据失败");
    } finally {
      setLoading(false);
    }
  };

  const openCreateGroup = () => {
    setGroupForm(emptyGroupForm);
    setGroupErrors({});
    setGroupModalOpen(true);
  };

  const openEditGroup = (group: AggregateNodeGroup) => {
    setGroupForm({ id: group.id, name: group.name, nodeIds: group.nodeIds || [], remark: group.remark || "" });
    setGroupErrors({});
    setGroupModalOpen(true);
  };

  const openCreateForward = () => {
    setForwardForm(emptyForwardForm);
    setForwardErrors({});
    setForwardModalOpen(true);
  };

  const openEditForward = (forward: AggregateForward) => {
    setForwardForm({
      id: forward.id,
      name: forward.name,
      entryGroupId: forward.entryGroupId,
      exitGroupId: forward.exitGroupId,
      entryAddresses: forward.entryAddresses,
      entryPortStart: forward.entryPortStart,
      entryPortEnd: forward.entryPortEnd,
      targetPortStart: forward.targetPortStart,
      targetPortEnd: forward.targetPortEnd,
      mode: forward.mode,
      trafficRatio: Number(forward.trafficRatio || 1),
      interfaceName: forward.interfaceName || "",
      remark: forward.remark || "",
    });
    setForwardErrors({});
    setForwardModalOpen(true);
  };

  const toggleGroupNode = (nodeId: number) => {
    setGroupForm((prev) => {
      const selected = prev.nodeIds.includes(nodeId);
      const nodeIds = selected ? prev.nodeIds.filter((id) => id !== nodeId) : [...prev.nodeIds, nodeId];
      return { ...prev, nodeIds };
    });
  };

  const validateGroupForm = () => {
    const errors: Record<string, string> = {};
    if (!groupForm.name.trim()) errors.name = "请输入节点组名称";
    if (groupForm.nodeIds.length === 0) errors.nodeIds = "请选择节点";
    setGroupErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const validateForwardForm = () => {
    const errors: Record<string, string> = {};
    if (!forwardForm.name.trim()) errors.name = "请输入聚合转发名称";
    if (!forwardForm.entryGroupId) errors.entryGroupId = "请选择入口节点组";
    if (!forwardForm.exitGroupId) errors.exitGroupId = "请选择出口节点组";
    if (!forwardForm.entryAddresses.trim()) errors.entryAddresses = "请输入入口IP或域名";
    validatePortRange(forwardForm.entryPortStart, forwardForm.entryPortEnd, "entryPort", errors);
    validatePortRange(forwardForm.targetPortStart, forwardForm.targetPortEnd, "targetPort", errors);
    if (forwardForm.entryPortStart && forwardForm.entryPortEnd && forwardForm.targetPortStart && forwardForm.targetPortEnd) {
      const entrySpan = forwardForm.entryPortEnd - forwardForm.entryPortStart + 1;
      const targetSpan = forwardForm.targetPortEnd - forwardForm.targetPortStart + 1;
      if (entrySpan !== targetSpan) errors.targetPort = "入口和出口端口数量必须一致";
      if (entrySpan > MAX_PORT_SPAN) errors.entryPort = `一次最多支持 ${MAX_PORT_SPAN} 个端口`;
    }
    if (!Number.isFinite(forwardForm.trafficRatio) || forwardForm.trafficRatio < 0.1) {
      errors.trafficRatio = "倍率不能小于0.1";
    }
    setForwardErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const validatePortRange = (start: number | null, end: number | null, key: string, errors: Record<string, string>) => {
    if (!start || !end) {
      errors[key] = "请输入端口范围";
      return;
    }
    if (start < 1 || start > 65535 || end < 1 || end > 65535 || end < start) {
      errors[key] = "端口范围必须在1-65535内";
    }
  };

  const submitGroup = async () => {
    if (!validateGroupForm()) return;
    setSubmitLoading(true);
    try {
      const res = groupForm.id ? await updateAggregateNodeGroup(groupForm) : await createAggregateNodeGroup(groupForm);
      if (res.code === 0) {
        toast.success(groupForm.id ? "节点组已更新" : "节点组已创建");
        setGroupModalOpen(false);
        loadData();
      } else {
        toast.error(res.msg || "操作失败");
      }
    } catch (error) {
      console.error("保存节点组失败:", error);
      toast.error("操作失败");
    } finally {
      setSubmitLoading(false);
    }
  };

  const submitForward = async () => {
    if (!validateForwardForm()) return;
    setSubmitLoading(true);
    try {
      const res = forwardForm.id ? await updateAggregateForward(forwardForm) : await createAggregateForward(forwardForm);
      if (res.code === 0) {
        toast.success(forwardForm.id ? "聚合转发已更新" : "聚合转发已创建");
        setForwardModalOpen(false);
        loadData();
      } else {
        toast.error(res.msg || "操作失败");
      }
    } catch (error) {
      console.error("保存聚合转发失败:", error);
      toast.error("操作失败");
    } finally {
      setSubmitLoading(false);
    }
  };

  const confirmDelete = async () => {
    if (!deleteTarget) return;
    setActionLoadingId(deleteTarget.item.id);
    try {
      const res = deleteTarget.type === "group"
        ? await deleteAggregateNodeGroup(deleteTarget.item.id)
        : await deleteAggregateForward(deleteTarget.item.id);
      if (res.code === 0) {
        toast.success("删除成功");
        setDeleteModalOpen(false);
        setDeleteTarget(null);
        loadData();
      } else {
        toast.error(res.msg || "删除失败");
      }
    } catch (error) {
      console.error("删除失败:", error);
      toast.error("删除失败");
    } finally {
      setActionLoadingId(null);
    }
  };

  const toggleForwardStatus = async (forward: AggregateForward) => {
    setActionLoadingId(forward.id);
    try {
      const res = forward.status === 1 ? await pauseAggregateForward(forward.id) : await resumeAggregateForward(forward.id);
      if (res.code === 0) {
        toast.success(forward.status === 1 ? "已暂停" : "已恢复");
        loadData();
      } else {
        toast.error(res.msg || "操作失败");
      }
    } catch (error) {
      console.error("切换聚合转发状态失败:", error);
      toast.error("操作失败");
    } finally {
      setActionLoadingId(null);
    }
  };

  const openDelete = (target: DeleteTarget) => {
    setDeleteTarget(target);
    setDeleteModalOpen(true);
  };

  const numberValue = (value: number | null) => value === null ? "" : value.toString();
  const setNumber = (value: string) => value ? Number(value) : null;

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="flex items-center gap-3">
          <Spinner size="sm" />
          <span className="text-default-600">正在加载...</span>
        </div>
      </div>
    );
  }

  return (
    <div className="px-3 lg:px-6 py-8 space-y-8">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold text-foreground">聚合转发</h1>
          <p className="text-sm text-default-500 mt-1">节点组、入口和模式统一管理</p>
        </div>
        <div className="flex gap-2">
          <Button color="primary" variant="flat" onPress={openCreateGroup}>新增节点组</Button>
          <Button color="primary" onPress={openCreateForward}>新增聚合转发</Button>
        </div>
      </div>

      <section className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="text-base font-semibold text-foreground">节点组</h2>
          <Chip size="sm" variant="flat">{groups.length} 组</Chip>
        </div>
        {groups.length > 0 ? (
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
            {groups.map((group) => (
              <Card key={group.id} className="shadow-sm border border-divider">
                <CardHeader className="pb-2">
                  <div className="flex items-start justify-between w-full gap-3">
                    <div className="min-w-0">
                      <h3 className="font-semibold text-foreground truncate">{group.name}</h3>
                      <p className="text-xs text-default-500 mt-1">{group.nodes?.length || 0} 个节点</p>
                    </div>
                    <Chip size="sm" color="success" variant="flat">启用</Chip>
                  </div>
                </CardHeader>
                <CardBody className="space-y-4">
                  <div className="flex flex-wrap gap-2">
                    {(group.nodes || []).map((node) => (
                      <Chip key={node.id} size="sm" variant="flat" color={node.status === 1 ? "success" : "default"}>
                        {node.name}
                      </Chip>
                    ))}
                  </div>
                  {group.remark && <p className="text-sm text-default-500 break-words">{group.remark}</p>}
                  <div className="flex gap-2">
                    <Button size="sm" variant="flat" className="flex-1" onPress={() => openEditGroup(group)}>编辑</Button>
                    <Button size="sm" variant="flat" color="danger" className="flex-1" onPress={() => openDelete({ type: "group", item: group })}>删除</Button>
                  </div>
                </CardBody>
              </Card>
            ))}
          </div>
        ) : (
          <Card className="shadow-sm border border-divider"><CardBody className="text-center py-10 text-default-500">暂无节点组</CardBody></Card>
        )}
      </section>

      <section className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="text-base font-semibold text-foreground">转发规则</h2>
          <Chip size="sm" variant="flat">{forwards.length} 条</Chip>
        </div>
        {forwards.length > 0 ? (
          <div className="grid grid-cols-1 lg:grid-cols-2 2xl:grid-cols-3 gap-4">
            {forwards.map((forward) => (
              <Card key={forward.id} className="shadow-sm border border-divider">
                <CardHeader className="pb-2">
                  <div className="flex items-start justify-between w-full gap-3">
                    <div className="min-w-0">
                      <h3 className="font-semibold text-foreground truncate">{forward.name}</h3>
                      <p className="text-xs text-default-500 mt-1">{forward.entryGroupName || "入口组缺失"} → {forward.exitGroupName || "出口组缺失"}</p>
                    </div>
                    <Chip size="sm" color={forward.status === 1 ? "success" : "default"} variant="flat">{forward.status === 1 ? "运行" : "暂停"}</Chip>
                  </div>
                </CardHeader>
                <CardBody className="space-y-4">
                  <div className="grid grid-cols-2 gap-3 text-sm">
                    <Info label="入口端口" value={`${forward.entryPortStart}-${forward.entryPortEnd}`} />
                    <Info label="出口端口" value={`${forward.targetPortStart}-${forward.targetPortEnd}`} />
                    <Info label="模式" value={modeLabel(forward.mode)} />
                    <Info label="倍率" value={`${forward.trafficRatio || 1}x`} />
                    <Info label="入站流量" value={formatBytes(forward.inFlow)} />
                    <Info label="出站流量" value={formatBytes(forward.outFlow)} />
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <Chip size="sm" variant="flat" color={modeColor(forward.mode) as any}>{modeLabel(forward.mode)}</Chip>
                    <Chip size="sm" variant="flat">{forward.serviceCount || 0} 服务</Chip>
                  </div>
                  <div className="space-y-2">
                    {(forward.accessAddresses || []).slice(0, 4).map((address) => (
                      <code key={address} className="block px-2 py-1 rounded bg-default-100 text-xs text-foreground truncate">{address}</code>
                    ))}
                    {(forward.accessAddresses?.length || 0) > 4 && <p className="text-xs text-default-500">还有 {(forward.accessAddresses?.length || 0) - 4} 个入口</p>}
                  </div>
                  {forward.remark && <p className="text-sm text-default-500 break-words">{forward.remark}</p>}
                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                    <Button size="sm" variant="flat" onPress={() => openEditForward(forward)}>编辑</Button>
                    <Button size="sm" variant="flat" color={forward.status === 1 ? "warning" : "success"} isLoading={actionLoadingId === forward.id} onPress={() => toggleForwardStatus(forward)}>
                      {forward.status === 1 ? "暂停" : "恢复"}
                    </Button>
                    <Button size="sm" variant="flat" color="danger" className="sm:col-span-2" onPress={() => openDelete({ type: "forward", item: forward })}>删除</Button>
                  </div>
                </CardBody>
              </Card>
            ))}
          </div>
        ) : (
          <Card className="shadow-sm border border-divider"><CardBody className="text-center py-10 text-default-500">暂无聚合转发</CardBody></Card>
        )}
      </section>

      <Modal isOpen={groupModalOpen} onOpenChange={setGroupModalOpen} size="2xl" scrollBehavior="outside" backdrop="blur">
        <ModalContent>
          {(onClose) => (
            <>
              <ModalHeader>{groupForm.id ? "编辑节点组" : "新增节点组"}</ModalHeader>
              <ModalBody className="space-y-4">
                <Input label="节点组名称" value={groupForm.name} onChange={(e) => setGroupForm((prev) => ({ ...prev, name: e.target.value }))} isInvalid={!!groupErrors.name} errorMessage={groupErrors.name} variant="bordered" />
                <div className="space-y-2">
                  <div className="text-sm text-default-700">成员节点</div>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 max-h-72 overflow-y-auto rounded border border-divider p-3">
                    {nodes.map((node) => (
                      <label key={node.id} className="flex items-start gap-3 rounded border border-divider px-3 py-2 cursor-pointer hover:bg-default-50">
                        <input
                          type="checkbox"
                          checked={groupForm.nodeIds.includes(node.id)}
                          onChange={() => toggleGroupNode(node.id)}
                          className="mt-1 h-4 w-4 accent-primary"
                        />
                        <span className="min-w-0">
                          <span className="block text-sm font-medium truncate">{node.name}</span>
                          <span className="block text-xs text-default-500 truncate">{node.serverIp || node.ip || "-"} · {node.portSta || "?"}-{node.portEnd || "?"}</span>
                        </span>
                      </label>
                    ))}
                  </div>
                  {groupErrors.nodeIds && <p className="text-xs text-danger">{groupErrors.nodeIds}</p>}
                </div>
                <Textarea label="备注" value={groupForm.remark} onChange={(e) => setGroupForm((prev) => ({ ...prev, remark: e.target.value }))} variant="bordered" minRows={2} />
              </ModalBody>
              <ModalFooter>
                <Button variant="light" onPress={onClose}>取消</Button>
                <Button color="primary" isLoading={submitLoading} onPress={submitGroup}>{groupForm.id ? "保存" : "创建"}</Button>
              </ModalFooter>
            </>
          )}
        </ModalContent>
      </Modal>

      <Modal isOpen={forwardModalOpen} onOpenChange={setForwardModalOpen} size="3xl" scrollBehavior="outside" backdrop="blur">
        <ModalContent>
          {(onClose) => (
            <>
              <ModalHeader>{forwardForm.id ? "编辑聚合转发" : "新增聚合转发"}</ModalHeader>
              <ModalBody className="space-y-4">
                <Input label="名称" value={forwardForm.name} onChange={(e) => setForwardForm((prev) => ({ ...prev, name: e.target.value }))} isInvalid={!!forwardErrors.name} errorMessage={forwardErrors.name} variant="bordered" />
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <GroupSelect label="入口节点组" value={forwardForm.entryGroupId} groups={groups} error={forwardErrors.entryGroupId} onChange={(entryGroupId) => setForwardForm((prev) => ({ ...prev, entryGroupId }))} />
                  <GroupSelect label="出口节点组" value={forwardForm.exitGroupId} groups={groups} error={forwardErrors.exitGroupId} onChange={(exitGroupId) => setForwardForm((prev) => ({ ...prev, exitGroupId }))} />
                </div>
                <Textarea label="入口IP或域名" value={forwardForm.entryAddresses} onChange={(e) => setForwardForm((prev) => ({ ...prev, entryAddresses: e.target.value }))} isInvalid={!!forwardErrors.entryAddresses} errorMessage={forwardErrors.entryAddresses} variant="bordered" minRows={2} />
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <PortRangeInputs label="入口端口" start={forwardForm.entryPortStart} end={forwardForm.entryPortEnd} error={forwardErrors.entryPort} onStartChange={(entryPortStart) => setForwardForm((prev) => ({ ...prev, entryPortStart }))} onEndChange={(entryPortEnd) => setForwardForm((prev) => ({ ...prev, entryPortEnd }))} numberValue={numberValue} setNumber={setNumber} />
                  <PortRangeInputs label="出口端口" start={forwardForm.targetPortStart} end={forwardForm.targetPortEnd} error={forwardErrors.targetPort} onStartChange={(targetPortStart) => setForwardForm((prev) => ({ ...prev, targetPortStart }))} onEndChange={(targetPortEnd) => setForwardForm((prev) => ({ ...prev, targetPortEnd }))} numberValue={numberValue} setNumber={setNumber} />
                </div>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  <Select label="模式" selectedKeys={[forwardForm.mode]} onSelectionChange={(keys) => setForwardForm((prev) => ({ ...prev, mode: Array.from(keys)[0] as ForwardForm["mode"] }))} variant="bordered">
                    <SelectItem key="load_balance">负载均衡</SelectItem>
                    <SelectItem key="failover">主备切换</SelectItem>
                  </Select>
                  <Input label="倍率" type="number" min={0.1} step={0.1} value={forwardForm.trafficRatio.toString()} onChange={(e) => setForwardForm((prev) => ({ ...prev, trafficRatio: Number(e.target.value) }))} isInvalid={!!forwardErrors.trafficRatio} errorMessage={forwardErrors.trafficRatio} variant="bordered" />
                  <Input label="出口网卡" value={forwardForm.interfaceName} onChange={(e) => setForwardForm((prev) => ({ ...prev, interfaceName: e.target.value }))} variant="bordered" />
                </div>
                <Textarea label="备注" value={forwardForm.remark} onChange={(e) => setForwardForm((prev) => ({ ...prev, remark: e.target.value }))} variant="bordered" minRows={2} />
              </ModalBody>
              <ModalFooter>
                <Button variant="light" onPress={onClose}>取消</Button>
                <Button color="primary" isLoading={submitLoading} onPress={submitForward}>{forwardForm.id ? "保存" : "创建"}</Button>
              </ModalFooter>
            </>
          )}
        </ModalContent>
      </Modal>

      <Modal isOpen={deleteModalOpen} onOpenChange={setDeleteModalOpen} size="md" backdrop="blur">
        <ModalContent>
          {(onClose) => (
            <>
              <ModalHeader>确认删除</ModalHeader>
              <ModalBody>
                <p className="text-sm text-default-600">确定要删除 {deleteTarget?.type === "group" ? "节点组" : "聚合转发"} <span className="font-semibold text-foreground">{deleteTarget?.item.name}</span> 吗？</p>
              </ModalBody>
              <ModalFooter>
                <Button variant="light" onPress={onClose}>取消</Button>
                <Button color="danger" isLoading={actionLoadingId === deleteTarget?.item.id} onPress={confirmDelete}>删除</Button>
              </ModalFooter>
            </>
          )}
        </ModalContent>
      </Modal>
    </div>
  );
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded border border-divider px-3 py-2">
      <div className="text-xs text-default-500">{label}</div>
      <div className="text-sm font-medium text-foreground truncate">{value}</div>
    </div>
  );
}

function GroupSelect({ label, value, groups, error, onChange }: { label: string; value: number | null; groups: AggregateNodeGroup[]; error?: string; onChange: (value: number | null) => void }) {
  return (
    <Select
      label={label}
      selectedKeys={value ? [value.toString()] : []}
      onSelectionChange={(keys) => {
        const selectedKey = Array.from(keys)[0] as string | undefined;
        onChange(selectedKey ? Number(selectedKey) : null);
      }}
      isInvalid={!!error}
      errorMessage={error}
      variant="bordered"
    >
      {groups.map((group) => (
        <SelectItem key={group.id}>{group.name}</SelectItem>
      ))}
    </Select>
  );
}

function PortRangeInputs({
  label,
  start,
  end,
  error,
  onStartChange,
  onEndChange,
  numberValue,
  setNumber,
}: {
  label: string;
  start: number | null;
  end: number | null;
  error?: string;
  onStartChange: (value: number | null) => void;
  onEndChange: (value: number | null) => void;
  numberValue: (value: number | null) => string;
  setNumber: (value: string) => number | null;
}) {
  return (
    <div className="space-y-2">
      <div className="grid grid-cols-2 gap-3">
        <Input label={`${label}起始`} type="number" value={numberValue(start)} onChange={(e) => onStartChange(setNumber(e.target.value))} isInvalid={!!error} variant="bordered" />
        <Input label={`${label}结束`} type="number" value={numberValue(end)} onChange={(e) => onEndChange(setNumber(e.target.value))} isInvalid={!!error} variant="bordered" />
      </div>
      {error && <p className="text-xs text-danger">{error}</p>}
    </div>
  );
}
