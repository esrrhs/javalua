# Lua 5.3.4 function-level port ledger

This is a **starting audit ledger, not a certificate of strict equivalence**. It inventories every explicit production Java type and executable, names possible C counterparts, and separates a small set of scoped reviews/witnesses from the large unreviewed remainder. A name match or green aggregate suite never upgrades a review state.

## Files

- [`mapping.json`](mapping.json): maintained file contexts and explicit declaration overrides. Edit this when reviewing a function or classifying a new source file.
- [`inventory.json`](inventory.json): reproducible machine-readable Java declarations, source fingerprints, C function/macro census, targets/candidates and evidence. IDs include source path, nested owner and parameter types; overloads stay separate.
- [`SUMMARY.md`](SUMMARY.md): generated denominators, per-file progress and reverse C triage queue.
- [`AUDIT.md`](AUDIT.md): actual representative review, limitations, commands and remaining work.
- [`../../tools/function_audit/`](../../tools/function_audit/): generator/checker, pinned parser dependencies and checker contract tests.

The old `target/reports/pairwise-534-compare.md` was **not present in this checkout**. None of its earlier conclusions are inherited. Reports under `target/` are disposable; this ledger lives with the code.

## Exact comparison baseline

Java baseline: `89b482dbec88a1d84c63f0c8ea8cf96b5dfb7cc5`. After integration, use the complete production-source SHA-256 manifest, not HEAD equality (documentation/test commits legitimately change HEAD).

C is the **unpatched PUC-Rio Lua 5.3.4** archive at <https://www.lua.org/ftp/lua-5.3.4.tar.gz>, SHA-256 `f681aa518233bc407e23acf0f5887c884f17436f000d453b2491a9f11a52400c`. Before every generation/check, the checker verifies the archive hash and byte-compares all extracted `.c`, `.h` and `src/Makefile` files against that archive. It does not trust an arbitrary local `lua` version string.

Configured C function census: `-std=gnu99 -DLUA_COMPAT_5_2 -DLUA_USE_POSIX -DLUA_USE_DLOPEN`, the same effective compatibility/host settings used by `tools/prepare_reference.sh` and stock `src/Makefile`. On this run: Linux x86-64, 32-bit int/instructions, 64-bit size_t/lua_Integer, IEEE binary64 lua_Number, default C assertions disabled. `LUA_COMPAT_5_2` enables `LUA_COMPAT_MATHLIB`, `LUA_COMPAT_BITLIB`, `LUA_COMPAT_IPAIRS` and `LUA_COMPAT_APIINTCASTS`; this is **not** the no-compatibility build and does not enable all 5.1 compatibility switches. Other OS/ABI configurations need separate review/censuses.

## Reproduce / gate source drift

From the repository root, Python 3.10+ and a C compiler are required (tested Python 3.13/GCC 14.2.0):

```sh
python3 -m venv /tmp/javalua-audit-venv
/tmp/javalua-audit-venv/bin/pip install -r tools/function_audit/requirements.txt
sh tools/prepare_reference.sh
/tmp/javalua-audit-venv/bin/python -m unittest discover -s tools/function_audit -p 'test_*.py' -v
/tmp/javalua-audit-venv/bin/python tools/function_audit/inventory.py check --compiler-cross-check
```

`check` is read-only and exits nonzero for source/snapshot drift, unknown Java files, orphan mappings, nonexistent C targets, stale reviewed Java/C bodies, changed hashed test evidence or missing evidence symbols. `--compiler-cross-check` independently compares extracted active C function names with GCC/ELF `-O0 -fkeep-static-functions` assembly symbols for **all 35 C translation units**. That option targets GNU-style compilers/ELF; omit it on other platforms and do not claim the independent check ran there. The AST scanner still checks on every run.

After a deliberate source change:

1. Inspect added/deleted/changed declarations and all file hashes. Add file classifications in `mapping.json`; do not silently classify a new file as aligned.
2. Review affected evidence. If reviewed function/C/test hashes changed, **downgrade to `unreviewed`** (remove old evidence/fingerprint claims), or repeat the scoped audit and deliberately update hashes. The tool refuses to silently retain a stale review.
3. Run `inventory.py update`, inspect `git diff -- docs/function-audit`, then run `check` and tests. Updating a snapshot acknowledges drift, not semantic alignment.
4. Commit source and updated ledger together. A CI job can use the `check` command after reference preparation; this change deliberately does not edit the parallel CI work.

