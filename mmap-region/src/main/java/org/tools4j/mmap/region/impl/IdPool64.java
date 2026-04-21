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
package org.tools4j.mmap.region.impl;

import org.agrona.concurrent.AtomicBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.region.api.AccessMode;
import org.tools4j.mmap.region.api.Mapping;
import org.tools4j.mmap.region.api.Mappings;

import java.io.File;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.impl.Constraints.validateNotClosed;

/**
 * A pool of 64 IDs based on a memory-mapped file.
 * <p>
 * The mapped file is used as a bit set with each bit representing an ID. The file content is updated atomically in a
 * thread (and process) safe manner when {@link #acquire() acquiring} or {@link #release(int) releasing} IDs.
 */
public class IdPool64 implements IdPool {
    private static final Logger LOGGER = LoggerFactory.getLogger(IdPool64.class);

    public static final int MAX_IDS = Long.SIZE;
    public static final int FILE_SIZE = MAX_IDS / Byte.SIZE;
    private final String name;
    private final Mapping mapping;

    public IdPool64(final File file) {
        this(file.getPath(), Mappings.fixedSizeMapping(file, AccessMode.READ_WRITE, 0L, FILE_SIZE));
    }

    public IdPool64(final String name, final Mapping mapping) {
        if (mapping.bytesAvailable() != FILE_SIZE) {
            throw new IllegalArgumentException("Invalid mapping, expected " + FILE_SIZE + " bytes available but found "
                    + mapping.bytesAvailable() + " for ID pool: " + name);
        }
        this.name = requireNonNull(name);
        this.mapping = requireNonNull(mapping);
        LOGGER.info("Opened ID pool: {} ({} acquired IDs)", name, acquired());
    }

    @Override
    public int acquire() {
        validateNotClosed(this);
        final AtomicBuffer buf = mapping.buffer();

        long idBitSet;
        long idBit;
        do {
            idBitSet = buf.getLongVolatile(0);
            idBit = Long.lowestOneBit(~idBitSet);
        } while (idBit != 0 && !buf.compareAndSetLong(0, idBitSet, idBitSet | idBit));
        if (idBit != 0) {
            final int id = Long.SIZE - Long.numberOfLeadingZeros(idBit - 1);
            LOGGER.info("Acquired ID {} from {} pool", id, name);
            return id;
        }
        throw new IllegalStateException("Exceeded max number of " + MAX_IDS + " IDs in pool: " + name);
    }

    @Override
    public boolean release(final int id) {
        validateNotClosed(this);
        if (id < 0 || id >= MAX_IDS) {
            throw new IllegalArgumentException("Invalid ID: " + id);
        }
        final AtomicBuffer buf = mapping.buffer();
        final long mask = ~(1L << id);

        long curBitSet;
        long newBitSet;
        do {
            curBitSet = buf.getLongVolatile(0);
            newBitSet = curBitSet & mask;
        } while (curBitSet != newBitSet && !buf.compareAndSetLong(0, curBitSet, newBitSet));
        if (curBitSet != newBitSet) {
            LOGGER.info("Released ID {} to {} pool", id, name);
            return true;
        }
        return false;
    }

    @Override
    public int acquired() {
        if (mapping.isClosed()) {
            return 0;
        }
        return Long.bitCount(mapping.buffer().getLongVolatile(0));
    }

    @Override
    public boolean isClosed() {
        return mapping.isClosed();
    }

    @Override
    public void close() {
        if (!isClosed()) {
            final int open = acquired();
            mapping.close();
            LOGGER.info("Closed ID pool: {} ({} acquired IDs)", name, open);
        }
    }

    @Override
    public String toString() {
        return "IdPool64" +
                ":name='" + name + '\'' +
                "|acquired=" + acquired();
    }
}
