/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2025 tools4j.org (Marco Terzer, Anton Anufriev)
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

import org.agrona.IoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.region.api.FileMapper;
import org.tools4j.mmap.region.api.MapMode;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class SingleFileReadOnlyMapper implements FileMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(SingleFileReadOnlyMapper.class);

    private final File file;
    private final Path filePath;
    private final FileInitialiser fileInitialiser;

    private static final MapMode MAP_MODE = MapMode.READ_ONLY;

    private RandomAccessFile rafFile = null;
    private FileChannel fileChannel = null;

    public SingleFileReadOnlyMapper(final File file, FileInitialiser fileInitialiser) {
        this.file = Objects.requireNonNull(file);
        this.filePath = file.toPath();
        this.fileInitialiser = Objects.requireNonNull(fileInitialiser);
    }

    public SingleFileReadOnlyMapper(String fileName, FileInitialiser fileInitialiser) {
        this(new File(fileName), fileInitialiser);
    }
    @Override
    public long map(long position, int length) {
        if (!init()) {
            return NULL_ADDRESS;
        }

        try {
            if (fileChannel != null && fileChannel.isOpen()) {
                if (position < fileChannel.size()) {
                    return IoUtil.map(fileChannel, MAP_MODE.getMapMode(), position, length);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to get fileChannel size " + file, e);
        }
        return NULL_ADDRESS;
    }

    @Override
    public void unmap(long address, long position, int length) {
        assert address >= 0;
        assert position >= 0;
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
                raf = new RandomAccessFile(file, MAP_MODE.getRandomAccessMode());
            } catch (FileNotFoundException e) {
                LOGGER.error("Failed to create new random access file " + file, e);
                return false;
            }

            rafFile = Objects.requireNonNull(raf);
            fileChannel = raf.getChannel();
            try {
                fileInitialiser.init(file.getName(), fileChannel);
            } catch (IOException e) {
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
    public void close() {
        try {
            if (fileChannel != null) {
                fileChannel.close();
            }
            if (rafFile != null) {
                rafFile.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        LOGGER.info("Closed read-only file mapper. file={}", file);
    }
}
