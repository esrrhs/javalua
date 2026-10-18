#!/bin/sh
# 编 luashim —— 那个把 lua_* / luaL_* 全部导出去的动态库。
#
# 产物放到 src/main/resources/native/<平台>/ 下，NativeBridge 从 classpath
# 里找到它、解到临时目录再 System.load。换平台就重跑一次；没编译器也能跑
# （只是 package.loadlib 走兜底分支）。
#
# 需要先跑 gen_capi.py 生成 luashim_ids.h / luashim_stubs.inc；本脚本会自己
# 检查，缺了就生成。
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(dirname "$HERE")

CC=${CC:-cc}
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home 2>/dev/null || echo "")}

if [ -z "$JAVA_HOME" ]; then
  # 兜底：从 javac 反推
  JAVAC=$(command -v javac || true)
  [ -n "$JAVAC" ] && JAVA_HOME=$(dirname "$(dirname "$(readlink -f "$JAVAC")")")
fi
if [ -z "$JAVA_HOME" ] || [ ! -f "$JAVA_HOME/include/jni.h" ]; then
  echo "找不到 JNI 头文件：请设置 JAVA_HOME" >&2
  exit 1
fi

OS=$(uname -s)
ARCH=$(uname -m)
case "$OS" in
  Darwin) PLAT="darwin"; EXT="dylib"; LIB="libluashim.dylib" ;;
  Linux)  PLAT="linux";  EXT="so";    LIB="libluashim.so" ;;
  CYGWIN*|MINGW*|MSYS*) PLAT="windows"; EXT="dll"; LIB="luashim.dll" ;;
  *) echo "不支持的系统: $OS" >&2; exit 1 ;;
esac
case "$ARCH" in
  arm64|aarch64) PLAT="$PLAT-arm64" ;;
  x86_64|amd64)  PLAT="$PLAT-x86_64" ;;
  *) PLAT="$PLAT-$ARCH" ;;
esac

OUT="$ROOT/src/main/resources/native/$PLAT"
mkdir -p "$OUT"

[ -f "$HERE/luashim_ids.h" ] || python3 "$HERE/gen_capi.py"

INCLUDE="-I$HERE/lua -I$JAVA_HOME/include"
case "$OS" in
  Darwin) INCLUDE="$INCLUDE -I$JAVA_HOME/include/darwin" ;;
  Linux)  INCLUDE="$INCLUDE -I$JAVA_HOME/include/linux" ;;
  *)      INCLUDE="$INCLUDE -I$JAVA_HOME/include/win32" ;;
esac

echo "编 $PLAT/$LIB  (CC=$CC, JAVA_HOME=$JAVA_HOME)"
# shellcheck disable=SC2086
$CC -shared -fPIC -O2 -Wall $INCLUDE -o "$OUT/$LIB" "$HERE/luashim.c"
echo "-> $OUT/$LIB"
