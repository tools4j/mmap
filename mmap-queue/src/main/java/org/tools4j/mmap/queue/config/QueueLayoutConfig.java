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
package org.tools4j.mmap.queue.config;

/**
 * Configuration of the queue layout, consisting of the settings that must be the same for all appenders and readers of
 * a queue. The queue layout is defined when the queue is created and is stored with the queue; when opening an existing
 * queue, the stored layout is used instead of the configured one.
 */
public interface QueueLayoutConfig {
    /**
     * Returns the minimum header file size, and increment size if the header file is expanded.
     *
     * @return the minimum header file size in bytes, zero for region-sized increments
     */
    long minHeaderFileSize();

    /**
     * Returns the maximum header file size, the size at which the header file is rolled if file rolling is used.
     *
     * @return the maximum header file size in bytes
     */
    long maxHeaderFileSize();

    /**
     * Returns the minimum payload file size, and increment size if payload files are expanded.
     *
     * @return the minimum payload file size in bytes, zero for region-sized increments
     */
    long minPayloadFileSize();

    /**
     * Returns the maximum payload file size, the size at which payload files are rolled if file rolling is used.
     *
     * @return the maximum payload file size in bytes
     */
    long maxPayloadFileSize();

    /**
     * Returns the header region size, the size of the header file blocks mapped into memory.
     *
     * @return the header region size in bytes
     */
    int headerRegionSize();

    /**
     * Returns the payload region size, which is also the maximum size of a queue entry including its length prefix,
     * since entries never cross a region boundary.
     *
     * @return the payload region size in bytes
     */
    int payloadRegionSize();

    /**
     * Returns true if the header file is expanded as needed, and false if it is created with maximum size.
     *
     * @return true if the header file is expanded
     */
    boolean expandHeaderFile();

    /**
     * Returns true if payload files are expanded as needed, and false if they are created with maximum size.
     *
     * @return true if payload files are expanded
     */
    boolean expandPayloadFiles();

    /**
     * Returns true if the header file is rolled when it reaches the maximum header file size.
     *
     * @return true if the header file is rolled
     */
    boolean rollHeaderFile();

    /**
     * Returns true if payload files are rolled when they reach the maximum payload file size.
     *
     * @return true if payload files are rolled
     */
    boolean rollPayloadFiles();
}
