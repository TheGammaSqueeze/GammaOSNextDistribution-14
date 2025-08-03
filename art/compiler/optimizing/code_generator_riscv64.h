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

#ifndef ART_COMPILER_OPTIMIZING_CODE_GENERATOR_RISCV64_H_
#define ART_COMPILER_OPTIMIZING_CODE_GENERATOR_RISCV64_H_

#include "android-base/logging.h"
#include "arch/riscv64/registers_riscv64.h"
#include "base/macros.h"
#include "code_generator.h"
#include "driver/compiler_options.h"
#include "intrinsics_list.h"
#include "optimizing/locations.h"
#include "parallel_move_resolver.h"
#include "utils/riscv64/assembler_riscv64.h"

namespace art HIDDEN {
namespace riscv64 {

// InvokeDexCallingConvention registers
static constexpr XRegister kParameterCoreRegisters[] = {A1, A2, A3, A4, A5, A6, A7};
static constexpr size_t kParameterCoreRegistersLength = arraysize(kParameterCoreRegisters);

static constexpr FRegister kParameterFpuRegisters[] = {FA0, FA1, FA2, FA3, FA4, FA5, FA6, FA7};
static constexpr size_t kParameterFpuRegistersLength = arraysize(kParameterFpuRegisters);

// InvokeRuntimeCallingConvention registers
static constexpr XRegister kRuntimeParameterCoreRegisters[] = {A0, A1, A2, A3, A4, A5, A6, A7};
static constexpr size_t kRuntimeParameterCoreRegistersLength =
    arraysize(kRuntimeParameterCoreRegisters);

static constexpr FRegister kRuntimeParameterFpuRegisters[] = {
    FA0, FA1, FA2, FA3, FA4, FA5, FA6, FA7
};
static constexpr size_t kRuntimeParameterFpuRegistersLength =
    arraysize(kRuntimeParameterFpuRegisters);

// FCLASS returns a 10-bit classification mask with the two highest bits marking NaNs
// (signaling and quiet). To detect a NaN, we can compare (either BGE or BGEU, the sign
// bit is always clear) the result with the `kFClassNaNMinValue`.
static_assert(kSignalingNaN == 0x100);
static_assert(kQuietNaN == 0x200);
static constexpr int32_t kFClassNaNMinValue = 0x100;

#define UNIMPLEMENTED_INTRINSIC_LIST_RISCV64(V) \
  V(SystemArrayCopyByte)                        \
  V(SystemArrayCopyChar)                        \
  V(SystemArrayCopyInt)                         \
  V(FP16Ceil)                                   \
  V(FP16Compare)                                \
  V(FP16Floor)                                  \
  V(FP16Rint)                                   \
  V(FP16ToFloat)                                \
  V(FP16ToHalf)                                 \
  V(FP16Greater)                                \
  V(FP16GreaterEquals)                          \
  V(FP16Less)                                   \
  V(FP16LessEquals)                             \
  V(FP16Min)                                    \
  V(FP16Max)                                    \
  V(StringCompareTo)                            \
  V(StringEquals)                               \
  V(StringGetCharsNoCheck)                      \
  V(StringStringIndexOf)                        \
  V(StringStringIndexOfAfter)                   \
  V(StringBufferAppend)                         \
  V(StringBufferLength)                         \
  V(StringBufferToString)                       \
  V(StringBuilderAppendObject)                  \
  V(StringBuilderAppendString)                  \
  V(StringBuilderAppendCharSequence)            \
  V(StringBuilderAppendCharArray)               \
  V(StringBuilderAppendBoolean)                 \
  V(StringBuilderAppendChar)                    \
  V(StringBuilderAppendInt)                     \
  V(StringBuilderAppendLong)                    \
  V(StringBuilderAppendFloat)                   \
  V(StringBuilderAppendDouble)                  \
  V(StringBuilderLength)                        \
  V(StringBuilderToString)                      \
  V(CRC32Update)                                \
  V(CRC32UpdateBytes)                           \
  V(CRC32UpdateByteBuffer)                      \
  V(MethodHandleInvokeExact)                    \
  V(MethodHandleInvoke)

// Method register on invoke.
static const XRegister kArtMethodRegister = A0;

// Helper functions used by codegen as well as intrinsics.
XRegister InputXRegisterOrZero(Location location);
int32_t ReadBarrierMarkEntrypointOffset(Location ref);

class CodeGeneratorRISCV64;

class InvokeRuntimeCallingConvention : public CallingConvention<XRegister, FRegister> {
 public:
  InvokeRuntimeCallingConvention()
      : CallingConvention(kRuntimeParameterCoreRegisters,
                          kRuntimeParameterCoreRegistersLength,
                          kRuntimeParameterFpuRegisters,
                          kRuntimeParameterFpuRegistersLength,
                          kRiscv64PointerSize) {}

