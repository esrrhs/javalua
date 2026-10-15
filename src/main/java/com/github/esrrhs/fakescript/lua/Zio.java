package com.github.esrrhs.fakescript.lua;

/**
 * 输入流 —— 对应 {@code lzio.c} / {@code lzio.h} 的 {@code Zio}。
 * <p>
 * C 版为了支持任意来源（文件、字符串、自定义 reader）做了一层带缓冲的 reader
 * 抽象。这里保留同样的形状（{@code next()} 一次给一个字节，读完给 EOZ），
 * 但底层先用 {@code byte[]} 承载：当前所有入口（{@code load}、{@code dofile}）
 * 拿到的都是完整字节数组。将来要接流式 reader 时替换底层即可，词法层不用动。
 * <p>
 * <b>注意字节是无符号的。</b>{@code byte} 在 Java 里有符号，取出来必须
 * {@code & 0xFF}，否则含高位字节的 UTF-8 会变成负数，与 EOZ 的判断混在一起。
 */
public final class Zio {

    /** 流结束，对应 {@code EOZ} */
    public static final int EOZ = -1;

    private final byte[] data;
    private int p;

    public Zio(byte[] data) {
        this.data = data;
        this.p = 0;
    }

    /** {@code zgetc}：取下一个字节（0..255），读完返回 {@link #EOZ} */
    public int getc() {
        if (p >= data.length) {
            return EOZ;
        }
        return data[p++] & 0xFF;
    }
}
