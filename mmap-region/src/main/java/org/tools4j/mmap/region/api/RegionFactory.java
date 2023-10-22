/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2023 tools4j.org (Marco Terzer, Anton Anufriev)
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
package org.tools4j.mmap.region.api;

import org.tools4j.mmap.region.impl.AsyncVolatileStateMachineRegion;
import org.tools4j.mmap.region.impl.SyncRegion;

import java.util.concurrent.TimeUnit;

/**
 * Region factory
 *
 * @param <T> type of the region, sync or async.
 */
public interface RegionFactory<T extends Region> {
    RegionFactory<AsyncRegion> ASYNC_VOLATILE_STATE_MACHINE = new RegionFactory<AsyncRegion>() {
        @Override
        public AsyncRegion create(int size, FileMapper fileMapper, long timeout, TimeUnit timeUnit) {
            return new AsyncVolatileStateMachineRegion(fileMapper, size, timeout, timeUnit);
        }

        @Override
        public String toString() {
            return "ASYNC_VOLATILE_STATE_MACHINE";
        }
    };

    RegionFactory<Region> SYNC = (size, fileMapper, timeout, timeUnit) -> new SyncRegion(fileMapper, size);

    T create(int size, FileMapper fileMapper, long timeout, TimeUnit timeUnit);

}
