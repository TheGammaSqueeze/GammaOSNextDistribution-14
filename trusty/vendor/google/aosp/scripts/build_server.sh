#!/bin/bash
#
# Copyright (C) 2018 The Android Open Source Project
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
#
# This build script to be used by the build server.
# It is supposed to be executed from trusty root directory
# end expects the following environment variables:
#
# BUILD_ROOT   - the root of intermediate build directory
# BUILD_OUTPUT - the location of build results directory
# BUILD_PROJECT - project(s) to to build (optional for now)
# BUILDID      - server build id
# BUILD_JOBS   - optinal max number of jobs

$(dirname ${BASH_SOURCE})/build.py \
	${BUILD_ROOT:+--build-root} ${BUILD_ROOT} \
	${BUILD_OUTPUT:+--archive} ${BUILD_OUTPUT} \
	${BUILDID:+--buildid} ${BUILDID} \
	${BUILD_JOBS:+--jobs} ${BUILD_JOBS} \
	${BUILD_PROJECT}
