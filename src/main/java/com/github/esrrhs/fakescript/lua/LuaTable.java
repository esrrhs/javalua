package com.github.esrrhs.fakescript.lua;

import java.util.ArrayList;
import java.util.List;

/**
 * Lua 表 —— 对应 {@code ltable.c} 的 {@code Table}。
 * <p>
 * 与 C 版一样分成两段：
 * <ul>
 *   <li><b>数组部分</b> {@code array}：密集的正整数键，下标 {@code k} 落在
 *       {@code array[k-1]}。这是 {@code for i = 1, #t} 能跑快的原因，也是
 *       {@code #t} 能二分查找边界的原因。</li>
 *   <li><b>哈希部分</b> {@code node}：其余所有键，链地址法，容量取 2 的幂。</li>
 * </ul>
 * <p>
 * 键的相等性用 Lua 的 {@code rawequal} 而不是 Java 的 {@code equals}：
 * {@code 1} 与 {@code 1.0} 是同一个键（Lua 里 {@code 1 == 1.0} 为真），
 * 但 {@code 1} 与 {@code "1"} 不是（整数键与字符串键是不同的项）。
 * <p>
 * 一处刻意的简化：C 版 5.4 用「假 alimit」把数组部分的实际边界藏在一个标志位里
 * （{@code isfakealimit}），以省掉部分二分查找。这里直接用精确的
 * {@code alimit == array.length}，语义等价、代码好读，代价是 {@code #t}
 * 偶尔多一次二分。
 */
public final class LuaTable {

    private static final LuaValue[] EMPTY_ARRAY = new LuaValue[0];

    /** 数组部分。下标 k（1 起）对应 array[k-1]，槽永不为空（nil 用 isNil() 表示） */
    LuaValue[] array = EMPTY_ARRAY;

    /** 哈希部分，长度为 2 的幂；为 null 表示为空（对应 C 的 dummy node） */
    Node[] node = null;

    /** 哈希部分长度 */
    int nodeSize = 0;

    /** 下一个待探测的空闲位置，从 {@link #nodeSize} 往下走（对应 {@code lastfree}） */
    int lastFree = 0;

    /** 元表 */
    LuaTable metatable;

    /** 哈希节点：键 + 值 + 同链下一跳（下标，-1 表示链尾） */
    static final class Node {
        final LuaValue key = new LuaValue();
        final LuaValue val = new LuaValue();
        int next = -1;
    }

    public LuaTable() {
    }

    /** 预分配两段。仅供需要预置大小的场合（如构造 registry） */
    public LuaTable(int arraySize, int hashSize) {
        if (arraySize > 0) {
            setArrayVector(arraySize);
        }
        if (hashSize > 0) {
            setNodeVector(hashSize);
        }
    }

    /* ==================== 哈希与主位置 ==================== */

    /** {@code mainposition}：键的主桶下标 */
    private int mainPosition(LuaValue key) {
        switch (key.tt) {
        case Lua.VNUMINT:
            return (int) (key.i & 0x7FFFFFFF) & (nodeSize - 1);
        case Lua.VNUMFLT:
            return hashFloat(key.n) & (nodeSize - 1);
        case Lua.VSHRSTR:
        case Lua.VLNGSTR:
            return key.asString().hash() & (nodeSize - 1);
        case Lua.TBOOLEAN:
            return (int) key.i & (nodeSize - 1);
        default:
            return System.identityHashCode(key.o) & (nodeSize - 1);
        }
    }

    /**
     * {@code ltable.c: l_hashfloat}：把浮点拆成尾数×2^指数再映射到整数。
     * <p>
     * {@code -0.0} 与 {@code 0.0} 必须哈希到同一处——Lua 里二者相等
     * （{@code luai_numeq}），因此必须是同一个键。
     */
    private static int hashFloat(double n) {
        if (Double.isNaN(n) || Double.isInfinite(n)) {
            return 0;
        }
        if (n == 0.0) {
            return 0; // 归一化 -0.0
        }
        int e = Math.getExponent(n) + 1;
        double m = Math.scalb(n, -e); // 0.5 <= |m| < 1
        long ni = (long) (m * (Integer.MAX_VALUE - Double.MAX_EXPONENT));
        ni += e;
        if (ni < 0) {
            ni = -ni;
        }
        return (int) (ni & 0x7FFFFFFF);
    }

