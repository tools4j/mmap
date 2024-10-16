/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2024 tools4j.org (Marco Terzer, Anton Anufriev)
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
package org.tools4j.mmap.region.unsafe;

import org.agrona.IoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.region.api.AccessMode;
import org.tools4j.mmap.region.api.Unsafe;
import org.tools4j.mmap.region.impl.FileInitialiser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;

@Unsafe
public class ReadOnlyFileMapper implements FileMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReadOnlyFileMapper.class);

    private final File file;
    private final Path filePath;
    private final FileInitialiser fileInitialiser;

    private RandomAccessFile rafFile = null;
    private FileChannel fileChannel = null;
    private boolean closed;

    public ReadOnlyFileMapper(final File file, final FileInitialiser fileInitialiser) {
        this.file = Objects.requireNonNull(file);
        this.filePath = file.toPath();
        this.fileInitialiser = Objects.requireNonNull(fileInitialiser);
    }

    public ReadOnlyFileMapper(final String fileName, final FileInitialiser fileInitialiser) {
        this(new File(fileName), fileInitialiser);
    }

    @Override
    public AccessMode mapMode() {
        return AccessMode.READ_ONLY;
    }

    @Override
    public long map(long position, int length) {
        if (!init()) {
            return NULL_ADDRESS;
        }

        try {
            if (fileChannel != null && fileChannel.isOpen()) {
                if (position < fileChannel.size()) {
                    return IoUtil.map(fileChannel, AccessMode.READ_ONLY.getMapMode(), position, length);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to get fileChannel size " + file, e);
        }
        return NULL_ADDRESS;
    }

    @Override
    public void unmap(long address, long position, int length) {
        assert address > NULL_ADDRESS;
        assert position > NULL_POSITION;
        IoUtil.unmap(fileChannel, address, length);
    }

    private boolean init() {
        if (rafFile == null) {
            if (!file.exists()) {
                return false;
            }

            try {
                if (Files.size(filePath) == 0) {
                    return false;
                }
            } catch (IOException e) {
                LOGGER.error("Failed to check file size" + file, e);
                return false;
            }

            final RandomAccessFile raf;
            try {
                raf = new RandomAccessFile(file, AccessMode.READ_ONLY.getRandomAccessMode());
            } catch (FileNotFoundException e) {
                LOGGER.error("Failed to create new random access file " + file, e);
                return false;
            }

            rafFile = Objects.requireNonNull(raf);
            fileChannel = raf.getChannel();
            try {
                fileInitialiser.init(file.getName(), fileChannel);
            } catch (final IOException e) {
                LOGGER.error("Failed to initialise fileChannel for " + file, e);
                try {
                    this.fileChannel.close();
                } catch (IOException ex) {
                    LOGGER.error("Failed to close fileChannel after initialisation failure: " + file, e);
                }
                try {
                    this.rafFile.close();
                } catch (IOException ex) {
                    LOGGER.error("Failed to close RAF file after initialisation failure: " + file, e);
                }
                this.rafFile = null;
                this.fileChannel = null;
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (!closed) {
            try {
                if (fileChannel != null) {
                    fileChannel.close();
                }
                if (rafFile != null) {
                    rafFile.close();
                }
            } catch (final IOException e) {
                LOGGER.warn("Closing read-only file mapper caused unexpected exception: file={}", file, e);
            } finally {
                fileChannel = null;
                rafFile = null;
                closed = true;
                LOGGER.info("Closed read-only file mapper: file={}", file);
            }
        }
    }
}
