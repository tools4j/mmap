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

import org.junit.jupiter.api.Test;
import org.tools4j.mmap.mapping.config.MappingStrategyConfig;
import org.tools4j.mmap.queue.config.IndexReaderConfig;
import org.tools4j.mmap.queue.config.IndexReaderConfigurator;
import org.tools4j.mmap.queue.config.MappingStrategy;
import org.tools4j.mmap.queue.config.QueueConfigurations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.tools4j.mmap.queue.impl.AppenderConfiguratorTest.STRATEGY_A;
import static org.tools4j.mmap.queue.impl.AppenderConfiguratorTest.STRATEGY_B;
import static org.tools4j.mmap.queue.impl.AppenderConfiguratorTest.assertMappingStrategy;
import static org.tools4j.mmap.queue.impl.IndexReaderConfigDefaults.INDEX_READER_CONFIG_DEFAULTS;

/**
 * Unit test for {@link IndexReaderConfiguratorImpl}, {@link IndexReaderConfigImpl} and
 * {@link IndexReaderConfigDefaults}.
 */
class IndexReaderConfiguratorTest {

    private static final IndexReaderConfig SYSTEM_DEFAULTS = new IndexReaderConfigImpl(
            QueueConfigurations.defaultIndexReaderHeaderMappingStrategy(),
            QueueConfigurations.defaultMaxOpenIndexReaderHeaderFiles());

    /** Defaults returning the "unset" sentinel for every value, forcing fallback to system defaults */
    private static IndexReaderConfig unsetDefaults() {
        return new IndexReaderConfig() {
            @Override
            public MappingStrategyConfig headerMappingStrategy() {
                return null;
            }

            @Override
            public int maxOpenHeaderFiles() {
                return 0;
            }

            @Override
            public IndexReaderConfig toImmutableIndexReaderConfig() {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static IndexReaderConfig customDefaults() {
        return new IndexReaderConfigImpl(STRATEGY_A, 5);
    }

    @Test
    void unsetValuesResolveToSystemDefaults() {
        assertEquals(SYSTEM_DEFAULTS, IndexReaderConfigurator.configure(unsetDefaults()).toImmutableIndexReaderConfig());
        assertEquals(SYSTEM_DEFAULTS, IndexReaderConfig.configure().toImmutableIndexReaderConfig());
        assertEquals(SYSTEM_DEFAULTS, INDEX_READER_CONFIG_DEFAULTS.toImmutableIndexReaderConfig());
        assertEquals(SYSTEM_DEFAULTS, new IndexReaderConfigImpl());
        assertSame(INDEX_READER_CONFIG_DEFAULTS, IndexReaderConfig.getDefault());
        assertInstanceOf(IndexReaderConfiguratorImpl.class, IndexReaderConfig.configure());
    }

    @Test
    void valuesAreResolvedFromDefaults() {
        assertEquals(customDefaults(),
                IndexReaderConfigurator.configure(customDefaults()).toImmutableIndexReaderConfig());
    }

    @Test
    void explicitValuesOverrideDefaultsUntilReset() {
        final IndexReaderConfigurator config = IndexReaderConfigurator.configure(customDefaults())
                .headerMappingStrategy(STRATEGY_B)
                .maxOpenHeaderFiles(7);
        assertEquals(new IndexReaderConfigImpl(STRATEGY_B, 7), config.toImmutableIndexReaderConfig());
        config.maxOpenHeaderFiles(0);
        assertEquals(5, config.maxOpenHeaderFiles());
        config.reset();
        assertEquals(customDefaults(), config.toImmutableIndexReaderConfig());
    }

    @Test
    void mappingStrategy() {
        final IndexReaderConfigurator config = IndexReaderConfigurator.configure(customDefaults())
                .headerMappingStrategy(MappingStrategy.SyncMappingStrategy);
        assertSame(MappingStrategyConfig.getDefaultSync(), config.headerMappingStrategy());
        config.reset().headerMappingStrategy(cfg -> cfg.lruCacheSize(16));
        assertMappingStrategy(STRATEGY_A, 16, config.headerMappingStrategy());
    }

    @Test
    void immutableConfigIsSnapshot() {
        final IndexReaderConfigurator config = IndexReaderConfigurator.configure(customDefaults());
        final IndexReaderConfig immutable = config.toImmutableIndexReaderConfig();
        config.headerMappingStrategy(STRATEGY_B).maxOpenHeaderFiles(9);
        assertEquals(customDefaults(), immutable);
        assertSame(immutable, immutable.toImmutableIndexReaderConfig());
        assertThrows(NullPointerException.class, () -> new IndexReaderConfigImpl(null, 1));
    }
}
