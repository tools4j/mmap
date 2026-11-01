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
package org.tools4j.mmap.mapping.impl;

import org.tools4j.mmap.mapping.api.CpuBinding;
import org.tools4j.mmap.mapping.api.CpuPinning;
import org.tools4j.mmap.mapping.api.Describable;
import org.tools4j.mmap.mapping.api.ErrorListener;

import org.tools4j.mmap.mapping.config.MappingConfigurations;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Objects.requireNonNull;

/**
 * Thread factory creating threads pinned to CPUs from a given list, each thread to a different CPU. A thread pins
 * itself to the first CPU of the list not used by another running thread of this factory when it starts, and frees the
 * CPU again when its runnable completes. The list can be a range of CPUs, or any CPUs, for instance only even ones.
 * Threads are named with the given prefix followed by a dash and a sequence number starting at 1, for instance
 * {@code mapper-1}.
 * <p>
 * If all CPUs of the list are in use, or if a thread cannot be pinned, a warning is reported to the error listener
 * and the thread runs unpinned. Different factories, and threads pinned through {@link CpuPinning} directly, do not
 * know of each other, so their CPUs should not overlap; for instance, main threads can be pinned to low CPUs and
 * factories given the higher ones.
 */
public final class PinnedThreadFactory implements ThreadFactory, Describable {
    private final String namePrefix;
    private final boolean daemon;
    private final int[] cpus;
    private final ErrorListener errorListener;
    private final AtomicIdBitSet cpusInUse;
    private final AtomicInteger threadCount = new AtomicInteger();

    /**
     * Creates a factory pinning threads to the CPUs from the given first CPU up to the highest online CPU, reporting
     * warnings to the {@linkplain MappingConfigurations#defaultErrorListener() default error listener}.
     *
     * @param namePrefix the prefix of the names of created threads
     * @param daemon     true to create daemon threads
     * @param firstCpu   the first CPU to pin threads to
     * @throws IllegalArgumentException if first CPU is negative or larger than the highest online CPU
     */
    public PinnedThreadFactory(final String namePrefix, final boolean daemon, final int firstCpu) {
        this(namePrefix, daemon, firstCpu, SystemCpus.maxOnlineCpu());
    }

    /**
     * Creates a factory pinning threads to the CPUs of the given range, reporting warnings to the
     * {@linkplain MappingConfigurations#defaultErrorListener() default error listener}.
     *
     * @param namePrefix the prefix of the names of created threads
     * @param daemon     true to create daemon threads
     * @param firstCpu   the first CPU to pin threads to
     * @param lastCpu    the last CPU to pin threads to, inclusive
     * @throws IllegalArgumentException if a CPU is negative or larger than {@link CpuPinning#MAX_CPU}, or if the
     *                                  first CPU is larger than the last
     */
    public PinnedThreadFactory(final String namePrefix, final boolean daemon, final int firstCpu, final int lastCpu) {
        this(namePrefix, daemon, firstCpu, lastCpu, MappingConfigurations.defaultErrorListener());
    }

    /**
     * Creates a factory pinning threads to the CPUs of the given range.
     *
     * @param namePrefix    the prefix of the names of created threads
     * @param daemon        true to create daemon threads
     * @param firstCpu      the first CPU to pin threads to
     * @param lastCpu       the last CPU to pin threads to, inclusive
     * @param errorListener the listener for warnings if a thread cannot be pinned
     * @throws IllegalArgumentException if a CPU is negative or larger than {@link CpuPinning#MAX_CPU}, or if the
     *                                  first CPU is larger than the last
     */
    public PinnedThreadFactory(final String namePrefix,
                               final boolean daemon,
                               final int firstCpu,
                               final int lastCpu,
                               final ErrorListener errorListener) {
        this(namePrefix, daemon, CpuLists.range(firstCpu, lastCpu), errorListener);
    }

    /**
     * Creates a factory pinning threads to the given CPUs, reporting warnings to the
     * {@linkplain MappingConfigurations#defaultErrorListener() default error listener}.
     *
     * @param namePrefix the prefix of the names of created threads
     * @param daemon     true to create daemon threads
     * @param cpus       the CPUs to pin threads to, in order of preference
     * @throws IllegalArgumentException if cpus is empty, contains a CPU twice, or a CPU that is negative or larger
     *                                  than {@link CpuPinning#MAX_CPU}
     */
    public PinnedThreadFactory(final String namePrefix, final boolean daemon, final int[] cpus) {
        this(namePrefix, daemon, cpus, MappingConfigurations.defaultErrorListener());
    }

    /**
     * Creates a factory pinning threads to the given CPUs.
     *
     * @param namePrefix    the prefix of the names of created threads
     * @param daemon        true to create daemon threads
     * @param cpus          the CPUs to pin threads to, in order of preference
     * @param errorListener the listener for warnings if a thread cannot be pinned
     * @throws IllegalArgumentException if cpus is empty, contains a CPU twice, or a CPU that is negative or larger
     *                                  than {@link CpuPinning#MAX_CPU}
     */
    public PinnedThreadFactory(final String namePrefix,
                               final boolean daemon,
                               final int[] cpus,
                               final ErrorListener errorListener) {
        this.cpus = CpuLists.validate(cpus.clone());
        this.namePrefix = requireNonNull(namePrefix);
        this.daemon = daemon;
        this.errorListener = requireNonNull(errorListener);
        this.cpusInUse = AtomicIdBitSet.allocate(this.cpus.length);
    }

    /** @return the CPUs threads are pinned to, in order of preference */
    public int[] cpus() {
        return cpus.clone();
    }

    @Override
    public Thread newThread(final Runnable runnable) {
        requireNonNull(runnable);
        final Thread thread = new Thread(() -> {
            try (final CpuBinding ignored = pinCurrentThread()) {
                runnable.run();
            }
        }, namePrefix + "-" + threadCount.incrementAndGet());
        thread.setDaemon(daemon);
        return thread;
    }

    private CpuBinding pinCurrentThread() {
        final int index = cpusInUse.acquire();
        if (index == AtomicIdBitSet.NO_ID) {
            errorListener.onWarning(this, "Thread " + Thread.currentThread().getName() +
                    " not pinned, all CPUs are in use by other threads");
            return CpuBinding.UNBOUND;
        }
        return PinnedCpuBinding.pin(cpus[index], this, errorListener, () -> cpusInUse.release(index));
    }

    @Override
    public StringBuilder describeTo(final StringBuilder sb) {
        return sb.append("PinnedThreadFactory")
                .append(":namePrefix=").append(namePrefix)
                .append("|cpus=").append(CpuLists.format(cpus))
                .append("|cpusInUse=").append(cpusInUse.acquired());
    }

    @Override
    public String toString() {
        return Describable.describe(this);
    }
}
