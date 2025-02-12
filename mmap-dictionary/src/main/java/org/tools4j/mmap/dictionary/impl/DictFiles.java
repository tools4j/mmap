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
package org.tools4j.mmap.dictionary.impl;

import java.io.File;
import java.util.function.IntFunction;

import static java.util.Objects.requireNonNull;

/**
 * Files used for a dictionary.
 */
final class DictFiles {
    public static final String FILE_ENDING = ".mmd";
    private static final int MAX_SECTORS = 64;

    private final File dictionaryFile;
    private final File indexFile;
    private final File idPoolFile;
    private final File[] sectorFiles;
    private final IntFunction<File> sectorFileFactory;
    private final File[] payloadFiles;
    private final IntFunction<File> payloadFileFactory;

    public DictFiles(final File dictionaryFile, final int maxUpdaters) {
        this.dictionaryFile = requireNonNull(dictionaryFile);
        this.indexFile = new File(dictionaryFile, dictionaryFile.getName() + "_idx.mmd");
        this.idPoolFile = new File(dictionaryFile, dictionaryFile.getName() + "_ids.mmd");
        this.sectorFiles = new File[MAX_SECTORS];
        this.sectorFileFactory = sector -> new File(dictionaryFile, dictionaryFile.getName() + "_sec_" + sector + ".mmd");
        this.payloadFiles = new File[maxUpdaters];
        this.payloadFileFactory = updaterId -> new File(dictionaryFile, dictionaryFile.getName() + "_dat_" + updaterId + ".mmd");
    }

    public String dictionaryName() {
        return dictionaryFile.getName();
    }

    public File indexFile() {
        return indexFile;
    }

    public File idPoolFile() {
        return idPoolFile;
    }

    public File sectorFile(final int sector) {
        File sectorFile = sectorFiles[sector];
        if (sectorFile == null) {
            sectorFile = sectorFileFactory.apply(sector);
            sectorFiles[sector] = sectorFile;
        }
        return sectorFile;
    }

    public File payloadFile(final int appenderId) {
        File payloadFile = payloadFiles[appenderId];
        if (payloadFile == null) {
            payloadFile = payloadFileFactory.apply(appenderId);
            payloadFiles[appenderId] = payloadFile;
        }
        return payloadFile;
    }

    public File[] listFiles() {
        return dictionaryFile.listFiles((dir, name) -> name != null && name.equals(FILE_ENDING));
    }

    @Override
    public String toString() {
        return "DictFiles:dictionary=" + dictionaryName();
    }
}
