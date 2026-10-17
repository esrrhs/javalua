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
全量 850 个用例绿，JaCoCo 行覆盖 0.90（门禁 0.89）。（上游套件这道闸门要
`LUA54_TESTS` 或 `target/lua54-tests`；没有用例目录时<b>整类自动跳过</b>，
所以那种机器上「绿」其实比看起来少 25 个用例。）

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

### `io` 里四处承认下来、不去修的偏离

* **`file (%p)`。** `f_tostring` 打的是指针，`print(io.stdout)` 无论如何也不
  可能逐字节相同。`IoLib` 给一个同形状的十六进制值，差分比对把 `0x…` 抹掉。
* **`stdin` 的定位。** 参考实现在测试里的 stdin 被定向到 `/dev/null`，那是
  **可定位**的字符设备（`lseek` 返回 0）；本实现这边是字节数组，定位不了。
  `io.stdout:seek()` 两边一致（管道上是 `nil, "Illegal seek", 29`）；
  `io.stdin:seek()` 不比对——差的是流的来源，不是 `io` 的行为。
* **更新模式上「读完不定位直接写」。** 字节落到哪里由 C 的预读缓冲定，ISO C
  里是未定义行为。实测：12 字节的文件落到 13、6002 字节的落到 4097、
  `read(5000)` 之后落到 5001。本实现写在逻辑位置上。良定义那条路（中间有
  `seek`）两边一致。见下文 *`io.open` 的模式矩阵*。
* **append 档写完之后的<b>位置</b>。** 数据在缓冲里时参考给 `base+n`，压掉
  缓冲后给 `EOF+n`（分界是 4096）；本实现一律给 `EOF+n`（这一边符合 POSIX）。
  **写进去的内容两边始终一致**。见同节。

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

#### 还有一个：`table.sort` 选枢轴要用<b>无符号</b>算术

`sort.lua` 在冒烟组里（它打印耗时，输出天生不可逐字节比），后来一次全量跑挂在
`table.sort` 抛出的 `attempt to compare nil with number`。这个文件不播种，
每次 `math.random()` 给的都是新数据，所以这是个<b>潜伏</b>缺陷：只有当某次分区
严重失衡、`auxsort` 去换新的随机种子时才会露出来。

* **`IdxT` 是 `unsigned int`，枢轴种子也是。** C 的 `rnd % (r4 * 2)` 是无符号
  取模，恒在 `[0, r4*2)`；Java 的 `int` 有符号，而种子恰好就是 C 会生成的那
  32 个比特——一半概率为负。有符号取模会把 `p` 拖到 `lo` 以下，极端时拖到 `1`
  以下：`a[p]` 是 nil，`sort_comp` 就报
  `attempt to compare nil with number`。现在用 `Integer.remainderUnsigned`。
* **`rnd` 是按值传的。** C 里它是 `auxsort` 的形参，递归调用里重新随机不会漏回
  外层帧。早先这里用 `int[]` 共享，于是在某个子树深处抽到的种子会一路影响整个
  排序剩下的所有分区。

「二十次跑挂一次」是很糟的找 bug 方式，所以 `choosePivot` 现在有一条自己的
确定性用例（`TableLibTest.choosePivotStaysInsideTheInterval`），直接验 C 版那条
`lua_assert`——`lo + r4 <= p <= up - r4`——而不再指望五万元素的排序碰巧踩中。

#### 阶段 11：预编译 chunk 要从<b>每一道门</b>进得来

`ldo.c: f_parser` 先读一个字节：是 `LUA_SIGNATURE` 的首字节就走 `luaU_undump`，
否则才进 `luaY_parser`。就这一步，调用方<b>完全不必声明</b>自己给的是源码还是
字节码——`load` / `loadfile` / `dofile` / `require` 因此全都天然支持预编译 chunk。

这个分派原先只存在于 `BaseLib` 的 `load` / `loadfile` 里，`lua_load` 本身
（`LuaState.load`）不看签名，于是 **`require` 一个预编译模块会失败**：

```
error loading module 'mod' from file '/tmp/mod.lua':
	/tmp/mod.lua:1: unexpected symbol near '<\27>'
```

参考实现能加载并正常返回模块。`PackageLib` 走的是 `LuaState.loadFile`，所以这个
缺陷出现在 `require` 而不是 `load`。修法落在 `lua_load` 那一层，不是给 `require`
打补丁：

* `LuaState.load` / `loadFile` 现在按 `Lua.LUA_SIGNATURE0` 分派；
* `loadFile` 仍然先过 `lauxlib` 那段前奏（BOM、`#!`）——预编译 chunk 的首字节
  不会是 `#`，所以顺序不能反；
* 二进制分支<b>不回退</b>首字节：`luaU_undump` 的注释是
  「skip 1st char (already read and checked)」，`checkHeader` 从第二个字节比。

`BinaryChunkTest` 两个方向都验：

* **真实 `luac` 编出来的 chunk**（`ReferenceLua.compile`，按 `LUAC54_BIN` 或
  参考 `lua` 同目录找），两边跑同一份产物逐字节比。自己编给自己判卷等于没判，
  所以产物必须出自参考实现的编译器。语料覆盖 upvalue、变长参数、`goto`、
  混合表构造、整数与浮点边界、协程、300 项的常量表，另有两条是<b>故意报错</b>
  的——错误消息里带着行号与局部变量名，undump 丢了调试信息就会红；
* **本实现自己 `string.dump` 出来的 chunk**（`require`、`dofile`、`loadfile`、
  `mode` 参数、`binary string` 源名规则，以及被截断与被篡改的 chunk——那些必须
  是加载期错误而不是崩）。

#### 附：fuzz 语料有 89% 是死的

生成器一直在产出**编译不过**的程序：

* 表构造写成 `{'key0' = v}`。只有**名字**能裸着当键，字符串键必须写成
  `[k] = v`——数字键分支加了方括号，字符串键分支没有；
* `expression` 无条件生成 `(select('#', ...))`，而函数只有 40% 的概率声明
  `...`。非变长函数里写它就是
  `cannot use '...' outside a vararg function`。

拿参考的 `luac` 量了一下：**200 颗种子里 178 个编不过**。编不过的程序在两边是
**同一条编译错误**，比对照样「一致」——这道最硬的闸门在几万颗种子上静默地什么
也没测。修完（键一律加方括号；生成器跟踪「当前函数在生成的是不是变长」）复测：
0 / 400。

`FuzzTest.assertCompiles` 让这个状态不可能再出现：参考实现的输出只要是编译期
错误的形状，那颗种子直接判失败。判据是「消息以 `near 'tok'` 收尾」而不是
「含 `expected`」——运行期错误里到处是 `expected`
（`bad argument #1 to 'upper' (string expected, got nil)`），而那些是**期望之中**
的。

#### 顺带把字节码那条路也 fuzz 上

常规 fuzz 走的一直是文本路径，`Dump` / `Undump` 只被几十条手写用例碰过——
「require 加载不了预编译 chunk」恰好就在这个盲区里。
`generatedChunksSurviveDumpAndUndump` 补上：把生成的程序体包进变长函数，
`string.dump` 出来再 `load` 回来跑。于是几千个**随机的** `Proto` 被推过序列化：
常量表顺序与变长编码、行号表、局部变量与 upvalue 的调试名、`maxstacksize`、
嵌套 proto。另有一条哨兵输出（`dumped true loaded true`）被断言，
`string.dump` 静默失败没法再伪装成一致。

#### 参考实现又裁定的五件事

fuzz 生成器学会了 `table.*`，立刻回本：五处不一致，没有一处是手写用例撞得到的。

* **`l_str2int` 的累加器是 `lua_Unsigned`。** `lobject.c` 里那个 `a` 是无符号的，
  溢出判据是 `a >= MAXBY10`。用有符号 `long`，`a` 一旦过 2^63 就变负，判据从此
  不再触发，溢出就漏了：`1 + (math.mininteger .. 0)` 的操作数是
  `"-92233720368547758080"`，绕回成精确的 0，于是返回 `1`，而正确行为是落到
  浮点路径给 `-9.2233720368548e+19`。改成 `Long.compareUnsigned`。
* **`luaG_runerror` 是<b>当场</b>加 `file:line:` 的。** `idiv` / `mod` 的除零走
  `luaG_runerror`，它在抛出点就调 `luaG_addinfo`。我们抛的是裸 `LuaError`，
  前缀要等到受保护边界才补上，那时抛出的栈帧已经没了——所以
  `coroutine.resume` 把错误当<b>值</b>返回时它是没有位置的：
  `false  attempt to divide by zero`，参考实现给的是
  `false  file:line: attempt to divide by zero`。为此 `Arith.rawArith` 现在收一个
  `LuaState`。
* **`luaH_newkey` 拒绝 nil 值。** 它的第一行是
  `if (ttisnil(value)) return; /* do not insert nil values */`。给一个不存在的键
  赋 nil，不建节点、也不触发 rehash。而建了节点就会改变算出来的<b>数组部分
  大小</b>，那是 `#t` 的直接输入：`table.move({[1]=4.0}, 0, 2, 1)` 之后参考实现
  的 `#t` 是 `2`，建了节点就成了 `0`。nil 键与 NaN 键的检查在<b>那一行之前</b>，
  所以 `t[nil] = nil` 仍然要报 `table index is nil`。
* **`lauxlib.c: interror` 判的是 `lua_isnumber`，不是类型。**
  `luaL_checkinteger` 取不到整数时，若 `lua_isnumber(L, arg)` 成立（能解析成
  数字的字符串也算），报 `number has no integer representation`，否则才是
  `number expected, got string`。于是 `string.format("%d", "3.5")` 是前者
  （`"3.5"` 解析得成 3.5，只是 3.5 不是整数），
  `string.format("%d", "abc")` 才是后者。原来的判据写成 `v.isNumber()`，
  两条都成了后者。
