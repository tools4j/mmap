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

import org.agrona.CloseHelper;
import org.agrona.concurrent.AtomicBuffer;
import org.tools4j.mmap.mapping.api.Describable;
import org.tools4j.mmap.mapping.api.ErrorListener;
import org.tools4j.mmap.mapping.api.LifecycleListener;
import org.tools4j.mmap.mapping.api.Mapping;
import org.tools4j.mmap.mapping.api.Mappings;
import org.tools4j.mmap.mapping.api.OpenMode;
import org.tools4j.mmap.mapping.config.MappingConfigurations;

import java.io.File;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.mapping.impl.Constraints.validateNotClosed;

/**
 * A pool of 64 IDs based on a memory-mapped file.
 * <p>
 * The mapped file is used as a bit set with each bit representing an ID. The file content is updated atomically in a
 * thread (and process) safe manner when {@link #acquire() acquiring} or {@link #release(int) releasing} IDs.
 */
public class IdPool64 implements IdPool, Describable {

    public static final int MAX_IDS = Long.SIZE;
    public static final int FILE_SIZE = MAX_IDS / Byte.SIZE;
    private final String name;
    private final Mapping mapping;
    private final LifecycleListener lifecycleListener;
    private int acquiredWhenClosed;

    public IdPool64(final File file) {
        this(file, MappingConfigurations.defaultLifecycleListener(), MappingConfigurations.defaultErrorListener());
    }

    public IdPool64(final File file, final LifecycleListener lifecycleListener, final ErrorListener errorListener) {
        this(file.getPath(), Mappings.fixedMapping(file, OpenMode.OPEN_OR_CREATE, 0L, FILE_SIZE, lifecycleListener,
                errorListener), lifecycleListener);
    }

    /**
     * Constructor for an ID pool using the given mapping, which is closed when the pool is closed, or if construction
     * fails.
     *
     * @param name      the pool name used in logging and exceptions
     * @param mapping   the mapping of {@link #FILE_SIZE} bytes
     */
    public IdPool64(final String name, final Mapping mapping) {
        this(name, mapping, MappingConfigurations.defaultLifecycleListener());
    }

    /**
     * Constructor for an ID pool using the given mapping, which is closed when the pool is closed, or if construction
     * fails.
     *
     * @param name              the pool name used in exceptions and descriptions
     * @param mapping           the mapping of {@link #FILE_SIZE} bytes
     * @param lifecycleListener the listener notified when the pool is opened and closed
     */
    public IdPool64(final String name, final Mapping mapping, final LifecycleListener lifecycleListener) {
        this.mapping = requireNonNull(mapping);
        try {
            if (mapping.bytesAvailable() != FILE_SIZE) {
                throw new IllegalArgumentException("Invalid mapping, expected " + FILE_SIZE + " bytes available but " +
                        "found " + mapping.bytesAvailable() + " for ID pool: " + name);
            }
            this.name = requireNonNull(name);
            this.lifecycleListener = requireNonNull(lifecycleListener);
        } catch (final Throwable t) {
            CloseHelper.quietClose(mapping);
            throw t;
        }
        lifecycleListener.onOpened(this);
    }

    @Override
    public int acquire() {
        validateNotClosed(this);
        final AtomicBuffer buf = mapping.buffer();

        long idBitSet;
        long idBit;
        do {
            idBitSet = buf.getLongAcquire(0);
            idBit = Long.lowestOneBit(~idBitSet);
        } while (idBit != 0 && !buf.compareAndSetLong(0, idBitSet, idBitSet | idBit));
        if (idBit != 0) {
            return Long.SIZE - Long.numberOfLeadingZeros(idBit - 1);
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
            curBitSet = buf.getLongAcquire(0);
            newBitSet = curBitSet & mask;
        } while (curBitSet != newBitSet && !buf.compareAndSetLong(0, curBitSet, newBitSet));
        if (curBitSet != newBitSet) {
            return true;
        }
        return false;
    }

    @Override
    public int acquired() {
        if (mapping.isClosed()) {
            return 0;
        }
        return Long.bitCount(mapping.buffer().getLongAcquire(0));
    }

    @Override
    public boolean isClosed() {
        return mapping.isClosed();
    }

    @Override
    public void close() {
        if (!isClosed()) {
            acquiredWhenClosed = acquired();
            mapping.close();
            lifecycleListener.onClosed(this);
        }
    }

    @Override
    public StringBuilder describeTo(final StringBuilder sb) {
        return sb.append("IdPool64")
                .append(":name=").append(name)
                .append("|acquired=").append(isClosed() ? acquiredWhenClosed : acquired())
                .append("|closed=").append(isClosed());
    }

    @Override
    public String toString() {
        return Describable.describe(this);
    }
}
