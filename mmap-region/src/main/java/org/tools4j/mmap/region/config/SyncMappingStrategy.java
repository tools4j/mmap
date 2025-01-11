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
package org.tools4j.mmap.region.config;

import org.tools4j.mmap.region.unsafe.SyncRegionMapper;
import org.tools4j.mmap.region.unsafe.SyncRingRegionMapper;

import java.util.Optional;

import static org.tools4j.mmap.region.impl.Constraints.validateNonNegative;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionCacheSize;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionSize;

/**
 * Synchronous mapping strategy that uses no async background mapping or unmapping.  The mapper implementations for sync
 * strategies are {@link SyncRegionMapper} and {@link SyncRingRegionMapper}.
 */
public class SyncMappingStrategy implements MappingStrategy {
    public static final String NAME = "SyncMappingStrategy";

    private final int regionSize;
    private final int cacheSize;

    public SyncMappingStrategy(final MappingStrategyConfig config) {
        this(config.regionSize(), config.cacheSize());
    }

    public SyncMappingStrategy(final int regionSize) {
        this(regionSize, 0);
    }

    public SyncMappingStrategy(final int regionSize, final int cacheSize) {
        validateRegionSize(regionSize);
        validateNonNegative("Cache size", cacheSize);
        if (cacheSize > 0) {
            validateRegionCacheSize(cacheSize);
        }
        this.regionSize = regionSize;
        this.cacheSize = cacheSize;
    }

    public static MappingStrategy getDefault() {
        return MappingConfigurations.defaultSyncMappingStrategy();
    }

    @Override
    public int regionSize() {
        return regionSize;
    }

    @Override
    public int cacheSize() {
        return cacheSize;
    }

    @Override
    public Optional<AsyncOptions> asyncOptions() {
        return Optional.empty();
    }

    @Override
    public String toString() {
        return "SyncMappingStrategy" +
                ":regionSize=" + regionSize +
                "|cacheSize=" + cacheSize;
    }
}
