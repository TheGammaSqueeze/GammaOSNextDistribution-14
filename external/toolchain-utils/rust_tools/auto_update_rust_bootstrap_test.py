#!/usr/bin/env python3
# Copyright 2023 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Tests for auto_update_rust_bootstrap."""


import os
from pathlib import Path
import shutil
import tempfile
import textwrap
import unittest
from unittest import mock

import auto_update_rust_bootstrap


_GIT_PUSH_OUTPUT = r"""
remote: Waiting for private key checker: 2/2 objects left
remote:
remote: Processing changes: new: 1 (\)
remote: Processing changes: new: 1 (|)
remote: Processing changes: new: 1 (/)
remote: Processing changes: refs: 1, new: 1 (/)
remote: Processing changes: refs: 1, new: 1 (/)
remote: Processing changes: refs: 1, new: 1 (/)
remote: Processing changes: refs: 1, new: 1, done
remote:
remote: SUCCESS
remote:
remote:   https://chromium-review.googlesource.com/c/chromiumos/overlays/chromiumos-overlay/+/5018826 rust-bootstrap: use prebuilts [WIP] [NEW]
remote:
To https://chromium.googlesource.com/chromiumos/overlays/chromiumos-overlay
 * [new reference]             HEAD -> refs/for/main
"""


