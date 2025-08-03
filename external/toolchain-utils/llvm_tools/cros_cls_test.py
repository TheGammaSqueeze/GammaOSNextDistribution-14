#!/usr/bin/env python3
# Copyright 2024 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Tests for cros_cls."""

import unittest

import cros_cls


class TestChangeListURL(unittest.TestCase):
    """ChangeListURL tests."""

    def test_parsing_long_form_url(self):
        self.assertEqual(
            cros_cls.ChangeListURL.parse(
                "chromium-review.googlesource.com/c/chromiumos/overlays/"
                "chromiumos-overlay/+/123456",
            ),
            cros_cls.ChangeListURL(cl_id=123456, patch_set=None),
        )

    def test_parsing_discards_http(self):
        self.assertEqual(
            cros_cls.ChangeListURL.parse("http://crrev.com/c/123456"),
            cros_cls.ChangeListURL(cl_id=123456, patch_set=None),
        )

    def test_parsing_discards_https(self):
        self.assertEqual(
            cros_cls.ChangeListURL.parse("https://crrev.com/c/123456"),
            cros_cls.ChangeListURL(cl_id=123456, patch_set=None),
        )

    def test_parsing_detects_patch_sets(self):
        self.assertEqual(
            cros_cls.ChangeListURL.parse("crrev.com/c/123456/14"),
            cros_cls.ChangeListURL(cl_id=123456, patch_set=14),
        )

    def test_parsing_is_okay_with_trailing_slash(self):
        self.assertEqual(
            cros_cls.ChangeListURL.parse("crrev.com/c/123456/"),
            cros_cls.ChangeListURL(cl_id=123456, patch_set=None),
        )
        self.assertEqual(
            cros_cls.ChangeListURL.parse("crrev.com/c/123456/14/"),
            cros_cls.ChangeListURL(cl_id=123456, patch_set=14),
        )

    def test_parsing_is_okay_with_valid_trailing_junk(self):
        self.assertEqual(
            cros_cls.ChangeListURL.parse("crrev.com/c/123456?foo=bar"),
            cros_cls.ChangeListURL(cl_id=123456, patch_set=None),
        )
        self.assertEqual(
            cros_cls.ChangeListURL.parse("crrev.com/c/123456/?foo=bar"),
            cros_cls.ChangeListURL(cl_id=123456, patch_set=None),
        )
        self.assertEqual(
            cros_cls.ChangeListURL.parse("crrev.com/c/123456/14/foo=bar"),
            cros_cls.ChangeListURL(cl_id=123456, patch_set=14),
        )
        self.assertEqual(
            cros_cls.ChangeListURL.parse("crrev.com/c/123456/14?foo=bar"),
            cros_cls.ChangeListURL(cl_id=123456, patch_set=14),
        )

        # While these aren't well-formed, Gerrit handles them without issue.
        self.assertEqual(
            cros_cls.ChangeListURL.parse("crrev.com/c/123456&foo=bar"),
            cros_cls.ChangeListURL(cl_id=123456, patch_set=None),
        )
        self.assertEqual(
            cros_cls.ChangeListURL.parse("crrev.com/c/123456/14&foo=bar"),
            cros_cls.ChangeListURL(cl_id=123456, patch_set=14),
        )

    def test_parsing_raises_on_invalid_trailing_jumk(self):
        with self.assertRaises(ValueError):
            cros_cls.ChangeListURL.parse("crrev.com/c/123456foo=bar")

        with self.assertRaises(ValueError):
            cros_cls.ChangeListURL.parse("crrev.com/c/123456/14foo=bar")

    def test_str_functions_properly(self):
        self.assertEqual(
            str(
                cros_cls.ChangeListURL(
                    cl_id=1234,
                    patch_set=2,
                )
            ),
            "https://crrev.com/c/1234/2",
        )

        self.assertEqual(
            str(
                cros_cls.ChangeListURL(
                    cl_id=1234,
                    patch_set=None,
                )
            ),
            "https://crrev.com/c/1234",
        )


class Test(unittest.TestCase):
    """General tests for cros_cls."""

    def test_release_builder_parsing_works(self):
        self.assertEqual(
            cros_cls.parse_release_from_builder_artifacts_link(
                "gs://chromeos-image-archive/amd64-generic-asan-cq/"
                "R122-15711.0.0-59730-8761718482083052481"
            ),
            "R122-15711.0.0",
        )
        self.assertEqual(
            cros_cls.parse_release_from_builder_artifacts_link(
                "gs://chromeos-image-archive/amd64-generic-asan-cq/"
                "R122-15711.0.0-59730-8761718482083052481/some/trailing/"
                "stuff.zip"
            ),
            "R122-15711.0.0",
        )


if __name__ == "__main__":
    unittest.main()
