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
 * LEAP ButtonGroup Object
 *
 * @author Bob Adair - Initial contribution
 */
public class ButtonGroup {
    private static final Pattern DEVICE_HREF_PATTERN = Pattern.compile("/device/([0-9]+)");
    private static final Pattern BUTTON_HREF_PATTERN = Pattern.compile("/button/([0-9]+)");
    private static final Pattern BUTTONGROUP_HREF_PATTERN = Pattern.compile("/buttongroup/([0-9]+)");

    @SerializedName("href")
    public String href;
    @SerializedName("Parent") // device href
    public Href parent = new Href();
    @SerializedName("Buttons")
    public Href[] buttons;
    @SerializedName("AffectedZones")
    public AffectedZone[] affectedZones;
    @SerializedName("SortOrder")
    public Integer sortOrder;
    @SerializedName("StopIfMoving")
    public String stopIfMoving; // Enabled or Disabled
    @SerializedName("ProgrammingType")
    public String programmingType; // Column

    // @SerializedName("Zone")
    // public Href zone = new Href();;

    public ButtonGroup() {
    }

    // public ButtonGroup(String href) {
    // // TODO
    // this.href = href;
    // }

    public int getParentDevice() {
        if (parent != null && parent.href != null) {
            Matcher matcher = DEVICE_HREF_PATTERN.matcher(parent.href);
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

    public int getButtonGroup() {
        // TODO: Abstract out numberFromHref(href, matcher)
        if (href != null) {
            Matcher matcher = BUTTONGROUP_HREF_PATTERN.matcher(href);
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
