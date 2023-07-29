/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2023 tools4j.org (Marco Terzer, Anton Anufriev)
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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.tools4j.spockito.Spockito;

import static org.junit.Assert.assertEquals;

@RunWith(Spockito.class)
public class HeaderCodecTest {

    @Test
    @Spockito.Unroll({
            "| appenderId      | payloadPosition | header              |",
            "|-----------------|-----------------|---------------------|",
            "| 0               | 0               | 0                   |",
            "| 1               | 0               | 72057594037927936   |",
            "| 1               | 324             | 72057594037928260   |",
            "| 5               | 352368422634    | 360288322558062314  |",
            "| 127             | 352368422634    | 9151314795185270506 |",
            "| 255             | 352368422634    | -72057241669505302  |",
    })
    @Spockito.Name("[{row}]: appenderId={0}, payloadPosition={1}, header={2}")
    public void header_appenderId_payloadPosition(final short appenderId, final long payloadPosition, final long header) {
        System.out.println("header = " + header);
        System.out.println("appenderId = " + (header >>> 56));

        //header
        assertEquals(header, HeaderCodec.header(appenderId, payloadPosition));

        //appenderId
        assertEquals(appenderId, HeaderCodec.appenderId(header));

        //payloadPosition
        assertEquals(payloadPosition, HeaderCodec.payloadPosition(header));
    }

    @Test
    @Spockito.Unroll({
            "| index           | position       |",
            "|-----------------|----------------|",
            "| 0               | 0              |",
            "| 1               | 64             |",
            "| 2               | 128            |",
            "| 3               | 192            |",
            "| 4               | 256            |",
            "| 5               | 320            |",
            "| 6               | 384            |",
            "| 7               | 448            |",
            "| 8               | 8              |",
            "| 9               | 72             |",
            "| 10              | 136            |",
            "| 11              | 200            |",
            "| 12              | 264            |",
            "| 13              | 328            |",
            "| 14              | 392            |",
            "| 15              | 456            |",
            "| 16              | 16             |",
            "| 17              | 80             |",
    })
    @Spockito.Name("[{row}]: index={0}, position={1}")
    public void headerPosition(final long index, final long position) {
        assertEquals(position, HeaderCodec.headerPosition(index));
    }

    @Test
    public void length() {
        assertEquals(8, HeaderCodec.length());
    }

    @Test
    public void initialPayloadPosition() {
        assertEquals(64, HeaderCodec.initialPayloadPosition());
    }
}
