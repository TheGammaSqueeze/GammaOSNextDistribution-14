#!/bin/bash
#
# Copyright (C) 2017 The Android Open Source Project
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

. "$(dirname $0)/buildbot-utils.sh"

# Testing on a Linux VM requires special cleanup.
if [[ -n "$ART_TEST_ON_VM" ]]; then
  [[ -d "$ART_TEST_VM_DIR" ]] || { msgfatal "no VM found in $ART_TEST_VM_DIR"; }
  $ART_SSH_CMD "true" || { msgfatal "VM not responding (tried \"$ART_SSH_CMD true\""; }
  $ART_SSH_CMD "
    sudo umount $ART_TEST_CHROOT/proc
    sudo umount $ART_TEST_CHROOT/sys
    sudo umount $ART_TEST_CHROOT/dev
    sudo umount $ART_TEST_CHROOT/bin
    sudo umount $ART_TEST_CHROOT/lib
    sudo umount $ART_TEST_CHROOT/usr/lib
    sudo umount $ART_TEST_CHROOT/usr/share/gdb
    rm -rf $ART_TEST_CHROOT
  "
  exit 0
fi

# Regular Android device. Setup as root, as device cleanup requires it.
adb root
adb wait-for-device

if [[ -n "$ART_TEST_CHROOT" ]]; then
  # Check that ART_TEST_CHROOT is correctly defined.
  if [[ "x$ART_TEST_CHROOT" != x/* ]]; then
    echo "$ART_TEST_CHROOT is not an absolute path"
    exit 1
  fi

  if adb shell test -d "$ART_TEST_CHROOT"; then
    msginfo "Remove entire /linkerconfig directory from chroot directory"
    adb shell rm -rf "$ART_TEST_CHROOT/linkerconfig"

    msginfo "Remove entire /system directory from chroot directory"
    adb shell rm -rf "$ART_TEST_CHROOT/system"

    msginfo "Remove entire /data directory from chroot directory"
    adb shell rm -rf "$ART_TEST_CHROOT/data"

    msginfo "Remove entire chroot directory"
    adb shell rmdir "$ART_TEST_CHROOT" || adb shell ls -la "$ART_TEST_CHROOT"
  fi
else
  adb shell rm -rf \
    /data/local/tmp /data/art-test /data/nativetest /data/nativetest64 '/data/misc/trace/*'
fi