* **`%.14g` 只能舍一次，而且要从真值舍。** Java 的
  `String.format("%.Nf")` 舍的不是真值，而是 `Double.toString` 的<b>最短表示</b>
  ——等于连舍两次。`math.sqrt(math.maxinteger)` 的真值是
  `3037000499.976049900054931640625`，最短表示是 `3037000499.97605`，第 15 位
  是 5，第二次舍入就进位成 `3037000499.9761`；参考实现给
  `3037000499.976`。`StringLib` 本来就是用 `BigDecimal` 的
  （`new BigDecimal(double)` 是精确的二进制展开），`luaO_tostring` 却另写了一份
  基于 `String.format` 的。同一个 C 转换有两份实现，正是它们分家的原因，
  现在 `Vm.formatG` 直接转调 `StringLib.generalDigits`。

最后一条值得单独说清楚：拿 4602 个浮点实测，旧实现错了 **12** 个（0.26%）。
手写用例永远撞不到，上游套件全绿也撞不到——是 fuzzer 撞到的。
`RuntimeTest.floatFormattingMatchesReferenceAcrossMagnitudes` 现在用脚本<b>内部</b>
的 LCG 生成几千个值钉住它，随机数发生器本身不进比较范围。

#### 把语料拓宽之后又咬出两处

生成器接着学会了 `utf8.*`、`gmatch`、`next`、`string.pack`/`unpack`、
`table.pack`、非字符串错误值、`math.ult`/`fmod`/`max`/`min`、
`rawequal`/`rawlen`/`select`。信它之前先量了一遍：每条新分支在 6%–29% 的程序里
出现，语料确实是活的。立刻又咬出两处，都在 `ltable.c` —— 前五处里也有三处在
这个文件。

* **`luaH_getn` 是先答 `limit-1`，根本不二分。** 手册只要求 `#t` 返回<b>某个</b>
  边界，这让「我们找到了一个合法边界」听起来就够了。<b>不够</b>：真实程序看得见
  具体是哪一个。`ltable.c` 为最常见的一串操作（`t[#t] = nil` 之后马上再取长度）
  专门留了快路径：

  ```c
  if (limit > 0 && isempty(&t->array[limit - 1])) {
    if (limit >= 2 && !isempty(&t->array[limit - 2]))
      return limit - 1;          /* (1a) 不去二分 */
    else { /* (1b) */ ... binsearch ... }
  }
  ```

  数组 `[1, nil, 3, nil]`：二分给 `1`（它找的是<b>第一个</b>边界），参考实现给
  `3`。`table.pack(1, nil, 3, nil)` 之后 `#t` 是 `3` 不是 `1`。系统性地铺开各种
  空洞（单个空洞到 `n = 32`、空洞对到 `n = 19`、三空洞到 `n = 11`、
  `table.pack` 的全部 32 种 nil 组合）—— **4968 条结果里错 212 条（4.3%）**。

* **`luaH_resize` 必须允许哈希部分缩成 dummy。** `rehash` 之后 C 的 `reinsert`
  用 `luaH_set` 搬旧数据，整数键只要 `<= asize` 就被路由进新数组部分，<b>不占
  哈希槽</b>；所以 `luaH_resize(L, t, 4, 0)` 结束时哈希部分是 dummy（0 槽）。
  这里早先按「所有搬过去的条目」撑哈希，于是数组 4 配着 2 个用不上的哈希槽。
  要命的是 `luaH_newkey` 的判据里有 `isdummy(t)`：哈希不是 dummy 时，下一次插入
  直接占空槽、<b>不再 rehash</b>，数组部分就再也长不大。
  `t = {}; t[2]=2; t[3]=3; t[4]=4; t[6]=6; t[7]=7`，参考实现数组部分 8、
  `#t` 是 7，这里是 4 和 4。上面那份空洞扫描正是把它挖出来的 —— 旧语料只会按
  `1..n` 顺序插入，永远走不到这条路。

两处都由 `RuntimeTest.tableLengthBoundaryMatchesReference` 钉住：空洞图案在脚本
<b>内部</b>用循环铺开（用 LCG 会把随机数发生器本身也拉进比较范围），每张表把
`#t` 读两次，因为 C 会在第一次调用时顺手把 `alimit` 改成一个提示。

#### 把语料搬进协程里跑，又咬出一处

同一批语料后来也塞进 `coroutine.create` 里跑了一遍，咬出一个跟协程其实没关系的
bug：

> 协程里的 `pcall(math.random, 5, 1)` 报
> `bad argument #1 to '?' (interval is empty)`，参考实现报
> `bad argument #1 to 'math.random' (interval is empty)`。

* **`_LOADED` 是 `global_State` 级的，不是每线程一份。** `lauxlib.c` 的
  `pushglobalfuncname` 会拿当前函数去 `package.loaded` 里反查两层，把 `'?'`
  换成真名：

  ```c
  static void pushglobalfuncname (lua_State *L, lua_Debug *ar) {
    ...
    lua_getfield(L, LUA_REGISTRYINDEX, LUA_LOADED_TABLE);
    if (findfield(L, L.top - 1, 2)) { ... }
  ```

  `LUA_LOADED_TABLE` 是 <b>registry</b> 的字段，而 registry 属于 `global_State`
  —— 所有 `lua_State` 共用同一个。这里早先把 `loaded` 做成了每个 `LuaState`
  一张，于是协程里的 `package.loaded` 是空的，协程内的每条诊断都退化成 `'?'`。
  修法只有一行：`loaded = main.loaded`。注意它<b>只在协程里</b>现形——主线程
  自己会填自己的那张表，所以整个上游套件和此前所有 fuzz 种子对此都一声不吭。

由 `RuntimeTest.libraryFunctionNamesResolveInsideCoroutines` 钉住。

#### 闸门本身有个洞

对 `string.format('%q', ...)` 的系统性扫描（0–255 每个字节、四种位置）是第一个
往 stdout 里写**非 UTF-8 字节**的探针。引擎本身没查出问题，倒是把探针自己查了
出来：两边都把捕获到的输出按 UTF-8 解码成 Java `String`。任何非法字节都会变成
`U+FFFD`，于是 `0x80` 与 `0xEF 0xBF 0xBD` 判成<b>相等</b>：

```
>>> b'"\x80"\n'.decode('utf-8', 'replace') == b'"\xef\xbf\xbd"\n'.decode('utf-8', 'replace')
True
>>> b'"\x80"\n'.decode('latin-1') == b'"\xef\xbf\xbd"\n'.decode('latin-1')
False
```

不是推理，是量的：把 `print` 故意改成经 Java `String` 往返一趟（于是每个非
UTF-8 字节都被写坏），新的 `%q` 扫描在 Latin-1 下<b>红</b>、在 UTF-8 下<b>绿</b>
—— 旧闸门放行了真实的字节级损坏。闸门现在按 Latin-1 解码（字节↔字符一一对应），
比对才算真的逐字节。钉子：
`RuntimeTest.quoteFormatMatchesReferenceForEveryByte`。

#### 把运算符和类型铺成矩阵，又咬出一处

下一个探针是全矩阵：每个运算符 × 每对类型，无元方法，比<b>报错文本</b>，1651 行。
第二个探针同样铺一遍但带上元方法 —— 两个 handler 谁先走、`__le` 回退成
`not (b < a)`、`__index`/`__newindex` 的表与函数两种形态、`__concat` 的右结合、
`__close` 的时机与错误传递，245 行。第二个干净，第一个咬出这一处：

* **`luaG_tointerror` 用的是 `F2Ieq`，不是「取整到整数」。**
  `lvm.h` 里 `LUA_FLOORN2I` 默认就定义成 `F2Ieq` —— 名字叫 floor，语义是
  「只接受整数值」：

  ```c
  void luaG_tointerror (lua_State *L, const TValue *p1, const TValue *p2) {
    lua_Integer temp;
    if (!luaV_tointegerns(p1, &temp, LUA_FLOORN2I))
      p2 = p1;          /* 左边转不成整数就报左边 */
    luaG_runerror(L, "number%s has no integer representation", varinfo(L, p2));
  }
  ```

  按「取整」的模式判，`1.5` 会被当成能转，于是 `1.5 & 3` 去报<b>右边</b>的
  `3`：`number (local 'b') has no integer representation`；参考实现报左边
  `(local 'a')`。矩阵里错 10 行。手写的用例只用 inf/nan 这两个极端值，两种模式
  在它们上面结论一样 —— 所以此前每道闸门都放它过去了。

两个矩阵都固化成了用例：`RuntimeTest.operatorTypeErrorsMatchReference` 与
`RuntimeTest.metamethodDispatchMatchesReference`。

#### 把格式串铺成矩阵，又咬出两处

`lstrlib.c: str_format` 是源码里分支最密的函数之一，所以下一个探针就照着它的每个
分支铺：22 个动词 × 10 组修饰符 × 5 种宽度 × 9 种精度 × 22 种值类型 ——
**20777 行**。咬出两处：

* **两道校验的先后。** `str_format` 并<b>不</b>先校验格式串。`d i u o x X` 是
  <b>先</b> `luaL_checkinteger`，`f e E g G` 是<b>先</b> `luaL_checknumber`：

  ```c
  case 'd': case 'i':  flags = L_FMTFLAGSI;  goto intcase;
  ...
   intcase: {
    lua_Integer n = luaL_checkinteger(L, arg);
    checkformat(L, form, flags, 1);
  ```

  （`c`、`a`、`s` 才是先校验格式串。）这里一律先校验格式串，于是
  `("%.100d"):format("x")` 报 `invalid conversion specification`，参考实现报
  `number expected, got string` —— **错 6620 行**。

* **整数动词给了精度时，`0` 标志失效。** C99：「For `d`, `i`, `o`, `u`, `x`, and
  `X` conversions, if a precision is specified, the `0` flag is ignored」。
  所以 `("%05.3d"):format(42)` 是 `  042` —— 精度先补成 `042`，宽度再用<b>空格</b>
  补，不是 `00042`。错 220 行。浮点动词没有这条规则（`%08.3f` 照旧补零），
  所以改动只落在 `emitInt` 里。

