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
import org.junit.jupiter.api.function.Executable;
import org.tools4j.mmap.mapping.api.Closeable;
import org.tools4j.mmap.mapping.api.RegionMetrics;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.tools4j.mmap.mapping.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.mapping.impl.Constants.REGION_SIZE_GRANULARITY;
import static org.tools4j.mmap.mapping.impl.Constraints.validateAdaptiveMappingLength;
import static org.tools4j.mmap.mapping.impl.Constraints.validateAddress;
import static org.tools4j.mmap.mapping.impl.Constraints.validateAheadMappingCacheSize;
import static org.tools4j.mmap.mapping.impl.Constraints.validateFileSizeIncrement;
import static org.tools4j.mmap.mapping.impl.Constraints.validateFilesToCreateAhead;
import static org.tools4j.mmap.mapping.impl.Constraints.validateFixedMappingLength;
import static org.tools4j.mmap.mapping.impl.Constraints.validateGreaterThanZero;
import static org.tools4j.mmap.mapping.impl.Constraints.validateInitialPoolSize;
import static org.tools4j.mmap.mapping.impl.Constraints.validateLimit;
import static org.tools4j.mmap.mapping.impl.Constraints.validateMaxAppenders;
import static org.tools4j.mmap.mapping.impl.Constraints.validateMaxFileSize;
import static org.tools4j.mmap.mapping.impl.Constraints.validateMaxOpenFiles;
import static org.tools4j.mmap.mapping.impl.Constraints.validateMinFileSize;
import static org.tools4j.mmap.mapping.impl.Constraints.validateNonNegative;
import static org.tools4j.mmap.mapping.impl.Constraints.validateNotClosed;
import static org.tools4j.mmap.mapping.impl.Constraints.validatePosition;
import static org.tools4j.mmap.mapping.impl.Constraints.validatePositionDelta;
import static org.tools4j.mmap.mapping.impl.Constraints.validatePositionState;
import static org.tools4j.mmap.mapping.impl.Constraints.validateRegionCacheSize;
import static org.tools4j.mmap.mapping.impl.Constraints.validateRegionLruCacheSize;
import static org.tools4j.mmap.mapping.impl.Constraints.validateRegionOffset;
import static org.tools4j.mmap.mapping.impl.Constraints.validateRegionPosition;
import static org.tools4j.mmap.mapping.impl.Constraints.validateRegionSize;
import static org.tools4j.mmap.mapping.impl.Constraints.validateRegionsToMapAhead;
import static org.tools4j.mmap.mapping.impl.Constraints.validateUnmappingCacheSize;

/**
 * Unit test for the validation methods in {@link Constraints}.
 */
class ConstraintsTest {

    private static final int GRANULARITY = (int) REGION_SIZE_GRANULARITY;
    private static final int REGION_SIZE = 4096;
    private static final RegionMetrics REGION_METRICS = new RegionMetricsImpl(REGION_SIZE);

    private static void valid(final Executable validation) {
        assertDoesNotThrow(validation);
    }

    private static void invalid(final Executable validation) {
        assertThrows(IllegalArgumentException.class, validation);
    }

    @Test
    void signConstraints() {
        valid(() -> validateNonNegative("x", 0));
        invalid(() -> validateNonNegative("x", -1));
        valid(() -> validateGreaterThanZero("x", 1));
        invalid(() -> validateGreaterThanZero("x", 0));
        invalid(() -> validateGreaterThanZero("x", -1));
        assertEquals("x value must be positive, but was 0",
                assertThrows(IllegalArgumentException.class, () -> validateGreaterThanZero("x", 0)).getMessage());
    }

    @Test
    void addressAndPositionConstraints() {
        valid(() -> validateAddress(NULL_ADDRESS + 1));
        invalid(() -> validateAddress(NULL_ADDRESS));
        valid(() -> validatePositionState(0));
        assertThrows(IllegalStateException.class, () -> validatePositionState(-1));
        valid(() -> validatePosition(0));
        invalid(() -> validatePosition(-1));
        valid(() -> validatePosition(16, 8));
        invalid(() -> validatePosition(4, 8));
        invalid(() -> validatePosition(-8, 8));
    }

    @Test
    void regionPositionAndOffsetConstraints() {
        valid(() -> validateRegionPosition(2L * REGION_SIZE, REGION_METRICS));
        invalid(() -> validateRegionPosition(REGION_SIZE + 1, REGION_METRICS));
        invalid(() -> validateRegionPosition(-REGION_SIZE, REGION_METRICS));
        valid(() -> validateRegionOffset(0, REGION_METRICS));
        valid(() -> validateRegionOffset(REGION_SIZE - 1, REGION_METRICS));
        invalid(() -> validateRegionOffset(REGION_SIZE, REGION_METRICS));
        invalid(() -> validateRegionOffset(-1, REGION_METRICS));
    }

