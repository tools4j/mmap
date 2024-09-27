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
package org.tools4j.mmap.region.impl;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.tools4j.mmap.region.api.Region;
import org.tools4j.mmap.region.api.RegionMapper;
import org.tools4j.spockito.jupiter.TableSource;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;

/**
 * Unit test for {@link DefaultRegion}
 */
class DefaultRegionTest {

    private static final int nOfRegions = 4;

    @Mock
    private RegionMapper regionMapper;

    private Region region;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    @ParameterizedTest(name = "regionSize={0}, bytes={2}, startPosition={1}, expectedPosition={3}")
    @TableSource({
            "| regionSize | bytes | startPosition | expectedPosition |",
            "|       4096 |    1  |         0     |         -1       |",
            "|       4096 |    1  |         0     |       8195       |",
            "|       4096 |    2  |         0     |       8196       |",
            "|       4096 |    4  |         0     |       8192       |",
            "|       4096 |    8  |         0     |       8160       |",
            "|       4096 |    1  |       100     |         -1       |",
            "|       4096 |    1  |       100     |       8195       |",
            "|       4096 |    2  |       100     |       8196       |",
            "|       4096 |    4  |       100     |       8192       |",
            "|       4096 |    8  |       120     |       8160       |",
    })
    void findAndSearchLast4K(final int regionSize, final int bytes, final long startPosition, final long expectedPosition) {
        //given
        final long dataPosition = Math.max(expectedPosition + 2L * regionSize, 2L * regionSize);
        final int dataLength = (int)(regionSize * ceil(dataPosition, regionSize));

        //when + then
        runTest(regionSize, bytes, dataLength, startPosition, expectedPosition, true);
    }

    @ParameterizedTest(name = "regionSize={0}, bytes={1}, startPosition={2}, expectedPosition={3}")
    @MethodSource("permuteBytesStartExpected4")
    void findAndSearchLast4(final int regionSize, final int bytes, final long start, final long expected) {
        //given
        final int dataLength = nOfRegions * regionSize;

        //when + then
        runTest(regionSize, bytes, dataLength, start, expected, false);
    }

    @ParameterizedTest(name = "regionSize={0}, bytes={1}, startPosition={2}, expectedPosition={3}")
    @MethodSource("permuteBytesStartExpected8")
    void findAndSearchLast8(final int regionSize, final int bytes, final long start, final long expected) {
        //given
        final int dataLength = nOfRegions * regionSize;

        //when + then
        runTest(regionSize, bytes, dataLength, start, expected, false);
    }

    @ParameterizedTest(name = "regionSize={0}, bytes={1}, startPosition={2}, expectedPosition={3}")
    @MethodSource("permuteBytesStartExpected16")
    void findAndSearchLast16(final int regionSize, final int bytes, final long start, final long expected) {
        //given
        final int dataLength = nOfRegions * regionSize;

        //when + then
        runTest(regionSize, bytes, dataLength, start, expected, false);
    }

    private void runTest(final int regionSize,
                         final int bytes,
                         final int dataLength,
                         final long startPosition,
                         final long expectedPosition,
                         final boolean count) {
        //given
        final AtomicInteger counter = count ? new AtomicInteger() : null;
        final int expectedLinearCount = 1 + (int) ceil(Math.max(0, 1 + expectedPosition - startPosition), bytes);
        final int expectedLogCount = Math.max(1, 2 * (int) Math.ceil(log2(expectedLinearCount)));
        final Predicate<Region> matcher = count ? counter(matcher(bytes), counter) : matcher(bytes);
        final DirectBuffer dataBuffer = dataBuffer(bytes, expectedPosition, dataLength);
        when(regionMapper.regionSize()).thenReturn(regionSize);
        when(regionMapper.map(anyLong())).thenAnswer(mapRegion(dataLength, dataBuffer));
        region = new DefaultRegion(regionMapper);

        //when
        if (counter != null) counter.set(0);
        final boolean found1 = region.findLast(startPosition, bytes, matcher);

        //then
        if (expectedPosition >= 0 && startPosition <= expectedPosition) {
            assertTrue(found1);
            assertEquals(expectedPosition, region.position());
        } else {
            assertFalse(found1);
        }
        if (count) {
            assertEquals(expectedLinearCount, counter.get());
        }

        //when
        if (counter != null) counter.set(0);
        final boolean found2 = region.binarySearchLast(startPosition, bytes, matcher);

        //then
        if (expectedPosition >= 0 && startPosition <= expectedPosition) {
            assertTrue(found2);
            assertEquals(expectedPosition, region.position());
        } else {
            assertFalse(found2);
        }
        if (count) {
            assertEquals(expectedLogCount, counter.get());
        }
    }

