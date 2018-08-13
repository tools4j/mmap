package org.tools4j.eventsourcing.poller;

import org.agrona.concurrent.UnsafeBuffer;
import org.tools4j.eventsourcing.api.BufferPoller;
import org.tools4j.eventsourcing.api.RegionAccessorSupplier;
import org.tools4j.eventsourcing.api.MessageConsumer;
import org.tools4j.eventsourcing.api.Poller;
import org.tools4j.eventsourcing.sbe.IndexDecoder;

import java.util.Objects;

public class IndexedPoller implements Poller {
    private static final int LENGTH_OFFSET = 0;
    private static final int LENGTH_LENGTH = 4;
    private static final int INDEX_OFFSET = LENGTH_OFFSET + LENGTH_LENGTH;
    private static final int INDEX_LENGTH = LENGTH_LENGTH + IndexDecoder.ENCODED_LENGTH;

    private final RegionAccessorSupplier regionAccessorSupplier;

    private final UnsafeBuffer mappedIndexBuffer;
    private final UnsafeBuffer mappedMessageBuffer;

    private final IndexPredicate skipPredicate;
    private final IndexPredicate pausePredicate;
    private final IndexConsumer beforeIndexConsumer;
    private final IndexConsumer afterIndexConsumer;
    private final BufferPoller bufferPoller;


    private final IndexDecoder indexDecoder = new IndexDecoder();

    private long currentIndex = 0;
    private long currentIndexPosition = 0;

    public IndexedPoller(final RegionAccessorSupplier regionAccessorSupplier,
                         final IndexPredicate skipPredicate,
                         final IndexPredicate pausePredicate,
                         final IndexConsumer beforeIndexConsumer,
                         final IndexConsumer afterIndexConsumer,
                         final BufferPoller bufferPoller) {
        this.regionAccessorSupplier = Objects.requireNonNull(regionAccessorSupplier);
        this.skipPredicate = Objects.requireNonNull(skipPredicate);
        this.pausePredicate = Objects.requireNonNull(pausePredicate);
        this.beforeIndexConsumer = Objects.requireNonNull(beforeIndexConsumer);
        this.afterIndexConsumer = Objects.requireNonNull(afterIndexConsumer);
        this.bufferPoller = Objects.requireNonNull(bufferPoller);

        this.mappedIndexBuffer = new UnsafeBuffer();
        this.mappedMessageBuffer = new UnsafeBuffer();
    }

    @Override
    public int poll(final MessageConsumer consumer) {
        wrapIndex();

        final int messageLength = currentMessageLength();
        if (messageLength <= 0) return 0;

        final long messagePosition = indexDecoder.position();
        final int source = indexDecoder.source();
        final long sourceId = indexDecoder.sourceId();
        final long eventTimeNanos = indexDecoder.eventTimeNanos();

        if (skipPredicate.test(currentIndex, source, sourceId, eventTimeNanos)) {
            advanceIndexToNextAppendPosition();
            return 0;
        } else if (!pausePredicate.test(currentIndex, source, sourceId, eventTimeNanos)) {
            final int done = pollMessages(messagePosition, messageLength,  source, sourceId, eventTimeNanos, consumer);
            advanceIndexToNextAppendPosition();
            return done;
        } else {
            return 0;
        }
    }

    private int currentMessageLength() {
        return mappedIndexBuffer.getIntVolatile(LENGTH_OFFSET);
    }

    private int pollMessages(final long messagePosition, final int messageLength, final int source, final long sourceId, final long eventTimeNanos,
                             final MessageConsumer consumer) {
        if (!regionAccessorSupplier.messageAccessor().wrap(messagePosition, mappedMessageBuffer)) {
            throw new IllegalStateException("Failed to wrap message buffer to position " + messagePosition);
        }
        beforeIndexConsumer.accept(currentIndex, source, sourceId, eventTimeNanos);
        final int done = bufferPoller.poll(mappedMessageBuffer, 0, messageLength, consumer);
        afterIndexConsumer.accept(currentIndex, source, sourceId, eventTimeNanos);
        return done;
    }

    private void wrapIndex() {
        if (!regionAccessorSupplier.indexAccessor().wrap(currentIndexPosition, mappedIndexBuffer)) {
            throw new IllegalStateException("Failed to wrap index buffer to position " + currentIndexPosition);
        }
        indexDecoder.wrap(mappedIndexBuffer, INDEX_OFFSET);
    }

    private void advanceIndexToNextAppendPosition() {
        currentIndex++;
        currentIndexPosition += INDEX_LENGTH;
    }
}
