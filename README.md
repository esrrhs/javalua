# fakescript-java

[![License](https://img.shields.io/github/license/esrrhs/fakescript_java)](https://github.com/esrrhs/fakescript_java)
[![Language](https://img.shields.io/github/languages/top/esrrhs/fakescript_java)](https://github.com/esrrhs/fakescript_java)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.esrrhs/fakescript-java)](https://central.sonatype.com/artifact/com.github.esrrhs/fakescript-java)
[![Build Status](https://github.com/esrrhs/fakescript_java/actions/workflows/maven.yml/badge.svg?branch=master)](https://github.com/esrrhs/fakescript_java/actions)

A lightweight embedded scripting language written in pure Java.

[简体中文](./README_CN.md)

---

## Overview

**fakescript-java** is a lightweight, embeddable scripting language implemented in Java. Its syntax draws inspiration from Lua, Go, and Erlang. The compiler utilizes JFlex and Bison to construct syntax trees, compiles source code into bytecode, and executes it via an internal virtual machine interpreter.

## Key Features

* **Clean Syntax**: Lua-inspired, concise, and clean grammar.
* **Pure Functional**: Everything is modeled as functions; supports multiple return values.
* **Rich Containers**: Built-in dynamic arrays (`array()`) and maps (`map()`, `_G()`) with arbitrary nesting.
* **Coroutines / Routines**: Lightweight cooperative concurrency using `fake func(args)` and `sleep` / `yield`.
* **Seamless Java Interop**: Direct, zero-boilerplate reflection binding for Java static methods and class member functions via `@fakescript` annotation.
* **Struct Support**: Lightweight user-defined data structures (`struct`).
* **Package & Modular System**: Modular namespace separation using `package` and `include`.
* **Types & Constants**: Built-in support for Int64 (`UUID`), `const` values, floating-point numbers, and strings.
* **Built-in Profiler**: Integrated performance profiling to measure execution time per script function.
* **Hot Reloading**: Supports dynamic parsing, script reloading, and bytecode updates at runtime.

---

## FakeScript Syntax Example

```fakescript
-- Define package namespace
package mypackage.test

-- Include external script files
include "common.fk"

-- Struct definition
struct User
    id
    name
    extra
end

-- Constants
const MAX_COUNT = 100
const DEFAULT_NAME = "Guest"
const CONFIG_MAP = {1 : "Alpha" 2 : "Beta"}

-- Function definition
func process_user(arg1, arg2)

    -- Calling bound Java static functions
    var sum = MathUtils.add(arg1, 10)

    -- Conditional statements
    if arg1 < arg2 then
        -- Spawn a lightweight coroutine
        fake background_task(arg1, arg2)
    elseif arg1 == arg2 then
        print("Values are equal")
    else
        print("arg1 is greater than arg2")
    end

    -- For loop
    for var i = 0, i < arg2, i++ then
        print("Loop index: ", i)
    end

    -- Dynamic Array
    var arr = array()
    arr[0] = 100
    arr[1] = 200

    -- Dynamic Map
    var m = map()
    m["key"] = "value"
    m[1] = arr

    -- Struct usage
    var u = User()
    u->id = 1001
    u->name = "Alice"

    -- Switch case
    switch arg1
        case 1 then
            print("case 1")
        case "a" then
            print("case a")
        default
            print("default")
    end

    -- Return multiple values
    return sum, m["key"]
end
```

---

## Java Integration Example

### 1. Bind Java Methods
Annotate methods with `@fakescript`:

```java
package com.example;

import com.github.esrrhs.fakescript.fakescript;

public class MathUtils {
    @fakescript
    public static int add(int a, int b) {
        return a + b;
    }
}
```

### 2. Execute Script in Java

```java
import com.github.esrrhs.fakescript.fake;
import com.github.esrrhs.fakescript.fk;
import com.github.esrrhs.fakescript.fkconfig;

public class Main {
    public static void main(String[] args) throws Exception {
        // 1. Initialize environment
        fake f = fk.newfake(new fkconfig());
        fk.openbaselib(f);

        // 2. Register Java classes or packages
        fk.regclass(f, MathUtils.class);

        // 3. Parse script code or file
        String script = 
                "func calc(x, y)\n" +
                "    return MathUtils.add(x, y) * 2\n" +
                "end\n";
        fk.parsestr(f, script);

        // 4. Run script function
        Object result = fk.run(f, "calc", 10, 20);
        System.out.println("Result: " + result); // Output: 60.0
    }
}
```

---

## Installation

### Maven

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.github.esrrhs</groupId>
    <artifactId>fakescript-java</artifactId>
    <version>1.0.16</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.github.esrrhs:fakescript-java:1.0.16'
```

---

## Profiling

Built-in per-function profiler:

```java
fk.openprofile(f);
fk.run(f, "main");
fk.closeprofile(f);
System.out.println(fk.dumpprofile(f)); // calls, total & per-call time per function
```

---

## Language Notes

* String concatenation uses `..` (Lua style): `"key" .. 1`. The `+` operator is arithmetic only.
* Multi-return declaration assignment is `a, b := f()`; `a, b = f()` assigns to pre-declared variables.
* Sleeping coroutines do not busy-wait: `fk.run` sleeps until the nearest wake-up point.

## Standard Library

Built-in functions available to scripts:

* **Values**: `print`, `format`, `typeof`, `tonumber`, `tostring`, `tolong`, `size`, `range`, `getconst`, `isfunc`
* **Containers**: `array`, `map`, `_G`, `new` (subject to `new_class_white_list`)
* **Array ops**: `push`, `pop`, `insert`, `remove`, `sort` (in-place; numbers by value, strings lexicographic, mixing fails)
* **Map ops**: `keys`, `values` (iteration order is HashMap order, not insertion order)
* **Data**: `copy` (deep copy, 64-level cap), `tojson`, `fromjson` — JSON numbers round-trip as INT/REAL, UUID as integer, map keys must be string/number
* **Math & time**: `abs` (INT-preserving), `floor`, `ceil` (return INT), `sqrt`, `pow`, `random()` (REAL in [0,1)) / `random(n)` (INT in [0,n)), `time` (epoch millis)
* **Strings**: `substr(s, start, len)`, `find(s, sub)` (0-based, -1 if absent), `upper`, `lower`, `trim`, `replace` (literal), `split` (literal separator, keeps empty parts)
* **Dynamic**: `dostring`, `dofile` (subject to `allow_dofile`), `getcurfile`, `getcurline`, `getcurfunc`, `getcurcallstack`, `dumpfunc`, `dumpallfunc`

---

## Numeric Types

Numbers come in two flavors:

* **INT** — signed 64-bit integers. Integer literals (`42`) and integer-only arithmetic (`+`, `-`, `*`, `%`, comparisons) produce INT, with full 64-bit precision (no double rounding above 2^53) and C-style overflow wrap.
* **REAL** — 64-bit doubles. Float literals (`4.2`) and any expression mixing INT and REAL promote to REAL.

Semantics worth knowing:

* Division is always floating point: `4 / 2` is `2.0`, `1 / 2` is `0.5`.
* Equality is value-based across numeric types: `1 == 1.0` is true, and both act as the same map key.
* `UUID` values (integer literals with a `u` suffix, e.g. `123u`) never participate in arithmetic.
* Host-side: `Integer`/`Short`/`Byte` arguments become INT, `Float`/`Double` become REAL, and `Long` becomes UUID (legacy convention) — set `fkconfig.long_as_int = true` to map `Long` to INT instead. Script INT results are returned to Java as `Long`.

---

## Runtime Safety & Control

When embedding scripts, use `fkconfig` to cap runaway scripts and `fk.stop` to cancel execution:

```java
fkconfig config = new fkconfig();
config.max_run_cmd_num = 1000000;  // max total commands per run; default 100M (dead-loop backstop), 0 = unlimited
config.run_timeout_ms = 1000;      // wall-clock limit per run (ms), 0 (default) = unlimited
config.container_max_size = 1000000; // max elements per array/map; default 10M, 0 = unlimited
config.new_class_white_list = new String[] { "com.example.script." }; // sandbox: classes new() may instantiate, null = unlimited

fake f = fk.newfake(config);
fk.openbaselib(f);
fk.parsestr(f, script);
Object ret = fk.run(f, "funcname", args);       // returns the first return value
Object[] rets = fk.runmulti(f, "funcname", args); // returns ALL return values

fk.stop(f); // cancels the current run at the next command boundary
```

Runtime errors set the error flag on the fake; `fk.geterror(f)` returns the message, the Java stack trace, and the script-level call stack.

## Coroutines & Frame-Sliced Execution

Scripts spawn coroutines with `fake func(args)` and can `sleep` / `yield`. Two hosting styles:

```java
// Style 1: run() blocks until the entry function and all its coroutines finish
Object ret = fk.run(f, "main");

// Style 2: resume() executes at most per_frame_cmd_num commands per call,
// returning null while unfinished - drive it from your game/framework loop
Object[] rets = fk.resume(f, "main"); // call once per frame until non-null
```

`resume` returns `null` while any coroutine is still alive, and the full return-value array once finished. After it finishes, calling `resume` again with a new function name starts a fresh run.

Note: a `fake` instance (and the objects obtained from it) is **not thread-safe** — drive it from a single thread. Concurrent access from two threads is detected at runtime and rejected with a `fake is busy` error instead of corrupting state (the error flag is last-writer-wins under such misuse); sequential use across different threads is fine.

---

## Building from Source

Build and run tests using the included Maven Wrapper:

```bash
./mvnw clean test
./mvnw package
```

Additional build profiles:

```bash
./mvnw verify -Pquality -DskipTests   # SpotBugs static analysis report
./mvnw clean test -Pgrammar           # regenerate YYParser.java (requires bison >= 3.0)
```

A micro benchmark (trend observation, not JMH-precise):

```bash
./mvnw -q -Pbench test-compile exec:java -Dexec.classpathScope=test -Dexec.mainClass=com.github.esrrhs.fakescript.Benchmark
```

The lexer (`Yylex.java`) is regenerated from `jflexbison/jflex.flex` on every build via the JFlex Maven plugin. The parser (`YYParser.java`) is checked in; regenerate it with the `grammar` profile when the grammar changes. Test coverage reports are generated under `target/site/jacoco/` on every `test` run.
