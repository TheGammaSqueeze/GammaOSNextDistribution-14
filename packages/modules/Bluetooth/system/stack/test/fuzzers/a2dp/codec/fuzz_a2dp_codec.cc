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

#include <fuzzer/FuzzedDataProvider.h>
#include "fuzzers/a2dp/codec/a2dpCodecFuzzFunctions.h"
#include "fuzzers/common/commonFuzzHelpers.h"

constexpr int32_t kMaxIterations = 100;

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* Data, size_t Size) {
  // Init our wrapper
  FuzzedDataProvider dataProvider(Data, Size);
  int32_t count = 1;

  // Call some functions
  while (dataProvider.remaining_bytes() > 0 && count++ <= kMaxIterations) {
    callArbitraryFunction(&dataProvider, a2dp_codec_operations);
  }

  // Cleanup our state
  cleanupA2dpCodecFuzz();

  return 0;
}
