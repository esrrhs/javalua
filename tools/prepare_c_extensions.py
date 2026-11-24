#!/usr/bin/env python3
"""Offline, fail-closed build of pinned upstream modules for the native-only gate."""
import hashlib
import json
import os
from pathlib import Path
import shlex
import subprocess
import sys
import tarfile

ROOT = Path(__file__).resolve().parent.parent
FIXTURES = ROOT / "src/test/resources/c-extensions"
DEST = ROOT / "target/c-extensions"


def main():
    if sys.platform not in ("linux", "darwin"):
        raise SystemExit("C extension gate currently supports Linux/macOS only")
    manifest = json.loads((FIXTURES / "manifest.json").read_text())
    # Validate EVERY archive before compiling any of them. Never download/skip/fallback.
    for module in manifest["modules"]:
        archive = FIXTURES / "upstream" / module["archive"]
        actual = hashlib.sha256(archive.read_bytes()).hexdigest()
        if actual != module["sha256"]:
            raise SystemExit(f"SHA256 mismatch: {archive}: {actual}")
        print(f"verified {module['module']} {module['version']}: {actual}", flush=True)
    stock = ROOT / "target/lua53/src"
    if not (stock / "lua.h").is_file() or not (stock / "lua").is_file():
        raise SystemExit("Missing stock Lua 5.3.4: run sh tools/prepare_reference.sh first")
    version = subprocess.check_output([str(stock / "lua"), "-v"], stderr=subprocess.STDOUT, timeout=10)
    if not version.startswith(b"Lua 5.3.4 "):
        raise SystemExit(f"Wrong oracle: {version!r}")
    compiler = shlex.split(os.environ.get("CC", "cc"))
    for module in manifest["modules"]:
        with tarfile.open(FIXTURES / "upstream" / module["archive"]) as archive:
            # Python's data filter refuses traversal, absolute paths and device files.
            archive.extractall(DEST / "sources", filter="data")
        source = DEST / "sources" / module["directory"]
        for lane, headers in (("stock", stock), ("shim", ROOT / "native/lua")):
            output = DEST / lane / (module["module"] + ".so")
            output.parent.mkdir(parents=True, exist_ok=True)
            cmd = compiler + ["-shared", "-fPIC", "-O2", "-I", str(headers),
                              "-o", str(output)]
            if sys.platform == "darwin":
                cmd += ["-undefined", "dynamic_lookup"]
            cmd += ["-D" + d for d in module["defines"]]
            cmd += [str(source / s) for s in module["sources"]]
            cmd += ["-lm"]
            print(shlex.join(cmd), flush=True)
            subprocess.run(cmd, check=True, timeout=120)
    (DEST / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")


if __name__ == "__main__":
    main()