  Location GetReturnLocation(DataType::Type return_type);

 private:
  DISALLOW_COPY_AND_ASSIGN(InvokeRuntimeCallingConvention);
};

class InvokeDexCallingConvention : public CallingConvention<XRegister, FRegister> {
 public:
  InvokeDexCallingConvention()
      : CallingConvention(kParameterCoreRegisters,
                          kParameterCoreRegistersLength,
                          kParameterFpuRegisters,
                          kParameterFpuRegistersLength,
                          kRiscv64PointerSize) {}

 private:
  DISALLOW_COPY_AND_ASSIGN(InvokeDexCallingConvention);
};

class InvokeDexCallingConventionVisitorRISCV64 : public InvokeDexCallingConventionVisitor {
 public:
  InvokeDexCallingConventionVisitorRISCV64() {}
  virtual ~InvokeDexCallingConventionVisitorRISCV64() {}

  Location GetNextLocation(DataType::Type type) override;
  Location GetReturnLocation(DataType::Type type) const override;
  Location GetMethodLocation() const override;

 private:
  InvokeDexCallingConvention calling_convention;

  DISALLOW_COPY_AND_ASSIGN(InvokeDexCallingConventionVisitorRISCV64);
};

class CriticalNativeCallingConventionVisitorRiscv64 : public InvokeDexCallingConventionVisitor {
 public:
  explicit CriticalNativeCallingConventionVisitorRiscv64(bool for_register_allocation)
      : for_register_allocation_(for_register_allocation) {}

  virtual ~CriticalNativeCallingConventionVisitorRiscv64() {}

  Location GetNextLocation(DataType::Type type) override;
  Location GetReturnLocation(DataType::Type type) const override;
  Location GetMethodLocation() const override;

  size_t GetStackOffset() const { return stack_offset_; }

 private:
  // Register allocator does not support adjusting frame size, so we cannot provide final locations
  // of stack arguments for register allocation. We ask the register allocator for any location and
  // move these arguments to the right place after adjusting the SP when generating the call.
  const bool for_register_allocation_;
  size_t gpr_index_ = 0u;
  size_t fpr_index_ = 0u;
  size_t stack_offset_ = 0u;

  DISALLOW_COPY_AND_ASSIGN(CriticalNativeCallingConventionVisitorRiscv64);
};

class SlowPathCodeRISCV64 : public SlowPathCode {
 public:
  explicit SlowPathCodeRISCV64(HInstruction* instruction)
      : SlowPathCode(instruction), entry_label_(), exit_label_() {}

  Riscv64Label* GetEntryLabel() { return &entry_label_; }
  Riscv64Label* GetExitLabel() { return &exit_label_; }

 private:
  Riscv64Label entry_label_;
  Riscv64Label exit_label_;

  DISALLOW_COPY_AND_ASSIGN(SlowPathCodeRISCV64);
};

class ParallelMoveResolverRISCV64 : public ParallelMoveResolverWithSwap {
 public:
  ParallelMoveResolverRISCV64(ArenaAllocator* allocator, CodeGeneratorRISCV64* codegen)
      : ParallelMoveResolverWithSwap(allocator), codegen_(codegen) {}

  void EmitMove(size_t index) override;
  void EmitSwap(size_t index) override;
  void SpillScratch(int reg) override;
  void RestoreScratch(int reg) override;

  void Exchange(int index1, int index2, bool double_slot);

  Riscv64Assembler* GetAssembler() const;

 private:
  CodeGeneratorRISCV64* const codegen_;

  DISALLOW_COPY_AND_ASSIGN(ParallelMoveResolverRISCV64);
};

class FieldAccessCallingConventionRISCV64 : public FieldAccessCallingConvention {
 public:
  FieldAccessCallingConventionRISCV64() {}

  Location GetObjectLocation() const override {
    return Location::RegisterLocation(A1);
  }
  Location GetFieldIndexLocation() const override {
    return Location::RegisterLocation(A0);
  }
  Location GetReturnLocation(DataType::Type type ATTRIBUTE_UNUSED) const override {
    return Location::RegisterLocation(A0);
  }
  Location GetSetValueLocation(DataType::Type type ATTRIBUTE_UNUSED,
                               bool is_instance) const override {
    return is_instance
        ? Location::RegisterLocation(A2)
        : Location::RegisterLocation(A1);
  }
  Location GetFpuLocation(DataType::Type type ATTRIBUTE_UNUSED) const override {
    return Location::FpuRegisterLocation(FA0);
  }

 private:
  DISALLOW_COPY_AND_ASSIGN(FieldAccessCallingConventionRISCV64);
};

class LocationsBuilderRISCV64 : public HGraphVisitor {
 public:
  LocationsBuilderRISCV64(HGraph* graph, CodeGeneratorRISCV64* codegen)
      : HGraphVisitor(graph), codegen_(codegen) {}

#define DECLARE_VISIT_INSTRUCTION(name, super) void Visit##name(H##name* instr) override;

