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
package org.tools4j.mmap.perf;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs a task in its own daemon thread and captures its error, used by {@link Sender} and {@link Receiver}.
 */
final class Worker {
    private final Thread thread;
    private final AtomicReference<Throwable> error = new AtomicReference<>();

    Worker(final String name, final ThreadFactory threadFactory, final Runnable task) {
        this.thread = threadFactory.newThread(task);
        thread.setName(name);
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((t, e) -> error.set(e));
    }

    String name() {
        return thread.getName();
    }

    void start() {
        thread.start();
    }

    /**
     * Waits for the worker to complete.
     *
     * @param deadlineNanos the {@link System#nanoTime()} until when to wait
     * @return true if the worker completed, false if it is still running
     * @throws Throwable the error thrown by the worker if it failed, or if waiting was interrupted
     */
    boolean join(final long deadlineNanos) throws Throwable {
        final long millis = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
        thread.join(Math.max(1, millis));
        final Throwable t = error.get();
        if (t != null) {
            throw t;
        }
        return !thread.isAlive();
    }
}
