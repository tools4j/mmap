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
package org.tools4j.mmap.mapping.impl;

import org.tools4j.mmap.core.buffer.MemoryBufferFlyweight;
import org.tools4j.mmap.core.buffer.MemoryBuffers;
import org.tools4j.mmap.core.buffer.MutableMemoryBuffer;
import org.tools4j.mmap.core.unsafe.Unsafe;
import org.tools4j.mmap.mapping.api.AccessMode;
import org.tools4j.mmap.mapping.api.DynamicMutableMapping;
import org.tools4j.mmap.mapping.api.RegionMetrics;
import org.tools4j.mmap.mapping.unsafe.RegionMapper;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.core.internal.Validations.validateNotClosed;
import static org.tools4j.mmap.mapping.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.mapping.api.NullValues.NULL_POSITION;
import static org.tools4j.mmap.mapping.impl.MappingValidations.validateMappingLength;
import static org.tools4j.mmap.mapping.impl.MappingValidations.validateLimit;
import static org.tools4j.mmap.mapping.impl.MappingValidations.validatePosition;
import static org.tools4j.mmap.mapping.impl.MappingValidations.validatePositionDelta;
import static org.tools4j.mmap.mapping.impl.MappingValidations.validatePositionState;

public final class DynamicMappingImpl implements DynamicMutableMapping {
    private final RegionMapper regionMapper;
    private final boolean closeRegionMapperOnClose;
    private final RegionMetrics regionMetrics;
    private final MemoryBufferFlyweight buffer = MemoryBuffers.flyweight();
    private final boolean readOnly;
    private long mappedRegionPosition;
    private long mappedRegionAddress;
    private int offset;
    /** The length last requested, {@link #STRETCH_LENGTH} or explicit, mapped again by moves without a length */
    private int requestedLength;
    private boolean closed;

    /**
     * Constructor to create a dynamic mapping backed by the given region mapper.
     * <p>
     * <b>NOTE:</b> Using this constructor directly is unsafe and could lead to a JVM crash in the worst case.
     * Application code should not normally need to call it directly; create dynamic mappings through
     * {@link org.tools4j.mmap.mapping.api.Mappings} instead.
     *
     * @param regionMapper             the region mapper providing the underlying mapping operations
     * @param closeRegionMapperOnClose if true the given region mapper is also closed when this mapping is closed
     */
    @Unsafe
    public DynamicMappingImpl(final RegionMapper regionMapper, final boolean closeRegionMapperOnClose) {
        validateNotClosed(regionMapper);
        this.regionMapper = requireNonNull(regionMapper);
        this.readOnly = regionMapper.accessMode() == AccessMode.READ_ONLY;
        this.closeRegionMapperOnClose = closeRegionMapperOnClose;
        this.regionMetrics = regionMapper.regionMetrics();
        this.mappedRegionPosition = NULL_POSITION;
        this.mappedRegionAddress = NULL_ADDRESS;
        this.offset = 0;
        this.requestedLength = STRETCH_LENGTH;
        this.closed = false;
    }

    @Override
    public AccessMode accessMode() {
        return regionMapper.accessMode();
    }

    @Override
    public RegionMetrics regionMetrics() {
        return regionMetrics;
    }

    @Override
    public MutableMemoryBuffer buffer() {
        return buffer;
    }

    @Override
    public long position() {
        return mappedRegionPosition + offset;//works also for NULL_POSITION
    }

    @Override
    public long address() {
        return mappedRegionAddress + offset;//works also for NULL_ADDRESS
    }

    @Override
    public int length() {
        return buffer.capacity();
    }

    @Override
    public int bytesAvailable() {
        return buffer.capacity();
    }

    @Override
    public int maxLengthAtPosition(final long position) {
        return position >= 0 ? regionMetrics.maxLengthAtPosition(position) : 0;
    }

