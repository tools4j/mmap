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
import org.tools4j.mmap.core.base.ConstantDescribable;
import org.tools4j.mmap.core.base.Describable;
import org.tools4j.mmap.mapping.unsafe.ConcurrentRollingFileMapper;
import org.tools4j.mmap.mapping.unsafe.FileMapper;
import org.tools4j.mmap.mapping.unsafe.RegionMapper;
import org.tools4j.mmap.mapping.unsafe.RollingFileMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

/**
 * Unit test for {@link MappingResources}.
 */
class MappingResourcesTest {

    /** Mock of a resource type, also {@link Describable} if the type isn't, as the resources implementing it are */
    private static Describable resource(final Class<?> type) {
        return Describable.class.isAssignableFrom(type) ? (Describable) mock(type) :
                (Describable) mock(type, withSettings().extraInterfaces(Describable.class));
    }

    @Test
    void regionMappersAndRollingFileMappersAreInternal() {
        assertTrue(MappingResources.isInternal(resource(RegionMapper.class)));
        assertTrue(MappingResources.isInternal(resource(RollingFileMapper.class)));
        assertTrue(MappingResources.isInternal(resource(ConcurrentRollingFileMapper.class)));
        assertTrue(MappingResources.INTERNAL.test(resource(RegionMapper.class)));
    }

    @Test
    void otherResourcesAreNotInternal() {
        assertFalse(MappingResources.isInternal(resource(FileMapper.class)));
        assertFalse(MappingResources.isInternal(new ConstantDescribable("resource")));
        assertFalse(MappingResources.INTERNAL.test(new ConstantDescribable("resource")));
        assertEquals("MappingResources.INTERNAL", MappingResources.INTERNAL.toString());
    }
}
