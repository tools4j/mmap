#!/usr/bin/env bash
#
# Runs a fixed set of mmap-perf queue scenarios several times -- latency at the default rate, throughput, two senders
# and larger messages -- so that the same set can be repeated after a change and compared with an earlier run.
# Rounds run the scenarios in turn, so that drift of the machine affects all scenarios alike.
#
set -euo pipefail

SCENARIOS=(
    "latency|"
    "throughput|--rate 0"
    "senders-2|--senders 2"
    "length-1000|--length 1000 --messages 3m"
)

usage() {
    cat <<EOF
Usage:
  $0 run [output-dir]
      builds mmap-perf, runs every scenario RUNS times, writes the outputs to output-dir (default
      mmap-perf/results/perf/<timestamp>), then prints the summary
  $0 table <dir>...
      prints the summary of earlier runs, pooling several directories into one
  $0 compare <before-dir> <after-dir>
  $0 compare <before-dir>... -- <after-dir>...
      prints the medians of two runs side by side, with the change in percent, pooling the directories on each side
      of --
  $0 -h | --help
      prints this help

Scenarios (mmap queue, otherwise mmap-perf's defaults: 1 sender, 2 receivers, 100 bytes, 1M msg/s, 11M messages
of which 1M warmup):
$(for scenario in "${SCENARIOS[@]}"; do printf '  %-12s %s\n' "${scenario%%|*}" "${scenario#*|}"; done)

Environment (defaults in brackets):
  RUNS [5]                      runs per scenario
  PAUSE [10]                    seconds of pause before each run but the first, so that the previous run's queue
                                files are written back and deleted before the next run starts
  QUEUE_DIR [java.io.tmpdir]    parent directory of the temporary queue files, passed as --dir
  PERF_OPTS []                  further mmap-perf options for all scenarios, e.g. --pin 2-
  JAVA_OPTS []                  JVM options, e.g. -Dmmap.queue.pollerHeaderRegionCacheSize=4
  JAVA_HOME                     the JDK building and running mmap-perf

With the defaults a run takes about 8 minutes and needs about 4 GB of free disk space for the queue files of one
run at a time (length-1000 being the largest); keep the machine otherwise idle. On macOS the run prevents idle
sleep with caffeinate, but closing the lid still puts the machine to sleep and invalidates the run.

The summary pools the receivers of all runs of a scenario: the median of each percentile, and its range from the
best to the worst receiver. Comparing two baselines of the same code shows how large a change must be to be real.
EOF
}

summarize() {
    python3 - "$@" <<'EOF'
import glob, os, re, statistics, sys

PERCENTILES = ['50%', '90%', '99%', '99.9%', '99.99%', 'max']

def load(directories):
    scenarios = {}
    #named <position>-<scenario>-run<run>.txt, so that sorting keeps the order of the scenarios
    files = [file for directory in directories for file in glob.glob(os.path.join(directory, '*-run*.txt'))]
    for file in sorted(files, key=os.path.basename):
        scenario = re.sub(r'^\d+-|-run\d+\.txt$', '', os.path.basename(file))
        text = open(file).read()
        data = scenarios.setdefault(scenario, {'runs': 0, 'rates': [], 'percentiles': {p: [] for p in PERCENTILES}})
        data['runs'] += 1
        data['rates'] += [float(rate) for rate in re.findall(r'msg/s\s*:\s*([\d.E]+)', text)]
        for receiver in re.findall(r'receiver-\d+: Percentiles \(micros\)\n((?:\t.*\n?)+)', text):
            values = dict(re.findall(r'\t(\S+)\s*:\s*([\d.]+)', receiver))
            for percentile in PERCENTILES:
                if percentile in values:
                    data['percentiles'][percentile].append(float(values[percentile]))
    if not scenarios:
        sys.exit(f"No results in {' '.join(directories)}")
    return scenarios

def short(value):
    return f"{value:.0f}" if value >= 100 else f"{value:.3g}"

def medians(data):
    return [statistics.median(data['percentiles'][p]) if data['percentiles'][p] else float('nan')
            for p in PERCENTILES]

def header(first):
    return f"{first:14} {'runs':>4} {'msg/s':>11} " + ' '.join(f"{p:>9}" for p in PERCENTILES)

def info(directory):
    path = os.path.join(directory, 'info.txt')
    return open(path).read().strip() if os.path.exists(path) else ''

if sys.argv[1] == 'table':
    directories = sys.argv[2:]
    for directory in directories:
        print(f"{directory}\n  " + info(directory).replace('\n', '\n  ') + '\n')
    print('latency in microseconds: median over all receivers of all runs, below it the range from best to worst\n')
    print(header('scenario'))
    for scenario, data in load(directories).items():
        rate = statistics.median(data['rates']) if data['rates'] else float('nan')
        print(f"{scenario:14} {data['runs']:>4} {rate:>11.0f} " + ' '.join(f"{m:>9.3f}" for m in medians(data)))
        ranges = [f"{short(min(v))}-{short(max(v))}" if v else '-' for v in (data['percentiles'][p] for p in PERCENTILES)]
        print(f"{'':14} {'':>4} {'':>11} " + ' '.join(f"{r:>9}" for r in ranges))
else:
    arguments = sys.argv[2:]
    separator = arguments.index('--') if '--' in arguments else 1
    before_directories, after_directories = arguments[:separator], arguments[separator + 1:] or arguments[1:]
    if '--' not in arguments and len(arguments) != 2 or not before_directories or not after_directories:
        sys.exit('compare takes <before-dir> <after-dir>, or <before-dir>... -- <after-dir>...')
    before, after = load(before_directories), load(after_directories)
    print(f"before: {' '.join(before_directories)}\nafter:  {' '.join(after_directories)}\n")
    print('latency in microseconds, medians over all receivers of all runs: before, after, change\n')
    print(header('scenario'))
    for scenario in before:
        if scenario not in after:
            continue
        for label, data in (('before', before[scenario]), ('after', after[scenario])):
            rate = statistics.median(data['rates']) if data['rates'] else float('nan')
            name = scenario if label == 'before' else ''
            print(f"{name:14} {data['runs']:>4} {rate:>11.0f} " + ' '.join(f"{m:>9.3f}" for m in medians(data)))
        changes = [f"{(a / b - 1) * 100:+.0f}%" if b else '-' for b, a in zip(medians(before[scenario]),
                                                                            medians(after[scenario]))]
        print(f"{'':14} {'':>4} {'':>11} " + ' '.join(f"{c:>9}" for c in changes))
EOF
}

