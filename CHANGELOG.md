# Changelog

Notable changes per release. Entries are curated from the commits since the previous release tag; run
`./gradlew -q changelogDraft` for a draft of the next release's entry.

## 3.0 (unreleased)

## 2.1 (2026-11-14)

### Breaking changes
- The basic types `Closeable`, `Describable`, `ErrorListener` and `LifecycleListener` moved from
  `org.tools4j.mmap.mapping.api` to `org.tools4j.mmap.core.base`, the `@Unsafe` annotation to
  `org.tools4j.mmap.core.unsafe`, and `LoggingErrorListener` from `org.tools4j.mmap.mapping.impl` to
  `org.tools4j.mmap.core.logging`, in the new module `mmap-core`, which `mmap-mapping` depends on.
- The default error listener is configured for all modules with the system property `mmap.core.errorListener`
  (`LOGGING`, the default, `NO_OP` or a class name), replacing `mmap.mapping.errorListener`; `ERROR_LISTENER_PROPERTY`,
  `ERROR_LISTENER_DEFAULT`, `LOGGING_LISTENER` and `defaultErrorListener()` moved from `MappingConfigurations` to
  `org.tools4j.mmap.core.config.CoreConfigurations`. `LoggingLifecycleListener` moved from
  `org.tools4j.mmap.mapping.impl` to `org.tools4j.mmap.core.logging` and logs all resources at its level; the
  `LOG_INFO` and `LOG_WARN` values of `mmap.mapping.lifecycleListener` still log internal resources at debug level,
  combining two logging listeners with the new `LifecycleListener.conditional(condition, matching, other)`.
- Async runtimes wait with mmap-core's `Idler` (`org.tools4j.mmap.core.idle`) instead of Agrona's `IdleStrategy`:
  `AsyncRuntime.create(..)`, `AsyncRuntimeInstances.newMappingRuntime(..)` and `newUnmappingRuntime(..)`, and the
  configurators' `mappingRuntimeUsing(..)` and `unmappingRuntimeUsing(..)` take an `Idler` or a `Supplier` of idlers,
  e.g. `Idler.backoff()`, `Idler.backoff(maxSpins, maxYields, minParkNanos, maxParkNanos)` or
  `IdleMode.BUSY_SPIN.newIdler()`. The system properties `mmap.mapping.async.mappingRuntimeIdleStrategy` and
  `mmap.mapping.async.unmappingRuntimeIdleStrategy` are renamed to `mmap.mapping.async.mappingRuntimeIdler` and
  `mmap.mapping.async.unmappingRuntimeIdler`, accepting `NO_OP`, `BUSY_SPIN`, `YIELDING` or `BACKOFF` (the default)
  besides class names, and `MappingConfigurations.default(Un)mappingRuntimeIdleStrategySupplier()` to
  `default(Un)mappingRuntimeIdlerSupplier()`.
- Pollers and entry iterators no longer idle while the queue or its first entry does not exist yet; idling is left to
  the application's poll or iteration loop. Removed `ReaderConfigurator.pendingIdleStrategy(..)`,
  `pendingQueueIdleStrategy(..)` and `pendingDataIdleStrategy(..)`, the corresponding `ReaderConfig` getters, and the
  system properties `mmap.queue.pendingQueueIdleStrategy`, `mmap.queue.pollerPendingDataIdleStrategy` and
  `mmap.queue.entryIteratorPendingDataIdleStrategy`.
- File initialisers are reworked: `FileInitialiser.none()` and `minLength(..)` are replaced by the initialisers
  `FileInitialiser.LAZY` (sparse file space, the default), `ZERO` and `MINUS_ONE` (allocating new space by writing
  zero or minus one bytes, so that writing to the mapping cannot fault on a full disk). A custom initialiser returns a
  `PreAccessHook` per access mode, invoked when a file is opened (`onOpen`) and extended (`onExtend`); mappers ensure
  the min file length themselves. Fixed mappings allocate their writable files with zero bytes.
- The file initialiser is part of `MappingConfig` (`MappingConfigurator.fileInitialiser(..)`, system property
  `mmap.mapping.fileInitialiser` with `LAZY`, `ZERO`, `MINUS_ONE` or a class name). The `Mappings` overloads taking a
  `FileInitialiser` are removed, and `FileMappers.create(..)` takes the initialiser from the config.

### Added
- Queues can log lifecycle events differently from the mapping module, with the system property
  `mmap.queue.lifecycleListener`, accepting the values of `mmap.mapping.lifecycleListener`, whose value it uses if not
  set.
