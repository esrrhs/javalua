package com.github.esrrhs.fakescript.lua;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * io 库 —— 对应 {@code liolib.c}。
 * <p>
 * 文件句柄是<b>带命名元表的 userdata</b>（元表名 {@code FILEHANDLE*}），这是
 * 本库对内核提出的第一个硬要求：{@code luaL_testudata} 判的是「这个 userdata
 * 的元表是不是注册表里那一张」，所以 userdata 的元表必须挂在对象上而不是类型上。
 * 之前的实现恰好只有类型元表，为此在阶段 7 补了 {@link LuaUserdata}。
 * <p>
 * 几处与 C 不同但刻意如此的地方（都记进了 README）：
 * <ol>
 *   <li><b>写不缓冲</b>。C 的 {@code stdout} 定向到管道时是块缓冲，
 *       {@code io.write} 的输出要等 4KB 或进程退出才落地；这里每次写完立刻 flush。
 *       同一条流上的<b>顺序</b>不受影响，只有与子进程输出、{@code stderr}
 *       交叉时才会看出差别。</li>
 *   <li><b>{@code f_tostring}</b> 里的 {@code %p} 是地址，不可能与参考实现相同，
 *       只能给个同形状的十六进制值（差分比对会把它抹成 {@code 0xADDR}）。</li>
 *   <li><b>{@code setvbuf}</b> 只认参数、总是成功，不真的改缓冲策略。</li>
 *   <li><b>{@code popen}</b> 的子进程输出走管道而不是 {@code FILE*}
 *       （{@code pclose} 起来失败时拿不到 shell 的真实 errno）。被接管的
 *       那一条（读模式是子进程的 {@code stdout}、写模式是它的 {@code stdin}）
 *       语义与 C 一致；<b>没被接管的那两条是转发到 {@code L.out} / {@code L.err}
 *       的</b>，不是 {@code Redirect.INHERIT} —— 后者走 JVM 进程的 fd 1 / fd 2，
 *       会绕过这两个流。代价是「转发线程什么时候写完」有个很小的时间窗，
 *       靠 {@code pclose} 里 {@code join} 收口；那一刀<b>没有用例盯得住</b>
 *       （窗口太小，破坏它矩阵照样全绿），它防的是偶发红，不是语义。</li>
 * </ol>
 */
public final class IoLib {

    private IoLib() {
    }

    /** {@code LUA_FILEHANDLE} */
    static final String FILE_HANDLE = "FILE*";

    /** {@code IO_INPUT} / {@code IO_OUTPUT}：注册表里那两个「默认文件」的键 */
    private static final String IO_INPUT = "_IO_input";
    private static final String IO_OUTPUT = "_IO_output";

    /** {@code MAXARGLINE}：{@code f:lines} 最多几个读格式 */
    private static final int MAXARGLINE = 250;

    /** {@code L_MAXLENNUM}：读数字时最多攒几个字符 */
    private static final int L_MAXLENNUM = 200;

    /** {@code LUAL_BUFFERSIZE}，只用于 {@code setvbuf} 的默认参数 */
    private static final int LUAL_BUFFERSIZE = 8192;

    /** {@code read_chars} 分块读用的块大小 */
    private static final int CHUNK = 8192;

    /**
     * {@code read_chars} 放弃分配的上限。
     * <p>
     * 真值是平台相关的：macOS 上参考实现二分出来在 {@code 2^46} 附近断
     * （{@code 70368740245501} 还能成、{@code 70368740507645} 就不行），
     * Linux 有过量提交、边界又不一样。所以这个数<b>不进差分用例</b>，
     * 它只管「明显大到谁都分配不出来」的那一档；唯一确定性的输入是
     * 负数（C 里转 {@code size_t} 之后必然是天文数字）。
     */
    private static final long MAX_READ_ALLOC = 1L << 46;

    /* ==================== 注册 ==================== */

    public static void open(LuaState L) {
        LuaTable io = new LuaTable();
        LuaAux.setFn(io, "close", IoLib::ioClose);
        LuaAux.setFn(io, "flush", IoLib::ioFlush);
        LuaAux.setFn(io, "input", IoLib::ioInput);
        LuaAux.setFn(io, "lines", IoLib::ioLines);
        LuaAux.setFn(io, "open", IoLib::ioOpen);
        LuaAux.setFn(io, "output", IoLib::ioOutput);
        LuaAux.setFn(io, "popen", IoLib::ioPopen);
        LuaAux.setFn(io, "read", IoLib::ioRead);
        LuaAux.setFn(io, "tmpfile", IoLib::ioTmpfile);
        LuaAux.setFn(io, "type", IoLib::ioType);
        LuaAux.setFn(io, "write", IoLib::ioWrite);
        LuaAux.registerLib(L, "io", io);

        createMeta(L);

        // 三个标准流。注意它们的 closef 是 io_noclose：关不掉，关了要给
        // (nil, "cannot close standard file")
        createStdFile(L, io, stdFile(L, true, false, false), IO_INPUT, "stdin");
        createStdFile(L, io, stdFile(L, false, true, false), IO_OUTPUT, "stdout");
        createStdFile(L, io, stdFile(L, false, true, true), null, "stderr");
    }

    /**
     * {@code createmeta}：建 {@code FILEHANDLE*} 元表，并把方法表挂到
     * {@code __index} 上。
     */
    private static void createMeta(LuaState L) {
        LuaTable mt = LuaAux.newMetatable(L, FILE_HANDLE);
        LuaAux.setFn(mt, "__gc", IoLib::fGc);
        /*
         * {@code __close} 与 {@code __gc} 必须是<b>同一个值</b>（C 里
         * {@code {"__gc", f_gc}} 与 {@code {"__close", f_gc}} 挂的是同一个函数
         * 指针，无 upvalue 时压的是轻 C 函数），所以
         * {@code getmetatable(io.stdout).__gc == getmetatable(io.stdout).__close}
         * 在参考实现里是 <b>true</b>。{@code setFn} 每次新建一层包装，两次调用
         * 就是两个值；这里直接把 {@code __gc} 那个值搬过来。
         * 与 {@code math.atan/atan2} 是同一件事（见 MathLib 的注册）。
         */
        mt.set("__close", mt.get(LuaString.latin1("__gc")));
        LuaAux.setFn(mt, "__tostring", IoLib::fTostring);
        LuaTable meth = new LuaTable();
        LuaAux.setFn(meth, "read", IoLib::fRead);
        LuaAux.setFn(meth, "write", IoLib::fWrite);
        LuaAux.setFn(meth, "lines", IoLib::fLines);
        LuaAux.setFn(meth, "flush", IoLib::fFlush);
        LuaAux.setFn(meth, "seek", IoLib::fSeek);
        LuaAux.setFn(meth, "close", IoLib::fClose);
        LuaAux.setFn(meth, "setvbuf", IoLib::fSetvbuf);
        mt.set("__index", LuaValue.ofTable(meth));
    }

