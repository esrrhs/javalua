# Shared-box performance investigation — 2026-11-24 (Asia/Shanghai, UTC+8)

## Outcome

Delivered repeatable diagnostic baselines, cold startup separated from warmed
execution, both Java↔Lua directions, allocation/heap/GC observations, checksum
smoke and a sampled hotspot investigation. **No engine optimization selected or
speedup claimed.** Default Lua 5.3.4 behavior is unchanged; no production source,
root POM or CI files were edited.

Two identical-code warmed runs show large timing spread on the shared box. An
allocation-sensitive candidate (CallInfo reuse) is worth a future isolated study,
but is not demonstrably low-risk here: CallInfo carries hook, yield, continuation,
error-recovery and previous-frame state. Reusing it without a complete lifecycle
proof risks observable debug/coroutine behavior. Table preallocation/pooling can
change Lua 5.3.4 array/hash layout and iteration/length behavior; it was not applied.
LuaValue pooling is likewise not a mechanical change because values are mutable
and can escape. The arithmetic workload's steady allocation count is already
approximately zero after sufficient warmup, so an apparent allocation fix based
on the first run would have been misleading.

## Environment and protocol

- Base: `89b482dbec88a1d84c63f0c8ea8cf96b5dfb7cc5` (`master`). Dedicated
  `iter/performance` worktree; main worktree untouched.
- Linux 6.12.113+, x86_64, glibc 2.41; Intel Xeon processor, 8 visible CPUs,
  affinity 0–7. Shared machine, not pinned or isolated; concurrent agents were
  allowed. No CPU-model/generation or reserved-core guarantee is inferred.
- Temurin OpenJDK 21.0.13+9, 64-bit server VM, fixed `-Xms256m -Xmx256m`,
  `-Djavalua.nonative=true`. All warm fork metadata reports native unavailable as
  expected. No JaCoCo/profiling agent in timing baselines.
- Each final run: 7 fresh-JVM startups; 3 independent warmed JVM forks; each case
  at least 15 warmup batches **and 1000 ms** warmup, then 9 measured batches;
  each batch 3 calls × 10000 logical iterations. Orders rotate across forks.
- Per-case actual warmup counts/time and every timed sample are in raw JSON.
  Summaries below pool 27 correlated samples; independent fork medians are also
  shown. Every batch validates mathematical checksums. Stock POSIX Lua 5.3.4
  independently checked the four portable workloads, not the host-boundary ones.
- Raw JSON captures exact commands, hashes, environment, dirty state and checksums.
  Benchmark files were still untracked during initial measurements, deliberately
  visible in captured Git status; source SHA-256s identify the harness precisely.
  Final harness SHA-256:
  `befce8c675001c15f71267d5036cb5292eef4c27d61833b48a2502c55eb5494b`.

## Recorded timings: same code, NOT before/after optimization

`warmed-a.json` and `warmed-b.json` are the final protocol. Units are milliseconds
per measured batch (30000 logical iterations); do not compare different rows as
identical operations. A row may execute multiple VM/library operations per iteration.

| Case | A median [min, max] | B median [min, max] | A fork medians | B fork medians |
|---|---|---|---|---|
| arithmetic | 0.498 [0.412, 12.513] | 0.495 [0.396, 4.502] | 0.421 / 0.490 / 0.504 | 0.406 / 0.495 / 0.526 |
| tables | 23.370 [14.991, 37.341] | 19.339 [9.645, 32.955] | 20.150 / 25.289 / 23.108 | 22.768 / 12.843 / 20.925 |
| strings | 34.030 [18.714, 72.715] | 26.975 [14.011, 40.889] | 35.024 / 37.359 / 25.049 | 26.975 / 25.491 / 31.527 |
| Java → Lua | 1.364 [1.171, 9.716] | 1.237 [1.101, 12.852] | 1.304 / 1.362 / 1.750 | 1.115 / 1.740 / 1.278 |
| Lua → Java | 1.457 [0.963, 25.791] | 1.348 [0.991, 6.793] | 1.424 / 1.761 / 1.274 | 1.382 / 1.465 / 1.025 |
| table allocation | 3.101 [2.712, 14.766] | 3.353 [2.768, 13.523] | 3.101 / 2.841 / 3.378 | 3.306 / 2.889 / 7.382 |

