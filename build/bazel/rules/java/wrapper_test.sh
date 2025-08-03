#!/bin/bash -eux

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

if [[ ! -d "build/bazel/ci" ]]; then
  echo "Please run this script from TOP".
  exit 1
fi

source "build/bazel/ci/build_with_bazel.sh"
source "build/bazel/ci/target_lists.sh"

function test_wrapper_providers() {
  for target in ${EXAMPLE_WRAPPER_TARGETS[@]}; do
    private_providers="$(build/bazel/bin/bazel ${STARTUP_FLAGS[@]} \
    cquery ${FLAGS[@]} --config=android "${target}_private" \
    --starlark:expr="sorted(providers(target).keys())" --output=starlark|uniq)"
    wrapper_providers="$(build/bazel/bin/bazel ${STARTUP_FLAGS[@]} \
    cquery ${FLAGS[@]} --config=android "${target}" \
    --starlark:expr="sorted(providers(target).keys())" --output=starlark|uniq)"
    if [[ -z "${private_providers}" ]]; then
      echo "Empty provider list, bazel invocation probably failed" >&2
      exit 1
    fi
    if [[ ! $(cmp -s <(echo "${private_providers}") <(echo "${wrapper_providers}")) ]]; then
      echo "${target} and ${target}_private should have the same providers. Diff:"
      diff <(echo "${private_providers}") <(echo "${wrapper_providers}")
    fi
  done
}
