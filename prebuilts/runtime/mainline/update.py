#!/usr/bin/env -S python3 -B
#
# Copyright (C) 2020 The Android Open Source Project
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

"""Downloads prebuilts for ART Module dependencies and creates CLs in git."""

import argparse
import collections
import os
import subprocess
import sys
import tempfile


# Prebuilt description used in commit message
PREBUILT_DESCR = "ART dependencies"


# Branch and target tuple for a CI build
BuildSource = collections.namedtuple("BuildSource", [
    "branch",
    "target",
])


# The list of arches does not include riscv64: it is not supported by mainline,
# and it is updated from a local build with --local-dist-riscv64 option (which
# overwrites the list of arches to include only riscv64).
#
# TODO(b/286551985): add riscv64 here and don't override the global list.
ARCHES = ["arm", "arm64", "x86", "x86_64"]

# CI build for SDK snapshots
SDK_SOURCE = BuildSource("aosp-main",
                         "mainline_modules_sdks-trunk_staging-userdebug")

# Architecture-specific CI builds for APEXes. These are only used in the chroot
# test setup (see art/tools/buildbot-build.sh). They never become part of any
# dist artifact.
#
# There are currently no CI builds except for x86_64, so APEX updates are
# skipped by default.
APEX_SOURCE = {
    "x86_64": BuildSource("aosp-main",
                          "mainline_modules_x86_64-trunk_staging-userdebug"),
}

# Architecture-specific CI builds for implementation libraries. These are only
# used in the chroot test setup (see art/tools/buildbot-build.sh). They never
# become part of any dist artifact.
#
# We'd prefer to take all these from the aosp-main branch, but in case they're
# not available there we have the option to use aosp-main-throttled as well. As
# long as they're new enough to contain the required changes (see README.md
# instructions) and pass tests, it doesn't matter much which exact builds they
# come from. riscv64 is updated from a local build (it is not supported by
# mainline), the target here is used only to construct path in
# `install_impl_lib_entries`.
IMPL_LIB_SOURCE = {
    "arm": None, # There's no longer any AOSP CI build for this arch.
    "arm64": BuildSource("aosp-main",
                         "aosp_arm64-trunk_staging-userdebug"),
    "riscv64": BuildSource("aosp-main",
                           "aosp_riscv64-trunk_staging-userdebug"),
    "x86": BuildSource("aosp-main",
                       "aosp_x86-trunk_staging-userdebug"),
    "x86_64": BuildSource("aosp-main",
                          "aosp_x86_64-trunk_staging-userdebug"),
}

# Paths to git projects to prepare CLs in
GIT_PROJECT_ROOTS = [
    "prebuilts/module_sdk/conscrypt",
    "prebuilts/module_sdk/StatsD",
    "prebuilts/runtime",
]

SDK_VERSION = "current"

SCRIPT_PATH = "prebuilts/runtime/mainline/update.py"


InstallEntry = collections.namedtuple(
    "InstallEntry",
    [
        # One of "apex", "module_sdk" and "impl_lib", for the --skip-* flags.
        "type",
        # Source CI build as a BuildSource tuple, or None if none exists.
        "source_build",
        # Artifact path in the build, passed to fetch_target. Should match a
        # single file. "{BUILD}" gets replaced with the build number.
        "source_path",
        # Local install path.
        "install_path",
        # True if the entry is a zip file that should be unzipped to
        # install_path.
        "install_unzipped",
        # If set, the entry is a zip from which this single file is extracted to
        # install_path.
        "unzip_single_file",
    ],
    defaults=(False, None), # Defaults for install_unzipped and unzip_single_file.
)


def install_apex_entries(module_name, apex_name):
  return [
      InstallEntry(
          type="apex",
          source_build=APEX_SOURCE.get(arch, None),
          source_path=os.path.join(
              "mainline_modules_" + arch,
              apex_name + ".apex"),
          install_path=os.path.join(
              "prebuilts/runtime/mainline",
              module_name,
              "apex",
              apex_name + "-" + arch + ".apex"))
      for arch in ARCHES]


