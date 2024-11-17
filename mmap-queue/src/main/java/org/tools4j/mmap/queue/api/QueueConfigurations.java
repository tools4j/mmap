/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2024 tools4j.org (Marco Terzer, Anton Anufriev)
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
package org.tools4j.mmap.queue.api;

import org.tools4j.mmap.region.api.AheadMappingStrategy;
import org.tools4j.mmap.region.api.MappingConfigurations;
import org.tools4j.mmap.region.api.MappingStrategy;
import org.tools4j.mmap.region.api.SyncMappingStrategy;
import org.tools4j.mmap.region.impl.Constraints;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import static org.tools4j.mmap.queue.impl.QueueConfigDefaults.QUEUE_CONFIG_DEFAULTS;

/**
 * Defines queue default configuration values and property constants to override default values via system properties.
 */
public enum QueueConfigurations {
    ;
    public static final String MAX_HEADER_FILE_SIZE_PROPERTY = "mmap.queue.maxHeaderFileSize";
    public static final int MAX_HEADER_FILE_SIZE_DEFAULT = MappingConfigurations.MAX_FILE_SIZE_DEFAULT;
    public static final String MAX_PAYLOAD_FILE_SIZE_PROPERTY = "mmap.queue.maxPayloadFileSize";
    public static final int MAX_PAYLOAD_FILE_SIZE_DEFAULT = MappingConfigurations.MAX_FILE_SIZE_DEFAULT;
    public static final String EXPAND_HEADER_FILE_PROPERTY = "mmap.queue.expandHeaderFile";
    public static final boolean EXPAND_HEADER_FILE_DEFAULT = true;
    public static final String EXPAND_PAYLOAD_FILES_PROPERTY = "mmap.queue.expandPayloadFiles";
    public static final boolean EXPAND_PAYLOAD_FILES_DEFAULT = true;
    public static final String ROLL_HEADER_FILE_PROPERTY = "mmap.queue.rollHeaderFile";
    public static final boolean ROLL_HEADER_FILE_DEFAULT = true;
    public static final String ROLL_PAYLOAD_FILES_PROPERTY = "mmap.queue.rollPayloadFiles";
    public static final boolean ROLL_PAYLOAD_FILES_DEFAULT = true;
    public static final String HEADER_FILES_TO_CREATE_AHEAD_PROPERTY = "mmap.queue.headerFilesToCreateAhead";
    public static final int HEADER_FILES_TO_CREATE_AHEAD_DEFAULT = 0;
    public static final String PAYLOAD_FILES_TO_CREATE_AHEAD_PROPERTY = "mmap.queue.payloadFilesToCreateAhead";
    public static final int PAYLOAD_FILES_TO_CREATE_AHEAD_DEFAULT = 0;
    public static final String CLOSE_POLLER_FILES_PROPERTY = "mmap.queue.closePollerFiles";
    public static final boolean CLOSE_POLLER_FILES_DEFAULT = true;
    public static final String CLOSE_READER_FILES_PROPERTY = "mmap.queue.closeReaderFiles";
    public static final boolean CLOSE_READER_FILES_DEFAULT = false;
    public static final String POLLER_HEADER_REGION_SIZE_PROPERTY = "mmap.queue.pollerHeaderRegionSize";
    public static final String POLLER_HEADER_REGION_CACHE_SIZE_PROPERTY = "mmap.queue.pollerHeaderRegionCacheSize";
    public static final String POLLER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.queue.pollerHeaderRegionsToMapAhead";
    public static final String POLLER_HEADER_MAPPING_STRATEGY_PROPERTY = "mmap.queue.pollerHeaderMappingStrategy";
    public static final String POLLER_PAYLOAD_REGION_SIZE_PROPERTY = "mmap.queue.pollerPayloadRegionSize";
    public static final String POLLER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY = "mmap.queue.pollerPayloadRegionCacheSize";
    public static final String POLLER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.queue.pollerPayloadRegionsToMapAhead";
    public static final String POLLER_PAYLOAD_MAPPING_STRATEGY_PROPERTY = "mmap.queue.pollerPayloadMappingStrategy";
    public static final String READER_HEADER_REGION_SIZE_PROPERTY = "mmap.queue.readerHeaderRegionSize";
    public static final String READER_HEADER_REGION_CACHE_SIZE_PROPERTY = "mmap.queue.readerHeaderRegionCacheSize";
    public static final String READER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.queue.readerHeaderRegionsToMapAhead";
    public static final String READER_HEADER_MAPPING_STRATEGY_PROPERTY = "mmap.queue.readerHeaderMappingStrategy";
    public static final String READER_PAYLOAD_REGION_SIZE_PROPERTY = "mmap.queue.readerPayloadRegionSize";
    public static final String READER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY = "mmap.queue.readerPayloadRegionCacheSize";
    public static final String READER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.queue.readerPayloadRegionsToMapAhead";
    public static final String READER_PAYLOAD_MAPPING_STRATEGY_PROPERTY = "mmap.queue.readerPayloadMappingStrategy";
    public static final String APPENDER_HEADER_REGION_SIZE_PROPERTY = "mmap.queue.appenderHeaderRegionSize";
    public static final String APPENDER_HEADER_REGION_CACHE_SIZE_PROPERTY = "mmap.queue.appenderHeaderRegionCacheSize";
    public static final String APPENDER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.queue.appenderHeaderRegionsToMapAhead";
    public static final String APPENDER_HEADER_MAPPING_STRATEGY_PROPERTY = "mmap.queue.appenderHeaderMappingStrategy";
    public static final String APPENDER_PAYLOAD_REGION_SIZE_PROPERTY = "mmap.queue.appenderPayloadRegionSize";
    public static final String APPENDER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY = "mmap.queue.appenderPayloadRegionCacheSize";
    public static final String APPENDER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.queue.appenderPayloadRegionsToMapAhead";
    public static final String APPENDER_PAYLOAD_MAPPING_STRATEGY_PROPERTY = "mmap.queue.appenderPayloadMappingStrategy";
    private static MappingStrategy POLLER_HEADER_MAPPING_STRATEGY;
    private static MappingStrategy POLLER_PAYLOAD_MAPPING_STRATEGY;
    private static MappingStrategy READER_HEADER_MAPPING_STRATEGY;
    private static MappingStrategy READER_PAYLOAD_MAPPING_STRATEGY;
    private static MappingStrategy APPENDER_HEADER_MAPPING_STRATEGY;
    private static MappingStrategy APPENDER_PAYLOAD_MAPPING_STRATEGY;

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

