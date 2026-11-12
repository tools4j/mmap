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
    /** The minimum message length, a message without payload. */
    public static final int MIN_MESSAGE_LENGTH = PAYLOAD_OFFSET;

    private final MutableDirectBuffer buffer = new UnsafeBuffer(0, 0);
    private final int payloadLength;

    /**
     * Creates a codec for messages of the given length.
     *
     * @param messageLength the message length in bytes, at least {@link #MIN_MESSAGE_LENGTH}
     * @throws IllegalArgumentException if the message length is less than {@link #MIN_MESSAGE_LENGTH}
     */
    public MessageCodec(final int messageLength) {
        if (messageLength < MIN_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Message length " + messageLength +
                    " cannot be less than " + MIN_MESSAGE_LENGTH);
        }
        this.payloadLength = messageLength - PAYLOAD_OFFSET;
    }

    /**
     * Returns the payload length.
     *
     * @return the payload length in bytes
     */
    public int payloadLength() {
        return payloadLength;
    }

    /**
     * Wraps the message in the given buffer, at most up to the message length of this codec.
     *
     * @param buffer the buffer containing the message
     * @param offset the message offset in the buffer
     * @param length the message length available in the buffer
     * @return this codec
     */
    public MessageCodec wrap(final DirectBuffer buffer, final int offset, final int length) {
        this.buffer.wrap(buffer, offset, Math.min(length, PAYLOAD_OFFSET + payloadLength));
        return this;
    }

    /**
     * Returns the send time of the message.
     *
     * @return the send time in nanoseconds, from {@link System#nanoTime()}
     */
    public long timestamp() {
        return buffer.getLong(TIMESTAMP_OFFSET);
    }

    /**
     * Returns the sender ID of the message.
     *
     * @return the ID of the sender of the message
     */
    public byte senderId() {
        return buffer.getByte(SENDER_ID_OFFSET);
    }

    /**
     * Copies the payload into the given array.
     *
     * @param dest the destination array, at least {@link #payloadLength()} long
     */
    public void getPayload(final byte[] dest) {
        buffer.getBytes(PAYLOAD_OFFSET, dest, 0, payloadLength);
    }

    /**
     * Sets the send time of the message.
     *
     * @param timestamp the send time in nanoseconds, from {@link System#nanoTime()}
     * @return this codec
     */
    public MessageCodec timestamp(final long timestamp) {
        buffer.putLong(TIMESTAMP_OFFSET, timestamp);
        return this;
    }

    /**
     * Sets the sender ID of the message.
     *
     * @param senderId the ID of the sender of the message
     * @return this codec
     */
    public MessageCodec senderId(final byte senderId) {
        buffer.putByte(SENDER_ID_OFFSET, senderId);
        return this;
    }

    /**
     * Copies the payload from the given array.
     *
     * @param src the source array, at least {@link #payloadLength()} long
     * @return this codec
     */
    public MessageCodec putPayload(final byte[] src) {
        buffer.putBytes(PAYLOAD_OFFSET, src, 0, payloadLength);
        return this;
    }
}
