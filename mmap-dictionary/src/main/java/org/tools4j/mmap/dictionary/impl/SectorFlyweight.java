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
package org.tools4j.mmap.dictionary.impl;

import org.agrona.concurrent.AtomicBuffer;
import org.tools4j.mmap.region.api.Mapping;
import org.tools4j.mmap.region.api.OffsetMapping;

import static java.util.Objects.requireNonNull;

public class SectorFlyweight {
    private Mapping indexMapping;
    private OffsetMapping headerMapping;

    private int sector;
    private long startPosition;
    private int slotsInBits;
    private long slots;

    public void wrapFirstSector(final Mapping indexMapping, final OffsetMapping headerMapping) {
        final int sector = IndexDescriptor.firstSectorIndexVolatile(indexMapping.buffer());
        wrapSector(indexMapping, headerMapping, sector);
    }

    public void wrapSector(final Mapping indexMapping, final OffsetMapping headerMapping, final int sector) {
        this.indexMapping = requireNonNull(indexMapping);
        this.headerMapping = requireNonNull(headerMapping);
        this.startPosition = IndexDescriptor.sectorStartPosition(indexMapping.buffer(), sector);
        this.slotsInBits = IndexDescriptor.sectorSlotsInBits(indexMapping.buffer(), sector);
        this.slots = 1L << slotsInBits;
    }

    public int sector() {
        return sector;
    }

    public int nextSectorVolatile() {
        return IndexDescriptor.nextSectorVolatile(indexMapping.buffer(), sector);
    }

    public int nextSectorInitAndGet(final boolean performChecks) {
        final AtomicBuffer buffer = indexMapping.buffer();
        final int nextSector = sector + 1;
        final int nextSlotsInBits = slotsInBits + 1;
        final long nextStartPosition = startPosition + SectorDescriptor.length(slots);
        if (IndexDescriptor.sectorInit(buffer, nextSector, nextStartPosition, nextSlotsInBits, performChecks)) {
            return nextSector;
        }
        return nextSectorVolatile();
    }

    public int slotsInBits() {
        return slotsInBits;
    }

    public long slots() {
        return slots;
    }

    public long slot(final long hash) {
        return SectorDescriptor.slot(hash, slots);
    }

    public long moveToAndGetKeyHeaderVolatile(final long slot) {
        return SectorDescriptor.moveToAndGetKeyHeaderVolatile(headerMapping, startPosition, slot);
    }

    public long moveToAndGetValueHeaderVolatile(final long slot) {
        return SectorDescriptor.moveToAndGetValueHeaderVolatile(headerMapping, startPosition, slot);
    }

    public long moveToAndGetHash(final long slot) {
        return SectorDescriptor.moveToAndGetHash(headerMapping, startPosition, slots, slot);
    }

    public long activeSlotsVolatile() {
        return IndexDescriptor.sectorActiveSlotsVolatile(indexMapping.buffer(), sector);
    }

    public long activeSlotsIncrementAndGet() {
        return IndexDescriptor.sectorActiveSlotsIncrementAndGet(indexMapping.buffer(), sector);
    }

    public long usedSlotsVolatile() {
        return IndexDescriptor.sectorUsedSlotsVolatile(indexMapping.buffer(), sector);
    }

    public long usedSlotsIncrementAndGet() {
        return IndexDescriptor.sectorUsedSlotsIncrementAndGet(indexMapping.buffer(), sector);
    }

    public long usedSlotsDecrementAndGet() {
        return IndexDescriptor.sectorUsedSlotsDecrementAndGet(indexMapping.buffer(), sector);
    }

    @Override
    public String toString() {
        return "SectorFlyweight" +
                ":sector=" + sector +
                "|startPosition=" + startPosition +
                "|slotsInBits=" + slotsInBits +
                "|slots=" + slots +
                "|activeSlots=" + activeSlotsVolatile() +
                "|usedSlots=" + usedSlotsVolatile();
    }
}
