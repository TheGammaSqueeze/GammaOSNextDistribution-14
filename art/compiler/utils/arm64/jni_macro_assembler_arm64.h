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

#ifndef ART_COMPILER_UTILS_ARM64_JNI_MACRO_ASSEMBLER_ARM64_H_
#define ART_COMPILER_UTILS_ARM64_JNI_MACRO_ASSEMBLER_ARM64_H_

#include <stdint.h>
#include <memory>
#include <vector>

#include <android-base/logging.h>

#include "assembler_arm64.h"
#include "base/arena_containers.h"
#include "base/enums.h"
#include "base/macros.h"
#include "offsets.h"
#include "utils/assembler.h"
#include "utils/jni_macro_assembler.h"

// TODO(VIXL): Make VIXL compile with -Wshadow.
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wshadow"
#include "aarch64/macro-assembler-aarch64.h"
#pragma GCC diagnostic pop

namespace art HIDDEN {
namespace arm64 {

class Arm64JNIMacroAssembler final : public JNIMacroAssemblerFwd<Arm64Assembler, PointerSize::k64> {
 public:
  explicit Arm64JNIMacroAssembler(ArenaAllocator* allocator)
      : JNIMacroAssemblerFwd(allocator) {}

  ~Arm64JNIMacroAssembler();

  // Finalize the code.
  void FinalizeCode() override;

  // Emit code that will create an activation on the stack.
  void BuildFrame(size_t frame_size,
                  ManagedRegister method_reg,
                  ArrayRef<const ManagedRegister> callee_save_regs) override;

  // Emit code that will remove an activation from the stack.
  void RemoveFrame(size_t frame_size,
                   ArrayRef<const ManagedRegister> callee_save_regs,
                   bool may_suspend) override;

  void IncreaseFrameSize(size_t adjust) override;
  void DecreaseFrameSize(size_t adjust) override;

  ManagedRegister CoreRegisterWithSize(ManagedRegister src, size_t size) override;

  // Store routines.
  void Store(FrameOffset offs, ManagedRegister src, size_t size) override;
  void Store(ManagedRegister base, MemberOffset offs, ManagedRegister src, size_t size) override;
  void StoreRawPtr(FrameOffset dest, ManagedRegister src) override;
  void StoreStackPointerToThread(ThreadOffset64 thr_offs, bool tag_sp) override;

  // Load routines.
  void Load(ManagedRegister dest, FrameOffset src, size_t size) override;
  void Load(ManagedRegister dest, ManagedRegister base, MemberOffset offs, size_t size) override;
  void LoadRawPtrFromThread(ManagedRegister dest, ThreadOffset64 offs) override;

  // Copying routines.
  void MoveArguments(ArrayRef<ArgumentLocation> dests,
                     ArrayRef<ArgumentLocation> srcs,
                     ArrayRef<FrameOffset> refs) override;
  void Move(ManagedRegister dest, ManagedRegister src, size_t size) override;
  void Move(ManagedRegister dest, size_t value) override;

  // Sign extension.
  void SignExtend(ManagedRegister mreg, size_t size) override;

  // Zero extension.
  void ZeroExtend(ManagedRegister mreg, size_t size) override;

  // Exploit fast access in managed code to Thread::Current().
  void GetCurrentThread(ManagedRegister dest) override;
  void GetCurrentThread(FrameOffset dest_offset) override;

  // Manipulating local reference table states.
  void LoadLocalReferenceTableStates(ManagedRegister jni_env_reg,
                                     ManagedRegister previous_state_reg,
                                     ManagedRegister current_state_reg) override;
  void StoreLocalReferenceTableStates(ManagedRegister jni_env_reg,
                                      ManagedRegister previous_state_reg,
                                      ManagedRegister current_state_reg) override;

  // Decode JNI transition or local `jobject`. For (weak) global `jobject`, jump to slow path.
  void DecodeJNITransitionOrLocalJObject(ManagedRegister reg,
                                         JNIMacroLabel* slow_path,
                                         JNIMacroLabel* resume) override;

  // Heap::VerifyObject on src. In some cases (such as a reference to this) we
  // know that src may not be null.
  void VerifyObject(ManagedRegister src, bool could_be_null) override;
  void VerifyObject(FrameOffset src, bool could_be_null) override;

  // Jump to address held at [base+offset] (used for tail calls).
  void Jump(ManagedRegister base, Offset offset) override;

  // Call to address held at [base+offset].
  void Call(ManagedRegister base, Offset offset) override;
  void CallFromThread(ThreadOffset64 offset) override;

