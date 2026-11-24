# Opt-in embedding execution controls

These Java-only APIs do **not** change normal Lua 5.3.4 execution or
`LuaLibs.openAll`. They are cooperative host resource controls and capability
reduction, **not a secure hostile-code sandbox**.

## Host example

```java
LuaState state = LuaLibs.newRestricted(java.util.EnumSet.of(
    LuaLibs.RestrictedLibrary.COROUTINE,
    LuaLibs.RestrictedLibrary.TABLE,
    LuaLibs.RestrictedLibrary.STRING,
    LuaLibs.RestrictedLibrary.MATH));
ExecutionControl control = state.executionControl();
try {
    // Start the wall-clock deadline now, not on first VM entry.
    control.reset(100_000, java.time.Duration.ofMillis(100));
    int function = state.top;
    state.pushClosure(state.load("return 6 * 7".getBytes(
        java.nio.charset.StandardCharsets.UTF_8), "=embedded"));
    LuaValue ordinaryLuaError = state.pcall(function, 1);
    // Handle ordinaryLuaError or consume the result normally.
} catch (ExecutionAbortedError aborted) {
    // Host-only terminal signal: log aborted.reason(), discard this ENTIRE family.
    // Do not translate it to a Lua error, attempt recovery, or reuse its coroutines.
} finally {
    state.close();
}
```

An independent watchdog thread can call `control.cancel()`. Only `cancel()` and
`isAborted()` support concurrent access; the Lua state itself remains single-threaded.
A trusted long-running Java callback may call `control.checkpoint()` cooperatively.
No production-wide system properties or process-global switches are introduced.

## Accounting, lifecycle and reset

* A new state's control is disabled: instruction budget `-1`, no deadline.
* `reset(instructions, timeout)` starts a new cumulative allowance. `-1` means
  unlimited instructions, `0` allows no primary VM opcode dispatch, and a positive
  value allows that many dispatch attempts. Auxiliary bytecode words and
  instructions consumed inside an opcode are not separate dispatches. This is a
  deterministic VM-work allowance, not a CPU-time unit. Debug-hook execution is
  charged too (a hook may interrupt a dispatch attempt).
* `timeout == null` means no deadline; `Duration.ZERO` expires immediately.
  Negative budgets below `-1`, negative timeouts and durations that overflow
  nanoseconds are rejected. Deadlines use `System.nanoTime`, not the civil clock.
* Every existing and future coroutine in a state family shares **the same control
  object**, including main-thread work and nested `resume` / `wrap`. Yield, resume,
  normal return, `pcall`, `xpcall`, ordinary Lua errors and replacing/removing debug
  hooks do not replenish the allowance. Idle/yielded time counts toward a deadline.
* The host may reset the allowance **only while no call/resume is active**, including
  while all coroutines are suspended. Reset during a Java callback fails. A successful
  idle reset affects all suspended coroutines. Reset to `(-1, null)` disables limits
  again if no abort has occurred. Reset is not safe concurrently with execution.
* Once a checkpoint observes expiry/exhaustion, or `cancel()` is called, the first
  terminal reason is latched permanently. Cancellation also works on an initially
  unlimited state, before execution, and while suspended. No reset/rearm is allowed
  after an abort; construct a fresh state for the next job. Independent states have
  independent budgets and cancellation. The allowance is never a reusable global.
* Enforcement is outside Lua debug hooks. `ExecutionAbortedError extends Error`,
  not `LuaError`/`Exception`, and passes through protected calls, error handlers and
  coroutine resumes to the Java host. It must never be swallowed or converted by a
  host callback. A state that aborts is intentionally **not** stack-recovered: arbitrary
  nested execution can be interrupted. Further call/resume/checkpoint attempts fail.
  `close()` on an aborted state skips script finalizers instead of re-entering script;
  release host resources independently. Closing a normally completing state retains
  the usual finalizer semantics and those finalizers still run under its allowance.

## Restricted library configuration

