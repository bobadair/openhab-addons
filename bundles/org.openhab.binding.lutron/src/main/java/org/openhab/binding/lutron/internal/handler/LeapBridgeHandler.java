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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingStatusInfo;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.lutron.internal.config.LeapBridgeConfig;
import org.openhab.binding.lutron.internal.discovery.LeapDeviceDiscoveryService;
import org.openhab.binding.lutron.internal.protocol.FanSpeedType;
import org.openhab.binding.lutron.internal.protocol.GroupCommand;
import org.openhab.binding.lutron.internal.protocol.LutronCommandNew;
import org.openhab.binding.lutron.internal.protocol.OutputCommand;
import org.openhab.binding.lutron.internal.protocol.leap.AbstractMessageBody;
import org.openhab.binding.lutron.internal.protocol.leap.LeapCommand;
import org.openhab.binding.lutron.internal.protocol.leap.Request;
import org.openhab.binding.lutron.internal.protocol.leap.dto.Area;
import org.openhab.binding.lutron.internal.protocol.leap.dto.ButtonGroup;
import org.openhab.binding.lutron.internal.protocol.leap.dto.Device;
import org.openhab.binding.lutron.internal.protocol.leap.dto.Header;
import org.openhab.binding.lutron.internal.protocol.leap.dto.OccupancyGroup;
import org.openhab.binding.lutron.internal.protocol.leap.dto.OccupancyGroupStatus;
import org.openhab.binding.lutron.internal.protocol.leap.dto.ZoneStatus;
import org.openhab.binding.lutron.internal.protocol.lip.LutronCommandType;
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
 * Bridge handler responsible for communicating with Lutron hubs that support the LEAP protocol, such as Caseta and
 * RA2 Select.
 *
 * @author Bob Adair - Initial contribution
 */
@NonNullByDefault
public class LeapBridgeHandler extends LutronBridgeHandler {
    private static final int DEFAULT_RECONNECT_MINUTES = 5;
    private static final int DEFAULT_HEARTBEAT_MINUTES = 5;
    private static final long KEEPALIVE_TIMEOUT_SECONDS = 30;

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

    private final AtomicBoolean deviceDataLoaded = new AtomicBoolean(false);
    private final AtomicBoolean buttonDataLoaded = new AtomicBoolean(false);

    private final Map<Integer, LutronHandler> childHandlerMap = new ConcurrentHashMap<>();
    private final Map<Integer, OGroupHandler> groupHandlerMap = new ConcurrentHashMap<>();

    private @Nullable LeapDeviceDiscoveryService discoveryService;

    public void setDiscoveryService(LeapDeviceDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    public LeapBridgeHandler(Bridge bridge) {
        super(bridge);
        gson = new GsonBuilder()
                // .setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE)
                .create();
    }

    @Override
    public void initialize() {
        SSLContext sslContext;

        childHandlerMap.clear();
        groupHandlerMap.clear();

        config = getConfigAs(LeapBridgeConfig.class);
        String keystorePassword = (config.keystorePassword == null) ? "" : config.keystorePassword;

        String ipAddress = config.ipAddress;
        if (ipAddress == null || ipAddress.isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "bridge address not specified");
            return;
        }

        reconnectInterval = (config.reconnect > 0) ? config.reconnect : DEFAULT_RECONNECT_MINUTES;
        heartbeatInterval = (config.heartbeat > 0) ? config.heartbeat : DEFAULT_HEARTBEAT_MINUTES;
        sendDelay = (config.delay < 0) ? 0 : config.delay;

        try {
            logger.trace("Initializing keystore");
            KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());

            if (config.keystore != null && keystorePassword != null) {
                keystore.load(new FileInputStream(config.keystore), keystorePassword.toCharArray());
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "Keystore/keystore password not configured");
                return;
            }

