/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2025 tools4j.org (Marco Terzer, Anton Anufriev)
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
package org.tools4j.mmap.queue.config;

import org.tools4j.mmap.region.api.AccessMode;

public interface QueueConfig {
    AccessMode accessMode();
    int maxAppenders();
    long maxHeaderFileSize();
    long maxPayloadFileSize();
    boolean expandHeaderFile();
    boolean expandPayloadFiles();
    boolean rollHeaderFile();
    boolean rollPayloadFiles();
    int headerFilesToCreateAhead();
    int payloadFilesToCreateAhead();

    AppenderConfig appenderConfig();
    ReaderConfig pollerConfig();
    ReaderConfig entryReaderConfig();
    ReaderConfig entryIteratorConfig();
    IndexReaderConfig indexReaderConfig();

    QueueConfig toImmutableQueueConfig();

    static QueueConfigurator configure() {
        return QueueConfigurator.configure();
    }

    static QueueConfigurator configure(final QueueConfig defaults) {
        return QueueConfigurator.configure(defaults);
    }

    static QueueConfig getDefault() {
        return QueueConfigurations.defaultQueueConfig();
    }
}
