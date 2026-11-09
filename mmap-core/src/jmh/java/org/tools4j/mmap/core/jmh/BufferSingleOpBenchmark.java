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
package org.tools4j.mmap.core.jmh;

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
 * Single operations per implementation and memory kind: an acquire read plus compare-and-set, and wrapping a buffer
 * alternately around two memory blocks plus one read, as when a mapping moves to another region; with
 * {@code polluted}, the call sites have seen the other memory kind first.
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
public class BufferSingleOpBenchmark {
    private static final int SIZE = 64;

    static volatile long pollutionResult;

    @Param({"AGRONA", "UNSAFE_SPLIT", "UNSAFE_BRANCH", "FFM_BRANCH", "FFM_SPLIT"})
    public Implementation implementation;

    @Param({"DIRECT", "HEAP"})
    public MemoryKind memory;

    @Param({"false", "true"})
    public boolean polluted;

    private final MemoryBlock[] blocks = new MemoryBlock[2];
    private BenchBuffer buffer;
    private int wraps;

    @Setup(Level.Trial)
    public void setup() {
        if (polluted) {
            pollutionResult = BufferOperations.pollute(implementation, memory.other(), SIZE);
        }
        blocks[0] = MemoryBlock.allocate(memory, SIZE);
        blocks[1] = MemoryBlock.allocate(memory, SIZE);
        buffer = implementation.create(memory);
        final BenchBuffer source = implementation.create(memory);
        blocks[1].wrapInto(source);
        blocks[0].wrapInto(buffer);
        BufferOperations.verify(buffer, source, SIZE, implementation.supportsCompareAndSet(memory));
    }

    /**
     * Fails for heap memory with FFM, which has no compare-and-set; exclude those combinations with {@code -p}.
     */
    @Benchmark
    public boolean increment() {
        return BufferOperations.increment(buffer);
    }

    @Benchmark
    public long wrapAndRead() {
        return BufferOperations.wrapAndRead(buffer, blocks[wraps++ & 1]);
    }
}