- Appenders can prefill new space of header and payload files, reserving the disk space so that appending cannot
  fault on a full disk: `AppenderConfigurator.prefillFiles(..)`, `prefillHeaderFiles(..)` and `prefillPayloadFiles(..)`,
  or the system properties `mmap.queue.appenderPrefillHeaderFiles` and `mmap.queue.appenderPrefillPayloadFiles`
  (default: sparse files, as before).

### Changed
- The default region size is 2 MB instead of 64 KB, for huge page eligibility and fewer unmaps; existing queues keep
  theirs. The maximum entry size of new queues with default region sizes grows accordingly.
- Regions are pre-touched only when mapped ahead by the async mapping thread, so that appenders with sync mapping
  strategies no longer stall at region boundaries, and pre-touching faults in every page, also on Windows and aarch64.
- New appenders find the end of the queue with a binary search, and scan back for their own last entry only if their
  appender ID has a payload file: opening an appender on a long queue takes well under a millisecond instead of
  seconds when the ID has no entries or its last entry is near the end.
- Readers look up payload mappings by appender ID in an array instead of a hash map, which makes polling about 15%
  faster per entry.
- Open modes determine whether rolling files exist by the first rolling file alone, without scanning the directory;
  rolling files whose first file was deleted are not supported.
- New queues expand their header files and roll them at 1 GB instead of creating 64 MB files at full size, so that
  header files roll about as rarely as payload files; existing queues keep their layout.
- Appenders create the next rolling header and payload file ahead, and rolling files keep up to 4 files open instead
  of 3 to make room for it.
- Async mapping maps 4 regions ahead instead of 2, with region caches of 8 instead of 4 regions, so that mapping
  ahead stays ahead of fast appenders; the cache sizes keep the previous payload region mapped for entries growing
  across a region.
- The mapping runtime thread backs off when idle instead of busy spinning, so that it no longer occupies a CPU by
  default; set `mmap.mapping.async.mappingRuntimeIdleStrategy` to `org.agrona.concurrent.BusySpinIdleStrategy` for
  the previous behaviour.
- Class files are compiled for Java 17 whatever JDK builds them, so that a release built on a newer JDK runs on
  Java 17 (checked by a CI job building on Java 25 and testing on Java 17).
- The benchmark `mmap-perf` prints its usage when run without options, instead of starting a run with the defaults;
  pass for instance `--queue mmap` to run it with the defaults.

### Fixed
- Files are extended under an exclusive file lock, so that a process extending a file with an outdated length can no
  longer truncate it after another process extended it further.
- Sync mapping strategies over rolling files allocating garbage on every region mapped.
- Closing appenders and readers with async mapping took tens of milliseconds, waiting for the async mapping thread
  in steps of 20 ms; it now takes about a millisecond.
- Waiting for a file lock held by another thread or process retried only every 10 ms; it now retries every
  millisecond, for instance when several appenders extend the same file.

### Dependencies
- Agrona 2.7.1 (was 2.7.0).
- Chronicle Queue 2026.8 in `mmap-perf` (was 5.21.98), which also runs on Java 21 and 25.

## 2.0 (2026-11-02)

2.0 is not compatible with 1.0: neither the API nor the queue files. Queues written by 1.0 cannot be opened by 2.0,
and there is no migration, since 1.0 is considered a draft release that is not publicly used yet.

### Breaking changes
- Module `mmap-region` renamed to `mmap-mapping`, packages `org.tools4j.mmap.region.*` to
  `org.tools4j.mmap.mapping.*`.
- New queue file format: a meta file (`<name>_meta.mmq`) stores the queue layout (region and file sizes, expand and
  roll flags), which applies to everyone opening the queue later.
- The method `Queue.create(..)` was renamed to `Queue.open(..)`. Mappings and queues are opened with an `OpenMode`
  (`READ_ONLY`, `READ_ONLY_LAZY`, `READ_WRITE`, `CREATE`, `OPEN_OR_CREATE`, `RECREATE`) instead of an `AccessMode`.
- Region size moved from `MappingStrategyConfig` to `MappingConfig`. Queues have one header and one payload region
  size (`mmap.queue.headerRegionSize`, `mmap.queue.payloadRegionSize`) instead of one per accessor kind.
