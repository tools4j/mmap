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
package org.tools4j.mmap.mapping.impl;

import org.tools4j.mmap.core.base.Describable;
import org.tools4j.mmap.mapping.unsafe.ConcurrentRollingFileMapper;
import org.tools4j.mmap.mapping.unsafe.RegionMapper;
import org.tools4j.mmap.mapping.unsafe.RollingFileMapper;

import java.util.function.Predicate;

/**
 * Resources of the mapping module, as seen by lifecycle listeners.
 */
public enum MappingResources {
    ;

    /**
     * Condition matching internal resources, see {@link #isInternal(Describable)}, for instance to log their lifecycle
     * events at debug level with a conditional lifecycle listener.
     */
    public static final Predicate<Describable> INTERNAL = new Predicate<>() {
        @Override
        public boolean test(final Describable resource) {
            return isInternal(resource);
        }

        @Override
        public String toString() {
            return "MappingResources.INTERNAL";
        }
    };

    /**
     * Returns whether a resource is internal to the mapping module, a region mapper or a rolling file mapper, whose
     * individual files are resources of their own.
     *
     * @param resource the resource
     * @return true if the resource is internal to the mapping module
     */
    public static boolean isInternal(final Describable resource) {
        return resource instanceof RegionMapper || resource instanceof RollingFileMapper ||
                resource instanceof ConcurrentRollingFileMapper;
    }
}
