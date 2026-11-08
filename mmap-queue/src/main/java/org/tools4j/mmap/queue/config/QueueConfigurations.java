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
import org.tools4j.mmap.queue.impl.AppenderIdPool;

import java.util.function.IntSupplier;

import static org.tools4j.mmap.mapping.impl.SystemProperties.booleanProperty;
import static org.tools4j.mmap.mapping.impl.SystemProperties.enumProperty;
import static org.tools4j.mmap.mapping.impl.SystemProperties.intProperty;
import static org.tools4j.mmap.mapping.impl.SystemProperties.longProperty;
import static org.tools4j.mmap.queue.config.MappingStrategy.AsyncMapAheadStrategy;
import static org.tools4j.mmap.queue.config.MappingStrategy.SyncMappingStrategyWithAsyncUnmapping;
import static org.tools4j.mmap.queue.impl.AppenderConfigDefaults.APPENDER_CONFIG_DEFAULTS;
import static org.tools4j.mmap.queue.impl.IndexReaderConfigDefaults.INDEX_READER_CONFIG_DEFAULTS;
import static org.tools4j.mmap.queue.impl.QueueConfigDefaults.QUEUE_CONFIG_DEFAULTS;
import static org.tools4j.mmap.queue.impl.ReaderConfigDefaults.ENTRY_ITERATOR_CONFIG_DEFAULTS;
import static org.tools4j.mmap.queue.impl.ReaderConfigDefaults.ENTRY_READER_CONFIG_DEFAULTS;
import static org.tools4j.mmap.queue.impl.ReaderConfigDefaults.POLLER_CONFIG_DEFAULTS;

/**
 * Defines default configuration values for queues and system property names to override them. The {@code default..()}
 * methods return the value of the respective system property if set, and the default value otherwise. Property values
 * are validated when read, and an {@link IllegalArgumentException} is thrown for an invalid value. <p> The values
 * returned by these methods are used by the system default configurations such as {@link QueueConfig#getDefault()},
 * {@link AppenderConfig#getDefault()} and {@link ReaderConfig#getDefaultPollerConfig()}, and by configurators for
 * values that are not explicitly set. Lifecycle and error listener defaults are defined by
 * {@link MappingConfigurations}.
 */
public enum QueueConfigurations {
    ;
    /**
     * The default maximum number of rolled header or payload files kept open at once by an appender or reader: one each
     * for the accessing thread, the async mapping thread and the async unmapping thread, plus one for a file created
     * ahead.
     */
    public static final int MAX_OPEN_FILES_DEFAULT = 4;

    /**
     * Name of the system property to override the default
     * {@linkplain QueueConfig#maxAppenders() maximum number of appenders}.
     */
    public static final String MAX_APPENDERS_PROPERTY = "mmap.queue.maxAppenders";
    /**
     * The default {@linkplain QueueConfig#maxAppenders() maximum number of appenders}, used if system property
     * {@value #MAX_APPENDERS_PROPERTY} is not set.
     */
    public static final int MAX_APPENDERS_DEFAULT = AppenderIdPool.MAX_IDS;
    /**
     * Name of the system property to override the default
     * {@linkplain QueueLayoutConfig#minHeaderFileSize() minimum header file size}.
     */
    public static final String MIN_HEADER_FILE_SIZE_PROPERTY = "mmap.queue.minHeaderFileSize";
    /**
     * The default {@linkplain QueueLayoutConfig#minHeaderFileSize() minimum header file size}, used if system property
     * {@value #MIN_HEADER_FILE_SIZE_PROPERTY} is not set.
     */
    public static final long MIN_HEADER_FILE_SIZE_DEFAULT = 16*1024*1024L;
    /**
     * Name of the system property to override the default
     * {@linkplain QueueLayoutConfig#minPayloadFileSize() minimum payload file size}.
     */
    public static final String MIN_PAYLOAD_FILE_SIZE_PROPERTY = "mmap.queue.minPayloadFileSize";
    /**
     * The default {@linkplain QueueLayoutConfig#minPayloadFileSize() minimum payload file size}, used if system
     * property {@value #MIN_PAYLOAD_FILE_SIZE_PROPERTY} is not set.
     */
    public static final long MIN_PAYLOAD_FILE_SIZE_DEFAULT = 64*1024*1024L;
    /**
     * Name of the system property to override the default
     * {@linkplain QueueLayoutConfig#maxHeaderFileSize() maximum header file size}.
     */
    public static final String MAX_HEADER_FILE_SIZE_PROPERTY = "mmap.queue.maxHeaderFileSize";
    /**
     * The default {@linkplain QueueLayoutConfig#maxHeaderFileSize() maximum header file size}, used if system property
     * {@value #MAX_HEADER_FILE_SIZE_PROPERTY} is not set.
     */
    public static final long MAX_HEADER_FILE_SIZE_DEFAULT = 1024*1024*1024L;
    /**
     * Name of the system property to override the default
     * {@linkplain QueueLayoutConfig#maxPayloadFileSize() maximum payload file size}.
     */
    public static final String MAX_PAYLOAD_FILE_SIZE_PROPERTY = "mmap.queue.maxPayloadFileSize";
    /**
     * The default {@linkplain QueueLayoutConfig#maxPayloadFileSize() maximum payload file size}, used if system
     * property {@value #MAX_PAYLOAD_FILE_SIZE_PROPERTY} is not set.
     */
    public static final long MAX_PAYLOAD_FILE_SIZE_DEFAULT = 2*1024*1024*1024L;
    /**
     * Name of the system property to override the default
     * {@linkplain QueueLayoutConfig#headerRegionSize() header region size}.
     */
    public static final String HEADER_REGION_SIZE_PROPERTY = "mmap.queue.headerRegionSize";
    /**
     * Name of the system property to override the default
     * {@linkplain QueueLayoutConfig#payloadRegionSize() payload region size}.
     */
    public static final String PAYLOAD_REGION_SIZE_PROPERTY = "mmap.queue.payloadRegionSize";
    /**
     * Name of the system property to override the default
     * {@linkplain QueueLayoutConfig#expandHeaderFile() expand-header-file flag}.
     */
    public static final String EXPAND_HEADER_FILE_PROPERTY = "mmap.queue.expandHeaderFile";
    /**
     * The default {@linkplain QueueLayoutConfig#expandHeaderFile() expand-header-file flag}, used if system property
     * {@value #EXPAND_HEADER_FILE_PROPERTY} is not set.
     */
    public static final boolean EXPAND_HEADER_FILE_DEFAULT = true;
    /**
     * Name of the system property to override the default
     * {@linkplain QueueLayoutConfig#expandPayloadFiles() expand-payload-files flag}.
     */
    public static final String EXPAND_PAYLOAD_FILES_PROPERTY = "mmap.queue.expandPayloadFiles";
    /**
     * The default {@linkplain QueueLayoutConfig#expandPayloadFiles() expand-payload-files flag}, used if system
     * property {@value #EXPAND_PAYLOAD_FILES_PROPERTY} is not set.
     */
    public static final boolean EXPAND_PAYLOAD_FILES_DEFAULT = true;
    /**
     * Name of the system property to override the default
     * {@linkplain QueueLayoutConfig#rollHeaderFile() roll-header-file flag}.
     */
    public static final String ROLL_HEADER_FILE_PROPERTY = "mmap.queue.rollHeaderFile";
    /**
     * The default {@linkplain QueueLayoutConfig#rollHeaderFile() roll-header-file flag}, used if system property
     * {@value #ROLL_HEADER_FILE_PROPERTY} is not set.
     */
    public static final boolean ROLL_HEADER_FILE_DEFAULT = true;
    /**
     * Name of the system property to override the default
     * {@linkplain QueueLayoutConfig#rollPayloadFiles() roll-payload-files flag}.
     */
    public static final String ROLL_PAYLOAD_FILES_PROPERTY = "mmap.queue.rollPayloadFiles";
    /**
     * The default {@linkplain QueueLayoutConfig#rollPayloadFiles() roll-payload-files flag}, used if system property
     * {@value #ROLL_PAYLOAD_FILES_PROPERTY} is not set.
     */
    public static final boolean ROLL_PAYLOAD_FILES_DEFAULT = true;

