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

import org.tools4j.mmap.mapping.api.OpenMode;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;

/**
 * Applies an {@link OpenMode} to a file before it is mapped: validates that it exists or not, creates or deletes it.
 * Files that remain to be created are created when they are mapped.
 */
public enum OpenModes {
    ;

    /**
     * Applies the open mode to the given file, or to all rolling files of the given base file.
     *
     * @param file      the file, or the base file of the rolling files
     * @param openMode  the open mode
     * @param rollFiles true if the file is the base file of rolling files
     * @throws IllegalArgumentException if the file must exist but does not, must not exist but does, or cannot be
     *                                  created or deleted
     */
    public static void apply(final File file, final OpenMode openMode, final boolean rollFiles) {
        switch (openMode) {
            case READ_ONLY_LAZY, OPEN_OR_CREATE -> {}
            case READ_ONLY, READ_WRITE -> {
                if (!exists(file, rollFiles)) {
                    throw new IllegalArgumentException("File does not exist: " + file);
                }
            }
            case CREATE -> create(file, rollFiles);
            case RECREATE -> delete(file, rollFiles);
            default -> throw new IllegalArgumentException("Unsupported open mode: " + openMode);
        }
    }

    private static boolean exists(final File file, final boolean rollFiles) {
        return rollFiles ? RollingFiles.listFiles(file).length > 0 : file.exists();
    }

    private static void create(final File file, final boolean rollFiles) {
        if (rollFiles && exists(file, true)) {
            throw new IllegalArgumentException("Rolling files already exist: " + file);
        }
        final File toCreate = rollFiles ? RollingFiles.fileForIndex(file, 0) : file;
        try {
            Files.createFile(toCreate.toPath());
        } catch (final FileAlreadyExistsException e) {
            throw new IllegalArgumentException("File already exists: " + toCreate, e);
        } catch (final IOException e) {
            throw new IllegalArgumentException("Failed to create file " + toCreate + ": " + e, e);
        }
    }

    private static void delete(final File file, final boolean rollFiles) {
        for (final File toDelete : rollFiles ? RollingFiles.listFiles(file) : new File[] {file}) {
            try {
                Files.deleteIfExists(toDelete.toPath());
            } catch (final IOException e) {
                throw new IllegalArgumentException("Failed to delete file " + toDelete + ": " + e, e);
            }
        }
    }
}
