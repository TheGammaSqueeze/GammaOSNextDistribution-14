# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import itertools

from autotest_lib.client.common_lib import utils
from autotest_lib.client.common_lib.cros import cros_config


def detect_camera(iface):
    """
    Checks whether the device has a built-in camera of specified interface by
    CrOS config.

    @param iface: string, either 'usb' or 'mipi', indicating the interface of
                  camera module.
    @returns boolean indicating whether there's a matching camera.
    """
    for i in itertools.count(0):
        queried = cros_config.call_cros_config_get_output(
                '/camera/devices/{} interface'.format(i), utils.run)
        if queried == '':
            break
        if queried == iface:
            return True
    return False
