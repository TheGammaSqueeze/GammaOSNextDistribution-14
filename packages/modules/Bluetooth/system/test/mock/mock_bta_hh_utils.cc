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
 *   Functions generated:10
 *
 *  mockcify.pl ver 0.3.0
 */
// Mock include file to share data between tests and mock
#include "test/mock/mock_bta_hh_utils.h"

#include <cstdint>

#include "test/common/mock_functions.h"
#include "types/raw_address.h"

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace bta_hh_utils {

// Function state capture and return values, if needed
struct bta_hh_add_device_to_list bta_hh_add_device_to_list;
struct bta_hh_clean_up_kdev bta_hh_clean_up_kdev;
struct bta_hh_cleanup_disable bta_hh_cleanup_disable;
struct bta_hh_dev_handle_to_cb_idx bta_hh_dev_handle_to_cb_idx;
struct bta_hh_find_cb bta_hh_find_cb;
struct bta_hh_get_cb bta_hh_get_cb;
struct bta_hh_read_ssr_param bta_hh_read_ssr_param;
struct bta_hh_tod_spt bta_hh_tod_spt;
struct bta_hh_trace_dev_db bta_hh_trace_dev_db;
struct bta_hh_update_di_info bta_hh_update_di_info;
struct bta_hh_le_is_hh_gatt_if bta_hh_le_is_hh_gatt_if;

}  // namespace bta_hh_utils
}  // namespace mock
}  // namespace test

// Mocked functions, if any
void bta_hh_add_device_to_list(tBTA_HH_DEV_CB* p_cb, uint8_t handle,
                               uint16_t attr_mask,
                               const tHID_DEV_DSCP_INFO* p_dscp_info,
                               uint8_t sub_class, uint16_t ssr_max_latency,
                               uint16_t ssr_min_tout, uint8_t app_id) {
  inc_func_call_count(__func__);
  test::mock::bta_hh_utils::bta_hh_add_device_to_list(
      p_cb, handle, attr_mask, p_dscp_info, sub_class, ssr_max_latency,
      ssr_min_tout, app_id);
}
void bta_hh_clean_up_kdev(tBTA_HH_DEV_CB* p_cb) {
  inc_func_call_count(__func__);
  test::mock::bta_hh_utils::bta_hh_clean_up_kdev(p_cb);
}
void bta_hh_cleanup_disable(tBTA_HH_STATUS status) {
  inc_func_call_count(__func__);
  test::mock::bta_hh_utils::bta_hh_cleanup_disable(status);
}
uint8_t bta_hh_dev_handle_to_cb_idx(uint8_t dev_handle) {
  inc_func_call_count(__func__);
  return test::mock::bta_hh_utils::bta_hh_dev_handle_to_cb_idx(dev_handle);
}
uint8_t bta_hh_find_cb(const tAclLinkSpec& link_spec) {
  inc_func_call_count(__func__);
  return test::mock::bta_hh_utils::bta_hh_find_cb(link_spec);
}
tBTA_HH_DEV_CB* bta_hh_get_cb(const tAclLinkSpec& link_spec) {
  inc_func_call_count(__func__);
  return test::mock::bta_hh_utils::bta_hh_get_cb(link_spec);
}
tBTA_HH_STATUS bta_hh_read_ssr_param(const tAclLinkSpec& link_spec,
                                     uint16_t* p_max_ssr_lat,
                                     uint16_t* p_min_ssr_tout) {
  inc_func_call_count(__func__);
  return test::mock::bta_hh_utils::bta_hh_read_ssr_param(
      link_spec, p_max_ssr_lat, p_min_ssr_tout);
}
bool bta_hh_tod_spt(tBTA_HH_DEV_CB* p_cb, uint8_t sub_class) {
  inc_func_call_count(__func__);
  return test::mock::bta_hh_utils::bta_hh_tod_spt(p_cb, sub_class);
}
void bta_hh_trace_dev_db(void) {
  inc_func_call_count(__func__);
  test::mock::bta_hh_utils::bta_hh_trace_dev_db();
}
void bta_hh_update_di_info(tBTA_HH_DEV_CB* p_cb, uint16_t vendor_id,
                           uint16_t product_id, uint16_t version, uint8_t flag,
                           uint8_t ctry_code) {
  inc_func_call_count(__func__);
  test::mock::bta_hh_utils::bta_hh_update_di_info(p_cb, vendor_id, product_id,
                                                  version, flag, ctry_code);
}
bool bta_hh_le_is_hh_gatt_if(tGATT_IF client_if) {
  inc_func_call_count(__func__);
  test::mock::bta_hh_utils::bta_hh_le_is_hh_gatt_if(client_if);
  return false;
}
// Mocked functions complete
// END mockcify generation
