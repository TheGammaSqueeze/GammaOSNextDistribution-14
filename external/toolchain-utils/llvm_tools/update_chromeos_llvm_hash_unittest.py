#!/usr/bin/env python3
# Copyright 2019 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Unit tests for updating LLVM hashes."""

import os
from pathlib import Path
import subprocess
import sys
import tempfile
from typing import Optional, Union
import unittest
from unittest import mock

import chroot
import failure_modes
import get_llvm_hash
import patch_utils
import test_helpers
import update_chromeos_llvm_hash


# These are unittests; protected access is OK to a point.
# pylint: disable=protected-access


class UpdateLLVMHashTest(unittest.TestCase):
    """Test class for updating LLVM hashes of packages."""

    @staticmethod
    def _make_patch_entry(
        relpath: Union[str, Path], workdir: Optional[Path] = None
    ) -> patch_utils.PatchEntry:
        if workdir is None:
            workdir = Path("llvm_tools/update_chromeos_llvm_hash_unittest.py")
        return patch_utils.PatchEntry(
            workdir=workdir,
            rel_patch_path=str(relpath),
            metadata={},
            platforms=["chromiumos"],
            version_range={"from": None, "until": None},
            verify_workdir=False,
        )

    @mock.patch.object(os.path, "realpath")
    def testDefaultCrosRootFromCrOSCheckout(self, mock_llvm_tools):
        llvm_tools_path = (
            "/path/to/cros/src/third_party/toolchain-utils/llvm_tools"
        )
        mock_llvm_tools.return_value = llvm_tools_path
        self.assertEqual(
            update_chromeos_llvm_hash.defaultCrosRoot(), Path("/path/to/cros")
        )

    @mock.patch.object(os.path, "realpath")
    def testDefaultCrosRootFromOutsideCrOSCheckout(self, mock_llvm_tools):
        mock_llvm_tools.return_value = "~/toolchain-utils/llvm_tools"
        self.assertEqual(
            update_chromeos_llvm_hash.defaultCrosRoot(),
            Path.home() / "chromiumos",
        )

    # Simulate behavior of 'os.path.isfile()' when the ebuild path to a package
    # does not exist.
    @mock.patch.object(os.path, "isfile", return_value=False)
    def testFailedToUpdateLLVMHashForInvalidEbuildPath(self, mock_isfile):
        ebuild_path = Path("/some/path/to/package.ebuild")
        llvm_variant = update_chromeos_llvm_hash.LLVMVariant.current
        git_hash = "a123testhash1"
        svn_version = 1000

        # Verify the exception is raised when the ebuild path does not exist.
        with self.assertRaises(ValueError) as err:
            update_chromeos_llvm_hash.UpdateEbuildLLVMHash(
                ebuild_path, llvm_variant, git_hash, svn_version
            )

        self.assertEqual(
            str(err.exception),
            "Invalid ebuild path provided: %s" % ebuild_path,
        )

        mock_isfile.assert_called_once()

    # Simulate 'os.path.isfile' behavior on a valid ebuild path.
    @mock.patch.object(os.path, "isfile", return_value=True)
    def testFailedToUpdateLLVMHash(self, mock_isfile):
        # Create a temporary file to simulate an ebuild file of a package.
        with test_helpers.CreateTemporaryJsonFile() as ebuild_file:
            with open(ebuild_file, "w", encoding="utf-8") as f:
                f.write(
                    "\n".join(
                        [
                            "First line in the ebuild",
                            "Second line in the ebuild",
                            "Last line in the ebuild",
                        ]
                    )
                )

            llvm_variant = update_chromeos_llvm_hash.LLVMVariant.current
            git_hash = "a123testhash1"
            svn_version = 1000

            # Verify the exception is raised when the ebuild file does not have
            # 'LLVM_HASH'.
            with self.assertRaises(ValueError) as err:
                update_chromeos_llvm_hash.UpdateEbuildLLVMHash(
                    Path(ebuild_file), llvm_variant, git_hash, svn_version
                )

            self.assertEqual(str(err.exception), "Failed to update LLVM_HASH")

            llvm_variant = update_chromeos_llvm_hash.LLVMVariant.next

        self.assertEqual(mock_isfile.call_count, 2)

    # Simulate 'os.path.isfile' behavior on a valid ebuild path.
    @mock.patch.object(os.path, "isfile", return_value=True)
    def testFailedToUpdateLLVMNextHash(self, mock_isfile):
        # Create a temporary file to simulate an ebuild file of a package.
        with test_helpers.CreateTemporaryJsonFile() as ebuild_file:
            with open(ebuild_file, "w", encoding="utf-8") as f:
                f.write(
                    "\n".join(
                        [
                            "First line in the ebuild",
                            "Second line in the ebuild",
                            "Last line in the ebuild",
                        ]
                    )
                )

            llvm_variant = update_chromeos_llvm_hash.LLVMVariant.next
            git_hash = "a123testhash1"
            svn_version = 1000

            # Verify the exception is raised when the ebuild file does not have
            # 'LLVM_NEXT_HASH'.
            with self.assertRaises(ValueError) as err:
                update_chromeos_llvm_hash.UpdateEbuildLLVMHash(
                    Path(ebuild_file), llvm_variant, git_hash, svn_version
                )

            self.assertEqual(
                str(err.exception), "Failed to update LLVM_NEXT_HASH"
            )

        self.assertEqual(mock_isfile.call_count, 2)

    @mock.patch.object(os.path, "isfile", return_value=True)
    @mock.patch.object(subprocess, "check_output", return_value=None)
    def testSuccessfullyStageTheEbuildForCommitForLLVMHashUpdate(
        self, mock_stage_commit_command, mock_isfile
    ):
        # Create a temporary file to simulate an ebuild file of a package.
        with test_helpers.CreateTemporaryJsonFile() as ebuild_file:
            # Updates LLVM_HASH to 'git_hash' and revision to
            # 'svn_version'.
            llvm_variant = update_chromeos_llvm_hash.LLVMVariant.current
            git_hash = "a123testhash1"
            svn_version = 1000

            with open(ebuild_file, "w", encoding="utf-8") as f:
                f.write(
                    "\n".join(
                        [
                            "First line in the ebuild",
                            "Second line in the ebuild",
                            'LLVM_HASH="a12b34c56d78e90" # r500',
                            "Last line in the ebuild",
                        ]
                    )
                )

            update_chromeos_llvm_hash.UpdateEbuildLLVMHash(
                Path(ebuild_file), llvm_variant, git_hash, svn_version
            )

            expected_file_contents = [
                "First line in the ebuild\n",
                "Second line in the ebuild\n",
                'LLVM_HASH="a123testhash1" # r1000\n',
                "Last line in the ebuild",
            ]

            # Verify the new file contents of the ebuild file match the expected
            # file contents.
            with open(ebuild_file, encoding="utf-8") as new_file:
                self.assertListEqual(
                    new_file.readlines(), expected_file_contents
                )

        self.assertEqual(mock_isfile.call_count, 2)

        mock_stage_commit_command.assert_called_once()

    @mock.patch.object(os.path, "isfile", return_value=True)
    @mock.patch.object(subprocess, "check_output", return_value=None)
    def testSuccessfullyStageTheEbuildForCommitForLLVMNextHashUpdate(
        self, mock_stage_commit_command, mock_isfile
    ):
        # Create a temporary file to simulate an ebuild file of a package.
        with test_helpers.CreateTemporaryJsonFile() as ebuild_file:
            # Updates LLVM_NEXT_HASH to 'git_hash' and revision to
            # 'svn_version'.
            llvm_variant = update_chromeos_llvm_hash.LLVMVariant.next
            git_hash = "a123testhash1"
            svn_version = 1000

            with open(ebuild_file, "w", encoding="utf-8") as f:
                f.write(
                    "\n".join(
                        [
                            "First line in the ebuild",
                            "Second line in the ebuild",
                            'LLVM_NEXT_HASH="a12b34c56d78e90" # r500',
                            "Last line in the ebuild",
                        ]
                    )
                )

            update_chromeos_llvm_hash.UpdateEbuildLLVMHash(
                Path(ebuild_file), llvm_variant, git_hash, svn_version
            )

            expected_file_contents = [
                "First line in the ebuild\n",
                "Second line in the ebuild\n",
                'LLVM_NEXT_HASH="a123testhash1" # r1000\n',
                "Last line in the ebuild",
            ]

            # Verify the new file contents of the ebuild file match the expected
            # file contents.
            with open(ebuild_file, encoding="utf-8") as new_file:
                self.assertListEqual(
                    new_file.readlines(), expected_file_contents
                )

        self.assertEqual(mock_isfile.call_count, 2)

        mock_stage_commit_command.assert_called_once()

    @mock.patch.object(get_llvm_hash, "GetLLVMMajorVersion")
    @mock.patch.object(os.path, "islink", return_value=False)
    def testFailedToUprevEbuildToVersionForInvalidSymlink(
        self, mock_islink, mock_llvm_version
    ):
        symlink_path = "/path/to/chroot/package/package.ebuild"
        svn_version = 1000
        git_hash = "badf00d"
        mock_llvm_version.return_value = "1234"

        # Verify the exception is raised when a invalid symbolic link is
        # passed in.
        with self.assertRaises(ValueError) as err:
            update_chromeos_llvm_hash.UprevEbuildToVersion(
                symlink_path, svn_version, git_hash
            )

        self.assertEqual(
            str(err.exception), "Invalid symlink provided: %s" % symlink_path
        )

        mock_islink.assert_called_once()
        mock_llvm_version.assert_not_called()

    @mock.patch.object(os.path, "islink", return_value=False)
    def testFailedToUprevEbuildSymlinkForInvalidSymlink(self, mock_islink):
        symlink_path = "/path/to/chroot/package/package.ebuild"

        # Verify the exception is raised when a invalid symbolic link is
        # passed in.
        with self.assertRaises(ValueError) as err:
            update_chromeos_llvm_hash.UprevEbuildSymlink(symlink_path)

        self.assertEqual(
            str(err.exception), "Invalid symlink provided: %s" % symlink_path
        )

        mock_islink.assert_called_once()

    @mock.patch.object(get_llvm_hash, "GetLLVMMajorVersion")
    # Simulate 'os.path.islink' when a symbolic link is passed in.
    @mock.patch.object(os.path, "islink", return_value=True)
    # Simulate 'os.path.realpath' when a symbolic link is passed in.
    @mock.patch.object(os.path, "realpath", return_value=True)
    def testFailedToUprevEbuildToVersion(
        self, mock_realpath, mock_islink, mock_llvm_version
    ):
        symlink_path = "/path/to/chroot/llvm/llvm_pre123_p.ebuild"
        mock_realpath.return_value = "/abs/path/to/llvm/llvm_pre123_p.ebuild"
        git_hash = "badf00d"
        mock_llvm_version.return_value = "1234"
        svn_version = 1000

        # Verify the exception is raised when the symlink does not match the
        # expected pattern
        with self.assertRaises(ValueError) as err:
            update_chromeos_llvm_hash.UprevEbuildToVersion(
                symlink_path, svn_version, git_hash
            )

        self.assertEqual(str(err.exception), "Failed to uprev the ebuild.")

        mock_llvm_version.assert_called_once_with(git_hash)
        mock_islink.assert_called_once_with(symlink_path)

    # Simulate 'os.path.islink' when a symbolic link is passed in.
    @mock.patch.object(os.path, "islink", return_value=True)
    def testFailedToUprevEbuildSymlink(self, mock_islink):
        symlink_path = "/path/to/chroot/llvm/llvm_pre123_p.ebuild"

        # Verify the exception is raised when the symlink does not match the
        # expected pattern
        with self.assertRaises(ValueError) as err:
            update_chromeos_llvm_hash.UprevEbuildSymlink(symlink_path)

        self.assertEqual(str(err.exception), "Failed to uprev the symlink.")

        mock_islink.assert_called_once_with(symlink_path)

    @mock.patch.object(get_llvm_hash, "GetLLVMMajorVersion")
    @mock.patch.object(os.path, "islink", return_value=True)
    @mock.patch.object(os.path, "realpath")
    @mock.patch.object(subprocess, "check_output", return_value=None)
    def testSuccessfullyUprevEbuildToVersionLLVM(
        self,
        mock_command_output,
        mock_realpath,
        mock_islink,
        mock_llvm_version,
    ):
        symlink = "/path/to/llvm/llvm-12.0_pre3_p2-r10.ebuild"
        ebuild = "/abs/path/to/llvm/llvm-12.0_pre3_p2.ebuild"
        mock_realpath.return_value = ebuild
        git_hash = "badf00d"
        mock_llvm_version.return_value = "1234"
        svn_version = 1000

        update_chromeos_llvm_hash.UprevEbuildToVersion(
            symlink, svn_version, git_hash
        )

        mock_llvm_version.assert_called_once_with(git_hash)

        mock_islink.assert_called()

        mock_realpath.assert_called_once_with(symlink)

        mock_command_output.assert_called()

        # Verify commands
        symlink_dir = os.path.dirname(symlink)
        new_ebuild = "/abs/path/to/llvm/llvm-1234.0_pre1000.ebuild"
        new_symlink = new_ebuild[: -len(".ebuild")] + "-r1.ebuild"

        expected_cmd = ["git", "-C", symlink_dir, "mv", ebuild, new_ebuild]
        self.assertEqual(
            mock_command_output.call_args_list[0], mock.call(expected_cmd)
        )

        expected_cmd = ["ln", "-s", "-r", new_ebuild, new_symlink]
        self.assertEqual(
            mock_command_output.call_args_list[1], mock.call(expected_cmd)
        )

        expected_cmd = ["git", "-C", symlink_dir, "add", new_symlink]
        self.assertEqual(
            mock_command_output.call_args_list[2], mock.call(expected_cmd)
        )

        expected_cmd = ["git", "-C", symlink_dir, "rm", symlink]
        self.assertEqual(
            mock_command_output.call_args_list[3], mock.call(expected_cmd)
        )

    @mock.patch.object(
        chroot,
        "GetChrootEbuildPaths",
        return_value=["/chroot/path/test.ebuild"],
    )
    @mock.patch.object(subprocess, "check_output", return_value="")
    def testManifestUpdate(self, mock_subprocess, mock_ebuild_paths):
        manifest_packages = ["sys-devel/llvm"]
        chroot_path = "/path/to/chroot"
        update_chromeos_llvm_hash.UpdatePortageManifests(
            manifest_packages, Path(chroot_path)
        )

        args = mock_subprocess.call_args_list[0]
        manifest_cmd = (
            [
                "cros_sdk",
                "--",
                "ebuild",
                "/chroot/path/test.ebuild",
                "manifest",
            ],
        )
        self.assertEqual(args[0], manifest_cmd)

        args = mock_subprocess.call_args_list[1]
        git_add_cmd = (
            [
                "cros_sdk",
                "--",
                "git",
                "-C",
                "/chroot/path",
                "add",
                "Manifest",
            ],
        )
        self.assertEqual(args[0], git_add_cmd)
        mock_ebuild_paths.assert_called_once()

    @mock.patch.object(get_llvm_hash, "GetLLVMMajorVersion")
    @mock.patch.object(os.path, "islink", return_value=True)
    @mock.patch.object(os.path, "realpath")
    @mock.patch.object(subprocess, "check_output", return_value=None)
    def testSuccessfullyUprevEbuildToVersionNonLLVM(
        self, mock_command_output, mock_realpath, mock_islink, mock_llvm_version
    ):
        symlink = (
            "/abs/path/to/compiler-rt/compiler-rt-12.0_pre314159265-r4.ebuild"
        )
        ebuild = "/abs/path/to/compiler-rt/compiler-rt-12.0_pre314159265.ebuild"
        mock_realpath.return_value = ebuild
        mock_llvm_version.return_value = "1234"
        svn_version = 1000
        git_hash = "5678"

        update_chromeos_llvm_hash.UprevEbuildToVersion(
            symlink, svn_version, git_hash
        )

        mock_islink.assert_called()

        mock_realpath.assert_called_once_with(symlink)

        mock_llvm_version.assert_called_once_with(git_hash)

        mock_command_output.assert_called()

        # Verify commands
        symlink_dir = os.path.dirname(symlink)
        new_ebuild = (
            "/abs/path/to/compiler-rt/compiler-rt-1234.0_pre1000.ebuild"
        )
        new_symlink = new_ebuild[: -len(".ebuild")] + "-r1.ebuild"

        expected_cmd = ["git", "-C", symlink_dir, "mv", ebuild, new_ebuild]
        self.assertEqual(
            mock_command_output.call_args_list[0], mock.call(expected_cmd)
        )

        expected_cmd = ["ln", "-s", "-r", new_ebuild, new_symlink]
        self.assertEqual(
            mock_command_output.call_args_list[1], mock.call(expected_cmd)
        )

        expected_cmd = ["git", "-C", symlink_dir, "add", new_symlink]
        self.assertEqual(
            mock_command_output.call_args_list[2], mock.call(expected_cmd)
        )

        expected_cmd = ["git", "-C", symlink_dir, "rm", symlink]
        self.assertEqual(
            mock_command_output.call_args_list[3], mock.call(expected_cmd)
        )

    @mock.patch.object(os.path, "islink", return_value=True)
    @mock.patch.object(subprocess, "check_output", return_value=None)
    def testSuccessfullyUprevEbuildSymlink(
        self, mock_command_output, mock_islink
    ):
        symlink_to_uprev = "/symlink/to/package-r1.ebuild"

        update_chromeos_llvm_hash.UprevEbuildSymlink(symlink_to_uprev)

        mock_islink.assert_called_once_with(symlink_to_uprev)

        mock_command_output.assert_called_once()

    @mock.patch.object(subprocess, "check_output", return_value=None)
    def testSuccessfullyRemovedPatchesFromFilesDir(self, mock_run_cmd):
        patches_to_remove_list = [
            "/abs/path/to/filesdir/cherry/fix_output.patch",
            "/abs/path/to/filesdir/display_results.patch",
        ]

        update_chromeos_llvm_hash.RemovePatchesFromFilesDir(
            patches_to_remove_list
        )

        self.assertEqual(mock_run_cmd.call_count, 2)

    @mock.patch.object(os.path, "isfile", return_value=False)
    def testInvalidPatchMetadataFileStagedForCommit(self, mock_isfile):
        patch_metadata_path = "/abs/path/to/filesdir/PATCHES"

        # Verify the exception is raised when the absolute path to the patch
        # metadata file does not exist or is not a file.
        with self.assertRaises(ValueError) as err:
            update_chromeos_llvm_hash.StagePatchMetadataFileForCommit(
                patch_metadata_path
            )

        self.assertEqual(
            str(err.exception),
            "Invalid patch metadata file provided: " "%s" % patch_metadata_path,
        )

        mock_isfile.assert_called_once()

    @mock.patch.object(os.path, "isfile", return_value=True)
    @mock.patch.object(subprocess, "check_output", return_value=None)
    def testSuccessfullyStagedPatchMetadataFileForCommit(self, mock_run_cmd, _):
        patch_metadata_path = "/abs/path/to/filesdir/PATCHES.json"

        update_chromeos_llvm_hash.StagePatchMetadataFileForCommit(
            patch_metadata_path
        )

        mock_run_cmd.assert_called_once()

    def testNoPatchResultsForCommit(self):
        package_1_patch_info = patch_utils.PatchInfo(
            applied_patches=[self._make_patch_entry("display_results.patch")],
            failed_patches=[self._make_patch_entry("fixes_output.patch")],
            non_applicable_patches=[],
            disabled_patches=[],
            removed_patches=[],
            modified_metadata=None,
        )

        package_2_patch_info = patch_utils.PatchInfo(
            applied_patches=[
                self._make_patch_entry("redirects_stdout.patch"),
                self._make_patch_entry("fix_display.patch"),
            ],
            failed_patches=[],
            non_applicable_patches=[],
            disabled_patches=[],
            removed_patches=[],
            modified_metadata=None,
        )

        test_package_info_dict = {
            "test-packages/package1": package_1_patch_info,
            "test-packages/package2": package_2_patch_info,
        }

        test_commit_message = ["Updated packages"]

        self.assertListEqual(
            update_chromeos_llvm_hash.StagePackagesPatchResultsForCommit(
                test_package_info_dict, test_commit_message
            ),
            test_commit_message,
        )

    @mock.patch.object(
        update_chromeos_llvm_hash, "StagePatchMetadataFileForCommit"
    )
    @mock.patch.object(update_chromeos_llvm_hash, "RemovePatchesFromFilesDir")
    def testAddedPatchResultsForCommit(
        self, mock_remove_patches, mock_stage_patches_for_commit
    ):
        package_1_patch_info = patch_utils.PatchInfo(
            applied_patches=[],
            failed_patches=[],
            non_applicable_patches=[],
            disabled_patches=["fixes_output.patch"],
            removed_patches=[],
            modified_metadata="/abs/path/to/filesdir/PATCHES.json",
        )

        package_2_patch_info = patch_utils.PatchInfo(
            applied_patches=[self._make_patch_entry("fix_display.patch")],
            failed_patches=[],
            non_applicable_patches=[],
            disabled_patches=[],
            removed_patches=["/abs/path/to/filesdir/redirect_stdout.patch"],
            modified_metadata="/abs/path/to/filesdir/PATCHES.json",
        )

        test_package_info_dict = {
            "test-packages/package1": package_1_patch_info,
            "test-packages/package2": package_2_patch_info,
        }

        test_commit_message = ["Updated packages"]

        expected_commit_messages = [
            "Updated packages",
            "\nFor the package test-packages/package1:",
            "The patch metadata file PATCHES.json was modified",
            "The following patches were disabled:",
            "fixes_output.patch",
            "\nFor the package test-packages/package2:",
            "The patch metadata file PATCHES.json was modified",
            "The following patches were removed:",
            "redirect_stdout.patch",
        ]

        self.assertListEqual(
            update_chromeos_llvm_hash.StagePackagesPatchResultsForCommit(
                test_package_info_dict, test_commit_message
            ),
            expected_commit_messages,
        )

        path_to_removed_patch = "/abs/path/to/filesdir/redirect_stdout.patch"

        mock_remove_patches.assert_called_once_with([path_to_removed_patch])

        self.assertEqual(mock_stage_patches_for_commit.call_count, 2)

    def setup_mock_src_tree(self, src_tree: Path):
        package_dir = (
            src_tree / "src/third_party/chromiumos-overlay/sys-devel/llvm"
        )
        package_dir.mkdir(parents=True)
        ebuild_path = package_dir / "llvm-00.00_pre0_p0.ebuild"
        with ebuild_path.open("w", encoding="utf-8") as f:
            f.writelines(
                [
                    'LLVM_HASH="abcdef123456" # r123456',
                    'LLVM_NEXT_HASH="987654321fedcba" # r99453',
                ]
            )
        symlink_path = package_dir / "llvm-00.00_pre0_p0-r1234.ebuild"
        symlink_path.symlink_to(ebuild_path)
        return package_dir, ebuild_path, symlink_path

    def testPortagePackageConstruction(self):
        with tempfile.TemporaryDirectory(
            "update_chromeos_llvm_hash.tmp"
        ) as workdir_str:
            src_tree = Path(workdir_str)
            package_dir, ebuild_path, symlink_path = self.setup_mock_src_tree(
                src_tree
            )

            # Test that we're upreving if there's a symlink.
            def mock_find_package_ebuild(_, package_name):
                self.assertEqual(
                    package_name,
                    f"{package_dir.parent.name}/{package_dir.name}",
                )
                return symlink_path

            with mock.patch(
                "update_chromeos_llvm_hash.PortagePackage.find_package_ebuild",
                mock_find_package_ebuild,
            ):
                pkg = update_chromeos_llvm_hash.PortagePackage(
                    src_tree, "sys-devel/llvm"
                )
                self.assertEqual(pkg.uprev_target, symlink_path.absolute())
                self.assertEqual(pkg.ebuild_path, ebuild_path.absolute())
                self.assertEqual(pkg.live_ebuild(), None)

                # Make sure if the live ebuild is there, we find it.
                live_ebuild_path = package_dir / "llvm-9999.ebuild"
                live_ebuild_path.touch()

                pkg = update_chromeos_llvm_hash.PortagePackage(
                    src_tree, "sys-devel/llvm"
                )
                self.assertEqual(pkg.live_ebuild(), live_ebuild_path)

    @mock.patch("subprocess.run")
    @mock.patch("subprocess.check_output")
    @mock.patch.object(get_llvm_hash, "GetLLVMMajorVersion")
    def testUpdatePackages(
        self, mock_llvm_major_version, _mock_check_output, _mock_run
    ):
        mock_llvm_major_version.return_value = "17"
        with tempfile.TemporaryDirectory(
            "update_chromeos_llvm_hash.tmp"
        ) as workdir_str:
            src_tree = Path(workdir_str)
            _package_dir, _ebuild_path, symlink_path = self.setup_mock_src_tree(
                src_tree
            )

            def mock_find_package_ebuild(*_):
                return symlink_path

            with mock.patch(
                "update_chromeos_llvm_hash.PortagePackage.find_package_ebuild",
                mock_find_package_ebuild,
            ):
                pkg = update_chromeos_llvm_hash.PortagePackage(
                    src_tree, "sys-devel/llvm"
                )
                pkg.update(
                    update_chromeos_llvm_hash.LLVMVariant.current,
                    "beef3333",
                    3333,
                )

    @mock.patch.object(chroot, "VerifyChromeOSRoot")
    @mock.patch.object(chroot, "VerifyOutsideChroot")
    @mock.patch.object(get_llvm_hash, "GetLLVMHashAndVersionFromSVNOption")
    @mock.patch.object(update_chromeos_llvm_hash, "UpdatePackages")
    def testMainDefaults(
        self,
        mock_update_packages,
        mock_gethash,
        mock_outside_chroot,
        mock_chromeos_root,
    ):
        git_hash = "1234abcd"
        svn_version = 5678
        mock_gethash.return_value = (git_hash, svn_version)
        argv = [
            "./update_chromeos_llvm_hash_unittest.py",
            "--llvm_version",
            "google3",
        ]

        with mock.patch.object(sys, "argv", argv) as mock.argv:
            update_chromeos_llvm_hash.main()

        expected_packages = set(update_chromeos_llvm_hash.DEFAULT_PACKAGES)
        expected_manifest_packages = set(
            update_chromeos_llvm_hash.DEFAULT_MANIFEST_PACKAGES,
        )
        expected_llvm_variant = update_chromeos_llvm_hash.LLVMVariant.current
        expected_chroot = update_chromeos_llvm_hash.defaultCrosRoot()
        mock_update_packages.assert_called_once_with(
            packages=expected_packages,
            manifest_packages=expected_manifest_packages,
            llvm_variant=expected_llvm_variant,
            git_hash=git_hash,
            svn_version=svn_version,
            chroot_path=expected_chroot,
            mode=failure_modes.FailureModes.FAIL,
            git_hash_source="google3",
            extra_commit_msg_lines=None,
            delete_branch=True,
            upload_changes=True,
        )
        mock_outside_chroot.assert_called()
        mock_chromeos_root.assert_called()

    @mock.patch.object(chroot, "VerifyChromeOSRoot")
    @mock.patch.object(chroot, "VerifyOutsideChroot")
    @mock.patch.object(get_llvm_hash, "GetLLVMHashAndVersionFromSVNOption")
    @mock.patch.object(update_chromeos_llvm_hash, "UpdatePackages")
    def testMainLlvmNext(
        self,
        mock_update_packages,
        mock_gethash,
        mock_outside_chroot,
        mock_chromeos_root,
    ):
        git_hash = "1234abcd"
        svn_version = 5678
        mock_gethash.return_value = (git_hash, svn_version)
        argv = [
            "./update_chromeos_llvm_hash_unittest.py",
            "--llvm_version",
            "google3",
            "--is_llvm_next",
        ]

        with mock.patch.object(sys, "argv", argv) as mock.argv:
            update_chromeos_llvm_hash.main()

        expected_packages = set(update_chromeos_llvm_hash.DEFAULT_PACKAGES)
        expected_llvm_variant = update_chromeos_llvm_hash.LLVMVariant.next
        expected_chroot = update_chromeos_llvm_hash.defaultCrosRoot()
        # llvm-next upgrade does not update manifest by default.
        mock_update_packages.assert_called_once_with(
            packages=expected_packages,
            manifest_packages=set(),
            llvm_variant=expected_llvm_variant,
            git_hash=git_hash,
            svn_version=svn_version,
            chroot_path=expected_chroot,
            mode=failure_modes.FailureModes.FAIL,
            git_hash_source="google3",
            extra_commit_msg_lines=None,
            delete_branch=True,
            upload_changes=True,
        )
        mock_outside_chroot.assert_called()
        mock_chromeos_root.assert_called()

    @mock.patch.object(chroot, "VerifyChromeOSRoot")
    @mock.patch.object(chroot, "VerifyOutsideChroot")
    @mock.patch.object(get_llvm_hash, "GetLLVMHashAndVersionFromSVNOption")
    @mock.patch.object(update_chromeos_llvm_hash, "UpdatePackages")
    def testMainAllArgs(
        self,
        mock_update_packages,
        mock_gethash,
        mock_outside_chroot,
        mock_chromeos_root,
    ):
        packages_to_update = "test-packages/package1,test-libs/lib1"
        manifest_packages = "test-libs/lib1,test-libs/lib2"
        failure_mode = failure_modes.FailureModes.REMOVE_PATCHES
        chroot_path = Path("/some/path/to/chroot")
        llvm_ver = 435698
        git_hash = "1234abcd"
        svn_version = 5678
        mock_gethash.return_value = (git_hash, svn_version)

        argv = [
            "./update_chromeos_llvm_hash_unittest.py",
            "--llvm_version",
            str(llvm_ver),
            "--is_llvm_next",
            "--chroot_path",
            str(chroot_path),
            "--update_packages",
            packages_to_update,
            "--manifest_packages",
            manifest_packages,
            "--failure_mode",
            failure_mode.value,
            "--patch_metadata_file",
            "META.json",
        ]

        with mock.patch.object(sys, "argv", argv) as mock.argv:
            update_chromeos_llvm_hash.main()

        expected_packages = {"test-packages/package1", "test-libs/lib1"}
        expected_manifest_packages = {"test-libs/lib1", "test-libs/lib2"}
        expected_llvm_variant = update_chromeos_llvm_hash.LLVMVariant.next
        mock_update_packages.assert_called_once_with(
            packages=expected_packages,
            manifest_packages=expected_manifest_packages,
            llvm_variant=expected_llvm_variant,
            git_hash=git_hash,
            svn_version=svn_version,
            chroot_path=chroot_path,
            mode=failure_mode,
            git_hash_source=llvm_ver,
            extra_commit_msg_lines=None,
            delete_branch=True,
            upload_changes=True,
        )
        mock_outside_chroot.assert_called()
        mock_chromeos_root.assert_called()

    @mock.patch.object(subprocess, "check_output", return_value=None)
    @mock.patch.object(get_llvm_hash, "GetLLVMMajorVersion")
    def testEnsurePackageMaskContainsExisting(
        self, mock_llvm_version, mock_git_add
    ):
        chroot_path = "absolute/path/to/chroot"
        git_hash = "badf00d"
        mock_llvm_version.return_value = "1234"
        with mock.patch(
            "update_chromeos_llvm_hash.open",
            mock.mock_open(read_data="\n=sys-devel/llvm-1234.0_pre*\n"),
            create=True,
        ) as mock_file:
            update_chromeos_llvm_hash.EnsurePackageMaskContains(
                chroot_path, git_hash
            )
            handle = mock_file()
            handle.write.assert_not_called()
        mock_llvm_version.assert_called_once_with(git_hash)

        overlay_dir = (
            "absolute/path/to/chroot/src/third_party/chromiumos-overlay"
        )
        mask_path = overlay_dir + "/profiles/targets/chromeos/package.mask"
        mock_git_add.assert_called_once_with(
            ["git", "-C", overlay_dir, "add", mask_path]
        )

    @mock.patch.object(subprocess, "check_output", return_value=None)
    @mock.patch.object(get_llvm_hash, "GetLLVMMajorVersion")
    def testEnsurePackageMaskContainsNotExisting(
        self, mock_llvm_version, mock_git_add
    ):
        chroot_path = "absolute/path/to/chroot"
        git_hash = "badf00d"
        mock_llvm_version.return_value = "1234"
        with mock.patch(
            "update_chromeos_llvm_hash.open",
            mock.mock_open(read_data="nothing relevant"),
            create=True,
        ) as mock_file:
            update_chromeos_llvm_hash.EnsurePackageMaskContains(
                chroot_path, git_hash
            )
            handle = mock_file()
            handle.write.assert_called_once_with(
                "=sys-devel/llvm-1234.0_pre*\n"
            )
        mock_llvm_version.assert_called_once_with(git_hash)

        overlay_dir = (
            "absolute/path/to/chroot/src/third_party/chromiumos-overlay"
        )
        mask_path = overlay_dir + "/profiles/targets/chromeos/package.mask"
        mock_git_add.assert_called_once_with(
            ["git", "-C", overlay_dir, "add", mask_path]
        )


if __name__ == "__main__":
    unittest.main()
