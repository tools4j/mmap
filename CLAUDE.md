# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

`tools4j/mmap` — memory-mapped files used to implement off-heap, low-latency utilities such as queues. Multi-module
Gradle project: `mmap-region` (memory-mapped region/file abstractions) and `mmap-queue` (built on top of it).

## Commands

- Build everything (default task is `clean build`): `./gradlew`
- Build/test a single module: `./gradlew :mmap-region:build` / `./gradlew :mmap-queue:build`
- Run all tests in a module: `./gradlew :mmap-region:test` / `./gradlew :mmap-queue:test`
- Run a single test class: `./gradlew :mmap-region:test --tests "org.tools4j.mmap.region.impl.SyncRegionMapperTest"`
  (swap module/package for `mmap-queue`, e.g. `:mmap-queue:test --tests "org.tools4j.mmap.queue.impl.QueueTest"`)
- Run a single test method: append `.methodName` to the `--tests` filter above
- Performance tests (JUnit-tagged `perf`, excluded from the normal `test` task): `./gradlew :mmap-region:perfTest` /
  `./gradlew :mmap-queue:perfTest`
- Javadoc: `./gradlew :mmap-region:javadoc` / `./gradlew :mmap-queue:javadoc`

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
that don't treat `0` as unset, `0` where `0` *is* a valid "no-op" value, `null` for boxed/object types). Every
configurator constructor calls `reset()` (never rely on field initializers alone) so a fresh instance's fields start
in that same sentinel state — a configurator built without going through `reset()` first previously caused getters to
silently skip both the `defaults` and system-default fallback and return `0`/`false`/etc. directly, since Java's
implicit zero-value for an uninitialized field doesn't necessarily match the sentinel the getter's guard expects.

## Architecture (`mmap-queue`)

Layered the same way as `mmap-region` but without an `unsafe` package — `mmap-queue` is a consumer of
`mmap-region`'s `api`/`unsafe` layer, not a mapping engine itself. `api` (public contract), `config` (same
Config/Configurator/ConfigImpl/ConfigDefaults pattern as above, four types: `QueueConfig`, `AppenderConfig`,
`ReaderConfig` — shared by poller/entry-reader/entry-iterator, `IndexReaderConfig`), `impl` (everything else).

### Queue structure and file layout

A `Queue` (`QueueImpl`) owns a set of files under one directory, named via `QueueFiles`: a header file
(`<name>_hdr.mmq`), an ID-pool file (`<name>_ids.mmq`, backing appender-ID allocation via `IdPool64`/`IdPool256`
from `mmap-region`), and one payload file per appender ID (`<name>_dat_<id>.mmq`). Each of `Appender`, `Poller`,
`EntryReader`, `EntryIterator`, `IndexReader` maps the header file plus whichever payload files it touches via its
own `ElasticMapping`s (see `AppenderMappings`/`ReaderMappings`/`IndexMappings`) — they don't share mapping state.

The header file is a dense array of fixed-size (`Headers.HEADER_LENGTH` = 8 bytes) header words, one per queue
entry index. Each header word packs an appender ID (low 8 bits) and a payload position (remaining bits, 8-byte
granularity) — see `Headers.header()`/`Headers.appenderId()`/`Headers.payloadPosition()`. Index-to-header-position
mapping is **not** a simple `index * 8`: it goes through an inlined step-bijection (`Headers.headerPositionForIndex`,
mirroring `StepBijection` from `mmap-region`) that deliberately scatters consecutive indices to non-adjacent byte
positions, to avoid false-sharing when multiple appenders write sequentially-indexed entries concurrently. A
consequence worth knowing if you touch `AppenderImpl`: moving from index `i` to index `i+1` is a real mapping
operation (often a region-boundary crossing), not a cheap offset bump — so it's on the hot path of every append,
not a rare corner case.

### Multi-writer append protocol (`AppenderImpl`)

