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

import org.junit.jupiter.api.Test;
import org.tools4j.mmap.mapping.config.MappingStrategyConfig;
import org.tools4j.mmap.queue.config.AppenderConfig;
import org.tools4j.mmap.queue.config.AppenderConfigurator;
import org.tools4j.mmap.queue.config.MappingStrategy;
import org.tools4j.mmap.queue.config.QueueConfigurations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.tools4j.mmap.queue.impl.AppenderConfigDefaults.APPENDER_CONFIG_DEFAULTS;

/**
 * Unit test for {@link AppenderConfiguratorImpl}, {@link AppenderConfigImpl} and {@link AppenderConfigDefaults}.
 */
class AppenderConfiguratorTest {

    static final MappingStrategyConfig STRATEGY_A = MappingStrategyConfig.configure().cacheSize(2)
            .deferUnmapping(true).toImmutableConfig();
    static final MappingStrategyConfig STRATEGY_B = MappingStrategyConfig.configure().cacheSize(16)
            .toImmutableConfig();

    /** Defaults returning the "unset" sentinel for every value, forcing fallback to system defaults */
    private static AppenderConfig unsetDefaults() {
        return new AppenderConfig() {
            @Override
            public MappingStrategyConfig headerMappingStrategy() {
                return null;
            }

            @Override
            public MappingStrategyConfig payloadMappingStrategy() {
                return null;
            }

            @Override
            public int maxOpenHeaderFiles() {
                return 0;
            }

            @Override
            public int maxOpenPayloadFiles() {
                return 0;
            }

            @Override
            public int headerFilesToCreateAhead() {
                return -1;
            }

            @Override
            public int payloadFilesToCreateAhead() {
                return -1;
            }

            @Override
            public AppenderConfig toImmutableAppenderConfig() {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static AppenderConfig customDefaults() {
        return new AppenderConfigImpl(STRATEGY_A, STRATEGY_B, 5, 6, 1, 2);
    }

    @Test
    void unsetDefaultsFallBackToSystemDefaults() {
        final AppenderConfig config = AppenderConfigurator.configure(unsetDefaults());
        assertEquals(QueueConfigurations.defaultAppenderHeaderMappingStrategy(), config.headerMappingStrategy());
        assertEquals(QueueConfigurations.defaultAppenderPayloadMappingStrategy(), config.payloadMappingStrategy());
        assertEquals(QueueConfigurations.defaultMaxOpenAppenderHeaderFiles(), config.maxOpenHeaderFiles());
        assertEquals(QueueConfigurations.defaultMaxOpenAppenderPayloadFiles(), config.maxOpenPayloadFiles());
        assertEquals(QueueConfigurations.defaultAppenderHeaderFilesToCreateAhead(), config.headerFilesToCreateAhead());
        assertEquals(QueueConfigurations.defaultAppenderPayloadFilesToCreateAhead(),
                config.payloadFilesToCreateAhead());
    }

    @Test
    void valuesAreResolvedFromDefaults() {
        assertEquals(customDefaults(), AppenderConfigurator.configure(customDefaults()).toImmutableAppenderConfig());
    }

    @Test
    void explicitValuesOverrideDefaults() {
        final AppenderConfigurator config = AppenderConfigurator.configure(customDefaults())
                .headerMappingStrategy(STRATEGY_B)
                .payloadMappingStrategy(STRATEGY_A)
                .maxOpenHeaderFiles(7)
                .maxOpenPayloadFiles(8)
                .headerFilesToCreateAhead(0)
                .payloadFilesToCreateAhead(3);
        assertEquals(new AppenderConfigImpl(STRATEGY_B, STRATEGY_A, 7, 8, 0, 3), config.toImmutableAppenderConfig());
    }

    @Test
    void resetRestoresFallbackToDefaults() {
        final AppenderConfigurator config = AppenderConfigurator.configure(customDefaults())
                .mappingStrategy(STRATEGY_B)
                .maxOpenHeaderFiles(7)
                .maxOpenPayloadFiles(8)
                .headerFilesToCreateAhead(0)
                .payloadFilesToCreateAhead(0);
        assertEquals(new AppenderConfigImpl(STRATEGY_B, STRATEGY_B, 7, 8, 0, 0), config.toImmutableAppenderConfig());
        config.reset();
        assertEquals(customDefaults(), config.toImmutableAppenderConfig());
    }

    @Test
    void nonPositiveMaxOpenFilesResetToDefault() {
        final AppenderConfigurator config = AppenderConfigurator.configure(customDefaults())
                .maxOpenHeaderFiles(7).maxOpenPayloadFiles(8);
        config.maxOpenHeaderFiles(0).maxOpenPayloadFiles(-1);
        assertEquals(5, config.maxOpenHeaderFiles());
        assertEquals(6, config.maxOpenPayloadFiles());
    }

    @Test
    void negativeFilesToCreateAheadIsRejected() {
        final AppenderConfigurator config = AppenderConfigurator.configure();
        assertThrows(IllegalArgumentException.class, () -> config.headerFilesToCreateAhead(-1));
        assertThrows(IllegalArgumentException.class, () -> config.payloadFilesToCreateAhead(-1));
    }

    @Test
    void mappingStrategySetsHeaderAndPayload() {
        final AppenderConfigurator config = AppenderConfigurator.configure(customDefaults());
        assertSame(STRATEGY_B, config.mappingStrategy(STRATEGY_B).headerMappingStrategy());
        assertSame(STRATEGY_B, config.payloadMappingStrategy());

        config.mappingStrategy(MappingStrategy.SyncMappingStrategy);
        assertSame(MappingStrategyConfig.getDefaultSync(), config.headerMappingStrategy());
        assertSame(MappingStrategyConfig.getDefaultSync(), config.payloadMappingStrategy());

        config.headerMappingStrategy(MappingStrategy.AsyncMapAheadStrategy)
                .payloadMappingStrategy(MappingStrategy.SyncMappingStrategyWithAsyncUnmapping);
        assertSame(MappingStrategyConfig.getDefaultAsyncMapAhead(), config.headerMappingStrategy());
        assertSame(MappingStrategyConfig.getDefaultSyncWithAsyncUnmapping(), config.payloadMappingStrategy());
    }

    @Test
    void mappingStrategyConsumerModifiesResolvedStrategy() {
        final AppenderConfigurator config = AppenderConfigurator.configure(customDefaults())
                .headerMappingStrategy(cfg -> cfg.lruCacheSize(16))
                .payloadMappingStrategy(cfg -> cfg.lruCacheSize(32));
        assertMappingStrategy(STRATEGY_A, 16, config.headerMappingStrategy());
        assertMappingStrategy(STRATEGY_B, 32, config.payloadMappingStrategy());

        config.mappingStrategy(cfg -> cfg.lruCacheSize(64));
        assertMappingStrategy(STRATEGY_A, 64, config.headerMappingStrategy());
        assertMappingStrategy(STRATEGY_B, 64, config.payloadMappingStrategy());
    }

    static void assertMappingStrategy(final MappingStrategyConfig base,
                                      final int lruCacheSize,
                                      final MappingStrategyConfig actual) {
        assertEquals(base.cacheSize(), actual.cacheSize(), "cacheSize");
        assertEquals(base.deferUnmapping(), actual.deferUnmapping(), "deferUnmapping");
        assertEquals(lruCacheSize, actual.lruCacheSize(), "lruCacheSize");
    }

    @Test
    void immutableConfigIsSnapshot() {
        final AppenderConfigurator config = AppenderConfigurator.configure(customDefaults());
        final AppenderConfig immutable = config.toImmutableAppenderConfig();
        config.mappingStrategy(STRATEGY_B).maxOpenHeaderFiles(9).headerFilesToCreateAhead(4);
        assertEquals(customDefaults(), immutable);
        assertSame(immutable, immutable.toImmutableAppenderConfig());
        assertThrows(NullPointerException.class, () -> new AppenderConfigImpl(null, STRATEGY_A, 1, 1, 0, 0));
        assertThrows(NullPointerException.class, () -> new AppenderConfigImpl(STRATEGY_A, null, 1, 1, 0, 0));
    }

    @Test
    void defaultsDelegateToSystemDefaults() {
        assertSame(APPENDER_CONFIG_DEFAULTS, AppenderConfig.getDefault());
        assertInstanceOf(AppenderConfiguratorImpl.class, AppenderConfig.configure());
        assertEquals(new AppenderConfigImpl(APPENDER_CONFIG_DEFAULTS), APPENDER_CONFIG_DEFAULTS.toImmutableAppenderConfig());
        assertEquals(new AppenderConfigImpl(), AppenderConfig.configure().toImmutableAppenderConfig());
    }
}