    private static void createStdFile(LuaState L, LuaTable io, LuaFile f, String key,
                                      String fname) {
        LuaValue v = newFileValue(L, f, IoLib::ioNoclose);
        if (key != null) {
            L.registry.set(key, v);
        }
        io.set(fname, v);
        /*
         * {@code liolib.c: createstdfile} 最后那句
         * {@code lua_setfield(L, -2, fname)} 是<b>弹栈</b>的。
         * <p>
         * 少了这一步，开库之后主线程栈上就永久压着三个 FILE*（stdin/stdout/
         * stderr）——栈顶凭空高 3 格。这不只是「多占三格」：{@code top} 之上的
         * 槽对脚本是看不见的，但递归深度是按 {@code top} 算的，于是同样的
         * {@code LUAI_MAXSTACK} 少递归三层。上游 {@code errors.lua} 打印的
         * 「expected stack overflow after N calls」正是对着这个数断言的。
         */
        L.pop(1);
    }

    /* ==================== 文件句柄 ==================== */

    /**
     * {@code LStream}：{@code FILE*} 的替身。
     * <p>
     * C 里一个 {@code FILE*} 同时能读能写还能定位，Java 没有这种东西，所以按
     * 「真实文件走 {@link RandomAccessFile}、标准流与管道走 {@code InputStream} /
     * {@code OutputStream}」分开存，读写时各自分派。
     * <p>
     * {@code closef == null} 就是 C 的 {@code isclosed}。
     */
    static final class LuaFile {

        RandomAccessFile raf;
        InputStream in;
        OutputStream out;
        java.lang.Process proc;

        /**
         * {@code io.popen} 的子进程里那些<b>没被接管</b>的流的转发线程。
         * 关句柄时要先 {@code join} 它们，否则子进程的最后几行输出可能还没
         * 落到 {@code L.out} / {@code L.err} 上，比对就时好时坏。
         */
        Thread[] pumps;

        /**
         * 标准流要<b>现用现取</b> {@code LuaState#out} / {@code #in} / {@code #err}。
         * 测试是在开库之后才把 {@code L.out} 换成内存缓冲区的，若是开库时就把
         * 流抓在手里，{@code io.write} 的输出就永远落不到那条缓冲上。
         */
        LuaState L;
        boolean stdIn;
        boolean stdOut;
        boolean stdErr;

        boolean readable;
        boolean writable;
        boolean append;

        /**
         * 打开的是<b>目录</b>。
         * <p>
         * POSIX 的 {@code fopen(dir, "r")} 是<b>成功</b>的（{@code open(dir, O_RDONLY)}
         * 允许），失败发生在第一次 {@code fgetc} / {@code fread} 上，errno 是
         * {@code EISDIR}(21)。实测（参考二进制）：
         * <pre>
         * io.open(dir, "r")   -> file 句柄；f:read("l") -> nil, "Is a directory", 21
         * io.open(dir, "w")   -> nil, "&lt;path&gt;: Is a directory", 21
         * </pre>
         * Java 的 {@code RandomAccessFile} 在目录上是开都开不了的，所以这里
         * 单独记一个标记：打开时放行只读，读写一律报 21。
         */
        boolean dir;

        /** 目录被当零长文件看待时的虚拟位置 —— 目录 fd 上的 {@code lseek} 是允许的 */
        long dirPos;

        /** {@code ungetc} 的回退位；{@code -1} 表示空 */
        int pushback = -1;

        /** 相当于 {@code ferror(f)} */
        boolean err;

        /** 出错时的 errno */
        int errno;

        /** {@code closef}：{@code null} 表示已关闭 */
        LuaFunction closef;

        /**
         * 写缓冲 —— 真实文件<b>必须</b>有，标准流不能有。
         * <p>
         * C 的 stdio 对文件是块缓冲的：{@code io.output(p):write("x")} 之后
         * 立刻 {@code io.input(p):read()} 读到的是<b>空</b>，因为那几个字节
         * 还在 {@code FILE*} 的缓冲里没落盘。不照做的话这类脚本就会在两边
         * 给出不同结果。标准流则相反——它们是行/无缓冲语义，写完就要看得见，
         * 而且 {@code L.out} 本来就是个 {@code PrintStream}。
         */
        private final byte[] wbuf = new byte[WBUF_CAP];
        private int wlen;

        private static final int WBUF_CAP = 8192;

        /**
         * 缓冲模式：0 = 无缓冲（IONBF），1 = 全缓冲（IOFBF），2 = 行缓冲（_IOLBF）。
         * <p>
         * 默认是全缓冲（与 C stdio 对文件的默认行为一致）。标准流在创建时设成行缓冲。
         */
        int bufMode = 1;

        /** 行缓冲模式下，缓冲区大小（由 setvbuf 的第三个参数指定） */
        int bufSize = WBUF_CAP;

        /** 把写缓冲落盘；{@code raf == null} 时是空操作 */
        private boolean flushW() {
            if (raf == null || wlen == 0) {
                return true;
            }
            try {
                raf.write(wbuf, 0, wlen);
                wlen = 0;
                return true;
            } catch (IOException e) {
                err = true;
                errno = LuaAux.errnoOf(e);
                return false;
            }
        }

        InputStream inStream() {
            return stdIn ? L.in : in;
        }

        OutputStream outStream() {
            if (stdOut) {
                return L.out;
            }
            if (stdErr) {
                return L.err;
            }
            return out;
        }

        /** {@code l_getc}：-1 表示 EOF */
        int getc() {
            if (pushback >= 0) {
                int c = pushback;
                pushback = -1;
                return c;
            }
            if (!readable) {
                err = true;
                errno = 9; // EBADF
                return -1;
            }
            if (dir) {
                err = true;
                errno = 21; // EISDIR：目录读不出来，但打开是成功的
                return -1;
            }
            flushW(); // C 要求输出与输入之间必须有 fflush 或定位操作
            try {
                return raf != null ? raf.read() : inStream().read();
            } catch (IOException e) {
                err = true;
                errno = LuaAux.errnoOf(e);
                return -1;
            }
        }

        /** {@code ungetc}：{@code c == -1} 时是空操作（C 里 ungetc(EOF) 也是） */
        void ungetc(int c) {
            pushback = c;
        }

        /** {@code fread}：返回读到的字节数，0 表示 EOF 或出错 */
        int read(byte[] b, int off, int len) {
            int done = 0;
            if (pushback >= 0 && len > 0) {
                b[off] = (byte) pushback;
                pushback = -1;
                done = 1;
            }
            if (done >= len) {
                return done;
            }
            if (!readable) {
                err = true;
                errno = 9; // EBADF
                return done;
            }
            if (dir) {
                err = true;
                errno = 21; // EISDIR
                return done;
            }
            flushW();
            try {
                int r = (raf != null) ? raf.read(b, off + done, len - done)
                        : inStream().read(b, off + done, len - done);
                return (r < 0) ? done : done + r;
            } catch (IOException e) {
                err = true;
                errno = LuaAux.errnoOf(e);
                return done;
            }
        }

