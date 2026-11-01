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
import org.tools4j.mmap.mapping.impl.PinnedCpuBinding;
import org.tools4j.mmap.mapping.impl.SystemCpus;
import org.tools4j.mmap.mapping.impl.ThreadAffinities;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadFactory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit test for {@link CpuPinning} and {@link PinnedCpuBinding}; pinning is only tested where
 * {@linkplain CpuPinning#isSupported() supported}.
 */
class CpuPinningTest {

    private static final Describable SOURCE = sb -> sb.append("CpuPinningTest");

    private final List<String> warnings = new ArrayList<>();
    private final ErrorListener errorListener = new ErrorListener() {
        @Override
        public void onWarning(final Describable source, final CharSequence message) {
            assertSame(SOURCE, source);
            warnings.add(message.toString());
        }
    };

    @Test
    void rejectsInvalidCpu() {
        assertThrows(IllegalArgumentException.class, () -> CpuPinning.pinCurrentThread(-1));
        assertThrows(IllegalArgumentException.class, () -> CpuPinning.pinCurrentThread(CpuPinning.MAX_CPU + 1));
    }

    @Test
    void nameIsReportedAsSource() {
        final List<String> sources = new ArrayList<>();
        final ErrorListener nameListener = new ErrorListener() {
            @Override
            public void onWarning(final Describable source, final CharSequence message) {
                sources.add(source.toString());
            }
        };
        //not supported or CPU not online: warns in either case
        try (final CpuBinding binding = CpuPinning.pinCurrentThread(SystemCpus.maxOnlineCpu() + 1, "pinner",
                nameListener)) {
            assertFalse(binding.isBound());
        }
        assertEquals(List.of("pinner"), sources);
    }

    @Test
    void threadFactoryCreatesPinnedThreadFactory() {
        final ThreadFactory even = CpuPinning.threadFactory("even", true, new int[]{0, 2}, errorListener);
        assertEquals("PinnedThreadFactory:namePrefix=even|cpus=0,2|cpusInUse=0", even.toString());
        assertEquals("even-1", even.newThread(() -> {}).getName());
        assertTrue(CpuPinning.threadFactory("range", false, 2, 3).toString().contains("|cpus=2-3|"));
        assertTrue(CpuPinning.threadFactory("max", true, 0).toString().contains("|cpus=0"));
        assertThrows(IllegalArgumentException.class, () -> CpuPinning.threadFactory("x", true, new int[0]));
    }

    @Test
    void unboundBindingIsClosed() {
        try (final CpuBinding binding = CpuBinding.UNBOUND) {
            assertFalse(binding.isBound());
            assertEquals(CpuBinding.NO_CPU, binding.cpu());
            assertTrue(binding.isClosed());
        }
    }

    @Test
    void unsupportedPlatformWarnsAndDoesNotPin() {
        assumeFalse(CpuPinning.isSupported(), "CPU affinity is supported");
        final List<String> released = new ArrayList<>();
        try (final CpuBinding binding = PinnedCpuBinding.pin(0, SOURCE, errorListener,
                () -> released.add("released"))) {
            assertSame(CpuBinding.UNBOUND, binding);
        }
        assertEquals(List.of("released"), released);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("not supported"), warnings.get(0));
    }

    @Test
    void pinsAndRestoresPreviousAffinity() {
        assumeTrue(CpuPinning.isSupported(), "CPU affinity is not supported");
        final int[] previous = ThreadAffinities.currentThreadAffinity();
        final int cpu = previous[previous.length - 1];
        final CpuBinding closed;
        try (final CpuBinding binding = CpuPinning.pinCurrentThread(cpu, SOURCE, errorListener)) {
            closed = binding;
            assertTrue(binding.isBound());
            assertFalse(binding.isClosed());
            assertEquals(cpu, binding.cpu());
            assertArrayEquals(new int[]{cpu}, ThreadAffinities.currentThreadAffinity());
        }
        assertTrue(closed.isClosed());
        assertArrayEquals(previous, ThreadAffinities.currentThreadAffinity());
        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    @Test
    void closingFromAnotherThreadThrowsAndLeavesBindingOpen() throws InterruptedException {
        assumeTrue(CpuPinning.isSupported(), "CPU affinity is not supported");
        final int[] previous = ThreadAffinities.currentThreadAffinity();
        final List<String> released = new ArrayList<>();
        try (final CpuBinding binding = PinnedCpuBinding.pin(previous[0], SOURCE, errorListener,
                () -> released.add("released"))) {
            final List<Throwable> failures = new ArrayList<>();
            final Thread other = new Thread(() -> {
                try {
                    binding.close();
                } catch (final Throwable t) {
                    failures.add(t);
                }
            });
            other.start();
            other.join(10_000);
            assertEquals(1, failures.size());
            assertInstanceOf(IllegalStateException.class, failures.get(0));
            assertFalse(binding.isClosed());
            assertTrue(released.isEmpty());
        }
        assertEquals(List.of("released"), released);
        assertArrayEquals(previous, ThreadAffinities.currentThreadAffinity());
    }

    @Test
    void warnsIfCpuIsNotAvailable() {
        assumeTrue(CpuPinning.isSupported(), "CPU affinity is not supported");
        final List<String> released = new ArrayList<>();
        try (final CpuBinding binding = PinnedCpuBinding.pin(SystemCpus.maxOnlineCpu() + 1, SOURCE,
                errorListener, () -> released.add("released"))) {
            assertFalse(binding.isBound());
        }
        assertEquals(List.of("released"), released);
        assertEquals(1, warnings.size());
    }
}
