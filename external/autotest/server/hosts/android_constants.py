# Copyright (c) 2022 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

ANDROID_PHONE_STATION_ATTR = 'phone_station'
ANDROID_PHONE_STATION_SSH_PORT_ATTR = 'phone_station_ssh_port'
ANDROID_SERIAL_NUMBER_ATTR = 'android_serial'

ALL_ANDROID_ATTRS = (ANDROID_PHONE_STATION_ATTR,
                     ANDROID_PHONE_STATION_SSH_PORT_ATTR,
                     ANDROID_SERIAL_NUMBER_ATTR)

CRITICAL_ANDROID_ATTRS = (ANDROID_PHONE_STATION_ATTR,
                          ANDROID_SERIAL_NUMBER_ATTR)
