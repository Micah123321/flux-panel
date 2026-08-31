package com.admin.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 隧道实体类
 * </p>
 *
 * @since 2025-06-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Tunnel extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 隧道名称
     */
    private String name;

    /**
     * 入口节点ID（单节点或节点组首个节点的兼容字段）
     */
    private Long inNodeId;

    /**
     * 入口节点组ID，为空表示单节点隧道
     */
    private Long inGroupId;

    /**
     * 入口IP (兼容字段)
     */
    private String inIp;

    /**
     * 出口节点ID（单节点或节点组首个节点的兼容字段）
     */
    private Long outNodeId;

    /**
     * 出口节点组ID，为空表示单节点出口
     */
    private Long outGroupId;

    /**
     * 出口IP (兼容字段)
     */
    private String outIp;

    /**
     * 隧道类型（1-端口转发，2-隧道转发）
     */
    private Integer type;

    /**
     * 流量计算类型（1 单向计算上传。2 双向）
     */
    private int flow;

    /**
     * 协议类型
     */
    private String protocol;

    /**
     * 节点组调度策略：fifo/round/rand/hash
     */
    private String strategy;

    /**
     * 流量倍率
     */
    private BigDecimal trafficRatio;


    private String tcpListenAddr;

    private String udpListenAddr;

    private String interfaceName;
}
