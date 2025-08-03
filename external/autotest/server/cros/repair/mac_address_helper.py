# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

import re

import common
from autotest_lib.client.common_lib import error


class MacAddressHelper():
    """Verify and update cached NIC mac address on servo.

    Servo_v4 plugged to the DUT and providing NIC for that. We caching mac
    address on servod side to better debugging.
    """

    # HUB and NIC VID/PID.
    # Values presented as the string of the hex without 0x to match
    # representation in sysfs (idVendor/idProduct).
    HUB_VID = '04b4'
    HUB_PID = '6502'
    NIC_VID = '0bda'
    NIC_PID = '8153'

    # Regex to check mac address format.
    # eg: f4:f5:e8:50:e9:45
    RE_MACADDR = re.compile('^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$')

    def is_supported(self, host):
        """Verify if setup is support cached NIC mac address on servo

        @param host:    CrosHost instance
        """
        if not host._servo_host.is_labstation():
            logging.info('Only servo_v4 has NIC; Skipping the action')
            return False
        if not host.servo.has_control('macaddr'):
            logging.info('"macaddr" control not supported;'
                         'Skipping the action')
            return False
        return True

    def update_if_needed(self, host):
        """Update the cached NIC mac address on servo

        The process will verify if NIC mac changes and update only if
        it required.

        @param host:    CrosHost instance
        """

        if not self.is_supported(host):
            return

        servo = host.servo
        # Path to the NIC has to be located in the HUB.
        # eg.
        # HUB: /sys/bus/usb/devices/1-1
        # NIC: /sys/bus/usb/devices/1-1.1
        hub_path = self._get_device_path(host, None, self.HUB_VID,
                                         self.HUB_PID)
        if not hub_path or hub_path == '.':
            raise Exception('The servo_v4 HUB not detected from DUT.')
        logging.debug('Path to the servo_v4 HUB device: %s', hub_path)
        nic_path = self._get_device_path(host, hub_path, self.NIC_VID,
                                         self.NIC_PID)
        if not nic_path or nic_path == '.':
            raise Exception('The servo_v4 NIC not detected in HUB folder.')
        logging.debug('Path to the servo_v4 NIC device: %s', nic_path)
        if hub_path == nic_path or not nic_path.startswith(hub_path):
            raise Exception('The servo_v4 NIC was detect out of servo_v4 HUB')

        macaddr = self._get_mac_address(host, nic_path)
        if not macaddr:
            raise Exception('Failed to extract mac address from host.')

        cached_mac = self._get_cached_mac_address(host)
        if not cached_mac or macaddr != cached_mac:
            try:
                servo.set('macaddr', macaddr)
                logging.info('Successfully updated the servo "macaddr"!')
            except error.TestFail as e:
                logging.debug('Fail to update macaddr value; %s', e)
                raise Exception('Fail to update the "macaddr" value!')
        else:
            logging.info('The servo "macaddr" doe not need update.')

    def _get_cached_mac_address(self, host):
        """Get NIC mac address from servo cache"""
        try:
            return host.servo.get('macaddr')
        except error.TestFail as e:
            logging.debug('(Non-critical) Fail to get macaddr: %s', e)
            return None

    def _get_mac_address(self, host, nic_path):
        """Get NIC mac address from host

        @param host:        CrosHost instance
        @param nic_path:    Path to network device on the host
        """
        cmd = r'find %s/ | grep /net/ | grep /address' % nic_path
        res = host.run(cmd,
                       timeout=30,
                       ignore_status=True,
                       ignore_timeout=True)
        if not res:
            logging.info('Timeout during retriving NIC address files.')
            return None
        addrs = res.stdout.splitlines()
        if not addrs or len(addrs) == 0:
            logging.info('No NIC address file found.')
            return None
        if len(addrs) > 1:
            logging.info('More than one NIC address file found.')
            return None
        logging.info('Found NIC address file: %s', addrs[0])
        cmd = r'cat %s' % addrs[0]
        res = host.run(cmd,
                       timeout=30,
                       ignore_status=True,
                       ignore_timeout=True)
        if not res:
            logging.info('Timeout during attemp read NIC address file: %s',
                         addrs[0])
            return None
        mac_addr = res.stdout.strip()
        if not self.RE_MACADDR.match(mac_addr):
            logging.info('incorrect format of the mac address: %s', mac_addr)
            return None
        logging.info('Servo_v4 NIC mac address from DUT side: %s', mac_addr)
        return mac_addr

    def _get_device_path(self, host, base_path, vid, pid):
        """Find a device by VID/PID under particular path.

        1) Get path to the unique idVendor file with VID
        2) Get path to the unique idProduct file with PID
        3) Get directions of both file and compare them

        @param host:        CrosHost instance
        @param base_path:   Path to the directory where to look for the device.
        @param vid:         Vendor ID of the looking device.
        @param pid:         Product ID of the looking device.

        @returns: path to the folder of the device
        """

        def _run(cmd):
            res = host.run(cmd,
                           timeout=30,
                           ignore_status=True,
                           ignore_timeout=True)
            l = res.stdout.splitlines()
            if not l or len(l) != 1:
                return None
            return l[0]

        if not base_path:
            base_path = '/sys/bus/usb/devices/*/'
        else:
            base_path += '*/'
        cmd_template = 'grep -l %s $(find %s -maxdepth 1 -name %s)'
        vid_path = _run(cmd_template % (vid, base_path, 'idVendor'))
        if not vid_path:
            return None

        pid_path = _run(cmd_template % (pid, base_path, 'idProduct'))
        if not pid_path:
            return None

        # check if both files locates in the same folder
        return _run('LC_ALL=C comm -12 <(dirname %s) <(dirname %s)' %
                    (vid_path, pid_path))
