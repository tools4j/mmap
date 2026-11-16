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
package org.tools4j.mmap.core.pinning;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.tools4j.mmap.core.config.CoreConfigurations;
import org.tools4j.mmap.core.internal.OS;
import org.tools4j.mmap.core.pinning.ThreadAffinities.Backend;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit test for {@link ThreadAffinities} and its backends; the backends are only tested where supported, and are
 * expected to be supported on Linux where their requirements are met.
 */
class ThreadAffinitiesTest {

    private static final List<AffinityMode> AUTO_ORDER = List.of(AffinityMode.FFM, AffinityMode.AGRONA,
            AffinityMode.TASKSET);
    private static final boolean LINUX = OS.NAME.startsWith("linux");
    private static final String FFM_AFFINITY_ACCESS = "org.tools4j.mmap.core.pinning.FfmAffinityAccess";

    @Test
    void modeInUseIsNeverAuto() {
        assertNotEquals(AffinityMode.AUTO, ThreadAffinities.mode());
        assertEquals(ThreadAffinities.isSupported(), ThreadAffinities.mode() != AffinityMode.NONE);
        assertEquals(ThreadAffinities.isSupported(), ThreadAffinities.unsupportedCause() == null);
        if (!ThreadAffinities.isSupported()) {
            assertThrows(IllegalStateException.class, ThreadAffinities::currentThreadAffinity);
            assertThrows(IllegalStateException.class, () -> ThreadAffinities.setCurrentThreadAffinity(0));
        }
    }

    @Test
    void noneIsNotSupported() {
        final Backend backend = ThreadAffinities.select(AffinityMode.NONE);
        assertEquals(AffinityMode.NONE, backend.mode);
        assertNull(backend.access);
        assertTrue(backend.unsupportedCause.getMessage().contains(CoreConfigurations.THREAD_AFFINITY_PROPERTY),
                backend.unsupportedCause.getMessage());
    }

    @Test
    void autoSelectsFirstSupportedBackend() {
        final Backend auto = ThreadAffinities.select(AffinityMode.AUTO);
        if (auto.access == null) {
            assertEquals(AffinityMode.NONE, auto.mode);
            assertEquals(AUTO_ORDER.size(), auto.unsupportedCause.getSuppressed().length);
            for (final AffinityMode mode : AUTO_ORDER) {
                assertNull(ThreadAffinities.select(mode).access, mode.name());
                assertTrue(auto.unsupportedCause.getMessage().contains(mode + ": "),
                        auto.unsupportedCause.getMessage());
            }
            return;
        }
        assertNull(auto.unsupportedCause);
        for (final AffinityMode before : AUTO_ORDER.subList(0, AUTO_ORDER.indexOf(auto.mode))) {
            assertNull(ThreadAffinities.select(before).access, before.name());
        }
    }

    @Test
    void invalidSystemPropertyIsNotSupported() {
        System.setProperty(CoreConfigurations.THREAD_AFFINITY_PROPERTY, "INVALID");
        try {
            final Backend backend = ThreadAffinities.selectFromSystemProperty();
            assertEquals(AffinityMode.NONE, backend.mode);
            assertNull(backend.access);
            assertInstanceOf(IllegalArgumentException.class, backend.unsupportedCause);
        } finally {
            System.clearProperty(CoreConfigurations.THREAD_AFFINITY_PROPERTY);
        }
    }

    @ParameterizedTest
    @EnumSource(names = {"FFM", "AGRONA", "TASKSET"})
    void backendIsSupportedWhereExpected(final AffinityMode mode) {
        final Backend backend = ThreadAffinities.select(mode);
        if (expectedSupported(mode)) {
            assertNotNull(backend.access, () -> mode + " not supported: " + backend.unsupportedCause);
            assertEquals(mode, backend.mode);
        } else if (backend.access == null) {
            assertEquals(AffinityMode.NONE, backend.mode);
            assertTrue(backend.unsupportedCause.getMessage().startsWith("CPU affinity through " + mode),
                    backend.unsupportedCause.getMessage());
        }
    }

    @ParameterizedTest
    @EnumSource(names = {"FFM", "AGRONA", "TASKSET"})
    void backendPinsAndRestoresAffinity(final AffinityMode mode) {
        final AffinityAccess access = supportedAccess(mode);
        final int[] previous = access.currentThreadAffinity();
        assertTrue(previous.length > 0);
        assertAscending(previous);
        final int cpu = previous[previous.length - 1];
        try {
            access.setCurrentThreadAffinity(new int[]{cpu});
            assertArrayEquals(new int[]{cpu}, access.currentThreadAffinity());
        } finally {
            access.setCurrentThreadAffinity(previous);
        }
        assertArrayEquals(previous, access.currentThreadAffinity());
    }

    @ParameterizedTest
    @EnumSource(names = {"FFM", "AGRONA", "TASKSET"})
    void backendFailsToSetUnavailableCpu(final AffinityMode mode) {
        final AffinityAccess access = supportedAccess(mode);
        final int[] previous = access.currentThreadAffinity();
        assumeTrue(previous[previous.length - 1] < CpuPinning.MAX_CPU, "all CPUs available");
        assertThrows(RuntimeException.class,
                () -> access.setCurrentThreadAffinity(new int[]{CpuPinning.MAX_CPU}));
        assertArrayEquals(previous, access.currentThreadAffinity());
    }

    private static AffinityAccess supportedAccess(final AffinityMode mode) {
        final Backend backend = ThreadAffinities.select(mode);
        assumeTrue(backend.access != null, () -> mode + " not supported: " + backend.unsupportedCause);
        return backend.access;
    }

    private static boolean expectedSupported(final AffinityMode mode) {
        if (!LINUX) {
            return false;
        }
        return switch (mode) {
            case FFM -> Runtime.version().feature() >= 22 && ffmInBuild();
            case AGRONA -> List.of("amd64", "x86_64", "aarch64").contains(System.getProperty("os.arch"));
            case TASKSET -> Arrays.stream(System.getenv().getOrDefault("PATH", "").split(File.pathSeparator))
                    .anyMatch(dir -> !dir.isEmpty() && Files.isExecutable(Paths.get(dir, "taskset")));
            case AUTO, NONE -> false;
        };
    }

    private static boolean ffmInBuild() {
        try {
            Class.forName(FFM_AFFINITY_ACCESS, false, ThreadAffinitiesTest.class.getClassLoader());
            return true;
        } catch (final ClassNotFoundException e) {
            return false;
        }
    }

    private static void assertAscending(final int[] cpus) {
        for (int i = 1; i < cpus.length; i++) {
            assertTrue(cpus[i - 1] < cpus[i], Arrays.toString(cpus));
        }
    }
}
