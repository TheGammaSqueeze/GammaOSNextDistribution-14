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

/*
 * Generated mock file from original source file
 *   Functions generated:5
 */

#include "stack/sdp/sdpint.h"
#include "test/common/mock_functions.h"
#include "types/raw_address.h"

tCONN_CB* sdp_conn_originate(const RawAddress& /* p_bd_addr */) {
  inc_func_call_count(__func__);
  return nullptr;
}
void sdp_conn_timer_timeout(void* /* data */) { inc_func_call_count(__func__); }
void sdp_disconnect(tCONN_CB* /* p_ccb */, uint16_t /* reason */) {
  inc_func_call_count(__func__);
}
void sdp_free(void) { inc_func_call_count(__func__); }
void sdp_init(void) { inc_func_call_count(__func__); }
