[![build](https://github.com/tools4j/mmap/actions/workflows/gradle.yml/badge.svg)](https://github.com/tools4j/mmap/actions/workflows/gradle.yml)
[![Coverage Status](https://coveralls.io/repos/github/tools4j/mmap/badge.svg?branch=master)](https://coveralls.io/github/tools4j/mmap?branch=master)
&nbsp;&nbsp;<sup><b>releases:</b></sup>
[![mmap-mapping](https://img.shields.io/maven-central/v/org.tools4j/mmap-mapping.svg?label=mmap-mapping)](https://central.sonatype.com/artifact/org.tools4j/mmap-mapping)
[![mmap-queue](https://img.shields.io/maven-central/v/org.tools4j/mmap-queue.svg?label=mmap-queue)](https://central.sonatype.com/artifact/org.tools4j/mmap-queue)
&nbsp;&nbsp;<sup><b>javadoc:</b></sup>
[![mmap-mapping](https://javadoc.io/badge2/org.tools4j/mmap-mapping/javadoc.svg?label=mmap-mapping)](https://javadoc.io/doc/org.tools4j/mmap-mapping)
[![mmap-queue](https://javadoc.io/badge2/org.tools4j/mmap-queue/javadoc.svg?label=mmap-queue)](https://javadoc.io/doc/org.tools4j/mmap-queue)

## tools4j/mmap
Memory mapped files used to implement off-heap low latency utilities such as queues.

The project has three library modules, and a benchmark:
* **mmap-core**: the basic types shared by the other modules (closeables, error and lifecycle listeners, logging, idle
  strategies, configuration), and memory buffers over direct and heap memory, to replace
  [Agrona](https://github.com/aeron-io/agrona)'s buffers in the other modules; see
  [Memory buffers](docs/memory-buffers.md)
* **mmap-mapping**: memory-mapped file regions exposed as Agrona buffers, with region caching, files rolling over when
  reaching a maximum size, and mapping/unmapping in a background thread; see [Mappings](docs/mappings.md)
* **mmap-queue**: a persistent off-heap queue built on mmap-mapping, with concurrent appenders and any number of
  pollers and readers; see [Queues](docs/queues.md)
* **mmap-perf**: a command line benchmark comparing the latency of the mmap queue with Chronicle Queue, and (from
  source, not published) microbenchmarks of the memory buffers, see [Benchmarks](docs/benchmarks.md)

### Quick start
Map a block of a file and write to it, through the mapping's buffer:
```java
try (FixedMapping mapping = Mappings.fixedMapping(new File("/tmp/fixed.map"), OpenMode.OPEN_OR_CREATE, 0, 64)) {
    mapping.buffer().putInt(0, 42);
}
```
Append to a queue and poll its entries:
```java
try (Queue queue = Queue.open(new File("/tmp/myQueue"))) {
    queue.createAppender().append("Hello".getBytes(StandardCharsets.US_ASCII));
    Poller poller = queue.createPoller();
    while (poller.poll((index, buffer, offset, len) -> {
        System.out.println(index + ":" + buffer.getStringWithoutLengthAscii(offset, len));//0:Hello
        return Move.NEXT;
    }) > 0);
}
```
More in [Mappings](docs/mappings.md), [Queues](docs/queues.md), [Memory buffers](docs/memory-buffers.md) and
[Benchmarks](docs/benchmarks.md).

### Maven/Gradle

Requires Java 17 or later, and the following JVM options:
```
--add-opens=java.base/sun.nio.ch=ALL-UNNAMED
--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED
```
`mmap-queue` includes `mmap-mapping`, which includes `mmap-core`; use `mmap-mapping` alone if you only need
mappings, or `mmap-core` alone for the memory buffers.

#### Maven
```xml
<dependency>
    <groupId>org.tools4j</groupId>
    <artifactId>mmap-queue</artifactId>
    <version>2.1</version>
</dependency>
```

#### Gradle
```groovy
implementation 'org.tools4j:mmap-queue:2.1'
```

See the [changelog](CHANGELOG.md) for the changes per release.

### Download
You can download binaries, sources and javadoc from Maven Central:
* [mmap-mapping](https://central.sonatype.com/artifact/org.tools4j/mmap-mapping)
* [mmap-queue](https://central.sonatype.com/artifact/org.tools4j/mmap-queue)
* [mmap-perf](https://central.sonatype.com/artifact/org.tools4j/mmap-perf)

### API Javadoc
API documentation of the latest release:
* [mmap-mapping](https://javadoc.io/doc/org.tools4j/mmap-mapping)
* [mmap-queue](https://javadoc.io/doc/org.tools4j/mmap-queue)
* [mmap-perf](https://javadoc.io/doc/org.tools4j/mmap-perf)

### Notes
* Licensed under the [MIT License](LICENSE.md).
* AI assistance (Claude) was used in developing this repository; all changes are reviewed by the maintainers.
