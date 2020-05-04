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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.lutron.internal.config.LeapBridgeConfig;
import org.openhab.binding.lutron.internal.discovery.LeapDeviceDiscoveryService;
import org.openhab.binding.lutron.internal.protocol.leap.ButtonGroup;
import org.openhab.binding.lutron.internal.protocol.leap.Device;
import org.openhab.binding.lutron.internal.protocol.leap.Href;
import org.openhab.binding.lutron.internal.protocol.leap.LeapCommand;
import org.openhab.binding.lutron.internal.protocol.leap.Request;
import org.openhab.binding.lutron.internal.protocol.leap.ZoneStatus;
import org.openhab.binding.lutron.internal.protocol.lip.LutronCommand;
import org.openhab.binding.lutron.internal.protocol.lip.LutronCommandType;
import org.openhab.binding.lutron.internal.protocol.lip.LutronOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * Bridge handler responsible for communicating with Lutron hubs using LEAP protocol, such as Caseta and RA2 Select.
 * This is an experimental testbed for LEAP communications and should not be used in production.
 *
 * @author Bob Adair - Initial contribution
 */
@NonNullByDefault
public class LeapBridgeHandler extends AbstractBridgeHandler {
    private static final int DEFAULT_RECONNECT_MINUTES = 5;
    private static final int DEFAULT_HEARTBEAT_MINUTES = 5;
    private static final long KEEPALIVE_TIMEOUT_SECONDS = 30;

    private static final String TEST_DATA_FILE = ""; // "C:\\Users\\Bob\\Documents\\leapdata.json";
    private static final String TEST_OUTPUT_FILE = ""; // "C:\\Users\\Bob\\Documents\\leapdata.out";

    private final Logger logger = LoggerFactory.getLogger(LeapBridgeHandler.class);

    private @NonNullByDefault({}) LeapBridgeConfig config;
    private int reconnectInterval;
    private int heartbeatInterval;
    private int sendDelay;

    private @NonNullByDefault({}) SSLSocketFactory sslsocketfactory;
    private @Nullable SSLSocket sslsocket;
    private @Nullable BufferedWriter writer;
    private @Nullable BufferedReader reader;

    private final Gson gson;

    private final BlockingQueue<LeapCommand> sendQueue = new LinkedBlockingQueue<>();

    private @Nullable Thread senderThread;
    private @Nullable Thread readerThread;

    private @Nullable ScheduledFuture<?> keepAlive;
    private @Nullable ScheduledFuture<?> keepAliveReconnect;
    private @Nullable ScheduledFuture<?> connectRetryJob;

    private final Map<Integer, Integer> zoneToDevice = new HashMap<>();
    private final Map<Integer, Integer> deviceToZone = new HashMap<>();
    private final Object zoneMapLock = new Object();

    // private @Nullable Date lastDbUpdateDate;
    private @Nullable LeapDeviceDiscoveryService discoveryService;

    public void setDiscoveryService(LeapDeviceDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    public LeapBridgeHandler(Bridge bridge) {
        super(bridge);
        gson = new GsonBuilder()
                // .registerTypeAdapter(Id.class, new IdTypeAdapter())
                // .enableComplexMapKeySerialization()
                // .serializeNulls()
                // .setDateFormat(DateFormat.LONG)
                // .setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE)
                // .setPrettyPrinting()
                .create();
    }

    @Override
    public void initialize() {
        SSLContext sslContext;

        config = getConfigAs(LeapBridgeConfig.class);

        if (StringUtils.isEmpty(config.ipAddress)) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "bridge address not specified");
            return;
        }

        reconnectInterval = (config.reconnect > 0) ? config.reconnect : DEFAULT_RECONNECT_MINUTES;
        heartbeatInterval = (config.heartbeat > 0) ? config.heartbeat : DEFAULT_HEARTBEAT_MINUTES;
        sendDelay = (config.delay < 0) ? 0 : config.delay;

