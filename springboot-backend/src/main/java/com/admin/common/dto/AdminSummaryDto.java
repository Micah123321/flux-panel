package com.admin.common.dto;

import lombok.Data;

import java.util.List;

/**
 * 管理员全站汇总DTO
 */
@Data
public class AdminSummaryDto {

    /**
     * 全站统计
     */
    private TotalsDto totals;

    /**
     * 隧道用量统计列表
     */
    private List<TunnelStatDto> tunnelStats;

    /**
     * 用户用量排行（前10）
     */
    private List<TopUserDto> topUsers;

    /**
     * 最近流量按小时聚合（statistics_flow 保留48小时）
     */
    private List<HourFlowDto> recentFlows;

    /**
     * 全站统计
     */
    @Data
    public static class TotalsDto {
        private long userCount;
        private long activeUsers;
        private long disabledUsers;
        private long forwardCount;
        private long tunnelCount;
        private long nodeCount;
        private long totalUsedFlow;
    }

    /**
     * 隧道用量统计
     */
    @Data
    public static class TunnelStatDto {
        private Integer tunnelId;
        private String tunnelName;
        private long userCount;
        private long forwardCount;
        private long usedFlow;
    }

    /**
     * 用户用量排行
     */
    @Data
    public static class TopUserDto {
        private Long userId;
        private String userName;
        private Integer status;
        private long usedFlow;
        private long forwardCount;
        private Long flowQuota;
    }

    /**
     * 小时流量
     */
    @Data
    public static class HourFlowDto {
        private String time;
        private long flow;
    }
}
