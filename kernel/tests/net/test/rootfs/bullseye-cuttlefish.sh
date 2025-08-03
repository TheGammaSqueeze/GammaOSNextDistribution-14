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

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)

. $SCRIPT_DIR/bullseye-common.sh

setup_dynamic_networking "eth1" "br0"

update_apt_sources bullseye ""

setup_cuttlefish_user

setup_and_build_cuttlefish
setup_and_build_iptables

install_and_cleanup_cuttlefish
sed -i "s,^#\(bridge_interface=\),\1br0," /etc/default/cuttlefish-host-resources
install_and_cleanup_iptables

create_systemd_getty_symlinks ttyS0 hvc1

setup_grub "quiet net.ifnames=0 8250.nr_uarts=1"

apt-get purge -y vim-tiny
bullseye_cleanup
