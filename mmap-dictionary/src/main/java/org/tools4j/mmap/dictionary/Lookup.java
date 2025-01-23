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
package org.tools4j.mmap.dictionary;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import java.nio.ByteBuffer;

public interface Lookup extends AutoCloseable {
    boolean containsKey(byte[] key);
    boolean containsKey(byte[] key, int keyOffset, int keyLength);
    boolean containsKey(ByteBuffer key);
    boolean containsKey(ByteBuffer key, int keyLength);
    boolean containsKey(ByteBuffer key, int keyOffset, int keyLength);
    boolean containsKey(DirectBuffer key);
    boolean containsKey(DirectBuffer key, int keyOffset, int keyLength);

    KeySpecifier getting();
    LookupContext getting(byte[] key);
    LookupContext getting(byte[] key, int keyOffset, int keyLength);
    LookupContext getting(ByteBuffer key);
    LookupContext getting(ByteBuffer key, int keyLength);
    LookupContext getting(ByteBuffer key, int keyOffset, int keyLength);
    LookupContext getting(DirectBuffer key);
    LookupContext getting(DirectBuffer key, int keyOffset, int keyLength);

    /**
     * @return true if this appender is closed
     */
    boolean isClosed();

    /**
     * Closes the appender.
     */
    @Override
    void close();

    interface KeySpecifier extends AutoCloseable {
        MutableDirectBuffer keyBuffer();
        LookupContext commitKey(int keyLength);

        /**
         * Closes the lookup context and unwraps the buffer
         */
        @Override
        void close();
    }

}
