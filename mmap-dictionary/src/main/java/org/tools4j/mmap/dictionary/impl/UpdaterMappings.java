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

import org.tools4j.mmap.dictionary.config.DictionaryConfig;
import org.tools4j.mmap.dictionary.config.UpdaterConfig;
import org.tools4j.mmap.region.api.AccessMode;
import org.tools4j.mmap.region.api.Mappings;
import org.tools4j.mmap.region.api.OffsetMapping;
import org.tools4j.mmap.region.config.MappingConfig;
import org.tools4j.mmap.region.impl.FileInitialiser;
import org.tools4j.mmap.region.impl.IdPool;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.dictionary.impl.DictMappingConfigs.headerMappingConfig;
import static org.tools4j.mmap.dictionary.impl.DictMappingConfigs.payloadMappingConfig;

/**
 * Header and payload mapping for dictionary updaters.
 */
interface UpdaterMappings extends AutoCloseable {
    /**
     * @return the appender ID
     */
    int updaterId();
    /**
     * @return header region
     */
    OffsetMapping header();

    /**
     * @return payload region
     */
    OffsetMapping payload();

    boolean isClosed();

    @Override
    void close();

    /**
     * Factory method for appender mappings.
     *
     * @param dictFiles         the dictionary files
     * @param updaterIdPool     the appender ID pool
     * @param dictionaryConfig  the dictionary configuration settings
     * @param updaterConfig     configuration for updater mappings
     * @return a new updater mappings instance
     */
    static UpdaterMappings create(final DictFiles dictFiles,
                                   final IdPool updaterIdPool,
                                   final DictionaryConfig dictionaryConfig,
                                   final UpdaterConfig updaterConfig) {
        requireNonNull(dictFiles);
        requireNonNull(dictionaryConfig);
        requireNonNull(updaterIdPool);
        final DictionaryConfig queueCfg = dictionaryConfig.toImmutableDictionaryConfig();
        final UpdaterConfig updaterCfg = updaterConfig.toImmutableUpdaterConfig();

        return new UpdaterMappings() {
            final int updaterId = updaterIdPool.acquire();
            final MappingConfig headerCfg = headerMappingConfig(queueCfg, updaterCfg);
            final MappingConfig payloadCfg = payloadMappingConfig(queueCfg, updaterCfg);
            final OffsetMapping header = Mappings.offsetMapping(dictFiles.indexFile(), AccessMode.READ_WRITE,
                    FileInitialiser.zeroBytes(AccessMode.READ_WRITE, Headers.HEADER_LENGTH), headerCfg);
            final OffsetMapping payload = Mappings.offsetMapping(dictFiles.payloadFile(updaterId),
                    AccessMode.READ_WRITE, payloadCfg);

            public int updaterId() {
                return updaterId;
            }

            @Override
            public OffsetMapping header() {
                return header;
            }

            @Override
            public OffsetMapping payload() {
                return payload;
            }

            @Override
            public boolean isClosed() {
                return header.isClosed();
            }

            @Override
            public void close() {
                if (!isClosed()) {
                    header.close();
                    payload.close();
                    updaterIdPool.release(updaterId);
                }
            }

            @Override
            public String toString() {
                return "UpdaterMappings" +
                        ":dictionary=" + dictFiles.dictionaryName() +
                        "|updaterId=" + updaterId +
                        "|closed=" + isClosed();
            }
        };
    }

}
