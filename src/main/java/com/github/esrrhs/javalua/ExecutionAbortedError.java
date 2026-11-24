package com.github.esrrhs.javalua;

/**
 * Host-only, terminal resource-control signal, deliberately not a {@link LuaError}
 * or an {@link Exception}. Lua pcall/xpcall/resume must not turn this into a value.
 * Discard the entire state family after catching it; only close is supported.
 */
public final class ExecutionAbortedError extends Error {
    private static final long serialVersionUID = 1L;

    /** The first terminal condition observed for the state family. */
    public enum Reason {
        INSTRUCTION_BUDGET, DEADLINE, CANCELLED
    }

    private final Reason reason;

    ExecutionAbortedError(Reason reason) {
        super("Lua execution aborted: " + reason);
        this.reason = reason;
    }

    /** Returns the latched terminal condition. */
    public Reason reason() {
        return reason;
    }
}
