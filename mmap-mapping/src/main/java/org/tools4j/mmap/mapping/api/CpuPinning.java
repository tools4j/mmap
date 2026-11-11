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

import org.tools4j.mmap.core.base.ConstantDescribable;
import org.tools4j.mmap.core.base.Describable;
import org.tools4j.mmap.core.base.ErrorListener;
import org.tools4j.mmap.core.config.CoreConfigurations;
import org.tools4j.mmap.mapping.impl.PinnedCpuBinding;
import org.tools4j.mmap.mapping.impl.PinnedThreadFactory;
import org.tools4j.mmap.mapping.impl.ThreadAffinities;

import java.util.concurrent.ThreadFactory;

/**
 * Pins the calling thread to a CPU. Pinning is supported on Linux for x86_64 and aarch64 (through Agrona's
 * {@link org.agrona.concurrent.affinity.ThreadAffinity ThreadAffinity}). Pinning never throws: if it is not supported
 * or fails, for instance because the CPU is not available to the process, a warning is reported to the error listener
 * and the thread continues to run unpinned.
 * <p>
 * To create threads pinned to different CPUs, use a {@linkplain #threadFactory(String, boolean, int[]) thread factory}
 * from this class: each thread it creates is pinned to the first CPU of its list not in use by another running thread
 * of the same factory, and frees the CPU again when its runnable completes. If all CPUs are in use, or a thread cannot
 * be pinned, a warning is reported and the thread runs unpinned. Different factories, and threads pinned directly, do
 * not know of each other, so their CPUs should not overlap; for instance, main threads can be pinned to low CPUs and
 * factories given the higher ones.
 */
public enum CpuPinning {
    ;
    /** The largest CPU ID supported */
    public static final int MAX_CPU = 8191;

    private static final Describable SOURCE = new ConstantDescribable("CpuPinning");

    /**
     * Returns whether pinning threads is supported on this platform; the first invocation loads Agrona's native
     * library.
     *
     * @return true if threads can be pinned to CPUs on this platform
     */
    public static boolean isSupported() {
        return ThreadAffinities.isSupported();
    }

    /**
     * Pins the calling thread to the given CPU, reporting warnings to the
     * {@linkplain CoreConfigurations#defaultErrorListener() default error listener}.
     *
     * @param cpu the CPU to pin the calling thread to
     * @return the binding, to be closed by the same thread when it no longer needs to be pinned; unbound if the
     *         thread was not pinned
     * @throws IllegalArgumentException if the CPU is negative or larger than {@link #MAX_CPU}
     */
    public static CpuBinding pinCurrentThread(final int cpu) {
        return pinCurrentThread(cpu, SOURCE, CoreConfigurations.defaultErrorListener());
    }

    /**
     * Pins the calling thread to the given CPU, reporting warnings with the given name as source.
     *
     * @param cpu           the CPU to pin the calling thread to
     * @param name          the name used as source of reported warnings, for instance the name of the component
     *                      pinning its thread
     * @param errorListener the listener for warnings if the thread cannot be pinned
     * @return the binding, to be closed by the same thread when it no longer needs to be pinned; unbound if the
     *         thread was not pinned
     * @throws IllegalArgumentException if the CPU is negative or larger than {@link #MAX_CPU}
     */
    public static CpuBinding pinCurrentThread(final int cpu, final String name, final ErrorListener errorListener) {
        return pinCurrentThread(cpu, new ConstantDescribable(name), errorListener);
    }

    /**
     * Pins the calling thread to the given CPU.
     *
     * @param cpu           the CPU to pin the calling thread to
     * @param source        the source of reported warnings, for instance a thread factory
     * @param errorListener the listener for warnings if the thread cannot be pinned
     * @return the binding, to be closed by the same thread when it no longer needs to be pinned; unbound if the
     *         thread was not pinned
     * @throws IllegalArgumentException if the CPU is negative or larger than {@link #MAX_CPU}
     */
    public static CpuBinding pinCurrentThread(final int cpu,
                                              final Describable source,
                                              final ErrorListener errorListener) {
        return PinnedCpuBinding.pin(validateCpu(cpu), source, errorListener, () -> {});
    }

