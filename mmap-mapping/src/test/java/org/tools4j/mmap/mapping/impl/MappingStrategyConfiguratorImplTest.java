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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.tools4j.mmap.mapping.config.AsyncMappingConfig;
import org.tools4j.mmap.mapping.config.AsyncUnmappingConfig;
import org.tools4j.mmap.mapping.config.MappingConfigurations;
import org.tools4j.mmap.mapping.config.MappingStrategyConfig;
import org.tools4j.mmap.mapping.config.MappingStrategyConfigurator;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tools4j.mmap.mapping.config.MappingConfigurations.ASYNC_MAPPING_PROPERTY;
import static org.tools4j.mmap.mapping.config.MappingConfigurations.ASYNC_UNMAPPING_PROPERTY;
import static org.tools4j.mmap.mapping.impl.AsyncMappingConfigDefaults.ASYNC_MAPPING_CONFIG_DEFAULTS;
import static org.tools4j.mmap.mapping.impl.AsyncUnmappingConfigDefaults.ASYNC_UNMAPPING_CONFIG_DEFAULTS;

/**
 * Unit test for {@link MappingStrategyConfiguratorImpl} value resolution: own value, then defaults, then system
 * defaults.
 */
class MappingStrategyConfiguratorImplTest {

    /** Defaults returning the "unset" sentinel for every value, forcing fallback to system defaults */
    @SuppressWarnings("OptionalAssignedToNull")
    private static final MappingStrategyConfig UNSET_DEFAULTS = new MappingStrategyConfig() {
        @Override
        public int cacheSize() {
            return -1;
        }

        @Override
        public int lruCacheSize() {
            return -1;
        }

        @Override
        public boolean deferUnmapping() {
            return false;
        }

        @Override
        public Optional<AsyncMappingConfig> asyncMapping() {
            return null;
        }

        @Override
        public Optional<AsyncUnmappingConfig> asyncUnmapping() {
            return null;
        }

        @Override
        public MappingStrategyConfig toImmutableConfig() {
            return this;
        }

        @Override
        public String toString() {
            return "UNSET_DEFAULTS";
        }
    };

    @AfterEach
    void clearProperties() {
        System.clearProperty(ASYNC_MAPPING_PROPERTY);
        System.clearProperty(ASYNC_UNMAPPING_PROPERTY);
    }

    @Test
    void unsetDefaultsFallBackToSystemDefaults() {
        final MappingStrategyConfigurator config = MappingStrategyConfigurator.configure(UNSET_DEFAULTS);
        assertEquals(MappingConfigurations.defaultRegionCacheSize(), config.cacheSize());
        assertEquals(MappingConfigurations.defaultRegionLruCacheSize(), config.lruCacheSize());
        assertFalse(config.deferUnmapping());
    }

    @Test
    void unsetAsyncDefaultsFallBackToSystemDefaultsIfEnabled() {
        System.setProperty(ASYNC_MAPPING_PROPERTY, "true");
        System.setProperty(ASYNC_UNMAPPING_PROPERTY, "true");
        final MappingStrategyConfigurator config = MappingStrategyConfigurator.configure(UNSET_DEFAULTS);
        assertSame(ASYNC_MAPPING_CONFIG_DEFAULTS, config.asyncMapping().orElseThrow());
        assertSame(ASYNC_UNMAPPING_CONFIG_DEFAULTS, config.asyncUnmapping().orElseThrow());
    }

    @Test
    void unsetAsyncDefaultsAreEmptyIfSystemDefaultsDisabled() {
        System.setProperty(ASYNC_MAPPING_PROPERTY, "false");
        System.setProperty(ASYNC_UNMAPPING_PROPERTY, "false");
        final MappingStrategyConfigurator config = MappingStrategyConfigurator.configure(UNSET_DEFAULTS);
        assertFalse(config.asyncMapping().isPresent());
        assertFalse(config.asyncUnmapping().isPresent());
    }

    @Test
    void valuesAreResolvedFromDefaults() {
        final MappingStrategyConfig defaults = MappingStrategyConfigurator.configure()
                .cacheSize(8).lruCacheSize(4).deferUnmapping(true);
        final MappingStrategyConfigurator config = MappingStrategyConfigurator.configure(defaults);
        assertEquals(8, config.cacheSize());
        assertEquals(4, config.lruCacheSize());
        assertTrue(config.deferUnmapping());
    }

    @Test
    void explicitValuesOverrideDefaults() {
        final MappingStrategyConfig defaults = MappingStrategyConfigurator.configure()
                .lruCacheSize(4).deferUnmapping(true);
        final MappingStrategyConfigurator config = MappingStrategyConfigurator.configure(defaults)
                .lruCacheSize(0).deferUnmapping(false);
        assertEquals(0, config.lruCacheSize());
        assertFalse(config.deferUnmapping());
    }

    @Test
    void asyncFlagsEnableAndDisableAsyncConfig() {
        final MappingStrategyConfigurator config = MappingStrategyConfigurator.configure(
                MappingStrategyConfig.getDefaultSync());
        assertSame(ASYNC_MAPPING_CONFIG_DEFAULTS, config.asyncMapping(true).asyncMapping().orElseThrow());
        assertSame(ASYNC_UNMAPPING_CONFIG_DEFAULTS, config.asyncUnmapping(true).asyncUnmapping().orElseThrow());
        assertFalse(config.asyncMapping(false).asyncMapping().isPresent());
        assertFalse(config.asyncUnmapping(false).asyncUnmapping().isPresent());
    }

    @Test
    void enablingAsyncUnmappingKeepsConfiguredAsyncUnmapping() {
        final AsyncUnmappingConfig asyncUnmapping = AsyncUnmappingConfig.configure().unmappingCacheSize(64)
                .toImmutableConfig();
        final MappingStrategyConfigurator config = MappingStrategyConfigurator.configure()
                .asyncUnmapping(asyncUnmapping).asyncUnmapping(true);
        assertSame(asyncUnmapping, config.asyncUnmapping().orElseThrow());
    }

    @Test
    void invalidValuesAreRejected() {
        final MappingStrategyConfigurator config = MappingStrategyConfigurator.configure();
        assertThrows(IllegalArgumentException.class, () -> config.cacheSize(0));
        assertThrows(IllegalArgumentException.class, () -> config.cacheSize(3));
        assertThrows(IllegalArgumentException.class, () -> config.lruCacheSize(-1));
    }

    @Test
    void toStringShowsUnresolvedValuesAndDefaults() {
        final MappingStrategyConfigurator config = MappingStrategyConfigurator.configure(UNSET_DEFAULTS);
        assertEquals("MappingStrategyConfiguratorImpl:cacheSize=-1|lruCacheSize=-1" +
                "|deferUnmapping=null|asyncMapping=null|asyncUnmapping=null|defaults=UNSET_DEFAULTS", config.toString());
        config.asyncMapping(false).asyncUnmapping(new AsyncUnmappingConfigImpl(16,
                MappingConfigurations.defaultUnmappingRuntimeSupplier()));
        assertEquals("MappingStrategyConfiguratorImpl:cacheSize=-1|lruCacheSize=-1" +
                "|deferUnmapping=null|asyncMapping=n/a|asyncUnmapping=AsyncUnmappingConfigImpl:unmappingCacheSize=16" +
                "|defaults=UNSET_DEFAULTS", config.toString());
    }
}
