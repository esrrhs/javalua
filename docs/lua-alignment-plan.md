# fakescript 对齐 Lua 5.4 语法 —— 实施方案

> 状态：**Stage A 已完成**（`local` / `function` / `do` / `~=` / `#` / `--[[ ]]` 六项全部落地，
> 旧写法 `var` / `func` / `&&` / `||` / `!` / `!=` 及循环后的 `then` 已从词法与文法中移除；
> 测试与 README 同步迁移）。**Stage C 的 D10（Lua 数值 for）与 D13（运行时表构造）也已落地**，
> 276 个用例全绿。Stage B 的 D8、Stage C 的 D11/D12、Stage D 仍为方案，未开工。
> 依据：仓库实际词法/文法/编译器/解释器的逐条核查结果，见第 1 节的现状盘点。

### Stage C 落地纪要（回填）

| 项 | 落地方式 | 踩到的坑 |
| --- | --- | --- |
| D10 数值 for | 新增 `*_CLOSED` opcode + `m_closed` 标志，既有 `->` 写法保持半开 | 半开/闭区间不同，直接复用会静默少跑一次 |
| D13 运行时表构造 | AST 层脱糖成「临时变量 + `array()`/`map()` + 逐元素赋值」，**不加 opcode** | 只挂在 `expr_value` 上够不到 `local t = {1,2}` 与 `return {1,2}`；`return` 容器返回 null 是既有行为 |

两条新增教训：
1. **挂规则要看清非终极符的"可达面"**。D13 第一版只把 `table_constructor` 加进 `expr_value`，
   实测 `{}`、`f({1,2})` 能用，但 `local t = {1,2}` 与 `return {1,2}` 全部 PARSE_FAIL ——
   因为 `assign_value` / `return_value` 的定义是 `explicit_value | variable | expr`，
   **并不包含 `expr_value`**。补挂到 `expr` 上才通。判断"新语法能否出现在某处"的正确做法是
   顺着 `expr_value → expr → assign_value` 这条链查一遍，而不是试几个样例就算数。
2. **先分清「新缺陷」和「既有行为」，再决定修不修**。`local t = {1,2} return t` 返回 null 看起来
   像 D13 的 bug，用 `fk.dumpfunc` 打出字节码确认构造与赋值都对，再用既有写法对照：
   `return array()` 与 `return map()` **同样**返回 null。这是既有的 return 语义限制，
   不在本项范围内。为此专门加了 `testReturningContainerIsNilIsPreexisting` 把它钉住 ——
   将来若修 return，这个用例会先失败，提示范围已经变了。

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
| D10 | Lua 数值 for | 已落地（`for i = 1, n, s do`） | `for i = 1, n, s do` | **L2**（区间语义不同，需 `*_CLOSED` opcode） | 无（并存） |
| D11 | **比较表达式作值** | `return a < b`、`var x = a < b`、`print(a<b)` 全 PARSE_FAIL | 比较是普通表达式 | **L2 需新 opcode** | 无 |
| D12 | 布尔类型 | `typeof(true)` = **REAL 1.0**、`typeof(false)` = REAL 0.0 | Lua 有 boolean 类型 | L2 类型系统 | **有**（影响现有 `typeof`/JSON） |
| D13 | 运行时表构造 | **已落地**（`t = {1,2}` / `t = {a=1,b=2}`） | `t = {1,2}` | **L1**（AST 脱糖，无需新 opcode） | 无（并存） |
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
**范围**：只剩 D8 全局赋值（需先定语义）

~~D10 Lua 数值 for~~ **已上移至 Stage C**，原因见下。

- D8：**先出语义说明书再动手**。fakescript 现在所有变量都是函数局部的吗？裸赋值是「赋值已存在变量」还是「创建」？这一条不定清楚就无法实施，建议单独一轮讨论

