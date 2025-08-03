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
 *   Functions generated:13
 */

#include <base/strings/stringprintf.h>

#include "gap_api.h"
#include "l2c_api.h"
#include "stack/include/bt_hdr.h"
#include "test/common/mock_functions.h"
#include "types/raw_address.h"

const RawAddress* GAP_ConnGetRemoteAddr(uint16_t /* gap_handle */) {
  inc_func_call_count(__func__);
  return nullptr;
}
int GAP_GetRxQueueCnt(uint16_t /* handle */, uint32_t* /* p_rx_queue_count */) {
  inc_func_call_count(__func__);
  return 0;
}
uint16_t GAP_ConnClose(uint16_t /* gap_handle */) {
  inc_func_call_count(__func__);
  return 0;
}
uint16_t GAP_ConnGetL2CAPCid(uint16_t /* gap_handle */) {
  inc_func_call_count(__func__);
  return 0;
}
uint16_t GAP_ConnGetRemMtuSize(uint16_t /* gap_handle */) {
  inc_func_call_count(__func__);
  return 0;
}
uint16_t GAP_ConnOpen(const char* /* p_serv_name */, uint8_t /* service_id */,
                      bool /* is_server */, const RawAddress* /* p_rem_bda */,
                      uint16_t /* psm */, uint16_t /* le_mps */,
                      tL2CAP_CFG_INFO* /* p_cfg */,
                      tL2CAP_ERTM_INFO* /* ertm_info */,
                      uint16_t /* security */, tGAP_CONN_CALLBACK* /* p_cb */,
                      tBT_TRANSPORT /* transport */) {
  inc_func_call_count(__func__);
  return 0;
}
uint16_t GAP_ConnReadData(uint16_t /* gap_handle */, uint8_t* /* p_data */,
                          uint16_t /* max_len */, uint16_t* /* p_len */) {
  inc_func_call_count(__func__);
  return 0;
}
uint16_t GAP_ConnWriteData(uint16_t /* gap_handle */, BT_HDR* /* msg */) {
  inc_func_call_count(__func__);
  return 0;
}
void GAP_Init(void) { inc_func_call_count(__func__); }
void gap_tx_complete_ind(uint16_t /* l2cap_cid */, uint16_t /* sdu_sent */) {
  inc_func_call_count(__func__);
}
