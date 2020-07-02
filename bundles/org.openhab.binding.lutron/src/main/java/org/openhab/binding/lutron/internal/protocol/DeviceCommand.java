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
 * Lutron DEVICE command object
 *
 * @author Bob Adair - Initial contribution
 */
@NonNullByDefault
public class DeviceCommand extends LutronCommandNew {

    private final Logger logger = LoggerFactory.getLogger(DeviceCommand.class);

    private final Integer component;
    private final @Nullable Integer leapComponent;
    private final Integer action;
    private final @Nullable Object parameter;

    public DeviceCommand(TargetType targetType, LutronOperation operation, Integer integrationId, Integer component,
            @Nullable Integer leapComponent, Integer action, @Nullable Object parameter) {
        super(targetType, operation, LutronCommandType.DEVICE, integrationId);
        this.action = action;
        this.component = component;
        this.leapComponent = leapComponent;
        this.parameter = parameter;
    }

    @Override
    public String lipCommand() {
        StringBuilder builder = new StringBuilder().append(operation).append(commandType);
        builder.append(',').append(integrationId);
        builder.append(',').append(component);
        builder.append(',').append(action);
        if (parameter != null) {
            builder.append(',').append(parameter);
        }

        return builder.toString();
    }

    @Override
    public @Nullable LeapCommand leapCommand(LeapBridgeHandler bridgeHandler, @Nullable Integer leapZone) {
        if (targetType == TargetType.KEYPAD) {
            if (leapComponent == null) {
                logger.debug("Ignoring device command. No leap component in command.");
                return null;
            }

            if (action.equals(LutronCommand.ACTION_PRESS) && integrationId != null && leapComponent != null) {
                int button = bridgeHandler.getButton(integrationId, leapComponent);
                if (button > 0) {
                    return new LeapCommand(Request.buttonCommand(button, CommandType.PRESSANDHOLD));
                }
            } else if (action.equals(LutronCommand.ACTION_RELEASE) && integrationId != null && leapComponent != null) {
                int button = bridgeHandler.getButton(integrationId, leapComponent);
                if (button > 0) {
                    return new LeapCommand(Request.buttonCommand(button, CommandType.RELEASE));
                }
            } else {
                logger.debug("Ignoring device command with unsupported action.");
                return null;
            }
        } else if (targetType == TargetType.VIRTUALKEYPAD) {
            if (action.equals(LutronCommand.ACTION_PRESS)) {
                return new LeapCommand(Request.virtualButtonCommand(component, CommandType.PRESSANDRELEASE));
            } else if (!action.equals(LutronCommand.ACTION_RELEASE)) {
                logger.debug("Ignoring device command with unsupported action.");
                return null;
            }
        } else {
            logger.debug("Ignoring device command with unsupported target type.");
            return null;
        }
        logger.debug("Ignoring unsupported device command.");
        return null;
    }

    @Override
    public String toString() {
        return lipCommand();
    }
}