        if (TEST_DATA_FILE.isEmpty()) {
            try {
                logger.trace("Initializing keystore");
                // Create keystore
                // KeyStore keystore = KeyStore.getInstance("JKS");
                KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
                // keystore.load(new FileInputStream("C:\\Users\\Bob\\Documents\\leap\\lutron.keystore"),
                // "secret".toCharArray());
                keystore.load(new FileInputStream(config.keystore), config.keystorePassword.toCharArray());
                // keystore.load(null, null);
                // keystore.setCertificateEntry(alias, cert);

                // Read certificate(s) in to certCollection but don't do anything with them yet
                // CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                // FileInputStream clientCertStream = new FileInputStream(config.clientCert);
                //
                // Collection<? extends java.security.cert.Certificate> certCollection = certFactory
                // .generateCertificates(clientCertStream);
                // Iterator<? extends java.security.cert.Certificate> i = certCollection.iterator();
                // while (i.hasNext()) {
                // java.security.cert.Certificate cert = i.next();
                // logger.trace("Loaded certificate: {}", cert);
                // // TODO: load into keystore
                // }

                logger.trace("Initializing SSL Context");
                // KeyManagerFactory kmf = KeyManagerFactory.getInstance("X509"); // X509 not available
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(keystore, config.keystorePassword.toCharArray());

                // TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(keystore);

                sslContext = SSLContext.getInstance("TLS");
                TrustManager[] trustManagers = tmf.getTrustManagers();
                sslContext.init(kmf.getKeyManagers(), trustManagers, null);

                sslsocketfactory = sslContext.getSocketFactory();
            } catch (FileNotFoundException e) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "File not found");
                return;
            } catch (CertificateException e) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Certificate exception");
                return;
            } catch (UnrecoverableKeyException e) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "Key unrecoverable with supplied password");
                return;
            } catch (KeyManagementException e) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NONE, "Key management exception");
                logger.warn("Key management exception", e);
                return;
            } catch (KeyStoreException | NoSuchAlgorithmException | IOException e) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NONE, "Error initializing keystore");
                logger.warn("Error initializing keystore", e);
                return;
            }
        }

        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NONE, "Connecting");
        scheduler.submit(this::connect); // start the async connect task
    }

    private synchronized void connect() {
        if (!TEST_DATA_FILE.isEmpty()) {
            // Open test data files instead of socket
            File testData = new File(TEST_DATA_FILE);
            File testOutput = new File(TEST_OUTPUT_FILE);

            try {
                reader = Files.newBufferedReader(testData.toPath(), StandardCharsets.UTF_8);
                writer = Files.newBufferedWriter(testOutput.toPath(), StandardCharsets.UTF_8);
            } catch (IOException | SecurityException e) {
                logger.debug("Exception opening test data files {} : {}", TEST_DATA_FILE, e.getMessage());
            }
        } else {
            // Open SSL connection to bridge
            try {
                logger.debug("Opening SSL connection to {}:{}", config.ipAddress, config.port);
                sslsocket = (SSLSocket) sslsocketfactory.createSocket(config.ipAddress, config.port);
                sslsocket.startHandshake();
                writer = new BufferedWriter(new OutputStreamWriter(sslsocket.getOutputStream()));
                reader = new BufferedReader(new InputStreamReader(sslsocket.getInputStream()));
            } catch (UnknownHostException e) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Unknown host");
                return;
            } catch (IOException e) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "IO error opening SSL connection");
                disconnect();
                scheduleConnectRetry(reconnectInterval); // Possibly a temporary problem. Try again later.
                return;
            } catch (IllegalArgumentException e) {
                // port out of valid range
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Invalid port number");
                return;
            }
        }

        readerThread = new Thread(this::readerThreadJob, "Lutron reader");
        readerThread.setDaemon(true);
        readerThread.start();

        senderThread = new Thread(this::senderThreadJob, "Lutron sender");
        senderThread.setDaemon(true);
        senderThread.start();

        sendCommand(new LeapCommand(Request.getDevices()));
        sendCommand(new LeapCommand(Request.getButtonGroups()));
        sendCommand(new LeapCommand(Request.subscribeOccupancyGroups()));

        // Delay updating status to online until device/zone info received

        logger.debug("Starting keepAlive job with interval {}", heartbeatInterval);
        keepAlive = scheduler.scheduleWithFixedDelay(this::sendKeepAlive, heartbeatInterval, heartbeatInterval,
                TimeUnit.MINUTES);
    }

    private void scheduleConnectRetry(long waitMinutes) {
        logger.debug("Scheduling connection retry in {} minutes", waitMinutes);
        connectRetryJob = scheduler.schedule(this::connect, waitMinutes, TimeUnit.MINUTES);
    }

    private synchronized void disconnect() {
        logger.debug("Disconnecting");

        if (connectRetryJob != null) {
            connectRetryJob.cancel(true);
        }
        if (keepAlive != null) {
            keepAlive.cancel(true);
        }
        if (keepAliveReconnect != null) {
            // May be called from keepAliveReconnect thread, so call cancel with false;
            keepAliveReconnect.cancel(false);
        }
        if (senderThread != null && senderThread.isAlive()) {
            senderThread.interrupt();
        }
        if (readerThread != null && readerThread.isAlive()) {
            readerThread.interrupt();
        }
        if (sslsocket != null) {
            try {
                sslsocket.close();
            } catch (IOException e) {
                logger.debug("Error closing SSL socket: {}", e.getMessage());
            }
            sslsocket = null;
        }
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
                logger.debug("Error closing reader: {}", e.getMessage());
            }
        }
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                logger.debug("Error closing writer: {}", e.getMessage());
            }
        }
    }

    private synchronized void reconnect() {
        logger.debug("Attempting to reconnect to the bridge");
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "reconnecting");
        disconnect();
        connect();
    }

    private void senderThreadJob() {
        logger.debug("Command sender thread started");
        try {
            while (!Thread.currentThread().isInterrupted()) {
                LeapCommand command = sendQueue.take();
                logger.debug("Sending command {}", command);

                try {
                    writer.write(command.toString() + "\n");
                    writer.flush();
                } catch (IOException e) {
                    logger.warn("Communication error, will try to reconnect. Error: {}", e.getMessage());
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
                    sendQueue.add(command); // Requeue command
                    reconnect();
                    break; // reconnect() will start a new thread; terminate this one
                }
                if (sendDelay > 0) {
                    Thread.sleep(sendDelay); // introduce delay to throttle send rate
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void readerThreadJob() {
        logger.debug("Message reader thread started");
        String msg = null;
        try {
            BufferedReader reader = this.reader;
            while (!Thread.interrupted() && reader != null && (msg = reader.readLine()) != null) {
                // logger.trace("Received msg: {}", msg);
                handleMessage(msg);
            }
            if (msg == null) {
                logger.info("End of input stream detected");
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Connection lost");
            }
        } catch (IOException e) {
            logger.debug("I/O error while reading from stream: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        } catch (RuntimeException e) {
            logger.warn("Runtime exception in reader thread", e);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        } finally {
            logger.debug("Message reader thread exiting");
        }
    }

    private void handleMessage(String msg) {
        if (msg.trim().equals("")) {
            return; // Ignore empty lines
        }
        logger.trace("Received message: {}", msg);

        try {
            JsonObject message = (JsonObject) new JsonParser().parse(msg);

            if (!message.has("CommuniqueType")) {
                logger.debug("No CommuniqueType found in message");
                return;
            }

            String communiqueType = message.get("CommuniqueType").getAsString();
            logger.trace("CommuniqueType: {}", communiqueType);

            // Got a good message, so cancel reconnect task.
            // TODO: add locking
            if (keepAliveReconnect != null) {
                logger.trace("Canceling scheduled reconnect job.");
                keepAliveReconnect.cancel(true);
                keepAliveReconnect = null;
            }

            switch (communiqueType) {
                case "SubscribeResponse":
                    return;
                case "UnsubscribeResponse":
                    return;
                case "ExceptionResponse":
                    return;
                case "CreateResponse":
                    return;
                case "ReadResponse":
                    handleReadResponseMessage(message);
                    break;
                default:
                    logger.debug("Unknown CommuniqueType received: {}", communiqueType);
                    break;
            }
        } catch (JsonParseException e) {
            logger.debug("Error parsing message: {}", e.getMessage());
            return;
        }
    }

    private void handleReadResponseMessage(JsonObject message) {
        try {
            JsonObject header = message.get("Header").getAsJsonObject();
            String messageBodyType = header.get("MessageBodyType").getAsString();
            logger.trace("MessageBodyType: {}", messageBodyType);

            if (!message.has("Body")) {
                logger.debug("No Body found in message");
                return;
            }
            JsonObject body = message.get("Body").getAsJsonObject();

            switch (messageBodyType) {
                case "OneZoneStatus":
                    handleOneZoneStatus(body);
                    break;
                case "MultipleOccupancyGroupDefinition":
                    break;
                case "MultipleDeviceDefinition":
                    handleMultipleDeviceDefinition(body);
                    break;
                case "MultipleButtonGroupDefinition":
                    handleMultipleButtonGroupDefinition(body);
                    break;
                case "MultipleAreaDefinition":
                    break;
                case "MultipleVirtualButtonDefinition":
                    break;
                case "MultipleOccupancyGroupStatus":
                    break;
                case "OnePingResponse":
                    handleOnePingResponse(body);
                    break;
                default:
                    logger.debug("Unknown MessageBodyType received: {}", messageBodyType);
                    break;
            }
        } catch (JsonParseException | IllegalStateException e) {
            logger.debug("Error parsing message: {}", e.getMessage());
            return;
        }
    }

    private void handleOneZoneStatus(JsonObject messageBody) {
        JsonObject status = messageBody.get("ZoneStatus").getAsJsonObject();
        ZoneStatus zoneStatus = gson.fromJson(status, ZoneStatus.class);
        handleZoneUpdate(zoneStatus);
    }

    private void handleOnePingResponse(JsonObject messageBody) {
        // TODO
    }

    private void handleMultipleDeviceDefinition(JsonObject messageBody) {
        // TODO: add try/catch here
        JsonArray devices = messageBody.get("Devices").getAsJsonArray();
        synchronized (zoneMapLock) {
            zoneToDevice.clear();
            deviceToZone.clear();
            for (JsonElement element : devices) {
                JsonObject jsonDeviceObj = element.getAsJsonObject();
                Device device = gson.fromJson(jsonDeviceObj, Device.class);
                Integer zoneid = device.getZone();
                Integer deviceid = device.getDevice();
                logger.debug("Found device: {} id: {} zone: {}", device.name, deviceid, zoneid);
                if (zoneid > 0 && deviceid > 0) {
                    zoneToDevice.put(zoneid, deviceid);
                    deviceToZone.put(deviceid, zoneid);
                }
            }
        }
        updateStatus(ThingStatus.ONLINE); // TODO: Move this

        if (discoveryService != null) {
            discoveryService.processMultipleDeviceDefinition(messageBody);
        }
    }

    private void handleMultipleButtonGroupDefinition(JsonObject messageBody) {
        JsonArray buttonGroups = messageBody.get("ButtonGroups").getAsJsonArray();
        for (JsonElement element : buttonGroups) {
            JsonObject jsonButtonGroupObj = element.getAsJsonObject();
            ButtonGroup buttonGroup = gson.fromJson(jsonButtonGroupObj, ButtonGroup.class);
            logger.trace("Found ButtonGroup: {} parent device: {}", buttonGroup.getButtonGroup(),
                    buttonGroup.getParentDevice());
            for (Href button : buttonGroup.buttons) {
                logger.trace("Button: {}", button.href);
            }
        }
    }

    private void handleZoneUpdate(ZoneStatus zoneStatus) {
        logger.trace("zone {} status level: {}", zoneStatus.getZone(), zoneStatus.level);
        int integrationId = this.zoneToDevice(zoneStatus.getZone());

        // dispatch update to proper thing handler
        LutronHandler handler = findThingHandler(integrationId);
        if (handler != null) {
            try {
                handler.handleUpdate(LutronCommandType.OUTPUT, "1", new Integer(zoneStatus.level).toString());
            } catch (NumberFormatException e) {
                logger.warn("Number format exception parsing update");
            } catch (RuntimeException e) {
                logger.warn("Runtime exception while processing update");
            }
        } else {
            logger.debug("No thing configured for integration ID {}", integrationId);
        }
    }

    public void sendCommand(LeapCommand command) {
        sendQueue.add(command);
    }

    @Override
    public void sendCommand(LutronCommand command) {
        logger.trace("Received request to send LIP command: {}", command);

        // Translate LIP commands to LEAP commands
        if (command.getOperation() == LutronOperation.QUERY && command.getType() == LutronCommandType.OUTPUT) {
            queryOutputToLeap(command);
        } else if (command.getOperation() == LutronOperation.EXECUTE && command.getType() == LutronCommandType.OUTPUT) {
            execOutputToLeap(command);
        } else if (command.getOperation() == LutronOperation.EXECUTE && command.getType() == LutronCommandType.DEVICE) {
            execDeviceToLeap(command);
        } else {
            logger.warn("Droping command: {}", command);
        }
        return;
    }

    private void queryOutputToLeap(LutronCommand command) {
        // TODO: check parameter 0 (action) == 1
        Integer zone = deviceToZone(command.getIntegrationId());
        if (zone != null) {
            sendCommand(new LeapCommand(Request.getZoneStatus(zone)));
        } else {
            logger.debug("Dropping query output command for ID {}. No zone mapping available.",
                    command.getIntegrationId());
        }
    }

    private void execOutputToLeap(LutronCommand command) {
        int action = command.getNumberParameter(0);
        int value = command.getNumberParameter(1);

        if (action == 1) {
            Integer zone = deviceToZone(command.getIntegrationId());
            if (zone != null) {
                sendCommand(new LeapCommand(Request.goToLevel(zone, value)));
            } else {
                logger.debug("Dropping set output command for ID {}. No zone mapping available.",
                        command.getIntegrationId());
            }
        } else {
            logger.warn("Unable to process set output command {} since action != 1", command);
            return;
        }
    }

    private void execDeviceToLeap(LutronCommand command) {
        // TODO
        // sendCommand(new LeapCommand(Request.buttonCommand(button, CommandType.PRESSANDHOLD)));
        // sendCommand(new LeapCommand(Request.buttonCommand(button, CommandType.RELEASE)));
    }

    private @Nullable LutronHandler findThingHandler(int integrationId) {
        for (Thing thing : getThing().getThings()) {
            if (thing.getHandler() instanceof LutronHandler) {
                LutronHandler handler = (LutronHandler) thing.getHandler();

                try {
                    if (handler != null && handler.getIntegrationId() == integrationId) {
                        return handler;
                    }
                } catch (IllegalStateException e) {
                    logger.trace("Handler for id {} not initialized", integrationId);
                }
            }
        }
        return null;
    }

    private @Nullable Integer zoneToDevice(int zone) {
        synchronized (zoneMapLock) {
            return zoneToDevice.get(zone);
        }
    }

    private @Nullable Integer deviceToZone(int device) {
        synchronized (zoneMapLock) {
            return deviceToZone.get(device);
        }
    }

    private void sendKeepAlive() {
        logger.debug("Scheduling keepalive reconnect job");
        // Reconnect if no response is received within 30 seconds.
        keepAliveReconnect = scheduler.schedule(this::reconnect, KEEPALIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        logger.trace("Sending keepalive query");
        sendCommand(new LeapCommand(Request.ping()));
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // Accepts no commands
    }

    @Override
    public void dispose() {
        disconnect();
    }
}
