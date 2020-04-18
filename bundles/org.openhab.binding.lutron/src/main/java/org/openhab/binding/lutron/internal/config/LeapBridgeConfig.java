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
package org.openhab.binding.lutron.internal.config;

/**
 * Configuration settings for an {@link org.openhab.binding.lutron.internal.handler.LeapBridgeHandler}.
 *
 * @author Bob Adair - Initial contribution
 */
public class LeapBridgeConfig {
    public String ipAddress;
    public int port = 8081;
    public String keystore;
    public String keystorePassword;
    public String clientKey;
    public String clientCert;
    public String bridgeCert;
    public String keyPassword;
    public String discoveryFile;
    public int reconnect;
    public int heartbeat;
    public int delay = 0;

    public boolean sameConnectionParameters(LeapBridgeConfig config) {
        // return StringUtils.equals(ipAddress, config.ipAddress) && (reconnect == config.reconnect)
        // && (heartbeat == config.heartbeat) && (delay == config.delay);
        return true;
    }
}
