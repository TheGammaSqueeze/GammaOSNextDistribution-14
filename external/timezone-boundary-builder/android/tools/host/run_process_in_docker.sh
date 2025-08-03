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
CONTAINER_TOOLS_DIR=$(realpath $HOST_TOOLS_DIR/../container)
TZBB_ROOT=$(realpath $HOST_TOOLS_DIR/../../..)

DOCKER_USERNAME=$(id -un)
DOCKER_UID=$(id -u)
DOCKER_GID=$(id -g)

echo "This may need your sudo password in order to access docker:"
set -x
sudo docker build --build-arg userid=$DOCKER_UID --build-arg groupid=$DOCKER_GID --build-arg username=$DOCKER_USERNAME --build-arg tzbbroot=$TZBB_ROOT -t android-tzbb .
sudo docker run -it --rm -v $TZBB_ROOT:/timezone-boundary-builder android-tzbb $*
set +x

