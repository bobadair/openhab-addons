/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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
package org.openhab.binding.lutron.internal.protocol.leap;

import org.eclipse.jdt.annotation.NonNullByDefault;

import com.google.gson.annotations.SerializedName;

/**
 * LEAP CommandType enum
 *
 * @author Bob Adair - Initial contribution
 */
@NonNullByDefault
public enum FanSpeedType {
    @SerializedName("Off")
    OFF("Off"),
    @SerializedName("Low")
    LOW("Low"),
    @SerializedName("Medium")
    MEDIUM("Medium"),
    @SerializedName("MediumHigh")
    MEDIUMHIGH("MediumHigh"),
    @SerializedName("High")
    HIGH("High");

    private final transient String string;

    FanSpeedType(String string) {
        this.string = string;
    }

    @Override
    public String toString() {
        return string;
    }
}
