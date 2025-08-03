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
import shutil
import tempfile
import unittest

import nsjail


class TestMountPt(unittest.TestCase):
    def test_requires_kwargs(self):
        self.assertRaises(AssertionError, nsjail.MountPt, 0)

    def test_args(self):
        """Test arguments."""
        # Test string values.  src and dst must be absolute paths.
        for name in "src", "dst":
            with self.subTest(arg=name):
                path = os.path.abspath(name)
                mnt = nsjail.MountPt(**{name: path})
                self.assertEqual(str(mnt),
                                 f'mount {{\n  {name}: "{path}"\n}}\n\n')

        # The rest of the string arguments do not care as much.
        for name in ("prefix_src_env", "src_content", "prefix_dst_env",
                     "fstype", "options"):
            with self.subTest(arg=name):
                mnt = nsjail.MountPt(**{name: name})
                self.assertEqual(str(mnt),
                                 f'mount {{\n  {name}: "{name}"\n}}\n\n')

        # Test the booleans with both True and False.
        for name in ("is_bind", "rw", "is_dir", "mandatory", "is_symlink",
                     "nosuid", "nodev", "noexec"):
            with self.subTest(arg=name, value=True):
                mnt = nsjail.MountPt(**{name: True})
                self.assertEqual(str(mnt), f'mount {{\n  {name}: true\n}}\n\n')
            with self.subTest(arg=name, value=False):
                mnt = nsjail.MountPt(**{name: False})
                self.assertEqual(str(mnt),
                                 f'mount {{\n  {name}: false\n}}\n\n')

    def test_absolute_paths(self):
        """Test that src and dst must be absolute paths."""
        with self.assertRaises(AssertionError):
            nsjail.MountPt(src="src")
        with self.assertRaises(AssertionError):
            nsjail.MountPt(dst="dst")


class TestNsjail(unittest.TestCase):
    def setUp(self):
        self.test_dir = tempfile.mkdtemp()
        self.cfg = nsjail.Nsjail(self.test_dir)
        self.cfg_name = os.path.join(self.test_dir, 'nsjail.cfg')

    def tearDown(self):
        shutil.rmtree(self.test_dir)

    @property
    def config(self):
        return self.cfg.generate_config(fn=None)

    def test_WriteConfig(self):
        self.cfg.generate_config(fn=self.cfg_name)
        self.assertTrue(os.path.exists(self.cfg_name))
        with open(self.cfg_name, encoding="iso-8859-1") as f:
            config = f.read()
        self.assertEqual(self.config, config)

    def testDefault(self):
        # Verify that a default entry is present.
        self.assertIn(
            '\nmount {\n  dst: "/dev/shm"\n  fstype: "tmpfs"\n'
            '  is_bind: false\n  rw: true\n}\n\n', self.config)
        # Verify that proc is mounted correctly.
        self.assertNotIn('\nmount_proc: true\n', self.config)
        self.assertIn('\nmount_proc: false\n', self.config)
        self.assertIn(
            ('\nmount {\n  src: "/proc"\n  dst: "/proc"\n  is_bind: true\n  '
             'rw: true\n  mandatory: true\n}\n'), self.config)

    def testCwd(self):
        self.cfg = nsjail.Nsjail(cwd="/cwd")
        self.assertIn('\ncwd: "/cwd"\n', self.config)

    def testMountPt(self):
        val = nsjail.MountPt(dst="/test")
        self.cfg.add_mountpt(dst="/test")
        self.assertEqual(val, self.cfg.mounts[-1])
        config = self.config
        # Verify that both a default entry and the one we added are present.
        self.assertIn(
            '\nmount {\n  dst: "/dev/shm"\n  fstype: "tmpfs"\n'
            '  is_bind: false\n  rw: true\n}\n\n', self.config)
        self.assertIn('\nmount {\n  dst: "/test"\n}\n\n', config)


if __name__ == "__main__":
    unittest.main()

# vim: sts=4:ts=4:sw=4
