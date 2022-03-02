/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.livisismarthome.internal.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.ConnectException;
import java.util.Collections;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.binding.livisismarthome.internal.LivisiBindingConstants;
import org.openhab.binding.livisismarthome.internal.LivisiWebSocket;
import org.openhab.binding.livisismarthome.internal.client.LivisiClient;
import org.openhab.binding.livisismarthome.internal.client.api.entity.device.DeviceConfigDTO;
import org.openhab.binding.livisismarthome.internal.client.api.entity.device.DeviceDTO;
import org.openhab.binding.livisismarthome.internal.manager.FullDeviceManager;
import org.openhab.core.auth.client.oauth2.OAuthClientService;
import org.openhab.core.auth.client.oauth2.OAuthFactory;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ThingUID;

/**
 * @author Sven Strohschein - Initial contribution
 */
public class LivisiBridgeHandlerTest {

    private static final int MAXIMUM_RETRY_EXECUTIONS = 10;

    private LivisiBridgeHandlerAccessible bridgeHandler;
    private Bridge bridgeMock;
    private LivisiWebSocket webSocketMock;

    @BeforeEach
    public void before() throws Exception {
        bridgeMock = mock(Bridge.class);
        when(bridgeMock.getUID()).thenReturn(new ThingUID("livisismarthome", "bridge"));

        webSocketMock = mock(LivisiWebSocket.class);

        OAuthClientService oAuthService = mock(OAuthClientService.class);

        OAuthFactory oAuthFactoryMock = mock(OAuthFactory.class);
        when(oAuthFactoryMock.createOAuthClientService(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(oAuthService);

        HttpClient httpClientMock = mock(HttpClient.class);

        bridgeHandler = new LivisiBridgeHandlerAccessible(bridgeMock, oAuthFactoryMock, httpClientMock);
    }

    @Test
    public void testInitialize() throws Exception {
        Configuration bridgeConfig = new Configuration();

        when(bridgeMock.getConfiguration()).thenReturn(bridgeConfig);

        bridgeHandler.initialize();

        verify(webSocketMock).start();
        assertEquals(1, bridgeHandler.getDirectExecutionCount());
    }

    @Test
    public void testInitializeErrorOnStartingWebSocket() throws Exception {
        Configuration bridgeConfig = new Configuration();

        when(bridgeMock.getConfiguration()).thenReturn(bridgeConfig);

        doThrow(new RuntimeException("Test-Exception")).when(webSocketMock).start();

        bridgeHandler.initialize();

        verify(webSocketMock, times(MAXIMUM_RETRY_EXECUTIONS)).start();
        assertEquals(1, bridgeHandler.getDirectExecutionCount()); // only the first execution should be without a delay
    }

    @Test
    public void testConnectionClosed() throws Exception {
        Configuration bridgeConfig = new Configuration();

        when(bridgeMock.getConfiguration()).thenReturn(bridgeConfig);

        bridgeHandler.initialize();

        verify(webSocketMock).start();
        assertEquals(1, bridgeHandler.getDirectExecutionCount());

        bridgeHandler.connectionClosed();

        verify(webSocketMock, times(2)).start(); // automatically restarted (with a delay)
        assertEquals(1, bridgeHandler.getDirectExecutionCount());

        bridgeHandler.connectionClosed();

        verify(webSocketMock, times(3)).start(); // automatically restarted (with a delay)
        assertEquals(1, bridgeHandler.getDirectExecutionCount());
    }

    @Test
    public void testConnectionClosedReconnectNotPossible() throws Exception {
        Configuration bridgeConfig = new Configuration();

        when(bridgeMock.getConfiguration()).thenReturn(bridgeConfig);

        bridgeHandler.initialize();

        verify(webSocketMock).start();
        assertEquals(1, bridgeHandler.getDirectExecutionCount());

        doThrow(new ConnectException("Connection refused")).when(webSocketMock).start();

        bridgeHandler.connectionClosed();

        verify(webSocketMock, times(10)).start(); // automatic reconnect attempts (with a delay)
        assertEquals(1, bridgeHandler.getDirectExecutionCount());
    }

    @Test
    public void testOnEventDisconnect() throws Exception {
        final String disconnectEventJSON = "{ type: \"Disconnect\" }";

        Configuration bridgeConfig = new Configuration();

        when(bridgeMock.getConfiguration()).thenReturn(bridgeConfig);

        bridgeHandler.initialize();

        verify(webSocketMock).start();
        assertEquals(1, bridgeHandler.getDirectExecutionCount());

        bridgeHandler.onEvent(disconnectEventJSON);

        verify(webSocketMock, times(2)).start(); // automatically restarted (with a delay)
        assertEquals(1, bridgeHandler.getDirectExecutionCount());

        bridgeHandler.onEvent(disconnectEventJSON);

        verify(webSocketMock, times(3)).start(); // automatically restarted (with a delay)
        assertEquals(1, bridgeHandler.getDirectExecutionCount());
    }

    @NonNullByDefault
    private class LivisiBridgeHandlerAccessible extends LivisiBridgeHandler {

        private final LivisiClient livisiClientMock;
        private final FullDeviceManager fullDeviceManagerMock;
        private final ScheduledExecutorService schedulerMock;
        private int executionCount;
        private int directExecutionCount;

        private LivisiBridgeHandlerAccessible(Bridge bridge, OAuthFactory oAuthFactory, HttpClient httpClient)
                throws Exception {
            super(bridge, oAuthFactory, httpClient);

            DeviceDTO bridgeDevice = new DeviceDTO();
            bridgeDevice.setId("bridgeId");
            bridgeDevice.setType(LivisiBindingConstants.DEVICE_SHC);
            bridgeDevice.setConfig(new DeviceConfigDTO());

            livisiClientMock = mock(LivisiClient.class);
            fullDeviceManagerMock = mock(FullDeviceManager.class);
            when(fullDeviceManagerMock.getFullDevices()).thenReturn(Collections.singletonList(bridgeDevice));

            schedulerMock = mock(ScheduledExecutorService.class);

            doAnswer(invocationOnMock -> {
                if (executionCount <= MAXIMUM_RETRY_EXECUTIONS) {
                    executionCount++;
                    invocationOnMock.getArgument(0, Runnable.class).run();
                }
                return null;
            }).when(schedulerMock).execute(any());

            doAnswer(invocationOnMock -> {
                if (executionCount <= MAXIMUM_RETRY_EXECUTIONS) {
                    executionCount++;
                    long seconds = invocationOnMock.getArgument(1);
                    if (seconds <= 0) {
                        directExecutionCount++;
                    }

                    invocationOnMock.getArgument(0, Runnable.class).run();
                }
                return mock(ScheduledFuture.class);
            }).when(schedulerMock).schedule(any(Runnable.class), anyLong(), any());
        }

        public int getDirectExecutionCount() {
            return directExecutionCount;
        }

        @Override
        FullDeviceManager createFullDeviceManager(LivisiClient client) {
            return fullDeviceManagerMock;
        }

        @Override
        LivisiClient createClient(OAuthClientService oAuthService, HttpClient httpClient) {
            return livisiClientMock;
        }

        @Override
        LivisiWebSocket createWebSocket(DeviceDTO bridgeDevice) {
            return webSocketMock;
        }

        @Override
        ScheduledExecutorService getScheduler() {
            return schedulerMock;
        }
    }
}