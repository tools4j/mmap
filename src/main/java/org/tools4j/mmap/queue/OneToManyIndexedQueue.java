/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 mmap (tools4j), Marco Terzer
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
package org.tools4j.mmap.queue;

import org.tools4j.mmap.io.InitialBytes;
import org.tools4j.mmap.io.MappedFile;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MappedQueue implementation optimised for single Appender and multiple Enumerator support.
 * Uses an index io to avoid back tracking for volatile puts of message length field.
 */
public class OneToManyIndexedQueue implements MappedQueue {

    public static final String SUFFIX_INDEX = ".idx";
    public static final String SUFFIX_DATA = ".dat";
    public static final long DEFAULT_INDEX_REGION_SIZE = 1L << 14;//16 KB
    public static final long DEFAULT_DATA_REGION_SIZE = 1L << 18;//256 KB

    private final MappedFile indexFile;
    private final MappedFile dataFile;
    private final AtomicBoolean appenderCreated = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private OneToManyIndexedQueue(final MappedFile indexFile, final MappedFile dataFile) {
        this.indexFile = Objects.requireNonNull(indexFile);
        this.dataFile = Objects.requireNonNull(dataFile);
    }

    public static final MappedQueue createOrReplace(final String fileName) throws IOException {
        return createOrReplace(fileName, DEFAULT_INDEX_REGION_SIZE, DEFAULT_DATA_REGION_SIZE);
    }

    public static final MappedQueue createOrReplace(final String fileName, final long indexRegionSize, final long dataRegionSize) throws IOException {
        return open(new MappedFile(fileName + SUFFIX_INDEX, MappedFile.Mode.READ_WRITE_CLEAR, indexRegionSize, OneToManyIndexedQueue::initIndexFile), new MappedFile(fileName + SUFFIX_DATA, MappedFile.Mode.READ_WRITE_CLEAR, dataRegionSize));
    }

    public static final MappedQueue createOrAppend(final String fileName) throws IOException {
        return createOrAppend(fileName, DEFAULT_INDEX_REGION_SIZE, DEFAULT_DATA_REGION_SIZE);
    }

    public static final MappedQueue createOrAppend(final String fileName, final long indexRegionSize, final long dataRegionSize) throws IOException {
        return open(new MappedFile(fileName + SUFFIX_INDEX, MappedFile.Mode.READ_WRITE, indexRegionSize, OneToManyIndexedQueue::initIndexFile), new MappedFile(fileName + SUFFIX_DATA, MappedFile.Mode.READ_WRITE, dataRegionSize));
    }

    public static final MappedQueue openReadOnly(final String fileName) throws IOException {
        return openReadOnly(fileName, DEFAULT_INDEX_REGION_SIZE, DEFAULT_DATA_REGION_SIZE);
    }

    public static final MappedQueue openReadOnly(final String fileName, final long indexRgionSize, final long dataRegionSize) throws IOException {
        return open(new MappedFile(fileName + SUFFIX_INDEX, MappedFile.Mode.READ_ONLY, indexRgionSize), new MappedFile(fileName + SUFFIX_DATA, MappedFile.Mode.READ_ONLY, dataRegionSize));
    }

    private static final MappedQueue open(final MappedFile indexFile, final MappedFile dataFile) {
        return new OneToManyIndexedQueue(indexFile, dataFile);
    }

    private static void initIndexFile(final FileChannel fileChannel, final MappedFile.Mode mode) throws IOException {
        final FileLock lock = fileChannel.lock();
        try {
            switch (mode) {
                case READ_ONLY:
                    if (fileChannel.size() < 8) {
                        throw new IllegalArgumentException("Invalid io format");
                    }
                    break;
                case READ_WRITE:
                    if (fileChannel.size() >= 8) {
                        break;
                    }
                    //else: FALL THROUGH
                case READ_WRITE_CLEAR:
                    fileChannel.truncate(0);
                    fileChannel.transferFrom(InitialBytes.MINUS_ONE, 0, 8);
                    fileChannel.force(true);
                    break;
                default:
                    throw new IllegalArgumentException("Invalid mode: " + mode);
            }
        } finally {
            lock.release();
        }
    }

    @Override
    public Appender appender() {
        if (indexFile.getMode() == MappedFile.Mode.READ_ONLY) {
            throw new IllegalStateException("Cannot access appender for io in read-only mode");
        }
        if (appenderCreated.compareAndSet(false, true)) {
            return new OneToManyIndexedAppender(indexFile, dataFile);
        }
        throw new IllegalStateException("Only one appender supported");
    }

    @Override
    public Enumerator enumerator() {
        return new OneToManyIndexedEnumerator(indexFile, dataFile);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            indexFile.close();
            dataFile.close();
        }
    }

}
