/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2024 tools4j.org (Marco Terzer, Anton Anufriev)
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
package org.tools4j.mmap.region.impl;

import java.lang.reflect.Method;

public class Constants {
    /**
     * Memory page size that is exposed from internal java implementation.
     * All memory region sizes are expected to be evenly divisible by this value
     * to ensure alignment of regions with memory pages and cache lines.
     */
    public static final long REGION_SIZE_GRANULARITY = regionSizeGranularity(); //4KB - page size

    private Constants() {
        throw new IllegalStateException("Constants is not instantiable");
    }

    /**
     * Digging into protected java code to get the memory page size.
     *
     * @return memory page size
     */
    public static long regionSizeGranularity() {
      try {
        Class<?> fileChannelImplClass = Class.forName("sun.nio.ch.FileChannelImpl");
        final Method method = fileChannelImplClass.getDeclaredMethod("initIDs");
        method.setAccessible(true);
        final long result = (long)method.invoke(null);
        method.setAccessible(false);
        return result;
      } catch (final Exception e) {
        throw new RuntimeException(e);
      }
    }
}