    /* ==================== 取 ==================== */

    /**
     * {@code luaH_get}：返回值的槽；键不存在时返回 {@code null}（调用方按 nil 处理）。
     * <p>
     * 返回的是<b>活的槽</b>而不是副本：调用方可以直接往里写。这与 C 版返回
     * {@code TValue*} 一致，也是「数组部分优先于哈希部分」这个优化能成立的前提。
     */
    public LuaValue get(LuaValue key) {
        if (key.tt == Lua.VNUMINT) {
            return getInt(key.i);
        }
        if (key.tt == Lua.VNUMFLT) {
            // 整值浮点与整数是同一个键：Lua 5.4.7 实测 t[1.0] 与 t[1] 同一项
            long[] k = new long[1];
            if (floatToIntExact(key.n, k)) {
                return getInt(k[0]);
            }
        }
        return getFromHash(key);
    }

    /** 整数键查找：先数组部分，再哈希部分 */
    private LuaValue getInt(long k) {
        if (k > 0 && k <= array.length) {
            return array[(int) k - 1];
        }
        return getFromHash(LuaValue.ofInteger(k));
    }

    /**
     * {@code luaV_flttointns} 的 F2Ieq 模式：浮点只有当<b>精确</b>等于某个整数
     * 时才转换。
     * <p>
     * 这条规则直接决定键的规范化，已用 Lua 5.4.7 实测确认：
     * <pre>
     *   t[1.0]  = "a"  →  next(t) 的键 math.type 是 "integer"，#t 是 1
     *   t[0.0]  = "a"  →  键是整数 0（但 0 不是数组下标，故 #t 仍是 0）
     *   t[1.5]  = "a"  →  键是浮点
     *   t[2^63] = "a"  →  键是浮点（超出 integer 可表示范围）
     * </pre>
     */
    private static boolean floatToIntExact(double n, long[] out) {
        if (Double.isNaN(n) || Double.isInfinite(n)) {
            return false;
        }
        if (n >= Long.MIN_VALUE && n < 9223372036854775808.0) {
            long k = (long) n;
            if ((double) k == n) { // 必须精确往返，否则 1.5 会被错误地当成 1
                out[0] = k;
                return true;
            }
        }
        return false;
    }

    /**
     * 写入前的键规范化：整值浮点转成整数键，nil 与 NaN 直接报错。
     * <p>
     * 放在这里而不是各调用点，是因为 {@code ltable.c} 里这两个检查就在
     * {@code luaH_newkey} 的开头——所有写路径（VM 的 {@code OP_SET*}、
     * {@code rawset}、宿主 API）最终都经过它，分散检查必漏一处。
     * <p>
     * NaN 的处理是 Lua 5.4.7 实测的：<b>读</b> {@code t[0/0]} 返回 nil 不报错，
     * <b>写</b> {@code t[0/0] = 1} 报 "table index is NaN"。所以这里只在
     * 写路径上拒绝。
     * <p>
     * 抛的是不带位置的裸错误值：{@code luaG_runerror} 的位置前缀由
     * {@link LuaState#pcall} 在保护边界补上，与 C 版 {@code luaG_addinfo}
     * 的时机一致。
     */
    private static LuaValue normalizeKeyForWrite(LuaValue key) {
        if (key.isNil()) {
            throw new LuaError(LuaValue.ofString(LuaString.latin1("table index is nil")));
        }
        if (key.tt == Lua.VNUMFLT) {
            if (Double.isNaN(key.n)) {
                throw new LuaError(LuaValue.ofString(LuaString.latin1("table index is NaN")));
            }
            long[] out = new long[1];
            if (floatToIntExact(key.n, out)) {
                return LuaValue.ofInteger(out[0]);
            }
        }
        return key;
    }

    private LuaValue getFromHash(LuaValue key) {
        if (nodeSize == 0) {
            return null;
        }
        Node n = node[mainPosition(key)];
        while (true) {
            if (LuaValue.rawEqual(n.key, key)) {
                return n.val;
            }
            if (n.next < 0) {
                return null;
            }
            n = node[n.next];
        }
    }