            logger.trace("Initializing SSL Context");
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keystore, keystorePassword.toCharArray());

            TrustManager[] trustManagers;
            if (!config.trusting) {
                // Use default trust manager which will attempt to validate server certificate from hub
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(keystore);
                trustManagers = tmf.getTrustManagers();
            } else {
                // Use no-op trust manager which will not verify certificates
                trustManagers = defineNoOpTrustManager();
            }

            sslContext = SSLContext.getInstance("TLS");
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

        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NONE, "Connecting");
        scheduler.submit(this::connect); // start the async connect task
    }

    /**
     * Return a no-op SSL trust manager which will not verify server or client certificates.
     */
    private TrustManager[] defineNoOpTrustManager() {
        return new TrustManager[] { new X509TrustManager() {
            @Override
            public void checkClientTrusted(final X509Certificate @Nullable [] chain, final @Nullable String authType) {
                logger.debug("Assuming client certificate is valid");
                if (chain != null) {
                    for (int cert = 0; cert < chain.length; cert++) {
                        logger.trace("Subject DN: {}", chain[cert].getSubjectDN());
                        logger.trace("Issuer DN: {}", chain[cert].getIssuerDN());
                        logger.trace("Serial number {}:", chain[cert].getSerialNumber());
                    }
                }
            }

            @Override
            public void checkServerTrusted(final X509Certificate @Nullable [] chain, final @Nullable String authType) {
                logger.debug("Assuming server certificate is valid");
                if (chain != null) {
                    for (int cert = 0; cert < chain.length; cert++) {
                        logger.trace("Subject DN: {}", chain[cert].getSubjectDN());
                        logger.trace("Issuer DN: {}", chain[cert].getIssuerDN());
                        logger.trace("Serial number: {}", chain[cert].getSerialNumber());
                    }
                }
            }

            @Override
            public X509Certificate @Nullable [] getAcceptedIssuers() {
                return null;
            }
        } };
    }

    private synchronized void connect() {
        deviceDataLoaded.set(false);
        buttonDataLoaded.set(false);

        try {
            logger.debug("Opening SSL connection to {}:{}", config.ipAddress, config.port);
            SSLSocket sslsocket = (SSLSocket) sslsocketfactory.createSocket(config.ipAddress, config.port);
            sslsocket.startHandshake();
            writer = new BufferedWriter(new OutputStreamWriter(sslsocket.getOutputStream()));
            reader = new BufferedReader(new InputStreamReader(sslsocket.getInputStream()));
            this.sslsocket = sslsocket;
        } catch (UnknownHostException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Unknown host");
            return;
        } catch (IllegalArgumentException e) {
            // port out of valid range
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Invalid port number");
            return;
        } catch (IOException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Error opening SSL connection"); // TODO
                                                                                                                      // getMesssage()
            logger.info("Error opening SSL connection: {}", e.getMessage());
            disconnect();
            scheduleConnectRetry(reconnectInterval); // Possibly a temporary problem. Try again later.
            return;
        }

        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NONE, STATUS_INITIALIZING);

        Thread readerThread = new Thread(this::readerThreadJob, "Lutron reader");
        readerThread.setDaemon(true);
        readerThread.start();
        this.readerThread = readerThread;

        Thread senderThread = new Thread(this::senderThreadJob, "Lutron sender");
        senderThread.setDaemon(true);
        senderThread.start();
        this.senderThread = senderThread;

        sendCommand(new LeapCommand(Request.getDevices()));
        sendCommand(new LeapCommand(Request.getButtonGroups()));
        sendCommand(new LeapCommand(Request.getAreas()));
        sendCommand(new LeapCommand(Request.getOccupancyGroups()));
        sendCommand(new LeapCommand(Request.subscribeOccupancyGroupStatus()));

        logger.debug("Starting keepAlive job with interval {}", heartbeatInterval);
        keepAlive = scheduler.scheduleWithFixedDelay(this::sendKeepAlive, heartbeatInterval, heartbeatInterval,
                TimeUnit.MINUTES);
    }

    /**
     * Called by discovery service to request fresh discovery data
     */
    public void queryDiscoveryData() {
        sendCommand(new LeapCommand(Request.getDevices()));
        sendCommand(new LeapCommand(Request.getAreas()));
        sendCommand(new LeapCommand(Request.getOccupancyGroups()));
    }

    private void scheduleConnectRetry(long waitMinutes) {
        logger.debug("Scheduling connection retry in {} minutes", waitMinutes);
        connectRetryJob = scheduler.schedule(this::connect, waitMinutes, TimeUnit.MINUTES);
    }

    private synchronized void disconnect() {
        logger.debug("Disconnecting");

        Thread senderThread = this.senderThread;
        Thread readerThread = this.readerThread;

        if (connectRetryJob != null) {
            connectRetryJob.cancel(true);
        }
        if (keepAlive != null) {
            keepAlive.cancel(true);
        }

        // May be called from keepAliveReconnect thread, so call cancel with false
        reconnectTaskCancel(false);

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

        deviceDataLoaded.set(false);
        buttonDataLoaded.set(false);
    }

    private synchronized void reconnect() {
        logger.debug("Attempting to reconnect to the bridge");
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "reconnecting");
        disconnect();
        connect();
    }

    /**
     * Method executed by the message sender thread (senderThread)
     */
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

    /**
     * Method executed by the message reader thread (readerThread)
     */
    private void readerThreadJob() {
        logger.debug("Message reader thread started");
        String msg = null;
        try {
            BufferedReader reader = this.reader;
            while (!Thread.interrupted() && reader != null && (msg = reader.readLine()) != null) {
                handleMessage(msg);
            }
            if (msg == null) {
                logger.debug("End of input stream detected");
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

    /**
     * Method called by the message reader thread to handle all received LEAP messages.
     *
     * @param msg LEAP message
     */
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
            // CommuniqueType type = CommuniqueType.valueOf(communiqueType);
            logger.debug("Received CommuniqueType: {}", communiqueType);
            reconnectTaskCancel(true); // Got a message, so cancel reconnect task.

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

    /**
     * Method called by handleMessage() to handle all LEAP ExceptionResponse messages.
     *
     * @param message LEAP message
     */
    private void handleExceptionResponse(JsonObject message) {
        // TODO
    }

    /**
     * Method called by handleMessage() to handle all LEAP ReadResponse and SubscribeResponse messages.
     *
     * @param message LEAP message
     */
    private void handleReadResponseMessage(JsonObject message) {
        try {
            JsonObject header = message.get("Header").getAsJsonObject();
            Header headerObj = gson.fromJson(header, Header.class);

            // if 204/NoContent response received for buttongroup request, create empty button map
            if (Request.BUTTON_GROUP_URL.equals(headerObj.url)
                    && Header.STATUS_NO_CONTENT.equalsIgnoreCase(headerObj.statusCode)) {
                handleEmptyButtonGroupDefinition();
                return;
            }

            if (!header.has("MessageBodyType")) {
                logger.trace("No MessageBodyType in header");
                return;
            }
            String messageBodyType = header.get("MessageBodyType").getAsString();
            logger.trace("MessageBodyType: {}", messageBodyType);

            if (!message.has("Body")) {
                logger.debug("No Body found in message");
                return;
            }
            JsonObject body = message.get("Body").getAsJsonObject();

            switch (messageBodyType) {
                case "OnePingResponse":
                    parseOnePingResponse(body);
                    break;
                case "OneZoneStatus":
                    parseOneZoneStatus(body);
                    break;
                case "MultipleAreaDefinition":
                    parseMultipleAreaDefinition(body);
                    break;
                case "MultipleButtonGroupDefinition":
                    parseMultipleButtonGroupDefinition(body);
                    break;
                case "MultipleDeviceDefinition":
                    parseMultipleDeviceDefinition(body);
                    break;
                case "MultipleOccupancyGroupDefinition":
                    parseMultipleOccupancyGroupDefinition(body);
                    break;
                case "MultipleOccupancyGroupStatus":
                    parseMultipleOccupancyGroupStatus(body);
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

    private @Nullable <T extends AbstractMessageBody> T parseBodySingle(JsonObject messageBody, String memberName,
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

    private <T extends AbstractMessageBody> List<T> parseBodyMultiple(JsonObject messageBody, String memberName,
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

    private void parseOnePingResponse(JsonObject messageBody) {
        logger.debug("Ping response received");
    }

    /**
     * Parses a OneZoneStatus message body. Calls handleZoneUpdate() to dispatch zone updates.
     */
    private void parseOneZoneStatus(JsonObject messageBody) {
        ZoneStatus zoneStatus = parseBodySingle(messageBody, "ZoneStatus", ZoneStatus.class);
        if (zoneStatus != null) {
            handleZoneUpdate(zoneStatus);
        }
    }

    /**
     * Parses a MultipleAreaDefinition message body.
     */
    private void parseMultipleAreaDefinition(JsonObject messageBody) {
        logger.trace("Parsing area list");
        List<Area> areaList = parseBodyMultiple(messageBody, "Areas", Area.class);

        if (discoveryService != null) {
            discoveryService.setAreas(areaList);
        }
    }

    /**
     * Parses a MultipleOccupancyGroupDefinition message body.
     */
    private void parseMultipleOccupancyGroupDefinition(JsonObject messageBody) {
        logger.trace("Parsing occupancy group list");
        List<OccupancyGroup> oGroupList = parseBodyMultiple(messageBody, "OccupancyGroups", OccupancyGroup.class);

        if (discoveryService != null) {
            discoveryService.setOccupancyGroups(oGroupList);
        }
    }

    /**
     * Parses a MultipleOccupancyGroupStatus message body and updates occupancy status.
     */
    private void parseMultipleOccupancyGroupStatus(JsonObject messageBody) {
        logger.trace("Parsing occupancy group status list");
        List<OccupancyGroupStatus> statusList = parseBodyMultiple(messageBody, "OccupancyGroupStatuses",
                OccupancyGroupStatus.class);
        for (OccupancyGroupStatus status : statusList) {
            int groupNumber = status.getOccupancyGroup();
            if (groupNumber > 0) {
                logger.debug("OccupancyGroup: {} Status: {}", groupNumber, status.occupancyStatus);
                handleGroupUpdate(groupNumber, status.occupancyStatus);
            }
        }
    }

    /**
     * Parses a MultipleDeviceDefinition message body and loads the zoneToDevice and deviceToZone maps. Also passes the
     * device data on to the discovery service and calls setBridgeProperties() with the hub's device entry.
     */
    private void parseMultipleDeviceDefinition(JsonObject messageBody) {
        List<Device> deviceList = parseBodyMultiple(messageBody, "Devices", Device.class);

        synchronized (zoneMapsLock) {
            zoneToDevice.clear();
            deviceToZone.clear();
            for (Device device : deviceList) {
                Integer zoneid = device.getZone();
                Integer deviceid = device.getDevice();
                logger.trace("Found device: {} id: {} zone: {}", device.name, deviceid, zoneid);
                if (zoneid > 0 && deviceid > 0) {
                    zoneToDevice.put(zoneid, deviceid);
                    deviceToZone.put(deviceid, zoneid);
                }
                if (deviceid == 1) { // ID 1 is the bridge
                    setBridgeProperties(device);
                }
            }
        }
        deviceDataLoaded.set(true);
        checkInitialized();

        if (discoveryService != null) {
            discoveryService.processDeviceDefinitions(deviceList);
        }
    }

    /**
     * Parse a MultipleButtonGroupDefinition message body and load the results into deviceButtonMap.
     */
    private void parseMultipleButtonGroupDefinition(JsonObject messageBody) {
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
            buttonDataLoaded.set(true);
        }
        checkInitialized();
    }

    /**
     * Called if NoContent response received for a buttongroup read request. Creates empty deviceButtonMap.
     */
    private void handleEmptyButtonGroupDefinition() {
        logger.debug("No content in button group definition. Creating empty deviceButtonMap.");
        Map<Integer, List<Integer>> deviceButtonMap = new HashMap<>();
        synchronized (deviceButtonMapLock) {
            this.deviceButtonMap = deviceButtonMap;
            buttonDataLoaded.set(true);
        }
        checkInitialized();
    }

    /**
     * Set state to online if offline/initializing and all required initialization info is loaded.
     * Currently this means device (zone) and button group data.
     */
    private void checkInitialized() {
        ThingStatusInfo statusInfo = getThing().getStatusInfo();
        if (statusInfo.getStatus() == ThingStatus.OFFLINE && STATUS_INITIALIZING.equals(statusInfo.getDescription())) {
            if (deviceDataLoaded.get() && buttonDataLoaded.get()) {
                updateStatus(ThingStatus.ONLINE);
            }
        }
    }

    /**
     * Notify child thing handler of a zonelevel update from a received zone status message.
     */
    private void handleZoneUpdate(ZoneStatus zoneStatus) {
        logger.trace("Zone: {} level: {}", zoneStatus.getZone(), zoneStatus.level);
        Integer integrationId = zoneToDevice(zoneStatus.getZone());

        if (integrationId == null) {
            logger.debug("Unable to map zone {} to device", zoneStatus.getZone());
            return;
        }
        logger.trace("Zone {} mapped to device id {}", zoneStatus.getZone(), integrationId);

        // dispatch update to proper thing handler
        LutronHandler handler = findThingHandler(integrationId);
        if (handler != null) {
            if (zoneStatus.fanSpeed != null) {
                // handle fan controller
                FanSpeedType fanSpeed = zoneStatus.fanSpeed;
                try {
                    handler.handleUpdate(LutronCommandType.OUTPUT, OutputCommand.ACTION_ZONELEVEL.toString(),
                            new Integer(fanSpeed.speed()).toString());
                } catch (NumberFormatException e) {
                    logger.warn("Number format exception parsing update");
                } catch (RuntimeException e) {
                    logger.warn("Runtime exception while processing update");
                }
            } else {
                // handle switch/dimmer/shade
                try {
                    handler.handleUpdate(LutronCommandType.OUTPUT, OutputCommand.ACTION_ZONELEVEL.toString(),
                            new Integer(zoneStatus.level).toString());
                } catch (NumberFormatException e) {
                    logger.warn("Number format exception parsing update");
                } catch (RuntimeException e) {
                    logger.warn("Runtime exception while processing update");
                }
            }
        } else {
            logger.debug("No thing configured for integration ID {}", integrationId);
        }
    }

    /**
     * Notify child group handler of a received occupancy group update.
     *
     * @param occupancyStatus
     * @param groupNumber
     */
    private void handleGroupUpdate(int groupNumber, String occupancyStatus) {
        logger.trace("Group {} state update: {}", groupNumber, occupancyStatus);

        // dispatch update to proper handler
        OGroupHandler handler = findGroupHandler(groupNumber);
        if (handler != null) {
            try {
                switch (occupancyStatus) {
                    case "Occupied":
                        handler.handleUpdate(LutronCommandType.GROUP, GroupCommand.ACTION_GROUPSTATE.toString(),
                                GroupCommand.STATE_GRP_OCCUPIED.toString());
                        break;
                    case "Unoccupied":
                        handler.handleUpdate(LutronCommandType.GROUP, GroupCommand.ACTION_GROUPSTATE.toString(),
                                GroupCommand.STATE_GRP_UNOCCUPIED.toString());
                        break;
                    case "Unknown":
                        handler.handleUpdate(LutronCommandType.GROUP, GroupCommand.ACTION_GROUPSTATE.toString(),
                                GroupCommand.STATE_GRP_UNKNOWN.toString());
                        break;
                    default:
                        logger.debug("Unexpected occupancy status: {}", occupancyStatus);
                        return;
                }
            } catch (NumberFormatException e) {
                logger.warn("Number format exception parsing update");
            } catch (RuntimeException e) {
                logger.warn("Runtime exception while processing update");
            }
        } else {
            logger.debug("No group thing configured for group ID {}", groupNumber);
        }
    }

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
     * Queue a LeapCommand for transmission by the sender thread.
     */
    public void sendCommand(@Nullable LeapCommand command) {
        if (command != null) {
            sendQueue.add(command);
        }
    }

    /**
     * Convert a LutronCommand into a LeapCommand and queue it for transmission by the sender thread.
     */
    @Override
    public void sendCommand(LutronCommandNew command) {
        logger.trace("Received request to send Lutron command: {}", command);
        sendCommand(command.leapCommand(this, deviceToZone(command.getIntegrationId())));
    }

    /**
     * Returns LEAP button number for given integrationID and component. Returns 0 if button number cannot be
     * determined.
     */
    public int getButton(int integrationID, int component) {
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

    private @Nullable LutronHandler findThingHandler(@Nullable Integer integrationId) {
        if (integrationId != null) {
            return childHandlerMap.get(integrationId);
        } else {
            return null;
        }
    }

    private @Nullable OGroupHandler findGroupHandler(int integrationId) {
        return groupHandlerMap.get(integrationId);
    }

    private @Nullable Integer zoneToDevice(int zone) {
        synchronized (zoneMapsLock) {
            return zoneToDevice.get(zone);
        }
    }

    private @Nullable Integer deviceToZone(@Nullable Integer device) {
        if (device == null) {
            return null;
        }
        synchronized (zoneMapsLock) {
            return deviceToZone.get(device);
        }
    }

    private void sendKeepAlive() {
        logger.trace("Sending keepalive query");
        sendCommand(new LeapCommand(Request.ping()));
        // Reconnect if no response is received within KEEPALIVE_TIMEOUT_SECONDS.
        reconnectTaskSchedule();
    }

    private void reconnectTaskSchedule() {
        synchronized (keepAliveReconnectLock) {
            keepAliveReconnect = scheduler.schedule(this::reconnect, KEEPALIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private void reconnectTaskCancel(boolean interrupt) {
        synchronized (keepAliveReconnectLock) {
            ScheduledFuture<?> keepAliveReconnect = this.keepAliveReconnect;
            if (keepAliveReconnect != null) {
                logger.trace("Canceling scheduled reconnect job.");
                keepAliveReconnect.cancel(interrupt);
                this.keepAliveReconnect = null;
            }
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
    public void childHandlerInitialized(ThingHandler childHandler, Thing childThing) {
        if (childHandler instanceof OGroupHandler) {
            // We need a different map for group things because the numbering is separate
            OGroupHandler handler = (OGroupHandler) childHandler;
            int groupId = handler.getIntegrationId();
            groupHandlerMap.put(groupId, handler);
            logger.trace("Registered group handler for ID {}", groupId);
        } else {
            LutronHandler handler = (LutronHandler) childHandler;
            int intId = handler.getIntegrationId();
            childHandlerMap.put(intId, handler);
            logger.trace("Registered child handler for ID {}", intId);
        }
    }

    @Override
    public void childHandlerDisposed(ThingHandler childHandler, Thing childThing) {
        if (childHandler instanceof OGroupHandler) {
            OGroupHandler handler = (OGroupHandler) childHandler;
            int groupId = handler.getIntegrationId();
            groupHandlerMap.remove(groupId);
            logger.trace("Unregistered group handler for ID {}", groupId);
        } else {
            LutronHandler handler = (LutronHandler) childHandler;
            int intId = handler.getIntegrationId();
            childHandlerMap.remove(intId);
            logger.trace("Unregistered child handler for ID {}", intId);
        }
    }

    @Override
    public void dispose() {
        disconnect();
    }
}
