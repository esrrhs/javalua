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
 *   <li><b>{@code popen}</b> 的子进程输出走管道而不是 {@code FILE*}，
 *       {@code io.popen(cmd, "w")} 的写端语义一致，但拿不到 shell 的真实 errno。</li>
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
        LuaAux.setFn(mt, "__close", IoLib::fGc);
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
                raf.seek(base + offset);
                return raf.getFilePointer();
            } catch (IOException e) {
                err = true;
                errno = LuaAux.errnoOf(e);
                return -1;
            }
        }

        /** {@code l_ftell} */
        long tell() {
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

    /** {@code newprefile} 的建值部分：userdata + 命名元表，closef 留空 */
    private static LuaValue newPreFileValue(LuaState L, LuaFile f) {
        L.push(LuaValue.ofUserdata(f));
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
    private static void openCheck(LuaState L, String fname, String mode) {
        LuaFile f = new LuaFile();
        L.push(LuaValue.ofUserdata(f));
        LuaAux.setMetatableByName(L, FILE_HANDLE);
        f.closef = IoLib::ioFclose;
        if (!openInto(f, fname, mode)) {
            throw L.libError("cannot open file '" + fname + "' ("
                    + LuaAux.strError(f.errno) + ")");
        }
    }

    /** 按 mode 打开真实文件；失败时把 errno 记进 {@code f} 并返回 false */
    private static boolean openInto(LuaFile f, String fname, String mode) {
        char base = mode.charAt(0);
        boolean plus = mode.indexOf('+') >= 0;
        f.readable = (base == 'r') || plus;
        f.writable = (base != 'r') || plus;
        f.append = (base == 'a');
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

    /** {@code io.open} */
    private static int ioOpen(LuaState L) {
        String filename = LuaAux.checkString(L, 1).toJavaString();
        String mode = LuaAux.optString(L, 2, LuaString.latin1("r")).toJavaString();
        LuaFile f = new LuaFile();
        newFile(L, f);
        LuaAux.argCheck(L, checkMode(mode), 2, "invalid mode");
        f.err = false;
        if (!openInto(f, filename, mode)) {
            LuaAux.fileResult(L, false, filename, f.errno);
            return 3;
        }
        return 1;
    }

    /** {@code io.popen} */
    private static int ioPopen(LuaState L) {
        String filename = LuaAux.checkString(L, 1).toJavaString();
        String mode = LuaAux.optString(L, 2, LuaString.latin1("r")).toJavaString();
        LuaFile f = new LuaFile();
        L.push(LuaValue.ofUserdata(f));
        LuaAux.setMetatableByName(L, FILE_HANDLE);
        LuaAux.argCheck(L, checkModeP(mode), 2, "invalid mode");
        f.err = false;
        boolean forRead = mode.charAt(0) == 'r';
        try {
            java.lang.Process p = new ProcessBuilder("sh", "-c", filename).start();
            f.proc = p;
            if (forRead) {
                f.in = p.getInputStream();
                f.readable = true;
            } else {
                f.out = p.getOutputStream();
                f.writable = true;
            }
        } catch (IOException e) {
            f.err = true;
            f.errno = LuaAux.errnoOf(e);
        }
        f.closef = IoLib::ioPclose;
        if (f.err) {
            LuaAux.fileResult(L, false, filename, f.errno);
            return 3;
        }
        return 1;
    }

    /** {@code io_pclose} */
    private static int ioPclose(LuaState L) {
        LuaFile p = toLStream(L);
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
        LuaAux.execResult(L, stat);
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
                openCheck(L, s.toJavaString(), mode);
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
            String filename = LuaAux.checkString(L, 1).toJavaString();
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

    /** {@code read_chars} */
    private static boolean readChars(LuaState L, LuaFile f, long n) {
        byte[] buf = new byte[(int) n];
        int nr = f.read(buf, 0, buf.length);
        L.push(LuaValue.ofString(L.newString(buf, 0, Math.max(nr, 0))));
        return nr > 0;
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
            throw L.libError(L.stack[L.top - n + 1].asString().toJavaString());
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
     * 参数照抄校验（模式名必须是 {@code no} / {@code full} / {@code line}），
     * 但不真的改缓冲策略——见类注释第 1 条。
     */
    private static int fSetvbuf(LuaState L) {
        toFile(L);
        LuaAux.checkOption(L, 2, null, new String[] { "no", "full", "line" });
        LuaAux.optInteger(L, 3, LUAL_BUFFERSIZE);
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
