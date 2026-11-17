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

import org.tools4j.mmap.core.unsafe.Unsafe;
import org.tools4j.mmap.core.unsafe.UnsafeAccess;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

/**
 * Maps, unmaps and locks files without allocating, through the JDK's internal file channel implementation and its
 * file dispatcher, reached through the trusted lookup; mapping goes through the file dispatcher on Java 21 or later,
 * and through the file channel implementation on Java 17. All internals are reached through method handles typed with
 * public types only, so that they are invoked exactly, without boxing or allocation.
 * <p>
 * Locking is package-private: all locks on our files are taken through {@link FileChannelLock}, which needs to see
 * them all, since OS locks belong to the process; mapping and unmapping are public, for the file mappers.
 */
@Unsafe
public enum FileChannels {
    ;
    /** Result of {@link #lock(Object, FileDescriptor, boolean)}: locked as requested */
    static final int LOCKED = 0;
    /** Result of {@link #lock(Object, FileDescriptor, boolean)}: locked exclusively, though a shared lock was requested */
    static final int RET_EX_LOCK = 1;

    private static final int MAP_READ_ONLY = 0;
    private static final int MAP_READ_WRITE = 1;
    private static final int MAP_PRIVATE = 2;

    /**
     * Map a range of a file and return the address at which the range begins.
     *
     * @param fileChannel to be mapped.
     * @param mode        for the mapped region.
     * @param offset      within the file the mapped region should start.
     * @param length      of the mapped region.
     * @return the address at which the mapping starts.
     * @throws UncheckedIOException if mapping fails with an I/O error
     */
    public static long map(final FileChannel fileChannel,
                           final MapMode mode,
                           final long offset,
                           final long length) {
        try {
            return (long) Internals.MAP.invokeExact(fileChannel, protection(mode), offset, length, false);
        } catch (final IOException ex) {
            throw new UncheckedIOException("Failed to map " + length + " bytes at offset " + offset + " with mode " +
                    mode, ex);
        } catch (final RuntimeException | Error ex) {
            throw ex;
        } catch (final Throwable ex) {
            throw new IllegalStateException("Failed to map " + length + " bytes at offset " + offset + " with mode " +
                    mode, ex);
        }
    }

    /**
     * Unmap a region of a file.
     *
     * @param address     at which the mapping begins.
     * @param length      of the mapped region.
     * @throws UncheckedIOException if unmapping fails with an I/O error
     */
    public static void unmap(final long address, final long length) {
        try {
            Internals.UNMAP.invokeExact(address, length);
        } catch (final IOException ex) {
            throw new UncheckedIOException("Failed to unmap " + length + " bytes at address " + address, ex);
        } catch (final RuntimeException | Error ex) {
            throw ex;
        } catch (final Throwable ex) {
            throw new IllegalStateException("Failed to unmap " + length + " bytes at address " + address, ex);
        }
    }

    private static int protection(final MapMode mode) {
        if (mode == MapMode.READ_ONLY) {
            return MAP_READ_ONLY;
        }
        if (mode == MapMode.READ_WRITE) {
            return MAP_READ_WRITE;
        }
        return MAP_PRIVATE;
    }

    static FileDescriptor fileDescriptor(final FileChannel channel) {
        try {
            return (FileDescriptor) Internals.GET_FILE_DESCRIPTOR.invokeExact(channel);
        } catch (final Throwable e) {
            throw new IllegalStateException("Failed to get the file descriptor of " + channel, e);
        }
    }

    static Object dispatcher(final FileChannel channel) {
        try {
            return (Object) Internals.GET_DISPATCHER.invokeExact(channel);
        } catch (final Throwable e) {
            throw new IllegalStateException("Failed to get the file dispatcher of " + channel, e);
        }
    }

    static Object fileKey(final FileDescriptor fd) throws IOException {
        try {
            return (Object) Internals.CREATE_FILE_KEY.invokeExact(fd);
        } catch (final IOException | RuntimeException | Error e) {
            throw e;
        } catch (final Throwable e) {
            throw new IllegalStateException("Failed to identify file of " + fd, e);
        }
    }

