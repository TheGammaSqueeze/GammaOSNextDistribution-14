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

#ifndef BERBERIS_GUEST_STATE_GUEST_ADDR_H_
#define BERBERIS_GUEST_STATE_GUEST_ADDR_H_

#include <cstdint>

namespace berberis {

// TODO(b/265372622): Make it configurable for specific guest arch.
using GuestAddr = uintptr_t;

constexpr GuestAddr kNullGuestAddr = {};

template <typename T>
inline GuestAddr ToGuestAddr(T* addr) {
  return reinterpret_cast<GuestAddr>(addr);
}

template <typename T>
inline T* ToHostAddr(GuestAddr addr) {
  return reinterpret_cast<T*>(addr);
}
}  // namespace berberis

#endif  // BERBERIS_GUEST_STATE_GUEST_ADDR_H_
