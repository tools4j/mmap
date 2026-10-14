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
import org.tools4j.mmap.mapping.api.AsyncRuntimeInstances;
import org.tools4j.mmap.mapping.config.AsyncMappingConfig;
import org.tools4j.mmap.mapping.config.AsyncUnmappingConfig;
import org.tools4j.mmap.mapping.config.MappingConfig;
import org.tools4j.mmap.mapping.config.MappingConfigurations;
import org.tools4j.mmap.mapping.config.MappingStrategyConfig;

import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Unit test for construction, copying and string representation of the immutable config records
 * {@link MappingConfigImpl}, {@link MappingStrategyConfigImpl}, {@link AsyncMappingConfigImpl} and
 * {@link AsyncUnmappingConfigImpl}.
 */
class ConfigImplTest {

    private static final int REGION_SIZE = MappingConfigurations.defaultRegionSize();
    private static final Supplier<? extends AsyncRuntime> MAPPING_RUNTIME = AsyncRuntimeInstances::sharedMappingRuntime;
    private static final Supplier<? extends AsyncRuntime> UNMAPPING_RUNTIME = AsyncRuntimeInstances::sharedUnmappingRuntime;

    @Test
    void asyncMappingConfigImpl() {
        final AsyncMappingConfigImpl config = new AsyncMappingConfigImpl(3, 8, MAPPING_RUNTIME);
        assertSame(config, config.toImmutableConfig());
        assertEquals(config, new AsyncMappingConfigImpl(config));
        assertEquals(config, new AsyncMappingConfigImpl(AsyncMappingConfig.configure()
                .regionsToMapAhead(3).aheadMappingCacheSize(8).mappingRuntimeSupplier(MAPPING_RUNTIME)));
        assertEquals("AsyncMappingConfigImpl:regionsToMapAhead=3|aheadMappingCacheSize=8", config.toString());
    }

    @Test
    void asyncMappingConfigImplDefaults() {
        final AsyncMappingConfigImpl config = new AsyncMappingConfigImpl();
        assertEquals(MappingConfigurations.defaultRegionsToMapAhead(), config.regionsToMapAhead());
        assertEquals(MappingConfigurations.defaultAheadMappingCacheSize(), config.aheadMappingCacheSize());
        assertSame(MappingConfigurations.defaultMappingRuntimeSupplier(), config.mappingRuntimeSupplier());
        assertEquals(config, new AsyncMappingConfigImpl());
    }

    @Test
    void asyncUnmappingConfigImpl() {
        final AsyncUnmappingConfigImpl config = new AsyncUnmappingConfigImpl(16, UNMAPPING_RUNTIME);
        assertSame(config, config.toImmutableConfig());
        assertEquals(config, new AsyncUnmappingConfigImpl(config));
        assertEquals(config, new AsyncUnmappingConfigImpl(AsyncUnmappingConfig.configure()
                .unmappingCacheSize(16).unmappingRuntimeSupplier(UNMAPPING_RUNTIME)));
        assertEquals("AsyncUnmappingConfigImpl:unmappingCacheSize=16", config.toString());
    }

    @Test
    void asyncUnmappingConfigImplDefaults() {
        final AsyncUnmappingConfigImpl config = new AsyncUnmappingConfigImpl();
        assertEquals(MappingConfigurations.defaultUnmappingCacheSize(), config.unmappingCacheSize());
        assertSame(MappingConfigurations.defaultUnmappingRuntimeSupplier(), config.unmappingRuntimeSupplier());
        assertEquals(config, new AsyncUnmappingConfigImpl());
    }

    @Test
    void mappingStrategyConfigImpl() {
        final MappingStrategyConfigImpl config = new MappingStrategyConfigImpl(REGION_SIZE, 4, 2, true,
                Optional.of(new AsyncMappingConfigImpl(3, 8, MAPPING_RUNTIME)), Optional.empty());
        assertSame(config, config.toImmutableConfig());
        assertEquals(config, new MappingStrategyConfigImpl(config));
        assertEquals("MappingStrategyConfigImpl:regionSize=" + REGION_SIZE + "|cacheSize=4|lruCacheSize=2" +
                "|deferUnmapping=true|asyncMapping=AsyncMappingConfigImpl:regionsToMapAhead=3|aheadMappingCacheSize=8" +
                "|asyncUnmapping=n/a", config.toString());
    }

    @Test
    void mappingStrategyConfigImplDefaults() {
        final MappingStrategyConfigImpl config = new MappingStrategyConfigImpl();
        assertEquals(MappingConfigurations.defaultRegionSize(), config.regionSize());
        assertEquals(MappingConfigurations.defaultRegionCacheSize(), config.cacheSize());
        assertEquals(MappingConfigurations.defaultRegionLruCacheSize(), config.lruCacheSize());
        assertEquals(MappingConfigurations.defaultDeferUnmapping(), config.deferUnmapping());
        assertEquals(MappingConfigurations.defaultAsyncMapping(), config.asyncMapping().isPresent());
        assertEquals(MappingConfigurations.defaultAsyncUnmapping(), config.asyncUnmapping().isPresent());
        assertEquals(config, new MappingStrategyConfigImpl());
    }

    @Test
    void mappingConfigImpl() {
        final MappingStrategyConfig strategy = MappingStrategyConfig.getDefaultSync().toImmutableConfig();
        final MappingConfigImpl config = new MappingConfigImpl(0, 4L * REGION_SIZE, true, false, 5, 1, strategy);
        assertSame(config, config.toImmutableConfig());
        assertEquals(config, new MappingConfigImpl(config));
        assertEquals("MappingConfigImpl:minFileSize=0|maxFileSize=" + 4L * REGION_SIZE + "|expandFile=true" +
                "|rollFiles=false|maxOpenFiles=5|filesToCreateAhead=1|mappingStrategy=" + strategy, config.toString());
    }

    @Test
    void mappingConfigImplDefaults() {
        final MappingConfigImpl config = new MappingConfigImpl();
        final MappingConfig defaults = MappingConfig.getDefault();
        assertEquals(defaults.minFileSize(), config.minFileSize());
        assertEquals(defaults.maxFileSize(), config.maxFileSize());
        assertEquals(defaults.expandFile(), config.expandFile());
        assertEquals(defaults.rollFiles(), config.rollFiles());
        assertEquals(defaults.maxOpenFiles(), config.maxOpenFiles());
        assertEquals(defaults.filesToCreateAhead(), config.filesToCreateAhead());
        assertEquals(defaults.mappingStrategy().toImmutableConfig(), config.mappingStrategy());
        assertEquals(config, new MappingConfigImpl());
    }
}
