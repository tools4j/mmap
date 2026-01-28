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
package org.tools4j.mmap.region.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.region.api.AccessMode;
import org.tools4j.mmap.region.api.RegionMetrics;
import org.tools4j.mmap.region.unsafe.RegionMapper;

import static java.util.Objects.requireNonNull;

class LoggingRegionMapper implements RegionMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingRegionMapper.class);
    private final RegionMapper mapper;
    LoggingRegionMapper(final RegionMapper mapper) {
        this.mapper = requireNonNull(mapper);
    }
    @Override
    public AccessMode accessMode() {
        return mapper.accessMode();
    }

    @Override
    public long mapInternal(final long position, final int regionSize) {
        final boolean cached = isMappedInCache(position);
        final long address = mapper.mapInternal(position, regionSize);
        LOGGER.info("mapInternal: {} -> @{}{}", position, address, cached ? " (cached)" : "");
        return address;
    }

    @Override
    public void unmapInternal(final long position, final long address, final int regionSize) {
        mapper.unmapInternal(position, address, regionSize);
        LOGGER.info("unmapInternal: {} -> @{}", position, address);
    }

    @Override
    public boolean isMappedInCache(final long position) {
        return mapper.isMappedInCache(position);
    }

    @Override
    public boolean isClosed() {
        return mapper.isClosed();
    }

    @Override
    public void close() {
        LOGGER.info("Closing: {}", this);
        mapper.close();
    }

    @Override
    public RegionMetrics regionMetrics() {
        return mapper.regionMetrics();
    }

    @Override
    public String toString() {
        return "LoggingRegionMapper{mapper=" + mapper + '}';
    }
}
