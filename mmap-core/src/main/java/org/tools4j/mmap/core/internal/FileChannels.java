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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.concurrent.atomic.AtomicLongArray;

import static org.tools4j.mmap.core.internal.CacheLines.CACHE_LINE_LENGTH;

/**
 * Maps, unmaps and locks files without allocating, through the JDK's internal file channel implementation and its
 * file dispatcher, reached through the trusted lookup; mapping goes through the file dispatcher on Java 21 or later,
 * and through the file channel implementation on Java 17. All internals are reached through method handles typed with
 * public types only, so that they are invoked exactly, without boxing or allocation. File space is allocated natively
 * with {@code fallocate} through FFM, on 64-bit Linux with Java 22 or later.
 * <p>
 * Locking and allocating are package-private: all locks on our files are taken through {@link FileChannelLock},
 * which needs to see them all, since OS locks belong to the process, and allocates while holding the lock; mapping and
 * unmapping are public, for the file mappers, which pass the same length to unmap as to map. The bytes currently
 * mapped and the number of mappings are counted, for {@link #mappedBytes()} and {@link #mappingCount()}.
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

    //NOTE: bytes and count share a cache line, written together on the rare map and unmap, padded on both sides with
    //      a cache line, as array objects are only 8-byte aligned, so that no other data shares their line(s)
    private static final int COUNTER_PADDING = CACHE_LINE_LENGTH / Long.BYTES;
    private static final int MAPPED_BYTES = COUNTER_PADDING;
    private static final int MAPPING_COUNT = COUNTER_PADDING + 1;
    private static final AtomicLongArray COUNTERS = new AtomicLongArray(MAPPING_COUNT + 1 + COUNTER_PADDING);

    /**
     * Returns the number of bytes currently mapped through {@link #map(FileChannel, MapMode, long, long)} and not
     * unmapped yet, the virtual address space taken by mappings of this library, not the memory they use.
     *
     * @return the bytes currently mapped
     */
    public static long mappedBytes() {
        return COUNTERS.get(MAPPED_BYTES);
    }

    /**
     * Returns the number of mappings currently mapped through {@link #map(FileChannel, MapMode, long, long)} and not
     * unmapped yet.
     *
     * @return the number of mappings currently mapped
     */
    public static long mappingCount() {
        return COUNTERS.get(MAPPING_COUNT);
    }

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
        final long address;
        try {
            address = (long) Internals.MAP.invokeExact(fileChannel, protection(mode), offset, length, false);
        } catch (final IOException ex) {
            throw new UncheckedIOException("Failed to map " + length + " bytes at offset " + offset + " with mode " +
                    mode, ex);
        } catch (final RuntimeException | Error ex) {
            throw ex;
        } catch (final Throwable ex) {
            throw new IllegalStateException("Failed to map " + length + " bytes at offset " + offset + " with mode " +
                    mode, ex);
        }
        COUNTERS.getAndAdd(MAPPED_BYTES, length);
        COUNTERS.getAndIncrement(MAPPING_COUNT);
        return address;
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
        COUNTERS.getAndAdd(MAPPED_BYTES, -length);
        COUNTERS.getAndDecrement(MAPPING_COUNT);
    }

    /**
     * Allocates the given range of the file natively, so that it reads as zero, extending the file if the range ends
     * beyond it; to be invoked while holding the file's exclusive lock. Allocates nothing if successful.
     *
     * @param fd                  the file descriptor, of a file open for writing
     * @param position            the start of the range
     * @param length              the length of the range, positive
     * @param withoutNativeAccess true to allocate also if native access is not enabled, as long as the JVM allows it
     *                            with a warning of its own, false to allocate only if native access is enabled
     * @return {@link FileAllocation#ALLOCATED}, or why the range was not allocated, never
     *         {@link FileAllocation#SKIPPED}
     * @throws IOException if allocating fails other than by the file system not supporting it, for instance if the
     *                     disk is full
     */
    static FileAllocation allocate(final FileDescriptor fd, final long position, final long length,
                                   final boolean withoutNativeAccess) throws IOException {
        final FileAllocation unsupported = AllocationSupport.UNSUPPORTED;
        if (unsupported != null) {
            return unsupported;
        }
        if (!withoutNativeAccess && !AllocationSupport.NATIVE_ACCESS_ENABLED) {
            return FileAllocation.NATIVE_ACCESS_DISABLED;
        }
        final FileAllocator allocator = Allocator.ALLOCATOR;
        if (allocator == null) {
            return Allocator.FAILURE;
        }
        return allocator.allocate(fdNumber(fd), position, length) ? FileAllocation.ALLOCATED :
                FileAllocation.FILE_SYSTEM_UNSUPPORTED;
    }

    private static int fdNumber(final FileDescriptor fd) {
        try {
            return (int) Internals.GET_FD_NUMBER.invokeExact(fd);
        } catch (final Throwable e) {
            throw new IllegalStateException("Failed to get the number of file descriptor " + fd, e);
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
        /** (FileDescriptor) to the descriptor's number */
        static final MethodHandle GET_FD_NUMBER;
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
                GET_FD_NUMBER = lookup.findGetter(FileDescriptor.class, "fd", int.class);
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

    /** Whether native allocation is possible at all, initialised on the first allocation */
    private static final class AllocationSupport {
        static final int FFM_JAVA_VERSION = 22;
        /** Why native allocation is not possible, whatever the mode, or null if it may be */
        static final FileAllocation UNSUPPORTED = Runtime.version().feature() < FFM_JAVA_VERSION
                ? FileAllocation.JDK_UNSUPPORTED
                : OS.LINUX ? null : FileAllocation.PLATFORM_UNSUPPORTED;
        static final boolean NATIVE_ACCESS_ENABLED = UNSUPPORTED == null && nativeAccessEnabled();

        /** Returns Module.isNativeAccessEnabled() of this module, public API since Java 22 */
        private static boolean nativeAccessEnabled() {
            try {
                //noinspection JavaLangInvokeHandleSignature: public API since Java 22, only invoked there
                return (boolean) MethodHandles.publicLookup()
                        .findVirtual(Module.class, "isNativeAccessEnabled", MethodType.methodType(boolean.class))
                        .invokeExact(FileChannels.class.getModule());
            } catch (final Throwable e) {
                throw new IllegalStateException("Failed to check whether native access is enabled", e);
            }
        }
    }

    /**
     * The native allocator, initialised when first needed only, as creating it is a restricted operation for which the
     * JVM warns unless native access is enabled
     */
    private static final class Allocator {
        static final String FFM_FILE_ALLOCATOR = "org.tools4j.mmap.core.internal.FfmFileAllocator";
        /** The allocator, or null if it cannot be created */
        static final FileAllocator ALLOCATOR;
        /** Why the allocator cannot be created, or null if it was */
        static final FileAllocation FAILURE;
        static {
            FileAllocator allocator = null;
            FileAllocation failure = null;
            try {
                allocator = Class.forName(FFM_FILE_ALLOCATOR).asSubclass(FileAllocator.class)
                        .getDeclaredConstructor().newInstance();
            } catch (final ClassNotFoundException e) {
                failure = FileAllocation.JDK_UNSUPPORTED;//mmap-core built with Java 21 or earlier
            } catch (final InvocationTargetException e) {
                if (e.getCause() instanceof IllegalCallerException) {
                    failure = FileAllocation.NATIVE_ACCESS_DENIED;
                } else if (e.getCause() instanceof UnsupportedOperationException) {
                    failure = FileAllocation.PLATFORM_UNSUPPORTED;//not 64-bit, or fallocate not found
                } else {
                    throw new IllegalStateException("Failed to create " + FFM_FILE_ALLOCATOR, e.getCause());
                }
            } catch (final ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to create " + FFM_FILE_ALLOCATOR, e);
            }
            ALLOCATOR = allocator;
            FAILURE = failure;
        }
    }
}
