import { useEffect, useMemo, useState } from "react";
import { Card, CardBody, CardHeader } from "@heroui/card";
import { Button } from "@heroui/button";
import { Input, Textarea } from "@heroui/input";
import { Modal, ModalContent, ModalHeader, ModalBody, ModalFooter } from "@heroui/modal";
import { Chip } from "@heroui/chip";
import { Spinner } from "@heroui/spinner";
import toast from "react-hot-toast";

import {
  createAggregateNodeGroup,
  deleteAggregateNodeGroup,
  getAggregateNodeGroups,
  getNodeList,
  updateAggregateNodeGroup,
} from "@/api";

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

interface GroupForm {
  id?: number;
  name: string;
  nodeIds: number[];
  remark: string;
}

const emptyGroupForm: GroupForm = { name: "", nodeIds: [], remark: "" };

const asNumberArray = (value: unknown): number[] => {
  if (!Array.isArray(value)) return [];
  return value
    .map((item) => Number(item))
    .filter((item) => Number.isFinite(item));
};

const nodeAddress = (node: NodeItem) => node.serverIp || node.ip || "-";
const portRangeText = (node: NodeItem) => `${node.portSta || "?"}-${node.portEnd || "?"}`;

const commonPortRange = (nodes: NodeItem[]) => {
  if (nodes.length === 0) return "-";
  const starts = nodes.map((node) => node.portSta).filter((value): value is number => Number.isFinite(value));
  const ends = nodes.map((node) => node.portEnd).filter((value): value is number => Number.isFinite(value));
  if (starts.length !== nodes.length || ends.length !== nodes.length) return "-";
  const start = Math.max(...starts);
  const end = Math.min(...ends);
  return start <= end ? `${start}-${end}` : "无共同端口";
};

