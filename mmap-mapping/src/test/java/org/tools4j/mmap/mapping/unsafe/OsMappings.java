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
package org.tools4j.mmap.mapping.unsafe;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Test utility asking the OS whether a file is currently memory-mapped by this process, via {@code /proc/self/maps} on
 * Linux and {@code lsof} on macOS. The mapping outlives the file channel it was created from, so this observes
 * whether munmap actually happened. Aborts the calling test as skipped on other platforms.
 */
final class OsMappings {

    private static final Path PROC_SELF_MAPS = Paths.get("/proc/self/maps");
    private static final boolean MAC_OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");

    private OsMappings() {
        throw new IllegalStateException("No OsMappings for you");
    }

    /** Call while the file exists: resolves the real path, as the OS reports it (e.g. /private/var vs /var on macOS) */
    static Path realPath(final File file) {
        try {
            return file.toPath().toRealPath();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static boolean isMapped(final Path realPath) {
        final String path = realPath.toString();
        if (Files.isReadable(PROC_SELF_MAPS)) {
            return readLines(PROC_SELF_MAPS).stream().anyMatch(line -> line.endsWith(" " + path));
        }
        assumeTrue(MAC_OS, "Mapped files can only be inspected on Linux and macOS");
        //lsof -Ffn prints an f (descriptor) line before each n (name) line; mappings have descriptor txt or mem,
        //everything else, such as the open file channel, has a numeric descriptor
        String descriptor = "";
        for (final String line : lsofFields()) {
            if (line.startsWith("f")) {
                descriptor = line.substring(1);
            } else if (line.equals("n" + path) && (descriptor.equals("txt") || descriptor.equals("mem"))) {
                return true;
            }
        }
        return false;
    }

    private static List<String> readLines(final Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> lsofFields() {
        try {
            final Process lsof = new ProcessBuilder("lsof", "-p", String.valueOf(ProcessHandle.current().pid()), "-Ffn")
                    .redirectErrorStream(true)
                    .start();
            final String output = new String(lsof.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            lsof.waitFor();
            return List.of(output.split("\n"));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
