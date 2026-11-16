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
package org.tools4j.mmap.core.pinning;

/**
 * The mechanism reading and setting the CPU affinity of threads, chosen once per JVM with the system property
 * {@value org.tools4j.mmap.core.config.CoreConfigurations#THREAD_AFFINITY_PROPERTY} when thread affinity is first
 * used, see {@link CpuPinning#mode()}. All backends support Linux only. FFM and Agrona call into native code, which
 * the JVM warns about unless {@code --enable-native-access=ALL-UNNAMED} allows it, for FFM's restricted methods since
 * Java 22 and for Agrona's JNI library since Java 24, and later Java versions may deny; a denied backend is not
 * supported.
 */
public enum AffinityMode {
    /** The first supported of {@link #FFM}, {@link #AGRONA} and {@link #TASKSET}, or {@link #NONE}; the default. */
    AUTO,
    /**
     * The Foreign Function and Memory API calling {@code sched_getaffinity} and {@code sched_setaffinity}, Java 22 or
     * later, 64-bit Linux on any architecture.
     */
    FFM,
    /**
     * Agrona's {@code ThreadAffinity}, through JNI, if Agrona is on the class path, an optional dependency; Linux on
     * x86_64 and aarch64 only, for which Agrona contains a native library.
     */
    AGRONA,
    /**
     * The {@code taskset} command of util-linux, run in a new process each time an affinity is set, which waits for
     * it to exit; the affinity is read from {@code /proc/thread-self/status}. Linux on any architecture, if
     * {@code taskset} is installed; calls no native code from Java.
     */
    TASKSET,
    /** None: thread affinity is not supported, and threads are not pinned. */
    NONE
}
