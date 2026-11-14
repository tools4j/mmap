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

| Functionality                  | mmap-core `MemoryBuffer`                            | Agrona `UnsafeBuffer`                    | Chronicle `Bytes`                  | Netty `ByteBuf`                        | JDK `ByteBuffer`      |
|--------------------------------|-----------------------------------------------------|------------------------------------------|------------------------------------|----------------------------------------|-----------------------|
| Wraps an address               | ✓                                                   | ✓                                        | ✓                                  | ✓, through a `ByteBuffer` created for it | –                   |
| Wraps `byte[]` and `ByteBuffer` | ✓                                                  | ✓                                        | ✓                                  | ✓                                      | `byte[]` only         |
| Re-wrapping without allocation | ✓                                                   | ✓                                        | direct memory only                 | –                                      | –                     |
| Expandable                     | ✓, heap and direct                                  | ✓, `Expandable…` without ordered access  | ✓, elastic                         | ✓, up to `maxCapacity`                 | –                     |
| Byte order                     | little endian, or any per call                      | native, or any per call                  | native                             | big endian, `…LE` methods              | big endian, per buffer |
| Ordered int and long           | opaque, acquire/release, volatile                   | opaque, acquire/release, volatile        | volatile, ordered write            | –                                      | –                     |
| Atomic int and long            | CAS, compare-and-exchange, get-and-add, get-and-set | the same                                 | CAS, add-and-get, test-and-set     | –                                      | –                     |
| Volatile byte, short and char  | ✓                                                   | ✓                                        | byte and short                     | –                                      | –                     |
| Ordered access on heap memory  | `long[]`-backed, always aligned                     | `byte[]`, alignment not guaranteed       | `byte[]`, alignment not guaranteed | –                                      | –                     |
| Memory released by             | the garbage collector                               | the garbage collector                    | reference counting                 | reference counting                     | the garbage collector |
| Memory accessed through        | Unsafe, or FFM (public API)                         | Unsafe                                   | Unsafe                             | Unsafe, or var handles (Java 24+ default) | the JDK            |

| On misuse                           | mmap-core                                    | Agrona                                                                                                   | Chronicle                         | Netty                                           | JDK `ByteBuffer`          |
|-------------------------------------|----------------------------------------------|----------------------------------------------------------------------------------------------------------|-----------------------------------|-------------------------------------------------|---------------------------|
| Bounds checks                       | always, intrinsic                            | by default, `agrona.disable.bounds.checks`                                                               | by default, `bytes.bounds.unchecked` | by default, `io.netty.buffer.checkBounds`    | always                    |
| Writing read-only memory            | `ReadOnlyBufferException`<sup>1</sup>        | not checked: writes through to read-only byte buffers; an `InternalError` from the JVM's fault handler on read-only mapped memory | throws for read-only mapped files | `ReadOnlyBufferException` | `ReadOnlyBufferException` |
| Misaligned ordered or atomic access | `IllegalStateException`, check can be disabled | not checked: crashes the JVM on ARM across a 16-byte boundary; `verifyAlignment()` checks only the start of the buffer, if called | throws on ARM only               | n/a                                             | n/a                       |
| Ordered access on `byte[]` memory   | `UnsupportedOperationException`              | allowed; `verifyAlignment()` warns on x64 and throws elsewhere, if called                                | allowed                           | n/a                                             | n/a                       |
| Read-only in the type               | `MemoryBuffer` and `MutableMemoryBuffer`     | `DirectBuffer` and `MutableDirectBuffer`                                                                 | `RandomDataInput` and `BytesIn`, but `Bytes` is always mutable | at runtime only                                 | at runtime only           |

<sup>1</sup> For memory wrapped as read-only: read-only byte buffers, buffers made read-only with `makeReadOnly()`, and
from 3.0 on the buffers of read-only mappings. Memory wrapped by its raw address with `MemoryBufferFlyweight.wrap(long,
int)`, marked `@Unsafe`, is writable until made read-only with `makeReadOnly()`, so writing to read-only mapped memory
wrapped that way is not caught.

The behaviour of Agrona on misuse was checked on macOS on aarch64 with Java 25.
