package com.github.esrrhs.javalua;

import java.time.Duration;

/**
 * Opt-in, host-owned cooperative limits for one Lua state and ALL its coroutines.
 * Obtain from {@link LuaState#executionControl()}. Disabled by default and independent
 * of Lua debug hooks. Not a memory limit or a hostile-code security boundary.
 *
 * <p>Only {@link #cancel()} and {@link #isAborted()} are safe from another Java thread. Other
 * operations require the execution thread (or an idle state with safe publication).
 * Reset is allowed only between calls/resumes, never after a terminal abort.
 * See tools/EMBEDDING_CONTROLS.md for the threat model and lifecycle.
 */
public final class ExecutionControl {
    private volatile boolean enabled;
    private volatile ExecutionAbortedError.Reason stopped;
    private long remaining = -1;
    private boolean timed;
    private long deadline;
    private int activeDepth;

    ExecutionControl() {
    }

    /**
     * Start a new cumulative allowance for this state family. A budget of -1 is
     * unlimited; zero permits no dispatched Lua instruction. A null timeout is
     * unlimited; zero expires immediately. The monotonic deadline starts NOW,
     * including time spent idle, yielded, or in callbacks. Negative values fail.
     *
     * @throws IllegalStateException if executing or already aborted
     * @throws IllegalArgumentException for an invalid budget or timeout
     */
    public synchronized void reset(long instructionBudget, Duration timeout) {
        if (activeDepth != 0 || stopped != null) {
            throw new IllegalStateException("cannot reset an executing or aborted Lua state");
        }
        if (instructionBudget < -1 || (timeout != null && timeout.isNegative())) {
            throw new IllegalArgumentException("invalid execution allowance");
        }
        long nanos;
        try {
            nanos = timeout == null ? 0 : timeout.toNanos();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("timeout does not fit nanoseconds", ex);
        }
        remaining = instructionBudget;
        timed = timeout != null;
        deadline = System.nanoTime() + nanos;
        enabled = instructionBudget >= 0 || timed;
    }

    /**
     * Irrevocably cancel this family, including suspended and future coroutines.
     * Thread-safe; observed at the next checkpoint, not an interrupt of a callback.
     */
    public void cancel() {
        stop(ExecutionAbortedError.Reason.CANCELLED);
    }

    /** Remaining dispatched instructions, or -1 for unlimited. Read only when idle. */
    public long remainingInstructions() {
        return remaining;
    }

    /** Whether this family has irreversibly stopped (safe from any thread). */
    public boolean isAborted() {
        return stopped != null;
    }

    /**
     * Cooperative check for trusted long-running Java callbacks. Does not consume
     * instructions; throws the same terminal signal as VM checkpoints.
     */
    public void checkpoint() {
        if (!enabled) {
            return;
        }
        ExecutionAbortedError.Reason reason = stopped;
        if (reason == null && timed && System.nanoTime() - deadline >= 0) {
            reason = stop(ExecutionAbortedError.Reason.DEADLINE);
        }
        if (reason != null) {
            throw new ExecutionAbortedError(reason);
        }
    }

    void instruction() {
        if (!enabled) {
            return; // One volatile read on the default, unlimited VM path.
        }
        checkpoint();
        if (remaining == 0) {
            throw new ExecutionAbortedError(stop(ExecutionAbortedError.Reason.INSTRUCTION_BUDGET));
        }
        if (remaining > 0) {
            remaining--;
        }
    }

    void enter() {
        checkpoint();
        activeDepth++;
    }

    void exit() {
        activeDepth--;
    }

    private synchronized ExecutionAbortedError.Reason stop(ExecutionAbortedError.Reason reason) {
        if (stopped == null) {
            stopped = reason;
        }
        enabled = true; // Publish the latch; reset holds this same monitor.
        return stopped;
    }
}