        /** {@code fwrite} */
        void write(byte[] b, int off, int len) {
            if (len <= 0) {
                return;
            }
            /*
             * 与 {@code fwrite} 一致：<b>写 0 个字节一定成功，而且不碰文件描述符</b>。
             * {@code liolib.c: g_write} 对字符串走的是
             * {@code fwrite(s, sizeof(char), l, f) == l}，{@code l == 0} 时
             * {@code fwrite} 返回 0、等于 {@code l}、算成功。所以往<b>只读</b>文件
             * 写空串在参考实现里是成功的 —— {@code f:write("")} 照样把文件本身
             * 返回来，而不是报 {@code Bad file descriptor}。
             * <p>
             * 早先这里先判 {@code !writable} 再看长度，于是空串也跟着报 EBADF。
             * 判据是实测的：{@code io.open(f):write("")} 参考给 1 个返回值（文件
             * 自己），{@code f:write("a")} 才给 3 个（nil, msg, errno）。
             * <p>
             * 注意 {@link #read} 那边本来就是先 {@code done >= len} 再判
             * {@code !readable}，所以读 0 字节从来没踩到这个 —— 写这边漏了同一刀。
             */
            if (!writable) {
                err = true;
                errno = 9; // EBADF
                return;
            }
            try {
                if (raf != null) {
                    // "a" 是追加：不管当前位置在哪，写之前都回到末尾
                    if (append) {
                        flushW();
                        raf.seek(raf.length());
                    }
                    // 无缓冲模式：直接写，不经过缓冲区
                    if (bufMode == 0) {
                        raf.write(b, off, len);
                        return;
                    }
                    if (wlen + len > wbuf.length) {
                        if (!flushW()) {
                            return;
                        }
                    }
                    if (len >= wbuf.length) {
                        raf.write(b, off, len); // 大块直接写，不绕缓冲
                    } else {
                        System.arraycopy(b, off, wbuf, wlen, len);
                        wlen += len;
                        // 行缓冲模式：检查是否有换行符，有就 flush
                        if (bufMode == 2) {
                            for (int i = 0; i < len; i++) {
                                if (b[off + i] == '\n') {
                                    flushW();
                                    break;
                                }
                            }
                        }
                    }
                } else {
                    outStream().write(b, off, len);
                    outStream().flush(); // 标准流不缓冲
                }
            } catch (IOException e) {
                err = true;
                errno = LuaAux.errnoOf(e);
            }
        }

        /** {@code l_fseek}：成功返回新位置，-1 表示失败（管道会失败） */
        long seek(long offset, int whence) {
            if (dir) {
                /*
                 * 目录 fd 上的 lseek 是允许的，位置照算；长度按 0 看。
                 * 真实目录的 SEEK_END 给出的是目录自身的大小（本机 64），
                 * 那是文件系统定的、不比 —— 见用例 javadoc。
                 */
                long base = (whence == 1) ? dirPos : 0; // 仅 SEEK_CUR 从当前位置起算
                long pos = base + offset;
                if (pos < 0) {
                    err = true;
                    errno = 22; // EINVAL
                    return -1;
                }
                dirPos = pos;
                return pos;
            }
            if (raf == null) {
                err = true;
                errno = 29; // ESPIPE
                return -1;
            }
            flushW();
            try {
                long base;
                switch (whence) {
                case 0: // SEEK_SET
                    base = 0;
                    break;
                case 1: // SEEK_CUR
                    base = raf.getFilePointer();
                    break;
                default: // SEEK_END
                    base = raf.length();
                    break;
                }
                /*
                 * 负的<b>目标位置</b>是 {@code EINVAL}(22)，不是随便一个 IO 错。
                 * C 的 {@code fseek} 在 {@code SEEK_SET} / {@code CUR} / {@code END}
                 * 三种起点上都是这个判据——实测参考实现三种都给
                 * {@code nil, "Invalid argument", 22}，而 Java 的
                 * {@code RandomAccessFile.seek} 抛的是 "Negative seek offset"，
                 * 落到 {@code errnoOf} 的兜底分支上就成了 {@code EIO}(5)。
                 * 往 EOF <b>之后</b>定位是允许的（返回新位置，不报错），别一起拦了。
                 */
                long pos = base + offset;
                if (pos < 0) {
                    err = true;
                    errno = 22; // EINVAL
                    return -1;
                }
                /*
                 * 成功的 {@code fseek} 会<b>丢掉 ungetc 退回来的那个字节</b>。
                 * 实测的分歧：{@code f:read("n")} 在 "three" 上失败时把 't' 退了回去，
                 * 随后 {@code f:seek("set",0)} 再 {@code f:read(3)} —— 参考给
                 * {@code "1 2"}，我们给 {@code "t1 "}。
                 */
                pushback = -1;
                raf.seek(pos);
                return raf.getFilePointer();
            } catch (IOException e) {
                err = true;
                errno = LuaAux.errnoOf(e);
                return -1;
            }
        }

        /** {@code l_ftell} */
        long tell() {
            if (dir) {
                return dirPos; // 目录 fd 上的 ftell 是允许的
            }
            if (raf == null) {
                err = true;
                errno = 29; // ESPIPE
                return -1;
            }
            try {
                // 缓冲里那几个字节也算在逻辑位置上：C 的 ftell 给的也是这个
                return raf.getFilePointer() + wlen;
            } catch (IOException e) {
                err = true;
                errno = LuaAux.errnoOf(e);
                return -1;
            }
        }

        /** {@code fflush} */
        void flush() {
            try {
                if (raf != null) {
                    flushW();
                    raf.getFD().sync();
                } else if (writable) {
                    outStream().flush();
                }
            } catch (IOException e) {
                err = true;
                errno = LuaAux.errnoOf(e);
            }
        }

        /** 真正关掉底层资源 */
        void closeRaw() {
            try {
                if (raf != null) {
                    flushW();
                    raf.close();
                }
                if (in != null) {
                    in.close();
                }
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                err = true;
                errno = LuaAux.errnoOf(e);
            }
        }
    }

    private static LuaFile stdFile(LuaState L, boolean r, boolean w, boolean e) {
        LuaFile f = new LuaFile();
        f.L = L;
        f.stdIn = r;
        f.stdOut = w && !e;
        f.stdErr = e;
        f.readable = r;
        f.writable = w;
        return f;
    }

