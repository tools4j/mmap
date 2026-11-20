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
 * The {@link OpenMode open modes} for reading and writing, also defined as constants of {@link OpenMode}.
 */
public enum ReadWriteOpenMode implements OpenMode {
    /**
     * Opens for reading and writing, which must exist.
     */
    READ_WRITE,
    /**
     * Creates for reading and writing, which must not exist yet.
     */
    CREATE,
    /**
     * Opens for reading and writing if it exists, and creates it otherwise.
     */
    OPEN_OR_CREATE,
    /**
     * Deletes if it exists, and creates it again for reading and writing.
     * <p>
     * <b>NOTE:</b> must only be used if no one else has it open. On POSIX systems, deleted files stay mapped by those
     * still using them, who silently continue with the old content, while new users get the new content.
     */
    RECREATE;

    /**
     * Returns {@link AccessMode#READ_WRITE READ_WRITE}.
     *
     * @return the read-write access mode
     */
    @Override
    public AccessMode accessMode() {
        return AccessMode.READ_WRITE;
    }
}
