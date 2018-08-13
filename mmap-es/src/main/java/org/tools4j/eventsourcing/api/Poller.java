/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2018 mmap (tools4j), Marco Terzer, Anton Anufriev
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
package org.tools4j.eventsourcing.api;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Queue poller
 */
public interface Poller {
    /**
     * polls a queue and invokes the consumer if a message is available for consumption.
     * @param consumer of a polled message if available
     * @return number of polled messages
     */
    int poll(MessageConsumer consumer);

    /**
     * Tests index details
     */
    interface IndexPredicate {

        boolean test(long index, int source, long sourceId, long eventTimeNanos);

        /**
         * Returns a composed predicate that represents a short-circuiting logical
         * AND of this predicate and another.  When evaluating the composed
         * predicate, if this predicate is {@code false}, then the {@code other}
         * predicate is not evaluated.
         *
         * <p>Any exceptions thrown during evaluation of either predicate are relayed
         * to the caller; if evaluation of this predicate throws an exception, the
         * {@code other} predicate will not be evaluated.
         *
         * @param other a predicate that will be logically-ANDed with this
         *              predicate
         * @return a composed predicate that represents the short-circuiting logical
         * AND of this predicate and the {@code other} predicate
         * @throws NullPointerException if other is null
         */
        default IndexPredicate and(final IndexPredicate other) {
            Objects.requireNonNull(other);
            return (i, s, sid, etn) -> test(i, s, sid, etn) && other.test(i, s, sid, etn);
        }

        /**
         * Returns a predicate that represents the logical negation of this
         * predicate.
         *
         * @return a predicate that represents the logical negation of this
         * predicate
         */
        default IndexPredicate negate() {
            return (i, s, sid, etn) -> !test(i, s, sid, etn);
        }

        /**
         * Returns a composed predicate that represents a short-circuiting logical
         * OR of this predicate and another.  When evaluating the composed
         * predicate, if this predicate is {@code true}, then the {@code other}
         * predicate is not evaluated.
         *
         * <p>Any exceptions thrown during evaluation of either predicate are relayed
         * to the caller; if evaluation of this predicate throws an exception, the
         * {@code other} predicate will not be evaluated.
         *
         * @param other a predicate that will be logically-ORed with this
         *              predicate
         * @return a composed predicate that represents the short-circuiting logical
         * OR of this predicate and the {@code other} predicate
         * @throws NullPointerException if other is null
         */
        default IndexPredicate or(final IndexPredicate other) {
            Objects.requireNonNull(other);
            return (i, s, sid, etn) -> test(i, s, sid, etn) || other.test(i, s, sid, etn);
        }

        static IndexPredicate sourceIdIsNotGreaterThanEventStateSourceId(final EventProcessingState eventProcessingState) {
            return (index, source, sourceId, eventTimeNanos) -> sourceId <= eventProcessingState.sourceId(source);
        }

        static IndexPredicate sourceIdIsGreaterThanEventStateSourceId(final EventProcessingState eventProcessingState) {
            return sourceIdIsNotGreaterThanEventStateSourceId(eventProcessingState).negate();
        }

        static IndexPredicate eventTimeBefore(final long timeNanos) {
            return (index, source, sourceId, eventTimeNanos) -> eventTimeNanos < timeNanos;
        }

        static IndexPredicate never() {
            return (index, source, sourceId, eventTimeNanos) -> false;
        }

        static IndexPredicate isLeader(final BooleanSupplier leadership) {
            return (index, source, sourceId, eventTimeNanos) -> leadership.getAsBoolean();
        }

        static IndexPredicate isNotLeader(final BooleanSupplier leadership) {
            return isLeader(leadership).negate();
        }
    }

    /**
     * Index consumer
     */
    interface IndexConsumer {
        void accept(long index, int source, long sourceId, long eventTimeNanos);

        default IndexConsumer andThen(final IndexConsumer after) {
            Objects.requireNonNull(after);
            return (i, s, sid, etn) -> { accept(i, s, sid, etn); after.accept(i, s, sid, etn); };
        }

        static IndexConsumer transactionInit(final Transaction transaction) {
            return (index, source, sourceId, eventTimeNanos) -> transaction.init(source, sourceId, eventTimeNanos);
        }

        static IndexConsumer transactionCommit(final Transaction transaction) {
            return (index, source, sourceId, eventTimeNanos) -> transaction.commit();
        }

        static IndexConsumer noop() {
            return (index, source, sourceId, eventTimeNanos) -> {};
        }
    }

}
