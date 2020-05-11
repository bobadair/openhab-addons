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

import static org.openhab.binding.lutron.internal.LutronBindingConstants.*;

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
import java.security.KeyFactory;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.lutron.internal.config.LeapBridgeConfig;
import org.openhab.binding.lutron.internal.discovery.LeapDeviceDiscoveryService;
import org.openhab.binding.lutron.internal.protocol.leap.AbstractBodyType;
import org.openhab.binding.lutron.internal.protocol.leap.ButtonGroup;
import org.openhab.binding.lutron.internal.protocol.leap.CommandType;
import org.openhab.binding.lutron.internal.protocol.leap.CommuniqueType;
import org.openhab.binding.lutron.internal.protocol.leap.Device;
import org.openhab.binding.lutron.internal.protocol.leap.FanSpeedType;
import org.openhab.binding.lutron.internal.protocol.leap.LeapCommand;
import org.openhab.binding.lutron.internal.protocol.leap.OccupancyGroupStatus;
import org.openhab.binding.lutron.internal.protocol.leap.Request;
import org.openhab.binding.lutron.internal.protocol.leap.ZoneStatus;
import org.openhab.binding.lutron.internal.protocol.lip.LutronCommand;
import org.openhab.binding.lutron.internal.protocol.lip.LutronCommandType;
import org.openhab.binding.lutron.internal.protocol.lip.LutronOperation;
import org.openhab.binding.lutron.internal.protocol.lip.TargetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

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

    private static final String STATUS_INITIALIZING = "Initializing";

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
    private final Object keepAliveReconnectLock = new Object();

    private final Map<Integer, Integer> zoneToDevice = new HashMap<>();
    private final Map<Integer, Integer> deviceToZone = new HashMap<>();
    private final Object zoneMapsLock = new Object();

    private @Nullable Map<Integer, List<Integer>> deviceButtonMap;
    private final Object deviceButtonMapLock = new Object();

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

    private void loadCertFromFile(String fileName, KeyStore keystore, String alias)
            throws CertificateException, FileNotFoundException, KeyStoreException {
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        FileInputStream certStream = new FileInputStream(fileName);

        Collection<? extends java.security.cert.Certificate> certCollection = certFactory
                .generateCertificates(certStream);
        Iterator<? extends java.security.cert.Certificate> i = certCollection.iterator();
        while (i.hasNext()) {
            java.security.cert.Certificate cert = i.next();
            logger.trace("Loaded certificate: {}", cert);
            keystore.setCertificateEntry(alias, cert);
        }
    }

    private void loadKeyFromFile(String fileName, KeyStore keystore, String alias)
            throws NoSuchAlgorithmException, FileNotFoundException, IOException, InvalidKeySpecException {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        FileInputStream keyStream = new FileInputStream(fileName);
        byte[] keyBytes = IOUtils.toByteArray(keyStream);
        keyStream.close();
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        PrivateKey key = keyFactory.generatePrivate(spec);
        // keystore.setKeyEntry(alias, key, null, chain);
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

                if (config.keystore != null) {
                    // Load keystore from keystore file
                    keystore.load(new FileInputStream(config.keystore), config.keystorePassword.toCharArray());
                } else if (config.clientCert != null && config.clientKey != null && config.bridgeCert != null) {
                    // Creat empty keystore and load key and certificates
                    keystore.load(null, null);
                    // loadCertFromFile(config.clientCert, keystore, "caseta");
                    // loadKeyFromFile(config.clientKey, keystore, "caseta");
                    // loadCertFromFile(config.bridgeCert, keystore, "caseta-bridge");
                } else {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                            "Neither keystore nor key files configured");
                    return;
                }

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
        sendCommand(new LeapCommand(Request.subscribeOccupancyGroupStatus()));

        // Add these test queries temporarily (TODO: Remove)
        sendCommand(new LeapCommand(Request.getAreas()));
        sendCommand(new LeapCommand(Request.getOccupancyGroups()));
        sendCommand(new LeapCommand(Request.request(CommuniqueType.READREQUEST, "/timeclock")));
        sendCommand(new LeapCommand(Request.request(CommuniqueType.READREQUEST, "/timeclockevent")));

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

        synchronized (keepAliveReconnectLock) {
            if (keepAliveReconnect != null) {
                // May be called from keepAliveReconnect thread, so call cancel with false;
                keepAliveReconnect.cancel(false);
            }
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
            while (!Thread.currentThread().isInterrupted() && writer != null) {
                LeapCommand command = sendQueue.take();
                logger.trace("Sending command {}", command);

                try {
                    BufferedWriter writer = this.writer;
                    if (writer != null) {
                        writer.write(command.toString() + "\n");
                        writer.flush();
                    }
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
        } finally {
            logger.debug("Command sender thread exiting");
        }
    }

    private void readerThreadJob() {
        logger.debug("Message reader thread started");
        String msg = null;
        try {
            BufferedReader reader = this.reader;
            while (!Thread.interrupted() && reader != null && (msg = reader.readLine()) != null) {
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
                logger.debug("No CommuniqueType found in message: {}", msg);
                return;
            }

            String communiqueType = message.get("CommuniqueType").getAsString();
            logger.debug("Received CommuniqueType: {}", communiqueType);

            // CommuniqueType type = CommuniqueType.valueOf(communiqueType);

            // Got a good message, so cancel reconnect task.
            synchronized (keepAliveReconnectLock) {
                if (keepAliveReconnect != null) {
                    logger.trace("Canceling scheduled reconnect job.");
                    keepAliveReconnect.cancel(true);
                    keepAliveReconnect = null;
                }
            }

            switch (communiqueType) {
                case "CreateResponse":
                    return;
                case "ReadResponse":
                    handleReadResponseMessage(message);
                    break;
                case "UpdateResponse":
                    break;
                case "SubscribeResponse":
                    // Subscribe responses can contain bodies with data
                    handleReadResponseMessage(message);
                    return;
                case "UnsubscribeResponse":
                    return;
                case "ExceptionResponse":
                    handleExceptionResponse(message);
                    return;
                default:
                    logger.debug("Unknown CommuniqueType received: {}", communiqueType);
                    break;
            }
        } catch (JsonParseException e) {
            logger.debug("Error parsing message: {}", e.getMessage());
            return;
        }
    }

    private void handleExceptionResponse(JsonObject message) {
        // TODO
    }

    private void handleReadResponseMessage(JsonObject message) {
        try {
            JsonObject header = message.get("Header").getAsJsonObject();

            if (!header.has("MessageBodyType")) {
                logger.trace("No MessageBodyType in header");
                return;
            }
            String messageBodyType = header.get("MessageBodyType").getAsString();
            // if (messageBodyType == null) {
            // logger.trace("No MessageBodyType in header");
            // return;
            // }
            logger.trace("MessageBodyType: {}", messageBodyType);

            if (!message.has("Body")) {
                logger.debug("No Body found in message");
                return;
            }
            JsonObject body = message.get("Body").getAsJsonObject();

            switch (messageBodyType) {
                case "OnePingResponse":
                    handleOnePingResponse(body);
                    break;
                case "OneZoneStatus":
                    handleOneZoneStatus(body);
                    break;
                case "MultipleAreaDefinition":
                    break;
                case "MultipleButtonGroupDefinition":
                    handleMultipleButtonGroupDefinition(body);
                    break;
                case "MultipleDeviceDefinition":
                    handleMultipleDeviceDefinition(body);
                    break;
                case "MultipleOccupancyGroupDefinition":
                    break;
                case "MultipleOccupancyGroupStatus":
                    handleMultipleOccupancyGroupStatus(body);
                    break;
                case "MultipleVirtualButtonDefinition":
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

    private @Nullable <T extends AbstractBodyType> T parseBodySingle(JsonObject messageBody, String memberName,
            Class<T> type) {
        try {
            if (messageBody.has(memberName)) {
                JsonObject jsonObject = messageBody.get(memberName).getAsJsonObject();
                T obj = gson.fromJson(jsonObject, type);
                return obj;
            } else {
                logger.debug("Member name {} not found in JSON message", memberName);
                return null;
            }
        } catch (IllegalStateException | JsonSyntaxException e) {
            logger.debug("Error parsing JSON message: {}", e.getMessage());
            return null;
        }
    }

    private <T extends AbstractBodyType> List<T> parseBodyMultiple(JsonObject messageBody, String memberName,
            Class<T> type) {
        List<T> objList = new LinkedList<T>();
        try {
            if (messageBody.has(memberName)) {
                JsonArray jsonArray = messageBody.get(memberName).getAsJsonArray();

                for (JsonElement element : jsonArray) {
                    JsonObject jsonObject = element.getAsJsonObject();
                    T obj = gson.fromJson(jsonObject, type);
                    objList.add(obj);
                }
                return objList;
            } else {
                logger.debug("Member name {} not found in JSON message", memberName);
                return objList;
            }
        } catch (IllegalStateException | JsonSyntaxException e) {
            logger.debug("Error parsing JSON message: {}", e.getMessage());
            return objList;
        }
    }

    private void handleMultipleOccupancyGroupStatus(JsonObject messageBody) {
        List<OccupancyGroupStatus> statusList = parseBodyMultiple(messageBody, "OccupancyGroupStatuses",
                OccupancyGroupStatus.class);
        for (OccupancyGroupStatus status : statusList) {
            logger.debug("OccupancyGroup: {} Status: {}", status.occupancyGroup.href, status.occupancyStatus);
            // TODO: Dispatch OccupancyGroup status updates
        }
    }

    private void handleOneZoneStatus(JsonObject messageBody) {
        ZoneStatus zoneStatus = parseBodySingle(messageBody, "ZoneStatus", ZoneStatus.class);
        if (zoneStatus != null) {
            handleZoneUpdate(zoneStatus);
        }
    }

    private void handleOnePingResponse(JsonObject messageBody) {
        // TODO
    }

    /**
     * Parses a MultipleDeviceDefinition message body and loads the zoneToDevice and deviceToZone maps. Also passes the
     * device data on to the discovery service and calls setBridgeProperties() with the hub's device entry.
     */
    private void handleMultipleDeviceDefinition(JsonObject messageBody) {
        List<Device> deviceList = parseBodyMultiple(messageBody, "Devices", Device.class);
        synchronized (zoneMapsLock) {
            zoneToDevice.clear();
            deviceToZone.clear();
            for (Device device : deviceList) {
                Integer zoneid = device.getZone();
                Integer deviceid = device.getDevice();
                logger.debug("Found device: {} id: {} zone: {}", device.name, deviceid, zoneid);
                if (zoneid > 0 && deviceid > 0) {
                    zoneToDevice.put(zoneid, deviceid);
                    deviceToZone.put(deviceid, zoneid);
                }
                if (deviceid == 1) { // ID 1 is the bridge
                    setBridgeProperties(device);
                }
            }
        }
        updateStatus(ThingStatus.ONLINE); // TODO: Move this
        // checkMapsLoaded();

        if (discoveryService != null) {
            discoveryService.processMultipleDeviceDefinition(messageBody);// TODO: change to pass Device list
        }
    }

    // TODO
    // private void checkMapsLoaded() {
    // ThingStatusInfo statusInfo = getThing().getStatusInfo();
    // if (statusInfo.getStatus() == ThingStatus.OFFLINE && STATUS_INITIALIZING.equals(statusInfo.getDescription())) {
    // if (devicesLoaded && areasLoaded && zonesLoaded) {
    // updateStatus(ThingStatus.ONLINE);
    // }
    // }
    // }

    /**
     * Set informational bridge properties from the Device entry for the hub/repeater
     */
    private void setBridgeProperties(Device device) {
        if (device.getDevice() == 1 && device.repeaterProperties != null) {
            Map<String, String> properties = editProperties();
            if (device.name != null) {
                properties.put(PROPERTY_PRODTYP, device.name);
            }
            if (device.modelNumber != null) {
                properties.put(Thing.PROPERTY_MODEL_ID, device.modelNumber);
            }
            if (device.serialNumber != null) {
                properties.put(Thing.PROPERTY_SERIAL_NUMBER, device.serialNumber);
            }
            if (device.firmwareImage != null && device.firmwareImage.firmware != null
                    && device.firmwareImage.firmware.displayName != null) {
                properties.put(Thing.PROPERTY_FIRMWARE_VERSION, device.firmwareImage.firmware.displayName);
            }
            updateProperties(properties);
        }
    }

    /**
     * Parse a MultipleButtonGroupDefinition message body and load the results into deviceButtonMap.
     */
    private void handleMultipleButtonGroupDefinition(JsonObject messageBody) {
        Map<Integer, List<Integer>> deviceButtonMap = new HashMap<>();

        List<ButtonGroup> buttonGroupList = parseBodyMultiple(messageBody, "ButtonGroups", ButtonGroup.class);
        for (ButtonGroup buttonGroup : buttonGroupList) {
            int parentDevice = buttonGroup.getParentDevice();
            logger.trace("Found ButtonGroup: {} parent device: {}", buttonGroup.getButtonGroup(), parentDevice);
            List<Integer> buttonList = buttonGroup.getButtonList();
            deviceButtonMap.put(parentDevice, buttonList);
        }
        synchronized (deviceButtonMapLock) {
            this.deviceButtonMap = deviceButtonMap;
        }
    }

    /**
     * Notify child thing handler of a zonelevel update from a received zone status message.
     */
    private void handleZoneUpdate(ZoneStatus zoneStatus) {
        logger.trace("zone {} status level: {}", zoneStatus.getZone(), zoneStatus.level);
        int integrationId = zoneToDevice(zoneStatus.getZone());

        // dispatch update to proper thing handler
        LutronHandler handler = findThingHandler(integrationId);
        if (handler != null) {
            try {
                handler.handleUpdate(LutronCommandType.OUTPUT, LutronCommand.ACTION_ZONELEVEL.toString(),
                        new Integer(zoneStatus.level).toString());
            } catch (NumberFormatException e) {
                logger.warn("Number format exception parsing update");
            } catch (RuntimeException e) {
                logger.warn("Runtime exception while processing update");
            }
        } else {
            logger.debug("No thing configured for integration ID {}", integrationId);
        }
    }

    /**
     * Queue a LEAP command for transmission by the sender thread.
     */
    public void sendCommand(LeapCommand command) {
        sendQueue.add(command);
    }

    /**
     * Receives commands from child things and translates them to LEAP commands
     */
    @Override
    public void sendCommand(LutronCommand command) {
        logger.trace("Received request to send LIP command: {}", command);

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

    /**
     * Translates an query output LutronCommand (i.e. a LIP ?OUTPUT command) to LEAP protocol.
     */
    private void queryOutputToLeap(LutronCommand command) {
        int action = command.getNumberParameter(0);

        if (action == LutronCommand.ACTION_ZONELEVEL) {
            Integer zone = deviceToZone(command.getIntegrationId());
            if (zone != null) {
                sendCommand(new LeapCommand(Request.getZoneStatus(zone)));
            } else {
                logger.debug("Dropping query output command for ID {}. No zone mapping available.",
                        command.getIntegrationId());
            }
        }
    }

    /**
     * Translates an execute output LutronCommand (i.e. a LIP !OUTPUT command) to LEAP protocol.
     */
    private void execOutputToLeap(LutronCommand command) {
        int action = command.getNumberParameter(0);
        Integer zone = deviceToZone(command.getIntegrationId());
        int zoneInt;

        if (zone == null) {
            logger.debug("Dropping output command for ID {}. No zone mapping available.", command.getIntegrationId());
            return;
        } else {
            zoneInt = zone;
        }

        if (command.targetType == TargetType.SWITCH || command.targetType == TargetType.DIMMER) {
            if (action == LutronCommand.ACTION_ZONELEVEL) {
                int value = command.getNumberParameter(1);
                sendCommand(new LeapCommand(Request.goToLevel(zoneInt, value)));
            } else {
                logger.debug("Dropping unsupported switch action: {}", command);
                return;
            }
        } else if (command.targetType == TargetType.SHADE) {
            if (action == LutronCommand.ACTION_ZONELEVEL) {
                int value = command.getNumberParameter(1);
                sendCommand(new LeapCommand(Request.goToLevel(zoneInt, value)));
            } else if (action == LutronCommand.ACTION_STARTRAISING) {
                sendCommand(new LeapCommand(Request.zoneCommand(zoneInt, CommandType.RAISE)));
                // TODO: Set channel to 100%
            } else if (action == LutronCommand.ACTION_STARTLOWERING) {
                sendCommand(new LeapCommand(Request.zoneCommand(zoneInt, CommandType.LOWER)));
                // TODO: Set channel to 0%
            } else if (action == LutronCommand.ACTION_STOP) {
                sendCommand(new LeapCommand(Request.zoneCommand(zoneInt, CommandType.STOP)));
            } else {
                logger.debug("Dropping unsupported shade action: {}", command);
                return;
            }
        } else if (command.targetType == TargetType.FAN) {
            if (action == LutronCommand.ACTION_ZONELEVEL) {
                int value = command.getNumberParameter(1);
                if (value > 0) {
                    sendCommand(new LeapCommand(Request.goToFanSpeed(zoneInt, FanSpeedType.HIGH)));
                } else {
                    sendCommand(new LeapCommand(Request.goToFanSpeed(zoneInt, FanSpeedType.OFF)));
                }
                // TODO: Add in other fan speeds
            } else {
                logger.debug("Dropping unsupported fan action: {}", command);
            }
        } else {
            logger.debug("Dropping command for unsupported output: {}", command);
        }
    }

    /**
     * Translates an execute device LutronCommand (i.e. a LIP !DEVICE command) to LEAP protocol.
     */
    private void execDeviceToLeap(LutronCommand command) {
        int id = command.getIntegrationId();
        int component;
        int action;

        try {
            component = command.getNumberParameter(0);
            action = command.getNumberParameter(1);
        } catch (IllegalArgumentException e) {
            logger.debug("Ignoring device command. Invalid parameters.");
            return;
        }

        if (command.targetType == TargetType.KEYPAD) {
            int leapComponent;
            if (command.getLeapComponent() != null) {
                leapComponent = command.getLeapComponent();
            } else {
                logger.debug("Ignoring device command. No leap component in command.");
                return;
            }

            if (action == LutronCommand.ACTION_PRESS) {
                int button = getButton(id, leapComponent);
                if (button > 0) {
                    sendCommand(new LeapCommand(Request.buttonCommand(button, CommandType.PRESSANDHOLD)));
                }
            } else if (action == LutronCommand.ACTION_RELEASE) {
                int button = getButton(id, leapComponent);
                if (button > 0) {
                    sendCommand(new LeapCommand(Request.buttonCommand(button, CommandType.RELEASE)));
                }
            } else {
                logger.debug("Ignoring device command. Unsupported action.");
            }
        } else if (command.targetType == TargetType.VIRTUALKEYPAD) {
            if (action == LutronCommand.ACTION_PRESS) {
                sendCommand(new LeapCommand(Request.virtualButtonCommand(component, CommandType.PRESSANDRELEASE)));
            } else if (action != LutronCommand.ACTION_RELEASE) {
                logger.debug("Ignoring device command. Unsupported action.");
            }
        } else {
            logger.debug("Ignoring device command. Unsupported target type.");
        }
    }

    /**
     * Returns LEAP button number for given integrationID and component. Returns 0 if button number cannot be
     * determined.
     */
    int getButton(int integrationID, int component) {
        synchronized (deviceButtonMapLock) {
            if (deviceButtonMap != null) {
                List<Integer> buttonList = deviceButtonMap.get(integrationID);
                if (buttonList != null && component <= buttonList.size()) {
                    return buttonList.get(component - 1);
                } else {
                    logger.debug("Could not find button component {} for id {}", component, integrationID);
                    return 0;
                }
            } else {
                logger.debug("Device to button map not populated");
                return 0;
            }
        }
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
        synchronized (zoneMapsLock) {
            return zoneToDevice.get(zone);
        }
    }

    private @Nullable Integer deviceToZone(int device) {
        synchronized (zoneMapsLock) {
            return deviceToZone.get(device);
        }
    }

    private void sendKeepAlive() {
        logger.trace("Sending keepalive query");
        sendCommand(new LeapCommand(Request.ping()));
        // Reconnect if no response is received within KEEPALIVE_TIMEOUT_SECONDS.
        synchronized (keepAliveReconnectLock) {
            keepAliveReconnect = scheduler.schedule(this::reconnect, KEEPALIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (channelUID.getId().equals(CHANNEL_COMMAND)) {
            if (command instanceof StringType) {
                sendCommand(new LeapCommand(command.toString()));
            }
        }
    }

    @Override
    public void dispose() {
        disconnect();
    }
}
