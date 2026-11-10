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

import org.tools4j.mmap.core.base.Describable;
import org.tools4j.mmap.core.base.ErrorListener;
import org.tools4j.mmap.mapping.api.CpuBinding;

import java.util.Arrays;

import static java.util.Objects.requireNonNull;

/**
 * Binding of a thread pinned to a CPU, restoring the previous affinity when closed by the bound thread.
 */
public final class PinnedCpuBinding implements CpuBinding {
    private final int cpu;
    private final int[] previous;
    private final Thread thread;
    private final Describable source;
    private final ErrorListener errorListener;
    private final Runnable release;
    private boolean closed;

    /**
     * Pins the calling thread to the given CPU, reporting a warning if not possible.
     *
     * @param cpu           the valid CPU to pin the calling thread to
     * @param source        the source of reported warnings
     * @param errorListener the listener for warnings
     * @param release       invoked when the binding is closed, or immediately if the thread is not pinned
     * @return the binding, unbound if the thread was not pinned
     */
    public static CpuBinding pin(final int cpu,
                                 final Describable source,
                                 final ErrorListener errorListener,
                                 final Runnable release) {
        if (!ThreadAffinities.isSupported()) {
            release.run();
            errorListener.onWarning(source, "Thread " + Thread.currentThread().getName() + " not pinned to CPU " +
                    cpu + ", CPU affinity is not supported on this platform: " +
                    ThreadAffinities.unsupportedCause());
            return CpuBinding.UNBOUND;
        }
        try {
            final int[] previous = ThreadAffinities.currentThreadAffinity();
            ThreadAffinities.setCurrentThreadAffinity(cpu);
            return new PinnedCpuBinding(cpu, previous, source, errorListener, release);
        } catch (final RuntimeException e) {
            release.run();
            errorListener.onWarning(source, "Thread " + Thread.currentThread().getName() + " not pinned to CPU " +
                    cpu + ": " + e);
            return CpuBinding.UNBOUND;
        }
    }

    private PinnedCpuBinding(final int cpu,
                     final int[] previous,
                     final Describable source,
                     final ErrorListener errorListener,
                     final Runnable release) {
        this.cpu = cpu;
        this.previous = requireNonNull(previous);
        this.thread = Thread.currentThread();
        this.source = requireNonNull(source);
        this.errorListener = requireNonNull(errorListener);
        this.release = requireNonNull(release);
    }

    @Override
    public int cpu() {
        return cpu;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        if (Thread.currentThread() != thread) {
            throw new IllegalStateException("Binding of thread " + thread.getName() + " to CPU " + cpu +
                    " cannot be closed by thread " + Thread.currentThread().getName());
        }
        closed = true;
        try {
            ThreadAffinities.setCurrentThreadAffinity(previous);
        } catch (final RuntimeException e) {
            errorListener.onWarning(source, "Thread " + thread.getName() + " pinned to CPU " + cpu +
                    " failed to restore previous CPU affinity " + Arrays.toString(previous) + ": " + e);
        } finally {
            release.run();
        }
    }

    @Override
    public String toString() {
        return "PinnedCpuBinding:thread=" + thread.getName() + "|cpu=" + cpu + "|closed=" + closed;
    }
}
