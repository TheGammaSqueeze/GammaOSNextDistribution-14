#
#   Copyright 2020 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

"""Common functions shared between the scripts."""

import os


def android_repository_root():
  """Returns the root of the tree."""
  if 'ANDROID_BUILD_TOP' not in os.environ:
    raise Exception('Environment variable ANDROID_BUILD_TOP not set')
  return os.environ['ANDROID_BUILD_TOP']


def printable_path(filename):
  """Returns the filename relative to the current dir, if it's under it."""
  relative_path = os.path.relpath(filename)
  if not relative_path.startswith('../'):
    return relative_path
  else:
    return filename
