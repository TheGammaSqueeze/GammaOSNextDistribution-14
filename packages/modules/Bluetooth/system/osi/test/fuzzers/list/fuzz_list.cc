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
#include "osi/include/list.h"
#include "osi/test/fuzzers/include/libosiFuzzHelperFunctions.h"

#define MAX_NUM_FUNCTIONS 512
#define MAX_BUF_SIZE 256

struct list_node_t {
  struct list_node_t* next;
  void* data;
};

void cb(void* data) {}
// Pass a ptr to FuzzedDataProvider in context
bool list_iter_cb_impl(void* data, void* context) {
  FuzzedDataProvider* dataProvider =
      reinterpret_cast<FuzzedDataProvider*>(context);
  return dataProvider->ConsumeBool();
}

list_t* createList(FuzzedDataProvider* dataProvider) {
  bool should_callback = dataProvider->ConsumeBool();
  if (should_callback) {
    return list_new(cb);
  } else {
    return list_new(nullptr);
  }
}

void* getArbitraryElement(std::vector<void*>* vector,
                          FuzzedDataProvider* dataProvider) {
  if (vector->size() == 0) {
    return nullptr;
  }
  // Get an index
  size_t index =
      dataProvider->ConsumeIntegralInRange<size_t>(0, vector->size() - 1);
  return vector->at(index);
}

list_node_t* getArbitraryNode(list_t* list, FuzzedDataProvider* dataProvider) {
  if (list == nullptr || list_is_empty(list)) {
    return nullptr;
  }
  size_t index =
      dataProvider->ConsumeIntegralInRange<size_t>(0, list_length(list) - 1);
  list_node_t* node = list_begin(list);
  for (size_t i = 0; i < index; i++) {
    node = node->next;
  }

  return node;
}

