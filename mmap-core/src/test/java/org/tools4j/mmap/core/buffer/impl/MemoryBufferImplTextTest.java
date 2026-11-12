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
package org.tools4j.mmap.core.buffer.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.tools4j.mmap.core.buffer.MemoryBuffers;
import org.tools4j.mmap.core.buffer.MutableMemoryBuffer;
import org.tools4j.mmap.core.text.Utf8;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Random;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for the ASCII and UTF-8 text accessors of {@link MemoryBufferImpl}, created through {@link MemoryBuffers}.
 */
class MemoryBufferImplTextTest {

    private static final int CAPACITY = 256;
    private static final String SMILE = "😀";

    private static final class Chars implements Appendable {
        final StringBuilder text = new StringBuilder();

        @Override
        public Appendable append(final CharSequence chars) {
            text.append(chars);
            return this;
        }

        @Override
        public Appendable append(final CharSequence chars, final int start, final int end) {
            text.append(chars, start, end);
            return this;
        }

        @Override
        public Appendable append(final char ch) {
            text.append(ch);
            return this;
        }
    }

    private static String randomText(final Random random, final int maxLength) {
        final StringBuilder text = new StringBuilder();
        final int length = random.nextInt(maxLength + 1);
        for (int i = 0; i < length; i++) {
            switch (random.nextInt(5)) {
                case 0:
                    text.append((char) random.nextInt(0x80));
                    break;
                case 1:
                    text.append((char) random.nextInt(0x800));
                    break;
                case 2:
                    text.append(SMILE);
                    break;
                case 3:
                    text.append((char) (Character.MIN_SURROGATE + random.nextInt(0x800)));
                    break;
                default:
                    text.append((char) random.nextInt(0x10000));
                    break;
            }
        }
        return text.toString();
    }

    @ParameterizedTest
    @EnumSource(TestMemory.class)
    void asciiTextRoundTrips(final TestMemory memory) {
        final MutableMemoryBuffer buffer = memory.create(CAPACITY);
        assertEquals(5, buffer.putTextAscii(3, "Hello"));
        assertEquals("Hello", buffer.getTextAscii(3, 5));
        final StringBuilder builder = new StringBuilder("> ");
        assertEquals(5, buffer.getTextAscii(3, 5, builder));
        assertEquals("> Hello", builder.toString());
        final Chars chars = new Chars();
        assertEquals(3, buffer.getTextAscii(4, 3, chars));
        assertEquals("ell", chars.text.toString());
        assertEquals(3, buffer.putTextAscii(10, "xaéb" + SMILE, 1, 3));
        assertEquals("a?b", buffer.getTextAscii(10, 3));
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.getTextAscii(CAPACITY - 2, 3));
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.putTextAscii(0, "abc", 2, 2));
    }

    @ParameterizedTest
    @EnumSource(TestMemory.class)
    void utf8TextIsEncodedAndDecodedAsByTheJdk(final TestMemory memory) {
        final MutableMemoryBuffer buffer = memory.create(CAPACITY);
        final Random random = new Random(memory.ordinal());
        for (int round = 0; round < 2_000; round++) {
            final String text = randomText(random, 40);
            final byte[] expected = text.getBytes(UTF_8);
            assertEquals(expected.length, buffer.putTextUtf8(7, text));
            assertEquals(Utf8.length(text), expected.length);
            final byte[] actual = new byte[expected.length];
            buffer.getBytes(7, actual);
            assertArrayEquals(expected, actual, text);
            final String decoded = new String(expected, UTF_8);
            assertEquals(decoded, buffer.getTextUtf8(7, expected.length));
            final StringBuilder builder = new StringBuilder();
            assertEquals(expected.length, buffer.getTextUtf8(7, expected.length, builder));
            assertEquals(decoded, builder.toString());
            final Chars chars = new Chars();
            assertEquals(expected.length, buffer.getTextUtf8(7, expected.length, chars));
            assertEquals(decoded, chars.text.toString());
        }
    }

    @Test
    void arbitraryBytesAreDecodedAsByTheJdk() {
        final MutableMemoryBuffer buffer = MemoryBuffers.allocate(CAPACITY);
        final Random random = new Random(42);
        final byte[] bytes = new byte[24];
        for (int round = 0; round < 50_000; round++) {
            for (int i = 0; i < bytes.length; i++) {
                //mostly bytes relevant to UTF-8 sequences, so that well-formed and ill-formed ones occur often
                bytes[i] = (byte) (random.nextInt(4) == 0 ? random.nextInt(0x80) : 0x80 + random.nextInt(0x80));
            }
            final int length = random.nextInt(bytes.length + 1);
            buffer.putBytes(0, bytes, 0, length);
            final String expected = new String(bytes, 0, length, UTF_8);
            assertEquals(expected, buffer.getTextUtf8(0, length), () -> Arrays.toString(Arrays.copyOf(bytes, length)));

            final StringBuilder resumed = new StringBuilder();
            final int read = buffer.getTextUtf8(0, length, resumed);
            assertEquals(new String(bytes, 0, read, UTF_8), resumed.toString());
            if (read < length) {
                assertTrue(length - read <= 3, "at most an incomplete sequence is left");
                assertEquals("\uFFFD", new String(bytes, read, length - read, UTF_8), "incomplete sequence");
            }
        }
    }

    @Test
    void utf8DecodingCanBeResumedChunkByChunk() {
        final MutableMemoryBuffer buffer = MemoryBuffers.allocateDirect(CAPACITY);
        final String text = "aé€" + SMILE + "z" + SMILE + "߿";
        final int length = buffer.putTextUtf8(0, text);
        for (int split = 0; split <= length; split++) {
            final StringBuilder builder = new StringBuilder();
            final int first = buffer.getTextUtf8(0, split, builder);
            assertTrue(split - first >= 0 && split - first <= 3, "unread bytes of a cut character");
            final int second = buffer.getTextUtf8(first, length - first, builder);
            assertEquals(length, first + second);
            assertEquals(text, builder.toString(), "split at " + split);
        }
        final StringBuilder cut = new StringBuilder();
        assertEquals(1, buffer.getTextUtf8(0, 2, cut));
        assertEquals("a", cut.toString());
        assertEquals("a�", buffer.getTextUtf8(0, 2));
    }

    @Test
    void utf8EncodingFailsWithoutSideEffects() {
        final MutableMemoryBuffer buffer = MemoryBuffers.allocate(16);
        buffer.setMemory(0, 16, (byte) 0x55);
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.putTextUtf8(10, "abc" + SMILE));
        for (int i = 0; i < 16; i++) {
            assertEquals(0x55, buffer.getByte(i));
        }
        assertEquals(2, buffer.putTextUtf8(14, "x" + SMILE + "y", 2, 2));
        assertEquals('?', buffer.getByte(14));
        assertEquals('y', buffer.getByte(15));
    }

    @Test
    void appendableErrorsAreRethrownUnchecked() {
        final MutableMemoryBuffer buffer = MemoryBuffers.allocate(16);
        buffer.putTextAscii(0, "abc");
        final Appendable failing = new Appendable() {
            @Override
            public Appendable append(final CharSequence chars) throws IOException {
                throw new IOException("test");
            }

            @Override
            public Appendable append(final CharSequence chars, final int start, final int end) throws IOException {
                throw new IOException("test");
            }

            @Override
            public Appendable append(final char ch) throws IOException {
                throw new IOException("test");
            }
        };
        assertThrows(UncheckedIOException.class, () -> buffer.getTextAscii(0, 3, failing));
        assertThrows(UncheckedIOException.class, () -> buffer.getTextUtf8(0, 3, failing));
    }
}
