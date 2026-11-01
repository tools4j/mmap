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
package org.tools4j.mmap.mapping.config;

import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.tools4j.mmap.mapping.api.AsyncRuntime;
import org.tools4j.mmap.mapping.api.AsyncRuntimeInstances;
import org.tools4j.mmap.mapping.api.CpuPinning;
import org.tools4j.mmap.mapping.api.ErrorListener;
import org.tools4j.mmap.mapping.api.LifecycleListener;
import org.tools4j.mmap.mapping.impl.AsyncRuntimeImpl;
import org.tools4j.mmap.mapping.impl.CpuLists;
import org.tools4j.mmap.mapping.impl.Constraints;
import org.tools4j.mmap.mapping.impl.LoggingErrorListener;
import org.tools4j.mmap.mapping.impl.LoggingLifecycleListener;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.mapping.impl.Constants.REGION_SIZE_GRANULARITY;

/**
 * Defines default configuration values for mappings and system property names to override them. The
 * {@code default..()} methods return the value of the respective system property if set, and the default value
 * otherwise. Property values are validated when read, and an {@link IllegalArgumentException} is thrown for an invalid
 * value.
 * <p>
 * The values returned by these methods are used by the system default configurations such as
 * {@link MappingConfig#getDefault()}, {@link MappingStrategyConfig#getDefault()},
 * {@link AsyncMappingConfig#getDefault()} and {@link AsyncUnmappingConfig#getDefault()}, and by configurators for
 * values that are not explicitly set.
 */
public enum MappingConfigurations {
    ;
    /**
     * Name of the system property to override the default minimum file size (and file size increment) in expand-file
     * mode.
     */
    public static final String MIN_FILE_SIZE_PROPERTY = "mmap.mapping.minFileSize";
    /**
     * The default minimum file size (and file size increment) in expand-file mode, used if system property
     * {@value #MIN_FILE_SIZE_PROPERTY} is not set.
     */
    public static final long MIN_FILE_SIZE_DEFAULT = 0;
    /** Name of the system property to override the default maximum file size. */
    public static final String MAX_FILE_SIZE_PROPERTY = "mmap.mapping.maxFileSize";
    /** The default maximum file size, used if system property {@value #MAX_FILE_SIZE_PROPERTY} is not set. */
    public static final long MAX_FILE_SIZE_DEFAULT = 2L*1024*1024*1024;
    /** Name of the system property to override the default expand-file flag. */
    public static final String EXPAND_FILE_PROPERTY = "mmap.mapping.expandFile";
    /** The default expand-file flag, used if system property {@value #EXPAND_FILE_PROPERTY} is not set. */
    public static final boolean EXPAND_FILE_DEFAULT = false;
    /** Name of the system property to override the default roll-files flag. */
    public static final String ROLL_FILES_PROPERTY = "mmap.mapping.rollFiles";
    /** The default roll-files flag, used if system property {@value #ROLL_FILES_PROPERTY} is not set. */
    public static final boolean ROLL_FILES_DEFAULT = true;
    /** Name of the system property to override the default maximum number of files kept open in roll-files mode. */
    public static final String MAX_OPEN_FILES_PROPERTY = "mmap.mapping.maxOpenFiles";
    /**
     * The default maximum number of files kept open in roll-files mode, used if system property
     * {@value #MAX_OPEN_FILES_PROPERTY} is not set.
     */
    public static final int MAX_OPEN_FILES_DEFAULT = 3;
    /** Name of the system property to override the default number of files to create ahead in roll-files mode. */
    public static final String FILES_TO_CREATE_AHEAD_PROPERTY = "mmap.mapping.filesToCreateAhead";
    /**
     * The default number of files to create ahead in roll-files mode, used if system property
     * {@value #FILES_TO_CREATE_AHEAD_PROPERTY} is not set.
     */
    public static final int FILES_TO_CREATE_AHEAD_DEFAULT = 0;
    /** Name of the system property to override the default region size. */
    public static final String REGION_SIZE_PROPERTY = "mmap.mapping.regionSize";
    /** The default region size, used if system property {@value #REGION_SIZE_PROPERTY} is not set. */
    public static final int REGION_SIZE_DEFAULT = (int)Math.max(64*1024, REGION_SIZE_GRANULARITY);
    /** Name of the system property to override the default initial mapping pool size. */
    public static final String INITIAL_MAPPING_POOL_SIZE_PROPERTY = "mmap.mapping.initialMappingPoolSize";
    /**
     * The default initial mapping pool size, used if system property {@value #INITIAL_MAPPING_POOL_SIZE_PROPERTY} is
     * not set.
     */
    public static final int INITIAL_MAPPING_POOL_SIZE_DEFAULT = 64;

