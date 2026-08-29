package com.admin.service;

import com.admin.common.dto.CommerceDto.*;
import com.admin.common.dto.PaymentDto.PaymentNotifyResult;
import com.admin.common.lang.R;

public interface CommerceService {
    R listPlans(boolean admin);
    R createPlan(PackagePlanRequest request);
    R updatePlan(PackagePlanRequest request);
    R deletePlan(Long id);

    R listDeviceGroups();
    R createDeviceGroup(DeviceGroupRequest request);
    R updateDeviceGroup(DeviceGroupRequest request);
    R deleteDeviceGroup(Long id);
    R bindDeviceGroupTunnels(BindTunnelsRequest request);

    R listUserGroups();
    R createUserGroup(UserGroupRequest request);
    R updateUserGroup(UserGroupRequest request);
    R deleteUserGroup(Long id);
    R bindUserGroupDeviceGroups(BindDeviceGroupsRequest request);

    R listRedeemCodes();
    R batchCreateRedeemCodes(BatchRedeemCodeRequest request);
    R deleteRedeemCode(Long id);
    R redeem(RedeemRequest request);

    R createOrder(CreateOrderRequest request);
    R completeOrder(Long id, boolean admin);
    R completePaidOrder(PaymentNotifyResult notifyResult);
    R listOrders(boolean admin);

    R getInviteConfig();
    R updateInviteConfig(InviteConfigRequest request);
    R inviteInfo();
    R inviteRecords(boolean admin);
    R register(RegisterRequest request);
}
