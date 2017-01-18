/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2017 mmap (tools4j), Marco Terzer
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
package org.tools4j.mmap.io;

import java.io.Closeable;

/**
 * Points to a position in a {@link MappedFile}. Only a region of the
 * file is memory mapped. The pointer automatically maps the next region
 * and unmaps the previous region when the pointer is moved over the region
 * boundaries.
 */
public final class MappedFilePointer implements Closeable {

    private final MappedRegion mappedRegion;
    private long offset;
    private boolean unmapRegionOnRoll;

    public MappedFilePointer(final MappedFile file, final MappedRegion.Mode mode) {
        this.mappedRegion = new MappedRegion(file, mode).map(0);
        this.offset = 0;
        this.unmapRegionOnRoll = true;
    }

    public long getOffset() {
        return offset;
    }

    public long getBytesRemaining() {
        return mappedRegion.getSize() - offset;
    }

    public long getPosition() {
        return mappedRegion.getPosition(offset);
    }

    public long getAddress() {
        return mappedRegion.getAddress(offset);
    }

    public long getAndIncrementAddress(final long add, final boolean padOnRoll) {
        final long off = offset;
        final long newOffset = off + add;
        final long regionSize = mappedRegion.getSize();
        if (newOffset <= regionSize) {
            offset = newOffset;
            return mappedRegion.getAddress(off);
        }
        if (padOnRoll) {
            pad(regionSize - off);
        }
        rollRegion();
        offset += add;
        return mappedRegion.getAddress();
    }

    public void moveBy(final long step) {
        moveToPosition(getPosition() + step);
    }

    public void moveToPosition(final long position) {
        long newOffset = position - mappedRegion.getPosition();
        while (newOffset >= mappedRegion.getSize()) {
            rollRegion();
            newOffset = position - mappedRegion.getPosition();
        }
        offset = newOffset;
    }

    private void pad(final long len) {
        if (len > 0) {
            UnsafeAccess.UNSAFE.setMemory(null, mappedRegion.getAddress(offset), len, (byte) 0);
        }
    }

    private void rollRegion() {
        final long position = mappedRegion.getPosition();
        if (unmapRegionOnRoll) {
            mappedRegion.unmap();
        } else {
            mappedRegion.unmapExternal();
            unmapRegionOnRoll = true;
        }
        mappedRegion.map(position + mappedRegion.getSize());
        offset = 0;
    }

    public boolean isClosed() {
        return !mappedRegion.isMapped();
    }

    public MappedFilePointer ensureNotClosed() {
        if (mappedRegion.isMapped()) {
            return this;
        }
        throw new RuntimeException("Pointer has already been closed");
    }

    public long unmapRegionOnRoll(final boolean unmapRegionOnRoll) {
        this.unmapRegionOnRoll = unmapRegionOnRoll;
        return mappedRegion.getAddress();
    }

    @Override
    public void close() {
        if (mappedRegion.isMapped()) {
            mappedRegion.unmap();
            offset = 0;
        }
    }
}
