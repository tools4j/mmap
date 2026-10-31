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
import org.tools4j.mmap.queue.config.IndexReaderConfig;
import org.tools4j.mmap.queue.config.MappingStrategy;
import org.tools4j.mmap.queue.config.QueueConfig;
import org.tools4j.mmap.queue.config.QueueConfigurations;
import org.tools4j.mmap.queue.config.QueueConfigurator;
import org.tools4j.mmap.queue.config.ReaderConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.tools4j.mmap.mapping.impl.Constants.REGION_SIZE_GRANULARITY;
import static org.tools4j.mmap.queue.impl.AppenderConfigDefaults.APPENDER_CONFIG_DEFAULTS;
import static org.tools4j.mmap.queue.impl.AppenderConfiguratorTest.STRATEGY_A;
import static org.tools4j.mmap.queue.impl.AppenderConfiguratorTest.STRATEGY_B;
import static org.tools4j.mmap.queue.impl.AppenderConfiguratorTest.assertMappingStrategy;
import static org.tools4j.mmap.queue.impl.IndexReaderConfigDefaults.INDEX_READER_CONFIG_DEFAULTS;
import static org.tools4j.mmap.queue.impl.QueueConfigDefaults.QUEUE_CONFIG_DEFAULTS;
import static org.tools4j.mmap.queue.impl.ReaderConfigDefaults.ENTRY_ITERATOR_CONFIG_DEFAULTS;
import static org.tools4j.mmap.queue.impl.ReaderConfigDefaults.ENTRY_READER_CONFIG_DEFAULTS;
import static org.tools4j.mmap.queue.impl.ReaderConfigDefaults.POLLER_CONFIG_DEFAULTS;

/**
 * Unit test for {@link QueueConfiguratorImpl}, {@link QueueConfigImpl} and {@link QueueConfigDefaults}.
 */
class QueueConfiguratorTest {

    private static final int REGION_SIZE = 2 * (int) REGION_SIZE_GRANULARITY;
    private static final long FILE_SIZE = 4 * REGION_SIZE_GRANULARITY;

    /** Every value differs from the system defaults, and header from payload values */
    private static QueueConfig customDefaults() {
        return new QueueConfigImpl(5, FILE_SIZE, 2 * FILE_SIZE, 4 * FILE_SIZE, 8 * FILE_SIZE,
                REGION_SIZE, 2 * REGION_SIZE, true, false, false, true,
                new AppenderConfigImpl(STRATEGY_A, STRATEGY_B, 11, 12, 1, 2),
                new ReaderConfigImpl(STRATEGY_A, STRATEGY_B, 21, 22),
                new ReaderConfigImpl(STRATEGY_B, STRATEGY_A, 31, 32),
                new ReaderConfigImpl(STRATEGY_A, STRATEGY_A, 41, 42),
                new IndexReaderConfigImpl(STRATEGY_B, 51));
    }

    private static void assertQueueConfig(final QueueConfig expected, final QueueConfig actual) {
        assertEquals(expected.maxAppenders(), actual.maxAppenders(), "maxAppenders");
        assertEquals(expected.minHeaderFileSize(), actual.minHeaderFileSize(), "minHeaderFileSize");
        assertEquals(expected.maxHeaderFileSize(), actual.maxHeaderFileSize(), "maxHeaderFileSize");
        assertEquals(expected.minPayloadFileSize(), actual.minPayloadFileSize(), "minPayloadFileSize");
        assertEquals(expected.maxPayloadFileSize(), actual.maxPayloadFileSize(), "maxPayloadFileSize");
        assertEquals(expected.headerRegionSize(), actual.headerRegionSize(), "headerRegionSize");
        assertEquals(expected.payloadRegionSize(), actual.payloadRegionSize(), "payloadRegionSize");
        assertEquals(expected.expandHeaderFile(), actual.expandHeaderFile(), "expandHeaderFile");
        assertEquals(expected.expandPayloadFiles(), actual.expandPayloadFiles(), "expandPayloadFiles");
        assertEquals(expected.rollHeaderFile(), actual.rollHeaderFile(), "rollHeaderFile");
        assertEquals(expected.rollPayloadFiles(), actual.rollPayloadFiles(), "rollPayloadFiles");
        assertEquals(expected.appenderConfig().toImmutableAppenderConfig(),
                actual.appenderConfig().toImmutableAppenderConfig(), "appenderConfig");
        assertEquals(expected.pollerConfig().toImmutableReaderConfig(),
                actual.pollerConfig().toImmutableReaderConfig(), "pollerConfig");
        assertEquals(expected.entryReaderConfig().toImmutableReaderConfig(),
                actual.entryReaderConfig().toImmutableReaderConfig(), "entryReaderConfig");
        assertEquals(expected.entryIteratorConfig().toImmutableReaderConfig(),
                actual.entryIteratorConfig().toImmutableReaderConfig(), "entryIteratorConfig");
        assertEquals(expected.indexReaderConfig().toImmutableIndexReaderConfig(),
                actual.indexReaderConfig().toImmutableIndexReaderConfig(), "indexReaderConfig");
    }

