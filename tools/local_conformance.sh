#!/bin/sh
# Full local equivalent of the two CI matrix lanes + combined 90% coverage gate.
# Prerequisites: JDK 21 on PATH (or JAVA_HOME), C compiler, pt_BR locale.
# First run: sh tools/prepare_reference.sh && sh native/build.sh
set -eu
cd "$(dirname "$0")/.."
export LUA53_BIN=${LUA53_BIN:-target/lua53/src/lua}
export LUAC53_BIN=${LUAC53_BIN:-target/lua53/src/luac}
export FKDUMP_BIN=${FKDUMP_BIN:-target/lua53/src/fkdump}
export TBLSTRUCT_BIN=${TBLSTRUCT_BIN:-target/lua53/src/tblstruct}
export LUA53_TESTS=${LUA53_TESTS:-target/lua53-tests}
BOUND_SECONDS=${BOUND_SECONDS:-1800}
mkdir -p target/local-conformance
# Only generated execution data is reset. Preserve per-lane reports/logs after each run.
rm -f target/jacoco.exec
rc=0
for mode in native java; do
  rm -rf target/surefire-reports
  extra=""
  minimum=1345
  gate=""
  if [ "$mode" = java ]; then
    extra="-Djavalua.nonative=true"
    minimum=1185
    gate="--nonative"
  fi
  python3 tools/run_bounded.py --seconds "$BOUND_SECONDS" -- ./mvnw -B test \
    -Djavalua.requireReference=true -Djavalua.test.mode="$mode" \
    -Djacoco.check.skip=true $extra > "target/local-conformance/$mode.log" 2>&1 || rc=1
  python3 tools/check_ci_gates.py "$minimum" $gate || rc=1
  if [ -d target/surefire-reports ]; then
    mkdir -p "target/local-conformance/$mode"
    cp -R target/surefire-reports/. "target/local-conformance/$mode/"
  fi
  if [ -f target/jacoco.exec ]; then
    cp target/jacoco.exec "target/local-conformance/$mode.exec"
  fi
done
./mvnw -B -DskipTests test -Djacoco.line.minimum=0.90 \
  > target/local-conformance/coverage.log 2>&1 || rc=1
printf 'Logs and per-mode reports: target/local-conformance (exit=%s)\n' "$rc"
exit "$rc"