        /**
     * 文件句柄 userdata 的长度：参考里 {@code lua_rawlen(io.stdout)} 给
     * <b>16</b>，那是 {@code sizeof(LStream)}（{@code FILE*} +
     * {@code lua_CFunction closef}）在 LP64 上的值。见 {@link LuaUserdata#len}。
     */
    private static final int FILE_UDATA_LEN = 16;

    /** {@code newprefile} 的建值部分：userdata + 命名元表，closef 留空 */
    private static LuaValue newPreFileValue(LuaState L, LuaFile f) {
        L.push(LuaValue.ofUserdata(f, FILE_UDATA_LEN));
        LuaAux.setMetatableByName(L, FILE_HANDLE);
        return new LuaValue(L.stack[L.top - 1]);
    }

    /** {@code newprefile} + 指定 {@code closef} */
    private static LuaValue newFileValue(LuaState L, LuaFile f, LuaFunction closef) {
        LuaValue v = newPreFileValue(L, f);
        f.closef = closef;
        return v;
    }

    /** {@code newfile}：普通文件，closef 是 {@code io_fclose} */
    private static LuaValue newFile(LuaState L, LuaFile f) {
        return newFileValue(L, f, IoLib::ioFclose);
    }

    /* ==================== 句柄查询 ==================== */

    /** {@code tolstream}：必须是文件句柄，否则报错 */
    private static LuaFile toLStream(LuaState L) {
        return (LuaFile) LuaAux.checkUdata(L, 1, FILE_HANDLE);
    }

    /** {@code tofile}：在上面再加一条「不能是已关闭的」 */
    private static LuaFile toFile(LuaState L) {
        LuaFile p = toLStream(L);
        if (p.closef == null) {
            throw L.libError("attempt to use a closed file");
        }
        return p;
    }

    /* ==================== io 库函数 ==================== */

    /** {@code io.type} */
    private static int ioType(LuaState L) {
        LuaAux.checkAny(L, 1);
        LuaFile p = (LuaFile) LuaAux.testUdata(L, 1, FILE_HANDLE);
        if (p == null) {
            LuaAux.pushFail(L);
        } else if (p.closef == null) {
            L.push(LuaValue.ofString(LuaString.latin1("closed file")));
        } else {
            L.push(LuaValue.ofString(LuaString.latin1("file")));
        }
        return 1;
    }

    /**
     * {@code f_tostring}。
     * <p>
     * C 是 {@code "file (%p)"}，地址不可能对得上，只能给个同形状的十六进制值。
     */
    private static int fTostring(LuaState L) {
        LuaFile p = toLStream(L);
        if (p.closef == null) {
            L.push(LuaValue.ofString(LuaString.latin1("file (closed)")));
        } else {
            // "%p" 不补前导零，与 BaseLib.tostring 的地址形状保持一致
            L.push(LuaValue.ofString(LuaString.latin1(
                    "file (0x" + Integer.toHexString(System.identityHashCode(p)) + ")")));
        }
        return 1;
    }

    /**
     * {@code aux_close}：调句柄自己的 {@code closef}。
     * <p>
     * 先把 {@code closef} 清空<b>再</b>调，顺序照抄 C——这样 {@code __gc} 与
     * 显式 {@code close} 撞到一起时不会关两遍。
     */
    private static int auxClose(LuaState L) {
        LuaFile p = toLStream(L);
        LuaFunction cf = p.closef;
        p.closef = null;
        return cf.call(L);
    }

    /** {@code f_close} */
    private static int fClose(LuaState L) {
        toFile(L); // 先确认是个开着的流
        return auxClose(L);
    }

    /** {@code io_close}：不给参数就关默认输出 */
    private static int ioClose(LuaState L) {
        if (L.isNoneArg(1)) {
            LuaValue v = L.registry.get(IO_OUTPUT);
            L.push(v == null ? LuaValue.ofNil() : v);
        }
        return fClose(L);
    }

    /** {@code f_gc}：{@code __gc} 与 {@code __close} 共用 */
    private static int fGc(LuaState L) {
        LuaFile p = toLStream(L);
        if (p.closef != null) {
            auxClose(L);
        }
        return 0;
    }

    /** {@code io_fclose}：普通文件的 closef */
    private static int ioFclose(LuaState L) {
        LuaFile p = toLStream(L);
        p.err = false;
        p.closeRaw();
        LuaAux.fileResult(L, !p.err, null, p.errno);
        return p.err ? 3 : 1;
    }

    /** {@code io_noclose}：标准文件的 closef，关不掉 */
    private static int ioNoclose(LuaState L) {
        LuaFile p = toLStream(L);
        p.closef = IoLib::ioNoclose; // 保持打开
        LuaAux.pushFail(L);
        L.push(LuaValue.ofString(LuaString.latin1("cannot close standard file")));
        return 2;
    }

    /** {@code l_checkmode}：只认 {@code [rwa]%+?[b]*} */
    private static boolean checkMode(String mode) {
        if (mode.isEmpty()) {
            return false;
        }
        char c = mode.charAt(0);
        if (c != 'r' && c != 'w' && c != 'a') {
            return false;
        }
        int i = 1;
        if (i < mode.length() && mode.charAt(i) == '+') {
            i++;
        }
        for (; i < mode.length(); i++) {
            if (mode.charAt(i) != 'b') {
                return false;
            }
        }
        return true;
    }

    /** {@code l_checkmodep}：{@code popen} 只认 {@code "r"} / {@code "w"} */
    private static boolean checkModeP(String mode) {
        return (mode.length() == 1) && (mode.charAt(0) == 'r' || mode.charAt(0) == 'w');
    }

    /** {@code opencheck}：打不开直接报错（{@code io.lines} 用它） */
    private static void openCheck(LuaState L, LuaString fname, String mode) {
        LuaFile f = new LuaFile();
        L.push(LuaValue.ofUserdata(f, FILE_UDATA_LEN));
        LuaAux.setMetatableByName(L, FILE_HANDLE);
        f.closef = IoLib::ioFclose;
        if (!openInto(f, fname.toJavaString(), mode)) {
            // 文件名是任意字节，消息得按字节拼
            throw L.libError(LuaString.latin1("cannot open file '").concat(fname)
                    .concat(LuaString.latin1("' (" + LuaAux.strError(f.errno) + ")")));
        }
    }

