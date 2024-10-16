/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2024 tools4j.org (Marco Terzer, Anton Anufriev)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.tools4j.mmap.region.impl;

import org.agrona.concurrent.IdleStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.region.api.AsyncRuntime;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;


public class DefaultAsyncRuntime implements AsyncRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAsyncRuntime.class);
    private static final Recurring[] EMPTY = {};

    private final String name;
    private final boolean autoStopOnLastDeregister;
    private final AtomicReference<Recurring[]> executables = new AtomicReference<>(EMPTY);

    private enum StopStatus {
        WHEN_IDLE,
        IMMEDIATELY,
        STOPPED
    }
    private final AtomicReference<StopStatus> stop = new AtomicReference<>(null);

    public DefaultAsyncRuntime(final IdleStrategy idleStrategy, final boolean autoStopOnLastDeregister) {
        this.name = "async-" + idleStrategy.alias();
        this.autoStopOnLastDeregister = autoStopOnLastDeregister;
        requireNonNull(idleStrategy);
        final Thread thread = new Thread(() -> {
            LOGGER.info("Started async region mapping runtime");
            idleStrategy.reset();
            StopStatus stopSignal;
            int workCount = 0;
            while ((stopSignal = stop.get()) == null || (stopSignal == StopStatus.WHEN_IDLE && workCount != 0)) {
                workCount = 0;
                for (final Recurring executable : executables.get()) {
                    try {
                        workCount += executable.execute();
                    } catch (final Exception ex) {
                        LOGGER.error("Uncaught error: {}", ex, ex);
                    }
                }
                idleStrategy.idle(workCount);
            }
            stop.set(StopStatus.STOPPED);
            LOGGER.info("Stopped async region mapping runtime");
        });
        thread.setName(name);
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((t, e) -> LOGGER.error("Async runtime failed with exception", e));
        thread.start();
    }

    @Override
    public void register(final Recurring recurring) {
        requireNonNull(recurring);
        Recurring[] current, modified;
        do {
            current = executables.get();
            final int length = current.length;
            modified = Arrays.copyOf(current,  length + 1);
            modified[length] = recurring;
        } while (!executables.compareAndSet(current, modified));
    }

    @Override
    public void deregister(final Recurring recurring) {
        Recurring[] current, modified;
        do {
            current = executables.get();
            final int index = indexOf(current, recurring);
            if (index < 0) {
                return;
            }
            final int length = current.length - 1;
            modified = Arrays.copyOf(current, length);
            if (index < length) {
                modified[index] = current[length];
            }
        } while (!executables.compareAndSet(current, modified));
        //NOTE: concurrent new registration is possible here, but we simply ignore it
        //       as we would also ignore new registrations after stopping
        if (autoStopOnLastDeregister && modified.length == 0) {
            stop(true);
        }
    }

    private static int indexOf(final Recurring[] array, final Recurring find) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == find) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void stop(final boolean immediately) {
        if (immediately) {
            StopStatus current;
            while ((current = stop.get()) != StopStatus.IMMEDIATELY && current != StopStatus.STOPPED) {
                stop.compareAndSet(current, StopStatus.IMMEDIATELY);
            }
        } else {
            stop.compareAndSet(null, StopStatus.WHEN_IDLE);
        }
    }

    @Override
    public boolean isRunning() {
        return stop.get() != StopStatus.STOPPED;
    }

    @Override
    public String toString() {
        final StopStatus stopStatus = stop.get();
        return "DefaultAsyncRuntime" +
                ":thread=" + name +
                "|status=" + (stopStatus == null ? "running" : stopStatus == StopStatus.STOPPED ? "stopped" : "stopping");
    }
}
