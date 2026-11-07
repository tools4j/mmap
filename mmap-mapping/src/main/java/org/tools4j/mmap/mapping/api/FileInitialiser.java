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

import org.tools4j.mmap.mapping.impl.StandardFileInitialiser;

/**
 * Initialises mapped files, providing the {@link PreAccessHook} for the access mode a file is opened with.
 * <p>
 * The standard initialisers leave read-only files untouched, and differ in how new space of writable files is
 * initialised:
 * <ul>
 *     <li>{@link #LAZY}: the file length is extended without writing, leaving sparse file space that reads as zero
 *         bytes and is allocated by the file system only when a page of the mapping is first written; if the disk is
 *         full at that point, the write faults, crashing the JVM or throwing an asynchronous {@link InternalError}</li>
 *     <li>{@link #ZERO}: new space is allocated by writing zero bytes when the file is opened or extended</li>
 *     <li>{@link #MINUS_ONE}: new space is allocated by writing bytes with all bits set</li>
 * </ul>
 * Existing file content is never overwritten.
 */
@FunctionalInterface
public interface FileInitialiser {
    /** Initialiser extending files without writing, leaving sparse space allocated on first write to the mapping */
    FileInitialiser LAZY = StandardFileInitialiser.LAZY;
    /** Initialiser allocating new file space by writing zero bytes */
    FileInitialiser ZERO = StandardFileInitialiser.ZERO;
    /** Initialiser allocating new file space by writing minus one bytes, that is, bytes with all bits set */
    FileInitialiser MINUS_ONE = StandardFileInitialiser.MINUS_ONE;

    /**
     * Returns the hook for a file opened with the given access mode, invoked once per file mapper, which uses the
     * returned hook for its file only; the standard initialisers return the same thread safe hook for all files.
     *
     * @param accessMode the access mode the file is opened with
     * @return the hook, {@link PreAccessHook#NO_OP} if the file needs no initialisation or validation
     */
    PreAccessHook hook(AccessMode accessMode);
}
