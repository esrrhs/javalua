# fakescript 对齐 Lua 5.4 语法 —— 实施方案

> 状态：**Stage A 已完成**（`local` / `function` / `do` / `~=` / `#` / `--[[ ]]` 六项全部落地，
> 旧写法 `var` / `func` / `&&` / `||` / `!` / `!=` 及循环后的 `then` 已从词法与文法中移除；
> 测试与 README 同步迁移，271 个用例全绿）。Stage B/C/D 仍为方案，未开工。
> 依据：仓库实际词法/文法/编译器/解释器的逐条核查结果，见第 1 节的现状盘点。

### Stage A 落地纪要（回填）

| 项 | 落地方式 | 踩到的坑 |
| --- | --- | --- |
| `~=` | 词法新增，与 `~`（异或）靠 JFlex 最长匹配共存 | 无 |
| `#t` | 文法层脱糖为 `size(t)`，写在 `unary_expr` 而非 `expr_value` | 放错位置会导致 `return #t` 解析失败；另需显式 `%right SHARP` |
| `--[[ ]]` | 词法宏，必须排在行注释 `{Comment}` 之前 | 顺序反了会被行注释吃掉 |
| `local` | 与 `var` 同 token，后改为**替换**并删除 `var` | 并存会让两套拼写长期分裂 |
| `function` | 同 `local`，删除 `func` | 同上 |
| `do` | while/for 的 `then` **替换**为 `do`；`if` / `elseif` 保留 `then` | 并存会产生 34 个 reduce/reduce 冲突，把 DO 规则判成死规则 |

两条通用教训：
1. **并存 ≠ 过渡**。同一前缀派生两条只差一个收尾记号的规则，LALR 无法用单记号区分，
   直接退化成 reduce/reduce；要么一次性替换，要么就别加。
2. **改文法必须重新生成并重测**。`%expect` 会把冲突漂移变成硬错误，但前提是数字同步更新
   （HEAD 基线 57 → 本轮 58）；手工改过的 `YYParser.java` 若不重新生成，旧表会让新写法全部
   PARSE_FAIL，且症状看起来像「文法写错了」。

---

## 0. 目标、边界与判据

### 目标
把 **语法表层**尽可能贴近 Lua 5.4，让熟悉 Lua 的人写 fakescript 时几乎不用切换习惯。

### 明确不做
不承诺成为「Lua 子集」。原因不是不想，是三条硬约束（见 1.3）：

- 语言里**没有 upvalue / 闭包机制**（全仓库 grep `upvalue|closure|metatable|coroutine` 命中数为 **0**）
- 函数**不是一等值**（`var x = g` 直接 PARSE_FAIL，匿名函数、闭包均不支持）
- `func` 只能定义在顶层（文法 `YYParser.y:135` 的 `FUNC IDENTIFIER ... block END` 是函数**描述**，不在语句层）

闭包、协程、metatable 属于运行时重构级别的工作量，本方案划为例外项，建议单独立项评估，不混在语法对齐里做。

### 判定基准
- 词法/语法：Lua 5.4 参考手册 Lexical Conventions 与 The Language 章节
- 数值语义：已完成的 Lua 5.4 对齐成果（`LuaNumberSemanticTest`）是基线，只许增强不许回退

---

## 1. 现状盘点

### 1.1 已经对齐的（无需改动）

