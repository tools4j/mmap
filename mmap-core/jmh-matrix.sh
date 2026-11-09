#!/usr/bin/env bash
#
# Runs the buffer benchmark matrix of mmap-core -- every implementation with direct and heap memory, unpolluted and
# polluted, and two sizes -- and prints the results as one table, implementations side by side, in ns/op (with the
# allocated bytes per operation where the gc profiler ran and found any).
#
set -euo pipefail

usage() {
    cat <<EOF
Usage:
  $0 run [output-dir]
      runs the matrix, writes JSON results and logs to output-dir (default mmap-core/build/jmh/<timestamp>), then
      prints the table
  $0 table <dir>
      prints the table of an earlier run
  $0 -h | --help
      prints this help

Environment (defaults in brackets):
  FORKS [2], WARMUP [3], MEASUREMENT [5]  forks, and warmup and measurement iterations per fork
  ITERATION_TIME [1s]                     time per iteration
  JMH_JAVA [25]                           Java version of the toolchain the benchmarks run with

With the defaults a run takes about 85 minutes; keep the machine otherwise idle. On macOS the run prevents idle
sleep with caffeinate, but closing the lid still puts the machine to sleep and invalidates the run. A quick smoke run
of about a minute: FORKS=1 WARMUP=0 MEASUREMENT=1 ITERATION_TIME=100ms $0 run

Paths must not contain spaces: the JMH arguments are passed to Gradle as one space separated property.
EOF
}

print_table() {
    python3 - "$1" <<'EOF'
import glob, json, os, sys

implementations = ['AGRONA', 'UNSAFE_SPLIT', 'UNSAFE_BRANCH', 'FFM_BRANCH', 'FFM_SPLIT', 'BYTE_BUFFER']
rows, jvm = {}, None
for file in sorted(glob.glob(os.path.join(sys.argv[1], '*.json'))):
    for result in json.load(open(file)):
        jvm = jvm or f"{result['jdkVersion']} ({result['vmName']} {result['vmVersion']})"
        params = result['params']
        key = (result['benchmark'].rsplit('.', 1)[-1], params.get('size', '-'), params['memory'], params['polluted'])
        metric = result['primaryMetric']
        alloc = result.get('secondaryMetrics', {}).get('gc.alloc.rate.norm', {}).get('score')
        rows.setdefault(key, {})[params['implementation']] = (metric['score'], metric['scoreError'], alloc)
if not rows:
    sys.exit(f"No results in {sys.argv[1]}")
print(f"JDK {jvm}, ns/op ± error (99.9%), allocated bytes per operation shown where non-zero\n")
print(f"{'benchmark':18} {'size':>5} {'memory':6} {'polluted':8} " + ' '.join(f"{i:>17}" for i in implementations))
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
    print(f"{key[0]:18} {key[1]:>5} {key[2]:6} {key[3]:8} " + ' '.join(f"{cell:>17}" for cell in cells))
EOF
}

run_matrix() {
    local out="$1"
    local common="-f ${FORKS:-2} -wi ${WARMUP:-3} -w ${ITERATION_TIME:-1s} -i ${MEASUREMENT:-5} -r ${ITERATION_TIME:-1s} -foe true"

    run() {
        local name="$1"
        shift
        echo "$(date +%T) $name"
        ./gradlew -q :mmap-core:jmh -PjmhJava="${JMH_JAVA:-25}" \
                -Pjmh="$common $* -rf json -rff $out/$name.json" > "$out/$name.log" 2>&1 ||
                { echo "Failed, see $out/$name.log" >&2; exit 1; }
    }

    echo "Writing results to $out"
    run loops 'BufferLoopBenchmark'
    run wrap 'BufferSingleOpBenchmark.wrapAndRead -prof gc'
    #FFM has no compare-and-set for heap memory, ByteBuffer none at all
    run increment-unsafe 'BufferSingleOpBenchmark.increment -prof gc -p implementation=AGRONA,UNSAFE_SPLIT,UNSAFE_BRANCH'
    run increment-ffm 'BufferSingleOpBenchmark.increment -prof gc -p implementation=FFM_BRANCH,FFM_SPLIT -p memory=DIRECT'
    echo "$(date +%T) done"
    echo
    print_table "$out"
}

case "${1:-}" in
    run)
        if [[ -z "${JMH_MATRIX_CAFFEINATED:-}" ]] && command -v caffeinate > /dev/null; then
            JMH_MATRIX_CAFFEINATED=1 exec caffeinate -i "$0" "$@"
        fi
        out="${2:-$(dirname "$0")/build/jmh/$(date +%Y%m%d-%H%M%S)}"
        mkdir -p "$out"
        out="$(cd "$out" && pwd)"
        cd "$(dirname "$0")/.."
        run_matrix "$out"
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
