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

import org.tools4j.mmap.core.base.Describable;
import org.tools4j.mmap.core.base.ErrorListener;
import org.tools4j.mmap.core.base.LifecycleListener;
import org.tools4j.mmap.core.unsafe.Unsafe;
import org.tools4j.mmap.mapping.api.AccessMode;
import org.tools4j.mmap.mapping.api.FileInitialiser;
import org.tools4j.mmap.mapping.config.MappingConfigurations;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.core.internal.Validations.validateNotClosed;
import static org.tools4j.mmap.mapping.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.mapping.api.NullValues.NULL_POSITION;

/**
 * A {@link FileMapper} for read-only access to an existing file.
 * <p>
 * <b>Note:</b> This class is <b>thread safe</b>; {@code map} and {@code unmap} may be called concurrently from
 * multiple threads.
 */
@Unsafe
public class ReadOnlyFileMapper implements FileMapper, Describable {

    private final File file;
    private final FileChannelProvider fileChannelProvider;
    private volatile long fileSizeCache;

    public ReadOnlyFileMapper(final File file, final FileInitialiser fileInitialiser) {
        this(file, fileInitialiser, MappingConfigurations.defaultLifecycleListener(),
                MappingConfigurations.defaultErrorListener());
    }

    public ReadOnlyFileMapper(final File file,
                              final FileInitialiser fileInitialiser,
                              final LifecycleListener lifecycleListener,
                              final ErrorListener errorListener) {
        this.file = requireNonNull(file);
        this.fileChannelProvider = new FileChannelProvider(this, AccessMode.READ_ONLY, file, fileInitialiser, 0,
                lifecycleListener, errorListener);
    }

    public ReadOnlyFileMapper(final String fileName, final FileInitialiser fileInitialiser) {
        this(new File(fileName), fileInitialiser);
    }

    @Override
    public AccessMode accessMode() {
        return AccessMode.READ_ONLY;
    }

    @Override
    public long map(final long position, final int length, final boolean preTouch) {
        assert position >= 0;
        assert length >= 0;
        //NOTE: relying on fileChannelProvider's closed check
        final FileChannel channel = fileChannelProvider.get();
        if (channel == null || !channel.isOpen()) {
            return NULL_ADDRESS;
        }
        try {
            final long end = position + length;
            if (end > fileSizeCache && end > (fileSizeCache = channel.size())) {
                return NULL_ADDRESS;
            }
            return FileChannels.map(channel, AccessMode.READ_ONLY.mapMode(), position, length);
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to map position " + position + " with length " + length +
                    " of file " + file, e);
        }
    }

    /**
     * Returns {@link Long#MAX_VALUE}: mapping beyond the current end of the file is legal and yields
     * {@code NULL_ADDRESS} until the file has been extended by a writer.
     *
     * @return {@link Long#MAX_VALUE}
     */
    @Override
    public long mappingLimit() {
        return Long.MAX_VALUE;
    }

    @Override
    public void unmap(long position, long address, int length) {
        assert address > NULL_ADDRESS;
        assert position > NULL_POSITION;
        validateNotClosed(this);
        FileChannels.unmap(address, length);
    }

    @Override
    public boolean isClosed() {
        return fileChannelProvider.isClosed();
    }

    @Override
    public void close() {
        fileChannelProvider.closeIfNeeded();
    }

    @Override
    public StringBuilder describeTo(final StringBuilder sb) {
        return sb.append("ReadOnlyFileMapper")
                .append(":file=").append(file.getPath())
                .append("|closed=").append(isClosed());
    }

    @Override
    public String toString() {
        return Describable.describe(this);
    }
}
