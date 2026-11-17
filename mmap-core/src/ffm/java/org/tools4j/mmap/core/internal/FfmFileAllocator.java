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
package org.tools4j.mmap.core.internal;

import java.io.IOException;
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
 * File space allocation through the Foreign Function and Memory API, Java 22 or later, calling {@code fallocate} of
 * the C library on 64-bit Linux; compiled only when building with Java 22 or later and loaded by name. Creating the
 * downcall handles is a restricted operation, warning unless {@code --enable-native-access} allows it, and throwing
 * {@link IllegalCallerException} once the JVM denies it.
 * <p>
 * A successful allocation allocates nothing on the Java heap: {@code fallocate} is called through a downcall handle
 * without captured call state first. Only if it fails is it called again, idempotently, capturing {@code errno} in a
 * confined arena allocated for that call, which is acceptable on the failure path.
 */
final class FfmFileAllocator implements FileAllocator {
    /** Mode 0: allocate, extending the file size if the range ends beyond it */
    private static final int ALLOCATE = 0;
    //errno values of Linux, the same on x86_64 and aarch64
    private static final int EINTR = 4;
    private static final int ENOSYS = 38;
    private static final int EOPNOTSUPP = 95;
    private static final String ERRNO = "errno";
    private static final StructLayout CALL_STATE_LAYOUT = Linker.Option.captureStateLayout();
    private static final VarHandle CALL_STATE_ERRNO = CALL_STATE_LAYOUT.varHandle(PathElement.groupElement(ERRNO));

    private final MethodHandle fallocate;
    private final MethodHandle fallocateCapturingErrno;

    FfmFileAllocator() {
        if (ADDRESS.byteSize() != Long.BYTES) {
            throw new UnsupportedOperationException("File allocation through FFM needs a 64-bit platform");
        }
        final Linker linker = Linker.nativeLinker();
        final MemorySegment function = linker.defaultLookup().find("fallocate").orElseThrow(() ->
                new UnsupportedOperationException("File allocation through FFM needs Linux, fallocate not found in " +
                        "the C library"));
        //int fallocate(int fd, int mode, off_t offset, off_t len), off_t being 64 bits on 64-bit Linux
        final FunctionDescriptor descriptor = FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG,
                JAVA_LONG);
        this.fallocate = linker.downcallHandle(function, descriptor);
        this.fallocateCapturingErrno = linker.downcallHandle(function, descriptor,
                Linker.Option.captureCallState(ERRNO));
    }

    @Override
    public boolean allocate(final int fd, final long position, final long length) throws IOException {
        try {
            while ((int) fallocate.invokeExact(fd, ALLOCATE, position, length) != 0) {
                final int errno = allocateCapturingErrno(fd, position, length);
                if (errno == 0) {
                    return true;
                }
                if (errno == EOPNOTSUPP || errno == ENOSYS) {
                    return false;
                }
                if (errno != EINTR) {
                    throw new IOException("fallocate failed with errno " + errno + " for " + length +
                            " bytes at position " + position);
                }
            }
            return true;
        } catch (final IOException | RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new IllegalStateException("fallocate failed: " + t, t);
        }
    }

    /** Returns 0 if allocated now, else the errno of the failure */
    private int allocateCapturingErrno(final int fd, final long position, final long length) throws Throwable {
        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment callState = arena.allocate(CALL_STATE_LAYOUT);
            final int result = (int) fallocateCapturingErrno.invokeExact(callState, fd, ALLOCATE, position, length);
            return result == 0 ? 0 : (int) CALL_STATE_ERRNO.get(callState, 0L);
        }
    }
}
