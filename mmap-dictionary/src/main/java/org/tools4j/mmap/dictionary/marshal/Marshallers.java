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
package org.tools4j.mmap.dictionary.marshal;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.util.Objects.requireNonNull;

public enum Marshallers {
    ;
    public static final IntMarshaller INT = new IntMarshaller() {
        @Override
        public void marshal(final int value, final MutableDirectBuffer buffer) {
            buffer.putInt(0, value, LITTLE_ENDIAN);
        }

        @Override
        public int unmarshalAsInt(final DirectBuffer buffer) {
            return buffer.getInt(0, LITTLE_ENDIAN);
        }

        @Override
        public String toString() {
            return "INT";
        }
    };

    public static final LongMarshaller LONG = new LongMarshaller() {
        @Override
        public void marshal(final long value, final MutableDirectBuffer buffer) {
            buffer.putLong(0, value, LITTLE_ENDIAN);
        }

        @Override
        public long unmarshalAsLong(final DirectBuffer buffer) {
            return buffer.getLong(0, LITTLE_ENDIAN);
        }

        @Override
        public String toString() {
            return "LONG";
        }
    };
    public static final DoubleMarshaller DOUBLE = new DoubleMarshaller() {
        @Override
        public void marshal(final double value, final MutableDirectBuffer buffer) {
            buffer.putDouble(0, value, LITTLE_ENDIAN);
        }

        @Override
        public double unmarshalAsDouble(final DirectBuffer buffer) {
            return buffer.getDouble(0, LITTLE_ENDIAN);
        }

        @Override
        public String toString() {
            return "DOUBLE";
        }
    };

    public static <E extends Enum<E>> Marshaller<E> enumMarshaller(final Class<E> enumClass) {
        requireNonNull(enumClass);
        return new Marshaller<E>() {
            final E[] constants = enumClass.getEnumConstants();
            final String name = "ENUM<" + enumClass.getSimpleName() + ">";
            @Override
            public int maxByteCapacity() {
                return Integer.BYTES;
            }

            @Override
            public int marshal(final E value, final MutableDirectBuffer buffer) {
                final int ordinal = value == null ? -1 : value.ordinal();
                buffer.putInt(0, ordinal, LITTLE_ENDIAN);
                return Integer.BYTES;
            }

            @Override
            public E unmarshal(final DirectBuffer buffer) {
                final int ordinal = buffer.getInt(0, LITTLE_ENDIAN);
                return ordinal >= 0 && ordinal <= constants.length ? constants[ordinal] : null;
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }

    public static Marshaller<String> asciiStringMarshaller(final int maxBytes) {
        if (maxBytes < 0) {
            throw new IllegalArgumentException("Max bytes cannot be negative: " + maxBytes);
        }
        return new Marshaller<String>() {
            final String name = "ASCII<" + maxBytes + ">";
            @Override
            public int maxByteCapacity() {
                return maxBytes + Integer.BYTES;
            }

            @Override
            public int marshal(final String value, final MutableDirectBuffer buffer) {
                if (value == null) {
                    buffer.putInt(0, -1, LITTLE_ENDIAN);
                    return Integer.BYTES;
                }
                final int len = value.length();
                if (len > maxBytes) {
                    throw new IllegalArgumentException("String length exceeds max of " + maxBytes + ": " + value);
                }
                buffer.putInt(0, len, LITTLE_ENDIAN);
                buffer.putStringWithoutLengthAscii(Integer.BYTES, value);
                return Integer.BYTES + len;
            }

            @Override
            public String unmarshal(final DirectBuffer buffer) {
                final int len = buffer.getInt(0, LITTLE_ENDIAN);
                return len >= 0 ? buffer.getStringWithoutLengthAscii(0, len) : null;
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }

}
