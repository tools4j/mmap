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

import org.tools4j.mmap.core.base.Closeable;

/**
 * The binding of a thread to a CPU, returned by {@link CpuPinning#pinCurrentThread(int)}. It is closed by the bound
 * thread when it no longer needs to be pinned, restoring its previous affinity.
 */
public interface CpuBinding extends Closeable {
    /** Value returned by {@link #cpu()} if the thread is not pinned */
    int NO_CPU = -1;

    /** Binding of a thread that was not pinned; it has nothing to restore and is always closed */
    CpuBinding UNBOUND = new CpuBinding() {
        @Override
        public int cpu() {
            return NO_CPU;
        }

        @Override
        public boolean isClosed() {
            return true;
        }

        @Override
        public void close() {
            //nothing to restore
        }

        @Override
        public String toString() {
            return "UNBOUND";
        }
    };

    /**
     * Returns the CPU the thread is pinned to.
     *
     * @return the CPU the thread is pinned to, or {@link #NO_CPU} if it was not pinned
     */
    int cpu();

    /**
     * Returns whether the thread was pinned.
     *
     * @return true if the thread was pinned
     */
    default boolean isBound() {
        return cpu() != NO_CPU;
    }

    /**
     * Releases the binding and restores the previous affinity of the bound thread. Invoking it again has no effect.
     *
     * @throws IllegalStateException if not invoked by the bound thread, leaving the binding open
     */
    @Override
    void close();
}
