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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for {@link RollingFiles}.
 */
class RollingFilesTest {

    @TempDir
    Path tempDir;

    @Test
    void fileForIndexInsertsIndexBeforeEnding() {
        final File dir = tempDir.toFile();
        assertEquals(new File(dir, "data_0.map"), RollingFiles.fileForIndex(new File(dir, "data.map"), 0));
        assertEquals(new File(dir, "data_12.map"), RollingFiles.fileForIndex(new File(dir, "data.map"), 12));
        assertEquals(new File(dir, "a.b_3.map"), RollingFiles.fileForIndex(new File(dir, "a.b.map"), 3));
        assertEquals(new File(dir, "data_3"), RollingFiles.fileForIndex(new File(dir, "data"), 3));
    }

    @Test
    void listFilesReturnsOnlyRollingFilesOfBaseFile() throws IOException {
        for (final String name : new String[] {"data_0.map", "data_1.map", "data_7.map", "data.map", "data_x.map",
                "data_1.map.bak", "other_1.map", "data_1.mmq", "xdata_1.map"}) {
            Files.createFile(tempDir.resolve(name));
        }
        assertArrayEquals(new String[] {"data_0.map", "data_1.map", "data_7.map"},
                names(RollingFiles.listFiles(tempDir.resolve("data.map").toFile())));
    }

    @Test
    void listFilesIsEmptyIfNoneExist() {
        assertEquals(0, RollingFiles.listFiles(tempDir.resolve("data.map").toFile()).length);
        assertEquals(0, RollingFiles.listFiles(tempDir.resolve("missing/data.map").toFile()).length);
    }

    static String[] names(final File[] files) {
        return Arrays.stream(files).map(File::getName).sorted().toArray(String[]::new);
    }
}
