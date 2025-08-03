#!/bin/bash -e

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

# This contains some utilities used by b
# They were moved separately to facilitate testing
source $(cd $(dirname $BASH_SOURCE) &> /dev/null && pwd)/../make/shell_utils.sh

function get_profile_out_dir {
   require_top
   if [[ -z ${OUT_DIR+x} ]]; then
      PROFILE_OUT=$TOP/out
   else
      PROFILE_OUT=$OUT_DIR
   fi

   echo $PROFILE_OUT
}

function is_command {
   arg=$1
   BAZEL_COMMAND_LIST="analyze-profile aquery build canonicalize-flags clean config coverage cquery dump fetch help info license mobile-install mod print_action query run shutdown sync test version"
   if echo "$BAZEL_COMMAND_LIST" | "grep" -ws -e "$arg"; then
      true
   else
      false
   fi
}

function formulate_b_args {
   # Always run with the bp2build configuration, which sets Bazel's package path to
   # the synthetic workspace.
   # Add the --config=bp2build after the first argument. That should be the bazel command
   # (build, test, run, etc) If the --config was added at the end, it wouldn't work
   # with commands like:  b run //foo -- --args-for-foo
   # This function will create a UUID for BES purposes if not already set to the ENV var
   # "BES_UUID". Likewise, the bazel profile file will be written to the dir set as "PROFILE_OUT"
   # or default to $TOP/out or out if not specified.

   # Represent the args as an array, not a string.
   bazel_args_with_config=()
   command_set=0
   PROFILE_OUT=${PROFILE_OUT:-`get_profile_out_dir`}

   for arg in $@; do
       bazel_args_with_config+=("$arg ")
       arg_is_command=$(is_command $arg)
       # Add the default configs after the first argument, which should be the command, e.g. build/test
       if [[ $arg_is_command && $command_set == 0 ]]; then
           bazel_args_with_config+=("--profile=$PROFILE_OUT/bazel_metrics-profile --config=bp2build ")
           command_set=1
       fi
   done
   echo ${bazel_args_with_config[@]}
}
