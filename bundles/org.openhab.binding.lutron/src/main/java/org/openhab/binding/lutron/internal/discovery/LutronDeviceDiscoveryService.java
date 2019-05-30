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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.lutron.internal.IPBridgeType;
import org.openhab.binding.lutron.internal.LutronHandlerFactory;
import org.openhab.binding.lutron.internal.discovery.project.Area;
import org.openhab.binding.lutron.internal.discovery.project.Device;
import org.openhab.binding.lutron.internal.discovery.project.DeviceGroup;
import org.openhab.binding.lutron.internal.discovery.project.DeviceNode;
import org.openhab.binding.lutron.internal.discovery.project.DeviceType;
import org.openhab.binding.lutron.internal.discovery.project.GreenMode;
import org.openhab.binding.lutron.internal.discovery.project.Output;
import org.openhab.binding.lutron.internal.discovery.project.OutputType;
import org.openhab.binding.lutron.internal.discovery.project.Project;
import org.openhab.binding.lutron.internal.discovery.project.Timeclock;
import org.openhab.binding.lutron.internal.handler.IPBridgeHandler;
import org.openhab.binding.lutron.internal.xml.DbXmlInfoReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link LutronDeviceDiscoveryService} finds all devices paired with a Lutron bridge.
 *
 * @author Allan Tong - Initial contribution
 * @author Bob Adair - Added support for phase-selectable dimmers, Pico, tabletop keypads, switch modules, VCRX,
 *         repeater virtual buttons, Timeclock, and Green Mode
 */
public class LutronDeviceDiscoveryService extends AbstractDiscoveryService {

    private static final String XML_DECLARATION_START = "<?xml";
    private static final int DECLARATION_MAX_LEN = 80;

    private final Logger logger = LoggerFactory.getLogger(LutronDeviceDiscoveryService.class);

    private IPBridgeHandler bridgeHandler;
    private DbXmlInfoReader dbXmlInfoReader = new DbXmlInfoReader();
    
    private HttpClient httpClient;

    private ScheduledFuture<?> scanTask;

    public LutronDeviceDiscoveryService(IPBridgeHandler bridgeHandler, HttpClient httpClient) throws IllegalArgumentException {
        super(LutronHandlerFactory.DISCOVERABLE_DEVICE_TYPES_UIDS, 10);

        this.bridgeHandler = bridgeHandler;
        this.httpClient = httpClient;
    }

    @Override
    protected synchronized void startScan() {
        if (bridgeHandler.getBridgeType() == IPBridgeType.RA2 || bridgeHandler.getBridgeType() == IPBridgeType.HWQS) {
            if (scanTask == null || this.scanTask.isDone()) {
                scanTask = scheduler.schedule(this::asyncDiscoveryTask, 0, TimeUnit.SECONDS);
            }
        }
    }

    private synchronized void asyncDiscoveryTask () {
        try {
            readDeviceDatabase();
        } catch (Exception e) {
            logger.warn("Error scanning for devices: {}", e.getMessage());

            if (scanListener != null) {
                scanListener.onErrorOccurred(e); // TODO: Change to .onFinished()
            }
        }
    }
    
    private void readDeviceDatabase() throws IOException {
        Project project = null;
        String discFileName = bridgeHandler.getIPBridgeConfig().getDiscoveryFile();
        String address = "http://" + bridgeHandler.getIPBridgeConfig().getIpAddress() + "/DbXmlInfo.xml";
        ContentResponse response = null;
        
        if (discFileName == null || discFileName.isEmpty()) {
            logger.trace("Sending http request");
            try {
                response = httpClient.newRequest(address)
                        .method(HttpMethod.GET)
                        .timeout(30, TimeUnit.SECONDS)
                        .header(HttpHeader.ACCEPT, "text/html")
                        .header(HttpHeader.ACCEPT_CHARSET, "utf-8")
                        .header(HttpHeader.ACCEPT_ENCODING, "identity")
                        .send();  
            } catch (InterruptedException | TimeoutException | ExecutionException e) {
                logger.info("Exception while making HTTP request: {}",e.getMessage());
            }
            //TODO - Change to async response processing using stream

            String xmlDocString = response.getContentAsString();
            StringReader xmlStringReader = new StringReader(xmlDocString);
            BufferedReader xmlBufReader = new BufferedReader(xmlStringReader);
            flushPrePrologLines(xmlBufReader);
            //TODO - Clean up resources and do exception handling for all of these
            
            project = dbXmlInfoReader.readFromXML(xmlBufReader);
            if (project == null) {
                logger.info("Could not process XML project file from {}", address);
            }
        } else {
            File xmlFile = new File(discFileName);
            
            //Path filePath = xmlFile.toPath();
            //BufferedReader xmlReader = Files.newBufferedReader(xmlFile.toPath(), StandardCharsets.UTF_8);
                        
            try (BufferedReader xmlReader = Files.newBufferedReader(xmlFile.toPath(), StandardCharsets.UTF_8)) {
                
                flushPrePrologLines(xmlReader);
                
                project = dbXmlInfoReader.readFromXML(xmlReader);
                if (project == null) {
                    logger.info("Could not process XML project file {}", discFileName);
                }
            } catch (IOException e) {
                logger.debug("IOException: ", e);
            }
        }

        if (project != null) {
            Stack<String> locationContext = new Stack<>();

            for (Area area : project.getAreas()) {
                processArea(area, locationContext);
            }
            for (Timeclock timeclock : project.getTimeclocks()) {
                processTimeclocks(timeclock, locationContext);
            }
            for (GreenMode greenMode : project.getGreenModes()) {
                processGreenModes(greenMode, locationContext);
            }
        }
    }

