# Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import glob, os
from autotest_lib.client.cros.video import device_capability

def find_cameras():
    """
    Find V4L camera devices

    @return [device list]. If no camera is found, returns empty list.
    """
    cameras = [os.path.basename(camera) for camera in
               glob.glob('/sys/bus/usb/drivers/uvcvideo/*/video4linux/video*')]
    if not cameras:
        return []
    return cameras


def has_builtin_usb_camera():
    """Check if there is a built-in USB camera by capability."""
    return device_capability.DeviceCapability().have_capability('builtin_usb_camera')


def has_builtin_or_vivid_camera():
    """Check if there is a built-in USB camera or MIPI camera by capability."""
    return device_capability.DeviceCapability().have_capability(
            'builtin_or_vivid_camera')


def get_camera_hal_paths():
    """Return the paths of all camera HALs on device."""
    return glob.glob('/usr/lib*/camera_hal/*.so')


def get_camera_hal_paths_for_test():
    """Return the paths of all camera HALs on device for test."""
    paths = []
    for path in get_camera_hal_paths():
        name = os.path.basename(path)
        # usb.so might be there for external cameras, skip it if there is no
        # built-in USB camera.
        if name == 'usb.so' and not has_builtin_usb_camera():
            continue
        paths.append(path)
    return paths
