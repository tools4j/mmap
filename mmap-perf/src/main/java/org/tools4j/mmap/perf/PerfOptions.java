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

import org.tools4j.mmap.mapping.impl.CpuLists;
import org.tools4j.mmap.queue.config.MappingStrategy;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Command line options of {@link QueuePerf}.
 *
 * @param queue       the queue implementation to measure
 * @param messages    the number of messages sent by each sender, including warmup messages
 * @param warmup      the number of messages sent by each sender before latencies are recorded
 * @param rate        the messages per second sent by each sender, or zero to send as fast as possible
 * @param length      the message length in bytes
 * @param senders     the number of sender threads
 * @param receivers   the number of receiver threads, each receiving all messages
 * @param dir         the parent directory of the temporary queue directory
 * @param keep        true to keep the queue files after the run
 * @param timeout     the max seconds to wait for senders and receivers to complete
 * @param strategy    the mmap mapping strategy, or null for the system default
 * @param regionSize  the mmap region size, or zero for the system default
 * @param mapAhead    the mmap regions to map ahead by accessors with async mapping, or zero for the system default
 * @param reader      the mmap reader used by receivers
 * @param prefill     the mmap prefill mode of new header and payload file space, or null for the system default
 * @param pinCpus     the CPUs to pin threads to, assigned in order to senders, receivers and then mmap mapping
 *                    runtime threads, or null to not pin threads
 */
