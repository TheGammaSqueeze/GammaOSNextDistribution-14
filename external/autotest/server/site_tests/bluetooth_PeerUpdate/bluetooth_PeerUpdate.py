# Lint as: python2, python3
# Copyright 2019 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""
Test which updates chameleond on the Bluetooth Peer device

This is not a test per se. This 'test' checks if the chameleond commit on the
Bluetooth peer device and updates it if it below the expected value.

The expected commit and the installation bundle is downloaded from google cloud
storage.
"""

from autotest_lib.server import test
from autotest_lib.server.cros.bluetooth import bluetooth_peer_update

class bluetooth_PeerUpdate(test.test):
    """
    This test updates chameleond on Bluetooth peer devices

    """

    version = 1

    def run_once(self, host, btpeer_args=[]):
        """ Update Bluetooth peer device

        @param host: the DUT, usually a chromebook
        """
        host.initialize_btpeer(btpeer_args=btpeer_args)
        bluetooth_peer_update.update_all_peers(host, raise_error=True)
