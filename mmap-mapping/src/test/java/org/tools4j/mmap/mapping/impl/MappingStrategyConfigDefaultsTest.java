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
import org.tools4j.mmap.mapping.config.MappingConfigurations;
import org.tools4j.mmap.mapping.config.MappingStrategyConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for the async/sync settings of {@link MappingStrategyConfigDefaults}.
 */
class MappingStrategyConfigDefaultsTest {

    @Test
    void defaultsUseSystemDefaults() {
        final MappingStrategyConfig config = MappingStrategyConfig.getDefault();
        assertEquals(MappingConfigurations.defaultAsyncMapping(), config.asyncMapping().isPresent());
        assertEquals(MappingConfigurations.defaultAsyncUnmapping(), config.asyncUnmapping().isPresent());
    }

    @Test
    void syncDefaultsAreFullySynchronous() {
        final MappingStrategyConfig config = MappingStrategyConfig.getDefaultSync();
        assertFalse(config.asyncMapping().isPresent());
        assertFalse(config.asyncUnmapping().isPresent());
    }

    @Test
    void syncWithAsyncUnmappingDefaultsOnlyUnmapAsynchronously() {
        final MappingStrategyConfig config = MappingStrategyConfig.getDefaultSyncWithAsyncUnmapping();
        assertFalse(config.asyncMapping().isPresent());
        assertTrue(config.asyncUnmapping().isPresent());
    }

    @Test
    void asyncMapAheadDefaultsAreFullyAsynchronous() {
        final MappingStrategyConfig config = MappingStrategyConfig.getDefaultAsyncMapAhead();
        assertTrue(config.asyncMapping().isPresent());
        assertTrue(config.asyncUnmapping().isPresent());
    }

    @Test
    void immutableCopiesPreserveAsyncSettings() {
        for (final MappingStrategyConfigDefaults defaults : MappingStrategyConfigDefaults.values()) {
            final MappingStrategyConfig copy = defaults.toImmutableConfig();
            assertEquals(defaults.asyncMapping().isPresent(), copy.asyncMapping().isPresent(), defaults.name());
            assertEquals(defaults.asyncUnmapping().isPresent(), copy.asyncUnmapping().isPresent(), defaults.name());
        }
    }
}
