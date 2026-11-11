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
package org.tools4j.mmap.mapping.api;

import org.tools4j.mmap.core.idle.Idler;
import org.tools4j.mmap.mapping.config.SharingPolicy;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.tools4j.mmap.mapping.config.MappingConfigurations.defaultErrorListener;
import static org.tools4j.mmap.mapping.config.MappingConfigurations.defaultLifecycleListener;
import static org.tools4j.mmap.mapping.config.MappingConfigurations.defaultMappingRuntimeIdlerSupplier;
import static org.tools4j.mmap.mapping.config.MappingConfigurations.defaultMappingRuntimeThreadFactory;
import static org.tools4j.mmap.mapping.config.MappingConfigurations.defaultUnmappingRuntimeIdlerSupplier;

/**
 * Defines factory and accessor methods to create new or retrieve shared {@link AsyncRuntime} instances.
 */
public enum AsyncRuntimeInstances {
    ;
    private static final AtomicInteger mapperCount = new AtomicInteger();
    private static final AtomicInteger unmapperCount = new AtomicInteger();
    private static final ThreadLocal<AsyncRuntime> threadLocalMapper
            = ThreadLocal.withInitial(AsyncRuntimeInstances::newMappingRuntime);
    private static final ThreadLocal<AsyncRuntime> threadLocalUnmapper
            = ThreadLocal.withInitial(AsyncRuntimeInstances::newUnmappingRuntime);
    private static final Supplier<AsyncRuntime> SHARED_MAPPING_RUNTIME_SUPPLIER =
            AsyncRuntimeInstances::sharedMappingRuntime;
    private static final Supplier<AsyncRuntime> SHARED_UNMAPPING_RUNTIME_SUPPLIER =
            AsyncRuntimeInstances::sharedUnmappingRuntime;
    private static final Supplier<AsyncRuntime> THREAD_LOCAL_MAPPING_RUNTIME_SUPPLIER =
            AsyncRuntimeInstances::threadLocalMappingRuntime;
    private static final Supplier<AsyncRuntime> THREAD_LOCAL_UNMAPPING_RUNTIME_SUPPLIER =
            AsyncRuntimeInstances::threadLocalUnmappingRuntime;
    private static final Supplier<AsyncRuntime> NEW_MAPPING_RUNTIME_SUPPLIER = AsyncRuntimeInstances::newMappingRuntime;
    private static final Supplier<AsyncRuntime> NEW_UNMAPPING_RUNTIME_SUPPLIER =
            AsyncRuntimeInstances::newUnmappingRuntime;

    /**
     * Creates a new mapping runtime instance that automatically stops after the last task de-registration and uses the
     * default idler.
     *
     * @return a new mapping runtime instance
     * @see org.tools4j.mmap.mapping.config.MappingConfigurations#defaultMappingRuntimeIdlerSupplier()
     */
    public static AsyncRuntime newMappingRuntime() {
        return newMappingRuntime(defaultMappingRuntimeIdlerSupplier().get());
    }

    /**
     * Creates a new mapping runtime instance that automatically stops after the last task de-registration and uses the
     * specified idler.
     *
     * @param idler the idler used by the new runtime instance
     * @return a new mapping runtime instance
     */
    public static AsyncRuntime newMappingRuntime(final Idler idler) {
        return newMappingRuntime(idler, true);
    }

    /**
     * Creates a new mapping runtime instance with the specified idler and auto-stop behavior, and a thread
     * pinned to CPUs as defined by the
     * {@linkplain org.tools4j.mmap.mapping.config.MappingConfigurations#defaultMappingRuntimeThreadFactory() default}.
     *
     * @param idler the idler used by the new runtime instance
     * @param autoStopOnLastDeregister if true the runtime automatically stops after the last task de-registration
     * @return a new mapping runtime instance
     */
    public static AsyncRuntime newMappingRuntime(final Idler idler,
                                                 final boolean autoStopOnLastDeregister) {
        return newMappingRuntime(idler, autoStopOnLastDeregister, defaultMappingRuntimeThreadFactory());
    }

    /**
     * Creates a new mapping runtime instance with the specified idler, auto-stop behavior and thread factory,
     * for instance a {@linkplain CpuPinning#threadFactory(String, boolean, int[]) pinned thread factory} to pin the
     * runtime thread to a CPU.
     *
     * @param idler the idler used by the new runtime instance
     * @param autoStopOnLastDeregister if true the runtime automatically stops after the last task de-registration
     * @param threadFactory the factory creating the runtime thread
     * @return a new mapping runtime instance
     */
    public static AsyncRuntime newMappingRuntime(final Idler idler,
                                                 final boolean autoStopOnLastDeregister,
                                                 final ThreadFactory threadFactory) {
        return AsyncRuntime.create("mapper-" + mapperCount.incrementAndGet(), idler, autoStopOnLastDeregister,
                threadFactory, defaultLifecycleListener(), defaultErrorListener());
    }

