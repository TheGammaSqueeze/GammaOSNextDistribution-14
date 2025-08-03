#!/usr/bin/env python3
# Copyright 2022 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Tests for check_clang_diags."""

import textwrap
import unittest
from unittest import mock

import check_clang_diags
from cros_utils import bugs


# pylint: disable=protected-access


class Test(unittest.TestCase):
    """Test class."""

    def test_process_new_diagnostics_ignores_new_tools(self):
        new_state, new_diags = check_clang_diags._process_new_diagnostics(
            old={},
            new={"clang": ["-Wone", "-Wtwo"]},
        )
        self.assertEqual(new_state, {"clang": ["-Wone", "-Wtwo"]})
        self.assertEqual(new_diags, {})

    def test_process_new_diagnostics_is_a_nop_when_no_changes(self):
        new_state, new_diags = check_clang_diags._process_new_diagnostics(
            old={"clang": ["-Wone", "-Wtwo"]},
            new={"clang": ["-Wone", "-Wtwo"]},
        )
        self.assertEqual(new_state, {"clang": ["-Wone", "-Wtwo"]})
        self.assertEqual(new_diags, {})

    def test_process_new_diagnostics_ignores_removals_and_readds(self):
        new_state, new_diags = check_clang_diags._process_new_diagnostics(
            old={"clang": ["-Wone", "-Wtwo"]},
            new={"clang": ["-Wone"]},
        )
        self.assertEqual(new_diags, {})
        new_state, new_diags = check_clang_diags._process_new_diagnostics(
            old=new_state,
            new={"clang": ["-Wone", "-Wtwo"]},
        )
        self.assertEqual(new_state, {"clang": ["-Wone", "-Wtwo"]})
        self.assertEqual(new_diags, {})

    def test_process_new_diagnostics_complains_when_warnings_are_added(self):
        new_state, new_diags = check_clang_diags._process_new_diagnostics(
            old={"clang": ["-Wone"]},
            new={"clang": ["-Wone", "-Wtwo"]},
        )
        self.assertEqual(new_state, {"clang": ["-Wone", "-Wtwo"]})
        self.assertEqual(new_diags, {"clang": ["-Wtwo"]})

    @mock.patch.object(bugs, "CreateNewBug")
    def test_bugs_are_created_as_expected(self, create_new_bug_mock):
        check_clang_diags._file_bugs_for_new_diags(
            {
                "clang": ["-Wone"],
                "clang-tidy": ["bugprone-foo"],
            }
        )

        expected_calls = [
            mock.call(
                component_id=bugs.WellKnownComponents.CrOSToolchainPublic,
                title="Investigate clang check `-Wone`",
                body=textwrap.dedent(
                    """\
                    It seems that the `-Wone` check was recently added
                    to clang. It's probably good to TAL at whether this
                    check would be good for us to enable in e.g., platform2, or
                    across ChromeOS.
                    """
                ),
                assignee=check_clang_diags._DEFAULT_ASSIGNEE,
                cc=check_clang_diags._DEFAULT_CCS,
            ),
            mock.call(
                component_id=bugs.WellKnownComponents.CrOSToolchainPublic,
                title="Investigate clang-tidy check `bugprone-foo`",
                body=textwrap.dedent(
                    """\
                    It seems that the `bugprone-foo` check was recently added
                    to clang-tidy. It's probably good to TAL at whether this
                    check would be good for us to enable in e.g., platform2, or
                    across ChromeOS.
                    """
                ),
                assignee=check_clang_diags._DEFAULT_ASSIGNEE,
                cc=check_clang_diags._DEFAULT_CCS,
            ),
        ]

        # Don't assertEqual the lists, since the diff is really hard to read for
        # that.
        for actual, expected in zip(
            create_new_bug_mock.call_args_list, expected_calls
        ):
            self.assertEqual(actual, expected)

        self.assertEqual(
            len(create_new_bug_mock.call_args_list), len(expected_calls)
        )


if __name__ == "__main__":
    unittest.main()
