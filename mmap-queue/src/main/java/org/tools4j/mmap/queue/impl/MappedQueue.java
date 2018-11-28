/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2018 mmap (tools4j), Marco Terzer, Anton Anufriev
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
package org.tools4j.mmap.queue.impl;

import java.io.IOException;

import org.agrona.CloseHelper;

import org.tools4j.mmap.queue.api.Appender;
import org.tools4j.mmap.queue.api.Enumerator;
import org.tools4j.mmap.queue.api.Poller;
import org.tools4j.mmap.queue.api.Queue;
import org.tools4j.mmap.region.api.AccessibleRegion;
import org.tools4j.mmap.region.api.FileSizeEnsurer;
import org.tools4j.mmap.region.api.RegionRingFactory;
import org.tools4j.mmap.region.impl.FileInitialiser;
import org.tools4j.mmap.region.impl.MappedFile;
import org.tools4j.mmap.region.impl.RegionRing;

public class MappedQueue implements Queue {

    private final MappedFile appenderFile;
    private final MappedFile enumeratorFile;
    private final AccessibleRegion appenderRegionRingAccessor;
    private final AccessibleRegion enumeratorRegionRingAccessor;

    public MappedQueue(final String fileName,
                       final int regionSize,
                       final RegionRingFactory factory,
                       final int ringSize,
                       final int regionsToMapAhead,
                       final long maxFileSize) throws IOException {
        this.appenderFile = new MappedFile(fileName, MappedFile.Mode.READ_WRITE_CLEAR, regionSize,
                FileInitialiser.forMode(MappedFile.Mode.READ_WRITE_CLEAR));
        this.enumeratorFile = new MappedFile(fileName, MappedFile.Mode.READ_ONLY, regionSize,
                FileInitialiser.forMode(MappedFile.Mode.READ_ONLY));

        appenderRegionRingAccessor = new RegionRing(
                factory.create(
                        ringSize,
                        regionSize,
                        appenderFile::getFileChannel,
                        FileSizeEnsurer.bounded(appenderFile::getFileLength, appenderFile::setFileLength, maxFileSize),
                        appenderFile.getMode().getMapMode()),
                regionsToMapAhead);

        enumeratorRegionRingAccessor = new RegionRing(
                factory.create(
                        ringSize,
                        regionSize,
                        enumeratorFile::getFileChannel,
                        FileSizeEnsurer.NO_OP,
                        enumeratorFile.getMode().getMapMode()),
                regionsToMapAhead);
    }

    @Override
    public Appender appender() {
        return new MappedAppender(appenderRegionRingAccessor, 64);
    }

    @Override
    public Poller poller() {
        return new MappedPoller(enumeratorRegionRingAccessor);
    }

    @Override
    public Enumerator enumerator() {
        return new MappedEnumerator(enumeratorRegionRingAccessor);
    }

    @Override
    public void close() {
        if (appenderRegionRingAccessor instanceof AutoCloseable) {
            CloseHelper.quietClose((AutoCloseable) appenderRegionRingAccessor);
        }
        if (enumeratorRegionRingAccessor instanceof AutoCloseable) {
            CloseHelper.quietClose((AutoCloseable) enumeratorRegionRingAccessor);
        }
        CloseHelper.quietClose(appenderFile);
        CloseHelper.quietClose(enumeratorFile);
    }
}
