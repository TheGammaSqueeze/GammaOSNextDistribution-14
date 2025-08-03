#!/usr/bin/env -S python3 -B
#
# Copyright (C) 2021 The Android Open Source Project
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

"""Downloads ART Module prebuilts and creates CLs to update them in git."""

import argparse
import collections
import os
import re
import subprocess
import sys
import tempfile


# Prebuilt description used in commit message
PREBUILT_DESCR = "ART Module"

# fetch_artifact branch and targets
BRANCH = "aosp-master-art"
MODULE_TARGET = "DOES_NOT_EXIST" # There is currently no CI build in AOSP.
SDK_TARGET = "mainline_modules_sdks"

# Where to install the APEX modules
MODULE_PATH = "packages/modules/ArtPrebuilt"

# Where to install the SDKs and module exports
SDK_PATH = "prebuilts/module_sdk/art"

SDK_VERSION = "current"

# Paths to git projects to prepare CLs in
GIT_PROJECT_ROOTS = [MODULE_PATH, SDK_PATH]

SCRIPT_PATH = MODULE_PATH + "/update-art-module-prebuilts.py"


InstallEntry = collections.namedtuple("InstallEntry", [
    # Artifact path in the build, passed to fetch_target
    "source_path",
    # Local install path
    "install_path",
    # True if this is a module SDK, to be skipped by --skip-module-sdk.
    "module_sdk",
    # True if the entry is a zip file that should be unzipped to install_path
    "install_unzipped",
])


def install_apks_entry(apex_name):
  return [InstallEntry(
      os.path.join(apex_name + ".apks"),
      os.path.join(MODULE_PATH, apex_name + ".apks"),
      module_sdk=False,
      install_unzipped=False)]


def install_sdk_entries(apex_name, mainline_sdk_name, sdk_dir):
  return [InstallEntry(
      os.path.join("mainline-sdks",
                   "for-latest-build",
                   SDK_VERSION,
                   apex_name,
                   sdk_dir,
                   mainline_sdk_name + "-" + SDK_VERSION + ".zip"),
      os.path.join(SDK_PATH, SDK_VERSION, sdk_dir),
      module_sdk=True,
      install_unzipped=True)]


install_entries = (
    install_apks_entry("com.android.art") +
    install_sdk_entries("com.android.art",
                        "art-module-sdk", "sdk") +
    install_sdk_entries("com.android.art",
                        "art-module-host-exports", "host-exports") +
    install_sdk_entries("com.android.art",
                        "art-module-test-exports", "test-exports")
)


def check_call(cmd, **kwargs):
  """Proxy for subprocess.check_call with logging."""
  msg = " ".join(cmd) if isinstance(cmd, list) else cmd
  if "cwd" in kwargs:
    msg = "In " + kwargs["cwd"] + ": " + msg
  print(msg)
  subprocess.check_call(cmd, **kwargs)


def fetch_artifact(branch, target, build, fetch_pattern, local_dir):
  """Fetches artifact from the build server."""
  fetch_artifact_path = "/google/data/ro/projects/android/fetch_artifact"
  cmd = [fetch_artifact_path, "--branch", branch, "--target", target,
         "--bid", build, fetch_pattern]
  check_call(cmd, cwd=local_dir)


def start_branch(git_branch_name, git_dirs):
  """Creates a new repo branch in the given projects."""
  check_call(["repo", "start", git_branch_name] + git_dirs)
  # In case the branch already exists we reset it to upstream, to get a clean
  # update CL.
  for git_dir in git_dirs:
    check_call(["git", "reset", "--hard", "@{upstream}"], cwd=git_dir)


def upload_branch(git_root, git_branch_name):
  """Uploads the CLs in the given branch in the given project."""
  # Set the branch as topic to bundle with the CLs in other git projects (if
  # any).
  check_call(["repo", "upload", "-t", "--br=" + git_branch_name, git_root])


def remove_files(git_root, subpaths, stage_removals):
  """Removes files in the work tree, optionally staging them in git."""
  if stage_removals:
    check_call(["git", "rm", "-qrf", "--ignore-unmatch"] + subpaths, cwd=git_root)
  # Need a plain rm afterwards even if git rm was executed, because git won't
  # remove directories if they have non-git files in them.
  check_call(["rm", "-rf"] + subpaths, cwd=git_root)


def commit(git_root, prebuilt_descr, branch, target, build, add_paths, bug_number):
  """Commits the new prebuilts."""
  check_call(["git", "add"] + add_paths, cwd=git_root)

  if build:
    message = (
        "Update {prebuilt_descr} prebuilts to build {build}.\n\n"
        "Taken from branch {branch}, target {target}."
        .format(prebuilt_descr=prebuilt_descr, branch=branch, target=target,
                build=build))
  else:
    message = (
        "DO NOT SUBMIT: Update {prebuilt_descr} prebuilts from local build."
        .format(prebuilt_descr=prebuilt_descr))
  message += ("\n\nCL prepared by {}."
              "\n\nTest: Presubmits".format(SCRIPT_PATH))
  if bug_number:
    message += ("\nBug: {}".format(bug_number))
  msg_fd, msg_path = tempfile.mkstemp()
  with os.fdopen(msg_fd, "w") as f:
    f.write(message)

  # Do a diff first to skip the commit without error if there are no changes to
  # commit.
  check_call("git diff-index --quiet --cached HEAD -- || "
             "git commit -F " + msg_path, shell=True, cwd=git_root)
  os.unlink(msg_path)


