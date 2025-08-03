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

#ifndef BT_STACK_FUZZ_A2DP_FUNCTIONS_H_
#define BT_STACK_FUZZ_A2DP_FUNCTIONS_H_

#include <fuzzer/FuzzedDataProvider.h>

#include <vector>

#include "a2dp_api.h"
#include "fuzzers/a2dp/a2dpFuzzHelpers.h"
#include "fuzzers/common/commonFuzzHelpers.h"
#include "fuzzers/sdp/sdpFuzzFunctions.h"
#include "osi/include/allocator.h"
#include "raw_address.h"
#include "stack/a2dp/a2dp_int.h"
#include "stack/include/bt_uuid16.h"

#define MAX_STR_LEN 4096

/* This is a vector of lambda functions the fuzzer will pull from.
 *  This is done so new functions can be added to the fuzzer easily
 *  without requiring modifications to the main fuzzer file. This also
 *  allows multiple fuzzers to include this file, if functionality is needed.
 */
std::vector<std::function<void(FuzzedDataProvider*)>> a2dp_operations = {
    // Init
    [](FuzzedDataProvider*) -> void {
      // Re-init zeros out memory containing some pointers.
      // Free the db first to prevent memleaks
      if (a2dp_cb.find.p_db) {
        osi_free(a2dp_cb.find.p_db);
      }

      // Attempt re-initializations mid-run.
      A2DP_Init();
    },

    // A2DP_AddRecord
    [](FuzzedDataProvider* fdp) -> void {
      std::vector<char> p_service_name =
          fdp->ConsumeBytesWithTerminator<char>(MAX_STR_LEN);
      std::vector<char> p_provider_name =
          fdp->ConsumeBytesWithTerminator<char>(MAX_STR_LEN);
      uint16_t service_uuid = fdp->ConsumeBool() ? UUID_SERVCLASS_AUDIO_SOURCE
                                                 : UUID_SERVCLASS_AUDIO_SINK;
      A2DP_AddRecord(service_uuid, p_service_name.data(),
                     p_provider_name.data(), fdp->ConsumeIntegral<uint16_t>(),
                     // This should be a val returned by SDP_CreateRecord
                     getArbitraryVectorElement(fdp, sdp_record_handles, true));
    },

    // A2DP_FindService
    [](FuzzedDataProvider* fdp) -> void {
      std::vector<uint16_t> attr_list;
      tA2DP_SDP_DB_PARAMS p_db = generateDBParams(fdp, attr_list);
      const RawAddress bd_addr = generateRawAddress(fdp);
      uint16_t service_uuid = fdp->ConsumeBool() ? UUID_SERVCLASS_AUDIO_SOURCE
                                                 : UUID_SERVCLASS_AUDIO_SINK;
      A2DP_FindService(service_uuid, bd_addr, &p_db, a2dp_find_callback);
    },

    // A2DP_GetAvdtpVersion
    [](FuzzedDataProvider*) -> void { A2DP_GetAvdtpVersion(); },

    // A2DP_BitsSet
    [](FuzzedDataProvider* fdp) -> void {
      A2DP_BitsSet(fdp->ConsumeIntegral<uint64_t>());
    },

    // SDP Calls
    [](FuzzedDataProvider* fdp) -> void {
      callArbitraryFunction(fdp, sdp_operations);
    }};

#endif  // BT_STACK_FUZZ_A2DP_FUNCTIONS_H_
