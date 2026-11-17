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
package org.tools4j.mmap.core.internal;

import org.tools4j.mmap.core.idle.Idler;
import org.tools4j.mmap.core.unsafe.UnsafeAccess;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;

/**
 * Exclusive and shared locks on the whole file of a file channel, excluding other processes as well as other threads
 * of this JVM, waiting until a lock is available or a timeout occurs. Waiting backs off with {@link Idler#backoff()},
 * spinning and yielding first, so that the usual lock held for microseconds is taken right after its release, then
 * parking up to a millisecond, for a lock held longer, for instance while another process initialises the file.
 * <p>
 * Locking and unlocking allocate nothing, unlike {@link FileChannel#tryLock(long, long, boolean)}, which creates a
 * {@code FileLock} and entries in the JDK's lock table on every call: the OS lock is taken through the JDK's internal
 * file dispatcher, the same one {@code tryLock} uses, so it excludes processes locking the file through the JDK API.
 * Instances are created once per opened channel, allocating a small state shared by all channels of this JVM on the
 * same file, looked up by the file's device and inode.
 * <p>
 * The shared state is needed because OS locks belong to the process on POSIX systems: threads of the same process
 * never exclude each other through them, the first release by any thread releases the lock for all, and closing
 * <i>any</i> descriptor of the file releases all locks of the process on it. Hence the OS lock is taken by the first
 * holder in this JVM and released by the last one, and {@link #close()} closes the channel only while no channel of
 * this JVM holds a lock on the file.
 * <p>
 * All locks on the file within this JVM must be taken through instances of this class; a lock taken through the JDK
 * API in the same JVM is not excluded.
 */
public final class FileChannelLock implements Closeable {
    static final long DEFAULT_MAX_WAIT_MILLIS = 5_000;
    private static final Idler BACKOFF = Idler.backoff();
    private static final AtomicReference<FileState[]> FILE_STATES = new AtomicReference<>(new FileState[0]);

    private final FileChannel channel;
    private final FileDescriptor fd;
    private final Object dispatcher;
    private final FileState fileState;
    private final AtomicBoolean closed = new AtomicBoolean();

    private FileChannelLock(final FileChannel channel) throws IOException {
        this.channel = requireNonNull(channel);
        this.fd = Internals.fileDescriptor(channel);
        this.dispatcher = Internals.dispatcher(channel);
        this.fileState = FileState.register(Internals.fileKey(fd));
    }

    /**
     * Creates the lock for the given channel, allocating it and, unless another channel of this JVM on the same file
     * exists, the state shared by all of them; invoke once when the channel is opened.
     *
     * @param channel the open file channel, to be closed through {@link #close()} only
     * @return a new lock for the channel
     * @throws IOException if the file cannot be identified
     */
    public static FileChannelLock create(final FileChannel channel) throws IOException {
        return new FileChannelLock(channel);
    }

    /**
     * Acquires an exclusive lock for the whole file, which requires a channel open for writing; released with
     * {@link #unlock()}.
     *
     * @throws IllegalStateException if the lock could not be acquired in time, acquiring it failed, or the thread was
     *                               interrupted while waiting, keeping its interrupt status
     */
    public void lock() {
        lock(false, DEFAULT_MAX_WAIT_MILLIS);
    }

    /**
     * Acquires a shared lock for the whole file, which requires a channel open for reading; released with
     * {@link #unlock()}.
     *
     * @throws IllegalStateException if the lock could not be acquired in time, acquiring it failed, or the thread was
     *                               interrupted while waiting, keeping its interrupt status
     */
    public void lockShared() {
        lock(true, DEFAULT_MAX_WAIT_MILLIS);
    }

