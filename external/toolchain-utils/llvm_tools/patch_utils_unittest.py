#!/usr/bin/env python3
# Copyright 2022 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Unit tests for the patch_utils.py file."""

import copy
import io
import json
from pathlib import Path
import subprocess
import tempfile
from typing import Callable
import unittest
from unittest import mock

import patch_utils as pu


class TestPatchUtils(unittest.TestCase):
    """Test the patch_utils."""

    def test_predict_indent(self):
        test_str1 = """
a
  a
      a
  a
a
"""
        self.assertEqual(pu.predict_indent(test_str1.splitlines()), 2)
        test_str2 = """
a
    a
        a
    a
a
"""
        self.assertEqual(pu.predict_indent(test_str2.splitlines()), 4)

    def test_from_to_dict(self):
        """Test to and from dict conversion."""
        d = TestPatchUtils._default_json_dict()
        d["metadata"] = {
            "title": "hello world",
            "info": [],
            "other_extra_info": {
                "extra_flags": [],
            },
        }
        e = pu.PatchEntry.from_dict(TestPatchUtils._mock_dir(), d)
        self.assertEqual(d, e.to_dict())

        # Test that they aren't serialised the same, as 'd' isn't sorted.
        self.assertNotEqual(
            json.dumps(d["metadata"]), json.dumps(e.to_dict()["metadata"])
        )
        self.assertEqual(
            ["info", "other_extra_info", "title"],
            list(e.to_dict()["metadata"].keys()),
        )

    def test_patch_path(self):
        """Test that we can get the full path from a PatchEntry."""
        d = TestPatchUtils._default_json_dict()
        with mock.patch.object(Path, "is_dir", return_value=True):
            entry = pu.PatchEntry.from_dict(Path("/home/dir"), d)
            self.assertEqual(
                entry.patch_path(), Path("/home/dir") / d["rel_patch_path"]
            )

    def test_can_patch_version(self):
        """Test that patch application based on version is correct."""
        base_dict = TestPatchUtils._default_json_dict()
        workdir = TestPatchUtils._mock_dir()
        e1 = pu.PatchEntry.from_dict(workdir, base_dict)
        self.assertFalse(e1.can_patch_version(3))
        self.assertTrue(e1.can_patch_version(4))
        self.assertTrue(e1.can_patch_version(5))
        self.assertFalse(e1.can_patch_version(9))
        base_dict["version_range"] = {"until": 9}
        e2 = pu.PatchEntry.from_dict(workdir, base_dict)
        self.assertTrue(e2.can_patch_version(0))
        self.assertTrue(e2.can_patch_version(5))
        self.assertFalse(e2.can_patch_version(9))
        base_dict["version_range"] = {"from": 4}
        e3 = pu.PatchEntry.from_dict(workdir, base_dict)
        self.assertFalse(e3.can_patch_version(3))
        self.assertTrue(e3.can_patch_version(5))
        self.assertTrue(e3.can_patch_version(1 << 31))
        base_dict["version_range"] = {"from": 4, "until": None}
        e4 = pu.PatchEntry.from_dict(workdir, base_dict)
        self.assertFalse(e4.can_patch_version(3))
        self.assertTrue(e4.can_patch_version(5))
        self.assertTrue(e4.can_patch_version(1 << 31))
        base_dict["version_range"] = {"from": None, "until": 9}
        e5 = pu.PatchEntry.from_dict(workdir, base_dict)
        self.assertTrue(e5.can_patch_version(0))
        self.assertTrue(e5.can_patch_version(5))
        self.assertFalse(e5.can_patch_version(9))

    def test_can_parse_from_json(self):
        """Test that patches be loaded from json."""
        patches_json = """
[
  {
    "metadata": {},
    "platforms": [],
    "rel_patch_path": "cherry/nowhere.patch",
    "version_range": {}
  },
  {
    "metadata": {},
    "rel_patch_path": "cherry/somewhere.patch",
    "version_range": {}
  },
  {
    "rel_patch_path": "where.patch",
    "version_range": null
  },
  {
    "rel_patch_path": "cherry/anywhere.patch"
  }
]
    """
        result = pu.json_str_to_patch_entries(Path(), patches_json)
        self.assertEqual(len(result), 4)

        result = pu.json_to_patch_entries(Path(), io.StringIO(patches_json))
        self.assertEqual(len(result), 4)

    def test_parsed_hunks(self):
        """Test that we can parse patch file hunks."""
        m = mock.mock_open(read_data=_EXAMPLE_PATCH)

        def mocked_open(self, *args, **kwargs):
            return m(self, *args, **kwargs)

        with mock.patch.object(Path, "open", mocked_open):
            e = pu.PatchEntry.from_dict(
                TestPatchUtils._mock_dir(), TestPatchUtils._default_json_dict()
            )
            hunk_dict = e.parsed_hunks()

        m.assert_called()
        filename1 = "clang/lib/Driver/ToolChains/Clang.cpp"
        filename2 = "llvm/lib/Passes/PassBuilder.cpp"
        self.assertEqual(set(hunk_dict.keys()), {filename1, filename2})
        hunk_list1 = hunk_dict[filename1]
        hunk_list2 = hunk_dict[filename2]
        self.assertEqual(len(hunk_list1), 1)
        self.assertEqual(len(hunk_list2), 2)

    def test_apply_when_patch_nonexistent(self):
        """Test that we error out when we try to apply a non-existent patch."""
        src_dir = TestPatchUtils._mock_dir("somewhere/llvm-project")
        patch_dir = TestPatchUtils._mock_dir()
        e = pu.PatchEntry.from_dict(
            patch_dir, TestPatchUtils._default_json_dict()
        )
        with mock.patch("subprocess.run", mock.MagicMock()):
            self.assertRaises(RuntimeError, lambda: e.apply(src_dir))

    def test_apply_success(self):
        """Test that we can call apply."""
        src_dir = TestPatchUtils._mock_dir("somewhere/llvm-project")
        patch_dir = TestPatchUtils._mock_dir()
        e = pu.PatchEntry.from_dict(
            patch_dir, TestPatchUtils._default_json_dict()
        )

        """Make a deepcopy of the case for testing commit patch option."""
        e1 = copy.deepcopy(e)

        with mock.patch("pathlib.Path.is_file", return_value=True):
            with mock.patch("subprocess.run", mock.MagicMock()):
                result = e.apply(src_dir)
        self.assertTrue(result.succeeded)

        """Test that commit patch option works."""
        with mock.patch("pathlib.Path.is_file", return_value=True):
            with mock.patch("subprocess.run", mock.MagicMock()):
                result1 = e1.apply(src_dir, pu.git_am)
        self.assertTrue(result1.succeeded)

    def test_parse_failed_patch_output(self):
        """Test that we can call parse `patch` output."""
        fixture = """
checking file a/b/c.cpp
Hunk #1 SUCCEEDED at 96 with fuzz 1.
Hunk #12 FAILED at 77.
Hunk #42 FAILED at 1979.
checking file x/y/z.h
Hunk #4 FAILED at 30.
checking file works.cpp
Hunk #1 SUCCEEDED at 96 with fuzz 1.
"""
        result = pu.parse_failed_patch_output(fixture)
        self.assertEqual(result["a/b/c.cpp"], [12, 42])
        self.assertEqual(result["x/y/z.h"], [4])
        self.assertNotIn("works.cpp", result)

    def test_is_git_dirty(self):
        """Test if a git directory has uncommitted changes."""
        with tempfile.TemporaryDirectory(
            prefix="patch_utils_unittest"
        ) as dirname:
            dirpath = Path(dirname)

            def _run_h(cmd):
                subprocess.run(
                    cmd,
                    cwd=dirpath,
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL,
                    check=True,
                )

            _run_h(["git", "init"])
            self.assertFalse(pu.is_git_dirty(dirpath))
            test_file = dirpath / "test_file"
            test_file.touch()
            self.assertTrue(pu.is_git_dirty(dirpath))
            _run_h(["git", "add", "."])
            _run_h(["git", "commit", "-m", "test"])
            self.assertFalse(pu.is_git_dirty(dirpath))
            test_file.touch()
            self.assertFalse(pu.is_git_dirty(dirpath))
            with test_file.open("w", encoding="utf-8"):
                test_file.write_text("abc")
            self.assertTrue(pu.is_git_dirty(dirpath))

    @mock.patch("patch_utils.git_clean_context", mock.MagicMock)
    def test_update_version_ranges(self):
        """Test the UpdateVersionRanges function."""
        with tempfile.TemporaryDirectory(
            prefix="patch_manager_unittest"
        ) as dirname:
            dirpath = Path(dirname)
            patches = [
                pu.PatchEntry(
                    workdir=dirpath,
                    rel_patch_path="x.patch",
                    metadata=None,
                    platforms=None,
                    version_range={
                        "from": 0,
                        "until": 2,
                    },
                ),
                pu.PatchEntry(
                    workdir=dirpath,
                    rel_patch_path="y.patch",
                    metadata=None,
                    platforms=None,
                    version_range={
                        "from": 0,
                        "until": 2,
                    },
                ),
                pu.PatchEntry(
                    workdir=dirpath,
                    rel_patch_path="z.patch",
                    metadata=None,
                    platforms=None,
                    version_range={
                        "from": 4,
                        "until": 5,
                    },
                ),
            ]

            patches[0].apply = mock.MagicMock(
                return_value=pu.PatchResult(
                    succeeded=False, failed_hunks={"a/b/c": []}
                )
            )
            patches[1].apply = mock.MagicMock(
                return_value=pu.PatchResult(succeeded=True)
            )
            patches[2].apply = mock.MagicMock(
                return_value=pu.PatchResult(succeeded=False)
            )

            # Make a deepcopy of patches to test commit patch option
            patches2 = copy.deepcopy(patches)

            results, _ = pu.update_version_ranges_with_entries(
                1, dirpath, patches, pu.gnu_patch
            )

            # We should only have updated the version_range of the first patch,
            # as that one failed to apply.
            self.assertEqual(len(results), 1)
            self.assertEqual(results[0].version_range, {"from": 0, "until": 1})
            self.assertEqual(patches[0].version_range, {"from": 0, "until": 1})
            self.assertEqual(patches[1].version_range, {"from": 0, "until": 2})
            self.assertEqual(patches[2].version_range, {"from": 4, "until": 5})

            # Test git am option
            results2, _ = pu.update_version_ranges_with_entries(
                1, dirpath, patches2, pu.git_am
            )

            # We should only have updated the version_range of the first patch
            # via git am, as that one failed to apply.
            self.assertEqual(len(results2), 1)
            self.assertEqual(results2[0].version_range, {"from": 0, "until": 1})
            self.assertEqual(patches2[0].version_range, {"from": 0, "until": 1})
            self.assertEqual(patches2[1].version_range, {"from": 0, "until": 2})
            self.assertEqual(patches2[2].version_range, {"from": 4, "until": 5})

    @mock.patch("builtins.print")
    def test_remove_old_patches(self, _):
        """Can remove old patches from PATCHES.json."""
        one_patch_dict = {
            "metadata": {
                "title": "[some label] hello world",
            },
            "platforms": [
                "chromiumos",
            ],
            "rel_patch_path": "x/y/z",
            "version_range": {
                "from": 4,
                "until": 5,
            },
        }
        patches = [
            one_patch_dict,
            {**one_patch_dict, "version_range": {"until": None}},
            {**one_patch_dict, "version_range": {"from": 100}},
            {**one_patch_dict, "version_range": {"until": 8}},
        ]
        cases = [
            (0, lambda x: self.assertEqual(len(x), 4)),
            (6, lambda x: self.assertEqual(len(x), 3)),
            (8, lambda x: self.assertEqual(len(x), 2)),
            (1000, lambda x: self.assertEqual(len(x), 2)),
        ]

        def _t(dirname: str, svn_version: int, assertion_f: Callable):
            json_filepath = Path(dirname) / "PATCHES.json"
            with json_filepath.open("w", encoding="utf-8") as f:
                json.dump(patches, f)
            pu.remove_old_patches(svn_version, Path(), json_filepath)
            with json_filepath.open("r", encoding="utf-8") as f:
                result = json.load(f)
            assertion_f(result)

        with tempfile.TemporaryDirectory(
            prefix="patch_utils_unittest"
        ) as dirname:
            for r, a in cases:
                _t(dirname, r, a)

    @staticmethod
    def _default_json_dict():
        return {
            "metadata": {
                "title": "hello world",
            },
            "platforms": ["a"],
            "rel_patch_path": "x/y/z",
            "version_range": {
                "from": 4,
                "until": 9,
            },
        }

    @staticmethod
    def _mock_dir(path: str = "a/b/c"):
        workdir = Path(path)
        workdir = mock.MagicMock(workdir)
        workdir.is_dir = lambda: True
        workdir.joinpath = lambda x: Path(path).joinpath(x)
        workdir.__truediv__ = lambda self, x: self.joinpath(x)
        return workdir


