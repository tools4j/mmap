/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2025 tools4j.org (Marco Terzer, Anton Anufriev)
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
package org.tools4j.mmap.queue.impl;

import org.agrona.concurrent.AtomicBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.region.api.AccessMode;
import org.tools4j.mmap.region.api.Mapping;
import org.tools4j.mmap.region.api.Mappings;

import java.io.File;

import static java.util.Objects.requireNonNull;

/**
 * A pool of 256 appender ids based on a memory-mapped file associated with a given queue name.
 * It guarantees that there are no two or more processes/threads acquiring the same id for given queue name.
 * <p>
 * The mapped file is used as a bit set with each bit representing an appender ID. The file content is updated
 * atomically in a thread (and process) safe manner when {@link #acquire() acquiring} or
 * {@link #release(int) releasing} appender IDs.
 */
public class AppenderIdPool256 implements AppenderIdPool {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppenderIdPool256.class);

    public static final int MAX_APPENDERS = 1 << Byte.SIZE;
    public static final int FILE_SIZE = MAX_APPENDERS / Byte.SIZE;
    private final String name;
    private final Mapping mapping;

    public AppenderIdPool256(final File file) {
        this(file.getPath(), Mappings.fixedSizeMapping(file, FILE_SIZE, AccessMode.READ_WRITE));
    }

    public AppenderIdPool256(final String name, final Mapping mapping) {
        if (mapping.bytesAvailable() != FILE_SIZE) {
            throw new IllegalArgumentException("Invalid mapping, expected " + FILE_SIZE + " bytes available but found "
                    + mapping.bytesAvailable() + " for appender ID pool: " + name);
        }
        this.name = requireNonNull(name);
        this.mapping = requireNonNull(mapping);
        LOGGER.info("Opened appender ID pool: {} ({} open appenders)", name, openAppenders());
    }

    private void ensureNotClosed() {
        if (mapping.isClosed()) {
            throw new IllegalStateException("Appender ID pool is closed: " + name);
        }
    }

    @Override
    public int acquire() {
        ensureNotClosed();
        final AtomicBuffer buf = mapping.buffer();

        for (int index = 0; index < FILE_SIZE; index += Long.BYTES) {
            long appenderBitSet;
            long appenderBit;
            do {
                appenderBitSet = buf.getLongVolatile(index);
                appenderBit = Long.lowestOneBit(~appenderBitSet);
            } while (appenderBit != 0 && !buf.compareAndSetLong(index, appenderBitSet, appenderBitSet | appenderBit));
            if (appenderBit != 0) {
                final int appenderBitValue = Long.SIZE - Long.numberOfLeadingZeros(appenderBit - 1);
                final int appenderId = index / Long.BYTES * Long.SIZE + appenderBitValue;
                LOGGER.info("Acquired appenderId {} from {}", appenderId, name);
                return appenderId;
            }
        }
        throw new IllegalStateException("Exceeded max number of " + MAX_APPENDERS + " appenders in appender ID pool: "
                + name);
    }

    @Override
    public boolean release(final int appenderId) {
        ensureNotClosed();
        if (appenderId < 0 || appenderId >= MAX_APPENDERS) {
            throw new IllegalArgumentException("Invalid appender id: " + appenderId);
        }
        final AtomicBuffer buf = mapping.buffer();

        final int index = (appenderId / Long.SIZE) * Long.BYTES;
        final int bit = appenderId % Long.SIZE;
        final long mask = ~(1L << bit);

        long curBitSet;
        long newBitSet;
        do {
            curBitSet = buf.getLongVolatile(index);
            newBitSet = curBitSet & mask;
        } while (curBitSet != newBitSet && !buf.compareAndSetLong(index, curBitSet, newBitSet));
        if (curBitSet != newBitSet) {
            LOGGER.info("Released appenderId {} for {}", appenderId, name);
            return true;
        }
        return false;
    }

    @Override
    public int openAppenders() {
        if (mapping.isClosed()) {
            return 0;
        }
        final AtomicBuffer buffer = mapping.buffer();
        int count = 0;
        for (int index = 0; index < FILE_SIZE; index += Long.BYTES) {
            count += Long.bitCount(buffer.getLongVolatile(index));
        }
        return count;
    }

    @Override
    public void close() {
        if (!mapping.isClosed()) {
            final int open = openAppenders();
            mapping.close();
            LOGGER.info("Closed appender ID pool: {} ({} open appenders)", name, open);
        }
    }

    @Override
    public String toString() {
        return "AppenderIdPool256" +
                ":name='" + name + '\'' +
                "|open-appenders=" + openAppenders();
    }
}