Examples to query the JSON without additional dependencies:

```sh
python3 - <<'PY'
import json
x = json.load(open('docs/function-audit/inventory.json'))
for d in x['java']:
    if d['kind'] == 'method_declaration' and not d['c_targets']:
        print(d['id'], d['category'], d['c_files'])
# C functions with no Java symbol reference are a triage queue, not proven omissions.
for d in x['c']:
    if d['kind'] == 'function' and not d['java_references']:
        print(d['id'], d['scope'])
PY
```

## Schema and interpretation

Each Java record has `id`, `kind`, `owner`, `name`/`signature`, source path/line range/hash, category, C file contexts, `c_targets`, `mapping_basis`, review status, evidence and a note. A `c_types` entry (where present) is a checked name/file context for a C representation, not a function target or whole-class behavioral proof. `c_targets` is deliberately many-to-many: Java may split a C function, combine helpers, implement a C macro, or move it to a different Java file.

### Review state (independent of category)

- **unreviewed**: enumeration/file classification/name candidate/manual symbol assignment only. No behavioral conclusion. `unique-name-candidate` is still unreviewed; ambiguity remains visible.
- **structure-mapped**: explicit C correspondence/control-flow site inspected; no equivalence claim. Cite the reviewed scope and gap.
- **behavior-tested**: a specifically identified, executed witness covers the recorded behavior. Not every branch/input of the function.
- **manually-reviewed**: the recorded representative scope has been compared to C with branch/invariant notes, often plus named tests. Still not a universal proof; read `AUDIT.md` and the entry note.

For every non-unreviewed executable, the checker requires explicit Java and C fingerprints, C targets and evidence. Evidence points to a real path and optional test symbol; hashed test files also fail closed when edited. Merely attaching the name of a large suite is insufficient as a review practice; the checker cannot prove the truth of a human-authored claim.

### Categories / exclusions

`port` = intended language/library port; `java-helper` = Java representation or adaptation (can still implement important C macros); `java-interface` = callback/annotation contract; `jni` = ABI dispatch/native glue; `generated` = executable carrying the generated API marker; `generated-metadata` = marker definition; `gc` = explicit GC/finalizer accommodation; `platform` = identified host backend/policy difference requiring separate validation. `@Generated` is also recorded orthogonally per executable: generated API stubs are **not** silently excluded from auditing. Constructors remain explicit helpers unless specifically reclassified.

Platform is **not a blanket permission to differ**. In particular, all of IoLib/OsLib/MathLib are not exempted, integer table behavior is not excused as pointer-order variability, and native/non-native API behavior still needs evidence. GC entries stay in the inventory/total denominator but out of strict non-GC claims.

### Denominators and scanner limits

- Java totals cover **all `src/main/java/**/*.java`**, including private/nested/local/anonymous declarations, explicit constructors, abstract/interface/native methods and annotation elements. Compiler-generated default constructors, enum machinery, lambda implementation methods, bridge methods, initializers and field members are not executable entries. Full-file hashes detect edits to those source constructs. Resources, tests, generated bytecode and native C implementation bodies are not Java methods.
- C **functions** are definitions active in the stated preprocessed configuration, including CLI and GC. Forward declarations/prototypes, static data and headers' typedefs are not function counts. File/name/line/fingerprint refer to the original verified source, not macro-expanded code. GCC varargs builtin expressions are normalized only for parsing declaration boundaries; original bytes are hashed.
- C **macros** are raw function-like definitions from `.c`/`.h`, all conditional branches, separately counted and line-qualified to avoid duplicate-definition ambiguity. A macro candidate may be inactive in this configuration. Object-like constants and C types are not in the function/macro denominator.
- Tree-sitter recovers inside `lstrlib.c::getoption` at an expanded `offsetof` construct; this is exposed as `body_parser_recovery`. Its declarator is clean, and the independent compiler census checks its name. This scanner is not a C semantic analyzer.
- A Java symbol candidate does not prove a C function is ported; lack of a candidate does not prove it is missing (macros/inlining/moved code). Review denominators must not be presented as conformance percentages.
