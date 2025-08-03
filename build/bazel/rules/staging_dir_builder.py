#!/usr/bin/env python3
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

import argparse
import json
import os
import shutil
import subprocess
import sys
import tempfile

def build_staging_dir(staging_dir_path, file_mapping, command_argv, base_staging_dir=None, base_staging_dir_file_list=None):
    '''Create a staging dir with provided file mapping and apply the command in the dir.

    At least

    Args:
      staging_dir_path (str): path to the staging directory
      file_mapping (str:str dict): Mapping from paths in the staging directory to source paths
      command_argv (str list): the command to be executed, with the first arg as the executable
      base_staging_dir (optional str): The path to another staging directory to copy into this one before adding the files from the file_mapping
    '''

    if base_staging_dir:
        # Resolve the symlink because we want to use copytree with symlinks=True later
        while os.path.islink(base_staging_dir):
            base_staging_dir = os.path.normpath(os.path.join(os.path.dirname(base_staging_dir), os.readlink(base_staging_dir)))
        if base_staging_dir_file_list:
            with open(base_staging_dir_file_list) as f:
                for line in f:
                    line = line.strip()
                    if not line:
                        return
                    if line != os.path.normpath(line):
                        sys.exit(f"{line}: not normalized")
                    if line.startswith("../") or line.startswith('/'):
                        sys.exit(f"{line}: escapes staging directory by starting with ../ or /")
                    src = os.path.join(base_staging_dir, line)
                    dst = os.path.join(staging_dir_path, line)
                    if os.path.isdir(src):
                        os.makedirs(dst, exist_ok=True)
                    else:
                        os.makedirs(os.path.dirname(dst), exist_ok=True)
                        os.link(src, dst, follow_symlinks=False)
        else:
            shutil.copytree(base_staging_dir, staging_dir_path, symlinks=True, dirs_exist_ok=True)

    for path_in_staging_dir, path_in_bazel in file_mapping.items():
        path_in_staging_dir = os.path.join(staging_dir_path, path_in_staging_dir)

        # Because Bazel execution root is a symlink forest, all the input files are symlinks, these
        # include the dependency files declared in the BUILD files as well as the files declared
        # and created in the bzl files. For sandbox runs the former are two or more level symlinks and
        # latter are one level symlinks. For non-sandbox runs, the former are one level symlinks
        # and the latter are actual files. Here are some examples:
        #
        # Two level symlinks:
        # system/timezone/output_data/version/tz_version ->
        # /usr/local/google/home/...out/bazel/output_user_root/b1ed7e1e9af3ebbd1403e9cf794e4884/
        # execroot/__main__/system/timezone/output_data/version/tz_version ->
        # /usr/local/google/home/.../system/timezone/output_data/version/tz_version
        #
        # Three level symlinks:
        # bazel-out/android_x86_64-fastbuild-ST-4ecd5e98bfdd/bin/external/boringssl/libcrypto.so ->
        # /usr/local/google/home/yudiliu/android/aosp/master/out/bazel/output_user_root/b1ed7e1e9af3ebbd1403e9cf794e4884/
        # execroot/__main__/bazel-out/android_x86_64-fastbuild-ST-4ecd5e98bfdd/bin/external/boringssl/libcrypto.so ->
        # /usr/local/google/home/yudiliu/android/aosp/master/out/bazel/output_user_root/b1ed7e1e9af3ebbd1403e9cf794e4884/
        # execroot/__main__/bazel-out/android_x86_64-fastbuild-ST-4ecd5e98bfdd/bin/external/boringssl/
        # liblibcrypto_stripped.so ->
        # /usr/local/google/home/yudiliu/android/aosp/master/out/bazel/output_user_root/b1ed7e1e9af3ebbd1403e9cf794e4884/
        # execroot/__main__/bazel-out/android_x86_64-fastbuild-ST-4ecd5e98bfdd/bin/external/boringssl/
        # liblibcrypto_unstripped.so
        #
        # One level symlinks:
        # bazel-out/android_target-fastbuild/bin/system/timezone/apex/apex_manifest.pb ->
        # /usr/local/google/home/.../out/bazel/output_user_root/b1ed7e1e9af3ebbd1403e9cf794e4884/
        # execroot/__main__/bazel-out/android_target-fastbuild/bin/system/timezone/apex/
        # apex_manifest.pb
        if os.path.islink(path_in_bazel):
            # Some of the symlinks are relative (start with ../). They're relative to the location
            # of the symlink, not to the cwd. So we have to join the directory of the symlink with
            # the symlink's target. If the symlink was absolute, os.path.join() will take it as-is
            # and ignore the first argument.
            path_in_bazel = os.path.abspath(os.path.join(os.path.dirname(path_in_bazel), os.readlink(path_in_bazel)))

            # For sandbox run these are the 2nd level symlinks and we need to resolve
            while os.path.islink(path_in_bazel) and 'execroot/__main__' in path_in_bazel:
                path_in_bazel = os.path.abspath(os.path.join(os.path.dirname(path_in_bazel), os.readlink(path_in_bazel)))

        if os.path.exists(path_in_staging_dir):
            sys.exit("error: " + path_in_staging_dir + " already exists because of the base_staging_dir")

        os.makedirs(os.path.dirname(path_in_staging_dir), exist_ok=True)
        # shutil.copy copies the file data and the file's permission mode
        # file's permission mode is helpful for tools, such as build/soong/scripts/gen_ndk_usedby_apex.sh,
        # that rely on the permission mode of the artifacts
        shutil.copy(path_in_bazel, path_in_staging_dir, follow_symlinks=False)

    result = subprocess.run(command_argv)

    sys.exit(result.returncode)

