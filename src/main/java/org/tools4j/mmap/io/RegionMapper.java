/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 mmap (tools4j), Marco Terzer
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
package org.tools4j.mmap.io;

import sun.nio.ch.FileChannelImpl;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.nio.channels.FileChannel;

/**
 * Maps regions of a file and manages those mapped regions.
 */
public final class RegionMapper {

    @FunctionalInterface
    private interface MapMethod {
        long map(FileChannel fileChannel, int mode, long position, long length) throws Throwable;
    }

    @FunctionalInterface
    private interface UnmapMethod {
        void unmap(long address, long length) throws Throwable;
    }

    private static final MapMethod MAP_METHOD = initMapMethod();
    private static final UnmapMethod UNMAP_METHOD = initUnmapMethod();

    public static final long REGION_SIZE_GRANULARITY = initRegionSizeGranularity();

    public static long map(final FileChannel fileChannel, final boolean readOnly, final long position, final long length) {
//        final long t0 = System.nanoTime();
        try {
            return MAP_METHOD.map(fileChannel, readOnly ? 0 : 1, position, length);
        } catch (final Throwable e) {
            throw new RuntimeException("Mapping failed for " + fileChannel + ":" + position + ":" + length, e);
//        } finally {
//            System.out.println("map: dt=" + (System.nanoTime() - t0)/1000f + " micros");
        }
    }

    public static void unmap(final FileChannel fileChannel, final long address, final long length) {
        try {
            UNMAP_METHOD.unmap(address, length);
        } catch (final Throwable e) {
            throw new RuntimeException("Unmapping failed for " + fileChannel + ":@" + address + ":" + length, e);
        }
    }

    private static long initRegionSizeGranularity() {
        try {
            final Method method = FileChannelImpl.class.getDeclaredMethod("initIDs");
            method.setAccessible(true);
            final long result = (long)method.invoke(null);
            method.setAccessible(false);
            return result;
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static MapMethod initMapMethod() {
        final MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            final Method method = FileChannelImpl.class.getDeclaredMethod("map0", int.class, long.class, long.class);
            method.setAccessible(true);
            final MethodHandle handle = lookup.unreflect(method);
            method.setAccessible(false);
            return (channel, mode, pos, len) -> (long)handle.invokeExact((FileChannelImpl)channel, mode, pos, len);

        } catch (final Throwable e) {
            throw new RuntimeException("Could not initialise map method", e);
        }
    }

    private static UnmapMethod initUnmapMethod() {
        final MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            final Method method = FileChannelImpl.class.getDeclaredMethod("unmap0", long.class, long.class);
            method.setAccessible(true);
            final MethodHandle handle = lookup.unreflect(method);
            method.setAccessible(false);
            return (pos, len) -> {
                final int dummy = (int)handle.invokeExact(pos, len);
            };
        } catch (final Throwable e) {
            throw new RuntimeException("Could not initialise map method", e);
        }
    }
}
