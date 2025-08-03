/*
 * Copyright (C) 2020 The Android Open Source Project
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

#ifndef CHRE_PLATFORM_SHARED_FLATBUFFERS_HELPERS_H_
#define CHRE_PLATFORM_SHARED_FLATBUFFERS_HELPERS_H_

#include "chre/util/container_support.h"
#include "chre/util/dynamic_vector.h"

#include "flatbuffers/flatbuffers.h"

namespace chre {

//! CHRE-specific flatbuffers allocator that routes to CHRE's alloc / free
//! functions.
class FlatBufferAllocator : public flatbuffers::Allocator {
 public:
  uint8_t *allocate(size_t size) override {
    return static_cast<uint8_t *>(memoryAlloc(size));
  }

  void deallocate(uint8_t *p, size_t) override {
    memoryFree(p);
  }
};

//! CHRE-specific FlatBufferBuilder that utilizes CHRE's allocator and adds
//! additional helper methods that make use of CHRE utilities.
class ChreFlatBufferBuilder : public flatbuffers::FlatBufferBuilder {
 public:
  explicit ChreFlatBufferBuilder(size_t initialSize = 1024)
      : flatbuffers::FlatBufferBuilder(initialSize, &mAllocator) {}

  // This is defined in flatbuffers::FlatBufferBuilder, but must be further
  // defined here since template functions aren't inherited.
  template <typename T>
  flatbuffers::Offset<flatbuffers::Vector<T>> CreateVector(const T *v,
                                                           size_t len) {
    return flatbuffers::FlatBufferBuilder::CreateVector(v, len);
  }

  /**
   * Serialize a DyanmicVector into a FlatBuffer `vector`.
   *
   * @tparam T The data type of the DynamicVector elements.
   * @param v A const reference to the DynamicVector to serialize into the
   * buffer as a `vector`.
   * @return Returns a typed `Offset` into the serialized data indicating
   * where the vector is stored.
   */
  template <typename T>
  flatbuffers::Offset<flatbuffers::Vector<T>> CreateVector(
      const DynamicVector<T> &v) {
    return flatbuffers::FlatBufferBuilder::CreateVector(v.data(), v.size());
  }

 private:
  FlatBufferAllocator mAllocator;
};

}  // namespace chre

#endif  // CHRE_PLATFORM_SHARED_FLATBUFFERS_HELPERS_H_
