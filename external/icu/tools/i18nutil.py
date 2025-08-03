# Copyright 2015 The Android Open Source Project
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

"""Utility methods associated with Android source and builds."""

from __future__ import print_function

import os
import sys
import tempfile

"""Shared functions for use in i18n scripts."""

def CheckDirExists(dir, dirname):
  if not os.path.isdir(dir):
    print("Couldn't find %s (%s)!" % (dirname, dir))
    sys.exit(1)


def GetAndroidRootOrDie():
  value = os.environ.get('ANDROID_BUILD_TOP')
  if not value:
    print("ANDROID_BUILD_TOP not defined: run envsetup.sh / lunch")
    sys.exit(1);
  CheckDirExists(value, '$ANDROID_BUILD_TOP')
  return value


def GetAndroidHostOutOrDie():
  value = os.environ.get('ANDROID_HOST_OUT')
  if not value:
    print("ANDROID_HOST_OUT not defined: run envsetup.sh / lunch")
    sys.exit(1);
  CheckDirExists(value, '$ANDROID_HOST_OUT')
  return value


def SwitchToNewTemporaryDirectory():
  tmp_dir = tempfile.mkdtemp('-i18n')
  os.chdir(tmp_dir)
  print('Created temporary directory "%s"...' % tmp_dir)
  return tmp_dir

