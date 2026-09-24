# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> **Scope note:** this file currently documents the `mmap-region` module only. `mmap-queue` (which depends on
> `mmap-region`) is not yet covered here — extend this file when working on it.

## Project

`tools4j/mmap` — memory-mapped files used to implement off-heap, low-latency utilities such as queues. Multi-module
Gradle project: `mmap-region` (memory-mapped region/file abstractions) and `mmap-queue` (built on top of it).

## Commands

- Build everything (default task is `clean build`): `./gradlew`
- Build/test only the region module: `./gradlew :mmap-region:build`
- Run all region-module tests: `./gradlew :mmap-region:test`
- Run a single test class: `./gradlew :mmap-region:test --tests "org.tools4j.mmap.region.impl.SyncRegionMapperTest"`
- Run a single test method: `./gradlew :mmap-region:test --tests "org.tools4j.mmap.region.impl.SyncRegionMapperTest.methodName"`
- Performance tests (JUnit-tagged `perf`, excluded from the normal `test` task): `./gradlew :mmap-region:perfTest`
- Javadoc: `./gradlew :mmap-region:javadoc`

Notes:
- The `test` task runs with assertions **enabled** (`enableAssertions = true`) — `assert` statements in this codebase
  are load-bearing invariant checks, not disabled documentation.
- New/changed `.java` files must carry the MIT license header from `etc/LICENSE.template`; the Gradle `license` plugin
  auto-applies it (`compileJava` depends on `licenseFormat`), so a missing/incorrect header is fixed automatically on
  build rather than failing it.
- CI (`.github/workflows/gradle.yml`) runs the default `./gradlew` task across Java 17/21/23 on Ubuntu and Windows.

## Architecture (`mmap-region`)

The module is layered into four packages under `org.tools4j.mmap.region`, and the layering is the thing to respect
when adding code — each has a distinct role:

- **`api`** — the public contract. Application code should only need this package (plus `config`).
- **`config`** — configuration interfaces, built as a repeated pattern (see below).
- **`unsafe`** — the low-level mapping engine (`RegionMapper`, `FileMapper` and implementations). Everything here is
  marked with the `@Unsafe` annotation: it deals in raw memory addresses, and misuse can crash the JVM. Not intended
  for direct use by application code — it exists to be composed by the `api`/`config` layers.
- **`impl`** — internal implementations backing both `api` and `config` (the concrete `Mapping` classes, the
  lock-free data structures, the configurator/config-defaults classes, misc. utilities).

### The `Mapping` type hierarchy (`api`)

`Mapping` is a file block mapped into memory, exposed via an `AtomicBuffer`. Two branches:
- `FixedMapping` — arbitrary start/end, fixed for its lifetime.
- `DynamicMapping` — repositionable via `moveTo`/`moveBy`, backed by a `RegionMapper`. Three subtypes differ in how
  much of the underlying region they expose: `RegionMapping` (always the whole region; moves must land on region
  boundaries), `ElasticMapping` (offset into a region, spans to the region end), `AdaptiveMapping` (arbitrary slice
  within a region, explicit length).

Instances are created via the `Mappings` facade (simple cases) or `MappingPool` (when many mappings share one
underlying file/region-mapper with ref-counted region sharing). Both are documented as **not thread-safe** — a
`MappingPool` and the mappings it produces belong to a single thread; concurrent access needs one pool per thread.

### The mapping engine (`unsafe`) — sync vs. async

`RegionMapper` maps/unmaps fixed-size regions on demand; `FileMapper` maps/unmaps arbitrary byte ranges within one
physical file. `RegionMappers.create(...)` and `FileMappers.create(...)` assemble these from a `MappingStrategyConfig`
via a decorator chain — this is the piece to trace when changing mapping/caching/async behavior:

1. Base mapper: `SyncRegionMapper` (fully synchronous) or `SyncRegionMapperAsyncUnmapper` (unmap offloaded to an
   `AsyncRuntime` thread via a lock-free SPSC ring buffer).
