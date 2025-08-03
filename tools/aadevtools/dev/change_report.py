#!/usr/bin/python3
#
# Copyright (C) 2021 The Android Open Source Project
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
"""Utilities for comparing two version of a codebase."""

import argparse
import difflib
import filecmp
import os
import pathlib
import re


class FileStat:
  """File statistics class for a file."""

  NON_TEXT = 0
  TEXT = 1

  def __init__(self, file_path):
    """Initializes with a file path string."""
    if file_path:
      self.file_name = str(file_path)
      self.size = file_path.stat().st_size
    else:
      self.file_name = ''
      self.size = 0

    self.line_cnt = 0
    self.group_cnt = 0
    self.add_line_cnt = 0
    self.remove_line_cnt = 0
    self.replace_line_cnt = 0

  @staticmethod
  def get_csv_header(prefix=None):
    """Returns CSV header string."""
    cols = ['file', 'size', 'line', 'group', 'add', 'remove', 'replace']
    if prefix:
      return ','.join('{0}_{1}'.format(prefix, c) for c in cols)
    else:
      return ','.join(c for c in cols)

  def get_csv_str(self, strip_dir_len=0):
    """Returns the file statistic CSV string."""
    name = self.file_name[strip_dir_len:]
    csv = [
        FileStat.no_comma(name), self.size, self.line_cnt, self.group_cnt,
        self.add_line_cnt, self.remove_line_cnt, self.replace_line_cnt
    ]
    return ','.join(str(i) for i in csv)

  @staticmethod
  def no_comma(astr):
    """Replaces , with _."""
    return astr.replace(',', '_')


class DiffStat:
  """Diff statistic class for 2 versions of a file."""

  SAME = 0
  NEW = 1
  REMOVED = 2
  MODIFIED = 3
  INCOMPARABLE = 4

  def __init__(self, common_name, old_file_stat, new_file_stat, state):
    """Initializes with the common names & etc."""
    self.old_file_stat = old_file_stat
    self.new_file_stat = new_file_stat
    self.name = common_name
    self.ext = os.path.splitext(self.name)[1].lstrip('.')
    self.state = state
    self.file_type = FileStat.NON_TEXT

  def add_diff_stat(self, diff_lines):
    """Adds the statistic by the diff lines."""
    # These align with https://github.com/python/cpython/blob/3.9/Lib/difflib.py
    old_pattern = re.compile(r'\*{3} (.*)')
    new_pattern = re.compile(r'-{3} (.*)')
    group_separator = '***************'
    old_group_header = re.compile(r'\*{3} (\d*),(\d*) \*{4}')
    new_group_header = re.compile(r'-{3} (\d*),(\d*) -{4}')

    # section 0 is old verion & 1 is new verion
    section = -1
    diff_stats = [self.old_file_stat, self.new_file_stat]
    in_group = False

    h1m = old_pattern.match(diff_lines[0])
    if not h1m:
      print('ERROR: wrong diff header line 1: %s' % diff_lines[0])
      return

    h2m = new_pattern.match(diff_lines[1])
    if not h2m:
      print('ERROR: wrong diff header line 2: %s' % diff_lines[1])
      return

    for line in diff_lines[2:]:
      if in_group:
        if line.startswith('  '):
          # equal
          continue
        elif line.startswith('! '):
          # replace
          diff_stats[section].replace_line_cnt += 1
          continue
        elif line.startswith('+ '):
          # add
          diff_stats[section].add_line_cnt += 1
          continue
        elif line.startswith('- '):
          # removed
          diff_stats[section].remove_line_cnt += 1
          continue

      oghm = old_group_header.match(line)
      if oghm:
        section = 0
        diff_stats[section].group_cnt += 1
        continue

      nghm = new_group_header.match(line)
      if nghm:
        section = 1
        diff_stats[section].group_cnt += 1
        continue

      if line.startswith(group_separator):
        in_group = True
        continue


