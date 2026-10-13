package com.admin.service.impl;

import com.admin.common.dto.TunnelListDto;
import com.admin.entity.AggregateNodeGroup;
import com.admin.entity.Node;
import com.admin.entity.Tunnel;
import com.admin.mapper.TunnelMapper;
import com.admin.service.AggregateNodeGroupService;
import com.admin.service.NodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class TunnelEntryAddressesTest {

    private TunnelServiceImpl service;
    private TunnelMapper tunnelMapper;
    private NodeService nodeService;
    private AggregateNodeGroupService groupService;

    @BeforeEach
    void setUp() {
        tunnelMapper = mock(TunnelMapper.class);
        nodeService = mock(NodeService.class);
        groupService = mock(AggregateNodeGroupService.class);
        service = new TunnelServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", tunnelMapper);
        service.nodeService = nodeService;
        service.aggregateNodeGroupService = groupService;
        when(nodeService.listByIds(anyCollection())).thenReturn(Collections.emptyList());
        when(groupService.listByIds(anyCollection())).thenReturn(Collections.emptyList());
        when(groupService.parseNodeIds(any())).thenAnswer(invocation ->
                new AggregateNodeGroupServiceImpl().parseNodeIds(invocation.getArgument(0)));
    }

    @Test
    void oldSingleNodeTunnelsDisplayNewIpv6WithoutChangingEitherDialingAddress() {
        Node entry = node(10L, "192.0.2.10,2001:db8::10", "198.51.100.10", 1000, 9000);
        when(nodeService.listByIds(anyCollection())).thenReturn(Collections.singletonList(entry));
        Tunnel forwarding = tunnel(1L, 10L, null, "192.0.2.10");
        forwarding.setType(1);
        forwarding.setOutNodeId(10L);
        forwarding.setOutIp("198.51.100.10");
        Tunnel relaying = tunnel(2L, 10L, null, "192.0.2.10");

        List<TunnelListDto> dtos = assertBothLists(Arrays.asList(forwarding, relaying),
                "192.0.2.10,2001:db8::10", "192.0.2.10,2001:db8::10");

        assertEquals(1000, dtos.get(0).getInNodePortSta());
        assertEquals(9000, dtos.get(0).getInNodePortEnd());
        verify(nodeService, times(2)).listByIds(Collections.singleton(10L));
        verifyNoMoreInteractions(nodeService);
        verifyNoInteractions(groupService);
    }

    @Test
    void groupsDisplayEveryMembersFullAddressesInMemberOrderIncludingOfflineNodes() {
        AggregateNodeGroup entryGroup = group(50L, "entry", "20,10");
        AggregateNodeGroup exitGroup = group(60L, "exit", "90");
        Node first = node(20L, "192.0.2.20,2001:db8::20", "198.51.100.20", 2000, 8000);
        first.setStatus(0);
        Node second = node(10L, "192.0.2.10,2001:db8::10", "198.51.100.10", 1000, 9000);
        when(groupService.listByIds(anyCollection())).thenReturn(Arrays.asList(entryGroup, exitGroup));
        when(nodeService.listByIds(anyCollection())).thenReturn(Arrays.asList(second, first));
        Tunnel saved = tunnel(1L, 10L, 50L, "192.0.2.10");
        saved.setOutGroupId(60L);

        TunnelListDto dto = assertBothLists(Collections.singletonList(saved),
                "192.0.2.20,2001:db8::20,192.0.2.10,2001:db8::10").get(0);

        assertEquals("entry", dto.getInGroupName());
        assertEquals("exit", dto.getOutGroupName());
        assertEquals(2000, dto.getInNodePortSta());
        assertEquals(8000, dto.getInNodePortEnd());
        assertEquals("20,10", entryGroup.getNodeIds());
        assertEquals(0, first.getStatus());
        assertReadOnlyBatches(true);
    }

    @Test
    void nullEmptyAndWhitespaceEntryAddressesFallBackToServerIp() {
        when(nodeService.listByIds(anyCollection())).thenReturn(Arrays.asList(
                node(10L, null, "198.51.100.10", 1000, 9000),
                node(20L, "", "2001:db8::20", 1000, 9000),
                node(30L, "   ", " 198.51.100.30 ", 1000, 9000)));

        assertBothLists(Arrays.asList(
                tunnel(1L, 10L, null, "old-10"),
                tunnel(2L, 20L, null, "old-20"),
                tunnel(3L, 30L, null, "old-30")),
                "198.51.100.10", "2001:db8::20", "198.51.100.30");

        assertReadOnlyBatches(false);
    }

    @Test
    void groupMembersWithEmptyEntryAddressesContributeTheirServerIp() {
        AggregateNodeGroup group = group(50L, "entry", "10,20");
        when(groupService.listByIds(anyCollection())).thenReturn(Collections.singletonList(group));
        when(nodeService.listByIds(anyCollection())).thenReturn(Arrays.asList(
                node(10L, "192.0.2.10,2001:db8::10", "198.51.100.10", 1000, 9000),
                node(20L, " ", "2001:db8::20", 1000, 9000)));

        assertBothLists(Collections.singletonList(tunnel(1L, 10L, 50L, "old")),
                "192.0.2.10,2001:db8::10,2001:db8::20");

        assertReadOnlyBatches(true);
    }

    @Test
    void missingNodesAndNodesWithoutAnyAddressPreserveHistoricalEntryIp() {
        when(nodeService.listByIds(anyCollection())).thenReturn(Collections.singletonList(
                node(20L, " ", null, 1000, 9000)));

        List<TunnelListDto> dtos = assertBothLists(Arrays.asList(
                tunnel(1L, 10L, null, "192.0.2.10,2001:db8::10"),
                tunnel(2L, 20L, null, "192.0.2.20"),
                tunnel(3L, null, null, "192.0.2.30")),
                "192.0.2.10,2001:db8::10", "192.0.2.20", "192.0.2.30");

        assertNull(dtos.get(0).getInNodePortSta());
        assertNull(dtos.get(2).getInNodePortSta());
        assertReadOnlyBatches(false);
    }

    @Test
    void missingGroupUsesCompatibleEntryNodeOrHistoricalAddress() {
        when(nodeService.listByIds(anyCollection())).thenReturn(Collections.singletonList(
                node(10L, "192.0.2.10,2001:db8::10", "198.51.100.10", 1000, 9000)));

        List<TunnelListDto> dtos = assertBothLists(Arrays.asList(
                tunnel(1L, 10L, 50L, "old-10"),
                tunnel(2L, 20L, 50L, "old-20")),
                "192.0.2.10,2001:db8::10", "old-20");

        assertNull(dtos.get(0).getInGroupName());
        assertNull(dtos.get(0).getInNodePortSta());
        assertReadOnlyBatches(true);
    }

    @Test
    void missingGroupMembersKeepSurvivingAddressesWithoutExpandingPortRange() {
        AggregateNodeGroup group = group(50L, "entry", "10,20");
        when(groupService.listByIds(anyCollection())).thenReturn(Collections.singletonList(group));
        when(nodeService.listByIds(anyCollection())).thenReturn(Collections.singletonList(
                node(10L, "192.0.2.10,2001:db8::10", "198.51.100.10", 1000, 9000)));

        TunnelListDto dto = assertBothLists(Collections.singletonList(tunnel(1L, 10L, 50L, "old")),
                "192.0.2.10,2001:db8::10").get(0);

        assertNull(dto.getInNodePortSta());
        assertNull(dto.getInNodePortEnd());
        assertEquals("10,20", group.getNodeIds());
        assertReadOnlyBatches(true);
    }

    @Test
    void lostOrEmptyGroupMembersKeepHistoryInsteadOfUsingAFormerNonMember() {
        AggregateNodeGroup lost = group(50L, "lost", "20");
        AggregateNodeGroup empty = group(60L, "empty", "");
        when(groupService.listByIds(anyCollection())).thenReturn(Arrays.asList(lost, empty));
        when(nodeService.listByIds(anyCollection())).thenReturn(Collections.singletonList(
                node(10L, "192.0.2.10,2001:db8::10", "198.51.100.10", 1000, 9000)));

        List<TunnelListDto> dtos = assertBothLists(Arrays.asList(
                tunnel(1L, 10L, 50L, "old-50"),
                tunnel(2L, 10L, 60L, "old-60")), "old-50", "old-60");

        assertNull(dtos.get(0).getInNodePortSta());
        assertNull(dtos.get(1).getInNodePortSta());
        assertReadOnlyBatches(true);
    }

    @Test
    void emptyListsDoNotQueryNodesOrGroups() {
        assertBothLists(Collections.emptyList());
        verifyNoInteractions(nodeService, groupService);
    }

    @SuppressWarnings("unchecked")
    private List<TunnelListDto> assertBothLists(List<Tunnel> saved, String... expected) {
        List<Tunnel> snapshots = saved.stream().map(tunnel -> {
            Tunnel snapshot = new Tunnel();
            BeanUtils.copyProperties(tunnel, snapshot);
            return snapshot;
        }).collect(Collectors.toList());
        when(tunnelMapper.selectList(any())).thenReturn(saved);

        List<Tunnel> displayed = (List<Tunnel>) service.getAllTunnels().getData();
        assertEquals(Arrays.asList(expected), displayed.stream().map(Tunnel::getInIp).collect(Collectors.toList()));
        for (int i = 0; i < saved.size(); i++) {
            assertNotSame(saved.get(i), displayed.get(i));
            assertEquals(saved.get(i).getOutIp(), displayed.get(i).getOutIp());
            assertEquals(saved.get(i).getOutNodeId(), displayed.get(i).getOutNodeId());
            assertEquals(saved.get(i).getOutGroupId(), displayed.get(i).getOutGroupId());
            Tunnel originalFields = new Tunnel();
            BeanUtils.copyProperties(displayed.get(i), originalFields);
            originalFields.setInIp(snapshots.get(i).getInIp());
            assertEquals(snapshots.get(i), originalFields);
        }
        List<TunnelListDto> dtos = ReflectionTestUtils.invokeMethod(service, "convertToTunnelListDtos", saved);
        assertNotNull(dtos);
        assertEquals(Arrays.asList(expected), dtos.stream().map(TunnelListDto::getIp).collect(Collectors.toList()));
        assertEquals(snapshots, saved);
        verify(tunnelMapper).selectList(any());
        verifyNoMoreInteractions(tunnelMapper);
        return dtos;
    }

    private void assertReadOnlyBatches(boolean queriesGroups) {
        verify(nodeService, times(2)).listByIds(anyCollection());
        verifyNoMoreInteractions(nodeService);
        if (queriesGroups) {
            verify(groupService, times(2)).listByIds(anyCollection());
        }
        verify(groupService, atLeast(0)).parseNodeIds(any());
        verifyNoMoreInteractions(groupService);
    }

    private Tunnel tunnel(Long id, Long nodeId, Long groupId, String historicalIp) {
        Tunnel tunnel = new Tunnel();
        tunnel.setId(id);
        tunnel.setName("tunnel-" + id);
        tunnel.setInNodeId(nodeId);
        tunnel.setInGroupId(groupId);
        tunnel.setInIp(historicalIp);
        tunnel.setOutNodeId(90L);
        tunnel.setOutIp("198.51.100.90");
        tunnel.setType(2);
        tunnel.setStatus(1);
        tunnel.setStrategy("round");
        tunnel.setCreatedTime(100L);
        tunnel.setUpdatedTime(200L);
        return tunnel;
    }

    private Node node(Long id, String ip, String serverIp, int portStart, int portEnd) {
        Node node = new Node();
        node.setId(id);
        node.setName("node-" + id);
        node.setIp(ip);
        node.setServerIp(serverIp);
        node.setPortSta(portStart);
        node.setPortEnd(portEnd);
        node.setStatus(1);
        return node;
    }

    private AggregateNodeGroup group(Long id, String name, String nodeIds) {
        AggregateNodeGroup group = new AggregateNodeGroup();
        group.setId(id);
        group.setName(name);
        group.setNodeIds(nodeIds);
        return group;
    }
}
