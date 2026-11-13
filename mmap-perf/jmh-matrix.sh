#!/usr/bin/env bash
#
# Runs the buffer benchmark matrix of mmap-core -- every implementation with direct and heap memory, unpolluted and
# polluted, and two sizes, plus the expandable profiles of the memory buffers -- and prints the results as one table,
# implementations side by side, in ns/op (with the allocated bytes per operation where the gc profiler ran and found
# any). The gap command runs only the benchmarks where the memory buffers lag behind, for fewer implementations.
#
set -euo pipefail

usage() {
    cat <<EOF
Usage:
  $0 run [output-dir]
      runs the matrix, writes JSON results and logs to output-dir (default mmap-perf/results/jmh/<timestamp>), then
      prints the table
  $0 gap <set> <benchmark>... [-o output-dir]
      runs only the given benchmarks where the memory buffers lag behind, for the implementations of the set, writes
      the results to output-dir (default mmap-perf/results/jmh/gap-<timestamp>), then prints the table
        set:       a  the memory buffers with Unsafe
                   b  a + Agrona
                   c  b + ByteBuffer
                   d  c + the prototype
                   e  d + the memory buffers with FFM, i.e. all implementations
        benchmark: acquire  readLongsAcquire, 8 cases per implementation (direct/heap, polluted, size 64/4096)
                   release  writeLongsRelease, 8 cases per implementation
                   copy     copy, 8 cases per implementation
                   wrap     wrapAndRead, 4 cases per implementation (direct/heap, polluted)
      With the defaults a case takes about 16.5 s: per implementation about 2.2 minutes for each of acquire, release
      and copy, 1.1 minutes for wrap, i.e. 7.7 minutes for all four, plus about a minute for Gradle.
  $0 table <dir>
      prints the table of an earlier run
  $0 -h | --help
      prints this help

Environment (defaults in brackets):
  FORKS [2], WARMUP [3], MEASUREMENT [5]  forks, and warmup and measurement iterations per fork
  ITERATION_TIME [1s]                     time per iteration
  JMH_JAVA [25]                           Java version of the toolchain the benchmarks run with
  JVM_ARGS []                             options for the benchmark JVMs, separated by spaces, e.g.
                                          -Dmmap.core.alignmentCheck=false

Gradle itself must run on Java 22 or later (JAVA_HOME), so that the build includes the memory access through FFM.

With the defaults a run takes about 2 hours 10 minutes; keep the machine otherwise idle. On macOS the run prevents
idle sleep with caffeinate, but closing the lid still puts the machine to sleep and invalidates the run. A quick smoke
run of a few minutes: FORKS=1 WARMUP=0 MEASUREMENT=1 ITERATION_TIME=100ms $0 run

Paths must not contain spaces: the JMH arguments are passed to Gradle as one space separated property.
EOF
}

print_table() {
    python3 - "$1" <<'EOF'
import glob, json, os, sys

implementations = ['AGRONA', 'UNSAFE_BRANCH', 'BYTE_BUFFER', 'MEMORY_BUFFER_UNSAFE', 'MEMORY_BUFFER_FFM']
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
print(f"JDK {jvm}, ns/op ± error (99.9%), allocated bytes per operation shown where non-zero")
print("readLongsAcquire and writeLongsRelease magnify the per-access overhead of ordered accesses, publish and consume"
      " are the use case\n")
print(f"{'benchmark':30} {'size':>5} {'memory':6} {'polluted/profile':16} " + ' '.join(f"{i:>20}" for i in implementations))
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
    print(f"{key[0]:30} {key[1]:>5} {key[2]:6} {key[3]:16} " + ' '.join(f"{cell:>20}" for cell in cells))
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

run_matrix() {
    echo "Writing results to $out"
    run loops 'BufferLoopBenchmark'
    run wrap 'BufferSingleOpBenchmark.wrapAndRead -prof gc'
    #ByteBuffer has no compare-and-set
    run increment 'BufferSingleOpBenchmark.increment -prof gc -p implementation=AGRONA,UNSAFE_BRANCH,MEMORY_BUFFER_UNSAFE,MEMORY_BUFFER_FFM'
    run expandable-loops 'ExpandableBenchmark.(writeLongs|writeLongsRelease|copy)$'
    #increment does not depend on the size
    run expandable-increment 'ExpandableBenchmark.increment -prof gc -p size=64'
    echo "$(date +%T) done"
    echo
    print_table "$out"
}

gap_implementations() {
    case "$1" in
        a) echo MEMORY_BUFFER_UNSAFE ;;
        b) echo AGRONA,MEMORY_BUFFER_UNSAFE ;;
        c) echo AGRONA,BYTE_BUFFER,MEMORY_BUFFER_UNSAFE ;;
        d) echo AGRONA,UNSAFE_BRANCH,BYTE_BUFFER,MEMORY_BUFFER_UNSAFE ;;
        e) echo AGRONA,UNSAFE_BRANCH,BYTE_BUFFER,MEMORY_BUFFER_UNSAFE,MEMORY_BUFFER_FFM ;;
        *) echo "Unknown set: $1" >&2; usage >&2; exit 1 ;;
    esac
}

#Runs the loop benchmarks in one invocation and wrap in another, since they are in different classes
run_gap() {
    local implementations="$1" loops="$2" wrap="$3"
    echo "Writing results to $out"
    if [[ -n "$loops" ]]; then
        run gap-loops "BufferLoopBenchmark.($loops)\$ -p implementation=$implementations"
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
        out="${2:-$(dirname "$0")/results/jmh/$(date +%Y%m%d-%H%M%S)}"
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
        [[ $# -ge 2 ]] || { usage >&2; exit 1; }
        implementations="$(gap_implementations "$1")"
        shift
        loops='' wrap='' out=''
        while [[ $# -gt 0 ]]; do
            case "$1" in
                acquire) loops="${loops:+$loops|}readLongsAcquire" ;;
                release) loops="${loops:+$loops|}writeLongsRelease" ;;
                copy) loops="${loops:+$loops|}copy" ;;
                wrap) wrap=1 ;;
                -o) [[ $# -ge 2 ]] || { usage >&2; exit 1; }; out="$2"; shift ;;
                *) echo "Unknown benchmark: $1" >&2; usage >&2; exit 1 ;;
            esac
            shift
        done
        [[ -n "$loops$wrap" ]] || { usage >&2; exit 1; }
        out="${out:-$(dirname "$0")/results/jmh/gap-$(date +%Y%m%d-%H%M%S)}"
        mkdir -p "$out"
        out="$(cd "$out" && pwd)"
        cd "$(dirname "$0")/.."
        run_gap "$implementations" "$loops" "$wrap"
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
