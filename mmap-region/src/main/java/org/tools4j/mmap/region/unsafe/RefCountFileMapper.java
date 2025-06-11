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
package org.tools4j.mmap.region.unsafe;

import org.agrona.collections.Hashing;
import org.agrona.collections.Long2LongCounterMap;
import org.agrona.collections.Long2LongHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.region.api.AccessMode;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;

/**
 * File mapper that can be shared, uses ref counting before unmapping a region.
 * NOTE that this file mapper is NOT thread safe, that is, it cannot be used with {@link AsyncRingRegionMapper}.
 */
public class RefCountFileMapper implements FileMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(RefCountFileMapper.class);

    private final FileMapper fileMapper;
    private final int regionSize;
    private final Long2LongHashMap positionToAddress;
    private final Long2LongCounterMap positionToRefCount;

    public RefCountFileMapper(final FileMapper fileMapper, final int regionSize, final int initialCacheSize) {
        this.fileMapper = requireNonNull(fileMapper);
        this.regionSize = regionSize;
        this.positionToAddress = new Long2LongHashMap(initialCacheSize, Hashing.DEFAULT_LOAD_FACTOR, NULL_ADDRESS);
        this.positionToRefCount = new Long2LongCounterMap(initialCacheSize, Hashing.DEFAULT_LOAD_FACTOR, 0);
    }

    @Override
    public AccessMode accessMode() {
        return fileMapper.accessMode();
    }

    @Override
    public long map(final long position, final int length) {
        checkNotClosed();
        if (position < 0) {
            return NULL_ADDRESS;
        }
        if (length != regionSize) {
            throw new IllegalArgumentException("Length " + length + " must match region size " + regionSize);
        }
        long address = positionToAddress.get(position);
        if (address != NULL_ADDRESS) {
            positionToRefCount.incrementAndGet(position);
            return address;
        }
        address = fileMapper.map(position, length);
        if (address != NULL_ADDRESS) {
            positionToAddress.put(position, address);
            positionToRefCount.put(position, 1);
            return address;
        }
        return NULL_ADDRESS;
    }

    public void notifyMapped(final long position, final long address, final int length) {
        checkNotClosed();
        assert address > NULL_ADDRESS;
        assert position > NULL_POSITION;
        if (length != regionSize) {
            throw new IllegalArgumentException("Length " + length + " must match region size " + regionSize);
        }
        final long addr = positionToAddress.get(position);
        if (addr != NULL_ADDRESS) {
            throw new IllegalArgumentException("Position " + position + " is already mapped to " + addr +
                    " and then again to " + address);
        }
        positionToAddress.put(position, address);
        positionToRefCount.put(position, 1);
    }

    @Override
    public void unmap(final long address, final long position, final int length) {
        checkNotClosed();
        assert address > NULL_ADDRESS;
        assert position > NULL_POSITION;
        if (length != regionSize) {
            throw new IllegalArgumentException("Length " + length + " must match region size " + regionSize);
        }
        final long addr = positionToAddress.get(position);
        if (addr != address) {
            throw new IllegalArgumentException("Position " + position + " is mapped to " + addr +
                    " but provided address is " + address);
        }
        final long remaining = positionToRefCount.decrementAndGet(position);
        if (remaining <= 0) {
            assert remaining == 0 : "remaining is negative";
            positionToAddress.remove(position);
            fileMapper.unmap(address, position, length);
        }
    }

    private void checkNotClosed() {
        if (isClosed()) {
            throw new IllegalStateException("Ref-count file mapper is closed");
        }
    }

    @Override
    public boolean isClosed() {
        return fileMapper.isClosed();
    }

    @Override
    public void close() {
        if (!isClosed()) {
            try {
                positionToAddress.forEachLong((pos, addr) -> {
                    if (pos != NULL_POSITION) {
                        fileMapper.unmap(pos, addr, regionSize);
                    }
                });
                positionToAddress.clear();
                positionToRefCount.clear();
                fileMapper.close();
            } finally {
                positionToAddress.clear();
                positionToRefCount.clear();
                fileMapper.close();
                LOGGER.info("Closed ref-count file mapper: fileMapper={}", fileMapper);
            }
        }
    }

    @Override
    public String toString() {
        return "RefCountFileMapper" +
                ":fileMapper=" + fileMapper +
                "|regionSize=" + regionSize +
                "|positionsMapped=" + positionToAddress.size();
    }
}
