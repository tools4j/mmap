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

import org.tools4j.mmap.mapping.config.MappingConfigurations;
import org.tools4j.mmap.mapping.config.MappingStrategyConfig;
import org.tools4j.mmap.mapping.config.MappingStrategyConfigurator;
import org.tools4j.mmap.mapping.impl.Constraints;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongConsumer;

import static org.tools4j.mmap.queue.config.MappingStrategy.AsyncMapAheadStrategy;
import static org.tools4j.mmap.queue.config.MappingStrategy.SyncMappingStrategyWithAsyncUnmapping;
import static org.tools4j.mmap.queue.impl.AppenderConfigDefaults.APPENDER_CONFIG_DEFAULTS;
import static org.tools4j.mmap.queue.impl.IndexReaderConfigDefaults.INDEX_READER_CONFIG_DEFAULTS;
import static org.tools4j.mmap.queue.impl.QueueConfigDefaults.QUEUE_CONFIG_DEFAULTS;
import static org.tools4j.mmap.queue.impl.ReaderConfigDefaults.ENTRY_ITERATOR_CONFIG_DEFAULTS;
import static org.tools4j.mmap.queue.impl.ReaderConfigDefaults.ENTRY_READER_CONFIG_DEFAULTS;
import static org.tools4j.mmap.queue.impl.ReaderConfigDefaults.POLLER_CONFIG_DEFAULTS;

/**
 * Defines queue default configuration values and property constants to override default values via system properties.
 */
public enum QueueConfigurations {
    ;
    public static final int MAX_OPEN_FILES_DEFAULT = 3;//up to 3 threads: requestor/async-mapper/async-unmapper

    public static final String MAX_APPENDERS_PROPERTY = "mmap.queue.maxAppenders";
    public static final int MAX_APPENDERS_DEFAULT = 64;
    public static final String MIN_HEADER_FILE_SIZE_PROPERTY = "mmap.queue.minHeaderFileSize";
    public static final long MIN_HEADER_FILE_SIZE_DEFAULT = 16*1024*1024L;
    public static final String MIN_PAYLOAD_FILE_SIZE_PROPERTY = "mmap.queue.minPayloadFileSize";
    public static final long MIN_PAYLOAD_FILE_SIZE_DEFAULT = 64*1024*1024L;
    public static final String MAX_HEADER_FILE_SIZE_PROPERTY = "mmap.queue.maxHeaderFileSize";
    public static final long MAX_HEADER_FILE_SIZE_DEFAULT = 64*1024*1024L;
    public static final String MAX_PAYLOAD_FILE_SIZE_PROPERTY = "mmap.queue.maxPayloadFileSize";
    public static final long MAX_PAYLOAD_FILE_SIZE_DEFAULT = 2*1024*1024*1024L;
    public static final String HEADER_REGION_SIZE_PROPERTY = "mmap.queue.headerRegionSize";
    public static final String PAYLOAD_REGION_SIZE_PROPERTY = "mmap.queue.payloadRegionSize";
    public static final String EXPAND_HEADER_FILE_PROPERTY = "mmap.queue.expandHeaderFile";
    public static final boolean EXPAND_HEADER_FILE_DEFAULT = false;
    public static final String EXPAND_PAYLOAD_FILES_PROPERTY = "mmap.queue.expandPayloadFiles";
    public static final boolean EXPAND_PAYLOAD_FILES_DEFAULT = true;
    public static final String ROLL_HEADER_FILE_PROPERTY = "mmap.queue.rollHeaderFile";
    public static final boolean ROLL_HEADER_FILE_DEFAULT = true;
    public static final String ROLL_PAYLOAD_FILES_PROPERTY = "mmap.queue.rollPayloadFiles";
    public static final boolean ROLL_PAYLOAD_FILES_DEFAULT = true;