两处都由 `RuntimeTest.formatSpecifierMatrixMatchesReference` 钉住：单独回退任何
一处，该用例就变红。

#### `luaL_checkinteger` 的第二份实现

下一片扫描是 `os.date` 的每个说明符、`os.time` 的参数校验、`utf8.*` 的边界、
`tonumber` 的 2–36 全进制，854 行。咬出一个老形状的 bug：
**`luaL_checkinteger` 有两份实现**，`BaseLib` 里那份少两样东西 —— 函数名前缀
（`bad argument #2 (...)` 而不是 `bad argument #2 to 'tonumber' (...)`），
以及「无整数表示」那一档（`1.5` 报成了 `number expected, got number`）。
四个调用点全受影响：`tonumber` 的进制、`select` 的下标、`error` 的层号、
`ipairs` 迭代器的下标。现在统一转调 `LuaAux.checkInteger`。

同一批扫描还翻出第二个「不是我们错、是 libc 不一致」的地方：
**`os.date('!%z', t)`**。C 的 `strftime` 读的是<b>进程全局时区</b>，不是 Lua 传给
它的那个 `tm`；带 `!`（UTC）时两个 libc 答案相反 —— macOS 给本地偏移 `+0800`，
glibc 给 `+0000`。所以用例只测<b>不带</b> `!` 的 `%z` / `%Z`：两边同一台机器，
结果是确定的。

`string.pack` / `unpack` / `packsize` 也整套扫了一遍 —— 全部格式动词（含
`i`/`I` 的 1–16 字节宽度、`c`/`s` 的计数、`X` 与 `!n` 对齐、四种字节序）×
各类型的值，外加非法格式串与偏移越界，706 行。这批是干净的，也收成了用例：
把「<b>已经</b>一致」这件事钉住，否则下次动 pack 那片分支时没有一条用例会说话。

#### 有一处<b>不是</b>分歧：次正规数的 `%a`

`quotefloat` 走的是 C 的 `%a`：

```c
int nb = lua_number2strx(L, buff, MAX_ITEM, "%" LUA_NUMBER_FRMLEN "a", n);
```

C99 只规定小数点左边恰好一位十六进制数字，并且「这个数字只在数<b>次正规</b>时为
0」—— 次正规<b>具体怎么写是 unspecified</b>。glibc 与 Apple libc 选的正好相反：

| 值 | glibc（CI 的 ubuntu） | Apple libc / musl |
| --- | --- | --- |
| `5e-324` | `0x0.0000000000001p-1022` | `0x1p-1074` |
| `3 * 5e-324` | `0x0.0000000000003p-1022` | `0x1.8p-1073` |

这里取 **glibc**：真正作数的闸门（CI）跑在 `ubuntu-latest` 上。
`StringLibTest.subnormalQuoteFollowsGlibc` 用普通断言在差分表<b>之外</b>钉住它，
免得有人照 macOS 上的参考实现「修」一把反而把 Linux 上本来对的行为改错。
（`Double.toHexString` 给出的正是 glibc 那种写法，所以不需额外实现。）

#### 把 `table` 库铺开扫：咬出 `tmove` 少了一个条件

`table` 矩阵 —— `insert`/`remove`/`move`/`concat`/`unpack`/`pack`/`sort` 的
位置 × 表大小 × 偏移 × 值类型，含同表重叠 `move` 与跨表 `move`，**6277 行** ——
跑出 268 行分歧，而且每一行都是同一个让人心里发毛的形状：**表内容完全一样，
只有 `#t` 不一样**。

```text
< MV2-dump  #0  -2=nil,-1=nil,0=nil,1=nil,2=1,3=nil,4=nil,...
> MV2-dump  #2  -2=nil,-1=nil,0=nil,1=nil,2=1,3=nil,4=nil,...
```

根因是漏了一个条件。C 判拷贝方向是这样写的：

```c
if (t > e || t <= f || (tt != 1 && !lua_compare(L, 1, tt, LUA_OPEQ))) {
  for (i = 0; i < n; i++) { ... }        /* 正向 */
} else {
  for (i = n - 1; i >= 0; i--) { ... }   /* 逆向 */
}
```

这份实现只写了前两项。第三项跟位置<b>毫无关系</b>：**源和目标是两张不同的表时
一律正向**，与 `t`、`f` 谁大谁小无关。漏掉它，本该正向的情形会走成逆向。

次序不改变表里最终有什么，但改变<b>布局</b>，而 `#t` 读的就是布局。
`table.move({1}, -2, 1, -1, {1})` 之后目标表同样只剩 `[2]=1`：正向先把 `[1]`
挖空，`alimit` 仍是 1，于是 `luaH_getn` 的第一条分支（「`array[alimit-1]` 为
nil 就只在数组部分二分」）成立，答案是 `0`；逆向却先塞 `[2]`，把 `alimit` 顶到
2，那条分支不再成立，答案变成 `2`。于是就有了那个绕人的现象：同样的写入序列，
走 `rawset` 一致、走 `table.move` 不一致——差的只是内部布局。

两条经验落进了测试：

* **光比表内容不够，必须连 `#t` 一起比**，否则布局类的错永远看不见。
* 这一片现在是三个常驻测试：
  `RuntimeTest.tableLibraryMatrixMatchesReference`（6277 行，就是它咬出来的）、
  `RuntimeTest.patternMatchingMatrixMatchesReference`（模式匹配 × 主题 ×
  `init` × 替换形态，24819 行，**干净**，照样钉住，照第 25 条经验：已经一致的
  也得有人替它说话）、
  `RuntimeTest.tableLengthAcrossInsertionHistoriesMatchesReference`
  （3736 行，把<b>插入历史</b>本身当成扫描维度：`1..n` 的全部子集 × 三种插入
  次序、先填满再挖空、`move` 造出的布局、`insert`/`remove` 交替、负键与 0 键、
  二分上到 40、尾部截断——也干净）。

把那一个条件回退，第一个测试立刻变红。

#### `debug` / `load` / `require` 矩阵：咬出 `xpcall` 少了参数校验

下一片：`debug.getinfo` 的 14 个字段 × 0–6 层、`getlocal`/`setlocal`、
upvalue 全家、`traceback`（含协程内）、`sethook` 的 10 种掩码加计数钩子、
`load` 的源码/读取函数/模式/env/二进制块、`package.path`/`cpath`/`config`/
`searchers` 与 `require` 的几种怪角、`error` 的 0–4 层、`xpcall`、`assert`，
以及错误在协程里的传播，289 行。咬出一处：**`xpcall` 把参数校验丢了**。

```c
static int luaB_xpcall (lua_State *L) {
  ...
  luaL_checktype(L, 2, LUA_TFUNCTION);  /* check error function */
```

C 在调用任何东西<b>之前</b>就先校验消息处理函数必须是函数。这份实现只判了
「参数够不够两个」，于是 `xpcall(f, 5)` 不报错，照常调 `f`，`f` 抛错后拿
`5` 当处理函数：

| | 参考实现 | 修之前的本实现 |
| --- | --- | --- |
| `xpcall(f, 5)` | `false  bad argument #2 to 'xpcall' (function expected, got number)` | `true  false  error in error handling` |
| `xpcall()` | `false  bad argument #2 to 'xpcall' (function expected, got no value)` | `false  bad argument #2 to 'xpcall' (value expected)` |

顺带把 `io` 矩阵也铺了：32 种打开模式（含全部非法模式）、`read` 的全部格式与
0–24 字节计数、数字读法、`seek`、`lines`、关闭后再用、
`io.input/output/read/write/close`、追加模式的 `seek` 语义、`r+` 交错、
5000 字节大块、同一文件开两次，167 行，干净。它仍值得留着：只有它碰得到
**errno 那一层** —— `io.open` 失败要返回 `"路径: No such file or directory"`
加错误号 `2`，C 那是 `strerror` 给的，我们得自己对齐。

#### 闸门自己又有个洞：回溯把输出静默截断了

`debug` 矩阵第一版是<b>绿</b>的 —— 而 `xpcall` 那个 bug 还在代码里。这是闸门
失灵，不是代码过关。

`Diff.assertMatches` 的归一化会把第一个 `stack traceback:` 之后的<b>全部内容
砍掉</b>。对「脚本自己崩了」的情形这是有意的：消息在前、回溯在后，回溯本来
就没法逐行比。但当回溯是<b>脚本打印出来的</b>（`debug.traceback()` 的返回值），
它就落在<b>中间</b>，后面脚本打印的东西全被丢掉 —— 这片脚本第 5 段打印了回溯，
后面还有 180 行。

现在加了门禁：回溯块之后只要出现「不以制表符开头的非空行」（回溯自己的每行
都以制表符开头），就直接判失败，并提示改用不剥回溯的 `assertFullMatches`。
既有 13 个用例的输出里带回溯，收紧后的判据（看的是<b>回溯之后有没有内容</b>，
而不是「有没有回溯」）把它们全放过了 —— 那些用例里回溯确实在最后。

这是第二次发现「闸门瞎了、代码没错」（第一次是捕获输出按 UTF-8 解码）。
一个悄悄比得比它声称的少的闸门，比没有闸门更糟：它报绿。

#### fuzz 咬出的一处：错的是<b>驱动</b>，不是引擎

放到 16500 个程序上跑，fuzzer 产出一个程序，栈溢出时参考实现报<b>第 67 行</b>、
我们报<b>第 65 行</b>。引擎没错，是驱动少占了一格栈：

```c
  lua_pushcfunction(L, &pmain);   /* to call 'pmain' in protected mode */
  lua_pushinteger(L, argc);       /* 1st argument */
  lua_pushlightuserdata(L, argv); /* 2nd argument */
  status = lua_pcall(L, 2, 1, 0); /* do the call */
```

`lua.c` 的 `main` 给 `pmain` 传<b>两个</b>实参，栈的初始 `top` 是 3；
`Diff.runLikeLuaC` 只传了一个。少一格会一路带到底：递归多走一层，
最深那一帧就换了个函数，报错行号也就跟着换。

