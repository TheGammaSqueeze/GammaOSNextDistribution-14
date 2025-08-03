#!/bin/bash

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

HOST_TOOLS_DIR=$(realpath $(dirname $0))
source ${HOST_TOOLS_DIR}/common.sh

${HOST_TOOLS_DIR}/run_process_in_docker.sh /timezone-boundary-builder/android/tools/container/run_tzbb.sh \
  --downloads_dir ${CONTAINER_DOWNLOADS_DIR} \
  --dist_dir ${CONTAINER_DIST_DIR} \
  --skip_zip \
  --skip_shapefile \
  $*
