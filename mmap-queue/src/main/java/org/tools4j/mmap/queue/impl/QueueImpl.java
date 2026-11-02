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

import org.agrona.CloseHelper;
import org.tools4j.mmap.mapping.api.AccessMode;
import org.tools4j.mmap.mapping.api.Closeable;
import org.tools4j.mmap.mapping.api.Describable;
import org.tools4j.mmap.mapping.api.ErrorListener;
import org.tools4j.mmap.mapping.api.LifecycleListener;
import org.tools4j.mmap.mapping.api.OpenMode;
import org.tools4j.mmap.queue.api.Appender;
import org.tools4j.mmap.queue.api.EntryIterator;
import org.tools4j.mmap.queue.api.EntryReader;
import org.tools4j.mmap.queue.api.IndexReader;
import org.tools4j.mmap.queue.api.Poller;
import org.tools4j.mmap.queue.api.Queue;
import org.tools4j.mmap.queue.config.AppenderConfig;
import org.tools4j.mmap.queue.config.IndexReaderConfig;
import org.tools4j.mmap.queue.config.QueueConfig;
import org.tools4j.mmap.queue.config.QueueLayoutConfig;
import org.tools4j.mmap.queue.config.ReaderConfig;

import java.io.File;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Implementation of {@link Queue} that allows a multiple writing threads and
 * multiple reading threads, when each thread creates their own instances of
 * {@link #createAppender() appender}, {@link #createPoller() poller} etc.
 */
public final class QueueImpl implements Queue, Describable {
    private final QueueFiles files;
    private final QueueConfig config;
    private final LifecycleListener lifecycleListener;
    private final ErrorListener errorListener;
    /** Config in effect, with the queue layout from the meta file; null until the queue exists (lazy read-only only) */
    private final AtomicReference<QueueConfig> effectiveConfig;
    private final Factory<ReaderConfig, Poller> pollerFactory;
    private final Factory<ReaderConfig, EntryReader> entryReaderFactory;
    private final Factory<ReaderConfig, EntryIterator> entryIteratorFactory;
    private final Factory<IndexReaderConfig, IndexReader> indexReaderFactory;
    private final Factory<AppenderConfig, Appender> appenderFactory;
    /** ID pool of appenders, null in read-only mode; closed last, after appenders have released their IDs */
    private final AppenderIdPool idPool;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final java.util.Queue<Registration> registrations = new ConcurrentLinkedQueue<>();

    public QueueImpl(final File file, final OpenMode openMode, final QueueConfig queueConfig) {
        this.files = new QueueFiles(file);
        this.config = queueConfig.toImmutableQueueConfig();
        this.lifecycleListener = config.lifecycleListener();
        this.errorListener = config.errorListener();
        this.effectiveConfig = new AtomicReference<>(openQueue(openMode));

        final Supplier<QueueConfig> effectiveConfigSupplier = this::effectiveConfig;
        //NOTE: pending idlers are created before the mappings, which are closed by the reader if its creation fails
        this.pollerFactory = factory((pollerConfig, onClose) -> {
            final PendingIdler pendingIdler = new PendingIdler(pollerConfig);
            return new PollerImpl(
                    queueNameIfNotClosed(),
                    ReaderMappings.create(files, openMode, effectiveConfigSupplier, pollerConfig),
                    pendingIdler,
                    onClose,
                    lifecycleListener,
                    errorListener
            );
        });
        this.entryReaderFactory = factory((readerConfig, onClose) -> new EntryReaderImpl(
                queueNameIfNotClosed(),
                ReaderMappings.create(files, openMode, effectiveConfigSupplier, readerConfig),
                onClose,
                lifecycleListener
        ));
        this.entryIteratorFactory = factory((readerConfig, onClose) -> {
            final PendingIdler pendingIdler = new PendingIdler(readerConfig);
            return new EntryIteratorImpl(
                    queueNameIfNotClosed(),
                    ReaderMappings.create(files, openMode, effectiveConfigSupplier, readerConfig),
                    pendingIdler,
                    onClose,
                    lifecycleListener
            );
        });
        this.indexReaderFactory = factory((indReaderConfig, onClose) -> new IndexReaderImpl(
                queueNameIfNotClosed(),
                ReaderMappings.create(files, openMode, effectiveConfigSupplier, indReaderConfig),
                onClose,
                lifecycleListener
        ));
        if (openMode.accessMode() == AccessMode.READ_ONLY) {
            this.idPool = null;
            this.appenderFactory = appenderConfig -> {
                throw new IllegalStateException("Cannot open appender in read-only mode for queue " +
                        queueNameIfNotClosed());
            };
        } else {
            final QueueConfig writerConfig = effectiveConfig.get();
            final AppenderIdPool pool = new AppenderIdPool(files.idPoolFile(), writerConfig.maxAppenders(), lifecycleListener,
                    errorListener);
            this.idPool = pool;
            this.appenderFactory = factory((appenderConfig, onClose) -> new AppenderImpl(
                    queueNameIfNotClosed(),
                    AppenderMappings.create(files, pool, writerConfig, appenderConfig),
                    onClose,
                    lifecycleListener
            ));
        }
        lifecycleListener.onOpened(this);
    }

    /**
     * Opens or creates the queue as defined by the open mode, and returns the config in effect.
     *
     * @param openMode the open mode
     * @return the config in effect, or null if the queue does not exist yet (lazy read-only only)
     */
    private QueueConfig openQueue(final OpenMode openMode) {
        final File metaFile = files.metaFile();
        final QueueLayoutConfig layout = switch (openMode) {
            case READ_ONLY -> {
                final QueueLayoutConfig existing = QueueMetaFile.read(metaFile);
                if (existing == null) {
                    throw new IllegalArgumentException("Queue does not exist: " + metaFile.getParentFile());
                }
                yield existing;
            }
            case READ_ONLY_LAZY -> QueueMetaFile.read(metaFile);
            case READ_WRITE -> QueueMetaFile.open(metaFile, openMode, () -> QueueLayoutConfigImpl.of(config), () -> {});
            case CREATE, OPEN_OR_CREATE, RECREATE -> {
                createQueueDir(metaFile.getParentFile());
                yield QueueMetaFile.open(metaFile, openMode, () -> QueueLayoutConfigImpl.of(config),
                        this::deleteOtherQueueFiles);
            }
        };
        return layout == null ? null : applyLayout(layout);
    }

    private QueueConfig effectiveConfig() {
        final QueueConfig cfg = effectiveConfig.get();
        return cfg != null ? cfg : effectiveConfigIfQueueExists();
    }

    private QueueConfig effectiveConfigIfQueueExists() {
        final QueueLayoutConfig layout = QueueMetaFile.read(files.metaFile());
        if (layout == null) {
            return null;
        }
        //NOTE: readers of several threads may read the meta file concurrently, the first to publish its config wins
        final QueueConfig cfg = QueueLayoutConfigs.applyLayout(config, layout);
        if (effectiveConfig.compareAndSet(null, cfg)) {
            warnIfDifferent(layout);
            return cfg;
        }
        return effectiveConfig.get();
    }

    private QueueConfig applyLayout(final QueueLayoutConfig layout) {
        warnIfDifferent(layout);
        return QueueLayoutConfigs.applyLayout(config, layout);
    }

    private void warnIfDifferent(final QueueLayoutConfig layout) {
        final CharSequence differences = QueueLayoutConfigs.diff(layout, config);
        if (differences != null) {
            errorListener.onWarning(this, "Queue was created with a different layout, using: " + differences);
        }
    }

    private void deleteOtherQueueFiles() {
        for (final File file : files.listFiles()) {
            if (!file.equals(files.metaFile())) {
                final boolean deleted = file.delete();
                assert deleted : files.queueName();
            }
        }
    }

    private static void createQueueDir(final File file) {
        //NOTE: a concurrent creator may have created it in the meantime
        if (!file.mkdir() && !file.isDirectory()) {
            throw new IllegalArgumentException("Parent directory does not exist: " + file);
        }
    }

    @FunctionalInterface
    private interface Factory<C, T extends Closeable> {
        T create(C config);
    }

    @FunctionalInterface
    private interface CloseRunnerFactory<C, T extends Closeable> {
        T create(C config, Runnable onClose);
    }

    /** Registration of an open element, which unregisters itself when run */
    private final class Registration implements Runnable {
        /** Plain field, published through adding the registration to the registrations queue */
        Closeable closeable;

        @Override
        public void run() {
            registrations.remove(this);
        }
    }

    private <C, T extends Closeable> Factory<C, T> factory(final CloseRunnerFactory<C, T> closeRunnerFactory) {
        requireNonNull(closeRunnerFactory);
        return config -> {
            final Registration registration = new Registration();
            final T closeable = closeRunnerFactory.create(config, registration);
            registration.closeable = closeable;
            registrations.add(registration);
            //NOTE: close() may have missed the registration if it ran concurrently, so we close the element ourselves;
            //      as both register-then-check and set-closed-then-close use volatile accesses, at least one of us
            //      closes it
            if (isClosed()) {
                CloseHelper.quietClose(closeable);
                throw queueClosedException();
            }
            return closeable;
        };
    }

    /**
     * Returns the number of open elements registered with this queue, for testing.
     *
     * @return the number of open appenders, pollers, entry readers, entry iterators and index readers
     */
    int openElements() {
        return registrations.size();
    }

    @Override
    public Appender createAppender() {
        return createAppender(config.appenderConfig());
    }

    @Override
    public Appender createAppender(final AppenderConfig config) {
        return appenderFactory.create(config);
    }

    @Override
    public Poller createPoller() {
        return createPoller(config.pollerConfig());
    }

    @Override
    public Poller createPoller(final ReaderConfig config) {
        return pollerFactory.create(config);
    }

    @Override
    public IndexReader createIndexReader() {
        return createIndexReader(config.indexReaderConfig());
    }

    @Override
    public EntryReader createEntryReader() {
        return createEntryReader(config.entryReaderConfig());
    }

    @Override
    public EntryReader createEntryReader(final ReaderConfig config) {
        return entryReaderFactory.create(config);
    }

    @Override
    public EntryIterator createEntryIterator() {
        return createEntryIterator(config.entryIteratorConfig());
    }

    @Override
    public EntryIterator createEntryIterator(final ReaderConfig config) {
        return entryIteratorFactory.create(config);
    }

    @Override
    public IndexReader createIndexReader(final IndexReaderConfig config) {
        return indexReaderFactory.create(config);
    }

    private String queueNameIfNotClosed() {
        if (!isClosed()) {
            return files.queueName();
        }
        throw queueClosedException();
    }

    private IllegalStateException queueClosedException() {
        return new IllegalStateException("Queue is closed: " + files.queueName());
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            for (final Registration registration : registrations) {
                CloseHelper.quietClose(registration.closeable);
            }
            registrations.clear();
            CloseHelper.quietClose(idPool);
            lifecycleListener.onClosed(this);
        }
    }

    @Override
    public StringBuilder describeTo(final StringBuilder sb) {
        return sb.append("QueueImpl")
                .append(":queue=").append(files.queueName())
                .append("|closed=").append(isClosed());
    }

    @Override
    public String toString() {
        return Describable.describe(this);
    }
}