export default function AggregateForwardPage() {
  const [loading, setLoading] = useState(true);
  const [submitLoading, setSubmitLoading] = useState(false);
  const [deleteLoading, setDeleteLoading] = useState(false);
  const [nodes, setNodes] = useState<NodeItem[]>([]);
  const [groups, setGroups] = useState<AggregateNodeGroup[]>([]);
  const [groupModalOpen, setGroupModalOpen] = useState(false);
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);
  const [groupForm, setGroupForm] = useState<GroupForm>(emptyGroupForm);
  const [groupErrors, setGroupErrors] = useState<Record<string, string>>({});
  const [groupToDelete, setGroupToDelete] = useState<AggregateNodeGroup | null>(null);

  const nodeById = useMemo(() => new Map(nodes.map((node) => [node.id, node])), [nodes]);

  const resolveGroupNodes = (group: AggregateNodeGroup) => {
    const groupNodes = new Map((group.nodes || []).map((node) => [node.id, node]));
    const ids = group.nodeIds?.length ? group.nodeIds : (group.nodes || []).map((node) => node.id);
    return ids
      .map((id) => {
        const base = nodeById.get(id);
        const groupNode = groupNodes.get(id);
        if (!base && !groupNode) return null;
        return { ...(groupNode || {}), ...(base || {}), id } as NodeItem;
      })
      .filter((node): node is NodeItem => Boolean(node));
  };

  const loadData = async () => {
    setLoading(true);
    try {
      const [nodesRes, groupsRes] = await Promise.all([getNodeList(), getAggregateNodeGroups()]);
      if (nodesRes.code === 0) {
        setNodes(nodesRes.data || []);
      } else {
        toast.error(nodesRes.msg || "获取节点列表失败");
      }
      if (groupsRes.code === 0) {
        setGroups((groupsRes.data || []).map((group: any) => ({
          ...group,
          nodeIds: asNumberArray(group.nodeIds),
          nodes: group.nodes || [],
        })));
      } else {
        toast.error(groupsRes.msg || "获取节点组失败");
      }
    } catch (error) {
      console.error("加载节点组失败:", error);
      toast.error("加载失败");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const openCreateGroup = () => {
    setGroupForm(emptyGroupForm);
    setGroupErrors({});
    setGroupModalOpen(true);
  };

  const openEditGroup = (group: AggregateNodeGroup) => {
    const nodeIds = group.nodeIds?.length ? group.nodeIds : resolveGroupNodes(group).map((node) => node.id);
    setGroupForm({
      id: group.id,
      name: group.name,
      nodeIds,
      remark: group.remark || "",
    });
    setGroupErrors({});
    setGroupModalOpen(true);
  };

  const validateGroup = () => {
    const errors: Record<string, string> = {};
    if (!groupForm.name.trim()) errors.name = "请输入节点组名称";
    if (groupForm.nodeIds.length === 0) errors.nodeIds = "请选择成员节点";
    setGroupErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const toggleGroupNode = (nodeId: number) => {
    setGroupForm((prev) => ({
      ...prev,
      nodeIds: prev.nodeIds.includes(nodeId)
        ? prev.nodeIds.filter((id) => id !== nodeId)
        : [...prev.nodeIds, nodeId],
    }));
  };

  const submitGroup = async () => {
    if (!validateGroup()) return;
    setSubmitLoading(true);
    try {
      const payload = {
        ...groupForm,
        name: groupForm.name.trim(),
        remark: groupForm.remark.trim(),
      };
      const res = groupForm.id ? await updateAggregateNodeGroup(payload) : await createAggregateNodeGroup(payload);
      if (res.code === 0) {
        toast.success(groupForm.id ? "已保存" : "已创建");
        setGroupModalOpen(false);
        loadData();
      } else {
        toast.error(res.msg || "保存失败");
      }
    } catch (error) {
      console.error("保存节点组失败:", error);
      toast.error("保存失败");
    } finally {
      setSubmitLoading(false);
    }
  };

  const openDeleteGroup = (group: AggregateNodeGroup) => {
    setGroupToDelete(group);
    setDeleteModalOpen(true);
  };

  const confirmDelete = async () => {
    if (!groupToDelete) return;
    setDeleteLoading(true);
    try {
      const res = await deleteAggregateNodeGroup(groupToDelete.id);
      if (res.code === 0) {
        toast.success("已删除");
        setDeleteModalOpen(false);
        setGroupToDelete(null);
        loadData();
      } else {
        toast.error(res.msg || "删除失败");
      }
    } catch (error) {
      console.error("删除节点组失败:", error);
      toast.error("删除失败");
    } finally {
      setDeleteLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <div className="flex items-center gap-3">
          <Spinner size="sm" />
          <span className="text-default-600">正在加载...</span>
        </div>
      </div>
    );
  }

  return (
    <div className="px-3 lg:px-6 py-8 space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold text-foreground">节点组</h1>
          <p className="text-sm text-default-500 mt-1">隧道管理可直接选择节点组作为入口或出口</p>
        </div>
        <Button color="primary" onPress={openCreateGroup}>新增节点组</Button>
      </div>

      <section className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="text-base font-semibold text-foreground">节点组</h2>
          <Chip size="sm" variant="flat">{groups.length} 组</Chip>
        </div>
        {groups.length > 0 ? (
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
            {groups.map((group) => {
              const groupNodes = resolveGroupNodes(group);
              return (
                <Card key={group.id} className="shadow-sm border border-divider">
                  <CardHeader className="pb-2">
                    <div className="flex items-start justify-between w-full gap-3">
                      <div className="min-w-0">
                        <h3 className="font-semibold text-foreground truncate">{group.name}</h3>
                        <p className="text-xs text-default-500 mt-1">{groupNodes.length} 个节点 · 共同端口 {commonPortRange(groupNodes)}</p>
                      </div>
                      <Chip size="sm" color="success" variant="flat">启用</Chip>
                    </div>
                  </CardHeader>
                  <CardBody className="space-y-4">
                    <div className="flex flex-wrap gap-2">
                      {groupNodes.map((node) => (
                        <Chip key={node.id} size="sm" variant="flat" color={node.status === 1 ? "success" : "default"}>
                          {node.name}
                        </Chip>
                      ))}
                    </div>
                    <div className="space-y-1">
                      {groupNodes.slice(0, 4).map((node) => (
                        <div key={node.id} className="flex items-center justify-between gap-3 text-xs text-default-500">
                          <span className="truncate">{nodeAddress(node)}</span>
                          <span className="shrink-0">{portRangeText(node)}</span>
                        </div>
                      ))}
                      {groupNodes.length > 4 && <p className="text-xs text-default-500">还有 {groupNodes.length - 4} 个节点</p>}
                    </div>
                    {group.remark && <p className="text-sm text-default-500 break-words">{group.remark}</p>}
                    <div className="flex gap-2">
                      <Button size="sm" variant="flat" className="flex-1" onPress={() => openEditGroup(group)}>编辑</Button>
                      <Button size="sm" variant="flat" color="danger" className="flex-1" onPress={() => openDeleteGroup(group)}>删除</Button>
                    </div>
                  </CardBody>
                </Card>
              );
            })}
          </div>
        ) : (
          <Card className="shadow-sm border border-divider">
            <CardBody className="text-center py-10 text-default-500">暂无节点组</CardBody>
          </Card>
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
                          <span className="block text-xs text-default-500 truncate">{nodeAddress(node)} · {portRangeText(node)}</span>
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

      <Modal isOpen={deleteModalOpen} onOpenChange={setDeleteModalOpen} size="md" backdrop="blur">
        <ModalContent>
          {(onClose) => (
            <>
              <ModalHeader>确认删除</ModalHeader>
              <ModalBody>
                <p className="text-sm text-default-600">确定删除节点组 <span className="font-medium text-foreground">{groupToDelete?.name}</span> 吗？</p>
              </ModalBody>
              <ModalFooter>
                <Button variant="light" onPress={onClose}>取消</Button>
                <Button color="danger" isLoading={deleteLoading} onPress={confirmDelete}>删除</Button>
              </ModalFooter>
            </>
          )}
        </ModalContent>
      </Modal>
    </div>
  );
}
