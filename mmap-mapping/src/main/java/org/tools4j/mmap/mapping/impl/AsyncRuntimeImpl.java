/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2026 tools4j.org (Marco Terzer, Anton Anufriev)
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
package org.tools4j.mmap.mapping.impl;

import org.tools4j.mmap.core.base.Describable;
import org.tools4j.mmap.core.base.ErrorListener;
import org.tools4j.mmap.core.base.LifecycleListener;
import org.tools4j.mmap.core.idle.Idler;
import org.tools4j.mmap.mapping.api.AsyncRuntime;
import org.tools4j.mmap.mapping.config.MappingConfigurations;

import java.util.Arrays;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;


public class AsyncRuntimeImpl implements AsyncRuntime, Describable {
    private static final Recurring[] EMPTY = {};
    public static final ThreadFactory DEFAULT_THREAD_FACTORY = Thread::new;

    private final String name;
    private final String idlerName;
    private final boolean autoStopOnLastDeregister;
    private final ThreadFactory threadFactory;
    private final AtomicReference<Recurring[]> executables = new AtomicReference<>(EMPTY);

    private enum StopStatus {
        WHEN_IDLE,
        IMMEDIATELY,
        STOPPED
    }
    private final AtomicReference<StopStatus> stop = new AtomicReference<>(null);

    public AsyncRuntimeImpl(final String name,
                            final Idler idler,
                            final boolean autoStopOnLastDeregister) {
        this(name, idler, autoStopOnLastDeregister, MappingConfigurations.defaultLifecycleListener(),
                MappingConfigurations.defaultErrorListener());
    }

    public AsyncRuntimeImpl(final String name,
                            final Idler idler,
                            final boolean autoStopOnLastDeregister,
                            final LifecycleListener lifecycleListener,
                            final ErrorListener errorListener) {
        this(name, idler, autoStopOnLastDeregister, DEFAULT_THREAD_FACTORY, lifecycleListener, errorListener);
    }

    public AsyncRuntimeImpl(final String name,
                            final Idler idler,
                            final boolean autoStopOnLastDeregister,
                            final ThreadFactory threadFactory,
                            final LifecycleListener lifecycleListener,
                            final ErrorListener errorListener) {
        this.name = requireNonNull(name);
        this.threadFactory = requireNonNull(threadFactory);
        this.idlerName = idler.toString();
        this.autoStopOnLastDeregister = autoStopOnLastDeregister;
        requireNonNull(lifecycleListener);
        requireNonNull(errorListener);
        final Thread thread = threadFactory.newThread(() -> {
            lifecycleListener.onOpened(this);
            idler.reset();
            StopStatus stopSignal;
            int workCount = 0;
            while ((stopSignal = stop.get()) == null || (stopSignal == StopStatus.WHEN_IDLE && workCount != 0)) {
                workCount = 0;
                for (final Recurring executable : executables.get()) {
                    try {
                        workCount += executable.execute();
                    } catch (final Exception ex) {
                        errorListener.onError(this, "Uncaught error", ex);
                    }
                }
                if (workCount > 0) {
                    idler.reset();
                } else {
                    idler.idle();
                }
            }
            stop.set(StopStatus.STOPPED);
            lifecycleListener.onClosed(this);
        });
        thread.setName(name);
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((t, e) -> errorListener.onError(this, "Async runtime failed", e));
        thread.start();
    }

    @Override
    public void register(final Recurring recurring) {
        requireNonNull(recurring);
        if (stop.get() != null) {
            throw new IllegalArgumentException("Async runtime has already been stopped or is in the process of stopping");
        }
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
    public StringBuilder describeTo(final StringBuilder sb) {
        final StopStatus stopStatus = stop.get();
        sb.append("AsyncRuntimeImpl")
                .append(":thread=").append(name)
                .append("|idler=").append(idlerName);
        if (threadFactory != DEFAULT_THREAD_FACTORY) {
            sb.append("|threadFactory=").append(threadFactory);
        }
        return sb.append("|status=").append(stopStatus == null ? "running" :
                        stopStatus == StopStatus.STOPPED ? "stopped" : "stopping");
    }

    @Override
    public String toString() {
        return Describable.describe(this);
    }
}
