# Copyright 2022 The Android Open Source Project
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

#!/usr/bin/env bash
#
# Bump the version.
#
# The CARGO_PKG_VERSION is not available for Android.bp builds
# so bump versions using a script.
#

# Absolute path to this script
SCRIPT=$(dirname $(readlink -f "$0"))
export CARGO=$SCRIPT/../rust/daemon/Cargo.toml
export CARGO_CLI=$SCRIPT/../rust/cli/Cargo.toml
export VERSION=$SCRIPT/../rust/daemon/src/version.rs
python <<EOF
import re
import os

m = None
for cargo in [os.environ["CARGO_CLI"], os.environ["CARGO"]]:
    with open(cargo, "r+") as f:

        version = re.compile(r'^version\s=\s"(\d+)\.(\d+)\.(\d+)"$')

        lines = f.readlines()
        for i, line in enumerate(lines):
            # Check if the line contains the string "version = "
            # and replace
            m = version.match(line)
            if m:
                lines[i] = 'version = "{0}.{1}.{2}"\n'.format(m[1], m[2], int(m[3]) + 1)
                break

        f.seek(0)
        f.writelines(lines)

with open(os.environ["VERSION"], "r+") as f:
        lines = f.readlines()
        for i, line in enumerate(lines):
            if line.startswith("pub const VERSION"):
               lines[i] = 'pub const VERSION: &str = "{0}.{1}.{2}";\n'.format(
                          m[1], m[2], (int(m[3]) + 1))
               break

        f.seek(0)
        f.writelines(lines)

EOF
