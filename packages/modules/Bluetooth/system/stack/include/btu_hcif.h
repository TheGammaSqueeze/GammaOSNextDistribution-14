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

#pragma once

#include <base/functional/callback.h>
#include <base/location.h>

#include <cstdint>

#include "include/hardware/bluetooth.h"
#include "osi/include/osi.h"  // UNUSED_ATTR
#include "stack/include/bt_hdr.h"

/* Functions provided by btu_hcif.cc
 ***********************************
 */
void btu_hcif_process_event(UNUSED_ATTR uint8_t controller_id,
                            const BT_HDR* p_buf);
void btu_hcif_send_cmd(UNUSED_ATTR uint8_t controller_id, const BT_HDR* p_msg);
void btu_hcif_send_cmd_with_cb(const base::Location& posted_from,
                               uint16_t opcode, uint8_t* params,
                               uint8_t params_len,
                               base::OnceCallback<void(uint8_t*, uint16_t)> cb);
namespace bluetooth::legacy::testing {
void btu_hcif_hdl_command_status(uint16_t opcode, uint8_t status,
                                 const uint8_t* p_cmd);
}  // namespace bluetooth::legacy::testing