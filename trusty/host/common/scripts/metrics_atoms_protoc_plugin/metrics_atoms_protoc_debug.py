#!/bin/sh
# Copyright (C) 2023 The Android Open Source Project
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
""":" # Shell script (in docstring to appease pylint)
# Find and invoke hermetic python3 interpreter
. "`dirname $0`"/../../../../../"trusty/vendor/google/aosp/scripts/envsetup.sh"
exec "$PY3" "$0" "$@"
# Shell script end

"""

import sys
import metrics_atoms_protoc_plugin


def main() -> None:
    in_file = sys.argv[1]
    pkg = sys.argv[2]
    with open(in_file, "rb") as f_data:
        data = f_data.read()

    metrics_atoms_protoc_plugin.process_data(data, pkg)


if __name__ == "__main__":
    main()
