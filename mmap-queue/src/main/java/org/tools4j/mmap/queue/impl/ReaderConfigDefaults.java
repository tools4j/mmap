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
package org.tools4j.mmap.queue.impl;

import org.tools4j.mmap.queue.config.ReaderConfig;
import org.tools4j.mmap.region.config.MappingStrategy;

import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultCloseEntryIteratorHeaderFiles;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultCloseEntryIteratorPayloadFiles;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultCloseEntryReaderHeaderFiles;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultCloseEntryReaderPayloadFiles;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultClosePollerHeaderFiles;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultClosePollerPayloadFiles;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultEntryIteratorHeaderMappingStrategy;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultEntryIteratorPayloadMappingStrategy;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultEntryReaderHeaderMappingStrategy;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultEntryReaderPayloadMappingStrategy;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultPollerHeaderMappingStrategy;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultPollerPayloadMappingStrategy;

public enum ReaderConfigDefaults implements ReaderConfig {
    POLLER_CONFIG_DEFAULTS {
        @Override
        public MappingStrategy headerMappingStrategy() {
            return defaultPollerHeaderMappingStrategy();
        }

        @Override
        public MappingStrategy payloadMappingStrategy() {
            return defaultPollerPayloadMappingStrategy();
        }

        @Override
        public boolean closeHeaderFiles() {
            return defaultClosePollerHeaderFiles();
        }

        @Override
        public boolean closePayloadFiles() {
            return defaultClosePollerPayloadFiles();
        }
    },
    ENTRY_READER_CONFIG_DEFAULTS {
        @Override
        public MappingStrategy headerMappingStrategy() {
            return defaultEntryReaderHeaderMappingStrategy();
        }

        @Override
        public MappingStrategy payloadMappingStrategy() {
            return defaultEntryReaderPayloadMappingStrategy();
        }

        @Override
        public boolean closeHeaderFiles() {
            return defaultCloseEntryReaderHeaderFiles();
        }

        @Override
        public boolean closePayloadFiles() {
            return defaultCloseEntryReaderPayloadFiles();
        }
    },
    ENTRY_ITERATOR_CONFIG_DEFAULTS {
        @Override
        public MappingStrategy headerMappingStrategy() {
            return defaultEntryIteratorHeaderMappingStrategy();
        }

        @Override
        public MappingStrategy payloadMappingStrategy() {
            return defaultEntryIteratorPayloadMappingStrategy();
        }

        @Override
        public boolean closeHeaderFiles() {
            return defaultCloseEntryIteratorHeaderFiles();
        }

        @Override
        public boolean closePayloadFiles() {
            return defaultCloseEntryIteratorPayloadFiles();
        }
    };

    @Override
    public ReaderConfig toImmutableReaderConfig() {
        return new ReaderConfigImpl(this);
    }

    @Override
    public String toString() {
        return "ReaderConfigDefaults:" +
                ":name=" + name() +
                "|headerMappingStrategy=" + headerMappingStrategy() +
                "|payloadMappingStrategy=" + payloadMappingStrategy() +
                "|closeHeaderFiles=" + closeHeaderFiles() +
                "|closePayloadFiles=" + closePayloadFiles();
    }
}
