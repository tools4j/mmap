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
import org.tools4j.mmap.core.internal.FileChannelLock;
import org.tools4j.mmap.mapping.api.AccessMode;
import org.tools4j.mmap.mapping.api.FileInitialiser;
import org.tools4j.mmap.mapping.api.PreAccessHook;
import org.tools4j.mmap.mapping.config.ZeroFillMode;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.core.internal.Validations.alreadyClosedException;

final class FileHandle implements Closeable {
    private final Describable owner;
    private final File file;
    private final PreAccessHook preAccessHook;
    private final AccessMode accessMode;
    private final long minFileSize;
    private final ZeroFillAllocator zeroFillAllocator;
    private final LifecycleListener lifecycleListener;
    private final ErrorListener errorListener;
    private final AtomicReference<OpenFile> openFile = new AtomicReference<>();

    /** The open file, its channel to be closed through its lock only, which waits until no lock is held on the file */
    private record OpenFile(RandomAccessFile randomAccessFile, FileChannel channel, FileChannelLock lock) {}

    private static final OpenFile CLOSED = new OpenFile(null, null, null);

    /**
     * Constructor for the handle of the file of a file mapper, opening the file on first use.
     *
     * @param owner             the file mapper owning this handle, the resource passed to listeners
     * @param accessMode        the access mode of the file mapper
     * @param file              the file
     * @param fileInitialiser   the initialiser providing the hook invoked when the file is opened or extended
     * @param minFileSize       the min size to set for a writable file and to expect for a read-only file, or zero
     *                          for none
     * @param zeroFillMode      how new space is filled if the initialiser is {@link FileInitialiser#ZERO}
     * @param lifecycleListener the listener notified when the file is created and when it is opened and closed
     * @param errorListener     the listener notified about errors when closing the file, and warned when new space
     *                          is filled with zeros, not allocated natively as the zero fill mode asks for
     */
    FileHandle(final Describable owner,
               final AccessMode accessMode,
               final File file,
               final FileInitialiser fileInitialiser,
               final long minFileSize,
               final ZeroFillMode zeroFillMode,
               final LifecycleListener lifecycleListener,
               final ErrorListener errorListener) {
        this.owner = requireNonNull(owner);
        this.file = requireNonNull(file);
        this.accessMode = requireNonNull(accessMode);
        this.preAccessHook = requireNonNull(fileInitialiser.hook(accessMode));
        this.minFileSize = minFileSize;
        this.lifecycleListener = requireNonNull(lifecycleListener);
        this.errorListener = requireNonNull(errorListener);
        this.zeroFillAllocator = new ZeroFillAllocator(owner, accessMode, file, fileInitialiser, zeroFillMode,
                errorListener);
    }

    @Override
    public boolean isClosed() {
        return openFile.get() == CLOSED;
    }

