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

import org.junit.jupiter.api.Test;
import org.tools4j.mmap.mapping.api.AsyncRuntime;
import org.tools4j.mmap.mapping.config.AsyncMappingConfig;
import org.tools4j.mmap.mapping.config.AsyncUnmappingConfig;
import org.tools4j.mmap.mapping.config.MappingConfigurations;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link AsyncMappingConfiguratorImpl} and {@link AsyncUnmappingConfiguratorImpl} value resolution.
 */
class AsyncConfiguratorImplTest {

    @Test
    void explicitZeroAheadMappingCacheSizeOverridesDefaults() {
        final AsyncMappingConfig defaults = AsyncMappingConfig.configure().aheadMappingCacheSize(4);
        assertEquals(4, AsyncMappingConfig.configure(defaults).aheadMappingCacheSize());
        assertEquals(0, AsyncMappingConfig.configure(defaults).aheadMappingCacheSize(0).aheadMappingCacheSize());
    }

    @Test
    void negativeAheadMappingCacheSizeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> AsyncMappingConfig.configure().aheadMappingCacheSize(-1));
        assertThrows(IllegalArgumentException.class, () -> AsyncMappingConfig.configure().aheadMappingCacheSize(3));
    }

    @Test
    void zeroUnmappingCacheSizeResetsToDefault() {
        final AsyncUnmappingConfig defaults = AsyncUnmappingConfig.configure().unmappingCacheSize(8);
        assertEquals(8, AsyncUnmappingConfig.configure(defaults).unmappingCacheSize());
        assertEquals(16, AsyncUnmappingConfig.configure(defaults).unmappingCacheSize(16).unmappingCacheSize());
        assertEquals(8, AsyncUnmappingConfig.configure(defaults).unmappingCacheSize(16).unmappingCacheSize(0)
                .unmappingCacheSize());
        assertEquals(MappingConfigurations.defaultUnmappingCacheSize(),
                AsyncUnmappingConfig.configure().unmappingCacheSize(0).unmappingCacheSize());
    }

    @Test
    void invalidUnmappingCacheSizeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> AsyncUnmappingConfig.configure().unmappingCacheSize(-1));
        assertThrows(IllegalArgumentException.class, () -> AsyncUnmappingConfig.configure().unmappingCacheSize(3));
        assertThrows(IllegalArgumentException.class, () -> new AsyncUnmappingConfigImpl(0,
                MappingConfigurations.defaultUnmappingRuntimeSupplier()));
    }

    @Test
    void unmappingRuntimeSupplierFallsBackToUnmappingSystemDefault() {
        final AsyncUnmappingConfig defaultsWithoutRuntime = new AsyncUnmappingConfig() {
            @Override
            public int unmappingCacheSize() {
                return -1;
            }

            @Override
            public Supplier<? extends AsyncRuntime> unmappingRuntimeSupplier() {
                return null;
            }

            @Override
            public AsyncUnmappingConfig toImmutableConfig() {
                return this;
            }
        };
        assertSame(MappingConfigurations.defaultUnmappingRuntimeSupplier(),
                AsyncUnmappingConfig.configure(defaultsWithoutRuntime).unmappingRuntimeSupplier());
    }
}
