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

import javax.jmdns.ServiceInfo;

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
 * The {@link LutronMdnsBridgeDiscoveryService} discovers Lutron Caseta Smart Bridge devices on the network using mDNS.
 * Note: We don't get the SN from mDNS so use the MAC address instead.
 *
 * @author Bob Adair - Initial contribution
 */
@Component(immediate = true)
public class LutronMdnsBridgeDiscoveryService implements MDNSDiscoveryParticipant {

    private static final String PROPERTY_PRODFAM = "productFamily";
    private static final String PROPERTY_PRODTYP = "productType";
    private static final String PROPERTY_CODEVER = "codeVersion";
    private static final String PROPERTY_MACADDR = "macAddress";

    private static final String DEVCLASS_CASETA_SBP2 = "08050100";

    private static final String LUTRON_MDNS_SERVICE_TYPE = "_lutron._tcp.local."; // Lutron mDNS
                                                                                  // app.protocol.servicedomain
    // private static final String LUTRON_MDNS_SERVICE_TYPE = "_amzn-wplay._tcp.local."; // TODO
    private static final String DEFAULT_LABEL = "Caseta Hub";

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
        String name = service.getName();
        String typeSubtype = service.getTypeWithSubtype();
        String type = service.getType();
        String subtype = service.getSubtype();
        String server = service.getServer();
        String application = service.getApplication();
        String domain = service.getDomain();
        String protocol = service.getProtocol();
        Integer port = service.getPort();
        InetAddress[] ipAddresses = service.getInetAddresses();

        String devclass = service.getPropertyString("DEVCLASS");
        String codever = service.getPropertyString("CODEVER");
        String macaddr = service.getPropertyString("MACADDR");

        // TODO REMOVE AFTER DEVELOPMENT vvv
        logger.debug("Caseta bridge discovery found device: {}", nice);
        logger.debug("Caseta bridge qualifiedName: {}", qualifiedName);
        logger.debug("Caseta bridge name: {}", name);
        logger.debug("Caseta bridge type: {}", type);
        logger.debug("Caseta bridge subtype: {}", subtype);
        logger.debug("Caseta bridge type with subtype: {}", typeSubtype);
        logger.debug("Caseta bridge application: {}", application);
        logger.debug("Caseta bridge protocol: {}", protocol);
        logger.debug("Caseta bridge domain: {}", domain);
        logger.debug("Caseta bridge server: {}", server);
        logger.debug("Caseta bridge ipAddresses: {}", (Object[]) ipAddresses);
        logger.debug("Caseta bridge port: {}", port);

        logger.debug("Caseta bridge property DEVCLASS: {}", devclass);
        logger.debug("Caseta bridge property CODEVER: {}", codever);
        logger.debug("Caseta bridge property MACADDR: {}", macaddr);

        // TODO REMOVE AFTER DEVELOPMENT ^^^

        Map<String, Object> properties = new HashMap<>();
        // properties.put(BRIDGE_TYPE, BRIDGE_TYPE_CASETA); // TODO - fix properties

        if (ipAddresses.length < 1) {
            return null;
        }
        if (ipAddresses.length > 1) {
            logger.info("Multiple addresses found for discovered hub device. Using only the first.");
        }
        properties.put(HOST, ipAddresses[0].getHostAddress());

        String strippedMac = getStrippedMac(service);
        properties.put(SERIAL_NUMBER, strippedMac);

        if (devclass != null && devclass.equals(DEVCLASS_CASETA_SBP2)) {
            properties.put(PROPERTY_PRODFAM, "Caseta");
            properties.put(PROPERTY_PRODTYP, "Smart Bridge Pro 2");
        } else {
            properties.put(PROPERTY_PRODFAM, "Unknown");
            if (devclass != null) {
                properties.put(PROPERTY_PRODTYP, "DEVCLASS=" + devclass);
            }
        }

        if (codever != null) {
            properties.put(PROPERTY_CODEVER, codever);
        }

        if (macaddr != null) {
            properties.put(PROPERTY_MACADDR, macaddr);
        }

        String label = DEFAULT_LABEL; // TODO - change to prodfam + prodtyp?
        String bridgeHostName = ipAddresses[0].getHostName();
        logger.debug("Caseta bridge hostname: {}", bridgeHostName);
        if (!bridgeHostName.equals(ipAddresses[0].getHostAddress())) {
            label = label + " " + bridgeHostName;
        }

        ThingUID uid = getThingUID(service);
        if (strippedMac != null && uid != null) {
            DiscoveryResult result = DiscoveryResultBuilder.create(uid).withLabel(label).withProperties(properties)
                    .withRepresentationProperty(SERIAL_NUMBER).build();
            return result;
        } else {
            return null;
        }
    }

    @Override
    public @Nullable ThingUID getThingUID(ServiceInfo service) {
        String strippedMac = getStrippedMac(service);
        if (strippedMac == null) {
            return null;
        } else {
            return new ThingUID(THING_TYPE_IPBRIDGE, strippedMac);
        }
    }

    private @Nullable String getStrippedMac(ServiceInfo service) {
        String mac = service.getPropertyString("MACADDR");
        // String mac = service.getPropertyString("c"); //TODO
        if (mac == null || mac.isEmpty()) {
            return null;
        }
        String strippedMac = mac.replace(":", ""); // strip colon chars from mac address
        return strippedMac;
    }
}