  FOR_EACH_CONCRETE_INSTRUCTION_COMMON(DECLARE_VISIT_INSTRUCTION)
  FOR_EACH_CONCRETE_INSTRUCTION_RISCV64(DECLARE_VISIT_INSTRUCTION)

#undef DECLARE_VISIT_INSTRUCTION

  void VisitInstruction(HInstruction* instruction) override {
    LOG(FATAL) << "Unreachable instruction " << instruction->DebugName() << " (id "
               << instruction->GetId() << ")";
  }

 protected:
  void HandleInvoke(HInvoke* invoke);
  void HandleBinaryOp(HBinaryOperation* operation);
  void HandleCondition(HCondition* instruction);
  void HandleShift(HBinaryOperation* operation);
  void HandleFieldSet(HInstruction* instruction);
  void HandleFieldGet(HInstruction* instruction);

  InvokeDexCallingConventionVisitorRISCV64 parameter_visitor_;

  CodeGeneratorRISCV64* const codegen_;

  DISALLOW_COPY_AND_ASSIGN(LocationsBuilderRISCV64);
};

class InstructionCodeGeneratorRISCV64 : public InstructionCodeGenerator {
 public:
  InstructionCodeGeneratorRISCV64(HGraph* graph, CodeGeneratorRISCV64* codegen);

#define DECLARE_VISIT_INSTRUCTION(name, super) void Visit##name(H##name* instr) override;

  FOR_EACH_CONCRETE_INSTRUCTION_COMMON(DECLARE_VISIT_INSTRUCTION)
  FOR_EACH_CONCRETE_INSTRUCTION_RISCV64(DECLARE_VISIT_INSTRUCTION)

#undef DECLARE_VISIT_INSTRUCTION

  void VisitInstruction(HInstruction* instruction) override {
    LOG(FATAL) << "Unreachable instruction " << instruction->DebugName() << " (id "
               << instruction->GetId() << ")";
  }

  Riscv64Assembler* GetAssembler() const { return assembler_; }

  void GenerateMemoryBarrier(MemBarrierKind kind);

  void FAdd(FRegister rd, FRegister rs1, FRegister rs2, DataType::Type type);
  void FClass(XRegister rd, FRegister rs1, DataType::Type type);

  void Load(Location out, XRegister rs1, int32_t offset, DataType::Type type);
  void Store(Location value, XRegister rs1, int32_t offset, DataType::Type type);

  // Sequentially consistent store. Used for volatile fields and intrinsics.
  // The `instruction` argument is for recording an implicit null check stack map with the
  // store instruction which may not be the last instruction emitted by `StoreSeqCst()`.
  void StoreSeqCst(Location value,
                   XRegister rs1,
                   int32_t offset,
                   DataType::Type type,
                   HInstruction* instruction = nullptr);

  void ShNAdd(XRegister rd, XRegister rs1, XRegister rs2, DataType::Type type);

 protected:
  void GenerateClassInitializationCheck(SlowPathCodeRISCV64* slow_path, XRegister class_reg);
  void GenerateBitstringTypeCheckCompare(HTypeCheckInstruction* check, XRegister temp);
  void GenerateSuspendCheck(HSuspendCheck* check, HBasicBlock* successor);
  void HandleBinaryOp(HBinaryOperation* operation);
  void HandleCondition(HCondition* instruction);
  void HandleShift(HBinaryOperation* operation);
  void HandleFieldSet(HInstruction* instruction,
                      const FieldInfo& field_info,
                      bool value_can_be_null,
                      WriteBarrierKind write_barrier_kind);
  void HandleFieldGet(HInstruction* instruction, const FieldInfo& field_info);

  // Generate a heap reference load using one register `out`:
  //
  //   out <- *(out + offset)
  //
  // while honoring heap poisoning and/or read barriers (if any).
  //
  // Location `maybe_temp` is used when generating a read barrier and
  // shall be a register in that case; it may be an invalid location
  // otherwise.
  void GenerateReferenceLoadOneRegister(HInstruction* instruction,
                                        Location out,
                                        uint32_t offset,
                                        Location maybe_temp,
                                        ReadBarrierOption read_barrier_option);
  // Generate a heap reference load using two different registers
  // `out` and `obj`:
  //
  //   out <- *(obj + offset)
  //
  // while honoring heap poisoning and/or read barriers (if any).
  //
  // Location `maybe_temp` is used when generating a Baker's (fast
  // path) read barrier and shall be a register in that case; it may
  // be an invalid location otherwise.
  void GenerateReferenceLoadTwoRegisters(HInstruction* instruction,
                                         Location out,
                                         Location obj,
                                         uint32_t offset,
                                         Location maybe_temp,
                                         ReadBarrierOption read_barrier_option);

