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
package org.tools4j.mmap.region.unsafe;

import org.agrona.IoUtil;
import org.agrona.LangUtil;
import org.agrona.UnsafeAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.region.api.AccessMode;
import org.tools4j.mmap.region.api.Unsafe;
import org.tools4j.mmap.region.impl.FileInitialiser;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;
import static org.tools4j.mmap.region.impl.Constants.REGION_SIZE_GRANULARITY;

@Unsafe
public class FixedSizeFileMapper implements FileMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(FixedSizeFileMapper.class);
    private final File file;
    private final long fileSize;
    private final AccessMode mapMode;
    private final FileInitialiser fileInitialiser;
    private long touchedSize;
    private RandomAccessFile rafFile;
    private FileChannel fileChannel;

    public FixedSizeFileMapper(final File file, 
                               final long fileSize, 
                               final AccessMode mapMode,
                               final FileInitialiser fileInitialiser) {
        this.file = requireNonNull(file);
        this.fileSize = fileSize;
        this.mapMode = requireNonNull(mapMode);
        this.fileInitialiser = requireNonNull(fileInitialiser);
        init();
    }

    public FixedSizeFileMapper(final String fileName,
                               final long fileSize,
                               final AccessMode mapMode,
                               final FileInitialiser fileInitialiser) {
        this(new File(fileName), fileSize, mapMode, fileInitialiser);
    }

    private void checkNotClosed() {
        if (isClosed()) {
            throw new IllegalStateException("Fixed-size file mapper is closed");
        }
    }

    public long fileSize() {
        return fileSize;
    }

    @Override
    public AccessMode mapMode() {
        return mapMode;
    }

    @Override
    public long map(final long position, final int length) {
        checkNotClosed();
        if (position < 0 || position + length > fileSize) {
            return NULL_ADDRESS;
        }
        final long address = IoUtil.map(fileChannel, mapMode.getMapMode(), position, length);
        preTouch(position, length, address);
        return address;
    }

    @Override
    public void unmap(final long address, final long position, final int length) {
        checkNotClosed();
        assert address > NULL_ADDRESS;
        assert position > NULL_POSITION;
        IoUtil.unmap(fileChannel, address, length);
    }

    private void preTouch(final long position, final int length, final long address) {
        if (address == NULL_ADDRESS) {
            return;
        }
        if (position + length <= touchedSize) {
            return;
        }
        final sun.misc.Unsafe unsafe = UnsafeAccess.UNSAFE;
        for (long i = 0; i < length; i += REGION_SIZE_GRANULARITY) {
            unsafe.compareAndSwapLong(null, address + i, 0L, 0L);
        }
        touchedSize = position + length;
    }

    private void init() {
        if (rafFile == null) {
            String action = "open";
            try {
                action = "create";
                if (!file.exists()) {
                    if (!file.createNewFile()) {
                        if (!file.exists()) {
                            throw new IOException("Could not create new file " + file);
                        }
                    } else {
                        LOGGER.info("Created new file: {}", file);
                    }
                }
                action = "open";
                rafFile = new RandomAccessFile(file, mapMode.getRandomAccessMode());
                fileChannel = rafFile.getChannel();
                action = "initialize";
                rafFile.setLength(fileSize);
                fileInitialiser.init(file.getName(), fileChannel);
            } catch (final IOException e) {
                LOGGER.error("Failed to {} file: {}", action, file, e);
                close(false);
                LangUtil.rethrowUnchecked(e);
            }
        }
    }

    public boolean isClosed() {
        return fileChannel == null;
    }

    @Override
    public void close() {
        close(true);
    }

    private void close(final boolean log) {
        boolean closed = false;
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
            LOGGER.warn("Closing fixed-size file mapper caused unexpected exception: file={}", file, e);
        } finally {
            fileChannel = null;
            rafFile = null;
            touchedSize = 0L;
            if (log && closed) {
                LOGGER.info("Closed fixed-size file mapper: file={}", file);
            }
        }
    }

    @Override
    public String toString() {
        return "FixedSizeFileMapper:" +
                "fileSize=" + fileSize +
                "|mapMode=" + mapMode +
                "|file=" + file +
                "|closed=" + isClosed();
    }
}
