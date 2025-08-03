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

#ifndef BERBERIS_GUEST_OS_PRIMITIVES_GUEST_MAP_SHADOW_H_
#define BERBERIS_GUEST_OS_PRIMITIVES_GUEST_MAP_SHADOW_H_

#include <cstddef>
#include <mutex>
#include <utility>

#include "berberis/base/arena_alloc.h"
#include "berberis/base/arena_vector.h"
#include "berberis/guest_state/guest_addr.h"

namespace berberis {

enum BitValue {
  kBitUnset,
  kBitSet,
  kBitMixed,
};

class GuestMapShadow {
 public:
  GuestMapShadow();
  ~GuestMapShadow();

  [[nodiscard]] BitValue GetExecutable(GuestAddr start, size_t size) const;

  // Check if region start..start+size is fully executable.
  [[nodiscard]] bool IsExecutable(GuestAddr start, size_t size) const;

  // Mark region start..start+size as executable.
  void SetExecutable(GuestAddr start, size_t size);

  // Mark region start..start+size as not executable.
  void ClearExecutable(GuestAddr start, size_t size);

  void RemapExecutable(GuestAddr old_start, size_t old_size, GuestAddr new_start, size_t new_size);

  void AddProtectedMapping(const void* start, const void* end);

  [[nodiscard]] bool IntersectsWithProtectedMapping(const void* start, const void* end);

  static GuestMapShadow* GetInstance();

 private:
  [[nodiscard]] bool IsExecAddr(GuestAddr addr) const;
  bool SetExecAddr(GuestAddr addr, int set);
  void CopyExecutable(GuestAddr from, size_t from_size, GuestAddr to, size_t to_size);

  uint8_t* shadow_;
  Arena arena_;
  std::mutex mutex_;
  // Mappings protected from guest tampering.
  // Warning: it's not optimized for quick look up since
  // we don't expect to store more than a few mappings.
  ArenaVector<std::pair<const void*, const void*>> protected_maps_;
};

}  // namespace berberis

#endif  // BERBERIS_GUEST_OS_PRIMITIVES_GUEST_MAP_SHADOW_H_
