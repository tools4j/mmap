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
package org.tools4j.mmap.perf;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Structure of the message:
 * <pre>
 * 0             8           9             9+payloadLength
 * +-------------+-----------+-------------+
 * | timestamp   | senderId  | payload     |
 * +-------------+-----------+-------------+
 * </pre>
 */
public final class MessageCodec {
    private static final int TIMESTAMP_OFFSET = 0;
    private static final int TIMESTAMP_LENGTH = 8;
    private static final int SENDER_ID_OFFSET = TIMESTAMP_OFFSET + TIMESTAMP_LENGTH;
    private static final int SENDER_ID_LENGTH = 1;
    private static final int PAYLOAD_OFFSET = SENDER_ID_OFFSET + SENDER_ID_LENGTH;
    public static final int MIN_MESSAGE_LENGTH = PAYLOAD_OFFSET;

    private final MutableDirectBuffer buffer = new UnsafeBuffer(0, 0);
    private final int payloadLength;

    public MessageCodec(final int messageLength) {
        if (messageLength < MIN_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Message length " + messageLength +
                    " cannot be less than " + MIN_MESSAGE_LENGTH);
        }
        this.payloadLength = messageLength - PAYLOAD_OFFSET;
    }

    public int payloadLength() {
        return payloadLength;
    }

    public MessageCodec wrap(final DirectBuffer buffer, final int offset, final int length) {
        this.buffer.wrap(buffer, offset, Math.min(length, PAYLOAD_OFFSET + payloadLength));
        return this;
    }

    public long timestamp() {
        return buffer.getLong(TIMESTAMP_OFFSET);
    }

    public byte senderId() {
        return buffer.getByte(SENDER_ID_OFFSET);
    }

    public void getPayload(final byte[] dest) {
        buffer.getBytes(PAYLOAD_OFFSET, dest, 0, payloadLength);
    }

    public MessageCodec timestamp(final long timestamp) {
        buffer.putLong(TIMESTAMP_OFFSET, timestamp);
        return this;
    }

    public MessageCodec senderId(final byte senderId) {
        buffer.putByte(SENDER_ID_OFFSET, senderId);
        return this;
    }

    public MessageCodec putPayload(final byte[] src) {
        buffer.putBytes(PAYLOAD_OFFSET, src, 0, payloadLength);
        return this;
    }
}