    /** Name of the system property to override the default ring cache size for mapped regions. */
    public static final String REGION_CACHE_SIZE_PROPERTY = "mmap.mapping.regionCacheSize";
    /**
     * The default ring cache size for mapped regions, used if system property {@value #REGION_CACHE_SIZE_PROPERTY} is
     * not set.
     */
    public static final int REGION_CACHE_SIZE_DEFAULT = 4;
    /** Name of the system property to override the default LRU cache size for mapped regions. */
    public static final String REGION_LRU_CACHE_SIZE_PROPERTY = "mmap.mapping.regionLruCacheSize";
    /**
     * The default LRU cache size for mapped regions, used if system property {@value #REGION_LRU_CACHE_SIZE_PROPERTY}
     * is not set.
     */
    public static final int REGION_LRU_CACHE_SIZE_DEFAULT = 0;
    /**
     * Name of the system property to override the default defer-unmapping flag.
     *
     * @see MappingStrategyConfig#deferUnmapping()
     */
    public static final String DEFER_UNMAPPING_PROPERTY = "mmap.mapping.deferUnmapping";
    /** The default defer-unmapping flag, used if system property {@value #DEFER_UNMAPPING_PROPERTY} is not set. */
    public static final boolean DEFER_UNMAPPING_DEFAULT = false;

    /** Name of the system property to override the default async-mapping flag. */
    public static final String ASYNC_MAPPING_PROPERTY = "mmap.mapping.asyncMapping";
    /** The default async-mapping flag, used if system property {@value #ASYNC_MAPPING_PROPERTY} is not set. */
    public static final boolean ASYNC_MAPPING_DEFAULT = true;
    /** Name of the system property to override the default async-unmapping flag. */
    public static final String ASYNC_UNMAPPING_PROPERTY = "mmap.mapping.asyncUnmapping";
    /** The default async-unmapping flag, used if system property {@value #ASYNC_UNMAPPING_PROPERTY} is not set. */
    public static final boolean ASYNC_UNMAPPING_DEFAULT = true;

