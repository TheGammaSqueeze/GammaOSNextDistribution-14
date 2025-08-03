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

#include "relative_patcher_riscv64.h"

#include "base/bit_utils.h"
#include "debug/method_debug_info.h"
#include "linker/linker_patch.h"

namespace art {
namespace linker {

Riscv64RelativePatcher::Riscv64RelativePatcher(
    [[maybe_unused]] RelativePatcherThunkProvider* thunk_provider,
    [[maybe_unused]] RelativePatcherTargetProvider* target_provider,
    [[maybe_unused]] const Riscv64InstructionSetFeatures* features)
    : RelativePatcher() {
}

uint32_t Riscv64RelativePatcher::ReserveSpace(
    uint32_t offset,
    [[maybe_unused]] const CompiledMethod* compiled_method,
    [[maybe_unused]] MethodReference method_ref) {
  // TODO(riscv64): Reduce code size for AOT by using shared trampolines for slow path
  // runtime calls across the entire oat file. These need space reserved here.
  return offset;
}

uint32_t Riscv64RelativePatcher::ReserveSpaceEnd(uint32_t offset) {
  // TODO(riscv64): Reduce code size for AOT by using shared trampolines for slow path
  // runtime calls across the entire oat file. These need space reserved here.
  return offset;
}

uint32_t Riscv64RelativePatcher::WriteThunks([[maybe_unused]] OutputStream* out, uint32_t offset) {
  // TODO(riscv64): Reduce code size for AOT by using shared trampolines for slow path
  // runtime calls across the entire oat file. These need to be written here.
  return offset;
}

void Riscv64RelativePatcher::PatchCall([[maybe_unused]] std::vector<uint8_t>* code,
                                       [[maybe_unused]] uint32_t literal_offset,
                                       [[maybe_unused]] uint32_t patch_offset,
                                       [[maybe_unused]] uint32_t target_offset) {
  // Direct calls are currently not used on any architecture.
  UNIMPLEMENTED(FATAL) << "Unsupported direct call.";
}

void Riscv64RelativePatcher::PatchPcRelativeReference(std::vector<uint8_t>* code,
                                                      const LinkerPatch& patch,
                                                      uint32_t patch_offset,
                                                      uint32_t target_offset) {
  DCHECK_ALIGNED(patch_offset, 2u);
  DCHECK_ALIGNED(target_offset, 2u);
  uint32_t literal_offset = patch.LiteralOffset();
  uint32_t insn = GetInsn(code, literal_offset);
  uint32_t pc_insn_offset = patch.PcInsnOffset();
  uint32_t disp = target_offset - (patch_offset - literal_offset + pc_insn_offset);
  if (literal_offset == pc_insn_offset) {
    // Check it's an AUIPC with imm == 0x12345 (unset).
    DCHECK_EQ((insn & 0xfffff07fu), 0x12345017u)
        << literal_offset << ", " << pc_insn_offset << ", 0x" << std::hex << insn;
    insn = PatchAuipc(insn, disp);
  } else {
    DCHECK_EQ((insn & 0xfff00000u), 0x67800000u);
    CHECK((insn & 0x0000707fu) == 0x00000013u ||  // ADD
          (insn & 0x0000707fu) == 0x00006003u ||  // LWU
          (insn & 0x0000707fu) == 0x00003003u)    // LD
        << "insn: 0x" << std::hex << insn << ", type: " << patch.GetType();
    // Check that pc_insn_offset points to AUIPC with matching register.
    DCHECK_EQ(GetInsn(code, pc_insn_offset) & 0x00000fffu,
              0x00000017 | (((insn >> 15) & 0x1fu) << 7));
    uint32_t imm12 = disp & 0xfffu;  // The instruction shall sign-extend this immediate.
    insn = (insn & ~(0xfffu << 20)) | (imm12 << 20);
  }
  SetInsn(code, literal_offset, insn);
}

void Riscv64RelativePatcher::PatchEntrypointCall([[maybe_unused]] std::vector<uint8_t>* code,
                                                 [[maybe_unused]] const LinkerPatch& patch,
                                                 [[maybe_unused]] uint32_t patch_offset) {
  // TODO(riscv64): Reduce code size for AOT by using shared trampolines for slow path
  // runtime calls across the entire oat file. Calls to these trapolines need to be patched here.
  UNIMPLEMENTED(FATAL) << "Shared entrypoint trampolines are not implemented.";
}

void Riscv64RelativePatcher::PatchBakerReadBarrierBranch(
    [[maybe_unused]] std::vector<uint8_t>* code,
    [[maybe_unused]] const LinkerPatch& patch,
    [[maybe_unused]] uint32_t patch_offset) {
  // Baker read barrier with introspection is not implemented.
  // Such implementation is impractical given the short reach of conditional branches.
  UNIMPLEMENTED(FATAL) << "Baker read barrier branches are not used on riscv64.";
}

std::vector<debug::MethodDebugInfo> Riscv64RelativePatcher::GenerateThunkDebugInfo(
      [[maybe_unused]] uint32_t executable_offset) {
  // TODO(riscv64): Reduce code size for AOT by using shared trampolines for slow path
  // runtime calls across the entire oat file. These need debug info generated here.
  return {};
}

uint32_t Riscv64RelativePatcher::PatchAuipc(uint32_t auipc, int32_t offset) {
  // The highest 0x800 values are out of range.
  DCHECK_LT(offset, 0x7ffff800);
  // Round `offset` to nearest 4KiB offset because short offset has range [-0x800, 0x800).
  int32_t near_offset = (offset + 0x800) & ~0xfff;
  // Extract the `imm20`.
  uint32_t imm20 = static_cast<uint32_t>(near_offset) >> 12;
  return (auipc & 0x00000fffu) |  // Clear offset bits, keep AUIPC with destination reg.
         (imm20 << 12);           // Encode the immediate.
}

void Riscv64RelativePatcher::SetInsn(std::vector<uint8_t>* code, uint32_t offset, uint32_t value) {
  DCHECK_LE(offset + 4u, code->size());
  DCHECK_ALIGNED(offset, 2u);
  uint8_t* addr = &(*code)[offset];
  addr[0] = (value >> 0) & 0xff;
  addr[1] = (value >> 8) & 0xff;
  addr[2] = (value >> 16) & 0xff;
  addr[3] = (value >> 24) & 0xff;
}

uint32_t Riscv64RelativePatcher::GetInsn(ArrayRef<const uint8_t> code, uint32_t offset) {
  DCHECK_LE(offset + 4u, code.size());
  DCHECK_ALIGNED(offset, 2u);
  const uint8_t* addr = &code[offset];
  return
      (static_cast<uint32_t>(addr[0]) << 0) +
      (static_cast<uint32_t>(addr[1]) << 8) +
      (static_cast<uint32_t>(addr[2]) << 16)+
      (static_cast<uint32_t>(addr[3]) << 24);
}

template <typename Alloc>
uint32_t Riscv64RelativePatcher::GetInsn(std::vector<uint8_t, Alloc>* code, uint32_t offset) {
  return GetInsn(ArrayRef<const uint8_t>(*code), offset);
}

}  // namespace linker
}  // namespace art
