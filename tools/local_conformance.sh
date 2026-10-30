#!/bin/sh
# 本地镜像 CI 的 conformance job（.github/workflows/maven.yml）：备好参考二进制与上游
# 套件后，两条 io 路径各跑一遍，再按 CI 用的同一组数值卡「用例数」与「合并覆盖率」。
#
# 判定步骤与 CI 逐条一对一：
#   1. Run conformance tests                     -> 第一遍 native（有界）
#   2. Check the gates actually ran (1080)       -> check_ci_gates.py 1080
#   3. Run conformance tests (pure-Java io path) -> 第二遍 -Djavalua.nonative=true（有界）
#   4. Check the gates actually ran (900)        -> check_ci_gates.py 900 --nonative
#   5. Check coverage of both io paths together  -> -DskipTests test -Djacoco.line.minimum=0.95
# 覆盖率只卡<b>合并</b>这一档：CI 的两个测试步都带 -Djacoco.check.skip=true，单遍的
# 0.94（pom 默认值）在 conformance job 里从不上闸，合并的 0.95 才是这道闸门。
#
# 除判定外，只有两类差别，都是有意的：
#   * 墙钟上限：CI 每步 1800s（runner 慢），本地默认 900s——本机带 jacoco 的整遍实测
#     186s，留约 4 倍余量。用 BOUND_SECONDS 覆盖。
#   * 数值可覆盖：MIN_NATIVE / MIN_NONATIVE / COVERAGE_MIN_MERGED 默认就是 CI 用的那几个，
#     覆盖只为临时试验；默认值下「本地跑绿」与「CI 跑绿」是同一组判定。
#
# 还有两点只是执行环境差异，不引入额外判定：
#   * 跑之前清掉 target/jacoco.exec 与 target/site/jacoco、target/surefire-reports：CI 每次
#     是干净 checkout，本地复用工作区必须自己重置，否则 jacoco 合并的是历史累积。
#   * 每遍跑完用 `-DskipTests test` 重新渲染一次报告，只为读出这一遍的行覆盖率数字
#     （`-DskipTests` 不 fork 测试 JVM，不会往 jacoco.exec 里再塞 session）。这两步都带
#     -Djacoco.check.skip=true，只读不判。
#
# 为什么两遍测试步都带 -Djacoco.check.skip=true：任何单遍都不该拿合并门槛去卡（那等于逼
# 人删掉兜底代码），与 CI 一致；合并后的 0.95 在最后单独 check 一次。
#
# 平台差异（README「已知差异」）：本机 macOS 上 StringLibTest#hexFloatPrecisionRoundingFollowsGlibc
# 必然失败（比的是 glibc 的 NaN 舍入）。surefire 一失败 Maven 立即中止，绑在 test 阶段的
# jacoco check 也就不会执行——所以覆盖率判定一律靠上面那两步 -DskipTests 重新渲染；也因此
# check_ci_gates.py 在本机会因这 1 条失败判红，脚本退出码常态是 1。看本地结果时看三样：执行数
# 是否 ≥ 下限、覆盖率两个数字、失败的是不是只有这一类。Linux（CI）上这两遍是干净的。
#
# 前置：参考二进制和上游套件要先备好（同 CI 的构建步骤）：
#   curl -sL -o /tmp/lua.tar.gz https://www.lua.org/ftp/lua-5.4.7.tar.gz
#   tar xzf /tmp/lua.tar.gz -C target && mv target/lua-5.4.7 target/lua54
#   make -C target/lua54 -j4 posix MYCFLAGS=-DLUA_USE_DLOPEN
#   clang -O2 -I target/lua54/src -o target/lua54/src/fkdump \
#     src/test/resources/fkdump.c target/lua54/src/liblua.a -lm
#   clang -O2 -I target/lua54/src -o target/lua54/src/tblstruct \
#     src/test/resources/tblstruct.c target/lua54/src/liblua.a -lm
#   curl -sL -o /tmp/tests.tar.gz https://www.lua.org/tests/lua-5.4.7-tests.tar.gz
#   mkdir -p target/lua54-tests && tar xzf /tmp/tests.tar.gz -C target/lua54-tests --strip-components=1
#   sh native/build.sh
#
# 逗号小数点那一档 CI 要 locale-gen pt_BR（runner 只有 C 与 en_US），macOS 自带
# pt_BR.UTF-8 / pt_BR，所以 LocaleNumberTest 在本地 native 那遍是真的跑着的（8 条）。
set -e
cd "$(dirname "$0")/.."

export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}
export LUA54_BIN=${LUA54_BIN:-target/lua54/src/lua}
export FKDUMP_BIN=${FKDUMP_BIN:-target/lua54/src/fkdump}
export TBLSTRUCT_BIN=${TBLSTRUCT_BIN:-target/lua54/src/tblstruct}
export LUA54_TESTS=${LUA54_TESTS:-target/lua54-tests}

