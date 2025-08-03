#!/usr/bin/python3
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Unit tests for Comparators."""

import unittest

import common

from autotest_lib.client.common_lib.test_utils import comparators


class TestComparators(unittest.TestCase):
    """Unittests for Seven comparator helpers."""

    def testIsA(self):
        class MockedClass(object):
            pass

        class FooClass(object):
            pass

        foo = comparators.IsA(MockedClass)
        self.assertTrue(foo == MockedClass)
        self.assertFalse(foo == FooClass)

    def testAnyStringWith(self):
        """Test AnyStringWith evaluates properly."""
        f = comparators.Substring("foobar")
        self.assertTrue(f == "foobarfizz")
        self.assertTrue("foobarfizz" == f)
        self.assertTrue(f == "fizzfoobar")
        self.assertTrue(f == "foobar")
        self.assertFalse(f == "fizzfoobuzzbar")
        self.assertFalse(f == "foo")

    def testAndComparitor(self):
        """Test AndComparator evaluates properly."""
        foo = comparators.Substrings(["host", "name", "cros"])
        self.assertTrue(foo == "a.host.name.cros")
        self.assertFalse(foo == "a.host.cros")


if __name__ == "__main__":
    unittest.TestCase()
