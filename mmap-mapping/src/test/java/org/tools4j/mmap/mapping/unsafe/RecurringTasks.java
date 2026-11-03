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
package org.tools4j.mmap.mapping.unsafe;

import org.tools4j.mmap.mapping.api.AsyncRuntime.Recurring;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Recurring tasks of tests with a mocked async runtime, executed by the test thread.
 */
enum RecurringTasks {
    ;
    private static final long MAX_WAIT_MILLIS = 10_000;

    /**
     * Closes on another thread while executing the recurring task on this thread, as closing waits for the task to
     * acknowledge the stop, which it only does when executed.
     *
     * @param close the close action, for instance of a region mapper
     * @param task  the recurring task registered with the mocked async runtime
     */
    static void closeWhileExecuting(final Runnable close, final Recurring task) throws InterruptedException {
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final Thread closer = new Thread(() -> {
            try {
                close.run();
            } catch (final Throwable t) {
                error.set(t);
            }
        }, "closer");
        closer.start();
        final long deadline = System.currentTimeMillis() + MAX_WAIT_MILLIS;
        while (closer.isAlive() && System.currentTimeMillis() < deadline) {
            task.execute();
        }
        closer.join(MAX_WAIT_MILLIS);
        assertFalse(closer.isAlive(), "closer still running");
        assertNull(error.get());
    }
}