    /**
     * Name of the system property to override the default
     * {@linkplain ReaderConfig#maxOpenHeaderFiles() maximum number of open header files} of pollers.
     */
    public static final String MAX_OPEN_POLLER_HEADER_FILES_PROPERTY =  "mmap.queue.maxOpenPollerHeaderFiles";
    /**
     * The default {@linkplain ReaderConfig#maxOpenHeaderFiles() maximum number of open header files} of pollers, used
     * if system property {@value #MAX_OPEN_POLLER_HEADER_FILES_PROPERTY} is not set.
     */
    public static final int MAX_OPEN_POLLER_HEADER_FILES_DEFAULT = MAX_OPEN_FILES_DEFAULT;
    /**
     * Name of the system property to override the default
     * {@linkplain ReaderConfig#maxOpenPayloadFiles() maximum number of open payload files} of pollers.
     */
    public static final String MAX_OPEN_POLLER_PAYLOAD_FILES_PROPERTY = "mmap.queue.maxOpenPollerPayloadFiles";
    /**
     * The default {@linkplain ReaderConfig#maxOpenPayloadFiles() maximum number of open payload files} of pollers, used
     * if system property {@value #MAX_OPEN_POLLER_PAYLOAD_FILES_PROPERTY} is not set.
     */
    public static final int MAX_OPEN_POLLER_PAYLOAD_FILES_DEFAULT = MAX_OPEN_FILES_DEFAULT;

    /**
     * Name of the system property to override the default
     * {@linkplain ReaderConfig#maxOpenHeaderFiles() maximum number of open header files} of entry readers.
     */
    public static final String MAX_OPEN_ENTRY_READER_HEADER_FILES_PROPERTY =  "mmap.queue.maxOpenEntryReaderHeaderFiles";
    /**
     * The default {@linkplain ReaderConfig#maxOpenHeaderFiles() maximum number of open header files} of entry readers,
     * used if system property {@value #MAX_OPEN_ENTRY_READER_HEADER_FILES_PROPERTY} is not set.
     */
    public static final int MAX_OPEN_ENTRY_READER_HEADER_FILES_DEFAULT = MAX_OPEN_FILES_DEFAULT;
    /**
     * Name of the system property to override the default
     * {@linkplain ReaderConfig#maxOpenPayloadFiles() maximum number of open payload files} of entry readers.
     */
    public static final String MAX_OPEN_ENTRY_READER_PAYLOAD_FILES_PROPERTY = "mmap.queue.maxOpenEntryReaderPayloadFiles";
    /**
     * The default {@linkplain ReaderConfig#maxOpenPayloadFiles() maximum number of open payload files} of entry
     * readers, used if system property {@value #MAX_OPEN_ENTRY_READER_PAYLOAD_FILES_PROPERTY} is not set.
     */
    public static final int MAX_OPEN_ENTRY_READER_PAYLOAD_FILES_DEFAULT = MAX_OPEN_FILES_DEFAULT;

    /**
     * Name of the system property to override the default
     * {@linkplain ReaderConfig#maxOpenHeaderFiles() maximum number of open header files} of entry iterators.
     */
    public static final String MAX_OPEN_ENTRY_ITERATOR_HEADER_FILES_PROPERTY =  "mmap.queue.maxOpenEntryIteratorHeaderFiles";
    /**
     * The default {@linkplain ReaderConfig#maxOpenHeaderFiles() maximum number of open header files} of entry
     * iterators, used if system property {@value #MAX_OPEN_ENTRY_ITERATOR_HEADER_FILES_PROPERTY} is not set.
     */
    public static final int MAX_OPEN_ENTRY_ITERATOR_HEADER_FILES_DEFAULT = MAX_OPEN_FILES_DEFAULT;
    /**
     * Name of the system property to override the default
     * {@linkplain ReaderConfig#maxOpenPayloadFiles() maximum number of open payload files} of entry iterators.
     */
    public static final String MAX_OPEN_ENTRY_ITERATOR_PAYLOAD_FILES_PROPERTY = "mmap.queue.maxOpenEntryIteratorPayloadFiles";
    /**
     * The default {@linkplain ReaderConfig#maxOpenPayloadFiles() maximum number of open payload files} of entry
     * iterators, used if system property {@value #MAX_OPEN_ENTRY_ITERATOR_PAYLOAD_FILES_PROPERTY} is not set.
     */
    public static final int MAX_OPEN_ENTRY_ITERATOR_PAYLOAD_FILES_DEFAULT = MAX_OPEN_FILES_DEFAULT;

    /**
     * Name of the system property to override the default
     * {@linkplain IndexReaderConfig#maxOpenHeaderFiles() maximum number of open header files} of index readers.
     */
    public static final String MAX_OPEN_INDEX_READER_HEADER_FILES_PROPERTY =  "mmap.queue.maxOpenIndexReaderHeaderFiles";
    /**
     * The default {@linkplain IndexReaderConfig#maxOpenHeaderFiles() maximum number of open header files} of index
     * readers, used if system property {@value #MAX_OPEN_INDEX_READER_HEADER_FILES_PROPERTY} is not set.
     */
    public static final int MAX_OPEN_INDEX_READER_HEADER_FILES_DEFAULT = MAX_OPEN_FILES_DEFAULT;

    /**
     * Name of the system property to override the default
     * {@linkplain AppenderConfig#maxOpenHeaderFiles() maximum number of open header files} of appenders.
     */
    public static final String MAX_OPEN_APPENDER_HEADER_FILES_PROPERTY =  "mmap.queue.maxOpenAppenderHeaderFiles";
    /**
     * The default {@linkplain AppenderConfig#maxOpenHeaderFiles() maximum number of open header files} of appenders,
     * used if system property {@value #MAX_OPEN_APPENDER_HEADER_FILES_PROPERTY} is not set.
     */
    public static final int MAX_OPEN_APPENDER_HEADER_FILES_DEFAULT = MAX_OPEN_FILES_DEFAULT;
    /**
     * Name of the system property to override the default
     * {@linkplain AppenderConfig#maxOpenPayloadFiles() maximum number of open payload files} of appenders.
     */
    public static final String MAX_OPEN_APPENDER_PAYLOAD_FILES_PROPERTY = "mmap.queue.maxOpenAppenderPayloadFiles";
    /**
     * The default {@linkplain AppenderConfig#maxOpenPayloadFiles() maximum number of open payload files} of appenders,
     * used if system property {@value #MAX_OPEN_APPENDER_PAYLOAD_FILES_PROPERTY} is not set.
     */
    public static final int MAX_OPEN_APPENDER_PAYLOAD_FILES_DEFAULT = MAX_OPEN_FILES_DEFAULT;
    /**
     * Name of the system property to override the default
     * {@linkplain AppenderConfig#headerFilesToCreateAhead() number of header files to create ahead} by appenders.
     */
    public static final String APPENDER_HEADER_FILES_TO_CREATE_AHEAD_PROPERTY = "mmap.queue.appenderHeaderFilesToCreateAhead";
    /**
     * The default {@linkplain AppenderConfig#headerFilesToCreateAhead() number of header files to create ahead} by
     * appenders, used if system property {@value #APPENDER_HEADER_FILES_TO_CREATE_AHEAD_PROPERTY} is not set.
     */
    public static final int APPENDER_HEADER_FILES_TO_CREATE_AHEAD_DEFAULT = 1;
    /**
     * Name of the system property to override the default
     * {@linkplain AppenderConfig#payloadFilesToCreateAhead() number of payload files to create ahead} by appenders.
     */
    public static final String APPENDER_PAYLOAD_FILES_TO_CREATE_AHEAD_PROPERTY = "mmap.queue.appenderPayloadFilesToCreateAhead";
    /**
     * The default {@linkplain AppenderConfig#payloadFilesToCreateAhead() number of payload files to create ahead} by
     * appenders, used if system property {@value #APPENDER_PAYLOAD_FILES_TO_CREATE_AHEAD_PROPERTY} is not set.
     */
    public static final int APPENDER_PAYLOAD_FILES_TO_CREATE_AHEAD_DEFAULT = 1;
    /**
     * Name of the system property to override the default
     * {@linkplain AppenderConfig#prefillHeaderFiles() prefill-header-files flag} of appenders.
     */
    public static final String APPENDER_PREFILL_HEADER_FILES_PROPERTY = "mmap.queue.appenderPrefillHeaderFiles";
    /**
     * The default {@linkplain AppenderConfig#prefillHeaderFiles() prefill-header-files flag} of appenders, used if
     * system property {@value #APPENDER_PREFILL_HEADER_FILES_PROPERTY} is not set.
     */
    public static final boolean APPENDER_PREFILL_HEADER_FILES_DEFAULT = false;
    /**
     * Name of the system property to override the default
     * {@linkplain AppenderConfig#prefillPayloadFiles() prefill-payload-files flag} of appenders.
     */
    public static final String APPENDER_PREFILL_PAYLOAD_FILES_PROPERTY = "mmap.queue.appenderPrefillPayloadFiles";
    /**
     * The default {@linkplain AppenderConfig#prefillPayloadFiles() prefill-payload-files flag} of appenders, used if
     * system property {@value #APPENDER_PREFILL_PAYLOAD_FILES_PROPERTY} is not set.
     */
    public static final boolean APPENDER_PREFILL_PAYLOAD_FILES_DEFAULT = false;

