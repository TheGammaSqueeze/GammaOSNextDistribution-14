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
/*
 * Generated mock file from original source file
 *   Functions generated:24
 *
 *  mockcify.pl ver 0.6.3
 */

// Mock include file to share data between tests and mock
#include "test/mock/mock_bta_av_api.h"

#include <cstdint>

#include "test/common/mock_functions.h"

// Original usings

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace bta_av_api {

// Function state capture and return values, if needed
struct BTA_AvClose BTA_AvClose;
struct BTA_AvCloseRc BTA_AvCloseRc;
struct BTA_AvDeregister BTA_AvDeregister;
struct BTA_AvDisable BTA_AvDisable;
struct BTA_AvDisconnect BTA_AvDisconnect;
struct BTA_AvEnable BTA_AvEnable;
struct BTA_AvMetaCmd BTA_AvMetaCmd;
struct BTA_AvMetaRsp BTA_AvMetaRsp;
struct BTA_AvOffloadStart BTA_AvOffloadStart;
struct BTA_AvOffloadStartRsp BTA_AvOffloadStartRsp;
struct BTA_AvOpen BTA_AvOpen;
struct BTA_AvOpenRc BTA_AvOpenRc;
struct BTA_AvProtectReq BTA_AvProtectReq;
struct BTA_AvProtectRsp BTA_AvProtectRsp;
struct BTA_AvReconfig BTA_AvReconfig;
struct BTA_AvRegister BTA_AvRegister;
struct BTA_AvRemoteCmd BTA_AvRemoteCmd;
struct BTA_AvRemoteVendorUniqueCmd BTA_AvRemoteVendorUniqueCmd;
struct BTA_AvSetLatency BTA_AvSetLatency;
struct BTA_AvSetPeerSep BTA_AvSetPeerSep;
struct BTA_AvStart BTA_AvStart;
struct BTA_AvStop BTA_AvStop;
struct BTA_AvVendorCmd BTA_AvVendorCmd;
struct BTA_AvVendorRsp BTA_AvVendorRsp;

}  // namespace bta_av_api
}  // namespace mock
}  // namespace test

// Mocked function return values, if any
namespace test {
namespace mock {
namespace bta_av_api {}  // namespace bta_av_api
}  // namespace mock
}  // namespace test