def install_unbundled_sdk_entries(apex_name, mainline_sdk_name, sdk_type, install_path):
  if "riscv64" in ARCHES:
    if sdk_type == "host-exports": # no host prebuilts for riscv64
      return []
    install_path = os.path.join("prebuilts/runtime/mainline/local_riscv64",
                                install_path)
  return [
      InstallEntry(
          type="module_sdk",
          source_build=SDK_SOURCE,
          source_path=os.path.join(
              "mainline-sdks/for-latest-build",
              SDK_VERSION,
              apex_name,
              sdk_type,
              mainline_sdk_name + "-" + sdk_type + "-" + SDK_VERSION + ".zip"),
          install_path=install_path,
          install_unzipped=True)]


def install_bundled_sdk_entries(module_name, sdk_type):
  if "riscv64" in ARCHES:
    if sdk_type == "host-exports": # no host prebuilts for riscv64
      return []
  return [
      InstallEntry(
          type="module_sdk",
          source_build=SDK_SOURCE,
          source_path=os.path.join(
              "bundled-mainline-sdks",
              "com.android." + module_name,
              sdk_type,
              module_name + "-module-" + sdk_type + "-" + SDK_VERSION + ".zip"),
          install_path=os.path.join(
              "prebuilts/runtime/mainline",
              module_name,
              sdk_type),
          install_unzipped=True)]


def install_platform_mainline_sdk_entries(sdk_type):
  if "riscv64" in ARCHES:
    if sdk_type == "host-exports": # no host prebuilts for riscv64
      return []
  return [
      InstallEntry(
          type="module_sdk",
          source_build=SDK_SOURCE,
          source_path=os.path.join(
              "bundled-mainline-sdks",
              "platform-mainline",
              sdk_type,
              "platform-mainline-" + sdk_type + "-" + SDK_VERSION + ".zip"),
          install_path=os.path.join(
              "prebuilts/runtime/mainline/platform",
              sdk_type),
          install_unzipped=True)]


def install_impl_lib_entries(lib_name):
  return [
      InstallEntry(
          type="impl_lib",
          source_build=IMPL_LIB_SOURCE[arch],
          source_path=(
              IMPL_LIB_SOURCE[arch].target.rpartition("-")[0] +
              "-target_files-{BUILD}.zip" if IMPL_LIB_SOURCE[arch] else None),
          unzip_single_file=os.path.join(
              "SYSTEM",
              "lib64" if arch.endswith("64") else "lib",
              lib_name),
          install_path=os.path.join(
              "prebuilts/runtime/mainline/platform/impl",
              arch,
              lib_name))
      for arch in ARCHES]


# This is defined as a function (not a global list) because it depends on the
# list of architectures, which may change after parsing options.
def install_entries():
  return (
    # Conscrypt
    install_apex_entries("conscrypt", "com.android.conscrypt") +
    install_unbundled_sdk_entries(
        "com.android.conscrypt", "conscrypt-module", "sdk",
        "prebuilts/module_sdk/conscrypt/current") +
    install_unbundled_sdk_entries(
        "com.android.conscrypt", "conscrypt-module", "test-exports",
        "prebuilts/module_sdk/conscrypt/current/test-exports") +
    install_unbundled_sdk_entries(
        "com.android.conscrypt", "conscrypt-module", "host-exports",
        "prebuilts/module_sdk/conscrypt/current/host-exports") +

    # Runtime (Bionic)
    # sdk and host-exports must always be updated together, because the linker
    # and the CRT object files gets embedded in the binaries on linux host
    # Bionic (see code and comments around host_bionic_linker_script in
    # build/soong).
    install_apex_entries("runtime", "com.android.runtime") +
    install_bundled_sdk_entries("runtime", "sdk") +
    install_bundled_sdk_entries("runtime", "host-exports") +

    # I18N
    install_apex_entries("i18n", "com.android.i18n") +
    install_bundled_sdk_entries("i18n", "sdk") +
    install_bundled_sdk_entries("i18n", "test-exports") +

    # tzdata
    install_apex_entries("tzdata", "com.android.tzdata") +
    install_bundled_sdk_entries("tzdata", "test-exports") +

    # statsd
    install_apex_entries("statsd", "com.android.os.statsd") +
    install_unbundled_sdk_entries(
        "com.android.os.statsd", "statsd-module", "sdk",
        "prebuilts/module_sdk/StatsD/current") +

    # Platform
    install_platform_mainline_sdk_entries("sdk") +
    install_platform_mainline_sdk_entries("test-exports") +
    install_impl_lib_entries("heapprofd_client_api.so") +
    install_impl_lib_entries("libartpalette-system.so") +
    install_impl_lib_entries("liblog.so") +
    install_impl_lib_entries("libbinder_ndk.so") +
    # libbinder_ndk dependencies:
    install_impl_lib_entries("libandroid_runtime_lazy.so") +
    install_impl_lib_entries("libbase.so") +
    install_impl_lib_entries("libbinder.so") +
    install_impl_lib_entries("libcutils.so") +
    install_impl_lib_entries("libutils.so") +
    install_impl_lib_entries("libvndksupport.so") +

    [])


