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
package org.openhab.binding.lutron.internal.protocol.lip;

/**
 * Command to a Lutron integration access point.
 *
 * @author Allan Tong - Initial contribution
 *
 */
public class LutronCommand {
    public static final Integer ACTION_ZONELEVEL = 1;
    // shades
    public static final Integer ACTION_STARTRAISING = 2;
    public static final Integer ACTION_STARTLOWERING = 3;
    public static final Integer ACTION_STOP = 4;
    public static final Integer ACTION_POSITION_UPDATE = 32; // For shades/blinds. Undocumented in protocol guide.
    // blinds
    public static final Integer ACTION_LIFTLEVEL = 1;
    public static final Integer ACTION_TILTLEVEL = 9;
    public static final Integer ACTION_LIFTTILTLEVEL = 10;
    public static final Integer ACTION_STARTRAISINGTILT = 11;
    public static final Integer ACTION_STARTLOWERINGTILT = 12;
    public static final Integer ACTION_STOPTILT = 13;
    public static final Integer ACTION_STARTRAISINGLIFT = 14;
    public static final Integer ACTION_STARTLOWERINGLIFT = 15;
    public static final Integer ACTION_STOPLIFT = 16;
    // cco
    public static final Integer ACTION_PULSE = 6;
    public static final Integer ACTION_STATE = 1;
    // keypads
    public static final Integer ACTION_PRESS = 3;
    public static final Integer ACTION_RELEASE = 4;
    public static final Integer ACTION_HOLD = 5;
    public static final Integer ACTION_LED_STATE = 9;
    public static final Integer LED_OFF = 0;
    public static final Integer LED_ON = 1;
    public static final Integer LED_FLASH = 2; // Same as 1 on RA2 keypads
    public static final Integer LED_RAPIDFLASH = 3; // Same as 1 on RA2 keypads
    // occupancy sensors
    public static final String OCCUPIED_STATE_COMPONENT = "2";
    public static final String STATE_OCCUPIED = "3";
    public static final String STATE_UNOCCUPIED = "4";
    // timeclock
    public static final Integer ACTION_CLOCKMODE = 1;
    public static final Integer ACTION_SUNRISE = 2;
    public static final Integer ACTION_SUNSET = 3;
    public static final Integer ACTION_EXECEVENT = 5;
    public static final Integer ACTION_SETEVENT = 6;
    public static final Integer EVENT_ENABLE = 1;
    public static final Integer EVENT_DISABLE = 2;
    // green mode
    public static final Integer ACTION_STEP = 1;

    public final TargetType targetType;
    private final LutronOperation operation;
    private final LutronCommandType type;
    private final int integrationId;
    public final Object[] parameters;

    public LutronCommand(TargetType targetType, LutronOperation operation, LutronCommandType type, int integrationId,
            Object... parameters) {
        this.targetType = targetType;
        this.operation = operation;
        this.type = type;
        this.integrationId = integrationId;
        this.parameters = parameters;
    }

    public LutronCommandType getType() {
        return this.type;
    }

    public LutronOperation getOperation() {
        return this.operation;
    }

    public int getIntegrationId() {
        return this.integrationId;
    }

    public Object[] getParameters() {
        return this.parameters;
    }

    public int getNumberParameter(int position) {
        if (parameters.length > position && parameters[position] instanceof Number) {
            Number num = (Number) parameters[position];
            return num.intValue();
        } else {
            throw (new IllegalArgumentException("Invalid command parameter"));
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder().append(this.operation).append(this.type);

        if (integrationId >= 0) {
            builder.append(',').append(this.integrationId);
        }

        if (parameters != null) {
            for (Object parameter : parameters) {
                builder.append(',').append(parameter);
            }
        }

        return builder.toString();
    }
}