def main():
    '''Build a staging directory, and then call a custom command.

    The first argument to this script must be the path to a file containing a json
    dictionary mapping paths in the staging directory to paths to files that should
    be copied there. The rest of the arguments will be run as a separate command.

    Example:
    staging_dir_builder options.json path/to/apexer --various-apexer-flags path/to/out.apex.unsigned
    '''
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "options_path",
        help="""Path to a JSON file containing options for staging_dir_builder. The top level object must be a dict. The keys for the dict can be:
file_mapping: A dict mapping from <staging dir path> to <bazel input path>
staging_dir_path: A string path to use as the output staging dir. If not given, a temporary directory will be used, and STAGING_DIR_PLACEHOLDER in the command will be substituted with the path to the temporary directory.
base_staging_dir: A string path to a staging dir to copy to the output staging dir before any of the files from the file_mapping are copied. Used to import the Make staging directory.""",
    )
    args, command_argv = parser.parse_known_args()

    try:
        with open(args.options_path, 'r') as f:
            options = json.load(f)
    except OSError as e:
        sys.exit(str(e))
    except json.JSONDecodeError as e:
        sys.exit(options_path + ": JSON decode error: " + str(e))

    # Validate and clean the options by making sure it's a dict[str, ?] with only these keys:
    #   - file_mapping, which must be a dict[str, str]. Then we:
    #     - Normalize the paths in the staging dir and stripping leading /s
    #     - Make sure there are no duplicate paths in the staging dir
    #     - Make sure no paths use .. to break out of the staging dir
    if not isinstance(options, dict):
        sys.exit(options_path + ": expected a JSON dict[str, ?]")
    for k in options.keys():
        if k not in ["file_mapping", "base_staging_dir", "base_staging_dir_file_list", "staging_dir_path"]:
            sys.exit("Unknown option: " + str(k))
    if not isinstance(options.get("file_mapping", {}), dict):
        sys.exit(options_path + ": file_mapping: expected a JSON dict[str, str]")

    file_mapping = {}
    for path_in_staging_dir, path_in_bazel in options.get("file_mapping", {}).items():
        if not isinstance(path_in_staging_dir, str) or not isinstance(path_in_bazel, str):
            sys.exit(options_path + ": expected a JSON dict[str, str]")
        path_in_staging_dir = os.path.normpath(path_in_staging_dir).lstrip('/')
        if path_in_staging_dir in file_mapping:
            sys.exit("Staging dir path repeated twice: " + path_in_staging_dir)
        if path_in_staging_dir.startswith('../'):
            sys.exit("Path attempts to break out of staging dir: " + path_in_staging_dir)
        file_mapping[path_in_staging_dir] = path_in_bazel

    if options.get("staging_dir_path"):
        build_staging_dir(options.get("staging_dir_path"), file_mapping, command_argv, options.get("base_staging_dir"), options.get("base_staging_dir_file_list"))
    else:
        if "STAGING_DIR_PLACEHOLDER" not in command_argv:
            sys.exit('At least one argument must be "STAGING_DIR_PLACEHOLDER"')
        with tempfile.TemporaryDirectory() as staging_dir_path:
            for i in range(len(command_argv)):
                if command_argv[i] == "STAGING_DIR_PLACEHOLDER":
                    command_argv[i] = staging_dir_path
            build_staging_dir(staging_dir_path, file_mapping, command_argv, options.get("base_staging_dir"), options.get("base_staging_dir_file_list"))

if __name__ == '__main__':
    main()
