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
#include "osi/include/ringbuffer.h"

#define MAX_NUM_FUNCTIONS 512
#define MAX_BUF_SIZE 2048

ringbuffer_t* getArbitraryRingBuf(std::vector<ringbuffer_t*>* ringbuf_vector,
                                  FuzzedDataProvider* dataProvider) {
  if (ringbuf_vector->empty()) {
    return nullptr;
  }

  size_t index = dataProvider->ConsumeIntegralInRange<size_t>(
      0, ringbuf_vector->size() - 1);
  return ringbuf_vector->at(index);
}

void callArbitraryFunction(std::vector<ringbuffer_t*>* ringbuf_vector,
                           FuzzedDataProvider* dataProvider) {
  // Get our function identifier
  char func_id = dataProvider->ConsumeIntegralInRange<char>(0, 8);

  ringbuffer_t* buf = nullptr;
  switch (func_id) {
    // Let 0 be a NO-OP, as ConsumeIntegral will return 0 on an empty buffer
    // (This will likely bias whatever action is here to run more often)
    case 0:
      return;
    case 1: {
      size_t size =
          dataProvider->ConsumeIntegralInRange<size_t>(0, MAX_BUF_SIZE);
      buf = ringbuffer_init(size);
      if (buf) {
        ringbuf_vector->push_back(buf);
      }
    }
      return;
    case 2: {
      if (ringbuf_vector->empty()) {
        return;
      }
      size_t index = dataProvider->ConsumeIntegralInRange<size_t>(
          0, ringbuf_vector->size() - 1);
      buf = ringbuf_vector->at(index);
      if (buf) {
        ringbuffer_free(buf);
        ringbuf_vector->erase(ringbuf_vector->begin() + index);
      }
    }
      return;
    case 3:
      buf = getArbitraryRingBuf(ringbuf_vector, dataProvider);
      if (buf) {
        ringbuffer_available(buf);
      }
      return;
    case 4:
      buf = getArbitraryRingBuf(ringbuf_vector, dataProvider);
      if (buf) {
        ringbuffer_size(buf);
      }
      return;
    case 5: {
      buf = getArbitraryRingBuf(ringbuf_vector, dataProvider);
      size_t size =
          dataProvider->ConsumeIntegralInRange<size_t>(1, MAX_BUF_SIZE);
      if (buf == nullptr || size == 0) {
        return;
      }
      void* src_buf = malloc(size);
      if (src_buf == nullptr) {
        return;
      }
      std::vector<uint8_t> bytes = dataProvider->ConsumeBytes<uint8_t>(size);
      memcpy(src_buf, bytes.data(), bytes.size());

      ringbuffer_insert(buf, reinterpret_cast<uint8_t*>(src_buf), size);
      free(src_buf);
    }
      return;
    case 6:
    case 7: {
      buf = getArbitraryRingBuf(ringbuf_vector, dataProvider);
      if (buf == nullptr) {
        return;
      }
      size_t max_size = ringbuffer_size(buf);
      if (max_size == 0) {
        return;
      }
      size_t size = dataProvider->ConsumeIntegralInRange<size_t>(1, max_size);

      // NOTE: 0-size may be a valid case, that crashes currently.
      if (size == 0) {
        return;
      }

      void* dst_buf = malloc(size);
      if (dst_buf == nullptr) {
        return;
      }
      if (func_id == 6) {
        off_t offset = dataProvider->ConsumeIntegral<off_t>();
        if (offset >= 0 &&
            static_cast<size_t>(offset) <= ringbuffer_size(buf)) {
          ringbuffer_peek(buf, offset, reinterpret_cast<uint8_t*>(dst_buf),
                          size);
        }
      } else {
        ringbuffer_pop(buf, reinterpret_cast<uint8_t*>(dst_buf), size);
      }
      free(dst_buf);
    }
      return;
    case 8: {
      buf = getArbitraryRingBuf(ringbuf_vector, dataProvider);
      size_t size =
          dataProvider->ConsumeIntegralInRange<size_t>(0, MAX_BUF_SIZE);
      if (buf) {
        ringbuffer_delete(buf, size);
      }
    }
      return;
    default:
      return;
  }
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* Data, size_t Size) {
  // Init our wrapper
  FuzzedDataProvider dataProvider(Data, Size);

  // Keep a vector of our allocated objects for freeing later
  std::vector<ringbuffer_t*> ringbuf_vector;

  // Call some functions, create some buffers
  size_t num_functions =
      dataProvider.ConsumeIntegralInRange<size_t>(0, MAX_NUM_FUNCTIONS);
  for (size_t i = 0; i < num_functions; i++) {
    callArbitraryFunction(&ringbuf_vector, &dataProvider);
  }

  // Free anything we've allocated
  for (const auto& ringbuf : ringbuf_vector) {
    if (ringbuf != nullptr) {
      ringbuffer_free(ringbuf);
    }
  }
  ringbuf_vector.clear();
  return 0;
}
