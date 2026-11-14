# Benchmarks

## Queue latency
The `mmap-perf` module is a command line benchmark measuring the latency from appending an entry to polling it, with
concurrent senders and receivers. It runs the mmap queue or
[Chronicle Queue](https://github.com/OpenHFT/Chronicle-Queue) through the same harness, so you can compare them on
your own machine.

Download the distribution `mmap-perf-<version>.zip` from Maven Central (next to the jar of `org.tools4j:mmap-perf`,
e.g. `mvn dependency:copy -Dartifact=org.tools4j:mmap-perf:<version>:zip -DoutputDirectory=.`) and unzip it, or build
it (from the project root):
```
./gradlew :mmap-perf:installDist
```
This puts `mmap-perf.jar` and its dependencies into `mmap-perf/build/install/mmap-perf/lib` (`lib` in the unzipped
distribution); the directory can be copied to another machine. Chronicle Queue is included in the distribution, but
optional when using `mmap-perf` as a dependency. Run it with Java 17 or later:
```
java -jar mmap-perf/build/install/mmap-perf/lib/mmap-perf.jar --help
java -jar mmap-perf/build/install/mmap-perf/lib/mmap-perf.jar --queue mmap
java -jar mmap-perf/build/install/mmap-perf/lib/mmap-perf.jar --queue chronicle
```
Alternatively use the start script `mmap-perf/build/install/mmap-perf/bin/mmap-perf` (`mmap-perf.bat` on Windows), or
`./gradlew :mmap-perf:run --args='--queue mmap'`. Without options, `mmap-perf` prints its usage; `--queue mmap` runs it
with all defaults.

By default, one sender sends 11 million messages of 100 bytes at 1 million messages per second to two receivers, and
the latency percentiles of each receiver are printed after the first million (warmup) messages. Main options:

| Option                          | Description                                                  | Default            |
|---------------------------------|--------------------------------------------------------------|--------------------|
| `--queue mmap\|chronicle`       | queue implementation                                         | `mmap`             |
| `--messages <n>`                | messages per sender, including warmup                        | `11m`              |
| `--warmup <n>`                  | warmup messages per sender, not measured                     | `1m`               |
| `--rate <n>`                    | messages per second per sender, `0` for max speed            | `1m`               |
| `--length <n>`                  | message length in bytes                                      | `100`              |
| `--senders <n>`                 | sender threads                                               | `1`                |
| `--receivers <n>`               | receiver threads, each receiving all messages                | `2`                |
| `--dir <path>`                  | parent directory of the temporary queue files                | `java.io.tmpdir`   |
| `--pin [<cpus>]`                | pin senders, receivers, then mapper threads to CPUs, one each | unpinned; `2-`    |
| `--strategy <name>`             | mmap mapping strategy, e.g. `AsyncMapAheadStrategy`          | per accessor kind  |
| `--map-ahead <n>`               | mmap regions to map ahead (async mapping strategies only)    | `4`                |
| `--reader poller\|iterator`     | mmap only: receivers use a poller or continuous iterator     | `poller`           |
| `--prefill <mode>`              | mmap only: prefill `none`, `header`, `payload` or `both`     | `none`             |

Run `--help` for all options. Other mmap settings can be passed as system properties (see `QueueConfigurations`), e.g.
`java -Dmmap.queue.pollerHeaderRegionCacheSize=4 -jar mmap-perf/build/install/mmap-perf/lib/mmap-perf.jar --queue mmap`.

Notes:
* Results depend heavily on the machine: for stable numbers use a quiet machine (or isolated cores), a fast local disk
  or `--dir /dev/shm` on Linux, and enough messages for the JIT to warm up.
* `--pin` assigns one CPU per thread from the given CPU list (e.g. `2-7` or `2,4,6,8`; without a list from CPU 2 up to
  the highest online CPU, leaving CPUs 0 and 1 to the OS and other processes), first to the senders, then to the
  receivers, and the remaining CPUs to the mmap mapping runtime threads. Pinning is supported on Linux (x86_64 and
  aarch64) only; elsewhere a warning is logged and threads run unpinned. Pin to isolated cores for the most stable
  latencies.

## Before and after a change
To compare the mmap queue before and after a change, `mmap-perf/perf-baseline.sh run` (from source) runs a fixed set
of scenarios several times and summarises them; `mmap-perf/perf-baseline.sh compare <before> <after>` prints two runs
side by side, and `compare <before>... -- <after>...` pools several runs per side, so that the spread between runs
shows how large a change must be to be real. Without arguments it prints its usage.

## Microbenchmarks
`mmap-perf` also contains [JMH](https://github.com/openjdk/jmh) microbenchmarks of the library's internals, e.g. the
memory buffers compared with Agrona's `UnsafeBuffer`, Chronicle Bytes, Netty's `ByteBuf` and the JDK's `ByteBuffer`.
They are not published; run them from source, with Gradle on Java 22 or later (for the memory access through FFM):
```
./gradlew :mmap-perf:jmh -Pjmh='BufferLoopBenchmark -f 1 -p implementation=MEMORY_BUFFER_UNSAFE,AGRONA'
mmap-perf/jmh-matrix.sh run --with all
mmap-perf/jmh-matrix.sh run --with agrona readLongsAcquire writeLongsRelease
```
`-Pjmh` takes the JMH command line arguments (`-Pjmh='-h'` lists them). `jmh-matrix.sh run` runs all buffer
benchmarks, or only those named, and prints one table. It measures the memory buffers with Unsafe, and adds other
implementations with `--with`: the memory buffers without the alignment check of ordered accesses or with FFM,
Agrona, Chronicle, Netty with and without Unsafe, the fastest prototype of the earlier benchmarks, the JDK's
`ByteBuffer`, or all. Without arguments it prints its usage.
