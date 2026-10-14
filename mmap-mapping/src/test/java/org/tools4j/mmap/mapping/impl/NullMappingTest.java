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
import org.tools4j.mmap.mapping.api.AccessMode;
import org.tools4j.mmap.mapping.api.Mapping;
import org.tools4j.mmap.mapping.api.Mappings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.tools4j.mmap.mapping.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.mapping.api.NullValues.NULL_POSITION;

/**
 * Unit test for {@link NullMapping}.
 */
class NullMappingTest {

    @Test
    void nullMappingIsUnmappedAndEmpty() {
        final Mapping mapping = Mappings.nullMapping();
        assertSame(NullMapping.INSTANCE, mapping);
        assertEquals(AccessMode.READ_ONLY, mapping.accessMode());
        assertEquals(NULL_POSITION, mapping.position());
        assertEquals(NULL_POSITION, mapping.limit());
        assertEquals(NULL_ADDRESS, mapping.address());
        assertEquals(0, mapping.bytesAvailable());
        assertSame(EmptyBuffer.INSTANCE, mapping.buffer());
        assertFalse(mapping.isMapped());
        assertEquals("NullMapping", mapping.toString());
    }

    @Test
    void closeHasNoEffect() {
        NullMapping.INSTANCE.close();
        assertFalse(NullMapping.INSTANCE.isClosed());
    }
}
