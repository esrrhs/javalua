# Representative audit record

Baseline and source/configuration are pinned in [README.md](README.md). The initial audit changed no runtime source; integration fixes and witnesses are recorded in [CONTINUATIONS.md](CONTINUATIONS.md). These are scoped findings, not another assertion that all non-GC code is strictly aligned.

## 1. Table boundary search (`ltable.c`)

Read `LuaTable.length` against `luaH_getn` (C lines 641–656): same array-last-is-nil binary-search branch, dummy-hash fast path, otherwise unbounded search. Read `LuaTable.unboundSearch` against `unbound_search` (613–634): initial `j++`, exponential search, `MAX_INT/2` overflow guard, linear fallback, then binary search. Java uses a long for unsigned intermediate arithmetic; the threshold remains C's 32-bit `MAX_INT`, not Long.MAX_VALUE. For ordinary representable array sizes the decision points correspond.

Executed witness: `FunctionAuditEvidenceTest.tableBoundaryBranches`: packed holes, empty tables, contiguous lengths 0..96, deletion and a sparse integer insertion. **Not covered**: physically huge array allocations and execution of the >MAX_INT/2 linear fallback. The witness alone cannot prove resize/hash layout equivalence.

`mainPosition` explicitly uses the 5.3 integer/boolean mask, string hash bucket and floating/object hash-modulo paths. `resize` gathers truncated array values then visits old hash nodes high-to-low before reinsertion, corresponding to `luaH_resize`. These two get only **structure-mapped** status: all collision patterns, floating hashes, weak entries and pointer-key backend choices have not been audited here. Stale comments above `hashMod`/float hashing still describe integers as sharing the modulo path, contrary to the actual integer mask branch; this is a documentation gap, not evidence of a runtime mismatch.

## 2. Compatibility `ipairs`, callback no-yield boundaries (`lbaselib.c`)

Read `BaseLib.pairsmeta`, `pairs`, `ipairs`, `ipairsAux` against `pairsmeta`, `luaB_pairs`, `luaB_ipairs`, `ipairsaux`. Under this exact `LUA_COMPAT_5_2` configuration, `__ipairs` must be consulted. The Java fallback accepts a non-table argument until iteration (C uses `luaL_checkany`), returns iterator/state/zero-or-nil, and the iterator pushes index then value, returning one value for nil and two otherwise. This preserves the C stack-result convention.

Metamethod callbacks call `callNoYield`, corresponding to `lua_call`/`luaD_callnoyield`, not a resumable `lua_callk`. `FunctionAuditEvidenceTest.compatIpairsAndFallback` covers the compatibility hook, scalar fallback triple and indexed metamethod termination. `nonYieldableLibraryCallbacks` covers attempted yield through `__pairs`, `__ipairs`, global tostring used by print, load reader and require loader. The latter asserts failure, error class/text fragment and dead coroutine state; it does not compare every error prefix/stack frame.

`print` and the reader/require call sites receive only **behavior-tested** credit for those callbacks, not a review of their full conversion/IO/searcher semantics.

## 3. Yield/protected-call execution (`ldo.c`)

Read `LuaState.callNoYield` against `luaD_callnoyield` (507–511): increment non-yield counter, call, decrement; Java uses finally to unwind exceptions, while C nonlocal exits rely on the surrounding protected boundary restoring state. `yieldk` rejects the main thread vs non-yieldable coroutine with the corresponding two messages, then records yield status/results.

`FunctionAuditEvidenceTest.protectedCallContinuationResults` covers yield through pcall, resume arguments/results, and an xpcall message handler. This is narrow **behavior-tested** evidence for pcall/yield, not a complete continuation API review.

Initial finding (subsequently investigated and fixed in [CONTINUATIONS.md](CONTINUATIONS.md)): `LuaState.finishCcall` / `finishPcallK` still contain 5.4-shaped `clsret`/close/unwind scaffolding and a `k == null` fallback, unlike C 5.3.4 `finishCcall` (518–537), whose continuation invariant is asserted. The fallback may be unreachable for valid 5.3 callers, but that reachability has **not** been proved here. Status is **structure-mapped**, not aligned. Native `lua_callk`/`lua_pcallk` across repeated yield, error-after-resume, hooks and nested recovery require a dedicated reachability/stack-state audit.

## 4. Binary constants and debug sections (`lundump.c`, `ldump.c`)

Read `Undump.loadConstants` against `LoadConstants` (118–149): nil, boolean tag + separate payload byte, number/integer, short/long string tags correspond. Java incrementally allocates rather than C's eager allocation. This allocation policy is intentional JVM hardening and is **not byte-for-byte algorithm identity**; resource-exhaustion timing for malformed input is not certified.

`FunctionAuditEvidenceTest.booleanConstantPayloadsLoadOnBothRuntimes` explicitly constructs a valid prototype whose LOADK reads a boolean constant (ordinary Lua source often uses LOADBOOL and would miss this path), dumps false and true payloads, then loads/executes each in both Java and stock 5.3.4. This tests dump/undump agreement against an independent runtime, not just Java self-loading.

Read `loadDebug` against `LoadDebug` (180–199): dumped line-info count, local variable names/start/end and raw dumped upvalue-name count are consumed in the same sequence. Java bounds-checks the upvalue-name store after reading a string; C would write beyond the allocation for a corrupt excessive count. That is **C undefined behavior**, not a defined compatibility guarantee. `debugNamesAndStrippedRoundTrip` covers ordinary stripped/unstripped nested closure names/values with explicit upvalue restoration; it does **not** cover every corrupt count.

