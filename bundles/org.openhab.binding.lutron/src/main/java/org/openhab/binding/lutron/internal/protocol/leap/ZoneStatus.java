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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.annotations.SerializedName;

/**
 * LEAP ZoneStatus Object
 *
 * @author Bob Adair - Initial contribution
 */
public class ZoneStatus {
    private static final Pattern ZONE_HREF_PATTERN = Pattern.compile("/zone/([0-9]+)");

    @SerializedName("href")
    public String href = "";
    @SerializedName("Level")
    public int level; // 0-100
    @SerializedName("SwitchedLevel")
    public String switchedLevel = ""; // "On" or "Off"
    @SerializedName("Zone")
    public Href zone = new Href();;
    @SerializedName("StatusAccuracy")
    public String statusAccuracy = ""; // "Good" or ??

    public ZoneStatus() {
    }

    public ZoneStatus(String href, int level, String switchedLevel, Href zone, String statusAccuracy) {
        this.href = href;
        this.level = level;
        this.switchedLevel = switchedLevel;
        this.zone = zone;
        this.statusAccuracy = statusAccuracy;
    }

    public boolean statusAccuracyGood() {
        return statusAccuracy.equals("Good");
    }

    public boolean switchedLevelOn() {
        return switchedLevel.equals("On");
    }

    public int getZone() {
        if (zone != null && zone.href != null) {
            Matcher matcher = ZONE_HREF_PATTERN.matcher(zone.href);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        return 0;
    }
}