| 项 | 依据 |
|---|---|
| 数值字面量：十六进制、十六进制浮点、`//` 整除、`%` floor 取模、整数溢出环绕 | 多笔 Lua 5.4 对齐提交；`LuaNumberSemanticTest` 覆盖 |
| 移位 `<<` `>>` 为逻辑移位、`|位移量|>=64` 得 0、负数反向移位 | `variant.java:451/470` |
| 一元 `~` 按位取非、二元 `~` 异或 | `12 ~ 10` = 6，与 Lua 5.4 一致 |
| 算术优先级、`..` 右结合且低于一切算术 | `YYParser.y:99-110` 优先级块 |
| 字符串转义 `\x` `\u{}` `\z` `\ddd` | `jflex.flex` STRING 状态；`EdgeCaseTest` 通过 |
| 注释 `--` 行注释 | `jflex.flex:131` |
| `elseif` / `then` / `end` 块结构 | 关键字表 |
| `and` / `or` / `not` 逻辑关键字（另有 `&&` `||` `!` C 风格别名） | `jflex.flex` 记号表对照 |
| 花括号表构造式（**仅 `const` 常量**） | `YYParser.y:1705` `OPEN_BIG_BRACKET const_map_list_value` |

**结论：表层差距比想象中小。** 剩下的差异绝大多数是「缺某个记号」或「缺某条文法规则」，而不是整体风格不同。

### 1.2 差异清单

记号现状来自 `jflexbison/jflex.flex` 的全量记号表（65 个）对照；行为现状来自逐条实跑验证。

| # | 主题 | fakescript 现状 | Lua 5.4 | 层级 | 破坏性 |
|---|---|---|---|---|---|
| D1 | 不等于 | 只有 `!=`，`1 ~= 2` PARSE_FAIL | `~=` | L0 词法 | 无（纯加法） |
| D2 | 空值字面量 | 无 `nil` 关键字；`null` 得 `POINTER` 而非 `NIL` | `nil` | L0+L2 | 无（新增关键字） |
| D3 | 取长度 | `#a` PARSE_FAIL（词法改动后直接报 lex fail），只能用 `size(a)` | `#a` | L0，可脱糖到 `size()` | 无 |
| D4 | 块注释 | 只有行注释 | `--[[ ]]` | L0 词法 | 无 |
| D5 | `local` | 只有 `var` | `local` | L0（作 `var` 别名） | 无 |
| D6 | `function` | 只有 `func` | `function` | L0（作 `func` 别名） | 无 |
| D7 | `do` 块关键字 | `while`/`for` 后只用 `then`，写 `do` PARSE_FAIL | `while ... do` | L1（作 `then` 别名） | 无 |
| D8 | 全局赋值 | 必须 `var`，裸 `x = 5` PARSE_FAIL | `x = 5` | L1+作用域语义 | **需先定语义** |
| D9 | 多值赋值 | `var a, b = g()` PARSE_FAIL | `local a, b = f()` | L2 需返回值传递 | 无 |
| D10 | Lua 数值 for | `for i = 1, 10, 2 do` PARSE_FAIL；现为 C 式 `for var i = 0, i < n, i++ then` | `for i = 1, n, s do` | L1 新增规则 | 无（并存） |
| D11 | **比较表达式作值** | `return a < b`、`var x = a < b`、`print(a<b)` 全 PARSE_FAIL | 比较是普通表达式 | **L2 需新 opcode** | 无 |
| D12 | 布尔类型 | `typeof(true)` = **REAL 1.0**、`typeof(false)` = REAL 0.0 | Lua 有 boolean 类型 | L2 类型系统 | **有**（影响现有 `typeof`/JSON） |
| D13 | 运行时表构造 | `{}` 只在 `const` 里；运行期 `var t = {1,2}` PARSE_FAIL | `t = {1,2}` | L2 需构造 opcode | 无 |
| D14 | 泛型 for | 无 `in`/`pairs` 记号 | `for k,v in pairs(t) do` | L2 迭代器协议 | 无 |
| D15 | 变长参数 | 无 `...` 记号 | `function f(...)` | L2 | 无 |
| D16 | 一等函数 | `var x = g` PARSE_FAIL，无匿名函数、无闭包 | 函数是值 | **L3 架构** | 有 |
| D17 | metatable / OOP | 无 | `setmetatable` | L3 架构 | 有 |
| D18 | 协程 | 无 | `coroutine.*` | L3 架构 | 有 |

