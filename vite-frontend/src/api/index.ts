import Network from './network';

// 登陆相关接口
export interface LoginData {
  username: string;
  password: string;
  captchaId: string;
}

export interface LoginResponse {
  token: string;
  role_id: number;
  name: string;
  requirePasswordChange?: boolean;
}

export const login = (data: LoginData) => Network.post<LoginResponse>("/user/login", data);

// 用户CRUD操作 - 全部使用POST请求
export const createUser = (data: any) => Network.post("/user/create", data);
export const getAllUsers = (pageData: any = {}) => Network.post("/user/list", pageData);
export const updateUser = (data: any) => Network.post("/user/update", data);
export const deleteUser = (id: number) => Network.post("/user/delete", { id });
export const getUserPackageInfo = () => Network.post("/user/package");

// 节点CRUD操作 - 全部使用POST请求
export const createNode = (data: any) => Network.post("/node/create", data);
export const getNodeList = () => Network.post("/node/list");
export const updateNode = (data: any) => Network.post("/node/update", data);
export const deleteNode = (id: number) => Network.post("/node/delete", { id });
export const getNodeInstallCommand = (id: number) => Network.post("/node/install", { id });
export const checkNodeStatus = (nodeId?: number) => {
  const params = nodeId ? { nodeId } : {};
  return Network.post("/node/check-status", params);
};

// 隧道CRUD操作 - 全部使用POST请求
export const createTunnel = (data: any) => Network.post("/tunnel/create", data);
export const getTunnelList = () => Network.post("/tunnel/list");
export const getTunnelById = (id: number) => Network.post("/tunnel/get", { id });
export const updateTunnel = (data: any) => Network.post("/tunnel/update", data);
export const deleteTunnel = (id: number) => Network.post("/tunnel/delete", { id });
export const diagnoseTunnel = (tunnelId: number) => Network.post("/tunnel/diagnose", { tunnelId });

// 用户隧道权限管理操作 - 全部使用POST请求
export const assignUserTunnel = (data: any) => Network.post("/tunnel/user/assign", data);
export const getUserTunnelList = (queryData: any = {}) => Network.post("/tunnel/user/list", queryData);
export const removeUserTunnel = (params: any) => Network.post("/tunnel/user/remove", params);
export const updateUserTunnel = (data: any) => Network.post("/tunnel/user/update", data);
export const userTunnel = () => Network.post("/tunnel/user/tunnel");

// 转发CRUD操作 - 全部使用POST请求
export const createForward = (data: any) => Network.post("/forward/create", data);
export const batchCreateForwards = (forwards: any[]) => Network.post("/forward/batch-create", { forwards });
export const getForwardList = () => Network.post("/forward/list");
export const updateForward = (data: any) => Network.post("/forward/update", data);
export const deleteForward = (id: number) => Network.post("/forward/delete", { id });
export const forceDeleteForward = (id: number) => Network.post("/forward/force-delete", { id });
export const batchDeleteForwards = (ids: number[], force: boolean = false) => Network.post("/forward/batch-delete", { ids, force });

// 转发服务控制操作 - 通过Java后端接口
export const pauseForwardService = (forwardId: number) => Network.post("/forward/pause", { id: forwardId });
export const resumeForwardService = (forwardId: number) => Network.post("/forward/resume", { id: forwardId });

// 转发诊断操作
export const diagnoseForward = (forwardId: number) => Network.post("/forward/diagnose", { forwardId });

// 转发排序操作
export const updateForwardOrder = (data: { forwards: Array<{ id: number; inx: number }> }) => Network.post("/forward/update-order", data);

// 聚合节点组与聚合转发操作 - 全部使用POST请求
export const createAggregateNodeGroup = (data: any) => Network.post("/aggregate-node-group/create", data);
export const getAggregateNodeGroups = () => Network.post("/aggregate-node-group/list");
export const updateAggregateNodeGroup = (data: any) => Network.post("/aggregate-node-group/update", data);
export const deleteAggregateNodeGroup = (id: number) => Network.post("/aggregate-node-group/delete", { id });
export const createAggregateForward = (data: any) => Network.post("/aggregate-forward/create", data);
export const getAggregateForwards = () => Network.post("/aggregate-forward/list");
export const updateAggregateForward = (data: any) => Network.post("/aggregate-forward/update", data);
export const deleteAggregateForward = (id: number) => Network.post("/aggregate-forward/delete", { id });
export const pauseAggregateForward = (id: number) => Network.post("/aggregate-forward/pause", { id });
export const resumeAggregateForward = (id: number) => Network.post("/aggregate-forward/resume", { id });

