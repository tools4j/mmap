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
package org.tools4j.mmap.queue.api;

import org.tools4j.mmap.queue.impl.QueueConfiguratorImpl;
import org.tools4j.mmap.region.api.MappingStrategy;

public interface QueueConfigurator extends QueueConfig {

    QueueConfigurator maxHeaderFileSize(long maxHeaderFileSize);
    QueueConfigurator maxPayloadFileSize(long maxPayloadFileSize);
    QueueConfigurator expandHeaderFile(boolean expandHeaderFile);
    QueueConfigurator expandPayloadFiles(boolean expandPayloadFiles);
    QueueConfigurator rollHeaderFile(boolean rollHeaderFile);
    QueueConfigurator rollPayloadFiles(boolean rollPayloadFiles);
    QueueConfigurator headerFilesToCreateAhead(int headerFilesToCreateAhead);
    QueueConfigurator payloadFilesToCreateAhead(int payloadFilesToCreateAhead);
    QueueConfigurator closePollerFiles(boolean closePollerFiles);
    QueueConfigurator closeReaderFiles(boolean closeReaderFiles);
    QueueConfigurator pollerHeaderMappingStrategy(MappingStrategy mappingStrategy);
    QueueConfigurator pollerPayloadMappingStrategy(MappingStrategy mappingStrategy);
    QueueConfigurator readerHeaderMappingStrategy(MappingStrategy mappingStrategy);
    QueueConfigurator readerPayloadMappingStrategy(MappingStrategy mappingStrategy);
    QueueConfigurator appenderHeaderMappingStrategy(MappingStrategy mappingStrategy);
    QueueConfigurator appenderPayloadMappingStrategy(MappingStrategy mappingStrategy);
    QueueConfigurator reset();

    static QueueConfigurator create() {
        return new QueueConfiguratorImpl();
    }

    static QueueConfigurator create(final QueueConfig defaults) {
        return new QueueConfiguratorImpl(defaults);
    }
}