### 1.3 补足说明：fakescript 特有的扩展**不是**差异项

`switch/case`、`sleep`/`yield`、`package`/`include`、`struct` + `->` 成员、`i++`、`array()`/`map()` 这些 Lua 里没有。**对齐 Lua 不等于删掉它们**——Lua 写手不会去用，现有脚本却会因此全崩。

方案确立一条原则：**只增不改**。除非某现行写法本身就是缺陷，否则一律保留，只在其旁新增 Lua 形态。

---

## 2. 成本分层

| 层级 | 含义 | 典型工作量 | 风险 |
|---|---|---|---|
| **L0 词法** | 加记号、加吐信规则、块注释 | 每条约 10 行 | 极低 |
| **L1 文法局部** | 在现有非终极符内加候选规则、或新增独立语句形态 | 每条约 20–50 行 + bison 重生成 | 低（可用冲突数度量） |
| **L2 代码生成** | 需新增 opcode / 解释器动作 / 新 AST 节点 | 每项百行级，涉 4–5 个文件 | 中 |
| **L3 架构** | 需运行时能力（upvalue、表统一、调度） | 量级重构 | 高，本方案不落地 |

**一条来之不易的经验**：上一次尝试 D11 时，文法改动非常便宜——引入「必须含运算符」的 `bool_expr` 层后，冲突只从基线 57 涨到 61，且 **0 reduce/reduce**。但解析器一通过，实际求值全线崩：

- `return 1 > 2` → **静默返回 null**
- `return 1 < 2` → `ArrayIndexOutOfBoundsException` 且**逃逸出错误处理**
- `print(1 < 2)` / 数组存比较结果 → interpreter assign error

根因是 `command.java` 里比较操作**只有 `*_JNE` 分支指令**（`LESS_JNE`/`EQUAL_JNE`/`AND_JNE`/`OR_JNE`…），解释器算出 boolean 后直接跳转，不写回任何栈槽——**语言里压根没有「产生值」的比较 opcode**。

这条经验直接决定了本方案的分期：**凡是需要新 opcode 的项（D2/D9/D11/D12/D13/D14/D15）一律归入 Stage C 之后，不与纯词法项混在一起做。**

---

## 3. 分期方案

### Stage A —— 纯加法记号（建议第一步）
**范围**：D1 `~=`、D3 `#`（脱糖为 `size()`）、D4 块注释、D5 `local`、D6 `function`、D7 `do`
**不碰**：任何 opcode、任何既有语义

要点：
- `#` 归入一元层，动作上复用 `size()` 内置，不新增 opcode
- `local` / `function` / `do` 做成**纯别名**，与 `var` / `func` / `then` 走同一条归约，避免重复路径产生 reduce/reduce
- `~=` 直接吐 `NOT_EQUAL`，与 `!=` 共用既有规则

预计：Conflict delta ≈ 0–2（若出现 reduce/reduce 说明别名没做对，应停下改法）
收益：**这一步做完，日常写法层面已经很难分辨 fakescript 与 Lua。**

### Stage B —— 语句形态
**范围**：D10 Lua 数值 for、D8 全局赋值（需先定语义）

- D10：新增独立语句形态 `FOR IDENTIFIER ASSIGN expr ARG_SPLITTER expr [ARG_SPLITTER expr] DO block END`，与现有 C 式 for 并存。编译器侧优先尝试**脱糖**进现有 for 节点，脱糖不通再新增节点类型
- D8：**先出语义说明书再动手**。fakescript 现在所有变量都是函数局部的吗？裸赋值是「赋值已存在变量」还是「创建」？这一条不定清楚就无法实施，建议单独一轮讨论

### Stage C —— 表达式能力（真正的硬骨头）
**范围**：D11 比较作值、D12 布尔类型、D13 运行时表构造