class Test(unittest.TestCase):
    """Tests for auto_update_rust_bootstrap."""

    def make_tempdir(self) -> Path:
        tempdir = Path(
            tempfile.mkdtemp(prefix="auto_update_rust_bootstrap_test_")
        )
        self.addCleanup(shutil.rmtree, tempdir)
        return tempdir

    def test_git_cl_id_scraping(self):
        self.assertEqual(
            auto_update_rust_bootstrap.scrape_git_push_cl_id(_GIT_PUSH_OUTPUT),
            5018826,
        )

    def test_ebuild_linking_logic_handles_direct_relative_symlinks(self):
        tempdir = self.make_tempdir()
        target = tempdir / "target.ebuild"
        target.touch()
        (tempdir / "symlink.ebuild").symlink_to(target.name)
        self.assertTrue(
            auto_update_rust_bootstrap.is_ebuild_linked_to_in_dir(target)
        )

    def test_ebuild_linking_logic_handles_direct_absolute_symlinks(self):
        tempdir = self.make_tempdir()
        target = tempdir / "target.ebuild"
        target.touch()
        (tempdir / "symlink.ebuild").symlink_to(target)
        self.assertTrue(
            auto_update_rust_bootstrap.is_ebuild_linked_to_in_dir(target)
        )

    def test_ebuild_linking_logic_handles_indirect_relative_symlinks(self):
        tempdir = self.make_tempdir()
        target = tempdir / "target.ebuild"
        target.touch()
        (tempdir / "symlink.ebuild").symlink_to(
            Path("..") / tempdir.name / target.name
        )
        self.assertTrue(
            auto_update_rust_bootstrap.is_ebuild_linked_to_in_dir(target)
        )

    def test_ebuild_linking_logic_handles_broken_symlinks(self):
        tempdir = self.make_tempdir()
        target = tempdir / "target.ebuild"
        target.touch()
        (tempdir / "symlink.ebuild").symlink_to("doesnt_exist.ebuild")
        self.assertFalse(
            auto_update_rust_bootstrap.is_ebuild_linked_to_in_dir(target)
        )

    def test_ebuild_linking_logic_only_steps_through_one_symlink(self):
        tempdir = self.make_tempdir()
        target = tempdir / "target.ebuild"
        target.symlink_to("doesnt_exist.ebuild")
        (tempdir / "symlink.ebuild").symlink_to(target.name)
        self.assertTrue(
            auto_update_rust_bootstrap.is_ebuild_linked_to_in_dir(target)
        )

    def test_raw_bootstrap_seq_finding_functions(self):
        ebuild_contents = textwrap.dedent(
            """\
            # Some copyright
            FOO=bar
            # Comment about RUSTC_RAW_FULL_BOOTSTRAP_SEQUENCE=(
            RUSTC_RAW_FULL_BOOTSTRAP_SEQUENCE=( # another comment
                1.2.3 # (with a comment with parens)
                4.5.6
            )
            """
        )

        ebuild_lines = ebuild_contents.splitlines()
        (
            start,
            end,
        ) = auto_update_rust_bootstrap.find_raw_bootstrap_sequence_lines(
            ebuild_lines
        )
        self.assertEqual(start, len(ebuild_lines) - 4)
        self.assertEqual(end, len(ebuild_lines) - 1)

    def test_collect_ebuilds_by_version_ignores_older_versions(self):
        tempdir = self.make_tempdir()
        ebuild_170 = tempdir / "rust-bootstrap-1.70.0.ebuild"
        ebuild_170.touch()
        ebuild_170_r1 = tempdir / "rust-bootstrap-1.70.0-r1.ebuild"
        ebuild_170_r1.touch()
        ebuild_171_r2 = tempdir / "rust-bootstrap-1.71.1-r2.ebuild"
        ebuild_171_r2.touch()

        self.assertEqual(
            auto_update_rust_bootstrap.collect_ebuilds_by_version(tempdir),
            [
                (
                    auto_update_rust_bootstrap.EbuildVersion(
                        major=1, minor=70, patch=0, rev=1
                    ),
                    ebuild_170_r1,
                ),
                (
                    auto_update_rust_bootstrap.EbuildVersion(
                        major=1, minor=71, patch=1, rev=2
                    ),
                    ebuild_171_r2,
                ),
            ],
        )

    def test_has_prebuilt_works(self):
        tempdir = self.make_tempdir()
        ebuild = tempdir / "rust-bootstrap-1.70.0.ebuild"
        ebuild.write_text(
            textwrap.dedent(
                """\
                # Some copyright
                FOO=bar
                # Comment about RUSTC_RAW_FULL_BOOTSTRAP_SEQUENCE=(
                RUSTC_RAW_FULL_BOOTSTRAP_SEQUENCE=( # another comment
                    1.67.0
                    1.68.1
                    1.69.0
                )
                """
            ),
            encoding="utf-8",
        )

        self.assertTrue(
            auto_update_rust_bootstrap.version_listed_in_bootstrap_sequence(
                ebuild,
                auto_update_rust_bootstrap.EbuildVersion(
                    major=1,
                    minor=69,
                    patch=0,
                    rev=0,
                ),
            )
        )

        self.assertFalse(
            auto_update_rust_bootstrap.version_listed_in_bootstrap_sequence(
                ebuild,
                auto_update_rust_bootstrap.EbuildVersion(
                    major=1,
                    minor=70,
                    patch=0,
                    rev=0,
                ),
            )
        )

    def test_ebuild_updating_works(self):
        tempdir = self.make_tempdir()
        ebuild = tempdir / "rust-bootstrap-1.70.0.ebuild"
        ebuild.write_text(
            textwrap.dedent(
                """\
                # Some copyright
                FOO=bar
                RUSTC_RAW_FULL_BOOTSTRAP_SEQUENCE=(
                \t1.67.0
                \t1.68.1
                \t1.69.0
                )
                """
            ),
            encoding="utf-8",
        )

        auto_update_rust_bootstrap.add_version_to_bootstrap_sequence(
            ebuild,
            auto_update_rust_bootstrap.EbuildVersion(
                major=1,
                minor=70,
                patch=1,
                rev=2,
            ),
            dry_run=False,
        )

        self.assertEqual(
            ebuild.read_text(encoding="utf-8"),
            textwrap.dedent(
                """\
                # Some copyright
                FOO=bar
                RUSTC_RAW_FULL_BOOTSTRAP_SEQUENCE=(
                \t1.67.0
                \t1.68.1
                \t1.69.0
                \t1.70.1-r2
                )
                """
            ),
        )

    def test_ebuild_version_parsing_works(self):
        self.assertEqual(
            auto_update_rust_bootstrap.parse_ebuild_version(
                "rust-bootstrap-1.70.0-r2.ebuild"
            ),
            auto_update_rust_bootstrap.EbuildVersion(
                major=1, minor=70, patch=0, rev=2
            ),
        )

        self.assertEqual(
            auto_update_rust_bootstrap.parse_ebuild_version(
                "rust-bootstrap-2.80.3.ebuild"
            ),
            auto_update_rust_bootstrap.EbuildVersion(
                major=2, minor=80, patch=3, rev=0
            ),
        )

        with self.assertRaises(ValueError):
            auto_update_rust_bootstrap.parse_ebuild_version(
                "rust-bootstrap-2.80.3_pre1234.ebuild"
            )

    def test_raw_ebuild_version_parsing_works(self):
        self.assertEqual(
            auto_update_rust_bootstrap.parse_raw_ebuild_version("1.70.0-r2"),
            auto_update_rust_bootstrap.EbuildVersion(
                major=1, minor=70, patch=0, rev=2
            ),
        )

        with self.assertRaises(ValueError):
            auto_update_rust_bootstrap.parse_ebuild_version("2.80.3_pre1234")

    def test_ensure_newest_version_does_nothing_if_no_new_rust_version(self):
        tempdir = self.make_tempdir()
        rust = tempdir / "rust"
        rust.mkdir()
        (rust / "rust-1.70.0-r1.ebuild").touch()
        rust_bootstrap = tempdir / "rust-bootstrap"
        rust_bootstrap.mkdir()
        (rust_bootstrap / "rust-bootstrap-1.70.0.ebuild").touch()

        self.assertFalse(
            auto_update_rust_bootstrap.maybe_add_new_rust_bootstrap_version(
                tempdir, rust_bootstrap, dry_run=True
            )
        )

    @mock.patch.object(auto_update_rust_bootstrap, "update_ebuild_manifest")
    def test_ensure_newest_version_upgrades_rust_bootstrap_properly(
        self, update_ebuild_manifest
    ):
        tempdir = self.make_tempdir()
        rust = tempdir / "rust"
        rust.mkdir()
        (rust / "rust-1.71.0-r1.ebuild").touch()
        rust_bootstrap = tempdir / "rust-bootstrap"
        rust_bootstrap.mkdir()
        rust_bootstrap_1_70 = rust_bootstrap / "rust-bootstrap-1.70.0-r2.ebuild"

        rust_bootstrap_contents = textwrap.dedent(
            """\
            # Some copyright
            FOO=bar
            RUSTC_RAW_FULL_BOOTSTRAP_SEQUENCE=(
            \t1.67.0
            \t1.68.1
            \t1.69.0
            \t1.70.0-r1
            )
            """
        )
        rust_bootstrap_1_70.write_text(
            rust_bootstrap_contents, encoding="utf-8"
        )

        self.assertTrue(
            auto_update_rust_bootstrap.maybe_add_new_rust_bootstrap_version(
                tempdir, rust_bootstrap, dry_run=False, commit=False
            )
        )
        update_ebuild_manifest.assert_called_once()
        rust_bootstrap_1_71 = rust_bootstrap / "rust-bootstrap-1.71.0.ebuild"

        self.assertTrue(rust_bootstrap_1_70.is_symlink())
        self.assertEqual(
            os.readlink(rust_bootstrap_1_70),
            rust_bootstrap_1_71.name,
        )
        self.assertFalse(rust_bootstrap_1_71.is_symlink())
        self.assertEqual(
            rust_bootstrap_1_71.read_text(encoding="utf-8"),
            rust_bootstrap_contents,
        )

    def test_ensure_newest_version_breaks_if_prebuilt_is_not_available(self):
        tempdir = self.make_tempdir()
        rust = tempdir / "rust"
        rust.mkdir()
        (rust / "rust-1.71.0-r1.ebuild").touch()
        rust_bootstrap = tempdir / "rust-bootstrap"
        rust_bootstrap.mkdir()
        rust_bootstrap_1_70 = rust_bootstrap / "rust-bootstrap-1.70.0-r2.ebuild"

        rust_bootstrap_contents = textwrap.dedent(
            """\
            # Some copyright
            FOO=bar
            RUSTC_RAW_FULL_BOOTSTRAP_SEQUENCE=(
            \t1.67.0
            \t1.68.1
            \t1.69.0
            # Note: Missing 1.70.0 for rust-bootstrap-1.71.1
            )
            """
        )
        rust_bootstrap_1_70.write_text(
            rust_bootstrap_contents, encoding="utf-8"
        )

        with self.assertRaises(
            auto_update_rust_bootstrap.MissingRustBootstrapPrebuiltError
        ):
            auto_update_rust_bootstrap.maybe_add_new_rust_bootstrap_version(
                tempdir, rust_bootstrap, dry_run=True
            )

    def test_version_deletion_does_nothing_if_all_versions_are_needed(self):
        tempdir = self.make_tempdir()
        rust = tempdir / "rust"
        rust.mkdir()
        (rust / "rust-1.71.0-r1.ebuild").touch()
        rust_bootstrap = tempdir / "rust-bootstrap"
        rust_bootstrap.mkdir()
        (rust_bootstrap / "rust-bootstrap-1.70.0-r2.ebuild").touch()

        self.assertFalse(
            auto_update_rust_bootstrap.maybe_delete_old_rust_bootstrap_ebuilds(
                tempdir, rust_bootstrap, dry_run=True
            )
        )

    def test_version_deletion_ignores_newer_than_needed_versions(self):
        tempdir = self.make_tempdir()
        rust = tempdir / "rust"
        rust.mkdir()
        (rust / "rust-1.71.0-r1.ebuild").touch()
        rust_bootstrap = tempdir / "rust-bootstrap"
        rust_bootstrap.mkdir()
        (rust_bootstrap / "rust-bootstrap-1.70.0-r2.ebuild").touch()
        (rust_bootstrap / "rust-bootstrap-1.71.0-r1.ebuild").touch()
        (rust_bootstrap / "rust-bootstrap-1.72.0.ebuild").touch()

        self.assertFalse(
            auto_update_rust_bootstrap.maybe_delete_old_rust_bootstrap_ebuilds(
                tempdir, rust_bootstrap, dry_run=True
            )
        )

    @mock.patch.object(auto_update_rust_bootstrap, "update_ebuild_manifest")
    def test_version_deletion_deletes_old_files(self, update_ebuild_manifest):
        tempdir = self.make_tempdir()
        rust = tempdir / "rust"
        rust.mkdir()
        (rust / "rust-1.71.0-r1.ebuild").touch()
        rust_bootstrap = tempdir / "rust-bootstrap"
        rust_bootstrap.mkdir()
        needed_rust_bootstrap = (
            rust_bootstrap / "rust-bootstrap-1.70.0-r2.ebuild"
        )
        needed_rust_bootstrap.touch()

        # There are quite a few of these, so corner-cases are tested.

        # Symlink to outside of the group of files to delete.
        bootstrap_1_68_symlink = rust_bootstrap / "rust-bootstrap-1.68.0.ebuild"
        bootstrap_1_68_symlink.symlink_to(needed_rust_bootstrap.name)
        # Ensure that absolute symlinks are caught.
        bootstrap_1_68_symlink_abs = (
            rust_bootstrap / "rust-bootstrap-1.68.0-r1.ebuild"
        )
        bootstrap_1_68_symlink_abs.symlink_to(needed_rust_bootstrap)
        # Regular files should be no issue.
        bootstrap_1_69_regular = rust_bootstrap / "rust-bootstrap-1.69.0.ebuild"
        bootstrap_1_69_regular.touch()
        # Symlinks linking back into the set of files to delete should also be
        # no issue.
        bootstrap_1_69_symlink = (
            rust_bootstrap / "rust-bootstrap-1.69.0-r2.ebuild"
        )
        bootstrap_1_69_symlink.symlink_to(bootstrap_1_69_regular.name)

        self.assertTrue(
            auto_update_rust_bootstrap.maybe_delete_old_rust_bootstrap_ebuilds(
                tempdir,
                rust_bootstrap,
                dry_run=False,
                commit=False,
            )
        )
        update_ebuild_manifest.assert_called_once()

        self.assertFalse(bootstrap_1_68_symlink.exists())
        self.assertFalse(bootstrap_1_68_symlink_abs.exists())
        self.assertFalse(bootstrap_1_69_regular.exists())
        self.assertFalse(bootstrap_1_69_symlink.exists())
        self.assertTrue(needed_rust_bootstrap.exists())

    def test_version_deletion_raises_when_old_file_has_dep(self):
        tempdir = self.make_tempdir()
        rust = tempdir / "rust"
        rust.mkdir()
        (rust / "rust-1.71.0-r1.ebuild").touch()
        rust_bootstrap = tempdir / "rust-bootstrap"
        rust_bootstrap.mkdir()
        old_rust_bootstrap = rust_bootstrap / "rust-bootstrap-1.69.0-r1.ebuild"
        old_rust_bootstrap.touch()
        (rust_bootstrap / "rust-bootstrap-1.70.0-r2.ebuild").symlink_to(
            old_rust_bootstrap.name
        )

        with self.assertRaises(
            auto_update_rust_bootstrap.OldEbuildIsLinkedToError
        ):
            auto_update_rust_bootstrap.maybe_delete_old_rust_bootstrap_ebuilds(
                tempdir, rust_bootstrap, dry_run=True
            )


if __name__ == "__main__":
    unittest.main()