    /**
     * Name of the system property to override the region cache size of the default header mapping strategy of pollers.
     */
    public static final String POLLER_HEADER_REGION_CACHE_SIZE_PROPERTY = "mmap.queue.pollerHeaderRegionCacheSize";
    /**
     * Name of the system property to override the regions to map ahead of the default header mapping strategy of
     * pollers, applicable only to strategies with async mapping.
     */
    public static final String POLLER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.queue.pollerHeaderRegionsToMapAhead";
    /**
     * Name of the system property to override the default
     * {@linkplain ReaderConfig#headerMappingStrategy() header mapping strategy} of pollers, with the name of a
     * {@link MappingStrategy} constant.
     */
    public static final String POLLER_HEADER_MAPPING_STRATEGY_PROPERTY = "mmap.queue.pollerHeaderMappingStrategy";
    /**
     * The default {@linkplain ReaderConfig#headerMappingStrategy() header mapping strategy} of pollers, used if system
     * property {@value #POLLER_HEADER_MAPPING_STRATEGY_PROPERTY} is not set.
     */
    public static final MappingStrategy POLLER_HEADER_MAPPING_STRATEGY_DEFAULT = AsyncMapAheadStrategy;
    /**
     * Name of the system property to override the region cache size of the default payload mapping strategy of pollers.
     */
    public static final String POLLER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY = "mmap.queue.pollerPayloadRegionCacheSize";
    /**
     * Name of the system property to override the regions to map ahead of the default payload mapping strategy of
     * pollers, applicable only to strategies with async mapping.
     */
    public static final String POLLER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.queue.pollerPayloadRegionsToMapAhead";
    /**
     * Name of the system property to override the default
     * {@linkplain ReaderConfig#payloadMappingStrategy() payload mapping strategy} of pollers, with the name of a
     * {@link MappingStrategy} constant.
     */
    public static final String POLLER_PAYLOAD_MAPPING_STRATEGY_PROPERTY = "mmap.queue.pollerPayloadMappingStrategy";
    /**
     * The default {@linkplain ReaderConfig#payloadMappingStrategy() payload mapping strategy} of pollers, used if
     * system property {@value #POLLER_PAYLOAD_MAPPING_STRATEGY_PROPERTY} is not set.
     */
    public static final MappingStrategy POLLER_PAYLOAD_MAPPING_STRATEGY_DEFAULT = AsyncMapAheadStrategy;
    /**
     * Name of the system property to override the region cache size of the default header mapping strategy of entry
     * readers.
     */
    public static final String ENTRY_READER_HEADER_REGION_CACHE_SIZE_PROPERTY = "mmap.queue.entryReaderHeaderRegionCacheSize";
    /**
     * Name of the system property to override the regions to map ahead of the default header mapping strategy of entry
     * readers, applicable only to strategies with async mapping.
     */
    public static final String ENTRY_READER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.queue.entryReaderHeaderRegionsToMapAhead";
    /**
     * Name of the system property to override the default
     * {@linkplain ReaderConfig#headerMappingStrategy() header mapping strategy} of entry readers, with the name of a
     * {@link MappingStrategy} constant.
     */
    public static final String ENTRY_READER_HEADER_MAPPING_STRATEGY_PROPERTY = "mmap.queue.entryReaderHeaderMappingStrategy";
    /**
     * The default {@linkplain ReaderConfig#headerMappingStrategy() header mapping strategy} of entry readers, used if
     * system property {@value #ENTRY_READER_HEADER_MAPPING_STRATEGY_PROPERTY} is not set.
     */
    public static final MappingStrategy ENTRY_READER_HEADER_MAPPING_STRATEGY_DEFAULT = SyncMappingStrategyWithAsyncUnmapping;
    /**
     * Name of the system property to override the region cache size of the default payload mapping strategy of entry
     * readers.
     */
    public static final String ENTRY_READER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY = "mmap.queue.entryReaderPayloadRegionCacheSize";
    /**
     * Name of the system property to override the regions to map ahead of the default payload mapping strategy of entry
     * readers, applicable only to strategies with async mapping.
     */
    public static final String ENTRY_READER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.queue.entryReaderPayloadRegionsToMapAhead";
    /**
     * Name of the system property to override the default
     * {@linkplain ReaderConfig#payloadMappingStrategy() payload mapping strategy} of entry readers, with the name of a
     * {@link MappingStrategy} constant.
     */
    public static final String ENTRY_READER_PAYLOAD_MAPPING_STRATEGY_PROPERTY = "mmap.queue.entryReaderPayloadMappingStrategy";
    /**
     * The default {@linkplain ReaderConfig#payloadMappingStrategy() payload mapping strategy} of entry readers, used if
     * system property {@value #ENTRY_READER_PAYLOAD_MAPPING_STRATEGY_PROPERTY} is not set.
     */
    public static final MappingStrategy ENTRY_READER_PAYLOAD_MAPPING_STRATEGY_DEFAULT = SyncMappingStrategyWithAsyncUnmapping;
    /**
     * Name of the system property to override the region cache size of the default header mapping strategy of entry
     * iterators.
     */
    public static final String ENTRY_ITERATOR_HEADER_REGION_CACHE_SIZE_PROPERTY = "mmap.queue.entryIteratorHeaderRegionCacheSize";
    /**
     * Name of the system property to override the regions to map ahead of the default header mapping strategy of entry
     * iterators, applicable only to strategies with async mapping.
     */
    public static final String ENTRY_ITERATOR_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.queue.entryIteratorHeaderRegionsToMapAhead";
    /**
     * Name of the system property to override the default
     * {@linkplain ReaderConfig#headerMappingStrategy() header mapping strategy} of entry iterators, with the name of a
     * {@link MappingStrategy} constant.
     */
    public static final String ENTRY_ITERATOR_HEADER_MAPPING_STRATEGY_PROPERTY = "mmap.queue.entryIteratorHeaderMappingStrategy";
    /**
     * The default {@linkplain ReaderConfig#headerMappingStrategy() header mapping strategy} of entry iterators, used if
     * system property {@value #ENTRY_ITERATOR_HEADER_MAPPING_STRATEGY_PROPERTY} is not set.
     */
    public static final MappingStrategy ENTRY_ITERATOR_HEADER_MAPPING_STRATEGY_DEFAULT = AsyncMapAheadStrategy;
    /**
     * Name of the system property to override the region cache size of the default payload mapping strategy of entry
     * iterators.
     */
    public static final String ENTRY_ITERATOR_PAYLOAD_REGION_CACHE_SIZE_PROPERTY = "mmap.queue.entryIteratorPayloadRegionCacheSize";
    /**
     * Name of the system property to override the regions to map ahead of the default payload mapping strategy of entry
     * iterators, applicable only to strategies with async mapping.
     */
    public static final String ENTRY_ITERATOR_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.queue.entryIteratorPayloadRegionsToMapAhead";
    /**
     * Name of the system property to override the default
     * {@linkplain ReaderConfig#payloadMappingStrategy() payload mapping strategy} of entry iterators, with the name of
     * a {@link MappingStrategy} constant.
     */
    public static final String ENTRY_ITERATOR_PAYLOAD_MAPPING_STRATEGY_PROPERTY = "mmap.queue.entryIteratorPayloadMappingStrategy";
    /**
     * The default {@linkplain ReaderConfig#payloadMappingStrategy() payload mapping strategy} of entry iterators, used
     * if system property {@value #ENTRY_ITERATOR_PAYLOAD_MAPPING_STRATEGY_PROPERTY} is not set.
     */
    public static final MappingStrategy ENTRY_ITERATOR_PAYLOAD_MAPPING_STRATEGY_DEFAULT = AsyncMapAheadStrategy;
    /**
     * Name of the system property to override the region cache size of the default header mapping strategy of index
     * readers.
     */
    public static final String INDEX_READER_HEADER_REGION_CACHE_SIZE_PROPERTY = "mmap.queue.indexReaderHeaderRegionCacheSize";
    /**
     * Name of the system property to override the regions to map ahead of the default header mapping strategy of index
     * readers, applicable only to strategies with async mapping.
     */
    public static final String INDEX_READER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.queue.indexReaderHeaderRegionsToMapAhead";
    /**
     * Name of the system property to override the default
     * {@linkplain IndexReaderConfig#headerMappingStrategy() header mapping strategy} of index readers, with the name of
     * a {@link MappingStrategy} constant.
     */
    public static final String INDEX_READER_HEADER_MAPPING_STRATEGY_PROPERTY = "mmap.queue.indexReaderHeaderMappingStrategy";
    /**
     * The default {@linkplain IndexReaderConfig#headerMappingStrategy() header mapping strategy} of index readers, used
     * if system property {@value #INDEX_READER_HEADER_MAPPING_STRATEGY_PROPERTY} is not set.
     */
    public static final MappingStrategy INDEX_READER_HEADER_MAPPING_STRATEGY_DEFAULT = SyncMappingStrategyWithAsyncUnmapping;

