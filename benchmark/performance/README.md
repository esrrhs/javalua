# Reproducible performance diagnostics

This supplements (does not replace) `../RunBenchmark.java`. Inventory before this
addition: the legacy runner times compilation + execution once after library setup;
`test_simple.lua` is plumbing, and `gc_simple.lua`, `gc_precise.lua`,
`gc_benchmark.lua` are large table/GC ballpark scripts. None has forked warmup,
repeat distributions, validated results or structured environment capture. There
was no JMH suite. Keep those scripts, but do not use their numbers as regression gates.

## Run (JDK 21+, Python 3, no new Maven dependencies)

From the repository root, with the desired JDK's `bin` on `PATH` (or set `JAVA_HOME`):

```sh
# CI-safe correctness/plumbing: fail on wrong checksums/errors, NEVER on time.
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s benchmark/performance -p 'test_*.py'
python3 benchmark/performance/run.py --smoke --output /tmp/perf-smoke-unique.json

# Local performance: 3 fresh JVMs, at least 15 warmup batches AND 1s/case/fork, 9 samples,
# n=10000 logical iterations, batch=3, plus 7 fresh-process startup trials.
python3 benchmark/performance/run.py --output /tmp/perf-baseline-a-unique.json
python3 benchmark/performance/run.py --skip-build --output /tmp/perf-baseline-b-unique.json

# Optional oracle: validates four portable scripts on EXACT stock Lua 5.3.4.
python3 benchmark/performance/run.py --smoke --reference /path/to/lua-5.3.4/src/lua \
  --output /tmp/perf-reference-unique.json
```

`--skip-build` skips Maven only; the standalone harness is always recompiled.
Output files must not exist. Choose a new path per invocation. `--n`, `--batch`,
`--warmup`, `--warmup-ms`, `--samples`, `--forks`, `--cold` explicitly override defaults;
`--smoke` numbers are **not** a baseline. All samples and checksums, exact VM
command, source SHA-256s, Git SHA/dirty state, JDK, OS, CPU, affinity, host memory,
load averages and external Java options are recorded in JSON. SHA/dirty state
records the pre-run state; uncommitted benchmark source is identified by hashes.
Environment capture is diagnostic, not hardware isolation: cgroup CPU/memory quotas,
power policy and other agents' activity can still affect results.

Default VM options: `-Xms256m -Xmx256m -Djavalua.nonative=true`.
`BENCH_JAVA_OPTS` replaces the **whole** list, e.g. to use native:

```sh
BENCH_JAVA_OPTS='-Xms256m -Xmx256m -Djavalua.luashim=/absolute/libluashim.so' \
  python3 benchmark/performance/run.py --output /tmp/perf-native-unique.json
```

Check each fork's `metadata.native_available`; a missing shim silently falls back
by design in the engine. Setting `-Djavalua.nonative=false` still disables native
(property presence matters). Do not compare different backends/heaps/JDK options
as if they were the same benchmark. Avoid VM options that print non-JSON to stdout
in `run.py`; profile the harness directly instead (below).

## What is (and is not) measured

| Case | Timed work | Logical iteration / checksum |
|---|---|---|
| cold process | fresh JVM launch through exit, state, open libraries, parse+execute `return 42`, close | checksum 42; OS page caches are NOT flushed |
| arithmetic | precompiled Lua integer loop | `3 * n*(n+1)/2` |
| tables | build a dense positive array + negative integer hash keys, then read both | `3 * n*(n+1)/2` |
| strings | decimal format, reverse, substring, byte | `sum(52 + i%10)` |
| allocations | construct/discard a small mixed table each iteration | `n*(n+1)/2 + n` |
| Java → Lua | Java repeatedly passes one integer to precompiled Lua `i+1` and reads result | `n*(n+1)/2 + n` |
| Lua → Java | Lua integer loop calls registered Java `LuaFunction`, passes/returns integer | `n*(n+1)/2 + n` |

