package org.tools4j.eventsourcing.api;

import org.agrona.DirectBuffer;

/**
 * Consumer of an event represented as a buffer at a given offset with a length.
 * The event is accompanied with identifying source and sourceId with a time of the event.
 */
public interface IndexedMessageConsumer {
    /**
     * Consumes a message.
     * @param source - message source
     * @param sourceId - message id in the source
     * @param eventTimeNanos - time of the event
     * @param buffer - direct buffer to read message from
     * @param offset - offset of the message in the buffer
     * @param length - length of the message
     */
    void accept(int source, long sourceId, long eventTimeNanos,
                   DirectBuffer buffer, int offset, int length);
}