def install_entry(branch, target, build, local_dist, entry):
  """Installs one file specified by entry."""

  install_dir, install_file = os.path.split(entry.install_path)
  if install_dir and not os.path.exists(install_dir):
    os.makedirs(install_dir)

  if build:
    fetch_artifact(branch, target, build, entry.source_path, install_dir)
  else:
    check_call(["cp", os.path.join(local_dist, entry.source_path), install_dir])
  source_file = os.path.basename(entry.source_path)

  if entry.install_unzipped:
    check_call(["mkdir", install_file], cwd=install_dir)
    # Add -DD to not extract timestamps that may confuse the build system.
    check_call(["unzip", "-DD", source_file, "-d", install_file],
               cwd=install_dir)
    check_call(["rm", source_file], cwd=install_dir)

  elif source_file != install_file:
    check_call(["mv", source_file, install_file], cwd=install_dir)


def install_paths_per_git_root(roots, paths):
  """Partitions the given paths into subpaths within the given roots.

  Args:
    roots: List of root paths.
    paths: List of paths relative to the same directory as the root paths.

  Returns:
    A dict mapping each root to the subpaths under it. It's an error if some
    path doesn't go into any root.
  """
  res = collections.defaultdict(list)
  for path in paths:
    found = False
    for root in roots:
      if path.startswith(root + "/"):
        res[root].append(path[len(root) + 1:])
        found = True
        break
    if not found:
      sys.exit("Install path {} is not in any of the git roots: {}"
               .format(path, " ".join(roots)))
  return res


def get_args():
  """Parses and returns command line arguments."""
  parser = argparse.ArgumentParser(
      epilog="Either --build or --local-dist is required.")

  parser.add_argument("--branch", default=BRANCH,
                      help="Branch to fetch, defaults to " + BRANCH)
  parser.add_argument("--module-target", default=MODULE_TARGET,
                      help="Target to fetch modules from, defaults to " +
                      MODULE_TARGET)
  parser.add_argument("--sdk-target", default=SDK_TARGET,
                      help="Target to fetch SDKs from, defaults to " +
                      SDK_TARGET)
  parser.add_argument("--build", metavar="NUMBER",
                      help="Build number to fetch")
  parser.add_argument("--local-dist", metavar="PATH",
                      help="Take prebuilts from this local dist dir instead of "
                      "using fetch_artifact")
  parser.add_argument("--skip-apex", default=True, action="store_true",
                      help="Do not fetch .apex files. Defaults to true.")
  parser.add_argument("--skip-module-sdk", action="store_true",
                      help="Do not fetch and unpack sdk and module_export zips.")
  parser.add_argument("--skip-cls", action="store_true",
                      help="Do not create branches or git commits")
  parser.add_argument("--bug", metavar="NUMBER",
                      help="Add a 'Bug' line with this number to commit "
                      "messages.")
  parser.add_argument("--upload", action="store_true",
                      help="Upload the CLs to Gerrit")

  args = parser.parse_args()
  if ((not args.build and not args.local_dist) or
      (args.build and args.local_dist)):
    sys.exit(parser.format_help())
  return args


def main():
  """Program entry point."""
  args = get_args()

  if any(path for path in GIT_PROJECT_ROOTS if not os.path.exists(path)):
    sys.exit("This script must be run in the root of the Android build tree.")

  entries = install_entries
  if args.skip_apex:
    entries = [entry for entry in entries if entry.module_sdk]
  if args.skip_module_sdk:
    entries = [entry for entry in entries if not entry.module_sdk]
  if not entries:
    sys.exit("Both APEXes and SDKs skipped - nothing to do.")

  install_paths = [entry.install_path for entry in entries]
  install_paths_per_root = install_paths_per_git_root(
      GIT_PROJECT_ROOTS, install_paths)

  git_branch_name = PREBUILT_DESCR.lower().replace(" ", "-") + "-update"
  if args.build:
    git_branch_name += "-" + args.build

  if not args.skip_cls:
    git_paths = list(install_paths_per_root.keys())
    start_branch(git_branch_name, git_paths)

  for git_root, subpaths in install_paths_per_root.items():
    remove_files(git_root, subpaths, not args.skip_cls)
  for entry in entries:
    target = args.sdk_target if entry.module_sdk else args.module_target
    install_entry(args.branch, target, args.build, args.local_dist, entry)

  if not args.skip_cls:
    for git_root, subpaths in install_paths_per_root.items():
      target = args.sdk_target if git_root == SDK_PATH else args.module_target
      commit(git_root, PREBUILT_DESCR, args.branch, target, args.build, subpaths,
             args.bug)

    if args.upload:
      # Don't upload all projects in a single repo upload call, because that
      # makes it pop up an interactive editor.
      for git_root in install_paths_per_root:
        upload_branch(git_root, git_branch_name)


if __name__ == "__main__":
  main()