    /**
     * Tries to lock the whole file without blocking: NO_LOCK if held by another process, else {@link #LOCKED}, or
     * {@link #RET_EX_LOCK} if an exclusive lock was taken instead of a shared one.
     */
    static int lock(final Object dispatcher, final FileDescriptor fd, final boolean shared) throws IOException {
        try {
            return (int) Internals.LOCK.invokeExact(dispatcher, fd, false, 0L, Long.MAX_VALUE, shared);
        } catch (final IOException | RuntimeException | Error e) {
            throw e;
        } catch (final Throwable e) {
            throw new IllegalStateException("Failed to lock file of " + fd, e);
        }
    }

    static void release(final Object dispatcher, final FileDescriptor fd) throws IOException {
        try {
            Internals.RELEASE.invokeExact(dispatcher, fd, 0L, Long.MAX_VALUE);
        } catch (final IOException | RuntimeException | Error e) {
            throw e;
        } catch (final Throwable e) {
            throw new IllegalStateException("Failed to release lock on file of " + fd, e);
        }
    }

    //TODO FFM: map with the public FileChannel.map(mode, position, size, arena) of Java 22 instead of JDK internals
    //through the trusted lookup, unmapping by closing the arena (measure the cost of closing shared arenas)
    private static final class Internals {
        static final MethodHandle GET_FILE_DESCRIPTOR;
        static final MethodHandle GET_DISPATCHER;
        /** (FileChannel, int protection, long position, long length, boolean sync) to address */
        static final MethodHandle MAP;
        /** (long address, long length) */
        static final MethodHandle UNMAP;
        static final MethodHandle LOCK;
        static final MethodHandle RELEASE;
        static final MethodHandle CREATE_FILE_KEY;
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
                final boolean staticDispatcher = Modifier.isStatic(dispatcherField.getModifiers());
                GET_DISPATCHER = staticDispatcher
                        ? MethodHandles.dropArguments(dispatcherGetter.asType(MethodType.methodType(Object.class)), 0,
                                FileChannel.class)
                        : dispatcherGetter.asType(MethodType.methodType(Object.class, FileChannel.class));
                final MethodType mapType = MethodType.methodType(long.class, FileChannel.class, int.class,
                        long.class, long.class, boolean.class);
                final MethodType unmapType = MethodType.methodType(void.class, long.class, long.class);
                final Method dispatcherMap = dispatcherMap(dispatcherClass);
                if (staticDispatcher && dispatcherMap != null) {
                    //Java 21+: mapping through the file dispatcher, shared by all channels
                    final Object dispatcher = dispatcherGetter.invoke();
                    MAP = MethodHandles.filterArguments(lookup.unreflect(dispatcherMap).bindTo(dispatcher), 0,
                            GET_FILE_DESCRIPTOR).asType(mapType);
                    //noinspection JavaReflectionMemberAccess
                    UNMAP = lookup.unreflect(dispatcherClass.getDeclaredMethod("unmap", long.class, long.class))
                            .bindTo(dispatcher).asType(unmapType);
                } else {
                    //Java 17: mapping through the file channel implementation
                    MAP = lookup.unreflect(fileChannelClass.getDeclaredMethod("map0", int.class, long.class,
                            long.class, boolean.class)).asType(mapType);
                    UNMAP = lookup.unreflect(fileChannelClass.getDeclaredMethod("unmap0", long.class, long.class))
                            .asType(unmapType);
                }
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
            } catch (final Throwable e) {
                throw new IllegalStateException("Failed to access the JDK's file channel internals", e);
            }
        }

        /** Returns the file dispatcher's map method of Java 21 or later, or null on Java 17 */
        private static Method dispatcherMap(final Class<?> dispatcherClass) {
            try {
                return dispatcherClass.getDeclaredMethod("map", FileDescriptor.class, int.class, long.class,
                        long.class, boolean.class);
            } catch (final NoSuchMethodException e) {
                return null;
            }
        }
    }
}
