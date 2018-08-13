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

import org.agrona.DirectBuffer;

import java.util.Objects;

/**
 * Interface that allows to process a message in a buffer located at an offset that has a specified length.
 */
public interface MessageConsumer {
    /**
     * Consumes a message.
     * @param buffer - direct buffer to read message from
     * @param offset - offset of the message in the buffer
     * @param length - length of the message
     */
    void accept(DirectBuffer buffer, int offset, int length);

    /**
     * Returns a composed {@code MessageConsumer} that performs, in sequence, this
     * operation followed by the {@code after} operation. If performing either
     * operation throws an exception, it is relayed to the caller of the
     * composed operation.  If performing this operation throws an exception,
     * the {@code after} operation will not be performed.
     *
     * @param after the operation to perform after this operation
     * @return a composed {@code MessageConsumer} that performs in sequence this
     * operation followed by the {@code after} operation
     * @throws NullPointerException if {@code after} is null
     */
    default MessageConsumer andThen(MessageConsumer after) {
        Objects.requireNonNull(after);
        return (b, o, l) -> {
            accept(b, o, l); after.accept(b, o, l);
        };
    }


    /**
     * Factory for upstream messages.
     */
    interface UpstreamFactory {
        /**
         * Upstream message consumer is the business logic to process upstream event. Normally this operation
         * can inspect current in-memory application state and decide which downstream messages are to be created.
         * Such downstream messages can either be state-changing events to be applied by the downstream message consumer
         * specified by DownstreamFactory
         * or just events to be polled by other components like sender or replicator.
         *
         * It is injected with a downstreamAppender so that resulting messages can be appended to a downstream queue.
         * It is also injected with upstream-before and downstream-after processing state instances.
         *
         * @param downstreamAppender - resulting message consumer which is a currently initialised transaction.
         * @param upstreamBeforeState - event processing state before upstream processing
         * @param downstreamAfterState - event processing state after downstream processing
         * @return upstream message consumer.
         */
        MessageConsumer create(MessageConsumer downstreamAppender,
                               EventProcessingState upstreamBeforeState,
                               EventProcessingState downstreamAfterState);
    }

    /**
     * Factory for downstream messages.
     */
    interface DownstreamFactory {
        /**
         * Downstream message consumer is the business logic to process downstream event. Normally this operation
         * is to update in-memory application state with the details provided in the downstream message.
         * @param downstreamBeforeState - event processing state before downstream processing
         * @param downstreamAfterState - event processing state after downstream processing
         * @return downstream message consumer
         */
        MessageConsumer create(EventProcessingState downstreamBeforeState,
                               EventProcessingState downstreamAfterState);
    }
}
