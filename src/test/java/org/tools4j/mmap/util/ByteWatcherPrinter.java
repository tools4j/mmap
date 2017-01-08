/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2017 mmap (tools4j), Marco Terzer
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
package org.tools4j.mmap.util;

import org.octtech.bw.ByteWatcher;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/**
 * Prints byte watch violations detected by {@link ByteWatcher} to console.
 */
public class ByteWatcherPrinter implements BiConsumer<Thread, Long> {

    private final long limit;
    private final Map<Thread, AtomicLong> lastSizePerThread = new ConcurrentHashMap<>();

    private ByteWatcherPrinter(final long limit) {
        this.limit = limit;
    }

    public static ByteWatcher watch() {
        return watchForLimit(0);
    }

    public static ByteWatcher watchForLimit(final long limit) {
        final ByteWatcher byteWatcher = new ByteWatcher();
        final ByteWatcherPrinter printer = new ByteWatcherPrinter(limit);
        byteWatcher.onByteWatch(printer, limit);
        return byteWatcher;
    }

    @Override
    public void accept(final Thread thread, final Long size) {
        final AtomicLong last = lastSizePerThread.computeIfAbsent(thread, k -> new AtomicLong());
        final long lastSize = last.getAndSet(size);
        if (lastSize < size) {
            System.out.printf("%s exceeded limit: %d using: %d allocated: %d\n", thread.getName(), limit, size, size - lastSize);
        }
    }
}
