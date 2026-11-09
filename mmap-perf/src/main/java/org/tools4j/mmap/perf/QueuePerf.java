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

import org.agrona.BitUtil;
import org.tools4j.mmap.mapping.api.CpuPinning;
import org.tools4j.mmap.mapping.config.MappingConfigurations;
import org.tools4j.mmap.mapping.config.MappingStrategyConfigurator;
import org.tools4j.mmap.mapping.impl.CpuLists;
import org.tools4j.mmap.perf.PerfOptions.QueueType;
import org.tools4j.mmap.queue.config.QueueConfig;
import org.tools4j.mmap.queue.config.QueueConfigurator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Command line application measuring queue latencies with concurrent senders and receivers, for the mmap queue or for
 * Chronicle Queue using the same harness. See {@link PerfOptions#USAGE} or run with {@code --help}.
 */
@SuppressWarnings("JavaPrintToLogpoint")
public enum QueuePerf {
    ;
    private static final String SLF4J_LOG_LEVEL_PROPERTY = "org.slf4j.simpleLogger.defaultLogLevel";
    private static final String CHRONICLE_QUEUE_CLASS = "net.openhft.chronicle.queue.ChronicleQueue";
    private static final String CHRONICLE_PROPERTIES = "chronicle.properties";
    private static final String CHRONICLE_QUEUE_VERSION_PROPERTY = "chronicleQueueVersion";

    /**
     * Runs the benchmark with the given command line options, see {@link PerfOptions#USAGE}; prints the usage instead
     * if there are none, so that trying the program out does not start a run.
     *
     * @param args the command line options
     * @throws Throwable if a sender or receiver failed, or did not complete in time
     */
    public static void main(final String... args) throws Throwable {
        //unless set on the command line, e.g. when started with java -jar
        if (System.getProperty(SLF4J_LOG_LEVEL_PROPERTY) == null) {
            System.setProperty(SLF4J_LOG_LEVEL_PROPERTY, "warn");
        }
        if (args.length == 0) {
            System.err.println(PerfOptions.USAGE);
            System.exit(2);
            return;
        }
        final PerfOptions options;
        try {
            options = PerfOptions.parse(args);
        } catch (final IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println();
            System.err.println(PerfOptions.USAGE);
            System.exit(2);
            return;
        }
        if (options == null) {
            System.out.println(PerfOptions.USAGE);
            return;
        }
        if (options.queue() == QueueType.CHRONICLE && !chronicleOnClassPath()) {
            System.err.println(chronicleNotOnClassPath());
            System.exit(2);
            return;
        }
        run(options);
    }

    /**
     * Runs the performance test and prints the results to standard out.
     *
     * @param options the options
     * @throws IllegalStateException if Chronicle Queue is requested but not on the class path
     * @throws Throwable if a sender or receiver failed, or did not complete in time
     */
    public static void run(final PerfOptions options) throws Throwable {
        if (options.queue() == QueueType.CHRONICLE && !chronicleOnClassPath()) {
            throw new IllegalStateException(chronicleNotOnClassPath());
        }
        final PinnedCpus cpus = PinnedCpus.assign(options);
        final String mapperCpusProperty = System.getProperty(MappingConfigurations.MAPPING_RUNTIME_CPUS_PROPERTY);
        if (cpus.mapper().length > 0) {
            //read when mapping runtimes are created; the shared runtime is pinned only if not created before
            System.setProperty(MappingConfigurations.MAPPING_RUNTIME_CPUS_PROPERTY, CpuLists.format(cpus.mapper()));
        }
        try {
            run(options, cpus);
        } finally {
            if (mapperCpusProperty == null) {
                System.clearProperty(MappingConfigurations.MAPPING_RUNTIME_CPUS_PROPERTY);
            } else {
                System.setProperty(MappingConfigurations.MAPPING_RUNTIME_CPUS_PROPERTY, mapperCpusProperty);
            }
        }
    }

    private static void run(final PerfOptions options, final PinnedCpus cpus) throws Throwable {
        final Path dir = Files.createTempDirectory(options.dir().toPath(), "mmap-perf-");
        try {
            try (final PerfQueue queue = open(options, dir.toFile())) {
                printSettings(options, cpus, queue, dir);
                final ThreadFactory receiverThreads = threadFactory("receiver", cpus.receivers());
                final ThreadFactory senderThreads = threadFactory("sender", cpus.senders());
                final List<Receiver> receivers = new ArrayList<>();
                final List<Sender> senders = new ArrayList<>();
                for (int i = 0; i < options.receivers(); i++) {
                    receivers.add(new Receiver(i, receiverThreads, queue, options.warmup() * options.senders(),
                            options.messages() * options.senders(), options.length()));
                }
                for (int i = 0; i < options.senders(); i++) {
                    senders.add(new Sender(i, senderThreads, queue, options.rate(), options.messages(),
                            options.length()));
                }
                receivers.forEach(receiver -> receiver.worker().start());
                senders.forEach(sender -> sender.worker().start());
                final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(options.timeout());
                for (final Worker worker : Stream.concat(senders.stream().map(Sender::worker),
                        receivers.stream().map(Receiver::worker)).toList()) {
                    if (!worker.join(deadline)) {
                        throw new IllegalStateException(worker.name() + " did not complete within " +
                                options.timeout() + " seconds");
                    }
                }
                senders.forEach(Sender::printResult);
                receivers.forEach(Receiver::printHistogram);
            }
        } finally {
            if (options.keep()) {
                System.out.println("Queue files kept in: " + dir);
            } else {
                deleteRecursively(dir);
            }
        }
    }

    //chronicle-queue is an optional dependency: only the distribution includes it
    private static boolean chronicleOnClassPath() {
        try {
            Class.forName(CHRONICLE_QUEUE_CLASS, false, QueuePerf.class.getClassLoader());
            return true;
        } catch (final ClassNotFoundException e) {
            return false;
        }
    }

    private static String chronicleNotOnClassPath() {
        return "Chronicle Queue is not on the class path, add net.openhft:chronicle-queue:" + chronicleQueueVersion();
    }

    /**
     * Returns the chronicle-queue version mmap-perf was built with, from {@code chronicleVersion} in build.gradle.
     *
     * @return the chronicle-queue version
     * @throws IllegalStateException if the version resource is missing
     */
    static String chronicleQueueVersion() {
        try (final InputStream in = QueuePerf.class.getResourceAsStream(CHRONICLE_PROPERTIES)) {
            if (in == null) {
                throw new IllegalStateException("Resource " + CHRONICLE_PROPERTIES + " not found");
            }
            final Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty(CHRONICLE_QUEUE_VERSION_PROPERTY);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ThreadFactory threadFactory(final String namePrefix, final int[] cpus) {
        return cpus.length == 0 ? Thread::new : CpuPinning.threadFactory(namePrefix, true, cpus);
    }

    private static PerfQueue open(final PerfOptions options, final File dir) {
        if (options.queue() == QueueType.CHRONICLE) {
            return new ChroniclePerfQueue(dir);
        }
        final QueueConfigurator config = QueueConfig.configure();
        if (options.strategy() != null) {
            config.mappingStrategy(options.strategy());
        }
        if (options.regionSize() != 0) {
            config.headerRegionSize(options.regionSize()).payloadRegionSize(options.regionSize());
        }
        if (options.mapAhead() != 0) {
            config.mappingStrategy(strategy -> mapAhead(strategy, options.mapAhead()));
        }
        if (options.prefill() != null) {
            config.appenderConfig(appender -> appender
                    .prefillHeaderFiles(options.prefill().header())
                    .prefillPayloadFiles(options.prefill().payload()));
        }
        return new MmapPerfQueue(dir, config.toImmutableQueueConfig(), options.reader());
    }

    private static void mapAhead(final MappingStrategyConfigurator strategy, final int regionsToMapAhead) {
        strategy.asyncMapping().ifPresent(current -> strategy.asyncMapping(async -> async
                .regionsToMapAhead(regionsToMapAhead)
                .aheadMappingCacheSize(Math.max(current.aheadMappingCacheSize(),
                        BitUtil.findNextPositivePowerOfTwo(regionsToMapAhead)))
        ));
    }

    private static void printSettings(final PerfOptions options,
                                      final PinnedCpus cpus,
                                      final PerfQueue queue,
                                      final Path dir) {
        final StringBuilder sb = new StringBuilder(1024);
        sb.append("Settings:\n");
        queue.describeSettings(sb);
        sb.append("directory             : ").append(dir).append('\n');
        sb.append("senders               : ").append(options.senders()).append('\n');
        sb.append("receivers             : ").append(options.receivers()).append('\n');
        sb.append("messages per sender   : ").append(options.messages()).append(" (warmup: ")
                .append(options.warmup()).append(")\n");
        sb.append("rate per sender       : ").append(options.rate() == 0 ? "max" : options.rate() + " msg/s")
                .append('\n');
        sb.append("message length        : ").append(options.length()).append(" bytes\n");
        if (options.pinCpus() != null) {
            sb.append("pinned cpus           : senders=").append(cpuList(cpus.senders()))
                    .append(", receivers=").append(cpuList(cpus.receivers()))
                    .append(", mapper=").append(cpuList(cpus.mapper()))
                    .append(CpuPinning.isSupported() ? "" : " (pinning not supported on this platform)")
                    .append('\n');
        }
        sb.append("java                  : ").append(System.getProperty("java.vm.name")).append(' ')
                .append(System.getProperty("java.runtime.version")).append('\n');
        System.out.println(sb.toString().replace("\n", "\n\t").replaceFirst("\t$", ""));
    }

    private static String cpuList(final int[] cpus) {
        return cpus.length == 0 ? "unpinned" : CpuLists.format(cpus);
    }

    private static void deleteRecursively(final Path dir) throws IOException {
        try (final Stream<Path> paths = Files.walk(dir)) {
            for (final Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
