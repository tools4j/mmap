/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2025 tools4j.org (Marco Terzer, Anton Anufriev)
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
///*
// * The MIT License (MIT)
// *
// * Copyright (c) 2016-2025 tools4j.org (Marco Terzer, Anton Anufriev)
// *
// * Permission is hereby granted, free of charge, to any person obtaining a copy
// * of this software and associated documentation files (the "Software"), to deal
// * in the Software without restriction, including without limitation the rights
// * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// * copies of the Software, and to permit persons to whom the Software is
// * furnished to do so, subject to the following conditions:
// *
// * The above copyright notice and this permission notice shall be included in all
// * copies or substantial portions of the Software.
// *
// * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// * SOFTWARE.
// */
//package org.tools4j.mmap.dictionary.impl;
//
//import org.agrona.DirectBuffer;
//import org.agrona.MutableDirectBuffer;
//import org.agrona.concurrent.AtomicBuffer;
//import org.agrona.concurrent.UnsafeBuffer;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.tools4j.mmap.dictionary.api.DeletePredicate;
//import org.tools4j.mmap.dictionary.api.DeletingContext;
//import org.tools4j.mmap.dictionary.api.UpdatePredicate;
//import org.tools4j.mmap.dictionary.api.Updater;
//import org.tools4j.mmap.dictionary.api.UpdatingContext;
//import org.tools4j.mmap.dictionary.api.UpdatingContext.Key;
//import org.tools4j.mmap.dictionary.api.UpdatingContext.Value;
//import org.tools4j.mmap.region.api.OffsetMapping;
//import org.tools4j.mmap.region.impl.EmptyBuffer;
//
//import java.nio.ByteBuffer;
//
//import static java.util.Objects.requireNonNull;
//import static org.tools4j.mmap.dictionary.impl.Headers.NULL_HEADER;
//
//final class UpdaterImpl implements Updater {
//    private static final Logger LOGGER = LoggerFactory.getLogger(UpdaterImpl.class);
//
//    private final String dictionaryName;
//    private final UpdaterMappings mappings;
//    private final int updaterId;
//    private final OffsetMapping header;
//    private final OffsetMapping payload;
//    private final boolean enableCopyFromPreviousRegion;
//    private final UpdatingContextImpl updContext;
//    private final DeletingContextImpl delContext;
//    private boolean closed;
//
//    public UpdaterImpl(final String dictionaryName,
//                       final UpdaterMappings mappings,
//                       final boolean enableCopyFromPreviousRegion) {
//        this.dictionaryName = requireNonNull(dictionaryName);
//        this.mappings = requireNonNull(mappings);
//        this.updaterId = mappings.updaterId();
//        this.header = requireNonNull(mappings.header());
//        this.payload = requireNonNull(mappings.payload());
//        this.enableCopyFromPreviousRegion = enableCopyFromPreviousRegion;
//        this.updContext = new UpdatingContextImpl(this);
//    }
//
//    private int maxKeyLength() {
//        //regionSize - hash - keyLen
//        return payload.regionSize() - Long.BYTES - Integer.BYTES;
//    }
//
//    private int maxValueLength() {
//        //regionSize - valueLen
//        return payload.regionSize() - Integer.BYTES;
//    }
//
//    @Override
//    public void put(final DirectBuffer key, final DirectBuffer value) {
//        put(key, value, null, (valCtxt, valLen, xtra) -> valCtxt.put(valLen))
//                .close();
//    }
//
//    @Override
//    public UpdatingContext.Result putIfAbsent(final DirectBuffer key, final DirectBuffer value) {
//        return put(key, value, null, (valCtxt, valLen, xtra) -> valCtxt.putIfAbsent(valLen));
//    }
//
//    @Override
//    public UpdatingContext.Result putIfMatching(final DirectBuffer key, final DirectBuffer value, final UpdatePredicate condition) {
//        return put(key, value, condition, UpdatingContext.Value::putIfMatching);
//    }
//
//    @FunctionalInterface
//    private interface PutOperation<X> {
//        UpdatingContext.Result put(UpdatingContext.Value valueContext, int valueLength, X extra);
//    }
//
//    private <X> UpdatingContext.Result put(final DirectBuffer key,
//                                           final DirectBuffer value,
//                                           final X extra,
//                                           final PutOperation<X> operation) {
//        final int valLen = value.capacity();
//        try (final UpdatingContext.Value valueContext = updating(key, valLen)) {
//            valueContext.valueBuffer().putBytes(0, value, 0, valLen);
//            return operation.put(valueContext, valLen, extra);
//        }
//    }
//
//    @Override
//    public DeletingContext.Result delete(final DirectBuffer key) {
//        return delete(key, null, (keyContext, keyLength, extra) -> keyContext.delete(keyLength));
//    }
//
//    @Override
//    public DeletingContext.Result deleteIfMatching(final DirectBuffer key, final DeletePredicate condition) {
//        return delete(key, condition, DeletingContext.Key::deleteIfMatching);
//    }
//
//    @FunctionalInterface
//    private interface DeleteOperation<X> {
//        DeletingContext.Result delete(DeletingContext.Key keyContext, X extra);
//    }
//
//    private <X> DeletingContext.Result delete(final DirectBuffer key,
//                                              final X extra,
//                                              final DeleteOperation<X> operation) {
//        checkNotClosed();
//        try (final DeletingContext.Key keyContext = delContext.init(key, 0, key.capacity())) {
//            return operation.delete(keyContext, extra);
//        }
//    }
//
//    @Override
//    public Value updating(final DirectBuffer key, final int valueCapacity) {
//        checkNotClosed();
//        return updContext.init(key, valueCapacity);
//    }
//
//    @Override
//    public Key updating(final int keyCapacity, final int valueCapacity) {
//        checkNotClosed();
//        return updContext.init(keyCapacity, valueCapacity);
//    }
//
//    @Override
//    public DeletingContext.Key deleting() {
//        return deletingContext.init();
//    }
//
//    private long appendEntry(final long payloadPosition, final int payloadLength) {
//        checkNotClosed();
//        final OffsetMapping hdr = header;
//        final AtomicBuffer buf = hdr.buffer();
//        final long headerValue = Headers.header(updaterId, payloadPosition);
//        long index = endIndex;
//        checkIndexNotExceedingMax(index);
//        while (!buf.compareAndSetLong(0, NULL_HEADER, headerValue)) {
//            do {
//                index++;
//                endIndex = index;
//                checkIndexNotExceedingMax(index);
//                if (!Headers.moveToHeaderIndex(hdr, index)) {
//                    throw headerMoveException(this, Headers.headerPositionForIndex(index));
//                }
//            } while (buf.getLongVolatile(0) != NULL_HEADER);
//        }
//        final long nextIndex = index + 1;
//        endIndex = nextIndex;//NOTE: may exceed MAX, but we check when appending (see above)
//        if (nextIndex <= Index.MAX) {
//            if (!Headers.moveToHeaderIndex(hdr, nextIndex)) {
//                throw headerMoveException(this, Headers.headerPositionForIndex(index));
//            }
//        }
//        lastOwnHeader = headerValue;
//        lastOwnPayloadLength = payloadLength;
//        return index;
//    }
//
//    private void checkNotClosed() {
//        if (isClosed()) {
//            throw new IllegalStateException("Updater " + updaterName() + " is closed");
//        }
//    }
//
//    @Override
//    public boolean isClosed() {
//        return closed;
//    }
//
//    @Override
//    public void close() {
//        if (!isClosed()) {
//            closed = true;
//            mappings.close();
//            LOGGER.info("Updater closed: {}", updaterName());
//        }
//    }
//
//    private static final class UpdatingContextImpl implements UpdatingContext.Key, UpdatingContext.Value {
//        final UpdaterImpl updater;
//        final OffsetMapping payload;
//        final MutableDirectBuffer buffer = new UnsafeBuffer(0, 0);
//        int maxKeyLength = -1;
//        int maxValueLength = -1;
//
//        UpdatingContextImpl(final UpdaterImpl updater) {
//            this.updater = requireNonNull(updater);
//            this.payload = requireNonNull(updater.payload);
//        }
//
//        private void validateKeyCapacity(final int capacity) {
//            if (capacity > updater.maxKeyLength()) {
//                throw new IllegalArgumentException("Capacity " + capacity + " exceeds maximum allowed key size " +
//                        updater.maxKeyLength());
//            }
//        }
//        private void validateValueCapacity(final int capacity) {
//            if (capacity > updater.maxValueLength()) {
//                throw new IllegalArgumentException("Capacity " + capacity + " exceeds maximum allowed value size " +
//                        updater.maxValueLength());
//            }
//        }
//
//        UpdatingContext init(final int keyCapacity, final int valueCapacity) {
//            if (!isClosed()) {
//                abort();
//                throw new IllegalStateException("Updating context has not been closed");
//            }
//            validateKeyCapacity(keyCapacity);
//            validateValueCapacity(valueCapacity);
//            this.maxKeyLength = initPayloadBuffer(updater.lastOwnHeader, updater.lastOwnPayloadLength, Math.max(0, keyCapacity));
//            return this;
//        }
//
//        private int initPayloadBuffer(final long lastOwnHeader, final int lastOwnPayloadLen, final int capacity) {
//            assert capacity >= 0 && capacity <= maxCapacity;
//            final OffsetMapping pld = payload;
//            final int minRequired = capacity + Integer.BYTES;
//            final long payloadPosition = lastOwnHeader == NULL_HEADER ? 0L :
//                    Headers.nextPayloadPosition(Headers.payloadPosition(lastOwnHeader), lastOwnPayloadLen);
//            if (!pld.moveTo(payloadPosition)) {
//                throw payloadMoveException(updater, payloadPosition);
//            }
//            if (pld.bytesAvailable() < minRequired) {
//                moveToNextPayloadRegion(pld);
//            }
//            if (pld.position() > MAX_PAYLOAD_POSITION) {
//                throw payloadPositionExceedsMaxException(updater, payloadPosition);
//            }
//            buffer.wrap(pld.buffer(), Integer.BYTES, capacity);
//            return capacity;
//        }
//
//        private void moveToNextPayloadRegion(final OffsetMapping payload) {
//            if (!payload.moveToNextRegion()) {
//                final long regionStartPosition = payload.regionStartPosition() + payload.regionSize();
//                throw payloadMoveException(updater, regionStartPosition);
//            }
//        }
//
//        @Override
//        public void ensureCapacity(final int capacity) {
//            final int max = maxLength;
//            if (max < 0) {
//                throw new IllegalStateException("Appending context is closed");
//            }
//            if (capacity <= max) {
//                return;
//            }
//            validateCapacity(capacity);
//            final int minRequired = capacity + Integer.BYTES;
//            final OffsetMapping pld = payload;
//            if (pld.bytesAvailable() < minRequired) {
//                if (!updater.enableCopyFromPreviousRegion) {
//                    throw new IllegalStateException("Need to enable payload region cache (for async no less than map-ahead + 2) to fully support ensureCapacity(..)");
//                }
//                moveToNextPayloadRegion(pld);
//                //NOTE: copy data from buffer to the mapping buffer
//                //      --> the buffer is still wrapped at old region address
//                //      --> old region address is still mapped because a region cache is in use
//                pld.buffer().getBytes(Integer.BYTES, buffer, 0, max);
//            }
//            buffer.wrap(pld.buffer(), Integer.BYTES, capacity);
//            maxLength = capacity;
//        }
//
//        @Override
//        public MutableDirectBuffer buffer() {
//            return buffer;
//        }
//
//        @Override
//        public void abort() {
//            this.maxLength = -1;
//        }
//
//        @Override
//        public long commit(final int length) {
//            final int max = maxLength;
//            maxLength = -1;
//            buffer.wrap(0, 0);
//            validateLength(length, max);
//            final OffsetMapping pld = payload;
//            pld.buffer().putInt(0, length);
//            return updater.appendEntry(pld.position(), length + Integer.BYTES);
//        }
//
//        static void validateLength(final int length, final int maxLength) {
//            if (length < 0) {
//                throw new IllegalArgumentException("Length cannot be negative: " + length);
//            } else if (length > maxLength) {
//                if (maxLength >= 0) {
//                    throw new IllegalArgumentException("Length " + length + " exceeds max length " + maxLength);
//                }
//                throw new IllegalStateException("Appending context is closed");
//            }
//        }
//
//        @Override
//        public boolean isClosed() {
//            return maxLength < 0;
//        }
//
//        @Override
//        public String toString() {
//            return "UpdatingContextImpl" +
//                    ":dictionary=" + updater.dictionaryName +
//                    "|updaterId=" + updater.updaterId +
//                    "|closed=" + isClosed();
//        }
//    }
//
//    private static final class DeletingContextImpl implements DeletingContext.Key {
//        final UpdaterImpl updater;
//        final DeleteResult result;
//        final MutableDirectBuffer defaultBuffer;
//        final MutableDirectBuffer wrapBuffer = new UnsafeBuffer(0, 0);
//        MutableDirectBuffer currentBuffer;
//
//        DeletingContextImpl(final UpdaterImpl updater) {
//            this.updater = requireNonNull(updater);
//            this.result = new DeleteResult(updater);
//            this.defaultBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(updater.maxKeyLength()));
//        }
//
//        DeletingContextImpl init() {
//            result.close();
//            return init(defaultBuffer);
//        }
//
//        DeletingContextImpl init(final MutableDirectBuffer key, final int offset, final int keyLength) {
//            if (keyLength > updater.maxKeyLength()) {
//                throw new IllegalArgumentException("Key length " + keyLength + " exceeds max allowed key length " +
//                        updater.maxKeyLength());
//            }
//            result.close();
//            wrapBuffer.wrap(key, offset, keyLength);
//            return init(wrapBuffer);
//        }
//
//        private DeletingContextImpl init(final MutableDirectBuffer keyBuffer) {
//            if (!isClosed()) {
//                abort();
//                throw new IllegalStateException("Deleting context has not been closed");
//            }
//            this.currentBuffer = requireNonNull(keyBuffer);
//            return this;
//        }
//
//        @Override
//        public MutableDirectBuffer keyBuffer() {
//            return currentBuffer != null ? currentBuffer : wrapBuffer;
//        }
//
//        @Override
//        public void abort() {
//            final DirectBuffer buf = currentBuffer;
//            if (buf != null) {
//                if (buf == wrapBuffer) {
//                    wrapBuffer.wrap(0, 0);
//                }
//                this.currentBuffer = null;
//            }
//        }
//
//        @Override
//        public Result delete(final int keyLength) {
//            return deleteIfMatching(keyLength, null);
//        }
//
//        @Override
//        public Result deleteIfMatching(final int keyLength, final DeletePredicate condition) {
//            if (isClosed()) {
//                throw new IllegalStateException("DeleteContext is closed");
//            }
//            DirectBuffer keyBuf, wrapBuf;
//            if ((keyBuf = currentBuffer) != (wrapBuf = wrapBuffer)) {
//                wrapBuf.wrap(keyBuf, 0, keyLength);
//                keyBuf = wrapBuf;
//            } else {
//                assert keyBuf.capacity() == keyLength : "invalid keyLength";
//            }
//            currentBuffer = null;
//            final long index = updater.findIndexForKey(keyBuf);
//            if (index < 0) {
//                return result.keyNotFound(keyBuf);
//            }
//            final OffsetMapping hdr = updater.header;
//            final long keyHeader = Headers.moveAndGetKeyHeader(hdr, index);
//            final long valHeader = Headers.moveAndGetValueHeader(hdr, index);
//            if (valHeader ) {
//                final long valueHeader =
//            }
//            return null;
//        }
//
//        @Override
//        public long commit(final int length) {
//            final int max = maxLength;
//            maxLength = -1;
//            buffer.wrap(0, 0);
//            validateLength(length, max);
//            final OffsetMapping pld = payload;
//            pld.buffer().putInt(0, length);
//            return updater.appendEntry(pld.position(), length + Integer.BYTES);
//        }
//
//        static void validateLength(final int length, final int maxLength) {
//            if (length < 0) {
//                throw new IllegalArgumentException("Length cannot be negative: " + length);
//            } else if (length > maxLength) {
//                if (maxLength >= 0) {
//                    throw new IllegalArgumentException("Length " + length + " exceeds max length " + maxLength);
//                }
//                throw new IllegalStateException("Appending context is closed");
//            }
//        }
//
//        @Override
//        public boolean isClosed() {
//            return currentBuffer == null;
//        }
//
//        @Override
//        public String toString() {
//            return "DeletingContextImpl" +
//                    ":dictionary=" + updater.dictionaryName +
//                    "|updaterId=" + updater.updaterId +
//                    "|closed=" + isClosed();
//        }
//
//        static final class DeleteResult implements DeletingContext.Result {
//            final UpdaterImpl updater;
//            final DirectBuffer keyWrapper = new UnsafeBuffer(0, 0);
//            final DirectBuffer valueWrapper = new UnsafeBuffer(0, 0);
//            DirectBuffer key;
//            DirectBuffer value;
//            boolean present;
//
//            DeleteResult(final UpdaterImpl updater) {
//                this.updater = updater;
//            }
//
//            DeleteResult keyNotFound(final DirectBuffer key) {
//                return init(key, EmptyBuffer.INSTANCE, false);
//            }
//
//            DeleteResult keyFoundWithTombstoneValue(final long header) {
//                return init(key, EmptyBuffer.INSTANCE, true);
//            }
//
//            DeleteResult keyFoundWithValue(final DirectBuffer key, final DirectBuffer) {
//                return init(key, EmptyBuffer.INSTANCE, true);
//            }
//
//            DeleteResult init(final DirectBuffer key, final DirectBuffer value, final boolean present) {
//                this.key = requireNonNull(key);
//                this.value = requireNonNull(value);
//                this.present = present;
//                return this;
//            }
//
//            @Override
//            public DirectBuffer key() {
//                return key;
//            }
//
//            @Override
//            public DirectBuffer value() {
//                return value;
//            }
//
//            @Override
//            public boolean isPresent() {
//                return present;
//            }
//
//            @Override
//            public boolean isDeleted() {
//                return value != EmptyBuffer.INSTANCE;
//            }
//
//            @Override
//            public boolean isClosed() {
//                return key == EmptyBuffer.INSTANCE;
//            }
//
//            @Override
//            public void close() {
//                if (!isClosed()) {
//                    DirectBuffer buf;
//                    if ((buf = keyWrapper) == key) {
//                        buf.wrap(0, 0);
//                    }
//                    if ((buf = valueWrapper) == value) {
//                        buf.wrap(0, 0);
//                    }
//                    key = EmptyBuffer.INSTANCE;
//                    value = EmptyBuffer.INSTANCE;
//                    present = false;
//                }
//            }
//
//            @Override
//            public String toString() {
//                return "DeleteResult" +
//                        ":dictionary=" + updater.dictionaryName +
//                        "|updaterId=" + updater.updaterId +
//                        "|present=" + present +
//                        "|deleted=" + isDeleted() +
//                        "|closed=" + isClosed();
//            }
//        }
//    }
//
//    String updaterName() {
//        return dictionaryName + ".updater-" + updaterId;
//    }
//
//    @Override
//    public String toString() {
//        return "UpdaterImpl:dictionary=" + dictionaryName + "|updaterId=" + updaterId + "|closed=" + closed;
//    }
//
//}
