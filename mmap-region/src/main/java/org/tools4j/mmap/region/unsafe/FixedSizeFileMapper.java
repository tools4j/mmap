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
import java.nio.channels.FileChannel;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;

@Unsafe
public class FixedSizeFileMapper implements FileMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(FixedSizeFileMapper.class);
    private final File file;
    private final long fileSize;
    private final AccessMode accessMode;
    private final FileChannelProvider fileChannelProvider;
    private final PreTouchHelper preTouchHelper;

    public FixedSizeFileMapper(final File file, 
                               final long fileSize, 
                               final AccessMode accessMode,
                               final FileInitialiser fileInitialiser) {
        this.file = requireNonNull(file);
        this.fileSize = fileSize;
        this.accessMode = requireNonNull(accessMode);
        this.preTouchHelper = new PreTouchHelper(accessMode);
        this.fileChannelProvider = new FileChannelProvider(this, file, fileInitialiser, fileSize);
        init();
    }

    public FixedSizeFileMapper(final String fileName,
                               final long fileSize,
                               final AccessMode accessMode,
                               final FileInitialiser fileInitialiser) {
        this(new File(fileName), fileSize, accessMode, fileInitialiser);
    }

    private void checkNotClosed() {
        if (isClosed()) {
            throw new IllegalStateException("Fixed-size file mapper is closed");
        }
    }

    public File file() {
        return file;
    }

    public long fileSize() {
        return fileSize;
    }

    @Override
    public AccessMode accessMode() {
        return accessMode;
    }

    @Override
    public long map(final long position, final int length) {
        assert position >= 0;
        assert length >= 0;
        checkNotClosed();
        if (position + length > fileSize) {
            return NULL_ADDRESS;
        }
        final long address = FileChannels.map(fileChannelProvider.get(), accessMode.getMapMode(), position, length);
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

    private void init() {
        final FileChannel channel = fileChannelProvider.get();
        if (channel == null) {
            LOGGER.error("Failed to open file channel for file {}", file);
        }
    }

    public boolean isClosed() {
        return fileChannelProvider.isClosed();
    }

    @Override
    public void close() {
        if (fileChannelProvider.closeIfNeeded()) {
            LOGGER.info("Closed: {}", this);
        }
    }

    @Override
    public String toString() {
        return "FixedSizeFileMapper" +
                ":fileSize=" + fileSize +
                "|accessMode=" + accessMode +
                "|file=" + file +
                "|closed=" + isClosed();
    }
}
