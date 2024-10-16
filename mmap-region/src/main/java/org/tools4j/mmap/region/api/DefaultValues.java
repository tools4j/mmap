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
package org.tools4j.mmap.region.api;

import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.tools4j.mmap.region.impl.Constraints;
import org.tools4j.mmap.region.impl.DefaultAsyncRuntime;

import java.util.function.IntConsumer;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

public enum DefaultValues {
    ;
    public static final String MAX_FILE_SIZE_PROPERTY = "mmap.maxFileSize";
    public static final int MAX_FILE_SIZE_DEFAULT = 256*1024*1034;
    public static final String EXPAND_FILE_PROPERTY = "mmap.expandFile";
    public static final boolean EXPAND_FILE_DEFAULT = true;
    public static final String ROLL_FILES_PROPERTY = "mmap.rollFiles";
    public static final boolean ROLL_FILES_DEFAULT = true;
    public static final String FILES_TO_CREATE_AHEAD_PROPERTY = "mmap.filesToCreateAhead";
    public static final int FILES_TO_CREATE_AHEAD_DEFAULT = 0;
    public static final String REGION_SIZE_PROPERTY = "mmap.regionSize";
    public static final int REGION_SIZE_DEFAULT = 4*1024*1034;
    public static final String REGION_CACHE_SIZE_PROPERTY = "mmap.regionCacheSize";
    public static final int REGION_CACHE_SIZE_DEFAULT = 16;
    public static final String REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.regionsToMapAhead";
    public static final int REGIONS_TO_MAP_AHEAD_DEFAULT = 8;
    public static final String MAPPING_IDLE_STRATEGY_PROPERTY = "mmap.mappingIdleStrategy";
    public static final IdleStrategy MAPPING_IDLE_STRATEGY_DEFAULT = BusySpinIdleStrategy.INSTANCE;
    public static final String UNMAPPING_IDLE_STRATEGY_PROPERTY = "mmap.unmappingIdleStrategy";
    public static final IdleStrategy UNMAPPING_IDLE_STRATEGY_DEFAULT = new SleepingMillisIdleStrategy(20);
    public static final String MAPPING_ASYNC_RUNTIME_PROPERTY = "mmap.mappingAsyncRuntime";
    private static AsyncRuntime MAPPING_ASYNC_RUNTIME_DEFAULT_VALUE;
    public static final String UNMAPPING_ASYNC_RUNTIME_PROPERTY = "mmap.unmappingAsyncRuntime";
    private static AsyncRuntime UNMAPPING_ASYNC_RUNTIME_DEFAULT_VALUE;
    public static final String MAPPING_STRATEGY_PROPERTY = "mmap.mappingStrategyDefault";
    public static final String MAPPING_STRATEGY_DEFAULT = AheadMappingStrategy.NAME;
    private static MappingStrategy MAPPING_STRATEGY_DEFAULT_VALUE;
    private static MappingStrategy SYNC_MAPPING_STRATEGY_DEFAULT_VALUE;
    private static MappingStrategy AHEAD_MAPPING_STRATEGY_DEFAULT_VALUE;
    private static MappingConfig MAPPING_CONFIG_DEFAULT_VALUE;

    public static int defaultMaxFileSize() {
        return getIntProperty(MAX_FILE_SIZE_PROPERTY, Constraints::validateMaxFileSize, MAX_FILE_SIZE_DEFAULT);
    }

    public static boolean defaultExpandFile() {
        return getBooleanProperty(EXPAND_FILE_PROPERTY, EXPAND_FILE_DEFAULT);
    }

    public static boolean defaultRollFiles() {
        return getBooleanProperty(ROLL_FILES_PROPERTY, ROLL_FILES_DEFAULT);
    }

    public static int defaultFilesToCreateAhead() {
        return getIntProperty(FILES_TO_CREATE_AHEAD_PROPERTY, Constraints::validateFilesToCreateAhead, FILES_TO_CREATE_AHEAD_DEFAULT);
    }

    public static int defaultRegionSize() {
        return getIntProperty(REGION_SIZE_PROPERTY, Constraints::validateRegionSize, REGION_SIZE_DEFAULT);
    }

    public static int defaultRegionCacheSize() {
        return getIntProperty(REGION_CACHE_SIZE_PROPERTY, Constraints::validateRegionCacheSize, REGION_CACHE_SIZE_DEFAULT);
    }

    public static int defaultRegionsToMapAhead() {
        return getIntProperty(REGIONS_TO_MAP_AHEAD_PROPERTY, Constraints::validateRegionsToMapAhead, REGIONS_TO_MAP_AHEAD_DEFAULT);
    }

    public static IdleStrategy defaultMappingIdleStrategy() {
        return getObjProperty(MAPPING_IDLE_STRATEGY_PROPERTY, IdleStrategy.class, MAPPING_IDLE_STRATEGY_DEFAULT);
    }

