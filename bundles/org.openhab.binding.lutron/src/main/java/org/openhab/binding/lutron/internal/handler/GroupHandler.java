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

import static org.openhab.binding.lutron.internal.LutronBindingConstants.CHANNEL_GROUPSTATE;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.lutron.internal.config.GroupConfig;
import org.openhab.binding.lutron.internal.protocol.lip.LutronCommand;
import org.openhab.binding.lutron.internal.protocol.lip.LutronCommandType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler responsible for communicating occupancy group states.
 *
 * @author Bob Adair - Initial contribution
 */
@NonNullByDefault
public class GroupHandler extends LutronHandler {
    private static final String STATE_OCCUPIED = "OCCUPIED";
    private static final String STATE_UNOCCUPIED = "UNOCCUPIED";
    private static final String STATE_UNKNOWN = "UNKNOWN";

    private final Logger logger = LoggerFactory.getLogger(GroupHandler.class);

    private @NonNullByDefault({}) GroupConfig config;

    public GroupHandler(Thing thing) {
        super(thing);
    }

    @Override
    public int getIntegrationId() {
        if (this.config == null) {
            throw new IllegalStateException("handler not initialized");
        }
        return config.integrationId;
    }

    @Override
    public void initialize() {
        config = getConfigAs(GroupConfig.class);
        if (config.integrationId <= 0) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "No valid integrationId configured");
            return;
        }
        logger.debug("Initializing Occupancy Group handler for integration ID {}", getIntegrationId());
        initDeviceState();
    }

    @Override
    protected void initDeviceState() {
        logger.debug("Initializing device state for Occupancy Group {}", getIntegrationId());
        Bridge bridge = getBridge();
        if (bridge == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "No bridge configured");
        } else if (bridge.getStatus() == ThingStatus.ONLINE) {
            updateStatus(ThingStatus.UNKNOWN, ThingStatusDetail.NONE, "Awaiting initial response");
            queryGroup(LutronCommand.ACTION_GROUPSTATE); // handleUpdate() will set thing status to
                                                         // online when response arrives
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        }
    }

    @Override
    public void channelLinked(ChannelUID channelUID) {
        if (channelUID.getId().equals(CHANNEL_GROUPSTATE)) {
            // Refresh state when new item is linked.
            queryGroup(LutronCommand.ACTION_GROUPSTATE);
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (channelUID.getId().equals(CHANNEL_GROUPSTATE)) {
            if (command instanceof RefreshType) {
                queryGroup(LutronCommand.ACTION_GROUPSTATE);
            }
        }
    }

    @Override
    public void handleUpdate(LutronCommandType type, String... parameters) {
        int state;

        if (type == LutronCommandType.GROUP && parameters.length > 1
                && LutronCommand.ACTION_GROUPSTATE.toString().equals(parameters[0])) {
            try {
                state = Integer.parseInt(parameters[1]);
            } catch (NumberFormatException e) {
                logger.debug("Error parsing response parameter: {}", e.getMessage());
                return;
            }
            if (getThing().getStatus() == ThingStatus.UNKNOWN) {
                updateStatus(ThingStatus.ONLINE);
            }
            if (state == LutronCommand.STATE_GRP_OCCUPIED) {
                updateState(CHANNEL_GROUPSTATE, new StringType(STATE_OCCUPIED));
            } else if (state == LutronCommand.STATE_GRP_UNOCCUPIED) {
                updateState(CHANNEL_GROUPSTATE, new StringType(STATE_UNOCCUPIED));
            } else if (state == LutronCommand.STATE_GRP_UNKNOWN) {
                updateState(CHANNEL_GROUPSTATE, new StringType(STATE_UNKNOWN));
            } else {
                logger.debug("Invalid occupancy state received: {}", state);
                updateState(CHANNEL_GROUPSTATE, new StringType(STATE_UNKNOWN));
            }
        }
    }
}
