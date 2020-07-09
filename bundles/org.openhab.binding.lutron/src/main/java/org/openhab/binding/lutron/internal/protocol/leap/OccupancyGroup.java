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

import java.util.regex.Pattern;

import com.google.gson.annotations.SerializedName;

/**
 * LEAP OccupancyGroup object
 *
 * @author Bob Adair - Initial contribution
 */
public class OccupancyGroup extends AbstractMessageBody {
    private static final Pattern OGROUP_HREF_PATTERN = Pattern.compile("/occupancygroup/([0-9]+)");

    @SerializedName("href")
    public String href;

    @SerializedName("AssociatedSensors")
    public OccupancySensor[] associatedSensors;

    @SerializedName("AssociatedAreas")
    public Area[] associatedAreas; // TODO: Verify this works

    @SerializedName("ProgrammingType")
    public String programmingType;

    @SerializedName("ProgrammingModel")
    public Href programmingModel;

    public OccupancyGroup() {
    }

    public int getOccupancyGroup() {
        return hrefNumber(OGROUP_HREF_PATTERN, href);
    }
}
