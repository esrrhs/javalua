# javalua

[![License](https://img.shields.io/github/license/esrrhs/javalua)](https://github.com/esrrhs/javalua)
[![Language](https://img.shields.io/github/languages/top/esrrhs/javalua)](https://github.com/esrrhs/javalua)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.esrrhs/javalua)](https://central.sonatype.com/artifact/com.github.esrrhs/javalua)
[![Build Status](https://github.com/esrrhs/javalua/actions/workflows/maven.yml/badge.svg?branch=master)](https://github.com/esrrhs/javalua/actions)

**纯 Java 实现的 Lua 5.4，垃圾回收交给 JVM。**

[English](./README.md)

javalua 是对 Lua 5.4 参考实现的逐模块移植。现有 Lua 5.4 程序可以原样运行：
完整语言语义、完整标准库、二进制 chunk、调试钩子，以及原生 C 模块。

## 特性

- **完整的 Lua 5.4 语义** —— 手写词法分析器、语法分析器和代码生成器，以及
  从 `lvm.c` 移植的寄存器虚拟机。支持 `goto`、变长参数、多返回值、整数/浮点
  子类型，以及包括 `__close`、`__gc`、`__mode` 在内的全部元方法。
- **完整的标准库** —— `base`、`coroutine`、`package`、`string`（含
  `pack`/`unpack`）、`table`、`math`、`io`、`os`、`utf8`，以及带钩子的
  `debug`。
- **内存交给 JVM** —— 不移植 `lgc.c`。弱表基于
  `WeakReference`/`ReferenceQueue`，`__gc` 用可复活的哨兵对象实现，字符串
  驻留使用弱引用池。`collectgarbage` 保留 Lua 的可观测行为，但实际回收交给
  JVM。
- **协程不是线程** —— 每个协程是一条独立的 Lua 栈，`coroutine.yield` 通过
  `LuaYield` 异常展开调用栈，等价于 C 的 `longjmp`。可以穿过 `pcall`、元方法和
  迭代器 yield；跨 C 调用边界 yield 会像参考实现一样报错。
- **二进制 chunk** —— `load`、`loadfile`、`dofile`、`require` 和
  `string.dump` 读写标准 Lua 5.4 字节码。
- **C API** —— `luashim` 是一个小型 JNI 库，导出 `lua_*` / `luaL_*` 接口，
  因此按 Lua 5.4 编译的真实 C 模块（lpeg、cjson、lfs、luabitop、mime、
  luaposix 等）可以通过 `package.loadlib` 和 `require` 加载。它还补上 Java
  没有对应物的那几个 libc 调用：`setlocale`（连带 `tonumber`、`tostring`、
  `string.format`、`io.read("n")` 都要走的那个 locale 小数点）、`waitpid`、
  `FILE*`。
- **需要 Java 21**，单个 jar，无运行时依赖。

## 快速开始

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

失败时 `run` 抛出 `LuaError`，`runProtected` 则返回错误值。每个 `LuaState`
拥有自己的全局表、注册表和协程，多个实例可以并行使用。

## C 模块

`native/build.sh` 把 `native/luashim.c` 编译到
`src/main/resources/native/<os>-<arch>/`，`NativeBridge` 会自动找到并加载。
编译需要 C 编译器和 JDK 头文件：

```bash
sh native/build.sh
```

也可以在启动时用 `-Djavalua.luashim=/path/to/libluashim.so` 指定自定义
构建。加载 shim 后，`require("lpeg")` 和
`package.loadlib(path, "luaopen_x")` 的行为与 C Lua 一致。没有 shim 时，
`package.loadlib` 会返回
`dynamic libraries not enabled; check your Lua installation`。

发布到 Maven Central 的 jar 不带 shim（`src/main/resources/native/` 被
gitignore，按机器现编），开箱用到的是纯 Java 那条路：`io` 走
`RandomAccessFile` 读写，缺的只是 C 模块加载。测试时可以两条路各自强制：
`-Djavalua.nonative=true` 假装 shim 装不起来。

## 正确性验证

正确性的标准是与真实的 Lua 5.4 二进制一致，而不是「看起来像」。针对参考
实现有四道闸门：

1. **上游测试套件** —— Lua 官方的 `lua-5.4.7-tests` 原样运行。
2. **差分测试** —— 同一份脚本在两边执行，stdout 必须逐字节相同。覆盖语言
   语义、标准库、错误消息、traceback 和字节码反汇编。
3. **差分 fuzz** —— 用确定性种子生成程序（包括协程模式和预编译 chunk
   模式），与参考实现对比。种子数量可通过 `-Dfuzz.seeds` 和
   `-Dfuzz.coSeeds` 调整。
4. **覆盖率** —— JaCoCo 行覆盖率，按<b>两条 `io` 路径合并</b>后的数据统计，
   不得低于 95%。

CI 分为三个 job：`Build (JDK 21)`、`Conformance (against lua5.4)` 和
`Static analysis`。其中 conformance job 会从源码构建 Lua 5.4.7、`fkdump`
字节码转储器和 `luashim`，再生成一个逗号小数点的 locale（`pt_BR`——没有它
那批 locale 用例全绿却什么都没测），然后<b>把全量测试跑两遍</b>：一遍带
shim，一遍加 `-Djavalua.nonative=true` 走纯 Java 的 `io` 路径，两条都要与
参考实现逐字节一致。每一遍之后紧跟一次 `tools/check_ci_gates.py`，某个闸门
类<b>一条用例都没建</b>时直接判失败；两遍的执行数据追加进同一个
`jacoco.exec`，95% 那条线最后对着合并后的文件卡一次。

### 已知差异

下面只保留**有意**或**平台无法对齐**的偏离（其余应对齐 Lua 5.4.7）：

- **GC**：内存交给 JVM（不移植 `lgc.c`）。弱表、`__gc`、`collectgarbage`
  尽量保留可观测形态，但 `collectgarbage("count")` 报的是 JVM 堆用量
  （绝对值不同，相对变化仍有意义）。上游 `gc.lua` / 收集器参数类用例不做
  差分闸门。
- 超越函数（`math.sin` 等）与平台 libc 可能相差几个 ulp（Java `Math`/fdlibm
  vs 参考实现链接的 libc）。
- `%p` 和 userdata/thread 的默认 `tostring` 输出 Java 身份标识，而不是 C
  地址。
- **NaN 文本符号**：C99 不规定 NaN 的符号。glibc 对 x86 默认 NaN（符号位为
  1）常打 `-nan`；本实现一律打 `nan`（`%+` 下为 `+nan`）。差分测试两侧都会
  归一化。
- 带 shim 时 `os.setlocale` 会真的切换 libc 的 locale，而那是<b>进程级</b>
  状态：同一个 JVM 里所有 `LuaState` 共享它，C 那边是一个进程一个状态。
  `tonumber`、`tostring`、`string.format`、`io.write`、`io.read("n")` 用的
  小数点（`lua_getlocaledecpoint()`）以及 `os.date` 的 locale 名字（`%A`、
  `%B`、`%c` 等，走 libc `strftime`）都会跟着变。
- 没有 shim 时，`os.execute` 对被信号杀死的进程只能给
  `("exit", 128+n)`，`os.setlocale` 只认 `nil`/`"C"`/`"POSIX"`，小数点恒为
  `.`，`os.date` 名字类转换仍走英文表。
- `io.popen` 在子进程**已经写完**再 `close`（极短的 `echo`）时，C 与本实现
  都会偶发 `exit 0` 而不是 `signal 13`——两边共有的时序窗口。子进程仍在写
  （`sleep; echo`、大块 `printf`）时两边都稳定给 `signal 13`。`os.clock`
  已按进程 CPU 时间对齐 C 的 `clock()`；绝对值仍会因 JVM 启动开销与刚拉起的
  `lua` 进程不同。

## 构建与测试

```bash
./mvnw test       # 找不到 Lua 5.4 二进制时，差分测试会自动跳过
./mvnw package
```

指定参考实现二进制即可启用全部闸门：

```bash
LUA54_BIN=/path/to/lua5.4 ./mvnw test
```

查找顺序为 `LUA54_BIN`、`target/lua54/src/lua`，然后是 `PATH` 上的
`lua5.4` 或 `lua`（会校验版本确实是 5.4）。差分驱动把临时文件写在
`target/rt/<pid>/` 下 —— 每个 JVM 一份，所以同时开两个 `./mvnw test`
也不会读到对方的用例文件。可选环境变量：

| 变量 | 用途 |
|---|---|
| `LUA54_BIN` | 参考解释器 |
| `LUAC54_BIN` | 参考 `luac` |
| `FKDUMP_BIN` | 针对参考实现编译的字节码转储器 |
| `LUA54_TESTS` | 解压后的上游测试套件 |
| `LUA54_LIB` | 参考实现的 `liblua.a`（C API 符号集对撞用；不设则依次试 `target/lua54/src/liblua.a`、`/tmp/lua-5.4.7/src/liblua.a`） |

本地构建参考实现：

```bash
curl -LO https://www.lua.org/ftp/lua-5.4.7.tar.gz
tar xzf lua-5.4.7.tar.gz
make -C lua-5.4.7 linux
LUA54_BIN=$PWD/lua-5.4.7/src/lua ./mvnw test
```

## 项目结构

| 路径 | 内容 |
|---|---|
| `src/main/java/com/github/esrrhs/javalua/` | 词法、语法、代码生成、虚拟机、状态、元方法、标准库 |
| `native/` | `luashim.c`、生成的 C API stub、构建脚本 |
| `src/test/java/...` | 单元、差分、fuzz 和原生扩展测试 |
| `benchmark/` | 本机的量级计时，不是 CI 闸门（理由见该目录 README） |
| `tools/` | `check_ci_gates.py`：闸门类一条用例都没跑时直接判失败；`local_conformance.sh`：本地镜像 CI 的 conformance job（两条 `io` 路径各跑一遍、覆盖率合并后统计） |

Java 类与 C 源码一一对应：`Lexer` ↔ `llex.c`、`Parser` ↔ `lparser.c`、
`Codegen` ↔ `lcode.c`、`Vm` ↔ `lvm.c`、`LuaState` ↔ `lstate.c`/`ldo.c`、
`Api` ↔ `lapi.c`、`LuaAux` ↔ `lauxlib.c`，每个库文件对应一个类。

## 许可证

MIT
