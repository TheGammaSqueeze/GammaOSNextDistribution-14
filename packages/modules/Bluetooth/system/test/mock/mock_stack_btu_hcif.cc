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

/*
 * Generated mock file from original source file
 *   Functions generated:9
 */

#include <cstdint>

#define LOG_TAG "bt_btu_hcif"
#include <base/functional/bind.h>
#include <base/functional/callback.h>
#include <base/location.h>

#include "stack/include/bt_hdr.h"
#include "stack/include/btu_hcif.h"
#include "test/common/mock_functions.h"

using hci_cmd_cb = base::OnceCallback<void(
    uint8_t* /* return_parameters */, uint16_t /* return_parameters_length*/)>;

struct cmd_with_cb_data {
  hci_cmd_cb cb;
  base::Location posted_from;
};

void btu_hcif_process_event(uint8_t /* controller_id */, BT_HDR* /* p_msg */) {
  inc_func_call_count(__func__);
}
void btu_hcif_send_cmd(uint8_t /* controller_id */, const BT_HDR* /* p_buf */) {
  inc_func_call_count(__func__);
}
void btu_hcif_send_cmd_with_cb(const base::Location& /* posted_from */,
                               uint16_t /* opcode */, uint8_t* /* params */,
                               uint8_t /* params_len */, hci_cmd_cb /* cb */) {
  inc_func_call_count(__func__);
}
void cmd_with_cb_data_cleanup(cmd_with_cb_data* /* cb_wrapper */) {
  inc_func_call_count(__func__);
}
void cmd_with_cb_data_init(cmd_with_cb_data* /* cb_wrapper */) {
  inc_func_call_count(__func__);
}
