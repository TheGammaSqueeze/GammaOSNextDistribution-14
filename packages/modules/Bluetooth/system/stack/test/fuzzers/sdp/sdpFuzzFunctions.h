/*
 * Copyright 2020 The Android Open Source Project
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

#ifndef FUZZER_SDP_FUNCTIONS_H_
#define FUZZER_SDP_FUNCTIONS_H_

#include <fuzzer/FuzzedDataProvider.h>

#include <vector>

#include "fuzzers/common/commonFuzzHelpers.h"
#include "fuzzers/sdp/sdpFuzzHelpers.h"
#include "stack/include/sdp_api.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

#define SDP_MAX_DB_LEN 1024 * 1024  // 1 MB
#define MAX_NUM_DBS 64

/* This is a vector of lambda functions the fuzzer will pull from.
 *  This is done so new functions can be added to the fuzzer easily
 *  without requiring modifications to the main fuzzer file. This also
 *  allows multiple fuzzers to include this file, if functionality is needed.
 */
static const std::vector<std::function<void(FuzzedDataProvider*)>>
    sdp_operations = {
        // ::SDP_InitDiscoveryDb
        [](FuzzedDataProvider* fdp) -> void {
          if (sdp_db_vect.size() >= MAX_NUM_DBS) {
            return;
          }

          // build out uuid_list
          std::vector<bluetooth::Uuid> uuid_list;
          uint8_t num_uuids = fdp->ConsumeIntegral<uint8_t>();
          for (uint8_t i = 0; i < num_uuids; i++) {
            uuid_list.push_back(generateArbitraryUuid(fdp));
          }

          // build out attr_list
          std::vector<uint16_t> attr_list = generateArbitraryAttrList(fdp);

          uint32_t db_size =
              fdp->ConsumeIntegralInRange<uint32_t>(0, SDP_MAX_DB_LEN);
          std::shared_ptr<tSDP_DISCOVERY_DB> p_db(
              reinterpret_cast<tSDP_DISCOVERY_DB*>(malloc(db_size)), free);
          if (p_db) {
            bool success =
                get_legacy_stack_sdp_api()->service.SDP_InitDiscoveryDb(
                    p_db.get(), db_size, uuid_list.size(), uuid_list.data(),
                    attr_list.size(),
                    reinterpret_cast<uint16_t*>(attr_list.data()));
            if (success) {
              sdp_db_vect.push_back(p_db);
            }
          }
        },

        // ::SDP_CancelServiceSearch
        [](FuzzedDataProvider* fdp) -> void {
          get_legacy_stack_sdp_api()->service.SDP_CancelServiceSearch(
              getArbitraryVectorElement(fdp, sdp_db_vect, true).get());
        },

        // ::SDP_ServiceSearchRequest
        [](FuzzedDataProvider* fdp) -> void {
          const RawAddress bd_addr = generateRawAddress(fdp);
          tSDP_DISCOVERY_DB* db =
              getArbitraryVectorElement(fdp, sdp_db_vect, false).get();
          if (db) {
            get_legacy_stack_sdp_api()->service.SDP_ServiceSearchRequest(
                bd_addr, db, &sdp_disc_cmpl_cb);
          }
        },

        // ::SDP_ServiceSearchAttributeRequest
        [](FuzzedDataProvider* fdp) -> void {
          const RawAddress bd_addr = generateRawAddress(fdp);
          tSDP_DISCOVERY_DB* db =
              getArbitraryVectorElement(fdp, sdp_db_vect, false).get();
          if (db) {
            get_legacy_stack_sdp_api()
                ->service.SDP_ServiceSearchAttributeRequest(bd_addr, db,
                                                            &sdp_disc_cmpl_cb);
          }
        },

        // ::SDP_ServiceSearchAttributeRequest2
        [](FuzzedDataProvider* fdp) -> void {
          const RawAddress bd_addr = generateRawAddress(fdp);
          std::vector<uint8_t> user_data = fdp->ConsumeBytes<uint8_t>(
              fdp->ConsumeIntegralInRange<size_t>(0, 1024));
          tSDP_DISCOVERY_DB* db =
              getArbitraryVectorElement(fdp, sdp_db_vect, false).get();

          if (db) {
            get_legacy_stack_sdp_api()
                ->service.SDP_ServiceSearchAttributeRequest2(
                    bd_addr, db, &sdp_disc_cmpl_cb2, user_data.data());
          }
        },

        // ::SDP_FindAttributeInRec
        [](FuzzedDataProvider* fdp) -> void {
          tSDP_DISC_REC* p_rec =
              generateArbitrarySdpDiscRecord(fdp, false).get();
          get_legacy_stack_sdp_api()->record.SDP_FindAttributeInRec(
              p_rec, fdp->ConsumeIntegral<uint16_t>());
        },

        // ::SDP_FindServiceInDb
        [](FuzzedDataProvider* fdp) -> void {
          get_legacy_stack_sdp_api()->db.SDP_FindServiceInDb(
              getArbitraryVectorElement(fdp, sdp_db_vect, true).get(),
              fdp->ConsumeIntegral<uint16_t>(),
              generateArbitrarySdpDiscRecord(fdp, true).get());
        },

        // ::SDP_FindServiceUUIDInDb
        [](FuzzedDataProvider* fdp) -> void {
          const bluetooth::Uuid uuid = generateArbitraryUuid(fdp);
          get_legacy_stack_sdp_api()->db.SDP_FindServiceUUIDInDb(
              getArbitraryVectorElement(fdp, sdp_db_vect, true).get(), uuid,
              generateArbitrarySdpDiscRecord(fdp, true).get());
        },

        // ::SDP_FindServiceUUIDInRec_128bit
        [](FuzzedDataProvider* fdp) -> void {
          bluetooth::Uuid uuid = generateArbitraryUuid(fdp);
          tSDP_DISC_REC* p_rec =
              generateArbitrarySdpDiscRecord(fdp, false).get();
          get_legacy_stack_sdp_api()->record.SDP_FindServiceUUIDInRec_128bit(
              p_rec, &uuid);
        },

        // ::SDP_FindServiceInDb_128bit
        [](FuzzedDataProvider* fdp) -> void {
          get_legacy_stack_sdp_api()->db.SDP_FindServiceInDb_128bit(
              getArbitraryVectorElement(fdp, sdp_db_vect, true).get(),
              generateArbitrarySdpDiscRecord(fdp, true).get());
        },

        // ::SDP_FindProtocolListElemInRec
        [](FuzzedDataProvider* fdp) -> void {
          tSDP_PROTOCOL_ELEM elem = generateArbitrarySdpProtocolElements(fdp);
          tSDP_DISC_REC* p_rec =
              generateArbitrarySdpDiscRecord(fdp, false).get();
          get_legacy_stack_sdp_api()->record.SDP_FindProtocolListElemInRec(
              p_rec, fdp->ConsumeIntegral<uint16_t>(), &elem);
        },

        // ::SDP_FindProfileVersionInRec
        [](FuzzedDataProvider* fdp) -> void {
          uint16_t p_version;
          tSDP_DISC_REC* p_rec =
              generateArbitrarySdpDiscRecord(fdp, false).get();

          get_legacy_stack_sdp_api()->record.SDP_FindProfileVersionInRec(
              p_rec, fdp->ConsumeIntegral<uint16_t>(), &p_version);
        },

        // ::SDP_CreateRecord
        [](FuzzedDataProvider* fdp) -> void {
          uint32_t handle =
              get_legacy_stack_sdp_api()->handle.SDP_CreateRecord();
          if (handle) {
            sdp_record_handles.push_back(handle);
          }
        },

        // ::SDP_DeleteRecord
        [](FuzzedDataProvider* fdp) -> void {
          get_legacy_stack_sdp_api()->handle.SDP_DeleteRecord(
              getArbitraryVectorElement(fdp, sdp_record_handles, true));
        },

        // ::SDP_AddAttribute
        [](FuzzedDataProvider* fdp) -> void {
          std::vector<uint8_t> val = fdp->ConsumeBytes<uint8_t>(
              fdp->ConsumeIntegralInRange<size_t>(1, 1024));
          if (val.size() > 0) {
            get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
                getArbitraryVectorElement(fdp, sdp_record_handles, true),
                fdp->ConsumeIntegral<uint16_t>(),
                fdp->ConsumeIntegral<uint8_t>(), val.size(), val.data());
          }
        },

        // ::SDP_AddSequence
        [](FuzzedDataProvider* fdp) -> void {
          SDP_Sequence_Helper seq = generateArbitrarySdpElemSequence(fdp);

          get_legacy_stack_sdp_api()->handle.SDP_AddSequence(
              getArbitraryVectorElement(fdp, sdp_record_handles, true),
              fdp->ConsumeIntegral<uint16_t>(), seq.num_elem, seq.type.get(),
              seq.len.get(), seq.p_val.get());
        },

        // ::SDP_AddUuidSequence
        [](FuzzedDataProvider* fdp) -> void {
          uint16_t num_uuids = fdp->ConsumeIntegralInRange<uint16_t>(1, 64);
          uint16_t* uuids = new uint16_t[num_uuids];
          for (uint16_t i = 0; i < num_uuids; i++) {
            uuids[i] = fdp->ConsumeIntegral<uint16_t>();
          }

          get_legacy_stack_sdp_api()->handle.SDP_AddUuidSequence(
              getArbitraryVectorElement(fdp, sdp_record_handles, true),
              fdp->ConsumeIntegral<uint16_t>(), num_uuids, uuids);
          delete[] uuids;
        },

        // ::SDP_AddProtocolList
        [](FuzzedDataProvider* fdp) -> void {
          std::shared_ptr<tSDP_PROTO_LIST_ELEM> p_proto_list =
              generateArbitrarySdpProtocolElementList(fdp);
          if (p_proto_list) {
            get_legacy_stack_sdp_api()->handle.SDP_AddProtocolList(
                getArbitraryVectorElement(fdp, sdp_record_handles, true),
                p_proto_list.get()->num_elems, p_proto_list.get()->list_elem);
          }
        },

        // ::SDP_AddAdditionProtoLists
        [](FuzzedDataProvider* fdp) -> void {
          uint16_t arr_size;
          tSDP_PROTO_LIST_ELEM** p_proto_list =
              generateArbitrarySdpProtocolElementListArray(fdp, &arr_size);
          if (p_proto_list) {
            if (p_proto_list[0]) {
              get_legacy_stack_sdp_api()->handle.SDP_AddAdditionProtoLists(
                  getArbitraryVectorElement(fdp, sdp_record_handles, true),
                  arr_size, p_proto_list[0]);
              for (uint16_t i = 0; i < arr_size; i++) {
                delete p_proto_list[i];
              }
            }
            free(p_proto_list);
          }
        },

        // ::SDP_AddProfileDescriptorList
        [](FuzzedDataProvider* fdp) -> void {
          get_legacy_stack_sdp_api()->handle.SDP_AddProfileDescriptorList(
              getArbitraryVectorElement(fdp, sdp_record_handles, true),
              fdp->ConsumeIntegral<uint16_t>(),
              fdp->ConsumeIntegral<uint16_t>());
        },

        // ::SDP_AddLanguageBaseAttrIDList
        [](FuzzedDataProvider* fdp) -> void {
          get_legacy_stack_sdp_api()->handle.SDP_AddLanguageBaseAttrIDList(
              getArbitraryVectorElement(fdp, sdp_record_handles, true),
              fdp->ConsumeIntegral<uint16_t>(),
              fdp->ConsumeIntegral<uint16_t>(),
              fdp->ConsumeIntegral<uint16_t>());
        },

        // ::SDP_AddServiceClassIdList
        [](FuzzedDataProvider* fdp) -> void {
          uint16_t num_services = fdp->ConsumeIntegralInRange<uint16_t>(0, 64);
          uint16_t* service_uuids = new uint16_t[num_services];
          for (uint16_t i = 0; i < num_services; i++) {
            service_uuids[i] = fdp->ConsumeIntegral<uint16_t>();
          }

          get_legacy_stack_sdp_api()->handle.SDP_AddServiceClassIdList(
              getArbitraryVectorElement(fdp, sdp_record_handles, true),
              num_services, service_uuids);

          delete[] service_uuids;
        },

        // ::SDP_SetLocalDiRecord
        [](FuzzedDataProvider* fdp) -> void {
          uint32_t handle;  // Output var
          tSDP_DI_RECORD device_info = generateArbitrarySdpDiRecord(fdp);
          get_legacy_stack_sdp_api()->device_id.SDP_SetLocalDiRecord(
              &device_info, &handle);
        },

        // ::SDP_DiDiscover
        [](FuzzedDataProvider* fdp) -> void {
          const RawAddress remote_device = generateRawAddress(fdp);

          // Create a new buffer for the discoveryDB init call
          uint32_t db_size =
              fdp->ConsumeIntegralInRange<uint32_t>(0, SDP_MAX_DB_LEN);
          std::shared_ptr<tSDP_DISCOVERY_DB> p_db(
              reinterpret_cast<tSDP_DISCOVERY_DB*>(malloc(db_size)), free);
          if (p_db) {
            get_legacy_stack_sdp_api()->device_id.SDP_DiDiscover(
                remote_device, p_db.get(), db_size, &sdp_disc_cmpl_cb);
          }
        },

        // ::SDP_GetNumDiRecords
        [](FuzzedDataProvider* fdp) -> void {
          get_legacy_stack_sdp_api()->device_id.SDP_GetNumDiRecords(
              getArbitraryVectorElement(fdp, sdp_db_vect, true).get());
        },

        // ::SDP_GetDiRecord
        [](FuzzedDataProvider* fdp) -> void {
          tSDP_DI_GET_RECORD device_info;  // Output var
          get_legacy_stack_sdp_api()->device_id.SDP_GetDiRecord(
              fdp->ConsumeIntegral<uint8_t>(), &device_info,
              getArbitraryVectorElement(fdp, sdp_db_vect, true).get());
        },

        // ::SDP_FindServiceUUIDInRec
        [](FuzzedDataProvider* fdp) -> void {
          tSDP_DISC_REC* p_rec =
              generateArbitrarySdpDiscRecord(fdp, false).get();
          bluetooth::Uuid uuid;  // Output var
          get_legacy_stack_sdp_api()->record.SDP_FindServiceUUIDInRec(p_rec,
                                                                      &uuid);
        }};

#endif  // FUZZER_SDP_FUNCTIONS_H_
