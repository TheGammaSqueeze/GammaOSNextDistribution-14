/*
 * Copyright (C) 2017 The Android Open Source Project
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

#ifndef ART_RUNTIME_DEOPTIMIZATION_KIND_H_
#define ART_RUNTIME_DEOPTIMIZATION_KIND_H_

#include "base/logging.h"
#include "base/macros.h"

namespace art HIDDEN {

enum class DeoptimizationKind {
  kAotInlineCache = 0,
  kJitInlineCache,
  kJitSameTarget,
  kLoopBoundsBCE,
  kLoopNullBCE,
  kBlockBCE,
  kCHA,
  kDebugging,
  kFullFrame,
  kLast = kFullFrame
};

inline const char* GetDeoptimizationKindName(DeoptimizationKind kind) {
  switch (kind) {
    case DeoptimizationKind::kAotInlineCache: return "AOT inline cache";
    case DeoptimizationKind::kJitInlineCache: return "JIT inline cache";
    case DeoptimizationKind::kJitSameTarget: return "JIT same target";
    case DeoptimizationKind::kLoopBoundsBCE: return "loop bounds check elimination";
    case DeoptimizationKind::kLoopNullBCE: return "loop bounds check elimination on null";
    case DeoptimizationKind::kBlockBCE: return "block bounds check elimination";
    case DeoptimizationKind::kCHA: return "class hierarchy analysis";
    case DeoptimizationKind::kDebugging: return "Deopt requested for debug support";
    case DeoptimizationKind::kFullFrame: return "full frame";
  }
  LOG(FATAL) << "Unexpected kind " << static_cast<size_t>(kind);
  UNREACHABLE();
}

std::ostream& operator<<(std::ostream& os, const DeoptimizationKind& kind);

// We use a DeoptimizationStackSlot to record if a deoptimization is required
// for functions that are already on stack. The value in the slot specifies the
// reason we need to deoptimize.
enum class DeoptimizeFlagValue: uint8_t {
  kCHA = 0b001,
  kForceDeoptForRedefinition = 0b010,
  kCheckCallerForDeopt = 0b100,
  kAll = kCHA | kForceDeoptForRedefinition | kCheckCallerForDeopt
};

}  // namespace art

#endif  // ART_RUNTIME_DEOPTIMIZATION_KIND_H_
