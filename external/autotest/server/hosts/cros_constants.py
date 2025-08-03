# Copyright (c) 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Device is not fixable due issues with hardware and has to be replaced
DEVICE_STATE_NEEDS_REPLACEMENT = 'needs_replacement'
# Device required manual attention to be fixed
DEVICE_STATE_NEEDS_MANUAL_REPAIR = 'needs_manual_repair'
# Device required re-deployment to be fixed
DEVICE_STATE_NEEDS_DEPLOY = 'needs_deploy'

# Timeout for verifiers.
SHORT_VERIFY_TIMEOUT_SEC = 60
VERIFY_TIMEOUT_SEC = 60 * 5
LONG_VERIFY_TIMEOUT_SEC = 60 * 20

# Timeout for repair actions.
SHORT_REPAIR_TIMEOUT_SEC = 60
REPAIR_TIMEOUT_SEC = 60 * 10
LONG_REPAIR_TIMEOUT_SEC = 60 * 30

# Minimum battery level for the good DUT.
MIN_BATTERY_LEVEL = 80.0
