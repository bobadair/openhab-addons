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
package org.openhab.binding.lutron.internal.protocol;

import java.util.Locale;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.lutron.internal.handler.LeapBridgeHandler;
import org.openhab.binding.lutron.internal.protocol.leap.CommandType;
import org.openhab.binding.lutron.internal.protocol.leap.LeapCommand;
import org.openhab.binding.lutron.internal.protocol.leap.Request;
import org.openhab.binding.lutron.internal.protocol.lip.LutronCommand;
import org.openhab.binding.lutron.internal.protocol.lip.LutronCommandType;
import org.openhab.binding.lutron.internal.protocol.lip.LutronOperation;
import org.openhab.binding.lutron.internal.protocol.lip.TargetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lutron OUTPUT command object
 *
 * @author Bob Adair - Initial contribution
 */
@NonNullByDefault
public class OutputCommand extends LutronCommandNew {

    private final Logger logger = LoggerFactory.getLogger(OutputCommand.class);

    private final Integer action;
    private final @Nullable Number parameter;
    private final @Nullable Object fadeTime;
    private final @Nullable Object delayTime;
    private final FanSpeedType fanSpeed;

    /**
     *
     * @param targetType
     * @param operation
     * @param integrationId
     * @param action
     * @param parameter
     * @param fadeTime
     * @param delayTime
     */
    public OutputCommand(TargetType targetType, LutronOperation operation, Integer integrationId, Integer action,
            @Nullable Number parameter, @Nullable Object fadeTime, @Nullable Object delayTime) {
        super(targetType, operation, LutronCommandType.OUTPUT, integrationId);
        this.action = action;
        this.parameter = parameter;
        if (parameter != null) {
            this.fanSpeed = FanSpeedType.toFanSpeedType(parameter.intValue());
        } else {
            this.fanSpeed = FanSpeedType.OFF;
        }
        this.fadeTime = fadeTime;
        this.delayTime = delayTime;
    }

    /**
     *
     * @param targetType
     * @param operation
     * @param integrationId
     * @param action
     * @param fanSpeed
     * @param fadeTime
     * @param delayTime
     */
    public OutputCommand(TargetType targetType, LutronOperation operation, Integer integrationId, Integer action,
            FanSpeedType fanSpeed, @Nullable Object fadeTime, @Nullable Object delayTime) {
        super(targetType, operation, LutronCommandType.OUTPUT, integrationId);
        this.action = action;
        this.fanSpeed = fanSpeed;
        this.parameter = fanSpeed.speed();
        this.fadeTime = fadeTime;
        this.delayTime = delayTime;
    }

    @Override
    public String lipCommand() {
        StringBuilder builder = new StringBuilder().append(operation).append(commandType);
        builder.append(',').append(integrationId);
        builder.append(',').append(action);

        if (parameter != null && targetType == TargetType.CCO && action.equals(LutronCommand.ACTION_PULSE)) {
            builder.append(',').append(String.format(Locale.ROOT, "%.2f", parameter));
        } else if (parameter != null) {
            builder.append(',').append(parameter);
        }

        if (fadeTime != null) {
            builder.append(',').append(fadeTime);
        } else if (fadeTime == null && delayTime != null) {
            // must add 0 placeholder here in order to set delay time
            builder.append(',').append("0");
        }
        if (delayTime != null) {
            builder.append(',').append(delayTime);
        }

        return builder.toString();
    }

    @Override
    public @Nullable LeapCommand leapCommand(LeapBridgeHandler bridgeHandler, @Nullable Integer leapZone) {
        int zone;
        if (leapZone == null) {
            return null;
        } else {
            zone = leapZone;
        }

        if (operation == LutronOperation.QUERY) {
            if (action.equals(LutronCommand.ACTION_ZONELEVEL)) {
                return new LeapCommand(Request.getZoneStatus(zone));
            } else {
                logger.debug("Ignoring unsupported query action");
                return null;
            }
        } else if (operation == LutronOperation.EXECUTE) {
            if (targetType == TargetType.SWITCH || targetType == TargetType.DIMMER) {
                if (action.equals(LutronCommand.ACTION_ZONELEVEL) && parameter != null) {
                    return new LeapCommand(Request.goToLevel(zone, parameter.intValue()));
                } else {
                    logger.debug("Ignoring unsupported switch/dimmer action");
                    return null;
                }
            } else if (targetType == TargetType.FAN) {
                if (action.equals(LutronCommand.ACTION_ZONELEVEL)) {
                    return new LeapCommand(Request.goToFanSpeed(zone, fanSpeed));
                } else {
                    logger.debug("Ignoring unsupported fan action");
                    return null;
                }
            } else if (targetType == TargetType.SHADE) {
                if (action.equals(LutronCommand.ACTION_ZONELEVEL) && parameter != null) {
                    return new LeapCommand(Request.goToLevel(zone, parameter.intValue()));
                } else if (action.equals(LutronCommand.ACTION_STARTRAISING)) {
                    return new LeapCommand(Request.zoneCommand(zone, CommandType.RAISE));
                } else if (action.equals(LutronCommand.ACTION_STARTLOWERING)) {
                    return new LeapCommand(Request.zoneCommand(zone, CommandType.LOWER));
                } else if (action.equals(LutronCommand.ACTION_STOP)) {
                    return new LeapCommand(Request.zoneCommand(zone, CommandType.STOP));
                } else {
                    logger.debug("Ignoring unsupported shade action");
                    return null;
                }
            } else {
                logger.debug("Ignoring unsupported target type: {}", targetType);
                return null;
            }
        } else {
            logger.debug("Ignoring unsupported operation: {}", operation);
            return null;
        }
    }

    @Override
    public String toString() {
        return lipCommand();
    }
}
