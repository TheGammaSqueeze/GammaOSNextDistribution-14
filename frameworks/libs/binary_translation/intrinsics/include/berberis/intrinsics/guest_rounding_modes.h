/*
 * Copyright (C) 2016 The Android Open Source Project
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

#ifndef BERBERIS_INTRINSICS_GUEST_FPSTATE_H_
#define BERBERIS_INTRINSICS_GUEST_FPSTATE_H_

// This interface allows to work with guest's fp-environment
// portion that is reflected in hosts' fp-environment.
// TODO(levarum): Rename file to reflect this.

#include <cfenv>  // FE_TONEAREST and friends.
#include <cstdint>

namespace berberis {

// Special rounding mode value to tell intrinsics
// and interpreter to use rounding mode stored on host.
const uint32_t FE_HOSTROUND = static_cast<uint32_t>(-1);
const uint32_t FE_TIESAWAY = static_cast<uint32_t>(-2);
static_assert(FE_HOSTROUND != FE_TONEAREST);
static_assert(FE_HOSTROUND != FE_UPWARD);
static_assert(FE_HOSTROUND != FE_DOWNWARD);
static_assert(FE_HOSTROUND != FE_TOWARDZERO);
static_assert(FE_TIESAWAY != FE_TONEAREST);
static_assert(FE_TIESAWAY != FE_UPWARD);
static_assert(FE_TIESAWAY != FE_DOWNWARD);
static_assert(FE_TIESAWAY != FE_TOWARDZERO);

class ScopedRoundingMode {
 public:
  ScopedRoundingMode() : saved_round_mode(std::fegetround()) {}
  ScopedRoundingMode(int rm) : saved_round_mode(std::fegetround()) { std::fesetround(rm); }
  ~ScopedRoundingMode() { std::fesetround(saved_round_mode); }

 private:
  int saved_round_mode;
};

}  // namespace berberis

#endif  // BERBERIS_INTRINSICS_GUEST_FPSTATE_H_