Fresh-process startup wall time: A **264.753 ms** median [220.703, 354.959];
B **241.665 ms** [232.544, 265.974]. These include JVM spawn, class loading,
state/library setup, compilation, execution, close and exit, not merely engine
construction. They do not flush OS page caches.

Host 1-minute load rose from 9.15 to 12.40 during A, then 12.36 to 13.73 during B,
with only 8 visible CPUs. This and the very long tails prevent attributing a small
change in timings to code. B being faster in several rows despite no engine edit
is itself a useful warning against reporting a false improvement.

Median thread allocation bytes/logical iteration (A and B approximately equal):
arithmetic **0.021**, tables **1194.95**, strings **832.02**, Java→Lua **96.00**,
Lua→Java **96.02**, table allocation **328.02**. These are cumulative allocation
traffic, including harness wrappers, not retained heap or C Lua allocator bytes.
Heap endpoints and GC counts remain in JSON; no forced collection was used.

## Warmup finding (retained evidence)

`baseline-a.json` / `baseline-b.json` are **preliminary**, not the final protocol:
15 fixed-count warmup batches with no duration floor. Arithmetic fork medians
ranged from 0.467 to 13.409 ms in B, and median reported allocation was 80 bytes/
iteration. The mixed JFR investigation and longer warmup showed much of that
allocation disappearing under JIT optimization. This justified improving the
**measurement harness** to require minimum elapsed warmup, rather than modifying
the engine in response to an under-warmed sample. The preliminary and final
protocols must not be called an engine before/after comparison. More warmup alone
does not remove shared-host scheduling/GC/JIT effects or prove full convergence.

## Hotspot investigation

`profile-summary.json` aggregates a separate longer run of the final harness,
2026-11-24 **18:36:31–18:36:56 Asia/Shanghai (UTC+8)**. Includes warmup/setup;
profiled timings were excluded from the baseline table. 1342 main-thread execution
samples and 7098 main-thread allocation sample events were observed.

- Execution leaf samples: `Vm.execute` 388; `LuaTable.setNodeVector` 102;
  `LuaState.prepCallInfo` 78; `LuaValue.isNil` 67; `LuaTable.resize` and
  `Vm.forPrep` 53 each; `LuaTable.newKey` and `StringPool.intern` 49 each.
- Inclusive frame samples: `Vm.execute` 712, `LuaTable.ensureSlot` 294,
  `LuaTable.rehash` 261, `LuaTable.resize` 244. Inclusive frames overlap and are
  not percentages summing to 100.
- Top sampled allocation classes by approximate weight: LuaValue, CallInfo,
  byte arrays, LuaTable.Node, String. First-project-frame allocation attribution
  highlights `prepCallInfo`, table node/array allocation, `LuaValue.ofInteger`,
  `LuaAux.Buf` and string formatting. See aggregate for weights; sampled weights
  are approximate and include setup, warmup and harness calls, not exact totals.

Inspection confirmed `LuaState.prepCallInfo` allocates a fresh frame and
`CallInfo` contains resumable/hook/error state. No safe one-line reuse optimization
was established. This is a concrete future profiling target, **not a claim that
pooling it is correct or measurably beneficial**. Table allocation is expected in
the chosen scripts; pooling/reordering its internals is outside a low-risk patch.

## Exact commands used

All commands were run in `/workspace/github/javalua-performance`. For the
following commands the JDK was selected using:

```sh
export PATH=/home/box/jdk/jdk-21.0.13+9/bin:$PATH
./mvnw -q -DskipTests compile
```

