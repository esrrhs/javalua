# Reproducible Lua 5.3.4 differential gates

Requirements: JDK 21, make/C compiler, Python 3, curl, sha256sum, Linux x86-64
(the binary serializer's fixed ABI), and a `pt_BR.UTF-8` locale for native locale tests.
No new dependencies or scheduled workflow are needed.

```sh
sh tools/prepare_reference.sh  # SHA-256 pinned official source AND test archives
sh native/build.sh            # JAVA_HOME must identify the existing JDK if not on PATH
sh tools/local_conformance.sh # full native + Java, test-count gates, merged >=90% lines
```

The CI matrix runs the same full suite in independent native/Java jobs with
`fail-fast: false`. Java mode still has a bundled shim: `ExecutionModeTest` checks
`AVAILABLE`, the disable reason and Linux's mapped libraries to prove no shim was
loaded. Native mode makes an actual JNI call and must have the shim mapped.
`-Djavalua.requireReference=true` and the gate checker prohibit missing reference
binaries/helpers/corpus and any non-GC skipped tests. Explicit `LUA53_BIN` and
`LUAC53_BIN` overrides fail if wrong; both must identify **5.3.4**, not just 5.3.
The separate coverage job merges exactly two lane files; the gate remains 90%.

## Generated coverage (both backends)

* Existing `FuzzTest`: 1500 grammar programs, 600 coroutine programs, 400 self
  dump/load programs. These keep their historical normalized comparisons and
  documented platform allowances. They are now mandatory in **both** CI lanes.
* New `GeneratedDiffTest`: 64 seeds (`534000..534063`), with typed recursive integer
  expressions (overflow/bit operations/shifts), sparse-table mutation/length,
  deterministic content observation, closed upvalues, bounded goto/repeat/while,
  multi-return/tail calls, metamethod dispatch, coroutine/pcall yields, forbidden
  C-boundary yields, protected arithmetic errors, syntax/load errors and a final
  unprotected error. Every family executes for every seed; a completion sentinel
  prevents early failures from looking like coverage.
* Each new seed checks **exact serialized bytecode**, with debug data and stripped,
  and runs source in both engines, stock chunk in Java, and Java chunk in stock Lua.
  That is 2 byte comparisons and 8 execution paths per seed. Comparisons retain
  literal output/error bytes plus protected-call status (`lua-status` 0/2) and host
  exit status. Errors inside separately loaded callbacks have fixed source names
  so stripping does not require masking error messages.
* New generated cases have no Diff output normalization or platform skips. They
  avoid GC, random sequences, libm/NaN, addresses, locale and hash iteration order;
  table content is checked via slots/count/checksum, **not** by discarding output.

Every source is written before execution under `target/generated-diff/<mode>/seed-*`.
Chunks, compiler output and both outputs remain there, including on failure.
CI uploads them, the old `target/fuzz` reproducers, `target/rt`, Surefire reports and
coverage data with a separate artifact per mode. Oracle processes have a 10-second
wall limit and 1 MiB output assertion; generated Lua has a one-million-instruction
hook budget, bounded control flow and small allocations. The outer 30-minute
runner captures JVM stacks then terminates a stuck suite. Output limits are
post-execution assertions (loops and instruction budget bound ordinary output).

## Longer optional local campaign / reproduce one seed

```sh
export LUA53_BIN=target/lua53/src/lua LUAC53_BIN=target/lua53/src/luac
export FKDUMP_BIN=target/lua53/src/fkdump TBLSTRUCT_BIN=target/lua53/src/tblstruct
export LUA53_TESTS=target/lua53-tests
# New corpus: start at any seed, independent of previous cases; 1..100000 cases.
python3 tools/run_bounded.py --seconds 3600 -- ./mvnw -B test \
  -Dtest=GeneratedDiffTest -Ddiff.seed=534064 -Ddiff.cases=10000 \
  -Djavalua.requireReference=true -Djavalua.test.mode=native -Djacoco.check.skip=true
# Repeat with -Djavalua.test.mode=java -Djavalua.nonative=true.
# Reproduce exactly one: -Ddiff.seed=<failed seed> -Ddiff.cases=1.
# Extend the original grammar without rerunning its prefix:
python3 tools/run_bounded.py --seconds 3600 -- ./mvnw -B test \
  -Dtest=FuzzTest -Dfuzz.seed=1500 -Dfuzz.seeds=20000 \
  -Dfuzz.coSeeds=5000 -Dfuzz.chunkSeeds=2000 -Djacoco.check.skip=true \
  -Djunit.jupiter.execution.timeout.default=3600s
```

The `fuzz.seed` offset is added to coroutine base 100000; dump/load artifact names
use base 200000. Longer campaigns are opt-in and do not weaken normal CI counts.
GC remains the existing five named skip allowances, not a new broad exclusion.
A green finite campaign is evidence for its corpus, **not** proof of equivalence.

`NativeRealExtTest` remains opt-in: tests requiring separately installed third-party C extensions are not created without their fixtures. No Lua semantic tests are excluded for Java mode. Existing native C-API/locale-only factories remain inapplicable there.

Both lanes run the same 21 upstream differential cases, including `literals.lua`. In Java mode only the two `pt_BR`/`ptb` locale capability probes return nil on both engines (identical init); every other lexical assertion still runs. The native lane also executes the comma-locale block. The checker rejects a Java lane with only 20 upstream cases.
