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
#include "osi/include/compat.h"

#define MAX_BUFFER_SIZE 4096

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* Data, size_t Size) {
// Our functions are only defined with __GLIBC__
#if __GLIBC__
  // Init our wrapper
  FuzzedDataProvider dataProvider(Data, Size);

  size_t buf_size =
      dataProvider.ConsumeIntegralInRange<size_t>(0, MAX_BUFFER_SIZE);
  if (buf_size == 0) {
    return 0;
  }

  // Set up our buffers
  // NOTE: If the src buffer is not NULL-terminated, the strlcpy will
  //       overread regardless of the len arg. Force null-term for now.
  std::vector<char> bytes =
      dataProvider.ConsumeBytesWithTerminator<char>(buf_size, '\0');
  if (bytes.empty()) {
    return 0;
  }
  buf_size = bytes.size();
  void* dst_buf = malloc(buf_size);
  if (dst_buf == nullptr) {
    return 0;
  }

  // Call the getId fn just to ensure things don't crash
  gettid();

  // Copy, then concat
  size_t len_to_cpy = dataProvider.ConsumeIntegralInRange<size_t>(0, buf_size);
  strlcpy(reinterpret_cast<char*>(dst_buf),
          reinterpret_cast<char*>(bytes.data()), len_to_cpy);
  strlcat(reinterpret_cast<char*>(dst_buf),
          reinterpret_cast<char*>(bytes.data()), len_to_cpy);

  // Clear out our dest buffer
  free(dst_buf);
#endif

  return 0;
}
