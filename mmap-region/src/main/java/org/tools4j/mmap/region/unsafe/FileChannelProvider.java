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
import org.tools4j.mmap.region.impl.Closeable;
import org.tools4j.mmap.region.impl.FileInitialiser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.impl.Constraints.validateNotClosed;

final class FileChannelProvider implements Closeable {
    ;

    private static final Logger LOGGER = LoggerFactory.getLogger(FileChannelProvider.class);

    private final Logger infoLogger;
    private final File file;
    private final FileInitialiser fileInitialiser;
    private final AccessMode accessMode;
    private final long minFileLength;
    private final AtomicReference<ChannelWithFile> channelWithFile = new AtomicReference<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private record ChannelWithFile(RandomAccessFile randomAccessFile, FileChannel channel) {
        ChannelWithFile {
            requireNonNull(randomAccessFile);
            requireNonNull(channel);
        }
    }

    FileChannelProvider(final FileMapper fileMapper, final File file, final FileInitialiser fileInitialiser) {
        this(fileMapper, file, fileInitialiser, 0);
    }

    FileChannelProvider(final FileMapper fileMapper,
                        final File file,
                        final FileInitialiser fileInitialiser,
                        final long minFileLength) {
        this.infoLogger = LoggerFactory.getLogger(fileMapper.getClass());
        this.file = requireNonNull(file);
        this.fileInitialiser = requireNonNull(fileInitialiser);
        this.accessMode = fileMapper.accessMode();
        this.minFileLength = minFileLength;
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    public boolean closeIfNeeded() {
        if (closed.compareAndSet(false, true)) {
            close(channelWithFile.getAndSet(null));
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        closeIfNeeded();
    }

    public FileChannel getOrNull() {
        final ChannelWithFile cwf = channelWithFile.get();
        return cwf == null ? null : cwf.channel;
    }

    public FileChannel get() {
        final ChannelWithFile cwf = getChannelWithFile();
        return cwf != null ? cwf.channel : null;
    }

    public boolean setSize(final long size) {
        final ChannelWithFile cwf = getChannelWithFile();
        if (cwf != null) {
            try {
                cwf.randomAccessFile.setLength(size);
                return true;
            } catch (final IOException e) {
                LOGGER.error("Failed to set size to {} for file {}", size, file, e);
                return false;
            }
        }
        return false;
    }

    private ChannelWithFile getChannelWithFile() {
        validateNotClosed(this);
        ChannelWithFile cwf = channelWithFile.get();
        if (cwf != null) {
            return cwf;
        }
        return open();
    }

    private ChannelWithFile open() {
        if (accessMode == AccessMode.READ_ONLY && file.length() == 0L) {
            return null;
        }
        if (accessMode != AccessMode.READ_ONLY && !file.exists() && !createNewFile()) {
            return null;
        }
        final RandomAccessFile raf = initRandomAccessFile();
        final FileChannel channel = raf == null ? null : initChannel(raf);
        return channel == null ? null : compareAndSetIfNotClosed(new ChannelWithFile(raf, channel));
    }

    private boolean createNewFile() {
        assert accessMode != AccessMode.READ_ONLY;
        try {
            if (!file.createNewFile()) {
                if (!file.exists()) {
                    LOGGER.error("Could not create new file {}", file);
                    return false;
                }
            } else {
                infoLogger.info("Created new file {}", file);
            }
        } catch (final IOException e) {
            LOGGER.error("Failed to create new file {}", file, e);
            return false;
        }
        return true;
    }

    private RandomAccessFile initRandomAccessFile() {
        final RandomAccessFile raf;
        try {
            raf = new RandomAccessFile(file, accessMode.getRandomAccessMode());
        } catch (final FileNotFoundException e) {
            LOGGER.error("Failed to create new random access file: {}", file, e);
            return null;
        }
        if (accessMode != AccessMode.READ_ONLY && minFileLength > 0) {
            try {
                if (raf.length() < minFileLength) {
                    raf.setLength(minFileLength);
                }
            } catch (final IOException e) {
                LOGGER.error("Failed to set min file length {} for file: {}", minFileLength, file, e);
                return null;
            }
        }
        return raf;
    }

    private FileChannel initChannel(final RandomAccessFile raf) {
        final FileChannel channel = raf.getChannel();
        try {
            fileInitialiser.init(file.getName(), channel);
        } catch (final IOException e) {
            close("channel", channel);
            close("raf", raf);
            return null;
        }
        assert channel != null : "channel cannot be null";
        assert channel.isOpen() : "channel must be open";
        return channel;
    }

    private ChannelWithFile compareAndSetIfNotClosed(final ChannelWithFile value) {
        ChannelWithFile result = value;
        final ChannelWithFile witness = channelWithFile.compareAndExchange(null, value);
        if (witness != null) {
            close(value);
            result = witness;
        }
        if (isClosed()) {
            close(result);
            return null;
        }
        return result;
    }

    private void close(final ChannelWithFile value) {
        if (value != null) {
            close("channelWithFile.channel", value.channel);
            close("channelWithFile.randomAccessFile", value.randomAccessFile);
        }
    }

    private void close(final String name, final java.io.Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (final IOException ex) {
                LOGGER.error("Failed to close {} for file : {}", name, file, ex);
            }
        }
    }

    @Override
    public String toString() {
        return "FileChannelProvider"
                + ":file=" + file
                + "|closed=" + closed;
    }
}
