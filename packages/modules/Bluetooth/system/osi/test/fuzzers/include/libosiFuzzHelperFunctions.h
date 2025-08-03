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

#ifndef LIBOSI_FUZZ_HELPERS_H_
#define LIBOSI_FUZZ_HELPERS_H_

#include <fuzzer/FuzzedDataProvider.h>
#include <vector>

char* generateBuffer(FuzzedDataProvider* dataProvider, size_t max_buffer_size,
                     bool null_terminate) {
  // Get our buffer size
  size_t buf_size =
      dataProvider->ConsumeIntegralInRange<size_t>(0, max_buffer_size);
  if (buf_size == 0) {
    return nullptr;
  }

  // Allocate and copy in data
  char* buf = reinterpret_cast<char*>(malloc(buf_size));
  std::vector<char> bytes = dataProvider->ConsumeBytes<char>(buf_size);
  memcpy(buf, bytes.data(), bytes.size());

  if (null_terminate) {
    // Force a null-termination
    buf[buf_size - 1] = 0x00;
  }

  return buf;
}

#endif  // LIBOSI_FUZZ_HELPERS_H_
