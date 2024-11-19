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

import static java.util.Objects.requireNonNull;

public class ReaderConfigImpl implements ReaderConfig {
    private final MappingStrategy headerMappingStrategy;
    private final MappingStrategy payloadMappingStrategy;
    private final boolean closeHeaderFiles;
    private final boolean closePayloadFiles;

    public ReaderConfigImpl(final ReaderConfig config) {
        this(config.headerMappingStrategy(), config.payloadMappingStrategy(),
                config.closeHeaderFiles(), config.closePayloadFiles());
    }

    public ReaderConfigImpl(final MappingStrategy headerMappingStrategy,
                            final MappingStrategy payloadMappingStrategy,
                            final boolean closeHeaderFiles,
                            final boolean closePayloadFiles) {
        this.headerMappingStrategy = requireNonNull(headerMappingStrategy);
        this.payloadMappingStrategy = requireNonNull(payloadMappingStrategy);
        this.closeHeaderFiles = closeHeaderFiles;
        this.closePayloadFiles = closePayloadFiles;
    }

    @Override
    public MappingStrategy headerMappingStrategy() {
        return headerMappingStrategy;
    }

    @Override
    public MappingStrategy payloadMappingStrategy() {
        return payloadMappingStrategy;
    }

    @Override
    public boolean closeHeaderFiles() {
        return closeHeaderFiles;
    }

    @Override
    public boolean closePayloadFiles() {
        return closePayloadFiles;
    }

    @Override
    public ReaderConfig toImmutableReaderConfig() {
        return this;
    }

    @Override
    public String toString() {
        return "ReaderConfigImpl" +
                ":headerMappingStrategy=" + headerMappingStrategy +
                "|payloadMappingStrategy=" + payloadMappingStrategy +
                "|closeHeaderFiles=" + closeHeaderFiles +
                "|closePayloadFiles=" + closePayloadFiles;
    }
}
