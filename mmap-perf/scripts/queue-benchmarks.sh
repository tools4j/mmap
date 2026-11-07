#!/usr/bin/env bash
#
# Runs a matrix of mmap-perf queue benchmarks: latency and throughput for combinations of mapping strategy (set), file
# size profile and file initialiser, with several runs per combination, and prints the results as Markdown tables.
#
# Sets:
#   A  latency with the default mapping strategy (async map-ahead)
#   B  latency with SyncMappingStrategy, where the sender extends (and fills) files itself
#   C  throughput at max rate (--rate 0)
#
# Profiles (region size, payload min file size, header min file size); files grow in steps of the min file size, which
# is the amount filled per extension with ZERO. Header files are made expandable so that the header size applies.
#   XS  64k  64k  64k
#   S   2m   2m   2m
#   M   2m   16m  4m
#   L   2m   64m  16m   (queue defaults, apart from the expandable header file)
#
# Each run's log starts with a '# bench' line recording its parameters, so that the summary can be rebuilt from a log
# directory with --summary-only.

set -euo pipefail

RUNS=2
SETS="A,B,C"
PROFILES="XS,S,M,L"
THROUGHPUT_PROFILES="L"
INITS="LAZY,ZERO"
MESSAGES=6m
WARMUP=1m
THROUGHPUT_MESSAGES=3m
OUT=""
BUILD=1
SUMMARY_ONLY=0
RUN=0

usage() {
    cat <<EOF
Usage: $(basename "$0") --run [options]
       $(basename "$0") --summary-only <dir>

Runs a matrix of mmap-perf queue benchmarks, one run per set, profile, file initialiser and repetition, and prints
the results as Markdown tables. A full run with the defaults takes several minutes.

Sets:
  A  latency with the default mapping strategy (async map-ahead)
  B  latency with SyncMappingStrategy, where the sender extends (and initialises) files itself
  C  throughput at max rate

Profiles (region size, payload min file size, header min file size; files grow in steps of the min file size, and
header files are made expandable so that the header size applies):
  XS  64k  64k  64k
  S   2m   2m   2m
  M   2m   16m  4m
  L   2m   64m  16m

Options:
  --run                       run the benchmarks (required, so that nothing long-running starts by accident)
  --runs <n>                  runs per cell, alternating initialisers (default: $RUNS)
  --sets <list>               comma separated sets A, B, C (default: $SETS)
  --profiles <list>           profiles for sets A and B: XS, S, M, L (default: $PROFILES)
  --throughput-profiles <list> profiles for set C (default: $THROUGHPUT_PROFILES)
  --inits <list>              file initialisers (default: $INITS)
  --messages <n>              messages per latency run, including warmup (default: $MESSAGES)
  --warmup <n>                warmup messages per run (default: $WARMUP)
  --throughput-messages <n>   messages per throughput run, including warmup (default: $THROUGHPUT_MESSAGES)
  --out <dir>                 directory for the run logs (default: a new temporary directory)
  --no-build                  do not build mmap-perf first
  --summary-only <dir>        only print the summary of the logs in the given directory
  --help                      print this help

Latencies are in micros, for the worst receiver of a run, as the median of the runs followed by the run values.
EOF
}

if [[ $# -eq 0 ]]; then
    usage
    exit 1
fi

while [[ $# -gt 0 ]]; do
    case "$1" in
        --run) RUN=1; shift;;
        --runs) RUNS="$2"; shift 2;;
        --sets) SETS="$2"; shift 2;;
        --profiles) PROFILES="$2"; shift 2;;
        --throughput-profiles) THROUGHPUT_PROFILES="$2"; shift 2;;
        --inits) INITS="$2"; shift 2;;
        --messages) MESSAGES="$2"; shift 2;;
        --warmup) WARMUP="$2"; shift 2;;
        --throughput-messages) THROUGHPUT_MESSAGES="$2"; shift 2;;
        --out) OUT="$2"; shift 2;;
        --no-build) BUILD=0; shift;;
        --summary-only) SUMMARY_ONLY=1; OUT="$2"; shift 2;;
        --help) usage; exit 0;;
        *) echo "Unknown option: $1" >&2; usage >&2; exit 1;;
    esac
done

if [[ $RUN -eq 0 && $SUMMARY_ONLY -eq 0 ]]; then
    usage
    echo >&2
    echo "Nothing run: add --run to start the benchmarks." >&2
    exit 1
fi

cd "$(dirname "$0")/../.."
JAR=mmap-perf/build/install/mmap-perf/lib/mmap-perf.jar

# Prints "<region size option> <payload min file size> <header min file size>" for a profile
profile() {
    case "$1" in
        XS) echo "64k 65536 65536";;
        S)  echo "2m 2097152 2097152";;
        M)  echo "2m 16777216 4194304";;
        L)  echo "2m 67108864 16777216";;
        *)  echo "Unknown profile: $1" >&2; exit 1;;
    esac
}