    /**
     * Name of the system property to override the region cache size of the default header mapping strategy of
     * appenders.
     */
    public static final String APPENDER_HEADER_REGION_CACHE_SIZE_PROPERTY = "mmap.queue.appenderHeaderRegionCacheSize";
    /**
     * Name of the system property to override the regions to map ahead of the default header mapping strategy of
     * appenders, applicable only to strategies with async mapping.
     */
    public static final String APPENDER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.queue.appenderHeaderRegionsToMapAhead";
    /**
     * Name of the system property to override the default
     * {@linkplain AppenderConfig#headerMappingStrategy() header mapping strategy} of appenders, with the name of a
     * {@link MappingStrategy} constant.
     */
    public static final String APPENDER_HEADER_MAPPING_STRATEGY_PROPERTY = "mmap.queue.appenderHeaderMappingStrategy";
    /**
     * The default {@linkplain AppenderConfig#headerMappingStrategy() header mapping strategy} of appenders, used if
     * system property {@value #APPENDER_HEADER_MAPPING_STRATEGY_PROPERTY} is not set.
     */
    public static final MappingStrategy APPENDER_HEADER_MAPPING_STRATEGY_DEFAULT = AsyncMapAheadStrategy;
    /**
     * Name of the system property to override the region cache size of the default payload mapping strategy of
     * appenders.
     */
    public static final String APPENDER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY = "mmap.queue.appenderPayloadRegionCacheSize";
    /**
     * Name of the system property to override the regions to map ahead of the default payload mapping strategy of
     * appenders, applicable only to strategies with async mapping.
     */
    public static final String APPENDER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.queue.appenderPayloadRegionsToMapAhead";
    /**
     * Name of the system property to override the default
     * {@linkplain AppenderConfig#payloadMappingStrategy() payload mapping strategy} of appenders, with the name of a
     * {@link MappingStrategy} constant.
     */
    public static final String APPENDER_PAYLOAD_MAPPING_STRATEGY_PROPERTY = "mmap.queue.appenderPayloadMappingStrategy";
    /**
     * The default {@linkplain AppenderConfig#payloadMappingStrategy() payload mapping strategy} of appenders, used if
     * system property {@value #APPENDER_PAYLOAD_MAPPING_STRATEGY_PROPERTY} is not set.
     */
    public static final MappingStrategy APPENDER_PAYLOAD_MAPPING_STRATEGY_DEFAULT = AsyncMapAheadStrategy;

