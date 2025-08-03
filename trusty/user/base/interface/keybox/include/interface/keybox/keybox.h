/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <lk/compiler.h>
#include <lk/macros.h>
#include <stddef.h>
#include <stdint.h>

#define KEYBOX_PORT "com.android.trusty.hwkeybox"

/**
 * enum keybox_cmd - Keybox service commands
 *
 * @KEYBOX_CMD_REQ_SHIFT: bitshift of the command index
 * @KEYBOX_CMD_RSP_BIT: bit indicating that this is a response
 * @KEYBOX_CMD_UNWRAP: Unwrap the provided keybox.
 */
enum keybox_cmd {
    KEYBOX_CMD_REQ_SHIFT = 1,
    KEYBOX_CMD_RSP_BIT = 1,
    KEYBOX_CMD_UNWRAP = 0 << KEYBOX_CMD_REQ_SHIFT,
};

/**
 * enum keybox_status - Keybox response code
 *
 * @KEYBOX_STATUS_SUCCESS:         Keybox successfully decrypted.
 * @KEYBOX_STATUS_INVALID_REQUEST: Arguments don't validate.
 * @KEYBOX_STATUS_UNWRAP_FAIL:     Failed to unwrap keybox.
 * @KEYBOX_STATUS_FORBIDDEN:       Process requesting decryption should not
 *                                 receive this keybox.
 * @KEYBOX_STATUS_INTERNAL_ERROR:  An internal error occurred. Please report a bug.
 */
enum keybox_status {
    KEYBOX_STATUS_SUCCESS = 0,
    KEYBOX_STATUS_INVALID_REQUEST = 1,
    KEYBOX_STATUS_UNWRAP_FAIL = 2,
    KEYBOX_STATUS_FORBIDDEN = 3,
    KEYBOX_STATUS_INTERNAL_ERROR = 4,
};

/**
 * KEYBOX_MAX_SIZE - Maximum size of a keybox to be unwrapped by the service.
 */
#define KEYBOX_MAX_SIZE 2048

/**
 * struct keybox_req - Keybox request message
 *
 * @cmd: Which command the keybox service should execute. Should be
 *       an &enum keybox_command
 * @reserved: MBZ
 *
 * This structure is used as a message header and is followed by a payload that
 * has a command-specific meaning.
 */
struct keybox_req {
    uint32_t cmd;
    uint32_t reserved;
};

/**
 * struct keybox_unwrap_req - Keybox unwrap request message
 *
 * @wrapped_keybox_len: The length of the wrapped keybox.
 *
 * The wrapped keybox follows.
 */
struct keybox_unwrap_req {
    uint64_t wrapped_keybox_len;
};

/**
 * struct keybox_resp - Keybox response message
 *
 * @cmd:    The command this is a response to, with the @KEYBOX_CMD_RSP_BIT set.
 *          See &enum keybox_command for values.
 * @status: Whether the request succeeded, or how it failed. This is
 *          represented by a &enum keybox_status value.
 *
 * If status == KEYBOX_STATUS_SUCCESS, the commmand response header will follow.
 *
 */
struct keybox_resp {
    uint32_t cmd;
    int32_t status;
};

/**
 * struct keybox_unwrap_resp - Keybox unwrap response message
 *
 * @unwrapped_keybox_len: The length of the unwrapped keybox.
 *
 * The unwrapped keybox follows.
 */
struct keybox_unwrap_resp {
    uint64_t unwrapped_keybox_len;
};
