#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""闸门自检：确认刚才那一遍 `mvnw test` 真的把该跑的都跑了。

为什么要有这个脚本：本仓库的一致性闸门全靠
`assumeTrue(ReferenceLua.available())` 之类的条件跳过。它本身是对的
（没有参考二进制时只能跳），但**跳过的用例在报告里和跑过的长得一样**——
`LUA53_BIN` 指错、上游套件没下载、环境变量没透传，都会让 job 绿着把
几百条差分用例全部跳过。历史上这个项目已经吃过一次「连续 8 次 push
consistency job 静默没跑」。所以每次跑完都要把 surefire 的 XML 数一遍。

用法：

    tools/check_ci_gates.py <最少执行用例数> [--nonative] [--dir=target/surefire-reports]

* `<最少执行用例数>`：本轮必须**执行**（非 skip）的用例总数下限。
* `--nonative`：这是 `-Djavalua.nonative=true` 那一遍。C API 那批用例
  在没有 native 层时是零用例返回的（不是 skip，是根本没有 dynamic test），
  所以那些类不要求出现。
"""

import glob
import os
import sys
import xml.etree.ElementTree as ET

# 无论哪一遍都必须真的执行过的类：缺一个就说明对应的闸门整块没跑。
ALWAYS_REQUIRED = [
    "ExecutionModeTest",
    "GeneratedDiffTest",
    "FuzzTest",
    "CodegenTest",
    "DumpTest",
    "ConformanceTest",      # 上游套件 + 逐字节 stdout 比对
    "RuntimeTest",          # 语言语义差分
    "SyntaxE2eTest",        # 语法 / 语言特性端到端差分（不含 GC）
    "IoLibTest",            # io 库差分（两条路径都要过）
    "OsLibTest",            # os 库差分
    "DebugTest",            # debug 库与 traceback 差分
    "BinaryChunkTest",      # 二进制 chunk 兼容
    # 表结构快照差分：C 侧 tblstruct 转储器与本实现的字段逐字节比。
    # 它不依赖 native 层（只要 tblstruct 二进制在就出活），两条路径都要跑，
    # 所以是 ALWAYS。历史上它靠 assumeTrue 找二进制，找不到就整类静默跳过——
    # 那正是这个脚本要盯的形状。
    "TableStructTest",
    # All semantic files, including literals.lua, must run in both modes.
    # Only the two native locale capability probes are restricted in Java mode.
    "UpstreamSuiteTest",
]

# 只有 native 层装得起来才有意义的类。
NATIVE_REQUIRED = [
    "NativeApiTest",
    "NativeExtTest",
    # locale 小数点那批：既要有 shim（os.setlocale 得真能改 libc 的 locale），
    # 也要机器上装了 pt_BR。CI 的 locale-gen 步骤一漏，这个类就变成零用例。
    "LocaleNumberTest",
    # C API 符号集对撞（nm 抽公开符号，与参考库比一个都不能少）。
    # 这个是被自己坑过一次才加进来的：它的参考库默认只认 /tmp/lua-5.3.4，
    # 而 CI 把 lua 建在 target/lua53，于是这道闸门在 CI 上一直是 skipped，
    # maven 与旧的名单都看不见。现在路径候选补上了，名单也盯着它。
    "NativeSymbolTest",
]


# 5.3.4 重对准后，Conformance 在参考二进制齐全时仍会固定跳过这 5 条（GC 相关）：
# GcTest×3、Runtime 弱表/__gc、
# UpstreamSmoke 的 gc 烟测。它们是 @Disabled / assumeTrue(false)，不是缺环境。
# 多出来的跳过才说明 LUA53_* / 上游套件没接通。
ALLOWED_SKIPS = {
    # Dump byte compares re-enabled after 5.3.4 codegen align.
    "GcTest.collectFinishesEveryFinalizerUnderStarvation",
    "GcTest.gcRulesWithoutCollection",
    "GcTest.gcScriptsMatchReference",
    "RuntimeTest.weakTableAndFinalizerMatrixMatchesReference",
    "UpstreamSmokeTest.gcRunsThroughFinalizers",
}


def summarize(reports_dir):
    files = sorted(glob.glob(os.path.join(reports_dir, "TEST-*.xml")))
    if not files:
        die("没有找到任何 surefire 报告：%s —— 测试根本没跑，或者跑在别处"
            % reports_dir)
    per_class = {}
    skipped_names = []
    total = skipped = failed = 0
    for f in files:
        suite = ET.parse(f).getroot()
        name = suite.get("name", "").split(".")[-1]
        t = int(suite.get("tests", 0))
        s = int(suite.get("skipped", 0))
        e = int(suite.get("errors", 0)) + int(suite.get("failures", 0))
        per_class[name] = (t, s, e)
        total += t
        skipped += s
        failed += e
        for tc in suite.findall("testcase"):
            if tc.find("skipped") is not None:
                skipped_names.append("%s.%s" % (name, tc.get("name", "")))
    return per_class, total, skipped, failed, skipped_names


def die(msg):
    print("闸门自检失败: " + msg, file=sys.stderr)
    sys.exit(1)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if not args:
        die("用法: check_ci_gates.py <最少执行用例数> [--nonative] [--dir=...]")
    min_tests = int(args[0])
    nonative = "--nonative" in sys.argv
    dirs = [a.split("=", 1)[1] for a in sys.argv[1:] if a.startswith("--dir=")]
    reports_dir = dirs[0] if dirs else "target/surefire-reports"

    per_class, total, skipped, failed, skipped_names = summarize(reports_dir)
    executed = total - skipped
    print("surefire: %d 类 / 用例 %d / 跳过 %d / 执行 %d (下限 %d)"
          % (len(per_class), total, skipped, executed, min_tests))

    if failed:
        die("%d 条用例失败或报错，本该由 maven 拦下——报告却走到了这一步" % failed)
    if executed < min_tests:
        die("执行的用例只有 %d，低于下限 %d：八成是差分用例整类跳过了"
            "（检查 LUA53_BIN / FKDUMP_BIN / TBLSTRUCT_BIN / LUA53_TESTS"
            " 是否透传）" % (executed, min_tests))

    missing = [c for c in ALWAYS_REQUIRED if per_class.get(c, (0, 0, 0))[0]
               - per_class.get(c, (0, 0, 0))[1] <= 0]
    if not nonative:
        missing += [c for c in NATIVE_REQUIRED
                    if per_class.get(c, (0, 0, 0))[0]
                    - per_class.get(c, (0, 0, 0))[1] <= 0]
    # Dynamic factories can silently return too few cases without a skipped marker.
    for cls, minimum in {"FuzzTest": 3, "GeneratedDiffTest": 64, "UpstreamSuiteTest": 21}.items():
        t, s, _ = per_class.get(cls, (0, 0, 0))
        if t - s < minimum:
            missing.append("%s (need >= %d executed)" % (cls, minimum))
    if missing:
        die("这些闸门类一条用例都没执行: %s" % ", ".join(sorted(set(missing))))

    # 跳过白名单：5.3.4 重对准固定 5 条（见 ALLOWED_SKIPS）。参考环境齐全时
    # 不应再出现 CodegenTest/TableStructTest 因缺二进制而跳过；多出来的跳过
    # 说明有闸门整片没接通。
    unexpected = [n for n in skipped_names if n not in ALLOWED_SKIPS]
    if unexpected:
        die("出现未登记的跳过 %d 条（允许的是 5.3.4 固定 %d 条）：%s"
            % (len(unexpected), len(ALLOWED_SKIPS), ", ".join(unexpected)))
    if skipped > len(ALLOWED_SKIPS):
        die("跳过了 %d 条用例，超过允许的 %d 条（%s）"
            % (skipped, len(ALLOWED_SKIPS), ", ".join(sorted(ALLOWED_SKIPS))))

    print("闸门自检通过。")


if __name__ == "__main__":
    main()
