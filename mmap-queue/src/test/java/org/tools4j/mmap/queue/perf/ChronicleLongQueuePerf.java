/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2024 tools4j.org (Marco Terzer, Anton Anufriev)
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
package org.tools4j.mmap.queue.perf;

import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.wire.WireType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.queue.util.FileUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ChronicleLongQueuePerf {
    private static final long MAX_WAIT_MILLIS = TimeUnit.SECONDS.toMillis(15);
    private static final Logger LOGGER = LoggerFactory.getLogger(ChronicleLongQueuePerf.class);

    public static void main(final String... args) throws Throwable {
        final Path tempDir = Files.createTempDirectory(ChronicleLongQueuePerf.class.getSimpleName());
        tempDir.toFile().deleteOnExit();

        final String name = "perf";
        final String path = tempDir + "/" + name + ".cq4";

        try (ChronicleQueue queue = ChronicleQueue.singleBuilder()
                .path(path)
                .wireType(WireType.BINARY_LIGHT)
                .build()) {

            final long messagesPerSecond = 1_000_000;
            final int messages = 11_000_000;
            final int warmup = 1_000_000;

            LOGGER.info("Queue created: {}", queue);

            final ChronicleLongSender sender = new ChronicleLongSender(queue::acquireAppender, messagesPerSecond, messages);
            final ChronicleLongReceiver receiver0 = new ChronicleLongReceiver(0, queue::createTailer, warmup, messages);
            final ChronicleLongReceiver receiver1 = new ChronicleLongReceiver(1, queue::createTailer, warmup, messages);

            sender.start();
            receiver0.start();
            receiver1.start();

            final long maxWaitNanos = TimeUnit.MILLISECONDS.toNanos(MAX_WAIT_MILLIS);
            final long startTime = System.nanoTime();
            assertTrue(sender.join(System.nanoTime() + maxWaitNanos - startTime, TimeUnit.NANOSECONDS));
            assertTrue(receiver0.join(System.nanoTime() + maxWaitNanos - startTime, TimeUnit.NANOSECONDS));
            assertTrue(receiver1.join(System.nanoTime() + maxWaitNanos - startTime, TimeUnit.NANOSECONDS));
            receiver0.printHistogram();
            receiver1.printHistogram();
        }
        FileUtil.deleteRecursively(tempDir.toFile());
    }
}