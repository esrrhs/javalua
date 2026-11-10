#!/bin/sh
# Exact PUC-Rio 5.3.4 oracle + helpers + upstream corpus. Safe to rerun.
set -eu
cd "$(dirname "$0")/.."
mkdir -p target/reference-downloads target/lua53 target/lua53-tests
fetch() {
  file="target/reference-downloads/$1"
  if ! printf '%s  %s\n' "$2" "$file" | sha256sum -c - >/dev/null 2>&1; then
    curl -fsSL --retry 5 --retry-all-errors --retry-delay 5 --connect-timeout 20 --max-time 120 -o "$file" "$3"
  fi
  printf '%s  %s\n' "$2" "$file" | sha256sum -c -
}
fetch lua-5.3.4.tar.gz f681aa518233bc407e23acf0f5887c884f17436f000d453b2491a9f11a52400c https://www.lua.org/ftp/lua-5.3.4.tar.gz
fetch lua-5.3.4-tests.tar.gz b80771238271c72565e5a1183292ef31bd7166414cd0d43a8eb79845fa7f599f https://www.lua.org/tests/lua-5.3.4-tests.tar.gz
# Re-extract even on a reused workstation: never trust an edited reference tree.
tar xzf target/reference-downloads/lua-5.3.4.tar.gz -C target/lua53 --strip-components=1
make -C target/lua53 clean
make -C target/lua53 -j2 posix MYCFLAGS=-DLUA_USE_DLOPEN MYLIBS="-Wl,-E -ldl"
for helper in fkdump tblstruct; do
  "${CC:-cc}" -O2 -I target/lua53/src -o "target/lua53/src/$helper" "src/test/resources/$helper.c" target/lua53/src/liblua.a -lm
done
tar xzf target/reference-downloads/lua-5.3.4-tests.tar.gz -C target/lua53-tests --strip-components=1
# Fail closed; shell and JVM checks both require the exact patch version.
for binary in lua luac; do
  version=$("target/lua53/src/$binary" -v 2>&1)
  case "$version" in 'Lua 5.3.4 '*) printf '%s\n' "$version";; *) echo "wrong oracle: $version" >&2; exit 1;; esac
done
target/lua53/src/lua -e 'assert(package.loadlib); assert(_VERSION=="Lua 5.3")'