strategy() {
    case "$1" in
        B) echo "SyncMappingStrategy";;
        *) echo "default";;
    esac
}

# Runs one benchmark: set profile init run
run() {
    local set=$1 prof=$2 init=$3 rep=$4
    local region pmin hmin
    read -r region pmin hmin <<< "$(profile "$prof")"
    local args=(--region-size "$region")
    case "$set" in
        A) args+=(--messages "$MESSAGES" --warmup "$WARMUP");;
        B) args+=(--messages "$MESSAGES" --warmup "$WARMUP" --strategy SyncMappingStrategy);;
        C) args+=(--messages "$THROUGHPUT_MESSAGES" --warmup "$WARMUP" --rate 0);;
        *) echo "Unknown set: $set" >&2; exit 1;;
    esac
    local log="$OUT/$set-$prof-$init-$rep.log"
    echo "# bench set=$set strategy=$(strategy "$set") profile=$prof init=$init run=$rep region=$region" \
         "payloadMin=$pmin headerMin=$hmin" > "$log"
    local status=0
    java -Dmmap.mapping.fileInitialiser="$init" -Dmmap.queue.expandHeaderFile=true \
         -Dmmap.queue.minPayloadFileSize="$pmin" -Dmmap.queue.minHeaderFileSize="$hmin" \
         -jar "$JAR" "${args[@]}" >> "$log" 2>&1 || status=$?
    echo "set=$set profile=$prof init=$init run=$rep exit=$status"
}

