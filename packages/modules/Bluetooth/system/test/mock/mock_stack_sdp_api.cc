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
 *   Functions generated:18
 *
 *  mockcify.pl ver 0.2.1
 */
// Mock include file to share data between tests and mock
#include "test/mock/mock_stack_sdp_api.h"

#include <cstdint>

#include "test/common/mock_functions.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

// Mocked compile conditionals, if any
// Mocked internal structures, if any

namespace test {
namespace mock {
namespace stack_sdp_api {

// Function state capture and return values, if needed
struct SDP_CancelServiceSearch SDP_CancelServiceSearch;
struct SDP_FindProfileVersionInRec SDP_FindProfileVersionInRec;
struct SDP_FindProtocolListElemInRec SDP_FindProtocolListElemInRec;
struct SDP_FindServiceUUIDInRec SDP_FindServiceUUIDInRec;
struct SDP_FindServiceUUIDInRec_128bit SDP_FindServiceUUIDInRec_128bit;
struct SDP_InitDiscoveryDb SDP_InitDiscoveryDb;
struct SDP_ServiceSearchAttributeRequest SDP_ServiceSearchAttributeRequest;
struct SDP_ServiceSearchAttributeRequest2 SDP_ServiceSearchAttributeRequest2;
struct SDP_ServiceSearchRequest SDP_ServiceSearchRequest;
struct SDP_FindAttributeInRec SDP_FindAttributeInRec;
struct SDP_FindServiceInDb SDP_FindServiceInDb;
struct SDP_FindServiceInDb_128bit SDP_FindServiceInDb_128bit;
struct SDP_FindServiceUUIDInDb SDP_FindServiceUUIDInDb;
struct SDP_DiDiscover SDP_DiDiscover;
struct SDP_GetDiRecord SDP_GetDiRecord;
struct SDP_SetLocalDiRecord SDP_SetLocalDiRecord;
struct SDP_GetNumDiRecords SDP_GetNumDiRecords;

}  // namespace stack_sdp_api
}  // namespace mock
}  // namespace test

