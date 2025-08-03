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

#ifndef ART_COMPILER_OPTIMIZING_JIT_PATCHES_ARM64_H_
#define ART_COMPILER_OPTIMIZING_JIT_PATCHES_ARM64_H_

#include "base/arena_allocator.h"
#include "base/arena_containers.h"
#include "dex/dex_file.h"
#include "dex/string_reference.h"
#include "dex/type_reference.h"
#include "handle.h"
#include "mirror/class.h"
#include "mirror/string.h"
#include "utils/arm64/assembler_arm64.h"

// TODO(VIXL): Make VIXL compile with -Wshadow.
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wshadow"
#include "aarch64/disasm-aarch64.h"
#include "aarch64/macro-assembler-aarch64.h"
#pragma GCC diagnostic pop

namespace art HIDDEN {

class CodeGenerationData;

namespace arm64 {

/**
 * Helper for emitting string or class literals into JIT generated code,
 * which can be shared between different compilers.
 */
class JitPatchesARM64 {
 public:
  JitPatchesARM64(Arm64Assembler* assembler, ArenaAllocator* allocator) :
      assembler_(assembler),
      uint32_literals_(std::less<uint32_t>(),
                       allocator->Adapter(kArenaAllocCodeGenerator)),
      uint64_literals_(std::less<uint64_t>(),
                       allocator->Adapter(kArenaAllocCodeGenerator)),
      jit_string_patches_(StringReferenceValueComparator(),
                          allocator->Adapter(kArenaAllocCodeGenerator)),
      jit_class_patches_(TypeReferenceValueComparator(),
                         allocator->Adapter(kArenaAllocCodeGenerator)) {
  }

  using Uint64ToLiteralMap = ArenaSafeMap<uint64_t, vixl::aarch64::Literal<uint64_t>*>;
  using Uint32ToLiteralMap = ArenaSafeMap<uint32_t, vixl::aarch64::Literal<uint32_t>*>;
  using StringToLiteralMap = ArenaSafeMap<StringReference,
                                          vixl::aarch64::Literal<uint32_t>*,
                                          StringReferenceValueComparator>;
  using TypeToLiteralMap = ArenaSafeMap<TypeReference,
                                        vixl::aarch64::Literal<uint32_t>*,
                                        TypeReferenceValueComparator>;

  vixl::aarch64::Literal<uint32_t>* DeduplicateUint32Literal(uint32_t value);
  vixl::aarch64::Literal<uint64_t>* DeduplicateUint64Literal(uint64_t value);
  vixl::aarch64::Literal<uint32_t>* DeduplicateBootImageAddressLiteral(uint64_t address);
  vixl::aarch64::Literal<uint32_t>* DeduplicateJitStringLiteral(
      const DexFile& dex_file,
      dex::StringIndex string_index,
      Handle<mirror::String> handle,
      CodeGenerationData* code_generation_data);
  vixl::aarch64::Literal<uint32_t>* DeduplicateJitClassLiteral(
      const DexFile& dex_file,
      dex::TypeIndex type_index,
      Handle<mirror::Class> handle,
      CodeGenerationData* code_generation_data);

  void EmitJitRootPatches(uint8_t* code,
                          const uint8_t* roots_data,
                          const CodeGenerationData& code_generation_data) const;

  Arm64Assembler* GetAssembler() const { return assembler_; }
  vixl::aarch64::MacroAssembler* GetVIXLAssembler() { return GetAssembler()->GetVIXLAssembler(); }

 private:
  Arm64Assembler* assembler_;
  // Deduplication map for 32-bit literals, used for JIT for boot image addresses.
  Uint32ToLiteralMap uint32_literals_;
  // Deduplication map for 64-bit literals, used for JIT for method address or method code.
  Uint64ToLiteralMap uint64_literals_;
  // Patches for string literals in JIT compiled code.
  StringToLiteralMap jit_string_patches_;
  // Patches for class literals in JIT compiled code.
  TypeToLiteralMap jit_class_patches_;
};

}  // namespace arm64

}  // namespace art

#endif  // ART_COMPILER_OPTIMIZING_JIT_PATCHES_ARM64_H_
