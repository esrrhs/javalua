#!/bin/sh
# Additive, native-only real upstream extension gate; ordinary matrix is untouched.
# Prerequisite: sh tools/prepare_reference.sh (exact stock 5.3.4 + dlopen).
set -eu
cd "$(dirname "$0")/.."
export LC_ALL=C
export LUA53_BIN="$PWD/target/lua53/src/lua"
REPORT="$PWD/reports/c-extension/latest"
XML=TEST-com.github.esrrhs.javalua.RealExtensionCompatibilityIT.xml
mkdir -p "$REPORT"
# Remove only this gate's prior result so no failed startup can reuse a green XML.
rm -f "target/surefire-reports/$XML" "$REPORT/$XML" "$REPORT/tests.log" \
  "$REPORT/shim-build.log" "$REPORT/module-build.log" "$REPORT/tooling-tests.log"
python3 -m unittest discover -s tools -p test_c_extension_tools.py \
  > "$REPORT/tooling-tests.log" 2>&1
sh native/build.sh > "$REPORT/shim-build.log" 2>&1
python3 tools/prepare_c_extensions.py > "$REPORT/module-build.log" 2>&1
status=0
python3 tools/run_bounded.py --seconds "${C_EXTENSION_SECONDS:-300}" -- \
  ./mvnw -B -Dtest=RealExtensionCompatibilityIT -Djacoco.skip=true test \
  > "$REPORT/tests.log" 2>&1 || status=$?
if [ -f "target/surefire-reports/$XML" ]; then
  cp "target/surefire-reports/$XML" "$REPORT/$XML"
fi
if [ "$status" -ne 0 ]; then
  printf 'Real extension tests failed (exit=%s); see %s\n' "$status" "$REPORT" >&2
  exit "$status"
fi
python3 tools/check_c_extension_gate.py "$REPORT/$XML"
printf 'Real C extension logs/reports: %s\n' "$REPORT"
