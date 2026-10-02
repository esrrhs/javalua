# fakescript-java

[![License](https://img.shields.io/github/license/esrrhs/fakescript_java)](https://github.com/esrrhs/fakescript_java)
[![Language](https://img.shields.io/github/languages/top/esrrhs/fakescript_java)](https://github.com/esrrhs/fakescript_java)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.esrrhs/fakescript-java)](https://central.sonatype.com/artifact/com.github.esrrhs/fakescript-java)
[![Build Status](https://github.com/esrrhs/fakescript_java/actions/workflows/maven.yml/badge.svg?branch=master)](https://github.com/esrrhs/fakescript_java/actions)

轻量级纯 Java 编写的嵌入式脚本语言。

[English](./README.md)

---

## 项目简介

**fakescript-java** 是一款纯 Java 编写的轻量级嵌入式脚本语言。语法设计借鉴了 Lua、Go 和 Erlang。底层基于 JFlex 与 Bison 生成语法树，将其编译为高效的字节码并由内置虚拟机解释器执行。

## 核心特性

* **简洁易学**：类似 Lua 的清晰语法结构。
* **纯函数式模型**：一切皆函数，支持函数多返回值。
* **丰富容器支持**：内置动态数组（`array()`）与哈希映射（`map()`、`_G()`），支持任意嵌套。
* **轻量级协程机制**：支持使用 `fake func(args)` 产生协程执行单元，结合 `sleep` / `yield` 实现非阻塞协同多任务。
* **无缝 Java 双向互操作**：支持使用 `@fakescript` 注解直接反射绑定 Java 静态方法与类成员函数。
* **结构体支持**：支持轻量级自定义数据结构体（`struct`）。
* **模块化与命名空间**：支持 `package` 与 `include` 实现代码模块化与包隔离。
* **丰富数据类型**：支持 Int64（`UUID`）、`const` 常量定义、浮点数以及字符串。
* **内置性能分析器**：内置 Profiler，可采集并输出脚本各函数执行时间。
* **支持热重载**：支持在宿主运行时动态解析并热更新脚本逻辑。

---

## 脚本语法示例

```fakescript
-- 定义包名
package mypackage.test

-- 引入依赖脚本
include "common.fk"

-- 结构体定义
struct User
    id
    name
    extra
end

-- 常量定义
const MAX_COUNT = 100
const DEFAULT_NAME = "Guest"
const CONFIG_MAP = {1 : "Alpha" 2 : "Beta"}

-- 函数定义
func process_user(arg1, arg2)

    -- 调用已绑定的 Java 静态函数
    var sum = MathUtils.add(arg1, 10)

    -- 分支控制
    if arg1 < arg2 then
        -- 启动一个轻量级协程
        fake background_task(arg1, arg2)
    elseif arg1 == arg2 then
        print("两值相等")
    else
        print("arg1 大于 arg2")
    end

    -- For 循环
    for var i = 0, i < arg2, i++ then
        print("当前循环索引: ", i)
    end

    -- 动态数组
    var arr = array()
    arr[0] = 100
    arr[1] = 200

    -- 动态哈希 Map
    var m = map()
    m["key"] = "value"
    m[1] = arr

    -- 结构体实例化与访问
    var u = User()
    u->id = 1001
    u->name = "Alice"

    -- Switch 语句
    switch arg1
        case 1 then
            print("分支 1")
        case "a" then
            print("分支 a")
        default
            print("默认分支")
    end

    -- 返回多个值
    return sum, m["key"]
end
```

---

## Java 嵌入示例

### 1. 声明并绑定 Java 方法
通过 `@fakescript` 标记供脚本调用的静态方法：

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

### 2. 在 Java 中执行脚本

```java
import com.github.esrrhs.fakescript.fake;
import com.github.esrrhs.fakescript.fk;
import com.github.esrrhs.fakescript.fkconfig;

public class Main {
    public static void main(String[] args) throws Exception {
        // 1. 初始化 fake 实例
        fake f = fk.newfake(new fkconfig());
        fk.openbaselib(f);

        // 2. 注册 Java 类或包
        fk.regclass(f, MathUtils.class);

        // 3. 解析脚本字符串或脚本文件
        String script = 
                "func calc(x, y)\n" +
                "    return MathUtils.add(x, y) * 2\n" +
                "end\n";
        fk.parsestr(f, script);

        // 4. 执行函数并获取结果
        Object result = fk.run(f, "calc", 10, 20);
        System.out.println("Result: " + result); // 输出: 60.0
    }
}
```

---

## 引入依赖

### Maven

在项目的 `pom.xml` 中添加依赖：

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

## 运行时安全与控制

嵌入脚本时，可以通过 `fkconfig` 限制失控脚本，并用 `fk.stop` 取消执行：

```java
fkconfig config = new fkconfig();
config.max_run_cmd_num = 1000000;    // 单次run总命令数上限,0表示不限制
config.run_timeout_ms = 1000;        // 单次run墙钟时间上限(毫秒),0表示不限制
config.container_max_size = 1000000; // 单个容器(array/map)元素个数上限,0表示不限制

fake f = fk.newfake(config);
fk.openbaselib(f);
fk.parsestr(f, script);
Object ret = fk.run(f, "funcname", args);         // 返回第一个返回值
Object[] rets = fk.runmulti(f, "funcname", args); // 返回全部返回值

fk.stop(f); // 在下一条命令边界取消当前run
```

运行时错误会在fake上记录错误标记;`fk.geterror(f)` 返回错误信息、Java堆栈以及脚本级调用栈。

---

## 源码构建

使用自带的 Maven Wrapper 即可进行构建与测试：

```bash
./mvnw clean test
./mvnw package
```

其他构建profile：

```bash
./mvnw verify -Pquality -DskipTests   # SpotBugs静态分析报告
./mvnw clean test -Pgrammar           # 重新生成YYParser.java(需要bison >= 3.0)
```

词法分析器(`Yylex.java`)由JFlex Maven插件在每次构建时从 `jflexbison/jflex.flex` 自动生成。语法分析器(`YYParser.java`)提交在仓库中，语法变更后使用 `grammar` profile(或 `build.sh`)重新生成。每次 `test` 构建会在 `target/site/jacoco/` 下生成测试覆盖率报告。
