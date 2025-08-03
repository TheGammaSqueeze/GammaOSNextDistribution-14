#!/usr/bin/env python3
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import os

PROFILE_FILE_DIR = '/var/lib/device_health_profile/'
PROFILE_DIR_CONTAINER = os.environ.get("SERVOD_PROFILES_DIR",
                                       '/var/servod/profile/')

# Constants that will be used as key name in device health profile.
BOARD_KEY = 'board'
MODEL_KEY = 'model'
LAST_UPDATE_TIME_KEY = 'last_update_time'
REPAIR_FAIL_COUNT_KEY = 'repair_fail_count'
PROVISION_FAIL_COUNT_KEY = 'provision_fail_count'
DUT_STATE_KEY = 'dut_state'
SERVO_STATE_KEY = 'servo_state'
FAILED_VERIFIERS_KEY = 'failed_verifiers'
SUCCEED_REPAIR_ACTIONS_KEY = 'succeed_repair_actions'
FAILED_REPAIR_ACTIONS_KEY = 'failed_repair_actions'
TIME_ENTER_CURRENT_STATE_KEY = 'time_enter_current_state'
PROFILE_VERSION_KEY = 'profile_verision'
CROS_STABLE_VERSION_KEY = 'last_used_cros_stable_version'
FIRMWARE_STABLE_VERSION_KEY = 'last_used_firmware_stable_version'
FAFT_STABLE_VERSION_KEY = 'last_used_faft_stable_version'
LAST_BADBLOCKS_RO_RUN_TIME_KEY = 'last_badblocks_ro_run_time'
LAST_BADBLOCKS_RW_RUN_TIME_KEY = 'last_badblocks_rw_run_time'
LAST_SERVO_MICRO_FW_UPDATE_RUN_TIME_KEY = 'last_servo_micro_flash_run_time'

# Constant for values
DEFAULT_STRING_VALUE = ''
DEFAULT_TIMESTAMP = '1970-01-01 00:00:00'
TIME_PATTERN = '%Y-%m-%d %H:%M:%S'
DUT_STATE_READY = 'ready'
DUT_STATE_REPAIR_FAILED = 'repair_failed'
STATES_NEED_RESET_COUNTER = (DUT_STATE_READY, DUT_STATE_REPAIR_FAILED)

# Please update profile version when you update any key name or template.
PROFILE_VERSION = '0.0.1'

DEVICE_HEALTH_PROFILE_TEMPLATE = {
        BOARD_KEY: DEFAULT_STRING_VALUE,
        MODEL_KEY: DEFAULT_STRING_VALUE,
        LAST_UPDATE_TIME_KEY: DEFAULT_TIMESTAMP,
        REPAIR_FAIL_COUNT_KEY: 0,
        PROVISION_FAIL_COUNT_KEY: 0,
        DUT_STATE_KEY: DEFAULT_STRING_VALUE,
        SERVO_STATE_KEY: DEFAULT_STRING_VALUE,
        FAILED_VERIFIERS_KEY: {},
        SUCCEED_REPAIR_ACTIONS_KEY: {},
        FAILED_REPAIR_ACTIONS_KEY: {},
        TIME_ENTER_CURRENT_STATE_KEY: DEFAULT_TIMESTAMP,
        PROFILE_VERSION_KEY: PROFILE_VERSION,
        CROS_STABLE_VERSION_KEY: DEFAULT_STRING_VALUE,
        FIRMWARE_STABLE_VERSION_KEY: DEFAULT_STRING_VALUE,
        FAFT_STABLE_VERSION_KEY: DEFAULT_STRING_VALUE,
        LAST_BADBLOCKS_RO_RUN_TIME_KEY: DEFAULT_TIMESTAMP,
        LAST_BADBLOCKS_RW_RUN_TIME_KEY: DEFAULT_TIMESTAMP,
        LAST_SERVO_MICRO_FW_UPDATE_RUN_TIME_KEY: DEFAULT_TIMESTAMP
}