class ChangeReport:
  """Change report class for the diff statistics on 2 versions of a codebase.

  Attributes:
    old_dir: The old codebase dir path string.
    new_dir: The new codebase dir path string.
    dircmp: The dircmp object
    group_cnt: How many diff groups.
    add_line_cnt: How many lines are added.
    remove_line_cnt: How many lines are removed.
    replace_line_cnt: Hoe many lines are changed.
  """

  def __init__(self, old_dir, new_dir, ignores=None, state_filter=None):
    """Initializes with old & new dir path strings."""
    self.old_dir = os.path.abspath(old_dir)
    self._old_dir_prefix_len = len(self.old_dir) + 1
    self.new_dir = os.path.abspath(new_dir)
    self._new_dir_prefix_len = len(self.new_dir) + 1
    if ignores:
      self._ignores = ignores.split(',')
      self._ignores.extend(filecmp.DEFAULT_IGNORES)
    else:
      self._ignores = filecmp.DEFAULT_IGNORES

    if state_filter:
      self._state_filter = list(map(int, state_filter.split(',')))
    else:
      self._state_filter = [0, 1, 2, 3, 4]

    self._do_same = DiffStat.SAME in self._state_filter
    self._do_new = DiffStat.NEW in self._state_filter
    self._do_removed = DiffStat.REMOVED in self._state_filter
    self._do_moeified = DiffStat.MODIFIED in self._state_filter
    self._do_incomparable = DiffStat.INCOMPARABLE in self._state_filter

    self.dircmp = filecmp.dircmp(
        self.old_dir, self.new_dir, ignore=self._ignores)
    self._diff_stats = []
    self._diff_stat_lines = []
    self._diff_lines = []
    self._processed_cnt = 0
    self._common_dir_len = ChangeReport.get_common_path_len(
        self.old_dir, self.new_dir)

  @staticmethod
  def get_common_path_len(dir1, dir2):
    """Gets the length of the common path of old & new folders."""
    sep = os.path.sep
    last_sep_pos = 0
    for i in range(len(dir1)):
      if dir1[i] == sep:
        last_sep_pos = i
      if dir1[i] != dir2[i]:
        break
    return last_sep_pos + 1

  @staticmethod
  def get_diff_stat_header():
    """Gets the diff statistic CSV header."""
    return 'file,ext,text,state,{0},{1}\n'.format(
        FileStat.get_csv_header('new'), FileStat.get_csv_header('old'))

  def get_diff_stat_lines(self):
    """Gets the diff statistic CSV lines."""
    if self._processed_cnt < 1:
      self._process_dircmp(self.dircmp)
      self._processed_cnt += 1

      self._diff_stat_lines = []
      for diff_stat in self._diff_stats:
        self._diff_stat_lines.append('{0},{1},{2},{3},{4},{5}\n'.format(
            FileStat.no_comma(diff_stat.name), diff_stat.ext,
            diff_stat.file_type, diff_stat.state,
            diff_stat.new_file_stat.get_csv_str(self._common_dir_len),
            diff_stat.old_file_stat.get_csv_str(self._common_dir_len)))

    return self._diff_stat_lines

  def get_diff_lines(self):
    """Gets the diff output lines."""
    if self._processed_cnt < 1:
      self._process_dircmp(self.dircmp)
      self._processed_cnt += 1
    return self._diff_lines

  def _process_dircmp(self, dircmp):
    """Compare all files in a dircmp object for diff statstics & output."""
    if self._do_moeified:
      self._process_diff_files(dircmp)

    for subdir_dircmp in dircmp.subdirs.values():
      rp = pathlib.Path(subdir_dircmp.right)
      lp = pathlib.Path(subdir_dircmp.left)
      if rp.is_symlink() or lp.is_symlink():
        print('SKIP: symlink: {0} or {1}'.format(subdir_dircmp.right,
                                                 subdir_dircmp.left))
        continue
      self._process_dircmp(subdir_dircmp)

    if self._do_new:
      self._process_others(dircmp.right_only, dircmp.right,
                           self._new_dir_prefix_len, DiffStat.NEW)
    if self._do_same:
      self._process_others(dircmp.same_files, dircmp.right,
                           self._new_dir_prefix_len, DiffStat.SAME)
    if self._do_incomparable:
      self._process_others(dircmp.funny_files, dircmp.right,
                           self._new_dir_prefix_len, DiffStat.INCOMPARABLE)
    if self._do_removed:
      self._process_others(dircmp.left_only, dircmp.left,
                           self._old_dir_prefix_len, DiffStat.REMOVED)

  def _process_others(self, files, adir, prefix_len, state):
    """Processes files are not modified."""
    empty_stat = FileStat(None)
    for file in files:
      file_path = pathlib.Path(adir, file)
      if file_path.is_symlink():
        print('SKIP: symlink: {0}, {1}'.format(state, file_path))
        continue
      elif file_path.is_dir():
        flist = self._get_filtered_files(file_path)
        self._process_others(flist, adir, prefix_len, state)
      else:
        file_stat = FileStat(file_path)
        common_name = str(file_path)[prefix_len:]
        if state == DiffStat.REMOVED:
          diff_stat = DiffStat(common_name, file_stat, empty_stat, state)
        else:
          diff_stat = DiffStat(common_name, empty_stat, file_stat, state)
        try:
          with open(file_path, encoding='utf-8') as f:
            lines = f.readlines()
          file_stat.line_cnt = len(lines)
          file_type = FileStat.TEXT
        except UnicodeDecodeError:
          file_type = FileStat.NON_TEXT

        diff_stat.file_type = file_type
        self._diff_stats.append(diff_stat)

  def _process_diff_files(self, dircmp):
    """Processes files are modified."""
    for file in dircmp.diff_files:
      old_file_path = pathlib.Path(dircmp.left, file)
      new_file_path = pathlib.Path(dircmp.right, file)
      self._diff_files(old_file_path, new_file_path)

  def _diff_files(self, old_file_path, new_file_path):
    """Diff old & new files."""
    old_file_stat = FileStat(old_file_path)
    new_file_stat = FileStat(new_file_path)
    common_name = str(new_file_path)[self._new_dir_prefix_len:]
    diff_stat = DiffStat(common_name, old_file_stat, new_file_stat,
                         DiffStat.MODIFIED)

    try:
      with open(old_file_path, encoding='utf-8') as f1:
        old_lines = f1.readlines()
      old_file_stat.line_cnt = len(old_lines)
      with open(new_file_path, encoding='utf-8') as f2:
        new_lines = f2.readlines()
      new_file_stat.line_cnt = len(new_lines)
      diff_lines = list(
          difflib.context_diff(old_lines, new_lines, old_file_path.name,
                               new_file_path.name))
      file_type = FileStat.TEXT
      if diff_lines:
        self._diff_lines.extend(diff_lines)
        diff_stat.add_diff_stat(diff_lines)
      else:
        print('WARNING: no diff lines on {0} {1}'.format(
            old_file_path, new_file_path))

    except UnicodeDecodeError:
      file_type = FileStat.NON_TEXT

    diff_stat.file_type = file_type
    self._diff_stats.append(diff_stat)

  def _get_filtered_files(self, dir_path):
    """Returns a filtered file list."""
    flist = []
    for f in dir_path.glob('*'):
      if f.name not in self._ignores:
        if f.is_symlink():
          print('SKIP: symlink: %s' % f)
          continue
        else:
          flist.append(f)
    return flist


