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
package org.tools4j.mmap.region.config;

import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.tools4j.mmap.region.api.AsyncRuntime;
import org.tools4j.mmap.region.impl.Constraints;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.impl.Constants.REGION_SIZE_GRANULARITY;
import static org.tools4j.mmap.region.impl.MappingConfigDefaults.MAPPING_CONFIG_DEFAULTS;
import static org.tools4j.mmap.region.impl.MappingStrategyConfigDefaults.MAPPING_STRATEGY_CONFIG_DEFAULTS;

/**
 * Defines region mapping default configuration values and property constants to override default values via system
 * properties.
 */
public enum MappingConfigurations {
    ;
    public static final String MAX_FILE_SIZE_PROPERTY = "mmap.region.maxFileSize";
    public static final int MAX_FILE_SIZE_DEFAULT = 256*1024*1024;
    public static final String EXPAND_FILE_PROPERTY = "mmap.region.expandFile";
    public static final boolean EXPAND_FILE_DEFAULT = true;
    public static final String ROLL_FILES_PROPERTY = "mmap.region.rollFiles";
    public static final boolean ROLL_FILES_DEFAULT = true;
    public static final String CLOSE_FILES_PROPERTY = "mmap.region.closeFiles";
    public static final boolean CLOSE_FILES_DEFAULT = true;
    public static final String FILES_TO_CREATE_AHEAD_PROPERTY = "mmap.region.filesToCreateAhead";
    public static final int FILES_TO_CREATE_AHEAD_DEFAULT = 0;
    public static final String REGION_SIZE_PROPERTY = "mmap.region.regionSize";
    public static final int REGION_SIZE_DEFAULT = (int)(1024*REGION_SIZE_GRANULARITY);//typically ~4MB
    public static final String REGION_CACHE_SIZE_PROPERTY = "mmap.region.regionCacheSize";
    public static final int REGION_CACHE_SIZE_DEFAULT = 4;
    public static final String REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.region.regionsToMapAhead";
    public static final int REGIONS_TO_MAP_AHEAD_DEFAULT = 2;
    public static final String MAPPING_RUNTIME_IDLE_STRATEGY_PROPERTY = "mmap.region.mappingRuntimeIdleStrategy";
    public static final Supplier<IdleStrategy> MAPPING_RUNTIME_IDLE_STRATEGY_DEFAULT = () -> BusySpinIdleStrategy.INSTANCE;
    public static final String MAPPING_RUNTIME_IDLE_STRATEGY_SHARED_PROPERTY = "mmap.region.mappingRuntimeIdleStrategyShared";
    public static final boolean MAPPING_RUNTIME_IDLE_STRATEGY_SHARED_DEFAULT = true;
    public static final String MAPPING_RUNTIME_SHARED_PROPERTY = "mmap.region.mappingRuntimeShared";
    public static final boolean MAPPING_RUNTIME_SHARED_DEFAULT = true;
    public static final String MAPPING_RUNTIME_AUTO_CLOSE_ON_LAST_DEREGISTER_PROPERTY = "mmap.region.mappingRuntimeAutoCloseOnLastDeregister";
    public static final boolean MAPPING_RUNTIME_AUTO_CLOSE_ON_LAST_DEREGISTER_DEFAULT = false;
    public static final String UNMAPPING_RUNTIME_IDLE_STRATEGY_PROPERTY = "mmap.region.unmappingRuntimeIdleStrategy";
    public static final Supplier<IdleStrategy> UNMAPPING_RUNTIME_IDLE_STRATEGY_DEFAULT = BackoffIdleStrategy::new;
    public static final String UNMAPPING_RUNTIME_IDLE_STRATEGY_SHARED_PROPERTY = "mmap.region.unmappingRuntimeIdleStrategyShared";
    public static final boolean UNMAPPING_RUNTIME_IDLE_STRATEGY_SHARED_DEFAULT = false;
    public static final String UNMAPPING_RUNTIME_SHARED_PROPERTY = "mmap.region.unmappingRuntimeShared";
    public static final boolean UNMAPPING_RUNTIME_SHARED_DEFAULT = true;
    public static final String UNMAPPING_RUNTIME_AUTO_CLOSE_ON_LAST_DEREGISTER_PROPERTY = "mmap.region.unmappingRuntimeAutoCloseOnLastDeregister";
    public static final boolean UNMAPPING_RUNTIME_AUTO_CLOSE_ON_LAST_DEREGISTER_DEFAULT = false;
    public static final String MAPPING_ASYNC_RUNTIME_PROPERTY = "mmap.region.mappingAsyncRuntime";
    private static Supplier<? extends AsyncRuntime> MAPPING_ASYNC_RUNTIME_DEFAULT_VALUE;
    public static final String UNMAPPING_ASYNC_RUNTIME_PROPERTY = "mmap.region.unmappingAsyncRuntime";
    private static Supplier<? extends AsyncRuntime> UNMAPPING_ASYNC_RUNTIME_DEFAULT_VALUE;
    public static final String MAPPING_STRATEGY_PROPERTY = "mmap.region.mappingStrategy";
    public static final String MAPPING_STRATEGY_DEFAULT = AheadMappingStrategy.NAME;
    private static MappingStrategy MAPPING_STRATEGY_DEFAULT_VALUE;
    private static MappingStrategy SYNC_MAPPING_STRATEGY_DEFAULT_VALUE;
    private static MappingStrategy AHEAD_MAPPING_STRATEGY_DEFAULT_VALUE;

