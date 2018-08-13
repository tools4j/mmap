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
package org.tools4j.eventsourcing.queue;

import org.tools4j.eventsourcing.api.*;
import org.tools4j.eventsourcing.poller.DefaultEventProcessingState;
import org.tools4j.eventsourcing.step.PollingProcessStep;
import org.tools4j.process.ProcessStep;

import java.io.IOException;
import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

public final class DefaultEventProcessingQueue implements EventProcessingQueue {
    private final IndexedQueue upstreamQueue;
    private final IndexedTransactionalQueue downstreamQueue;
    private final ProcessStep processorStep;

    public DefaultEventProcessingQueue(final IndexedQueue upstreamQueue,
                                       final IndexedTransactionalQueue downstreamQueue,
                                       final LongSupplier systemNanoClock,
                                       final BooleanSupplier leadership,
                                       final Poller.IndexConsumer upstreamBeforeIndexHandler,
                                       final Poller.IndexConsumer upstreamAfterIndexHandler,
                                       final Poller.IndexConsumer downstreamBeforeIndexHandler,
                                       final Poller.IndexConsumer downstreamAfterIndexHandler,
                                       final MessageConsumer.UpstreamFactory upstreamFactory,
                                       final MessageConsumer.DownstreamFactory downstreamFactory,
                                       final BinaryOperator<ProcessStep> processorStepFactory) throws IOException {
        this.upstreamQueue = Objects.requireNonNull(upstreamQueue);
        this.downstreamQueue = Objects.requireNonNull(downstreamQueue);

        final DefaultEventProcessingState upstreamBeforeState = new DefaultEventProcessingState(systemNanoClock);
        final DefaultEventProcessingState downstreamBeforeState = new DefaultEventProcessingState(systemNanoClock);
        final DefaultEventProcessingState downstreamAfterState = new DefaultEventProcessingState(systemNanoClock);

        final Transaction downstreamProcessorAppender = downstreamQueue.appender();

        final Poller upstreamProcessorPoller = upstreamQueue.createPoller(
                Poller.IndexPredicate.sourceIdIsNotGreaterThanEventStateSourceId(downstreamAfterState),
                Poller.IndexPredicate.isNotLeader(leadership)
                        .and(Poller.IndexPredicate.sourceIdIsGreaterThanEventStateSourceId(downstreamAfterState)),
                upstreamBeforeState
                        .andThen(Poller.IndexConsumer.transactionInit(downstreamProcessorAppender))
                        .andThen(upstreamBeforeIndexHandler),
                Poller.IndexConsumer.transactionCommit(downstreamProcessorAppender)
                        .andThen(upstreamAfterIndexHandler));

        final MessageConsumer upstreamMessageConsumer = upstreamFactory.create(
                downstreamProcessorAppender,
                upstreamBeforeState,
                downstreamAfterState);

        final MessageConsumer downstreamMessageConsumer = downstreamFactory.create(
                downstreamBeforeState,
                downstreamAfterState);

        final ProcessStep upstreamProcessorStep = new PollingProcessStep(upstreamProcessorPoller, upstreamMessageConsumer);

        final Poller downstreamProcessorPoller = downstreamQueue.createPoller(
                Poller.IndexPredicate.never(),
                Poller.IndexPredicate.never(),
                downstreamBeforeState.andThen(downstreamBeforeIndexHandler),
                downstreamAfterState.andThen(downstreamAfterIndexHandler));

        final ProcessStep downstreamProcessorStep = new PollingProcessStep(downstreamProcessorPoller, downstreamMessageConsumer);

        this.processorStep = processorStepFactory.apply(upstreamProcessorStep, downstreamProcessorStep);
    }

    @Override
    public IndexedMessageConsumer appender() {
        return upstreamQueue.appender();
    }

    @Override
    public ProcessStep processorStep() {
        return processorStep;
    }

    @Override
    public Poller createPoller(final Poller.IndexPredicate skipPredicate,
                               final Poller.IndexPredicate pausePredicate,
                               final Poller.IndexConsumer beforeIndexHandler,
                               final Poller.IndexConsumer afterIndexHandler) throws IOException {
        return downstreamQueue.createPoller(skipPredicate,
                                            pausePredicate,
                                            beforeIndexHandler,
                                            afterIndexHandler);
    }
}
