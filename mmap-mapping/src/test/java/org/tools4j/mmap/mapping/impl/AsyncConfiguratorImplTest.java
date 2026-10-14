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

import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.junit.jupiter.api.Test;
import org.tools4j.mmap.mapping.api.AsyncRuntime;
import org.tools4j.mmap.mapping.api.AsyncRuntimeInstances;
import org.tools4j.mmap.mapping.config.AsyncMappingConfig;
import org.tools4j.mmap.mapping.config.AsyncMappingConfigurator;
import org.tools4j.mmap.mapping.config.AsyncUnmappingConfig;
import org.tools4j.mmap.mapping.config.AsyncUnmappingConfigurator;
import org.tools4j.mmap.mapping.config.MappingConfigurations;
import org.tools4j.mmap.mapping.config.SharingPolicy;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Unit test for {@link AsyncMappingConfiguratorImpl} and {@link AsyncUnmappingConfiguratorImpl} value resolution.
 */
class AsyncConfiguratorImplTest {

    /** Async mapping defaults returning the "unset" sentinel for every value, forcing fallback to system defaults */
    private static final AsyncMappingConfig UNSET_MAPPING_DEFAULTS = new AsyncMappingConfig() {
        @Override
        public int regionsToMapAhead() {
            return 0;
        }

        @Override
        public int aheadMappingCacheSize() {
            return -1;
        }

        @Override
        public Supplier<? extends AsyncRuntime> mappingRuntimeSupplier() {
            return null;
        }

        @Override
        public AsyncMappingConfig toImmutableConfig() {
            return this;
        }

        @Override
        public String toString() {
            return "UNSET_MAPPING_DEFAULTS";
        }
    };

    /** Async unmapping defaults returning the "unset" sentinel for every value, forcing fallback to system defaults */
    private static final AsyncUnmappingConfig UNSET_UNMAPPING_DEFAULTS = new AsyncUnmappingConfig() {
        @Override
        public int unmappingCacheSize() {
            return 0;
        }

        @Override
        public Supplier<? extends AsyncRuntime> unmappingRuntimeSupplier() {
            return null;
        }

        @Override
        public AsyncUnmappingConfig toImmutableConfig() {
            return this;
        }

        @Override
        public String toString() {
            return "UNSET_UNMAPPING_DEFAULTS";
        }
    };

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

    @Test
    void unsetAsyncMappingDefaultsFallBackToSystemDefaults() {
        final AsyncMappingConfigurator config = AsyncMappingConfigurator.configure(UNSET_MAPPING_DEFAULTS);
        assertEquals(MappingConfigurations.defaultRegionsToMapAhead(), config.regionsToMapAhead());
        assertEquals(MappingConfigurations.defaultAheadMappingCacheSize(), config.aheadMappingCacheSize());
        assertSame(MappingConfigurations.defaultMappingRuntimeSupplier(), config.mappingRuntimeSupplier());
    }

    @Test
    void unsetAsyncUnmappingDefaultsFallBackToSystemDefaults() {
        final AsyncUnmappingConfigurator config = AsyncUnmappingConfigurator.configure(UNSET_UNMAPPING_DEFAULTS);
        assertEquals(MappingConfigurations.defaultUnmappingCacheSize(), config.unmappingCacheSize());
        assertSame(MappingConfigurations.defaultUnmappingRuntimeSupplier(), config.unmappingRuntimeSupplier());
    }

    @Test
    void runtimeSupplierIsResolvedFromDefaults() {
        final Supplier<AsyncRuntime> mappingRuntime = AsyncRuntimeInstances::sharedMappingRuntime;
        final Supplier<AsyncRuntime> unmappingRuntime = AsyncRuntimeInstances::sharedUnmappingRuntime;
        assertSame(mappingRuntime, AsyncMappingConfigurator.configure(
                AsyncMappingConfigurator.configure().mappingRuntimeSupplier(mappingRuntime)).mappingRuntimeSupplier());
        assertSame(unmappingRuntime, AsyncUnmappingConfigurator.configure(
                AsyncUnmappingConfigurator.configure().unmappingRuntimeSupplier(unmappingRuntime))
                .unmappingRuntimeSupplier());
    }

    @Test
    void mappingRuntimeSettersInstallSupplier() {
        final AsyncRuntime runtime = mock(AsyncRuntime.class);
        assertSame(runtime, AsyncMappingConfigurator.configure().mappingRuntime(runtime).mappingRuntimeSupplier().get());
        assertSame(AsyncRuntimeInstances.sharedMappingRuntime(), AsyncMappingConfigurator.configure()
                .mappingRuntime(SharingPolicy.SHARED).mappingRuntimeSupplier().get());
        final AsyncMappingConfigurator config = AsyncMappingConfigurator.configure();
        assertThrows(NullPointerException.class, () -> config.mappingRuntime((AsyncRuntime) null));
        assertThrows(NullPointerException.class, () -> config.mappingRuntimeSupplier(null));
        assertThrows(NullPointerException.class, () -> config.mappingRuntimeUsing((IdleStrategy) null));
        assertThrows(NullPointerException.class, () -> config.mappingRuntimeUsing((Supplier<IdleStrategy>) null));
    }

