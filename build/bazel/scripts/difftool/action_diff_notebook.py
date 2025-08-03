#!/usr/bin/env python3
#
# Copyright (C) 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Provides useful diff information for build artifacts.

This file is intended to be used like a Jupyter notebook. Since there isn't a
one-to-one pairing between Soong intermediate artifacts and Bazel intermediate
artifacts, I've found it's easiest to automate some of the diffing while
leaving room for manual selection of what targets/artifacts to compare.

In this file, the runnable sections are separated by the `# %%` identifier, and
a compatible editor should be able to run those code blocks independently. I
used VSCode during development, but this functionality also exists in other
editors via plugins.

There are some comments throughout to give an idea of how this notebook can be
used.
"""

# %%
import os
import pathlib

# This script should be run from the $TOP directory
ANDROID_CHECKOUT_PATH = pathlib.Path(".").resolve()
os.chdir(ANDROID_CHECKOUT_PATH)

# %%
import subprocess

os.chdir(os.path.join(ANDROID_CHECKOUT_PATH, "build/bazel/scripts/difftool"))
import difftool
import commands
import importlib

# Python doesn't reload packages that have already been imported unless you
# use importlib to explicitly reload them
importlib.reload(difftool)
importlib.reload(commands)
os.chdir(ANDROID_CHECKOUT_PATH)

# %%
LUNCH_TARGET = "aosp_arm64"
TARGET_BUILD_VARIANT = "userdebug"

subprocess.run([
    "build/soong/soong_ui.bash",
    "--make-mode",
    f"TARGET_PRODUCT={LUNCH_TARGET}",
    f"TARGET_BUILD_VARIANT={TARGET_BUILD_VARIANT}",
    "--skip-soong-tests",
    "bp2build",
    "nothing",
])


# %%
def get_bazel_actions(
    *, expr: str, config: str, mnemonic: str, additional_args: list[str] = []
):
  return difftool.collect_commands_bazel(
      expr, config, mnemonic, *additional_args
  )


def get_ninja_actions(*, lunch_target: str, target: str, mnemonic: str):
  ninja_output = difftool.collect_commands_ninja(
      pathlib.Path(f"out/combined-{lunch_target}.ninja").resolve(),
      pathlib.Path(target),
      pathlib.Path("prebuilts/build-tools/linux-x86/bin/ninja").resolve(),
  )
  return [l for l in ninja_output if mnemonic in l]

# %%
# Example 1: Comparing link actions
# This example gets all of the "CppLink" actions from the adb_test module, and
# also gets the build actions that are needed to build the same module from
# through Ninja.
#
# After getting the action lists from each build tool, you can inspect the list
# to find the particular action you're interested in diffing. In this case, there
# was only 1 CppLink action from Bazel. The corresponding link action from Ninja
# happened to be the last one (this is pretty typical).
#
# Then we set a new variable to keep track of each of these action strings.

bzl_actions = get_bazel_actions(
    config="linux_x86_64",
    expr="//packages/modules/adb:adb_test__test_binary_unstripped",
    mnemonic="CppLink",
)
ninja_actions = get_ninja_actions(
    lunch_target=LUNCH_TARGET,
    target="out/soong/.intermediates/packages/modules/adb/adb_test/linux_glibc_x86_64/adb_test",
    mnemonic="clang++",
)
bazel_action = bzl_actions[0]["arguments"]
ninja_action = ninja_actions[-1].split()

# %%
# Example 2: Comparing compile actions
# This example is similar and gets all of the "CppCompile" actions from the
# internal sub-target of adb_test. There is a "CppCompile" action for every
# .cc file that goes into the target, so we just pick one of these files and
# get the corresponding compile action from Ninja for this file.
#
# Similarly, we select an action from the Bazel list and its corresponding
# Ninja action.

# bzl_actions = get_bazel_actions(
#     config="linux_x86_64",
#     expr="//packages/modules/adb:adb_test__test_binary__internal_root_cpp",
#     mnemonic="CppCompile",
# )
# ninja_actions = get_ninja_actions(
#     lunch_target=LUNCH_TARGET,
#     target="out/soong/.intermediates/packages/modules/adb/adb_test/linux_glibc_x86_64/obj/packages/modules/adb/adb_io_test.o",
#     mnemonic="clang++",
# )
# bazel_action = bzl_actions[0]["arguments"]
# ninja_action = ninja_actions[-1].split()

# %%
# Example 3: more complex expressions in the Bazel action
# This example gets all of the "CppCompile" actions from the deps of everything
# under the //packages/modules/adb package, but it uses the additional_args
# to exclude "manual" internal targets.

# bzl_actions = get_bazel_actions(
#     config="linux_x86_64",
#     expr="deps(//packages/modules/adb/...)",
#     mnemonic="CppCompile",
#     additional_args=[
#         "--build_tag_filters=-manual",
#     ],
# )

# %%
# Once we have the command-line string for each action from Bazel and Ninja,
# we can use difftool to parse and compare the actions.
ninja_action = commands.expand_rsp(ninja_action)
bzl_rich_commands = difftool.rich_command_info(" ".join(bazel_action))
ninja_rich_commands = difftool.rich_command_info(" ".join(ninja_action))

print("\nBazel args:")
print(" \\\n\t".join([bzl_rich_commands.tool] + bzl_rich_commands.args))
print("\nSoong args:")
print(" \\\n\t".join([ninja_rich_commands.tool] + ninja_rich_commands.args))

bzl_only = bzl_rich_commands.compare(ninja_rich_commands)
soong_only = ninja_rich_commands.compare(bzl_rich_commands)
print("\nIn Bazel, not Soong:")
print(bzl_only)
print("\nIn Soong, not Bazel:")
print(soong_only)

# %%
# Now that we've diffed the action strings, it is sometimes useful to also
# diff the paths that go into the action. This helps us narrow down diffs
# in a module that are created in their dependencies. This section attempts
# to match paths from the Bazel action to corresponding paths in the Ninja
# action, and the runs difftool on these paths.
bzl_paths, _ = commands.extract_paths_from_action_args(bazel_action)
ninja_paths, _ = commands.extract_paths_from_action_args(ninja_action)
unmatched_paths = []
for p1, p2 in commands.match_paths(bzl_paths, ninja_paths).items():
  if p2 is None:
    unmatched_paths.append(p1)
    continue
  diff = difftool.file_differences(
      pathlib.Path(p1).resolve(),
      pathlib.Path(p2).resolve(),
      level=difftool.DiffLevel.FINE,
  )
  for row in diff:
    print(row)
if unmatched_paths:
  # Since the test for file paths looks for existing files, this matching won't
  # work if the Soong artifacts don't exist.
  print(
      "Found some Bazel paths that didn't have a good match in Soong "
      + "intermediates. Did you run `m`?"
  )
  print("Unmatched paths:")
  for i in unmatched_paths:
    print("\t" + i)
