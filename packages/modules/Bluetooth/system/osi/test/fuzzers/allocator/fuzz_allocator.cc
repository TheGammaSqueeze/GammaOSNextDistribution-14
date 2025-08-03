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
#include "osi/include/allocator.h"
#include "osi/test/fuzzers/include/libosiFuzzHelperFunctions.h"

#define MAX_NUM_FUNCTIONS 512
#define MAX_BUF_SIZE 256

void callArbitraryFunction(std::vector<void*>* alloc_vector,
                           FuzzedDataProvider* dataProvider) {
  // Get our function identifier
  char func_id = dataProvider->ConsumeIntegralInRange<char>(0, 6);

  switch (func_id) {
    // Let 0 be a NO-OP, as ConsumeIntegral will return 0 on an empty buffer
    // (This will likely bias whatever action is here to run more often)
    case 0:
      return;
    // Let case 1 be osi_malloc, and 2 be osi_calloc
    case 1:
    case 2: {
      size_t size =
          dataProvider->ConsumeIntegralInRange<size_t>(0, MAX_BUF_SIZE);
      void* ptr = nullptr;
      if (size == 0) {
        return;
      }
      if (func_id == 1) {
        ptr = osi_malloc(size);
      } else {
        ptr = osi_calloc(size);
      }
      if (ptr) {
        alloc_vector->push_back(ptr);
      }
    }
      return;
    // Let case 3 be osi_free, and 4 be osi_free_and_reset
    case 3:
    case 4: {
      if (alloc_vector->size() == 0) {
        return;
      }
      size_t index = dataProvider->ConsumeIntegralInRange<size_t>(
          0, alloc_vector->size() - 1);
      void* ptr = alloc_vector->at(index);
      if (ptr) {
        if (func_id == 3) {
          osi_free(ptr);
        } else {
          osi_free_and_reset(&ptr);
        }
      }
      alloc_vector->erase(alloc_vector->begin() + index);
    }
      return;
    // Let case 5 be osi_strdup, and 6 be osi_strdup
    case 5:
    case 6: {
      // Make a src buffer
      char* buf = generateBuffer(dataProvider, MAX_BUF_SIZE, true);
      char* str = nullptr;
      if (buf == nullptr) {
        return;
      }
      if (func_id == 5) {
        str = osi_strdup(buf);
      } else {
        size_t size =
            dataProvider->ConsumeIntegralInRange<size_t>(1, MAX_BUF_SIZE);
        str = osi_strndup(buf, size);
      }
      free(buf);
      if (str) {
        alloc_vector->push_back(str);
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
  std::vector<void*> alloc_vector;
  // Call some functions, create some buffers
  size_t num_functions =
      dataProvider.ConsumeIntegralInRange<size_t>(0, MAX_NUM_FUNCTIONS);
  for (size_t i = 0; i < num_functions; i++) {
    callArbitraryFunction(&alloc_vector, &dataProvider);
  }
  // Free anything we've allocated
  for (const auto& alloc : alloc_vector) {
    if (alloc != nullptr) {
      osi_free(alloc);
    }
  }
  alloc_vector.clear();
  return 0;
}