  void GenerateTestAndBranch(HInstruction* instruction,
                             size_t condition_input_index,
                             Riscv64Label* true_target,
                             Riscv64Label* false_target);
  void DivRemOneOrMinusOne(HBinaryOperation* instruction);
  void DivRemByPowerOfTwo(HBinaryOperation* instruction);
  void GenerateDivRemWithAnyConstant(HBinaryOperation* instruction);
  void GenerateDivRemIntegral(HBinaryOperation* instruction);
  void GenerateIntLongCondition(IfCondition cond, LocationSummary* locations);
  void GenerateIntLongCondition(IfCondition cond,
                                LocationSummary* locations,
                                XRegister rd,
                                bool to_all_bits);
  void GenerateIntLongCompareAndBranch(IfCondition cond,
                                       LocationSummary* locations,
                                       Riscv64Label* label);
  void GenerateFpCondition(IfCondition cond,
                           bool gt_bias,
                           DataType::Type type,
                           LocationSummary* locations,
                           Riscv64Label* label = nullptr);
  void GenerateFpCondition(IfCondition cond,
                           bool gt_bias,
                           DataType::Type type,
                           LocationSummary* locations,
                           Riscv64Label* label,
                           XRegister rd,
                           bool to_all_bits);
  void GenerateMethodEntryExitHook(HInstruction* instruction);
  void HandleGoto(HInstruction* got, HBasicBlock* successor);
  void GenPackedSwitchWithCompares(XRegister adjusted,
                                   XRegister temp,
                                   uint32_t num_entries,
                                   HBasicBlock* switch_block);
  void GenTableBasedPackedSwitch(XRegister adjusted,
                                 XRegister temp,
                                 uint32_t num_entries,
                                 HBasicBlock* switch_block);
  int32_t VecAddress(LocationSummary* locations,
                     size_t size,
                     /*out*/ XRegister* adjusted_base);

  template <typename Reg,
            void (Riscv64Assembler::*opS)(Reg, FRegister, FRegister),
            void (Riscv64Assembler::*opD)(Reg, FRegister, FRegister)>
  void FpBinOp(Reg rd, FRegister rs1, FRegister rs2, DataType::Type type);
  void FSub(FRegister rd, FRegister rs1, FRegister rs2, DataType::Type type);
  void FDiv(FRegister rd, FRegister rs1, FRegister rs2, DataType::Type type);
  void FMul(FRegister rd, FRegister rs1, FRegister rs2, DataType::Type type);
  void FMin(FRegister rd, FRegister rs1, FRegister rs2, DataType::Type type);
  void FMax(FRegister rd, FRegister rs1, FRegister rs2, DataType::Type type);
  void FEq(XRegister rd, FRegister rs1, FRegister rs2, DataType::Type type);
  void FLt(XRegister rd, FRegister rs1, FRegister rs2, DataType::Type type);
  void FLe(XRegister rd, FRegister rs1, FRegister rs2, DataType::Type type);

  template <typename Reg,
            void (Riscv64Assembler::*opS)(Reg, FRegister),
            void (Riscv64Assembler::*opD)(Reg, FRegister)>
  void FpUnOp(Reg rd, FRegister rs1, DataType::Type type);
  void FAbs(FRegister rd, FRegister rs1, DataType::Type type);
  void FNeg(FRegister rd, FRegister rs1, DataType::Type type);
  void FMv(FRegister rd, FRegister rs1, DataType::Type type);
  void FMvX(XRegister rd, FRegister rs1, DataType::Type type);

  Riscv64Assembler* const assembler_;
  CodeGeneratorRISCV64* const codegen_;

  DISALLOW_COPY_AND_ASSIGN(InstructionCodeGeneratorRISCV64);
};

class CodeGeneratorRISCV64 : public CodeGenerator {
 public:
  CodeGeneratorRISCV64(HGraph* graph,
                       const CompilerOptions& compiler_options,
                       OptimizingCompilerStats* stats = nullptr);
  virtual ~CodeGeneratorRISCV64() {}

  void GenerateFrameEntry() override;
  void GenerateFrameExit() override;

  void Bind(HBasicBlock* block) override;

  size_t GetWordSize() const override {
    // The "word" for the compiler is the core register size (64-bit for riscv64) while the
    // riscv64 assembler uses "word" for 32-bit values and "double word" for 64-bit values.
    return kRiscv64DoublewordSize;
  }

  bool SupportsPredicatedSIMD() const override {
    // TODO(riscv64): Check the vector extension.
    return false;
  }

