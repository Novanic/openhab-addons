/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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
import static org.openhab.binding.tesla.internal.TeslaBindingConstants.*;

import java.util.concurrent.ScheduledExecutorService;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandlerCallback;

/**
 * @author Sven Strohschein - Initial contribution
 */
public class TeslaWallConnectorHandlerTest {

    private static final String VERSION_URL = "http://127.0.0.1/api/1/version";
    private static final String VITALS_URL = "http://127.0.0.1/api/1/vitals";

    private TeslaWallConnectorHandler handler;
    private ThingHandlerCallback thingHandlerCallbackMock;
    private Thing thingMock;
    private HttpClient httpClientMock;

    @BeforeEach
    public void before() {
        Configuration configMock = mock(Configuration.class);
        when(configMock.get(CONFIG_CONNECTOR_HOST)).thenReturn("127.0.0.1");

        thingMock = mock(Thing.class);
        when(thingMock.getUID()).thenReturn(new ThingUID(THING_TYPE_WALL_CONNECTOR, "1"));
        when(thingMock.getConfiguration()).thenReturn(configMock);

        httpClientMock = mock(HttpClient.class);

        HttpClientFactory httpClientFactoryMock = mock(HttpClientFactory.class);
        when(httpClientFactoryMock.getCommonHttpClient()).thenReturn(httpClientMock);

        thingHandlerCallbackMock = mock(ThingHandlerCallback.class);

        handler = new TeslaWallConnectorHandlerAccessible(thingMock, httpClientFactoryMock);
        handler.setCallback(thingHandlerCallbackMock);
    }

    @Test
    public void testInitialize_Empty() throws Exception {
        ContentResponse responseMock = mock(ContentResponse.class);
        when(responseMock.getContentAsString()).thenReturn("");

        when(httpClientMock.GET(VERSION_URL)).thenReturn(responseMock);
        when(httpClientMock.GET(VITALS_URL)).thenReturn(responseMock);

        handler.initialize();

        verify(thingMock, never()).setProperty(anyString(), anyString());
        verify(thingHandlerCallbackMock, never()).stateUpdated(any(), any());
    }

    @Test
    public void testInitialize_NULL() throws Exception {
        ContentResponse responseMock = mock(ContentResponse.class);
        when(responseMock.getContentAsString()).thenReturn(null);

        when(httpClientMock.GET(VERSION_URL)).thenReturn(responseMock);
        when(httpClientMock.GET(VITALS_URL)).thenReturn(responseMock);

        handler.initialize();

        verify(thingMock, never()).setProperty(anyString(), anyString());
        verify(thingHandlerCallbackMock, never()).stateUpdated(any(), any());
    }

    @Test
    public void testInitialize() throws Exception {
        ContentResponse versionResponseMock = mock(ContentResponse.class);
        when(versionResponseMock.getContentAsString()).thenReturn(
                "{\"firmware_version\":\"22.41.2+gdb42x98x0xxxxx\",\"part_number\":\"1529400-02-D\",\"serial_number\":\"PGT21309042000\"}");

        ContentResponse vitalsResponseMock = mock(ContentResponse.class);
        when(vitalsResponseMock.getContentAsString()).thenReturn(
                "{\"contactor_closed\":false,\"vehicle_connected\":false,\"session_s\":0,\"grid_v\":224.1,\"grid_hz\":49.779,"
                        + "\"vehicle_current_a\":0.8,\"currentA_a\":0.4,\"currentB_a\":0.1,\"currentC_a\":0.6,\"currentN_a\":0.0,"
                        + "\"voltageA_v\":0.0,\"voltageB_v\":0.9,\"voltageC_v\":5.6,\"relay_coil_v\":11.7,"
                        + "\"pcba_temp_c\":15.9,\"handle_temp_c\":12.9,\"mcu_temp_c\":14.1,\"uptime_s\":743890,"
                        + "\"input_thermopile_uv\":-202,\"prox_v\":0.0,\"pilot_high_v\":11.6,\"pilot_low_v\":11.6,"
                        + "\"session_energy_wh\":12751.601,\"config_status\":5,\"evse_state\":1,\"current_alerts\":[]}");

        when(httpClientMock.GET(VERSION_URL)).thenReturn(versionResponseMock);
        when(httpClientMock.GET(VITALS_URL)).thenReturn(vitalsResponseMock);

        handler.initialize();

        verify(thingMock).setProperty("serial_number", "PGT21309042000");
        verify(thingMock).setProperty("firmware_version", "22.41.2+gdb42x98x0xxxxx");

        verify(thingHandlerCallbackMock).stateUpdated(createChannelUID(CHANNEL_CONNECTOR_CONTACTOR_VEHICLE_CONNECTED),
                OnOffType.OFF);
        verify(thingHandlerCallbackMock).stateUpdated(createChannelUID(CHANNEL_CONNECTOR_SESSION_DURATION),
                QuantityType.valueOf(0, Units.SECOND));
        verify(thingHandlerCallbackMock).stateUpdated(createChannelUID(CHANNEL_CONNECTOR_SESSION_ENERGY),
                QuantityType.valueOf(12.752, Units.KILOWATT_HOUR));
    }

    private ChannelUID createChannelUID(String channelId) {
        return new ChannelUID(thingMock.getUID(), channelId);
    }

    private static class TeslaWallConnectorHandlerAccessible extends TeslaWallConnectorHandler {

        private final ScheduledExecutorService schedulerMock;

        public TeslaWallConnectorHandlerAccessible(Thing thing, HttpClientFactory httpClientFactory) {
            super(thing, httpClientFactory);
            schedulerMock = mock(ScheduledExecutorService.class);
            doAnswer((Answer<Void>) invocationOnMock -> {
                ((Runnable) invocationOnMock.getArgument(0)).run();
                return null;
            }).when(schedulerMock).scheduleWithFixedDelay(any(), anyLong(), anyLong(), any());
        }

        @Override
        @NonNull
        protected ScheduledExecutorService getScheduler() {
            return schedulerMock;
        }
    }
}