    public LuaValue get(long k) {
        return get(LuaValue.ofInteger(k));
    }

    public LuaValue get(LuaString k) {
        return get(LuaValue.ofString(k));
    }

    public LuaValue get(String k) {
        return get(LuaString.latin1(k));
    }

    /** 键是否存在且值非 nil */
    public boolean contains(LuaValue key) {
        LuaValue v = get(key);
        return v != null && !v.isNil();
    }

    /* ==================== 存 ==================== */

    public void set(LuaValue key, LuaValue value) {
        ensureSlot(key).set(value);
    }

    public void set(long k, LuaValue value) {
        set(LuaValue.ofInteger(k), value);
    }

    public void set(LuaString k, LuaValue value) {
        set(LuaValue.ofString(k), value);
    }

    public void set(String k, LuaValue value) {
        set(LuaString.latin1(k), value);
    }

    /**
     * {@code luaH_set}：拿到键对应的可写槽，键不存在就先建一个值为 nil 的条目。
     * <p>
     * <b>为什么不能一步到位。</b>rehash 有可能把这个键直接安排进数组部分
     * （例如空表上 {@code t[1]=v}：rehash 算出数组部分该是 1，于是槽在
     * {@code array[0]}，而它是 {@code LuaValue} 不是 {@code Node}）。所以
     * rehash 之后必须重新查一次，不能假设结果还在哈希部分——这正是 C 版在
     * rehash 后重新调用 {@code luaH_set} 的原因，只是那里靠指针统一了两种槽。
     */
    private LuaValue ensureSlot(LuaValue rawKey) {
        LuaValue key = normalizeKeyForWrite(rawKey);
        LuaValue slot = get(key);
        if (slot != null) {
            return slot;
        }
        while (true) {
            if (nodeSize == 0) {
                // 还没有哈希部分：交给 rehash 决定两段大小
                rehash(key);
            } else {
                Node n = newKey(key, false);
                if (n != null) {
                    return n.val;
                }
                rehash(key);
            }
            slot = get(key);
            if (slot != null) {
                return slot;
            }
        }
    }

    /**
     * {@code luaH_newkey}：为表中不存在的键分配条目。
     * <p>
     * 返回 {@code null} 表示「需要 rehash」，由调用方 rehash 后重试。之所以把这个
     * 决定交回调用方，是因为 {@link #resize} 搬运旧数据时必须保证不再触发
     * 嵌套 rehash（否则搬一半的表会被再次重建）。
     */
    private Node newKey(LuaValue key, boolean allowRehash) {
        if (nodeSize == 0) {
            return null;
        }
        int mpIdx = mainPosition(key);
        Node mp = node[mpIdx];
        if (!mp.val.isNil()) {
            int f = getFreePos();
            if (f < 0) {
                return null; // 没有空位
            }
            int otherIdx = mainPosition(mp.key);
            if (otherIdx != mpIdx) {
                // mp 不在它自己的主位置：把它挪到空闲位 f，腾出 mp 给新键
                int p = otherIdx;
                while (node[p].next != mpIdx) {
                    p = node[p].next;
                }
                node[p].next = f;
                copyNode(mp, node[f]);
                mp.next = -1;
                mp.val.setNil();
            } else {
                // mp 就在自己的主位置：新节点挂到 f，链在 mp 之后
                node[f].next = mp.next;
                mp.next = f;
                mpIdx = f;
                mp = node[f];
            }
        }
        mp.key.set(key);
        mp.val.setNil();
        return mp;
    }

    private static void copyNode(Node from, Node to) {
        to.key.set(from.key);
        to.val.set(from.val);
        to.next = from.next;
    }

    /** {@code getfreepos}：从 lastFree 往下找第一个键为 nil 的位置 */
    private int getFreePos() {
        while (lastFree > 0) {
            lastFree--;
            if (node[lastFree].key.isNil()) {
                return lastFree;
            }
        }
        return -1;
    }

    /* ==================== rehash ==================== */

