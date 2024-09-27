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
package org.tools4j.mmap.region.api;

import org.agrona.concurrent.IdleStrategy;
import org.tools4j.mmap.region.impl.DefaultAsyncRuntime;

/**
 * Async runtime to perform recurring operations in the background, such as async mapping and unmapping operations.
 */
public interface AsyncRuntime extends AutoCloseable {
    /**
     * A recurring executable invoked repeatedly until it is removed from the runtime.
     */
    interface Recurring {
        /**
         * Executes an async operation and returns the work count executed.
         *
         * @return work count, zero if it was a no-op and a positive value otherwise
         */
        int execute();
    }

    /**
     * Register a recurring executable for continuous execution.
     *
     * @param recurring a recurring executable to be invoked until deregistered
     */
    void register(Recurring recurring);

    /**
     * De-registers the given recurring executable.
     *
     * @param recurring the recurring executable to deregister
     */
    void deregister(Recurring recurring);

    /**
     * Creates an async runtime backed by a thread watching to execute registered mapping tasks.
     *
     * @param idleStrategy the strategy to use by the runtime when it is idle
     * @return a new async runtime for the given idle strategy
     */
    static AsyncRuntime create(final IdleStrategy idleStrategy, final boolean autoStopOnLastDeregister) {
        return new DefaultAsyncRuntime(idleStrategy, autoStopOnLastDeregister);
    }

    /**
     * Closes the runtime, with or without first finishing jobs.
     * @param immediately stop occurs immediately if true, or when no more jobs are found to be executed otherwise
     */
    void stop(boolean immediately);

    /**
     * Returns true if this runtime is currently running.
     * @return true if running, and false if it has stopped after invoking {@link #stop(boolean)}
     */
    boolean isRunning();

    /**
     * Closes the runtime, stopping immediately without finishing jobs
     * @see #stop(boolean)
     */
    @Override
    default void close() {
        stop(true);
    }
}