  // Get FP register width in bytes for spilling/restoring in the slow paths.
  //
  // Note: In SIMD graphs this should return SIMD register width as all FP and SIMD registers
  // alias and live SIMD registers are forced to be spilled in full size in the slow paths.
  size_t GetSlowPathFPWidth() const override {
    // Default implementation.
    return GetCalleePreservedFPWidth();
  }

  size_t GetCalleePreservedFPWidth() const override {
    return kRiscv64FloatRegSizeInBytes;
  };

  size_t GetSIMDRegisterWidth() const override {
    // TODO(riscv64): Implement SIMD with the Vector extension.
    // Note: HLoopOptimization calls this function even for an ISA without SIMD support.
    return kRiscv64FloatRegSizeInBytes;
  };

  uintptr_t GetAddressOf(HBasicBlock* block) override {
    return assembler_.GetLabelLocation(GetLabelOf(block));
  };

  Riscv64Label* GetLabelOf(HBasicBlock* block) const {
    return CommonGetLabelOf<Riscv64Label>(block_labels_, block);
  }

  void Initialize() override { block_labels_ = CommonInitializeLabels<Riscv64Label>(); }

  void MoveConstant(Location destination, int32_t value) override;
  void MoveLocation(Location destination, Location source, DataType::Type dst_type) override;
  void AddLocationAsTemp(Location location, LocationSummary* locations) override;

  Riscv64Assembler* GetAssembler() override { return &assembler_; }
  const Riscv64Assembler& GetAssembler() const override { return assembler_; }

  HGraphVisitor* GetLocationBuilder() override { return &location_builder_; }

  InstructionCodeGeneratorRISCV64* GetInstructionVisitor() override {
    return &instruction_visitor_;
  }

  void MaybeGenerateInlineCacheCheck(HInstruction* instruction, XRegister klass);

  void SetupBlockedRegisters() const override;

  size_t SaveCoreRegister(size_t stack_index, uint32_t reg_id) override;
  size_t RestoreCoreRegister(size_t stack_index, uint32_t reg_id) override;
  size_t SaveFloatingPointRegister(size_t stack_index, uint32_t reg_id) override;
  size_t RestoreFloatingPointRegister(size_t stack_index, uint32_t reg_id) override;

  void DumpCoreRegister(std::ostream& stream, int reg) const override;
  void DumpFloatingPointRegister(std::ostream& stream, int reg) const override;

  InstructionSet GetInstructionSet() const override { return InstructionSet::kRiscv64; }

  const Riscv64InstructionSetFeatures& GetInstructionSetFeatures() const;

  uint32_t GetPreferredSlotsAlignment() const override {
    return static_cast<uint32_t>(kRiscv64PointerSize);
  }

  void Finalize() override;

  // Generate code to invoke a runtime entry point.
  void InvokeRuntime(QuickEntrypointEnum entrypoint,
                     HInstruction* instruction,
                     uint32_t dex_pc,
                     SlowPathCode* slow_path = nullptr) override;

  // Generate code to invoke a runtime entry point, but do not record
  // PC-related information in a stack map.
  void InvokeRuntimeWithoutRecordingPcInfo(int32_t entry_point_offset,
                                           HInstruction* instruction,
                                           SlowPathCode* slow_path);

  ParallelMoveResolver* GetMoveResolver() override { return &move_resolver_; }

  bool NeedsTwoRegisters([[maybe_unused]] DataType::Type type) const override { return false; }

  void IncreaseFrame(size_t adjustment) override;
  void DecreaseFrame(size_t adjustment) override;

  void GenerateNop() override;

  void GenerateImplicitNullCheck(HNullCheck* instruction) override;
  void GenerateExplicitNullCheck(HNullCheck* instruction) override;

  // Check if the desired_string_load_kind is supported. If it is, return it,
  // otherwise return a fall-back kind that should be used instead.
  HLoadString::LoadKind GetSupportedLoadStringKind(
      HLoadString::LoadKind desired_string_load_kind) override;

  // Check if the desired_class_load_kind is supported. If it is, return it,
  // otherwise return a fall-back kind that should be used instead.
  HLoadClass::LoadKind GetSupportedLoadClassKind(
      HLoadClass::LoadKind desired_class_load_kind) override;

  // Check if the desired_dispatch_info is supported. If it is, return it,
  // otherwise return a fall-back info that should be used instead.
  HInvokeStaticOrDirect::DispatchInfo GetSupportedInvokeStaticOrDirectDispatch(
      const HInvokeStaticOrDirect::DispatchInfo& desired_dispatch_info, ArtMethod* method) override;

