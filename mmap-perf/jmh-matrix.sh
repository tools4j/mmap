#!/usr/bin/env bash
#
# Runs the buffer benchmark matrix of mmap-core -- every implementation with direct and heap memory, unpolluted and
# polluted, and two sizes, plus the expandable profiles of the memory buffers -- and prints the results as one table,
# implementations side by side, in ns/op (with the allocated bytes per operation where the gc profiler ran and found
# any). The memory buffers with Unsafe always run, the other implementations only when opted in with --with. The gap
# command runs only the benchmarks where the memory buffers lag behind.
#
set -euo pipefail

usage() {
    cat <<EOF
Usage:
  $0 run [--with <implementations>] [output-dir]
      runs the matrix, writes JSON results and logs to output-dir (default mmap-perf/results/jmh/<timestamp>), then
      prints the table
  $0 gap [--with <implementations>] <benchmark>... [-o output-dir]
      runs only the given benchmarks where the memory buffers lagged behind, writes the results to output-dir (default
      mmap-perf/results/jmh/gap-<timestamp>), then prints the table
        benchmark: acquire  readLongsAcquire, 8 cases per implementation (direct/heap, polluted, size 64/4096)
                   release  writeLongsRelease, 8 cases per implementation
                   copy     copy, 8 cases per implementation
                   wrap     wrapAndRead, 4 cases per implementation (direct/heap, polluted)
  $0 table <dir>
      prints the table of an earlier run
  $0 -h | --help
      prints this help

Implementations:
  The memory buffers of mmap-core with Unsafe always run; --with adds a comma separated list of, in the order of the
  columns, those with Unsafe:
      agrona        Agrona's UnsafeBuffer
      chronicle     Chronicle Bytes
      netty-unsafe  Netty's ByteBuf with Unsafe (-Dio.netty.noUnsafe=false), the default before Java 24
      prototype     the fastest prototype of the earlier benchmarks
  and those with public JDK API only:
      ffm           the memory buffers of mmap-core with FFM
      netty         Netty's ByteBuf as by default on Java 24 and later
      bytebuffer    the JDK's ByteBuffer
  or all. Each runs the benchmarks it supports: Netty has no ordered access and no compare-and-set, ByteBuffer no
  compare-and-set, and only the memory buffers are expandable.

Running time, with the defaults about 16.5 s per case:
  run   about 40 minutes for the memory buffers with Unsafe, plus about 23 minutes each for agrona, chronicle and
        prototype, 40 for ffm, 17 for bytebuffer and 8 each for the netty variants: about 3 hours with all
  gap   about 2.2 minutes per implementation for each of acquire, release and copy, and 1.1 minutes for wrap

Environment (defaults in brackets):
  FORKS [2], WARMUP [3], MEASUREMENT [5]  forks, and warmup and measurement iterations per fork
  ITERATION_TIME [1s]                     time per iteration
  JMH_JAVA [25]                           Java version of the toolchain the benchmarks run with
  JVM_ARGS []                             options for the benchmark JVMs, separated by spaces, e.g.
                                          -Dmmap.core.alignmentCheck=false

Gradle itself must run on Java 22 or later (JAVA_HOME), so that the build includes the memory access through FFM.

Keep the machine otherwise idle while running. On macOS the runs prevent idle sleep with caffeinate, but closing the
lid still puts the machine to sleep and invalidates the run. A quick smoke run of a few minutes:
  FORKS=1 WARMUP=0 MEASUREMENT=1 ITERATION_TIME=100ms $0 run --with all

Paths must not contain spaces: the JMH arguments are passed to Gradle as one space separated property.
EOF
}

