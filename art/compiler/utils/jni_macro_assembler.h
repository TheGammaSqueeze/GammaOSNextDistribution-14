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

#ifndef ART_COMPILER_UTILS_JNI_MACRO_ASSEMBLER_H_
#define ART_COMPILER_UTILS_JNI_MACRO_ASSEMBLER_H_

#include <vector>

#include <android-base/logging.h>

#include "arch/instruction_set.h"
#include "base/arena_allocator.h"
#include "base/arena_object.h"
#include "base/array_ref.h"
#include "base/enums.h"
#include "base/macros.h"
#include "managed_register.h"
#include "offsets.h"

namespace art HIDDEN {

class ArenaAllocator;
class DebugFrameOpCodeWriterForAssembler;
class InstructionSetFeatures;
class MemoryRegion;
class JNIMacroLabel;

enum class JNIMacroUnaryCondition {
  kZero,
  kNotZero
};

class ArgumentLocation {
 public:
  ArgumentLocation(ManagedRegister reg, size_t size)
      : reg_(reg), frame_offset_(0u), size_(size) {
    DCHECK(reg.IsRegister());
  }

  ArgumentLocation(FrameOffset frame_offset, size_t size)
      : reg_(ManagedRegister::NoRegister()), frame_offset_(frame_offset), size_(size) {}

  bool IsRegister() const {
    return reg_.IsRegister();
  }

  ManagedRegister GetRegister() const {
    DCHECK(IsRegister());
    return reg_;
  }

  FrameOffset GetFrameOffset() const {
    DCHECK(!IsRegister());
    return frame_offset_;
  }

  size_t GetSize() const {
    return size_;
  }

 private:
  ManagedRegister reg_;
  FrameOffset frame_offset_;
  size_t size_;
};

template <PointerSize kPointerSize>
class JNIMacroAssembler : public DeletableArenaObject<kArenaAllocAssembler> {
 public:
  static std::unique_ptr<JNIMacroAssembler<kPointerSize>> Create(
      ArenaAllocator* allocator,
      InstructionSet instruction_set,
      const InstructionSetFeatures* instruction_set_features = nullptr);

  // Finalize the code; emit slow paths, fixup branches, add literal pool, etc.
  virtual void FinalizeCode() = 0;

  // Size of generated code
  virtual size_t CodeSize() const = 0;

  // Copy instructions out of assembly buffer into the given region of memory
  virtual void CopyInstructions(const MemoryRegion& region) = 0;

  // Emit code that will create an activation on the stack
  virtual void BuildFrame(size_t frame_size,
                          ManagedRegister method_reg,
                          ArrayRef<const ManagedRegister> callee_save_regs) = 0;

  // Emit code that will remove an activation from the stack
  //
  // Argument `may_suspend` must be `true` if the compiled method may be
  // suspended during its execution (otherwise `false`, if it is impossible
  // to suspend during its execution).
  virtual void RemoveFrame(size_t frame_size,
                           ArrayRef<const ManagedRegister> callee_save_regs,
                           bool may_suspend) = 0;

  virtual void IncreaseFrameSize(size_t adjust) = 0;
  virtual void DecreaseFrameSize(size_t adjust) = 0;

  // Return the same core register but with correct size if the architecture-specific
  // ManagedRegister has different representation for different sizes.
  virtual ManagedRegister CoreRegisterWithSize(ManagedRegister src, size_t size) = 0;

  // Store routines
  virtual void Store(FrameOffset offs, ManagedRegister src, size_t size) = 0;
  virtual void Store(ManagedRegister base, MemberOffset offs, ManagedRegister src, size_t size) = 0;
  virtual void StoreRawPtr(FrameOffset dest, ManagedRegister src) = 0;

  // Stores stack pointer by tagging it if required so we can walk the stack. In debuggable runtimes
  // we use tag to tell if we are using JITed code or AOT code. In non-debuggable runtimes we never
  // use JITed code when AOT code is present. So checking for AOT code is sufficient to detect which
  // code is being executed. We avoid tagging in non-debuggable runtimes to reduce instructions.
  virtual void StoreStackPointerToThread(ThreadOffset<kPointerSize> thr_offs, bool tag_sp) = 0;