Reference source was copied from `/workspace/lua-sources/lua-5.3.4` into this
worktree's `target/lua53`. `make linux` initially failed because optional readline
was unavailable; `make clean` then `make posix -j2` in `target/lua53` succeeded.
No reference source or host tooling was modified. The POSIX reference binary
hash is recorded in B/final timing JSON; it identifies as Lua 5.3.4. The first
regression run exposed a reference configuration mismatch (POSIX-only omitted
dlopen): 2 RuntimeTest package matrices expected dynamic-loader errors but got
`absent`. The regression reference was then rebuilt, without readline, using:

```sh
make -C target/lua53/src clean
make -C target/lua53/src all -j2 SYSCFLAGS='-DLUA_USE_POSIX -DLUA_USE_DLOPEN' SYSLIBS='-Wl,-E -ldl'
```

The entire same 7-class subset was rerun; the initial counts/setup explanation
are retained in `evidence/regression-initial-reference-setup.json`. Portable
benchmark checksums did not depend on dlopen. The final regression summary
records the new binary hash, so the two reference configurations are explicit.

```sh
# Preliminary fixed-count-only warmup, on the initial harness version:
python3 benchmark/performance/run.py --skip-build --output benchmark/performance/evidence/baseline-a.json
python3 benchmark/performance/run.py --skip-build --reference target/lua53/src/lua --output benchmark/performance/evidence/baseline-b.json

# Final minimum-duration warmup, no engine change between runs:
python3 benchmark/performance/run.py --skip-build --reference target/lua53/src/lua --output benchmark/performance/evidence/warmed-a.json
python3 benchmark/performance/run.py --skip-build --reference target/lua53/src/lua --output benchmark/performance/evidence/warmed-b.json

# Final profiling invocation; jfr + summarizer commands are in README.md:
java -Xms256m -Xmx256m -Djavalua.nonative=true \
  -XX:StartFlightRecording=filename=target/performance-profile-final.jfr,settings=profile,dumponexit=true \
  -cp target/classes:target/performance-classes \
  com.github.esrrhs.javalua.PerformanceHarness benchmark/performance/workloads 20000 10 40 15 0 1000

# Correctness-only harness checks:
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s benchmark/performance -p 'test_*.py'
python3 benchmark/performance/run.py --smoke --skip-build --n 11 \
  --reference target/lua53/src/lua --output benchmark/performance/evidence/smoke-final.json

# Relevant strict reference Diff regression, no coverage threshold on this subset:
LUA53_BIN=target/lua53/src/lua LUAC53_BIN=target/lua53/src/luac \
  ./mvnw -q -Dtest=GeneratedDiffTest,FuzzTest,ArithGridTest,TableLibTest,StringLibTest,RuntimeTest,ClosureTest \
  -Djavalua.nonative=true -Djacoco.skip=true test
```

Output paths above now exist: use new names when rerunning. `smoke.json` is the
initial plumbing run (also not a performance baseline). Only small JSON evidence
is committed; large JFR/event exports and Maven outputs are rebuildable in target.

## Validation and integration

- Harness utility tests: **5 passed** (statistics, expected sums, overwrite
  refusal, invalid bounds and main-thread JFR aggregation).
- Final six-workload smoke (`n=11`, one fork, two samples) and two cold JVM trials:
  all checksums passed. Four portable scripts independently matched Lua 5.3.4.
- Full warmed timing runs: all warmup and sample checksums passed in both runs;
  four reference checksums passed each time.
- Relevant strict 5.3.4 regression: **467 tests, 466 passed, 0 failures, 0 errors,
  1 documented GC/weak/finalizer skip**, across 7 classes, including all 64
  generated differential seeds and 3 fuzz tests. Exit code 0. Details and reference
  configuration are in `evidence/regression-summary.json`. This was a relevant
  subset, not a claim of rerunning the whole native/nonative matrix.

No CI/POM integration conflicts: cherry-pick the local performance commit, then
optionally add README's fast unit/smoke commands to an existing CI lane. Do not
make baseline elapsed times a gate. This stream did not push or integrate into
master. No GC compatibility promise is added.
