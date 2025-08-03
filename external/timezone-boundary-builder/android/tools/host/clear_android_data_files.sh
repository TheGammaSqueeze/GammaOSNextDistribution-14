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

# Fail fast on any error.
set -e

HOST_TOOLS_DIR=$(realpath $(dirname $0))
source ${HOST_TOOLS_DIR}/common.sh

DIRS=(\
  ${HOST_INPUTS_DIR}\
  ${HOST_DOWNLOADS_DIR}\
  ${HOST_DIST_DIR}\
)

for DIR in ${DIRS[@]}; do
  echo Deleting content of ${DIR}
  rm -f ${DIR}/*
  # Just in case
  mkdir -p ${DIR}
done

