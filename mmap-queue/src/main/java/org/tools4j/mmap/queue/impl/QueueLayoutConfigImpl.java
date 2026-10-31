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
package org.tools4j.mmap.queue.impl;

import org.tools4j.mmap.queue.config.QueueConfig;
import org.tools4j.mmap.queue.config.QueueLayoutConfig;

import static org.tools4j.mmap.mapping.impl.Constraints.validateFileSizes;
import static org.tools4j.mmap.mapping.impl.Constraints.validateMaxFileSize;
import static org.tools4j.mmap.mapping.impl.Constraints.validateMinFileSize;
import static org.tools4j.mmap.mapping.impl.Constraints.validateRegionSize;

/**
 * Immutable {@link QueueLayoutConfig queue layout}, as stored in the queue's {@link QueueMetaFile meta file}; it
 * overrides the corresponding values of the {@link QueueConfig} used to open an existing queue.
 *
 * @param headerRegionSize   the header region size, defining the increments of expanded header files if the minimum
 *                           header file size is zero
 * @param payloadRegionSize  the payload region size, defining where payload entries wrap to the next region
 * @param minHeaderFileSize  the minimum header file size, used if the header file is expanded
 * @param maxHeaderFileSize  the maximum header file size, defining the file index if the header file is rolled
 * @param minPayloadFileSize the minimum payload file size, used if payload files are expanded
 * @param maxPayloadFileSize the maximum payload file size, defining the file index if payload files are rolled
 * @param expandHeaderFile   true if the header file is expanded
 * @param expandPayloadFiles true if payload files are expanded
 * @param rollHeaderFile     true if the header file is rolled
 * @param rollPayloadFiles   true if payload files are rolled
 */
record QueueLayoutConfigImpl(int headerRegionSize,
                             int payloadRegionSize,
                             long minHeaderFileSize,
                             long maxHeaderFileSize,
                             long minPayloadFileSize,
                             long maxPayloadFileSize,
                             boolean expandHeaderFile,
                             boolean expandPayloadFiles,
                             boolean rollHeaderFile,
                             boolean rollPayloadFiles) implements QueueLayoutConfig {

    QueueLayoutConfigImpl {
        validateRegionSize(headerRegionSize);
        validateRegionSize(payloadRegionSize);
        validateMinFileSize(minHeaderFileSize);
        validateMaxFileSize(maxHeaderFileSize);
        validateMinFileSize(minPayloadFileSize);
        validateMaxFileSize(maxPayloadFileSize);
        validateFileSizes(minHeaderFileSize, maxHeaderFileSize, expandHeaderFile, headerRegionSize);
        validateFileSizes(minPayloadFileSize, maxPayloadFileSize, expandPayloadFiles, payloadRegionSize);
    }

    static QueueLayoutConfigImpl of(final QueueLayoutConfig config) {
        return new QueueLayoutConfigImpl(
                config.headerRegionSize(),
                config.payloadRegionSize(),
                config.minHeaderFileSize(),
                config.maxHeaderFileSize(),
                config.minPayloadFileSize(),
                config.maxPayloadFileSize(),
                config.expandHeaderFile(),
                config.expandPayloadFiles(),
                config.rollHeaderFile(),
                config.rollPayloadFiles());
    }

    @Override
    public String toString() {
        return "QueueLayoutConfigImpl" +
                ":headerRegionSize=" + headerRegionSize +
                "|payloadRegionSize=" + payloadRegionSize +
                "|minHeaderFileSize=" + minHeaderFileSize +
                "|maxHeaderFileSize=" + maxHeaderFileSize +
                "|minPayloadFileSize=" + minPayloadFileSize +
                "|maxPayloadFileSize=" + maxPayloadFileSize +
                "|expandHeaderFile=" + expandHeaderFile +
                "|expandPayloadFiles=" + expandPayloadFiles +
                "|rollHeaderFile=" + rollHeaderFile +
                "|rollPayloadFiles=" + rollPayloadFiles;
    }
}
