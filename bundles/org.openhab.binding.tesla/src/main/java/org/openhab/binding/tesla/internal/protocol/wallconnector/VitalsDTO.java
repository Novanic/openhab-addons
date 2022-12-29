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
package org.openhab.binding.tesla.internal.protocol.wallconnector;

import com.google.gson.annotations.SerializedName;

/**
 * The {@link VitalsDTO} holds data of the vitals endpoint.
 *
 * @author Sven Strohschein - Initial contribution
 */
public class VitalsDTO {

    @SerializedName("vehicle_connected")
    private boolean isVehicleConnected;
    @SerializedName("session_s")
    private long sessionDurationInSeconds;
    @SerializedName("session_energy_wh")
    private float sessionEnergyWh;

    public boolean isVehicleConnected() {
        return isVehicleConnected;
    }

    public long getSessionDurationInSeconds() {
        return sessionDurationInSeconds;
    }

    public float getSessionEnergyWh() {
        return sessionEnergyWh;
    }
}
