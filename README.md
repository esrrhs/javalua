# javalua

[![License](https://img.shields.io/github/license/esrrhs/javalua)](https://github.com/esrrhs/javalua)
[![Language](https://img.shields.io/github/languages/top/esrrhs/javalua)](https://github.com/esrrhs/javalua)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.esrrhs/javalua)](https://central.sonatype.com/artifact/com.github.esrrhs/javalua)
[![Build Status](https://github.com/esrrhs/javalua/actions/workflows/maven.yml/badge.svg?branch=master)](https://github.com/esrrhs/javalua/actions)

**Lua 5.4 in pure Java. The JVM handles the garbage collection.**

[简体中文](./README_CN.md)

javalua is a module-by-module port of the Lua 5.4 reference implementation to
Java. Existing Lua 5.4 programs run unchanged: the full language, the complete
standard library, binary chunks, debug hooks, and native C modules.

## Features

- **Full Lua 5.4 semantics** — hand-written lexer, parser and code generator,
  plus a register VM ported from `lvm.c`. `goto`, varargs, multiple returns,
  integer/float subtypes, and every metamethod including `__close`, `__gc` and
  `__mode`.
- **Complete standard library** — `base`, `coroutine`, `package`, `string`
  (including `pack`/`unpack`), `table`, `math`, `io`, `os`, `utf8`, and
  `debug` with hooks.
- **Memory by the JVM** — `lgc.c` is not ported. Weak tables use
  `WeakReference`/`ReferenceQueue`, `__gc` uses a resurrection sentinel, and
  string interning uses a weak pool. `collectgarbage` keeps Lua's observable
  bookkeeping but delegates collection to the JVM.
- **No threads for coroutines** — a coroutine is a separate Lua stack, and
  `coroutine.yield` unwinds through a `LuaYield` exception, the analogue of
  C's `longjmp`. Yielding through `pcall`, metamethods and iterators works;
  yielding across a C-call boundary raises the same error as the reference.
- **Binary chunks** — `load`, `loadfile`, `dofile`, `require` and
  `string.dump` read and write standard Lua 5.4 bytecode.
- **C API** — `luashim`, a small JNI library, exports the `lua_*` / `luaL_*`
  surface, so real C modules built for Lua 5.4 (lpeg, cjson, lfs, luabitop,
  mime, luaposix, …) load through `package.loadlib` and `require`. It also
  gives the runtime the few libc calls Java has no equivalent for: `setlocale`
  (and with it the locale radix character that `tonumber`, `tostring`,
  `string.format` and `io.read("n")` all go through), `waitpid`, `FILE*`.
- **Java 21**, a single jar, no runtime dependencies.

## Quick start

```xml
<dependency>
  <groupId>com.github.esrrhs</groupId>
  <artifactId>javalua</artifactId>
  <version>1.0.0</version>
</dependency>
```

```java
import com.github.esrrhs.javalua.LuaLibs;
import com.github.esrrhs.javalua.LuaState;
import com.github.esrrhs.javalua.LuaValue;
import java.nio.file.Files;
import java.nio.file.Path;

LuaState L = new LuaState();
LuaLibs.openAll(L);

L.run("print('hello from Lua')");

LuaValue err = L.runProtected(
        Files.readAllBytes(Path.of("script.lua")), "@script.lua");
if (err != null) {
    System.err.println(err);
}
L.close();
```

`run` throws `LuaError` on failure; `runProtected` returns the error value
instead. Each `LuaState` owns its globals, registry and coroutines, so
independent states can be used side by side.

## C modules

`native/build.sh` compiles `native/luashim.c` into
`src/main/resources/native/<os>-<arch>/`, where `NativeBridge` finds and loads
it automatically. Building needs a C compiler and the JDK headers:

```bash
sh native/build.sh
```

A custom build can be selected at startup with
`-Djavalua.luashim=/path/to/libluashim.so`. With the shim loaded,
`require("lpeg")` and `package.loadlib(path, "luaopen_x")` behave as in C Lua.
Without it, `package.loadlib` reports
`dynamic libraries not enabled; check your Lua installation`.

The published jar does not bundle a shim (`src/main/resources/native/` is
git-ignored and built per machine), so running it out of the box gives you the
pure-Java paths: `io` reads and writes through `RandomAccessFile`, and only C
module loading is missing. Force either path for testing with
`-Djavalua.nonative=true`, which pretends the shim failed to load.

## Verification

Correctness means agreeing with a real Lua 5.4 binary, not looking similar.
Four gates run against a reference build:

1. **Upstream suite** — Lua's own `lua-5.4.7-tests` run unmodified.
2. **Differential tests** — the same script runs under both implementations
   and stdout must match byte for byte. This covers language semantics, the
   standard library, error messages, tracebacks and bytecode disassembly.
3. **Differential fuzzing** — deterministic generated programs (including
   coroutine and precompiled-chunk modes) are compared against the reference.
   Seed counts are configurable with `-Dfuzz.seeds` and `-Dfuzz.coSeeds`.
4. **Coverage** — JaCoCo line coverage, measured over both `io` paths
   together, must stay at or above 95%.

CI has three jobs: `Build (JDK 21)`, `Conformance (against lua5.4)`, and
`Static analysis`. The conformance job builds Lua 5.4.7, the `fkdump` bytecode
dumper and `luashim` from source, generates a comma-decimal locale (`pt_BR`,
otherwise the locale tests would pass while proving nothing), then runs the
full test suite twice — once with the shim, once with `-Djavalua.nonative=true`
so the pure-Java `io` path is diffed against the reference too. Each run is
followed by `tools/check_ci_gates.py`, which fails when a gate class executed
zero tests; the two runs append into one `jacoco.exec`, and the 95% line is
checked against that merged file.

### Known divergences

Intentional / unfixable platform notes (everything else is expected to match
Lua 5.4.7):

- **GC**: memory is owned by the JVM (`lgc.c` is not ported). Weak tables,
  `__gc`, and `collectgarbage` keep Lua-observable shapes where possible, but
  `collectgarbage("count")` reports JVM heap usage (absolute values differ;
  relative changes are still meaningful). Upstream `gc.lua` / collector-param
  tests are not differential gates.
- Transcendental `math` functions can differ from the platform libc by a few
  ulp (Java `Math` / fdlibm vs the reference's libc).
- `%p` and the default `tostring` of userdata/threads print Java identities,
  not C addresses.
- **NaN sign in text**: C99 leaves the sign of a NaN unspecified. glibc often
  prints `-nan` for the default x86 NaN (sign bit set); this port always prints
  `nan` / `+nan` under `%+`. Differential tests normalize both sides.
- With the shim, `os.setlocale` really switches libc's locale, which is
  **per-process**: every `LuaState` in the same JVM shares it, while C Lua has
  one state per process. The radix character that `tonumber`, `tostring`,
  `string.format`, `io.write` and `io.read("n")` use follows it
  (`lua_getlocaledecpoint()`), and `os.date` locale names (`%A`, `%B`, `%c`,
  …) follow it too via libc `strftime`.
- Without the shim, `os.execute` / `io.popen:close` can only report
  `("exit", 128+n)` for a signal-killed process (e.g. undrained close →
  `("exit", 141)` instead of `("signal", 13)`), `os.setlocale` only accepts
  `nil`/`"C"`/`"POSIX"`, the radix character is always `.`, and `os.date`
  name conversions stay on the English tables.
- With the shim, `io.popen` undrained-close while the child is still writing
  (`sleep; echo`, large `printf`) agrees with C on `signal 13`. A tiny
  `echo` that already finished can still yield `exit 0` on **both** sides —
  a shared timing window. Absolute `os.clock` values still differ from a
  fresh `lua` process because of JVM startup CPU; the function measures
  process CPU time like C's `clock()`.

## Build and test

```bash
./mvnw test       # differential tests skip when no Lua 5.4 binary is found
./mvnw package
```

Point the tests at a reference binary to enable all gates:

```bash
LUA54_BIN=/path/to/lua5.4 ./mvnw test
```

Discovery order is `LUA54_BIN`, then `target/lua54/src/lua`, then `lua5.4` or
`lua` on `PATH` (the version is checked to be 5.4). The differential harness
writes its scratch files under `target/rt/<pid>/` — one directory per JVM — so
two `./mvnw test` runs at the same time cannot read each other's case file.
Optional variables:

| Variable | Purpose |
|---|---|
| `LUA54_BIN` | reference interpreter |
| `LUAC54_BIN` | reference `luac` |
| `FKDUMP_BIN` | bytecode dumper built against the reference |
| `LUA54_TESTS` | unpacked upstream test suite |
| `LUA54_LIB` | reference `liblua.a` for the C API symbol sweep; otherwise tries `target/lua54/src/liblua.a`, then `/tmp/lua-5.4.7/src/liblua.a` |

Building a reference locally:

```bash
curl -LO https://www.lua.org/ftp/lua-5.4.7.tar.gz
tar xzf lua-5.4.7.tar.gz
make -C lua-5.4.7 linux
LUA54_BIN=$PWD/lua-5.4.7/src/lua ./mvnw test
```

## Project layout

| Path | Contents |
|---|---|
| `src/main/java/com/github/esrrhs/javalua/` | lexer, parser, code generator, VM, state, metamethods, standard library |
| `native/` | `luashim.c`, generated C API stubs, build script |
| `src/test/java/...` | unit, differential, fuzz, and native-extension tests |
| `benchmark/` | local ballpark timing; not a CI gate (see its README) |
| `tools/` | `check_ci_gates.py` — fails when a gate class silently ran zero tests; `local_conformance.sh` — mirrors the CI conformance job locally (both `io` paths, merged coverage) |

The Java classes mirror the C sources: `Lexer` ↔ `llex.c`, `Parser` ↔
`lparser.c`, `Codegen` ↔ `lcode.c`, `Vm` ↔ `lvm.c`, `LuaState` ↔
`lstate.c`/`ldo.c`, `Api` ↔ `lapi.c`, `LuaAux` ↔ `lauxlib.c`, and one class per
library file.

## License

MIT