    @Test
    void unsetValuesResolveToSystemDefaults() {
        final QueueConfig config = QueueConfig.configure();
        assertEquals(QueueConfigurations.defaultMaxAppenders(), config.maxAppenders());
        assertEquals(QueueConfigurations.defaultMinHeaderFileSize(), config.minHeaderFileSize());
        assertEquals(QueueConfigurations.defaultMaxHeaderFileSize(), config.maxHeaderFileSize());
        assertEquals(QueueConfigurations.defaultMinPayloadFileSize(), config.minPayloadFileSize());
        assertEquals(QueueConfigurations.defaultMaxPayloadFileSize(), config.maxPayloadFileSize());
        assertEquals(QueueConfigurations.defaultHeaderRegionSize(), config.headerRegionSize());
        assertEquals(QueueConfigurations.defaultPayloadRegionSize(), config.payloadRegionSize());
        assertEquals(QueueConfigurations.defaultExpandHeaderFile(), config.expandHeaderFile());
        assertEquals(QueueConfigurations.defaultExpandPayloadFiles(), config.expandPayloadFiles());
        assertEquals(QueueConfigurations.defaultRollHeaderFile(), config.rollHeaderFile());
        assertEquals(QueueConfigurations.defaultRollPayloadFiles(), config.rollPayloadFiles());
        assertSame(APPENDER_CONFIG_DEFAULTS, config.appenderConfig());
        assertSame(POLLER_CONFIG_DEFAULTS, config.pollerConfig());
        assertSame(ENTRY_READER_CONFIG_DEFAULTS, config.entryReaderConfig());
        assertSame(ENTRY_ITERATOR_CONFIG_DEFAULTS, config.entryIteratorConfig());
        assertSame(INDEX_READER_CONFIG_DEFAULTS, config.indexReaderConfig());

        assertQueueConfig(QUEUE_CONFIG_DEFAULTS, config);
        assertQueueConfig(QUEUE_CONFIG_DEFAULTS, QUEUE_CONFIG_DEFAULTS.toImmutableQueueConfig());
        assertQueueConfig(QUEUE_CONFIG_DEFAULTS, new QueueConfigImpl());
        assertSame(QUEUE_CONFIG_DEFAULTS, QueueConfig.getDefault());
        assertInstanceOf(QueueConfiguratorImpl.class, QueueConfig.configure(customDefaults()));
    }

    @Test
    void valuesAreResolvedFromDefaults() {
        assertQueueConfig(customDefaults(), QueueConfig.configure(customDefaults()));
    }

    @Test
    void explicitValuesOverrideDefaultsUntilReset() {
        final AppenderConfig appenderConfig = new AppenderConfigImpl(STRATEGY_B, STRATEGY_B, 13, 14, 3, 4);
        final ReaderConfig pollerConfig = new ReaderConfigImpl(STRATEGY_B, STRATEGY_B, 23, 24);
        final ReaderConfig entryReaderConfig = new ReaderConfigImpl(STRATEGY_A, STRATEGY_B, 33, 34);
        final ReaderConfig entryIteratorConfig = new ReaderConfigImpl(STRATEGY_B, STRATEGY_A, 43, 44);
        final IndexReaderConfig indexReaderConfig = new IndexReaderConfigImpl(STRATEGY_A, 53);
        final QueueConfigurator config = QueueConfig.configure(customDefaults())
                .maxAppenders(7)
                .minHeaderFileSize(0)
                .maxHeaderFileSize(FILE_SIZE)
                .minPayloadFileSize(0)
                .maxPayloadFileSize(2 * FILE_SIZE)
                .headerRegionSize(4 * REGION_SIZE)
                .payloadRegionSize(8 * REGION_SIZE)
                .expandHeaderFile(false)
                .expandPayloadFiles(true)
                .rollHeaderFile(true)
                .rollPayloadFiles(false)
                .appenderConfig(appenderConfig)
                .pollerConfig(pollerConfig)
                .entryReaderConfig(entryReaderConfig)
                .entryIteratorConfig(entryIteratorConfig)
                .indexReaderConfig(indexReaderConfig);
        assertQueueConfig(new QueueConfigImpl(7, 0, FILE_SIZE, 0, 2 * FILE_SIZE,
                4 * REGION_SIZE, 8 * REGION_SIZE, false, true, true, false,
                appenderConfig, pollerConfig, entryReaderConfig, entryIteratorConfig, indexReaderConfig), config);
        config.reset();
        assertQueueConfig(customDefaults(), config);
    }

