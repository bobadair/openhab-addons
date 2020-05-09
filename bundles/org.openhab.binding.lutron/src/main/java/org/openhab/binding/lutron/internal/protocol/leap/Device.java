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
 * LEAP Device Object
 *
 * @author Bob Adair - Initial contribution
 */
public class Device extends AbstractBodyType {
    private static final Pattern ZONE_HREF_PATTERN = Pattern.compile("/zone/([0-9]+)");
    private static final Pattern DEVICE_HREF_PATTERN = Pattern.compile("/device/([0-9]+)");

    @SerializedName("href")
    public String href;

    @SerializedName("Name")
    public String name;

    @SerializedName("FullyQualifiedName")
    public String[] fullyQualifiedName;

    @SerializedName("Parent")
    public Href parent = new Href();

    @SerializedName("SerialNumber")
    public String serialNumber;

    @SerializedName("ModelNumber")
    public String modelNumber;

    @SerializedName("DeviceType")
    public String deviceType;

    @SerializedName("LocalZones")
    public Href[] localZones;

    @SerializedName("AssociatedArea")
    public Href associatedArea = new Href();

    @SerializedName("OccupancySensors")
    public Href[] occupancySensors;

    @SerializedName("LinkNodes")
    public Href[] linkNodes;

    @SerializedName("DeviceRules")
    public Href[] deviceRules;

    @SerializedName("RepeaterProperties")
    public RepeaterProperties repeaterProperties;

    @SerializedName("FirmwareImage")
    public FirmwareImage firmwareImage;

    public class FirmwareImage {
        @SerializedName("Firmware")
        public Firmware firmware;
        @SerializedName("Installed")
        public Installed installed;
    }

    public class Firmware {
        @SerializedName("DisplayName")
        public String displayName;
    }

    public class Installed {
        @SerializedName("Year")
        public int year;
        @SerializedName("Month")
        public int month;
        @SerializedName("Day")
        public int day;
        @SerializedName("Hour")
        public int hour;
        @SerializedName("Minute")
        public int minute;
        @SerializedName("Second")
        public int second;
        @SerializedName("Utc")
        public String utc;
    }

    public class RepeaterProperties {
        @SerializedName("IsRepeater")
        public boolean isRepeater;
    }

    public Device() {
    }

    public int getDevice() {
        if (href != null) {
            Matcher matcher = DEVICE_HREF_PATTERN.matcher(href);
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

    public int getZone() {
        if (localZones != null && localZones.length > 0) {
            Matcher matcher = ZONE_HREF_PATTERN.matcher(localZones[0].href);
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

    public String getFullyQualifiedName() {
        if (fullyQualifiedName != null && fullyQualifiedName.length > 0) {
            return String.join(" ", fullyQualifiedName);
        } else {
            return "";
        }
    }
}
