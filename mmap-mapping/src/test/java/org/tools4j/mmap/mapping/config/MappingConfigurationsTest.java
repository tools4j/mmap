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

import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.agrona.concurrent.YieldingIdleStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.tools4j.mmap.mapping.api.AsyncRuntimeInstances;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for system property handling in {@link MappingConfigurations}.
 */
class MappingConfigurationsTest {

    /** Supplier returning the same idle strategy every time, instantiated via the idle strategy property */
    public static final class NoOpIdleStrategySupplier implements Supplier<IdleStrategy> {
        @Override
        public IdleStrategy get() {
            return NoOpIdleStrategy.INSTANCE;
        }
    }

    @AfterEach
    void clearProperties() {
        System.clearProperty(MappingConfigurations.ROLL_FILES_PROPERTY);
        System.clearProperty(MappingConfigurations.MAPPING_RUNTIME_SHARING_POLICY_PROPERTY);
        System.clearProperty(MappingConfigurations.MAX_OPEN_FILES_PROPERTY);
        System.clearProperty(MappingConfigurations.MAX_FILE_SIZE_PROPERTY);
        System.clearProperty(MappingConfigurations.INITIAL_MAPPING_POOL_SIZE_PROPERTY);
        System.clearProperty(MappingConfigurations.MAPPING_RUNTIME_IDLE_STRATEGY_PROPERTY);
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

    @Test
    void intPropertyIsParsedAndValidated() {
        assertEquals(MappingConfigurations.MAX_OPEN_FILES_DEFAULT, MappingConfigurations.defaultMaxOpenFiles());
        System.setProperty(MappingConfigurations.MAX_OPEN_FILES_PROPERTY, "5");
        assertEquals(5, MappingConfigurations.defaultMaxOpenFiles());
        System.setProperty(MappingConfigurations.MAX_OPEN_FILES_PROPERTY, "five");
        assertThrows(IllegalArgumentException.class, MappingConfigurations::defaultMaxOpenFiles);
        System.setProperty(MappingConfigurations.MAX_OPEN_FILES_PROPERTY, "0");
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                MappingConfigurations::defaultMaxOpenFiles);
        assertEquals("Invalid value for system property: " + MappingConfigurations.MAX_OPEN_FILES_PROPERTY + "=0",
                e.getMessage());
    }

    @Test
    void longPropertyIsParsedAndValidated() {
        assertEquals(MappingConfigurations.MAX_FILE_SIZE_DEFAULT, MappingConfigurations.defaultMaxFileSize());
        final long maxFileSize = 4 * MappingConfigurations.defaultRegionSize();
        System.setProperty(MappingConfigurations.MAX_FILE_SIZE_PROPERTY, String.valueOf(maxFileSize));
        assertEquals(maxFileSize, MappingConfigurations.defaultMaxFileSize());
        System.setProperty(MappingConfigurations.MAX_FILE_SIZE_PROPERTY, "4GB");
        assertThrows(IllegalArgumentException.class, MappingConfigurations::defaultMaxFileSize);
        System.setProperty(MappingConfigurations.MAX_FILE_SIZE_PROPERTY, String.valueOf(maxFileSize + 1));
        assertThrows(IllegalArgumentException.class, MappingConfigurations::defaultMaxFileSize);
    }

    @Test
    void initialMappingPoolSizeProperty() {
        assertEquals(MappingConfigurations.INITIAL_MAPPING_POOL_SIZE_DEFAULT,
                MappingConfigurations.defaultInitialMappingPoolSize());
        System.setProperty(MappingConfigurations.INITIAL_MAPPING_POOL_SIZE_PROPERTY, "8");
        assertEquals(8, MappingConfigurations.defaultInitialMappingPoolSize());
        System.setProperty(MappingConfigurations.INITIAL_MAPPING_POOL_SIZE_PROPERTY, "-1");
        assertThrows(IllegalArgumentException.class, MappingConfigurations::defaultInitialMappingPoolSize);
    }

    @Test
    void idleStrategyPropertyDefault() {
        assertSame(MappingConfigurations.MAPPING_RUNTIME_IDLE_STRATEGY_DEFAULT,
                MappingConfigurations.defaultMappingRuntimeIdleStrategySupplier());
    }

    @Test
    void idleStrategyPropertyWithIdleStrategyClassCreatesNewInstances() {
        System.setProperty(MappingConfigurations.MAPPING_RUNTIME_IDLE_STRATEGY_PROPERTY,
                YieldingIdleStrategy.class.getName());
        final Supplier<? extends IdleStrategy> supplier = MappingConfigurations.defaultMappingRuntimeIdleStrategySupplier();
        final IdleStrategy first = supplier.get();
        assertInstanceOf(YieldingIdleStrategy.class, first);
        assertInstanceOf(YieldingIdleStrategy.class, supplier.get());
        assertNotSame(first, supplier.get());
    }

    @Test
    void idleStrategyPropertyWithSupplierClassDelegatesToSupplier() {
        System.setProperty(MappingConfigurations.MAPPING_RUNTIME_IDLE_STRATEGY_PROPERTY,
                NoOpIdleStrategySupplier.class.getName());
        final Supplier<? extends IdleStrategy> supplier = MappingConfigurations.defaultMappingRuntimeIdleStrategySupplier();
        assertSame(NoOpIdleStrategy.INSTANCE, supplier.get());
        assertSame(NoOpIdleStrategy.INSTANCE, supplier.get());
    }

    @Test
    void idleStrategyPropertyWithInvalidClassFailsOnFirstUse() {
        System.setProperty(MappingConfigurations.MAPPING_RUNTIME_IDLE_STRATEGY_PROPERTY, "no.such.IdleStrategy");
        assertThrows(IllegalArgumentException.class,
                MappingConfigurations.defaultMappingRuntimeIdleStrategySupplier()::get);
        System.setProperty(MappingConfigurations.MAPPING_RUNTIME_IDLE_STRATEGY_PROPERTY, String.class.getName());
        assertThrows(IllegalArgumentException.class,
                MappingConfigurations.defaultMappingRuntimeIdleStrategySupplier()::get);
    }
}