// 限速规则CRUD操作 - 全部使用POST请求
export const createSpeedLimit = (data: any) => Network.post("/speed-limit/create", data);
export const getSpeedLimitList = () => Network.post("/speed-limit/list");
export const updateSpeedLimit = (data: any) => Network.post("/speed-limit/update", data);
export const deleteSpeedLimit = (id: number) => Network.post("/speed-limit/delete", { id });

// 修改密码接口
export const updatePassword = (data: any) => Network.post("/user/updatePassword", data);

// 重置流量接口
export const resetUserFlow = (data: { id: number; type: number }) => Network.post("/user/reset", data);

// 网站配置相关接口
export const getConfigs = () => Network.post("/config/list");
export const getConfigByName = (name: string) => Network.post("/config/get", { name });
export const updateConfigs = (configMap: Record<string, string>) => Network.post("/config/update", configMap);
export const updateConfig = (name: string, value: string) => Network.post("/config/update-single", { name, value });

// 商业化功能接口
export const registerUser = (data: any) => Network.post("/commerce/register", data);
export const getPackagePlans = () => Network.post("/commerce/plans");
export const getPaymentConfigs = () => Network.post("/commerce/payment/configs");
export const createOrder = (data: any) => Network.post("/commerce/order/create", data);
export const getMyOrders = () => Network.post("/commerce/orders");
export const redeemCode = (code: string) => Network.post("/commerce/redeem", { code });
export const getInviteInfo = () => Network.post("/commerce/invite/info");
export const getMyInviteRecords = () => Network.post("/commerce/invite/records");

export const adminGetPackagePlans = () => Network.post("/admin/commerce/plan/list");
export const adminCreatePackagePlan = (data: any) => Network.post("/admin/commerce/plan/create", data);
export const adminUpdatePackagePlan = (data: any) => Network.post("/admin/commerce/plan/update", data);
export const adminDeletePackagePlan = (id: number) => Network.post("/admin/commerce/plan/delete", { id });
export const adminGetDeviceGroups = () => Network.post("/admin/commerce/device-group/list");
export const adminCreateDeviceGroup = (data: any) => Network.post("/admin/commerce/device-group/create", data);
export const adminUpdateDeviceGroup = (data: any) => Network.post("/admin/commerce/device-group/update", data);
export const adminDeleteDeviceGroup = (id: number) => Network.post("/admin/commerce/device-group/delete", { id });
export const adminBindDeviceGroupTunnels = (data: any) => Network.post("/admin/commerce/device-group/bind-tunnels", data);
export const adminGetUserGroups = () => Network.post("/admin/commerce/user-group/list");
export const adminCreateUserGroup = (data: any) => Network.post("/admin/commerce/user-group/create", data);
export const adminUpdateUserGroup = (data: any) => Network.post("/admin/commerce/user-group/update", data);
export const adminDeleteUserGroup = (id: number) => Network.post("/admin/commerce/user-group/delete", { id });
export const adminBindUserGroupDeviceGroups = (data: any) => Network.post("/admin/commerce/user-group/bind-device-groups", data);
export const adminGetRedeemCodes = () => Network.post("/admin/commerce/redeem-code/list");
export const adminBatchCreateRedeemCodes = (data: any) => Network.post("/admin/commerce/redeem-code/batch-create", data);
export const adminDeleteRedeemCode = (id: number) => Network.post("/admin/commerce/redeem-code/delete", { id });
export const adminGetOrders = () => Network.post("/admin/commerce/order/list");
export const adminCompleteOrder = (id: number) => Network.post("/admin/commerce/order/complete", { id });
export const adminGetInviteConfig = () => Network.post("/admin/commerce/invite/config");
export const adminUpdateInviteConfig = (data: any) => Network.post("/admin/commerce/invite/config/update", data);
export const adminGetInviteRecords = () => Network.post("/admin/commerce/invite/records");
export const adminGetPaymentConfigs = () => Network.post("/admin/commerce/payment/configs");
export const adminUpdatePaymentConfig = (data: any) => Network.post("/admin/commerce/payment/config/update", data);

// 验证码相关接口
export const checkCaptcha = () => Network.post("/captcha/check");
export const generateCaptcha = () => Network.post(`/captcha/generate`);
export const verifyCaptcha = (data: { captchaId: string; trackData: string }) => Network.post("/captcha/verify", data);
