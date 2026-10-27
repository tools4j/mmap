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

import org.tools4j.mmap.queue.config.QueueConfig;
import org.tools4j.mmap.queue.config.QueueLayoutConfig;

/**
 * Contains static helper methods related to {@link QueueLayoutConfig}.
 */
enum QueueLayoutConfigs {
    ;

    /**
     * Returns the given configuration with all layout values replaced by the values from the given layout.
     *
     * @param config the configuration used to open the queue
     * @param layout the layout of the queue read from the layout meta file
     * @return the configuration in effect for the queue
     */
    static QueueConfig applyLayout(final QueueConfig config, final QueueLayoutConfig layout) {
        return new QueueConfigImpl(
                layout.maxAppenders(),
                layout.minHeaderFileSize(),
                layout.maxHeaderFileSize(),
                layout.minPayloadFileSize(),
                layout.maxPayloadFileSize(),
                layout.headerRegionSize(),
                layout.payloadRegionSize(),
                layout.expandHeaderFile(),
                layout.expandPayloadFiles(),
                layout.rollHeaderFile(),
                layout.rollPayloadFiles(),
                config.appenderConfig(),
                config.pollerConfig(),
                config.entryReaderConfig(),
                config.entryIteratorConfig(),
                config.indexReaderConfig());
    }

    /**
     * Returns the values of layout that differ from the given configuration, or null if all values are the
     * same.
     *
     * @param layout the current layout config
     * @param config the configuration to compare with
     * @return the differing values, for instance {@code "maxAppenders=64 (configured 256)"}, or null if none differ
     */
    static CharSequence diff(final QueueLayoutConfig layout, final QueueLayoutConfig config) {
        StringBuilder diffs = null;
        diffs = appendDiff(diffs, "maxAppenders", layout.maxAppenders(), config.maxAppenders());
        diffs = appendDiff(diffs, "headerRegionSize", layout.headerRegionSize(), config.headerRegionSize());
        diffs = appendDiff(diffs, "payloadRegionSize", layout.payloadRegionSize(), config.payloadRegionSize());
        diffs = appendDiff(diffs, "minHeaderFileSize", layout.minHeaderFileSize(), config.minHeaderFileSize());
        diffs = appendDiff(diffs, "maxHeaderFileSize", layout.maxHeaderFileSize(), config.maxHeaderFileSize());
        diffs = appendDiff(diffs, "minPayloadFileSize", layout.minPayloadFileSize(), config.minPayloadFileSize());
        diffs = appendDiff(diffs, "maxPayloadFileSize", layout.maxPayloadFileSize(), config.maxPayloadFileSize());
        diffs = appendDiff(diffs, "expandHeaderFile", layout.expandHeaderFile(), config.expandHeaderFile());
        diffs = appendDiff(diffs, "expandPayloadFiles", layout.expandPayloadFiles(), config.expandPayloadFiles());
        diffs = appendDiff(diffs, "rollHeaderFile", layout.rollHeaderFile(), config.rollHeaderFile());
        diffs = appendDiff(diffs, "rollPayloadFiles", layout.rollPayloadFiles(), config.rollPayloadFiles());
        return diffs;
    }

    private static StringBuilder appendDiff(final StringBuilder differences,
                                            final String name,
                                            final long layoutValue,
                                            final long configValue) {
        if (layoutValue == configValue) {
            return differences;
        }
        return appendSeparator(differences)
                .append(name).append('=').append(layoutValue)
                .append(" (configured ").append(configValue).append(')');
    }

    private static StringBuilder appendDiff(final StringBuilder differences,
                                            final String name,
                                            final boolean layoutValue,
                                            final boolean configValue) {
        if (layoutValue == configValue) {
            return differences;
        }
        return appendSeparator(differences)
                .append(name).append('=').append(layoutValue)
                .append(" (configured ").append(configValue).append(')');
    }

    private static StringBuilder appendSeparator(final StringBuilder differences) {
        return differences == null ? new StringBuilder(128) : differences.append(", ");
    }
}
