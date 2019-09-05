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

import java.util.EnumSet;

import org.eclipse.smarthome.core.thing.Thing;
import org.openhab.binding.lutron.internal.KeypadComponent;
import org.openhab.binding.lutron.internal.discovery.project.ComponentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler responsible for communicating with the virtual buttons on the RadioRA2 main repeater
 *
 * @author Bob Adair - Initial contribution
 */
public class VirtualKeypadHandler extends BaseKeypadHandler {

    private static enum Component implements KeypadComponent {
        BUTTON1(1, "button1", "Virtual button", ComponentType.BUTTON),
        BUTTON2(2, "button2", "Virtual button", ComponentType.BUTTON),
        BUTTON3(3, "button3", "Virtual button", ComponentType.BUTTON),
        BUTTON4(4, "button4", "Virtual button", ComponentType.BUTTON),
        BUTTON5(5, "button5", "Virtual button", ComponentType.BUTTON),
        BUTTON6(6, "button6", "Virtual button", ComponentType.BUTTON),
        BUTTON7(7, "button7", "Virtual button", ComponentType.BUTTON),
        BUTTON8(8, "button8", "Virtual button", ComponentType.BUTTON),
        BUTTON9(9, "button9", "Virtual button", ComponentType.BUTTON),
        BUTTON10(10, "button10", "Virtual button", ComponentType.BUTTON),
        BUTTON11(11, "button11", "Virtual button", ComponentType.BUTTON),
        BUTTON12(12, "button12", "Virtual button", ComponentType.BUTTON),
        BUTTON13(13, "button13", "Virtual button", ComponentType.BUTTON),
        BUTTON14(14, "button14", "Virtual button", ComponentType.BUTTON),
        BUTTON15(15, "button15", "Virtual button", ComponentType.BUTTON),
        BUTTON16(16, "button16", "Virtual button", ComponentType.BUTTON),
        BUTTON17(17, "button17", "Virtual button", ComponentType.BUTTON),
        BUTTON18(18, "button18", "Virtual button", ComponentType.BUTTON),
        BUTTON19(19, "button19", "Virtual button", ComponentType.BUTTON),
        BUTTON20(20, "button20", "Virtual button", ComponentType.BUTTON),
        BUTTON21(21, "button21", "Virtual button", ComponentType.BUTTON),
        BUTTON22(22, "button22", "Virtual button", ComponentType.BUTTON),
        BUTTON23(23, "button23", "Virtual button", ComponentType.BUTTON),
        BUTTON24(24, "button24", "Virtual button", ComponentType.BUTTON),
        BUTTON25(25, "button25", "Virtual button", ComponentType.BUTTON),
        BUTTON26(26, "button26", "Virtual button", ComponentType.BUTTON),
        BUTTON27(27, "button27", "Virtual button", ComponentType.BUTTON),
        BUTTON28(28, "button28", "Virtual button", ComponentType.BUTTON),
        BUTTON29(29, "button29", "Virtual button", ComponentType.BUTTON),
        BUTTON30(30, "button30", "Virtual button", ComponentType.BUTTON),
        BUTTON31(31, "button31", "Virtual button", ComponentType.BUTTON),
        BUTTON32(32, "button32", "Virtual button", ComponentType.BUTTON),
        BUTTON33(33, "button33", "Virtual button", ComponentType.BUTTON),
        BUTTON34(34, "button34", "Virtual button", ComponentType.BUTTON),
        BUTTON35(35, "button35", "Virtual button", ComponentType.BUTTON),
        BUTTON36(36, "button36", "Virtual button", ComponentType.BUTTON),
        BUTTON37(37, "button37", "Virtual button", ComponentType.BUTTON),
        BUTTON38(38, "button38", "Virtual button", ComponentType.BUTTON),
        BUTTON39(39, "button39", "Virtual button", ComponentType.BUTTON),
        BUTTON40(40, "button40", "Virtual button", ComponentType.BUTTON),
        BUTTON41(41, "button41", "Virtual button", ComponentType.BUTTON),
        BUTTON42(42, "button42", "Virtual button", ComponentType.BUTTON),
        BUTTON43(43, "button43", "Virtual button", ComponentType.BUTTON),
        BUTTON44(44, "button44", "Virtual button", ComponentType.BUTTON),
        BUTTON45(45, "button45", "Virtual button", ComponentType.BUTTON),
        BUTTON46(46, "button46", "Virtual button", ComponentType.BUTTON),
        BUTTON47(47, "button47", "Virtual button", ComponentType.BUTTON),
        BUTTON48(48, "button48", "Virtual button", ComponentType.BUTTON),
        BUTTON49(49, "button49", "Virtual button", ComponentType.BUTTON),
        BUTTON50(50, "button50", "Virtual button", ComponentType.BUTTON),
        BUTTON51(51, "button51", "Virtual button", ComponentType.BUTTON),
        BUTTON52(52, "button52", "Virtual button", ComponentType.BUTTON),
        BUTTON53(53, "button53", "Virtual button", ComponentType.BUTTON),
        BUTTON54(54, "button54", "Virtual button", ComponentType.BUTTON),
        BUTTON55(55, "button55", "Virtual button", ComponentType.BUTTON),
        BUTTON56(56, "button56", "Virtual button", ComponentType.BUTTON),
        BUTTON57(57, "button57", "Virtual button", ComponentType.BUTTON),
        BUTTON58(58, "button58", "Virtual button", ComponentType.BUTTON),
        BUTTON59(59, "button59", "Virtual button", ComponentType.BUTTON),
        BUTTON60(60, "button60", "Virtual button", ComponentType.BUTTON),
        BUTTON61(61, "button61", "Virtual button", ComponentType.BUTTON),
        BUTTON62(62, "button62", "Virtual button", ComponentType.BUTTON),
        BUTTON63(63, "button63", "Virtual button", ComponentType.BUTTON),
        BUTTON64(64, "button64", "Virtual button", ComponentType.BUTTON),
        BUTTON65(65, "button65", "Virtual button", ComponentType.BUTTON),
        BUTTON66(66, "button66", "Virtual button", ComponentType.BUTTON),
        BUTTON67(67, "button67", "Virtual button", ComponentType.BUTTON),
        BUTTON68(68, "button68", "Virtual button", ComponentType.BUTTON),
        BUTTON69(69, "button69", "Virtual button", ComponentType.BUTTON),
        BUTTON70(70, "button70", "Virtual button", ComponentType.BUTTON),
        BUTTON71(71, "button71", "Virtual button", ComponentType.BUTTON),
        BUTTON72(72, "button72", "Virtual button", ComponentType.BUTTON),
        BUTTON73(73, "button73", "Virtual button", ComponentType.BUTTON),
        BUTTON74(74, "button74", "Virtual button", ComponentType.BUTTON),
        BUTTON75(75, "button75", "Virtual button", ComponentType.BUTTON),
        BUTTON76(76, "button76", "Virtual button", ComponentType.BUTTON),
        BUTTON77(77, "button77", "Virtual button", ComponentType.BUTTON),
        BUTTON78(78, "button78", "Virtual button", ComponentType.BUTTON),
        BUTTON79(79, "button79", "Virtual button", ComponentType.BUTTON),
        BUTTON80(80, "button80", "Virtual button", ComponentType.BUTTON),
        BUTTON81(81, "button81", "Virtual button", ComponentType.BUTTON),
        BUTTON82(82, "button82", "Virtual button", ComponentType.BUTTON),
        BUTTON83(83, "button83", "Virtual button", ComponentType.BUTTON),
        BUTTON84(84, "button84", "Virtual button", ComponentType.BUTTON),
        BUTTON85(85, "button85", "Virtual button", ComponentType.BUTTON),
        BUTTON86(86, "button86", "Virtual button", ComponentType.BUTTON),
        BUTTON87(87, "button87", "Virtual button", ComponentType.BUTTON),
        BUTTON88(88, "button88", "Virtual button", ComponentType.BUTTON),
        BUTTON89(89, "button89", "Virtual button", ComponentType.BUTTON),
        BUTTON90(90, "button90", "Virtual button", ComponentType.BUTTON),
        BUTTON91(91, "button91", "Virtual button", ComponentType.BUTTON),
        BUTTON92(92, "button92", "Virtual button", ComponentType.BUTTON),
        BUTTON93(93, "button93", "Virtual button", ComponentType.BUTTON),
        BUTTON94(94, "button94", "Virtual button", ComponentType.BUTTON),
        BUTTON95(95, "button95", "Virtual button", ComponentType.BUTTON),
        BUTTON96(96, "button96", "Virtual button", ComponentType.BUTTON),
        BUTTON97(97, "button97", "Virtual button", ComponentType.BUTTON),
        BUTTON98(98, "button98", "Virtual button", ComponentType.BUTTON),
        BUTTON99(99, "button99", "Virtual button", ComponentType.BUTTON),
        BUTTON100(100, "button100", "Virtual button", ComponentType.BUTTON),

