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
"""Manages the release process for Bazel binary.

This script walks the user through all steps required to cut a new
Bazel binary (and related artifacts) for AOSP. This script is intended
for use only by the current Bazel release manager.

Use './release_bazel.py --help` for usage details.
"""

import argparse
import glob
import os
import pathlib
import re
import subprocess
import sys
import tempfile

from typing import Final

# String formatting constants for prettyprint output.
BOLD: Final[str] = "\033[1m"
RESET: Final[str] = "\033[0m"

# The sourceroot-relative-path of the shell script which updates
# Bazel-related prebuilts to a given commit.
UPDATE_SCRIPT_PATH: Final[str] = "prebuilts/bazel/common/update.sh"
# All project directories that may be changed as a result of updating
# release prebuilts.
AFFECTED_PROJECT_DIRECTORIES: Final[list[str]] = [
    "prebuilts/bazel/common",
    "prebuilts/bazel/linux-x86_64",
    "prebuilts/bazel/darwin-x86_64",
]
MIXED_DROID_PATH: Final[str] = "build/bazel/ci/mixed_droid.sh"

# Global that represents the value of --dry-run
dry_run: bool

# Temporary directory used for log files.
# This directory should be unique per run of this script, and should
# thus only be initialized on demand and then reused for the rest
# of the run.
log_dir: str = None


def print_step_header(description):
  """Print the release process step with the given description."""
  print()
  print(f"{BOLD}===== {description}{RESET}")


def temp_file_path(filename) -> pathlib.Path:
  global log_dir
  parent_dir=os.path.expanduser("~/.cache/bazel-aosp")
  if not os.path.exists(parent_dir):
    os.makedirs(parent_dir)
  if log_dir is None:
    log_dir = tempfile.mkdtemp(dir=parent_dir)
  result = pathlib.Path(log_dir).joinpath(filename)
  result.touch()
  return result


def temp_dir_path(dirname) -> pathlib.Path:
  global log_dir
  parent_dir=os.path.expanduser("~/.cache/bazel-aosp")
  if not os.path.exists(parent_dir):
    os.makedirs(parent_dir)
  if log_dir is None:
    log_dir = tempfile.mkdtemp(dir=parent_dir)
  result = pathlib.Path(log_dir).joinpath(dirname)
  result.mkdir(exist_ok=True)
  return result


def prompt(s) -> bool:
  """Prompts the user for y/n input using the given string.

  Will not return until the user specifies either "y" or "n".
  Returns True if the user responded "y" and False if "n".
  """
  while True:
    response = input(s + " (y/n): ")
    if response == "y":
      print()
      return True
    elif response == "n":
      print()
      return False
    else:
      print("'%s' invalid, please specify y or n." % response)



def current_bazel_commit():
  """Returns the commit of the current checked-in Bazel binary."""
  current_bazel_files = glob.glob(
      "prebuilts/bazel/linux-x86_64/bazel_nojdk-*-linux-x86_64")
  if len(current_bazel_files) < 1:
    print("could not find an existing bazel named " +
          "prebuilts/bazel/linux-x86_64/bazel_nojdk.*")
    sys.exit(1)
  if len(current_bazel_files) > 1:
    print("found multiple bazel binaries under " +
          "prebuilts/bazel/linux-x86_64. Ensure that project is clean " +
          f"and synced. Found: {current_bazel_files}")
    sys.exit(1)
  match_group = re.search(
      "^prebuilts/bazel/linux-x86_64/bazel_nojdk-(.*)-linux-x86_64$",
      current_bazel_files[0])
  return match_group.group(1)


def ensure_commit_is_new(target_commit, bazel_src_dir):
  """Verify that the target commit is newer than the current Bazel."""

  curr_commit = current_bazel_commit()

  if target_commit == curr_commit:
    print("Requested commit matches current Bazel binary version.\n" +
          "If this is your first time running this script, this indicates " +
          "that no new release is necessary.\n\n" +
          "Alternatively:\n" +
          "  - If you want to rerun release verification after already " +
          "running this script, specify --verify-only.\n" +
          "  - If you want to rerun the update anyway (for example, " +
          "in the case that updating other tools failed), specify -f.")
    sys.exit(1)

  result = subprocess.run(
      ["git", "merge-base", "--is-ancestor", curr_commit, target_commit],
      cwd=bazel_src_dir,
      check=False)
  if result.returncode != 0:
    print(f"Requested commit {target_commit} is not a descendant of " +
          f"current Bazel binary commit {curr_commit}. Are you trying to " +
          "update to an older commit?\n" +
          "To force an update anyway, specify -f.")
    sys.exit(1)


def checkout_bazel_at(commit) -> pathlib.Path:
  clone_dir = temp_dir_path("bazelsrc")
  print(f"Cloning Bazel into {clone_dir}...")
  result = subprocess.run(
      ["git", "clone", "https://github.com/bazelbuild/bazel.git"],
      cwd=clone_dir,
      check=False)
  if result.returncode != 0:
    print("Clone failed.")
    sys.exit(1)

  bazel_src_dir = clone_dir.joinpath("bazel")
  result = subprocess.run(
      ["git", "checkout", commit],
      cwd=bazel_src_dir,
      check=False)
  if result.returncode != 0:
    print("Sync @%s failed." % commit)
  return bazel_src_dir


