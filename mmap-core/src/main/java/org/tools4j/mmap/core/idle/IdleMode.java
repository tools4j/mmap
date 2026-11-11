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
package org.tools4j.mmap.core.idle;

/**
 * The built-in idle modes, by name, e.g. as values of system properties.
 */
public enum IdleMode {
    /** Returning immediately, see {@link Idler#noOp()}. */
    NO_OP,
    /** Busy-spinning with {@link Thread#onSpinWait()}, see {@link Idler#busySpin()}. */
    BUSY_SPIN,
    /** Yielding with {@link Thread#yield()}, see {@link Idler#yielding()}. */
    YIELDING,
    /** Spinning, then yielding, then parking for increasing periods, see {@link Idler#backoff()}. */
    BACKOFF;

    /**
     * Returns an idler of this mode, for one thread: the shared instance of a stateless mode, and a new instance
     * otherwise.
     *
     * @return the idler
     */
    public Idler newIdler() {
        return switch (this) {
            case NO_OP -> Idler.noOp();
            case BUSY_SPIN -> Idler.busySpin();
            case YIELDING -> Idler.yielding();
            default -> Idler.backoff();
        };
    }

    /**
     * Returns the idle mode with the given name, or null if there is none.
     *
     * @param name the name, e.g. of a system property value
     * @return the idle mode with the name, or null if the name is not one of an idle mode
     */
    public static IdleMode forName(final String name) {
        for (final IdleMode mode : values()) {
            if (mode.name().equals(name)) {
                return mode;
            }
        }
        return null;
    }
}
