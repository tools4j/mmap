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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.io.TempDir;
import org.tools4j.mmap.mapping.api.AccessMode;
import org.tools4j.mmap.mapping.api.Describable;
import org.tools4j.mmap.mapping.api.ErrorListener;
import org.tools4j.mmap.mapping.api.FileInitialiser;
import org.tools4j.mmap.mapping.api.PreAccessHook;
import org.tools4j.mmap.mapping.api.LifecycleListener;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.condition.OS.WINDOWS;
import static org.tools4j.mmap.mapping.impl.Constants.REGION_SIZE_GRANULARITY;

/**
 * Unit test for {@link FileChannelProvider} with the standard and custom {@link FileInitialiser file initialisers}.
 */
class FileChannelProviderTest {

    private static final int SIZE = 4 * (int) REGION_SIZE_GRANULARITY;
    private static final Describable OWNER = sb -> sb.append("FileChannelProviderTest");
    private static final byte[] EXISTING_CONTENT = {1, 2, 3, 4, 5, 6, 7, 8};

    @TempDir
    Path tempDir;

    private Path path;

    private FileChannelProvider provider(final AccessMode accessMode, final FileInitialiser initialiser, final long minLength) {
        path = tempDir.resolve("provider.map");
        return new FileChannelProvider(OWNER, accessMode, path.toFile(), initialiser, minLength, LifecycleListener.NO_OP,
                ErrorListener.NO_OP);
    }

    private void writeExistingContent() throws IOException {
        Files.write(tempDir.resolve("provider.map"), EXISTING_CONTENT);
    }

    @Test
    void lazyExtendsFileWhenOpenedAndExtended() throws IOException {
        try (final FileChannelProvider provider = provider(AccessMode.READ_WRITE, FileInitialiser.LAZY, SIZE)) {
            assertEquals(SIZE, provider.get().size());
            assertEquals(2 * SIZE, provider.extend(2 * SIZE));
            assertEquals(2 * SIZE, provider.get().size());
        }
        assertFilled(Files.readAllBytes(path), 0, (byte) 0);
    }

    @Test
    @DisabledOnOs(value = WINDOWS, disabledReason = "allocated size measured with du")
    void lazyLeavesNewFileSpaceUnallocated() throws Exception {
        try (final FileChannelProvider provider = provider(AccessMode.READ_WRITE, FileInitialiser.LAZY, SIZE)) {
            provider.extend(2 * SIZE);
        }
        assertTrue(allocatedBytes(path) < SIZE, "allocated bytes: " + allocatedBytes(path));
    }

    @Test
    @DisabledOnOs(value = WINDOWS, disabledReason = "allocated size measured with du")
    void zeroAllocatesNewFileSpace() throws Exception {
        try (final FileChannelProvider provider = provider(AccessMode.READ_WRITE, FileInitialiser.ZERO, SIZE)) {
            provider.extend(2 * SIZE);
        }
        assertEquals(2 * SIZE, Files.size(path));
        assertTrue(allocatedBytes(path) >= 2 * SIZE, "allocated bytes: " + allocatedBytes(path));
        assertFilled(Files.readAllBytes(path), 0, (byte) 0);
    }

    @Test
    void minusOneFillsNewFileSpaceKeepingExistingContent() throws IOException {
        writeExistingContent();
        try (final FileChannelProvider provider = provider(AccessMode.READ_WRITE, FileInitialiser.MINUS_ONE, SIZE)) {
            assertEquals(SIZE, provider.get().size());
            provider.extend(2 * SIZE);
        }
        final byte[] content = Files.readAllBytes(path);
        assertEquals(2 * SIZE, content.length);
        assertEquals(Arrays.toString(EXISTING_CONTENT),
                Arrays.toString(Arrays.copyOf(content, EXISTING_CONTENT.length)));
        assertFilled(content, EXISTING_CONTENT.length, (byte) -1);
    }

    @Test
    void extendNeverShrinksFileAndReturnsLongerLength() throws IOException {
        try (final FileChannelProvider provider = provider(AccessMode.READ_WRITE, FileInitialiser.ZERO, 2 * SIZE)) {
            assertEquals(2 * SIZE, provider.extend(SIZE));
            assertEquals(2 * SIZE, provider.get().size());
        }
    }

    @Test
    void readOnlyFileShorterThanMinLengthIsRejectedAndNotModified() throws IOException {
        writeExistingContent();
        try (final FileChannelProvider provider = provider(AccessMode.READ_ONLY, FileInitialiser.ZERO, SIZE)) {
            assertThrows(IllegalArgumentException.class, provider::get);
        }
        assertEquals(EXISTING_CONTENT.length, Files.size(path));
    }

    @Test
    void hookForAccessModeIsInvokedWhenOpenedAndExtendedWhileFileIsShort() throws IOException {
        final List<String> calls = new ArrayList<>();
        final FileInitialiser initialiser = accessMode -> {
            calls.add("hook:" + accessMode);
            return new PreAccessHook() {
                @Override
                public void onOpen(final File file, final FileChannel channel, final long size) {
                    calls.add("open:" + size);
                }

                @Override
                public void onExtend(final File file, final FileChannel channel, final long size) {
                    calls.add("extend:" + size);
                }
            };
        };
        try (final FileChannelProvider provider = provider(AccessMode.READ_WRITE, initialiser, SIZE)) {
            provider.get();
            provider.extend(2 * SIZE);
            provider.extend(SIZE);
            assertEquals(2 * SIZE, provider.get().size());
        }
        assertEquals(List.of("hook:READ_WRITE", "open:" + SIZE, "extend:" + 2 * SIZE), calls);
    }

    private static void assertFilled(final byte[] content, final int from, final byte value) {
        for (int i = from; i < content.length; i++) {
            if (content[i] != value) {
                assertEquals(value, content[i], "byte at position " + i);
            }
        }
    }

    private static long allocatedBytes(final Path path) throws Exception {
        final Process process = new ProcessBuilder("du", "-k", path.toString()).start();
        final String output = new String(process.getInputStream().readAllBytes()).trim();
        assertEquals(0, process.waitFor(), "du exit code");
        return 1024L * Long.parseLong(output.split("\\s+")[0]);
    }
}
