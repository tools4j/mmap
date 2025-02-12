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

import org.tools4j.mmap.region.api.OffsetMapping;

import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.tools4j.mmap.dictionary.impl.Exceptions.mappingMoveException;
import static org.tools4j.mmap.dictionary.impl.Exceptions.mappingMoveToNextRegionException;

/**
 * Describes the layout of entries in the data file:
 * <p>
 * For keys, we have
 * <pre>

 0         1         2         3         4         5         6
 0 2 4 6 8 0 2 4 6 8 0 2 4 6 8 0 2 4 6 8 0 2 4 6 8 0 2 4 6 8 0 2 4
 +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 |                           Key Hash                            |
 +-------+-------+-------+-------+-------+-------+-------+-------+
 |         Payload Size          |       (unused padding)        |
 +-------+-------+-------+-------+-------+-------+-------+-------+
 |                       Key Payload Data                        |
 |                              ...                              |
 +=======+=======+=======+=======+=======+=======+=======+=======+

 * </pre>
 * <p>
 * For values, we have
 * <pre>

 0         1         2         3         4         5         6
 0 2 4 6 8 0 2 4 6 8 0 2 4 6 8 0 2 4 6 8 0 2 4 6 8 0 2 4 6 8 0 2 4
 +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 |         Payload Size          |       (unused padding)        |
 +-------+-------+-------+-------+-------+-------+-------+-------+
 |                      Value Payload Data                       |
 |                              ...                              |
 +=======+=======+=======+=======+=======+=======+=======+=======+

 * </pre>
 */
public enum DataDescriptor {
    ;
    private static final int PAYLOAD_SIZE_LENGTH = Integer.BYTES;
    
    public static final String NAME = "data";
    public static final int KEY_HASH_OFFSET = 0;
    public static final int KEY_HASH_LENGTH = Long.BYTES;
    public static final int KEY_PAYLOAD_SIZE_OFFSET = KEY_HASH_OFFSET + KEY_HASH_LENGTH;
    public static final int KEY_PAYLOAD_SIZE_LENGTH = PAYLOAD_SIZE_LENGTH;
    public static final int KEY_PADDING_OFFSET = KEY_PAYLOAD_SIZE_OFFSET + KEY_PAYLOAD_SIZE_LENGTH;
    public static final int KEY_PADDING_LENGTH = Integer.BYTES;
    public static final int KEY_PAYLOAD_OFFSET = KEY_PADDING_OFFSET + KEY_PADDING_LENGTH;

    public static final int VALUE_PAYLOAD_SIZE_OFFSET = 0;
    public static final int VALUE_PAYLOAD_SIZE_LENGTH = PAYLOAD_SIZE_LENGTH;
    public static final int VALUE_PADDING_OFFSET = VALUE_PAYLOAD_SIZE_OFFSET + VALUE_PAYLOAD_SIZE_LENGTH;
    public static final int VALUE_PADDING_LENGTH = Integer.BYTES;
    public static final int VALUE_PAYLOAD_OFFSET = VALUE_PADDING_OFFSET + VALUE_PADDING_LENGTH;

    public long moveAndGetKeyHash(final OffsetMapping mapping, final long keyPosition) {
        final long position = keyPosition + KEY_HASH_OFFSET;
        if (mapping.moveTo(position)) {
            return mapping.buffer().getLong(0, LITTLE_ENDIAN);
        }
        throw mappingMoveException(NAME, position);
    }

    public int moveToKeyPayload(final OffsetMapping mapping, final long keyPosition) {
        return moveToPayload(mapping, keyPosition + KEY_PAYLOAD_SIZE_OFFSET);
    }

    public int moveToValuePayload(final OffsetMapping mapping, final long valuePosition) {
        return moveToPayload(mapping, valuePosition + VALUE_PAYLOAD_OFFSET);
    }

    private int moveToPayload(final OffsetMapping mapping, final long position) {
        if (mapping.moveTo(position)) {
            ensureBytesAvailable(mapping, PAYLOAD_SIZE_LENGTH);
            final int payloadSize = mapping.buffer().getInt(0);
            mapping.moveBy(PAYLOAD_SIZE_LENGTH);
            ensureBytesAvailable(mapping, payloadSize);
            return payloadSize;
        }
        throw mappingMoveException(NAME, position);
    }

    private void ensureBytesAvailable(final OffsetMapping mapping, final int minBytesAvailable) {
        if (mapping.bytesAvailable() >= minBytesAvailable) {
            return;
        }
        final long position = mapping.position();
        if (mapping.moveToNextRegion() && mapping.bytesAvailable() >= minBytesAvailable) {
            return;
        }
        if (minBytesAvailable > mapping.regionSize()) {
            throw new IllegalStateException("Min bytes available " + minBytesAvailable + " exceeds region size " +
                    mapping.regionSize());
        }
        throw mappingMoveToNextRegionException(NAME, mapping.regionMetrics(), position);
    }
}
