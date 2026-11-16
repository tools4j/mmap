# JVM options
mmap runs on Java 17 or later. It needs one JVM option in all cases, and further ones only for optional features.

## Required
```
--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED
```
`--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED` works too. Without it, creating the first mapping or queue fails
with an `IllegalAccessError`.

The option gives mmap access to `jdk.internal.misc.Unsafe`, used for:
* the default memory access of the memory buffers (`mmap.core.memoryAccess=UNSAFE`)
* the OS page size, and touching pages when mapping ahead
* the JDK's trusted lookup, which reaches the JDK internals for mapping files without opening further packages:

| JDK internal | Used for | Java |
|---|---|---|
| `sun.nio.ch.FileChannelImpl.map0` and `unmap0` | mapping and unmapping file regions | 17 |
| `sun.nio.ch.FileDispatcher.map` and `unmap` | mapping and unmapping file regions | 21+ |
| `sun.nio.ch.FileChannelImpl.initIDs()` | the granularity of mappings, which region sizes must be a multiple of | 17 |
| `sun.nio.ch.FileDispatcher.allocationGranularity()` | ditto | 21+ |

The option is required also when memory is accessed through the Foreign Function and Memory API (FFM, see below). Not
needing JDK internals at all with FFM on Java 22 or later is planned for a 3.x release, mapping files through the public
`FileChannel.map(.., Arena)` and querying the page size through FFM; only the native access option would then be needed.

## Optional features

| Feature | Java, platform | JVM option | Without it |
|---|---|---|---|
| Memory access through FFM (`-Dmmap.core.memoryAccess=FFM`) | 22+ | `--enable-native-access=ALL-UNNAMED` | warning by the JVM, see below |
| ditto, wrapping byte buffers without allocating | 22+ | `--add-opens=java.base/java.nio=ALL-UNNAMED` | warning once, then a memory segment is allocated per wrap or copy |
| CPU pinning through FFM (`mmap.core.threadAffinity`, `AUTO` or `FFM`) | 22+, Linux | `--enable-native-access=ALL-UNNAMED` | warning by the JVM, see below |
| CPU pinning through Agrona (`AUTO` or `AGRONA`, Agrona on the class path) | 17+, Linux on x86_64 or aarch64 | `--enable-native-access=ALL-UNNAMED` on Java 24+ | warning by the JVM on Java 24+, see below |
| CPU pinning through the `taskset` command (`AUTO` or `TASKSET`) | 17+, Linux | none | |

An executable jar can set the options in its manifest instead: `Add-Exports: java.base/jdk.internal.misc`,
`Add-Opens: java.base/java.nio` and `Enable-Native-Access: ALL-UNNAMED`.

### Native access
FFM and Agrona call native code: FFM through restricted methods, Agrona through JNI. The JVM warns about native access
not enabled with `--enable-native-access`, for FFM since Java 22 and for JNI since Java 24
([JEP 472](https://openjdk.org/jeps/472)). A future JDK will deny it by default, unless enabled with the option, which
remains the supported way to allow it; the FFM API itself is final. Without the option once it is denied:
* memory access through FFM fails when the first memory buffer is used
* CPU pinning in mode `AUTO` falls back to the next backend, ultimately `taskset`, so threads are still pinned; in mode
  `FFM` or `AGRONA`, threads run unpinned and the error listener gets a warning

## Building and benchmarks
* The build compiles mmap-core with `--add-exports java.base/jdk.internal.misc=ALL-UNNAMED`, and runs the tests with
  that option and `--enable-native-access=ALL-UNNAMED`.
* The queue latency CLI `mmap-perf` sets its options in the jar manifest; its long list of further options is needed by
  Chronicle Queue, not by mmap. See [Benchmarks](benchmarks.md).
