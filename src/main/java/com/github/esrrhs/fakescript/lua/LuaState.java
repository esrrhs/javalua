package com.github.esrrhs.fakescript.lua;

import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Lua 状态 —— 对应 {@code lstate.h} 的 {@code lua_State} 与 {@code ldo.c} 的
 * 调用机制。
 * <p>
 * <b>核心不变式：栈槽对象一旦创建就不再替换。</b>C 版开放 upvalue 保存的是栈槽的
 * 地址；这里保存的是槽对象的引用。两者等价的前提是栈扩容只复制数组、不动槽对象。
 * 因此全类内部一律 {@code stack[i].set(v)}，从不写 {@code stack[i] = v}，
 * 新槽只在 {@link #ensureSlots(int)} 里 {@code new} 出来。
 * <p>
 * 几处与 C 版的差异（都是刻意的）：
 * <ul>
 *   <li>指针换成<b>栈下标</b>。{@code StkId} 在 C 里能加减，Java 里只能这样；</li>
 *   <li>没有 {@code EXTRA_STACK} 保证金：{@code luaD_call} 前需要栈空间的地方
 *       显式 {@code ensureSlots}；</li>
 *   <li>{@code luaD_throw} 换成抛 {@link LuaError}，{@code pcall} 用
 *       {@code try/catch} 实现；</li>
 *   <li>没有「非 Lua 线程」：协程只是另一个 {@code LuaState}，靠
 *       {@link #xmove} 在两条栈之间搬值，不需要任何线程/纤程去模拟第二条
 *       执行流；yield 用抛 {@link LuaYield} 实现。</li>
 * </ul>
 */
public final class LuaState {

    /** {@code LUA_MULTRET}：调用方要全部结果 */
    public static final int MULTRET = -1;

    /** {@code LUA_MINSTACK}：C 函数帧保证的可用槽数 */
    public static final int MINSTACK = 20;

    /** {@code LUAI_MAXCCALLS}：C 调用深度上限，超过报「C stack overflow」 */
    public static final int MAXCCALLS = 200;

    /**
     * {@code LUAI_MAXSTACK}：Lua 栈槽上限，超过报「stack overflow」。
     * <p>
     * 这个数不是安全阀的取值问题，而是<b>语义</b>：实测参考实现在 1,000,000
     * 槽处溢出（{@code luaconf.h} 里 {@code LUAI_IS32INT} 为真时取这个分支）。
     * 没有它，无限递归在 Java 这边的表现是 {@code OutOfMemoryError} 而不是
     * 一句 Lua 错误——上游 {@code cstack.lua} 整篇都在断言这件事。
     */
    public static final int MAXSTACK = 1000000;

    /** {@code ERRORSTACKSIZE}：栈溢出之后留给错误处理用的额外空间 */
    private static final int ERRORSTACKSIZE = MAXSTACK + 200;

    /** {@code ldo.c: luaD_seterrorobj} 对 {@code LUA_ERRERR} 用的固定串 */
    private static final String ERRERR_MSG = "error in error handling";

    /** 初始栈容量 */
    private static final int BASIC_STACK_SIZE = 2 * MINSTACK;

    /* ==================== 栈 ==================== */

    LuaValue[] stack = new LuaValue[BASIC_STACK_SIZE];
    int top;

    /* ==================== 调用 ==================== */

    CallInfo ci;
    private final CallInfo baseCi = new CallInfo();
    int nCcalls;

    /**
     * 是否处在「栈溢出之后、正在处理这个错误」的状态。
     * <p>
     * C 版把它编码成 {@code stacksize(L) == ERRORSTACKSIZE}：一旦溢出就把栈扩到
     * {@code MAXSTACK + 200}，之后任何再增长的请求都不再报
     * {@code stack overflow}，而是直接 {@code LUA_ERRERR}——那说明连错误处理
     * 自己也把栈用完了，再给一句新消息只会再溢出一次。
     */
    private boolean stackErr;

    /** {@link #applyErrMsg} 的重入保护，见那里的注释 */
    private boolean inErrMsg;

    /**
     * 不可 yield 的调用层数 —— C 版是 {@code nCcalls} 的高 16 位
     * （{@code nyci = 0x10000 | 1}，{@code yieldable(L)} 判高位是否为 0）。
     * 这里拆成独立字段：{@code nCcalls} 只管深度，这里只管能不能 yield。
     */
    private int nNy;

    /**
     * 关 tbc 变量时被 yield 打断，记下「当时正要往上抛的那个错误对象」。
     * <p>
     * C 版不需要这个字段：错误对象一直躺在栈上（{@code luaD_seterrorobj} 每次
     * 关一个变量都会把它重新写到 {@code tbc + 1}），{@code finishpcallk} 从头
     * {@code L->top - 1} 取即可。本实现的错误对象住在异常里，yield 一抛就跟着
     * Java 栈一起没了，只能在这里暂存，等 {@link #finishPcallK} 来取。
     */
    LuaValue closeErrPending;

    /* ==================== 调试钩子 ==================== */

    /**
     * {@code L->hook}：当前钩子。{@code null} 表示没开。
     * <p>
     * 只有 {@code debug.sethook} 会写它，而它只被 {@code DebugLib.dHook} 读。
     */
    LuaHook hook;

    /** {@code L->hookmask}：{@link Lua#LUA_MASKCALL} 等的按位或 */
    int hookmask;

    /** {@code L->hookcount}：还有几条指令触发计数钩子 */
    int hookcount;

    /** {@code L->basehookcount}：{@code sethook} 传入的计数周期 */
    int baseHookCount;

    /**
     * {@code L->allowhook}：钩子<b>内</b>不再触发钩子。
     * <p>
     * C 版在 {@code luaD_hook} 里先置 0 再调钩子，回来再置 1。缺了它，
     * 钩子函数里执行的每一条 Lua 指令都会递归触发同一个钩子，
     * 一瞬间就把 C 栈（这里是 Java 栈）撑爆。
     */
    boolean allowHook = true;

    /**
     * {@code L->oldpc}：上一次触发行钩子的那条指令。
     * <p>
     * 行钩子的判据是「{@code pc} 往回跳了」或「中途换过行」，两者都要拿
     * 上一次的位置比。回到上层函数时由 {@code rethook} 更新。
     */
    int oldpc;

    /* ==================== 线程状态 ==================== */

    /** {@code LUA_OK}：没在跑，或者跑完了 */
    public static final int LUA_OK = 0;

    /** {@code LUA_YIELD}：挂起了 */
    public static final int LUA_YIELD = 1;

    /** {@code LUA_ERRRUN}：运行期错误（本实现目前只区分这三种状态） */
    public static final int LUA_ERRRUN = 2;

    /** {@code lua_State.status} */
    int status = LUA_OK;

    /** {@code lua.h: LUA_GCGEN} —— 分代模式 */
    public static final int LUA_GCGEN = 10;

    /** {@code lua.h: LUA_GCINC} —— 增量模式（5.4 的默认） */
    public static final int LUA_GCINC = 11;

    /**
     * {@code global_State.gcstp}：收集器是否在运行。
     * <p>
     * 只为 {@code collectgarbage("stop"/"restart"/"isrunning")} 三者自洽而存在，
     * 本实现不真的收集。
     */
    boolean gcRunning = true;

    /**
     * {@code global_State.gckind}：{@link #LUA_GCGEN} 或 {@link #LUA_GCINC}。
     * <p>
     * {@code collectgarbage("generational"/"incremental")} 返回的是<b>切换前</b>
     * 的模式，所以要记住当前是哪一个。
     */
    int gcKind = LUA_GCINC;

    /** {@code global_State.gcpause} / {@code gcmul}：只用于让 set* 有值可报 */
    int gcPause = 200;

    /** @see #gcPause */
    int gcStepMul = 100;

    /**
     * 主线程，对应 {@code global_State.mainthread}。
     * <p>
     * 唯一用途是区分「跨 C 调用边界 yield」和「在协程外 yield」两条报错——
     * 两条消息的措辞不同，得知道自己在不在主线程上。
     */
    final LuaState mainThread;

    /* ==================== 全局 ==================== */

    public final StringPool strings;
    public LuaTable globals;
    public final LuaTable registry;

    /**
     * 注册表里的 {@code _LOADED} 表，也就是 {@code package.loaded}。
     * <p>
     * 除了阶段 8 的 {@code require} 要用它，这里<b>立刻</b>就要用它：
     * {@code lauxlib.c: pushglobalfuncname} 靠它反查「这个 C 函数在哪个库里」，
     * 于是 {@code pcall(string.len)} 报的是
     * 「bad argument #1 to 'string.len'」而不是「… to 'len'」。少的不是里子
     * 而是面子——但真实项目的日志与断言里全是这种字符串。
     * <p>
     * 与 {@link #registry} / {@link #globals} 一样<b>必须与主线程共享</b>：
     * C 版 {@code _LOADED} 挂在注册表上，而注册表是 {@code global_State}
     * 级别的，所有 {@code lua_State} 共用一份。早先这里是每个 state 一份，
     * 于是协程里 {@code pcall(math.random, 5, 1)} 反查不到名字，报成
     * 「bad argument #1 to '?'」——参考实现给的是 'math.random'。
     */
    private final LuaTable loaded;

    /**
     * 每个基础类型的默认元表，对应 {@code global_State.mt[]}。
     * <p>
     * 只有 {@code debug.setmetatable(nil, mt)} 这类「给整个类型设元表」的调用
     * 才会用到；表与 userdata 的元表存在对象自身。
     */
    private final LuaTable[] typeMt;

    /** 元方法名常量表，对应 {@code global_State.tmname[]} */
    final LuaString[] tmName;

    /** 开放 upvalue 列表，按 {@link UpVal#stackIdx} 升序 */
    private final List<UpVal> openUpvals = new ArrayList<>();

    /**
     * 当前 chunk 的名字，仅用于错误信息。
     * <p>
     * 存<b>字节</b>而不是 {@code String}：源名是 Lua 字符串，而
     * {@code luaO_chunkid} 的截断是按字节算的（见 {@link #chunkId}）。
     */
    private byte[] chunkName = { '?' };

    /**
     * 标准输出。{@code print} 写这里，测试把它换成内存缓冲区即可做差分比对。
     * <p>
     * {@code io.stdout} 默认也写这里（不是 {@code System.out}）——否则
     * {@code io.write} 的输出接不到测试装的那条缓冲区上。
     */
    public java.io.PrintStream out;

    /**
     * 标准输入。{@code io.stdin} 读这里。
     * <p>
     * 默认是一条<b>空流</b>而不是 {@code System.in}：差分测试里参考实现的 stdin
     * 被定向到 {@code /dev/null}，两边必须一致，否则 {@code io.read()} 一边
     * 返回 nil 另一边阻塞在终端上。
     */
    public java.io.InputStream in = new java.io.ByteArrayInputStream(new byte[0]);

    /**
     * 标准错误。{@code io.stderr} 写这里。
     * <p>
     * 单独留一个字段而不是直接用 {@code System.err}：差分测试里参考实现的
     * stdout 与 stderr 是<b>合并</b>捕获的，这边也得能把两条流指到同一个缓冲上。
     */
    public java.io.PrintStream err = System.err;

    public LuaState() {
        this(null);
    }

    /**
     * 建一个新线程（协程）。
     * <p>
     * 与主线程共享的只有「全局」那部分：字符串池、全局表、注册表、类型元表、
     * 标准输出。栈、调用帧、开放 upvalue 都是每线程一份——这恰恰就是协程能
     * 各自挂起的全部原因，也就不需要任何线程、纤程去模拟「另一条执行流」。
     * <p>
     * <b>第 0 号栈槽是保留的。</b>{@code base_ci.func} 指向它（见
     * {@code lstate.c: stack_init} 里「先占一格给函数」），协程主体函数落在
     * 下标 1。{@code lua_resume} 那句
     * {@code nresults = top - (ci->func + 1)} 只有在这个布局下才成立：
     * 协程跑完、结果搬回下标 1 之后，top 减去 1 正好是结果个数。
     */
    private LuaState(LuaState main) {
        mainThread = (main == null) ? this : main;
        /*
         * 主线程<b>永远不可 yield</b>——{@code lstate.c: lua_newstate} 里那句
         * {@code incnny(L); /* main thread is always non yieldable * /}。
         * 实测：主协程里 {@code coroutine.isyieldable()} 是 false，即使在
         * 最外层、一个 C 调用都没有的时候。协程起自 0，每次 resume 也重置为 0
         * （C 版 {@code L->nCcalls = getCcalls(from)} 只拷低 16 位，高位清零）。
         */
        nNy = (main == null) ? 1 : 0;
        if (main == null) {
            strings = new StringPool();
            globals = new LuaTable();
            registry = new LuaTable();
            loaded = new LuaTable();
            typeMt = new LuaTable[Lua.TTHREAD + 1];
            out = System.out;
        } else {
            strings = main.strings;
            globals = main.globals;
            registry = main.registry;
            loaded = main.loaded;
            typeMt = main.typeMt;
            out = main.out;
        }
        tmName = new LuaString[Tm.TM_N];
        for (int i = 0; i < stack.length; i++) {
            stack[i] = new LuaValue();
        }
        baseCi.func = 0;
        baseCi.top = MINSTACK;
        baseCi.nresults = 0;
        baseCi.isLua = false;
        ci = baseCi;
        /*
         * {@code lstate.c: stack_init}：先给 {@code base_ci} 的「函数」占一格
         * （置 nil），再把 {@code top} 抬过它。
         * <p>
         * 主线程也要这么做，而<b>不能</b>停在 0：C 版主线程开库之前
         * {@code top} 就是 {@code stack + 1}，{@code lua.c: main} 里
         * {@code pmain} 因此落在下标 1。差一格，所有按 {@code top} 递推出来的
         * 递归深度就整体差一。
         */
        if (main == null) {
            stack[0].setNil();
            top = 1;
        }
        for (int i = 0; i < Tm.TM_N; i++) {
            tmName[i] = LuaString.latin1(Tm.EVENT_NAMES[i]);
        }
        if (main == null) {
            registry.set(2, LuaValue.ofTable(globals)); // LUA_RIDX_GLOBALS
            globals.set("_G", LuaValue.ofTable(globals));
            /*
             * 上游的 _LOADED 里也挂着 _G 本身，所以全局函数（print 等）能被
             * findfield 反查到名字（查出来是 "_G.print"，再把前缀去掉）。
             * 不挂的话 pcall(print, ...) 的报错里函数名会变成 "?"。
             */
            loaded.set("_G", LuaValue.ofTable(globals));
        }
    }

    /**
     * {@code package.loaded}（注册表里的 {@code _LOADED}）。
     * <p>
     * 开库时应当顺手把库表登记进来（等价于 {@code luaL_requiref} 的
     * {@code LOADED[modname] = module}）——这既让 {@code require} 能用，
     * 也让库函数的报错带上 {@code 'string.len'} 这样的全名。
     */
    public LuaTable loadedTable() {
        return loaded;
    }

    /**
     * 主线程上登记的所有线程（含它自己），弱引用。
     * <p>
     * 唯一用途是 {@link #clearDeadStackSlices}：C 版在 GC 的 atomic 阶段会遍历
     * <b>每一个</b>线程，把 {@code [top, stack_last)} 清掉；这里要清就得找得到
     * 所有协程。用弱引用是因为协程本身是 Lua 值，它死活由脚本决定，不该被这份
     * 登记表拖住。
     */
    private final List<WeakReference<LuaState>> threads = new ArrayList<>();

    /** {@code lua_newthread} */
    public LuaState newThread() {
        LuaState nl = new LuaState(mainThread);
        nl.top = 1; // 第 0 格留给 base_ci.func，主体函数从下标 1 开始
        mainThread.threads.add(new java.lang.ref.WeakReference<>(nl));
        return nl;
    }

    /**
     * {@code lgc.c: traversethread} 的 atomic 分支：把所有栈 {@code [top, ...)}
     * 那一段置 nil。
     * <p>
     * 为什么是「收集时才清」而不是「每次 {@code top} 下降就清」——这是照着 C 版
     * 来的，也是踩出来的：C 只在 GC 的 atomic 阶段清死区，中间那些 {@code top}
     * 变动一律不清。急切清会撞上两处，而且都很难看出来：
     * <ul>
     *   <li>{@code luaT_callTMres} 写完结果才弹栈，而<b>目标寄存器可能就是弹出
     *       的那一格</b>；清早一步就把刚写好的结果抹了（{@code __lt} / {@code __eq}
     *       全军覆没）。</li>
     *   <li>{@code LuaError.value} 直接别名了栈槽（{@code error('boom', 0)} 里
     *       {@code L.arg(0)} 就是活槽）。{@code pcall} 的收尾会把 {@code top}
     *       降回去，一清就把错误对象清成 nil（{@code pcall} 变成 {@code false nil}）。</li>
     * </ul>
     * 反过来，等到收集时再清是<b>精确</b>的：此刻 {@code top} 之上的东西按定义
     * 就是死的，C 的 mark 阶段也只看得到 {@code [stack, top)}。
     */
    void clearDeadStackSlices() {
        clearDeadSlice(this);
        for (WeakReference<LuaState> r : mainThread.threads) {
            LuaState t = r.get();
            if (t != null) {
                clearDeadSlice(t);
            }
        }
    }

    private static void clearDeadSlice(LuaState t) {
        for (int i = t.top; i < t.stack.length; i++) {
            t.stack[i].setNil();
        }
    }

    /* ==================== __gc ==================== */

    /**
     * 所有挂过 {@code __gc} 的表，弱引用。
     * <p>
     * 只用于 {@link #close}：C 版 {@code lua_close} 会<b>不管还活不活</b>把清单上
     * 的终结器全跑一遍（{@code callallpendingfinalizers}），所以退出时不能只看
     * 「已经不可达的那些」。
     */
    private final List<WeakReference<Object>> finalizables = new ArrayList<>();

    /**
     * 已经不可达、等着跑 {@code __gc} 的表。
     * <p>
     * 由 {@link GcSentinel#finalize} 从 <b>JVM 终结线程</b>填进来，只能在 Lua 线程
     * 上排空，所以下面的存取都要锁。
     */
    private final List<Object> pendingFinalizers = new ArrayList<>();

    void registerFinalizable(Object t) {
        synchronized (finalizables) {
            finalizables.add(new WeakReference<>(t));
        }
    }

    /** {@link GcSentinel#finalize} 用：把表拽回可达集合并排队 */
    void enqueueFinalizer(Object t) {
        synchronized (pendingFinalizers) {
            pendingFinalizers.add(t);
        }
        gcPending = true;
    }

    /**
     * 排空待终结队列，在 {@code L} 上跑它们的 {@code __gc}（{@code lgc.c: GCTM}）。
     * <p>
     * 后进先出：实测参考实现里三个对象的 {@code __gc} 顺序是 {@code 3,2,1}，
     * 也就是<b>创建顺序的逆序</b>。Lua 没规定顺序，但差分测的是逐字节一致，
     * 照它来最省事。
     */
    void drainFinalizers(LuaState L) {
        while (true) {
            Object t;
            synchronized (pendingFinalizers) {
                if (pendingFinalizers.isEmpty()) {
                    return;
                }
                t = pendingFinalizers.remove(pendingFinalizers.size() - 1);
            }
            runGc(L, t);
        }
    }

    /* ==================== 自动收集：luaC_checkGC 的等价物 ==================== */

    /*
     * 没有收集器，就得回答「什么时候去戳一下 JVM」。两个判据：
     *
     * 1. <b>分配债</b>。C 版每 new 一个可回收对象就 {@code luaC_checkGC}，
     *    债满了推进一轮收集；这里照抄，债满就 {@code System.gc()}。
     * 2. <b>待终结队列非空</b>。JVM 自己也会回收，哨兵的 {@code finalize}
     *    可能在没人请求的时候就把对象排进队——那时不用等债满，直接排空。
     *
     * 计数做成静态的：{@link LuaTable#LuaTable()} 这类构造器拿不到 {@code L}，
     * 而它只决定「什么时候戳一下」，多个 {@code LuaState} 共享也不影响正确性。
     */
    private static volatile long gcDebt;

    /** 下一次强制收集要攒到多少债。每次收集后翻倍，避免分配密集的程序被拖死 */
    private static volatile long gcThreshold = 1024;

    /** 翻倍的上限：{@code 1 << 24}。再大就基本等于「只靠 JVM 自己回收」了 */
    private static final long GC_THRESHOLD_MAX = 1L << 24;

    /** JVM 已经把对象排进队、等着在 Lua 线程上跑 {@code __gc} */
    private volatile boolean gcPending;

    /** 排空中。{@code __gc} 里也会建表，不挡住就会重进 */
    private boolean inGc;

    /** 每 new 一个可回收对象收一次债 */
    static void chargeGc() {
        gcDebt++;
    }

    /** 该不该看一眼收集器。放在主循环里每条指令都问一次，所以要够便宜 */
    boolean gcDue() {
        return gcDebt >= gcThreshold || mainThread.gcPending;
    }

    /**
     * 只在<b>指令边界</b>上调：先要一次 GC + 终结（债满时），再把排上队的
     * {@code __gc} 跑完（{@code lgc.c: GCTM}）。
     * <p>
     * 为什么必须挑指令边界：{@link #drainFinalizers} 会在当前栈上<b>跑 Lua
     * 代码</b>，而 {@link Vm#execute} 的 {@code ci / base / pc / k} 都是局部量。
     * 好在这些量在指令边界上都是自洽的——栈槽对象不会被替换（只有
     * {@link #ensureSlots} 会换数组，而它保持下标），{@code ci} 也不会被
     * 嵌套调用换掉。
     */
    void maybeCollect(LuaState L) {
        LuaState m = mainThread;
        if (m.inGc) {
            return;
        }
        if (!m.gcPending) {
            if (gcDebt < gcThreshold) {
                return;
            }
            gcDebt = 0;
            if (gcThreshold < GC_THRESHOLD_MAX) {
                gcThreshold <<= 1;
            }
            /*
             * 与 collectgarbage 同一件事：清掉各栈 {@code top} 之上的死区。
             * 少了这一步，凡是被调函数参数槽「顺便留住」的对象都收不掉——
             * 实测 {@code setmetatable({}, {__gc=f})} 里那个 {@code {}} 就
             * 卡在 {@code setmetatable} 帧的参数槽里，一直活到那个槽被别的
             * 调用覆盖为止，于是 {@code repeat until name} 永远出不来。
             */
            L.clearDeadStackSlices();
            System.gc();
            /*
             * {@code gc()} 返回时终结线程未必已经跑完，不催这一下的话哨兵
             * 还没入队我们就往下走了——那样一次 {@code System.gc()} 换不来
             * 任何 {@code __gc}，外层的 {@code repeat until} 就永远出不来。
             */
            System.runFinalization();
        }
        m.inGc = true;
        try {
            m.drainFinalizers(L);
        } finally {
            m.inGc = false;
        }
        synchronized (m.pendingFinalizers) {
            if (m.pendingFinalizers.isEmpty()) {
                m.gcPending = false;
            }
        }
    }

    /**
     * 对一个「可终结对象」跑 {@code __gc}。
     * <p>
     * 表与 userdata 都可能挂 {@code __gc}（{@code io} 库的文件句柄就是后者：
     * 不跑它的 {@code __gc}，脚本里忘了 {@code f:close()} 的文件就永远不关，
     * 描述符一路泄漏到进程退出）。两套对象的收尾动作不同，所以在这里分派。
     */
    private static void runGc(LuaState L, Object o) {
        if (o instanceof LuaTable) {
            ((LuaTable) o).runGcMetamethod(L);
        } else if (o instanceof LuaUserdata) {
            ((LuaUserdata) o).runGcMetamethod(L);
        }
    }

    /**
     * {@code lua_close}：把还没跑过的 {@code __gc} 全跑一遍。
     * <p>
     * 实测参考实现：脚本退出时不调 {@code collectgarbage}，{@code __gc} 照样会跑。
     * 少了这一步，「不调 collectgarbage 的 {@code __gc} 用例」两边就对不上。
     * <p>
     * 顺序无所谓（Lua 没规定），可达的与已入队的两批都过一遍。
     */
    public void close() {
        synchronized (finalizables) {
            for (WeakReference<Object> r : finalizables) {
                Object t = r.get();
                if (t != null) {
                    runGc(this, t);
                }
            }
            finalizables.clear();
        }
        drainFinalizers(this);
    }

    /* ==================== 线程查询 ==================== */

    /** {@code lua_gettop} */
    public int getTop() {
        return top - (ci.func + 1);
    }

    /**
     * {@code lua_upvalueindex(n)} 的取值：读<b>当前正在跑的</b>闭包的第
     * {@code i} 个 upvalue（从 0 起）。
     * <p>
     * C 版用伪栈下标表达，这里直接给值。C 闭包绑的是值，Lua 闭包绑的是槽，
     * 所以后者要过一层 {@link UpVal#value()}。
     */
    public LuaValue upvalue(int i) {
        LuaValue f = stack[ci.func];
        if (f.tt == Lua.VCCL) {
            return ((CClosure) f.o).upvalue[i];
        }
        if (f.tt == Lua.VLIF) {
            return ((LClosure) f.o).upvals[i].value();
        }
        throw new IllegalStateException("current frame is not a closure");
    }

    /** {@code lua_getstack(L, 0, &ar)}：还有没有调用帧 */
    public boolean hasFrames() {
        return ci != baseCi;
    }

    /** {@code lua_status} */
    public int getStatus() {
        return status;
    }

    /**
     * {@code lua_pushthread}：压入当前线程，返回「是不是主线程」。
     * {@code coroutine.running} 靠这个返回值区分主协程。
     */
    public int pushThread() {
        push(LuaValue.ofThread(this));
        return (mainThread == this) ? 1 : 0;
    }

    /**
     * {@code luaS_newlstr}：<b>运行时</b>新建字符串。
     * <p>
     * 与编译期（{@link Lexer}）那条路的唯一区别是来源，驻留是同一件事：短字符串
     * 全局唯一。少了这一步，{@code string.rep("a", 10)} 与
     * {@code string.rep("aa", 5)} 是两个不同的对象——上游 {@code strings.lua}
     * 就拿 {@code string.format("%p", s1) == string.format("%p", s2)} 来测这一点。
     * 长字符串不驻留（{@link StringPool#intern} 里判），与上游一致。
     */
    public LuaString newString(byte[] bytes) {
        return strings.intern(LuaString.of(bytes));
    }

    /** {@link #newString(byte[])} 的带区间形态 */
    public LuaString newString(byte[] buf, int off, int len) {
        return strings.intern(LuaString.of(buf, off, len));
    }

    /** {@code lua_isyieldable} */
    public boolean isYieldable() {
        return nNy == 0;
    }

    /** {@code yieldable(L)}：与 {@link #isYieldable} 同义，包内用短名 */
    private boolean yieldable() {
        return nNy == 0;
    }

    /* ==================== 调试钩子：C API 那一层 ==================== */

    /**
     * {@code ldebug.c: lua_sethook}。
     * <p>
     * 末尾那句 {@code settraps} 是<b>不能省</b>的。主循环里的 {@code trap} 是
     * 一个只在特定点重读的局部变量，「执行到一半调用 {@code debug.sethook}」
     * 时它还是 0；{@code settraps} 把所有<b>已经活着</b>的 Lua 帧的
     * {@code trap} 置 1，配合 {@code OP_CALL} 之后的 {@code updatetrap}
     * 才让新开的钩子立刻生效。省掉它的话
     * {@code debug.sethook(f, "l")} 之后一行钩子都不会来。
     */
    public void setHook(LuaHook f, int mask, int count) {
        if (f == null || mask == 0) {
            mask = 0;
            f = null;
        }
        hook = f;
        baseHookCount = count;
        resetHookCount();
        hookmask = mask;
        if (mask != 0) {
            for (CallInfo c = ci; c != null; c = c.previous) {
                if (c.isLua) {
                    c.trap = true;
                }
            }
        }
    }

    /** {@code lua_gethook} */
    public LuaHook getHook() {
        return hook;
    }

    /** {@code lua_gethookmask} */
    public int getHookMask() {
        return hookmask;
    }

    /**
     * {@code lua_gethookcount}：注意返回的是 {@code basehookcount}，
     * 不是还剩几条——{@code debug.gethook()} 的第三个结果要的是周期。
     */
    public int getHookCount() {
        return baseHookCount;
    }

    /** {@code resethookcount(L)} */
    void resetHookCount() {
        hookcount = baseHookCount;
    }

    /**
     * {@code lua_xmove}：把 {@code n} 个值从一个线程搬到另一个。
     * <p>
     * 值本身是<b>拷贝</b>不是共享槽：两个协程的栈是两条独立的 {@code LuaValue[]}，
     * 共享槽会让一个协程写栈时把另一个的栈也改掉。
     */
    public static void xmove(LuaState from, LuaState to, int n) {
        if (n <= 0) {
            return;
        }
        /*
         * 只降 top，<b>不</b>顺手清槽：接下来还要从 {@code from.top + i} 把值读出来。
         * C 版也是先 {@code from->top -= n} 再拷，拷的是新 top 之上的那几格——
         * 那些格子对 GC 已经不可见，但对这段代码还得是可读的。
         * 清掉它们的工作交给 {@link #clearDeadStackSlices}，时机才对。
         */
        from.top -= n;
        to.checkStack(n);
        for (int i = 0; i < n; i++) {
            to.stack[to.top].set(from.stack[from.top + i]);
            to.top++;
        }
    }

    public LuaTable getGlobals() {
        return globals;
    }

    /* ==================== 栈操作 ==================== */

    /**
     * 保证下标 {@code 0 .. n-1} 的槽都存在。
     * <p>
     * 只增长、只追加新槽，绝不替换已有槽对象（见类注释的不变式）。
     */
    void ensureSlots(int n) {
        if (n <= stack.length) {
            return;
        }
        int cap = stack.length;
        while (cap < n) {
            cap *= 2;
        }
        LuaValue[] bigger = new LuaValue[cap];
        System.arraycopy(stack, 0, bigger, 0, stack.length);
        for (int i = stack.length; i < cap; i++) {
            bigger[i] = new LuaValue();
        }
        stack = bigger;
    }

    /**
     * {@code luaD_checkstack} → {@code luaD_growstack}：保证从 {@code top} 起
     * 还有 {@code n} 个槽，并根据 {@link #MAXSTACK} 判「stack overflow」。
     * <p>
     * 两档行为照抄 {@code ldo.c}：常态下超过 {@code MAXSTACK} 就把栈撑到
     * {@code ERRORSTACKSIZE}（给后面的错误处理留地方）再抛；
     * 已经在 {@code stackErr} 里还要涨，就抛 {@link #errErr}。
     */
    void checkStack(int n) {
        /*
         * 用 {@code long} 算：{@code top + n} 用 {@code int} 会溢出。
         * {@code table.unpack(t, 1, 2^31-1)} 这种 {@code n} 一下子就把和
         * 顶成负数，「超过 MAXSTACK」这个判断反而失效，于是栈一路 push 到
         * {@code OutOfMemoryError}——上游 {@code sort.lua} 第一节正撞这里。
         */
        long need = (long) top + n;
        if (need > (stackErr ? ERRORSTACKSIZE : MAXSTACK)) {
            if (stackErr) {
                throw errErr();
            }
            stackErr = true;
            ensureSlots(ERRORSTACKSIZE);
            throw runError("stack overflow");
        }
        ensureSlots((int) need);
    }

    /**
     * {@code lapi.c: lua_checkstack}：只判「还容不容得下 {@code n} 个槽」，
     * <b>不抛错</b>。
     * <p>
     * 与 {@link #checkStack} 的差别不只是返回值：措辞不同的错误各有各的出处——
     * {@code table.unpack} 报 {@code too many results to unpack}、
     * {@code coroutine.resume} 报 {@code too many results to resume}，
     * 都不是笼统的 {@code stack overflow}。让 {@link #checkStack} 代抛就把
     * 这两句都吞成一句，上游 {@code coroutine.lua} 末尾那个
     * 「{@code table.unpack({}, 1, lim)}」的用例正是对着它们断言的。
     */
    boolean checkStackOk(int n) {
        /* 同上：{@code top + n} 溢出会让这个判断整个失效 */
        long need = (long) top + n;
        if (need > (stackErr ? ERRORSTACKSIZE : MAXSTACK)) {
            return false;
        }
        ensureSlots((int) need);
        /* 与 C 版一致：顺带把当前帧的 top 抬上去，后面的压栈才落在帧内 */
        if (ci.top < need) {
            ci.top = (int) need;
        }
        return true;
    }

    /**
     * {@code luaD_shrinkstack}。
     * <p>
     * C 版在这里把 {@code ERRORSTACKSIZE} 的栈收回正常大小，顺带也就退出了
     * 「正在处理栈溢出」的状态——这正是 {@code stacksize(L) == ERRORSTACKSIZE}
     * 那个判据能复位的原因。这里只需要把标志清掉：栈数组缩不缩纯粹是内存问题，
     * 交给 JVM 更省事，但缩一下能让跑过 {@code heavy.lua} 这类用例之后的内存
     * 回到正常量级。
     */
    void shrinkStack() {
        int inuse = stackInUse();
        int max = (inuse > MAXSTACK / 3) ? MAXSTACK : inuse * 3;
        if (inuse <= MAXSTACK && stack.length > max) {
            int nsize = (inuse > MAXSTACK / 2) ? MAXSTACK : inuse * 2;
            trimStack(Math.max(nsize, BASIC_STACK_SIZE));
            /*
             * 「正在处理栈溢出」这个状态，C 版的判据是
             * {@code stacksize(L) == ERRORSTACKSIZE} —— 也就是说它是被
             * <b>收回常规尺寸</b>这个动作结束的，不是被 {@code luaD_shrinkstack}
             * 这个调用结束的。
             * <p>
             * 无条件在这里清标志会出这种事：消息处理函数内部再来一次
             * {@code pcall}，那个内层 {@code pcall} 收尾时调了
             * {@code shrinkStack}，而它自己的 {@code inuse} 很大（外层的帧
             * 还在），于是<b>没有</b>真的收缩，标志却被清成 false；等处理函数
             * 接着调用 {@code tostring}/{@code math.sin}，{@code checkStack}
             * 就按 {@code MAXSTACK} 判，立刻又报一次
             * {@code stack overflow}——本该拿到 {@code error in error handling}
             * 或正常结果的。
             */
            stackErr = false;
        }
    }

    /**
     * {@code ldo.c: stackinuse}：栈上<b>真正还在用</b>的格数。
     * <p>
     * 不是 {@code top}，而是 {@code top} 与<b>每一帧</b> {@code ci.top} 的
     * 最大值——帧的寄存器区一直延伸到 {@code ci.top}，哪怕 {@code top} 已经
     * 落回帧内很浅的位置。
     * <p>
     * 只按 {@code top} 算会在这种情形下把栈锯短：{@code xpcall} 的消息处理函数
     * 是在栈<b>已经撑到</b> {@code ERRORSTACKSIZE} 的时候跑的，它内部再来一次
     * {@code pcall}，那个内层 {@code pcall} 收尾时 {@code top} 才一千出头
     * （是处理函数自己的局部栈顶），于是栈被锯回 {@code MAXSTACK}，而外层
     * 那几百万格还在用的帧瞬间越界——表现是
     * {@code ArrayIndexOutOfBoundsException}。上游 {@code errors.lua} 的
     * 「error in error handling」一段正踩在这个坑上。
     */
    private int stackInUse() {
        int lim = top;
        for (CallInfo c = ci; c != null; c = c.previous) {
            if (lim < c.top) {
                lim = c.top;
            }
        }
        return (lim + 1 < MINSTACK) ? MINSTACK : lim + 1;
    }

    /** 只缩不扩，且绝不缩掉 {@code top} 以下的活槽 */
    private void trimStack(int nsize) {
        if (nsize >= stack.length || nsize < top) {
            return;
        }
        LuaValue[] smaller = new LuaValue[nsize];
        System.arraycopy(stack, 0, smaller, 0, nsize);
        stack = smaller;
    }

    /**
     * {@code ldo.c: luaD_seterrorobj} 里 {@code LUA_ERRERR} 那一档：
     * 不取栈顶的错误值，固定写 {@code "error in error handling"}。
     */
    private LuaValue errObjOf(LuaError e) {
        return e.errErr ? LuaValue.ofString(LuaString.latin1(ERRERR_MSG)) : e.value;
    }

    /** {@code luaD_throw(L, LUA_ERRERR)} */
    private LuaError errErr() {
        LuaError e = new LuaError(LuaValue.ofString(LuaString.latin1(ERRERR_MSG)), "");
        e.errErr = true;
        e.addTraceback = false;
        return e;
    }

    /** 取栈槽。等价于 C 的 {@code s2v(L->top.p + i)} */
    public LuaValue at(int idx) {
        return stack[idx];
    }

    /** 压入一个值（拷贝，不是共享槽） */
    public void push(LuaValue v) {
        ensureSlots(top + 1);
        stack[top++].set(v);
    }

    public void pushNil() {
        ensureSlots(top + 1);
        stack[top++].setNil();
    }

    void pushFunction(LuaFunction f) {
        ensureSlots(top + 1);
        stack[top++].setObject(Lua.VLCF, f);
    }

    void pushClosure(LClosure c) {
        ensureSlots(top + 1);
        stack[top++].setObject(Lua.VLIF, c);
    }

    /** 当前帧的参数个数 */
    public int argCount() {
        return top - ci.func - 1;
    }

    /** 第 i 个参数（0 起） */
    public LuaValue arg(int i) {
        int idx = ci.func + 1 + i;
        /*
         * 越界必须给 nil，不能把栈槽里的<b>残值</b>交出去。
         * C 里 lua_type 对越界下标返回 LUA_TNONE，取值拿到的是 nilvalue；
         * 而我们的栈是复用的数组，越界读出来的是<b>上一帧留下的东西</b>——
         * 实测 pcall(setmetatable) 有时报 "got function"、有时 "got nil"，
         * 取决于那一刻槽里恰好是什么。这类 bug 的难处在于它<b>随机</b>：
         * 同一个用例这次绿下次红。
         */
        return (idx >= top) ? LuaValue.ofNil() : stack[idx];
    }

    /** {@code lua_settop}（正数下标版：1 = 第一个参数，0 = 清空） */
    void setTop(int idx) {
        int t = ci.func + 1 + idx;
        if (t > top) {
            ensureSlots(t);
            while (top < t) {
                stack[top++].setNil();
            }
        } else {
            while (top > t) {
                stack[--top].setNil();
            }
        }
    }

    /** {@code lua_pop}：从栈顶扔掉 {@code n} 个 */
    void pop(int n) {
        while (n-- > 0 && top > 0) {
            stack[--top].setNil();
        }
    }

    /** {@code lua_pushvalue}：把第 {@code idx} 个（1 起）再压一份 */
    void pushValue(int idx) {
        push(stack[ci.func + idx]);
    }

    /** {@code lua_replace}：用栈顶覆盖第 {@code idx} 个（1 起）并弹栈 */
    void replace(int idx) {
        stack[ci.func + idx].set(stack[--top]);
    }

    /* ==================== upvalue ==================== */

    /**
     * {@code luaF_findupval}：找到指向 {@code stackIdx} 的开放 upvalue，没有就建一个。
     * <p>
     * 列表按 {@code stackIdx} 升序，所以从后往前扫能尽早命中「最近创建」的那批。
     */
    UpVal findUpval(int stackIdx) {
        for (int i = openUpvals.size() - 1; i >= 0; i--) {
            UpVal uv = openUpvals.get(i);
            if (uv.stackIdx == stackIdx) {
                return uv;
            }
            if (uv.stackIdx < stackIdx) {
                break;
            }
        }
        UpVal uv = UpVal.open(stack[stackIdx], stackIdx);
        int pos = 0;
        while (pos < openUpvals.size() && openUpvals.get(pos).stackIdx < stackIdx) {
            pos++;
        }
        openUpvals.add(pos, uv);
        return uv;
    }

    /**
     * {@code luaF_newtbcupval}：为 {@code <close>} 变量新建一个 to-be-closed 的
     * 开放 upvalue，并<b>当场</b>检查它真的有 {@code __close}。
     * <p>
     * 两个容易漏掉的细节，都是实测出来的：
     * <ul>
     *   <li>{@code nil} 与 {@code false} 直接跳过，不需要 {@code __close}
     *       （C 版是 {@code if (l_isfalse(s2v(level))) return;}）。所以
     *       {@code local x <close> = nil} 完全合法。</li>
     *   <li>检查在<b>赋值的当下</b>做，不在作用域结束时。所以
     *       {@code local x <close> = 5 print(x)} 在 {@code OP_TBC} 上就抛错，
     *       {@code print} 根本不会执行——把它挪到关闭时检查，输出会先多出一行。</li>
     * </ul>
     * <p>
     * 与 {@link #findUpval} 不同，这里<b>不去重</b>：C 版也是每次新建一个，
     * 因为 tbc 变量是「槽」而不是「变量绑定」，同一个槽可以被标记多次。
     */
    UpVal newTbcUpval(int stackIdx) {
        LuaValue v = stack[stackIdx];
        if (v.isNil() || (v.tt == Lua.TBOOLEAN && !v.toBoolean())) {
            // false / nil 不需要关闭，也不建 upvalue（C 版直接 return）
            return null;
        }
        LuaValue tm = LuaTm.getTmByObj(this, v, Tm.TM_CLOSE);
        if (tm == null || tm.isNil()) {
            LuaString vn = localNameAt(stackIdx - ci.func);
            throw runError("variable '" + ((vn == null) ? "?" : vn.toJavaString())
                    + "' got a non-closable value");
        }
        UpVal uv = UpVal.open(stack[stackIdx], stackIdx);
        uv.toBeClosed = true;
        int pos = 0;
        while (pos < openUpvals.size() && openUpvals.get(pos).stackIdx < stackIdx) {
            pos++;
        }
        openUpvals.add(pos, uv);
        return uv;
    }

    /**
     * {@code luaF_close}：关闭 {@code stackIdx >= level} 的全部开放 upvalue。
     * <p>
     * 从栈顶往下关（与 C 版一致），因为 {@code __close} 元方法可能改动栈、
     * 也可能再建 upvalue。tbc 变量在关闭前先跑 {@code __close}。
     *
     * @param errObj 传给 {@code __close} 的错误对象；{@code null} 表示正常关闭
     */
    void closeUpvals(int level, LuaValue errObj) {
        closeUpvals(level, errObj, false);
    }

    /**
     * @param forceYy {@code lfunc.c: luaF_close} 的 {@code yy} 参数取 1：
     *                <b>不看当前帧是不是 Lua 帧</b>，一律允许 {@code __close}
     *                里 yield
     */
    void closeUpvals(int level, LuaValue errObj, boolean forceYy) {
        /*
         * 下面会动栈（{@code __close} 的调用帧就摆在变量之上），而传进来的
         * {@code errObj} 常常是某个栈槽的别名——先拷一份，否则写完调用帧它就
         * 变成了 {@code __close} 自己。
         */
        LuaValue err = (errObj == null) ? null : new LuaValue(errObj);
        while (!openUpvals.isEmpty()) {
            UpVal uv = openUpvals.get(openUpvals.size() - 1);
            if (uv.stackIdx < level) {
                break;
            }
            openUpvals.remove(openUpvals.size() - 1);
            LuaValue obj = uv.value();
            boolean isTbc = uv.toBeClosed;
            int absIdx = uv.stackIdx;              // 绝对下标，close() 之后就取不到了
            int localIdx = absIdx - ci.func;        // luaG_findlocal 的序号
            uv.close();
            if (isTbc && !(obj.isNil())) {
                LuaTm.callCloseMethod(this, obj, err, absIdx, localIdx, forceYy);
            }
        }
    }

    /* ==================== 类型元表 ==================== */

    LuaTable typeMetatable(int baseType) {
        return typeMt[baseType];
    }

    /** {@code lapi.c: lua_setmetatable} 里「给整个类型设元表」的分支 */
    public void setTypeMetatable(int baseType, LuaTable mt) {
        typeMt[baseType] = mt;
    }

    /* ==================== 调用 ==================== */

    private CallInfo prepCallInfo(int funcIdx, int nresults, boolean isLua, int topIdx) {
        CallInfo c = new CallInfo();
        c.func = funcIdx;
        c.nresults = nresults;
        c.isLua = isLua;
        c.top = topIdx;
        c.previous = ci;
        ci = c;
        return c;
    }

    /** {@code ldo.c: precallC} —— C 函数在这里就被真正调用掉 */
    private int precallC(int funcIdx, int nresults, LuaFunction f) {
        checkStack(MINSTACK);
        CallInfo c = prepCallInfo(funcIdx, nresults, false, top + MINSTACK);
        /*
         * 调用钩子在「真正调 C 函数」<b>之前</b>。这个先后是能观测到的：
         * 实测 {@code debug.sethook(function() error('x') end, 'c')} 之后
         * {@code pcall(function() ... end)}，钩子在 pcall 的 C 帧上就炸了，
         * 于是 <b>pcall 抓不到这个错误</b>——它的恢复点要到下面那一行才建起来。
         * 挪到 {@code f.call} 之后就会让 pcall 把它吞掉，与参考实现不符。
         */
        if ((hookmask & Lua.LUA_MASKCALL) != 0) {
            DebugLib.dHook(this, Lua.LUA_HOOKCALL, -1, 1, top - funcIdx - 1);
        }
        int n = f.call(this);
        poscall(c, n);
        return n;
    }

    /**
     * {@code luaD_precall}：准备一次调用。
     *
     * @return Lua 函数返回新帧（交由 {@link Vm} 执行）；C 函数返回 {@code null}
     *         （函数已经跑完，结果在 {@code funcIdx} 处）
     */
    CallInfo precall(int funcIdx, int nresults) {
        while (true) {
            LuaValue f = stack[funcIdx];
            if (f.tt == Lua.VLIF) {
                LClosure cl = (LClosure) f.o;
                Proto p = cl.p;
                int narg = top - funcIdx - 1;
                int nfixparams = p.numparams;
                int fsize = p.maxstacksize;
                checkStack(fsize);
                CallInfo c = prepCallInfo(funcIdx, nresults, true, funcIdx + 1 + fsize);
                c.savedpc = 0;
                for (; narg < nfixparams; narg++) {
                    stack[top++].setNil();
                }
                return c;
            } else if (f.tt == Lua.VLCF) {
                precallC(funcIdx, nresults, (LuaFunction) f.o);
                return null;
            } else if (f.tt == Lua.VCCL) {
                precallC(funcIdx, nresults, ((CClosure) f.o).f);
                return null;
            } else {
                funcIdx = tryFuncTM(funcIdx);
            }
        }
    }

    /**
     * {@code luaD_pretailcall}：为尾调用准备帧。
     * <p>
     * Lua 函数<b>复用调用方的 CallInfo</b>（这正是尾调用不涨栈的原因），
     * C 函数则照常新建一帧跑完后销毁。
     *
     * @return Lua 函数返回 {@code -1}（帧已就位，交给 {@link Vm} 跑）；
     *         C 函数返回结果个数
     */
    int pretailcall(CallInfo c, int funcIdx, int narg1, int delta) {
        while (true) {
            LuaValue f = stack[funcIdx];
            if (f.tt == Lua.VCCL) {
                /*
                 * C 参考实现里 {@code luaD_pretailcall} 有独立的 {@code LUA_VCCL}
                 * 分支，漏掉它的代价很隐蔽：{@code return wrapfn()} 这种尾调用
                 * 会掉进 {@code default} 去试 {@code __call}，于是报
                 * 「attempt to call a function value」——一个明明是函数的函数
                 * 说自己不可调用。{@code coroutine.wrap} 返回的就是 C closure。
                 */
                return precallC(funcIdx, MULTRET, ((CClosure) f.o).f);
            } else if (f.tt == Lua.VLIF) {
                LClosure cl = (LClosure) f.o;
                Proto p = cl.p;
                int fsize = p.maxstacksize;
                int nfixparams = p.numparams;
                checkStack(Math.max(fsize - delta, 0));
                c.func -= delta;
                for (int i = 0; i < narg1; i++) {
                    stack[c.func + i].set(stack[funcIdx + i]);
                }
                funcIdx = c.func; // 函数与参数已下移
                for (; narg1 <= nfixparams; narg1++) {
                    stack[funcIdx + narg1].setNil();
                }
                c.top = funcIdx + 1 + fsize;
                c.savedpc = 0;
                c.tail = true;
                top = funcIdx + narg1;
                return -1;
            } else if (f.tt == Lua.VLCF) {
                return precallC(funcIdx, MULTRET, (LuaFunction) f.o);
            } else {
                funcIdx = tryFuncTM(funcIdx);
                narg1++; // 元方法本身也是参数
            }
        }
    }

    /** {@code tryfuncTM}：不可调用的值，找 {@code __call} 元方法顶上 */
    private int tryFuncTM(int funcIdx) {
        LuaValue tm = LuaTm.getTmByObj(this, stack[funcIdx], Tm.TM_CALL);
        if (tm == null || tm.isNil()) {
            throw callError(stack[funcIdx]);
        }
        // 把元方法插到 func 位置，其余整体右移一格
        checkStack(1);
        for (int p = top; p > funcIdx; p--) {
            stack[p].set(stack[p - 1]);
        }
        top++;
        stack[funcIdx].set(tm);
        return funcIdx;
    }

    /** {@code ldo.c: moveresults} */
    private void moveResults(int res, int nres, int wanted) {
        if (wanted == 0) {
            top = res;
            return;
        }
        if (wanted == 1) {
            if (nres == 0) {
                stack[res].setNil();
            } else {
                stack[res].set(stack[top - nres]);
            }
            top = res + 1;
            return;
        }
        if (wanted == MULTRET) {
            wanted = nres;
        }
        int firstresult = top - nres;
        if (nres > wanted) {
            nres = wanted;
        }
        for (int i = 0; i < nres; i++) {
            stack[res + i].set(stack[firstresult + i]);
        }
        for (int i = nres; i < wanted; i++) {
            stack[res + i].setNil();
        }
        top = res + wanted;
    }

    /** {@code luaD_poscall} */
    void poscall(CallInfo c, int nres) {
        int wanted = c.nresults;
        /*
         * 返回钩子。C 版的条件是
         * {@code L->hookmask && !hastocloseCfunc(wanted)}：带 to-be-closed
         * 变量的 C 帧要先关变量、再报钩子（那是 {@code moveresults} 里的分支）。
         * 「有 tbc」在 C 里编码在 {@code nresults} 的高位，本实现不这么做，
         * 只在 {@link #finishCcall} 那条 yield 恢复路径上用
         * {@link CallInfo#clsret} 记——所以走到这里时 {@code clsret} 恒为假，
         * 与 C 的判据在可达路径上一致。
         */
        if (hookmask != 0 && !c.clsret) {
            DebugLib.rethook(this, c, nres);
        }
        moveResults(c.func, nres, wanted);
        ci = c.previous;
    }

    /** {@code luaD_call}：从宿主调进 Lua */
    public void call(int funcIdx, int nresults) {
        nCcalls++;
        if (nCcalls >= MAXCCALLS) {
            checkStack(0); // 先把 EXTRA_STACK 的临时占用清掉，再判
            checkCStack();
        }
        try {
            CallInfo c = precall(funcIdx, nresults);
            if (c != null) {
                c.fresh = true;
                Vm.execute(this, c);
            }
        } finally {
            nCcalls--;
        }
    }

    /**
     * {@code lstate.c: luaE_checkcstack}。
     * <p>
     * 两档都留着是对的，而且<b>中间那段「什么都不做」的区间正是设计</b>：
     * 第 200 层抛 {@code C stack overflow} 之后，消息处理函数还要在 201…
     * 219 层里跑（{@code luaG_errormsg} 会 {@code callnoyield} 调它），
     * 直到 220 层（{@code MAXCCALLS / 10 * 11}）才认定「连错误处理都溢出了」，
     * 转成 {@code LUA_ERRERR}。少了这段余量，{@code xpcall(f, f)} 之类的
     * 处理函数一进去就被判死，拿不到 {@code "error in error handling"}。
     */
    private void checkCStack() {
        if (nCcalls == MAXCCALLS) {
            throw runError("C stack overflow");
        }
        if (nCcalls >= MAXCCALLS / 10 * 11) {
            throw errErr();
        }
    }

    /**
     * {@code lstate.c: luaE_incCstack}。
     * <p>
     * 调用方是<b>语法分析器的 {@code enterlevel}</b>（{@code #define enterlevel(ls)
     * luaE_incCstack(ls->L)}）。它与 {@link #call} 共用 {@code nCcalls}，所以
     * 调用深度会吃掉表达式嵌套深度的配额——这正是参考实现的行为。
     */
    public void incCstack() {
        nCcalls++;
        if (nCcalls >= MAXCCALLS) {
            checkCStack();
        }
    }

    /** {@code leavelevel}：{@code L->nCcalls--} */
    public void decCstack() {
        nCcalls--;
    }

    /**
     * {@code lua_callk}：<b>带续体</b>的调用。
     * <p>
     * 续体只在「给了 {@code k} 且当前可 yield」时才登记，否则走
     * {@link #callNoYield}——那时在被调用的函数里 yield 会报「attempt to yield
     * across a C-call boundary」。这正是 C 版 {@code lua_call}（{@code k} 为
     * {@code NULL}）与 {@code lua_callk} 的分界。
     * <p>
     * 标准库里只有两处需要它：{@code pairs} 调 {@code __pairs}
     * （{@code lbaselib.c: pairscont}）与 {@code dofile} 调加载出来的 chunk
     * （{@code dofilecont}）。少了它，{@code __pairs} 里 yield 就会在恢复时
     * 撞上 {@code c.k == null}（上游 {@code nextvar.lua} 专门测这一条）。
     */
    /**
     * {@code ltm.c} 里反复出现的那句
     * <b>「metamethod may yield only when called from Lua code」</b>
     * （{@code luaT_callTM} / {@code luaT_callTMres}）。
     * <p>
     * 判据是<b>当前帧</b>是不是 Lua 帧：从 Lua 代码里触发的元方法可以 yield，
     * 从 C 函数里触发的不行（那时报「attempt to yield across a C-call
     * boundary」）。一律放行会让 {@code table.sort} 之类的回调真的 yield 出去，
     * 而恢复时 {@link CallInfo#k} 是 {@code null}——直接 NPE。
     */
    void callTmMightYield(int funcIdx, int nresults) {
        if (ci.isLua) {
            call(funcIdx, nresults);
        } else {
            callNoYield(funcIdx, nresults);
        }
    }

    void callK(int funcIdx, int nresults, int ctx, LuaKFunction k) {
        if (k != null && yieldable()) {
            ci.k = k;
            ci.ctx = ctx;
            call(funcIdx, nresults);
        } else {
            callNoYield(funcIdx, nresults);
        }
    }

    /**
     * {@code lua_pcall}：带保护地调用。
     * <p>
     * 只捕获 {@link LuaError}。实现自身的 Java 异常必须继续上冒，否则缺陷会被
     * 引擎吞成「脚本报错」。
     *
     * @return 出错时返回错误对象，否则 {@code null}
     */
    public LuaValue pcall(int funcIdx, int nresults) {
        return pcall(funcIdx, nresults, null);
    }

    /**
     * {@code lua_pcall} 带消息处理函数的形态（{@code xpcall} 用它）。
     * <p>
     * 处理函数必须在<b>调用链还没退</b>的时候跑。C 版是在<b>抛出点</b>
     * （{@code ldebug.c: luaG_errormsg}）直接 {@code luaD_callnoyield} 调它，
     * 那会儿整条调用链都还在——这正是 {@code xpcall(f, debug.traceback)} 能打出
     * 完整回溯的原因。放在「先恢复调用链再调处理函数」的位置上是错的：
     * 回溯会只剩最外面两层，而这是 Lua 项目打错误日志的标准写法。
     * <p>
     * Java 这边没有 longjmp，{@code catch} 到的时候 Java 栈已经退了，但
     * <b>Lua 栈没有</b>（{@link #stack} / {@link #top} / {@link #ci} 都还是出错
     * 那一刻的样子，恢复动作就在下面几行）。所以处理函数放在 {@code catch} 的最
     * 前面跑，等价于 C 的在抛出点跑。
     *
     * @param handler 消息处理函数；{@code null} 表示没有（与 {@code msgh = 0} 等价）
     * @return 出错时返回（可能被处理函数改写过的）错误对象，否则 {@code null}
     */
    public LuaValue pcall(int funcIdx, int nresults, LuaValue handler) {
        int oldTop = top;
        CallInfo saved = ci;
        /*
         * {@code luaD_pcall} 里的 {@code old_allowhooks}。
         * <p>
         * <b>恢复的时机很讲究</b>：钩子<b>内</b>抛错时 {@code luaD_hook} 的收尾
         * （把 {@code allowhook} 置回 1）被跳过了，此时 {@code allowhook} 还是 0。
         * 而消息处理函数在 C 版是 {@code luaG_errormsg} 在<b>抛出点</b>调的，
         * 那会儿 {@code allowhook} 依然是 0——所以处理函数（也就是
         * {@code luaL_traceback}）跑的时候<b>不会再触发钩子</b>。
         * 提前恢复成 1 会让回溯过程中又进一次钩子、再抛一次错，最后被
         * {@code applyErrMsg} 兜成 {@code "error in error handling"}。
         * 实测：{@code debug.sethook(function() error("x") end, "c")} 之后
         * 未捕获的错误，参考实现给的是完整回溯，不是那句话。
         */
        boolean oldAllowHook = allowHook;
        /*
         * {@code luaD_rawrunprotected} 最后那句 {@code L->nCcalls = oldnCcalls}
         * ——<b>无论成功失败都要回滚</b>。
         * <p>
         * 少了这一句会漏：{@code luaD_call} 是「先 {@code nCcalls++}，再判
         * {@code luaE_checkcstack}」，判不过就直接抛，自增那一步落在
         * {@code try} <b>外面</b>，{@code finally} 管不到。C 那边靠 longjmp 回到
         * 这里的恢复点兜住，Java 没有 longjmp，只能自己记。
         * <p>
         * 实测：脚本里每来一次 C 栈溢出就永久少一层配额，
         * {@code pcall} 递归探针从 197 一路掉到 193，上游 {@code errors.lua}
         * 的「error in error handling」段因此提前触发。
         */
        int oldNCcalls = nCcalls;
        try {
            call(funcIdx, nresults);
            return null;
        } catch (LuaError e) {
            /*
             * 位置信息在这里补，而不是在抛出的地方补：像「除以零」这类错误
             * 来自 Arith 这种没有 LuaState 的纯算术层，它抛的是裸错误值，
             * 只有走到保护边界才知道当前行号。这正是 C 版 msghandler 里
             * luaL_traceback 干的活。
             */
            LuaValue v;
            if (e.errErr) {
                /*
                 * {@code luaD_throw(L, LUA_ERRERR)} 与 {@code luaG_errormsg}
                 * 是两条路：后者才会调 {@code L->errfunc}，前者直接跳。所以
                 * {@code ERRERR} <b>不再过消息处理函数</b>——它本来就是因为
                 * 处理函数把栈跑穿才产生的。
                 */
                v = LuaValue.ofString(LuaString.latin1(ERRERR_MSG));
            } else {
                v = e.value;
                if (e.where == null && v != null && v.isString()) {
                    String prefix = where(); // 必须用出错时的帧，先于恢复调用链
                    v = LuaValue.ofString(
                            LuaString.latin1(prefix + v.asString().toJavaString()));
                }
                if (handler != null) {
                    v = applyErrMsg(v, handler);
                }
            }
            /*
             * 回滚点就在这里，不能早也不能晚：
             * <ul>
             *   <li><b>不能早</b>：消息处理函数是 C 版在<b>抛出点</b>
             *       （{@code luaG_errormsg}）调的，那会儿 {@code nCcalls} 还
             *       带着溢出时的高位（201…219 那段「给错误处理留的余量」正是
             *       为它准备的）。提前回滚，{@code debug.traceback} 就跑在
             *       一个比参考实现更浅的位置上；</li>
             *   <li><b>不能晚</b>：{@code __close} 是回滚<b>之后</b>才跑的
             *       （{@code luaD_closeprotected} 在 rawrunprotected 之外），
             *       带着高位跑会让收尾时的调用白白撞上
             *       {@code C stack overflow}。</li>
             * </ul>
             */
            nCcalls = oldNCcalls;
            // 到这一步才算走到 C 版 luaD_pcall 的恢复点：__close 是带钩子的
            allowHook = oldAllowHook;
            /*
             * C 版 {@code luaD_pcall} 是<b>先</b> {@code L->ci = old_ci} 再
             * {@code luaD_closeprotected}。顺序反了，{@code __close} 里
             * {@code debug.getinfo(2)} 看到的就是出错的那个 Lua 帧而不是
             * {@code pcall}——实测上游 {@code locals.lua} 断言
             * {@code debug.getinfo(2).name == "pcall"}，照旧写法拿到的是
             * {@code "error"}（{@code foo} 里最后调的是 {@code error}）。
             */
            ci = saved;
            /*
             * {@code forceYy} 取 {@code yieldable()}，对应 C 版
             * {@code lua_pcallk} 那个分支判据 {@code k != NULL && yieldable(L)}：
             * 带续体的 {@code pcall} 在可 yield 的线程里<b>不建本地恢复点</b>，
             * 错误一路冒到 {@code lua_resume}，由 {@code precover} → 
             * {@code finishpcallk} → {@code luaF_close(..., yy = 1)} 收尾。
             * <p>
             * 本实现用 Java 异常代替长跳转，恢复点就在这里的 {@code catch}，
             * 于是只能把「要不要允许 {@code __close} yield」显式传下去。取
             * {@code false} 时 {@code __close} 里的 yield 会报
             * 「attempt to yield across a C-call boundary」——上游
             * {@code locals.lua}「yielding inside closing metamethods」
             * 第三段（函数体里 {@code error(10)}）正测这条：三个 {@code <close>}
             * 变量要能<b>依次</b> yield 出去。
             */
            v = closeUpvalsOnUnwind(saved, v, yieldable()); // 返回值可能已被 __close 换掉
            top = oldTop;
            shrinkStack(); // 与 C 版 luaD_pcall 末尾那句同位置
            return v;
        }
    }

    /**
     * {@code ldebug.c: luaG_errormsg} 里跑消息处理函数的那一段。
     * <p>
     * 处理函数是在<b>出错的那一帧之上</b>起新帧调用的（C 版把 {@code msgh} 压在
     * 当时的 {@code L->top} 上），所以它的 {@code previous} 就是出错的那一帧——
     * 回溯能从这里一路走到底。
     * <p>
     * 重入保护：处理函数自己又出错时，C 版会再次进入 {@code luaG_errormsg}，
     * 而 {@code L->errfunc} 还在，于是一路递归到 C 栈溢出、被
     * {@code luaE_checkcstack} 兜成 {@code LUA_ERRERR}，最后
     * {@code luaD_seterrorobj} 把它写成 {@code "error in error handling"}。
     * 这里直接认这个结果，中间那几十层递归没有意义。
     */
    private LuaValue applyErrMsg(LuaValue v, LuaValue handler) {
        if (inErrMsg) {
            return v;
        }
        inErrMsg = true;
        try {
            int hf = top;
            checkStack(2);
            stack[hf].set(handler);
            stack[hf + 1].set(v);
            top = hf + 2;
            LuaValue bad = pcall(hf, 1); // 处理函数自身出错不再兜底
            if (bad != null) {
                return LuaValue.ofString(LuaString.latin1("error in error handling"));
            }
            LuaValue res = new LuaValue(stack[top - 1]);
            top = hf;
            return res;
        } finally {
            inErrMsg = false;
        }
    }

    /**
     * 出错展开时关掉被丢弃帧上的 upvalue，带错误对象跑 {@code __close}。
     * <p>
     * 对应 {@code ldo.c: luaD_closeprotected}。正常返回时 {@code OP_CLOSE} 与
     * {@code OP_RETURN} 自己会关，<b>出错路径没有人关</b>——不在这里补，
     * {@code local f <close> = ... error('x')} 里的 {@code __close} 就永远不跑。
     * <p>
     * <b>{@code __close} 自己又出错时，新错误顶掉原错误</b>（实测：
     * {@code error('orig')} 之后 {@code __close} 里 {@code error('in close')}，
     * {@code pcall} 拿到的是 {@code in close}）。所以这里返回的是<b>可能已被换掉</b>
     * 的错误对象，调用方必须拿返回值覆盖原来的。
     * <p>
     * 另一个细节是循环：C 版用 {@code luaD_rawrunprotected} 一次只关一批，
     * 出错就记下新错误再接着关剩下的，直到关完。这里同样循环到没有可关的为止，
     * 但加了「一轮下来一个都没关掉就停」的兜底，避免某个 {@code __close}
     * 一进入就抛错时转不出来。
     *
     * @param forceYy 同 {@link #closeUpvals(int, LuaValue, boolean)} 的
     *                {@code forceYy}；由调用方按「这个 {@code pcall} 是不是
     *                可 yield 的 {@code pcall}」给
     * @return 最终的错误对象（可能与传入的 {@code errObj} 不同）
     */
    private LuaValue closeUpvalsOnUnwind(CallInfo target, LuaValue errObj, boolean forceYy) {
        if (target == null || openUpvals.isEmpty()) {
            return errObj;
        }
        int level = target.func + 1;
        CallInfo saved = ci;
        /* 同上：{@code errObj} 可能是栈槽别名，而这一趟下来栈要被反复改写 */
        if (errObj != null) {
            errObj = new LuaValue(errObj);
        }
        while (true) {
            int before = openUpvals.size();
            try {
                closeUpvals(level, errObj, forceYy);
                return errObj;
            } catch (LuaYield y) {
                /*
                 * {@code __close} 里 yield 出去了，本帧的收尾只能等 resume 之后
                 * 由 {@link #finishPcallK} 接着做。错误对象要留给它——见
                 * {@link #closeErrPending} 的说明。
                 */
                closeErrPending = errObj;
                throw y;
            } catch (LuaError e2) {
                LuaValue v = e2.value;
                if (e2.where == null && v != null && v.isString()) {
                    String prefix = where(); // 出错那刻的帧，先于恢复
                    v = LuaValue.ofString(
                            LuaString.latin1(prefix + v.asString().toJavaString()));
                }
                ci = saved;
                errObj = new LuaValue(v);
                if (openUpvals.size() >= before) {
                    return errObj; // 一个也没关掉，再试也不会有进展
                }
            }
        }
    }

    /** 恢复调用链到初始状态。宿主在捕获错误后必须调用，否则下一次调用从脏帧开始 */
    public void reset() {
        ci = baseCi;
        top = 0;
        openUpvals.clear();
        closeErrPending = null;
    }

    /* ==================== 协程：yield / resume / unroll ==================== */

    /**
     * {@code lua_yieldk}。
     * <p>
     * 抛 {@link LuaYield} 而不是返回值——C 版这里是
     * {@code luaD_throw(L, LUA_YIELD)}，一跳到底，中间所有帧都不做收尾。
     * <b>这正是 yield 能穿过 {@code pcall} 的原因</b>：{@code pcall} 只捕
     * {@link LuaError}，这个异常它看不见，于是一路冒到 {@link #resume}。
     * <p>
     * 顺带一提，两条不能 yield 的报错措辞不同，得靠 {@link #mainThread} 区分。
     * 实测参考实现：{@code table.sort} 的比较函数里 yield 报
     * {@code attempt to yield across a C-call boundary}；主协程里直接 yield 报
     * {@code attempt to yield from outside a coroutine}。两条都不带位置前缀，
     * 因为当时 {@code L->ci} 是 C 帧（{@link #where} 对 C 帧返回空串）。
     */
    public void yieldk(int nresults) {
        if (!yieldable()) {
            throw runError((mainThread == this)
                    ? "attempt to yield from outside a coroutine"
                    : "attempt to yield across a C-call boundary");
        }
        status = LUA_YIELD;
        ci.nyield = nresults;
        throw LuaYield.INSTANCE;
    }

    /** {@code lua_yield}：不带续体的形态，{@code coroutine.yield} 用它 */
    public void yield(int nresults) {
        yieldk(nresults);
    }

    /**
     * {@code luaD_callnoyield}：从宿主调进 Lua，但<b>期间不许 yield</b>。
     * <p>
     * {@code table.sort} 的比较函数、{@code string.gsub} 的替换函数这类回调都
     * 走这里——C 版同样用 {@code nyci} 把「不可 yield」这一层记上，所以回调里
     * yield 会报跨 C 调用边界，与参考实现一致。
     */
    public void callNoYield(int funcIdx, int nresults) {
        nNy++;
        try {
            call(funcIdx, nresults);
        } finally {
            nNy--;
        }
    }

    /**
     * {@code lua_resume}。
     * <p>
     * 调用前，给协程的参数必须已经由 {@link #xmove} 压到本线程栈上。
     *
     * @param nargs       压上来的参数个数
     * @param nresultsOut 长度 1 的数组，用来带出「该从协程栈上搬走几个值」
     * @return {@link #LUA_OK} / {@link #LUA_YIELD} / 错误码
     */
    public int resume(LuaState from, int nargs, int[] nresultsOut) {
        if (status == LUA_OK) {
            if (ci != baseCi) {
                return resumeError("cannot resume non-suspended coroutine", nargs);
            } else if (top - (ci.func + 1) == nargs) {
                // base_ci.func 之上只剩参数 —— 说明连函数都没了，协程已经跑完
                return resumeError("cannot resume dead coroutine", nargs);
            }
        } else if (status != LUA_YIELD) {
            return resumeError("cannot resume dead coroutine", nargs);
        }
        /*
         * C 版这里写 {@code L->nCcalls = getCcalls(from)}。
         * <p>
         * <b>必须继承调用深度</b>，不能清零。清了之后一层套一层的协程就永远
         * 到不了 {@code LUAI_MAXCCALLS}：{@code coroutine.wrap(f)()} 的
         * {@code f} 里再 wrap 再调，深度每进一层就被重置回 0，于是无限递归。
         * 上游 {@code cstack.lua}「nesting of resuming yielded coroutines」
         * 与 {@code coroutine.lua} 里那段 wrap 递归专测这一条。
         * <p>
         * 「不可 yield」的 {@code nNy} 则<b>不继承</b>：C 版 {@code getCcalls}
         * 只取低 16 位，高位清零。所以每个协程能不能 yield 是独立算的——
         * 在 {@code table.sort} 的回调里 resume 另一个协程，那个协程照样能
         * yield（实测如此）。
         */
        nNy = 0;
        nCcalls = (from == null) ? 0 : from.nCcalls;
        if (nCcalls >= MAXCCALLS) {
            return resumeError("C stack overflow", nargs);
        }
        nCcalls++;
        int st;
        try {
            doResume(nargs);
            st = LUA_OK;
        } catch (LuaYield y) {
            st = LUA_YIELD;
        } catch (LuaError e) {
            /*
             * luaD_seterrorobj：错误对象必须落到栈顶，后面
             * finishPcallK / auxresume 都是从 top-1 取它的。
             * <p>
             * 顺带把 {@link #closeErrPending} 清掉：这个错误是<b>刚抛出来的</b>，
             * 比「上一趟关变量时被 yield 打断时暂存的那个」新。C 版不存在这个
             * 取舍——它永远取 {@code L->top - 1}，而 {@code luaG_errormsg} 早把
             * 新错误写到那儿了。上游 {@code locals.lua} 里 {@code y} 的
             * {@code __close} 先 yield 再 {@code error(err + 20)}，下一个变量
             * 看到的必须是 30 而不是 10。
             */
            push(errObjOf(e));
            closeErrPending = null;
            st = LUA_ERRRUN;
        }
        st = precover(st);
        if (st != LUA_OK && st != LUA_YIELD) {
            status = st; // 标记线程死亡，之后 resume 一律「cannot resume dead coroutine」
        }
        nresultsOut[0] = (st == LUA_YIELD) ? ci.nyield
                : (top - (ci.func + 1));
        return st;
    }

    private int resumeError(String msg, int narg) {
        top -= narg; // 把参数从栈上摘掉
        push(LuaValue.ofString(LuaString.latin1(msg)));
        return LUA_ERRRUN;
    }

    /** {@code ldo.c: resume} */
    private void doResume(int nargs) {
        if (status == LUA_OK) {
            /*
             * 刚起一个协程：直接调它的主体。
             * <p>
             * C 版是 {@code ccall(L, firstArg - 1, LUA_MULTRET, 0)}——
             * {@code inc} 传的是 <b>0</b>，因为 {@code lua_resume} 进来时已经
             * 做过 {@code L->nCcalls++}。这里再用 {@link #call} 会再加一层，
             * 于是嵌套 resume 的深度变成每级 +2，{@code LUAI_MAXCCALLS} 撑得住
             * 的层数正好减半：上游 {@code cstack.lua} 那几处「final count」
             * 会从 197 掉到 98。
             */
            CallInfo c = precall(top - nargs - 1, MULTRET);
            if (c != null) {
                c.fresh = true;
                Vm.execute(this, c);
            }
            return;
        }
        status = LUA_OK; // 标记「又在跑了」
        CallInfo c = ci;
        if (c.isLua) {
            // 只可能发生在 hook 里 yield，我们没有 hook
            throw new IllegalStateException("yield inside a hook is not supported");
        }
        /*
         * 关键一步：<b>resume 传进来的参数，就是 yield 的返回值</b>。
         * 所以这里用 nargs（而不是 yield 出去的个数）去做 poscall——
         * 实测 {@code coroutine.yield(1)} 之后 {@code resume(co, 99)}，
         * 协程里拿到的就是 99。
         */
        int n = nargs;
        if (c.k != null) {
            n = c.k.call(this, LUA_YIELD, c.ctx);
        }
        poscall(c, n);
        unroll();
    }

    /** {@code ldo.c: unroll}：把被 yield 打断的调用链一路跑到底 */
    private void unroll() {
        while (ci != baseCi) {
            CallInfo c = ci;
            if (!c.isLua) {
                finishCcall(c);
            } else {
                Vm.finishOp(this); // 补完被打断的那条指令
                Vm.execute(this, c); // 一直跑到「fresh 帧返回」为止
            }
        }
    }

    /** {@code ldo.c: finishCcall}：补完一个被 yield 打断的 C 帧 */
    private void finishCcall(CallInfo c) {
        int n;
        if (c.clsret) {
            n = c.nres; // 只是在关 tbc 变量时被中断，重做 poscall 即可
        } else {
            int st = LUA_YIELD; // 没有别的错误时就是「被 yield 打断」
            if (c.yieldPcall) {
                st = finishPcallK(c);
            }
            // adjustresults(L, LUA_MULTRET)：只抬高 ci.top，不动 L.top
            if (c.top < top) {
                c.top = top;
            }
            n = c.k.call(this, st, c.ctx);
        }
        poscall(c, n);
    }

    /**
     * {@code ldo.c: finishpcallk}：补完一个被 yield 打断的 {@code pcall}。
     * <p>
     * 有两种「被打断」：真的 yield 了（{@code recst == LUA_OK}），或者恢复之后
     * 被保护的函数自己出错（{@code recst} 记着那个错误状态）。后者要在这里把
     * tbc 变量关掉、把错误对象放到位——和 {@code luaD_pcall} 出错时做的一样。
     */
    private int finishPcallK(CallInfo c) {
        int st = c.recst;
        if (st != LUA_OK) {
            int fIdx = c.funcIdx; // 被保护的那个函数的栈下标
            /*
             * 正常情况同 C 版 {@code luaD_seterrorobj} 从 {@code top - 1} 取。
             * 但如果上一趟是在 {@code __close} 里 yield 出去的，栈上的 {@code top}
             * 已经被「搬走 yield 值」动过，那里不再是错误对象——取
             * {@link #closeErrPending} 里暂存的那个。
             */
            LuaValue errObj = (closeErrPending != null)
                    ? closeErrPending
                    : new LuaValue(stack[top - 1]);
            closeErrPending = null;
            top = fIdx + 1;
            errObj = closeUpvalsFrom(fIdx, errObj);
            if (c.errHandler != null) {
                errObj = applyHandler(c.errHandler, errObj, fIdx);
            }
            stack[fIdx].set(errObj);
            top = fIdx + 1;
            c.recst = LUA_OK;
            shrinkStack(); // 与 ldo.c: finishpcallk 末尾那句同位置
        } else {
            st = LUA_YIELD; // 不是出错，是 yield
        }
        c.yieldPcall = false;
        return st;
    }

    /**
     * 关掉 {@code level} 以上的 upvalue 并跑 {@code __close}。
     * <p>
     * 与 {@link #closeUpvalsOnUnwind} 同构，同样要处理「{@code __close} 自己又
     * 出错，新错误顶掉旧错误」——C 版 {@code luaF_close} 一次只关一批，
     * 出错就抛，由 {@code luaD_closeprotected} 循环接着关剩下的。
     */
    private LuaValue closeUpvalsFrom(int level, LuaValue errObj) {
        while (true) {
            if (openUpvals.isEmpty()) {
                return errObj;
            }
            int before = openUpvals.size();
            try {
                /*
                 * {@code ldo.c: finishpcallk} 里那句是
                 * {@code luaF_close(L, func, status, 1)} —— {@code yy} 恒为 1。
                 * <p>
                 * 不能按「当前帧是不是 Lua 帧」判：走到这里时 {@code ci} 是被打断的
                 * {@code pcall} 那个 <b>C 帧</b>，照 {@code luaT_callTM} 那套判据
                 * 会退化成 {@code luaD_callnoyield}，于是 {@code __close} 里
                 * {@code coroutine.yield} 报「attempt to yield across a C-call
                 * boundary」。上游 {@code locals.lua}「yielding inside closing
                 * metamethods」那一段正测这个：三个 {@code <close>} 变量，
                 * 中间那个在 {@code __close} 里 yield 完再 {@code error}，
                 * 剩下那个要能接着 yield。
                 */
                closeUpvals(level, errObj, true);
                return errObj;
            } catch (LuaYield y) {
                /*
                 * 还有没关完的：yield 出去之后 {@link #finishPcallK} 会再进来
                 * 一趟（{@code recst} 还是错误状态），错误对象得留着。
                 */
                closeErrPending = errObj;
                throw y;
            } catch (LuaError e2) {
                errObj = e2.value;
                if (openUpvals.size() >= before) {
                    return errObj; // 一个也没关掉，再试也不会有进展
                }
            }
        }
    }

    /**
     * 跑 {@code xpcall} 的消息处理函数，返回值顶掉原来的错误对象。
     * <p>
     * 与 {@link #pcall(int, int, LuaValue)} 里那段同形，只是错误对象来自
     * 协程恢复路径而不是 {@code catch}。
     */
    private LuaValue applyHandler(LuaValue handler, LuaValue err, int fIdx) {
        int hf = top;
        checkStack(2);
        stack[hf].set(handler);
        stack[hf + 1].set(err);
        top = hf + 2;
        LuaValue bad = pcall(hf, 1); // 处理函数自身出错不再兜底
        if (bad != null) {
            top = fIdx + 1;
            return bad;
        }
        LuaValue res = new LuaValue(stack[top - 1]);
        top = fIdx + 1;
        return res;
    }

    /**
     * {@code ldo.c: precover}：出错时一层层往回找恢复点。
     * <p>
     * 只要还能找到一个被打断的 {@code pcall}，就把调用链退到那一层，
     * 记下错误状态，再跑一次 {@link #unroll}——于是错误在这层 {@code pcall}
     * 上被「捕获」，协程继续跑。找不到恢复点才是真的错误，线程就此死亡。
     */
    private int precover(int st) {
        while (st != LUA_OK && st != LUA_YIELD) {
            CallInfo c = findPcall();
            if (c == null) {
                return st;
            }
            ci = c;
            c.recst = st;
            try {
                unroll();
                st = LUA_OK;
            } catch (LuaYield y) {
                st = LUA_YIELD;
            } catch (LuaError e) {
                push(errObjOf(e));
                closeErrPending = null; // 同上：新错误顶掉 yield 时暂存的那个
                st = LUA_ERRRUN;
            }
        }
        return st;
    }

    /** {@code ldo.c: findpcall} */
    private CallInfo findPcall() {
        for (CallInfo c = ci; c != null; c = c.previous) {
            if (c.yieldPcall) {
                return c;
            }
        }
        return null;
    }

    /**
     * {@code lua_closethread}：关掉协程里所有待关闭（{@code <close>}）的变量。
     * <p>
     * {@code coroutine.close} 用它。已死亡的协程没有可关的东西，直接报原状态。
     */
    public int closeThread(LuaState from) {
        return (closeThreadValue(from) == null) ? LUA_OK : LUA_ERRRUN;
    }

    /**
     * {@code lua_closethread}：关掉协程里所有待关闭（{@code <close>}）的变量，
     * 并把协程置为死亡。
     * <p>
     * 已经因为出错而死的协程没有可关的东西，直接返回 {@code null}——这一点很
     * 要紧：{@code coroutine.wrap} 在错误分支里调它，如果这里还「搬一次错误
     * 对象」，搬走的会是栈上的陈值，把真正的错误消息顶掉。
     *
     * @return {@code __close} 抛出的错误对象（已压在本线程栈顶，供
     *         {@link #xmove} 搬走）；没有错误返回 {@code null}
     */
    public LuaValue closeThreadValue(LuaState from) {
        return closeThreadValue(from,
                (top > 0) ? new LuaValue(stack[top - 1]) : LuaValue.ofNil());
    }

    /**
     * 带「原错误对象」的重载。
     * <p>
     * {@code coroutine.wrap} 必须走这一个：它在调 {@code lua_closethread}
     * <b>之前</b>已经用 {@code lua_xmove(co, L, 1)} 把错误对象搬走了，协程栈上
     * 的 {@code top} 已经降了一格，{@code stack[top - 1]} 不再是错误对象——
     * 拿到的是排序比较用的 {@code 1} 之类的陈值。
     * <p>
     * C 版不会踩到这个坑，因为 {@code luaD_seterrorobj} 的 {@code default}
     * 分支是 {@code setobjs2s(L, oldtop, L->top - 1)}：错误对象在栈上是
     * <b>连着两格</b>的，搬走上面那格之后下面那格还是它。本实现的错误对象
     * 住在异常里、不在栈上，所以只有一格，只能由调用方把它带进来。
     */
    public LuaValue closeThreadValue(LuaState from, LuaValue prevErr) {
        /*
         * {@code lua_closethread} 的第一句就是
         * {@code L->nCcalls = (from) ? getCcalls(from) : 0}。
         * <p>
         * 这行是<b>链式 close 唯一的深度记账</b>：{@code __close} 里再
         * {@code coroutine.close} 上一个协程时，深度靠它往上传一层。漏了，
         * 一千个互相 close 的协程就会真的递归一千层——上游
         * {@code cstack.lua}「chain of 'coroutine.close'」断言的正是
         * {@code C stack overflow}，不是 Unlimited Recursion。
         */
        nCcalls = (from == null) ? 0 : from.nCcalls;
        /*
         * {@code lstate.c: luaE_resetthread}。
         * <p>
         * 两个反直觉的地方，都实测于参考实现（上游 {@code coroutine.lua}
         * 「closing a coroutine after an error」那一段）：
         * <ul>
         *   <li><b>已经因错而死的协程也要走这一步</b>，而且要把<b>原来的错误对象</b>
         *       原样带回来。{@code coroutine.close(co)} 在协程因
         *       {@code error(100)} 死亡之后返回 {@code false, 100}——
         *       之前这里直接返回 null（「已死亡，没有可关的」），于是返回
         *       {@code true}，断言就炸了。原来的错误对象在栈顶，是
         *       {@code lua_resume} 出错时用 {@code luaD_seterrorobj} 放上去的。</li>
         *   <li>{@code status} 要<b>先</b>置成 {@code LUA_OK} 再关，因为
         *       {@code __close} 本身要在「线程还活着」的前提下跑；而且关完之后
         *       <b>不</b>恢复成原来的错误码。所以紧接着第二次
         *       {@code coroutine.close(co)} 返回的是 {@code true}。</li>
         * </ul>
         */
        int st = status;
        if (st == LUA_YIELD) {
            st = LUA_OK;
        }
        LuaValue errObj = (st == LUA_OK) ? LuaValue.ofNil() : new LuaValue(prevErr);
        status = LUA_OK; // 让 __close 能跑
        LuaValue err = null;
        /*
         * {@code luaD_closeprotected}：一个 {@code __close} 抛了错，那个错误对象
         * 要<b>接着传给下一个</b> {@code __close}，而不是就地放弃。实测参考实现：
         * 两个 tbc 变量，内层用 {@code error(111)}、外层看到
         * {@code err == 111} 后再 {@code error(200)}，最终
         * {@code coroutine.close} 返回 {@code false, 200}——只关一个就返回的话
         * 拿到的是 {@code false, 111}，而且外层那个 {@code __close} 要等第二次
         * {@code close} 才跑（上游 {@code coroutine.lua} 就测这一条）。
         */
        while (true) {
            try {
                closeUpvals(1, errObj); // 从第 1 格起全部关掉
                break;
            } catch (LuaError e) {
                errObj = e.value; // 下一个 __close 看到的 err
                err = e.value;    // 也是最终要交出去的那个
                st = LUA_ERRRUN;
                ci = baseCi;      // 与 C 版「恢复 old_ci 再重来」一致
            }
        }
        ci = baseCi;
        ensureSlots(2);
        top = 1;
        if (st != LUA_OK) {
            stack[1].set((err != null) ? err : errObj); // luaD_seterrorobj
            top = 2;
        }
        baseCi.top = top + MINSTACK;
        status = LUA_OK; // 关完之后一律「正常」——第二次 close 才会返回 true
        return (st == LUA_OK) ? null : new LuaValue(stack[1]);
    }

    /* ==================== 加载与执行 ==================== */

    /**
     * 编译一段源码，返回主 chunk 的闭包。
     * <p>
     * 对应 {@code lapi.c: lua_load}：闭包的第 0 号 upvalue 是关闭的，里面放全局表。
     */
    public LClosure load(byte[] source, String chunkName) {
        return load(new Zio(source), LuaString.utf8(chunkName).bytes);
    }

    /**
     * 从一条（可能是惰性的）输入流编译。
     *
     * @see Zio#Source
     */
    public LClosure load(Zio z, String chunkName) {
        return load(z, LuaString.utf8(chunkName).bytes);
    }

    /** 源名是 Lua 字符串（字节串）时的重载，避免 UTF-8 往返带来的失真 */
    public LClosure load(Zio z, byte[] chunkName) {
        return loadDispatch(z, chunkName, z.getc());
    }

    /**
     * {@code ldo.c: f_parser} 那一步分派：首字节是 {@code LUA_SIGNATURE} 的
     * 第一个字节就走 {@code luaU_undump}，否则才进词法／语法分析。
     * <p>
     * 这一层之前只存在于 {@link BaseLib} 的 {@code load} / {@code loadfile} 里，
     * 而 {@code lua_load} 本身（也就是 {@link #load}）不看签名——于是
     * {@code require} 一个<b>预编译模块</b>会失败：{@code PackageLib} 走的是
     * {@link #loadFile}，报的是
     * {@code unexpected symbol near '<\27>'}（把签名当源码读了）。
     * 参考实现里 {@code require} 预编译 chunk 是正常路径——
     * {@code luac -o mod.luac} 之后直接 {@code require "mod"} 是常见分发方式。
     * <p>
     * 二进制分支<b>不回退</b>首字节：C 的 {@code luaU_undump} 注释写得很直白，
     * 「skip 1st char (already read and checked)」，{@code checkHeader} 从第二个
     * 字节开始比。
     */
    private LClosure loadDispatch(Zio z, byte[] chunkName, int firstChar) {
        if (firstChar == Lua.LUA_SIGNATURE0) {
            return undump(z, chunkName);
        }
        return load(z, chunkName, firstChar);
    }

    /**
     * {@code firstChar} 是已经读出来的首字节（{@code f_parser} 的形态）。
     *
     * @see Lexer#Lexer(Zio, byte[], StringPool, int)
     */
    public LClosure load(Zio z, byte[] chunkName, int firstChar) {
        this.chunkName = chunkName;
        /*
         * luaD_protectedparser 那层保护：<b>出错时 nCcalls 要整体回滚</b>
         * （C 是 luaD_rawrunprotected 里的 {@code L->nCcalls = oldnCcalls}）。
         * <p>
         * 它必须落在<b>这一层</b>而不是 loadDispatch：解析器的 enterlevel 借的
         * 是 nCcalls，语法错误在 leavelevel 之前就抛出来，不平衡的层数会永久
         * 留在计数器上。实测：{@code load("x = =")} 失败一次漏两层，上游
         * {@code errors.lua} 里几十次失败解析之后，深度配额从 197 掉到 95，
         * 于是「error in error handling」那段提前触发，整个文件假红。
         */
        int saved = nCcalls;
        try {
            Proto p = Parser.parse(z, chunkName, strings, firstChar, this);
            LClosure cl = new LClosure(p, 1);
            cl.initUpvals();
            cl.upvals[0] = UpVal.closed(LuaValue.ofTable(globals));
            return cl;
        } finally {
            nCcalls = saved;
        }
    }

    /** 二进制 chunk：走 {@code luaU_undump} 而不是词法／语法分析 */
    public LClosure undump(Zio z, byte[] name) {
        this.chunkName = name;
        return Undump.undump(this, z, name);
    }

    /**
     * {@code luaL_loadfilex}：编译一个脚本文件。
     * <p>
     * 与 {@link #load} 的差别是会跳过 BOM 与 {@code #!} 行（那属于加载层，
     * 不属于词法层）。行号必须靠补一个 {@code \n} 保住。
     */
    public LClosure loadFile(byte[] source, String chunkName) {
        byte[] name = LuaString.utf8(chunkName).bytes;
        this.chunkName = name;
        /*
         * 先过 lauxlib 那一步（BOM / #!），再按签名分派。
         * 顺序不能反：预编译 chunk 的第一个字节是签名，不是 '#'，
         * skipFilePreamble 不会动它；反过来若先分派就吃不到 BOM 了。
         */
        Zio z = new Zio(Parser.skipFilePreamble(source));
        return loadDispatch(z, name, z.getc());
    }

    /** 编译并执行一段源码 */
    public void run(String source) {
        run(LuaString.latin1(source).bytes, "=chunk");
    }

    /** 编译并执行，等价于 {@code luaL_dostring} */
    public void run(byte[] source, String chunkName) {
        int base = top;
        pushClosure(load(source, chunkName));
        call(base, 0);
    }

    /** 带保护地执行；返回错误对象或 {@code null} */
    public LuaValue runProtected(byte[] source, String chunkName) {
        int base = top;
        pushClosure(load(source, chunkName));
        return pcall(base, 0);
    }

    /* ==================== 错误 ==================== */

    /**
     * {@code luaG_runerror}。
     * <p>
     * 位置前缀直接<b>拼进错误值</b>，不是另存一份元数据——C 版就是这样：
     * {@code luaG_addinfo} 改写的是栈顶那个消息串。{@link LuaError#where}
     * 只当「已经加过位置」的标记用，避免 {@link #pcall} 再加一次。
     */
    public LuaError runError(String msg) {
        String prefix = where();
        return new LuaError(LuaValue.ofString(LuaString.latin1(prefix + msg)), prefix);
    }

    /** {@code luaG_typeerror}（含 {@code varinfo} 的变量名后缀） */
    public LuaError typeError(LuaValue v, String op) {
        return runError("attempt to " + op + " a " + LuaTm.objTypeName(this, v)
                + " value" + varInfo(v));
    }

    /**
     * {@code luaG_callerror}：名字先从「怎么调的」找，找不到才退回 {@link #varInfo}。
     * <p>
     * 两条路都试是必要的：{@code t:nope()} 走 {@code funcnamefromcall}
     * （{@code OP_CALL} → {@code OP_SELF} → "method 'nope'"），而
     * {@code local f = nil; f()} 这类只能靠 {@code varinfo} 认出局部变量。
     * 只用其中一条就会在另一类场景丢掉 {@code (method 'xxx')} 后缀。
     */
    public LuaError callError(LuaValue v) {
        String[] name = new String[1];
        String kind = funcNameFromCall(name);
        String extra = (kind != null) ? formatVarInfo(kind, name[0]) : varInfo(v);
        return runError("attempt to call a " + LuaTm.objTypeName(this, v) + " value" + extra);
    }

    /** {@code ldebug.c: funcnamefromcall} */
    private String funcNameFromCall(String[] name) {
        CallInfo c = ci;
        if (c == null || c.func >= stack.length) {
            return null;
        }
        // CIST_FIN：正在跑 __gc，报成「metamethod '__gc'」
        if (c.fin) {
            name[0] = "__gc";
            return "metamethod";
        }
        if (!c.isLua) {
            return null;
        }
        LuaValue f = stack[c.func];
        if (f.tt != Lua.VLIF) {
            return null;
        }
        return funcNameFromCode(((LClosure) f.o).p,
                (c.savedpc > 0) ? c.savedpc - 1 : 0, name);
    }

    /**
     * {@code ldebug.c: funcnamefromcode}：按<b>调用点那条指令</b>反推它在调什么。
     * <p>
     * 元方法那一批共用一个名字来源：{@code tmname[tm]} 去掉开头的 {@code __}
     * （C 版就是 {@code getshrstr(tmname[tm]) + 2}）。
     */
    static String funcNameFromCode(Proto p, int pc, String[] name) {
        if (pc < 0 || pc >= p.code.length) {
            return null;
        }
        int i = p.code[pc];
        int tm = -1;
        switch (OpCode.getOpCode(i)) {
        case OpCode.OP_CALL:
        case OpCode.OP_TAILCALL:
            return getObjName(p, pc, OpCode.getArgA(i), name);
        case OpCode.OP_TFORCALL:
            name[0] = "for iterator";
            return "for iterator";
        case OpCode.OP_SELF:
        case OpCode.OP_GETTABUP:
        case OpCode.OP_GETTABLE:
        case OpCode.OP_GETI:
        case OpCode.OP_GETFIELD:
            tm = Tm.TM_INDEX;
            break;
        case OpCode.OP_SETTABUP:
        case OpCode.OP_SETTABLE:
        case OpCode.OP_SETI:
        case OpCode.OP_SETFIELD:
            tm = Tm.TM_NEWINDEX;
            break;
        case OpCode.OP_MMBIN:
        case OpCode.OP_MMBINI:
        case OpCode.OP_MMBINK:
            tm = OpCode.getArgC(i);
            break;
        case OpCode.OP_UNM:
            tm = Tm.TM_UNM;
            break;
        case OpCode.OP_BNOT:
            tm = Tm.TM_BNOT;
            break;
        case OpCode.OP_LEN:
            tm = Tm.TM_LEN;
            break;
        case OpCode.OP_CONCAT:
            tm = Tm.TM_CONCAT;
            break;
        case OpCode.OP_EQ:
            tm = Tm.TM_EQ;
            break;
        case OpCode.OP_LT:
        case OpCode.OP_LTI:
        case OpCode.OP_GTI:
            tm = Tm.TM_LT;
            break;
        case OpCode.OP_LE:
        case OpCode.OP_LEI:
        case OpCode.OP_GEI:
            tm = Tm.TM_LE;
            break;
        case OpCode.OP_CLOSE:
        case OpCode.OP_RETURN:
        case OpCode.OP_RETURN0:
        case OpCode.OP_RETURN1:
            tm = Tm.TM_CLOSE;
            break;
        default:
            return null;
        }
        String ev = (tm >= 0 && tm < Tm.EVENT_NAMES.length) ? Tm.EVENT_NAMES[tm] : null;
        name[0] = (ev != null && ev.startsWith("__")) ? ev.substring(2) : "?";
        return "metamethod";
    }

    /**
     * {@code luaG_opinterror}：算术/位运算的类型错误报<b>坏掉的那一个</b>。
     * <p>
     * 判据是「第一个操作数不是数字就报它」——所以 {@code 1 + nil} 与
     * {@code nil + 1} 都得到「attempt to perform arithmetic on a nil value」，
     * 而不是把两个类型都打进去。
     */
    public LuaError opInterror(LuaValue p1, LuaValue p2, String op) {
        return typeError(p1.isNumber() ? p2 : p1, op);
    }

    /**
     * {@code luaG_ordererror}。
     * <p>
     * 两个类型名都取自 {@link LuaTm#objTypeName}（C 版的
     * {@code luaT_objtypename}），也就是先看 {@code __name}。
     * 用 {@code v.typeName()} 会让 {@code XX < io.stdin} 报成
     * {@code nil with userdata}，而参考实现给 {@code nil with FILE*}。
     */
    public LuaError orderError(LuaValue p1, LuaValue p2) {
        String t1 = LuaTm.objTypeName(this, p1);
        String t2 = LuaTm.objTypeName(this, p2);
        if (t1.equals(t2)) {
            return runError("attempt to compare two " + t1 + " values");
        }
        return runError("attempt to compare " + t1 + " with " + t2);
    }

    /**
     * 抛出一个带位置信息的错误，对应 {@code error(msg, level)} 与
     * {@code assert} 的失败路径。
     * <p>
     * 返回值写成异常是为了调用点能直接 {@code throw L.errorAt(...)}。
     * {@link #where} 非空是这个错误「已经带位置」的标记，{@link #pcall} 据此
     * 决定要不要再补一次——补两次会得到 {@code file:1: file:1: msg} 这种叠前缀。
     *
     * @param level 位置取自第几层调用者（{@code 1} = 直接调用者）；
     *              {@code <= 0} 表示不加位置
     */
    public LuaError errorAt(LuaValue msg, int level) {
        if (level > 0 && msg.isString()) {
            String prefix = whereAt(level);
            return new LuaError(
                    LuaValue.ofString(LuaString.latin1(prefix + msg.asString().toJavaString())),
                    prefix);
        }
        return new LuaError(msg, "");
    }

    /**
     * {@code luaL_error} / {@code luaL_argerror}：库函数抛的错误。
     * <p>
     * 位置取自 {@code luaL_where(L, 1)}（回跳一层到调用者），<b>不是</b>
     * {@link #where}：库函数执行时 {@code ci} 是 C 帧，{@link #where} 只会
     * 给出空串，报出来的消息就没有 {@code src:line:} 前缀了。两类错误的取位
     * 方式不同，是 {@code luaG_runerror} 与 {@code luaL_error} 本身就不同——
     * 合成一个会在某一边丢掉行号。
     */
    public LuaError libError(String msg) {
        return errorAt(LuaValue.ofString(LuaString.latin1(msg)), 1);
    }

    /* ==================== lauxlib.c 那套「参数检查」 ==================== */

    /**
     * 取第 {@code arg} 个参数（<b>1 起</b>，与 {@code luaL_*} 一致）。
     * <p>
     * 越界时返回一个 {@code LUA_TNONE} 的值而不是栈槽里的残值：C 里
     * {@code lua_type} 对越界下标返回 {@code LUA_TNONE}，报出来是
     * 「no value」，而栈槽里往往还是 nil，报出来就成了「nil」——
     * 两者是不同的错误文本，混掉就会在 {@code pcall(f)} 这类用例上对不上。
     */
    LuaValue arg1(int arg) {
        int idx = ci.func + arg; // 1 起：arg=1 → func+1 是第一个参数
        if (idx >= top) {
            return LuaValue.ofNil(); // 仅用于取值，配合下面的 isNone 判断
        }
        return stack[idx];
    }

    /** {@code lua_isnone}：第 {@code arg} 个参数（1 起）根本没传？ */
    boolean isNoneArg(int arg) {
        return ci.func + arg >= top;
    }

    /** {@code luaL_typename}：越界是 {@code no value} */
    String argTypeName(int arg) {
        return isNoneArg(arg) ? "no value" : stack[ci.func + arg].typeName();
    }

    /**
     * {@code lua_type}（1 起参数下标）：越界是 {@link Lua#TNONE} 而不是 nil。
     * <p>
     * 专供 {@code luaL_argexpected} 那类「条件是若干类型之一」的判断：
     * 缺参必须落进<b>不满足</b>那一支，否则 {@code setmetatable({})} 会因为
     * 「nil 也算合法」而静默通过 —— 参考实现报
     * {@code bad argument #2 to 'setmetatable' (nil or table expected, got no value)}。
     */
    int argBaseType(int arg) {
        return isNoneArg(arg) ? Lua.TNONE : stack[ci.func + arg].baseType();
    }

    /**
     * {@code lauxlib.c: luaL_typeerror}：{@code "<tname> expected, got <X>"}。
     * <p>
     * {@code __name} 元字段优先于真实类型名——这是 userdata 报
     * 「MyType expected」的机制，照抄。
     */
    public LuaError typeArgError(int arg, String tname) {
        String typeArg;
        if (!isNoneArg(arg)) {
            LuaValue v = stack[ci.func + arg];
            LuaValue nm = metafieldOf(v, "__name");
            if (nm != null && nm.isString()) {
                typeArg = nm.asString().toJavaString();
            } else if (v.tt == Lua.TLIGHTUSERDATA) {
                /*
                 * {@code lauxlib.c: luaL_typeerror} 对<b>轻量</b> userdata 有
                 * 专门的措辞。{@code luaT_typenames_} 里它和完整 userdata
                 * 共用 {@code "userdata"}，只有报错走的这一条会区分——
                 * 实测 {@code debug.setuservalue(lightud, {})} 报
                 * {@code got light userdata}，上游 {@code errors.lua} 断言这句话。
                 */
                typeArg = "light userdata";
            } else {
                typeArg = v.typeName();
            }
        } else {
            typeArg = "no value";
        }
        return argError(arg, tname + " expected, got " + typeArg);
    }

    /**
     * {@code lauxlib.c: luaL_argerror}。
     * <p>
     * 函数名有三个来源，顺序不能改：
     * <ol>
     *   <li><b>调用点</b>（{@code ldebug.c: getfuncname}）：从调用者那条
     *       {@code OP_CALL} 反推。所以 {@code local f = function() string.len(nil) end}
     *       报的是 {@code 'len'}，与 {@code pcall(string.len)} 报的
     *       {@code 'string.len'} 不一样——后者拿不到调用点（调用者是 C 帧）。</li>
     *   <li><b>{@code _LOADED} 反查</b>（{@code pushglobalfuncname}）：在
     *       {@code package.loaded} 里找两层，找到 {@code string.len}。</li>
     *   <li>都没有就给 {@code ?}。</li>
     * </ol>
     * <p>
     * 还有一处「减一」：以方法形式调用（{@code s:rep(nil)}）时 {@code self}
     * 不算在参数编号里，所以 {@code arg--}；减完是 0 说明错的就是 self 本身，
     * 那时报 {@code "calling 'x' on bad self"} 而不是 {@code bad argument}。
     */
    public LuaError argError(int arg, String extra) {
        String[] name = new String[1];
        String what = null;
        if (ci != null && !ci.tail) {
            what = callerFuncName(name);
        }
        if ("method".equals(what)) {
            arg--;
            if (arg == 0) {
                return libError("calling '" + name[0] + "' on bad self (" + extra + ")");
            }
        }
        if (name[0] == null) {
            name[0] = globalFuncName();
            if (name[0] == null) {
                name[0] = "?";
            }
        }
        return libError("bad argument #" + arg + " to '" + name[0] + "' (" + extra + ")");
    }

    /** {@code ldebug.c: getfuncname}：从<b>调用者</b>那帧反推被调函数的名字 */
    private String callerFuncName(String[] name) {
        CallInfo prev = (ci == null) ? null : ci.previous;
        if (prev == null || !prev.isLua || prev.func >= stack.length) {
            return null;
        }
        LuaValue f = stack[prev.func];
        if (f.tt != Lua.VLIF) {
            return null;
        }
        return funcNameFromCode(((LClosure) f.o).p, (prev.savedpc > 0) ? prev.savedpc - 1 : 0,
                name);
    }

    /**
     * {@code luaL_getmetafield}：直接读元表字段，<b>不</b>做元表的元表递归。
     * <p>
     * 与 {@link LuaTm#getTmByObj} 分得开是必要的：元方法查找要走 {@code __index}
     * 链，而 {@code __name} 这种「描述性字段」只认自己那张表——
     * 递归进去会拿到别处的 {@code __name}。
     */
    LuaValue metafieldOf(LuaValue v, String name) {
        LuaTable mt = LuaTm.metatableOf(this, v);
        if (mt == null) {
            return null;
        }
        LuaValue r = mt.get(LuaString.latin1(name));
        return (r == null || r.isNil()) ? null : r;
    }

    /** {@code lauxlib.c: pushglobalfuncname} 的前半段：在 {@code _LOADED} 里找两层 */
    private String globalFuncName() {
        if (ci == null || ci.func >= stack.length) {
            return null;
        }
        String s = findFieldName(loaded, stack[ci.func], 2);
        if (s == null) {
            return null;
        }
        return s.startsWith("_G.") ? s.substring(3) : s;
    }

    /**
     * {@code lauxlib.c: findfield}。
     * <p>
     * 只认字符串键（C 版 {@code lua_type(L,-2) == LUA_TSTRING}），且<b>只下钻两层</b>：
     * 这正是 {@code string.len} 能查到、{@code a.b.c} 查不到的原因。
     */
    static String findFieldName(LuaTable t, LuaValue obj, int level) {
        if (level == 0) {
            return null;
        }
        LuaValue key = LuaValue.ofNil();
        while (true) {
            LuaValue[] r = t.next(key);
            if (r == null) {
                return null;
            }
            key = r[0];
            LuaValue k = r[0];
            LuaValue v = r[1];
            if (k.isString()) {
                if (LuaValue.rawEqual(obj, v)) {
                    return k.asString().toJavaString();
                }
                if (v.isTable()) {
                    String sub = findFieldName(v.asTable(), obj, level - 1);
                    if (sub != null) {
                        return k.asString().toJavaString() + "." + sub;
                    }
                }
            }
        }
    }

    /**
     * {@code luaG_concaterror}：拼接失败时报<b>坏掉的那一个</b>操作数。
     * <p>
     * 判据是「第一个操作数本身能当字符串用吗」——能，说明坏的是第二个；
     * 不能，就报第一个。所以 {@code nil .. 'x'} 与 {@code 'x' .. nil}
     * 都得到「attempt to concatenate a nil value」。
     */
    public LuaError concatError(LuaValue p1, LuaValue p2) {
        LuaValue bad = (p1.isString() || p1.isNumber()) ? p2 : p1;
        return typeError(bad, "concatenate");
    }

    /**
     * {@code luaG_forerror}：消息里要带出错值的类型名。
     * <p>
     * 用 {@link LuaTm#objTypeName} 而不是 {@code v.typeName()}：C 版
     * {@code luaG_forerror} 调的是 {@code luaT_objtypename}，
     * 会先看元表的 {@code __name}。实测 {@code for i = io.stdin, 10 do end}
     * 在参考实现里报 {@code got FILE*}，不是 {@code got userdata}
     * ——上游 {@code errors.lua} 有一整组这样的断言。
     */
    public LuaError forError(LuaValue v, String what) {
        return runError("bad 'for' " + what + " (number expected, got "
                + LuaTm.objTypeName(this, v) + ")");
    }

    /**
     * {@code ldebug.c: varinfo}：给出错误值所在变量的名字，形如
     * {@code " (local 'b')"}、{@code " (constant '10')"}、{@code " (method 'nope')"}。
     * <p>
     * 三段来源，顺序与 C 版一致：先认 upvalue，再认寄存器，寄存器还要反推指令流
     * （{@code findsetreg} + {@code getobjname}）才能知道它是常量、字段还是方法。
     * 少了反推那一段，{@code '10' | 1} 就只能报「attempt to perform bitwise
     * operation on a string value」，而参考实现报的是
     * 「… on a string value (constant '10')」——这类后缀在真实项目里天天出现。
     *
     * @return {@code " (kind 'name')"} 或空串
     */
    public String varInfo(LuaValue o) {
        CallInfo c = ci;
        if (c == null || !c.isLua || c.func >= stack.length) {
            return "";
        }
        LuaValue f = stack[c.func];
        if (f.tt != Lua.VLIF) {
            return "";
        }
        LClosure cl = (LClosure) f.o;
        Proto p = cl.p;
        String[] name = new String[1];
        String kind = getUpvalName(cl, o, name);
        if (kind == null) {
            int reg = inStack(c, o);
            if (reg >= 0) {
                kind = getObjName(p, (c.savedpc > 0) ? c.savedpc - 1 : 0, reg, name);
            }
        }
        return formatVarInfo(kind, name[0]);
    }

    /**
     * {@code luaG_findlocal}：<b>当前</b> Lua 帧里第 {@code n} 号局部量的名字。
     * <p>
     * {@code checkclosemth} 用它给「non-closable value」报错带上变量名。
     *
     * @param n 从 1 起（与 {@code luaF_getlocalname} 一致）
     */
    public LuaString localNameAt(int n) {
        CallInfo c = ci;
        if (c == null || !c.isLua || c.func >= stack.length) {
            return null;
        }
        LuaValue f = stack[c.func];
        if (f.tt != Lua.VLIF) {
            return null;
        }
        return ((LClosure) f.o).p.getLocalName(n, (c.savedpc > 0) ? c.savedpc - 1 : 0);
    }

    private static String formatVarInfo(String kind, String name) {
        if (kind == null) {
            return "";
        }
        return " (" + kind + " '" + ((name == null) ? "?" : name) + "')";
    }

    /** {@code ldebug.c: instack}：值是第几个寄存器？不在栈上返回 {@code -1} */
    private int inStack(CallInfo c, LuaValue o) {
        int base = c.func + 1;
        for (int pos = 0; base + pos < c.top && base + pos < stack.length; pos++) {
            if (stack[base + pos] == o) {
                return pos;
            }
        }
        return -1;
    }

    /** {@code ldebug.c: getupvalname} */
    private static String getUpvalName(LClosure cl, LuaValue o, String[] name) {
        for (int i = 0; i < cl.upvals.length; i++) {
            UpVal uv = cl.upvals[i];
            if (uv != null && uv.value() == o) {
                name[0] = upvalName(cl.p, i);
                return "upvalue";
            }
        }
        return null;
    }

    /** {@code ldebug.c: upvalname}：没有名字时给 {@code "?"}，与 C 版一致 */
    static String upvalName(Proto p, int uv) {
        LuaString s = (uv < p.sizeupvalues && p.upvalues[uv] != null)
                ? p.upvalues[uv].name : null;
        return (s == null) ? "?" : s.toJavaString();
    }

    /** {@code ldebug.c: kname}：只有字符串常量才有名字 */
    static String kName(Proto p, int index, String[] name) {
        if (index < 0 || index >= p.k.length) {
            name[0] = "?";
            return null;
        }
        LuaValue kv = p.k[index];
        if (kv.isString()) {
            name[0] = kv.asString().toJavaString();
            return "constant";
        }
        name[0] = "?";
        return null;
    }

    /** {@code ldebug.c: filterpc}：在跳转目标之前的赋值不算数（可能没执行到） */
    private static int filterPc(int pc, int jmptarget) {
        return (pc < jmptarget) ? -1 : pc;
    }

    /**
     * {@code ldebug.c: findsetreg}：反推「最后一次改写 {@code reg} 的指令」。
     * <p>
     * 只扫 {@code lastpc} 之前的指令，且一旦被 {@code jmptarget} 之后的条件跳转
     * 越过就作废——这正是它叫「符号执行」的原因：要顺着控制流判断哪次赋值
     * 真的发生过。
     */
    static int findSetReg(Proto p, int lastpc, int reg) {
        int setreg = -1;
        int jmptarget = 0;
        int[] code = p.code;
        if (lastpc >= 0 && lastpc < code.length && OpCode.testMMMode(OpCode.getOpCode(code[lastpc]))) {
            lastpc--; // 前一条指令其实没执行（那是落空的 MMBIN）
        }
        for (int pc = 0; pc < lastpc && pc < code.length; pc++) {
            int i = code[pc];
            int op = OpCode.getOpCode(i);
            int a = OpCode.getArgA(i);
            boolean change;
            switch (op) {
            case OpCode.OP_LOADNIL: {
                int b = OpCode.getArgB(i);
                change = (a <= reg && reg <= a + b);
                break;
            }
            case OpCode.OP_TFORCALL: {
                change = (reg >= a + 2);
                break;
            }
            case OpCode.OP_CALL:
            case OpCode.OP_TAILCALL: {
                change = (reg >= a);
                break;
            }
            case OpCode.OP_JMP: {
                int dest = pc + 1 + OpCode.getArgSJ(i);
                if (dest <= lastpc && dest > jmptarget) {
                    jmptarget = dest;
                }
                change = false;
                break;
            }
            default:
                change = (OpCode.testAMode(op) && reg == a);
                break;
            }
            if (change) {
                setreg = filterPc(pc, jmptarget);
            }
        }
        return setreg;
    }

    /** {@code ldebug.c: basicgetobjname}：局部名优先，其次反推指令流 */
    static String basicGetObjName(Proto p, int[] ppc, int reg, String[] name) {
        int pc = ppc[0];
        LuaString local = p.getLocalName(reg + 1, pc);
        if (local != null) {
            name[0] = local.toJavaString();
            return "local";
        }
        pc = findSetReg(p, pc, reg);
        ppc[0] = pc;
        if (pc != -1 && pc < p.code.length) {
            int i = p.code[pc];
            switch (OpCode.getOpCode(i)) {
            case OpCode.OP_MOVE: {
                int b = OpCode.getArgB(i);
                if (b < OpCode.getArgA(i)) {
                    return basicGetObjName(p, ppc, b, name);
                }
                break;
            }
            case OpCode.OP_GETUPVAL: {
                name[0] = upvalName(p, OpCode.getArgB(i));
                return "upvalue";
            }
            case OpCode.OP_LOADK:
                return kName(p, OpCode.getArgBx(i), name);
            case OpCode.OP_LOADKX:
                return kName(p, OpCode.getArgAx(p.code[pc + 1]), name);
            default:
                break;
            }
        }
        return null;
    }

    /** {@code ldebug.c: rname}：只要常量名，别的都写成 {@code "?"} */
    static void rName(Proto p, int pc, int c, String[] name) {
        int[] ppc = { pc };
        String what = basicGetObjName(p, ppc, c, name);
        if (what == null || what.charAt(0) != 'c') {
            name[0] = "?";
        }
    }

    /** {@code ldebug.c: rkname}：RK 操作数，可能是常量也可能是寄存器 */
    static void rkName(Proto p, int pc, int i, String[] name) {
        int c = OpCode.getArgC(i);
        if (OpCode.getArgK(i) != 0) {
            kName(p, c, name);
        } else {
            rName(p, pc, c, name);
        }
    }

    /** {@code ldebug.c: isenv}：被索引的表是不是 {@code _ENV} */
    static String isEnv(Proto p, int pc, int i, boolean isUp) {
        int t = OpCode.getArgB(i);
        String[] name = new String[1];
        if (isUp) {
            name[0] = upvalName(p, t);
        } else {
            int[] ppc = { pc };
            basicGetObjName(p, ppc, t, name);
        }
        return ("_ENV".equals(name[0])) ? "global" : "field";
    }

    /** {@code ldebug.c: getobjname}：在 {@code basicgetobjname} 之上再认表访问 */
    static String getObjName(Proto p, int lastpc, int reg, String[] name) {
        int[] ppc = { lastpc };
        String kind = basicGetObjName(p, ppc, reg, name);
        if (kind != null) {
            return kind;
        }
        int pc = ppc[0];
        if (pc != -1 && pc < p.code.length) {
            int i = p.code[pc];
            switch (OpCode.getOpCode(i)) {
            case OpCode.OP_GETTABUP:
                kName(p, OpCode.getArgC(i), name);
                return isEnv(p, pc, i, true);
            case OpCode.OP_GETTABLE:
                rName(p, pc, OpCode.getArgC(i), name);
                return isEnv(p, pc, i, false);
            case OpCode.OP_GETI:
                name[0] = "integer index";
                return "field";
            case OpCode.OP_GETFIELD:
                kName(p, OpCode.getArgC(i), name);
                return isEnv(p, pc, i, false);
            case OpCode.OP_SELF:
                rkName(p, pc, i, name);
                return "method";
            default:
                break;
            }
        }
        return null;
    }

    /**
     * {@code luaG_runerror} 的位置前缀：只在<b>当前</b>帧是 Lua 帧时才加。
     * <p>
     * 与 {@link #whereAt} 的差别是刻意的：{@code luaG_runerror} 用的是
     * {@code isLua(ci)} 这一个判断，而 {@code error()} 走的是
     * {@code luaL_where(L, 1)}，会往回跳一层到调用者。混用会让
     * {@code error("x")} 报出行号偏移一格。
     */
    public String where() {
        CallInfo c = ci;
        if (c == null || !c.isLua) {
            return "";
        }
        return lineInfo(c, true);
    }

    /**
     * {@code luaL_where(L, level)}：回跳 {@code level} 个调用帧后的位置。
     * <p>
     * {@code level} 只数调用层数、不跳过 C 帧——这一点与 {@code lua_getstack}
     * 一致：若那一层恰好是 C 函数，就没有行号可给，返回空串。
     */
    public String whereAt(int level) {
        CallInfo c = ci;
        for (int i = 0; i < level && c != null; i++) {
            c = c.previous;
        }
        if (c == null || !c.isLua) {
            return "";
        }
        return lineInfo(c, false);
    }

    /**
     * 取某一帧的 {@code 源名:行号: } 前缀。
     * <p>
     * {@code addInfo} 区分的是 C 里<b>两个不同的函数</b>，它们对「没有调试信息」
     * 的处理完全相反，合成一个必然在一边错：
     * <ul>
     *   <li>{@code luaG_addinfo}：源名为 {@code NULL} 时用 {@code "?"}，行号照印
     *       （剥离调试信息后是 {@code -1}），所以报出来是 {@code ?:-1: msg}。
     *       实测参考实现：{@code load(string.dump(f, true))} 之后
     *       {@code f({})} 报 {@code ?:-1: attempt to perform arithmetic...}，
     *       上游 {@code errors.lua} 断言的 {@code "^%?:%-1:"} 就是这一条。</li>
     *   <li>{@code luaL_where}：{@code ar.currentline > 0} 才给位置，否则空串。
     *       同一个剥离过的函数里 {@code error("x")} 报的是裸的 {@code "x"}。</li>
     * </ul>
     * 只写「源名为 null 就整段不给」会让前者丢掉 {@code ?:-1:}；而退化到「用
     * chunkName 兜底」更糟——它会把那段二进制 chunk 名塞进消息里，那串是非法
     * UTF-8，解码出来就是替换字符。
     */
    private String lineInfo(CallInfo c, boolean addInfo) {
        if (c.func >= stack.length) {
            return "";
        }
        LuaValue f = stack[c.func];
        if (f.tt != Lua.VLIF) {
            return "";
        }
        LClosure cl = (LClosure) f.o;
        int pc = (c.savedpc > 0) ? c.savedpc - 1 : 0;
        int line = cl.p.getFuncline(pc);
        if (!addInfo && line <= 0) {
            return ""; // luaL_where：没有行号就不给位置
        }
        String src = (cl.p.source == null) ? "?"
                : new String(chunkId(cl.p.source.bytes), StandardCharsets.UTF_8);
        return src + ":" + line + ": ";
    }

    /**
     * {@code luaO_chunkid}：把源名整理成给人看的样子。
     * <p>
     * 三种写法，逐条照 C：
     * <ul>
     *   <li>{@code =xxx}：字面源名，原样（过长则截到 {@code LUA_IDSIZE-1}）；</li>
     *   <li>{@code @xxx}：文件名，去掉 {@code @}（过长则在<b>前面</b>加
     *       {@code ...}——保留的是末尾那一段）；</li>
     *   <li>其余：包成 {@code [string "xxx"]}，过长则在<b>后面</b>加
     *       {@code ...}，且只取第一行（遇到 {@code \n} 就停）。</li>
     * </ul>
     * 最后一条最容易被漏：{@code load("x++")} 报的是
     * {@code [string "x++"]:1: syntax error}，不是 {@code x++:1: ...}。
     */
    static String chunkId(String source) {
        return new String(chunkId(source.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8);
    }

    /**
     * 字节版的 {@code chunkId}：源名是 Lua 字符串（字节串），截取是按<b>字节</b>
     * 算的，不能先解码成 {@code String} 再截，否则遇到非法 UTF-8 时长度就对不上了。
     */
    static byte[] chunkId(byte[] source) {
        final int IDSIZE = 60; // LUA_IDSIZE
        final byte[] RETS = { '.', '.', '.' };
        final byte[] PRE = "[string \"".getBytes(StandardCharsets.US_ASCII);
        final byte[] POS = "\"]".getBytes(StandardCharsets.US_ASCII);
        int srclen = source.length;
        /*
         * 空源名<b>不</b>是「原样返回」。C 版读的是 {@code *source}，Lua 字符串
         * 结尾有 {@code '\0'}，所以走的是最后那条 {@code [string "..."]} 分支，
         * 产出 {@code [string ""]}。实测参考实现：
         * {@code load(s, "")} 之后 {@code debug.getinfo(f).short_src} 是
         * {@code [string ""]}（上游 {@code db.lua} 里就钉着这一条）。
         */
        byte c = (srclen > 0) ? source[0] : 0;
        if (c == '=') {
            // 字面源名：去掉 '='，过长则从右边截（保留开头那一段）
            return Arrays.copyOfRange(source, 1, Math.min(srclen, IDSIZE));
        }
        if (c == '@') {
            // 文件名：去掉 '@'，过长则在前头加 '...'（保留末尾那一段）
            if (srclen <= IDSIZE) {
                return Arrays.copyOfRange(source, 1, srclen);
            }
            /*
             * {@code srclen} 是<b>含</b>那个 {@code '@'} 的总长。C 版在这里
             * {@code memcpy(out, source + 1 + srclen - bufflen, bufflen)}——
             * 末位读到的其实是字符串结尾的 {@code '\0'}，所以真正搬过去的内容只有
             * {@code bufflen - 1} 个字节，可见长度是
             * {@code RETS + bufflen - 1 = IDSIZE - 1}。
             * 照抄那个 {@code bufflen} 会在 Java 里越界一字节（Lua 字符串没有
             * 结尾的 {@code '\0'}），而且是<b>只在长于 59 字节的路径上</b>才炸——
             * 本机跑上游测试套件时第一次暴露，因为用例目录的绝对路径够长。
             * 实测参考实现：66 字节的 {@code @路径} 打出来是 59 字节的
             * {@code ...dddddd/.../x.lua}。
             */
            int bufflen = IDSIZE - RETS.length;
            ByteArrayOutputStream out = new ByteArrayOutputStream(IDSIZE);
            out.write(RETS, 0, RETS.length);
            out.write(source, 1 + srclen - bufflen, bufflen - 1);
            return out.toByteArray();
        }
        // 其余：包成 [string "xxx"]，过长则从右边截；只取第一行
        int bufflen = IDSIZE - (PRE.length + RETS.length + POS.length) - 1;
        int nl = srclen;
        for (int i = 0; i < srclen; i++) {
            if (source[i] == '\n') {
                nl = i;
                break;
            }
        }
        boolean whole = (srclen < bufflen && nl == srclen);
        int len = whole ? srclen : Math.min(nl, bufflen);
        ByteArrayOutputStream out = new ByteArrayOutputStream(IDSIZE);
        out.write(PRE, 0, PRE.length);
        out.write(source, 0, len);
        if (!whole) {
            out.write(RETS, 0, RETS.length);
        }
        out.write(POS, 0, POS.length);
        return out.toByteArray();
    }
}
