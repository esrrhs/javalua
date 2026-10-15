package com.github.esrrhs.fakescript.lua;

import java.util.Arrays;

/**
 * 函数原型 —— 对应 {@code lobject.h} 的 {@code Proto}，以及 {@code lfunc.c} 里的
 * 一部分访问函数。
 * <p>
 * 一个 Proto 就是一段编译产物：指令、常量表、内嵌函数、upvalue 描述、局部变量
 * 描述、以及行号信息。运行期 {@code LClosure} 引用它，同一个 Proto 可以被多个
 * 闭包共享（{@code local f = function() end} 在循环里就会共享）。
 * <p>
 * 没有 {@code CommonHeader} 与 {@code gclist}——那是给 {@code lgc.c} 用的链表字段，
 * 这里由 JVM 管生命周期，不需要。
 */
public final class Proto {

    /**
     * {@code ABSLINEINFO}：写在 {@code lineinfo} 里的哨兵值，表示「这一条的行号
     * 在 abslineinfo 里另存」，因为相对差值塞不进一个字节。
     */
    public static final int ABSLINEINFO = -0x80;

    /** {@code MAXIWTHABS}：每隔这么多条指令就存一次绝对行号，便于二分查找 */
    public static final int MAXIWTHABS = 128;

    /**
     * upvalue 描述（{@code Upvaldesc}）。
     * <p>
     * {@code kind} 用的是 {@code RDKREG/RDKCONST/RDKTOCLOSE/RDKCTC}，运行期据此
     * 决定要不要为它生成 to-be-closed 的清理动作。
     */
    public static final class Upvaldesc {
        public LuaString name;
        /** 是否在本函数的寄存器栈上（false 表示在外层函数的 upvalue 列表里） */
        public boolean instack;
        /** 寄存器号，或外层 upvalue 列表的下标 */
        public int idx;
        /** {@code kind}：RDKREG / RDKCONST / RDKTOCLOSE / RDKCTC */
        public int kind;

        public Upvaldesc() {
        }

        public Upvaldesc(LuaString name, boolean instack, int idx, int kind) {
            this.name = name;
            this.instack = instack;
            this.idx = idx;
            this.kind = kind;
        }
    }

    /** 局部变量描述（{@code LocVar}），仅供调试信息 */
    public static final class LocVar {
        public LuaString varname;
        /** 变量生效的第一条指令 */
        public int startpc;
        /** 变量失效的第一条指令 */
        public int endpc;

        public LocVar() {
        }

        public LocVar(LuaString varname, int startpc, int endpc) {
            this.varname = varname;
            this.startpc = startpc;
            this.endpc = endpc;
        }
    }

    /** 绝对行号条目（{@code AbsLineInfo}） */
    public static final class AbsLineInfo {
        public int pc;
        public int line;

        public AbsLineInfo() {
        }

        public AbsLineInfo(int pc, int line) {
            this.pc = pc;
            this.line = line;
        }
    }

    /** 固定参数个数 */
    public int numparams;
    /** 是否是可变参数函数 */
    public boolean isVararg;
    /** 本函数需要的寄存器数 */
    public int maxstacksize;

    public int[] code = new int[0];
    public int sizecode;

    /** 常量表。每个元素是一份拷贝，槽位在运行期不变（字节码里按下标引用） */
    public LuaValue[] k = new LuaValue[0];
    public int sizek;

    /** 内嵌的函数原型 */
    public Proto[] p = new Proto[0];
    public int sizep;

    public Upvaldesc[] upvalues = new Upvaldesc[0];
    public int sizeupvalues;

    public LocVar[] locvars = new LocVar[0];
    public int sizelocvars;

    /** 相对行号（与上一条的差值），{@link #ABSLINEINFO} 表示走绝对行号 */
    public byte[] lineinfo = new byte[0];
    public int sizelineinfo;

    public AbsLineInfo[] abslineinfo = new AbsLineInfo[0];
    public int sizeabslineinfo;

    /** 调试信息：函数定义起始行 / 结束行 */
    public int linedefined;
    public int lastlinedefined;

    /** 源名（调试信息） */
    public LuaString source;

    /* ---------- 构建期辅助 ---------- */

    /**
     * 写第 {@code pc} 条指令。
     * <p>
     * 用「按下标写」而不是「追加」：codegen 里 {@code removelastinstruction}
     * 会回退 {@code pc} 再重写同一条，追加语义会在那里长出一条多余的指令。
     */
    public void setCode(int pc, int instr) {
        if (pc >= code.length) {
            int n = Math.max(8, code.length);
            while (n <= pc) {
                n *= 2;
            }
            code = Arrays.copyOf(code, n);
        }
        code[pc] = instr;
        if (pc + 1 > sizecode) {
            sizecode = pc + 1;
        }
    }

