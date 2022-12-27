/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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
package org.openhab.binding.tesla.internal.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.openhab.core.common.ThreadPoolManager;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.thing.Thing;

import java.util.concurrent.ScheduledExecutorService;

/**
 * @author Sven Strohschein - Initial contribution
 */
public class TeslaWallConnectorHandlerTest {

    private TeslaWallConnectorHandler handler;
    private Thing thingMock;
    private HttpClientFactory httpClientFactoryMock;
    private HttpClient httpClientMock;

    @BeforeEach
    public void before() {
        Configuration configMock = mock(Configuration.class);

        thingMock = mock(Thing.class);
        when(thingMock.getConfiguration()).thenReturn(configMock);

        httpClientMock = mock(HttpClient.class);

        httpClientFactoryMock = mock(HttpClientFactory.class);
        when(httpClientFactoryMock.getCommonHttpClient()).thenReturn(httpClientMock);

        handler = new TeslaWallConnectorHandlerAccessible(thingMock, httpClientFactoryMock);
    }

    @Test
    public void testInitialize_Empty() throws Exception {
        ContentResponse responseMock = mock(ContentResponse.class);
        when(responseMock.getContentAsString()).thenReturn("");

        when(httpClientMock.GET(anyString())).thenReturn(responseMock);

        handler.initialize();

        verify(thingMock, never()).setProperty(anyString(), anyString());
    }

    @Test
    public void testInitialize_NULL() throws Exception {
        ContentResponse responseMock = mock(ContentResponse.class);
        when(responseMock.getContentAsString()).thenReturn(null);

        when(httpClientMock.GET(anyString())).thenReturn(responseMock);

        handler.initialize();

        verify(thingMock, never()).setProperty(anyString(), anyString());
    }

    @Test
    public void testInitialize() throws Exception {
        ContentResponse responseMock = mock(ContentResponse.class);
        when(responseMock.getContentAsString()).thenReturn("{\"firmware_version\":\"22.41.2+gdb42x98x0xxxxx\",\"part_number\":\"1529400-02-D\",\"serial_number\":\"PGT21309042000\"}");

        when(httpClientMock.GET(anyString())).thenReturn(responseMock);

        handler.initialize();

        verify(thingMock).setProperty("serial_number", "PGT21309042000");
        verify(thingMock).setProperty("firmware_version", "22.41.2+gdb42x98x0xxxxx");
    }

    private static class TeslaWallConnectorHandlerAccessible extends TeslaWallConnectorHandler {

        private final ScheduledExecutorService schedulerMock;

        public TeslaWallConnectorHandlerAccessible(Thing thing, HttpClientFactory httpClientFactory) {
            super(thing, httpClientFactory);
            schedulerMock = mock(ScheduledExecutorService.class);
            doAnswer((Answer<Void>) invocationOnMock -> {
                ((Runnable)invocationOnMock.getArgument(0)).run();
                return null;
            }).when(schedulerMock).execute(any());
        }

        @Override
        protected ScheduledExecutorService getScheduler() {
            return schedulerMock;
        }
    }
}
