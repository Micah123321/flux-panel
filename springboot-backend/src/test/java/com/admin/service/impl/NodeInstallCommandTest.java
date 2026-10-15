package com.admin.service.impl;

import com.admin.common.lang.R;
import com.admin.controller.NodeController;
import com.admin.entity.Node;
import com.admin.entity.ViteConfig;
import com.admin.mapper.NodeMapper;
import com.admin.service.ViteConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NodeInstallCommandTest {
    private static final Long NODE_ID = 7L;
    private static final String SECRET = "MiXeD_09-node-key";
    private static final String SCRIPT_URL =
            "https://raw.githubusercontent.com/Micah123321/flux-panel/refs/heads/main/install.sh";
    private static final String DOWNLOAD_SUFFIX =
            " -o ./install.sh && chmod +x ./install.sh && ./install.sh";
    private static final String DIRECT_COMMAND = "curl -fL --retry 3 " + SCRIPT_URL + DOWNLOAD_SUFFIX;
    private static final String DOMESTIC_COMMAND =
            "curl -fL --retry 3 https://cghp2.535888.xyz/" + SCRIPT_URL + DOWNLOAD_SUFFIX + " -c";

    private NodeServiceImpl service;
    private NodeController controller;
    private NodeMapper mapper;
    private ViteConfigService configs;
    private ViteConfig address;

    @BeforeEach
    void setUp() {
        mapper = mock(NodeMapper.class);
        configs = mock(ViteConfigService.class);
        service = new NodeServiceImpl();
        controller = new NodeController();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        ReflectionTestUtils.setField(service, "viteConfigService", configs);
        ReflectionTestUtils.setField(controller, "nodeService", service);

        Node node = new Node();
        node.setId(NODE_ID);
        node.setSecret(SECRET);
        address = new ViteConfig();
        address.setName("ip");
        address.setValue("panel.example:6365");
        when(mapper.selectById(NODE_ID)).thenReturn(node);
        when(configs.getOne(any())).thenReturn(address);
    }

    @Test
    void legacyOverloadAndExplicitFalseKeepDirectDownloadAndArguments() {
        String expected = DIRECT_COMMAND + " -a panel.example:6365 -s " + SECRET;
        assertEquals(expected, command(service.getInstallCommand(NODE_ID)));
        assertEquals(expected, command(service.getInstallCommand(NODE_ID, false)));
    }

    @Test
    void domesticModeUsesMirrorAndPassesChinaFlagToInstaller() {
        address.setValue("192.0.2.10:6365");
        assertEquals(DOMESTIC_COMMAND + " -a 192.0.2.10:6365 -s " + SECRET,
                command(service.getInstallCommand(NODE_ID, true)));
    }

    @ParameterizedTest
    @CsvSource({
            "false, 2001:db8::10:6365, [2001:db8::10]:6365",
            "true, 2001:db8::10:6365, [2001:db8::10]:6365",
            "false, [2001:db8::10]:6365, [2001:db8::10]:6365",
            "true, [2001:db8::10]:6365, [2001:db8::10]:6365",
            "false, [2001:db8::10], [2001:db8::10]",
            "true, [2001:db8::10], [2001:db8::10]"
    })
    void ipv6AddressPortAndNodeSecretSurviveBothModes(boolean domestic, String input, String expected) {
        address.setValue(input);
        String prefix = domestic ? DOMESTIC_COMMAND : DIRECT_COMMAND;
        assertEquals(prefix + " -a " + expected + " -s " + SECRET,
                command(service.getInstallCommand(NODE_ID, domestic)));
        assertEquals(input, address.getValue());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingNodeReturnsErrorBeforeReadingConfiguration(boolean domestic) {
        when(mapper.selectById(NODE_ID)).thenReturn(null);
        R result = service.getInstallCommand(NODE_ID, domestic);
        assertEquals(-1, result.getCode());
        assertEquals("节点不存在", result.getMsg());
        assertNull(result.getData());
        verifyNoInteractions(configs);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingAddressConfigurationReturnsErrorInsteadOfCommand(boolean domestic) {
        when(configs.getOne(any())).thenReturn(null);
        R result = controller.getInstallCommand(Map.of("id", NODE_ID, "domestic", domestic));
        assertEquals(-1, result.getCode());
        assertEquals("请先前往网站配置中设置ip", result.getMsg());
        assertNull(result.getData());
    }

    @Test
    void controllerDefaultsMissingDomesticToDirectModeAndAcceptsIntegerId() {
        R result = controller.getInstallCommand(Map.of("id", NODE_ID.intValue()));
        assertEquals(DIRECT_COMMAND + " -a panel.example:6365 -s " + SECRET, command(result));
        verify(mapper).selectById(NODE_ID);
    }

    @ParameterizedTest
    @MethodSource("domesticValues")
    void controllerParsesDomesticAndStringIdThroughRealService(Object value, boolean expectedDomestic) {
        Map<String, Object> body = new HashMap<>();
        body.put("id", NODE_ID.toString());
        body.put("domestic", value);
        String prefix = expectedDomestic ? DOMESTIC_COMMAND : DIRECT_COMMAND;
        assertEquals(prefix + " -a panel.example:6365 -s " + SECRET,
                command(controller.getInstallCommand(body)));
        verify(mapper).selectById(NODE_ID);
    }

    private static Stream<Arguments> domesticValues() {
        return Stream.of(
                Arguments.of(true, true),
                Arguments.of(false, false),
                Arguments.of(null, false),
                Arguments.of("TrUe", true),
                Arguments.of("false", false),
                Arguments.of(" true ", false),
                Arguments.of(1, false),
                Arguments.of("yes", false));
    }

    private static String command(R result) {
        assertEquals(0, result.getCode(), result.getMsg());
        return assertInstanceOf(String.class, result.getData());
    }
}