    public static boolean defaultClosePollerFiles() {
        return getBooleanProperty(CLOSE_POLLER_FILES_PROPERTY, CLOSE_POLLER_FILES_DEFAULT);
    }

    public static boolean defaultCloseReaderFiles() {
        return getBooleanProperty(CLOSE_READER_FILES_PROPERTY, CLOSE_READER_FILES_DEFAULT);
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
            POLLER_HEADER_MAPPING_STRATEGY = getMappingStrategyProperty(POLLER_HEADER_MAPPING_STRATEGY_PROPERTY);
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
            POLLER_PAYLOAD_MAPPING_STRATEGY = getMappingStrategyProperty(POLLER_PAYLOAD_MAPPING_STRATEGY_PROPERTY);
            assert POLLER_PAYLOAD_MAPPING_STRATEGY != null;
        }
        return POLLER_PAYLOAD_MAPPING_STRATEGY;
    }

    // READER_HEADER_MAPPING_STRATEGY

    public static int defaultReaderHeaderRegionSize() {
        return getIntProperty(READER_HEADER_REGION_SIZE_PROPERTY, Constraints::validateRegionSize, MappingConfigurations::defaultRegionSize);
    }

    public static int defaultReaderHeaderRegionCacheSize() {
        return getIntProperty(READER_HEADER_REGION_CACHE_SIZE_PROPERTY, Constraints::validateRegionCacheSize, MappingConfigurations::defaultRegionCacheSize);
    }

    public static int defaultReaderHeaderRegionsToMapAhead() {
        return getIntProperty(READER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY, Constraints::validateRegionsToMapAhead, MappingConfigurations::defaultRegionsToMapAhead);
    }

    public static MappingStrategy defaultReaderHeaderMappingStrategy() {
        if (READER_HEADER_MAPPING_STRATEGY == null) {
            READER_HEADER_MAPPING_STRATEGY = getMappingStrategyProperty(READER_HEADER_MAPPING_STRATEGY_PROPERTY);
            assert READER_HEADER_MAPPING_STRATEGY != null;
        }
        return READER_HEADER_MAPPING_STRATEGY;
    }

    // READER_PAYLOAD_MAPPING_STRATEGY

    public static int defaultReaderPayloadRegionSize() {
        return getIntProperty(READER_PAYLOAD_REGION_SIZE_PROPERTY, Constraints::validateRegionSize, MappingConfigurations::defaultRegionSize);
    }

    public static int defaultReaderPayloadRegionCacheSize() {
        return getIntProperty(READER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY, Constraints::validateRegionCacheSize, MappingConfigurations::defaultRegionCacheSize);
    }

    public static int defaultReaderPayloadRegionsToMapAhead() {
        return getIntProperty(READER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY, Constraints::validateRegionsToMapAhead, MappingConfigurations::defaultRegionsToMapAhead);
    }

    public static MappingStrategy defaultReaderPayloadMappingStrategy() {
        if (READER_PAYLOAD_MAPPING_STRATEGY == null) {
            READER_PAYLOAD_MAPPING_STRATEGY = getMappingStrategyProperty(READER_PAYLOAD_MAPPING_STRATEGY_PROPERTY);
            assert READER_PAYLOAD_MAPPING_STRATEGY != null;
        }
        return READER_PAYLOAD_MAPPING_STRATEGY;
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
            APPENDER_HEADER_MAPPING_STRATEGY = getMappingStrategyProperty(APPENDER_HEADER_MAPPING_STRATEGY_PROPERTY);
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
            APPENDER_PAYLOAD_MAPPING_STRATEGY = getMappingStrategyProperty(APPENDER_PAYLOAD_MAPPING_STRATEGY_PROPERTY);
            assert APPENDER_PAYLOAD_MAPPING_STRATEGY != null;
        }
        return APPENDER_PAYLOAD_MAPPING_STRATEGY;
    }

    public static QueueConfig defaultQueueConfig() {
        return QUEUE_CONFIG_DEFAULTS;
    }

    private static MappingStrategy getMappingStrategyProperty(final String mappingStrategyProperty) {
        final String propVal = System.getProperty(mappingStrategyProperty, null);
        if (propVal == null) {
            return MappingConfigurations.defaultMappingStrategy();
        }
        switch (propVal) {
            case AheadMappingStrategy.NAME:
                return MappingConfigurations.defaultAheadMappingStrategy();
            case SyncMappingStrategy.NAME:
                return MappingConfigurations.defaultSyncMappingStrategy();
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
