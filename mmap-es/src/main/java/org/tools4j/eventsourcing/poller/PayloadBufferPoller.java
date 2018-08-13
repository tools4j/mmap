package org.tools4j.eventsourcing.poller;

import org.agrona.DirectBuffer;
import org.tools4j.eventsourcing.api.BufferPoller;
import org.tools4j.eventsourcing.api.MessageConsumer;
import org.tools4j.eventsourcing.sbe.MessageHeaderDecoder;
import org.tools4j.eventsourcing.sbe.MultiPayloadDecoder;
import org.tools4j.eventsourcing.sbe.SinglePayloadDecoder;

public final class PayloadBufferPoller implements BufferPoller {

    private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    private final SinglePayloadDecoder singlePayloadBodyDecoder = new SinglePayloadDecoder();
    private final MultiPayloadDecoder multiPayloadDecoder = new MultiPayloadDecoder();

    @Override
    public int poll(final DirectBuffer srcBuffer, final int srcOffset, final int srcLength, final MessageConsumer consumer) {
        messageHeaderDecoder.wrap(srcBuffer, srcOffset);

        int done = 0;

        if (messageHeaderDecoder.templateId() == SinglePayloadDecoder.TEMPLATE_ID) {
            singlePayloadBodyDecoder.wrap(srcBuffer, messageHeaderDecoder.encodedLength(),
                    messageHeaderDecoder.blockLength(), messageHeaderDecoder.schemaId());

            consumer.accept(singlePayloadBodyDecoder.buffer(),
                    singlePayloadBodyDecoder.limit() + SinglePayloadDecoder.valueHeaderLength(),
                    singlePayloadBodyDecoder.valueLength());
            return ++done;
        } else if (messageHeaderDecoder.templateId() == MultiPayloadDecoder.TEMPLATE_ID) {
            multiPayloadDecoder.wrap(srcBuffer, messageHeaderDecoder.encodedLength(),
                    messageHeaderDecoder.blockLength(), messageHeaderDecoder.schemaId());
            for (final MultiPayloadDecoder.EntriesDecoder entriesDecoder : multiPayloadDecoder.entries()) {
                consumer.accept(multiPayloadDecoder.buffer(),
                        multiPayloadDecoder.limit() + MultiPayloadDecoder.EntriesDecoder.valueHeaderLength(),
                        entriesDecoder.valueLength());
                multiPayloadDecoder.limit(multiPayloadDecoder.limit() + MultiPayloadDecoder.EntriesDecoder.valueHeaderLength() + entriesDecoder.valueLength());
                done++;
            }
            return done;
        } else {
            throw new IllegalStateException("Unexpected message type " + messageHeaderDecoder.templateId());
        }
    }
}
