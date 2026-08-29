package com.admin.common.task;

import com.admin.common.utils.GostUtil;
import com.admin.entity.Forward;
import com.admin.entity.Tunnel;
import com.admin.entity.User;
import com.admin.entity.UserTunnel;
import com.admin.service.ForwardService;
import com.admin.service.TunnelService;
import com.admin.service.UserService;
import com.admin.service.UserTunnelService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Configuration
@EnableScheduling
public class ResetFlowAsync {

    @Resource
    UserService userService;

    @Resource
    UserTunnelService userTunnelService;

    @Resource
    ForwardService forwardService;

    @Resource
    TunnelService tunnelService;

    private static final long BYTES_TO_GB = 1024L * 1024L * 1024L;

    /**
     * 每天0点执行流量重置任务
     * 查询出用户和隧道的重置流量日期是今天的数据，将上下流量重置为0
     * 考虑当月是29、30天，但是选择是31的这种边界情况
     * 
     * 并发安全说明：
     * - 使用setSql()进行原子SQL更新，只更新流量字段(in_flow, out_flow)
     * - 不会影响DelayQueueManager的到期任务对status等其他字段的更新
     * - 避免了并发修改导致的数据覆盖问题
     */
    @Scheduled(cron = "5 0 0 * * ?")
    public void reset_flow(){
        log.info("开始执行流量重置任务");
        
        try {
            // 获取当前日期信息
            LocalDate today = LocalDate.now();
            int currentDay = today.getDayOfMonth(); // 当前是几号
            int lastDayOfMonth = today.lengthOfMonth(); // 当月最后一天
            
            log.info("当前日期: {}, 当月第{}天, 当月最后一天: {}", today, currentDay, lastDayOfMonth);
            
            // 重置用户流量
            resetUserFlow(currentDay, lastDayOfMonth);
            
            // 重置用户隧道流量
            resetUserTunnelFlow(currentDay, lastDayOfMonth);
            
            // 重置每日流量计数（每日流量限制固定每天0点重新计数）
            // ha-min: 在清零前捕获"已超日限"的转发作为恢复候选集，避免误恢复手动暂停的转发；
            // 升级路径: 为转发增加暂停原因字段后可精确区分
            List<Forward> dailyResumeCandidates = findDailyOverLimitForwards();
            resetDailyFlow();
            
            log.info("流量重置任务执行完成");


            // 处理过期账号
            user();

            // 处理过期隧道
            userTunnel();

            // 恢复因每日流量超限而暂停的转发
            resumeDailyLimitedForwards(dailyResumeCandidates);

            log.info("到期任务执行完成");
            
        } catch (Exception e) {
            log.info("定时任务执行失败", e);
        }
    }
    
    /**
     * 重置用户流量
     * @param currentDay 当前日期（几号）
     * @param lastDayOfMonth 当月最后一天
     */
    private void resetUserFlow(int currentDay, int lastDayOfMonth) {
        try {
            // flowResetTime字段存储的是0-31的数字，0表示不重置，1-31表示每月第几号重置
            // 构建查询条件：重置日期等于今天，或者重置日期大于当月最大天数且今天是月末
            // 排除flowResetTime为0的记录（不重置）
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.ne("flow_reset_time", 0); // 排除不重置的用户
            
            if (currentDay == lastDayOfMonth) {
                // 如果今天是月末，查询重置日期等于今天或者大于当月最大天数的记录
                // 例如：当月30天，但用户设置31号重置，则在30号执行重置
                queryWrapper.and(wrapper -> wrapper.eq("flow_reset_time", currentDay)
                                                  .or().gt("flow_reset_time", lastDayOfMonth));
            } else {
                // 否则只查询重置日期等于今天的记录
                queryWrapper.eq("flow_reset_time", currentDay);
            }
            
            // 查询需要重置的用户
            List<User> usersToReset = userService.list(queryWrapper);
            
            if (usersToReset.isEmpty()) {
                log.info("没有需要重置流量的用户");
                return;
            }
            
            log.info("找到{}个需要重置流量的用户", usersToReset.size());
            
            // 批量重置用户流量 - 使用SQL原子操作避免与到期任务的并发冲突
            for (User user : usersToReset) {
                UpdateWrapper<User> updateWrapper = new UpdateWrapper<>();
                updateWrapper.eq("id", user.getId())
                           .setSql("in_flow = 0, out_flow = 0"); // 使用SQL原子操作，只更新流量字段
                
                boolean success = userService.update(null, updateWrapper);
                if (success) {
                    log.info("用户[ID: {}, 用户名: {}]流量重置成功，重置日期: 每月{}号", 
                           user.getId(), user.getUser(), user.getFlowResetTime());
                } else {
                    log.info("用户[ID: {}, 用户名: {}]流量重置失败", user.getId(), user.getUser());
                }
            }
            
        } catch (Exception e) {
            log.info("重置用户流量失败", e);
        }
    }
    
