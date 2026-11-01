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
package org.tools4j.mmap.mapping.impl;

import org.tools4j.mmap.mapping.api.CpuPinning;

import java.util.Arrays;
import java.util.BitSet;

/**
 * Parses, formats and validates CPU lists in Linux cpulist format, for instance {@code 0-3,8,10-11}.
 */
public enum CpuLists {
    ;

    /**
     * Parses a CPU list, keeping the order of its CPUs. A range without end, for instance {@code 4-}, ends at the
     * {@linkplain SystemCpus#maxOnlineCpu() highest online CPU}.
     *
     * @param cpuList the CPU list, for instance {@code 0-3,8,10-11}, {@code 0,2,4,6} or {@code 4-}
     * @return the CPUs in list order
     * @throws IllegalArgumentException if the CPU list is invalid, or contains a CPU twice
     */
    public static int[] parse(final String cpuList) {
        final String[] elements = cpuList.split(",", -1);
        int[] cpus = new int[elements.length];
        int count = 0;
        try {
            for (final String element : elements) {
                final int dash = element.indexOf('-');
                final int first = Integer.parseInt((dash < 0 ? element : element.substring(0, dash)).trim());
                final String lastValue = dash < 0 ? null : element.substring(dash + 1).trim();
                final int last = lastValue == null ? first :
                        lastValue.isEmpty() ? SystemCpus.maxOnlineCpu() : Integer.parseInt(lastValue);
                if (first < 0 || first > last || last > CpuPinning.MAX_CPU) {
                    throw new IllegalArgumentException("Invalid CPU range " + element.trim() + " in CPU list " +
                            cpuList);
                }
                if (count + last - first + 1 > cpus.length) {
                    cpus = Arrays.copyOf(cpus, count + last - first + 1);
                }
                for (int cpu = first; cpu <= last; cpu++) {
                    cpus[count++] = cpu;
                }
            }
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException("Invalid CPU list " + cpuList, e);
        }
        return validate(Arrays.copyOf(cpus, count));
    }

    /**
     * Validates the CPUs of a CPU list.
     *
     * @param cpus the CPUs
     * @return the CPUs
     * @throws IllegalArgumentException if empty, or if a CPU is invalid or contained twice
     */
    public static int[] validate(final int[] cpus) {
        if (cpus.length == 0) {
            throw new IllegalArgumentException("CPU list must not be empty");
        }
        final BitSet seen = new BitSet();
        for (final int cpu : cpus) {
            if (cpu < 0 || cpu > CpuPinning.MAX_CPU) {
                throw new IllegalArgumentException("Invalid CPU " + cpu + ", must be in [0, " + CpuPinning.MAX_CPU +
                        "]");
            }
            if (seen.get(cpu)) {
                throw new IllegalArgumentException("CPU " + cpu + " is contained twice in CPU list " +
                        format(cpus));
            }
            seen.set(cpu);
        }
        return cpus;
    }

    /**
     * Formats CPUs as CPU list in their order, joining ascending runs to ranges.
     *
     * @param cpus the CPUs
     * @return the CPU list, for instance {@code 0-3,8,10-11}
     */
    public static String format(final int[] cpus) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cpus.length; ) {
            int end = i;
            while (end + 1 < cpus.length && cpus[end + 1] == cpus[end] + 1) {
                end++;
            }
            if (!sb.isEmpty()) {
                sb.append(',');
            }
            sb.append(cpus[i]);
            if (end > i) {
                sb.append('-').append(cpus[end]);
            }
            i = end + 1;
        }
        return sb.toString();
    }

    /**
     * @param first the first CPU
     * @param last  the last CPU, inclusive
     * @return the CPUs of the range in ascending order
     * @throws IllegalArgumentException if a CPU is invalid, or first is larger than last
     */
    public static int[] range(final int first, final int last) {
        if (first < 0 || last > CpuPinning.MAX_CPU || first > last) {
            throw new IllegalArgumentException("Invalid CPU range " + first + "-" + last + ", must be within [0, " +
                    CpuPinning.MAX_CPU + "] and first must not be larger than last");
        }
        final int[] cpus = new int[last - first + 1];
        Arrays.setAll(cpus, i -> first + i);
        return cpus;
    }
}