难查在于它<b>不是每次都差</b>：每帧开销是 `k+1`（`k` 为局部变量个数），
深度是 `floor((limit - base) / (k+1))`，基址差 1 只在刚好跨过整除边界时
才改变结果。按帧大小量下来：

| 局部变量 `k` | 0 | 1 | 2 | 3 | 4 | 6 | 8 | 16 | 32 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 修之前是否差一层 | 是 | 是 | 是 | 否 | 否 | 是 | 否 | 否 | 否 |

只挑一个例子试会以为没问题。补上那一格之后九个全对，现在由
`RuntimeTest.stackOverflowDepthAndLineMatchReference` 铺开盯着。

（那片探针还有个陷阱：`local function f() return f() end` 是<b>尾调用</b>，
不占栈，会变成死循环而不是溢出 —— 参考实现实测挂满 5 分钟才被杀。
所以探针里一律写成 `return 1 + f()` 这种非尾形式。）

#### 三片扫下来是干净的

不是每片都能咬出东西。`math` 全函数 × 30 个边界值（含 ±inf、nan、-0.0、
`maxinteger`、`mininteger`、`2^53`、`2^63`、`1/3`）、`modf` 的两个返回值、
`log` 的底、每个函数六类参数错误、播种后的随机序列，加上 `os` 里输出确定的
那部分，共 **1423 行**，干净。照样留在套件里，理由三条：

* **浮点的最后一比特**：`math.sin` 这些走的是 `libm`，`Math.sin` 与之未必
  逐比特一致。默认 `tostring` 只打 14 位有效数字，正好把它盖住 —— 这片证的
  是「确实盖得住」，而不是「从来没看过」。
* **`math.random` 的序列**：xoshiro256** 把它定死了，播种后必须逐个对上。
* **`errno` / `strerror` 那一层**，在 `os.remove` / `os.rename` 上再碰一次。

其中有三处<b>根本没法测</b>：`os.clock`（耗时）、无参 `math.randomseed`
（拿时钟播种）、无参 `os.time`（当前时间）。

#### `string` 库矩阵：咬出两条，外加一处参考实现自己都对不上的

`string` 库<b>非模式匹配</b>的那一半 —— `sub` / `byte` / `char` / `rep` /
`upper` / `lower` / `len` / `reverse` / `format` / `tonumber(base)` /
`tostring`（模式匹配的 pattern 语义另有 24819 行那片单独覆盖）——
铺开 **3921 行**，咬出两条：

**其一，`__name` 走的是 C 字符串语义。**

```c
        int tt = luaL_getmetafield(L, idx, "__name");
        const char *kind = (tt == LUA_TSTRING) ? lua_tostring(L, -1) : ...;
        lua_pushfstring(L, "%s: %p", kind, lua_topointer(L, idx));
```

`lua_tostring` 交出来的是 `const char *`，`"%s"` 又按 C 字符串处理，于是名字
在第一个 `\0` 处被悄悄砍断：`__name = "A\0B"`，参考实现只打印 `A`，我们把整个
`"A\0B"` 都打了出去。Lua 字符串不以 NUL 结尾、C 字符串以 NUL 结尾，这一局是
后者赢。修法是 `LuaString.toCString()`，专供「C 版当 `const char *` 用」的那些
地方。

**其二，`%p` 不补前导零。** 地址写成了 `0x%016x`，参考实现是 `0x7ab30002c0`
这种不补零的样子（macOS 与 glibc 都不补）。

**再有一处是两边根本不能比的**：`"+"` / 空格标志作用在 NaN 上时，macOS 的 libc
不打符号（`nan`），glibc 打（`+nan`）。CI 的 conformance job 在 ubuntu 上跑、
本机在 macOS 上跑，于是**参考实现自己**两次跑出来就不一样。脚本里先把 NaN 的
符号抹掉再比，实现这边保持与 glibc 一致。

#### 顺带发现：参考实现有一处是<b>不确定</b>的

`math` 矩阵第一次跑红了，报的是 `math.atan` 的报错名对不上。查下去才发现
`atan` 与 `atan2` 在 `lmathlib.c` 里是<b>同一个</b> `math_atan`：

```c
  {"atan",  math_atan},
  ...
#if defined(LUA_COMPAT_MATHLIB)
  {"atan2", math_atan},
```

报错里的函数名由 `lauxlib.c: findfield` 遍历 `package.loaded` 时**先撞见谁**
决定，而那个顺序由字符串哈希决定，哈希又被 `luai_makeseed`（按时间）播种：

```
$ for i in 1 2 3; do lua -e 'print(pcall(math.atan))'; done
false  bad argument #1 to 'math.atan2' (number expected, got no value)
false  bad argument #1 to 'math.atan2' (number expected, got no value)
false  bad argument #1 to 'math.atan'  (number expected, got no value)
```

八次里六次 `atan2`、两次 `atan`。**参考实现不确定，我们确定**（哈希不按时间
播种），这是有意保留的偏离。把 `math` / `string` / `table` / `os` / `io` /
`coroutine` / `debug` 七个库<b>所有</b>函数的零参报错名各跑八遍扫过，
`atan` 是唯一会变的一个；脚本里统一成 `atan` 再比。

#### 元方法与「参数缺失」矩阵：同一个根因咬出七处

元方法语义 × 变量来源推断 × 参数缺失，**2025 行**。前两片是干净的：
19 个二元操作符 × 9 类值、`-` / `#` / `~` / `not`，以及
`global` / `local` / `upvalue` / `field` / `method` / `constant` / 变长参数
这七种上下文里同一句报错带的不同后缀 —— 那部分本来就对。

咬出的全在第三片：六十来个库函数各用「不传 / `nil` / 数字 / 字符串 / 表 /
布尔」调一遍。根子就一个 —— **缺的参数读到的是残值**。

```java
public LuaValue arg(int i) {
    return stack[ci.func + 1 + i];   // 越界？读到的是上一帧留下的东西
}
```

C 里 `lua_type` 对越界下标返回 `LUA_TNONE`、取值拿到的是 `nil`；我们的栈是
**复用的数组**，越界读出来的是上一帧留下的东西。于是：

| | 参考实现 | 修之前 |
| --- | --- | --- |
| `pcall(setmetatable)` | `(table expected, got no value)` | `got function` / `got nil`（看那一刻槽里是什么） |
| `assert()` | `false  bad argument #1 to 'assert' (value expected)` | `true`（残值为真 → 返回 0 个值） |
| `ipairs()` | `(value expected)` | `true  function 0 0` |

**同一个用例这次绿、下次红**——这类最难查。改成越界返回 `nil` 之后才谈得上
「缺参」到底是什么语义，接着就是七处：

1. **缺参报 `no value`，不是 `nil`**：`rawlen` / `rawequal` / `rawget` /
   `rawset` / `setmetatable` / 五个 `coroutine.*`，全军覆没。C 的
   `luaL_typename` 对越界下标给 `no value`。
2. **`rawequal` 漏了两遍 `luaL_checkany`**：`rawequal(1)` 本该报
   `bad argument #2 ... (value expected)`，我们返回 `true true`。
3. **`pairs` / `ipairs` 不要求是表**：C 只做 `luaL_checkany`，
   `pairs(5)` 照样返回三个值，真正炸是在迭代时 `next(5)` 报
   `(table expected, got number)`。我们多加了「必须是表」，把它变成报错。
4. **`select` 的 `#` 只比第一个字节**：C 是
   `*lua_tostring(L, 1) == '#'`，所以 `select("#abc", 1)` 也是「数个数」，
   返回 1；判断整串等于 `"#"` 就漏了。另外缺参报的是
   `number expected, got no value`，不是 `value expected`。
5. **`rawget` 没有「键不能是 nil」那条**：`rawget({}, nil)` 是 `true nil`
   （nil 键落到 `ltable.c` 的 dummynode）。报「table index is nil」的是
   `rawset`，那条来自 `luaH_newkey`。
6. **`setmetatable` 的第二个参数**走 `luaL_argexpected`，必须带
   `got <实际类型>`；而且缺参是 `TNONE`，不属于 {nil, table}，所以
   `setmetatable({})` 要报错 —— 我们把它当合法放过去了。
7. **`next` 的第二个参数不给就补一个**，不是报错（C 的 `lua_settop(L, 2)`）。

（顺带：`__index` 链、`__eq` / `__lt` 的回退、`__metatable` 保护、元方法自身
报错、把带 `__call` 的表当元方法用 —— 那些一行不差。）

#### 可选参数矩阵：两处「校验顺序写反」

上一片只试了「0 个 / 1 个实参」，这一片把第 2、第 3 个参数「给不给、给什么」
也做全排列：七十来个函数，按 `{nil, 1, "x"}` 的所有组合从 0 个实参一直喂到
各函数的形参上限，**1866 行**。咬出两条，都是同一个毛病 —— **多道校验的先后
写反了**：

**其一，`string.gsub`。**

```c
  int tr = lua_type(L, 3);                          /* 只记类型，不校验 */
  lua_Integer max_s = luaL_optinteger(L, 4, srcl + 1);
  int anchor = (*p == '^');
  lua_Integer n = 0;
  int changed = 0;
  ...
  luaL_argexpected(L, tr == LUA_TNUMBER || tr == LUA_TSTRING ||
                   tr == LUA_TFUNCTION || tr == LUA_TTABLE, 3,
                      "string/function/table");
```

第 4 个参数的校验在<b>前面</b>。于是 `gsub(s, p, nil, "x")` 报的是
`bad argument #4 (number expected, got string)`，而我们因为先校验第 3 个参数，
报成了 `bad argument #3 (string/function/table expected, got nil)`。

**其二，`tonumber`。** 三道校验的顺序是
`luaL_checkinteger(2)` → `luaL_checktype(1, LUA_TSTRING)` →
`luaL_argcheck(2 <= base <= 36, 2, "base out of range")`，**范围检查在最后**：

