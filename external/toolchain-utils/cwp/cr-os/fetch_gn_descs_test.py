#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# Copyright 2020 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Tests for fetch_gn_descs.py."""


import io
import unittest

import fetch_gn_descs

# pylint: disable=protected-access


class Test(unittest.TestCase):
    """Tests for fetch_gn_descs."""

    def test_fix_result_removes_uninteresting_items(self):
        items = {
            "//uninteresting:a": {},
            "//uninteresting:b": {
                "sources": ["whee"],
            },
            "//uninteresting:c": {
                "configs": ["whee"],
            },
            "//uninteresting:d": {
                "sources": [],
                "configs": [],
            },
            "//interesting:a": {
                "sources": ["a"],
                "configs": ["b"],
            },
            "//interesting:b": {
                "sources": ["d"],
                "configs": ["c"],
            },
        }

        expected_items = {
            "//interesting:a": items["//interesting:a"],
            "//interesting:b": items["//interesting:b"],
        }

        self.assertDictEqual(
            fetch_gn_descs._fix_result("/", "/", "/", items), expected_items
        )

    def test_fix_result_translates_paths_in_out_dir(self):
        items = {
            "//interesting:a": {
                "sources": ["//out_dir/foo", "//out_dir"],
                "configs": ["b"],
            },
        }

        expected_items = {
            "//interesting:a": {
                "sources": ["//out_translated/foo", "//out_translated/"],
                "configs": ["b"],
            },
        }

        self.assertDictEqual(
            fetch_gn_descs._fix_result(
                rename_out="//out_translated",
                out_dir="/chromium/src/out_dir",
                chromium_root="/chromium",
                gn_desc=items,
            ),
            expected_items,
        )

    def test_gn_desc_output_parsing_skips_pre_json_warnings(self):
        gn_desc = io.StringIO(
            "\n".join(
                (
                    "foo",
                    'warning: "{" is bad',
                    '{"bar": "baz",',
                    ' "qux": true}',
                )
            )
        )

        warnings, desc_json = fetch_gn_descs._parse_gn_desc_output(gn_desc)
        self.assertEqual(
            warnings,
            "\n".join(
                (
                    "foo",
                    'warning: "{" is bad',
                )
            ),
        )
        self.assertEqual(
            desc_json,
            {
                "bar": "baz",
                "qux": True,
            },
        )

    def test_gn_desc_output_parsing_issues_no_warnings_if_none_are_present(
        self,
    ):
        gn_desc = io.StringIO('{"bar": "baz"}')
        warnings, desc_json = fetch_gn_descs._parse_gn_desc_output(gn_desc)
        self.assertEqual(warnings, "")
        self.assertEqual(desc_json, {"bar": "baz"})

        gn_desc = io.StringIO('\n  \n\t\n{"bar": "baz"}')
        warnings, desc_json = fetch_gn_descs._parse_gn_desc_output(gn_desc)
        self.assertEqual(warnings, "")
        self.assertEqual(desc_json, {"bar": "baz"})


if __name__ == "__main__":
    unittest.main()
