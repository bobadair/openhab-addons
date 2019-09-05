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
import java.util.List;

import org.eclipse.smarthome.core.thing.Thing;
import org.openhab.binding.lutron.internal.KeypadComponent;
import org.openhab.binding.lutron.internal.discovery.project.ComponentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler responsible for communicating with Lutron Tabletop seeTouch keypads used in RadioRA2 and Homeworks QS systems
 * (e.g. RR-T5RL, RR-T10RL, RR-T15RL, etc.)
 *
 * @author Bob Adair - Initial contribution
 */
public class TabletopKeypadHandler extends BaseKeypadHandler {

    private static enum Component implements KeypadComponent {
        BUTTON1(1, "button1", "Button 1", ComponentType.BUTTON),
        BUTTON2(2, "button2", "Button 2", ComponentType.BUTTON),
        BUTTON3(3, "button3", "Button 3", ComponentType.BUTTON),
        BUTTON4(4, "button4", "Button 4", ComponentType.BUTTON),
        BUTTON5(5, "button5", "Button 5", ComponentType.BUTTON),
        BUTTON6(6, "button6", "Button 6", ComponentType.BUTTON),
        BUTTON7(7, "button7", "Button 7", ComponentType.BUTTON),
        BUTTON8(8, "button8", "Button 8", ComponentType.BUTTON),
        BUTTON9(9, "button9", "Button 9", ComponentType.BUTTON),
        BUTTON10(10, "button10", "Button 10", ComponentType.BUTTON),
        BUTTON11(11, "button11", "Button 11", ComponentType.BUTTON),
        BUTTON12(12, "button12", "Button 12", ComponentType.BUTTON),
        BUTTON13(13, "button13", "Button 13", ComponentType.BUTTON),
        BUTTON14(14, "button14", "Button 14", ComponentType.BUTTON),
        BUTTON15(15, "button15", "Button 15", ComponentType.BUTTON),

        BUTTON16(16, "button16", "Button 16", ComponentType.BUTTON),
        BUTTON17(17, "button17", "Button 17", ComponentType.BUTTON),

        LOWER1(20, "buttonlower1", "Lower button 1", ComponentType.BUTTON),
        RAISE1(21, "buttonraise1", "Raise button 1", ComponentType.BUTTON),
        LOWER2(22, "buttonlower2", "Lower button 2", ComponentType.BUTTON),
        RAISE2(23, "buttonraise2", "Raise button 2", ComponentType.BUTTON),
        LOWER3(24, "buttonlower3", "Lower button 3", ComponentType.BUTTON),
        RAISE3(25, "buttonraise3", "Raise button 3", ComponentType.BUTTON),

        LED1(81, "led1", "LED 1", ComponentType.LED),
        LED2(82, "led2", "LED 2", ComponentType.LED),
        LED3(83, "led3", "LED 3", ComponentType.LED),
        LED4(84, "led4", "LED 4", ComponentType.LED),
        LED5(85, "led5", "LED 5", ComponentType.LED),
        LED6(86, "led6", "LED 6", ComponentType.LED),
        LED7(87, "led7", "LED 7", ComponentType.LED),
        LED8(88, "led8", "LED 8", ComponentType.LED),
        LED9(89, "led9", "LED 9", ComponentType.LED),
        LED10(90, "led10", "LED 10", ComponentType.LED),
        LED11(91, "led11", "LED 11", ComponentType.LED),
        LED12(92, "led12", "LED 12", ComponentType.LED),
        LED13(93, "led13", "LED 13", ComponentType.LED),
        LED14(94, "led14", "LED 14", ComponentType.LED),
        LED15(95, "led15", "LED 15", ComponentType.LED),

        LED16(96, "led16", "LED 16", ComponentType.LED),
        LED17(97, "led17", "LED 17", ComponentType.LED);

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

    private static final List<Component> buttonGroup1 = Arrays.asList(Component.BUTTON1, Component.BUTTON2,
            Component.BUTTON3, Component.BUTTON4, Component.BUTTON5);
    private static final List<Component> buttonGroup2 = Arrays.asList(Component.BUTTON6, Component.BUTTON7,
            Component.BUTTON8, Component.BUTTON9, Component.BUTTON10);
    private static final List<Component> buttonGroup3 = Arrays.asList(Component.BUTTON11, Component.BUTTON12,
            Component.BUTTON13, Component.BUTTON14, Component.BUTTON15);

    private static final List<Component> buttonsBottomRL = Arrays.asList(Component.BUTTON16, Component.BUTTON17,
            Component.LOWER3, Component.RAISE3);
    private static final List<Component> buttonsBottomCRL = Arrays.asList(Component.LOWER1, Component.RAISE1,
            Component.LOWER2, Component.RAISE2, Component.LOWER3, Component.RAISE3);

    private static final List<Component> ledGroup1 = Arrays.asList(Component.LED1, Component.LED2, Component.LED3,
            Component.LED4, Component.LED5);
    private static final List<Component> ledGroup2 = Arrays.asList(Component.LED6, Component.LED7, Component.LED8,
            Component.LED9, Component.LED10);
    private static final List<Component> ledGroup3 = Arrays.asList(Component.LED11, Component.LED12, Component.LED13,
            Component.LED14, Component.LED15);

    private static final List<Component> LedsBottomRL = Arrays.asList(Component.LED16, Component.LED17);

    private final Logger logger = LoggerFactory.getLogger(TabletopKeypadHandler.class);

    @Override
    protected boolean isLed(int id) {
        return (id >= 81 && id <= 95);
    }

    @Override
    protected boolean isButton(int id) {
        return (id >= 1 && id <= 25);
    }

    @Override
    protected boolean isCCI(int id) {
        return false;
    }

    @Override
    protected void configureComponents(String model) {
        String mod = model == null ? "Generic" : model;
        logger.debug("Configuring components for keypad model {}", model);

        switch (mod) {
            default:
                logger.warn("No valid keypad model defined ({}). Assuming model T15RL.", mod);
                // fall through
            case "Generic":
            case "T15RL":
                buttonList.addAll(buttonGroup3);
                ledList.addAll(ledGroup3);
                // fall through
            case "T10RL":
                buttonList.addAll(buttonGroup2);
                ledList.addAll(ledGroup2);
                // fall through
            case "T5RL":
                buttonList.addAll(buttonGroup1);
                buttonList.addAll(buttonsBottomRL);
                ledList.addAll(ledGroup1);
                ledList.addAll(LedsBottomRL);
                break;

            case "T15CRL":
                buttonList.addAll(buttonGroup3);
                ledList.addAll(ledGroup3);
                // fall through
            case "T10CRL":
                buttonList.addAll(buttonGroup2);
                ledList.addAll(ledGroup2);
                // fall through
            case "T5CRL":
                buttonList.addAll(buttonGroup1);
                buttonList.addAll(buttonsBottomCRL);
                ledList.addAll(ledGroup1);
                break;
        }
    }

    public TabletopKeypadHandler(Thing thing) {
        super(thing);
    }

}
