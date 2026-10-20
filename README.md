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
  mime, luaposix, …) load through `package.loadlib` and `require`.
- **Java 21**, a single jar, no runtime dependencies.

## Quick start

```xml
<dependency>
  <groupId>com.github.esrrhs</groupId>
  <artifactId>javalua</artifactId>
  <version>1.0.16</version>
</dependency>
```

```java
import com.github.esrrhs.fakescript.lua.LuaLibs;
import com.github.esrrhs.fakescript.lua.LuaState;
import com.github.esrrhs.fakescript.lua.LuaValue;
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
`-Dfakescript.luashim=/path/to/libluashim.so`. With the shim loaded,
`require("lpeg")` and `package.loadlib(path, "luaopen_x")` behave as in C Lua.
Without it, pure-Lua code is unaffected and `package.loadlib` reports
`dynamic libraries not enabled; check your Lua installation`.

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
4. **Coverage** — JaCoCo line coverage must stay at or above 95%.

CI has three jobs: `Build (JDK 21)`, `Conformance (against lua5.4)`, and
`Static analysis`. The conformance job builds Lua 5.4.7, the `fkdump` bytecode
dumper and `luashim` from source, then runs the full test suite.

### Known divergences

- Transcendental `math` functions can differ from the platform libc by a few
  ulp.
- `%p` and the default `tostring` of userdata/threads print Java identities,
  not C addresses.
- `collectgarbage("count")` reports JVM heap usage, so absolute values differ
  from the reference (relative changes are still meaningful).
- `io.popen` has a rare race between the pump thread and `close`.

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
`lua` on `PATH` (the version is checked to be 5.4). Optional variables:

| Variable | Purpose |
|---|---|
| `LUA54_BIN` | reference interpreter |
| `LUAC54_BIN` | reference `luac` |
| `FKDUMP_BIN` | bytecode dumper built against the reference |
| `LUA54_TESTS` | unpacked upstream test suite |

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
| `src/main/java/com/github/esrrhs/fakescript/lua/` | lexer, parser, code generator, VM, state, metamethods, standard library |
| `native/` | `luashim.c`, generated C API stubs, build script |
| `src/test/java/...` | unit, differential, fuzz, and native-extension tests |
| `benchmark/` | standalone Lua benchmark scripts |

The Java classes mirror the C sources: `Lexer` ↔ `llex.c`, `Parser` ↔
`lparser.c`, `Codegen` ↔ `lcode.c`, `Vm` ↔ `lvm.c`, `LuaState` ↔
`lstate.c`/`ldo.c`, `Api` ↔ `lapi.c`, `LuaAux` ↔ `lauxlib.c`, and one class per
library file.

## License

MIT
