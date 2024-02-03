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

import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.tools4j.mmap.queue.api.Appender;
import org.tools4j.mmap.queue.api.Poller;
import org.tools4j.mmap.queue.api.Queue;
import org.tools4j.mmap.queue.perf.Receiver;
import org.tools4j.mmap.queue.perf.Sender;
import org.tools4j.mmap.queue.util.FileUtil;
import org.tools4j.mmap.region.api.AsyncRuntime;
import org.tools4j.mmap.region.api.RegionMapperFactory;
import org.tools4j.mmap.region.impl.OS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class QueueLatencyTest {

    private static final IdleStrategy ASYNC_RUNTIME_IDLE_STRATEGY = BusySpinIdleStrategy.INSTANCE;
    //private static final IdleStrategy ASYNC_RUNTIME_IDLE_STRATEGY = new BackoffIdleStrategy();
    //private static final int REGION_SIZE = QueueBuilder.DEFAULT_REGION_SIZE;
    private static final int REGION_SIZE = 128 * 1024;//fast latencies, but high outliers due to many mappings
    //private static final int MAX_FILE_SIZE = QueueBuilder.DEFAULT_MAX_FILE_SIZE;
    private static final int MAX_FILE_SIZE = 4 * 1024 * 1024;//good to test file rolling
    private static final int REGION_CACHE_SIZE = 4;
    private static final int REGIONS_TO_MAP_AHEAD = 1;
    private static final long MAX_WAIT_MILLIS = TimeUnit.SECONDS.toMillis(20);
    private static AsyncRuntime asyncRuntime;

    private static class TestRuntime {
        public TestRuntime(final RegionMapperFactory regionMapperFactory) throws Exception {
            this.regionMapperFactory = Objects.requireNonNull(regionMapperFactory);
            setup();
        }

        private final RegionMapperFactory regionMapperFactory;

        private Queue queue;
        private Appender appender;
        private Poller poller;

        private Path tempDir;

        void setup() throws Exception {
            tempDir = Files.createTempDirectory(QueueLatencyTest.class.getSimpleName());
            tempDir.toFile().deleteOnExit();

            //NOTE: build fails sometimes on Windows, hence we increase the timeouts
            final long readTimeout = (OS.WINDOWS ? 5 : 1) * QueueBuilder.DEFAULT_READ_TIMEOUT_MILLIS;
            final long writeTimeout = (OS.WINDOWS ? 5 : 1) * QueueBuilder.DEFAULT_WRITE_TIMEOUT_MILLIS;

            queue = Queue
                    .builder("queue-latency-test", tempDir.toString(), regionMapperFactory)
                    .maxFileSize(MAX_FILE_SIZE)
                    .regionSize(REGION_SIZE)
                    .regionCacheSize(REGION_CACHE_SIZE)
                    .regionsToMapAhead(REGIONS_TO_MAP_AHEAD)
                    .readTimeout(readTimeout, TimeUnit.MILLISECONDS)
                    .writeTimeout(writeTimeout, TimeUnit.MILLISECONDS)
                    .build();

            appender = queue.createAppender();
            poller = queue.createPoller();
        }

        void tearDown() {
            if (appender != null) {
                appender.close();
                appender = null;
            }
            if (poller != null) {
                poller.close();
                poller = null;
            }
            try {
                FileUtil.deleteRecursively(tempDir.toFile());
            } catch (final IOException e) {
                System.err.println("Deleting temp files failed: tempDir=" + tempDir + ", e=" + e);
            }
        }
    }

    private TestRuntime testRuntime;

    public static Stream<Arguments> testRunParameters() {
        asyncRuntime = AsyncRuntime.create(ASYNC_RUNTIME_IDLE_STRATEGY);

        return Stream.of(
                Arguments.of(200_000, 100, RegionMapperFactory.SYNC),
                Arguments.of(500_000, 100, RegionMapperFactory.SYNC),
                Arguments.of(1_000_000, 100, RegionMapperFactory.SYNC),
                Arguments.of(2_000_000, 100, RegionMapperFactory.SYNC),
                Arguments.of(200_000, 1000, RegionMapperFactory.SYNC),
                Arguments.of(500_000, 1000, RegionMapperFactory.SYNC),
                Arguments.of(1_000_000, 1000, RegionMapperFactory.SYNC),
                Arguments.of(2_000_000, 1000, RegionMapperFactory.SYNC),

                Arguments.of(200_000, 100, RegionMapperFactory.async("ASYNC", asyncRuntime)),
                Arguments.of(500_000, 100, RegionMapperFactory.async("ASYNC", asyncRuntime)),
                Arguments.of(1_000_000, 100, RegionMapperFactory.async("ASYNC", asyncRuntime)),
                Arguments.of(2_000_000, 100, RegionMapperFactory.async("ASYNC", asyncRuntime)),
                Arguments.of(200_000, 1000, RegionMapperFactory.async("ASYNC", asyncRuntime)),
                Arguments.of(500_000, 1000, RegionMapperFactory.async("ASYNC", asyncRuntime)),
                Arguments.of(1_000_000, 1000, RegionMapperFactory.async("ASYNC", asyncRuntime)),
                Arguments.of(2_000_000, 1000, RegionMapperFactory.async("ASYNC", asyncRuntime))
        );
    }

    @AfterEach
    public void tearDown() {
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
                            final RegionMapperFactory regionMapperFactory) throws Throwable {
        //given
        testRuntime = new TestRuntime(regionMapperFactory);
        final int warmup = 100_000;
        final int hot = 200_000;
        final int messages = warmup + hot;

        System.out.println("\tregionMapperFactory : " + regionMapperFactory);
        System.out.println("\twarmup + count      : " + warmup + " + " + hot + " = " + messages);
        System.out.println("\tmessagesPerSecond   : " + messagesPerSecond);
        System.out.println("\tmessageSize         : " + messageLength + " bytes");
        System.out.println();

        final Sender sender = new Sender((byte) 0, testRuntime.queue::createAppender, messagesPerSecond, messages, messageLength);
        final Receiver receiver0 = new Receiver(0, testRuntime.queue::createPoller, warmup, messageLength);

        sender.start();
        receiver0.start();

        final long maxWaitNanos = TimeUnit.MILLISECONDS.toNanos(MAX_WAIT_MILLIS);
        final long startTime = System.nanoTime();
        assertTrue(sender.join(System.nanoTime() + maxWaitNanos - startTime, TimeUnit.NANOSECONDS));
        assertTrue(receiver0.join(System.nanoTime() + maxWaitNanos - startTime, TimeUnit.NANOSECONDS));

        receiver0.printHistogram();
    }

    public static void main(String... args) throws Throwable {
        final int byteLen = 2000;
        final int[] messagesPerSec = {200_000, 500_000, 1_000_000, 2_000_000};
        try (AsyncRuntime asyncRuntime = AsyncRuntime.create(ASYNC_RUNTIME_IDLE_STRATEGY)) {
            for (final RegionMapperFactory regionMapperFactory : Arrays.asList(RegionMapperFactory.SYNC,
                    RegionMapperFactory.async("ASYNC", asyncRuntime))) {
                for (final int mps : messagesPerSec) {
                    final QueueLatencyTest latencyTest = new QueueLatencyTest();
                    try {
                        latencyTest.latencyTest(mps, byteLen, regionMapperFactory);
                    } finally {
                        latencyTest.tearDown();
                    }
                }
            }
        }
    }
}