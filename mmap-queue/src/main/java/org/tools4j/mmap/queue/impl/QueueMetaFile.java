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

import org.tools4j.mmap.mapping.api.OpenMode;
import org.tools4j.mmap.mapping.impl.FileLocks;
import org.tools4j.mmap.queue.config.QueueLayoutConfig;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Supplier;

/**
 * Reads and writes the queue meta file, which stores the {@link QueueLayoutConfig queue layout}. The file is
 * binary, little-endian, and starts with a magic number and a format version; version 1 has the following layout:
 * <pre>
 *     offset  size  field
 *          0     8  magic, the ASCII bytes "t4jmmapq"
 *          8     4  format version
 *         12     4  reserved, zero
 *         16     4  max appenders
 *         20     4  header region size
 *         24     4  payload region size
 *         28     4  flags: bit 0 expand header file, bit 1 expand payload files, bit 2 roll header file,
 *                   bit 3 roll payload files
 *         32     8  min header file size
 *         40     8  max header file size
 *         48     8  min payload file size
 *         56     8  max payload file size
 * </pre>
 * The format version also covers the layout of the other queue files, such as the header encoding and the
 * index-to-header-position bijection defined in {@link Headers}.
 * <p>
 * The meta file is written under an exclusive file lock, and read under a shared file lock. The magic number is
 * written last, so that a meta file without magic number is incomplete, for instance because its creator crashed, and
 * is treated as if the queue did not exist.
 */
enum QueueMetaFile {
    ;
    static final int VERSION = 1;
    static final int LENGTH = 64;