`LuaLibs.newRestricted(Set<RestrictedLibrary>)` is deliberately a **fresh-state
factory**, not a blacklist/sanitize operation on an already opened environment.
It opens an explicit minimal base allowlist (never registering `load`, `loadfile`,
`dofile`, `print`, `collectgarbage`); the only
optional libraries are `COROUTINE`, `TABLE`, `STRING`, `MATH`, `UTF8`, `BIT32`.
An empty set gives minimal base only. It never opens `package`, `debug`, `io`, or
`os`, never installs `require`, and never records those modules in `_LOADED` or
preload registries. Merely hiding globals after `openAll` would leave aliases in
`package.loaded`/`debug.getregistry`; this factory avoids creating them at all.
Ordinary base operations such as `pcall`, metatables and raw table access remain.

The trusted Java host loads source using `state.load(...)`, bounds its size and
format before loading, and chooses what values/functions to provide. Do not call
`openAll`, open denied libraries, expose the registry/native API, or inject a closure,
userdata, metatable or table retaining ambient capabilities and then expect the
factory to revoke those references. Host-side registry access intentionally remains
available. No automatic copying or revocation of injected objects is attempted.

## Threat model and hard limitations

Intended use: contain accidental/uncooperative **Lua VM instruction loops** and
reduce ambient standard-library capabilities in an embedded, trusted JVM application.
Even the restricted profile is **not** a secure isolation boundary for hostile code.

* Checks occur at primary VM dispatches, call/resume entry and completion, and around
  Java/native function calls. Timeouts/cancellation are cooperative, not hard real-time.
  A blocking or CPU-bound Java/C callback or a single long library operation is not
  preempted. A callback that never returns can defeat the deadline forever. Trusted
  callbacks must be bounded/cooperative; use a separate process for a hard kill limit.
* No heap quota is implemented. Large tables/strings, parser/undumper allocations,
  pattern matching, sorting and other single library operations may consume memory
  or CPU between checks. An instruction allowance does not imply an allocation bound.
  Loading/parsing is not VM dispatch and is not interrupted by these controls. Bound
  source/chunk size and prohibit untrusted binary chunks at the host boundary.
* JVM `OutOfMemoryError`, stack exhaustion, VM bugs, native crashes, reflection exposed
  by a callback, process exits and unsafe native extensions are not contained.
* Lua code, host callbacks and native extensions share a process and address space.
  Callbacks/extensions are trusted: one that catches `Throwable` and loops, disables
  controls through host APIs, or exposes filesystem/process access defeats the model.
  Skipped finalizers after abort mean Lua-owned external resources need host cleanup.
* For hostile tenants or strict memory/wall-clock guarantees, use OS-isolated worker
  processes with memory/CPU limits, bounded IPC, restricted filesystem/network, and an
  external watchdog. Do not rely on Java thread interruption or unsafe thread stopping.

## Regression tests and integration

`ExecutionControlTest` covers default semantics, finite normal code, exact cumulative
accounting, idle reset/unlimited reset, invalid configuration, reset rejection during
execution/after abort, cancellation, state isolation, coroutine sharing across host
resumes, deadline at idle/callback return, and restricted globals/loaded registries.
Adversarial infinite loops run in child JVMs with an **external 8-second host timeout**
and forced process cleanup: plain loops, pcall/xpcall/error handlers, coroutine
resume/wrap/recreation, debug hook removal, loops inside hooks, deadline and asynchronous
cancellation. Native-enabled runs additionally compile a tiny C probe and verify
that `lua_pcall` and a yielded C continuation preserve the host abort across JNI,
without relying on a later VM checkpoint to mask a Lua-error conversion. Thus a
regression cannot permanently hang the test runner.

With JDK 21 and a prepared Lua 5.3.4 reference:

```sh
./mvnw -B -Dtest=ExecutionControlTest,CoroutineTest,ErrorPropagationTest,LibraryCallbackTest,LuaLibsTest,GeneratedDiffTest \
  -Djavalua.requireReference=true test-compile surefire:test
# Repeat under -Djavalua.nonative=true.
# Integration keeps the existing full native/nonative suite, coverage and SpotBugs gates:
sh tools/local_conformance.sh
```

No pom/CI changes, gate reduction, new broad skip, external dependency or C ABI change
is required. Parents integrating other work should retain the single dispatch check
in `Vm.execute` and shared-control initialization in `LuaState`, and run the existing
full two-backend conformance/coverage gates. The controlled Java API adds no Lua global
and does not change the Lua 5.3.4 C API. Existing uncapped differential tests remain
mandatory; restricted mode intentionally removes capabilities and is separately tested.