def check_call(cmd, **kwargs):
  """Proxy for subprocess.check_call with logging."""
  msg = " ".join(cmd) if isinstance(cmd, list) else cmd
  if "cwd" in kwargs:
    msg = "In " + kwargs["cwd"] + ": " + msg
  print(msg)
  subprocess.check_call(cmd, **kwargs)


def fetch_artifact(branch, target, build, fetch_pattern, local_dir, zip_entry=None):
  """Fetches artifact from the build server."""
  fetch_artifact_path = "/google/data/ro/projects/android/fetch_artifact"
  cmd = [fetch_artifact_path, "--branch", branch, "--target", target,
         "--bid", build, fetch_pattern]
  if zip_entry:
    cmd += ["--zip_entry", zip_entry]
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


def commit(git_root, prebuilt_descr, installed_sources, add_paths, bug_number):
  """Commits the new prebuilts."""
  check_call(["git", "add"] +
             [path for path in add_paths
              if os.path.exists(os.path.join(git_root, path))],
             cwd=git_root)

  if installed_sources:
    message = "Update {} prebuilts.\n\n".format(prebuilt_descr)
    if len(installed_sources) == 1:
      message += "Taken from {}.".format(installed_sources[0])
    else:
      message += "Taken from:\n{}".format(
          "\n".join([s.capitalize() for s in installed_sources]))
  else:
    # For riscv64, update from a local tree is the only available option.
    message = "" if "riscv64" in ARCHES else "DO NOT SUBMIT: "
    message += (
        "Update {prebuilt_descr} prebuilts from local build."
        .format(prebuilt_descr=prebuilt_descr))
  message += ("\n\nCL prepared by {}."
              "\n\nTest: Presubmits".format(SCRIPT_PATH))
  if bug_number:
    message += ("\nBug: {}".format(bug_number))

  msg_fd, msg_path = tempfile.mkstemp()
  try:
    with os.fdopen(msg_fd, "w") as f:
      f.write(message)
    # Do a diff first to skip the commit without error if there are no changes
    # to commit.
    check_call("git diff-index --quiet --cached HEAD -- || "
               "git commit -F " + msg_path, shell=True, cwd=git_root)
  finally:
    os.unlink(msg_path)


