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

#ifndef ART_RUNTIME_NTERP_HELPERS_INL_H_
#define ART_RUNTIME_NTERP_HELPERS_INL_H_

#include "nterp_helpers.h"

namespace art HIDDEN {

ALWAYS_INLINE inline uint32_t GetNterpFastPathFlags(std::string_view shorty,
                                                    uint32_t access_flags,
                                                    InstructionSet isa) {
  bool all_parameters_are_reference = true;
  bool all_parameters_are_reference_or_int = true;
  for (size_t i = 1; i < shorty.length(); ++i) {
    if (shorty[i] != 'L') {
      all_parameters_are_reference = false;
      if (shorty[i] == 'F' || shorty[i] == 'D' || shorty[i] == 'J') {
        all_parameters_are_reference_or_int = false;
        break;
      }
    }
  }

  // Check for nterp entry fast-path based on shorty.
  uint32_t nterp_flags = 0u;
  if ((access_flags & kAccNative) == 0u && all_parameters_are_reference) {
    nterp_flags |= kAccNterpEntryPointFastPathFlag;
  }

  // Check for nterp invoke fast-path based on shorty.
  const bool no_float_return = shorty[0] != 'F' && shorty[0] != 'D';
  if (isa != InstructionSet::kRiscv64 && all_parameters_are_reference_or_int && no_float_return) {
    nterp_flags |= kAccNterpInvokeFastPathFlag;
  } else if (isa == InstructionSet::kRiscv64 && all_parameters_are_reference && no_float_return) {
    nterp_flags |= kAccNterpInvokeFastPathFlag;
  }
  return nterp_flags;
}

}  // namespace art

#endif  // ART_RUNTIME_NTERP_HELPERS_INL_H_
