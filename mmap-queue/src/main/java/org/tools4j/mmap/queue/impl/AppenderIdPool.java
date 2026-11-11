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
package org.tools4j.mmap.queue.impl;

import org.tools4j.mmap.core.base.Closeable;
import org.tools4j.mmap.core.base.Describable;
import org.tools4j.mmap.core.base.ErrorListener;
import org.tools4j.mmap.core.base.LifecycleListener;
import org.tools4j.mmap.core.config.CoreConfigurations;
import org.tools4j.mmap.core.internal.Closeables;
import org.tools4j.mmap.mapping.api.Mapping;
import org.tools4j.mmap.mapping.api.Mappings;
import org.tools4j.mmap.mapping.api.OpenMode;
import org.tools4j.mmap.mapping.config.MappingConfigurations;
import org.tools4j.mmap.mapping.impl.AtomicIdBitSet;

import java.io.File;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.core.internal.Validations.validateNotClosed;

/**
 * The pool of appender IDs of a queue, up to 256 IDs in a memory-mapped file.
 * <p>
 * The mapped file is used as a bit set with each bit representing an ID. The file content is updated atomically in a
 * thread (and process) safe manner when {@link #acquire() acquiring} or {@link #release(int) releasing} IDs.
 * <p>
 * The file always has {@link #FILE_SIZE} bytes, independent of the max IDs of the pool. Pools with different max IDs
 * can therefore share the same file, each acquiring only IDs below its own max IDs, lowest available ID first.
 */
public final class AppenderIdPool implements Closeable, Describable {

    public static final int MAX_IDS = 1 << Byte.SIZE;
    public static final int FILE_SIZE = MAX_IDS / Byte.SIZE;
    private final String name;
    private final Mapping mapping;
    private final LifecycleListener lifecycleListener;
    private final AtomicIdBitSet ids;
    private int acquiredWhenClosed;//only used for describe string after close

    /**
     * Validates the max appenders of a queue, the max IDs acquired through its appender ID pool.
     *
     * @param maxAppenders the max appenders
     * @throws IllegalArgumentException if max appenders is not in {@code [1, MAX_IDS]}
     */
    public static void validateMaxAppenders(final int maxAppenders) {
        if (maxAppenders < 1 || maxAppenders > MAX_IDS) {
            throw new IllegalArgumentException("Max appenders must be in [1, " + MAX_IDS + "] but was " +
                    maxAppenders);
        }
    }

    public AppenderIdPool(final File file) {
        this(file, MAX_IDS);
    }

    public AppenderIdPool(final File file, final int maxIds) {
        this(file, maxIds, MappingConfigurations.defaultLifecycleListener(),
                CoreConfigurations.defaultErrorListener());
    }

    public AppenderIdPool(final File file,
                          final int maxIds,
                          final LifecycleListener lifecycleListener,
                          final ErrorListener errorListener) {
        this(file.getPath(), Mappings.fixedMapping(file, OpenMode.OPEN_OR_CREATE, 0L, FILE_SIZE, lifecycleListener,
                errorListener), maxIds, lifecycleListener);
    }

    public AppenderIdPool(final String name, final Mapping mapping) {
        this(name, mapping, MAX_IDS);
    }

    /**
     * Constructor for an ID pool using the given mapping, which is closed when the pool is closed, or if construction
     * fails.
     *
     * @param name      the pool name used in logging and exceptions
     * @param mapping   the mapping of {@link #FILE_SIZE} bytes
     * @param maxIds    the max IDs of this pool, only IDs below this value are acquired
     */
    public AppenderIdPool(final String name, final Mapping mapping, final int maxIds) {
        this(name, mapping, maxIds, MappingConfigurations.defaultLifecycleListener());
    }

    /**
     * Constructor for an ID pool using the given mapping, which is closed when the pool is closed, or if construction
     * fails.
     *
     * @param name              the pool name used in exceptions and descriptions
     * @param mapping           the mapping of {@link #FILE_SIZE} bytes
     * @param maxIds            the max IDs of this pool, only IDs below this value are acquired
     * @param lifecycleListener the listener notified when the pool is opened and closed
     */
    public AppenderIdPool(final String name,
                          final Mapping mapping,
                          final int maxIds,
                          final LifecycleListener lifecycleListener) {
        this.mapping = requireNonNull(mapping);
        try {
            if (maxIds < 1 || maxIds > MAX_IDS) {
                throw new IllegalArgumentException("Max IDs must be in [1, " + MAX_IDS + "] but was " + maxIds +
                        " for ID pool: " + name);
            }
            if (mapping.bytesAvailable() != FILE_SIZE) {
                throw new IllegalArgumentException("Invalid mapping, expected " + FILE_SIZE + " bytes available but " +
                        "found " + mapping.bytesAvailable() + " for ID pool: " + name);
            }
            this.name = requireNonNull(name);
            this.lifecycleListener = requireNonNull(lifecycleListener);
        } catch (final Throwable t) {
            Closeables.closeOnFailure(mapping, t);
            throw t;
        }
        this.ids = new AtomicIdBitSet(mapping.buffer(), maxIds);
        lifecycleListener.onOpened(this);
    }

    /**
     * Acquires the lowest free ID below the max IDs of this pool.
     *
     * @return the acquired ID
     * @throws IllegalStateException if no IDs are left in the pool or if the pool is closed
     */
    public int acquire() {
        validateNotClosed(this);
        final int id = ids.acquire();
        if (id == AtomicIdBitSet.NO_ID) {
            throw new IllegalStateException("Exceeded max number of " + ids.maxIds() + " IDs in pool: " + name);
        }
        return id;
    }

    /**
     * Releases the given ID.
     *
     * @param id the ID to release
     * @return true if the ID was released, and false if it was not acquired
     * @throws IllegalArgumentException if the ID is not below the max IDs of this pool
     * @throws IllegalStateException if the pool is closed
     */
    public boolean release(final int id) {
        validateNotClosed(this);
        return ids.release(id);
    }

    /**
     * Returns the number of IDs currently acquired, including IDs acquired by pools with a larger max sharing the
     * file.
     *
     * @return the IDs currently acquired, or zero if the pool is closed
     */
    public int acquired() {
        return mapping.isClosed() ? 0 : ids.acquired();
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
        return sb.append("AppenderIdPool")
                .append(":name=").append(name)
                .append("|maxIds=").append(ids.maxIds())
                .append("|acquired=").append(isClosed() ? acquiredWhenClosed : acquired())
                .append("|closed=").append(isClosed());
    }

    @Override
    public String toString() {
        return Describable.describe(this);
    }
}
