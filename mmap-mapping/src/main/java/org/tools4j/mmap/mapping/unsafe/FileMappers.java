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
import org.tools4j.mmap.mapping.api.FileInitialiser;
import org.tools4j.mmap.mapping.api.Unsafe;
import org.tools4j.mmap.mapping.config.MappingConfig;

import java.io.File;

import static java.util.Objects.requireNonNull;

@Unsafe
public enum FileMappers {
    ;
    public static FileMapper create(final File file,
                                    final AccessMode accessMode,
                                    final FileInitialiser fileInitialiser,
                                    final MappingConfig config) {
        switch (accessMode) {
            case READ_ONLY:
                if (config.rollFiles()) {
                    return isAsyncMapping(config)
                            ? ConcurrentRollingFileMapper.forReadOnly(file, config, fileInitialiser)
                            : RollingFileMapper.forReadOnly(file, config, fileInitialiser);
                }
                return new ReadOnlyFileMapper(file, fileInitialiser);
            case READ_WRITE:
                if (config.rollFiles()) {
                    return isAsyncMapping(config)
                            ? ConcurrentRollingFileMapper.forReadWrite(file, accessMode, config, fileInitialiser)
                            : RollingFileMapper.forReadWrite(file, accessMode, config, fileInitialiser);
                }
                if (config.expandFile()) {
                    return new ExpandableSizeFileMapper(file, config.minFileSize(), config.maxFileSize(),
                            config.mappingStrategy().regionSize(), fileInitialiser);
                }
                return new FixedSizeFileMapper(file, config.maxFileSize(), accessMode, fileInitialiser);
            default:
                throw new IllegalArgumentException("Unsupported access mode: " + accessMode);
        }
    }

    /**
     * Async mapping maps from a second thread, which requires a thread-safe rolling file mapper; async unmapping does
     * not, as unmapping is thread-safe for all file mappers.
     */
    private static boolean isAsyncMapping(final MappingConfig config) {
        return config.mappingStrategy().asyncMapping().isPresent();
    }

    /**
     * Returns a file mapper over the same file(s) as the given one, for use by one other thread such as an async
     * mapping thread. For a {@link ConcurrentRollingFileMapper}, this is a new per-thread file mapper;
     * all other file mappers except for {@link RollingFileMapper} are thread-safe, and the given file mapper itself is
     * returned.
     * <p>
     * The returned file mapper must not be closed by the caller, it is released when the given file mapper is closed,
     * which must happen only after the other thread has stopped using it.
     *
     * @param fileMapper the file mapper whose file(s) are to be mapped by another thread
     * @return a file mapper for use by the other thread
     * @throws IllegalArgumentException if the given file mapper is single-threaded
     */
    public static FileMapper forThread(final FileMapper fileMapper) {
        if (fileMapper instanceof ConcurrentRollingFileMapper.PerThreadFileMapper perThreadFileMapper) {
            return perThreadFileMapper.newSibling();
        }
        if (fileMapper instanceof RollingFileMapper) {
            throw new IllegalArgumentException("Single-threaded " + fileMapper + " cannot be used by another thread");
        }
        return requireNonNull(fileMapper);
    }
}