    /** Name of the system property to override the default number of regions to map ahead. */
    public static final String REGIONS_TO_MAP_AHEAD_PROPERTY = "mmap.mapping.async.regionsToMapAhead";
    /**
     * The default number of regions to map ahead, used if system property {@value #REGIONS_TO_MAP_AHEAD_PROPERTY} is
     * not set.
     */
    public static final int REGIONS_TO_MAP_AHEAD_DEFAULT = 2;
    /** Name of the system property to override the default ahead-mapping cache size. */
    public static final String AHEAD_MAPPING_CACHE_SIZE_PROPERTY = "mmap.mapping.async.aheadMappingCacheSize";
    /**
     * The default ahead-mapping cache size, used if system property {@value #AHEAD_MAPPING_CACHE_SIZE_PROPERTY} is not
     * set.
     */
    public static final int AHEAD_MAPPING_CACHE_SIZE_DEFAULT = 0;
    /** Name of the system property to override the default unmapping cache size. */
    public static final String UNMAPPING_CACHE_SIZE_PROPERTY = "mmap.mapping.async.unmappingCacheSize";
    /** The default unmapping cache size, used if system property {@value #UNMAPPING_CACHE_SIZE_PROPERTY} is not set. */
    public static final int UNMAPPING_CACHE_SIZE_DEFAULT = 32;
    /**
     * Name of the system property to override the idle strategy of mapping runtimes created by default, with the class
     * name of an {@link IdleStrategy} or of a {@link Supplier} of idle strategies (with a public no-arg constructor).
     */
    public static final String MAPPING_RUNTIME_IDLE_STRATEGY_PROPERTY = "mmap.mapping.async.mappingRuntimeIdleStrategy";
    /** Supplier of the default idle strategy of mapping runtimes, used if the idle strategy property is not set. */
    public static final Supplier<IdleStrategy> MAPPING_RUNTIME_IDLE_STRATEGY_DEFAULT = () -> BusySpinIdleStrategy.INSTANCE;
    /**
     * Name of the system property to pin the threads of mapping runtimes created by default to CPUs, with a CPU list
     * in Linux cpulist format such as {@code 4-7}, {@code 0,2,4,6}, {@code 4-} for CPU 4 up to the highest online
     * CPU, or {@code 4} for CPU 4 only; every mapping runtime thread is pinned to a different CPU of the list, the
     * first one free in list order, see {@link CpuPinning#threadFactory(String, boolean, int[])}. Unmapping
     * runtime threads are never pinned.
     */
    public static final String MAPPING_RUNTIME_CPUS_PROPERTY = "mmap.mapping.async.mappingRuntimeCpus";
    /** Value of the mapping runtime CPUs property for unpinned mapping runtime threads */
    public static final String MAPPING_RUNTIME_CPUS_NONE = "NONE";
    /** The default CPUs of mapping runtime threads, used if the CPUs property is not set: unpinned. */
    public static final String MAPPING_RUNTIME_CPUS_DEFAULT = MAPPING_RUNTIME_CPUS_NONE;
    /** Name of the system property to override the {@link SharingPolicy} of the default mapping runtime. */
    public static final String MAPPING_RUNTIME_SHARING_POLICY_PROPERTY = "mmap.mapping.async.mappingRuntimeSharingPolicy";
    /** The default sharing policy of the mapping runtime, used if the sharing policy property is not set. */
    public static final SharingPolicy MAPPING_RUNTIME_SHARING_POLICY_DEFAULT = SharingPolicy.SHARED;
    /**
     * Name of the system property to override the idle strategy of unmapping runtimes created by default, with the
     * class
     * name of an {@link IdleStrategy} or of a {@link Supplier} of idle strategies (with a public no-arg constructor).
     */
    public static final String UNMAPPING_RUNTIME_IDLE_STRATEGY_PROPERTY = "mmap.mapping.async.unmappingRuntimeIdleStrategy";
    /** Supplier of the default idle strategy of unmapping runtimes, used if the idle strategy property is not set. */
    public static final Supplier<IdleStrategy> UNMAPPING_RUNTIME_IDLE_STRATEGY_DEFAULT = BackoffIdleStrategy::new;
    /** Name of the system property to override the {@link SharingPolicy} of the default unmapping runtime. */
    public static final String UNMAPPING_RUNTIME_SHARING_POLICY_PROPERTY = "mmap.mapping.async.unmappingRuntimeSharingPolicy";
    /** The default sharing policy of the unmapping runtime, used if the sharing policy property is not set. */
    public static final SharingPolicy UNMAPPING_RUNTIME_SHARING_POLICY_DEFAULT = SharingPolicy.SHARED;

    /** Listener property value for listeners ignoring all events. */
    public static final String NO_OP_LISTENER = "NO_OP";
    /**
     * Lifecycle listener property value for a listener logging lifecycle events at debug level to SLF4J if present on
     * the class path, and to {@link java.util.logging java.util.logging} otherwise.
     */
    public static final String LOG_DEBUG_LISTENER = "LOG_DEBUG";
    /** As {@link #LOG_DEBUG_LISTENER}, but logging at info level (internal resources still at debug level). */
    public static final String LOG_INFO_LISTENER = "LOG_INFO";
    /** As {@link #LOG_DEBUG_LISTENER}, but logging at warn level (internal resources still at debug level). */
    public static final String LOG_WARN_LISTENER = "LOG_WARN";
    /**
     * Error listener property value for a listener logging errors and warnings to SLF4J if present on the class path,
     * and to {@link java.util.logging java.util.logging} otherwise.
     */
    public static final String LOGGING_LISTENER = "LOGGING";
    /**
     * Name of the system property to override the default lifecycle listener, with {@value #LOG_DEBUG_LISTENER},
     * {@value #LOG_INFO_LISTENER}, {@value #LOG_WARN_LISTENER}, {@value #NO_OP_LISTENER} or the class name of a
     * {@link LifecycleListener} with a public no-arg constructor.
     */
    public static final String LIFECYCLE_LISTENER_PROPERTY = "mmap.mapping.lifecycleListener";
    /** The default lifecycle listener, used if the lifecycle listener property is not set. */
    public static final String LIFECYCLE_LISTENER_DEFAULT = LOG_DEBUG_LISTENER;
    /**
     * Name of the system property to override the default error listener, with {@value #LOGGING_LISTENER},
     * {@value #NO_OP_LISTENER} or the class name of an {@link ErrorListener} with a public no-arg constructor.
     */
    public static final String ERROR_LISTENER_PROPERTY = "mmap.mapping.errorListener";
    /** The default error listener, used if the error listener property is not set. */
    public static final String ERROR_LISTENER_DEFAULT = LOGGING_LISTENER;

