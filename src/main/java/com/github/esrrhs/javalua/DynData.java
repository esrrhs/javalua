package com.github.esrrhs.javalua;

import java.util.ArrayList;
import java.util.List;

/** 动态解析数据 —— Lua 5.3.4 {@code Dyndata}（兼容字段保留至 Parser 重写完成）。 */
public final class DynData {

    public static final int VDKREG = 0;
    public static final int RDKCONST = 1;
    public static final int RDKTOCLOSE = 2;
    public static final int RDKCTC = 3;

    public static final class Vardesc {
        public int idx;
        public int ridx; // alias
        public int pidx;
        public LuaString name;
        public int kind = VDKREG;
        public LuaValue k = LuaValue.ofNil();
    }

    public static final class Labeldesc {
        public LuaString name;
        public int pc;
        public int line;
        public int nactvar;
        public boolean close;
    }

    public final List<Vardesc> actvar = new ArrayList<>();
    public int actvarN;
    public final List<Labeldesc> gt = new ArrayList<>();
    public final List<Labeldesc> label = new ArrayList<>();

    public void setActvar(int i, Vardesc v) {
        while (actvar.size() <= i) {
            actvar.add(new Vardesc());
        }
        actvar.set(i, v);
        if (i + 1 > actvarN) {
            actvarN = i + 1;
        }
    }
}
