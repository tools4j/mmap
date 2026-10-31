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
package org.tools4j.mmap.mapping.impl;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tools4j.mmap.mapping.api.DynamicMapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link DynamicMappings} edge cases: invalid increments, no match, and positions close to
 * {@link Long#MAX_VALUE} where doubling the increment overflows.
 */
class DynamicMappingsTest {

    @Test
    void nonPositiveIncrementIsRejected() {
        final DynamicMapping mapping = mock(DynamicMapping.class);
        for (final long increment : new long[]{0, -1}) {
            assertThrows(IllegalArgumentException.class, () -> DynamicMappings.findLast(mapping, 0, increment, m -> true));
            assertThrows(IllegalArgumentException.class, () -> DynamicMappings.binarySearchLast(mapping, 0, increment, m -> true));
        }
    }

    @Test
    void noMatchAtStartPositionReturnsFalse() {
        final DynamicMapping mapping = mock(DynamicMapping.class);
        when(mapping.moveTo(anyLong())).thenReturn(true);
        assertFalse(DynamicMappings.findLast(mapping, 0, 8, m -> false));
        assertFalse(DynamicMappings.binarySearchLast(mapping, 0, 8, m -> false));

        when(mapping.moveTo(anyLong())).thenReturn(false);
        assertFalse(DynamicMappings.findLast(mapping, 0, 8, m -> true));
        assertFalse(DynamicMappings.binarySearchLast(mapping, 0, 8, m -> true));
    }

    @Test
    void binarySearchLastStopsAtMaxPositionWhenIncrementOverflows() {
        //every position matches, so the increment doubles until it overflows
        assertBinarySearchLastEndsAt(0, 1, Long.MAX_VALUE);
    }

    @Test
    void binarySearchLastStopsAtMaxPositionWhenPositionOverflows() {
        //low + increment overflows to a negative position after the first step
        assertBinarySearchLastEndsAt(Long.MAX_VALUE - 1, 1, Long.MAX_VALUE);
    }

    private static void assertBinarySearchLastEndsAt(final long start, final long increment, final long expected) {
        final DynamicMapping mapping = mock(DynamicMapping.class);
        when(mapping.moveTo(anyLong())).thenReturn(true);

        assertTrue(DynamicMappings.binarySearchLast(mapping, start, increment, m -> true));

        final ArgumentCaptor<Long> positions = ArgumentCaptor.forClass(Long.class);
        verify(mapping, atLeastOnce()).moveTo(positions.capture());
        assertEquals(expected, positions.getValue(), "final position");
        assertTrue(positions.getAllValues().stream().allMatch(position -> position >= start),
                () -> "positions: " + positions.getAllValues());
    }
}
