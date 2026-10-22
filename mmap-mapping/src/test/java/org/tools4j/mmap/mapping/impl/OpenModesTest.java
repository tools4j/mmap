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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tools4j.mmap.mapping.api.AccessMode;
import org.tools4j.mmap.mapping.api.OpenMode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tools4j.mmap.mapping.impl.RollingFilesTest.names;

/**
 * Unit test for {@link OpenModes}.
 */
class OpenModesTest {

    @TempDir
    Path tempDir;

    @Test
    void accessMode() {
        for (final OpenMode openMode : OpenMode.values()) {
            final boolean readOnly = openMode == OpenMode.READ_ONLY || openMode == OpenMode.READ_ONLY_LAZY;
            final AccessMode expected = readOnly ? AccessMode.READ_ONLY : AccessMode.READ_WRITE;
            assertEquals(expected, openMode.accessMode(), openMode.toString());
        }
    }

    @ParameterizedTest(name = "rollFiles={0}")
    @ValueSource(booleans = {false, true})
    void readOnlyLazyAndOpenOrCreateAcceptMissingFileWithoutCreatingIt(final boolean rollFiles) {
        final File file = file("data.map");
        OpenModes.apply(file, OpenMode.READ_ONLY_LAZY, rollFiles);
        OpenModes.apply(file, OpenMode.OPEN_OR_CREATE, rollFiles);
        assertEquals(0, tempDir.toFile().list().length);
    }

    @ParameterizedTest(name = "rollFiles={0}")
    @ValueSource(booleans = {false, true})
    void readOnlyAndReadWriteRequireExistingFile(final boolean rollFiles) throws IOException {
        final File file = file("data.map");
        for (final OpenMode openMode : new OpenMode[] {OpenMode.READ_ONLY, OpenMode.READ_WRITE}) {
            assertThrows(IllegalArgumentException.class, () -> OpenModes.apply(file, openMode, rollFiles));
        }
        createFiles(rollFiles ? "data_3.map" : "data.map");
        for (final OpenMode openMode : new OpenMode[] {OpenMode.READ_ONLY, OpenMode.READ_WRITE}) {
            assertDoesNotThrow(() -> OpenModes.apply(file, openMode, rollFiles));
        }
    }

    @Test
    void readWriteOfRollingFilesIgnoresBaseFile() throws IOException {
        createFiles("data.map");
        assertThrows(IllegalArgumentException.class,
                () -> OpenModes.apply(file("data.map"), OpenMode.READ_WRITE, true));
    }

    @ParameterizedTest(name = "rollFiles={0}")
    @ValueSource(booleans = {false, true})
    void createCreatesFileAndFailsIfItExists(final boolean rollFiles) {
        final File file = file("data.map");
        OpenModes.apply(file, OpenMode.CREATE, rollFiles);
        assertTrue(file(rollFiles ? "data_0.map" : "data.map").exists());
        assertThrows(IllegalArgumentException.class, () -> OpenModes.apply(file, OpenMode.CREATE, rollFiles));
    }

    @Test
    void createOfRollingFilesFailsIfAnyRollingFileExists() throws IOException {
        createFiles("data_2.map");
        assertThrows(IllegalArgumentException.class, () -> OpenModes.apply(file("data.map"), OpenMode.CREATE, true));
        assertFalse(file("data_0.map").exists());
    }

    @Test
    void recreateDeletesFile() throws IOException {
        createFiles("data.map", "data_0.map", "other.map");
        OpenModes.apply(file("data.map"), OpenMode.RECREATE, false);
        assertArrayEquals(new String[] {"data_0.map", "other.map"}, names(tempDir.toFile().listFiles()));
        assertDoesNotThrow(() -> OpenModes.apply(file("data.map"), OpenMode.RECREATE, false));
    }

    @Test
    void recreateDeletesAllRollingFiles() throws IOException {
        createFiles("data.map", "data_0.map", "data_1.map", "data_5.map", "other_1.map");
        OpenModes.apply(file("data.map"), OpenMode.RECREATE, true);
        assertArrayEquals(new String[] {"data.map", "other_1.map"}, names(tempDir.toFile().listFiles()));
        assertDoesNotThrow(() -> OpenModes.apply(file("data.map"), OpenMode.RECREATE, true));
    }

    private File file(final String name) {
        return tempDir.resolve(name).toFile();
    }

    private void createFiles(final String... names) throws IOException {
        for (final String name : names) {
            Files.createFile(tempDir.resolve(name));
        }
    }
}