    /**
     * Returns a factory creating threads pinned to the given CPUs, each thread to a different CPU, reporting warnings
     * to the {@linkplain CoreConfigurations#defaultErrorListener() default error listener}. Threads are named with
     * the given prefix followed by a dash and a sequence number starting at 1, for instance {@code mapper-1}.
     *
     * @param namePrefix the prefix of the names of created threads
     * @param daemon     true to create daemon threads
     * @param cpus       the CPUs to pin threads to, in order of preference, for instance only even CPUs
     * @return a factory creating pinned threads
     * @throws IllegalArgumentException if cpus is empty, contains a CPU twice, or a CPU that is negative or larger
     *                                  than {@link #MAX_CPU}
     */
    public static ThreadFactory threadFactory(final String namePrefix, final boolean daemon, final int[] cpus) {
        return new PinnedThreadFactory(namePrefix, daemon, cpus);
    }

    /**
     * Returns a factory creating threads pinned to the given CPUs, each thread to a different CPU. Threads are named
     * with the given prefix followed by a dash and a sequence number starting at 1, for instance {@code mapper-1}.
     *
     * @param namePrefix    the prefix of the names of created threads
     * @param daemon        true to create daemon threads
     * @param cpus          the CPUs to pin threads to, in order of preference, for instance only even CPUs
     * @param errorListener the listener for warnings if a thread cannot be pinned
     * @return a factory creating pinned threads
     * @throws IllegalArgumentException if cpus is empty, contains a CPU twice, or a CPU that is negative or larger
     *                                  than {@link #MAX_CPU}
     */
    public static ThreadFactory threadFactory(final String namePrefix,
                                              final boolean daemon,
                                              final int[] cpus,
                                              final ErrorListener errorListener) {
        return new PinnedThreadFactory(namePrefix, daemon, cpus, errorListener);
    }

    /**
     * Returns a factory creating threads pinned to the CPUs of the given range, each thread to a different CPU, see
     * {@link #threadFactory(String, boolean, int[])}.
     *
     * @param namePrefix the prefix of the names of created threads
     * @param daemon     true to create daemon threads
     * @param firstCpu   the first CPU to pin threads to
     * @param lastCpu    the last CPU to pin threads to, inclusive
     * @return a factory creating pinned threads
     * @throws IllegalArgumentException if a CPU is negative or larger than {@link #MAX_CPU}, or if the first CPU is
     *                                  larger than the last
     */
    public static ThreadFactory threadFactory(final String namePrefix,
                                              final boolean daemon,
                                              final int firstCpu,
                                              final int lastCpu) {
        return new PinnedThreadFactory(namePrefix, daemon, firstCpu, lastCpu);
    }

    /**
     * Returns a factory creating threads pinned to the CPUs from the given first CPU up to the highest online CPU, each
     * thread to a different CPU, see {@link #threadFactory(String, boolean, int[])}.
     *
     * @param namePrefix the prefix of the names of created threads
     * @param daemon     true to create daemon threads
     * @param firstCpu   the first CPU to pin threads to
     * @return a factory creating pinned threads
     * @throws IllegalArgumentException if first CPU is negative or larger than the highest online CPU
     */
    public static ThreadFactory threadFactory(final String namePrefix, final boolean daemon, final int firstCpu) {
        return new PinnedThreadFactory(namePrefix, daemon, firstCpu);
    }

    static int validateCpu(final int cpu) {
        if (cpu < 0 || cpu > MAX_CPU) {
            throw new IllegalArgumentException("Invalid CPU " + cpu + ", must be in [0, " + MAX_CPU + "]");
        }
        return cpu;
    }
}