    public static final String MAX_OPEN_POLLER_HEADER_FILES_PROPERTY =  "mmap.queue.maxOpenPollerHeaderFiles";
    public static final int MAX_OPEN_POLLER_HEADER_FILES_DEFAULT = MAX_OPEN_FILES_DEFAULT;
    public static final String MAX_OPEN_POLLER_PAYLOAD_FILES_PROPERTY = "mmap.queue.maxOpenPollerPayloadFiles";
    public static final int MAX_OPEN_POLLER_PAYLOAD_FILES_DEFAULT = MAX_OPEN_FILES_DEFAULT;

    public static final String MAX_OPEN_ENTRY_READER_HEADER_FILES_PROPERTY =  "mmap.queue.maxOpenEntryReaderHeaderFiles";
    public static final int MAX_OPEN_ENTRY_READER_HEADER_FILES_DEFAULT = MAX_OPEN_FILES_DEFAULT;
    public static final String MAX_OPEN_ENTRY_READER_PAYLOAD_FILES_PROPERTY = "mmap.queue.maxOpenEntryReaderPayloadFiles";
    public static final int MAX_OPEN_ENTRY_READER_PAYLOAD_FILES_DEFAULT = MAX_OPEN_FILES_DEFAULT;

    public static final String MAX_OPEN_ENTRY_ITERATOR_HEADER_FILES_PROPERTY =  "mmap.queue.maxOpenEntryIteratorHeaderFiles";
    public static final int MAX_OPEN_ENTRY_ITERATOR_HEADER_FILES_DEFAULT = MAX_OPEN_FILES_DEFAULT;
    public static final String MAX_OPEN_ENTRY_ITERATOR_PAYLOAD_FILES_PROPERTY = "mmap.queue.maxOpenEntryIteratorPayloadFiles";
    public static final int MAX_OPEN_ENTRY_ITERATOR_PAYLOAD_FILES_DEFAULT = MAX_OPEN_FILES_DEFAULT;

    public static final String MAX_OPEN_INDEX_READER_HEADER_FILES_PROPERTY =  "mmap.queue.maxOpenIndexReaderHeaderFiles";
    public static final int MAX_OPEN_INDEX_READER_HEADER_FILES_DEFAULT = MAX_OPEN_FILES_DEFAULT;

    public static final String MAX_OPEN_APPENDER_HEADER_FILES_PROPERTY =  "mmap.queue.maxOpenAppenderHeaderFiles";
    public static final int MAX_OPEN_APPENDER_HEADER_FILES_DEFAULT = MAX_OPEN_FILES_DEFAULT;
    public static final String MAX_OPEN_APPENDER_PAYLOAD_FILES_PROPERTY = "mmap.queue.maxOpenAppenderPayloadFiles";
    public static final int MAX_OPEN_APPENDER_PAYLOAD_FILES_DEFAULT = MAX_OPEN_FILES_DEFAULT;
    public static final String APPENDER_HEADER_FILES_TO_CREATE_AHEAD_PROPERTY = "mmap.queue.appenderHeaderFilesToCreateAhead";
    public static final int APPENDER_HEADER_FILES_TO_CREATE_AHEAD_DEFAULT = 0;
    public static final String APPENDER_PAYLOAD_FILES_TO_CREATE_AHEAD_PROPERTY = "mmap.queue.appenderPayloadFilesToCreateAhead";
    public static final int APPENDER_PAYLOAD_FILES_TO_CREATE_AHEAD_DEFAULT = 0;

