package com.github.esrrhs.javalua;

import java.util.Locale;

/**
 * 把 {@link Proto} 转成与 {@code fkdump}（参考实现侧那个小转储器）
 * <b>逐字节相同</b>的文本。
 * <p>
 * 为什么自造格式而不用 {@code luac -l}：后者是给人看的，每个 opcode 打的字段
 * 不一样、还夹着 {@code ; _ENV "print"} 这类注释，要反解回原始字段就得把
 * 那个大 switch 抄一遍——抄错一处，比对结论就废了。这里对<b>所有</b> opcode
 * 统一打印全部字段，两边各写一遍也不会写歪。
 * <p>
 * 浮点常量打<b>位模式的十六进制</b>：十进制格式化在 C 与 Java 之间差异极大
 * （{@code %g} 去不去尾零、{@code NaN} 怎么打），位模式没有这些分歧。
 */
final class ProtoDump {

    private ProtoDump() {
    }

    static String dump(Proto f) {
        StringBuilder sb = new StringBuilder();
        dumpInto(f, 0, 0, sb);
        return sb.toString();
    }

    private static void dumpInto(Proto f, int level, int index, StringBuilder sb) {
        sb.append(String.format(Locale.ROOT,
                "PROTO level=%d index=%d linedefined=%d lastlinedefined=%d\n",
                level, index, f.linedefined, f.lastlinedefined));
        sb.append(String.format(Locale.ROOT,
                "  numparams=%d isvararg=%d maxstacksize=%d nups=%d nconsts=%d nsubs=%d\n",
                f.numparams, f.isVararg ? 1 : 0, f.maxstacksize,
                f.sizeupvalues, f.sizek, f.sizep));
        for (int i = 0; i < f.sizecode; i++) {
            int inst = f.code[i];
            sb.append(String.format(Locale.ROOT,
                    "  I %d %s a=%d b=%d c=%d k=%d bx=%d sbx=%d ax=%d sb=%d sc=%d sj=%d line=%d\n",
                    i, OpCode.NAMES[OpCode.getOpCode(inst)],
                    OpCode.getArgA(inst), OpCode.getArgB(inst), OpCode.getArgC(inst),
                    OpCode.getArgK(inst), OpCode.getArgBx(inst), OpCode.getArgSBx(inst),
                    OpCode.getArgAx(inst), OpCode.getArgSB(inst), OpCode.getArgSC(inst),
                    OpCode.getArgSJ(inst), f.getFuncline(i)));
        }
        for (int i = 0; i < f.sizek; i++) {
            sb.append("  K ").append(i).append(' ').append(constant(f.k[i])).append('\n');
        }
        for (int i = 0; i < f.sizeupvalues; i++) {
            Proto.Upvaldesc up = f.upvalues[i];
            sb.append(String.format(Locale.ROOT, "  UP %d instack=%d idx=%d kind=%d name=",
                    i, up.instack ? 1 : 0, up.idx, up.kind));
            sb.append(up.name == null ? "?" : escape(up.name));
            sb.append('\n');
        }
        for (int i = 0; i < f.sizep; i++) {
            dumpInto(f.p[i], level + 1, i, sb);
        }
    }

    private static String constant(LuaValue v) {
        if (v.isNil()) {
            return "nil";
        }
        if (v.isBoolean()) {
            return "bool:" + (v.asBoolean() ? 1 : 0);
        }
        if (v.isInteger()) {
            return "int:" + v.i;
        }
        if (v.isFloat()) {
            return String.format(Locale.ROOT, "flt:%016x", Double.doubleToRawLongBits(v.n));
        }
        if (v.isString()) {
            return "str:" + escape(v.asString());
        }
        return "other";
    }

    /** 与 {@code fkdump.c} 的 {@code printString} 同一套转义 */
    private static String escape(LuaString s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            int c = s.byteAt(i) & 0xFF;
            if (c == '\\' || c == '"') {
                sb.append(String.format(Locale.ROOT, "\\%02x", c));
            } else if (c >= 0x20 && c < 0x7f) {
                sb.append((char) c);
            } else {
                sb.append(String.format(Locale.ROOT, "\\%02x", c));
            }
        }
        return sb.toString();
    }
}