summary() {
    local files=("$OUT"/*.log)
    for f in "${files[@]}"; do
        # one line per log: meta fields, then worst receiver percentiles and sender rate (or NA)
        awk '
            FNR == 1 && $1 == "#" && $2 == "bench" { for (i = 3; i <= NF; i++) { split($i, kv, "="); meta[kv[1]] = kv[2] } }
            $1 == "50%" || $1 == "99%" || $1 == "99.9%" || $1 == "99.99%" || $1 == "max" {
                if (!($1 in v) || $3 + 0 > v[$1]) v[$1] = $3 + 0
            }
            $1 == "msg/s" { rate = $3 }
            END {
                if (!("set" in meta)) exit
                printf "%s %s %s %s %s %s %s %s", meta["set"], meta["strategy"], meta["profile"], meta["init"],
                       meta["run"], meta["region"], meta["payloadMin"], meta["headerMin"]
                n = split("50% 99% 99.9% 99.99% max", keys, " ")
                for (i = 1; i <= n; i++) printf " %s", (keys[i] in v) ? v[keys[i]] : "NA"
                printf " %s\n", (rate == "" ? "NA" : rate)
            }' "$f"
    done | awk -v sets="$SETS" -v profiles="$PROFILES" -v tprofiles="$THROUGHPUT_PROFILES" -v inits="$INITS" '
        function group(s) { while (s ~ /[0-9][0-9][0-9][0-9]/) sub(/[0-9][0-9][0-9]$|[0-9][0-9][0-9],/, ",&", s); return s }
        function fmt(x) { return x >= 100 ? group(sprintf("%.0f", x)) : x < 10 ? sprintf("%.2f", x) : sprintf("%.1f", x) }
        function size(x) {
            if (x ~ /[kmgKMG]$/) return toupper(x)
            return x >= 1073741824 && x % 1073741824 == 0 ? x / 1073741824 "G" : x >= 1048576 && x % 1048576 == 0 ? x / 1048576 "M" : x >= 1024 && x % 1024 == 0 ? x / 1024 "K" : x
        }
        function median(list,    a, n, i, j, t) {
            n = split(list, a, " ")
            for (i = 1; i <= n; i++) for (j = i + 1; j <= n; j++) if (a[j] + 0 < a[i] + 0) { t = a[i]; a[i] = a[j]; a[j] = t }
            return n % 2 ? a[(n + 1) / 2] : (a[n / 2] + a[n / 2 + 1]) / 2
        }
        function cell(list,    a, n, i, s) {
            if (list ~ /NA/) return "FAILED"
            n = split(list, a, " ")
            s = fmt(median(list))
            if (n > 1) { s = s " ["; for (i = 1; i <= n; i++) s = s (i > 1 ? " / " : "") fmt(a[i]); s = s "]" }
            return s
        }
        {
            key = $1 SUBSEP $3 SUBSEP $4
            strategy[$1] = $2; region[key] = $6; pmin[key] = $7; hmin[key] = $8
            for (i = 0; i < 5; i++) values[key, i] = values[key, i] (values[key, i] == "" ? "" : " ") $(9 + i)
            rates[key] = rates[key] (rates[key] == "" ? "" : " ") $14
            seen[key] = 1
        }
        END {
            ns = split(sets, S, ","); ni = split(inits, I, ",")
            for (s = 1; s <= ns; s++) {
                set = S[s]
                np = split(set == "C" ? tprofiles : profiles, P, ",")
                header = 0
                for (p = 1; p <= np; p++) for (i = 1; i <= ni; i++) {
                    key = set SUBSEP P[p] SUBSEP I[i]
                    if (!(key in seen)) continue
                    if (!header) {
                        header = 1
                        if (set == "C") {
                            printf "\n### Set C: throughput, strategy %s (sender msg/s)\n\n", strategy[set]
                            print "| profile | region | payload min | header min | init | msg/s |"
                            print "|---|---|---|---|---|---|"
                        } else {
                            printf "\n### Set %s: latency, strategy %s (micros)\n\n", set, strategy[set]
                            print "| profile | region | payload min | header min | init | p50 | p99 | p99.9 | p99.99 | max |"
                            print "|---|---|---|---|---|---|---|---|---|---|"
                        }
                    }
                    printf "| %s | %s | %s | %s | %s", P[p], size(region[key]), size(pmin[key]), size(hmin[key]), I[i]
                    if (set == "C") printf " | %s", cell(rates[key])
                    else for (k = 0; k < 5; k++) printf " | %s", cell(values[key, k])
                    print " |"
                }
            }
        }'
}

# Prints a count such as 6m or 100_000 as a number, or nothing if it cannot be converted
count() {
    local n=${1//_/}
    case "$n" in
        *[kK]) n=$(( ${n%?} * 1000 ));;
        *[mM]) n=$(( ${n%?} * 1000000 ));;
        *[gG]) n=$(( ${n%?} * 1000000000 ));;
    esac 2>/dev/null || return 0
    [[ "$n" =~ ^[0-9]+$ ]] && echo "$n"
}

# Prints the runs to be made and a rough duration estimate: latency runs send at the default rate of 1m msg/s,
# throughput runs at roughly 3m msg/s, plus about 2 seconds startup per run
plan() {
    local total=0 seconds=0 set profiles cells n msgs rate
    local -a profile_list set_list init_list
    IFS=, read -r -a set_list <<< "$SETS"
    IFS=, read -r -a init_list <<< "$INITS"
    echo "Plan:"
    for set in "${set_list[@]}"; do
        if [[ "$set" == "C" ]]; then
            profiles=$THROUGHPUT_PROFILES; msgs=$THROUGHPUT_MESSAGES; rate=3000000
        else
            profiles=$PROFILES; msgs=$MESSAGES; rate=1000000
        fi
        IFS=, read -r -a profile_list <<< "$profiles"
        cells=$(( ${#profile_list[@]} * ${#init_list[@]} ))
        n=$(( cells * RUNS ))
        total=$(( total + n ))
        if [[ -n "$(count "$msgs")" && $seconds -ge 0 ]]; then
            seconds=$(( seconds + n * ($(count "$msgs") / rate + 2) ))
        else
            seconds=-1
        fi
        echo "  set $set ($(strategy "$set") strategy$([[ "$set" == "C" ]] && echo ", max rate")):" \
             "profiles $profiles x initialisers $INITS x $RUNS runs = $n runs of $msgs messages"
    done
    if [[ $seconds -ge 0 ]]; then
        echo "  total: $total runs, roughly $(( (seconds + 59) / 60 )) minutes"
    else
        echo "  total: $total runs"
    fi
}

if [[ $SUMMARY_ONLY -eq 0 ]]; then
    plan
    if [[ -z "$OUT" ]]; then
        OUT=$(mktemp -d "${TMPDIR:-/tmp}/queue-benchmarks.XXXXXX")
    fi
    mkdir -p "$OUT"
    if [[ $BUILD -eq 1 ]]; then
        ./gradlew -q :mmap-perf:installDist
    fi
    echo "Logs in: $OUT"
    echo "Runs per cell: $RUNS, messages: $MESSAGES (throughput: $THROUGHPUT_MESSAGES), warmup: $WARMUP," \
         "java: $(java -version 2>&1 | head -1)" > "$OUT/settings.txt"
    IFS=, read -r -a set_list <<< "$SETS"
    IFS=, read -r -a init_list <<< "$INITS"
    for set in "${set_list[@]}"; do
        if [[ "$set" == "C" ]]; then
            IFS=, read -r -a profile_list <<< "$THROUGHPUT_PROFILES"
        else
            IFS=, read -r -a profile_list <<< "$PROFILES"
        fi
        for prof in "${profile_list[@]}"; do
            for ((rep = 1; rep <= RUNS; rep++)); do
                for init in "${init_list[@]}"; do
                    run "$set" "$prof" "$init" "$rep"
                done
            done
        done
    done
fi

echo
if [[ -f "$OUT/settings.txt" ]]; then
    cat "$OUT/settings.txt"
fi
echo "Logs: $OUT"
summary
