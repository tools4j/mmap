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
package org.tools4j.mmap.concurrent.imp;

import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.concurrent.api.UniqueIdPool;
import org.tools4j.mmap.region.api.RegionAccessor;
import org.tools4j.mmap.region.impl.FileInitialiser;
import org.tools4j.mmap.region.impl.FixedSizeRegion;
import org.tools4j.mmap.region.impl.InitialBytes;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * A pool of unique ids based on a memory-mapped file.
 * It guarantees that there are no two or more processes/threads acquiring the same id for given name.
 *
 */
public class DefaultUniqueIdPool implements UniqueIdPool {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultUniqueIdPool.class);

    private final int maxValues;
    private static final String FILE_SUFFIX = "id_pool";
    private final RegionAccessor region;
    private final UnsafeBuffer buffer = new UnsafeBuffer();
    private final String name;
    private final AtomicBoolean closed = new AtomicBoolean();

    public DefaultUniqueIdPool(final String directory, final String name, final int maxValues) {
        this.name = requireNonNull(name);
        this.maxValues = maxValues;
        int regionSize = bytesToAccommodateNumberOfValues(maxValues);
        region = FixedSizeRegion.open(directory, name + "_" + FILE_SUFFIX,
                maxValues * Integer.SIZE, fileInitialiser(regionSize));

        region.wrap(0, buffer);
    }

    private int bytesToAccommodateNumberOfValues(final int maxValues) {
        return -Math.floorDiv(-maxValues, Integer.SIZE);
    }

    private static boolean isBitNotSet(int value, int bitPosition) {
        return (value & (1 << bitPosition)) == 0;
    }

    private static int setBit(int value, int bitPosition) {
        return value | (1 << bitPosition);
    }

    private static int clearBit(int value, int bitPosition) {
        return value & ~(1 << bitPosition);
    }

    @Override
    public int acquire() {
        if (closed.get()) {
            throw new IllegalStateException(format("Unique id pool for %s queue is closed", name));
        }

        for (int i = 0; i < maxValues; i++) {  // loop over all bits
            int intIndex = i / Integer.SIZE;
            int bitPosition = i & (Integer.SIZE - 1);

            int intValue = buffer.getIntVolatile(intIndex);
            if (isBitNotSet(intValue, bitPosition)) {
                int updatedValue = setBit(intValue, bitPosition);
                if (buffer.compareAndSetInt(intIndex, intValue, updatedValue)) {
                    LOGGER.info("Acquired id value {} for pool {}", i, name);
                    return i;
                }
            }
        }
        throw new IllegalStateException(format("Exceeded max number of values %d in pool %s", maxValues, name));
    }

    @Override
    public void release(final int idValue) {
        if (closed.get()) {
            throw new IllegalStateException(format("Unique id pool %s is closed", name));
        }
        if (idValue >= 0 && idValue < maxValues) {
            int intIndex = idValue / Integer.SIZE;
            int bitPosition = idValue & (Integer.SIZE - 1);

            int currentValue;
            int updatedValue;
            do {
                currentValue = buffer.getIntVolatile(intIndex);
                if (isBitNotSet(currentValue, bitPosition)) {
                    return;
                }
                updatedValue = clearBit(currentValue, bitPosition);
            } while (!buffer.compareAndSetInt(intIndex, currentValue, updatedValue));
            LOGGER.info("Released id value {} for pool {}", idValue, name);
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            LOGGER.info("Closing unique id pool {}. Acquired id values: {}", name, acquiredIdValues());
            region.close();
        }
    }

    private List<Integer> acquiredIdValues() {
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < maxValues; i++) {
            int intIndex = i / Integer.SIZE;
            int bitPosition = i & (Integer.SIZE - 1);

            int intValue = buffer.getIntVolatile(intIndex);
            if (!isBitNotSet(intValue, bitPosition)) {
                values.add(i);
            }
        }
        return values;
    }

    private static FileInitialiser fileInitialiser(int regionSize) {
        return (fileName, fileChannel) -> {
            try {
                FileLock fileLock = acquireLock(fileChannel);
                try {
                    if (fileChannel.size() == 0) {
                        fileChannel.transferFrom(InitialBytes.ZERO, 0, regionSize);
                        fileChannel.force(true);
                        LOGGER.info("Initialised file data in {} for unique id pool", fileName);
                    }
                } finally {
                    fileLock.release();
                }
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Failed to initialise an unique id pool file " + fileName, e);
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