    public static final String POLLER_HEADER_REGION_CACHE_SIZE_PROPERTY = "mmap.queue.pollerHeaderRegionCacheSize";
    public static final String POLLER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.queue.pollerHeaderRegionsToMapAhead";
    public static final String POLLER_HEADER_MAPPING_STRATEGY_PROPERTY = "mmap.queue.pollerHeaderMappingStrategy";
    public static final MappingStrategy POLLER_HEADER_MAPPING_STRATEGY_DEFAULT = AsyncMapAheadStrategy;
    public static final String POLLER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY = "mmap.queue.pollerPayloadRegionCacheSize";
    public static final String POLLER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.queue.pollerPayloadRegionsToMapAhead";
    public static final String POLLER_PAYLOAD_MAPPING_STRATEGY_PROPERTY = "mmap.queue.pollerPayloadMappingStrategy";
    public static final MappingStrategy POLLER_PAYLOAD_MAPPING_STRATEGY_DEFAULT = AsyncMapAheadStrategy;
    public static final String ENTRY_READER_HEADER_REGION_CACHE_SIZE_PROPERTY = "mmap.queue.entryReaderHeaderRegionCacheSize";
    public static final String ENTRY_READER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.queue.entryReaderHeaderRegionsToMapAhead";
    public static final String ENTRY_READER_HEADER_MAPPING_STRATEGY_PROPERTY = "mmap.queue.entryReaderHeaderMappingStrategy";
    public static final MappingStrategy ENTRY_READER_HEADER_MAPPING_STRATEGY_DEFAULT = SyncMappingStrategyWithAsyncUnmapping;
    public static final String ENTRY_READER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY = "mmap.queue.entryReaderPayloadRegionCacheSize";
    public static final String ENTRY_READER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.queue.entryReaderPayloadRegionsToMapAhead";
    public static final String ENTRY_READER_PAYLOAD_MAPPING_STRATEGY_PROPERTY = "mmap.queue.entryReaderPayloadMappingStrategy";
    public static final MappingStrategy ENTRY_READER_PAYLOAD_MAPPING_STRATEGY_DEFAULT = SyncMappingStrategyWithAsyncUnmapping;
    public static final String ENTRY_ITERATOR_HEADER_REGION_CACHE_SIZE_PROPERTY = "mmap.queue.entryIteratorHeaderRegionCacheSize";
    public static final String ENTRY_ITERATOR_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.queue.entryIteratorHeaderRegionsToMapAhead";
    public static final String ENTRY_ITERATOR_HEADER_MAPPING_STRATEGY_PROPERTY = "mmap.queue.entryIteratorHeaderMappingStrategy";
    public static final MappingStrategy ENTRY_ITERATOR_HEADER_MAPPING_STRATEGY_DEFAULT = AsyncMapAheadStrategy;
    public static final String ENTRY_ITERATOR_PAYLOAD_REGION_CACHE_SIZE_PROPERTY = "mmap.queue.entryIteratorPayloadRegionCacheSize";
    public static final String ENTRY_ITERATOR_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.queue.entryIteratorPayloadRegionsToMapAhead";
    public static final String ENTRY_ITERATOR_PAYLOAD_MAPPING_STRATEGY_PROPERTY = "mmap.queue.entryIteratorPayloadMappingStrategy";
    public static final MappingStrategy ENTRY_ITERATOR_PAYLOAD_MAPPING_STRATEGY_DEFAULT = AsyncMapAheadStrategy;
    public static final String INDEX_READER_HEADER_REGION_CACHE_SIZE_PROPERTY = "mmap.queue.indexReaderHeaderRegionCacheSize";
    public static final String INDEX_READER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.queue.indexReaderHeaderRegionsToMapAhead";
    public static final String INDEX_READER_HEADER_MAPPING_STRATEGY_PROPERTY = "mmap.queue.indexReaderHeaderMappingStrategy";
    public static final MappingStrategy INDEX_READER_HEADER_MAPPING_STRATEGY_DEFAULT = SyncMappingStrategyWithAsyncUnmapping;

    public static final String APPENDER_HEADER_REGION_CACHE_SIZE_PROPERTY = "mmap.queue.appenderHeaderRegionCacheSize";
    public static final String APPENDER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.queue.appenderHeaderRegionsToMapAhead";
    public static final String APPENDER_HEADER_MAPPING_STRATEGY_PROPERTY = "mmap.queue.appenderHeaderMappingStrategy";
    public static final MappingStrategy APPENDER_HEADER_MAPPING_STRATEGY_DEFAULT = AsyncMapAheadStrategy;
    public static final String APPENDER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY = "mmap.queue.appenderPayloadRegionCacheSize";
    public static final String APPENDER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.queue.appenderPayloadRegionsToMapAhead";
    public static final String APPENDER_PAYLOAD_MAPPING_STRATEGY_PROPERTY = "mmap.queue.appenderPayloadMappingStrategy";
    public static final MappingStrategy APPENDER_PAYLOAD_MAPPING_STRATEGY_DEFAULT = AsyncMapAheadStrategy;

