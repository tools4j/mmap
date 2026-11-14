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
 * The cost of the expandable flag checked by every write of the memory buffers, per {@link ExpandableProfile}: never
 * taken, always taken (within the capacity, so without growing), or taken by some buffers at the same call sites.
 * Only the memory buffers have the flag, so only they are compared; {@code increment} does not depend on the size.
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
public class ExpandableBenchmark {
    static volatile long pollutionResult;

    @Param({"MEMORY_BUFFER_UNSAFE", "MEMORY_BUFFER_FFM"})
    public Implementation implementation;

    @Param({"DIRECT", "HEAP"})
    public BenchMemory memory;

    @Param({"FIXED", "EXPANDABLE", "MIXED", "MIXED_ALL"})
    public ExpandableProfile profile;

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
        if (profile == ExpandableProfile.MIXED || profile == ExpandableProfile.MIXED_ALL) {
            pollutionResult = BufferOperations.polluteWithExpandable(memory, size);
        }
        if (profile == ExpandableProfile.MIXED_ALL) {
            pollutionResult += BufferOperations.polluteWithExpandable(memory.other(), size);
        }
        sourceBlock = MemoryBlock.allocate(memory, size);
        source = implementation.create(memory);
        sourceBlock.wrapInto(source);
        if (profile == ExpandableProfile.EXPANDABLE) {
            buffer = MemoryBufferBench.expandable(memory, size);
        } else {
            block = MemoryBlock.allocate(memory, size);
            buffer = implementation.create(memory);
            block.wrapInto(buffer);
        }
        BufferOperations.verify(buffer, source, size, true, true);
        if (buffer.capacity() != size) {
            throw new IllegalStateException("Buffer grew to " + buffer.capacity() + " bytes, expected " + size);
        }
    }

    @Benchmark
    public void writeLongs() {
        BufferOperations.writeLongs(buffer, size, value++);
    }

    @Benchmark
    public void writeLongsRelease() {
        BufferOperations.writeLongsRelease(buffer, size, value++);
    }

    @Benchmark
    public void copy() {
        BufferOperations.copy(buffer, source, size);
    }

    @Benchmark
    public boolean increment() {
        return BufferOperations.increment(buffer);
    }
}
