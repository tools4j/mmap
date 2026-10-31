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
package org.tools4j.mmap.queue.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.tools4j.mmap.mapping.config.MappingConfigurations;
import org.tools4j.mmap.mapping.config.MappingStrategyConfig;
import org.tools4j.mmap.mapping.impl.MappingStrategyConfigImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tools4j.mmap.mapping.impl.Constants.REGION_SIZE_GRANULARITY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.APPENDER_HEADER_FILES_TO_CREATE_AHEAD_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.APPENDER_HEADER_FILES_TO_CREATE_AHEAD_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.APPENDER_HEADER_MAPPING_STRATEGY_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.APPENDER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.APPENDER_HEADER_REGION_CACHE_SIZE_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.APPENDER_PAYLOAD_FILES_TO_CREATE_AHEAD_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.APPENDER_PAYLOAD_FILES_TO_CREATE_AHEAD_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.APPENDER_PAYLOAD_MAPPING_STRATEGY_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.APPENDER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.APPENDER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.ENTRY_ITERATOR_HEADER_MAPPING_STRATEGY_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.ENTRY_ITERATOR_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.ENTRY_ITERATOR_HEADER_REGION_CACHE_SIZE_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.ENTRY_ITERATOR_PAYLOAD_MAPPING_STRATEGY_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.ENTRY_ITERATOR_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.ENTRY_ITERATOR_PAYLOAD_REGION_CACHE_SIZE_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.ENTRY_READER_HEADER_MAPPING_STRATEGY_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.ENTRY_READER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.ENTRY_READER_HEADER_REGION_CACHE_SIZE_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.ENTRY_READER_PAYLOAD_MAPPING_STRATEGY_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.ENTRY_READER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.ENTRY_READER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.EXPAND_HEADER_FILE_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.EXPAND_HEADER_FILE_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.EXPAND_PAYLOAD_FILES_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.EXPAND_PAYLOAD_FILES_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.HEADER_REGION_SIZE_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.INDEX_READER_HEADER_MAPPING_STRATEGY_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.INDEX_READER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.INDEX_READER_HEADER_REGION_CACHE_SIZE_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.MAX_APPENDERS_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.MAX_APPENDERS_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.MAX_HEADER_FILE_SIZE_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.MAX_HEADER_FILE_SIZE_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.MAX_OPEN_APPENDER_HEADER_FILES_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.MAX_OPEN_APPENDER_HEADER_FILES_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.MAX_OPEN_APPENDER_PAYLOAD_FILES_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.MAX_OPEN_APPENDER_PAYLOAD_FILES_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.MAX_OPEN_ENTRY_ITERATOR_HEADER_FILES_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.MAX_OPEN_ENTRY_ITERATOR_HEADER_FILES_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.MAX_OPEN_ENTRY_ITERATOR_PAYLOAD_FILES_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.MAX_OPEN_ENTRY_ITERATOR_PAYLOAD_FILES_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.MAX_OPEN_ENTRY_READER_HEADER_FILES_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.MAX_OPEN_ENTRY_READER_HEADER_FILES_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.MAX_OPEN_ENTRY_READER_PAYLOAD_FILES_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.MAX_OPEN_ENTRY_READER_PAYLOAD_FILES_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.MAX_OPEN_INDEX_READER_HEADER_FILES_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.MAX_OPEN_INDEX_READER_HEADER_FILES_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.MAX_OPEN_POLLER_HEADER_FILES_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.MAX_OPEN_POLLER_HEADER_FILES_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.MAX_OPEN_POLLER_PAYLOAD_FILES_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.MAX_OPEN_POLLER_PAYLOAD_FILES_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.MAX_PAYLOAD_FILE_SIZE_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.MAX_PAYLOAD_FILE_SIZE_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.MIN_HEADER_FILE_SIZE_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.MIN_HEADER_FILE_SIZE_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.MIN_PAYLOAD_FILE_SIZE_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.MIN_PAYLOAD_FILE_SIZE_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.PAYLOAD_REGION_SIZE_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.POLLER_HEADER_MAPPING_STRATEGY_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.POLLER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.POLLER_HEADER_REGION_CACHE_SIZE_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.POLLER_PAYLOAD_MAPPING_STRATEGY_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.POLLER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.POLLER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.ROLL_HEADER_FILE_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.ROLL_HEADER_FILE_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.ROLL_PAYLOAD_FILES_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.ROLL_PAYLOAD_FILES_PROPERTY;

