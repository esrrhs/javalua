#!/usr/bin/env python3
"""Small aggregate from `jfr print --json`; main thread only, no timing claims."""
from collections import Counter
import hashlib
import json
from pathlib import Path
import sys


def method_name(frame):
    method = frame["method"]
    return method["type"]["name"].replace("/", ".") + "." + method["name"]


def summarize(events):
    counts, leaf, inclusive, objects, project_allocators = (Counter() for _ in range(5))
    times = []
    for event in events:
        values = event["values"]
        thread = values.get("sampledThread") or values.get("eventThread") or {}
        if thread.get("javaName") != "main":
            continue
        kind = event["type"]
        counts[kind] += 1
        times.append(values["startTime"])
        frames = (values.get("stackTrace") or {}).get("frames", [])
        names = [method_name(f) for f in frames]
        if kind == "jdk.ExecutionSample":
            if names:
                leaf[names[0]] += 1
                inclusive.update(set(names))
        elif kind == "jdk.ObjectAllocationSample":
            weight = values["weight"]
            objects[values["objectClass"]["name"].replace("/", ".")] += weight
            project = next((n for n in names if n.startswith("com.github.esrrhs.javalua.")), "<no project frame>")
            project_allocators[project] += weight
    def top(counter):
        return dict(sorted(counter.items(), key=lambda pair: (-pair[1], pair[0]))[:15])
    return dict(main_thread_event_counts=dict(counts),
                first_event=min(times) if times else None, last_event=max(times) if times else None,
                execution_leaf_samples=top(leaf), execution_inclusive_samples=top(inclusive),
                approximate_allocation_weight_by_class=top(objects),
                approximate_allocation_weight_by_first_project_frame=top(project_allocators))


def main():
    if len(sys.argv) != 2:
        raise SystemExit("usage: summarize_jfr.py <jfr-print-events.json>")
    data = Path(sys.argv[1]).read_bytes()
    doc = json.loads(data)
    result = summarize(doc["recording"]["events"])
    result.update(input_sha256=hashlib.sha256(data).hexdigest(),
                  event_count=len(doc["recording"]["events"]),
                  note="Mixed run including warmup/setup; sampled counts/weights, not exact CPU shares or allocation totals")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
