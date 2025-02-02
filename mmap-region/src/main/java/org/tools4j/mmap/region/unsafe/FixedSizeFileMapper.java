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
package org.tools4j.mmap.region.unsafe;

import org.agrona.LangUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.region.api.AccessMode;
import org.tools4j.mmap.region.api.Unsafe;
import org.tools4j.mmap.region.impl.FileInitialiser;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;

@Unsafe
public class FixedSizeFileMapper implements FileMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(FixedSizeFileMapper.class);
    private final File file;
    private final long fileSize;
    private final AccessMode accessMode;
    private final FileInitialiser fileInitialiser;
    private final PreTouchHelper preTouchHelper;
    private RandomAccessFile rafFile;
    private FileChannel fileChannel;

    public FixedSizeFileMapper(final File file, 
                               final long fileSize, 
                               final AccessMode accessMode,
                               final FileInitialiser fileInitialiser) {
        this.file = requireNonNull(file);
        this.fileSize = fileSize;
        this.accessMode = requireNonNull(accessMode);
        this.preTouchHelper = new PreTouchHelper(accessMode);
        this.fileInitialiser = requireNonNull(fileInitialiser);
        init();
    }

    public FixedSizeFileMapper(final String fileName,
                               final long fileSize,
                               final AccessMode accessMode,
                               final FileInitialiser fileInitialiser) {
        this(new File(fileName), fileSize, accessMode, fileInitialiser);
    }

    private void checkNotClosed() {
        if (isClosed()) {
            throw new IllegalStateException("Fixed-size file mapper is closed");
        }
    }

    public long fileSize() {
        return fileSize;
    }

    @Override
    public AccessMode accessMode() {
        return accessMode;
    }

    @Override
    public long map(final long position, final int length) {
        checkNotClosed();
        if (position < 0 || length < 0 || position + length > fileSize) {
            return NULL_ADDRESS;
        }
        final long address = FileChannels.map(fileChannel, accessMode.getMapMode(), position, length);
        preTouchHelper.preTouch(position, length, address);
        return address;
    }

    @Override
    public void unmap(final long address, final long position, final int length) {
        checkNotClosed();
        assert address > NULL_ADDRESS;
        assert position > NULL_POSITION;
        FileChannels.unmap(fileChannel, address, length);
    }

    private void init() {
        if (rafFile == null) {
            String action = "open";
            try {
                action = "create";
                if (!file.exists()) {
                    if (!file.createNewFile()) {
                        if (!file.exists()) {
                            throw new IOException("Could not create new file " + file);
                        }
                    } else {
                        LOGGER.info("Created new file: {}", file);
                    }
                }
                action = "open";
                rafFile = new RandomAccessFile(file, accessMode.getRandomAccessMode());
                fileChannel = rafFile.getChannel();
                action = "initialize";
                rafFile.setLength(fileSize);
                fileInitialiser.init(file.getName(), fileChannel);
            } catch (final IOException e) {
                LOGGER.error("Failed to {} file: {}", action, file, e);
                close(false);
                LangUtil.rethrowUnchecked(e);
            }
        }
    }

    public boolean isClosed() {
        return fileChannel == null;
    }

    @Override
    public void close() {
        close(true);
    }

    private void close(final boolean log) {
        boolean closed = false;
        try {
            if (fileChannel != null) {
                closed = true;
                fileChannel.close();
            }
            if (rafFile != null) {
                closed = true;
                rafFile.close();
            }
        } catch (final IOException e) {
            LOGGER.warn("Closing fixed-size file mapper caused unexpected exception: file={}", file, e);
        } finally {
            fileChannel = null;
            rafFile = null;
            preTouchHelper.reset();
            if (log && closed) {
                LOGGER.info("Closed fixed-size file mapper: file={}", file);
            }
        }
    }

    @Override
    public String toString() {
        return "FixedSizeFileMapper" +
                ":fileSize=" + fileSize +
                "|accessMode=" + accessMode +
                "|file=" + file +
                "|closed=" + isClosed();
    }
}
