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
import static org.tools4j.mmap.queue.config.QueueConfigurations.APPENDER_HEADER_MAPPING_STRATEGY_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.APPENDER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.APPENDER_HEADER_REGION_CACHE_SIZE_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.APPENDER_PAYLOAD_FILES_TO_CREATE_AHEAD_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.APPENDER_PAYLOAD_FILES_TO_CREATE_AHEAD_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.APPENDER_PAYLOAD_MAPPING_STRATEGY_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.APPENDER_PAYLOAD_MAPPING_STRATEGY_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.APPENDER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.APPENDER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.ENTRY_ITERATOR_HEADER_MAPPING_STRATEGY_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.ENTRY_ITERATOR_HEADER_MAPPING_STRATEGY_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.ENTRY_ITERATOR_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.ENTRY_ITERATOR_HEADER_REGION_CACHE_SIZE_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.ENTRY_ITERATOR_PAYLOAD_MAPPING_STRATEGY_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.ENTRY_ITERATOR_PAYLOAD_MAPPING_STRATEGY_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.ENTRY_ITERATOR_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.ENTRY_ITERATOR_PAYLOAD_REGION_CACHE_SIZE_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.ENTRY_READER_HEADER_MAPPING_STRATEGY_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.ENTRY_READER_HEADER_MAPPING_STRATEGY_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.ENTRY_READER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.ENTRY_READER_HEADER_REGION_CACHE_SIZE_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.ENTRY_READER_PAYLOAD_MAPPING_STRATEGY_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.ENTRY_READER_PAYLOAD_MAPPING_STRATEGY_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.ENTRY_READER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.ENTRY_READER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.EXPAND_HEADER_FILE_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.EXPAND_HEADER_FILE_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.EXPAND_PAYLOAD_FILES_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.EXPAND_PAYLOAD_FILES_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.HEADER_REGION_SIZE_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.INDEX_READER_HEADER_MAPPING_STRATEGY_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.INDEX_READER_HEADER_MAPPING_STRATEGY_PROPERTY;
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
import static org.tools4j.mmap.queue.config.QueueConfigurations.POLLER_HEADER_MAPPING_STRATEGY_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.POLLER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.POLLER_HEADER_REGION_CACHE_SIZE_PROPERTY;
import static org.tools4j.mmap.queue.config.QueueConfigurations.POLLER_PAYLOAD_MAPPING_STRATEGY_DEFAULT;
import static org.tools4j.mmap.queue.config.QueueConfigurations.POLLER_PAYLOAD_MAPPING_STRATEGY_PROPERTY;
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

    private static Arguments mappingStrategy(final String name,
                                             final Supplier<MappingStrategyConfig> getter,
                                             final String strategyProperty,
                                             final MappingStrategy strategyDefault,
                                             final String cacheSizeProperty,
                                             final String regionsToMapAheadProperty) {
        return Arguments.of(name, getter, strategyProperty, strategyDefault, cacheSizeProperty,
                regionsToMapAheadProperty);
    }

    static Stream<Arguments> mappingStrategies() {
        return Stream.of(
                mappingStrategy("poller header", QueueConfigurations::defaultPollerHeaderMappingStrategy,
                        POLLER_HEADER_MAPPING_STRATEGY_PROPERTY, POLLER_HEADER_MAPPING_STRATEGY_DEFAULT,
                        POLLER_HEADER_REGION_CACHE_SIZE_PROPERTY, POLLER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY),
                mappingStrategy("poller payload", QueueConfigurations::defaultPollerPayloadMappingStrategy,
                        POLLER_PAYLOAD_MAPPING_STRATEGY_PROPERTY, POLLER_PAYLOAD_MAPPING_STRATEGY_DEFAULT,
                        POLLER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY, POLLER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY),
                mappingStrategy("entry reader header", QueueConfigurations::defaultEntryReaderHeaderMappingStrategy,
                        ENTRY_READER_HEADER_MAPPING_STRATEGY_PROPERTY, ENTRY_READER_HEADER_MAPPING_STRATEGY_DEFAULT,
                        ENTRY_READER_HEADER_REGION_CACHE_SIZE_PROPERTY, ENTRY_READER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY),
                mappingStrategy("entry reader payload", QueueConfigurations::defaultEntryReaderPayloadMappingStrategy,
                        ENTRY_READER_PAYLOAD_MAPPING_STRATEGY_PROPERTY, ENTRY_READER_PAYLOAD_MAPPING_STRATEGY_DEFAULT,
                        ENTRY_READER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY, ENTRY_READER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY),
                mappingStrategy("entry iterator header", QueueConfigurations::defaultEntryIteratorHeaderMappingStrategy,
                        ENTRY_ITERATOR_HEADER_MAPPING_STRATEGY_PROPERTY, ENTRY_ITERATOR_HEADER_MAPPING_STRATEGY_DEFAULT,
                        ENTRY_ITERATOR_HEADER_REGION_CACHE_SIZE_PROPERTY, ENTRY_ITERATOR_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY),
                mappingStrategy("entry iterator payload", QueueConfigurations::defaultEntryIteratorPayloadMappingStrategy,
                        ENTRY_ITERATOR_PAYLOAD_MAPPING_STRATEGY_PROPERTY, ENTRY_ITERATOR_PAYLOAD_MAPPING_STRATEGY_DEFAULT,
                        ENTRY_ITERATOR_PAYLOAD_REGION_CACHE_SIZE_PROPERTY, ENTRY_ITERATOR_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY),
                mappingStrategy("index reader header", QueueConfigurations::defaultIndexReaderHeaderMappingStrategy,
                        INDEX_READER_HEADER_MAPPING_STRATEGY_PROPERTY, INDEX_READER_HEADER_MAPPING_STRATEGY_DEFAULT,
                        INDEX_READER_HEADER_REGION_CACHE_SIZE_PROPERTY, INDEX_READER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY),
                mappingStrategy("appender header", QueueConfigurations::defaultAppenderHeaderMappingStrategy,
                        APPENDER_HEADER_MAPPING_STRATEGY_PROPERTY, APPENDER_HEADER_MAPPING_STRATEGY_DEFAULT,
                        APPENDER_HEADER_REGION_CACHE_SIZE_PROPERTY, APPENDER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY),
                mappingStrategy("appender payload", QueueConfigurations::defaultAppenderPayloadMappingStrategy,
                        APPENDER_PAYLOAD_MAPPING_STRATEGY_PROPERTY, APPENDER_PAYLOAD_MAPPING_STRATEGY_DEFAULT,
                        APPENDER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY, APPENDER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY)
        );
    }

    private static void assertStrategy(final MappingStrategy expected, final MappingStrategyConfig actual) {
        assertInstanceOf(MappingStrategyConfigImpl.class, actual);
        assertTrue(actual.deferUnmapping(), "deferUnmapping");
        assertEquals(expected.mappingStrategyConfig().asyncMapping().isPresent(), actual.asyncMapping().isPresent(),
                "asyncMapping");
        assertEquals(expected.mappingStrategyConfig().asyncUnmapping().isPresent(),
                actual.asyncUnmapping().isPresent(), "asyncUnmapping");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mappingStrategies")
    void defaultMappingStrategyIsBasedOnStrategyDefault(final String name,
                                                        final Supplier<MappingStrategyConfig> getter,
                                                        final String strategyProperty,
                                                        final MappingStrategy strategyDefault,
                                                        final String cacheSizeProperty,
                                                        final String regionsToMapAheadProperty) {
        final MappingStrategyConfig config = getter.get();
        assertStrategy(strategyDefault, config);
        assertEquals(MappingConfigurations.defaultRegionCacheSize(), config.cacheSize());
        assertEquals(config, getter.get());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mappingStrategies")
    void mappingStrategyPropertySelectsStrategy(final String name,
                                                final Supplier<MappingStrategyConfig> getter,
                                                final String strategyProperty,
                                                final MappingStrategy strategyDefault,
                                                final String cacheSizeProperty,
                                                final String regionsToMapAheadProperty) {
        for (final MappingStrategy strategy : MappingStrategy.values()) {
            setProperty(strategyProperty, strategy.name());
            assertStrategy(strategy, getter.get());
        }
        setProperty(strategyProperty, "AsyncMapAhead");
        assertThrows(IllegalArgumentException.class, getter::get);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mappingStrategies")
    void cacheSizeAndRegionsToMapAheadPropertiesAreApplied(final String name,
                                                           final Supplier<MappingStrategyConfig> getter,
                                                           final String strategyProperty,
                                                           final MappingStrategy strategyDefault,
                                                           final String cacheSizeProperty,
                                                           final String regionsToMapAheadProperty) {
        setProperty(cacheSizeProperty, 64);
        setProperty(regionsToMapAheadProperty, 7);
        setProperty(strategyProperty, MappingStrategy.AsyncMapAheadStrategy.name());
        final MappingStrategyConfig async = getter.get();
        assertEquals(64, async.cacheSize());
        assertEquals(7, async.asyncMapping().orElseThrow().regionsToMapAhead());

        setProperty(strategyProperty, MappingStrategy.SyncMappingStrategyWithAsyncUnmapping.name());
        final MappingStrategyConfig sync = getter.get();
        assertEquals(64, sync.cacheSize());
        assertFalse(sync.asyncMapping().isPresent(), "regions to map ahead ignored for sync mapping");
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
