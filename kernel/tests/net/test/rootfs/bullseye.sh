#!/bin/bash
#
# Copyright (C) 2018 The Android Open Source Project
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

setup_static_networking

update_apt_sources bullseye ""

# Disable the root password
passwd -d root

get_installed_packages >/root/originally-installed
setup_and_build_iptables
get_installed_packages >/root/installed
remove_installed_packages /root/originally-installed /root/installed
install_and_cleanup_iptables

create_systemd_getty_symlinks ttyS0

bullseye_cleanup
