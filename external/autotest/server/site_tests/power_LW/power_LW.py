# Lint as: python2, python3
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
import logging


from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import utils
from autotest_lib.server import autotest
from autotest_lib.server import test
from autotest_lib.server.hosts import factory


class power_LW(test.test):
    """Wrapper test around a client test for power lab."""
    version = 1

    SERVO_V4_ETH_VENDOR = '0bda'
    SERVO_V4_ETH_PRODUCT = '8153'
    WIFI_SSID = 'powertest_ap'
    WIFI_PASSWORD = 'chromeos'

    def _get_wlan_ip(self, host):
        """Connect to wifi and return wlan ip address."""
        wlan_ip = host.get_wlan_ip()
        logging.info('wlan_ip=%s', wlan_ip)
        if wlan_ip:
            return wlan_ip

        if not host.connect_to_wifi(self.WIFI_SSID, self.WIFI_PASSWORD):
            logging.info('Script to connect to wifi is probably missing.'
                         'Run stub_Pass as a workaround to install it.')
            autotest_client = autotest.Autotest(host)
            autotest_client.run_test('stub_Pass')
            if not host.connect_to_wifi(self.WIFI_SSID, self.WIFI_PASSWORD):
                raise error.TestError('Can not connect to wifi.')

        wlan_ip = host.get_wlan_ip()
        logging.info('After connected to wifi wlan_ip=%s', wlan_ip)
        if not wlan_ip:
            raise error.TestError('Can not find wlan ip.')
        return wlan_ip

    def _get_wlan_host(self, host, machine):
        """Return CrosHost object that use wifi."""
        wlan_ip = self._get_wlan_ip(host)
        if machine['hostname'] == wlan_ip:
            return host

        hostname = wlan_ip
        if utils.host_is_in_power_lab(machine['hostname']):
            hostname = utils.get_power_lab_wlan_hostname(machine['hostname'])

        machine['hostname'] = hostname
        return factory.create_host(machine)

    def _stop_ethernet(self, host):
        """Find and unbind servo v4 usb ethernet."""
        # Stop check_ethernet.hook to reconnect the usb device
        try:
            host.run('stop recover_duts')
        except:
            logging.warning("Continue if stop recover_duts failed.")

        eth_usb = host.find_usb_devices(
            self.SERVO_V4_ETH_VENDOR, self.SERVO_V4_ETH_PRODUCT)
        if len(eth_usb) == 1 and eth_usb[0] and host.get_wlan_ip():
            host.unbind_usb_device(eth_usb[0])

    def run_once(self, host, test, args, machine):
        """Prepare DUT for power test then run the client test.

        The DUT will
        - Switch from ethernet connection to wifi.
        - Unbind Servo v4 USB ethernet device.
        - Set EC to force discharge during the client test.

        @param host: CrosHost object representing the DUT.
        @param test: testname
        @param args: arguments of the test in a dict.
        @param machine: machine dict of the host.
        """
        wlan_host = self._get_wlan_host(host, machine)
        if wlan_host != host:
            self._stop_ethernet(host)

        args['force_discharge'] = True
        args['tag'] = args.get('tag', 'PLW')

        autotest_client = autotest.Autotest(wlan_host)
        autotest_client.run_test(test, **args)

        # Restore USB ethernet device.
        wlan_host.reboot()