check_free_space() {
    local dir="$1"
    local free_kb
    free_kb="$(df -Pk "$dir" | awk 'NR == 2 { print $4 }')"
    if [[ "$free_kb" -lt $((5 * 1024 * 1024)) ]]; then
        echo "Only $((free_kb / 1024)) MB free in $dir, the queue files of one run need about 4 GB" >&2
        exit 1
    fi
}

run_scenarios() {
    local out="$1"
    local java="${JAVA_HOME:+$JAVA_HOME/bin/}java"
    local jar="mmap-perf/build/install/mmap-perf/lib/mmap-perf.jar"
    local queue_dir="${QUEUE_DIR:-$("$java" -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java.io.tmpdir = //p')}"
    check_free_space "$queue_dir"

    echo "Building mmap-perf"
    ./gradlew -q :mmap-perf:installDist > "$out/build.log" 2>&1 || { echo "Build failed, see $out/build.log" >&2; exit 1; }
    {
        echo "commit: $(git rev-parse --short HEAD) ($(git rev-parse --abbrev-ref HEAD))$(git diff --quiet HEAD || echo ', with uncommitted changes')"
        echo "date:   $(date '+%Y-%m-%d %H:%M')"
        echo "java:   $("$java" -version 2>&1 | tail -1)"
        echo "os:     $(uname -sm)"
        echo "runs:   ${RUNS:-5}, pause: ${PAUSE:-10} s, PERF_OPTS: ${PERF_OPTS:-none}, JAVA_OPTS: ${JAVA_OPTS:-none}"
    } > "$out/info.txt"

    echo "Writing results to $out"
    for ((run = 1; run <= ${RUNS:-5}; run++)); do
        for position in "${!SCENARIOS[@]}"; do
            local scenario="${SCENARIOS[$position]}"
            local name="${scenario%%|*}"
            local file="$out/$((position + 1))-$name-run$run.txt"
            if [[ "$run" -gt 1 || "$position" -gt 0 ]]; then
                sleep "${PAUSE:-10}"
            fi
            echo "$(date +%T) run $run: $name"
            # shellcheck disable=SC2086 #the options are split into words on purpose
            "$java" ${JAVA_OPTS:-} -jar "$jar" --queue mmap --dir "$queue_dir" ${scenario#*|} ${PERF_OPTS:-} \
                    > "$file" 2>&1 || { echo "Failed, see $file" >&2; exit 1; }
        done
    done
    echo "$(date +%T) done"
    echo
    summarize table "$out"
}

case "${1:-}" in
    run)
        if [[ -z "${PERF_BASELINE_CAFFEINATED:-}" ]] && command -v caffeinate > /dev/null; then
            PERF_BASELINE_CAFFEINATED=1 exec caffeinate -i "$0" "$@"
        fi
        out="${2:-$(dirname "$0")/results/perf/$(date +%Y%m%d-%H%M%S)}"
        mkdir -p "$out"
        out="$(cd "$out" && pwd)"
        cd "$(dirname "$0")/.."
        run_scenarios "$out"
        ;;
    table)
        [[ -n "${2:-}" ]] || { usage >&2; exit 1; }
        summarize "$@"
        ;;
    compare)
        [[ -n "${2:-}" && -n "${3:-}" ]] || { usage >&2; exit 1; }
        summarize "$@"
        ;;
    -h | --help)
        usage
        ;;
    *)
        usage >&2
        exit 1
        ;;
esac
