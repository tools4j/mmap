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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.tools4j.mmap.core.buffer.MemoryBufferFlyweight;
import org.tools4j.mmap.core.buffer.MemoryBuffers;
import org.tools4j.mmap.core.unsafe.UnsafeAccess;
import org.tools4j.mmap.mapping.api.FileInitialiser;
import org.tools4j.mmap.mapping.config.MappingConfigurations;
import org.tools4j.mmap.mapping.unsafe.ExpandableSizeFileMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import static org.tools4j.mmap.mapping.unsafe.MappingMode.MAP_OR_CREATE;

/**
 * The cost of extending a file by one region and writing to each new page through a mapping, with the file
 * initialiser {@code ZERO} allocating the new space with {@code fallocate} or writing zeros, and {@code LAZY} leaving
 * it sparse for reference: the time holding the file lock while extending, and the page faults of the first writes,
 * which convert the unwritten extents of allocated space or allocate blocks of sparse space.
 * <p>
 * Only meaningful on Linux with Java 22 or later, where {@code ZERO_FALLOCATE} calls {@code fallocate}; elsewhere it
 * writes zeros like {@code ZERO_WRITE}. The file grows with every operation, so each measurement is a single shot of
 * {@value #BATCH} extensions of a new file, deleted after the iteration: divide the score by {@value #BATCH} for the
 * time per region.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, batchSize = FileExtensionBenchmark.BATCH)
@Measurement(iterations = 20, batchSize = FileExtensionBenchmark.BATCH)
@Fork(value = 2, jvmArgsAppend = {"--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
        "--enable-native-access=ALL-UNNAMED"})
public class FileExtensionBenchmark {
    static final int BATCH = 64;
    private static final int REGION_SIZE = 2 * 1024 * 1024;
    private static final int PAGE_SIZE = UnsafeAccess.pageSize();

    /** How new file space is initialised. */
    public enum Fill {
        /** {@code ZERO} allocating with {@code fallocate}, zero fill mode {@code FALLOCATE} */
        ZERO_FALLOCATE(FileInitialiser.ZERO, "FALLOCATE"),
        /** {@code ZERO} writing zeros, zero fill mode {@code WRITE} */
        ZERO_WRITE(FileInitialiser.ZERO, "WRITE"),
        /** {@code LAZY}, sparse space, for reference */
        LAZY(FileInitialiser.LAZY, "WRITE");

        final FileInitialiser initialiser;
        final String zeroFillMode;

        Fill(final FileInitialiser initialiser, final String zeroFillMode) {
            this.initialiser = initialiser;
            this.zeroFillMode = zeroFillMode;
        }
    }

    @Param
    public Fill fill;

    private final MemoryBufferFlyweight buffer = MemoryBuffers.flyweight();
    private File file;
    private ExpandableSizeFileMapper fileMapper;
    private long position;

    @Setup(Level.Iteration)
    public void createFile() throws IOException {
        System.setProperty(MappingConfigurations.ZERO_FILL_PROPERTY, fill.zeroFillMode);
        file = Files.createTempFile("file-extension", ".map").toFile();
        fileMapper = new ExpandableSizeFileMapper(file, REGION_SIZE, (long) REGION_SIZE * BATCH, REGION_SIZE, 0,
                fill.initialiser);
        position = 0;
    }

    @TearDown(Level.Iteration)
    public void deleteFile() throws IOException {
        fileMapper.close();
        Files.deleteIfExists(file.toPath());
    }

    @Benchmark
    public long extendAndWrite() {
        final long address = fileMapper.map(position, REGION_SIZE, MAP_OR_CREATE);
        try {
            buffer.wrap(address, REGION_SIZE);
            for (int offset = 0; offset < REGION_SIZE; offset += PAGE_SIZE) {
                buffer.putLong(offset, position + offset);
            }
        } finally {
            fileMapper.unmap(position, address, REGION_SIZE);
        }
        position += REGION_SIZE;
        return address;
    }
}
