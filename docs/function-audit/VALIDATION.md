# Integrated validation — 2026-11-24 (Asia/Shanghai, UTC+8)

All four streams were cherry-picked into master from the clean `89b482d` baseline.
Integration then independently reproduced and fixed six non-GC continuation defects;
see [CONTINUATIONS.md](CONTINUATIONS.md) for failing cases, source reasoning, fixes
and finite scope. This is not a universal strict-alignment certificate.

## Exact integrated results

| Gate | Result |
|---|---|
| Full native suite | **1457 total, 1452 executed, 5 existing GC skips, 0 failures/errors** |
| Full forced pure-Java suite | **1260 total, 1255 executed, 5 existing GC skips, 0 failures/errors** |
| Existing execution/skip checkers | both passed; unchanged skip allowlist and lower bounds |
| Combined line coverage | **11340 / 12449 = 91.09%**, unchanged 90% gate passed |
| Real C extensions | **60 executed, 0 failures/errors/skips**, plus **11 Python checks passed** |
| Extension report isolation | ordinary Surefire XML file set unchanged; JaCoCo session SHA-256 identical before/after extension gate |
| Benchmark utility tests | **5 passed** |
| Integrated checksum smoke | six workloads, two cold starts, four independent stock-Lua portable checks; all passed, **no timing threshold or speedup claim** |
| Audit checker contracts | **20 passed** |
| Exact C/source inventory + compiler cross-check | passed; all **35 translation units**, **921 active C functions**, no census mismatches |
| SpotBugs existing HIGH gate | passed, **0 bug instances** |
| Git diff whitespace check | passed |

The new `ContinuationAuditTest` has six witnesses on both backends; the native
fixture has four dynamic cases in native mode. The existing eight initial audit
witnesses and fourteen resource-control tests ran in both full suites. Infinite
resource-control adversaries remain subprocess-bounded. The full suite includes
all 64 generated differential seeds and existing fuzz/compiler/native API gates.

The ledger was regenerated after the final production edits: **58 Java files,
92 explicit types, 1586 explicit executables (1508 methods, 78 constructors)**.
**1559 executables remain unreviewed**; scoped review states are **13 manual,
10 behavior-tested, 4 structure-mapped**. **777/1586** have a C symbol target/candidate;
**809** do not. **272/921** active C functions have no Java symbol reference.
There are **494** raw function-like macros. These denominators include explicit
helpers/GC/platform entries and are **not conformance percentages**. New opt-in
host APIs are classified as Java helpers; they are not invented stock C mappings.
The changed broad pcall body lost its stale scoped review; the two continuation
entries cite only the scoped new evidence.

## Reproduction and environment

JDK 21, GCC 14.2.0, Python 3.13, pinned audit parsers, exact checksum-verified stock
5.3.4 source with POSIX + DLOPEN + COMPAT_5_2. The shared box lacked pt_BR, so an
isolated locale was compiled under `/workspace/resource-controls-test-environment`
and supplied with LOCPATH; no global locale or production configuration changed.
CI uses its existing pt_BR installation step.

```sh
export JAVA_HOME=/home/box/jdk/jdk-21.0.13+9
export PATH="$JAVA_HOME/bin:$PATH"
export LOCPATH=/workspace/resource-controls-test-environment/locale
sh tools/prepare_reference.sh && sh native/build.sh
sh tools/local_conformance.sh
sh tools/test_c_extensions.sh
python3 -m unittest discover -s benchmark/performance -p 'test_*.py' -v
# Choose a fresh output filename (existing evidence is intentionally not overwritten):
python3 benchmark/performance/run.py --smoke --skip-build --n 11 \
  --reference target/lua53/src/lua --output target/performance-integration-new.json
/workspace/function-audit-venv/bin/python -m unittest discover -s tools/function_audit -p 'test_*.py' -v
/workspace/function-audit-venv/bin/python tools/function_audit/inventory.py check --compiler-cross-check
./mvnw -B -Pquality spotbugs:check
```

The additive CI maintenance job runs function-audit drift/census and contract tests,
plus performance utility/checksum smoke (not benchmark time thresholds). Real C
extensions run only in the native lane, outside ordinary XML totals and coverage.
No POM changes or gate lowering were needed. Existing performance baseline numbers
predate the integrated resource-control polling and must not be called integrated
engine timings. `benchmark/performance/evidence/integrated-smoke.json` is correctness
plumbing only. Remote CI status is recorded in the delivery response, not assumed
from these local results.

---

# Initial isolated audit snapshot (historical)

Executed 2026-11-24, Asia/Shanghai (UTC+8), against Java baseline `89b482dbec88a1d84c63f0c8ea8cf96b5dfb7cc5`; no production Java/runtime source changed by this work.

Toolchain: Temurin JDK 21.0.13.1, Python 3.13, GCC 14.2.0 on Linux x86-64. Parser versions are pinned in `tools/function_audit/requirements.txt`. The exact 5.3.4 archive and every extracted C/header/Makefile were verified by the checker.

| Check | Result |
|---|---|
| Checker contract tests | **20 passed** |
| Reproducible ledger `check` | **passed** |
| Independent GCC/ELF function census | **35/35 translation units**, 921 active functions, zero mismatches |
| Native-required focused JUnit run | **20 passed, 0 failures/errors/skips** |
| Forced pure-Java focused JUnit run | **20 passed, 0 failures/errors/skips** |
| `git diff --check` | **passed** |

Each JUnit run comprised `FunctionAuditEvidenceTest` (8), `BinaryChunkTest` (5), `DumpTest` (4), and `CodegenTest` (3). These are JUnit case counts, **not** Lua-script, branch or function-coverage counts. Exact commands are in `AUDIT.md`. The eight new witnesses require their oracle scripts to exit successfully and reach a completion marker, then compare against Java, avoiding accidental “both fail early” passes.

A fresh worktree has no ignored, locally built bundled shim. The initial default run therefore fell back to Java. It is **not counted as native validation**. For the final native run, the shim was compiled only to `target/audit-libluashim.so`, its availability was confirmed (`NativeBridge.AVAILABLE=true`, `LOAD_ERROR=null`), and `-Djavalua.audit.requireNative=true` made the witnesses fail instead of silently falling back. The other final run explicitly set `-Djavalua.nonative=true`; the witnesses assert that native is disabled. No tracked or source-resource binary was rebuilt/committed.

Ledger totals: 56 production Java files; 88 explicit types; 1,568 explicit executables (1,492 methods + 76 constructors). 33 types have explicit C representation/callback name contexts. 775 executables have a symbol candidate/target; 793 have only file context/no symbol target, including Java helpers and other explicit categories. Of the 1,568 executables, **1,540 remain unreviewed**; 13 have scoped manual review, 9 scoped behavioral witnesses, and 6 inspected structural mappings. C reverse queue: 275/921 active functions have no Java symbol candidate/target, including explicitly identified CLI/GC functions. Raw function-like macros: 494, with all conditional definitions counted separately.

The counts do not establish strict port completeness. No whole-suite, CI, performance or resource-control validation is claimed here. Integration that changes production files must regenerate/check the ledger and re-audit any stale scoped evidence. Raw Surefire/compiler outputs are disposable under `target/`; the durable commands, test sources, fingerprints and results are retained here.
