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
package org.openhab.binding.livisismarthome.internal.discovery;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.livisismarthome.internal.LivisiBindingConstants;
import org.openhab.binding.livisismarthome.internal.client.api.entity.device.DeviceDTO;
import org.openhab.binding.livisismarthome.internal.handler.LivisiBridgeHandler;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.config.discovery.DiscoveryService;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The {@link LivisiDeviceDiscoveryService} is responsible for discovering new devices.
 *
 * @author Oliver Kuhl - Initial contribution
 * @author Sven Strohschein - Renamed from Innogy to Livisi
 */
@NonNullByDefault
public class LivisiDeviceDiscoveryService extends AbstractDiscoveryService
        implements DiscoveryService, ThingHandlerService {

    private static final int SEARCH_TIME_SECONDS = 60;

    private final Logger logger = LoggerFactory.getLogger(LivisiDeviceDiscoveryService.class);

    private @Nullable LivisiBridgeHandler bridgeHandler;

    /**
     * Construct an {@link LivisiDeviceDiscoveryService}.
     */
    public LivisiDeviceDiscoveryService() {
        super(SEARCH_TIME_SECONDS);
    }

    /**
     * Deactivates the {@link LivisiDeviceDiscoveryService} by unregistering it as
     * {@link org.openhab.binding.livisismarthome.internal.listener.DeviceStatusListener} on the
     * {@link LivisiBridgeHandler}. Older discovery results will be removed.
     *
     * @see org.openhab.core.config.discovery.AbstractDiscoveryService#deactivate()
     */
    @Override
    public void deactivate() {
        removeOlderResults(new Date().getTime());
    }

    @Override
    public Set<ThingTypeUID> getSupportedThingTypes() {
        return LivisiBindingConstants.SUPPORTED_DEVICE_THING_TYPES;
    }

    @Override
    protected void startScan() {
        logger.debug("SCAN for new LIVISI SmartHome devices started...");
        if (bridgeHandler != null) {
            for (final DeviceDTO d : bridgeHandler.loadDevices()) {
                onDeviceAdded(d);
            }
        }
    }

    @Override
    protected synchronized void stopScan() {
        super.stopScan();
        removeOlderResults(getTimestampOfLastScan());
    }

    public void onDeviceAdded(DeviceDTO device) {
        if (bridgeHandler != null) {
            final ThingUID bridgeUID = bridgeHandler.getThing().getUID();
            final Optional<ThingUID> thingUID = getThingUID(bridgeUID, device);
            final Optional<ThingTypeUID> thingTypeUID = getThingTypeUID(device);

            if (thingUID.isPresent() && thingTypeUID.isPresent()) {
                String name = device.getConfig().getName();
                if (name.isEmpty()) {
                    name = device.getSerialnumber();
                }

                final Map<String, Object> properties = new HashMap<>();
                properties.put(LivisiBindingConstants.PROPERTY_ID, device.getId());

                final String label;
                if (device.hasLocation()) {
                    label = device.getType() + ": " + name + " (" + device.getLocation().getName() + ")";
                } else {
                    label = device.getType() + ": " + name;
                }

                final DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID.get())
                        .withThingType(thingTypeUID.get()).withProperties(properties).withBridge(bridgeUID)
                        .withLabel(label).build();

                thingDiscovered(discoveryResult);
            } else {
                logger.debug("Discovered unsupported device of type '{}' and name '{}' with id {}", device.getType(),
                        device.getConfig().getName(), device.getId());
            }
        }
    }

    /**
     * Returns the {@link ThingUID} for the given {@link DeviceDTO} or empty, if the device type is not available.
     *
     * @param bridgeUID bridge
     * @param device device
     * @return {@link ThingUID} for the given {@link DeviceDTO} or empty
     */
    private Optional<ThingUID> getThingUID(ThingUID bridgeUID, DeviceDTO device) {
        final Optional<ThingTypeUID> thingTypeUID = getThingTypeUID(device);

        if (thingTypeUID.isPresent() && getSupportedThingTypes().contains(thingTypeUID.get())) {
            return Optional.of(new ThingUID(thingTypeUID.get(), bridgeUID, device.getId()));
        }
        return Optional.empty();
    }

    /**
     * Returns a {@link ThingTypeUID} for the given {@link DeviceDTO} or empty, if the device type is not available.
     *
     * @param device device
     * @return {@link ThingTypeUID} for the given {@link DeviceDTO} or empty
     */
    private Optional<ThingTypeUID> getThingTypeUID(DeviceDTO device) {
        final String thingTypeId = device.getType();
        if (thingTypeId != null) {
            return Optional.of(new ThingTypeUID(LivisiBindingConstants.BINDING_ID, thingTypeId));
        }
        return Optional.empty();
    }

    @Override
    public void setThingHandler(@Nullable ThingHandler handler) {
        if (handler instanceof LivisiBridgeHandler) {
            bridgeHandler = (LivisiBridgeHandler) handler;
        }
    }

    @Override
    public @Nullable ThingHandler getThingHandler() {
        return bridgeHandler;
    }
}
