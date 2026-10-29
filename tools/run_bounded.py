#!/usr/bin/env python3
"""给一条命令加「有界超时 + 挂死现场取证」。

用法:

    python3 tools/run_bounded.py --seconds 2400 -- ./mvnw -B test -Djacoco.check.skip=true

为什么光有超时不够
------------------
2026-10 有一次 conformance job 的第 11 步（pure-Java io path）从 09:34Z 起
跑满约 6 小时，直到 GitHub 的硬上限才被取消。这期间日志一行都拿不到（gh 要等
job 结束才有日志），于是既不知道卡在哪条用例、也不知道卡在哪个线程，只能整批
重跑。GitHub 自己的 timeout-minutes 是同一类死法：到点直接取消 step，不留现场。

这里做的是「到点先取证、再收尸」：

  1) 到点对整个进程组发 SIGQUIT —— 组里每个 JVM 都会把线程栈打到自己的 stderr，
     顺着 mvnw 的输出进 CI 日志；
  2) 再用 jstack 逐个补一份 —— 不依赖 surefire 有没有把 fork 的 stdout/stderr
     转发回 Maven；
  3) 打印超时那一刻的进程清单（谁在跑、已经跑了多久）；
  4) 然后 SIGTERM / SIGKILL 把整组收干净，退出码 124（与 coreutils 的 timeout 一致，
     CI 里一眼能认出「这是超时，不是用例失败」）。

进程组是关键：`./mvnw` 自己是个 shell，它 fork 出 Maven 的 JVM，surefire 再 fork
出跑测试的 JVM —— 它们都在同一个进程组里（Popen(start_new_session=True) 建组），
killpg 一发全中，不用去猜"卡住的是哪一个 pid"。
"""

import argparse
import os
import shlex
import shutil
import signal
import subprocess
import sys
import time

# 与 coreutils 的 timeout 保持一致：超时退出码 124，好和用例失败的 1 区分开。
EXIT_TIMEOUT = 124


def group_processes(pgid):
    """列出同一进程组里的进程：(pid, 已运行时长, 命令行)。"""
    try:
        out = subprocess.run(
            ["ps", "-eo", "pid=,pgid=,etime=,command="],
            capture_output=True, text=True, timeout=30,
        ).stdout
    except Exception:  # ps 不在、或超时：取证尽力而为，绝不能因此再挂一次
        return []
    rows = []
    for line in out.splitlines():
        parts = line.split(None, 3)
        if len(parts) < 4:
            continue
        pid_s, pgid_s, etime, cmd = parts
        if not pid_s.isdigit() or not pgid_s.isdigit():
            continue
        if int(pgid_s) == pgid:
            rows.append((int(pid_s), etime, cmd))
    return rows


def is_jvm(cmd):
    exe = os.path.basename(cmd.split()[0]) if cmd.split() else ""
    return exe == "java" or exe.startswith("java")


def jstack_path():
    found = shutil.which("jstack")
    if found:
        return found
    home = os.environ.get("JAVA_HOME")
    if home:
        cand = os.path.join(home, "bin", "jstack")
        if os.path.exists(cand):
            return cand
    return None


def dump_stacks(pgid, procs):
    """把挂死现场写进我们自己的 stdout（= CI 日志）。"""
    print(f"[run_bounded] 同组进程 {len(procs)} 个：", flush=True)
    for pid, etime, cmd in procs:
        print(f"    pid={pid} etime={etime}  {cmd[:200]}", flush=True)

    # (1) 整组 SIGQUIT：JVM 收到就打印 "Full thread dump" 到自己 stderr 上。
    try:
        os.killpg(pgid, signal.SIGQUIT)
        print("[run_bounded] 已向整组发 SIGQUIT，等 JVM 打完线程栈…", flush=True)
    except OSError as e:
        print(f"[run_bounded] SIGQUIT 失败：{e}", flush=True)
    time.sleep(3)

    # (2) jstack 逐个补：万一 fork 的输出没被转发回来，这份还在。
    tool = jstack_path()
    if not tool:
        print("[run_bounded] 找不到 jstack，只保留上面 SIGQUIT 的那一份", flush=True)
        return
    for pid, _, cmd in procs:
        if not is_jvm(cmd):
            continue
        print(f"\n===== jstack {pid} =====", flush=True)
        try:
            subprocess.run([tool, "-l", str(pid)], timeout=30)
        except Exception as e:
            print(f"[run_bounded] jstack {pid} 失败：{e}", flush=True)


def kill_group(pgid, sig, what):
    try:
        os.killpg(pgid, sig)
        return True
    except ProcessLookupError:
        return False
    except OSError as e:
        print(f"[run_bounded] {what} 失败：{e}", flush=True)
        return False


def main(argv):
    ap = argparse.ArgumentParser(description="有界超时 + 超时取证")
    ap.add_argument("--seconds", type=float, required=True, help="上限秒数")
    ap.add_argument("--grace", type=float, default=10.0, help="SIGTERM 之后等待秒数")
    ap.add_argument("cmd", nargs=argparse.REMAINDER, help="-- 之后是要跑的命令")
    args = ap.parse_args(argv)
    cmd = args.cmd[1:] if args.cmd and args.cmd[0] == "--" else args.cmd
    if not cmd:
        ap.error("没有给要跑的命令（形式：-- <命令>）")
    if args.seconds <= 0:
        ap.error("--seconds 必须为正")

    print(f"[run_bounded] 起跑：{shlex.join(cmd)}（上限 {args.seconds:.0f}s）", flush=True)
    start = time.monotonic()
    # start_new_session=True：让子进程自成一个进程组，SIGQUIT/SIGKILL 一次打到整棵树。
    proc = subprocess.Popen(cmd, start_new_session=True)

    timed_out = False
    try:
        rc = proc.wait(timeout=args.seconds)
    except subprocess.TimeoutExpired:
        timed_out = True
        elapsed = time.monotonic() - start
        print("\n" + "!" * 78, flush=True)
        print(f"!! 超时：已跑 {elapsed:.0f}s，上限 {args.seconds:.0f}s —— 下面这段就是挂死现场",
              flush=True)
        print("!" * 78, flush=True)
        dump_stacks(proc.pid, group_processes(proc.pid))
        kill_group(proc.pid, signal.SIGTERM, "SIGTERM")
        try:
            proc.wait(timeout=args.grace)
        except subprocess.TimeoutExpired:
            kill_group(proc.pid, signal.SIGKILL, "SIGKILL")
            try:
                proc.wait(timeout=15)
            except subprocess.TimeoutExpired:
                print("[run_bounded] 进程组没能收干净（已发 SIGKILL）", flush=True)
        rc = EXIT_TIMEOUT

    elapsed = time.monotonic() - start
    verdict = "超时" if timed_out else "正常退出"
    print(f"[run_bounded] {verdict}：用时 {elapsed:.1f}s，退出码 {rc}", flush=True)
    return rc


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))