/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2018 mmap (tools4j), Marco Terzer, Anton Anufriev
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
package org.tools4j.process;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

public class Process implements Service {

    private final ProcessLoop processLoop;
    private final String name;
    private final Thread thread;
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private final long gracefulShutdownTimeout;
    private final TimeUnit gracefulShutdownTimeunit;
    private final AtomicLong gracefulShutdownMaxTime = new AtomicLong();


    public Process(final String name,
                   final Runnable onStartHandler,
                   final Runnable onStopHandler,
                   final IdleStrategy idleStrategy,
                   final BiConsumer<? super String, ? super Exception> exceptionHandler,
                   final long gracefulShutdownTimeout,
                   final TimeUnit gracefulShutdownTimeunit,
                   final boolean daemonThread,
                   final BooleanSupplier shutdownCondition,
                   final ProcessStep... steps) {
        this.gracefulShutdownTimeunit = Objects.requireNonNull(gracefulShutdownTimeunit);
        this.gracefulShutdownTimeout = gracefulShutdownTimeout;

        this.processLoop = new ProcessLoop(name,
                onStartHandler,
                onStopHandler,
                () -> shutdownCondition.getAsBoolean() || stopping.get(),
                () -> System.currentTimeMillis() > gracefulShutdownMaxTime.get(),
                idleStrategy,
                exceptionHandler,
                steps);
        this.thread = new Thread(processLoop, name);
        this.thread.setDaemon(daemonThread);
        this.name = name;
    }

    @Override
    public Service.Stoppable start() {
        thread.start();

        return new Service.Stoppable() {
            @Override
            public void stop() {
                final long gracefulShutdownTimeoutMillis = gracefulShutdownTimeunit.toMillis(gracefulShutdownTimeout);
                gracefulShutdownMaxTime.set(System.currentTimeMillis() + gracefulShutdownTimeoutMillis);
                stopping.set(true);
                try {
                    thread.join(gracefulShutdownTimeoutMillis);
                    thread.interrupt();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for service thread " + name + " to stop", e);
                }
            }

            @Override
            public void awaitShutdown() {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for service thread " + name + " to shutdown", e);
                }
            }
        };
    }
}