    @Override
    public boolean isMapped() {
        return mappedRegionPosition != NULL_POSITION;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public boolean moveTo(final long position) {
        validatePosition(position);
        return moveToInternal(position, requestedLength);
    }

    @Override
    public boolean moveTo(final long position, final int length) {
        validatePosition(position);
        return moveToInternal(position, length);
    }

    @Override
    public boolean moveBy(final long delta) {
        final long position = position();
        validatePositionState(position);
        validatePositionDelta(position, delta);
        return moveToInternal(position + delta, requestedLength);
    }

    @Override
    public boolean moveBy(final long delta, final int length) {
        final long position = position();
        validatePositionState(position);
        validatePositionDelta(position, delta);
        return moveToInternal(position + delta, length);
    }

    /**
     * Moves to the position mapping the requested length, which becomes the requested length of later moves if the
     * move succeeds; a failing move leaves this mapping as it was, including its requested length.
     */
    private boolean moveToInternal(final long position, final int length) {
        final int newLength = resolveLength(position, length);
        final RegionMetrics metrics = regionMetrics;
        final int newOffset = metrics.regionOffset(position);
        final long newRegionPosition = metrics.regionPosition(position);
        final long oldRegionPosition = mappedRegionPosition;
        if (newRegionPosition == oldRegionPosition) {
            if (newOffset != offset || newLength != buffer.capacity()) {
                initBufferAndOffset(mappedRegionAddress, newOffset, newLength);
            }
            requestedLength = length;
            return true;
        }
        final long oldRegionAddress = mappedRegionAddress;
        final long newRegionAddress = map(newRegionPosition);
        if (newRegionAddress > NULL_ADDRESS) {
            initBufferAndOffset(newRegionAddress, newOffset, newLength);
            requestedLength = length;
            unmap(oldRegionPosition, oldRegionAddress, false);
            return true;
        }
        return false;
    }

    @Override
    public void length(final int length) {
        final int newLength = resolveLength(position(), length);
        if (newLength != buffer.capacity()) {
            wrapBuffer(address(), newLength);
        }
        requestedLength = length;
    }

    @Override
    public void limit(final long limit) {
        final long position = position();
        validateLimit(position, limit, maxLengthAtCurrentPosition());
        final int newLength = (int) (limit - position);
        if (newLength != buffer.capacity()) {
            wrapBuffer(address(), newLength);
        }
        requestedLength = newLength;
    }

    /** Returns the length to map at the position: the max length for STRETCH_LENGTH, else the validated length */
    private int resolveLength(final long position, final int requested) {
        final int maxLength = maxLengthAtPosition(position);
        if (requested == STRETCH_LENGTH) {
            return maxLength;
        }
        validateMappingLength(position, requested, maxLength);
        return requested;
    }

    private void initBufferAndOffset(final long regionAddress, final int offset, final int length) {
        wrapBuffer(regionAddress + offset, length);
        this.offset = offset;
    }

    private void wrapBuffer(final long address, final int length) {
        buffer.wrap(address, length);
        if (readOnly) {
            buffer.makeReadOnly();
        }
    }

    private long map(final long regionPosition) {
        final long addr = regionMapper.map(regionPosition);
        if (addr > NULL_ADDRESS) {
            mappedRegionPosition = regionPosition;
            mappedRegionAddress = addr;
        }
        return addr;
    }

    private void unmap(final long mappedPos, final long mappedAddr, final boolean clearState) {
        if (mappedPos == NULL_POSITION) {
            assert mappedAddr == NULL_ADDRESS : "address cannot be mapped";
            return;
        }
        assert mappedAddr != NULL_ADDRESS : "address cannot be unmapped";
        if (clearState) {
            buffer.unwrap();
            mappedRegionPosition = NULL_POSITION;
            mappedRegionAddress = NULL_ADDRESS;
            offset = 0;
        }
        regionMapper.unmap(mappedPos, mappedAddr);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        unmap(mappedRegionPosition, mappedRegionAddress, true);
        closed = true;
        if (closeRegionMapperOnClose) {
            regionMapper.close();
        }
    }

    @Override
    public String toString() {
        return "DynamicMappingImpl:mapped=" + isMapped() +
                "|position=" + position() +
                "|length=" + length() +
                "|requestedLength=" + requestedLength +
                "|maxLengthAtCurrentPosition=" + maxLengthAtCurrentPosition() +
                "|regionSize=" + regionMetrics.regionSize() +
                "|overlapSize=" + regionMetrics.overlapSize() +
                "|accessMode=" + accessMode() +
                "|closed=" + isClosed();
    }
}