    /** 按 mode 打开真实文件；失败时把 errno 记进 {@code f} 并返回 false */
    private static boolean openInto(LuaFile f, String fname, String mode) {
        char base = mode.charAt(0);
        boolean plus = mode.indexOf('+') >= 0;
        f.readable = (base == 'r') || plus;
        f.writable = (base != 'r') || plus;
        f.append = (base == 'a');
        /*
         * 目录：C 的 fopen 在只读模式上是开得开的（open(dir, O_RDONLY) 允许），
         * 要写才给 EISDIR。Java 这边 RandomAccessFile 两种模式都开不了，
         * 所以先判目录再分模式 —— 否则写目录报的是 ENOENT 而不是 EISDIR。
         */
        if (new java.io.File(fname).isDirectory()) {
            if (f.writable) {
                f.err = true;
                f.errno = 21; // EISDIR
                return false;
            }
            f.dir = true;
            return true;
        }
        /*
         * {@code "r+"}：<b>C 的 {@code fopen("r+") 不创建文件</b>——它走的是
         * {@code open(path, O_RDWR)}，没带 {@code O_CREAT}；而 Java 的
         * {@code RandomAccessFile(f, "rw")} <b>一定会建</b>。于是
         * {@code io.open(不存在的文件, "r+")} 参考给
         * {@code nil, "...: No such file or directory", 2}，我们却开出一个
         * 新文件。
         * <p>
         * 修法是先用<b>只读</b>模式探一次（{@code "r"} 模式本身就不创建），
         * 打不开就说明文件不在（或者没权限），errno 交给
         * {@code LuaAux.errnoOf} 从异常消息里反查——于是 ENOENT 与 EACCES
         * 各归各的，不用在这儿再判一遍。直接用 {@code File.exists()} 会把
         * 「父目录不可搜索」的 EACCES 错报成 ENOENT。
         */
        if (base == 'r' && f.writable) {
            try (RandomAccessFile exists = new RandomAccessFile(fname, "r")) {
                // 只为确认「这个文件已经在」，句柄立刻关掉
            } catch (IOException e) {
                f.err = true;
                f.errno = LuaAux.errnoOf(e);
                return false;
            }
        }
        try {
            f.raf = new RandomAccessFile(fname, f.writable ? "rw" : "r");
            if (base == 'w') {
                f.raf.setLength(0);
            }
            if (f.append) {
                f.raf.seek(f.raf.length());
            }
            return true;
        } catch (IOException e) {
            f.err = true;
            f.errno = LuaAux.errnoOf(e);
            return false;
        }
    }

    /**
     * {@code io.open}
     * <p>
     * 文件名与模式都是 {@code fopen} 的 {@code const char*}，在第一个 {@code \0}
     * 处砍断：{@code io.open("p", "r\0w")} 的模式是 {@code "r"}，不是 {@code "r\0w"}
     * （后者 {@code checkMode} 会判成 invalid mode）。
     */
    private static int ioOpen(LuaState L) {
        LuaString fname = LuaAux.checkString(L, 1).toCString();
        String filename = fname.toJavaString(); // 交给 Java 文件 API 的那一侧
        String mode = LuaAux.optString(L, 2, LuaString.latin1("r")).toCString().toJavaString();
        LuaFile f = new LuaFile();
        newFile(L, f);
        LuaAux.argCheck(L, checkMode(mode), 2, "invalid mode");
        f.err = false;
        if (!openInto(f, filename, mode)) {
            LuaAux.fileResult(L, false, fname, f.errno);
            return 3;
        }
        return 1;
    }

