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
package org.tools4j.mmap.region.impl;

import org.tools4j.mmap.region.api.DynamicMapping;

import java.util.function.Predicate;

import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;

public enum DynamicMappings {
    ;
    public static boolean findLast(final DynamicMapping mapping,
                                   final long startPosition,
                                   final long positionIncrement,
                                   final Predicate<? super DynamicMapping> matcher) {
        if (positionIncrement <= 0) {
            throw new IllegalArgumentException("Position increment most be positive: " + positionIncrement);
        }
        long lastPosition = NULL_POSITION;
        for (long position = startPosition; mapping.moveTo(position) && matcher.test(mapping); position += positionIncrement) {
            lastPosition = position;
        }
        if (lastPosition != NULL_POSITION) {
            final boolean success = mapping.moveTo(lastPosition);
            assert success : "moveTo failed unexpectedly";
            return true;
        }
        return false;
    }

    public static boolean binarySearchLast(final DynamicMapping mapping,
                                           final long startPosition,
                                           final long positionIncrement,
                                           final Predicate<? super DynamicMapping> matcher) {
        if (positionIncrement <= 0) {
            throw new IllegalArgumentException("Position increment most be positive: " + positionIncrement);
        }
        //1) initial low
        if (!mapping.moveTo(startPosition) || !matcher.test(mapping)) {
            return false;
        }
        long lowPosition = startPosition;
        long highPosition = NULL_POSITION;

        //2) find low + high
        while (highPosition == NULL_POSITION && lowPosition + positionIncrement >= 0) {
            long increment = positionIncrement;
            do {
                if (highPosition != NULL_POSITION) {
                    lowPosition = highPosition;
                }
                highPosition = lowPosition + increment;
                if (increment <= 0 || highPosition < 0) {
                    highPosition = NULL_POSITION;
                    break;
                }
                increment <<= 1;
            } while (mapping.moveTo(highPosition) && matcher.test(mapping));
        }

        //3) find middle
        if (highPosition != NULL_POSITION) {
            while (lowPosition + positionIncrement < highPosition) {
                final long midPosition = mid(lowPosition, highPosition);
                if (mapping.moveTo(midPosition) && matcher.test(mapping)) {
                    lowPosition = midPosition;
                } else {
                    highPosition = midPosition;
                }
            }
        }
        final boolean success = mapping.moveTo(lowPosition);
        assert success : "moveTo failed unexpectedly";
        return true;
    }

    private static long mid(final long a, final long b) {
        return (a >>> 1) + (b >>> 1) + (a & b & 0x1L);
    }

}
