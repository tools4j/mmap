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

import org.HdrHistogram.Histogram;

/**
 * Prints histogram percentiles in microseconds for values recorded in nanoseconds.
 */
public enum HistogramPrinter {
    ;
    private static final double[] PERCENTILES = {50, 90, 99, 99.9, 99.99, 99.999};

    public static void printHistogram(final String name, final Histogram histogram) {
        final StringBuilder sb = new StringBuilder(256);
        sb.append(name).append(": Percentiles (micros)\n");
        line(sb, "min", histogram.getMinValue());
        for (final double percentile : PERCENTILES) {
            line(sb, percentile + "%", histogram.getValueAtPercentile(percentile));
        }
        line(sb, "max", histogram.getMaxValue());
        sb.append("\tcount  : ").append(histogram.getTotalCount()).append('\n');
        System.out.println(sb);
    }

    private static void line(final StringBuilder sb, final String label, final long nanos) {
        sb.append('\t').append(String.format("%-7s", label.replace(".0%", "%"))).append(": ").append(nanos / 1000f)
                .append('\n');
    }
}
