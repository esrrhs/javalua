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

**阶段 6（依赖回收动作的语义）。** 旧的 fakescript 语言实现已从 `master` 移除，
见下文 *迁移说明*。阶段 0~6 已落地：编译器（词法 → 语法 → 代码生成）产出的字节码
与真实 `lua5.4` **逐字段一致**，VM 已经能把它跑起来——158 + 121 + 60 + 32 段差分
脚本与 2100 个生成程序的 stdout 与真实 `lua5.4` **逐字节一致**，错误信息逐行一致。

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

下一步：阶段 7，标准库——`l*lib.c` 那一批与 `loadlib.c`。

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

---

## 许可证

MIT