    @Test
    void invalidValuesAreRejected() {
        final QueueConfigurator config = QueueConfig.configure();
        assertThrows(IllegalArgumentException.class, () -> config.maxAppenders(0));
        assertThrows(IllegalArgumentException.class, () -> config.maxAppenders(257));
        assertThrows(IllegalArgumentException.class, () -> config.minHeaderFileSize(-1));
        assertThrows(IllegalArgumentException.class, () -> config.minPayloadFileSize(REGION_SIZE + 1));
        assertThrows(IllegalArgumentException.class, () -> config.maxHeaderFileSize(0));
        assertThrows(IllegalArgumentException.class, () -> config.maxPayloadFileSize(3 * REGION_SIZE_GRANULARITY));
        assertThrows(IllegalArgumentException.class, () -> config.headerRegionSize(0));
        assertThrows(IllegalArgumentException.class, () -> config.payloadRegionSize(REGION_SIZE + 1));
        assertThrows(NullPointerException.class, () -> config.appenderConfig((AppenderConfig) null));
        assertThrows(NullPointerException.class, () -> config.pollerConfig((ReaderConfig) null));
        assertThrows(NullPointerException.class, () -> config.entryReaderConfig((ReaderConfig) null));
        assertThrows(NullPointerException.class, () -> config.entryIteratorConfig((ReaderConfig) null));
        assertThrows(NullPointerException.class, () -> config.indexReaderConfig((IndexReaderConfig) null));
        assertThrows(NullPointerException.class, () -> config.mappingStrategy((MappingStrategyConfig) null));
        assertThrows(NullPointerException.class, () -> config.headerMappingStrategy((MappingStrategyConfig) null));
        assertThrows(NullPointerException.class, () -> config.payloadMappingStrategy((MappingStrategyConfig) null));
        assertQueueConfig(QUEUE_CONFIG_DEFAULTS, config);
    }

    @Test
    void maxOpenFilesAndFilesToCreateAheadApplyToAllAccessorsKeepingOtherDefaults() {
        final QueueConfig defaults = customDefaults();
        final QueueConfig config = QueueConfig.configure(defaults)
                .maxOpenHeaderFiles(61)
                .maxOpenPayloadFiles(62)
                .headerFilesToCreateAhead(3)
                .payloadFilesToCreateAhead(4);
        assertEquals(new AppenderConfigImpl(STRATEGY_A, STRATEGY_B, 61, 62, 3, 4),
                config.appenderConfig().toImmutableAppenderConfig());
        assertEquals(new ReaderConfigImpl(STRATEGY_A, STRATEGY_B, 61, 62),
                config.pollerConfig().toImmutableReaderConfig());
        assertEquals(new ReaderConfigImpl(STRATEGY_B, STRATEGY_A, 61, 62),
                config.entryReaderConfig().toImmutableReaderConfig());
        assertEquals(new ReaderConfigImpl(STRATEGY_A, STRATEGY_A, 61, 62),
                config.entryIteratorConfig().toImmutableReaderConfig());
        assertEquals(new IndexReaderConfigImpl(STRATEGY_B, 61),
                config.indexReaderConfig().toImmutableIndexReaderConfig());
    }

