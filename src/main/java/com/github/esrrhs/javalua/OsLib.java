package com.github.esrrhs.javalua;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * os 库 —— 对应 {@code loslib.c}。
 * <p>
 * 这一整块是<b>平台相关</b>的，翻译的重点不是「照抄 C」，而是把 C 那些依赖
 * libc 的行为换成 Java 里语义等价的东西，并且<b>如实标出哪里对不上</b>：
 * <ul>
 *   <li>{@code os.date} 要自己实现一套 {@code strftime}——Java 没有。转换符
 *       集合照抄 {@code LUA_STRFTIMEOPTIONS}（含 {@code E} / {@code O} 修饰），
 *       连「不认识的转换符报 {@code invalid conversion specifier '%XX'}」都一致。</li>
 *   <li>{@code os.time} 要自己实现 {@code mktime} 的<b>归一化</b>：
 *       {@code {year=2023, month=13, day=32, hour=25}} 会被正规化成
 *       {@code 2024-02-02 02:00}，并且<b>回写进传入的那张表</b>。
 *       Java 的 {@link GregorianCalendar} 开了 lenient 之后正好做这件事。</li>
 *   <li>历法切换要关掉：{@code mktime} 用的是前推格里历，而
 *       {@code GregorianCalendar} 默认在 1582-10-15 之前走儒略历，不关会差好几天。</li>
 * </ul>
 * <p>
 * 原来对不上的三处，现在的状态：
 * <ol>
 *   <li><b>{@code setlocale}</b>：已经能真做——JNI 调 C 的 {@code setlocale}，
 *       查询与设置都走它（见 {@link #setlocale}）。native 层装不上时才退回
 *       「只认 {@code nil} / {@code "C"} / {@code "POSIX"}」。</li>
 *   <li><b>{@code os.clock}</b>：仍然取<b>当前线程</b>的 CPU 时间
 *       （{@code ThreadMXBean}），不是整个进程的——本实现本来就只在一条线程上跑，
 *       这一条是刻意保留的偏离。</li>
 *   <li><b>{@code os.execute} 的信号</b>：已经能报——JNI 调 {@code waitpid}
 *       拿原始 status，被信号杀死给 {@code ("signal", 编号)}（见
 *       {@link #execute}）。native 层装不上时退回 {@code Process.waitFor()}，
 *       那只有退出码，被信号杀死的进程会报成 {@code ("exit", 128+n)}。</li>
 * </ol>
 */
public final class OsLib {

    private OsLib() {
    }

    /* ==================== 注册 ==================== */

    public static void open(LuaState L) {
        LuaTable o = new LuaTable();
        LuaAux.setFn(o, "clock", OsLib::clock);
        LuaAux.setFn(o, "date", OsLib::date);
        LuaAux.setFn(o, "difftime", OsLib::difftime);
        LuaAux.setFn(o, "execute", OsLib::execute);
        LuaAux.setFn(o, "exit", OsLib::exit);
        LuaAux.setFn(o, "getenv", OsLib::getenv);
        LuaAux.setFn(o, "remove", OsLib::remove);
        LuaAux.setFn(o, "rename", OsLib::rename);
        LuaAux.setFn(o, "setlocale", OsLib::setlocale);
        LuaAux.setFn(o, "time", OsLib::time);
        LuaAux.setFn(o, "tmpname", OsLib::tmpname);
        LuaAux.registerLib(L, "os", o);
    }

    /* ==================== 日历 ==================== */

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    /** 前推格里历的「切换点」：设成下界就是全程格里历，与 {@code mktime} 一致 */
    private static final Date PURE_GREGORIAN = new Date(Long.MIN_VALUE);

    /** {@code time_t} 是 64 位，但秒 → 毫秒会溢出，先挡一道 */
    private static final long MAX_SEC = Long.MAX_VALUE / 1000;

    private static GregorianCalendar newCal(TimeZone tz) {
        GregorianCalendar c = new GregorianCalendar(tz);
        c.setGregorianChange(PURE_GREGORIAN);
        c.setLenient(true);
        return c;
    }

    /**
     * {@code l_gmtime} / {@code l_localtime}：把 epoch 秒变成一个 {@code struct tm}。
     * <p>
     * 参考实现给不出结果时（{@code gmtime} 返回 NULL）报
     * {@code date result cannot be represented in this installation}；这里用
     * 秒→毫秒的溢出挡同一件事。
     */
    private static Calendar tm(long sec, boolean utc) {
        if (sec > MAX_SEC || sec < -MAX_SEC) {
            return null;
        }
        Calendar c = newCal(utc ? UTC : TimeZone.getDefault());
        c.setTimeInMillis(sec * 1000);
        return c;
    }

    private static LuaError badDate(LuaState L) {
        return L.libError("date result cannot be represented in this installation");
    }

    private static LuaError badTime(LuaState L) {
        return L.libError("time result cannot be represented in this installation");
    }

    /* ==================== 字段读写 ==================== */

    /** 走 {@code __index} 的字段读（{@code lua_getfield} 会走） */
    private static LuaValue field(LuaState L, LuaValue t, String key) {
        int f = L.top;
        L.ensureSlots(f + 1);
        Vm.getTable(L, t, LuaValue.ofString(LuaString.latin1(key)), f);
        return new LuaValue(L.stack[f]);
    }

    /** 走 {@code __newindex} 的字段写（{@code lua_setfield} 会走） */
    private static void setField(LuaState L, LuaValue t, String key, LuaValue v) {
        Vm.setTable(L, t, LuaValue.ofString(LuaString.latin1(key)), v);
    }

    /**
     * {@code loslib.c: getfield}。
     * <p>
     * 两个报错文本都不带 {@code bad argument} 前缀——C 用的是 {@code luaL_error}
     * 而不是 {@code luaL_argerror}，别套错。
     */
    private static int getField(LuaState L, LuaValue t, String key, int def, int delta) {
        LuaValue v = field(L, t, key);
        /*
         * C 这里是 {@code lua_tointegerx}，<b>带字符串转换</b>
         * （{@code cvt2num} 让可解析的字符串也走 {@code luaV_tointeger}）。
         * 实测 {@code os.time{year="2000", month=1, day=1}} 在参考实现里就是
         * 2000 年；只认整数／浮点会把它报成 {@code field 'year' is not an
         * integer}。库函数参数走 {@code luaL_check*} 那一套（会做类型转换），
         * 与 VM 算术指令刻意不转换的做法相反。
         */
        Long i = Vm.toInteger(v, Arith.F2I_EQ);
        long res;
        if (i == null) {
            if (!v.isNil()) {
                throw L.libError("field '" + key + "' is not an integer");
            }
            if (def < 0) {
                throw L.libError("field '" + key + "' missing in date table");
            }
            res = def;
        } else {
            res = i;
            boolean ok = (res >= 0) ? (res - (long) delta <= Integer.MAX_VALUE)
                    : (Integer.MIN_VALUE + (long) delta <= res);
            if (!ok) {
                throw L.libError("field '" + key + "' is out-of-bound");
            }
            res -= delta;
        }
        return (int) res;
    }

    /** {@code getboolfield}：nil 是「没给」，返回 -1 */
    private static int getBoolField(LuaState L, LuaValue t, String key) {
        LuaValue v = field(L, t, key);
        return v.isNil() ? -1 : (v.toBoolean() ? 1 : 0);
    }

    /** {@link #setAllFields(LuaState, LuaValue, Calendar, boolean)} 的纯算术版 */
    private static void setAllFields(LuaState L, LuaValue t, long sec) {
        long[] c = epochToCivil(sec);
        setField(L, t, "year", LuaValue.ofInteger(c[0]));
        setField(L, t, "month", LuaValue.ofInteger(c[1]));
        setField(L, t, "day", LuaValue.ofInteger(c[2]));
        setField(L, t, "hour", LuaValue.ofInteger(c[3]));
        setField(L, t, "min", LuaValue.ofInteger(c[4]));
        setField(L, t, "sec", LuaValue.ofInteger(c[5]));
        setField(L, t, "yday", LuaValue.ofInteger(c[7]));
        setField(L, t, "wday", LuaValue.ofInteger(c[6]));
        setField(L, t, "isdst", LuaValue.ofBoolean(false));
    }

    /** {@code setallfields}：{@code isdst} 只在已知时才写（{@code tm_isdst < 0} 不写） */
    private static void setAllFields(LuaState L, LuaValue t, Calendar c, boolean utc) {
        TimeZone tz = utc ? UTC : TimeZone.getDefault();
        setField(L, t, "year", LuaValue.ofInteger(c.get(Calendar.YEAR)));
        setField(L, t, "month", LuaValue.ofInteger(c.get(Calendar.MONTH) + 1));
        setField(L, t, "day", LuaValue.ofInteger(c.get(Calendar.DAY_OF_MONTH)));
        setField(L, t, "hour", LuaValue.ofInteger(c.get(Calendar.HOUR_OF_DAY)));
        setField(L, t, "min", LuaValue.ofInteger(c.get(Calendar.MINUTE)));
        setField(L, t, "sec", LuaValue.ofInteger(c.get(Calendar.SECOND)));
        setField(L, t, "yday", LuaValue.ofInteger(c.get(Calendar.DAY_OF_YEAR)));
        // Lua 的 wday 是 1=星期天，正好与 Calendar 的 SUNDAY..SATURDAY = 1..7 重合
        setField(L, t, "wday", LuaValue.ofInteger(c.get(Calendar.DAY_OF_WEEK)));
        int isdst = utc ? 0
                : ((tz.getOffset(c.getTimeInMillis()) != tz.getRawOffset()) ? 1 : 0);
        setField(L, t, "isdst", LuaValue.ofBoolean(isdst != 0));
    }

    /* ==================== os.date ==================== */

    /** {@code LUA_STRFTIMEOPTIONS}，照 C 源码原样搬（{@code ||} 之后是两字符那组） */
    private static final String OPTIONS = "aAbBcCdDeFgGhHIjmMnprRStTuUVwWxXyYzZ%"
            + "||" + "EcECExEXEyEY" + "OdOeOHOIOmOMOSOuOUOVOwOWOy";

    private static int date(LuaState L) {
        LuaString fmt = LuaAux.optString(L, 1, LuaString.latin1("%c"));
        long t;
        if (L.isNoneArg(2) || L.arg1(2).isNil()) {
            t = System.currentTimeMillis() / 1000;
        } else {
            t = LuaAux.checkInteger(L, 2);
        }
        byte[] f = fmt.bytes;
        int p = 0;
        boolean utc = false;
        if (f.length > 0 && f[0] == '!') {
            utc = true;
            p = 1;
        }
        Calendar c = tm(t, utc);
        if (c == null) {
            throw badDate(L);
        }
        /*
         * C 这里是 {@code strcmp(s, "*t") == 0}，不是「以 *t 开头」—— 所以
         * {@code os.date("*t x")} 走的是 strftime 那条路，{@code *}、{@code t}、
         * 空格、{@code x} 全当字面量，结果是字符串 {@code "*t x"} 而不是表。
         * 按前缀匹配会把这类格式串错认成取表。
         * <p>
         * 另一个细节：{@code strcmp} 遇到 {@code \0} 就停，所以
         * {@code os.date("*t\0x")} 在 C 里<b>仍然是</b>取表 —— 先截到第一个 NUL
         * 再比长度，不能直接用 {@code f.length}。
         */
        int n = 0;
        while (p + n < f.length && f[p + n] != 0) {
            n++;
        }
        if (n == 2 && f[p] == '*' && f[p + 1] == 't') {
            LuaTable r = new LuaTable(0, 9);
            LuaValue tv = LuaValue.ofTable(r);
            setAllFields(L, tv, c, utc);
            L.push(tv);
            return 1;
        }
        LuaAux.Buf b = new LuaAux.Buf();
        int i = p;
        while (i < f.length) {
            if (f[i] != '%') {
                b.addChar(f[i++] & 0xFF);
                continue;
            }
            i++; // 跳过 '%'
            int[] next = new int[1];
            int spec = checkOption(L, f, i, next);
            i = next[0];
            emit(b, spec, c, utc);
        }
        L.push(LuaValue.ofString(b.result()));
        return 1;
    }

    /**
     * {@code loslib.c: checkoption}。
     * <p>
     * 报错里带的是<b>从 {@code %} 开始剩下的全部</b>，不是一个字符——
     * {@code os.date("%QQ")} 报的是 {@code '%QQ'}，照 {@code conv} 的原样输出。
     */
    private static int checkOption(LuaState L, byte[] f, int i, int[] next) {
        int end = f.length;
        int o = 0;
        int oplen = 1;
        while (o < OPTIONS.length() && oplen <= end - i) {
            if (OPTIONS.charAt(o) == '|') {
                oplen++;
                o += oplen;
                continue;
            }
            boolean match = true;
            for (int k = 0; k < oplen; k++) {
                if (i + k >= end || (f[i + k] & 0xFF) != OPTIONS.charAt(o + k)) {
                    match = false;
                    break;
                }
            }
            if (match) {
                next[0] = i + oplen;
                // E/O 修饰在 C locale 下与基础转换符等价，取第二个字符即可
                return OPTIONS.charAt(o + oplen - 1);
            }
            o += oplen;
        }
        String conv = new String(f, i, Math.max(0, end - i), StandardCharsets.ISO_8859_1);
        throw L.argError(1, "invalid conversion specifier '%" + conv + "'");
    }

    /* ==================== strftime ==================== */

    private static final String[] WDAY = { "Sunday", "Monday", "Tuesday", "Wednesday",
            "Thursday", "Friday", "Saturday" };
    private static final String[] MONTH = { "January", "February", "March", "April", "May",
            "June", "July", "August", "September", "October", "November", "December" };

    private static void addAscii(LuaAux.Buf b, String s) {
        b.addAscii(s);
    }

    private static void pad(LuaAux.Buf b, long v, int w) {
        addAscii(b, zeroPad(v, w));
    }

    private static void padSpace(LuaAux.Buf b, long v, int w) {
        String s = Long.toString(v);
        for (int k = s.length(); k < w; k++) {
            b.addChar(' ');
        }
        addAscii(b, s);
    }

    private static String zeroPad(long v, int w) {
        boolean neg = v < 0;
        String s = Long.toString(Math.abs(v));
        StringBuilder sb = new StringBuilder();
        for (int k = s.length(); k < w; k++) {
            sb.append('0');
        }
        sb.append(s);
        if (neg) {
            sb.insert(0, '-');
        }
        return sb.toString();
    }

    private static void emit(LuaAux.Buf b, int spec, Calendar c, boolean utc) {
        long ms = c.getTimeInMillis();
        int year = c.get(Calendar.YEAR);
        int mon = c.get(Calendar.MONTH) + 1; // 1..12
        int day = c.get(Calendar.DAY_OF_MONTH);
        int hour = c.get(Calendar.HOUR_OF_DAY);
        int min = c.get(Calendar.MINUTE);
        int sec = c.get(Calendar.SECOND);
        int yday = c.get(Calendar.DAY_OF_YEAR); // 1..366
        int wday = c.get(Calendar.DAY_OF_WEEK) - 1; // 0=星期天，与 tm_wday 一致
        int isoWday = (wday + 6) % 7 + 1; // 1=星期一

        switch (spec) {
        case 'a':
            addAscii(b, WDAY[wday].substring(0, 3));
            return;
        case 'A':
            addAscii(b, WDAY[wday]);
            return;
        case 'b':
        case 'h':
            addAscii(b, MONTH[mon - 1].substring(0, 3));
            return;
        case 'B':
            addAscii(b, MONTH[mon - 1]);
            return;
        case 'c': // C locale: "%a %b %e %H:%M:%S %Y"
            addAscii(b, WDAY[wday].substring(0, 3));
            b.addChar(' ');
            addAscii(b, MONTH[mon - 1].substring(0, 3));
            b.addChar(' ');
            padSpace(b, day, 2);
            b.addChar(' ');
            pad(b, hour, 2);
            b.addChar(':');
            pad(b, min, 2);
            b.addChar(':');
            pad(b, sec, 2);
            b.addChar(' ');
            addAscii(b, zeroPad(year, 4));
            return;
        case 'C':
            pad(b, year / 100, 2);
            return;
        case 'd':
            pad(b, day, 2);
            return;
        case 'D':
            pad(b, mon, 2);
            b.addChar('/');
            pad(b, day, 2);
            b.addChar('/');
            pad(b, Math.floorMod(year, 100L), 2);
            return;
        case 'e':
            padSpace(b, day, 2);
            return;
        case 'F':
            addAscii(b, zeroPad(year, 4));
            b.addChar('-');
            pad(b, mon, 2);
            b.addChar('-');
            pad(b, day, 2);
            return;
        case 'g':
            pad(b, Math.floorMod(isoYear(c), 100L), 2);
            return;
        case 'G':
            addAscii(b, zeroPad(isoYear(c), 4));
            return;
        case 'H':
            pad(b, hour, 2);
            return;
        case 'I':
            pad(b, (hour % 12 == 0) ? 12 : (hour % 12), 2);
            return;
        case 'j':
            pad(b, yday, 3);
            return;
        case 'm':
            pad(b, mon, 2);
            return;
        case 'M':
            pad(b, min, 2);
            return;
        case 'n':
            b.addChar('\n');
            return;
        case 'p':
            addAscii(b, hour < 12 ? "AM" : "PM");
            return;
        case 'r': // "%I:%M:%S %p"
            pad(b, (hour % 12 == 0) ? 12 : (hour % 12), 2);
            b.addChar(':');
            pad(b, min, 2);
            b.addChar(':');
            pad(b, sec, 2);
            b.addChar(' ');
            addAscii(b, hour < 12 ? "AM" : "PM");
            return;
        case 'R':
            pad(b, hour, 2);
            b.addChar(':');
            pad(b, min, 2);
            return;
        case 'S':
            pad(b, sec, 2);
            return;
        case 't':
            b.addChar('\t');
            return;
        case 'T':
            pad(b, hour, 2);
            b.addChar(':');
            pad(b, min, 2);
            b.addChar(':');
            pad(b, sec, 2);
            return;
        case 'u':
            pad(b, isoWday, 1);
            return;
        case 'U': // 星期天为每周第一天
            pad(b, (yday - 1 + 7 - wday) / 7, 2);
            return;
        case 'V':
            pad(b, isoWeek(c)[0], 2);
            return;
        case 'w':
            pad(b, wday, 1);
            return;
        case 'W': // 星期一为每周第一天
            pad(b, (yday - 1 + 7 - ((wday + 6) % 7)) / 7, 2);
            return;
        case 'x': // C locale: "%m/%d/%y"
            pad(b, mon, 2);
            b.addChar('/');
            pad(b, day, 2);
            b.addChar('/');
            pad(b, Math.floorMod(year, 100L), 2);
            return;
        case 'X': // C locale: "%H:%M:%S"
            pad(b, hour, 2);
            b.addChar(':');
            pad(b, min, 2);
            b.addChar(':');
            pad(b, sec, 2);
            return;
        case 'y':
            pad(b, Math.floorMod(year, 100L), 2);
            return;
        case 'Y':
            addAscii(b, zeroPad(year, 4));
            return;
        case 'z': {
            TimeZone tz = utc ? UTC : TimeZone.getDefault();
            int off = utc ? 0 : tz.getOffset(ms);
            b.addChar(off < 0 ? '-' : '+');
            int a = Math.abs(off) / 60000;
            pad(b, a / 60, 2);
            pad(b, a % 60, 2);
            return;
        }
        case 'Z': {
            TimeZone tz = utc ? UTC : TimeZone.getDefault();
            String n = utc ? "UTC"
                    : tz.getDisplayName(tz.inDaylightTime(new Date(ms)), TimeZone.SHORT);
            addAscii(b, n);
            return;
        }
        case '%':
            b.addChar('%');
            return;
        default:
            b.addChar('%');
            b.addChar(spec);
            return;
        }
    }

    /* ==================== ISO 周 / 年 ==================== */

    /** 一年的 ISO 周数：52 或 53 */
    private static int isoWeeksInYear(int y) {
        long p = pOf(y);
        return (p == 4 || pOf(y - 1) == 3) ? 53 : 52;
    }

    /** {@code p(y) = (y + y/4 - y/100 + y/400) mod 7} */
    private static long pOf(long y) {
        long r = y + y / 4 - y / 100 + y / 400;
        return Math.floorMod(r, 7L);
    }

    private static long[] isoWeek(Calendar c) {
        int y = c.get(Calendar.YEAR);
        int ord = c.get(Calendar.DAY_OF_YEAR);
        int wd = (c.get(Calendar.DAY_OF_WEEK) - 1 + 6) % 7 + 1; // ISO: 1=周一..7=周日
        long w = (ord - wd + 10) / 7;
        if (w < 1) {
            return new long[] { isoWeeksInYear(y - 1), y - 1 };
        }
        if (w > isoWeeksInYear(y)) {
            return new long[] { 1, y + 1 };
        }
        return new long[] { w, y };
    }

    private static int isoYear(Calendar c) {
        return (int) isoWeek(c)[1];
    }

    /* ==================== os.time ==================== */

    private static int time(LuaState L) {
        if (L.isNoneArg(1) || L.arg1(1).isNil()) {
            L.push(LuaValue.ofInteger(System.currentTimeMillis() / 1000));
            return 1;
        }
        LuaAux.checkType(L, 1, Lua.TTABLE);
        LuaValue t = L.arg1(1);
        int tmYear = getField(L, t, "year", -1, 1900);
        int tmMon = getField(L, t, "month", -1, 1);
        int tmDay = getField(L, t, "day", -1, 0);
        int tmHour = getField(L, t, "hour", 12, 0);
        int tmMin = getField(L, t, "min", 0, 0);
        int tmSec = getField(L, t, "sec", 0, 0);
        int isdst = getBoolField(L, t, "isdst");

        TimeZone tz = TimeZone.getDefault();
        long year = (long) tmYear + 1900;
        // 先把这六个字段当成 UTC 算出一个「纯本地时刻」，再按偏移量折回 epoch
        // tmMon 是 0 起（Calendar 的规矩），{@code civilToEpoch} 收的是 1 起的月份
        long asUtcSec = civilToEpoch(year, tmMon + 1, tmDay, tmHour, tmMin, tmSec);

        long sec;
        if (isdst < 0) {
            /*
             * 自动判定：交给 Calendar，它会按当时生效的规则挑偏移量。
             * 但 Calendar 存的是<b>毫秒</b>，年份一大就溢出（见
             * {@link #civilToEpoch}），所以撑不住的年份退成「只有原始偏移」——
             * 那种年份本来也不存在任何夏令时规则。
             */
            if (year > 9999 || year < -9999) {
                sec = asUtcSec - tz.getRawOffset() / 1000;
            } else {
                GregorianCalendar c = newCal(tz);
                c.clear();
                c.set((int) year, tmMon, tmDay, tmHour, tmMin, tmSec);
                sec = c.getTimeInMillis() / 1000;
            }
        } else {
            long dst = tz.getDSTSavings();
            if (dst == 0) {
                // 这个时区根本没有夏令时规则，但 C 的 mktime 收到 tm_isdst=1
                // 仍然照惯例挪一小时（实测如此）
                dst = 3600000L;
            }
            sec = asUtcSec - tz.getRawOffset() / 1000 - (isdst == 1 ? dst / 1000 : 0);
        }

        Calendar out = tm(sec, false);
        if (out != null) {
            setAllFields(L, t, out, false); // mktime 之后字段被正规化并回写
        } else {
            /*
             * Calendar 存毫秒，{@code |sec|} 一大就装不下（{@link #MAX_SEC}）。但 C 的
             * {@code time_t} 是 64 位，这一档 mktime 照样给得出结果——所以这里
             * <b>不能</b>跟着报 badTime，改用纯算术回写。
             */
            setAllFields(L, t, sec);
        }
        if (sec == -1) {
            throw badTime(L);
        }
        L.push(LuaValue.ofInteger(sec));
        return 1;
    }

    /**
     * 前推格里历的 civil → epoch 秒（Howard Hinnant 的算法，全程 {@code long}）。
     * <p>
     * 为什么要另写一份而不继续用 {@link GregorianCalendar}：{@code Calendar} 存的是
     * <b>毫秒</b>，年份到 {@code 2^31} 那一档 {@code getTimeInMillis()} 直接溢出、
     * 折回一个莫名其妙的负数。实测 {@code os.time{year=2147483647}} 参考实现给
     * {@code 67767976201968000}，我们原来给 {@code -6019000092870206}；而
     * {@code 67767976201968000} 正好等于 {@code 86400 * days_from_civil} 减时区
     * 偏移——<b>纯算术</b>，两边 libc 都一样。
     */
    private static long civilToEpoch(long y, long m, long d, long h, long mi, long s) {
        long mm = m - 1; // 0..11 之外的月份先折进年份
        long yy = y + Math.floorDiv(mm, 12);
        long m0 = Math.floorMod(mm, 12) + 1; // 回到 1..12
        long ya = yy - ((m0 <= 2) ? 1 : 0);
        long era = Math.floorDiv(ya, 400);
        long yoe = ya - era * 400; // [0, 399]
        long doy = (153 * (m0 + ((m0 > 2) ? -3 : 9)) + 2) / 5 + d - 1;
        long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        long days = era * 146097 + doe - 719468;
        return days * 86400 + h * 3600 + mi * 60 + s;
    }

    /** {@link #civilToEpoch} 的逆；返回 {year, month, day, hour, min, sec, wday, yday} */
    private static long[] epochToCivil(long sec) {
        long days = Math.floorDiv(sec, 86400L);
        long rem = Math.floorMod(sec, 86400L);
        long z = days + 719468;
        long era = Math.floorDiv(z, 146097);
        long doe = z - era * 146097; // [0, 146096]
        long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365; // [0, 399]
        long y = yoe + era * 400;
        long doy = doe - (365 * yoe + yoe / 4 - yoe / 100); // [0, 365]
        long mp = (5 * doy + 2) / 153; // [0, 11]
        long d = doy - (153 * mp + 2) / 5 + 1; // [1, 31]
        long m = mp + ((mp < 10) ? 3 : -9); // [1, 12]
        y += (m <= 2) ? 1 : 0;
        long wday = Math.floorMod(days + 4, 7) + 1; // 1970-01-01 是周四；Lua 的 wday 1=周日
        long yday = days - civilToEpoch(y, 1, 1, 0, 0, 0) / 86400 + 1;
        return new long[] { y, m, d, rem / 3600, (rem / 60) % 60, rem % 60, wday, yday };
    }

    /* ==================== 其余 ==================== */

    private static int difftime(LuaState L) {
        long t1 = LuaAux.checkInteger(L, 1);
        long t2 = LuaAux.checkInteger(L, 2);
        L.push(LuaValue.ofFloat((double) (t1 - t2)));
        return 1;
    }

    /**
     * {@code os.clock}。
     * <p>
     * C 的 {@code clock()} 是<b>进程</b> CPU 时间；Java 拿得到的最接近的东西是
     * 当前线程的时间（{@code ThreadMXBean}）。本实现本来就只在一条线程上跑脚本，
     * 差别只在 JVM 自己的后台线程上。
     */
    private static int clock(LuaState L) {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        long ns = bean.isCurrentThreadCpuTimeSupported() ? bean.getCurrentThreadCpuTime() : -1;
        L.push(LuaValue.ofFloat(ns < 0 ? 0.0 : ns / 1e9));
        return 1;
    }

    private static int getenv(LuaState L) {
        // getenv 的参数是 const char*：在第一个 \0 处砍断
        String v = System.getenv(LuaAux.checkString(L, 1).toCString().toJavaString());
        if (v == null) {
            L.pushNil();
        } else {
            L.push(LuaValue.ofString(L.newString(v.getBytes(StandardCharsets.UTF_8))));
        }
        return 1;
    }

    /**
     * {@code os.execute}。
     * <p>
     * 无参时 {@code system(NULL)} 问「有没有 shell」，这里一律给 true。
     * 有参时走 {@code sh -c}，并照 {@code luaL_execresult} 给三元组
     * {@code (true|fail, "exit"|"signal", 状态码)}——注意<b>成功时也返回三个值</b>，
     * 不是只返回 true。
     * <p>
     * 命令名写 {@code "sh"} 而不是 {@code "/bin/sh"}：C 的 {@code system()} 是
     * {@code execl("/bin/sh", "sh", "-c", cmd)}，<b>argv[0] 是 {@code "sh"}</b>。
     * shell 的错误信息用 argv[0] 打头（{@code sh: 42: command not found}），
     * 写成全路径就会变成 {@code /bin/sh: ...}，和参考实现对不上。
     * <p>
     * 子进程由 shim 的 {@code posix_spawn} 起（{@link NativeBridge#cSpawn}），
     * {@code argv[0]} 同样给 {@code "sh"}；退出状态用 {@code waitpid} 拿原始
     * status，好区分正常退出与被信号杀死。走 {@code ProcessBuilder} 拿不到
     * 原始 status，理由见 {@link #executeSpawn}。
     */
    private static int execute(LuaState L) {
        if (L.isNoneArg(1) || L.arg1(1).isNil()) {
            L.push(LuaValue.ofBoolean(true));
            return 1;
        }
        String cmd = LuaAux.checkString(L, 1).toCString().toJavaString();
        if (NativeBridge.AVAILABLE) {
            return executeSpawn(L, cmd);
        }
        return executeJava(L, cmd);
    }

    /**
     * shim 在时的路：{@code posix_spawn} 由 C 起子进程，{@code waitpid} 由我们
     * 独占，于是 {@code ("signal", n)} 这一档是<b>稳定</b>的。
     * <p>
     * 为什么不能用 {@code ProcessBuilder}：那样孩子是 JVM 的，JDK 为了
     * {@code Process.onExit()} 起的 reaper 线程会抢同一个 {@code waitpid}——
     * 抢输就只能退回 {@code waitFor()} 那个 {@code 128+n}，抢赢则反过来把 JVM
     * 那侧掏空（{@code waitFor()} 回来 0）。实测同一句
     * {@code os.execute("kill -TERM $$")} 二十次里十五次给错的答案。
     */
    private static int executeSpawn(LuaState L, String cmd) {
        long[] fds = new long[3];
        long pid = NativeBridge.cSpawn(cmd.getBytes(StandardCharsets.UTF_8),
                true, true, false, fds);
        if (pid < 0) {
            /* C 那边是 system() 返回 -1，luaL_execresult 拿 errno 说话 */
            LuaAux.fileResult(L, false, null, NativeBridge.cGetErrno());
            return 3;
        }
        Thread g1 = fdGobbler(fds[0], L);
        Thread g2 = fdGobbler(fds[1], L);
        g1.start();
        g2.start();
        long[] statusOut = new long[1];
        int wr = NativeBridge.cWaitpid(pid, statusOut);
        try {
            g1.join();
            g2.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LuaAux.fileResult(L, false, null, 4); // EINTR
            return 3;
        }
        if (wr < 0) {
            // 收割失败（孩子不是我们的、或已被收走）：C 的 system() 这里也会给 -1
            pushExit(L, -1);
            return 3;
        }
        long[] decoded = new long[4];
        NativeBridge.cDecodeStatus((int) statusOut[0], decoded);
        LuaAux.waitStatus(L, decoded[0] != 0, (int) decoded[1],
                decoded[2] != 0, (int) decoded[3]);
        return 3;
    }

    /** {@code ("exit", n)} 那一档：{@code n == 0} 时第一个返回值是 {@code true} */
    private static void pushExit(LuaState L, int code) {
        if (code == 0) {
            L.push(LuaValue.ofBoolean(true));
        } else {
            L.pushNil();
        }
        L.push(LuaValue.ofString(LuaString.latin1("exit")));
        L.push(LuaValue.ofInteger(code));
    }

    /**
     * shim 不在时的路：只有 {@code waitFor()} 那个退出码，被信号杀死的进程
     * 一律报成 {@code ("exit", 128+n)}——这是 README 里认下来的偏离
     * （没有 {@code waitpid} 就没有原始 status，Java 也不给）。
     */
    private static int executeJava(LuaState L, String cmd) {
        Process p;
        try {
            p = new ProcessBuilder("sh", "-c", cmd).start();
            // 子进程的输出必须抄到 L.out 上，不能用 inheritIO：后者走的是
            // 进程的文件描述符，脚本里换掉的输出流接不到，于是
            // os.execute("nosuch") 连「command not found」都看不见
            Thread g1 = gobbler(p.getInputStream(), L);
            Thread g2 = gobbler(p.getErrorStream(), L);
            g1.start();
            g2.start();
            int stat = p.waitFor();
            g1.join();
            g2.join();
            pushExit(L, stat);
            return 3;
        } catch (IOException e) {
            LuaAux.fileResult(L, false, null, LuaAux.errnoOf(e));
            return 3;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LuaAux.fileResult(L, false, null, 4); // EINTR
            return 3;
        }
    }

    /**
     * {@link #fdGobbler} 的 {@code read} 缓冲大小；与 {@link #gobbler} 一致。
     */
    private static final int FD_CHUNK = 4096;

    /** 从 C 那侧的管道 fd 上抄到 {@code L.out}，读到 EOF 就关 fd */
    private static Thread fdGobbler(long fd, LuaState L) {
        Thread t = new Thread(() -> {
            if (fd < 0) {
                return;
            }
            byte[] buf = new byte[FD_CHUNK];
            for (;;) {
                int n = NativeBridge.cPipeRead(fd, buf);
                if (n <= 0) {
                    break;   // 0 = EOF；负数是出错（含别的线程已关这个 fd）
                }
                L.out.write(buf, 0, n);
                L.out.flush();
            }
            NativeBridge.cCloseFd(fd);
        });
        t.setName("lua-exec-fd");
        t.setDaemon(true);
        return t;
    }

    /** 把子进程的一条输出流抄到 {@code L.out} 上，并负责关流 */
    private static Thread gobbler(java.io.InputStream in, LuaState L) {
        Thread t = new Thread(() -> {
            byte[] buf = new byte[4096];
            try (java.io.InputStream s = in) {
                for (;;) {
                    int n = s.read(buf);
                    if (n < 0) {
                        break;
                    }
                    L.out.write(buf, 0, n);
                }
            } catch (IOException ignored) {
                // 子进程把管道关了就是 EOF，不当错误
            }
            L.out.flush();
        });
        t.setDaemon(true);
        return t;
    }

    private static int remove(LuaState L) {
        // remove 的参数是 const char*：在第一个 \0 处砍断。不砍的话 Java 的
        // Paths.get 会抛 InvalidPathException —— 而它不是 LuaError，pcall 拦不住，
        // 会整颗逃逸成 Java 异常（实测 os.remove("a\0b") 直接崩掉进程）。
        LuaString pname = LuaAux.checkString(L, 1).toCString();
        String path = pname.toJavaString();
        /*
         * 空路径：C 的 remove("") 走 unlink 直接给 ENOENT（实测
         * os.remove("") -> nil, ": No such file or directory", 2），
         * 而 Java 的 Paths.get("") 抛的是别的异常、落进 errnoOf 的兜底成了 EIO。
         * 与 loadfile("") 是同一类「C 与 Java 判路径不一致」的角落。
         */
        if (path.isEmpty()) {
            LuaAux.fileResult(L, false, pname, 2); // ENOENT
            return 3;
        }
        try {
            Files.delete(Paths.get(path));
            L.push(LuaValue.ofBoolean(true));
            return 1;
        } catch (IOException | RuntimeException e) {
            LuaAux.fileResult(L, false, pname, LuaAux.errnoOf(e));
            return 3;
        }
    }

    private static int rename(LuaState L) {
        String from = LuaAux.checkString(L, 1).toCString().toJavaString();
        String to = LuaAux.checkString(L, 2).toCString().toJavaString();
        /*
         * 目标与源<b>类型冲突</b>的那两个形状必须<b>自己先拦下来</b>：Java 的
         * {@code Files.move} 比 C 的 {@code rename(2)} 宽松得多 ——「文件改名到
         * 目录上」「目录改名到文件上」它都<b>成功</b>（前者是把文件挪进那个目录），
         * 而 C 一律失败。不拦就是「参考给 nil、我们给 true」这种最坏的分歧。
         * <p>
         * 但拦下来之后 errno 报哪个，**是内核定的，不由 POSIX 统一**：
         * macOS 给 {@code Is a directory, 21} / {@code Not a directory, 20}，
         * Linux 的 {@code os.rename(文件, 非空目录)} 给
         * {@code Directory not empty, 39}。两边都是各自内核的答案，都不是错
         * —— 所以差分用例只比「它失败了」，不比失败成哪个 errno（约定 #98）。
         * 试过撤掉这道预检、一律交给 {@code errnoOf} 反查，结果更糟：Java 根本
         * 不报错，直接成功。
         */
        if (from.isEmpty() || to.isEmpty()) {
            LuaAux.fileResult(L, false, null, 2); // ENOENT
            return 3;
        }
        java.io.File sf = new java.io.File(from);
        java.io.File df = new java.io.File(to);
        int en = 0;
        if (!sf.exists()) {
            en = 2; // ENOENT
        } else if (sf.isFile() && df.isDirectory()) {
            en = 21; // EISDIR（Linux 上是 ENOTEMPTY，见上）
        } else if (sf.isDirectory() && df.isFile()) {
            en = 20; // ENOTDIR
        }
        if (en != 0) {
            LuaAux.fileResult(L, false, null, en);
            return 3;
        }
        /*
         * POSIX 的 {@code rename(x, x)}：<b>成功且什么都不做</b>。
         * 交给 {@code Files.move} 会变成「把目标当已存在项先删再挪」，
         * 而源和目标又是同一个——实测那样做会把目录自己删掉。
         * 判同一文件用 {@code Files.isSameFile} 而不是比字符串路径：
         * {@code "au_tmp/e"} 与 {@code "au_tmp/./e"} 是同一个东西。
         */
        try {
            if (df.exists() && Files.isSameFile(sf.toPath(), df.toPath())) {
                L.push(LuaValue.ofBoolean(true));
                return 1;
            }
        } catch (IOException ignored) {
            // 判不出来就照下面的路走
        }
        /*
         * 目录 → 目录：POSIX 要求目标<b>是空目录</b>（否则 ENOTEMPTY）。
         * <p>
         * 这一段必须自己扛，不能交给 {@code Files.move(..., REPLACE_EXISTING)}：
         * 它在这个形状上<b>时好时坏</b>：目标明明是空目录，却有时候抛
         * {@code DirectoryNotEmptyException}，报出来就成了
         * {@code Directory not empty | 66}，而 C 那边稳稳成功。
         * 全量跑里约 1/7 会红，单独重跑又一连十几次全绿，是典型的「赌 Java」。
         * 这里按 POSIX 来：目标空就先摘掉再挪，不空就自己报 66。
         */
        if (df.isDirectory()) {
            String[] kids = df.list();
            if (kids == null || kids.length > 0) {
                LuaAux.fileResult(L, false, null, 66); // ENOTEMPTY
                return 3;
            }
            try {
                Files.delete(df.toPath());
                Files.move(sf.toPath(), df.toPath());
                L.push(LuaValue.ofBoolean(true));
                return 1;
            } catch (IOException | RuntimeException e) {
                LuaAux.fileResult(L, false, null, LuaAux.errnoOf(e));
                return 3;
            }
        }
        try {
            Files.move(sf.toPath(), df.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            L.push(LuaValue.ofBoolean(true));
            return 1;
        } catch (IOException | RuntimeException e) {
            LuaAux.fileResult(L, false, null, LuaAux.errnoOf(e));
            return 3;
        }
    }

    /**
     * {@code os.tmpname}：C 默认是 {@code mkstemp("/tmp/lua_XXXXXX")}。
     * <p>
     * 目录要写死 {@code /tmp}：Java 的 {@code java.io.tmpdir} 在 macOS 上是
     * {@code /var/folders/...}，跟参考实现给的路径对不上。
     */
    private static int tmpname(LuaState L) {
        try {
            File dir = new File("/tmp");
            if (!dir.isDirectory()) {
                dir = null; // 退回 java.io.tmpdir
            }
            File f = File.createTempFile("lua_", "", dir);
            L.push(LuaValue.ofString(L.newString(
                    f.getAbsolutePath().getBytes(StandardCharsets.UTF_8))));
            return 1;
        } catch (IOException e) {
            throw L.libError("unable to generate a unique filename");
        }
    }

    private static final String[] CATS = { "all", "collate", "ctype", "monetary", "numeric",
            "time" };

    /**
     * {@code os.setlocale}。
     * <p>
     * 直接调 C 的 {@code setlocale}：Java 没有进程级 locale，只能走 JNI。
     * 查询（{@code locale=nil}）和设置都走同一条路——C 的 {@code setlocale}
     * 在 {@code locale=NULL} 时就是查询。
     * <p>
     * category 映射：{@code "all"} → {@code LC_ALL}(0)，其余按名字查表。
     * 设置失败（C 返回 {@code NULL}）时推 nil，成功时推 C 返回的 locale 名字。
     * <p>
     * 新进程基线的处理见 {@link LuaState#localeBaseline}：脚本还没碰 locale 时，
     * 查询不能把 JVM 启动时塞进 libc 的宿主 locale 漏出去。
     * <p>
     * native 层装不上时退成「只有 {@code "C"} / {@code "POSIX"} 认」：查询答
     * {@code "C"}，其余 locale 一律 nil —— Java 没有进程级 locale，做不了真的切换。
     */
    private static int setlocale(LuaState L) {
        String loc = null;
        if (!L.isNoneArg(1) && !L.arg1(1).isNil()) {
            loc = LuaAux.checkString(L, 1).toCString().toJavaString();
        }
        int catIdx = LuaAux.checkOption(L, 2, "all", CATS);
        int cat = catToLc(catIdx);
        if (!NativeBridge.AVAILABLE) {
            if (loc == null) {
                L.push(LuaValue.ofString(LuaString.latin1("C")));
            } else if (loc.equals("C") || loc.equals("POSIX")) {
                L.push(LuaValue.ofString(LuaString.latin1("C")));
            } else {
                L.pushNil();
            }
            return 1;
        }
        if (!L.localeBaseline) {
            if (loc == null) {
                L.push(LuaValue.ofString(LuaString.utf8("C")));
                return 1;
            }
            byte[] c = NativeBridge.cSetlocale(catToLc(0),
                    "C".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (c != null) {
                L.localeBaseline = true;
            }
        }
        byte[] locBytes = loc != null ? loc.getBytes(java.nio.charset.StandardCharsets.UTF_8) : null;
        byte[] result = NativeBridge.cSetlocale(cat, locBytes);
        if (result == null) {
            L.pushNil();
        } else {
            L.push(LuaValue.ofString(LuaString.of(result)));
        }
        return 1;
    }

    /**
     * Lua 的 category 下标（{@code checkOption} 返回的 0-based 索引）映射到 C 的
     * {@code LC_*} 常量。顺序必须和 {@link #CATS} 数组一致。
     */
    private static int catToLc(int idx) {
        // CATS = { "all", "collate", "ctype", "monetary", "numeric", "time" }
        // macOS/Linux: LC_ALL=0, LC_COLLATE=1, LC_CTYPE=2, LC_MONETARY=3, LC_NUMERIC=4, LC_TIME=5
        return idx; // 刚好一一对应
    }

    private static int exit(LuaState L) {
        int status;
        LuaValue a = L.arg1(1);
        if (a.isBoolean()) {
            status = a.toBoolean() ? 0 : 1;
        } else {
            status = (int) LuaAux.optInteger(L, 1, 0);
        }
        if (!L.isNoneArg(2) && L.arg1(2).toBoolean()) {
            L.close();
        }
        System.exit(status);
        return 0;
    }
}
