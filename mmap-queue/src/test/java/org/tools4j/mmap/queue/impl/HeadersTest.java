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
package org.tools4j.mmap.queue.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.tools4j.mmap.region.impl.StepBijection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.tools4j.mmap.queue.impl.Headers.BIJECTION_BLOCK_SIZE;
import static org.tools4j.mmap.queue.impl.Headers.BIJECTION_STEP_BW;
import static org.tools4j.mmap.queue.impl.Headers.BIJECTION_STEP_FW;

/**
 * Unit test for {@link Headers}.
 */
public class HeadersTest {
    @CsvSource(delimiter = '|', value = {
            "  0 |                  0 |                  256",
            "  1 |                  0 |                  257",
            "  2 |                  0 |                  258",
            "255 |                  0 |                  511",
            "  0 |                  8 |                  512",
            "  1 |                  8 |                  513",
            "  0 |                 16 |                  768",
            "  1 |                 16 |                  769",
            "  0 |                320 |                10496",
            "  1 |                320 |                10497",
            "  5 |       352368422632 |       11275789524485",
            "127 |       352368422632 |       11275789524607",
            "255 |       352368422632 |       11275789524735",
            "  0 | 288230376151711728 |  9223372036854775552",
            "  1 | 288230376151711728 |  9223372036854775553",
            "127 | 288230376151711728 |  9223372036854775679",
            "255 | 288230376151711728 |  9223372036854775807",
            "  0 | 288230376151711736 | -9223372036854775808",
            "  1 | 288230376151711736 | -9223372036854775807",
            "127 | 288230376151711736 | -9223372036854775681",
            "255 | 288230376151711736 | -9223372036854775553",
            "  0 | 576460752303423472 |                 -256",
            "  1 | 576460752303423472 |                 -255",
            "127 | 576460752303423472 |                 -129",
            "255 | 576460752303423472 |                   -1",
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

    @CsvSource(delimiter = '|', value = {
            " -1 |                   0 | false",
            "256 |                   0 | false",
            "  1 |                  -1 | true ",
            "  1 |                   1 | true ",
            "  1 |                   2 | true ",
            "  1 |                   4 | true ",
            "  1 |                   7 | true ",
            "  1 |  576460752303423488 | true ",
            "  1 | 9223372036854775807 | true ",
    })
    @ParameterizedTest(name = "[{index}]: appenderId={0}, payloadPosition={1}")
    public void invalidHeaderInput(final short appenderId, final long payloadPosition, final boolean invalidPosition) {
        assertThrows(AssertionError.class, () -> Headers.header(appenderId, payloadPosition));
        assertEquals(!invalidPosition, Headers.validPayloadPosition(payloadPosition));
        if (invalidPosition) {
            assertThrows(IllegalArgumentException.class, () -> Headers.validatePayloadPosition(payloadPosition));
        }
    }

//    @CsvSource(delimiter = '|', value = {
//            "0               | 0    ",
//            "1               | 248  ",
//            "2               | 496 ",
//            "3               | 744 ",
//            "4               | 992 ",
//            "62              | 3088",
//            "63              | 3336",
//            "64              | 3584",
//            "65              | 3832",
//            "126             | 2576",
//            "127             | 2824",
//            "128             | 3072",
//            "129             | 3320",
//            "4095            | 32520",
//            "4096            | 32768",
//            "4097            | 33016",
//            "4098            | 33264",
//            "4158            | 35856",
//            "4159            | 36104",
//            "4160            | 36352",
//            "4161            | 36600",
//            "4222            | 35344",
//            "4223            | 35592",
//            "4224            | 35840",
//            "4225            | 36088",
//    })
    @CsvSource(delimiter = '|', value = {
            "0               | 0    ",
            "1               | 248  ",
            "2               | 496 ",
            "3               | 744 ",
            "4               | 992 ",
            "62              | 3088",
            "63              | 3336",
            "64              | 3584",
            "65              | 3832",
            "66              | 4080",
            "67              | 232",
            "68              | 480",
            "126             | 2576",
            "127             | 2824",
            "128             | 3072",
            "129             | 3320",
            "130             | 3568",
            "131             | 3816",
            "132             | 4064",
            "133             | 216",
            "134             | 464",
            "4095            | 32520",
            "4096            | 32768",
            "4097            | 33016",
            "4098            | 33264",
            "4158            | 35856",
            "4159            | 36104",
            "4160            | 36352",
            "4161            | 36600",
            "4222            | 35344",
            "4223            | 35592",
            "4224            | 35840",
            "4225            | 36088",
            "4226            | 36336",
            "4227            | 36584",
            "4228            | 36832",
            "4229            | 32984",
            "4230            | 33232",
            "4231            | 33480",
    })
    @ParameterizedTest(name = "[{index}]: index={0}, position={1}")
    public void headerPositionAndIndex(final long index, final long position) {
        assertEquals(position, Headers.headerPositionForIndex(index));
        assertEquals(index, Headers.indexForHeaderPosition(position));
    }

    @Test
    public void headerStepBijectionParameters() {
        final StepBijection bijection = new StepBijection(BIJECTION_BLOCK_SIZE, BIJECTION_STEP_FW);

        assertEquals(BIJECTION_STEP_FW, bijection.step());
        assertEquals(BIJECTION_STEP_BW, bijection.back());
        assertEquals(BIJECTION_BLOCK_SIZE, bijection.block());
    }

}
