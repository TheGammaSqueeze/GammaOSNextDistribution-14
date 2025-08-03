#!/usr/bin/env python
# Lint as: python2, python3
# Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import logging
import logging.handlers

import common
from autotest_lib.client.cros import constants
from autotest_lib.client.cros import xmlrpc_server
from autotest_lib.client.cros.multimedia import bluetooth_facade


class BluetoothDeviceXmlRpcDelegate(xmlrpc_server.XmlRpcDelegate,
                                    bluetooth_facade.BluezFacadeLocal):
    """Exposes DUT methods called remotely during Bluetooth autotests.

    The delegate inherits from BluezFacadeLocal where all native calls
    should be kept. This XmlRpcDelegate is kept around for when Bluetooth needs
    to be called without using the MultimediaRpcDelegate.

    TODO(abps): Remove this xmlrpc delegate once it's no longer used.
    """

    def __init__(self):
        super(BluetoothDeviceXmlRpcDelegate, self).__init__()

if __name__ == '__main__':
    logging.basicConfig(level=logging.DEBUG)
    handler = logging.handlers.SysLogHandler(address='/dev/log')
    formatter = logging.Formatter(
            'bluetooth_device_xmlrpc_server: [%(levelname)s] %(message)s')
    handler.setFormatter(formatter)
    logging.getLogger().addHandler(handler)
    logging.debug('bluetooth_device_xmlrpc_server main...')
    server = xmlrpc_server.XmlRpcServer(
            'localhost', constants.BLUETOOTH_DEVICE_XMLRPC_SERVER_PORT)
    server.register_delegate(BluetoothDeviceXmlRpcDelegate())
    server.run()
