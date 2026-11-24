#!/usr/bin/env python3
"""AST inventory of explicit Java declarations and exact, configured Lua 5.3.4.

No network access or writes to production sources. See docs/function-audit/README.md.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import tarfile

from tree_sitter import Language, Parser
import tree_sitter_c
import tree_sitter_java

ROOT = Path(__file__).resolve().parents[2]
DOC = ROOT / "docs/function-audit"
SOURCE_SHA256 = "f681aa518233bc407e23acf0f5887c884f17436f000d453b2491a9f11a52400c"
FLAGS = ["-std=gnu99", "-DLUA_COMPAT_5_2", "-DLUA_USE_POSIX", "-DLUA_USE_DLOPEN"]
CLASS_TYPES = {"class_declaration", "interface_declaration", "enum_declaration",
               "record_declaration", "annotation_type_declaration"}
METHOD_TYPES = {"method_declaration", "constructor_declaration", "compact_constructor_declaration", "annotation_type_element_declaration"}
STATUSES = {"unreviewed", "structure-mapped", "behavior-tested", "manually-reviewed"}
CATEGORIES = {"port", "java-helper", "java-interface", "jni", "generated", "generated-metadata", "gc", "platform"}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def text(node, data):
    return data[node.start_byte:node.end_byte].decode()


def compact(s):
    return re.sub(r"\s+", "", s)


def walk(node):
    yield node
    for child in node.children:
        yield from walk(child)


def java_declarations(path, data):
    tree = Parser(Language(tree_sitter_java.language())).parse(data)
    if tree.root_node.has_error:
        raise ValueError(f"Java parse error: {path}")
    result = []
    anonymous = [0]

    def visit(node, owners):
        here = owners
        if node.type in CLASS_TYPES:
            name = text(node.child_by_field_name("name"), data)
            here = owners + [name]
            result.append(dict(id=f"{path}::{'$'.join(here)}", kind=node.type,
                               name=name, owner='$'.join(owners), path=path,
                               line=node.start_point.row + 1, end_line=node.end_point.row + 1,
                               sha256=sha(data[node.start_byte:node.end_byte])))
        elif node.type == "class_body" and node.parent.type in {"object_creation_expression", "enum_constant"}:
            anonymous[0] += 1
            name = f"anonymous{anonymous[0]}"
            here = owners + [name]
            result.append(dict(id=f"{path}::{'$'.join(here)}", kind="anonymous_class",
                               name=name, owner='$'.join(owners), path=path,
                               line=node.start_point.row + 1, end_line=node.end_point.row + 1,
                               sha256=sha(data[node.start_byte:node.end_byte])))
        elif node.type in METHOD_TYPES:
            name = text(node.child_by_field_name("name"), data)
            params = node.child_by_field_name("parameters")
            types = []
            if params:
                for param in params.named_children:
                    if param.type not in {"formal_parameter", "spread_parameter", "receiver_parameter"}:
                        continue
                    typ = param.child_by_field_name("type")
                    if typ:
                        value = compact(text(typ, data))
                    else:  # spread_parameter grammar nests its variable declarator
                        value = compact(text(param.named_children[0], data))
                    if param.type == "spread_parameter":
                        value += "..."
                    dims = param.child_by_field_name("dimensions")
                    if dims:
                        value += compact(text(dims, data))
                    types.append(value)
            signature = f"{name}({','.join(types)})"
            result.append(dict(id=f"{path}::{'$'.join(owners)}#{signature}", kind=node.type,
                               name=name, signature=signature, owner='$'.join(owners), path=path,
                               line=node.start_point.row + 1, end_line=node.end_point.row + 1,
                               sha256=sha(data[node.start_byte:node.end_byte]),
                               native=bool(re.search(r"\bnative\b", text(node, data).split('(')[0])),
                               generated_marker=bool(re.search(r"@Generated\b", text(node, data)))))
        for child in node.children:
            visit(child, here)
    visit(tree.root_node, [])
    ids = [d['id'] for d in result]
    if len(ids) != len(set(ids)):
        raise ValueError(f"ambiguous declaration IDs: {path}")
    return result


def verify_reference(reference, archive):
    blob = archive.read_bytes()
    if sha(blob) != SOURCE_SHA256:
        raise ValueError("not the exact published Lua 5.3.4 source archive")
    files = {}
    with tarfile.open(archive, "r:gz") as tar:
        for member in tar.getmembers():
            if member.isfile() and member.name.startswith("lua-5.3.4/src/"):
                name = member.name.removeprefix("lua-5.3.4/src/")
                if not name.endswith((".h", ".c")) and name != "Makefile":
                    continue
                expected = tar.extractfile(member).read()
                actual = (reference / name).read_bytes()
                if actual != expected:
                    raise ValueError(f"edited/wrong reference: {name}; rerun tools/prepare_reference.sh")
                files[name] = sha(expected)
    extras = {p.name for p in reference.iterdir() if p.suffix in {'.c', '.h'}} - set(files)
    if extras:
        raise ValueError(f"unexpected reference sources: {sorted(extras)}")
    return files


def declarator_name(node, data):
    if node.type == "identifier":
        return text(node, data)
    child = node.child_by_field_name("declarator")
    if child:
        return declarator_name(child, data)
    raise ValueError(f"unrecognized C declarator {node.type}: {text(node, data)[:120]}")


def c_inventory(reference, cc):
    parser = Parser(Language(tree_sitter_c.language()))
    entries = []
    for path in sorted(reference.glob("*.c")):
        # GCC/Clang line markers retain exact source coordinates after macro expansion.
        # Builtin varargs expressions are irrelevant to declarations; normalize
        # their type argument to a cast accepted by tree-sitter. Hash original C.
        output = subprocess.check_output([cc, "-E", *FLAGS, "-D__builtin_va_arg(a,t)=(*(t*)0)", str(path)], cwd=reference)
        lines, locations = [], []
        current, line_no = "", 0
        for line in output.decode().splitlines(keepends=True):
            marker = re.match(r'^#\s+(\d+)\s+"([^"]+)"', line)
            if marker:
                line_no, current = int(marker[1]), marker[2]
                lines.append("\n")
                locations.append((current, line_no))
                continue
            lines.append(line)
            locations.append((current, line_no))
            line_no += 1
        data = ''.join(lines).encode()
        root = parser.parse(data).root_node
        original = path.read_bytes().splitlines(keepends=True)
        for node in walk(root):
            if node.type != "function_definition":
                continue
            source, first = locations[node.start_point.row]
            if Path(source).resolve() != path.resolve():
                continue
            declarator = node.child_by_field_name("declarator")
            if declarator.has_error:
                raise ValueError(f"C declarator parse error {path.name}:{first}")
            endsource, last = locations[node.end_point.row]
            if source != endsource:
                raise ValueError(f"C function crosses source files: {path}:{first}")
            name = declarator_name(node.child_by_field_name("declarator"), data)
            entries.append(dict(id=f"{path.name}::{name}", kind="function", name=name,
                                path=path.name, line=first, end_line=last,
                                sha256=sha(b''.join(original[first-1:last])),
                                body_parser_recovery=node.has_error))
    # Function-like macros are addressable targets, not counted as C functions.
    for path in sorted(reference.glob("*")):
        if path.suffix not in {".c", ".h"}:
            continue
        lines = path.read_bytes().splitlines(keepends=True)
        for i, line in enumerate(lines):
            match = re.match(rb"\s*#\s*define\s+(\w+)\(", line)
            if not match:
                continue
            j = i
            while lines[j].rstrip().endswith(b"\\"):
                j += 1
            name = match[1].decode()
            entries.append(dict(id=f"{path.name}::{name}@{i+1}", kind="macro", name=name,
                                path=path.name, line=i+1, end_line=j+1,
                                sha256=sha(b''.join(lines[i:j+1]))))
    if len(entries) != len({e['id'] for e in entries}):
        raise ValueError("duplicate C declaration IDs")
    return sorted(entries, key=lambda x: x['id'])


def normalized(name):
    name = re.sub(r"^(?:lua[A-Z]_?|lua_|[bB]_|str_|math_|os_|io_|utf8_|tinsert_|f_)", "", name)
    return re.sub(r"[^a-z0-9]", "", name.lower())


def category(decl, rule):
    if rule.get('category') == 'generated-metadata':
        return 'generated-metadata'
    if decl['kind'] in {'interface_declaration', 'annotation_type_declaration'} or rule.get('category') == 'java-interface':
        return 'java-interface'
    if decl.get('generated_marker'):
        return 'generated'
    if decl['kind'] == 'constructor_declaration':
        return 'java-helper'
    return rule.get('category', 'port')


def assemble(reference, archive, cc):
    rules = json.loads((DOC / "mapping.json").read_text())
    cfiles = verify_reference(reference, archive)
    cent = c_inventory(reference, cc)
    cids = {d['id']: d for d in cent}
    java, sources = [], {}
    paths = sorted((ROOT / "src/main/java").rglob("*.java"))
    stems = [p.stem for p in paths]
    if len(stems) != len(set(stems)):
        raise ValueError("duplicate Java source basenames; migrate file rules to disambiguated paths")
    orphan_files = set(rules['files']) - set(stems)
    if orphan_files:
        raise ValueError(f"orphan file classifications: {sorted(orphan_files)}")
    for path in paths:
        rel = path.relative_to(ROOT).as_posix()
        blob = path.read_bytes()
        sources[rel] = sha(blob)
        if path.stem not in rules['files']:
            raise ValueError(f"unclassified Java source: {rel}; add a file rule")
        rule = rules['files'][path.stem]
        for decl in java_declarations(rel, blob):
            candidates = []
            if decl['kind'] in METHOD_TYPES and decl['kind'] != 'constructor_declaration':
                candidates = [d['id'] for d in cent if d['path'] in rule['c_files']
                              and normalized(d['name']) == normalized(decl['name'])]
            decl.update(category=category(decl, rule), status="unreviewed",
                        c_files=rule['c_files'], c_targets=candidates,
                        mapping_basis="unique-name-candidate" if len(candidates) == 1 else
                                      "ambiguous-name-candidates" if candidates else "file-context-only",
                        evidence=[], note=rule['note'])
            override = rules['declarations'].get(decl['id'])
            if override:
                override = dict(override)
                expected = override.pop('java_sha256', None)
                if expected and expected != decl['sha256']:
                    raise ValueError(f"stale Java review: {decl['id']}; downgrade or re-audit")
                for target, expected_sha in override.pop('c_sha256', {}).items():
                    if target not in cids or cids[target]['sha256'] != expected_sha:
                        raise ValueError(f"stale C review: {target}")
                decl.update(override)
            if decl['status'] not in STATUSES or decl['category'] not in CATEGORIES:
                raise ValueError(f"invalid status/category: {decl['id']}")
            if decl['status'] != 'unreviewed':
                if not override or not expected or not decl['c_targets'] or not decl['evidence']:
                    raise ValueError(f"unsupported review claim: {decl['id']}")
                recorded_c = rules['declarations'][decl['id']].get('c_sha256', {})
                if set(recorded_c) != set(decl['c_targets']):
                    raise ValueError(f"missing C review fingerprints: {decl['id']}")
                if decl['status'] == 'behavior-tested' and not any(e.get('symbol') and e.get('sha256') and e['path'].startswith('src/test/') for e in decl['evidence']):
                    raise ValueError(f"behavior-tested claim needs a hashed named test: {decl['id']}")
            for ctype in decl.get('c_types', []):
                type_path = reference / ctype['path']
                if ctype['path'] not in cfiles or not re.search(r'\b'+re.escape(ctype['name'])+r'\b', type_path.read_text()):
                    raise ValueError(f"unknown C representation: {ctype}")
            for target in decl['c_targets']:
                if target not in cids:
                    raise ValueError(f"unknown C target: {target}")
            for evidence in decl['evidence']:
                ep = ROOT / evidence['path']
                if not ep.is_file():
                    raise ValueError(f"missing evidence file: {ep}")
                if evidence.get('sha256') and sha(ep.read_bytes()) != evidence['sha256']:
                    raise ValueError(f"changed evidence file: {ep}; revalidate witness")
                if evidence.get('symbol') and evidence['symbol'] not in ep.read_text():
                    raise ValueError(f"missing evidence symbol: {evidence}")
            java.append(decl)
    ids = {d['id'] for d in java}
    unknown = set(rules['declarations']) - ids
    if unknown:
        raise ValueError(f"orphan mappings: {sorted(unknown)}")
    refs = Counter(target for d in java for target in d['c_targets'])
    for decl in cent:
        decl['java_references'] = refs[decl['id']]
        decl['scope'] = 'gc' if decl['path'] == 'lgc.c' else 'cli' if decl['path'] in {'lua.c', 'luac.c'} else 'library'
    return dict(schema_version=1, baseline_java_revision=rules['baseline_java_revision'],
                reference=dict(version="PUC-Rio Lua 5.3.4", archive_sha256=SOURCE_SHA256,
                               url="https://www.lua.org/ftp/lua-5.3.4.tar.gz",
                               preprocess_flags=FLAGS, files=cfiles),
                java_source_sha256=sources, java=sorted(java, key=lambda x: x['id']), c=cent)


def report(inv):
    java = inv['java']
    methods = [d for d in java if d['kind'] in METHOD_TYPES]
    types = [d for d in java if d['kind'] not in METHOD_TYPES]
    functions = [d for d in inv['c'] if d['kind'] == 'function']
    reviewed = [d for d in methods if d['status'] != 'unreviewed']
    out = ['# Generated inventory summary', '',
           'Generated by `tools/function_audit/inventory.py`; do not edit this summary.', '',
           f"Java baseline: `{inv['baseline_java_revision']}` (source hashes are authoritative after integration).", '',
           f"- Production source files: **{len(inv['java_source_sha256'])}**",
           f"- Explicit types (including nested/anonymous): **{len(types)}**",
           f"- Explicit executables (methods + constructors): **{len(methods)}**",
           f"- Executables with any C symbol candidate/target: **{sum(bool(d['c_targets']) for d in methods)}/{len(methods)}**",
           f"- Executables with scoped audit evidence: **{len(reviewed)}/{len(methods)}**",
           f"- Executables carrying @Generated (orthogonal marker, not a coverage exemption): **{sum(d.get('generated_marker', False) for d in methods)}**",
           f"- Active reference C function definitions (configured POSIX + DLOPEN + COMPAT_5_2): **{len(functions)}**",
           f"- Active C functions with any Java candidate/target: **{sum(bool(d['java_references']) for d in functions)}/{len(functions)}**",
           f"- Raw function-like macro definitions (all conditional branches, separate denominator): **{sum(d['kind']=='macro' for d in inv['c'])}**", '',
           '**Candidates and file contexts are NOT proof of port coverage or semantic alignment.**', '',
           '## Executable categories / review states', '', '| Category | Count |', '|---|---:|']
    out += [f'| {k} | {v} |' for k,v in sorted(Counter(d['category'] for d in methods).items())]
    out += ['', '| State | Count |', '|---|---:|']
    out += [f'| {k} | {v} |' for k,v in sorted(Counter(d['status'] for d in methods).items())]
    out += ['', '## Per-source coverage denominators', '',
            '| Java source | Explicit types | Executables | C symbol candidates/targets | Scoped audit evidence | C file contexts |',
            '|---|---:|---:|---:|---:|---|']
    for path in inv['java_source_sha256']:
        all_decls = [d for d in java if d['path']==path]
        ms = [d for d in all_decls if d['kind'] in METHOD_TYPES]
        rule = all_decls[0]
        out.append(f"| {Path(path).name} | {len(all_decls)-len(ms)} | {len(ms)} | {sum(bool(d['c_targets']) for d in ms)} | {sum(d['status']!='unreviewed' for d in ms)} | {', '.join(rule['c_files']) or 'none'} |")
    out += ['', '## Scoped audit entries', '']
    for d in reviewed:
        out.append(f"- `{d['owner']}#{d['signature']}` → {', '.join('`'+t+'`' for t in d['c_targets'])}: **{d['status']}**. {d['note']}")
    out += ['', '## Active C reverse gaps (triage queue, NOT confirmed missing implementations)', '',
            '| C file | Active functions | No Java symbol candidate/target | Scope |', '|---|---:|---:|---|']
    for path in sorted({d['path'] for d in functions}):
        ds = [d for d in functions if d['path']==path]
        out.append(f"| {path} | {len(ds)} | {sum(not d['java_references'] for d in ds)} | {ds[0]['scope']} |")
    return '\n'.join(out)+'\n'


def compiler_cross_check(inv, reference, cc):
    """Independent GCC/ELF symbol census; optional, requires GNU-style assembly."""
    for path in sorted(reference.glob("*.c")):
        assembly = subprocess.check_output([cc, "-S", "-O0", "-fkeep-static-functions",
                                            *FLAGS, str(path), "-o", "-"], text=True)
        emitted = set(re.findall(r"\.type\s+(\w+),\s*[@%]function", assembly))
        extracted = {d['name'] for d in inv['c'] if d['path'] == path.name and d['kind'] == 'function'}
        if emitted != extracted:
            raise ValueError(f"compiler/AST mismatch {path.name}: emitted-only={sorted(emitted-extracted)}, AST-only={sorted(extracted-emitted)}")
    print("compiler cross-check: all configured C translation units match AST census")


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('command', choices=['update', 'check'])
    ap.add_argument('--reference', type=Path, default=ROOT/'target/lua53/src')
    ap.add_argument('--archive', type=Path, default=ROOT/'target/reference-downloads/lua-5.3.4.tar.gz')
    ap.add_argument('--cc', default='cc')
    ap.add_argument('--compiler-cross-check', action='store_true', help='independent GCC/ELF function census')
    args = ap.parse_args(argv)
    try:
        inv = assemble(args.reference.resolve(), args.archive.resolve(), args.cc)
        if args.compiler_cross_check:
            compiler_cross_check(inv, args.reference.resolve(), args.cc)
        outputs = {'inventory.json': json.dumps(inv, indent=2, ensure_ascii=False)+'\n',
                   'SUMMARY.md': report(inv)}
        stale = []
        for name, content in outputs.items():
            path = DOC/name
            if args.command == 'update':
                path.write_text(content)
            elif not path.exists() or path.read_text() != content:
                stale.append(name)
        if stale:
            raise ValueError('inventory drift: '+', '.join(stale)+'; inspect source changes, re-audit stale evidence, then run update')
        print(f"{args.command}: {len(inv['java'])} Java declarations; {len(inv['c'])} C functions/macros; exact reference verified")
        methods = [d for d in inv['java'] if d['kind'] in METHOD_TYPES]
        print(f"open queue: {sum(not d['c_targets'] for d in methods)}/{len(methods)} executables lack a C symbol candidate/target; "
              f"{sum(d['status']=='unreviewed' for d in methods)} remain unreviewed (including helpers/exclusions); "
              "see SUMMARY.md; these are not conformance percentages")
        return 0
    except (ValueError, OSError, subprocess.CalledProcessError) as exc:
        print(f"audit failed: {exc}", file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