  // Load routines
  virtual void Load(ManagedRegister dest, FrameOffset src, size_t size) = 0;
  virtual void Load(ManagedRegister dest, ManagedRegister base, MemberOffset offs, size_t size) = 0;
  virtual void LoadRawPtrFromThread(ManagedRegister dest, ThreadOffset<kPointerSize> offs) = 0;

  // Load reference from a `GcRoot<>`. The default is to load as `jint`. Some architectures
  // (say, RISC-V) override this to provide a different sign- or zero-extension.
  virtual void LoadGcRootWithoutReadBarrier(ManagedRegister dest,
                                            ManagedRegister base,
                                            MemberOffset offs);

  // Load reference from a `StackReference<>`. The default is to load as `jint`. Some architectures
  // (say, RISC-V) override this to provide a different sign- or zero-extension.
  virtual void LoadStackReference(ManagedRegister dest, FrameOffset offs);

  // Copying routines

  // Move arguments from `srcs` locations to `dests` locations.
  //
  // References shall be spilled to `refs` frame offsets (kInvalidReferenceOffset indicates
  // a non-reference type) if they are in registers and corresponding `dests` shall be
  // filled with `jobject` replacements. If the first argument is a reference, it is
  // assumed to be `this` and cannot be null, all other reference arguments can be null.
  virtual void MoveArguments(ArrayRef<ArgumentLocation> dests,
                             ArrayRef<ArgumentLocation> srcs,
                             ArrayRef<FrameOffset> refs) = 0;

  virtual void Move(ManagedRegister dest, ManagedRegister src, size_t size) = 0;

  virtual void Move(ManagedRegister dst, size_t value) = 0;

  // Sign extension
  virtual void SignExtend(ManagedRegister mreg, size_t size) = 0;

  // Zero extension
  virtual void ZeroExtend(ManagedRegister mreg, size_t size) = 0;

  // Exploit fast access in managed code to Thread::Current()
  virtual void GetCurrentThread(ManagedRegister dest) = 0;
  virtual void GetCurrentThread(FrameOffset dest_offset) = 0;

  // Manipulating local reference table states.
  //
  // These have a default implementation but they can be overridden to use register pair
  // load/store instructions on architectures that support them (arm, arm64).
  virtual void LoadLocalReferenceTableStates(ManagedRegister jni_env_reg,
                                             ManagedRegister previous_state_reg,
                                             ManagedRegister current_state_reg);
  virtual void StoreLocalReferenceTableStates(ManagedRegister jni_env_reg,
                                              ManagedRegister previous_state_reg,
                                              ManagedRegister current_state_reg);

  // Decode JNI transition or local `jobject`. For (weak) global `jobject`, jump to slow path.
  virtual void DecodeJNITransitionOrLocalJObject(ManagedRegister reg,
                                                 JNIMacroLabel* slow_path,
                                                 JNIMacroLabel* resume) = 0;

  // Heap::VerifyObject on src. In some cases (such as a reference to this) we
  // know that src may not be null.
  virtual void VerifyObject(ManagedRegister src, bool could_be_null) = 0;
  virtual void VerifyObject(FrameOffset src, bool could_be_null) = 0;

  // Jump to address held at [base+offset] (used for tail calls).
  virtual void Jump(ManagedRegister base, Offset offset) = 0;

  // Call to address held at [base+offset]
  virtual void Call(ManagedRegister base, Offset offset) = 0;
  virtual void CallFromThread(ThreadOffset<kPointerSize> offset) = 0;

  // Generate fast-path for transition to Native. Go to `label` if any thread flag is set.
  // The implementation can use `scratch_regs` which should be callee save core registers
  // (already saved before this call) and must preserve all argument registers.
  virtual void TryToTransitionFromRunnableToNative(
      JNIMacroLabel* label, ArrayRef<const ManagedRegister> scratch_regs) = 0;

  // Generate fast-path for transition to Runnable. Go to `label` if any thread flag is set.
  // The implementation can use `scratch_regs` which should be core argument registers
  // not used as return registers and it must preserve the `return_reg` if any.
  virtual void TryToTransitionFromNativeToRunnable(JNIMacroLabel* label,
                                                   ArrayRef<const ManagedRegister> scratch_regs,
                                                   ManagedRegister return_reg) = 0;