    /** Instance created for a property value, reused while the property value is unchanged */
    private record PropertyInstance(String value, Object instance) {}
    private static final AtomicReference<PropertyInstance> LIFECYCLE_LISTENER_INSTANCE = new AtomicReference<>();
    private static final AtomicReference<PropertyInstance> ERROR_LISTENER_INSTANCE = new AtomicReference<>();
    private static final AtomicReference<PropertyInstance> MAPPING_RUNTIME_THREAD_FACTORY_INSTANCE =
            new AtomicReference<>();
    private static final String MAPPING_RUNTIME_THREAD_NAME_PREFIX = "mapper";

    /**
     * Returns the default minimum file size (and file size increment) in expand-file mode.
     *
     * @return the value of system property {@value #MIN_FILE_SIZE_PROPERTY} if set, and {@value #MIN_FILE_SIZE_DEFAULT}
     *         otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     * @see MappingConfig#minFileSize()
     */
    public static long defaultMinFileSize() {
        return getLongProperty(MIN_FILE_SIZE_PROPERTY, Constraints::validateMinFileSize, MIN_FILE_SIZE_DEFAULT);
    }

    /**
     * Returns the default maximum file size.
     *
     * @return the value of system property {@value #MAX_FILE_SIZE_PROPERTY} if set, and {@value #MAX_FILE_SIZE_DEFAULT}
     *         otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     * @see MappingConfig#maxFileSize()
     */
    public static long defaultMaxFileSize() {
        return getLongProperty(MAX_FILE_SIZE_PROPERTY, Constraints::validateMaxFileSize, MAX_FILE_SIZE_DEFAULT);
    }

    /**
     * Returns the default expand-file flag.
     *
     * @return the value of system property {@value #EXPAND_FILE_PROPERTY} if set, and {@value #EXPAND_FILE_DEFAULT}
     *         otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     * @see MappingConfig#expandFile()
     */
    public static boolean defaultExpandFile() {
        return getBooleanProperty(EXPAND_FILE_PROPERTY, EXPAND_FILE_DEFAULT);
    }

    /**
     * Returns the default roll-files flag.
     *
     * @return the value of system property {@value #ROLL_FILES_PROPERTY} if set, and {@value #ROLL_FILES_DEFAULT}
     *         otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     * @see MappingConfig#rollFiles()
     */
    public static boolean defaultRollFiles() {
        return getBooleanProperty(ROLL_FILES_PROPERTY, ROLL_FILES_DEFAULT);
    }

    /**
     * Returns the default maximum number of files kept open in roll-files mode.
     *
     * @return the value of system property {@value #MAX_OPEN_FILES_PROPERTY} if set, and
     *         {@value #MAX_OPEN_FILES_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     * @see MappingConfig#maxOpenFiles()
     */
    public static int defaultMaxOpenFiles() {
        return getIntProperty(MAX_OPEN_FILES_PROPERTY, Constraints::validateMaxOpenFiles, MAX_OPEN_FILES_DEFAULT);
    }

    /**
     * Returns the default number of files to create ahead in roll-files mode.
     *
     * @return the value of system property {@value #FILES_TO_CREATE_AHEAD_PROPERTY} if set, and
     *         {@value #FILES_TO_CREATE_AHEAD_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     * @see MappingConfig#filesToCreateAhead()
     */
    public static int defaultFilesToCreateAhead() {
        return getIntProperty(FILES_TO_CREATE_AHEAD_PROPERTY, Constraints::validateFilesToCreateAhead, FILES_TO_CREATE_AHEAD_DEFAULT);
    }

