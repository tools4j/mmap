# Changelog

Notable changes per release. Entries are curated from the commits since the previous release tag; run
`./gradlew -q changelogDraft` for a draft of the next release's entry.

## 2.0 (unreleased)

2.0 is not compatible with 1.0: neither the API nor the queue files. Queues written by 1.0 cannot be opened by 2.0,
and there is no migration.

### Breaking changes
- Module `mmap-region` renamed to `mmap-mapping`, packages `org.tools4j.mmap.region.*` to
  `org.tools4j.mmap.mapping.*`.
- New queue file format: a meta file (`<name>_meta.mmq`) stores the queue layout (region and file sizes, expand and
  roll flags), which applies to everyone opening the queue later.
- `Queue.create(..)` renamed to `Queue.open(..)`. Mappings and queues are opened with an `OpenMode` (`READ_ONLY`,
  `READ_ONLY_LAZY`, `READ_WRITE`, `CREATE`, `OPEN_OR_CREATE`, `RECREATE`) instead of an `AccessMode`.
- Region size moved from `MappingStrategyConfig` to `MappingConfig`. Queues have one header and one payload region
  size (`mmap.queue.headerRegionSize`, `mmap.queue.payloadRegionSize`) instead of one per accessor kind.
- No logging in the library: lifecycle events go to a `LifecycleListener` and swallowed errors and warnings to an
  `ErrorListener`, both configured in `MappingConfig` and `QueueConfig`. The defaults log to SLF4J if present, else
  to java.util.logging; SLF4J is an optional dependency.
- Mapping errors are thrown instead of being logged; `NULL_ADDRESS` now only means "not available yet".
- System properties `mmap.mapping.async.mappingRuntime` and `mmap.mapping.async.unmappingRuntime` removed; custom
  runtimes are set through the async mapping and unmapping configurators.
- `AsyncMappingConfigurator.mappingRuntimeUsing(..)` takes either an idle strategy or a thread factory; set both by
  chaining.
- Entry iterators end once `hasNext()` returns false, like Java collection iterators; use
  `IterableContext.continuous()` for iterators returning entries appended later. `IterableContext.reverse()` (a
  toggle) is replaced by `backward()`.

### Added
- Iteration modes `FORWARD` (default), `BACKWARD` and `CONTINUOUS`, set with `IterableContext.forward()`,
  `backward()` and `continuous()`, and queried with `mode()`.
- CPU pinning (Linux x86_64 and aarch64): `CpuPinning.pinCurrentThread(..)` and `CpuPinning.threadFactory(..)`;
  mapping runtime threads are pinned with `mmap.mapping.async.mappingRuntimeCpus`, e.g. `4-7`. Async runtimes accept
  a `ThreadFactory`.
- `READ_ONLY_LAZY` readers of queues that do not exist yet.
- Pollers and entry iterators idle instead of busy spinning while there is nothing to read yet: until the queue
  exists (`READ_ONLY_LAZY`), and until its first entry. Both default to a `BackoffIdleStrategy`, set per reader with
  `ReaderConfigurator.pendingQueueIdleStrategy(..)` and `pendingDataIdleStrategy(..)`, or with
  `mmap.queue.pendingQueueIdleStrategy`, `mmap.queue.pollerPendingDataIdleStrategy` and
  `mmap.queue.entryIteratorPendingDataIdleStrategy`. Once an entry has been read, they never idle.
- Lifecycle log level `mmap.mapping.lifecycleListener` = `LOG_DEBUG` (default), `LOG_INFO` or `LOG_WARN`.
- `mmap-perf`, a command line benchmark measuring mmap and Chronicle Queue latencies through the same harness
  (not published), see the README.

### Fixed
- Appender overwriting the last entry of a reused appender ID.
- `AppendingContext.ensureCapacity` crossing a payload region.
- Rolling file mappers leaking the mappings of evicted files.
- Region mappers with an LRU cache corrupting their cache lists, in particular when reusing regions whose unmapping
  was deferred.
- Async mapping of rolling files leaving cache slots claimed forever after a failed file creation, and never creating
  files ahead.
- `FileLocks.acquireLock` spinning forever after a timeout.
- Fixed mappings at offsets not aligned to the mapping granularity.
- Read-only files opened with write access on Windows.
- Configurators not falling back to their defaults, and sync default strategies starting an async mapping thread.
- Async map-ahead mapping beyond the file size limit.

### Dependencies
- Agrona 2.7.0 (was 2.6.1).
