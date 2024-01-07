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

import org.tools4j.mmap.region.impl.Word;

final class HeaderCodec {
    private static final long PAYLOAD_POSITION_MASK = 0xFFFFFFFFFFFFFFL;
    private static final int APPENDER_ID_BITS = Long.SIZE - 8;
    private static final long INITIAL_PAYLOAD_POSITION = 64;
    public static final Word HEADER_WORD = new Word(8, 64);


    public static short appenderId(final long header) {
        return (short) (header >>> APPENDER_ID_BITS);
    }

    public static long payloadPosition(final long header) {
        return header & PAYLOAD_POSITION_MASK;
    }

    public static long header(final short appenderId, final long payloadPosition) {
        return  (((long) appenderId) << APPENDER_ID_BITS) | payloadPosition;
    }

    public static long initialPayloadPosition() {
        return INITIAL_PAYLOAD_POSITION;
    }

}
