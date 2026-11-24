# Real Lua 5.3 C extension gate (native only)

This is a separate, mandatory-when-invoked integration gate, not another set of
hand-written C API probes. It compiles **unmodified real upstream source**, loads
it through `require`/`package.loadlib`, and runs the same scripts on PUC-Rio 5.3.4
and JavaLua. Nothing here asserts arbitrary extension compatibility.

## Run

Prerequisites: JDK 21+, a C compiler (`CC`, default `cc`), make, Python with
`tarfile`'s `data` extraction filter (Python >=3.11.8 / >=3.12), Linux or macOS.
Linux x86-64 is the validated platform; macOS build flags are provided but have
not been validated by this change. The existing native bridge must be usable.
No additional system Lua, luarocks, installed extensions, locale pack or external
C library is needed. The full native/nonative CI matrix remains unchanged.

```sh
# Existing oracle setup, needed once in a fresh checkout (network, SHA256 checked):
sh tools/prepare_reference.sh
# Rebuild shim and both header variants, then run the strict gate:
sh tools/test_c_extensions.sh
```

The extension gate itself has **no network downloads**. Three original archives
(~180 kB total) are checked in under `src/test/resources/c-extensions/upstream`.
Every invocation verifies *all* SHA256 hashes before extraction/compilation. No
fallback, system-installed module, toy implementation, assume/skip or conditional
module omission is allowed. Missing archive, bad checksum, wrong/missing oracle,
compile/link error, unavailable native bridge, native-disabled mode, incomplete
factory, script error, output difference or timeout makes the gate fail.

`tools/prepare_reference.sh` already fails on download errors/hash mismatches;
a network failure cannot turn the mandatory gate into skipped tests. The normal
Maven wrapper/dependency cache is still required, as with the existing CI.

## Pinned fixtures and limits

The lock file `src/test/resources/c-extensions/manifest.json` records URLs,
release/tag, resolved Git revisions where available, archive checksums, source
file lists and license locations. Archives are untouched and contain their full
original notices; readable MIT notices are also in `licenses/`. The cjson archive
additionally retains David Gay/Lucent notices in `dtoa.c`/`g_fmt.c`; this build uses
upstream `fpconv.c`, not those optional files. The bundled RFC 4627 text retains
its Internet Society/IETF copyright notice; it is not compiled. The original
archives total exactly 180,363 bytes (580,373 bytes of expanded files), contain
only regular files/directories, and live under test resources, not the production
artifact. These versions are compatibility
fixtures, **not application runtime dependencies**.

| Module | Pin | Coverage exercised (5 cases each) |
| --- | --- | --- |
| LPeg | 1.1.0 official release | loader/cache/explicit `luaopen` and missing-symbol error; grammar, folds, substitutions/table captures; C-to-Lua match-time/capture callbacks with multiple results and 600 captures; repeated callback table-error propagation/recovery; non-yieldable C callback boundary |
| lua-cjson | mpx 2.1.0, `4bc5e917c8cd5fc2f6b217512ef530007529322f` | loader/cache, nested decoding/array encoding/null lightuserdata, binary bytes and 80-KiB string buffers, UTF-16 surrogate decoding, decoder errors/depth limits/recovery, independent C closure/config userdata |
| LuaFileSystem | v1_8_0 / 1.8.0, `7c6e1b013caec0602ca4796df3b1d7253a2dd258` | loader/cache, real mkdir/files/attributes/touch, C directory iterator and userdata, errno/argument errors, 200 explicit close/use-after-close cycles |

For each module we build a `.so` against **stock 5.3.4 headers** and another
against **our shim headers** (six builds). Both binaries run on **both engines**
to catch header/ABI drift. Each of 15 scripts runs in main-thread and coroutine
contexts: **60 differential JUnit cases / 120 engine script executions**.

The stock execution must exit zero and reach a per-case completion sentinel. The
Java execution must return no uncaught error. stdout/error messages are compared
byte-for-byte (Latin-1 byte mapping), with **no traceback/address normalization**.
Assertions also check meaningful behavior, return arity/types and recovery, so
"both engines failed the same way" cannot pass. String-key traversal order and
random addresses are not printed. `LC_ALL=C` is set for reproducible error text.

This does **not** establish support for LuaSocket/LuaSec/POSIX libraries, modules
that access Lua's private structs, other Lua ABIs, arbitrary module releases,
Windows, allocator replacement, finalizer/GC timing, cross-C-frame continuations,
thread safety or all functions of even these three modules. Those are untested or
outside this gate's scope, not silently classified as compatible. Resources in
LFS cases are explicitly closed; GC conformance is intentionally not tested.

## CI wiring

The workflow runs this gate only in the native conformance lane, after the
existing full-suite execution/skip checks. The POM is unchanged. `RealExtensionCompatibilityIT` deliberately does
not match Surefire's normal `*Test` discovery, so normal native/nonative full-suite
counts and the five-GC-skip allowlist are not changed.

The step in `.github/workflows/maven.yml` is **after** the existing executed-test
check in the **native** `conformance` matrix lane, before diagnostics upload:

```yaml
- name: Real upstream C extension compatibility
  if: matrix.mode == 'native'
  timeout-minutes: 8
  run: sh tools/test_c_extensions.sh
```

The existing full-suite invocation, native/java matrix, gate counts and coverage
merge are unchanged. The lane artifact upload uses `if: always()` and includes
`reports/c-extension/latest/`, preserving build/test diagnostics even on failure.
The script moves its XML from Surefire to that directory, so the suite is present
only once in artifacts and is not added to ordinary full-suite XML totals. The script disables
JaCoCo for its dedicated invocation, leaving the existing native/java execution
data untouched. It runs 11 Python tooling/negative-control checks as well as the
60-case integration suite and enforces exactly 60 executed, zero skips/errors.

For an isolated local developer invocation after preparation:

```sh
python3 tools/prepare_c_extensions.py
LUA53_BIN=target/lua53/src/lua ./mvnw -B \
  -Dtest=RealExtensionCompatibilityIT -Djacoco.skip=true test
python3 tools/check_c_extension_gate.py \
  target/surefire-reports/TEST-com.github.esrrhs.javalua.RealExtensionCompatibilityIT.xml
python3 -m unittest discover -s tools -p test_c_extension_tools.py -v
```

The older `NativeRealExtTest` remains an ad-hoc environment-variable-driven probe
for a broader set of modules; its optional skips are **not** evidence this gate
ran. This gate never consults `LPEG_SRC`/`CJSON_SRC`/`LFS_SRC`.

## Outputs and maintenance

Generated `.so`/source trees are disposable in `target/c-extensions`; fixtures,
lock file and tests are durable source. Logs plus relocated Surefire XML live in
`reports/c-extension/latest` (outside `target`, so `mvn clean` does not remove the
last diagnostic report). Only a fresh report is accepted; the prior gate XML is
removed before startup. Overall Maven execution is process-tree bounded to 300
seconds (`C_EXTENSION_SECONDS` override), individual fixture compiles to 120s.

To update a fixture deliberately: obtain the original release archive, review its
license and source changes, replace its archive and update the recorded checksum
and revision, then rerun both header variants and review differential results.
Changing the 15-case corpus also requires intentionally updating the exact count
in the Java factory and report checker. Do not weaken counts or skip a failing
module just to make the build green.
