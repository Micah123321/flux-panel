package com.admin.controller;

import com.admin.common.aop.LogAnnotation;
import com.admin.common.dto.CommerceDto.*;
import com.admin.common.lang.R;
import com.admin.service.CommerceService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@CrossOrigin
@RequestMapping("/api/v1/commerce")
public class CommerceController extends BaseController {
    @Resource
    private CommerceService commerceService;

    @LogAnnotation
    @PostMapping("/plans")
    public R plans() { return commerceService.listPlans(false); }

    @LogAnnotation
    @PostMapping("/order/create")
    public R createOrder(@Validated @RequestBody CreateOrderRequest request) { return commerceService.createOrder(request); }

    @LogAnnotation
    @PostMapping("/order/complete")
    public R completeOrder(@Validated @RequestBody CompleteOrderRequest request) { return commerceService.completeOrder(request.getId(), false); }

    @LogAnnotation
    @PostMapping("/orders")
    public R orders() { return commerceService.listOrders(false); }

    @LogAnnotation
    @PostMapping("/redeem")
    public R redeem(@Validated @RequestBody RedeemRequest request) { return commerceService.redeem(request); }

    @LogAnnotation
    @PostMapping("/invite/info")
    public R inviteInfo() { return commerceService.inviteInfo(); }

    @LogAnnotation
    @PostMapping("/invite/records")
    public R inviteRecords() { return commerceService.inviteRecords(false); }

    @LogAnnotation
    @PostMapping("/register")
    public R register(@Validated @RequestBody RegisterRequest request) { return commerceService.register(request); }
}
