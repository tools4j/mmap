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
import org.tools4j.mmap.core.base.ErrorListener;
import org.tools4j.mmap.core.base.LifecycleListener;
import org.tools4j.mmap.core.idle.IdleMode;
import org.tools4j.mmap.core.idle.Idler;
import org.tools4j.mmap.core.logging.LoggingErrorListener;
import org.tools4j.mmap.mapping.api.AccessMode;
import org.tools4j.mmap.mapping.api.AsyncRuntimeInstances;
import org.tools4j.mmap.mapping.api.FileInitialiser;
import org.tools4j.mmap.mapping.api.PreAccessHook;
import org.tools4j.mmap.mapping.impl.AsyncRuntimeImpl;
import org.tools4j.mmap.mapping.impl.LoggingLifecycleListener;
import org.tools4j.mmap.mapping.impl.PinnedThreadFactory;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

    /** Custom idler, instantiated via the idler property */
    public static final class CustomIdler implements Idler {
        @Override
        public void idle() {
            Thread.onSpinWait();
        }
    }

    /** Supplier returning the same idler every time, instantiated via the idler property */
    public static final class BusySpinIdlerSupplier implements Supplier<Idler> {
        @Override
        public Idler get() {
            return Idler.busySpin();
        }
    }

    /** Custom lifecycle listener, instantiated via the lifecycle listener property */
    public static final class CustomLifecycleListener implements LifecycleListener {}

    /** Another custom lifecycle listener, instantiated via the lifecycle listener property */
    public static final class OtherLifecycleListener implements LifecycleListener {}

    /** Custom error listener, instantiated via the error listener property */
    public static final class CustomErrorListener implements ErrorListener {}

    /** Custom file initialiser, instantiated via the file initialiser property */
    public static final class CustomFileInitialiser implements FileInitialiser {
        @Override
        public PreAccessHook hook(final AccessMode accessMode) {
            return PreAccessHook.NO_OP;
        }
    }

    @AfterEach
    void clearProperties() {
        System.clearProperty(MappingConfigurations.ROLL_FILES_PROPERTY);
        System.clearProperty(MappingConfigurations.MAPPING_RUNTIME_SHARING_POLICY_PROPERTY);
        System.clearProperty(MappingConfigurations.MAX_OPEN_FILES_PROPERTY);
        System.clearProperty(MappingConfigurations.MAX_FILE_SIZE_PROPERTY);
        System.clearProperty(MappingConfigurations.INITIAL_MAPPING_POOL_SIZE_PROPERTY);
        System.clearProperty(MappingConfigurations.MAPPING_RUNTIME_IDLER_PROPERTY);
        System.clearProperty(MappingConfigurations.UNMAPPING_RUNTIME_IDLER_PROPERTY);
        System.clearProperty(MappingConfigurations.LIFECYCLE_LISTENER_PROPERTY);
        System.clearProperty(MappingConfigurations.ERROR_LISTENER_PROPERTY);
        System.clearProperty(MappingConfigurations.MAPPING_RUNTIME_CPUS_PROPERTY);
        System.clearProperty(MappingConfigurations.FILE_INITIALISER_PROPERTY);
    }

    @Test
    void mappingRuntimeCpusPropertyDefaultsToUnpinnedThreads() {
        assertSame(AsyncRuntimeImpl.DEFAULT_THREAD_FACTORY, MappingConfigurations.defaultMappingRuntimeThreadFactory());
        System.setProperty(MappingConfigurations.MAPPING_RUNTIME_CPUS_PROPERTY, " none ");
        assertSame(AsyncRuntimeImpl.DEFAULT_THREAD_FACTORY, MappingConfigurations.defaultMappingRuntimeThreadFactory());
    }

    @Test
    void mappingRuntimeCpusPropertyCreatesPinnedThreadFactoryOncePerValue() {
        System.setProperty(MappingConfigurations.MAPPING_RUNTIME_CPUS_PROPERTY, "2 - 5");
        final PinnedThreadFactory range = assertInstanceOf(PinnedThreadFactory.class,
                MappingConfigurations.defaultMappingRuntimeThreadFactory());
        assertArrayEquals(new int[]{2, 3, 4, 5}, range.cpus());
        assertSame(range, MappingConfigurations.defaultMappingRuntimeThreadFactory());

        System.setProperty(MappingConfigurations.MAPPING_RUNTIME_CPUS_PROPERTY, "3");
        final PinnedThreadFactory single = assertInstanceOf(PinnedThreadFactory.class,
                MappingConfigurations.defaultMappingRuntimeThreadFactory());
        assertArrayEquals(new int[]{3}, single.cpus());

        System.setProperty(MappingConfigurations.MAPPING_RUNTIME_CPUS_PROPERTY, "6,0,2,4");
        assertArrayEquals(new int[]{6, 0, 2, 4}, assertInstanceOf(PinnedThreadFactory.class,
                MappingConfigurations.defaultMappingRuntimeThreadFactory()).cpus());

        System.setProperty(MappingConfigurations.MAPPING_RUNTIME_CPUS_PROPERTY, "0-");
        final PinnedThreadFactory toMax = assertInstanceOf(PinnedThreadFactory.class,
                MappingConfigurations.defaultMappingRuntimeThreadFactory());
        assertEquals(0, toMax.cpus()[0]);
        assertTrue(toMax.cpus().length >= Runtime.getRuntime().availableProcessors(), toMax::toString);
        assertNotSame(range, toMax);
    }

    @Test
    void mappingRuntimeCpusPropertyRejectsInvalidValue() {
        for (final String invalid : new String[]{"x", "-1", "1,1", "1,,2", "3-2", "1-x", "8192", "FIXED:1",
                String.class.getName()}) {
            System.setProperty(MappingConfigurations.MAPPING_RUNTIME_CPUS_PROPERTY, invalid);
            final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    MappingConfigurations::defaultMappingRuntimeThreadFactory, invalid);
            assertTrue(e.getMessage().contains(MappingConfigurations.MAPPING_RUNTIME_CPUS_PROPERTY), e::getMessage);
        }
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
    void defaultMappingRuntimeSupplierFollowsSharingPolicyProperty() {
        final SharingPolicy defaultPolicy = MappingConfigurations.MAPPING_RUNTIME_SHARING_POLICY_DEFAULT;
        assertSame(AsyncRuntimeInstances.mappingRuntimeSupplier(defaultPolicy),
                MappingConfigurations.defaultMappingRuntimeSupplier());
        System.setProperty(MappingConfigurations.MAPPING_RUNTIME_SHARING_POLICY_PROPERTY, "PER_THREAD");
        assertSame(AsyncRuntimeInstances.mappingRuntimeSupplier(SharingPolicy.PER_THREAD),
                MappingConfigurations.defaultMappingRuntimeSupplier());
    }

    @Test
    void defaultUnmappingIdlerSupplierReturnsNewInstances() {
        assertNotSame(MappingConfigurations.defaultUnmappingRuntimeIdlerSupplier().get(),
                MappingConfigurations.defaultUnmappingRuntimeIdlerSupplier().get());
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
    void idlerPropertyDefaultsToBackoff() {
        assertSame(IdleMode.BACKOFF, MappingConfigurations.MAPPING_RUNTIME_IDLER_DEFAULT);
        assertEquals(Idler.backoff().toString(),
                MappingConfigurations.defaultMappingRuntimeIdlerSupplier().get().toString());
        assertEquals(Idler.backoff().toString(),
                MappingConfigurations.defaultUnmappingRuntimeIdlerSupplier().get().toString());
    }

    @Test
    void idlerPropertyWithIdleModeName() {
        System.setProperty(MappingConfigurations.MAPPING_RUNTIME_IDLER_PROPERTY, "BUSY_SPIN");
        assertSame(Idler.busySpin(), MappingConfigurations.defaultMappingRuntimeIdlerSupplier().get());
        System.setProperty(MappingConfigurations.UNMAPPING_RUNTIME_IDLER_PROPERTY, "YIELDING");
        assertSame(Idler.yielding(), MappingConfigurations.defaultUnmappingRuntimeIdlerSupplier().get());
        System.setProperty(MappingConfigurations.MAPPING_RUNTIME_IDLER_PROPERTY, "BACKOFF");
        final Supplier<? extends Idler> backoff = MappingConfigurations.defaultMappingRuntimeIdlerSupplier();
        assertNotSame(backoff.get(), backoff.get());
    }

    @Test
    void idlerPropertyWithIdlerClassCreatesNewInstances() {
        System.setProperty(MappingConfigurations.MAPPING_RUNTIME_IDLER_PROPERTY, CustomIdler.class.getName());
        final Supplier<? extends Idler> supplier = MappingConfigurations.defaultMappingRuntimeIdlerSupplier();
        final Idler first = supplier.get();
        assertInstanceOf(CustomIdler.class, first);
        assertInstanceOf(CustomIdler.class, supplier.get());
        assertNotSame(first, supplier.get());
    }

    @Test
    void idlerPropertyWithSupplierClassDelegatesToSupplier() {
        System.setProperty(MappingConfigurations.MAPPING_RUNTIME_IDLER_PROPERTY, BusySpinIdlerSupplier.class.getName());
        final Supplier<? extends Idler> supplier = MappingConfigurations.defaultMappingRuntimeIdlerSupplier();
        assertSame(Idler.busySpin(), supplier.get());
        assertSame(Idler.busySpin(), supplier.get());
    }

    @Test
    void idlerPropertyWithInvalidValueFailsOnFirstUse() {
        System.setProperty(MappingConfigurations.MAPPING_RUNTIME_IDLER_PROPERTY, "no.such.Idler");
        assertThrows(IllegalArgumentException.class, MappingConfigurations.defaultMappingRuntimeIdlerSupplier()::get);
        System.setProperty(MappingConfigurations.MAPPING_RUNTIME_IDLER_PROPERTY, String.class.getName());
        assertThrows(IllegalArgumentException.class, MappingConfigurations.defaultMappingRuntimeIdlerSupplier()::get);
        System.setProperty(MappingConfigurations.MAPPING_RUNTIME_IDLER_PROPERTY, "backoff");
        assertThrows(IllegalArgumentException.class, MappingConfigurations.defaultMappingRuntimeIdlerSupplier()::get,
                "mode names are case-sensitive");
    }

    @Test
    void listenerPropertiesDefaultToLoggingListeners() {
        assertSame(LoggingLifecycleListener.DEBUG, MappingConfigurations.defaultLifecycleListener());
        assertSame(LoggingErrorListener.INSTANCE, MappingConfigurations.defaultErrorListener());
        System.setProperty(MappingConfigurations.ERROR_LISTENER_PROPERTY, MappingConfigurations.LOGGING_LISTENER);
        assertSame(LoggingErrorListener.INSTANCE, MappingConfigurations.defaultErrorListener());
    }

    @Test
    void lifecycleListenerPropertyAcceptsLogLevels() {
        System.setProperty(MappingConfigurations.LIFECYCLE_LISTENER_PROPERTY, MappingConfigurations.LOG_DEBUG_LISTENER);
        assertSame(LoggingLifecycleListener.DEBUG, MappingConfigurations.defaultLifecycleListener());
        System.setProperty(MappingConfigurations.LIFECYCLE_LISTENER_PROPERTY, MappingConfigurations.LOG_INFO_LISTENER);
        assertSame(LoggingLifecycleListener.INFO, MappingConfigurations.defaultLifecycleListener());
        System.setProperty(MappingConfigurations.LIFECYCLE_LISTENER_PROPERTY, MappingConfigurations.LOG_WARN_LISTENER);
        assertSame(LoggingLifecycleListener.WARN, MappingConfigurations.defaultLifecycleListener());
    }

    @Test
    void listenerPropertiesAcceptNoOp() {
        System.setProperty(MappingConfigurations.LIFECYCLE_LISTENER_PROPERTY, MappingConfigurations.NO_OP_LISTENER);
        System.setProperty(MappingConfigurations.ERROR_LISTENER_PROPERTY, MappingConfigurations.NO_OP_LISTENER);
        assertSame(LifecycleListener.NO_OP, MappingConfigurations.defaultLifecycleListener());
        assertSame(ErrorListener.NO_OP, MappingConfigurations.defaultErrorListener());
    }

    @Test
    void listenerPropertyWithClassNameCreatesOneInstancePerPropertyValue() {
        System.setProperty(MappingConfigurations.LIFECYCLE_LISTENER_PROPERTY, CustomLifecycleListener.class.getName());
        System.setProperty(MappingConfigurations.ERROR_LISTENER_PROPERTY, CustomErrorListener.class.getName());
        final LifecycleListener lifecycleListener = MappingConfigurations.defaultLifecycleListener();
        final ErrorListener errorListener = MappingConfigurations.defaultErrorListener();
        assertInstanceOf(CustomLifecycleListener.class, lifecycleListener);
        assertInstanceOf(CustomErrorListener.class, errorListener);
        assertSame(lifecycleListener, MappingConfigurations.defaultLifecycleListener());
        assertSame(errorListener, MappingConfigurations.defaultErrorListener());

        System.setProperty(MappingConfigurations.LIFECYCLE_LISTENER_PROPERTY, OtherLifecycleListener.class.getName());
        assertInstanceOf(OtherLifecycleListener.class, MappingConfigurations.defaultLifecycleListener());
    }

    @Test
    void listenerPropertyWithInvalidClassIsRejected() {
        System.setProperty(MappingConfigurations.LIFECYCLE_LISTENER_PROPERTY, "no.such.Listener");
        assertThrows(IllegalArgumentException.class, MappingConfigurations::defaultLifecycleListener);
        System.setProperty(MappingConfigurations.LIFECYCLE_LISTENER_PROPERTY, MappingConfigurations.LOGGING_LISTENER);
        assertThrows(IllegalArgumentException.class, MappingConfigurations::defaultLifecycleListener);
        System.setProperty(MappingConfigurations.ERROR_LISTENER_PROPERTY, CustomLifecycleListener.class.getName());
        assertThrows(IllegalArgumentException.class, MappingConfigurations::defaultErrorListener);
    }

    @Test
    void fileInitialiserPropertyDefaultsToLazyAndAcceptsNamesAndClassNames() {
        assertSame(FileInitialiser.LAZY, MappingConfigurations.defaultFileInitialiser());
        System.setProperty(MappingConfigurations.FILE_INITIALISER_PROPERTY, "ZERO");
        assertSame(FileInitialiser.ZERO, MappingConfigurations.defaultFileInitialiser());
        System.setProperty(MappingConfigurations.FILE_INITIALISER_PROPERTY, "MINUS_ONE");
        assertSame(FileInitialiser.MINUS_ONE, MappingConfigurations.defaultFileInitialiser());
        System.setProperty(MappingConfigurations.FILE_INITIALISER_PROPERTY, "LAZY");
        assertSame(FileInitialiser.LAZY, MappingConfigurations.defaultFileInitialiser());
        System.setProperty(MappingConfigurations.FILE_INITIALISER_PROPERTY, CustomFileInitialiser.class.getName());
        final FileInitialiser initialiser = MappingConfigurations.defaultFileInitialiser();
        assertInstanceOf(CustomFileInitialiser.class, initialiser);
        assertSame(initialiser, MappingConfigurations.defaultFileInitialiser());
        System.setProperty(MappingConfigurations.FILE_INITIALISER_PROPERTY, CustomErrorListener.class.getName());
        assertThrows(IllegalArgumentException.class, MappingConfigurations::defaultFileInitialiser);
    }
}
