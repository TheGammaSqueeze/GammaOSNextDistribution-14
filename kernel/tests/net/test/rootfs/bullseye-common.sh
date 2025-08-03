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

. $SCRIPT_DIR/common.sh

iptables=iptables-1.8.7
debian_iptables=1.8.7-1
cuttlefish=android-cuttlefish

setup_and_build_iptables() {
  get_installed_packages >/root/originally-installed

  # Install everything needed from bullseye to build iptables
  apt-get install -y \
    build-essential \
    autoconf \
    automake \
    bison \
    debhelper \
    devscripts \
    fakeroot \
    flex \
    libmnl-dev \
    libnetfilter-conntrack-dev \
    libnfnetlink-dev \
    libnftnl-dev \
    libtool

  # Construct the iptables source package to build
  mkdir -p /usr/src/$iptables

  cd /usr/src/$iptables
    # Download a specific revision of iptables from AOSP
    wget -qO - \
      https://android.googlesource.com/platform/external/iptables/+archive/master.tar.gz | \
      tar -zxf -
    # Download a compatible 'debian' overlay from Debian salsa
    # We don't want all of the sources, just the Debian modifications
    # NOTE: This will only work if Android always uses a version of iptables
    #       that exists for Debian as well.
    debian_iptables_dir=pkg-iptables-debian-$debian_iptables
    wget -qO - \
      https://salsa.debian.org/pkg-netfilter-team/pkg-iptables/-/archive/debian/$debian_iptables/$debian_iptables_dir.tar.gz | \
      tar --strip-components 1 -zxf - \
      $debian_iptables_dir/debian
  cd -

  cd /usr/src
    # Generate a source package to leave in the filesystem. This is done for
    # license compliance and build reproducibility.
    tar --exclude=debian -cf - $iptables | \
      xz -9 >$(echo $iptables | tr -s '-' '_').orig.tar.xz
  cd -

  cd /usr/src/$iptables
    # Build debian packages from the integrated iptables source
    dpkg-buildpackage -F -d -us -uc
  cd -

  get_installed_packages >/root/installed
  remove_installed_packages /root/originally-installed /root/installed
  apt-get clean
}

install_and_cleanup_iptables() {
  cd /usr/src
    # Find any packages generated, resolve to the debian package name, then
    # exclude any compat, header or symbol packages
    packages=$(find -maxdepth 1 -name '*.deb' | colrm 1 2 | cut -d'_' -f1 |
               grep -ve '-compat$\|-dbg$\|-dbgsym$\|-dev$' | xargs)
    # Install the patched iptables packages, and 'hold' then so
    # "apt-get dist-upgrade" doesn't replace them
    apt-get install --allow-downgrades -y -f \
      $(for package in $packages; do echo ./${package}_*.deb; done | xargs)
    for package in $packages; do
      echo "$package hold" | LANG=C dpkg --set-selections
    done
    update-alternatives --set iptables /usr/sbin/iptables-legacy

    # Tidy up the mess we left behind, leaving just the source tarballs
    rm -rf $iptables *.{buildinfo,changes,deb,dsc}
  cd -
}

setup_and_build_cuttlefish() {
  if [ "$(uname -m)" = "aarch64" ]; then
    apt-get install -y libc6:amd64
  fi

  get_installed_packages >/root/originally-installed

  # Install everything needed from bullseye to build android-cuttlefish
  apt-get install -y \
    cdbs \
    debhelper \
    devscripts \
    dpkg-dev \
    equivs \
    git

  # Fetch android-cuttlefish and build it
  git clone https://github.com/google/android-cuttlefish.git /usr/src/$cuttlefish
  for subdir in base frontend; do
    cd /usr/src/$cuttlefish/$subdir
      mk-build-deps --install --tool='apt-get -o Debug::pkgProblemResolver=yes --no-install-recommends --yes' debian/control
      dpkg-buildpackage -d -uc -us
    cd -
  done

  get_installed_packages >/root/installed
  remove_installed_packages /root/originally-installed /root/installed
  apt-get clean
}

install_and_cleanup_cuttlefish() {
  # Install and clean up cuttlefish host packages
  cd /usr/src/$cuttlefish
    apt-get install -y -f ./cuttlefish-base_*.deb
    apt-get install -y -f ./cuttlefish-user_*.deb
    apt-get install -y -f ./cuttlefish-integration_*.deb
    apt-get install -y -f ./cuttlefish-common_*.deb
  cd -
  rm -rf /usr/src/$cuttlefish
}

bullseye_cleanup() {
  # SELinux is supported by our kernels, but we don't install the policy files
  # which causes an error to be printed by systemd. Disable selinux.
  echo "SELINUX=disabled" >/etc/selinux/config

  # Switch binfmt misc over to a static mount, to avoid an autofs4 dependency
  systemctl mask proc-sys-fs-binfmt_misc.automount
  systemctl enable proc-sys-fs-binfmt_misc.mount

  # This package gets pulled in as a phantom dependency. Remove it
  apt-get purge -y gcc-9-base

  cleanup
}
