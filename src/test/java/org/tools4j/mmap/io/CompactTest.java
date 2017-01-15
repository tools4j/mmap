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

import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.nio.ByteBuffer;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link Compact}
 */
@RunWith(MockitoJUnitRunner.class)
public class CompactTest {

    @Mock
    private MessageWriter messageWriter;
    @Mock
    private MessageReader messageReader;

    private ByteBuffer byteBuffer = ByteBuffer.allocate(9);

    @Before
    public void beforeEach() {
        when(messageWriter.putInt8(anyInt())).thenAnswer(invocation -> {
            final int value = invocation.getArgumentAt(0, Integer.class);
            byteBuffer.put((byte)value);
            return null;
        });
        when(messageWriter.putInt32(anyInt())).thenAnswer(invocation -> {
            final int value = invocation.getArgumentAt(0, Integer.class);
            byteBuffer.putInt(value);
            return null;
        });
        when(messageWriter.putInt64(anyLong())).thenAnswer(invocation -> {
            final long value = invocation.getArgumentAt(0, Long.class);
            byteBuffer.putLong(value);
            return null;
        });
        when(messageReader.getInt8()).thenAnswer(invocation -> byteBuffer.get());
        when(messageReader.getInt8AsInt()).thenAnswer(invocation -> 0xff & byteBuffer.get());
        when(messageReader.getInt32()).thenAnswer(invocation -> byteBuffer.getInt());
        when(messageReader.getInt64()).thenAnswer(invocation -> byteBuffer.getLong());
        byteBuffer.mark();
    }

    @Test
    public void writeSmallInts() throws Exception {
        for (int value = 0; value < (1<<10); value++) {
            byteBuffer.reset();
            Compact.putUnsignedInt(value, messageWriter);
            Assertions.assertThat(byteBuffer.position()).as("length: %s", value).isEqualTo(value < 128 ? 1 : 2);
            byteBuffer.reset();
            final int read = Compact.getUnsignedInt(messageReader);
            Assertions.assertThat(read).isEqualTo(value);
        }
    }

    @Test
    public void writeSmallLongs() throws Exception {
        for (int value = 0; value < (1<<10); value++) {
            byteBuffer.reset();
            Compact.putUnsignedLong(value, messageWriter);
            Assertions.assertThat(byteBuffer.position()).as("length: %s", value).isEqualTo(value < 128 ? 1 : 2);
            byteBuffer.reset();
            final long read = Compact.getUnsignedLong(messageReader);
            Assertions.assertThat(read).isEqualTo(value);
        }
    }

    @Test
    public void writeSpecialIntValues() throws Exception {
        final int[] specials = {Integer.MIN_VALUE, Short.MIN_VALUE, Byte.MIN_VALUE, -1, 0, 1, Byte.MAX_VALUE, Short.MAX_VALUE, Integer.MAX_VALUE};
        for (final int special : specials) {
            for (int i = -2; i <= 2; i++) {
                final int value = special + i;
                byteBuffer.reset();
                Compact.putUnsignedInt(value, messageWriter);
                byteBuffer.reset();
                final int read = Compact.getUnsignedInt(messageReader);
                Assertions.assertThat(read).isEqualTo(value);
            }
        }
    }

    @Test
    public void writeSpecialLongValues() throws Exception {
        final long[] specials = {Long.MIN_VALUE, Integer.MIN_VALUE, Short.MIN_VALUE, Byte.MIN_VALUE, -1, 0, 1, Byte.MAX_VALUE, Short.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE};
        for (final long special : specials) {
            for (int i = -2; i <= 2; i++) {
                final long value = special + i;
                byteBuffer.reset();
                Compact.putUnsignedLong(value, messageWriter);
                byteBuffer.reset();
                final long read = Compact.getUnsignedLong(messageReader);
                Assertions.assertThat(read).isEqualTo(value);
            }
        }
    }

    @Test
    public void assertIntValueLengths() throws Exception {
        final int[][] specials = {
                {},
                {0, 1, 1 << 6, (1 << 7) - 1},
                {1 << 7, 129, 1 << 13, (1 << 14) - 1},
                {},
                {1 << 14, 1 << 15, 1 << 28, (1 << 29) - 1},
                {1 << 29, 1 << 30, 1 << 31, Integer.MAX_VALUE, Integer.MIN_VALUE, -3, -2, -1},
        };
        for (int len = 0; len < specials.length; len++) {
            for (final int value : specials[len]) {
                byteBuffer.reset();
                Compact.putUnsignedInt(value, messageWriter);
                Assertions.assertThat(byteBuffer.position()).as("length: %s", value).isEqualTo(len);
                byteBuffer.reset();
                final long read = Compact.getUnsignedInt(messageReader);
                Assertions.assertThat(read).isEqualTo(value);
            }
        }
    }

    @Test
    public void assertLongValueLengths() throws Exception {
        final long[][] specials = {
                {},
                {0, 1, 1<<6, (1<<7)-1},
                {1<<7, 129, 1<<13, (1<<14)-1},
                {},
                {1<<14, 1<<15, 1<<28, (1<<29)-1},
                {},
                {},
                {},
                {1L<<29, 1L<<30, 1L<<31, Integer.MAX_VALUE, 1L<<32, 1L<<59, (1L<<60)-1},
                {1L<<60, 1L<<61, Long.MAX_VALUE, Long.MIN_VALUE, -3, -2, -1},
        };
        for (int len = 0; len < specials.length; len++) {
            for (final long value : specials[len]) {
                byteBuffer.reset();
                Compact.putUnsignedLong(value, messageWriter);
                Assertions.assertThat(byteBuffer.position()).as("length: %s", value).isEqualTo(len);
                byteBuffer.reset();
                final long read = Compact.getUnsignedLong(messageReader);
                Assertions.assertThat(read).isEqualTo(value);
            }
        }
    }
}