Multiple `Appender` instances (from multiple threads, each with its own instance) can append concurrently to the
same queue. Claiming the next index is lock-free: CAS `NULL_HEADER → headerValue` on successive header slots
starting from the appender's own `endIndex` cursor, advancing past any slot another appender already claimed,
until the CAS succeeds. **Once that CAS succeeds, the entry is durably committed** — nothing after that point may
throw and cause the caller to lose the returned index, and nothing may leave the appender's bookkeeping (`endIndex`)
pointing somewhere its `header` mapping isn't actually positioned (doing so causes the next append to skip an
index permanently — the CAS on a stale/wrong slot fails for the wrong reason and the retry loop moves past the
real target). This bit us once already (see git history on `AppenderImpl.appendEntry`) — treat "committed" as a
one-way door when modifying this method.

### Index/Move sentinels — a sharp edge

`Index` (`NULL`=-1, `FIRST`=0, `MAX`, `LAST`, `END`) and `Move` (`NEXT`, `PREVIOUS`, `NONE`, `FIRST`, `LAST`, `END`)
look parallel but aren't numerically consistent: `Move.LAST == Index.LAST` and `Move.END == Index.END` exactly (so
code resolving those two can pass the raw value through unchanged), but `Move.FIRST` (`Long.MIN_VALUE`) is **not**
equal to `Index.FIRST` (`0`) — it must be explicitly translated wherever it's consumed (see `PollerImpl.nextIndex`).
Don't assume symmetry between the two constant sets without checking.

`Poller.seekLast()`/`seekEnd()` (and an `EntryHandler` returning `Move.LAST`/`Move.END`) don't resolve instantly —
`PollerImpl` walks toward the target one `poll()` call at a time (see `PollerImpl.moveTo`), only pinning down the
concrete index once it actually reaches a `NULL_HEADER` while scanning forward. Don't expect `nextIndex()` to
reflect the real target index immediately after a `seekLast()`/`seekEnd()` call.

### Configuration (`config` + `impl`)

Same four-type pattern as `mmap-region` (`XConfig`/`XConfigurator`/`XConfigImpl`/`XConfigDefaults`), but `ReaderConfig`
is shared by three distinct reader kinds — poller, entry-reader, entry-iterator — through **one** implementation
class, `ReaderConfiguratorImpl`, parameterized by which `ReaderConfigDefaults` singleton (`POLLER_CONFIG_DEFAULTS`/
`ENTRY_READER_CONFIG_DEFAULTS`/`ENTRY_ITERATOR_CONFIG_DEFAULTS`) it's constructed with. Because that class has no
"which kind am I" field, its own system-default fallback (used only via the generic public
`ReaderConfigurator.configure(ReaderConfig)` entry point, when a caller-supplied `defaults` doesn't resolve a value)
can't pick a kind-specific default — it deliberately reuses the entry-reader-specific one
(`QueueConfigurations.defaultEntryReaderHeaderMappingStrategy()` etc.) as a pragmatic generic fallback. Keep that in
mind if `ReaderConfiguratorImpl` ever grows a real per-kind default requirement — it'll need an actual kind field.

`QueueConfigurations` (~630 lines) defines system-property-driven defaults for 9 near-identical header/payload ×
reader-kind combinations (poller, entry-reader, entry-iterator, index-reader, appender — each header/payload pair
gets its own region-size/cache-size/regions-to-map-ahead/mapping-strategy property block). When adding or editing
one of these blocks, diff it carefully against its siblings — copy-paste slips here (wrong prefix on one property
name, wrong field reused in one setter) are exactly the bug shape found repeatedly across this config subsystem
during review; there's no compiler check that a `*HeaderX` getter didn't accidentally read a `*Payload` property.

Known gap, left as-is: the `*_MAPPING_STRATEGY_PROPERTY` system properties (e.g.
`mmap.queue.pollerHeaderMappingStrategy`) are declared but never read — `QueueConfigurations.getMappingStrategyProperty`
always uses the hard-coded `*_MAPPING_STRATEGY_DEFAULT` regardless of what the property is set to (see the `//FIXME`
and commented-out intended implementation in that method). The `regionSize`/`cacheSize`/`regionsToMapAhead`
sub-properties for the same mapping strategy *do* work individually — only the top-level strategy choice is unwired.