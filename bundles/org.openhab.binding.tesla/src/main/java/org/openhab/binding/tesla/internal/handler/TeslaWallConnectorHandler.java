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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.openhab.binding.tesla.internal.TeslaBindingConstants;
import org.openhab.binding.tesla.internal.protocol.wallconnector.VersionDTO;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;

import com.google.gson.Gson;

/**
 * The {@link TeslaWallConnectorHandler} is responsible for the communication with the wall connector device.
 *
 * @author Sven Strohschein - Initial contribution
 */
@NonNullByDefault
public class TeslaWallConnectorHandler extends BaseThingHandler {

    private static final String VERSION_URL = "/api/1/version";

    private final HttpClient httpClient;
    private final Gson gson;

    public TeslaWallConnectorHandler(Thing thing, HttpClientFactory httpClientFactory) {
        super(thing);
        this.httpClient = httpClientFactory.getCommonHttpClient();
        this.gson = new Gson();
    }

    @Override
    public void initialize() {
        final String wallConnectorIPAddress = (String) getConfig().get(TeslaBindingConstants.CONFIG_IP_ADDRESS);
        getScheduler().execute(() -> initializeProperties(wallConnectorIPAddress));
    }

    private void initializeProperties(String wallConnectorIPAddress) {
        try {
            ContentResponse response = httpClient.GET(wallConnectorIPAddress + VERSION_URL);
            String content = response.getContentAsString();
            @Nullable
            VersionDTO versionObject = gson.fromJson(content, VersionDTO.class);
            initializeProperties(versionObject);
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private void initializeProperties(@Nullable VersionDTO versionObject) {
        if (versionObject != null) {
            getThing().setProperty("serial_number", versionObject.getSerialNumber());
            getThing().setProperty("firmware_version", versionObject.getFirmwareVersion());
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    protected ScheduledExecutorService getScheduler() {
        return scheduler;
    }
}
