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

import static java.util.Objects.requireNonNull;

/**
 * Defines how a file, or a queue, is opened: with which {@linkplain #accessMode() access mode}, and what happens if it
 * does or does not exist yet.
 * <p>
 * For a mapping, the file exists if the mapped file exists, or for rolling files any of them. For a queue, the
 * queue exists if it has been created before, even if it is still empty.
 */
public enum OpenMode {
    /**
     * Opens for reading only, which must exist.
     */
    READ_ONLY(AccessMode.READ_ONLY),
    /**
     * Opens for reading only, which may not exist yet. A mapping becomes available once its file is created, and a
     * queue's entries once the queue is created and they are appended.
     */
    READ_ONLY_LAZY(AccessMode.READ_ONLY),
    /**
     * Opens for reading and writing, which must exist.
     */
    READ_WRITE(AccessMode.READ_WRITE),
    /**
     * Creates for reading and writing, which must not exist yet.
     */
    CREATE(AccessMode.READ_WRITE),
    /**
     * Opens for reading and writing if it exists, and creates it otherwise.
     */
    OPEN_OR_CREATE(AccessMode.READ_WRITE),
    /**
     * Deletes if it exists, and creates it again for reading and writing.
     * <p>
     * <b>NOTE:</b> must only be used if no one else has it open. On POSIX systems, deleted files stay mapped by those
     * still using them, who silently continue with the old content, while new users get the new content.
     */
    RECREATE(AccessMode.READ_WRITE);

    private final AccessMode accessMode;

    OpenMode(final AccessMode accessMode) {
        this.accessMode = requireNonNull(accessMode);
    }

    /**
     * Returns the access mode resulting from opening with this open mode.
     *
     * @return {@link AccessMode#READ_ONLY READ_ONLY} for {@link #READ_ONLY} and {@link #READ_ONLY_LAZY}, and
     *         {@link AccessMode#READ_WRITE READ_WRITE} otherwise
     */
    public AccessMode accessMode() {
        return accessMode;
    }
}
