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

import org.tools4j.mmap.perf.PerfOptions.QueueType;
import org.tools4j.mmap.queue.config.QueueConfig;
import org.tools4j.mmap.queue.config.QueueConfigurator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Command line application measuring queue latencies with concurrent senders and receivers, for the mmap queue or for
 * Chronicle Queue using the same harness. See {@link PerfOptions#USAGE} or run with {@code --help}.
 */
@SuppressWarnings("JavaPrintToLogpoint")
public final class QueuePerf {

    private QueuePerf() {
        throw new IllegalStateException("No QueuePerf for you");
    }

    public static void main(final String... args) throws Throwable {
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
        run(options);
    }

    /**
     * Runs the performance test and prints the results to standard out.
     *
     * @param options the options
     * @throws Throwable if a sender or receiver failed, or did not complete in time
     */
    public static void run(final PerfOptions options) throws Throwable {
        final Path dir = Files.createTempDirectory(options.dir().toPath(), "mmap-perf-");
        try {
            try (final PerfQueue queue = open(options, dir.toFile())) {
                printSettings(options, queue, dir);
                final ThreadFactory threadFactory = Thread::new;
                final List<Receiver> receivers = new ArrayList<>();
                final List<Sender> senders = new ArrayList<>();
                for (int i = 0; i < options.receivers(); i++) {
                    receivers.add(new Receiver(i, threadFactory, queue, options.warmup() * options.senders(),
                            options.messages() * options.senders(), options.length()));
                }
                for (int i = 0; i < options.senders(); i++) {
                    senders.add(new Sender(i, threadFactory, queue, options.rate(), options.messages(),
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
                if (options.readBack()) {
                    new ReadBackPerf(((MmapPerfQueue) queue).queue(), options.messages(), options.warmup(),
                            options.length()).run();
                }
            }
        } finally {
            if (options.keep()) {
                System.out.println("Queue files kept in: " + dir);
            } else {
                deleteRecursively(dir);
            }
        }
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
        return new MmapPerfQueue(dir, config.toImmutableQueueConfig());
    }

    private static void printSettings(final PerfOptions options, final PerfQueue queue, final Path dir) {
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
        sb.append("java                  : ").append(System.getProperty("java.vm.name")).append(' ')
                .append(System.getProperty("java.runtime.version")).append('\n');
        System.out.println(sb.toString().replace("\n", "\n\t").replaceFirst("\t$", ""));
    }

    private static void deleteRecursively(final Path dir) throws IOException {
        try (final Stream<Path> paths = Files.walk(dir)) {
            for (final Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
