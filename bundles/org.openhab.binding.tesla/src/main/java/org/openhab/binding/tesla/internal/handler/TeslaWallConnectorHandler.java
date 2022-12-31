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

import static org.openhab.binding.tesla.internal.TeslaBindingConstants.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.openhab.binding.tesla.internal.TeslaBindingConstants;
import org.openhab.binding.tesla.internal.protocol.wallconnector.VersionDTO;
import org.openhab.binding.tesla.internal.protocol.wallconnector.VitalsDTO;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * The {@link TeslaWallConnectorHandler} is responsible for the communication with the wall connector device.
 *
 * @author Sven Strohschein - Initial contribution
 */
@NonNullByDefault
public class TeslaWallConnectorHandler extends BaseThingHandler {

    private static final String PROTOCOL = "http://";
    private static final String VERSION_URL = "/api/1/version";
    private static final String VITALS_URL = "/api/1/vitals";

    private final Logger logger = LoggerFactory.getLogger(TeslaWallConnectorHandler.class);

    private final HttpClient httpClient;
    private final Gson gson;

    public TeslaWallConnectorHandler(Thing thing, HttpClientFactory httpClientFactory) {
        super(thing);
        this.httpClient = httpClientFactory.getCommonHttpClient();
        this.gson = new Gson();
    }

    @Override
    public void initialize() {
        final String wallConnectorIPAddress = (String) getConfig().get(TeslaBindingConstants.CONFIG_CONNECTOR_HOST);
        getScheduler().scheduleWithFixedDelay(() -> initializeProperties(wallConnectorIPAddress), 0, 1, TimeUnit.HOURS);
        getScheduler().scheduleWithFixedDelay(() -> refreshChannels(wallConnectorIPAddress), 0, 5, TimeUnit.MINUTES);
    }

    private void initializeProperties(String wallConnectorIPAddress) {
        @Nullable
        VersionDTO version = executeGETRequest(wallConnectorIPAddress, VERSION_URL, VersionDTO.class);
        initializeProperties(version);
    }

    private void initializeProperties(@Nullable VersionDTO version) {
        if (version != null) {
            getThing().setProperty("serial_number", version.getSerialNumber());
            getThing().setProperty("firmware_version", version.getFirmwareVersion());
        } else {
            updateStatusOfflineNotReachable();
        }
    }

    private void refreshChannels(String wallConnectorIPAddress) {
        @Nullable
        VitalsDTO vitals = executeGETRequest(wallConnectorIPAddress, VITALS_URL, VitalsDTO.class);
        refreshChannels(vitals);
    }

    private void refreshChannels(@Nullable VitalsDTO vitals) {
        if (vitals != null) {
            updateState(CHANNEL_CONNECTOR_CONTACTOR_VEHICLE_CONNECTED, OnOffType.from(vitals.isVehicleConnected()));
            updateState(CHANNEL_CONNECTOR_SESSION_DURATION,
                    QuantityType.valueOf((double) vitals.getSessionDurationInSeconds(), Units.SECOND));

            BigDecimal sessionEnergyKWh = BigDecimal.valueOf(vitals.getSessionEnergyWh());
            sessionEnergyKWh = sessionEnergyKWh.setScale(3, RoundingMode.HALF_UP);
            sessionEnergyKWh = sessionEnergyKWh.divide(BigDecimal.valueOf(1000), RoundingMode.HALF_UP);

            updateState(CHANNEL_CONNECTOR_SESSION_ENERGY,
                    QuantityType.valueOf(sessionEnergyKWh.doubleValue(), Units.KILOWATT_HOUR));

            updateStatus(ThingStatus.ONLINE);
        } else {
            updateStatusOfflineNotReachable();
        }
    }

    private void updateStatusOfflineNotReachable() {
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "@text/error.notReachable");
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    protected ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    private <T> @Nullable T executeGETRequest(String wallConnectorIPAddress, String urlPath, Class<T> targetType) {
        try {
            ContentResponse response = httpClient.GET(PROTOCOL + wallConnectorIPAddress + urlPath);
            String content = response.getContentAsString();
            @Nullable
            T object = gson.fromJson(content, targetType);
            return object;
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            logger.debug("Error on initializing properties!", e);
            updateStatusOfflineNotReachable();
            return null;
        }
    }
}
