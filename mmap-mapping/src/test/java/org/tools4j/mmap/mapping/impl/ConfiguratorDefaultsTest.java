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
import org.tools4j.mmap.mapping.config.MappingConfig;
import org.tools4j.mmap.mapping.config.MappingStrategyConfig;
import org.tools4j.mmap.mapping.config.MappingStrategyConfigurator;
import org.tools4j.mmap.mapping.config.SharingPolicy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test asserting that configurator methods customizing nested configurations start from the defaults the
 * configurator was created with.
 */
class ConfiguratorDefaultsTest {

    @Test
    void mappingStrategyConsumerStartsFromDefaultsStrategy() {
        final MappingConfig defaults = MappingConfig.configure().mappingStrategy(cfg -> cfg.cacheSize(8));
        final MappingConfig config = MappingConfig.configure(defaults).mappingStrategy(cfg -> cfg.lruCacheSize(2));
        assertEquals(8, config.mappingStrategy().cacheSize());
        assertEquals(2, config.mappingStrategy().lruCacheSize());
    }

    @Test
    void asyncMappingConsumerStartsFromDefaultsAsyncMapping() {
        final MappingStrategyConfig defaults = MappingStrategyConfig.configure()
                .asyncMapping(cfg -> cfg.regionsToMapAhead(5));
        final MappingStrategyConfig config = MappingStrategyConfig.configure(defaults)
                .asyncMapping(cfg -> cfg.aheadMappingCacheSize(8));
        assertEquals(5, config.asyncMapping().orElseThrow().regionsToMapAhead());
        assertEquals(8, config.asyncMapping().orElseThrow().aheadMappingCacheSize());
    }

    @Test
    void asyncUnmappingConsumerStartsFromDefaultsAsyncUnmapping() {
        final MappingStrategyConfig defaults = MappingStrategyConfig.configure()
                .asyncUnmapping(cfg -> cfg.unmappingCacheSize(64));
        final MappingStrategyConfigurator config = MappingStrategyConfig.configure(defaults)
                .asyncUnmapping(cfg -> cfg.unmappingRuntime(SharingPolicy.SHARED));
        assertEquals(64, config.asyncUnmapping().orElseThrow().unmappingCacheSize());
    }

    @Test
    void enablingAsyncMappingKeepsDefaultsAsyncMapping() {
        final MappingStrategyConfig defaults = MappingStrategyConfig.configure()
                .asyncMapping(cfg -> cfg.regionsToMapAhead(5));
        final MappingStrategyConfig config = MappingStrategyConfig.configure(defaults).asyncMapping(true);
        assertEquals(5, config.asyncMapping().orElseThrow().regionsToMapAhead());
    }

    @Test
    void enablingAsyncMappingUsesSystemDefaultsIfDisabledByDefaults() {
        final MappingStrategyConfig defaults = MappingStrategyConfig.getDefaultSync();
        final MappingStrategyConfig config = MappingStrategyConfig.configure(defaults).asyncMapping(true);
        assertTrue(config.asyncMapping().isPresent());
    }
}
