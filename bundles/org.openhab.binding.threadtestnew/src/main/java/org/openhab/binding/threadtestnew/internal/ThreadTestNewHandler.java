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
package org.openhab.binding.threadtestnew.internal;

import static org.openhab.binding.threadtestnew.internal.ThreadTestNewBindingConstants.CHANNEL_POOLSTATS;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ThreadTestNewHandler} is for researching scheduler thread pool issues
 *
 * @author Bob Adair - Initial contribution
 */
@NonNullByDefault
public class ThreadTestNewHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(ThreadTestNewHandler.class);

    private @Nullable ThreadTestNewConfiguration config;
    private @Nullable ScheduledFuture<?> periodicJob;
    private @Nullable ScheduledFuture<?> longRunningJob;
    private long periodicJobInterval = 30;

    public ThreadTestNewHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        config = getConfigAs(ThreadTestNewConfiguration.class);
        if (config != null) {
            periodicJobInterval = config.interval;
        }
        updateStatus(ThingStatus.UNKNOWN);

        longRunningJob = scheduler.schedule(this::longRunningTask, 0, TimeUnit.SECONDS);

        logger.info("Scheduling periodic job with interval {}", periodicJobInterval);
        periodicJob = scheduler.scheduleWithFixedDelay(this::periodicTask, periodicJobInterval, periodicJobInterval,
                TimeUnit.SECONDS);
        logSchedulerStats();

        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (CHANNEL_POOLSTATS.equals(channelUID.getId())) {
            if (command instanceof StringType) {
                logSchedulerStats();
            }
        }
    }

    private void longRunningTask() {
        // normally this would do something useful, but here it just sleeps
        try {
            while (true) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            logger.info("Long running task interrupted");
            Thread.currentThread().interrupt();
        }
    }

    private void periodicTask() {
        logger.info("Periodic task executing");
    }

    private void logSchedulerStats() {
        logger.info("Periodic job status: delay {} done {} cancelled {}", periodicJob.getDelay(TimeUnit.SECONDS),
                periodicJob.isDone(), periodicJob.isCancelled());
        logger.info("Scheduler status: {} shut: {} term: {}", scheduler.toString(), scheduler.isShutdown(),
                scheduler.isTerminated());
    }

    @Override
    public void dispose() {
        logger.info("Stopping scheduled jobs");
        if (periodicJob != null) {
            periodicJob.cancel(true);
        }
        if (longRunningJob != null) {
            longRunningJob.cancel(true);
        }
    }
}
