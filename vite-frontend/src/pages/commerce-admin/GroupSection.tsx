import { Button } from "@heroui/button";
import { Card, CardBody, CardHeader } from "@heroui/card";
import { Chip } from "@heroui/chip";
import { Input } from "@heroui/input";
import { useState } from "react";
import toast from "react-hot-toast";

import {
  adminBindDeviceGroupTunnels,
  adminBindUserGroupDeviceGroups,
  adminCreateDeviceGroup,
  adminCreateUserGroup,
  adminDeleteDeviceGroup,
  adminDeleteUserGroup,
  adminUpdateDeviceGroup,
  adminUpdateUserGroup,
} from "@/api";
import { DeviceGroup, Tunnel, UserGroup } from "@/types";
import { emptyDeviceGroupForm, emptyUserGroupForm, statusText, toggleId } from "./constants";

interface GroupSectionProps {
  deviceGroups: DeviceGroup[];
  userGroups: UserGroup[];
  tunnels: Tunnel[];
  saving: boolean;
  setSaving: (value: boolean) => void;
  reload: () => void;
}

export default function GroupSection({ deviceGroups, userGroups, tunnels, saving, setSaving, reload }: GroupSectionProps) {
  const [deviceGroupForm, setDeviceGroupForm] = useState({ ...emptyDeviceGroupForm });
  const [userGroupForm, setUserGroupForm] = useState({ ...emptyUserGroupForm });

  const saveDeviceGroup = async () => {
    if (!deviceGroupForm.name.trim()) return toast.error("请输入设备组名称");
    setSaving(true);
    try {
      const payload = {
        ...(deviceGroupForm.id ? { id: Number(deviceGroupForm.id) } : {}),
        name: deviceGroupForm.name.trim(),
        description: deviceGroupForm.description,
        tunnelIds: deviceGroupForm.tunnelIds,
        status: Number(deviceGroupForm.status),
      };
      const res = deviceGroupForm.id ? await adminUpdateDeviceGroup(payload) : await adminCreateDeviceGroup(payload);
      if (res.code !== 0) return toast.error(res.msg || "设备组保存失败");
      if (res.data?.id) await adminBindDeviceGroupTunnels({ id: res.data.id, tunnelIds: deviceGroupForm.tunnelIds });
      toast.success("设备组已保存");
      setDeviceGroupForm({ ...emptyDeviceGroupForm });
      reload();
    } finally {
      setSaving(false);
    }
  };

  const saveUserGroup = async () => {
    if (!userGroupForm.name.trim()) return toast.error("请输入用户组名称");
    setSaving(true);
    try {
      const payload = {
        ...(userGroupForm.id ? { id: Number(userGroupForm.id) } : {}),
        name: userGroupForm.name.trim(),
        description: userGroupForm.description,
        status: Number(userGroupForm.status),
      };
      const res = userGroupForm.id ? await adminUpdateUserGroup(payload) : await adminCreateUserGroup(payload);
      if (res.code !== 0) return toast.error(res.msg || "用户组保存失败");
      const id = Number(res.data?.id || userGroupForm.id);
      await adminBindUserGroupDeviceGroups({ id, deviceGroupIds: userGroupForm.deviceGroupIds });
      toast.success("用户组已保存");
      setUserGroupForm({ ...emptyUserGroupForm });
      reload();
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
      <Card className="border border-gray-200 dark:border-default-200 shadow-sm"><CardHeader><h2 className="text-lg font-semibold">设备组</h2></CardHeader><CardBody className="space-y-4">
        <Input label="设备组名称" value={deviceGroupForm.name} onChange={(e) => setDeviceGroupForm({ ...deviceGroupForm, name: e.target.value })} />
        <textarea className="w-full min-h-20 rounded-md border border-default-200 bg-transparent px-3 py-2 text-sm" placeholder="说明" value={deviceGroupForm.description} onChange={(e) => setDeviceGroupForm({ ...deviceGroupForm, description: e.target.value })} />
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">{tunnels.map((tunnel) => <label key={tunnel.id} className="flex items-center gap-2 rounded-md border border-default-200 px-3 py-2 text-sm"><input type="checkbox" checked={deviceGroupForm.tunnelIds.includes(tunnel.id)} onChange={() => setDeviceGroupForm({ ...deviceGroupForm, tunnelIds: toggleId(deviceGroupForm.tunnelIds, tunnel.id) })} />{tunnel.name}</label>)}</div>
        <div className="flex gap-2"><Button color="primary" isLoading={saving} onClick={saveDeviceGroup}>保存设备组</Button><Button variant="flat" onClick={() => setDeviceGroupForm({ ...emptyDeviceGroupForm })}>清空</Button></div>
        <div className="space-y-2">{deviceGroups.map((group) => <div key={group.id} className="rounded-md border border-default-200 p-3"><div className="flex items-center justify-between"><b>{group.name}</b><Chip size="sm">{statusText(group.status)}</Chip></div><p className="text-sm text-default-500 mt-1">{group.tunnelNames || "未绑定隧道"}</p><div className="flex gap-2 mt-2"><Button size="sm" variant="flat" onClick={() => setDeviceGroupForm({ id: String(group.id), name: group.name, description: group.description || "", tunnelIds: group.tunnelIdList || [], status: String(group.status ?? 1) })}>编辑</Button><Button size="sm" color="danger" variant="flat" onClick={async () => { if (window.confirm("确认删除设备组？")) { const res = await adminDeleteDeviceGroup(group.id); res.code === 0 ? toast.success("已删除") : toast.error(res.msg || "删除失败"); reload(); } }}>删除</Button></div></div>)}</div>
      </CardBody></Card>
      <Card className="border border-gray-200 dark:border-default-200 shadow-sm"><CardHeader><h2 className="text-lg font-semibold">用户组</h2></CardHeader><CardBody className="space-y-4">
        <Input label="用户组名称" value={userGroupForm.name} onChange={(e) => setUserGroupForm({ ...userGroupForm, name: e.target.value })} />
        <textarea className="w-full min-h-20 rounded-md border border-default-200 bg-transparent px-3 py-2 text-sm" placeholder="说明" value={userGroupForm.description} onChange={(e) => setUserGroupForm({ ...userGroupForm, description: e.target.value })} />
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">{deviceGroups.map((group) => <label key={group.id} className="flex items-center gap-2 rounded-md border border-default-200 px-3 py-2 text-sm"><input type="checkbox" checked={userGroupForm.deviceGroupIds.includes(group.id)} onChange={() => setUserGroupForm({ ...userGroupForm, deviceGroupIds: toggleId(userGroupForm.deviceGroupIds, group.id) })} />{group.name}</label>)}</div>
        <div className="flex gap-2"><Button color="primary" isLoading={saving} onClick={saveUserGroup}>保存用户组</Button><Button variant="flat" onClick={() => setUserGroupForm({ ...emptyUserGroupForm })}>清空</Button></div>
        <div className="space-y-2">{userGroups.map((group) => <div key={group.id} className="rounded-md border border-default-200 p-3"><div className="flex items-center justify-between"><b>{group.name}</b><Chip size="sm">{statusText(group.status)}</Chip></div><p className="text-sm text-default-500 mt-1">{group.deviceGroupNames || "未绑定设备组"}</p><div className="flex gap-2 mt-2"><Button size="sm" variant="flat" onClick={() => setUserGroupForm({ id: String(group.id), name: group.name, description: group.description || "", deviceGroupIds: group.deviceGroupIds || [], status: String(group.status ?? 1) })}>编辑</Button><Button size="sm" color="danger" variant="flat" onClick={async () => { if (window.confirm("确认删除用户组？")) { const res = await adminDeleteUserGroup(group.id); res.code === 0 ? toast.success("已删除") : toast.error(res.msg || "删除失败"); reload(); } }}>删除</Button></div></div>)}</div>
      </CardBody></Card>
    </div>
  );
}
