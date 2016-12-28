/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 mmap (tools4j), Marco Terzer
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

import static org.tools4j.mmap.io.UnsafeAccess.UNSAFE;

/**
 * The single appender of a {@link OneToManyIndexedQueue}.
 */
final class OneToManyIndexedAppender implements Appender {

    private final MappedFile indexFile;
    private final MappedFile dataFile;
    private final MessageWriterImpl messageWriter;

    public OneToManyIndexedAppender(final MappedFile indexFile, final MappedFile dataFile) {
        this.indexFile = Objects.requireNonNull(indexFile);
        this.dataFile = Objects.requireNonNull(dataFile);
        this.messageWriter = new MessageWriterImpl();
    }

    @Override
    public MessageWriter appendMessage() {
        return messageWriter.startAppendMessage();
    }

    @Override
    public void close() {
        messageWriter.close();
    }

    private final class MessageWriterImpl extends AbstractUnsafeMessageWriter {

        private final MappedFilePointer indexPtr = new MappedFilePointer(indexFile);
        private final MappedFilePointer dataPtr = new MappedFilePointer(dataFile);
        long messageStartPosition = -1;

        public MessageWriterImpl() {
            skipExistingMessages();
        }

        private void skipExistingMessages() {
            long offset = 0;
            long messageLen;
            while ((messageLen = UNSAFE.getLong(null, indexPtr.getAddress())) >= 0) {
                indexPtr.moveBy(8);
                offset += messageLen;
            }
            dataPtr.moveToPosition(offset);
        }

        @Override
        protected long getAndIncrementAddress(final int add) {
            if (messageStartPosition < 0) {
                throw new IllegalStateException("Message not started");
            }
            return dataPtr.ensureNotClosed().getAndIncrementAddress(add, true);
        }

        private MessageWriter startAppendMessage() {
            if (messageStartPosition >= 0) {
                throw new IllegalStateException("Current message is not finished, must be finished before appending next");
            }
            messageStartPosition = dataPtr.getPosition();
            return this;
        }

        @Override
        public void finishWriteMessage() {
            if (messageStartPosition < 0) {
                throw new IllegalStateException("No message to finish");
            }
            padMessageEnd();
            writeNextAndCurrentMessageLength();
        }

        private void writeNextAndCurrentMessageLength() {
            final long messageLen = dataPtr.getPosition() - messageStartPosition;
            long rem = indexPtr.getBytesRemaining();
            if (rem < 8) {
                indexPtr.getAndIncrementAddress(rem, true);
                rem = indexPtr.getBytesRemaining();
            }
            if (rem >= 16) {
                UNSAFE.putOrderedLong(null, indexPtr.getAddress() + 8, -1);
                UNSAFE.putOrderedLong(null, indexPtr.getAndIncrementAddress(8, false), messageLen);
            } else {
                //current message lenght and next message length are in different regions
                final long addrToRelease = indexPtr.unmapRegionOnRoll(false);
                final long addr0 = indexPtr.getAndIncrementAddress(8, true);
                final long addr1 = indexPtr.getAndIncrementAddress(8, true);
                UNSAFE.putOrderedLong(null, addr1, -1);
                UNSAFE.putOrderedLong(null, addr0, messageLen);
                indexPtr.moveBy(-8);
                if (addrToRelease != indexPtr.unmapRegionOnRoll(true)) {
                    RegionMapper.unmap(indexFile.getFileChannel(), addrToRelease, indexFile.getRegionSize());
                }
            }
            messageStartPosition = -1;
        }

        private void padMessageEnd() {
            final long pad = 8 - (int) (dataPtr.getPosition() & 0x7);
            if (pad < 8) {
                UNSAFE.setMemory(null, dataPtr.getAndIncrementAddress(pad, false), pad, (byte) 0);
            }
            if (dataPtr.getBytesRemaining() < 8) {
                dataPtr.getAndIncrementAddress(8, true);
            }
        }

        public void close() {
            if (!indexPtr.isClosed() && !dataPtr.isClosed()) {
                if (messageStartPosition >= 0) {
                    finishWriteMessage();
                }
            }
            dataPtr.close();
            indexPtr.close();
        }

    }
}