        LED1(101, "led1", "Virtual LED", ComponentType.LED),
        LED2(102, "led2", "Virtual LED", ComponentType.LED),
        LED3(103, "led3", "Virtual LED", ComponentType.LED),
        LED4(104, "led4", "Virtual LED", ComponentType.LED),
        LED5(105, "led5", "Virtual LED", ComponentType.LED),
        LED6(106, "led6", "Virtual LED", ComponentType.LED),
        LED7(107, "led7", "Virtual LED", ComponentType.LED),
        LED8(108, "led8", "Virtual LED", ComponentType.LED),
        LED9(109, "led9", "Virtual LED", ComponentType.LED),
        LED10(110, "led10", "Virtual LED", ComponentType.LED),
        LED11(111, "led11", "Virtual LED", ComponentType.LED),
        LED12(112, "led12", "Virtual LED", ComponentType.LED),
        LED13(113, "led13", "Virtual LED", ComponentType.LED),
        LED14(114, "led14", "Virtual LED", ComponentType.LED),
        LED15(115, "led15", "Virtual LED", ComponentType.LED),
        LED16(116, "led16", "Virtual LED", ComponentType.LED),
        LED17(117, "led17", "Virtual LED", ComponentType.LED),
        LED18(118, "led18", "Virtual LED", ComponentType.LED),
        LED19(119, "led19", "Virtual LED", ComponentType.LED),
        LED20(120, "led20", "Virtual LED", ComponentType.LED),
        LED21(121, "led21", "Virtual LED", ComponentType.LED),
        LED22(122, "led22", "Virtual LED", ComponentType.LED),
        LED23(123, "led23", "Virtual LED", ComponentType.LED),
        LED24(124, "led24", "Virtual LED", ComponentType.LED),
        LED25(125, "led25", "Virtual LED", ComponentType.LED),
        LED26(126, "led26", "Virtual LED", ComponentType.LED),
        LED27(127, "led27", "Virtual LED", ComponentType.LED),
        LED28(128, "led28", "Virtual LED", ComponentType.LED),
        LED29(129, "led29", "Virtual LED", ComponentType.LED),
        LED30(130, "led30", "Virtual LED", ComponentType.LED),
        LED31(131, "led31", "Virtual LED", ComponentType.LED),
        LED32(132, "led32", "Virtual LED", ComponentType.LED),
        LED33(133, "led33", "Virtual LED", ComponentType.LED),
        LED34(134, "led34", "Virtual LED", ComponentType.LED),
        LED35(135, "led35", "Virtual LED", ComponentType.LED),
        LED36(136, "led36", "Virtual LED", ComponentType.LED),
        LED37(137, "led37", "Virtual LED", ComponentType.LED),
        LED38(138, "led38", "Virtual LED", ComponentType.LED),
        LED39(139, "led39", "Virtual LED", ComponentType.LED),
        LED40(140, "led40", "Virtual LED", ComponentType.LED),
        LED41(141, "led41", "Virtual LED", ComponentType.LED),
        LED42(142, "led42", "Virtual LED", ComponentType.LED),
        LED43(143, "led43", "Virtual LED", ComponentType.LED),
        LED44(144, "led44", "Virtual LED", ComponentType.LED),
        LED45(145, "led45", "Virtual LED", ComponentType.LED),
        LED46(146, "led46", "Virtual LED", ComponentType.LED),
        LED47(147, "led47", "Virtual LED", ComponentType.LED),
        LED48(148, "led48", "Virtual LED", ComponentType.LED),
        LED49(149, "led49", "Virtual LED", ComponentType.LED),
        LED50(150, "led50", "Virtual LED", ComponentType.LED),
        LED51(151, "led51", "Virtual LED", ComponentType.LED),
        LED52(152, "led52", "Virtual LED", ComponentType.LED),
        LED53(153, "led53", "Virtual LED", ComponentType.LED),
        LED54(154, "led54", "Virtual LED", ComponentType.LED),
        LED55(155, "led55", "Virtual LED", ComponentType.LED),
        LED56(156, "led56", "Virtual LED", ComponentType.LED),
        LED57(157, "led57", "Virtual LED", ComponentType.LED),
        LED58(158, "led58", "Virtual LED", ComponentType.LED),
        LED59(159, "led59", "Virtual LED", ComponentType.LED),
        LED60(160, "led60", "Virtual LED", ComponentType.LED),
        LED61(161, "led61", "Virtual LED", ComponentType.LED),
        LED62(162, "led62", "Virtual LED", ComponentType.LED),
        LED63(163, "led63", "Virtual LED", ComponentType.LED),
        LED64(164, "led64", "Virtual LED", ComponentType.LED),
        LED65(165, "led65", "Virtual LED", ComponentType.LED),
        LED66(166, "led66", "Virtual LED", ComponentType.LED),
        LED67(167, "led67", "Virtual LED", ComponentType.LED),
        LED68(168, "led68", "Virtual LED", ComponentType.LED),
        LED69(169, "led69", "Virtual LED", ComponentType.LED),
        LED70(170, "led70", "Virtual LED", ComponentType.LED),
        LED71(171, "led71", "Virtual LED", ComponentType.LED),
        LED72(172, "led72", "Virtual LED", ComponentType.LED),
        LED73(173, "led73", "Virtual LED", ComponentType.LED),
        LED74(174, "led74", "Virtual LED", ComponentType.LED),
        LED75(175, "led75", "Virtual LED", ComponentType.LED),
        LED76(176, "led76", "Virtual LED", ComponentType.LED),
        LED77(177, "led77", "Virtual LED", ComponentType.LED),
        LED78(178, "led78", "Virtual LED", ComponentType.LED),
        LED79(179, "led79", "Virtual LED", ComponentType.LED),
        LED80(180, "led80", "Virtual LED", ComponentType.LED),
        LED81(181, "led81", "Virtual LED", ComponentType.LED),
        LED82(182, "led82", "Virtual LED", ComponentType.LED),
        LED83(183, "led83", "Virtual LED", ComponentType.LED),
        LED84(184, "led84", "Virtual LED", ComponentType.LED),
        LED85(185, "led85", "Virtual LED", ComponentType.LED),
        LED86(186, "led86", "Virtual LED", ComponentType.LED),
        LED87(187, "led87", "Virtual LED", ComponentType.LED),
        LED88(188, "led88", "Virtual LED", ComponentType.LED),
        LED89(189, "led89", "Virtual LED", ComponentType.LED),
        LED90(190, "led90", "Virtual LED", ComponentType.LED),
        LED91(191, "led91", "Virtual LED", ComponentType.LED),
        LED92(192, "led92", "Virtual LED", ComponentType.LED),
        LED93(193, "led93", "Virtual LED", ComponentType.LED),
        LED94(194, "led94", "Virtual LED", ComponentType.LED),
        LED95(195, "led95", "Virtual LED", ComponentType.LED),
        LED96(196, "led96", "Virtual LED", ComponentType.LED),
        LED97(197, "led97", "Virtual LED", ComponentType.LED),
        LED98(198, "led98", "Virtual LED", ComponentType.LED),
        LED99(199, "led99", "Virtual LED", ComponentType.LED),
        LED100(200, "led100", "Virtual LED", ComponentType.LED);

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

    private final Logger logger = LoggerFactory.getLogger(VirtualKeypadHandler.class);

    @Override
    protected boolean isLed(int id) {
        return (id >= 101 && id <= 200);
    }

    @Override
    protected boolean isButton(int id) {
        return (id >= 1 && id <= 100);
    }

    @Override
    protected boolean isCCI(int id) {
        return false;
    }

    @Override
    protected void configureComponents(String model) {
        logger.debug("Configuring components for virtual keypad");

        for (Component x : EnumSet.allOf(Component.class)) {
            if (isLed(x.id)) {
                ledList.add(x);
            }
            if (isButton(x.id)) {
                buttonList.add(x);
            }
        }
    }

    public VirtualKeypadHandler(Thing thing) {
        super(thing);
        // Mark all channels "Advanced" since most are unlikely to be used in any particular config
        advancedChannels = true;
    }

}
