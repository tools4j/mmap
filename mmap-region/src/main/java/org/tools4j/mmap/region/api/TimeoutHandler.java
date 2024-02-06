/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2024 tools4j.org (Marco Terzer, Anton Anufriev)
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
package org.tools4j.mmap.region.api;

import java.util.function.BiFunction;

/**
 * Handles timeouts when failing to meet a condition using a {@link WaitingPolicy}.
 * @param <T> the state parameter, usually the object that caused the timeout
 */
public interface TimeoutHandler<T> {
    /**
     * Handles a timeout involving the {@code state} object and returns the same (or a modified/alternative) state
     * object.
     *
     * @param state         the state object for which a timeout occurred
     * @param waitingPolicy the waiting policy that was in place when the timeout occurred
     */
    void handleTimeout(T state, WaitingPolicy waitingPolicy);

    /**
     * Returns a constant no-op handler that simply returns the unchanged state object.
     * @return a no-op handler
     * @param <T> the state parameter, usually the object that caused the timeout
     */
    static <T> TimeoutHandler<T> noOp() {
        return TimeoutHandlers.noOp();
    }

    /**
     * Returns a timeout handler that throws an exception on timeout.
     * @param exceptionFactory factory for exception given the state and waiting policy passed to the timeout handler
     * @return a handler that throws the exception that is produced by the exception factory
     * @param <T> the state parameter, usually the object that caused the timeout
     */
    static <T> TimeoutHandler<T> exception(final BiFunction<T, ? super WaitingPolicy, ? extends RuntimeException> exceptionFactory) {
        return TimeoutHandlers.exception(exceptionFactory);
    }

    /**
     * Returns a timeout handler that invokes both provided timeout handlers, for instance to log the timeout and then
     * throw an exception.
     *
     * @param first the first timeout handler
     * @param second the second timeout handler
     * @return a handler that invokes both provided timeout handlers in sequence
     * @param <T> the state parameter, usually the object that caused the timeout
     */
    static <T> TimeoutHandler<T> consecutive(final TimeoutHandler<T> first, final TimeoutHandler<T> second) {
        return TimeoutHandlers.consecutive(first, second);
    }
}
