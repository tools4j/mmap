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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.tools4j.mmap.mapping.config.MappingStrategyConfig;
import org.tools4j.mmap.queue.config.MappingStrategy;
import org.tools4j.mmap.queue.config.QueueConfigurations;
import org.tools4j.mmap.queue.config.ReaderConfig;
import org.tools4j.mmap.queue.config.ReaderConfigurator;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.tools4j.mmap.queue.impl.AppenderConfiguratorTest.STRATEGY_A;
import static org.tools4j.mmap.queue.impl.AppenderConfiguratorTest.STRATEGY_B;
import static org.tools4j.mmap.queue.impl.AppenderConfiguratorTest.assertMappingStrategy;
import static org.tools4j.mmap.queue.impl.ReaderConfigDefaults.ENTRY_ITERATOR_CONFIG_DEFAULTS;
import static org.tools4j.mmap.queue.impl.ReaderConfigDefaults.ENTRY_READER_CONFIG_DEFAULTS;
import static org.tools4j.mmap.queue.impl.ReaderConfigDefaults.POLLER_CONFIG_DEFAULTS;

/**
 * Unit test for {@link ReaderConfiguratorImpl}, {@link ReaderConfigImpl} and {@link ReaderConfigDefaults}.
 */
class ReaderConfiguratorTest {

    /** Reader kind with its configurator factory and expected defaults */
    enum Kind {
        POLLER(ReaderConfig::configurePoller, ReaderConfig::getDefaultPollerConfig, POLLER_CONFIG_DEFAULTS,
                new ReaderConfigImpl(
                        QueueConfigurations.defaultPollerHeaderMappingStrategy(),
                        QueueConfigurations.defaultPollerPayloadMappingStrategy(),
                        QueueConfigurations.defaultMaxOpenPollerHeaderFiles(),
                        QueueConfigurations.defaultMaxOpenPollerPayloadFiles())),
        ENTRY_READER(ReaderConfig::configureEntryReader, ReaderConfig::getDefaultEntryReaderConfig,
                ENTRY_READER_CONFIG_DEFAULTS, new ReaderConfigImpl(
                        QueueConfigurations.defaultEntryReaderHeaderMappingStrategy(),
                        QueueConfigurations.defaultEntryReaderPayloadMappingStrategy(),
                        QueueConfigurations.defaultMaxOpenEntryReaderHeaderFiles(),
                        QueueConfigurations.defaultMaxOpenEntryReaderPayloadFiles())),
        ENTRY_ITERATOR(ReaderConfig::configureEntryIterator, ReaderConfig::getDefaultEntryIteratorConfig,
                ENTRY_ITERATOR_CONFIG_DEFAULTS, new ReaderConfigImpl(
                        QueueConfigurations.defaultEntryIteratorHeaderMappingStrategy(),
                        QueueConfigurations.defaultEntryIteratorPayloadMappingStrategy(),
                        QueueConfigurations.defaultMaxOpenEntryIteratorHeaderFiles(),
                        QueueConfigurations.defaultMaxOpenEntryIteratorPayloadFiles()));

        final Supplier<ReaderConfigurator> configurator;
        final Supplier<ReaderConfig> defaultConfig;
        final ReaderConfigDefaults defaults;
        final ReaderConfig expected;

        Kind(final Supplier<ReaderConfigurator> configurator,
             final Supplier<ReaderConfig> defaultConfig,
             final ReaderConfigDefaults defaults,
             final ReaderConfig expected) {
            this.configurator = configurator;
            this.defaultConfig = defaultConfig;
            this.defaults = defaults;
            this.expected = expected;
        }
    }

    /** Defaults returning the "unset" sentinel for every value, forcing fallback to system defaults */
    private static ReaderConfig unsetDefaults() {
        return new ReaderConfig() {
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
            public ReaderConfig toImmutableReaderConfig() {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static ReaderConfig customDefaults() {
        return new ReaderConfigImpl(STRATEGY_A, STRATEGY_B, 5, 6);
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void unsetValuesResolveToKindSpecificDefaults(final Kind kind) {
        assertEquals(kind.expected, kind.configurator.get().toImmutableReaderConfig());
        assertEquals(kind.expected, kind.defaults.toImmutableReaderConfig());
        assertSame(kind.defaults, kind.defaultConfig.get());
    }

    @Test
    void unsetDefaultsFallBackToEntryReaderSystemDefaults() {
        assertEquals(Kind.ENTRY_READER.expected,
                ReaderConfigurator.configure(unsetDefaults()).toImmutableReaderConfig());
    }

    @Test
    void valuesAreResolvedFromDefaults() {
        assertEquals(customDefaults(), ReaderConfigurator.configure(customDefaults()).toImmutableReaderConfig());
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void explicitValuesOverrideDefaultsUntilReset(final Kind kind) {
        final ReaderConfigurator config = kind.configurator.get()
                .headerMappingStrategy(STRATEGY_B)
                .payloadMappingStrategy(STRATEGY_A)
                .maxOpenHeaderFiles(7)
                .maxOpenPayloadFiles(8);
        assertEquals(new ReaderConfigImpl(STRATEGY_B, STRATEGY_A, 7, 8), config.toImmutableReaderConfig());
        config.reset();
        assertEquals(kind.expected, config.toImmutableReaderConfig());
    }

    @Test
    void nonPositiveMaxOpenFilesResetToDefault() {
        final ReaderConfigurator config = ReaderConfigurator.configure(customDefaults())
                .maxOpenHeaderFiles(7).maxOpenPayloadFiles(8);
        config.maxOpenHeaderFiles(0).maxOpenPayloadFiles(-1);
        assertEquals(5, config.maxOpenHeaderFiles());
        assertEquals(6, config.maxOpenPayloadFiles());
    }

    @Test
    void mappingStrategySetsHeaderAndPayload() {
        final ReaderConfigurator config = ReaderConfigurator.configure(customDefaults());
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
        final ReaderConfigurator config = ReaderConfigurator.configure(customDefaults())
                .headerMappingStrategy(cfg -> cfg.lruCacheSize(16))
                .payloadMappingStrategy(cfg -> cfg.lruCacheSize(32));
        assertMappingStrategy(STRATEGY_A, 16, config.headerMappingStrategy());
        assertMappingStrategy(STRATEGY_B, 32, config.payloadMappingStrategy());

        config.mappingStrategy(cfg -> cfg.lruCacheSize(64));
        assertMappingStrategy(STRATEGY_A, 64, config.headerMappingStrategy());
        assertMappingStrategy(STRATEGY_B, 64, config.payloadMappingStrategy());
    }

    @Test
    void immutableConfigIsSnapshot() {
        final ReaderConfigurator config = ReaderConfigurator.configure(customDefaults());
        final ReaderConfig immutable = config.toImmutableReaderConfig();
        config.mappingStrategy(STRATEGY_B).maxOpenHeaderFiles(9);
        assertEquals(customDefaults(), immutable);
        assertSame(immutable, immutable.toImmutableReaderConfig());
        assertThrows(NullPointerException.class, () -> new ReaderConfigImpl(null, STRATEGY_A, 1, 1));
        assertThrows(NullPointerException.class, () -> new ReaderConfigImpl(STRATEGY_A, null, 1, 1));
        assertThrows(NullPointerException.class, () -> ReaderConfigurator.configure(null));
    }
}