    /**
     * Creates a new unmapping runtime instance that automatically stops after the last task de-registration and uses the
     * default idler.
     *
     * @return a new unmapping runtime instance
     * @see org.tools4j.mmap.mapping.config.MappingConfigurations#defaultUnmappingRuntimeIdlerSupplier()
     */
    public static AsyncRuntime newUnmappingRuntime() {
        return newUnmappingRuntime(defaultUnmappingRuntimeIdlerSupplier().get());
    }

    /**
     * Creates a new unmapping runtime instance that automatically stops after the last task de-registration and uses the
     * specified idler.
     *
     * @param idler the idler used by the new runtime instance
     * @return a new unmapping runtime instance
     */
    public static AsyncRuntime newUnmappingRuntime(final Idler idler) {
        return newUnmappingRuntime(idler, true);
    }

    /**
     * Creates a new unmapping runtime instance with the specified idler and auto-stop behavior.
     *
     * @param idler the idler used by the new runtime instance
     * @param autoStopOnLastDeregister if true the runtime automatically stops after the last task de-registration
     * @return a new unmapping runtime instance
     */
    public static AsyncRuntime newUnmappingRuntime(final Idler idler,
                                                   final boolean autoStopOnLastDeregister) {
        return AsyncRuntime.create("unmapper-" + unmapperCount.incrementAndGet(), idler, autoStopOnLastDeregister);
    }

    /**
     * Returns the shared mapping runtime instance, a singleton instance in the virtual machine. Its thread is pinned
     * to CPUs as defined by the
     * {@linkplain org.tools4j.mmap.mapping.config.MappingConfigurations#defaultMappingRuntimeThreadFactory() default}
     * when this method is first invoked.
     * @return the shared mapping runtime instance
     */
    public static AsyncRuntime sharedMappingRuntime() {
        return MappingInstance.SHARED;
    }

    /**
     * Returns the shared unmapping runtime instance, a singleton instance in the virtual machine.
     * @return the shared unmapping runtime instance
     */
    public static AsyncRuntime sharedUnmappingRuntime() {
        return UnmappingInstance.SHARED;
    }

    /**
     * Returns the thread-local mapping runtime instance for sharing within the calling thread
     * @return the per-thread mapping runtime instance for sharing within the calling thread
     */
    public static AsyncRuntime threadLocalMappingRuntime() {
        return threadLocalMapper.get();
    }

    /**
     * Returns the thread-local unmapping runtime instance for sharing within the calling thread
     * @return the per-thread unmapping runtime instance for sharing within the calling thread
     */
    public static AsyncRuntime threadLocalUnmappingRuntime() {
        return threadLocalUnmapper.get();
    }

    /**
     * Returns a mapping runtime supplier that returns either shared or newly created runtime instances when called,
     * depending on the provided sharing policy. The same supplier instance is returned for the same sharing policy.
     *
     * @param sharingPolicy the policy defining if and how the retrieved runtime instances should be shared
     * @return a supplier for mapping runtime instances
     */
    public static Supplier<AsyncRuntime> mappingRuntimeSupplier(final SharingPolicy sharingPolicy) {
        return switch (sharingPolicy) {
            case SHARED -> SHARED_MAPPING_RUNTIME_SUPPLIER;
            case PER_THREAD -> THREAD_LOCAL_MAPPING_RUNTIME_SUPPLIER;
            case INDIVIDUAL -> NEW_MAPPING_RUNTIME_SUPPLIER;
        };
    }

    /**
     * Returns an unmapping runtime supplier that returns either shared or newly created runtime instances when called,
     * depending on the provided sharing policy. The same supplier instance is returned for the same sharing policy.
     *
     * @param sharingPolicy the policy defining if and how the retrieved runtime instances should be shared
     * @return a supplier for unmapping runtime instances
     */
    public static Supplier<AsyncRuntime> unmappingRuntimeSupplier(final SharingPolicy sharingPolicy) {
        return switch (sharingPolicy) {
            case SHARED -> SHARED_UNMAPPING_RUNTIME_SUPPLIER;
            case PER_THREAD -> THREAD_LOCAL_UNMAPPING_RUNTIME_SUPPLIER;
            case INDIVIDUAL -> NEW_UNMAPPING_RUNTIME_SUPPLIER;
        };
    }

    //lazy init through nested class which is only loaded when needed
    private static class MappingInstance {
        static final AsyncRuntime SHARED = AsyncRuntime.create("mapper-shared",
                defaultMappingRuntimeIdlerSupplier().get(), false, defaultMappingRuntimeThreadFactory(),
                defaultLifecycleListener(), defaultErrorListener());
    }

    //lazy init through nested class which is only loaded when needed
    private static class UnmappingInstance {
        static final AsyncRuntime SHARED = AsyncRuntime.create("unmapper-shared",
                defaultUnmappingRuntimeIdlerSupplier().get(), false);
    }
}
