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
package org.tools4j.mmap.mapping.api;

/**
 * Defines how a file, or a queue, is opened: with which {@linkplain #accessMode() access mode}, and what happens if it
 * does or does not exist yet.
 * <p>
 * The open modes are the constants of {@link ReadOnlyOpenMode} and {@link ReadWriteOpenMode}, which are also defined
 * here, each typed by its enum, so that {@code OpenMode.READ_ONLY} can be passed where a {@link ReadOnlyOpenMode} is
 * expected, for instance to create a read-only mapping, and {@code OpenMode.CREATE} where a {@link ReadWriteOpenMode}
 * is expected, for instance to create a mutable mapping.
 * <p>
 * For a mapping, the file exists if the mapped file exists, or for rolling files any of them. For a queue, the
 * queue exists if it has been created before, even if it is still empty.
 */
public sealed interface OpenMode permits ReadOnlyOpenMode, ReadWriteOpenMode {
    //NOTE: no default methods, they would initialise this interface when the enums are initialised, before its
    //      constants are assigned, see JLS 12.4.1 and 12.4.2

    /**
     * Opens for reading only, which must exist.
     */
    ReadOnlyOpenMode READ_ONLY = ReadOnlyOpenMode.READ_ONLY;
    /**
     * Opens for reading only, which may not exist yet. A mapping becomes available once its file is created, and a
     * queue's entries once the queue is created and they are appended.
     */
    ReadOnlyOpenMode READ_ONLY_LAZY = ReadOnlyOpenMode.READ_ONLY_LAZY;
    /**
     * Opens for reading and writing, which must exist.
     */
    ReadWriteOpenMode READ_WRITE = ReadWriteOpenMode.READ_WRITE;
    /**
     * Creates for reading and writing, which must not exist yet.
     */
    ReadWriteOpenMode CREATE = ReadWriteOpenMode.CREATE;
    /**
     * Opens for reading and writing if it exists, and creates it otherwise.
     */
    ReadWriteOpenMode OPEN_OR_CREATE = ReadWriteOpenMode.OPEN_OR_CREATE;
    /**
     * Deletes if it exists, and creates it again for reading and writing.
     * <p>
     * <b>NOTE:</b> must only be used if no one else has it open. On POSIX systems, deleted files stay mapped by those
     * still using them, who silently continue with the old content, while new users get the new content.
     */
    ReadWriteOpenMode RECREATE = ReadWriteOpenMode.RECREATE;

    /**
     * Returns the access mode resulting from opening with this open mode.
     *
     * @return {@link AccessMode#READ_ONLY READ_ONLY} for {@link ReadOnlyOpenMode} constants, and
     *         {@link AccessMode#READ_WRITE READ_WRITE} for {@link ReadWriteOpenMode} constants
     */
    AccessMode accessMode();
}
