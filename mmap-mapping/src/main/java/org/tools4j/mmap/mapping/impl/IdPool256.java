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

import org.agrona.concurrent.AtomicBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.mapping.api.Mapping;
import org.tools4j.mmap.mapping.api.Mappings;
import org.tools4j.mmap.mapping.api.OpenMode;

import java.io.File;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.mapping.impl.Constraints.validateNotClosed;

/**
 * A pool of up to 256 IDs based on a memory-mapped file.
 * <p>
 * The mapped file is used as a bit set with each bit representing an ID. The file content is updated atomically in a
 * thread (and process) safe manner when {@link #acquire() acquiring} or {@link #release(int) releasing} IDs.
 * <p>
 * The file always has {@link #FILE_SIZE} bytes, independent of the max IDs of the pool. Pools with different max IDs
 * can therefore share the same file, each acquiring only IDs below its own max IDs, lowest available ID first.
 */
public class IdPool256 implements IdPool {
    private static final Logger LOGGER = LoggerFactory.getLogger(IdPool256.class);

    public static final int MAX_IDS = 1 << Byte.SIZE;
    public static final int FILE_SIZE = MAX_IDS / Byte.SIZE;
    private final String name;
    private final Mapping mapping;
    private final int maxIds;
    private final int lastWordIndex;
    private final long lastWordMask;

    public IdPool256(final File file) {
        this(file, MAX_IDS);
    }

    public IdPool256(final File file, final int maxIds) {
        this(file.getPath(), Mappings.fixedMapping(file, OpenMode.OPEN_OR_CREATE, 0L, FILE_SIZE), maxIds);
    }

    public IdPool256(final String name, final Mapping mapping) {
        this(name, mapping, MAX_IDS);
    }

    public IdPool256(final String name, final Mapping mapping, final int maxIds) {
        if (maxIds < 1 || maxIds > MAX_IDS) {
            throw new IllegalArgumentException("Max IDs must be in [1, " + MAX_IDS + "] but was " + maxIds +
                    " for ID pool: " + name);
        }
        if (mapping.bytesAvailable() != FILE_SIZE) {
            throw new IllegalArgumentException("Invalid mapping, expected " + FILE_SIZE + " bytes available but found "
                    + mapping.bytesAvailable() + " for ID pool: " + name);
        }
        this.name = requireNonNull(name);
        this.mapping = requireNonNull(mapping);
        this.maxIds = maxIds;
        this.lastWordIndex = (maxIds - 1) / Long.SIZE * Long.BYTES;
        final int lastWordIds = maxIds - lastWordIndex / Long.BYTES * Long.SIZE;
        this.lastWordMask = -1L >>> (Long.SIZE - lastWordIds);
        LOGGER.info("Opened ID pool: {} ({} acquired IDs)", name, acquired());
    }

    @Override
    public int acquire() {
        validateNotClosed(this);
        final AtomicBuffer buf = mapping.buffer();

        for (int index = 0; index <= lastWordIndex; index += Long.BYTES) {
            final long idMask = index == lastWordIndex ? lastWordMask : -1L;
            long idBitSet;
            long idBit;
            do {
                idBitSet = buf.getLongAcquire(index);
                idBit = Long.lowestOneBit(~idBitSet & idMask);
            } while (idBit != 0 && !buf.compareAndSetLong(index, idBitSet, idBitSet | idBit));
            if (idBit != 0) {
                final int idBitValue = Long.SIZE - Long.numberOfLeadingZeros(idBit - 1);
                final int id = index / Long.BYTES * Long.SIZE + idBitValue;
                LOGGER.info("Acquired ID {} from {} pool", id, name);
                return id;
            }
        }
        throw new IllegalStateException("Exceeded max number of " + maxIds + " IDs in pool: " + name);
    }

    @Override
    public boolean release(final int id) {
        validateNotClosed(this);
        if (id < 0 || id >= maxIds) {
            throw new IllegalArgumentException("Invalid ID: " + id);
        }
        final AtomicBuffer buf = mapping.buffer();
        final int index = (id / Long.SIZE) * Long.BYTES;
        final int bit = id % Long.SIZE;
        final long mask = ~(1L << bit);

        long curBitSet;
        long newBitSet;
        do {
            curBitSet = buf.getLongAcquire(index);
            newBitSet = curBitSet & mask;
        } while (curBitSet != newBitSet && !buf.compareAndSetLong(index, curBitSet, newBitSet));
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
        final AtomicBuffer buffer = mapping.buffer();
        int count = 0;
        for (int index = 0; index < FILE_SIZE; index += Long.BYTES) {
            count += Long.bitCount(buffer.getLongAcquire(index));
        }
        return count;
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
        return "IdPool256" +
                ":name='" + name + '\'' +
                "|maxIds=" + maxIds +
                "|acquired=" + acquired();
    }
}
