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
package org.tools4j.mmap.perf.jmh;

import org.tools4j.mmap.core.buffer.MemoryAccessMode;
import org.tools4j.mmap.core.config.CoreConfigurations;

/**
 * The buffer implementations compared by the benchmarks: Agrona's, the JDK's, the fastest prototype of the earlier
 * benchmarks, and the memory buffers of mmap-core with either memory access mode.
 */
public enum Implementation {
    /** Agrona's UnsafeBuffer: one class, every access with the byte array (null for direct memory) as base object */
    AGRONA {
        @Override
        BenchBuffer create(final BenchMemory kind) {
            return new AgronaBuffer();
        }
    },
    /** The prototype with Unsafe and one class branching per access between the array and a constant null base */
    UNSAFE_BRANCH {
        @Override
        BenchBuffer create(final BenchMemory kind) {
            return new UnsafeBranchBuffer();
        }
    },
    /** The JDK's ByteBuffer, see {@link JdkByteBuffer} */
    BYTE_BUFFER {
        @Override
        BenchBuffer create(final BenchMemory kind) {
            return new JdkByteBuffer();
        }
    },
    /** The memory buffers of mmap-core with memory access through Unsafe, see {@link MemoryBufferBench} */
    MEMORY_BUFFER_UNSAFE {
        @Override
        BenchBuffer create(final BenchMemory kind) {
            return MemoryBufferBench.flyweight();
        }
    },
    /** The memory buffers of mmap-core with memory access through FFM, see {@link MemoryBufferBench} */
    MEMORY_BUFFER_FFM {
        @Override
        BenchBuffer create(final BenchMemory kind) {
            return MemoryBufferBench.flyweight();
        }
    };

    abstract BenchBuffer create(BenchMemory kind);

    /**
     * Selects the memory access mode of the memory buffers, which is chosen once per JVM: to be invoked before any
     * buffer is created, which works because JMH runs every combination of parameters in JVMs of its own.
     */
    void selectMemoryAccess() {
        if (this == MEMORY_BUFFER_FFM) {
            System.setProperty(CoreConfigurations.MEMORY_ACCESS_PROPERTY, MemoryAccessMode.FFM.name());
        }
    }

    boolean supportsCompareAndSet(final BenchMemory kind) {
        return this != BYTE_BUFFER;
    }
}
