# Copyright (c) 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.


from autotest_lib.client.common_lib import global_config
_CONFIG = global_config.global_config

# Names of the host attributes in the database that represent the values for
# the servo_host and servo_port for a servo connected to the DUT.
SERVO_HOST_ATTR = 'servo_host'
SERVO_HOST_SSH_PORT_ATTR = 'servo_host_ssh_port'
SERVO_PORT_ATTR = 'servo_port'
SERVO_BOARD_ATTR = 'servo_board'
# Model is inferred from host labels.
SERVO_MODEL_ATTR = 'servo_model'
SERVO_SERIAL_ATTR = 'servo_serial'
SERVOD_DOCKER_ATTR = 'servod_docker'
# Indicates what type of servo setup, example value: REGULAR or DUAL_V4.
SERVO_SETUP_ATTR = 'servo_setup'
SERVO_FW_CHANNEL_ATTR = 'servo_fw_channel'
SERVO_SETUP_VALUE_DUAL_V4 = 'DUAL_V4'
SERVO_RECOVERY_MODE = 'servo_recovery'
SERVO_ATTR_KEYS = (
        SERVO_BOARD_ATTR,
        SERVO_HOST_ATTR,
        SERVOD_DOCKER_ATTR,
        SERVO_HOST_SSH_PORT_ATTR,
        SERVO_PORT_ATTR,
        SERVO_SERIAL_ATTR,
        SERVO_SETUP_ATTR,
        SERVO_FW_CHANNEL_ATTR,
)

# Additional args that will be appended to servod start command.
ADDITIONAL_SERVOD_ARGS = 'additional_servod_args'

# Timeout value for stop/start servod process.
SERVOD_TEARDOWN_TIMEOUT = 3
SERVOD_QUICK_STARTUP_TIMEOUT = 20
SERVOD_STARTUP_TIMEOUT = 60

# Prefix of pools that require servod cr50 config.
CR50_CONFIG_POOL_PREFIX = 'faft-cr50'

ENABLE_SSH_TUNNEL_FOR_SERVO = _CONFIG.get_config_value(
        'CROS', 'enable_ssh_tunnel_for_servo', type=bool, default=False)

SERVO_TYPE_LABEL_PREFIX = 'servo_type'
SERVO_STATE_LABEL_PREFIX = 'servo_state'

# constants to support whole list of states for servo
SERVO_STATE_UNKNOWN = 'UNKNOWN'
SERVO_STATE_MISSING_CONFIG = 'MISSING_CONFIG'
SERVO_STATE_WRONG_CONFIG = 'WRONG_CONFIG'
SERVO_STATE_NO_SSH = 'NO_SSH'
SERVO_STATE_SERVO_HOST_ISSUE = 'SERVO_HOST_ISSUE'
SERVO_STATE_NOT_CONNECTED = 'NOT_CONNECTED'
SERVO_STATE_SERIAL_MISMATCH = 'SERVO_SERIAL_MISMATCH'
SERVO_STATE_SERVO_UPDATER_ISSUE = 'SERVO_UPDATER_ISSUE'
SERVO_STATE_SERVOD_DUT_CONTROLLER_MISSING = 'SERVOD_DUT_CONTROLLER_MISSING'
SERVO_STATE_NEED_REPLACEMENT = 'NEED_REPLACEMENT'
SERVO_STATE_CR50_CONSOLE_MISSING = 'CR50_CONSOLE_MISSING'
SERVO_STATE_CCD_TESTLAB_ISSUE = 'CCD_TESTLAB_ISSUE'
SERVO_STATE_SERVOD_ISSUE = 'SERVOD_ISSUE'
SERVO_STATE_SERVOD_PROXY_ISSUE = 'SERVOD_PROXY_ISSUE'
SERVO_STATE_LID_OPEN_FAILED = 'LID_OPEN_FAILED'
SERVO_STATE_BAD_RIBBON_CABLE = 'BAD_RIBBON_CABLE'
SERVO_STATE_TOPOLOGY_ISSUE = 'TOPOLOGY_ISSUE'
SERVO_STATE_SBU_LOW_VOLTAGE = 'SBU_LOW_VOLTAGE'
SERVO_STATE_CR50_NOT_ENUMERATED = 'CR50_NOT_ENUMERATED'
SERVO_STATE_DUT_NOT_CONNECTED = 'DUT_NOT_CONNECTED'
SERVO_STATE_EC_BROKEN = 'EC_BROKEN'
SERVO_STATE_BROKEN = 'BROKEN'
SERVO_STATE_WORKING = 'WORKING'

# constants to support reset servo via smart usbhub.
SMART_USBHUB_LABEL = 'smart_usbhub'
SERVO_RESET_TIMEOUT_SECONDS = 20
ERROR_MESSAGE_USB_HUB_NOT_COMPATIBLE = ('Be sure the hub is a supported'
                                        ' smart hub')
# a servo serial number should be fed to this constant
ERROR_MESSAGE_DEVICE_NOT_FOUND = 'Device with serial \'%s\' not found.'