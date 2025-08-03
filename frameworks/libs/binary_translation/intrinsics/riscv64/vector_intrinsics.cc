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

#include "berberis/intrinsics/vector_intrinsics.h"

#include <cstdint>
#include <tuple>

namespace berberis::intrinsics {

namespace {

constexpr uint64_t kVill = uint64_t{1} << 63;
constexpr uint64_t kVtypeNoVill = 0b1'1'111'111;

// Note: only 3bit vsew and 3bit vlmul are verified here.
// Vill is verified elsewhere, vma/vta are always valid and other bits are reserved and thus
// should be ignored for forward compatibility.
inline uint64_t VtypeToVlMax(uint8_t vtype) {
  constexpr uint8_t kVtypeToVlMax[64] = {
       16,  32,  64, 128,   0,   2,   4,   8,
        8,  16,  32,  64,   0,   1,   2,   4,
        4,   8,  16,  32,   0,   0,   1,   2,
        2,   4,   8,  16,   0,   0,   0,   1,
        0,   0,   0,   0,   0,   0,   0,   0,
        0,   0,   0,   0,   0,   0,   0,   0,
        0,   0,   0,   0,   0,   0,   0,   0,
        0,   0,   0,   0,   0,   0,   0,   0};
  return kVtypeToVlMax[vtype & 0b111'111];
}

inline uint64_t AvlToVl(uint64_t avl, uint64_t vlmax) {
  if (avl <= vlmax) {
    return avl;
  } else if (avl >= 2 * vlmax) {
    return vlmax;
  } else {
    return (avl + 1) / 2;
  }
}

}  // namespace

std::tuple<uint64_t, uint64_t> Vsetivli(uint8_t avl, uint16_t vtype) {
  return Vsetvli(avl, vtype);
}

std::tuple<uint64_t, uint64_t> Vsetvl(uint64_t avl, uint64_t vtype) {
  uint64_t vlmax = VtypeToVlMax(vtype);
  if (vlmax == 0) {
    return {0, kVill};
  }
  // Documentation is unclear about what should we do if someone attempts to set vill flag.
  // Clear it out for now.
  return {AvlToVl(avl, vlmax), vtype & kVtypeNoVill};
}

std::tuple<uint64_t, uint64_t> Vsetvli(uint64_t avl, uint16_t vtype) {
  uint64_t vlmax = VtypeToVlMax(vtype);
  if (vlmax == 0) {
    return {0, kVill};
  }
  // We wouldn't set vill bit with vsetivli instruction because range of immediates is too small.
  // Thus unlike Vsetvlmax we don't have anything to filter out here.
  return {AvlToVl(avl, vlmax), vtype};
}

std::tuple<uint64_t, uint64_t> Vsetvlmax(uint64_t vtype) {
  return Vsetvl(~0ULL, vtype);
}

std::tuple<uint64_t, uint64_t> Vsetvlimax(uint16_t vtype) {
  return Vsetvli(~0ULL, vtype);
}

std::tuple<uint64_t, uint64_t> Vtestvl(uint8_t vl_orig, uint64_t vtype_orig, uint64_t vtype_new) {
  if (vtype_orig & kVill) {
    return {0, kVill};
  }
  uint64_t vlmax_orig = VtypeToVlMax(vtype_orig);
  uint64_t vlmax_new = VtypeToVlMax(vtype_new);
  if (vlmax_orig != vlmax_new) {
    return {0, kVill};
  }
  return {vl_orig, vtype_new & kVtypeNoVill};
}

std::tuple<uint64_t, uint64_t> Vtestvli(uint8_t vl_orig, uint64_t vtype_orig, uint16_t vtype_new) {
  return Vtestvl(vl_orig, vtype_orig, vtype_new);
}

}  // namespace berberis::intrinsics
