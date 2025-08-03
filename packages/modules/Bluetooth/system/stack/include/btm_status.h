/*
 * Copyright 2020 The Android Open Source Project
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

#include <base/strings/stringprintf.h>

#include <cstdint>

#include "macros.h"

/* BTM application return status codes */
enum : uint8_t {
  BTM_SUCCESS = 0,         /* 0  Command succeeded                 */
  BTM_CMD_STARTED,         /* 1  Command started OK.               */
  BTM_BUSY,                /* 2  Device busy with another command  */
  BTM_NO_RESOURCES,        /* 3  No resources to issue command     */
  BTM_MODE_UNSUPPORTED,    /* 4  Request for 1 or more unsupported modes */
  BTM_ILLEGAL_VALUE,       /* 5  Illegal parameter value           */
  BTM_WRONG_MODE,          /* 6  Device in wrong mode for request  */
  BTM_UNKNOWN_ADDR,        /* 7  Unknown remote BD address         */
  BTM_DEVICE_TIMEOUT,      /* 8  Device timeout                    */
  BTM_BAD_VALUE_RET,       /* 9  A bad value was received from HCI */
  BTM_ERR_PROCESSING,      /* 10 Generic error                     */
  BTM_NOT_AUTHORIZED,      /* 11 Authorization failed              */
  BTM_DEV_RESET,           /* 12 Device has been reset             */
  BTM_CMD_STORED,          /* 13 request is stored in control block */
  BTM_ILLEGAL_ACTION,      /* 14 state machine gets illegal command */
  BTM_DELAY_CHECK,         /* 15 delay the check on encryption */
  BTM_SCO_BAD_LENGTH,      /* 16 Bad SCO over HCI data length */
  BTM_SUCCESS_NO_SECURITY, /* 17 security passed, no security set  */
  BTM_FAILED_ON_SECURITY,  /* 18 security failed                   */
  BTM_REPEATED_ATTEMPTS,   /* 19 repeated attempts for LE security requests */
  BTM_MODE4_LEVEL4_NOT_SUPPORTED,    /* 20 Secure Connections Only Mode can't be
                                        supported */
  BTM_DEV_RESTRICT_LISTED,           /* 21 The device is restrict listed */
  BTM_ERR_KEY_MISSING,               /* 22 Handle for Pin or Key Missing */
  BTM_NOT_AUTHENTICATED,             /* 23 the link is not authenticated */
  BTM_NOT_ENCRYPTED,                 /* 24 the link is not encrypted */
  BTM_INSUFFICIENT_ENCRYPT_KEY_SIZE, /* 25 the encrypt key size not sufficient
                                      */
  BTM_MAX_STATUS_VALUE,
  BTM_UNDEFINED = 0xFF,
};
typedef uint8_t tBTM_STATUS;

inline uint8_t btm_status_value(const tBTM_STATUS& status) {
  return static_cast<uint8_t>(status);
}

inline tBTM_STATUS to_btm_status(const uint8_t& value) {
  if (value >= BTM_MAX_STATUS_VALUE) return BTM_UNDEFINED;
  return static_cast<tBTM_STATUS>(value);
}

inline std::string btm_status_text(const tBTM_STATUS& status) {
  switch (status) {
    CASE_RETURN_TEXT(BTM_SUCCESS);
    CASE_RETURN_TEXT(BTM_CMD_STARTED);
    CASE_RETURN_TEXT(BTM_BUSY);
    CASE_RETURN_TEXT(BTM_NO_RESOURCES);
    CASE_RETURN_TEXT(BTM_MODE_UNSUPPORTED);
    CASE_RETURN_TEXT(BTM_ILLEGAL_VALUE);
    CASE_RETURN_TEXT(BTM_WRONG_MODE);
    CASE_RETURN_TEXT(BTM_UNKNOWN_ADDR);
    CASE_RETURN_TEXT(BTM_DEVICE_TIMEOUT);
    CASE_RETURN_TEXT(BTM_BAD_VALUE_RET);
    CASE_RETURN_TEXT(BTM_ERR_PROCESSING);
    CASE_RETURN_TEXT(BTM_NOT_AUTHORIZED);
    CASE_RETURN_TEXT(BTM_DEV_RESET);
    CASE_RETURN_TEXT(BTM_CMD_STORED);
    CASE_RETURN_TEXT(BTM_ILLEGAL_ACTION);
    CASE_RETURN_TEXT(BTM_DELAY_CHECK);
    CASE_RETURN_TEXT(BTM_SCO_BAD_LENGTH);
    CASE_RETURN_TEXT(BTM_SUCCESS_NO_SECURITY);
    CASE_RETURN_TEXT(BTM_FAILED_ON_SECURITY);
    CASE_RETURN_TEXT(BTM_REPEATED_ATTEMPTS);
    CASE_RETURN_TEXT(BTM_MODE4_LEVEL4_NOT_SUPPORTED);
    CASE_RETURN_TEXT(BTM_DEV_RESTRICT_LISTED);
    CASE_RETURN_TEXT(BTM_ERR_KEY_MISSING);
    CASE_RETURN_TEXT(BTM_NOT_AUTHENTICATED);
    CASE_RETURN_TEXT(BTM_NOT_ENCRYPTED);
    CASE_RETURN_TEXT(BTM_INSUFFICIENT_ENCRYPT_KEY_SIZE);
    default:
      return base::StringPrintf("UNKNOWN[%hhu]", status);
  }
}
