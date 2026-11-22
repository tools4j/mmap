# Mappings
Package `org.tools4j.mmap.mapping.api` contains the mapping types and the `Mappings` factory, the main entry point.
Configuration is in `org.tools4j.mmap.mapping.config`. The low-level mapping engine in
`org.tools4j.mmap.mapping.unsafe` is not intended for use by application code.

Main classes:
* `Mappings`: factory methods for all mapping types and mapping pools
* `Mapping`: a block of a file mapped into memory for reading, accessed via its `buffer()`, a `MemoryBuffer` of
  mmap-core
* `MutableMapping`: a mapping also for writing, its `buffer()` a `MutableMemoryBuffer`
* `FixedMapping`: a mapping with fixed start and end position, `FixedMutableMapping` its mutable variant
* `DynamicMapping`: a mapping of a slice of the file that can be moved to any position, with a length from zero up to
  the end of the overlap of the region the position lies in (see [Region overlap](#region-overlap));
  moves map the length last requested, by default `DynamicMapping.STRETCH_LENGTH`, all bytes to the end of the
  overlap, and throw if an explicit length does not fit; `DynamicMutableMapping` its mutable variant
* `MappingPool`: provides many mappings of the same file that share the underlying mapped regions,
  `MutableMappingPool` mutable ones
* `OpenMode`: how a file is opened, a `ReadOnlyOpenMode` (`READ_ONLY`, `READ_ONLY_LAZY` before it exists) for
  mappings, or a `ReadWriteOpenMode` (`READ_WRITE`, `CREATE`, `OPEN_OR_CREATE`, `RECREATE`) for mutable mappings,
  e.g. `Mappings.dynamicMapping(file, OpenMode.READ_ONLY)` and
  `Mappings.dynamicMutableMapping(file, OpenMode.OPEN_OR_CREATE)`; the other kind of open mode does not compile
* `MappingConfig` and `MappingStrategyConfig`: file size, rolling files, region size, overlap size, region caching and
  async mapping/unmapping; defaults can be set via system properties

## Region overlap
Dynamic mappings map the file in regions of the region size, and every region with an overlap beyond its end: the
region at position `k * regionSize` is mapped with `regionSize + overlapSize` bytes, the overlap being the first bytes
of the next region, mapped a second time. A slice can extend to the end of the overlap, so that at least the overlap
size of contiguous bytes is accessible from any position, also across a region end. `safeLength(alignment)` is the
length that fits at every position that is a multiple of the alignment, the overlap size plus the alignment.

The last region of a file has its overlap too, so that the safe length holds at every position: **files are physically
larger than the max file size by the overlap size.** The max file size is the size of the data a file holds, and the
extra bytes at its end are only accessible through the overlap of its last region. With rolling files, the next file
starts with its own bytes: the tail of a file is not continued in the next one, so data written across the max file
size stays in the file it started in, and is read back through the same region.

The overlap size is 64 KB by default (system property `mmap.mapping.overlapSize`), limited to the region size unless
set explicitly through `MappingConfigurator.overlapSize(..)`; it is a multiple of the OS page size, at most the region
size, and zero for no overlap.

**A file must be read with an overlap size no larger than the one it was written with.** A region can only be mapped
for reading once the file covers the region plus the reader's overlap, so with a larger overlap, the last region of a
file never becomes available: the writer does not grow the file beyond its own overlap. A smaller overlap is fine, but
limits the contiguous bytes accessible to the reader's overlap. A writable mapping with a larger overlap than a file was
created with grows the file by the difference.

## Concurrent Access
Mappings and mapping pools are not thread-safe: each thread needs its own mappings, or its own pool. The mapped
memory itself is shared, however, and the mapping's buffer supports CAS and memory ordering operations, for
`long` and `int` values:
* CAS: `compareAndSetLong`, `getAndAddLong`, ...
* Memory ordering: `putLongRelease`, `getLongAcquire`, `putLongVolatile`, `getLongVolatile`, ...

Atomic operations require naturally aligned file positions, e.g. a multiple of 8 for `long` values. Threads, and also
processes, can hence coordinate through their own mappings of the same file, as the queue does when appenders claim
the next entry.

## Quick example
```java
File file = new File("/tmp/fixed.map");

//fixed mutable mapping: maps a fixed slice of the file for writing, here the first 64 bytes
try (MutableMapping mapping = Mappings.fixedMutableMapping(file, OpenMode.OPEN_OR_CREATE, 0, 64)) {
    mapping.buffer().putInt(0, 42);
    mapping.buffer().putInt(60, 43);
}

//fixed mapping for reading only; without length, it spans from the offset to the end of the file
try (Mapping mapping = Mappings.fixedMapping(file, OpenMode.READ_ONLY, 60)) {
    int available = mapping.bytesAvailable();//4
    int value = mapping.buffer().getInt(0);//43
}
```
Dynamic mappings can be moved within a file, mapping regions of the file on demand:
```java
File file = new File("/tmp/data.map");
long mb = 1 << 20;

//DynamicMutableMapping: maps a slice of the file that can be moved, here spanning to the end of the overlap
try (DynamicMutableMapping mapping = Mappings.dynamicMutableMapping(file, OpenMode.OPEN_OR_CREATE)) {
    for (int i = 0; i < 10; i++) {
        mapping.moveTo(i * mb, DynamicMapping.STRETCH_LENGTH);
        mapping.buffer().putInt(0, 100 * i);
        mapping.buffer().putInt(4, 100 * i + 1);
    }
}

//DynamicMapping for reading only; moves keep the length, here 4 bytes for one int after another
try (DynamicMapping mapping = Mappings.dynamicMapping(file, OpenMode.READ_ONLY)) {
    mapping.moveTo(5 * mb, Integer.BYTES);
    int value = mapping.buffer().getInt(0);//500
    mapping.moveBy(Integer.BYTES);
    value = mapping.buffer().getInt(0);//501, length still 4
}

//MappingPool: mappings from the same pool share mapped regions (one pool per thread)
try (MappingPool pool = Mappings.mappingPool(file, OpenMode.READ_ONLY)) {
    DynamicMapping first = pool.acquireMapping();
    DynamicMapping second = pool.acquireMapping();
    first.moveTo(0);
    second.moveTo(4, Integer.BYTES);
    int firstValue = first.buffer().getInt(0);//0
    int secondValue = second.buffer().getInt(0);//1
}
```
Rolling files with async mapping, i.e. regions mapped ahead in a background thread for sequential access:
```java
//roll file every 1MB, map regions ahead in a background thread when accessed sequentially
int regionSize = 1 << 16;
MappingConfig config = MappingConfig.configure()
        .rollFiles(true)
        .maxFileSize(1 << 20)
        .maxOpenFiles(4)
        .regionSize(regionSize)
        .mappingStrategy(cfg -> cfg.asyncMapping(true));
File file = new File("/tmp/rolling.map");//creates rolling_0.map, rolling_1.map, ...

try (var mapping = Mappings.dynamicMutableMapping(file, OpenMode.OPEN_OR_CREATE, config)) {
    for (int region = 0; region < 64; region++) {
        mapping.moveTo((long) region * regionSize);
        mapping.buffer().putInt(0, region);
    }
}
try (var mapping = Mappings.dynamicMapping(file, OpenMode.READ_ONLY, config)) {
    mapping.moveTo(63L * regionSize);
    int value = mapping.buffer().getInt(0);//63
}
```
The full example is in
[MappingIntroTest](../mmap-mapping/src/test/java/org/tools4j/mmap/mapping/api/MappingIntroTest.java).
