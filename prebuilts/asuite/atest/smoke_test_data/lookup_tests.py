# Copyright 2020, The Android Open Source Project
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

import os
import subprocess
import sys

import utils


# Must assign TITLE so that smoke_tests is able to print it.
TITLE = "Lookup Test Types"

def main():
    """Test looking up all test types via atest_integration_tests.py."""
    args = " --clear-cache --verify-cmd-mapping"
    atest_dir = os.path.join(os.getenv(utils.TOP), 'tools/asuite/atest')
    integration = os.path.join(atest_dir, 'atest_integration_tests.py')
    cmd = integration + args
    utils.print_banner(TITLE)
    try:
        os.chdir(os.getenv(utils.TOP))
        subprocess.check_output(cmd, shell=True)
    except subprocess.CalledProcessError as cpe:
        print(cpe)
        sys.exit(1)
    utils.print_banner(TITLE, True)
