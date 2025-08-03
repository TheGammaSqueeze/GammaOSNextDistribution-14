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
#pragma once

/*
 * Generated mock file from original source file
 *   Functions generated:24
 *
 *  mockcify.pl ver 0.6.3
 */

#include <cstdint>
#include <functional>

// Original included files, if any
// NOTE: Since this is a mock file with mock definitions some number of
//       include files may not be required.  The include-what-you-use
//       still applies, but crafting proper inclusion is out of scope
//       for this effort.  This compilation unit may compile as-is, or
//       may need attention to prune from (or add to ) the inclusion set.
#include "bta/av/bta_av_int.h"
#include "btif/include/btif_av.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "osi/include/compat.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/bt_uuid16.h"
#include "types/raw_address.h"

// Original usings

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace bta_av_api {

// Shared state between mocked functions and tests
// Name: BTA_AvClose
// Params: tBTA_AV_HNDL handle
// Return: void
struct BTA_AvClose {
  std::function<void(tBTA_AV_HNDL handle)> body{
      [](tBTA_AV_HNDL /* handle */) {}};
  void operator()(tBTA_AV_HNDL handle) { body(handle); };
};
extern struct BTA_AvClose BTA_AvClose;

// Name: BTA_AvCloseRc
// Params: uint8_t rc_handle
// Return: void
struct BTA_AvCloseRc {
  std::function<void(uint8_t rc_handle)> body{[](uint8_t /* rc_handle */) {}};
  void operator()(uint8_t rc_handle) { body(rc_handle); };
};
extern struct BTA_AvCloseRc BTA_AvCloseRc;

// Name: BTA_AvDeregister
// Params: tBTA_AV_HNDL hndl
// Return: void
struct BTA_AvDeregister {
  std::function<void(tBTA_AV_HNDL hndl)> body{[](tBTA_AV_HNDL /* hndl */) {}};
  void operator()(tBTA_AV_HNDL hndl) { body(hndl); };
};
extern struct BTA_AvDeregister BTA_AvDeregister;

// Name: BTA_AvDisable
// Params: void
// Return: void
struct BTA_AvDisable {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct BTA_AvDisable BTA_AvDisable;

// Name: BTA_AvDisconnect
// Params: tBTA_AV_HNDL handle
// Return: void
struct BTA_AvDisconnect {
  std::function<void(tBTA_AV_HNDL handle)> body{
      [](tBTA_AV_HNDL /* handle */) {}};
  void operator()(tBTA_AV_HNDL handle) { body(handle); };
};
extern struct BTA_AvDisconnect BTA_AvDisconnect;

// Name: BTA_AvEnable
// Params: tBTA_AV_FEAT features, tBTA_AV_CBACK* p_cback
// Return: void
struct BTA_AvEnable {
  std::function<void(tBTA_AV_FEAT features, tBTA_AV_CBACK* p_cback)> body{
      [](tBTA_AV_FEAT /* features */, tBTA_AV_CBACK* /* p_cback */) {}};
  void operator()(tBTA_AV_FEAT features, tBTA_AV_CBACK* p_cback) {
    body(features, p_cback);
  };
};
extern struct BTA_AvEnable BTA_AvEnable;

// Name: BTA_AvMetaCmd
// Params: uint8_t rc_handle, uint8_t label, tBTA_AV_CMD cmd_code, BT_HDR* p_pkt
// Return: void
struct BTA_AvMetaCmd {
  std::function<void(uint8_t rc_handle, uint8_t label, tBTA_AV_CMD cmd_code,
                     BT_HDR* p_pkt)>
      body{[](uint8_t /* rc_handle */, uint8_t /* label */,
              tBTA_AV_CMD /* cmd_code */, BT_HDR* /* p_pkt */) {}};
  void operator()(uint8_t rc_handle, uint8_t label, tBTA_AV_CMD cmd_code,
                  BT_HDR* p_pkt) {
    body(rc_handle, label, cmd_code, p_pkt);
  };
};
extern struct BTA_AvMetaCmd BTA_AvMetaCmd;

// Name: BTA_AvMetaRsp
// Params: uint8_t rc_handle, uint8_t label, tBTA_AV_CODE rsp_code, BT_HDR*
// p_pkt Return: void
struct BTA_AvMetaRsp {
  std::function<void(uint8_t rc_handle, uint8_t label, tBTA_AV_CODE rsp_code,
                     BT_HDR* p_pkt)>
      body{[](uint8_t /* rc_handle */, uint8_t /* label */,
              tBTA_AV_CODE /* rsp_code */, BT_HDR* /* p_pkt */) {}};
  void operator()(uint8_t rc_handle, uint8_t label, tBTA_AV_CODE rsp_code,
                  BT_HDR* p_pkt) {
    body(rc_handle, label, rsp_code, p_pkt);
  };
};
extern struct BTA_AvMetaRsp BTA_AvMetaRsp;

// Name: BTA_AvOffloadStart
// Params: tBTA_AV_HNDL hndl
// Return: void
struct BTA_AvOffloadStart {
  std::function<void(tBTA_AV_HNDL hndl)> body{[](tBTA_AV_HNDL /* hndl */) {}};
  void operator()(tBTA_AV_HNDL hndl) { body(hndl); };
};
extern struct BTA_AvOffloadStart BTA_AvOffloadStart;

// Name: BTA_AvOffloadStartRsp
// Params: tBTA_AV_HNDL hndl, tBTA_AV_STATUS status
// Return: void
struct BTA_AvOffloadStartRsp {
  std::function<void(tBTA_AV_HNDL hndl, tBTA_AV_STATUS status)> body{
      [](tBTA_AV_HNDL /* hndl */, tBTA_AV_STATUS /* status */) {}};
  void operator()(tBTA_AV_HNDL hndl, tBTA_AV_STATUS status) {
    body(hndl, status);
  };
};
extern struct BTA_AvOffloadStartRsp BTA_AvOffloadStartRsp;

// Name: BTA_AvOpen
// Params: const RawAddress& bd_addr, tBTA_AV_HNDL handle, bool use_rc, uint16_t
// uuid Return: void
struct BTA_AvOpen {
  std::function<void(const RawAddress& bd_addr, tBTA_AV_HNDL handle,
                     bool use_rc, uint16_t uuid)>
      body{[](const RawAddress& /* bd_addr */, tBTA_AV_HNDL /* handle */,
              bool /* use_rc */, uint16_t /* uuid */) {}};
  void operator()(const RawAddress& bd_addr, tBTA_AV_HNDL handle, bool use_rc,
                  uint16_t uuid) {
    body(bd_addr, handle, use_rc, uuid);
  };
};
extern struct BTA_AvOpen BTA_AvOpen;

// Name: BTA_AvOpenRc
// Params: tBTA_AV_HNDL handle
// Return: void
struct BTA_AvOpenRc {
  std::function<void(tBTA_AV_HNDL handle)> body{
      [](tBTA_AV_HNDL /* handle */) {}};
  void operator()(tBTA_AV_HNDL handle) { body(handle); };
};
extern struct BTA_AvOpenRc BTA_AvOpenRc;

// Name: BTA_AvProtectReq
// Params: tBTA_AV_HNDL hndl, uint8_t* p_data, uint16_t len
// Return: void
struct BTA_AvProtectReq {
  std::function<void(tBTA_AV_HNDL hndl, uint8_t* p_data, uint16_t len)> body{
      [](tBTA_AV_HNDL /* hndl */, uint8_t* /* p_data */, uint16_t /* len */) {
      }};
  void operator()(tBTA_AV_HNDL hndl, uint8_t* p_data, uint16_t len) {
    body(hndl, p_data, len);
  };
};
extern struct BTA_AvProtectReq BTA_AvProtectReq;

// Name: BTA_AvProtectRsp
// Params: tBTA_AV_HNDL hndl, uint8_t error_code, uint8_t* p_data, uint16_t len
// Return: void
struct BTA_AvProtectRsp {
  std::function<void(tBTA_AV_HNDL hndl, uint8_t error_code, uint8_t* p_data,
                     uint16_t len)>
      body{[](tBTA_AV_HNDL /* hndl */, uint8_t /* error_code */,
              uint8_t* /* p_data */, uint16_t /* len */) {}};
  void operator()(tBTA_AV_HNDL hndl, uint8_t error_code, uint8_t* p_data,
                  uint16_t len) {
    body(hndl, error_code, p_data, len);
  };
};
extern struct BTA_AvProtectRsp BTA_AvProtectRsp;

// Name: BTA_AvReconfig
// Params: tBTA_AV_HNDL hndl, bool suspend, uint8_t sep_info_idx, uint8_t*
// p_codec_info, uint8_t num_protect, const uint8_t* p_protect_info Return: void
struct BTA_AvReconfig {
  std::function<void(tBTA_AV_HNDL hndl, bool suspend, uint8_t sep_info_idx,
                     uint8_t* p_codec_info, uint8_t num_protect,
                     const uint8_t* p_protect_info)>
      body{[](tBTA_AV_HNDL /* hndl */, bool /* suspend */,
              uint8_t /* sep_info_idx */, uint8_t* /* p_codec_info */,
              uint8_t /* num_protect */,
              const uint8_t* /* p_protect_info */) {}};
  void operator()(tBTA_AV_HNDL hndl, bool suspend, uint8_t sep_info_idx,
                  uint8_t* p_codec_info, uint8_t num_protect,
                  const uint8_t* p_protect_info) {
    body(hndl, suspend, sep_info_idx, p_codec_info, num_protect,
         p_protect_info);
  };
};
extern struct BTA_AvReconfig BTA_AvReconfig;

// Name: BTA_AvRegister
// Params: tBTA_AV_CHNL chnl, const char* p_service_name, uint8_t app_id,
// tBTA_AV_SINK_DATA_CBACK* p_sink_data_cback, uint16_t service_uuid Return:
// void
struct BTA_AvRegister {
  std::function<void(tBTA_AV_CHNL chnl, const char* p_service_name,
                     uint8_t app_id, tBTA_AV_SINK_DATA_CBACK* p_sink_data_cback,
                     uint16_t service_uuid)>
      body{[](tBTA_AV_CHNL /* chnl */, const char* /* p_service_name */,
              uint8_t /* app_id */,
              tBTA_AV_SINK_DATA_CBACK* /* p_sink_data_cback */,
              uint16_t /* service_uuid */) {}};
  void operator()(tBTA_AV_CHNL chnl, const char* p_service_name, uint8_t app_id,
                  tBTA_AV_SINK_DATA_CBACK* p_sink_data_cback,
                  uint16_t service_uuid) {
    body(chnl, p_service_name, app_id, p_sink_data_cback, service_uuid);
  };
};
extern struct BTA_AvRegister BTA_AvRegister;

// Name: BTA_AvRemoteCmd
// Params: uint8_t rc_handle, uint8_t label, tBTA_AV_RC rc_id, tBTA_AV_STATE
// key_state Return: void
struct BTA_AvRemoteCmd {
  std::function<void(uint8_t rc_handle, uint8_t label, tBTA_AV_RC rc_id,
                     tBTA_AV_STATE key_state)>
      body{[](uint8_t /* rc_handle */, uint8_t /* label */,
              tBTA_AV_RC /* rc_id */, tBTA_AV_STATE /* key_state */) {}};
  void operator()(uint8_t rc_handle, uint8_t label, tBTA_AV_RC rc_id,
                  tBTA_AV_STATE key_state) {
    body(rc_handle, label, rc_id, key_state);
  };
};
extern struct BTA_AvRemoteCmd BTA_AvRemoteCmd;

// Name: BTA_AvRemoteVendorUniqueCmd
// Params: uint8_t rc_handle, uint8_t label, tBTA_AV_STATE key_state, uint8_t*
// p_msg, uint8_t buf_len Return: void
struct BTA_AvRemoteVendorUniqueCmd {
  std::function<void(uint8_t rc_handle, uint8_t label, tBTA_AV_STATE key_state,
                     uint8_t* p_msg, uint8_t buf_len)>
      body{[](uint8_t /* rc_handle */, uint8_t /* label */,
              tBTA_AV_STATE /* key_state */, uint8_t* /* p_msg */,
              uint8_t /* buf_len */) {}};
  void operator()(uint8_t rc_handle, uint8_t label, tBTA_AV_STATE key_state,
                  uint8_t* p_msg, uint8_t buf_len) {
    body(rc_handle, label, key_state, p_msg, buf_len);
  };
};
extern struct BTA_AvRemoteVendorUniqueCmd BTA_AvRemoteVendorUniqueCmd;

// Name: BTA_AvSetLatency
// Params: tBTA_AV_HNDL handle, bool is_low_latency
// Return: void
struct BTA_AvSetLatency {
  std::function<void(tBTA_AV_HNDL handle, bool is_low_latency)> body{
      [](tBTA_AV_HNDL /* handle */, bool /* is_low_latency */) {}};
  void operator()(tBTA_AV_HNDL handle, bool is_low_latency) {
    body(handle, is_low_latency);
  };
};
extern struct BTA_AvSetLatency BTA_AvSetLatency;

// Name: BTA_AvSetPeerSep
// Params: const RawAddress& bdaddr, uint8_t sep
// Return: void
struct BTA_AvSetPeerSep {
  std::function<void(const RawAddress& bdaddr, uint8_t sep)> body{
      [](const RawAddress& /* bdaddr */, uint8_t /* sep */) {}};
  void operator()(const RawAddress& bdaddr, uint8_t sep) { body(bdaddr, sep); };
};
extern struct BTA_AvSetPeerSep BTA_AvSetPeerSep;

// Name: BTA_AvStart
// Params: tBTA_AV_HNDL handle, bool use_latency_mode
// Return: void
struct BTA_AvStart {
  std::function<void(tBTA_AV_HNDL handle, bool use_latency_mode)> body{
      [](tBTA_AV_HNDL /* handle */, bool /* use_latency_mode */) {}};
  void operator()(tBTA_AV_HNDL handle, bool use_latency_mode) {
    body(handle, use_latency_mode);
  };
};
extern struct BTA_AvStart BTA_AvStart;

// Name: BTA_AvStop
// Params: tBTA_AV_HNDL handle, bool suspend
// Return: void
struct BTA_AvStop {
  std::function<void(tBTA_AV_HNDL handle, bool suspend)> body{
      [](tBTA_AV_HNDL /* handle */, bool /* suspend */) {}};
  void operator()(tBTA_AV_HNDL handle, bool suspend) { body(handle, suspend); };
};
extern struct BTA_AvStop BTA_AvStop;

// Name: BTA_AvVendorCmd
// Params: uint8_t rc_handle, uint8_t label, tBTA_AV_CODE cmd_code, uint8_t*
// p_data, uint16_t len Return: void
struct BTA_AvVendorCmd {
  std::function<void(uint8_t rc_handle, uint8_t label, tBTA_AV_CODE cmd_code,
                     uint8_t* p_data, uint16_t len)>
      body{[](uint8_t /* rc_handle */, uint8_t /* label */,
              tBTA_AV_CODE /* cmd_code */, uint8_t* /* p_data */,
              uint16_t /* len */) {}};
  void operator()(uint8_t rc_handle, uint8_t label, tBTA_AV_CODE cmd_code,
                  uint8_t* p_data, uint16_t len) {
    body(rc_handle, label, cmd_code, p_data, len);
  };
};
extern struct BTA_AvVendorCmd BTA_AvVendorCmd;

// Name: BTA_AvVendorRsp
// Params: uint8_t rc_handle, uint8_t label, tBTA_AV_CODE rsp_code, uint8_t*
// p_data, uint16_t len, uint32_t company_id Return: void
struct BTA_AvVendorRsp {
  std::function<void(uint8_t rc_handle, uint8_t label, tBTA_AV_CODE rsp_code,
                     uint8_t* p_data, uint16_t len, uint32_t company_id)>
      body{[](uint8_t /* rc_handle */, uint8_t /* label */,
              tBTA_AV_CODE /* rsp_code */, uint8_t* /* p_data */,
              uint16_t /* len */, uint32_t /* company_id */) {}};
  void operator()(uint8_t rc_handle, uint8_t label, tBTA_AV_CODE rsp_code,
                  uint8_t* p_data, uint16_t len, uint32_t company_id) {
    body(rc_handle, label, rsp_code, p_data, len, company_id);
  };
};
extern struct BTA_AvVendorRsp BTA_AvVendorRsp;

}  // namespace bta_av_api
}  // namespace mock
}  // namespace test

// END mockcify generation
