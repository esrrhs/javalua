package com.github.esrrhs.javalua;

/** 函数编译状态 —— Lua 5.3.4 {@code FuncState}。 */
public final class FuncState {

    public Proto f;
    public FuncState prev;
    public Lexer ls;
    public Parser.BlockCnt bl;
    public DynData dyd;
    public int pc;
    public int lasttarget;
    public int jpc = Codegen.NO_JUMP;
    public int nk;
    public int np;
    public int firstlocal;
    public int firstlabel;
    public int nlocvars;
    public int nactvar;
    public int nups;
    public int freereg;
    public int previousline;
    public int iwthabs;
    public int ndebugvars;
    public boolean needclose;

    public FuncState(Proto f, FuncState prev, Lexer ls, DynData dyd) {
        this.f = f;
        this.prev = prev;
        this.ls = ls;
        this.dyd = dyd;
        this.previousline = ls != null ? ls.lastline : 0;
        this.jpc = Codegen.NO_JUMP;
    }

    public DynData.Vardesc getLocalVarDesc(int i) {
        return dyd.actvar.get(firstlocal + i);
    }

    /** 5.3: active locals occupy registers 0..nactvar-1 */
    public int nvarstack() {
        return nactvar;
    }

    public static int reglevel(FuncState fs, int nactvar) {
        return nactvar;
    }
}
