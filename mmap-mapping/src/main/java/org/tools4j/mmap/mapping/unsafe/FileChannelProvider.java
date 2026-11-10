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
package org.tools4j.mmap.mapping.unsafe;

import org.tools4j.mmap.core.base.Closeable;
import org.tools4j.mmap.core.base.Describable;
import org.tools4j.mmap.core.base.ErrorListener;
import org.tools4j.mmap.core.base.LifecycleListener;
import org.tools4j.mmap.core.internal.FileLocks;
import org.tools4j.mmap.mapping.api.AccessMode;
import org.tools4j.mmap.mapping.api.FileInitialiser;
import org.tools4j.mmap.mapping.api.PreAccessHook;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.core.internal.Validations.validateNotClosed;

final class FileChannelProvider implements Closeable {
    private final Describable owner;
    private final File file;
    private final PreAccessHook preAccessHook;
    private final AccessMode accessMode;
    private final long minFileLength;
    private final LifecycleListener lifecycleListener;
    private final ErrorListener errorListener;
    private final AtomicReference<ChannelWithFile> channelWithFile = new AtomicReference<>();

    private record ChannelWithFile(RandomAccessFile randomAccessFile, FileChannel channel) {
        static final ChannelWithFile CLOSED = new ChannelWithFile(null, null);
        ChannelWithFile {
            if (CLOSED != null) {
                requireNonNull(randomAccessFile);
                requireNonNull(channel);
            }
        }
    }

    /**
     * Constructor for a provider of the file channel of a file mapper.
     *
     * @param owner             the file mapper owning this provider, the resource passed to listeners
     * @param accessMode        the access mode of the file mapper
     * @param file              the file
     * @param fileInitialiser   the initialiser providing the hook invoked when the file is opened or extended
     * @param minFileLength     the min length to set for a writable file and to expect for a read-only file, or zero
     *                          for none
     * @param lifecycleListener the listener notified when the file is created and when it is opened and closed
     * @param errorListener     the listener notified about errors when closing the file
     */
    FileChannelProvider(final Describable owner,
                        final AccessMode accessMode,
                        final File file,
                        final FileInitialiser fileInitialiser,
                        final long minFileLength,
                        final LifecycleListener lifecycleListener,
                        final ErrorListener errorListener) {
        this.owner = requireNonNull(owner);
        this.file = requireNonNull(file);
        this.accessMode = requireNonNull(accessMode);
        this.preAccessHook = requireNonNull(fileInitialiser.hook(accessMode));
        this.minFileLength = minFileLength;
        this.lifecycleListener = requireNonNull(lifecycleListener);
        this.errorListener = requireNonNull(errorListener);
    }

    @Override
    public boolean isClosed() {
        return channelWithFile.get() == ChannelWithFile.CLOSED;
    }

