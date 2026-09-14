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
    <version>1.0.14</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.github.esrrhs:fakescript-java:1.0.14'
```

---

## Building from Source

Build and run tests using the included Maven Wrapper:

```bash
./mvnw clean test
./mvnw package
```
