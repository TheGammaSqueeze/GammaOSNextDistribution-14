#!/usr/bin/env python3
#
# See: go/camera-sideline#versions for more context
#
# This script generates the apex manifest version number (which is also used for
# the outer aab/jar object's version available to PackageManager).
#
# That version is limited to INT_MAX
# Strategy:
#   if(local eng build)
#      version = 2147480000
#   else
#      version = brach specific code + padding + numeric part of build number
#
# 2147480000 is chosen as being a value that can install over any expected build
# server build number that is still a little smaller than INT_MAX to leave room
# for maneuvering

import os
import re
import sys

BRANCH_SPECIFIC_VERSION_IDENTIFIER = 15  # main
DEFAULT_ENG_BUILD_NUMBER = 2147480000
DEFAULT_BAD_BUILD_NUMBER = DEFAULT_ENG_BUILD_NUMBER - 1


def tame_box(numeric_build_number):
  # Box the regex extracted value to a min and a max, just in case. Should not
  # be needed ever.
  if numeric_build_number < 1:
    numeric_build_number = 1
  if numeric_build_number > DEFAULT_ENG_BUILD_NUMBER:
    numeric_build_number = DEFAULT_BAD_BUILD_NUMBER
  return numeric_build_number


def get_version(input_path, output_path):

  soong_build_number = DEFAULT_BAD_BUILD_NUMBER
  numeric_build_number = DEFAULT_BAD_BUILD_NUMBER

  # Extract the Android Build ID from the build products
  out_dir = os.getenv('OUT_DIR')
  with open('%s/soong/build_number.txt' % out_dir, 'r') as file:
    soong_build_number = file.read().replace('\n', '')

  # Eng builds all have a default very high version number to permit for local
  # builds to ovewrite whatever else is installed on the OS.
  if soong_build_number.startswith('eng.'):  # eng.bills.20210901.235403
    numeric_build_number = DEFAULT_ENG_BUILD_NUMBER
  else:
    # We only want the numeric part of the Android Build ID
    match = re.search(r'([0-9]+)', soong_build_number)
    if match:
      numeric_build_number = int(match.group(0))
    else:
      numeric_build_number = DEFAULT_BAD_BUILD_NUMBER

  # Tame the inputs into a reasonable box, just in case
  numeric_build_number = tame_box(numeric_build_number)
  # print('numeric_build_number: %s' % str(numeric_build_number))

  # With the numeric build number as a starting point, let's augment it with
  # the BRANCH_SPECIFIC_VERSION_IDENTIFIER to differentiate build products from
  # this branch according to: go/camera-sideline#versions
  branched_build_number = numeric_build_number
  if numeric_build_number == DEFAULT_ENG_BUILD_NUMBER:
    # High default eng numbers can't really be multiplied so we add our branch
    # specific number instead
    branched_build_number = numeric_build_number + BRANCH_SPECIFIC_VERSION_IDENTIFIER
  else:
    # Two cases to consider:
    #   1. A "regular" Android Build ID like: 7699287
    #   2. A pending Android Build ID like: P25748464

    # "It's just string concatenation"
    string_build_number = '%s%s' % (BRANCH_SPECIFIC_VERSION_IDENTIFIER,
                                    str(numeric_build_number).zfill(8))
    # Ints in python3 are long
    branched_build_number = int(string_build_number)
    # Tame the result into a reasonable box, just in case
    branched_build_number = tame_box(branched_build_number)

  # print('soong_build_number: %s' % soong_build_number)
  # print('branched_build_number: %s' % str(branched_build_number))

  # Bash version:
  # cat $1 | \
  # sed -E "s/\{BUILD_NUMBER\}/$numeric_build_number/g" | \
  # sed -E "s/\{BUILD_ID\}/$soong_build_number/g" > $2
  try:
    if os.path.exists(input_path):
      input_fh = open(input_path, 'r')
      contents = input_fh.readlines()
      # print('Read: %s' % input_path)
      new_contents = []
      input_fh.close()
      for line in contents:
        line = line.replace('{BUILD_ID}', soong_build_number)
        line = line.replace('{BUILD_NUMBER}', str(branched_build_number))
        new_contents.append(line)
      # print(new_contents)
      output_fh = open(output_path, 'w')
      output_fh.write(''.join(new_contents))
      output_fh.close()
      # print('Wrote: %s' % output_path)
  except IOError:
    print(f'error occurred trying to read the file {input_path}')
    return 1

  return 0


def main():
  return get_version(*sys.argv[1:])


if __name__ == '__main__':
  sys.exit(main())

