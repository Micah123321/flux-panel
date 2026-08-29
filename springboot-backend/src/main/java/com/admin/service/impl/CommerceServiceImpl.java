package com.admin.service.impl;

import com.admin.common.dto.CommerceDto.*;
import com.admin.common.lang.R;
import com.admin.common.utils.JwtUtil;
import com.admin.common.utils.Md5Util;
import com.admin.entity.*;
import com.admin.mapper.*;
import com.admin.service.CommerceService;
import com.admin.service.ViteConfigService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CommerceServiceImpl implements CommerceService {
    private static final int ACTIVE = 1;
    private static final int DISABLED = 0;
    private static final int VISIBLE = 0;
    private static final int ORDER_PENDING = 0;
    private static final int ORDER_COMPLETED = 1;
    private static final int USER_ROLE_ID = 1;
    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final String INVITE_RATIO_KEY = "invite_ratio";
    private static final String INVITE_RENEWAL_RATIO_KEY = "invite_renewal_ratio";

    @Resource
    private PackagePlanMapper packagePlanMapper;
    @Resource
    private DeviceGroupMapper deviceGroupMapper;
    @Resource
    private UserGroupMapper userGroupMapper;
    @Resource
    private UserGroupDeviceGroupMapper userGroupDeviceGroupMapper;
    @Resource
    private OrderRecordMapper orderRecordMapper;
    @Resource
    private RedeemCodeMapper redeemCodeMapper;
    @Resource
    private InviteRecordMapper inviteRecordMapper;
    @Resource
    private InviteRewardRecordMapper inviteRewardRecordMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private UserTunnelMapper userTunnelMapper;
    @Resource
    private TunnelMapper tunnelMapper;
    @Resource
    private ViteConfigService viteConfigService;

    @Override
    public R listPlans(boolean admin) {
        QueryWrapper<PackagePlan> wrapper = new QueryWrapper<>();
        if (!admin) {
            wrapper.eq("status", ACTIVE).eq("hidden", VISIBLE);
        }
        wrapper.orderByAsc("price").orderByDesc("id");
        return R.ok(packagePlanMapper.selectList(wrapper));
    }

    @Override
    public R createPlan(PackagePlanRequest request) {
        R validation = validatePlanRequest(request);
        if (validation.getCode() != 0) return validation;

        long now = System.currentTimeMillis();
        PackagePlan plan = new PackagePlan();
        copyPlanRequest(request, plan);
        plan.setCreatedTime(now);
        plan.setUpdatedTime(now);
        plan.setStatus(request.getStatus() == null ? ACTIVE : request.getStatus());
        packagePlanMapper.insert(plan);
        return R.ok(plan);
    }

    @Override
    public R updatePlan(PackagePlanRequest request) {
        if (request.getId() == null) return R.err("套餐ID不能为空");
        PackagePlan plan = packagePlanMapper.selectById(request.getId());
        if (plan == null) return R.err("套餐不存在");
        R validation = validatePlanRequest(request);
        if (validation.getCode() != 0) return validation;

        copyPlanRequest(request, plan);
        plan.setUpdatedTime(System.currentTimeMillis());
        plan.setStatus(request.getStatus() == null ? ACTIVE : request.getStatus());
        packagePlanMapper.updateById(plan);
        return R.ok(plan);
    }

    @Override
    public R deletePlan(Long id) {
        if (packagePlanMapper.selectById(id) == null) return R.err("套餐不存在");
        QueryWrapper<OrderRecord> orderWrapper = new QueryWrapper<>();
        orderWrapper.eq("package_plan_id", id);
        if (orderRecordMapper.selectCount(orderWrapper) > 0) return R.err("套餐已有订单，不能删除");
        packagePlanMapper.deleteById(id);
        return R.ok("套餐删除成功");
    }

    @Override
    public R listDeviceGroups() {
        QueryWrapper<DeviceGroup> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("id");
        List<DeviceGroup> groups = deviceGroupMapper.selectList(wrapper);
        groups.forEach(this::decorateDeviceGroup);
        return R.ok(groups);
    }

    @Override
    public R createDeviceGroup(DeviceGroupRequest request) {
        List<Long> tunnelIds = normalizeIds(request.getTunnelIds());
        R validation = validateTunnelIds(tunnelIds);
        if (validation.getCode() != 0) return validation;

        long now = System.currentTimeMillis();
        DeviceGroup group = new DeviceGroup();
        group.setName(request.getName());
        group.setDescription(request.getDescription());
        group.setTunnelIds(joinIds(tunnelIds));
        group.setCreatedTime(now);
        group.setUpdatedTime(now);
        group.setStatus(request.getStatus() == null ? ACTIVE : request.getStatus());
        deviceGroupMapper.insert(group);
        decorateDeviceGroup(group);
        return R.ok(group);
    }

    @Override
    public R updateDeviceGroup(DeviceGroupRequest request) {
        if (request.getId() == null) return R.err("设备组ID不能为空");
        DeviceGroup group = deviceGroupMapper.selectById(request.getId());
        if (group == null) return R.err("设备组不存在");
        List<Long> tunnelIds = normalizeIds(request.getTunnelIds());
        R validation = validateTunnelIds(tunnelIds);
        if (validation.getCode() != 0) return validation;

        group.setName(request.getName());
        group.setDescription(request.getDescription());
        group.setTunnelIds(joinIds(tunnelIds));
        group.setUpdatedTime(System.currentTimeMillis());
        group.setStatus(request.getStatus() == null ? ACTIVE : request.getStatus());
        deviceGroupMapper.updateById(group);
        decorateDeviceGroup(group);
        return R.ok(group);
    }

    @Override
    public R deleteDeviceGroup(Long id) {
        if (deviceGroupMapper.selectById(id) == null) return R.err("设备组不存在");
        QueryWrapper<UserGroupDeviceGroup> wrapper = new QueryWrapper<>();
        wrapper.eq("device_group_id", id);
        if (userGroupDeviceGroupMapper.selectCount(wrapper) > 0) return R.err("设备组已绑定用户组，不能删除");
        deviceGroupMapper.deleteById(id);
        return R.ok("设备组删除成功");
    }

    @Override
    public R bindDeviceGroupTunnels(BindTunnelsRequest request) {
        DeviceGroup group = deviceGroupMapper.selectById(request.getId());
        if (group == null) return R.err("设备组不存在");
        List<Long> tunnelIds = normalizeIds(request.getTunnelIds());
        R validation = validateTunnelIds(tunnelIds);
        if (validation.getCode() != 0) return validation;
        group.setTunnelIds(joinIds(tunnelIds));
        group.setUpdatedTime(System.currentTimeMillis());
        deviceGroupMapper.updateById(group);
        decorateDeviceGroup(group);
        return R.ok(group);
    }

    @Override
    public R listUserGroups() {
        QueryWrapper<UserGroup> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("id");
        List<UserGroup> groups = userGroupMapper.selectList(wrapper);
        groups.forEach(this::decorateUserGroup);
        return R.ok(groups);
    }

    @Override
    public R createUserGroup(UserGroupRequest request) {
        long now = System.currentTimeMillis();
        UserGroup group = new UserGroup();
        group.setName(request.getName());
        group.setDescription(request.getDescription());
        group.setCreatedTime(now);
        group.setUpdatedTime(now);
        group.setStatus(request.getStatus() == null ? ACTIVE : request.getStatus());
        userGroupMapper.insert(group);
        return R.ok(group);
    }

    @Override
    public R updateUserGroup(UserGroupRequest request) {
        if (request.getId() == null) return R.err("用户组ID不能为空");
        UserGroup group = userGroupMapper.selectById(request.getId());
        if (group == null) return R.err("用户组不存在");
        group.setName(request.getName());
        group.setDescription(request.getDescription());
        group.setUpdatedTime(System.currentTimeMillis());
        group.setStatus(request.getStatus() == null ? ACTIVE : request.getStatus());
        userGroupMapper.updateById(group);
        decorateUserGroup(group);
        return R.ok(group);
    }

    @Override
    public R deleteUserGroup(Long id) {
        if (userGroupMapper.selectById(id) == null) return R.err("用户组不存在");
        QueryWrapper<PackagePlan> planWrapper = new QueryWrapper<>();
        planWrapper.eq("user_group_id", id);
        if (packagePlanMapper.selectCount(planWrapper) > 0) return R.err("用户组已被套餐使用，不能删除");
        QueryWrapper<UserGroupDeviceGroup> mappingWrapper = new QueryWrapper<>();
        mappingWrapper.eq("user_group_id", id);
        userGroupDeviceGroupMapper.delete(mappingWrapper);
        userGroupMapper.deleteById(id);
        return R.ok("用户组删除成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R bindUserGroupDeviceGroups(BindDeviceGroupsRequest request) {
        if (userGroupMapper.selectById(request.getId()) == null) return R.err("用户组不存在");
        List<Long> deviceGroupIds = normalizeIds(request.getDeviceGroupIds());
        for (Long deviceGroupId : deviceGroupIds) {
            if (deviceGroupMapper.selectById(deviceGroupId) == null) return R.err("设备组不存在: " + deviceGroupId);
        }

        QueryWrapper<UserGroupDeviceGroup> deleteWrapper = new QueryWrapper<>();
        deleteWrapper.eq("user_group_id", request.getId());
        userGroupDeviceGroupMapper.delete(deleteWrapper);
        long now = System.currentTimeMillis();
        for (Long deviceGroupId : deviceGroupIds) {
            UserGroupDeviceGroup mapping = new UserGroupDeviceGroup();
            mapping.setUserGroupId(request.getId());
            mapping.setDeviceGroupId(deviceGroupId);
            mapping.setCreatedTime(now);
            mapping.setUpdatedTime(now);
            mapping.setStatus(ACTIVE);
            userGroupDeviceGroupMapper.insert(mapping);
        }
        UserGroup group = userGroupMapper.selectById(request.getId());
        decorateUserGroup(group);
        return R.ok(group);
    }

    @Override
    public R listRedeemCodes() {
        QueryWrapper<RedeemCode> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("id");
        return R.ok(redeemCodeMapper.selectList(wrapper));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R batchCreateRedeemCodes(BatchRedeemCodeRequest request) {
        PackagePlan plan = packagePlanMapper.selectById(request.getPackagePlanId());
        if (plan == null) return R.err("套餐不存在");
        Integer discountRatio = clampRatio(request.getDiscountRatio(), 100);
        Integer totalTimes = request.getTotalTimes() == null ? 1 : request.getTotalTimes();
        List<String> codes = requestedCodes(request);
        if (codes.size() > 500) return R.err("单次最多创建500个兑换码");

        List<RedeemCode> created = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (String rawCode : codes) {
            String code = rawCode.trim().toUpperCase();
            if (!StringUtils.hasText(code)) continue;
            QueryWrapper<RedeemCode> exists = new QueryWrapper<>();
            exists.eq("code", code);
            if (redeemCodeMapper.selectCount(exists) > 0) return R.err("兑换码已存在: " + code);
            RedeemCode redeemCode = new RedeemCode();
            redeemCode.setPackagePlanId(plan.getId());
            redeemCode.setPackageName(plan.getName());
            redeemCode.setDiscountRatio(discountRatio);
            redeemCode.setTotalTimes(totalTimes);
            redeemCode.setUsedTimes(0);
            redeemCode.setCode(code);
            redeemCode.setCreatedTime(now);
            redeemCode.setUpdatedTime(now);
            redeemCode.setStatus(ACTIVE);
            redeemCodeMapper.insert(redeemCode);
            created.add(redeemCode);
        }
        return R.ok(created);
    }

    @Override
    public R deleteRedeemCode(Long id) {
        if (redeemCodeMapper.selectById(id) == null) return R.err("兑换码不存在");
        redeemCodeMapper.deleteById(id);
        return R.ok("兑换码删除成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R redeem(RedeemRequest request) {
        User user = currentUser();
        if (user == null) return R.err("用户不存在");
        RedeemCode redeemCode = findRedeemCode(request.getCode());
        R validation = validateRedeemCode(redeemCode, null);
        if (validation.getCode() != 0) return validation;
        PackagePlan plan = packagePlanMapper.selectById(redeemCode.getPackagePlanId());
        R planValidation = validatePlanActive(plan);
        if (planValidation.getCode() != 0) return planValidation;
        if (!consumeRedeemCode(redeemCode)) return R.err("兑换码次数不足");

        OrderRecord order = buildOrder(user, plan, redeemCode.getDiscountRatio(), redeemCode, ORDER_COMPLETED);
        order.setCompletedTime(System.currentTimeMillis());
        orderRecordMapper.insert(order);
        applyPackage(user, plan);
        createInviteReward(order, user);
        return R.ok(order);
    }

    @Override
    public R createOrder(CreateOrderRequest request) {
        User user = currentUser();
        if (user == null) return R.err("用户不存在");
        PackagePlan plan = packagePlanMapper.selectById(request.getPackagePlanId());
        R planValidation = validatePlanForUser(plan);
        if (planValidation.getCode() != 0) return planValidation;

        RedeemCode redeemCode = null;
        int discountRatio = 100;
        if (StringUtils.hasText(request.getRedeemCode())) {
            redeemCode = findRedeemCode(request.getRedeemCode());
            R redeemValidation = validateRedeemCode(redeemCode, plan.getId());
            if (redeemValidation.getCode() != 0) return redeemValidation;
            discountRatio = redeemCode.getDiscountRatio();
        }

        OrderRecord order = buildOrder(user, plan, discountRatio, redeemCode, ORDER_PENDING);
        orderRecordMapper.insert(order);
        return R.ok(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R completeOrder(Long id, boolean admin) {
        OrderRecord order = orderRecordMapper.selectById(id);
        if (order == null) return R.err("订单不存在");
        User user = currentUser();
        if (user == null) return R.err("用户不存在");
        if (!admin && !Objects.equals(order.getUserId(), user.getId())) return R.err("无权操作此订单");
        if (!Objects.equals(order.getStatus(), ORDER_PENDING)) return R.err("订单不是待完成状态");

        User buyer = userMapper.selectById(order.getUserId());
        if (buyer == null) return R.err("订单用户不存在");
        PackagePlan plan = packagePlanMapper.selectById(order.getPackagePlanId());
        R planValidation = validatePlanActive(plan);
        if (planValidation.getCode() != 0) return planValidation;
        if (order.getRedeemCodeId() != null) {
            RedeemCode redeemCode = redeemCodeMapper.selectById(order.getRedeemCodeId());
            R redeemValidation = validateRedeemCode(redeemCode, plan.getId());
            if (redeemValidation.getCode() != 0) return redeemValidation;
            if (!consumeRedeemCode(redeemCode)) return R.err("兑换码次数不足");
        }

        long now = System.currentTimeMillis();
        order.setStatus(ORDER_COMPLETED);
        order.setCompletedTime(now);
        order.setUpdatedTime(now);
        orderRecordMapper.updateById(order);
        applyPackage(buyer, plan);
        createInviteReward(order, buyer);
        return R.ok(orderRecordMapper.selectById(order.getId()));
    }

    @Override
    public R listOrders(boolean admin) {
        QueryWrapper<OrderRecord> wrapper = new QueryWrapper<>();
        if (!admin) {
            User user = currentUser();
            if (user == null) return R.err("用户不存在");
            wrapper.eq("user_id", user.getId());
        }
        wrapper.orderByDesc("id");
        return R.ok(orderRecordMapper.selectList(wrapper));
    }

    @Override
    public R getInviteConfig() {
        Map<String, Integer> config = new HashMap<>();
        config.put("inviteRatio", getConfigInt(INVITE_RATIO_KEY, 0));
        config.put("inviteRenewalRatio", getConfigInt(INVITE_RENEWAL_RATIO_KEY, 0));
        return R.ok(config);
    }

    @Override
    public R updateInviteConfig(InviteConfigRequest request) {
        R inviteResult = viteConfigService.updateConfig(INVITE_RATIO_KEY, clampRatio(request.getInviteRatio(), 0).toString());
        if (inviteResult.getCode() != 0) return inviteResult;
        R renewalResult = viteConfigService.updateConfig(INVITE_RENEWAL_RATIO_KEY, clampRatio(request.getInviteRenewalRatio(), 0).toString());
        if (renewalResult.getCode() != 0) return renewalResult;
        return getInviteConfig();
    }

    @Override
    public R inviteInfo() {
        User user = currentUser();
        if (user == null) return R.err("用户不存在");
        ensureInviteCode(user);
        Map<String, Object> info = new HashMap<>();
        info.put("inviteCode", user.getInviteCode());
        info.put("inviteBalance", user.getInviteBalance() == null ? BigDecimal.ZERO : user.getInviteBalance());
        info.put("inviteRatio", getConfigInt(INVITE_RATIO_KEY, 0));
        info.put("inviteRenewalRatio", getConfigInt(INVITE_RENEWAL_RATIO_KEY, 0));
        return R.ok(info);
    }

    @Override
    public R inviteRecords(boolean admin) {
        QueryWrapper<InviteRecord> inviteWrapper = new QueryWrapper<>();
        QueryWrapper<InviteRewardRecord> rewardWrapper = new QueryWrapper<>();
        if (!admin) {
            User user = currentUser();
            if (user == null) return R.err("用户不存在");
            inviteWrapper.eq("inviter_user_id", user.getId());
            rewardWrapper.eq("inviter_user_id", user.getId());
        }
        inviteWrapper.orderByDesc("id");
        rewardWrapper.orderByDesc("id");
        Map<String, Object> data = new HashMap<>();
        data.put("invites", inviteRecordMapper.selectList(inviteWrapper));
        data.put("rewards", inviteRewardRecordMapper.selectList(rewardWrapper));
        return R.ok(data);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R register(RegisterRequest request) {
        if (request.getPwd() == null || request.getPwd().length() < 6) return R.err("密码长度至少6位");
        QueryWrapper<User> userWrapper = new QueryWrapper<>();
        userWrapper.eq("user", request.getUser());
        if (userMapper.selectCount(userWrapper) > 0) return R.err("用户名已存在");

        User inviter = null;
        if (StringUtils.hasText(request.getInviteCode())) {
            QueryWrapper<User> inviteWrapper = new QueryWrapper<>();
            inviteWrapper.eq("invite_code", request.getInviteCode().trim());
            inviter = userMapper.selectOne(inviteWrapper);
            if (inviter == null) return R.err("邀请码不存在");
        }

        long now = System.currentTimeMillis();
        User user = new User();
        user.setUser(request.getUser());
        user.setPwd(Md5Util.md5(request.getPwd()));
        user.setRoleId(USER_ROLE_ID);
        user.setExpTime(now);
        user.setFlow(0L);
        user.setInFlow(0L);
        user.setOutFlow(0L);
        user.setFlowResetTime(1L);
        user.setNum(0);
        user.setStatus(ACTIVE);
        user.setCreatedTime(now);
        user.setUpdatedTime(now);
        user.setInviteCode(generateUniqueInviteCode());
        user.setInviteBalance(BigDecimal.ZERO);
        if (inviter != null) user.setInviterUserId(inviter.getId());
        userMapper.insert(user);

        if (inviter != null) {
            InviteRecord record = new InviteRecord();
            record.setInviterUserId(inviter.getId());
            record.setInviteeUserId(user.getId());
            record.setInviteCode(request.getInviteCode().trim());
            record.setCreatedTime(now);
            record.setUpdatedTime(now);
            record.setStatus(ACTIVE);
            inviteRecordMapper.insert(record);
        }
        return R.ok("注册成功");
    }

    private R validatePlanRequest(PackagePlanRequest request) {
        if (request.getUserGroupId() != null && request.getUserGroupId() > 0 && userGroupMapper.selectById(request.getUserGroupId()) == null) {
            return R.err("用户组不存在");
        }
        return R.ok();
    }

    private void copyPlanRequest(PackagePlanRequest request, PackagePlan plan) {
        plan.setName(request.getName());
        plan.setHidden(request.getHidden() == null ? VISIBLE : request.getHidden());
        plan.setPrice(request.getPrice() == null ? BigDecimal.ZERO : request.getPrice());
        plan.setType(request.getType() == null ? 1 : request.getType());
        plan.setDurationMultiplier(request.getDurationMultiplier());
        plan.setUserGroupId(request.getUserGroupId());
        plan.setFlow(request.getFlow());
        plan.setMaxRules(request.getMaxRules());
        plan.setSpeedMbps(request.getSpeedMbps() == null ? 0 : request.getSpeedMbps());
        plan.setIpLimit(request.getIpLimit() == null ? 0 : request.getIpLimit());
        plan.setConnectionLimit(request.getConnectionLimit() == null ? 0 : request.getConnectionLimit());
        plan.setDescription(request.getDescription());
    }

    private R validatePlanForUser(PackagePlan plan) {
        R activeValidation = validatePlanActive(plan);
        if (activeValidation.getCode() != 0) return activeValidation;
        if (!Objects.equals(plan.getHidden(), VISIBLE)) return R.err("套餐已隐藏");
        return R.ok();
    }

    private R validatePlanActive(PackagePlan plan) {
        if (plan == null || !Objects.equals(plan.getStatus(), ACTIVE)) return R.err("套餐不存在或已停用");
        return R.ok();
    }

    private R validateTunnelIds(List<Long> tunnelIds) {
        for (Long tunnelId : tunnelIds) {
            if (tunnelMapper.selectById(tunnelId) == null) return R.err("隧道不存在: " + tunnelId);
        }
        return R.ok();
    }

    private void decorateDeviceGroup(DeviceGroup group) {
        List<Long> ids = parseIds(group.getTunnelIds());
        group.setTunnelIdList(ids);
        if (ids.isEmpty()) {
            group.setTunnelNames("");
            return;
        }
        List<Tunnel> tunnels = tunnelMapper.selectBatchIds(ids);
        group.setTunnelNames(tunnels.stream().map(Tunnel::getName).collect(Collectors.joining(", ")));
    }

    private void decorateUserGroup(UserGroup group) {
        QueryWrapper<UserGroupDeviceGroup> wrapper = new QueryWrapper<>();
        wrapper.eq("user_group_id", group.getId()).eq("status", ACTIVE);
        List<Long> ids = userGroupDeviceGroupMapper.selectList(wrapper).stream()
                .map(UserGroupDeviceGroup::getDeviceGroupId)
                .collect(Collectors.toList());
        group.setDeviceGroupIds(ids);
        if (ids.isEmpty()) {
            group.setDeviceGroupNames("");
            return;
        }
        List<DeviceGroup> groups = deviceGroupMapper.selectBatchIds(ids);
        group.setDeviceGroupNames(groups.stream().map(DeviceGroup::getName).collect(Collectors.joining(", ")));
    }

    private List<String> requestedCodes(BatchRedeemCodeRequest request) {
        if (request.getCodes() != null && !request.getCodes().isEmpty()) {
            return request.getCodes().stream().filter(StringUtils::hasText).collect(Collectors.toList());
        }
        int count = request.getCount() == null ? 1 : request.getCount();
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < count; i++) codes.add(generateUniqueRedeemCode());
        return codes;
    }

    private RedeemCode findRedeemCode(String code) {
        QueryWrapper<RedeemCode> wrapper = new QueryWrapper<>();
        wrapper.eq("code", code.trim().toUpperCase());
        return redeemCodeMapper.selectOne(wrapper);
    }

    private R validateRedeemCode(RedeemCode redeemCode, Long packagePlanId) {
        if (redeemCode == null || !Objects.equals(redeemCode.getStatus(), ACTIVE)) return R.err("兑换码不存在或已停用");
        if (packagePlanId != null && !Objects.equals(redeemCode.getPackagePlanId(), packagePlanId)) return R.err("兑换码不适用于该套餐");
        if (redeemCode.getUsedTimes() != null && redeemCode.getTotalTimes() != null && redeemCode.getUsedTimes() >= redeemCode.getTotalTimes()) return R.err("兑换码已用完");
        return R.ok();
    }

    private boolean consumeRedeemCode(RedeemCode redeemCode) {
        UpdateWrapper<RedeemCode> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", redeemCode.getId())
                .lt("used_times", redeemCode.getTotalTimes())
                .set("updated_time", System.currentTimeMillis())
                .setSql("used_times = used_times + 1");
        return redeemCodeMapper.update(null, wrapper) == 1;
    }

    private OrderRecord buildOrder(User user, PackagePlan plan, Integer discountRatio, RedeemCode redeemCode, Integer status) {
        long now = System.currentTimeMillis();
        OrderRecord order = new OrderRecord();
        order.setOrderNo("ORD" + now + randomCode(6));
        order.setUserId(user.getId());
        order.setPackagePlanId(plan.getId());
        order.setPackageName(plan.getName());
        order.setOriginalAmount(plan.getPrice());
        order.setDiscountRatio(clampRatio(discountRatio, 100));
        order.setPayableAmount(plan.getPrice().multiply(BigDecimal.valueOf(order.getDiscountRatio())).divide(HUNDRED, 2, RoundingMode.HALF_UP));
        order.setStatus(status);
        order.setRedeemCodeId(redeemCode == null ? null : redeemCode.getId());
        order.setInviterUserId(user.getInviterUserId());
        order.setRewardRatio(0);
        order.setRewardAmount(BigDecimal.ZERO);
        order.setCreatedTime(now);
        order.setUpdatedTime(now);
        return order;
    }

    private void applyPackage(User user, PackagePlan plan) {
        long now = System.currentTimeMillis();
        long baseExpTime = user.getExpTime() != null && user.getExpTime() > now ? user.getExpTime() : now;
        long expTime = baseExpTime + plan.getDurationMultiplier() * 30L * DAY_MS;
        user.setPackagePlanId(plan.getId());
        user.setUserGroupId(plan.getUserGroupId());
        user.setFlow(plan.getFlow());
        user.setInFlow(0L);
        user.setOutFlow(0L);
        user.setNum(plan.getMaxRules());
        user.setSpeedMbps(plan.getSpeedMbps());
        user.setIpLimit(plan.getIpLimit());
        user.setConnectionLimit(plan.getConnectionLimit());
        user.setExpTime(expTime);
        user.setFlowResetTime(user.getFlowResetTime() == null ? 1L : user.getFlowResetTime());
        user.setStatus(ACTIVE);
        user.setUpdatedTime(now);
        userMapper.updateById(user);
        syncUserGroupTunnels(user, plan, expTime);
    }

    private void syncUserGroupTunnels(User user, PackagePlan plan, long expTime) {
        if (plan.getUserGroupId() == null || plan.getUserGroupId() <= 0) return;
        List<Long> tunnelIds = resolveUserGroupTunnelIds(plan.getUserGroupId());
        for (Long tunnelId : tunnelIds) {
            QueryWrapper<UserTunnel> wrapper = new QueryWrapper<>();
            wrapper.eq("user_id", user.getId()).eq("tunnel_id", tunnelId);
            UserTunnel userTunnel = userTunnelMapper.selectOne(wrapper);
            if (userTunnel == null) {
                userTunnel = new UserTunnel();
                userTunnel.setUserId(user.getId().intValue());
                userTunnel.setTunnelId(tunnelId.intValue());
                userTunnel.setInFlow(0L);
                userTunnel.setOutFlow(0L);
                userTunnel.setStatus(ACTIVE);
            } else {
                userTunnel.setInFlow(0L);
                userTunnel.setOutFlow(0L);
            }
            userTunnel.setFlow(plan.getFlow());
            userTunnel.setNum(plan.getMaxRules());
            userTunnel.setFlowResetTime(user.getFlowResetTime());
            userTunnel.setExpTime(expTime);
            if (userTunnel.getId() == null) userTunnelMapper.insert(userTunnel); else userTunnelMapper.updateById(userTunnel);
        }
    }

    private List<Long> resolveUserGroupTunnelIds(Long userGroupId) {
        QueryWrapper<UserGroupDeviceGroup> wrapper = new QueryWrapper<>();
        wrapper.eq("user_group_id", userGroupId).eq("status", ACTIVE);
        List<UserGroupDeviceGroup> mappings = userGroupDeviceGroupMapper.selectList(wrapper);
        List<Long> tunnelIds = new ArrayList<>();
        for (UserGroupDeviceGroup mapping : mappings) {
            DeviceGroup group = deviceGroupMapper.selectById(mapping.getDeviceGroupId());
            if (group != null && Objects.equals(group.getStatus(), ACTIVE)) tunnelIds.addAll(parseIds(group.getTunnelIds()));
        }
        return tunnelIds.stream().distinct().collect(Collectors.toList());
    }

    private void createInviteReward(OrderRecord order, User buyer) {
        if (buyer.getInviterUserId() == null || buyer.getInviterUserId() <= 0 || Objects.equals(buyer.getInviterUserId(), buyer.getId())) return;
        QueryWrapper<OrderRecord> completedWrapper = new QueryWrapper<>();
        completedWrapper.eq("user_id", buyer.getId()).eq("status", ORDER_COMPLETED).ne("id", order.getId());
        boolean renewal = orderRecordMapper.selectCount(completedWrapper) > 0;
        int ratio = getConfigInt(renewal ? INVITE_RENEWAL_RATIO_KEY : INVITE_RATIO_KEY, 0);
        if (ratio <= 0) return;
        BigDecimal rewardAmount = order.getPayableAmount().multiply(BigDecimal.valueOf(ratio)).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        if (rewardAmount.compareTo(BigDecimal.ZERO) <= 0) return;

        long now = System.currentTimeMillis();
        InviteRewardRecord reward = new InviteRewardRecord();
        reward.setOrderId(order.getId());
        reward.setInviterUserId(buyer.getInviterUserId());
        reward.setInviteeUserId(buyer.getId());
        reward.setRewardAmount(rewardAmount);
        reward.setRatio(ratio);
        reward.setType(renewal ? 2 : 1);
        reward.setCreatedTime(now);
        reward.setUpdatedTime(now);
        reward.setStatus(ACTIVE);
        inviteRewardRecordMapper.insert(reward);

        User inviter = userMapper.selectById(buyer.getInviterUserId());
        if (inviter != null) {
            BigDecimal balance = inviter.getInviteBalance() == null ? BigDecimal.ZERO : inviter.getInviteBalance();
            inviter.setInviteBalance(balance.add(rewardAmount));
            inviter.setUpdatedTime(now);
            userMapper.updateById(inviter);
        }
        order.setRewardRatio(ratio);
        order.setRewardAmount(rewardAmount);
        order.setInviterUserId(buyer.getInviterUserId());
        order.setUpdatedTime(now);
        orderRecordMapper.updateById(order);
    }

    private User currentUser() {
        Integer userId = JwtUtil.getUserIdFromToken();
        if (userId == null) return null;
        return userMapper.selectById(userId);
    }

    private void ensureInviteCode(User user) {
        if (StringUtils.hasText(user.getInviteCode())) return;
        user.setInviteCode(generateUniqueInviteCode());
        user.setUpdatedTime(System.currentTimeMillis());
        userMapper.updateById(user);
    }

    private int getConfigInt(String name, int defaultValue) {
        QueryWrapper<ViteConfig> wrapper = new QueryWrapper<>();
        wrapper.eq("name", name);
        ViteConfig config = viteConfigService.getOne(wrapper);
        if (config == null || !StringUtils.hasText(config.getValue())) return defaultValue;
        try {
            return clampRatio(Integer.parseInt(config.getValue()), defaultValue);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Integer clampRatio(Integer value, int defaultValue) {
        int ratio = value == null ? defaultValue : value;
        if (ratio < 0) return 0;
        return Math.min(ratio, 100);
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null) return Collections.emptyList();
        return ids.stream().filter(Objects::nonNull).filter(id -> id > 0).distinct().collect(Collectors.toList());
    }

    private String joinIds(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private List<Long> parseIds(String value) {
        if (!StringUtils.hasText(value)) return Collections.emptyList();
        List<Long> ids = new ArrayList<>();
        for (String item : value.split(",")) {
            try {
                if (StringUtils.hasText(item)) ids.add(Long.valueOf(item.trim()));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("设备组隧道ID格式错误: " + item);
            }
        }
        return ids;
    }

    private String generateUniqueRedeemCode() {
        for (int i = 0; i < 20; i++) {
            String code = "RC" + randomCode(10);
            QueryWrapper<RedeemCode> wrapper = new QueryWrapper<>();
            wrapper.eq("code", code);
            if (redeemCodeMapper.selectCount(wrapper) == 0) return code;
        }
        return "RC" + System.currentTimeMillis();
    }

    private String generateUniqueInviteCode() {
        for (int i = 0; i < 20; i++) {
            String code = "IV" + randomCode(8);
            QueryWrapper<User> wrapper = new QueryWrapper<>();
            wrapper.eq("invite_code", code);
            if (userMapper.selectCount(wrapper) == 0) return code;
        }
        return "IV" + System.currentTimeMillis();
    }

    private String randomCode(int length) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, length).toUpperCase();
    }
}
