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
package org.tools4j.mmap.queue.impl;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.tools4j.mmap.queue.api.Appender;
import org.tools4j.mmap.queue.api.Poller;
import org.tools4j.mmap.queue.api.Queue;
import org.tools4j.mmap.queue.util.FileUtil;
import org.tools4j.mmap.region.api.AsyncRuntime;
import org.tools4j.mmap.region.api.RegionRingFactory;
import org.tools4j.mmap.region.impl.RegionRingFactories;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

@RunWith(Parameterized.class)
public class QueueLatencyTest {
    private static AsyncRuntime asyncRuntime;


    private final long messagesPerSecond;
    private final int messageLength;
    private final RegionRingFactory regionRingFactory;

    private Queue queue;
    private Appender appender;
    private Poller poller;

    private Path tempDir;

    @Parameterized.Parameters(name = "{index}: MPS={0}, NBYTES={1}, QUEUE={2}, POLLER={3}")
    public static Collection<?> testRunParameters() {
        asyncRuntime = AsyncRuntime.createDefault();

        return Arrays.asList(new Object[][]{
                {160000, 100, RegionRingFactories.sync()},
                {500000, 100, RegionRingFactories.sync()},
                {160000, 1000, RegionRingFactories.sync()},
                {500000, 1000, RegionRingFactories.sync()},

                {160000, 100, RegionRingFactories.async(asyncRuntime)},
                {500000, 100, RegionRingFactories.async(asyncRuntime)},
                {160000, 1000, RegionRingFactories.async(asyncRuntime)},
                {500000, 1000, RegionRingFactories.async(asyncRuntime)},
        });
    }

    public QueueLatencyTest(final long messagesPerSecond,
                            final int messageLength,
                            final RegionRingFactory regionRingFactory) {
        this.messagesPerSecond = messagesPerSecond;
        this.messageLength = messageLength;
        this.regionRingFactory = Objects.requireNonNull(regionRingFactory);
    }

    @Before
    public void setup() throws Exception {
        tempDir = Files.createTempDirectory(QueueLatencyTest.class.getSimpleName());
        tempDir.toFile().deleteOnExit();

        queue = Queue.builder("regiontest", tempDir.toString(), regionRingFactory).build();

        appender = queue.createAppender();
        poller = queue.createPoller();
    }

    @After
    public void tearDown() throws IOException {
        if (appender != null) {
            appender.close();
            appender = null;
        }
        if (poller != null) {
            poller.close();
            poller = null;
        }
        FileUtil.deleteRecursively(tempDir.toFile());
    }

    @AfterClass
    public static void afterClass() {
        if (asyncRuntime != null) {
            asyncRuntime.close();
        }
    }

    @Test
    public void latencyTest() throws Throwable {
        //given
        final int warmup = 100000;
        final int hot = 200000;
        final int messages = warmup + hot;

        System.out.println("\twarmup + count      : " + warmup + " + " + hot + " = " + messages);
        System.out.println("\tmessagesPerSecond   : " + messagesPerSecond);
        System.out.println("\tmessageSize         : " + messageLength + " bytes");
        System.out.println();

        final Sender sender = new Sender((byte) 0, queue::createAppender, messagesPerSecond, messages, messageLength);
        final Receiver receiver0 = new Receiver(0, queue::createPoller, warmup, messageLength);

        sender.start();
        receiver0.start();

        sender.join();
        receiver0.join();
        receiver0.printHistogram();
    }

    public static void main(String... args) throws Throwable {
        final int byteLen = 2000;
        final int[] messagesPerSec = {160000, 160000, 160000, 160000, 160000, 160000};
        try (AsyncRuntime asyncRuntime = AsyncRuntime.createDefault()) {
            for (final RegionRingFactory regionRingFactory : Arrays.asList(RegionRingFactories.sync(),
                    RegionRingFactories.async(asyncRuntime))) {
                for (final int mps : messagesPerSec) {
                    final QueueLatencyTest latencyTest =
                            new QueueLatencyTest(mps, byteLen, regionRingFactory);
                    latencyTest.setup();
                    try {
                        latencyTest.latencyTest();
                    } finally {
                        latencyTest.tearDown();
                    }
                }
            }
        }
    }
}