public record PerfOptions(QueueType queue,
                          long messages,
                          long warmup,
                          long rate,
                          int length,
                          int senders,
                          int receivers,
                          File dir,
                          boolean keep,
                          long timeout,
                          MappingStrategy strategy,
                          int regionSize,
                          int mapAhead,
                          ReaderType reader,
                          PrefillMode prefill,
                          int[] pinCpus) {

    /** The queue implementations */
    public enum QueueType {
        /** The mmap queue */
        MMAP,
        /** Chronicle Queue */
        CHRONICLE
    }

    /** The mmap readers used by receivers */
    public enum ReaderType {
        /** Reading with a {@link org.tools4j.mmap.queue.api.Poller Poller} */
        POLLER,
        /** Reading with a continuous {@link org.tools4j.mmap.queue.api.EntryIterator EntryIterator} */
        ITERATOR
    }

    /** The mmap prefill modes, prefilling new space of header and/or payload files written by senders */
    public enum PrefillMode {
        /** Neither header nor payload files are prefilled, new file space is sparse */
        NONE(false, false),
        /** Header files are prefilled */
        HEADER(true, false),
        /** Payload files are prefilled */
        PAYLOAD(false, true),
        /** Header and payload files are prefilled */
        BOTH(true, true);

        private final boolean header;
        private final boolean payload;

        PrefillMode(final boolean header, final boolean payload) {
            this.header = header;
            this.payload = payload;
        }

        /** @return true if new header file space is prefilled */
        public boolean header() {
            return header;
        }

        /** @return true if new payload file space is prefilled */
        public boolean payload() {
            return payload;
        }
    }

    /** CPU list used by {@code --pin} without CPUs: from CPU 2 up to the highest online CPU */
    public static final String DEFAULT_PIN_CPUS = "2-";

    /** Usage printed with {@code --help} and without options */
    public static final String USAGE = """
            Usage: mmap-perf <options>
            Measures the latency from sending to receiving messages through a queue, after a warmup phase. Without
            options, prints this help; to run with all defaults, pass e.g. --queue mmap. Numbers can have '_'
            separators and a k, m or g suffix, decimal for counts (1m = 1_000_000) and binary for sizes (64k = 65536).

              --queue <mmap|chronicle>   queue implementation (default: mmap), chronicle requires Java 17 and
                                         chronicle-queue on the class path
              --messages <n>             messages per sender, including warmup (default: 11m)
              --warmup <n>               warmup messages per sender, not measured (default: 1m)
              --rate <n>                 messages per second per sender, 0 for max speed (default: 1m)
              --length <n>               message length in bytes, at least %d (default: 100)
              --senders <n>              sender threads, at most 256 (default: 1)
              --receivers <n>            receiver threads, each receiving all messages (default: 2)
              --dir <path>               parent of the temporary queue directory, e.g. /dev/shm (default: java.io.tmpdir)
              --keep                     keep the queue files after the run
              --timeout <seconds>        max time to wait for senders and receivers (default: 300)
              --pin [<cpus>]             pin threads to CPUs, one CPU per thread, assigned in order to senders,
                                         receivers and then mmap mapping runtime threads; cpus is a CPU list such
                                         as 2-7 or 2,4,6,8 (default: %s, unpinned without --pin)
              --help                     print this help

            mmap only (other settings via -Dmmap.queue.* and -Dmmap.mapping.* system properties):
              --strategy <name>          mapping strategy for all accessors: %s
                                         (default: system default per accessor)
              --region-size <n>          header and payload region size, power of two (default: system default)
              --map-ahead <n>            regions to map ahead by accessors with async mapping, the ahead-mapping
                                         cache size is raised to fit (default: system default)
              --reader <poller|iterator> receivers read with a poller or a continuous entry iterator
                                         (default: poller)
              --prefill <mode>           prefill new space of header and/or payload files: none, header, payload,
                                         both (default: system default, none unless set by system property)
            """.formatted(MessageCodec.MIN_MESSAGE_LENGTH, DEFAULT_PIN_CPUS, Arrays.stream(MappingStrategy.values())
                    .map(Enum::name).collect(Collectors.joining(", ")));

    /**
     * Parses the command line arguments.
     *
     * @param args the arguments
     * @return the options, or null if help was requested
     * @throws IllegalArgumentException if an argument is invalid
     */
    public static PerfOptions parse(final String... args) {
        QueueType queue = QueueType.MMAP;
        long messages = 11_000_000;
        long warmup = 1_000_000;
        long rate = 1_000_000;
        int length = 100;
        int senders = 1;
        int receivers = 2;
        File dir = new File(System.getProperty("java.io.tmpdir"));
        boolean keep = false;
        long timeout = 300;
        MappingStrategy strategy = null;
        int regionSize = 0;
        int mapAhead = 0;
        ReaderType reader = ReaderType.POLLER;
        PrefillMode prefill = null;
        int[] pinCpus = null;
        for (int i = 0; i < args.length; i++) {
            final String option = args[i];
            switch (option) {
                case "--help", "-h" -> {
                    return null;
                }
                case "--keep" -> keep = true;
                case "--pin" -> {
                    //the CPU list is optional
                    final boolean hasCpus = i + 1 < args.length && !args[i + 1].startsWith("--");
                    pinCpus = cpus(option, hasCpus ? args[++i] : DEFAULT_PIN_CPUS);
                }
                default -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("Missing value for option: " + option);
                    }
                    final String value = args[++i];
                    switch (option) {
                        case "--queue" -> queue = enumValue(QueueType.class, option, value);
                        case "--messages" -> messages = number(option, value);
                        case "--warmup" -> warmup = number(option, value);
                        case "--rate" -> rate = number(option, value);
                        case "--length" -> length = intNumber(option, value);
                        case "--senders" -> senders = intNumber(option, value);
                        case "--receivers" -> receivers = intNumber(option, value);
                        case "--dir" -> dir = new File(value);
                        case "--timeout" -> timeout = number(option, value);
                        case "--strategy" -> strategy = enumValue(MappingStrategy.class, option, value);
                        case "--region-size" -> regionSize = intSize(option, value);
                        case "--map-ahead" -> mapAhead = intNumber(option, value);
                        case "--reader" -> reader = enumValue(ReaderType.class, option, value);
                        case "--prefill" -> prefill = enumValue(PrefillMode.class, option, value);
                        default -> throw new IllegalArgumentException("Unknown option: " + option);
                    }
                }
            }
        }
        return new PerfOptions(queue, messages, warmup, rate, length, senders, receivers, dir, keep, timeout,
                strategy, regionSize, mapAhead, reader, prefill, pinCpus).validate();
    }

    private PerfOptions validate() {
        check(messages > 0, "--messages must be positive");
        check(warmup >= 0 && warmup < messages, "--warmup must be in [0, messages)");
        check(rate >= 0, "--rate must not be negative");
        check(length >= MessageCodec.MIN_MESSAGE_LENGTH, "--length must be at least " +
                MessageCodec.MIN_MESSAGE_LENGTH);
        check(senders >= 1 && senders <= 256, "--senders must be in [1, 256]");
        check(receivers >= 1, "--receivers must be positive");
        check(timeout > 0, "--timeout must be positive");
        check(dir.isDirectory(), "--dir must be an existing directory: " + dir);
        check(mapAhead >= 0, "--map-ahead must not be negative");
        if (queue != QueueType.MMAP) {
            check(strategy == null && regionSize == 0 && mapAhead == 0 && reader == ReaderType.POLLER
                            && prefill == null,
                    "--strategy, --region-size, --map-ahead, --reader and --prefill are only supported for --queue " +
                            "mmap");
        }
        check(mapAhead == 0 || strategy == null || strategy.mappingStrategyConfig().asyncMapping().isPresent(),
                "--map-ahead requires a strategy with async mapping, e.g. " + MappingStrategy.AsyncMapAheadStrategy);
        return this;
    }

    private static int[] cpus(final String option, final String value) {
        try {
            return CpuLists.parse(value);
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid CPU list for " + option + ": " + value + " (" +
                    e.getMessage() + ")");
        }
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static <E extends Enum<E>> E enumValue(final Class<E> type, final String option, final String value) {
        for (final E constant : type.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(value)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("Invalid " + option + " " + value + ", must be one of: " +
                Arrays.toString(type.getEnumConstants()));
    }

    private static int intNumber(final String option, final String value) {
        return toInt(option, value, number(option, value));
    }

    private static int intSize(final String option, final String value) {
        return toInt(option, value, number(option, value, 1024));
    }

    private static int toInt(final String option, final String value, final long number) {
        check(number <= Integer.MAX_VALUE, option + " exceeds max int value: " + value);
        return (int) number;
    }

    private static long number(final String option, final String value) {
        return number(option, value, 1000);
    }

    private static long number(final String option, final String value, final int unit) {
        final String digits = value.replace("_", "").toLowerCase(Locale.ROOT);
        final int last = digits.length() - 1;
        final int shift = last < 0 ? 0 : switch (digits.charAt(last)) {
            case 'k' -> 1;
            case 'm' -> 2;
            case 'g' -> 3;
            default -> 0;
        };
        try {
            long number = Long.parseLong(shift == 0 ? digits : digits.substring(0, last));
            for (int i = 0; i < shift; i++) {
                number = Math.multiplyExact(number, unit);
            }
            return number;
        } catch (final NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("Invalid number for " + option + ": " + value);
        }
    }
}
