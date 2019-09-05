/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
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

import java.util.Arrays;

import org.eclipse.smarthome.core.thing.Thing;
import org.openhab.binding.lutron.internal.KeypadComponent;
import org.openhab.binding.lutron.internal.discovery.project.ComponentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler responsible for communicating with Lutron Pico keypads
 *
 * @author Bob Adair - Initial contribution
 */
public class PicoKeypadHandler extends BaseKeypadHandler {

    private static enum Component implements KeypadComponent {
        // Buttons for 2B, 2BRL, 3B, and 3BRL models
        BUTTON1(2, "button1", "Button 1", ComponentType.BUTTON),
        BUTTON2(3, "button2", "Button 2", ComponentType.BUTTON),
        BUTTON3(4, "button3", "Button 3", ComponentType.BUTTON),

        RAISE(5, "buttonraise", "Raise Button", ComponentType.BUTTON),
        LOWER(6, "buttonlower", "Lower Button", ComponentType.BUTTON),

        // Buttons for PJ2-4B model
        BUTTON1_4B(8, "button01", "Button 1", ComponentType.BUTTON),
        BUTTON2_4B(9, "button02", "Button 2", ComponentType.BUTTON),
        BUTTON3_4B(10, "button03", "Button 3", ComponentType.BUTTON),
        BUTTON4_4B(11, "button04", "Button 4", ComponentType.BUTTON);

        private final int id;
        private final String channel;
        private final String description;
        private final ComponentType type;

        Component(int id, String channel, String description, ComponentType type) {
            this.id = id;
            this.channel = channel;
            this.description = description;
            this.type = type;
        }

        @Override
        public int id() {
            return id;
        }

        @Override
        public String channel() {
            return channel;
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public ComponentType type() {
            return type;
        }
    }

    private final Logger logger = LoggerFactory.getLogger(PicoKeypadHandler.class);

    public PicoKeypadHandler(Thing thing) {
        super(thing);
    }

    @Override
    protected boolean isLed(int id) {
        return false; // No LEDs on Picos
    }

    @Override
    protected boolean isButton(int id) {
        return (id >= 2 && id <= 11);
    }

    @Override
    protected boolean isCCI(int id) {
        return false;
    }

    @Override
    protected void configureComponents(String model) {
        String mod = model == null ? "Generic" : model;
        logger.debug("Configuring components for keypad model {}", mod);

        switch (mod) {
            case "2B":
                buttonList = Arrays.asList(Component.BUTTON1, Component.BUTTON3);
                break;
            case "2BRL":
                buttonList = Arrays.asList(Component.BUTTON1, Component.BUTTON3, Component.RAISE, Component.LOWER);
                break;
            case "3B":
                buttonList = Arrays.asList(Component.BUTTON1, Component.BUTTON2, Component.BUTTON3);
                break;
            case "4B":
                buttonList = Arrays.asList(Component.BUTTON1_4B, Component.BUTTON2_4B, Component.BUTTON3_4B,
                        Component.BUTTON4_4B);
                break;
            default:
                logger.warn("No valid keypad model defined ({}). Assuming model 3BRL.", mod);
                // fall through
            case "Generic":
            case "3BRL":
                buttonList = Arrays.asList(Component.BUTTON1, Component.BUTTON2, Component.BUTTON3, Component.RAISE,
                        Component.LOWER);
                break;
        }
    }

}
