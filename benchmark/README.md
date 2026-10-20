# benchmark

跑 Lua 脚本的量级计时工具。**这里的东西不是闸门** —— 项目的正确性闸门是
`src/test` 那批与真实 lua5.4 逐字节比对 stdout 的差分测试，本目录只用于本机
「快了多少 / 慢了多少」的粗略比较。

These scripts are local ballpark timing only; they are deliberately not part of
CI. Nothing here is byte-comparable with the reference implementation.

## 怎么跑

```bash
./mvnw -q -o compile                      # 先出 target/classes
javac -cp target/classes -d /tmp/bench benchmark/RunBenchmark.java
java -cp target/classes:/tmp/bench RunBenchmark benchmark/gc_simple.lua
```

输出长这样（`elapsed_ms` 在 stderr，脚本自己的输出在 stdout）：

```
=== 汇总 ===
构造耗时: 3.39s
GC 耗时: 0.0008s
节点数: 1000000
script=gc_simple.lua elapsed_ms=12503
```

要和参考实现比，就同一份脚本再跑一遍真实 lua，注意两边的堆/内存上限都要
给够，否则比的是「谁先 OOM」：

```bash
time /path/to/lua5.4 benchmark/gc_simple.lua > /dev/null
```

## 脚本清单

| 脚本 | 在量什么 | 用什么跑 | 坑 |
|---|---|---|---|
| `test_simple.lua` | 冒烟：确认 harness 通 | `RunBenchmark` | 没有性能信息 |
| `gc_simple.lua` | 构造 100 万个 table 节点的耗时 | `RunBenchmark`，建议 `-Xmx1g` | 脚本里 `collectgarbage("count")` 与 `os.clock` 的读数与 C 不同，见下 |
| `gc_precise.lua` | 同上，毫秒级计时 | 同上 | 同上 |
| `gc_benchmark.lua` | 目标「构造约 1GB 网状对象」后跑一轮 GC | 需要 `-Xmx` 明显大于 1GB | 1GB 是按 Lua 分配器的字节算的，JVM 堆不是同一个单位；节点数到百万级时在 CI 上会 OOM |

## 为什么这里没有数字，也进不了 CI

三条理由，都是踩过的坑：

1. **内存数字两边不可比**。C 的 `collectgarbage("count")` 数的是 Lua 分配器
   手里的字节；JVM 这边堆由 GC 管，绝对值与参考实现必然不同（README 的
   「已知差异」里也记了这条）。曾经有个 `GcPreciseBenchmarkTest` 就是这么比的，
   删了；`RunBenchmark` 现在只报 `elapsed_ms`，不报内存增量。
2. **百万级节点会把 CI 挤爆**。基准脚本一跑就是几十分钟或直接把 runner 打
   OOM，而 job 红掉的原因跟代码好坏无关。
3. **计时进闸门等于自找 flaky**。JVM 有 JIT 预热，第一次和第十次跑差好几倍；
   逐字节比对的闸门可以严格，时间比对不行。仓库里现在没有正经的微基准框架，
   要测性能请引入 JMH（或用 JFR / async-profiler 现场采），别把这个目录的
   `elapsed_ms` 当成回归线。

## 加新脚本要注意的

- 别在脚本里依赖 `collectgarbage("count")` 的**绝对值**，它只能看相对变化。
- 百万级以上的分配量单独标在表格里，并写清需要的 `-Xmx`。
- 脚本要保持能在真实 lua5.4 上原样跑，否则连「量级比较」这个用途都没了。
