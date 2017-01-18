/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2017 mmap (tools4j), Marco Terzer
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
package org.tools4j.mmap.queue;

import org.tools4j.mmap.io.MappedFile;
import org.tools4j.mmap.io.MessageReader;

import static org.tools4j.mmap.io.UnsafeAccess.UNSAFE;

/**
 * Enumerator of a {@link SingleQueue}.
 */
final class SingleQueueEnumerator implements Enumerator {

    private final MappedFile file;
    private final MessageReaderImpl messageReader;
    private long messageLen = -1;

    public SingleQueueEnumerator(final MappedFile file) {
        if (file.getFileLength() < 8) {
            throw new IllegalStateException("Not a queue queue io");
        }
        this.file = file;
        this.messageReader = new MessageReaderImpl();
    }

    @Override
    public boolean hasNextMessage() {
        return getMessageLength() >= 0;
    }

    @Override
    public MessageReader readNextMessage() {
        final long messageLength = getMessageLength();
        if (messageLength >= 0) {
            this.messageLen = -1;
            return messageReader.readNextMessage(messageLength);
        }
        throw new IllegalStateException("No next message found");
    }

    @Override
    public Enumerator skipNextMessage() {
        readNextMessage().finishReadMessage();
        return this;
    }

    private long getMessageLength() {
        if (messageLen < 0) {
            messageLen = messageReader.pollNextMessageLength();
        }
        return messageLen;
    }

    @Override
    public void close() {
        messageReader.close();
    }

    private final class MessageReaderImpl extends AbstractQueueMessageReader {

        public MessageReaderImpl() {
            super(file);
        }

        private long pollNextMessageLength() {
            if (messageEndPosition >= 0) {
                finishReadMessage();
            }
            return UNSAFE.getLongVolatile(null, ptr.getAddress());
        }

        private MessageReader readNextMessage(final long messageLen) {
            if (messageEndPosition < 0) {
                ptr.ensureNotClosed().moveBy(8);//skip message length field
                messageEndPosition = ptr.getPosition() + messageLen;
                return messageReader;
            }
            //should never get here
            throw new IllegalStateException("Message reading not finished");
        }

        @Override
        public void finishReadMessage() {
            if (messageEndPosition < 0) {
                throw new IllegalStateException("No message is currently being read");
            }
            ptr.ensureNotClosed().moveToPosition(messageEndPosition);
            final long rem = ptr.getBytesRemaining();
            if (rem < 8) {
                ptr.moveBy(rem);
            }
            messageEndPosition = -1;
            return;
        }
    }
}
