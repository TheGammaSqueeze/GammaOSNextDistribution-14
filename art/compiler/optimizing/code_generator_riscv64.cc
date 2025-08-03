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

#include "code_generator_riscv64.h"

#include "android-base/logging.h"
#include "android-base/macros.h"
#include "arch/riscv64/jni_frame_riscv64.h"
#include "arch/riscv64/registers_riscv64.h"
#include "base/arena_containers.h"
#include "base/macros.h"
#include "class_root-inl.h"
#include "code_generator_utils.h"
#include "dwarf/register.h"
#include "gc/heap.h"
#include "gc/space/image_space.h"
#include "heap_poisoning.h"
#include "intrinsics_list.h"
#include "intrinsics_riscv64.h"
#include "jit/profiling_info.h"
#include "linker/linker_patch.h"
#include "mirror/class-inl.h"
#include "optimizing/nodes.h"
#include "optimizing/profiling_info_builder.h"
#include "runtime.h"
#include "scoped_thread_state_change-inl.h"
#include "stack_map_stream.h"
#include "trace.h"
#include "utils/label.h"
#include "utils/riscv64/assembler_riscv64.h"
#include "utils/stack_checks.h"

namespace art HIDDEN {
namespace riscv64 {

// Placeholder values embedded in instructions, patched at link time.
constexpr uint32_t kLinkTimeOffsetPlaceholderHigh = 0x12345;
constexpr uint32_t kLinkTimeOffsetPlaceholderLow = 0x678;

// Compare-and-jump packed switch generates approx. 3 + 1.5 * N 32-bit
// instructions for N cases.
// Table-based packed switch generates approx. 10 32-bit instructions
// and N 32-bit data words for N cases.
// We switch to the table-based method starting with 6 entries.
static constexpr uint32_t kPackedSwitchCompareJumpThreshold = 6;

static constexpr XRegister kCoreCalleeSaves[] = {
    // S1(TR) is excluded as the ART thread register.
    S0, S2, S3, S4, S5, S6, S7, S8, S9, S10, S11, RA
};

static constexpr FRegister kFpuCalleeSaves[] = {
    FS0, FS1, FS2, FS3, FS4, FS5, FS6, FS7, FS8, FS9, FS10, FS11
};

#define QUICK_ENTRY_POINT(x) QUICK_ENTRYPOINT_OFFSET(kRiscv64PointerSize, x).Int32Value()

Location RegisterOrZeroBitPatternLocation(HInstruction* instruction) {
  DCHECK(!DataType::IsFloatingPointType(instruction->GetType()));
  return IsZeroBitPattern(instruction)
      ? Location::ConstantLocation(instruction)
      : Location::RequiresRegister();
}

Location FpuRegisterOrZeroBitPatternLocation(HInstruction* instruction) {
  DCHECK(DataType::IsFloatingPointType(instruction->GetType()));
  return IsZeroBitPattern(instruction)
      ? Location::ConstantLocation(instruction)
      : Location::RequiresFpuRegister();
}

XRegister InputXRegisterOrZero(Location location) {
  if (location.IsConstant()) {
    DCHECK(location.GetConstant()->IsZeroBitPattern());
    return Zero;
  } else {
    return location.AsRegister<XRegister>();
  }
}

Location ValueLocationForStore(HInstruction* value) {
  if (IsZeroBitPattern(value)) {
    return Location::ConstantLocation(value);
  } else if (DataType::IsFloatingPointType(value->GetType())) {
    return Location::RequiresFpuRegister();
  } else {
    return Location::RequiresRegister();
  }
}

Location Riscv64ReturnLocation(DataType::Type return_type) {
  switch (return_type) {
    case DataType::Type::kBool:
    case DataType::Type::kUint8:
    case DataType::Type::kInt8:
    case DataType::Type::kUint16:
    case DataType::Type::kInt16:
    case DataType::Type::kUint32:
    case DataType::Type::kInt32:
    case DataType::Type::kReference:
    case DataType::Type::kUint64:
    case DataType::Type::kInt64:
      return Location::RegisterLocation(A0);

    case DataType::Type::kFloat32:
    case DataType::Type::kFloat64:
      return Location::FpuRegisterLocation(FA0);

    case DataType::Type::kVoid:
      return Location::NoLocation();
  }
  UNREACHABLE();
}

static RegisterSet OneRegInReferenceOutSaveEverythingCallerSaves() {
  InvokeRuntimeCallingConvention calling_convention;
  RegisterSet caller_saves = RegisterSet::Empty();
  caller_saves.Add(Location::RegisterLocation(calling_convention.GetRegisterAt(0)));
  DCHECK_EQ(
      calling_convention.GetRegisterAt(0),
      calling_convention.GetReturnLocation(DataType::Type::kReference).AsRegister<XRegister>());
  return caller_saves;
}

template <ClassStatus kStatus>
static constexpr int64_t ShiftedSignExtendedClassStatusValue() {
  // This is used only for status values that have the highest bit set.
  static_assert(CLZ(enum_cast<uint32_t>(kStatus)) == status_lsb_position);
  constexpr uint32_t kShiftedStatusValue = enum_cast<uint32_t>(kStatus) << status_lsb_position;
  static_assert(kShiftedStatusValue >= 0x80000000u);
  return static_cast<int64_t>(kShiftedStatusValue) - (INT64_C(1) << 32);
}

// Split a 64-bit address used by JIT to the nearest 4KiB-aligned base address and a 12-bit
// signed offset. It is usually cheaper to materialize the aligned address than the full address.
std::pair<uint64_t, int32_t> SplitJitAddress(uint64_t address) {
  uint64_t bits0_11 = address & UINT64_C(0xfff);
  uint64_t bit11 = address & UINT64_C(0x800);
  // Round the address to nearest 4KiB address because the `imm12` has range [-0x800, 0x800).
  uint64_t base_address = (address & ~UINT64_C(0xfff)) + (bit11 << 1);
  int32_t imm12 = dchecked_integral_cast<int32_t>(bits0_11) -
                  dchecked_integral_cast<int32_t>(bit11 << 1);
  return {base_address, imm12};
}

int32_t ReadBarrierMarkEntrypointOffset(Location ref) {
  DCHECK(ref.IsRegister());
  int reg = ref.reg();
  DCHECK(T0 <= reg && reg <= T6 && reg != TR) << reg;
  // Note: Entrypoints for registers X30 (T5) and X31 (T6) are stored in entries
  // for X0 (Zero) and X1 (RA) because these are not valid registers for marking
  // and we currently have slots only up to register 29.
  int entry_point_number = (reg >= 30) ? reg - 30 : reg;
  return Thread::ReadBarrierMarkEntryPointsOffset<kRiscv64PointerSize>(entry_point_number);
}

Location InvokeRuntimeCallingConvention::GetReturnLocation(DataType::Type return_type) {
  return Riscv64ReturnLocation(return_type);
}

Location InvokeDexCallingConventionVisitorRISCV64::GetReturnLocation(DataType::Type type) const {
  return Riscv64ReturnLocation(type);
}

Location InvokeDexCallingConventionVisitorRISCV64::GetMethodLocation() const {
  return Location::RegisterLocation(kArtMethodRegister);
}

Location InvokeDexCallingConventionVisitorRISCV64::GetNextLocation(DataType::Type type) {
  Location next_location;
  if (type == DataType::Type::kVoid) {
    LOG(FATAL) << "Unexpected parameter type " << type;
  }

  // Note: Unlike the RISC-V C/C++ calling convention, managed ABI does not use
  // GPRs to pass FP args when we run out of FPRs.
  if (DataType::IsFloatingPointType(type) &&
      float_index_ < calling_convention.GetNumberOfFpuRegisters()) {
    next_location =
        Location::FpuRegisterLocation(calling_convention.GetFpuRegisterAt(float_index_++));
  } else if (!DataType::IsFloatingPointType(type) &&
             (gp_index_ < calling_convention.GetNumberOfRegisters())) {
    next_location = Location::RegisterLocation(calling_convention.GetRegisterAt(gp_index_++));
  } else {
    size_t stack_offset = calling_convention.GetStackOffsetOf(stack_index_);
    next_location = DataType::Is64BitType(type) ? Location::DoubleStackSlot(stack_offset) :
                                                  Location::StackSlot(stack_offset);
  }

  // Space on the stack is reserved for all arguments.
  stack_index_ += DataType::Is64BitType(type) ? 2 : 1;

  return next_location;
}

Location CriticalNativeCallingConventionVisitorRiscv64::GetNextLocation(DataType::Type type) {
  DCHECK_NE(type, DataType::Type::kReference);

  Location location = Location::NoLocation();
  if (DataType::IsFloatingPointType(type)) {
    if (fpr_index_ < kParameterFpuRegistersLength) {
      location = Location::FpuRegisterLocation(kParameterFpuRegisters[fpr_index_]);
      ++fpr_index_;
    } else {
      // Native ABI allows passing excessive FP args in GPRs. This is facilitated by
      // inserting fake conversion intrinsic calls (`Double.doubleToRawLongBits()`
      // or `Float.floatToRawIntBits()`) by `CriticalNativeAbiFixupRiscv64`.
      // Remaining FP args shall be passed on the stack.
      CHECK_EQ(gpr_index_, kRuntimeParameterCoreRegistersLength);
    }
  } else {
    // Native ABI uses the same core registers as a runtime call.
    if (gpr_index_ < kRuntimeParameterCoreRegistersLength) {
      location = Location::RegisterLocation(kRuntimeParameterCoreRegisters[gpr_index_]);
      ++gpr_index_;
    }
  }
  if (location.IsInvalid()) {
    // Only a `float` gets a single slot. Integral args need to be sign-extended to 64 bits.
    if (type == DataType::Type::kFloat32) {
      location = Location::StackSlot(stack_offset_);
    } else {
      location = Location::DoubleStackSlot(stack_offset_);
    }
    stack_offset_ += kFramePointerSize;

    if (for_register_allocation_) {
      location = Location::Any();
    }
  }
  return location;
}

Location CriticalNativeCallingConventionVisitorRiscv64::GetReturnLocation(
    DataType::Type type) const {
  // The result is returned the same way in native ABI and managed ABI. No result conversion is
  // needed, see comments in `Riscv64JniCallingConvention::RequiresSmallResultTypeExtension()`.
  InvokeDexCallingConventionVisitorRISCV64 dex_calling_convention;
  return dex_calling_convention.GetReturnLocation(type);
}

Location CriticalNativeCallingConventionVisitorRiscv64::GetMethodLocation() const {
  // Pass the method in the hidden argument T0.
  return Location::RegisterLocation(T0);
}

#define __ down_cast<CodeGeneratorRISCV64*>(codegen)->GetAssembler()->  // NOLINT

void LocationsBuilderRISCV64::HandleInvoke(HInvoke* instruction) {
  InvokeDexCallingConventionVisitorRISCV64 calling_convention_visitor;
  CodeGenerator::CreateCommonInvokeLocationSummary(instruction, &calling_convention_visitor);
}

class CompileOptimizedSlowPathRISCV64 : public SlowPathCodeRISCV64 {
 public:
  CompileOptimizedSlowPathRISCV64(HSuspendCheck* suspend_check, XRegister base, int32_t imm12)
      : SlowPathCodeRISCV64(suspend_check),
        base_(base),
        imm12_(imm12) {}

  void EmitNativeCode(CodeGenerator* codegen) override {
    uint32_t entrypoint_offset =
        GetThreadOffset<kRiscv64PointerSize>(kQuickCompileOptimized).Int32Value();
    __ Bind(GetEntryLabel());
    CodeGeneratorRISCV64* riscv64_codegen = down_cast<CodeGeneratorRISCV64*>(codegen);
    riscv64::ScratchRegisterScope srs(riscv64_codegen->GetAssembler());
    XRegister counter = srs.AllocateXRegister();
    __ LoadConst32(counter, ProfilingInfo::GetOptimizeThreshold());
    __ Sh(counter, base_, imm12_);
    if (instruction_ != nullptr) {
      // Only saves live vector regs for SIMD.
      SaveLiveRegisters(codegen, instruction_->GetLocations());
    }
    __ Loadd(RA, TR, entrypoint_offset);
    // Note: we don't record the call here (and therefore don't generate a stack
    // map), as the entrypoint should never be suspended.
    __ Jalr(RA);
    if (instruction_ != nullptr) {
      // Only restores live vector regs for SIMD.
      RestoreLiveRegisters(codegen, instruction_->GetLocations());
    }
    __ J(GetExitLabel());
  }

  const char* GetDescription() const override { return "CompileOptimizedSlowPath"; }

 private:
  XRegister base_;
  const int32_t imm12_;

  DISALLOW_COPY_AND_ASSIGN(CompileOptimizedSlowPathRISCV64);
};

class SuspendCheckSlowPathRISCV64 : public SlowPathCodeRISCV64 {
 public:
  SuspendCheckSlowPathRISCV64(HSuspendCheck* instruction, HBasicBlock* successor)
      : SlowPathCodeRISCV64(instruction), successor_(successor) {}

  void EmitNativeCode(CodeGenerator* codegen) override {
    LocationSummary* locations = instruction_->GetLocations();
    CodeGeneratorRISCV64* riscv64_codegen = down_cast<CodeGeneratorRISCV64*>(codegen);
    __ Bind(GetEntryLabel());
    SaveLiveRegisters(codegen, locations);  // Only saves live vector registers for SIMD.
    riscv64_codegen->InvokeRuntime(kQuickTestSuspend, instruction_, instruction_->GetDexPc(), this);
    CheckEntrypointTypes<kQuickTestSuspend, void, void>();
    RestoreLiveRegisters(codegen, locations);  // Only restores live vector registers for SIMD.
    if (successor_ == nullptr) {
      __ J(GetReturnLabel());
    } else {
      __ J(riscv64_codegen->GetLabelOf(successor_));
    }
  }

  Riscv64Label* GetReturnLabel() {
    DCHECK(successor_ == nullptr);
    return &return_label_;
  }

  const char* GetDescription() const override { return "SuspendCheckSlowPathRISCV64"; }

  HBasicBlock* GetSuccessor() const { return successor_; }

 private:
  // If not null, the block to branch to after the suspend check.
  HBasicBlock* const successor_;

  // If `successor_` is null, the label to branch to after the suspend check.
  Riscv64Label return_label_;

  DISALLOW_COPY_AND_ASSIGN(SuspendCheckSlowPathRISCV64);
};

class NullCheckSlowPathRISCV64 : public SlowPathCodeRISCV64 {
 public:
  explicit NullCheckSlowPathRISCV64(HNullCheck* instr) : SlowPathCodeRISCV64(instr) {}

  void EmitNativeCode(CodeGenerator* codegen) override {
    CodeGeneratorRISCV64* riscv64_codegen = down_cast<CodeGeneratorRISCV64*>(codegen);
    __ Bind(GetEntryLabel());
    if (instruction_->CanThrowIntoCatchBlock()) {
      // Live registers will be restored in the catch block if caught.
      SaveLiveRegisters(codegen, instruction_->GetLocations());
    }
    riscv64_codegen->InvokeRuntime(
        kQuickThrowNullPointer, instruction_, instruction_->GetDexPc(), this);
    CheckEntrypointTypes<kQuickThrowNullPointer, void, void>();
  }

  bool IsFatal() const override { return true; }

  const char* GetDescription() const override { return "NullCheckSlowPathRISCV64"; }

 private:
  DISALLOW_COPY_AND_ASSIGN(NullCheckSlowPathRISCV64);
};

class BoundsCheckSlowPathRISCV64 : public SlowPathCodeRISCV64 {
 public:
  explicit BoundsCheckSlowPathRISCV64(HBoundsCheck* instruction)
      : SlowPathCodeRISCV64(instruction) {}

  void EmitNativeCode(CodeGenerator* codegen) override {
    LocationSummary* locations = instruction_->GetLocations();
    CodeGeneratorRISCV64* riscv64_codegen = down_cast<CodeGeneratorRISCV64*>(codegen);
    __ Bind(GetEntryLabel());
    if (instruction_->CanThrowIntoCatchBlock()) {
      // Live registers will be restored in the catch block if caught.
      SaveLiveRegisters(codegen, instruction_->GetLocations());
    }
    // We're moving two locations to locations that could overlap, so we need a parallel
    // move resolver.
    InvokeRuntimeCallingConvention calling_convention;
    codegen->EmitParallelMoves(locations->InAt(0),
                               Location::RegisterLocation(calling_convention.GetRegisterAt(0)),
                               DataType::Type::kInt32,
                               locations->InAt(1),
                               Location::RegisterLocation(calling_convention.GetRegisterAt(1)),
                               DataType::Type::kInt32);
    QuickEntrypointEnum entrypoint = instruction_->AsBoundsCheck()->IsStringCharAt() ?
                                         kQuickThrowStringBounds :
                                         kQuickThrowArrayBounds;
    riscv64_codegen->InvokeRuntime(entrypoint, instruction_, instruction_->GetDexPc(), this);
    CheckEntrypointTypes<kQuickThrowStringBounds, void, int32_t, int32_t>();
    CheckEntrypointTypes<kQuickThrowArrayBounds, void, int32_t, int32_t>();
  }

  bool IsFatal() const override { return true; }

  const char* GetDescription() const override { return "BoundsCheckSlowPathRISCV64"; }

 private:
  DISALLOW_COPY_AND_ASSIGN(BoundsCheckSlowPathRISCV64);
};

class LoadClassSlowPathRISCV64 : public SlowPathCodeRISCV64 {
 public:
  LoadClassSlowPathRISCV64(HLoadClass* cls, HInstruction* at) : SlowPathCodeRISCV64(at), cls_(cls) {
    DCHECK(at->IsLoadClass() || at->IsClinitCheck());
    DCHECK_EQ(instruction_->IsLoadClass(), cls_ == instruction_);
  }

  void EmitNativeCode(CodeGenerator* codegen) override {
    LocationSummary* locations = instruction_->GetLocations();
    Location out = locations->Out();
    const uint32_t dex_pc = instruction_->GetDexPc();
    bool must_resolve_type = instruction_->IsLoadClass() && cls_->MustResolveTypeOnSlowPath();
    bool must_do_clinit = instruction_->IsClinitCheck() || cls_->MustGenerateClinitCheck();

    CodeGeneratorRISCV64* riscv64_codegen = down_cast<CodeGeneratorRISCV64*>(codegen);
    __ Bind(GetEntryLabel());
    SaveLiveRegisters(codegen, locations);

    InvokeRuntimeCallingConvention calling_convention;
    if (must_resolve_type) {
      DCHECK(IsSameDexFile(cls_->GetDexFile(), riscv64_codegen->GetGraph()->GetDexFile()) ||
             riscv64_codegen->GetCompilerOptions().WithinOatFile(&cls_->GetDexFile()) ||
             ContainsElement(Runtime::Current()->GetClassLinker()->GetBootClassPath(),
                             &cls_->GetDexFile()));
      dex::TypeIndex type_index = cls_->GetTypeIndex();
      __ LoadConst32(calling_convention.GetRegisterAt(0), type_index.index_);
      if (cls_->NeedsAccessCheck()) {
        CheckEntrypointTypes<kQuickResolveTypeAndVerifyAccess, void*, uint32_t>();
        riscv64_codegen->InvokeRuntime(
            kQuickResolveTypeAndVerifyAccess, instruction_, dex_pc, this);
      } else {
        CheckEntrypointTypes<kQuickResolveType, void*, uint32_t>();
        riscv64_codegen->InvokeRuntime(kQuickResolveType, instruction_, dex_pc, this);
      }
      // If we also must_do_clinit, the resolved type is now in the correct register.
    } else {
      DCHECK(must_do_clinit);
      Location source = instruction_->IsLoadClass() ? out : locations->InAt(0);
      riscv64_codegen->MoveLocation(
          Location::RegisterLocation(calling_convention.GetRegisterAt(0)), source, cls_->GetType());
    }
    if (must_do_clinit) {
      riscv64_codegen->InvokeRuntime(kQuickInitializeStaticStorage, instruction_, dex_pc, this);
      CheckEntrypointTypes<kQuickInitializeStaticStorage, void*, mirror::Class*>();
    }

    // Move the class to the desired location.
    if (out.IsValid()) {
      DCHECK(out.IsRegister() && !locations->GetLiveRegisters()->ContainsCoreRegister(out.reg()));
      DataType::Type type = DataType::Type::kReference;
      DCHECK_EQ(type, instruction_->GetType());
      riscv64_codegen->MoveLocation(out, calling_convention.GetReturnLocation(type), type);
    }
    RestoreLiveRegisters(codegen, locations);

    __ J(GetExitLabel());
  }

  const char* GetDescription() const override { return "LoadClassSlowPathRISCV64"; }

 private:
  // The class this slow path will load.
  HLoadClass* const cls_;

  DISALLOW_COPY_AND_ASSIGN(LoadClassSlowPathRISCV64);
};

class DeoptimizationSlowPathRISCV64 : public SlowPathCodeRISCV64 {
 public:
  explicit DeoptimizationSlowPathRISCV64(HDeoptimize* instruction)
      : SlowPathCodeRISCV64(instruction) {}

  void EmitNativeCode(CodeGenerator* codegen) override {
    CodeGeneratorRISCV64* riscv64_codegen = down_cast<CodeGeneratorRISCV64*>(codegen);
    __ Bind(GetEntryLabel());
    LocationSummary* locations = instruction_->GetLocations();
    SaveLiveRegisters(codegen, locations);
    InvokeRuntimeCallingConvention calling_convention;
    __ LoadConst32(calling_convention.GetRegisterAt(0),
                   static_cast<uint32_t>(instruction_->AsDeoptimize()->GetDeoptimizationKind()));
    riscv64_codegen->InvokeRuntime(kQuickDeoptimize, instruction_, instruction_->GetDexPc(), this);
    CheckEntrypointTypes<kQuickDeoptimize, void, DeoptimizationKind>();
  }

  const char* GetDescription() const override { return "DeoptimizationSlowPathRISCV64"; }

 private:
  DISALLOW_COPY_AND_ASSIGN(DeoptimizationSlowPathRISCV64);
};

// Slow path generating a read barrier for a GC root.
class ReadBarrierForRootSlowPathRISCV64 : public SlowPathCodeRISCV64 {
 public:
  ReadBarrierForRootSlowPathRISCV64(HInstruction* instruction, Location out, Location root)
      : SlowPathCodeRISCV64(instruction), out_(out), root_(root) {
  }

  void EmitNativeCode(CodeGenerator* codegen) override {
    DCHECK(codegen->EmitReadBarrier());
    LocationSummary* locations = instruction_->GetLocations();
    DataType::Type type = DataType::Type::kReference;
    XRegister reg_out = out_.AsRegister<XRegister>();
    DCHECK(locations->CanCall());
    DCHECK(!locations->GetLiveRegisters()->ContainsCoreRegister(reg_out));
    DCHECK(instruction_->IsLoadClass() ||
           instruction_->IsLoadString() ||
           (instruction_->IsInvoke() && instruction_->GetLocations()->Intrinsified()))
        << "Unexpected instruction in read barrier for GC root slow path: "
        << instruction_->DebugName();

    __ Bind(GetEntryLabel());
    SaveLiveRegisters(codegen, locations);

    InvokeRuntimeCallingConvention calling_convention;
    CodeGeneratorRISCV64* riscv64_codegen = down_cast<CodeGeneratorRISCV64*>(codegen);
    riscv64_codegen->MoveLocation(Location::RegisterLocation(calling_convention.GetRegisterAt(0)),
                                  root_,
                                  DataType::Type::kReference);
    riscv64_codegen->InvokeRuntime(kQuickReadBarrierForRootSlow,
                                   instruction_,
                                   instruction_->GetDexPc(),
                                   this);
    CheckEntrypointTypes<kQuickReadBarrierForRootSlow, mirror::Object*, GcRoot<mirror::Object>*>();
    riscv64_codegen->MoveLocation(out_, calling_convention.GetReturnLocation(type), type);

    RestoreLiveRegisters(codegen, locations);
    __ J(GetExitLabel());
  }

  const char* GetDescription() const override { return "ReadBarrierForRootSlowPathRISCV64"; }

 private:
  const Location out_;
  const Location root_;

  DISALLOW_COPY_AND_ASSIGN(ReadBarrierForRootSlowPathRISCV64);
};

class MethodEntryExitHooksSlowPathRISCV64 : public SlowPathCodeRISCV64 {
 public:
  explicit MethodEntryExitHooksSlowPathRISCV64(HInstruction* instruction)
      : SlowPathCodeRISCV64(instruction) {}

  void EmitNativeCode(CodeGenerator* codegen) override {
    LocationSummary* locations = instruction_->GetLocations();
    QuickEntrypointEnum entry_point =
        (instruction_->IsMethodEntryHook()) ? kQuickMethodEntryHook : kQuickMethodExitHook;
    CodeGeneratorRISCV64* riscv64_codegen = down_cast<CodeGeneratorRISCV64*>(codegen);
    __ Bind(GetEntryLabel());
    SaveLiveRegisters(codegen, locations);
    if (instruction_->IsMethodExitHook()) {
      __ Li(A4, riscv64_codegen->GetFrameSize());
    }
    riscv64_codegen->InvokeRuntime(entry_point, instruction_, instruction_->GetDexPc(), this);
    RestoreLiveRegisters(codegen, locations);
    __ J(GetExitLabel());
  }

  const char* GetDescription() const override {
    return "MethodEntryExitHooksSlowPathRISCV";
  }

 private:
  DISALLOW_COPY_AND_ASSIGN(MethodEntryExitHooksSlowPathRISCV64);
};

class ArraySetSlowPathRISCV64 : public SlowPathCodeRISCV64 {
 public:
  explicit ArraySetSlowPathRISCV64(HInstruction* instruction) : SlowPathCodeRISCV64(instruction) {}

  void EmitNativeCode(CodeGenerator* codegen) override {
    LocationSummary* locations = instruction_->GetLocations();
    __ Bind(GetEntryLabel());
    SaveLiveRegisters(codegen, locations);

    InvokeRuntimeCallingConvention calling_convention;
    HParallelMove parallel_move(codegen->GetGraph()->GetAllocator());
    parallel_move.AddMove(
        locations->InAt(0),
        Location::RegisterLocation(calling_convention.GetRegisterAt(0)),
        DataType::Type::kReference,
        nullptr);
    parallel_move.AddMove(
        locations->InAt(1),
        Location::RegisterLocation(calling_convention.GetRegisterAt(1)),
        DataType::Type::kInt32,
        nullptr);
    parallel_move.AddMove(
        locations->InAt(2),
        Location::RegisterLocation(calling_convention.GetRegisterAt(2)),
        DataType::Type::kReference,
        nullptr);
    codegen->GetMoveResolver()->EmitNativeCode(&parallel_move);

    CodeGeneratorRISCV64* riscv64_codegen = down_cast<CodeGeneratorRISCV64*>(codegen);
    riscv64_codegen->InvokeRuntime(kQuickAputObject, instruction_, instruction_->GetDexPc(), this);
    CheckEntrypointTypes<kQuickAputObject, void, mirror::Array*, int32_t, mirror::Object*>();
    RestoreLiveRegisters(codegen, locations);
    __ J(GetExitLabel());
  }

  const char* GetDescription() const override { return "ArraySetSlowPathRISCV64"; }

 private:
  DISALLOW_COPY_AND_ASSIGN(ArraySetSlowPathRISCV64);
};

class TypeCheckSlowPathRISCV64 : public SlowPathCodeRISCV64 {
 public:
  explicit TypeCheckSlowPathRISCV64(HInstruction* instruction, bool is_fatal)
      : SlowPathCodeRISCV64(instruction), is_fatal_(is_fatal) {}

  void EmitNativeCode(CodeGenerator* codegen) override {
    LocationSummary* locations = instruction_->GetLocations();

    uint32_t dex_pc = instruction_->GetDexPc();
    DCHECK(instruction_->IsCheckCast()
           || !locations->GetLiveRegisters()->ContainsCoreRegister(locations->Out().reg()));
    CodeGeneratorRISCV64* riscv64_codegen = down_cast<CodeGeneratorRISCV64*>(codegen);

    __ Bind(GetEntryLabel());
    if (!is_fatal_ || instruction_->CanThrowIntoCatchBlock()) {
      SaveLiveRegisters(codegen, locations);
    }

    // We're moving two locations to locations that could overlap, so we need a parallel
    // move resolver.
    InvokeRuntimeCallingConvention calling_convention;
    codegen->EmitParallelMoves(locations->InAt(0),
                               Location::RegisterLocation(calling_convention.GetRegisterAt(0)),
                               DataType::Type::kReference,
                               locations->InAt(1),
                               Location::RegisterLocation(calling_convention.GetRegisterAt(1)),
                               DataType::Type::kReference);
    if (instruction_->IsInstanceOf()) {
      riscv64_codegen->InvokeRuntime(kQuickInstanceofNonTrivial, instruction_, dex_pc, this);
      CheckEntrypointTypes<kQuickInstanceofNonTrivial, size_t, mirror::Object*, mirror::Class*>();
      DataType::Type ret_type = instruction_->GetType();
      Location ret_loc = calling_convention.GetReturnLocation(ret_type);
      riscv64_codegen->MoveLocation(locations->Out(), ret_loc, ret_type);
    } else {
      DCHECK(instruction_->IsCheckCast());
      riscv64_codegen->InvokeRuntime(kQuickCheckInstanceOf, instruction_, dex_pc, this);
      CheckEntrypointTypes<kQuickCheckInstanceOf, void, mirror::Object*, mirror::Class*>();
    }

    if (!is_fatal_) {
      RestoreLiveRegisters(codegen, locations);
      __ J(GetExitLabel());
    }
  }

  const char* GetDescription() const override { return "TypeCheckSlowPathRISCV64"; }

  bool IsFatal() const override { return is_fatal_; }

 private:
  const bool is_fatal_;

  DISALLOW_COPY_AND_ASSIGN(TypeCheckSlowPathRISCV64);
};

class DivZeroCheckSlowPathRISCV64 : public SlowPathCodeRISCV64 {
 public:
  explicit DivZeroCheckSlowPathRISCV64(HDivZeroCheck* instruction)
      : SlowPathCodeRISCV64(instruction) {}

  void EmitNativeCode(CodeGenerator* codegen) override {
    CodeGeneratorRISCV64* riscv64_codegen = down_cast<CodeGeneratorRISCV64*>(codegen);
    __ Bind(GetEntryLabel());
    riscv64_codegen->InvokeRuntime(
        kQuickThrowDivZero, instruction_, instruction_->GetDexPc(), this);
    CheckEntrypointTypes<kQuickThrowDivZero, void, void>();
  }

  bool IsFatal() const override { return true; }

  const char* GetDescription() const override { return "DivZeroCheckSlowPathRISCV64"; }

 private:
  DISALLOW_COPY_AND_ASSIGN(DivZeroCheckSlowPathRISCV64);
};

class ReadBarrierMarkSlowPathRISCV64 : public SlowPathCodeRISCV64 {
 public:
  ReadBarrierMarkSlowPathRISCV64(HInstruction* instruction, Location ref, Location entrypoint)
      : SlowPathCodeRISCV64(instruction), ref_(ref), entrypoint_(entrypoint) {
    DCHECK(entrypoint.IsRegister());
  }

  const char* GetDescription() const override { return "ReadBarrierMarkSlowPathRISCV64"; }

  void EmitNativeCode(CodeGenerator* codegen) override {
    DCHECK(codegen->EmitReadBarrier());
    LocationSummary* locations = instruction_->GetLocations();
    XRegister ref_reg = ref_.AsRegister<XRegister>();
    DCHECK(locations->CanCall());
    DCHECK(!locations->GetLiveRegisters()->ContainsCoreRegister(ref_reg)) << ref_reg;
    DCHECK(instruction_->IsInstanceFieldGet() ||
           instruction_->IsStaticFieldGet() ||
           instruction_->IsArrayGet() ||
           instruction_->IsArraySet() ||
           instruction_->IsLoadClass() ||
           instruction_->IsLoadString() ||
           instruction_->IsInstanceOf() ||
           instruction_->IsCheckCast() ||
           (instruction_->IsInvoke() && instruction_->GetLocations()->Intrinsified()))
        << "Unexpected instruction in read barrier marking slow path: "
        << instruction_->DebugName();

    __ Bind(GetEntryLabel());
    // No need to save live registers; it's taken care of by the
    // entrypoint. Also, there is no need to update the stack mask,
    // as this runtime call will not trigger a garbage collection.
    CodeGeneratorRISCV64* riscv64_codegen = down_cast<CodeGeneratorRISCV64*>(codegen);
    DCHECK(ref_reg >= T0 && ref_reg != TR);

    // "Compact" slow path, saving two moves.
    //
    // Instead of using the standard runtime calling convention (input
    // and output in A0 and V0 respectively):
    //
    //   A0 <- ref
    //   V0 <- ReadBarrierMark(A0)
    //   ref <- V0
    //
    // we just use rX (the register containing `ref`) as input and output
    // of a dedicated entrypoint:
    //
    //   rX <- ReadBarrierMarkRegX(rX)
    //
    riscv64_codegen->ValidateInvokeRuntimeWithoutRecordingPcInfo(instruction_, this);
    DCHECK_NE(entrypoint_.AsRegister<XRegister>(), TMP);  // A taken branch can clobber `TMP`.
    __ Jalr(entrypoint_.AsRegister<XRegister>());  // Clobbers `RA` (used as the `entrypoint_`).
    __ J(GetExitLabel());
  }

 private:
  // The location (register) of the marked object reference.
  const Location ref_;

  // The location of the already loaded entrypoint.
  const Location entrypoint_;

  DISALLOW_COPY_AND_ASSIGN(ReadBarrierMarkSlowPathRISCV64);
};

class LoadStringSlowPathRISCV64 : public SlowPathCodeRISCV64 {
 public:
  explicit LoadStringSlowPathRISCV64(HLoadString* instruction)
      : SlowPathCodeRISCV64(instruction) {}

  void EmitNativeCode(CodeGenerator* codegen) override {
    DCHECK(instruction_->IsLoadString());
    DCHECK_EQ(instruction_->AsLoadString()->GetLoadKind(), HLoadString::LoadKind::kBssEntry);
    LocationSummary* locations = instruction_->GetLocations();
    DCHECK(!locations->GetLiveRegisters()->ContainsCoreRegister(locations->Out().reg()));
    const dex::StringIndex string_index = instruction_->AsLoadString()->GetStringIndex();
    CodeGeneratorRISCV64* riscv64_codegen = down_cast<CodeGeneratorRISCV64*>(codegen);
    InvokeRuntimeCallingConvention calling_convention;
    __ Bind(GetEntryLabel());
    SaveLiveRegisters(codegen, locations);

    __ LoadConst32(calling_convention.GetRegisterAt(0), string_index.index_);
    riscv64_codegen->InvokeRuntime(
        kQuickResolveString, instruction_, instruction_->GetDexPc(), this);
    CheckEntrypointTypes<kQuickResolveString, void*, uint32_t>();

    DataType::Type type = DataType::Type::kReference;
    DCHECK_EQ(type, instruction_->GetType());
    riscv64_codegen->MoveLocation(
        locations->Out(), calling_convention.GetReturnLocation(type), type);
    RestoreLiveRegisters(codegen, locations);

    __ J(GetExitLabel());
  }

  const char* GetDescription() const override { return "LoadStringSlowPathRISCV64"; }