    @Test
    void unmappingRuntimeSettersInstallSupplier() {
        final AsyncRuntime runtime = mock(AsyncRuntime.class);
        assertSame(runtime, AsyncUnmappingConfigurator.configure().unmappingRuntime(runtime)
                .unmappingRuntimeSupplier().get());
        assertSame(AsyncRuntimeInstances.sharedUnmappingRuntime(), AsyncUnmappingConfigurator.configure()
                .unmappingRuntime(SharingPolicy.SHARED).unmappingRuntimeSupplier().get());
        final AsyncUnmappingConfigurator config = AsyncUnmappingConfigurator.configure();
        assertThrows(NullPointerException.class, () -> config.unmappingRuntime((AsyncRuntime) null));
        assertThrows(NullPointerException.class, () -> config.unmappingRuntimeSupplier(null));
        assertThrows(NullPointerException.class, () -> config.unmappingRuntimeUsing((IdleStrategy) null));
        assertThrows(NullPointerException.class, () -> config.unmappingRuntimeUsing((Supplier<IdleStrategy>) null));
    }

    @Test
    void mappingRuntimeUsingIdleStrategyCreatesNewRuntimes() {
        assertCreatesNewRuntimes("mapper-", AsyncMappingConfigurator.configure()
                .mappingRuntimeUsing(new SleepingMillisIdleStrategy(1)).mappingRuntimeSupplier());
        assertCreatesNewRuntimes("mapper-", AsyncMappingConfigurator.configure()
                .mappingRuntimeUsing(() -> new SleepingMillisIdleStrategy(1)).mappingRuntimeSupplier());
    }

    @Test
    void unmappingRuntimeUsingIdleStrategyCreatesNewRuntimes() {
        assertCreatesNewRuntimes("unmapper-", AsyncUnmappingConfigurator.configure()
                .unmappingRuntimeUsing(new SleepingMillisIdleStrategy(1)).unmappingRuntimeSupplier());
        assertCreatesNewRuntimes("unmapper-", AsyncUnmappingConfigurator.configure()
                .unmappingRuntimeUsing(() -> new SleepingMillisIdleStrategy(1)).unmappingRuntimeSupplier());
    }

    private static void assertCreatesNewRuntimes(final String namePrefix,
                                                 final Supplier<? extends AsyncRuntime> runtimeSupplier) {
        final AsyncRuntime runtime1 = runtimeSupplier.get();
        final AsyncRuntime runtime2 = runtimeSupplier.get();
        try {
            assertNotSame(runtime1, runtime2);
            assertTrue(runtime1.isRunning());
            assertTrue(runtime1.toString().startsWith("AsyncRuntimeImpl:thread=" + namePrefix), runtime1.toString());
        } finally {
            runtime1.close();
            runtime2.close();
        }
    }

    @Test
    void toImmutableConfigCopiesResolvedValues() {
        final AsyncMappingConfig mapping = AsyncMappingConfigurator.configure()
                .regionsToMapAhead(3).aheadMappingCacheSize(8).toImmutableConfig();
        assertInstanceOf(AsyncMappingConfigImpl.class, mapping);
        assertEquals(3, mapping.regionsToMapAhead());
        assertEquals(8, mapping.aheadMappingCacheSize());
        final AsyncUnmappingConfig unmapping = AsyncUnmappingConfigurator.configure()
                .unmappingCacheSize(16).toImmutableConfig();
        assertInstanceOf(AsyncUnmappingConfigImpl.class, unmapping);
        assertEquals(16, unmapping.unmappingCacheSize());
    }

    @Test
    void toStringShowsUnresolvedValuesAndDefaults() {
        assertEquals("AsyncMappingConfiguratorImpl:regionsToMapAhead=3|aheadMappingCacheSize=-1" +
                        "|defaults=UNSET_MAPPING_DEFAULTS",
                AsyncMappingConfigurator.configure(UNSET_MAPPING_DEFAULTS).regionsToMapAhead(3).toString());
        assertEquals("AsyncUnmappingConfiguratorImpl:unmappingCacheSize=0|defaults=UNSET_UNMAPPING_DEFAULTS",
                AsyncUnmappingConfigurator.configure(UNSET_UNMAPPING_DEFAULTS).toString());
    }
}
