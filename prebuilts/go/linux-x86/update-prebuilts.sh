#!/bin/bash -e

# Copyright 2020 Google Inc. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
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

# A script to update the Go prebuilts from a build completed on Android CI.

set -eo pipefail

if [ -z $1 ]; then
  echo "usage: $0 <build number>"
  exit 1
fi

readonly BUILD_NUMBER=$1
readonly GERRIT_TOPIC="update-go-${BUILD_NUMBER}"

cd "$(dirname $0)"

readonly tmpdir=$(mktemp -d)

function finish {
  if [ ! -z "${tmpdir}" ]; then
    rm -rf "${tmpdir}"
  fi
}
trap finish EXIT

function fetch_artifact() {
  local target=$1; shift
  local artifact=$1; shift
  local output=$1; shift
  /google/data/ro/projects/android/fetch_artifact \
    --branch aosp-build-tools-release \
    --bid ${BUILD_NUMBER} \
    --target ${target}\
    "${artifact}" "${output}"
}

# Downloads the relevant go.zip and creates a CL with its contents.
function upload_cl() {
  # aosp-build-tools-release target
  local target=$1; shift

  # os directory name of the go prebuilts
  local arch=$1; shift

  zipfile="${tmpdir}/${arch}.zip"

  echo "Downloading ${arch} go.zip from ab/${BUILD_NUMBER}.."
  fetch_artifact "${target}" go.zip "${zipfile}"

  pushd "../../prebuilts/go/${arch}" > /dev/null

  echo "Uploading new ${arch} go.zip to Gerrit.."
  repo start "${GERRIT_TOPIC}"

  git rm -rf .
  unzip -q -d "$(pwd)" "${zipfile}"
  git add -A .
  git commit -m "Update ${arch} Go prebuilts from ab/${BUILD_NUMBER}

https://ci.android.com/builds/branches/aosp-build-tools-release/grid?head=${BUILD_NUMBER}&tail=${BUILD_NUMBER}

Update script: toolchain/go/update-prebuilts.sh

Test: Treehugger presubmit"
  repo upload --cbr -t -y .

  popd
}

# upload_cl <aosp-build-tools-release target> <prebuilts dir>
upload_cl linux linux-x86
upload_cl darwin_mac darwin-x86

echo "Uploaded CLs: https://android-review.googlesource.com/q/topic:%22${GERRIT_TOPIC}%22+status:open"
echo "Done."
