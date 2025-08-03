#!/usr/bin/python
#
# Copyright (C) 2024 The Android Open Source Project
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
#

import argparse
import re
import subprocess
import sys


def check_disallowed_words(commit):
  try:
    output = subprocess.check_output(["git", "show", f"{commit}"], shell=False).decode("utf-8")
  except subprocess.CalledProcessError as e:
    print(f"Error running: {e}")
    return 1

  disallowed_words = [
      'Google Inc',
      'NDK translation',
      'NdkTranslation',
      'ndk_translation',
      'typedef ',
  ]

  for line in output.splitlines():
    if (line.startswith('-')):
      continue
    for word in disallowed_words:
      if re.search(word, line, re.IGNORECASE):
        print(f"Found disallowed word '{word}' in line '{line}'")
        return 1

  return 0


def main():
  parser = argparse.ArgumentParser(description="check_disallowed_words")
  parser.add_argument("--commit", help="check this commit")

  return check_disallowed_words(parser.parse_args().commit)

if __name__ == "__main__":
  sys.exit(main())
