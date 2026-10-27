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
package org.tools4j.mmap.queue.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tools4j.mmap.queue.api.Appender;
import org.tools4j.mmap.queue.api.Move;
import org.tools4j.mmap.queue.api.Poller;
import org.tools4j.mmap.queue.api.Queue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for {@link Appender} and {@link AppenderImpl}.
 */
class AppenderTest {

    @TempDir
    Path tempDir;

    @Test
    void appenderReusingIdAppendsAfterEntriesOfPreviousOwner() {
        final File dir = tempDir.resolve("queue").toFile();
        try (final Queue queue = Queue.open(dir)) {
            try (final Appender appender = queue.createAppender()) {
                append(appender, "first", "second entry");
            }
            //the new appender reuses the released ID, and must not overwrite the previous owner's last entry
            try (final Appender appender = queue.createAppender()) {
                append(appender, "third");
            }
        }
        try (final Queue queue = Queue.open(dir)) {
            try (final Appender appender = queue.createAppender()) {
                append(appender, "fourth");
            }
            assertEquals(List.of("first", "second entry", "third", "fourth"), pollAll(queue.createPoller()));
        }
    }

    private static void append(final Appender appender, final String... entries) {
        for (final String entry : entries) {
            appender.append(entry.getBytes(StandardCharsets.US_ASCII));
        }
    }

    private static List<String> pollAll(final Poller poller) {
        final List<String> entries = new ArrayList<>();
        while (poller.poll((index, buffer, offset, length) -> {
            entries.add(buffer.getStringWithoutLengthAscii(offset, length));
            return Move.NEXT;
        }) > 0) {
            //poll until no more entries
        }
        return entries;
    }
}
