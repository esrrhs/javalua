"""Checker contract tests; stdlib unittest plus the pinned AST parsers."""
import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import inventory as audit


class JavaAstTests(unittest.TestCase):
    def test_nested_anonymous_interface_and_overloads(self):
        src = b'''class A {
          A() {} void f(int x) {} void f(String[] x) {}
          class B { native int g(long x); }
          Runnable r = new Runnable() { public void run() {} };
          interface I { int call(int... xs); }
        }'''
        decls = audit.java_declarations('A.java', src)
        self.assertEqual(4, sum(d['kind'] not in audit.METHOD_TYPES for d in decls))
        self.assertEqual(6, sum(d['kind'] in audit.METHOD_TYPES for d in decls))
        ids = {d['id'] for d in decls}
        self.assertIn('A.java::A#f(String[])', ids)
        self.assertIn('A.java::A$I#call(int...)', ids)
        self.assertIn('A.java::A$anonymous1#run()', ids)
        self.assertTrue(next(d for d in decls if d['name']=='g')['native'])

    def test_generated_marker_and_array_declarator(self):
        decls = audit.java_declarations('A.java', b'class A { @Generated int f(int a[]) { return 1; } }')
        self.assertEqual('f(int[])', decls[1]['signature'])
        self.assertTrue(decls[1]['generated_marker'])

    def test_no_implicit_constructor_or_lambda_method(self):
        decls = audit.java_declarations('A.java', b'class A { Runnable r = () -> {}; }')
        self.assertEqual(1, len(decls))

    def test_broken_java_fails_closed(self):
        with self.assertRaisesRegex(ValueError, 'Java parse error'):
            audit.java_declarations('A.java', b'class A { void f( }')

    def test_body_edit_changes_fingerprint_not_identity(self):
        a = audit.java_declarations('A.java', b'class A { int f(){return 1;} }')[1]
        b = audit.java_declarations('A.java', b'class A { int f(){return 2;} }')[1]
        self.assertEqual(a['id'], b['id'])
        self.assertNotEqual(a['sha256'], b['sha256'])


class LedgerTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.doc = self.root/'docs/function-audit'
        self.doc.mkdir(parents=True)
        self.source = self.root/'src/main/java/A.java'
        self.source.parent.mkdir(parents=True)
        self.source.write_text('class A { int f(){return 1;} }')
        self.rules = {'baseline_java_revision':'fixture', 'files':{
            'A': {'c_files':['a.c'], 'category':'port', 'note':'fixture'}}, 'declarations':{}}
        self.c = [dict(id='a.c::f', kind='function', name='f', path='a.c', line=1,
                       end_line=1, sha256='c-fixture', body_parser_recovery=False)]
        for p in [patch.object(audit, 'ROOT', self.root), patch.object(audit, 'DOC', self.doc),
                  patch.object(audit, 'verify_reference', return_value={'a.c':'fixture'}),
                  patch.object(audit, 'c_inventory', return_value=self.c)]:
            p.start()
            self.addCleanup(p.stop)

    def assemble(self):
        (self.doc/'mapping.json').write_text(json.dumps(self.rules))
        return audit.assemble(self.root, self.root/'reference.tar.gz', 'cc')

    def test_name_candidate_is_not_a_review(self):
        decl = self.assemble()['java'][1]
        self.assertEqual('unreviewed', decl['status'])
        self.assertEqual(['a.c::f'], decl['c_targets'])
        self.assertEqual([], decl['evidence'])

    def test_new_file_requires_explicit_classification(self):
        (self.source.parent/'New.java').write_text('class New {}')
        with self.assertRaisesRegex(ValueError, 'unclassified Java source'):
            self.assemble()

    def test_deleted_method_mapping_is_rejected(self):
        self.rules['declarations']['not-present'] = {}
        with self.assertRaisesRegex(ValueError, 'orphan mappings'):
            self.assemble()

    def test_unsupported_semantic_claim_is_rejected(self):
        self.rules['declarations']['src/main/java/A.java::A#f()'] = {'status':'manually-reviewed'}
        with self.assertRaisesRegex(ValueError, 'unsupported review claim'):
            self.assemble()

    def test_stale_review_is_rejected(self):
        self.rules['declarations']['src/main/java/A.java::A#f()'] = {'java_sha256':'old'}
        with self.assertRaisesRegex(ValueError, 'stale Java review'):
            self.assemble()

    def test_missing_c_target_is_rejected(self):
        self.rules['declarations']['src/main/java/A.java::A#f()'] = {'c_targets':['a.c::missing']}
        with self.assertRaisesRegex(ValueError, 'unknown C target'):
            self.assemble()

    def test_evidence_symbol_drift_is_rejected(self):
        (self.root/'witness.java').write_text('renamed')
        self.rules['declarations']['src/main/java/A.java::A#f()'] = {
            'evidence':[{'path':'witness.java', 'symbol':'oldName'}]}
        with self.assertRaisesRegex(ValueError, 'missing evidence symbol'):
            self.assemble()

    def test_snapshot_detects_even_field_only_drift(self):
        self.assemble()
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(0, audit.main(['update']))
            self.assertEqual(0, audit.main(['check']))
            self.source.write_text('class A { int added; int f(){return 1;} }')
            self.assertEqual(1, audit.main(['check']))

    def test_stale_c_review_is_rejected(self):
        self.rules['declarations']['src/main/java/A.java::A#f()'] = {'c_sha256':{'a.c::f':'old'}}
        with self.assertRaisesRegex(ValueError, 'stale C review'):
            self.assemble()

    def test_changed_hashed_evidence_is_rejected(self):
        (self.root/'witness.java').write_text('current')
        self.rules['declarations']['src/main/java/A.java::A#f()'] = {
            'evidence':[{'path':'witness.java','sha256':'old'}]}
        with self.assertRaisesRegex(ValueError, 'changed evidence file'):
            self.assemble()

    def test_review_requires_c_fingerprints(self):
        d = audit.java_declarations('src/main/java/A.java', self.source.read_bytes())[1]
        self.rules['declarations'][d['id']] = {
            'status':'structure-mapped', 'java_sha256':d['sha256'],
            'evidence':[{'path':'docs/function-audit/mapping.json'}]}
        with self.assertRaisesRegex(ValueError, 'missing C review fingerprints'):
            self.assemble()

    def test_behavior_requires_a_named_hashed_test(self):
        d = audit.java_declarations('src/main/java/A.java', self.source.read_bytes())[1]
        self.rules['declarations'][d['id']] = {
            'status':'behavior-tested', 'java_sha256':d['sha256'],
            'c_sha256':{'a.c::f':'c-fixture'},
            'evidence':[{'path':'docs/function-audit/mapping.json'}]}
        with self.assertRaisesRegex(ValueError, 'hashed named test'):
            self.assemble()

    def test_deleted_file_classification_is_rejected(self):
        self.rules['files']['Deleted'] = self.rules['files']['A']
        with self.assertRaisesRegex(ValueError, 'orphan file classifications'):
            self.assemble()

    def test_duplicate_basename_is_not_silently_classified(self):
        (self.source.parent/'other').mkdir()
        (self.source.parent/'other/A.java').write_text('class A {}')
        with self.assertRaisesRegex(ValueError, 'duplicate Java source basenames'):
            self.assemble()


class ReferenceVerificationTests(unittest.TestCase):
    def test_wrong_archive_hash_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            archive = Path(temp)/'wrong.tar.gz'
            archive.write_bytes(b'not Lua 5.3.4')
            with self.assertRaisesRegex(ValueError, 'exact published Lua'):
                audit.verify_reference(Path(temp), archive)


if __name__ == '__main__':
    unittest.main()
