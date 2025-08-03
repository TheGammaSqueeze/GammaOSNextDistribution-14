/*
 * Copyright 2021 The Android Open Source Project
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

#pragma once

#ifdef __cplusplus
#include <cstddef>
#include <cstdint>
#else
#include <stddef.h>
#include <stdint.h>
#endif  // _cplusplus

/* Define the header of each buffer used in the Bluetooth stack.
 */
typedef struct {
  uint16_t event;
  uint16_t len;
  uint16_t offset;
  uint16_t layer_specific;
  uint8_t data[];
} BT_HDR;

typedef struct {
  uint16_t event;
  uint16_t len;
  uint16_t offset;
  uint16_t layer_specific;
  // Note: Removal of flexible array member with no specified size.
  // This struct may be embedded in any position within other structs
  // and will not trigger various flexible member compilation issues.
} BT_HDR_RIGID;

#ifdef __cplusplus
template <typename T>
T* ToPacketData(BT_HDR* bt_hdr, size_t offset = 0) {
  return reinterpret_cast<T*>(bt_hdr->data + bt_hdr->offset + offset);
}
template <typename T>
const T* ToPacketData(const BT_HDR* bt_hdr, size_t offset = 0) {
  return reinterpret_cast<const T*>(bt_hdr->data + bt_hdr->offset + offset);
}
#endif  // __cplusplus

#define BT_HDR_SIZE (sizeof(BT_HDR))
