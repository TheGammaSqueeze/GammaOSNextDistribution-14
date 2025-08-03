# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from .utils import detect_camera


def detect():
    """
    Checks whether the device has a built-in MIPI camera.

    @returns string: 'mipi_camera' if true, '' otherwise.
    """
    return 'mipi_camera' if detect_camera('mipi') else ''
