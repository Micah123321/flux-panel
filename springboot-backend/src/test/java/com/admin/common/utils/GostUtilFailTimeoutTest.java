package com.admin.common.utils;

import com.admin.common.dto.GostDto;
import com.admin.entity.Tunnel;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GostUtilFailTimeoutTest {

    private static final long NODE_ID = -600L;
    private WebSocketSession session;
    private Map<Long, WebSocketSession> nodeSessions;
    private JSONObject request;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("gost-fail-timeout-test");
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(Map.of("id", Long.toString(NODE_ID), "type", "1"));
        WebSocketServer server = new WebSocketServer();
        doAnswer(invocation -> {
            TextMessage message = invocation.getArgument(0);
            request = JSONObject.parseObject(message.getPayload());
            JSONObject response = new JSONObject();
            response.put("requestId", request.getString("requestId"));
            response.put("message", "OK");
            server.handleTextMessage(session, new TextMessage(response.toJSONString()));
            return null;
        }).when(session).sendMessage(any(TextMessage.class));
        nodeSessions = (Map<Long, WebSocketSession>) ReflectionTestUtils.getField(WebSocketServer.class, "nodeSessions");
        assertNotNull(nodeSessions);
        assertNull(nodeSessions.putIfAbsent(NODE_ID, session));
    }

    @AfterEach
    void tearDown() {
        if (nodeSessions != null) {
            nodeSessions.remove(NODE_ID, session);
        }
        Map<?, ?> locks = (Map<?, ?>) ReflectionTestUtils.getField(WebSocketServer.class, "sessionLocks");
        assertNotNull(locks);
        locks.remove("gost-fail-timeout-test");
    }

    @Test
    void addChainSendsNumericNanoseconds() {
        JSONObject chain = send("AddChains", () -> GostUtil.AddChains(
                NODE_ID, "test", "192.0.2.1:9000", "tls", null, "round"))
                .getJSONObject("data");

        assertSelector(chain.getJSONArray("hops").getJSONObject(0).getJSONObject("selector"), "round");
    }

    @Test
    void updateChainSendsNumericNanosecondsInsideUpdateEnvelope() {
        JSONObject update = send("UpdateChains", () -> GostUtil.UpdateChains(
                NODE_ID, "test", "192.0.2.1:9000", "tls", null)).getJSONObject("data");

        assertEquals("test_chains", update.getString("chain"));
        assertSelector(update.getJSONObject("data").getJSONArray("hops")
                .getJSONObject(0).getJSONObject("selector"), "fifo");
    }

    @Test
    void addRemoteServiceSendsNumericNanoseconds() {
        JSONArray services = send("AddService", () -> GostUtil.AddRemoteService(
                NODE_ID, "test", 9000, "192.0.2.2:8080", "tls", null, null)).getJSONArray("data");

        assertForwarders(services, 1, "fifo");
    }

    @Test
    void updateRemoteServiceSendsNumericNanoseconds() {
        JSONArray services = send("UpdateService", () -> GostUtil.UpdateRemoteService(
                NODE_ID, "test", 9000, "192.0.2.2:8080", "tls", "round", null)).getJSONArray("data");

        assertForwarders(services, 1, "round");
    }

    @Test
    void addPortForwardingSendsNumericNanosecondsForTcpAndUdp() {
        JSONArray services = send("AddService", () -> GostUtil.AddService(
                NODE_ID, "test", 9000, null, "192.0.2.2:8080", 1, tunnel(), null, null))
                .getJSONArray("data");

        assertForwarders(services, 2, "fifo");
    }

    @Test
    void updatePortForwardingSendsNumericNanosecondsForTcpAndUdp() {
        JSONArray services = send("UpdateService", () -> GostUtil.UpdateService(
                NODE_ID, "test", 9000, null, "192.0.2.2:8080", 1, tunnel(), "round", null))
                .getJSONArray("data");

        assertForwarders(services, 2, "round");
    }

    private JSONObject send(String type, Supplier<GostDto> command) {
        assertEquals("OK", command.get().getMsg());
        assertNotNull(request);
        assertEquals(type, request.getString("type"));
        return request;
    }

    private void assertForwarders(JSONArray services, int count, String strategy) {
        assertEquals(count, services.size());
        for (int i = 0; i < services.size(); i++) {
            assertSelector(services.getJSONObject(i).getJSONObject("forwarder").getJSONObject("selector"), strategy);
        }
    }

    private void assertSelector(JSONObject selector, String strategy) {
        Object timeout = selector.get("failTimeout");
        assertTrue(timeout instanceof Long, "failTimeout must be a JSON integer, not a duration string");
        assertEquals(600_000_000_000L, ((Long) timeout).longValue());
        assertEquals(strategy, selector.getString("strategy"));
    }

    private Tunnel tunnel() {
        Tunnel tunnel = new Tunnel();
        tunnel.setTcpListenAddr("0.0.0.0");
        tunnel.setUdpListenAddr("0.0.0.0");
        return tunnel;
    }
}