  // Generate fast-path for transition to Native. Go to `label` if any thread flag is set.
  // The implementation can use `scratch_regs` which should be callee save core registers
  // (already saved before this call) and must preserve all argument registers.
  void TryToTransitionFromRunnableToNative(
      JNIMacroLabel* label, ArrayRef<const ManagedRegister> scratch_regs) override;

  // Generate fast-path for transition to Runnable. Go to `label` if any thread flag is set.
  // The implementation can use `scratch_regs` which should be core argument registers
  // not used as return registers and it must preserve the `return_reg` if any.
  void TryToTransitionFromNativeToRunnable(JNIMacroLabel* label,
                                           ArrayRef<const ManagedRegister> scratch_regs,
                                           ManagedRegister return_reg) override;

  // Generate suspend check and branch to `label` if there is a pending suspend request.
  void SuspendCheck(JNIMacroLabel* label) override;

  // Generate code to check if Thread::Current()->exception_ is non-null
  // and branch to the `label` if it is.
  void ExceptionPoll(JNIMacroLabel* label) override;
  // Deliver pending exception.
  void DeliverPendingException() override;

  // Create a new label that can be used with Jump/Bind calls.
  std::unique_ptr<JNIMacroLabel> CreateLabel() override;
  // Emit an unconditional jump to the label.
  void Jump(JNIMacroLabel* label) override;
  // Emit a conditional jump to the label by applying a unary condition test to the GC marking flag.
  void TestGcMarking(JNIMacroLabel* label, JNIMacroUnaryCondition cond) override;
  // Emit a conditional jump to the label by applying a unary condition test to object's mark bit.
  void TestMarkBit(ManagedRegister ref, JNIMacroLabel* label, JNIMacroUnaryCondition cond) override;
  // Emit a conditional jump to label if the loaded value from specified locations is not zero.
  void TestByteAndJumpIfNotZero(uintptr_t address, JNIMacroLabel* label) override;
  // Code at this offset will serve as the target for the Jump call.
  void Bind(JNIMacroLabel* label) override;

 private:
  void StoreWToOffset(StoreOperandType type,
                      WRegister source,
                      XRegister base,
                      int32_t offset);
  void StoreToOffset(XRegister source, XRegister base, int32_t offset);
  void StoreSToOffset(SRegister source, XRegister base, int32_t offset);
  void StoreDToOffset(DRegister source, XRegister base, int32_t offset);

  void LoadImmediate(XRegister dest,
                     int32_t value,
                     vixl::aarch64::Condition cond = vixl::aarch64::al);
  void Load(Arm64ManagedRegister dst, XRegister src, int32_t src_offset, size_t size);
  void LoadWFromOffset(LoadOperandType type,
                       WRegister dest,
                       XRegister base,
                       int32_t offset);
  void LoadFromOffset(XRegister dest, XRegister base, int32_t offset);
  void LoadSFromOffset(SRegister dest, XRegister base, int32_t offset);
  void LoadDFromOffset(DRegister dest, XRegister base, int32_t offset);

  void Copy(FrameOffset dest, FrameOffset src, size_t size);

  // Set up `out_reg` to hold a `jobject` (`StackReference<Object>*` to a spilled value),
  // or to be null if the value is null and `null_allowed`. `in_reg` holds a possibly
  // stale reference that can be used to avoid loading the spilled value to
  // see if the value is null.
  void CreateJObject(ManagedRegister out_reg,
                     FrameOffset spilled_reference_offset,
                     ManagedRegister in_reg,
                     bool null_allowed);

  // Set up `out_off` to hold a `jobject` (`StackReference<Object>*` to a spilled value),
  // or to be null if the value is null and `null_allowed`.
  void CreateJObject(FrameOffset out_off,
                     FrameOffset spilled_reference_offset,
                     bool null_allowed);

  void AddConstant(XRegister rd,
                   int32_t value,
                   vixl::aarch64::Condition cond = vixl::aarch64::al);
  void AddConstant(XRegister rd,
                   XRegister rn,
                   int32_t value,
                   vixl::aarch64::Condition cond = vixl::aarch64::al);
};

class Arm64JNIMacroLabel final
    : public JNIMacroLabelCommon<Arm64JNIMacroLabel,
                                 vixl::aarch64::Label,
                                 InstructionSet::kArm64> {
 public:
  vixl::aarch64::Label* AsArm64() {
    return AsPlatformLabel();
  }
};

}  // namespace arm64
}  // namespace art

#endif  // ART_COMPILER_UTILS_ARM64_JNI_MACRO_ASSEMBLER_ARM64_H_
