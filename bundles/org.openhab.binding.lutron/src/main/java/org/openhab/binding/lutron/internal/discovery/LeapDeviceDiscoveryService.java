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
import org.openhab.binding.lutron.internal.protocol.leap.Device;
import org.openhab.binding.lutron.internal.protocol.leap.OccupancyGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

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

    private final Gson gson;
    private final LeapBridgeHandler bridgeHandler;

    public LeapDeviceDiscoveryService(LeapBridgeHandler bridgeHandler) throws IllegalArgumentException {
        super(LutronHandlerFactory.DISCOVERABLE_DEVICE_TYPES_UIDS, DISCOVERY_SERVICE_TIMEOUT);
        this.bridgeHandler = bridgeHandler;
        gson = new GsonBuilder().create();
    }

    @Override
    protected void startScan() {
        // TODO: Query bridge
    }

    public void processMultipleDeviceDefinition(JsonObject messageBody) {
        try {
            JsonArray devices = messageBody.get("Devices").getAsJsonArray();
            for (JsonElement element : devices) {
                JsonObject jsonDeviceObj = element.getAsJsonObject();
                Device device = gson.fromJson(jsonDeviceObj, Device.class);
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
        } catch (IllegalStateException | JsonSyntaxException e) {
            logger.debug("Exception parsing device definitions: {}", e.getMessage());
        }
    }

    public void processOccupancyGroup(OccupancyGroup occuGroup) {
        // TODO: Get Area name to use a label
        // TODO: Ignore if already processed
        int groupNum = occuGroup.getOccupancyGroup();
        if (groupNum > 0 && occuGroup.AssociatedSensors != null) {
            notifyDiscovery(THING_TYPE_GROUP, groupNum, "Occupancy Group");
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