Warm cases exclude file I/O, compilation, state/library setup, closing and warmup;
each case has its own state and runs sequentially on the calling platform thread.
Case order rotates between forks to expose ordering sensitivity. Warmup uses both
a minimum batch count and duration, recording actual warmup batches/time; this is
still not a guarantee of JIT convergence (inspect distributions). All batches,
including warmup, validate a mathematical checksum; the result also escapes to a
volatile sink. Per-sample checksum is the case checksum times `batch`. Boundary
cases use low-level Java embedding calls (including package-private stack reset),
**not JNI/C extension calls**; per-call wrapper
and result handling are included. This is an end-to-end workload harness, not a
claim about a single opcode's cost. There are no coroutine/virtual-thread cases.

Cold wall time includes process spawning/class initialization/exit; the secondary
`setup_execute_close_ns` starts in `main`, after JVM and harness static telemetry
initialization. Neither is warmed throughput. "Cold" means a fresh JVM, not a
machine reboot or cold filesystem. Stock Lua is deliberately only a **checksum
oracle**, not a timing comparator: comparing its CLI process cost to Java warmed
VM throughput, or its allocator byte count to the JVM heap, would be misleading.
The two boundary scripts require a host and are excluded from the stock oracle.

Allocation telemetry uses HotSpot/OpenJDK's optional ThreadMXBean cumulative
**bytes allocated on this thread**; unsupported is `-1`/summary `null`. It includes
VM and harness-call allocations, not live/retained size, not native memory, and
not allocations on background/other threads. Heap endpoints are non-forced-GC
occupancy only; they can go down across a sample. GC counts are process-wide and
may include unrelated background work. Never compare these byte counts to C Lua's
`collectgarbage('count')`. No forced collection is inserted between samples.

JSON summaries give inclusive-interpolated quartiles, median/min/max, all fork
medians, allocation bytes/iteration, and observed collection counts. Pooled
samples are correlated; fork medians matter more than treating every row as an
independent statistical trial. The harness is dependency-free and intentionally
not advertised as JMH-grade isolation. For publication-grade microbenchmarks,
port isolated operations to JMH and use controlled dedicated hardware.

## Profile a hotspot; do not guess an optimization

After a runner invocation has compiled the harness:

```sh
java -Xms256m -Xmx256m -Djavalua.nonative=true \
  -XX:StartFlightRecording=filename=target/performance-profile.jfr,settings=profile,dumponexit=true \
  -cp target/classes:target/performance-classes \
  com.github.esrrhs.javalua.PerformanceHarness \
  benchmark/performance/workloads 20000 10 40 15 0 1000 > target/performance-profile.jsonl
jfr print --json --events jdk.ExecutionSample,jdk.ObjectAllocationSample \
  target/performance-profile.jfr > target/performance-profile-events.json
python3 benchmark/performance/summarize_jfr.py target/performance-profile-events.json \
  > /tmp/profile-summary-unique.json
```

This profiles a longer **mixed** run, including setup and warmup. Execution sample
counts and allocation weights are approximate, not exact per-case cost attribution.
Profiled wall times are NOT comparable with unprofiled baselines. Keep raw JFRs in
`target` locally (rebuildable, potentially large); commit only small aggregates and
selected raw benchmark JSON under durable `benchmark/performance/evidence/`.

For a proposed optimization: first identify cost in profiles, prove its semantic
safety, then use alternating baseline/candidate builds with identical harness,
flags, workload and enough independent forks on an otherwise idle machine.
Compare distributions and allocation evidence, not one best sample. Run strict
5.3.4 Diff regression and both engine backends where relevant. If evidence is
unstable or semantic risk is unclear, make **no engine change**; record that result.

## Integration

This directory is additive: no production sources, root POM or workflows changed.
A parent CI job may run the two smoke commands above; do not add noisy performance
thresholds. Existing correctness/differential CI remains authoritative. Baseline
numbers are diagnostic artifacts, not a promise of speed or a pass/fail gate.
See `RESULTS.md` for the recorded shared-machine investigation and decision.
