#!/usr/bin/env python3
#
# Copyright (C) 2018 The Android Open Source Project
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

import sys

_XML = """\
<testsuites tests='1' failures='1' disabled='0' errors='0' time='0.1' name='AllTests'>
  <testsuite name='{testsuite}' tests='1' failures='1' disabled='0' errors='0' time='0.1'>
    <testcase name='{testcase}' status='run' result='completed' time='0.003' classname='{testsuite}'>
      <failure type='Failure'>GTest didn't generate any output. Maybe the run was aborted. This dummy xml template is used to signal the failure to the result parsing scripts.</failure>
    </testcase>
  </testsuite>
</testsuites>"""

print(_XML.format(testsuite=sys.argv[1], testcase=sys.argv[2]))