    public static int defaultMaxAppenders() {
        return getIntProperty(MAX_APPENDERS_PROPERTY, Constraints::validateMaxAppenders, MAX_APPENDERS_DEFAULT);
    }

    public static long defaultMinHeaderFileSize() {
        return getLongProperty(MIN_HEADER_FILE_SIZE_PROPERTY, Constraints::validateMinFileSize, MIN_HEADER_FILE_SIZE_DEFAULT);
    }

    public static long defaultMinPayloadFileSize() {
        return getLongProperty(MIN_PAYLOAD_FILE_SIZE_PROPERTY, Constraints::validateMinFileSize, MIN_PAYLOAD_FILE_SIZE_DEFAULT);
    }

    public static long defaultMaxHeaderFileSize() {
        return getLongProperty(MAX_HEADER_FILE_SIZE_PROPERTY, Constraints::validateMaxFileSize, MAX_HEADER_FILE_SIZE_DEFAULT);
    }

    public static long defaultMaxPayloadFileSize() {
        return getLongProperty(MAX_PAYLOAD_FILE_SIZE_PROPERTY, Constraints::validateMaxFileSize, MAX_PAYLOAD_FILE_SIZE_DEFAULT);
    }

    public static int defaultHeaderRegionSize() {
        return getIntProperty(HEADER_REGION_SIZE_PROPERTY, Constraints::validateRegionSize, MappingConfigurations::defaultRegionSize);
    }

    public static int defaultPayloadRegionSize() {
        return getIntProperty(PAYLOAD_REGION_SIZE_PROPERTY, Constraints::validateRegionSize, MappingConfigurations::defaultRegionSize);
    }

    public static boolean defaultExpandHeaderFile() {
        return getBooleanProperty(EXPAND_HEADER_FILE_PROPERTY, EXPAND_HEADER_FILE_DEFAULT);
    }

    public static boolean defaultExpandPayloadFiles() {
        return getBooleanProperty(EXPAND_PAYLOAD_FILES_PROPERTY, EXPAND_PAYLOAD_FILES_DEFAULT);
    }

    public static boolean defaultRollHeaderFile() {
        return getBooleanProperty(ROLL_HEADER_FILE_PROPERTY, ROLL_HEADER_FILE_DEFAULT);
    }

    public static boolean defaultRollPayloadFiles() {
        return getBooleanProperty(ROLL_PAYLOAD_FILES_PROPERTY, ROLL_PAYLOAD_FILES_DEFAULT);
    }


    // POLLER

    public static int defaultMaxOpenPollerHeaderFiles() {
        return getIntProperty(MAX_OPEN_POLLER_HEADER_FILES_PROPERTY, Constraints::validateMaxOpenFiles, MAX_OPEN_POLLER_HEADER_FILES_DEFAULT);
    }

    public static int defaultMaxOpenPollerPayloadFiles() {
        return getIntProperty(MAX_OPEN_POLLER_PAYLOAD_FILES_PROPERTY, Constraints::validateMaxOpenFiles, MAX_OPEN_POLLER_PAYLOAD_FILES_DEFAULT);
    }

    // ENTRY_READER

    public static int defaultMaxOpenEntryReaderHeaderFiles() {
        return getIntProperty(MAX_OPEN_ENTRY_READER_HEADER_FILES_PROPERTY, Constraints::validateMaxOpenFiles, MAX_OPEN_ENTRY_READER_HEADER_FILES_DEFAULT);
    }

    public static int defaultMaxOpenEntryReaderPayloadFiles() {
        return getIntProperty(MAX_OPEN_ENTRY_READER_PAYLOAD_FILES_PROPERTY, Constraints::validateMaxOpenFiles, MAX_OPEN_ENTRY_READER_PAYLOAD_FILES_DEFAULT);
    }

