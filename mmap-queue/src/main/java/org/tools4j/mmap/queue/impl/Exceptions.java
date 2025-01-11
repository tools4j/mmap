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
package org.tools4j.mmap.queue.impl;

enum Exceptions {
    ;

    static IllegalArgumentException invalidIndexException(final String name, final long index) {
        return new IllegalArgumentException("Invalid index for " + name + ": " + index);
    }

    static IllegalStateException headerMoveException(final AppenderImpl appender, final long position) {
        return mappingMoveException(appender.appenderName() + ".header", position);
    }

    static IllegalStateException payloadMoveException(final AppenderImpl appender, final long position) {
        return mappingMoveException(appender.appenderName() + ".payload", position);
    }

    static IllegalStateException payloadMoveException(final EntryReaderImpl reader,
                                                      final int appenderId,
                                                      final long position) {
        return mappingMoveException(reader.readerName() + "-" + appenderId + ".payload", position);
    }

    static IllegalStateException payloadMoveException(final EntryIteratorImpl iterator,
                                                      final int appenderId,
                                                      final long position) {
        return mappingMoveException(iterator.iteratorName() + "-" + appenderId + ".payload", position);
    }

    private static IllegalStateException mappingMoveException(final String name, final long position) {
        throw new IllegalStateException("Moving " + name + " mapping to position " + position + " failed");
    }
}