按 D11 拆出的四个子项推进：
1. `command.java` 新增约 8 个值模式 opcode（`LESS/MORE/EQUAL/MOREEQUAL/LESSEQUAL/NOTEQUAL/NOT` 及值模式 `AND/OR`）
2. `interpreter.java` 对应处理，走 `variant.less/more/...` 实例方法（这些方法**已经**通过 `set_cmp_ret` 产出 1/0）
3. `compiler.java:983 compile_cmp_stmt` 增加「产生值」的第二套 codegen
4. 定夺真假值表示——`set_cmp_ret` 现在 INT 操作数得 INT 1/0、否则得 REAL 1.0/0.0，意味着 `typeof(1 < 2)` 会随操作数类型漂移，这本身要先定死

**排序建议**：D13 运行时表构造先于 D11。`const` 版本已有 AST 与求值路径（`explicit_value_node` 的 `EVT_MAP`/`EVT_ARRAY`），复用到运行期比从零做比较值更稳，也先享受 Stage A 的成果。

### Stage D —— 架构级（本方案只列不落地）
D16 一等函数与闭包、D14 泛型 for、D15 变长参数、D17 metatable、D18 协程

其中 D16 是分水岭：没有 upvalue 机制，闭包无从谈起；而一旦引入，函数调用、栈帧、常量池都要动。建议作为独立项目评估，不要搭在「语法对齐」的车上。

---

## 4. 每个 Stage 的验证方法（必须先落地再往前走）

1. **冲突数可度量**：基线 **57 shift/reduce、0 reduce/reduce**（`%expect 57` 恰好被满足）。每个 Stage 记录 delta；出现任何 reduce/reduce 都要给出书面理由，不接受「bison 自己会选」
2. **生成文件同步**：`YYParser.java` 只能由 bison 重生成并提交，**禁止手改**。CI 的 `Grammar regeneration` job 就是这道闸
3. **回归测试**：全量 `./mvnw clean test` 绿 + JaCoCo 行覆盖门禁 0.85
4. **新增用例**：每条差异项至少 1 个正向用例 + 1 个「旧写法仍可用」的保兼容性用例，集中放进 `EdgeCaseTest`
5. **反向变异验证**：新增测试必须证明自己有牙齿——把改动故意改坏，测试要能报错。上一轮用这套做法验证了 4 个差分 oracle，各自在最早用例就失败；同一轮里它也反过来证明「比较作值」在语义上根本跑不通。测不出来问题的新测试不如不写
6. **CI 7/7 全绿**：含 `Fuzzing (Jazzer)` 与 `Grammar regeneration`

---

## 5. 红线

- **每个 Stage 一个 PR**，独立可回滚；禁止一个 PR 同时动词法与 opcode
- **只增不改**：不删除任何现行写法（第 1.3 节原则）
- **不手改 `YYParser.java`**
- **数值语义不得回退**：`LuaNumberSemanticTest` 是红线集合，任何一条挂掉就停下
- **文档同步**：README / README_CN 的语法说明与「fakescript 扩展语法」章节同步更新，明确哪些是 Lua 兼容哪些是本语言扩展

---

## 6. 建议的推进顺序

| 顺序 | 内容 | 预估量级 | 建议 |
|---|---|---|---|
| 1 | Stage A 全部 | 小 | **立即做**，性价比最高 |
| 2 | Stage B 的 D10 数值 for | 小–中 | 做；先在 branch 上验证 |
| 3 | Stage C 的 D13 运行时表构造 | 中 | 做，为后续铺路 |
| 4 | Stage C 的 D11 比较作值 + D12 布尔 | 中–大 | 做，但先就「真假值表示」达成一致 |
| 5 | Stage B 的 D8 全局赋值 | 中 | **先出语义说明**，再决定 |
| 6 | Stage D | 大 | 单独立项 |

第一步落地前需要确认两点：是否接受「`local`/`function`/`do` 走别名」这种加法路线（而非替换），以及 Stage A 是否一次性全做完还是拆六个 PR。
