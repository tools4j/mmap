/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 hover-raft (tools4j), Anton Anufriev, Marco Terzer
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
package org.tools4j.mmap.region.impl;

import sun.nio.ch.FileChannelImpl;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.channels.FileChannel;
import java.util.Objects;

public final class MappedFile implements Closeable {
    public static long REGION_SIZE_GRANULARITY = regionSizeGranularity();

    public enum Mode {
        READ_ONLY("r", FileChannel.MapMode.READ_ONLY),
        READ_WRITE("rw", FileChannel.MapMode.READ_WRITE),
        /** Delete io contents on open*/
        READ_WRITE_CLEAR("rw", FileChannel.MapMode.READ_WRITE);

        private final String rasMode;
        private final FileChannel.MapMode mapMode;
        Mode(final String rasMode, final FileChannel.MapMode mapMode) {
            this.rasMode = Objects.requireNonNull(rasMode);
            this.mapMode = Objects.requireNonNull(mapMode);
        }

        public String getRandomAccessMode() {
            return rasMode;
        }
        public FileChannel.MapMode getMapMode() {
            return mapMode;
        }
    }

    public interface FileInitialiser {
        void init(FileChannel file, Mode mode) throws IOException;
    }

    private final RandomAccessFile file;
    private final FileChannel fileChannel;
    private final Mode mode;
    private final long regionSize;

    public MappedFile(final String fileName, final Mode mode, final long regionSize) throws IOException {
        this(new File(fileName), mode, regionSize);
    }

    public MappedFile(final String fileName, final Mode mode, final long regionSize, final FileInitialiser fileInitialiser) throws IOException {
        this(new File(fileName), mode, regionSize, fileInitialiser);
    }

    public MappedFile(final File file, final Mode mode, final long regionSize) throws IOException {
        this(file, mode, regionSize, (c,m) -> {});
    }

    public MappedFile(final File file, final Mode mode, final long regionSize, final FileInitialiser fileInitialiser) throws IOException {
        if (regionSize <= 0 || (regionSize % REGION_SIZE_GRANULARITY) != 0) {
            throw new IllegalArgumentException("Region size must be positive and a multiple of " + REGION_SIZE_GRANULARITY + " but was " + regionSize);
        }
        if (mode == Mode.READ_WRITE_CLEAR && file.exists()) {
            file.delete();
        }
        if (!file.exists()) {
            if (mode == Mode.READ_ONLY) {
                throw new FileNotFoundException(file.getAbsolutePath());
            }
            file.createNewFile();
        }
        final RandomAccessFile raf = new RandomAccessFile(file, mode.getRandomAccessMode());
        this.file = Objects.requireNonNull(raf);
        this.mode = Objects.requireNonNull(mode);
        this.regionSize = regionSize;
        this.fileChannel = raf.getChannel();
        fileInitialiser.init(fileChannel, mode);
    }

    public Mode getMode() {
        return mode;
    }

    public long getRegionSize() {
        return regionSize;
    }

    public long getFileLength() {
        try {
            return file.length();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setFileLength(final long length) {
        try {
            file.setLength(length);
        } catch (final IOException e) {
            throw new RuntimeException("could not set io length to " + length, e);
        }
    }

    public FileChannel getFileChannel() {
        return fileChannel;
    }

    public void close() {
        try {
            file.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static long regionSizeGranularity() {
        try {
            final Method method = FileChannelImpl.class.getDeclaredMethod("initIDs");
            method.setAccessible(true);
            final long result = (long)method.invoke(null);
            method.setAccessible(false);
            return result;
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }
}
