#!/usr/bin/env python3
# Copyright 2019 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Tests for git_llvm_rev."""

import unittest

import git_llvm_rev
import llvm_project


def get_llvm_config() -> git_llvm_rev.LLVMConfig:
    return git_llvm_rev.LLVMConfig(
        dir=llvm_project.get_location(), remote="origin"
    )


class Test(unittest.TestCase):
    """Test cases for git_llvm_rev."""

    def rev_to_sha_with_round_trip(self, rev: git_llvm_rev.Rev) -> str:
        config = get_llvm_config()
        sha = git_llvm_rev.translate_rev_to_sha(config, rev)
        roundtrip_rev = git_llvm_rev.translate_sha_to_rev(config, sha)
        self.assertEqual(roundtrip_rev, rev)
        return sha

    def test_sha_to_rev_on_base_sha_works(self) -> None:
        sha = self.rev_to_sha_with_round_trip(
            git_llvm_rev.Rev(
                branch=git_llvm_rev.MAIN_BRANCH,
                number=git_llvm_rev.base_llvm_revision,
            )
        )
        self.assertEqual(sha, git_llvm_rev.base_llvm_sha)

    def test_sha_to_rev_prior_to_base_rev_works(self) -> None:
        sha = self.rev_to_sha_with_round_trip(
            git_llvm_rev.Rev(branch=git_llvm_rev.MAIN_BRANCH, number=375000)
        )
        self.assertEqual(sha, "2f6da767f13b8fd81f840c211d405fea32ac9db7")

    def test_sha_to_rev_after_base_rev_works(self) -> None:
        sha = self.rev_to_sha_with_round_trip(
            git_llvm_rev.Rev(branch=git_llvm_rev.MAIN_BRANCH, number=375506)
        )
        self.assertEqual(sha, "3bf7fddeb05655d9baed4cc69e13535c677ed1dd")

    def test_llvm_svn_parsing_runs_ignore_reverts(self) -> None:
        # This commit has a revert that mentions the reverted llvm-svn in the
        # commit message.

        # Commit which performed the revert
        sha = self.rev_to_sha_with_round_trip(
            git_llvm_rev.Rev(branch=git_llvm_rev.MAIN_BRANCH, number=374895)
        )
        self.assertEqual(sha, "1731fc88d1fa1fa55edd056db73a339b415dd5d6")

        # Commit that was reverted
        sha = self.rev_to_sha_with_round_trip(
            git_llvm_rev.Rev(branch=git_llvm_rev.MAIN_BRANCH, number=374841)
        )
        self.assertEqual(sha, "2a1386c81de504b5bda44fbecf3f7b4cdfd748fc")

    def test_imaginary_revs_raise(self) -> None:
        with self.assertRaises(ValueError) as r:
            git_llvm_rev.translate_rev_to_sha(
                get_llvm_config(),
                git_llvm_rev.Rev(
                    branch=git_llvm_rev.MAIN_BRANCH, number=9999999
                ),
            )

        self.assertIn("Try updating your tree?", str(r.exception))

    def test_merge_commits_count_as_one_commit_crbug1041079(self) -> None:
        # This CL merged _a lot_ of commits in. Verify a few hand-computed
        # properties about it.
        merge_sha_rev_number = 4496 + git_llvm_rev.base_llvm_revision
        sha = self.rev_to_sha_with_round_trip(
            git_llvm_rev.Rev(
                branch=git_llvm_rev.MAIN_BRANCH, number=merge_sha_rev_number
            )
        )
        self.assertEqual(sha, "0f0d0ed1c78f1a80139a1f2133fad5284691a121")

        sha = self.rev_to_sha_with_round_trip(
            git_llvm_rev.Rev(
                branch=git_llvm_rev.MAIN_BRANCH, number=merge_sha_rev_number - 1
            )
        )
        self.assertEqual(sha, "6f635f90929da9545dd696071a829a1a42f84b30")

        sha = self.rev_to_sha_with_round_trip(
            git_llvm_rev.Rev(
                branch=git_llvm_rev.MAIN_BRANCH, number=merge_sha_rev_number + 1
            )
        )
        self.assertEqual(sha, "199700a5cfeedf227619f966aa3125cef18bc958")

    def test_known_rev_sha_pairs_are_sorted(self):
        revs = [rev for rev, _ in git_llvm_rev.known_llvm_rev_sha_pairs]
        self.assertEqual(revs, sorted(revs))

    # NOTE: The below tests have _zz_ in their name as an optimization.
    # Iterating on a quick test is painful when these larger tests come before
    # it and take 7secs to run. Python's unittest module guarantees tests are
    # run in alphabetical order by their method name, so...
    #
    # If you're wondering, the slow part is `git branch -r --contains`. I
    # imagine it's going to be very cold code, so I'm not inclined to optimize
    # it much.

    def test_zz_non_base_rev_sha_pairs_are_correct(self):
        base_rev_sha_pairs = git_llvm_rev.known_llvm_rev_sha_pairs

        def restore_rev_sha_pairs():
            git_llvm_rev.known_llvm_rev_sha_pairs = base_rev_sha_pairs

        self.addCleanup(restore_rev_sha_pairs)
        git_llvm_rev.known_llvm_rev_sha_pairs = (
            (git_llvm_rev.base_llvm_revision, git_llvm_rev.base_llvm_sha),
        )

        for rev, cached_sha in base_rev_sha_pairs:
            got_sha = self.rev_to_sha_with_round_trip(
                git_llvm_rev.Rev(branch=git_llvm_rev.MAIN_BRANCH, number=rev)
            )
            self.assertEqual(cached_sha, got_sha)

    def test_zz_branch_revs_work_after_merge_points_and_svn_cutoff(
        self,
    ) -> None:
        # Arbitrary 9.x commit without an attached llvm-svn: value.
        sha = self.rev_to_sha_with_round_trip(
            git_llvm_rev.Rev(branch="upstream/release/9.x", number=366670)
        )
        self.assertEqual(sha, "4e858e4ac00b59f064da4e1f7e276916e7d296aa")

    def test_zz_branch_revs_work_at_merge_points(self) -> None:
        rev_number = 366426
        backing_sha = "c89a3d78f43d81b9cff7b9248772ddf14d21b749"

        sha = self.rev_to_sha_with_round_trip(
            git_llvm_rev.Rev(branch=git_llvm_rev.MAIN_BRANCH, number=rev_number)
        )
        self.assertEqual(sha, backing_sha)

        # Note that this won't round-trip: since this commit is on the main
        # branch, we'll pick main for this. That's fine.
        sha = git_llvm_rev.translate_rev_to_sha(
            get_llvm_config(),
            git_llvm_rev.Rev(branch="upstream/release/9.x", number=rev_number),
        )
        self.assertEqual(sha, backing_sha)

    def test_zz_branch_revs_work_after_merge_points(self) -> None:
        # Picking the commit on the 9.x branch after the merge-base for that +
        # main. Note that this is where llvm-svn numbers should diverge from
        # ours, and are therefore untrustworthy. The commit for this *does*
        # have a different `llvm-svn:` string than we should have.
        sha = self.rev_to_sha_with_round_trip(
            git_llvm_rev.Rev(branch="upstream/release/9.x", number=366427)
        )
        self.assertEqual(sha, "2cf681a11aea459b50d712abc7136f7129e4d57f")


# FIXME: When release/10.x happens, it may be nice to have a test-case
# generally covering that, since it's the first branch that we have to travel
# back to the base commit for.

if __name__ == "__main__":
    llvm_project.ensure_up_to_date()
    unittest.main()
