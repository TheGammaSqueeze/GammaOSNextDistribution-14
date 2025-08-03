# Copyright (c) 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import unittest
import common
from autotest_lib.server.hosts import labstation_host


LabstationHost = labstation_host.LabstationHost


class MockRPMClient(object):
    """Mocking out the rpm_client to avoid actual API calls"""
    def __init__(self, expected_state=None):
        self._expected_state = expected_state

    def set_power(self, host, new_state):
        assert new_state == self._expected_state


class MockMainSsh(object):
    def __init__(self):
        self.ssh_option = ""

    def maybe_start(self, *args, **kwargs):
        pass

    def close(self, *args, **kwargs):
        pass


class LabstationHostSkipInit(LabstationHost):
    """LabstationHostSkipInit is like a labstation host, but
    skips initialization.

    This gives us the ability to inject our own mocks during host
    setup.
    """
    def __init__(self):
        self._is_localhost = False
        self._main_ssh = MockMainSsh()
        self.env = {}
        self.user = "a"
        self.port = 7
        self.known_hosts_file = None
        self.hostname = "a"

    def run(self, *args, **kwargs):
        class FakeResult(object):
            def __init__(self):
                self.stdout = ""
        return FakeResult()

    def wait_down(self, *args, **kwargs):
        return True

    def wait_up(self, *args, **kwargs):
        return True


class LabstationHostUnittest(unittest.TestCase):
    def test_rpm_power_on_and_wait(self):
        m = MockRPMClient(expected_state='ON')
        labstation = LabstationHostSkipInit()
        labstation.rpm_power_on_and_wait(_rpm_client=m)

    def test_rpm_power_off_and_wait(self):
        m = MockRPMClient(expected_state='OFF')
        labstation = LabstationHostSkipInit()
        labstation.rpm_power_off_and_wait(_rpm_client=m)


if __name__ == "__main__":
    unittest.main()
