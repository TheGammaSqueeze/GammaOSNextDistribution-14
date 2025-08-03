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

trap "echo 2 >${exitcode}" ERR

# Remove the old ramdisk root; we don't need it any more
umount -l /host

# Complete the debootstrap process
/debootstrap/debootstrap --second-stage

# We are done with apt; clean up apt and debootstrap intermediaries
apt-get clean
rm -rf /debootstrap /var/lib/apt/lists/*

# Read-only root breaks booting via init
cat >/etc/fstab << EOF
LABEL=ROOT   /             ext4  defaults,discard 0 1
tmpfs        /tmp          tmpfs defaults         0 0
tmpfs        /var/log      tmpfs defaults         0 0
tmpfs        /var/tmp      tmpfs defaults         0 0
EOF

# If we're installing grub, add the EFI partition
if [[ "${install_grub}" = "1" ]]; then
  cat >>/etc/fstab << EOF
LABEL=SYSTEM /boot/efi     vfat  umask=0077       0 1
EOF
fi

# systemd will attempt to re-create this symlink if it does not exist,
# which fails if it is booting from a read-only root filesystem (which
# is normally the case). The syslink must be relative, not absolute,
# and it must point to /proc/self/mounts, not /proc/mounts.
ln -sf ../proc/self/mounts /etc/mtab

# Set up the virtual device image hostname
echo "127.0.1.1       debian" >>/etc/hosts
echo debian >/etc/hostname

# Support chroot jailing with minijail
cat >/etc/sysctl.d/80-nsjail.conf <<EOF
kernel.unprivileged_userns_clone=1
EOF
mkdir -p /var/empty

# Clean up any other junk created by the imaging process
rm -rf /root/stage1.sh /root/stage2.sh /root/lib /tmp/*
find /var/log -type f -exec rm -f '{}' ';'
find /var/tmp -type f -exec rm -f '{}' ';'

# Create an empty initramfs to be combined with modules later
sed -i -e 's,^MODULES=dep,MODULES=most,' \
       -e 's,^COMPRESS=gzip,COMPRESS=lz4,' \
       /etc/initramfs-tools/initramfs.conf
depmod -a $(uname -r)
update-initramfs -c -k $(uname -r)
dd if=/boot/initrd.img-$(uname -r) of=/dev/vdb conv=fsync

echo 0 >"${exitcode}"
sync && poweroff -f
