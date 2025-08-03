/*
 * Copyright 2021 The Android Open Source Project
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

#include <cstdint>

#include "macros.h"

/*
 * Define states and events for the RFC multiplexer state machine
 */
typedef enum : uint16_t {
  RFC_MX_STATE_IDLE = 0,
  RFC_MX_STATE_WAIT_CONN_CNF = 1,
  RFC_MX_STATE_CONFIGURE = 2,
  RFC_MX_STATE_SABME_WAIT_UA = 3,
  RFC_MX_STATE_WAIT_SABME = 4,
  RFC_MX_STATE_CONNECTED = 5,
  RFC_MX_STATE_DISC_WAIT_UA = 6,
} tRFC_MX_STATE;

/*
 * Define port states
 */
typedef enum : uint8_t {
  RFC_STATE_CLOSED = 0,
  RFC_STATE_SABME_WAIT_UA = 1,
  RFC_STATE_ORIG_WAIT_SEC_CHECK = 2,
  RFC_STATE_TERM_WAIT_SEC_CHECK = 3,
  RFC_STATE_OPENED = 4,
  RFC_STATE_DISC_WAIT_UA = 5,
} tRFC_PORT_STATE;

inline std::string rfcomm_mx_state_text(const tRFC_MX_STATE& state) {
  switch (state) {
    CASE_RETURN_TEXT(RFC_MX_STATE_IDLE);
    CASE_RETURN_TEXT(RFC_MX_STATE_WAIT_CONN_CNF);
    CASE_RETURN_TEXT(RFC_MX_STATE_CONFIGURE);
    CASE_RETURN_TEXT(RFC_MX_STATE_SABME_WAIT_UA);
    CASE_RETURN_TEXT(RFC_MX_STATE_WAIT_SABME);
    CASE_RETURN_TEXT(RFC_MX_STATE_CONNECTED);
    CASE_RETURN_TEXT(RFC_MX_STATE_DISC_WAIT_UA);
    default:
      return std::string("UNKNOWN[") + std::to_string(state) + std::string("]");
  }
}

inline std::string rfcomm_port_state_text(const tRFC_PORT_STATE& state) {
  switch (state) {
    CASE_RETURN_TEXT(RFC_STATE_CLOSED);
    CASE_RETURN_TEXT(RFC_STATE_SABME_WAIT_UA);
    CASE_RETURN_TEXT(RFC_STATE_ORIG_WAIT_SEC_CHECK);
    CASE_RETURN_TEXT(RFC_STATE_TERM_WAIT_SEC_CHECK);
    CASE_RETURN_TEXT(RFC_STATE_OPENED);
    CASE_RETURN_TEXT(RFC_STATE_DISC_WAIT_UA);
    default:
      return std::string("UNKNOWN[") + std::to_string(state) + std::string("]");
  }
}
