package com.github.esrrhs.fakescript.lua;

/**
 * 输入流 —— 对应 {@code lzio.c} / {@code lzio.h} 的 {@code Zio}。
 * <p>
 * C 版为了支持任意来源（文件、字符串、自定义 reader）做了一层带缓冲的 reader
 * 抽象。这里保留同样的形状（{@code getc()} 一次给一个字节，读完给 EOZ），
 * 底层有两种承载：
 * <ul>
 *   <li><b>一次性给全</b>：{@code load} 一个字符串、{@code loadfile} 一个文件，
 *       拿到的都是完整字节数组；</li>
 *   <li><b>惰性</b>：{@code load} 一个读函数。这一种是<b>必需的，不是优化</b>——
 *       读函数是<b>边解析边调</b>的，C 的 {@code luaZ_fill} 只在当前那批耗尽
 *       时才去要下一批。改成「先一股脑读完再解析」，遇到永不返回 nil 的读
 *       函数就会一直累积下去（实测直接把堆撑爆），而 C 那边早就因为语法错误
 *       或读到一个完整 chunk 停了。</li>
 * </ul>
 * <p>
 * <b>注意字节是无符号的。</b>{@code byte} 在 Java 里有符号，取出来必须
 * {@code & 0xFF}，否则含高位字节的 UTF-8 会变成负数，与 EOZ 的判断混在一起。
 */
public final class Zio {

    /** 流结束，对应 {@code EOZ} */
    public static final int EOZ = -1;

    /**
     * 惰性的字节来源。
     * <p>
     * 返回 {@code null} 或长度为 0 都表示流结束——C 判定的是
     * {@code size == 0}（{@code luaZ_fill}），空串与 nil 是一回事。
     */
    public interface Source {
        byte[] read();
    }

    /** 惰性来源；一次性给全时为 {@code null} */
    private final Source src;

    /** 当前这批字节 */
    private byte[] buf;

    private int p;

    public Zio(byte[] data) {
        this.src = null;
        this.buf = data;
        this.p = 0;
    }

    public Zio(Source src) {
        this.src = src;
        this.buf = null;
        this.p = 0;
    }

    /** {@code zgetc}：取下一个字节（0..255），读完返回 {@link #EOZ} */
    public int getc() {
        for (;;) {
            if (buf != null && p < buf.length) {
                return buf[p++] & 0xFF;
            }
            if (src == null) {
                return EOZ;
            }
            byte[] n = src.read();
            if (n == null || n.length == 0) {
                buf = null;
                return EOZ;
            }
            buf = n;
            p = 0;
        }
    }
}
