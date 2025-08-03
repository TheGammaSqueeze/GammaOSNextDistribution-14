#!/bin/bash
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
#

set -e
set -u

trap "echo 1 >${exitcode}" ERR

# So we have a rw location to extract kmod
mount -t tmpfs tmpfs /tmp

# Extract kmod utility to /tmp
dpkg-deb -x /var/cache/apt/archives/kmod*.deb /tmp
ln -s /tmp/bin/kmod /tmp/insmod

# Load just enough to get the rootfs from virtio_blk
module_dir=/lib/modules/$(uname -r)/kernel
# virtio_pci_modern_dev.ko for 5.12-5.19 kernel
if [ -e "${module_dir}/drivers/virtio/virtio_pci_modern_dev.ko" ]; then
    /tmp/insmod ${module_dir}/drivers/virtio/virtio_pci_modern_dev.ko || sh
fi
# virtio_pci_legacy_dev.ko for 6.0+ kernel
if [ -e "${module_dir}/drivers/virtio/virtio_pci_legacy_dev.ko" ]; then
    /tmp/insmod ${module_dir}/drivers/virtio/virtio_pci_legacy_dev.ko
fi
/tmp/insmod ${module_dir}/drivers/virtio/virtio_pci.ko
/tmp/insmod ${module_dir}/drivers/block/virtio_blk.ko
/tmp/insmod ${module_dir}/drivers/char/hw_random/virtio-rng.ko

# Mount devtmpfs so we can see /dev/vda
mount -t devtmpfs devtmpfs /dev

# Mount /dev/vda over the top of /root
rm -f /dev/ram0
mount -n -t proc proc /proc
mount LABEL=ROOT /root
if [[ "${install_grub}" = "1" ]]; then
  mkdir -p /root/boot/efi
  mount LABEL=SYSTEM /root/boot/efi
fi
umount /proc

# Switch to the new root and start stage 2
mount -n --move /dev /root/dev
mount -n --move /tmp /root/tmp
mount -n -t proc none /root/proc
mount -n -t sysfs none /root/sys
mount -n -t tmpfs tmpfs /root/run
pivot_root /root /root/host
exec chroot / /root/stage2.sh ${exitcode} </dev/console >/dev/console 2>&1
