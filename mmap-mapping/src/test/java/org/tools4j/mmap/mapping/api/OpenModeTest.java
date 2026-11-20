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
package org.tools4j.mmap.mapping.api;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for {@link OpenMode}, {@link ReadOnlyOpenMode} and {@link ReadWriteOpenMode}.
 */
class OpenModeTest {

    @Test
    void definesConstantForEachConstantOfBothEnumsTypedByItsEnum() throws ReflectiveOperationException {
        final List<String> enumConstants = new ArrayList<>();
        for (final Enum<?> constant : allEnumConstants()) {
            final Field field = OpenMode.class.getDeclaredField(constant.name());
            assertEquals(constant.getDeclaringClass(), field.getType(), field.toString());
            assertSame(constant, field.get(null), field.toString());
            enumConstants.add(constant.name());
        }
        final List<String> fields = new ArrayList<>();
        for (final Field field : OpenMode.class.getDeclaredFields()) {
            if (!field.isSynthetic()) {
                fields.add(field.getName());
            }
        }
        //NOTE: the order of declared fields is unspecified
        enumConstants.sort(null);
        fields.sort(null);
        assertEquals(enumConstants, fields, "no other fields than the enum constants");
    }

    @Test
    void declaresNoDefaultMethodsSinceTheyWouldLeaveConstantsNullWhenEnumsInitialiseFirst() {
        for (final Method method : OpenMode.class.getDeclaredMethods()) {
            assertFalse(method.isDefault(), method.toString());
        }
    }

    @Test
    void isSealedForBothEnums() {
        assertTrue(OpenMode.class.isSealed());
        assertArrayEquals(new Class<?>[] {ReadOnlyOpenMode.class, ReadWriteOpenMode.class},
                OpenMode.class.getPermittedSubclasses());
    }

    @Test
    void accessMode() {
        for (final ReadOnlyOpenMode openMode : ReadOnlyOpenMode.values()) {
            assertEquals(AccessMode.READ_ONLY, openMode.accessMode(), openMode.name());
        }
        for (final ReadWriteOpenMode openMode : ReadWriteOpenMode.values()) {
            assertEquals(AccessMode.READ_WRITE, openMode.accessMode(), openMode.name());
        }
    }

    private static List<Enum<?>> allEnumConstants() {
        final List<Enum<?>> constants = new ArrayList<>(Arrays.asList(ReadOnlyOpenMode.values()));
        constants.addAll(Arrays.asList(ReadWriteOpenMode.values()));
        return constants;
    }
}
