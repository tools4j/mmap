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
package org.tools4j.mmap.queue.impl;

import org.junit.jupiter.params.ParameterizedTest;
import org.tools4j.spockito.jupiter.TableSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link Headers}.
 */
public class HeadersTest {
    @TableSource({
            "| appenderId |    payloadPosition |               header |",
            "|------------|--------------------|----------------------|",
            "|          0 |                  0 |                  256 |",
            "|          1 |                  0 |                  257 |",
            "|          2 |                  0 |                  258 |",
            "|        255 |                  0 |                  511 |",
            "|          0 |                  8 |                  512 |",
            "|          1 |                  8 |                  513 |",
            "|          0 |                 16 |                  768 |",
            "|          1 |                 16 |                  769 |",
            "|          0 |                320 |                10496 |",
            "|          1 |                320 |                10497 |",
            "|          5 |       352368422632 |       11275789524485 |",
            "|        127 |       352368422632 |       11275789524607 |",
            "|        255 |       352368422632 |       11275789524735 |",
            "|          0 | 288230376151711728 |  9223372036854775552 |",
            "|          1 | 288230376151711728 |  9223372036854775553 |",
            "|        127 | 288230376151711728 |  9223372036854775679 |",
            "|        255 | 288230376151711728 |  9223372036854775807 |",
            "|          0 | 288230376151711736 | -9223372036854775808 |",
            "|          1 | 288230376151711736 | -9223372036854775807 |",
            "|        127 | 288230376151711736 | -9223372036854775681 |",
            "|        255 | 288230376151711736 | -9223372036854775553 |",
            "|          0 | 576460752303423472 |                 -256 |",
            "|          1 | 576460752303423472 |                 -255 |",
            "|        127 | 576460752303423472 |                 -129 |",
            "|        255 | 576460752303423472 |                   -1 |",
    })
    @ParameterizedTest(name = "[{index}]: appenderId={0}, payloadPosition={1}, header={2}")
    public void header_appenderId_payloadPosition(final short appenderId, final long payloadPosition, final long header) {
        System.out.println("header = " + header);
        System.out.println("appenderId = " + (header >>> 56));

        //header
        assertEquals(header, Headers.header(appenderId, payloadPosition));

        //appenderId
        assertEquals(appenderId, Headers.appenderId(header));

        //payloadPosition
        assertEquals(payloadPosition, Headers.payloadPosition(header));
    }

    @TableSource({
            "| appenderId |     payloadPosition | invalidPosition |",
            "|------------|---------------------|-----------------|",
            "|         -1 |                   0 |       false     |",
            "|        256 |                   0 |       false     |",
            "|          1 |                  -1 |       true      |",
            "|          1 |                   1 |       true      |",
            "|          1 |                   2 |       true      |",
            "|          1 |                   4 |       true      |",
            "|          1 |                   7 |       true      |",
            "|          1 |  576460752303423488 |       true      |",
            "|          1 | 9223372036854775807 |       true      |",
    })
    @ParameterizedTest(name = "[{index}]: appenderId={0}, payloadPosition={1}")
    public void invalidHeaderInput(final short appenderId, final long payloadPosition, final boolean invalidPosition) {
        assertThrows(AssertionError.class, () -> Headers.header(appenderId, payloadPosition));
        assertEquals(!invalidPosition, Headers.validPayloadPosition(payloadPosition));
        if (invalidPosition) {
            assertThrows(IllegalArgumentException.class, () -> Headers.validatePayloadPosition(payloadPosition));
        }
    }

    @TableSource({
            "| index           | position       |",
            "|-----------------|----------------|",
            "| 0               | 0              |",
            "| 1               | 512            |",
            "| 2               | 1024           |",
            "| 3               | 1536           |",
            "| 4               | 2048           |",
            "| 62              | 31744          |",
            "| 63              | 32256          |",
            "| 64              | 8              |",
            "| 65              | 520            |",
            "| 126             | 31752          |",
            "| 127             | 32264          |",
            "| 128             | 16             |",
            "| 129             | 528            |",
            "| 4095            | 32760          |",
            "| 4096            | 32768          |",
            "| 4097            | 33280          |",
            "| 4098            | 33792          |",
            "| 4158            | 64512          |",
            "| 4159            | 65024          |",
            "| 4160            | 32776          |",
            "| 4161            | 33288          |",
            "| 4222            | 64520          |",
            "| 4223            | 65032          |",
            "| 4224            | 32784          |",
            "| 4225            | 33296          |",
    })
    @ParameterizedTest(name = "[{index}]: index={0}, position={1}")
    public void headerPositionAndIndex(final long index, final long position) {
        assertEquals(position, Headers.headerPositionForIndex(index));
        assertEquals(index, Headers.indexForHeaderPosition(position));
    }

}
