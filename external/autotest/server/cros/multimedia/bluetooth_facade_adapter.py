# Lint as: python2, python3
# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""An adapter to remotely access the bluetooth facade on DUT."""

from autotest_lib.server.cros.bluetooth.bluetooth_device import BluetoothDevice


class BluetoothFacadeRemoteAdapter(BluetoothDevice):
    """This is an adapter to remotely control DUT bluetooth.

    The Autotest host object representing the remote DUT, passed to this
    class on initialization, can be accessed from its _client property.

    """

    def __init__(self, host, remote_facade_proxy, floss):
        """Construct an BluetoothFacadeRemoteAdapter.

        @param host: Host object representing a remote host.
        @param remote_facade_proxy: RemoteFacadeProxy object.
        @param floss: Target the Floss daemon?

        """
        self._client = host
        super(BluetoothFacadeRemoteAdapter,
              self).__init__(host, remote_facade_proxy, floss)
