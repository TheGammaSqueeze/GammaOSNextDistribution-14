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

#include <bluetooth/log.h>

#include <cstdint>

#include "macros.h"

/*
 * Events that can be received by multiplexer as well as port state machines
 */
enum tRFC_EVENT : uint16_t {
  /*
   * Events that can be received by multiplexer as well as port state machines
   */
  RFC_EVENT_SABME = 0,
  RFC_EVENT_UA = 1,
  RFC_EVENT_DM = 2,
  RFC_EVENT_DISC = 3,
  RFC_EVENT_UIH = 4,
  RFC_EVENT_TIMEOUT = 5,
  RFC_EVENT_BAD_FRAME = 50,
};

/*
 * Multiplexer events
 */
enum tRFC_MX_EVENT : uint16_t {
  /*
   * Multiplexer events
   */
  RFC_MX_EVENT_SABME = RFC_EVENT_SABME,
  RFC_MX_EVENT_UA = RFC_EVENT_UA,
  RFC_MX_EVENT_DM = RFC_EVENT_DM,
  RFC_MX_EVENT_DISC = RFC_EVENT_DISC,
  RFC_MX_EVENT_UIH = RFC_EVENT_UIH,
  RFC_MX_EVENT_TIMEOUT = RFC_EVENT_TIMEOUT,
  RFC_MX_EVENT_START_REQ = 6,
  RFC_MX_EVENT_START_RSP = 7,
  RFC_MX_EVENT_CLOSE_REQ = 8,
  RFC_MX_EVENT_CONN_CNF = 9,
  RFC_MX_EVENT_CONN_IND = 10,
  RFC_MX_EVENT_CONF_CNF = 11,
  RFC_MX_EVENT_CONF_IND = 12,
  RFC_MX_EVENT_QOS_VIOLATION_IND = 13,
  RFC_MX_EVENT_DISC_IND = 14,
};

/*
 * Port events
 */
enum tRFC_PORT_EVENT : uint16_t {
  /*
   * Port events
   */
  RFC_PORT_EVENT_SABME = RFC_EVENT_SABME,
  RFC_PORT_EVENT_UA = RFC_EVENT_UA,
  RFC_PORT_EVENT_DM = RFC_EVENT_DM,
  RFC_PORT_EVENT_DISC = RFC_EVENT_DISC,
  RFC_PORT_EVENT_UIH = RFC_EVENT_UIH,
  RFC_PORT_EVENT_TIMEOUT = RFC_EVENT_TIMEOUT,
  RFC_PORT_EVENT_OPEN = 9,
  RFC_PORT_EVENT_ESTABLISH_RSP = 11,
  RFC_PORT_EVENT_CLOSE = 12,
  RFC_PORT_EVENT_CLEAR = 13,
  RFC_PORT_EVENT_DATA = 14,
  RFC_PORT_EVENT_SEC_COMPLETE = 15,
};

// Common events for both port and mux
inline std::string rfcomm_event_text(const tRFC_EVENT& event) {
  switch (event) {
    CASE_RETURN_TEXT(RFC_EVENT_SABME);
    CASE_RETURN_TEXT(RFC_EVENT_UA);
    CASE_RETURN_TEXT(RFC_EVENT_DM);
    CASE_RETURN_TEXT(RFC_EVENT_DISC);
    CASE_RETURN_TEXT(RFC_EVENT_UIH);
    CASE_RETURN_TEXT(RFC_EVENT_TIMEOUT);
    CASE_RETURN_TEXT(RFC_EVENT_BAD_FRAME);
    default:
      return std::string("UNKNOWN[") + std::to_string(event) + std::string("]");
  }
}

inline std::string rfcomm_mx_event_text(const tRFC_MX_EVENT& event) {
  switch (event) {
    CASE_RETURN_TEXT(RFC_MX_EVENT_SABME);
    CASE_RETURN_TEXT(RFC_MX_EVENT_UA);
    CASE_RETURN_TEXT(RFC_MX_EVENT_DM);
    CASE_RETURN_TEXT(RFC_MX_EVENT_DISC);
    CASE_RETURN_TEXT(RFC_MX_EVENT_UIH);
    CASE_RETURN_TEXT(RFC_MX_EVENT_TIMEOUT);
    CASE_RETURN_TEXT(RFC_MX_EVENT_START_REQ);
    CASE_RETURN_TEXT(RFC_MX_EVENT_START_RSP);
    CASE_RETURN_TEXT(RFC_MX_EVENT_CLOSE_REQ);
    CASE_RETURN_TEXT(RFC_MX_EVENT_CONN_CNF);
    CASE_RETURN_TEXT(RFC_MX_EVENT_CONN_IND);
    CASE_RETURN_TEXT(RFC_MX_EVENT_CONF_CNF);
    CASE_RETURN_TEXT(RFC_MX_EVENT_CONF_IND);
    CASE_RETURN_TEXT(RFC_MX_EVENT_QOS_VIOLATION_IND);
    CASE_RETURN_TEXT(RFC_MX_EVENT_DISC_IND);
    default:
      return std::string("UNKNOWN[") + std::to_string(event) + std::string("]");
  }
}

inline std::string rfcomm_port_event_text(const tRFC_PORT_EVENT& event) {
  switch (event) {
    CASE_RETURN_TEXT(RFC_PORT_EVENT_SABME);
    CASE_RETURN_TEXT(RFC_PORT_EVENT_UA);
    CASE_RETURN_TEXT(RFC_PORT_EVENT_DM);
    CASE_RETURN_TEXT(RFC_PORT_EVENT_DISC);
    CASE_RETURN_TEXT(RFC_PORT_EVENT_UIH);
    CASE_RETURN_TEXT(RFC_PORT_EVENT_TIMEOUT);
    CASE_RETURN_TEXT(RFC_PORT_EVENT_OPEN);
    CASE_RETURN_TEXT(RFC_PORT_EVENT_ESTABLISH_RSP);
    CASE_RETURN_TEXT(RFC_PORT_EVENT_CLOSE);
    CASE_RETURN_TEXT(RFC_PORT_EVENT_CLEAR);
    CASE_RETURN_TEXT(RFC_PORT_EVENT_DATA);
    CASE_RETURN_TEXT(RFC_PORT_EVENT_SEC_COMPLETE);
    default:
      return std::string("UNKNOWN[") + std::to_string(event) + std::string("]");
  }
}

namespace fmt {
template <>
struct formatter<tRFC_EVENT> : enum_formatter<tRFC_EVENT> {};
template <>
struct formatter<tRFC_MX_EVENT> : enum_formatter<tRFC_MX_EVENT> {};
template <>
struct formatter<tRFC_PORT_EVENT> : enum_formatter<tRFC_PORT_EVENT> {};
}  // namespace fmt
