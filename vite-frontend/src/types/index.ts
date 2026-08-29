import { SVGProps } from "react";

export type IconSvgProps = SVGProps<SVGSVGElement> & {
  size?: number;
};

// 用户管理相关类型
export interface User {
  id: number;
  name?: string;
  user: string;
  pwd?: string;
  status: number; // 1-正常, 0-禁用
  flow: number; // 流量限制(GB)
  num: number; // 转发数量
  expTime?: number; // 过期时间戳
  flowResetTime?: number; // 流量重置日期(1-31号)
  createdTime?: number; // 创建时间戳
  inFlow?: number; // 下载流量(字节)
  outFlow?: number; // 上传流量(字节)
  packagePlanId?: number | null;
  userGroupId?: number | null;
  speedMbps?: number;
  ipLimit?: number;
  connectionLimit?: number;
  inviteCode?: string;
  inviterUserId?: number | null;
  inviteBalance?: number;
}

export interface UserForm {
  id?: number;
  name?: string;
  user: string;
  pwd?: string;
  status: number;
  flow: number;
  num: number;
  expTime: Date | null;
  flowResetTime: number;
  packagePlanId?: number | null;
  userGroupId?: number | null;
  speedMbps?: number;
  ipLimit?: number;
  connectionLimit?: number;
}

export interface UserTunnel {
  id: number;
  userId: number;
  tunnelId: number;
  tunnelName: string;
  status: number; // 1-正常, 0-禁用
  flow: number; // 流量限制(GB)
  num: number; // 转发数量
  expTime: number; // 过期时间戳
  flowResetTime: number; // 流量重置日期
  speedId?: number | null; // 限速规则ID
  speedLimitName?: string; // 限速规则名称
  inFlow?: number; // 下载流量(字节)
  outFlow?: number; // 上传流量(字节)
  tunnelFlow?: number; // 隧道流量计算类型(1-单向, 2-双向)
}

export interface UserTunnelForm {
  tunnelId: number | null;
  flow: number;
  num: number;
  expTime: Date | null;
  flowResetTime: number;
  speedId: number | null;
}

export interface Tunnel {
  id: number;
  name: string;
  entryNodeId: number;
  exitNodeId: number;
  entryNodeName?: string;
  exitNodeName?: string;
  status?: number;
  flow?: number; // 流量计算类型
}

export interface SpeedLimit {
  id: number;
  name: string;
  tunnelId: number;
  uploadSpeed: number;
  downloadSpeed: number;
}

export interface PackagePlan {
  id: number;
  name: string;
  hidden: number;
  price: number;
  type: number;
  durationMultiplier: number;
  userGroupId?: number | null;
  flow: number;
  maxRules: number;
  speedMbps: number;
  ipLimit: number;
  connectionLimit: number;
  description?: string;
  createdTime?: number;
  updatedTime?: number;
  status: number;
}

export interface DeviceGroup {
  id: number;
  name: string;
  tunnelIds?: string;
  tunnelIdList?: number[];
  tunnelNames?: string;
  description?: string;
  createdTime?: number;
  updatedTime?: number;
  status: number;
}

export interface UserGroup {
  id: number;
  name: string;
  description?: string;
  deviceGroupIds?: number[];
  deviceGroupNames?: string;
  createdTime?: number;
  updatedTime?: number;
  status: number;
}

export interface RedeemCode {
  id: number;
  packagePlanId: number;
  packageName: string;
  discountRatio: number;
  totalTimes: number;
  usedTimes: number;
  code: string;
  createdTime?: number;
  updatedTime?: number;
  status: number;
}

export interface OrderRecord {
  id: number;
  orderNo: string;
  userId: number;
  packagePlanId: number;
  packageName: string;
  originalAmount: number;
  discountRatio: number;
  payableAmount: number;
  status: number;
  redeemCodeId?: number | null;
  inviterUserId?: number | null;
  rewardRatio: number;
  rewardAmount: number;
  completedTime?: number | null;
  createdTime?: number;
  updatedTime?: number;
}

export interface InviteInfo {
  inviteCode: string;
  inviteBalance: number;
  inviteRatio: number;
  inviteRenewalRatio: number;
}

export interface InviteRecord {
  id: number;
  inviterUserId: number;
  inviteeUserId: number;
  inviteCode: string;
  createdTime?: number;
  updatedTime?: number;
  status: number;
}

export interface InviteRewardRecord {
  id: number;
  orderId: number;
  inviterUserId: number;
  inviteeUserId: number;
  rewardAmount: number;
  ratio: number;
  type: number;
  createdTime?: number;
  updatedTime?: number;
  status: number;
}

export interface InviteRecordsData {
  invites: InviteRecord[];
  rewards: InviteRewardRecord[];
}

export interface Pagination {
  current: number;
  size: number;
  total: number;
}