 private:
  DISALLOW_COPY_AND_ASSIGN(LoadStringSlowPathRISCV64);
};

#undef __
#define __ down_cast<Riscv64Assembler*>(GetAssembler())->  // NOLINT

template <typename Reg,
          void (Riscv64Assembler::*opS)(Reg, FRegister, FRegister),
          void (Riscv64Assembler::*opD)(Reg, FRegister, FRegister)>
inline void InstructionCodeGeneratorRISCV64::FpBinOp(
    Reg rd, FRegister rs1, FRegister rs2, DataType::Type type) {
  Riscv64Assembler* assembler = down_cast<CodeGeneratorRISCV64*>(codegen_)->GetAssembler();
  if (type == DataType::Type::kFloat32) {
    (assembler->*opS)(rd, rs1, rs2);
  } else {
    DCHECK_EQ(type, DataType::Type::kFloat64);
    (assembler->*opD)(rd, rs1, rs2);
  }
}

void InstructionCodeGeneratorRISCV64::FAdd(
    FRegister rd, FRegister rs1, FRegister rs2, DataType::Type type) {
  FpBinOp<FRegister, &Riscv64Assembler::FAddS, &Riscv64Assembler::FAddD>(rd, rs1, rs2, type);
}

inline void InstructionCodeGeneratorRISCV64::FSub(
    FRegister rd, FRegister rs1, FRegister rs2, DataType::Type type) {
  FpBinOp<FRegister, &Riscv64Assembler::FSubS, &Riscv64Assembler::FSubD>(rd, rs1, rs2, type);
}

inline void InstructionCodeGeneratorRISCV64::FDiv(
    FRegister rd, FRegister rs1, FRegister rs2, DataType::Type type) {
  FpBinOp<FRegister, &Riscv64Assembler::FDivS, &Riscv64Assembler::FDivD>(rd, rs1, rs2, type);
}

inline void InstructionCodeGeneratorRISCV64::FMul(
    FRegister rd, FRegister rs1, FRegister rs2, DataType::Type type) {
  FpBinOp<FRegister, &Riscv64Assembler::FMulS, &Riscv64Assembler::FMulD>(rd, rs1, rs2, type);
}

inline void InstructionCodeGeneratorRISCV64::FMin(
    FRegister rd, FRegister rs1, FRegister rs2, DataType::Type type) {
  FpBinOp<FRegister, &Riscv64Assembler::FMinS, &Riscv64Assembler::FMinD>(rd, rs1, rs2, type);
}

inline void InstructionCodeGeneratorRISCV64::FMax(
    FRegister rd, FRegister rs1, FRegister rs2, DataType::Type type) {
  FpBinOp<FRegister, &Riscv64Assembler::FMaxS, &Riscv64Assembler::FMaxD>(rd, rs1, rs2, type);
}

inline void InstructionCodeGeneratorRISCV64::FEq(
    XRegister rd, FRegister rs1, FRegister rs2, DataType::Type type) {
  FpBinOp<XRegister, &Riscv64Assembler::FEqS, &Riscv64Assembler::FEqD>(rd, rs1, rs2, type);
}

inline void InstructionCodeGeneratorRISCV64::FLt(
    XRegister rd, FRegister rs1, FRegister rs2, DataType::Type type) {
  FpBinOp<XRegister, &Riscv64Assembler::FLtS, &Riscv64Assembler::FLtD>(rd, rs1, rs2, type);
}

inline void InstructionCodeGeneratorRISCV64::FLe(
    XRegister rd, FRegister rs1, FRegister rs2, DataType::Type type) {
  FpBinOp<XRegister, &Riscv64Assembler::FLeS, &Riscv64Assembler::FLeD>(rd, rs1, rs2, type);
}

template <typename Reg,
          void (Riscv64Assembler::*opS)(Reg, FRegister),
          void (Riscv64Assembler::*opD)(Reg, FRegister)>
inline void InstructionCodeGeneratorRISCV64::FpUnOp(
    Reg rd, FRegister rs1, DataType::Type type) {
  Riscv64Assembler* assembler = down_cast<CodeGeneratorRISCV64*>(codegen_)->GetAssembler();
  if (type == DataType::Type::kFloat32) {
    (assembler->*opS)(rd, rs1);
  } else {
    DCHECK_EQ(type, DataType::Type::kFloat64);
    (assembler->*opD)(rd, rs1);
  }
}

inline void InstructionCodeGeneratorRISCV64::FAbs(
    FRegister rd, FRegister rs1, DataType::Type type) {
  FpUnOp<FRegister, &Riscv64Assembler::FAbsS, &Riscv64Assembler::FAbsD>(rd, rs1, type);
}

inline void InstructionCodeGeneratorRISCV64::FNeg(
    FRegister rd, FRegister rs1, DataType::Type type) {
  FpUnOp<FRegister, &Riscv64Assembler::FNegS, &Riscv64Assembler::FNegD>(rd, rs1, type);
}

inline void InstructionCodeGeneratorRISCV64::FMv(
    FRegister rd, FRegister rs1, DataType::Type type) {
  FpUnOp<FRegister, &Riscv64Assembler::FMvS, &Riscv64Assembler::FMvD>(rd, rs1, type);
}

inline void InstructionCodeGeneratorRISCV64::FMvX(
    XRegister rd, FRegister rs1, DataType::Type type) {
  FpUnOp<XRegister, &Riscv64Assembler::FMvXW, &Riscv64Assembler::FMvXD>(rd, rs1, type);
}

void InstructionCodeGeneratorRISCV64::FClass(
    XRegister rd, FRegister rs1, DataType::Type type) {
  FpUnOp<XRegister, &Riscv64Assembler::FClassS, &Riscv64Assembler::FClassD>(rd, rs1, type);
}

void InstructionCodeGeneratorRISCV64::Load(
    Location out, XRegister rs1, int32_t offset, DataType::Type type) {
  switch (type) {
    case DataType::Type::kBool:
    case DataType::Type::kUint8:
      __ Loadbu(out.AsRegister<XRegister>(), rs1, offset);
      break;
    case DataType::Type::kInt8:
      __ Loadb(out.AsRegister<XRegister>(), rs1, offset);
      break;
    case DataType::Type::kUint16:
      __ Loadhu(out.AsRegister<XRegister>(), rs1, offset);
      break;
    case DataType::Type::kInt16:
      __ Loadh(out.AsRegister<XRegister>(), rs1, offset);
      break;
    case DataType::Type::kInt32:
      __ Loadw(out.AsRegister<XRegister>(), rs1, offset);
      break;
    case DataType::Type::kInt64:
      __ Loadd(out.AsRegister<XRegister>(), rs1, offset);
      break;
    case DataType::Type::kReference:
      __ Loadwu(out.AsRegister<XRegister>(), rs1, offset);
      break;
    case DataType::Type::kFloat32:
      __ FLoadw(out.AsFpuRegister<FRegister>(), rs1, offset);
      break;
    case DataType::Type::kFloat64:
      __ FLoadd(out.AsFpuRegister<FRegister>(), rs1, offset);
      break;
    case DataType::Type::kUint32:
    case DataType::Type::kUint64:
    case DataType::Type::kVoid:
      LOG(FATAL) << "Unreachable type " << type;
      UNREACHABLE();
  }
}

void InstructionCodeGeneratorRISCV64::Store(
    Location value, XRegister rs1, int32_t offset, DataType::Type type) {
  DCHECK_IMPLIES(value.IsConstant(), IsZeroBitPattern(value.GetConstant()));
  if (kPoisonHeapReferences && type == DataType::Type::kReference && !value.IsConstant()) {
    riscv64::ScratchRegisterScope srs(GetAssembler());
    XRegister tmp = srs.AllocateXRegister();
    __ Mv(tmp, value.AsRegister<XRegister>());
    codegen_->PoisonHeapReference(tmp);
    __ Storew(tmp, rs1, offset);
    return;
  }
  switch (type) {
    case DataType::Type::kBool:
    case DataType::Type::kUint8:
    case DataType::Type::kInt8:
      __ Storeb(InputXRegisterOrZero(value), rs1, offset);
      break;
    case DataType::Type::kUint16:
    case DataType::Type::kInt16:
      __ Storeh(InputXRegisterOrZero(value), rs1, offset);
      break;
    case DataType::Type::kFloat32:
      if (!value.IsConstant()) {
        __ FStorew(value.AsFpuRegister<FRegister>(), rs1, offset);
        break;
      }
      FALLTHROUGH_INTENDED;
    case DataType::Type::kInt32:
    case DataType::Type::kReference:
      __ Storew(InputXRegisterOrZero(value), rs1, offset);
      break;
    case DataType::Type::kFloat64:
      if (!value.IsConstant()) {
        __ FStored(value.AsFpuRegister<FRegister>(), rs1, offset);
        break;
      }
      FALLTHROUGH_INTENDED;
    case DataType::Type::kInt64:
      __ Stored(InputXRegisterOrZero(value), rs1, offset);
      break;
    case DataType::Type::kUint32:
    case DataType::Type::kUint64:
    case DataType::Type::kVoid:
      LOG(FATAL) << "Unreachable type " << type;
      UNREACHABLE();
  }
}

void InstructionCodeGeneratorRISCV64::StoreSeqCst(Location value,
                                                  XRegister rs1,
                                                  int32_t offset,
                                                  DataType::Type type,
                                                  HInstruction* instruction) {
  if (DataType::Size(type) >= 4u) {
    // Use AMOSWAP for 32-bit and 64-bit data types.
    ScratchRegisterScope srs(GetAssembler());
    XRegister swap_src = kNoXRegister;
    if (kPoisonHeapReferences && type == DataType::Type::kReference && !value.IsConstant()) {
      swap_src = srs.AllocateXRegister();
      __ Mv(swap_src, value.AsRegister<XRegister>());
      codegen_->PoisonHeapReference(swap_src);
    } else if (DataType::IsFloatingPointType(type) && !value.IsConstant()) {
      swap_src = srs.AllocateXRegister();
      FMvX(swap_src, value.AsFpuRegister<FRegister>(), type);
    } else {
      swap_src = InputXRegisterOrZero(value);
    }
    XRegister addr = rs1;
    if (offset != 0) {
      addr = srs.AllocateXRegister();
      __ AddConst64(addr, rs1, offset);
    }
    if (DataType::Is64BitType(type)) {
      __ AmoSwapD(Zero, swap_src, addr, AqRl::kRelease);
    } else {
      __ AmoSwapW(Zero, swap_src, addr, AqRl::kRelease);
    }
    if (instruction != nullptr) {
      codegen_->MaybeRecordImplicitNullCheck(instruction);
    }
  } else {
    // Use fences for smaller data types.
    codegen_->GenerateMemoryBarrier(MemBarrierKind::kAnyStore);
    Store(value, rs1, offset, type);
    if (instruction != nullptr) {
      codegen_->MaybeRecordImplicitNullCheck(instruction);
    }
    codegen_->GenerateMemoryBarrier(MemBarrierKind::kAnyAny);
  }
}

void InstructionCodeGeneratorRISCV64::ShNAdd(
    XRegister rd, XRegister rs1, XRegister rs2, DataType::Type type) {
  switch (type) {
    case DataType::Type::kBool:
    case DataType::Type::kUint8:
    case DataType::Type::kInt8:
      DCHECK_EQ(DataType::SizeShift(type), 0u);
      __ Add(rd, rs1, rs2);
      break;
    case DataType::Type::kUint16:
    case DataType::Type::kInt16:
      DCHECK_EQ(DataType::SizeShift(type), 1u);
      __ Sh1Add(rd, rs1, rs2);
      break;
    case DataType::Type::kInt32:
    case DataType::Type::kReference:
    case DataType::Type::kFloat32:
      DCHECK_EQ(DataType::SizeShift(type), 2u);
      __ Sh2Add(rd, rs1, rs2);
      break;
    case DataType::Type::kInt64:
    case DataType::Type::kFloat64:
      DCHECK_EQ(DataType::SizeShift(type), 3u);
      __ Sh3Add(rd, rs1, rs2);
      break;
    case DataType::Type::kUint32:
    case DataType::Type::kUint64:
    case DataType::Type::kVoid:
      LOG(FATAL) << "Unreachable type " << type;
      UNREACHABLE();
  }
}

Riscv64Assembler* ParallelMoveResolverRISCV64::GetAssembler() const {
  return codegen_->GetAssembler();
}

void ParallelMoveResolverRISCV64::EmitMove(size_t index) {
  MoveOperands* move = moves_[index];
  codegen_->MoveLocation(move->GetDestination(), move->GetSource(), move->GetType());
}

void ParallelMoveResolverRISCV64::EmitSwap(size_t index) {
  MoveOperands* move = moves_[index];
  codegen_->SwapLocations(move->GetDestination(), move->GetSource(), move->GetType());
}

void ParallelMoveResolverRISCV64::SpillScratch([[maybe_unused]] int reg) {
  LOG(FATAL) << "Unimplemented";
  UNREACHABLE();
}

void ParallelMoveResolverRISCV64::RestoreScratch([[maybe_unused]] int reg) {
  LOG(FATAL) << "Unimplemented";
  UNREACHABLE();
}

void ParallelMoveResolverRISCV64::Exchange(int index1, int index2, bool double_slot) {
  // We have 2 scratch X registers and 1 scratch F register that we can use. We prefer
  // to use X registers for the swap but if both offsets are too big, we need to reserve
  // one of the X registers for address adjustment and use an F register.
  bool use_fp_tmp2 = false;
  if (!IsInt<12>(index2)) {
    if (!IsInt<12>(index1)) {
      use_fp_tmp2 = true;
    } else {
      std::swap(index1, index2);
    }
  }
  DCHECK_IMPLIES(!IsInt<12>(index2), use_fp_tmp2);

  Location loc1(double_slot ? Location::DoubleStackSlot(index1) : Location::StackSlot(index1));
  Location loc2(double_slot ? Location::DoubleStackSlot(index2) : Location::StackSlot(index2));
  riscv64::ScratchRegisterScope srs(GetAssembler());
  Location tmp = Location::RegisterLocation(srs.AllocateXRegister());
  DataType::Type tmp_type = double_slot ? DataType::Type::kInt64 : DataType::Type::kInt32;
  Location tmp2 = use_fp_tmp2
      ? Location::FpuRegisterLocation(srs.AllocateFRegister())
      : Location::RegisterLocation(srs.AllocateXRegister());
  DataType::Type tmp2_type = use_fp_tmp2
      ? (double_slot ? DataType::Type::kFloat64 : DataType::Type::kFloat32)
      : tmp_type;

  codegen_->MoveLocation(tmp, loc1, tmp_type);
  codegen_->MoveLocation(tmp2, loc2, tmp2_type);
  if (use_fp_tmp2) {
    codegen_->MoveLocation(loc2, tmp, tmp_type);
  } else {
    // We cannot use `Stored()` or `Storew()` via `MoveLocation()` because we have
    // no more scratch registers available. Use `Sd()` or `Sw()` explicitly.
    DCHECK(IsInt<12>(index2));
    if (double_slot) {
      __ Sd(tmp.AsRegister<XRegister>(), SP, index2);
    } else {
      __ Sw(tmp.AsRegister<XRegister>(), SP, index2);
    }
    srs.FreeXRegister(tmp.AsRegister<XRegister>());  // Free a temporary for `MoveLocation()`.
  }
  codegen_->MoveLocation(loc1, tmp2, tmp2_type);
}

InstructionCodeGeneratorRISCV64::InstructionCodeGeneratorRISCV64(HGraph* graph,
                                                                 CodeGeneratorRISCV64* codegen)
    : InstructionCodeGenerator(graph, codegen),
      assembler_(codegen->GetAssembler()),
      codegen_(codegen) {}

void InstructionCodeGeneratorRISCV64::GenerateClassInitializationCheck(
    SlowPathCodeRISCV64* slow_path, XRegister class_reg) {
  ScratchRegisterScope srs(GetAssembler());
  XRegister tmp = srs.AllocateXRegister();
  XRegister tmp2 = srs.AllocateXRegister();

  // We shall load the full 32-bit status word with sign-extension and compare as unsigned
  // to a sign-extended shifted status value. This yields the same comparison as loading and
  // materializing unsigned but the constant is materialized with a single LUI instruction.
  __ Loadw(tmp, class_reg, mirror::Class::StatusOffset().SizeValue());  // Sign-extended.
  __ Li(tmp2, ShiftedSignExtendedClassStatusValue<ClassStatus::kVisiblyInitialized>());
  __ Bltu(tmp, tmp2, slow_path->GetEntryLabel());
  __ Bind(slow_path->GetExitLabel());
}

void InstructionCodeGeneratorRISCV64::GenerateBitstringTypeCheckCompare(
    HTypeCheckInstruction* instruction, XRegister temp) {
  UNUSED(instruction);
  UNUSED(temp);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::GenerateSuspendCheck(HSuspendCheck* instruction,
                                                           HBasicBlock* successor) {
  if (instruction->IsNoOp()) {
    if (successor != nullptr) {
      __ J(codegen_->GetLabelOf(successor));
    }
    return;
  }

  if (codegen_->CanUseImplicitSuspendCheck()) {
    LOG(FATAL) << "Unimplemented ImplicitSuspendCheck";
    return;
  }

  SuspendCheckSlowPathRISCV64* slow_path =
      down_cast<SuspendCheckSlowPathRISCV64*>(instruction->GetSlowPath());

  if (slow_path == nullptr) {
    slow_path =
        new (codegen_->GetScopedAllocator()) SuspendCheckSlowPathRISCV64(instruction, successor);
    instruction->SetSlowPath(slow_path);
    codegen_->AddSlowPath(slow_path);
    if (successor != nullptr) {
      DCHECK(successor->IsLoopHeader());
    }
  } else {
    DCHECK_EQ(slow_path->GetSuccessor(), successor);
  }

  ScratchRegisterScope srs(GetAssembler());
  XRegister tmp = srs.AllocateXRegister();
  __ Loadw(tmp, TR, Thread::ThreadFlagsOffset<kRiscv64PointerSize>().Int32Value());
  static_assert(Thread::SuspendOrCheckpointRequestFlags() != std::numeric_limits<uint32_t>::max());
  static_assert(IsPowerOfTwo(Thread::SuspendOrCheckpointRequestFlags() + 1u));
  // Shift out other bits. Use an instruction that can be 16-bit with the "C" Standard Extension.
  __ Slli(tmp, tmp, CLZ(static_cast<uint64_t>(Thread::SuspendOrCheckpointRequestFlags())));
  if (successor == nullptr) {
    __ Bnez(tmp, slow_path->GetEntryLabel());
    __ Bind(slow_path->GetReturnLabel());
  } else {
    __ Beqz(tmp, codegen_->GetLabelOf(successor));
    __ J(slow_path->GetEntryLabel());
    // slow_path will return to GetLabelOf(successor).
  }
}

void InstructionCodeGeneratorRISCV64::GenerateReferenceLoadOneRegister(
    HInstruction* instruction,
    Location out,
    uint32_t offset,
    Location maybe_temp,
    ReadBarrierOption read_barrier_option) {
  XRegister out_reg = out.AsRegister<XRegister>();
  if (read_barrier_option == kWithReadBarrier) {
    DCHECK(codegen_->EmitReadBarrier());
    if (kUseBakerReadBarrier) {
      // Load with fast path based Baker's read barrier.
      // /* HeapReference<Object> */ out = *(out + offset)
      codegen_->GenerateFieldLoadWithBakerReadBarrier(instruction,
                                                      out,
                                                      out_reg,
                                                      offset,
                                                      maybe_temp,
                                                      /* needs_null_check= */ false);
    } else {
      // Load with slow path based read barrier.
      // Save the value of `out` into `maybe_temp` before overwriting it
      // in the following move operation, as we will need it for the
      // read barrier below.
      __ Mv(maybe_temp.AsRegister<XRegister>(), out_reg);
      // /* HeapReference<Object> */ out = *(out + offset)
      __ Loadwu(out_reg, out_reg, offset);
      codegen_->GenerateReadBarrierSlow(instruction, out, out, maybe_temp, offset);
    }
  } else {
    // Plain load with no read barrier.
    // /* HeapReference<Object> */ out = *(out + offset)
    __ Loadwu(out_reg, out_reg, offset);
    codegen_->MaybeUnpoisonHeapReference(out_reg);
  }
}

void InstructionCodeGeneratorRISCV64::GenerateReferenceLoadTwoRegisters(
    HInstruction* instruction,
    Location out,
    Location obj,
    uint32_t offset,
    Location maybe_temp,
    ReadBarrierOption read_barrier_option) {
  XRegister out_reg = out.AsRegister<XRegister>();
  XRegister obj_reg = obj.AsRegister<XRegister>();
  if (read_barrier_option == kWithReadBarrier) {
    DCHECK(codegen_->EmitReadBarrier());
    if (kUseBakerReadBarrier) {
      // Load with fast path based Baker's read barrier.
      // /* HeapReference<Object> */ out = *(obj + offset)
      codegen_->GenerateFieldLoadWithBakerReadBarrier(instruction,
                                                      out,
                                                      obj_reg,
                                                      offset,
                                                      maybe_temp,
                                                      /* needs_null_check= */ false);
    } else {
      // Load with slow path based read barrier.
      // /* HeapReference<Object> */ out = *(obj + offset)
      __ Loadwu(out_reg, obj_reg, offset);
      codegen_->GenerateReadBarrierSlow(instruction, out, out, obj, offset);
    }
  } else {
    // Plain load with no read barrier.
    // /* HeapReference<Object> */ out = *(obj + offset)
    __ Loadwu(out_reg, obj_reg, offset);
    codegen_->MaybeUnpoisonHeapReference(out_reg);
  }
}

SlowPathCodeRISCV64* CodeGeneratorRISCV64::AddGcRootBakerBarrierBarrierSlowPath(
    HInstruction* instruction, Location root, Location temp) {
  SlowPathCodeRISCV64* slow_path =
      new (GetScopedAllocator()) ReadBarrierMarkSlowPathRISCV64(instruction, root, temp);
  AddSlowPath(slow_path);
  return slow_path;
}

void CodeGeneratorRISCV64::EmitBakerReadBarierMarkingCheck(
    SlowPathCodeRISCV64* slow_path, Location root, Location temp) {
  const int32_t entry_point_offset = ReadBarrierMarkEntrypointOffset(root);
  // Loading the entrypoint does not require a load acquire since it is only changed when
  // threads are suspended or running a checkpoint.
  __ Loadd(temp.AsRegister<XRegister>(), TR, entry_point_offset);
  __ Bnez(temp.AsRegister<XRegister>(), slow_path->GetEntryLabel());
  __ Bind(slow_path->GetExitLabel());
}

void CodeGeneratorRISCV64::GenerateGcRootFieldLoad(HInstruction* instruction,
                                                   Location root,
                                                   XRegister obj,
                                                   uint32_t offset,
                                                   ReadBarrierOption read_barrier_option,
                                                   Riscv64Label* label_low) {
  DCHECK_IMPLIES(label_low != nullptr, offset == kLinkTimeOffsetPlaceholderLow) << offset;
  XRegister root_reg = root.AsRegister<XRegister>();
  if (read_barrier_option == kWithReadBarrier) {
    DCHECK(EmitReadBarrier());
    if (kUseBakerReadBarrier) {
      // Note that we do not actually check the value of `GetIsGcMarking()`
      // to decide whether to mark the loaded GC root or not.  Instead, we
      // load into `temp` (T6) the read barrier mark entry point corresponding
      // to register `root`. If `temp` is null, it means that `GetIsGcMarking()`
      // is false, and vice versa.
      //
      //     GcRoot<mirror::Object> root = *(obj+offset);  // Original reference load.
      //     temp = Thread::Current()->pReadBarrierMarkReg ## root.reg()
      //     if (temp != null) {
      //       root = temp(root)
      //     }
      //
      // TODO(riscv64): Introduce a "marking register" that holds the pointer to one of the
      // register marking entrypoints if marking (null if not marking) and make sure that
      // marking entrypoints for other registers are at known offsets, so that we can call
      // them using the "marking register" plus the offset embedded in the JALR instruction.

      if (label_low != nullptr) {
        __ Bind(label_low);
      }
      // /* GcRoot<mirror::Object> */ root = *(obj + offset)
      __ Loadwu(root_reg, obj, offset);
      static_assert(
          sizeof(mirror::CompressedReference<mirror::Object>) == sizeof(GcRoot<mirror::Object>),
          "art::mirror::CompressedReference<mirror::Object> and art::GcRoot<mirror::Object> "
          "have different sizes.");
      static_assert(sizeof(mirror::CompressedReference<mirror::Object>) == sizeof(int32_t),
                    "art::mirror::CompressedReference<mirror::Object> and int32_t "
                    "have different sizes.");

      // Use RA as temp. It is clobbered in the slow path anyway.
      Location temp = Location::RegisterLocation(RA);
      SlowPathCodeRISCV64* slow_path =
          AddGcRootBakerBarrierBarrierSlowPath(instruction, root, temp);
      EmitBakerReadBarierMarkingCheck(slow_path, root, temp);
    } else {
      // GC root loaded through a slow path for read barriers other
      // than Baker's.
      // /* GcRoot<mirror::Object>* */ root = obj + offset
      if (label_low != nullptr) {
        __ Bind(label_low);
      }
      __ AddConst32(root_reg, obj, offset);
      // /* mirror::Object* */ root = root->Read()
      GenerateReadBarrierForRootSlow(instruction, root, root);
    }
  } else {
    // Plain GC root load with no read barrier.
    // /* GcRoot<mirror::Object> */ root = *(obj + offset)
    if (label_low != nullptr) {
      __ Bind(label_low);
    }
    __ Loadwu(root_reg, obj, offset);
    // Note that GC roots are not affected by heap poisoning, thus we
    // do not have to unpoison `root_reg` here.
  }
}

void InstructionCodeGeneratorRISCV64::GenerateTestAndBranch(HInstruction* instruction,
                                                            size_t condition_input_index,
                                                            Riscv64Label* true_target,
                                                            Riscv64Label* false_target) {
  HInstruction* cond = instruction->InputAt(condition_input_index);

  if (true_target == nullptr && false_target == nullptr) {
    // Nothing to do. The code always falls through.
    return;
  } else if (cond->IsIntConstant()) {
    // Constant condition, statically compared against "true" (integer value 1).
    if (cond->AsIntConstant()->IsTrue()) {
      if (true_target != nullptr) {
        __ J(true_target);
      }
    } else {
      DCHECK(cond->AsIntConstant()->IsFalse()) << cond->AsIntConstant()->GetValue();
      if (false_target != nullptr) {
        __ J(false_target);
      }
    }
    return;
  }

  // The following code generates these patterns:
  //  (1) true_target == nullptr && false_target != nullptr
  //        - opposite condition true => branch to false_target
  //  (2) true_target != nullptr && false_target == nullptr
  //        - condition true => branch to true_target
  //  (3) true_target != nullptr && false_target != nullptr
  //        - condition true => branch to true_target
  //        - branch to false_target
  if (IsBooleanValueOrMaterializedCondition(cond)) {
    // The condition instruction has been materialized, compare the output to 0.
    Location cond_val = instruction->GetLocations()->InAt(condition_input_index);
    DCHECK(cond_val.IsRegister());
    if (true_target == nullptr) {
      __ Beqz(cond_val.AsRegister<XRegister>(), false_target);
    } else {
      __ Bnez(cond_val.AsRegister<XRegister>(), true_target);
    }
  } else {
    // The condition instruction has not been materialized, use its inputs as
    // the comparison and its condition as the branch condition.
    HCondition* condition = cond->AsCondition();
    DataType::Type type = condition->InputAt(0)->GetType();
    LocationSummary* locations = condition->GetLocations();
    IfCondition if_cond = condition->GetCondition();
    Riscv64Label* branch_target = true_target;

    if (true_target == nullptr) {
      if_cond = condition->GetOppositeCondition();
      branch_target = false_target;
    }

    switch (type) {
      case DataType::Type::kFloat32:
      case DataType::Type::kFloat64:
        GenerateFpCondition(if_cond, condition->IsGtBias(), type, locations, branch_target);
        break;
      default:
        // Integral types and reference equality.
        GenerateIntLongCompareAndBranch(if_cond, locations, branch_target);
        break;
    }
  }

  // If neither branch falls through (case 3), the conditional branch to `true_target`
  // was already emitted (case 2) and we need to emit a jump to `false_target`.
  if (true_target != nullptr && false_target != nullptr) {
    __ J(false_target);
  }
}

void InstructionCodeGeneratorRISCV64::DivRemOneOrMinusOne(HBinaryOperation* instruction) {
  DCHECK(instruction->IsDiv() || instruction->IsRem());
  DataType::Type type = instruction->GetResultType();

  LocationSummary* locations = instruction->GetLocations();
  Location second = locations->InAt(1);
  DCHECK(second.IsConstant());

  XRegister out = locations->Out().AsRegister<XRegister>();
  XRegister dividend = locations->InAt(0).AsRegister<XRegister>();
  int64_t imm = Int64FromConstant(second.GetConstant());
  DCHECK(imm == 1 || imm == -1);

  if (instruction->IsRem()) {
    __ Mv(out, Zero);
  } else {
    if (imm == -1) {
      if (type == DataType::Type::kInt32) {
        __ Subw(out, Zero, dividend);
      } else {
        DCHECK_EQ(type, DataType::Type::kInt64);
        __ Sub(out, Zero, dividend);
      }
    } else if (out != dividend) {
      __ Mv(out, dividend);
    }
  }
}

void InstructionCodeGeneratorRISCV64::DivRemByPowerOfTwo(HBinaryOperation* instruction) {
  DCHECK(instruction->IsDiv() || instruction->IsRem());
  DataType::Type type = instruction->GetResultType();
  DCHECK(type == DataType::Type::kInt32 || type == DataType::Type::kInt64) << type;

  LocationSummary* locations = instruction->GetLocations();
  Location second = locations->InAt(1);
  DCHECK(second.IsConstant());

  XRegister out = locations->Out().AsRegister<XRegister>();
  XRegister dividend = locations->InAt(0).AsRegister<XRegister>();
  int64_t imm = Int64FromConstant(second.GetConstant());
  int64_t abs_imm = static_cast<uint64_t>(AbsOrMin(imm));
  int ctz_imm = CTZ(abs_imm);
  DCHECK_GE(ctz_imm, 1);  // Division by +/-1 is handled by `DivRemOneOrMinusOne()`.

  ScratchRegisterScope srs(GetAssembler());
  XRegister tmp = srs.AllocateXRegister();
  // Calculate the negative dividend adjustment `tmp = dividend < 0 ? abs_imm - 1 : 0`.
  // This adjustment is needed for rounding the division result towards zero.
  if (type == DataType::Type::kInt32 || ctz_imm == 1) {
    // A 32-bit dividend is sign-extended to 64-bit, so we can use the upper bits.
    // And for a 64-bit division by +/-2, we need just the sign bit.
    DCHECK_IMPLIES(type == DataType::Type::kInt32, ctz_imm < 32);
    __ Srli(tmp, dividend, 64 - ctz_imm);
  } else {
    // For other 64-bit divisions, we need to replicate the sign bit.
    __ Srai(tmp, dividend, 63);
    __ Srli(tmp, tmp, 64 - ctz_imm);
  }
  // The rest of the calculation can use 64-bit operations even for 32-bit div/rem.
  __ Add(tmp, tmp, dividend);
  if (instruction->IsDiv()) {
    __ Srai(out, tmp, ctz_imm);
    if (imm < 0) {
      __ Neg(out, out);
    }
  } else {
    if (ctz_imm <= 11) {
      __ Andi(tmp, tmp, -abs_imm);
    } else {
      ScratchRegisterScope srs2(GetAssembler());
      XRegister tmp2 = srs2.AllocateXRegister();
      __ Li(tmp2, -abs_imm);
      __ And(tmp, tmp, tmp2);
    }
    __ Sub(out, dividend, tmp);
  }
}

void InstructionCodeGeneratorRISCV64::GenerateDivRemWithAnyConstant(HBinaryOperation* instruction) {
  DCHECK(instruction->IsDiv() || instruction->IsRem());
  LocationSummary* locations = instruction->GetLocations();
  XRegister dividend = locations->InAt(0).AsRegister<XRegister>();
  XRegister out = locations->Out().AsRegister<XRegister>();
  Location second = locations->InAt(1);
  int64_t imm = Int64FromConstant(second.GetConstant());
  DataType::Type type = instruction->GetResultType();
  ScratchRegisterScope srs(GetAssembler());
  XRegister tmp = srs.AllocateXRegister();

  // TODO: optimize with constant.
  __ LoadConst64(tmp, imm);
  if (instruction->IsDiv()) {
    if (type == DataType::Type::kInt32) {
      __ Divw(out, dividend, tmp);
    } else {
      __ Div(out, dividend, tmp);
    }
  } else {
    if (type == DataType::Type::kInt32)  {
      __ Remw(out, dividend, tmp);
    } else {
      __ Rem(out, dividend, tmp);
    }
  }
}

void InstructionCodeGeneratorRISCV64::GenerateDivRemIntegral(HBinaryOperation* instruction) {
  DCHECK(instruction->IsDiv() || instruction->IsRem());
  DataType::Type type = instruction->GetResultType();
  DCHECK(type == DataType::Type::kInt32 || type == DataType::Type::kInt64) << type;

  LocationSummary* locations = instruction->GetLocations();
  XRegister out = locations->Out().AsRegister<XRegister>();
  Location second = locations->InAt(1);

  if (second.IsConstant()) {
    int64_t imm = Int64FromConstant(second.GetConstant());
    if (imm == 0) {
      // Do not generate anything. DivZeroCheck would prevent any code to be executed.
    } else if (imm == 1 || imm == -1) {
      DivRemOneOrMinusOne(instruction);
    } else if (IsPowerOfTwo(AbsOrMin(imm))) {
      DivRemByPowerOfTwo(instruction);
    } else {
      DCHECK(imm <= -2 || imm >= 2);
      GenerateDivRemWithAnyConstant(instruction);
    }
  } else {
    XRegister dividend = locations->InAt(0).AsRegister<XRegister>();
    XRegister divisor = second.AsRegister<XRegister>();
    if (instruction->IsDiv()) {
      if (type == DataType::Type::kInt32) {
        __ Divw(out, dividend, divisor);
      } else {
        __ Div(out, dividend, divisor);
      }
    } else {
      if (type == DataType::Type::kInt32) {
        __ Remw(out, dividend, divisor);
      } else {
        __ Rem(out, dividend, divisor);
      }
    }
  }
}

void InstructionCodeGeneratorRISCV64::GenerateIntLongCondition(IfCondition cond,
                                                               LocationSummary* locations) {
  XRegister rd = locations->Out().AsRegister<XRegister>();
  GenerateIntLongCondition(cond, locations, rd, /*to_all_bits=*/ false);
}

void InstructionCodeGeneratorRISCV64::GenerateIntLongCondition(IfCondition cond,
                                                               LocationSummary* locations,
                                                               XRegister rd,
                                                               bool to_all_bits) {
  XRegister rs1 = locations->InAt(0).AsRegister<XRegister>();
  Location rs2_location = locations->InAt(1);
  bool use_imm = rs2_location.IsConstant();
  int64_t imm = use_imm ? CodeGenerator::GetInt64ValueOf(rs2_location.GetConstant()) : 0;
  XRegister rs2 = use_imm ? kNoXRegister : rs2_location.AsRegister<XRegister>();
  bool reverse_condition = false;
  switch (cond) {
    case kCondEQ:
    case kCondNE:
      if (!use_imm) {
        __ Sub(rd, rs1, rs2);  // SUB is OK here even for 32-bit comparison.
      } else if (imm != 0) {
        DCHECK(IsInt<12>(-imm));
        __ Addi(rd, rs1, -imm);  // ADDI is OK here even for 32-bit comparison.
      }  // else test `rs1` directly without subtraction for `use_imm && imm == 0`.
      if (cond == kCondEQ) {
        __ Seqz(rd, (use_imm && imm == 0) ? rs1 : rd);
      } else {
        __ Snez(rd, (use_imm && imm == 0) ? rs1 : rd);
      }
      break;

    case kCondLT:
    case kCondGE:
      if (use_imm) {
        DCHECK(IsInt<12>(imm));
        __ Slti(rd, rs1, imm);
      } else {
        __ Slt(rd, rs1, rs2);
      }
      // Calculate `rs1 >= rhs` as `!(rs1 < rhs)` since there's only the SLT but no SGE.
      reverse_condition = (cond == kCondGE);
      break;

    case kCondLE:
    case kCondGT:
      if (use_imm) {
        // Calculate `rs1 <= imm` as `rs1 < imm + 1`.
        DCHECK(IsInt<12>(imm + 1));  // The value that overflows would fail this check.
        __ Slti(rd, rs1, imm + 1);
      } else {
        __ Slt(rd, rs2, rs1);
      }
      // Calculate `rs1 > imm` as `!(rs1 < imm + 1)` and calculate
      // `rs1 <= rs2` as `!(rs2 < rs1)` since there's only the SLT but no SGE.
      reverse_condition = ((cond == kCondGT) == use_imm);
      break;

    case kCondB:
    case kCondAE:
      if (use_imm) {
        // Sltiu sign-extends its 12-bit immediate operand before the comparison
        // and thus lets us compare directly with unsigned values in the ranges
        // [0, 0x7ff] and [0x[ffffffff]fffff800, 0x[ffffffff]ffffffff].
        DCHECK(IsInt<12>(imm));
        __ Sltiu(rd, rs1, imm);
      } else {
        __ Sltu(rd, rs1, rs2);
      }
      // Calculate `rs1 AE rhs` as `!(rs1 B rhs)` since there's only the SLTU but no SGEU.
      reverse_condition = (cond == kCondAE);
      break;

    case kCondBE:
    case kCondA:
      if (use_imm) {
        // Calculate `rs1 BE imm` as `rs1 B imm + 1`.
        // Sltiu sign-extends its 12-bit immediate operand before the comparison
        // and thus lets us compare directly with unsigned values in the ranges
        // [0, 0x7ff] and [0x[ffffffff]fffff800, 0x[ffffffff]ffffffff].
        DCHECK(IsInt<12>(imm + 1));  // The value that overflows would fail this check.
        __ Sltiu(rd, rs1, imm + 1);
      } else {
        __ Sltu(rd, rs2, rs1);
      }
      // Calculate `rs1 A imm` as `!(rs1 B imm + 1)` and calculate
      // `rs1 BE rs2` as `!(rs2 B rs1)` since there's only the SLTU but no SGEU.
      reverse_condition = ((cond == kCondA) == use_imm);
      break;
  }
  if (to_all_bits) {
    // Store the result to all bits; in other words, "true" is represented by -1.
    if (reverse_condition) {
      __ Addi(rd, rd, -1);  // 0 -> -1, 1 -> 0
    } else {
      __ Neg(rd, rd);  // 0 -> 0, 1 -> -1
    }
  } else {
    if (reverse_condition) {
      __ Xori(rd, rd, 1);
    }
  }
}

void InstructionCodeGeneratorRISCV64::GenerateIntLongCompareAndBranch(IfCondition cond,
                                                                      LocationSummary* locations,
                                                                      Riscv64Label* label) {
  XRegister left = locations->InAt(0).AsRegister<XRegister>();
  Location right_location = locations->InAt(1);
  if (right_location.IsConstant()) {
    DCHECK_EQ(CodeGenerator::GetInt64ValueOf(right_location.GetConstant()), 0);
    switch (cond) {
      case kCondEQ:
      case kCondBE:  // <= 0 if zero
        __ Beqz(left, label);
        break;
      case kCondNE:
      case kCondA:  // > 0 if non-zero
        __ Bnez(left, label);
        break;
      case kCondLT:
        __ Bltz(left, label);
        break;
      case kCondGE:
        __ Bgez(left, label);
        break;
      case kCondLE:
        __ Blez(left, label);
        break;
      case kCondGT:
        __ Bgtz(left, label);
        break;
      case kCondB:  // always false
        break;
      case kCondAE:  // always true
        __ J(label);
        break;
    }
  } else {
    XRegister right_reg = right_location.AsRegister<XRegister>();
    switch (cond) {
      case kCondEQ:
        __ Beq(left, right_reg, label);
        break;
      case kCondNE:
        __ Bne(left, right_reg, label);
        break;
      case kCondLT:
        __ Blt(left, right_reg, label);
        break;
      case kCondGE:
        __ Bge(left, right_reg, label);
        break;
      case kCondLE:
        __ Ble(left, right_reg, label);
        break;
      case kCondGT:
        __ Bgt(left, right_reg, label);
        break;
      case kCondB:
        __ Bltu(left, right_reg, label);
        break;
      case kCondAE:
        __ Bgeu(left, right_reg, label);
        break;
      case kCondBE:
        __ Bleu(left, right_reg, label);
        break;
      case kCondA:
        __ Bgtu(left, right_reg, label);
        break;
    }
  }
}

void InstructionCodeGeneratorRISCV64::GenerateFpCondition(IfCondition cond,
                                                          bool gt_bias,
                                                          DataType::Type type,
                                                          LocationSummary* locations,
                                                          Riscv64Label* label) {
  DCHECK_EQ(label != nullptr, locations->Out().IsInvalid());
  ScratchRegisterScope srs(GetAssembler());
  XRegister rd =
      (label != nullptr) ? srs.AllocateXRegister() : locations->Out().AsRegister<XRegister>();
  GenerateFpCondition(cond, gt_bias, type, locations, label, rd, /*to_all_bits=*/ false);
}

void InstructionCodeGeneratorRISCV64::GenerateFpCondition(IfCondition cond,
                                                          bool gt_bias,
                                                          DataType::Type type,
                                                          LocationSummary* locations,
                                                          Riscv64Label* label,
                                                          XRegister rd,
                                                          bool to_all_bits) {
  // RISCV-V FP compare instructions yield the following values:
  //                      l<r  l=r  l>r Unordered
  //             FEQ l,r   0    1    0    0
  //             FLT l,r   1    0    0    0
  //             FLT r,l   0    0    1    0
  //             FLE l,r   1    1    0    0
  //             FLE r,l   0    1    1    0
  //
  // We can calculate the `Compare` results using the following formulas:
  //                      l<r  l=r  l>r Unordered
  //     Compare/gt_bias  -1    0    1    1       = ((FLE l,r) ^ 1) - (FLT l,r)
  //     Compare/lt_bias  -1    0    1   -1       = ((FLE r,l) - 1) + (FLT r,l)
  // These are emitted in `VisitCompare()`.
  //
  // This function emits a fused `Condition(Compare(., .), 0)`. If we compare the
  // `Compare` results above with 0, we get the following values and formulas:
  //                      l<r  l=r  l>r Unordered
  //     CondEQ/-          0    1    0    0       = (FEQ l, r)
  //     CondNE/-          1    0    1    1       = (FEQ l, r) ^ 1
  //     CondLT/gt_bias    1    0    0    0       = (FLT l,r)
  //     CondLT/lt_bias    1    0    0    1       = (FLE r,l) ^ 1
  //     CondLE/gt_bias    1    1    0    0       = (FLE l,r)
  //     CondLE/lt_bias    1    1    0    1       = (FLT r,l) ^ 1
  //     CondGT/gt_bias    0    0    1    1       = (FLE l,r) ^ 1
  //     CondGT/lt_bias    0    0    1    0       = (FLT r,l)
  //     CondGE/gt_bias    0    1    1    1       = (FLT l,r) ^ 1
  //     CondGE/lt_bias    0    1    1    0       = (FLE r,l)
  // (CondEQ/CondNE comparison with zero yields the same result with gt_bias and lt_bias.)
  //
  // If the condition is not materialized, the `^ 1` is not emitted,
  // instead the condition is reversed by emitting BEQZ instead of BNEZ.

  FRegister rs1 = locations->InAt(0).AsFpuRegister<FRegister>();
  FRegister rs2 = locations->InAt(1).AsFpuRegister<FRegister>();

  bool reverse_condition = false;
  switch (cond) {
    case kCondEQ:
      FEq(rd, rs1, rs2, type);
      break;
    case kCondNE:
      FEq(rd, rs1, rs2, type);
      reverse_condition = true;
      break;
    case kCondLT:
      if (gt_bias) {
        FLt(rd, rs1, rs2, type);
      } else {
        FLe(rd, rs2, rs1, type);
        reverse_condition = true;
      }
      break;
    case kCondLE:
      if (gt_bias) {
        FLe(rd, rs1, rs2, type);
      } else {
        FLt(rd, rs2, rs1, type);
        reverse_condition = true;
      }
      break;
    case kCondGT:
      if (gt_bias) {
        FLe(rd, rs1, rs2, type);
        reverse_condition = true;
      } else {
        FLt(rd, rs2, rs1, type);
      }
      break;
    case kCondGE:
      if (gt_bias) {
        FLt(rd, rs1, rs2, type);
        reverse_condition = true;
      } else {
        FLe(rd, rs2, rs1, type);
      }
      break;
    default:
      LOG(FATAL) << "Unexpected floating-point condition " << cond;
      UNREACHABLE();
  }

  if (label != nullptr) {
    if (reverse_condition) {
      __ Beqz(rd, label);
    } else {
      __ Bnez(rd, label);
    }
  } else if (to_all_bits) {
    // Store the result to all bits; in other words, "true" is represented by -1.
    if (reverse_condition) {
      __ Addi(rd, rd, -1);  // 0 -> -1, 1 -> 0
    } else {
      __ Neg(rd, rd);  // 0 -> 0, 1 -> -1
    }
  } else {
    if (reverse_condition) {
      __ Xori(rd, rd, 1);
    }
  }
}

void CodeGeneratorRISCV64::GenerateFieldLoadWithBakerReadBarrier(HInstruction* instruction,
                                                                 Location ref,
                                                                 XRegister obj,
                                                                 uint32_t offset,
                                                                 Location temp,
                                                                 bool needs_null_check) {
  GenerateReferenceLoadWithBakerReadBarrier(
      instruction, ref, obj, offset, /*index=*/ Location::NoLocation(), temp, needs_null_check);
}

void CodeGeneratorRISCV64::GenerateArrayLoadWithBakerReadBarrier(HInstruction* instruction,
                                                                 Location ref,
                                                                 XRegister obj,
                                                                 uint32_t data_offset,
                                                                 Location index,
                                                                 Location temp,
                                                                 bool needs_null_check) {
  GenerateReferenceLoadWithBakerReadBarrier(
      instruction, ref, obj, data_offset, index, temp, needs_null_check);
}

void CodeGeneratorRISCV64::GenerateReferenceLoadWithBakerReadBarrier(HInstruction* instruction,
                                                                     Location ref,
                                                                     XRegister obj,
                                                                     uint32_t offset,
                                                                     Location index,
                                                                     Location temp,
                                                                     bool needs_null_check) {
  // For now, use the same approach as for GC roots plus unpoison the reference if needed.
  // TODO(riscv64): Implement checking if the holder is black.
  UNUSED(temp);

  DCHECK(EmitBakerReadBarrier());
  XRegister reg = ref.AsRegister<XRegister>();
  if (index.IsValid()) {
    DCHECK(!needs_null_check);
    DCHECK(index.IsRegister());
    DataType::Type type = DataType::Type::kReference;
    DCHECK_EQ(type, instruction->GetType());
    if (instruction->IsArrayGet()) {
      // /* HeapReference<Object> */ ref = *(obj + index * element_size + offset)
      instruction_visitor_.ShNAdd(reg, index.AsRegister<XRegister>(), obj, type);
    } else {
      // /* HeapReference<Object> */ ref = *(obj + index + offset)
      DCHECK(instruction->IsInvoke());
      DCHECK(instruction->GetLocations()->Intrinsified());
      __ Add(reg, index.AsRegister<XRegister>(), obj);
    }
    __ Loadwu(reg, reg, offset);
  } else {
    // /* HeapReference<Object> */ ref = *(obj + offset)
    __ Loadwu(reg, obj, offset);
    if (needs_null_check) {
      MaybeRecordImplicitNullCheck(instruction);
    }
  }
  MaybeUnpoisonHeapReference(reg);

  // Slow path marking the reference.
  XRegister tmp = RA;  // Use RA as temp. It is clobbered in the slow path anyway.
  SlowPathCodeRISCV64* slow_path = new (GetScopedAllocator()) ReadBarrierMarkSlowPathRISCV64(
      instruction, ref, Location::RegisterLocation(tmp));
  AddSlowPath(slow_path);

  const int32_t entry_point_offset = ReadBarrierMarkEntrypointOffset(ref);
  // Loading the entrypoint does not require a load acquire since it is only changed when
  // threads are suspended or running a checkpoint.
  __ Loadd(tmp, TR, entry_point_offset);
  __ Bnez(tmp, slow_path->GetEntryLabel());
  __ Bind(slow_path->GetExitLabel());
}

SlowPathCodeRISCV64* CodeGeneratorRISCV64::AddReadBarrierSlowPath(HInstruction* instruction,
                                                                  Location out,
                                                                  Location ref,
                                                                  Location obj,
                                                                  uint32_t offset,
                                                                  Location index) {
  UNUSED(instruction);
  UNUSED(out);
  UNUSED(ref);
  UNUSED(obj);
  UNUSED(offset);
  UNUSED(index);
  LOG(FATAL) << "Unimplemented";
  UNREACHABLE();
}

void CodeGeneratorRISCV64::GenerateReadBarrierSlow(HInstruction* instruction,
                                                   Location out,
                                                   Location ref,
                                                   Location obj,
                                                   uint32_t offset,
                                                   Location index) {
  UNUSED(instruction);
  UNUSED(out);
  UNUSED(ref);
  UNUSED(obj);
  UNUSED(offset);
  UNUSED(index);
  LOG(FATAL) << "Unimplemented";
}

void CodeGeneratorRISCV64::MaybeGenerateReadBarrierSlow(HInstruction* instruction,
                                                        Location out,
                                                        Location ref,
                                                        Location obj,
                                                        uint32_t offset,
                                                        Location index) {
  if (EmitReadBarrier()) {
    // Baker's read barriers shall be handled by the fast path
    // (CodeGeneratorRISCV64::GenerateReferenceLoadWithBakerReadBarrier).
    DCHECK(!kUseBakerReadBarrier);
    // If heap poisoning is enabled, unpoisoning will be taken care of
    // by the runtime within the slow path.
    GenerateReadBarrierSlow(instruction, out, ref, obj, offset, index);
  } else if (kPoisonHeapReferences) {
    UnpoisonHeapReference(out.AsRegister<XRegister>());
  }
}

void CodeGeneratorRISCV64::GenerateReadBarrierForRootSlow(HInstruction* instruction,
                                                          Location out,
                                                          Location root) {
  DCHECK(EmitReadBarrier());

  // Insert a slow path based read barrier *after* the GC root load.
  //
  // Note that GC roots are not affected by heap poisoning, so we do
  // not need to do anything special for this here.
  SlowPathCodeRISCV64* slow_path =
      new (GetScopedAllocator()) ReadBarrierForRootSlowPathRISCV64(instruction, out, root);
  AddSlowPath(slow_path);

  __ J(slow_path->GetEntryLabel());
  __ Bind(slow_path->GetExitLabel());
}

void InstructionCodeGeneratorRISCV64::HandleGoto(HInstruction* instruction,
                                                 HBasicBlock* successor) {
  if (successor->IsExitBlock()) {
    DCHECK(instruction->GetPrevious()->AlwaysThrows());
    return;  // no code needed
  }

  HBasicBlock* block = instruction->GetBlock();
  HInstruction* previous = instruction->GetPrevious();
  HLoopInformation* info = block->GetLoopInformation();

  if (info != nullptr && info->IsBackEdge(*block) && info->HasSuspendCheck()) {
    codegen_->MaybeIncrementHotness(info->GetSuspendCheck(), /*is_frame_entry=*/ false);
    GenerateSuspendCheck(info->GetSuspendCheck(), successor);
    return;  // `GenerateSuspendCheck()` emitted the jump.
  }
  if (block->IsEntryBlock() && previous != nullptr && previous->IsSuspendCheck()) {
    GenerateSuspendCheck(previous->AsSuspendCheck(), nullptr);
  }
  if (!codegen_->GoesToNextBlock(block, successor)) {
    __ J(codegen_->GetLabelOf(successor));
  }
}

void InstructionCodeGeneratorRISCV64::GenPackedSwitchWithCompares(XRegister adjusted,
                                                                  XRegister temp,
                                                                  uint32_t num_entries,
                                                                  HBasicBlock* switch_block) {
  // Note: The `adjusted` register holds `value - lower_bound`. If the `lower_bound` is 0,
  // `adjusted` is the original `value` register and we must not clobber it. Otherwise,
  // `adjusted` is the `temp`. The caller already emitted the `adjusted < num_entries` check.

  // Create a set of compare/jumps.
  ArrayRef<HBasicBlock* const> successors(switch_block->GetSuccessors());
  uint32_t index = 0;
  for (; num_entries - index >= 2u; index += 2u) {
    // Jump to `successors[index]` if `value == lower_bound + index`.
    // Note that `adjusted` holds `value - lower_bound - index`.
    __ Beqz(adjusted, codegen_->GetLabelOf(successors[index]));
    if (num_entries - index == 2u) {
      break;  // The last entry shall match, so the branch shall be unconditional.
    }
    // Jump to `successors[index + 1]` if `value == lower_bound + index + 1`.
    // Modify `adjusted` to hold `value - lower_bound - index - 2` for this comparison.
    __ Addi(temp, adjusted, -2);
    adjusted = temp;
    __ Bltz(adjusted, codegen_->GetLabelOf(successors[index + 1]));
  }
  // For the last entry, unconditionally jump to `successors[num_entries - 1]`.
  __ J(codegen_->GetLabelOf(successors[num_entries - 1u]));
}

void InstructionCodeGeneratorRISCV64::GenTableBasedPackedSwitch(XRegister adjusted,
                                                                XRegister temp,
                                                                uint32_t num_entries,
                                                                HBasicBlock* switch_block) {
  // Note: The `adjusted` register holds `value - lower_bound`. If the `lower_bound` is 0,
  // `adjusted` is the original `value` register and we must not clobber it. Otherwise,
  // `adjusted` is the `temp`. The caller already emitted the `adjusted < num_entries` check.

  // Create a jump table.
  ArenaVector<Riscv64Label*> labels(num_entries,
                                    __ GetAllocator()->Adapter(kArenaAllocSwitchTable));
  const ArenaVector<HBasicBlock*>& successors = switch_block->GetSuccessors();
  for (uint32_t i = 0; i < num_entries; i++) {
    labels[i] = codegen_->GetLabelOf(successors[i]);
  }
  JumpTable* table = __ CreateJumpTable(std::move(labels));

  // Load the address of the jump table.
  // Note: The `LoadLabelAddress()` emits AUIPC+ADD. It is possible to avoid the ADD and
  // instead embed that offset in the LW below as well as all jump table entries but
  // that would need some invasive changes in the jump table handling in the assembler.
  ScratchRegisterScope srs(GetAssembler());
  XRegister table_base = srs.AllocateXRegister();
  __ LoadLabelAddress(table_base, table->GetLabel());

  // Load the PC difference from the jump table.
  // TODO(riscv64): Use SH2ADD from the Zba extension.
  __ Slli(temp, adjusted, 2);
  __ Add(temp, temp, table_base);
  __ Lw(temp, temp, 0);

  // Compute the absolute target address by adding the table start address
  // (the table contains offsets to targets relative to its start).
  __ Add(temp, temp, table_base);
  // And jump.
  __ Jr(temp);
}

int32_t InstructionCodeGeneratorRISCV64::VecAddress(LocationSummary* locations,
                                                    size_t size,
                                                    /*out*/ XRegister* adjusted_base) {
  UNUSED(locations);
  UNUSED(size);
  UNUSED(adjusted_base);
  LOG(FATAL) << "Unimplemented";
  UNREACHABLE();
}

void LocationsBuilderRISCV64::HandleBinaryOp(HBinaryOperation* instruction) {
  DCHECK_EQ(instruction->InputCount(), 2u);
  LocationSummary* locations = new (GetGraph()->GetAllocator()) LocationSummary(instruction);
  DataType::Type type = instruction->GetResultType();
  switch (type) {
    case DataType::Type::kInt32:
    case DataType::Type::kInt64: {
      locations->SetInAt(0, Location::RequiresRegister());
      HInstruction* right = instruction->InputAt(1);
      bool can_use_imm = false;
      if (instruction->IsMin() || instruction->IsMax()) {
        can_use_imm = IsZeroBitPattern(instruction);
      } else if (right->IsConstant()) {
        int64_t imm = CodeGenerator::GetInt64ValueOf(right->AsConstant());
        can_use_imm = IsInt<12>(instruction->IsSub() ? -imm : imm);
      }
      if (can_use_imm) {
        locations->SetInAt(1, Location::ConstantLocation(right));
      } else {
        locations->SetInAt(1, Location::RequiresRegister());
      }
      locations->SetOut(Location::RequiresRegister(), Location::kNoOutputOverlap);
      break;
    }

    case DataType::Type::kFloat32:
    case DataType::Type::kFloat64:
      locations->SetInAt(0, Location::RequiresFpuRegister());
      locations->SetInAt(1, Location::RequiresFpuRegister());
      if (instruction->IsMin() || instruction->IsMax()) {
        locations->SetOut(Location::RequiresFpuRegister(), Location::kOutputOverlap);
      } else {
        locations->SetOut(Location::RequiresFpuRegister(), Location::kNoOutputOverlap);
      }
      break;

    default:
      LOG(FATAL) << "Unexpected " << instruction->DebugName() << " type " << type;
      UNREACHABLE();
  }
}

void InstructionCodeGeneratorRISCV64::HandleBinaryOp(HBinaryOperation* instruction) {
  DataType::Type type = instruction->GetType();
  LocationSummary* locations = instruction->GetLocations();

  switch (type) {
    case DataType::Type::kInt32:
    case DataType::Type::kInt64: {
      XRegister rd = locations->Out().AsRegister<XRegister>();
      XRegister rs1 = locations->InAt(0).AsRegister<XRegister>();
      Location rs2_location = locations->InAt(1);

      bool use_imm = rs2_location.IsConstant();
      XRegister rs2 = use_imm ? kNoXRegister : rs2_location.AsRegister<XRegister>();
      int64_t imm = use_imm ? CodeGenerator::GetInt64ValueOf(rs2_location.GetConstant()) : 0;

      if (instruction->IsAnd()) {
        if (use_imm) {
          __ Andi(rd, rs1, imm);
        } else {
          __ And(rd, rs1, rs2);
        }
      } else if (instruction->IsOr()) {
        if (use_imm) {
          __ Ori(rd, rs1, imm);
        } else {
          __ Or(rd, rs1, rs2);
        }
      } else if (instruction->IsXor()) {
        if (use_imm) {
          __ Xori(rd, rs1, imm);
        } else {
          __ Xor(rd, rs1, rs2);
        }
      } else if (instruction->IsAdd() || instruction->IsSub()) {
        if (type == DataType::Type::kInt32) {
          if (use_imm) {
            __ Addiw(rd, rs1, instruction->IsSub() ? -imm : imm);
          } else if (instruction->IsAdd()) {
            __ Addw(rd, rs1, rs2);
          } else {
            DCHECK(instruction->IsSub());
            __ Subw(rd, rs1, rs2);
          }
        } else {
          if (use_imm) {
            __ Addi(rd, rs1, instruction->IsSub() ? -imm : imm);
          } else if (instruction->IsAdd()) {
            __ Add(rd, rs1, rs2);
          } else {
            DCHECK(instruction->IsSub());
            __ Sub(rd, rs1, rs2);
          }
        }
      } else if (instruction->IsMin()) {
        DCHECK_IMPLIES(use_imm, imm == 0);
        __ Min(rd, rs1, use_imm ? Zero : rs2);
      } else {
        DCHECK(instruction->IsMax());
        DCHECK_IMPLIES(use_imm, imm == 0);
        __ Max(rd, rs1, use_imm ? Zero : rs2);
      }
      break;
    }
    case DataType::Type::kFloat32:
    case DataType::Type::kFloat64: {
      FRegister rd = locations->Out().AsFpuRegister<FRegister>();
      FRegister rs1 = locations->InAt(0).AsFpuRegister<FRegister>();
      FRegister rs2 = locations->InAt(1).AsFpuRegister<FRegister>();
      if (instruction->IsAdd()) {
        FAdd(rd, rs1, rs2, type);
      } else if (instruction->IsSub()) {
        FSub(rd, rs1, rs2, type);
      } else {
        DCHECK(instruction->IsMin() || instruction->IsMax());
        // If one of the operands is NaN and the other is not, riscv64 instructions FMIN/FMAX
        // return the other operand while we want to return the NaN operand.
        DCHECK_NE(rd, rs1);  // Requested `Location::kOutputOverlap`.
        DCHECK_NE(rd, rs2);  // Requested `Location::kOutputOverlap`.
        ScratchRegisterScope srs(GetAssembler());
        XRegister tmp = srs.AllocateXRegister();
        XRegister tmp2 = srs.AllocateXRegister();
        Riscv64Label done;
        // Return `rs1` if it's NaN.
        FClass(tmp, rs1, type);
        __ Li(tmp2, kFClassNaNMinValue);
        FMv(rd, rs1, type);
        __ Bgeu(tmp, tmp2, &done);
        // Return `rs2` if it's NaN.
        FClass(tmp, rs2, type);
        FMv(rd, rs2, type);
        __ Bgeu(tmp, tmp2, &done);
        // Calculate Min/Max for non-NaN arguments.
        if (instruction->IsMin()) {
          FMin(rd, rs1, rs2, type);
        } else {
          FMax(rd, rs1, rs2, type);
        }
        __ Bind(&done);
      }
      break;
    }
    default:
      LOG(FATAL) << "Unexpected binary operation type " << type;
      UNREACHABLE();
  }
}

void LocationsBuilderRISCV64::HandleCondition(HCondition* instruction) {
  LocationSummary* locations = new (GetGraph()->GetAllocator()) LocationSummary(instruction);
  switch (instruction->InputAt(0)->GetType()) {
    case DataType::Type::kFloat32:
    case DataType::Type::kFloat64:
      locations->SetInAt(0, Location::RequiresFpuRegister());
      locations->SetInAt(1, Location::RequiresFpuRegister());
      break;

    default: {
      locations->SetInAt(0, Location::RequiresRegister());
      HInstruction* rhs = instruction->InputAt(1);
      bool use_imm = false;
      if (rhs->IsConstant()) {
        int64_t imm = CodeGenerator::GetInt64ValueOf(rhs->AsConstant());
        if (instruction->IsEmittedAtUseSite()) {
          // For `HIf`, materialize all non-zero constants with an `HParallelMove`.
          // Note: For certain constants and conditions, the code could be improved.
          // For example, 2048 takes two instructions to materialize but the negative
          // -2048 could be embedded in ADDI for EQ/NE comparison.
          use_imm = (imm == 0);
        } else {
          // Constants that cannot be embedded in an instruction's 12-bit immediate shall be
          // materialized with an `HParallelMove`. This simplifies the code and avoids cases
          // with arithmetic overflow. Adjust the `imm` if needed for a particular instruction.
          switch (instruction->GetCondition()) {
            case kCondEQ:
            case kCondNE:
              imm = -imm;  // ADDI with negative immediate (there is no SUBI).
              break;
            case kCondLE:
            case kCondGT:
            case kCondBE:
            case kCondA:
              imm += 1;    // SLTI/SLTIU with adjusted immediate (there is no SLEI/SLEIU).
              break;
            default:
              break;
          }
          use_imm = IsInt<12>(imm);
        }
      }
      if (use_imm) {
        locations->SetInAt(1, Location::ConstantLocation(rhs));
      } else {
        locations->SetInAt(1, Location::RequiresRegister());
      }
      break;
    }
  }
  if (!instruction->IsEmittedAtUseSite()) {
    locations->SetOut(Location::RequiresRegister(), Location::kNoOutputOverlap);
  }
}

void InstructionCodeGeneratorRISCV64::HandleCondition(HCondition* instruction) {
  if (instruction->IsEmittedAtUseSite()) {
    return;
  }

  DataType::Type type = instruction->InputAt(0)->GetType();
  LocationSummary* locations = instruction->GetLocations();
  switch (type) {
    case DataType::Type::kFloat32:
    case DataType::Type::kFloat64:
      GenerateFpCondition(instruction->GetCondition(), instruction->IsGtBias(), type, locations);
      return;
    default:
      // Integral types and reference equality.
      GenerateIntLongCondition(instruction->GetCondition(), locations);
      return;
  }
}

void LocationsBuilderRISCV64::HandleShift(HBinaryOperation* instruction) {
  DCHECK(instruction->IsShl() ||
         instruction->IsShr() ||
         instruction->IsUShr() ||
         instruction->IsRor());

  LocationSummary* locations = new (GetGraph()->GetAllocator()) LocationSummary(instruction);
  DataType::Type type = instruction->GetResultType();
  switch (type) {
    case DataType::Type::kInt32:
    case DataType::Type::kInt64: {
      locations->SetInAt(0, Location::RequiresRegister());
      locations->SetInAt(1, Location::RegisterOrConstant(instruction->InputAt(1)));
      locations->SetOut(Location::RequiresRegister(), Location::kNoOutputOverlap);
      break;
    }
    default:
      LOG(FATAL) << "Unexpected shift type " << type;
      UNREACHABLE();
  }
}

void InstructionCodeGeneratorRISCV64::HandleShift(HBinaryOperation* instruction) {
  DCHECK(instruction->IsShl() ||
         instruction->IsShr() ||
         instruction->IsUShr() ||
         instruction->IsRor());
  LocationSummary* locations = instruction->GetLocations();
  DataType::Type type = instruction->GetType();

  switch (type) {
    case DataType::Type::kInt32:
    case DataType::Type::kInt64: {
      XRegister rd = locations->Out().AsRegister<XRegister>();
      XRegister rs1 = locations->InAt(0).AsRegister<XRegister>();
      Location rs2_location = locations->InAt(1);

      if (rs2_location.IsConstant()) {
        int64_t imm = CodeGenerator::GetInt64ValueOf(rs2_location.GetConstant());
        uint32_t shamt =
            imm & (type == DataType::Type::kInt32 ? kMaxIntShiftDistance : kMaxLongShiftDistance);

        if (shamt == 0) {
          if (rd != rs1) {
            __ Mv(rd, rs1);
          }
        } else if (type == DataType::Type::kInt32) {
          if (instruction->IsShl()) {
            __ Slliw(rd, rs1, shamt);
          } else if (instruction->IsShr()) {
            __ Sraiw(rd, rs1, shamt);
          } else if (instruction->IsUShr()) {
            __ Srliw(rd, rs1, shamt);
          } else {
            DCHECK(instruction->IsRor());
            __ Roriw(rd, rs1, shamt);
          }
        } else {
          if (instruction->IsShl()) {
            __ Slli(rd, rs1, shamt);
          } else if (instruction->IsShr()) {
            __ Srai(rd, rs1, shamt);
          } else if (instruction->IsUShr()) {
            __ Srli(rd, rs1, shamt);
          } else {
            DCHECK(instruction->IsRor());
            __ Rori(rd, rs1, shamt);
          }
        }
      } else {
        XRegister rs2 = rs2_location.AsRegister<XRegister>();
        if (type == DataType::Type::kInt32) {
          if (instruction->IsShl()) {
            __ Sllw(rd, rs1, rs2);
          } else if (instruction->IsShr()) {
            __ Sraw(rd, rs1, rs2);
          } else if (instruction->IsUShr()) {
            __ Srlw(rd, rs1, rs2);
          } else {
            DCHECK(instruction->IsRor());
            __ Rorw(rd, rs1, rs2);
          }
        } else {
          if (instruction->IsShl()) {
            __ Sll(rd, rs1, rs2);
          } else if (instruction->IsShr()) {
            __ Sra(rd, rs1, rs2);
          } else if (instruction->IsUShr()) {
            __ Srl(rd, rs1, rs2);
          } else {
            DCHECK(instruction->IsRor());
            __ Ror(rd, rs1, rs2);
          }
        }
      }
      break;
    }
    default:
      LOG(FATAL) << "Unexpected shift operation type " << type;
  }
}

void CodeGeneratorRISCV64::MaybeMarkGCCard(XRegister object,
                                           XRegister value,
                                           bool value_can_be_null) {
  Riscv64Label done;
  if (value_can_be_null) {
    __ Beqz(value, &done);
  }
  MarkGCCard(object);
  __ Bind(&done);
}

void CodeGeneratorRISCV64::MarkGCCard(XRegister object) {
  ScratchRegisterScope srs(GetAssembler());
  XRegister card = srs.AllocateXRegister();
  XRegister temp = srs.AllocateXRegister();
  // Load the address of the card table into `card`.
  __ Loadd(card, TR, Thread::CardTableOffset<kRiscv64PointerSize>().Int32Value());

  // Calculate the address of the card corresponding to `object`.
  __ Srli(temp, object, gc::accounting::CardTable::kCardShift);
  __ Add(temp, card, temp);
  // Write the `art::gc::accounting::CardTable::kCardDirty` value into the
  // `object`'s card.
  //
  // Register `card` contains the address of the card table. Note that the card
  // table's base is biased during its creation so that it always starts at an
  // address whose least-significant byte is equal to `kCardDirty` (see
  // art::gc::accounting::CardTable::Create). Therefore the SB instruction
  // below writes the `kCardDirty` (byte) value into the `object`'s card
  // (located at `card + object >> kCardShift`).
  //
  // This dual use of the value in register `card` (1. to calculate the location
  // of the card to mark; and 2. to load the `kCardDirty` value) saves a load
  // (no need to explicitly load `kCardDirty` as an immediate value).
  __ Sb(card, temp, 0);  // No scratch register left for `Storeb()`.
}

void CodeGeneratorRISCV64::CheckGCCardIsValid(XRegister object) {
  Riscv64Label done;
  ScratchRegisterScope srs(GetAssembler());
  XRegister card = srs.AllocateXRegister();
  XRegister temp = srs.AllocateXRegister();
  // Load the address of the card table into `card`.
  __ Loadd(card, TR, Thread::CardTableOffset<kRiscv64PointerSize>().Int32Value());

  // Calculate the address of the card corresponding to `object`.
  __ Srli(temp, object, gc::accounting::CardTable::kCardShift);
  __ Add(temp, card, temp);
  // assert (!clean || !self->is_gc_marking)
  __ Lb(temp, temp, 0);
  static_assert(gc::accounting::CardTable::kCardClean == 0);
  __ Bnez(temp, &done);
  __ Loadw(temp, TR, Thread::IsGcMarkingOffset<kRiscv64PointerSize>().Int32Value());
  __ Beqz(temp, &done);
  __ Unimp();
  __ Bind(&done);
}

void LocationsBuilderRISCV64::HandleFieldSet(HInstruction* instruction) {
  LocationSummary* locations =
      new (GetGraph()->GetAllocator()) LocationSummary(instruction, LocationSummary::kNoCall);
  locations->SetInAt(0, Location::RequiresRegister());
  locations->SetInAt(1, ValueLocationForStore(instruction->InputAt(1)));
}

void InstructionCodeGeneratorRISCV64::HandleFieldSet(HInstruction* instruction,
                                                     const FieldInfo& field_info,
                                                     bool value_can_be_null,
                                                     WriteBarrierKind write_barrier_kind) {
  DataType::Type type = field_info.GetFieldType();
  LocationSummary* locations = instruction->GetLocations();
  XRegister obj = locations->InAt(0).AsRegister<XRegister>();
  Location value = locations->InAt(1);
  DCHECK_IMPLIES(value.IsConstant(), IsZeroBitPattern(value.GetConstant()));
  bool is_volatile = field_info.IsVolatile();
  uint32_t offset = field_info.GetFieldOffset().Uint32Value();

  if (is_volatile) {
    StoreSeqCst(value, obj, offset, type, instruction);
  } else {
    Store(value, obj, offset, type);
    codegen_->MaybeRecordImplicitNullCheck(instruction);
  }

  bool needs_write_barrier =
      codegen_->StoreNeedsWriteBarrier(type, instruction->InputAt(1), write_barrier_kind);
  if (needs_write_barrier) {
    if (value.IsConstant()) {
      DCHECK_EQ(write_barrier_kind, WriteBarrierKind::kEmitBeingReliedOn);
      codegen_->MarkGCCard(obj);
    } else {
      codegen_->MaybeMarkGCCard(
          obj,
          value.AsRegister<XRegister>(),
          value_can_be_null && write_barrier_kind == WriteBarrierKind::kEmitNotBeingReliedOn);
    }
  } else if (codegen_->ShouldCheckGCCard(type, instruction->InputAt(1), write_barrier_kind)) {
    codegen_->CheckGCCardIsValid(obj);
  }
}

void LocationsBuilderRISCV64::HandleFieldGet(HInstruction* instruction) {
  DCHECK(instruction->IsInstanceFieldGet() || instruction->IsStaticFieldGet());

  bool object_field_get_with_read_barrier =
      (instruction->GetType() == DataType::Type::kReference) && codegen_->EmitReadBarrier();
  LocationSummary* locations = new (GetGraph()->GetAllocator()) LocationSummary(
      instruction,
      object_field_get_with_read_barrier
          ? LocationSummary::kCallOnSlowPath
          : LocationSummary::kNoCall);

  // Input for object receiver.
  locations->SetInAt(0, Location::RequiresRegister());

  if (DataType::IsFloatingPointType(instruction->GetType())) {
    locations->SetOut(Location::RequiresFpuRegister());
  } else {
    // The output overlaps for an object field get when read barriers
    // are enabled: we do not want the load to overwrite the object's
    // location, as we need it to emit the read barrier.
    locations->SetOut(
        Location::RequiresRegister(),
        object_field_get_with_read_barrier ? Location::kOutputOverlap : Location::kNoOutputOverlap);
  }

  if (object_field_get_with_read_barrier && kUseBakerReadBarrier) {
    locations->SetCustomSlowPathCallerSaves(RegisterSet::Empty());  // No caller-save registers.
    // We need a temporary register for the read barrier marking slow
    // path in CodeGeneratorRISCV64::GenerateFieldLoadWithBakerReadBarrier.
    locations->AddTemp(Location::RequiresRegister());
  }
}

void InstructionCodeGeneratorRISCV64::HandleFieldGet(HInstruction* instruction,
                                                     const FieldInfo& field_info) {
  DCHECK(instruction->IsInstanceFieldGet() || instruction->IsStaticFieldGet());
  DCHECK_EQ(DataType::Size(field_info.GetFieldType()), DataType::Size(instruction->GetType()));
  DataType::Type type = instruction->GetType();
  LocationSummary* locations = instruction->GetLocations();
  Location obj_loc = locations->InAt(0);
  XRegister obj = obj_loc.AsRegister<XRegister>();
  Location dst_loc = locations->Out();
  bool is_volatile = field_info.IsVolatile();
  uint32_t offset = field_info.GetFieldOffset().Uint32Value();

  if (is_volatile) {
    codegen_->GenerateMemoryBarrier(MemBarrierKind::kAnyAny);
  }

  if (type == DataType::Type::kReference && codegen_->EmitBakerReadBarrier()) {
    // /* HeapReference<Object> */ dst = *(obj + offset)
    Location temp_loc = locations->GetTemp(0);
    // Note that a potential implicit null check is handled in this
    // CodeGeneratorRISCV64::GenerateFieldLoadWithBakerReadBarrier call.
    codegen_->GenerateFieldLoadWithBakerReadBarrier(instruction,
                                                    dst_loc,
                                                    obj,
                                                    offset,
                                                    temp_loc,
                                                    /* needs_null_check= */ true);
  } else {
    Load(dst_loc, obj, offset, type);
    codegen_->MaybeRecordImplicitNullCheck(instruction);
  }

  if (is_volatile) {
    codegen_->GenerateMemoryBarrier(MemBarrierKind::kLoadAny);
  }

  if (type == DataType::Type::kReference && !codegen_->EmitBakerReadBarrier()) {
    // If read barriers are enabled, emit read barriers other than
    // Baker's using a slow path (and also unpoison the loaded
    // reference, if heap poisoning is enabled).
    codegen_->MaybeGenerateReadBarrierSlow(instruction, dst_loc, dst_loc, obj_loc, offset);
  }
}

void InstructionCodeGeneratorRISCV64::GenerateMethodEntryExitHook(HInstruction* instruction) {
  SlowPathCodeRISCV64* slow_path =
      new (codegen_->GetScopedAllocator()) MethodEntryExitHooksSlowPathRISCV64(instruction);
  codegen_->AddSlowPath(slow_path);

  ScratchRegisterScope temps(GetAssembler());
  XRegister tmp = temps.AllocateXRegister();

  if (instruction->IsMethodExitHook()) {
    // Check if we are required to check if the caller needs a deoptimization. Strictly speaking it
    // would be sufficient to check if CheckCallerForDeopt bit is set. Though it is faster to check
    // if it is just non-zero. kCHA bit isn't used in debuggable runtimes as cha optimization is
    // disabled in debuggable runtime. The other bit is used when this method itself requires a
    // deoptimization due to redefinition. So it is safe to just check for non-zero value here.
    __ Loadwu(tmp, SP, codegen_->GetStackOffsetOfShouldDeoptimizeFlag());
    __ Bnez(tmp, slow_path->GetEntryLabel());
  }

  uint64_t hook_offset = instruction->IsMethodExitHook() ?
      instrumentation::Instrumentation::HaveMethodExitListenersOffset().SizeValue() :
      instrumentation::Instrumentation::HaveMethodEntryListenersOffset().SizeValue();
  auto [base_hook_address, hook_imm12] = SplitJitAddress(
      reinterpret_cast64<uint64_t>(Runtime::Current()->GetInstrumentation()) + hook_offset);
  __ LoadConst64(tmp, base_hook_address);
  __ Lbu(tmp, tmp, hook_imm12);
  // Check if there are any method entry / exit listeners. If no, continue.
  __ Beqz(tmp, slow_path->GetExitLabel());
  // Check if there are any slow (jvmti / trace with thread cpu time) method entry / exit listeners.
  // If yes, just take the slow path.
  static_assert(instrumentation::Instrumentation::kFastTraceListeners == 1u);
  __ Addi(tmp, tmp, -1);
  __ Bnez(tmp, slow_path->GetEntryLabel());

  // Check if there is place in the buffer to store a new entry, if no, take the slow path.
  int32_t trace_buffer_index_offset =
      Thread::TraceBufferIndexOffset<kRiscv64PointerSize>().Int32Value();
  __ Loadd(tmp, TR, trace_buffer_index_offset);
  __ Addi(tmp, tmp, -dchecked_integral_cast<int32_t>(kNumEntriesForWallClock));
  __ Bltz(tmp, slow_path->GetEntryLabel());

  // Update the index in the `Thread`.
  __ Stored(tmp, TR, trace_buffer_index_offset);

  // Allocate second core scratch register. We can no longer use `Stored()`
  // and similar macro instructions because there is no core scratch register left.
  XRegister tmp2 = temps.AllocateXRegister();

  // Calculate the entry address in the buffer.
  // /*addr*/ tmp = TR->GetMethodTraceBuffer() + sizeof(void*) * /*index*/ tmp;
  __ Loadd(tmp2, TR, Thread::TraceBufferPtrOffset<kRiscv64PointerSize>().SizeValue());
  __ Sh3Add(tmp, tmp, tmp2);

  // Record method pointer and trace action.
  __ Ld(tmp2, SP, 0);
  // Use last two bits to encode trace method action. For MethodEntry it is 0
  // so no need to set the bits since they are 0 already.
  DCHECK_GE(ArtMethod::Alignment(kRuntimePointerSize), static_cast<size_t>(4));
  static_assert(enum_cast<int32_t>(TraceAction::kTraceMethodEnter) == 0);
  static_assert(enum_cast<int32_t>(TraceAction::kTraceMethodExit) == 1);
  if (instruction->IsMethodExitHook()) {
    __ Ori(tmp2, tmp2, enum_cast<int32_t>(TraceAction::kTraceMethodExit));
  }
  static_assert(IsInt<12>(kMethodOffsetInBytes));  // No free scratch register for `Stored()`.
  __ Sd(tmp2, tmp, kMethodOffsetInBytes);

  // Record the timestamp.
  __ RdTime(tmp2);
  static_assert(IsInt<12>(kTimestampOffsetInBytes));  // No free scratch register for `Stored()`.
  __ Sd(tmp2, tmp, kTimestampOffsetInBytes);

  __ Bind(slow_path->GetExitLabel());
}

void LocationsBuilderRISCV64::VisitAbove(HAbove* instruction) {
  HandleCondition(instruction);
}

void InstructionCodeGeneratorRISCV64::VisitAbove(HAbove* instruction) {
  HandleCondition(instruction);
}

void LocationsBuilderRISCV64::VisitAboveOrEqual(HAboveOrEqual* instruction) {
  HandleCondition(instruction);
}

void InstructionCodeGeneratorRISCV64::VisitAboveOrEqual(HAboveOrEqual* instruction) {
  HandleCondition(instruction);
}

void LocationsBuilderRISCV64::VisitAbs(HAbs* abs) {
  LocationSummary* locations = new (GetGraph()->GetAllocator()) LocationSummary(abs);
  switch (abs->GetResultType()) {
    case DataType::Type::kInt32:
    case DataType::Type::kInt64:
      locations->SetInAt(0, Location::RequiresRegister());
      locations->SetOut(Location::RequiresRegister(), Location::kNoOutputOverlap);
      break;
    case DataType::Type::kFloat32:
    case DataType::Type::kFloat64:
      locations->SetInAt(0, Location::RequiresFpuRegister());
      locations->SetOut(Location::RequiresFpuRegister(), Location::kNoOutputOverlap);
      break;
    default:
      LOG(FATAL) << "Unexpected abs type " << abs->GetResultType();
  }
}

void InstructionCodeGeneratorRISCV64::VisitAbs(HAbs* abs) {
  LocationSummary* locations = abs->GetLocations();
  switch (abs->GetResultType()) {
    case DataType::Type::kInt32: {
      XRegister in = locations->InAt(0).AsRegister<XRegister>();
      XRegister out = locations->Out().AsRegister<XRegister>();
      ScratchRegisterScope srs(GetAssembler());
      XRegister tmp = srs.AllocateXRegister();
      __ Sraiw(tmp, in, 31);
      __ Xor(out, in, tmp);
      __ Subw(out, out, tmp);
      break;
    }
    case DataType::Type::kInt64: {
      XRegister in = locations->InAt(0).AsRegister<XRegister>();
      XRegister out = locations->Out().AsRegister<XRegister>();
      ScratchRegisterScope srs(GetAssembler());
      XRegister tmp = srs.AllocateXRegister();
      __ Srai(tmp, in, 63);
      __ Xor(out, in, tmp);
      __ Sub(out, out, tmp);
      break;
    }
    case DataType::Type::kFloat32:
    case DataType::Type::kFloat64:
      FAbs(locations->Out().AsFpuRegister<FRegister>(),
           locations->InAt(0).AsFpuRegister<FRegister>(),
           abs->GetResultType());
      break;
    default:
      LOG(FATAL) << "Unexpected abs type " << abs->GetResultType();
  }
}

void LocationsBuilderRISCV64::VisitAdd(HAdd* instruction) {
  HandleBinaryOp(instruction);
}

void InstructionCodeGeneratorRISCV64::VisitAdd(HAdd* instruction) {
  HandleBinaryOp(instruction);
}

void LocationsBuilderRISCV64::VisitAnd(HAnd* instruction) {
  HandleBinaryOp(instruction);
}

void InstructionCodeGeneratorRISCV64::VisitAnd(HAnd* instruction) {
  HandleBinaryOp(instruction);
}

void LocationsBuilderRISCV64::VisitArrayGet(HArrayGet* instruction) {
  DataType::Type type = instruction->GetType();
  bool object_array_get_with_read_barrier =
      (type == DataType::Type::kReference) && codegen_->EmitReadBarrier();
  LocationSummary* locations = new (GetGraph()->GetAllocator())
      LocationSummary(instruction,
                      object_array_get_with_read_barrier ? LocationSummary::kCallOnSlowPath :
                                                           LocationSummary::kNoCall);
  locations->SetInAt(0, Location::RequiresRegister());
  locations->SetInAt(1, Location::RegisterOrConstant(instruction->InputAt(1)));
  if (DataType::IsFloatingPointType(type)) {
    locations->SetOut(Location::RequiresFpuRegister(), Location::kNoOutputOverlap);
  } else {
    // The output overlaps in the case of an object array get with
    // read barriers enabled: we do not want the move to overwrite the
    // array's location, as we need it to emit the read barrier.
    locations->SetOut(
        Location::RequiresRegister(),
        object_array_get_with_read_barrier ? Location::kOutputOverlap : Location::kNoOutputOverlap);
  }
  if (object_array_get_with_read_barrier && kUseBakerReadBarrier) {
    locations->SetCustomSlowPathCallerSaves(RegisterSet::Empty());  // No caller-save registers.
    // We need a temporary register for the read barrier marking slow
    // path in CodeGeneratorRISCV64::GenerateArrayLoadWithBakerReadBarrier.
    locations->AddTemp(Location::RequiresRegister());
  }
}

void InstructionCodeGeneratorRISCV64::VisitArrayGet(HArrayGet* instruction) {
  LocationSummary* locations = instruction->GetLocations();
  Location obj_loc = locations->InAt(0);
  XRegister obj = obj_loc.AsRegister<XRegister>();
  Location out_loc = locations->Out();
  Location index = locations->InAt(1);
  uint32_t data_offset = CodeGenerator::GetArrayDataOffset(instruction);
  DataType::Type type = instruction->GetType();
  const bool maybe_compressed_char_at =
      mirror::kUseStringCompression && instruction->IsStringCharAt();

  Riscv64Label string_char_at_done;
  if (maybe_compressed_char_at) {
    DCHECK_EQ(type, DataType::Type::kUint16);
    uint32_t count_offset = mirror::String::CountOffset().Uint32Value();
    Riscv64Label uncompressed_load;
    {
      ScratchRegisterScope srs(GetAssembler());
      XRegister tmp = srs.AllocateXRegister();
      __ Loadw(tmp, obj, count_offset);
      codegen_->MaybeRecordImplicitNullCheck(instruction);
      __ Andi(tmp, tmp, 0x1);
      static_assert(static_cast<uint32_t>(mirror::StringCompressionFlag::kCompressed) == 0u,
                    "Expecting 0=compressed, 1=uncompressed");
      __ Bnez(tmp, &uncompressed_load);
    }
    XRegister out = out_loc.AsRegister<XRegister>();
    if (index.IsConstant()) {
        int32_t const_index = index.GetConstant()->AsIntConstant()->GetValue();
      __ Loadbu(out, obj, data_offset + const_index);
    } else {
      __ Add(out, obj, index.AsRegister<XRegister>());
      __ Loadbu(out, out, data_offset);
    }
    __ J(&string_char_at_done);
    __ Bind(&uncompressed_load);
  }

  if (type == DataType::Type::kReference && codegen_->EmitBakerReadBarrier()) {
    static_assert(
        sizeof(mirror::HeapReference<mirror::Object>) == sizeof(int32_t),
        "art::mirror::HeapReference<art::mirror::Object> and int32_t have different sizes.");
    // /* HeapReference<Object> */ out =
    //     *(obj + data_offset + index * sizeof(HeapReference<Object>))
    // Note that a potential implicit null check could be handled in these
    // `CodeGeneratorRISCV64::Generate{Array,Field}LoadWithBakerReadBarrier()` calls
    // but we currently do not support implicit null checks on `HArrayGet`.
    DCHECK(!instruction->CanDoImplicitNullCheckOn(instruction->InputAt(0)));
    Location temp = locations->GetTemp(0);
    if (index.IsConstant()) {
      // Array load with a constant index can be treated as a field load.
      static constexpr size_t shift = DataType::SizeShift(DataType::Type::kReference);
      size_t offset = (index.GetConstant()->AsIntConstant()->GetValue() << shift) + data_offset;
      codegen_->GenerateFieldLoadWithBakerReadBarrier(instruction,
                                                      out_loc,
                                                      obj,
                                                      offset,
                                                      temp,
                                                      /* needs_null_check= */ false);
    } else {
      codegen_->GenerateArrayLoadWithBakerReadBarrier(instruction,
                                                      out_loc,
                                                      obj,
                                                      data_offset,
                                                      index,
                                                      temp,
                                                      /* needs_null_check= */ false);
    }
  } else if (index.IsConstant()) {
    int32_t const_index = index.GetConstant()->AsIntConstant()->GetValue();
    int32_t offset = data_offset + (const_index << DataType::SizeShift(type));
    Load(out_loc, obj, offset, type);
    if (!maybe_compressed_char_at) {
      codegen_->MaybeRecordImplicitNullCheck(instruction);
    }
    if (type == DataType::Type::kReference) {
      DCHECK(!codegen_->EmitBakerReadBarrier());
      // If read barriers are enabled, emit read barriers other than Baker's using
      // a slow path (and also unpoison the loaded reference, if heap poisoning is enabled).
      codegen_->MaybeGenerateReadBarrierSlow(instruction, out_loc, out_loc, obj_loc, offset);
    }
  } else {
    ScratchRegisterScope srs(GetAssembler());
    XRegister tmp = srs.AllocateXRegister();
    ShNAdd(tmp, index.AsRegister<XRegister>(), obj, type);
    Load(out_loc, tmp, data_offset, type);
    if (!maybe_compressed_char_at) {
      codegen_->MaybeRecordImplicitNullCheck(instruction);
    }
    if (type == DataType::Type::kReference) {
      DCHECK(!codegen_->EmitBakerReadBarrier());
      // If read barriers are enabled, emit read barriers other than Baker's using
      // a slow path (and also unpoison the loaded reference, if heap poisoning is enabled).
      codegen_->MaybeGenerateReadBarrierSlow(
          instruction, out_loc, out_loc, obj_loc, data_offset, index);
    }
  }

  if (maybe_compressed_char_at) {
    __ Bind(&string_char_at_done);
  }
}

void LocationsBuilderRISCV64::VisitArrayLength(HArrayLength* instruction) {
  LocationSummary* locations = new (GetGraph()->GetAllocator()) LocationSummary(instruction);
  locations->SetInAt(0, Location::RequiresRegister());
  locations->SetOut(Location::RequiresRegister(), Location::kNoOutputOverlap);
}

void InstructionCodeGeneratorRISCV64::VisitArrayLength(HArrayLength* instruction) {
  LocationSummary* locations = instruction->GetLocations();
  uint32_t offset = CodeGenerator::GetArrayLengthOffset(instruction);
  XRegister obj = locations->InAt(0).AsRegister<XRegister>();
  XRegister out = locations->Out().AsRegister<XRegister>();
  __ Loadwu(out, obj, offset);  // Unsigned for string length; does not matter for other arrays.
  codegen_->MaybeRecordImplicitNullCheck(instruction);
  // Mask out compression flag from String's array length.
  if (mirror::kUseStringCompression && instruction->IsStringLength()) {
    __ Srli(out, out, 1u);
  }
}

void LocationsBuilderRISCV64::VisitArraySet(HArraySet* instruction) {
  bool needs_type_check = instruction->NeedsTypeCheck();
  LocationSummary* locations = new (GetGraph()->GetAllocator()) LocationSummary(
      instruction,
      needs_type_check ? LocationSummary::kCallOnSlowPath : LocationSummary::kNoCall);
  locations->SetInAt(0, Location::RequiresRegister());
  locations->SetInAt(1, Location::RegisterOrConstant(instruction->InputAt(1)));
  locations->SetInAt(2, ValueLocationForStore(instruction->GetValue()));
  if (kPoisonHeapReferences &&
      instruction->GetComponentType() == DataType::Type::kReference &&
      !locations->InAt(1).IsConstant() &&
      !locations->InAt(2).IsConstant()) {
    locations->AddTemp(Location::RequiresRegister());
  }
}

void InstructionCodeGeneratorRISCV64::VisitArraySet(HArraySet* instruction) {
  LocationSummary* locations = instruction->GetLocations();
  XRegister array = locations->InAt(0).AsRegister<XRegister>();
  Location index = locations->InAt(1);
  Location value = locations->InAt(2);
  DataType::Type value_type = instruction->GetComponentType();
  bool needs_type_check = instruction->NeedsTypeCheck();
  const WriteBarrierKind write_barrier_kind = instruction->GetWriteBarrierKind();
  bool needs_write_barrier =
      codegen_->StoreNeedsWriteBarrier(value_type, instruction->GetValue(), write_barrier_kind);
  size_t data_offset = mirror::Array::DataOffset(DataType::Size(value_type)).Uint32Value();
  SlowPathCodeRISCV64* slow_path = nullptr;

  if (needs_write_barrier) {
    DCHECK_EQ(value_type, DataType::Type::kReference);
    DCHECK_IMPLIES(value.IsConstant(), value.GetConstant()->IsArithmeticZero());
    const bool storing_constant_zero = value.IsConstant();
    if (!storing_constant_zero) {
      Riscv64Label do_store;

      bool can_value_be_null = instruction->GetValueCanBeNull();
      if (can_value_be_null) {
        __ Beqz(value.AsRegister<XRegister>(), &do_store);
      }

      if (needs_type_check) {
        slow_path = new (codegen_->GetScopedAllocator()) ArraySetSlowPathRISCV64(instruction);
        codegen_->AddSlowPath(slow_path);

        uint32_t class_offset = mirror::Object::ClassOffset().Int32Value();
        uint32_t super_offset = mirror::Class::SuperClassOffset().Int32Value();
        uint32_t component_offset = mirror::Class::ComponentTypeOffset().Int32Value();

        ScratchRegisterScope srs(GetAssembler());
        XRegister temp1 = srs.AllocateXRegister();
        XRegister temp2 = srs.AllocateXRegister();

        // Note that when read barriers are enabled, the type checks are performed
        // without read barriers.  This is fine, even in the case where a class object
        // is in the from-space after the flip, as a comparison involving such a type
        // would not produce a false positive; it may of course produce a false
        // negative, in which case we would take the ArraySet slow path.

        // /* HeapReference<Class> */ temp1 = array->klass_
        __ Loadwu(temp1, array, class_offset);
        codegen_->MaybeRecordImplicitNullCheck(instruction);
        codegen_->MaybeUnpoisonHeapReference(temp1);

        // /* HeapReference<Class> */ temp2 = temp1->component_type_
        __ Loadwu(temp2, temp1, component_offset);
        // /* HeapReference<Class> */ temp1 = value->klass_
        __ Loadwu(temp1, value.AsRegister<XRegister>(), class_offset);
        // If heap poisoning is enabled, no need to unpoison `temp1`
        // nor `temp2`, as we are comparing two poisoned references.
        if (instruction->StaticTypeOfArrayIsObjectArray()) {
          Riscv64Label do_put;
          __ Beq(temp1, temp2, &do_put);
          // If heap poisoning is enabled, the `temp2` reference has
          // not been unpoisoned yet; unpoison it now.
          codegen_->MaybeUnpoisonHeapReference(temp2);

          // /* HeapReference<Class> */ temp1 = temp2->super_class_
          __ Loadwu(temp1, temp2, super_offset);
          // If heap poisoning is enabled, no need to unpoison
          // `temp1`, as we are comparing against null below.
          __ Bnez(temp1, slow_path->GetEntryLabel());
          __ Bind(&do_put);
        } else {
          __ Bne(temp1, temp2, slow_path->GetEntryLabel());
        }
      }

      if (can_value_be_null) {
        __ Bind(&do_store);
      }
    }

    DCHECK_NE(write_barrier_kind, WriteBarrierKind::kDontEmit);
    // TODO(solanes): The WriteBarrierKind::kEmitNotBeingReliedOn case should be able to skip
    // this write barrier when its value is null (without an extra Beqz since we already checked
    // if the value is null for the type check). This will be done as a follow-up since it is a
    // runtime optimization that needs extra care.
    DCHECK_IMPLIES(storing_constant_zero,
                   write_barrier_kind == WriteBarrierKind::kEmitBeingReliedOn);
    codegen_->MarkGCCard(array);
  } else if (codegen_->ShouldCheckGCCard(value_type, instruction->GetValue(), write_barrier_kind)) {
    codegen_->CheckGCCardIsValid(array);
  }

  if (index.IsConstant()) {
    int32_t const_index = index.GetConstant()->AsIntConstant()->GetValue();
    int32_t offset = data_offset + (const_index << DataType::SizeShift(value_type));
    Store(value, array, offset, value_type);
  } else {
    ScratchRegisterScope srs(GetAssembler());
    // Heap poisoning needs two scratch registers in `Store()`, except for null constants.
    XRegister tmp =
        (kPoisonHeapReferences && value_type == DataType::Type::kReference && !value.IsConstant())
            ? locations->GetTemp(0).AsRegister<XRegister>()
            : srs.AllocateXRegister();
    ShNAdd(tmp, index.AsRegister<XRegister>(), array, value_type);
    Store(value, tmp, data_offset, value_type);
  }
  // There must be no instructions between the `Store()` and the `MaybeRecordImplicitNullCheck()`.
  // We can avoid this if the type check makes the null check unconditionally.
  DCHECK_IMPLIES(needs_type_check, needs_write_barrier);
  if (!(needs_type_check && !instruction->GetValueCanBeNull())) {
    codegen_->MaybeRecordImplicitNullCheck(instruction);
  }

  if (slow_path != nullptr) {
    __ Bind(slow_path->GetExitLabel());
  }
}

void LocationsBuilderRISCV64::VisitBelow(HBelow* instruction) {
  HandleCondition(instruction);
}

void InstructionCodeGeneratorRISCV64::VisitBelow(HBelow* instruction) {
  HandleCondition(instruction);
}

void LocationsBuilderRISCV64::VisitBelowOrEqual(HBelowOrEqual* instruction) {
  HandleCondition(instruction);
}

void InstructionCodeGeneratorRISCV64::VisitBelowOrEqual(HBelowOrEqual* instruction) {
  HandleCondition(instruction);
}

void LocationsBuilderRISCV64::VisitBooleanNot(HBooleanNot* instruction) {
  LocationSummary* locations = new (GetGraph()->GetAllocator()) LocationSummary(instruction);
  locations->SetInAt(0, Location::RequiresRegister());
  locations->SetOut(Location::RequiresRegister(), Location::kNoOutputOverlap);
}

void InstructionCodeGeneratorRISCV64::VisitBooleanNot(HBooleanNot* instruction) {
  LocationSummary* locations = instruction->GetLocations();
  __ Xori(locations->Out().AsRegister<XRegister>(), locations->InAt(0).AsRegister<XRegister>(), 1);
}

void LocationsBuilderRISCV64::VisitBoundsCheck(HBoundsCheck* instruction) {
  RegisterSet caller_saves = RegisterSet::Empty();
  InvokeRuntimeCallingConvention calling_convention;
  caller_saves.Add(Location::RegisterLocation(calling_convention.GetRegisterAt(0)));
  caller_saves.Add(Location::RegisterLocation(calling_convention.GetRegisterAt(1)));
  LocationSummary* locations = codegen_->CreateThrowingSlowPathLocations(instruction, caller_saves);

  HInstruction* index = instruction->InputAt(0);
  HInstruction* length = instruction->InputAt(1);

  bool const_index = false;
  bool const_length = false;

  if (length->IsConstant()) {
    if (index->IsConstant()) {
      const_index = true;
      const_length = true;
    } else {
      int32_t length_value = length->AsIntConstant()->GetValue();
      if (length_value == 0 || length_value == 1) {
        const_length = true;
      }
    }
  } else if (index->IsConstant()) {
    int32_t index_value = index->AsIntConstant()->GetValue();
    if (index_value <= 0) {
      const_index = true;
    }
  }

  locations->SetInAt(
      0,
      const_index ? Location::ConstantLocation(index) : Location::RequiresRegister());
  locations->SetInAt(
      1,
      const_length ? Location::ConstantLocation(length) : Location::RequiresRegister());
}

void InstructionCodeGeneratorRISCV64::VisitBoundsCheck(HBoundsCheck* instruction) {
  LocationSummary* locations = instruction->GetLocations();
  Location index_loc = locations->InAt(0);
  Location length_loc = locations->InAt(1);

  if (length_loc.IsConstant()) {
    int32_t length = length_loc.GetConstant()->AsIntConstant()->GetValue();
    if (index_loc.IsConstant()) {
      int32_t index = index_loc.GetConstant()->AsIntConstant()->GetValue();
      if (index < 0 || index >= length) {
        BoundsCheckSlowPathRISCV64* slow_path =
            new (codegen_->GetScopedAllocator()) BoundsCheckSlowPathRISCV64(instruction);
        codegen_->AddSlowPath(slow_path);
        __ J(slow_path->GetEntryLabel());
      } else {
        // Nothing to be done.
      }
      return;
    }

    BoundsCheckSlowPathRISCV64* slow_path =
        new (codegen_->GetScopedAllocator()) BoundsCheckSlowPathRISCV64(instruction);
    codegen_->AddSlowPath(slow_path);
    XRegister index = index_loc.AsRegister<XRegister>();
    if (length == 0) {
      __ J(slow_path->GetEntryLabel());
    } else {
      DCHECK_EQ(length, 1);
      __ Bnez(index, slow_path->GetEntryLabel());
    }
  } else {
    XRegister length = length_loc.AsRegister<XRegister>();
    BoundsCheckSlowPathRISCV64* slow_path =
        new (codegen_->GetScopedAllocator()) BoundsCheckSlowPathRISCV64(instruction);
    codegen_->AddSlowPath(slow_path);
    if (index_loc.IsConstant()) {
      int32_t index = index_loc.GetConstant()->AsIntConstant()->GetValue();
      if (index < 0) {
        __ J(slow_path->GetEntryLabel());
      } else {
        DCHECK_EQ(index, 0);
        __ Blez(length, slow_path->GetEntryLabel());
      }
    } else {
      XRegister index = index_loc.AsRegister<XRegister>();
      __ Bgeu(index, length, slow_path->GetEntryLabel());
    }
  }
}

void LocationsBuilderRISCV64::VisitBoundType([[maybe_unused]] HBoundType* instruction) {
  // Nothing to do, this should be removed during prepare for register allocator.
  LOG(FATAL) << "Unreachable";
}

void InstructionCodeGeneratorRISCV64::VisitBoundType([[maybe_unused]] HBoundType* instruction) {
  // Nothing to do, this should be removed during prepare for register allocator.
  LOG(FATAL) << "Unreachable";
}

// Temp is used for read barrier.
static size_t NumberOfInstanceOfTemps(bool emit_read_barrier, TypeCheckKind type_check_kind) {
  if (emit_read_barrier &&
      (kUseBakerReadBarrier ||
       type_check_kind == TypeCheckKind::kAbstractClassCheck ||
       type_check_kind == TypeCheckKind::kClassHierarchyCheck ||
       type_check_kind == TypeCheckKind::kArrayObjectCheck)) {
    return 1;
  }
  return 0;
}

// Interface case has 3 temps, one for holding the number of interfaces, one for the current
// interface pointer, one for loading the current interface.
// The other checks have one temp for loading the object's class and maybe a temp for read barrier.
static size_t NumberOfCheckCastTemps(bool emit_read_barrier, TypeCheckKind type_check_kind) {
  if (type_check_kind == TypeCheckKind::kInterfaceCheck) {
    return 3;
  }
  return 1 + NumberOfInstanceOfTemps(emit_read_barrier, type_check_kind);
}

void LocationsBuilderRISCV64::VisitCheckCast(HCheckCast* instruction) {
  TypeCheckKind type_check_kind = instruction->GetTypeCheckKind();
  LocationSummary::CallKind call_kind = codegen_->GetCheckCastCallKind(instruction);
  LocationSummary* locations =
      new (GetGraph()->GetAllocator()) LocationSummary(instruction, call_kind);
  locations->SetInAt(0, Location::RequiresRegister());
  if (type_check_kind == TypeCheckKind::kBitstringCheck) {
    locations->SetInAt(1, Location::ConstantLocation(instruction->InputAt(1)));
    locations->SetInAt(2, Location::ConstantLocation(instruction->InputAt(2)));
    locations->SetInAt(3, Location::ConstantLocation(instruction->InputAt(3)));
  } else {
    locations->SetInAt(1, Location::RequiresRegister());
  }
  locations->AddRegisterTemps(NumberOfCheckCastTemps(codegen_->EmitReadBarrier(), type_check_kind));
}

void InstructionCodeGeneratorRISCV64::VisitCheckCast(HCheckCast* instruction) {
TypeCheckKind type_check_kind = instruction->GetTypeCheckKind();
  LocationSummary* locations = instruction->GetLocations();
  Location obj_loc = locations->InAt(0);
  XRegister obj = obj_loc.AsRegister<XRegister>();
  Location cls = (type_check_kind == TypeCheckKind::kBitstringCheck)
      ? Location::NoLocation()
      : locations->InAt(1);
  Location temp_loc = locations->GetTemp(0);
  XRegister temp = temp_loc.AsRegister<XRegister>();
  const size_t num_temps = NumberOfCheckCastTemps(codegen_->EmitReadBarrier(), type_check_kind);
  DCHECK_GE(num_temps, 1u);
  DCHECK_LE(num_temps, 3u);
  Location maybe_temp2_loc = (num_temps >= 2) ? locations->GetTemp(1) : Location::NoLocation();
  Location maybe_temp3_loc = (num_temps >= 3) ? locations->GetTemp(2) : Location::NoLocation();
  const uint32_t class_offset = mirror::Object::ClassOffset().Int32Value();
  const uint32_t super_offset = mirror::Class::SuperClassOffset().Int32Value();
  const uint32_t component_offset = mirror::Class::ComponentTypeOffset().Int32Value();
  const uint32_t primitive_offset = mirror::Class::PrimitiveTypeOffset().Int32Value();
  const uint32_t iftable_offset = mirror::Class::IfTableOffset().Uint32Value();
  const uint32_t array_length_offset = mirror::Array::LengthOffset().Uint32Value();
  const uint32_t object_array_data_offset =
      mirror::Array::DataOffset(kHeapReferenceSize).Uint32Value();
  Riscv64Label done;

  bool is_type_check_slow_path_fatal = codegen_->IsTypeCheckSlowPathFatal(instruction);
  SlowPathCodeRISCV64* slow_path =
      new (codegen_->GetScopedAllocator()) TypeCheckSlowPathRISCV64(
          instruction, is_type_check_slow_path_fatal);
  codegen_->AddSlowPath(slow_path);

  // Avoid this check if we know `obj` is not null.
  if (instruction->MustDoNullCheck()) {
    __ Beqz(obj, &done);
  }

  switch (type_check_kind) {
    case TypeCheckKind::kExactCheck:
    case TypeCheckKind::kArrayCheck: {
      // /* HeapReference<Class> */ temp = obj->klass_
      GenerateReferenceLoadTwoRegisters(instruction,
                                        temp_loc,
                                        obj_loc,
                                        class_offset,
                                        maybe_temp2_loc,
                                        kWithoutReadBarrier);
      // Jump to slow path for throwing the exception or doing a
      // more involved array check.
      __ Bne(temp, cls.AsRegister<XRegister>(), slow_path->GetEntryLabel());
      break;
    }

    case TypeCheckKind::kAbstractClassCheck: {
      // /* HeapReference<Class> */ temp = obj->klass_
      GenerateReferenceLoadTwoRegisters(instruction,
                                        temp_loc,
                                        obj_loc,
                                        class_offset,
                                        maybe_temp2_loc,
                                        kWithoutReadBarrier);
      // If the class is abstract, we eagerly fetch the super class of the
      // object to avoid doing a comparison we know will fail.
      Riscv64Label loop;
      __ Bind(&loop);
      // /* HeapReference<Class> */ temp = temp->super_class_
      GenerateReferenceLoadOneRegister(instruction,
                                       temp_loc,
                                       super_offset,
                                       maybe_temp2_loc,
                                       kWithoutReadBarrier);
      // If the class reference currently in `temp` is null, jump to the slow path to throw the
      // exception.
      __ Beqz(temp, slow_path->GetEntryLabel());
      // Otherwise, compare the classes.
      __ Bne(temp, cls.AsRegister<XRegister>(), &loop);
      break;
    }

    case TypeCheckKind::kClassHierarchyCheck: {
      // /* HeapReference<Class> */ temp = obj->klass_
      GenerateReferenceLoadTwoRegisters(instruction,
                                        temp_loc,
                                        obj_loc,
                                        class_offset,
                                        maybe_temp2_loc,
                                        kWithoutReadBarrier);
      // Walk over the class hierarchy to find a match.
      Riscv64Label loop;
      __ Bind(&loop);
      __ Beq(temp, cls.AsRegister<XRegister>(), &done);
      // /* HeapReference<Class> */ temp = temp->super_class_
      GenerateReferenceLoadOneRegister(instruction,
                                       temp_loc,
                                       super_offset,
                                       maybe_temp2_loc,
                                       kWithoutReadBarrier);
      // If the class reference currently in `temp` is null, jump to the slow path to throw the
      // exception. Otherwise, jump to the beginning of the loop.
      __ Bnez(temp, &loop);
      __ J(slow_path->GetEntryLabel());
      break;
    }

    case TypeCheckKind::kArrayObjectCheck: {
      // /* HeapReference<Class> */ temp = obj->klass_
      GenerateReferenceLoadTwoRegisters(instruction,
                                        temp_loc,
                                        obj_loc,
                                        class_offset,
                                        maybe_temp2_loc,
                                        kWithoutReadBarrier);
      // Do an exact check.
      __ Beq(temp, cls.AsRegister<XRegister>(), &done);
      // Otherwise, we need to check that the object's class is a non-primitive array.
      // /* HeapReference<Class> */ temp = temp->component_type_
      GenerateReferenceLoadOneRegister(instruction,
                                       temp_loc,
                                       component_offset,
                                       maybe_temp2_loc,
                                       kWithoutReadBarrier);
      // If the component type is null, jump to the slow path to throw the exception.
      __ Beqz(temp, slow_path->GetEntryLabel());
      // Otherwise, the object is indeed an array, further check that this component
      // type is not a primitive type.
      __ Loadhu(temp, temp, primitive_offset);
      static_assert(Primitive::kPrimNot == 0, "Expected 0 for kPrimNot");
      __ Bnez(temp, slow_path->GetEntryLabel());
      break;
    }

    case TypeCheckKind::kUnresolvedCheck:
      // We always go into the type check slow path for the unresolved check case.
      // We cannot directly call the CheckCast runtime entry point
      // without resorting to a type checking slow path here (i.e. by
      // calling InvokeRuntime directly), as it would require to
      // assign fixed registers for the inputs of this HInstanceOf
      // instruction (following the runtime calling convention), which
      // might be cluttered by the potential first read barrier
      // emission at the beginning of this method.
      __ J(slow_path->GetEntryLabel());
      break;

    case TypeCheckKind::kInterfaceCheck: {
      // Avoid read barriers to improve performance of the fast path. We can not get false
      // positives by doing this. False negatives are handled by the slow path.
      // /* HeapReference<Class> */ temp = obj->klass_
      GenerateReferenceLoadTwoRegisters(instruction,
                                        temp_loc,
                                        obj_loc,
                                        class_offset,
                                        maybe_temp2_loc,
                                        kWithoutReadBarrier);
      // /* HeapReference<Class> */ temp = temp->iftable_
      GenerateReferenceLoadOneRegister(instruction,
                                       temp_loc,
                                       iftable_offset,
                                       maybe_temp2_loc,
                                       kWithoutReadBarrier);
      XRegister temp2 = maybe_temp2_loc.AsRegister<XRegister>();
      XRegister temp3 = maybe_temp3_loc.AsRegister<XRegister>();
      // Iftable is never null.
      __ Loadw(temp2, temp, array_length_offset);
      // Loop through the iftable and check if any class matches.
      Riscv64Label loop;
      __ Bind(&loop);
      __ Beqz(temp2, slow_path->GetEntryLabel());
      __ Lwu(temp3, temp, object_array_data_offset);
      codegen_->MaybeUnpoisonHeapReference(temp3);
      // Go to next interface.
      __ Addi(temp, temp, 2 * kHeapReferenceSize);
      __ Addi(temp2, temp2, -2);
      // Compare the classes and continue the loop if they do not match.
      __ Bne(temp3, cls.AsRegister<XRegister>(), &loop);
      break;
    }

    case TypeCheckKind::kBitstringCheck: {
      // /* HeapReference<Class> */ temp = obj->klass_
      GenerateReferenceLoadTwoRegisters(instruction,
                                        temp_loc,
                                        obj_loc,
                                        class_offset,
                                        maybe_temp2_loc,
                                        kWithoutReadBarrier);

      GenerateBitstringTypeCheckCompare(instruction, temp);
      __ Bnez(temp, slow_path->GetEntryLabel());
      break;
    }
  }

  __ Bind(&done);
  __ Bind(slow_path->GetExitLabel());
}

void LocationsBuilderRISCV64::VisitClassTableGet(HClassTableGet* instruction) {
  LocationSummary* locations =
      new (GetGraph()->GetAllocator()) LocationSummary(instruction, LocationSummary::kNoCall);
  locations->SetInAt(0, Location::RequiresRegister());
  locations->SetOut(Location::RequiresRegister(), Location::kNoOutputOverlap);
}

void InstructionCodeGeneratorRISCV64::VisitClassTableGet(HClassTableGet* instruction) {
  LocationSummary* locations = instruction->GetLocations();
  XRegister in = locations->InAt(0).AsRegister<XRegister>();
  XRegister out = locations->Out().AsRegister<XRegister>();
  if (instruction->GetTableKind() == HClassTableGet::TableKind::kVTable) {
    MemberOffset method_offset =
        mirror::Class::EmbeddedVTableEntryOffset(instruction->GetIndex(), kRiscv64PointerSize);
    __ Loadd(out, in, method_offset.SizeValue());
  } else {
    uint32_t method_offset = dchecked_integral_cast<uint32_t>(
        ImTable::OffsetOfElement(instruction->GetIndex(), kRiscv64PointerSize));
    __ Loadd(out, in, mirror::Class::ImtPtrOffset(kRiscv64PointerSize).Uint32Value());
    __ Loadd(out, out, method_offset);
  }
}

static int32_t GetExceptionTlsOffset() {
  return Thread::ExceptionOffset<kRiscv64PointerSize>().Int32Value();
}

void LocationsBuilderRISCV64::VisitClearException(HClearException* instruction) {
  new (GetGraph()->GetAllocator()) LocationSummary(instruction, LocationSummary::kNoCall);
}

void InstructionCodeGeneratorRISCV64::VisitClearException(
    [[maybe_unused]] HClearException* instruction) {
  __ Stored(Zero, TR, GetExceptionTlsOffset());
}

void LocationsBuilderRISCV64::VisitClinitCheck(HClinitCheck* instruction) {
  LocationSummary* locations = new (GetGraph()->GetAllocator()) LocationSummary(
      instruction, LocationSummary::kCallOnSlowPath);
  locations->SetInAt(0, Location::RequiresRegister());
  if (instruction->HasUses()) {
    locations->SetOut(Location::SameAsFirstInput());
  }
  // Rely on the type initialization to save everything we need.
  locations->SetCustomSlowPathCallerSaves(OneRegInReferenceOutSaveEverythingCallerSaves());
}

void InstructionCodeGeneratorRISCV64::VisitClinitCheck(HClinitCheck* instruction) {
  // We assume the class is not null.
  SlowPathCodeRISCV64* slow_path = new (codegen_->GetScopedAllocator()) LoadClassSlowPathRISCV64(
      instruction->GetLoadClass(), instruction);
  codegen_->AddSlowPath(slow_path);
  GenerateClassInitializationCheck(slow_path,
                                   instruction->GetLocations()->InAt(0).AsRegister<XRegister>());
}

void LocationsBuilderRISCV64::VisitCompare(HCompare* instruction) {
  DataType::Type in_type = instruction->InputAt(0)->GetType();

  LocationSummary* locations = new (GetGraph()->GetAllocator()) LocationSummary(instruction);

  switch (in_type) {
    case DataType::Type::kBool:
    case DataType::Type::kUint8:
    case DataType::Type::kInt8:
    case DataType::Type::kUint16:
    case DataType::Type::kInt16:
    case DataType::Type::kInt32:
    case DataType::Type::kInt64:
      locations->SetInAt(0, Location::RequiresRegister());
      locations->SetInAt(1, RegisterOrZeroBitPatternLocation(instruction->InputAt(1)));
      locations->SetOut(Location::RequiresRegister(), Location::kNoOutputOverlap);
      break;

    case DataType::Type::kFloat32:
    case DataType::Type::kFloat64:
      locations->SetInAt(0, Location::RequiresFpuRegister());
      locations->SetInAt(1, Location::RequiresFpuRegister());
      locations->SetOut(Location::RequiresRegister(), Location::kNoOutputOverlap);
      break;

    default:
      LOG(FATAL) << "Unexpected type for compare operation " << in_type;
      UNREACHABLE();
  }
}

void InstructionCodeGeneratorRISCV64::VisitCompare(HCompare* instruction) {
  LocationSummary* locations = instruction->GetLocations();
  XRegister result = locations->Out().AsRegister<XRegister>();
  DataType::Type in_type = instruction->InputAt(0)->GetType();

  //  0 if: left == right
  //  1 if: left  > right
  // -1 if: left  < right
  switch (in_type) {
    case DataType::Type::kBool:
    case DataType::Type::kUint8:
    case DataType::Type::kInt8:
    case DataType::Type::kUint16:
    case DataType::Type::kInt16:
    case DataType::Type::kInt32:
    case DataType::Type::kInt64: {
      XRegister left = locations->InAt(0).AsRegister<XRegister>();
      XRegister right = InputXRegisterOrZero(locations->InAt(1));
      ScratchRegisterScope srs(GetAssembler());
      XRegister tmp = srs.AllocateXRegister();
      __ Slt(tmp, left, right);
      __ Slt(result, right, left);
      __ Sub(result, result, tmp);
      break;
    }

    case DataType::Type::kFloat32:
    case DataType::Type::kFloat64: {
      FRegister left = locations->InAt(0).AsFpuRegister<FRegister>();
      FRegister right = locations->InAt(1).AsFpuRegister<FRegister>();
      ScratchRegisterScope srs(GetAssembler());
      XRegister tmp = srs.AllocateXRegister();
      if (instruction->IsGtBias()) {
        // ((FLE l,r) ^ 1) - (FLT l,r); see `GenerateFpCondition()`.
        FLe(tmp, left, right, in_type);
        FLt(result, left, right, in_type);
        __ Xori(tmp, tmp, 1);
        __ Sub(result, tmp, result);
      } else {
        // ((FLE r,l) - 1) + (FLT r,l); see `GenerateFpCondition()`.
        FLe(tmp, right, left, in_type);
        FLt(result, right, left, in_type);
        __ Addi(tmp, tmp, -1);
        __ Add(result, result, tmp);
      }
      break;
    }

    default:
      LOG(FATAL) << "Unimplemented compare type " << in_type;
  }
}

void LocationsBuilderRISCV64::VisitConstructorFence(HConstructorFence* instruction) {
  instruction->SetLocations(nullptr);
}

void InstructionCodeGeneratorRISCV64::VisitConstructorFence(
    [[maybe_unused]] HConstructorFence* instruction) {
  codegen_->GenerateMemoryBarrier(MemBarrierKind::kStoreStore);
}

void LocationsBuilderRISCV64::VisitCurrentMethod(HCurrentMethod* instruction) {
  LocationSummary* locations =
      new (GetGraph()->GetAllocator()) LocationSummary(instruction, LocationSummary::kNoCall);
  locations->SetOut(Location::RegisterLocation(kArtMethodRegister));
}

void InstructionCodeGeneratorRISCV64::VisitCurrentMethod(
    [[maybe_unused]] HCurrentMethod* instruction) {
  // Nothing to do, the method is already at its location.
}

void LocationsBuilderRISCV64::VisitShouldDeoptimizeFlag(HShouldDeoptimizeFlag* instruction) {
  LocationSummary* locations =
      new (GetGraph()->GetAllocator()) LocationSummary(instruction, LocationSummary::kNoCall);
  locations->SetOut(Location::RequiresRegister());
}

void InstructionCodeGeneratorRISCV64::VisitShouldDeoptimizeFlag(
    HShouldDeoptimizeFlag* instruction) {
  __ Loadw(instruction->GetLocations()->Out().AsRegister<XRegister>(),
           SP,
           codegen_->GetStackOffsetOfShouldDeoptimizeFlag());
}

void LocationsBuilderRISCV64::VisitDeoptimize(HDeoptimize* instruction) {
  LocationSummary* locations = new (GetGraph()->GetAllocator())
      LocationSummary(instruction, LocationSummary::kCallOnSlowPath);
  InvokeRuntimeCallingConvention calling_convention;
  RegisterSet caller_saves = RegisterSet::Empty();
  caller_saves.Add(Location::RegisterLocation(calling_convention.GetRegisterAt(0)));
  locations->SetCustomSlowPathCallerSaves(caller_saves);
  if (IsBooleanValueOrMaterializedCondition(instruction->InputAt(0))) {
    locations->SetInAt(0, Location::RequiresRegister());
  }
}

void InstructionCodeGeneratorRISCV64::VisitDeoptimize(HDeoptimize* instruction) {
  SlowPathCodeRISCV64* slow_path =
      deopt_slow_paths_.NewSlowPath<DeoptimizationSlowPathRISCV64>(instruction);
  GenerateTestAndBranch(instruction,
                        /* condition_input_index= */ 0,
                        slow_path->GetEntryLabel(),
                        /* false_target= */ nullptr);
}

void LocationsBuilderRISCV64::VisitDiv(HDiv* instruction) {
  LocationSummary* locations =
      new (GetGraph()->GetAllocator()) LocationSummary(instruction, LocationSummary::kNoCall);
  switch (instruction->GetResultType()) {
    case DataType::Type::kInt32:
    case DataType::Type::kInt64:
      locations->SetInAt(0, Location::RequiresRegister());
      locations->SetInAt(1, Location::RegisterOrConstant(instruction->InputAt(1)));
      locations->SetOut(Location::RequiresRegister(), Location::kNoOutputOverlap);
      break;

    case DataType::Type::kFloat32:
    case DataType::Type::kFloat64:
      locations->SetInAt(0, Location::RequiresFpuRegister());
      locations->SetInAt(1, Location::RequiresFpuRegister());
      locations->SetOut(Location::RequiresFpuRegister(), Location::kNoOutputOverlap);
      break;

    default:
      LOG(FATAL) << "Unexpected div type " << instruction->GetResultType();
      UNREACHABLE();
  }
}

void InstructionCodeGeneratorRISCV64::VisitDiv(HDiv* instruction) {
  DataType::Type type = instruction->GetType();
  LocationSummary* locations = instruction->GetLocations();

  switch (type) {
    case DataType::Type::kInt32:
    case DataType::Type::kInt64:
      GenerateDivRemIntegral(instruction);
      break;
    case DataType::Type::kFloat32:
    case DataType::Type::kFloat64: {
      FRegister dst = locations->Out().AsFpuRegister<FRegister>();
      FRegister lhs = locations->InAt(0).AsFpuRegister<FRegister>();
      FRegister rhs = locations->InAt(1).AsFpuRegister<FRegister>();
      FDiv(dst, lhs, rhs, type);
      break;
    }
    default:
      LOG(FATAL) << "Unexpected div type " << type;
      UNREACHABLE();
  }
}

void LocationsBuilderRISCV64::VisitDivZeroCheck(HDivZeroCheck* instruction) {
  LocationSummary* locations = codegen_->CreateThrowingSlowPathLocations(instruction);
  locations->SetInAt(0, Location::RegisterOrConstant(instruction->InputAt(0)));
}

void InstructionCodeGeneratorRISCV64::VisitDivZeroCheck(HDivZeroCheck* instruction) {
  SlowPathCodeRISCV64* slow_path =
      new (codegen_->GetScopedAllocator()) DivZeroCheckSlowPathRISCV64(instruction);
  codegen_->AddSlowPath(slow_path);
  Location value = instruction->GetLocations()->InAt(0);

  DataType::Type type = instruction->GetType();

  if (!DataType::IsIntegralType(type)) {
    LOG(FATAL) << "Unexpected type " << type << " for DivZeroCheck.";
    UNREACHABLE();
  }

  if (value.IsConstant()) {
    int64_t divisor = codegen_->GetInt64ValueOf(value.GetConstant()->AsConstant());
    if (divisor == 0) {
      __ J(slow_path->GetEntryLabel());
    } else {
      // A division by a non-null constant is valid. We don't need to perform
      // any check, so simply fall through.
    }
  } else {
    __ Beqz(value.AsRegister<XRegister>(), slow_path->GetEntryLabel());
  }
}

void LocationsBuilderRISCV64::VisitDoubleConstant(HDoubleConstant* instruction) {
  LocationSummary* locations =
      new (GetGraph()->GetAllocator()) LocationSummary(instruction, LocationSummary::kNoCall);
  locations->SetOut(Location::ConstantLocation(instruction));
}

void InstructionCodeGeneratorRISCV64::VisitDoubleConstant(
    [[maybe_unused]] HDoubleConstant* instruction) {
  // Will be generated at use site.
}

void LocationsBuilderRISCV64::VisitEqual(HEqual* instruction) {
  HandleCondition(instruction);
}

void InstructionCodeGeneratorRISCV64::VisitEqual(HEqual* instruction) {
  HandleCondition(instruction);
}

void LocationsBuilderRISCV64::VisitExit(HExit* instruction) {
  instruction->SetLocations(nullptr);
}

void InstructionCodeGeneratorRISCV64::VisitExit([[maybe_unused]] HExit* instruction) {}

void LocationsBuilderRISCV64::VisitFloatConstant(HFloatConstant* instruction) {
  LocationSummary* locations =
      new (GetGraph()->GetAllocator()) LocationSummary(instruction, LocationSummary::kNoCall);
  locations->SetOut(Location::ConstantLocation(instruction));
}

void InstructionCodeGeneratorRISCV64::VisitFloatConstant(
    [[maybe_unused]] HFloatConstant* instruction) {
  // Will be generated at use site.
}

void LocationsBuilderRISCV64::VisitGoto(HGoto* instruction) {
  instruction->SetLocations(nullptr);
}

void InstructionCodeGeneratorRISCV64::VisitGoto(HGoto* instruction) {
  HandleGoto(instruction, instruction->GetSuccessor());
}

void LocationsBuilderRISCV64::VisitGreaterThan(HGreaterThan* instruction) {
  HandleCondition(instruction);
}

void InstructionCodeGeneratorRISCV64::VisitGreaterThan(HGreaterThan* instruction) {
  HandleCondition(instruction);
}

void LocationsBuilderRISCV64::VisitGreaterThanOrEqual(HGreaterThanOrEqual* instruction) {
  HandleCondition(instruction);
}

void InstructionCodeGeneratorRISCV64::VisitGreaterThanOrEqual(HGreaterThanOrEqual* instruction) {
  HandleCondition(instruction);
}

void LocationsBuilderRISCV64::VisitIf(HIf* instruction) {
  LocationSummary* locations = new (GetGraph()->GetAllocator()) LocationSummary(instruction);
  if (IsBooleanValueOrMaterializedCondition(instruction->InputAt(0))) {
    locations->SetInAt(0, Location::RequiresRegister());
    if (GetGraph()->IsCompilingBaseline() &&
        codegen_->GetCompilerOptions().ProfileBranches() &&
        !Runtime::Current()->IsAotCompiler()) {
      DCHECK(instruction->InputAt(0)->IsCondition());
      ProfilingInfo* info = GetGraph()->GetProfilingInfo();
      DCHECK(info != nullptr);
      BranchCache* cache = info->GetBranchCache(instruction->GetDexPc());
      if (cache != nullptr) {
        locations->AddTemp(Location::RequiresRegister());
      }
    }
  }
}

void InstructionCodeGeneratorRISCV64::VisitIf(HIf* instruction) {
  HBasicBlock* true_successor = instruction->IfTrueSuccessor();
  HBasicBlock* false_successor = instruction->IfFalseSuccessor();
  Riscv64Label* true_target = codegen_->GoesToNextBlock(instruction->GetBlock(), true_successor)
      ? nullptr
      : codegen_->GetLabelOf(true_successor);
  Riscv64Label* false_target = codegen_->GoesToNextBlock(instruction->GetBlock(), false_successor)
      ? nullptr
      : codegen_->GetLabelOf(false_successor);
  if (IsBooleanValueOrMaterializedCondition(instruction->InputAt(0))) {
    if (GetGraph()->IsCompilingBaseline() &&
        codegen_->GetCompilerOptions().ProfileBranches() &&
        !Runtime::Current()->IsAotCompiler()) {
      DCHECK(instruction->InputAt(0)->IsCondition());
      ProfilingInfo* info = GetGraph()->GetProfilingInfo();
      DCHECK(info != nullptr);
      BranchCache* cache = info->GetBranchCache(instruction->GetDexPc());
      // Currently, not all If branches are profiled.
      if (cache != nullptr) {
        uint64_t address =
            reinterpret_cast64<uint64_t>(cache) + BranchCache::FalseOffset().Int32Value();
        static_assert(
            BranchCache::TrueOffset().Int32Value() - BranchCache::FalseOffset().Int32Value() == 2,
            "Unexpected offsets for BranchCache");
        Riscv64Label done;
        XRegister condition = instruction->GetLocations()->InAt(0).AsRegister<XRegister>();
        XRegister temp = instruction->GetLocations()->GetTemp(0).AsRegister<XRegister>();
        __ LoadConst64(temp, address);
        __ Sh1Add(temp, condition, temp);
        ScratchRegisterScope srs(GetAssembler());
        XRegister counter = srs.AllocateXRegister();
        __ Loadhu(counter, temp, 0);
        __ Addi(counter, counter, 1);
        {
          ScratchRegisterScope srs2(GetAssembler());
          XRegister overflow = srs2.AllocateXRegister();
          __ Srli(overflow, counter, 16);
          __ Bnez(overflow, &done);
        }
        __ Storeh(counter, temp, 0);
        __ Bind(&done);
      }
    }
  }
  GenerateTestAndBranch(instruction, /* condition_input_index= */ 0, true_target, false_target);
}

void LocationsBuilderRISCV64::VisitInstanceFieldGet(HInstanceFieldGet* instruction) {
  HandleFieldGet(instruction);
}

void InstructionCodeGeneratorRISCV64::VisitInstanceFieldGet(HInstanceFieldGet* instruction) {
  HandleFieldGet(instruction, instruction->GetFieldInfo());
}

void LocationsBuilderRISCV64::VisitInstanceFieldSet(HInstanceFieldSet* instruction) {
  HandleFieldSet(instruction);
}

void InstructionCodeGeneratorRISCV64::VisitInstanceFieldSet(HInstanceFieldSet* instruction) {
  HandleFieldSet(instruction,
                 instruction->GetFieldInfo(),
                 instruction->GetValueCanBeNull(),
                 instruction->GetWriteBarrierKind());
}

void LocationsBuilderRISCV64::VisitInstanceOf(HInstanceOf* instruction) {
  LocationSummary::CallKind call_kind = LocationSummary::kNoCall;
  TypeCheckKind type_check_kind = instruction->GetTypeCheckKind();
  bool baker_read_barrier_slow_path = false;
  switch (type_check_kind) {
    case TypeCheckKind::kExactCheck:
    case TypeCheckKind::kAbstractClassCheck:
    case TypeCheckKind::kClassHierarchyCheck:
    case TypeCheckKind::kArrayObjectCheck: {
      bool needs_read_barrier = codegen_->InstanceOfNeedsReadBarrier(instruction);
      call_kind = needs_read_barrier ? LocationSummary::kCallOnSlowPath : LocationSummary::kNoCall;
      baker_read_barrier_slow_path = kUseBakerReadBarrier && needs_read_barrier;
      break;
    }
    case TypeCheckKind::kArrayCheck:
    case TypeCheckKind::kUnresolvedCheck:
    case TypeCheckKind::kInterfaceCheck:
      call_kind = LocationSummary::kCallOnSlowPath;
      break;
    case TypeCheckKind::kBitstringCheck:
      break;
  }

  LocationSummary* locations =
      new (GetGraph()->GetAllocator()) LocationSummary(instruction, call_kind);
  if (baker_read_barrier_slow_path) {
    locations->SetCustomSlowPathCallerSaves(RegisterSet::Empty());  // No caller-save registers.
  }
  locations->SetInAt(0, Location::RequiresRegister());
  if (type_check_kind == TypeCheckKind::kBitstringCheck) {
    locations->SetInAt(1, Location::ConstantLocation(instruction->InputAt(1)));
    locations->SetInAt(2, Location::ConstantLocation(instruction->InputAt(2)));
    locations->SetInAt(3, Location::ConstantLocation(instruction->InputAt(3)));
  } else {
    locations->SetInAt(1, Location::RequiresRegister());
  }
  // The output does overlap inputs.
  // Note that TypeCheckSlowPathRISCV64 uses this register too.
  locations->SetOut(Location::RequiresRegister(), Location::kOutputOverlap);
  locations->AddRegisterTemps(
      NumberOfInstanceOfTemps(codegen_->EmitReadBarrier(), type_check_kind));
}

void InstructionCodeGeneratorRISCV64::VisitInstanceOf(HInstanceOf* instruction) {
  TypeCheckKind type_check_kind = instruction->GetTypeCheckKind();
  LocationSummary* locations = instruction->GetLocations();
  Location obj_loc = locations->InAt(0);
  XRegister obj = obj_loc.AsRegister<XRegister>();
  Location cls = (type_check_kind == TypeCheckKind::kBitstringCheck)
      ? Location::NoLocation()
      : locations->InAt(1);
  Location out_loc = locations->Out();
  XRegister out = out_loc.AsRegister<XRegister>();
  const size_t num_temps = NumberOfInstanceOfTemps(codegen_->EmitReadBarrier(), type_check_kind);
  DCHECK_LE(num_temps, 1u);
  Location maybe_temp_loc = (num_temps >= 1) ? locations->GetTemp(0) : Location::NoLocation();
  uint32_t class_offset = mirror::Object::ClassOffset().Int32Value();
  uint32_t super_offset = mirror::Class::SuperClassOffset().Int32Value();
  uint32_t component_offset = mirror::Class::ComponentTypeOffset().Int32Value();
  uint32_t primitive_offset = mirror::Class::PrimitiveTypeOffset().Int32Value();
  Riscv64Label done;
  SlowPathCodeRISCV64* slow_path = nullptr;

  // Return 0 if `obj` is null.
  // Avoid this check if we know `obj` is not null.
  if (instruction->MustDoNullCheck()) {
    __ Mv(out, Zero);
    __ Beqz(obj, &done);
  }

  switch (type_check_kind) {
    case TypeCheckKind::kExactCheck: {
      ReadBarrierOption read_barrier_option =
          codegen_->ReadBarrierOptionForInstanceOf(instruction);
      // /* HeapReference<Class> */ out = obj->klass_
      GenerateReferenceLoadTwoRegisters(
          instruction, out_loc, obj_loc, class_offset, maybe_temp_loc, read_barrier_option);
      // Classes must be equal for the instanceof to succeed.
      __ Xor(out, out, cls.AsRegister<XRegister>());
      __ Seqz(out, out);
      break;
    }

    case TypeCheckKind::kAbstractClassCheck: {
      ReadBarrierOption read_barrier_option =
          codegen_->ReadBarrierOptionForInstanceOf(instruction);
      // /* HeapReference<Class> */ out = obj->klass_
      GenerateReferenceLoadTwoRegisters(
          instruction, out_loc, obj_loc, class_offset, maybe_temp_loc, read_barrier_option);
      // If the class is abstract, we eagerly fetch the super class of the
      // object to avoid doing a comparison we know will fail.
      Riscv64Label loop;
      __ Bind(&loop);
      // /* HeapReference<Class> */ out = out->super_class_
      GenerateReferenceLoadOneRegister(
          instruction, out_loc, super_offset, maybe_temp_loc, read_barrier_option);
      // If `out` is null, we use it for the result, and jump to `done`.
      __ Beqz(out, &done);
      __ Bne(out, cls.AsRegister<XRegister>(), &loop);
      __ LoadConst32(out, 1);
      break;
    }

    case TypeCheckKind::kClassHierarchyCheck: {
      ReadBarrierOption read_barrier_option =
          codegen_->ReadBarrierOptionForInstanceOf(instruction);
      // /* HeapReference<Class> */ out = obj->klass_
      GenerateReferenceLoadTwoRegisters(
          instruction, out_loc, obj_loc, class_offset, maybe_temp_loc, read_barrier_option);
      // Walk over the class hierarchy to find a match.
      Riscv64Label loop, success;
      __ Bind(&loop);
      __ Beq(out, cls.AsRegister<XRegister>(), &success);
      // /* HeapReference<Class> */ out = out->super_class_
      GenerateReferenceLoadOneRegister(
          instruction, out_loc, super_offset, maybe_temp_loc, read_barrier_option);
      __ Bnez(out, &loop);
      // If `out` is null, we use it for the result, and jump to `done`.
      __ J(&done);
      __ Bind(&success);
      __ LoadConst32(out, 1);
      break;
    }

    case TypeCheckKind::kArrayObjectCheck: {
      ReadBarrierOption read_barrier_option =
          codegen_->ReadBarrierOptionForInstanceOf(instruction);
      // FIXME(riscv64): We currently have marking entrypoints for 29 registers.
      // We need to either store entrypoint for register `N` in entry `N-A` where
      // `A` can be up to 5 (Zero, RA, SP, GP, TP are not valid registers for
      // marking), or define two more entrypoints, or request an additional temp
      // from the register allocator instead of using a scratch register.
      ScratchRegisterScope srs(GetAssembler());
      Location tmp = Location::RegisterLocation(srs.AllocateXRegister());
      // /* HeapReference<Class> */ tmp = obj->klass_
      GenerateReferenceLoadTwoRegisters(
          instruction, tmp, obj_loc, class_offset, maybe_temp_loc, read_barrier_option);
      // Do an exact check.
      __ LoadConst32(out, 1);
      __ Beq(tmp.AsRegister<XRegister>(), cls.AsRegister<XRegister>(), &done);
      // Otherwise, we need to check that the object's class is a non-primitive array.
      // /* HeapReference<Class> */ out = out->component_type_
      GenerateReferenceLoadTwoRegisters(
          instruction, out_loc, tmp, component_offset, maybe_temp_loc, read_barrier_option);
      // If `out` is null, we use it for the result, and jump to `done`.
      __ Beqz(out, &done);
      __ Loadhu(out, out, primitive_offset);
      static_assert(Primitive::kPrimNot == 0, "Expected 0 for kPrimNot");
      __ Seqz(out, out);
      break;
    }

    case TypeCheckKind::kArrayCheck: {
      // No read barrier since the slow path will retry upon failure.
      // /* HeapReference<Class> */ out = obj->klass_
      GenerateReferenceLoadTwoRegisters(
          instruction, out_loc, obj_loc, class_offset, maybe_temp_loc, kWithoutReadBarrier);
      DCHECK(locations->OnlyCallsOnSlowPath());
      slow_path = new (codegen_->GetScopedAllocator())
          TypeCheckSlowPathRISCV64(instruction, /* is_fatal= */ false);
      codegen_->AddSlowPath(slow_path);
      __ Bne(out, cls.AsRegister<XRegister>(), slow_path->GetEntryLabel());
      __ LoadConst32(out, 1);
      break;
    }

    case TypeCheckKind::kUnresolvedCheck:
    case TypeCheckKind::kInterfaceCheck: {
      // Note that we indeed only call on slow path, but we always go
      // into the slow path for the unresolved and interface check
      // cases.
      //
      // We cannot directly call the InstanceofNonTrivial runtime
      // entry point without resorting to a type checking slow path
      // here (i.e. by calling InvokeRuntime directly), as it would
      // require to assign fixed registers for the inputs of this
      // HInstanceOf instruction (following the runtime calling
      // convention), which might be cluttered by the potential first
      // read barrier emission at the beginning of this method.
      //
      // TODO: Introduce a new runtime entry point taking the object
      // to test (instead of its class) as argument, and let it deal
      // with the read barrier issues. This will let us refactor this
      // case of the `switch` code as it was previously (with a direct
      // call to the runtime not using a type checking slow path).
      // This should also be beneficial for the other cases above.
      DCHECK(locations->OnlyCallsOnSlowPath());
      slow_path = new (codegen_->GetScopedAllocator()) TypeCheckSlowPathRISCV64(
          instruction, /* is_fatal= */ false);
      codegen_->AddSlowPath(slow_path);
      __ J(slow_path->GetEntryLabel());
      break;
    }

    case TypeCheckKind::kBitstringCheck: {
      // /* HeapReference<Class> */ temp = obj->klass_
      GenerateReferenceLoadTwoRegisters(
          instruction, out_loc, obj_loc, class_offset, maybe_temp_loc, kWithoutReadBarrier);

      GenerateBitstringTypeCheckCompare(instruction, out);
      __ Beqz(out, out);
      break;
    }
  }

  __ Bind(&done);

  if (slow_path != nullptr) {
    __ Bind(slow_path->GetExitLabel());
  }
}

void LocationsBuilderRISCV64::VisitIntConstant(HIntConstant* instruction) {
  LocationSummary* locations = new (GetGraph()->GetAllocator()) LocationSummary(instruction);
  locations->SetOut(Location::ConstantLocation(instruction));
}

void InstructionCodeGeneratorRISCV64::VisitIntConstant([[maybe_unused]] HIntConstant* instruction) {
  // Will be generated at use site.
}

void LocationsBuilderRISCV64::VisitIntermediateAddress(HIntermediateAddress* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::VisitIntermediateAddress(HIntermediateAddress* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void LocationsBuilderRISCV64::VisitInvokeUnresolved(HInvokeUnresolved* instruction) {
  // The trampoline uses the same calling convention as dex calling conventions, except
  // instead of loading arg0/A0 with the target Method*, arg0/A0 will contain the method_idx.
  HandleInvoke(instruction);
}

void InstructionCodeGeneratorRISCV64::VisitInvokeUnresolved(HInvokeUnresolved* instruction) {
  codegen_->GenerateInvokeUnresolvedRuntimeCall(instruction);
}

void LocationsBuilderRISCV64::VisitInvokeInterface(HInvokeInterface* instruction) {
  HandleInvoke(instruction);
  // Use T0 as the hidden argument for `art_quick_imt_conflict_trampoline`.
  if (instruction->GetHiddenArgumentLoadKind() == MethodLoadKind::kRecursive) {
    instruction->GetLocations()->SetInAt(instruction->GetNumberOfArguments() - 1,
                                         Location::RegisterLocation(T0));
  } else {
    instruction->GetLocations()->AddTemp(Location::RegisterLocation(T0));
  }
}

void InstructionCodeGeneratorRISCV64::VisitInvokeInterface(HInvokeInterface* instruction) {
  LocationSummary* locations = instruction->GetLocations();
  XRegister temp = locations->GetTemp(0).AsRegister<XRegister>();
  XRegister receiver = locations->InAt(0).AsRegister<XRegister>();
  int32_t class_offset = mirror::Object::ClassOffset().Int32Value();
  Offset entry_point = ArtMethod::EntryPointFromQuickCompiledCodeOffset(kRiscv64PointerSize);

  // /* HeapReference<Class> */ temp = receiver->klass_
  __ Loadwu(temp, receiver, class_offset);
  codegen_->MaybeRecordImplicitNullCheck(instruction);
  // Instead of simply (possibly) unpoisoning `temp` here, we should
  // emit a read barrier for the previous class reference load.
  // However this is not required in practice, as this is an
  // intermediate/temporary reference and because the current
  // concurrent copying collector keeps the from-space memory
  // intact/accessible until the end of the marking phase (the
  // concurrent copying collector may not in the future).
  codegen_->MaybeUnpoisonHeapReference(temp);

  // If we're compiling baseline, update the inline cache.
  codegen_->MaybeGenerateInlineCacheCheck(instruction, temp);

  // The register T0 is required to be used for the hidden argument in
  // `art_quick_imt_conflict_trampoline`.
  if (instruction->GetHiddenArgumentLoadKind() != MethodLoadKind::kRecursive &&
      instruction->GetHiddenArgumentLoadKind() != MethodLoadKind::kRuntimeCall) {
    Location hidden_reg = instruction->GetLocations()->GetTemp(1);
    // Load the resolved interface method in the hidden argument register T0.
    DCHECK_EQ(T0, hidden_reg.AsRegister<XRegister>());
    codegen_->LoadMethod(instruction->GetHiddenArgumentLoadKind(), hidden_reg, instruction);
  }

  __ Loadd(temp, temp, mirror::Class::ImtPtrOffset(kRiscv64PointerSize).Uint32Value());
  uint32_t method_offset = static_cast<uint32_t>(ImTable::OffsetOfElement(
      instruction->GetImtIndex(), kRiscv64PointerSize));
  // temp = temp->GetImtEntryAt(method_offset);
  __ Loadd(temp, temp, method_offset);
  if (instruction->GetHiddenArgumentLoadKind() == MethodLoadKind::kRuntimeCall) {
    // We pass the method from the IMT in case of a conflict. This will ensure
    // we go into the runtime to resolve the actual method.
    Location hidden_reg = instruction->GetLocations()->GetTemp(1);
    DCHECK_EQ(T0, hidden_reg.AsRegister<XRegister>());
    __ Mv(hidden_reg.AsRegister<XRegister>(), temp);
  }
  // RA = temp->GetEntryPoint();
  __ Loadd(RA, temp, entry_point.Int32Value());

  // RA();
  __ Jalr(RA);
  DCHECK(!codegen_->IsLeafMethod());
  codegen_->RecordPcInfo(instruction, instruction->GetDexPc());
}

void LocationsBuilderRISCV64::VisitInvokeStaticOrDirect(HInvokeStaticOrDirect* instruction) {
  // Explicit clinit checks triggered by static invokes must have been pruned by
  // art::PrepareForRegisterAllocation.
  DCHECK(!instruction->IsStaticWithExplicitClinitCheck());

  IntrinsicLocationsBuilderRISCV64 intrinsic(GetGraph()->GetAllocator(), codegen_);
  if (intrinsic.TryDispatch(instruction)) {
    return;
  }

  if (instruction->GetCodePtrLocation() == CodePtrLocation::kCallCriticalNative) {
    CriticalNativeCallingConventionVisitorRiscv64 calling_convention_visitor(
        /*for_register_allocation=*/ true);
    CodeGenerator::CreateCommonInvokeLocationSummary(instruction, &calling_convention_visitor);
  } else {
    HandleInvoke(instruction);
  }
}

static bool TryGenerateIntrinsicCode(HInvoke* invoke, CodeGeneratorRISCV64* codegen) {
  if (invoke->GetLocations()->Intrinsified()) {
    IntrinsicCodeGeneratorRISCV64 intrinsic(codegen);
    intrinsic.Dispatch(invoke);
    return true;
  }
  return false;
}

void InstructionCodeGeneratorRISCV64::VisitInvokeStaticOrDirect(
    HInvokeStaticOrDirect* instruction) {
  // Explicit clinit checks triggered by static invokes must have been pruned by
  // art::PrepareForRegisterAllocation.
  DCHECK(!instruction->IsStaticWithExplicitClinitCheck());

  if (TryGenerateIntrinsicCode(instruction, codegen_)) {
    return;
  }

  LocationSummary* locations = instruction->GetLocations();
  codegen_->GenerateStaticOrDirectCall(
      instruction, locations->HasTemps() ? locations->GetTemp(0) : Location::NoLocation());
}

void LocationsBuilderRISCV64::VisitInvokeVirtual(HInvokeVirtual* instruction) {
  IntrinsicLocationsBuilderRISCV64 intrinsic(GetGraph()->GetAllocator(), codegen_);
  if (intrinsic.TryDispatch(instruction)) {
    return;
  }

  HandleInvoke(instruction);
}

void InstructionCodeGeneratorRISCV64::VisitInvokeVirtual(HInvokeVirtual* instruction) {
  if (TryGenerateIntrinsicCode(instruction, codegen_)) {
    return;
  }

  codegen_->GenerateVirtualCall(instruction, instruction->GetLocations()->GetTemp(0));
  DCHECK(!codegen_->IsLeafMethod());
}

void LocationsBuilderRISCV64::VisitInvokePolymorphic(HInvokePolymorphic* instruction) {
  IntrinsicLocationsBuilderRISCV64 intrinsic(GetGraph()->GetAllocator(), codegen_);
  if (intrinsic.TryDispatch(instruction)) {
    return;
  }
  HandleInvoke(instruction);
}

void InstructionCodeGeneratorRISCV64::VisitInvokePolymorphic(HInvokePolymorphic* instruction) {
  if (TryGenerateIntrinsicCode(instruction, codegen_)) {
    return;
  }
  codegen_->GenerateInvokePolymorphicCall(instruction);
}

void LocationsBuilderRISCV64::VisitInvokeCustom(HInvokeCustom* instruction) {
  HandleInvoke(instruction);
}

void InstructionCodeGeneratorRISCV64::VisitInvokeCustom(HInvokeCustom* instruction) {
  codegen_->GenerateInvokeCustomCall(instruction);
}

void LocationsBuilderRISCV64::VisitLessThan(HLessThan* instruction) {
  HandleCondition(instruction);
}

void InstructionCodeGeneratorRISCV64::VisitLessThan(HLessThan* instruction) {
  HandleCondition(instruction);
}

void LocationsBuilderRISCV64::VisitLessThanOrEqual(HLessThanOrEqual* instruction) {
  HandleCondition(instruction);
}

void InstructionCodeGeneratorRISCV64::VisitLessThanOrEqual(HLessThanOrEqual* instruction) {
  HandleCondition(instruction);
}

void LocationsBuilderRISCV64::VisitLoadClass(HLoadClass* instruction) {
  HLoadClass::LoadKind load_kind = instruction->GetLoadKind();
  if (load_kind == HLoadClass::LoadKind::kRuntimeCall) {
    InvokeRuntimeCallingConvention calling_convention;
    Location loc = Location::RegisterLocation(calling_convention.GetRegisterAt(0));
    DCHECK_EQ(DataType::Type::kReference, instruction->GetType());
    DCHECK(loc.Equals(calling_convention.GetReturnLocation(DataType::Type::kReference)));
    CodeGenerator::CreateLoadClassRuntimeCallLocationSummary(instruction, loc, loc);
    return;
  }
  DCHECK_EQ(instruction->NeedsAccessCheck(),
            load_kind == HLoadClass::LoadKind::kBssEntryPublic ||
                load_kind == HLoadClass::LoadKind::kBssEntryPackage);

  const bool requires_read_barrier = !instruction->IsInBootImage() && codegen_->EmitReadBarrier();
  LocationSummary::CallKind call_kind = (instruction->NeedsEnvironment() || requires_read_barrier)
      ? LocationSummary::kCallOnSlowPath
      : LocationSummary::kNoCall;
  LocationSummary* locations =
      new (GetGraph()->GetAllocator()) LocationSummary(instruction, call_kind);
  if (kUseBakerReadBarrier && requires_read_barrier && !instruction->NeedsEnvironment()) {
    locations->SetCustomSlowPathCallerSaves(RegisterSet::Empty());  // No caller-save registers.
  }
  if (load_kind == HLoadClass::LoadKind::kReferrersClass) {
    locations->SetInAt(0, Location::RequiresRegister());
  }
  locations->SetOut(Location::RequiresRegister());
  if (load_kind == HLoadClass::LoadKind::kBssEntry ||
      load_kind == HLoadClass::LoadKind::kBssEntryPublic ||
      load_kind == HLoadClass::LoadKind::kBssEntryPackage) {
    if (codegen_->EmitNonBakerReadBarrier()) {
      // For non-Baker read barriers we have a temp-clobbering call.
    } else {
      // Rely on the type resolution or initialization and marking to save everything we need.
      locations->SetCustomSlowPathCallerSaves(OneRegInReferenceOutSaveEverythingCallerSaves());
    }
  }
}

// NO_THREAD_SAFETY_ANALYSIS as we manipulate handles whose internal object we know does not
// move.
void InstructionCodeGeneratorRISCV64::VisitLoadClass(HLoadClass* instruction)
    NO_THREAD_SAFETY_ANALYSIS {
  HLoadClass::LoadKind load_kind = instruction->GetLoadKind();
  if (load_kind == HLoadClass::LoadKind::kRuntimeCall) {
    codegen_->GenerateLoadClassRuntimeCall(instruction);
    return;
  }
  DCHECK_EQ(instruction->NeedsAccessCheck(),
            load_kind == HLoadClass::LoadKind::kBssEntryPublic ||
                load_kind == HLoadClass::LoadKind::kBssEntryPackage);

  LocationSummary* locations = instruction->GetLocations();
  Location out_loc = locations->Out();
  XRegister out = out_loc.AsRegister<XRegister>();
  const ReadBarrierOption read_barrier_option =
      instruction->IsInBootImage() ? kWithoutReadBarrier : codegen_->GetCompilerReadBarrierOption();
  bool generate_null_check = false;
  switch (load_kind) {
    case HLoadClass::LoadKind::kReferrersClass: {
      DCHECK(!instruction->CanCallRuntime());
      DCHECK(!instruction->MustGenerateClinitCheck());
      // /* GcRoot<mirror::Class> */ out = current_method->declaring_class_
      XRegister current_method = locations->InAt(0).AsRegister<XRegister>();
      codegen_->GenerateGcRootFieldLoad(instruction,
                                        out_loc,
                                        current_method,
                                        ArtMethod::DeclaringClassOffset().Int32Value(),
                                        read_barrier_option);
      break;
    }
    case HLoadClass::LoadKind::kBootImageLinkTimePcRelative: {
      DCHECK(codegen_->GetCompilerOptions().IsBootImage() ||
             codegen_->GetCompilerOptions().IsBootImageExtension());
      DCHECK_EQ(read_barrier_option, kWithoutReadBarrier);
      CodeGeneratorRISCV64::PcRelativePatchInfo* info_high =
          codegen_->NewBootImageTypePatch(instruction->GetDexFile(), instruction->GetTypeIndex());
      codegen_->EmitPcRelativeAuipcPlaceholder(info_high, out);
      CodeGeneratorRISCV64::PcRelativePatchInfo* info_low =
          codegen_->NewBootImageTypePatch(
              instruction->GetDexFile(), instruction->GetTypeIndex(), info_high);
      codegen_->EmitPcRelativeAddiPlaceholder(info_low, out, out);
      break;
    }
    case HLoadClass::LoadKind::kBootImageRelRo: {
      DCHECK(!codegen_->GetCompilerOptions().IsBootImage());
      uint32_t boot_image_offset = codegen_->GetBootImageOffset(instruction);
      codegen_->LoadBootImageRelRoEntry(out, boot_image_offset);
      break;
    }
    case HLoadClass::LoadKind::kBssEntry:
    case HLoadClass::LoadKind::kBssEntryPublic:
    case HLoadClass::LoadKind::kBssEntryPackage: {
      CodeGeneratorRISCV64::PcRelativePatchInfo* bss_info_high =
          codegen_->NewTypeBssEntryPatch(instruction);
      codegen_->EmitPcRelativeAuipcPlaceholder(bss_info_high, out);
      CodeGeneratorRISCV64::PcRelativePatchInfo* info_low = codegen_->NewTypeBssEntryPatch(
          instruction, bss_info_high);
      codegen_->GenerateGcRootFieldLoad(instruction,
                                        out_loc,
                                        out,
                                        /* offset= */ kLinkTimeOffsetPlaceholderLow,
                                        read_barrier_option,
                                        &info_low->label);
      generate_null_check = true;
      break;
    }
    case HLoadClass::LoadKind::kJitBootImageAddress: {
      DCHECK_EQ(read_barrier_option, kWithoutReadBarrier);
      uint32_t address = reinterpret_cast32<uint32_t>(instruction->GetClass().Get());
      DCHECK_NE(address, 0u);
      __ Loadwu(out, codegen_->DeduplicateBootImageAddressLiteral(address));
      break;
    }
    case HLoadClass::LoadKind::kJitTableAddress:
      __ Loadwu(out, codegen_->DeduplicateJitClassLiteral(instruction->GetDexFile(),
                                                          instruction->GetTypeIndex(),
                                                          instruction->GetClass()));
      codegen_->GenerateGcRootFieldLoad(
          instruction, out_loc, out, /* offset= */ 0, read_barrier_option);
      break;
    case HLoadClass::LoadKind::kRuntimeCall:
    case HLoadClass::LoadKind::kInvalid:
      LOG(FATAL) << "UNREACHABLE";
      UNREACHABLE();
  }

  if (generate_null_check || instruction->MustGenerateClinitCheck()) {
    DCHECK(instruction->CanCallRuntime());
    SlowPathCodeRISCV64* slow_path =
        new (codegen_->GetScopedAllocator()) LoadClassSlowPathRISCV64(instruction, instruction);
    codegen_->AddSlowPath(slow_path);
    if (generate_null_check) {
      __ Beqz(out, slow_path->GetEntryLabel());
    }
    if (instruction->MustGenerateClinitCheck()) {
      GenerateClassInitializationCheck(slow_path, out);
    } else {
      __ Bind(slow_path->GetExitLabel());
    }
  }
}

void LocationsBuilderRISCV64::VisitLoadException(HLoadException* instruction) {
  LocationSummary* locations =
      new (GetGraph()->GetAllocator()) LocationSummary(instruction, LocationSummary::kNoCall);
  locations->SetOut(Location::RequiresRegister());
}

void InstructionCodeGeneratorRISCV64::VisitLoadException(HLoadException* instruction) {
  XRegister out = instruction->GetLocations()->Out().AsRegister<XRegister>();
  __ Loadwu(out, TR, GetExceptionTlsOffset());
}

void LocationsBuilderRISCV64::VisitLoadMethodHandle(HLoadMethodHandle* instruction) {
  InvokeRuntimeCallingConvention calling_convention;
  Location loc = Location::RegisterLocation(calling_convention.GetRegisterAt(0));
  CodeGenerator::CreateLoadMethodHandleRuntimeCallLocationSummary(instruction, loc, loc);
}

void InstructionCodeGeneratorRISCV64::VisitLoadMethodHandle(HLoadMethodHandle* instruction) {
  codegen_->GenerateLoadMethodHandleRuntimeCall(instruction);
}

void LocationsBuilderRISCV64::VisitLoadMethodType(HLoadMethodType* instruction) {
  InvokeRuntimeCallingConvention calling_convention;
  Location loc = Location::RegisterLocation(calling_convention.GetRegisterAt(0));
  CodeGenerator::CreateLoadMethodTypeRuntimeCallLocationSummary(instruction, loc, loc);
}

void InstructionCodeGeneratorRISCV64::VisitLoadMethodType(HLoadMethodType* instruction) {
  codegen_->GenerateLoadMethodTypeRuntimeCall(instruction);
}

void LocationsBuilderRISCV64::VisitLoadString(HLoadString* instruction) {
  HLoadString::LoadKind load_kind = instruction->GetLoadKind();
  LocationSummary::CallKind call_kind = codegen_->GetLoadStringCallKind(instruction);
  LocationSummary* locations =
      new (GetGraph()->GetAllocator()) LocationSummary(instruction, call_kind);
  if (load_kind == HLoadString::LoadKind::kRuntimeCall) {
    InvokeRuntimeCallingConvention calling_convention;
    DCHECK_EQ(DataType::Type::kReference, instruction->GetType());
    locations->SetOut(calling_convention.GetReturnLocation(DataType::Type::kReference));
  } else {
    locations->SetOut(Location::RequiresRegister());
    if (load_kind == HLoadString::LoadKind::kBssEntry) {
      if (codegen_->EmitNonBakerReadBarrier()) {
        // For non-Baker read barriers we have a temp-clobbering call.
      } else {
        // Rely on the pResolveString and marking to save everything we need.
        locations->SetCustomSlowPathCallerSaves(OneRegInReferenceOutSaveEverythingCallerSaves());
      }
    }
  }
}

// NO_THREAD_SAFETY_ANALYSIS as we manipulate handles whose internal object we know does not
// move.
void InstructionCodeGeneratorRISCV64::VisitLoadString(HLoadString* instruction)
    NO_THREAD_SAFETY_ANALYSIS {
  HLoadString::LoadKind load_kind = instruction->GetLoadKind();
  LocationSummary* locations = instruction->GetLocations();
  Location out_loc = locations->Out();
  XRegister out = out_loc.AsRegister<XRegister>();

  switch (load_kind) {
    case HLoadString::LoadKind::kBootImageLinkTimePcRelative: {
      DCHECK(codegen_->GetCompilerOptions().IsBootImage() ||
             codegen_->GetCompilerOptions().IsBootImageExtension());
      CodeGeneratorRISCV64::PcRelativePatchInfo* info_high = codegen_->NewBootImageStringPatch(
          instruction->GetDexFile(), instruction->GetStringIndex());
      codegen_->EmitPcRelativeAuipcPlaceholder(info_high, out);
      CodeGeneratorRISCV64::PcRelativePatchInfo* info_low = codegen_->NewBootImageStringPatch(
          instruction->GetDexFile(), instruction->GetStringIndex(), info_high);
      codegen_->EmitPcRelativeAddiPlaceholder(info_low, out, out);
      return;
    }
    case HLoadString::LoadKind::kBootImageRelRo: {
      DCHECK(!codegen_->GetCompilerOptions().IsBootImage());
      uint32_t boot_image_offset = codegen_->GetBootImageOffset(instruction);
      codegen_->LoadBootImageRelRoEntry(out, boot_image_offset);
      return;
    }
    case HLoadString::LoadKind::kBssEntry: {
      CodeGeneratorRISCV64::PcRelativePatchInfo* info_high = codegen_->NewStringBssEntryPatch(
          instruction->GetDexFile(), instruction->GetStringIndex());
      codegen_->EmitPcRelativeAuipcPlaceholder(info_high, out);
      CodeGeneratorRISCV64::PcRelativePatchInfo* info_low = codegen_->NewStringBssEntryPatch(
          instruction->GetDexFile(), instruction->GetStringIndex(), info_high);
      codegen_->GenerateGcRootFieldLoad(instruction,
                                        out_loc,
                                        out,
                                        /* offset= */ kLinkTimeOffsetPlaceholderLow,
                                        codegen_->GetCompilerReadBarrierOption(),
                                        &info_low->label);
      SlowPathCodeRISCV64* slow_path =
          new (codegen_->GetScopedAllocator()) LoadStringSlowPathRISCV64(instruction);
      codegen_->AddSlowPath(slow_path);
      __ Beqz(out, slow_path->GetEntryLabel());
      __ Bind(slow_path->GetExitLabel());
      return;
    }
    case HLoadString::LoadKind::kJitBootImageAddress: {
      uint32_t address = reinterpret_cast32<uint32_t>(instruction->GetString().Get());
      DCHECK_NE(address, 0u);
      __ Loadwu(out, codegen_->DeduplicateBootImageAddressLiteral(address));
      return;
    }
    case HLoadString::LoadKind::kJitTableAddress:
      __ Loadwu(
          out,
          codegen_->DeduplicateJitStringLiteral(
              instruction->GetDexFile(), instruction->GetStringIndex(), instruction->GetString()));
      codegen_->GenerateGcRootFieldLoad(
          instruction, out_loc, out, 0, codegen_->GetCompilerReadBarrierOption());
      return;
    default:
      break;
  }

  DCHECK(load_kind == HLoadString::LoadKind::kRuntimeCall);
  InvokeRuntimeCallingConvention calling_convention;
  DCHECK(calling_convention.GetReturnLocation(DataType::Type::kReference).Equals(out_loc));
  __ LoadConst32(calling_convention.GetRegisterAt(0), instruction->GetStringIndex().index_);
  codegen_->InvokeRuntime(kQuickResolveString, instruction, instruction->GetDexPc());
  CheckEntrypointTypes<kQuickResolveString, void*, uint32_t>();
}

void LocationsBuilderRISCV64::VisitLongConstant(HLongConstant* instruction) {
  LocationSummary* locations = new (GetGraph()->GetAllocator()) LocationSummary(instruction);
  locations->SetOut(Location::ConstantLocation(instruction));
}

void InstructionCodeGeneratorRISCV64::VisitLongConstant(
    [[maybe_unused]] HLongConstant* instruction) {
  // Will be generated at use site.
}

void LocationsBuilderRISCV64::VisitMax(HMax* instruction) {
  HandleBinaryOp(instruction);
}

void InstructionCodeGeneratorRISCV64::VisitMax(HMax* instruction) {
  HandleBinaryOp(instruction);
}

void LocationsBuilderRISCV64::VisitMemoryBarrier(HMemoryBarrier* instruction) {
  instruction->SetLocations(nullptr);
}

void InstructionCodeGeneratorRISCV64::VisitMemoryBarrier(HMemoryBarrier* instruction) {
  codegen_->GenerateMemoryBarrier(instruction->GetBarrierKind());
}

void LocationsBuilderRISCV64::VisitMethodEntryHook(HMethodEntryHook* instruction) {
  new (GetGraph()->GetAllocator()) LocationSummary(instruction, LocationSummary::kCallOnSlowPath);
}

void InstructionCodeGeneratorRISCV64::VisitMethodEntryHook(HMethodEntryHook* instruction) {
  DCHECK(codegen_->GetCompilerOptions().IsJitCompiler() && GetGraph()->IsDebuggable());
  DCHECK(codegen_->RequiresCurrentMethod());
  GenerateMethodEntryExitHook(instruction);
}

void LocationsBuilderRISCV64::VisitMethodExitHook(HMethodExitHook* instruction) {
  LocationSummary* locations = new (GetGraph()->GetAllocator())
      LocationSummary(instruction, LocationSummary::kCallOnSlowPath);
  DataType::Type return_type = instruction->InputAt(0)->GetType();
  locations->SetInAt(0, Riscv64ReturnLocation(return_type));
}

void InstructionCodeGeneratorRISCV64::VisitMethodExitHook(HMethodExitHook* instruction) {
  DCHECK(codegen_->GetCompilerOptions().IsJitCompiler() && GetGraph()->IsDebuggable());
  DCHECK(codegen_->RequiresCurrentMethod());
  GenerateMethodEntryExitHook(instruction);
}

void LocationsBuilderRISCV64::VisitMin(HMin* instruction) {
  HandleBinaryOp(instruction);
}

void InstructionCodeGeneratorRISCV64::VisitMin(HMin* instruction) {
  HandleBinaryOp(instruction);
}

void LocationsBuilderRISCV64::VisitMonitorOperation(HMonitorOperation* instruction) {
  LocationSummary* locations = new (GetGraph()->GetAllocator()) LocationSummary(
      instruction, LocationSummary::kCallOnMainOnly);
  InvokeRuntimeCallingConvention calling_convention;
  locations->SetInAt(0, Location::RegisterLocation(calling_convention.GetRegisterAt(0)));
}

void InstructionCodeGeneratorRISCV64::VisitMonitorOperation(HMonitorOperation* instruction) {
  codegen_->InvokeRuntime(instruction->IsEnter() ? kQuickLockObject : kQuickUnlockObject,
                          instruction,
                          instruction->GetDexPc());
  if (instruction->IsEnter()) {
    CheckEntrypointTypes<kQuickLockObject, void, mirror::Object*>();
  } else {
    CheckEntrypointTypes<kQuickUnlockObject, void, mirror::Object*>();
  }
}

void LocationsBuilderRISCV64::VisitMul(HMul* instruction) {
  LocationSummary* locations =
      new (GetGraph()->GetAllocator()) LocationSummary(instruction, LocationSummary::kNoCall);
  switch (instruction->GetResultType()) {
    case DataType::Type::kInt32:
    case DataType::Type::kInt64:
      locations->SetInAt(0, Location::RequiresRegister());
      locations->SetInAt(1, Location::RequiresRegister());
      locations->SetOut(Location::RequiresRegister(), Location::kNoOutputOverlap);
      break;

    case DataType::Type::kFloat32:
    case DataType::Type::kFloat64:
      locations->SetInAt(0, Location::RequiresFpuRegister());
      locations->SetInAt(1, Location::RequiresFpuRegister());
      locations->SetOut(Location::RequiresFpuRegister(), Location::kNoOutputOverlap);
      break;

    default:
      LOG(FATAL) << "Unexpected mul type " << instruction->GetResultType();
  }
}

void InstructionCodeGeneratorRISCV64::VisitMul(HMul* instruction) {
  LocationSummary* locations = instruction->GetLocations();
  switch (instruction->GetResultType()) {
    case DataType::Type::kInt32:
      __ Mulw(locations->Out().AsRegister<XRegister>(),
              locations->InAt(0).AsRegister<XRegister>(),
              locations->InAt(1).AsRegister<XRegister>());
      break;

    case DataType::Type::kInt64:
      __ Mul(locations->Out().AsRegister<XRegister>(),
             locations->InAt(0).AsRegister<XRegister>(),
             locations->InAt(1).AsRegister<XRegister>());
      break;

    case DataType::Type::kFloat32:
    case DataType::Type::kFloat64:
      FMul(locations->Out().AsFpuRegister<FRegister>(),
           locations->InAt(0).AsFpuRegister<FRegister>(),
           locations->InAt(1).AsFpuRegister<FRegister>(),
           instruction->GetResultType());
      break;

    default:
      LOG(FATAL) << "Unexpected mul type " << instruction->GetResultType();
  }
}

void LocationsBuilderRISCV64::VisitNeg(HNeg* instruction) {
  LocationSummary* locations = new (GetGraph()->GetAllocator()) LocationSummary(instruction);
  switch (instruction->GetResultType()) {
    case DataType::Type::kInt32:
    case DataType::Type::kInt64:
      locations->SetInAt(0, Location::RequiresRegister());
      locations->SetOut(Location::RequiresRegister(), Location::kNoOutputOverlap);
      break;

    case DataType::Type::kFloat32:
    case DataType::Type::kFloat64:
      locations->SetInAt(0, Location::RequiresFpuRegister());
      locations->SetOut(Location::RequiresFpuRegister(), Location::kNoOutputOverlap);
      break;

    default:
      LOG(FATAL) << "Unexpected neg type " << instruction->GetResultType();
      UNREACHABLE();
  }
}

void InstructionCodeGeneratorRISCV64::VisitNeg(HNeg* instruction) {
  LocationSummary* locations = instruction->GetLocations();
  switch (instruction->GetResultType()) {
    case DataType::Type::kInt32:
      __ NegW(locations->Out().AsRegister<XRegister>(), locations->InAt(0).AsRegister<XRegister>());
      break;

    case DataType::Type::kInt64:
      __ Neg(locations->Out().AsRegister<XRegister>(), locations->InAt(0).AsRegister<XRegister>());
      break;

    case DataType::Type::kFloat32:
    case DataType::Type::kFloat64:
      FNeg(locations->Out().AsFpuRegister<FRegister>(),
           locations->InAt(0).AsFpuRegister<FRegister>(),
           instruction->GetResultType());
      break;

    default:
      LOG(FATAL) << "Unexpected neg type " << instruction->GetResultType();
      UNREACHABLE();
  }
}

void LocationsBuilderRISCV64::VisitNewArray(HNewArray* instruction) {
  LocationSummary* locations = new (GetGraph()->GetAllocator())
      LocationSummary(instruction, LocationSummary::kCallOnMainOnly);
  InvokeRuntimeCallingConvention calling_convention;
  locations->SetOut(calling_convention.GetReturnLocation(DataType::Type::kReference));
  locations->SetInAt(0, Location::RegisterLocation(calling_convention.GetRegisterAt(0)));
  locations->SetInAt(1, Location::RegisterLocation(calling_convention.GetRegisterAt(1)));
}

void InstructionCodeGeneratorRISCV64::VisitNewArray(HNewArray* instruction) {
  QuickEntrypointEnum entrypoint = CodeGenerator::GetArrayAllocationEntrypoint(instruction);
  codegen_->InvokeRuntime(entrypoint, instruction, instruction->GetDexPc());
  CheckEntrypointTypes<kQuickAllocArrayResolved, void*, mirror::Class*, int32_t>();
  DCHECK(!codegen_->IsLeafMethod());
}

void LocationsBuilderRISCV64::VisitNewInstance(HNewInstance* instruction) {
  LocationSummary* locations = new (GetGraph()->GetAllocator()) LocationSummary(
      instruction, LocationSummary::kCallOnMainOnly);
  InvokeRuntimeCallingConvention calling_convention;
  locations->SetInAt(0, Location::RegisterLocation(calling_convention.GetRegisterAt(0)));
  locations->SetOut(calling_convention.GetReturnLocation(DataType::Type::kReference));
}

void InstructionCodeGeneratorRISCV64::VisitNewInstance(HNewInstance* instruction) {
  codegen_->InvokeRuntime(instruction->GetEntrypoint(), instruction, instruction->GetDexPc());
  CheckEntrypointTypes<kQuickAllocObjectWithChecks, void*, mirror::Class*>();
}

void LocationsBuilderRISCV64::VisitNop(HNop* instruction) {
  new (GetGraph()->GetAllocator()) LocationSummary(instruction);
}

void InstructionCodeGeneratorRISCV64::VisitNop([[maybe_unused]] HNop* instruction) {
  // The environment recording already happened in CodeGenerator::Compile.
}

void LocationsBuilderRISCV64::VisitNot(HNot* instruction) {
  LocationSummary* locations = new (GetGraph()->GetAllocator()) LocationSummary(instruction);
  locations->SetInAt(0, Location::RequiresRegister());
  locations->SetOut(Location::RequiresRegister(), Location::kNoOutputOverlap);
}

void InstructionCodeGeneratorRISCV64::VisitNot(HNot* instruction) {
  LocationSummary* locations = instruction->GetLocations();
  switch (instruction->GetResultType()) {
    case DataType::Type::kInt32:
    case DataType::Type::kInt64:
      __ Not(locations->Out().AsRegister<XRegister>(), locations->InAt(0).AsRegister<XRegister>());
      break;

    default:
      LOG(FATAL) << "Unexpected type for not operation " << instruction->GetResultType();
      UNREACHABLE();
  }
}

void LocationsBuilderRISCV64::VisitNotEqual(HNotEqual* instruction) {
  HandleCondition(instruction);
}

void InstructionCodeGeneratorRISCV64::VisitNotEqual(HNotEqual* instruction) {
  HandleCondition(instruction);
}

void LocationsBuilderRISCV64::VisitNullConstant(HNullConstant* instruction) {
  LocationSummary* locations = new (GetGraph()->GetAllocator()) LocationSummary(instruction);
  locations->SetOut(Location::ConstantLocation(instruction));
}

void InstructionCodeGeneratorRISCV64::VisitNullConstant(
    [[maybe_unused]] HNullConstant* instruction) {
  // Will be generated at use site.
}

void LocationsBuilderRISCV64::VisitNullCheck(HNullCheck* instruction) {
  LocationSummary* locations = codegen_->CreateThrowingSlowPathLocations(instruction);
  locations->SetInAt(0, Location::RequiresRegister());
}

void InstructionCodeGeneratorRISCV64::VisitNullCheck(HNullCheck* instruction) {
  codegen_->GenerateNullCheck(instruction);
}

void LocationsBuilderRISCV64::VisitOr(HOr* instruction) {
  HandleBinaryOp(instruction);
}

void InstructionCodeGeneratorRISCV64::VisitOr(HOr* instruction) {
  HandleBinaryOp(instruction);
}

void LocationsBuilderRISCV64::VisitPackedSwitch(HPackedSwitch* instruction) {
  LocationSummary* locations =
      new (GetGraph()->GetAllocator()) LocationSummary(instruction, LocationSummary::kNoCall);
  locations->SetInAt(0, Location::RequiresRegister());
}

void InstructionCodeGeneratorRISCV64::VisitPackedSwitch(HPackedSwitch* instruction) {
  int32_t lower_bound = instruction->GetStartValue();
  uint32_t num_entries = instruction->GetNumEntries();
  LocationSummary* locations = instruction->GetLocations();
  XRegister value = locations->InAt(0).AsRegister<XRegister>();
  HBasicBlock* switch_block = instruction->GetBlock();
  HBasicBlock* default_block = instruction->GetDefaultBlock();

  // Prepare a temporary register and an adjusted zero-based value.
  ScratchRegisterScope srs(GetAssembler());
  XRegister temp = srs.AllocateXRegister();
  XRegister adjusted = value;
  if (lower_bound != 0) {
    adjusted = temp;
    __ AddConst32(temp, value, -lower_bound);
  }

  // Jump to the default block if the index is out of the packed switch value range.
  // Note: We could save one instruction for `num_entries == 1` with BNEZ but the
  // `HInstructionBuilder` transforms that case to an `HIf`, so let's keep the code simple.
  CHECK_NE(num_entries, 0u);  // `HInstructionBuilder` creates a `HGoto` for empty packed-switch.
  {
    ScratchRegisterScope srs2(GetAssembler());
    XRegister temp2 = srs2.AllocateXRegister();
    __ LoadConst32(temp2, num_entries);
    __ Bgeu(adjusted, temp2, codegen_->GetLabelOf(default_block));  // Can clobber `TMP` if taken.
  }

  if (num_entries >= kPackedSwitchCompareJumpThreshold) {
    GenTableBasedPackedSwitch(adjusted, temp, num_entries, switch_block);
  } else {
    GenPackedSwitchWithCompares(adjusted, temp, num_entries, switch_block);
  }
}

void LocationsBuilderRISCV64::VisitParallelMove([[maybe_unused]] HParallelMove* instruction) {
  LOG(FATAL) << "Unreachable";
}

void InstructionCodeGeneratorRISCV64::VisitParallelMove(HParallelMove* instruction) {
  if (instruction->GetNext()->IsSuspendCheck() &&
      instruction->GetBlock()->GetLoopInformation() != nullptr) {
    HSuspendCheck* suspend_check = instruction->GetNext()->AsSuspendCheck();
    // The back edge will generate the suspend check.
    codegen_->ClearSpillSlotsFromLoopPhisInStackMap(suspend_check, instruction);
  }

  codegen_->GetMoveResolver()->EmitNativeCode(instruction);
}

void LocationsBuilderRISCV64::VisitParameterValue(HParameterValue* instruction) {
  LocationSummary* locations = new (GetGraph()->GetAllocator()) LocationSummary(instruction);
  Location location = parameter_visitor_.GetNextLocation(instruction->GetType());
  if (location.IsStackSlot()) {
    location = Location::StackSlot(location.GetStackIndex() + codegen_->GetFrameSize());
  } else if (location.IsDoubleStackSlot()) {
    location = Location::DoubleStackSlot(location.GetStackIndex() + codegen_->GetFrameSize());
  }
  locations->SetOut(location);
}

void InstructionCodeGeneratorRISCV64::VisitParameterValue(
    [[maybe_unused]] HParameterValue* instruction) {
  // Nothing to do, the parameter is already at its location.
}

void LocationsBuilderRISCV64::VisitPhi(HPhi* instruction) {
  LocationSummary* locations = new (GetGraph()->GetAllocator()) LocationSummary(instruction);
  for (size_t i = 0, e = locations->GetInputCount(); i < e; ++i) {
    locations->SetInAt(i, Location::Any());
  }
  locations->SetOut(Location::Any());
}

void InstructionCodeGeneratorRISCV64::VisitPhi([[maybe_unused]] HPhi* instruction) {
  LOG(FATAL) << "Unreachable";
}

void LocationsBuilderRISCV64::VisitRem(HRem* instruction) {
  DataType::Type type = instruction->GetResultType();
  LocationSummary::CallKind call_kind =
      DataType::IsFloatingPointType(type) ? LocationSummary::kCallOnMainOnly
                                          : LocationSummary::kNoCall;
  LocationSummary* locations =
      new (GetGraph()->GetAllocator()) LocationSummary(instruction, call_kind);

  switch (type) {
    case DataType::Type::kInt32:
    case DataType::Type::kInt64:
      locations->SetInAt(0, Location::RequiresRegister());
      locations->SetInAt(1, Location::RegisterOrConstant(instruction->InputAt(1)));
      locations->SetOut(Location::RequiresRegister(), Location::kNoOutputOverlap);
      break;

    case DataType::Type::kFloat32:
    case DataType::Type::kFloat64: {
      InvokeRuntimeCallingConvention calling_convention;
      locations->SetInAt(0, Location::FpuRegisterLocation(calling_convention.GetFpuRegisterAt(0)));
      locations->SetInAt(1, Location::FpuRegisterLocation(calling_convention.GetFpuRegisterAt(1)));
      locations->SetOut(calling_convention.GetReturnLocation(type));
      break;
    }

    default:
      LOG(FATAL) << "Unexpected rem type " << type;
      UNREACHABLE();
  }
}

void InstructionCodeGeneratorRISCV64::VisitRem(HRem* instruction) {
  DataType::Type type = instruction->GetType();

  switch (type) {
    case DataType::Type::kInt32:
    case DataType::Type::kInt64:
      GenerateDivRemIntegral(instruction);
      break;

    case DataType::Type::kFloat32:
    case DataType::Type::kFloat64: {
      QuickEntrypointEnum entrypoint =
          (type == DataType::Type::kFloat32) ? kQuickFmodf : kQuickFmod;
      codegen_->InvokeRuntime(entrypoint, instruction, instruction->GetDexPc());
      if (type == DataType::Type::kFloat32) {
        CheckEntrypointTypes<kQuickFmodf, float, float, float>();
      } else {
        CheckEntrypointTypes<kQuickFmod, double, double, double>();
      }
      break;
    }
    default:
      LOG(FATAL) << "Unexpected rem type " << type;
      UNREACHABLE();
  }
}

void LocationsBuilderRISCV64::VisitReturn(HReturn* instruction) {
  LocationSummary* locations = new (GetGraph()->GetAllocator()) LocationSummary(instruction);
  DataType::Type return_type = instruction->InputAt(0)->GetType();
  DCHECK_NE(return_type, DataType::Type::kVoid);
  locations->SetInAt(0, Riscv64ReturnLocation(return_type));
}

void InstructionCodeGeneratorRISCV64::VisitReturn(HReturn* instruction) {
  if (GetGraph()->IsCompilingOsr()) {
    // To simplify callers of an OSR method, we put a floating point return value
    // in both floating point and core return registers.
    DataType::Type type = instruction->InputAt(0)->GetType();
    if (DataType::IsFloatingPointType(type)) {
      FMvX(A0, FA0, type);
    }
  }
  codegen_->GenerateFrameExit();
}

void LocationsBuilderRISCV64::VisitReturnVoid(HReturnVoid* instruction) {
  instruction->SetLocations(nullptr);
}

void InstructionCodeGeneratorRISCV64::VisitReturnVoid([[maybe_unused]] HReturnVoid* instruction) {
  codegen_->GenerateFrameExit();
}

void LocationsBuilderRISCV64::VisitRor(HRor* instruction) {
  HandleShift(instruction);
}

void InstructionCodeGeneratorRISCV64::VisitRor(HRor* instruction) {
  HandleShift(instruction);
}

void LocationsBuilderRISCV64::VisitShl(HShl* instruction) {
  HandleShift(instruction);
}

void InstructionCodeGeneratorRISCV64::VisitShl(HShl* instruction) {
  HandleShift(instruction);
}

void LocationsBuilderRISCV64::VisitShr(HShr* instruction) {
  HandleShift(instruction);
}

void InstructionCodeGeneratorRISCV64::VisitShr(HShr* instruction) {
  HandleShift(instruction);
}

void LocationsBuilderRISCV64::VisitStaticFieldGet(HStaticFieldGet* instruction) {
  HandleFieldGet(instruction);
}

void InstructionCodeGeneratorRISCV64::VisitStaticFieldGet(HStaticFieldGet* instruction) {
  HandleFieldGet(instruction, instruction->GetFieldInfo());
}

void LocationsBuilderRISCV64::VisitStaticFieldSet(HStaticFieldSet* instruction) {
  HandleFieldSet(instruction);
}

void InstructionCodeGeneratorRISCV64::VisitStaticFieldSet(HStaticFieldSet* instruction) {
  HandleFieldSet(instruction,
                 instruction->GetFieldInfo(),
                 instruction->GetValueCanBeNull(),
                 instruction->GetWriteBarrierKind());
}

void LocationsBuilderRISCV64::VisitStringBuilderAppend(HStringBuilderAppend* instruction) {
  codegen_->CreateStringBuilderAppendLocations(instruction, Location::RegisterLocation(A0));
}

void InstructionCodeGeneratorRISCV64::VisitStringBuilderAppend(HStringBuilderAppend* instruction) {
  __ LoadConst32(A0, instruction->GetFormat()->GetValue());
  codegen_->InvokeRuntime(kQuickStringBuilderAppend, instruction, instruction->GetDexPc());
}

void LocationsBuilderRISCV64::VisitUnresolvedInstanceFieldGet(
    HUnresolvedInstanceFieldGet* instruction) {
  FieldAccessCallingConventionRISCV64 calling_convention;
  codegen_->CreateUnresolvedFieldLocationSummary(
      instruction, instruction->GetFieldType(), calling_convention);
}

void InstructionCodeGeneratorRISCV64::VisitUnresolvedInstanceFieldGet(
    HUnresolvedInstanceFieldGet* instruction) {
  FieldAccessCallingConventionRISCV64 calling_convention;
  codegen_->GenerateUnresolvedFieldAccess(instruction,
                                          instruction->GetFieldType(),
                                          instruction->GetFieldIndex(),
                                          instruction->GetDexPc(),
                                          calling_convention);
}

void LocationsBuilderRISCV64::VisitUnresolvedInstanceFieldSet(
    HUnresolvedInstanceFieldSet* instruction) {
  FieldAccessCallingConventionRISCV64 calling_convention;
  codegen_->CreateUnresolvedFieldLocationSummary(
      instruction, instruction->GetFieldType(), calling_convention);
}

void InstructionCodeGeneratorRISCV64::VisitUnresolvedInstanceFieldSet(
    HUnresolvedInstanceFieldSet* instruction) {
  FieldAccessCallingConventionRISCV64 calling_convention;
  codegen_->GenerateUnresolvedFieldAccess(instruction,
                                          instruction->GetFieldType(),
                                          instruction->GetFieldIndex(),
                                          instruction->GetDexPc(),
                                          calling_convention);
}

void LocationsBuilderRISCV64::VisitUnresolvedStaticFieldGet(
    HUnresolvedStaticFieldGet* instruction) {
  FieldAccessCallingConventionRISCV64 calling_convention;
  codegen_->CreateUnresolvedFieldLocationSummary(
      instruction, instruction->GetFieldType(), calling_convention);
}

void InstructionCodeGeneratorRISCV64::VisitUnresolvedStaticFieldGet(
    HUnresolvedStaticFieldGet* instruction) {
  FieldAccessCallingConventionRISCV64 calling_convention;
  codegen_->GenerateUnresolvedFieldAccess(instruction,
                                          instruction->GetFieldType(),
                                          instruction->GetFieldIndex(),
                                          instruction->GetDexPc(),
                                          calling_convention);
}

void LocationsBuilderRISCV64::VisitUnresolvedStaticFieldSet(
    HUnresolvedStaticFieldSet* instruction) {
  FieldAccessCallingConventionRISCV64 calling_convention;
  codegen_->CreateUnresolvedFieldLocationSummary(
      instruction, instruction->GetFieldType(), calling_convention);
}

void InstructionCodeGeneratorRISCV64::VisitUnresolvedStaticFieldSet(
    HUnresolvedStaticFieldSet* instruction) {
  FieldAccessCallingConventionRISCV64 calling_convention;
  codegen_->GenerateUnresolvedFieldAccess(instruction,
                                          instruction->GetFieldType(),
                                          instruction->GetFieldIndex(),
                                          instruction->GetDexPc(),
                                          calling_convention);
}

void LocationsBuilderRISCV64::VisitSelect(HSelect* instruction) {
  LocationSummary* locations = new (GetGraph()->GetAllocator()) LocationSummary(instruction);
  if (DataType::IsFloatingPointType(instruction->GetType())) {
    locations->SetInAt(0, FpuRegisterOrZeroBitPatternLocation(instruction->GetFalseValue()));
    locations->SetInAt(1, FpuRegisterOrZeroBitPatternLocation(instruction->GetTrueValue()));
    locations->SetOut(Location::RequiresFpuRegister(), Location::kNoOutputOverlap);
    if (!locations->InAt(0).IsConstant() && !locations->InAt(1).IsConstant()) {
      locations->AddTemp(Location::RequiresRegister());
    }
  }  else {
    locations->SetInAt(0, RegisterOrZeroBitPatternLocation(instruction->GetFalseValue()));
    locations->SetInAt(1, RegisterOrZeroBitPatternLocation(instruction->GetTrueValue()));
    locations->SetOut(Location::RequiresRegister(), Location::kOutputOverlap);
  }

  if (IsBooleanValueOrMaterializedCondition(instruction->GetCondition())) {
    locations->SetInAt(2, Location::RequiresRegister());
  }
}

void InstructionCodeGeneratorRISCV64::VisitSelect(HSelect* instruction) {
  LocationSummary* locations = instruction->GetLocations();
  HInstruction* cond = instruction->GetCondition();
  ScratchRegisterScope srs(GetAssembler());
  XRegister tmp = srs.AllocateXRegister();
  if (!IsBooleanValueOrMaterializedCondition(cond)) {
    DataType::Type cond_type = cond->InputAt(0)->GetType();
    IfCondition if_cond = cond->AsCondition()->GetCondition();
    if (DataType::IsFloatingPointType(cond_type)) {
      GenerateFpCondition(if_cond,
                          cond->AsCondition()->IsGtBias(),
                          cond_type,
                          cond->GetLocations(),
                          /*label=*/ nullptr,
                          tmp,
                          /*to_all_bits=*/ true);
    } else {
      GenerateIntLongCondition(if_cond, cond->GetLocations(), tmp, /*to_all_bits=*/ true);
    }
  } else {
    // TODO(riscv64): Remove the normalizing SNEZ when we can ensure that booleans
    // have only values 0 and 1. b/279302742
    __ Snez(tmp, locations->InAt(2).AsRegister<XRegister>());
    __ Neg(tmp, tmp);
  }

  XRegister true_reg, false_reg, xor_reg, out_reg;
  DataType::Type type = instruction->GetType();
  if (DataType::IsFloatingPointType(type)) {
    if (locations->InAt(0).IsConstant()) {
      DCHECK(locations->InAt(0).GetConstant()->IsZeroBitPattern());
      false_reg = Zero;
    } else {
      false_reg = srs.AllocateXRegister();
      FMvX(false_reg, locations->InAt(0).AsFpuRegister<FRegister>(), type);
    }
    if (locations->InAt(1).IsConstant()) {
      DCHECK(locations->InAt(1).GetConstant()->IsZeroBitPattern());
      true_reg = Zero;
    } else {
      true_reg = (false_reg == Zero) ? srs.AllocateXRegister()
                                     : locations->GetTemp(0).AsRegister<XRegister>();
      FMvX(true_reg, locations->InAt(1).AsFpuRegister<FRegister>(), type);
    }
    // We can clobber the "true value" with the XOR result.
    // Note: The XOR is not emitted if `true_reg == Zero`, see below.
    xor_reg = true_reg;
    out_reg = tmp;
  } else {
    false_reg = InputXRegisterOrZero(locations->InAt(0));
    true_reg = InputXRegisterOrZero(locations->InAt(1));
    xor_reg = srs.AllocateXRegister();
    out_reg = locations->Out().AsRegister<XRegister>();
  }

  // We use a branch-free implementation of `HSelect`.
  // With `tmp` initialized to 0 for `false` and -1 for `true`:
  //     xor xor_reg, false_reg, true_reg
  //     and tmp, tmp, xor_reg
  //     xor out_reg, tmp, false_reg
  if (false_reg == Zero) {
    xor_reg = true_reg;
  } else if (true_reg == Zero) {
    xor_reg = false_reg;
  } else {
    DCHECK_NE(xor_reg, Zero);
    __ Xor(xor_reg, false_reg, true_reg);
  }
  __ And(tmp, tmp, xor_reg);
  __ Xor(out_reg, tmp, false_reg);

  if (type == DataType::Type::kFloat64) {
    __ FMvDX(locations->Out().AsFpuRegister<FRegister>(), out_reg);
  } else if (type == DataType::Type::kFloat32) {
    __ FMvWX(locations->Out().AsFpuRegister<FRegister>(), out_reg);
  }
}

void LocationsBuilderRISCV64::VisitSub(HSub* instruction) {
  HandleBinaryOp(instruction);
}

void InstructionCodeGeneratorRISCV64::VisitSub(HSub* instruction) {
  HandleBinaryOp(instruction);
}

void LocationsBuilderRISCV64::VisitSuspendCheck(HSuspendCheck* instruction) {
  LocationSummary* locations = new (GetGraph()->GetAllocator())
      LocationSummary(instruction, LocationSummary::kCallOnSlowPath);
  // In suspend check slow path, usually there are no caller-save registers at all.
  // If SIMD instructions are present, however, we force spilling all live SIMD
  // registers in full width (since the runtime only saves/restores lower part).
  locations->SetCustomSlowPathCallerSaves(GetGraph()->HasSIMD() ? RegisterSet::AllFpu() :
                                                                  RegisterSet::Empty());
}

void InstructionCodeGeneratorRISCV64::VisitSuspendCheck(HSuspendCheck* instruction) {
  HBasicBlock* block = instruction->GetBlock();
  if (block->GetLoopInformation() != nullptr) {
    DCHECK(block->GetLoopInformation()->GetSuspendCheck() == instruction);
    // The back edge will generate the suspend check.
    return;
  }
  if (block->IsEntryBlock() && instruction->GetNext()->IsGoto()) {
    // The goto will generate the suspend check.
    return;
  }
  GenerateSuspendCheck(instruction, nullptr);
}

void LocationsBuilderRISCV64::VisitThrow(HThrow* instruction) {
  LocationSummary* locations = new (GetGraph()->GetAllocator())
      LocationSummary(instruction, LocationSummary::kCallOnMainOnly);
  InvokeRuntimeCallingConvention calling_convention;
  locations->SetInAt(0, Location::RegisterLocation(calling_convention.GetRegisterAt(0)));
}

void InstructionCodeGeneratorRISCV64::VisitThrow(HThrow* instruction) {
  codegen_->InvokeRuntime(kQuickDeliverException, instruction, instruction->GetDexPc());
  CheckEntrypointTypes<kQuickDeliverException, void, mirror::Object*>();
}

void LocationsBuilderRISCV64::VisitTryBoundary(HTryBoundary* instruction) {
  instruction->SetLocations(nullptr);
}

void InstructionCodeGeneratorRISCV64::VisitTryBoundary(HTryBoundary* instruction) {
  HBasicBlock* successor = instruction->GetNormalFlowSuccessor();
  if (!successor->IsExitBlock()) {
    HandleGoto(instruction, successor);
  }
}

void LocationsBuilderRISCV64::VisitTypeConversion(HTypeConversion* instruction) {
  DataType::Type input_type = instruction->GetInputType();
  DataType::Type result_type = instruction->GetResultType();
  DCHECK(!DataType::IsTypeConversionImplicit(input_type, result_type))
      << input_type << " -> " << result_type;

  if ((input_type == DataType::Type::kReference) || (input_type == DataType::Type::kVoid) ||
      (result_type == DataType::Type::kReference) || (result_type == DataType::Type::kVoid)) {
    LOG(FATAL) << "Unexpected type conversion from " << input_type << " to " << result_type;
  }

  LocationSummary* locations = new (GetGraph()->GetAllocator()) LocationSummary(instruction);

  if (DataType::IsFloatingPointType(input_type)) {
    locations->SetInAt(0, Location::RequiresFpuRegister());
  } else {
    locations->SetInAt(0, Location::RequiresRegister());
  }

  if (DataType::IsFloatingPointType(result_type)) {
    locations->SetOut(Location::RequiresFpuRegister(), Location::kNoOutputOverlap);
  } else {
    locations->SetOut(Location::RequiresRegister(), Location::kNoOutputOverlap);
  }
}

void InstructionCodeGeneratorRISCV64::VisitTypeConversion(HTypeConversion* instruction) {
  LocationSummary* locations = instruction->GetLocations();
  DataType::Type result_type = instruction->GetResultType();
  DataType::Type input_type = instruction->GetInputType();

  DCHECK(!DataType::IsTypeConversionImplicit(input_type, result_type))
      << input_type << " -> " << result_type;

  if (DataType::IsIntegralType(result_type) && DataType::IsIntegralType(input_type)) {
    XRegister dst = locations->Out().AsRegister<XRegister>();
    XRegister src = locations->InAt(0).AsRegister<XRegister>();
    switch (result_type) {
      case DataType::Type::kUint8:
        __ ZextB(dst, src);
        break;
      case DataType::Type::kInt8:
        __ SextB(dst, src);
        break;
      case DataType::Type::kUint16:
        __ ZextH(dst, src);
        break;
      case DataType::Type::kInt16:
        __ SextH(dst, src);
        break;
      case DataType::Type::kInt32:
      case DataType::Type::kInt64:
        // Sign-extend 32-bit int into bits 32 through 63 for int-to-long and long-to-int
        // conversions, except when the input and output registers are the same and we are not
        // converting longs to shorter types. In these cases, do nothing.
        if ((input_type == DataType::Type::kInt64) || (dst != src)) {
          __ Addiw(dst, src, 0);
        }
        break;

      default:
        LOG(FATAL) << "Unexpected type conversion from " << input_type
                   << " to " << result_type;
        UNREACHABLE();
    }
  } else if (DataType::IsFloatingPointType(result_type) && DataType::IsIntegralType(input_type)) {
    FRegister dst = locations->Out().AsFpuRegister<FRegister>();
    XRegister src = locations->InAt(0).AsRegister<XRegister>();
    if (input_type == DataType::Type::kInt64) {
      if (result_type == DataType::Type::kFloat32) {
        __ FCvtSL(dst, src, FPRoundingMode::kRNE);
      } else {
        __ FCvtDL(dst, src, FPRoundingMode::kRNE);
      }
    } else {
      if (result_type == DataType::Type::kFloat32) {
        __ FCvtSW(dst, src, FPRoundingMode::kRNE);
      } else {
        __ FCvtDW(dst, src);  // No rounding.
      }
    }
  } else if (DataType::IsIntegralType(result_type) && DataType::IsFloatingPointType(input_type)) {
    CHECK(result_type == DataType::Type::kInt32 || result_type == DataType::Type::kInt64);
    XRegister dst = locations->Out().AsRegister<XRegister>();
    FRegister src = locations->InAt(0).AsFpuRegister<FRegister>();
    if (result_type == DataType::Type::kInt64) {
      if (input_type == DataType::Type::kFloat32) {
        __ FCvtLS(dst, src, FPRoundingMode::kRTZ);
      } else {
        __ FCvtLD(dst, src, FPRoundingMode::kRTZ);
      }
    } else {
      if (input_type == DataType::Type::kFloat32) {
        __ FCvtWS(dst, src, FPRoundingMode::kRTZ);
      } else {
        __ FCvtWD(dst, src, FPRoundingMode::kRTZ);
      }
    }
    // For NaN inputs we need to return 0.
    ScratchRegisterScope srs(GetAssembler());
    XRegister tmp = srs.AllocateXRegister();
    FClass(tmp, src, input_type);
    __ Sltiu(tmp, tmp, kFClassNaNMinValue);  // 0 for NaN, 1 otherwise.
    __ Neg(tmp, tmp);  // 0 for NaN, -1 otherwise.
    __ And(dst, dst, tmp);  // Cleared for NaN.
  } else if (DataType::IsFloatingPointType(result_type) &&
             DataType::IsFloatingPointType(input_type)) {
    FRegister dst = locations->Out().AsFpuRegister<FRegister>();
    FRegister src = locations->InAt(0).AsFpuRegister<FRegister>();
    if (result_type == DataType::Type::kFloat32) {
      __ FCvtSD(dst, src);
    } else {
      __ FCvtDS(dst, src);
    }
  } else {
    LOG(FATAL) << "Unexpected or unimplemented type conversion from " << input_type
                << " to " << result_type;
    UNREACHABLE();
  }
}

void LocationsBuilderRISCV64::VisitUShr(HUShr* instruction) {
  HandleShift(instruction);
}

void InstructionCodeGeneratorRISCV64::VisitUShr(HUShr* instruction) {
  HandleShift(instruction);
}

void LocationsBuilderRISCV64::VisitXor(HXor* instruction) {
  HandleBinaryOp(instruction);
}

void InstructionCodeGeneratorRISCV64::VisitXor(HXor* instruction) {
  HandleBinaryOp(instruction);
}

void LocationsBuilderRISCV64::VisitVecReplicateScalar(HVecReplicateScalar* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::VisitVecReplicateScalar(HVecReplicateScalar* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void LocationsBuilderRISCV64::VisitVecExtractScalar(HVecExtractScalar* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::VisitVecExtractScalar(HVecExtractScalar* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void LocationsBuilderRISCV64::VisitVecReduce(HVecReduce* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::VisitVecReduce(HVecReduce* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void LocationsBuilderRISCV64::VisitVecCnv(HVecCnv* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::VisitVecCnv(HVecCnv* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void LocationsBuilderRISCV64::VisitVecNeg(HVecNeg* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::VisitVecNeg(HVecNeg* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void LocationsBuilderRISCV64::VisitVecAbs(HVecAbs* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::VisitVecAbs(HVecAbs* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void LocationsBuilderRISCV64::VisitVecNot(HVecNot* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::VisitVecNot(HVecNot* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void LocationsBuilderRISCV64::VisitVecAdd(HVecAdd* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::VisitVecAdd(HVecAdd* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void LocationsBuilderRISCV64::VisitVecHalvingAdd(HVecHalvingAdd* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::VisitVecHalvingAdd(HVecHalvingAdd* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void LocationsBuilderRISCV64::VisitVecSub(HVecSub* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::VisitVecSub(HVecSub* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void LocationsBuilderRISCV64::VisitVecMul(HVecMul* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::VisitVecMul(HVecMul* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void LocationsBuilderRISCV64::VisitVecDiv(HVecDiv* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::VisitVecDiv(HVecDiv* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void LocationsBuilderRISCV64::VisitVecMin(HVecMin* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::VisitVecMin(HVecMin* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void LocationsBuilderRISCV64::VisitVecMax(HVecMax* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::VisitVecMax(HVecMax* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void LocationsBuilderRISCV64::VisitVecAnd(HVecAnd* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::VisitVecAnd(HVecAnd* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void LocationsBuilderRISCV64::VisitVecAndNot(HVecAndNot* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::VisitVecAndNot(HVecAndNot* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void LocationsBuilderRISCV64::VisitVecOr(HVecOr* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::VisitVecOr(HVecOr* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void LocationsBuilderRISCV64::VisitVecXor(HVecXor* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::VisitVecXor(HVecXor* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void LocationsBuilderRISCV64::VisitVecSaturationAdd(HVecSaturationAdd* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::VisitVecSaturationAdd(HVecSaturationAdd* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void LocationsBuilderRISCV64::VisitVecSaturationSub(HVecSaturationSub* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::VisitVecSaturationSub(HVecSaturationSub* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void LocationsBuilderRISCV64::VisitVecShl(HVecShl* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::VisitVecShl(HVecShl* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void LocationsBuilderRISCV64::VisitVecShr(HVecShr* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::VisitVecShr(HVecShr* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void LocationsBuilderRISCV64::VisitVecUShr(HVecUShr* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::VisitVecUShr(HVecUShr* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void LocationsBuilderRISCV64::VisitVecSetScalars(HVecSetScalars* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::VisitVecSetScalars(HVecSetScalars* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void LocationsBuilderRISCV64::VisitVecMultiplyAccumulate(HVecMultiplyAccumulate* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::VisitVecMultiplyAccumulate(
    HVecMultiplyAccumulate* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void LocationsBuilderRISCV64::VisitVecSADAccumulate(HVecSADAccumulate* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::VisitVecSADAccumulate(HVecSADAccumulate* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void LocationsBuilderRISCV64::VisitVecDotProd(HVecDotProd* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::VisitVecDotProd(HVecDotProd* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void LocationsBuilderRISCV64::VisitVecLoad(HVecLoad* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::VisitVecLoad(HVecLoad* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void LocationsBuilderRISCV64::VisitVecStore(HVecStore* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::VisitVecStore(HVecStore* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void LocationsBuilderRISCV64::VisitVecPredSetAll(HVecPredSetAll* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::VisitVecPredSetAll(HVecPredSetAll* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void LocationsBuilderRISCV64::VisitVecPredWhile(HVecPredWhile* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::VisitVecPredWhile(HVecPredWhile* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void LocationsBuilderRISCV64::VisitVecPredToBoolean(HVecPredToBoolean* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::VisitVecPredToBoolean(HVecPredToBoolean* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void LocationsBuilderRISCV64::VisitVecCondition(HVecCondition* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::VisitVecCondition(HVecCondition* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void LocationsBuilderRISCV64::VisitVecPredNot(HVecPredNot* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

void InstructionCodeGeneratorRISCV64::VisitVecPredNot(HVecPredNot* instruction) {
  UNUSED(instruction);
  LOG(FATAL) << "Unimplemented";
}

namespace detail {

// Mark which intrinsics we don't have handcrafted code for.
template <Intrinsics T>
struct IsUnimplemented {
  bool is_unimplemented = false;
};

#define TRUE_OVERRIDE(Name)                     \
  template <>                                   \
  struct IsUnimplemented<Intrinsics::k##Name> { \
    bool is_unimplemented = true;               \
  };
UNIMPLEMENTED_INTRINSIC_LIST_RISCV64(TRUE_OVERRIDE)
#undef TRUE_OVERRIDE

static constexpr bool kIsIntrinsicUnimplemented[] = {
    false,  // kNone
#define IS_UNIMPLEMENTED(Intrinsic, ...) \
    IsUnimplemented<Intrinsics::k##Intrinsic>().is_unimplemented,
    ART_INTRINSICS_LIST(IS_UNIMPLEMENTED)
#undef IS_UNIMPLEMENTED
};

}  // namespace detail

CodeGeneratorRISCV64::CodeGeneratorRISCV64(HGraph* graph,
                                           const CompilerOptions& compiler_options,
                                           OptimizingCompilerStats* stats)
    : CodeGenerator(graph,
                    kNumberOfXRegisters,
                    kNumberOfFRegisters,
                    /*number_of_register_pairs=*/ 0u,
                    ComputeRegisterMask(kCoreCalleeSaves, arraysize(kCoreCalleeSaves)),
                    ComputeRegisterMask(kFpuCalleeSaves, arraysize(kFpuCalleeSaves)),
                    compiler_options,
                    stats,
                    ArrayRef<const bool>(detail::kIsIntrinsicUnimplemented)),
      assembler_(graph->GetAllocator(),
                 compiler_options.GetInstructionSetFeatures()->AsRiscv64InstructionSetFeatures()),
      location_builder_(graph, this),
      instruction_visitor_(graph, this),
      block_labels_(nullptr),
      move_resolver_(graph->GetAllocator(), this),
      uint32_literals_(std::less<uint32_t>(),
                       graph->GetAllocator()->Adapter(kArenaAllocCodeGenerator)),
      uint64_literals_(std::less<uint64_t>(),
                       graph->GetAllocator()->Adapter(kArenaAllocCodeGenerator)),
      boot_image_method_patches_(graph->GetAllocator()->Adapter(kArenaAllocCodeGenerator)),
      method_bss_entry_patches_(graph->GetAllocator()->Adapter(kArenaAllocCodeGenerator)),
      boot_image_type_patches_(graph->GetAllocator()->Adapter(kArenaAllocCodeGenerator)),
      type_bss_entry_patches_(graph->GetAllocator()->Adapter(kArenaAllocCodeGenerator)),
      public_type_bss_entry_patches_(graph->GetAllocator()->Adapter(kArenaAllocCodeGenerator)),
      package_type_bss_entry_patches_(graph->GetAllocator()->Adapter(kArenaAllocCodeGenerator)),
      boot_image_string_patches_(graph->GetAllocator()->Adapter(kArenaAllocCodeGenerator)),
      string_bss_entry_patches_(graph->GetAllocator()->Adapter(kArenaAllocCodeGenerator)),
      boot_image_jni_entrypoint_patches_(graph->GetAllocator()->Adapter(kArenaAllocCodeGenerator)),
      boot_image_other_patches_(graph->GetAllocator()->Adapter(kArenaAllocCodeGenerator)),
      jit_string_patches_(StringReferenceValueComparator(),
                          graph->GetAllocator()->Adapter(kArenaAllocCodeGenerator)),
      jit_class_patches_(TypeReferenceValueComparator(),
                         graph->GetAllocator()->Adapter(kArenaAllocCodeGenerator)) {
  // Always mark the RA register to be saved.
  AddAllocatedRegister(Location::RegisterLocation(RA));
}

void CodeGeneratorRISCV64::MaybeIncrementHotness(HSuspendCheck* suspend_check,
                                                 bool is_frame_entry) {
  if (GetCompilerOptions().CountHotnessInCompiledCode()) {
    ScratchRegisterScope srs(GetAssembler());
    XRegister method = is_frame_entry ? kArtMethodRegister : srs.AllocateXRegister();
    if (!is_frame_entry) {
      __ Loadd(method, SP, 0);
    }
    XRegister counter = srs.AllocateXRegister();
    __ Loadhu(counter, method, ArtMethod::HotnessCountOffset().Int32Value());
    Riscv64Label done;
    DCHECK_EQ(0u, interpreter::kNterpHotnessValue);
    __ Beqz(counter, &done);  // Can clobber `TMP` if taken.
    __ Addi(counter, counter, -1);
    // We may not have another scratch register available for `Storeh`()`,
    // so we must use the `Sh()` function directly.
    static_assert(IsInt<12>(ArtMethod::HotnessCountOffset().Int32Value()));
    __ Sh(counter, method, ArtMethod::HotnessCountOffset().Int32Value());
    __ Bind(&done);
  }

  if (GetGraph()->IsCompilingBaseline() &&
      GetGraph()->IsUsefulOptimizing() &&
      !Runtime::Current()->IsAotCompiler()) {
    ProfilingInfo* info = GetGraph()->GetProfilingInfo();
    DCHECK(info != nullptr);
    DCHECK(!HasEmptyFrame());
    uint64_t address = reinterpret_cast64<uint64_t>(info) +
                       ProfilingInfo::BaselineHotnessCountOffset().SizeValue();
    auto [base_address, imm12] = SplitJitAddress(address);
    ScratchRegisterScope srs(GetAssembler());
    XRegister counter = srs.AllocateXRegister();
    XRegister tmp = RA;
    __ LoadConst64(tmp, base_address);
    SlowPathCodeRISCV64* slow_path =
        new (GetScopedAllocator()) CompileOptimizedSlowPathRISCV64(suspend_check, tmp, imm12);
    AddSlowPath(slow_path);
    __ Lhu(counter, tmp, imm12);
    __ Beqz(counter, slow_path->GetEntryLabel());  // Can clobber `TMP` if taken.
    __ Addi(counter, counter, -1);
    __ Sh(counter, tmp, imm12);
    __ Bind(slow_path->GetExitLabel());
  }
}

bool CodeGeneratorRISCV64::CanUseImplicitSuspendCheck() const {
  // TODO(riscv64): Implement implicit suspend checks to reduce code size.
  return false;
}

void CodeGeneratorRISCV64::GenerateMemoryBarrier(MemBarrierKind kind) {
  switch (kind) {
    case MemBarrierKind::kAnyAny:
      __ Fence(/*pred=*/ kFenceRead | kFenceWrite, /*succ=*/ kFenceRead | kFenceWrite);
      break;
    case MemBarrierKind::kAnyStore:
      __ Fence(/*pred=*/ kFenceRead | kFenceWrite, /*succ=*/ kFenceWrite);
      break;
    case MemBarrierKind::kLoadAny:
      __ Fence(/*pred=*/ kFenceRead, /*succ=*/ kFenceRead | kFenceWrite);
      break;
    case MemBarrierKind::kStoreStore:
      __ Fence(/*pred=*/ kFenceWrite, /*succ=*/ kFenceWrite);
      break;

    default:
      LOG(FATAL) << "Unexpected memory barrier " << kind;
      UNREACHABLE();
  }
}

void CodeGeneratorRISCV64::GenerateFrameEntry() {
  // Check if we need to generate the clinit check. We will jump to the
  // resolution stub if the class is not initialized and the executing thread is
  // not the thread initializing it.
  // We do this before constructing the frame to get the correct stack trace if
  // an exception is thrown.
  if (GetCompilerOptions().ShouldCompileWithClinitCheck(GetGraph()->GetArtMethod())) {
    Riscv64Label resolution;
    Riscv64Label memory_barrier;

    ScratchRegisterScope srs(GetAssembler());
    XRegister tmp = srs.AllocateXRegister();
    XRegister tmp2 = srs.AllocateXRegister();

    // We don't emit a read barrier here to save on code size. We rely on the
    // resolution trampoline to do a clinit check before re-entering this code.
    __ Loadwu(tmp2, kArtMethodRegister, ArtMethod::DeclaringClassOffset().Int32Value());

    // We shall load the full 32-bit status word with sign-extension and compare as unsigned
    // to sign-extended shifted status values. This yields the same comparison as loading and
    // materializing unsigned but the constant is materialized with a single LUI instruction.
    __ Loadw(tmp, tmp2, mirror::Class::StatusOffset().SizeValue());  // Sign-extended.

    // Check if we're visibly initialized.
    __ Li(tmp2, ShiftedSignExtendedClassStatusValue<ClassStatus::kVisiblyInitialized>());
    __ Bgeu(tmp, tmp2, &frame_entry_label_);  // Can clobber `TMP` if taken.

    // Check if we're initialized and jump to code that does a memory barrier if so.
    __ Li(tmp2, ShiftedSignExtendedClassStatusValue<ClassStatus::kInitialized>());
    __ Bgeu(tmp, tmp2, &memory_barrier);  // Can clobber `TMP` if taken.

    // Check if we're initializing and the thread initializing is the one
    // executing the code.
    __ Li(tmp2, ShiftedSignExtendedClassStatusValue<ClassStatus::kInitializing>());
    __ Bltu(tmp, tmp2, &resolution);  // Can clobber `TMP` if taken.

    __ Loadwu(tmp2, kArtMethodRegister, ArtMethod::DeclaringClassOffset().Int32Value());
    __ Loadw(tmp, tmp2, mirror::Class::ClinitThreadIdOffset().Int32Value());
    __ Loadw(tmp2, TR, Thread::TidOffset<kRiscv64PointerSize>().Int32Value());
    __ Beq(tmp, tmp2, &frame_entry_label_);
    __ Bind(&resolution);

    // Jump to the resolution stub.
    ThreadOffset64 entrypoint_offset =
        GetThreadOffset<kRiscv64PointerSize>(kQuickQuickResolutionTrampoline);
    __ Loadd(tmp, TR, entrypoint_offset.Int32Value());
    __ Jr(tmp);

    __ Bind(&memory_barrier);
    GenerateMemoryBarrier(MemBarrierKind::kAnyAny);
  }
  __ Bind(&frame_entry_label_);

  bool do_overflow_check =
      FrameNeedsStackCheck(GetFrameSize(), InstructionSet::kRiscv64) || !IsLeafMethod();

  if (do_overflow_check) {
    DCHECK(GetCompilerOptions().GetImplicitStackOverflowChecks());
    __ Loadw(
        Zero, SP, -static_cast<int32_t>(GetStackOverflowReservedBytes(InstructionSet::kRiscv64)));
    RecordPcInfo(nullptr, 0);
  }

  if (!HasEmptyFrame()) {
    // Make sure the frame size isn't unreasonably large.
    DCHECK_LE(GetFrameSize(), GetMaximumFrameSize());

    // Spill callee-saved registers.

    uint32_t frame_size = GetFrameSize();

    IncreaseFrame(frame_size);

    uint32_t offset = frame_size;
    for (size_t i = arraysize(kCoreCalleeSaves); i != 0; ) {
      --i;
      XRegister reg = kCoreCalleeSaves[i];
      if (allocated_registers_.ContainsCoreRegister(reg)) {
        offset -= kRiscv64DoublewordSize;
        __ Stored(reg, SP, offset);
        __ cfi().RelOffset(dwarf::Reg::Riscv64Core(reg), offset);
      }
    }

    for (size_t i = arraysize(kFpuCalleeSaves); i != 0; ) {
      --i;
      FRegister reg = kFpuCalleeSaves[i];
      if (allocated_registers_.ContainsFloatingPointRegister(reg)) {
        offset -= kRiscv64DoublewordSize;
        __ FStored(reg, SP, offset);
        __ cfi().RelOffset(dwarf::Reg::Riscv64Fp(reg), offset);
      }
    }

    // Save the current method if we need it. Note that we do not
    // do this in HCurrentMethod, as the instruction might have been removed
    // in the SSA graph.
    if (RequiresCurrentMethod()) {
      __ Stored(kArtMethodRegister, SP, 0);
    }

    if (GetGraph()->HasShouldDeoptimizeFlag()) {
      // Initialize should_deoptimize flag to 0.
      __ Storew(Zero, SP, GetStackOffsetOfShouldDeoptimizeFlag());
    }
  }
  MaybeIncrementHotness(/* suspend_check= */ nullptr, /*is_frame_entry=*/ true);
}

void CodeGeneratorRISCV64::GenerateFrameExit() {
  __ cfi().RememberState();

  if (!HasEmptyFrame()) {
    // Restore callee-saved registers.

    // For better instruction scheduling restore RA before other registers.
    uint32_t offset = GetFrameSize();
    for (size_t i = arraysize(kCoreCalleeSaves); i != 0; ) {
      --i;
      XRegister reg = kCoreCalleeSaves[i];
      if (allocated_registers_.ContainsCoreRegister(reg)) {
        offset -= kRiscv64DoublewordSize;
        __ Loadd(reg, SP, offset);
        __ cfi().Restore(dwarf::Reg::Riscv64Core(reg));
      }
    }

    for (size_t i = arraysize(kFpuCalleeSaves); i != 0; ) {
      --i;
      FRegister reg = kFpuCalleeSaves[i];
      if (allocated_registers_.ContainsFloatingPointRegister(reg)) {
        offset -= kRiscv64DoublewordSize;
        __ FLoadd(reg, SP, offset);
        __ cfi().Restore(dwarf::Reg::Riscv64Fp(reg));
      }
    }

    DecreaseFrame(GetFrameSize());
  }

  __ Jr(RA);

  __ cfi().RestoreState();
  __ cfi().DefCFAOffset(GetFrameSize());
}

void CodeGeneratorRISCV64::Bind(HBasicBlock* block) { __ Bind(GetLabelOf(block)); }

void CodeGeneratorRISCV64::MoveConstant(Location destination, int32_t value) {
  DCHECK(destination.IsRegister());
  __ LoadConst32(destination.AsRegister<XRegister>(), value);
}

void CodeGeneratorRISCV64::MoveLocation(Location destination,
                                        Location source,
                                        DataType::Type dst_type) {
  if (source.Equals(destination)) {
    return;
  }

  // A valid move type can always be inferred from the destination and source locations.
  // When moving from and to a register, the `dst_type` can be used to generate 32-bit instead
  // of 64-bit moves but it's generally OK to use 64-bit moves for 32-bit values in registers.
  bool unspecified_type = (dst_type == DataType::Type::kVoid);
  // TODO(riscv64): Is the destination type known in all cases?
  // TODO(riscv64): Can unspecified `dst_type` move 32-bit GPR to FPR without NaN-boxing?
  CHECK(!unspecified_type);

  if (destination.IsRegister() || destination.IsFpuRegister()) {
    if (unspecified_type) {
      HConstant* src_cst = source.IsConstant() ? source.GetConstant() : nullptr;
      if (source.IsStackSlot() ||
          (src_cst != nullptr &&
           (src_cst->IsIntConstant() || src_cst->IsFloatConstant() || src_cst->IsNullConstant()))) {
        // For stack slots and 32-bit constants, a 32-bit type is appropriate.
        dst_type = destination.IsRegister() ? DataType::Type::kInt32 : DataType::Type::kFloat32;
      } else {
        // If the source is a double stack slot or a 64-bit constant, a 64-bit type
        // is appropriate. Else the source is a register, and since the type has not
        // been specified, we chose a 64-bit type to force a 64-bit move.
        dst_type = destination.IsRegister() ? DataType::Type::kInt64 : DataType::Type::kFloat64;
      }
    }
    DCHECK((destination.IsFpuRegister() && DataType::IsFloatingPointType(dst_type)) ||
           (destination.IsRegister() && !DataType::IsFloatingPointType(dst_type)));

    if (source.IsStackSlot() || source.IsDoubleStackSlot()) {
      // Move to GPR/FPR from stack
      if (DataType::IsFloatingPointType(dst_type)) {
        if (DataType::Is64BitType(dst_type)) {
          __ FLoadd(destination.AsFpuRegister<FRegister>(), SP, source.GetStackIndex());
        } else {
          __ FLoadw(destination.AsFpuRegister<FRegister>(), SP, source.GetStackIndex());
        }
      } else {
        if (DataType::Is64BitType(dst_type)) {
          __ Loadd(destination.AsRegister<XRegister>(), SP, source.GetStackIndex());
        } else if (dst_type == DataType::Type::kReference) {
          __ Loadwu(destination.AsRegister<XRegister>(), SP, source.GetStackIndex());
        } else {
          __ Loadw(destination.AsRegister<XRegister>(), SP, source.GetStackIndex());
        }
      }
    } else if (source.IsConstant()) {
      // Move to GPR/FPR from constant
      // TODO(riscv64): Consider using literals for difficult-to-materialize 64-bit constants.
      int64_t value = GetInt64ValueOf(source.GetConstant()->AsConstant());
      ScratchRegisterScope srs(GetAssembler());
      XRegister gpr = DataType::IsFloatingPointType(dst_type)
          ? srs.AllocateXRegister()
          : destination.AsRegister<XRegister>();
      if (DataType::IsFloatingPointType(dst_type) && value == 0) {
        gpr = Zero;  // Note: The scratch register allocated above shall not be used.
      } else {
        // Note: For `float` we load the sign-extended value here as it can sometimes yield
        // a shorter instruction sequence. The higher 32 bits shall be ignored during the
        // transfer to FP reg and the result shall be correctly NaN-boxed.
        __ LoadConst64(gpr, value);
      }
      if (dst_type == DataType::Type::kFloat32) {
        __ FMvWX(destination.AsFpuRegister<FRegister>(), gpr);
      } else if (dst_type == DataType::Type::kFloat64) {
        __ FMvDX(destination.AsFpuRegister<FRegister>(), gpr);
      }
    } else if (source.IsRegister()) {
      if (destination.IsRegister()) {
        // Move to GPR from GPR
        __ Mv(destination.AsRegister<XRegister>(), source.AsRegister<XRegister>());
      } else {
        DCHECK(destination.IsFpuRegister());
        if (DataType::Is64BitType(dst_type)) {
          __ FMvDX(destination.AsFpuRegister<FRegister>(), source.AsRegister<XRegister>());
        } else {
          __ FMvWX(destination.AsFpuRegister<FRegister>(), source.AsRegister<XRegister>());
        }
      }
    } else if (source.IsFpuRegister()) {
      if (destination.IsFpuRegister()) {
        if (GetGraph()->HasSIMD()) {
          LOG(FATAL) << "Vector extension is unsupported";
          UNREACHABLE();
        } else {
          // Move to FPR from FPR
          if (dst_type == DataType::Type::kFloat32) {
            __ FMvS(destination.AsFpuRegister<FRegister>(), source.AsFpuRegister<FRegister>());
          } else {
            DCHECK_EQ(dst_type, DataType::Type::kFloat64);
            __ FMvD(destination.AsFpuRegister<FRegister>(), source.AsFpuRegister<FRegister>());
          }
        }
      } else {
        DCHECK(destination.IsRegister());
        if (DataType::Is64BitType(dst_type)) {
          __ FMvXD(destination.AsRegister<XRegister>(), source.AsFpuRegister<FRegister>());
        } else {
          __ FMvXW(destination.AsRegister<XRegister>(), source.AsFpuRegister<FRegister>());
        }
      }
    }
  } else if (destination.IsSIMDStackSlot()) {
    LOG(FATAL) << "SIMD is unsupported";
    UNREACHABLE();
  } else {  // The destination is not a register. It must be a stack slot.
    DCHECK(destination.IsStackSlot() || destination.IsDoubleStackSlot());
    if (source.IsRegister() || source.IsFpuRegister()) {
      if (unspecified_type) {
        if (source.IsRegister()) {
          dst_type = destination.IsStackSlot() ? DataType::Type::kInt32 : DataType::Type::kInt64;
        } else {
          dst_type =
              destination.IsStackSlot() ? DataType::Type::kFloat32 : DataType::Type::kFloat64;
        }
      }
      DCHECK_EQ(source.IsFpuRegister(), DataType::IsFloatingPointType(dst_type));
      // For direct @CriticalNative calls, we need to sign-extend narrow integral args
      // to 64 bits, so widening integral values is allowed. Narrowing is forbidden.
      DCHECK_IMPLIES(DataType::IsFloatingPointType(dst_type) || destination.IsStackSlot(),
                     destination.IsDoubleStackSlot() == DataType::Is64BitType(dst_type));
      // Move to stack from GPR/FPR
      if (destination.IsDoubleStackSlot()) {
        if (source.IsRegister()) {
          __ Stored(source.AsRegister<XRegister>(), SP, destination.GetStackIndex());
        } else {
          __ FStored(source.AsFpuRegister<FRegister>(), SP, destination.GetStackIndex());
        }
      } else {
        if (source.IsRegister()) {
          __ Storew(source.AsRegister<XRegister>(), SP, destination.GetStackIndex());
        } else {
          __ FStorew(source.AsFpuRegister<FRegister>(), SP, destination.GetStackIndex());
        }
      }
    } else if (source.IsConstant()) {
      // Move to stack from constant
      int64_t value = GetInt64ValueOf(source.GetConstant());
      ScratchRegisterScope srs(GetAssembler());
      XRegister gpr = (value != 0) ? srs.AllocateXRegister() : Zero;
      if (value != 0) {
        __ LoadConst64(gpr, value);
      }
      if (destination.IsStackSlot()) {
        __ Storew(gpr, SP, destination.GetStackIndex());
      } else {
        DCHECK(destination.IsDoubleStackSlot());
        __ Stored(gpr, SP, destination.GetStackIndex());
      }
    } else {
      DCHECK(source.IsStackSlot() || source.IsDoubleStackSlot());
      // For direct @CriticalNative calls, we need to sign-extend narrow integral args
      // to 64 bits, so widening move is allowed. Narrowing move is forbidden.
      DCHECK_IMPLIES(destination.IsStackSlot(), source.IsStackSlot());
      // Move to stack from stack
      ScratchRegisterScope srs(GetAssembler());
      XRegister tmp = srs.AllocateXRegister();
      if (source.IsStackSlot()) {
        __ Loadw(tmp, SP, source.GetStackIndex());
      } else {
        __ Loadd(tmp, SP, source.GetStackIndex());
      }
      if (destination.IsStackSlot()) {
        __ Storew(tmp, SP, destination.GetStackIndex());
      } else {
        __ Stored(tmp, SP, destination.GetStackIndex());
      }
    }
  }
}

void CodeGeneratorRISCV64::AddLocationAsTemp(Location location, LocationSummary* locations) {
  if (location.IsRegister()) {
    locations->AddTemp(location);
  } else {
    UNIMPLEMENTED(FATAL) << "AddLocationAsTemp not implemented for location " << location;
  }
}

void CodeGeneratorRISCV64::SetupBlockedRegisters() const {
  // ZERO, GP, SP, RA, TP and TR(S1) are reserved and can't be allocated.
  blocked_core_registers_[Zero] = true;
  blocked_core_registers_[GP] = true;
  blocked_core_registers_[SP] = true;
  blocked_core_registers_[RA] = true;
  blocked_core_registers_[TP] = true;
  blocked_core_registers_[TR] = true;  // ART Thread register.

  // TMP(T6), TMP2(T5) and FTMP(FT11) are used as temporary/scratch registers.
  blocked_core_registers_[TMP] = true;
  blocked_core_registers_[TMP2] = true;
  blocked_fpu_registers_[FTMP] = true;

  if (GetGraph()->IsDebuggable()) {
    // Stubs do not save callee-save floating point registers. If the graph
    // is debuggable, we need to deal with these registers differently. For
    // now, just block them.
    for (size_t i = 0; i < arraysize(kFpuCalleeSaves); ++i) {
      blocked_fpu_registers_[kFpuCalleeSaves[i]] = true;
    }
  }
}

size_t CodeGeneratorRISCV64::SaveCoreRegister(size_t stack_index, uint32_t reg_id) {
  __ Stored(XRegister(reg_id), SP, stack_index);
  return kRiscv64DoublewordSize;
}

size_t CodeGeneratorRISCV64::RestoreCoreRegister(size_t stack_index, uint32_t reg_id) {
  __ Loadd(XRegister(reg_id), SP, stack_index);
  return kRiscv64DoublewordSize;
}

size_t CodeGeneratorRISCV64::SaveFloatingPointRegister(size_t stack_index, uint32_t reg_id) {
  if (GetGraph()->HasSIMD()) {
    // TODO(riscv64): RISC-V vector extension.
    UNIMPLEMENTED(FATAL) << "Vector extension is unsupported";
    UNREACHABLE();
  }
  __ FStored(FRegister(reg_id), SP, stack_index);
  return kRiscv64FloatRegSizeInBytes;
}

size_t CodeGeneratorRISCV64::RestoreFloatingPointRegister(size_t stack_index, uint32_t reg_id) {
  if (GetGraph()->HasSIMD()) {
    // TODO(riscv64): RISC-V vector extension.
    UNIMPLEMENTED(FATAL) << "Vector extension is unsupported";
    UNREACHABLE();
  }
  __ FLoadd(FRegister(reg_id), SP, stack_index);
  return kRiscv64FloatRegSizeInBytes;
}

void CodeGeneratorRISCV64::DumpCoreRegister(std::ostream& stream, int reg) const {
  stream << XRegister(reg);
}

void CodeGeneratorRISCV64::DumpFloatingPointRegister(std::ostream& stream, int reg) const {
  stream << FRegister(reg);
}

const Riscv64InstructionSetFeatures& CodeGeneratorRISCV64::GetInstructionSetFeatures() const {
  return *GetCompilerOptions().GetInstructionSetFeatures()->AsRiscv64InstructionSetFeatures();
}

void CodeGeneratorRISCV64::Finalize() {
  // Ensure that we fix up branches and literal loads and emit the literal pool.
  __ FinalizeCode();

  // Adjust native pc offsets in stack maps.
  StackMapStream* stack_map_stream = GetStackMapStream();
  for (size_t i = 0, num = stack_map_stream->GetNumberOfStackMaps(); i != num; ++i) {
    uint32_t old_position = stack_map_stream->GetStackMapNativePcOffset(i);
    uint32_t new_position = __ GetAdjustedPosition(old_position);
    DCHECK_GE(new_position, old_position);
    stack_map_stream->SetStackMapNativePcOffset(i, new_position);
  }

  // Adjust pc offsets for the disassembly information.
  if (disasm_info_ != nullptr) {
    GeneratedCodeInterval* frame_entry_interval = disasm_info_->GetFrameEntryInterval();
    frame_entry_interval->start = __ GetAdjustedPosition(frame_entry_interval->start);
    frame_entry_interval->end = __ GetAdjustedPosition(frame_entry_interval->end);
    for (auto& entry : *disasm_info_->GetInstructionIntervals()) {
      entry.second.start = __ GetAdjustedPosition(entry.second.start);
      entry.second.end = __ GetAdjustedPosition(entry.second.end);
    }
    for (auto& entry : *disasm_info_->GetSlowPathIntervals()) {
      entry.code_interval.start = __ GetAdjustedPosition(entry.code_interval.start);
      entry.code_interval.end = __ GetAdjustedPosition(entry.code_interval.end);
    }
  }
}

// Generate code to invoke a runtime entry point.
void CodeGeneratorRISCV64::InvokeRuntime(QuickEntrypointEnum entrypoint,
                                         HInstruction* instruction,
                                         uint32_t dex_pc,
                                         SlowPathCode* slow_path) {
  ValidateInvokeRuntime(entrypoint, instruction, slow_path);

  ThreadOffset64 entrypoint_offset = GetThreadOffset<kRiscv64PointerSize>(entrypoint);

  // TODO(riscv64): Reduce code size for AOT by using shared trampolines for slow path
  // runtime calls across the entire oat file.
  __ Loadd(RA, TR, entrypoint_offset.Int32Value());
  __ Jalr(RA);
  if (EntrypointRequiresStackMap(entrypoint)) {
    RecordPcInfo(instruction, dex_pc, slow_path);
  }
}

// Generate code to invoke a runtime entry point, but do not record
// PC-related information in a stack map.
void CodeGeneratorRISCV64::InvokeRuntimeWithoutRecordingPcInfo(int32_t entry_point_offset,
                                                               HInstruction* instruction,
                                                               SlowPathCode* slow_path) {
  ValidateInvokeRuntimeWithoutRecordingPcInfo(instruction, slow_path);
  __ Loadd(RA, TR, entry_point_offset);
  __ Jalr(RA);
}

void CodeGeneratorRISCV64::IncreaseFrame(size_t adjustment) {
  int32_t adjustment32 = dchecked_integral_cast<int32_t>(adjustment);
  __ AddConst64(SP, SP, -adjustment32);
  GetAssembler()->cfi().AdjustCFAOffset(adjustment32);
}

void CodeGeneratorRISCV64::DecreaseFrame(size_t adjustment) {
  int32_t adjustment32 = dchecked_integral_cast<int32_t>(adjustment);
  __ AddConst64(SP, SP, adjustment32);
  GetAssembler()->cfi().AdjustCFAOffset(-adjustment32);
}

void CodeGeneratorRISCV64::GenerateNop() {
  __ Nop();
}

void CodeGeneratorRISCV64::GenerateImplicitNullCheck(HNullCheck* instruction) {
  if (CanMoveNullCheckToUser(instruction)) {
    return;
  }
  Location obj = instruction->GetLocations()->InAt(0);

  __ Lw(Zero, obj.AsRegister<XRegister>(), 0);
  RecordPcInfo(instruction, instruction->GetDexPc());
}

void CodeGeneratorRISCV64::GenerateExplicitNullCheck(HNullCheck* instruction) {
  SlowPathCodeRISCV64* slow_path = new (GetScopedAllocator()) NullCheckSlowPathRISCV64(instruction);
  AddSlowPath(slow_path);

  Location obj = instruction->GetLocations()->InAt(0);

  __ Beqz(obj.AsRegister<XRegister>(), slow_path->GetEntryLabel());
}

HLoadString::LoadKind CodeGeneratorRISCV64::GetSupportedLoadStringKind(
    HLoadString::LoadKind desired_string_load_kind) {
  switch (desired_string_load_kind) {
    case HLoadString::LoadKind::kBootImageLinkTimePcRelative:
    case HLoadString::LoadKind::kBootImageRelRo:
    case HLoadString::LoadKind::kBssEntry:
      DCHECK(!Runtime::Current()->UseJitCompilation());
      break;
    case HLoadString::LoadKind::kJitBootImageAddress:
    case HLoadString::LoadKind::kJitTableAddress:
      DCHECK(Runtime::Current()->UseJitCompilation());
      break;
    case HLoadString::LoadKind::kRuntimeCall:
      break;
  }
  return desired_string_load_kind;
}

HLoadClass::LoadKind CodeGeneratorRISCV64::GetSupportedLoadClassKind(
    HLoadClass::LoadKind desired_class_load_kind) {
  switch (desired_class_load_kind) {
    case HLoadClass::LoadKind::kInvalid:
      LOG(FATAL) << "UNREACHABLE";
      UNREACHABLE();
    case HLoadClass::LoadKind::kReferrersClass:
      break;
    case HLoadClass::LoadKind::kBootImageLinkTimePcRelative:
    case HLoadClass::LoadKind::kBootImageRelRo:
    case HLoadClass::LoadKind::kBssEntry:
    case HLoadClass::LoadKind::kBssEntryPublic:
    case HLoadClass::LoadKind::kBssEntryPackage:
      DCHECK(!Runtime::Current()->UseJitCompilation());
      break;
    case HLoadClass::LoadKind::kJitBootImageAddress:
    case HLoadClass::LoadKind::kJitTableAddress:
      DCHECK(Runtime::Current()->UseJitCompilation());
      break;
    case HLoadClass::LoadKind::kRuntimeCall:
      break;
  }
  return desired_class_load_kind;
}

HInvokeStaticOrDirect::DispatchInfo CodeGeneratorRISCV64::GetSupportedInvokeStaticOrDirectDispatch(
    const HInvokeStaticOrDirect::DispatchInfo& desired_dispatch_info, ArtMethod* method) {
  UNUSED(method);
  // On RISCV64 we support all dispatch types.
  return desired_dispatch_info;
}

CodeGeneratorRISCV64::PcRelativePatchInfo* CodeGeneratorRISCV64::NewBootImageIntrinsicPatch(
    uint32_t intrinsic_data, const PcRelativePatchInfo* info_high) {
  return NewPcRelativePatch(
      /* dex_file= */ nullptr, intrinsic_data, info_high, &boot_image_other_patches_);
}

CodeGeneratorRISCV64::PcRelativePatchInfo* CodeGeneratorRISCV64::NewBootImageRelRoPatch(
    uint32_t boot_image_offset, const PcRelativePatchInfo* info_high) {
  return NewPcRelativePatch(
      /* dex_file= */ nullptr, boot_image_offset, info_high, &boot_image_other_patches_);
}

CodeGeneratorRISCV64::PcRelativePatchInfo* CodeGeneratorRISCV64::NewBootImageMethodPatch(
    MethodReference target_method, const PcRelativePatchInfo* info_high) {
  return NewPcRelativePatch(
      target_method.dex_file, target_method.index, info_high, &boot_image_method_patches_);
}

CodeGeneratorRISCV64::PcRelativePatchInfo* CodeGeneratorRISCV64::NewMethodBssEntryPatch(
    MethodReference target_method, const PcRelativePatchInfo* info_high) {
  return NewPcRelativePatch(
      target_method.dex_file, target_method.index, info_high, &method_bss_entry_patches_);
}

CodeGeneratorRISCV64::PcRelativePatchInfo* CodeGeneratorRISCV64::NewBootImageTypePatch(
    const DexFile& dex_file, dex::TypeIndex type_index, const PcRelativePatchInfo* info_high) {
  return NewPcRelativePatch(&dex_file, type_index.index_, info_high, &boot_image_type_patches_);
}

CodeGeneratorRISCV64::PcRelativePatchInfo* CodeGeneratorRISCV64::NewBootImageJniEntrypointPatch(
    MethodReference target_method, const PcRelativePatchInfo* info_high) {
  return NewPcRelativePatch(
      target_method.dex_file, target_method.index, info_high, &boot_image_jni_entrypoint_patches_);
}

CodeGeneratorRISCV64::PcRelativePatchInfo* CodeGeneratorRISCV64::NewTypeBssEntryPatch(
    HLoadClass* load_class,
    const PcRelativePatchInfo* info_high) {
  const DexFile& dex_file = load_class->GetDexFile();
  dex::TypeIndex type_index = load_class->GetTypeIndex();
  ArenaDeque<PcRelativePatchInfo>* patches = nullptr;
  switch (load_class->GetLoadKind()) {
    case HLoadClass::LoadKind::kBssEntry:
      patches = &type_bss_entry_patches_;
      break;
    case HLoadClass::LoadKind::kBssEntryPublic:
      patches = &public_type_bss_entry_patches_;
      break;
    case HLoadClass::LoadKind::kBssEntryPackage:
      patches = &package_type_bss_entry_patches_;
      break;
    default:
      LOG(FATAL) << "Unexpected load kind: " << load_class->GetLoadKind();
      UNREACHABLE();
  }
  return NewPcRelativePatch(&dex_file, type_index.index_, info_high, patches);
}

CodeGeneratorRISCV64::PcRelativePatchInfo* CodeGeneratorRISCV64::NewBootImageStringPatch(
    const DexFile& dex_file, dex::StringIndex string_index, const PcRelativePatchInfo* info_high) {
  return NewPcRelativePatch(&dex_file, string_index.index_, info_high, &boot_image_string_patches_);
}

CodeGeneratorRISCV64::PcRelativePatchInfo* CodeGeneratorRISCV64::NewStringBssEntryPatch(
    const DexFile& dex_file, dex::StringIndex string_index, const PcRelativePatchInfo* info_high) {
  return NewPcRelativePatch(&dex_file, string_index.index_, info_high, &string_bss_entry_patches_);
}

CodeGeneratorRISCV64::PcRelativePatchInfo* CodeGeneratorRISCV64::NewPcRelativePatch(
    const DexFile* dex_file,
    uint32_t offset_or_index,
    const PcRelativePatchInfo* info_high,
    ArenaDeque<PcRelativePatchInfo>* patches) {
  patches->emplace_back(dex_file, offset_or_index, info_high);
  return &patches->back();
}

Literal* CodeGeneratorRISCV64::DeduplicateUint32Literal(uint32_t value) {
  return uint32_literals_.GetOrCreate(value,
                                      [this, value]() { return __ NewLiteral<uint32_t>(value); });
}

Literal* CodeGeneratorRISCV64::DeduplicateUint64Literal(uint64_t value) {
  return uint64_literals_.GetOrCreate(value,
                                      [this, value]() { return __ NewLiteral<uint64_t>(value); });
}

Literal* CodeGeneratorRISCV64::DeduplicateBootImageAddressLiteral(uint64_t address) {
  return DeduplicateUint32Literal(dchecked_integral_cast<uint32_t>(address));
}

Literal* CodeGeneratorRISCV64::DeduplicateJitStringLiteral(const DexFile& dex_file,
                                                           dex::StringIndex string_index,
                                                           Handle<mirror::String> handle) {
  ReserveJitStringRoot(StringReference(&dex_file, string_index), handle);
  return jit_string_patches_.GetOrCreate(
      StringReference(&dex_file, string_index),
      [this]() { return __ NewLiteral<uint32_t>(/* value= */ 0u); });
}

Literal* CodeGeneratorRISCV64::DeduplicateJitClassLiteral(const DexFile& dex_file,
                                                          dex::TypeIndex type_index,
                                                          Handle<mirror::Class> handle) {
  ReserveJitClassRoot(TypeReference(&dex_file, type_index), handle);
  return jit_class_patches_.GetOrCreate(
      TypeReference(&dex_file, type_index),
      [this]() { return __ NewLiteral<uint32_t>(/* value= */ 0u); });
}

void CodeGeneratorRISCV64::PatchJitRootUse(uint8_t* code,
                                          const uint8_t* roots_data,
                                          const Literal* literal,
                                          uint64_t index_in_table) const {
  uint32_t literal_offset = GetAssembler().GetLabelLocation(literal->GetLabel());
  uintptr_t address =
      reinterpret_cast<uintptr_t>(roots_data) + index_in_table * sizeof(GcRoot<mirror::Object>);
  reinterpret_cast<uint32_t*>(code + literal_offset)[0] = dchecked_integral_cast<uint32_t>(address);
}

void CodeGeneratorRISCV64::EmitJitRootPatches(uint8_t* code, const uint8_t* roots_data) {
  for (const auto& entry : jit_string_patches_) {
    const StringReference& string_reference = entry.first;
    Literal* table_entry_literal = entry.second;
    uint64_t index_in_table = GetJitStringRootIndex(string_reference);
    PatchJitRootUse(code, roots_data, table_entry_literal, index_in_table);
  }
  for (const auto& entry : jit_class_patches_) {
    const TypeReference& type_reference = entry.first;
    Literal* table_entry_literal = entry.second;
    uint64_t index_in_table = GetJitClassRootIndex(type_reference);
    PatchJitRootUse(code, roots_data, table_entry_literal, index_in_table);
  }
}

void CodeGeneratorRISCV64::EmitPcRelativeAuipcPlaceholder(PcRelativePatchInfo* info_high,
                                                          XRegister out) {
  DCHECK(info_high->pc_insn_label == &info_high->label);
  __ Bind(&info_high->label);
  __ Auipc(out, /*imm20=*/ kLinkTimeOffsetPlaceholderHigh);
}

void CodeGeneratorRISCV64::EmitPcRelativeAddiPlaceholder(PcRelativePatchInfo* info_low,
                                                         XRegister rd,
                                                         XRegister rs1) {
  DCHECK(info_low->pc_insn_label != &info_low->label);
  __ Bind(&info_low->label);
  __ Addi(rd, rs1, /*imm12=*/ kLinkTimeOffsetPlaceholderLow);
}

void CodeGeneratorRISCV64::EmitPcRelativeLwuPlaceholder(PcRelativePatchInfo* info_low,
                                                        XRegister rd,
                                                        XRegister rs1) {
  DCHECK(info_low->pc_insn_label != &info_low->label);
  __ Bind(&info_low->label);
  __ Lwu(rd, rs1, /*offset=*/ kLinkTimeOffsetPlaceholderLow);
}

void CodeGeneratorRISCV64::EmitPcRelativeLdPlaceholder(PcRelativePatchInfo* info_low,
                                                       XRegister rd,
                                                       XRegister rs1) {
  DCHECK(info_low->pc_insn_label != &info_low->label);
  __ Bind(&info_low->label);
  __ Ld(rd, rs1, /*offset=*/ kLinkTimeOffsetPlaceholderLow);
}

template <linker::LinkerPatch (*Factory)(size_t, const DexFile*, uint32_t, uint32_t)>
inline void CodeGeneratorRISCV64::EmitPcRelativeLinkerPatches(
    const ArenaDeque<PcRelativePatchInfo>& infos,
    ArenaVector<linker::LinkerPatch>* linker_patches) {
  for (const PcRelativePatchInfo& info : infos) {
    linker_patches->push_back(Factory(__ GetLabelLocation(&info.label),
                                      info.target_dex_file,
                                      __ GetLabelLocation(info.pc_insn_label),
                                      info.offset_or_index));
  }
}

template <linker::LinkerPatch (*Factory)(size_t, uint32_t, uint32_t)>
linker::LinkerPatch NoDexFileAdapter(size_t literal_offset,
                                     const DexFile* target_dex_file,
                                     uint32_t pc_insn_offset,
                                     uint32_t boot_image_offset) {
  DCHECK(target_dex_file == nullptr);  // Unused for these patches, should be null.
  return Factory(literal_offset, pc_insn_offset, boot_image_offset);
}

void CodeGeneratorRISCV64::EmitLinkerPatches(ArenaVector<linker::LinkerPatch>* linker_patches) {
  DCHECK(linker_patches->empty());
  size_t size =
      boot_image_method_patches_.size() +
      method_bss_entry_patches_.size() +
      boot_image_type_patches_.size() +
      type_bss_entry_patches_.size() +
      public_type_bss_entry_patches_.size() +
      package_type_bss_entry_patches_.size() +
      boot_image_string_patches_.size() +
      string_bss_entry_patches_.size() +
      boot_image_jni_entrypoint_patches_.size() +
      boot_image_other_patches_.size();
  linker_patches->reserve(size);
  if (GetCompilerOptions().IsBootImage() || GetCompilerOptions().IsBootImageExtension()) {
    EmitPcRelativeLinkerPatches<linker::LinkerPatch::RelativeMethodPatch>(
        boot_image_method_patches_, linker_patches);
    EmitPcRelativeLinkerPatches<linker::LinkerPatch::RelativeTypePatch>(
        boot_image_type_patches_, linker_patches);
    EmitPcRelativeLinkerPatches<linker::LinkerPatch::RelativeStringPatch>(
        boot_image_string_patches_, linker_patches);
  } else {
    DCHECK(boot_image_method_patches_.empty());
    DCHECK(boot_image_type_patches_.empty());
    DCHECK(boot_image_string_patches_.empty());
  }
  if (GetCompilerOptions().IsBootImage()) {
    EmitPcRelativeLinkerPatches<NoDexFileAdapter<linker::LinkerPatch::IntrinsicReferencePatch>>(
        boot_image_other_patches_, linker_patches);
  } else {
    EmitPcRelativeLinkerPatches<NoDexFileAdapter<linker::LinkerPatch::DataBimgRelRoPatch>>(
        boot_image_other_patches_, linker_patches);
  }
  EmitPcRelativeLinkerPatches<linker::LinkerPatch::MethodBssEntryPatch>(
      method_bss_entry_patches_, linker_patches);
  EmitPcRelativeLinkerPatches<linker::LinkerPatch::TypeBssEntryPatch>(
      type_bss_entry_patches_, linker_patches);
  EmitPcRelativeLinkerPatches<linker::LinkerPatch::PublicTypeBssEntryPatch>(
      public_type_bss_entry_patches_, linker_patches);
  EmitPcRelativeLinkerPatches<linker::LinkerPatch::PackageTypeBssEntryPatch>(
      package_type_bss_entry_patches_, linker_patches);
  EmitPcRelativeLinkerPatches<linker::LinkerPatch::StringBssEntryPatch>(
      string_bss_entry_patches_, linker_patches);
  EmitPcRelativeLinkerPatches<linker::LinkerPatch::RelativeJniEntrypointPatch>(
      boot_image_jni_entrypoint_patches_, linker_patches);
  DCHECK_EQ(size, linker_patches->size());
}

void CodeGeneratorRISCV64::LoadTypeForBootImageIntrinsic(XRegister dest,
                                                         TypeReference target_type) {
  // Load the type the same way as for HLoadClass::LoadKind::kBootImageLinkTimePcRelative.
  DCHECK(GetCompilerOptions().IsBootImage() || GetCompilerOptions().IsBootImageExtension());
  PcRelativePatchInfo* info_high =
      NewBootImageTypePatch(*target_type.dex_file, target_type.TypeIndex());
  EmitPcRelativeAuipcPlaceholder(info_high, dest);
  PcRelativePatchInfo* info_low =
      NewBootImageTypePatch(*target_type.dex_file, target_type.TypeIndex(), info_high);
  EmitPcRelativeAddiPlaceholder(info_low, dest, dest);
}

void CodeGeneratorRISCV64::LoadBootImageRelRoEntry(XRegister dest, uint32_t boot_image_offset) {
  PcRelativePatchInfo* info_high = NewBootImageRelRoPatch(boot_image_offset);
  EmitPcRelativeAuipcPlaceholder(info_high, dest);
  PcRelativePatchInfo* info_low = NewBootImageRelRoPatch(boot_image_offset, info_high);
  // Note: Boot image is in the low 4GiB and the entry is always 32-bit, so emit a 32-bit load.
  EmitPcRelativeLwuPlaceholder(info_low, dest, dest);
}

void CodeGeneratorRISCV64::LoadBootImageAddress(XRegister dest, uint32_t boot_image_reference) {
  if (GetCompilerOptions().IsBootImage()) {
    PcRelativePatchInfo* info_high = NewBootImageIntrinsicPatch(boot_image_reference);
    EmitPcRelativeAuipcPlaceholder(info_high, dest);
    PcRelativePatchInfo* info_low = NewBootImageIntrinsicPatch(boot_image_reference, info_high);
    EmitPcRelativeAddiPlaceholder(info_low, dest, dest);
  } else if (GetCompilerOptions().GetCompilePic()) {
    LoadBootImageRelRoEntry(dest, boot_image_reference);
  } else {
    DCHECK(GetCompilerOptions().IsJitCompiler());
    gc::Heap* heap = Runtime::Current()->GetHeap();
    DCHECK(!heap->GetBootImageSpaces().empty());
    const uint8_t* address = heap->GetBootImageSpaces()[0]->Begin() + boot_image_reference;
    // Note: Boot image is in the low 4GiB (usually the low 2GiB, requiring just LUI+ADDI).
    // We may not have an available scratch register for `LoadConst64()` but it never
    // emits better code than `Li()` for 32-bit unsigned constants anyway.
    __ Li(dest, reinterpret_cast32<uint32_t>(address));
  }
}

void CodeGeneratorRISCV64::LoadIntrinsicDeclaringClass(XRegister dest, HInvoke* invoke) {
  DCHECK_NE(invoke->GetIntrinsic(), Intrinsics::kNone);
  if (GetCompilerOptions().IsBootImage()) {
    MethodReference target_method = invoke->GetResolvedMethodReference();
    dex::TypeIndex type_idx = target_method.dex_file->GetMethodId(target_method.index).class_idx_;
    LoadTypeForBootImageIntrinsic(dest, TypeReference(target_method.dex_file, type_idx));
  } else {
    uint32_t boot_image_offset = GetBootImageOffsetOfIntrinsicDeclaringClass(invoke);
    LoadBootImageAddress(dest, boot_image_offset);
  }
}

void CodeGeneratorRISCV64::LoadClassRootForIntrinsic(XRegister dest, ClassRoot class_root) {
  if (GetCompilerOptions().IsBootImage()) {
    ScopedObjectAccess soa(Thread::Current());
    ObjPtr<mirror::Class> klass = GetClassRoot(class_root);
    TypeReference target_type(&klass->GetDexFile(), klass->GetDexTypeIndex());
    LoadTypeForBootImageIntrinsic(dest, target_type);
  } else {
    uint32_t boot_image_offset = GetBootImageOffset(class_root);
    LoadBootImageAddress(dest, boot_image_offset);
  }
}

void CodeGeneratorRISCV64::LoadMethod(MethodLoadKind load_kind, Location temp, HInvoke* invoke) {
  switch (load_kind) {
    case MethodLoadKind::kBootImageLinkTimePcRelative: {
      DCHECK(GetCompilerOptions().IsBootImage() || GetCompilerOptions().IsBootImageExtension());
      CodeGeneratorRISCV64::PcRelativePatchInfo* info_high =
          NewBootImageMethodPatch(invoke->GetResolvedMethodReference());
      EmitPcRelativeAuipcPlaceholder(info_high, temp.AsRegister<XRegister>());
      CodeGeneratorRISCV64::PcRelativePatchInfo* info_low =
          NewBootImageMethodPatch(invoke->GetResolvedMethodReference(), info_high);
      EmitPcRelativeAddiPlaceholder(
          info_low, temp.AsRegister<XRegister>(), temp.AsRegister<XRegister>());
      break;
    }
    case MethodLoadKind::kBootImageRelRo: {
      uint32_t boot_image_offset = GetBootImageOffset(invoke);
      LoadBootImageRelRoEntry(temp.AsRegister<XRegister>(), boot_image_offset);
      break;
    }
    case MethodLoadKind::kBssEntry: {
      PcRelativePatchInfo* info_high = NewMethodBssEntryPatch(invoke->GetMethodReference());
      EmitPcRelativeAuipcPlaceholder(info_high, temp.AsRegister<XRegister>());
      PcRelativePatchInfo* info_low =
          NewMethodBssEntryPatch(invoke->GetMethodReference(), info_high);
      EmitPcRelativeLdPlaceholder(
          info_low, temp.AsRegister<XRegister>(), temp.AsRegister<XRegister>());
      break;
    }
    case MethodLoadKind::kJitDirectAddress: {
      __ LoadConst64(temp.AsRegister<XRegister>(),
                     reinterpret_cast<uint64_t>(invoke->GetResolvedMethod()));
      break;
    }
    case MethodLoadKind::kRuntimeCall: {
      // Test situation, don't do anything.
      break;
    }
    default: {
      LOG(FATAL) << "Load kind should have already been handled " << load_kind;
      UNREACHABLE();
    }
  }
}

void CodeGeneratorRISCV64::GenerateStaticOrDirectCall(HInvokeStaticOrDirect* invoke,
                                                      Location temp,
                                                      SlowPathCode* slow_path) {
  // All registers are assumed to be correctly set up per the calling convention.
  Location callee_method = temp;  // For all kinds except kRecursive, callee will be in temp.

  switch (invoke->GetMethodLoadKind()) {
    case MethodLoadKind::kStringInit: {
      // temp = thread->string_init_entrypoint
      uint32_t offset =
          GetThreadOffset<kRiscv64PointerSize>(invoke->GetStringInitEntryPoint()).Int32Value();
      __ Loadd(temp.AsRegister<XRegister>(), TR, offset);
      break;
    }
    case MethodLoadKind::kRecursive:
      callee_method = invoke->GetLocations()->InAt(invoke->GetCurrentMethodIndex());
      break;
    case MethodLoadKind::kRuntimeCall:
      GenerateInvokeStaticOrDirectRuntimeCall(invoke, temp, slow_path);
      return;  // No code pointer retrieval; the runtime performs the call directly.
    case MethodLoadKind::kBootImageLinkTimePcRelative:
      DCHECK(GetCompilerOptions().IsBootImage() || GetCompilerOptions().IsBootImageExtension());
      if (invoke->GetCodePtrLocation() == CodePtrLocation::kCallCriticalNative) {
        // Do not materialize the method pointer, load directly the entrypoint.
        CodeGeneratorRISCV64::PcRelativePatchInfo* info_high =
            NewBootImageJniEntrypointPatch(invoke->GetResolvedMethodReference());
        EmitPcRelativeAuipcPlaceholder(info_high, RA);
        CodeGeneratorRISCV64::PcRelativePatchInfo* info_low =
            NewBootImageJniEntrypointPatch(invoke->GetResolvedMethodReference(), info_high);
        EmitPcRelativeLdPlaceholder(info_low, RA, RA);
        break;
      }
      FALLTHROUGH_INTENDED;
    default:
      LoadMethod(invoke->GetMethodLoadKind(), temp, invoke);
      break;
  }

  switch (invoke->GetCodePtrLocation()) {
    case CodePtrLocation::kCallSelf:
      DCHECK(!GetGraph()->HasShouldDeoptimizeFlag());
      __ Jal(&frame_entry_label_);
      RecordPcInfo(invoke, invoke->GetDexPc(), slow_path);
      break;
    case CodePtrLocation::kCallArtMethod:
      // RA = callee_method->entry_point_from_quick_compiled_code_;
      __ Loadd(RA,
               callee_method.AsRegister<XRegister>(),
               ArtMethod::EntryPointFromQuickCompiledCodeOffset(kRiscv64PointerSize).Int32Value());
      // RA()
      __ Jalr(RA);
      RecordPcInfo(invoke, invoke->GetDexPc(), slow_path);
      break;
    case CodePtrLocation::kCallCriticalNative: {
      size_t out_frame_size =
          PrepareCriticalNativeCall<CriticalNativeCallingConventionVisitorRiscv64,
                                    kNativeStackAlignment,
                                    GetCriticalNativeDirectCallFrameSize>(invoke);
      if (invoke->GetMethodLoadKind() == MethodLoadKind::kBootImageLinkTimePcRelative) {
        // Entrypoint is already loaded in RA.
      } else {
        // RA = callee_method->ptr_sized_fields_.data_;  // EntryPointFromJni
        MemberOffset offset = ArtMethod::EntryPointFromJniOffset(kRiscv64PointerSize);
        __ Loadd(RA, callee_method.AsRegister<XRegister>(), offset.Int32Value());
      }
      __ Jalr(RA);
      RecordPcInfo(invoke, invoke->GetDexPc(), slow_path);
      // The result is returned the same way in native ABI and managed ABI. No result conversion is
      // needed, see comments in `Riscv64JniCallingConvention::RequiresSmallResultTypeExtension()`.
      if (out_frame_size != 0u) {
        DecreaseFrame(out_frame_size);
      }
      break;
    }
  }

  DCHECK(!IsLeafMethod());
}

void CodeGeneratorRISCV64::MaybeGenerateInlineCacheCheck(HInstruction* instruction,
                                                         XRegister klass) {
  if (ProfilingInfoBuilder::IsInlineCacheUseful(instruction->AsInvoke(), this)) {
    ProfilingInfo* info = GetGraph()->GetProfilingInfo();
    DCHECK(info != nullptr);
    InlineCache* cache = ProfilingInfoBuilder::GetInlineCache(
        info, GetCompilerOptions(), instruction->AsInvoke());
    if (cache != nullptr) {
      uint64_t address = reinterpret_cast64<uint64_t>(cache);
      Riscv64Label done;
      // The `art_quick_update_inline_cache` expects the inline cache in T5.
      XRegister ic_reg = T5;
      ScratchRegisterScope srs(GetAssembler());
      DCHECK_EQ(srs.AvailableXRegisters(), 2u);
      srs.ExcludeXRegister(ic_reg);
      DCHECK_EQ(srs.AvailableXRegisters(), 1u);
      __ LoadConst64(ic_reg, address);
      {
        ScratchRegisterScope srs2(GetAssembler());
        XRegister tmp = srs2.AllocateXRegister();
        __ Loadd(tmp, ic_reg, InlineCache::ClassesOffset().Int32Value());
        // Fast path for a monomorphic cache.
        __ Beq(klass, tmp, &done);
      }
      InvokeRuntime(kQuickUpdateInlineCache, instruction, instruction->GetDexPc());
      __ Bind(&done);
    } else {
      // This is unexpected, but we don't guarantee stable compilation across
      // JIT runs so just warn about it.
      ScopedObjectAccess soa(Thread::Current());
      LOG(WARNING) << "Missing inline cache for " << GetGraph()->GetArtMethod()->PrettyMethod();
    }
  }
}

void CodeGeneratorRISCV64::GenerateVirtualCall(HInvokeVirtual* invoke,
                                               Location temp_location,
                                               SlowPathCode* slow_path) {
  // Use the calling convention instead of the location of the receiver, as
  // intrinsics may have put the receiver in a different register. In the intrinsics
  // slow path, the arguments have been moved to the right place, so here we are
  // guaranteed that the receiver is the first register of the calling convention.
  InvokeDexCallingConvention calling_convention;
  XRegister receiver = calling_convention.GetRegisterAt(0);
  XRegister temp = temp_location.AsRegister<XRegister>();
  MemberOffset method_offset =
      mirror::Class::EmbeddedVTableEntryOffset(invoke->GetVTableIndex(), kRiscv64PointerSize);
  MemberOffset class_offset = mirror::Object::ClassOffset();
  Offset entry_point = ArtMethod::EntryPointFromQuickCompiledCodeOffset(kRiscv64PointerSize);

  // temp = object->GetClass();
  __ Loadwu(temp, receiver, class_offset.Int32Value());
  MaybeRecordImplicitNullCheck(invoke);
  // Instead of simply (possibly) unpoisoning `temp` here, we should
  // emit a read barrier for the previous class reference load.
  // However this is not required in practice, as this is an
  // intermediate/temporary reference and because the current
  // concurrent copying collector keeps the from-space memory
  // intact/accessible until the end of the marking phase (the
  // concurrent copying collector may not in the future).
  MaybeUnpoisonHeapReference(temp);

  // If we're compiling baseline, update the inline cache.
  MaybeGenerateInlineCacheCheck(invoke, temp);

  // temp = temp->GetMethodAt(method_offset);
  __ Loadd(temp, temp, method_offset.Int32Value());
  // RA = temp->GetEntryPoint();
  __ Loadd(RA, temp, entry_point.Int32Value());
  // RA();
  __ Jalr(RA);
  RecordPcInfo(invoke, invoke->GetDexPc(), slow_path);
}

void CodeGeneratorRISCV64::MoveFromReturnRegister(Location trg, DataType::Type type) {
  if (!trg.IsValid()) {
    DCHECK_EQ(type, DataType::Type::kVoid);
    return;
  }

  DCHECK_NE(type, DataType::Type::kVoid);

  if (DataType::IsIntegralType(type) || type == DataType::Type::kReference) {
    XRegister trg_reg = trg.AsRegister<XRegister>();
    XRegister res_reg = Riscv64ReturnLocation(type).AsRegister<XRegister>();
    if (trg_reg != res_reg) {
      __ Mv(trg_reg, res_reg);
    }
  } else {
    FRegister trg_reg = trg.AsFpuRegister<FRegister>();
    FRegister res_reg = Riscv64ReturnLocation(type).AsFpuRegister<FRegister>();
    if (trg_reg != res_reg) {
      __ FMvD(trg_reg, res_reg);  // 64-bit move is OK also for `float`.
    }
  }
}

void CodeGeneratorRISCV64::PoisonHeapReference(XRegister reg) {
  __ Sub(reg, Zero, reg);  // Negate the ref.
  __ ZextW(reg, reg);      // Zero-extend the 32-bit ref.
}

void CodeGeneratorRISCV64::UnpoisonHeapReference(XRegister reg) {
  __ Sub(reg, Zero, reg);  // Negate the ref.
  __ ZextW(reg, reg);      // Zero-extend the 32-bit ref.
}

void CodeGeneratorRISCV64::MaybePoisonHeapReference(XRegister reg) {
  if (kPoisonHeapReferences) {
    PoisonHeapReference(reg);
  }
}

void CodeGeneratorRISCV64::MaybeUnpoisonHeapReference(XRegister reg) {
  if (kPoisonHeapReferences) {
    UnpoisonHeapReference(reg);
  }
}

void CodeGeneratorRISCV64::SwapLocations(Location loc1, Location loc2, DataType::Type type) {
  DCHECK(!loc1.IsConstant());
  DCHECK(!loc2.IsConstant());

  if (loc1.Equals(loc2)) {
    return;
  }

  bool is_slot1 = loc1.IsStackSlot() || loc1.IsDoubleStackSlot();
  bool is_slot2 = loc2.IsStackSlot() || loc2.IsDoubleStackSlot();
  bool is_simd1 = loc1.IsSIMDStackSlot();
  bool is_simd2 = loc2.IsSIMDStackSlot();
  bool is_fp_reg1 = loc1.IsFpuRegister();
  bool is_fp_reg2 = loc2.IsFpuRegister();

  if ((is_slot1 != is_slot2) ||
      (loc2.IsRegister() && loc1.IsRegister()) ||
      (is_fp_reg2 && is_fp_reg1)) {
    if ((is_fp_reg2 && is_fp_reg1) && GetGraph()->HasSIMD()) {
      LOG(FATAL) << "Unsupported";
      UNREACHABLE();
    }
    ScratchRegisterScope srs(GetAssembler());
    Location tmp = (is_fp_reg2 || is_fp_reg1)
        ? Location::FpuRegisterLocation(srs.AllocateFRegister())
        : Location::RegisterLocation(srs.AllocateXRegister());
    MoveLocation(tmp, loc1, type);
    MoveLocation(loc1, loc2, type);
    MoveLocation(loc2, tmp, type);
  } else if (is_slot1 && is_slot2) {
    move_resolver_.Exchange(loc1.GetStackIndex(), loc2.GetStackIndex(), loc1.IsDoubleStackSlot());
  } else if (is_simd1 && is_simd2) {
    // TODO(riscv64): Add VECTOR/SIMD later.
    UNIMPLEMENTED(FATAL) << "Vector extension is unsupported";
  } else if ((is_fp_reg1 && is_simd2) || (is_fp_reg2 && is_simd1)) {
    // TODO(riscv64): Add VECTOR/SIMD later.
    UNIMPLEMENTED(FATAL) << "Vector extension is unsupported";
  } else {
    LOG(FATAL) << "Unimplemented swap between locations " << loc1 << " and " << loc2;
  }
}

}  // namespace riscv64
}  // namespace art