def install_entry(tmp_dir, local_dist, build_numbers, entry):
  """Installs one file specified by entry."""

  if not local_dist and not entry.source_build:
    print("WARNING: No CI build for {} - skipping.".format(entry.source_path))
    return None

  build_number = (build_numbers[entry.source_build.branch]
                  if build_numbers else None)

  # Fall back to the username as the build ID if we have no build number. That's
  # what a dist install does in a local build.
  source_path = entry.source_path.replace(
      "{BUILD}", str(build_number) if build_number else os.getenv("USER"))

  source_dir, source_file = os.path.split(source_path)

  if local_dist:
    download_dir = os.path.join(tmp_dir, source_dir)
  else:
    download_dir = os.path.join(tmp_dir,
                                entry.source_build.branch,
                                build_number,
                                entry.source_build.target,
                                source_dir)
  os.makedirs(download_dir, exist_ok=True)
  download_file = os.path.join(download_dir, source_file)

  unzip_dir = None
  unzip_file = None
  if entry.unzip_single_file:
    unzip_dir = os.path.join(download_dir,
                             source_path.removesuffix(".zip") + "_unzip")
    os.makedirs(unzip_dir, exist_ok=True)
    unzip_file = os.path.join(unzip_dir, entry.unzip_single_file)

  if not local_dist and unzip_file:
    if not os.path.exists(unzip_file):
      # Use the --zip_entry option to fetch_artifact to avoid downloading the
      # whole zip.
      fetch_artifact(entry.source_build.branch,
                     entry.source_build.target,
                     build_number,
                     source_path,
                     unzip_dir,
                     entry.unzip_single_file)
      if not os.path.exists(unzip_file):
        sys.exit("fetch_artifact didn't create expected file {}".format(unzip_file))
  else:
    # Fetch files once by downloading them into a specific location in tmp_dir
    # only if they're not already there.
    if not os.path.exists(download_file):
      if local_dist:
        check_call(["cp", os.path.join(local_dist, source_path), download_dir])
      else:
        fetch_artifact(entry.source_build.branch,
                       entry.source_build.target,
                       build_number,
                       source_path,
                       download_dir)
      if not os.path.exists(download_file):
        sys.exit("Failed to retrieve {}".format(source_path))

  install_dir, install_file = os.path.split(entry.install_path)
  os.makedirs(install_dir, exist_ok=True)

  if entry.install_unzipped:
    if "riscv64" in ARCHES:
      tmp_dir = os.path.join(install_file, "tmp")
      check_call(["mkdir", "-p", tmp_dir], cwd=install_dir)
      check_call(["unzip", "-DD", "-o", download_file, "-d", tmp_dir],
                 cwd=install_dir)
      # Conscrypt and statsd are not owned by the ART team, so we keep a local
      # copy if their prebuilts with Android.bp files renamed to ArtThinBuild.bp
      # to avoid Soong adding them as part of the build graph.
      if "local_riscv64" in install_dir:
        patch_cmd = ("sed -i 's|This is auto-generated. DO NOT EDIT.|"
            "DO NOT COMMIT. Changes in this file are temporary and generated "
            "by art/tools/buildbot-build.sh. See b/286551985.|g' {} ; ")
        rename_cmd = 'f="{}" ; mv "$f" "$(dirname $f)"/ArtThinBuild.bp'
        check_call(["find", "-type", "f", "-name", "Android.bp",
                       "-exec", "sh", "-c", patch_cmd + rename_cmd, ";"],
                   cwd=os.path.join(install_dir, tmp_dir))
      check_call(["find", "-type", "f", "-regextype", "posix-extended",
                     "-regex", ".*riscv64.*|.*Android.bp|.*ArtThinBuild.bp",
                     "-exec", "cp", "--parents", "{}", "..", ";"],
                 cwd=os.path.join(install_dir, tmp_dir))
      check_call(["rm", "-rf", tmp_dir], cwd=install_dir)
    else:
      check_call(["mkdir", install_file], cwd=install_dir)
      # Add -DD to not extract timestamps that may confuse the build system.
      check_call(["unzip", "-DD", download_file, "-d", install_file],
                 cwd=install_dir)
  elif entry.unzip_single_file:
    if not os.path.exists(unzip_file):
      check_call(["unzip", download_file, "-d", unzip_dir, entry.unzip_single_file])
    check_call(["cp", unzip_file, install_file], cwd=install_dir)
  else:
    check_call(["cp", download_file, install_file], cwd=install_dir)

  # Return a description of the source location for inclusion in the commit
  # message.
  return (
      "branch {}, target {}, build {}".format(
          entry.source_build.branch,
          entry.source_build.target,
          build_number)
      if not local_dist else None)


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
      epilog="Either --aosp-main-build and --aosp-main-throttled-build, "
      "or --local-dist, is required.")

  parser.add_argument("--aosp-main-build", metavar="NUMBER",
                      help="Build number to fetch from aosp-main")
  parser.add_argument("--aosp-main-throttled-build", metavar="NUMBER",
                      help="Build number to fetch from aosp-main-throttled")
  parser.add_argument("--local-dist", metavar="PATH",
                      help="Take prebuilts from this local dist dir instead of "
                      "using fetch_artifact")
  parser.add_argument("--local-dist-riscv64", metavar="PATH",
                      help="Copy riscv64 prebuilts from a local path, which "
                      "must be $HOME/<path-to-aosp-root>/out/dist with prebuilts "
                      "already built for aosp_riscv64-trunk_staging-userdebug "
                      "target as described in README_riscv64.md. Only "
                      "riscv64-specific files and Android.bp are updated. "
                      "Options such as --skip-apex, --skip-module-sdk, "
                      "--skip-impl-lib are ignored. It is a temporary "
                      "workaround until mainline supports riscv64.")
  parser.add_argument("--skip-apex", default=True,
                      action=argparse.BooleanOptionalAction,
                      help="Do not fetch .apex files.")
  parser.add_argument("--skip-module-sdk", action="store_true",
                      help="Do not fetch and unpack sdk and module_export zips.")
  parser.add_argument("--skip-impl-lib", action="store_true",
                      help="Do not fetch implementation libraries.")
  parser.add_argument("--skip-cls", action="store_true",
                      help="Do not create branches or git commits")
  parser.add_argument("--bug", metavar="NUMBER",
                      help="Add a 'Bug' line with this number to commit "
                      "messages.")
  parser.add_argument("--upload", action="store_true",
                      help="Upload the CLs to Gerrit")
  parser.add_argument("--tmp-dir", metavar="PATH",
                      help="Temporary directory for downloads. The default is "
                      "to create one and delete it when finished, but this one "
                      "will be kept, and any files already in it won't be "
                      "downloaded again.")

  args = parser.parse_args()

  if args.local_dist_riscv64:
    global ARCHES
    ARCHES = ["riscv64"]
    args.local_dist = args.local_dist_riscv64

  got_build_numbers = bool(args.aosp_main_build and
                           args.aosp_main_throttled_build)
  if ((not got_build_numbers and not args.local_dist) or
      (got_build_numbers and args.local_dist)):
    sys.exit(parser.format_help())

  return args


