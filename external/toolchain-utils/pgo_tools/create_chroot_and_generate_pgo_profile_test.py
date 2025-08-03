#!/usr/bin/env python3
# Copyright 2023 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Tests for generate_llvm_next_pgo."""

from pathlib import Path
import shutil
import tempfile
import textwrap
import unittest

# This script's name makes lines exceed 80 chars if it's not imported `as`
# something shorter.
import create_chroot_and_generate_pgo_profile as create_chroot_etc


class Test(unittest.TestCase):
    """Tests for generate_llvm_next_pgo."""

    def make_tempdir(self) -> Path:
        tempdir = Path(tempfile.mkdtemp(prefix="generate_llvm_next_pgo_test_"))
        self.addCleanup(lambda: shutil.rmtree(tempdir))
        return tempdir

    def test_path_translation_works(self):
        repo_root = Path("/some/repo")
        chroot_info = create_chroot_etc.ChrootInfo(
            chroot_name="my-chroot",
            out_dir_name="my-out",
        )
        self.assertEqual(
            create_chroot_etc.translate_chroot_path_to_out_of_chroot(
                repo_root, "/tmp/file/path", chroot_info
            ),
            repo_root / "my-out" / "tmp/file/path",
        )

    def test_llvm_ebuild_location(self):
        tempdir = self.make_tempdir()

        llvm_subdir = (
            tempdir / "src/third_party/chromiumos-overlay/sys-devel/llvm"
        )
        want_ebuild = llvm_subdir / "llvm-18.0.0_pre12345.ebuild"
        files = [
            llvm_subdir / "llvm-15.ebuild",
            llvm_subdir / "llvm-16.0.1-r3.ebuild",
            want_ebuild,
            llvm_subdir / "llvm-9999.ebuild",
        ]

        llvm_subdir.mkdir(parents=True)
        for f in files:
            f.touch()

        self.assertEqual(
            create_chroot_etc.locate_current_llvm_ebuild(tempdir),
            want_ebuild,
        )

    def test_llvm_hash_parsing(self):
        h = create_chroot_etc.parse_llvm_next_hash(
            textwrap.dedent(
                """\
            # Copyright blah blah
            EAPI=7
            LLVM_HASH="98f5a340975bc00197c57e39eb4ca26e2da0e8a2" # r496208
            LLVM_NEXT_HASH="14f0776550b5a49e1c42f49a00213f7f3fa047bf" # r498229
            # Snip
            CROS_WORKON_COMMIT=("${LLVM_NEXT_HASH}")
            """
            )
        )

        self.assertEqual(h, "14f0776550b5a49e1c42f49a00213f7f3fa047bf")


if __name__ == "__main__":
    unittest.main()
