# Copyright 2022 Google LLC. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the License);
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

"""is_eligible_friend"""

load("//:visibility.bzl", "RULES_DEFS_THAT_COMPILE_KOTLIN")

visibility(RULES_DEFS_THAT_COMPILE_KOTLIN)

def is_eligible_friend(target, friend):
    """
    Determines if `target` is allowed to use `internal` members of `friend`

    To be eligible, one of:
      1. `target` and `friend` in same pkg
      2. `target` in `testing/` subpkg of `friend` pkg
      3. `target` in `javatests/` pkg, `friend` in parallel `java/` pkg
      4. `target` in `test/java/` pkg, `friend` in parallel `main/java/` pkg

    Args:
      target: (Target) The current target
      friend: (Target) A potential friend of `target`

    Returns:
      True if `friend` is an eligible friend of `target`.
    """

    target_pkg = target.label.package + "/"
    friend_pkg = friend.label.package + "/"

    if target_pkg == friend_pkg:
        # Case 1
        return True

    if target_pkg.removesuffix("testing/") == friend_pkg:
        # Case 2
        return True

    if "javatests/" in target_pkg and "java/" in friend_pkg:
        # Case 3
        target_java_pkg = target_pkg.rsplit("javatests/", 1)[1]
        friend_java_pkg = friend_pkg.rsplit("java/", 1)[1]
        if target_java_pkg == friend_java_pkg:
            return True

    if ("test/java/" in target_pkg and "main/java/" in friend_pkg and
        True):
        # Case 4
        target_split = target_pkg.rsplit("test/java/", 1)
        friend_split = friend_pkg.rsplit("main/java/", 1)
        if target_split == friend_split:
            return True

    return False
