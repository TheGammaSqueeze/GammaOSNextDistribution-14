# Copyright 2020 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.bin import test
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros.network import interface
from autotest_lib.client.cros.networking import shill_proxy


class network_WlanPresent(test.test):
    """
    Fail unless at least WiFi device is present or shill is uninitialized.

    """
    version = 1

    def run_once(self):
        """Test main loop."""
        proxy = shill_proxy.ShillProxy()
        uninit = proxy.get_proxy().get_dbus_property(
                proxy.manager,
                proxy.MANAGER_PROPERTY_UNINITIALIZED_TECHNOLOGIES)
        if "wifi" in uninit:
            raise error.TestNAError('shill disables wireless support')

        wlan_ifs = [nic for nic in interface.get_interfaces()
                        if nic.is_wifi_device()]
        if not wlan_ifs:
            raise error.TestFail('Found no recognized wireless device')
        wlan_names = [w.name for w in wlan_ifs]
        logging.info('WiFi device found: %s', ', '.join(wlan_names))