print_table() {
    python3 - "$1" <<'EOF'
import glob, json, os, sys

#in the order of the columns: with Unsafe, then with public JDK API only, from the first of PUBLIC_API on
implementations = ['MEMORY_BUFFER_UNSAFE', 'AGRONA', 'CHRONICLE', 'NETTY_UNSAFE', 'UNSAFE_BRANCH',
                   'MEMORY_BUFFER_FFM', 'NETTY', 'BYTE_BUFFER']
PUBLIC_API = {'MEMORY_BUFFER_FFM', 'NETTY', 'BYTE_BUFFER'}
rows, jvm = {}, None
for file in sorted(glob.glob(os.path.join(sys.argv[1], '*.json'))):
    for result in json.load(open(file)):
        jvm = jvm or f"{result['jdkVersion']} ({result['vmName']} {result['vmVersion']})"
        params = result['params']
        benchmark = '.'.join(result['benchmark'].rsplit('.', 2)[-2:]).replace('Benchmark.', '.')
        variant = params.get('polluted', params.get('profile', '-'))
        key = (benchmark, params.get('size', '-'), params['memory'], variant)
        metric = result['primaryMetric']
        alloc = result.get('secondaryMetrics', {}).get('gc.alloc.rate.norm', {}).get('score')
        rows.setdefault(key, {})[params['implementation']] = (metric['score'], metric['scoreError'], alloc)
if not rows:
    sys.exit(f"No results in {sys.argv[1]}")
implementations = [i for i in implementations if any(i in row for row in rows.values())]
#the first implementation with public JDK API only gets a separator in front of its column
first_public = next((i for i in implementations if i in PUBLIC_API), None)
def columns(cells):
    return ' '.join(('| ' if i == first_public else '') + f"{cell:>20}" for i, cell in zip(implementations, cells))
print(f"JDK {jvm}, ns/op ± error (99.9%), allocated bytes per operation shown where non-zero")
print("readLongsAcquire and writeLongsRelease magnify the per-access overhead of ordered accesses, publish and consume"
      " are the use case\n")
print(f"{'benchmark':30} {'size':>5} {'memory':6} {'polluted/profile':16} " + columns(implementations))
for key in sorted(rows):
    cells = []
    for implementation in implementations:
        value = rows[key].get(implementation)
        if value is None:
            cells.append('-')
        else:
            score, error, alloc = value
            #a single measurement iteration has no error, reported as "NaN"
            error = f" ±{error:6.2f}" if isinstance(error, (int, float)) and error == error else ''
            cells.append(f"{score:8.2f}{error}" + (f" {alloc:.0f}B" if isinstance(alloc, (int, float)) and alloc >= 0.5 else ''))
    print(f"{key[0]:30} {key[1]:>5} {key[2]:6} {key[3]:16} " + columns(cells))
EOF
}

#Runs one JMH invocation into $out/<name>.json, with the forks and iterations from the environment
run() {
    local name="$1"
    shift
    local common="-f ${FORKS:-2} -wi ${WARMUP:-3} -w ${ITERATION_TIME:-1s} -i ${MEASUREMENT:-5} -r ${ITERATION_TIME:-1s} -foe true"
    local arg
    for arg in ${JVM_ARGS:-}; do
        common="$common -jvmArgsPrepend $arg"
    done
    echo "$(date +%T) $name"
    ./gradlew -q :mmap-perf:jmh -PjmhJava="${JMH_JAVA:-25}" \
            -Pjmh="$common $* -rf json -rff $out/$name.json" > "$out/$name.log" 2>&1 ||
            { echo "Failed, see $out/$name.log" >&2; exit 1; }
}

#The implementations to opt in to, as <name>:<JMH name> in the order of the columns
OPT_INS="agrona:AGRONA chronicle:CHRONICLE netty-unsafe:NETTY_UNSAFE prototype:UNSAFE_BRANCH ffm:MEMORY_BUFFER_FFM
         netty:NETTY bytebuffer:BYTE_BUFFER"

