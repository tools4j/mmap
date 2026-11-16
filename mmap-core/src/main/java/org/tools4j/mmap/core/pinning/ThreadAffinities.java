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

import org.tools4j.mmap.core.config.CoreConfigurations;

import java.lang.reflect.InvocationTargetException;

import static java.util.Objects.requireNonNull;

/**
 * Reads and sets the CPU affinity of the calling thread for {@link CpuPinning}, through the backend chosen once per
 * JVM by system property {@value CoreConfigurations#THREAD_AFFINITY_PROPERTY} when first used, see
 * {@link AffinityMode}. Choosing it probes the backends, reading the affinity of the calling thread and setting it
 * unchanged.
 * <p>
 * Reading and setting the affinity allocates, depending on the backend, which is acceptable as it happens once per
 * pinned thread, not on a hot path.
 */
enum ThreadAffinities {
    ;
    private static final AffinityMode[] AUTO_BACKENDS = {AffinityMode.FFM, AffinityMode.AGRONA, AffinityMode.TASKSET};
    private static final int FFM_JAVA_VERSION = 22;
    private static final String FFM_AFFINITY_ACCESS = "org.tools4j.mmap.core.pinning.FfmAffinityAccess";
    private static final String AGRONA_THREAD_AFFINITY = "org.agrona.concurrent.affinity.ThreadAffinity";

    //lazy init through nested class which is only loaded when needed
    private static final class Selected {
        static final Backend BACKEND = selectFromSystemProperty();
    }

    /**
     * Returns the backend in use, chosen when thread affinity was first used, or now; it doesn't change when the
     * system property changes later.
     *
     * @return the backend in use, {@link AffinityMode#NONE} if not supported; never {@link AffinityMode#AUTO}
     */
    static AffinityMode mode() {
        return Selected.BACKEND.mode;
    }

    /** @return true if CPU affinity can be set and read on this platform */
    static boolean isSupported() {
        return Selected.BACKEND.access != null;
    }

    /** @return the reason why CPU affinity is not supported, or null if it is supported */
    static Throwable unsupportedCause() {
        return Selected.BACKEND.unsupportedCause;
    }

    /**
     * Returns the CPUs the calling thread may run on, allocating the returned array.
     *
     * @return the CPUs in ascending order
     * @throws IllegalStateException if not {@linkplain #isSupported() supported}
     * @throws RuntimeException if the affinity cannot be read
     */
    static int[] currentThreadAffinity() {
        return supportedAccess().currentThreadAffinity();
    }

    /**
     * Sets the CPUs the calling thread may run on.
     *
     * @param cpus the CPUs, not empty, each between 0 and {@link CpuPinning#MAX_CPU}
     * @throws IllegalStateException if not {@linkplain #isSupported() supported}
     * @throws RuntimeException if the affinity cannot be set, for instance if the CPUs are not available to the process
     */
    static void setCurrentThreadAffinity(final int... cpus) {
        supportedAccess().setCurrentThreadAffinity(cpus);
    }

    private static AffinityAccess supportedAccess() {
        final Backend backend = Selected.BACKEND;
        if (backend.access == null) {
            throw new IllegalStateException("CPU affinity is not supported on this platform",
                    backend.unsupportedCause);
        }
        return backend.access;
    }

    /**
     * Selects and probes the backend for the mode given by system property.
     *
     * @return the backend, unsupported with the cause if not supported or if the property value is invalid
     */
    static Backend selectFromSystemProperty() {
        try {
            return select(CoreConfigurations.systemAffinityMode());
        } catch (final IllegalArgumentException e) {
            return Backend.unsupported(e);
        }
    }

    /**
     * Selects and probes the backend for a mode.
     *
     * @param mode the mode
     * @return the backend, unsupported with the cause if no backend of the mode is supported
     */
    static Backend select(final AffinityMode mode) {
        switch (mode) {
            case AUTO:
                return selectFirstSupported();
            case NONE:
                return Backend.unsupported(new IllegalStateException("CPU affinity is disabled by system property " +
                        CoreConfigurations.THREAD_AFFINITY_PROPERTY + "=" + AffinityMode.NONE));
            default:
                try {
                    return new Backend(mode, probe(mode), null);
                } catch (final RuntimeException | LinkageError e) {
                    return Backend.unsupported(unsupported(mode, e));
                }
        }
    }

    private static Backend selectFirstSupported() {
        final StringBuilder reasons = new StringBuilder("CPU affinity is not supported on this platform: ");
        final IllegalStateException[] failures = new IllegalStateException[AUTO_BACKENDS.length];
        for (int i = 0; i < AUTO_BACKENDS.length; i++) {
            try {
                return new Backend(AUTO_BACKENDS[i], probe(AUTO_BACKENDS[i]), null);
            } catch (final RuntimeException | LinkageError e) {
                failures[i] = unsupported(AUTO_BACKENDS[i], e);
                reasons.append(i == 0 ? "" : ", ").append(AUTO_BACKENDS[i]).append(": ").append(e);
            }
        }
        final IllegalStateException unsupported = new IllegalStateException(reasons.toString());
        for (final IllegalStateException failure : failures) {
            unsupported.addSuppressed(failure);
        }
        return Backend.unsupported(unsupported);
    }

    private static IllegalStateException unsupported(final AffinityMode mode, final Throwable cause) {
        return new IllegalStateException("CPU affinity through " + mode + " is not supported: " + cause, cause);
    }

    /** Creates the backend and checks that it works, reading the affinity and setting it unchanged. */
    private static AffinityAccess probe(final AffinityMode backend) {
        final AffinityAccess access = create(backend);
        access.setCurrentThreadAffinity(access.currentThreadAffinity());
        return access;
    }

    private static AffinityAccess create(final AffinityMode backend) {
        return switch (backend) {
            case FFM -> createFfm();
            case AGRONA -> createAgrona();
            case TASKSET -> new TasksetAffinityAccess();
            case AUTO, NONE -> throw new IllegalArgumentException("No backend for " + backend);
        };
    }

    private static AffinityAccess createFfm() {
        if (Runtime.version().feature() < FFM_JAVA_VERSION) {
            throw new IllegalStateException("Thread affinity through FFM needs Java " + FFM_JAVA_VERSION +
                    " or later, running on " + Runtime.version());
        }
        try {
            return Class.forName(FFM_AFFINITY_ACCESS).asSubclass(AffinityAccess.class).getDeclaredConstructor()
                    .newInstance();
        } catch (final ClassNotFoundException e) {
            throw new IllegalStateException("Thread affinity through FFM is not in this build of mmap-core, " +
                    "built with Java " + (FFM_JAVA_VERSION - 1) + " or earlier", e);
        } catch (final InvocationTargetException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(cause);
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static AffinityAccess createAgrona() {
        try {
            Class.forName(AGRONA_THREAD_AFFINITY, false, ThreadAffinities.class.getClassLoader());
        } catch (final ClassNotFoundException e) {
            throw new IllegalStateException("Agrona is not on the class path", e);
        }
        return new AgronaAffinityAccess();
    }

    /** The selected backend: its mode and access, or mode NONE, no access and the cause if not supported. */
    static final class Backend {
        final AffinityMode mode;
        final AffinityAccess access;
        final Throwable unsupportedCause;

        private Backend(final AffinityMode mode, final AffinityAccess access, final Throwable unsupportedCause) {
            this.mode = requireNonNull(mode);
            this.access = access;
            this.unsupportedCause = unsupportedCause;
        }

        static Backend unsupported(final Throwable cause) {
            return new Backend(AffinityMode.NONE, null, requireNonNull(cause));
        }
    }
}
