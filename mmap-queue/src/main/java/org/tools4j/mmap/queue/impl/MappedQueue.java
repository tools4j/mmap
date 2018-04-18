/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 hover-raft (tools4j), Anton Anufriev, Marco Terzer
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

import org.tools4j.mmap.queue.api.Appender;
import org.tools4j.mmap.queue.api.Poller;
import org.tools4j.mmap.queue.api.Queue;
import org.tools4j.mmap.region.api.FileSizeEnsurer;
import org.tools4j.mmap.region.api.RegionAccessor;
import org.tools4j.mmap.region.api.RegionRingFactory;
import org.tools4j.mmap.region.impl.FileInitialiser;
import org.tools4j.mmap.region.impl.MappedFile;
import org.tools4j.mmap.region.impl.RegionRingAccessor;

import java.io.IOException;

public class MappedQueue implements Queue {
    private final RegionAccessor appenderRegionRingAccessor;
    private final RegionAccessor enumeratorRegionRingAccessor;

    public MappedQueue(final String fileName,
                       final int regionSize,
                       final RegionRingFactory factory,
                       final int ringSize,
                       final int regionsToMapAhead,
                       final long maxFileSize) throws IOException {
        final MappedFile appenderFile = new MappedFile(fileName, MappedFile.Mode.READ_WRITE_CLEAR,
                regionSize, FileInitialiser::initFile);
        final MappedFile enumeratorFile = new MappedFile(fileName, MappedFile.Mode.READ_ONLY,
                regionSize, FileInitialiser::initFile);

        appenderRegionRingAccessor = new RegionRingAccessor(
                factory.create(
                        ringSize,
                        regionSize,
                        appenderFile::getFileChannel,
                        FileSizeEnsurer.forWritableFile(appenderFile::getFileLength, appenderFile::setFileLength, maxFileSize),
                        appenderFile.getMode().getMapMode()),
                regionSize,
                regionsToMapAhead,
                appenderFile::close);

        enumeratorRegionRingAccessor = new RegionRingAccessor(
                factory.create(
                        ringSize,
                        regionSize,
                        enumeratorFile::getFileChannel,
                        FileSizeEnsurer.NO_OP,
                        enumeratorFile.getMode().getMapMode()),
                regionSize,
                regionsToMapAhead,
                enumeratorFile::close);
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
    public void close() {
        appenderRegionRingAccessor.close();
        enumeratorRegionRingAccessor.close();
    }
}
