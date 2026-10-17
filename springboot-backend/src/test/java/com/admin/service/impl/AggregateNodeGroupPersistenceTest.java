package com.admin.service.impl;

import com.admin.common.dto.AggregateNodeGroupDto;
import com.admin.common.lang.R;
import com.admin.entity.AggregateNodeGroup;
import com.admin.entity.Node;
import com.admin.mapper.AggregateNodeGroupMapper;
import com.admin.mapper.TunnelMapper;
import com.admin.service.AggregateForwardService;
import com.admin.service.ForwardService;
import com.admin.service.NodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AggregateNodeGroupPersistenceTest {
    private AggregateNodeGroupServiceImpl service;
    private AggregateNodeGroupMapper mapper;
    private NodeService nodes;
    private ForwardService forwards;
    private AggregateForwardService aggregates;
    private TunnelMapper tunnels;
    private AggregateNodeGroup group;
    private Node first;
    private Node second;

    @BeforeEach
    void setUp() {
        service = new AggregateNodeGroupServiceImpl();
        mapper = mock(AggregateNodeGroupMapper.class);
        nodes = mock(NodeService.class);
        forwards = mock(ForwardService.class);
        aggregates = mock(AggregateForwardService.class);
        tunnels = mock(TunnelMapper.class);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        ReflectionTestUtils.setField(service, "nodeService", nodes);
        ReflectionTestUtils.setField(service, "forwardService", forwards);
        ReflectionTestUtils.setField(service, "aggregateForwardService", aggregates);
        ReflectionTestUtils.setField(service, "tunnelMapper", tunnels);
        group = new AggregateNodeGroup();
        group.setId(1L);
        group.setName("saved-members");
        group.setNodeIds("20,10");
        group.setUpdatedTime(123L);
        first = node(20L, 1);
        second = node(10L, 0);
        when(mapper.selectById(1L)).thenReturn(group);
        when(mapper.selectList(any())).thenReturn(List.of(group));
        when(nodes.listByIds(anyCollection())).thenReturn(List.of(second, first));
    }

    @Test
    void listingPreservesOfflineMembersAndTheirOrder() {
        R result = service.listGroups();
        assertEquals(0, result.getCode());
        @SuppressWarnings("unchecked") List<Map<String, Object>> data = (List<Map<String, Object>>) result.getData();
        assertEquals(List.of(20L, 10L), data.get(0).get("nodeIds"));
        @SuppressWarnings("unchecked") List<Map<String, Object>> members = (List<Map<String, Object>>) data.get(0).get("nodes");
        assertEquals(20L, members.get(0).get("id"));
        assertEquals(10L, members.get(1).get("id"));
        assertEquals(0, members.get(1).get("status"));
        assertNoMembershipWrites();
    }

    @Test
    void temporaryAllOfflineAndReconnectNeverEraseSavedMembers() {
        first.setStatus(0);
        assertEquals(0, service.pruneOfflineNode(first.getId()).getCode());
        assertEquals(0, service.pruneOfflineNode(second.getId()).getCode());
        assertEquals(0, service.listGroups().getCode());
        first.setStatus(1);
        second.setStatus(1);
        assertEquals(0, service.listGroups().getCode());
        assertEquals(List.of(20L, 10L), service.parseNodeIds(group));
        assertNoMembershipWrites();
    }

    @Test
    void runtimeResolutionEntryPointDoesNotPersistOfflineFiltering() {
        assertEquals(0, service.pruneOfflineNodes(1L).getCode());
        assertNoMembershipWrites();
    }

    @Test
    void temporarilyMissingOrUnknownStatusNodesRetainConfiguredIds() {
        when(nodes.listByIds(anyCollection())).thenReturn(List.of(first));
        first.setStatus(null);
        assertEquals(0, service.pruneOfflineNodes(1L).getCode());
        assertEquals(0, service.listGroups().getCode());
        assertNoMembershipWrites();
    }

    @Test
    void restartingServicePreservesMembersWhileNodesReconnect() {
        first.setStatus(0);
        second.setStatus(0);
        for (int i = 0; i < 2; i++) {
            AggregateNodeGroupServiceImpl restarted = new AggregateNodeGroupServiceImpl();
            ReflectionTestUtils.setField(restarted, "baseMapper", mapper);
            ReflectionTestUtils.setField(restarted, "nodeService", nodes);
            assertEquals(0, restarted.listGroups().getCode());
            assertEquals(List.of(20L, 10L), restarted.parseNodeIds(group));
        }
        assertNoMembershipWrites();
    }

    @Test
    void explicitMemberEditingStillPersistsAndSynchronizes() {
        AggregateNodeGroupDto dto = new AggregateNodeGroupDto();
        dto.setId(1L);
        dto.setName("saved-members");
        dto.setNodeIds(List.of(10L));
        when(nodes.listByIds(List.of(10L))).thenReturn(List.of(second));
        when(nodes.getById(10L)).thenReturn(second);
        when(mapper.updateById(any())).thenReturn(1);
        when(tunnels.selectList(any())).thenReturn(Collections.emptyList());
        when(forwards.syncNodeGroupForwards(eq(1L), anyList(), anyList())).thenReturn(R.ok());
        when(aggregates.syncNodeGroupForwards(eq(1L), anyList(), anyList())).thenReturn(R.ok());
        assertEquals(0, service.updateGroup(dto).getCode());
        assertEquals("10", group.getNodeIds());
        verify(mapper).updateById(group);
        verify(forwards).syncNodeGroupForwards(1L, List.of(20L, 10L), List.of(10L));
        verify(aggregates).syncNodeGroupForwards(1L, List.of(20L, 10L), List.of(10L));
    }

    @Test
    void missingGroupAndNodeIdStillReturnErrors() {
        assertNotEquals(0, service.pruneOfflineNodes(99L).getCode());
        assertNotEquals(0, service.pruneOfflineNode(null).getCode());
        assertNoMembershipWrites();
    }

    private void assertNoMembershipWrites() {
        assertEquals("20,10", group.getNodeIds());
        assertEquals(123L, group.getUpdatedTime());
        verify(mapper, never()).updateById(any());
        verify(mapper, never()).update(any(), any());
        verifyNoInteractions(forwards, aggregates, tunnels);
    }

    private Node node(long id, Integer status) {
        Node node = new Node();
        node.setId(id);
        node.setName("node-" + id);
        node.setStatus(status);
        return node;
    }
}
