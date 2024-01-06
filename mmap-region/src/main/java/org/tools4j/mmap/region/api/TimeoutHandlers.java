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

import static java.util.Objects.requireNonNull;

/**
 * Defines static methods to create {@link TimeoutHandler} instances; access is exposed through the static methods of
 * {@link TimeoutHandler}.
 */
enum TimeoutHandlers {
    ;
    private static final TimeoutHandler<?> NO_OP = (state, policy) -> state;

    @SuppressWarnings("unchecked")
    static <T> TimeoutHandler<T> noOp() {
        return (TimeoutHandler<T>) NO_OP;
    }

    static <T> TimeoutHandler<T> exception(final BiFunction<T, ? super WaitingPolicy, ? extends RuntimeException> exceptionFactory) {
        requireNonNull(exceptionFactory);
        return (state, waitingPolicy) -> {
            throw exceptionFactory.apply(state, waitingPolicy);
        };
    }

    static <T> TimeoutHandler<T> consecutive(final TimeoutHandler<T> first, final TimeoutHandler<T> second) {
        requireNonNull(first);
        requireNonNull(second);
        return (state, waitingPolicy) -> second.handleTimeout(first.handleTimeout(state, waitingPolicy), waitingPolicy);
    }
}
