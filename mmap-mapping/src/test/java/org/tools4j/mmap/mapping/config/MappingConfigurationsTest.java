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
package org.tools4j.mmap.mapping.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.tools4j.mmap.mapping.api.AsyncRuntimeInstances;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for system property handling in {@link MappingConfigurations}.
 */
class MappingConfigurationsTest {

    @AfterEach
    void clearProperties() {
        System.clearProperty(MappingConfigurations.ROLL_FILES_PROPERTY);
        System.clearProperty(MappingConfigurations.MAPPING_RUNTIME_SHARING_POLICY_PROPERTY);
    }

    @Test
    void booleanPropertyAcceptsTrueAndFalseIgnoringCase() {
        System.setProperty(MappingConfigurations.ROLL_FILES_PROPERTY, "FALSE");
        assertFalse(MappingConfigurations.defaultRollFiles());
        System.setProperty(MappingConfigurations.ROLL_FILES_PROPERTY, "True");
        assertTrue(MappingConfigurations.defaultRollFiles());
    }

    @Test
    void booleanPropertyRejectsInvalidValue() {
        System.setProperty(MappingConfigurations.ROLL_FILES_PROPERTY, "ture");
        assertThrows(IllegalArgumentException.class, MappingConfigurations::defaultRollFiles);
    }

    @Test
    void sharingPolicyProperty() {
        assertEquals(MappingConfigurations.MAPPING_RUNTIME_SHARING_POLICY_DEFAULT,
                MappingConfigurations.defaultMappingRuntimeSharingPolicy());
        System.setProperty(MappingConfigurations.MAPPING_RUNTIME_SHARING_POLICY_PROPERTY, "PER_THREAD");
        assertEquals(SharingPolicy.PER_THREAD, MappingConfigurations.defaultMappingRuntimeSharingPolicy());
        System.setProperty(MappingConfigurations.MAPPING_RUNTIME_SHARING_POLICY_PROPERTY, "shared");
        assertThrows(IllegalArgumentException.class, MappingConfigurations::defaultMappingRuntimeSharingPolicy);
    }

    @Test
    void defaultUnmappingRuntimeIsSharedRuntimeInstance() {
        assertSame(AsyncRuntimeInstances.sharedUnmappingRuntime(),
                MappingConfigurations.defaultUnmappingRuntimeSupplier().get());
        assertSame(MappingConfigurations.defaultUnmappingRuntimeSupplier(),
                MappingConfigurations.defaultUnmappingRuntimeSupplier());
    }

    @Test
    void defaultUnmappingIdleStrategySupplierReturnsNewInstances() {
        assertNotSame(MappingConfigurations.defaultUnmappingRuntimeIdleStrategySupplier().get(),
                MappingConfigurations.defaultUnmappingRuntimeIdleStrategySupplier().get());
    }
}
