#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Downloads a new ExoPlayer copy into platform/exoplayer/external.

The copy is put in tree_<SHA>/ directory, where <SHA> identifies the downloaded
commit.
"""
import argparse
import atexit
import datetime
import logging
import os
import re
import shutil
import subprocess
import sys
import tempfile

EXOPLAYER_SOURCE_REPO = "https://github.com/google/ExoPlayer.git"
METADATA_FILE = "METADATA"
SCRIPT_PARENT_DIRECTORY = os.path.dirname(os.path.abspath(sys.argv[0]))
TREE_LOCATION = "tree/"


def cd_to_script_parent_directory():
  os.chdir(SCRIPT_PARENT_DIRECTORY)


def run(command, check=True):
  logging.info(f"Running: $ {command}")
  return (subprocess.run(
      command, shell=True, check=check, capture_output=True,
      text=True).stdout.strip())


def confirm_deletion_or_exit(files_to_delete):
  print("The following files will not be added to exoplayer/external: \n" +
        files_to_delete)
  while True:
    print("Please confirm [Y/n] ")
    choice = input().lower()
    if choice in ["y", "yes", ""]:
      return
    elif choice in ["n", "no"]:
      sys.exit("User rejected the list of .mk files to exclude from the tree.")
    else:
      print("Please select y or n.")


logging.basicConfig(level=logging.INFO)

parser = argparse.ArgumentParser(
    description=f"Downloads an ExoPlayer copy into the tree_<SHA>/ "
    "directory, where <SHA> identifies the commit to download. This script "
    "also stages the changes for commit. Either --tag or --commit must be "
    "provided.")
refGroup = parser.add_mutually_exclusive_group(required=True)
refGroup.add_argument(
    "--tag", help="The tag that identifies the ExoPlayer commit to download.")
refGroup.add_argument(
    "--commit", help="The commit SHA of the ExoPlayer version to download.")
parser.add_argument(
    "--branch",
    help="The branch to create for the change.",
    default="download-exoplayer")
args = parser.parse_args()

cd_to_script_parent_directory()

# Check whether the branch exists, and abort if it does.
if run(f"git rev-parse --verify --quiet {args.branch}", check=False):
  sys.exit(f"\nBranch {args.branch} already exists. Please delete, or change "
           "branch.")

if run(f"repo start {args.branch}"):
  sys.exit(f"\nFailed to repo start {args.branch}. Check you don't have "
           "uncommited changes in your current branch.")

with tempfile.TemporaryDirectory() as tmpdir:
  logging.info(f"Created temporary directory {tmpdir}")
  run("git clone --no-checkout --filter=tree:0 "
      f"{EXOPLAYER_SOURCE_REPO} {tmpdir}")
  os.chdir(tmpdir)

  if args.tag:
    # Get the commit SHA associated to the tag.
    commit_sha = run(f"git rev-list -n 1 {args.tag}")
  else:  # A commit SHA was provided.
    commit_sha = args.commit

  # Checkout the version we want to update to.
  run(f"git checkout {commit_sha}")

  # Copy all files in the tree into tree_<SHA>.
  shutil.rmtree(".git/", ignore_errors=True)

  # Remove all Android.mk files in the exoplayer tree to avoid licensing issues.
  mk_files_to_remove = run("find . -name \"*.mk\"")
  confirm_deletion_or_exit(mk_files_to_remove)
  run("find . -name \"*.mk\" -delete")

  cd_to_script_parent_directory()
  new_tree_location = f"tree_{commit_sha}"
  run(f"mv {tmpdir} {new_tree_location}")
  run(f"git add {new_tree_location}")

with open(METADATA_FILE) as metadata_file:
  metadata_lines = metadata_file.readlines()

# Update the metadata file.
today = datetime.date.today()
with open(METADATA_FILE, "w") as metadata_file:
  for line in metadata_lines:
    line = re.sub(r"version: \".+\"", f"version: \"{args.tag or commit_sha}\"",
                  line)
    line = re.sub(
        r"last_upgrade_date {.+}", f"last_upgrade_date "
        f"{{ year: {today.year} month: {today.month} day: {today.day} }}", line)
    metadata_file.write(line)

run(f"git add {METADATA_FILE}")
logging.info("All done. Ready to commit.")
