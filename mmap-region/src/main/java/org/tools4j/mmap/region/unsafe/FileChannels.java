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

import org.agrona.LangUtil;
import org.agrona.UnsafeApi;
import org.tools4j.mmap.region.api.Unsafe;

import java.io.FileDescriptor;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

/**
 * Code copied from older versions of Agrona's {@link org.agrona.IoUtil}.
 */
@Unsafe
public enum FileChannels {
    ;
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
     */
    public static long map(final FileChannel fileChannel,
                           final FileChannel.MapMode mode,
                           final long offset,
                           final long length) {
        try {
            if (null != MappingMethods.MAP_FILE_DISPATCHER) {
                final FileDescriptor fd = (FileDescriptor)MappingMethods.GET_FILE_DESCRIPTOR.invoke(fileChannel);
                return (long)MappingMethods.MAP_FILE_DISPATCHER.invoke(
                        MappingMethods.FILE_DISPATCHER, fd, getMode(mode), offset, length, false);
            } else {
                return (long)MappingMethods.MAP_WITH_SYNC_ADDRESS.invoke(
                        fileChannel, getMode(mode), offset, length, false);
            }
        } catch (final Throwable ex) {
            LangUtil.rethrowUnchecked(ex);
        }
        return 0;
    }
    /**
     * Unmap a region of a file.
     *
     * @param fileChannel which has been mapped.
     * @param address     at which the mapping begins.
     * @param length      of the mapped region.
     */
    public static void unmap(final FileChannel fileChannel, final long address, final long length) {
        try {
            if (null != MappingMethods.UNMAP_FILE_DISPATCHER) {
                MappingMethods.UNMAP_FILE_DISPATCHER.invoke(MappingMethods.FILE_DISPATCHER, address, length);
            } else {
                MappingMethods.UNMAP_ADDRESS.invoke(address, length);
            }
        } catch (final Throwable ex) {
            LangUtil.rethrowUnchecked(ex);
        }
    }

    private static int getMode(final FileChannel.MapMode mode) {
        if (mode == MapMode.READ_ONLY) {
            return MAP_READ_ONLY;
        }
        else if (mode == MapMode.READ_WRITE) {
            return MAP_READ_WRITE;
        } else {
            return MAP_PRIVATE;
        }
    }

    static class MappingMethods {
        static final MethodHandle MAP_FILE_DISPATCHER;
        static final MethodHandle UNMAP_FILE_DISPATCHER;
        static final Object FILE_DISPATCHER;
        static final MethodHandle GET_FILE_DESCRIPTOR;
        static final MethodHandle MAP_WITH_SYNC_ADDRESS;
        static final MethodHandle UNMAP_ADDRESS;
        static {
            try {
                final Class<?> fileChannelClass = Class.forName("sun.nio.ch.FileChannelImpl");
                final Class<?> fileDispatcherClass = Class.forName("sun.nio.ch.FileDispatcher");
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                Object fileDispatcher = null;
                MethodHandle mapFileDispatcher = null;
                MethodHandle getFD = null;
                MethodHandle mapWithSyncAddress = null;
                MethodHandle unmapFileDispatcher = null;
                MethodHandle unmapAddress = null;
                try {
                    // JDK 21+
                    final Field implLookupField = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
                    lookup = (MethodHandles.Lookup)UnsafeApi.getReference(
                            MethodHandles.Lookup.class, UnsafeApi.staticFieldOffset(implLookupField));
                    fileDispatcher = lookup.unreflectGetter(fileChannelClass.getDeclaredField("nd")).invoke();
                    getFD = lookup.unreflectGetter(fileChannelClass.getDeclaredField("fd"));
                    mapFileDispatcher = lookup.unreflect(fileDispatcherClass.getDeclaredMethod(
                            "map",
                            FileDescriptor.class,
                            int.class,
                            long.class,
                            long.class,
                            boolean.class));
                    unmapFileDispatcher = lookup.unreflect(
                            fileDispatcherClass.getDeclaredMethod("unmap", long.class, long.class));
                } catch (final Throwable ex) {
                    unmapAddress = lookup.unreflect(getMethod(fileChannelClass, "unmap0", long.class, long.class));
                    // JDK 17
                    mapWithSyncAddress = lookup.unreflect(getMethod(
                            fileChannelClass, "map0", int.class, long.class, long.class, boolean.class));
                }
                MAP_FILE_DISPATCHER = mapFileDispatcher;
                UNMAP_FILE_DISPATCHER = unmapFileDispatcher;
                FILE_DISPATCHER = fileDispatcher;
                GET_FILE_DESCRIPTOR = getFD;
                MAP_WITH_SYNC_ADDRESS = mapWithSyncAddress;
                UNMAP_ADDRESS = unmapAddress;
            } catch (final Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        private static Method getMethod(final Class<?> klass, final String name, final Class<?>... parameterTypes)
                throws NoSuchMethodException {
            final Method method = klass.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        }
    }
}
