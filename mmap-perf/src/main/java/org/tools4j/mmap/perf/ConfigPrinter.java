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

import org.tools4j.mmap.queue.config.AppenderConfig;
import org.tools4j.mmap.queue.config.QueueConfig;
import org.tools4j.mmap.queue.config.ReaderConfig;
import org.tools4j.mmap.mapping.config.MappingStrategyConfig;

import java.util.Arrays;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * Describes the queue config settings relevant for appender and poller performance.
 */
public enum ConfigPrinter {
    ;
    private static final String[] NAMES = {"a.hdr", "a.pld", "p.hdr", "p.pld"};

    public static void describeConfig(final QueueConfig config, final StringBuilder sb) {
        sb.append("regionSize            : ").append(regionSize(config)).append('\n');
        sb.append("mappingStrategy       : ").append(mappingStrategy(config)).append('\n');
        sb.append("cacheSize             : ").append(cacheSize(config)).append('\n');
        sb.append("regionsToMapAhead     : ").append(regionsToMapAhead(config)).append('\n');
        sb.append("aheadMappingCacheSize : ").append(aheadMappingCacheSize(config)).append('\n');
        sb.append("maxHeaderFileSize     : ").append(size(config.maxHeaderFileSize())).append(' ')
                .append(fileMapperType(config, true)).append('\n');
        sb.append("maxPayloadFileSize    : ").append(size(config.maxPayloadFileSize())).append(' ')
                .append(fileMapperType(config, false)).append('\n');
    }

    private static String mappingStrategy(final QueueConfig config) {
        final String[] strategies = Arrays.stream(strategies(config.appenderConfig(), config.pollerConfig()))
                .map(ConfigPrinter::strategyName)
                .toArray(String[]::new);
        if (Arrays.stream(strategies).allMatch(s -> s.equals(strategies[0]))) return strategies[0];
        return '[' + names(strategies.length, NAMES) + "] = [" + String.join(" / ", strategies) + ']';
    }

    private static String strategyName(final MappingStrategyConfig config) {
        if (config.asyncMapping().isPresent()) return "async map-ahead";
        return config.asyncUnmapping().isPresent() ? "sync with async unmapping" : "sync";
    }

    private static String fileMapperType(final QueueConfig config, final boolean header) {
        if (header && config.rollHeaderFile() || !header && config.rollPayloadFiles()) {
            if (header && config.expandHeaderFile() || !header && config.expandPayloadFiles()) return "expanding+rolling";
            return "rolling";
        }
        if (header && config.expandHeaderFile() || !header && config.expandPayloadFiles()) return "expanding";
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
        final int header = config.headerRegionSize();
        final int payload = config.payloadRegionSize();
        return header == payload ? size(header) : "[header / payload] = [" + size(header, payload) + ']';
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
        return ints(valueExtractor, strategies(appenderConfig, pollerConfig));
    }

    private static MappingStrategyConfig[] strategies(final AppenderConfig appenderConfig,
                                                      final ReaderConfig pollerConfig) {
        return new MappingStrategyConfig[] {
                appenderConfig.headerMappingStrategy(),
                appenderConfig.payloadMappingStrategy(),
                pollerConfig.headerMappingStrategy(),
                pollerConfig.payloadMappingStrategy()
        };
    }

    private static int[] ints(final ToIntFunction<? super MappingStrategyConfig> valueExtractor,
                              final MappingStrategyConfig... strategies) {
        return Arrays.stream(strategies).mapToInt(valueExtractor).toArray();
    }
}
