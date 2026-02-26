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
package org.tools4j.mmap.queue.util;

import org.tools4j.mmap.queue.config.AppenderConfig;
import org.tools4j.mmap.queue.config.QueueConfig;
import org.tools4j.mmap.queue.config.ReaderConfig;
import org.tools4j.mmap.region.config.MappingStrategyConfig;

import java.util.Arrays;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

public class ConfigPrinter {
    private static final String[] NAMES = {"a.hdr", "a.pld", "p.hdr", "p.pld"};

    public static void printConfig(final QueueConfig config) {
        System.out.println("QueueConfig:");
        System.out.println("\tregionSize            : " + regionSize(config));
        System.out.println("\tcacheSize             : " + cacheSize(config));
        System.out.println("\tregionsToMapAhead     : " + regionsToMapAhead(config));
        System.out.println("\taheadMappingCacheSize : " + aheadMappingCacheSize(config));
        System.out.println("\tmaxHeaderFileSize     : " + size(config.maxHeaderFileSize()) + ' ' + fileMapperType(config, true));
        System.out.println("\tmaxPayloadFileSize    : " + size(config.maxPayloadFileSize()) + ' ' + fileMapperType(config, false));
        System.out.println();
    }

    private static String fileMapperType(final QueueConfig config, final boolean header) {
        if (header && config.rollHeaderFile() || !header && config.rollPayloadFiles()) return "rolling";
        if (header && config.expandHeaderFile() || !header && config.expandHeaderFile()) return "expanding";
        return "fixed";
    }

    private static String size(final long size) {
        int shift = size <= 0 ? 0 : Long.numberOfTrailingZeros(size);
        final String ext = "KMGT";
        long value;
        int index;
        for (index = -1, value = size; index < ext.length() && shift >= 10; index++, shift -= 10, value >>= 10);
        return index < 0 ? Long.toString(value) : value + ext.substring(index, index + 1);
    }

    private static String regionSize(final QueueConfig config) {
        return sizes(ints(config.appenderConfig(), config.pollerConfig(), MappingStrategyConfig::regionSize));
    }

    private static String cacheSize(final QueueConfig config) {
        return sizes(ints(config.appenderConfig(), config.pollerConfig(), MappingStrategyConfig::cacheSize));
    }

    private static String regionsToMapAhead(final QueueConfig config) {
        return sizes(ints(config.appenderConfig(), config.pollerConfig(),
                cfg -> cfg.asyncMapping().isPresent() ? cfg.asyncMapping().get().regionsToMapAhead() : 0
        ));
    }

    private static String aheadMappingCacheSize(final QueueConfig config) {
        return sizes(ints(config.appenderConfig(), config.pollerConfig(),
                cfg -> cfg.asyncMapping().isPresent() ? cfg.asyncMapping().get().aheadMappingCacheSize() : 0
        ));
    }

    private static String sizes(final int[] sizes) {
        if (sizes.length == 0) return "n/a";
        if (sizes.length == 1) return size(sizes[0]);
        if (Arrays.stream(sizes).allMatch(s -> s == sizes[0])) return size(sizes[0]);
        return '[' + names(sizes.length, NAMES) + "] = [" + size(sizes) + ']';
    }

    private static String names(final int count, final String... names) {
        return Arrays.stream(names, 0, count).collect(Collectors.joining(" / "));
    }

    private static String size(final int... size) {
        return Arrays.stream(size).mapToObj(ConfigPrinter::size).collect(Collectors.joining(" / "));
    }

    private static int[] ints(final AppenderConfig appenderConfig,
                              final ReaderConfig pollerConfig,
                              final ToIntFunction<? super MappingStrategyConfig> valueExtractor) {
        return ints(valueExtractor,
                appenderConfig.headerMappingStrategy(),
                appenderConfig.payloadMappingStrategy(),
                pollerConfig.headerMappingStrategy(),
                pollerConfig.payloadMappingStrategy());
    }

    private static int[] ints(final ToIntFunction<? super MappingStrategyConfig> valueExtractor,
                              final MappingStrategyConfig... strategies) {
        return Arrays.stream(strategies).mapToInt(valueExtractor).toArray();
    }
}
