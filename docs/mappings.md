# Mappings
Package `org.tools4j.mmap.mapping.api` contains the mapping types and the `Mappings` factory, the main entry point.
Configuration is in `org.tools4j.mmap.mapping.config`. The low-level mapping engine in
`org.tools4j.mmap.mapping.unsafe` is not intended for use by application code.

Main classes:
* `Mappings`: factory methods for all mapping types and mapping pools
* `Mapping`: a block of a file mapped into memory, accessed via its `buffer()`, an Agrona `AtomicBuffer`
* `FixedMapping`: a mapping with fixed start and end position
* `RegionMapping`: a mapping of exactly one region, moved region by region
* `ElasticMapping`: a mapping that can be moved to any position, its buffer spans to the end of the region
* `AdaptiveMapping`: a mapping that can be moved to any position with an explicit length within a region
* `MappingPool`: provides many mappings of the same file that share the underlying mapped regions
* `OpenMode`: how a file is opened: read-only (optionally before it exists), read/write, create, open or create,
  or recreate
* `MappingConfig` and `MappingStrategyConfig`: file size, rolling files, region size, region caching and async
  mapping/unmapping; defaults can be set via system properties

## Concurrent Access
Mappings and mapping pools are not thread-safe: each thread needs its own mappings, or its own pool. The mapped
memory itself is shared, however, and the mapping's `AtomicBuffer` supports CAS and memory ordering operations, for
`long` and `int` values:
* CAS: `compareAndSetLong`, `getAndAddLong`, ...
* Memory ordering: `putLongRelease`, `getLongAcquire`, `putLongVolatile`, `getLongVolatile`, ...

Atomic operations require naturally aligned file positions, e.g. a multiple of 8 for `long` values. Threads, and also
processes, can hence coordinate through their own mappings of the same file, as the queue does when appenders claim
the next entry.

## Quick example
```java
File file = new File("/tmp/fixed.map");

//FixedMapping: maps a fixed slice of the file, here the first 64 bytes
try (FixedMapping mapping = Mappings.fixedMapping(file, OpenMode.OPEN_OR_CREATE, 0, 64)) {
    mapping.buffer().putInt(0, 42);
    mapping.buffer().putInt(60, 43);
}

//without length, the mapping spans from the offset to the end of the file
try (FixedMapping mapping = Mappings.fixedMapping(file, OpenMode.READ_ONLY, 60)) {
    int available = mapping.bytesAvailable();//4
    int value = mapping.buffer().getInt(0);//43
}
```
Dynamic mappings can be moved within a file, mapping regions of the file on demand:
```java
File file = new File("/tmp/data.map");

//RegionMapping: exposes a whole region of the file and is moved region by region
try (RegionMapping mapping = Mappings.regionMapping(file, OpenMode.OPEN_OR_CREATE)) {
    for (int region = 0; region < 3; region++) {
        mapping.moveToRegion(region);
        mapping.buffer().putInt(0, 100 * region);
        mapping.buffer().putInt(4, 100 * region + 1);
    }
}

//ElasticMapping: can be moved to any position, its buffer spans to the end of the region
try (ElasticMapping mapping = Mappings.elasticMapping(file, OpenMode.READ_ONLY)) {
    mapping.moveTo(2L * mapping.regionSize());
    int value = mapping.buffer().getInt(0);//200
    mapping.moveBy(4);
    value = mapping.buffer().getInt(0);//201
}

//AdaptiveMapping: maps an arbitrary slice of the underlying file from one region
try (AdaptiveMapping mapping = Mappings.adaptiveMapping(file, OpenMode.READ_ONLY)) {
    int readLength = 8;
    mapping.moveTo(2L * mapping.regionSize(), readLength);//buffer capped at size 8
    int first = mapping.buffer().getInt(0);//200
    int second = mapping.buffer().getInt(4);//201
}

//MappingPool: mappings from the same pool share mapped regions (one pool per thread)
try (MappingPool pool = Mappings.mappingPool(file, OpenMode.READ_ONLY)) {
    ElasticMapping elastic = pool.acquireElasticMapping();
    AdaptiveMapping adaptive = pool.acquireAdaptiveMapping();
    elastic.moveToFirstRegion();
    adaptive.moveTo(4, Integer.BYTES);
    int first = elastic.buffer().getInt(0);//0
    int second = adaptive.buffer().getInt(0);//1
}
```
Rolling files with async mapping, i.e. regions mapped ahead in a background thread for sequential access:
```java
//roll file every 1MB, map regions ahead in a background thread when accessed sequentially
MappingConfig config = MappingConfig.configure()
        .rollFiles(true)
        .maxFileSize(1 << 20)
        .maxOpenFiles(4)
        .regionSize(1 << 16)
        .mappingStrategy(cfg -> cfg.asyncMapping(true));
File file = new File("/tmp/rolling.map");//creates rolling_0.map, rolling_1.map, ...

try (var mapping = Mappings.regionMapping(file, OpenMode.OPEN_OR_CREATE, config)) {
    for (int region = 0; region < 64; region++) {
        mapping.moveToRegion(region);
        mapping.buffer().putInt(0, region);
    }
}
try (var mapping = Mappings.regionMapping(file, OpenMode.READ_ONLY, config)) {
    mapping.moveToRegion(63);
    int value = mapping.buffer().getInt(0);//63
}
```
The full example is in
[MappingIntroTest](../mmap-mapping/src/test/java/org/tools4j/mmap/mapping/api/MappingIntroTest.java).