    /**
     * {@code ltable.c: rehash}：插入新键却找不到空位时，重新分配两段的大小。
     * <p>
     * 核心是 {@code computesizes}：按 2 的幂逐档统计整数键，只要某一档里
     * 「已用数量超过该档容量的一半」，就把数组部分扩到那一档。这就是
     * 「先 {@code t[1]=…} 再 {@code t[2]=…} 会让数组部分从 0 长到 1 再长到 2」
     * 的原因，也是为什么乱序赋值（先 {@code t[1000]=1}）不会撑出一百万长的数组。
     */
    private void rehash(LuaValue extraKey) {
        int[] nums = new int[Lua.MAXABITS + 1];
        int na = numUseArray(nums);
        int totalUse = na;
        int[] hashUse = { 0 };
        totalUse += numUseHash(nums, hashUse);
        na += hashUse[0];

        // 计入本次要插入的那个键
        if (extraKey.tt == Lua.VNUMINT) {
            na += countInt(extraKey.i, nums);
        }
        totalUse++;

        int[] outNa = { na };
        int newArraySize = computeSizes(nums, outNa);
        resize(newArraySize, Math.max(totalUse - outNa[0], 0));
    }

    /** {@code numusearray}：统计数组部分里非 nil 的元素，按 2 的幂分档 */
    private int numUseArray(int[] nums) {
        int ause = 0;
        int i = 1;
        int ttlg = 1;
        for (int lg = 0; lg <= Lua.MAXABITS; lg++, ttlg *= 2) {
            int lim = Math.min(ttlg, array.length);
            int lc = 0;
            for (; i <= lim; i++) {
                if (!array[i - 1].isNil()) {
                    lc++;
                }
            }
            nums[lg] = lc;
            ause += lc;
            if (i > array.length) {
                break;
            }
        }
        return ause;
    }

    /** {@code numusehash}：遍历哈希部分，统计总数与「可以放进数组部分」的整数键数 */
    private int numUseHash(int[] nums, int[] pna) {
        int totalUse = 0;
        int ause = 0;
        for (int i = 0; i < nodeSize; i++) {
            Node n = node[i];
            if (!n.val.isNil()) {
                if (n.key.tt == Lua.VNUMINT) {
                    ause += countInt(n.key.i, nums);
                }
                totalUse++;
            }
        }
        pna[0] += ause;
        return totalUse;
    }

    /** {@code countint}：整数键落在数组范围内就计入分档 */
    private static int countInt(long key, int[] nums) {
        int k = arrayIndex(key);
        if (k != 0) {
            nums[Lua.ceilLog2(k)]++;
            return 1;
        }
        return 0;
    }

    private static int arrayIndex(long k) {
        return (k > 0 && k <= Lua.MAXASIZE) ? (int) k : 0;
    }

    /** {@code computesizes}：算出数组部分的「最优」大小 */
    private static int computeSizes(int[] nums, int[] pna) {
        int optimal = 0;
        int a = 0;
        int na = 0;
        for (int i = 0, twotoi = 1; twotoi > 0 && pna[0] > twotoi / 2; i++, twotoi *= 2) {
            if (nums[i] > 0) {
                a += nums[i];
                if (a > twotoi / 2) {
                    optimal = twotoi;
                    na = a;
                }
            }
        }
        pna[0] = na;
        return optimal;
    }

    /**
     * {@code luaH_resize}：重建两段，把旧内容搬过去。
     * <p>
     * 搬的顺序有讲究：先把「数组部分被砍掉的那一段」和「旧哈希部分」的所有键值
     * 收集起来，等新空间就绪后再逐个插入。不能边搬边写目标表——插入过程中若再
     * 触发 rehash，还没搬的数据就丢了。因此这里用 {@code newKey(key, false)}：
     * 空间不足视为实现错误，而不是静默 rehash。
     */
    private void resize(int newArraySize, int newHashSize) {
        LuaValue[] oldArray = array;
        Node[] oldNodes = node;
        int oldNodeSize = nodeSize;

        List<LuaValue[]> moved = new ArrayList<>();

        // 数组部分收缩：被切掉的那一段要搬进哈希部分
        for (int j = newArraySize; j < oldArray.length; j++) {
            if (!oldArray[j].isNil()) {
                moved.add(new LuaValue[] { LuaValue.ofInteger(j + 1), oldArray[j] });
            }
        }

        // 新数组部分，保留前缀
        setArrayVector(newArraySize);
        int keep = Math.min(newArraySize, oldArray.length);
        for (int j = 0; j < keep; j++) {
            array[j].set(oldArray[j]);
        }

        // 旧哈希部分的存活条目
        if (oldNodes != null) {
            for (int i = 0; i < oldNodeSize; i++) {
                if (!oldNodes[i].val.isNil()) {
                    moved.add(new LuaValue[] { oldNodes[i].key, oldNodes[i].val });
                }
            }
        }

        // 新哈希部分。moved 非空时必须至少有一个槽，否则下面的搬运会失败
        int hashSize = newHashSize;
        if (moved.size() > hashSize) {
            hashSize = moved.size();
        }
        setNodeVector(hashSize);

        for (LuaValue[] kv : moved) {
            insertNoRehash(kv[0], kv[1]);
        }
    }

