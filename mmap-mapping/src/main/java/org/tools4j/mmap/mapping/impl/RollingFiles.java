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

import java.io.File;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * Naming of rolling files: the file index is inserted before the file ending of the base file, e.g. {@code data_3.map}
 * for base file {@code data.map} and index 3.
 */
public enum RollingFiles {
    ;

    /**
     * Returns the rolling file for the given index.
     *
     * @param baseFile the base file of the rolling files
     * @param index    the zero-based file index
     * @return the rolling file for the given index
     */
    public static File fileForIndex(final File baseFile, final int index) {
        final String name = baseFile.getName();
        final int nameEnd = nameEnd(name);
        return new File(baseFile.getParentFile(), name.substring(0, nameEnd) + "_" + index + name.substring(nameEnd));
    }

    /**
     * Returns all existing rolling files of the given base file, in no particular order.
     *
     * @param baseFile the base file of the rolling files
     * @return the existing rolling files, empty if there are none
     */
    public static File[] listFiles(final File baseFile) {
        final String name = baseFile.getName();
        final int nameEnd = nameEnd(name);
        final Pattern pattern = Pattern.compile(Pattern.quote(name.substring(0, nameEnd)) + "_\\d+" +
                Pattern.quote(name.substring(nameEnd)));
        final File dir = requireNonNull(baseFile.getAbsoluteFile().getParentFile());
        final File[] files = dir.listFiles((d, fileName) -> pattern.matcher(fileName).matches());
        return files == null ? new File[0] : files;
    }

    private static int nameEnd(final String name) {
        final int dotIndex = name.lastIndexOf('.');
        return dotIndex < 0 ? name.length() : dotIndex;
    }
}
