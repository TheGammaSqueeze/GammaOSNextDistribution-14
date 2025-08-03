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
 *   Functions generated:21
 */

#include <cstdint>

#include "bta/av/bta_av_int.h"
#include "stack/include/bt_hdr.h"
#include "test/common/mock_functions.h"
#include "types/raw_address.h"

bool bta_av_chk_start(tBTA_AV_SCB* p_scb) {
  inc_func_call_count(__func__);
  return false;
}
bool bta_av_hdl_event(const BT_HDR_RIGID* p_msg) {
  inc_func_call_count(__func__);
  return false;
}
bool bta_av_link_role_ok(tBTA_AV_SCB* p_scb, uint8_t bits) {
  inc_func_call_count(__func__);
  return false;
}
bool bta_av_switch_if_needed(tBTA_AV_SCB* p_scb) {
  inc_func_call_count(__func__);
  return false;
}
const char* bta_av_evt_code(uint16_t evt_code) {
  inc_func_call_count(__func__);
  return nullptr;
}
int BTA_AvObtainPeerChannelIndex(const RawAddress& peer_address) {
  inc_func_call_count(__func__);
  return 0;
}
tBTA_AV_SCB* bta_av_addr_to_scb(const RawAddress& bd_addr) {
  inc_func_call_count(__func__);
  return nullptr;
}
tBTA_AV_SCB* bta_av_hndl_to_scb(uint16_t handle) {
  inc_func_call_count(__func__);
  return nullptr;
}
void bta_av_api_deregister(tBTA_AV_DATA* p_data) {
  inc_func_call_count(__func__);
}
void bta_av_conn_cback(uint8_t handle, const RawAddress& bd_addr, uint8_t event,
                       tAVDT_CTRL* p_data, uint8_t scb_index) {
  inc_func_call_count(__func__);
}
void bta_av_dup_audio_buf(tBTA_AV_SCB* p_scb, BT_HDR* p_buf) {
  inc_func_call_count(__func__);
}
void bta_av_free_scb(tBTA_AV_SCB* p_scb) { inc_func_call_count(__func__); }
void bta_av_restore_switch(void) { inc_func_call_count(__func__); }
void bta_av_sm_execute(tBTA_AV_CB* p_cb, uint16_t event, tBTA_AV_DATA* p_data) {
  inc_func_call_count(__func__);
}
void bta_debug_av_dump(int fd) { inc_func_call_count(__func__); }
void tBTA_AV_SCB::OnConnected(const RawAddress& peer_address) {
  inc_func_call_count(__func__);
}
void tBTA_AV_SCB::OnDisconnected() { inc_func_call_count(__func__); }
void tBTA_AV_SCB::SetAvdtpVersion(uint16_t avdtp_version) {
  inc_func_call_count(__func__);
}
