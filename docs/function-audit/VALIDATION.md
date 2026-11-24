# Validation snapshot

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
