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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.tools4j.mmap.queue.api.Appender;
import org.tools4j.mmap.queue.api.Poller;
import org.tools4j.mmap.queue.api.Queue;
import org.tools4j.mmap.queue.config.QueueConfig;
import org.tools4j.mmap.queue.perf.Receiver;
import org.tools4j.mmap.queue.perf.Sender;
import org.tools4j.mmap.queue.util.ConfigPrinter;
import org.tools4j.mmap.queue.util.FileUtil;
import org.tools4j.mmap.mapping.config.SharingPolicy;
import org.tools4j.mmap.mapping.impl.OS;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QueueLatencyTest {

    private static final int REGION_CACHE_SIZE = 16;
    private static final int REGIONS_TO_MAP_AHED = 8;
    private static final int AHEAD_MAPPING_CACHE_SIZE = 16;
    private static final SharingPolicy SHARING_POLICY = SharingPolicy.SHARED;
    private static final long MAX_WAIT_MILLIS = TimeUnit.SECONDS.toMillis(OS.ifWindows(60, 20));

    static {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn");
    }

    private static class TestRuntime {
        public TestRuntime(final QueueConfig config) throws Exception {
            this.config = requireNonNull(config);
            setup();
        }

        private final QueueConfig config;
        private Queue queue;
        private Appender appender;
        private Poller poller;

        private Path tempDir;

        void setup() throws Exception {
            tempDir = Files.createTempDirectory(QueueLatencyTest.class.getSimpleName());
            tempDir.toFile().deleteOnExit();

            queue = Queue.create(new File(tempDir.toFile(), "mmap-queue"), config);

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

            if (queue != null) {
                queue.close();
                queue = null;
            }
            try {
                FileUtil.deleteRecursively(tempDir.toFile());
            } catch (final IOException e) {
                System.err.println("Deleting temp files failed: tempDir=" + tempDir + ", e=" + e);
            }
        }
    }

    private TestRuntime testRuntime;

    private static QueueConfig syncConfig() {
        return QueueConfig.configure()
                .mappingStrategy(config -> config
                        .cacheSize(REGION_CACHE_SIZE)
                        .asyncMapping(false)
                ).toImmutableQueueConfig();
    }

    private static QueueConfig asyncConfig() {
        return QueueConfig.configure()
                .mappingStrategy(config -> config
                        .cacheSize(REGION_CACHE_SIZE)
                        .asyncMapping(async -> async
                                .regionsToMapAhead(REGIONS_TO_MAP_AHED)
                                .aheadMappingCacheSize(AHEAD_MAPPING_CACHE_SIZE)
                                .mappingRuntime(SHARING_POLICY)
                        )
                ).toImmutableQueueConfig();
    }

    private static ThreadFactory threadFactory() {
//        try {
//            return new AffinityThreadFactory("affinity", AffinityStrategies.ANY);
//        } catch (final Throwable t) {
//            // Ignore
//        }
        return Thread::new;
    }

    public static Stream<Arguments> testRunParameters() {
        final ThreadFactory threadFactory = threadFactory();
        final QueueConfig syncConfig = syncConfig();
        final QueueConfig asyncConfig = asyncConfig();

        return Stream.of(
                Arguments.of(200_000, 100, "sync", threadFactory, syncConfig),
                Arguments.of(500_000, 100, "sync", threadFactory, syncConfig),
                Arguments.of(1_000_000, 100, "sync", threadFactory, syncConfig),
                Arguments.of(2_000_000, 100, "sync", threadFactory, syncConfig),
                Arguments.of(200_000, 1000, "sync", threadFactory, syncConfig),
                Arguments.of(500_000, 1000, "sync", threadFactory, syncConfig),
                Arguments.of(1_000_000, 1000, "sync", threadFactory, syncConfig),
                Arguments.of(2_000_000, 1000, "sync", threadFactory, syncConfig),

                Arguments.of(200_000, 100, "async", threadFactory, asyncConfig),
                Arguments.of(500_000, 100, "async", threadFactory, asyncConfig),
                Arguments.of(1_000_000, 100, "async", threadFactory, asyncConfig),
                Arguments.of(2_000_000, 100, "async", threadFactory, asyncConfig),
                Arguments.of(200_000, 1000, "async", threadFactory, asyncConfig),
                Arguments.of(500_000, 1000, "async", threadFactory, asyncConfig),
                Arguments.of(1_000_000, 1000, "async", threadFactory, asyncConfig),
                Arguments.of(2_000_000, 1000, "async", threadFactory, asyncConfig)
        );
    }

    @AfterEach
    public void tearDown() {
        if (testRuntime != null) {
            testRuntime.tearDown();
            testRuntime = null;
        }
    }

    @ParameterizedTest(name = "latencyTest[{index}] -> {0} msg/s, {1} bytes, {2}")
    @MethodSource("testRunParameters")
    public void latencyTest(final long messagesPerSecond,
                            final int messageLength,
                            final String type,
                            final ThreadFactory threadFactory,
                            final QueueConfig config) throws Throwable {
        //given
        testRuntime = new TestRuntime(config);
        final int warmup = 100_000;
        final int hot = 200_000;
        final int messages = warmup + hot;

        ConfigPrinter.printConfig(config);
        System.out.println(getClass().getSimpleName() + ":");
        System.out.println("\twarmup + count      : " + warmup + " + " + hot + " = " + messages);
        System.out.println("\tmessagesPerSecond   : " + messagesPerSecond);
        System.out.println("\tmessageSize         : " + messageLength + " bytes");
        System.out.println();

        final Sender sender = new Sender((byte) 0, threadFactory, testRuntime.queue::createAppender, messagesPerSecond, messages, messageLength);
        final Receiver receiver0 = new Receiver(0, threadFactory, testRuntime.queue::createPoller, warmup, messageLength);

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
        final ThreadFactory threadFactory = threadFactory();
        final QueueConfig syncConfig = syncConfig();
        final QueueConfig asyncConfig = asyncConfig();
        for (final QueueConfig queueConfig : Arrays.asList(syncConfig, asyncConfig)) {
            for (final int mps : messagesPerSec) {
                final QueueLatencyTest latencyTest = new QueueLatencyTest();
                try {
                    final String type = queueConfig == syncConfig ? "sync" : "async";
                    latencyTest.latencyTest(mps, byteLen, type, threadFactory, queueConfig);
                } finally {
                    latencyTest.tearDown();
                }
            }
        }
    }
}