    /** 写第 {@code pc} 条的行号信息（{@code lineinfo} 与 {@code code} 一一对应） */
    public void setLineInfo(int pc, byte v) {
        if (pc >= lineinfo.length) {
            int n = Math.max(8, lineinfo.length);
            while (n <= pc) {
                n *= 2;
            }
            lineinfo = Arrays.copyOf(lineinfo, n);
        }
        lineinfo[pc] = v;
        if (pc + 1 > sizelineinfo) {
            sizelineinfo = pc + 1;
        }
    }

    public void addAbsLineInfo(int pc, int line) {
        if (sizeabslineinfo >= abslineinfo.length) {
            abslineinfo = Arrays.copyOf(abslineinfo, Math.max(8, abslineinfo.length * 2));
        }
        abslineinfo[sizeabslineinfo++] = new AbsLineInfo(pc, line);
    }

    /** 追加一个常量（不去重，去重在 Codegen 里做），返回下标 */
    public int addConstant(LuaValue v) {
        if (sizek >= k.length) {
            k = Arrays.copyOf(k, Math.max(8, k.length * 2));
        }
        k[sizek] = new LuaValue(v);
        return sizek++;
    }

    public int addNested(Proto child) {
        if (sizep >= p.length) {
            p = Arrays.copyOf(p, Math.max(4, p.length * 2));
        }
        p[sizep++] = child;
        return sizep - 1;
    }

    /** 把指令数截断到 {@code n}（配合 {@link #sizecode} 回退使用） */
    public void truncateCode(int n) {
        sizecode = n;
    }

    /**
     * 追加一个空的 upvalue 槽位并返回它（{@code lparser.c: allocupvalue}）。
     * <p>
     * 返回的是<b>同一个对象</b>，调用方拿到后直接填字段——不要在别处
     * {@code new} 一个再塞回来，那样 {@code Parser} 里「先分配后填」的顺序
     * 就会被打乱。
     */
    public Upvaldesc allocUpvalue() {
        if (sizeupvalues >= upvalues.length) {
            upvalues = Arrays.copyOf(upvalues, Math.max(4, upvalues.length * 2));
        }
        if (upvalues[sizeupvalues] == null) {
            upvalues[sizeupvalues] = new Upvaldesc();
        }
        return upvalues[sizeupvalues++];
    }

    /** 追加一个空的局部变量槽位并返回它（{@code lparser.c: registerlocalvar}） */
    public LocVar allocLocVar() {
        if (sizelocvars >= locvars.length) {
            locvars = Arrays.copyOf(locvars, Math.max(8, locvars.length * 2));
        }
        if (locvars[sizelocvars] == null) {
            locvars[sizelocvars] = new LocVar();
        }
        return locvars[sizelocvars++];
    }

    /* ---------- 调试信息 ---------- */

    /**
     * {@code ldebug.c: luaG_getfuncline}：由 pc 反推行号。
     * <p>
     * 行号是差分存的，所以只能从最近的一个「绝对行号锚点」线性累加过去；
     * {@code abslineinfo} 的作用就是让这个锚点不要离得太远。
     */
    public int getFuncline(int pc) {
        if (sizelineinfo == 0) {
            return -1; // 没有调试信息
        }
        int[] basepcHolder = new int[1];
        int baseline = getBaseline(pc, basepcHolder);
        int basepc = basepcHolder[0];
        while (basepc++ < pc) {
            baseline += lineinfo[basepc];
        }
        return baseline;
    }

    /** {@code getbaseline} */
    private int getBaseline(int pc, int[] basepcOut) {
        if (sizeabslineinfo == 0 || pc < abslineinfo[0].pc) {
            basepcOut[0] = -1; // 从头开始
            return linedefined;
        }
        int i = pc / MAXIWTHABS - 1; // 估算（必须是正确锚点的下界）
        while (i + 1 < sizeabslineinfo && pc >= abslineinfo[i + 1].pc) {
            i++;
        }
        basepcOut[0] = abslineinfo[i].pc;
        return abslineinfo[i].line;
    }

    /** {@code lfunc.c: luaF_getlocalname} */
    public LuaString getLocalName(int localNumber, int pc) {
        for (int i = 0; i < sizelocvars && locvars[i].startpc <= pc; i++) {
            if (pc < locvars[i].endpc) { // 变量在这一点生效？
                localNumber--;
                if (localNumber == 0) {
                    return locvars[i].varname;
                }
            }
        }
        return null;
    }
}