    @Test
    void mappingStrategiesApplyToAllAccessorsKeepingOtherDefaults() {
        final MappingStrategyConfig sync = MappingStrategyConfig.getDefaultSync();
        final MappingStrategyConfig async = MappingStrategyConfig.getDefaultAsyncMapAhead();
        final QueueConfigurator config = QueueConfig.configure(customDefaults())
                .mappingStrategy(MappingStrategy.SyncMappingStrategy);
        assertEquals(new AppenderConfigImpl(sync, sync, 11, 12, 1, 2),
                config.appenderConfig().toImmutableAppenderConfig());
        assertEquals(new ReaderConfigImpl(sync, sync, 21, 22), config.pollerConfig().toImmutableReaderConfig());
        assertEquals(new ReaderConfigImpl(sync, sync, 31, 32), config.entryReaderConfig().toImmutableReaderConfig());
        assertEquals(new ReaderConfigImpl(sync, sync, 41, 42), config.entryIteratorConfig().toImmutableReaderConfig());
        assertEquals(new IndexReaderConfigImpl(sync, 51), config.indexReaderConfig().toImmutableIndexReaderConfig());

        config.headerMappingStrategy(MappingStrategy.AsyncMapAheadStrategy);
        assertEquals(new AppenderConfigImpl(async, sync, 11, 12, 1, 2),
                config.appenderConfig().toImmutableAppenderConfig());
        assertEquals(new ReaderConfigImpl(async, sync, 21, 22), config.pollerConfig().toImmutableReaderConfig());
        assertEquals(new ReaderConfigImpl(async, sync, 31, 32), config.entryReaderConfig().toImmutableReaderConfig());
        assertEquals(new ReaderConfigImpl(async, sync, 41, 42), config.entryIteratorConfig().toImmutableReaderConfig());
        assertEquals(new IndexReaderConfigImpl(async, 51), config.indexReaderConfig().toImmutableIndexReaderConfig());

        config.payloadMappingStrategy(MappingStrategy.AsyncMapAheadStrategy);
        assertEquals(new AppenderConfigImpl(async, async, 11, 12, 1, 2),
                config.appenderConfig().toImmutableAppenderConfig());
        assertEquals(new ReaderConfigImpl(async, async, 21, 22), config.pollerConfig().toImmutableReaderConfig());
        assertEquals(new ReaderConfigImpl(async, async, 31, 32), config.entryReaderConfig().toImmutableReaderConfig());
        assertEquals(new ReaderConfigImpl(async, async, 41, 42),
                config.entryIteratorConfig().toImmutableReaderConfig());
        assertEquals(new IndexReaderConfigImpl(async, 51), config.indexReaderConfig().toImmutableIndexReaderConfig());
    }

    @Test
    void mappingStrategyConsumersApplyToAllAccessors() {
        final QueueConfig config = QueueConfig.configure()
                .mappingStrategy(cfg -> cfg.lruCacheSize(16))
                .headerMappingStrategy(cfg -> cfg.lruCacheSize(32))
                .payloadMappingStrategy(cfg -> cfg.lruCacheSize(64));
        for (final MappingStrategyConfig header : new MappingStrategyConfig[] {
                config.appenderConfig().headerMappingStrategy(),
                config.pollerConfig().headerMappingStrategy(),
                config.entryReaderConfig().headerMappingStrategy(),
                config.entryIteratorConfig().headerMappingStrategy(),
                config.indexReaderConfig().headerMappingStrategy()}) {
            assertEquals(32, header.lruCacheSize());
        }
        for (final MappingStrategyConfig payload : new MappingStrategyConfig[] {
                config.appenderConfig().payloadMappingStrategy(),
                config.pollerConfig().payloadMappingStrategy(),
                config.entryReaderConfig().payloadMappingStrategy(),
                config.entryIteratorConfig().payloadMappingStrategy()}) {
            assertEquals(64, payload.lruCacheSize());
        }
    }

