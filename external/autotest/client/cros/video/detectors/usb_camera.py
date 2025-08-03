# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from .utils import detect_camera


def detect():
    """
    Checks whether the device has a built-in USB camera.

    @returns string: 'usb_camera' if true, '' otherwise.
    """
    return 'usb_camera' if detect_camera('usb') else ''
