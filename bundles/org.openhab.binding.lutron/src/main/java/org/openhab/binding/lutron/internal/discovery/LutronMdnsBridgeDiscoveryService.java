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

package org.openhab.binding.lutron.internal.discovery;

import static org.openhab.binding.lutron.internal.LutronBindingConstants.*;

import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jmdns.ServiceInfo;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.config.discovery.mdns.MDNSDiscoveryParticipant;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link LutronMdnsBridgeDiscoveryService} discovers Lutron Caseta Smart Bridge and RA2 Select Main Repeater
 * devices on the network using mDNS.
 *
 * @author Bob Adair - Initial contribution
 */
@Component(immediate = true)
@NonNullByDefault
public class LutronMdnsBridgeDiscoveryService implements MDNSDiscoveryParticipant {

    // Lutron mDNS service <app>.<protocol>.<servicedomain>
    private static final String LUTRON_MDNS_SERVICE_TYPE = "_lutron._tcp.local.";

    private static final String PRODFAM_CASETA = "Caseta";
    private static final String PRODTYP_CASETA_SBP2 = "Smart Bridge Pro 2";
    private static final String DEVCLASS_CASETA_SBP2 = "08050100";
    private static final String DEFAULT_LABEL = "Unknown Lutron bridge";

    private static final Pattern HOSTNAME_REGEX = Pattern.compile("lutron-([0-9a-f]+)\\."); // ex: lutron-01f1529a.local

    private final Logger logger = LoggerFactory.getLogger(LutronMdnsBridgeDiscoveryService.class);

    @Override
    public Set<ThingTypeUID> getSupportedThingTypeUIDs() {
        return Collections.singleton(THING_TYPE_IPBRIDGE);
    }

    @Override
    public String getServiceType() {
        return LUTRON_MDNS_SERVICE_TYPE;
    }

    @Override
    public @Nullable DiscoveryResult createResult(ServiceInfo service) {
        if (!service.hasData()) {
            return null;
        }

        String nice = service.getNiceTextString();
        String qualifiedName = service.getQualifiedName();

        InetAddress[] ipAddresses = service.getInetAddresses();
        String devclass = service.getPropertyString("DEVCLASS");
        String codever = service.getPropertyString("CODEVER");
        String macaddr = service.getPropertyString("MACADDR");

        logger.debug("Lutron mDNS bridge discovery found Lutron mDNS service: {}", nice);
        logger.trace("Lutron mDNS service qualifiedName: {}", qualifiedName);
        logger.trace("Lutron mDNS service ipAddresses: {}", (Object[]) ipAddresses);
        logger.trace("Lutron mDNS service property DEVCLASS: {}", devclass);
        logger.trace("Lutron mDNS service property CODEVER: {}", codever);
        logger.trace("Lutron mDNS service property MACADDR: {}", macaddr);

        Map<String, Object> properties = new HashMap<>();
        String label = DEFAULT_LABEL;

        if (ipAddresses.length < 1) {
            return null;
        }
        if (ipAddresses.length > 1) {
            logger.info("Multiple addresses found for discovered bridge device. Using only the first.");
        }
        properties.put(HOST, ipAddresses[0].getHostAddress());

        String bridgeHostName = ipAddresses[0].getHostName();
        logger.debug("Lutron mDNS bridge hostname: {}", bridgeHostName);

        if (devclass != null && devclass.equals(DEVCLASS_CASETA_SBP2)) {
            properties.put(PROPERTY_PRODFAM, PRODFAM_CASETA);
            properties.put(PROPERTY_PRODTYP, PRODTYP_CASETA_SBP2);
            label = PRODFAM_CASETA + " " + PRODTYP_CASETA_SBP2;
        } else {
            properties.put(PROPERTY_PRODFAM, "Unknown");
            if (devclass != null) {
                properties.put(PROPERTY_PRODTYP, "DEVCLASS " + devclass);
            }
        }

        if (!bridgeHostName.equals(ipAddresses[0].getHostAddress())) {
            label = label + " " + bridgeHostName;
        }

        if (codever != null) {
            properties.put(PROPERTY_CODEVER, codever);
        }

        if (macaddr != null) {
            properties.put(PROPERTY_MACADDR, macaddr);
        }

        String sn = getSerial(service);
        logger.trace("Lutron mDNS bridge serial number: {}", sn);
        if (sn != null) {
            properties.put(SERIAL_NUMBER, sn);
        }

        ThingUID uid = getThingUID(service);
        if (uid != null) {
            DiscoveryResult result = DiscoveryResultBuilder.create(uid).withLabel(label).withProperties(properties)
                    .withRepresentationProperty(SERIAL_NUMBER).build();
            logger.debug("Discovered Lutron bridge device via mDNS {}", uid);
            return result;
        } else {
            logger.trace("Failed to successfully discover bridge via mDNS");
            return null;
        }
    }

    @Override
    public @Nullable ThingUID getThingUID(ServiceInfo service) {
        String serial = getSerial(service);
        if (serial == null) {
            return null;
        } else {
            return new ThingUID(THING_TYPE_IPBRIDGE, serial);
        }
    }

    /**
     * Return the serial number from the mDNS service. Extracts serial number from the mDNS service hostname or uses MAC
     * address if serial number is unavailable. Used as unique thing representation property.
     *
     * @param service Lutron mDNS service
     * @return String containing serial number or MAC, or null if neither can be determined
     */
    private @Nullable String getSerial(ServiceInfo service) {
        InetAddress[] ipAddresses = service.getInetAddresses();
        if (ipAddresses.length < 1) {
            return null;
        }
        Matcher matcher = HOSTNAME_REGEX.matcher(ipAddresses[0].getHostName());
        boolean matched = matcher.find();
        String serialnum = null;

        if (matched) {
            serialnum = matcher.group(1);
        }
        if (matched && serialnum != null && !serialnum.isEmpty()) {
            return serialnum;
        } else {
            String macaddr = service.getPropertyString("MACADDR");
            String strippedMac = stripMac(macaddr);
            if (strippedMac != null) {
                return strippedMac;
            } else {
                return null;
            }
        }
    }

    /**
     * Utility routine to strip colon characters from MAC address
     *
     * @param mac String containing the MAC address to strip
     * @return String containing stripped MAC address or null if mac is null or empty
     */
    private @Nullable String stripMac(@Nullable String mac) {
        if (mac == null || mac.isEmpty()) {
            return null;
        }
        return mac.replace(":", ""); // strip colon chars from MAC address
    }
}
