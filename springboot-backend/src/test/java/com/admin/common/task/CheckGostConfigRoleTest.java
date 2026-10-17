package com.admin.common.task;

import com.admin.common.dto.ConfigItem;
import com.admin.common.dto.GostConfigDto;
import com.admin.entity.*;
import com.admin.service.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CheckGostConfigRoleTest {
    private CheckGostConfigAsync task;
    private ForwardService forwards;
    private TunnelService tunnels;
    private AggregateNodeGroupService groups;
    private Forward forward;
    private Tunnel tunnel;

    @BeforeEach
    void setUp() {
        task = new CheckGostConfigAsync();
        forwards = mock(ForwardService.class);
        tunnels = mock(TunnelService.class);
        groups = mock(AggregateNodeGroupService.class);
        ReflectionTestUtils.setField(task, "forwardService", forwards);
        ReflectionTestUtils.setField(task, "tunnelService", tunnels);
        ReflectionTestUtils.setField(task, "aggregateNodeGroupService", groups);
        ReflectionTestUtils.setField(task, "userTunnelService", mock(UserTunnelService.class));
        tunnel = new Tunnel();
        tunnel.setId(9L);
        tunnel.setType(2);
        tunnel.setInNodeId(13L);
        tunnel.setOutNodeId(14L);
        forward = new Forward();
        forward.setId(23L);
        forward.setUserId(1);
        forward.setTunnelId(9);
        forward.setStatus(1);
        when(tunnels.list()).thenReturn(List.of(tunnel));
        when(forwards.list(any(QueryWrapper.class))).thenReturn(List.of(forward));
    }

    private ConfigItem item(String name) {
        ConfigItem item = new ConfigItem();
        item.setName(name);
        return item;
    }

    private void report(long nodeId, boolean chain, String... services) {
        Node node = new Node();
        node.setId(nodeId);
        GostConfigDto config = new GostConfigDto();
        config.setServices(java.util.Arrays.stream(services).map(this::item).toList());
        config.setChains(chain ? List.of(item("23_1_0_chains")) : List.of());
        ReflectionTestUtils.invokeMethod(task, "syncMissingServices", config, node);
    }

    @Test
    void healthyEntryDoesNotNeedExitListener() {
        report(13L, true, "23_1_0_tcp", "23_1_0_udp");
        verify(forwards, never()).updateForwardA(any());
    }

    @Test
    void healthyExitDoesNotNeedEntryListenersOrChain() {
        report(14L, false, "23_1_0_tls");
        verify(forwards, never()).updateForwardA(any());
    }

    @Test
    void incompleteEntryOrChainTriggersRebuild() {
        report(13L, true, "23_1_0_tcp");
        report(13L, false, "23_1_0_tcp", "23_1_0_udp");
        verify(forwards, times(2)).updateForwardA(forward);
    }

    @Test
    void missingExitListenerTriggersRebuild() {
        report(14L, false);
        verify(forwards).updateForwardA(forward);
    }

    @Test
    void sharedNodeRequiresBothRoles() {
        tunnel.setOutNodeId(13L);
        report(13L, true, "23_1_0_tcp", "23_1_0_udp", "23_1_0_tls");
        verify(forwards, never()).updateForwardA(any());
        report(13L, true, "23_1_0_tcp", "23_1_0_udp");
        verify(forwards).updateForwardA(forward);
    }

    @Test
    void plainForwardNeedsOnlyTcpAndUdp() {
        tunnel.setType(1);
        report(13L, false, "23_1_0_tcp", "23_1_0_udp");
        verify(forwards, never()).updateForwardA(any());
    }

    @Test
    void pausedOrFailedRecordsAreNeverAutomaticallyEnabled() {
        forward.setStatus(0);
        report(13L, false);
        forward.setStatus(-1);
        report(13L, false);
        verify(forwards, never()).updateForwardA(any());
    }

    @Test
    void entryGroupMemberIsCheckedUsingItsOwnRole() {
        AggregateNodeGroup group = new AggregateNodeGroup();
        group.setId(100L);
        tunnel.setInGroupId(100L);
        when(groups.getById(100L)).thenReturn(group);
        when(groups.parseNodeIds(group)).thenReturn(List.of(13L, 15L));
        report(15L, true, "23_1_0_tcp", "23_1_0_udp");
        verify(forwards, never()).updateForwardA(any());
        report(15L, false, "23_1_0_tcp", "23_1_0_udp");
        verify(forwards).updateForwardA(forward);
    }
}