| | 参考实现 | 修之前 |
| --- | --- | --- |
| `tonumber(nil, 1)` | `bad argument #1 (string expected, got nil)` | `bad argument #2 (base out of range)` |
| `tonumber(1, 1)` | `bad argument #1 (string expected, got number)` | `bad argument #2 (base out of range)` |

三个函数不在这张表里：`os.time` / `os.date` 第一个参数为 `nil` 时读的是时钟，
`math.randomseed(nil)` 拿时钟播种，`io.lines` 无参会去读 stdin。

#### `package` / `require` / `load` 矩阵：两处 bug，外加一个必须共用的计数器

**311 行**：`package` 的静态结构、`searchpath`、`require` 的各种形态（模块返回
`nil` / `false` / `true` / 什么都不返回、缓存语义、模块里抛错、循环依赖、
`preload` 压过磁盘、模块名带斜杠），以及 `load` 的 `mode` / `env` /
`chunkname` / 读函数。

**其一，`package.loadlib` 的第三个返回值说的是「在哪一步失败」，不是「失败」。**
`ERRLIB`（库打不开）给 `"open"`，`ERRFUNC`（库里没这个符号）给 `"absent"`：

```c
    lua_pushliteral(L, (stat == ERRLIB) ?  "open" : "absent");
```

我们照着「没有 dlopen 的兜底分支」写死成 `"absent"` —— 可那个分支下
`lookforfunc` 恒返回 `ERRLIB`，照样该给 `"open"`。用第三个值决定「还找不找」
的代码会因此走错分支。

**其二，循环依赖报的不是裸的 `C stack overflow`。** 参考实现给的是：

```
error loading module '_pk_b' from file './_pk_b.lua':
	C stack overflow
```

因为<b>语法分析器与 `luaD_call` 共用同一个计数器</b>：
`#define enterlevel(ls) luaE_incCstack(ls->L)`。第 200 层不是在 `lua_call` 上
撞到的，而是在某次 `luaL_loadfile` 的解析中途撞到的 —— 错误被
`luaD_protectedparser` 兜住，`loadfile` 返回非 `LUA_OK`，最后由 `checkload`
包上那层前缀。调用深度与表达式嵌套深度各数各的，就只能拿到裸消息。
连模块名都钉在用例里了。

修它顺带咬出计数器的另一半 —— **回滚**。`luaD_call` 的自增写在
`try` 等价物<b>之前</b>，`luaE_checkcstack` 抛错时 `finally` 管不到那一下；
C 靠 `luaD_rawrunprotected` 末尾的 `L->nCcalls = oldnCcalls`（在 `pcall` 边界）
把账补回来，Java 没有 longjmp 可退。不补的话每次 C 栈溢出永久漏一层：
用 `pcall` 递归探针量出来是 197 → 196 → 195 → 194 → 193，上游 `errors.lua`
于是开始报 `error in error handling`，而参考实现那里是 `OK`。解析器那头同理，
回滚点必须挂在 `lua_load` 上，挂低了（挂在二进制 / 文本分派处）一次失败的
`load` 就漏两层。

**还有一处根本没法比**：`package.loadlib` 的失败**消息**来自平台 libc
（macOS 是 `dlopen(...)`，glibc 是 `cannot open shared object file...`），
本实现压根不加载动态库 —— 三边三种文本，只能比「它是不是字符串」。

#### `io` 库深矩阵：`fseek` 欠你一个 `EINVAL`

`io` 原本是十个标准库里最薄的一片（167 行），这一片补上 **124 行输出**：
`open` 的十六种合法模式与九种非法模式（非法模式是**抛错**不是返 `nil`+消息，
`l_checkmode` 的判据是 `[rwa]%+?b?`）、`read` 的全部格式、`write` 写整数与
浮点的形态差异、`seek` 三个起点与越界、`lines`、`setvbuf`、关两次、
`io.output` / `io.input` 切换，以及含 `\0` 与非法 UTF-8 的二进制安全。

咬出一处：**负的目标位置是 `EINVAL`，不是笼统的 IO 错**。

```
参考实现： nil	Invalid argument	22        -- seek("set", -1)
修之前：   nil	Input/output error	5
```

`fseek` 对三种起点（`SET` / `CUR` / `END`）算出来的负偏移**一律**给 `EINVAL`，
而 Java 的 `RandomAccessFile.seek` 抛的是 "Negative seek offset"，掉进
`errnoOf` 的兜底分支就成了 `EIO`(5)。往 EOF **之后**定位是合法的（返回新位置，
不报错）—— 修这条时别顺手一起拦了。

一处刻意不测：`print` 写的是 C 层的 `stdout`，不受 `io.output` 切换影响 ——
假设它会跟着变的脚本，两边会各自给出一个错的答案。

`fopen` 一个**目录**原本也写在这一节里，那是错的：它是一条完整的 POSIX 规则
（`open(dir, O_RDONLY)` 成功、`O_WRONLY` 给 `EISDIR`、第一次读失败于 `EISDIR`），
macOS 与 glibc 一样，只是 JVM 不愿意建模。现在建模了，见下文 *目录那个 bug*。
真正不能比的只有目录上的 `seek("end")`：给的是目录自身的大小（本机 64），
那是文件系统的事实，不是 Lua 的事实。

#### `os.time` 那半边：`lua_tointegerx` 是带字符串转换的

既有那片 854 行铺的是 `os.date` 的每个转换说明符；这一片铺它没盖住的半边：
`os.time` 的各种表形态（非闰年进位、月/日/时越界、缺省值、被忽略的多余字段）、
`difftime`、`getenv`、`remove` / `rename`、`tmpname`、`execute` 的退出码、
`setlocale`。

**日期表的字段是用 `lua_tointegerx` 读的，它会转换字符串**：

```c
  lua_Integer res = lua_tointegerx(L, -1, &isnum);   /* cvt2num：字符串也走 */
```

所以 `os.time{year = "2000", month = 1, day = 1}` **就是** 2000 年，而我们报
`field 'year' is not an integer`。库函数参数走的是会做类型转换的 `luaL_check*`
那一套，VM 的算术指令则刻意不转换 —— 两条相反的规矩，很容易写成同一条。

顺带钉住：`month` / `day` 是**必填**（缺了报 `field 'month' missing in date
table`，`year` 有缺省、`hour` 缺省 12）；字段大到越出 int 范围报
`field 'x' is out-of-bound`；`yday` / `wday` 之类的多余字段被忽略。

两处刻意不测：`year = -100` 这种极端年份（`mktime` 的可表示范围随 libc 而定，
macOS 报 `time result cannot be represented in this installation`）；删**目录**
（`ENOTEMPTY` 的数值 macOS 是 66、glibc 是 39）。

#### 弱表 / `__gc` / `<close>` 矩阵：`isdecGCmodegen`

这是整个项目**语义风险最大**的一块 —— **没有收集器**（`lgc.c` 不移植），弱表靠
`WeakReference`、`__gc` 靠 `Cleaner` 重建，光靠 `GcTest` 那 12 个手写用例不够。
这一片铺：弱键 / 弱值 / 双弱、仍被强引用着的键、互相引用的弱值、根本不可收集的
值（字符串 / 数字 / 布尔）、`__mode` 的十种写法、`__gc` 一个与二十个、`__gc`
里抛错、`setmetatable` **之后**才加的 `__gc`（C 不认，只在设元表那一刻检查）、
`<close>` 的关闭顺序与 `err` 参数。

咬出一处：**`collectgarbage("generational"/"incremental")` 报的是
`isdecGCmodegen`，不是 `gckind` 本身**：

```c
#define isdecGCmodegen(g)  (g->gckind == KGC_GEN || g->lastatomic != 0)
```

`lastatomic` 在**头一次真正收集之后**就非 0，而加载脚本本身就要分配 —— 用户代码
刚跑起来时它已经不是 0，于是尽管 `gckind` 是 `KGC_INC`，看起来却是分代模式。
参考实现连续切换五步：

```
1 generational   2 incremental   3 incremental   4 generational   5 incremental
```

只记 `gckind` 的话**第一行就假**。两种切换都会把 `lastatomic` 清 0，之后就只看
`gckind`。

两处不可比：`collectgarbage("count")` 的数值（没有收集器，无从统计）与
`collectgarbage("step")` 的返回值（参考实现的一「步」永远走不完一轮、恒
`false`；我们没有增量收集器，一步就是一整轮、恒 `true`。**刻意**取 `true`：
取 `false` 会让 `repeat until collectgarbage("step")` 这类写法死循环）。

#### `debug` × 协程 交叉矩阵：`_LOADED` 得挂在注册表上

两边各自都铺过（114 / 99 行），没铺的是**交叉**那一层：给协程做 `getinfo` /
`getlocal` / `traceback` / `sethook`、钩子的**计数**（打印成一个数而不是一条一行
—— 计数本身就是对行号表与调用序列的强校验，`tail call` 事件也钉在里面）、
registry / `uservalue` / `setmetatable`，以及协程状态机的补充。

咬出一处：**`_LOADED` 必须是注册表的一个字段**：

```c
#define LUA_LOADED_TABLE  "_LOADED"     /* lauxlib.h */
```

`package.loaded` 不是「别处存一份、注册表里再放一份」，它**就是**那个注册表字段，
所以 `debug.getregistry()["_LOADED"]` 得拿得到。我们只在 `LuaState` 里存了一份，
于是 `type(debug.getregistry()["_LOADED"])` 给 `nil`，参考实现给 `table` —— 靠
注册表做手脚的脚本会直接失效。改成从注册表里读回来：脚本把 `_LOADED` 换掉时
我们也跟着换。

#### 错误矩阵（208 行）：`__name` 上的两处

一条此前没人铺过的轴：`error(msg, level)` 的 level 从 `-1` 一路到
`math.maxinteger`、各种类型的**错误对象**（以及 `print` / `tostring` /
`string.format("%s")` 各自怎么处置它）、`luaL_where` 的位置前缀在**每一个可能
抛错的语法位置**上的取值（表构造、数值与泛型 `for`、十二个元方法、`gsub` 的替换
函数、`sort` 的比较器、协程、`<close>`、方法、`__index` 链、尾调用、`load` 出来的
chunk 配 `=` / `@` / 字符串三种名字）、`xpcall` 的形状、钩子里抛错、以及 `strip`
过的 chunk。208 行一致。

