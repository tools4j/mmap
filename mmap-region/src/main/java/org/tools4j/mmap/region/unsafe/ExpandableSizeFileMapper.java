/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2025 tools4j.org (Marco Terzer, Anton Anufriev)
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.region.api.AccessMode;
import org.tools4j.mmap.region.api.Unsafe;
import org.tools4j.mmap.region.impl.FileInitialiser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;

@Unsafe
public class ExpandableSizeFileMapper implements FileMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExpandableSizeFileMapper.class);
    private final File file;
    private final long maxSize;
    private final FileInitialiser fileInitialiser;
    private final PreTouchHelper preTouchHelper;
    private final AtomicLong fileLengthCache = new AtomicLong();
    private final AtomicBoolean fileSizeExtensionLatch = new AtomicBoolean();
    private RandomAccessFile rafFile = null;
    private FileChannel fileChannel = null;
    private boolean closed;

    public ExpandableSizeFileMapper(final File file, final long maxSize, final FileInitialiser fileInitialiser) {
        this.file = Objects.requireNonNull(file);
        this.maxSize = maxSize;
        this.fileInitialiser = Objects.requireNonNull(fileInitialiser);
        this.preTouchHelper = new PreTouchHelper(AccessMode.READ_WRITE);
    }

    @Override
    public AccessMode accessMode() {
        return AccessMode.READ_WRITE;
    }

    @Override
    public long map(long position, int length) {
        checkNotClosed();
        if (!init() || position < 0 || length < 0 || position + length > maxSize) {
            return NULL_ADDRESS;
        }
        ensureFileLength(position + length);
        final long address = IoUtil.map(fileChannel, AccessMode.READ_WRITE.getMapMode(), position, length);
        preTouchHelper.preTouch(position, length, address);
        return address;
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
                raf = new RandomAccessFile(file, AccessMode.READ_WRITE.getRandomAccessMode());
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
        checkNotClosed();
        assert address > NULL_ADDRESS;
        assert position > NULL_POSITION;
        IoUtil.unmap(fileChannel, address, length);
    }

    void ensureFileLength(final long minLength) {
        long cachedFileLength = fileLengthCache.get();
        if (minLength <= cachedFileLength) {
            return;
        }
        if (minLength > maxSize) {
            throw new IllegalStateException("Exceeded max file size " + maxSize + " for file " + file);
        }
        do {
            final long fileLength = fileLength();
            if (fileLength > cachedFileLength) {
                cachedFileLength = fileLengthCache.accumulateAndGet(fileLength, Math::max);
            }
            if (cachedFileLength < minLength) {
                final long extendedLength = tryExtendFile(fileLength, minLength);
                if (extendedLength > cachedFileLength) {
                    cachedFileLength = fileLengthCache.accumulateAndGet(fileLength, Math::max);
                }
            }
        } while (cachedFileLength < minLength);
    }

    private long tryExtendFile(final long fileLength, final long minLength) {
        if (!fileSizeExtensionLatch.compareAndSet(false, true)) {
            return fileLength;
        }
        try {
            final long newestFileLength = rafFile.length();
            if (newestFileLength < minLength) {
                rafFile.setLength(minLength);
                return minLength;
            } else {
                return newestFileLength;
            }
        } catch (final IOException e) {
            throw new IllegalStateException("Extending file " + file + " to size " + minLength +
                    " failed, e=" + e, e);
        } finally {
            fileSizeExtensionLatch.set(false);
        }
    }

    private long fileLength() {
        try {
            return rafFile.length();
        } catch (final IOException e) {
            throw new IllegalStateException("Reading the length of file " + fileChannel + " failed, e=" + e, e);
        }
    }

    private void checkNotClosed() {
        if (isClosed()) {
            throw new IllegalStateException("Expandable-size file mapper is closed");
        }
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
                preTouchHelper.reset();
                LOGGER.info("Closed expandable-size file mapper: file={}", file);
            }
        }
    }

    @Override
    public String toString() {
        return "ExpandableSizeFileMapper" +
                ":maxSize=" + maxSize +
                "|file=" + file +
                "|closed=" + isClosed();
    }
}
