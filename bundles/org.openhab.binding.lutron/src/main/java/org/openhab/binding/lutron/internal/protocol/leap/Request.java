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

/**
 * Contains static methods for constructing LEAP messages
 *
 * @author Bob Adair - Initial contribution
 */
@NonNullByDefault
public class Request {

    public static String goToLevel(int zone, int value) {
        String request = "{\"CommuniqueType\": \"CreateRequest\","
                + "\"Header\": {\"Url\": \"/zone/%d/commandprocessor\"}," + "\"Body\": {" + "\"Command\": {"
                + "\"CommandType\": \"GoToLevel\"," + "\"Parameter\": [{\"Type\": \"Level\", \"Value\": %d}]}}}";
        return String.format(request, zone, value);
    }

    public static String goToFanSpeed(int zone, FanSpeedType fanSpeed) {
        String request = "{\"CommuniqueType\": \"CreateRequest\","
                + "\"Header\": {\"Url\": \"/zone/%d/commandprocessor\"}," + "\"Body\": {"
                + "\"Command\": {\"CommandType\": \"GoToFanSpeed\","
                + "\"FanSpeedParameters\": {\"FanSpeed\": \"%s\"}}}}";
        return String.format(request, zone, fanSpeed.toString());
    }

    public static String buttonCommand(int button, CommandType command) {
        String request = "{\"CommuniqueType\": \"CreateRequest\","
                + "\"Header\": {\"Url\": \"/button/%d/commandprocessor\"},"
                + "\"Body\": {\"Command\": {\"CommandType\": \"%s\"}}}";
        return String.format(request, button, command.toString());
    }

    public static String virtualButtonCommand(int virtualbutton, CommandType command) {
        String request = "{\"CommuniqueType\": \"CreateRequest\","
                + "\"Header\": {\"Url\": \"/virtualbutton/%d/commandprocessor\"},"
                + "\"Body\": {\"Command\": {\"CommandType\": \"%s\"}}}";
        return String.format(request, virtualbutton, command.toString());
    }

    public static String zoneCommand(int zone, CommandType commandType) {
        String request = "{\"CommuniqueType\": \"CreateRequest\","
                + "\"Header\": {\"Url\": \"/zone/%d/commandprocessor\"}," + "\"Body\": {" + "\"Command\": {"
                + "\"CommandType\": \"%s\"}}}";
        return String.format(request, zone, commandType.toString());
    }

    public static String request(CommuniqueType cType, String url) {
        String request = "{\"CommuniqueType\": \"%s\",\"Header\": {\"Url\": \"%s\"}}";
        return String.format(request, cType.toString(), url);
    }

    public static String ping() {
        return request(CommuniqueType.READREQUEST, "/server/1/status/ping");
    }

    public static String getDevices() {
        return request(CommuniqueType.READREQUEST, "/device");
    }

    public static String getVirtualButtons() {
        return request(CommuniqueType.READREQUEST, "/virtualbutton");
    }

    public static String getButtonGroups() {
        return request(CommuniqueType.READREQUEST, "/buttongroup");
    }

    public static String getAreas() {
        return request(CommuniqueType.READREQUEST, "/area");
    }

    public static String getOccupancyGroups() {
        return request(CommuniqueType.READREQUEST, "/occupancygroup");
    }

    public static String getZoneStatus(int zone) {
        return request(CommuniqueType.READREQUEST, String.format("/zone/%d/status", zone));
    }

    public static String getOccupancyGroupStatus() {
        return request(CommuniqueType.READREQUEST, "/occupancygroup/status");
    }

    public static String subscribeOccupancyGroupStatus() {
        return request(CommuniqueType.SUBSCRIBEREQUEST, "/occupancygroup/status");
    }
}