咬出两处，都在 `luaT_objtypename` 上：

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

**`__name` 是 C 字符串**：`getstr()` 交出来的是 `const char *`，`luaG_typeerror`
用 `%s` 拼，于是在第一个 `\0` 处被砍断 —— `__name = "A\0B"` 报的是
`attempt to perform arithmetic on a A value`，只有 `A`。我们把整个字节数组都
交出去了。（`luaL_tolstring` 那条路上同一个坑早就修过，这是它的第二份拷贝。）

**只有表与（完整）userdata 认 `__name`**：其余类型**连类型元表都不看**，直接走
`ttypename`。所以 `debug.setmetatable(nil, {__name = "NilT"})` 之后 `nil + 1`
参考实现报的仍是 `... on a nil value`。我们的查找走的是 `luaT_gettmbyobj`，它会
把**类型元表**也算进来，于是多报了 `NilT`。

另有四条钉住了、但并不是 bug 的：

* **`debug.traceback` 把非字符串、非 `nil` 的消息原样返回**（`ldblib.c:
  db_traceback`），所以 `xpcall(f, debug.traceback)` 碰上表错误对象返回的是那张
  **表**，不是回溯。而 `lua_tostring` **不**认 `__tostring` —— 这正是 `lua.c` 的
  `msghandler` 要显式 `luaL_callmeta(L, 1, "__tostring")` 的原因。
* **行钩子在这一行的第一条指令之前触发**，所以 `print(pcall(f))` 与钩子写在
  同一行是**拦不住**的；调用钩子连 `print` / `pcall` 自己都算。
* **`xpcall` 会丢掉处理器的第二个返回值**：`ldo.c: dothecall` 是
  `luaD_callnoyield(L, L->top - 2, 1)`，结果数写死 1。
* `coroutine.wrap` 抛出的错带**两层**位置前缀；`pcall(error, "x")` **没有**前缀
  （C 函数没有行号）；库函数报错**有**，因为 `luaL_error` 的 `luaL_where(L, 1)`
  取的是调用者那一层。

#### 内嵌 `\0` 矩阵（88 行）：C 在十三个门口各砍一刀

一条轴、一条规则：Lua 字符串是字节串，但 C 里凡是接 `const char *` 的 API ——
`strlen` / `%s` / `strstr` / `strchr` / `strcmp` —— 都在第一个 NUL 处停。88 行，
咬出**十三处**分歧，全是同一个 bug 在十三个不同门口：

| 在第一个 `\0` 处砍断 | chunk 名（`luaS_new`）、模块名与 `package.loaded` 的键、`searchpath` 四个参数、`io.open` / `io.lines` / `os.remove` / `os.rename` / `os.getenv` 的文件名、`fopen` 的**模式**、`debug.getinfo` 的 `what`、`debug.sethook` 的 mask、`luaL_traceback` 的消息、`luaL_checkoption`（`collectgarbage`）、`string.pack` 的格式串、`__name` |
| --- | --- |
| **不**砍断 | `string.format("%s")`（走 `luaL_tolstring`，带长度）、`error` 的消息（`lua_concat`）、`os.date`（按 `s + slen` 扫完整个格式串）、表键、`gsub`、`upper` |
| 是**拒绝**而不是砍断 | 带修饰符的 `%s` 与 `string.pack` 的 `z`：`luaL_argcheck(L, l == strlen(s), arg, "string contains zeros")` |

其中两处值得单独说：

**`os.remove("/x\0y")` 直接把进程干掉了。** `Paths.get` 抛
`InvalidPathException`，它是 `RuntimeException`、**不是 `LuaError`**，而
`pcall` 只拦 `LuaError`，于是整颗逃逸出去。按 C 字符串语义截断之后这类根本
不会出现了，`catch` 顺手也把 `RuntimeException` 兜住。

**`__name` 是它的第二份拷贝。** `luaL_typeerror` 与 `luaG_typeerror` 都走
`luaT_objtypename`，但 `typeArgError` 自己又写了一份
`metafieldOf(v, "__name")` —— 于是两条规则（截断、只认表与 userdata）**两条都
错**。现在跟另外两处一样调 `LuaTm.objTypeName`。

两个 `debug` 上的最阴：`debug.getinfo(f, "S\0ln")` **不该**报 `invalid option`
（C 扫到 NUL 就收工，`l` 和 `n` 它根本没看见）；`debug.sethook(f, "l\0c")`
只装行钩子，`"c\0l"` 只装调用钩子 —— 两者的计数正好反过来。

#### 内嵌 `\0` 第二轮（40 行）：`load` / `loadfile` / `io.output`

把 `src/main` 里剩下的 `.toJavaString()` 逐个过一遍，又揪出三个门口：
`load` 的**模式**（`checkmode` 比的是 `const char*`）、`loadfile` 的文件名、
`io.output` / `io.input` 拿到的文件名。于是 `load(src, name, "t\0b")` 的模式是
`"t"`，而 `load(src, name, "b\0t")` 会去 undump 一个文本 chunk、报
`attempt to load a text chunk (mode is 'b')`。

还咬出一处 C 与 Java **判路径判得不一样**的地方：**`loadfile("")`**。C 的
`fopen("")` 给 `ENOENT`，报 `cannot open : No such file or directory`；Java 把
空路径解析成当前目录、失败在读，给 `Is a directory`，我们于是报成了
`Input/output error`。空路径是唯一一处，按 C 的判据单独取 `ENOENT`。
目录原本在同一句里被断言成「不用改、也不该改」—— 而这句是错的，值得留着当教训：
它是**读 lauxlib.c 读出来的，不是跑参考实现跑出来的**。C 里 `loadfile("/tmp")`
确实是 `fopen` 成功、失败在读，但本实现报的是
`cannot open /tmp: Input/output error`（Java 的 `Files.readAllBytes` 在开就抛了），
也就是<b>另一条分支</b>。量过之后，一句错话变成五个门口的真 bug，
见下文 *目录那个 bug*。

#### 一片铺下来是干净的：`load` 的读取器契约（54 行）

`lua_load` 的 `lua_Reader` 此前没人铺：一次给完 / 一个字节一个字节给 / 任意切分、
**空串不是结束**（`Zio` 会继续问下一个，只有 `nil` 才收尾，所以
`{"", "", src}` 编出来是 `src`，而 `{"", ""}` 编出来是**空 chunk**）、读取器
返回 `true` / 表 / 函数就当结束、读取器抛错要原样传出去（哪怕已经喂进去一半）、
读取器里**不能 yield**（`lua_load` 走 `luaD_callnoyield`）、以及 `load` **不像
`loadfile` 那样吃 BOM 与 `#!` 行**（那属于加载层，不属于词法层）。

一处也没咬出来 —— 但这本身就是铺它的理由：字节流翻译最容易在读取器这一层
丢字节或重复字节。

#### 两片铺下来是干净的：协程语义、数值 `for` 与强制转换

**协程（99 行）**：`yield` 到底能跨哪些边界、`close` 的语义、错误怎么传播、
四个状态怎么转、嵌套 `resume`。实测分界（两边一致）：

| 能 `yield` | `pcall` / `xpcall`、数值与泛型 `for`（含自定义迭代器）、`gmatch`、
`__index` / `__newindex` / `__add` / `__lt` / `__concat` / `__len` /
`__call` / `__close` |
|---|---|
| 不能 | `table.sort` 的比较函数、`string.gsub` 的替换函数、`print` 里的
`__tostring` —— 报 `attempt to yield across a C-call boundary` |

另外几处一并盯住了：`__eq` 里 `yield` 之后协程是 **dead**（不是 suspended）；
自己 `close` 自己报 `cannot close a running coroutine`；`<close>` 变量按
**声明的逆序**关闭，且 `__close` 里抛错会替换掉原错误。

**语法与作用域（121 行）**：用 `load()` 把六十来个片段编一遍，把**编译期**错误
也纳入比对 —— `goto` 跳进作用域、重复标签、给 `<const>` 赋值、`break` 在循环外、
畸形数字与转义、函数名的各种形态。这些只有手写才碰得到，fuzzer 生成的程序
撞不上。运行期那一半是多重赋值「右边先全部求值」、表构造器的求值顺序与键覆盖、
变长参数（含 `nil` 混在里面）、`:` 的各种畸形用法、upvalue 共享、
`repeat` 的条件能看见循环体里的 `local`、运算符优先级与结合性。

**`debug` 与 `utf8`（114 行）**：`getinfo` 的 `what` 每个字母单独与组合、
`getlocal`/`setlocal`/`getupvalue`/`setupvalue` 的越界、`sethook` 的四个掩码、
`traceback` 的**形状**（不整段比对，里头有 chunk 名与地址）、`utf8` 五个函数的
越界与非法序列。

**数值 `for` 与强制转换（551 行）**：`forprep`/`forloop` 的四个分支
（整数升/降、浮点升/降）各铺一遍，含 `maxinteger` 边界、字符串当起止、
步长 0、浮点步长；外加 `//` / `%` / 位运算 / 字符串转换的角落。三点值得记住：
`for i = 0, 1, 0.5` 的首值是**整数** `0`（`for i = 0, 1.0, 0.5` 则全程浮点）；
位运算**不**做字符串转换（`"10" // 2` 给 5，但 `"10" & 1` 报错）；
`for i = 2^53, 2^53+2` 与 `for i = 1e300, 1e300` 在浮点精度下是**不推进**的
死循环 —— 探针里加了 20000 次兜底，两边在同一处跳出来。

#### 一次符号审计：`warn` 整函数缺失，而 828 个用例全绿

这一片与上面那些矩阵**不是一类**：那些是在「已经写到的函数」里找分歧。这次是拿
`lbaselib.c` 的 `base_funcs[]` 逐行对 `BaseLib.open` 的注册，发现 **`warn` 压根
没实现** —— 而 828 个用例全绿，因为没有任何一个脚本用过它。行为矩阵按构造就
看不见「缺席的函数」。