_EXAMPLE_PATCH = """
diff --git a/clang/lib/Driver/ToolChains/Clang.cpp b/clang/lib/Driver/ToolChains/Clang.cpp
index 5620a543438..099eb769ca5 100644
--- a/clang/lib/Driver/ToolChains/Clang.cpp
+++ b/clang/lib/Driver/ToolChains/Clang.cpp
@@ -3995,8 +3995,11 @@ void Clang::ConstructJob(Compilation &C, const JobAction &JA,
       Args.hasArg(options::OPT_dA))
     CmdArgs.push_back("-masm-verbose");

-  if (!TC.useIntegratedAs())
+  if (!TC.useIntegratedAs()) {
     CmdArgs.push_back("-no-integrated-as");
+    CmdArgs.push_back("-mllvm");
+    CmdArgs.push_back("-enable-call-graph-profile-sort=false");
+  }

   if (Args.hasArg(options::OPT_fdebug_pass_structure)) {
     CmdArgs.push_back("-mdebug-pass");
diff --git a/llvm/lib/Passes/PassBuilder.cpp b/llvm/lib/Passes/PassBuilder.cpp
index c5fd68299eb..4c6e15eeeb9 100644
--- a/llvm/lib/Passes/PassBuilder.cpp
+++ b/llvm/lib/Passes/PassBuilder.cpp
@@ -212,6 +212,10 @@ static cl::opt<bool>
     EnableCHR("enable-chr-npm", cl::init(true), cl::Hidden,
               cl::desc("Enable control height reduction optimization (CHR)"));

+static cl::opt<bool> EnableCallGraphProfileSort(
+    "enable-call-graph-profile-sort", cl::init(true), cl::Hidden,
+    cl::desc("Enable call graph profile pass for the new PM (default = on)"));
+
 extern cl::opt<bool> EnableHotColdSplit;
 extern cl::opt<bool> EnableOrderFileInstrumentation;

@@ -939,7 +943,8 @@ ModulePassManager PassBuilder::buildModuleOptimizationPipeline(
   // Add the core optimizing pipeline.
   MPM.addPass(createModuleToFunctionPassAdaptor(std::move(OptimizePM)));

-  MPM.addPass(CGProfilePass());
+  if (EnableCallGraphProfileSort)
+    MPM.addPass(CGProfilePass());

   // Now we need to do some global optimization transforms.
   // FIXME: It would seem like these should come first in the optimization
"""

if __name__ == "__main__":
    unittest.main()
