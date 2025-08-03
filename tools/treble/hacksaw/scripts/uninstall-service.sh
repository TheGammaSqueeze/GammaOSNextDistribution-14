#!/bin/bash
set -e
set -v

SCRIPT_DIR=$(dirname $(readlink --canonicalize $0))

# This script must be run with sudo

systemctl stop hacksaw.socket
systemctl stop hacksaw.service
systemctl disable hacksaw.service
systemctl disable hacksaw.socket
rm /lib/systemd/system/hacksaw.service
rm /lib/systemd/system/hacksaw.socket
deluser ${SUDO_USER} hacksaw
groupdel hacksaw
