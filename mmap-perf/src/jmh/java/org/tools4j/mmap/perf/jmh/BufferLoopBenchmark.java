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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * Loops of plain and ordered long accesses over a buffer, and copies between two buffers, per implementation and
 * memory kind; with {@code polluted}, the call sites have seen the other memory kind first.
 * <p>
 * The loops of ordered accesses magnify their per-access overhead, since each barrier makes the next access load the
 * buffer's fields and check bounds and alignment again; they catch regressions in the checks, but real code seldom
 * has such loops. {@code publish} and {@code consume} are the use case: a payload written with plain writes and
 * published with one release write of a header, and read after one acquire read of the header.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = {
        "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED",
        "--enable-native-access=ALL-UNNAMED"
})
public class BufferLoopBenchmark {
    static volatile long pollutionResult;

    @Param({"AGRONA", "UNSAFE_BRANCH", "BYTE_BUFFER", "MEMORY_BUFFER_UNSAFE", "MEMORY_BUFFER_FFM"})
    public Implementation implementation;

    @Param({"DIRECT", "HEAP"})
    public BenchMemory memory;

    @Param({"false", "true"})
    public boolean polluted;

    @Param({"64", "4096"})
    public int size;

    @SuppressWarnings({"unused", "FieldCanBeLocal"}) //keeps the memory reachable
    private MemoryBlock block;
    @SuppressWarnings({"unused", "FieldCanBeLocal"}) //keeps the memory reachable
    private MemoryBlock sourceBlock;
    private BenchBuffer buffer;
    private BenchBuffer source;
    private long value;

    @Setup(Level.Trial)
    public void setup() {
        implementation.selectMemoryAccess();
        if (polluted) {
            pollutionResult = BufferOperations.pollute(implementation, memory.other(), size);
        }
        block = MemoryBlock.allocate(memory, size);
        sourceBlock = MemoryBlock.allocate(memory, size);
        buffer = implementation.create(memory);
        source = implementation.create(memory);
        block.wrapInto(buffer);
        sourceBlock.wrapInto(source);
        BufferOperations.verify(buffer, source, size, implementation.supportsCompareAndSet(memory));
    }

    @Benchmark
    public long readLongs() {
        return BufferOperations.readLongs(buffer, size);
    }

    @Benchmark
    public void writeLongs() {
        BufferOperations.writeLongs(buffer, size, value++);
    }

    @Benchmark
    public long readLongsAcquire() {
        return BufferOperations.readLongsAcquire(buffer, size);
    }

    @Benchmark
    public void writeLongsRelease() {
        BufferOperations.writeLongsRelease(buffer, size, value++);
    }

    @Benchmark
    public void publish() {
        BufferOperations.publish(buffer, size, value++);
    }

    @Benchmark
    public long consume() {
        return BufferOperations.consume(buffer, size);
    }

    @Benchmark
    public void copy() {
        BufferOperations.copy(buffer, source, size);
    }
}