    /** {@code io.popen} */
    private static int ioPopen(LuaState L) {
        LuaString fname = LuaAux.checkString(L, 1).toCString();
        String filename = fname.toJavaString();
        String mode = LuaAux.optString(L, 2, LuaString.latin1("r")).toCString().toJavaString();
        LuaFile f = new LuaFile();
        L.push(LuaValue.ofUserdata(f, FILE_UDATA_LEN));
        LuaAux.setMetatableByName(L, FILE_HANDLE);
        LuaAux.argCheck(L, checkModeP(mode), 2, "invalid mode");
        f.err = false;
        boolean forRead = mode.charAt(0) == 'r';
        /*
         * C 的 {@code popen} <b>只接管一条流</b>：读模式接管子进程的
         * {@code stdout}，写模式接管它的 {@code stdin}，<b>其余一律继承</b>
         * ——它内部就是 {@code pipe} + {@code fork} + 一次 {@code dup2} +
         * {@code exec}，没碰的 fd 原样传下去。
         * <p>
         * 实测（参考二进制）：
         * <pre>
         * io.popen("echo out; echo err 1>&2", "r")  -- 读到 "out"，"err" 落在 lua 自己的 stderr
         * io.popen("cat; echo WERR 1>&2", "w")      -- 写进去的 "hello" 被 cat 回显到 stdout
         * </pre>
         * Java 的 {@code ProcessBuilder} 默认是<b>三条全接成管道</b>，于是这两
         * 边全被吞掉；更糟的是读模式下子进程的 {@code stdin} 也是管道且没人关
         * ——{@code io.popen("cat", "r")} 会一直等到天荒地老。
         * <p>
         * 目标流用 {@code LuaState#out} / {@code #err} 而不是
         * {@code Redirect.INHERIT}：INHERIT 走的是 <b>JVM 进程</b>的 fd 1 / fd 2，
         * 而本实现的「标准输出 / 标准错误」就是 {@code L.out} / {@code L.err}
         * （差分测试正是靠把它们换成内存缓冲区来比 {@code popen} 的输出的）。
         * C 那边的落点是 fd 1 / fd 2，两边是同一件事。
         */
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", filename);
            /*
             * 重定向必须在 {@code start()} <b>之前</b>设好 —— {@code ProcessBuilder}
             * 是在 {@code start} 那一刻才照这份配置去摆 fd 的，之后再改不影响已
             * 经起来的子进程。读模式要把 {@code stdin} 设为 INHERIT：子进程的
             * 输入应当来自 lua 自己的标准输入（{@code cat} 这类命令才读得到东西），
             * 而不是一条没人写也没人关的管道。
             */
            if (forRead) {
                pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
            }
            java.lang.Process p = pb.start();
            f.proc = p;
            java.util.List<Thread> ps = new java.util.ArrayList<>();
            if (forRead) {
                f.in = p.getInputStream();
                f.readable = true;
                ps.add(pump(p.getErrorStream(), L.err));
            } else {
                f.out = p.getOutputStream();
                f.writable = true;
                ps.add(pump(p.getInputStream(), L.out));
                ps.add(pump(p.getErrorStream(), L.err));
            }
            f.pumps = ps.toArray(new Thread[0]);
        } catch (IOException e) {
            f.err = true;
            f.errno = LuaAux.errnoOf(e);
        }
        f.closef = IoLib::ioPclose;
        if (f.err) {
            LuaAux.fileResult(L, false, fname, f.errno);
            return 3;
        }
        return 1;
    }

    /**
     * 起一条守护线程，把子进程那条「没被接管」的流<b>原样搬</b>到
     * {@code LuaState} 的输出流上。
     * <p>
     * 搬而不是 {@code Redirect.INHERIT} 的理由见 {@link #ioPopen}：
     * INHERIT 绕过了 {@code L.out} / {@code L.err}。
     * <p>
     * 必须<b>一直抽干</b>：子进程往里写而我们不读的话，管道缓冲满了它就会
     * 阻塞 —— C 那边目标是继承来的 fd，不会堵。守护线程是为了「脚本压根没
     * 关这个句柄」时也不拖住 JVM 退出。
     */
    private static Thread pump(InputStream src, OutputStream dst) {
        Thread t = new Thread(() -> {
            byte[] b = new byte[4096];
            try {
                int n;
                while ((n = src.read(b)) > 0) {
                    dst.write(b, 0, n);
                    dst.flush(); // 行/无缓冲语义：子进程写一行就看得见
                }
            } catch (IOException e) {
                // 子进程一退出管道就自然断在这里，正常结束
            } finally {
                try {
                    src.close();
                } catch (IOException ignored) {
                    // 关不掉就算了
                }
            }
        });
        t.setName("lua-popen-pump");
        t.setDaemon(true);
        t.start();
        return t;
    }

    /** {@code pclose} 等转发线程的上限（毫秒），理由见 {@link #ioPclose} */
    private static final long PUMP_JOIN_MS = 2000;

    /** {@code io_pclose} */
    private static int ioPclose(LuaState L) {
        LuaFile p = toLStream(L);
        if (p.proc == null) {
            // popen 就没起来过（shell 不存在之类），没有进程可等
            LuaAux.fileResult(L, false, null, (p.errno != 0) ? p.errno : 2);
            return 3;
        }
        int stat;
        try {
            if (p.out != null) {
                p.out.close();
            }
            stat = p.proc.waitFor();
            if (p.in != null) {
                p.in.close();
            }
        } catch (IOException e) {
            LuaAux.fileResult(L, false, null, LuaAux.errnoOf(e));
            return 3;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LuaAux.fileResult(L, false, null, 4); // EINTR
            return 3;
        }
        /*
         * 等转发线程收尾 —— 子进程已经退出了，它们读到 EOF 就会结束，所以
         * join 不会久等。不等的话 {@code popen} 的最后几行输出可能还没落到
         * {@code L.out} / {@code L.err} 上，比对就时好时坏：C 那边是 fd 直接
         * 写的，{@code pclose} 返回时早就写完了。
         * <p>
         * <b>但 join 必须有上限。</b>C 的 {@code pclose} 只 {@code waitpid}
         * 那一个子进程，因为没被接管的那两条流是<b>继承来的 fd</b>，孙进程
         * 拿着也无所谓；而这边是<b>管道</b> —— {@code io.popen("sleep 60 & echo hi")}
         * 这种会把写端留给一个活着的孙进程，{@code waitFor} 早返回了，转发线程
         * 却永远读不到 EOF，无上限的 join 就把 {@code f:close()} 卡死。
         * 设一个 2 秒的上限：正常情况下子进程一退出、管道里那点数据瞬间就抽
         * 干了；抽不干的只可能是「还有别人握着写端」，那种情况下等也没用。
         */
        if (p.pumps != null) {
            for (Thread t : p.pumps) {
                try {
                    t.join(PUMP_JOIN_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            p.pumps = null;
        }
        LuaAux.execResult(L, stat, 0);
        return 3;
    }

    /** {@code io.tmpfile} */
    private static int ioTmpfile(LuaState L) {
        LuaFile f = new LuaFile();
        newFile(L, f);
        f.err = false;
        try {
            java.io.File tmp = java.io.File.createTempFile("lua_", ".tmp");
            tmp.deleteOnExit();
            f.raf = new RandomAccessFile(tmp, "rw");
            f.readable = true;
            f.writable = true;
        } catch (IOException e) {
            f.err = true;
            f.errno = LuaAux.errnoOf(e);
        }
        if (f.err) {
            LuaAux.fileResult(L, false, null, f.errno);
            return 3;
        }
        return 1;
    }

    /**
     * {@code getiofile}：取注册表里的默认文件，已关就报错。
     * <p>
     * 返回的是<b>值</b>而不是句柄，因为 C 的 {@code getiofile} 是
     * {@code lua_getfield}——它把文件<b>压到了栈上</b>，而 {@code io.read} /
     * {@code io.write} 正是靠这个把句柄当成返回值带出去的。
     */
    private static LuaValue getIoFile(LuaState L, String findex) {
        LuaValue v = L.registry.get(findex);
        LuaFile p = (v == null || v.baseType() != Lua.TUSERDATA)
                ? null : (LuaFile) LuaUserdata.dataOf(v);
        if (p == null || p.closef == null) {
            throw L.libError("default " + findex.substring("_IO_".length())
                    + " file is closed");
        }
        return v;
    }

    /** {@code g_iofile}：{@code io.input} / {@code io.output} 共用 */
    private static int gIoFile(LuaState L, String key, String mode) {
        if (!L.isNoneArg(1) && !L.arg1(1).isNil()) {
            LuaValue a = L.arg1(1);
            LuaString s = (a.isString() || a.isNumber()) ? LuaAux.asString(a) : null;
            if (s != null) {
                openCheck(L, s.toCString(), mode);
            } else {
                toFile(L); // 确认是个开着的句柄
                L.push(a);
            }
            L.registry.set(key, new LuaValue(L.stack[L.top - 1]));
        }
        LuaValue v = L.registry.get(key);
        L.push(v == null ? LuaValue.ofNil() : v);
        return 1;
    }

    private static int ioInput(LuaState L) {
        return gIoFile(L, IO_INPUT, "r");
    }

    private static int ioOutput(LuaState L) {
        return gIoFile(L, IO_OUTPUT, "w");
    }

    /* ==================== lines ==================== */

    /**
     * {@code aux_lines}。
     * <p>
     * C 是「压三个值再 {@code lua_rotate} 再 {@code lua_pushcclosure}」，这里是
     * 直接按最终布局（文件、读格式个数、是否要关、各读格式）填 {@link CClosure}
     * 的 upvalue——排布完全一样，省掉那次旋转。
     */
    private static void auxLines(LuaState L, boolean toclose) {
        int n = L.getTop() - 1; // 读格式个数
        LuaAux.argCheck(L, n <= MAXARGLINE, MAXARGLINE + 2, "too many arguments");
        CClosure cl = new CClosure(IoLib::readline, 3 + n);
        cl.upvalue[0].set(L.arg(0)); // 文件
        cl.upvalue[1].set(LuaValue.ofInteger(n));
        cl.upvalue[2].set(LuaValue.ofBoolean(toclose));
        for (int i = 0; i < n; i++) {
            cl.upvalue[3 + i].set(L.arg(1 + i));
        }
        L.push(LuaValue.ofCClosure(cl));
    }

    private static int fLines(LuaState L) {
        toFile(L);
        auxLines(L, false);
        return 1;
    }

    /**
     * {@code io.lines}。
     * <p>
     * 给了文件名时返回的第四个数是「待关闭变量」——泛型 for 会把它当
     * {@code <close>} 处理，迭代结束（或中途出错）时关掉文件。
     */
    private static int ioLines(LuaState L) {
        boolean toclose;
        if (L.isNoneArg(1)) {
            L.pushNil();
        }
        if (L.arg1(1).isNil()) {
            LuaValue v = L.registry.get(IO_INPUT);
            if (v == null) {
                v = LuaValue.ofNil();
            }
            if (L.isNoneArg(1)) {
                L.push(v); // 一个参数都没有：直接让它当第 1 个
            } else {
                L.push(v);
                L.replace(1); // 显式传了 nil：覆盖掉
            }
            toFile(L); // 确认默认输入还开着
            toclose = false;
        } else {
            LuaString filename = LuaAux.checkString(L, 1).toCString();
            openCheck(L, filename, "r");
            L.replace(1);
            toclose = true;
        }
        auxLines(L, toclose);
        if (toclose) {
            L.pushNil(); // state
            L.pushNil(); // control
            L.pushValue(1); // 文件作为待关闭变量
            return 4;
        }
        return 1;
    }

    /* ==================== 读 ==================== */

    /** {@code test_eof}：{@code f:read(0)} 用它 */
    private static boolean testEof(LuaState L, LuaFile f) {
        int c = f.getc();
        f.ungetc(c);
        L.push(LuaValue.ofString(LuaString.latin1("")));
        return c >= 0;
    }

    /** {@code read_line}：一直读到 {@code '\n'} 或 EOF；{@code chop} 决定要不要留换行 */
    private static boolean readLine(LuaState L, LuaFile f, boolean chop) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int c;
        do {
            c = f.getc();
            if (c < 0 || c == '\n') {
                break;
            }
            bos.write(c);
        } while (true);
        if (!chop && c == '\n') {
            bos.write('\n');
        }
        byte[] b = bos.toByteArray();
        L.push(LuaValue.ofString(L.newString(b, 0, b.length)));
        return (c == '\n') || b.length > 0;
    }

    /** {@code read_all} */
    private static void readAll(LuaState L, LuaFile f) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        for (;;) {
            int n = f.read(buf, 0, buf.length);
            if (n <= 0) {
                break;
            }
            bos.write(buf, 0, n);
        }
        byte[] b = bos.toByteArray();
        L.push(LuaValue.ofString(L.newString(b, 0, b.length)));
    }

    /**
     * {@code read_chars}。
     * <p>
     * C 里 {@code g_read} 写的是 {@code size_t l = (size_t)luaL_checkinteger(L, n)}：
     * <b>负数按无符号解释成一个天文数字</b>，随后 {@code luaL_prepbuffsize} 去
     * 预分配这么大一块，{@code resizebox} 分配失败抛 {@code "not enough memory"}。
     * 实测 {@code f:read(-1)} 在参考实现上就是 {@code false  not enough memory}，
     * 而不是什么整数越界。
     * <p>
     * 所以这里<b>不预分配 {@code n} 个字节</b>，改成循环读到「流干」或「够数」为止
     * ——这也更贴近 {@code fread}：短读时 C 会继续读到 {@code n} 或 EOF。只有大得
     * 任何系统都分配不出来时才抛内存错。
     */
    private static boolean readChars(LuaState L, LuaFile f, long n) {
        if (n < 0 || n > MAX_READ_ALLOC) {
            /*
             * C 那边是 {@code resizebox} 里 {@code lua_pushliteral +
             * lua_error}，而 {@code lua_error} 认出这是内存错误消息，于是走
             * {@code luaM_error}（{@code LUA_ERRMEM}）——
             * {@code xpcall} 的处理函数<b>接不到</b>。
             */
            LuaError e = new LuaError(LuaValue.ofString(LuaString.latin1("not enough memory")));
            e.errMem = true;
            throw e;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[CHUNK];
        long left = n;
        while (left > 0) {
            int nr = f.read(buf, 0, (int) Math.min(left, CHUNK));
            if (nr <= 0) {
                break;
            }
            bos.write(buf, 0, nr);
            left -= nr;
        }
        byte[] b = bos.toByteArray();
        L.push(LuaValue.ofString(L.newString(b, 0, b.length)));
        return b.length > 0;
    }

    /**
     * {@code read_number}。
     * <p>
     * 先把「一个合法数字前缀」读进缓冲，再交给 {@code lua_stringtonumber} 判定——
     * 这样 {@code "3x"} 会读成 {@code 3} 并把 {@code x} 留在流里，与 C 一致。
     */
    private static boolean readNumber(LuaState L, LuaFile f) {
        StringBuilder b = new StringBuilder();
        int count = 0;
        boolean hex = false;
        int c;
        do {
            c = f.getc();
        } while (c >= 0 && isSpace(c));
        if (c == '-' || c == '+') {
            b.append((char) c);
            c = f.getc();
        }
        if (c == '0') {
            b.append('0');
            c = f.getc();
            if (c == 'x' || c == 'X') {
                hex = true;
                b.append((char) c);
                c = f.getc();
            } else {
                count = 1;
            }
        }
        while (c >= 0 && (hex ? isXdigit(c) : isDigit(c)) && b.length() <= L_MAXLENNUM) {
            b.append((char) c);
            count++;
            c = f.getc();
        }
        if (c == '.') { // locale 的小数点这里只认 '.'，与 C 的默认一致
            b.append('.');
            c = f.getc();
            while (c >= 0 && (hex ? isXdigit(c) : isDigit(c)) && b.length() <= L_MAXLENNUM) {
                b.append((char) c);
                count++;
                c = f.getc();
            }
        }
        if (count > 0 && (c == 'e' || c == 'E' || (hex && (c == 'p' || c == 'P')))) {
            b.append((char) c);
            c = f.getc();
            if (c == '-' || c == '+') {
                b.append((char) c);
                c = f.getc();
            }
            while (c >= 0 && isDigit(c) && b.length() <= L_MAXLENNUM) {
                b.append((char) c);
                c = f.getc();
            }
        }
        f.ungetc(c);
        LuaValue num = (b.length() > L_MAXLENNUM) ? null : Numeral.str2num(b.toString());
        if (num != null) {
            L.push(num);
            return true;
        }
        L.pushNil();
        return false;
    }

    /**
     * C 的 {@code isspace}（C locale）：空格、{@code \f \n \r \t \v}。
     * <p>
     * 竖制表符写成 {@code 11}：Java 没有 {@code '\v'} 这个转义（{@code '\f'} 倒是有）。
     */
    private static boolean isSpace(int c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == 11 || c == '\f';
    }

    private static boolean isDigit(int c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isXdigit(int c) {
        return isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    /**
     * {@code g_read}。
     *
     * @param first 第一个读格式所在的<b>绝对参数下标</b>（{@code io.read} 是 1，
     *              {@code f:read} 是 2）
     */
    private static int gRead(LuaState L, LuaFile f, int first) {
        int nargs = L.getTop() - 1;
        f.err = false;
        int n;
        boolean success;
        if (nargs == 0) {
            success = readLine(L, f, true);
            n = first + 1;
        } else {
            LuaAux.checkStack(L, nargs + LuaState.MINSTACK, "too many arguments");
            success = true;
            n = first;
            for (int left = nargs; left > 0 && success; left--, n++) {
                LuaValue a = L.arg1(n);
                if (a.baseType() == Lua.TNUMBER) {
                    long len = LuaAux.checkInteger(L, n);
                    success = (len == 0) ? testEof(L, f) : readChars(L, f, len);
                } else {
                    LuaString p = LuaAux.checkString(L, n);
                    int at = (p.length() > 0 && p.byteAt(0) == '*') ? 1 : 0;
                    int fmt = (at < p.length()) ? p.byteAt(at) : 0;
                    switch (fmt) {
                    case 'n':
                        success = readNumber(L, f);
                        break;
                    case 'l':
                        success = readLine(L, f, true);
                        break;
                    case 'L':
                        success = readLine(L, f, false);
                        break;
                    case 'a':
                        readAll(L, f);
                        success = true;
                        break;
                    default:
                        throw L.argError(n, "invalid format");
                    }
                }
            }
        }
        if (f.err) {
            LuaAux.fileResult(L, false, null, f.errno);
            return 3;
        }
        if (!success) {
            L.top--; // 丢掉最后一个结果
            LuaAux.pushFail(L);
        }
        return n - first;
    }

    private static int ioRead(LuaState L) {
        LuaValue v = getIoFile(L, IO_INPUT);
        L.push(v); // 压在所有读格式之后，g_read 会把它顶掉
        return gRead(L, (LuaFile) LuaUserdata.dataOf(v), 1);
    }

    private static int fRead(LuaState L) {
        return gRead(L, toFile(L), 2);
    }

    /**
     * {@code io_readline}：{@code lines} 的迭代函数。
     */
    private static int readline(LuaState L) {
        LuaFile p = (LuaFile) LuaUserdata.dataOf(L.upvalue(0));
        int n = (int) L.upvalue(1).i;
        if (p.closef == null) {
            throw L.libError("file is already closed");
        }
        L.setTop(1);
        LuaAux.checkStack(L, n, "too many arguments");
        for (int i = 0; i < n; i++) {
            L.push(L.upvalue(3 + i));
        }
        n = gRead(L, p, 2);
        if (L.stack[L.top - n].toBoolean()) {
            return n;
        }
        if (n > 1) {
            // 第二个结果是错误信息
            throw L.libError(L.stack[L.top - n + 1].asString().toLatin1String());
        }
        if (L.upvalue(2).toBoolean()) {
            L.setTop(0);
            L.push(L.upvalue(0));
            auxClose(L);
        }
        return 0;
    }

    /* ==================== 写 ==================== */

    /** {@code g_write} */
    private static int gWrite(LuaState L, LuaFile f, int arg) {
        int nargs = L.getTop() - arg;
        boolean status = true;
        f.err = false;
        for (; nargs-- > 0; arg++) {
            LuaValue v = L.arg1(arg);
            byte[] b;
            if (v.baseType() == Lua.TNUMBER) {
                /*
                 * 浮点按 LUA_NUMBER_FMT（%.14g）写，<b>不补 .0</b>——
                 * 那是 luaO_tostring 才做的事。实测 io.write(-0.0) 出 "-0"，
                 * 而 print(-0.0) 出 "-0.0"。
                 */
                String s = v.isInteger() ? Long.toString(v.i) : Vm.formatG(v.n, 14);
                b = s.getBytes(StandardCharsets.US_ASCII);
            } else {
                LuaString s = LuaAux.checkString(L, arg);
                b = s.bytes;
            }
            f.write(b, 0, b.length);
            status = status && !f.err;
        }
        if (status) {
            return 1; // 句柄已经在栈顶
        }
        LuaAux.fileResult(L, false, null, f.errno);
        return 3;
    }

    private static int ioWrite(LuaState L) {
        LuaValue v = getIoFile(L, IO_OUTPUT);
        L.push(v); // 压在待写的值之后，g_write 返回的 1 个值就是它
        return gWrite(L, (LuaFile) LuaUserdata.dataOf(v), 1);
    }

    private static int fWrite(LuaState L) {
        LuaFile f = toFile(L);
        L.pushValue(1); // 把句柄压到栈顶作为返回值
        return gWrite(L, f, 2);
    }

    /* ==================== 定位与缓冲 ==================== */

    /** {@code f_seek} */
    private static int fSeek(LuaState L) {
        LuaFile f = toFile(L);
        int op = LuaAux.checkOption(L, 2, "cur", new String[] { "set", "cur", "end" });
        long p3 = LuaAux.optInteger(L, 3, 0);
        f.err = false;
        long pos = f.seek(p3, op);
        if (pos < 0) {
            LuaAux.fileResult(L, false, null, f.errno);
            return 3;
        }
        L.push(LuaValue.ofInteger(f.tell()));
        return 1;
    }

    /**
     * {@code f_setvbuf}。
     * <p>
     * 参数校验后真正改缓冲策略：{@code no} 无缓冲、{@code full} 全缓冲、
     * {@code line} 行缓冲。第三个参数指定缓冲区大小（行缓冲/全缓冲时有效）。
     */
    private static int fSetvbuf(LuaState L) {
        LuaFile f = toFile(L);
        String[] modes = new String[] { "no", "full", "line" };
        int modeIdx = LuaAux.checkOption(L, 2, null, modes);
        String mode = modes[modeIdx];
        int size = (int) LuaAux.optInteger(L, 3, LUAL_BUFFERSIZE);
        // 先 flush 现有缓冲，再切换模式
        f.flushW();
        switch (mode) {
            case "no":
                f.bufMode = 0;
                break;
            case "full":
                f.bufMode = 1;
                f.bufSize = size;
                break;
            case "line":
                f.bufMode = 2;
                f.bufSize = size;
                break;
        }
        LuaAux.fileResult(L, true, null, 0);
        return 1;
    }

    private static int ioFlush(LuaState L) {
        LuaFile f = (LuaFile) LuaUserdata.dataOf(getIoFile(L, IO_OUTPUT));
        f.err = false;
        f.flush();
        LuaAux.fileResult(L, !f.err, null, f.errno);
        return f.err ? 3 : 1;
    }

    private static int fFlush(LuaState L) {
        LuaFile f = toFile(L);
        f.err = false;
        f.flush();
        LuaAux.fileResult(L, !f.err, null, f.errno);
        return f.err ? 3 : 1;
    }
}
