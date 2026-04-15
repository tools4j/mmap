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
package org.tools4j.mmap.region.api;

import org.agrona.concurrent.IdleStrategy;
import org.tools4j.mmap.region.config.SharingPolicy;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.tools4j.mmap.region.config.MappingConfigurations.defaultMappingRuntimeIdleStrategySupplier;
import static org.tools4j.mmap.region.config.MappingConfigurations.defaultUnmappingRuntimeIdleStrategySupplier;

/**
 * Defines factory and accessor methods to create new or retrieve shared {@link AsyncRuntime} instances.
 */
public enum AsyncRuntimeInstances {
    ;
    private static final AtomicInteger mapperCount = new AtomicInteger();
    private static final AtomicInteger unmapperCount = new AtomicInteger();
    private static final ThreadLocal<AsyncRuntime> threadLocalMapper
            = ThreadLocal.withInitial(AsyncRuntimeInstances::newMappingRuntimeInstance);
    private static final ThreadLocal<AsyncRuntime> threadLocalUnmapper
            = ThreadLocal.withInitial(AsyncRuntimeInstances::newUnmappingRuntimeInstance);

    /**
     * Creates a new mapper runtime instance that automatically stops after the last task de-registration and uses the
     * default idle strategy.
     *
     * @return a new mapping runtime instance
     * @see org.tools4j.mmap.region.config.MappingConfigurations#defaultMappingRuntimeIdleStrategySupplier()
     */
    public static AsyncRuntime newMappingRuntimeInstance() {
        return newMappingRuntimeInstance(defaultMappingRuntimeIdleStrategySupplier().get());
    }

    /**
     * Creates a new mapper runtime instance that automatically stops after the last task de-registration and uses the
     * specified idle strategy.
     *
     * @param idleStrategy the idle strategy used by the new runtime instance
     * @return a new mapping runtime instance
     */
    public static AsyncRuntime newMappingRuntimeInstance(final IdleStrategy idleStrategy) {
        return newMappingRuntimeInstance(idleStrategy, true);
    }

    /**
     * Creates a new mapper runtime instance with the specified idle strategy and auto-stop behavior.
     *
     * @param idleStrategy the idle strategy used by the new runtime instance
     * @param autoStopOnLastDeregister if true the runtime automatically stops after the last task de-registration
     * @return a new mapping runtime instance
     */
    public static AsyncRuntime newMappingRuntimeInstance(final IdleStrategy idleStrategy,
                                                         final boolean autoStopOnLastDeregister) {
        return AsyncRuntime.create("mapper-" + mapperCount.incrementAndGet(), idleStrategy, autoStopOnLastDeregister);
    }

    /**
     * Creates a new unmapper runtime instance that automatically stops after the last task de-registration and uses the
     * default idle strategy.
     *
     * @return a new unmapping runtime instance
     * @see org.tools4j.mmap.region.config.MappingConfigurations#defaultUnmappingRuntimeIdleStrategySupplier()
     */
    public static AsyncRuntime newUnmappingRuntimeInstance() {
        return newUnmappingRuntimeInstance(defaultUnmappingRuntimeIdleStrategySupplier().get());
    }

    /**
     * Creates a new unmapper runtime instance that automatically stops after the last task de-registration and uses the
     * specified idle strategy.
     *
     * @param idleStrategy the idle strategy used by the new runtime instance
     * @return a new unmapping runtime instance
     */
    public static AsyncRuntime newUnmappingRuntimeInstance(final IdleStrategy idleStrategy) {
        return newUnmappingRuntimeInstance(idleStrategy, true);
    }

    /**
     * Creates a new unmapper runtime instance with the specified idle strategy and auto-stop behavior.
     *
     * @param idleStrategy the idle strategy used by the new runtime instance
     * @param autoStopOnLastDeregister if true the runtime automatically stops after the last task de-registration
     * @return a new unmapping runtime instance
     */
    public static AsyncRuntime newUnmappingRuntimeInstance(final IdleStrategy idleStrategy,
                                                           final boolean autoStopOnLastDeregister) {
        return AsyncRuntime.create("unmapper-" + unmapperCount.incrementAndGet(), idleStrategy, autoStopOnLastDeregister);
    }

    /**
     * Returns the shared mapping runtime instance, a singleton instance in the virtual machine.
     * @return the shared mapping runtime instance
     */
    public static AsyncRuntime sharedMappingRuntimeInstance() {
        return MappingInstance.SHARED;
    }

    /**
     * Returns the shared unmapping runtime instance, a singleton instance in the virtual machine.
     * @return the shared unmapping runtime instance
     */
    public static AsyncRuntime sharedUnmappingRuntimeInstance() {
        return UnmappingInstance.SHARED;
    }

    /**
     * Returns the thread-local mapping runtime instance for sharing within the calling thread
     * @return the per-thread mapping runtime instance for sharing within the calling thread
     */
    public static AsyncRuntime threadLocalMappingRuntimeInstance() {
        return threadLocalMapper.get();
    }

    /**
     * Returns the thread-local unmapping runtime instance for sharing within the calling thread
     * @return the per-thread unmapping runtime instance for sharing within the calling thread
     */
    public static AsyncRuntime threadLocalUnmappingRuntimeInstance() {
        return threadLocalUnmapper.get();
    }

    /**
     * Returns a mapping runtime supplier that returns either shared or newly created runtime instances when called,
     * depending on the provided sharing policy.
     *
     * @param sharingPolicy the policy defining if and how the retrieved runtime instances should be shared
     * @return a supplier for mapping runtime instances
     */
    public static Supplier<AsyncRuntime> mappingRuntimeSupplier(final SharingPolicy sharingPolicy) {
        return switch (sharingPolicy) {
            case SHARED -> AsyncRuntimeInstances::sharedMappingRuntimeInstance;
            case PER_THREAD -> AsyncRuntimeInstances::threadLocalMappingRuntimeInstance;
            case INDIVIDUAL -> AsyncRuntimeInstances::newMappingRuntimeInstance;
            default -> throw new IllegalArgumentException("Unsupported sharing policy: " + sharingPolicy);
        };
    }

    /**
     * Returns an unmapping runtime supplier that returns either shared or newly created runtime instances when called,
     * depending on the provided sharing policy.
     *
     * @param sharingPolicy the policy defining if and how the retrieved runtime instances should be shared
     * @return a supplier for unmapping runtime instances
     */
    public static Supplier<AsyncRuntime> unmappingRuntimeSupplier(final SharingPolicy sharingPolicy) {
        return switch (sharingPolicy) {
            case SHARED -> AsyncRuntimeInstances::sharedUnmappingRuntimeInstance;
            case PER_THREAD -> AsyncRuntimeInstances::threadLocalUnmappingRuntimeInstance;
            case INDIVIDUAL -> AsyncRuntimeInstances::newUnmappingRuntimeInstance;
            default -> throw new IllegalArgumentException("Unsupported sharing policy: " + sharingPolicy);
        };
    }

    //lazy init through nested class which is only loaded when needed
    private static class MappingInstance {
        static final AsyncRuntime SHARED = AsyncRuntime.create("mapper-shared",
                defaultMappingRuntimeIdleStrategySupplier().get(), false);
    }

    //lazy init through nested class which is only loaded when needed
    private static class UnmappingInstance {
        static final AsyncRuntime SHARED = AsyncRuntime.create("unmapper-shared",
                defaultUnmappingRuntimeIdleStrategySupplier().get(), false);
    }
}