// Mocked functions, if any
bool SDP_CancelServiceSearch(const tSDP_DISCOVERY_DB* p_db) {
  inc_func_call_count(__func__);
  return test::mock::stack_sdp_api::SDP_CancelServiceSearch(p_db);
}
bool SDP_FindProfileVersionInRec(const tSDP_DISC_REC* p_rec,
                                 uint16_t profile_uuid, uint16_t* p_version) {
  inc_func_call_count(__func__);
  return test::mock::stack_sdp_api::SDP_FindProfileVersionInRec(
      p_rec, profile_uuid, p_version);
}
bool SDP_FindProtocolListElemInRec(const tSDP_DISC_REC* p_rec,
                                   uint16_t layer_uuid,
                                   tSDP_PROTOCOL_ELEM* p_elem) {
  inc_func_call_count(__func__);
  return test::mock::stack_sdp_api::SDP_FindProtocolListElemInRec(
      p_rec, layer_uuid, p_elem);
}
bool SDP_FindServiceUUIDInRec(const tSDP_DISC_REC* p_rec,
                              bluetooth::Uuid* p_uuid) {
  inc_func_call_count(__func__);
  return test::mock::stack_sdp_api::SDP_FindServiceUUIDInRec(p_rec, p_uuid);
}
bool SDP_FindServiceUUIDInRec_128bit(const tSDP_DISC_REC* p_rec,
                                     bluetooth::Uuid* p_uuid) {
  inc_func_call_count(__func__);
  return test::mock::stack_sdp_api::SDP_FindServiceUUIDInRec_128bit(p_rec,
                                                                    p_uuid);
}
bool SDP_InitDiscoveryDb(tSDP_DISCOVERY_DB* p_db, uint32_t len,
                         uint16_t num_uuid, const bluetooth::Uuid* p_uuid_list,
                         uint16_t num_attr, const uint16_t* p_attr_list) {
  inc_func_call_count(__func__);
  return test::mock::stack_sdp_api::SDP_InitDiscoveryDb(
      p_db, len, num_uuid, p_uuid_list, num_attr, p_attr_list);
}
bool SDP_ServiceSearchAttributeRequest(const RawAddress& p_bd_addr,
                                       tSDP_DISCOVERY_DB* p_db,
                                       tSDP_DISC_CMPL_CB* p_cb) {
  inc_func_call_count(__func__);
  return test::mock::stack_sdp_api::SDP_ServiceSearchAttributeRequest(
      p_bd_addr, p_db, p_cb);
}
bool SDP_ServiceSearchAttributeRequest2(const RawAddress& p_bd_addr,
                                        tSDP_DISCOVERY_DB* p_db,
                                        tSDP_DISC_CMPL_CB2* p_cb2,
                                        const void* user_data) {
  inc_func_call_count(__func__);
  return test::mock::stack_sdp_api::SDP_ServiceSearchAttributeRequest2(
      p_bd_addr, p_db, p_cb2, user_data);
}
bool SDP_ServiceSearchRequest(const RawAddress& p_bd_addr,
                              tSDP_DISCOVERY_DB* p_db,
                              tSDP_DISC_CMPL_CB* p_cb) {
  inc_func_call_count(__func__);
  return test::mock::stack_sdp_api::SDP_ServiceSearchRequest(p_bd_addr, p_db,
                                                             p_cb);
}
tSDP_DISC_ATTR* SDP_FindAttributeInRec(const tSDP_DISC_REC* p_rec,
                                       uint16_t attr_id) {
  inc_func_call_count(__func__);
  return test::mock::stack_sdp_api::SDP_FindAttributeInRec(p_rec, attr_id);
}
tSDP_DISC_REC* SDP_FindServiceInDb(const tSDP_DISCOVERY_DB* p_db,
                                   uint16_t service_uuid,
                                   tSDP_DISC_REC* p_start_rec) {
  inc_func_call_count(__func__);
  return test::mock::stack_sdp_api::SDP_FindServiceInDb(p_db, service_uuid,
                                                        p_start_rec);
}
tSDP_DISC_REC* SDP_FindServiceInDb_128bit(const tSDP_DISCOVERY_DB* p_db,
                                          tSDP_DISC_REC* p_start_rec) {
  inc_func_call_count(__func__);
  return test::mock::stack_sdp_api::SDP_FindServiceInDb_128bit(p_db,
                                                               p_start_rec);
}
tSDP_DISC_REC* SDP_FindServiceUUIDInDb(const tSDP_DISCOVERY_DB* p_db,
                                       const bluetooth::Uuid& uuid,
                                       tSDP_DISC_REC* p_start_rec) {
  inc_func_call_count(__func__);
  return test::mock::stack_sdp_api::SDP_FindServiceUUIDInDb(p_db, uuid,
                                                            p_start_rec);
}
tSDP_STATUS SDP_DiDiscover(const RawAddress& remote_device,
                           tSDP_DISCOVERY_DB* p_db, uint32_t len,
                           tSDP_DISC_CMPL_CB* p_cb) {
  inc_func_call_count(__func__);
  return test::mock::stack_sdp_api::SDP_DiDiscover(remote_device, p_db, len,
                                                   p_cb);
}
uint16_t SDP_GetDiRecord(uint8_t get_record_index,
                         tSDP_DI_GET_RECORD* p_device_info,
                         const tSDP_DISCOVERY_DB* p_db) {
  inc_func_call_count(__func__);
  return test::mock::stack_sdp_api::SDP_GetDiRecord(get_record_index,
                                                    p_device_info, p_db);
}
uint16_t SDP_SetLocalDiRecord(const tSDP_DI_RECORD* p_device_info,
                              uint32_t* p_handle) {
  inc_func_call_count(__func__);
  return test::mock::stack_sdp_api::SDP_SetLocalDiRecord(p_device_info,
                                                         p_handle);
}
uint8_t SDP_GetNumDiRecords(const tSDP_DISCOVERY_DB* p_db) {
  inc_func_call_count(__func__);
  return test::mock::stack_sdp_api::SDP_GetNumDiRecords(p_db);
}
// END mockcify generation
