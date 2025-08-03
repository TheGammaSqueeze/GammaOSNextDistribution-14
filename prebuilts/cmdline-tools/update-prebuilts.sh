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

if [ -z $1 ]; then
    echo "usage: $0 <build number>"
    exit 1
fi

readonly BUILD_NUMBER=$1

cd "$(dirname $0)"

if ! git diff HEAD --quiet; then
    echo "must be run with a clean prebuilts/build-tools project"
    exit 1
fi

readonly tmpdir=$(mktemp -d)

function finish {
    if [ ! -z "${tmpdir}" ]; then
        rm -rf "${tmpdir}"
    fi
}
trap finish EXIT

function fetch_artifact() {
    /google/data/ro/projects/android/fetch_artifact --bid ${BUILD_NUMBER} --target $1 "$2" "$3"
}

fetch_artifact studio-linux artifacts/commandlinetools_linux.zip "${tmpdir}/commandlinetools_linux.zip"
fetch_artifact studio-linux artifacts/lint-tests.jar "${tmpdir}/lint-tests.jar"
fetch_artifact studio-linux manifest_${BUILD_NUMBER}.xml "${tmpdir}/manifest.xml"

function unzip_to() {
    rm -rf "$1"
    mkdir "$1"
    unzip -qDD -d "$1" "$2"
}

rm -rf tools
unzip -q "${tmpdir}/commandlinetools_linux.zip"
mv cmdline-tools tools

cp -f "${tmpdir}/lint-tests.jar" lint-tests.jar
cp -f "${tmpdir}/manifest.xml" manifest.xml

patch -p1 < patches/bin-lint.patch
patch -p1 < patches/bin-resourceshrinker.patch

git add tools lint-tests.jar manifest.xml
git commit -m "Update cmdline-tools to ab/${BUILD_NUMBER}

https://ci.android.com/builds/submitted/${BUILD_NUMBER}/studio-linux/latest

Test: treehugger"
