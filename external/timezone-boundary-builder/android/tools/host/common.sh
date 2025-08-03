# Copyright 2021 The Android Open Source Project
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

if [[ -z "${HOST_TOOLS_DIR}" ]]; then
  echo HOST_TOOLS_DIR not set
  exit 1
fi

# The android_ prefix is to get around the upstream project's .gitignore rules
# for dist / downloads. Android will want to commit them to version control to
# improve change tracking / repeatability and avoid unnecessary load on OSM
# servers.
INPUTS_DIR=android_inputs
DOWNLOADS_DIR=android_downloads
DIST_DIR=android_dist

CONTAINER_ANDROID_DIR=./android
CONTAINER_DOWNLOADS_DIR=${CONTAINER_ANDROID_DIR}/${DOWNLOADS_DIR}
CONTAINER_DIST_DIR=${CONTAINER_ANDROID_DIR}/${DIST_DIR}

HOST_ANDROID_DIR=${HOST_TOOLS_DIR}/../..
HOST_ANDROID_DIR=$(realpath ${HOST_ANDROID_DIR})
HOST_INPUTS_DIR=${HOST_ANDROID_DIR}/${INPUTS_DIR}
HOST_DOWNLOADS_DIR=${HOST_ANDROID_DIR}/${DOWNLOADS_DIR}
HOST_DIST_DIR=${HOST_ANDROID_DIR}/${DIST_DIR}

