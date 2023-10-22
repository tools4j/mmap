/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2023 tools4j.org (Marco Terzer, Anton Anufriev)
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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.util.Objects;
import java.util.stream.Stream;

public class QueueLatencyTest {

    private static AsyncRuntime asyncRuntime;

    private static class TestRuntime {
        public TestRuntime(final long messagesPerSecond,
                           final int messageLength,
                           final RegionRingFactory regionRingFactory) throws Exception {
            this.messagesPerSecond = messagesPerSecond;
            this.messageLength = messageLength;
            this.regionRingFactory = Objects.requireNonNull(regionRingFactory);
            setup();
        }

        private final long messagesPerSecond;
        private final int messageLength;
        private final RegionRingFactory regionRingFactory;

        private Queue queue;
        private Appender appender;
        private Poller poller;

        private Path tempDir;

        void setup() throws Exception {
            tempDir = Files.createTempDirectory(QueueLatencyTest.class.getSimpleName());
            tempDir.toFile().deleteOnExit();

            queue = Queue.builder("regiontest", tempDir.toString(), regionRingFactory).build();

            appender = queue.createAppender();
            poller = queue.createPoller();
        }

        void tearDown() throws IOException {
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
    }

    private TestRuntime testRuntime;

    public static Stream<Arguments> testRunParameters() {
        asyncRuntime = AsyncRuntime.createDefault();

        return Stream.of(
                Arguments.of(160000, 100, RegionRingFactories.sync()),
                Arguments.of(500000, 100, RegionRingFactories.sync()),
                Arguments.of(160000, 1000, RegionRingFactories.sync()),
                Arguments.of(500000, 1000, RegionRingFactories.sync()),

                Arguments.of(160000, 100, RegionRingFactories.async(asyncRuntime)),
                Arguments.of(500000, 100, RegionRingFactories.async(asyncRuntime)),
                Arguments.of(160000, 1000, RegionRingFactories.async(asyncRuntime)),
                Arguments.of(500000, 1000, RegionRingFactories.async(asyncRuntime))
        );
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (testRuntime != null) {
            testRuntime.tearDown();
            testRuntime = null;
        }
    }

    @AfterAll
    public static void afterAll() {
        if (asyncRuntime != null) {
            asyncRuntime.close();
        }
    }

    @ParameterizedTest
    @MethodSource("testRunParameters")
    public void latencyTest(final long messagesPerSecond,
                            final int messageLength,
                            final RegionRingFactory regionRingFactory) throws Throwable {
        //given
        testRuntime = new TestRuntime(messagesPerSecond, messageLength, regionRingFactory);
        final int warmup = 100000;
        final int hot = 200000;
        final int messages = warmup + hot;

        System.out.println("\twarmup + count      : " + warmup + " + " + hot + " = " + messages);
        System.out.println("\tmessagesPerSecond   : " + messagesPerSecond);
        System.out.println("\tmessageSize         : " + messageLength + " bytes");
        System.out.println();

        final Sender sender = new Sender((byte) 0, testRuntime.queue::createAppender, messagesPerSecond, messages, messageLength);
        final Receiver receiver0 = new Receiver(0, testRuntime.queue::createPoller, warmup, messageLength);

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
                    final QueueLatencyTest latencyTest = new QueueLatencyTest();
                    try {
                        latencyTest.latencyTest(mps, byteLen, regionRingFactory);
                    } finally {
                        latencyTest.tearDown();
                    }
                }
            }
        }
    }
}