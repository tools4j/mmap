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
import org.junit.jupiter.api.io.TempDir;
import org.tools4j.mmap.mapping.api.AccessMode;
import org.tools4j.mmap.mapping.api.FileInitialiser;
import org.tools4j.mmap.mapping.api.FixedMapping;
import org.tools4j.mmap.mapping.api.Mappings;
import org.tools4j.mmap.mapping.api.OpenMode;
import org.tools4j.mmap.mapping.unsafe.FileMapper;
import org.tools4j.mmap.mapping.unsafe.FixedSizeFileMapper;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.tools4j.mmap.mapping.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.mapping.impl.Constants.REGION_SIZE_GRANULARITY;

/**
 * Unit test for {@link FixedMappingImpl}: construction, creation failures with read-only files, mapping at offsets
 * that are not aligned to {@link Constants#REGION_SIZE_GRANULARITY}, and closing.
 */
class FixedMappingImplTest {

    @TempDir
    Path tempDir;

    @Test
    void readOnlyFileTooShortThrowsIllegalArgumentException() throws IOException {
        final File file = Files.write(tempDir.resolve("short.dat"), new byte[4]).toFile();
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Mappings.fixedMapping(file, OpenMode.READ_ONLY, 0, 8));
        assertTrue(e.getMessage().contains("expected file of size at least 8"), e.getMessage());
    }

    @Test
    void readOnlyFileEmptyThrowsIllegalStateException() throws IOException {
        final File file = Files.write(tempDir.resolve("empty.dat"), new byte[0]).toFile();
        assertThrows(IllegalStateException.class, () -> Mappings.fixedMapping(file, OpenMode.READ_ONLY, 0, 8));
    }

    @Test
    void readOnlyFileMissingThrowsIllegalArgumentException() {
        final File file = tempDir.resolve("missing.dat").toFile();
        assertThrows(IllegalArgumentException.class, () -> Mappings.fixedMapping(file, OpenMode.READ_ONLY, 0, 8));
    }

    @Test
    void readOnlyLazyFileMissingThrowsIllegalStateExceptionAsFixedMappingIsMappedImmediately() {
        final File file = tempDir.resolve("missing.dat").toFile();
        assertThrows(IllegalStateException.class, () -> Mappings.fixedMapping(file, OpenMode.READ_ONLY_LAZY, 0, 8));
    }

    @Test
    void readOnlyFileLongEnoughIsMapped() throws IOException {
        final File file = Files.write(tempDir.resolve("ok.dat"), new byte[]{1, 2, 3, 4, 5, 6, 7, 8}).toFile();
        try (final FixedMapping mapping = Mappings.fixedMapping(file, OpenMode.READ_ONLY, 0, 8)) {
            assertEquals(8, mapping.bytesAvailable());
            assertEquals(8, mapping.buffer().getByte(7));
        }
    }

    @Test
    void readOnlyAtUnalignedOffsets() throws IOException {
        final int granularity = (int) REGION_SIZE_GRANULARITY;
        final byte[] bytes = new byte[2 * granularity + 16];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i * 31);
        }
        final File file = Files.write(tempDir.resolve("unaligned.dat"), bytes).toFile();
        for (final int offset : new int[]{1, 7, granularity - 1, granularity + 3, 2 * granularity + 15}) {
            try (final FixedMapping mapping = Mappings.fixedMapping(file, OpenMode.READ_ONLY, offset, 1)) {
                assertEquals(offset, mapping.position(), "offset=" + offset);
                assertEquals(1, mapping.bytesAvailable(), "offset=" + offset);
                assertEquals(bytes[offset], mapping.buffer().getByte(0), "offset=" + offset);
            }
        }
    }

    @Test
    void readWriteAtUnalignedOffsetWritesToFilePosition() throws IOException {
        final int offset = (int) REGION_SIZE_GRANULARITY + 5;
        final File file = tempDir.resolve("unaligned.map").toFile();
        try (final FixedMapping mapping = Mappings.fixedMapping(file, OpenMode.OPEN_OR_CREATE, offset, 8)) {
            assertEquals(8, mapping.bytesAvailable());
            mapping.buffer().putLong(0, 0x0102030405060708L);
        }
        final byte[] bytes = Files.readAllBytes(file.toPath());
        assertEquals(offset + 8, bytes.length);
        final long value = ByteBuffer.wrap(bytes, offset, 8).order(ByteOrder.nativeOrder()).getLong();
        assertEquals(0x0102030405060708L, value);
        assertEquals(0, bytes[offset - 1]);
    }
    @Test
    void constructorsMapWholeFileFromOffsetOrGivenLength() {
        final File file = tempDir.resolve("ctor.map").toFile();
        try (final FixedMapping mapping = new FixedMappingImpl(fixedSizeFileMapper(file, 64))) {
            assertEquals(0, mapping.position());
            assertEquals(64, mapping.bytesAvailable());
            assertEquals(AccessMode.READ_WRITE, mapping.accessMode());
            mapping.buffer().putLong(8, 42);
        }
        try (final FixedMapping mapping = new FixedMappingImpl(fixedSizeFileMapper(file, 64), 8)) {
            assertEquals(8, mapping.position());
            assertEquals(56, mapping.bytesAvailable());
            assertEquals(42, mapping.buffer().getLong(0));
        }
        try (final FixedMapping mapping = new FixedMappingImpl(fixedSizeFileMapper(file, 64), 0, 16)) {
            assertEquals(16, mapping.bytesAvailable());
            assertEquals(42, mapping.buffer().getLong(8));
        }
        try (final FixedMapping mapping = new FixedMappingImpl(fixedSizeFileMapper(file, 64), 8, 16)) {
            assertEquals(8, mapping.position());
            assertEquals(16, mapping.bytesAvailable());
            assertEquals(42, mapping.buffer().getLong(0));
        }
        assertThrows(IllegalArgumentException.class, () -> new FixedMappingImpl(fixedSizeFileMapper(file, 64), 65));
    }

    @Test
    void closeUnmapsOnceAndClosesFileMapperOnlyIfRequested() {
        final File file = tempDir.resolve("close.map").toFile();
        final FixedSizeFileMapper fileMapper = fixedSizeFileMapper(file, 64);
        final FixedMapping mapping = new FixedMappingImpl(fileMapper, 0, 64, false);
        assertTrue(mapping.isMapped());
        assertFalse(mapping.isClosed());

        mapping.close();
        assertFalse(mapping.isMapped());
        assertTrue(mapping.isClosed());
        assertEquals(0, mapping.bytesAvailable());
        assertFalse(fileMapper.isClosed());
        mapping.close();

        try (final FixedMapping owning = new FixedMappingImpl(fileMapper, 0, 64, true)) {
            assertTrue(owning.isMapped());
        }
        assertTrue(fileMapper.isClosed());
    }

    @Test
    void failedMappingThrows() {
        final FileMapper fileMapper = mock(FileMapper.class);
        when(fileMapper.map(anyLong(), anyInt())).thenReturn(NULL_ADDRESS);
        assertThrows(IllegalArgumentException.class, () -> new FixedMappingImpl(fileMapper, 0, 64, false));
    }

    @Test
    void lengthExceedingIntegerRangeWhenAlignedThrows() {
        final FileMapper fileMapper = mock(FileMapper.class);
        assertThrows(IllegalArgumentException.class,
                () -> new FixedMappingImpl(fileMapper, 1, Integer.MAX_VALUE, false));
        assertThrows(IllegalArgumentException.class, () -> new FixedMappingImpl(fileMapper, -1, 8, false));
        assertThrows(IllegalArgumentException.class, () -> new FixedMappingImpl(fileMapper, 0, -1, false));
        verifyNoInteractions(fileMapper);
    }

    private static FixedSizeFileMapper fixedSizeFileMapper(final File file, final int size) {
        return new FixedSizeFileMapper(file, size, AccessMode.READ_WRITE, FileInitialiser.minLength(size));
    }
}
