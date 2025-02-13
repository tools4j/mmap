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
package org.tools4j.mmap.dictionary.api;

import org.tools4j.mmap.dictionary.config.DictionaryConfig;
import org.tools4j.mmap.dictionary.config.ReaderConfig;
import org.tools4j.mmap.dictionary.config.UpdaterConfig;
import org.tools4j.mmap.dictionary.impl.DictionaryImpl;

import java.io.File;

public interface Dictionary extends AutoCloseable {
    Updater createUpdater();
    Updater createUpdater(UpdaterConfig config);

    Lookup createLookup();
    Lookup createLookup(ReaderConfig config);

    DictionaryIterator createIterator();
    DictionaryIterator createIterator(ReaderConfig config);

    boolean isClosed();

    /**
     * Closes the dictionary and all updaters, lookups and iterators created via this dictionary.
     */
    @Override
    void close();

    static Dictionary create(final File file) {
        return new DictionaryImpl(file);
    }

    static Dictionary create(final File file, final DictionaryConfig config) {
        return new DictionaryImpl(file, config);
    }
}
