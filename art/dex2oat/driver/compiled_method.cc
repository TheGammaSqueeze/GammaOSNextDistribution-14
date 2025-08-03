/*
 * Copyright (C) 2011 The Android Open Source Project
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

#include "compiled_method.h"

#include "driver/compiled_method_storage.h"
#include "utils/swap_space.h"

namespace art {

CompiledCode::CompiledCode(CompiledMethodStorage* storage,
                           InstructionSet instruction_set,
                           const ArrayRef<const uint8_t>& quick_code)
    : storage_(storage),
      quick_code_(storage->DeduplicateCode(quick_code)),
      packed_fields_(InstructionSetField::Encode(instruction_set)) {
}

CompiledCode::~CompiledCode() {
  GetStorage()->ReleaseCode(quick_code_);
}

bool CompiledCode::operator==(const CompiledCode& rhs) const {
  if (quick_code_ != nullptr) {
    if (rhs.quick_code_ == nullptr) {
      return false;
    } else if (quick_code_->size() != rhs.quick_code_->size()) {
      return false;
    } else {
      return std::equal(quick_code_->begin(), quick_code_->end(), rhs.quick_code_->begin());
    }
  }
  return (rhs.quick_code_ == nullptr);
}

size_t CompiledCode::AlignCode(size_t offset) const {
  return AlignCode(offset, GetInstructionSet());
}

size_t CompiledCode::AlignCode(size_t offset, InstructionSet instruction_set) {
  return RoundUp(offset, GetInstructionSetCodeAlignment(instruction_set));
}

size_t CompiledCode::GetEntryPointAdjustment() const {
  return GetInstructionSetEntryPointAdjustment(GetInstructionSet());
}

CompiledMethod::CompiledMethod(CompiledMethodStorage* storage,
                               InstructionSet instruction_set,
                               const ArrayRef<const uint8_t>& quick_code,
                               const ArrayRef<const uint8_t>& vmap_table,
                               const ArrayRef<const uint8_t>& cfi_info,
                               const ArrayRef<const linker::LinkerPatch>& patches)
    : CompiledCode(storage, instruction_set, quick_code),
      vmap_table_(storage->DeduplicateVMapTable(vmap_table)),
      cfi_info_(storage->DeduplicateCFIInfo(cfi_info)),
      patches_(storage->DeduplicateLinkerPatches(patches)) {
}

CompiledMethod* CompiledMethod::SwapAllocCompiledMethod(
    CompiledMethodStorage* storage,
    InstructionSet instruction_set,
    const ArrayRef<const uint8_t>& quick_code,
    const ArrayRef<const uint8_t>& vmap_table,
    const ArrayRef<const uint8_t>& cfi_info,
    const ArrayRef<const linker::LinkerPatch>& patches) {
  SwapAllocator<CompiledMethod> alloc(storage->GetSwapSpaceAllocator());
  CompiledMethod* ret = alloc.allocate(1);
  alloc.construct(ret,
                  storage,
                  instruction_set,
                  quick_code,
                  vmap_table,
                  cfi_info, patches);
  return ret;
}

void CompiledMethod::ReleaseSwapAllocatedCompiledMethod(CompiledMethodStorage* storage,
                                                        CompiledMethod* m) {
  SwapAllocator<CompiledMethod> alloc(storage->GetSwapSpaceAllocator());
  alloc.destroy(m);
  alloc.deallocate(m, 1);
}

CompiledMethod::~CompiledMethod() {
  CompiledMethodStorage* storage = GetStorage();
  storage->ReleaseLinkerPatches(patches_);
  storage->ReleaseCFIInfo(cfi_info_);
  storage->ReleaseVMapTable(vmap_table_);
}

}  // namespace art
