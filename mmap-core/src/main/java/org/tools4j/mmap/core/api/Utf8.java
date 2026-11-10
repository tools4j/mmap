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
package org.tools4j.mmap.core.api;

import java.util.Objects;

/**
 * UTF-8 utilities complementing the UTF-8 accessors of {@link MemoryBuffer} and {@link MutableMemoryBuffer}.
 */
public enum Utf8 {
    ;

    /**
     * Returns the number of bytes encoding the characters as UTF-8, as {@link MutableMemoryBuffer#putUtf8(int,
     * CharSequence)} writes them, with an unpaired surrogate encoded as one byte {@code '?'}.
     *
     * @param value the characters
     * @return the number of bytes
     */
    public static int length(final CharSequence value) {
        return length(value, 0, value.length());
    }

    /**
     * Returns the number of bytes encoding a range of characters as UTF-8, as
     * {@link MutableMemoryBuffer#putUtf8(int, CharSequence, int, int)} writes them, with an unpaired surrogate
     * encoded as one byte {@code '?'}; a surrogate pair split by the end of the range counts as unpaired.
     *
     * @param value the characters
     * @param offset the index of the first character
     * @param length the number of characters
     * @return the number of bytes
     */
    public static int length(final CharSequence value, final int offset, final int length) {
        Objects.checkFromIndexSize(offset, length, value.length());
        final int end = offset + length;
        int bytes = 0;
        for (int index = offset; index < end; index++) {
            final char ch = value.charAt(index);
            if (ch < 0x80) {
                bytes += 1;
            } else if (ch < 0x800) {
                bytes += 2;
            } else if (!Character.isSurrogate(ch)) {
                bytes += 3;
            } else if (Character.isHighSurrogate(ch) && index + 1 < end
                    && Character.isLowSurrogate(value.charAt(index + 1))) {
                bytes += 4;
                index++;
            } else {
                bytes += 1;
            }
        }
        return bytes;
    }
}