2. Optional caching: `RingCacheRegionMapper` (fixed-size ring keyed by region index) and/or `LruCacheRegionMapper`
   (LRU eviction over a bounded set), each with an optional *defer-unmapping* mode (mark-as-unused instead of
   unmapping immediately, in case the region is reused before eviction).
3. Optional read-ahead: `AsyncRunAheadRegionMapper` pre-maps upcoming regions on a background thread when it detects
   sequential access, using another lock-free ownership-token handoff between the requesting thread and the mapper
   thread.

`FileMapper` implementations: `FixedSizeFileMapper` (preallocated), `ExpandableSizeFileMapper` (grows on demand,
power-of-two-sized increments), `ReadOnlyFileMapper`. On top of any of these, `RollingFileMapper` (single-threaded)
or `ConcurrentRollingFileMapper` (thread-safe, for the async-mapping strategy) splits a logical stream across
multiple physical files ("rolling"), keeping at most `maxOpenFiles` open at once on an LRU basis.

### Concurrency model

The two layers have opposite default expectations, and it matters which one you're editing:

- **`Mapping`/`MappingPool`/`RegionMapper` (the `api` layer and its `unsafe` `RegionMapper` decorator chain) are
  single-threaded.** A `MappingPool` and the mappings/region mappers it produces belong to one thread; sharing them
  across threads needs a separate pool per thread. `RingCacheRegionMapper`/`LruCacheRegionMapper` use plain
  (unsynchronized) fields on this assumption.
- **`FileMapper` implementations are thread-safe by design** (`map`/`unmap` may be called concurrently from multiple
  threads) — see each class's own class-level Javadoc for its specific guarantee. The one exception is
  `RollingFileMapper`, which is single-threaded; its thread-safe counterpart is `ConcurrentRollingFileMapper`, used
  when an async mapping/unmapping strategy is configured. `ConcurrentRollingFileMapper` is backed by `AtomicLruCache`
  (a fixed-capacity, lock-free, pin-aware LRU cache keyed by int index) and `AtomicArray` (a lock-free,
  dynamically-growing array). Both — and the ownership-token handoff in `AsyncRunAheadRegionMapper` — follow the same
  deliberate pattern: **one atomic field is the sole synchronization point**, and other fields are plain reads/writes
  that piggyback on the happens-before edge that atomic field establishes (documented in `AtomicLruCache`'s
  class-level Javadoc). Preserve that pattern — and its reasoning in comments — when touching these classes; don't
  casually add more atomics or "simplify" the plain fields without understanding why they're plain.

Region sizes are required to be a power of two and a multiple of the OS page size (`Constants.REGION_SIZE_GRANULARITY`,
detected via reflection into JDK internals) — this constraint underlies most of the bit-mask arithmetic in the
`unsafe` and `impl` packages (region offset/index/position calculations all use shifts and masks, not division).

### Configuration (`config` + `impl`)

Four config types (`MappingConfig`, `MappingStrategyConfig`, `AsyncMappingConfig`, `AsyncUnmappingConfig`) all follow
the same repeated pattern:
- `XConfig` — read-only interface.
- `XConfigurator extends XConfig` — fluent mutable builder (`XConfigurator.configure()` / `configure(XConfig defaults)`).
- `XConfigImpl` (in `impl`) — immutable record snapshot, produced by `toImmutableConfig()`.
- `XConfigDefaults` (in `impl`) — enum singleton delegating every getter to `MappingConfigurations.defaultX()`
  (system-property-driven defaults).

Configurator getters lazily resolve a value the first time they're read: own field → supplied `defaults` → system
default (`MappingConfigurations`). When adding a new configurable field, follow the existing sentinel convention for
"unset" exactly (each configurator's `reset()` method shows the correct sentinel per field type — `-1` for ints/longs
that don't treat `0` as unset, `0` where `0` *is* a valid "no-op" value, `null` for boxed/object types) and make sure
the field's initial state (constructor) actually matches that sentinel, not just `reset()`.