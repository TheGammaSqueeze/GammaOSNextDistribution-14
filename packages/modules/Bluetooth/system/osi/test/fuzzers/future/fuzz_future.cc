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
#include "osi/include/future.h"

#define MAX_BUFFER_SIZE 8

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* Data, size_t Size) {
  // Init our wrapper
  FuzzedDataProvider dataProvider(Data, Size);

  // The value of this result ptr shouldn't matter, but make a buffer to be safe
  size_t buf_size =
      dataProvider.ConsumeIntegralInRange<size_t>(1, MAX_BUFFER_SIZE);
  void* buf = malloc(buf_size);
  if (buf == nullptr) {
    return 0;
  }
  std::vector<uint8_t> bytes = dataProvider.ConsumeBytes<uint8_t>(buf_size);
  memcpy(buf, bytes.data(), bytes.size());

  // Is our future an immediate?
  future_t* future = nullptr;
  bool is_immediate = dataProvider.ConsumeBool();
  if (is_immediate) {
    future = future_new_immediate(buf);
  } else {
    future = future_new();
  }

  // These functions require a non-null object, according to the header
  if (future != nullptr) {
    // If we need to, specify that the future is ready
    if (!is_immediate) {
      future_ready(future, buf);
    }

    // Free the object
    future_await(future);
  }

  free(buf);
  return 0;
}
