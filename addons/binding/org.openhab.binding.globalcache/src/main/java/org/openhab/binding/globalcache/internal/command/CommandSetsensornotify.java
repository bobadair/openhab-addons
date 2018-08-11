/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.globalcache.internal.command;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.smarthome.core.thing.Thing;
import org.openhab.binding.globalcache.GlobalCacheBindingConstants.CommandType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link CommandSetsensornotify} class implements the GlobalCache set_SENSORNOTIFY command for iTach Flex
 * Command format: set_SENSORNOTIFY,<module>:<port>,<notify_port>,<notify_interval>
 *
 * @author Bob Adair - Initial contribution
 *         based on Mark Hilbush's CommandSetstate
 */
public class CommandSetsensornotify extends AbstractCommand {

    private final Logger logger = LoggerFactory.getLogger(CommandSetsensornotify.class);

    public CommandSetsensornotify(Thing thing, LinkedBlockingQueue<RequestMessage> queue, String mod, String con,
            String notifyPort, String notifyInterval) {

        super(thing, queue, "set_SENSORNOTIFY", CommandType.COMMAND);
        deviceCommand = "set_SENSORNOTIFY," + mod + ":" + con + "," + notifyPort + "," + notifyInterval;
    }

    @Override
    public void parseSuccessfulReply() {
        if (deviceReply == null) {
            return;
        }
        if (!matchReply(deviceReply)) {
            logger.warn("Successful reply from device can't be matched: {}", deviceReply);
            return;
        }
    }

    private boolean matchReply(String reply) {
        // Match on response of form: SENSORNOTIFY,<module>:<port>,<notify_port>,<notify_interval>
        Pattern p = Pattern.compile("(SENSORNOTIFY),(\\d):(\\d),(\\d+),(\\d+)");
        Matcher m = p.matcher(reply);
        if (m.matches()) {
            logger.trace("Matched SENSORNOTIFY response: g2={}, g3={}, g4={}, g5={}", m.group(2), m.group(3),
                    m.group(4), m.group(5));
            if (m.groupCount() == 5) {
                setModule(m.group(2));
                setConnector(m.group(3));
                return true;
            }
        }
        return false;
    }

    @Override
    public void logSuccess() {
        logger.debug("Execute '{}' succeeded on thing {} at {}", commandName, thing.getUID().getId(), ipAddress);
    }

    @Override
    public void logFailure() {
        logger.error("Execute '{}' failed on thing {} at {}: errorCode={}, errorMessage={}", commandName,
                thing.getUID().getId(), ipAddress, errorCode, errorMessage);
    }
}
