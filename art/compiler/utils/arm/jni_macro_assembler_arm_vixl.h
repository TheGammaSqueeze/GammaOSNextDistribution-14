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

#ifndef ART_COMPILER_UTILS_ARM_JNI_MACRO_ASSEMBLER_ARM_VIXL_H_
#define ART_COMPILER_UTILS_ARM_JNI_MACRO_ASSEMBLER_ARM_VIXL_H_

#include <android-base/logging.h>

#include "base/arena_containers.h"
#include "base/macros.h"
#include "constants_arm.h"
#include "offsets.h"
#include "utils/arm/assembler_arm_vixl.h"
#include "utils/arm/managed_register_arm.h"
#include "utils/assembler.h"
#include "utils/jni_macro_assembler.h"

namespace art HIDDEN {
namespace arm {

class ArmVIXLJNIMacroAssembler final
    : public JNIMacroAssemblerFwd<ArmVIXLAssembler, PointerSize::k32> {
 public:
  explicit ArmVIXLJNIMacroAssembler(ArenaAllocator* allocator)
      : JNIMacroAssemblerFwd(allocator) {}

  virtual ~ArmVIXLJNIMacroAssembler() {}
  void FinalizeCode() override;

  //
  // Overridden common assembler high-level functionality
  //

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

  void StoreStackPointerToThread(ThreadOffset32 thr_offs, bool tag_sp) override;

  // Load routines.
  void Load(ManagedRegister dest, FrameOffset src, size_t size) override;
  void Load(ManagedRegister dest, ManagedRegister base, MemberOffset offs, size_t size) override;

  void LoadRawPtrFromThread(ManagedRegister dest, ThreadOffset32 offs) override;

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
  void CallFromThread(ThreadOffset32 offset) override;

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
  void Copy(FrameOffset dest, FrameOffset src, size_t size);
  void Load(ArmManagedRegister dest, vixl32::Register base, int32_t offset, size_t size);

  // Set up `out_reg` to hold a `jobject` (`StackReference<Object>*` to a spilled value),
  // or to be null if the value is null and `null_allowed`. `in_reg` holds a possibly
  // stale reference that can be used to avoid loading the spilled value to
  // see if the value is null.
  void CreateJObject(ManagedRegister out_reg,
                     FrameOffset spilled_reference_offset,
                     ManagedRegister in_reg,
                     bool null_allowed);

  // Used for testing.
  ART_FRIEND_TEST(ArmVIXLAssemblerTest, VixlJniHelpers);
  ART_FRIEND_TEST(ArmVIXLAssemblerTest, VixlLoadFromOffset);
  ART_FRIEND_TEST(ArmVIXLAssemblerTest, VixlStoreToOffset);
};

class ArmVIXLJNIMacroLabel final
    : public JNIMacroLabelCommon<ArmVIXLJNIMacroLabel,
                                 vixl32::Label,
                                 InstructionSet::kArm> {
 public:
  vixl32::Label* AsArm() {
    return AsPlatformLabel();
  }
};

}  // namespace arm
}  // namespace art

#endif  // ART_COMPILER_UTILS_ARM_JNI_MACRO_ASSEMBLER_ARM_VIXL_H_