> **D10 上移的教训（回填）**：方案原本把 D10 判为 L1「新增一条文法规则即可」，实际做下去发现
> 文法确实只要 1 个 shift/reduce（58 → 59，0 reduce/reduce），但**语义对不上**：
> `OPCODE_FORBEGIN/FORLOOP` 判的是**半开区间** `[begin, end)`，而 Lua 的数值 for 是
> **闭区间** `[begin, end]`。若直接脱糖复用，`for i = 1, 4 do` 会跑成 1/2/3（和是 6 而非 10），
> 从 Lua 抄来的循环**静默少跑一次** —— 正是第 2 节那条「比报错更糟」的失败模式。
>
> 最终做法：新增 `OPCODE_FORBEGIN_CLOSED` / `OPCODE_FORLOOP_CLOSED` 两个变体，
> `interpreter` 用 fall-through 复用同一段代码只改判定方向（`>=`→`>`、`<`→`<=`），
> `for_loop_stmt` 加 `m_closed` 标志选择 opcode，既有的 `->` 写法保持半开不动。
>
> 判据更新：**凡是「写法一样但语义细节不同」的项，都不能按 L1 估算。** 复用现有 opcode
> 前必须先问「它对边界、类型、求值次数的约定和目标语言一致吗」。

### Stage C —— 表达式能力（真正的硬骨头）
**范围**：D10 Lua 数值 for（已做完）、D13 运行时表构造（已做完）、D11 比较作值、D12 布尔类型

D10 的落地形态：`FOR IDENTIFIER ASSIGN expr ARG_SPLITTER expr [ARG_SPLITTER expr] DO block END`
四条候选（带/不带步长 × 带/不带循环体），构造动作收在 `Yylex.make_lua_for`；
第一段用**裸 IDENTIFIER**而不是 `var`，否则会和 C 式 `for_stmt` 的 `FOR block` 前缀
抢 `local i = 1` 这一串的归约（那串既是 `var_stmt` 又是 `var ASSIGN assign_value`，
单记号 lookahead 分不开）。区间问题见 Stage B 的回填。

按 D11 拆出的四个子项推进：
1. `command.java` 新增约 8 个值模式 opcode（`LESS/MORE/EQUAL/MOREEQUAL/LESSEQUAL/NOTEQUAL/NOT` 及值模式 `AND/OR`）
2. `interpreter.java` 对应处理，走 `variant.less/more/...` 实例方法（这些方法**已经**通过 `set_cmp_ret` 产出 1/0）
3. `compiler.java:983 compile_cmp_stmt` 增加「产生值」的第二套 codegen
4. 定夺真假值表示——`set_cmp_ret` 现在 INT 操作数得 INT 1/0、否则得 REAL 1.0/0.0，意味着 `typeof(1 < 2)` 会随操作数类型漂移，这本身要先定死

**排序建议**：D13 运行时表构造先于 D11。`const` 版本已有 AST 与求值路径（`explicit_value_node` 的 `EVT_MAP`/`EVT_ARRAY`），复用到运行期比从零做比较值更稳，也先享受 Stage A 的成果。

> **D13 落地形态（回填）**：
> ```
> table_constructor : '{' '}' | '{' table_array_list '}' | '{' table_map_list '}'
> table_array_list  : expr_value | table_array_list ',' expr_value
> table_map_list    : IDENTIFIER '=' expr_value | table_map_list ',' IDENTIFIER '=' expr_value
> ```
> `table_constructor` 同时挂在 **`expr_value` 和 `expr`** 两个非终极符下（只挂前者够不到
> `local t = {1,2}`，见开头回填的第 1 条）。编译期在 `compile_table_constructor` 里脱糖成
> 临时变量 `__fktable_N` + `array()`/`map()` 调用 + 逐元素 `container_get` 赋值，
> 最后把临时变量作为表达式的值 —— **没有新增 opcode**，与原方案「L2 需构造 opcode」的预估不同，
> 这也说明"复用现有节点/指令"这条路比想象中宽。
>
> 支持范围（有测试锁定）：`{1,2,3}`、`{a=1,b=2}`、`{}`、元素为表达式或函数调用、嵌套
> （`{1,{2,3}}` 经临时变量取内层）、作实参（`size({1,2,3})`）、循环内构造。
> **不支持**（干净地 PARSE_FAIL，有用例锁定）：`{["a"]=1}` 表达式键、`{a=1, 2}` 混合键、
> `t[1][0]` 链式下标（既有限制，与 D13 无关）。
> 冲突数 59 → **69**（+10，全 shift/reduce，0 reduce/reduce）。

