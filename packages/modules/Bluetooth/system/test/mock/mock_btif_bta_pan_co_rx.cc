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
 *   Functions generated:7
 *
 *  mockcify.pl ver 0.2.1
 */
// Mock include file to share data between tests and mock
#include "test/mock/mock_btif_bta_pan_co_rx.h"

#include <cstdint>

#include "test/common/mock_functions.h"

// Mocked compile conditionals, if any
// Mocked internal structures, if any

namespace test {
namespace mock {
namespace btif_bta_pan_co_rx {

// Function state capture and return values, if needed
struct bta_pan_co_init bta_pan_co_init;
struct bta_pan_co_close bta_pan_co_close;
struct bta_pan_co_mfilt_ind bta_pan_co_mfilt_ind;
struct bta_pan_co_pfilt_ind bta_pan_co_pfilt_ind;
struct bta_pan_co_rx_flow bta_pan_co_rx_flow;
struct bta_pan_co_rx_path bta_pan_co_rx_path;
struct bta_pan_co_tx_path bta_pan_co_tx_path;

}  // namespace btif_bta_pan_co_rx
}  // namespace mock
}  // namespace test

// Mocked functions, if any
uint8_t bta_pan_co_init(uint8_t* q_level) {
  inc_func_call_count(__func__);
  return test::mock::btif_bta_pan_co_rx::bta_pan_co_init(q_level);
}
void bta_pan_co_close(uint16_t handle, uint8_t app_id) {
  inc_func_call_count(__func__);
  test::mock::btif_bta_pan_co_rx::bta_pan_co_close(handle, app_id);
}
void bta_pan_co_mfilt_ind(uint16_t handle, bool indication,
                          tBTA_PAN_STATUS result, uint16_t len,
                          uint8_t* p_filters) {
  inc_func_call_count(__func__);
  test::mock::btif_bta_pan_co_rx::bta_pan_co_mfilt_ind(handle, indication,
                                                       result, len, p_filters);
}
void bta_pan_co_pfilt_ind(uint16_t handle, bool indication,
                          tBTA_PAN_STATUS result, uint16_t len,
                          uint8_t* p_filters) {
  inc_func_call_count(__func__);
  test::mock::btif_bta_pan_co_rx::bta_pan_co_pfilt_ind(handle, indication,
                                                       result, len, p_filters);
}
void bta_pan_co_rx_flow(uint16_t handle, uint8_t app_id, bool enable) {
  inc_func_call_count(__func__);
  test::mock::btif_bta_pan_co_rx::bta_pan_co_rx_flow(handle, app_id, enable);
}
void bta_pan_co_rx_path(uint16_t handle, uint8_t app_id) {
  inc_func_call_count(__func__);
  test::mock::btif_bta_pan_co_rx::bta_pan_co_rx_path(handle, app_id);
}
void bta_pan_co_tx_path(uint16_t handle, uint8_t app_id) {
  inc_func_call_count(__func__);
  test::mock::btif_bta_pan_co_rx::bta_pan_co_tx_path(handle, app_id);
}

// END mockcify generation
