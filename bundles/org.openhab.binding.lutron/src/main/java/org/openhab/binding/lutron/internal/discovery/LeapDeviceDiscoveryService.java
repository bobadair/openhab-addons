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

package org.openhab.binding.lutron.internal.discovery;

import static org.openhab.binding.lutron.internal.LutronBindingConstants.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.lutron.internal.LutronHandlerFactory;
import org.openhab.binding.lutron.internal.handler.LeapBridgeHandler;
import org.openhab.binding.lutron.internal.protocol.leap.dto.Area;
import org.openhab.binding.lutron.internal.protocol.leap.dto.Device;
import org.openhab.binding.lutron.internal.protocol.leap.dto.OccupancyGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link LeapDeviceDiscoveryService} discovers devices paired with Lutron bridges using the LEAP protocol, such as
 * Caseta.
 *
 * @author Bob Adair - Initial contribution
 */
@NonNullByDefault
public class LeapDeviceDiscoveryService extends AbstractDiscoveryService {

    private static final int DISCOVERY_SERVICE_TIMEOUT = 60; // seconds

    private final Logger logger = LoggerFactory.getLogger(LeapDeviceDiscoveryService.class);

    private @Nullable Map<String, String> areaMap;
    private @Nullable List<OccupancyGroup> oGroupList;

    // private final Gson gson;
    private final LeapBridgeHandler bridgeHandler;

    public LeapDeviceDiscoveryService(LeapBridgeHandler bridgeHandler) throws IllegalArgumentException {
        super(LutronHandlerFactory.DISCOVERABLE_DEVICE_TYPES_UIDS, DISCOVERY_SERVICE_TIMEOUT);
        this.bridgeHandler = bridgeHandler;
        // gson = new GsonBuilder().create();
    }

    @Override
    protected void startScan() {
        // TODO: Query bridge for devices, areas, occupancy groups
    }

    public void processDeviceDefinitions(List<Device> deviceList) {
        for (Device device : deviceList) {
            // Integer zoneid = device.getZone();
            Integer deviceId = device.getDevice();
            String label = device.getFullyQualifiedName();
            if (deviceId > 0) {
                logger.trace("Discovered device: {} type: {} id: {}", label, device.deviceType, deviceId);
                if (device.deviceType != null) {
                    switch (device.deviceType) {
                        case "SmartBridge":
                            notifyDiscovery(THING_TYPE_VIRTUALKEYPAD, deviceId, label, "model", "Caseta");
                            break;
                        case "WallDimmer":
                        case "PlugInDimmer":
                            notifyDiscovery(THING_TYPE_DIMMER, deviceId, label);
                            break;
                        case "WallSwitch":
                        case "PlugInSwitch":
                            notifyDiscovery(THING_TYPE_SWITCH, deviceId, label);
                            break;
                        case "CasetaFanSpeedController":
                        case "MaestroFanSpeedController":
                            notifyDiscovery(THING_TYPE_FAN, deviceId, label);
                            break;
                        case "Pico2Button":
                            notifyDiscovery(THING_TYPE_PICO, deviceId, label, "model", "2B");
                            break;
                        case "Pico2ButtonRaiseLower":
                            notifyDiscovery(THING_TYPE_PICO, deviceId, label, "model", "2BRL");
                            break;
                        case "Pico3ButtonRaiseLower":
                            notifyDiscovery(THING_TYPE_PICO, deviceId, label, "model", "3BRL");
                            break;
                        case "SerenaRollerShade":
                        case "SerenaHoneycombShade":
                        case "TriathlonRollerShade":
                        case "TriathlonHoneycombShade":
                        case "QsWirelessShade":
                            notifyDiscovery(THING_TYPE_SHADE, deviceId, label);
                            break;
                        case "RPSOccupancySensor":
                            notifyDiscovery(THING_TYPE_OCCUPANCYSENSOR, deviceId, label);
                            break;
                        default:
                            logger.info("Unrecognized device type: {}", device.deviceType);
                            break;
                    }
                }
            }
        }
    }

    private void processOccupancyGroups() {
        Map<String, String> areaMap = this.areaMap;
        List<OccupancyGroup> oGroupList = this.oGroupList;

        if (areaMap != null && oGroupList != null) {
            for (OccupancyGroup oGroup : oGroupList) {
                logger.trace("Processing OccupancyGroup: {}", oGroup.href);
                int groupNum = oGroup.getOccupancyGroup();
                // Only process occupancy groups with associated occupancy sensors
                if (groupNum > 0 && oGroup.associatedSensors != null) {
                    String areaName;
                    if (oGroup.associatedAreas.length > 0) {
                        // If multiple associated areas are listed, use only the first
                        areaName = areaMap.get(oGroup.associatedAreas[0].href);
                    } else {
                        areaName = "Occupancy Group";
                    }
                    notifyDiscovery(THING_TYPE_GROUP, groupNum, areaName);
                }
            }
            areaMap = null;
            oGroupList = null;
        }
    }

    public void setOccupancyGroups(List<OccupancyGroup> oGroupList) {
        this.oGroupList = oGroupList;

        if (areaMap != null) {
            processOccupancyGroups();
        }
    }

    public void setAreas(List<Area> areaList) {
        Map<String, String> areaMap = new HashMap<>();

        for (Area area : areaList) {
            areaMap.put(area.href, area.name);
        }
        this.areaMap = areaMap;

        if (oGroupList != null) {
            processOccupancyGroups();
        }
    }

    private void notifyDiscovery(ThingTypeUID thingTypeUID, @Nullable Integer integrationId, String label,
            @Nullable String propName, @Nullable Object propValue) {
        if (integrationId == null) {
            logger.info("Discovered {} with no integration ID", label);
            return;
        }
        ThingUID bridgeUID = this.bridgeHandler.getThing().getUID();
        ThingUID uid = new ThingUID(thingTypeUID, bridgeUID, integrationId.toString());

        Map<String, Object> properties = new HashMap<>();

        properties.put(INTEGRATION_ID, integrationId);
        if (propName != null && propValue != null) {
            properties.put(propName, propValue);
        }

        DiscoveryResult result = DiscoveryResultBuilder.create(uid).withBridge(bridgeUID).withLabel(label)
                .withProperties(properties).withRepresentationProperty(INTEGRATION_ID).build();
        thingDiscovered(result);
        logger.debug("Discovered {}", uid);
    }

    private void notifyDiscovery(ThingTypeUID thingTypeUID, Integer integrationId, String label) {
        notifyDiscovery(thingTypeUID, integrationId, label, null, null);
    }
}
