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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.tools4j.spockito.jupiter.TableSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HeaderCodecTest {

    @TableSource({
            "| appenderId      | payloadPosition | header              |",
            "|-----------------|-----------------|---------------------|",
            "| 0               | 0               | 0                   |",
            "| 1               | 0               | 72057594037927936   |",
            "| 1               | 324             | 72057594037928260   |",
            "| 5               | 352368422634    | 360288322558062314  |",
            "| 127             | 352368422634    | 9151314795185270506 |",
            "| 255             | 352368422634    | -72057241669505302  |",
    })
    @ParameterizedTest(name = "[{index}]: appenderId={0}, payloadPosition={1}, header={2}")
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
    public void initialPayloadPosition() {
        assertEquals(64, HeaderCodec.initialPayloadPosition());
    }
}
