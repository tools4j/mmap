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
package org.tools4j.mmap.core.pinning;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Thread affinity through the {@code taskset} command of util-linux, for Linux without native code called from Java.
 * The affinity is read from {@code /proc/thread-self/status}, and set by running {@code taskset -p -c <cpus> <tid>}
 * in a new process, waiting for it to exit; the thread ID is read from the link {@code /proc/thread-self}. Each call
 * allocates, reading files or starting a process: this is acceptable as it happens once per pinned thread, when it
 * is pinned and when its previous affinity is restored, not on a hot path.
 */
final class TasksetAffinityAccess implements AffinityAccess {
    private static final String TASKSET = "taskset";
    private static final Path THREAD_SELF = Paths.get("/proc/thread-self");
    private static final Path THREAD_STATUS = THREAD_SELF.resolve("status");
    private static final String CPUS_ALLOWED_LIST = "Cpus_allowed_list:";

    @Override
    public int[] currentThreadAffinity() {
        try {
            for (final String line : Files.readAllLines(THREAD_STATUS, StandardCharsets.US_ASCII)) {
                if (line.startsWith(CPUS_ALLOWED_LIST)) {
                    return CpuLists.parse(line.substring(CPUS_ALLOWED_LIST.length()));
                }
            }
        } catch (final IOException e) {
            throw new UncheckedIOException("Cannot read the CPU affinity from " + THREAD_STATUS, e);
        }
        throw new IllegalStateException("No " + CPUS_ALLOWED_LIST + " in " + THREAD_STATUS);
    }

    @Override
    public void setCurrentThreadAffinity(final int[] cpus) {
        final String[] command = {TASKSET, "-p", "-c", CpuLists.format(cpus), currentThreadId()};
        try {
            final Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            final String output = new String(process.getInputStream().readAllBytes()).trim();
            final int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException(String.join(" ", command) + " failed with exit code " + exitCode +
                        ": " + output);
            }
        } catch (final IOException e) {
            throw new UncheckedIOException("Cannot run " + String.join(" ", command), e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for " + String.join(" ", command), e);
        }
    }

    private static String currentThreadId() {
        try {
            //a relative link to <pid>/task/<tid>
            return Files.readSymbolicLink(THREAD_SELF).getFileName().toString();
        } catch (final IOException e) {
            throw new UncheckedIOException("Cannot read the thread ID from " + THREAD_SELF, e);
        }
    }
}
