package com.admin.service.impl;

import com.admin.common.lang.R;
import com.admin.common.utils.WebSocketServer;
import com.admin.controller.ForwardController;
import com.admin.entity.*;
import com.admin.mapper.ForwardMapper;
import com.admin.service.*;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.*;
import org.springframework.web.socket.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ForwardResyncTest {
    private ForwardServiceImpl service;
    private ForwardController controller;
    private ForwardMapper mapper;
    private TunnelService tunnels;
    private NodeService nodes;
    private UserService users;
    private UserTunnelService grants;
    private final List<JSONObject> requests = new ArrayList<>();
    private Forward forward;
    private Tunnel tunnel;
    private final List<String> commands = new ArrayList<>();
    private String failure;
    private final WebSocketServer server = new WebSocketServer();

    @BeforeEach
    void setUp() throws Exception {
        service = new ForwardServiceImpl();
        controller = new ForwardController();
        mapper = mock(ForwardMapper.class);
        tunnels = mock(TunnelService.class);
        nodes = mock(NodeService.class);
        users = mock(UserService.class);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        ReflectionTestUtils.setField(service, "tunnelService", tunnels);
        ReflectionTestUtils.setField(service, "nodeService", nodes);
        ReflectionTestUtils.setField(service, "userService", users);
        grants = mock(UserTunnelService.class);
        ReflectionTestUtils.setField(service, "userTunnelService", grants);
        ReflectionTestUtils.setField(controller, "forwardService", service);
        forward = new Forward();
        forward.setId(23L); forward.setUserId(1); forward.setName("persisted-name");
        forward.setTunnelId(9); forward.setStatus(-1); forward.setInPort(50000);
        forward.setOutPort(21701); forward.setRemoteAddr("192.0.2.1:9000");
        forward.setInterfaceName("eth1"); forward.setStrategy("round");
        tunnel = new Tunnel();
        tunnel.setId(9L); tunnel.setType(2); tunnel.setStatus(1);
        tunnel.setInNodeId(94013L); tunnel.setOutNodeId(94014L); tunnel.setProtocol("tls");
        tunnel.setTcpListenAddr("0.0.0.0"); tunnel.setUdpListenAddr("0.0.0.0");
        when(mapper.selectById(23L)).thenReturn(forward);
        when(mapper.updateById(any(Forward.class))).thenReturn(1);
        when(tunnels.getById(9)).thenReturn(tunnel);
        bindUser(1,0);
        for(long id : List.of(94013L,94014L)) {
            Node node = new Node(); node.setId(id); node.setStatus(1); node.setServerIp("192.0.2.10");
            when(nodes.getNodeById(id)).thenReturn(node);
            WebSocketSession session = mock(WebSocketSession.class);
            when(session.getId()).thenReturn("resync-test-"+id);
            when(session.isOpen()).thenReturn(true);
            when(session.getAttributes()).thenReturn(new HashMap<>(Map.of("id",Long.toString(id),"type","0")));
            doAnswer(invocation -> {
                JSONObject request = JSONObject.parseObject(((TextMessage)invocation.getArgument(0)).getPayload());
                requests.add(request);
                String type = request.getString("type") + "@" + id; commands.add(type);
                JSONObject response = new JSONObject(); response.put("requestId",request.getString("requestId"));
                response.put("message",type.equals(failure)?"fixture update failed":"OK");
                server.handleTextMessage(session,new TextMessage(response.toJSONString()));
                return null;
            }).when(session).sendMessage(any());
            sessions().put(id,session);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<Long,WebSocketSession> sessions() {
        return (Map<Long,WebSocketSession>) ReflectionTestUtils.getField(WebSocketServer.class,"nodeSessions");
    }

    private void bindUser(int id,int role) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String claims = "{\"sub\":\""+id+"\",\"role_id\":"+role+",\"name\":\"fixture\"}";
        request.addHeader("Authorization","fixture."+Base64.getUrlEncoder().withoutPadding().encodeToString(claims.getBytes(StandardCharsets.UTF_8))+".fixture");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    @SuppressWarnings("unchecked")
    void tearDown() {
        sessions().remove(94013L); sessions().remove(94014L);
        Map<String,Object> locks=(Map<String,Object>) ReflectionTestUtils.getField(WebSocketServer.class,"sessionLocks");
        locks.remove("resync-test-94013"); locks.remove("resync-test-94014");
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void fullSuccessfulDispatchRestoresStatusAndPreservesSavedConfiguration() {
        assertEquals(0,controller.resync(Map.of("id",23)).getCode());
        assertEquals(List.of("UpdateChains@94013","UpdateService@94014","UpdateService@94013"),commands);
        var capture=org.mockito.ArgumentCaptor.forClass(Forward.class);
        verify(mapper).updateById(capture.capture());
        Forward saved=capture.getValue();
        assertEquals(1,saved.getStatus()); assertEquals(50000,saved.getInPort());
        assertEquals(21701,saved.getOutPort()); assertEquals("persisted-name",saved.getName());
        assertEquals("192.0.2.1:9000",saved.getRemoteAddr()); assertEquals("eth1",saved.getInterfaceName());
    }

    @Test
    void failureAtAnyDispatchStageNeverMarksTheRecordEnabled() {
        for(String stage : List.of("UpdateChains@94013","UpdateService@94014","UpdateService@94013")) {
            clearInvocations(mapper); commands.clear(); failure=stage; forward.setStatus(-1);
            assertNotEquals(0,service.resyncForward(23L).getCode());
            var capture=org.mockito.ArgumentCaptor.forClass(Forward.class);
            verify(mapper).updateById(capture.capture());
            assertEquals(-1,capture.getValue().getStatus());
            assertEquals(stage,commands.get(commands.size()-1));
        }
    }

    @Test
    void activePausedAndUnknownStatesRequireRefreshingInsteadOfEnabling() {
        for(Integer status : Arrays.asList(0,1,null)) {
            forward.setStatus(status);
            assertNotEquals(0,service.resyncForward(23L).getCode());
        }
        assertTrue(commands.isEmpty()); verify(mapper,never()).updateById(any(Forward.class));
    }

    @Test
    void otherUsersCannotResyncTheRecord() {
        bindUser(99,1);
        assertNotEquals(0,service.resyncForward(23L).getCode());
        assertTrue(commands.isEmpty()); verifyNoInteractions(nodes);
    }

    @Test
    void disabledOwnerAndDisabledTunnelAreRejectedBeforeDispatch() {
        bindUser(1,1); User owner=new User();owner.setId(1L);owner.setStatus(0);
        when(users.getById(1)).thenReturn(owner);
        assertNotEquals(0,service.resyncForward(23L).getCode());
        bindUser(1,0); tunnel.setStatus(0);
        assertNotEquals(0,service.resyncForward(23L).getCode());
        assertTrue(commands.isEmpty());
    }

    private UserTunnel validGrant() {
        User owner = new User(); owner.setId(1L); owner.setStatus(1);
        owner.setFlow(100L); owner.setInFlow(0L); owner.setOutFlow(0L);
        when(users.getById(1)).thenReturn(owner);
        UserTunnel grant = new UserTunnel(); grant.setId(7); grant.setUserId(1);
        grant.setTunnelId(9); grant.setStatus(1); grant.setSpeedId(42);
        grant.setFlow(100L); grant.setInFlow(0L); grant.setOutFlow(0L);
        when(grants.getOne(any(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class))).thenReturn(grant);
        return grant;
    }

    @Test
    void retryPreservesSavedLimiterForNormalUserAndAdmin() {
        validGrant();
        for(int role : List.of(1,0)) {
            bindUser(1,role); forward.setStatus(-1); requests.clear();
            assertEquals(0,service.resyncForward(23L).getCode());
            JSONObject main = requests.get(requests.size()-1);
            var services = main.getJSONArray("data");
            assertEquals(2,services.size());
            for(int i=0;i<services.size();i++) assertEquals("42",services.getJSONObject(i).getString("limiter"));
            assertEquals("23_1_7_chains",requests.get(0).getJSONObject("data").getString("chain"));
        }
    }

    @Test
    void disabledOrMissingTunnelGrantBlocksDispatch() {
        UserTunnel grant=validGrant(); bindUser(1,1); grant.setStatus(0);
        assertNotEquals(0,service.resyncForward(23L).getCode());
        when(grants.getOne(any(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class))).thenReturn(null);
        assertNotEquals(0,service.resyncForward(23L).getCode());
        assertTrue(commands.isEmpty()); verify(mapper,never()).updateById(any(Forward.class));
    }

    @Test
    void expiredGrantOrAccountBlocksDispatch() {
        UserTunnel grant=validGrant(); bindUser(1,1); grant.setExpTime(System.currentTimeMillis()-1000);
        assertNotEquals(0,service.resyncForward(23L).getCode());
        grant.setExpTime(null); users.getById(1).setExpTime(System.currentTimeMillis()-1000);
        assertNotEquals(0,service.resyncForward(23L).getCode());
        assertTrue(commands.isEmpty()); verify(mapper,never()).updateById(any(Forward.class));
    }

    @Test
    void exhaustedAccountOrTunnelTrafficBlocksDispatch() {
        UserTunnel grant=validGrant(); bindUser(1,1); users.getById(1).setFlow(0L);
        assertNotEquals(0,service.resyncForward(23L).getCode());
        users.getById(1).setFlow(100L); grant.setFlow(0L);
        assertNotEquals(0,service.resyncForward(23L).getCode());
        assertTrue(commands.isEmpty()); verify(mapper,never()).updateById(any(Forward.class));
    }

    @Test
    void adminRetryForAnotherOwnerStillChecksTheirGrant() {
        UserTunnel grant=validGrant(); bindUser(99,0); grant.setStatus(0);
        assertNotEquals(0,service.resyncForward(23L).getCode());
        assertTrue(commands.isEmpty()); verify(mapper,never()).updateById(any(Forward.class));
    }

    @Test
    void invalidControllerIdsReturnErrorsWithoutNodeRequests() {
        for(Map<String,Object> params : List.of(Map.<String,Object>of(),Map.<String,Object>of("id","bad"))) assertNotEquals(0,controller.resync(params).getCode());
        assertNotEquals(0,controller.resync(null).getCode()); assertTrue(commands.isEmpty());
    }
}
