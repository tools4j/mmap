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
import org.tools4j.mmap.io.MessageWriter;
import org.tools4j.mmap.io.RegionMapper;
import org.tools4j.mmap.io.UnsafeAccess;

import java.util.Objects;

import static org.tools4j.mmap.io.UnsafeAccess.UNSAFE;

/**
 * The single appender of a {@link OneToManyQueue}.
 */
final class OneToManyAppender implements Appender {

    private final MappedFile file;
    private final MessageWriterImpl messageWriter;

    public OneToManyAppender(final MappedFile file) {
        this.file = Objects.requireNonNull(file);
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

    private final class MessageWriterImpl extends AbstractQueueMessageWriter {

        private long regionStartPosition = 0;
        private long regionStartAddress = 0;

        public MessageWriterImpl() {
            super(file);
            skipExistingMessages();
        }

        private void skipExistingMessages() {
            long messageLen;
            while ((messageLen = UNSAFE.getLong(null, ptr.getAddress())) >= 0) {
                ptr.moveBy(8 + messageLen);
            }
        }

        private MessageWriter startAppendMessage() {
            if (messageStartPosition >= 0) {
                throw new IllegalStateException("Current message is not finished, must be finished before appending next");
            }
            messageStartPosition = ptr.ensureNotClosed().getPosition();
            regionStartPosition = messageStartPosition - ptr.getOffset();
            regionStartAddress = ptr.unmapRegionOnRoll(false);
            ptr.moveBy(8);
            return this;
        }

        @Override
        public void finishWriteMessage() {
            if (messageStartPosition < 0) {
                throw new IllegalStateException("No message to finish");
            }
            ptr.ensureNotClosed();
            padMessageAndWriteNextLength();
            writeMessageLength();
        }

        private void writeMessageLength() {
            final long startPos = messageStartPosition;
            final long startAddr = regionStartAddress + (messageStartPosition - regionStartPosition);
            final long length = ptr.getPosition() - startPos - 8;
            UNSAFE.putOrderedLong(null, startAddr, length);
            if (regionStartAddress != ptr.unmapRegionOnRoll(true)) {
                RegionMapper.unmap(file.getFileChannel(), regionStartAddress, file.getRegionSize());
            }
            messageStartPosition = -1;
            regionStartPosition = 0;
            regionStartAddress = 0;
        }

        private void padMessageAndWriteNextLength() {
            padMessageEnd();
            UNSAFE.putOrderedLong(null, ptr.getAddress(), -1);
        }
    }
}
