package com.github.esrrhs.fakescript.lua;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 短字符串驻留池 —— 对应 {@code lstring.c} 的 {@code strt}（string table）。
 * <p>
 * 驻留是 Lua 的性能基石：短字符串全局唯一，相等比较可以退化成引用比较，
 * 表查找也能省掉逐字节比对。语义上不可观测，但少了它 {@code t.x = t.x} 这类
 * 代码会慢一个量级。
 * <p>
 * <b>为什么要弱引用。</b>C 版里驻留表是由 {@code lgc.c} 显式管理的：GC 周期中
 * 会清掉没人引用的短字符串（{@code iscleared} 那套逻辑）。这里没有自己的 GC，
 * 如果驻留池用强引用，脚本里出现过的每一个短字符串都会永久驻留——跑数据处理
 * 脚本时这会实打实地泄漏。所以池用 {@link WeakHashMap} + {@link WeakReference}：
 * 键弱、值也弱，脚本不再引用某个字符串时它自然被 JVM 回收。
 * <p>
 * 注意不能用 {@code WeakHashMap<LuaString, LuaString>}：值就是键本身的话，
 * 值对键形成强引用，条目永远不会被清掉，池就退化成泄漏源。
 */
public final class StringPool {

    private final Map<LuaString, WeakReference<LuaString>> shortStrings = new WeakHashMap<>();

    /**
     * 返回与 {@code s} 内容相同的规范实例。
     * <p>
     * 长字符串不驻留（与上游一致：{@code LUAI_MAXSHORTLEN} 以上的字符串每次都新建）。
     */
    public LuaString intern(LuaString s) {
        if (!s.isShort()) {
            return s;
        }
        synchronized (this) {
            WeakReference<LuaString> ref = shortStrings.get(s);
            LuaString canonical = (ref == null) ? null : ref.get();
            if (canonical != null) {
                return canonical;
            }
            shortStrings.put(s, new WeakReference<>(s));
            return s;
        }
    }

    /** 池中条目数（含尚未被清理的失效条目），仅用于测试与诊断 */
    public synchronized int size() {
        return shortStrings.size();
    }
}
