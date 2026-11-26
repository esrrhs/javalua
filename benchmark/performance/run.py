#!/usr/bin/env python3
"""Forked diagnostic benchmarks. Standard library only; no wall-time pass/fail gates."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import platform
import shlex
import shutil
import statistics
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
HERE = ROOT / "benchmark/performance"
MAIN = "com.github.esrrhs.javalua.PerformanceHarness"


def run(command, **kwargs):
    result = subprocess.run([str(x) for x in command], cwd=ROOT, text=True,
                            capture_output=True, timeout=1200, **kwargs)
    if result.returncode:
        raise RuntimeError(f"{shlex.join(map(str, command))}\n{result.stdout}\n{result.stderr}")
    return result


def describe(values):
    ordered = sorted(values)
    # Inclusive linear interpolation, explicitly reproducible even with 2 smoke samples.
    def percentile(p):
        i = (len(ordered) - 1) * p
        lo = int(i)
        return ordered[lo] + (ordered[min(lo + 1, len(ordered) - 1)] - ordered[lo]) * (i - lo)
    return dict(count=len(values), min=min(values), p25=percentile(.25),
                median=statistics.median(values), p75=percentile(.75), max=max(values))


def expected(name, n):
    if name in ("arithmetic", "tables"):
        return 3 * n * (n + 1) // 2
    if name == "allocations":
        return n * (n + 1) // 2 + n
    return 52 * n + 45 * (n // 10) + (n % 10) * (n % 10 + 1) // 2


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--output", type=Path, required=True, help="new JSON file; never overwrite evidence")
    p.add_argument("--smoke", action="store_true", help="checksums and plumbing only, NOT performance")
    p.add_argument("--skip-build", action="store_true", help="caller guarantees current target/classes")
    p.add_argument("--reference", type=Path, help="optional exact Lua 5.3.4 checksum oracle, not speed comparator")
    p.add_argument("--forks", type=int)
    p.add_argument("--warmup", type=int)
    p.add_argument("--warmup-ms", type=int, help="minimum warmup time per case, in addition to batch count")
    p.add_argument("--samples", type=int)
    p.add_argument("--n", type=int)
    p.add_argument("--batch", type=int)
    p.add_argument("--cold", type=int)
    args = p.parse_args()
    output = args.output.resolve()
    if output.exists():
        p.error("output already exists (choose a new filename)")
    config = {}
    for name, smoke, measure in (("forks", 1, 3), ("warmup", 1, 15), ("samples", 2, 9),
                                  ("n", 100, 10000), ("batch", 1, 3), ("cold", 2, 7), ("warmup_ms", 0, 1000)):
        value = getattr(args, name)
        config[name] = value if value is not None else (smoke if args.smoke else measure)
        if config[name] < (0 if name in ("warmup", "warmup_ms") else 1):
            p.error(f"invalid {name}")
    if config["n"] > 1000000:
        p.error("n must be <= 1000000")
    java_home = os.environ.get("JAVA_HOME")
    java = str(Path(java_home) / "bin/java") if java_home else shutil.which("java")
    javac = str(Path(java_home) / "bin/javac") if java_home else shutil.which("javac")
    if not java or not javac:
        p.error("JDK 21+ required; set JAVA_HOME or PATH")
    if not args.skip_build:
        run(["./mvnw", "-q", "-DskipTests", "compile"])
    classes = ROOT / "target/performance-classes"
    classes.mkdir(parents=True, exist_ok=True)
    run([javac, "--release", "21", "-cp", ROOT / "target/classes", "-d", classes,
         HERE / "PerformanceHarness.java"])
    # Keep VM settings fixed across cold/warm forks. Override the WHOLE list to benchmark native.
    options = shlex.split(os.environ.get("BENCH_JAVA_OPTS", "-Xms256m -Xmx256m -Djavalua.nonative=true"))
    command = [java, *options, "-cp", os.pathsep.join(map(str, [ROOT / "target/classes", classes])), MAIN]
    tracked = run(["git", "ls-files", "src/main", "pom.xml", "benchmark/performance"]).stdout.splitlines()
    # Include new harness files even before commit; exclude evidence to avoid recursive hashes.
    sources = set(tracked) | {str(x.relative_to(ROOT)) for x in HERE.rglob("*") if x.is_file()}
    hashes = {s: hashlib.sha256((ROOT / s).read_bytes()).hexdigest() for s in sorted(sources)
              if (ROOT / s).is_file() and "__pycache__" not in s}
    cpuinfo = Path("/proc/cpuinfo")
    cpu = next((line.split(":", 1)[1].strip() for line in cpuinfo.read_text().splitlines()
                if line.startswith("model name")), platform.processor()) if cpuinfo.exists() else platform.processor()
    doc = dict(schema=1, smoke=args.smoke, config=config, command=command,
               invocation=sys.argv, timestamp=datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))).isoformat(),
               environment=dict(java=run([java, "-version"]).stderr, javac=run([javac, "-version"]).stdout,
                                platform=platform.platform(), cpu=cpu, cpu_count=os.cpu_count(),
                                affinity=sorted(os.sched_getaffinity(0)) if hasattr(os, "sched_getaffinity") else None,
                                load_start=os.getloadavg() if hasattr(os, "getloadavg") else None,
                                meminfo=Path("/proc/meminfo").read_text() if Path("/proc/meminfo").exists() else None,
                                revision=run(["git", "rev-parse", "HEAD"]).stdout.strip(),
                                status=run(["git", "status", "--short"]).stdout,
                                external_java_options={k: os.environ[k] for k in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS") if k in os.environ}),
               source_sha256=hashes, cold=[], forks=[])
    if args.reference:
        reference = args.reference.resolve()
        version = run([reference, "-v"])
        if "Lua 5.3.4" not in version.stdout + version.stderr:
            p.error("reference must identify as Lua 5.3.4")
        checks = {}
        for name in ("arithmetic", "tables", "strings", "allocations"):
            # Filenames are generated by us; JSON quoting is valid for these ASCII paths.
            source = f'local f=assert(loadfile({json.dumps(str(HERE / "workloads" / (name + ".lua")))})); print(f({config["n"]}))'
            value = int(run([reference, "-"], input=source).stdout.strip())
            if value != expected(name, config["n"]):
                raise AssertionError((name, value))
            checks[name] = value
        doc["reference"] = dict(path=str(reference), version=version.stdout + version.stderr,
                                sha256=hashlib.sha256(reference.read_bytes()).hexdigest(), checksums=checks)
    for _ in range(config["cold"]):
        start = time.perf_counter_ns()
        result = run([*command, "startup"])
        elapsed = time.perf_counter_ns() - start
        row = json.loads(result.stdout)
        if row["checksum"] != 42:
            raise AssertionError("startup checksum")
        row.update(process_wall_ns=elapsed, stderr=result.stderr)
        doc["cold"].append(row)
    for fork in range(config["forks"]):
        result = run([*command, HERE / "workloads", config["n"], config["batch"], config["warmup"], config["samples"], fork, config["warmup_ms"]])
        rows = [json.loads(line) for line in result.stdout.splitlines()]
        metadata, samples = rows[0], rows[1:]
        if len(samples) != config["samples"] * 6 or set(row["workload"] for row in samples) != {
                "arithmetic", "tables", "strings", "allocations", "java-to-lua", "lua-to-java"}:
            raise AssertionError("incomplete measurement")
        doc["forks"].append(dict(index=fork, metadata=metadata, samples=samples, stderr=result.stderr))
    doc["environment"]["load_end"] = os.getloadavg() if hasattr(os, "getloadavg") else None
    doc["summary"] = {"cold_process_wall_ms": describe([r["process_wall_ns"] / 1e6 for r in doc["cold"]])}
    for name in sorted({row["workload"] for row in doc["forks"][0]["samples"]}):
        rows = [r for fork in doc["forks"] for r in fork["samples"] if r["workload"] == name]
        doc["summary"][name] = dict(
            sample_ms=describe([r["elapsed_ns"] / 1e6 for r in rows]),
            ns_per_logical_iteration=describe([r["elapsed_ns"] / (config["n"] * config["batch"]) for r in rows]),
            allocated_bytes_per_iteration=describe([r["allocated_bytes"] / (config["n"] * config["batch"])
                                                     for r in rows]) if all(r["allocated_bytes"] >= 0 for r in rows) else None,
            fork_median_ms=[statistics.median(r["elapsed_ns"] / 1e6 for r in f["samples"] if r["workload"] == name)
                            for f in doc["forks"]],
            gc_collections=sum(r["gc_collections"] for r in rows))
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("x") as f:
        json.dump(doc, f, indent=2)
        f.write("\n")
    print(json.dumps(doc["summary"], indent=2))
    print(f"Raw evidence: {output}")


if __name__ == "__main__":
    main()
