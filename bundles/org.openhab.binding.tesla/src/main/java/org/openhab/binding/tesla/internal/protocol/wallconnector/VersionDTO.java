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
 * The {@link VersionDTO} hold data from the version endpoint.
 *
 * @author Sven Strohschein - Initial contribution
 */
public class VersionDTO {

    @SerializedName("firmware_version")
    private String firmwareVersion;
    @SerializedName("serial_number")
    private String serialNumber;

    public String getFirmwareVersion() {
        return firmwareVersion;
    }

    public String getSerialNumber() {
        return serialNumber;
    }
}
