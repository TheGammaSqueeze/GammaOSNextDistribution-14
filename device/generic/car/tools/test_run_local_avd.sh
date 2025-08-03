#!/bin/bash

# Copyright (C) 2020 The Android Open Source Project
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

echo Setup a clean local AVD with the Img in $HOME/Downloads/x86_64 and run

MY_NAME=$0
MY_FILENAME=${MY_NAME##*/}  # = "test_run_local_avd.sh"
MY_DIR=${MY_NAME%/$MY_FILENAME}  # = "/path/to"
echo running from $MY_DIR

WORKDIR="$HOME/workdir"
echo Setup a clean $WORKDIR
rm -rf $WORKDIR
mkdir $WORKDIR

ABI="x86_64"
AVD_IMAGE_DIR="$HOME/Downloads/$ABI"

echo link $AVD_IMAGE_DIR to "$WORKDIR/$ABI"
ln -s $AVD_IMAGE_DIR "$WORKDIR/$ABI"

WORKDIR=$WORKDIR \
    ABI=$ABI \
    AVD_IMAGE_DIR=$AVD_IMAGE_DIR \
    $MY_DIR/run_local_avd.sh -verbose -show-kernel -debug init $@
