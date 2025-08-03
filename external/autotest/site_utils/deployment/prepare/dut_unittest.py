# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.


from __future__ import print_function
from __future__ import absolute_import
from __future__ import division
from __future__ import unicode_literals

import six
import unittest

import common
import autotest_lib.site_utils.deployment.prepare.dut as dut

from autotest_lib.server.hosts.host_info import InMemoryHostInfoStore, HostInfo


mock_labstation_args = {
    "labels": [
        "os:labstation",
    ],
    "attributes": None,
    "stable_versions": None,
}

mock_labstation_args_bad_config = {
    "labels": [
        "os:labstation",
    ],
    "attributes": {
        "powerunit_outlet": "AAAA"
    },
    "stable_versions": None,
}


class MockHost(object):
    """Mock Host object, mimics a labstation with a servo attached"""
    SHUTDOWN_TIMEOUT = 0.001
    BOOT_TIMEOUT = 0.001

    def __init__(self, info, power_on=True, power_off=True):
        self.host_info_store = InMemoryHostInfoStore(info=info)
        self.hostname = "a"
        self._called_rpm_power_off_and_wait = 0
        self._called_rpm_power_on_and_wait = 0

    def rpm_power_off_and_wait(self):
        self._called_rpm_power_off_and_wait += 1

    def rpm_power_on_and_wait(self):
        self._called_rpm_power_on_and_wait += 1

class TestDut(unittest.TestCase):
    def test_verify_labstation_RPM_config_unsafe(self):
        """Test successful labstation RPM powercycle"""
        m = MockHost(info=HostInfo(**mock_labstation_args))
        dut.verify_labstation_RPM_config_unsafe(m)

    def test_verify_labstation_RPM_config_unsafe_bad_config(self):
        m = MockHost(info=HostInfo(**mock_labstation_args_bad_config))
        with self.assertRaises(Exception):
            dut.verify_labstation_RPM_config_unsafe(m)


if __name__ == "__main__":
    unittest.main()
