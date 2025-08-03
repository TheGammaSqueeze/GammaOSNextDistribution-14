# Copyright (C) 2023 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""List of clang versions supported by Kleaf.

We can't use a glob expression because each directory is an actual bazel
package. The glob expression is going to produce empty result, **unless** one
delete `clang-*/BUILD.bazel`, OR use --delete_packages (except that
--deleted_packages does not accept patterns, so we still need to provide a
explicit list somewhere).

The alternative solution is to create a workspace rule to read the directory.
Caveat to this approach:
- Need to hardcode the path `prebuilts/clang/host/linux-x86`, which has
  adversary effect when using Kleaf as a subworkspace
- Reading a directory can't be done with a shell command because one needs to
  ensure hermeticity by hardcoding the path to `ls`
- Reading the directory must be done with path.readdir.

Such workspace rule is much more heavy-weighted than a simple array, hence we
did not move forward with this approach unless it requires a lot of maintenance
in the future.
"""

VERSIONS = [
    # keep sorted
    "r450784e",
    "r475365b",
    "r487747c",
    "r498229b",
    "r510928",
]