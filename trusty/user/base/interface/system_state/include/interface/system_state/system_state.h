/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <stdint.h>

#define SYSTEM_STATE_PORT "com.android.trusty.system-state"
#define SYSTEM_STATE_MAX_MESSAGE_SIZE 32

/**
 * enum system_state_cmd - command identifiers for system_state functions
 * @SYSTEM_STATE_CMD_RESP_BIT:  Message is a response.
 * @SYSTEM_STATE_CMD_REQ_SHIFT: Number of bits used by @SYSTEM_STATE_RESP_BIT.
 *
 */
enum system_state_cmd {
    SYSTEM_STATE_CMD_RESP_BIT = 1,
    SYSTEM_STATE_CMD_REQ_SHIFT = 1,

    /** @SYSTEM_STATE_CMD_GET_FLAG: Command to read a system state flag. */
    SYSTEM_STATE_CMD_GET_FLAG = (1 << SYSTEM_STATE_CMD_REQ_SHIFT),
};

/**
 * enum system_state_flag - flag identifiers for %SYSTEM_STATE_GET_FLAG
 */
enum system_state_flag {
    /**
     * @SYSTEM_STATE_FLAG_PROVISIONING_ALLOWED:
     *     Flag used to restrict when provisoning is allowed.
     */
    SYSTEM_STATE_FLAG_PROVISIONING_ALLOWED = 1,

    /**
     * @SYSTEM_STATE_FLAG_APP_LOADING_UNLOCKED:
     *     Flag used to indicate that loading apps signed with insecure dev keys
     *     is allowed.
     */
    SYSTEM_STATE_FLAG_APP_LOADING_UNLOCKED = 2,

    /**
     * @SYSTEM_STATE_FLAG_APP_LOADING_VERSION_CHECK:
     *     Flag used to permit skipping of app version checks or rollback
     *     version updates. Contains a value of type enum
     *     system_state_flag_app_loading_version_check.
     */
    SYSTEM_STATE_FLAG_APP_LOADING_VERSION_CHECK = 3,
};

/**
 * enum system_state_flag_provisioning_allowed - Provisioning allowed states
 * @SYSTEM_STATE_FLAG_PROVISIONING_ALLOWED_VALUE_NOT_ALLOWED:
 *     Provisoning is not currently allowed.
 * @SYSTEM_STATE_FLAG_PROVISIONING_ALLOWED_VALUE_ALLOWED:
 *     Provisoning is currently allowed.
 * @SYSTEM_STATE_FLAG_PROVISIONING_ALLOWED_VALUE_ALLOWED_AT_BOOT:
 *     Provisoning is currently allowed if the client is in a boot stage.
 *     For backward compatibility. Not recommened for new systems.
 */
enum system_state_flag_provisioning_allowed {
    SYSTEM_STATE_FLAG_PROVISIONING_ALLOWED_VALUE_NOT_ALLOWED = 0,
    SYSTEM_STATE_FLAG_PROVISIONING_ALLOWED_VALUE_ALLOWED = 1,
    SYSTEM_STATE_FLAG_PROVISIONING_ALLOWED_VALUE_ALLOWED_AT_BOOT = 2,
};

/**
 * enum system_state_flag_app_loading_version_check - App loading version check
 * states
 * @SYSTEM_STATE_FLAG_APP_LOADING_VERSION_CHECK_VALUE_REQUIRED
 *     Rollback version check and updating is required
 * @SYSTEM_STATE_FLAG_APP_LOADING_VERSION_CHECK_VALUE_SKIP_UPDATE
 *     Rollback version check is required, but the rollback version will not be
 *     updated.
 * @SYSTEM_STATE_FLAG_APP_LOADING_VERSION_CHECK_VALUE_SKIP_CHECK
 *     Rollback version check should be skipped (rollback version will not be
 *     updated)
 */
enum system_state_flag_app_loading_version_check {
    SYSTEM_STATE_FLAG_APP_LOADING_VERSION_CHECK_VALUE_REQUIRED = 0,
    SYSTEM_STATE_FLAG_APP_LOADING_VERSION_CHECK_VALUE_SKIP_UPDATE = 1,
    SYSTEM_STATE_FLAG_APP_LOADING_VERSION_CHECK_VALUE_SKIP_CHECK = 2,
};

/**
 * struct system_state_req - common request structure for system_state
 * @cmd:        Command identifier.
 * @reserved:   Reserved, must be 0.
 * @payload:    Payload buffer, meaning determined by @cmd.
 */
struct system_state_req {
    uint32_t cmd;
    uint32_t reserved;
    uint8_t payload[0];
};

/**
 * struct system_state_resp - common response structure for system_state
 * @cmd:        Command identifier.
 * @result:     If non-0, an lk error code.
 * @payload:    Payload buffer, meaning determined by @cmd.
 */
struct system_state_resp {
    uint32_t cmd;
    int32_t result;
    uint8_t payload[0];
};

/**
 * struct system_state_get_flag_req - payload for get-flag request
 * @flag:       One of @enum system_state_flag.
 */
struct system_state_get_flag_req {
    uint32_t flag;
};

/**
 * struct system_state_get_flag_resp - payload for get-flag response
 * @flag:       One of @enum system_state_flag.
 * @reserved:   Reserved, must be 0.
 * @value:      Current value of flag @flag.
 */
struct system_state_get_flag_resp {
    uint32_t flag;
    uint32_t reserved;
    uint64_t value;
};
