/*
 * Copyright 2023 The Android Open Source Project
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

#include "test/headless/text.h"

#include <string>

#include "include/hardware/bluetooth.h"
#include "macros.h"
#include "os/log.h"

std::string bt_conn_direction_text(const bt_conn_direction_t& direction) {
  switch (direction) {
    CASE_RETURN_TEXT(BT_CONN_DIRECTION_UNKNOWN);
    CASE_RETURN_TEXT(BT_CONN_DIRECTION_OUTGOING);
    CASE_RETURN_TEXT(BT_CONN_DIRECTION_INCOMING);
    default:
      ASSERT_LOG(false, "Illegal bt_conn_direction:%d", direction);
  }
}

std::string bt_discovery_state_text(const bt_discovery_state_t& state) {
  switch (state) {
    CASE_RETURN_TEXT(BT_DISCOVERY_STOPPED);
    CASE_RETURN_TEXT(BT_DISCOVERY_STARTED);
    default:
      ASSERT_LOG(false, "Illegal bt_discovery state:%d", state);
  }
}
