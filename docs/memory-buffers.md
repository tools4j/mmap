# Memory buffers
Package `org.tools4j.mmap.core.buffer` of `mmap-core`, with `MemoryBuffers` as the entry point:
* flyweights wrapping direct memory, byte buffers and arrays, and buffers of fixed or expandable capacity, on the heap
  or direct
* little endian unless another byte order is given, all accesses bounds checked
* ordered and atomic accesses (`putLongRelease`, `getLongAcquire`, `compareAndSetLong`, ...) on direct memory and on
  heap memory backed by a `long[]`, as allocated by `MemoryBuffers.allocate(..)`, at naturally aligned positions
* memory access through `jdk.internal.misc.Unsafe` by default, or through the Foreign Function and Memory API on
  Java 22 or later with `-Dmmap.core.memoryAccess=FFM` (plus `--enable-native-access=ALL-UNNAMED` to avoid a warning,
  and `--add-opens=java.base/java.nio=ALL-UNNAMED` to access byte buffers without allocating)
* configuration shared by all modules, e.g. the default error listener (`mmap.core.errorListener`), in
  `org.tools4j.mmap.core.config.CoreConfigurations`

## Quick example
```java
//direct memory, freed by the garbage collector once the buffer is no longer referenced
MutableMemoryBuffer buffer = MemoryBuffers.allocateDirect(64);
buffer.putLong(8, 42);//payload, plain write
buffer.putLongRelease(0, 1);//header, release write publishing the payload
if (buffer.getLongAcquire(0) == 1) {
    long value = buffer.getLong(8);//42
}

//a flyweight wraps existing memory without copying, and can be wrapped again
MemoryBufferFlyweight flyweight = MemoryBuffers.flyweight();
flyweight.wrap(new byte[16]);
flyweight.putInt(0, 7);
```

## Comparison with other buffers
Features of the memory buffers compared with those of the other buffers in our
[microbenchmarks](benchmarks.md#microbenchmarks): [Agrona](https://github.com/aeron-io/agrona)'s `UnsafeBuffer`,
[Chronicle Bytes](https://github.com/OpenHFT/Chronicle-Bytes), [Netty](https://github.com/netty/netty)'s `ByteBuf`
and the JDK's `ByteBuffer`, as of Agrona 2.7.1, Chronicle Bytes 2026.4 and Netty 4.2.20.

| Functionality | mmap-core<br>`MemoryBuffer` | Agrona<br>`UnsafeBuffer` | Chronicle<br>`Bytes` | Netty<br>`ByteBuf` | JDK<br>`ByteBuffer` |
|---|---|---|---|---|---|
| Wraps&nbsp;an&nbsp;address | ✅ | ✅ | ✅ | ✅ (through a `ByteBuffer`) | – |
| Wraps&nbsp;`byte[]`,&nbsp;`ByteBuffer` | ✅ | ✅ | ✅ | ✅ | `byte[]` only |
| Re-wraps&nbsp;without&nbsp;allocating | ✅ | ✅ | ✅ (direct only) | – | – |
| Positions | none (absolute indices only) | none (absolute indices only) | read and write position and limit | reader and writer index, with marks | position, limit and mark |
| Expandable | ✅ (heap and direct) | ✅ (no ordered access) | ✅ (elastic) | ✅ (up to `maxCapacity`) | – |
| Byte&nbsp;order | little endian, any per call | native, any per call | native | big endian, `…LE` methods | big endian, per buffer |
| Ordered&nbsp;int/long | opaque, acquire/release, volatile | opaque, acquire/release, volatile | volatile, ordered write | – | – |
| Atomic&nbsp;int/long | CAS, compare-and-exchange, get-and-add/set | CAS, compare-and-exchange, get-and-add/set | CAS, add-and-get, test-and-set | – | – |
| Volatile&nbsp;byte/short/char | ✅ | ✅ | byte/short | – | – |
| Ordered&nbsp;on&nbsp;heap | `long[]` (aligned) | `byte[]` (unaligned) | `byte[]` (unaligned) | – | – |
| Memory&nbsp;freed&nbsp;by | garbage collector | garbage collector | reference counting | reference counting | garbage collector |
| Access&nbsp;through | Unsafe or FFM | Unsafe | Unsafe | var handles (Java 24+ default) or Unsafe | JDK |

| On misuse | mmap-core<br>`MemoryBuffer` | Agrona<br>`UnsafeBuffer` | Chronicle<br>`Bytes` | Netty<br>`ByteBuf` | JDK<br>`ByteBuffer` |
|---|---|---|---|---|---|
| Bounds&nbsp;checks | always (intrinsic) | by default (`agrona.disable.bounds.checks`) | by default (`bytes.bounds.unchecked`) | by default (`io.netty.buffer.checkBounds`) | always |
| Write&nbsp;to&nbsp;read-only&nbsp;memory | `ReadOnlyBufferException`<sup>1</sup> | not checked: writes through to read-only byte buffers; `InternalError` from the JVM's fault handler on read-only mapped memory | throws for read-only mapped files | `ReadOnlyBufferException` | `ReadOnlyBufferException` |
| Misaligned&nbsp;ordered&nbsp;access | `IllegalStateException` (check can be disabled) | not checked: crashes the JVM on ARM across a 16-byte boundary; `verifyAlignment()` checks only the buffer start, if called | throws on ARM only | n/a | n/a |
| Ordered&nbsp;on&nbsp;`byte[]` | `UnsupportedOperationException` | allowed; `verifyAlignment()` warns on x64 and throws elsewhere, if called | allowed | n/a | n/a |
| Read-only&nbsp;type | `MemoryBuffer` and `MutableMemoryBuffer` | `DirectBuffer` and `MutableDirectBuffer` | `RandomDataInput` and `BytesIn` (`Bytes` always mutable) | runtime only | runtime only |

<sup>1</sup> For memory wrapped as read-only: read-only byte buffers, buffers made read-only with `makeReadOnly()`, and
from 3.0 on the buffers of read-only mappings. Memory wrapped by its raw address with `MemoryBufferFlyweight.wrap(long,
int)`, marked `@Unsafe`, is writable until made read-only with `makeReadOnly()`, so writing to read-only mapped memory
wrapped that way is not caught.

The behaviour of Agrona on misuse was checked on macOS on aarch64 with Java 25.
