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

import org.tools4j.mmap.mapping.impl.MappingValidations;

/**
 * Validations of queue configuration values.
 */
public enum QueueValidations {
    ;
    /**
     * The granularity of the payload overlap size, independent of the OS page size, so that a queue can be opened on
     * systems with larger pages; mappings need not be a multiple of the page size in length.
     */
    public static final int PAYLOAD_OVERLAP_SIZE_GRANULARITY = 4096;

    public static void validatePayloadOverlapSize(final int payloadOverlapSize) {
        if (payloadOverlapSize <= 0 || payloadOverlapSize % PAYLOAD_OVERLAP_SIZE_GRANULARITY != 0) {
            throw new IllegalArgumentException("Payload overlap size must be a positive multiple of " +
                    PAYLOAD_OVERLAP_SIZE_GRANULARITY + " but was " + payloadOverlapSize);
        }
    }

    /** Validates the payload overlap size relative to the payload region size, which is expected to be valid */
    public static void validatePayloadOverlapSize(final int payloadRegionSize, final int payloadOverlapSize) {
        validatePayloadOverlapSize(payloadOverlapSize);
        MappingValidations.validateOverlapSize(payloadRegionSize, payloadOverlapSize);
    }
}
