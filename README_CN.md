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

## 性能剖析

内置按函数统计的profiler:

```java
fk.openprofile(f);
fk.run(f, "main");
fk.closeprofile(f);
System.out.println(fk.dumpprofile(f)); // 每个函数的调用次数、总耗时与单次耗时
```

---

## 数值类型

数值分两类:

* **INT** —— 64位有符号整数。整数字面量(`42`)与纯整数运算(`+`、`-`、`*`、`%`、比较)产生INT,全程64位精度(2^53以上不再有double舍入误差),溢出与C一致按位回绕。
* **REAL** —— 64位双精度浮点。浮点字面量(`4.2`)以及任何INT与REAL混合的表达式提升为REAL。

需要注意的语义:

* 除法恒为浮点:`4 / 2` 得到 `2.0`,`1 / 2` 得到 `0.5`。
* 相等判断跨数值类型按值比较:`1 == 1.0` 为真,二者作为map键也是同一个键。
* `UUID`值(整数加`u`后缀,如`123u`)不参与任何算术运算。
* 宿主侧:`Integer`/`Double`/`Float`参数映射为REAL,`Long`映射为UUID(历史约定),脚本INT返回值在Java侧为`Long`。

---

## 运行时安全与控制

嵌入脚本时，可以通过 `fkconfig` 限制失控脚本，并用 `fk.stop` 取消执行：

```java
fkconfig config = new fkconfig();
config.max_run_cmd_num = 1000000;    // 单次run总命令数上限,0表示不限制
config.run_timeout_ms = 1000;        // 单次run墙钟时间上限(毫秒),0表示不限制
config.container_max_size = 1000000; // 单个容器(array/map)元素个数上限,0表示不限制
config.new_class_white_list = new String[] { "com.example.script." }; // 沙箱:内置new()允许实例化的类名前缀,null表示不限制

fake f = fk.newfake(config);
fk.openbaselib(f);
fk.parsestr(f, script);
Object ret = fk.run(f, "funcname", args);         // 返回第一个返回值
Object[] rets = fk.runmulti(f, "funcname", args); // 返回全部返回值

fk.stop(f); // 在下一条命令边界取消当前run
```

运行时错误会在fake上记录错误标记;`fk.geterror(f)` 返回错误信息、Java堆栈以及脚本级调用栈。

## 协程与分帧执行

脚本内用 `fake func(args)` 启动协程，用 `sleep`/`yield` 挂起。宿主有两种驱动方式：

```java
// 方式一:run()阻塞到入口函数及其所有协程结束
Object ret = fk.run(f, "main");

// 方式二:resume()每次最多执行per_frame_cmd_num条命令后返回,
// 未结束返回null,适合游戏/框架主循环按帧驱动
Object[] rets = fk.resume(f, "main"); // 每帧调用一次,直到返回非null
```

`resume` 在所有协程结束前返回 `null`，结束后返回完整的返回值数组。结束后再用新的函数名调用 `resume` 可以启动新一轮执行。

注意:`fake` 实例(及从它获取的对象)**不是线程安全的**——请在单一线程中驱动。

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