#Prints the JMH names of the memory buffers with Unsafe and of the opt-ins in the comma separated list (or all)
select_implementations() {
    local with=",$1," selected=MEMORY_BUFFER_UNSAFE name entry
    for name in ${1//,/ }; do
        if [[ "$name" != all && " $OPT_INS " != *" $name:"* ]]; then
            echo "Unknown implementation: $name" >&2
            return 1
        fi
    done
    for entry in $OPT_INS; do
        if [[ "$with" == *",all,"* || "$with" == *",${entry%%:*},"* ]]; then
            selected="$selected,${entry#*:}"
        fi
    done
    echo "$selected"
}

#Prints the comma separated list of implementations in $1 without those given after it
without() {
    local list=",$1," name
    shift
    for name in "$@"; do
        list="${list//,$name,/,}"
    done
    list="${list#,}"
    echo "${list%,}"
}

#Netty has no ordered access, ByteBuffer and Netty no compare-and-set, and only the memory buffers are expandable
run_matrix() {
    local ordered compare_and_set expandable
    ordered="$(without "$implementations" NETTY_UNSAFE NETTY)"
    compare_and_set="$(without "$ordered" BYTE_BUFFER)"
    expandable="$(without "$compare_and_set" AGRONA CHRONICLE UNSAFE_BRANCH)"
    echo "Writing results to $out, comparing $implementations"
    run loops "BufferLoopBenchmark.(readLongs|writeLongs|copy)\$ -p implementation=$implementations"
    run loops-ordered "BufferLoopBenchmark.(readLongsAcquire|writeLongsRelease|publish|consume)\$ -p implementation=$ordered"
    run wrap "BufferSingleOpBenchmark.wrapAndRead -prof gc -p implementation=$implementations"
    run increment "BufferSingleOpBenchmark.increment -prof gc -p implementation=$compare_and_set"
    run expandable-loops "ExpandableBenchmark.(writeLongs|writeLongsRelease|copy)\$ -p implementation=$expandable"
    #increment does not depend on the size
    run expandable-increment "ExpandableBenchmark.increment -prof gc -p size=64 -p implementation=$expandable"
    echo "$(date +%T) done"
    echo
    print_table "$out"
}

#Runs the loop benchmarks, those with ordered access without Netty, and wrap, each in an invocation of its own
run_gap() {
    local loops="$1" ordered_loops="$2" wrap="$3"
    echo "Writing results to $out, comparing $implementations"
    if [[ -n "$loops" ]]; then
        run gap-loops "BufferLoopBenchmark.($loops)\$ -p implementation=$implementations"
    fi
    if [[ -n "$ordered_loops" ]]; then
        run gap-loops-ordered "BufferLoopBenchmark.($ordered_loops)\$ -p implementation=$(without "$implementations" \
                NETTY_UNSAFE NETTY)"
    fi
    if [[ -n "$wrap" ]]; then
        run gap-wrap "BufferSingleOpBenchmark.wrapAndRead -prof gc -p implementation=$implementations"
    fi
    echo "$(date +%T) done"
    echo
    print_table "$out"
}

check_gradle_java() {
    local java="${JAVA_HOME:+$JAVA_HOME/bin/}java"
    local version
    version="$("$java" -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java.specification.version = //p')"
    if [[ -z "$version" || "$version" -lt 22 ]]; then
        echo "Gradle must run on Java 22 or later for the memory access through FFM, but $java is Java" \
             "${version:-unknown}; set JAVA_HOME" >&2
        exit 1
    fi
}

case "${1:-}" in
    run)
        check_gradle_java
        if [[ -z "${JMH_MATRIX_CAFFEINATED:-}" ]] && command -v caffeinate > /dev/null; then
            JMH_MATRIX_CAFFEINATED=1 exec caffeinate -i "$0" "$@"
        fi
        shift
        with='' out=''
        while [[ $# -gt 0 ]]; do
            case "$1" in
                --with) [[ $# -ge 2 ]] || { usage >&2; exit 1; }; with="$2"; shift ;;
                -*) echo "Unknown option: $1" >&2; usage >&2; exit 1 ;;
                *) [[ -z "$out" ]] || { usage >&2; exit 1; }; out="$1" ;;
            esac
            shift
        done
        implementations="$(select_implementations "$with")"
        out="${out:-$(dirname "$0")/results/jmh/$(date +%Y%m%d-%H%M%S)}"
        mkdir -p "$out"
        out="$(cd "$out" && pwd)"
        cd "$(dirname "$0")/.."
        run_matrix
        ;;
    gap)
        check_gradle_java
        if [[ -z "${JMH_MATRIX_CAFFEINATED:-}" ]] && command -v caffeinate > /dev/null; then
            JMH_MATRIX_CAFFEINATED=1 exec caffeinate -i "$0" "$@"
        fi
        shift
        with='' loops='' ordered_loops='' wrap='' out=''
        while [[ $# -gt 0 ]]; do
            case "$1" in
                acquire) ordered_loops="${ordered_loops:+$ordered_loops|}readLongsAcquire" ;;
                release) ordered_loops="${ordered_loops:+$ordered_loops|}writeLongsRelease" ;;
                copy) loops="copy" ;;
                wrap) wrap=1 ;;
                --with) [[ $# -ge 2 ]] || { usage >&2; exit 1; }; with="$2"; shift ;;
                -o) [[ $# -ge 2 ]] || { usage >&2; exit 1; }; out="$2"; shift ;;
                *) echo "Unknown benchmark: $1" >&2; usage >&2; exit 1 ;;
            esac
            shift
        done
        [[ -n "$loops$ordered_loops$wrap" ]] || { usage >&2; exit 1; }
        implementations="$(select_implementations "$with")"
        out="${out:-$(dirname "$0")/results/jmh/gap-$(date +%Y%m%d-%H%M%S)}"
        mkdir -p "$out"
        out="$(cd "$out" && pwd)"
        cd "$(dirname "$0")/.."
        run_gap "$loops" "$ordered_loops" "$wrap"
        ;;
    table)
        [[ -n "${2:-}" ]] || { usage >&2; exit 1; }
        print_table "$2"
        ;;
    -h | --help)
        usage
        ;;
    *)
        usage >&2
        exit 1
        ;;
esac