/**
 * Unit test for system property handling in {@link QueueConfigurations}.
 */
class QueueConfigurationsTest {

    private final List<String> properties = new ArrayList<>();

    @AfterEach
    void clearProperties() {
        properties.forEach(System::clearProperty);
    }

    private void setProperty(final String name, final Object value) {
        properties.add(name);
        System.setProperty(name, String.valueOf(value));
    }

    private static Arguments intProperty(final String property,
                                         final IntSupplier getter,
                                         final int defaultValue,
                                         final int validValue,
                                         final String invalidValue) {
        return Arguments.of(property, getter, defaultValue, validValue, invalidValue);
    }

    static Stream<Arguments> intProperties() {
        final int regionSize = 2 * (int) REGION_SIZE_GRANULARITY;
        final int cacheSize = MappingConfigurations.defaultRegionCacheSize();
        final int toMapAhead = MappingConfigurations.defaultRegionsToMapAhead();
        return Stream.of(
                intProperty(MAX_APPENDERS_PROPERTY, QueueConfigurations::defaultMaxAppenders,
                        MAX_APPENDERS_DEFAULT, 256, "257"),
                intProperty(HEADER_REGION_SIZE_PROPERTY, QueueConfigurations::defaultHeaderRegionSize,
                        MappingConfigurations.defaultRegionSize(), regionSize, "0"),
                intProperty(PAYLOAD_REGION_SIZE_PROPERTY, QueueConfigurations::defaultPayloadRegionSize,
                        MappingConfigurations.defaultRegionSize(), regionSize, "4097"),
                intProperty(MAX_OPEN_POLLER_HEADER_FILES_PROPERTY, QueueConfigurations::defaultMaxOpenPollerHeaderFiles,
                        MAX_OPEN_POLLER_HEADER_FILES_DEFAULT, 11, "0"),
                intProperty(MAX_OPEN_POLLER_PAYLOAD_FILES_PROPERTY,
                        QueueConfigurations::defaultMaxOpenPollerPayloadFiles,
                        MAX_OPEN_POLLER_PAYLOAD_FILES_DEFAULT, 12, "-1"),
                intProperty(MAX_OPEN_ENTRY_READER_HEADER_FILES_PROPERTY,
                        QueueConfigurations::defaultMaxOpenEntryReaderHeaderFiles,
                        MAX_OPEN_ENTRY_READER_HEADER_FILES_DEFAULT, 13, "0"),
                intProperty(MAX_OPEN_ENTRY_READER_PAYLOAD_FILES_PROPERTY,
                        QueueConfigurations::defaultMaxOpenEntryReaderPayloadFiles,
                        MAX_OPEN_ENTRY_READER_PAYLOAD_FILES_DEFAULT, 14, "0"),
                intProperty(MAX_OPEN_ENTRY_ITERATOR_HEADER_FILES_PROPERTY,
                        QueueConfigurations::defaultMaxOpenEntryIteratorHeaderFiles,
                        MAX_OPEN_ENTRY_ITERATOR_HEADER_FILES_DEFAULT, 15, "0"),
                intProperty(MAX_OPEN_ENTRY_ITERATOR_PAYLOAD_FILES_PROPERTY,
                        QueueConfigurations::defaultMaxOpenEntryIteratorPayloadFiles,
                        MAX_OPEN_ENTRY_ITERATOR_PAYLOAD_FILES_DEFAULT, 16, "0"),
                intProperty(MAX_OPEN_INDEX_READER_HEADER_FILES_PROPERTY,
                        QueueConfigurations::defaultMaxOpenIndexReaderHeaderFiles,
                        MAX_OPEN_INDEX_READER_HEADER_FILES_DEFAULT, 17, "0"),
                intProperty(MAX_OPEN_APPENDER_HEADER_FILES_PROPERTY,
                        QueueConfigurations::defaultMaxOpenAppenderHeaderFiles,
                        MAX_OPEN_APPENDER_HEADER_FILES_DEFAULT, 18, "0"),
                intProperty(MAX_OPEN_APPENDER_PAYLOAD_FILES_PROPERTY,
                        QueueConfigurations::defaultMaxOpenAppenderPayloadFiles,
                        MAX_OPEN_APPENDER_PAYLOAD_FILES_DEFAULT, 19, "0"),
                intProperty(APPENDER_HEADER_FILES_TO_CREATE_AHEAD_PROPERTY,
                        QueueConfigurations::defaultAppenderHeaderFilesToCreateAhead,
                        APPENDER_HEADER_FILES_TO_CREATE_AHEAD_DEFAULT, 1, "-1"),
                intProperty(APPENDER_PAYLOAD_FILES_TO_CREATE_AHEAD_PROPERTY,
                        QueueConfigurations::defaultAppenderPayloadFilesToCreateAhead,
                        APPENDER_PAYLOAD_FILES_TO_CREATE_AHEAD_DEFAULT, 2, "-1"),
                intProperty(POLLER_HEADER_REGION_CACHE_SIZE_PROPERTY,
                        QueueConfigurations::defaultPollerHeaderRegionCacheSize, cacheSize, 32, "3"),
                intProperty(POLLER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY,
                        QueueConfigurations::defaultPollerHeaderRegionsToMapAhead, toMapAhead, 21, "0"),
                intProperty(POLLER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY,
                        QueueConfigurations::defaultPollerPayloadRegionCacheSize, cacheSize, 64, "3"),
                intProperty(POLLER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY,
                        QueueConfigurations::defaultPollerPayloadRegionsToMapAhead, toMapAhead, 22, "0"),
                intProperty(ENTRY_READER_HEADER_REGION_CACHE_SIZE_PROPERTY,
                        QueueConfigurations::defaultEntryReaderHeaderRegionCacheSize, cacheSize, 128, "3"),
                intProperty(ENTRY_READER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY,
                        QueueConfigurations::defaultEntryReaderHeaderRegionsToMapAhead, toMapAhead, 23, "0"),
                intProperty(ENTRY_READER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY,
                        QueueConfigurations::defaultEntryReaderPayloadRegionCacheSize, cacheSize, 256, "3"),
                intProperty(ENTRY_READER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY,
                        QueueConfigurations::defaultEntryReaderPayloadRegionsToMapAhead, toMapAhead, 24, "0"),
                intProperty(ENTRY_ITERATOR_HEADER_REGION_CACHE_SIZE_PROPERTY,
                        QueueConfigurations::defaultEntryIteratorHeaderRegionCacheSize, cacheSize, 512, "3"),
                intProperty(ENTRY_ITERATOR_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY,
                        QueueConfigurations::defaultEntryIteratorHeaderRegionsToMapAhead, toMapAhead, 25, "0"),
                intProperty(ENTRY_ITERATOR_PAYLOAD_REGION_CACHE_SIZE_PROPERTY,
                        QueueConfigurations::defaultEntryIteratorPayloadRegionCacheSize, cacheSize, 1024, "3"),
                intProperty(ENTRY_ITERATOR_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY,
                        QueueConfigurations::defaultEntryIteratorPayloadRegionsToMapAhead, toMapAhead, 26, "0"),
                intProperty(INDEX_READER_HEADER_REGION_CACHE_SIZE_PROPERTY,
                        QueueConfigurations::defaultIndexReaderHeaderRegionCacheSize, cacheSize, 2048, "3"),
                intProperty(INDEX_READER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY,
                        QueueConfigurations::defaultIndexReaderHeaderRegionsToMapAhead, toMapAhead, 27, "0"),
                intProperty(APPENDER_HEADER_REGION_CACHE_SIZE_PROPERTY,
                        QueueConfigurations::defaultAppenderHeaderRegionCacheSize, cacheSize, 4096, "3"),
                intProperty(APPENDER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY,
                        QueueConfigurations::defaultAppenderHeaderRegionsToMapAhead, toMapAhead, 28, "0"),
                intProperty(APPENDER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY,
                        QueueConfigurations::defaultAppenderPayloadRegionCacheSize, cacheSize, 8192, "3"),
                intProperty(APPENDER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY,
                        QueueConfigurations::defaultAppenderPayloadRegionsToMapAhead, toMapAhead, 29, "0")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("intProperties")
    void intPropertyIsParsedAndValidated(final String property,
                                         final IntSupplier getter,
                                         final int defaultValue,
                                         final int validValue,
                                         final String invalidValue) {
        assertEquals(defaultValue, getter.getAsInt());
        setProperty(property, validValue);
        assertEquals(validValue, getter.getAsInt());
        setProperty(property, invalidValue);
        assertThrows(IllegalArgumentException.class, getter::getAsInt);
        setProperty(property, "one");
        assertThrows(IllegalArgumentException.class, getter::getAsInt);
    }

    static Stream<Arguments> longProperties() {
        final long fileSize = 4 * REGION_SIZE_GRANULARITY;
        return Stream.of(
                Arguments.of(MIN_HEADER_FILE_SIZE_PROPERTY,
                        (LongSupplier) QueueConfigurations::defaultMinHeaderFileSize,
                        MIN_HEADER_FILE_SIZE_DEFAULT, 0L, "-1"),
                Arguments.of(MIN_PAYLOAD_FILE_SIZE_PROPERTY,
                        (LongSupplier) QueueConfigurations::defaultMinPayloadFileSize,
                        MIN_PAYLOAD_FILE_SIZE_DEFAULT, fileSize, "3"),
                Arguments.of(MAX_HEADER_FILE_SIZE_PROPERTY,
                        (LongSupplier) QueueConfigurations::defaultMaxHeaderFileSize,
                        MAX_HEADER_FILE_SIZE_DEFAULT, 2 * fileSize, "0"),
                Arguments.of(MAX_PAYLOAD_FILE_SIZE_PROPERTY,
                        (LongSupplier) QueueConfigurations::defaultMaxPayloadFileSize,
                        MAX_PAYLOAD_FILE_SIZE_DEFAULT, 1L << 40, "5")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("longProperties")
    void longPropertyIsParsedAndValidated(final String property,
                                          final LongSupplier getter,
                                          final long defaultValue,
                                          final long validValue,
                                          final String invalidValue) {
        assertEquals(defaultValue, getter.getAsLong());
        setProperty(property, validValue);
        assertEquals(validValue, getter.getAsLong());
        setProperty(property, invalidValue);
        assertThrows(IllegalArgumentException.class, getter::getAsLong);
        setProperty(property, "1MB");
        assertThrows(IllegalArgumentException.class, getter::getAsLong);
    }

    static Stream<Arguments> booleanProperties() {
        return Stream.of(
                Arguments.of(EXPAND_HEADER_FILE_PROPERTY,
                        (BooleanSupplier) QueueConfigurations::defaultExpandHeaderFile, EXPAND_HEADER_FILE_DEFAULT),
                Arguments.of(EXPAND_PAYLOAD_FILES_PROPERTY,
                        (BooleanSupplier) QueueConfigurations::defaultExpandPayloadFiles, EXPAND_PAYLOAD_FILES_DEFAULT),
                Arguments.of(ROLL_HEADER_FILE_PROPERTY,
                        (BooleanSupplier) QueueConfigurations::defaultRollHeaderFile, ROLL_HEADER_FILE_DEFAULT),
                Arguments.of(ROLL_PAYLOAD_FILES_PROPERTY,
                        (BooleanSupplier) QueueConfigurations::defaultRollPayloadFiles, ROLL_PAYLOAD_FILES_DEFAULT)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("booleanProperties")
    void booleanPropertyAcceptsTrueAndFalseIgnoringCase(final String property,
                                                        final BooleanSupplier getter,
                                                        final boolean defaultValue) {
        assertEquals(defaultValue, getter.getAsBoolean());
        setProperty(property, "FALSE");
        assertFalse(getter.getAsBoolean());
        setProperty(property, "True");
        assertTrue(getter.getAsBoolean());
        setProperty(property, "ture");
        assertThrows(IllegalArgumentException.class, getter::getAsBoolean);
    }

    static Stream<Arguments> mappingStrategies() {
        return Stream.of(
                Arguments.of("poller header", (Supplier<MappingStrategyConfig>)
                        QueueConfigurations::defaultPollerHeaderMappingStrategy,
                        POLLER_HEADER_MAPPING_STRATEGY_DEFAULT),
                Arguments.of("poller payload", (Supplier<MappingStrategyConfig>)
                        QueueConfigurations::defaultPollerPayloadMappingStrategy,
                        POLLER_PAYLOAD_MAPPING_STRATEGY_DEFAULT),
                Arguments.of("entry reader header", (Supplier<MappingStrategyConfig>)
                        QueueConfigurations::defaultEntryReaderHeaderMappingStrategy,
                        ENTRY_READER_HEADER_MAPPING_STRATEGY_DEFAULT),
                Arguments.of("entry reader payload", (Supplier<MappingStrategyConfig>)
                        QueueConfigurations::defaultEntryReaderPayloadMappingStrategy,
                        ENTRY_READER_PAYLOAD_MAPPING_STRATEGY_DEFAULT),
                Arguments.of("entry iterator header", (Supplier<MappingStrategyConfig>)
                        QueueConfigurations::defaultEntryIteratorHeaderMappingStrategy,
                        ENTRY_ITERATOR_HEADER_MAPPING_STRATEGY_DEFAULT),
                Arguments.of("entry iterator payload", (Supplier<MappingStrategyConfig>)
                        QueueConfigurations::defaultEntryIteratorPayloadMappingStrategy,
                        ENTRY_ITERATOR_PAYLOAD_MAPPING_STRATEGY_DEFAULT),
                Arguments.of("index reader header", (Supplier<MappingStrategyConfig>)
                        QueueConfigurations::defaultIndexReaderHeaderMappingStrategy,
                        INDEX_READER_HEADER_MAPPING_STRATEGY_DEFAULT),
                Arguments.of("appender header", (Supplier<MappingStrategyConfig>)
                        QueueConfigurations::defaultAppenderHeaderMappingStrategy,
                        APPENDER_HEADER_MAPPING_STRATEGY_DEFAULT),
                Arguments.of("appender payload", (Supplier<MappingStrategyConfig>)
                        QueueConfigurations::defaultAppenderPayloadMappingStrategy,
                        APPENDER_PAYLOAD_MAPPING_STRATEGY_DEFAULT)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mappingStrategies")
    void defaultMappingStrategyIsImmutableAndBasedOnStrategyDefault(final String name,
                                                                    final Supplier<MappingStrategyConfig> getter,
                                                                    final MappingStrategy strategyDefault) {
        final MappingStrategyConfig config = getter.get();
        assertInstanceOf(MappingStrategyConfigImpl.class, config);
        assertSame(config, getter.get());
        assertTrue(config.deferUnmapping());
        assertEquals(strategyDefault.mappingStrategyConfig().asyncUnmapping().isPresent(),
                config.asyncUnmapping().isPresent());
    }

    @Test
    void mappingStrategyEnumMapsToMappingStrategyConfigDefaults() {
        assertSame(MappingStrategyConfig.getDefaultSync(), MappingStrategy.SyncMappingStrategy.mappingStrategyConfig());
        assertSame(MappingStrategyConfig.getDefaultSyncWithAsyncUnmapping(),
                MappingStrategy.SyncMappingStrategyWithAsyncUnmapping.mappingStrategyConfig());
        assertSame(MappingStrategyConfig.getDefaultAsyncMapAhead(),
                MappingStrategy.AsyncMapAheadStrategy.mappingStrategyConfig());
    }
}
