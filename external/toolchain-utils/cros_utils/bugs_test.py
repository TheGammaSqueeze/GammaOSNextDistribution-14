#!/usr/bin/env python3
# Copyright 2021 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# We're testing protected methods, so allow protected access.
# pylint: disable=protected-access

"""Tests bug filing bits."""

import datetime
import json
import os
from pathlib import Path
import tempfile
import unittest
import unittest.mock

from cros_utils import bugs


_ARBITRARY_DATETIME = datetime.datetime(2020, 1, 1, 23, 0, 0, 0)


class Tests(unittest.TestCase):
    """Tests for the bugs module."""

    def testWritingJSONFileSeemsToWork(self):
        """Tests JSON file writing."""
        old_x20_path = bugs.X20_PATH

        def restore_x20_path():
            bugs.X20_PATH = old_x20_path

        self.addCleanup(restore_x20_path)

        with tempfile.TemporaryDirectory() as tempdir:
            bugs.X20_PATH = tempdir
            file_path = bugs._WriteBugJSONFile(
                "ObjectType",
                {
                    "foo": "bar",
                    "baz": bugs.WellKnownComponents.CrOSToolchainPublic,
                },
                None,
            )

            self.assertTrue(
                file_path.startswith(tempdir),
                f"Expected {file_path} to start with {tempdir}",
            )

            with open(file_path, encoding="utf-8") as f:
                self.assertEqual(
                    json.load(f),
                    {
                        "type": "ObjectType",
                        "value": {
                            "foo": "bar",
                            "baz": int(
                                bugs.WellKnownComponents.CrOSToolchainPublic
                            ),
                        },
                    },
                )

    @unittest.mock.patch.object(bugs, "_WriteBugJSONFile")
    def testAppendingToBugsSeemsToWork(self, mock_write_json_file):
        """Tests AppendToExistingBug."""
        bugs.AppendToExistingBug(1234, "hello, world!")
        mock_write_json_file.assert_called_once_with(
            "AppendToExistingBugRequest",
            {
                "body": "hello, world!",
                "bug_id": 1234,
            },
            None,
        )

    @unittest.mock.patch.object(bugs, "_WriteBugJSONFile")
    def testBugCreationSeemsToWork(self, mock_write_json_file):
        """Tests CreateNewBug."""
        test_case_additions = (
            {},
            {
                "component_id": bugs.WellKnownComponents.CrOSToolchainPublic,
            },
            {
                "assignee": "foo@gbiv.com",
                "cc": ["bar@baz.com"],
            },
            {
                "parent_bug": 123,
            },
        )

        for additions in test_case_additions:
            test_case = {
                "component_id": 123,
                "title": "foo",
                "body": "bar",
                **additions,
            }

            bugs.CreateNewBug(**test_case)

            expected_output = {
                "component_id": test_case["component_id"],
                "subject": test_case["title"],
                "body": test_case["body"],
            }

            if assignee := test_case.get("assignee"):
                expected_output["assignee"] = assignee

            if cc := test_case.get("cc"):
                expected_output["cc"] = cc

            if parent_bug := test_case.get("parent_bug"):
                expected_output["parent_bug"] = parent_bug

            mock_write_json_file.assert_called_once_with(
                "FileNewBugRequest",
                expected_output,
                None,
            )
            mock_write_json_file.reset_mock()

    @unittest.mock.patch.object(bugs, "_WriteBugJSONFile")
    def testCronjobLogSendingSeemsToWork(self, mock_write_json_file):
        """Tests SendCronjobLog."""
        bugs.SendCronjobLog("my_name", False, "hello, world!")
        mock_write_json_file.assert_called_once_with(
            "CronjobUpdate",
            {
                "name": "my_name",
                "message": "hello, world!",
                "failed": False,
            },
            None,
        )

    @unittest.mock.patch.object(bugs, "_WriteBugJSONFile")
    def testCronjobLogSendingSeemsToWorkWithTurndown(
        self, mock_write_json_file
    ):
        """Tests SendCronjobLog."""
        bugs.SendCronjobLog(
            "my_name", False, "hello, world!", turndown_time_hours=42
        )
        mock_write_json_file.assert_called_once_with(
            "CronjobUpdate",
            {
                "name": "my_name",
                "message": "hello, world!",
                "failed": False,
                "cronjob_turndown_time_hours": 42,
            },
            None,
        )

    @unittest.mock.patch.object(bugs, "_WriteBugJSONFile")
    def testCronjobLogSendingSeemsToWorkWithParentBug(
        self, mock_write_json_file
    ):
        """Tests SendCronjobLog."""
        bugs.SendCronjobLog("my_name", False, "hello, world!", parent_bug=42)
        mock_write_json_file.assert_called_once_with(
            "CronjobUpdate",
            {
                "name": "my_name",
                "message": "hello, world!",
                "failed": False,
                "parent_bug": 42,
            },
            None,
        )

    def testFileNameGenerationProducesFileNamesInSortedOrder(self):
        """Tests that _FileNameGenerator gives us sorted file names."""
        gen = bugs._FileNameGenerator()
        first = gen.generate_json_file_name(_ARBITRARY_DATETIME)
        second = gen.generate_json_file_name(_ARBITRARY_DATETIME)
        self.assertLess(first, second)

    def testFileNameGenerationProtectsAgainstRipplingAdds(self):
        """Tests that _FileNameGenerator gives us sorted file names."""
        gen = bugs._FileNameGenerator()
        gen._entropy = 9
        first = gen.generate_json_file_name(_ARBITRARY_DATETIME)
        second = gen.generate_json_file_name(_ARBITRARY_DATETIME)
        self.assertLess(first, second)

        gen = bugs._FileNameGenerator()
        all_9s = "9" * (gen._ENTROPY_STR_SIZE - 1)
        gen._entropy = int(all_9s)
        third = gen.generate_json_file_name(_ARBITRARY_DATETIME)
        self.assertLess(second, third)

        fourth = gen.generate_json_file_name(_ARBITRARY_DATETIME)
        self.assertLess(third, fourth)

    @unittest.mock.patch.object(os, "getpid")
    def testForkingProducesADifferentReport(self, mock_getpid):
        """Tests that _FileNameGenerator gives us sorted file names."""
        gen = bugs._FileNameGenerator()

        mock_getpid.return_value = 1
        gen._entropy = 0
        parent_file = gen.generate_json_file_name(_ARBITRARY_DATETIME)

        mock_getpid.return_value = 2
        gen._entropy = 0
        child_file = gen.generate_json_file_name(_ARBITRARY_DATETIME)
        self.assertNotEqual(parent_file, child_file)

    @unittest.mock.patch.object(bugs, "_WriteBugJSONFile")
    def testCustomDirectoriesArePassedThrough(self, mock_write_json_file):
        directory = "/path/to/somewhere/interesting"
        bugs.AppendToExistingBug(1, "foo", directory=directory)
        mock_write_json_file.assert_called_once_with(
            unittest.mock.ANY, unittest.mock.ANY, directory
        )
        mock_write_json_file.reset_mock()

        bugs.CreateNewBug(1, "title", "body", directory=directory)
        mock_write_json_file.assert_called_once_with(
            unittest.mock.ANY, unittest.mock.ANY, directory
        )
        mock_write_json_file.reset_mock()

        bugs.SendCronjobLog("cronjob", False, "message", directory=directory)
        mock_write_json_file.assert_called_once_with(
            unittest.mock.ANY, unittest.mock.ANY, directory
        )

    def testWriteBugJSONFileWritesToGivenDirectory(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            bugs.AppendToExistingBug(1, "body", directory=tmpdir)
            json_files = list(Path(tmpdir).glob("*.json"))
            self.assertEqual(len(json_files), 1, json_files)


if __name__ == "__main__":
    unittest.main()
