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
package org.tools4j.mmap.mapping.impl;

import org.junit.jupiter.api.Test;
import org.tools4j.mmap.mapping.config.MappingConfig;
import org.tools4j.mmap.mapping.config.MappingConfigurations;
import org.tools4j.mmap.mapping.config.MappingConfigurator;
import org.tools4j.mmap.mapping.config.MappingStrategyConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tools4j.mmap.mapping.impl.Constants.REGION_SIZE_GRANULARITY;
import static org.tools4j.mmap.mapping.impl.MappingStrategyConfigDefaults.MAPPING_STRATEGY_CONFIG_DEFAULTS;

/**
 * Unit test for {@link MappingConfiguratorImpl} value resolution: own value, then defaults, then system defaults.
 */
class MappingConfiguratorImplTest {

    private static final long FILE_SIZE = 4 * REGION_SIZE_GRANULARITY;
    private static final int REGION_SIZE = 2 * (int) REGION_SIZE_GRANULARITY;

    /** Defaults returning the "unset" sentinel for every value, forcing fallback to system defaults */
    private static final MappingConfig UNSET_DEFAULTS = new MappingConfig() {
        @Override
        public long minFileSize() {
            return -1;
        }

        @Override
        public long maxFileSize() {
            return 0;
        }

        @Override
        public int regionSize() {
            return 0;
        }

        @Override
        public boolean expandFile() {
            return false;
        }

        @Override
        public boolean rollFiles() {
            return false;
        }

        @Override
        public int maxOpenFiles() {
            return -1;
        }

        @Override
        public int filesToCreateAhead() {
            return -1;
        }

        @Override
        public MappingStrategyConfig mappingStrategy() {
            return null;
        }

        @Override
        public MappingConfig toImmutableConfig() {
            return this;
        }

        @Override
        public String toString() {
            return "UNSET_DEFAULTS";
        }
    };

    @Test
    void unsetDefaultsFallBackToSystemDefaults() {
        final MappingConfigurator config = MappingConfigurator.configure(UNSET_DEFAULTS);
        assertEquals(MappingConfigurations.defaultMinFileSize(), config.minFileSize());
        assertEquals(MappingConfigurations.defaultMaxFileSize(), config.maxFileSize());
        assertEquals(MappingConfigurations.defaultRegionSize(), config.regionSize());
        assertEquals(MappingConfigurations.defaultMaxOpenFiles(), config.maxOpenFiles());
        assertEquals(MappingConfigurations.defaultFilesToCreateAhead(), config.filesToCreateAhead());
        assertSame(MAPPING_STRATEGY_CONFIG_DEFAULTS, config.mappingStrategy());
    }

    @Test
    void valuesAreResolvedFromDefaults() {
        final MappingConfig defaults = MappingConfigurator.configure()
                .minFileSize(FILE_SIZE).maxFileSize(2 * FILE_SIZE).regionSize(REGION_SIZE).expandFile(true)
                .rollFiles(false).maxOpenFiles(7).filesToCreateAhead(2)
                .mappingStrategy(MappingStrategyConfig.getDefaultSync());
        final MappingConfigurator config = MappingConfigurator.configure(defaults);
        assertEquals(FILE_SIZE, config.minFileSize());
        assertEquals(2 * FILE_SIZE, config.maxFileSize());
        assertEquals(REGION_SIZE, config.regionSize());
        assertTrue(config.expandFile());
        assertFalse(config.rollFiles());
        assertEquals(7, config.maxOpenFiles());
        assertEquals(2, config.filesToCreateAhead());
        assertSame(MappingStrategyConfig.getDefaultSync(), config.mappingStrategy());
    }

    @Test
    void explicitValuesOverrideDefaults() {
        final MappingConfig defaults = MappingConfigurator.configure()
                .minFileSize(FILE_SIZE).regionSize(REGION_SIZE).rollFiles(true).maxOpenFiles(7).filesToCreateAhead(2);
        final MappingConfigurator config = MappingConfigurator.configure(defaults)
                .minFileSize(0).regionSize(2 * REGION_SIZE).rollFiles(false).maxOpenFiles(1).filesToCreateAhead(0);
        assertEquals(0, config.minFileSize());
        assertEquals(2 * REGION_SIZE, config.regionSize());
        assertFalse(config.rollFiles());
        assertEquals(1, config.maxOpenFiles());
        assertEquals(0, config.filesToCreateAhead());
    }

    @Test
    void invalidValuesAreRejected() {
        final MappingConfigurator config = MappingConfigurator.configure();
        assertThrows(IllegalArgumentException.class, () -> config.minFileSize(3));
        assertThrows(IllegalArgumentException.class, () -> config.maxFileSize(0));
        assertThrows(IllegalArgumentException.class, () -> config.regionSize(REGION_SIZE + 1));
        assertThrows(IllegalArgumentException.class, () -> config.maxOpenFiles(0));
        assertThrows(IllegalArgumentException.class, () -> config.filesToCreateAhead(-1));
        assertThrows(NullPointerException.class, () -> config.mappingStrategy((MappingStrategyConfig) null));
    }

    @Test
    void resetRestoresDefaults() {
        final MappingConfig defaults = MappingConfigurator.configure().maxOpenFiles(7).filesToCreateAhead(2);
        final MappingConfigurator config = MappingConfigurator.configure(defaults)
                .maxOpenFiles(1).filesToCreateAhead(0).rollFiles(!defaults.rollFiles());
        config.reset();
        assertEquals(7, config.maxOpenFiles());
        assertEquals(2, config.filesToCreateAhead());
        assertEquals(defaults.rollFiles(), config.rollFiles());
    }

    @Test
    void toStringShowsUnresolvedValuesAndDefaults() {
        final MappingConfigurator config = MappingConfigurator.configure(UNSET_DEFAULTS);
        assertEquals("MappingConfiguratorImpl:minFileSize=-1|maxFileSize=0|regionSize=0|expandFile=null" +
                "|rollFiles=null|maxOpenFiles=-1|filesToCreateAhead=-1|mappingStrategy=null|defaults=UNSET_DEFAULTS",
                config.toString());
        config.maxOpenFiles(5);
        config.rollFiles();
        assertEquals("MappingConfiguratorImpl:minFileSize=-1|maxFileSize=0|regionSize=0|expandFile=null" +
                "|rollFiles=false|maxOpenFiles=5|filesToCreateAhead=-1|mappingStrategy=null|defaults=UNSET_DEFAULTS",
                config.toString());
    }
}
