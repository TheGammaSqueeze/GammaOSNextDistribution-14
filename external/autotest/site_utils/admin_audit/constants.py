#!/usr/bin/env python3
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Hardware states
HW_STATE_NORMAL = 'NORMAL'
HW_STATE_ACCEPTABLE = 'ACCEPTABLE'
HW_STATE_NEED_REPLACEMENT = 'NEED_REPLACEMENT'
HW_STATE_NOT_DETECTED = 'NOT_DETECTED'
HW_STATE_UNKNOWN = 'UNKNOWN'

# Labels for hardware parts
DUT_STORAGE_STATE_PREFIX = 'storage_state'
SERVO_USB_STATE_PREFIX = 'servo_usb_state'
BATTERY_STATE_PREFIX = 'battery_state'

# RPM states
RPM_STATE_LABEL_PREFIX = 'rpm_state'
RPM_STATE_UNKNOWN = 'UNKNOWN'
# Config not provided
RPM_STATE_MISSING_CONFIG = 'MISSING_CONFIG'
# Config is incorrect or not working
RPM_STATE_WRONG_CONFIG = 'WRONG_CONFIG'
# Config present and working as expected
RPM_STATE_WORKING = 'WORKING'
RPM_STATES_SUPPORTED = (
        RPM_STATE_UNKNOWN,
        RPM_STATE_MISSING_CONFIG,
        RPM_STATE_WRONG_CONFIG,
        RPM_STATE_WORKING,
)
