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

import org.tools4j.mmap.mapping.api.AccessMode;
import org.tools4j.mmap.mapping.api.FileInitialiser;
import org.tools4j.mmap.mapping.api.PreAccessHook;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * The standard {@link FileInitialiser file initialisers}, accessed through the constants in {@link FileInitialiser}.
 */
public enum StandardFileInitialiser implements FileInitialiser {
    LAZY,
    ZERO((byte) 0),
    MINUS_ONE((byte) -1);

    private static final int FILL_CHUNK_SIZE = 64 * 1024;

    private final PreAccessHook readWriteHook;

    StandardFileInitialiser() {
        this.readWriteHook = PreAccessHook.NO_OP;
    }

    StandardFileInitialiser(final byte value) {
        final ByteBuffer buffer = ByteBuffer.allocateDirect(FILL_CHUNK_SIZE);
        for (int i = 0; i < FILL_CHUNK_SIZE; i++) {
            buffer.put(i, value);
        }
        final ByteBuffer initialBytes = buffer.asReadOnlyBuffer();
        this.readWriteHook = new Filler(name(), ThreadLocal.withInitial(initialBytes::duplicate));
    }

    @Override
    public PreAccessHook hook(final AccessMode accessMode) {
        return accessMode == AccessMode.READ_WRITE ? readWriteHook : PreAccessHook.NO_OP;
    }

    /**
     * Fills new file space with the initial bytes, shared by all files and thread safe: each thread writes from its own
     * view of the prefilled buffer.
     */
    private record Filler(String name, ThreadLocal<ByteBuffer> initialBytes) implements PreAccessHook {
        @Override
        public void onExtend(final String fileName, final FileChannel channel, final long size) throws IOException {
            final ByteBuffer source = initialBytes.get();
            long position = channel.size();
            while (position < size) {
                source.clear().limit((int) Math.min(FILL_CHUNK_SIZE, size - position));
                while (source.hasRemaining()) {
                    position += channel.write(source, position);
                }
            }
        }

        @Override
        public String toString() {
            return "Filler:" + name;
        }
    }
}