    /**
     * Returns the default {@linkplain QueueConfig#maxAppenders() maximum number of appenders}.
     *
     * @return the value of system property {@value #MAX_APPENDERS_PROPERTY} if set, and {@link #MAX_APPENDERS_DEFAULT}
     *         otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static int defaultMaxAppenders() {
        return intProperty(MAX_APPENDERS_PROPERTY, AppenderIdPool::validateMaxAppenders, MAX_APPENDERS_DEFAULT);
    }

    /**
     * Returns the default {@linkplain QueueLayoutConfig#minHeaderFileSize() minimum header file size}.
     *
     * @return the value of system property {@value #MIN_HEADER_FILE_SIZE_PROPERTY} if set, and
     *         {@link #MIN_HEADER_FILE_SIZE_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static long defaultMinHeaderFileSize() {
        return longProperty(MIN_HEADER_FILE_SIZE_PROPERTY, Constraints::validateMinFileSize, MIN_HEADER_FILE_SIZE_DEFAULT);
    }

    /**
     * Returns the default {@linkplain QueueLayoutConfig#minPayloadFileSize() minimum payload file size}.
     *
     * @return the value of system property {@value #MIN_PAYLOAD_FILE_SIZE_PROPERTY} if set, and
     *         {@link #MIN_PAYLOAD_FILE_SIZE_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static long defaultMinPayloadFileSize() {
        return longProperty(MIN_PAYLOAD_FILE_SIZE_PROPERTY, Constraints::validateMinFileSize, MIN_PAYLOAD_FILE_SIZE_DEFAULT);
    }

    /**
     * Returns the default {@linkplain QueueLayoutConfig#maxHeaderFileSize() maximum header file size}.
     *
     * @return the value of system property {@value #MAX_HEADER_FILE_SIZE_PROPERTY} if set, and
     *         {@link #MAX_HEADER_FILE_SIZE_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static long defaultMaxHeaderFileSize() {
        return longProperty(MAX_HEADER_FILE_SIZE_PROPERTY, Constraints::validateMaxFileSize, MAX_HEADER_FILE_SIZE_DEFAULT);
    }

    /**
     * Returns the default {@linkplain QueueLayoutConfig#maxPayloadFileSize() maximum payload file size}.
     *
     * @return the value of system property {@value #MAX_PAYLOAD_FILE_SIZE_PROPERTY} if set, and
     *         {@link #MAX_PAYLOAD_FILE_SIZE_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static long defaultMaxPayloadFileSize() {
        return longProperty(MAX_PAYLOAD_FILE_SIZE_PROPERTY, Constraints::validateMaxFileSize, MAX_PAYLOAD_FILE_SIZE_DEFAULT);
    }

    /**
     * Returns the default {@linkplain QueueLayoutConfig#headerRegionSize() header region size}.
     *
     * @return the value of system property {@value #HEADER_REGION_SIZE_PROPERTY} if set, and
     *         {@link MappingConfigurations#defaultRegionSize()} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static int defaultHeaderRegionSize() {
        return intProperty(HEADER_REGION_SIZE_PROPERTY, Constraints::validateRegionSize, MappingConfigurations::defaultRegionSize);
    }

    /**
     * Returns the default {@linkplain QueueLayoutConfig#payloadRegionSize() payload region size}.
     *
     * @return the value of system property {@value #PAYLOAD_REGION_SIZE_PROPERTY} if set, and
     *         {@link MappingConfigurations#defaultRegionSize()} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static int defaultPayloadRegionSize() {
        return intProperty(PAYLOAD_REGION_SIZE_PROPERTY, Constraints::validateRegionSize, MappingConfigurations::defaultRegionSize);
    }

    /**
     * Returns the default {@linkplain QueueLayoutConfig#expandHeaderFile() expand-header-file flag}.
     *
     * @return the value of system property {@value #EXPAND_HEADER_FILE_PROPERTY} if set, and
     *         {@link #EXPAND_HEADER_FILE_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static boolean defaultExpandHeaderFile() {
        return booleanProperty(EXPAND_HEADER_FILE_PROPERTY, EXPAND_HEADER_FILE_DEFAULT);
    }

    /**
     * Returns the default {@linkplain QueueLayoutConfig#expandPayloadFiles() expand-payload-files flag}.
     *
     * @return the value of system property {@value #EXPAND_PAYLOAD_FILES_PROPERTY} if set, and
     *         {@link #EXPAND_PAYLOAD_FILES_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static boolean defaultExpandPayloadFiles() {
        return booleanProperty(EXPAND_PAYLOAD_FILES_PROPERTY, EXPAND_PAYLOAD_FILES_DEFAULT);
    }

    /**
     * Returns the default {@linkplain QueueLayoutConfig#rollHeaderFile() roll-header-file flag}.
     *
     * @return the value of system property {@value #ROLL_HEADER_FILE_PROPERTY} if set, and
     *         {@link #ROLL_HEADER_FILE_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static boolean defaultRollHeaderFile() {
        return booleanProperty(ROLL_HEADER_FILE_PROPERTY, ROLL_HEADER_FILE_DEFAULT);
    }

    /**
     * Returns the default {@linkplain QueueLayoutConfig#rollPayloadFiles() roll-payload-files flag}.
     *
     * @return the value of system property {@value #ROLL_PAYLOAD_FILES_PROPERTY} if set, and
     *         {@link #ROLL_PAYLOAD_FILES_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static boolean defaultRollPayloadFiles() {
        return booleanProperty(ROLL_PAYLOAD_FILES_PROPERTY, ROLL_PAYLOAD_FILES_DEFAULT);
    }


    // POLLER

    /**
     * Returns the default {@linkplain ReaderConfig#maxOpenHeaderFiles() maximum number of open header files} of
     * pollers.
     *
     * @return the value of system property {@value #MAX_OPEN_POLLER_HEADER_FILES_PROPERTY} if set, and
     *         {@link #MAX_OPEN_POLLER_HEADER_FILES_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static int defaultMaxOpenPollerHeaderFiles() {
        return intProperty(MAX_OPEN_POLLER_HEADER_FILES_PROPERTY, Constraints::validateMaxOpenFiles, MAX_OPEN_POLLER_HEADER_FILES_DEFAULT);
    }

    /**
     * Returns the default {@linkplain ReaderConfig#maxOpenPayloadFiles() maximum number of open payload files} of
     * pollers.
     *
     * @return the value of system property {@value #MAX_OPEN_POLLER_PAYLOAD_FILES_PROPERTY} if set, and
     *         {@link #MAX_OPEN_POLLER_PAYLOAD_FILES_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static int defaultMaxOpenPollerPayloadFiles() {
        return intProperty(MAX_OPEN_POLLER_PAYLOAD_FILES_PROPERTY, Constraints::validateMaxOpenFiles, MAX_OPEN_POLLER_PAYLOAD_FILES_DEFAULT);
    }

    // ENTRY_READER

    /**
     * Returns the default {@linkplain ReaderConfig#maxOpenHeaderFiles() maximum number of open header files} of entry
     * readers.
     *
     * @return the value of system property {@value #MAX_OPEN_ENTRY_READER_HEADER_FILES_PROPERTY} if set, and
     *         {@link #MAX_OPEN_ENTRY_READER_HEADER_FILES_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static int defaultMaxOpenEntryReaderHeaderFiles() {
        return intProperty(MAX_OPEN_ENTRY_READER_HEADER_FILES_PROPERTY, Constraints::validateMaxOpenFiles, MAX_OPEN_ENTRY_READER_HEADER_FILES_DEFAULT);
    }

    /**
     * Returns the default {@linkplain ReaderConfig#maxOpenPayloadFiles() maximum number of open payload files} of entry
     * readers.
     *
     * @return the value of system property {@value #MAX_OPEN_ENTRY_READER_PAYLOAD_FILES_PROPERTY} if set, and
     *         {@link #MAX_OPEN_ENTRY_READER_PAYLOAD_FILES_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static int defaultMaxOpenEntryReaderPayloadFiles() {
        return intProperty(MAX_OPEN_ENTRY_READER_PAYLOAD_FILES_PROPERTY, Constraints::validateMaxOpenFiles, MAX_OPEN_ENTRY_READER_PAYLOAD_FILES_DEFAULT);
    }

    // ENTRY_ITERATOR

    /**
     * Returns the default {@linkplain ReaderConfig#maxOpenHeaderFiles() maximum number of open header files} of entry
     * iterators.
     *
     * @return the value of system property {@value #MAX_OPEN_ENTRY_ITERATOR_HEADER_FILES_PROPERTY} if set, and
     *         {@link #MAX_OPEN_ENTRY_ITERATOR_HEADER_FILES_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static int defaultMaxOpenEntryIteratorHeaderFiles() {
        return intProperty(MAX_OPEN_ENTRY_ITERATOR_HEADER_FILES_PROPERTY, Constraints::validateMaxOpenFiles, MAX_OPEN_ENTRY_ITERATOR_HEADER_FILES_DEFAULT);
    }

    /**
     * Returns the default {@linkplain ReaderConfig#maxOpenPayloadFiles() maximum number of open payload files} of entry
     * iterators.
     *
     * @return the value of system property {@value #MAX_OPEN_ENTRY_ITERATOR_PAYLOAD_FILES_PROPERTY} if set, and
     *         {@link #MAX_OPEN_ENTRY_ITERATOR_PAYLOAD_FILES_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static int defaultMaxOpenEntryIteratorPayloadFiles() {
        return intProperty(MAX_OPEN_ENTRY_ITERATOR_PAYLOAD_FILES_PROPERTY, Constraints::validateMaxOpenFiles, MAX_OPEN_ENTRY_ITERATOR_PAYLOAD_FILES_DEFAULT);
    }

    // INDEX_READER
    /**
     * Returns the default {@linkplain IndexReaderConfig#maxOpenHeaderFiles() maximum number of open header files} of
     * index readers.
     *
     * @return the value of system property {@value #MAX_OPEN_INDEX_READER_HEADER_FILES_PROPERTY} if set, and
     *         {@link #MAX_OPEN_INDEX_READER_HEADER_FILES_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static int defaultMaxOpenIndexReaderHeaderFiles() {
        return intProperty(MAX_OPEN_INDEX_READER_HEADER_FILES_PROPERTY, Constraints::validateMaxOpenFiles, MAX_OPEN_INDEX_READER_HEADER_FILES_DEFAULT);
    }

    // APPENDER
    /**
     * Returns the default {@linkplain AppenderConfig#maxOpenHeaderFiles() maximum number of open header files} of
     * appenders.
     *
     * @return the value of system property {@value #MAX_OPEN_APPENDER_HEADER_FILES_PROPERTY} if set, and
     *         {@link #MAX_OPEN_APPENDER_HEADER_FILES_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static int defaultMaxOpenAppenderHeaderFiles() {
        return intProperty(MAX_OPEN_APPENDER_HEADER_FILES_PROPERTY, Constraints::validateMaxOpenFiles, MAX_OPEN_APPENDER_HEADER_FILES_DEFAULT);
    }

    /**
     * Returns the default {@linkplain AppenderConfig#maxOpenPayloadFiles() maximum number of open payload files} of
     * appenders.
     *
     * @return the value of system property {@value #MAX_OPEN_APPENDER_PAYLOAD_FILES_PROPERTY} if set, and
     *         {@link #MAX_OPEN_APPENDER_PAYLOAD_FILES_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static int defaultMaxOpenAppenderPayloadFiles() {
        return intProperty(MAX_OPEN_APPENDER_PAYLOAD_FILES_PROPERTY, Constraints::validateMaxOpenFiles, MAX_OPEN_APPENDER_PAYLOAD_FILES_DEFAULT);
    }

    /**
     * Returns the default {@linkplain AppenderConfig#headerFilesToCreateAhead() number of header files to create ahead}
     * by appenders.
     *
     * @return the value of system property {@value #APPENDER_HEADER_FILES_TO_CREATE_AHEAD_PROPERTY} if set, and
     *         {@link #APPENDER_HEADER_FILES_TO_CREATE_AHEAD_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static int defaultAppenderHeaderFilesToCreateAhead() {
        return intProperty(APPENDER_HEADER_FILES_TO_CREATE_AHEAD_PROPERTY, Constraints::validateFilesToCreateAhead, APPENDER_HEADER_FILES_TO_CREATE_AHEAD_DEFAULT);
    }

    /**
     * Returns the default
     * {@linkplain AppenderConfig#payloadFilesToCreateAhead() number of payload files to create ahead} by appenders.
     *
     * @return the value of system property {@value #APPENDER_PAYLOAD_FILES_TO_CREATE_AHEAD_PROPERTY} if set, and
     *         {@link #APPENDER_PAYLOAD_FILES_TO_CREATE_AHEAD_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static int defaultAppenderPayloadFilesToCreateAhead() {
        return intProperty(APPENDER_PAYLOAD_FILES_TO_CREATE_AHEAD_PROPERTY, Constraints::validateFilesToCreateAhead, APPENDER_PAYLOAD_FILES_TO_CREATE_AHEAD_DEFAULT);
    }

    /**
     * Returns the default {@linkplain AppenderConfig#prefillHeaderFiles() prefill-header-files flag} of appenders.
     *
     * @return the value of system property {@value #APPENDER_PREFILL_HEADER_FILES_PROPERTY} if set, and
     *         {@link #APPENDER_PREFILL_HEADER_FILES_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static boolean defaultAppenderPrefillHeaderFiles() {
        return booleanProperty(APPENDER_PREFILL_HEADER_FILES_PROPERTY, APPENDER_PREFILL_HEADER_FILES_DEFAULT);
    }

    /**
     * Returns the default {@linkplain AppenderConfig#prefillPayloadFiles() prefill-payload-files flag} of appenders.
     *
     * @return the value of system property {@value #APPENDER_PREFILL_PAYLOAD_FILES_PROPERTY} if set, and
     *         {@link #APPENDER_PREFILL_PAYLOAD_FILES_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static boolean defaultAppenderPrefillPayloadFiles() {
        return booleanProperty(APPENDER_PREFILL_PAYLOAD_FILES_PROPERTY, APPENDER_PREFILL_PAYLOAD_FILES_DEFAULT);
    }


    // POLLER_HEADER_MAPPING_STRATEGY

    /**
     * Returns the region cache size of the default header mapping strategy of pollers.
     *
     * @return the value of system property {@value #POLLER_HEADER_REGION_CACHE_SIZE_PROPERTY} if set, and
     *         {@link MappingConfigurations#defaultRegionCacheSize()} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static int defaultPollerHeaderRegionCacheSize() {
        return intProperty(POLLER_HEADER_REGION_CACHE_SIZE_PROPERTY, Constraints::validateRegionCacheSize, MappingConfigurations::defaultRegionCacheSize);
    }

    /**
     * Returns the regions to map ahead of the default header mapping strategy of pollers, applicable only to strategies
     * with async mapping.
     *
     * @return the value of system property {@value #POLLER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY} if set, and
     *         {@link MappingConfigurations#defaultRegionsToMapAhead()} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static int defaultPollerHeaderRegionsToMapAhead() {
        return intProperty(POLLER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY, Constraints::validateRegionsToMapAhead, MappingConfigurations::defaultRegionsToMapAhead);
    }

    /**
     * Returns the default {@linkplain ReaderConfig#headerMappingStrategy() header mapping strategy} of pollers. The
     * strategy is configured with region cache size {@link #defaultPollerHeaderRegionCacheSize()} and deferred
     * unmapping, and if it maps asynchronously with regions to map ahead
     * {@link #defaultPollerHeaderRegionsToMapAhead()}.
     *
     * @return the strategy named by system property {@value #POLLER_HEADER_MAPPING_STRATEGY_PROPERTY} if set, and
     *         {@link #POLLER_HEADER_MAPPING_STRATEGY_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static MappingStrategyConfig defaultPollerHeaderMappingStrategy() {
        return getMappingStrategyProperty(
                POLLER_HEADER_MAPPING_STRATEGY_PROPERTY,
                POLLER_HEADER_MAPPING_STRATEGY_DEFAULT,
                QueueConfigurations::defaultPollerHeaderRegionCacheSize,
                QueueConfigurations::defaultPollerHeaderRegionsToMapAhead
        );
    }

    // POLLER_PAYLOAD_MAPPING_STRATEGY

    /**
     * Returns the region cache size of the default payload mapping strategy of pollers.
     *
     * @return the value of system property {@value #POLLER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY} if set, and
     *         {@link MappingConfigurations#defaultRegionCacheSize()} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static int defaultPollerPayloadRegionCacheSize() {
        return intProperty(POLLER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY, Constraints::validateRegionCacheSize, MappingConfigurations::defaultRegionCacheSize);
    }

    /**
     * Returns the regions to map ahead of the default payload mapping strategy of pollers, applicable only to
     * strategies with async mapping.
     *
     * @return the value of system property {@value #POLLER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY} if set, and
     *         {@link MappingConfigurations#defaultRegionsToMapAhead()} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static int defaultPollerPayloadRegionsToMapAhead() {
        return intProperty(POLLER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY, Constraints::validateRegionsToMapAhead, MappingConfigurations::defaultRegionsToMapAhead);
    }

    /**
     * Returns the default {@linkplain ReaderConfig#payloadMappingStrategy() payload mapping strategy} of pollers. The
     * strategy is configured with region cache size {@link #defaultPollerPayloadRegionCacheSize()} and deferred
     * unmapping, and if it maps asynchronously with regions to map ahead
     * {@link #defaultPollerPayloadRegionsToMapAhead()}.
     *
     * @return the strategy named by system property {@value #POLLER_PAYLOAD_MAPPING_STRATEGY_PROPERTY} if set, and
     *         {@link #POLLER_PAYLOAD_MAPPING_STRATEGY_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static MappingStrategyConfig defaultPollerPayloadMappingStrategy() {
        return getMappingStrategyProperty(
                POLLER_PAYLOAD_MAPPING_STRATEGY_PROPERTY,
                POLLER_PAYLOAD_MAPPING_STRATEGY_DEFAULT,
                QueueConfigurations::defaultPollerPayloadRegionCacheSize,
                QueueConfigurations::defaultPollerPayloadRegionsToMapAhead
        );
    }

    // ENTRY_READER_HEADER_MAPPING_STRATEGY

    /**
     * Returns the region cache size of the default header mapping strategy of entry readers.
     *
     * @return the value of system property {@value #ENTRY_READER_HEADER_REGION_CACHE_SIZE_PROPERTY} if set, and
     *         {@link MappingConfigurations#defaultRegionCacheSize()} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static int defaultEntryReaderHeaderRegionCacheSize() {
        return intProperty(ENTRY_READER_HEADER_REGION_CACHE_SIZE_PROPERTY, Constraints::validateRegionCacheSize, MappingConfigurations::defaultRegionCacheSize);
    }

    /**
     * Returns the regions to map ahead of the default header mapping strategy of entry readers, applicable only to
     * strategies with async mapping.
     *
     * @return the value of system property {@value #ENTRY_READER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY} if set, and
     *         {@link MappingConfigurations#defaultRegionsToMapAhead()} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static int defaultEntryReaderHeaderRegionsToMapAhead() {
        return intProperty(ENTRY_READER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY, Constraints::validateRegionsToMapAhead, MappingConfigurations::defaultRegionsToMapAhead);
    }

    /**
     * Returns the default {@linkplain ReaderConfig#headerMappingStrategy() header mapping strategy} of entry readers.
     * The strategy is configured with region cache size {@link #defaultEntryReaderHeaderRegionCacheSize()} and deferred
     * unmapping, and if it maps asynchronously with regions to map ahead
     * {@link #defaultEntryReaderHeaderRegionsToMapAhead()}.
     *
     * @return the strategy named by system property {@value #ENTRY_READER_HEADER_MAPPING_STRATEGY_PROPERTY} if set, and
     *         {@link #ENTRY_READER_HEADER_MAPPING_STRATEGY_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static MappingStrategyConfig defaultEntryReaderHeaderMappingStrategy() {
        return getMappingStrategyProperty(
                ENTRY_READER_HEADER_MAPPING_STRATEGY_PROPERTY,
                ENTRY_READER_HEADER_MAPPING_STRATEGY_DEFAULT,
                QueueConfigurations::defaultEntryReaderHeaderRegionCacheSize,
                QueueConfigurations::defaultEntryReaderHeaderRegionsToMapAhead
        );
    }

    // ENTRY_READER_PAYLOAD_MAPPING_STRATEGY

    /**
     * Returns the region cache size of the default payload mapping strategy of entry readers.
     *
     * @return the value of system property {@value #ENTRY_READER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY} if set, and
     *         {@link MappingConfigurations#defaultRegionCacheSize()} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static int defaultEntryReaderPayloadRegionCacheSize() {
        return intProperty(ENTRY_READER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY, Constraints::validateRegionCacheSize, MappingConfigurations::defaultRegionCacheSize);
    }

    /**
     * Returns the regions to map ahead of the default payload mapping strategy of entry readers, applicable only to
     * strategies with async mapping.
     *
     * @return the value of system property {@value #ENTRY_READER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY} if set, and
     *         {@link MappingConfigurations#defaultRegionsToMapAhead()} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static int defaultEntryReaderPayloadRegionsToMapAhead() {
        return intProperty(ENTRY_READER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY, Constraints::validateRegionsToMapAhead, MappingConfigurations::defaultRegionsToMapAhead);
    }

    /**
     * Returns the default {@linkplain ReaderConfig#payloadMappingStrategy() payload mapping strategy} of entry readers.
     * The strategy is configured with region cache size {@link #defaultEntryReaderPayloadRegionCacheSize()} and
     * deferred unmapping, and if it maps asynchronously with regions to map ahead
     * {@link #defaultEntryReaderPayloadRegionsToMapAhead()}.
     *
     * @return the strategy named by system property {@value #ENTRY_READER_PAYLOAD_MAPPING_STRATEGY_PROPERTY} if set,
     *         and {@link #ENTRY_READER_PAYLOAD_MAPPING_STRATEGY_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static MappingStrategyConfig defaultEntryReaderPayloadMappingStrategy() {
        return getMappingStrategyProperty(
                ENTRY_READER_PAYLOAD_MAPPING_STRATEGY_PROPERTY,
                ENTRY_READER_PAYLOAD_MAPPING_STRATEGY_DEFAULT,
                QueueConfigurations::defaultEntryReaderPayloadRegionCacheSize,
                QueueConfigurations::defaultEntryReaderPayloadRegionsToMapAhead
        );
    }

    // ENTRY_ITERATOR_HEADER_MAPPING_STRATEGY

    /**
     * Returns the region cache size of the default header mapping strategy of entry iterators.
     *
     * @return the value of system property {@value #ENTRY_ITERATOR_HEADER_REGION_CACHE_SIZE_PROPERTY} if set, and
     *         {@link MappingConfigurations#defaultRegionCacheSize()} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static int defaultEntryIteratorHeaderRegionCacheSize() {
        return intProperty(ENTRY_ITERATOR_HEADER_REGION_CACHE_SIZE_PROPERTY, Constraints::validateRegionCacheSize, MappingConfigurations::defaultRegionCacheSize);
    }

    /**
     * Returns the regions to map ahead of the default header mapping strategy of entry iterators, applicable only to
     * strategies with async mapping.
     *
     * @return the value of system property {@value #ENTRY_ITERATOR_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY} if set, and
     *         {@link MappingConfigurations#defaultRegionsToMapAhead()} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static int defaultEntryIteratorHeaderRegionsToMapAhead() {
        return intProperty(ENTRY_ITERATOR_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY, Constraints::validateRegionsToMapAhead, MappingConfigurations::defaultRegionsToMapAhead);
    }

    /**
     * Returns the default {@linkplain ReaderConfig#headerMappingStrategy() header mapping strategy} of entry iterators.
     * The strategy is configured with region cache size {@link #defaultEntryIteratorHeaderRegionCacheSize()} and
     * deferred unmapping, and if it maps asynchronously with regions to map ahead
     * {@link #defaultEntryIteratorHeaderRegionsToMapAhead()}.
     *
     * @return the strategy named by system property {@value #ENTRY_ITERATOR_HEADER_MAPPING_STRATEGY_PROPERTY} if set,
     *         and {@link #ENTRY_ITERATOR_HEADER_MAPPING_STRATEGY_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static MappingStrategyConfig defaultEntryIteratorHeaderMappingStrategy() {
        return getMappingStrategyProperty(
                ENTRY_ITERATOR_HEADER_MAPPING_STRATEGY_PROPERTY,
                ENTRY_ITERATOR_HEADER_MAPPING_STRATEGY_DEFAULT,
                QueueConfigurations::defaultEntryIteratorHeaderRegionCacheSize,
                QueueConfigurations::defaultEntryIteratorHeaderRegionsToMapAhead
        );
    }

    // ENTRY_ITERATOR_PAYLOAD_MAPPING_STRATEGY

    /**
     * Returns the region cache size of the default payload mapping strategy of entry iterators.
     *
     * @return the value of system property {@value #ENTRY_ITERATOR_PAYLOAD_REGION_CACHE_SIZE_PROPERTY} if set, and
     *         {@link MappingConfigurations#defaultRegionCacheSize()} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static int defaultEntryIteratorPayloadRegionCacheSize() {
        return intProperty(ENTRY_ITERATOR_PAYLOAD_REGION_CACHE_SIZE_PROPERTY, Constraints::validateRegionCacheSize, MappingConfigurations::defaultRegionCacheSize);
    }

    /**
     * Returns the regions to map ahead of the default payload mapping strategy of entry iterators, applicable only to
     * strategies with async mapping.
     *
     * @return the value of system property {@value #ENTRY_ITERATOR_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY} if set, and
     *         {@link MappingConfigurations#defaultRegionsToMapAhead()} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static int defaultEntryIteratorPayloadRegionsToMapAhead() {
        return intProperty(ENTRY_ITERATOR_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY, Constraints::validateRegionsToMapAhead, MappingConfigurations::defaultRegionsToMapAhead);
    }

    /**
     * Returns the default {@linkplain ReaderConfig#payloadMappingStrategy() payload mapping strategy} of entry
     * iterators. The strategy is configured with region cache size
     * {@link #defaultEntryIteratorPayloadRegionCacheSize()} and deferred unmapping, and if it maps asynchronously with
     * regions to map ahead {@link #defaultEntryIteratorPayloadRegionsToMapAhead()}.
     *
     * @return the strategy named by system property {@value #ENTRY_ITERATOR_PAYLOAD_MAPPING_STRATEGY_PROPERTY} if set,
     *         and {@link #ENTRY_ITERATOR_PAYLOAD_MAPPING_STRATEGY_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static MappingStrategyConfig defaultEntryIteratorPayloadMappingStrategy() {
        return getMappingStrategyProperty(
                ENTRY_ITERATOR_PAYLOAD_MAPPING_STRATEGY_PROPERTY,
                ENTRY_ITERATOR_PAYLOAD_MAPPING_STRATEGY_DEFAULT,
                QueueConfigurations::defaultEntryIteratorPayloadRegionCacheSize,
                QueueConfigurations::defaultEntryIteratorPayloadRegionsToMapAhead
        );
    }

    // INDEX_READER_HEADER_MAPPING_STRATEGY

    /**
     * Returns the region cache size of the default header mapping strategy of index readers.
     *
     * @return the value of system property {@value #INDEX_READER_HEADER_REGION_CACHE_SIZE_PROPERTY} if set, and
     *         {@link MappingConfigurations#defaultRegionCacheSize()} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static int defaultIndexReaderHeaderRegionCacheSize() {
        return intProperty(INDEX_READER_HEADER_REGION_CACHE_SIZE_PROPERTY, Constraints::validateRegionCacheSize, MappingConfigurations::defaultRegionCacheSize);
    }

    /**
     * Returns the regions to map ahead of the default header mapping strategy of index readers, applicable only to
     * strategies with async mapping.
     *
     * @return the value of system property {@value #INDEX_READER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY} if set, and
     *         {@link MappingConfigurations#defaultRegionsToMapAhead()} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static int defaultIndexReaderHeaderRegionsToMapAhead() {
        return intProperty(INDEX_READER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY, Constraints::validateRegionsToMapAhead, MappingConfigurations::defaultRegionsToMapAhead);
    }

    /**
     * Returns the default {@linkplain IndexReaderConfig#headerMappingStrategy() header mapping strategy} of index
     * readers. The strategy is configured with region cache size {@link #defaultIndexReaderHeaderRegionCacheSize()} and
     * deferred unmapping, and if it maps asynchronously with regions to map ahead
     * {@link #defaultIndexReaderHeaderRegionsToMapAhead()}.
     *
     * @return the strategy named by system property {@value #INDEX_READER_HEADER_MAPPING_STRATEGY_PROPERTY} if set, and
     *         {@link #INDEX_READER_HEADER_MAPPING_STRATEGY_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static MappingStrategyConfig defaultIndexReaderHeaderMappingStrategy() {
        return getMappingStrategyProperty(
                INDEX_READER_HEADER_MAPPING_STRATEGY_PROPERTY,
                INDEX_READER_HEADER_MAPPING_STRATEGY_DEFAULT,
                QueueConfigurations::defaultIndexReaderHeaderRegionCacheSize,
                QueueConfigurations::defaultIndexReaderHeaderRegionsToMapAhead
        );
    }

    // APPENDER_HEADER_MAPPING_STRATEGY

    /**
     * Returns the region cache size of the default header mapping strategy of appenders.
     *
     * @return the value of system property {@value #APPENDER_HEADER_REGION_CACHE_SIZE_PROPERTY} if set, and
     *         {@link MappingConfigurations#defaultRegionCacheSize()} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static int defaultAppenderHeaderRegionCacheSize() {
        return intProperty(APPENDER_HEADER_REGION_CACHE_SIZE_PROPERTY, Constraints::validateRegionCacheSize, MappingConfigurations::defaultRegionCacheSize);
    }

    /**
     * Returns the regions to map ahead of the default header mapping strategy of appenders, applicable only to
     * strategies with async mapping.
     *
     * @return the value of system property {@value #APPENDER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY} if set, and
     *         {@link MappingConfigurations#defaultRegionsToMapAhead()} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static int defaultAppenderHeaderRegionsToMapAhead() {
        return intProperty(APPENDER_HEADER_REGIONS_TO_MAP_AHEAD_PROPERTY, Constraints::validateRegionsToMapAhead, MappingConfigurations::defaultRegionsToMapAhead);
    }

    /**
     * Returns the default {@linkplain AppenderConfig#headerMappingStrategy() header mapping strategy} of appenders. The
     * strategy is configured with region cache size {@link #defaultAppenderHeaderRegionCacheSize()} and deferred
     * unmapping, and if it maps asynchronously with regions to map ahead
     * {@link #defaultAppenderHeaderRegionsToMapAhead()}.
     *
     * @return the strategy named by system property {@value #APPENDER_HEADER_MAPPING_STRATEGY_PROPERTY} if set, and
     *         {@link #APPENDER_HEADER_MAPPING_STRATEGY_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static MappingStrategyConfig defaultAppenderHeaderMappingStrategy() {
        return getMappingStrategyProperty(
                APPENDER_HEADER_MAPPING_STRATEGY_PROPERTY,
                APPENDER_HEADER_MAPPING_STRATEGY_DEFAULT,
                QueueConfigurations::defaultAppenderHeaderRegionCacheSize,
                QueueConfigurations::defaultAppenderHeaderRegionsToMapAhead
        );
    }

    // APPENDER_PAYLOAD_MAPPING_STRATEGY

    /**
     * Returns the region cache size of the default payload mapping strategy of appenders.
     *
     * @return the value of system property {@value #APPENDER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY} if set, and
     *         {@link MappingConfigurations#defaultRegionCacheSize()} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static int defaultAppenderPayloadRegionCacheSize() {
        return intProperty(APPENDER_PAYLOAD_REGION_CACHE_SIZE_PROPERTY, Constraints::validateRegionCacheSize, MappingConfigurations::defaultRegionCacheSize);
    }

    /**
     * Returns the regions to map ahead of the default payload mapping strategy of appenders, applicable only to
     * strategies with async mapping.
     *
     * @return the value of system property {@value #APPENDER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY} if set, and
     *         {@link MappingConfigurations#defaultRegionsToMapAhead()} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static int defaultAppenderPayloadRegionsToMapAhead() {
        return intProperty(APPENDER_PAYLOAD_REGIONS_TO_MAP_AHEAD_PROPERTY, Constraints::validateRegionsToMapAhead, MappingConfigurations::defaultRegionsToMapAhead);
    }

    /**
     * Returns the default {@linkplain AppenderConfig#payloadMappingStrategy() payload mapping strategy} of appenders.
     * The strategy is configured with region cache size {@link #defaultAppenderPayloadRegionCacheSize()} and deferred
     * unmapping, and if it maps asynchronously with regions to map ahead
     * {@link #defaultAppenderPayloadRegionsToMapAhead()}.
     *
     * @return the strategy named by system property {@value #APPENDER_PAYLOAD_MAPPING_STRATEGY_PROPERTY} if set, and
     *         {@link #APPENDER_PAYLOAD_MAPPING_STRATEGY_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     */
    public static MappingStrategyConfig defaultAppenderPayloadMappingStrategy() {
        return getMappingStrategyProperty(
                APPENDER_PAYLOAD_MAPPING_STRATEGY_PROPERTY,
                APPENDER_PAYLOAD_MAPPING_STRATEGY_DEFAULT,
                QueueConfigurations::defaultAppenderPayloadRegionCacheSize,
                QueueConfigurations::defaultAppenderPayloadRegionsToMapAhead
        );
    }