    /**
     * Returns the default region size.
     *
     * @return the value of system property {@value #REGION_SIZE_PROPERTY} if set, and {@link #REGION_SIZE_DEFAULT}
     *         otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     * @see MappingConfig#regionSize()
     */
    public static int defaultRegionSize() {
        return getIntProperty(REGION_SIZE_PROPERTY, Constraints::validateRegionSize, REGION_SIZE_DEFAULT);
    }

    /**
     * Returns the initial size of a {@link org.tools4j.mmap.mapping.api.MappingPool MappingPool} created via
     * {@link org.tools4j.mmap.mapping.api.Mappings Mappings} without explicit pool size, a capacity hint for the
     * expected number of mappings acquired from the pool. This is a pool parameter and hence not part of
     * {@link MappingConfig}.
     *
     * @return the value of system property {@value #INITIAL_MAPPING_POOL_SIZE_PROPERTY} if set, and
     *         {@value #INITIAL_MAPPING_POOL_SIZE_DEFAULT} otherwise
     */
    public static int defaultInitialMappingPoolSize() {
        return getIntProperty(INITIAL_MAPPING_POOL_SIZE_PROPERTY, Constraints::validateInitialPoolSize, INITIAL_MAPPING_POOL_SIZE_DEFAULT);
    }

    /**
     * Returns the default ring cache size for mapped regions.
     *
     * @return the value of system property {@value #REGION_CACHE_SIZE_PROPERTY} if set, and
     *         {@value #REGION_CACHE_SIZE_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     * @see MappingStrategyConfig#cacheSize()
     */
    public static int defaultRegionCacheSize() {
        return getIntProperty(REGION_CACHE_SIZE_PROPERTY, Constraints::validateRegionCacheSize, REGION_CACHE_SIZE_DEFAULT);
    }

    /**
     * Returns the default LRU cache size for mapped regions.
     *
     * @return the value of system property {@value #REGION_LRU_CACHE_SIZE_PROPERTY} if set, and
     *         {@value #REGION_LRU_CACHE_SIZE_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     * @see MappingStrategyConfig#lruCacheSize()
     */
    public static int defaultRegionLruCacheSize() {
        return getIntProperty(REGION_LRU_CACHE_SIZE_PROPERTY, Constraints::validateRegionLruCacheSize, REGION_LRU_CACHE_SIZE_DEFAULT);
    }

    /**
     * Returns the default defer-unmapping flag.
     *
     * @return the value of system property {@value #DEFER_UNMAPPING_PROPERTY} if set, and
     *         {@value #DEFER_UNMAPPING_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     * @see MappingStrategyConfig#deferUnmapping()
     */
    public static boolean defaultDeferUnmapping() {
        return getBooleanProperty(DEFER_UNMAPPING_PROPERTY, DEFER_UNMAPPING_DEFAULT);
    }

    /**
     * Returns the default async-mapping flag.
     *
     * @return the value of system property {@value #ASYNC_MAPPING_PROPERTY} if set, and {@value #ASYNC_MAPPING_DEFAULT}
     *         otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     * @see MappingStrategyConfig#asyncMapping()
     */
    public static boolean defaultAsyncMapping() {
        return getBooleanProperty(ASYNC_MAPPING_PROPERTY, ASYNC_MAPPING_DEFAULT);
    }

    /**
     * Returns the default async-unmapping flag.
     *
     * @return the value of system property {@value #ASYNC_UNMAPPING_PROPERTY} if set, and
     *         {@value #ASYNC_UNMAPPING_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     * @see MappingStrategyConfig#asyncUnmapping()
     */
    public static boolean defaultAsyncUnmapping() {
        return getBooleanProperty(ASYNC_UNMAPPING_PROPERTY, ASYNC_UNMAPPING_DEFAULT);
    }

    /**
     * Returns the default number of regions to map ahead.
     *
     * @return the value of system property {@value #REGIONS_TO_MAP_AHEAD_PROPERTY} if set, and
     *         {@value #REGIONS_TO_MAP_AHEAD_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     * @see AsyncMappingConfig#regionsToMapAhead()
     */
    public static int defaultRegionsToMapAhead() {
        return getIntProperty(REGIONS_TO_MAP_AHEAD_PROPERTY, Constraints::validateRegionsToMapAhead, REGIONS_TO_MAP_AHEAD_DEFAULT);
    }