    void lock(final boolean shared, final long maxWaitMillis) {
        final long endTime = System.currentTimeMillis() + maxWaitMillis;
        int idleCount = 0;
        while (true) {
            final int holders = fileState.holders.get();
            if (holders == FileState.ACQUIRING || holders == FileState.RELEASING) {
                Thread.onSpinWait();
                continue;
            }
            if (shared && holders > 0) {
                if (fileState.holders.compareAndSet(holders, holders + 1)) {
                    return;
                }
                continue;
            }
            if (holders == FileState.FREE && fileState.holders.compareAndSet(FileState.FREE, FileState.ACQUIRING)
                    && tryLockFile(shared)) {
                return;
            }
            if (System.currentTimeMillis() >= endTime) {
                throw new IllegalStateException("Timeout while waiting for file lock for " + channel);
            }
            idleCount = BACKOFF.idle(idleCount);
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for file lock for " + channel);
            }
        }
    }

    /** Takes the OS lock while the file state is ACQUIRING, leaving the state locked if successful, else free */
    private boolean tryLockFile(final boolean shared) {
        boolean locked = false;
        try {
            final int result = Internals.lock(dispatcher, fd, shared);
            locked = result == Internals.LOCKED || result == Internals.RET_EX_LOCK;
        } catch (final IOException e) {
            throw new IllegalStateException("Unexpected exception while attempting to acquire file lock for " +
                    channel, e);
        } finally {
            if (locked) {
                fileState.owner = this;
                fileState.holders.set(shared ? 1 : FileState.EXCLUSIVE);
            } else {
                fileState.holders.set(FileState.FREE);
            }
        }
        return locked;
    }

    /**
     * Releases the exclusive or shared lock acquired by this thread through {@link #lock()} or
     * {@link #lockShared()}, through this or another channel of this JVM on the same file.
     *
     * @throws IllegalStateException if releasing the lock failed
     */
    public void unlock() {
        while (true) {
            final int holders = fileState.holders.get();
            assert holders != FileState.FREE && holders != FileState.CLOSING : "file is not locked";
            if (holders > 1) {
                if (fileState.holders.compareAndSet(holders, holders - 1)) {
                    return;
                }
            } else if ((holders == 1 || holders == FileState.EXCLUSIVE) &&
                    fileState.holders.compareAndSet(holders, FileState.RELEASING)) {
                unlockFile();
                return;
            } else {
                Thread.onSpinWait();
            }
        }
    }

    /** Releases the OS lock through the owner, the channel that took it, while the file state is RELEASING */
    private void unlockFile() {
        final FileChannelLock owner = fileState.owner;
        fileState.owner = null;
        try {
            Internals.release(owner.dispatcher, owner.fd);
        } catch (final IOException e) {
            throw new IllegalStateException("Unexpected exception while releasing file lock for " + channel, e);
        } finally {
            fileState.holders.set(FileState.FREE);
        }
    }

    /**
     * Returns true once {@link #close()} was invoked, the channel closed then or, while a channel of this JVM holds a
     * lock on the file, as soon as it is released.
     *
     * @return true if closed
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Closes the channel unless closed already, once no channel of this JVM holds a lock on the file, waiting for it
     * without timeout if needed; locks are held only briefly, by the code between {@code lock} and {@code unlock}, so
     * that waiting is bounded by their release. Thread-safe: only the first invocation closes the channel, others
     * return right away, without waiting.
     *
     * @throws IOException if closing the channel fails
     */
    @Override
    public void close() throws IOException {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        int holders;
        int idleCount = 0;
        while ((holders = fileState.holders.compareAndExchange(FileState.FREE, FileState.CLOSING)) != FileState.FREE) {
            if (holders > 0 || holders == FileState.EXCLUSIVE) {
                idleCount = BACKOFF.idle(idleCount);
            } else {
                Thread.onSpinWait();
            }
        }
        try {
            channel.close();
        } finally {
            fileState.holders.set(FileState.FREE);
            fileState.unregister();
        }
    }

    @Override
    public String toString() {
        return "FileChannelLock:channel=" + channel + "|closed=" + isClosed();
    }

    /**
     * The lock state of a file shared by all channels of this JVM on the file; {@link #holders} is the only
     * synchronisation point, {@link #owner} is a plain field written before and read after transitions of it.
     */
    private static final class FileState {
        static final int FREE = 0;
        static final int EXCLUSIVE = -1;
        static final int ACQUIRING = -2;
        static final int RELEASING = -3;
        static final int CLOSING = -4;

        final Object fileKey;
        /** FREE, the number of shared holders if positive, or EXCLUSIVE, ACQUIRING, RELEASING or CLOSING */
        final AtomicInteger holders = new AtomicInteger(FREE);
        /** The number of channels using this state, zero once unregistered, never to be used again */
        final AtomicInteger channels = new AtomicInteger(1);
        /** The channel that took the OS lock, needed to release it through the same descriptor on Windows */
        FileChannelLock owner;

        FileState(final Object fileKey) {
            this.fileKey = requireNonNull(fileKey);
        }

        /** Returns the registered state of the file, registering a new one unless one exists, lock free */
        static FileState register(final Object fileKey) {
            FileState created = null;
            while (true) {
                final FileState[] states = FILE_STATES.get();
                for (final FileState state : states) {
                    if (state.fileKey.equals(fileKey) && state.retain()) {
                        return state;
                    }
                }
                if (created == null) {
                    created = new FileState(fileKey);
                }
                final FileState[] next = Arrays.copyOf(states, states.length + 1);
                next[states.length] = created;
                if (FILE_STATES.compareAndSet(states, next)) {
                    return created;
                }
            }
        }

        private boolean retain() {
            int count;
            do {
                count = channels.get();
                if (count == 0) {
                    return false;
                }
            } while (!channels.compareAndSet(count, count + 1));
            return true;
        }

        void unregister() {
            if (channels.decrementAndGet() > 0) {
                return;
            }
            while (true) {
                final FileState[] states = FILE_STATES.get();
                final int index = indexOf(states, this);
                if (index < 0) {
                    return;
                }
                final FileState[] next = new FileState[states.length - 1];
                System.arraycopy(states, 0, next, 0, index);
                System.arraycopy(states, index + 1, next, index, next.length - index);
                if (FILE_STATES.compareAndSet(states, next)) {
                    return;
                }
            }
        }

        private static int indexOf(final FileState[] states, final FileState state) {
            for (int i = 0; i < states.length; i++) {
                if (states[i] == state) {
                    return i;
                }
            }
            return -1;
        }
    }

    /**
     * Method handles into the JDK's internal file channel, file dispatcher and file key, through the trusted lookup,
     * typed with public types only, so that they are invoked exactly, without boxing or allocation.
     */
    private static final class Internals {
        static final int LOCKED = 0;
        static final int RET_EX_LOCK = 1;
        private static final MethodHandle GET_FILE_DESCRIPTOR;
        private static final MethodHandle GET_DISPATCHER;
        private static final MethodHandle LOCK;
        private static final MethodHandle RELEASE;
        private static final MethodHandle CREATE_FILE_KEY;
        static {
            try {
                final MethodHandles.Lookup lookup = UnsafeAccess.trustedLookup();
                final Class<?> fileChannelClass = Class.forName("sun.nio.ch.FileChannelImpl");
                final Class<?> dispatcherClass = Class.forName("sun.nio.ch.FileDispatcher");
                final Class<?> fileKeyClass = Class.forName("sun.nio.ch.FileKey");
                GET_FILE_DESCRIPTOR = lookup.unreflectGetter(fileChannelClass.getDeclaredField("fd"))
                        .asType(MethodType.methodType(FileDescriptor.class, FileChannel.class));
                //static since Java 21, an instance field of the channel before
                final Field dispatcherField = fileChannelClass.getDeclaredField("nd");
                final MethodHandle dispatcherGetter = lookup.unreflectGetter(dispatcherField);
                GET_DISPATCHER = Modifier.isStatic(dispatcherField.getModifiers())
                        ? MethodHandles.dropArguments(dispatcherGetter.asType(MethodType.methodType(Object.class)), 0,
                                FileChannel.class)
                        : dispatcherGetter.asType(MethodType.methodType(Object.class, FileChannel.class));
                LOCK = lookup.findVirtual(dispatcherClass, "lock", MethodType.methodType(int.class,
                                FileDescriptor.class, boolean.class, long.class, long.class, boolean.class))
                        .asType(MethodType.methodType(int.class, Object.class, FileDescriptor.class, boolean.class,
                                long.class, long.class, boolean.class));
                RELEASE = lookup.findVirtual(dispatcherClass, "release", MethodType.methodType(void.class,
                                FileDescriptor.class, long.class, long.class))
                        .asType(MethodType.methodType(void.class, Object.class, FileDescriptor.class, long.class,
                                long.class));
                CREATE_FILE_KEY = lookup.findStatic(fileKeyClass, "create",
                                MethodType.methodType(fileKeyClass, FileDescriptor.class))
                        .asType(MethodType.methodType(Object.class, FileDescriptor.class));
            } catch (final ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to access the JDK's file channel internals", e);
            }
        }

        static FileDescriptor fileDescriptor(final FileChannel channel) {
            try {
                return (FileDescriptor) GET_FILE_DESCRIPTOR.invokeExact(channel);
            } catch (final Throwable e) {
                throw new IllegalStateException("Failed to get the file descriptor of " + channel, e);
            }
        }

        static Object dispatcher(final FileChannel channel) {
            try {
                return (Object) GET_DISPATCHER.invokeExact(channel);
            } catch (final Throwable e) {
                throw new IllegalStateException("Failed to get the file dispatcher of " + channel, e);
            }
        }

        static Object fileKey(final FileDescriptor fd) throws IOException {
            try {
                return (Object) CREATE_FILE_KEY.invokeExact(fd);
            } catch (final IOException | RuntimeException | Error e) {
                throw e;
            } catch (final Throwable e) {
                throw new IllegalStateException("Failed to identify file of " + fd, e);
            }
        }

        /**
         * Tries to lock the whole file without blocking: NO_LOCK if held by another process, else LOCKED, or
         * RET_EX_LOCK if an exclusive lock was taken instead of a shared one.
         */
        static int lock(final Object dispatcher, final FileDescriptor fd, final boolean shared) throws IOException {
            try {
                return (int) LOCK.invokeExact(dispatcher, fd, false, 0L, Long.MAX_VALUE, shared);
            } catch (final IOException | RuntimeException | Error e) {
                throw e;
            } catch (final Throwable e) {
                throw new IllegalStateException("Failed to lock file of " + fd, e);
            }
        }

        static void release(final Object dispatcher, final FileDescriptor fd) throws IOException {
            try {
                RELEASE.invokeExact(dispatcher, fd, 0L, Long.MAX_VALUE);
            } catch (final IOException | RuntimeException | Error e) {
                throw e;
            } catch (final Throwable e) {
                throw new IllegalStateException("Failed to release lock on file of " + fd, e);
            }
        }
    }
}