def write_file(file, lines, header=None):
  """Write lines into a file."""

  with open(file, 'w') as f:
    if header:
      f.write(header)

    f.writelines(lines)
  print('OUTPUT: {0}, {1} lines'.format(file, len(lines)))


def main():
  parser = argparse.ArgumentParser(
      'Generate a diff stat cvs file for 2 versions of a codebase')
  parser.add_argument('--old_dir', help='the old version codebase dir')
  parser.add_argument('--new_dir', help='the new version codebase dir')
  parser.add_argument(
      '--csv_file', required=False, help='the diff stat cvs file if to create')
  parser.add_argument(
      '--diff_output_file',
      required=False,
      help='the diff output file if to create')
  parser.add_argument(
      '--ignores',
      required=False,
      default='.repo,.git,.github,.idea,__MACOSX,.prebuilt_info',
      help='names to ignore')
  parser.add_argument(
      '--state_filter',
      required=False,
      default='1,2,3',
      help='csv diff states to process, 0:SAME, 1:NEW, 2:REMOVED, 3:MODIFIED, '
      '4:INCOMPARABLE')

  args = parser.parse_args()

  if not os.path.isdir(args.old_dir):
    print('ERROR: %s does not exist.' % args.old_dir)
    exit()

  if not os.path.isdir(args.new_dir):
    print('ERROR: %s does not exist.' % args.new_dir)
    exit()

  change_report = ChangeReport(args.old_dir, args.new_dir, args.ignores,
                               args.state_filter)
  if args.csv_file:
    write_file(
        args.csv_file,
        change_report.get_diff_stat_lines(),
        header=ChangeReport.get_diff_stat_header())

  if args.diff_output_file:
    write_file(args.diff_output_file, change_report.get_diff_lines())


if __name__ == '__main__':
  main()
