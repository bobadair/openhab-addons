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
package org.openhab.binding.lutron.internal.handler;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingStatusInfo;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.openhab.binding.lutron.internal.protocol.DeviceCommand;
import org.openhab.binding.lutron.internal.protocol.GroupCommand;
import org.openhab.binding.lutron.internal.protocol.LutronCommandNew;
import org.openhab.binding.lutron.internal.protocol.ModeCommand;
import org.openhab.binding.lutron.internal.protocol.OutputCommand;
import org.openhab.binding.lutron.internal.protocol.TimeclockCommand;
import org.openhab.binding.lutron.internal.protocol.lip.LutronCommandType;
import org.openhab.binding.lutron.internal.protocol.lip.LutronOperation;
import org.openhab.binding.lutron.internal.protocol.lip.TargetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base type for all Lutron thing handlers.
 *
 * @author Allan Tong - Initial contribution
 * @author Bob Adair - Added methods for status and state management. Added TargetType to LutronCommand for LEAP bridge.
 */
@NonNullByDefault
public abstract class LutronHandler extends BaseThingHandler {
    private final Logger logger = LoggerFactory.getLogger(LutronHandler.class);

    public LutronHandler(Thing thing) {
        super(thing);
    }

    public abstract int getIntegrationId();

    public abstract void handleUpdate(LutronCommandType type, String... parameters);

    /**
     * Queries for any device state needed at initialization time or after losing connectivity to the bridge, and
     * updates device status. Will be called when bridge status changes to ONLINE and thing has status
     * OFFLINE:BRIDGE_OFFLINE.
     */
    protected abstract void initDeviceState();

    /**
     * Called when changing thing status to offline. Subclasses may override to take any needed actions.
     */
    protected void thingOfflineNotify() {
    }

    protected @Nullable LutronBridgeHandler getBridgeHandler() {
        Bridge bridge = getBridge();

        return bridge == null ? null : (LutronBridgeHandler) bridge.getHandler();
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        logger.debug("Bridge status changed to {} for lutron device handler {}", bridgeStatusInfo.getStatus(),
                getIntegrationId());

        if (bridgeStatusInfo.getStatus() == ThingStatus.ONLINE
                && getThing().getStatusInfo().getStatusDetail() == ThingStatusDetail.BRIDGE_OFFLINE) {
            initDeviceState();

        } else if (bridgeStatusInfo.getStatus() == ThingStatus.OFFLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            thingOfflineNotify();
        }
    }

    // private void sendCommand(LutronCommand command) {
    // LutronBridgeHandler bridgeHandler = getBridgeHandler();
    //
    // if (bridgeHandler == null) {
    // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.HANDLER_MISSING_ERROR, "No bridge associated");
    // thingOfflineNotify();
    // } else {
    // bridgeHandler.sendCommand(command);
    // }
    // }

    protected void sendCommand(LutronCommandNew command) {
        LutronBridgeHandler bridgeHandler = getBridgeHandler();

        if (bridgeHandler == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.HANDLER_MISSING_ERROR, "No bridge associated");
            thingOfflineNotify();
        } else {
            bridgeHandler.sendCommand(command);
        }
    }

    protected void output(TargetType type, int action, @Nullable Number parameter, @Nullable Object fade,
            @Nullable Object delay) {
        sendCommand(
                new OutputCommand(type, LutronOperation.EXECUTE, getIntegrationId(), action, parameter, fade, delay));
    }

    protected void queryOutput(TargetType type, int action) {
        sendCommand(
                new OutputCommand(type, LutronOperation.QUERY, getIntegrationId(), action, (Integer) null, null, null));
    }

    protected void device(TargetType type, Integer component, @Nullable Integer leapComponent, Integer action,
            @Nullable Object parameter) {
        sendCommand(new DeviceCommand(type, LutronOperation.EXECUTE, getIntegrationId(), component, leapComponent,
                action, parameter));
    }

    protected void queryDevice(TargetType type, Integer component, Integer action) {
        sendCommand(new DeviceCommand(type, LutronOperation.QUERY, getIntegrationId(), component, null, action, null));
    }

    protected void timeclock(Integer action, @Nullable Object parameter, @Nullable Boolean enable) {
        sendCommand(new TimeclockCommand(LutronOperation.EXECUTE, getIntegrationId(), action, parameter, enable));
    }

    protected void queryTimeclock(Integer action) {
        sendCommand(new TimeclockCommand(LutronOperation.QUERY, getIntegrationId(), action, null, null));
    }

    protected void greenMode(Integer action, @Nullable Integer parameter) {
        sendCommand(new ModeCommand(LutronOperation.EXECUTE, getIntegrationId(), action, parameter));
    }

    protected void queryGreenMode(Integer action) {
        sendCommand(new ModeCommand(LutronOperation.QUERY, getIntegrationId(), action, null));
    }

    protected void queryGroup(Integer action) {
        sendCommand(new GroupCommand(LutronOperation.QUERY, getIntegrationId(), action, null));
    }
}
