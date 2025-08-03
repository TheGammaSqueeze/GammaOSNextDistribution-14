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

#include <string>

/* HCI role definitions */
typedef enum : uint8_t {
  HCI_ROLE_CENTRAL = 0x00,
  HCI_ROLE_PERIPHERAL = 0x01,
  HCI_ROLE_UNKNOWN = 0xff,
} tHCI_ROLE;

inline std::string hci_role_text(const tHCI_ROLE& role) {
  switch (role) {
    case HCI_ROLE_CENTRAL:
      return std::string("central");
    case HCI_ROLE_PERIPHERAL:
      return std::string("peripheral");
    default:
      return std::string("unknown");
  }
}

inline tHCI_ROLE to_hci_role(const uint8_t& role) {
  if (role == 0)
    return HCI_ROLE_CENTRAL;
  else if (role == 1)
    return HCI_ROLE_PERIPHERAL;
  else
    return HCI_ROLE_UNKNOWN;
}

typedef tHCI_ROLE hci_role_t;         // LEGACY
const auto RoleText = hci_role_text;  // LEGACY