void callArbitraryFunction(std::vector<void*>* list_vector,
                           std::vector<void*>* alloc_vector,
                           FuzzedDataProvider* dataProvider) {
  list_t* list = nullptr;
  // Get our function identifier
  switch (dataProvider->ConsumeIntegralInRange<char>(0, 18)) {
    // Let 0 be a NO-OP, as ConsumeIntegral will return 0 on an empty buffer
    // (This will likely bias whatever action is here to run more often)
    case 0:
      return;
    // Create a new list
    case 1:
      list = createList(dataProvider);
      list_vector->push_back(list);
      return;
    // Free a list
    case 2: {
      size_t index = 0;
      if (list_vector->size() > 0) {
        // Get an index
        index = dataProvider->ConsumeIntegralInRange<size_t>(
            0, list_vector->size() - 1);
        list = reinterpret_cast<list_t*>(list_vector->at(index));
      }
      list_free(list);
      // Otherwise free a valid list
      if (list != nullptr) {
        list_vector->erase(list_vector->begin() + index);
      }
      return;
    }
    case 3:
      list = reinterpret_cast<list_t*>(
          getArbitraryElement(list_vector, dataProvider));
      if (list != nullptr) {
        list_is_empty(list);
      }
      return;
    case 4:
      list = reinterpret_cast<list_t*>(
          getArbitraryElement(list_vector, dataProvider));
      if (list != nullptr) {
        void* search_buf = getArbitraryElement(alloc_vector, dataProvider);
        if (search_buf != nullptr) {
          list_contains(list, search_buf);
        }
      }
      return;
    case 5:
      list = reinterpret_cast<list_t*>(
          getArbitraryElement(list_vector, dataProvider));
      if (list != nullptr) {
        list_length(list);
      }
      return;
    case 6:
      list = reinterpret_cast<list_t*>(
          getArbitraryElement(list_vector, dataProvider));
      if (list != nullptr && !list_is_empty(list)) {
        list_front(list);
      }
      return;
    case 7:
      list = reinterpret_cast<list_t*>(
          getArbitraryElement(list_vector, dataProvider));
      if (list != nullptr && !list_is_empty(list)) {
        list_back(list);
      }
      return;
    case 8:
      list = reinterpret_cast<list_t*>(
          getArbitraryElement(list_vector, dataProvider));
      if (list != nullptr && !list_is_empty(list)) {
        list_back_node(list);
      }
      return;
    case 9: {
      list = reinterpret_cast<list_t*>(
          getArbitraryElement(list_vector, dataProvider));
      if (list == nullptr) {
        return;
      }
      void* buf = generateBuffer(dataProvider, MAX_BUF_SIZE, false);
      alloc_vector->push_back(buf);
      list_node_t* node = getArbitraryNode(list, dataProvider);
      if (node != nullptr && buf != nullptr) {
        list_insert_after(list, node, buf);
      }
      return;
    }
    case 10: {
      list = reinterpret_cast<list_t*>(
          getArbitraryElement(list_vector, dataProvider));
      void* buf = generateBuffer(dataProvider, MAX_BUF_SIZE, false);
      alloc_vector->push_back(buf);
      if (list != nullptr && buf != nullptr) {
        list_prepend(list, buf);
      }
      return;
    }
    case 11: {
      list = reinterpret_cast<list_t*>(
          getArbitraryElement(list_vector, dataProvider));
      void* buf = generateBuffer(dataProvider, MAX_BUF_SIZE, false);
      alloc_vector->push_back(buf);
      if (list != nullptr && buf != nullptr) {
        list_append(list, buf);
      }
      return;
    }
    case 12: {
      list = reinterpret_cast<list_t*>(
          getArbitraryElement(list_vector, dataProvider));
      // The buffer will be valid, but may be for a different list
      void* buf = getArbitraryElement(alloc_vector, dataProvider);
      if (list != nullptr && buf != nullptr) {
        list_remove(list, buf);
      }
      return;
    }
    case 13:
      list = reinterpret_cast<list_t*>(
          getArbitraryElement(list_vector, dataProvider));
      if (list != nullptr) {
        list_clear(list);
      }
      return;
    case 14:
      list = reinterpret_cast<list_t*>(
          getArbitraryElement(list_vector, dataProvider));
      if (list != nullptr) {
        list_foreach(list, list_iter_cb_impl, dataProvider);
      }
      return;
    case 15:
      list = reinterpret_cast<list_t*>(
          getArbitraryElement(list_vector, dataProvider));
      if (list != nullptr) {
        list_begin(list);
      }
      return;
    case 16:
      list = reinterpret_cast<list_t*>(
          getArbitraryElement(list_vector, dataProvider));
      if (list != nullptr) {
        list_end(list);
      }
      return;
    case 17: {
      list = reinterpret_cast<list_t*>(
          getArbitraryElement(list_vector, dataProvider));
      if (list == nullptr) {
        return;
      }
      list_node_t* node = getArbitraryNode(list, dataProvider);
      if (node != nullptr) {
        list_next(node);
      }
      return;
    }
    case 18: {
      list = reinterpret_cast<list_t*>(
          getArbitraryElement(list_vector, dataProvider));
      if (list == nullptr) {
        return;
      }
      list_node_t* node = getArbitraryNode(list, dataProvider);
      if (node != nullptr && node != list_end(list)) {
        list_node(node);
      }
      return;
    }
    default:
      return;
  }
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* Data, size_t Size) {
  // Init our wrapper
  FuzzedDataProvider dataProvider(Data, Size);

  // Keep a vector of our allocated objects for freeing later
  std::vector<void*> list_vector;
  std::vector<void*> alloc_vector;

  // Call some functions, create some buffers
  size_t num_functions =
      dataProvider.ConsumeIntegralInRange<size_t>(0, MAX_NUM_FUNCTIONS);
  for (size_t i = 0; i < num_functions; i++) {
    callArbitraryFunction(&list_vector, &alloc_vector, &dataProvider);
  }

  // Free anything we've allocated
  for (const auto& list : list_vector) {
    if (list != nullptr) {
      list_free(reinterpret_cast<list_t*>(list));
    }
  }
  for (const auto& alloc : alloc_vector) {
    if (alloc != nullptr) {
      free(alloc);
    }
  }
  list_vector.clear();

  return 0;
}
