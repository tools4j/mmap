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
 * @param readBack    true to measure reading back the mmap queue with entry reader, iterator and index reader
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
                          boolean readBack) {

    /** The queue implementations */
    public enum QueueType {
        MMAP,
        CHRONICLE
    }

    public static final String USAGE = """
            Usage: mmap-perf [options]
            Measures the latency from sending to receiving messages through a queue, after a warmup phase. Numbers can
            have '_' separators and a k, m or g suffix, decimal for counts (1m = 1_000_000) and binary for sizes
            (64k = 65536).

              --queue <mmap|chronicle>   queue implementation (default: mmap), chronicle requires Java 17
              --messages <n>             messages per sender, including warmup (default: 11m)
              --warmup <n>               warmup messages per sender, not measured (default: 1m)
              --rate <n>                 messages per second per sender, 0 for max speed (default: 1m)
              --length <n>               message length in bytes, at least %d (default: 100)
              --senders <n>              sender threads, at most 256 (default: 1)
              --receivers <n>            receiver threads, each receiving all messages (default: 2)
              --dir <path>               parent of the temporary queue directory, e.g. /dev/shm (default: java.io.tmpdir)
              --keep                     keep the queue files after the run
              --timeout <seconds>        max time to wait for senders and receivers (default: 300)
              --help                     print this help

            mmap only (other settings via -Dmmap.queue.* and -Dmmap.mapping.* system properties):
              --strategy <name>          mapping strategy for all accessors: %s
                                         (default: system default per accessor)
              --region-size <n>          header and payload region size, power of two (default: system default)
              --read-back                also measure reading back by index, iterating and checking indices
            """.formatted(MessageCodec.MIN_MESSAGE_LENGTH, Arrays.stream(MappingStrategy.values())
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
        boolean readBack = false;
        for (int i = 0; i < args.length; i++) {
            final String option = args[i];
            switch (option) {
                case "--help", "-h" -> {
                    return null;
                }
                case "--keep" -> keep = true;
                case "--read-back" -> readBack = true;
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
                        default -> throw new IllegalArgumentException("Unknown option: " + option);
                    }
                }
            }
        }
        return new PerfOptions(queue, messages, warmup, rate, length, senders, receivers, dir, keep, timeout,
                strategy, regionSize, readBack).validate();
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
        if (queue != QueueType.MMAP) {
            check(strategy == null && regionSize == 0 && !readBack,
                    "--strategy, --region-size and --read-back are only supported for --queue mmap");
        }
        check(!readBack || senders == 1, "--read-back requires --senders 1");
        return this;
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