于是留下两样东西：实现，和一道**常驻闸门**——把两边的**符号集**对撞
（190 行：全部全局、九个库的每个键、字符串元表、`package.loaded`，各打成
`名字 类型`）。不比值：值是地址，归一化之后什么都不剩，用例会退化成
`assertFullMatches` 本来就拦的那种空用例。

同一片审计还咬出 **`_VERSION`**，第二个整缺口。它是 `Lua 5.4` 而不是
`Lua 5.4.7`（`LUA_VERSION` 不含修订号，带修订号的叫 `LUA_RELEASE`，是
`lua -v` 打的）。它比看上去要紧：真实项目到处是
`if _VERSION == "Lua 5.1" then …` 这种探测，缺了 `_VERSION` 走的是 else 分支
——**悄悄错**，而不是响亮地崩。

第三个 `arg` **不是**库的缺口：`lua.c` 在 `luaL_openlibs` 之后由 `createargtable`
造的。差分驱动既然整体在模仿 `lua.c` 的形状（`pmain`、`msghandler`、多占的那一格），
这一张表也照着造——否则所有读 `arg[1]` 的脚本都进不了比对。

实现 `warn` 要连带移植 `lauxlib.c` 的三态状态机，探针咬出三处，每一处都是
「只测 `warn("@on")` 永远碰不到」的：

| 规则 | 漏了的症状 |
|---|---|
| `checkcontrol` 要求 `tocont == 0` —— 所以 `warn("@on", "x")` 是把 `@onsecond` **原样打出来** | `@on` 被当控制消息吃掉 |
| 只有 **OFF 与 ON** 两个状态查 `checkcontrol`，`warnfcont` 直接写 —— `warn("a", "@on")` 打的是 `a@on` | 一条消息里出现第二个 `Lua warning: ` 前缀 |
| 消息是 `const char*`，且 `lua_writestringerror` 写的是**字节** | `warn("@on\0junk")` 不再能打开警告；`warn("\255")` 出来变成 `0xC3 0xBF` |

最后那两条就是前面几节里的 NUL 规则与字节规则，在一个函数里同时出现——这正是
要按**契约**铺、而不是按例子铺的理由。

#### 符号闸门自己也有个洞：文件方法住在 `__index` 里

闸门第一版只查 `io` 表，于是**七个文件方法整片漏看** —— `close` / `flush` /
`lines` / `read` / `seek` / `setvbuf` / `write`。它们住在
`getmetatable(io.stdout).__index` 里（旁边还有 `__gc` / `__close` /
`__name = FILE*` / `__tostring`），不在 `io` 表上。符号审计必须顺着
`__index` 往下挖才有用，现在挖了。

顺着这七个方法一探，咬出一处真分歧：**`fwrite(s, 1, 0, f)` 返回 0，正好等于
`l`，所以写 0 个字节一定成功** —— 往**只读**文件 `f:write("")` 返回的是文件
自己，而不是 `nil, "Bad file descriptor", 9`。我们的 `write` 是先判
`!writable` 再看长度。

只有一格能把它逼出来，这正是要铺格子而不是挑例子的理由：

| | 可写文件 | 只读文件 |
| --- | --- | --- |
| `f:write("")` | 文件（两边同） | **文件** / ~~`nil, msg, 9`~~ |
| `f:write("a")` | 文件（两边同） | `nil, msg, 9`（两边同） |

读那一侧是不对称的、本来就是对的，**而且得保持不对称**：`f:read(0)` 走的不是
`read_chars` 而是 `test_eof`（`getc` 再 `ungetc`），所以在只写流上照样失败；
而 `read` 是先 `done >= len` 再判 `readable`。

一并实测且一致：只读流上 `flush` / `setvbuf` / `seek` 都成功；
`io.stdout:close()` 给 `nil, "cannot close standard file"`。

#### 两片「对所有库函数统一施加」的矩阵

`f:write("")` 那次是**返回元数**上的 bug，而且是手摸出来的。于是补两片机械的：
拿同一个输入施加到全部 ~130 个库函数上，比对「成功还是失败 + 每个返回值的
**类型** + 出错时的消息」：

* **零参**（约 120 行）—— 铺「参数缺失」这条路；
* **一个错类型实参**（`{}` / `nil` / `1`，359 行）—— 铺「类型不对」那条路。
  两条在 `lauxlib.c` 里是<b>两个不同的函数</b>（`luaL_check*` 与 `luaL_opt*`
  对「缺失」的处理不一样），只铺一条会漏掉另一条的报错文本。

零参那片没咬出新东西，错类型那片<b>铺下来也是干净的</b>。留着是因为它们一次性
钉住 ~370 个调用形状：以后改任何一个库的报错文本、返回值个数，或者少一个参数
校验，这里立刻红 —— 而这类角落手写矩阵往往不碰。

四样必须排除或归一化，每一样都是踩出来的：

| | 理由 |
| --- | --- |
| `os.exit` | 会真的杀掉进程 |
| `io.*`、`dofile`、`loadfile` | 动默认流或读 stdin，两边不一样 |
| `debug.debug()` | 进交互式提示符（`lua_debug>`）并读命令 |
| `math.atan` / `math.atan2` | **同一个 C 函数**（`{"atan", math_atan}` 与
`{"atan2", math_atan}`），报错里报哪个取决于 `findfield` 用 `lua_next` 撞见谁，
而字符串哈希种子<b>每次进程随机</b> —— 参考实现连跑六次，三次 `atan`、
三次 `atan2`，它<b>自己对自己都不一致</b>。先把名字统一成 `atan` 再比。 |

值一律不打印、只打 `type()`：`os.time()` / `os.date()` / `math.random()` 的值
每次都不同。

#### 钩子的格子：掩码 × count × 线程

已有的钩子用例是「一个掩码一片」，这片是**全格子** —— 八个掩码、三种 count、
四个函数体，外加按线程安装。因为好几条语义只在格子里看得出来：

* `makemask` 用 `strchr`、只认 `c` / `r` / `l`：`"x"` 与 `"CC"` 都是**空掩码**
  （而且大小写敏感）。只看 `"crl"` 永远发现不了。
* **`lua_sethook` 在 `mask == 0` 时把钩子整个关掉** —— 所以
  `debug.sethook(f, "")` **装不上**钩子，`debug.gethook()` 返回 `nil`，
  而不是「装了一个什么都不触发的钩子」。`debug.sethook(f, "x")` 同理。
* `count` 只有 `> 0` 才并进 `LUA_MASKCOUNT`：传 0 与 -1 都没有 count 事件，
  而 `""` + count 1 是**只有** count 事件。
* 钩子是**按线程**装的：主线程的行钩子在协程里一行都不触发；
  `debug.sethook(co, …)` 只管那个协程。
* `gethook` 的掩码字符串**不含** count（`unmakemask` 只输出 c / r / l），
  count 是第三个返回值单独给的。

两样形状上的约束与断言同等重要：事件**收集起来最后统一打印** —— 钩子开着的
时候每一个 `print` 都会自己产生一对 call / return，直接把结果淹掉；钩子体本身
跨好几行、还调函数 —— `allowhook` 的重入保护要是坏了，这里是无限递归而不是
比对失败。

铺下来是干净的。

#### 整数 / 浮点<b>子类型</b>网格

5.4 里「数字」分成两个**子类型**，`math.type` 看得见差别 —— 这是移植 5.4 最容易
错的一类：写成「一律 double」的实现大多脚本能跑，只在 `//` / `%` /
`string.format("%d")` / `math.tointeger` 这些地方悄悄不同。所以把标准库里**所有
会返回数字**的门口过一遍，只比子类型（不比值：`os.time` 与 `math.random` 每次
都不同）。

156 行，分两格：常用形状（数学库「整型进整型出、浮点进浮点出」、`tonumber` 的
四种写法、位运算、`string.unpack` 各宽度、`io` 的 seek、`os.date` 各字段），
以及**边界**（`maxinteger + 1`、`mininteger // -1`、`math.fmod(1, 0)` 与
`1 // 0` 是**抛错**而不是给 nan、`math.floor(-0.0)` 是整数、
`math.tointeger(2^63)` 是 nil）。

每条都套在 `pcall` 里 —— 那两个抛错的就是这么发现的。不套的话脚本在第一条就死，
后面一百行一条也不会比，而用例照样是绿的。

这片第一版有个值得记下的 bug：合并两个探针脚本时，第二半的调用落到了第一半那个
「收值不收 thunk」的同名 helper 上，于是 156 行里 72 行静默变成 `tag nil`，
而用例依然是绿的。**一整片都是 `nil` 的矩阵什么也没证明** —— 相信「铺下来干净」
之前，先看一眼参考输出里有没有整片相同值的行。

#### 子进程那一片：`luaL_pushfail` 是 `nil` 不是 `false`

`io.popen`、`io.tmpfile`、`setvbuf` 是 io 库里最薄的一块 —— 各只有一两个用例。
四个脚本（管道读写、模式校验、tmpfile、`setvbuf` 各模式）铺开之后，第一跑就咬到：

```
e3    true  nil     exit  3     <- 参考实现
e3    true  false   exit  3     <- 我们
```

子进程非零退出时 `luaL_execresult` 压的是 `luaL_pushfail`，而 5.4 里这个宏
<b>就是 `lua_pushnil`</b>（`lauxlib.h: #define luaL_pushfail(L) lua_pushnil(L)`）
—— 从 5.4 把「fail」与 `false` 拆开起就是这样。整个项目里它一直看不出来，是因为
`os.execute` 自己另有一份逻辑、那份本来是对的；只有 `io.popen` 的收尾走共用的
`execResult`，于是 popen 是唯一能暴露它的门口。**同一个 helper 有两份实现，
bug 就藏在没人测的那一份里。**

这里有三样东西是实测定为「不比」的，每一条都是量过的，不是托辞：

