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

import org.agrona.CloseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.dictionary.api.Dictionary;
import org.tools4j.mmap.dictionary.api.DictionaryIterator;
import org.tools4j.mmap.dictionary.api.Lookup;
import org.tools4j.mmap.dictionary.api.Updater;
import org.tools4j.mmap.dictionary.config.DictionaryConfig;
import org.tools4j.mmap.dictionary.config.ReaderConfig;
import org.tools4j.mmap.dictionary.config.UpdaterConfig;
import org.tools4j.mmap.region.api.AccessMode;
import org.tools4j.mmap.region.config.MappingStrategy;
import org.tools4j.mmap.region.impl.IdPool;
import org.tools4j.mmap.region.impl.IdPool256;
import org.tools4j.mmap.region.impl.IdPool64;

import java.io.File;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Implementation of {@link Dictionary} that allows a multiple writing threads and
 * multiple reading threads, when each thread creates their own instances of
 * {@link #createUpdater() updater}, {@link #createLookup() lookup} and {@link #createIterator() iterator}.
 */
public final class DictionaryImpl implements Dictionary {
    private static final Logger LOGGER = LoggerFactory.getLogger(DictionaryImpl.class);

    private final DictFiles files;
    private final DictionaryConfig config;
    private final Function<ReaderConfig, Lookup> lookupFactory;
    private final Function<ReaderConfig, DictionaryIterator> iteratorFactory;
    private final Function<UpdaterConfig, Updater> updaterFoactory;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Queue<AutoCloseable> closeables = new ConcurrentLinkedQueue<>();

    public DictionaryImpl(final File file) {
//        this(file, DictionaryConfig.getDefault());
        this(file, null);
    }

    public DictionaryImpl(final File file, final DictionaryConfig dictionaryConfig) {
        final AccessMode accessMode = dictionaryConfig.accessMode();
        final int maxUpdaters = dictionaryConfig.maxUpdaters();
        this.files = new DictFiles(file, maxUpdaters);
        this.config = dictionaryConfig.toImmutableDictionaryConfig();
        if (accessMode == AccessMode.READ_WRITE_CLEAR) {
            deleteDictionaryFiles();
        }
        if (!file.exists() && accessMode != AccessMode.READ_ONLY) {
            createDictionaryDir(file);
        }

        final IdPool idPool = open(updaterIdPool(files, maxUpdaters));
        this.lookupFactory = readerConfig -> null;
        this.iteratorFactory = readerConfig -> null;
        this.updaterFoactory = updaterConfig -> null;
//        this.lookupFactory = lookupConfig -> open(new LookupImpl(
//                dictionaryNameIfNotClosed(),
//                ReaderMappings.create(files, config, lookupConfig)
//        ));
//        this.iteratorFactory = iteratorConfig -> open(new DictionaryIteratorImpl(
//                dictionaryNameIfNotClosed(),
//                ReaderMappings.create(files, config, iteratorConfig)
//        ));
//        this.updaterFoactory = accessMode == AccessMode.READ_ONLY ?
//                updaterConfig -> {throw new IllegalStateException(
//                        "Cannot open updater in read-only mode for dictionary " + dictionaryNameIfNotClosed());
//                } :
//                updaterConfig -> open(new UpdaterImpl(
//                        dictionaryNameIfNotClosed(),
//                        UpdaterMappings.create(files, idPool, config, updaterConfig),
//                        enableCopyFromPreviousRegion(updaterConfig)
//                ));
    }

    private void deleteDictionaryFiles() {
        for (final File file : files.listFiles()) {
            final boolean deleted = file.delete();
            assert deleted : files.dictionaryName();
        }
    }

    private void createDictionaryDir(final File file) {
        if (!file.mkdir()) {
            throw new IllegalArgumentException("Parent directory does not exist: " + file);
        }
    }

    private static boolean enableCopyFromPreviousRegion(final UpdaterConfig updaterConfig) {
        final MappingStrategy mappingStrategy = updaterConfig.ownPayloadMappingStrategy();
        final int cacheSie = mappingStrategy.cacheSize();
        final int mapAhead = mappingStrategy.asyncOptions().isPresent() ?
                mappingStrategy.asyncOptions().get().regionsToMapAhead() : 0;
        return cacheSie > Math.max(1, mapAhead + 1);
    }

    private static IdPool updaterIdPool(final DictFiles dictFiles, final int maxUpdaters) {
        if (maxUpdaters <= IdPool64.MAX_IDS) {
            return new IdPool64(dictFiles.idPoolFile());
        }
        if (maxUpdaters <= IdPool256.MAX_IDS) {
            return new IdPool256(dictFiles.idPoolFile());
        }
        throw new IllegalArgumentException("Invalid value for max updaters: " + maxUpdaters);
    }

    private <T extends AutoCloseable> T open(final T closeable) {
        closeables.add(closeable);
        return closeable;
    }

    @Override
    public Updater createUpdater() {
        return createUpdater(config.updaterConfig());
    }

    @Override
    public Updater createUpdater(final UpdaterConfig config) {
        return updaterFoactory.apply(config);
    }

    @Override
    public Lookup createLookup() {
        return createLookup(config.lookupConfig());
    }

    @Override
    public Lookup createLookup(final ReaderConfig config) {
        return lookupFactory.apply(config);
    }

    @Override
    public DictionaryIterator createIterator() {
        return createIterator(config.iteratorConfig());
    }

    @Override
    public DictionaryIterator createIterator(final ReaderConfig config) {
        return iteratorFactory.apply(config);
    }

    private String dictionaryNameIfNotClosed() {
        if (isClosed()) {
            return files.dictionaryName();
        }
        throw new IllegalStateException("Dictionary is closed: " + files.dictionaryName());
    }


    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            CloseHelper.quietCloseAll(closeables);
            closeables.clear();
            LOGGER.info("Closed dictionary: {}", files.dictionaryName());
        }
    }

    @Override
    public String toString() {
        return "DictionaryImpl" +
                ":queue=" + files.dictionaryName() +
                "|closed=" + isClosed();
    }
}
