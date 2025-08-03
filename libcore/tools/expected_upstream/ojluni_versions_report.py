#!/usr/bin/python3 -B

# Copyright 2021 The Android Open Source Project
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

"""Command line tool to report the statistics of EXPECTED_UPSTREAM file."""

import collections

from common_util import ExpectedUpstreamFile
from common_util import OJLUNI_JAVA_BASE_PATH


def get_major_version(git_ref: str) -> str:
  index = git_ref.find('/')
  if index == -1:
    return git_ref
  return git_ref[:index]


def main() -> None:
  expected_upstream_file = ExpectedUpstreamFile()
  expected_entries = expected_upstream_file.read_all_entries()

  non_test_filter = lambda e: e.dst_path.startswith(OJLUNI_JAVA_BASE_PATH)
  non_test_entries = list(filter(non_test_filter, expected_entries))
  non_test_refs = list(map(lambda e: e.git_ref, non_test_entries))
  total = len(non_test_refs)

  minor_groups = dict(collections.Counter(non_test_refs))

  top_tree = {}
  for git_ref, count in minor_groups.items():
    major_version = get_major_version(git_ref)
    if major_version not in top_tree:
      top_tree[major_version] = {}
    top_tree[major_version][git_ref] = count

  top_tree = {k: top_tree[k] for k in sorted(top_tree)}

  print('=== Ojluni Version Report ===')
  for major_version, counts in top_tree.items():
    subtotal = sum(counts[key] for key in counts)
    percentages = '{:.2%}'.format(subtotal / total)
    print(f'{major_version}:\t{subtotal}\t{percentages}')
    for minor_version, count in counts.items():
      sub_percentages = '{:.2%}'.format(count / subtotal)
      print(f'  {minor_version}:\t{count}\t{sub_percentages}')


if __name__ == '__main__':
  main()
