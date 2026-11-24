# Integration continuation review: defined 5.3.4 recovery

Reviewed on 2026-11-24 (Asia/Shanghai, UTC+8). This closes the specifically flagged
`finishCcall` / `finishPcallK` investigation with source reasoning and finite witnesses,
not an assertion of universal continuation or port equivalence.

## Independent failing witnesses, then fixes

All witnesses require stock 5.3.4 to exit zero and reach a completion sentinel,
then compare stdout byte-for-byte. No traceback/address masking or “both failed”
acceptance is used. The investigation used `tools/run_bounded.py --seconds 60/90`
around focused Maven invocations, before and after fixes. Tests are retained in
`ContinuationAuditTest` and `NativeContinuationAuditTest`.

| Defined case | Stock 5.3.4 | Before integration fix | Correction |
|---|---|---|---|
| Resumed xpcall raises literal `not enough memory` | `handled:not enough memory` | handler skipped | distinguish status `LUA_ERRMEM` from user string |
| Resumed xpcall handler itself raises | `error in error handling` | raw `handler-failed` | use recursive nonyielding message processing and preserve actual status |
| Resumed xpcall handler yields | protected failure, dead coroutine | yields `forbidden-handler`, suspended | message handlers always call through nonyieldable boundary |
| C `lua_pcall` with NULL continuation invokes yielding Lua | status 2, C-boundary error, dead after completion | yields `forbidden`; upper C frame has NULL continuation | conventional C API pcall uses nonyieldable protected call |
| Resumed handler inspects `debug.getinfo(2,'n').name` | `error` | `xpcall` | process handler while throwing frames still exist, before recover restores ci |
| Hook raises after protected callee yielded; next hook installed | replacement hook runs (`true`, sum 15) | replacement hook suppressed (`false`) | save/restore protected `allowhook` as CIST_OAH |

The NULL case is a **real, reachable non-GC mismatch**, not merely an imagined
invalid Java state. Fixing only a NULL check or trusting an earlier green suite
would have missed it. Native tests compile a small C module against the verified
stock headers, run the same binary on both engines, and cover native `lua_callk`,
`lua_pcallk`, NULL `k`, repeated yield/resume, status/context/result arity (including
nil results), error after resume, lexical-upvalue closing, hooks and message handlers.
A failed handler's C continuation receives **LUA_ERRERR (6)**, not LUA_ERRRUN (2).
There are six Lua witnesses plus four native dynamic cases. Native-disabled mode
runs the six Lua cases and creates no native cases, matching the existing native
factory convention; the CI gate explicitly requires the native class in its lane.

## Source correspondence and reachability

Compared verified `ldo.c::finishCcall` (518–537), `unroll` (548–559), `recover`
(581–595), `resume` (619–645), `lapi.c::lua_callk` / `lua_pcallk` (947–987), and
`ldebug.c::luaG_errormsg`. Java splits C's protected longjmp recovery across
`resumeControlled`, `prepareResumeError`, `precover`, `finishPcallK`, `finishCcall`.
The helper split is not itself a semantic difference.

1. A direct `lua_yieldk` without a continuation is legal. It is completed by
   `doResume` using resume arguments, just as stock `resume` does; it must not be
   confused with an **upper interrupted C frame** encountered by `unroll`.
2. Upper valid 5.3 C frames can suspend only through `callK`/`pcallK` with nonnull
   continuations, or the explicit standard-library continuation adapters (pcall,
   xpcall, dofile). `callK` without k already uses `callNoYield`; C API conventional
   pcall now uses `pcallNoYield`. Lua VM calls operate from Lua frames; C-entered
   metamethod calls use `callNoYield`. The forced-yield close caller belongs only to
   the dead 5.4 to-be-closed path below. `finishCcall` now fails fast on a missing
   continuation rather than inventing results and silently continuing.
3. `clsret` is assigned only by `moveResults` when `wanted < LUA_MULTRET (-1)`.
   Valid 5.3 API callers supply -1 or nonnegative result counts. The shim exports
   only the 5.3 API; legacy Java `Api.lua_toclose` is a no-op. No active source caller
   invokes `newTbcUpval`; `toBeClosed=true` is written only there. The parser's
   `getLocalAttribute` always returns VDKREG and rejects `<close>`/`<const>` syntax;
   the 47-opcode 5.3 VM has no TBC instruction. Thus this close path is not reachable
   from valid stock-language/API use (host reflection or arbitrary field mutation
   is outside that contract). The 5.4 clsret branch, pending-close-error handling,
   and unused close-recovery helper were removed from continuation completion.
4. Error handlers now run before `ci` restoration and before closing lexical
   upvalues, under the nonyieldable handler rule. Recovery restores the saved
   hook allowance, closes lexical upvalues, places one error at the protected
   function slot, shrinks the stack, clears YPCALL, and invokes k with the recorded
   status/context. Nested errors from a continuation go through the same path.
5. Host `ExecutionAbortedError` is not LuaError and never enters this recovery.
   Native C-function and continuation wrappers preserve it as Error; sticky controls
   remain shared across the state family. Existing bounded abort probes are rerun.

This is a targeted supported-entry-point argument plus a finite input matrix, not
formal verification. Other legacy 5.4-shaped helpers remain inventoried/unreviewed;
invalid C API arguments, arbitrary Java callback frame mutation, native-hook raw
callback boundaries, every stack-pressure/debug/ABI combination, and GC behavior
are not certified here. The modified broad `pcall` body was downgraded to unreviewed
in the ledger rather than inheriting stale whole-body fingerprints. The two focused
continuation entries receive only **behavior-tested** status for these witnesses.

## Reproduce

```sh
python3 tools/run_bounded.py --seconds 90 -- ./mvnw -B \
  -Dtest=ContinuationAuditTest,NativeContinuationAuditTest,FunctionAuditEvidenceTest,ExecutionControlTest,CoroutineTest \
  -Djavalua.requireReference=true -Djavalua.test.mode=native test-compile surefire:test
# Full unchanged two-lane and combined coverage gates:
sh tools/local_conformance.sh
```

The pre-fix logs are diagnostic local artifacts, not required for replay: the
assertions/scripts and comparison oracle are checked in. See VALIDATION.md for
final integrated counts and checks; do not infer them from this narrative.
