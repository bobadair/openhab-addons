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
import org.openhab.binding.lutron.internal.protocol.lip.LutronCommand;
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

    protected @Nullable AbstractBridgeHandler getBridgeHandler() {
        Bridge bridge = getBridge();

        return bridge == null ? null : (AbstractBridgeHandler) bridge.getHandler();
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

    private void sendCommand(LutronCommand command) {
        AbstractBridgeHandler bridgeHandler = getBridgeHandler();

        if (bridgeHandler == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.HANDLER_MISSING_ERROR, "No bridge associated");
            thingOfflineNotify();
        } else {
            bridgeHandler.sendCommand(command);
        }
    }

    protected void output(TargetType type, Object... parameters) {
        sendCommand(new LutronCommand(type, LutronOperation.EXECUTE, LutronCommandType.OUTPUT, getIntegrationId(), null,
                parameters));
    }

    protected void device(TargetType type, @Nullable Integer leapComponent, Object... parameters) {
        sendCommand(new LutronCommand(type, LutronOperation.EXECUTE, LutronCommandType.DEVICE, getIntegrationId(),
                leapComponent, parameters));
    }

    protected void device(TargetType type, Object... parameters) {
        sendCommand(new LutronCommand(type, LutronOperation.EXECUTE, LutronCommandType.DEVICE, getIntegrationId(), null,
                parameters));
    }

    protected void timeclock(Object... parameters) {
        sendCommand(new LutronCommand(TargetType.TIMECLOCK, LutronOperation.EXECUTE, LutronCommandType.TIMECLOCK,
                getIntegrationId(), null, parameters));
    }

    protected void greenMode(Object... parameters) {
        sendCommand(new LutronCommand(TargetType.GREENMODE, LutronOperation.EXECUTE, LutronCommandType.MODE,
                getIntegrationId(), null, parameters));
    }

    protected void queryOutput(TargetType type, Object... parameters) {
        sendCommand(new LutronCommand(type, LutronOperation.QUERY, LutronCommandType.OUTPUT, getIntegrationId(), null,
                parameters));
    }

    protected void queryDevice(TargetType type, Object... parameters) {
        sendCommand(new LutronCommand(type, LutronOperation.QUERY, LutronCommandType.DEVICE, getIntegrationId(), null,
                parameters));
    }

    protected void queryTimeclock(Object... parameters) {
        sendCommand(new LutronCommand(TargetType.TIMECLOCK, LutronOperation.QUERY, LutronCommandType.TIMECLOCK,
                getIntegrationId(), null, parameters));
    }

    protected void queryGreenMode(Object... parameters) {
        sendCommand(new LutronCommand(TargetType.GREENMODE, LutronOperation.QUERY, LutronCommandType.MODE,
                getIntegrationId(), null, parameters));
    }

    protected void queryGroup(Object... parameters) {
        sendCommand(new LutronCommand(TargetType.GROUP, LutronOperation.QUERY, LutronCommandType.GROUP,
                getIntegrationId(), null, parameters));
    }
}
