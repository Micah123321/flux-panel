package com.admin.controller;

import com.admin.common.annotation.RequireRole;
import com.admin.common.aop.LogAnnotation;
import com.admin.common.dto.CommerceDto.*;
import com.admin.common.dto.PaymentDto.PaymentConfigRequest;
import com.admin.common.lang.R;
import com.admin.service.CommerceService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@CrossOrigin
@RequestMapping("/api/v1/admin/commerce")
public class CommerceAdminController extends BaseController {
    @Resource
    private CommerceService commerceService;

    @LogAnnotation
    @RequireRole
    @PostMapping("/plan/list")
    public R listPlans() { return commerceService.listPlans(true); }

    @LogAnnotation
    @RequireRole
    @PostMapping("/plan/create")
    public R createPlan(@Validated @RequestBody PackagePlanRequest request) { return commerceService.createPlan(request); }

    @LogAnnotation
    @RequireRole
    @PostMapping("/plan/update")
    public R updatePlan(@Validated @RequestBody PackagePlanRequest request) { return commerceService.updatePlan(request); }

    @LogAnnotation
    @RequireRole
    @PostMapping("/plan/delete")
    public R deletePlan(@Validated @RequestBody IdRequest request) { return commerceService.deletePlan(request.getId()); }

    @LogAnnotation
    @RequireRole
    @PostMapping("/device-group/list")
    public R listDeviceGroups() { return commerceService.listDeviceGroups(); }

    @LogAnnotation
    @RequireRole
    @PostMapping("/device-group/create")
    public R createDeviceGroup(@Validated @RequestBody DeviceGroupRequest request) { return commerceService.createDeviceGroup(request); }

    @LogAnnotation
    @RequireRole
    @PostMapping("/device-group/update")
    public R updateDeviceGroup(@Validated @RequestBody DeviceGroupRequest request) { return commerceService.updateDeviceGroup(request); }

    @LogAnnotation
    @RequireRole
    @PostMapping("/device-group/delete")
    public R deleteDeviceGroup(@Validated @RequestBody IdRequest request) { return commerceService.deleteDeviceGroup(request.getId()); }

    @LogAnnotation
    @RequireRole
    @PostMapping("/device-group/bind-tunnels")
    public R bindDeviceGroupTunnels(@Validated @RequestBody BindTunnelsRequest request) { return commerceService.bindDeviceGroupTunnels(request); }

    @LogAnnotation
    @RequireRole
    @PostMapping("/user-group/list")
    public R listUserGroups() { return commerceService.listUserGroups(); }

    @LogAnnotation
    @RequireRole
    @PostMapping("/user-group/create")
    public R createUserGroup(@Validated @RequestBody UserGroupRequest request) { return commerceService.createUserGroup(request); }

    @LogAnnotation
    @RequireRole
    @PostMapping("/user-group/update")
    public R updateUserGroup(@Validated @RequestBody UserGroupRequest request) { return commerceService.updateUserGroup(request); }

    @LogAnnotation
    @RequireRole
    @PostMapping("/user-group/delete")
    public R deleteUserGroup(@Validated @RequestBody IdRequest request) { return commerceService.deleteUserGroup(request.getId()); }

    @LogAnnotation
    @RequireRole
    @PostMapping("/user-group/bind-device-groups")
    public R bindUserGroupDeviceGroups(@Validated @RequestBody BindDeviceGroupsRequest request) { return commerceService.bindUserGroupDeviceGroups(request); }

    @LogAnnotation
    @RequireRole
    @PostMapping("/redeem-code/list")
    public R listRedeemCodes() { return commerceService.listRedeemCodes(); }

    @LogAnnotation
    @RequireRole
    @PostMapping("/redeem-code/batch-create")
    public R batchCreateRedeemCodes(@Validated @RequestBody BatchRedeemCodeRequest request) { return commerceService.batchCreateRedeemCodes(request); }

    @LogAnnotation
    @RequireRole
    @PostMapping("/redeem-code/delete")
    public R deleteRedeemCode(@Validated @RequestBody IdRequest request) { return commerceService.deleteRedeemCode(request.getId()); }

    @LogAnnotation
    @RequireRole
    @PostMapping("/order/list")
    public R listOrders() { return commerceService.listOrders(true); }

    @LogAnnotation
    @RequireRole
    @PostMapping("/order/complete")
    public R completeOrder(@Validated @RequestBody CompleteOrderRequest request) { return commerceService.completeOrder(request.getId(), true); }

    @LogAnnotation
    @RequireRole
    @PostMapping("/invite/config")
    public R getInviteConfig() { return commerceService.getInviteConfig(); }

    @LogAnnotation
    @RequireRole
    @PostMapping("/invite/config/update")
    public R updateInviteConfig(@Validated @RequestBody InviteConfigRequest request) { return commerceService.updateInviteConfig(request); }

    @LogAnnotation
    @RequireRole
    @PostMapping("/invite/records")
    public R inviteRecords() { return commerceService.inviteRecords(true); }

    @LogAnnotation
    @RequireRole
    @PostMapping("/payment/configs")
    public R paymentConfigs() { return paymentService.listPaymentConfigs(true); }

    @LogAnnotation
    @RequireRole
    @PostMapping("/payment/config/update")
    public R updatePaymentConfig(@Validated @RequestBody PaymentConfigRequest request) { return paymentService.updatePaymentConfig(request); }
}
