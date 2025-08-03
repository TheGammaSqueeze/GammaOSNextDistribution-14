#! /usr/bin/env python3
#
# Copyright 2023, The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import argparse
from collections import defaultdict
from enum import Enum
import os
import re


class SortType(Enum):
  NONE = 'none'
  SIMPLE = 'simple'
  OPT_NEIGHBOURS = 'opt_neighbours'


def merge_same_procnames(entries):
  path_regex = r'(.+)_(\d+).txt'
  prog = re.compile(path_regex)

  merged_entries = defaultdict(set)
  for path, objs in entries:
    basename = os.path.basename(path)
    m = prog.match(basename)
    if m:
      merged_entries[m.group(1)].update(objs)

  return sorted(merged_entries.items(), key=lambda x: len(x[1]))


def opt_neighbours(sort_keys):
  sort_keys = dict(sort_keys)
  res = list()

  # Start with a bin with the lowest process and objects count.
  cur_key = min(
      sort_keys.items(), key=lambda item: (item[0].bit_count(), len(item[1]))
  )[0]
  res.append((cur_key, sort_keys[cur_key]))
  del sort_keys[cur_key]

  # Find next most similar sort key and update the result.
  while sort_keys:

    def jaccard_index(x):
      return (x & cur_key).bit_count() / (x | cur_key).bit_count()

    next_key = max(sort_keys.keys(), key=jaccard_index)
    res.append((next_key, sort_keys[next_key]))
    del sort_keys[next_key]
    cur_key = next_key
  return res


def process_dirty_entries(entries, sort_type):
  dirty_image_objects = []

  union = set()
  for k, v in entries:
    union = union.union(v)

  if sort_type == SortType.NONE:
    dirty_obj_lines = [obj + '\n' for obj in sorted(union)]
    return (dirty_obj_lines, dict())

  # sort_key -> [objs]
  sort_keys = defaultdict(list)
  for obj in union:
    sort_key = 0
    # Nth bit of sort_key is set if this object is dirty in Nth process.
    for idx, (k, v) in enumerate(entries):
      if obj in v:
        sort_key = (sort_key << 1) | 1
      else:
        sort_key = sort_key << 1

    sort_keys[sort_key].append(obj)

  sort_keys = sorted(sort_keys.items())

  if sort_type == SortType.OPT_NEIGHBOURS:
    sort_keys = opt_neighbours(sort_keys)

  dirty_obj_lines = list()
  for idx, (_, objs) in enumerate(sort_keys):
    for obj in objs:
      dirty_obj_lines.append(obj + ' ' + str(idx) + '\n')

  return (dirty_obj_lines, sort_keys)


def main():
  parser = argparse.ArgumentParser(
      description=(
          'Create dirty-image-objects file from specified imgdiag output files.'
      ),
      formatter_class=argparse.ArgumentDefaultsHelpFormatter,
  )
  parser.add_argument(
      'imgdiag_files',
      nargs='+',
      help='imgdiag files to use.',
  )
  parser.add_argument(
      '--sort-type',
      choices=[e.value for e in SortType],
      default=SortType.OPT_NEIGHBOURS.value,
      help=(
          'Object sorting type. "simple" puts objects with the same usage'
          ' pattern in the same bins. "opt_neighbours" also tries to put bins'
          ' with similar usage patterns close to each other.'
      ),
  )
  parser.add_argument(
      '--merge-same-procnames',
      action=argparse.BooleanOptionalAction,
      default=False,
      help=(
          'Merge dirty objects from files with the same process name (different'
          ' pid). Files are expected to end with "_{pid}.txt"'
      ),
  )
  parser.add_argument(
      '--output-filename',
      default='dirty-image-objects.txt',
      help='Output file for dirty image objects.',
  )
  parser.add_argument(
      '--print-stats',
      action=argparse.BooleanOptionalAction,
      default=False,
      help='Print dirty object stats.',
  )

  args = parser.parse_args()

  entries = list()
  for path in args.imgdiag_files:
    with open(path) as f:
      lines = f.readlines()
    prefix = 'dirty_obj: '
    lines = [l.strip().removeprefix(prefix) for l in lines if prefix in l]
    entries.append((path, set(lines)))

  entries = sorted(entries, key=lambda x: len(x[1]))

  if args.merge_same_procnames:
    entries = merge_same_procnames(entries)

  print('Using processes:')
  for k, v in entries:
    print(f'{k}: {len(v)}')
  print()

  dirty_image_objects, sort_keys = process_dirty_entries(
      entries=entries, sort_type=SortType(args.sort_type)
  )

  with open(args.output_filename, 'w') as f:
    f.writelines(dirty_image_objects)

  if args.print_stats:
    print(','.join(k for k, v in entries), ',obj_count')
    total_count = 0
    for sort_key, objs in sort_keys:
      bits_csv = ','.join(
          '{sort_key:0{width}b}'.format(sort_key=sort_key, width=len(entries))
      )
      print(bits_csv, ',', len(objs))
      total_count += len(objs)
    print('total: ', total_count)


if __name__ == '__main__':
  main()
