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
package org.tools4j.mmap.queue.impl;

import org.tools4j.mmap.mapping.impl.RollingFiles;

import java.io.File;
import java.util.function.IntFunction;

import static java.util.Objects.requireNonNull;

/**
 * Files used for a queue.
 */
final class QueueFiles {
    public static final String FILE_ENDING = ".mmq";

    private final File queueFile;
    private final File metaFile;
    private final File headerFile;
    private final File idPoolFile;
    //NOTE: lazily filled by plain writes from appender and reader threads; the race is benign: File is immutable with
    //      final fields, so a racily published instance is fully constructed, and racing threads at worst create
    //      equal duplicates
    private final File[] payloadFiles;
    private final File[] firstRollingPayloadFiles;
    private final IntFunction<File> payloadFileFactory;
    private final IntFunction<File> firstRollingPayloadFileFactory;

    public QueueFiles(final File queueFile) {
        this.queueFile = requireNonNull(queueFile);
        this.metaFile = new File(queueFile, queueFile.getName() + "_met.mmq");
        this.headerFile = new File(queueFile, queueFile.getName() + "_hdr.mmq");
        this.idPoolFile = new File(queueFile, queueFile.getName() + "_ids.mmq");
        this.payloadFiles = new File[Headers.MAX_APPENDERS];
        this.firstRollingPayloadFiles = new File[Headers.MAX_APPENDERS];
        this.payloadFileFactory = appenderId -> new File(queueFile, queueFile.getName() + "_dat_" + appenderId + ".mmq");
        this.firstRollingPayloadFileFactory = appenderId -> RollingFiles.fileForIndex(payloadFile(appenderId), 0);
    }

    public String queueName() {
        return queueFile.getName();
    }

    public File metaFile() {
        return metaFile;
    }

    public File headerFile() {
        return headerFile;
    }

    public File idPoolFile() {
        return idPoolFile;
    }

    public File payloadFile(final int appenderId) {
        return cached(payloadFiles, appenderId, payloadFileFactory);
    }

    /**
     * Returns the first payload file of the appender ID, which exists if the appender ID has ever mapped its payload:
     * the payload file itself, or the first of its rolling files. Cached like the payload file, so that checking its
     * existence creates no garbage.
     *
     * @param appenderId the appender ID
     * @param rollFiles  true if payload files are rolled
     * @return the payload file or the first rolling payload file
     */
    public File firstPayloadFile(final int appenderId, final boolean rollFiles) {
        return rollFiles ? cached(firstRollingPayloadFiles, appenderId, firstRollingPayloadFileFactory) :
                payloadFile(appenderId);
    }

    /** Returns the cached file, creating and caching it first if necessary (benign race, see note on the caches) */
    private static File cached(final File[] cache, final int appenderId, final IntFunction<File> factory) {
        File file = cache[appenderId];
        if (file == null) {
            file = factory.apply(appenderId);
            cache[appenderId] = file;
        }
        return file;
    }

    public File[] listFiles() {
        return queueFile.listFiles((dir, name) -> name != null && name.endsWith(FILE_ENDING));
    }

    @Override
    public String toString() {
        return "QueueFiles:queue=" + queueName();
    }
}