    public static IdleStrategy defaultUnmappingIdleStrategy() {
        return getObjProperty(UNMAPPING_IDLE_STRATEGY_PROPERTY, IdleStrategy.class, UNMAPPING_IDLE_STRATEGY_DEFAULT);
    }

    public synchronized static AsyncRuntime defaultMappingAsyncRuntime() {
        if (MAPPING_ASYNC_RUNTIME_DEFAULT_VALUE == null) {
            MAPPING_ASYNC_RUNTIME_DEFAULT_VALUE = getObjProperty(MAPPING_ASYNC_RUNTIME_PROPERTY, AsyncRuntime.class,
                    (Supplier<AsyncRuntime>) () -> new DefaultAsyncRuntime(defaultMappingIdleStrategy(), false)
            );
            assert MAPPING_ASYNC_RUNTIME_DEFAULT_VALUE != null;
        }
        return MAPPING_ASYNC_RUNTIME_DEFAULT_VALUE;
    }

    public synchronized static AsyncRuntime defaultUnmappingAsyncRuntime() {
        if (UNMAPPING_ASYNC_RUNTIME_DEFAULT_VALUE == null) {
            UNMAPPING_ASYNC_RUNTIME_DEFAULT_VALUE = getObjProperty(UNMAPPING_ASYNC_RUNTIME_PROPERTY, AsyncRuntime.class,
                    (Supplier<AsyncRuntime>) () -> new DefaultAsyncRuntime(defaultUnmappingIdleStrategy(), false)
            );
            assert UNMAPPING_ASYNC_RUNTIME_DEFAULT_VALUE != null;
        }
        return UNMAPPING_ASYNC_RUNTIME_DEFAULT_VALUE;
    }

    public static MappingConfig defaultMappingConfig() {
        if (MAPPING_CONFIG_DEFAULT_VALUE == null) {
            MAPPING_CONFIG_DEFAULT_VALUE = MappingConfig.configure();
            //noinspection ConstantValue
            assert MAPPING_CONFIG_DEFAULT_VALUE != null;
        }
        return MAPPING_CONFIG_DEFAULT_VALUE;
    }

    public static MappingStrategy defaultMappingStrategy() {
        if (MAPPING_STRATEGY_DEFAULT_VALUE == null) {
            MAPPING_STRATEGY_DEFAULT_VALUE = getMappingStrategyProperty();
            assert MAPPING_STRATEGY_DEFAULT_VALUE != null;
        }
        return MAPPING_STRATEGY_DEFAULT_VALUE;
    }

    private static MappingStrategy getMappingStrategyProperty() {
        final String propVal = System.getProperty(MAPPING_STRATEGY_PROPERTY, MAPPING_STRATEGY_DEFAULT);
        switch (propVal) {
            case AheadMappingStrategy.NAME:
                return defaultAheadMappingStrategy();
            case SyncMappingStrategy.NAME:
                return defaultSyncMappingStrategy();
            default:
                return getObjProperty(MAPPING_STRATEGY_PROPERTY, MappingStrategy.class,
                        (Supplier<? extends MappingStrategy>) DefaultValues::defaultAheadMappingStrategy);
        }
    }

    public static MappingStrategy defaultSyncMappingStrategy() {
        if (SYNC_MAPPING_STRATEGY_DEFAULT_VALUE == null) {
            SYNC_MAPPING_STRATEGY_DEFAULT_VALUE = new SyncMappingStrategy(defaultRegionSize());
        }
        return SYNC_MAPPING_STRATEGY_DEFAULT_VALUE;
    }

    public static MappingStrategy defaultAheadMappingStrategy() {
        if (AHEAD_MAPPING_STRATEGY_DEFAULT_VALUE == null) {
            AHEAD_MAPPING_STRATEGY_DEFAULT_VALUE = new AheadMappingStrategy(defaultRegionSize(), defaultRegionCacheSize(), defaultRegionsToMapAhead());
        }
        return AHEAD_MAPPING_STRATEGY_DEFAULT_VALUE;
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

    private static boolean getBooleanProperty(final String propertyName, final boolean defaultValue) {
        final String propVal = System.getProperty(propertyName, null);
        return propVal == null ? defaultValue : Boolean.parseBoolean(propVal);
    }

    @SuppressWarnings("SameParameterValue")
    private static <T> T getObjProperty(final String propertyName, final Class<T> type, final T defaultValue) {
        requireNonNull(defaultValue);
        return getObjProperty(propertyName, type, (Supplier<T>)() -> defaultValue);
    }

    private static <T> T getObjProperty(final String propertyName,
                                        final Class<T> type,
                                        final Supplier<? extends T> defaultValueSupplier) {
        final String propVal = System.getProperty(propertyName, null);
        if (propVal == null) {
            return defaultValueSupplier.get();
        }
        try {
            final Class<?> clazz = Class.forName(propVal);
            final Object value = clazz.newInstance();
            return type.cast(value);
        } catch (final Exception e) {
            throw new IllegalArgumentException("Invalid value for system property: " + propertyName + "=" + propVal, e);
        }
    }
}
