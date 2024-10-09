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
package org.tools4j.mmap.region.impl;

import org.agrona.IoUtil;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.region.api.FileMapper;
import org.tools4j.mmap.region.api.MapMode;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;

public class ExpandableSizeFileMapper implements FileMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExpandableSizeFileMapper.class);
    private static final long FILE_EXTENSION_LOCK = -1L;
    private final File file;
    private final FileInitialiser fileInitialiser;
    private final long maxSize;

    private final ThreadLocal<PerThreadState> perThreadState = ThreadLocal.withInitial(PerThreadState::new);
    private final AtomicLong fileLengthExtensionLatch = new AtomicLong();
    private RandomAccessFile rafFile = null;
    private FileChannel fileChannel = null;
    private boolean closed;

    private static class PerThreadState {
        final AtomicBuffer preTouchBuffer = new UnsafeBuffer();
        long fileLengthCache;
    }


    public ExpandableSizeFileMapper(File file, long maxSize, FileInitialiser fileInitialiser) {
        this.file = Objects.requireNonNull(file);
        this.maxSize = maxSize;
        this.fileInitialiser = Objects.requireNonNull(fileInitialiser);
    }

    public ExpandableSizeFileMapper(String fileName, long maxSize, FileInitialiser fileInitialiser) {
        this(new File(fileName), maxSize, fileInitialiser);
    }

    @Override
    public MapMode mapMode() {
        return MapMode.READ_WRITE;
    }

    @Override
    public long map(long position, int length) {
        if (!init()) {
            return NULL_ADDRESS;
        }

        FileSizeResult result = ensureFileLength(position + length);
        if (result != FileSizeResult.ERROR) {
            long address = IoUtil.map(fileChannel, MapMode.READ_WRITE.getMapMode(), position, length);

            if (result == FileSizeResult.EXTENDED) {
                preTouch(length, address);
            }

            return address;
        }

        return NULL_ADDRESS;
    }

    private void preTouch(final int length, final long address) {
        final AtomicBuffer preTouchBuffer = perThreadState.get().preTouchBuffer;
        preTouchBuffer.wrap(address, length);
        for (int i = 0; i < length; i = i + (int) Constants.REGION_SIZE_GRANULARITY) {
            preTouchBuffer.compareAndSetLong(i, 0L, 0L);
        }
        preTouchBuffer.wrap(0, 0);
    }

    /**
     * Initialisation is expected to be performed in region-mapper thread.
     * @return true if already initialised or the initialisation is succeeded
     */
    boolean init() {
        if (rafFile == null) {
            if (!file.exists()) {
                try {
                    if (!file.createNewFile()) {
                        if (!file.exists()) {
                            LOGGER.error("Could not create new file {}", file);
                            return false;
                        }
                    } else {
                        LOGGER.info("Created new file {}", file);
                    }
                } catch (IOException e) {
                    LOGGER.error("Failed to create new file " + file, e);
                    return false;
                }
            }
            final RandomAccessFile raf;
            try {
                raf = new RandomAccessFile(file, MapMode.READ_WRITE.getRandomAccessMode());
            } catch (FileNotFoundException e) {
                LOGGER.error("Failed to create new random access file " + file, e);
                return false;
            }

            rafFile = Objects.requireNonNull(raf);
            fileChannel = raf.getChannel();
            try {
                fileInitialiser.init(file.getName(), fileChannel);
            } catch (IOException e) {
                LOGGER.error("Failed to initialise fileChannel for " + file, e);
                try {
                    this.fileChannel.close();
                } catch (IOException ex) {
                    LOGGER.error("Failed to close fileChannel after initialisation failure: " + file, e);
                }
                try {
                    this.rafFile.close();
                } catch (IOException ex) {
                    LOGGER.error("Failed to close RAF file after initialisation failure: " + file, e);
                }
                this.rafFile = null;
                this.fileChannel = null;
                return false;
            }
        }
        return true;
    }

    @Override
    public void unmap(final long address, final long position, final int length) {
        assert address > NULL_ADDRESS;
        assert position > NULL_POSITION;
        IoUtil.unmap(fileChannel, address, length);
    }

    private long fileLength() {
        try {
            return rafFile.length();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private long extendFileLength(final long minLength) {
        long curLength = fileLengthExtensionLatch.get();
        while (curLength < minLength) {
            if (curLength != FILE_EXTENSION_LOCK && fileLengthExtensionLatch.compareAndSet(curLength, FILE_EXTENSION_LOCK)) {
                try {
                    rafFile.setLength(minLength);
                    fileLengthExtensionLatch.set(minLength);
                    return minLength;
                } catch (final IOException e) {
                    fileLengthExtensionLatch.set(curLength);
                    LOGGER.error("Could not extend length to " + minLength + " for file " + file, e);
                    return -1;
                }
            }
            curLength = fileLengthExtensionLatch.get();
        }
        return curLength;
    }

    enum FileSizeResult {
        OK,
        ERROR,
        EXTENDED
    }

    FileSizeResult ensureFileLength(final long minLength) {
        final PerThreadState threadState = perThreadState.get();
        if (threadState.fileLengthCache < minLength) {
            final long actualLength = fileLength();
            if (actualLength < minLength) {
                if (minLength > maxSize) {
                    LOGGER.error("Exceeded max file size {}, requested size {} for file {}", maxSize, minLength, file);
                    return FileSizeResult.ERROR;
                }
                final long extendedLength = extendFileLength(minLength);
                if (extendedLength >= 0) {
                    threadState.fileLengthCache = extendedLength;
                    return FileSizeResult.EXTENDED;
                }
                return FileSizeResult.ERROR;
            } else {
                threadState.fileLengthCache = actualLength;
            }
        }
        return FileSizeResult.OK;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (!closed) {
            try {
                if (fileChannel != null) {
                    closed = true;
                    fileChannel.close();
                }
                if (rafFile != null) {
                    closed = true;
                    rafFile.close();
                }
            } catch (final IOException e) {
                LOGGER.warn("Closing expandable-size file mapper caused unexpected exception: file={}", file, e);
            } finally {
                fileChannel = null;
                rafFile = null;
                closed = true;
                LOGGER.info("Closed expandable-size file mapper: file={}", file);
            }
        }
    }

}