    /**
     * Returns the default ahead-mapping cache size.
     *
     * @return the value of system property {@value #AHEAD_MAPPING_CACHE_SIZE_PROPERTY} if set, and
     *         {@value #AHEAD_MAPPING_CACHE_SIZE_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     * @see AsyncMappingConfig#aheadMappingCacheSize()
     */
    public static int defaultAheadMappingCacheSize() {
        return getIntProperty(AHEAD_MAPPING_CACHE_SIZE_PROPERTY, Constraints::validateAheadMappingCacheSize, AHEAD_MAPPING_CACHE_SIZE_DEFAULT);
    }

    /**
     * Returns the default unmapping cache size.
     *
     * @return the value of system property {@value #UNMAPPING_CACHE_SIZE_PROPERTY} if set, and
     *         {@value #UNMAPPING_CACHE_SIZE_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     * @see AsyncUnmappingConfig#unmappingCacheSize()
     */
    public static int defaultUnmappingCacheSize() {
        return getIntProperty(UNMAPPING_CACHE_SIZE_PROPERTY, Constraints::validateUnmappingCacheSize, UNMAPPING_CACHE_SIZE_DEFAULT);
    }

    /**
     * Returns the supplier of idle strategies for mapping runtimes created by default. Every invocation of the returned
     * supplier provides a new idle strategy instance, unless the configured supplier itself returns shared instances.
     *
     * @return a supplier for the idle strategy class (or supplier class) given by system property
     *         {@value #MAPPING_RUNTIME_IDLE_STRATEGY_PROPERTY} if set, and
     *         {@link #MAPPING_RUNTIME_IDLE_STRATEGY_DEFAULT} otherwise
     * @see org.tools4j.mmap.mapping.api.AsyncRuntimeInstances
     */
    public static Supplier<? extends IdleStrategy> defaultMappingRuntimeIdleStrategySupplier() {
        return getSupplierProperty(MAPPING_RUNTIME_IDLE_STRATEGY_PROPERTY, IdleStrategy.class,
                MAPPING_RUNTIME_IDLE_STRATEGY_DEFAULT);
    }

    /**
     * Returns the supplier of idle strategies for unmapping runtimes created by default. Every invocation of the
     * returned supplier provides a new idle strategy instance, unless the configured supplier itself returns shared
     * instances.
     *
     * @return a supplier for the idle strategy class (or supplier class) given by system property
     *         {@value #UNMAPPING_RUNTIME_IDLE_STRATEGY_PROPERTY} if set, and
     *         {@link #UNMAPPING_RUNTIME_IDLE_STRATEGY_DEFAULT} otherwise
     * @see org.tools4j.mmap.mapping.api.AsyncRuntimeInstances
     */
    public static Supplier<? extends IdleStrategy> defaultUnmappingRuntimeIdleStrategySupplier() {
        return getSupplierProperty(UNMAPPING_RUNTIME_IDLE_STRATEGY_PROPERTY, IdleStrategy.class,
                UNMAPPING_RUNTIME_IDLE_STRATEGY_DEFAULT);
    }

    /**
     * Returns the factory for threads of mapping runtimes created by default, pinning them to CPUs if configured. The
     * thread factory is created once and reused while the property value is unchanged, so that every new mapping
     * runtime thread is pinned to a CPU not in use by another one.
     *
     * @return the thread factory for the CPUs given by system property {@value #MAPPING_RUNTIME_CPUS_PROPERTY} if
     *         set, and {@value #MAPPING_RUNTIME_CPUS_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid
     * @see org.tools4j.mmap.mapping.api.AsyncRuntimeInstances
     */
    public static ThreadFactory defaultMappingRuntimeThreadFactory() {
        final String propVal = System.getProperty(MAPPING_RUNTIME_CPUS_PROPERTY, MAPPING_RUNTIME_CPUS_DEFAULT).trim();
        if (propVal.equalsIgnoreCase(MAPPING_RUNTIME_CPUS_NONE)) {
            return AsyncRuntimeImpl.DEFAULT_THREAD_FACTORY;
        }
        return getInstanceProperty(propVal, ThreadFactory.class, MAPPING_RUNTIME_THREAD_FACTORY_INSTANCE,
                value -> mappingRuntimeThreadFactory(MAPPING_RUNTIME_CPUS_PROPERTY, value));
    }