- No logging in the library: lifecycle events go to a `LifecycleListener` and swallowed errors and warnings to an
  `ErrorListener`, both configured in `MappingConfig` and `QueueConfig`. The defaults log to SLF4J if present, else
  to java.util.logging; SLF4J is an optional dependency.
- Mapping errors are thrown instead of being logged; `NULL_ADDRESS` now only means "not available yet".
- System properties `mmap.mapping.async.mappingRuntime` and `mmap.mapping.async.unmappingRuntime` removed; custom
  runtimes are set through the async mapping and unmapping configurators.
- The method `AsyncMappingConfigurator.mappingRuntimeUsing(..)` takes either an idle strategy or a thread factory;
  set both by chaining.
- Entry iterators end once `hasNext()` returns false, like Java collection iterators; use
  `IterableContext.continuous()` for iterators returning entries appended later. `IterableContext.reverse()` (a
  toggle) is replaced by `backward()`.

### Added
- Iteration modes `FORWARD` (default), `BACKWARD` and `CONTINUOUS`, set with `IterableContext.forward()`,
  `backward()` and `continuous()`, and queried with `mode()`.
- CPU pinning (Linux x86_64 and aarch64): `CpuPinning.pinCurrentThread(..)` and `CpuPinning.threadFactory(..)`;
  mapping runtime threads are pinned with `mmap.mapping.async.mappingRuntimeCpus`, e.g. `4-7`. Async runtimes accept
  a `ThreadFactory`.
- The open mode `READ_ONLY_LAZY` for readers of queues that do not exist yet.
- Pollers and entry iterators idle instead of busy spinning while there is nothing to read yet: until the queue
  exists (`READ_ONLY_LAZY`), and until its first entry. Both default to a `BackoffIdleStrategy`, set per reader with
  `ReaderConfigurator.pendingQueueIdleStrategy(..)` and `pendingDataIdleStrategy(..)`, or with
  `mmap.queue.pendingQueueIdleStrategy`, `mmap.queue.pollerPendingDataIdleStrategy` and
  `mmap.queue.entryIteratorPendingDataIdleStrategy`. Once an entry has been read, they never idle.
- Lifecycle log level `mmap.mapping.lifecycleListener` = `LOG_DEBUG` (default), `LOG_INFO` or `LOG_WARN`.
- The module `mmap-perf`, a command line benchmark measuring mmap and Chronicle Queue latencies through the same
  harness, see the README.

### Changed
- The queue config `maxAppenders` accepts any value from 1 to 256 (was 1, 64 or 256) and defaults to 256 (was 64). It
  only limits the appender IDs of the queue instance opened with it, so instances with different limits can share a
  queue.
- Javadoc for all public API and configuration packages.

### Fixed
- Appender overwriting the last entry of a reused appender ID.
- The method `AppendingContext.ensureCapacity` crossing a payload region.
- Rolling file mappers leaking the mappings of evicted files.
- Region mappers with an LRU cache corrupting their cache lists, in particular when reusing regions whose unmapping
  was deferred.
- Async mapping (map-ahead) of rolling files made more stable, with a new lock-free cache of open files: cache slots
  no longer stay claimed forever after a failed file creation, contention no longer fails mapping, and files are
  created ahead again.
- An `AtomicArray` grown beyond its first block returning offsets within the block instead of array indices from
  `indexOfMin`, `lastIndexOfMin`, `indexOfMax`, `lastIndexOfMax` and to the function of `computeIfPresent`.
- The method `FileLocks.acquireLock` spinning forever after a timeout.
- Fixed mappings at offsets not aligned to the mapping granularity.
- Read-only files opened with write access on Windows.
- Configurators not falling back to their defaults, and sync default strategies starting an async mapping thread.
- Async map-ahead mapping beyond the file size limit.
- Searching the last entry looping or overflowing when indices reach `Index.MAX`.
- Files left open when creating a fixed mapping or the appender ID pool failed, which prevented deleting them on
  Windows.

### Dependencies
- Agrona 2.7.0 (was 2.6.1).

## 1.0 (2026-10-06)

First (draft) release.

- The `mmap-region` module: memory mapped file regions exposed as Agrona buffers, through fixed, region, elastic and
  adaptive mappings and mapping pools. Sync or async mapping strategies with region caches, async unmapping and async
  map-ahead of upcoming regions, over fixed-size, expandable or rolling files.
- The `mmap-queue` module: an off-heap queue on top of it, with lock-free concurrent appenders (up to 256), pollers,
  entry readers, entry iterators and index readers, configured per accessor kind through configurators and system
  properties.
