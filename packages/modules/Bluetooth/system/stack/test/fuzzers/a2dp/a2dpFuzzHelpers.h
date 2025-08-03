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

#ifndef BT_STACK_FUZZ_A2DP_HELPERS_H_
#define BT_STACK_FUZZ_A2DP_HELPERS_H_

// NOTE: This file should not be included directly.
//       It is included by the corresponding "...Functions.h" file.

#include <fuzzer/FuzzedDataProvider.h>

#include <vector>

#include "fuzzers/sdp/sdpFuzzHelpers.h"
#include "internal_include/bt_target.h"
#include "osi/include/allocator.h"
#include "stack/a2dp/a2dp_int.h"
#include "types/raw_address.h"

#define MAX_DB_SIZE 4096

tA2DP_SDP_DB_PARAMS generateDBParams(FuzzedDataProvider* fdp,
                                     std::vector<uint16_t>& attr_list) {
  attr_list = generateArbitraryAttrList(fdp);

  tA2DP_SDP_DB_PARAMS db_params;
  db_params.db_len = fdp->ConsumeIntegralInRange<uint32_t>(0, MAX_DB_SIZE);
  db_params.num_attr = attr_list.size();
  db_params.p_attrs = attr_list.empty() ? nullptr : attr_list.data();

  return db_params;
}

// Define our empty callback function
void a2dp_find_callback(bool found, tA2DP_Service* p_service,
                        const RawAddress& peer_address) {
  // Free the RawAddress we created in the generate function
  delete &peer_address;
}

// Function to clean up and clear our allocated objects
void cleanupA2dpFuzz() {
  // Delete our a2dp_cb database if it exists
  if (a2dp_cb.find.p_db) {
    osi_free(a2dp_cb.find.p_db);
  }
  // This function resets the a2dp_cb global to defaults
  A2DP_Init();

  // SDP needs to perform cleanup as well.
  cleanupSdpFuzz();
}

#endif  // BT_STACK_FUZZ_A2DP_HELPERS_H_
