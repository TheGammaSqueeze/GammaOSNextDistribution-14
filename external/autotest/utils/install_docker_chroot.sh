#!/bin/bash

# Copyright 2022 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# This script install pip2, pip3, docker library for python2 and python3.


function install_docker_library {
    mkdir /tmp/docker_library_bootstrap

    # Install pip2
    wget -O /tmp/docker_library_bootstrap/get_pip2.py https://bootstrap.pypa.io/pip/2.7/get-pip.py
    python2 /tmp/docker_library_bootstrap/get_pip2.py

    # Install pip3
    wget -O /tmp/docker_library_bootstrap/get_pip3.py https://bootstrap.pypa.io/pip/3.6/get-pip.py
    python3 /tmp/docker_library_bootstrap/get_pip3.py

    # Install Docker Python SDK
   pip2 install docker==4.4.4 --upgrade
   pip3 install docker==4.4.4 --upgrade

    # Cleaning up
    rm -rf /tmp/docker_library_bootstrap
}

cat << EOF
###############################################################################
IMPORTANT: Please read below information
###############################################################################
The script will install the following into your system:
    - pip2
    - pip3
    - python2/3 Docker SDK
Please run the script using sudo within chroot or container as this might
permanently changed your environment.

DO NOT RUN THIS ON YOUR WORKSTATION.
###############################################################################
EOF

while true; do
    read -p "Do you wish to proceed? [y/N]: " yn
    case "$yn" in
        [Yy]* ) install_docker_library; break;;
        [Nn]* ) exit;;
        * ) echo "Please answer yes or no.";;
    esac
done