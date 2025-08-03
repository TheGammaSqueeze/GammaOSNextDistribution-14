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

#ifndef ART_DEX2OAT_LINKER_RISCV64_RELATIVE_PATCHER_RISCV64_H_
#define ART_DEX2OAT_LINKER_RISCV64_RELATIVE_PATCHER_RISCV64_H_

#include "base/array_ref.h"
#include "linker/relative_patcher.h"

namespace art {

namespace linker {

class Riscv64RelativePatcher final : public RelativePatcher {
 public:
  Riscv64RelativePatcher(RelativePatcherThunkProvider* thunk_provider,
                         RelativePatcherTargetProvider* target_provider,
                         const Riscv64InstructionSetFeatures* features);

  uint32_t ReserveSpace(uint32_t offset,
                        const CompiledMethod* compiled_method,
                        MethodReference method_ref) override;
  uint32_t ReserveSpaceEnd(uint32_t offset) override;
  uint32_t WriteThunks(OutputStream* out, uint32_t offset) override;
  void PatchCall(std::vector<uint8_t>* code,
                 uint32_t literal_offset,
                 uint32_t patch_offset,
                 uint32_t target_offset) override;
  void PatchPcRelativeReference(std::vector<uint8_t>* code,
                                const LinkerPatch& patch,
                                uint32_t patch_offset,
                                uint32_t target_offset) override;
  void PatchEntrypointCall(std::vector<uint8_t>* code,
                           const LinkerPatch& patch,
                           uint32_t patch_offset) override;
  void PatchBakerReadBarrierBranch(std::vector<uint8_t>* code,
                                   const LinkerPatch& patch,
                                   uint32_t patch_offset) override;
  std::vector<debug::MethodDebugInfo> GenerateThunkDebugInfo(
      uint32_t executable_offset) override;

 private:
  static uint32_t PatchAuipc(uint32_t auipc, int32_t offset);

  static void SetInsn(std::vector<uint8_t>* code, uint32_t offset, uint32_t value);
  static uint32_t GetInsn(ArrayRef<const uint8_t> code, uint32_t offset);
  template <typename Alloc>
  uint32_t GetInsn(std::vector<uint8_t, Alloc>* code, uint32_t offset);

  DISALLOW_COPY_AND_ASSIGN(Riscv64RelativePatcher);
};

}  // namespace linker
}  // namespace art

#endif  // ART_DEX2OAT_LINKER_RISCV64_RELATIVE_PATCHER_RISCV64_H_