    /**
     * 重置用户隧道流量
     * @param currentDay 当前日期（几号）
     * @param lastDayOfMonth 当月最后一天
     */
    private void resetUserTunnelFlow(int currentDay, int lastDayOfMonth) {
        try {
            // flowResetTime字段存储的是0-31的数字，0表示不重置，1-31表示每月第几号重置
            // 构建查询条件：重置日期等于今天，或者重置日期大于当月最大天数且今天是月末
            // 排除flowResetTime为0的记录（不重置）
            QueryWrapper<UserTunnel> queryWrapper = new QueryWrapper<>();
            queryWrapper.ne("flow_reset_time", 0); // 排除不重置的用户隧道
            
            if (currentDay == lastDayOfMonth) {
                // 如果今天是月末，查询重置日期等于今天或者大于当月最大天数的记录
                // 例如：当月30天，但用户设置31号重置，则在30号执行重置
                queryWrapper.and(wrapper -> wrapper.eq("flow_reset_time", currentDay)
                                                  .or().gt("flow_reset_time", lastDayOfMonth));
            } else {
                // 否则只查询重置日期等于今天的记录
                queryWrapper.eq("flow_reset_time", currentDay);
            }
            
            // 查询需要重置的用户隧道
            List<UserTunnel> userTunnelsToReset = userTunnelService.list(queryWrapper);
            
            if (userTunnelsToReset.isEmpty()) {
                log.info("没有需要重置流量的用户隧道");
                return;
            }
            
            log.info("找到{}个需要重置流量的用户隧道", userTunnelsToReset.size());
            
            // 批量重置用户隧道流量 - 使用SQL原子操作避免与到期任务的并发冲突
            for (UserTunnel userTunnel : userTunnelsToReset) {
                UpdateWrapper<UserTunnel> updateWrapper = new UpdateWrapper<>();
                updateWrapper.eq("id", userTunnel.getId())
                           .setSql("in_flow = 0, out_flow = 0"); // 使用SQL原子操作，只更新流量字段
                
                boolean success = userTunnelService.update(null, updateWrapper);
                if (success) {
                    log.info("用户隧道[ID: {}, 用户ID: {}, 隧道ID: {}]流量重置成功，重置日期: 每月{}号", 
                           userTunnel.getId(), userTunnel.getUserId(), userTunnel.getTunnelId(), userTunnel.getFlowResetTime());
                } else {
                    log.info("用户隧道[ID: {}, 用户ID: {}, 隧道ID: {}]流量重置失败",
                            userTunnel.getId(), userTunnel.getUserId(), userTunnel.getTunnelId());
                }
            }
            
        } catch (Exception e) {
            log.info("重置用户隧道流量失败", e);
        }
    }

