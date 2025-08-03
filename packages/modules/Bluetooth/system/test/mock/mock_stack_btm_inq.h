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
 *   Functions generated:43
 *
 *  mockcify.pl ver 0.6.0
 */

#include <cstdint>
#include <functional>

// Original included files, if any
#include <base/logging.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "stack/btm/neighbor_inquiry.h"
#include "stack/include/bt_hdr.h"
#include "types/bluetooth/uuid.h"
#include "types/bt_transport.h"
#include "types/raw_address.h"

// Original usings
using bluetooth::Uuid;

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace stack_btm_inq {

// Shared state between mocked functions and tests
// Name: BTM_AddEirService
// Params: uint32_t* p_eir_uuid, uint16_t uuid16
// Return: void
struct BTM_AddEirService {
  std::function<void(uint32_t* p_eir_uuid, uint16_t uuid16)> body{
      [](uint32_t* /* p_eir_uuid */, uint16_t /* uuid16 */) {}};
  void operator()(uint32_t* p_eir_uuid, uint16_t uuid16) {
    body(p_eir_uuid, uuid16);
  };
};
extern struct BTM_AddEirService BTM_AddEirService;

// Name: BTM_CancelInquiry
// Params: void
// Return: void
struct BTM_CancelInquiry {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct BTM_CancelInquiry BTM_CancelInquiry;

// Name: BTM_CancelRemoteDeviceName
// Params: void
// Return: tBTM_STATUS
struct BTM_CancelRemoteDeviceName {
  static tBTM_STATUS return_value;
  std::function<tBTM_STATUS(void)> body{[](void) { return return_value; }};
  tBTM_STATUS operator()(void) { return body(); };
};
extern struct BTM_CancelRemoteDeviceName BTM_CancelRemoteDeviceName;

// Name: BTM_ClearInqDb
// Params: const RawAddress* p_bda
// Return: tBTM_STATUS
struct BTM_ClearInqDb {
  static tBTM_STATUS return_value;
  std::function<tBTM_STATUS(const RawAddress* p_bda)> body{
      [](const RawAddress* /* p_bda */) { return return_value; }};
  tBTM_STATUS operator()(const RawAddress* p_bda) { return body(p_bda); };
};
extern struct BTM_ClearInqDb BTM_ClearInqDb;

// Name: BTM_EnableInterlacedInquiryScan
// Params:
// Return: void
struct BTM_EnableInterlacedInquiryScan {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct BTM_EnableInterlacedInquiryScan BTM_EnableInterlacedInquiryScan;

// Name: BTM_EnableInterlacedPageScan
// Params:
// Return: void
struct BTM_EnableInterlacedPageScan {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct BTM_EnableInterlacedPageScan BTM_EnableInterlacedPageScan;

// Name: BTM_GetEirSupportedServices
// Params: uint32_t* p_eir_uuid, uint8_t** p, uint8_t max_num_uuid16, uint8_t*
// p_num_uuid16 Return: uint8_t
struct BTM_GetEirSupportedServices {
  static uint8_t return_value;
  std::function<uint8_t(uint32_t* p_eir_uuid, uint8_t** p,
                        uint8_t max_num_uuid16, uint8_t* p_num_uuid16)>
      body{[](uint32_t* /* p_eir_uuid */, uint8_t** /* p */,
              uint8_t /* max_num_uuid16 */,
              uint8_t* /* p_num_uuid16 */) { return return_value; }};
  uint8_t operator()(uint32_t* p_eir_uuid, uint8_t** p, uint8_t max_num_uuid16,
                     uint8_t* p_num_uuid16) {
    return body(p_eir_uuid, p, max_num_uuid16, p_num_uuid16);
  };
};
extern struct BTM_GetEirSupportedServices BTM_GetEirSupportedServices;

// Name: BTM_GetEirUuidList
// Params: const uint8_t* p_eir, size_t eir_len, uint8_t uuid_size, uint8_t*
// p_num_uuid, uint8_t* p_uuid_list, uint8_t max_num_uuid Return: uint8_t
struct BTM_GetEirUuidList {
  static uint8_t return_value;
  std::function<uint8_t(const uint8_t* p_eir, size_t eir_len, uint8_t uuid_size,
                        uint8_t* p_num_uuid, uint8_t* p_uuid_list,
                        uint8_t max_num_uuid)>
      body{[](const uint8_t* /* p_eir */, size_t /* eir_len */,
              uint8_t /* uuid_size */, uint8_t* /* p_num_uuid */,
              uint8_t* /* p_uuid_list */,
              uint8_t /* max_num_uuid */) { return return_value; }};
  uint8_t operator()(const uint8_t* p_eir, size_t eir_len, uint8_t uuid_size,
                     uint8_t* p_num_uuid, uint8_t* p_uuid_list,
                     uint8_t max_num_uuid) {
    return body(p_eir, eir_len, uuid_size, p_num_uuid, p_uuid_list,
                max_num_uuid);
  };
};
extern struct BTM_GetEirUuidList BTM_GetEirUuidList;

// Name: BTM_HasEirService
// Params: const uint32_t* p_eir_uuid, uint16_t uuid16
// Return: bool
struct BTM_HasEirService {
  static bool return_value;
  std::function<bool(const uint32_t* p_eir_uuid, uint16_t uuid16)> body{
      [](const uint32_t* /* p_eir_uuid */, uint16_t /* uuid16 */) {
        return return_value;
      }};
  bool operator()(const uint32_t* p_eir_uuid, uint16_t uuid16) {
    return body(p_eir_uuid, uuid16);
  };
};
extern struct BTM_HasEirService BTM_HasEirService;

// Name: BTM_InqDbFirst
// Params: void
// Return: tBTM_INQ_INFO*
struct BTM_InqDbFirst {
  static tBTM_INQ_INFO* return_value;
  std::function<tBTM_INQ_INFO*(void)> body{[](void) { return return_value; }};
  tBTM_INQ_INFO* operator()(void) { return body(); };
};
extern struct BTM_InqDbFirst BTM_InqDbFirst;

// Name: BTM_InqDbNext
// Params: tBTM_INQ_INFO* p_cur
// Return: tBTM_INQ_INFO*
struct BTM_InqDbNext {
  static tBTM_INQ_INFO* return_value;
  std::function<tBTM_INQ_INFO*(tBTM_INQ_INFO* p_cur)> body{
      [](tBTM_INQ_INFO* /* p_cur */) { return return_value; }};
  tBTM_INQ_INFO* operator()(tBTM_INQ_INFO* p_cur) { return body(p_cur); };
};
extern struct BTM_InqDbNext BTM_InqDbNext;

// Name: BTM_InqDbRead
// Params: const RawAddress& p_bda
// Return: tBTM_INQ_INFO*
struct BTM_InqDbRead {
  static tBTM_INQ_INFO* return_value;
  std::function<tBTM_INQ_INFO*(const RawAddress& p_bda)> body{
      [](const RawAddress& /* p_bda */) { return return_value; }};
  tBTM_INQ_INFO* operator()(const RawAddress& p_bda) { return body(p_bda); };
};
extern struct BTM_InqDbRead BTM_InqDbRead;

// Name: BTM_IsInquiryActive
// Params: void
// Return: uint16_t
struct BTM_IsInquiryActive {
  static uint16_t return_value;
  std::function<uint16_t(void)> body{[](void) { return return_value; }};
  uint16_t operator()(void) { return body(); };
};
extern struct BTM_IsInquiryActive BTM_IsInquiryActive;

// Name: BTM_ReadRemoteDeviceName
// Params: const RawAddress& remote_bda, tBTM_NAME_CMPL_CB* p_cb, tBT_TRANSPORT
// transport Return: tBTM_STATUS
struct BTM_ReadRemoteDeviceName {
  static tBTM_STATUS return_value;
  std::function<tBTM_STATUS(const RawAddress& remote_bda,
                            tBTM_NAME_CMPL_CB* p_cb, tBT_TRANSPORT transport)>
      body{[](const RawAddress& /* remote_bda */, tBTM_NAME_CMPL_CB* /* p_cb */,
              tBT_TRANSPORT /* transport */) { return return_value; }};
  tBTM_STATUS operator()(const RawAddress& remote_bda, tBTM_NAME_CMPL_CB* p_cb,
                         tBT_TRANSPORT transport) {
    return body(remote_bda, p_cb, transport);
  };
};
extern struct BTM_ReadRemoteDeviceName BTM_ReadRemoteDeviceName;

// Name: BTM_RemoveEirService
// Params: uint32_t* p_eir_uuid, uint16_t uuid16
// Return: void
struct BTM_RemoveEirService {
  std::function<void(uint32_t* p_eir_uuid, uint16_t uuid16)> body{
      [](uint32_t* /* p_eir_uuid */, uint16_t /* uuid16 */) {}};
  void operator()(uint32_t* p_eir_uuid, uint16_t uuid16) {
    body(p_eir_uuid, uuid16);
  };
};
extern struct BTM_RemoveEirService BTM_RemoveEirService;

// Name: BTM_SetConnectability
// Params: uint16_t page_mode
// Return: tBTM_STATUS
struct BTM_SetConnectability {
  static tBTM_STATUS return_value;
  std::function<tBTM_STATUS(uint16_t page_mode)> body{
      [](uint16_t /* page_mode */) { return return_value; }};
  tBTM_STATUS operator()(uint16_t page_mode) { return body(page_mode); };
};
extern struct BTM_SetConnectability BTM_SetConnectability;

// Name: BTM_SetDiscoverability
// Params: uint16_t inq_mode
// Return: tBTM_STATUS
struct BTM_SetDiscoverability {
  static tBTM_STATUS return_value;
  std::function<tBTM_STATUS(uint16_t inq_mode)> body{
      [](uint16_t /* inq_mode */) { return return_value; }};
  tBTM_STATUS operator()(uint16_t inq_mode) { return body(inq_mode); };
};
extern struct BTM_SetDiscoverability BTM_SetDiscoverability;

// Name: BTM_SetInquiryMode
// Params: uint8_t mode
// Return: tBTM_STATUS
struct BTM_SetInquiryMode {
  static tBTM_STATUS return_value;
  std::function<tBTM_STATUS(uint8_t mode)> body{
      [](uint8_t /* mode */) { return return_value; }};
  tBTM_STATUS operator()(uint8_t mode) { return body(mode); };
};
extern struct BTM_SetInquiryMode BTM_SetInquiryMode;

// Name: BTM_StartInquiry
// Params: tBTM_INQ_RESULTS_CB* p_results_cb, tBTM_CMPL_CB* p_cmpl_cb
// Return: tBTM_STATUS
struct BTM_StartInquiry {
  static tBTM_STATUS return_value;
  std::function<tBTM_STATUS(tBTM_INQ_RESULTS_CB* p_results_cb,
                            tBTM_CMPL_CB* p_cmpl_cb)>
      body{[](tBTM_INQ_RESULTS_CB* /* p_results_cb */,
              tBTM_CMPL_CB* /* p_cmpl_cb */) { return return_value; }};
  tBTM_STATUS operator()(tBTM_INQ_RESULTS_CB* p_results_cb,
                         tBTM_CMPL_CB* p_cmpl_cb) {
    return body(p_results_cb, p_cmpl_cb);
  };
};
extern struct BTM_StartInquiry BTM_StartInquiry;

// Name: BTM_WriteEIR
// Params: BT_HDR* p_buff
// Return: tBTM_STATUS
struct BTM_WriteEIR {
  static tBTM_STATUS return_value;
  std::function<tBTM_STATUS(BT_HDR* p_buff)> body{
      [](BT_HDR* /* p_buff */) { return return_value; }};
  tBTM_STATUS operator()(BT_HDR* p_buff) { return body(p_buff); };
};
extern struct BTM_WriteEIR BTM_WriteEIR;

// Name: SendRemoteNameRequest
// Params: const RawAddress& raw_address
// Return: void
struct SendRemoteNameRequest {
  std::function<void(const RawAddress& raw_address)> body{
      [](const RawAddress& /* raw_address */) {}};
  void operator()(const RawAddress& raw_address) { body(raw_address); };
};
extern struct SendRemoteNameRequest SendRemoteNameRequest;

// Name: btm_clear_all_pending_le_entry
// Params: void
// Return: void
struct btm_clear_all_pending_le_entry {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct btm_clear_all_pending_le_entry btm_clear_all_pending_le_entry;

// Name: btm_clr_inq_db
// Params: const RawAddress* p_bda
// Return: void
struct btm_clr_inq_db {
  std::function<void(const RawAddress* p_bda)> body{
      [](const RawAddress* /* p_bda */) {}};
  void operator()(const RawAddress* p_bda) { body(p_bda); };
};
extern struct btm_clr_inq_db btm_clr_inq_db;

// Name: btm_clr_inq_result_flt
// Params: void
// Return: void
struct btm_clr_inq_result_flt {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct btm_clr_inq_result_flt btm_clr_inq_result_flt;

// Name: btm_inq_clear_ssp
// Params: void
// Return: void
struct btm_inq_clear_ssp {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct btm_inq_clear_ssp btm_inq_clear_ssp;

// Name: btm_inq_db_find
// Params: const RawAddress& p_bda
// Return: tINQ_DB_ENT*
struct btm_inq_db_find {
  static tINQ_DB_ENT* return_value;
  std::function<tINQ_DB_ENT*(const RawAddress& p_bda)> body{
      [](const RawAddress& /* p_bda */) { return return_value; }};
  tINQ_DB_ENT* operator()(const RawAddress& p_bda) { return body(p_bda); };
};
extern struct btm_inq_db_find btm_inq_db_find;

// Name: btm_inq_db_free
// Params: void
// Return: void
struct btm_inq_db_free {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct btm_inq_db_free btm_inq_db_free;

// Name: btm_inq_db_init
// Params: void
// Return: void
struct btm_inq_db_init {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct btm_inq_db_init btm_inq_db_init;

// Name: btm_inq_db_new
// Params: const RawAddress& p_bda
// Return: tINQ_DB_ENT*
struct btm_inq_db_new {
  static tINQ_DB_ENT* return_value;
  std::function<tINQ_DB_ENT*(const RawAddress& p_bda, bool is_ble)> body{
      [](const RawAddress& /* p_bda */, bool /* is_ble */) {
        return return_value;
      }};
  tINQ_DB_ENT* operator()(const RawAddress& p_bda, bool is_ble) { return body(p_bda, is_ble); };
};
extern struct btm_inq_db_new btm_inq_db_new;

// Name: btm_inq_db_reset
// Params: void
// Return: void
struct btm_inq_db_reset {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct btm_inq_db_reset btm_inq_db_reset;

// Name: btm_inq_find_bdaddr
// Params: const RawAddress& p_bda
// Return: bool
struct btm_inq_find_bdaddr {
  static bool return_value;
  std::function<bool(const RawAddress& p_bda)> body{
      [](const RawAddress& /* p_bda */) { return return_value; }};
  bool operator()(const RawAddress& p_bda) { return body(p_bda); };
};
extern struct btm_inq_find_bdaddr btm_inq_find_bdaddr;

// Name: btm_inq_remote_name_timer_timeout
// Params:  void* data
// Return: void
struct btm_inq_remote_name_timer_timeout {
  std::function<void(void* data)> body{[](void* /* data */) {}};
  void operator()(void* data) { body(data); };
};
extern struct btm_inq_remote_name_timer_timeout
    btm_inq_remote_name_timer_timeout;

// Name: btm_inq_rmt_name_failed_cancelled
// Params: void
// Return: void
struct btm_inq_rmt_name_failed_cancelled {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct btm_inq_rmt_name_failed_cancelled
    btm_inq_rmt_name_failed_cancelled;

// Name: btm_inq_stop_on_ssp
// Params: void
// Return: void
struct btm_inq_stop_on_ssp {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct btm_inq_stop_on_ssp btm_inq_stop_on_ssp;

// Name: btm_process_inq_complete
// Params: tHCI_STATUS status, uint8_t mode
// Return: void
struct btm_process_inq_complete {
  std::function<void(tHCI_STATUS status, uint8_t mode)> body{
      [](tHCI_STATUS /* status */, uint8_t /* mode */) {}};
  void operator()(tHCI_STATUS status, uint8_t mode) { body(status, mode); };
};
extern struct btm_process_inq_complete btm_process_inq_complete;

// Name: btm_process_remote_name
// Params: const RawAddress* bda, const BD_NAME bdn, uint16_t evt_len,
// tHCI_STATUS hci_status Return: void
struct btm_process_remote_name {
  std::function<void(const RawAddress* bda, const BD_NAME bdn, uint16_t evt_len,
                     tHCI_STATUS hci_status)>
      body{[](const RawAddress* /* bda */, const BD_NAME /* bdn */,
              uint16_t /* evt_len */, tHCI_STATUS /* hci_status */) {}};
  void operator()(const RawAddress* bda, const BD_NAME bdn, uint16_t evt_len,
                  tHCI_STATUS hci_status) {
    body(bda, bdn, evt_len, hci_status);
  };
};
extern struct btm_process_remote_name btm_process_remote_name;

// Name: btm_set_eir_uuid
// Params: const uint8_t* p_eir, tBTM_INQ_RESULTS* p_results
// Return: void
struct btm_set_eir_uuid {
  std::function<void(const uint8_t* p_eir, tBTM_INQ_RESULTS* p_results)> body{
      [](const uint8_t* /* p_eir */, tBTM_INQ_RESULTS* /* p_results */) {}};
  void operator()(const uint8_t* p_eir, tBTM_INQ_RESULTS* p_results) {
    body(p_eir, p_results);
  };
};
extern struct btm_set_eir_uuid btm_set_eir_uuid;

// Name: btm_sort_inq_result
// Params: void
// Return: void
struct btm_sort_inq_result {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct btm_sort_inq_result btm_sort_inq_result;

}  // namespace stack_btm_inq
}  // namespace mock
}  // namespace test

// END mockcify generation
