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

#include <cstdint>
#include <functional>

#include "stack/include/sdp_callback.h"
#include "stack/include/sdp_device_id.h"
#include "stack/sdp/sdp_discovery_db.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

// Mocked compile conditionals, if any
namespace test {
namespace mock {
namespace stack_sdp_api {

// Shared state between mocked functions and tests
// Name: SDP_CancelServiceSearch
// Params: tSDP_DISCOVERY_DB* p_db
// Returns: bool
struct SDP_CancelServiceSearch {
  std::function<bool(const tSDP_DISCOVERY_DB* p_db)> body{
      [](const tSDP_DISCOVERY_DB* /* p_db */) { return false; }};
  bool operator()(const tSDP_DISCOVERY_DB* p_db) { return body(p_db); };
};
extern struct SDP_CancelServiceSearch SDP_CancelServiceSearch;
// Name: SDP_FindProfileVersionInRec
// Params: tSDP_DISC_REC* p_rec, uint16_t profile_uuid, uint16_t* p_version
// Returns: bool
struct SDP_FindProfileVersionInRec {
  std::function<bool(const tSDP_DISC_REC* p_rec, uint16_t profile_uuid,
                     uint16_t* p_version)>
      body{[](const tSDP_DISC_REC* /* p_rec */, uint16_t /* profile_uuid */,
              uint16_t* /* p_version */) { return false; }};
  bool operator()(const tSDP_DISC_REC* p_rec, uint16_t profile_uuid,
                  uint16_t* p_version) {
    return body(p_rec, profile_uuid, p_version);
  };
};
extern struct SDP_FindProfileVersionInRec SDP_FindProfileVersionInRec;
// Name: SDP_FindProtocolListElemInRec
// Params: tSDP_DISC_REC* p_rec, uint16_t layer_uuid, tSDP_PROTOCOL_ELEM* p_elem
// Returns: bool
struct SDP_FindProtocolListElemInRec {
  std::function<bool(const tSDP_DISC_REC* p_rec, uint16_t layer_uuid,
                     tSDP_PROTOCOL_ELEM* p_elem)>
      body{[](const tSDP_DISC_REC* /* p_rec */, uint16_t /* layer_uuid */,
              tSDP_PROTOCOL_ELEM* /* p_elem */) { return false; }};
  bool operator()(const tSDP_DISC_REC* p_rec, uint16_t layer_uuid,
                  tSDP_PROTOCOL_ELEM* p_elem) {
    return body(p_rec, layer_uuid, p_elem);
  };
};
extern struct SDP_FindProtocolListElemInRec SDP_FindProtocolListElemInRec;
// Name: SDP_FindServiceUUIDInRec
// Params: tSDP_DISC_REC* p_rec, bluetooth::Uuid* p_uuid
// Returns: bool
struct SDP_FindServiceUUIDInRec {
  std::function<bool(const tSDP_DISC_REC* p_rec, bluetooth::Uuid* p_uuid)> body{
      [](const tSDP_DISC_REC* /* p_rec */, bluetooth::Uuid* /* p_uuid */) {
        return false;
      }};
  bool operator()(const tSDP_DISC_REC* p_rec, bluetooth::Uuid* p_uuid) {
    return body(p_rec, p_uuid);
  };
};
extern struct SDP_FindServiceUUIDInRec SDP_FindServiceUUIDInRec;
// Name: SDP_FindServiceUUIDInRec_128bit
// Params: tSDP_DISC_REC* p_rec, bluetooth::Uuid* p_uuid
// Returns: bool
struct SDP_FindServiceUUIDInRec_128bit {
  std::function<bool(const tSDP_DISC_REC* p_rec, bluetooth::Uuid* p_uuid)> body{
      [](const tSDP_DISC_REC* /* p_rec */, bluetooth::Uuid* /* p_uuid */) {
        return false;
      }};
  bool operator()(const tSDP_DISC_REC* p_rec, bluetooth::Uuid* p_uuid) {
    return body(p_rec, p_uuid);
  };
};
extern struct SDP_FindServiceUUIDInRec_128bit SDP_FindServiceUUIDInRec_128bit;
// Name: SDP_InitDiscoveryDb
// Params: tSDP_DISCOVERY_DB* p_db, uint32_t len, uint16_t num_uuid, const
// bluetooth::Uuid* p_uuid_list, uint16_t num_attr, uint16_t* p_attr_list
// Returns: bool
struct SDP_InitDiscoveryDb {
  std::function<bool(tSDP_DISCOVERY_DB* p_db, uint32_t len, uint16_t num_uuid,
                     const bluetooth::Uuid* p_uuid_list, uint16_t num_attr,
                     const uint16_t* p_attr_list)>
      body{[](tSDP_DISCOVERY_DB* /* p_db */, uint32_t /* len */,
              uint16_t /* num_uuid */, const bluetooth::Uuid* /* p_uuid_list */,
              uint16_t /* num_attr */,
              const uint16_t* /* p_attr_list */) { return false; }};
  bool operator()(tSDP_DISCOVERY_DB* p_db, uint32_t len, uint16_t num_uuid,
                  const bluetooth::Uuid* p_uuid_list, uint16_t num_attr,
                  const uint16_t* p_attr_list) {
    return body(p_db, len, num_uuid, p_uuid_list, num_attr, p_attr_list);
  };
};
extern struct SDP_InitDiscoveryDb SDP_InitDiscoveryDb;
// Name: SDP_ServiceSearchAttributeRequest
// Params: const RawAddress& p_bd_addr, tSDP_DISCOVERY_DB* p_db,
// tSDP_DISC_CMPL_CB* p_cb Returns: bool
struct SDP_ServiceSearchAttributeRequest {
  std::function<bool(const RawAddress& p_bd_addr, tSDP_DISCOVERY_DB* p_db,
                     tSDP_DISC_CMPL_CB* p_cb)>
      body{[](const RawAddress& /* p_bd_addr */, tSDP_DISCOVERY_DB* /* p_db */,
              tSDP_DISC_CMPL_CB* /* p_cb */) { return false; }};
  bool operator()(const RawAddress& p_bd_addr, tSDP_DISCOVERY_DB* p_db,
                  tSDP_DISC_CMPL_CB* p_cb) {
    return body(p_bd_addr, p_db, p_cb);
  };
};
extern struct SDP_ServiceSearchAttributeRequest
    SDP_ServiceSearchAttributeRequest;
// Name: SDP_ServiceSearchAttributeRequest2
// Params: const RawAddress& p_bd_addr, tSDP_DISCOVERY_DB* p_db,
// tSDP_DISC_CMPL_CB2* p_cb2, void* user_data Returns: bool
struct SDP_ServiceSearchAttributeRequest2 {
  std::function<bool(const RawAddress& p_bd_addr, tSDP_DISCOVERY_DB* p_db,
                     tSDP_DISC_CMPL_CB2* p_cb2, const void* user_data)>
      body{[](const RawAddress& /* p_bd_addr */, tSDP_DISCOVERY_DB* /* p_db */,
              tSDP_DISC_CMPL_CB2* /* p_cb2 */,
              const void* /* user_data */) { return false; }};
  bool operator()(const RawAddress& p_bd_addr, tSDP_DISCOVERY_DB* p_db,
                  tSDP_DISC_CMPL_CB2* p_cb2, const void* user_data) {
    return body(p_bd_addr, p_db, p_cb2, user_data);
  };
};
extern struct SDP_ServiceSearchAttributeRequest2
    SDP_ServiceSearchAttributeRequest2;
// Name: SDP_ServiceSearchRequest
// Params: const RawAddress& p_bd_addr, tSDP_DISCOVERY_DB* p_db,
// tSDP_DISC_CMPL_CB* p_cb Returns: bool
struct SDP_ServiceSearchRequest {
  std::function<bool(const RawAddress& p_bd_addr, tSDP_DISCOVERY_DB* p_db,
                     tSDP_DISC_CMPL_CB* p_cb)>
      body{[](const RawAddress& /* p_bd_addr */, tSDP_DISCOVERY_DB* /* p_db */,
              tSDP_DISC_CMPL_CB* /* p_cb */) { return false; }};
  bool operator()(const RawAddress& p_bd_addr, tSDP_DISCOVERY_DB* p_db,
                  tSDP_DISC_CMPL_CB* p_cb) {
    return body(p_bd_addr, p_db, p_cb);
  };
};
extern struct SDP_ServiceSearchRequest SDP_ServiceSearchRequest;
// Name: SDP_FindAttributeInRec
// Params: tSDP_DISC_REC* p_rec, uint16_t attr_id
// Returns: tSDP_DISC_ATTR*
struct SDP_FindAttributeInRec {
  std::function<tSDP_DISC_ATTR*(const tSDP_DISC_REC* p_rec, uint16_t attr_id)>
      body{[](const tSDP_DISC_REC* /* p_rec */, uint16_t /* attr_id */) {
        return nullptr;
      }};
  tSDP_DISC_ATTR* operator()(const tSDP_DISC_REC* p_rec, uint16_t attr_id) {
    return body(p_rec, attr_id);
  };
};
extern struct SDP_FindAttributeInRec SDP_FindAttributeInRec;
// Name: SDP_FindServiceInDb
// Params: tSDP_DISCOVERY_DB* p_db, uint16_t service_uuid, tSDP_DISC_REC*
// p_start_rec Returns: tSDP_DISC_REC*
struct SDP_FindServiceInDb {
  std::function<tSDP_DISC_REC*(const tSDP_DISCOVERY_DB* p_db,
                               uint16_t service_uuid,
                               tSDP_DISC_REC* p_start_rec)>
      body{[](const tSDP_DISCOVERY_DB* /* p_db */, uint16_t /* service_uuid */,
              tSDP_DISC_REC* /* p_start_rec */) { return nullptr; }};
  tSDP_DISC_REC* operator()(const tSDP_DISCOVERY_DB* p_db,
                            uint16_t service_uuid, tSDP_DISC_REC* p_start_rec) {
    return body(p_db, service_uuid, p_start_rec);
  };
};
extern struct SDP_FindServiceInDb SDP_FindServiceInDb;
// Name: SDP_FindServiceInDb_128bit
// Params: tSDP_DISCOVERY_DB* p_db, tSDP_DISC_REC* p_start_rec
// Returns: tSDP_DISC_REC*
struct SDP_FindServiceInDb_128bit {
  std::function<tSDP_DISC_REC*(const tSDP_DISCOVERY_DB* p_db,
                               tSDP_DISC_REC* p_start_rec)>
      body{[](const tSDP_DISCOVERY_DB* /* p_db */,
              tSDP_DISC_REC* /* p_start_rec */) { return nullptr; }};
  tSDP_DISC_REC* operator()(const tSDP_DISCOVERY_DB* p_db,
                            tSDP_DISC_REC* p_start_rec) {
    return body(p_db, p_start_rec);
  };
};
extern struct SDP_FindServiceInDb_128bit SDP_FindServiceInDb_128bit;
// Name: SDP_FindServiceUUIDInDb
// Params: tSDP_DISCOVERY_DB* p_db, const bluetooth::Uuid& uuid, tSDP_DISC_REC*
// p_start_rec Returns: tSDP_DISC_REC*
struct SDP_FindServiceUUIDInDb {
  std::function<tSDP_DISC_REC*(const tSDP_DISCOVERY_DB* p_db,
                               const bluetooth::Uuid& uuid,
                               tSDP_DISC_REC* p_start_rec)>
      body{[](const tSDP_DISCOVERY_DB* /* p_db */,
              const bluetooth::Uuid& /* uuid */,
              tSDP_DISC_REC* /* p_start_rec */) { return nullptr; }};
  tSDP_DISC_REC* operator()(const tSDP_DISCOVERY_DB* p_db,
                            const bluetooth::Uuid& uuid,
                            tSDP_DISC_REC* p_start_rec) {
    return body(p_db, uuid, p_start_rec);
  };
};
extern struct SDP_FindServiceUUIDInDb SDP_FindServiceUUIDInDb;
// Name: SDP_DiDiscover
// Params: const RawAddress& remote_device, tSDP_DISCOVERY_DB* p_db, uint32_t
// len, tSDP_DISC_CMPL_CB* p_cb Returns: tSDP_STATUS
struct SDP_DiDiscover {
  std::function<tSDP_STATUS(const RawAddress& remote_device,
                            tSDP_DISCOVERY_DB* p_db, uint32_t len,
                            tSDP_DISC_CMPL_CB* p_cb)>
      body{[](const RawAddress& /* remote_device */,
              tSDP_DISCOVERY_DB* /* p_db */, uint32_t /* len */,
              tSDP_DISC_CMPL_CB* /* p_cb */) { return SDP_SUCCESS; }};
  tSDP_STATUS operator()(const RawAddress& remote_device,
                         tSDP_DISCOVERY_DB* p_db, uint32_t len,
                         tSDP_DISC_CMPL_CB* p_cb) {
    return body(remote_device, p_db, len, p_cb);
  };
};
extern struct SDP_DiDiscover SDP_DiDiscover;
// Name: SDP_GetDiRecord
// Params: uint8_t get_record_index, tSDP_DI_GET_RECORD* p_device_info,
// tSDP_DISCOVERY_DB* p_db Returns: uint16_t
struct SDP_GetDiRecord {
  std::function<uint16_t(uint8_t get_record_index,
                         tSDP_DI_GET_RECORD* p_device_info,
                         const tSDP_DISCOVERY_DB* p_db)>
      body{[](uint8_t /* get_record_index */,
              tSDP_DI_GET_RECORD* /* p_device_info */,
              const tSDP_DISCOVERY_DB* /* p_db */) { return 0; }};
  uint16_t operator()(uint8_t get_record_index,
                      tSDP_DI_GET_RECORD* p_device_info,
                      const tSDP_DISCOVERY_DB* p_db) {
    return body(get_record_index, p_device_info, p_db);
  };
};
extern struct SDP_GetDiRecord SDP_GetDiRecord;
// Name: SDP_SetLocalDiRecord
// Params: tSDP_DI_RECORD* p_device_info, uint32_t* p_handle
// Returns: uint16_t
struct SDP_SetLocalDiRecord {
  std::function<uint16_t(const tSDP_DI_RECORD* p_device_info,
                         uint32_t* p_handle)>
      body{[](const tSDP_DI_RECORD* /* p_device_info */,
              uint32_t* /* p_handle */) { return 0; }};
  uint16_t operator()(const tSDP_DI_RECORD* p_device_info, uint32_t* p_handle) {
    return body(p_device_info, p_handle);
  };
};
extern struct SDP_SetLocalDiRecord SDP_SetLocalDiRecord;
// Name: SDP_GetNumDiRecords
// Params: tSDP_DISCOVERY_DB* p_db
// Returns: uint8_t
struct SDP_GetNumDiRecords {
  std::function<uint8_t(const tSDP_DISCOVERY_DB* p_db)> body{
      [](const tSDP_DISCOVERY_DB* /* p_db */) { return 0; }};
  uint8_t operator()(const tSDP_DISCOVERY_DB* p_db) { return body(p_db); };
};
extern struct SDP_GetNumDiRecords SDP_GetNumDiRecords;

}  // namespace stack_sdp_api
}  // namespace mock
}  // namespace test

// END mockcify generation
