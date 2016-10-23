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
package org.tools4j.mmap.queue.util;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Structure of the message entry:
 * 0             8          9             10           10+payloadLength
 * +-------------+----------+-------------+------------+
 * | timestamp   | terminal | publisherId | payload    |
 * +-------------+----------+-------------+------------+
 */
public class MessageCodec {
    private static final int TIMESTAMP_OFFSET = 0;
    private static final int TIMESTAMP_LENGTH = 8;
    private static final int TERMINAL_OFFSET = TIMESTAMP_OFFSET + TIMESTAMP_LENGTH;
    private static final int TERMINAL_LENGTH = 1;
    private static final int PUBLISHER_ID_OFFSET = TERMINAL_OFFSET + TERMINAL_LENGTH;
    private static final int PUBLISHER_ID_LENGTH = 1;
    private static final int PAYLOAD_OFFSET = PUBLISHER_ID_OFFSET + PUBLISHER_ID_LENGTH;

    private final MutableDirectBuffer buffer = new UnsafeBuffer();
    private final int payloadLength;

    public int encodedLength() {
        return PAYLOAD_OFFSET + payloadLength;
    }

    public static int headerLength() {
        return PAYLOAD_OFFSET;
    }

    public MessageCodec(final int messageLength) {
        if (messageLength < headerLength()) {
            throw new IllegalArgumentException(
                    "Message length [" + messageLength + "] cannot be less than the header length [" + headerLength() + "]");
        }
        this.payloadLength = messageLength - headerLength();
    }

    public MessageCodec wrap(final DirectBuffer buffer) {
        this.buffer.wrap(buffer, 0, encodedLength());
        return this;
    }

    public long timestamp() {
        return buffer.getLong(TIMESTAMP_OFFSET);
    }

    public boolean terminal() {
        return buffer.getByte(TERMINAL_OFFSET) == 1;
    }
    public byte publisherId() {
        return buffer.getByte(PUBLISHER_ID_OFFSET);
    }

    public int payloadLength() {
        return payloadLength;
    }

    public void getPayload(byte[] dest) {
        buffer.getBytes(PAYLOAD_OFFSET, dest, 0, payloadLength);
    }

    public MessageCodec timestamp(final long timestamp) {
        buffer.putLong(TIMESTAMP_OFFSET, timestamp);
        return this;
    }

    public MessageCodec terminal(final boolean terminal) {
        buffer.putByte(TERMINAL_OFFSET, (byte)(terminal ? 1 : 0));
        return this;
    }

    public MessageCodec publisherId(final byte publisherId) {
        buffer.putByte(PUBLISHER_ID_OFFSET, publisherId);
        return this;
    }

    public MessageCodec putPayload(final byte[] src) {
        buffer.putBytes(PAYLOAD_OFFSET, src, 0, payloadLength);
        return this;
    }

    @Override
    public String toString() {
        return "TestMessage{" +
                " timestamp=" + timestamp() +
                " terminal=" + terminal() +
                " publisherId=" + publisherId() +
                '}';
    }
}
