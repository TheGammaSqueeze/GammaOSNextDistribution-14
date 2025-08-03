#!/bin/bash
#
# Copyright (C) 2022 The Android Open Source Project
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

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)

. $SCRIPT_DIR/bullseye-common.sh

arch=$(uname -m)
nvidia_arch=${arch}
[ "${arch}" = "x86_64" ] && arch=amd64
[ "${arch}" = "aarch64" ] && arch=arm64

# Workaround for unnecessary firmware warning on ampere/gigabyte
mkdir -p /lib/firmware
touch /lib/firmware/ast_dp501_fw.bin

setup_dynamic_networking "eth0" ""

# NVIDIA driver needs dkms which requires /dev/fd
if [ ! -d /dev/fd ]; then
 ln -s /proc/self/fd /dev/fd
fi

update_apt_sources "bullseye bullseye-backports" "non-free"

setup_cuttlefish_user

# Install JRE
apt-get install -y openjdk-17-jre

# Ubuntu compatibility
cat >>/etc/skel/.profile << EOF
PATH="/usr/sbin:\$PATH"
EOF

# Get kernel and QEMU from backports
for package in linux-image-${arch} qemu-system-arm qemu-system-x86; do
  apt-get install -y -t bullseye-backports ${package}
done

# Install AOSP customized kernel package and extra Debian packages
if [ -e /root/extradeb.tar.gz ]; then
    tar --one-top-level=/root/extra_deb -zxvf /root/extradeb.tar.gz
    apt -o APT::Color=0 -o DPkgPM::Progress-Fancy=0 install /root/extra_deb/*.deb
    rm -rf /root/extra_deb
    rm -f /root/extradeb.tar.gz
fi

# Install firmware package for AMD graphics
apt-get install -y firmware-amd-graphics

get_installed_packages >/root/originally-installed

# Using "Depends:" is more reliable than "Version:", because it works for
# backported ("bpo") kernels as well. NOTE: "Package" can be used instead
# if we don't install the metapackage ("linux-image-${arch}") but a
# specific version in the future
kmodver=$(dpkg -s linux-image-${arch} | grep ^Depends: | \
          cut -d: -f2 | cut -d" " -f2 | sed 's/linux-image-//')

# Install headers from backports, to match the linux-image (removed below)
apt-get install -y -t bullseye-backports $(echo linux-headers-${kmodver})

# Dependencies for nvidia-installer (removed below)
apt-get install -y dkms libglvnd-dev libc6-dev pkg-config

nvidia_version=525.60.13
wget -q https://us.download.nvidia.com/tesla/${nvidia_version}/NVIDIA-Linux-${nvidia_arch}-${nvidia_version}.run
chmod a+x NVIDIA-Linux-${nvidia_arch}-${nvidia_version}.run
./NVIDIA-Linux-${nvidia_arch}-${nvidia_version}.run -x
cd NVIDIA-Linux-${nvidia_arch}-${nvidia_version}
if [[ "${nvidia_arch}" = "x86_64" ]]; then
  installer_flags="--no-install-compat32-libs"
else
  installer_flags=""
fi
./nvidia-installer ${installer_flags} --silent --no-backup --no-wine-files \
                   --install-libglvnd --dkms -k "${kmodver}"
cd -
rm -rf NVIDIA-Linux-${nvidia_arch}-${nvidia_version}*

get_installed_packages >/root/installed

remove_installed_packages /root/originally-installed /root/installed

setup_and_build_cuttlefish

install_and_cleanup_cuttlefish

# ttyAMA0 for ampere/gigabyte
# ttyS0 for GCE t2a
create_systemd_getty_symlinks ttyAMA0 ttyS0

setup_grub "net.ifnames=0 console=ttyAMA0 8250.nr_uarts=1 console=ttyS0 loglevel=4 amdgpu.runpm=0 amdgpu.dc=0"

# Set up NTP using Google time servers and switch to UTC for uniformity
# NOTE: Installing ntp removes systemd-timesyncd
apt-get install -y ntp
sed -i -e 's,^\(pool .*debian.*\)$,# \1,' /etc/ntp.conf
cat >>/etc/ntp.conf <<EOF
pool time1.google.com iburst
pool time2.google.com iburst
pool time3.google.com iburst
pool time4.google.com iburst
# time.google.com as backup
pool time.google.com iburst
EOF
timedatectl set-timezone UTC

# Switch to NetworkManager. To disrupt the bootstrapping the least, do this
# right at the end..
rm -f /etc/network/interfaces.d/eth0.conf
apt-get install -y network-manager
apt-get purge -y vim-tiny
bullseye_cleanup
