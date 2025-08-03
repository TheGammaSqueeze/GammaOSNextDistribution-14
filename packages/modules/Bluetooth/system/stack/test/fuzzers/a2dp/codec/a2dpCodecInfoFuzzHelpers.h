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

#ifndef BT_STACK_FUZZ_A2DP_CODECINFO_HELPERS_H_
#define BT_STACK_FUZZ_A2DP_CODECINFO_HELPERS_H_

// NOTE: This file should not be included directly.
//       It is included by the corresponding "...Functions.h" file.

#include <fuzzer/FuzzedDataProvider.h>

#include <vector>

#include "a2dp_codec_api.h"
#include "stack/test/fuzzers/common/commonFuzzHelpers.h"

// Keep a vector of any allocated codec_info objects.
// It will be up to the caller to free this array at the end of a fuzz loop
std::vector<uint8_t*> a2dp_codec_info_vect;

// Calls a function from the ops_vector
void callArbitraryCodecInfoFunction(
    FuzzedDataProvider* fdp,
    std::vector<std::function<void(FuzzedDataProvider*, uint8_t*)>>
        ops_vector) {
  // Choose which function we'll be calling
  uint8_t function_id =
      fdp->ConsumeIntegralInRange<uint8_t>(0, ops_vector.size() - 1);

  // Get a info object
  uint8_t* codec_info =
      getArbitraryVectorElement(fdp, a2dp_codec_info_vect, false);

  // Most functions require a valid codec_info
  if (codec_info || function_id == 0 || function_id == 25 ||
      function_id == 26) {
    // Call the function we've chosen
    ops_vector[function_id](fdp, codec_info);
  }
}

// Function to clean up and clear our allocated objects
void cleanupA2dpCodecInfoFuzz() {
  for (auto it : a2dp_codec_info_vect) {
    if (it != nullptr) {
      delete it;
    }
  }
  a2dp_codec_info_vect.clear();
}

#endif  // BT_STACK_FUZZ_A2DP_CODECINFO_HELPERS_H_