// Mocked functions, if any
void BTA_AvClose(tBTA_AV_HNDL handle) {
  inc_func_call_count(__func__);
  test::mock::bta_av_api::BTA_AvClose(handle);
}
void BTA_AvCloseRc(uint8_t rc_handle) {
  inc_func_call_count(__func__);
  test::mock::bta_av_api::BTA_AvCloseRc(rc_handle);
}
void BTA_AvDeregister(tBTA_AV_HNDL hndl) {
  inc_func_call_count(__func__);
  test::mock::bta_av_api::BTA_AvDeregister(hndl);
}
void BTA_AvDisable(void) {
  inc_func_call_count(__func__);
  test::mock::bta_av_api::BTA_AvDisable();
}
void BTA_AvDisconnect(tBTA_AV_HNDL handle) {
  inc_func_call_count(__func__);
  test::mock::bta_av_api::BTA_AvDisconnect(handle);
}
void BTA_AvEnable(tBTA_AV_FEAT features, tBTA_AV_CBACK* p_cback) {
  inc_func_call_count(__func__);
  test::mock::bta_av_api::BTA_AvEnable(features, p_cback);
}
void BTA_AvMetaCmd(uint8_t rc_handle, uint8_t label, tBTA_AV_CMD cmd_code,
                   BT_HDR* p_pkt) {
  inc_func_call_count(__func__);
  test::mock::bta_av_api::BTA_AvMetaCmd(rc_handle, label, cmd_code, p_pkt);
}
void BTA_AvMetaRsp(uint8_t rc_handle, uint8_t label, tBTA_AV_CODE rsp_code,
                   BT_HDR* p_pkt) {
  inc_func_call_count(__func__);
  test::mock::bta_av_api::BTA_AvMetaRsp(rc_handle, label, rsp_code, p_pkt);
}
void BTA_AvOffloadStart(tBTA_AV_HNDL hndl) {
  inc_func_call_count(__func__);
  test::mock::bta_av_api::BTA_AvOffloadStart(hndl);
}
void BTA_AvOffloadStartRsp(tBTA_AV_HNDL hndl, tBTA_AV_STATUS status) {
  inc_func_call_count(__func__);
  test::mock::bta_av_api::BTA_AvOffloadStartRsp(hndl, status);
}
void BTA_AvOpen(const RawAddress& bd_addr, tBTA_AV_HNDL handle, bool use_rc,
                uint16_t uuid) {
  inc_func_call_count(__func__);
  test::mock::bta_av_api::BTA_AvOpen(bd_addr, handle, use_rc, uuid);
}
void BTA_AvOpenRc(tBTA_AV_HNDL handle) {
  inc_func_call_count(__func__);
  test::mock::bta_av_api::BTA_AvOpenRc(handle);
}
void BTA_AvProtectReq(tBTA_AV_HNDL hndl, uint8_t* p_data, uint16_t len) {
  inc_func_call_count(__func__);
  test::mock::bta_av_api::BTA_AvProtectReq(hndl, p_data, len);
}
void BTA_AvProtectRsp(tBTA_AV_HNDL hndl, uint8_t error_code, uint8_t* p_data,
                      uint16_t len) {
  inc_func_call_count(__func__);
  test::mock::bta_av_api::BTA_AvProtectRsp(hndl, error_code, p_data, len);
}
void BTA_AvReconfig(tBTA_AV_HNDL hndl, bool suspend, uint8_t sep_info_idx,
                    uint8_t* p_codec_info, uint8_t num_protect,
                    const uint8_t* p_protect_info) {
  inc_func_call_count(__func__);
  test::mock::bta_av_api::BTA_AvReconfig(
      hndl, suspend, sep_info_idx, p_codec_info, num_protect, p_protect_info);
}
void BTA_AvRegister(tBTA_AV_CHNL chnl, const char* p_service_name,
                    uint8_t app_id, tBTA_AV_SINK_DATA_CBACK* p_sink_data_cback,
                    uint16_t service_uuid) {
  inc_func_call_count(__func__);
  test::mock::bta_av_api::BTA_AvRegister(chnl, p_service_name, app_id,
                                         p_sink_data_cback, service_uuid);
}
void BTA_AvRemoteCmd(uint8_t rc_handle, uint8_t label, tBTA_AV_RC rc_id,
                     tBTA_AV_STATE key_state) {
  inc_func_call_count(__func__);
  test::mock::bta_av_api::BTA_AvRemoteCmd(rc_handle, label, rc_id, key_state);
}
void BTA_AvRemoteVendorUniqueCmd(uint8_t rc_handle, uint8_t label,
                                 tBTA_AV_STATE key_state, uint8_t* p_msg,
                                 uint8_t buf_len) {
  inc_func_call_count(__func__);
  test::mock::bta_av_api::BTA_AvRemoteVendorUniqueCmd(
      rc_handle, label, key_state, p_msg, buf_len);
}
void BTA_AvSetLatency(tBTA_AV_HNDL handle, bool is_low_latency) {
  inc_func_call_count(__func__);
  test::mock::bta_av_api::BTA_AvSetLatency(handle, is_low_latency);
}
void BTA_AvSetPeerSep(const RawAddress& bdaddr, uint8_t sep) {
  inc_func_call_count(__func__);
  test::mock::bta_av_api::BTA_AvSetPeerSep(bdaddr, sep);
}
void BTA_AvStart(tBTA_AV_HNDL handle, bool use_latency_mode) {
  inc_func_call_count(__func__);
  test::mock::bta_av_api::BTA_AvStart(handle, use_latency_mode);
}
void BTA_AvStop(tBTA_AV_HNDL handle, bool suspend) {
  inc_func_call_count(__func__);
  test::mock::bta_av_api::BTA_AvStop(handle, suspend);
}
void BTA_AvVendorCmd(uint8_t rc_handle, uint8_t label, tBTA_AV_CODE cmd_code,
                     uint8_t* p_data, uint16_t len) {
  inc_func_call_count(__func__);
  test::mock::bta_av_api::BTA_AvVendorCmd(rc_handle, label, cmd_code, p_data,
                                          len);
}
void BTA_AvVendorRsp(uint8_t rc_handle, uint8_t label, tBTA_AV_CODE rsp_code,
                     uint8_t* p_data, uint16_t len, uint32_t company_id) {
  inc_func_call_count(__func__);
  test::mock::bta_av_api::BTA_AvVendorRsp(rc_handle, label, rsp_code, p_data,
                                          len, company_id);
}
// Mocked functions complete
// END mockcify generation
