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
 *   Functions generated:8
 */

#include <cstdint>

#include "bta/sys/bta_sys.h"
#include "stack/include/avdt_api.h"
#include "test/common/mock_functions.h"
#include "types/raw_address.h"

void bta_ar_avdt_conn(tBTA_SYS_ID sys_id, const RawAddress& bd_addr,
                      uint8_t scb_index) {
  inc_func_call_count(__func__);
}
void bta_ar_dereg_avct() { inc_func_call_count(__func__); }
void bta_ar_dereg_avdt() { inc_func_call_count(__func__); }
void bta_ar_dereg_avrc(uint16_t service_uuid) { inc_func_call_count(__func__); }
void bta_ar_init(void) { inc_func_call_count(__func__); }
void bta_ar_reg_avct() { inc_func_call_count(__func__); }
void bta_ar_reg_avdt(AvdtpRcb* p_reg, tAVDT_CTRL_CBACK* p_cback) {
  inc_func_call_count(__func__);
}
void bta_ar_reg_avrc(uint16_t service_uuid, const char* service_name,
                     const char* provider_name, uint16_t categories,
                     bool browse_supported, uint16_t profile_version) {
  inc_func_call_count(__func__);
}
