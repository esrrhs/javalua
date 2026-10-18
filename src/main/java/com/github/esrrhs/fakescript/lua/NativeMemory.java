package com.github.esrrhs.fakescript.lua;

/**
 * C 侧 {@code lua_newuserdata} 给的那块内存。
 * <p>
 * C 扩展拿到 {@code lua_touserdata} 的返回值是要<b>直接读写</b>的（结构体、数
 * 组、字符串缓冲都在里面），所以这块内存不能放在 Java 堆上——必须是真的 native
 * 内存，地址是能交给 C 的那种。
 * <p>
 * TODO：释放还没接上。当前只分配不回收，长时间反复创建 userdata 的程序会漏。
 * 接法是把释放挂到 {@link LuaUserdata} 的终结路径上（与 __gc 那条同一个口子），
 * 等 C4 阶段一起做——先保证地址是对的、读写是对的。
 */
public final class NativeMemory {

    /** native 地址，等于 C 侧拿到的 {@code void*} */
    public final long ptr;

    /** 字节数，{@code lua_rawlen} 用的是它 */
    public final long size;

    NativeMemory(long ptr, long size) {
        this.ptr = ptr;
        this.size = size;
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
