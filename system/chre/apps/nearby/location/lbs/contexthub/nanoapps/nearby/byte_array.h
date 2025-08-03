/*
 * Copyright (C) 2023 The Android Open Source Project
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

#ifndef LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_BYTE_ARRAY_H_
#define LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_BYTE_ARRAY_H_

#include <cstddef>
#include <cstdint>

namespace nearby {

// ByteArray struct includes a byte array together with the length of bytes.
// This makes it convenient to pass a byte array around with its length.
// The struct has the same lifetime of input data, i.e. it becomes invalid if
// data is reclaimed.
struct ByteArray {
  ByteArray() = default;
  ByteArray(uint8_t data[], size_t length) : data(data), length(length) {}

  uint8_t *data = nullptr;
  size_t length = 0;
};

}  // namespace nearby

#endif  // LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_BYTE_ARRAY_H_
