# Lint as: python3
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import glob
import os

# Relative to .star root (in autotest/files).
OUTPUT_FILE = "metadata/tests.star"

HEADER = """# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

TESTS = []
def _append_tests(tests):
    for t in tests:
        TESTS.append(t)
"""

TEMPLATE = """
load("//{path}", {unique_name} = "TESTS")
_append_tests({unique_name})
"""

def main():
  """Main entry point of this script."""
  root_dir = os.path.realpath(
      os.path.join(os.path.realpath(__file__), "../../.."))
  os.chdir(root_dir)
  output = HEADER
  for test_type in ["client", "server"]:
    for path in glob.iglob(test_type + "/site_tests/*/*.star"):
      head, file_name = os.path.split(path)
      head, test_folder = os.path.split(head)
      unique_name = "_".join(
          [test_type, test_folder, file_name[:-len(".star")]])
      output += TEMPLATE.format(path=path, unique_name=unique_name)

  with open(OUTPUT_FILE, 'w') as fh:
    fh.write(output)
  print("Wrote output to", OUTPUT_FILE)


if __name__ == '__main__':
  main()
