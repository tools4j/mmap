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

import org.tools4j.mmap.io.AbstractUnsafeMessageWriter;
import org.tools4j.mmap.io.MappedFile;
import org.tools4j.mmap.io.MappedRegion;
import org.tools4j.mmap.io.MessageWriter;

import java.util.Objects;

/**
 * The single appender of a {@link OneToManyQueue}.
 */
public final class OneToManyAppender implements Appender {

    private final MappedFile file;
    private final MessageWriterImpl messageWriter;

    public OneToManyAppender(final MappedFile file) {
        this.file = Objects.requireNonNull(file);
        this.messageWriter = new MessageWriterImpl();
    }

    @Override
    public MessageWriter<Appender> appendMessage() {
        return messageWriter.startAppendMessage();
    }

    @Override
    public void close() {
        messageWriter.close();
    }

    private final class MessageWriterImpl extends AbstractUnsafeMessageWriter<Appender> {

        private final RollingRegionPointer ptr = new RollingRegionPointer(file);

        private MappedRegion startRegion;
        private long startOffset;

        public MessageWriterImpl() {
            skipExistingMessages();
        }

        private void skipExistingMessages() {
            long messageLen;
            while ((messageLen = UnsafeAccess.UNSAFE.getLong(null, ptr.getAddress())) >= 0) {
                ptr.moveBy(8 + messageLen);
            }
        }

        @Override
        protected long getAndIncrementAddress(final int add) {
            if (startRegion == null) {
                throw new IllegalStateException("Message not started");
            }
            return ptr.ensureNotClosed().getAndIncrementAddress(add, true);
        }

        private MessageWriter<Appender> startAppendMessage() {
            if (startRegion != null) {
                throw new IllegalStateException("Current message is not finished, must be finished before appending next");
            }
            startRegion = ptr.ensureNotClosed().getRegion();
            startOffset = ptr.getOffset();
            startRegion.incAndGetRefCount();
            ptr.moveBy(8);
            return this;
        }

        @Override
        public Appender finishAppendMessage() {
            if (startRegion == null) {
                throw new IllegalStateException("No message to finish");
            }
            ptr.ensureNotClosed();
            padMessageAndWriteNextLength();
            writeMessageLength();
            return OneToManyAppender.this;
        }

        private void writeMessageLength() {
            final long startAddr = startRegion.getAddress(startOffset);
            final long startPos = startRegion.getPosition() + startOffset;
            final long length = ptr.getPosition() - startPos - 8;
            UnsafeAccess.UNSAFE.putOrderedLong(null, startAddr, length);
            file.releaseRegion(startRegion);
            startRegion = null;
            startOffset = -1;
        }

        private void padMessageAndWriteNextLength() {
            padMessageEnd();
            UnsafeAccess.UNSAFE.putOrderedLong(null, ptr.getAddress(), -1);
        }

        //POSTCONDITION: guaranteed that we can write a 8 byte msg len after padding
        private void padMessageEnd() {
            final long pad = 8 - (int) (ptr.getPosition() & 0x7);
            if (pad < 8) {
                UnsafeAccess.UNSAFE.setMemory(null, ptr.getAndIncrementAddress(pad, false), pad, (byte) 0);
            }
            if (ptr.getBytesRemaining() < 8) {
                ptr.getAndIncrementAddress(8, true);
            }
        }

        public void close() {
            if (!ptr.isClosed()) {
                if (startRegion != null) {
                    finishAppendMessage();
                }
                ptr.close();
            }
        }

    }
}
