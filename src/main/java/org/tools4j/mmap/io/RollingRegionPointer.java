/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 mmap (tools4j), Marco Terzer
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
 * Points to a position in a {@link MappedRegion} and automatically
 * rolls to next region when pointer is moved over region boundary.
 */
public final class RollingRegionPointer implements Closeable {

    private final MappedFile file;
    private MappedRegion region;
    private long offset;

    public RollingRegionPointer(final MappedFile file) {
        this.file = file;//null checked next
        this.region = file.reserveRegion(0);
        this.offset = 0;
    }

    public MappedRegion getRegion() {
        return region;
    }

    public long getOffset() {
        return offset;
    }

    public long getBytesRemaining() {
        return region.getSize() - offset;
    }

    public long getPosition() {
        return region.getPosition() + offset;
    }

    public long getAddress() {
        return region.getAddress(offset);
    }

    public long getAndIncrementAddress(final long add, final boolean padOnRoll) {
        final long off = offset;
        final long newOffset = off + add;
        final long regionSize = region.getSize();
        if (newOffset <= regionSize) {
            offset = newOffset;
            return region.getAddress(off);
        }
        if (padOnRoll) {
            pad(regionSize - off);
        }
        rollRegion();
        offset += add;
        return region.getAddress();
    }

    public void moveBy(final long step) {
        moveToPosition(getPosition() + step);
    }

    public void moveToPosition(final long position) {
        long newOffset = position - region.getPosition();
        while (newOffset >= region.getSize()) {
            rollRegion();
            newOffset = position - region.getPosition();
        }
        offset = newOffset;
    }

    private void pad(final long len) {
        if (len > 0) {
            UnsafeAccess.UNSAFE.setMemory(null, region.getAddress(), len, (byte) 0);
        }
    }

    private void rollRegion() {
        final MappedRegion previousRegion = region;
        region = file.reserveRegion(region.getIndex() + 1);
        file.releaseRegion(previousRegion);
        offset = 0;
    }

    public boolean isClosed() {
        return region == null;
    }

    public RollingRegionPointer ensureNotClosed() {
        if (region != null) {
            return this;
        }
        throw new RuntimeException("Pointer has already been closed");
    }

    @Override
    public void close() {
        if (region != null) {
            file.releaseRegion(region);
            region = null;
        }
    }
}
