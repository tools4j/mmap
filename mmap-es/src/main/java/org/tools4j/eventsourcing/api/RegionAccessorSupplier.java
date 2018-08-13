package org.tools4j.eventsourcing.api;

import org.tools4j.mmap.region.api.FileSizeEnsurer;
import org.tools4j.mmap.region.api.RegionAccessor;
import org.tools4j.mmap.region.api.RegionRingFactory;
import org.tools4j.mmap.region.impl.InitialBytes;
import org.tools4j.mmap.region.impl.MappedFile;
import org.tools4j.mmap.region.impl.RegionRingAccessor;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

/**
 * Index and Message region accessor supplier for indexed queues and stores.
 */
public interface RegionAccessorSupplier {
    /**
     * @return index region accessor
     */
    RegionAccessor indexAccessor();

    /**
     * @return message region accessor
     */
    RegionAccessor messageAccessor();

    /**
     * Factory method for readOnly region accessors
     * @param directory - directory where the files are located
     * @param filePrefix - file prefix for both index and message files.
     *                   Index would have "_index" suffix and message would have "_message" suffix.
     * @param regionRingFactory - region ring factory
     * @param regionSize - region size in bytes
     * @param regionRingSize  - number of regions in a ring
     * @param regionsToMapAhead - number of regions to map ahead.
     * @return an instance of RegionAccessorSupplier
     * @throws IOException when either index and message files could not be mapped.
     */
    static RegionAccessorSupplier forReadOnly(final String directory,
                                              final String filePrefix,
                                              final RegionRingFactory regionRingFactory,
                                              final int regionSize,
                                              final int regionRingSize,
                                              final int regionsToMapAhead) throws IOException {
        final String indexFileName = directory + "/" + filePrefix + "_index";
        final String messageFileName = directory + "/" + filePrefix + "_message";

        final MappedFile indexPollerFile = new MappedFile(indexFileName, MappedFile.Mode.READ_ONLY,
                regionSize, RegionAccessorSupplier::initIndexFile);
        final MappedFile messagePollerFile = new MappedFile(messageFileName, MappedFile.Mode.READ_ONLY,
                regionSize, (file, mode) -> {});

        final RegionAccessor indexRegionRingAccessor = new RegionRingAccessor(
                regionRingFactory.create(
                        regionRingSize,
                        regionSize,
                        indexPollerFile::getFileChannel,
                        FileSizeEnsurer.NO_OP,
                        indexPollerFile.getMode().getMapMode()),
                regionSize,
                regionsToMapAhead,
                indexPollerFile::close);

        final RegionAccessor messageRegionRingAccessor = new RegionRingAccessor(
                regionRingFactory.create(
                        regionRingSize,
                        regionSize,
                        messagePollerFile::getFileChannel,
                        FileSizeEnsurer.NO_OP,
                        messagePollerFile.getMode().getMapMode()),
                regionSize,
                regionsToMapAhead,
                messagePollerFile::close);

        return new RegionAccessorSupplier() {
            @Override
            public RegionAccessor indexAccessor() {
                return indexRegionRingAccessor;
            }

            @Override
            public RegionAccessor messageAccessor() {
                return messageRegionRingAccessor;
            }
        };
    }

    /**
     * Factory method for readWrite region accessors with files to be cleared before usage.
     * @param directory - directory where the files are located
     * @param filePrefix - file prefix for both index and message files.
     *                   Index would have "_index" suffix and message would have "_message" suffix.
     * @param regionRingFactory - region ring factory
     * @param regionSize - region size in bytes
     * @param regionRingSize  - number of regions in a ring
     * @param regionsToMapAhead - number of regions to map ahead.
     * @param maxFileSize - max file size to prevent unexpected file growth
     * @return an instance of RegionAccessorSupplier
     * @throws IOException when either index and message files could not be mapped.
     */
    static RegionAccessorSupplier forReadWriteClear(final String directory,
                                                    final String filePrefix,
                                                    final RegionRingFactory regionRingFactory,
                                                    final int regionSize,
                                                    final int regionRingSize,
                                                    final int regionsToMapAhead,
                                                    final long maxFileSize) throws IOException {
        return forReadWrite(directory, filePrefix, true, regionRingFactory, regionSize, regionRingSize,
                regionsToMapAhead, maxFileSize);
    }