    private static ThreadFactory mappingRuntimeThreadFactory(final String propName, final String propVal) {
        try {
            return CpuPinning.threadFactory(MAPPING_RUNTIME_THREAD_NAME_PREFIX, true, CpuLists.parse(propVal),
                    defaultErrorListener());
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid value for system property: " + propName + "=" + propVal +
                    ", expected " + MAPPING_RUNTIME_CPUS_NONE + " or a CPU list such as 4-7, 0,2,4,6 or 4-", e);
        }
    }

    /**
     * Returns the sharing policy of the default mapping runtime.
     *
     * @return the sharing policy named by system property {@value #MAPPING_RUNTIME_SHARING_POLICY_PROPERTY} if set,
     *         and {@link #MAPPING_RUNTIME_SHARING_POLICY_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is not a {@link SharingPolicy} constant name
     */
    public static SharingPolicy defaultMappingRuntimeSharingPolicy() {
        return getEnumProperty(MAPPING_RUNTIME_SHARING_POLICY_PROPERTY, SharingPolicy.class,
                MAPPING_RUNTIME_SHARING_POLICY_DEFAULT);
    }

    /**
     * Returns the sharing policy of the default unmapping runtime.
     *
     * @return the sharing policy named by system property {@value #UNMAPPING_RUNTIME_SHARING_POLICY_PROPERTY} if set,
     *         and {@link #UNMAPPING_RUNTIME_SHARING_POLICY_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is not a {@link SharingPolicy} constant name
     */
    public static SharingPolicy defaultUnmappingRuntimeSharingPolicy() {
        return getEnumProperty(UNMAPPING_RUNTIME_SHARING_POLICY_PROPERTY, SharingPolicy.class,
                UNMAPPING_RUNTIME_SHARING_POLICY_DEFAULT);
    }

    /**
     * Returns the default lifecycle listener, also used by the shared async runtimes. The logging listeners log
     * lifecycle events at the given level to SLF4J if present on the class path, and to
     * {@link java.util.logging java.util.logging} otherwise. A listener class given by system property is instantiated
     * once and reused while the property value is unchanged.
     *
     * @return the listener given by system property {@value #LIFECYCLE_LISTENER_PROPERTY} if set, and by
     *         {@link #LIFECYCLE_LISTENER_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid, or the listener class cannot be
     *                                  instantiated
     */
    public static LifecycleListener defaultLifecycleListener() {
        return getListenerProperty(LIFECYCLE_LISTENER_PROPERTY, LifecycleListener.class, LIFECYCLE_LISTENER_DEFAULT,
                MappingConfigurations::lifecycleListenerByName, LIFECYCLE_LISTENER_INSTANCE);
    }

    /**
     * Returns the default error listener, also used by the shared async runtimes. The logging listener logs errors and
     * warnings to SLF4J if present on the class path, and to {@link java.util.logging java.util.logging} otherwise,
     * suppressing repetitions of recently logged errors. A listener class given by system property is instantiated
     * once and reused while the property value is unchanged.
     *
     * @return the listener given by system property {@value #ERROR_LISTENER_PROPERTY} if set, and by
     *         {@link #ERROR_LISTENER_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is invalid, or the listener class cannot be
     *                                  instantiated
     */
    public static ErrorListener defaultErrorListener() {
        return getListenerProperty(ERROR_LISTENER_PROPERTY, ErrorListener.class, ERROR_LISTENER_DEFAULT,
                MappingConfigurations::errorListenerByName, ERROR_LISTENER_INSTANCE);
    }

    /**
     * Returns the supplier of the default mapping runtime, provided by
     * {@link AsyncRuntimeInstances#mappingRuntimeSupplier(SharingPolicy)} for the
     * {@linkplain #defaultMappingRuntimeSharingPolicy() default sharing policy}.
     *
     * @return the supplier of the default mapping runtime
     * @throws IllegalArgumentException if the sharing policy system property value is invalid
     */
    public static Supplier<? extends AsyncRuntime> defaultMappingRuntimeSupplier() {
        return AsyncRuntimeInstances.mappingRuntimeSupplier(defaultMappingRuntimeSharingPolicy());
    }

    /**
     * Returns the supplier of the default unmapping runtime, provided by
     * {@link AsyncRuntimeInstances#unmappingRuntimeSupplier(SharingPolicy)} for the
     * {@linkplain #defaultUnmappingRuntimeSharingPolicy() default sharing policy}.
     *
     * @return the supplier of the default unmapping runtime
     * @throws IllegalArgumentException if the sharing policy system property value is invalid
     */
    public static Supplier<? extends AsyncRuntime> defaultUnmappingRuntimeSupplier() {
        return AsyncRuntimeInstances.unmappingRuntimeSupplier(defaultUnmappingRuntimeSharingPolicy());
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

    @SuppressWarnings("SameParameterValue")
    private static <E extends Enum<E>> E getEnumProperty(final String propertyName,
                                                         final Class<E> type,
                                                         final E defaultValue) {
        final String propVal = System.getProperty(propertyName, null);
        if (propVal == null) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(type, propVal);
        } catch (final Exception e) {
            throw new IllegalArgumentException("Invalid value for system property: " + propertyName + "=" + propVal, e);
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static <T> Supplier<T> getSupplierProperty(final String propertyName,
                                                       final Class<T> type,
                                                       final Supplier<T> defaultValueSupplier) {
        requireNonNull(propertyName);
        requireNonNull(type);
        requireNonNull(defaultValueSupplier);
        final String propVal = System.getProperty(propertyName, null);
        return propVal == null ? defaultValueSupplier : newSupplier(propertyName, propVal, type);
    }

    /**
     * Returns a supplier for the type given by class name as property value, either the class of the value itself or
     * of a supplier of such values. Every invocation returns a new value, unless it is a supplier returning the same
     * value every time.
     */
    private static <T> Supplier<T> newSupplier(final String propName, final String propVal, final Class<T> type) {
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
                    supplierPtr.set(() -> newObjInstance(propName, propVal, type));
                    return type.cast(value);
                }
                if (value instanceof Supplier) {
                    supplier = (Supplier<?>) value;
                    supplierPtr.set(supplier);
                    return type.cast(supplier.get());
                }
                throw new IllegalArgumentException("Value expected to be of type " + type.getName() +
                        " or a supplier of such a value, but was found to be: " + value);
            } catch (final Exception e) {
                throw new IllegalArgumentException("Invalid value for system property: " + propName + "=" + propVal, e);
            }
        };
    }

    /** Returns the lifecycle listener for the given property value, or null if it is not one of the named values */
    private static LifecycleListener lifecycleListenerByName(final String name) {
        return switch (name) {
            case LOG_DEBUG_LISTENER -> LoggingLifecycleListener.DEBUG;
            case LOG_INFO_LISTENER -> LoggingLifecycleListener.INFO;
            case LOG_WARN_LISTENER -> LoggingLifecycleListener.WARN;
            case NO_OP_LISTENER -> LifecycleListener.NO_OP;
            default -> null;
        };
    }

    /** Returns the error listener for the given property value, or null if it is not one of the named values */
    private static ErrorListener errorListenerByName(final String name) {
        return switch (name) {
            case LOGGING_LISTENER -> LoggingErrorListener.INSTANCE;
            case NO_OP_LISTENER -> ErrorListener.NO_OP;
            default -> null;
        };
    }

    private static <T> T getListenerProperty(final String propertyName,
                                             final Class<T> type,
                                             final String defaultValue,
                                             final Function<? super String, ? extends T> listenersByName,
                                             final AtomicReference<PropertyInstance> instance) {
        final String propVal = System.getProperty(propertyName, defaultValue);
        final T namedListener = listenersByName.apply(propVal);
        if (namedListener != null) {
            return namedListener;
        }
        return getInstanceProperty(propVal, type, instance, value -> newObjInstance(propertyName, value, type));
    }

    private static <T> T getInstanceProperty(final String propVal,
                                             final Class<T> type,
                                             final AtomicReference<PropertyInstance> instance,
                                             final Function<? super String, ? extends T> factory) {
        final PropertyInstance current = instance.get();
        if (current != null && current.value().equals(propVal)) {
            return type.cast(current.instance());
        }
        //NOTE: racing threads may each create an instance, the first to publish its instance wins
        final PropertyInstance created = new PropertyInstance(propVal, factory.apply(propVal));
        final PropertyInstance witness = instance.compareAndExchange(current, created);
        final PropertyInstance winner = witness == current ? created : witness;
        return type.cast(winner.value().equals(propVal) ? winner.instance() : created.instance());
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
