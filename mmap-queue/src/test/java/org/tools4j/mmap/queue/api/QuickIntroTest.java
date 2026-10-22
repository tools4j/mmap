/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2026 tools4j.org (Marco Terzer, Anton Anufriev)
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
package org.tools4j.mmap.queue.api;

import org.agrona.DirectBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quick introduction to queues, used as example in the README.
 */
class QuickIntroTest {

    @Test
    @SuppressWarnings({"ControlFlowStatementWithoutBraces", "StatementWithEmptyBody"})
    void appendAndRead(@TempDir final Path tempDir) {
        final File directory = tempDir.resolve("myQueue").toFile();
        try (final Queue queue = Queue.open(directory)) {

            //append: copy from a byte array or buffer, or encode in place (zero-copy) and commit
            final Appender appender = queue.createAppender();
            appender.append("Hello".getBytes(StandardCharsets.US_ASCII));
            try (final AppendingContext context = appender.appending(64)) {
                final int length = context.buffer().putStringWithoutLengthAscii(0, "World");
                context.commit(length);
            }

            //poll: sequential retrieval via callback, the returned move determines the next entry
            final Poller poller = queue.createPoller();
            final List<String> polled = new ArrayList<>();
            while (poller.poll((index, buffer, offset, length) -> {
                polled.add(index + ":" + buffer.getStringWithoutLengthAscii(offset, length));
                return Move.NEXT;
            }) > 0);
            assertEquals(List.of("0:Hello", "1:World"), polled);

            //read: random access by index
            final EntryReader reader = queue.createEntryReader();
            try (final ReadingContext entry = reader.reading(1)) {
                assertTrue(entry.hasEntry());
                assertEquals("World", text(entry.buffer()));
            }

            //iterate: forward or reverse from a start index
            final EntryIterator iterator = queue.createEntryIterator();
            final List<String> iterated = new ArrayList<>();
            try (final IterableContext entries = iterator.readingFromLast().reverse()) {
                for (final Entry entry : entries) {
                    iterated.add(text(entry.buffer()));
                }
            }
            assertEquals(List.of("World", "Hello"), iterated);

            //index: first and last index, size
            final IndexReader indexReader = queue.createIndexReader();
            assertEquals(0, indexReader.firstIndex());
            assertEquals(1, indexReader.lastIndex());
            assertEquals(2, indexReader.size());
        }
    }

    private static String text(final DirectBuffer buffer) {
        return buffer.getStringWithoutLengthAscii(0, buffer.capacity());
    }
}
