/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2023 tools4j.org (Marco Terzer, Anton Anufriev)
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
import org.agrona.collections.MutableLong;
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

public class SingleFileReadWriteFileMapper implements FileMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(SingleFileReadWriteFileMapper.class);
    private static final MapMode MAP_MODE = MapMode.READ_WRITE;
    private final File file;
    private final FileInitialiser fileInitialiser;
    private final long maxSize;
    private final AtomicBuffer preTouchBuffer = new UnsafeBuffer();

    private RandomAccessFile rafFile = null;
    private FileChannel fileChannel = null;

    private final MutableLong fileSizeCache = new MutableLong(0);

    public SingleFileReadWriteFileMapper(File file, long maxSize, FileInitialiser fileInitialiser) {
        this.file = Objects.requireNonNull(file);
        this.maxSize = maxSize;
        this.fileInitialiser = Objects.requireNonNull(fileInitialiser);
    }

    public SingleFileReadWriteFileMapper(String fileName, long maxSize, FileInitialiser fileInitialiser) {
        this(new File(fileName), maxSize, fileInitialiser);
    }

    @Override
    public long map(long position, int length) {
        if (!init()) {
            return NULL_ADDRESS;
        }

        FileSizeResult result = ensureFileSize(position + length);
        if (result != FileSizeResult.ERROR) {
            long address = IoUtil.map(fileChannel, MAP_MODE.getMapMode(), position, length);

            if (result == FileSizeResult.EXTENDED) {
                preTouch(length, address);
            }

            return address;
        }

        return NULL_ADDRESS;
    }

    private void preTouch(int length, long address) {
        preTouchBuffer.wrap(address, length);
        for (int i = 0; i < length; i = i + (int) Constants.REGION_SIZE_GRANULARITY) {
            //preTouchBuffer.putInt(i, 0);
            //preTouchBuffer.putByte(i, (byte)0);
            preTouchBuffer.compareAndSetLong(i, 0L, 0L);
        }
        preTouchBuffer.wrap(0, 0);
    }

    /**
     * Initialisation is expected to be performed in region-mapper thread.
     * @return true if already initialised or the initialisation is succeeded
     */
    private boolean init() {
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
                raf = new RandomAccessFile(file, MAP_MODE.getRandomAccessMode());
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
    public void unmap(long address, long position, int length) {
        IoUtil.unmap(fileChannel, address, length);
    }

    private long fileLength() {
        try {
            return rafFile.length();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean extendFileLength(final long length) {
        try {
            rafFile.setLength(length);
            return true;
        } catch (final IOException e) {
            LOGGER.error("Could not extend length to " + length + " for file " + file, e);
            return false;
        }
    }

    enum FileSizeResult {
        OK,
        ERROR,
        EXTENDED
    }

    private FileSizeResult ensureFileSize(long minSize) {
        if (fileSizeCache.get() < minSize) {
            final long len = fileLength();
            if (len < minSize) {
                if (minSize > maxSize) {
                    LOGGER.error("Exceeded max file size {}, requested size {} for file {}", maxSize, minSize, file);
                    return FileSizeResult.ERROR;
                }
                if (extendFileLength(minSize)) {
                    fileSizeCache.set(minSize);
                    return FileSizeResult.EXTENDED;
                }
                return FileSizeResult.ERROR;
            } else {
                fileSizeCache.set(len);
            }
        }
        return FileSizeResult.OK;
    }

    @Override
    public void close() {
        try {
            if (fileChannel != null) {
                fileChannel.close();
            }
            if (rafFile != null) {
                rafFile.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        LOGGER.info("Closed read-write file mapper. file={}", file);
    }

}