def main():
  """Program entry point."""
  args = get_args()

  if any(path for path in GIT_PROJECT_ROOTS if not os.path.exists(path)):
    sys.exit("This script must be run in the root of the Android build tree.")

  build_numbers = None
  if args.aosp_main_build:
    build_numbers = {
        "aosp-main": args.aosp_main_build,
        "aosp-main-throttled": args.aosp_main_throttled_build,
    }

  entries = install_entries()
  # For riscv64, filtering is more complex; `install_entries` takes care of it.
  if not args.local_dist_riscv64:
    if args.skip_apex:
      entries = [entry for entry in entries if entry.type != "apex"]
    if args.skip_module_sdk:
      entries = [entry for entry in entries if entry.type != "module_sdk"]
    if args.skip_impl_lib:
      entries = [entry for entry in entries if entry.type != "impl_lib"]
    if not entries:
      sys.exit("All prebuilts skipped - nothing to do.")

  install_paths = [entry.install_path for entry in entries]
  install_paths_per_root = install_paths_per_git_root(
      GIT_PROJECT_ROOTS, install_paths)

  git_branch_name = PREBUILT_DESCR.lower().replace(" ", "-") + "-update"
  if args.aosp_main_build:
    git_branch_name += "-" + args.aosp_main_build

  if not args.skip_cls:
    git_paths = list(install_paths_per_root.keys())
    start_branch(git_branch_name, git_paths)

  if not args.local_dist_riscv64:
    for git_root, subpaths in install_paths_per_root.items():
      remove_files(git_root, subpaths, not args.skip_cls)

  all_installed_sources = {}

  tmp_dir_obj = None
  tmp_dir = args.tmp_dir
  if not args.tmp_dir:
    tmp_dir_obj = tempfile.TemporaryDirectory()
    tmp_dir = tmp_dir_obj.name
  tmp_dir = os.path.abspath(tmp_dir)
  try:
    for entry in entries:
      installed_source = install_entry(
          tmp_dir, args.local_dist, build_numbers, entry)
      if installed_source:
        all_installed_sources[entry.install_path] = installed_source
  finally:
    if tmp_dir_obj:
      tmp_dir_obj.cleanup()

  if not args.skip_cls:
    for git_root, subpaths in install_paths_per_root.items():
      installed_sources = set(src for path, src in all_installed_sources.items()
                              if path.startswith(git_root + "/"))
      commit(git_root, PREBUILT_DESCR, sorted(list(installed_sources)),
             subpaths, args.bug)

    if args.upload:
      # Don't upload all projects in a single repo upload call, because that
      # makes it pop up an interactive editor.
      for git_root in install_paths_per_root:
        upload_branch(git_root, git_branch_name)


if __name__ == "__main__":
  main()
