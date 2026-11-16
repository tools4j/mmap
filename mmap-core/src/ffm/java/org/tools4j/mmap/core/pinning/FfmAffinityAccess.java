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

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Thread affinity through the Foreign Function and Memory API, Java 22 or later, calling {@code sched_getaffinity} and
 * {@code sched_setaffinity} of the C library on 64-bit Linux; compiled only when building with Java 22 or later and
 * loaded by name. Creating the downcall handles is a restricted operation, warning unless
 * {@code --enable-native-access} allows it.
 * <p>
 * The CPU mask is a {@code cpu_set_t} of {@link ThreadAffinities#MAX_CPU} + 1 bits, as 64-bit words in native byte
 * order: CPU {@code n} is bit {@code n % 64} of word {@code n / 64}. Each call allocates a confined arena for the mask
 * and the captured {@code errno}, freed before returning: this is acceptable as it happens once per pinned thread,
 * when it is pinned and when its previous affinity is restored, not on a hot path.
 */
final class FfmAffinityAccess implements AffinityAccess {
    /** pid 0: the calling thread */
    private static final int CALLING_THREAD = 0;
    private static final int MASK_WORDS = (CpuPinning.MAX_CPU + 1) / Long.SIZE;
    private static final long MASK_BYTES = (long) MASK_WORDS * Long.BYTES;
    private static final String ERRNO = "errno";
    private static final StructLayout CALL_STATE_LAYOUT = Linker.Option.captureStateLayout();
    private static final VarHandle CALL_STATE_ERRNO = CALL_STATE_LAYOUT.varHandle(PathElement.groupElement(ERRNO));

    private final MethodHandle getAffinity;
    private final MethodHandle setAffinity;

    FfmAffinityAccess() {
        if (ADDRESS.byteSize() != Long.BYTES) {
            throw new UnsupportedOperationException("Thread affinity through FFM needs a 64-bit platform");
        }
        final Linker linker = Linker.nativeLinker();
        //int sched_getaffinity(pid_t pid, size_t cpusetsize, cpu_set_t *mask), and the same for sched_setaffinity
        final FunctionDescriptor descriptor = FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_LONG, ADDRESS);
        final Linker.Option captureErrno = Linker.Option.captureCallState(ERRNO);
        this.getAffinity = linker.downcallHandle(function(linker, "sched_getaffinity"), descriptor, captureErrno);
        this.setAffinity = linker.downcallHandle(function(linker, "sched_setaffinity"), descriptor, captureErrno);
    }

    private static MemorySegment function(final Linker linker, final String name) {
        return linker.defaultLookup().find(name).orElseThrow(() -> new UnsupportedOperationException(
                "Thread affinity through FFM needs Linux, " + name + " not found in the C library"));
    }

    @Override
    public int[] currentThreadAffinity() {
        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment mask = arena.allocate(JAVA_LONG, MASK_WORDS);
            final MemorySegment callState = arena.allocate(CALL_STATE_LAYOUT);
            final int result = (int) getAffinity.invokeExact(callState, CALLING_THREAD, MASK_BYTES, mask);
            checkResult("sched_getaffinity", result, callState);
            return cpus(mask);
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new IllegalStateException("sched_getaffinity failed: " + t, t);
        }
    }

    @Override
    public void setCurrentThreadAffinity(final int[] cpus) {
        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment mask = arena.allocate(JAVA_LONG, MASK_WORDS);
            for (final int cpu : cpus) {
                final long word = cpu / Long.SIZE;
                mask.setAtIndex(JAVA_LONG, word, mask.getAtIndex(JAVA_LONG, word) | (1L << cpu));
            }
            final MemorySegment callState = arena.allocate(CALL_STATE_LAYOUT);
            final int result = (int) setAffinity.invokeExact(callState, CALLING_THREAD, MASK_BYTES, mask);
            checkResult("sched_setaffinity", result, callState);
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new IllegalStateException("sched_setaffinity failed: " + t, t);
        }
    }

    private static void checkResult(final String function, final int result, final MemorySegment callState) {
        if (result != 0) {
            throw new IllegalStateException(function + " failed with errno " +
                    (int) CALL_STATE_ERRNO.get(callState, 0L));
        }
    }

    private static int[] cpus(final MemorySegment mask) {
        int count = 0;
        for (int word = 0; word < MASK_WORDS; word++) {
            count += Long.bitCount(mask.getAtIndex(JAVA_LONG, word));
        }
        final int[] cpus = new int[count];
        int index = 0;
        for (int word = 0; word < MASK_WORDS; word++) {
            for (long bits = mask.getAtIndex(JAVA_LONG, word); bits != 0; bits &= bits - 1) {
                cpus[index++] = word * Long.SIZE + Long.numberOfTrailingZeros(bits);
            }
        }
        return cpus;
    }
}