    // ENTRY_ITERATOR

    public static int defaultMaxOpenEntryIteratorHeaderFiles() {
        return getIntProperty(MAX_OPEN_ENTRY_ITERATOR_HEADER_FILES_PROPERTY, Constraints::validateMaxOpenFiles, MAX_OPEN_ENTRY_ITERATOR_HEADER_FILES_DEFAULT);
    }

    public static int defaultMaxOpenEntryIteratorPayloadFiles() {
        return getIntProperty(MAX_OPEN_ENTRY_ITERATOR_PAYLOAD_FILES_PROPERTY, Constraints::validateMaxOpenFiles, MAX_OPEN_ENTRY_ITERATOR_PAYLOAD_FILES_DEFAULT);
    }

    // INDEX_READER
    public static int defaultMaxOpenIndexReaderHeaderFiles() {
        return getIntProperty(MAX_OPEN_INDEX_READER_HEADER_FILES_PROPERTY, Constraints::validateMaxOpenFiles, MAX_OPEN_INDEX_READER_HEADER_FILES_DEFAULT);
    }

    // APPENDER
    public static int defaultMaxOpenAppenderHeaderFiles() {
        return getIntProperty(MAX_OPEN_APPENDER_HEADER_FILES_PROPERTY, Constraints::validateMaxOpenFiles, MAX_OPEN_APPENDER_HEADER_FILES_DEFAULT);
    }

    public static int defaultMaxOpenAppenderPayloadFiles() {
        return getIntProperty(MAX_OPEN_APPENDER_PAYLOAD_FILES_PROPERTY, Constraints::validateMaxOpenFiles, MAX_OPEN_APPENDER_PAYLOAD_FILES_DEFAULT);
    }

    public static int defaultAppenderHeaderFilesToCreateAhead() {
        return getIntProperty(APPENDER_HEADER_FILES_TO_CREATE_AHEAD_PROPERTY, Constraints::validateFilesToCreateAhead, APPENDER_HEADER_FILES_TO_CREATE_AHEAD_DEFAULT);
    }

    public static int defaultAppenderPayloadFilesToCreateAhead() {
        return getIntProperty(APPENDER_PAYLOAD_FILES_TO_CREATE_AHEAD_PROPERTY, Constraints::validateFilesToCreateAhead, APPENDER_PAYLOAD_FILES_TO_CREATE_AHEAD_DEFAULT);
    }


    // POLLER_HEADER_MAPPING_STRATEGY

    public static int defaultPollerHeaderRegionCacheSize() {
        return getIntProperty(POLLER_HEADER_REGION_CACHE_SIZE_PROPERTY, Constraints::validateRegionCacheSize, MappingConfigurations::defaultRegionCacheSize);
    }

    public static int defaultPollerHeaderRegionsToMapAhead() {
        return getIntProperty(POLLER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY, Constraints::validateRegionsToMapAhead, MappingConfigurations::defaultRegionsToMapAhead);
    }

    public static MappingStrategyConfig defaultPollerHeaderMappingStrategy() {
        return getMappingStrategyProperty(
                POLLER_HEADER_MAPPING_STRATEGY_PROPERTY,
                POLLER_HEADER_MAPPING_STRATEGY_DEFAULT,
                QueueConfigurations::defaultPollerHeaderRegionCacheSize,
                QueueConfigurations::defaultPollerHeaderRegionsToMapAhead
        );
    }

    // POLLER_PAYLOAD_MAPPING_STRATEGY

    public static int defaultPollerPayloadRegionCacheSize() {
        return getIntProperty(POLLER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY, Constraints::validateRegionCacheSize, MappingConfigurations::defaultRegionCacheSize);
    }

    public static int defaultPollerPayloadRegionsToMapAhead() {
        return getIntProperty(POLLER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY, Constraints::validateRegionsToMapAhead, MappingConfigurations::defaultRegionsToMapAhead);
    }

