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
import org.tools4j.mmap.mapping.config.MappingConfig;
import org.tools4j.mmap.mapping.config.MappingConfigurator;
import org.tools4j.mmap.mapping.config.MappingStrategyConfig;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.tools4j.mmap.mapping.impl.Constants.REGION_SIZE_GRANULARITY;

/**
 * Unit test for validation performed by {@link MappingConfigImpl}.
 */
class MappingConfigImplTest {

    private static final int REGION_SIZE = 4 * (int) REGION_SIZE_GRANULARITY;

    private static MappingConfigurator configure() {
        return MappingConfig.configure().mappingStrategy(cfg -> cfg.regionSize(REGION_SIZE));
    }

    @Test
    void validFileSizes() {
        assertDoesNotThrow(() -> configure().maxFileSize(REGION_SIZE).toImmutableConfig());
        assertDoesNotThrow(() -> configure().expandFile(true).minFileSize(REGION_SIZE).maxFileSize(4L * REGION_SIZE)
                .toImmutableConfig());
        assertDoesNotThrow(() -> configure().expandFile(true).minFileSize(0).maxFileSize(REGION_SIZE)
                .toImmutableConfig());
    }

    @Test
    void maxFileSizeSmallerThanRegionSizeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> configure().maxFileSize(REGION_SIZE / 2)
                .toImmutableConfig());
    }

    @Test
    void minFileSizeIsOnlyValidatedAgainstRegionSizeInExpandFileMode() {
        assertThrows(IllegalArgumentException.class, () -> configure().expandFile(true).minFileSize(REGION_SIZE / 2)
                .toImmutableConfig());
        assertDoesNotThrow(() -> configure().expandFile(false).minFileSize(REGION_SIZE / 2).toImmutableConfig());
    }

    @Test
    void minFileSizeGreaterThanMaxFileSizeIsRejectedInExpandFileMode() {
        assertThrows(IllegalArgumentException.class, () -> configure().expandFile(true).minFileSize(2L * REGION_SIZE)
                .maxFileSize(REGION_SIZE).toImmutableConfig());
    }

    @Test
    void invalidIndividualValuesAreRejected() {
        final MappingStrategyConfig strategy = MappingStrategyConfig.configure().regionSize(REGION_SIZE)
                .toImmutableConfig();
        assertThrows(IllegalArgumentException.class, () -> new MappingConfigImpl(3, REGION_SIZE, true, true, 1,
                0, strategy));
        assertThrows(IllegalArgumentException.class, () -> new MappingConfigImpl(0, REGION_SIZE, true, true, 0,
                0, strategy));
    }
}