    /**
     * 重置每日流量计数（每天0点全表清零）
     * 每日流量限制与月度流量重置（flow_reset_time）机制无关，固定每天0点重新计数
     */
    private void resetDailyFlow() {
        try {
            UpdateWrapper<User> userWrapper = new UpdateWrapper<>();
            userWrapper.setSql("daily_in_flow = 0, daily_out_flow = 0");
            userService.update(null, userWrapper);

            UpdateWrapper<UserTunnel> userTunnelWrapper = new UpdateWrapper<>();
            userTunnelWrapper.setSql("daily_in_flow = 0, daily_out_flow = 0");
            userTunnelService.update(null, userTunnelWrapper);

            log.info("每日流量计数已全部清零");
        } catch (Exception e) {
            log.info("重置每日流量计数失败", e);
        }
    }

    /**
     * 查找"已超每日流量限制"的转发（用户或隧道任一维度超日限），作为重置后的恢复候选集
     */
    private List<Forward> findDailyOverLimitForwards() {
        List<Forward> pausedForwards = forwardService.list(new QueryWrapper<Forward>().eq("status", 0));
        List<Forward> candidates = new ArrayList<>();
        for (Forward forward : pausedForwards) {
            User user = userService.getById(forward.getUserId());
            if (user == null) continue;
            UserTunnel userTunnel = userTunnelService.getOne(new QueryWrapper<UserTunnel>()
                    .eq("user_id", forward.getUserId()).eq("tunnel_id", forward.getTunnelId()));
            if (userTunnel == null) continue;

            boolean userDailyOver = user.getDailyFlow() != null && user.getDailyFlow() > 0
                    && user.getDailyInFlow() != null && user.getDailyOutFlow() != null
                    && (user.getDailyInFlow() + user.getDailyOutFlow()) >= user.getDailyFlow() * BYTES_TO_GB;
            boolean tunnelDailyOver = userTunnel.getDailyFlow() != null && userTunnel.getDailyFlow() > 0
                    && userTunnel.getDailyInFlow() != null && userTunnel.getDailyOutFlow() != null
                    && (userTunnel.getDailyInFlow() + userTunnel.getDailyOutFlow()) >= userTunnel.getDailyFlow() * BYTES_TO_GB;
            if (userDailyOver || tunnelDailyOver) candidates.add(forward);
        }
        return candidates;
    }

    /**
     * 恢复因每日流量超限而暂停的转发
     * 仅处理重置前已超日限的候选转发，且恢复前重新校验全部限额与状态条件；
     * 用户手动暂停、月度流量超限、到期等原因暂停的转发不会被误恢复。
     */
    private void resumeDailyLimitedForwards(List<Forward> candidates) {
        try {
            if (candidates.isEmpty()) return;

            int resumed = 0;
            for (Forward forward : candidates) {
                User user = userService.getById(forward.getUserId());
                if (user == null) continue;

                UserTunnel userTunnel = userTunnelService.getOne(new QueryWrapper<UserTunnel>()
                        .eq("user_id", forward.getUserId()).eq("tunnel_id", forward.getTunnelId()));
                if (userTunnel == null) continue;

                if (!isForwardAllowed(user, userTunnel)) continue;

                Tunnel tunnel = tunnelService.getById(forward.getTunnelId());
                if (tunnel == null) continue;

                String serviceName = buildServiceName(forward.getId(), forward.getUserId(), userTunnel.getId());
                GostUtil.ResumeService(tunnel.getInNodeId(), serviceName);
                if (tunnel.getType() == 2) {
                    GostUtil.ResumeRemoteService(tunnel.getOutNodeId(), serviceName);
                }
                forward.setStatus(1);
                forwardService.updateById(forward);
                resumed++;
            }
            log.info("每日流量重置后恢复转发 {} 条", resumed);
        } catch (Exception e) {
            log.info("恢复每日流量超限转发失败", e);
        }
    }

