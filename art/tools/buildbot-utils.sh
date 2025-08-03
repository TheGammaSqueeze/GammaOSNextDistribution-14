#! /bin/bash
#
# Copyright (C) 2021 The Android Open Source Project
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

# Utilities for buildbot. This script is sourced by other buildbot-*.sh scripts.

if [ -t 1 ]; then
  # Color sequences if terminal is a tty.

  red='\033[0;31m'
  green='\033[0;32m'
  yellow='\033[0;33m'
  blue='\033[0;34m'
  magenta='\033[0;35m'
  cyan='\033[0;36m'

  boldred='\033[1;31m'
  boldgreen='\033[1;32m'
  boldyellow='\033[1;33m'
  boldblue='\033[1;34m'
  boldmagenta='\033[1;95m'
  boldcyan='\033[1;36m'

  nc='\033[0m'
fi

function msginfo() {
  local heading="$1"
  shift
  local message="$*"
  echo -e "${green}${heading}${nc} ${message}"
}

function msgwarning() {
  local message="$*"
  echo -e "${boldmagenta}Warning: ${nc}${message}"
}

function msgerror() {
  local message="$*"
  echo -e "${boldred}Error: ${nc}${message}"
}

function msgfatal() {
  local message="$*"
  echo -e "${boldred}Fatal: ${nc}${message}"
  exit 1
}

function msgnote() {
  local message="$*"
  echo -e "${boldcyan}Note: ${nc}${message}"
}

export TARGET_ARCH=$(build/soong/soong_ui.bash --dumpvar-mode TARGET_ARCH)

# Do some checks and prepare environment for tests that run on Linux (not on Android).
if [[ -n "$ART_TEST_ON_VM" ]]; then
  if [[ -z $ANDROID_BUILD_TOP ]]; then
    msgfatal "ANDROID_BUILD_TOP is not set"
  elif [[ -z "$ART_TEST_SSH_USER" ]]; then
    msgfatal "ART_TEST_SSH_USER not set"
  elif [[ -z "$ART_TEST_SSH_HOST" ]]; then
    msgfatal "ART_TEST_SSH_HOST not set"
  elif [[ -z "$ART_TEST_SSH_PORT" ]]; then
    msgfatal "ART_TEST_SSH_PORT not set"
  fi

  export ART_TEST_CHROOT_BASENAME="art-test-chroot"
  export ART_TEST_CHROOT="/home/$ART_TEST_SSH_USER/$ART_TEST_CHROOT_BASENAME"
  export ART_CHROOT_CMD="unshare --user --map-root-user chroot $ART_TEST_CHROOT_BASENAME"
  export ART_SSH_CMD="ssh -q -i ~/.ssh/ubuntu -p $ART_TEST_SSH_PORT -o StrictHostKeyChecking=no $ART_TEST_SSH_USER@$ART_TEST_SSH_HOST"
  export ART_SCP_CMD="scp -i ~/.ssh/ubuntu -o StrictHostKeyChecking=no -P $ART_TEST_SSH_PORT -p -r"
  export ART_RSYNC_CMD="rsync -az"
  export RSYNC_RSH="ssh -i ~/.ssh/ubuntu -p $ART_TEST_SSH_PORT -o StrictHostKeyChecking=no" # don't prefix with "ART_", rsync expects this name

  if [[ "$TARGET_ARCH" =~ ^(arm64|riscv64)$ ]]; then
    export ART_TEST_VM_IMG="ubuntu-23.10-server-cloudimg-$TARGET_ARCH.img"
    export ART_TEST_VM_DIR="$ANDROID_BUILD_TOP/vm/$TARGET_ARCH"
    export ART_TEST_VM="$ART_TEST_VM_DIR/$ART_TEST_VM_IMG"
  else
    msgfatal "unexpected TARGET_ARCH=$TARGET_ARCH; expected one of {arm64,riscv64}"
  fi
fi
