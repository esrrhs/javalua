# Real upstream C extension validation

Validated 2026-11-24 (Asia/Shanghai, UTC+8), Linux x86-64, JDK 21.0.13,
GCC/cc, against unmodified PUC-Rio Lua 5.3.4.

Branch: `iter/c-extension`.
Base: `89b482dbec88a1d84c63f0c8ea8cf96b5dfb7cc5`.
Only the isolated worktree was changed; no POM/engine modifications. A minimal
additive native-only CI step and always-upload diagnostics path are now wired.

## Implemented

- Offline SHA256-locked original LPeg 1.1.0, mpx lua-cjson 2.1.0 and
  LuaFileSystem 1.8.0 release archives, upstream revisions and MIT notices.
- Six real module builds (stock/shim headers), both variants loaded on both
  engines in main/coroutine contexts. No generated toy replacement code.
- Fifteen assertion-rich scripts: loading/cache, actual module work, callback
  stack growth/return values, error object identity/recovery, no-yield boundary,
  userdata/closure state, large buffers, errno and explicitly closed iterators.
- Strict byte output comparison plus completion and success checks; no output
  normalization, no silent skip/fallback, fixed case-count gate and process bound.
- Additive native-only script, with 11 fail-closed infrastructure tests.

## Executed evidence

1. `sh tools/test_c_extensions.sh`: **60 tests, 0 failures, 0 errors, 0 skipped**;
   six builds successful, 120 engine script executions. Script also runs the
   **11 Python tooling checks**, all passed. Final wall time ~11 seconds on this
   workstation with a warm Maven cache; no extension downloads involved.
2. `LUA53_BIN=target/lua53/src/lua ./mvnw -B
   -Dtest=NativeApiTest,NativeExtTest,NativeSymbolTest,RealExtensionCompatibilityIT
   -Djacoco.check.skip=true test`: **248 tests, 0 failures/errors/skips**:
   NativeApiTest 167, NativeExtTest 18, NativeSymbolTest 3, real modules 60.
3. Explicit negative control:
   `./mvnw -B -Dtest=RealExtensionCompatibilityIT -Djavalua.nonative=true
   -Djacoco.skip=true test` was **rejected**, exit 1, with
   `This gate requires native mode`. This failure is expected, not a residual
   failing supported test.
4. `python3 -m unittest discover -s tools -p test_c_extension_tools.py -v`:
   **11/11 passed**, including corrupt archive, compiler failure, wrong oracle,
   missing/partial report, skipped/failing cases and wrong-suite rejection.
5. `sh -n tools/test_c_extensions.sh`, Python compile checks and
   `git diff --check`: passed.

Initial harness smoke testing caught and corrected a **test driver** stack-base
mistake (LuaState reserves stack slot zero); it was not an engine/shim bug.
The final corpus found **no new concrete shim defect**, so no speculative engine
changes were made. These passing cases are not proof of universal compatibility.

## Scope and integration

Supported by evidence: the tested behaviors of these exact three releases on
Linux/native. Pure Java cannot load C extensions and is explicitly rejected by
this gate; its existing full test lane remains unchanged. GC/finalizer timing
is excluded. LuaSocket/LuaSec/POSIX and other releases/private-API extensions
were **not tested**, not declared supported or declared broken.

Integration documentation: `tools/C_EXTENSION_TESTING.md`. The native-only
conformance step is now wired **after existing full-suite gate checks** and its
diagnostics directory is included by the existing `if: always()` artifact step.
The dedicated invocation disables JaCoCo to preserve lane data. Parent only needs
to integrate both local commits; no further extension-gate wiring is required.
No push or hosted CI run was performed.

Durable checked-in inputs/report live outside generated directories. Local raw
logs/XML are in `reports/c-extension/latest/`, with the broader regression log in
`reports/c-extension/native-regressions.log` and negative-control log in
`reports/c-extension/nonative-rejection.log` (ignored generated diagnostics).

## CI follow-up validation

- Workflow YAML parsed successfully. Structural comparison against the previous
  commit proves only the native extension step and diagnostics path were added;
  native/java matrix, full-suite commands/counts, coverage jobs and other jobs
  are identical.
- Re-ran the exact native CI command, `sh tools/test_c_extensions.sh`: 60/60
  integration cases and 11/11 tooling tests passed, no skips/failures/errors.
- `target/jacoco.exec` SHA256 before/after remained
  `8b7355dfe63281c5ea35da16312c3ce3de1e21b5d3a162fe8c5fb31ebf4b87e8`.
  The three existing ordinary native regression-suite XML files were byte-identical.
- The dedicated XML is moved, not copied, into `reports/c-extension/latest`.
  Thus ordinary suite totals remain unchanged and uploaded report paths contain
  only one copy of the extension suite (no double-counting).
- Original archives total 180,363 bytes compressed, 580,373 bytes expanded,
  with only regular files/directories. Reviewed MIT notices are retained;
  cjson additionally retains Lucent source notices and the Internet Society/IETF
  notice in bundled RFC 4627. No third-party source was modified, no binary
  runtime dependency was added, and these are test-only resources.