    /**
     * 搬运旧数据时插入一项，<b>不允许</b>再触发 rehash。
     * <p>
     * 与 {@link #set} 的差别不只是「不许 rehash」：这里必须走 {@link #get}
     * 的路由，让整数键有机会落进新扩出来的数组部分。若图省事直接塞进哈希，
     * 就会破坏「所有 {@code 1..alimit} 的整数键都在数组部分」这条不变式——
     * 那样 {@code get} 会在数组槽上拿到 nil 就返回，哈希里那份数据再也读不到。
     */
    private void insertNoRehash(LuaValue key, LuaValue value) {
        LuaValue slot = get(key);
        if (slot != null) {
            slot.set(value);
            return;
        }
        Node n = newKey(key, false);
        if (n == null) {
            throw new IllegalStateException("hash part too small after resize");
        }
        n.val.set(value);
    }

    private void setArrayVector(int size) {
        LuaValue[] a = new LuaValue[size];
        for (int i = 0; i < size; i++) {
            a[i] = new LuaValue();
        }
        array = a;
    }

    private void setNodeVector(int size) {
        if (size <= 0) {
            node = null;
            nodeSize = 0;
            lastFree = 0;
            return;
        }
        // 向上取整到 2 的幂
        int n = 1;
        while (n < size) {
            n <<= 1;
        }
        Node[] nodes = new Node[n];
        for (int i = 0; i < n; i++) {
            nodes[i] = new Node();
        }
        node = nodes;
        nodeSize = n;
        lastFree = n;
    }

    /**
     * 直接写数组部分的第 {@code k} 项（1 起），必要时只把数组部分扩到 {@code k}。
     * <p>
     * 对应 {@code OP_SETLIST} 的 {@code luaH_resizearray + 直接写}。与
     * {@link #set} 的差别是<b>不走 rehash</b>：表构造的路径已经从字节码里知道
     * 该分配多大，再走一次 rehash 纯属浪费，而且会把刚攒起来的字段搅乱。
     * <p>
     * 不搬哈希部分里落在 {@code [1, k]} 的整数键——C 版也是这样，两边行为一致
     * （数组部分优先于哈希部分，读的时候只会看到数组里的那份）。
     */
    public void setArrayRaw(long k, LuaValue value) {
        if (k <= 0) {
            set(LuaValue.ofInteger(k), value);
            return;
        }
        if (k > array.length) {
            int n = array.length;
            if (n == 0) {
                n = 1;
            }
            while (n < k) {
                n *= 2;
            }
            LuaValue[] bigger = new LuaValue[n];
            System.arraycopy(array, 0, bigger, 0, array.length);
            for (int i = array.length; i < n; i++) {
                bigger[i] = new LuaValue();
            }
            array = bigger;
        }
        array[(int) k - 1].set(value);
    }

    /* ==================== 长度（#t）==================== */

