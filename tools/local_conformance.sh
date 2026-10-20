#!/bin/sh
# 本地镜像 CI 的 conformance job：参考二进制 + 上游套件 + 两条 io 路径各跑一遍。
# 顺带报出每一遍的用例数和行覆盖率（第二遍的 jacoco agent 是累加的，
# 所以 RATIO_MERGED 是两遍合起来的数字，等于 CI 合并后的门禁值）。
#
# 前置：参考二进制和上游套件要先备好（同 CI 的构建步骤）：
#   curl -sL -o /tmp/lua.tar.gz https://www.lua.org/ftp/lua-5.4.7.tar.gz
#   tar xzf /tmp/lua.tar.gz -C target && mv target/lua-5.4.7 target/lua54
#   make -C target/lua54 -j4 posix MYCFLAGS=-DLUA_USE_DLOPEN
#   clang -O2 -I target/lua54/src -o target/lua54/src/fkdump \
#     src/test/resources/fkdump.c target/lua54/src/liblua.a -lm
#   curl -sL -o /tmp/tests.tar.gz https://www.lua.org/tests/lua-5.4.7-tests.tar.gz
#   mkdir -p target/lua54-tests && tar xzf /tmp/tests.tar.gz -C target/lua54-tests --strip-components=1
#   sh native/build.sh
set -e
cd "$(dirname "$0")/.."

export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}
export LUA54_BIN=${LUA54_BIN:-target/lua54/src/lua}
export FKDUMP_BIN=${FKDUMP_BIN:-target/lua54/src/fkdump}
export LUA54_TESTS=${LUA54_TESTS:-target/lua54-tests}

ratio() {
  python3 - "$1" <<'PY'
import sys, xml.etree.ElementTree as ET
for c in ET.parse(sys.argv[1]).getroot().findall('counter'):
    if c.get('type') == 'LINE':
        m, cv = int(c.get('missed')), int(c.get('covered'))
        print('%.4f' % (cv / (m + cv)))
PY
}

# 第一遍：native（luashim 在，io 走 C 那侧）
rm -f target/jacoco.exec
rm -rf target/surefire-reports
./mvnw -B test > /tmp/javalua-native.log 2>&1 || echo "NATIVE_EXIT=$?"
echo "== native =="; python3 tools/check_ci_gates.py 0 || true
echo "RATIO_NATIVE=$(ratio target/site/jacoco/jacoco.xml)"
cp target/jacoco.exec /tmp/jacoco-native.exec

# 第二遍：强制当作 luashim 装不上，io/os 走纯 Java 兜底
rm -rf target/surefire-reports
./mvnw -B test -Djavalua.nonative=true > /tmp/javalua-java.log 2>&1 || echo "JAVA_EXIT=$?"
echo "== java 兜底 =="; python3 tools/check_ci_gates.py 0 --nonative || true
echo "RATIO_MERGED=$(ratio target/site/jacoco/jacoco.xml)"