    /**
     * 判断用户与隧道两个维度是否都允许转发运行（限额未超、未到期、状态正常）
     */
    private boolean isForwardAllowed(User user, UserTunnel userTunnel) {
        long now = System.currentTimeMillis();
        if (user.getStatus() == null || user.getStatus() != 1) return false;
        if (user.getExpTime() != null && user.getExpTime() <= now) return false;
        if (user.getFlow() != null && user.getInFlow() != null && user.getOutFlow() != null
                && (user.getInFlow() + user.getOutFlow()) >= user.getFlow() * BYTES_TO_GB) return false;
        if (user.getDailyFlow() != null && user.getDailyFlow() > 0
                && user.getDailyInFlow() != null && user.getDailyOutFlow() != null
                && (user.getDailyInFlow() + user.getDailyOutFlow()) >= user.getDailyFlow() * BYTES_TO_GB) return false;

        if (userTunnel.getStatus() == null || userTunnel.getStatus() != 1) return false;
        if (userTunnel.getExpTime() != null && userTunnel.getExpTime() <= now) return false;
        if (userTunnel.getFlow() != null && userTunnel.getInFlow() != null && userTunnel.getOutFlow() != null
                && (userTunnel.getInFlow() + userTunnel.getOutFlow()) >= userTunnel.getFlow() * BYTES_TO_GB) return false;
        if (userTunnel.getDailyFlow() != null && userTunnel.getDailyFlow() > 0
                && userTunnel.getDailyInFlow() != null && userTunnel.getDailyOutFlow() != null
                && (userTunnel.getDailyInFlow() + userTunnel.getDailyOutFlow()) >= userTunnel.getDailyFlow() * BYTES_TO_GB) return false;
        return true;
    }


    public void user(){
        // 查询过期用户
        List<User> user_list = userService.list(new QueryWrapper<User>().ne("role_id", 0).eq("status", 1).isNotNull("exp_time").lt("exp_time", new Date().getTime()));
        for (User user : user_list) {
            // 查询对应转发
            List<Forward> forwardList = forwardService.list(new QueryWrapper<Forward>().eq("user_id", user.getId()).eq("status", 1));
            for (Forward forward : forwardList) {
                UserTunnel userTunnel = userTunnelService.getOne(new QueryWrapper<UserTunnel>().eq("user_id", forward.getUserId()).eq("tunnel_id", forward.getTunnelId()));
                if (userTunnel != null) {
                    pauseForwardService(forward, userTunnel.getId());
                    forward.setStatus(0);
                    forwardService.updateById(forward);
                }
            }
            user.setStatus(0);
            userService.updateById(user);
        }
    }


    public void userTunnel(){
        // 查询过期隧道
        List<UserTunnel> user_tunnel_list = userTunnelService.list(new QueryWrapper<UserTunnel>().eq("status", 1).isNotNull("exp_time").lt("exp_time", new Date().getTime()));
        // 查询对应转发
        for (UserTunnel userTunnel : user_tunnel_list) {
            List<Forward> forwardList = forwardService.list(new QueryWrapper<Forward>().eq("tunnel_id", userTunnel.getTunnelId()).eq("user_id", userTunnel.getUserId()).eq("status", 1));
            for (Forward forward : forwardList) {
                pauseForwardService(forward, userTunnel.getId());
                forward.setStatus(0);
                forwardService.updateById(forward);
            }
            userTunnel.setStatus(0);
            userTunnelService.updateById(userTunnel);
        }
    }


    private void pauseForwardService(Forward forward, Integer userTunnelId) {
        Tunnel tunnel = tunnelService.getById(forward.getTunnelId());
        if (tunnel == null) return;

        GostUtil.PauseService(tunnel.getInNodeId(), buildServiceName(forward.getId(), forward.getUserId(), userTunnelId));
        if (tunnel.getType() == 2){
            GostUtil.PauseRemoteService(tunnel.getOutNodeId(), buildServiceName(forward.getId(), forward.getUserId(), userTunnelId));
        }
    }


    private String buildServiceName(Long forwardId, Integer userId, Integer userTunnelId) {
        return forwardId + "_" + userId + "_" + userTunnelId;
    }
}
