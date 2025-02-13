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

import org.agrona.collections.Hashing;
import org.agrona.collections.Int2ObjectHashMap;
import org.tools4j.mmap.dictionary.config.DictionaryConfig;
import org.tools4j.mmap.dictionary.config.ReaderConfig;
import org.tools4j.mmap.region.api.AccessMode;
import org.tools4j.mmap.region.api.Mapping;
import org.tools4j.mmap.region.api.Mappings;
import org.tools4j.mmap.region.api.OffsetMapping;
import org.tools4j.mmap.region.config.MappingConfig;

import java.util.function.IntFunction;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.dictionary.impl.DictMappingConfigs.payloadMappingConfig;
import static org.tools4j.mmap.dictionary.impl.Headers.MAX_UPDATERS;
import static org.tools4j.mmap.dictionary.impl.SectorDescriptor.MAX_SECTORS;

/**
 * Mappings for dictionary readers. Note that some mappings still have write access so the
 * reader can help with copying of key/value headers and hashes to new sectors.
 */
interface ReaderMappings extends AutoCloseable {
    /**
     * Returns the read/write mapping with the sector index.
     * @return mapping with sector index
     */
    Mapping index();

    /**
     * Returns the read/write mapping with sector data containing keys and value headers and hashes.
     *
     * @param sector the sector, referencing an entry from the index file
     * @return mapping with sector data
     */
    OffsetMapping sector(int sector);

    /**
     * Returns the read-only mapping with payload data containing key and value payload data owned by the updater
     * specified by ID.
     *
     * @param updaterId the ID of the updater writing the given data
     * @return mapping with key and value payload data
     */
    OffsetMapping payload(int updaterId);

    boolean isClosed();

    @Override
    void close();

    /**
     * Factory method for reader mappings.
     *
     * @param dictFiles         the dictionary files
     * @param dictionaryConfig  the dictionary configuration settings
     * @param readerConfig      configuration for reader mappings
     * @return a new reader mappings instance
     */
    static ReaderMappings create(final DictFiles dictFiles,
                                 final DictionaryConfig dictionaryConfig,
                                 final ReaderConfig readerConfig) {
        requireNonNull(dictFiles);
        final DictionaryConfig queueCfg = dictionaryConfig.toImmutableDictionaryConfig();
        final ReaderConfig readerCfg = readerConfig.toImmutableReaderConfig();

        return new ReaderMappings() {
            final MappingConfig sectorCfg = DictMappingConfigs.sectorMappingConfig(queueCfg, readerCfg);
            final MappingConfig payloadCfg = payloadMappingConfig(queueCfg, readerCfg);
            final Mapping index = Mappings.fixedSizeMapping(dictFiles.indexFile(), IndexDescriptor.FILE_SIZE,
                    AccessMode.READ_WRITE);
            final Int2ObjectHashMap<OffsetMapping> sectorMappings = new Int2ObjectHashMap<>(MAX_SECTORS,
                    Hashing.DEFAULT_LOAD_FACTOR);
            final IntFunction<OffsetMapping> sectorMappingFactory = sector -> Mappings.offsetMapping(
                    dictFiles.sectorFile(sector), AccessMode.READ_WRITE, sectorCfg);

            final Int2ObjectHashMap<OffsetMapping> payloadMappings = new Int2ObjectHashMap<>(MAX_UPDATERS,
                    Hashing.DEFAULT_LOAD_FACTOR);
            final IntFunction<OffsetMapping> payloadMappingFactory = updaterId -> Mappings.offsetMapping(
                    dictFiles.sectorFile(updaterId), AccessMode.READ_ONLY, payloadCfg);

            @Override
            public Mapping index() {
                return index;
            }

            @Override
            public OffsetMapping sector(final int sector) {
                return sectorMappings.computeIfAbsent(sector, sectorMappingFactory);
            }

            @Override
            public OffsetMapping payload(final int updaterId) {
                return payloadMappings.computeIfAbsent(updaterId, payloadMappingFactory);
            }

            @Override
            public boolean isClosed() {
                return index.isClosed();
            }

            @Override
            public void close() {
                if (!isClosed()) {
                    index.close();
                    payloadMappings.forEachInt((appenderId, mapping) -> mapping.close());
                    payloadMappings.clear();
                }
            }

            @Override
            public String toString() {
                return "ReaderMappings" +
                        ":dictionary=" + dictFiles.dictionaryName() +
                        "|closed=" + isClosed();
            }
        };
    }

}