def ensure_projects_clean():
  """Ensure that relevant projects in the working directory are ready.

  The relevant projects must be clean, have fresh branches, and synced.
  """
  # TODO(b/239044269): Automate instead of asking the user.
  print_step_header("Manual step: Clear and sync all local projects.")
  is_new_input = prompt("Are all relevant local projects in your working " +
                        "directory clean (fresh branches) and synced to " +
                        "HEAD?")
  if not is_new_input:
    print("Please ready your local projects before continuing with the " +
          "release script")
    sys.exit(1)


def run_update(commit: str, bazel_src_dir: pathlib.Path):
  """Run the update script to update prebuilts.

  Retrieves a prebuilt bazel at the given commit, and updates other checked
  in bazel prebuilts using bazel source tree at that commit.
  """

  print_step_header("Updating prebuilts...")
  update_script_path = pathlib.Path(UPDATE_SCRIPT_PATH).resolve()

  cmd_args = [f"./{update_script_path.name}", commit, str(bazel_src_dir.absolute())]
  target_cwd = update_script_path.parent.absolute()
  print(f"Runnning update script (CWD: {target_cwd}): {' '.join(cmd_args)}")

  if not dry_run:
    logfile_path = temp_file_path("update.log")
    print(f"Streaming results to {logfile_path}")
    with logfile_path.open("w") as logfile:
      result = subprocess.run(
          cmd_args, cwd=target_cwd, check=False, stdout=logfile, stderr=logfile)
      if result.returncode != 0:
        print(f"Update failed. Check {logfile_path} for failure info.")
        sys.exit(1)
  else:
    print("Dry run: actual update skipped")

  print("Updated prebuilts successfully.")
  print("Note this may have changed the following directories:")
  for directory in AFFECTED_PROJECT_DIRECTORIES:
    print("  " + directory)


def verify_update():
  """Run tests to verify the integrity of the Bazel release.

  Failure during this step will require manual intervention by the user;
  a failure might be fixed by updating other dependencies in the Android
  tree, or might indicate that Bazel at the given commit is problematic
  and the release may need to be abandoned.
  """

  print_step_header("Verifying the update...")
  cmd_args = [MIXED_DROID_PATH]
  env = {
      "TARGET_BUILD_VARIANT": "userdebug",
      "TARGET_PRODUCT": "aosp_arm64",
      "PATH": os.environ["PATH"]
  }
  env_string = " ".join([k + "=" + v for k, v in env.items()])
  cmd_string = " ".join(cmd_args)

  print(f"Running {env_string} {cmd_string}")
  if dry_run:
    print("Dry run: Verification skipped")
    return

  logfile_path = temp_file_path("verify.log")
  print(f"Streaming results to {logfile_path}")
  with logfile_path.open("w") as logfile:
    result = subprocess.run(
        cmd_args, env=env, check=False, stdout=logfile, stderr=logfile)

  if result.returncode != 0:
    print(f"Verification failed. Check {logfile_path} for failure info.")
    print("Please remedy all issues until verification runs successfully.\n" +
          "You may skip to the verify step in this script by using " +
          "--verify-only")
    sys.exit(1)
  print("Verification successful.")


def create_commits():
  """Create commits for all projects related to the Bazel release."""
  print_step_header(
      "Manual step: Create CLs for all projects that need to be " + "updated.")
  # TODO(b/239044269): Automate instead of asking the user.
  commits_created = prompt("Have you created CLs for all projects that need " +
                           "to be updated?")
  if not commits_created:
    print(
        "Create CLs for all projects. After approval and CL submission, the " +
        "release is complete.")
    sys.exit(1)


def verify_run_from_top():
  """Verifies that this script is being run from the workspace root.

  Prints an error and exits if this is not the case.
  """
  if not pathlib.Path(UPDATE_SCRIPT_PATH).is_file():
    print(f"{UPDATE_SCRIPT_PATH} not found. Are you running from the " +
          "source root?")
    sys.exit(1)


def main():
  verify_run_from_top()

  parser = argparse.ArgumentParser(
      description="Walks the user through all steps required to cut a new " +
      "Bazel binary (and related artifacts) for AOSP. This script is " +
      "intended for use only by the current Bazel release manager.")
  parser.add_argument(
      "--commit",
      default=None,
    # TODO(b/239044269): Obtain the most recent pre-release Bazel commit
    # from github.
      nargs="?",
      help="The bazel commit hash to use. Must be specified.")
  parser.add_argument(
      "--force",
      "-f",
      action=argparse.BooleanOptionalAction,
      help="If true, will update bazel to the given commit " +
      "even if it is older than the current bazel binary.")
  parser.add_argument(
      "--verify-only",
      action=argparse.BooleanOptionalAction,
      help="If true, will only do verification and CL " +
      "creation if verification passes.")
  parser.add_argument(
      "--dry-run",
      action=argparse.BooleanOptionalAction,
      help="If true, will not make any changes to local " +
      "projects, and will instead output commands that " +
      "should be run to do so.")
  args = parser.parse_args()
  global dry_run
  dry_run = args.dry_run
  if not args.verify_only:
    commit = args.commit
    if not commit:
      raise Exception("Must specify a value for --commit")
    bazel_src_dir = checkout_bazel_at(commit)
    if not args.force:
      ensure_commit_is_new(commit, bazel_src_dir)
    ensure_projects_clean()
    run_update(commit, bazel_src_dir)

  verify_update()
  create_commits()

  print("Bazel release CLs created. After approval and " +
        "CL submission, the release is complete.")


if __name__ == "__main__":
  main()