  // The PcRelativePatchInfo is used for PC-relative addressing of methods/strings/types,
  // whether through .data.bimg.rel.ro, .bss, or directly in the boot image.
  //
  // The 20-bit and 12-bit parts of the 32-bit PC-relative offset are patched separately,
  // necessitating two patches/infos. There can be more than two patches/infos if the
  // instruction supplying the high part is shared with e.g. a slow path, while the low
  // part is supplied by separate instructions, e.g.:
  //     auipc r1, high       // patch
  //     lwu   r2, low(r1)    // patch
  //     beqz  r2, slow_path
  //   back:
  //     ...
  //   slow_path:
  //     ...
  //     sw    r2, low(r1)    // patch
  //     j     back
  struct PcRelativePatchInfo : PatchInfo<Riscv64Label> {
    PcRelativePatchInfo(const DexFile* dex_file,
                        uint32_t off_or_idx,
                        const PcRelativePatchInfo* info_high)
        : PatchInfo<Riscv64Label>(dex_file, off_or_idx),
          pc_insn_label(info_high != nullptr ? &info_high->label : &label) {
      DCHECK_IMPLIES(info_high != nullptr, info_high->pc_insn_label == &info_high->label);
    }

    // Pointer to the info for the high part patch or nullptr if this is the high part patch info.
    const Riscv64Label* pc_insn_label;

   private:
    PcRelativePatchInfo(PcRelativePatchInfo&& other) = delete;
    DISALLOW_COPY_AND_ASSIGN(PcRelativePatchInfo);
  };

  PcRelativePatchInfo* NewBootImageIntrinsicPatch(uint32_t intrinsic_data,
                                                  const PcRelativePatchInfo* info_high = nullptr);
  PcRelativePatchInfo* NewBootImageRelRoPatch(uint32_t boot_image_offset,
                                              const PcRelativePatchInfo* info_high = nullptr);
  PcRelativePatchInfo* NewBootImageMethodPatch(MethodReference target_method,
                                               const PcRelativePatchInfo* info_high = nullptr);
  PcRelativePatchInfo* NewMethodBssEntryPatch(MethodReference target_method,
                                              const PcRelativePatchInfo* info_high = nullptr);
  PcRelativePatchInfo* NewBootImageJniEntrypointPatch(
      MethodReference target_method, const PcRelativePatchInfo* info_high = nullptr);

  PcRelativePatchInfo* NewBootImageTypePatch(const DexFile& dex_file,
                                             dex::TypeIndex type_index,
                                             const PcRelativePatchInfo* info_high = nullptr);
  PcRelativePatchInfo* NewTypeBssEntryPatch(HLoadClass* load_class,
                                            const PcRelativePatchInfo* info_high = nullptr);
  PcRelativePatchInfo* NewBootImageStringPatch(const DexFile& dex_file,
                                               dex::StringIndex string_index,
                                               const PcRelativePatchInfo* info_high = nullptr);
  PcRelativePatchInfo* NewStringBssEntryPatch(const DexFile& dex_file,
                                              dex::StringIndex string_index,
                                              const PcRelativePatchInfo* info_high = nullptr);

  void EmitPcRelativeAuipcPlaceholder(PcRelativePatchInfo* info_high, XRegister out);
  void EmitPcRelativeAddiPlaceholder(PcRelativePatchInfo* info_low, XRegister rd, XRegister rs1);
  void EmitPcRelativeLwuPlaceholder(PcRelativePatchInfo* info_low, XRegister rd, XRegister rs1);
  void EmitPcRelativeLdPlaceholder(PcRelativePatchInfo* info_low, XRegister rd, XRegister rs1);

  void EmitLinkerPatches(ArenaVector<linker::LinkerPatch>* linker_patches) override;

  Literal* DeduplicateBootImageAddressLiteral(uint64_t address);
  void PatchJitRootUse(uint8_t* code,
                       const uint8_t* roots_data,
                       const Literal* literal,
                       uint64_t index_in_table) const;
  Literal* DeduplicateJitStringLiteral(const DexFile& dex_file,
                                       dex::StringIndex string_index,
                                       Handle<mirror::String> handle);
  Literal* DeduplicateJitClassLiteral(const DexFile& dex_file,
                                      dex::TypeIndex type_index,
                                      Handle<mirror::Class> handle);
  void EmitJitRootPatches(uint8_t* code, const uint8_t* roots_data) override;

  void LoadTypeForBootImageIntrinsic(XRegister dest, TypeReference target_type);
  void LoadBootImageRelRoEntry(XRegister dest, uint32_t boot_image_offset);
  void LoadBootImageAddress(XRegister dest, uint32_t boot_image_reference);
  void LoadIntrinsicDeclaringClass(XRegister dest, HInvoke* invoke);
  void LoadClassRootForIntrinsic(XRegister dest, ClassRoot class_root);

  void LoadMethod(MethodLoadKind load_kind, Location temp, HInvoke* invoke);
  void GenerateStaticOrDirectCall(HInvokeStaticOrDirect* invoke,
                                  Location temp,
                                  SlowPathCode* slow_path = nullptr) override;
  void GenerateVirtualCall(HInvokeVirtual* invoke,
                           Location temp,
                           SlowPathCode* slow_path = nullptr) override;
  void MoveFromReturnRegister(Location trg, DataType::Type type) override;