    private void flushPrePrologLines(BufferedReader xmlReader) throws IOException {
        String inLine = null;
        xmlReader.mark(DECLARATION_MAX_LEN);
        Boolean foundXmlDec = false;

        while (!foundXmlDec && (inLine = xmlReader.readLine()) != null) {
            if (inLine.startsWith(XML_DECLARATION_START)) {
                foundXmlDec = true;
                xmlReader.reset();
            } else {
                logger.trace("discarding line: {}", inLine);
                xmlReader.mark(DECLARATION_MAX_LEN);
            }
        }
    }
    
    private void processArea(Area area, Stack<String> context) {
        context.push(area.getName());

        for (DeviceNode deviceNode : area.getDeviceNodes()) {
            if (deviceNode instanceof DeviceGroup) {
                processDeviceGroup((DeviceGroup) deviceNode, context);
            } else if (deviceNode instanceof Device) {
                processDevice((Device) deviceNode, context);
            }
        }

        for (Output output : area.getOutputs()) {
            processOutput(output, context);
        }

        for (Area subarea : area.getAreas()) {
            processArea(subarea, context);
        }

        context.pop();
    }

    private void processDeviceGroup(DeviceGroup deviceGroup, Stack<String> context) {
        context.push(deviceGroup.getName());

        for (Device device : deviceGroup.getDevices()) {
            processDevice(device, context);
        }

        context.pop();
    }

    private void processDevice(Device device, Stack<String> context) {
        DeviceType type = device.getDeviceType();

        if (type != null) {
            String label = generateLabel(context, device.getName());

            switch (type) {
                case MOTION_SENSOR:
                    notifyDiscovery(THING_TYPE_OCCUPANCYSENSOR, device.getIntegrationId(), label);
                    break;

                case SEETOUCH_KEYPAD:
                case HYBRID_SEETOUCH_KEYPAD:
                    notifyDiscovery(THING_TYPE_KEYPAD, device.getIntegrationId(), label);
                    break;

                case INTERNATIONAL_SEETOUCH_KEYPAD:
                    notifyDiscovery(THING_TYPE_QSIKEYPAD, device.getIntegrationId(), label);
                    break;
                
                case SEETOUCH_TABLETOP_KEYPAD:
                    notifyDiscovery(THING_TYPE_TTKEYPAD, device.getIntegrationId(), label);
                    break;

                case PICO_KEYPAD:
                    notifyDiscovery(THING_TYPE_PICO, device.getIntegrationId(), label);
                    break;
                    
                case VISOR_CONTROL_RECEIVER:
                    notifyDiscovery(THING_TYPE_VCRX, device.getIntegrationId(), label);
                    break;

                case MAIN_REPEATER:
                    notifyDiscovery(THING_TYPE_VIRTUALKEYPAD, device.getIntegrationId(), label);
                    break;
            }
        } else {
            logger.warn("Unrecognized device type {}", device.getType());
        }
    }

    private void processOutput(Output output, Stack<String> context) {
        OutputType type = output.getOutputType();

        if (type != null) {
            String label = generateLabel(context, output.getName());

            switch (type) {
                case INC:
                case MLV:
                case ELV:
                case ECO_SYSTEM_FLUORESCENT:
                case FLUORESCENT_DB:
                case ZERO_TO_TEN:
                case AUTO_DETECT:
                case CEILING_FAN_TYPE:
                    notifyDiscovery(THING_TYPE_DIMMER, output.getIntegrationId(), label);
                    break;

                case NON_DIM:
                case NON_DIM_INC:
                case NON_DIM_ELV:
                    notifyDiscovery(THING_TYPE_SWITCH, output.getIntegrationId(), label);
                    break;

                case CCO_PULSED:
                    notifyDiscovery(THING_TYPE_CCO_PULSED, output.getIntegrationId(), label);
                    break;

                case CCO_MAINTAINED:
                    notifyDiscovery(THING_TYPE_CCO_MAINTAINED, output.getIntegrationId(), label);
                    break;

                case SYSTEM_SHADE:
                    notifyDiscovery(THING_TYPE_SHADE, output.getIntegrationId(), label);
                    break;
            }
        } else {
            logger.warn("Unrecognized output type {}", output.getType());
        }
    }

    private void processTimeclocks(Timeclock timeclock, Stack<String> context) {
        String label = generateLabel(context, timeclock.getName());
        notifyDiscovery(THING_TYPE_TIMECLOCK, timeclock.getIntegrationId(), label);
    }

    private void processGreenModes(GreenMode greenmode, Stack<String> context) {
        String label = generateLabel(context, greenmode.getName());
        notifyDiscovery(THING_TYPE_GREENMODE, greenmode.getIntegrationId(), label);
    }

    private void notifyDiscovery(ThingTypeUID thingTypeUID, Integer integrationId, String label) {
        if (integrationId == null) {
            logger.info("Discovered {} with no integration ID", label);

            return;
        }

        ThingUID bridgeUID = this.bridgeHandler.getThing().getUID();
        ThingUID uid = new ThingUID(thingTypeUID, bridgeUID, integrationId.toString());

        Map<String, Object> properties = new HashMap<>();

        properties.put(INTEGRATION_ID, integrationId);

        DiscoveryResult result = DiscoveryResultBuilder.create(uid).withBridge(bridgeUID).withLabel(label)
                .withProperties(properties).withRepresentationProperty(INTEGRATION_ID).build();

        thingDiscovered(result);

        logger.debug("Discovered {}", uid);
    }

    private String generateLabel(Stack<String> context, String deviceName) {
        return String.join(" ", context) + " " + deviceName;
    }
}
