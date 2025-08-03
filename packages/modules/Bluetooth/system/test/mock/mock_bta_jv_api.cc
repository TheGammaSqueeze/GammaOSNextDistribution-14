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
 *   Functions generated:22
 */

#include <base/functional/bind.h>
#include <base/location.h>
#include <base/logging.h>

#include <cstdint>
#include <memory>

#include "bta/include/bta_jv_api.h"
#include "stack/include/bt_hdr.h"
#include "test/common/mock_functions.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

tBTA_JV_STATUS BTA_JvCreateRecordByUser(uint32_t rfcomm_slot_id) {
  inc_func_call_count(__func__);
  return 0;
}
tBTA_JV_STATUS BTA_JvDeleteRecord(uint32_t handle) {
  inc_func_call_count(__func__);
  return 0;
}
tBTA_JV_STATUS BTA_JvEnable(tBTA_JV_DM_CBACK* p_cback) {
  inc_func_call_count(__func__);
  return 0;
}
tBTA_JV_STATUS BTA_JvFreeChannel(uint16_t channel, int conn_type) {
  inc_func_call_count(__func__);
  return 0;
}
tBTA_JV_STATUS BTA_JvL2capClose(uint32_t handle) {
  inc_func_call_count(__func__);
  return 0;
}
tBTA_JV_STATUS BTA_JvL2capRead(uint32_t handle, uint32_t req_id,
                               uint8_t* p_data, uint16_t len) {
  inc_func_call_count(__func__);
  return 0;
}
tBTA_JV_STATUS BTA_JvL2capReady(uint32_t handle, uint32_t* p_data_size) {
  inc_func_call_count(__func__);
  return 0;
}
tBTA_JV_STATUS BTA_JvL2capStopServer(uint16_t local_psm,
                                     uint32_t l2cap_socket_id) {
  inc_func_call_count(__func__);
  return 0;
}
tBTA_JV_STATUS BTA_JvL2capWrite(uint32_t handle, uint32_t req_id, BT_HDR* msg,
                                uint32_t user_id) {
  inc_func_call_count(__func__);
  return 0;
}
tBTA_JV_STATUS BTA_JvRfcommClose(uint32_t handle, uint32_t rfcomm_slot_id) {
  inc_func_call_count(__func__);
  return 0;
}
tBTA_JV_STATUS BTA_JvRfcommConnect(tBTA_SEC sec_mask, tBTA_JV_ROLE role,
                                   uint8_t remote_scn,
                                   const RawAddress& peer_bd_addr,
                                   tBTA_JV_RFCOMM_CBACK* p_cback,
                                   uint32_t rfcomm_slot_id) {
  inc_func_call_count(__func__);
  return 0;
}
tBTA_JV_STATUS BTA_JvRfcommStartServer(tBTA_SEC sec_mask, tBTA_JV_ROLE role,
                                       uint8_t local_scn, uint8_t max_session,
                                       tBTA_JV_RFCOMM_CBACK* p_cback,
                                       uint32_t rfcomm_slot_id) {
  inc_func_call_count(__func__);
  return 0;
}
tBTA_JV_STATUS BTA_JvRfcommStopServer(uint32_t handle,
                                      uint32_t rfcomm_slot_id) {
  inc_func_call_count(__func__);
  return 0;
}
tBTA_JV_STATUS BTA_JvRfcommWrite(uint32_t handle, uint32_t req_id) {
  inc_func_call_count(__func__);
  return 0;
}
tBTA_JV_STATUS BTA_JvSetPmProfile(uint32_t handle, tBTA_JV_PM_ID app_id,
                                  tBTA_JV_CONN_STATE init_st) {
  inc_func_call_count(__func__);
  return 0;
}
tBTA_JV_STATUS BTA_JvStartDiscovery(const RawAddress& bd_addr,
                                    uint16_t num_uuid,
                                    const bluetooth::Uuid* p_uuid_list,
                                    uint32_t rfcomm_slot_id) {
  inc_func_call_count(__func__);
  return 0;
}
uint16_t BTA_JvRfcommGetPortHdl(uint32_t handle) {
  inc_func_call_count(__func__);
  return 0;
}
void BTA_JvDisable(void) { inc_func_call_count(__func__); }
void BTA_JvGetChannelId(int conn_type, uint32_t id, int32_t channel) {
  inc_func_call_count(__func__);
}
void BTA_JvL2capConnect(int conn_type, tBTA_SEC sec_mask, tBTA_JV_ROLE role,
                        std::unique_ptr<tL2CAP_ERTM_INFO> ertm_info,
                        uint16_t remote_psm, uint16_t rx_mtu,
                        std::unique_ptr<tL2CAP_CFG_INFO> cfg,
                        const RawAddress& peer_bd_addr,
                        tBTA_JV_L2CAP_CBACK* p_cback,
                        uint32_t l2cap_socket_id) {
  inc_func_call_count(__func__);
}
void BTA_JvL2capStartServer(int conn_type, tBTA_SEC sec_mask, tBTA_JV_ROLE role,
                            std::unique_ptr<tL2CAP_ERTM_INFO> ertm_info,
                            uint16_t local_psm, uint16_t rx_mtu,
                            std::unique_ptr<tL2CAP_CFG_INFO> cfg,
                            tBTA_JV_L2CAP_CBACK* p_cback,
                            uint32_t l2cap_socket_id) {
  inc_func_call_count(__func__);
}