Read `loadFunction` against `LoadFunction` (202–216): source inheritance, line definitions, parameter/vararg/stack metadata, code/constants/upvalues/prototypes/debug order correspond. `checkHeader` follows C `checkHeader` (236–252): signature remainder, 0x53, format, LUAC_DATA, sizes, integer and float sentinels. The chosen target is the recorded little-endian/64-bit ABI; cross-endian or 32-bit size_t chunks are not claimed supported.

Existing `BinaryChunkTest` and `DumpTest` are run as additional evidence, but **their aggregate success is not assigned to all Undump/Dump methods**. `CodegenTest.sharedScriptsCompileOnBothSides` explicitly checks compilation only, despite older class comments describing stronger bytecode identity. Do not cite that method as exact bytecode proof.

## 5. Empty return / vararg initializers (`lparser.c`, `lcode.c`)

Read `Parser.retStat` against C `retstat`: empty return initializes first/nret to zero; multret emits all results at nactvar; tail-call conversion and fixed-result register choice follow the corresponding branches. `Codegen.ret` emits RETURN A=first, B=nret+1, C=0 as `luaK_ret`. `Parser.simpleExp` vararg branch emits ABC(VARARG,0,1,0) before later result-count patching, matching C `simpleexp` for that site. The rest of `simpleExp` is **not** covered by this targeted review.

`FunctionAuditEvidenceTest.returnVarargBytecodeWitness` asserts empty RETURN fields directly and compares vararg execution/results. `DumpTest.dumpBytesGridMatchReference` is the additional actual byte-level oracle evidence for the compiler grid. That finite grid does not exhaust goto/upvalue closing, register-pressure or constant-table edge cases.

## 6. Load modes / reader boundary (`lbaselib.c`, `ldo.c`)

`BaseLib.loadZio` centralizes the text/binary mode decision and protected load. The named `loadModeAndReaderErrorBoundary` witness covers binary rejected by t-only, text rejected by b-only, accepted binary and a reader error. This gets **behavior-tested**, not full-function manual review; arbitrary reader chunk boundaries, allocation failure/error-handler nesting and all malformed headers remain work.

## 7. C auxiliary callmeta (`lauxlib.c`)

Read `Api.luaL_callmeta` against C `luaL_callmeta` (788ff): missing metafield returns zero; present metafield calls it with the object and keeps one result, using a non-yieldable call. The Java object lookup after popping the metafield sees the original stack height, preserving negative relative indices. Marked **structure-mapped** only: this audit did not add/execute a dedicated native callmeta/negative-index witness. Temporary native string allocation/lifetime and shim dispatch also deserve a native resource audit.

## 8. Known categories are not conformance waivers

Random/randomseed and pure-Java process/locale/clock adapters are explicitly platform-classified; their sequence, libc RNG algorithm, errno/locale/signal/status behavior still need backend-specific acceptance criteria. GC collector/finalizer helpers are explicitly categorized and retained in totals. Many Java-only registration helpers and 5.4-shaped compatibility/dead-code helpers remain unreviewed; type/file mapping alone says nothing about reachability.

## Validation record

Commands used (Java 21; exact prepared C oracle; Linux x86-64):

```sh
python -m unittest discover -s tools/function_audit -p 'test_*.py' -v
python tools/function_audit/inventory.py check --compiler-cross-check
# Build without overwriting the source resource binary (JAVA_HOME points to JDK 21):
cc -shared -fPIC -O2 -Wall -Inative/lua -I"$JAVA_HOME/include" \
  -I"$JAVA_HOME/include/linux" -o target/audit-libluashim.so native/luashim.c
./mvnw -q -Dtest=FunctionAuditEvidenceTest,BinaryChunkTest,DumpTest,CodegenTest \
  -Djacoco.skip=true -Djavalua.luashim="$PWD/target/audit-libluashim.so" \
  -Djavalua.audit.requireNative=true test
./mvnw -q -Dtest=FunctionAuditEvidenceTest,BinaryChunkTest,DumpTest,CodegenTest \
  -Djacoco.skip=true -Djavalua.nonative=true test
```

The checker contract suite has 20 tests, including nested/anonymous/interface/native/overloaded declarations, generated markers, parse rejection, new-file classification, method deletion, stale review/C-target/evidence checks and field-only source drift. Compiler cross-check: 35/35 C translation units, no name mismatches. JUnit results are recorded in `VALIDATION.md` after execution. No full-suite or CI run is claimed by this audit.

## Remaining work (in priority order)

1. Extend the finite finishCcall/finishPcallK investigation in CONTINUATIONS.md to additional hook/native ABI/stack-pressure combinations; similarly triage remaining 5.4-shaped helpers such as parser close/readonly scaffolding and opcode compatibility helpers.
2. Triage every `file-context-only` and ambiguous-name entry, replacing candidates with deliberate many-to-many mappings or an explained helper/JNI/generated/GC/platform classification. Progress is in the generated summary; **most methods remain unreviewed**.
3. Audit active C reverse gaps by function, excluding CLI/GC only explicitly. A Java method containing an inlined C function needs a mapping even without a similar name.
4. Extend function witnesses to VM numeric conversions/comparisons, metamethod fast/slow paths, pattern backtracking/captures, pack alignment/overflow, IO error paths, package loader/native ABI, debug hook state and parser register-pressure/goto/upvalue-close combinations.
5. Review C callback contracts (lua.h), Java representation classes vs C structs/macros, allocation/status failures and every generated/shim dispatch path. Native C bodies are not fully inventoried by this Java ledger.
6. Checker execution is now an additive CI gate; extend per-function evidence on each backend. A mapping can go stale semantically through callees even when its own source hash has not changed.

**Initial-stream outcome:** no new mismatch was reproduced by its eight initial witnesses. Integration then reproduced and fixed six defined non-GC continuation discrepancies (CONTINUATIONS.md). Significant unreviewed coverage and structural gaps remain; this work does not justify “all differences eliminated”.