    private static final byte[] MAGIC = "t4jmmapq".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] NO_MAGIC = new byte[MAGIC.length];
    private static final int VERSION_OFFSET = 8;
    private static final int MAX_APPENDERS_OFFSET = 16;
    private static final int HEADER_REGION_SIZE_OFFSET = 20;
    private static final int PAYLOAD_REGION_SIZE_OFFSET = 24;
    private static final int FLAGS_OFFSET = 28;
    private static final int MIN_HEADER_FILE_SIZE_OFFSET = 32;
    private static final int MAX_HEADER_FILE_SIZE_OFFSET = 40;
    private static final int MIN_PAYLOAD_FILE_SIZE_OFFSET = 48;
    private static final int MAX_PAYLOAD_FILE_SIZE_OFFSET = 56;
    private static final int EXPAND_HEADER_FILE_FLAG = 1;
    private static final int EXPAND_PAYLOAD_FILES_FLAG = 1 << 1;
    private static final int ROLL_HEADER_FILE_FLAG = 1 << 2;
    private static final int ROLL_PAYLOAD_FILES_FLAG = 1 << 3;

    /**
     * Reads the queue layout from the given meta file, if it exists and is complete.
     *
     * @param file the meta file
     * @return the queue layout, or null if the file does not exist or is incomplete
     * @throws IllegalStateException if the file is not a queue meta file, or has an unsupported format version
     */
    static QueueLayoutConfig read(final File file) {
        if (!file.isFile()) {
            return null;
        }
        try (final RandomAccessFile raf = new RandomAccessFile(file, "r");
             final FileChannel channel = raf.getChannel();
             final FileLock ignored = FileLocks.acquireSharedLock(channel)) {
            return read(file, channel);
        } catch (final IOException e) {
            throw new IllegalStateException("Reading queue meta file " + file + " failed: " + e, e);
        }
    }

    /**
     * Opens the meta file for writing as defined by the open mode, and returns the existing or newly written queue
     * layout.
     *
     * @param file                    the meta file
     * @param openMode                the open mode, one with read/write access
     * @param layoutToCreate          supplies the queue layout to write if the queue is created
     * @param deleteOtherQueueFiles   deletes all other queue files, invoked under the meta file lock if the queue is
     *                                recreated
     * @return the existing queue layout, or the supplied one if the queue was created
     * @throws IllegalArgumentException if the queue cannot be opened as defined by the open mode
     * @throws IllegalStateException if the file is not a queue meta file, or has an unsupported format version
     */
    static QueueLayoutConfig open(final File file,
                                  final OpenMode openMode,
                                  final Supplier<QueueLayoutConfig> layoutToCreate,
                                  final Runnable deleteOtherQueueFiles) {
        if (openMode == OpenMode.READ_WRITE && !file.isFile()) {
            throw new IllegalArgumentException("Queue does not exist: " + file.getParentFile());
        }
        try (final RandomAccessFile raf = new RandomAccessFile(file, "rw");
             final FileChannel channel = raf.getChannel();
             final FileLock ignored = FileLocks.acquireLock(channel)) {
            final QueueLayoutConfig existing = read(file, channel);
            switch (openMode) {
                case READ_WRITE -> {
                    if (existing == null) {
                        throw new IllegalArgumentException("Queue does not exist: " + file.getParentFile());
                    }
                    return existing;
                }
                case CREATE -> {
                    if (existing != null) {
                        throw new IllegalArgumentException("Queue already exists: " + file.getParentFile());
                    }
                }
                case OPEN_OR_CREATE -> {
                    if (existing != null) {
                        return existing;
                    }
                }
                case RECREATE -> {
                    channel.truncate(0);
                    deleteOtherQueueFiles.run();
                }
                default -> throw new IllegalArgumentException("Unsupported open mode for writing: " + openMode);
            }
            final QueueLayoutConfig layout = layoutToCreate.get();
            write(channel, layout);
            return layout;
        } catch (final IOException e) {
            throw new IllegalStateException("Opening queue meta file " + file + " failed: " + e, e);
        }
    }

    private static QueueLayoutConfig read(final File file, final FileChannel channel) throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(LENGTH).order(ByteOrder.LITTLE_ENDIAN);
        while (buffer.hasRemaining() && channel.read(buffer, buffer.position()) >= 0) {
            //read until buffer is full or end of file is reached
        }
        final byte[] magic = new byte[MAGIC.length];
        buffer.get(0, magic);//NOTE: bytes beyond the end of the file remain zero
        if (Arrays.equals(NO_MAGIC, magic)) {
            return null;
        }
        if (!Arrays.equals(MAGIC, magic)) {
            throw new IllegalStateException("Not a queue meta file: " + file);
        }
        if (buffer.position() < VERSION_OFFSET + Integer.BYTES) {
            throw invalidLength(file, buffer.position());
        }
        final int version = buffer.getInt(VERSION_OFFSET);
        if (version != VERSION) {
            throw new IllegalStateException("Unsupported queue format version " + version + ", expected " +
                    VERSION + ": " + file);
        }
        if (buffer.position() < LENGTH) {
            throw invalidLength(file, buffer.position());
        }
        final int flags = buffer.getInt(FLAGS_OFFSET);
        return new QueueLayoutConfigImpl(
                buffer.getInt(MAX_APPENDERS_OFFSET),
                buffer.getInt(HEADER_REGION_SIZE_OFFSET),
                buffer.getInt(PAYLOAD_REGION_SIZE_OFFSET),
                buffer.getLong(MIN_HEADER_FILE_SIZE_OFFSET),
                buffer.getLong(MAX_HEADER_FILE_SIZE_OFFSET),
                buffer.getLong(MIN_PAYLOAD_FILE_SIZE_OFFSET),
                buffer.getLong(MAX_PAYLOAD_FILE_SIZE_OFFSET),
                (flags & EXPAND_HEADER_FILE_FLAG) != 0,
                (flags & EXPAND_PAYLOAD_FILES_FLAG) != 0,
                (flags & ROLL_HEADER_FILE_FLAG) != 0,
                (flags & ROLL_PAYLOAD_FILES_FLAG) != 0);
    }

    private static IllegalStateException invalidLength(final File file, final int length) {
        return new IllegalStateException("Invalid queue meta file of length " + length + ", expected " + LENGTH +
                ": " + file);
    }

    private static void write(final FileChannel channel, final QueueLayoutConfig layout) throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(LENGTH).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(VERSION_OFFSET, VERSION);
        buffer.putInt(MAX_APPENDERS_OFFSET, layout.maxAppenders());
        buffer.putInt(HEADER_REGION_SIZE_OFFSET, layout.headerRegionSize());
        buffer.putInt(PAYLOAD_REGION_SIZE_OFFSET, layout.payloadRegionSize());
        buffer.putInt(FLAGS_OFFSET, flags(layout));
        buffer.putLong(MIN_HEADER_FILE_SIZE_OFFSET, layout.minHeaderFileSize());
        buffer.putLong(MAX_HEADER_FILE_SIZE_OFFSET, layout.maxHeaderFileSize());
        buffer.putLong(MIN_PAYLOAD_FILE_SIZE_OFFSET, layout.minPayloadFileSize());
        buffer.putLong(MAX_PAYLOAD_FILE_SIZE_OFFSET, layout.maxPayloadFileSize());
        //magic last, and only once everything else is durable
        writeFully(channel, buffer.position(MAGIC.length));
        channel.force(true);
        writeFully(channel, ByteBuffer.wrap(MAGIC));
        channel.force(true);
    }

    private static void writeFully(final FileChannel channel, final ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            channel.write(buffer, buffer.position());
        }
    }

    private static int flags(final QueueLayoutConfig layout) {
        return (layout.expandHeaderFile() ? EXPAND_HEADER_FILE_FLAG : 0)
                | (layout.expandPayloadFiles() ? EXPAND_PAYLOAD_FILES_FLAG : 0)
                | (layout.rollHeaderFile() ? ROLL_HEADER_FILE_FLAG : 0)
                | (layout.rollPayloadFiles() ? ROLL_PAYLOAD_FILES_FLAG : 0);
    }
}