    static Stream<Arguments> permuteBytesStartExpected4() {
        final int regionSize = 4;
        final long posMin = 0;
        final long posMax = nOfRegions * regionSize;
        final int[] bytes = {1, 2, 4};
        return permute(regionSize, bytes, posMin, posMax);
    }

    static Stream<Arguments> permuteBytesStartExpected8() {
        final int regionSize = 8;
        final long posMin = 0;
        final long posMax = nOfRegions * regionSize;
        final int[] bytes = {1, 2, 4, 8};
        return permute(regionSize, bytes, posMin, posMax);
    }

    static Stream<Arguments> permuteBytesStartExpected16() {
        final int regionSize = 16;
        final long posMin = 0;
        final long posMax = nOfRegions * regionSize;
        final int[] bytes = {1, 2, 4, 8};
        return permute(regionSize, bytes, posMin, posMax);
    }

    private static Stream<Arguments> permute(final int regionSize, final int[] bytes, final long start, final long end) {
        int count = 0;
        final Arguments[][] arguments = new Arguments[bytes.length][];
        for (int i = 0; i < bytes.length; i++) {
            final long[][] positions = permute(start, end, bytes[i]);
            arguments[i] = arguments(regionSize, bytes[i], positions).toArray(Arguments[]::new);
            count += arguments[i].length;
        }
        final Arguments[] flat = new Arguments[count];
        int index = 0;
        for (final Arguments[] argument : arguments) {
            System.arraycopy(argument, 0, flat, index, argument.length);
            index += argument.length;
        }
        return Stream.of(flat);
    }

    private static Stream<Arguments> arguments(final int regionSize, final int bytes, final long[][] positions) {
        return Stream.of(positions).map(pos -> Arguments.of(regionSize, bytes, pos[0], pos[1]));
    }

    private static long[][] permute(final long start, final long end, final int increment) {
        final int count = (int)ceil(end - start, increment);
        final int prod = (count * (count + 1)) / 2;
        final long[][] permutations = new long[prod][2];
        int index = 0;
        for (int i = 0; i < count; i++) {
            final long expectedPos = start + i*(long)increment;
            for (int j = 0; j <= i; j++) {
                final long startPos = start + j*(long)increment;
                permutations[index][0] = startPos;
                permutations[index][1] = expectedPos;
                index++;
            }
        }
        return permutations;
    }

    private static DirectBuffer dataBuffer(final int bytes, final long expectedPosition, final int dataLength) {
        final ByteBuffer buf = ByteBuffer.allocateDirect(dataLength);
        for (int i = 0; i < expectedPosition; i+= bytes) {
            buf.put(i + (i % bytes), (byte)1);
        }
        if (expectedPosition >= 0) {
            buf.put((int) expectedPosition, (byte) 1);
        }
        final DirectBuffer buffer = new UnsafeBuffer(buf);
        return buffer;
    }

    private static Answer<Long> mapRegion(final int dataLength, final DirectBuffer buffer) {
        return invocation -> {
            final long position = invocation.getArgument(0);
            if (position < dataLength) {
                return buffer.addressOffset() + position;
            }
            return NULL_ADDRESS;
        };
    }
    private static Predicate<Region> counter(final Predicate<? super Region> matcher, final AtomicInteger counter) {
        requireNonNull(matcher);
        requireNonNull(counter);
        return region -> {
            counter.incrementAndGet();
            return matcher.test(region);
        };
    }

    private static Predicate<Region> matcher(final int bytes) {
        final Predicate<Region> matcher;
        switch (bytes) {
            case 1:
                matcher = region -> region.buffer().getByte(0) != 0;
                break;
            case 2:
                matcher = region -> region.buffer().getShort(0) != 0;
                break;
            case 4:
                matcher = region -> region.buffer().getInt(0) != 0;
                break;
            case 8:
                matcher = region -> region.buffer().getLong(0) != 0;
                break;
            default:
                throw new IllegalArgumentException("Not supported: " + bytes + " bytes");
        }
        return matcher;
    }

    private static long ceil(final long size, final long unit) {
        return (size + unit - 1) / unit;
    }

    private static double log2(final double value) {
        return Math.log(value)/Math.log(2);
    }
}