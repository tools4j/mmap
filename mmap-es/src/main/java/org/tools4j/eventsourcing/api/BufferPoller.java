package org.tools4j.eventsourcing.api;

import org.agrona.DirectBuffer;

/**
 * Interfaces that allows to process multiple messages encoded in the message.
 */
public interface BufferPoller {
    /**
     * Polls a buffer that may contain multiple messages and delegates the messages to the consumer.
     * @param srcBuffer source buffer where the messages are encoded
     * @param srcOffset offset of the message that contains multiple encoded messages
     * @param srcLength length of the message that contains multiple encoded messages
     * @param consumer consumer that is to receive the multiple encoded messages
     * @return number of processed encoded messages
     */
    int poll(DirectBuffer srcBuffer, int srcOffset, int srcLength, MessageConsumer consumer);
}