# 门禁参数：默认值 = CI 用的那一组（见 .github/workflows/maven.yml）。
BOUND_SECONDS=${BOUND_SECONDS:-900}
MIN_NATIVE=${MIN_NATIVE:-1080}
MIN_NONATIVE=${MIN_NONATIVE:-900}
# 合并覆盖率门槛，与 CI 最后一步同值（本机两遍合并实测 0.9568）。
COVERAGE_MIN_MERGED=${COVERAGE_MIN_MERGED:-0.95}

rc=0
fail() {
  echo "!! $1"
  rc=1
}

# 读刚生成的那份报告。文件不在就失败退出（陈旧数字比没有数字更糟）。
ratio() {
  [ -f "$1" ] || {
    echo "报告不存在：$1 —— 这一步没出成报告，它前面的失败才是根因" >&2
    exit 3
  }
  python3 - "$1" <<'PY'
import sys, xml.etree.ElementTree as ET
for c in ET.parse(sys.argv[1]).getroot().findall('counter'):
    if c.get('type') == 'LINE':
        m, cv = int(c.get('missed')), int(c.get('covered'))
        print('%.4f' % (cv / (m + cv)))
PY
}

# 清干净再开跑：执行数据要重新攒（否则合并的是历史累积），报告一律重建。
rm -f target/jacoco.exec
rm -rf target/site/jacoco target/surefire-reports

# ---- 第一遍：native（luashim 在，io 走 C 那侧）----
# -Djacoco.check.skip=true：单遍不该拿合并门槛去卡，与 CI 同；合并门槛留到最后一步。
python3 tools/run_bounded.py --seconds "$BOUND_SECONDS" -- \
  ./mvnw -B test -Djacoco.check.skip=true > /tmp/javalua-native.log 2>&1 \
  || fail "native 测试遍未干净退出（用例失败或超时），日志 /tmp/javalua-native.log"
echo "== native =="
python3 tools/check_ci_gates.py "$MIN_NATIVE" \
  || fail "native 那遍闸门自检未通过（上方执行数须 ≥ ${MIN_NATIVE}；macOS 上那条 glibc 平台差异用例同样会让它判红）"
# 只含 native 这一遍的执行数据留一份，便于与合并后的对比。
cp target/jacoco.exec /tmp/jacoco-native.exec
# 只读不判：渲染出这一遍的报告，读它的行覆盖率（CI 这一步是判定，这里只取数字）。
./mvnw -B -DskipTests test -Djacoco.check.skip=true \
  > /tmp/javalua-native-coverage.log 2>&1 || fail "native 单遍的报告没渲染出来，详见 /tmp/javalua-native-coverage.log"
RATIO_NATIVE=$(ratio target/site/jacoco/jacoco.xml) || fail "读不出 native 单遍的覆盖率报告"
echo "RATIO_NATIVE=$RATIO_NATIVE"

# ---- 第二遍：强制当作 luashim 装不上，io/os 走纯 Java 兜底 ----
rm -rf target/surefire-reports
python3 tools/run_bounded.py --seconds "$BOUND_SECONDS" -- \
  ./mvnw -B test -Djavalua.nonative=true -Djacoco.check.skip=true > /tmp/javalua-java.log 2>&1 \
  || fail "纯 Java 兜底那遍未干净退出（用例失败或超时），日志 /tmp/javalua-java.log"
echo "== java 兜底 =="
python3 tools/check_ci_gates.py "$MIN_NONATIVE" --nonative \
  || fail "兜底那遍闸门自检未通过（上方执行数须 ≥ ${MIN_NONATIVE}；macOS 上那条 glibc 平台差异用例同样会让它判红）"

# ---- 合并覆盖率：与 CI 的最后一步同形状 ----
# 两遍各写了一份 jacoco.exec（append），这里不跑测试，只用合并后的数据出报告并卡门槛。
# 那四个 export（LUA54_BIN/FKDUMP_BIN/TBLSTRUCT_BIN/LUA54_TESTS）不是多余的：pom 里的
# no-reference-lua profile 是按环境变量在不在决定跳不跳校验的，少了它就静默跳过，
# 覆盖率闸门又变回「绿着没跑」。
./mvnw -B -DskipTests test -Djacoco.line.minimum="$COVERAGE_MIN_MERGED" \
  > /tmp/javalua-coverage.log 2>&1 \
  || fail "两遍合并后的覆盖率未达 ${COVERAGE_MIN_MERGED}，详见 /tmp/javalua-coverage.log"
echo "== 合并覆盖率（门槛 ${COVERAGE_MIN_MERGED}）=="
RATIO_MERGED=$(ratio target/site/jacoco/jacoco.xml) || fail "读不出合并后的覆盖率报告"
echo "RATIO_MERGED=$RATIO_MERGED"
# 合并口径的证据：session 数应当正好等于跑过的测试遍数（2）。多于 2 说明
# jacoco 的执行数据里混进了别的行程，这个数字就不再是「两遍合并」。
echo "SESSIONS=$(grep -o '<sessioninfo ' target/site/jacoco/jacoco.xml | wc -l | tr -d ' ')"

if [ "$rc" -ne 0 ]; then
  echo
  echo "本地镜像 conformance：有未通过项（rc=${rc}）。注意 macOS 上那条 glibc NaN 用例"
  echo "必然失败，属于已知平台差异，不算移植回归。"
fi
exit "$rc"