  void GenerateMemoryBarrier(MemBarrierKind kind);

  void MaybeIncrementHotness(HSuspendCheck* suspend_check, bool is_frame_entry);

  bool CanUseImplicitSuspendCheck() const;


  // Create slow path for a Baker read barrier for a GC root load within `instruction`.
  SlowPathCodeRISCV64* AddGcRootBakerBarrierBarrierSlowPath(
      HInstruction* instruction, Location root, Location temp);

  // Emit marking check for a Baker read barrier for a GC root load within `instruction`.
  void EmitBakerReadBarierMarkingCheck(
      SlowPathCodeRISCV64* slow_path, Location root, Location temp);

  // Generate a GC root reference load:
  //
  //   root <- *(obj + offset)
  //
  // while honoring read barriers (if any).
  void GenerateGcRootFieldLoad(HInstruction* instruction,
                               Location root,
                               XRegister obj,
                               uint32_t offset,
                               ReadBarrierOption read_barrier_option,
                               Riscv64Label* label_low = nullptr);

  // Fast path implementation of ReadBarrier::Barrier for a heap
  // reference field load when Baker's read barriers are used.
  void GenerateFieldLoadWithBakerReadBarrier(HInstruction* instruction,
                                             Location ref,
                                             XRegister obj,
                                             uint32_t offset,
                                             Location temp,
                                             bool needs_null_check);
  // Fast path implementation of ReadBarrier::Barrier for a heap
  // reference array load when Baker's read barriers are used.
  void GenerateArrayLoadWithBakerReadBarrier(HInstruction* instruction,
                                             Location ref,
                                             XRegister obj,
                                             uint32_t data_offset,
                                             Location index,
                                             Location temp,
                                             bool needs_null_check);
  // Factored implementation, used by GenerateFieldLoadWithBakerReadBarrier,
  // GenerateArrayLoadWithBakerReadBarrier and intrinsics.
  void GenerateReferenceLoadWithBakerReadBarrier(HInstruction* instruction,
                                                 Location ref,
                                                 XRegister obj,
                                                 uint32_t offset,
                                                 Location index,
                                                 Location temp,
                                                 bool needs_null_check);

  // Create slow path for a read barrier for a heap reference within `instruction`.
  //
  // This is a helper function for GenerateReadBarrierSlow() that has the same
  // arguments. The creation and adding of the slow path is exposed for intrinsics
  // that cannot use GenerateReadBarrierSlow() from their own slow paths.
  SlowPathCodeRISCV64* AddReadBarrierSlowPath(HInstruction* instruction,
                                              Location out,
                                              Location ref,
                                              Location obj,
                                              uint32_t offset,
                                              Location index);

  // Generate a read barrier for a heap reference within `instruction`
  // using a slow path.
  //
  // A read barrier for an object reference read from the heap is
  // implemented as a call to the artReadBarrierSlow runtime entry
  // point, which is passed the values in locations `ref`, `obj`, and
  // `offset`:
  //
  //   mirror::Object* artReadBarrierSlow(mirror::Object* ref,
  //                                      mirror::Object* obj,
  //                                      uint32_t offset);
  //
  // The `out` location contains the value returned by
  // artReadBarrierSlow.
  //
  // When `index` is provided (i.e. for array accesses), the offset
  // value passed to artReadBarrierSlow is adjusted to take `index`
  // into account.
  void GenerateReadBarrierSlow(HInstruction* instruction,
                               Location out,
                               Location ref,
                               Location obj,
                               uint32_t offset,
                               Location index = Location::NoLocation());

  // If read barriers are enabled, generate a read barrier for a heap
  // reference using a slow path. If heap poisoning is enabled, also
  // unpoison the reference in `out`.
  void MaybeGenerateReadBarrierSlow(HInstruction* instruction,
                                    Location out,
                                    Location ref,
                                    Location obj,
                                    uint32_t offset,
                                    Location index = Location::NoLocation());

  // Generate a read barrier for a GC root within `instruction` using
  // a slow path.
  //
  // A read barrier for an object reference GC root is implemented as
  // a call to the artReadBarrierForRootSlow runtime entry point,
  // which is passed the value in location `root`:
  //
  //   mirror::Object* artReadBarrierForRootSlow(GcRoot<mirror::Object>* root);
  //
  // The `out` location contains the value returned by
  // artReadBarrierForRootSlow.
  void GenerateReadBarrierForRootSlow(HInstruction* instruction, Location out, Location root);

  // Emit a write barrier if:
  // A) emit_null_check is false
  // B) emit_null_check is true, and value is not null.
  void MaybeMarkGCCard(XRegister object, XRegister value, bool emit_null_check);