    public static MappingStrategyConfig defaultPollerPayloadMappingStrategy() {
        return getMappingStrategyProperty(
                POLLER_PAYLOAD_MAPPING_STRATEGY_PROPERTY,
                POLLER_PAYLOAD_MAPPING_STRATEGY_DEFAULT,
                QueueConfigurations::defaultPollerPayloadRegionCacheSize,
                QueueConfigurations::defaultPollerPayloadRegionsToMapAhead
        );
    }

    // ENTRY_READER_HEADER_MAPPING_STRATEGY

    public static int defaultEntryReaderHeaderRegionCacheSize() {
        return getIntProperty(ENTRY_READER_HEADER_REGION_CACHE_SIZE_PROPERTY, Constraints::validateRegionCacheSize, MappingConfigurations::defaultRegionCacheSize);
    }

    public static int defaultEntryReaderHeaderRegionsToMapAhead() {
        return getIntProperty(ENTRY_READER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY, Constraints::validateRegionsToMapAhead, MappingConfigurations::defaultRegionsToMapAhead);
    }

    public static MappingStrategyConfig defaultEntryReaderHeaderMappingStrategy() {
        return getMappingStrategyProperty(
                ENTRY_READER_HEADER_MAPPING_STRATEGY_PROPERTY,
                ENTRY_READER_HEADER_MAPPING_STRATEGY_DEFAULT,
                QueueConfigurations::defaultEntryReaderHeaderRegionCacheSize,
                QueueConfigurations::defaultEntryReaderHeaderRegionsToMapAhead
        );
    }

    // ENTRY_READER_PAYLOAD_MAPPING_STRATEGY

    public static int defaultEntryReaderPayloadRegionCacheSize() {
        return getIntProperty(ENTRY_READER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY, Constraints::validateRegionCacheSize, MappingConfigurations::defaultRegionCacheSize);
    }

    public static int defaultEntryReaderPayloadRegionsToMapAhead() {
        return getIntProperty(ENTRY_READER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY, Constraints::validateRegionsToMapAhead, MappingConfigurations::defaultRegionsToMapAhead);
    }

    public static MappingStrategyConfig defaultEntryReaderPayloadMappingStrategy() {
        return getMappingStrategyProperty(
                ENTRY_READER_PAYLOAD_MAPPING_STRATEGY_PROPERTY,
                ENTRY_READER_PAYLOAD_MAPPING_STRATEGY_DEFAULT,
                QueueConfigurations::defaultEntryReaderPayloadRegionCacheSize,
                QueueConfigurations::defaultEntryReaderPayloadRegionsToMapAhead
        );
    }

    // ENTRY_ITERATOR_HEADER_MAPPING_STRATEGY

    public static int defaultEntryIteratorHeaderRegionCacheSize() {
        return getIntProperty(ENTRY_ITERATOR_HEADER_REGION_CACHE_SIZE_PROPERTY, Constraints::validateRegionCacheSize, MappingConfigurations::defaultRegionCacheSize);
    }

    public static int defaultEntryIteratorHeaderRegionsToMapAhead() {
        return getIntProperty(ENTRY_ITERATOR_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY, Constraints::validateRegionsToMapAhead, MappingConfigurations::defaultRegionsToMapAhead);
    }

    public static MappingStrategyConfig defaultEntryIteratorHeaderMappingStrategy() {
        return getMappingStrategyProperty(
                ENTRY_ITERATOR_HEADER_MAPPING_STRATEGY_PROPERTY,
                ENTRY_ITERATOR_HEADER_MAPPING_STRATEGY_DEFAULT,
                QueueConfigurations::defaultEntryIteratorHeaderRegionCacheSize,
                QueueConfigurations::defaultEntryIteratorHeaderRegionsToMapAhead
        );
    }

    // ENTRY_ITERATOR_PAYLOAD_MAPPING_STRATEGY

    public static int defaultEntryIteratorPayloadRegionCacheSize() {
        return getIntProperty(ENTRY_ITERATOR_PAYLOAD_REGION_CACHE_SIZE_PROPERTY, Constraints::validateRegionCacheSize, MappingConfigurations::defaultRegionCacheSize);
    }

