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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The online CPUs, read from Linux sysfs.
 */
public enum SystemCpus {
    ;
    private static final Path ONLINE = Paths.get("/sys/devices/system/cpu/online");

    /** @return the highest online CPU, or the number of available processors minus one if unknown */
    public static int maxOnlineCpu() {
        try {
            if (Files.isReadable(ONLINE)) {
                return lastCpu(Files.readString(ONLINE, StandardCharsets.US_ASCII));
            }
        } catch (final IOException | RuntimeException e) {
            //fall through
        }
        return Runtime.getRuntime().availableProcessors() - 1;
    }

    /**
     * @param cpuList a CPU list in Linux cpulist format, e.g. {@code 0-3,8,10-11}
     * @return the last CPU of the list
     * @throws NumberFormatException if the last CPU is not a number
     */
    public static int lastCpu(final String cpuList) {
        final String trimmed = cpuList.trim();
        final int separator = Math.max(trimmed.lastIndexOf(','), trimmed.lastIndexOf('-'));
        return Integer.parseInt(trimmed.substring(separator + 1).trim());
    }
}
