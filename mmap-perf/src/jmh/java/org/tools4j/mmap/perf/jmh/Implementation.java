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
 * The buffer implementations compared by the benchmarks, in the order of the columns of the results: those accessing
 * memory through Unsafe (the memory buffers of mmap-core, Agrona's, Chronicle's, Netty's with Unsafe and the fastest
 * prototype of the earlier benchmarks), then those through public JDK API only (the memory buffers with FFM, Netty's
 * default on Java 24 and later, and the JDK's ByteBuffer).
 */
public enum Implementation {
    /** The memory buffers of mmap-core with memory access through Unsafe, see {@link MemoryBufferBench} */
    MEMORY_BUFFER_UNSAFE {
        @Override
        BenchBuffer create(final BenchMemory kind) {
            return MemoryBufferBench.flyweight();
        }
    },
    /** Agrona's UnsafeBuffer: one class, every access with the byte array (null for direct memory) as base object */
    AGRONA {
        @Override
        BenchBuffer create(final BenchMemory kind) {
            return new AgronaBuffer();
        }
    },
    /** Chronicle Bytes, see {@link ChronicleBuffer} */
    CHRONICLE {
        @Override
        BenchBuffer create(final BenchMemory kind) {
            return new ChronicleBuffer();
        }
    },
    /** Netty's ByteBuf with Unsafe, as by default before Java 24, see {@link NettyBuffer} */
    NETTY_UNSAFE {
        @Override
        BenchBuffer create(final BenchMemory kind) {
            return new NettyBuffer();
        }
    },
    /** The prototype with Unsafe and one class branching per access between the array and a constant null base */
    UNSAFE_BRANCH {
        @Override
        BenchBuffer create(final BenchMemory kind) {
            return new UnsafeBranchBuffer();
        }
    },
    /** The memory buffers of mmap-core with memory access through FFM, see {@link MemoryBufferBench} */
    MEMORY_BUFFER_FFM {
        @Override
        BenchBuffer create(final BenchMemory kind) {
            return MemoryBufferBench.flyweight();
        }
    },
    /** Netty's ByteBuf as by default on Java 24 and later, without Unsafe, see {@link NettyBuffer} */
    NETTY {
        @Override
        BenchBuffer create(final BenchMemory kind) {
            return new NettyBuffer();
        }
    },
    /** The JDK's ByteBuffer, see {@link JdkByteBuffer} */
    BYTE_BUFFER {
        @Override
        BenchBuffer create(final BenchMemory kind) {
            return new JdkByteBuffer();
        }
    };

    private static final String NETTY_NO_UNSAFE_PROPERTY = "io.netty.noUnsafe";

    abstract BenchBuffer create(BenchMemory kind);

    /**
     * Selects how the memory buffers and Netty access memory, which each chooses once per JVM: to be invoked before
     * any buffer is created, which works because JMH runs every combination of parameters in JVMs of its own.
     */
    void selectMemoryAccess() {
        if (this == MEMORY_BUFFER_FFM) {
            System.setProperty(CoreConfigurations.MEMORY_ACCESS_PROPERTY, MemoryAccessMode.FFM.name());
        }
        if (this == NETTY_UNSAFE) {
            System.setProperty(NETTY_NO_UNSAFE_PROPERTY, "false");
        }
    }

    boolean supportsOrderedAccess() {
        return this != NETTY && this != NETTY_UNSAFE;
    }

    boolean supportsCompareAndSet(final BenchMemory kind) {
        return this != BYTE_BUFFER && supportsOrderedAccess();
    }
}
