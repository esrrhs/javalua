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

**Phase 5 — coroutines.** The previous fakescript language implementation has been
removed from `master`; see *Migration* below. Phases 0 through 5 are in: the
compiler produces bytecode that is **field-for-field identical** to a real
`lua5.4`'s, and the VM executes it — **stdout matches a real `lua5.4` byte for
byte** across 158 + 121 + 60 differential scripts and 2100 generated programs, and
error messages match line for line.

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

Next: Phase 6, collection-dependent semantics — weak tables, `__gc`, and a weak
string pool.

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

---

## License

MIT
