[![build](https://github.com/tools4j/mmap/actions/workflows/gradle.yml/badge.svg)](https://github.com/tools4j/mmap/actions/workflows/gradle.yml)
[![Coverage Status](https://coveralls.io/repos/github/tools4j/mmap/badge.svg?branch=master)](https://coveralls.io/github/tools4j/mmap?branch=master)
&nbsp;&nbsp;<sup><b>releases:</b></sup>
[![mmap-mapping](https://img.shields.io/maven-central/v/org.tools4j/mmap-region.svg?label=mmap-region)](https://central.sonatype.com/artifact/org.tools4j/mmap-region)
[![mmap-queue](https://img.shields.io/maven-central/v/org.tools4j/mmap-queue.svg?label=mmap-queue)](https://central.sonatype.com/artifact/org.tools4j/mmap-queue)
&nbsp;&nbsp;<sup><b>javadoc:</b></sup>
[![mmap-region](https://javadoc.io/badge2/org.tools4j/mmap-region/javadoc.svg?label=mmap-region)](https://javadoc.io/doc/org.tools4j/mmap-region)
[![mmap-queue](https://javadoc.io/badge2/org.tools4j/mmap-queue/javadoc.svg?label=mmap-queue)](https://javadoc.io/doc/org.tools4j/mmap-queue)

## tools4j/mmap
Memory mapped files used to implement off-heap low latency utilities such as queues.

The project has two modules:
* **mmap-mapping**: memory-mapped file regions exposed as [Agrona](https://github.com/aeron-io/agrona) buffers, with
  region caching, files rolling over when reaching a maximum size, and mapping/unmapping in a background thread
* **mmap-queue**: a persistent off-heap queue built on mmap-mapping, with concurrent appenders and any number of
  pollers and readers

### Mappings
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
* `MappingConfig` and `MappingStrategyConfig`: file size, rolling files, region size, region caching and async
  mapping/unmapping; defaults can be set via system properties

#### Concurrent Access
Mappings and mapping pools are not thread-safe: each thread needs its own mappings, or its own pool. The mapped
memory itself is shared, however, and the mapping's `AtomicBuffer` supports CAS and memory ordering operations, for
`long` and `int` values:
* CAS: `compareAndSetLong`, `getAndAddLong`, ...
* Memory ordering: `putLongRelease`, `getLongAcquire`, `putLongVolatile`, `getLongVolatile`, ...

Atomic operations require naturally aligned file positions, e.g. a multiple of 8 for `long` values. Threads, and also
processes, can hence coordinate through their own mappings of the same file, as the queue does when appenders claim
the next entry.

#### Quick example
```java
File fixedFile = new File("/tmp/fixed.map");

//FixedMapping: maps a fixed slice of the file, here the first 64 bytes (file is created if necessary)
try (FixedMapping mapping = Mappings.fixedMapping(fixedFile, AccessMode.READ_WRITE, 0, 64)) {
    mapping.buffer().putInt(0, 42);
    mapping.buffer().putInt(60, 43);
}

//without length, the mapping spans from the offset to the end of the file
try (FixedMapping mapping = Mappings.fixedMapping(fixedFile, AccessMode.READ_ONLY, 60)) {
    int available = mapping.bytesAvailable();//4
    int value = mapping.buffer().getInt(0);//43
}
```
Dynamic mappings can be moved within a file, mapping regions of the file on demand:
```java
File file = new File("/tmp/data.map");

//RegionMapping: exposes a whole region of the file and is moved region by region
try (RegionMapping mapping = Mappings.regionMapping(file, AccessMode.READ_WRITE)) {
    for (int region = 0; region < 3; region++) {
        mapping.moveToRegion(region);
        mapping.buffer().putInt(0, 100 * region);
        mapping.buffer().putInt(4, 100 * region + 1);
    }
}

//ElasticMapping: can be moved to any position, its buffer spans to the end of the region
try (ElasticMapping mapping = Mappings.elasticMapping(file, AccessMode.READ_ONLY)) {
    mapping.moveTo(2L * mapping.regionSize());
    int value = mapping.buffer().getInt(0);//200
    mapping.moveBy(4);
    value = mapping.buffer().getInt(0);//201
}

//AdaptiveMapping: maps an arbitrary slice of the underlying file from one region
try (AdaptiveMapping mapping = Mappings.adaptiveMapping(file, AccessMode.READ_ONLY)) {
    int readLength = 8;
    mapping.moveTo(2L * mapping.regionSize(), readLength);//buffer capped at size 8
    int first = mapping.buffer().getInt(0);//200
    int second = mapping.buffer().getInt(4);//201
}

//MappingPool: mappings from the same pool share mapped regions (one pool per thread)
try (MappingPool pool = Mappings.mappingPool(file, AccessMode.READ_ONLY)) {
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
//roll over to a new file every 1MB, map regions ahead in a background thread when accessed sequentially
MappingConfig config = MappingConfig.configure()
        .rollFiles(true)
        .maxFileSize(1 << 20)
        .maxOpenFiles(4)
        .mappingStrategy(cfg -> cfg.asyncMapping(true).regionSize(1 << 16));
File file = new File("/tmp/rolling.map");//creates rolling_0.map, rolling_1.map, ...

try (RegionMapping mapping = Mappings.regionMapping(file, AccessMode.READ_WRITE, config)) {
    for (int region = 0; region < 64; region++) {
        mapping.moveToRegion(region);
        mapping.buffer().putInt(0, region);
    }
}
try (RegionMapping mapping = Mappings.regionMapping(file, AccessMode.READ_ONLY, config)) {
    mapping.moveToRegion(63);
    int value = mapping.buffer().getInt(0);//63
}
```
The full example is in
[QuickIntroTest](mmap-mapping/src/test/java/org/tools4j/mmap/mapping/api/QuickIntroTest.java).

### Queues
Package `org.tools4j.mmap.queue.api` contains the queue types, with `Queue.create(..)` as the main entry point.
Configuration is in `org.tools4j.mmap.queue.config`.

Main classes:
* `Queue`: a queue stored in a directory; creates appenders, pollers and readers, and closes them when it is closed
* `Appender`: appends entries from a byte array or buffer, or zero-copy via `appending(..)` and `AppendingContext`;
  multiple appenders can append to the same queue concurrently
* `Poller`: sequential retrieval of entries via `EntryHandler` callback, which returns how to move to the next entry
  (see `Move`)
* `EntryReader`: random access to entries by index
* `EntryIterator`: iteration over entries, forward or in reverse, from a start index
* `IndexReader`: first and last index, and number of entries
* `QueueConfig`, `AppenderConfig`, `ReaderConfig` and `IndexReaderConfig`: configuration of the queue, appenders and
  readers; defaults can be set via system properties

#### Concurrent Access
Appenders, pollers and readers are not thread-safe: each thread needs its own instances. Multiple appenders can
append to the same queue concurrently, and pollers and readers can read the queue while entries are being appended.

#### Quick example
```java
try (Queue queue = Queue.create(new File("/tmp/myQueue"))) {

    //append: copy from a byte array or buffer, or encode in place (zero-copy) and commit
    Appender appender = queue.createAppender();
    appender.append("Hello".getBytes(StandardCharsets.US_ASCII));
    try (AppendingContext context = appender.appending(64)) {
        int length = context.buffer().putStringWithoutLengthAscii(0, "World");
        context.commit(length);
    }

    //poll: sequential retrieval via callback, the returned move determines the next entry
    Poller poller = queue.createPoller();
    while (poller.poll((index, buffer, offset, length) -> {
        System.out.println(index + ":" + buffer.getStringWithoutLengthAscii(offset, length));//0:Hello, 1:World
        return Move.NEXT;
    }) > 0);

    //read: random access by index
    EntryReader reader = queue.createEntryReader();
    try (ReadingContext entry = reader.reading(1)) {
        if (entry.hasEntry()) {
            DirectBuffer buffer = entry.buffer();//World
        }
    }

    //iterate: forward or reverse from a start index
    EntryIterator iterator = queue.createEntryIterator();
    try (IterableContext entries = iterator.readingFromLast().reverse()) {
        for (Entry entry : entries) {
            DirectBuffer buffer = entry.buffer();//World, Hello
        }
    }

    //index: first and last index, size
    IndexReader indexReader = queue.createIndexReader();
    long first = indexReader.firstIndex();//0
    long last = indexReader.lastIndex();//1
    long size = indexReader.size();//2
}
```
The full example is in
[QuickIntroTest](mmap-queue/src/test/java/org/tools4j/mmap/queue/api/QuickIntroTest.java).

### Maven/Gradle

Requires Java 17 or later, and the following JVM options:
```
--add-opens=java.base/sun.nio.ch=ALL-UNNAMED
--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED
```
`mmap-queue` includes `mmap-mapping`; use `mmap-mapping` alone if you only need mappings.

#### Maven
```xml
<dependency>
    <groupId>org.tools4j</groupId>
    <artifactId>mmap-queue</artifactId>
    <version>1.1</version>
</dependency>
```

#### Gradle
```groovy
implementation 'org.tools4j:mmap-queue:1.1'
```

Release 1.0 was published as `mmap-region` (now `mmap-mapping`) and `mmap-queue`.

### Download
You can download binaries, sources and javadoc from Maven Central:
* [mmap-mapping](https://central.sonatype.com/artifact/org.tools4j/mmap-mapping)
* [mmap-queue](https://central.sonatype.com/artifact/org.tools4j/mmap-queue)

### API Javadoc
API documentation of the latest release:
* [mmap-mapping](https://javadoc.io/doc/org.tools4j/mmap-mapping)
* [mmap-queue](https://javadoc.io/doc/org.tools4j/mmap-queue)

### Notes
* Licensed under the [MIT License](LICENSE.md).
* AI assistance (Claude) was used in developing this repository; all changes are reviewed by the maintainers.
