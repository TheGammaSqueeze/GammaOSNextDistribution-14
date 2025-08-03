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

set -eu

common_device="$1"
gpu_common="$2"
serial="$3"
net="$4"
block="$5"
vhost_user="$6"
vhost_vsock="$7"
# NOTE: We can't require all of the files to exist because aarch64 doesn't have
# all of them.
if ! [[ -f $common_device ]] || ! [[ -f $gpu_common ]] || ! [[ -f $serial ]] || ! [[ -f $net ]]; then
  echo "usage: $0 /path/to/common_device.policy /path/to/gpu_common.policy /path/to/serial.policy/ /path/to/net.policy /path/to/block.policy /path/to/vhost_user.policy <input.policy >output.policy"
  exit 1
fi

while IFS= read -r line
do
  if echo "$line" | egrep "@include[[:space:]]+/usr/share/policy/crosvm/common_device.policy" > /dev/null; then
    cat $common_device
    continue
  elif echo "$line" | egrep "@include[[:space:]]+/usr/share/policy/crosvm/gpu_common.policy" > /dev/null; then
    cat $gpu_common
    continue
  elif echo "$line" | egrep "@include[[:space:]]+/usr/share/policy/crosvm/serial.policy" > /dev/null; then
    cat $serial
    continue
  elif echo "$line" | egrep "@include[[:space:]]+/usr/share/policy/crosvm/net.policy" > /dev/null; then
    cat $net
    continue
  elif echo "$line" | egrep "@include[[:space:]]+/usr/share/policy/crosvm/block.policy" > /dev/null; then
    cat $block
    continue
  elif echo "$line" | egrep "@include[[:space:]]+/usr/share/policy/crosvm/vhost_user.policy" > /dev/null; then
    cat $vhost_user
    continue
  elif echo "$line" | egrep "@include[[:space:]]+/usr/share/policy/crosvm/vhost_vsock.policy" > /dev/null; then
    cat $vhost_vsock
    continue
  elif echo "$line" | egrep "@include" > /dev/null; then
    echo "ERROR: Unsupported include statement $line" >&2
    exit 1
  fi
  echo $line
done
