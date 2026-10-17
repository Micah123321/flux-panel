package com.admin.service.impl;

import com.admin.common.lang.R;
import com.admin.common.utils.WebSocketServer;
import com.admin.controller.ForwardController;
import com.admin.entity.AggregateNodeGroup;
import com.admin.entity.Forward;
import com.admin.entity.Node;
import com.admin.entity.Tunnel;
import com.admin.mapper.ForwardMapper;
import com.admin.service.AggregateNodeGroupService;
import com.admin.service.NodeService;
import com.admin.service.TunnelService;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ForwardUdpDiagnosisTest {
    private final List<JSONObject> requests = new ArrayList<>();
    private final List<Long> sessionIds = new ArrayList<>();
    private final WebSocketServer server = new WebSocketServer();
    private ForwardServiceImpl service;
    private ForwardController controller;
    private Forward forward;
    private Tunnel tunnel;
    private Node entry;
    private Node exit;
    private NodeService nodes;
    private AggregateNodeGroupService groups;
    private Consumer<JSONObject> mutateData;
    private String responseStatus;
    private String responseMessage;
    private boolean omitData;

    @BeforeEach
    void setUp() throws Exception {
        service = new ForwardServiceImpl();
        controller = new ForwardController();
        nodes = mock(NodeService.class);
        groups = mock(AggregateNodeGroupService.class);
        ForwardMapper mapper = mock(ForwardMapper.class);
        TunnelService tunnels = mock(TunnelService.class);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        ReflectionTestUtils.setField(service, "nodeService", nodes);
        ReflectionTestUtils.setField(service, "tunnelService", tunnels);
        ReflectionTestUtils.setField(service, "aggregateNodeGroupService", groups);
        ReflectionTestUtils.setField(controller, "forwardService", service);
        forward = new Forward();
        forward.setId(7L);
        forward.setUserId(8);
        forward.setTunnelId(9);
        forward.setName("udp-forward");
        forward.setRemoteAddr("192.0.2.53:53");
        forward.setInPort(1053);
        forward.setOutPort(2053);
        tunnel = new Tunnel();
        tunnel.setId(9L);
        tunnel.setType(1);
        tunnel.setInNodeId(94001L);
        tunnel.setOutNodeId(94002L);
        tunnel.setProtocol("tls");
        tunnel.setUdpListenAddr("0.0.0.0");
        entry = node(94001L);
        exit = node(94002L);
        when(mapper.selectById(7L)).thenReturn(forward);
        when(tunnels.getById(9)).thenReturn(tunnel);
        when(nodes.getNodeById(94001L)).thenReturn(entry);
        when(nodes.getNodeById(94002L)).thenReturn(exit);
        responseStatus = "reachable";
        responseMessage = "OK";
        bindUser(8);
        connect(entry);
        connect(exit);
    }

    @AfterEach
    void tearDown() {
        Map<Long, WebSocketSession> sessions = sessions();
        Map<String, Object> locks = (Map<String, Object>) ReflectionTestUtils.getField(WebSocketServer.class, "sessionLocks");
        for (Long id : sessionIds) {
            sessions.remove(id);
            locks.remove("udp-test-" + id);
        }
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void oldOverloadAndMissingProtocolStillUseTcp() {
        List<ForwardServiceImpl.DiagnosisResult> legacy = results(service.diagnoseForward(7L));
        List<ForwardServiceImpl.DiagnosisResult> defaults = results(controller.diagnoseForward(Map.of("forwardId", 7)));
        assertEquals(2, requests.size());
        for (JSONObject request : requests) {
            assertEquals("TcpPing", request.getString("type"));
            assertEquals(2, request.getJSONObject("data").getIntValue("count"));
            assertEquals(3000, request.getJSONObject("data").getIntValue("timeout"));
        }
        assertEquals("tcp", legacy.get(0).getProtocol());
        assertEquals("tcp", defaults.get(0).getProtocol());
        assertEquals("reachable", legacy.get(0).getStatus());
        assertTrue(legacy.get(0).isSuccess());
    }

    @Test
    void udpOptionsAndLocalForwardPathAreSentOverRealWebSocketDispatch() {
        R report = controller.diagnoseForward(Map.of("forwardId", 7, "protocol", "udp", "payload", "Aa 00 FF"));
        List<ForwardServiceImpl.DiagnosisResult> results = results(report);
        assertEquals("udp", ((Map<?, ?>) report.getData()).get("protocol"));
        assertEquals(2, requests.size());
        for (JSONObject request : requests) {
            assertEquals("UdpPing", request.getString("type"));
            JSONObject data = request.getJSONObject("data");
            assertEquals(2, data.getIntValue("count"));
            assertEquals(1500, data.getIntValue("timeout"));
            assertEquals("aa00ff", data.getString("payload"));
        }
        ForwardServiceImpl.DiagnosisResult path = results.get(0);
        assertEquals("127.0.0.1", path.getTargetIp());
        assertEquals(1053, path.getTargetPort());
        assertTrue(path.getDescription().contains("本地UDP入口"));
        assertEquals("192.0.2.53", results.get(1).getTargetIp());
        for (ForwardServiceImpl.DiagnosisResult result : results) {
            assertEquals("udp", result.getProtocol());
            assertEquals("reachable", result.getStatus());
            assertTrue(result.isSuccess());
            assertEquals(2, result.getSentPackets());
            assertEquals(2, result.getReceivedPackets());
            assertEquals(1.25, result.getAverageTime());
            assertEquals(0.0, result.getPacketLoss());
            assertTrue(result.getMessage().contains("回包"));
        }
    }

    @Test
    void omittedAndEmptyPayloadDelegateToNodeDefaultAndMaximumIsAccepted() {
        for (String payload : new String[] {null, "", "  ", "ab".repeat(1024)}) {
            requests.clear();
            results(service.diagnoseForward(7L, "udp", payload));
            String expected = payload == null ? "" : payload.replace(" ", "");
            assertEquals(expected, requests.get(0).getJSONObject("data").getString("payload"));
        }
    }

    @Test
    void rejectsInvalidProtocolPayloadTypesHexLengthAndForwardIdsBeforeDispatch() {
        for (String protocol : new String[] {"", "TCP", "icmp", " udp"}) {
            assertError(service.diagnoseForward(7L, protocol, null));
        }
        for (String payload : new String[] {"0", "xyz", "0x12", "aa-ff", "ab".repeat(1025)}) {
            assertError(service.diagnoseForward(7L, "udp", payload));
        }
        assertError(controller.diagnoseForward(Map.of("forwardId", 7, "protocol", 1)));
        assertError(controller.diagnoseForward(Map.of("forwardId", 7, "payload", List.of("ab"))));
        assertError(controller.diagnoseForward(Map.of("forwardId", "bad")));
        assertError(controller.diagnoseForward(Map.of()));
        assertError(controller.diagnoseForward(null));
        assertTrue(requests.isEmpty());
    }

    @Test
    void explicitUnreachableHasNoLatencyOrLossAndNeverSucceeds() {
        responseStatus = "unreachable";
        for (ForwardServiceImpl.DiagnosisResult result : results(service.diagnoseForward(7L, "udp", null))) {
            assertUnconfirmed(result, "unreachable");
            assertEquals(2, result.getSentPackets());
            assertEquals(0, result.getReceivedPackets());
            assertTrue(result.getMessage().contains("明确不可达"));
        }
    }

    @Test
    void immediateNetworkUnreachableCanPrecedeTheFirstSentPacket() {
        responseStatus = "unreachable";
        mutateData = data -> data.put("sentPackets", 0);
        for (ForwardServiceImpl.DiagnosisResult result : results(service.diagnoseForward(7L, "udp", null))) {
            assertUnconfirmed(result, "unreachable");
            assertEquals(0, result.getSentPackets());
        }
    }

    @Test
    void timeoutIsUnknownEvenWithCommandOkAndCompletePacketCounters() {
        responseStatus = "unknown";
        for (ForwardServiceImpl.DiagnosisResult result : results(service.diagnoseForward(7L, "udp", null))) {
            assertUnconfirmed(result, "unknown");
            assertEquals(2, result.getSentPackets());
            assertEquals(0, result.getReceivedPackets());
            assertTrue(result.getMessage().contains("未确认"));
        }
    }

    @Test
    void legacyCommandOkWithoutDataAndUnknownCommandsRemainUnknown() {
        omitData = true;
        for (String message : new String[] {"OK", "unknown command: UdpPing", "unsupported command", "未知命令"}) {
            responseMessage = message;
            for (ForwardServiceImpl.DiagnosisResult result : results(service.diagnoseForward(7L, "udp", null))) {
                assertUnconfirmed(result, "unknown");
                assertTrue(result.getMessage().contains("更新节点"));
            }
        }
    }

    @Test
    void malformedAndInconsistentResponsesNeverProduceSuccess() {
        List<Consumer<JSONObject>> mutations = List.of(
                data -> data.clear(),
                data -> data.remove("status"),
                data -> data.put("status", "ok"),
                data -> data.put("status", "unknown"),
                data -> data.put("success", false),
                data -> data.put("success", "true"),
                data -> data.put("ip", "192.0.2.99"),
                data -> data.put("port", 9999),
                data -> data.put("port", "53"),
                data -> data.remove("requestId"),
                data -> data.put("sentPackets", 3),
                data -> data.put("sentPackets", 1.5),
                data -> data.put("receivedPackets", 0),
                data -> data.put("receivedPackets", 3),
                data -> data.remove("averageTime"),
                data -> data.put("averageTime", -1),
                data -> data.put("averageTime", "1.25"),
                data -> data.put("packetLoss", 100),
                data -> data.put("errorMessage", List.of("invalid")));
        for (Consumer<JSONObject> mutation : mutations) {
            mutateData = mutation;
            for (ForwardServiceImpl.DiagnosisResult result : results(service.diagnoseForward(7L, "udp", null))) {
                assertUnconfirmed(result, "unknown");
                assertTrue(result.getMessage().contains("字段"), result.getMessage());
                assertNull(result.getSentPackets());
            }
        }
    }

    @Test
    void malformedUnreachableMetricsRemainUnknownAndNullMetricsAreAccepted() {
        responseStatus = "unreachable";
        mutateData = data -> data.put("averageTime", "invalid");
        for (ForwardServiceImpl.DiagnosisResult result : results(service.diagnoseForward(7L, "udp", null))) {
            assertUnconfirmed(result, "unknown");
        }
        mutateData = data -> {
            data.put("averageTime", null);
            data.put("packetLoss", null);
        };
        for (ForwardServiceImpl.DiagnosisResult result : results(service.diagnoseForward(7L, "udp", null))) {
            assertUnconfirmed(result, "unreachable");
        }
    }

    @Test
    void commandErrorsAndEmptyReplyRemainUnknown() {
        omitData = true;
        for (String message : new String[] {null, "", "等待响应超时", "节点连接已断开"}) {
            responseMessage = message;
            for (ForwardServiceImpl.DiagnosisResult result : results(service.diagnoseForward(7L, "udp", null))) {
                assertUnconfirmed(result, "unknown");
                assertTrue(result.getMessage().contains("未确认"));
            }
        }
    }

    @Test
    void tcpTunnelKeepsTransportAndExitProbes() {
        tunnel.setType(2);
        List<ForwardServiceImpl.DiagnosisResult> results = results(service.diagnoseForward(7L, "tcp", null));
        assertEquals(2, results.size());
        assertEquals("入口->出口", results.get(0).getDescription());
        assertEquals(2053, results.get(0).getTargetPort());
        assertTrue(results.stream().allMatch(result -> "tcp".equals(result.getProtocol())));
        assertTrue(requests.stream().allMatch(request -> "TcpPing".equals(request.getString("type"))));
    }

    @Test
    void partialRepliesAreReachableOnlyWithConsistentLoss() {
        mutateData = data -> {
            data.put("receivedPackets", 1);
            data.put("packetLoss", 50);
        };
        for (ForwardServiceImpl.DiagnosisResult result : results(service.diagnoseForward(7L, "udp", null))) {
            assertTrue(result.isSuccess());
            assertEquals("reachable", result.getStatus());
            assertEquals(50.0, result.getPacketLoss());
        }
    }

    @Test
    void listenerSelectionNormalizesWildcardAndIpv6Bindings() {
        String[][] addresses = {{"", "127.0.0.1"}, {"0.0.0.0", "127.0.0.1"},
                {"::", "::1"}, {"[::]", "::1"}, {"[2001:db8::1]", "2001:db8::1"},
                {"192.0.2.10", "192.0.2.10"}};
        for (String[] pair : addresses) {
            tunnel.setUdpListenAddr(pair[0]);
            assertEquals(pair[1], results(service.diagnoseForward(7L, "udp", null)).get(0).getTargetIp());
        }
        tunnel.setUdpListenAddr(null);
        assertEquals("127.0.0.1", results(service.diagnoseForward(7L, "udp", null)).get(0).getTargetIp());
    }

    @Test
    void tunnelUdpSkipsTransportPortForBothTlsAndQuicAndChecksBothDirectPaths() {
        tunnel.setType(2);
        for (String transport : List.of("tls", "quic")) {
            tunnel.setProtocol(transport);
            requests.clear();
            List<ForwardServiceImpl.DiagnosisResult> results = results(service.diagnoseForward(7L, "udp", null));
            assertEquals(3, results.size());
            assertEquals(3, requests.size());
            assertTrue(results.stream().noneMatch(result -> result.getTargetPort().equals(2053)));
            assertTrue(results.stream().anyMatch(result -> result.getDescription().equals("出口->目标（直达）")));
        }
    }

    @Test
    void sharedEntryAndExitNodeStillReportsBothRoles() {
        tunnel.setType(2);
        tunnel.setOutNodeId(entry.getId());
        List<ForwardServiceImpl.DiagnosisResult> results = results(service.diagnoseForward(7L, "udp", null));
        assertEquals(3, results.size());
        assertEquals("入口->目标（直达）", results.get(1).getDescription());
        assertEquals("出口->目标（直达）", results.get(2).getDescription());
    }

    @Test
    void offlineAndDisconnectedNodesRemainUnknown() {
        entry.setStatus(0);
        for (ForwardServiceImpl.DiagnosisResult result : results(service.diagnoseForward(7L, "udp", null))) {
            assertUnconfirmed(result, "unknown");
            assertTrue(result.getMessage().contains("离线"));
        }
        assertTrue(requests.isEmpty());
        entry.setStatus(1);
        sessions().remove(entry.getId());
        for (ForwardServiceImpl.DiagnosisResult result : results(service.diagnoseForward(7L, "udp", null))) {
            assertUnconfirmed(result, "unknown");
            assertTrue(result.getMessage().contains("节点不在线"));
        }
    }

    @Test
    void groupedEntriesKeepOrderAndDiagnoseOfflineMembersWithoutPruning() {
        tunnel.setInGroupId(100L);
        AggregateNodeGroup group = new AggregateNodeGroup();
        group.setId(100L);
        when(groups.getById(100L)).thenReturn(group);
        when(groups.parseNodeIds(group)).thenReturn(List.of(exit.getId(), entry.getId()));
        when(nodes.listByIds(anyCollection())).thenReturn(List.of(entry, exit));
        entry.setStatus(0);
        List<ForwardServiceImpl.DiagnosisResult> results = results(service.diagnoseForward(7L, "udp", null));
        assertEquals(4, results.size());
        assertEquals(exit.getId(), results.get(0).getNodeId());
        assertEquals(entry.getId(), results.get(1).getNodeId());
        assertEquals("127.0.0.1", results.get(0).getTargetIp());
        assertEquals("127.0.0.1", results.get(1).getTargetIp());
        assertUnconfirmed(results.get(1), "unknown");
        assertUnconfirmed(results.get(3), "unknown");
        verify(groups, never()).pruneOfflineNodes(anyLong());
    }

    @Test
    void existingOwnerCheckStillRejectsOtherUsersBeforeNodeDispatch() {
        bindUser(99);
        assertError(service.diagnoseForward(7L, "udp", null));
        assertTrue(requests.isEmpty());
        verifyNoInteractions(nodes);
    }

    private void connect(Node node) throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("udp-test-" + node.getId());
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(new HashMap<>(Map.of("id", node.getId().toString(), "type", "0")));
        doAnswer(invocation -> {
            TextMessage outgoing = invocation.getArgument(0);
            JSONObject request = JSONObject.parseObject(outgoing.getPayload());
            requests.add(request);
            JSONObject response = new JSONObject();
            response.put("type", "UdpPingResponse");
            response.put("requestId", request.getString("requestId"));
            response.put("message", responseMessage);
            if (!omitData) {
                JSONObject data = new JSONObject();
                JSONObject options = request.getJSONObject("data");
                data.put("ip", options.getString("ip"));
                data.put("port", options.getInteger("port"));
                data.put("success", "reachable".equals(responseStatus));
                data.put("status", responseStatus);
                data.put("sentPackets", 2);
                data.put("receivedPackets", "reachable".equals(responseStatus) ? 2 : 0);
                data.put("averageTime", "reachable".equals(responseStatus) ? 1.25 : 0.0);
                data.put("packetLoss", "reachable".equals(responseStatus) ? 0.0 : 100.0);
                data.put("errorMessage", "reachable".equals(responseStatus) ? "" : "probe error");
                data.put("requestId", request.getString("requestId"));
                if (mutateData != null) {
                    mutateData.accept(data);
                }
                response.put("data", data);
            }
            server.handleTextMessage(session, new TextMessage(JSONObject.toJSONString(response, com.alibaba.fastjson.serializer.SerializerFeature.WriteMapNullValue)));
            return null;
        }).when(session).sendMessage(any());
        sessions().put(node.getId(), session);
        sessionIds.add(node.getId());
    }

    @SuppressWarnings("unchecked")
    private Map<Long, WebSocketSession> sessions() {
        return (ConcurrentHashMap<Long, WebSocketSession>) ReflectionTestUtils.getField(WebSocketServer.class, "nodeSessions");
    }

    @SuppressWarnings("unchecked")
    private List<ForwardServiceImpl.DiagnosisResult> results(R response) {
        assertEquals(0, response.getCode(), response.getMsg());
        return (List<ForwardServiceImpl.DiagnosisResult>) ((Map<?, ?>) response.getData()).get("results");
    }

    private void assertUnconfirmed(ForwardServiceImpl.DiagnosisResult result, String status) {
        assertFalse(result.isSuccess());
        assertEquals(status, result.getStatus());
        assertEquals("udp", result.getProtocol());
        assertNull(result.getAverageTime());
        assertNull(result.getPacketLoss());
    }

    private void assertError(R response) {
        assertEquals(-1, response.getCode());
    }

    private void bindUser(int userId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String claims = "{\"sub\":\"" + userId + "\",\"name\":\"test\",\"role_id\":1}";
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(claims.getBytes(StandardCharsets.UTF_8));
        request.addHeader("Authorization", "test." + payload + ".test");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private Node node(Long id) {
        Node node = new Node();
        node.setId(id);
        node.setName("node-" + id);
        node.setStatus(1);
        node.setServerIp("192.0.2.10");
        return node;
    }
}
