# Copyright 2022 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
from autotest_lib.client.common_lib import error
from autotest_lib.server import test


class infra_MultiDutsWithAndroid(test.test):
    """
    Verify the test can create correct host type for Android devices.

    """
    version = 1

    def _verify_adb(self, dut):
        logging.info("Starting to verify basic adb actions.")
        dut.restart_adb_server()
        dut.ensure_device_connectivity()
        ip_address = dut.get_wifi_ip_address()
        logging.info("Ip address from Android device: %s", ip_address)

    def run_once(self, host, companions):
        """
        Starting point of this test.

        Note: base class sets host as self._host.

        """
        self.host = host
        android_device_tested = False
        for dut in companions:
            if hasattr(dut, 'phone_station'):
                dut_out = dut.phone_station.run('echo True').stdout.strip()
                if dut_out != 'True':
                    raise error.TestError(
                            'phone station stdout != True (got: %s)', dut_out)
                self._verify_adb(dut)
                android_device_tested = True
        if not android_device_tested:
            raise error.TestError(
                    'No Android host detected from companion duts.')
