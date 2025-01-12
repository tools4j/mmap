/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2025 tools4j.org (Marco Terzer, Anton Anufriev)
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

import org.tools4j.mmap.queue.impl.AppenderIdPool64;
import org.tools4j.mmap.region.api.AccessMode;
import org.tools4j.mmap.region.config.AheadMappingStrategy;
import org.tools4j.mmap.region.config.MappingConfigurations;
import org.tools4j.mmap.region.config.MappingStrategy;
import org.tools4j.mmap.region.config.SyncMappingStrategy;
import org.tools4j.mmap.region.impl.Constraints;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

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
    public static final String MAX_APPENDERS_PROPERTY = "mmap.queue.maxAppenders";
    public static final int MAX_APPENDERS_DEFAULT = AppenderIdPool64.MAX_APPENDERS;
    public static final String ACCESS_MODE_PROPERTY = "mmap.queue.accessMode";
    public static final AccessMode ACCESS_MODE_DEFAULT = AccessMode.READ_WRITE;
    public static final String MAX_HEADER_FILE_SIZE_PROPERTY = "mmap.queue.maxHeaderFileSize";
    public static final int MAX_HEADER_FILE_SIZE_DEFAULT = 64*1024*1024;
    public static final String MAX_PAYLOAD_FILE_SIZE_PROPERTY = "mmap.queue.maxPayloadFileSize";
    public static final int MAX_PAYLOAD_FILE_SIZE_DEFAULT = 64*1024*1024;
    public static final String EXPAND_HEADER_FILE_PROPERTY = "mmap.queue.expandHeaderFile";
    public static final boolean EXPAND_HEADER_FILE_DEFAULT = false;
    public static final String EXPAND_PAYLOAD_FILES_PROPERTY = "mmap.queue.expandPayloadFiles";
    public static final boolean EXPAND_PAYLOAD_FILES_DEFAULT = false;
    public static final String ROLL_HEADER_FILE_PROPERTY = "mmap.queue.rollHeaderFile";
    public static final boolean ROLL_HEADER_FILE_DEFAULT = true;
    public static final String ROLL_PAYLOAD_FILES_PROPERTY = "mmap.queue.rollPayloadFiles";
    public static final boolean ROLL_PAYLOAD_FILES_DEFAULT = true;
    public static final String HEADER_FILES_TO_CREATE_AHEAD_PROPERTY = "mmap.queue.headerFilesToCreateAhead";
    public static final int HEADER_FILES_TO_CREATE_AHEAD_DEFAULT = 0;
    public static final String PAYLOAD_FILES_TO_CREATE_AHEAD_PROPERTY = "mmap.queue.payloadFilesToCreateAhead";
    public static final int PAYLOAD_FILES_TO_CREATE_AHEAD_DEFAULT = 0;
    public static final String CLOSE_POLLER_HEADER_FILES_PROPERTY = "mmap.queue.closePollerHeaderFiles";
    public static final String CLOSE_POLLER_PAYLOAD_FILES_PROPERTY = "mmap.queue.closePollerPayloadFiles";
    public static final boolean CLOSE_POLLER_HEADER_FILES_DEFAULT = true;
    public static final boolean CLOSE_POLLER_PAYLOAD_FILES_DEFAULT = true;
    public static final String CLOSE_ENTRY_READER_HEADER_FILES_PROPERTY = "mmap.queue.closeEntryReaderHeaderFiles";
    public static final String CLOSE_ENTRY_READER_PAYLOAD_FILES_PROPERTY = "mmap.queue.closeEntryReaderPayloadFiles";
    public static final boolean CLOSE_ENTRY_READER_HEADER_FILES_DEFAULT = false;
    public static final boolean CLOSE_ENTRY_READER_PAYLOAD_FILES_DEFAULT = false;
    public static final String CLOSE_ENTRY_ITERATOR_HEADER_FILES_PROPERTY = "mmap.queue.closeEntryIteratorHeaderFiles";
    public static final String CLOSE_ENTRY_ITERATOR_PAYLOAD_FILES_PROPERTY = "mmap.queue.closeEntryIteratorPayloadFiles";
    public static final boolean CLOSE_ENTRY_ITERATOR_HEADER_FILES_DEFAULT = true;
    public static final boolean CLOSE_ENTRY_ITERATOR_PAYLOAD_FILES_DEFAULT = true;
    public static final String CLOSE_INDEX_READER_HEADER_FILES_PROPERTY = "mmap.queue.closeIndexReaderHeaderFiles";
    public static final boolean CLOSE_INDEX_READER_HEADER_FILES_DEFAULT = false;
    public static final String POLLER_HEADER_REGION_SIZE_PROPERTY = "mmap.queue.pollerHeaderRegionSize";
    public static final String POLLER_HEADER_REGION_CACHE_SIZE_PROPERTY = "mmap.queue.pollerHeaderRegionCacheSize";
    public static final String POLLER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.queue.pollerHeaderRegionsToMapAhead";
    public static final String POLLER_HEADER_MAPPING_STRATEGY_PROPERTY = "mmap.queue.pollerHeaderMappingStrategy";
    public static final String POLLER_HEADER_MAPPING_STRATEGY_DEFAULT = AheadMappingStrategy.NAME;
    public static final String POLLER_PAYLOAD_REGION_SIZE_PROPERTY = "mmap.queue.pollerPayloadRegionSize";
    public static final String POLLER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY = "mmap.queue.pollerPayloadRegionCacheSize";
    public static final String POLLER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.queue.pollerPayloadRegionsToMapAhead";
    public static final String POLLER_PAYLOAD_MAPPING_STRATEGY_PROPERTY = "mmap.queue.pollerPayloadMappingStrategy";
    public static final String POLLER_PAYLOAD_MAPPING_STRATEGY_DEFAULT = AheadMappingStrategy.NAME;
    public static final String ENTRY_READER_HEADER_REGION_SIZE_PROPERTY = "mmap.queue.entryReaderHeaderRegionSize";
    public static final String ENTRY_READER_HEADER_REGION_CACHE_SIZE_PROPERTY = "mmap.queue.entryReaderHeaderRegionCacheSize";
    public static final String ENTRY_READER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.queue.entryReaderHeaderRegionsToMapAhead";
    public static final String ENTRY_READER_HEADER_MAPPING_STRATEGY_PROPERTY = "mmap.queue.entryReaderHeaderMappingStrategy";
    public static final String ENTRY_READER_HEADER_MAPPING_STRATEGY_DEFAULT = SyncMappingStrategy.NAME;
    public static final String ENTRY_READER_PAYLOAD_REGION_SIZE_PROPERTY = "mmap.queue.entryReaderPayloadRegionSize";
    public static final String ENTRY_READER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY = "mmap.queue.entryReaderPayloadRegionCacheSize";
    public static final String ENTRY_READER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.queue.entryReaderPayloadRegionsToMapAhead";
    public static final String ENTRY_READER_PAYLOAD_MAPPING_STRATEGY_PROPERTY = "mmap.queue.entryReaderPayloadMappingStrategy";
    public static final String ENTRY_READER_PAYLOAD_MAPPING_STRATEGY_DEFAULT = SyncMappingStrategy.NAME;
    public static final String ENTRY_ITERATOR_HEADER_REGION_SIZE_PROPERTY = "mmap.queue.entryIteratorHeaderRegionSize";
    public static final String ENTRY_ITERATOR_HEADER_REGION_CACHE_SIZE_PROPERTY = "mmap.queue.entryIteratorHeaderRegionCacheSize";
    public static final String ENTRY_ITERATOR_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.queue.entryIteratorHeaderRegionsToMapAhead";
    public static final String ENTRY_ITERATOR_HEADER_MAPPING_STRATEGY_PROPERTY = "mmap.queue.entryIteratorHeaderMappingStrategy";
    public static final String ENTRY_ITERATOR_HEADER_MAPPING_STRATEGY_DEFAULT = AheadMappingStrategy.NAME;
    public static final String ENTRY_ITERATOR_PAYLOAD_REGION_SIZE_PROPERTY = "mmap.queue.entryIteratorPayloadRegionSize";
    public static final String ENTRY_ITERATOR_PAYLOAD_REGION_CACHE_SIZE_PROPERTY = "mmap.queue.entryIteratorPayloadRegionCacheSize";
    public static final String ENTRY_ITERATOR_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.queue.entryIteratorPayloadRegionsToMapAhead";
    public static final String ENTRY_ITERATOR_PAYLOAD_MAPPING_STRATEGY_PROPERTY = "mmap.queue.entryIteratorPayloadMappingStrategy";
    public static final String ENTRY_ITERATOR_PAYLOAD_MAPPING_STRATEGY_DEFAULT = AheadMappingStrategy.NAME;
    public static final String INDEX_READER_HEADER_REGION_SIZE_PROPERTY = "mmap.queue.indexReaderHeaderRegionSize";
    public static final String INDEX_READER_HEADER_REGION_CACHE_SIZE_PROPERTY = "mmap.queue.indexReaderHeaderRegionCacheSize";
    public static final String INDEX_READER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.queue.indexReaderHeaderRegionsToMapAhead";
    public static final String INDEX_READER_HEADER_MAPPING_STRATEGY_PROPERTY = "mmap.queue.indexReaderHeaderMappingStrategy";
    public static final String INDEX_READER_HEADER_MAPPING_STRATEGY_DEFAULT = SyncMappingStrategy.NAME;
    public static final String APPENDER_HEADER_REGION_SIZE_PROPERTY = "mmap.queue.appenderHeaderRegionSize";
    public static final String APPENDER_HEADER_REGION_CACHE_SIZE_PROPERTY = "mmap.queue.appenderHeaderRegionCacheSize";
    public static final String APPENDER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.queue.appenderHeaderRegionsToMapAhead";
    public static final String APPENDER_HEADER_MAPPING_STRATEGY_PROPERTY = "mmap.queue.appenderHeaderMappingStrategy";
    public static final String APPENDER_HEADER_MAPPING_STRATEGY_DEFAULT = AheadMappingStrategy.NAME;
    public static final String APPENDER_PAYLOAD_REGION_SIZE_PROPERTY = "mmap.queue.appenderPayloadRegionSize";
    public static final String APPENDER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY = "mmap.queue.appenderPayloadRegionCacheSize";
    public static final String APPENDER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.queue.appenderPayloadRegionsToMapAhead";
    public static final String APPENDER_PAYLOAD_MAPPING_STRATEGY_PROPERTY = "mmap.queue.appenderPayloadMappingStrategy";
    public static final String APPENDER_PAYLOAD_MAPPING_STRATEGY_DEFAULT = AheadMappingStrategy.NAME;
    private static MappingStrategy POLLER_HEADER_MAPPING_STRATEGY;
    private static MappingStrategy POLLER_PAYLOAD_MAPPING_STRATEGY;
    private static MappingStrategy ENTRY_READER_HEADER_MAPPING_STRATEGY;
    private static MappingStrategy ENTRY_READER_PAYLOAD_MAPPING_STRATEGY;
    private static MappingStrategy ENTRY_ITERATOR_HEADER_MAPPING_STRATEGY;
    private static MappingStrategy ENTRY_ITERATOR_PAYLOAD_MAPPING_STRATEGY;
    private static MappingStrategy INDEX_READER_HEADER_MAPPING_STRATEGY;
    private static MappingStrategy APPENDER_HEADER_MAPPING_STRATEGY;
    private static MappingStrategy APPENDER_PAYLOAD_MAPPING_STRATEGY;

    public static int defaultMaxAppenders() {
        return getIntProperty(MAX_APPENDERS_PROPERTY, Constraints::validateMaxAppenders, MAX_APPENDERS_DEFAULT);
    }

    public static AccessMode defaultAccessMode() {
        return getEnumProperty(ACCESS_MODE_PROPERTY, AccessMode.class, ACCESS_MODE_DEFAULT);
    }

    public static int defaultMaxHeaderFileSize() {
        return getIntProperty(MAX_HEADER_FILE_SIZE_PROPERTY, Constraints::validateMaxFileSize, MAX_HEADER_FILE_SIZE_DEFAULT);
    }

    public static int defaultMaxPayloadFileSize() {
        return getIntProperty(MAX_PAYLOAD_FILE_SIZE_PROPERTY, Constraints::validateMaxFileSize, MAX_PAYLOAD_FILE_SIZE_DEFAULT);
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

    public static int defaultHeaderFilesToCreateAhead() {
        return getIntProperty(HEADER_FILES_TO_CREATE_AHEAD_PROPERTY, Constraints::validateFilesToCreateAhead, HEADER_FILES_TO_CREATE_AHEAD_DEFAULT);
    }

    public static int defaultPayloadFilesToCreateAhead() {
        return getIntProperty(PAYLOAD_FILES_TO_CREATE_AHEAD_PROPERTY, Constraints::validateFilesToCreateAhead, PAYLOAD_FILES_TO_CREATE_AHEAD_DEFAULT);
    }

    public static boolean defaultClosePollerHeaderFiles() {
        return getBooleanProperty(CLOSE_POLLER_HEADER_FILES_PROPERTY, CLOSE_POLLER_HEADER_FILES_DEFAULT);
    }

    public static boolean defaultClosePollerPayloadFiles() {
        return getBooleanProperty(CLOSE_POLLER_PAYLOAD_FILES_PROPERTY, CLOSE_POLLER_PAYLOAD_FILES_DEFAULT);
    }

    public static boolean defaultCloseEntryReaderHeaderFiles() {
        return getBooleanProperty(CLOSE_ENTRY_READER_HEADER_FILES_PROPERTY, CLOSE_ENTRY_READER_HEADER_FILES_DEFAULT);
    }

    public static boolean defaultCloseEntryReaderPayloadFiles() {
        return getBooleanProperty(CLOSE_ENTRY_READER_PAYLOAD_FILES_PROPERTY, CLOSE_ENTRY_READER_PAYLOAD_FILES_DEFAULT);
    }

    public static boolean defaultCloseEntryIteratorHeaderFiles() {
        return getBooleanProperty(CLOSE_ENTRY_ITERATOR_HEADER_FILES_PROPERTY, CLOSE_ENTRY_ITERATOR_HEADER_FILES_DEFAULT);
    }

    public static boolean defaultCloseEntryIteratorPayloadFiles() {
        return getBooleanProperty(CLOSE_ENTRY_ITERATOR_PAYLOAD_FILES_PROPERTY, CLOSE_ENTRY_ITERATOR_PAYLOAD_FILES_DEFAULT);
    }

    public static boolean defaultCloseIndexReaderHeaderFiles() {
        return getBooleanProperty(CLOSE_INDEX_READER_HEADER_FILES_PROPERTY, CLOSE_INDEX_READER_HEADER_FILES_DEFAULT);
    }

    // POLLER_HEADER_MAPPING_STRATEGY

    public static int defaultPollerHeaderRegionSize() {
        return getIntProperty(POLLER_HEADER_REGION_SIZE_PROPERTY, Constraints::validateRegionSize, MappingConfigurations::defaultRegionSize);
    }

    public static int defaultPollerHeaderRegionCacheSize() {
        return getIntProperty(POLLER_HEADER_REGION_CACHE_SIZE_PROPERTY, Constraints::validateRegionCacheSize, MappingConfigurations::defaultRegionCacheSize);
    }

    public static int defaultPollerHeaderRegionsToMapAhead() {
        return getIntProperty(POLLER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY, Constraints::validateRegionsToMapAhead, MappingConfigurations::defaultRegionsToMapAhead);
    }

    public static MappingStrategy defaultPollerHeaderMappingStrategy() {
        if (POLLER_HEADER_MAPPING_STRATEGY == null) {
            POLLER_HEADER_MAPPING_STRATEGY = getMappingStrategyProperty(
                    POLLER_HEADER_MAPPING_STRATEGY_PROPERTY,
                    POLLER_HEADER_MAPPING_STRATEGY_DEFAULT,
                    QueueConfigurations::defaultPollerHeaderRegionSize,
                    QueueConfigurations::defaultPollerHeaderRegionCacheSize,
                    QueueConfigurations::defaultPollerHeaderRegionsToMapAhead
            );
            assert POLLER_HEADER_MAPPING_STRATEGY != null;
        }
        return POLLER_HEADER_MAPPING_STRATEGY;
    }

    // POLLER_PAYLOAD_MAPPING_STRATEGY

    public static int defaultPollerPayloadRegionSize() {
        return getIntProperty(POLLER_PAYLOAD_REGION_SIZE_PROPERTY, Constraints::validateRegionSize, MappingConfigurations::defaultRegionSize);
    }

    public static int defaultPollerPayloadRegionCacheSize() {
        return getIntProperty(POLLER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY, Constraints::validateRegionCacheSize, MappingConfigurations::defaultRegionCacheSize);
    }

    public static int defaultPollerPayloadRegionsToMapAhead() {
        return getIntProperty(POLLER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY, Constraints::validateRegionsToMapAhead, MappingConfigurations::defaultRegionsToMapAhead);
    }

    public static MappingStrategy defaultPollerPayloadMappingStrategy() {
        if (POLLER_PAYLOAD_MAPPING_STRATEGY == null) {
            POLLER_PAYLOAD_MAPPING_STRATEGY = getMappingStrategyProperty(
                    POLLER_PAYLOAD_MAPPING_STRATEGY_PROPERTY,
                    POLLER_PAYLOAD_MAPPING_STRATEGY_DEFAULT,
                    QueueConfigurations::defaultPollerPayloadRegionSize,
                    QueueConfigurations::defaultPollerPayloadRegionCacheSize,
                    QueueConfigurations::defaultPollerPayloadRegionsToMapAhead
            );
            assert POLLER_PAYLOAD_MAPPING_STRATEGY != null;
        }
        return POLLER_PAYLOAD_MAPPING_STRATEGY;
    }

    // ENTRY_READER_HEADER_MAPPING_STRATEGY

    public static int defaultEntryReaderHeaderRegionSize() {
        return getIntProperty(ENTRY_READER_HEADER_REGION_SIZE_PROPERTY, Constraints::validateRegionSize, MappingConfigurations::defaultRegionSize);
    }

    public static int defaultEntryReaderHeaderRegionCacheSize() {
        return getIntProperty(ENTRY_READER_HEADER_REGION_CACHE_SIZE_PROPERTY, Constraints::validateRegionCacheSize, MappingConfigurations::defaultRegionCacheSize);
    }

    public static int defaultEntryReaderHeaderRegionsToMapAhead() {
        return getIntProperty(ENTRY_READER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY, Constraints::validateRegionsToMapAhead, MappingConfigurations::defaultRegionsToMapAhead);
    }

    public static MappingStrategy defaultEntryReaderHeaderMappingStrategy() {
        if (ENTRY_READER_HEADER_MAPPING_STRATEGY == null) {
            ENTRY_READER_HEADER_MAPPING_STRATEGY = getMappingStrategyProperty(
                    ENTRY_READER_HEADER_MAPPING_STRATEGY_PROPERTY,
                    ENTRY_READER_HEADER_MAPPING_STRATEGY_DEFAULT,
                    QueueConfigurations::defaultEntryReaderHeaderRegionSize,
                    QueueConfigurations::defaultEntryReaderHeaderRegionCacheSize,
                    QueueConfigurations::defaultEntryReaderHeaderRegionsToMapAhead
            );
            assert ENTRY_READER_HEADER_MAPPING_STRATEGY != null;
        }
        return ENTRY_READER_HEADER_MAPPING_STRATEGY;
    }

    // ENTRY_READER_PAYLOAD_MAPPING_STRATEGY

    public static int defaultEntryReaderPayloadRegionSize() {
        return getIntProperty(ENTRY_READER_PAYLOAD_REGION_SIZE_PROPERTY, Constraints::validateRegionSize, MappingConfigurations::defaultRegionSize);
    }

    public static int defaultEntryReaderPayloadRegionCacheSize() {
        return getIntProperty(ENTRY_READER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY, Constraints::validateRegionCacheSize, MappingConfigurations::defaultRegionCacheSize);
    }

    public static int defaultEntryReaderPayloadRegionsToMapAhead() {
        return getIntProperty(ENTRY_READER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY, Constraints::validateRegionsToMapAhead, MappingConfigurations::defaultRegionsToMapAhead);
    }

    public static MappingStrategy defaultEntryReaderPayloadMappingStrategy() {
        if (ENTRY_READER_PAYLOAD_MAPPING_STRATEGY == null) {
            ENTRY_READER_PAYLOAD_MAPPING_STRATEGY = getMappingStrategyProperty(
                    ENTRY_READER_PAYLOAD_MAPPING_STRATEGY_PROPERTY,
                    ENTRY_READER_PAYLOAD_MAPPING_STRATEGY_DEFAULT,
                    QueueConfigurations::defaultEntryReaderPayloadRegionSize,
                    QueueConfigurations::defaultEntryReaderPayloadRegionCacheSize,
                    QueueConfigurations::defaultEntryReaderPayloadRegionsToMapAhead
            );
            assert ENTRY_READER_PAYLOAD_MAPPING_STRATEGY != null;
        }
        return ENTRY_READER_PAYLOAD_MAPPING_STRATEGY;
    }

    // ENTRY_ITERATOR_HEADER_MAPPING_STRATEGY

    public static int defaultEntryIteratorHeaderRegionSize() {
        return getIntProperty(ENTRY_ITERATOR_HEADER_REGION_SIZE_PROPERTY, Constraints::validateRegionSize, MappingConfigurations::defaultRegionSize);
    }

    public static int defaultEntryIteratorHeaderRegionCacheSize() {
        return getIntProperty(ENTRY_ITERATOR_HEADER_REGION_CACHE_SIZE_PROPERTY, Constraints::validateRegionCacheSize, MappingConfigurations::defaultRegionCacheSize);
    }

    public static int defaultEntryIteratorHeaderRegionsToMapAhead() {
        return getIntProperty(ENTRY_ITERATOR_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY, Constraints::validateRegionsToMapAhead, MappingConfigurations::defaultRegionsToMapAhead);
    }

    public static MappingStrategy defaultEntryIteratorHeaderMappingStrategy() {
        if (ENTRY_ITERATOR_HEADER_MAPPING_STRATEGY == null) {
            ENTRY_ITERATOR_HEADER_MAPPING_STRATEGY = getMappingStrategyProperty(
                    ENTRY_ITERATOR_HEADER_MAPPING_STRATEGY_PROPERTY,
                    ENTRY_ITERATOR_HEADER_MAPPING_STRATEGY_DEFAULT,
                    QueueConfigurations::defaultEntryIteratorHeaderRegionSize,
                    QueueConfigurations::defaultEntryIteratorHeaderRegionCacheSize,
                    QueueConfigurations::defaultEntryIteratorHeaderRegionsToMapAhead
            );
            assert ENTRY_ITERATOR_HEADER_MAPPING_STRATEGY != null;
        }
        return ENTRY_ITERATOR_HEADER_MAPPING_STRATEGY;
    }

    // ENTRY_ITERATOR_PAYLOAD_MAPPING_STRATEGY

    public static int defaultEntryIteratorPayloadRegionSize() {
        return getIntProperty(ENTRY_ITERATOR_PAYLOAD_REGION_SIZE_PROPERTY, Constraints::validateRegionSize, MappingConfigurations::defaultRegionSize);
    }

    public static int defaultEntryIteratorPayloadRegionCacheSize() {
        return getIntProperty(ENTRY_ITERATOR_PAYLOAD_REGION_CACHE_SIZE_PROPERTY, Constraints::validateRegionCacheSize, MappingConfigurations::defaultRegionCacheSize);
    }

    public static int defaultEntryIteratorPayloadRegionsToMapAhead() {
        return getIntProperty(ENTRY_ITERATOR_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY, Constraints::validateRegionsToMapAhead, MappingConfigurations::defaultRegionsToMapAhead);
    }

    public static MappingStrategy defaultEntryIteratorPayloadMappingStrategy() {
        if (ENTRY_ITERATOR_PAYLOAD_MAPPING_STRATEGY == null) {
            ENTRY_ITERATOR_PAYLOAD_MAPPING_STRATEGY = getMappingStrategyProperty(
                    ENTRY_ITERATOR_PAYLOAD_MAPPING_STRATEGY_PROPERTY,
                    ENTRY_ITERATOR_PAYLOAD_MAPPING_STRATEGY_DEFAULT,
                    QueueConfigurations::defaultEntryIteratorPayloadRegionSize,
                    QueueConfigurations::defaultEntryIteratorPayloadRegionCacheSize,
                    QueueConfigurations::defaultEntryIteratorPayloadRegionsToMapAhead
            );
            assert ENTRY_ITERATOR_PAYLOAD_MAPPING_STRATEGY != null;
        }
        return ENTRY_ITERATOR_PAYLOAD_MAPPING_STRATEGY;
    }

    // INDEX_READER_HEADER_MAPPING_STRATEGY

    public static int defaultIndexReaderHeaderRegionSize() {
        return getIntProperty(INDEX_READER_HEADER_REGION_SIZE_PROPERTY, Constraints::validateRegionSize, MappingConfigurations::defaultRegionSize);
    }

    public static int defaultIndexReaderHeaderRegionCacheSize() {
        return getIntProperty(INDEX_READER_HEADER_REGION_CACHE_SIZE_PROPERTY, Constraints::validateRegionCacheSize, MappingConfigurations::defaultRegionCacheSize);
    }

    public static int defaultIndexReaderHeaderRegionsToMapAhead() {
        return getIntProperty(INDEX_READER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY, Constraints::validateRegionsToMapAhead, MappingConfigurations::defaultRegionsToMapAhead);
    }

    public static MappingStrategy defaultIndexReaderHeaderMappingStrategy() {
        if (INDEX_READER_HEADER_MAPPING_STRATEGY == null) {
            INDEX_READER_HEADER_MAPPING_STRATEGY = getMappingStrategyProperty(
                    INDEX_READER_HEADER_MAPPING_STRATEGY_PROPERTY,
                    INDEX_READER_HEADER_MAPPING_STRATEGY_DEFAULT,
                    QueueConfigurations::defaultIndexReaderHeaderRegionSize,
                    QueueConfigurations::defaultIndexReaderHeaderRegionCacheSize,
                    QueueConfigurations::defaultIndexReaderHeaderRegionsToMapAhead
            );
            assert INDEX_READER_HEADER_MAPPING_STRATEGY != null;
        }
        return INDEX_READER_HEADER_MAPPING_STRATEGY;
    }

    // APPENDER_HEADER_MAPPING_STRATEGY

    public static int defaultAppenderHeaderRegionSize() {
        return getIntProperty(APPENDER_HEADER_REGION_SIZE_PROPERTY, Constraints::validateRegionSize, MappingConfigurations::defaultRegionSize);
    }

    public static int defaultAppenderHeaderRegionCacheSize() {
        return getIntProperty(APPENDER_HEADER_REGION_CACHE_SIZE_PROPERTY, Constraints::validateRegionCacheSize, MappingConfigurations::defaultRegionCacheSize);
    }

    public static int defaultAppenderHeaderRegionsToMapAhead() {
        return getIntProperty(APPENDER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY, Constraints::validateRegionsToMapAhead, MappingConfigurations::defaultRegionsToMapAhead);
    }

    public static MappingStrategy defaultAppenderHeaderMappingStrategy() {
        if (APPENDER_HEADER_MAPPING_STRATEGY == null) {
            APPENDER_HEADER_MAPPING_STRATEGY = getMappingStrategyProperty(
                    APPENDER_HEADER_MAPPING_STRATEGY_PROPERTY,
                    APPENDER_HEADER_MAPPING_STRATEGY_DEFAULT,
                    QueueConfigurations::defaultAppenderHeaderRegionSize,
                    QueueConfigurations::defaultAppenderHeaderRegionCacheSize,
                    QueueConfigurations::defaultAppenderHeaderRegionsToMapAhead
            );
            assert APPENDER_HEADER_MAPPING_STRATEGY != null;
        }
        return APPENDER_HEADER_MAPPING_STRATEGY;
    }

    // APPENDER_PAYLOAD_MAPPING_STRATEGY

    public static int defaultAppenderPayloadRegionSize() {
        return getIntProperty(APPENDER_PAYLOAD_REGION_SIZE_PROPERTY, Constraints::validateRegionSize, MappingConfigurations::defaultRegionSize);
    }

    public static int defaultAppenderPayloadRegionCacheSize() {
        return getIntProperty(APPENDER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY, Constraints::validateRegionCacheSize, MappingConfigurations::defaultRegionCacheSize);
    }

    public static int defaultAppenderPayloadRegionsToMapAhead() {
        return getIntProperty(APPENDER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY, Constraints::validateRegionsToMapAhead, MappingConfigurations::defaultRegionsToMapAhead);
    }

    public static MappingStrategy defaultAppenderPayloadMappingStrategy() {
        if (APPENDER_PAYLOAD_MAPPING_STRATEGY == null) {
            APPENDER_PAYLOAD_MAPPING_STRATEGY = getMappingStrategyProperty(
                    APPENDER_PAYLOAD_MAPPING_STRATEGY_PROPERTY,
                    APPENDER_PAYLOAD_MAPPING_STRATEGY_DEFAULT,
                    QueueConfigurations::defaultAppenderPayloadRegionSize,
                    QueueConfigurations::defaultAppenderPayloadRegionCacheSize,
                    QueueConfigurations::defaultAppenderPayloadRegionsToMapAhead
            );
            assert APPENDER_PAYLOAD_MAPPING_STRATEGY != null;
        }
        return APPENDER_PAYLOAD_MAPPING_STRATEGY;
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

    private static MappingStrategy getMappingStrategyProperty(final String mappingStrategyProperty,
                                                              final String mappingStrategyDefault,
                                                              final IntSupplier regionSizeSupplier,
                                                              final IntSupplier regionCacheSizeSupplier,
                                                              final IntSupplier regionsToMapAheadSupplier) {
        final String strategy = System.getProperty(mappingStrategyProperty, mappingStrategyDefault);
        switch (strategy) {
            case AheadMappingStrategy.NAME:
                return new AheadMappingStrategy(regionSizeSupplier.getAsInt(), regionCacheSizeSupplier.getAsInt(),
                        regionsToMapAheadSupplier.getAsInt());
            case SyncMappingStrategy.NAME:
                return new SyncMappingStrategy(regionSizeSupplier.getAsInt(), regionCacheSizeSupplier.getAsInt());
            default:
                return getObjProperty(mappingStrategyProperty, MappingStrategy.class,
                        (Supplier<? extends MappingStrategy>) MappingConfigurations::defaultAheadMappingStrategy);
        }
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

    private static boolean getBooleanProperty(final String propertyName, final boolean defaultValue) {
        final String propVal = System.getProperty(propertyName, null);
        return propVal == null ? defaultValue : Boolean.parseBoolean(propVal);
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
            throw new IllegalArgumentException("Invalid value for system property: " + propVal + "=" + propVal, e);
        }
    }

    private static <T> T getObjProperty(final String propertyName,
                                        final Class<T> type,
                                        final Supplier<? extends T> defaultValueSupplier) {
        final String propVal = System.getProperty(propertyName, null);
        return propVal == null ? defaultValueSupplier.get() : newObjInstance(propertyName, type);
    }

    private static <T> T newObjInstance(final String propVal, final Class<T> type) {
        try {
            final Class<?> clazz = Class.forName(propVal);
            final Object value = clazz.newInstance();
            return type.cast(value);
        } catch (final Exception e) {
            throw new IllegalArgumentException("Invalid value for system property: " + propVal + "=" + propVal, e);
        }
    }
}
