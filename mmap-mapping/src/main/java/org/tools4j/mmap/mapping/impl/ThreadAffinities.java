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

import org.agrona.concurrent.affinity.ThreadAffinity;

/**
 * Reads and sets the CPU affinity of the calling thread through Agrona's {@link ThreadAffinity}, which needs a native
 * library only available for Linux on x86_64 and aarch64.
 */
public enum ThreadAffinities {
    ;
    //lazy init through nested class which is only loaded when needed
    private static final class Support {
        static final Throwable UNSUPPORTED_CAUSE = unsupportedCause();

        private static Throwable unsupportedCause() {
            try {
                ThreadAffinity.getAffinity(ThreadAffinity.CURRENT_THREAD);
                return null;
            } catch (final LinkageError | RuntimeException e) {
                return e;
            }
        }
    }

    /** @return true if CPU affinity can be set and read on this platform */
    public static boolean isSupported() {
        return Support.UNSUPPORTED_CAUSE == null;
    }

    /** @return the reason why CPU affinity is not supported, or null if it is supported */
    public static Throwable unsupportedCause() {
        return Support.UNSUPPORTED_CAUSE;
    }

    /**
     * @return the CPUs the calling thread may run on
     * @throws IllegalStateException if not {@linkplain #isSupported() supported}
     * @throws RuntimeException if the affinity cannot be read
     */
    public static int[] currentThreadAffinity() {
        checkSupported();
        return ThreadAffinity.getAffinity(ThreadAffinity.CURRENT_THREAD);
    }

    /**
     * @param cpus the CPUs the calling thread may run on, not empty
     * @throws IllegalStateException if not {@linkplain #isSupported() supported}
     * @throws RuntimeException if the affinity cannot be set, for instance if the CPUs are not available to the process
     */
    public static void setCurrentThreadAffinity(final int... cpus) {
        checkSupported();
        ThreadAffinity.setAffinity(ThreadAffinity.CURRENT_THREAD, cpus);
    }

    private static void checkSupported() {
        final Throwable cause = Support.UNSUPPORTED_CAUSE;
        if (cause != null) {
            throw new IllegalStateException("CPU affinity is not supported on this platform", cause);
        }
    }
}