    public static int defaultMaxFileSize() {
        return getIntProperty(MAX_FILE_SIZE_PROPERTY, Constraints::validateMaxFileSize, MAX_FILE_SIZE_DEFAULT);
    }

    public static boolean defaultExpandFile() {
        return getBooleanProperty(EXPAND_FILE_PROPERTY, EXPAND_FILE_DEFAULT);
    }

    public static boolean defaultRollFiles() {
        return getBooleanProperty(ROLL_FILES_PROPERTY, ROLL_FILES_DEFAULT);
    }

    public static boolean defaultCloseFiles() {
        return getBooleanProperty(CLOSE_FILES_PROPERTY, CLOSE_FILES_DEFAULT);
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

    public static boolean defaultMappingRuntimeIdleStrategyShared() {
        return getBooleanProperty(MAPPING_RUNTIME_IDLE_STRATEGY_SHARED_PROPERTY, MAPPING_RUNTIME_IDLE_STRATEGY_SHARED_DEFAULT);
    }

    public static Supplier<? extends IdleStrategy> defaultMappingRuntimeIdleStrategySupplier() {
        return getSupplierProperty(MAPPING_RUNTIME_IDLE_STRATEGY_PROPERTY, IdleStrategy.class,
                defaultMappingRuntimeIdleStrategyShared(), MAPPING_RUNTIME_IDLE_STRATEGY_DEFAULT);
    }

    public static boolean defaultMappingRuntimeShared() {
        return getBooleanProperty(MAPPING_RUNTIME_SHARED_PROPERTY, MAPPING_RUNTIME_SHARED_DEFAULT);
    }

    public static boolean defaultMappingRuntimeAutoCloseOnLastDeregister() {
        return getBooleanProperty(MAPPING_RUNTIME_AUTO_CLOSE_ON_LAST_DEREGISTER_PROPERTY, MAPPING_RUNTIME_AUTO_CLOSE_ON_LAST_DEREGISTER_DEFAULT);
    }

    public static boolean defaultUnmappingRuntimeIdleStrategyShared() {
        return getBooleanProperty(UNMAPPING_RUNTIME_IDLE_STRATEGY_SHARED_PROPERTY, UNMAPPING_RUNTIME_IDLE_STRATEGY_SHARED_DEFAULT);
    }

    public static Supplier<? extends IdleStrategy> defaultUnmappingRuntimeIdleStrategySupplier() {
        return getSupplierProperty(UNMAPPING_RUNTIME_IDLE_STRATEGY_PROPERTY, IdleStrategy.class,
                defaultUnmappingRuntimeIdleStrategyShared(), UNMAPPING_RUNTIME_IDLE_STRATEGY_DEFAULT);
    }

    public static boolean defaultUnmappingRuntimeShared() {
        return getBooleanProperty(UNMAPPING_RUNTIME_SHARED_PROPERTY, UNMAPPING_RUNTIME_SHARED_DEFAULT);
    }

    public static boolean defaultUnmappingRuntimeAutoCloseOnLastDeregister() {
        return getBooleanProperty(UNMAPPING_RUNTIME_AUTO_CLOSE_ON_LAST_DEREGISTER_PROPERTY, UNMAPPING_RUNTIME_AUTO_CLOSE_ON_LAST_DEREGISTER_DEFAULT);
    }

    public synchronized static Supplier<? extends AsyncRuntime> defaultMappingAsyncRuntimeSupplier() {
        if (MAPPING_ASYNC_RUNTIME_DEFAULT_VALUE == null) {
            MAPPING_ASYNC_RUNTIME_DEFAULT_VALUE = createDefaultAsyncRuntimeSupplier(
                    "mapper-default", MAPPING_ASYNC_RUNTIME_PROPERTY,
                    MappingConfigurations.defaultMappingRuntimeShared(),
                    defaultMappingRuntimeIdleStrategySupplier(),
                    MappingConfigurations::defaultMappingRuntimeAutoCloseOnLastDeregister);
            assert MAPPING_ASYNC_RUNTIME_DEFAULT_VALUE != null;
        }
        return MAPPING_ASYNC_RUNTIME_DEFAULT_VALUE;
    }

    public synchronized static Supplier<? extends AsyncRuntime> defaultUnmappingAsyncRuntimeSupplier() {
        if (UNMAPPING_ASYNC_RUNTIME_DEFAULT_VALUE == null) {
            UNMAPPING_ASYNC_RUNTIME_DEFAULT_VALUE = createDefaultAsyncRuntimeSupplier(
                    "unmapper-default", UNMAPPING_ASYNC_RUNTIME_PROPERTY,
                    MappingConfigurations.defaultUnmappingRuntimeShared(),
                    defaultUnmappingRuntimeIdleStrategySupplier(),
                    MappingConfigurations::defaultUnmappingRuntimeAutoCloseOnLastDeregister);
            assert UNMAPPING_ASYNC_RUNTIME_DEFAULT_VALUE != null;
        }
        return UNMAPPING_ASYNC_RUNTIME_DEFAULT_VALUE;
    }

    private static Supplier<? extends AsyncRuntime> createDefaultAsyncRuntimeSupplier(final String name,
                                                                                      final String runtimePropertyName,
                                                                                      final boolean sharedRuntime,
                                                                                      final Supplier<? extends IdleStrategy> idleStrategySupplier,
                                                                                      final BooleanSupplier autoStopOnLastDeregisterSupplier) {
        requireNonNull(name);
        requireNonNull(runtimePropertyName);
        requireNonNull(idleStrategySupplier);
        requireNonNull(autoStopOnLastDeregisterSupplier);
        return getSupplierProperty(runtimePropertyName, AsyncRuntime.class, sharedRuntime, () ->
                AsyncRuntime.create(name, idleStrategySupplier.get(), autoStopOnLastDeregisterSupplier.getAsBoolean())
        );
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
                        (Supplier<? extends MappingStrategy>) MappingConfigurations::defaultAheadMappingStrategy);
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
            AHEAD_MAPPING_STRATEGY_DEFAULT_VALUE = new AheadMappingStrategy(
                    defaultRegionSize(), defaultRegionCacheSize(), defaultRegionsToMapAhead(),
                    defaultMappingAsyncRuntimeSupplier(), defaultUnmappingAsyncRuntimeSupplier()
            );
        }
        return AHEAD_MAPPING_STRATEGY_DEFAULT_VALUE;
    }

    public static MappingConfig defaultMappingConfig() {
        return MAPPING_CONFIG_DEFAULTS;
    }

    public static MappingStrategyConfig defaultMappingStrategyConfig() {
        return MAPPING_STRATEGY_CONFIG_DEFAULTS;
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
        final String propVal = System.getProperty(propertyName, null);
        return propVal == null ? defaultValue : newObjInstance(propertyName, propVal, type);
    }

    private static <T> T getObjProperty(final String propertyName,
                                        final Class<T> type,
                                        final Supplier<? extends T> defaultValueSupplier) {
        final String propVal = System.getProperty(propertyName, null);
        return propVal == null ? defaultValueSupplier.get() : newObjInstance(propertyName, propVal, type);
    }

    private static <T> Supplier<T> getSupplierProperty(final String propertyName,
                                                       final Class<T> type,
                                                       final boolean sharedInstance,
                                                       final Supplier<T> defaultValueSupplier) {
        requireNonNull(propertyName);
        requireNonNull(type);
        requireNonNull(defaultValueSupplier);
        final String propVal = System.getProperty(propertyName, null);
        if (propVal == null) {
            if (sharedInstance) {
                final T instance = defaultValueSupplier.get();
                return () -> instance;
            }
            return defaultValueSupplier;
        }
        return newSupplier(propertyName, propVal, type, sharedInstance);
    }

    private static <T> Supplier<T> newSupplier(final String propName,
                                               final String propVal,
                                               final Class<T> type,
                                               final boolean sharedInstance) {
        requireNonNull(propName);
        requireNonNull(type);
        final AtomicReference<Supplier<?>> supplierPtr = new AtomicReference<>();
        return () -> {
            try {
                Supplier<?> supplier = supplierPtr.get();
                if (supplier != null) {
                    return type.cast(supplier.get());
                }
                final Object value = newObjInstance(propName, propVal, Object.class);
                if (type.isInstance(value)) {
                    supplier = sharedInstance ? () -> value : () -> newObjInstance(propName, propVal, type);
                    supplierPtr.set(supplier);
                    return type.cast(value);
                }
                if (value instanceof Supplier) {
                    supplier = (Supplier<?>) value;
                    final T instance = type.cast(supplier.get());
                    supplierPtr.set(sharedInstance ? () -> instance : supplier);
                    return instance;
                }
                throw new IllegalArgumentException("Value expected to be of type " + type.getName() +
                        " or a supplier of such a value, but was found to be: " + value);
            } catch (final Exception e) {
                throw new IllegalArgumentException("Invalid value for system property: " + propName + "=" + propVal);
            }
        };
    }

    private static <T> T newObjInstance(final String propName, final String propVal, final Class<T> type) {
        try {
            final Class<?> clazz = Class.forName(propVal);
            final Object value = clazz.getDeclaredConstructor().newInstance();
            return type.cast(value);
        } catch (final Exception e) {
            throw new IllegalArgumentException("Invalid value for system property: " + propName + "=" + propVal, e);
        }
    }
}
