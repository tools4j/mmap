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