  // Generate suspend check and branch to `label` if there is a pending suspend request.
  virtual void SuspendCheck(JNIMacroLabel* label) = 0;

  // Generate code to check if Thread::Current()->exception_ is non-null
  // and branch to the `label` if it is.
  virtual void ExceptionPoll(JNIMacroLabel* label) = 0;
  // Deliver pending exception.
  virtual void DeliverPendingException() = 0;

  // Create a new label that can be used with Jump/Bind calls.
  virtual std::unique_ptr<JNIMacroLabel> CreateLabel() = 0;
  // Emit an unconditional jump to the label.
  virtual void Jump(JNIMacroLabel* label) = 0;
  // Emit a conditional jump to the label by applying a unary condition test to the GC marking flag.
  virtual void TestGcMarking(JNIMacroLabel* label, JNIMacroUnaryCondition cond) = 0;
  // Emit a conditional jump to the label by applying a unary condition test to object's mark bit.
  virtual void TestMarkBit(ManagedRegister ref,
                           JNIMacroLabel* label,
                           JNIMacroUnaryCondition cond) = 0;
  // Emit a conditional jump to label if the loaded value from specified locations is not zero.
  virtual void TestByteAndJumpIfNotZero(uintptr_t address, JNIMacroLabel* label) = 0;
  // Code at this offset will serve as the target for the Jump call.
  virtual void Bind(JNIMacroLabel* label) = 0;

  virtual ~JNIMacroAssembler() {}

  /**
   * @brief Buffer of DWARF's Call Frame Information opcodes.
   * @details It is used by debuggers and other tools to unwind the call stack.
   */
  virtual DebugFrameOpCodeWriterForAssembler& cfi() = 0;

  void SetEmitRunTimeChecksInDebugMode(bool value) {
    emit_run_time_checks_in_debug_mode_ = value;
  }

  static constexpr FrameOffset kInvalidReferenceOffset = FrameOffset(0);

 protected:
  JNIMacroAssembler() {}

  // Should run-time checks be emitted in debug mode?
  bool emit_run_time_checks_in_debug_mode_ = false;
};

// A "Label" class used with the JNIMacroAssembler
// allowing one to use branches (jumping from one place to another).
//
// This is just an interface, so every platform must provide
// its own implementation of it.
//
// It is only safe to use a label created
// via JNIMacroAssembler::CreateLabel with that same macro assembler.
class JNIMacroLabel : public DeletableArenaObject<kArenaAllocAssembler> {
 public:
  virtual ~JNIMacroLabel() = 0;

  const InstructionSet isa_;
 protected:
  explicit JNIMacroLabel(InstructionSet isa) : isa_(isa) {}
};

inline JNIMacroLabel::~JNIMacroLabel() {
  // Compulsory definition for a pure virtual destructor
  // to avoid linking errors.
}

template <typename T, PointerSize kPointerSize>
class JNIMacroAssemblerFwd : public JNIMacroAssembler<kPointerSize> {
 public:
  void FinalizeCode() override {
    asm_.FinalizeCode();
  }

  size_t CodeSize() const override {
    return asm_.CodeSize();
  }

  void CopyInstructions(const MemoryRegion& region) override {
    asm_.CopyInstructions(region);
  }

  DebugFrameOpCodeWriterForAssembler& cfi() override {
    return asm_.cfi();
  }

 protected:
  explicit JNIMacroAssemblerFwd(ArenaAllocator* allocator) : asm_(allocator) {}

  T asm_;
};

template <typename Self, typename PlatformLabel, InstructionSet kIsa>
class JNIMacroLabelCommon : public JNIMacroLabel {
 public:
  static Self* Cast(JNIMacroLabel* label) {
    CHECK(label != nullptr);
    CHECK_EQ(kIsa, label->isa_);

    return reinterpret_cast<Self*>(label);
  }

 protected:
  PlatformLabel* AsPlatformLabel() {
    return &label_;
  }

  JNIMacroLabelCommon() : JNIMacroLabel(kIsa) {
  }

  ~JNIMacroLabelCommon() override {}

 private:
  PlatformLabel label_;
};

}  // namespace art

#endif  // ART_COMPILER_UTILS_JNI_MACRO_ASSEMBLER_H_
