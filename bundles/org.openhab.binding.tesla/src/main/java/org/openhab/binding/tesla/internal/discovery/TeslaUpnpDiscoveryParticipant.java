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
package org.openhab.binding.tesla.internal.discovery;

import java.util.Collections;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.jupnp.model.meta.RemoteDevice;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.upnp.UpnpDiscoveryParticipant;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Component;

/**
 * TODO
 *
 * @author Sven Strohschein - Initial contribution
 */
@NonNullByDefault
@Component(service = UpnpDiscoveryParticipant.class, configurationPid = "discovery.tesla.upnp")
public class TeslaUpnpDiscoveryParticipant implements UpnpDiscoveryParticipant {

    @Override
    public Set<ThingTypeUID> getSupportedThingTypeUIDs() {
        // TODO
        return Collections.emptySet();
    }

    @Override
    public @Nullable DiscoveryResult createResult(RemoteDevice remoteDevice) {
        // TODO
        return null;
    }

    @Override
    public @Nullable ThingUID getThingUID(RemoteDevice remoteDevice) {
        // TODO
        return null;
    }
}