* **子进程的输出/错误必须在命令里重定向掉。** 继承来的描述符不进本实现的捕获
  缓冲（Java 的子进程继承的是 JVM 真那个 stderr），两边对不齐。所以写的是
  `cat > /dev/null` 与 `2>/dev/null`，不是 `cat` 与裸命令。
* **不排空管道就 `close` 是竞态。** 参考实现拿到 `nil, "signal", 13`
  （`echo` 被 SIGPIPE 杀掉），我们给 `true, "exit", 0`。取决于子进程有没有先
  写完，逐次运行不稳定，不能进比对。
* **`setvbuf` 负的 / 极大的 size 是 libc 自己定的。** C 把 size 当 `size_t`
  交给 libc 的 `setvbuf`，能不能分配出来由 libc 决定：同一台机器上 `2^31`
  失败而 `2^40` 成功，`"no"` 配 `-1` 成功而 `"line"` 配 `-1` 失败。
  Java 侧没法合理建模。

#### 第三片矩阵：成功路径

前两片对<b>每个库函数</b>的矩阵问的是两件事：函数<b>在不在</b>（符号审计），
以及喂<b>错</b>参数时怎么报错（零参 / 错类型那两片）。两条走的都是
`luaL_check*` / `luaL_opt*` —— **检查分支**。检查通过<b>之后</b>那段计算本体，
两片一行都没碰：一个算错的 `string.rep` 在它们底下会一直绿。

于是补第三片：给每个库函数喂一组<b>正常</b>实参，比返回值<b>本身</b>而不是类型。
209 行，三个脚本 —— string / math（68 行）、table / utf8 / os / coroutine
（44 行）、io / debug / 全局（97 行）。

值由 `d()` 描述，既确定性又不失真：数字带 `math.type` 子类型、字符串带长度、
表递归展开两层并按键排序（`pairs` 顺序与地址都进不来）。只有少数几处比类型
不比值：`os.time`、`os.clock`、`collectgarbage("count")`、表的 `tostring`。

两个值得一提的结果：带种子的 `math.random` 对上了（xoshiro256** 的**序列**是对的，
不只是取值范围）；`getmetatable("")` 一行就把整个字符串元表连同 `__index` 里
十七个方法全倒出来了。

**矩阵的敏感性是量过的，不是假设的。** 把 `string.upper` 改坏一格
（`'a'..'z'` 挪成 `'B'..'Z'`），这一片立刻红在 `s.upper [2] q(3)[Abc]`。
不会红的矩阵比没有矩阵更糟，而唯一知道的办法就是改坏一处、看它红不红。

209 行逐字节一致，第一跑就干净 —— 这本身也是可疑结果（见上面「整片都是 nil
的矩阵什么也没证明」那条）。这里 209 行里有 205 行带着真值，所以不是空转。

#### `io.open` 的模式矩阵，以及 stdio 说了算的三件事

`l_checkmode` 认的是 `[rwa]%+?b*`，而且尾巴是 `strspn(mode, "b") == strlen(mode)`，
不是「最多一个 b」—— 所以 `"rbb"` 合法而 `"rb+"` 不合法，`"r b"` / `"br"` /
`"R"` / `"r\t"` 全失败。34 种模式 ×（读 / 定位 / 写 / 位置 / 关 / 最终内容），
79 行。

咬出**三处**分歧。三处都是 **stdio** 定的、不是 Lua 定的，而且每一处都是量过
才放到一边的：

1. **更新模式上「读完不定位直接写」** —— ISO C 里这本就是未定义行为 —— 落点由
   C 的**预读缓冲**决定。实测：12 字节的文件写到 13（EOF），6002 字节的写到
   4097（BUFSIZ+1），而 `read(5000)` 又快又准地写到 5001（大块读绕过缓冲）。
   矩阵改成每次写之前显式 `seek` 一次绕开这条 UB 路径 —— 绕开之后
   （`read → seek → write`）两边**逐字节一致**。
2. **append 档上写完的位置。** `seek("set",0)` 再写 1 字节：参考给 **1**
   （`base+n`），我们给 13（`EOF+n`，这一边才符合 POSIX）。再量下去发现它也是
   缓冲定的：`n<=4095` 给 `base+n`、`n>=4096` 给 `EOF+n`。所以矩阵在量位置之前
   先 `flush`，把位置压到 POSIX 那个唯一确定的答案上，两边才可比。
   **写进去的内容两边始终一致**，分歧只在 `seek("cur",0)` 报的数。
3. **标准流上的 `seek`** 取决于真实 fd 可不可定位：重定向到文件给 0、
   `/dev/null` 给 0、管道与 tty 给 `"Illegal seek"`。本实现拿不到那个 fd，不比。

其中两处与 `setvbuf` 的 size 是同一个形状：C 把决定权交给 libc，没什么可建模的。
差别在于这里的**可观测量**不只是 errno 而是文件偏移 —— 所以要写下来，而不是
悄悄排除。

#### 让 `require` 真去磁盘上找模块

`package` 那道闸门之前只走了搜索器链的「纯 Lua 那一半」（在内存里摆
`package.preload`），而 `PackageLib` 是未覆盖率第二高的类。所以补一片
<b>把模块真写进磁盘</b>再让 `require` 去找的矩阵，57 行：模块返回
nil / false / true / 数字 / 无返回 / 多值（只取第一个）、模块抛错、模块语法错、
模块里自己设 `package.loaded`、点号模块名换目录分隔符、第二次 `require` 的缓存、
循环 `require`（参考给 `C stack overflow`，不是哨兵）、`package.loaded` 预置成
值 / false / nil 三种后果、`package.preload` 给函数 / 返回 nil / 抛错 / 非函数、
`searchpath` 的 `sep` 与 `rep`、`package.searchers` 的个数与逐个调用、整条链被换掉。

顺带钉住一件会悄悄坏掉的事：每条 `module 'X' not found` 都把候选路径<b>整串</b>
打印出来，所以默认的 `package.path` 必须与参考二进制编译进去的
`LUA_PATH_DEFAULT` 逐字符一致。

57 行第一跑逐字节一致 —— 所以敏感性照旧是量过的：把 `searchpath` 的默认 `sep`
从 `"."` 改成 `"-"`，立刻红在 `sp_dots`。

#### 逐行读覆盖率报告

按「类的未覆盖率」挑目标只能挑到这一步 —— 模式矩阵与 require 矩阵铺完之后，
那几个大类的缺口几乎没动，因为一类里没跑到的指令大半是
`catch (IOException e)`，Lua 脚本根本走不到。所以再往下走一层：打开
`target/site/jacoco/<包>/IoLib.java.html`（XML 报告没有行级信息，HTML 有），
把 `class="nc"` 的行号抄出来，逐条问「**这一行 Lua 能走到吗**」。

`IoLib` 的 63 行未覆盖里，大多数确实走不到。剩下这些是<b>能走到却从没走到</b>的，
也就是真正漏测的行为，39 行：

* 已关闭句柄的 `tostring` 是 `"file (closed)"` —— **不是** `io.type` 的
  `"closed file"`。两个字符串不一样，之前只测了其中一个。
* `io.lines()` **无参**与显式 `nil`：走注册表里的 `IO_INPUT`，与
  `io.lines(文件名)` 不是同一条路。
* `f:read("n")` 的各形状：指数（`1e5`，以及 `1e999` 给 inf）、十六进制
  （`0x10` / `0x1p4` / `0x.8p1`）、前导零（`0777` 是十进制不是八进制）、
  残缺前缀（`0x` 与 `1e` 给 nil）、超长数字串（给 nil）。
* `lines` 迭代途中把文件关掉：迭代器再调用报 `"file is already closed"`。
* 大块写（5000 字节）走「绕开 wbuf 直接落盘」那条分支。

39 行全逐字节一致 —— 也就是说这些分支<b>本来就是对的，只是没人跑过</b>。
它们的价值在回归：谁改了 `read("n")` 的前缀判断或 wbuf 的阈值，这里立刻红。

#### 目录那个 bug：一条 POSIX 规则，五处全错

读 PackageLib 的未覆盖行、逐条问「Lua 能不能走到」，摸到 `searcher_C` 的兜底
分支，再摸到「模块名撞上目录」这件事 —— 结果是**一条规则没建模，五个门口全错**：

POSIX 允许 `open(dir, O_RDONLY)` 成功。于是 `fopen(dir,"r")` 成功、
`fopen(dir,"w")` 给 `EISDIR`、第一次 `fgetc`/`fread` 失败于 `EISDIR`。
JVM 根本不让打开目录，于是本实现以同一种方式在五处同时错：

| | 参考 | 修之前 |
| --- | --- | --- |
| `loadfile(dir)` | `nil, "cannot read <p>: Is a directory"` | `nil, "cannot open <p>: Input/output error"` |
| `io.open(dir,"r")` | file 句柄 | `nil, "<p>: No such file or directory", 2` |
| `io.open(dir,"w")` | `nil, "<p>: Is a directory", 21` | `nil, "<p>: No such file or directory", 2` |
| `package.searchpath` | 找到（返回路径） | `nil, "no file '<p>'"` |
| `require(name)` | `error loading module … cannot read …` | `module '…' not found` |

修法是同一个念头落在四处：`openInto` 放行只读的目录打开（要写就报 EISDIR）、
`LuaFile.getc`/`read` 报 21、`seek`/`tell` 按零长文件算（目录 fd **是**可定位的：
`f:seek("set",5)` 给 5，`f:seek("set",-1)` 仍然是 EINVAL）、
`PackageLib.readable` 认目录、`loadfile` 与 `searcher_Lua` 改走 `cannot read`
那条分支。

修完 18 行逐字节一致。怎么发现的，比这个 bug 本身更值得记：

* README 之前写着目录「没问题、不要修」—— 那是<b>读 lauxlib.c 读出来的，不是
  跑参考实现跑出来的</b>，而且它是错的。先量，再写。
* 代码注释（`BaseLib`）里那句同样断言了「目录不用改」。
  **注释跟代码保持一致不是证据。**

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
