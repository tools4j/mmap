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

import org.tools4j.mmap.io.*;

import java.util.Objects;

/**
 * Enumerator of a {@link IndexedQueue}.
 */
final class IndexedQueueEnumerator implements Enumerator {

    private final MappedFile indexFile;
    private final MappedFile dataFile;
    private final MessageReaderImpl messageReader;
    private long messageLen = -1;

    public IndexedQueueEnumerator(final MappedFile indexFile, final MappedFile dataFile) {
        if (indexFile.getFileLength() < 8) {
            throw new IllegalStateException("Not a valid index io");
        }
        this.indexFile = indexFile;
        this.dataFile = Objects.requireNonNull(dataFile);
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

        private final MappedFilePointer indexPtr = new MappedFilePointer(indexFile, MappedRegion.Mode.READ_ONLY);

        public MessageReaderImpl() {
            super(dataFile);
        }

        private long pollNextMessageLength() {
            if (messageEndPosition >= 0) {
                finishReadMessage();
            }
            return UnsafeAccess.UNSAFE.getLongVolatile(null, indexPtr.getAddress());
        }

        private MessageReader readNextMessage(final long messageLen) {
            if (messageEndPosition < 0) {
                indexPtr.ensureNotClosed().moveBy(8);//prepare to read next length
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
        }

        public void close() {
            indexPtr.close();
            super.close();
        }

    }
}
