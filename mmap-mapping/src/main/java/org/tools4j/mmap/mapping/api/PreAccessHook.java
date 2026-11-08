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

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * Hook invoked before a file or its new space is accessed through a mapping, provided by a {@link FileInitialiser}
 * for the access mode the file is opened with: once when the file is opened, and each time a writable file is
 * extended.
 * <p>
 * For writable files, the hook is invoked under an exclusive file lock. New space starts at the current file size, and
 * existing content must be left untouched. The file has at least the requested size afterwards whatever the hook did:
 * space it did not write is added without writing, as sparse file space reading as zero bytes.
 * <p>
 * Read-only files are never extended, and must not be modified by the hook.
 */
public interface PreAccessHook {
    /** Hook doing nothing, leaving new file space sparse */
    PreAccessHook NO_OP = new PreAccessHook() {
        @Override
        public String toString() {
            return "NO_OP";
        }
    };

    /**
     * Invoked when the file is opened, validating a read-only file or initialising a writable file, by default
     * initialising new space like {@link #onExtend(File, FileChannel, long) onExtend}.
     *
     * @param file    the file, for reference in messages
     * @param channel the file channel
     * @param size    the min file size, zero for none
     * @throws IOException if initialising or validating the file fails
     */
    default void onOpen(final File file, final FileChannel channel, final long size) throws IOException {
        onExtend(file, channel, size);
    }

    /**
     * Invoked when a writable file is extended, to initialise the new space from the current file size up to the
     * given size; does nothing by default.
     *
     * @param file    the file, for reference in messages
     * @param channel the file channel
     * @param size    the new file size
     * @throws IOException if initialising the new space fails
     */
    default void onExtend(final File file, final FileChannel channel, final long size) throws IOException {
        //no-op
    }
}
