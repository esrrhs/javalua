# fakescript-java

[![License](https://img.shields.io/github/license/esrrhs/fakescript_java)](https://github.com/esrrhs/fakescript_java)
[![Language](https://img.shields.io/github/languages/top/esrrhs/fakescript_java)](https://github.com/esrrhs/fakescript_java)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.esrrhs/fakescript-java)](https://central.sonatype.com/artifact/com.github.esrrhs/fakescript-java)
[![Build Status](https://github.com/esrrhs/fakescript_java/actions/workflows/maven.yml/badge.svg?branch=master)](https://github.com/esrrhs/fakescript_java/actions)

**Lua 5.4 in pure Java — no GC of its own; the JVM does the collecting.**

[简体中文](./README_CN.md)

---

## Design Goal

> Run existing Lua projects on the JVM **without changing a single line of their Lua code.**

This project is a **module-by-module port of the Lua 5.4 reference implementation**
(`lua-5.4.x` C sources) to Java. It is not a Lua-inspired language that merely looks
similar — it is Lua 5.4, with the memory-management layer removed and handed to the
host runtime.

| | |
|---|---|
| **Target** | Full Lua 5.4 language semantics + complete standard library |
| **Method** | Direct, module-by-module translation of the C reference sources |
| **GC** | **Not implemented.** Object lifetime is entirely the JVM's job (G1/ZGC are concurrent collectors already) |
| **Runtime** | Java 17+ (coroutines are plain stacks, no threads — see below) |
| **Compatibility** | Pure-Lua projects; C extension modules (`.so` / `luaopen_*`) are out of scope for now |

### Why there is no GC

`lgc.c` is one of the most intricate parts of the C implementation: tri-colour
incremental marking, generational mode, write barriers, finalizers, weak tables.
All of it exists to do what the JVM already does — concurrently, and better tuned
than a hand-rolled collector written in Java would be. The file is deleted
outright. Only the *semantics that depend on collection happening* are rebuilt on
JVM primitives:

| Lua concept | C mechanism | This implementation |
|---|---|---|
| Object lifetime | `lgc.c` tri-colour marking | **JVM GC** (G1 / ZGC) |
| Weak tables (`__mode = "k"/"v"/"kv"`) | weak links swept in `lgc.c` | `WeakReference` + `ReferenceQueue` |
| `__gc` finalizers | `luaC_checkfinalizer` / `GCTM` | `java.lang.ref.Cleaner` |
| Short-string interning | `lstring.c` table pinned against collection | weak intern pool; entries die when unreferenced |

Everything else — lexer, parser, code generator, VM, metamethods, coroutines,
standard library — is a direct port.

---

## Architecture

The package layout mirrors the C sources one-to-one, so a reader holding
`lua-5.4.x` side by side can find their way around:

| Lua 5.4 C source | Java |
|---|---|
| `lobject.h` / `lobject.c` | `LuaValue`, `LuaString`, `LuaTable`, `Proto`, `Closure`, `Upvalue` |
| `lstring.c` | `StringPool` |
| `ltable.c` | `LuaTable` |
| `lfunc.c` | `Proto`, `Closure`, `Upvalue` |
| `lstate.c` | `LuaState`, `CallInfo`, `GlobalState` |
| `lzio.c` | `Zio` |
| `llex.c` | `Lexer` |
| `lparser.c` | `Parser` (hand-written recursive descent, as upstream) |
| `lcode.c` / `lopcodes.h` | `FuncState`, `Codegen`, `OpCode` |
| `lvm.c` | `Interpreter` |
| `ldo.c` | call frames, protected calls, error propagation |
| `ltm.c` | `Meta` (metamethod dispatch) |
| `ldebug.c` | `Debug`, error messages, stack traces |
| `lapi.c` / `lauxlib.c` | operations on `LuaState`, `AuxLib` |
| `lundump.c` / `ldump.c` | bytecode load / dump |
| `lbaselib.c`, `lcorolib.c`, `loadlib.c`, `lstrlib.c`, `ltablib.c`, `lmathlib.c`, `liolib.c`, `loslib.c`, `lutf8lib.c`, `ldblib.c` | corresponding `lib.*` classes |
| `lgc.c` | **absent** — see above |

The auxiliary layer (`lauxlib.c` and friends) comes along, because the standard
library is written against it.

---

## Key Design Decisions

These are the places where translating C to Java is not mechanical, and where a
wrong call costs correctness that is expensive to recover later.

### 1. Lua strings are byte strings, so Java `String` cannot be used

A Lua string may contain `\0`; `#s` counts **bytes**; `s:byte(i)` returns a byte.
Java `String` is UTF-16 and rejects lone surrogates. Any real-world Lua project
that touches binary data, UTF-8 text, or embedded `\0` will break if strings are
backed by `String`. Lua strings are therefore backed by `byte[]`, and every string
library function is implemented byte-wise.

### 2. Values keep the integer/float subtype split

Lua 5.4 distinguishes `1` (integer) from `1.0` (float): they compare equal, but
`math.type` differs, `//` differs from `/`, `1/2` is `0.5` while `1//2` is `0`,
and coercion to string yields `1` vs `1.0`. Values are a **mutable slot** type
(a `type` tag plus `long`, `double` and an `Object` payload) mirroring C's `TValue`
— this avoids boxing and lets the VM write stack slots in place.

### 3. Tables keep the array part / hash part split

Ported from `ltable.c`, including the migration logic that decides whether an
integer key is dense enough to live in the array part. Integer keys and string keys
are distinct entries: `t[1]` and `t["1"]` are different, as in Lua.

### 4. Coroutines are separate stacks, not threads

Lua coroutines can yield from arbitrarily deep stacks — inside `pcall`, inside a
metamethod, inside a `for` iterator. The plan was to give each one a Java virtual
thread. That turned out to be the wrong call, and phase 5 dropped it.

A Lua coroutine is *cooperative*: exactly one runs at a time, and the suspension
point is stated explicitly by `coroutine.yield`. C models this with one stack per
`lua_State` plus `longjmp` out to `lua_resume`. Java has the same two ingredients
— one stack per `LuaState`, and an exception (`LuaYield`) that unwinds the Java
call stack. **Nothing needs a thread**, because all the Lua state lives on the
heap: unwinding the Java frames leaves the stack, the `CallInfo` chain and the
open upvalues exactly as they were, ready to continue on `resume`.

Virtual threads were also *too permissive*. A C function that calls back into Lua
(`table.sort`'s comparator, `string.gsub`'s replacement function) cannot yield in
real Lua — it raises `attempt to yield across a C-call boundary`. On a virtual
thread it would simply work, and that difference is a bug, not a feature, for a
project whose goal is "run existing Lua code unchanged."

This also drops the Java floor from 21 to 17, which is what makes the local build
work today.

The machinery that replaced threads is a faithful port of `ldo.c`:
`lua_resume` / `unroll` / `finishCcall` / `finishpcallk` / `precover`, plus
`luaV_finishOp`, which completes the half-executed instruction that a yield
interrupted. C functions that can be interrupted (`pcall`, `xpcall`) register a
continuation — the Java equivalent of `lua_KFunction`.

### 5. Errors use Java exceptions, not `setjmp` / `longjmp`

`lua_error` becomes a thrown error carrying the Lua error value. `pcall` / `xpcall`
catch it, as does the coroutine boundary. Care is needed so that non-string error
values, error objects carrying `__tostring`, and errors crossing a coroutine
boundary all behave as upstream does.

### 6. No JFlex / Bison

The Lua reference parser is hand-written recursive descent. Porting it directly
removes the build-time code-generation step, along with the generated-parser drift
problem the previous architecture had.

### 7. Metamethods: all of them, including the awkward rules

`__index`, `__newindex`, `__call`, `__concat`, `__len`, `__eq`, `__lt`, `__le`,
`__unm`, `__add` … `__idiv`, `__close`, `__gc`, `__mode`, `__name`, `__metatable`,
`__pairs`, `__ipairs`. Including the parts that are easy to get wrong: `__le`
degrading to `not (b < a)` when only `__lt` exists; `__eq` firing only for
comparable types; `__len` on tables ignoring non-integer results; and `__index`
chain traversal.

---

## Roadmap

Each phase ends with tests green. Nothing advances on a red phase.

| Phase | Content | Upstream reference |
|---|---|---|
| **0** | Foundations: `LuaValue`, `LuaString` + interning, `LuaTable`, `LuaState`, error type, `Zio` | `lobject`, `lstring`, `ltable`, `lstate`, `lzio` |
| **1** | Lexer + recursive-descent parser; emits directly as upstream does, with no separate tree | `llex`, `lparser` |
| **2** | Bytecode: full Lua 5.4 instruction set, code generator, constant folding, upvalue resolution | `lopcodes`, `lcode`, `lfunc` |
| **3** | Interpreter main loop: calls/returns, arithmetic & comparison with coercion, metamethod dispatch | `lvm`, `ldo`, `ltm` |
| **4** | Closures & open/closed upvalues, `goto`/labels, varargs, multiple assignment and returns | `lparser`, `lvm` |
| **5** | Coroutines: `coroutine.*`, yield/resume/unroll, yield & error across boundaries | `lcorolib`, `ldo`, `lvm` |
| **6** | Collection-dependent semantics: weak tables, `__gc`, weak string pool | *(replaces `lgc.c`)* |
| **7** | Standard library: `base`, `coroutine`, `package`, `string`, `table`, `math`, `io`, `os`, `utf8`, `debug` | `l*lib.c`, `loadlib.c` |
| **8** | `load` / `loadstring` / `dump`, `require` searchers, `package.path` / `cpath` | `lundump`, `ldump`, `loadlib` |
| **9** | Conformance: upstream test suite + differential testing against a real `lua5.4` binary | — |

---

## How "it is actually Lua" gets verified

A rewrite that merely *looks* like Lua is worthless. Four gates, in increasing
order of strength:

1. **Upstream test suite.** The `lua-5.4.x/tests/*.lua` files (`alls.lua`,
   `big.lua`, `strings.lua`, `coroutine.lua`, `pm.lua`, …) run unmodified. This is
   the primary gate — upstream's own suite is far more adversarial than anything
   written by hand here.
2. **Differential testing.** The same script runs under this implementation and
   under a real `lua5.4` binary; stdout must match byte for byte. Wired into CI
   with `lua5.4` installed.
3. **Differential fuzzing.** Generated programs compared against the reference
   binary — this is what catches the "no crash but wrong answer" class.
4. **Coverage gate** (JaCoCo line ratio).

A phase is done only when the gates that apply to it are green.

---

## Status

**Phase 10 — the upstream `lua-5.4.x/tests` suite runs green end to end.** 19
files whose output is deterministic (`api`, `bitwise`, `calls`, `closure`,
`code`, `coroutine`, `db`, `errors`, `events`, `gengc`, `goto`, `nextvar`, `pm`,
`strings`, `tpack`, `utf8`, `vararg`, `verybig`, …) match a real `lua5.4`
**byte for byte**. Five more (`cstack`, `gc`, `literals`, `locals`,
`constructs`) print things that vary with the implementation or with a random
seed, so they are only asserted to run to completion and print `OK` — the reason
for each is listed under *Phase 10* below. 826 tests green, JaCoCo line coverage
0.90 against a 0.89 gate. (The upstream-suite gate needs `LUA54_TESTS` or
`target/lua54-tests`; without a test directory that whole class **skips itself**,
so a green run on a machine without it is 25 tests weaker than it looks.)

**Phase 9 — complete, including `debug.sethook`.** The previous fakescript
language implementation has been removed from `master`; see *Migration* below.
Phases 0 through 9 are in: the compiler produces bytecode that is
**field-for-field identical** to a real `lua5.4`'s, and the VM executes it —
**stdout matches a real `lua5.4` byte for byte** across 158 + 121 + 60 + 32 + 25
+ 16 + 21 + 36 differential scripts and 2100 generated programs, and error
messages match line for line. `string.dump`'s output is additionally compared as
**raw bytes**, not just as behaviour. All ten standard libraries are in (base,
coroutine, package, string, utf8, table, math, os, io, debug), `debug` included
in full — hooks and all.

Phase 6 is the one phase where "no GC of our own" costs real ground. Weak tables,
`__gc` and the string pool are all rebuilt on JVM reachability, which is *not* the
same thing as Lua's mark-and-sweep; three places where it cannot be made to match
are listed below under *Known divergences*.

Landed so far:

| Piece | Upstream | Notes |
|---|---|---|
| `Lua` type tags | `lua.h`, `lobject.h` | integer/float subtypes kept as distinct variants |
| `LuaValue` | `TValue` | mutable slot, so the VM can write stack slots in place |
| `LuaString` | `TString` | `byte[]`-backed; `\0`, arbitrary and invalid-UTF-8 bytes all preserved |
| `StringPool` | `lstring.c` `strt` | interned weakly, so unused strings do not pin the heap |
| `LuaTable` | `ltable.c` | array part + hash part, `rehash`, `#t` borders, integral-float key normalisation |
| `LuaError` | `lua_error` | carries a Lua value; only this type is caught by `pcall` |
| `Zio` | `lzio.c` | byte input stream; `EOZ` is `-1`, bytes are unsigned |
| `LCType` | `lctype.c` | the `luai_ctype_` table copied byte for byte, so `lislalpha` treats `_` as a letter exactly as upstream does |
| `Numeral` | `lobject.c` `luaO_str2num` | int-first-then-float; strict decimal syntax, so `1d`/`inf`/`nan` are rejected the way C's `strtod` rejects them |
| `Lexer` | `llex.c` | full token set, all escapes incl. `\z` / `\xXX` / `\u{XXX}` / `\ddd`, long strings & comments, byte-exact error messages |
| `OpCode` | `lopcodes.h/.c` | all 83 opcodes; order, names and `luaP_opmodes` verified mechanically against upstream headers |
| `Proto` | `lobject.h`, `lfunc.c` | code / constants / nested protos / upvalues / locals / line info |
| `Arith` | `lobject.c`, `lvm.c` | the arithmetic used by constant folding — same code path as the VM will use |
| `ExpDesc`, `FuncState`, `DynData` | `lparser.h` | `expdesc`'s union flattened; block/goto/variable bookkeeping |
| `Codegen` | `lcode.c` | instruction emission, register allocation, jumps & patching, constant folding, `NEWTABLE`/`SETLIST` |
| `Parser` | `lparser.c` | hand-written recursive descent, emitting code as it parses |
| `UpVal` | `UpVal` | two-state (open = stack slot reference, closed = private copy) |
| `LClosure` | `LClosure` | proto + upvalue array |
| `CallInfo` | `CallInfo` | frame record; `CIST_FRESH` decides where a Lua→Lua return stops |
| `LuaState` | `ldo.c`, `lstate.c` | stack, call/return, `pcall`, upvalue open/close, all `luaG_*` errors |
| `LuaTm` | `ltm.c` | metamethod lookup & dispatch, `__le` fallback, `to-be-closed` |
| `Vm` | `lvm.c` | the interpreter main loop; all 83 opcodes |
| `BaseLib`, `MathLib` | `lbaselib.c`, `lmathlib.c`, part of `lstrlib.c` | minimal but differentially-verified |
| `Diff` (test) | — | shared differential driver: runs one script on both sides |
| `ConformanceTest`, `CodegenTest`, `ParserTest`, `RuntimeTest` | — | run a real `lua5.4` binary as the oracle |
| `ClosureTest` (test) | — | Phase 4 gate: closures, `goto`, varargs, multiple assignment |
| `CClosure` | `CClosure` | a C function bound to values — needed by `coroutine.wrap` |
| `LuaThread` value (`Lua.VTHREAD`) | `LUA_TTHREAD` | a `LuaState` reachable as a Lua value |
| `CoroutineLib` | `lcorolib.c` | `create` / `resume` / `yield` / `status` / `wrap` / `isyieldable` / `running` / `close` |
| `resume` / `unroll` / `finishCcall` / `finishpcallk` / `precover` | `ldo.c` | the yield/resume state machine, no threads |
| `LuaKFunction` | `lua_KFunction` | continuation for a C function interrupted by a yield |
| `LuaYield` | `luaD_throw(L, LUA_YIELD)` | unwinding exception; deliberately *not* caught by `pcall` |
| `Vm.finishOp` | `luaV_finishOp` | completes the half-executed instruction a yield interrupted |
| `CoroutineTest` (test) | — | Phase 5 gate: 60 differential scripts, incl. yield inside every metamethod |
| `FuzzTest` (test) | — | 1500 seeded programs + 600 more run inside a coroutine with random yields |
| `LuaTable.WeakRef` | `iscleared` in `lgc.c` | a weak payload *is* the stored form; keeps the identity hash, since `identityHashCode(null)` is 0 and would collapse every dead key onto bucket 0 |
| `LuaTable.purge` / `rewrap` | `cleartable` / `traverseweakvalue` | dead entries are dropped at the end of a collection round, and re-wrapped when `__mode` changes |
| `LuaState.clearDeadStackSlices` | `traversethread`'s atomic branch | nils `[top, stack_last)` on every thread, inside `collectgarbage` |
| `GcSentinel` | `GCTM` in `lgc.c` | sentinel whose `finalize` resurrects the table onto a pending queue; drained on the Lua thread |
| `LuaState.close` | `lua_close` → `callallpendingfinalizers` | runs `__gc` for every object that still has one, at exit |
| `WeakTableTest` (test) | — | Phase 6 gate: 20 differential scripts, split into a collection-dependent half and an always-true half |
| `GcTest` (test) | — | Phase 6 gate: 12 scripts — once-only, errors swallowed, late `__gc` ignored |
| `LuaUserdata` | `Udata` | payload + **per-object** metatable; `luaL_testudata` needs the latter |
| `Undump` | `lundump.c` | reads a precompiled chunk; header check, varint sizes, the `binary string` chunk-name rule |
| `Dump` | `ldump.c` | writes one; `string.dump`, `strip` |
| `LoadTest` (test) | — | Phase 8 gate: 25 scripts — modes, chunk names, `_ENV`, reader functions, `loadfile` / `dofile` |
| `DumpTest` (test) | — | Phase 8 gate: 16 — runtime stdout **and** `string.dump` output byte for byte |
| `StringPack` | part of `lstrlib.c` | `string.pack` / `unpack` / `packsize`; the only platform-dependent corner of the library |
| `PackTest` (test) | — | 21 scripts — integers, floats, strings, alignment, wide integers, errors, key set |
| `DebugLib` | `ldblib.c` + part of `ldebug.c` / `ldo.c` / `lauxlib.c` | `getinfo` / locals / upvalues / `traceback` / registry / metatables / user values / `setcstacklimit` / `sethook` / `gethook` |
| `LuaHook` | `lua.h` `lua_Hook` | the hook callback; `DebugLib`'s `hookf` is the one `sethook` installs |
| `LuaLibs` | `linit.c` | `luaL_openlibs`; the ten libraries in `loadedlibs` order |
| `DebugTest` (test) | — | Phase 9 gate: 36 scripts — levels, options, locals, upvalues, `traceback`, deep stacks, and 14 hook scripts |
| `LuaLibsTest` (test) | — | `openAll` really opens all ten, and they are the same tables the globals see |

### The bytecode gate

The compiler is validated by **differential comparison, not by self-declared
expectations**. `src/test/resources/fkdump.c` is a small dumper compiled against
the reference `lua5.4` sources; for every script it prints every instruction with
every field (`a`, `b`, `c`, `k`, `Bx`, `sBx`, `Ax`, `sB`, `sC`, `sJ`), plus the
line, the constant table and the upvalue table, recursively for nested functions.
`CodegenTest` compares that against `ProtoDump`'s rendering of our `Proto`,
requiring the two to be identical.

Two corpora: ~50 hand-written scripts covering every construct, and the 33 files
of the **upstream `lua-5.4.7` test suite** (`target/lua54-tests`, fetched in CI),
compiled but not run. All of them match.

Error messages are gated the same way: `ParserTest` writes broken sources to disk,
runs them through a real `lua5.4`, and compares the message byte for byte —
`'end' expected (to close 'if' at line 1)`, `label 'a' already defined on line 1`,
`<goto l> at line 1 jumps into the scope of local 'x'`, and so on. The lexer half
was already gated like this: a table of deliberately-broken sources is compared
verbatim, including the `near '<token>'` part whose quoting rules differ between
`<eof>` and everything else.

Two decisions in this phase were settled by running the reference implementation
rather than by reading the manual:

* an integral float used as a key **is normalised to an integer** on write
  (`t[1.0]` yields `math.type(next(t)) == "integer"` and `#t == 1`);
* writing a **NaN** key raises `table index is NaN`, while *reading* one silently
  returns `nil`.

Both are locked by tests that run against real Lua 5.4 in `ConformanceTest`.

### Three things the reference binary settled this phase

Each of these looks like a bug and is not; all three are locked by tests.

* **`>>` is a logical shift.** `intop(>>,x,-y)` casts to `lua_Unsigned` first, so
  `-8 >> 2` is `4611686018427387902`, not `-2`.
* **Upstream's constant table contains duplicates.** `ls->h` — the
  "constant → index" cache — is shared by the whole chunk, while each function
  numbers its own constants from 0. A nested function overwrites cached indices
  with its own small ones; back in the outer function the index no longer points
  at the right constant, so the entry is rejected and a duplicate is appended.
* **Integral floats get a perturbed cache key.** Lua compares table keys with
  `==`, so `100000` and `100000.0` are the *same* key; without
  `luaK_numberK`'s `r + r*2^-52` trick they would evict each other from the cache
  and a constant would be re-created on every use.

### The runtime gate

`RuntimeTest` is the Phase 3 gate. Every case is a script that runs **twice** —
once under this implementation, once under a real `lua5.4` — and the two stdout
streams must be equal. Error cases are gated on the message line, including the
`(local 'x')` / `(constant '10')` / `(method 'nope')` suffixes.

The stack is modelled with one invariant that everything else depends on:

> A stack slot `LuaValue` is **never replaced**. `LuaState` always does
> `stack[i].set(v)`, never `stack[i] = v`.

That single rule is what makes "an open upvalue holds a reference to the stack
slot" mean the same thing in Java as "an open upvalue holds a pointer to the
stack slot" means in C. Break it once and closures silently stop seeing updates.

Running it today:

```java
LuaState L = new LuaState();
BaseLib.open(L);
MathLib.open(L);
L.run("print('hello from Lua')");
```

### Six things the reference binary settled this phase

Each of these was guessed wrong from the manual or from intuition first; all six
are locked by differential tests.

* **Arithmetic does not coerce strings, and `"10" + 1 == 11` does not come from
  the VM.** `lvm.c`'s arithmetic macros use `tonumberns`/`tointegerns`, which
  ignore strings. The coercion lives in `lstrlib.c`, which installs `__add` (and
  friends) on the string metatable — first `lua_stringtonumber` both sides, then
  `lua_arith`. Skip those metamethods and you get a plausible but wrong
  `attempt to perform arithmetic on a string value`.
* **A C function's `n` return values are the top `n` pushed values.** `ipairsaux`
  pushes `i`, then pushes `t[i]`, and returns `1` when `t[i]` is nil — so the
  value seen by the generic `for` is that trailing **nil**, not `i`. Written the
  intuitive way (`return i`), `for _, v in ipairs(t)` never terminates, because
  the loop only stops when the *first* result is nil.
* **Every arithmetic opcode is followed by an `OP_MMBIN*`.** On success the VM
  does `pc++` to skip it; on failure it falls through and the metamethod gets
  dispatched. Miss one `pc++` and a working expression runs its metamethod too.
* **`OP_NEWTABLE`'s `EXTRAARG` is unconditional** (because `k == 0` ⟺ `Ax == 0`),
  while `OP_SETLIST`'s is conditional. Reading it conditionally produces
  `malformed opcode EXTRAARG` on every table constructor.
* **C's `%g` strips trailing zeros; Java's does not.** `1.5` printed as
  `1.5000000000000` until `formatG` was written by hand.
* **`luaG_runerror` and `luaL_error` take their position differently.** The
  former uses `isLua(ci)` on the *current* frame; the latter is
  `luaL_where(L, 1)` and walks back one level to the caller. A library function
  that uses the former reports no `src:line:` prefix at all, because its own
  frame is a C frame.

### The fuzzing gate

`FuzzTest` generates 2100 programs from a small grammar and diffs each one
against the reference — 1500 plain, and 600 more whose main chunk runs inside a
`coroutine.create` with `coroutine.yield` sprinkled at random points and driven
by a bounded `resume` loop. Two constraints keep it from becoming a nuisance
generator:

* **It must terminate.** Only bounded `for` / `ipairs` loops, no `while`, no
  `repeat`, no `goto`; a call may only target a function that finished being
  defined *earlier*, so the call graph is a DAG and recursion is impossible.
* **It must be reproducible.** Seeds increment from 0 — no `Math.random`. A
  failure is reproducible from "seed 84".

The generator knows nothing about how we *think* Lua behaves, which is the point:
it is the only gate that finds "does not crash, but the value is wrong".

### Four things the reference binary settled this phase

* **`luaF_newtbcupval` checks `__close` at assignment time, not at scope exit.**
  `OP_TBC` runs right after the local receives its value, so
  `local x <close> = 5 print(x)` raises
  `variable 'x' got a non-closable value` *before* printing anything. `nil` and
  `false` are exempt (`l_isfalse`), so `local x <close> = nil` is legal.
* **An error inside `__close` during unwinding *replaces* the original error.**
  `error('orig')` followed by a `__close` that does `error('in close')` makes
  `pcall` return `in close`. Keeping the original instead is wrong.
* **The code generator folds `OPR_MINUS` and `OPR_BNOT` only — never `OPR_LEN`.**
  So `#7.5` is a *runtime* error, not a load-time one. Folding it would make a
  script fail to compile that upstream compiles and only fails when run.
* **Two harness rules, learned the hard way.** When a script errors, the stdout
  it already produced must still be compared (the reference's stdout and stderr
  are captured together, so "print ten lines, then crash" has both in its
  output); and the `lua.c` program-name prefix must be stripped *everywhere*,
  not just at the start, because it appears mid-output once stdout precedes it.

### Five things the reference binary settled this phase

* **`pcall`, `xpcall`, metamethods and `for` iterators are yieldable; C-library
  callbacks are not.** `table.sort`'s comparator and `string.gsub`'s replacement
  function raise `attempt to yield across a C-call boundary`, because
  `ltablib.c`/`lstrlib.c` call back through `lua_call` (`luaD_callnoyield`).
  `__tostring` invoked by `print` is the same story. This is precisely the
  behaviour virtual threads would have "fixed" — and broken.
* **The main thread is never yieldable.** `lstate.c`'s `lua_newstate` does
  `incnny(L)` unconditionally, so `coroutine.isyieldable()` is `false` on the main
  thread even with no C call in sight. A coroutine starts at zero, and every
  `resume` resets it to zero (`lua_resume` copies only the low 16 bits of
  `nCcalls` from the resumer).
* **`resume`'s arguments *are* the return values of `yield`.** `lua_resume`'s
  `poscall(ci, nargs)` is what makes `coroutine.yield(1)` see `99` after
  `resume(co, 99)` — the number of values moved back is the number passed in,
  not the number yielded out.
* **`coroutine.wrap` reports its own name, not `create`'s.** `luaB_cowrap` calls
  `luaB_cocreate` directly, and `luaL_argerror` derives the name from how the
  *current* frame was called — so `coroutine.wrap(nil)` says
  `bad argument #1 to 'coroutine.wrap'`.
* **`luaV_finishOp` has a branch per interruptible opcode, and `OP_CONCAT`'s is
  not optional.** Concatenation proceeds in batches, so after a `__concat` yield
  the metamethod result has to be moved back one slot, `top` pulled in by one,
  and the remaining elements concatenated — possibly yielding again. Skipping it
  silently yields the *operand table* instead of the concatenation result.

### Five things the reference binary settled in Phase 6

* **The GC-visible stack ends at `L->top`, and that is load-bearing.**
  `lgc.c: traversethread` marks only `[stack, top)` and then, in the atomic
  phase, nils out `[top, stack_last)` — the source comment calls it "clear dead
  stack slice". So a value sitting in a register *above* `top` is invisible to
  the collector, and gets collected. Two shapes depend on this: a value pushed
  by a C function such as `next` (the call returns, `top` drops back, the slot
  still holds the key), and the table built by the last iteration of a loop
  (`for i = 1, 5 do t[i] = {i} end` leaves `{5}` in a register while `L->top`
  only reaches the call's arguments). Our `stack` is a Java array, so **every
  slot is a live reference** — `LuaState.clearDeadStackSlices` reproduces that
  clearance, and has to run *inside* `collectgarbage`, not on every `top`
  change (see the next bullet).
* **…but clearing eagerly on every `top` change breaks two things.** C only
  clears during the atomic phase. Doing it inline silently destroyed both
  `luaT_callTMres`'s result (the destination register can *be* the slot being
  popped) and the error object of `pcall` (`LuaError.value` aliases a live stack
  slot, because `error('boom', 0)` throws `L.arg(0)` itself — so `pcall` started
  returning `false nil`). Waiting until `collectgarbage` is both faithful and
  exactly as precise.
* **A weak table's storage has to *be* weak, not merely carry a `WeakReference`
  beside it.** The payload in the slot is the `WeakReference`; a strong payload
  means nothing is ever released. That has a consequence: `LuaTable.get` must
  hand back a *copy* for weak tables, so the write fast path
  (`OP_SETTABLE`'s "slot found, write into it") cannot be used on them —
  writing into a copy is a no-op, and `t.f = g` would leave `t.f` equal to `f`.
* **`__mode` is read dynamically, and re-wraps everything that is already in the
  table.** Building a table, filling it, and *then* calling
  `setmetatable(t, {__mode = 'k'})` still lets those entries be collected —
  measured. Since our weakness is a storage form, changing the mode has to walk
  the table and re-wrap in place, and a weak table must keep everything in the
  hash part (array slots are plain `LuaValue`s and cannot hold a `WeakRef`).
* **`__gc` needs the object *alive*, which no JVM reference type can give you.**
  Every "it's dead" notification (`WeakReference` cleared, `Cleaner`,
  `PhantomReference`) arrives after the object is unreachable, and the reference
  cannot be recovered. Only `finalize()` runs while `this` is still usable, and
  it is allowed to *resurrect*. So a table with `__gc` gets a sentinel that only
  it points to; when both go unreachable the sentinel's `finalize` pushes the
  table onto a pending queue, and the next `collectgarbage` drains that queue on
  the **Lua thread** — never on the JVM finalizer thread, where calling into a
  `LuaState` would race and any exception would vanish without a trace.

### Three known divergences from the reference

These are not oversights; each is a place where the JVM has no primitive to
match, and each is documented rather than papered over.

* **Ephemeron tables only handle the case where the value *is* the key.**
  `__mode='k'` with `t[k] = k` clears the entry (matching the reference), and
  the usual cache shape `cache[obj] = {fresh data}` keeps its value (also
  matching). But `t[k] = {k}` — value *contains* key, key otherwise
  unreachable — keeps the entry where Lua would drop it. The two halves are
  mutually exclusive in Java: holding the value strongly keeps the key alive;
  holding it weakly loses the value whenever the key is alive but the value is
  not otherwise referenced, which is exactly the common cache. Data loss is
  worse than retention, so we retain.
* **A weak entry whose key has a `__gc` is dropped one cycle early.** Lua marks
  finalizable objects, so the entry survives the cycle that schedules the
  finalizer. Ours keys off `WeakReference` clearing, which happens in the same
  cycle.
* **Objects with `__gc` are only reclaimed when `collectgarbage` runs.** The
  JVM never tells us an object is unreachable except by collecting it, so there
  is no way to schedule a finalizer without an explicit cycle.

### Phase 7 so far

`string` (`StringLib`), `table` (`TableLib`), `math` (`MathLib`), `utf8`
(`Utf8Lib`), `os` (`OsLib`), `io` (`IoLib`) and `package` (`PackageLib`) are
ported, each with its own differential gate — `StringLibTest` (29),
`MathLibTest` (26), `TableLibTest` (18), `Utf8LibTest` (13), `OsLibTest` (24),
`IoLibTest` (35), `PackageLibTest` (22).

#### Ten things the reference binary settled in Phase 7

* **A shared `lauxlib.c` layer has to come *before* any library.** Argument
  checking (`checkString` / `checkInteger` / `argError` / …) is not scaffolding:
  the message text is part of the observable contract, and six hand-rolled
  copies would drift apart within a week. One detail is only right if the
  parameter numbering is 1-based: `s:rep(nil)` reports `bad argument #1`,
  because `luaL_argerror` decrements for a method call.
* **`math.max` / `math.min` use `lua_compare`, not a numeric comparison.** So
  `math.max("a","b")` is `"b"`, and `math.max(1,"b")` raises
  `attempt to compare number with string` via the failed `__lt` path. They also
  preserve the operand's subtype: `math.max(1, 2.0)` is `2.0` (a float).
* **`math.fmod`'s float path is C's `fmod`, not Lua's `%`.** They differ in the
  sign of the result: `math.fmod(-7.5, 2)` is `-1.5` (sign of the dividend)
  while `-7.5 % 2` is `0.5` (sign of the divisor). Reusing the VM's `Arith.modf`
  here is the easy mistake and it is wrong by a sign.
* **`math.log`'s base-2 and base-10 branches are genuinely different
  computations.** C calls `log2` / `log10`; `log(x)/log(2)` differs from
  `log2(x)` on 9 of 43 sampled inputs, and `log(x)/log(10)` from `log10(x)` on
  25 of 43. Java has no `log2`, so it is reconstructed as
  `e + log(m)/ln2` from the mantissa decomposition `x = m * 2^e`, which lands
  within 1 ulp on 190 of 192 inputs.
* **`table.insert` on an object with `__newindex` must call it.** `ltablib.c`
  does not use `luaH_setint`; it uses `lua_seti`, which goes through
  `luaV_finishset`. Writing raw leaves `table.insert(ud, v)` silently doing
  nothing — no error, no write.
* **`os.execute` must spawn `sh`, not `/bin/sh`.** C's `system()` is
  `execl("/bin/sh", "sh", "-c", cmd)` — **argv[0] is `sh`** — and the shell
  prefixes its diagnostics with argv[0]. Spelling the full path turns
  `sh: 42: command not found` into `/bin/sh: 42: command not found`, which the
  differential gate sees immediately.
* **A userdata's metatable lives on the object, not on its type.**
  `luaL_testudata` asks "is this userdata's metatable the one registered under
  `FILE*`?", so file handles cannot share a per-type metatable with the
  internal ones (the PRNG state, `gmatch`'s state) without those being mistaken
  for files. `LuaUserdata` was added for this, and with it `__gc` — otherwise a
  file left un-`close()`d by a script never gets closed at all.
* **`io` on a real file must be block-buffered, and `io.write` must not be.**
  C stdio buffers file output: after `io.output(p):write("x")`, reading `p`
  back gives *nothing*, because the bytes are still in the `FILE*`. Standard
  streams are the opposite — they are line/unbuffered, and `io.write`'s output
  has to reach whichever stream the host installed. So `IoLib` buffers
  `RandomAccessFile` writes (flushing on `flush`, `seek`, any read, and close)
  and writes straight through for stdin/stdout/stderr.
* **`require`'s searcher result is tested with `lua_isstring`, not "is a
  string".** A searcher that returns the *number* `42` has its value
  concatenated into the not-found message; only a non-string, non-number
  result is treated as "this searcher said nothing" and drops the
  `\n\t` prefix again.
* **Load-time errors run the chunk name through `luaO_chunkid` too.** The
  module file is compiled as `"@" + path`, but `luaG_addinfo` strips the `@`
  before printing, so a syntax error in a required module reads
  `/tmp/m.lua:1: syntax error near 'is'` — not `@/tmp/m.lua:1: ...`.
* **Every library has to be registered in `_LOADED`, not just in `_G`.**
  `coroutine` was the one that slipped through: `require("coroutine")` reported
  `module 'coroutine' not found`, because the table was only in the globals.

#### One divergence that is accepted, not fixed

`sin` / `cos` / `tan` / `asin` / `acos` / `atan` / `exp` / `log` / `log10` /
`sinh` / `cosh` / `tanh` / `pow` are **not bit-identical** to the reference.
Java's `Math` is fdlibm; the reference binary links the platform libm (macOS
`libsystem_m` here). Both are correct to within 1 ulp of the true value, but
they round differently in the last place: of 43 typical inputs, `sin` differs on
1, `tan` on 12, `cosh` on 6. Closing that gap means shipping a libm of our own,
which neither fits the goal nor matches what Lua itself guarantees — Lua's own
results shift with the platform. The differential cases therefore compare these
functions to 14 significant digits (1 ulp is ~2.2e-16 relative). Everything
that *can* agree does agree: `sqrt`, `abs`, `floor`, `ceil`, `frexp`, `ldexp`
and the `log` branches above are bit-exact.

#### Two divergences in `io` that are accepted, not fixed

* **`file (%p)`.** `f_tostring` prints a pointer, so `print(io.stdout)` can
  never match byte for byte. `IoLib` emits a same-shaped hex value and the
  differential harness normalises `0x…` away.
* **`stdin` seeking.** The reference's stdin in the harness is redirected from
  `/dev/null`, which *is* seekable (`lseek` returns 0); ours is a byte array,
  which is not. `io.stdout:seek()` agrees (`nil, "Illegal seek", 29` on a
  pipe); `io.stdin:seek()` is not compared, because the difference is in where
  the stream comes from, not in what `io` does.

#### Two divergences in `package` that are accepted, not fixed

`loadlib.c`'s fallback branch is the honest one here: there is no `dlopen` on
the JVM (reaching it would mean JNI or FFI — a different project), so the C
searcher behaves the way C does on a platform built without dynamic loading.

* **`package.loadlib`** returns `nil, "dynamic libraries not enabled; check
  your Lua installation", "absent"`. The reference binary is built with
  `dlopen`, so it returns a `dlerror()` string and `"open"`.
* **`require` of a module whose `.so` actually exists** stops at "dynamic
  libraries not enabled" instead of loading it. Modules that only exist as
  `.lua`, and `package.preload` entries, are unaffected.

### Phase 8 so far

`load` / `loadfile` / `dofile` (`BaseLib`) and the binary-chunk pair
`lundump.c` / `ldump.c` (`Undump` / `Dump`) are in, with two differential
gates: `LoadTest` (25) and `DumpTest` (16).

`DumpTest` compares at **two granularities**, and the second one is the real
gate for this phase:

1. **Runtime.** Each script's stdout is compared byte for byte, as everywhere
   else.
2. **Artefact.** `string.dump(f)` is written to a file by each side and the two
   files are compared byte for byte. Runtime-only comparison would let "the
   compiler emits something slightly wrong that still happens to run" through;
   the artefact comparison pins down constant ordering, the line tables, local
   and upvalue debug info, and `maxstacksize` as well.

Eight shapes are checked that way — plain, nested, vararg, upvalue, `goto`,
loop, assorted constants, and a constant string longer than
`LUAI_MAXSHORTLEN` (which takes the non-interned long-string path on both the
dump and the undump side).

#### Six things the reference binary settled in Phase 8

* **`luaU_undump` rewrites the chunk name before it ever reports an error.**
  A leading `@` or `=` is dropped; a name that starts with the signature byte
  (i.e. the chunk name *is* the binary chunk, which is what `load(s)` uses when
  no second argument is given) becomes the literal `binary string`. So
  `load("\27Lua")` reports `binary string: bad binary format (truncated
  chunk)` — not an ESC-prefixed mess.
* **`f_parser` consumes the first byte on the binary path but hands it back on
  the text path.** `luaU_undump`'s comment says so directly: "skip 1st char
  (already read and checked)". Ungetting it before `undump` misaligns the whole
  header by one byte and turns `truncated chunk` into
  `not a binary chunk`.
* **`luaO_chunkid` has a third form, and it is the one `load` hits.** Only
  `@x` and `=x` are stripped; anything else is wrapped as `[string "x"]`.
  `load("x++")` reports `[string "x++"]:1: syntax error near '+'` — a
  "strip the prefix" implementation that skips the wrapping reports `x++:1:`,
  and every load-time error message is then wrong.
* **Chunk names are byte strings, and `luaO_chunkid` truncates by bytes.**
  Decoding to a Java `String` first and truncating there gets the length wrong
  on non-UTF-8 names. `chunkId` therefore has a `byte[]` core and the `String`
  overload goes through it.
* **A prototype with no source gets *no* position at all — not even a line
  number.** `luaG_addinfo` is `if (src) ... else return msg`. This is
  observable: `string.dump(f, true)` strips the source, so
  `pcall(load(string.dump(f, true)))` reports the bare `boom`. Falling back to
  the chunk name not only adds a prefix, it splices the raw binary chunk into
  the message (invalid UTF-8, so it decodes to replacement characters).
* **`lundump`'s `default: lua_assert(0)` is a no-op.** `lua_assert` compiles to
  `((void)0)` in a default build, so a chunk whose constant type byte is `99`
  loads fine and that constant is just `nil`. Raising an error there is
  inventing a semantic of our own. Measured, not assumed: the hand-built chunk
  loads as a `function` on the reference.

One more, on the dump side: `dumpFunction` compares sources **by reference**
(`f->source == psource`), not by content. Short strings are interned so the two
agree, but a long source name is not interned and C writes it out again for
every nested function. Comparing by content would emit *less* than C does.

### `string.pack` / `string.unpack` / `string.packsize`

Ported as `StringPack` (the pack/unpack half of `lstrlib.c`), with `PackTest`
(21 scripts). This is the **only part of the standard library whose output
depends on the host platform**: `l` / `L` are C's `long`, `T` is `size_t`, `!`'s
default maximum alignment is `offsetof` of `LUAI_MAXALIGN`, and the default byte
order is the host's. Those are pinned to the platform the reference binary runs
on — LP64 + little-endian — because on a platform where `long` is 4 bytes the
reference itself emits different bytes.

Two more things the reference binary settled here:

* **`X` consumes the option after it.** `getdetails` calls `getoption` to learn
  the next option's alignment, and that call advances the format string. So
  `string.packsize("Xi4")` is `0`, and `string.pack("Xi4", 7)` is `""`. An
  implementation that peeks without advancing returns `4` — which looks more
  sensible and is wrong.
* **`str_pack`'s `lua_pushnil` marker is observable.** It exists to separate the
  arguments from the string buffer, but it sits at index 2, so with no second
  argument `string.pack("c2")` reports `string expected, got nil` rather than
  `got no value`.

### Phase 9 — the `debug` library, hooks included

`ldblib.c` is in as `DebugLib`, together with the parts of `ldebug.c` that only
the introspection API needs (`lua_getstack` / `lua_getinfo` / `luaG_findlocal` /
`luaG_traceexec` / `luaG_tracecall`) and `lauxlib.c`'s `luaL_traceback`.
`DebugTest` (36 scripts) is the gate. All sixteen `debug` functions are in.

`LuaLibs.openAll` (`linit.c`'s `luaL_openlibs`) also landed — before it existed,
"open the standard libraries" was ten calls that only the test driver knew the
order of.

#### The hook machinery, and where its call points are

`sethook` is not a library function that can be ported on its own: it is a hook
*dispatcher* whose call points are spread over the interpreter. Porting it meant
adding, in addition to `DebugLib.dHook` (`ldo.c: luaD_hook`):

| Call point | Upstream | Fires |
|---|---|---|
| `LuaState.precallC` | `ldo.c` | `call` for C functions — **before** the C function itself runs |
| `DebugLib.dHookCall` from `Vm`'s `startfunc` and from `OP_VARARGPREP` | `ldo.c: luaD_hookcall` | `call` / `tail call` for Lua functions |
| `DebugLib.rethook` from `LuaState.poscall` | `ldo.c: rethook` | `return` for both kinds |
| `DebugLib.traceExec` from `Vm`'s `vmfetch` | `ldebug.c: luaG_traceexec` | `line` and `count` |

plus `trap` on `CallInfo` and `hook` / `hookmask` / `hookcount` / `allowhook` /
`oldpc` on `LuaState`. The point of `trap` is that it is a *local* variable in
`luaV_execute`, so with hooks off the main loop does not even branch.

#### Four more things the reference binary settled

* **`lua_sethook` must call `settraps`.** The main loop's `trap` is a local that
  is only re-read from the frame at a few points (`updatetrap`). Turning a hook
  on *mid-execution* therefore does nothing at all unless every live Lua frame's
  `trap` is set to 1 — which is what `settraps` does. Without it,
  `debug.sethook(f, "l")` produces zero line events.
* **A hook error is *not* caught by the `pcall` it is reported for.** The `call`
  hook for `pcall` fires inside `precallC`, *before* `pcall` establishes its
  recovery point, so `pcall(function() ... end)` cannot catch an error raised by
  a `call` hook on `pcall` itself.
* **`CIST_HOOKED` is tested before `isLua`** in `funcnamefromcall`. The frame
  carrying the mark is often a **C** frame (the `call` hook for `pcall` is
  reported on `pcall`'s own C frame), so testing `isLua` first never reaches it
  — and the traceback line changes from `in hook '?'` to
  `in function <file:line>`.
* **Hooks can never yield.** `hookf` calls the user function with `lua_call`,
  which goes through `luaD_callnoyield`, so the non-yieldable counter is non-zero
  inside a hook. Measured: `coroutine.yield()` in a hook reports `attempt to
  yield from outside a coroutine` on the main thread and `attempt to yield
  across a C-call boundary` in a coroutine — in both cases the program or
  coroutine dies. This is why the port does not implement `CIST_HOOKYIELD` at
  all: it is unreachable from Lua. (`CIST_HOOKYIELD` exists upstream for *C*
  hooks installed via the C API, which are called directly rather than through
  `lua_call`.)

#### One ordering bug found by the hook gate

`allowhook` must be restored at the **protected-call boundary**, *after* the
message handler has run — not before. The handler runs at the throw point, where
`allowhook` is still 0 (that is what stops a traceback from re-entering the
hook). Restoring it earlier means the traceback triggers the hook again, which
raises again, which `applyErrMsg` turns into `error in error handling`. The
symptom: `debug.sethook(function() error("x") end, "c")` followed by an uncaught
error printed `error in error handling` instead of a real traceback.

#### Five things the reference binary settled in Phase 9

* **Level 0 is the C frame of `getinfo` itself.** `lua_getstack(L, 0, &ar)`
  succeeds and yields `what = "C", currentline = -1, name = "getinfo"`. The name
  comes from `funcnamefromcall` reading the *caller's* frame, so it answers "what
  was the caller calling", not "what is this frame called". Getting this off by
  one shifts every `debug.getinfo(n)` result by a level.
* **`'f'` and `'L'` need `lua_rotate`, not a pop.** The result table is pushed
  *after* `lua_getinfo`, so `lua_getinfo`'s own outputs sit *underneath* it.
  `treatstackoption` is `lua_rotate(L, -2, 1)` followed by `lua_setfield(L, -2)`.
  Doing `L.top--` instead pops the result table itself — the symptom is
  `debug.getinfo(1)` returning something that prints as a function, and
  `debug.getinfo(1).func` holding the table.
* **`pushfuncname` tries a *global* name first.** `error` is `global 'error'` as
  far as `getobjname` is concerned (it is a `GETTABUP _ENV`), but a traceback
  prints `function 'error'` — `pushglobalfuncname` looks the function up **by
  value** in `_LOADED._G` and wins. Checking only `ar.namewhat` gets the
  qualifier wrong on every C function that has a global name.
* **The message handler runs *before* the stack unwinds** — `luaG_errormsg`
  calls it at the throw point, not from `luaD_pcall`'s recovery. This is the whole
  reason `xpcall(f, debug.traceback)` shows the full traceback instead of just the
  outer two frames. Java has no `longjmp`, but the *Lua* stack (`stack` / `top` /
  `ci`) is still intact at the `catch`, so running the handler at the top of
  `pcall`'s catch is equivalent.
* **`LEVELS1` / `LEVELS2` are 10 and 11**, not a round 10/10, and the head and
  tail are *not* the same length. The "(skipping N levels)" count is off by one
  otherwise, and only on stacks deep enough to trigger it.

#### Two bugs found by the `debug` gate, neither of them in `debug`

* **`OP_TESTSET`'s condition was inverted, which broke `and` / `or`.** Upstream
  tests `l_isfalse(rb) == k`; this port tested `toBoolean() == (k != 0)` — and
  those two are exact opposites over `k`'s two values. It is a *quiet* bug:
  `TESTSET` only survives when the result register differs from `B`
  (`patchTestReg` rewrites it to `OP_TEST` otherwise), so `if x then` was always
  right while `x and y` was always wrong. The symptom is
  `print(t and "yes")` printing the table, and — worse — `cond and a or b`,
  the most common idiom in Lua, taking the wrong branch. **The bytecode gate
  could not see it**: the emitted code was field-for-field identical to the
  reference's. `RuntimeTest.logicalOperatorsReturnValues` now pins the returned
  value, not just the truthiness.
* **`xpcall`'s handler ran after the call chain had already been restored.**
  See the fourth bullet above; `xpcall(f, debug.traceback)`'s traceback was two
  frames long instead of seven.

#### One thing the reference binary settled that is a *driver* difference

`lua.c` runs the script from inside `pmain`, a **C function**, so an extra C
frame sits under the main chunk. It shows up as the trailing `[C]: in ?` in every
traceback and as `debug.getinfo(3).what == "C"`. A host that calls
`luaL_dostring` directly has no such frame and sees neither. This is therefore
fixed in the differential driver (`Diff` now mirrors `lua.c`: `pmain` +
`docall` + `msghandler`), not in the engine — bending the engine to match would
have made `luaL_dostring` wrong instead.

### Phase 10 — the upstream conformance suite, end to end

The heaviest part of the fourth gate: `UpstreamSuiteTest` runs the upstream
`.lua` files unmodified and compares their output with the reference binary
**byte for byte**; `UpstreamSmokeTest` collects the ones that must not be
compared that way but still have to finish.

| File | Why it cannot be compared byte for byte |
|---|---|
| `cstack.lua` | each case prints a `final count` = how much C stack one nesting level eats; Java and C necessarily differ |
| `gc.lua` | the whole file tests the incremental collector's `count` / `step` / `setpause`; this port has no such layer |
| `literals.lua` | the trailing locale section needs `os.setlocale("pt_BR")` to actually take effect; the JVM cannot |
| `locals.lua` | `tracegc` writes a progress dot from a finalizer with `io.stderr:write'.'`; when it runs is up to the JVM |
| `constructs.lua` | line 319 is `_ENV.GLOB1 = math.random(0, 1)`; two processes can never share a seed |
| `sort.lua` | it prints sort timings and a comparison count, and the input is generated with `math.random` |
| `math.lua` | it prints `random seeds`, the range of the random draws, and timings |

#### What the reference binary settled in Phase 10

* **`__close` can still yield after an error.** When `yieldable(L)` holds,
  `lua_pcallk` does **not** install a local recovery point: the error travels all
  the way to `lua_resume`, and `precover` → `finishpcallk` →
  `luaF_close(..., yy = 1)` finishes the job — so every `__close` may yield, and
  a new error raised by a `__close` **replaces** the old one for the variables
  still pending. In `locals.lua`'s "yielding inside closing metamethods", three
  `<close>` variables yield in turn; the middle one raises `error(err + 20)`
  after yielding, so the third sees (and `pcall` finally returns) 30, not 10.
  This port uses Java exceptions instead of `longjmp`, so the recovery point sits
  in `pcall`'s `catch` and `yy` has to be passed down explicitly — `pcall` picks
  it from `yieldable()`, which is exactly `k != NULL && yieldable(L)`.
* **When a yield interrupts the unwinding, somebody has to keep the error
  object.** In C it is always on the stack (`luaD_seterrorobj` rewrites it before
  every close, and `finishpcallk` reads `L->top - 1`). Here it lives in the
  exception and vanishes with the Java stack, so it is parked in
  `LuaState.closeErrPending` until the next `finishPcallK`. It must **yield to a
  newer error**, or the third variable would still see the stale 10.
* **`luaB_error` has to be written in the C shape**, `lua_settop(L, 1)`
  included: that call decides what sits one slot below the top, which is what
  `luaD_seterrorobj`'s `default` branch reads. Without it, errors out of
  `coroutine.wrap(...)` lose their `file:line:` prefix.
* **`luaL_where` and `luaG_addinfo` answer "no debug info" in opposite ways.**
  The former needs `currentline > 0` and otherwise emits nothing; the latter
  prints `?:-1:` anyway. Merging them breaks both `error("x")` and the
  `^%?:%-1:` assertion in `errors.lua`.
* **`collectgarbage` validates its option first**, and `"generational"` /
  `"incremental"` return the **previous mode as a string**
  (`lbaselib.c: pushmode`), not an integer.
* **`luaD_shrinkstack`'s `stackinuse` is the max of `top` and every
  `ci->top`**, not `top` alone, and `stackErr` is cleared only when a shrink
  actually happens.
* **The main thread starts one slot up** (`stack_init` reserves it for
  `base_ci.func`), `IoLib` must `lua_pop` after creating the std files, and
  `Diff` must push `msghandler` onto the stack the way `lua.c: docall` does —
  each is worth one level of recursion in `errors.lua`'s "expected stack overflow
  after N calls".

#### Four real bugs found while getting `sort.lua` / `math.lua` to finish

Both files used to crash on entry; they only reach `OK` after these. None of them
is a "we differ" — all four were plain mistakes on our side:

* **`tunpack`'s overflow test must compare as unsigned.** In C, `n` is
  `lua_Unsigned`, so a wrapping `e - i` becomes a *huge* value; a Java `long`
  wraps to a *negative* one, the signed comparison lets it through, and the loop
  then pushes from `minI` to `maxI` until `table.unpack` dies with
  `OutOfMemoryError`.
* **`top + n` overflows in `checkStack` / `checkStackOk`.** With an `n` of `2^31`
  the sum goes negative, so the "over `MAXSTACK`" test stops firing — same
  `OutOfMemoryError`. Now computed as `long`.
* **`tmove`'s test must not be simplified.** C asks `e < LUA_MAXINTEGER + f`,
  *not* `e - f < LUA_MAXINTEGER`: the two agree only when nothing wraps, and
  wrapping is exactly what is being tested. Simplified,
  `table.move({}, minI, maxI, 1)` reports `#4 (destination wrap around)` where
  the reference reports `#3 (too many elements to move)`.
* **`luaG_tointerror` carries a `varinfo` suffix.** For `math.huge << 1` the
  reference says `number (field 'huge') has no integer representation`.
* **`tonumber` must not use `String.trim()`.** Java's `trim` strips "characters
  at or below U+0020", which *includes `\0`*; C recognises only `SPACECHARS`
  (`" \f\n\r\t\v"`) and additionally requires the parse pointer to land exactly
  on `s + l`. So both `tonumber('1\0')` and `tonumber('1\0', 2)` silently
  returned `1`. `b_str2int` is now written over bytes and re-checks the length.

#### And one more: `table.sort` picks its pivot with *unsigned* arithmetic

`sort.lua` lives in the smoke set — it prints timings, so its output can never be
compared byte for byte — and a later full run failed inside it with
`attempt to compare nil with number` out of `table.sort`. The file seeds nothing,
so `math.random()` hands it fresh data on every run, which makes this a *latent*
bug: it only surfaces when a partition comes out unbalanced enough for `auxsort`
to draw a new random pivot.

* **`IdxT` is `unsigned int`, and so is the pivot seed.** C's `rnd % (r4 * 2)` is
  an unsigned modulo, always in `[0, r4*2)`. Java's `int` is signed, and the seed
  carries exactly the 32 bits C would have produced — negative half the time. A
  signed modulo then drags `p` below `lo`, and at the extreme below `1`, where
  `a[p]` is nil and `sort_comp` reports `attempt to compare nil with number`.
  Now `Integer.remainderUnsigned`, matching C.
* **`rnd` is passed by value.** In C it is a parameter of `auxsort`, so
  re-randomizing inside a recursive call never leaks back to the outer frame. It
  used to be shared through an `int[]` here, which meant a seed drawn deep in one
  subtree went on to steer every remaining partition of the whole sort.

One run in twenty is a bad way to find a bug, so `choosePivot` now carries its own
deterministic test (`TableLibTest.choosePivotStaysInsideTheInterval`) that checks
the C `lua_assert` — `lo + r4 <= p <= up - r4` — directly, instead of hoping a
50 000-element sort trips over it.

#### Phase 11: a precompiled chunk must load through *every* door

`ldo.c: f_parser` reads one byte. If it is the first byte of `LUA_SIGNATURE`, the
chunk goes to `luaU_undump`; otherwise to `luaY_parser`. That single test is why a
caller never has to say whether it is handing over source or bytecode — and why
`load`, `loadfile`, `dofile` and `require` all accept precompiled chunks for free.

The dispatch used to exist only inside `BaseLib`'s `load` / `loadfile`. `lua_load`
itself (`LuaState.load`) did not look at the signature, so **`require` of a
precompiled module failed**:

```
error loading module 'mod' from file '/tmp/mod.lua':
	/tmp/mod.lua:1: unexpected symbol near '<\27>'
```

The reference loads it and returns the module. `PackageLib` reaches the loader
through `LuaState.loadFile`, which is why this showed up there and not in `load`.
The fix is at the `lua_load` layer, not a patch on `require`:

* `LuaState.load` / `loadFile` now dispatch on `Lua.LUA_SIGNATURE0`;
* `loadFile` still runs the `lauxlib` preamble first (BOM, `#!`), because a
  precompiled chunk never starts with `#` — order matters;
* the binary branch does **not** push the first byte back: `luaU_undump` says
  "skip 1st char (already read and checked)", so `checkHeader` starts at byte two.

`BinaryChunkTest` covers both directions:

* **chunks produced by the real `luac`** (`ReferenceLua.compile`, located via
  `LUAC54_BIN` or sitting next to the reference `lua`), run on both sides and
  compared byte for byte. Self-compiled chunks would be grading our own compiler
  with our own compiler, so the reference's `luac` is what produces them. The
  corpus covers upvalues, varargs, `goto`, mixed table constructors, integer and
  float edges, coroutines, a 300-entry constant table, and two scripts that *fail*
  on purpose — the error messages carry line numbers and local-variable names, so
  losing the debug info in `undump` turns them red;
* **chunks produced by our own `string.dump`** (`require`, `dofile`, `loadfile`,
  the `mode` argument, the `binary string` chunk-name rule, and truncated or
  tampered chunks, which must be load-time errors rather than crashes).

#### And the fuzz corpus was 89% dead

The generator had been emitting programs that do not compile:

* table constructors wrote `{'key0' = v}`. Only a *name* can be a bare key, so a
  string key needs `[k] = v` — the numeric case was bracketed, the string case was
  not;
* `expression` emitted `(select('#', ...))` unconditionally, while a function
  declares `...` only 40% of the time. Outside a vararg function that is
  `cannot use '...' outside a vararg function`.

Measured with the reference `luac`: **178 of 200 seeds failed to compile.** A
program that does not compile produces the *same* error on both sides, so the
comparison "passes" while proving nothing — the strongest gate had been quietly
vacuous over tens of thousands of seeds. Fixed (keys are always bracketed; the
generator tracks whether the function being built is vararg), and re-measured:
0 / 400.

`FuzzTest.assertCompiles` now makes that state unrepresentable: it fails the seed
whose reference output has the shape of a compile error. The discriminator is
"the message ends in `near 'tok'`" rather than "contains `expected`", because
runtime errors are full of `expected` (`bad argument #1 to 'upper' (string
expected, got nil)`) and those are *supposed* to happen.

#### Fuzzing the bytecode path too

Ordinary fuzzing only ever takes the text path — `Dump` and `Undump` were touched
by a few dozen hand-written cases, which is exactly the blind spot the
`require`-a-precompiled-chunk bug lived in. `generatedChunksSurviveDumpAndUndump`
closes it: the generated body is wrapped in a vararg function, `string.dump`ed,
`load`ed back and run. That pushes thousands of *random* `Proto`s through
serialization — constant-table order and varint encoding, line info, local and
upvalue debug names, `maxstacksize`, nested protos. A sentinel print
(`dumped true loaded true`) is asserted on, so a silent `string.dump` failure
cannot masquerade as agreement.

#### Five more things the reference binary settled

The fuzz generator learned `table.*` and immediately paid for itself: five
divergences, none of which any hand-written case had hit.

* **`l_str2int`'s accumulator is `lua_Unsigned`.** In `lobject.c` the running
  value `a` is unsigned, and the overflow guard is `a >= MAXBY10`. With a signed
  `long`, once `a` passes 2^63 it goes *negative* and the guard stops firing, so
  the overflow escapes: `1 + (math.mininteger .. 0)` — the operand is
  `"-92233720368547758080"` — wraps to exactly 0 and returns `1` instead of
  falling through to the float path (`-9.2233720368548e+19`). Now
  `Long.compareUnsigned`.
* **`luaG_runerror` adds `file:line:` where it is raised, not later.** `idiv` and
  `mod` divide-by-zero go through `luaG_runerror`, which calls `luaG_addinfo` on
  the spot. Ours threw a bare `LuaError` that only received its prefix at the
  protection boundary, by which time the raising frame was gone — so
  `coroutine.resume` returned the error as a *value* and it arrived without a
  position: `false  attempt to divide by zero` instead of
  `false  file:line: attempt to divide by zero`. `Arith.rawArith` now takes a
  `LuaState` for exactly this reason.
* **`luaH_newkey` refuses nil values.** Its first line is
  `if (ttisnil(value)) return; /* do not insert nil values */`. Assigning nil to a
  key that does not exist creates no node and triggers no rehash. Creating the
  node changes the computed *array-part size*, which is a direct input to `#t`:
  `table.move({[1]=4.0}, 0, 2, 1)` leaves `#t == 2` in the reference and `0`
  otherwise. The nil-key and NaN-key checks sit *before* that line, so
  `t[nil] = nil` must still raise `table index is nil`.
* **`lauxlib.c: interror` tests `lua_isnumber`, not the type.**
  `luaL_checkinteger` that cannot get an integer reports
  `number has no integer representation` when `lua_isnumber(L, arg)` — true for a
  string that parses as a number — and `number expected, got string` otherwise.
  So `string.format("%d", "3.5")` is the former (`"3.5"` parses, 3.5 just is not
  an integer) and `string.format("%d", "abc")` is the latter. The guard used to be
  `v.isNumber()`, which made both the latter.
* **`%.14g` must round once, from the exact value.** Java's
  `String.format("%.Nf")` does not round the *value*: it rounds
  `Double.toString`'s shortest representation, i.e. twice.
  `math.sqrt(math.maxinteger)` is exactly `3037000499.976049900054931640625`,
  whose shortest form is `3037000499.97605`; the 15th digit is a 5, so the second
  rounding carries and produces `3037000499.9761`. The reference prints
  `3037000499.976`. `StringLib` already formatted through `BigDecimal` (whose
  `new BigDecimal(double)` is the exact binary expansion); `luaO_tostring` had its
  own copy built on `String.format`. Two copies of one C conversion is how they
  drifted, so `Vm.formatG` now delegates to `StringLib.generalDigits`.

That last one is worth stating plainly: measured over 4602 printed floats, the
old code was wrong on **12** — 0.26%. No hand-written case was ever going to find
it, and no amount of upstream-suite greenness would have either. It took the
fuzzer. `RuntimeTest.floatFormattingMatchesReferenceAcrossMagnitudes` now pins it
with a few thousand values, generated by an LCG *inside* the script so that the
random-number generator itself stays out of the comparison.

#### And two more, from widening the corpus

The generator then learned `utf8.*`, `gmatch`, `next`, `string.pack`/`unpack`,
`table.pack`, non-string error values, `math.ult`/`fmod`/`max`/`min`, and
`rawequal`/`rawlen`/`select`. Measured before trusting it: every new branch fires
in 6–29% of programs, so the corpus is actually alive. It immediately found two
more, both in `ltable.c` — the same file as three of the previous five.

* **`luaH_getn` answers `limit - 1` before it ever binary-searches.** The manual
  only asks `#t` to return *a* boundary, which makes "we found a valid boundary"
  sound sufficient. It is not: real programs observe which one. `ltable.c` has a
  fast path for the common case (`t[#t] = nil`, then ask for the length again):

  ```c
  if (limit > 0 && isempty(&t->array[limit - 1])) {
    if (limit >= 2 && !isempty(&t->array[limit - 2]))
      return limit - 1;          /* (1a) do not search */
    else { /* (1b) */ ... binsearch ... }
  }
  ```

  An array of `[1, nil, 3, nil]`: binary search returns `1` (it finds the *first*
  boundary), the reference returns `3`. `table.pack(1, nil, 3, nil)` leaves
  `#t == 3`, not `1`. Across a systematic sweep of hole patterns — every single
  hole up to `n = 32`, every hole pair up to `n = 19`, every triple up to
  `n = 11`, all 32 `table.pack` nil combinations — **212 of 4968 results were
  wrong (4.3%)**.

* **`luaH_resize` must let the hash part shrink to *dummy*.** After
  `rehash`, C's `reinsert` moves the old entries with `luaH_set`, so any integer
  key `<= asize` is routed into the new array part and occupies **no hash slot**;
  `luaH_resize(L, t, 4, 0)` therefore ends with a dummy (zero-slot) hash. This
  port sized the hash to fit *all* the moved entries, so an array of 4 came with
  2 stale hash slots. That matters because `luaH_newkey` tests `isdummy(t)`: with
  a non-dummy hash, the next insert just takes a free slot and **never rehashes**,
  so the array part stops growing. `t = {}; t[2]=2; t[3]=3; t[4]=4; t[6]=6;
  t[7]=7` gives the reference an array part of 8 and `#t == 7`; this port had 4
  and 4. The sweep above is what exposed it — the previous corpus only ever
  inserted keys `1..n` in order, which never reaches this path.

Both are pinned by `RuntimeTest.tableLengthBoundaryMatchesReference`, which builds
the hole patterns with loops inside the script (an LCG would put the generator
itself into the comparison) and reads `#t` twice per table, because C rewrites
`alimit` as a hint on the first call.

#### One more, from running the corpus inside coroutines

The same corpus was then also run through `coroutine.create`, which found a bug
that had nothing to do with coroutines:

> `pcall(math.random, 5, 1)` inside a coroutine reported
> `bad argument #1 to '?' (interval is empty)`; the reference reports
> `bad argument #1 to 'math.random' (interval is empty)`.

* **`_LOADED` lives at `global_State` level, not per-thread.** `lauxlib.c`'s
  `pushglobalfuncname` reverse-looks-up the current function in
  `package.loaded`, two levels deep, to turn `'?'` into a real name:

  ```c
  static void pushglobalfuncname (lua_State *L, lua_Debug *ar) {
    ...
    lua_getfield(L, LUA_REGISTRYINDEX, LUA_LOADED_TABLE);
    if (findfield(L, L.top - 1, 2)) { ... }
  ```

  `LUA_LOADED_TABLE` is a field of the *registry*, and the registry belongs to
  `global_State` — every `lua_State` shares one. This port had made `loaded` a
  per-`LuaState` table, so a coroutine's `package.loaded` was empty and every
  diagnostic inside it degraded to `'?'`. The fix is one line in the
  constructor: `loaded = main.loaded`. Note that this only shows up *inside a
  coroutine* — the main thread populated its own table, which is why the whole
  upstream suite and every earlier fuzz seed were silent about it.

`RuntimeTest.libraryFunctionNamesResolveInsideCoroutines` pins it.

#### The gate itself had a hole

A systematic sweep of `string.format("%q", ...)` — every byte 0–255, in four
positions — was the first probe that wrote **non-UTF-8 bytes** to stdout. It found
nothing wrong with the engine, and something wrong with the harness: both sides
captured the reference's and this port's output into a Java `String` by decoding
UTF-8. Any invalid byte becomes `U+FFFD`, so `0x80` and `0xEF 0xBF 0xBD` compare
*equal*:

```
>>> b'"\x80"\n'.decode('utf-8', 'replace') == b'"\xef\xbf\xbd"\n'.decode('utf-8', 'replace')
True
>>> b'"\x80"\n'.decode('latin-1') == b'"\xef\xbf\xbd"\n'.decode('latin-1')
False
```

Measured rather than assumed: with `print` deliberately changed to round-trip
through a Java `String` (i.e. corrupting every non-UTF-8 byte), the new `%q`
sweep is **red under Latin-1 and green under UTF-8** — the old gate let a real
byte-level corruption through. The gate now decodes Latin-1, which is a
byte-for-char bijection, so comparisons are genuinely byte-exact.
`RuntimeTest.quoteFormatMatchesReferenceForEveryByte` is the pin.

#### And one more, from sweeping operators against types

The next probe was a full matrix: every operator × every pair of types, with no
metamethods, comparing the *error text*. 1651 lines. A second one did the same
with metamethods present — which of the two handlers runs, `__le` falling back to
`not (b < a)`, `__index`/`__newindex` in both table and function form, `__concat`
right-associativity, `__close` timing and error propagation (245 lines). The
second one was clean; the first found this:

* **`luaG_tointerror` converts with `F2Ieq`, not "floor to integer".**
  `lvm.h` defines `LUA_FLOORN2I` as `F2Ieq` by default — the name says *floor*,
  the semantics are *"accepts only integral values"*:

  ```c
  void luaG_tointerror (lua_State *L, const TValue *p1, const TValue *p2) {
    lua_Integer temp;
    if (!luaV_tointegerns(p1, &temp, LUA_FLOORN2I))
      p2 = p1;          /* report the *first* operand if it cannot convert */
    luaG_runerror(L, "number%s has no integer representation", varinfo(L, p2));
  }
  ```

  Judging with a rounding mode makes `1.5` look convertible, so `1.5 & 3`
  reported the **right** operand: `number (local 'b') has no integer
  representation`, where the reference reports the left, `(local 'a')`. Ten lines
  of the matrix were wrong. Hand-written cases only ever used `inf`/`nan`, where
  both modes agree — which is why this survived every earlier gate.

Both matrices are now permanent tests:
`RuntimeTest.operatorTypeErrorsMatchReference` and
`RuntimeTest.metamethodDispatchMatchesReference`.

#### Two more, from sweeping the format specifiers

`lstrlib.c: str_format` is one of the densest functions in the source, so the next
probe swept it: 22 verbs × 10 flag combinations × 5 widths × 9 precisions × 22
value types — **20777 lines**. It found two things.

* **The order of the two checks.** `str_format` does *not* validate the spec
  first. For `d i u o x X` it calls `luaL_checkinteger` **before** `checkformat`,
  and for `f e E g G` it calls `luaL_checknumber` first:

  ```c
  case 'd': case 'i':  flags = L_FMTFLAGSI;  goto intcase;
  ...
   intcase: {
    lua_Integer n = luaL_checkinteger(L, arg);
    checkformat(L, form, flags, 1);
  ```

  (`c`, `a` and `s` are the other way round.) Validating the spec first meant
  `("%.100d"):format("x")` reported `invalid conversion specification` where the
  reference reports `number expected, got string` — **6620 lines wrong**.

* **A precision turns off the `0` flag for integer verbs.** C99: *"For `d`, `i`,
  `o`, `u`, `x`, and `X` conversions, if a precision is specified, the `0` flag
  is ignored."* So `("%05.3d"):format(42)` is `  042` — the precision pads to
  `042`, then the width pads with **spaces** — not `00042`. 220 lines. Float verbs
  have no such rule (`%08.3f` still zero-pads), so the fix is confined to
  `emitInt`.

`RuntimeTest.formatSpecifierMatrixMatchesReference` pins both; reverting either
one alone turns it red.

#### And a second copy of `luaL_checkinteger`

The next sweep was `os.date` specifiers, `os.time` argument checking,
`utf8.*` boundaries, and `tonumber` across bases 2–36 (854 lines). It found a
familiar shape of bug: **`luaL_checkinteger` had two implementations**, and the
older one in `BaseLib` was missing two things — the function-name prefix
(`bad argument #2 (...)` instead of `bad argument #2 to 'tonumber' (...)`) and
the "no integer representation" case, reporting `number expected, got number`
for `1.5`. Four call sites were affected: the base of `tonumber`, the index of
`select`, the level of `error`, and the index of the `ipairs` iterator. It now
delegates to `LuaAux.checkInteger`.

That sweep also turned up a second instance of "not our bug, libc disagreeing":
**`os.date("!%z", t)`**. C's `strftime` reads the *process-global timezone*,
not the `tm` Lua hands it, and with the `!` (UTC) flag the two libc's answer
differently — macOS prints the local offset `+0800`, glibc prints `+0000`. The
test therefore only exercises `%z`/`%Z` **without** `!`, where both sides run on
the same machine and the answer is well-defined.

`string.pack`/`unpack`/`packsize` also got a full sweep — every format verb
(including `i`/`I` widths 1–16, `c`/`s` counts, `X` and `!n` alignment, all four
byte orders) × values, plus invalid formats and out-of-range offsets, 706 lines.
It was clean; it is now a test so that "already agrees" stays agreed.

#### One thing that is *not* a divergence: subnormal `%a`

`quotefloat` formats through C's `%a`:

```c
int nb = lua_number2strx(L, buff, MAX_ITEM, "%" LUA_NUMBER_FRMLEN "a", n);
```

C99 fixes one hexadecimal digit to the left of the point and says it "is `0` only
if the argument is denormalized" — which leaves *how* a subnormal is written
**unspecified**. glibc and Apple libc pick opposite answers:

| value | glibc (CI: ubuntu) | Apple libc / musl |
| --- | --- | --- |
| `5e-324` | `0x0.0000000000001p-1022` | `0x1p-1074` |
| `3 * 5e-324` | `0x0.0000000000003p-1022` | `0x1.8p-1073` |

This port matches **glibc**, because the gate that matters — CI — runs on
`ubuntu-latest`. `StringLibTest.subnormalQuoteFollowsGlibc` pins it with a plain
assertion *outside* the differential table, so that nobody "fixes" it by matching
a macOS reference binary and breaks Linux. (`Double.toHexString` already produces
the glibc form, so there is nothing to implement.)

#### One more, from sweeping the `table` library: `tmove` dropped a condition

The `table` sweep — `insert` / `remove` / `move` / `concat` / `unpack` / `pack` /
`sort` × positions × sizes × offsets × value types, including same-table
overlapping `move` and cross-table `move`, **6277 lines** — produced 268 diff
lines, and every one of them had an unsettling shape: **the table's contents were
identical, only `#t` differed.**

```text
< MV2-dump  #0  -2=nil,-1=nil,0=nil,1=nil,2=1,3=nil,4=nil,...
> MV2-dump  #2  -2=nil,-1=nil,0=nil,1=nil,2=1,3=nil,4=nil,...
```

The cause is a missing clause. C decides the copy direction like this:

```c
if (t > e || t <= f || (tt != 1 && !lua_compare(L, 1, tt, LUA_OPEQ))) {
  for (i = 0; i < n; i++) { ... }        /* forward */
} else {
  for (i = n - 1; i >= 0; i--) { ... }   /* backward */
}
```

This port had only the first two clauses. The third one is not about positions at
all: **when the source and destination are different tables, the copy is always
forward**, regardless of how `t` and `f` relate. Dropping it sends cases that
should go forward down the backward path.

Order does not change what ends up in the table — but it changes the *layout*,
which is what `#t` reads. `table.move({1}, -2, 1, -1, {1})` leaves the destination
with only `[2] = 1` either way; going forward clears `[1]` first and leaves
`alimit` at 1, so `luaH_getn`'s first case ("`array[alimit-1]` is nil ⇒ binary
search inside the array part only") fires and the answer is `0`. Going backward
sets `[2]` first, pushing `alimit` to 2, that case no longer applies, and the
answer becomes `2`. Hence the confusing symptom: the same sequence of writes
agrees when driven through `rawset` and disagrees when driven through
`table.move`, because only the internal layout differs.

Two lessons went into the tests:

* **Comparing table contents is not enough — `#t` has to be compared too**, or
  layout bugs are invisible.
* The sweep is now three permanent tests:
  `RuntimeTest.tableLibraryMatrixMatchesReference` (6277 lines, the one that
  found it), `RuntimeTest.patternMatchingMatrixMatchesReference` (24819 lines of
  pattern matching × subject × `init` × replacement form — **clean**, pinned so
  that "already agrees" keeps agreeing), and
  `RuntimeTest.tableLengthAcrossInsertionHistoriesMatchesReference` (3736 lines
  that make *insertion history* itself the sweep dimension: every subset of
  `1..n` × three insertion orders, fill-then-punch, `move`-built layout,
  `insert`/`remove` alternation, negative/zero keys, bisection up to 40,
  tail truncation — also clean).

Reverting the one clause turns the first of them red.

#### One more, from sweeping `debug` / `load` / `require`: `xpcall` skipped its check

The next sweep — `debug.getinfo` (14 fields × levels 0–6), `getlocal`/`setlocal`,
the upvalue family, `traceback` (including inside a coroutine), `sethook` with 10
masks plus the count hook, `load` (source / reader function / mode / env / binary
chunk), `package.path` / `cpath` / `config` / `searchers` and `require`'s odd
corners, `error` at levels 0–4, `xpcall`, `assert`, and error propagation through
coroutines, 289 lines — found **`xpcall` had lost its argument check**:

```c
static int luaB_xpcall (lua_State *L) {
  ...
  luaL_checktype(L, 2, LUA_TFUNCTION);  /* check error function */
```

C checks that the message handler is a function *before* calling anything. This
port only checked that two arguments were present, so `xpcall(f, 5)` did not
raise — it called `f`, and when `f` errored it tried to use `5` as the handler:

| | reference | this port, before |
| --- | --- | --- |
| `xpcall(f, 5)` | `false  bad argument #2 to 'xpcall' (function expected, got number)` | `true  false  error in error handling` |
| `xpcall()` | `false  bad argument #2 to 'xpcall' (function expected, got no value)` | `false  bad argument #2 to 'xpcall' (value expected)` |

The `io` sweep also went in — 32 open modes (including every invalid one), every
`read` format and byte counts 0–24, numeric reads, `seek`, `lines`, using a
closed file, `io.input`/`output`/`read`/`write`/`close`, append-mode `seek`
semantics, `r+` interleaving, a 5000-byte block, and the same file opened twice,
167 lines. It was clean, and it is worth having anyway: it is the only thing that
exercises the **errno layer** — a failed `io.open` must return
`"path: No such file or directory"` plus errno `2`, which C gets from `strerror`
and we have to match on our own.

#### A second hole in the gate: tracebacks silently truncated the output

The `debug` matrix was green on the first attempt — with the `xpcall` bug still in
the code. That was the gate failing, not the code passing.

`Diff.assertMatches` normalizes by cutting everything from the first
`stack traceback:` onwards. That is deliberate: when a *script crashes*, the
message comes first and the traceback last, and our `ldebug.c` cannot be expected
to match it line for line. But when the traceback is **printed by the script** —
`debug.traceback()`'s return value — it lands in the *middle*, and everything the
script printed afterwards was thrown away. This script prints a traceback in its
fifth section and has 180 lines after it.

The guard now rejects that case: if anything other than traceback lines (each
starts with a tab) follows the traceback, `assertMatches` fails and says to use
`assertFullMatches`, which does not strip. Thirteen existing tests turned out to
have tracebacks in their output; the refined check — content *after* the
traceback, not merely its presence — leaves all of them alone, because in those
the traceback really is last.

This is the second time the gate has been found blind rather than the code wrong
(the first was UTF-8 decoding of captured output). A gate that quietly compares
less than it claims is worse than no gate: it reports green.

#### One the fuzzer found, where the bug was in the *harness*

At scale (16500 generated programs) the fuzzer produced a program whose stack
overflow was reported on **line 67** by the reference and **line 65** by this
port. The engine was fine; the harness was one stack slot short:

```c
  lua_pushcfunction(L, &pmain);   /* to call 'pmain' in protected mode */
  lua_pushinteger(L, argc);       /* 1st argument */
  lua_pushlightuserdata(L, argv); /* 2nd argument */
  status = lua_pcall(L, 2, 1, 0); /* do the call */
```

`lua.c`'s `main` passes `pmain` **two** arguments, so the stack's initial `top`
is 3. `Diff.runLikeLuaC` passed one. One missing slot is carried all the way
down: the recursion that overflows the stack goes one level deeper, so the
innermost frame is a different function, so the reported line differs.

The reason it took a while to see: it does **not** always differ. Each frame
costs `k + 1` slots (`k` = number of locals) and the depth is
`floor((limit - base) / (k + 1))`, so a base one slot off only changes the answer
when it happens to cross a division boundary. Measured across frame sizes:

| locals `k` | 0 | 1 | 2 | 3 | 4 | 6 | 8 | 16 | 32 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| differed before | yes | yes | yes | no | no | yes | no | no | no |

Trying one example would have looked fine. Fixing the harness makes all nine
match, and `RuntimeTest.stackOverflowDepthAndLineMatchReference` now sweeps them.

(The other trap in that probe: `local function f() return f() end` is a **tail
call**, consumes no stack, and loops forever instead of overflowing — the
reference binary had to be killed after five minutes. The probe uses
`return 1 + f()`.)

#### And three sweeps that came back clean

Not every sweep finds something. `math` (every function × 30 boundary values
including ±inf, nan, −0.0, `maxinteger`, `mininteger`, `2^53`, `2^63`, `1/3`; both
return values of `modf`; `log`'s base; six kinds of argument error per function;
the seeded random sequence) plus the deterministic parts of `os`, **1423 lines**,
is clean. It stays in the suite for three reasons:

* **The last bit of floating point.** `math.sin` and friends go through `libm`;
  `Math.sin` need not agree bit for bit. Default `tostring` prints 14 significant
  digits, which happens to hide it — the sweep proves it is hidden, rather than
  never having been looked at.
* **`math.random`'s sequence** — xoshiro256\*\* pins it exactly, so a seeded run
  must match number for number.
* **The `errno` / `strerror` layer** again, on `os.remove` / `os.rename`.

Three things there cannot be tested at all: `os.clock` (elapsed time), a
`math.randomseed` with no argument (seeds from the clock), and `os.time` with no
argument (current time).

One catch while landing it: `math.atan` and `math.atan2` are **the same**
`math_atan` in `lmathlib.c`, so which name shows up in the error message is
decided by whichever key `lauxlib.c: findfield` runs into first while walking
`package.loaded` — and that order comes from the string hash, which is seeded by
`luai_makeseed` (time-based). The reference binary is **nondeterministic** here:

```
$ for i in 1 2 3; do lua -e 'print(pcall(math.atan))'; done
false  bad argument #1 to 'math.atan2' (number expected, got no value)
false  bad argument #1 to 'math.atan2' (number expected, got no value)
false  bad argument #1 to 'math.atan'  (number expected, got no value)
```

Six `atan2` and two `atan` out of eight runs. We are deterministic (the hash is
not seeded) and that is a deliberate deviation. Sweeping the zero-argument error
name of **every** function in `math`, `string`, `table`, `os`, `io`, `coroutine`
and `debug`, eight runs each, `atan` is the only one that moves; the script now
normalises it before comparing.

#### The `string` sweep: two bugs, and one thing the reference disagrees with itself about

The non-pattern-matching half of the `string` library — `sub`, `byte`, `char`,
`rep`, `upper`, `lower`, `len`, `reverse`, `format`, `tonumber(base)`, `tostring`
(pattern semantics are covered separately by the 24819-line sweep) — **3921
lines**, and it found two:

**`__name` follows C string rules.**

```c
        int tt = luaL_getmetafield(L, idx, "__name");
        const char *kind = (tt == LUA_TSTRING) ? lua_tostring(L, -1) : ...;
        lua_pushfstring(L, "%s: %p", kind, lua_topointer(L, idx));
```

`lua_tostring` hands back a `const char *` and `"%s"` treats it as a C string, so
the name is cut at the first `\0`: with `__name = "A\0B"` the reference prints
just `A`, while we printed the whole `"A\0B"`. Lua strings are not
NUL-terminated, C strings are, and here C wins. Fixed with
`LuaString.toCString()`, for the places where the C code uses a value as a
`const char *`.

**`%p` does not zero-pad.** The address was formatted `0x%016x`; the reference
prints `0x7ab30002c0`, with no leading zeros (neither macOS nor glibc pads).

**And one thing that cannot be compared at all:** with the `+` or space flag on a
NaN, macOS libc prints no sign (`nan`) while glibc prints one (`+nan`). CI's
conformance job runs on ubuntu, this machine runs macOS, so the **reference
itself** differs between the two. The script strips NaN's sign before comparing;
the implementation stays glibc-compatible.

#### The optional-argument sweep: two checks in the wrong order

The previous sweep only tried zero or one argument. This one permutes the second
and third arguments too: for ~70 functions, every combination of `{nil, 1, "x"}`
from zero arguments up to that function's arity — **1866 lines**. It found two,
and both are the same mistake: **the order of the argument checks**.

**`string.gsub`:**

```c
  int tr = lua_type(L, 3);                          /* just record the type */
  lua_Integer max_s = luaL_optinteger(L, 4, srcl + 1);
  ...
  luaL_argexpected(L, tr == LUA_TNUMBER || tr == LUA_TSTRING ||
                   tr == LUA_TFUNCTION || tr == LUA_TTABLE, 3,
                      "string/function/table");
```

The fourth argument is checked **first**. So `gsub(s, p, nil, "x")` reports
`bad argument #4 to 'gsub' (number expected, got string)`; we checked argument 3
first and reported `bad argument #3 (string/function/table expected, got nil)`.

**`tonumber`:** the three checks run `luaL_checkinteger(2)` →
`luaL_checktype(1, LUA_TSTRING)` → `luaL_argcheck(2 <= base <= 36, ...)` — the
range check comes **last**:

| | reference | before the fix |
| --- | --- | --- |
| `tonumber(nil, 1)` | `bad argument #1 to 'tonumber' (string expected, got nil)` | `bad argument #2 (base out of range)` |
| `tonumber(1, 1)` | `bad argument #1 to 'tonumber' (string expected, got number)` | `bad argument #2 (base out of range)` |

Three functions are deliberately absent: `os.time` and `os.date` read the clock
when their first argument is `nil`, `math.randomseed(nil)` seeds from the clock,
and `io.lines` with no argument reads stdin.

#### The `package` / `require` / `load` sweep: two bugs, and one counter that has to be shared

**311 lines.** `package`'s static structure, `searchpath`, every shape of
`require` (module returning `nil` / `false` / `true` / nothing, caching, a module
that raises, circular dependency, `preload` winning over disk, names with
slashes), and `load`'s `mode` / `env` / `chunkname` / reader functions.

**`package.loadlib`'s third return value says *where* it failed, not *that* it
failed.** `ERRLIB` (the library could not be opened) gives `"open"`, `ERRFUNC`
(the symbol is not in it) gives `"absent"`:

```c
    lua_pushliteral(L, (stat == ERRLIB) ?  "open" : "absent");
```

We had it hard-coded to `"absent"`, copying the fallback branch for builds
without `dlopen` — but in that branch `lookforfunc` always returns `ERRLIB`, so
`"open"` is what it should have been all along. Code that uses the third value
to decide whether to keep looking picks the wrong branch.

**A circular `require` does not report a bare `C stack overflow`.** It reports:

```
error loading module '_pk_b' from file './_pk_b.lua':
	C stack overflow
```

because the parser and `luaD_call` **share one counter**: `#define enterlevel(ls)
luaE_incCstack(ls->L)`. So the 200th level is not reached at a `lua_call` — it is
reached in the middle of some `luaL_loadfile`, the error is caught by
`luaD_protectedparser`, `loadfile` returns a non-`OK` status, and `checkload`
wraps it. Counting call depth and expression depth separately gives the naked
message instead. The sweep pins the exact shape, module name included.

Fixing that exposed the counter's other half: **the rollback**. `luaD_call`
increments `nCcalls` *before* the `try`-equivalent, so when `luaE_checkcstack`
raises, the increment is outside the `finally`; C gets it back from
`luaD_rawrunprotected`'s `L->nCcalls = oldnCcalls` at the `pcall` boundary, and
Java has no `longjmp` to fall back on. Without the rollback every C-stack
overflow leaks one level forever — measured with a `pcall`-recursion probe, the
quota walks 197 → 196 → 195 → 194 → 193, and upstream `errors.lua` starts
reporting `error in error handling` where the reference prints `OK`. Same for the
parser: the restore has to sit on `lua_load`, not on the binary/text dispatch
below it, or a failed `load` leaks two levels each time.

**And one that cannot be compared:** `package.loadlib`'s failure *message* comes
from the platform `libc` (`dlopen(...)` on macOS, `cannot open shared object
file...` on glibc), and this implementation does not load dynamic libraries at
all — three different strings, so the sweep compares only that it is a string.

#### The deep `io` sweep: `fseek` owes you `EINVAL`

`io` was the thinnest of the ten standard libraries at 167 lines. This one adds
**124 lines** of output: sixteen legal `open` modes and nine illegal ones (which
*raise* rather than returning `nil` + message — `l_checkmode` wants `[rwa]%+?b?`),
every `read` format, how `write` renders integers versus floats, all three
`seek` origins and their out-of-range cases, `lines`, `setvbuf`, closing twice,
`io.output` / `io.input` switching, and binary safety with `\0` and invalid UTF-8.

It found one thing: **a negative target position is `EINVAL`, not a generic I/O
error.**

```
reference: nil	Invalid argument	22        -- seek("set", -1)
before:    nil	Input/output error	5
```

`fseek` gives `EINVAL` for a negative resulting offset from *all three* origins
(`SET`, `CUR`, `END`), while Java's `RandomAccessFile.seek` throws *"Negative
seek offset"*, which falls through `errnoOf`'s catch-all into `EIO`. Seeking
**past** EOF is legal and returns the new position — easy to block by accident
while fixing this.

Two things are deliberately absent. `fopen` on a **directory** succeeds on both
macOS and glibc but then behaves differently on read, and the JVM will not open a
directory as a stream at all. And `print` writes to the C-level `stdout`, so it
is *not* redirected by `io.output` — a script that assumes otherwise produces
two different wrong answers.

#### The `os.time` sweep: `lua_tointegerx` converts strings

The 854-line sweep covered every `os.date` conversion specifier. This one covers
the half it did not: `os.time` with tables (leap-year carry, month/day/hour out
of range, defaults, ignored extra fields), `difftime`, `getenv`, `remove` /
`rename`, `tmpname`, `execute`'s exit codes, `setlocale`.

**The fields of a date table are read with `lua_tointegerx`, which converts
strings.**

```c
  lua_Integer res = lua_tointegerx(L, -1, &isnum);   /* cvt2num: strings too */
```

So `os.time{year = "2000", month = 1, day = 1}` *is* the year 2000; we reported
`field 'year' is not an integer`. Library arguments go through the coercing
`luaL_check*` family, while the VM's arithmetic instructions deliberately do not
coerce — two different rules, easy to write as one.

The sweep also pins down that `month` and `day` are **mandatory** (`field 'month'
missing in date table`; `year` defaults and `hour` defaults to 12), that a field
too large for the `int` range reports `field 'x' is out-of-bound`, and that extra
fields (`yday`, `wday`, …) are ignored.

Two things are deliberately absent: extreme years such as `year = -100`, because
`mktime`'s representable range is `libc`-specific (macOS reports *"time result
cannot be represented in this installation"*), and removing a **directory**,
because `ENOTEMPTY` is 66 on macOS and 39 on glibc.

#### The weak-table / `__gc` / `<close>` sweep: `isdecGCmodegen`

This is the semantically riskiest corner of the whole project: **there is no
collector** (`lgc.c` is not ported), so weak tables are rebuilt on
`WeakReference` and `__gc` on `Cleaner`. It deserves more than twelve hand-written
tests. The sweep covers weak keys / values / both, keys that are still strongly
reachable, mutually-referencing weak values, values that are not collectable at
all (strings, numbers, booleans), every spelling of `__mode`, `__gc` on one and on
twenty objects, `__gc` raising, `__gc` added *after* `setmetatable` (which C
ignores — it only checks at that moment), and `<close>`'s ordering and error
argument.

It found one thing: **`collectgarbage("generational")` / `("incremental")` report
`isdecGCmodegen`, not `gckind`.**

```c
#define isdecGCmodegen(g)  (g->gckind == KGC_GEN || g->lastatomic != 0)
```

`lastatomic` is non-zero after the first real collection, and loading a script
allocates, so user code starts out looking *generational* even though `gckind` is
`KGC_INC`. Five consecutive switches in the reference:

```
1 generational   2 incremental   3 incremental   4 generational   5 incremental
```

Tracking only `gckind` gets the **first** one wrong. Both switches clear
`lastatomic`, so afterwards `gckind` alone decides.

Two things cannot be compared: `collectgarbage("count")` (nothing to count
without a collector) and `collectgarbage("step")`'s return value — a single step
in the reference never finishes a cycle, so it is always `false`; with no
incremental collector one step *is* a full round, so we return `true`.
Deliberately: returning `false` would make the `repeat until
collectgarbage("step")` idiom spin forever.

#### The `debug` × coroutine sweep: `_LOADED` lives in the registry

Both sides had been swept separately (114 and 99 lines), but not the
**cross-product**: `getinfo` / `getlocal` / `traceback` / `sethook` *on a
coroutine*, hook **counts** (printed as a number instead of one line per event —
the count itself is a strong check on the line table and the call sequence, and it
pins the `tail call` hook event), registry / `uservalue` / `setmetatable`, and the
rest of the coroutine state machine.

It found one thing: **`_LOADED` has to be a field of the registry.**

```c
#define LUA_LOADED_TABLE  "_LOADED"     /* lauxlib.h */
```

`package.loaded` is not stored somewhere else with a copy in the registry — it
*is* the registry field, so `debug.getregistry()["_LOADED"]` must return it. We
kept it in a `LuaState` field only, so `type(debug.getregistry()["_LOADED"])`
gave `nil` where the reference gives `table`. Now it is read back out of the
registry, so a script that swaps `_LOADED` swaps ours too.

#### The error sweep: two things about `__name`

208 lines on one axis that no earlier matrix had touched: `error(msg, level)` for
`level` from `-1` to `math.maxinteger`, error **objects** of every type (and what
`print` / `tostring` / `string.format("%s")` do to them), the `luaL_where`
position prefix in every syntactic position that can raise (table constructor,
numeric and generic `for`, all twelve metamethods, a `gsub` replacement, a `sort`
comparator, a coroutine, `<close>`, a method, an `__index` chain, a tail call, a
`load`ed chunk with `=` / `@` / string names), the shape of `xpcall`, errors
inside hooks, and chunks dumped with `strip`.

It found two things, both in `luaT_objtypename`:

```c
const char *luaT_objtypename (lua_State *L, const TValue *o) {
  Table *mt;
  if ((ttistable(o) && (mt = hvalue(o)->metatable) != NULL) ||
      (ttisfulluserdata(o) && (mt = uvalue(o)->metatable) != NULL)) {
    const TValue *name = luaH_getshortstr(mt, luaS_new(L, "__name"));
    if (ttisstring(name))  /* is '__name' a string? */
      return getstr(tsvalue(name));  /* use it as type name */
  }
  return ttypename(ttype(o));  /* else use standard type name */
}
```

**`__name` is a C string.** `getstr()` hands back a `const char *` and
`luaG_typeerror` interpolates it with `%s`, so it is cut at the first `\0`:
`__name = "A\0B"` reports `attempt to perform arithmetic on a A value`. We
returned the whole byte array. (The same rule was already applied on
`luaL_tolstring`'s path — this was the second copy of the bug.)

**Only tables and (full) userdata consult `__name`.** Everything else falls
straight to `ttypename`, *type metatable and all*: after
`debug.setmetatable(nil, {__name = "NilT"})`, `nil + 1` still reports
`... on a nil value`. Our lookup went through `luaT_gettmbyobj`, which also
returns the *type* metatable — so we reported `NilT`.

Four more things the sweep pinned down, none of which were bugs:

* **`debug.traceback` returns a non-string, non-`nil` message untouched**
  (`ldblib.c: db_traceback`), so `xpcall(f, debug.traceback)` with a table error
  object yields the *table*, not a traceback. And `lua_tostring` does **not**
  honour `__tostring` — which is exactly why `lua.c`'s `msghandler` has to call
  `luaL_callmeta(L, 1, "__tostring")` by hand.
* **The line hook fires before the first instruction of the line**, so
  `print(pcall(f))` written on the same line does *not* protect the hook's error;
  the call hook fires for `print` and `pcall` themselves.
* **`xpcall` drops the handler's second result**: `ldo.c: dothecall` calls it as
  `luaD_callnoyield(L, L->top - 2, 1)` — `nresults` is hard-coded to 1.
* `coroutine.wrap`'s error carries **two** position prefixes; `pcall(error, "x")`
  has **none** (a C function has no line); a library error **does** have one,
  because `luaL_error`'s `luaL_where(L, 1)` asks for the *caller*.

#### The NUL sweep: thirteen places C cuts a Lua string at `\0`

One axis, one rule: a Lua string is a byte string, but every C API that takes a
`const char *` — `strlen`, `%s`, `strstr`, `strchr`, `strcmp` — stops at the
first NUL. 88 lines, and it found thirteen divergences, all of them the same bug
in thirteen different doorways:

| cut at the first `\0` | chunk name (`luaS_new`), module name and `package.loaded`'s key, all four `searchpath` arguments, `io.open` / `io.lines` / `os.remove` / `os.rename` / `os.getenv` filename, `fopen`'s *mode*, `debug.getinfo`'s `what`, `debug.sethook`'s mask, `luaL_traceback`'s message, `luaL_checkoption` (`collectgarbage`), `string.pack`'s format, and `__name` |
| --- | --- |
| **not** cut | `string.format("%s")` (goes through `luaL_tolstring`, which carries the length), `error`'s message (`lua_concat`), `os.date` (it iterates `s + slen`), table keys, `gsub`, `upper` |
| **rejected** instead of cut | `%s` *with modifiers*, and `string.pack`'s `z` — `luaL_argcheck(L, l == strlen(s), arg, "string contains zeros")` |

Two of them are worth calling out.

**`os.remove("/x\0y")` crashed the whole process.** `Paths.get` throws
`InvalidPathException`, which is a `RuntimeException` — not a `LuaError`, so
`pcall` cannot catch it (only `LuaError` is) and it escapes all the way out.
Truncating to C-string semantics removes the case entirely; the `catch` now also
covers `RuntimeException` as a backstop.

**`__name` was the second copy.** `luaL_typeerror` and `luaG_typeerror` both go
through `luaT_objtypename`, but `typeArgError` had its own `metafieldOf(v,
"__name")` lookup — which got *both* rules wrong (no truncation, and no
table/userdata restriction). It now calls `LuaTm.objTypeName` like the other two.

The two `debug` ones are the subtlest: `debug.getinfo(f, "S\0ln")` must **not**
report `invalid option` (C stops scanning at the NUL, so it never sees `l` and
`n` at all), and `debug.sethook(f, "l\0c")` installs only the line hook while
`"c\0l"` installs only the call hook — the counts come out inverted.

#### Two sweeps that came back clean: coroutines, and numeric `for` with coercion

**Coroutines (99 lines):** which boundaries `yield` may cross, the semantics of
`close`, how errors propagate, the four states, and nested `resume`. The split,
measured and matching:

| may `yield` | `pcall` / `xpcall`, numeric and generic `for` (custom iterators too), `gmatch`, `__index` / `__newindex` / `__add` / `__lt` / `__concat` / `__len` / `__call` / `__close` |
| --- | --- |
| may not | a `table.sort` comparator, a `string.gsub` replacement function, `__tostring` inside `print` — `attempt to yield across a C-call boundary` |

It also pins down that a coroutine is **dead** (not suspended) after yielding
inside `__eq`, that closing yourself reports `cannot close a running coroutine`,
that `<close>` variables are closed in **reverse order of declaration**, and that
an error raised inside `__close` replaces the original error.

**Syntax and scope (121 lines):** sixty-odd fragments pushed through `load()`, so
**compile-time** errors are part of the comparison too — `goto` into a local's
scope, duplicate labels, assigning to a `<const>`, `break` outside a loop,
malformed numerals and escapes, the various shapes of function names. Nothing a
generated program will ever produce. The runtime half covers "the right-hand side
is evaluated first" in multiple assignment, table-constructor evaluation order and
key overwriting, varargs with `nil` mixed in, malformed uses of `:`, upvalue
sharing, `repeat`'s condition seeing locals declared in the body, and operator
precedence and associativity.

**`debug` and `utf8` (114 lines):** every `what` letter of `getinfo` alone and in
combination, out-of-range `getlocal` / `setlocal` / `getupvalue` / `setupvalue`,
all four `sethook` masks, the **shape** of `traceback` (not compared wholesale —
it contains chunk names and addresses), and the boundary cases of all five `utf8`
functions.

**Numeric `for` and coercion (551 lines):** all four `forprep` / `forloop`
branches (integer up/down, float up/down), including `maxinteger` bounds,
strings as limits, a zero step, and float steps; plus `//`, `%`, bitwise
operations and string coercion. Three things worth remembering: in
`for i = 0, 1, 0.5` the first value is an **integer** `0` (with `1.0` as the
limit it is float throughout); bitwise operators do **not** coerce strings
(`"10" // 2` is 5 but `"10" & 1` errors); and `for i = 2^53, 2^53+2` /
`for i = 1e300, 1e300` never advance under float precision — the probe has a
20000-iteration guard so both sides bail out at the same place.

### What is still missing

Nothing in the language or the ten standard libraries, and every upstream file
whose output is deterministic now matches byte for byte. The remaining work is
verification breadth: more differential scripts and a wider fuzz corpus. The only
upstream files still off the list are `all.lua` / `main.lua` (the driver: it needs
the `T` module built from `ltests.c` and writes `time.txt`), `attrib.lua` /
`files.lua` / `big.lua` (they need the C modules in `libs/` and the globals
`all.lua` sets up — the reference binary fails on them standalone too),
`heavy.lua` (minutes), and `bwcoercion.lua` (no output standalone).

---

## Migration from the old fakescript language

This is a **breaking rewrite**. The old fakescript syntax is gone entirely:
`package`, `include`, `struct`, `const`, `->`-style `for`, `array()`, `map()`,
`fake` coroutines, `:=`, the Java binding annotations, and the profiler.

Existing fakescript scripts must be rewritten as Lua — which is the point: after
this change, what runs is Lua 5.4, and scripts are portable to and from any other
Lua 5.4 implementation. Users who still need the old language should pin to the
last commit before the rewrite.

---

## Build

```bash
./mvnw clean test          # requires JDK 21+
```

With no `lua5.4` binary on the machine, the differential tests skip themselves
(a single `assumeTrue` guard in `Diff`) and the coverage gate is skipped with
them — the differential suites are most of the line coverage, so a gate that
runs while they are all skipped would only ever be red for the wrong reason.
Set `LUA54_BIN`, or build the reference into `target/lua54/src/lua`, to get the
real gate:

```bash
LUA54_BIN=/path/to/lua5.4 ./mvnw clean test
```

CI builds the reference from source and exports `LUA54_BIN` and `FKDUMP_BIN`, so
this only affects local builds without a reference binary.

---

## License

MIT
