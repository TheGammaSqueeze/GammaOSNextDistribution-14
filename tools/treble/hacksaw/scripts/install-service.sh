#!/bin/bash
set -e
set -v

SCRIPT_DIR=$(dirname $(readlink --canonicalize $0))

# This script must be run with sudo

# The hacksaw group is used to manage access to the
# hacksawd service
groupadd hacksaw
usermod -a -G hacksaw ${SUDO_USER}
cp ${SCRIPT_DIR}/hacksaw.service /lib/systemd/system/hacksaw.service
cp ${SCRIPT_DIR}/hacksaw.socket /lib/systemd/system/hacksaw.socket
chmod 755 /lib/systemd/system/hacksaw.*
systemctl enable hacksaw.service
systemctl enable hacksaw.socket
# hacksaw.socket starts hacksaw.service on-demand
# so hacksaw.service does not need to be explicitly
# started
systemctl start hacksaw.socket
echo "The installation scripts creates a new 'hacksaw' group and adds you to it."
echo "You will need to log out and log back in for the group changes to take effect."
