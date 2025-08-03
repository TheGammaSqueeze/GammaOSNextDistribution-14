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

#include <string>

#define BT_TRANSPORT_INVALID 0
#define BT_TRANSPORT_UNKNOWN BT_TRANSPORT_INVALID
#define BT_TRANSPORT_AUTO BT_TRANSPORT_INVALID

#define BT_TRANSPORT_BR_EDR 1
#define BT_TRANSPORT_LE 2
typedef uint8_t tBT_TRANSPORT;

inline std::string bt_transport_text(tBT_TRANSPORT transport) {
  switch (transport) {
    case BT_TRANSPORT_BR_EDR:
      return std::string("br_edr");
    case BT_TRANSPORT_LE:
      return std::string("le");
    default:
      return std::string("unknown");
  }
}
