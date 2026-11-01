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
package org.tools4j.mmap.perf;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.tools4j.mmap.mapping.config.SharingPolicy;
import org.tools4j.mmap.mapping.impl.OS;
import org.tools4j.mmap.queue.config.QueueConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Latency test for {@link org.tools4j.mmap.queue.api.Queue} through {@link MmapPerfQueue} with a concurrent
 * {@link Sender} and {@link Receiver}.
 */
public class QueueLatencyTest {

    private static final int REGION_CACHE_SIZE = 16;
    private static final int REGIONS_TO_MAP_AHEAD = 8;
    private static final int AHEAD_MAPPING_CACHE_SIZE = 16;
    private static final SharingPolicy SHARING_POLICY = SharingPolicy.SHARED;
    private static final long MAX_WAIT_SECONDS = OS.ifWindows(60, 20);
    private static final ThreadFactory THREAD_FACTORY = Thread::new;

    private Path tempDir;

    @BeforeEach
    public void createTempDir() throws IOException {
        tempDir = Files.createTempDirectory(QueueLatencyTest.class.getSimpleName());
    }

    @AfterEach
    public void deleteTempDir() throws IOException {
        try (final Stream<Path> paths = Files.walk(tempDir)) {
            for (final Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

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
                                .regionsToMapAhead(REGIONS_TO_MAP_AHEAD)
                                .aheadMappingCacheSize(AHEAD_MAPPING_CACHE_SIZE)
                                .mappingRuntime(SHARING_POLICY)
                        )
                ).toImmutableQueueConfig();
    }

    public static Stream<Arguments> testRunParameters() {
        final QueueConfig syncConfig = syncConfig();
        final QueueConfig asyncConfig = asyncConfig();
        return Stream.of(
                Arguments.of(200_000, 100, "sync", syncConfig),
                Arguments.of(500_000, 100, "sync", syncConfig),
                Arguments.of(1_000_000, 100, "sync", syncConfig),
                Arguments.of(2_000_000, 100, "sync", syncConfig),
                Arguments.of(200_000, 1000, "sync", syncConfig),
                Arguments.of(500_000, 1000, "sync", syncConfig),
                Arguments.of(1_000_000, 1000, "sync", syncConfig),
                Arguments.of(2_000_000, 1000, "sync", syncConfig),

                Arguments.of(200_000, 100, "async", asyncConfig),
                Arguments.of(500_000, 100, "async", asyncConfig),
                Arguments.of(1_000_000, 100, "async", asyncConfig),
                Arguments.of(2_000_000, 100, "async", asyncConfig),
                Arguments.of(200_000, 1000, "async", asyncConfig),
                Arguments.of(500_000, 1000, "async", asyncConfig),
                Arguments.of(1_000_000, 1000, "async", asyncConfig),
                Arguments.of(2_000_000, 1000, "async", asyncConfig)
        );
    }

    @ParameterizedTest(name = "latencyTest[{index}] -> {0} msg/s, {1} bytes, {2}")
    @MethodSource("testRunParameters")
    public void latencyTest(final long messagesPerSecond,
                            final int messageLength,
                            final String type,
                            final QueueConfig config) throws Throwable {
        //given
        final int warmup = 100_000;
        final int hot = 200_000;
        final int messages = warmup + hot;

        try (final MmapPerfQueue queue = new MmapPerfQueue(tempDir.toFile(), config, PerfOptions.ReaderType.POLLER)) {
            final StringBuilder sb = new StringBuilder(1024);
            queue.describeSettings(sb);
            sb.append(getClass().getSimpleName()).append(" (").append(type).append("):\n")
                    .append("\twarmup + count      : ").append(warmup).append(" + ").append(hot).append(" = ")
                    .append(messages).append('\n')
                    .append("\tmessagesPerSecond   : ").append(messagesPerSecond).append('\n')
                    .append("\tmessageSize         : ").append(messageLength).append(" bytes\n");
            System.out.println(sb);

            final Sender sender = new Sender(0, THREAD_FACTORY, queue, messagesPerSecond, messages, messageLength);
            final Receiver receiver = new Receiver(0, THREAD_FACTORY, queue, warmup, messages, messageLength);

            //when
            receiver.worker().start();
            sender.worker().start();

            //then
            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(MAX_WAIT_SECONDS);
            assertTrue(sender.worker().join(deadline), "sender completed");
            assertTrue(receiver.worker().join(deadline), "receiver completed");
            sender.printResult();
            receiver.printHistogram();
        }
    }
}