    @Test
    void positionDeltaConstraints() {
        valid(() -> validatePositionDelta(10, -10));
        invalid(() -> validatePositionDelta(10, -11));
        valid(() -> validatePositionDelta(8, 8, 8));
        valid(() -> validatePositionDelta(8, -8, 8));
        invalid(() -> validatePositionDelta(8, 4, 8));
        invalid(() -> validatePositionDelta(8, -16, 8));
    }

    @Test
    void lengthAndLimitConstraints() {
        valid(() -> validateLimit(10, 10, 0));
        valid(() -> validateLimit(10, 20, 10));
        invalid(() -> validateLimit(10, 9, 100));
        invalid(() -> validateLimit(10, 21, 10));
        valid(() -> validateFixedMappingLength(0, Integer.MAX_VALUE));
        valid(() -> validateFixedMappingLength(1, Integer.MAX_VALUE + 1L));
        invalid(() -> validateFixedMappingLength(0, Integer.MAX_VALUE + 1L));
        valid(() -> validateAdaptiveMappingLength(0, 0));
        valid(() -> validateAdaptiveMappingLength(10, 10));
        invalid(() -> validateAdaptiveMappingLength(-1, 10));
        invalid(() -> validateAdaptiveMappingLength(11, 10));
    }

    @Test
    void sizesMustBePowersOfTwoAndMultiplesOfGranularity() {
        valid(() -> validateRegionSize(GRANULARITY));
        valid(() -> validateRegionSize(2 * GRANULARITY));
        invalid(() -> validateRegionSize(0));
        invalid(() -> validateRegionSize(3 * GRANULARITY));
        invalid(() -> validateRegionSize(GRANULARITY / 2));

        valid(() -> validateMinFileSize(0));
        valid(() -> validateMinFileSize(GRANULARITY));
        invalid(() -> validateMinFileSize(3L * GRANULARITY));
        invalid(() -> validateMinFileSize(GRANULARITY / 2));

        valid(() -> validateMaxFileSize(GRANULARITY));
        invalid(() -> validateMaxFileSize(0));
        invalid(() -> validateMaxFileSize(3L * GRANULARITY));
        invalid(() -> validateMaxFileSize(GRANULARITY / 2));

        valid(() -> validateFileSizeIncrement(GRANULARITY));
        invalid(() -> validateFileSizeIncrement(3L * GRANULARITY));
        invalid(() -> validateFileSizeIncrement(GRANULARITY / 2));
    }

    @Test
    void countConstraints() {
        valid(() -> validateInitialPoolSize(0));
        invalid(() -> validateInitialPoolSize(-1));
        valid(() -> validateMaxOpenFiles(1));
        invalid(() -> validateMaxOpenFiles(0));
        valid(() -> validateFilesToCreateAhead(0));
        invalid(() -> validateFilesToCreateAhead(-1));
        valid(() -> validateRegionsToMapAhead(1));
        invalid(() -> validateRegionsToMapAhead(0));
        for (final int maxAppenders : new int[]{1, 64, 256}) {
            valid(() -> validateMaxAppenders(maxAppenders));
        }
        for (final int maxAppenders : new int[]{0, 2, 128, 512}) {
            invalid(() -> validateMaxAppenders(maxAppenders));
        }
    }

    @Test
    void cacheSizeConstraints() {
        valid(() -> validateRegionCacheSize(1));
        valid(() -> validateRegionCacheSize(8));
        invalid(() -> validateRegionCacheSize(0));
        invalid(() -> validateRegionCacheSize(3));
        valid(() -> validateRegionLruCacheSize(0));
        invalid(() -> validateRegionLruCacheSize(-1));
        valid(() -> validateAheadMappingCacheSize(0));
        valid(() -> validateAheadMappingCacheSize(8));
        invalid(() -> validateAheadMappingCacheSize(-1));
        invalid(() -> validateAheadMappingCacheSize(3));
        valid(() -> validateUnmappingCacheSize(32));
        invalid(() -> validateUnmappingCacheSize(0));
        invalid(() -> validateUnmappingCacheSize(3));
    }

    @Test
    void notClosedConstraint() {
        valid(() -> validateNotClosed(closeable(false)));
        final IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> validateNotClosed(closeable(true)));
        assertEquals("Already closed: closeable", e.getMessage());
    }

    private static Closeable closeable(final boolean closed) {
        return new Closeable() {
            @Override
            public boolean isClosed() {
                return closed;
            }

            @Override
            public void close() {
                //no-op
            }

            @Override
            public String toString() {
                return "closeable";
            }
        };
    }
}