    @Test
    void mappingStrategyConsumersModifyEachAccessorsResolvedStrategy() {
        final QueueConfigurator config = QueueConfig.configure(customDefaults())
                .headerMappingStrategy(cfg -> cfg.lruCacheSize(32))
                .payloadMappingStrategy(cfg -> cfg.lruCacheSize(64));
        assertMappingStrategy(STRATEGY_A, 32, config.appenderConfig().headerMappingStrategy());
        assertMappingStrategy(STRATEGY_B, 64, config.appenderConfig().payloadMappingStrategy());
        assertMappingStrategy(STRATEGY_A, 32, config.pollerConfig().headerMappingStrategy());
        assertMappingStrategy(STRATEGY_B, 64, config.pollerConfig().payloadMappingStrategy());
        assertMappingStrategy(STRATEGY_B, 32, config.entryReaderConfig().headerMappingStrategy());
        assertMappingStrategy(STRATEGY_A, 64, config.entryReaderConfig().payloadMappingStrategy());
        assertMappingStrategy(STRATEGY_A, 32, config.entryIteratorConfig().headerMappingStrategy());
        assertMappingStrategy(STRATEGY_A, 64, config.entryIteratorConfig().payloadMappingStrategy());
        assertMappingStrategy(STRATEGY_B, 32, config.indexReaderConfig().headerMappingStrategy());

        config.mappingStrategy(cfg -> cfg.lruCacheSize(16));
        assertMappingStrategy(STRATEGY_A, 16, config.appenderConfig().headerMappingStrategy());
        assertMappingStrategy(STRATEGY_B, 16, config.appenderConfig().payloadMappingStrategy());
        assertMappingStrategy(STRATEGY_B, 16, config.entryReaderConfig().headerMappingStrategy());
        assertMappingStrategy(STRATEGY_A, 16, config.entryReaderConfig().payloadMappingStrategy());
        assertMappingStrategy(STRATEGY_B, 16, config.indexReaderConfig().headerMappingStrategy());
        assertEquals(51, config.indexReaderConfig().maxOpenHeaderFiles());
    }

    @Test
    void accessorConsumersStartFromKindSpecificDefaults() {
        final QueueConfig config = QueueConfig.configure()
                .appenderConfig(cfg -> cfg.maxOpenHeaderFiles(61))
                .pollerConfig(cfg -> cfg.maxOpenHeaderFiles(62))
                .entryReaderConfig(cfg -> cfg.maxOpenHeaderFiles(63))
                .entryIteratorConfig(cfg -> cfg.maxOpenHeaderFiles(64))
                .indexReaderConfig(cfg -> cfg.maxOpenHeaderFiles(65));
        assertEquals(61, config.appenderConfig().maxOpenHeaderFiles());
        assertEquals(62, config.pollerConfig().maxOpenHeaderFiles());
        assertEquals(63, config.entryReaderConfig().maxOpenHeaderFiles());
        assertEquals(64, config.entryIteratorConfig().maxOpenHeaderFiles());
        assertEquals(65, config.indexReaderConfig().maxOpenHeaderFiles());
        assertEquals(QueueConfigurations.defaultAppenderPayloadMappingStrategy(),
                config.appenderConfig().payloadMappingStrategy());
        assertEquals(QueueConfigurations.defaultPollerPayloadMappingStrategy(),
                config.pollerConfig().payloadMappingStrategy());
        assertEquals(QueueConfigurations.defaultEntryReaderPayloadMappingStrategy(),
                config.entryReaderConfig().payloadMappingStrategy());
        assertEquals(QueueConfigurations.defaultEntryIteratorPayloadMappingStrategy(),
                config.entryIteratorConfig().payloadMappingStrategy());
        assertEquals(QueueConfigurations.defaultIndexReaderHeaderMappingStrategy(),
                config.indexReaderConfig().headerMappingStrategy());
    }

    @Test
    void immutableConfigIsSnapshot() {
        final QueueConfigurator config = QueueConfig.configure(customDefaults()).maxOpenHeaderFiles(61);
        final QueueConfig immutable = config.toImmutableQueueConfig();
        assertInstanceOf(AppenderConfigImpl.class, immutable.appenderConfig());
        assertInstanceOf(ReaderConfigImpl.class, immutable.pollerConfig());
        assertInstanceOf(ReaderConfigImpl.class, immutable.entryReaderConfig());
        assertInstanceOf(ReaderConfigImpl.class, immutable.entryIteratorConfig());
        assertInstanceOf(IndexReaderConfigImpl.class, immutable.indexReaderConfig());
        assertSame(immutable, immutable.toImmutableQueueConfig());

        config.maxAppenders(9).headerRegionSize(8 * REGION_SIZE).rollHeaderFile(true).maxOpenHeaderFiles(71);
        assertEquals(5, immutable.maxAppenders());
        assertEquals(REGION_SIZE, immutable.headerRegionSize());
        assertEquals(false, immutable.rollHeaderFile());
        assertEquals(61, immutable.appenderConfig().maxOpenHeaderFiles());
        assertEquals(61, immutable.indexReaderConfig().maxOpenHeaderFiles());
    }
}
