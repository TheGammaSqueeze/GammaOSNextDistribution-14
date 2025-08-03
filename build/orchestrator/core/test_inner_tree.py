#!/usr/bin/env python3
#
# Copyright (C) 2008 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import os
import tempfile
import unittest

import inner_tree
import utils


class TestInnerTreeKey(unittest.TestCase):
    def test_eq(self):
        k1 = inner_tree.InnerTreeKey('inner', 'product')
        k2 = inner_tree.InnerTreeKey('inner', 'product')
        self.assertEqual(k1, k2)
        self.assertGreaterEqual(k1, k2)
        self.assertLessEqual(k1, k2)
        self.assertEqual(hash(k1), hash(k2))

        k1 = inner_tree.InnerTreeKey(['inner', 'o1'], 'product')
        k2 = inner_tree.InnerTreeKey(['inner', 'o1'], 'product')
        self.assertEqual(k1, k2)
        self.assertGreaterEqual(k1, k2)
        self.assertLessEqual(k1, k2)
        self.assertEqual(hash(k1), hash(k2))

    def test_neq(self):
        k1 = inner_tree.InnerTreeKey('inner1', 'product')
        k2 = inner_tree.InnerTreeKey('inner2', 'product')
        self.assertNotEqual(k1, k2)
        self.assertNotEqual(hash(k1), hash(k2))
        self.assertLess(k1, k2)
        self.assertGreater(k2, k1)

        k1 = inner_tree.InnerTreeKey(['inner1', 'o1'], 'product')
        k2 = inner_tree.InnerTreeKey(['inner1', 'o2'], 'product')
        self.assertNotEqual(k1, k2)
        self.assertNotEqual(hash(k1), hash(k2))
        self.assertLess(k1, k2)
        self.assertGreater(k2, k1)

        k1 = inner_tree.InnerTreeKey(['inner1', 'o1'], 'product1')
        k2 = inner_tree.InnerTreeKey(['inner1', 'o2'], 'product2')
        self.assertNotEqual(k1, k2)
        self.assertNotEqual(hash(k1), hash(k2))
        self.assertLess(k1, k2)
        self.assertGreater(k2, k1)


class TestInnerTree(unittest.TestCase):
    def setUp(self):
        self.test_dir = tempfile.mkdtemp()
        self.oldcwd = os.getcwd()
        os.chdir(self.test_dir)
        self.context = utils.ContextTest(self.test_dir, self.id())

    def tearDown(self):
        os.chdir(self.oldcwd)

    def test_values(self):
        tree = inner_tree.InnerTree(self.context, ["inner", "meld1", "meld2"], "product", "user")
        self.assertEqual(tree.root, "inner")
        self.assertEqual(tree.meld_dirs, ["meld1", "meld2"])
        self.assertEqual(tree.product, "product")

    def test_bad_meld(self):
        with self.assertRaises(Exception):
            inner_tree.InnerTree(self.context, ["inner", "/meld1"], "product", "user")

    def test_meld_config(self):
        # Create git projects in inner, meld1, meld2
        os.makedirs(os.path.join(self.test_dir, 'inner'))
        dirs = (('inner', 'p1'), ('meld1', 'p1'), ('meld1', 'p2'), ('meld2', 'p2'), ('meld2', 'p3'))
        for d in dirs:
            os.makedirs(os.path.join(self.test_dir, *d, '.git'))
        tree = inner_tree.InnerTree(self.context, ["inner", "meld1", "meld2"], "product", "user")
        meld_config = tree.meld_config
        srcs = {x.src:x for x in meld_config.mounts}

        # inner/p1 is a git project, so it wins.
        self.assertNotIn(os.path.join(self.test_dir, 'meld1', 'p1'), srcs)

        p2 = os.path.join(self.test_dir, 'meld1', 'p2')
        self.assertIn(p2, srcs)
        self.assertEqual(os.path.abspath(os.path.join('inner', 'p2')), srcs[p2].dst)
        # meld1/p2 was melded first, so it wins.
        self.assertNotIn(os.path.join(self.test_dir, 'meld2', 'p2'), srcs)

        p3 = os.path.join(self.test_dir, 'meld2', 'p3')
        self.assertIn(p3, srcs)
        self.assertEqual(os.path.abspath(os.path.join('inner', 'p3')), srcs[p3].dst)


if __name__ == "__main__":
    unittest.main()

# vim: sts=4:ts=4:sw=4
