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
package org.tools4j.mmap.region.unsafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.region.api.AccessMode;
import org.tools4j.mmap.region.api.Unsafe;
import org.tools4j.mmap.region.impl.FileInitialiser;

import java.io.File;
import java.io.IOException;
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
    private final FileChannelProvider fileChannelProvider;
    private final PreTouchHelper preTouchHelper;
    private final AtomicLong fileLengthCache = new AtomicLong();
    private final AtomicBoolean fileSizeExtensionLatch = new AtomicBoolean();

    public ExpandableSizeFileMapper(final File file, final long maxSize, final FileInitialiser fileInitialiser) {
        this.file = Objects.requireNonNull(file);
        this.maxSize = maxSize;
        this.fileChannelProvider = new FileChannelProvider(this, file, fileInitialiser);
        this.preTouchHelper = new PreTouchHelper(AccessMode.READ_WRITE);
    }

    @Override
    public AccessMode accessMode() {
        return AccessMode.READ_WRITE;
    }

    @Override
    public long map(final long position, final int length) {
        assert position >= 0;
        assert length >= 0;
        checkNotClosed();
        if (position + length > maxSize) {
            return NULL_ADDRESS;
        }
        final FileChannel channel = fileChannelProvider.get();
        if (channel == null || !channel.isOpen()) {
            return NULL_ADDRESS;
        }
        ensureFileLength(channel, position + length);
        final long address = FileChannels.map(channel, AccessMode.READ_WRITE.getMapMode(), position, length);
        preTouchHelper.preTouch(position, length, address);
        return address;
    }

    @Override
    public void unmap(final long position, final long address, final int length) {
        checkNotClosed();
        assert address > NULL_ADDRESS;
        assert position > NULL_POSITION;
        final FileChannel channel = fileChannelProvider.getOrNull();
        if (channel != null) {
            FileChannels.unmap(channel, address, length);
        }
    }

    void ensureFileLength(final FileChannel channel, final long minLength) {
        long cachedFileLength = fileLengthCache.get();
        if (minLength <= cachedFileLength) {
            return;
        }
        if (minLength > maxSize) {
            throw new IllegalStateException("Exceeded max file size " + maxSize + " for file " + file);
        }
        do {
            final long fileLength = fileLength(channel);
            if (fileLength > cachedFileLength) {
                cachedFileLength = fileLengthCache.accumulateAndGet(fileLength, Math::max);
            }
            if (cachedFileLength < minLength) {
                final long extendedLength = tryExtendFile(channel, fileLength, minLength);
                if (extendedLength > cachedFileLength) {
                    cachedFileLength = fileLengthCache.accumulateAndGet(fileLength, Math::max);
                }
            }
        } while (cachedFileLength < minLength);
    }

    private long tryExtendFile(final FileChannel channel, final long fileLength, final long minLength) {
        if (!fileSizeExtensionLatch.compareAndSet(false, true)) {
            return fileLength;
        }
        final FileChannel fileChannel = fileChannelProvider.getOrNull();
        if (fileChannel == null) {
            return fileLength;
        }
        try {
            final long newestFileLength = channel.size();
            if (newestFileLength < minLength) {
                return fileChannelProvider.setSize(minLength) ? minLength : channel.size();
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

    private long fileLength(final FileChannel channel) {
        try {
            return channel.size();
        } catch (final IOException e) {
            throw new IllegalStateException("Reading the length of file " + file + " failed, e=" + e, e);
        }
    }

    private void checkNotClosed() {
        if (isClosed()) {
            throw new IllegalStateException("Expandable-size file mapper is closed");
        }
    }

    @Override
    public boolean isClosed() {
        return fileChannelProvider.isClosed();
    }

    @Override
    public void close() {
        if (fileChannelProvider.closeIfNeeded()) {
            preTouchHelper.reset();
            LOGGER.info("Closed: {}", this);
        }
    }

    @Override
    public String toString() {
        return "ExpandableSizeFileMapper" +
                ":maxSize=" + maxSize +
                "|file=" + file +
                "|size=" + file.length() +
                "|closed=" + isClosed();
    }
}
