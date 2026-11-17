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
package org.tools4j.mmap.mapping.config;

import org.tools4j.mmap.mapping.api.FileInitialiser;

/**
 * How the file initialiser {@link FileInitialiser#ZERO} fills new file space with zeros: by allocating it natively
 * with {@code fallocate}, so that it reads as zero without writing, or by writing zeros. Native allocation needs
 * 64-bit Linux, Java 22 or later, a file system supporting {@code fallocate}, and native access, enabled with
 * {@code --enable-native-access=ALL-UNNAMED}; zeros are written where it cannot be used. Selected by the system
 * property {@value MappingConfigurations#ZERO_FILL_PROPERTY}, see {@link MappingConfigurations#defaultZeroFillMode()}.
 * <p>
 * Warnings go to the error listener once per JVM, the one about the file system once per file.
 */
public enum ZeroFillMode {
    /**
     * Allocates natively if possible with native access enabled, and writes zeros otherwise, warning only if native
     * access is not enabled on Java 22 or later; the default.
     */
    AUTO,
    /**
     * Allocates natively whenever the JVM allows it, also if native access is not enabled, which the JVM then warns
     * about itself; writes zeros with a warning otherwise: on Java 17 to 21, on other platforms, once the JVM denies
     * native access, and on file systems not supporting it.
     */
    FALLOCATE,
    /** Always writes zeros, never warning. */
    WRITE
}