    public boolean closeIfNeeded() {
        ChannelWithFile witness = channelWithFile.compareAndExchange(null, ChannelWithFile.CLOSED);
        if (witness == null || witness == ChannelWithFile.CLOSED) {
            return false;
        }
        if (witness == channelWithFile.compareAndExchange(witness, ChannelWithFile.CLOSED)) {
            close(witness);
            lifecycleListener.onClosed(owner);
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        closeIfNeeded();
    }

    public FileChannel get() {
        final ChannelWithFile cwf = getChannelWithFile();
        return cwf != null ? cwf.channel : null;
    }

    /**
     * Extends the writable file to the given size unless it is already that long, initialising the new space under an
     * exclusive file lock.
     *
     * @param size the new file size
     * @return the file length after extending, the given size or the current length if it was longer already, for
     *         instance if another process extended the file further
     * @throws UncheckedIOException if extending the file fails
     */
    public long extend(final long size) {
        assert accessMode != AccessMode.READ_ONLY;
        final ChannelWithFile cwf = getChannelWithFile();
        if (cwf == null) {
            throw new IllegalStateException("No file channel to extend file " + file + " to size " + size);
        }
        try {
            final FileLock lock = FileLocks.acquireLock(cwf.channel);
            try {
                final long length = cwf.channel.size();
                if (length >= size) {
                    return length;
                }
                preAccessHook.onExtend(file, cwf.channel, size);
                ensureLength(cwf.randomAccessFile, size);
                return size;
            } finally {
                lock.release();
            }
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to extend file " + file + " to size " + size, e);
        }
    }

    /** Extends the file without writing if the initialiser left it shorter than the size, never truncating it */
    private static void ensureLength(final RandomAccessFile raf, final long size) throws IOException {
        if (raf.length() < size) {
            raf.setLength(size);
        }
    }

    private ChannelWithFile getChannelWithFile() {
        validateNotClosed(this);
        ChannelWithFile cwf = channelWithFile.get();
        if (cwf != null) {
            return cwf;
        }
        return open();
    }

    /**
     * Opens the file, creating it first unless in read-only mode.
     *
     * @return the channel with file, or null if in read-only mode and the file does not exist or is empty
     * @throws UncheckedIOException if creating or opening the file fails
     */
    private ChannelWithFile open() {
        if (accessMode == AccessMode.READ_ONLY && file.length() == 0L) {
            return null;
        }
        if (accessMode != AccessMode.READ_ONLY && !file.exists()) {
            createNewFile();
        }
        final RandomAccessFile raf = initRandomAccessFile();
        if (raf == null) {
            return null;
        }
        final FileChannel channel = initChannel(raf);
        return setOrGet(new ChannelWithFile(raf, channel));
    }

    private void createNewFile() {
        assert accessMode != AccessMode.READ_ONLY;
        try {
            if (file.createNewFile()) {
                lifecycleListener.onCreated(owner);
            } else if (!file.exists()) {
                throw new IOException("File was not created");
            }
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to create new file " + file, e);
        }
    }

    private RandomAccessFile initRandomAccessFile() {
        final RandomAccessFile raf;
        try {
            raf = new RandomAccessFile(file, accessMode.randomAccessMode());
        } catch (final FileNotFoundException e) {
            if (accessMode == AccessMode.READ_ONLY && !file.exists()) {
                return null;
            }
            throw new UncheckedIOException("Failed to open file " + file, e);
        }
        return raf;
    }

    private FileChannel initChannel(final RandomAccessFile raf) {
        final FileChannel channel = raf.getChannel();
        try {
            if (accessMode == AccessMode.READ_ONLY) {
                validateMinFileLength(channel);
                preAccessHook.onOpen(file, channel, minFileLength);
            } else {
                final FileLock lock = FileLocks.acquireLock(channel);
                try {
                    preAccessHook.onOpen(file, channel, minFileLength);
                    ensureLength(raf, minFileLength);
                } finally {
                    lock.release();
                }
            }
        } catch (final IOException e) {
            close(channel);
            close(raf);
            throw new UncheckedIOException("Failed to initialise file " + file, e);
        } catch (final RuntimeException e) {
            close(channel);
            close(raf);
            throw e;
        }
        assert channel != null : "channel cannot be null";
        assert channel.isOpen() : "channel must be open";
        return channel;
    }

    private void validateMinFileLength(final FileChannel channel) throws IOException {
        final long length = channel.size();
        if (length < minFileLength) {
            throw new IllegalArgumentException("Invalid file, expected file of size at least " + minFileLength +
                    " but found only " + length + ": " + file);
        }
    }

    private ChannelWithFile setOrGet(final ChannelWithFile value) {
        final ChannelWithFile witness = channelWithFile.compareAndExchange(null, value);
        if (witness == null) {
            lifecycleListener.onOpened(owner);
            return value;
        }
        close(value);
        return witness;
    }

    private void close(final ChannelWithFile value) {
        if (value != null) {
            close(value.channel);
            close(value.randomAccessFile);
        }
    }

    private void close(final java.io.Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (final IOException ex) {
                errorListener.onError(owner, "Failed to close file", ex);
            }
        }
    }

    @Override
    public String toString() {
        return "FileChannelProvider"
                + ":file=" + file
                + "|closed=" + isClosed();
    }
}
