# Copyright (C) 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Unit tests for difftool.py."""

import os
import pathlib
import unittest
import clangcompile
import difftool


def get_path(name):
  return os.path.join(os.getenv("TEST_TMPDIR"), name)


def create_file(name, content):
  path = get_path(name)
  with open(path, "w") as f:
    f.write(content)
  return pathlib.Path(path)


def _substring_in_list(s, slist):
  for elem in slist:
    if s in elem:
      return True
  return False


class DifftoolTest(unittest.TestCase):

  def assertNotInErrors(self, expected, errorlist):
    if _substring_in_list(expected, errorlist):
      self.fail("{!r} found in errors: {!r}".format(expected, errorlist))

  def assertInErrors(self, expected, errorlist):
    if not _substring_in_list(expected, errorlist):
      self.fail("{!r} not found in errors: {!r}".format(expected, errorlist))

  def test_file_differences_not_exist(self):
    obj_file = create_file("foo.o", "object contents")

    diffs = difftool.file_differences(pathlib.Path("doesntexist.o"),
                                      obj_file)
    self.assertEqual(["doesntexist.o does not exist"], diffs)

  @unittest.skip("TODO(usta)")
  def test_file_differences_different_types(self):
    obj_file = create_file("foo.o", "object contents")
    obj_file_two = create_file("foo2.o", "object contents two")
    txt_file = create_file("foo3.txt", "other")
    so_file = create_file("bar.so", "shared lib contents")

    diffs = difftool.file_differences(obj_file, so_file)
    self.assertInErrors("file types differ", diffs)

    diffs = difftool.file_differences(obj_file, txt_file)
    self.assertInErrors("file types differ", diffs)

    diffs = difftool.file_differences(so_file, obj_file)
    self.assertInErrors("file types differ", diffs)

    diffs = difftool.file_differences(obj_file, obj_file_two)
    self.assertNotInErrors("file types differ", diffs)

  @unittest.skip("TODO(usta)")
  def test_object_contents_differ(self):
    obj_file = create_file("foo.o", "object contents\none\n")
    obj_file_two = create_file("foo2.o", "object contents\ntwo\n")

    diffs = difftool.file_differences(obj_file, obj_file_two)
    self.assertNotInErrors("object_contents", diffs)
    self.assertInErrors("one", diffs)
    self.assertInErrors("two", diffs)

  def test_soong_clang_compile_info(self):
    fake_cmd = ("PWD=/proc/self/cwd prebuilts/clang -c -Wall -Wno-unused " +
                "foo.cpp -Iframeworks/av/include -Dsomedefine " +
                "-misc_flag misc_arg " +
                "-o foo.o # comment")
    info = difftool.rich_command_info(fake_cmd)
    self.assertIsInstance(info, clangcompile.ClangCompileInfo)
    self.assertEqual([("I", "frameworks/av/include")], info.i_includes)
    self.assertEqual(["-Dsomedefine"], info.defines)
    self.assertEqual(["-Wall", "-Wno-unused"], info.warnings)
    self.assertEqual(["-c", ("misc_flag", "misc_arg")], info.misc_flags)
    self.assertEqual(["foo.cpp", ("o", "foo.o")], info.file_flags)

  def test_bazel_clang_compile_info(self):
    fake_cmd = ("cd out/bazel/execroot && rm -f foo.o &&  " +
                "prebuilts/clang -MD -MF bazel-out/foo.d " +
                "-iquote . -iquote bazel-out/foo/bin " +
                "-I frameworks/av/include " +
                "-I bazel-out/frameworks/av/include/bin " +
                " -Dsomedefine " +
                "-misc_flag misc_arg " +
                "-Werror=int-conversion " +
                "-Wno-reserved-id-macro "
                "-o foo.o # comment")
    info = difftool.rich_command_info(fake_cmd)
    self.assertIsInstance(info, clangcompile.ClangCompileInfo)
    self.assertEqual([("iquote", ".")], info.iquote_includes)
    self.assertEqual([("I", "frameworks/av/include")], info.i_includes)
    self.assertEqual(["-Dsomedefine"], info.defines)
    self.assertEqual(["-Werror=int-conversion", "-Wno-reserved-id-macro"],
                     info.warnings)
    self.assertEqual(["-MD", ("misc_flag", "misc_arg")], info.misc_flags)
    self.assertEqual([("MF", "bazel-out/foo.d"), ("o", "foo.o")], info.file_flags)


if __name__ == "__main__":
  unittest.main()
