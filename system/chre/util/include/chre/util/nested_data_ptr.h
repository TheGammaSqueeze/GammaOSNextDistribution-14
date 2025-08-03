/*
 * Copyright (C) 2019 The Android Open Source Project
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

#ifndef UTIL_CHRE_NESTED_DATA_PTR_H_
#define UTIL_CHRE_NESTED_DATA_PTR_H_

#include <string.h>
#include <type_traits>

namespace chre {

/**
 * Template which provides type punning capability between the template type and
 * void*. Note that this void* representation must not be dereferenced - it is
 * only safe as a temporary representation of the underlying type, for example
 * passed through to a callback which accepts an opaque void* parameter.
 */
template <typename DataType>
union NestedDataPtr {
  static_assert(sizeof(DataType) <= sizeof(void *),
                "Requested data type must fit in a void* to use NestedDataPtr");
  // If the sizeof() check passes, then this is unlikely to be an issue, and in
  // many usage scenarios this wouldn't be an issue (e.g. reinterpreting a value
  // stored in a register), but it's included here just to be safe.
  static_assert(alignof(DataType) <= alignof(void *),
                "Additional alignment in NestedDataPtr can't be guaranteed");
  static_assert(std::is_trivially_copyable<DataType>::value,
                "Only trivially copyable types may be used in NestedDataPtr");

  NestedDataPtr() = default;

  explicit NestedDataPtr(DataType nestedData) : data(nestedData) {}
  explicit NestedDataPtr(void *ptr) {
    // We use memcpy here and in the void* conversion operator, as the C++11
    // language standard defines that accessing any field of a union other than
    // most recently set value is undefined behavior, unless it's a structure
    // with a common prefix to the active field. Most compilers (e.g. GCC,
    // clang) allow for this anyways as it's permitted in C, but to avoid the UB
    // we do the conversion via memcpy. Note that compilers will recognize this
    // as a simple store operation and produce equivalent assembly as if we were
    // assigning to mUnusedPtr.
    memcpy(&data, &ptr, sizeof(ptr));
  }

  // Implicit conversions
  operator DataType() const {
    return data;
  }

  operator void *() const {
    void *result;
    static_assert(sizeof(data) <= sizeof(result), "Broken assumption");
    memcpy(&result, &data, sizeof(data));
    return result;
  }

  DataType data;

 private:
  // Here to force that this union has at least the alignment + size of a
  // pointer, but we don't access it directly
  void *mUnusedPtr;
};

}  // namespace chre

#endif  // UTIL_CHRE_NESTED_DATA_PTR_H_