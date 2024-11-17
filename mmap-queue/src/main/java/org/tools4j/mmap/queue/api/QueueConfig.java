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

import org.tools4j.mmap.region.api.MappingConfig;
import org.tools4j.mmap.region.api.MappingStrategy;

public interface QueueConfig {
    long maxHeaderFileSize();
    long maxPayloadFileSize();
    boolean expandHeaderFile();
    boolean expandPayloadFiles();
    boolean rollHeaderFile();
    boolean rollPayloadFiles();
    int headerFilesToCreateAhead();
    int payloadFilesToCreateAhead();
    boolean closePollerFiles();
    boolean closeReaderFiles();
    MappingStrategy pollerHeaderMappingStrategy();
    MappingStrategy pollerPayloadMappingStrategy();
    MappingStrategy readerHeaderMappingStrategy();
    MappingStrategy readerPayloadMappingStrategy();
    MappingStrategy appenderHeaderMappingStrategy();
    MappingStrategy appenderPayloadMappingStrategy();

    MappingConfig pollerHeaderConfig();
    MappingConfig pollerPayloadConfig();
    MappingConfig readerHeaderConfig();
    MappingConfig readerPayloadConfig();
    MappingConfig appenderHeaderConfig();
    MappingConfig appenderPayloadConfig();

    QueueConfig toImmutableQueueConfig();

    static QueueConfigurator configure() {
        return QueueConfigurator.create();
    }

    static QueueConfigurator configure(final QueueConfig defaults) {
        return QueueConfigurator.create(defaults);
    }

    static QueueConfig getDefault() {
        return QueueConfigurations.defaultQueueConfig();
    }
}