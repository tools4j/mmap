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
package org.tools4j.mmap.region.unsafe;

import org.tools4j.mmap.region.api.AccessMode;
import org.tools4j.mmap.region.api.Unsafe;
import org.tools4j.mmap.region.config.MappingConfig;
import org.tools4j.mmap.region.impl.FileInitialiser;

import java.io.File;

import static org.tools4j.mmap.region.impl.Constraints.validateMaxOpenFiles;

@Unsafe
public class FileMappers {
    ;
    public static FileMapper create(final File file,
                                    final AccessMode accessMode,
                                    final FileInitialiser fileInitialiser,
                                    final MappingConfig config) {
        switch (accessMode) {
            case READ_ONLY:
                if (config.rollFiles()) {
                    final int maxThreadCount = maxThreadCountFor(config);
                    validateMaxOpenFiles(config.maxOpenFiles(), maxThreadCount);
                    return maxThreadCount > 1
                            ? ConcurrentRollingFileMapper.forReadOnly(file, config, fileInitialiser)
                            : RollingFileMapper.forReadOnly(file, config, fileInitialiser);
                }
                return new ReadOnlyFileMapper(file, fileInitialiser);
            case READ_WRITE:
            case READ_WRITE_CLEAR:
                final int maxThreadCount = maxThreadCountFor(config);
                if (config.rollFiles()) {
                    validateMaxOpenFiles(config.maxOpenFiles(), maxThreadCount);
                    return maxThreadCount > 1
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

    private static int maxThreadCountFor(final MappingConfig config) {
        int maxThreadCount = 1;
        if (config.mappingStrategy().asyncMapping().isPresent()) {
            maxThreadCount++;
        }
        if (config.mappingStrategy().asyncUnmapping().isPresent()) {
            maxThreadCount++;
        }
        //NOTE: if mapper/unmapper share the same runtime, we only have 2 threads, but it is difficult to work this out
        //      from here
        return maxThreadCount;
    }
}