    /**
     * Closes this handle, and the file if it was opened.
     *
     * @return true if the file was opened and this call closed it, false if it was never opened or closed already
     */
    public boolean closeIfOpened() {
        final OpenFile witness = openFile.compareAndExchange(null, CLOSED);
        if (witness == null || witness == CLOSED) {
            return false;
        }
        if (witness == openFile.compareAndExchange(witness, CLOSED)) {
            close(witness);
            lifecycleListener.onClosed(owner);
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        closeIfOpened();
    }

    /**
     * Opens the file unless open already, creating it first unless in read-only mode; a read-only file that does not
     * exist or is empty yet stays unopened.
     *
     * @throws IllegalStateException if this handle is closed
     * @throws UncheckedIOException if creating or opening the file fails
     */
    public void ensureOpen() {
        openFile();
    }

    /**
     * Returns the channel of the file, opening the file on first use, and creating it first unless in read-only mode.
     *
     * @return the channel, or null if in read-only mode and the file does not exist or is empty yet
     * @throws IllegalStateException if this handle is closed
     * @throws UncheckedIOException if creating or opening the file fails
     */
    public FileChannel channel() {
        final OpenFile open = openFile();
        return open != null ? open.channel : null;
    }

    /**
     * Extends the writable file to the given size unless it is already that long, initialising the new space under an
     * exclusive file lock.
     *
     * @param size the new file size
     * @return the file size after extending, the given size or the current size if it was larger already, for
     *         instance if another process extended the file further
     * @throws IllegalStateException if this handle is closed
     * @throws UncheckedIOException if extending the file fails
     */
    public long extendTo(final long size) {
        assert accessMode != AccessMode.READ_ONLY;
        final OpenFile open = openFile();
        if (open == null) {
            throw new IllegalStateException("No file channel to extend file " + file + " to size " + size);
        }
        try {
            open.lock.lock();
            try {
                final long currentSize = open.channel.size();
                if (currentSize >= size) {
                    return currentSize;
                }
                fillNewSpace(open, currentSize, size, false);
                ensureSize(open.randomAccessFile, size);
                return size;
            } finally {
                open.lock.unlock();
            }
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to extend file " + file + " to size " + size, e);
        }
    }

    /**
     * Fills the new space of the writable file through the hook, unless the zero fill allocator allocates it natively
     * instead; invoked while holding the exclusive lock.
     */
    private void fillNewSpace(final OpenFile open, final long currentSize, final long size, final boolean opening)
            throws IOException {
        if (zeroFillAllocator.allocate(open.lock, currentSize, size)) {
            return;
        }
        if (opening) {
            preAccessHook.onOpen(file, open.channel, size);
        } else {
            preAccessHook.onExtend(file, open.channel, size);
        }
    }

    /** Extends the file without writing if the initialiser left it shorter than the size, never truncating it */
    private static void ensureSize(final RandomAccessFile raf, final long size) throws IOException {
        if (raf.length() < size) {
            raf.setLength(size);
        }
    }

    /** Returns the open file, opening it unless open already, a closed handle detected by reading CLOSED only */
    private OpenFile openFile() {
        final OpenFile open = openFile.get();
        if (open == CLOSED) {
            throw alreadyClosedException(this);
        }
        return open != null ? open : tryOpen();
    }

    /**
     * Opens the file, creating it first unless in read-only mode.
     *
     * @return the open file, or null if in read-only mode and the file does not exist or is empty
     * @throws IllegalStateException if this handle was closed meanwhile
     * @throws UncheckedIOException if creating or opening the file fails
     */
    private OpenFile tryOpen() {
        if (accessMode == AccessMode.READ_ONLY && file.length() == 0L) {
            return null;
        }
        if (accessMode != AccessMode.READ_ONLY && !file.exists()) {
            createNewFile();
        }
        final RandomAccessFile raf = tryOpenRandomAccessFile();
        if (raf == null) {
            return null;
        }
        final OpenFile open = new OpenFile(raf, raf.getChannel(), createLock(raf));
        initialise(open);
        return publish(open);
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

    private RandomAccessFile tryOpenRandomAccessFile() {
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

    /** Creates the lock of the file's channel, closing the file on failure */
    private FileChannelLock createLock(final RandomAccessFile raf) {
        try {
            return FileChannelLock.create(raf.getChannel());
        } catch (final IOException e) {
            close(raf);
            throw new UncheckedIOException("Failed to create lock for file " + file, e);
        } catch (final RuntimeException e) {
            close(raf);
            throw e;
        }
    }

    /** Initialises the opened file, closing it on failure */
    private void initialise(final OpenFile open) {
        try {
            if (accessMode == AccessMode.READ_ONLY) {
                validateMinFileSize(open.channel);
                preAccessHook.onOpen(file, open.channel, minFileSize);
            } else {
                open.lock.lock();
                try {
                    fillNewSpace(open, open.channel.size(), minFileSize, true);
                    ensureSize(open.randomAccessFile, minFileSize);
                } finally {
                    open.lock.unlock();
                }
            }
        } catch (final IOException e) {
            close(open);
            throw new UncheckedIOException("Failed to initialise file " + file, e);
        } catch (final RuntimeException e) {
            close(open);
            throw e;
        }
        assert open.channel.isOpen() : "channel must be open";
    }

    private void validateMinFileSize(final FileChannel channel) throws IOException {
        final long size = channel.size();
        if (size < minFileSize) {
            throw new IllegalArgumentException("Invalid file, expected file of size at least " + minFileSize +
                    " but found only " + size + ": " + file);
        }
    }

    /**
     * Publishes the open file unless another thread was first, closing it then and returning the winner's value, or
     * throwing if the winner was a close of this handle
     */
    private OpenFile publish(final OpenFile value) {
        final OpenFile witness = openFile.compareAndExchange(null, value);
        if (witness == null) {
            lifecycleListener.onOpened(owner);
            return value;
        }
        close(value);
        if (witness == CLOSED) {
            throw alreadyClosedException(this);
        }
        return witness;
    }

    private void close(final OpenFile value) {
        if (value != null) {
            close(value.lock);
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
        return "FileHandle"
                + ":file=" + file
                + "|closed=" + isClosed();
    }
}
