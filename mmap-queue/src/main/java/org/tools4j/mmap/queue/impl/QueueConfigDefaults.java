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
package org.tools4j.mmap.queue.impl;

import org.tools4j.mmap.queue.config.AppenderConfig;
import org.tools4j.mmap.queue.config.IndexReaderConfig;
import org.tools4j.mmap.queue.config.QueueConfig;
import org.tools4j.mmap.queue.config.ReaderConfig;
import org.tools4j.mmap.region.api.AccessMode;

import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultAccessMode;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultExpandHeaderFile;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultExpandPayloadFiles;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultHeaderFilesToCreateAhead;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultMaxAppenders;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultMaxHeaderFileSize;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultMaxPayloadFileSize;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultPayloadFilesToCreateAhead;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultRollHeaderFile;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultRollPayloadFiles;
import static org.tools4j.mmap.queue.impl.AppenderConfigDefaults.APPENDER_CONFIG_DEFAULTS;
import static org.tools4j.mmap.queue.impl.IndexReaderConfigDefaults.INDEX_READER_CONFIG_DEFAULTS;
import static org.tools4j.mmap.queue.impl.ReaderConfigDefaults.ENTRY_ITERATOR_CONFIG_DEFAULTS;
import static org.tools4j.mmap.queue.impl.ReaderConfigDefaults.ENTRY_READER_CONFIG_DEFAULTS;
import static org.tools4j.mmap.queue.impl.ReaderConfigDefaults.POLLER_CONFIG_DEFAULTS;

public enum QueueConfigDefaults implements QueueConfig {
    QUEUE_CONFIG_DEFAULTS;

    @Override
    public AccessMode accessMode() {
        return defaultAccessMode();
    }

    @Override
    public int maxAppenders() {
        return defaultMaxAppenders();
    }

    @Override
    public long maxHeaderFileSize() {
        return defaultMaxHeaderFileSize();
    }

    @Override
    public long maxPayloadFileSize() {
        return defaultMaxPayloadFileSize();
    }

    @Override
    public boolean expandHeaderFile() {
        return defaultExpandHeaderFile();
    }

    @Override
    public boolean expandPayloadFiles() {
        return defaultExpandPayloadFiles();
    }

    @Override
    public boolean rollHeaderFile() {
        return defaultRollHeaderFile();
    }

    @Override
    public boolean rollPayloadFiles() {
        return defaultRollPayloadFiles();
    }

    @Override
    public int headerFilesToCreateAhead() {
        return defaultHeaderFilesToCreateAhead();
    }

    @Override
    public int payloadFilesToCreateAhead() {
        return defaultPayloadFilesToCreateAhead();
    }

    @Override
    public AppenderConfig appenderConfig() {
        return APPENDER_CONFIG_DEFAULTS;
    }

    @Override
    public ReaderConfig pollerConfig() {
        return POLLER_CONFIG_DEFAULTS;
    }

    @Override
    public ReaderConfig entryReaderConfig() {
        return ENTRY_READER_CONFIG_DEFAULTS;
    }

    @Override
    public ReaderConfig entryIteratorConfig() {
        return ENTRY_ITERATOR_CONFIG_DEFAULTS;
    }

    @Override
    public IndexReaderConfig indexReaderConfig() {
        return INDEX_READER_CONFIG_DEFAULTS;
    }

    @Override
    public QueueConfig toImmutableQueueConfig() {
        return new QueueConfigImpl(this);
    }

    @Override
    public String toString() {
        return "QueueConfigDefaults" +
                ":accessMode=" + accessMode() +
                "|maxAppenders=" + maxAppenders() +
                "|maxHeaderFileSize=" + maxHeaderFileSize() +
                "|maxPayloadFileSize=" + maxPayloadFileSize() +
                "|expandHeaderFile=" + expandHeaderFile() +
                "|expandPayloadFiles=" + expandPayloadFiles() +
                "|rollHeaderFile=" + rollHeaderFile() +
                "|rollPayloadFiles=" + rollPayloadFiles() +
                "|headerFilesToCreateAhead=" + headerFilesToCreateAhead() +
                "|payloadFilesToCreateAhead=" + payloadFilesToCreateAhead() +
                "|appenderConfig={" + appenderConfig() + "}" +
                "|pollerConfig={" + pollerConfig() + "}" +
                "|entryReaderConfig={" + entryReaderConfig() + "}" +
                "|entryIteratorConfig={" + entryIteratorConfig() + "}" +
                "|indexReaderConfig={" + indexReaderConfig() + "}";
    }
}
