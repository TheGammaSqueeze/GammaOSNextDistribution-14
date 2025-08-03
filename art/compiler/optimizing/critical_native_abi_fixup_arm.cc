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

#include "critical_native_abi_fixup_arm.h"

#include "intrinsics.h"
#include "nodes.h"

namespace art HIDDEN {
namespace arm {

// Fix up FP arguments passed in core registers for call to @CriticalNative by inserting fake calls
// to Float.floatToRawIntBits() or Double.doubleToRawLongBits() to satisfy type consistency checks.
static void FixUpArguments(HInvokeStaticOrDirect* invoke) {
  DCHECK_EQ(invoke->GetCodePtrLocation(), CodePtrLocation::kCallCriticalNative);
  size_t reg = 0u;
  for (size_t i = 0, num_args = invoke->GetNumberOfArguments(); i != num_args; ++i) {
    HInstruction* input = invoke->InputAt(i);
    DataType::Type input_type = input->GetType();
    size_t next_reg = reg + 1u;
    if (DataType::Is64BitType(input_type)) {
      reg = RoundUp(reg, 2u);
      next_reg = reg + 2u;
    }
    if (reg == 4u) {
      break;  // Remaining arguments are passed on stack.
    }
    if (DataType::IsFloatingPointType(input_type)) {
      InsertFpToIntegralIntrinsic(invoke, i);
    }
    reg = next_reg;
  }
}

bool CriticalNativeAbiFixupArm::Run() {
  if (!graph_->HasDirectCriticalNativeCall()) {
    return false;
  }

  for (HBasicBlock* block : graph_->GetReversePostOrder()) {
    for (HInstructionIterator it(block->GetInstructions()); !it.Done(); it.Advance()) {
      HInstruction* instruction = it.Current();
      if (instruction->IsInvokeStaticOrDirect() &&
          instruction->AsInvokeStaticOrDirect()->GetCodePtrLocation() ==
              CodePtrLocation::kCallCriticalNative) {
        FixUpArguments(instruction->AsInvokeStaticOrDirect());
      }
    }
  }
  return true;
}

}  // namespace arm
}  // namespace art
