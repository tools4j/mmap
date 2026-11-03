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
package org.tools4j.mmap.mapping.unsafe;

import org.tools4j.mmap.mapping.api.AccessMode;
import org.tools4j.mmap.mapping.api.Describable;
import org.tools4j.mmap.mapping.api.ErrorListener;
import org.tools4j.mmap.mapping.api.FileInitialiser;
import org.tools4j.mmap.mapping.api.LifecycleListener;
import org.tools4j.mmap.mapping.api.Unsafe;
import org.tools4j.mmap.mapping.config.MappingConfigurations;

import java.io.File;
import java.nio.channels.FileChannel;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.mapping.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.mapping.api.NullValues.NULL_POSITION;
import static org.tools4j.mmap.mapping.impl.Constraints.validateNotClosed;

/**
 * A {@link FileMapper} for a file of fixed, pre-allocated size.
 * <p>
 * <b>Note:</b> This class is <b>thread safe</b>; {@code map} and {@code unmap} may be called concurrently from
 * multiple threads.
 */
@Unsafe
public class FixedSizeFileMapper implements FileMapper, Describable {
    private final File file;
    private final long fileSize;
    private final AccessMode accessMode;
    private final FileChannelProvider fileChannelProvider;
    private final PreToucher preToucher;

    public FixedSizeFileMapper(final File file, 
                               final long fileSize, 
                               final AccessMode accessMode,
                               final FileInitialiser fileInitialiser) {
        this(file, fileSize, accessMode, fileInitialiser, MappingConfigurations.defaultLifecycleListener(),
                MappingConfigurations.defaultErrorListener());
    }

    public FixedSizeFileMapper(final File file,
                               final long fileSize,
                               final AccessMode accessMode,
                               final FileInitialiser fileInitialiser,
                               final LifecycleListener lifecycleListener,
                               final ErrorListener errorListener) {
        this.file = requireNonNull(file);
        this.fileSize = fileSize;
        this.accessMode = requireNonNull(accessMode);
        this.preToucher = new PreToucher();
        this.fileChannelProvider = new FileChannelProvider(this, accessMode, file, fileInitialiser, fileSize,
                lifecycleListener, errorListener);
        init();
    }

    public File file() {
        return file;
    }

    public long fileSize() {
        return fileSize;
    }

    @Override
    public long mappingLimit() {
        return fileSize;
    }

    @Override
    public AccessMode accessMode() {
        return accessMode;
    }

    @Override
    public long map(final long position, final int length, final boolean preTouch) {
        assert position >= 0;
        assert length >= 0;
        if (position + length > fileSize) {
            throw new IllegalArgumentException("Attempt to map [" + position + ", " + (position + length - 1) + "] " +
                    "exceeds fixed file size " + fileSize + " for file " + file);
        }
        //NOTE: relying on fileChannelProvider's closed check
        final FileChannel channel = fileChannelProvider.get();
        if (channel == null) {
            throw new IllegalStateException("Failed to open file channel for file " + file);
        }
        final long address = FileChannels.map(channel, accessMode.mapMode(), position, length);
        if (preTouch && accessMode != AccessMode.READ_ONLY) {//pre-touching writes, which a read-only mapping must not
            preToucher.preTouch(position, length, address);
        }
        return address;
    }

    @Override
    public void unmap(final long position, final long address, final int length) {
        assert address > NULL_ADDRESS;
        assert position > NULL_POSITION;
        validateNotClosed(this);
        FileChannels.unmap(address, length);
    }

    private void init() {
        fileChannelProvider.get();
    }

    public boolean isClosed() {
        return fileChannelProvider.isClosed();
    }

    @Override
    public void close() {
        fileChannelProvider.closeIfNeeded();
    }

    @Override
    public StringBuilder describeTo(final StringBuilder sb) {
        return sb.append("FixedSizeFileMapper")
                .append(":file=").append(file.getPath())
                .append("|fileSize=").append(fileSize)
                .append("|accessMode=").append(accessMode)
                .append("|closed=").append(isClosed());
    }

    @Override
    public String toString() {
        return Describable.describe(this);
    }
}