### 已知分歧：数值 for 的两处与 Lua 不一致（尚未修）

给 D10/D13 补 fuzz oracle 时顺手探出来的。**都不是 D10 引入的** —— 既有的 `->`
与 C 式写法同样如此，只是 Lua 化之后会有更多人照 Lua 的直觉来用，从而踩到：

| # | 写法 | Lua 5.4 | 本实现 | 严重度 |
|---|---|---|---|---|
| 1 | `for i = a, b, 0 do`（且 `a >= b`） | 报 `'for' step is zero` | **死循环**，跑满 `max_run_cmd_num`（1 亿条指令）才被掐断 | **高** |
| 2 | `for i = 1, n do ... n = 100 ... end` | 边界只求值一次，跑 3 次得 6 | `m_end` / `m_add` 每轮重新求值，一路跑到 100 得 5050 | 中 |

第 1 条的成因：`interpreter` 的 FORBEGIN 只在 `add > 0` 时才用 `<=` 判越界，
否则走 `>=` 分支；步长 0 落到后者，`a >= b` 时不跳转 → 进循环体 → `i += 0` 恒不变
→ 永远满足继续条件。嵌入式场景里这是实打实的拒绝服务面（宿主脚本卡死）。

第 2 条的成因：`compiler.compile_for_loop_stmt` 把 `m_end` / `m_add` **编译了两次**
（循环头一次、`continuepos` 处一次）。对 C 式 `for local i = 0, i < n, i++ do`
来说条件必须每轮求值，这是刻意设计；但 Lua 形态要求只求值一次。

两处都由 `LuaSyntaxTest` 里的 `testZeroStepIsNotRejected` 与
`testForBoundsAreReevaluatedEachIteration` 钉住当前行为。
**注意第 1 条只钉了 `a < b` 那个方向**（静默 0 次迭代）——`a >= b` 的方向故意不写成
用例，它会跑满一亿条指令把套件拖垮。

修法待定：
- 第 1 条可以在 FORBEGIN 里加「步长为 0 直接报错」，但那是行为变更
  （原本静默 0 次迭代的 `a < b` 情形会开始报错），需要确认是否接受
- 第 2 条若只改 Lua 形态（把边界提到隐藏临时变量里）则不影响既有写法，
  代价是 for 的 codegen 要分两条路

### Stage D —— 架构级（本方案只列不落地）
D16 一等函数与闭包、D14 泛型 for、D15 变长参数、D17 metatable、D18 协程

其中 D16 是分水岭：没有 upvalue 机制，闭包无从谈起；而一旦引入，函数调用、栈帧、常量池都要动。建议作为独立项目评估，不要搭在「语法对齐」的车上。

---

## 4. 每个 Stage 的验证方法（必须先落地再往前走）

1. **冲突数可度量**：基线 **57 shift/reduce、0 reduce/reduce**（`%expect 57` 恰好被满足），
   现为 **69**（Stage A +1、D10 +1、D13 +10）。每个 Stage 记录 delta；出现任何 reduce/reduce
   都要给出书面理由，不接受「bison 自己会选」
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
| 2 | Stage B 的 D10 数值 for | 小–中 | **已完成** |
| 3 | Stage C 的 D13 运行时表构造 | 中 | **已完成**（实际为 L1 脱糖，量级比预估小） |
| 4 | Stage C 的 D11 比较作值 + D12 布尔 | 中–大 | 下一步；先就「真假值表示」达成一致 |
| 5 | Stage B 的 D8 全局赋值 | 中 | **先出语义说明**，再决定 |
| 6 | Stage D | 大 | 单独立项 |

第一步落地前需要确认两点：是否接受「`local`/`function`/`do` 走别名」这种加法路线（而非替换），以及 Stage A 是否一次性全做完还是拆六个 PR。
（**已确认**：走替换路线且 Stage A 一次性做完，见开头纪要。）