    /**
     * Factory method for readWrite region accessors.
     * @param directory - directory where the files are located
     * @param filePrefix - file prefix for both index and message files.
     *                   Index would have "_index" suffix and message would have "_message" suffix.
     * @param regionRingFactory - region ring factory
     * @param regionSize - region size in bytes
     * @param regionRingSize  - number of regions in a ring
     * @param regionsToMapAhead - number of regions to map ahead.
     * @param maxFileSize - max file size to prevent unexpected file growth
     * @return an instance of RegionAccessorSupplier
     * @throws IOException when either index and message files could not be mapped.
     */
    static RegionAccessorSupplier forReadWrite(final String directory,
                                               final String filePrefix,
                                               final RegionRingFactory regionRingFactory,
                                               final int regionSize,
                                               final int regionRingSize,
                                               final int regionsToMapAhead,
                                               final long maxFileSize) throws IOException {
        return forReadWrite(directory, filePrefix, false, regionRingFactory, regionSize, regionRingSize,
                regionsToMapAhead, maxFileSize);
    }

    /**
     * Factory method for readWrite region accessors with file clearing option.
     * @param directory - directory where the files are located
     * @param filePrefix - file prefix for both index and message files.
     *                   Index would have "_index" suffix and message would have "_message" suffix.
     * @param clear - true if the files are to be cleared
     * @param regionRingFactory - region ring factory
     * @param regionSize - region size in bytes
     * @param regionRingSize  - number of regions in a ring
     * @param regionsToMapAhead - number of regions to map ahead.
     * @param maxFileSize - max file size to prevent unexpected file growth
     * @return an instance of RegionAccessorSupplier
     * @throws IOException when either index and message files could not be mapped.
     */
    static RegionAccessorSupplier forReadWrite(final String directory,
                                               final String filePrefix,
                                               final boolean clear,
                                               final RegionRingFactory regionRingFactory,
                                               final int regionSize,
                                               final int regionRingSize,
                                               final int regionsToMapAhead,
                                               final long maxFileSize) throws IOException {
        final String indexFileName = directory + "/" + filePrefix + "_index";
        final String messageFileName = directory + "/" + filePrefix + "_message";
        final MappedFile.Mode mapMode = clear ? MappedFile.Mode.READ_WRITE_CLEAR : MappedFile.Mode.READ_WRITE;

        final MappedFile indexAppenderFile = new MappedFile(indexFileName, mapMode,
                regionSize, RegionAccessorSupplier::initIndexFile);
        final MappedFile messageAppenderFile = new MappedFile(messageFileName, mapMode,
                regionSize, (file, mode) -> {});

        final RegionAccessor indexRegionRingAccessor = new RegionRingAccessor(
                regionRingFactory.create(
                        regionRingSize,
                        regionSize,
                        indexAppenderFile::getFileChannel,
                        FileSizeEnsurer.forWritableFile(indexAppenderFile::getFileLength, indexAppenderFile::setFileLength, maxFileSize),
                        indexAppenderFile.getMode().getMapMode()),
                regionSize,
                regionsToMapAhead,
                indexAppenderFile::close);

        final RegionAccessor messageRegionRingAccessor = new RegionRingAccessor(
                regionRingFactory.create(
                        regionRingSize,
                        regionSize,
                        messageAppenderFile::getFileChannel,
                        FileSizeEnsurer.forWritableFile(messageAppenderFile::getFileLength, messageAppenderFile::setFileLength, maxFileSize),
                        messageAppenderFile.getMode().getMapMode()),
                regionSize,
                regionsToMapAhead,
                messageAppenderFile::close);

        return new RegionAccessorSupplier() {
            @Override
            public RegionAccessor indexAccessor() {
                return indexRegionRingAccessor;
            }

            @Override
            public RegionAccessor messageAccessor() {
                return messageRegionRingAccessor;
            }
        };
    }

    static void initIndexFile(final FileChannel fileChannel, final MappedFile.Mode mode) throws IOException {
        switch (mode) {
            case READ_ONLY:
                if (fileChannel.size() < 8) {
                    throw new IllegalArgumentException("Invalid io format");
                }
                break;
            case READ_WRITE:
                if (fileChannel.size() == 0) {
                    clearFile(fileChannel);
                }
                break;
            case READ_WRITE_CLEAR:
                clearFile(fileChannel);
                break;
            default:
                throw new IllegalArgumentException("Invalid mode: " + mode);
        }
    }

    static void clearFile(final FileChannel fileChannel) throws IOException {
        final FileLock lock = fileChannel.lock();
        try {
            fileChannel.truncate(0);
            fileChannel.transferFrom(InitialBytes.ZERO, 0, 8);
            fileChannel.force(true);
        } finally {
            lock.release();
        }
    }
}
