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
 *   Functions generated:12
 */

#include <cstdint>

#include "bta/include/bta_hh_api.h"
#include "bta/include/bta_hh_co.h"
#include "btif/include/btif_hh.h"
#include "test/common/mock_functions.h"
#include "types/raw_address.h"

int bta_hh_co_write(int /* fd */, uint8_t* /* rpt */, uint16_t /* len */) {
  inc_func_call_count(__func__);
  return 0;
}
tBTA_HH_RPT_CACHE_ENTRY* bta_hh_le_co_cache_load(
    const tAclLinkSpec& /* link_spec */, uint8_t* /* p_num_rpt */,
    uint8_t /* app_id */) {
  inc_func_call_count(__func__);
  return nullptr;
}
void bta_hh_co_close(btif_hh_device_t* /* p_dev */) {
  inc_func_call_count(__func__);
}
void bta_hh_co_data(uint8_t /* dev_handle */, uint8_t* /* p_rpt */,
                    uint16_t /* len */, tBTA_HH_PROTO_MODE /* mode */,
                    uint8_t /* sub_class */, uint8_t /* ctry_code */,
                    const tAclLinkSpec& /* link_spec */, uint8_t /* app_id */) {
  inc_func_call_count(__func__);
}
void bta_hh_co_get_rpt_rsp(uint8_t /* dev_handle */, uint8_t /* status */,
                           const uint8_t* /* p_rpt */, uint16_t /* len */) {
  inc_func_call_count(__func__);
}
bool bta_hh_co_open(uint8_t /* dev_handle */, uint8_t /* sub_class */,
                    tBTA_HH_ATTR_MASK /* attr_mask */, uint8_t /* app_id */) {
  inc_func_call_count(__func__);
  return true;
}
void bta_hh_co_send_hid_info(btif_hh_device_t* /* p_dev */,
                             const char* /* dev_name */,
                             uint16_t /* vendor_id */,
                             uint16_t /* product_id */, uint16_t /* version */,
                             uint8_t /* ctry_code */, int /* dscp_len */,
                             uint8_t* /* p_dscp */) {
  inc_func_call_count(__func__);
}
void bta_hh_co_set_rpt_rsp(uint8_t /* dev_handle */, uint8_t /* status */) {
  inc_func_call_count(__func__);
}
void bta_hh_le_co_reset_rpt_cache(const tAclLinkSpec& /* link_spec */,
                                  uint8_t /* app_id */) {
  inc_func_call_count(__func__);
}
void bta_hh_le_co_rpt_info(const tAclLinkSpec& /* link_spec */,
                           tBTA_HH_RPT_CACHE_ENTRY* /* p_entry */,
                           uint8_t /* app_id */) {
  inc_func_call_count(__func__);
}
void uhid_set_non_blocking(int /* fd */) { inc_func_call_count(__func__); }
