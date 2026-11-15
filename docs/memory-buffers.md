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

### Features & Characteristics
| Functionality | mmap-core<br>`MemoryBuffer` | Agrona<br>`UnsafeBuffer` | Chronicle<br>`Bytes` | Netty<br>`ByteBuf` | JDK<br>`ByteBuffer` |
|---|:-:|:-:|:-:|:-:|:-:|
| Wrap<br><sub>(raw address)</sub> | ✅ | ✅ | ✅ | ✅<br><sub>(via `ByteBuffer`)</sub> | – |
| Wrap<br><sub>(`byte[]`,&nbsp;`ByteBuffer`)</sub> | ✅ | ✅ | ✅ | ✅ | <sub>`byte[]` only</sub> |
| Wrap<br><sub>(without&nbsp;allocating)</sub> | ✅<br><sub>&nbsp;</sub> | ✅<br><sub>&nbsp;</sub> | ✅<br><sub>(direct&nbsp;only)</sub> | – | – |
| Positions | none (absolute indices only) | none (absolute indices only) | read&nbsp;/&nbsp;write position&nbsp;and&nbsp;limit | reader&nbsp;/&nbsp;writer index, with marks | position,&nbsp;limit and&nbsp;mark |
| Expandable | ✅<br><sub>(heap&nbsp;and&nbsp;direct)</sub> | ✅<br><sub>(no&nbsp;ordered&nbsp;access)</sub> | ✅<br><sub>(elastic)</sub> | ✅<br><sub>(up&nbsp;to&nbsp;`maxCapacity`)</sub> | – |
| Alignment | ✅<br>direct, also expandable and kept when growing<p><sub>(8 bytes by default, or any multiple of 8)</sub> |  ☑️<br>direct only<br><br><br><p><sub>(expandable ones are not aligned)</sub> | – | ☑️<br>pooled&nbsp;direct&nbsp;only<br>also&nbsp;when&nbsp;growing<br><br><p><sub>(opt-in, not with the default allocator)</sub> | ☑️<br>direct only<br><br><br><p><sub>via `alignedSlice(..)`</sub> |
| Byte&nbsp;order | little endian,<br>any per call | native,<br>any per call | native | big endian,<br><sub>or `…LE` methods</sub> | big endian,<br>any per buffer |
| Ordered&nbsp;int/long | ✅<br>opaque, acquire/release, volatile | ✅<br>opaque, acquire/release, volatile | ☑️<br><br>ordered write,<br>volatile | – | – |
| Atomic&nbsp;int/long | ✅<br>CAS, compare-and-exchange, get-and-add/set | ✅<br>CAS, compare-and-exchange, get-and-add/set | ✅<br>CAS, add-and-get, test-and-set<br>&nbsp; | – | – |
| Volatile&nbsp;byte/short/char | ✅ | ✅ | ✅<br><sub>(byte/short)</sub> | – | – |
| Ordered&nbsp;on&nbsp;heap | <sub>`long[]`<br>(aligned)</sub> | <sub>`byte[]`<br>(unaligned)</sub> | <sub>`byte[]`<br>(unaligned)</sub> | – | – |
| Memory&nbsp;freed&nbsp;by | garbage collector | garbage collector | reference counting | reference counting | garbage collector |
| Access&nbsp;through | Unsafe or FFM | Unsafe | Unsafe | var handles<br><sub>(Java 24+ default)</sub><br>or Unsafe | JDK |

### Safety & Exceptions
| On misuse | mmap-core<br>`MemoryBuffer` | Agrona<br>`UnsafeBuffer` | Chronicle<br>`Bytes` | Netty<br>`ByteBuf` | JDK<br>`ByteBuffer` |
|---|:-:|:-:|:-:|:-:|:-:|
| Bounds&nbsp;checks | always<br><sub>(intrinsic)</sub> | by default<br><sub>(can be disabled via system property)</sub> | by default<br><sub>(can be disabled via system property)</sub> | by default<br><sub>(can be disabled via system property)</sub> | always<br><sub>(intrinsic)</sub> |
| Write&nbsp;to&nbsp;read&#x2011;only memory | <sub><code>ReadOnly&#8203;BufferException</code></sub><p><br>(details see <sup>#1</sup>) | not checked<p><sub>(writes through to<br>read-only byte buffers; `InternalError` from the JVM's fault handler on read-only mapped memory)</sub> | throws for read-only mapped files | <sub><code>ReadOnly&#8203;BufferException</code></sub> | <sub><code>ReadOnly&#8203;BufferException</code></sub> |
| Misaligned ordered&nbsp;access | <sub><code>IllegalState&#8203;Exception</code></sub><br><br><sub>(can be disabled via system property)</sub> | not checked<p><sub>(crashes the JVM on ARM across a 16-byte boundary)</sub><p><sub>(calling `verifyAlignment()` checks only the buffer start)</sub> | throws on ARM only | n/a | n/a |
| Ordered&nbsp;on wrapped&nbsp;`byte[]` | <sub><code>Unsupported&#8203;OperationException</code></sub> | allowed <br><sub>(calling `verifyAlignment()` warns on x64 and throws elsewhere)</sub> | allowed | n/a | n/a |
| Read-only&nbsp;vs mutable type | <sub>`MemoryBuffer` and<br>`MutableMemoryBuffer`</sub> | <sub>`DirectBuffer` and<br>`MutableDirectBuffer`</sub> | <sub>`BytesIn` and<br>`RandomDataInput`<br>(with `Bytes` always mutable)</sub> | <sub>runtime only</sub> | <sub>runtime only</sub> |

<sup>#1</sup>**Throws:** (i) for memory buffers wrapping read-only byte buffers, or (ii) if explicitly made read-only with `makeReadOnly()`. This also applies to buffers returned by read-only mappings (from v3.0 on).<br>
<sup>&nbsp;&nbsp;&nbsp;&nbsp;</sup>**Does not throw:** Memory wrapped by its raw address with `MemoryBufferFlyweight.wrap(long, int)` is writable until made read-only with `makeReadOnly()`, so writing to read-only mapped memory wrapped that way is not caught. Note that this `wrap(..)` method is marked with `@Unsafe`.

The behaviour of Agrona on misuse was checked on macOS on aarch64 with Java 25.
