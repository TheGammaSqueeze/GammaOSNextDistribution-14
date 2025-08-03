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

#include "code_generation_data.h"
#include "gc_root.h"
#include "jit_patches_arm64.h"

namespace art HIDDEN {

namespace arm64 {

vixl::aarch64::Literal<uint32_t>* JitPatchesARM64::DeduplicateUint32Literal(
    uint32_t value) {
  return uint32_literals_.GetOrCreate(
      value,
      [this, value]() {
        return GetVIXLAssembler()->CreateLiteralDestroyedWithPool<uint32_t>(value);
      });
}

vixl::aarch64::Literal<uint64_t>* JitPatchesARM64::DeduplicateUint64Literal(
    uint64_t value) {
  return uint64_literals_.GetOrCreate(
      value,
      [this, value]() {
        return GetVIXLAssembler()->CreateLiteralDestroyedWithPool<uint64_t>(value);
      });
}

static void PatchJitRootUse(uint8_t* code,
                            const uint8_t* roots_data,
                            vixl::aarch64::Literal<uint32_t>* literal,
                            uint64_t index_in_table) {
  uint32_t literal_offset = literal->GetOffset();
  uintptr_t address =
      reinterpret_cast<uintptr_t>(roots_data) + index_in_table * sizeof(GcRoot<mirror::Object>);
  uint8_t* data = code + literal_offset;
  reinterpret_cast<uint32_t*>(data)[0] = dchecked_integral_cast<uint32_t>(address);
}

void JitPatchesARM64::EmitJitRootPatches(
    uint8_t* code,
    const uint8_t* roots_data,
    const CodeGenerationData& code_generation_data) const {
  for (const auto& entry : jit_string_patches_) {
    const StringReference& string_reference = entry.first;
    vixl::aarch64::Literal<uint32_t>* table_entry_literal = entry.second;
    uint64_t index_in_table = code_generation_data.GetJitStringRootIndex(string_reference);
    PatchJitRootUse(code, roots_data, table_entry_literal, index_in_table);
  }
  for (const auto& entry : jit_class_patches_) {
    const TypeReference& type_reference = entry.first;
    vixl::aarch64::Literal<uint32_t>* table_entry_literal = entry.second;
    uint64_t index_in_table = code_generation_data.GetJitClassRootIndex(type_reference);
    PatchJitRootUse(code, roots_data, table_entry_literal, index_in_table);
  }
}

vixl::aarch64::Literal<uint32_t>* JitPatchesARM64::DeduplicateBootImageAddressLiteral(
    uint64_t address) {
  return DeduplicateUint32Literal(dchecked_integral_cast<uint32_t>(address));
}

vixl::aarch64::Literal<uint32_t>* JitPatchesARM64::DeduplicateJitStringLiteral(
    const DexFile& dex_file,
    dex::StringIndex string_index,
    Handle<mirror::String> handle,
    CodeGenerationData* code_generation_data) {
  code_generation_data->ReserveJitStringRoot(StringReference(&dex_file, string_index), handle);
  return jit_string_patches_.GetOrCreate(
      StringReference(&dex_file, string_index),
      [this]() {
        return GetVIXLAssembler()->CreateLiteralDestroyedWithPool<uint32_t>(/* value= */ 0u);
      });
}

vixl::aarch64::Literal<uint32_t>* JitPatchesARM64::DeduplicateJitClassLiteral(
    const DexFile& dex_file,
    dex::TypeIndex type_index,
    Handle<mirror::Class> handle,
    CodeGenerationData* code_generation_data) {
  code_generation_data->ReserveJitClassRoot(TypeReference(&dex_file, type_index), handle);
  return jit_class_patches_.GetOrCreate(
      TypeReference(&dex_file, type_index),
      [this]() {
        return GetVIXLAssembler()->CreateLiteralDestroyedWithPool<uint32_t>(/* value= */ 0u);
      });
}

}  // namespace arm64
}  // namespace art
