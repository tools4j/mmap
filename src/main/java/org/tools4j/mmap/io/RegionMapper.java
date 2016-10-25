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

import java.lang.reflect.Method;
import java.nio.channels.FileChannel;

/**
 * Maps regions of a file and manages those mapped regions.
 */
public final class RegionMapper {

    private static final Method MAP_METHOD = getMethod(FileChannelImpl.class, "map0", int.class, long.class, long.class);
    private static final Method UNMAP_METHOD = getMethod(FileChannelImpl.class, "unmap0", long.class, long.class);

    public static final long REGION_SIZE_GRANULARITY = initRegionSizeGranularity();

    public static long map(final FileChannel fileChannel, final long position, final long length) {
        try {
            return (long) MAP_METHOD.invoke(fileChannel, 1, position, length);
        } catch (final Exception e) {
            throw new RuntimeException("Mapping failed for " + fileChannel + ":" + position + ":" + length, e);
        }
    }

    public static void unmap(final FileChannel fileChannel, final long address, final long length) {
        try {
            UNMAP_METHOD.invoke(null, address, length);
        } catch (final Exception e) {
            throw new RuntimeException("Unmapping failed for " + fileChannel + ":@" + address + ":" + length, e);
        }
    }

    private static long initRegionSizeGranularity() {
        try {
            return (Long)getMethod(FileChannelImpl.class, "initIDs").invoke(null);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Method getMethod(final Class<?> cls, final String name, final Class<?>... params) {
        try {
            final Method m = cls.getDeclaredMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (final Exception e) {
            throw new RuntimeException("Could not get declared method " + cls.getName() + "." + name + "(..)", e);
        }
    }
}