    /**
     * Returns the default queue configuration, whose values are given by the {@code default..()} methods of this class,
     * that is, by system properties if set.
     *
     * @return the default queue configuration
     */
    public static QueueConfig defaultQueueConfig() {
        return QUEUE_CONFIG_DEFAULTS;
    }

    /**
     * Returns the default appender configuration, whose values are given by the {@code default..()} methods of this
     * class, that is, by system properties if set.
     *
     * @return the default appender configuration
     */
    public static AppenderConfig defaultAppenderConfig() {
        return APPENDER_CONFIG_DEFAULTS;
    }

    /**
     * Returns the default poller configuration, whose values are given by the {@code default..()} methods of this
     * class, that is, by system properties if set.
     *
     * @return the default poller configuration
     */
    public static ReaderConfig defaultPollerConfig() {
        return POLLER_CONFIG_DEFAULTS;
    }

    /**
     * Returns the default entry reader configuration, whose values are given by the {@code default..()} methods of this
     * class, that is, by system properties if set.
     *
     * @return the default entry reader configuration
     */
    public static ReaderConfig defaultEntryReaderConfig() {
        return ENTRY_READER_CONFIG_DEFAULTS;
    }

    /**
     * Returns the default entry iterator configuration, whose values are given by the {@code default..()} methods of
     * this class, that is, by system properties if set.
     *
     * @return the default entry iterator configuration
     */
    public static ReaderConfig defaultEntryIteratorConfig() {
        return ENTRY_ITERATOR_CONFIG_DEFAULTS;
    }

    /**
     * Returns the default index reader configuration, whose values are given by the {@code default..()} methods of this
     * class, that is, by system properties if set.
     *
     * @return the default index reader configuration
     */
    public static IndexReaderConfig defaultIndexReaderConfig() {
        return INDEX_READER_CONFIG_DEFAULTS;
    }

    private static MappingStrategyConfig getMappingStrategyProperty(final String mappingStrategyProperty,
                                                                    final MappingStrategy mappingStrategyDefault,
                                                                    final IntSupplier regionCacheSizeSupplier,
                                                                    final IntSupplier regionsToMapAheadSupplier) {
        final MappingStrategyConfig strategy = enumProperty(mappingStrategyProperty, MappingStrategy.class,
                mappingStrategyDefault).mappingStrategyConfig();
        final MappingStrategyConfigurator config = MappingStrategyConfig.configure(strategy)
                .cacheSize(regionCacheSizeSupplier.getAsInt())
                .deferUnmapping(true);
        if (strategy.asyncMapping().isPresent()) {
            config.asyncMapping(async -> async.regionsToMapAhead(regionsToMapAheadSupplier.getAsInt()));
        }
        return config.toImmutableConfig();
    }
}
