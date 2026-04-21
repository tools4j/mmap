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

import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;
import static org.tools4j.mmap.region.impl.Constraints.validateNotClosed;

@Unsafe
public class ReadOnlyFileMapper implements FileMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReadOnlyFileMapper.class);

    private final File file;
    private final FileChannelProvider fileChannelProvider;
    private long fileSizeCache;

    public ReadOnlyFileMapper(final File file, final FileInitialiser fileInitialiser) {
        this.file = Objects.requireNonNull(file);
        this.fileChannelProvider = new FileChannelProvider(this, file, fileInitialiser);
    }

    public ReadOnlyFileMapper(final String fileName, final FileInitialiser fileInitialiser) {
        this(new File(fileName), fileInitialiser);
    }

    @Override
    public AccessMode accessMode() {
        return AccessMode.READ_ONLY;
    }

    @Override
    public long map(long position, int length) {
        assert position >= 0;
        assert length >= 0;
        validateNotClosed(this);
        final FileChannel channel = fileChannelProvider.get();
        if (channel == null || !channel.isOpen()) {
            return NULL_ADDRESS;
        }
        try {
            final long end = position + length;
            if (end > fileSizeCache && end > (fileSizeCache = channel.size())) {
                return NULL_ADDRESS;
            }
            return FileChannels.map(channel, AccessMode.READ_ONLY.getMapMode(), position, length);
        } catch (final IOException e) {
            LOGGER.error("Failed to map file {}", file, e);
        }
        return NULL_ADDRESS;
    }

    @Override
    public void unmap(long position, long address, int length) {
        validateNotClosed(this);
        assert address > NULL_ADDRESS;
        assert position > NULL_POSITION;
        final FileChannel channel = fileChannelProvider.getOrNull();
        if (channel != null) {
            FileChannels.unmap(channel, address, length);
        }
    }

    @Override
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
        return "ReadOnlyFileMapper" +
                ":file=" + file +
                "|closed=" + isClosed();
    }
}
