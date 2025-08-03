#  Copyright (C) 2023 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import sys
# TODO(b/311467339): add asbl support for test runs in the lab
# from absl import flags
from mobly import test_runner


"""Pass test arguments after '--' to the test runner. Needed for Mobly Test Runner.

Splits the arguments vector by '--'. Anything before separtor is treated as absl flags.
Everything after is a Mobly Test Runner arguments. Example:

    python3 <test> --itreations=2 -- -c /tmp/config.yaml

Example usage:

    if __name__ == '__main__':
        common_main()
"""
def common_main():
    absl_argv = []
    if '--' in sys.argv:
        index = sys.argv.index('--')
        absl_argv = sys.argv[:index]
        sys.argv = sys.argv[:1] + sys.argv[index + 1:]
    # TODO(b/311467339): add asbl support for test runs in the lab
    # flags.FLAGS(absl_argv)
    test_runner.main()