    public static int defaultEntryIteratorPayloadRegionsToMapAhead() {
        return getIntProperty(ENTRY_ITERATOR_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY, Constraints::validateRegionsToMapAhead, MappingConfigurations::defaultRegionsToMapAhead);
    }

    public static MappingStrategyConfig defaultEntryIteratorPayloadMappingStrategy() {
        return getMappingStrategyProperty(
                ENTRY_ITERATOR_PAYLOAD_MAPPING_STRATEGY_PROPERTY,
                ENTRY_ITERATOR_PAYLOAD_MAPPING_STRATEGY_DEFAULT,
                QueueConfigurations::defaultEntryIteratorPayloadRegionCacheSize,
                QueueConfigurations::defaultEntryIteratorPayloadRegionsToMapAhead
        );
    }

    // INDEX_READER_HEADER_MAPPING_STRATEGY

    public static int defaultIndexReaderHeaderRegionCacheSize() {
        return getIntProperty(INDEX_READER_HEADER_REGION_CACHE_SIZE_PROPERTY, Constraints::validateRegionCacheSize, MappingConfigurations::defaultRegionCacheSize);
    }

    public static int defaultIndexReaderHeaderRegionsToMapAhead() {
        return getIntProperty(INDEX_READER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY, Constraints::validateRegionsToMapAhead, MappingConfigurations::defaultRegionsToMapAhead);
    }

    public static MappingStrategyConfig defaultIndexReaderHeaderMappingStrategy() {
        return getMappingStrategyProperty(
                INDEX_READER_HEADER_MAPPING_STRATEGY_PROPERTY,
                INDEX_READER_HEADER_MAPPING_STRATEGY_DEFAULT,
                QueueConfigurations::defaultIndexReaderHeaderRegionCacheSize,
                QueueConfigurations::defaultIndexReaderHeaderRegionsToMapAhead
        );
    }

    // APPENDER_HEADER_MAPPING_STRATEGY

    public static int defaultAppenderHeaderRegionCacheSize() {
        return getIntProperty(APPENDER_HEADER_REGION_CACHE_SIZE_PROPERTY, Constraints::validateRegionCacheSize, MappingConfigurations::defaultRegionCacheSize);
    }

    public static int defaultAppenderHeaderRegionsToMapAhead() {
        return getIntProperty(APPENDER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY, Constraints::validateRegionsToMapAhead, MappingConfigurations::defaultRegionsToMapAhead);
    }

    public static MappingStrategyConfig defaultAppenderHeaderMappingStrategy() {
        return getMappingStrategyProperty(
                APPENDER_HEADER_MAPPING_STRATEGY_PROPERTY,
                APPENDER_HEADER_MAPPING_STRATEGY_DEFAULT,
                QueueConfigurations::defaultAppenderHeaderRegionCacheSize,
                QueueConfigurations::defaultAppenderHeaderRegionsToMapAhead
        );
    }

    // APPENDER_PAYLOAD_MAPPING_STRATEGY

    public static int defaultAppenderPayloadRegionCacheSize() {
        return getIntProperty(APPENDER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY, Constraints::validateRegionCacheSize, MappingConfigurations::defaultRegionCacheSize);
    }

    public static int defaultAppenderPayloadRegionsToMapAhead() {
        return getIntProperty(APPENDER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY, Constraints::validateRegionsToMapAhead, MappingConfigurations::defaultRegionsToMapAhead);
    }

    public static MappingStrategyConfig defaultAppenderPayloadMappingStrategy() {
        return getMappingStrategyProperty(
                APPENDER_PAYLOAD_MAPPING_STRATEGY_PROPERTY,
                APPENDER_PAYLOAD_MAPPING_STRATEGY_DEFAULT,
                QueueConfigurations::defaultAppenderPayloadRegionCacheSize,
                QueueConfigurations::defaultAppenderPayloadRegionsToMapAhead
        );
    }

