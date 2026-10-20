package com.github.esrrhs.javalua;

/**
 * C 侧 {@code lua_newuserdata} 给的那块内存。
 * <p>
 * C 扩展拿到 {@code lua_touserdata} 的返回值是要<b>直接读写</b>的（结构体、数
 * 组、字符串缓冲都在里面），所以这块内存不能放在 Java 堆上——必须是真的 native
 * 内存，地址是能交给 C 的那种。
 * <p>
 * 释放挂到 {@link NativeBridge#freeWith} 上：{@code NativeMemory} 对象被 GC 回
 * 收时，Cleaner 自动调 {@code free(ptr)}。{@code lua_newuserdatauv} 创建的
 * {@code NativeMemory} 活在 {@link LuaUserdata#data} 里，跟着 userdata 一起生
 * 死——userdata 没了，{@code NativeMemory} 也没强引用了，native 内存就释放。
 */
public final class NativeMemory {

    /** native 地址，等于 C 侧拿到的 {@code void*} */
    public final long ptr;

    /** 字节数，{@code lua_rawlen} 用的是它 */
    public final long size;

    NativeMemory(long ptr, long size) {
        this.ptr = ptr;
        this.size = size;
        if (ptr != 0) {
            NativeBridge.freeWith(this, ptr);
        }
    }

    public byte[] read(long off, int n) {
        if (ptr == 0 || n <= 0) {
            return new byte[0];
        }
        return NativeBridge.bytesAtN(ptr + off, n);
    }

    @Override
    public String toString() {
        return "native memory: " + Long.toHexString(ptr);
    }
}