  // Emit a write barrier unconditionally.
  void MarkGCCard(XRegister object);

  // Crash if the card table is not valid. This check is only emitted for the CC GC. We assert
  // `(!clean || !self->is_gc_marking)`, since the card table should not be set to clean when the CC
  // GC is marking for eliminated write barriers.
  void CheckGCCardIsValid(XRegister object);

  //
  // Heap poisoning.
  //

  // Poison a heap reference contained in `reg`.
  void PoisonHeapReference(XRegister reg);

  // Unpoison a heap reference contained in `reg`.
  void UnpoisonHeapReference(XRegister reg);

  // Poison a heap reference contained in `reg` if heap poisoning is enabled.
  void MaybePoisonHeapReference(XRegister reg);

  // Unpoison a heap reference contained in `reg` if heap poisoning is enabled.
  void MaybeUnpoisonHeapReference(XRegister reg);

  void SwapLocations(Location loc1, Location loc2, DataType::Type type);

 private:
  using Uint32ToLiteralMap = ArenaSafeMap<uint32_t, Literal*>;
  using Uint64ToLiteralMap = ArenaSafeMap<uint64_t, Literal*>;
  using StringToLiteralMap =
      ArenaSafeMap<StringReference, Literal*, StringReferenceValueComparator>;
  using TypeToLiteralMap = ArenaSafeMap<TypeReference, Literal*, TypeReferenceValueComparator>;

  Literal* DeduplicateUint32Literal(uint32_t value);
  Literal* DeduplicateUint64Literal(uint64_t value);

  PcRelativePatchInfo* NewPcRelativePatch(const DexFile* dex_file,
                                          uint32_t offset_or_index,
                                          const PcRelativePatchInfo* info_high,
                                          ArenaDeque<PcRelativePatchInfo>* patches);

  template <linker::LinkerPatch (*Factory)(size_t, const DexFile*, uint32_t, uint32_t)>
  void EmitPcRelativeLinkerPatches(const ArenaDeque<PcRelativePatchInfo>& infos,
                                   ArenaVector<linker::LinkerPatch>* linker_patches);

  Riscv64Assembler assembler_;
  LocationsBuilderRISCV64 location_builder_;
  InstructionCodeGeneratorRISCV64 instruction_visitor_;
  Riscv64Label frame_entry_label_;

  // Labels for each block that will be compiled.
  Riscv64Label* block_labels_;  // Indexed by block id.

  ParallelMoveResolverRISCV64 move_resolver_;

  // Deduplication map for 32-bit literals, used for non-patchable boot image addresses.
  Uint32ToLiteralMap uint32_literals_;
  // Deduplication map for 64-bit literals, used for non-patchable method address or method code
  // address.
  Uint64ToLiteralMap uint64_literals_;

  // PC-relative method patch info for kBootImageLinkTimePcRelative.
  ArenaDeque<PcRelativePatchInfo> boot_image_method_patches_;
  // PC-relative method patch info for kBssEntry.
  ArenaDeque<PcRelativePatchInfo> method_bss_entry_patches_;
  // PC-relative type patch info for kBootImageLinkTimePcRelative.
  ArenaDeque<PcRelativePatchInfo> boot_image_type_patches_;
  // PC-relative type patch info for kBssEntry.
  ArenaDeque<PcRelativePatchInfo> type_bss_entry_patches_;
  // PC-relative public type patch info for kBssEntryPublic.
  ArenaDeque<PcRelativePatchInfo> public_type_bss_entry_patches_;
  // PC-relative package type patch info for kBssEntryPackage.
  ArenaDeque<PcRelativePatchInfo> package_type_bss_entry_patches_;
  // PC-relative String patch info for kBootImageLinkTimePcRelative.
  ArenaDeque<PcRelativePatchInfo> boot_image_string_patches_;
  // PC-relative String patch info for kBssEntry.
  ArenaDeque<PcRelativePatchInfo> string_bss_entry_patches_;
  // PC-relative method patch info for kBootImageLinkTimePcRelative+kCallCriticalNative.
  ArenaDeque<PcRelativePatchInfo> boot_image_jni_entrypoint_patches_;
  // PC-relative patch info for IntrinsicObjects for the boot image,
  // and for method/type/string patches for kBootImageRelRo otherwise.
  ArenaDeque<PcRelativePatchInfo> boot_image_other_patches_;

  // Patches for string root accesses in JIT compiled code.
  StringToLiteralMap jit_string_patches_;
  // Patches for class root accesses in JIT compiled code.
  TypeToLiteralMap jit_class_patches_;
};

}  // namespace riscv64
}  // namespace art

#endif  // ART_COMPILER_OPTIMIZING_CODE_GENERATOR_RISCV64_H_