    public static QueueConfig defaultQueueConfig() {
        return QUEUE_CONFIG_DEFAULTS;
    }

    public static AppenderConfig defaultAppenderConfig() {
        return APPENDER_CONFIG_DEFAULTS;
    }

    public static ReaderConfig defaultPollerConfig() {
        return POLLER_CONFIG_DEFAULTS;
    }

    public static ReaderConfig defaultEntryReaderConfig() {
        return ENTRY_READER_CONFIG_DEFAULTS;
    }

    public static ReaderConfig defaultEntryIteratorConfig() {
        return ENTRY_ITERATOR_CONFIG_DEFAULTS;
    }

    public static IndexReaderConfig defaultIndexReaderConfig() {
        return INDEX_READER_CONFIG_DEFAULTS;
    }

    private static MappingStrategyConfig getMappingStrategyProperty(final String mappingStrategyProperty,
                                                                    final MappingStrategy mappingStrategyDefault,
                                                                    final IntSupplier regionCacheSizeSupplier,
                                                                    final IntSupplier regionsToMapAheadSupplier) {
        final MappingStrategyConfig strategy = getEnumProperty(mappingStrategyProperty, MappingStrategy.class,
                mappingStrategyDefault).mappingStrategyConfig();
        final MappingStrategyConfigurator config = MappingStrategyConfig.configure(strategy)
                .cacheSize(regionCacheSizeSupplier.getAsInt())
                .deferUnmapping(true);
        if (strategy.asyncMapping().isPresent()) {
            config.asyncMapping(async -> async.regionsToMapAhead(regionsToMapAheadSupplier.getAsInt()));
        }
        return config.toImmutableConfig();
    }

    private static int getIntProperty(final String propertyName, final IntConsumer validator, final int defaultValue) {
        final String propVal = System.getProperty(propertyName, null);
        if (propVal == null) {
            return defaultValue;
        }
        try {
            final int intValue = Integer.parseInt(propVal);
            validator.accept(intValue);
            return intValue;
        } catch (final Exception e) {
            throw new IllegalArgumentException("Invalid value for system property: " + propertyName + "=" + propVal, e);
        }
    }

    private static int getIntProperty(final String propertyName, final IntConsumer validator, final IntSupplier defaultValueSupplier) {
        final String propVal = System.getProperty(propertyName, null);
        if (propVal == null) {
            return defaultValueSupplier.getAsInt();
        }
        try {
            final int intValue = Integer.parseInt(propVal);
            validator.accept(intValue);
            return intValue;
        } catch (final Exception e) {
            throw new IllegalArgumentException("Invalid value for system property: " + propertyName + "=" + propVal, e);
        }
    }

    private static long getLongProperty(final String propertyName, final LongConsumer validator, final long defaultValue) {
        final String propVal = System.getProperty(propertyName, null);
        if (propVal == null) {
            return defaultValue;
        }
        try {
            final long longValue = Long.parseLong(propVal);
            validator.accept(longValue);
            return longValue;
        } catch (final Exception e) {
            throw new IllegalArgumentException("Invalid value for system property: " + propertyName + "=" + propVal, e);
        }
    }

    private static boolean getBooleanProperty(final String propertyName, final boolean defaultValue) {
        final String propVal = System.getProperty(propertyName, null);
        if (propVal == null) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(propVal)) {
            return true;
        }
        if ("false".equalsIgnoreCase(propVal)) {
            return false;
        }
        throw new IllegalArgumentException("Invalid value for system property: " + propertyName + "=" + propVal +
                " (expected true or false)");
    }

    private static <E extends Enum<E>> E getEnumProperty(final String propertyName,
                                                         final Class<E> enumType,
                                                         final E defaultValue) {
        final String propVal = System.getProperty(propertyName, null);
        if (propVal == null) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(enumType, propVal);
        } catch (final Exception e) {
            throw new IllegalArgumentException("Invalid value for system property: " + propertyName + "=" + propVal, e);
        }
    }
}
