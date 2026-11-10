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
package org.tools4j.mmap.core.base;

/**
 * Listener for lifecycle events of resources such as files, file mappers, ID pools, async runtimes, queues, appenders
 * and readers. Listeners can identify the resource type with {@code instanceof}, for instance to track file rolls with
 * {@code resource instanceof FileMapper}.
 * <p>
 * Listener methods are invoked by the thread causing the event, possibly from multiple threads concurrently, and
 * must not throw exceptions.
 */
public interface LifecycleListener {
    /** Listener ignoring all events */
    LifecycleListener NO_OP = new LifecycleListener() {
        @Override
        public String toString() {
            return "LifecycleListener.NO_OP";
        }
    };

    /**
     * Invoked when a resource such as a file has been created.
     *
     * @param resource the created resource
     */
    default void onCreated(final Describable resource) {}

    /**
     * Invoked when a resource has been opened or started.
     *
     * @param resource the opened resource
     */
    default void onOpened(final Describable resource) {}

    /**
     * Invoked when a resource has been closed or stopped.
     *
     * @param resource the closed resource
     */
    default void onClosed(final Describable resource) {}
}
