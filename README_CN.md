# fakescript-java

[![License](https://img.shields.io/github/license/esrrhs/fakescript_java)](https://github.com/esrrhs/fakescript_java)
[![Language](https://img.shields.io/github/languages/top/esrrhs/fakescript_java)](https://github.com/esrrhs/fakescript_java)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.esrrhs/fakescript-java)](https://central.sonatype.com/artifact/com.github.esrrhs/fakescript-java)
[![Build Status](https://github.com/esrrhs/fakescript_java/actions/workflows/maven.yml/badge.svg?branch=master)](https://github.com/esrrhs/fakescript_java/actions)

**纯 Java 实现的 Lua 5.4 —— 不自己实现 GC，内存回收全部交给 JVM。**

[English](./README.md)

---

## 设计目标

> 让现有的 Lua 项目**一行代码都不用改**就能跑在 JVM 上。

本项目是 **Lua 5.4 参考实现（`lua-5.4.x` C 源码）到 Java 的逐模块翻译**。
它不是一门"借鉴了 Lua 的"语言——它就是 Lua 5.4，只是把内存管理那一层摘掉、
交给了宿主运行时。

| | |
|---|---|
| **目标** | 完整的 Lua 5.4 语言语义 + 完整的标准库 |
| **方法** | 逐模块直译 C 参考实现 |
| **GC** | **不实现**。对象生命周期完全交给 JVM（G1/ZGC 本身就是并发收集器） |
| **运行环境** | Java 17+（协程是独立的栈，不用线程，见下） |
| **兼容范围** | 纯 Lua 项目；C 扩展模块（`.so` / `luaopen_*`）本阶段不做 |

### 为什么不写 GC

C 实现的 `lgc.c` 是最复杂的部分之一：三色增量标记、分代模式、写屏障、终结器、弱表。
它做的事，JVM 已经做得更好——并发、且调优过。所以这个文件整体删除，
**只有那些"依赖回收动作"的语义**用 JVM 原语重建：

| Lua 概念 | C 实现机制 | 本实现 |
|---|---|---|
| 对象生命周期 | `lgc.c` 三色标记 | **JVM GC**（G1 / ZGC） |
| 弱表（`__mode = "k"/"v"/"kv"`） | `lgc.c` 里清扫弱链 | `WeakReference` + `ReferenceQueue` |
| `__gc` 终结器 | `luaC_checkfinalizer` / `GCTM` | `java.lang.ref.Cleaner` |
| 短字符串驻留 | `lstring.c` 的驻留表需抵抗回收 | 弱引用驻留池，无人引用即释放 |

其余部分——词法、语法、代码生成、虚拟机、元方法、协程、标准库——都是直译。

---

## 架构

包结构一比一对应 C 源码，拿着 `lua-5.4.x` 对照即可定位：

| Lua 5.4 C 源码 | Java |
|---|---|
| `lobject.h` / `lobject.c` | `LuaValue`、`LuaString`、`LuaTable`、`Proto`、`Closure`、`Upvalue` |
| `lstring.c` | `StringPool` |
| `ltable.c` | `LuaTable` |
| `lfunc.c` | `Proto`、`Closure`、`Upvalue` |
| `lstate.c` | `LuaState`、`CallInfo`、`GlobalState` |
| `lzio.c` | `Zio` |
| `llex.c` | `Lexer` |
| `lparser.c` | `Parser`（与上游一致的手写递归下降） |
| `lcode.c` / `lopcodes.h` | `FuncState`、`Codegen`、`OpCode` |
| `lvm.c` | `Interpreter` |
| `ldo.c` | 调用帧、保护调用、错误传播 |
| `ltm.c` | `Meta`（元方法派发） |
| `ldebug.c` | `Debug`、错误信息、栈回溯 |
| `lapi.c` / `lauxlib.c` | `LuaState` 上的操作、`AuxLib` |
| `lundump.c` / `ldump.c` | 字节码载入 / 转储 |
| `lbaselib.c`、`lcorolib.c`、`loadlib.c`、`lstrlib.c`、`ltablib.c`、`lmathlib.c`、`liolib.c`、`loslib.c`、`lutf8lib.c`、`ldblib.c` | 对应的 `lib.*` 类 |
| `lgc.c` | **不存在** —— 见上 |

`lauxlib.c` 等辅助层一并搬过来，因为标准库是照着它们写的。

---

## 关键设计决策

以下几处是"把 C 翻译成 Java"不能机械照搬的地方，选错了后面要花很大代价才补得回来。

### 1. Lua 字符串是字节串，所以不能用 Java `String`

Lua 字符串可以包含 `\0`；`#s` 数的是**字节**；`s:byte(i)` 返回一个字节。
Java 的 `String` 是 UTF-16，且不接受孤立代理项。任何处理二进制数据、UTF-8 文本
或含 `\0` 的真实 Lua 项目，只要字符串底层用 `String` 就会出错。
因此 Lua 字符串底层用 `byte[]`，所有字符串库函数按字节实现。

### 2. 值保留整数/浮点子类型区分

Lua 5.4 区分 `1`（integer）与 `1.0`（float）：二者比较相等，但 `math.type` 不同，
`//` 与 `/` 不同，`1/2` 是 `0.5` 而 `1//2` 是 `0`，转字符串分别是 `1` 与 `1.0`。
值采用**可变槽**结构（`type` 标签 + `long` + `double` + `Object` 载荷），
对应 C 的 `TValue`：既避免装箱，也让 VM 能原地写栈槽。

### 3. 表保留数组部分/哈希部分的分离

照搬 `ltable.c`，包括"这个整数键是否足够密集、能否放进数组部分"的迁移逻辑。
整数键与字符串键是不同的项：`t[1]` 与 `t["1"]` 是两个不同的条目，与 Lua 一致。

### 4. 协程是独立的栈，不是线程

Lua 协程可以在任意深度的栈上 yield——`pcall` 内部、元方法内部、`for` 迭代器内部。
原计划是每个协程给一条 Java 虚拟线程。阶段 5 实测下来这是个错的选择，已经改掉。

Lua 协程是**协作式**的：同一时刻只有一个在跑，挂起点由 `coroutine.yield` 显式
指出。C 版的实现就是「每个 `lua_State` 一条独立栈 + `longjmp` 跳回
`lua_resume`」。Java 有同样的两样东西——每个 `LuaState` 一条独立栈，加上一个
把 Java 调用栈弹掉的异常（`LuaYield`）。**根本不需要线程**：Lua 那边的状态全在
堆上，弹掉 Java 帧之后，栈、`CallInfo` 链、开放 upvalue 都原样留着，`resume`
时接着跑就行。

虚拟线程还有个问题是**过于宽松**：C 函数回调进 Lua 的地方（`table.sort` 的比较
函数、`string.gsub` 的替换函数、被 `print` 调用的 `__tostring`）在真实 Lua 里
不能 yield，会报 `attempt to yield across a C-call boundary`；换成虚拟线程它就
「能跑」了。对一个目标是「一行不改跑现有 Lua 代码」的项目来说，这个差异是 bug
不是特性。

顺带的好处是 Java 下限从 21 降回 17——本机现在的构建就是 17。

取代线程的是 `ldo.c` 的忠实移植：`lua_resume` / `unroll` / `finishCcall` /
`finishpcallk` / `precover`，外加 `luaV_finishOp`（把被 yield 打断的那半条指令
补完）。会被打断的 C 函数（`pcall`、`xpcall`）注册一个续体，也就是 Java 版的
`lua_KFunction`。

### 5. 错误用 Java 异常，不用 `setjmp` / `longjmp`

`lua_error` 变成抛出携带 Lua 错误值的异常。`pcall` / `xpcall` 捕获它，协程边界
同样处理。需要小心处理：非字符串的错误值、带 `__tostring` 的错误对象、
以及跨越协程边界传播的错误——都要与上游行为一致。

### 6. 不再用 JFlex / Bison

Lua 参考实现的解析器是手写递归下降。直接搬过来，就去掉了构建期代码生成这一步，
以及旧架构里"生成物漂移"的问题。

### 7. 元方法：全部实现，包括那些别扭的规则

`__index`、`__newindex`、`__call`、`__concat`、`__len`、`__eq`、`__lt`、`__le`、
`__unm`、`__add` …… `__idiv`、`__close`、`__gc`、`__mode`、`__name`、`__metatable`、
`__pairs`、`__ipairs`。包括容易做错的部分：只有 `__lt` 时 `__le` 退化为
`not (b < a)`；`__eq` 只在类型可比时触发；表上的 `__len` 忽略非整数结果；
`__index` 的链式查找。

---

## 实施路线

每一阶段结束都必须测试全绿；红着不许往下走。

| 阶段 | 内容 | 上游对应 |
|---|---|---|
| **0** | 地基：`LuaValue`、`LuaString` 与驻留、`LuaTable`、`LuaState`、错误类型、`Zio` | `lobject`、`lstring`、`ltable`、`lstate`、`lzio` |
| **1** | 词法 + 递归下降语法分析，按上游方式直接产出而不单独建树 | `llex`、`lparser` |
| **2** | 字节码：完整 Lua 5.4 指令集、代码生成、常量折叠、upvalue 解析 | `lopcodes`、`lcode`、`lfunc` |
| **3** | 解释器主循环：调用/返回、带类型转换的算术与比较、元方法派发 | `lvm`、`ldo`、`ltm` |
| **4** | 闭包与 open/closed upvalue、`goto`/label、变长参数、多重赋值与多返回值 | `lparser`、`lvm` |
| **5** | 协程：`coroutine.*`、yield/resume/unroll、yield 与错误跨边界 | `lcorolib`、`ldo`、`lvm` |
| **6** | 依赖回收的语义：弱表、`__gc`、弱字符串驻留池 | *（取代 `lgc.c`）* |
| **7** | 标准库：`base`、`coroutine`、`package`、`string`、`table`、`math`、`io`、`os`、`utf8`、`debug` | `l*lib.c`、`loadlib.c` |
| **8** | `load` / `loadstring` / `dump`、`require` 搜索器、`package.path` / `cpath` | `lundump`、`ldump`、`loadlib` |
| **9** | 一致性验证：上游测试套件 + 与真实 `lua5.4` 二进制的差分测试 | — |

---

## 怎么证明"它真的是 Lua"

一个只是**看起来像** Lua 的重写是没有价值的。四道闸门，强度递增：

1. **上游测试套件**。直接跑 `lua-5.4.x/tests/*.lua`（`alls.lua`、`big.lua`、
   `strings.lua`、`coroutine.lua`、`pm.lua` 等），不做任何修改。这是主闸门——
   上游自己的测试集比这里手写的任何用例都刁钻。
2. **差分测试**。同一份脚本分别在本实现与真实的 `lua5.4` 二进制下运行，
   stdout 必须逐字节相同。CI 里装 `lua5.4` 后自动跑。
3. **差分模糊测试**。生成程序与参考二进制对比——这一条专门抓"不崩但算错"那类缺陷。
4. **覆盖率门禁**（JaCoCo 行覆盖率）。

一个阶段只有在适用于它的闸门全绿之后才算完成。

---

## 现状

**阶段 10（上游 `lua-5.4.x/tests` 端到端跑绿）已完成。** 19 个「独立可跑且输出
确定」的用例文件（`api`、`bitwise`、`calls`、`closure`、`code`、`coroutine`、
`db`、`errors`、`events`、`gengc`、`goto`、`nextvar`、`pm`、`strings`、`tpack`、
`utf8`、`vararg`、`verybig` 等）与真实 `lua5.4` 的输出**逐字节一致**；
`cstack` / `gc` / `literals` / `locals` / `constructs` 五个因为打印的是随实现或
随随机数而变的东西，只断言「跑完并打印 `OK`」——理由逐个列在 *阶段 10* 一节里。
全量 777 个用例绿，JaCoCo 行覆盖 0.90（门禁 0.89）。

**阶段 9（`debug` 库，含 `sethook`）已完成。** 旧的 fakescript 语言实现已从
`master` 移除，见下文 *迁移说明*。阶段 0~9 已落地：编译器（词法 → 语法 → 代码生成）
产出的字节码与真实 `lua5.4` **逐字段一致**，VM 已经能把它跑起来——158 + 121 + 60
+ 32 + 25 + 16 + 21 + 36 段差分脚本与 2100 个生成程序的 stdout 与真实 `lua5.4`
**逐字节一致**，错误信息逐行一致。`string.dump` 的产物另外按<b>原始字节</b>比过，
不只是比行为。十个标准库（base、coroutine、package、string、utf8、table、math、
os、io、debug）全部落地，`debug` 是完整的——钩子也在内。

阶段 6 是「不自己实现 GC」这个决定真正付出代价的一处。弱表、`__gc`、字符串池
全部重建在 JVM 的可达性之上，而那与 Lua 的标记-清除**不是一回事**；有三处
怎么也对不齐，列在下面的 *已知的不一致* 里。

已落地：

| 部件 | 上游对应 | 说明 |
|---|---|---|
| `Lua` 类型标签 | `lua.h`、`lobject.h` | 保留整数/浮点两个变体子类型 |
| `LuaValue` | `TValue` | 可变槽，让 VM 能原地写栈槽 |
| `LuaString` | `TString` | 底层 `byte[]`；`\0`、任意字节与非法 UTF-8 都原样保存 |
| `StringPool` | `lstring.c` 的 `strt` | 弱引用驻留，不再引用的字符串不会钉住堆 |
| `LuaTable` | `ltable.c` | 数组部分 + 哈希部分、`rehash`、`#t` 边界、整值浮点键规范化 |
| `LuaError` | `lua_error` | 携带 Lua 值；只有这个类型会被 `pcall` 捕获 |
| `Zio` | `lzio.c` | 字节输入流；`EOZ` 是 -1，字节按无符号取 |
| `LCType` | `lctype.c` | 逐字节照搬 `luai_ctype_` 表，因此 `lislalpha` 与上游一样把 `_` 当字母 |
| `Numeral` | `lobject.c` 的 `luaO_str2num` | 先整数后浮点；十进制用严格正则，把 `1d`/`inf`/`nan` 按 C `strtod` 的方式拒掉 |
| `Lexer` | `llex.c` | 完整 token 集、`\z`/`\xXX`/`\u{XXX}`/`\ddd` 全部转义、长字符串与长注释、逐字节一致的错误信息 |
| `OpCode` | `lopcodes.h/.c` | 83 条指令；顺序、名字与 `luaP_opmodes` 对着上游头文件机械校验过 |
| `Proto` | `lobject.h`、`lfunc.c` | 指令、常量、嵌套原型、upvalue、局部变量、行号信息 |
| `Arith` | `lobject.c`、`lvm.c` | 常量折叠用的算术——与将来 VM 用的是同一套 |
| `ExpDesc`、`FuncState`、`DynData` | `lparser.h` | `expdesc` 的 union 摊平；块/goto/变量的簿记 |
| `Codegen` | `lcode.c` | 发指令、分配寄存器、跳转与回填、常量折叠、`NEWTABLE`/`SETLIST` |
| `Parser` | `lparser.c` | 手写递归下降，边解析边发指令 |
| `UpVal` | `UpVal` | 两态：开放时持有栈槽引用，关闭时持有私有副本 |
| `LClosure` | `LClosure` | 原型 + upvalue 数组 |
| `CallInfo` | `CallInfo` | 帧记录；`CIST_FRESH` 决定 Lua→Lua 返回在哪一层停 |
| `LuaState` | `ldo.c`、`lstate.c` | 栈、调用/返回、`pcall`、upvalue 开关闭、全部 `luaG_*` 错误 |
| `LuaTm` | `ltm.c` | 元方法查找与派发、`__le` 退化、to-be-closed |
| `Vm` | `lvm.c` | 解释器主循环，83 条指令全覆盖 |
| `BaseLib`、`MathLib` | `lbaselib.c`、`lmathlib.c` 与部分 `lstrlib.c` | 最小集合，但每条都对过差分 |
| `Diff`（测试） | — | 差分驱动的公共部分：一段脚本两边各跑一遍 |
| `ConformanceTest`、`CodegenTest`、`ParserTest`、`RuntimeTest` | — | 以真实的 `lua5.4` 二进制作为裁判 |
| `ClosureTest`（测试） | — | 阶段 4 闸门：闭包、goto、变长参数、多重赋值 |
| `CClosure` | `CClosure` | 绑定了值的 C 函数，`coroutine.wrap` 需要它 |
| 线程值（`Lua.VTHREAD`） | `LUA_TTHREAD` | 一个能被当作 Lua 值使用的 `LuaState` |
| `CoroutineLib` | `lcorolib.c` | `create` / `resume` / `yield` / `status` / `wrap` / `isyieldable` / `running` / `close` |
| `resume` / `unroll` / `finishCcall` / `finishpcallk` / `precover` | `ldo.c` | yield/resume 状态机，不用线程 |
| `LuaKFunction` | `lua_KFunction` | 被 yield 打断的 C 函数的续体 |
| `LuaYield` | `luaD_throw(L, LUA_YIELD)` | 用于展开的异常；刻意**不**被 `pcall` 捕获 |
| `Vm.finishOp` | `luaV_finishOp` | 把被 yield 打断的半条指令补完 |
| `CoroutineTest`（测试） | — | 阶段 5 闸门：60 段差分脚本，含在每个元方法里 yield |
| `FuzzTest`（测试） | — | 1500 个生成程序，另有 600 个跑在协程里并随机撒 yield |
| `LuaTable.WeakRef` | `lgc.c` 的 `iscleared` | 弱引用**就是**存储形式；额外记下身份哈希，因为 `identityHashCode(null)` 恒为 0，会把所有失效键一起塌到 0 号桶 |
| `LuaTable.purge` / `rewrap` | `cleartable` / `traverseweakvalue` | 失效条目在一轮收集结束时摘掉；`__mode` 变化时整体重新包装 |
| `LuaState.clearDeadStackSlices` | `traversethread` 的 atomic 分支 | 把所有线程栈的 `[top, stack_last)` 置 nil，只在 `collectgarbage` 里做 |
| `GcSentinel` | `lgc.c` 的 `GCTM` | 只被目标表引用的哨兵；它的 `finalize` 把表复活到待终结队列，由 **Lua 线程**排空 |
| `LuaState.close` | `lua_close` → `callallpendingfinalizers` | 退出时把还有 `__gc` 的对象全跑一遍 |
| `WeakTableTest`（测试） | — | 阶段 6 闸门：20 段差分脚本，拆成「依赖收集时机」与「恒成立」两半 |
| `GcTest`（测试） | — | 阶段 6 闸门：12 段脚本——只跑一次、错误静默、后置 `__gc` 不生效 |
| `LuaUserdata` | `Udata` | 载荷 + **每对象**元表；`luaL_testudata` 要的正是后者 |
| `Undump` | `lundump.c` | 读预编译 chunk：头部校验、变长长度、`binary string` 源名规则 |
| `Dump` | `ldump.c` | 写预编译 chunk：`string.dump` 与 `strip` |
| `LoadTest`（测试） | — | 阶段 8 闸门：25 段脚本——模式、chunk 名、`_ENV`、读函数、`loadfile` / `dofile` |
| `DumpTest`（测试） | — | 阶段 8 闸门：16——运行期 stdout 与 `string.dump` 产物都逐字节比 |
| `StringPack` | `lstrlib.c` 的一部分 | `string.pack` / `unpack` / `packsize`；标准库里唯一平台相关的一角 |
| `PackTest`（测试） | — | 21 段脚本——整数、浮点、字符串、对齐、宽整数、报错、键集合 |
| `DebugLib` | `ldblib.c` + `ldebug.c` / `ldo.c` / `lauxlib.c` 的一部分 | `getinfo` / 局部变量 / upvalue / `traceback` / 注册表 / 元表 / 用户值 / `setcstacklimit` / `sethook` / `gethook` |
| `LuaHook` | `lua.h` 的 `lua_Hook` | 钩子回调；`DebugLib` 的 `hookf` 就是 `sethook` 装的那个 |
| `LuaLibs` | `linit.c` | `luaL_openlibs`；按 `loadedlibs` 的顺序开十个库 |
| `DebugTest`（测试） | — | 阶段 9 闸门：36 段脚本——层号、选项、局部变量、upvalue、`traceback`、深栈，外加 14 段钩子用例 |
| `LuaLibsTest`（测试） | — | `openAll` 真的开全了十个，而且库表就是元表指的那张 |

### 字节码这道闸门

编译器是**用差分比对验证的，不是靠自称的预期值**。`src/test/resources/fkdump.c`
是挂在参考 `lua5.4` 源码上的一个小转储器：对每份脚本，它把每条指令的
**全部字段**（`a`、`b`、`c`、`k`、`Bx`、`sBx`、`Ax`、`sB`、`sC`、`sJ`）、行号、
常量表、upvalue 表递归地打出来。`CodegenTest` 拿它和 `ProtoDump` 渲染出的
`Proto` 逐行比对，要求完全相同。

语料有两份：约 50 个覆盖各种语法结构的小脚本，以及**上游 `lua-5.4.7` 测试套件**
的 33 个文件（CI 里下载到 `target/lua54-tests`），只编译不运行。全部一致。

错误信息同样走差分：`ParserTest` 把写坏的源码落盘交给真实 `lua5.4` 跑，
再逐字节比对消息——`'end' expected (to close 'if' at line 1)`、
`label 'a' already defined on line 1`、`<goto l> at line 1 jumps into the scope of local 'x'`
等等。

词法层是**用差分比对验证的，不是靠自称的预期值**：一批故意写坏的源码先落盘
交给真实 `lua5.4` 跑，再把同一份字节喂给 `Lexer`，逐字比对错误信息（含
`near '<token>'` 那一段——`<eof>` 与其他 token 的加引号规则并不一样）。
另一批用例反向验证：参考实现能加载的源码，这里也不许报错。

本阶段有两条语义是**跑参考实现实测**定下来的，而不是照手册推的：

* 整值浮点作键时，写入会**规范化成整数键**
  （`t[1.0]` 之后 `math.type(next(t))` 是 `"integer"`，`#t` 是 1）；
* 写 **NaN** 键报 `table index is NaN`，而**读** NaN 键静默返回 `nil`。

两条都由 `ConformanceTest` 里跑真实 Lua 5.4 的用例锁住。

### 本阶段由参考实现裁定的三件事

每一条看起来都像 bug，其实不是；三条都有测试锁住。

* **`>>` 是逻辑右移**。`intop(>>,x,-y)` 先把操作数转成 `lua_Unsigned`，
  所以 `-8 >> 2` 是 `4611686018427387902`，不是 `-2`。
* **上游的常量表里确实有重复项**。`ls->h`（「常量 → 下标」缓存）由整份源码共享，
  而每个函数的常量各自从 0 开始编号。内层函数会把缓存里的下标改成自己的小下标；
  回到外层时那个下标已经指向别的常量，校验失败，于是<b>再追加一份重复的</b>。
* **整值浮点要用扰动过的缓存键**。Lua 的表用 `==` 判键，`100000` 与 `100000.0`
  是同一个键；没有 `luaK_numberK` 里那个 `r + r*2^-52`，两者会互相把对方从缓存
  里挤掉，每用一次就新建一份常量。

### 运行期这道闸门

`RuntimeTest` 是阶段 3 的闸门。每条用例都是一段脚本**跑两遍**——本实现一遍、
真实 `lua5.4` 一遍——两份 stdout 必须相等。出错的用例比对消息行，含
`(local 'x')` / `(constant '10')` / `(method 'nope')` 这类后缀。

栈靠一条不变量撑着，其它一切都以它为前提：

> 栈槽 `LuaValue` **永不被替换**。`LuaState` 永远做 `stack[i].set(v)`，
> 绝不做 `stack[i] = v`。

只有守住这一条，「开放 upvalue 持有栈槽的引用」在 Java 里才等价于 C 里的
「开放 upvalue 持有栈槽的指针」。破一次，闭包就会静默地看不到更新。

现在就可以这样跑：

```java
LuaState L = new LuaState();
BaseLib.open(L);
MathLib.open(L);
L.run("print('hello from Lua')");
```

### 本阶段由参考实现裁定的六件事

每一条最初都凭手册或直觉写错了，六条全部由差分测试锁住。

* **算术不做字符串转换，`"10" + 1 == 11` 也不来自 VM。** `lvm.c` 的算术宏用的是
  `tonumberns` / `tointegerns`，两者都不认字符串。转换在 `lstrlib.c`：它给字符串
  元表挂上 `__add` 等元方法，先把两侧 `lua_stringtonumber`，再 `lua_arith`。
  少了这一组，就会得到一个看起来合理但与参考实现不同的
  `attempt to perform arithmetic on a string value`。
* **C 函数的 n 个返回值是「栈顶最后 n 个」。** `ipairsaux` 先压 `i`、再压 `t[i]`，
  当 `t[i]` 是 nil 时 `return 1`——泛型 for 拿到的正是最后那个 **nil**，不是 `i`。
  按直觉写成「返回 i」，`for _, v in ipairs(t)` 永远不会终止，因为循环只在
  **第一个**返回值为 nil 时退出。
* **每条算术指令后面必定跟一条 `OP_MMBIN*`。** 成功就 `pc++` 跳过它，
  失败就自然落下去派发元方法。漏一个 `pc++`，正确的表达式也会多跑一次元方法。
* **`OP_NEWTABLE` 的 `EXTRAARG` 是无条件的**（因为 `k == 0` ⟺ `Ax == 0`），
  而 `OP_SETLIST` 的是有条件的。按条件读前者，每个表构造都会报
  `malformed opcode EXTRAARG`。
* **C 的 `%g` 会去掉末尾的零，Java 的不会。** 在手写 `formatG` 之前，
  `1.5` 被打印成 `1.5000000000000`。
* **`luaG_runerror` 与 `luaL_error` 取位置的方式不同。** 前者看**当前**帧的
  `isLua(ci)`；后者是 `luaL_where(L, 1)`，会往回跳一层到调用者。库函数用错了
  前者，就完全没有 `src:line:` 前缀——因为它自己那帧是 C 帧。

### 差分 fuzz 这道闸门

`FuzzTest` 用一个小文法生成 2100 段程序，逐段与参考实现比对：1500 段常规程序，
另有 600 段把主 chunk 塞进 `coroutine.create`、随机撒 `coroutine.yield`，再用一个
有界的 `resume` 循环驱动。两条约束保证它
不会变成一个麻烦制造机：

* **必须终止。** 只生成有界的 `for` / `ipairs`，不生成 `while`、`repeat`、
  `goto`；函数调用只允许指向**更早定义完**的函数，于是调用图是 DAG，
  递归与死循环都不可能。
* **必须可复现。** 种子从 0 递增，不用 `Math.random`。失败时凭「第几个种子」
  就能精确复现。

生成器不知道我们认为 Lua 该怎样——这正是它的价值：它是唯一能抓住
「不崩、但答案错」的闸门。

### 本阶段由参考实现裁定的四件事

* **`luaF_newtbcupval` 在赋值当下就检查 `__close`，不是在作用域结束时。**
  `OP_TBC` 紧跟在局部变量拿到值之后，所以 `local x <close> = 5 print(x)`
  会在打印之前就抛 `variable 'x' got a non-closable value`。`nil` 与 `false`
  豁免（`l_isfalse`），所以 `local x <close> = nil` 是合法的。
* **展开过程中 `__close` 里的错误会顶掉原错误。** `error('orig')` 之后
  `__close` 里再 `error('in close')`，`pcall` 拿到的是 `in close`。
  沿用原错误是错的。
* **代码生成只折叠 `OPR_MINUS` 与 `OPR_BNOT`，从不折叠 `OPR_LEN`。**
  所以 `#7.5` 是**运行期**错误，不是加载期错误。折叠它会让一段上游能编译、
  只在运行时才失败的脚本变成编译不过。
* **两条闸门自身的规则，都是踩过才知道。** 脚本出错时它已经打出的 stdout
  **仍然要参与比对**（参考实现的 stdout 与 stderr 是一起捕获的，「先打十行
  再崩」的输出里两者都有）；而 `lua.c` 加的程序名前缀要**全文抹掉**，
  不能只抹开头——一旦 stdout 在前面，它就出现在输出中间了。

### 本阶段被参考实现裁定的五件事

* **`pcall`、`xpcall`、元方法、`for` 迭代器都可以 yield；标准库的回调不可以。**
  `table.sort` 的比较函数、`string.gsub` 的替换函数、被 `print` 调用的
  `__tostring` 都会报 `attempt to yield across a C-call boundary`——因为
  `ltablib.c` / `lstrlib.c` / `luaL_callmeta` 是经 `lua_call`
  （`luaD_callnoyield`）回调进 Lua 的。这恰恰是虚拟线程会「修好」、
  而实际上是改错的行为。
* **主线程永远不可 yield。** `lstate.c` 的 `lua_newstate` 无条件执行
  `incnny(L)`，所以主协程上 `coroutine.isyieldable()` 是 `false`，哪怕一个 C
  调用都还没发生。协程起始为 0，每次 `resume` 也重置为 0（`lua_resume` 只从
  resume 方拷 `nCcalls` 的低 16 位）。
* **`resume` 的参数就是 `yield` 的返回值。** `lua_resume` 里那句
  `poscall(ci, nargs)` 决定了 `resume(co, 99)` 之后 `coroutine.yield(1)` 看到的
  是 `99`——搬回去的值的个数是传进来的个数，不是 yield 出去的个数。
* **`coroutine.wrap` 报错用的是自己的名字，不是 `create` 的。** `luaB_cowrap`
  直接调 `luaB_cocreate`，而 `luaL_argerror` 的函数名是从「当前帧是被谁调用的」
  倒推出来的，所以 `coroutine.wrap(nil)` 报的是
  `bad argument #1 to 'coroutine.wrap'`。
* **`luaV_finishOp` 每个可中断操作码都有一个分支，`OP_CONCAT` 那个不能省。**
  拼接是一批一批做的，`__concat` 里 yield 之后要把元方法结果往回挪一格、把
  `top` 收一格，再把剩下的元素拼完——可能又 yield 一次。省掉它的表现是
  拿到**操作数那张表**而不是拼接结果。

### 阶段 6：参考实现裁定的五件事

* **GC 可见的栈到 `L->top` 为止，这一点是承重的。** `lgc.c: traversethread`
  只 mark `[stack, top)`，然后在 atomic 阶段把 `[top, stack_last)` 整段置 nil
  ——源码注释就叫 “clear dead stack slice”。所以**落在 `top` 之上的寄存器里的
  值对收集器不可见**，会被收掉。两种形态依赖这一点：C 函数压出来的值（比如
  `next` 取回的键，调用结束 `top` 回落，槽还攥着它），以及循环最后一次迭代
  建的表（`for i = 1, 5 do t[i] = {i} end` 之后 `{5}` 还留在寄存器里，而
  `L->top` 只到调用参数处）。我们这边 `stack` 是条 Java 数组，**每个槽都是活
  引用**，所以 `LuaState.clearDeadStackSlices` 必须把这一步补回来——而且得在
  `collectgarbage` 里做，不能在每次改 `top` 时做（见下一条）。
* **……但在每次改 `top` 时急切地清，会撞坏两处。** C 只在 atomic 阶段清。
  照抄到内联位置，会静默毁掉 `luaT_callTMres` 的结果（目标寄存器**就是**弹出
  的那一格），以及 `pcall` 的错误对象（`LuaError.value` 直接别名了活栈槽——
  `error('boom', 0)` 抛的就是 `L.arg(0)` 本身——于是 `pcall` 变成返回
  `false nil`）。等到 `collectgarbage` 再清既忠实，也同样精确。
* **弱表的存储必须「本身是弱的」，不是在旁边挂一个 `WeakReference`。** 槽里的
  载荷就是那个 `WeakReference`；载荷若是强的，就什么也释放不掉。这带来一个
  后果：弱表上 `LuaTable.get` 只能交出**副本**，于是写路径那条快速分支
  （`OP_SETTABLE` 的「槽已存在，直接写进去」）不能用在弱表上——往副本里写等于
  没写，症状是 `t.f = g` 之后 `t.f` 还是 `f`。
* **`__mode` 是动态读的，而且要把已在表里的条目重新包装一遍。** 先建表、塞好
  条目、再 `setmetatable(t, {__mode = 'k'})`，那些条目照样会被回收——实测过。
  既然弱是本实现的存储形式，模式一变就得遍历全表重新包装；而且弱表只能把所有
  东西放在哈希部分（数组槽是普通 `LuaValue`，放不了 `WeakRef`）。
* **`__gc` 要求对象还「活着」，这是 JVM 所有引用类型都给不了的。** 每一种
  「它死了」的通知（`WeakReference` 被清、`Cleaner`、`PhantomReference`）都发生
  在对象已经不可达之后，引用再也取不回来。只有 `finalize()` 是 `this` 还能用
  的时候被调到，而且**允许复活**。于是挂了 `__gc` 的表会得到一个只有它引用的
  哨兵：两者一起不可达时，哨兵的 `finalize` 把表塞进待终结队列，下一次
  `collectgarbage` 在 **Lua 线程**上排空——绝不能在 JVM 终结线程上调 Lua 函数，
  那里既会和 `LuaState` 抢数据，抛出的异常也会被 JVM 连日志都不留地吞掉。

### 已知的三处与参考实现不一致

这些不是疏漏，每一处都是 JVM 没有对应原语的地方，如实记着而不是糊过去。

* **ephemeron 只做对了「值就是键」这一种。** `__mode='k'` 下 `t[k] = k` 会摘掉
  条目（与参考实现一致），常见的缓存形态 `cache[obj] = {新数据}` 会保住值（也
  一致）。但 `t[k] = {k}`——值**包含**键、键又没有别的引用——本实现会留着，
  而 Lua 会摘掉。两半在 Java 里互斥：值存强的会把键养活，值存弱的又会在「键
  活着但值没有别的引用」时把值弄丢，而那正是最常见的缓存用法。丢数据比漏摘
  条目严重，所以选了漏摘。
* **键挂了 `__gc` 的弱条目会早一轮被摘掉。** Lua 会 mark 带终结器的对象，于是
  那一轮条目还在；本实现靠 `WeakReference` 被清来判死活，同一轮就摘了。
* **挂了 `__gc` 的对象只在 `collectgarbage` 时才被回收。** JVM 除了真收掉它，
  没有别的办法告诉我们「它不可达了」，所以没有显式收集周期就排不上终结器。

### 阶段 7 到目前为止

`string`（`StringLib`）、`table`（`TableLib`）、`math`（`MathLib`）、
`utf8`（`Utf8Lib`）、`os`（`OsLib`）、`io`（`IoLib`）、`package`
（`PackageLib`）已经搬完，各带一道差分闸门：`StringLibTest`（29）、
`MathLibTest`（26）、`TableLibTest`（18）、`Utf8LibTest`（13）、
`OsLibTest`（24）、`IoLibTest`（35）、`PackageLibTest`（22）。

### 阶段 7 里由参考实现裁定的十件事

* **共享的 `lauxlib.c` 那一层必须**先于**任何库落地。** 参数检查
  （`checkString` / `checkInteger` / `argError` / …）不是脚手架：错误文本本身
  就是可观测契约的一部分，六个库各抄一遍，一周内就会彼此走样。其中一处只有
  在「参数从 1 起编号」时才对：`s:rep(nil)` 报的是 `bad argument #1`，因为
  `luaL_argerror` 对方法调用会减一。
* **`math.max` / `math.min` 用的是 `lua_compare`，不是数字比较。** 所以
  `math.max("a","b")` 是 `"b"`，`math.max(1,"b")` 报的是
  `attempt to compare number with string`（走 `__lt` 失败那条路）。它们还保留
  操作数的子类型：`math.max(1, 2.0)` 是 `2.0`（float）。
* **`math.fmod` 的浮点路径是 C 的 `fmod`，不是 Lua 的 `%`。** 两者结果符号
  不同：`math.fmod(-7.5, 2)` 是 `-1.5`（取被除数符号），而 `-7.5 % 2` 是
  `0.5`（取除数符号）。顺手复用 VM 的 `Arith.modf` 是最容易犯的错，且正好
  差一个符号。
* **`math.log` 的 base 2 / base 10 两条分支确实是不同的计算。** C 调的是
  `log2` / `log10`：`log(x)/log(2)` 与 `log2(x)` 在 43 个样本里有 9 个不同，
  `log(x)/log(10)` 与 `log10(x)` 更是差 25 个。Java 没有 `log2`，这里用尾数
  分解 `x = m * 2^e` 还原成 `e + log(m)/ln2`，192 个样本里 190 个落在 1 ulp 内。
* **`table.insert` 落在带 `__newindex` 的对象上必须真的调它。** `ltablib.c`
  用的不是 `luaH_setint` 而是 `lua_seti`，后者走 `luaV_finishset`。写成裸写
  会让 `table.insert(ud, v)` 静默地什么都不做——不报错，也不写入。
* **`os.execute` 起的进程名必须是 `sh`，不能是 `/bin/sh`。** C 的 `system()`
  是 `execl("/bin/sh", "sh", "-c", cmd)`——**argv[0] 是 `sh`**——而 shell 的
  诊断信息是用 argv[0] 打头的。写成全路径会把 `sh: 42: command not found`
  变成 `/bin/sh: 42: command not found`，差分闸门立刻就能看到。
* **userdata 的元表在对象上，不在类型上。** `luaL_testudata` 问的是「这个
  userdata 的元表是不是注册在 `FILE*` 名下那一张」，所以文件句柄不能跟内部
  userdata（随机数状态、`gmatch` 的状态）共用一张按类型分的元表，否则后者
  会被当成文件。为此补了 `LuaUserdata`，顺带补了它的 `__gc`——不然脚本里忘了
  `f:close()` 的文件永远不会关。
* **真实文件上的 `io` 必须块缓冲，而 `io.write` 不能缓冲。** C 的 stdio 对
  文件是缓冲的：`io.output(p):write("x")` 之后把 `p` 读回来是**空的**，那几
  个字节还在 `FILE*` 里。标准流正好相反——它们是行/无缓冲的，而且 `io.write`
  的输出必须落到宿主装的那条流上。所以 `IoLib` 对 `RandomAccessFile` 的写做
  缓冲（`flush`、`seek`、任何读、以及关闭时落盘），对 `stdin` / `stdout` /
  `stderr` 则直接写穿。
* **`require` 判断搜索器结果用的是 `lua_isstring`，不是「是不是字符串」。**
  返回**数字** `42` 的搜索器，那个 `42` 会被拼进「找不到模块」的消息里；
  只有既非字符串又非数字的结果才被当成「这个搜索器没说什么」，并把刚加的
  `\n\t` 前缀退掉。
* **加载期错误的源名也要过 `luaO_chunkid`。** 模块文件是按 `"@" + 路径` 编
  译的，但 `luaG_addinfo` 打印前会把 `@` 剥掉，所以 require 一个语法错误的
  模块报的是 `/tmp/m.lua:1: syntax error near 'is'`，不是 `@/tmp/m.lua:1: ...`。
* **每个库都要同时登记进 `_LOADED`，不能只进 `_G`。** 漏掉的是 `coroutine`：
  它当时只在全局表里，于是 `require("coroutine")` 报
  `module 'coroutine' not found`。

### 一处承认下来、不去修的偏离

`sin` / `cos` / `tan` / `asin` / `acos` / `atan` / `exp` / `log` / `log10` /
`sinh` / `cosh` / `tanh` / `pow` 与参考实现**不是逐位相同**。Java 的 `Math`
是 fdlibm，参考二进制链的是平台 libm（本机是 macOS 的 `libsystem_m`）。两者
对真值的误差都在 1 ulp 之内，但最后一位的舍入不同：43 个典型输入里 `sin`
差 1 个、`tan` 差 12 个、`cosh` 差 6 个。要消掉就得自带一份 libm，那既超出
目标，也不符合 Lua 自己给出的保证——Lua 的结果本来就随平台漂。所以差分用例
对这几个函数只比到 14 位有效数字（1 ulp 约 2.2e-16 相对误差）。能对齐的地
方都对齐了：`sqrt`、`abs`、`floor`、`ceil`、`frexp`、`ldexp` 以及上面那两条
`log` 分支都是逐位一致的。

### `io` 里两处承认下来、不去修的偏离

* **`file (%p)`。** `f_tostring` 打的是指针，`print(io.stdout)` 无论如何也不
  可能逐字节相同。`IoLib` 给一个同形状的十六进制值，差分比对把 `0x…` 抹掉。
* **`stdin` 的定位。** 参考实现在测试里的 stdin 被定向到 `/dev/null`，那是
  **可定位**的字符设备（`lseek` 返回 0）；本实现这边是字节数组，定位不了。
  `io.stdout:seek()` 两边一致（管道上是 `nil, "Illegal seek", 29`）；
  `io.stdin:seek()` 不比对——差的是流的来源，不是 `io` 的行为。

### `package` 里两处承认下来、不去修的偏离

这里如实走 `loadlib.c` 的兜底分支：JVM 上没有 `dlopen`（真要接得走 JNI 或
FFI，那是另一个工程），所以 C 搜索器的行为就是 C 版在「未启用动态库」的
平台上的行为。

* **`package.loadlib`** 返回 `nil, "dynamic libraries not enabled; check your
  Lua installation", "absent"`。参考二进制是用 `dlopen` 编出来的，那边给的
  是 `dlerror()` 的文本和 `"open"`。
* **`require` 一个真能找到 `.so` 的模块**会停在「动态库未启用」这一步，
  而不是真的加载它。只有 `.lua` 的模块与 `package.preload` 里的条目不受影响。

### 阶段 8 到目前为止

`load` / `loadfile` / `dofile`（`BaseLib`）与二进制 chunk 那对
`lundump.c` / `ldump.c`（`Undump` / `Dump`）已经落地，各带一道差分闸门：
`LoadTest`（25）与 `DumpTest`（16）。

`DumpTest` 用**两种粒度**比，第二种才是这一阶段的硬闸门：

1. **运行期**：脚本的 stdout 逐字节比，与其它闸门一致；
2. **产物**：两边各自把 `string.dump(f)` 写成文件，再比这两个文件的<b>字节</b>。

只比第一种的话，「编译产物略有偏差、但恰好能跑」是漏得过去的。比产物则把
常量顺序、行号表、局部变量与 upvalue 的调试信息、`maxstacksize` 一起钉住了。
目前比了八种形状：普通、内嵌函数、变长参数、带 upvalue、`goto`、循环、各类
常量，以及一个超过 `LUAI_MAXSHORTLEN` 的常量字符串（dump 与 undump 两侧都走
不驻留的长字符串那条路）。

### 阶段 8 里由参考实现裁定的六件事

* **`luaU_undump` 在报错之前会先改写源名。** 开头的 `@` 或 `=` 要去掉；如果
  源名以签名首字节开头（也就是<b>源名本身就是那段二进制 chunk</b>——
  `load(s)` 不给第二个参数时正是这样），就整段换成字面量 `binary string`。
  于是 `load("\27Lua")` 报的是
  `binary string: bad binary format (truncated chunk)`，不是一串 ESC 开头的乱码。
* **二进制那条路 `f_parser` 吃掉首字节，文本那条路要还回去。**
  `luaU_undump` 的注释写得直白："skip 1st char (already read and checked)"。
  在 undump 之前回退，整个头部会错开一个字节，`truncated chunk` 就变成了
  `not a binary chunk`。
* **`luaO_chunkid` 有第三种形态，而 `load` 碰到的正是它。** 只有 `@x` 与
  `=x` 是剥前缀，其余一律包成 `[string "x"]`。`load("x++")` 报的是
  `[string "x++"]:1: syntax error near '+'`——只剥不包的实现会报 `x++:1:`，
  于是每一条加载期错误都是错的。
* **源名是字节串，`luaO_chunkid` 按<b>字节</b>截。** 先解码成 Java `String`
  再截，遇到非 UTF-8 的名字长度就对不上。所以 `chunkId` 的核心写成 `byte[]`
  版，`String` 那个重载走它。
* **没有源名的原型，位置<b>整段都不给</b>，连行号也不给。**
  `luaG_addinfo` 是 `if (src) ... else 原样返回 msg`。这一点是可观测的：
  `string.dump(f, true)` 会剥掉源名，于是
  `pcall(load(string.dump(f, true)))` 报的是裸的 `boom`。退化到「用 chunk 名
  兜底」不但多一截前缀，还会把那段二进制 chunk 塞进消息里——它是非法 UTF-8，
  解码出来就是替换字符。
* **`lundump` 里的 `default: lua_assert(0)` 是空操作。**
  `lua_assert` 在默认构建下编成 `((void)0)`，所以常量类型字节写成 `99` 的
  chunk 能正常加载，那个常量就停在 `nil` 上。在那儿报错是我们自己发明语义。
  这一条是量出来的（手工拼的 chunk 在参考实现上加载成一个 `function`），
  不是推出来的。

dump 那一侧还有一条：`dumpFunction` 比源名用的是<b>引用</b>
（`f->source == psource`）而不是内容。短字符串驻留之后两者等价，但长字符串
不驻留，C 会为每个内嵌函数各写一遍源名；按内容比会写得比 C 少。

### `string.pack` / `string.unpack` / `string.packsize`

作为 `StringPack`（`lstrlib.c` 的 pack/unpack 那半段）落地，配 `PackTest`
（21 段）。这是标准库里**唯一一处输出取决于宿主平台**的部分：`l` / `L` 是
C 的 `long`，`T` 是 `size_t`，`!` 的默认最大对齐是 `LUAI_MAXALIGN` 的偏移，
默认字节序取宿主的。这些按参考二进制所在的平台（LP64 + 小端）钉死——
在 `long` 只有 4 字节的平台上，参考实现自己给出的字节也不一样。

这里还有两处由参考实现裁定的：

* **`X` 会吃掉它后面那一项。** `getdetails` 调 `getoption` 是为了拿到后一项的
  对齐要求，而那次调用同时把格式串往前推了一格。于是
  `string.packsize("Xi4")` 是 `0`，`string.pack("Xi4", 7)` 得到空串。
  「先看一眼再决定」的实现会给出 `4`——看着更合理，但它是错的。
* **`str_pack` 那个 `lua_pushnil` 标记是可观测的。** 它本来只是把参数与字符串
  缓冲隔开，但它站在 2 号位置，所以不传第二个参数时
  `string.pack("c2")` 报的是 `string expected, got nil` 而不是
  `got no value`。

### 阶段 9 —— `debug` 库，钩子也在内

`ldblib.c` 落地为 `DebugLib`，顺带把 `ldebug.c` 里只有内省接口才用到的那部分
（`lua_getstack` / `lua_getinfo` / `luaG_findlocal` / `luaG_traceexec` /
`luaG_tracecall`）与 `lauxlib.c` 的 `luaL_traceback` 一起搬进来。闸门是
`DebugTest`（36 段脚本）。十六个函数全部落地。

`LuaLibs.openAll`（`linit.c` 的 `luaL_openlibs`）也一并落地——之前「开标准库」
是十次调用，只有测试驱动知道顺序。

#### 钩子这套机制，以及它的调用点都在哪儿

`sethook` 不是一个能单独搬的库函数：它只是一个<b>分发器</b>，真正的调用点散在
解释器各处。搬它意味着除了 `DebugLib.dHook`（`ldo.c: luaD_hook`）之外还要补：

| 调用点 | 上游 | 触发 |
|---|---|---|
| `LuaState.precallC` | `ldo.c` | C 函数的 `call`——在 C 函数本身<b>之前</b> |
| `DebugLib.dHookCall`，由 `Vm` 的 `startfunc` 与 `OP_VARARGPREP` 调 | `ldo.c: luaD_hookcall` | Lua 函数的 `call` / `tail call` |
| `DebugLib.rethook`，由 `LuaState.poscall` 调 | `ldo.c: rethook` | 两类函数的 `return` |
| `DebugLib.traceExec`，由 `Vm` 的 `vmfetch` 调 | `ldebug.c: luaG_traceexec` | `line` 与 `count` |

外加 `CallInfo.trap` 与 `LuaState` 上的 `hook` / `hookmask` / `hookcount` /
`allowhook` / `oldpc`。`trap` 的意义在于它是 `luaV_execute` 里的一个<b>局部</b>
变量——不开钩子时主循环连分支都不走。

#### 又四件由参考实现裁定的事

* **`lua_sethook` 必须调 `settraps`。** 主循环里的 `trap` 是局部变量，只在少数
  几个点（`updatetrap`）才从帧上重读。所以「执行到一半把钩子打开」在别的帧上一
  点用都没有，除非把每个活着的 Lua 帧的 `trap` 都置成 1——这正是 `settraps` 做的。
  少了它，`debug.sethook(f, "l")` 一个行事件都不会来。
* **钩子里的错误，<b>不会被它所报告的那个 `pcall` 抓住</b>。** `pcall` 的 `call`
  钩子是在 `precallC` 里、<b>在 `pcall` 建好恢复点之前</b>报的，所以
  `pcall(function() ... end)` 抓不到「钩子在 `pcall` 自己身上抛的错」。
* **`funcnamefromcall` 里 `CIST_HOOKED` 要判在 `isLua` <b>之前</b>。** 带这个标记
  的那一帧常常是 <b>C 帧</b>（`pcall` 的调用钩子就打在 `pcall` 自己的 C 帧上），
  先判 `isLua` 永远走不到这一支——回溯里那一行会从 `in hook '?'` 变成
  `in function <文件:行>`。
* **钩子永远不能 yield。** `hookf` 用 `lua_call` 调用户函数，而 `lua_call` 走
  `luaD_callnoyield`，「不可 yield」的层数在钩子里非零。实测：钩子里
  `coroutine.yield()` 在主线程上报 `attempt to yield from outside a coroutine`、
  在协程里报 `attempt to yield across a C-call boundary`，两种情况都让程序/协程
  死掉。这就是为什么本实现<b>根本没搬 `CIST_HOOKYIELD`</b>——从 Lua 里走不到
  （上游那个标记是给「由 C API 直接装的 C 钩子」用的，那种钩子不走 `lua_call`）。

#### 由钩子这道闸门抓出来的一个顺序 bug

`allowhook` 必须在<b>保护调用边界</b>上恢复，而且是<b>在消息处理函数跑完之后</b>，
不是在它之前。处理函数是在抛出点跑的，那时 `allowhook` 还是 0（正是这一点让回溯
不会重新进钩子）。提前恢复成 1，回溯就会再触发一次钩子、再抛一次错，最后被
`applyErrMsg` 兜成 `error in error handling`。症状是
`debug.sethook(function() error("x") end, "c")` 之后那个未捕获错误打出来的是
`error in error handling`，而不是真正的回溯。

#### 阶段 9 里由参考实现裁定的五件事

* **层号 0 是 `getinfo` 自己的 C 帧。** `lua_getstack(L, 0, &ar)` 会成功，
  给出 `what = "C", currentline = -1, name = "getinfo"`。名字来自
  `funcnamefromcall` 读<b>调用者</b>那一帧，所以它回答的是「调用者当时在调谁」，
  不是「这一帧叫什么」。搞错一处，所有 `debug.getinfo(n)` 整体错一层。
* **`'f'` 与 `'L'` 要 `lua_rotate`，不能弹。** 结果表是 `lua_getinfo` <b>之后</b>
  才压的，所以 `lua_getinfo` 自己的产物在它<b>底下</b>。
  `treatstackoption` 是 `lua_rotate(L, -2, 1)` 再 `lua_setfield(L, -2)`。
  写成 `L.top--` 弹掉的是结果表本人——症状是 `debug.getinfo(1)` 返回一个
  打印出来像函数的东西，而 `debug.getinfo(1).func` 里存着那张表。
* **`pushfuncname` 先试<b>全局</b>名。** `error` 在 `getobjname` 眼里是
  `global 'error'`（一条 `GETTABUP _ENV`），但回溯打的是 `function 'error'`——
  `pushglobalfuncname` 在 `_LOADED._G` 里按<b>值</b>反查到了它，并且优先。
  只看 `ar.namewhat`，每个有全局名的 C 函数的修饰语都会错。
* **消息处理函数在<b>栈还没退</b>的时候跑** —— `luaG_errormsg` 是在抛出点调它的，
  不是在 `luaD_pcall` 的恢复里。这就是 `xpcall(f, debug.traceback)` 能打出完整
  回溯、而不是只剩最外面两层的原因。Java 没有 `longjmp`，但 `catch` 到的那一刻
  <b>Lua 栈</b>（`stack` / `top` / `ci`）还是完好的，所以把处理函数放在
  `pcall` 的 `catch` 最前面跑是等价的。
* **`LEVELS1` / `LEVELS2` 是 10 和 11**，不是整整齐齐的 10/10，头尾也不等长。
  否则「(skipping N levels)」那个数差 1——而且只在深到会触发它的栈上才暴露。

#### 由 `debug` 这道闸门抓出来的两个 bug，都不在 `debug` 里

* **`OP_TESTSET` 的判条件写反了，于是 `and` / `or` 是错的。** 上游判
  `l_isfalse(rb) == k`，这里判成了 `toBoolean() == (k != 0)`——这两者在 `k`
  的两个取值上恰好相反。它<b>静默</b>：`TESTSET` 只在「结果寄存器 ≠ B」时才
  保留下来（`patchTestReg` 否则把它改写成 `OP_TEST`），于是
  `if x then` 一直是对的，`x and y` 一直是错的。症状是
  `print(t and "yes")` 打印出那张表；更糟的是 `cond and a or b`——Lua 里最常用的
  惯用法——整体取错分支。**字节码闸门看不见它**：两边的字节码逐字段相同。
  `RuntimeTest.logicalOperatorsReturnValues` 现在钉住的是返回的<b>哪个值</b>，
  不只是真假。
* **`xpcall` 的处理函数跑在调用链已经恢复之后。** 见上面第四条：
  `xpcall(f, debug.traceback)` 的回溯只有两层，应该是七层。

#### 一处由参考实现裁出来、但属于「驱动方式」而不属于引擎的差异

`lua.c` 是在 `pmain`——一个 <b>C 函数</b>——里面跑脚本的，所以主 chunk 底下多一个
C 帧。它表现为每次回溯末尾那行 `[C]: in ?`，以及
`debug.getinfo(3).what == "C"`。宿主直接 `luaL_dostring` 时没有这一帧，两者都
看不到。所以这是修在差分驱动上的（`Diff` 现在照 `lua.c` 搭：`pmain` + `docall`
+ `msghandler`），不是修在引擎上——为了让引擎凑上它，反而会让 `luaL_dostring`
变成错的。

### 阶段 10 —— 把 `lua-5.4.x/tests` 端到端跑绿

第四道闸门里最重的一块：`UpstreamSuiteTest` 原样跑上游那套 `.lua`，输出与参考
二进制<b>逐字节</b>比；`UpstreamSmokeTest` 收那些「不该逐字节比、但必须跑完」的。

分到后者的四个文件，理由各自不同，都写在测试类的注释里：

| 文件 | 为什么不能逐字节比 |
|---|---|
| `cstack.lua` | 每个用例打印的 `final count` 是「一层嵌套吃掉多少 C 栈」，Java 与 C 必然不同 |
| `gc.lua` | 整篇在测增量收集器的 `count` / `step` / `setpause`，本实现没有这一层 |
| `literals.lua` | 末尾要 `os.setlocale("pt_BR")` 真的生效，JVM 设不上 |
| `locals.lua` | `tracegc` 用 `io.stderr:write'.'` 在终结器里写进度点，终结时机由 JVM 定 |
| `constructs.lua` | `_ENV.GLOB1 = math.random(0, 1)`（第 319 行），两个进程的种子不可能一样 |
| `sort.lua` | 打印排序耗时与比较次数，而待排数据是 `math.random` 生成的 |
| `math.lua` | 打印 `random seeds`、随机数的取值范围与耗时 |

#### 本阶段由参考实现裁定的事

* **出错之后 `__close` 里还能 yield。** `lua_pcallk` 在 `yieldable(L)` 时
  <b>不建本地恢复点</b>：错误一路冒到 `lua_resume`，由 `precover` →
  `finishpcallk` → `luaF_close(..., yy = 1)` 收尾，所以每个 `__close` 都能
  yield，而 `__close` 自己抛的新错误要<b>顶掉</b>旧的、接着传给下一个还没关的
  变量。上游 `locals.lua` 那段「yielding inside closing metamethods」里，三个
  `<close>` 变量依次 yield，中间那个 yield 完再 `error(err + 20)`，于是第三个
  看到的错误对象和 `pcall` 最终拿到的是 30 而不是 10。
  本实现用 Java 异常代替长跳转，恢复点落在 `pcall` 的 `catch` 里，因此
  `yy` 得显式传下去：`pcall` 按 `yieldable()` 决定，正好对上
  `k != NULL && yieldable(L)`。
* **yield 打断收尾之后，错误对象得有人接着保管。** C 版它一直在栈上
  （`luaD_seterrorobj` 每关一个变量就重新写一次），`finishpcallk` 从
  `L->top - 1` 取即可；本实现的错误对象住在异常里，yield 一抛就跟着 Java 栈
  没了，只能暂存在 `LuaState.closeErrPending` 上，等下一次 `finishPcallK` 来取。
  它必须<b>让位给新错误</b>——否则 `y` 的 `__close` 先 yield 再 `error(30)` 时，
  `z` 看到的还是旧的 10。
* **`luaB_error` 必须照 C 的形状写**，连那句 `lua_settop(L, 1)` 都不能省：它决定
  「栈顶下面一格是什么」，而 `luaD_seterrorobj` 的 `default` 分支读的就是那格。
  少了它，`coroutine.wrap(...)` 抛出的错误会丢掉 `file:line:` 前缀。
* **`luaL_where` 与 `luaG_addinfo` 对「没有调试信息」给的是相反的答案**。前者要
  `currentline > 0` 否则返回空串，后者照样打 `?:-1:`。合成一个会同时弄坏
  `error("x")` 与 `errors.lua` 里那条 `^%?:%-1:` 断言。
* **`collectgarbage` 要先校验选项**，且 `"generational"` / `"incremental"`
  返回的是<b>切换前的模式名</b>（`lbaselib.c: pushmode`），不是整数。
* **`luaD_shrinkstack` 的 `stackinuse` 取 `top` 与所有 `ci->top` 的最大值**，不是
  只看 `top`；`stackErr` 也只在真的缩了栈时才清。
* **主线程要留一格起手**（`stack_init` 给 `base_ci.func` 预留），`IoLib` 建标准
  文件后要 `lua_pop`，`Diff` 要照 `lua.c: docall` 把 `msghandler` 压栈——
  三处各差一格时，`errors.lua` 的「expected stack overflow after N calls」
  就会偏一。

#### 把 `sort.lua` / `math.lua` 跑完时又挖出来的四个真缺陷

这两个文件原来一进来就崩，修完才跑得到 `OK`。四个都不是「不一致」，是本实现
写错了：

* **`tunpack` 的越界判断要按无符号比。** C 版 `n` 是 `lua_Unsigned`，
  `e - i` 溢出时回绕成一个巨大的数；Java 的 `long` 回绕成<b>负数</b>，照有符号
  比就漏过去，然后循环从 `minI` 一路 push 到 `maxI`，把 `table.unpack` 撑成
  `OutOfMemoryError`。
* **`checkStack` / `checkStackOk` 里的 `top + n` 会整型溢出。** `n` 一下子来个
  `2^31` 时和变成负数，「超过 `MAXSTACK`」的判断反而失效——同样是一路
  `OutOfMemoryError`。改成 `long` 算。
* **`tmove` 的判据不能化简。** C 版是 `e < LUA_MAXINTEGER + f`，不是
  `e - f < LUA_MAXINTEGER`：两者在无溢出时等价，但这里<b>判的就是溢出</b>。
  化简后 `table.move({}, minI, maxI, 1)` 报的是 `#4 (destination wrap around)`，
  参考实现报 `#3 (too many elements to move)`。
* **`luaG_tointerror` 要带 `varinfo` 后缀。** `math.huge << 1` 参考实现给的是
  `number (field 'huge') has no integer representation`。
* **`tonumber` 不能用 `String.trim()`。** Java 的 `trim` 去掉的是
  「小于等于 U+0020 的字符」，<b>连 `\0` 一起去掉</b>；C 版只认
  `SPACECHARS`（`" \f\n\r\t\v"`），并且要求解析指针正好走到 `s + l`。于是
  `tonumber('1\0')` 与 `tonumber('1\0', 2)` 双双被算成 `1`。
  `b_str2int` 现在整个改在字节上做，并回到长度上判「吃完了没有」。

### 还没补上的

语言与十个标准库本身没有缺项，上游套件里能逐字节比的那些也都比过了。剩下的是
<b>验证的广度</b>：更多差分脚本、更大的 fuzz 语料。上游用例里没进表的只剩
`all.lua` / `main.lua`（总驱动，要 `ltests.c` 编出来的 `T` 模块，还写
`time.txt`）、`attrib.lua` / `files.lua` / `big.lua`（要 `libs/` 里那几个 C
模块，以及 `all.lua` 建的全局环境——参考实现自己单跑也是失败）、`heavy.lua`
（要跑好几分钟）、`bwcoercion.lua`（单跑没有输出）。

---

## 从旧 fakescript 语言迁移

这是一次**破坏性重写**。旧 fakescript 语法整体移除：`package`、`include`、
`struct`、`const`、`->` 式 `for`、`array()`、`map()`、`fake` 协程、`:=`、
Java 绑定注解、以及性能剖析器。

既有的 fakescript 脚本需要改写成 Lua——而这正是重点：改完之后跑的是 Lua 5.4，
脚本可以在任意其他 Lua 5.4 实现之间来回移植。仍需要旧语言的用户请锁定到
重写前的最后一个提交。

---

## 构建

```bash
./mvnw clean test          # 需要 JDK 21+
```

机器上没有 `lua5.4` 二进制时，差分测试会自己跳过（`Diff` 里一个 `assumeTrue`
守卫），覆盖率门禁跟着一起跳过——差分套件占了行覆盖的绝大部分，在它们全被跳过
的情况下还跑门禁，只会因为一个不相干的理由一直红。要拿到真正的门禁，设
`LUA54_BIN`，或者把参考实现编到 `target/lua54/src/lua`：

```bash
LUA54_BIN=/path/to/lua5.4 ./mvnw clean test
```

CI 从源码编参考实现并导出 `LUA54_BIN` 与 `FKDUMP_BIN`，所以这只影响本地没有
参考二进制的构建。

---

## 许可证

MIT