    /**
     * {@code luaH_getn}：返回一个「边界」——{@code t[n]} 非 nil 且 {@code t[n+1]} 为 nil。
     * <p>
     * Lua 只要求返回<b>某个</b>边界，不指定是哪一个，所以这里的二分查找只要落在
     * 合法边界上就算正确。数组部分满且哈希部分非空时，边界可能藏在哈希里，
     * 走 {@code unbound_search}：先指数级试探再二分。
     */
    public long length() {
        int j = array.length;
        if (j > 0 && array[j - 1].isNil()) {
            // 数组部分末尾为空 → 边界在数组内，二分
            int i = 0;
            while (j - i > 1) {
                int m = (i + j) / 2;
                if (array[m - 1].isNil()) {
                    j = m;
                } else {
                    i = m;
                }
            }
            return i;
        }
        if (nodeSize == 0) {
            return j;
        }
        return unboundSearch(j);
    }

    /** {@code unbound_search}：边界在哈希部分，先翻倍试探上界再二分 */
    private long unboundSearch(long j) {
        long i = j;
        j++;
        while (true) {
            LuaValue v = get(LuaValue.ofInteger(j));
            if (v == null || v.isNil()) {
                break;
            }
            i = j;
            if (j > Integer.MAX_VALUE / 2) {
                return i; // 表过大，无法继续翻倍（C 版在此报 "table too big"）
            }
            j *= 2;
        }
        while (j - i > 1) {
            long m = (i + j) / 2;
            LuaValue v = get(LuaValue.ofInteger(m));
            if (v == null || v.isNil()) {
                j = m;
            } else {
                i = m;
            }
        }
        return i;
    }

    /* ==================== 遍历（pairs/next）==================== */

    /**
     * {@code luaH_next}：返回 {@code key} 之后的下一项，形如 {@code [key, value]}；
     * 没有下一项时返回 {@code null}。传入 nil 表示从头开始。
     * <p>
     * 先数组部分后哈希部分，与 C 版一致。顺序本身在 Lua 里是<b>未定义</b>的，
     * 这里不保证任何顺序，只保证每项不重不漏。
     */
    public LuaValue[] next(LuaValue key) {
        int start;
        if (key == null || key.isNil()) {
            start = 0;
        } else if (key.tt == Lua.VNUMINT) {
            long k = key.i;
            if (k > 0 && k <= array.length) {
                start = (int) k; // 下一个数组槽的 0 起下标
            } else {
                int ni = findNodeIndex(key);
                if (ni < 0) {
                    throw new IllegalArgumentException("invalid key to 'next'");
                }
                start = array.length + ni + 1;
            }
        } else {
            int ni = findNodeIndex(key);
            if (ni < 0) {
                throw new IllegalArgumentException("invalid key to 'next'");
            }
            start = array.length + ni + 1;
        }

        for (int i = Math.max(start, 0); i < array.length; i++) {
            if (!array[i].isNil()) {
                return new LuaValue[] { LuaValue.ofInteger(i + 1), array[i] };
            }
        }
        if (nodeSize > 0) {
            for (int j = Math.max(start - array.length, 0); j < nodeSize; j++) {
                if (!node[j].val.isNil()) {
                    return new LuaValue[] { node[j].key, node[j].val };
                }
            }
        }
        return null;
    }

    /** 找到键在哈希部分的下标，找不到返回 -1 */
    private int findNodeIndex(LuaValue key) {
        if (nodeSize == 0) {
            return -1;
        }
        int i = mainPosition(key);
        while (true) {
            if (LuaValue.rawEqual(node[i].key, key)) {
                return i;
            }
            if (node[i].next < 0) {
                return -1;
            }
            i = node[i].next;
        }
    }

    /* ==================== 元表 ==================== */

    public LuaTable getMetatable() {
        return metatable;
    }

    public void setMetatable(LuaTable mt) {
        metatable = mt;
    }

    /* ==================== 诊断 ==================== */

    /** 表中非 nil 项的个数（遍历统计，仅供测试与诊断，不是 O(1)） */
    public int count() {
        int n = 0;
        for (LuaValue v : array) {
            if (!v.isNil()) {
                n++;
            }
        }
        if (nodeSize > 0) {
            for (int i = 0; i < nodeSize; i++) {
                if (!node[i].val.isNil()) {
                    n++;
                }
            }
        }
        return n;
    }

    /** 数组部分长度，供测试观察 rehash 行为 */
    public int arrayPartSize() {
        return array.length;
    }

    /** 哈希部分长度，供测试观察 rehash 行为 */
    public int hashPartSize() {
        return nodeSize;
    }
}
