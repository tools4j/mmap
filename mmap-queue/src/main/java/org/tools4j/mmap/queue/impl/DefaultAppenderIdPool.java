/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2024 tools4j.org (Marco Terzer, Anton Anufriev)
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
package org.tools4j.mmap.queue.impl;

import org.agrona.concurrent.AtomicBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.region.api.FileMapper;
import org.tools4j.mmap.region.api.Region;
import org.tools4j.mmap.region.impl.FileInitialiser;
import org.tools4j.mmap.region.impl.InitialBytes;
import org.tools4j.mmap.region.impl.RegionMapperFactories;
import org.tools4j.mmap.region.impl.SingleFileReadWriteFileMapper;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * A pool of appender ids based on a memory-mapped file associated with a given queue name.
 * It guarantees that there are no two or more processes/threads acquiring the same id for given queue name.
 *
 * File contains number of open appenders (counter). Acquiring of an appender id is done
 * by incrementing the counter atomically and releasing appender id is done
 * by decrementing the counter atomically.
 */
public class DefaultAppenderIdPool implements AppenderIdPool {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAppenderIdPool.class);

    private static final int MAX_APPENDERS = 256;
    private static final int REGION_SIZE = 8;
    private static final String FILE_SUFFIX = "open_appenders";
    private final Region region;
    private final String queueName;
    private final AtomicBoolean closed = new AtomicBoolean();

    public DefaultAppenderIdPool(final String directory, final String queueName) {
        this.queueName = requireNonNull(queueName);
        this.region = fixedRegion(directory, queueName);
    }


    @Override
    public short acquire() {
        if (closed.get()) {
            throw new IllegalStateException(format("Appender id pool for %s queue is closed", queueName));
        }
        final AtomicBuffer buffer = region.buffer();

        int currentAppenderCounter;
        do {
            currentAppenderCounter = buffer.getIntVolatile(0);
            if (currentAppenderCounter >= MAX_APPENDERS) {
                throw new IllegalStateException(format("Exceeded max number of appenders %d in %s queue", MAX_APPENDERS, queueName));
            }
        } while (!buffer.compareAndSetInt(0, currentAppenderCounter, currentAppenderCounter + 1));

        LOGGER.info("Acquired appenderId {} for {} queue", currentAppenderCounter, queueName);
        return (short) currentAppenderCounter;
    }

    @Override
    public void release(final short appenderId) {
        if (closed.get()) {
            throw new IllegalStateException(format("Appender id pool for %s queue is closed", queueName));
        }
        final AtomicBuffer buffer = region.buffer();

        int currentAppenderCounter;
        do {
            currentAppenderCounter = buffer.getIntVolatile(0);
        } while (!buffer.compareAndSetInt(0, currentAppenderCounter, currentAppenderCounter - 1));
        LOGGER.info("Released appenderId {} for {} queue", appenderId, queueName);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            final int currentCounter = region.buffer().getIntVolatile(0);
            region.close();
            LOGGER.info("Closed appender id pool. queue={}, open appenders={}", queueName, currentCounter);
        }
    }

    private static Region fixedRegion(final String directory, final String queueName) {
        final File file = new File(directory, queueName + "_" + FILE_SUFFIX);
        final FileMapper fileMapper = new SingleFileReadWriteFileMapper(file, REGION_SIZE, fileInitialiser());
        final Region region = RegionMapperFactories.sync(fileMapper, REGION_SIZE, 1).map(0);
        if (region.isReady()) {
            return region;
        }
        throw new IllegalStateException("Could not map fixed region: " + region);
    }

    private static FileInitialiser fileInitialiser() {
        return (fileName, fileChannel) -> {
            try {
                FileLock fileLock = acquireLock(fileChannel);
                try {
                    if (fileChannel.size() == 0) {
                        fileChannel.transferFrom(InitialBytes.ZERO, 0, REGION_SIZE);
                        fileChannel.force(true);
                        LOGGER.info("Initialised file data in {} for appender id pool", fileName);
                    }
                } finally {
                    fileLock.release();
                }
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Failed to initialise an appender id pool file " + fileName, e);
            }
        };
    }

    private static FileLock acquireLock(final FileChannel fileChannel) throws IOException {
        FileLock fileLock = null;
        boolean lockAcquired = false;
        while (!lockAcquired) {
            try {
                fileLock = fileChannel.tryLock();
                lockAcquired = fileLock != null;
            } catch (OverlappingFileLockException e) {
                // handle the exception - sleep and try again
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e1) {
                    throw new RuntimeException(e);
                }
            }
        }
        return fileLock;
    }

}
