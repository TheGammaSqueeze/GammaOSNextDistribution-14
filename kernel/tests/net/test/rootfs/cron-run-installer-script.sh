#!/bin/bash

# Wait for Login Prompt fully active.
# Otherwise the output are mixed together.
while true; do
    systemctl is-active --quiet multi-user.target
    if [ $? -eq 0 ]; then
	break
    fi
    sleep 2
done
sleep 10

# Parsing /proc/cmdline and export all the variables
PARAMS=""
if [ -e /proc/cmdline ]; then
    PARAMS=$(cat /proc/cmdline)
fi

for i in ${PARAMS}
do
    export ${i}
done

# Log output for qemu serial.
LOG_FILE=/dev/null
if [ x"${console}" != x"" ]; then
    if [ -e /dev/${console} ]; then
	LOG_FILE=/dev/${console}
    fi
fi

# Run the script
cd /
if [ x"${installer_script}" = x"" ]; then
    exit
fi
if [ ! -x "${installer_script}" ]; then
    exit
fi

${installer_script} > "${LOG_FILE}" 2>&1

# shutdown the machine.
shutdown -h 1
