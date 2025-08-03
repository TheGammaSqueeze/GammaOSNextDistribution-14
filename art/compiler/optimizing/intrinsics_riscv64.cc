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

#include "intrinsics_riscv64.h"

#include "code_generator_riscv64.h"
#include "intrinsic_objects.h"
#include "intrinsics_utils.h"
#include "well_known_classes.h"

namespace art HIDDEN {
namespace riscv64 {

using IntrinsicSlowPathRISCV64 = IntrinsicSlowPath<InvokeDexCallingConventionVisitorRISCV64,
                                                   SlowPathCodeRISCV64,
                                                   Riscv64Assembler>;

#define __ assembler->

// Slow path implementing the SystemArrayCopy intrinsic copy loop with read barriers.
class ReadBarrierSystemArrayCopySlowPathRISCV64 : public SlowPathCodeRISCV64 {
 public:
  ReadBarrierSystemArrayCopySlowPathRISCV64(HInstruction* instruction, Location tmp)
      : SlowPathCodeRISCV64(instruction), tmp_(tmp) {}

  void EmitNativeCode(CodeGenerator* codegen_in) override {
    DCHECK(codegen_in->EmitBakerReadBarrier());
    CodeGeneratorRISCV64* codegen = down_cast<CodeGeneratorRISCV64*>(codegen_in);
    Riscv64Assembler* assembler = codegen->GetAssembler();
    LocationSummary* locations = instruction_->GetLocations();
    DCHECK(locations->CanCall());
    DCHECK(instruction_->IsInvokeStaticOrDirect())
        << "Unexpected instruction in read barrier arraycopy slow path: "
        << instruction_->DebugName();
    DCHECK(instruction_->GetLocations()->Intrinsified());
    DCHECK_EQ(instruction_->AsInvoke()->GetIntrinsic(), Intrinsics::kSystemArrayCopy);

    const int32_t element_size = DataType::Size(DataType::Type::kReference);

    XRegister src_curr_addr = locations->GetTemp(0).AsRegister<XRegister>();
    XRegister dst_curr_addr = locations->GetTemp(1).AsRegister<XRegister>();
    XRegister src_stop_addr = locations->GetTemp(2).AsRegister<XRegister>();
    XRegister tmp_reg = tmp_.AsRegister<XRegister>();

    __ Bind(GetEntryLabel());
    // The source range and destination pointer were initialized before entering the slow-path.
    Riscv64Label slow_copy_loop;
    __ Bind(&slow_copy_loop);
    __ Loadwu(tmp_reg, src_curr_addr, 0);
    codegen->MaybeUnpoisonHeapReference(tmp_reg);
    // TODO: Inline the mark bit check before calling the runtime?
    // tmp_reg = ReadBarrier::Mark(tmp_reg);
    // No need to save live registers; it's taken care of by the
    // entrypoint. Also, there is no need to update the stack mask,
    // as this runtime call will not trigger a garbage collection.
    // (See ReadBarrierMarkSlowPathRISCV64::EmitNativeCode for more
    // explanations.)
    int32_t entry_point_offset = ReadBarrierMarkEntrypointOffset(tmp_);
    // This runtime call does not require a stack map.
    codegen->InvokeRuntimeWithoutRecordingPcInfo(entry_point_offset, instruction_, this);
    codegen->MaybePoisonHeapReference(tmp_reg);
    __ Storew(tmp_reg, dst_curr_addr, 0);
    __ Addi(src_curr_addr, src_curr_addr, element_size);
    __ Addi(dst_curr_addr, dst_curr_addr, element_size);
    __ Bne(src_curr_addr, src_stop_addr, &slow_copy_loop);
    __ J(GetExitLabel());
  }

  const char* GetDescription() const override {
    return "ReadBarrierSystemArrayCopySlowPathRISCV64";
  }

 private:
  Location tmp_;

  DISALLOW_COPY_AND_ASSIGN(ReadBarrierSystemArrayCopySlowPathRISCV64);
};

bool IntrinsicLocationsBuilderRISCV64::TryDispatch(HInvoke* invoke) {
  Dispatch(invoke);
  LocationSummary* res = invoke->GetLocations();
  if (res == nullptr) {
    return false;
  }
  return res->Intrinsified();
}

Riscv64Assembler* IntrinsicCodeGeneratorRISCV64::GetAssembler() {
  return codegen_->GetAssembler();
}

static void CreateFPToIntLocations(ArenaAllocator* allocator, HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::RequiresFpuRegister());
  locations->SetOut(Location::RequiresRegister());
}

static void CreateIntToFPLocations(ArenaAllocator* allocator, HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::RequiresRegister());
  locations->SetOut(Location::RequiresFpuRegister());
}

static void CreateFPToFPCallLocations(ArenaAllocator* allocator, HInvoke* invoke) {
  DCHECK_EQ(invoke->GetNumberOfArguments(), 1U);
  DCHECK(DataType::IsFloatingPointType(invoke->InputAt(0)->GetType()));
  DCHECK(DataType::IsFloatingPointType(invoke->GetType()));

  LocationSummary* const locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kCallOnMainOnly, kIntrinsified);
  InvokeRuntimeCallingConvention calling_convention;

  locations->SetInAt(0, Location::FpuRegisterLocation(calling_convention.GetFpuRegisterAt(0)));
  locations->SetOut(calling_convention.GetReturnLocation(invoke->GetType()));
}

static void CreateFPFPToFPCallLocations(ArenaAllocator* allocator, HInvoke* invoke) {
  DCHECK_EQ(invoke->GetNumberOfArguments(), 2U);
  DCHECK(DataType::IsFloatingPointType(invoke->InputAt(0)->GetType()));
  DCHECK(DataType::IsFloatingPointType(invoke->InputAt(1)->GetType()));
  DCHECK(DataType::IsFloatingPointType(invoke->GetType()));

  LocationSummary* const locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kCallOnMainOnly, kIntrinsified);
  InvokeRuntimeCallingConvention calling_convention;

  locations->SetInAt(0, Location::FpuRegisterLocation(calling_convention.GetFpuRegisterAt(0)));
  locations->SetInAt(1, Location::FpuRegisterLocation(calling_convention.GetFpuRegisterAt(1)));
  locations->SetOut(calling_convention.GetReturnLocation(invoke->GetType()));
}

static void CreateFpFpFpToFpNoOverlapLocations(ArenaAllocator* allocator, HInvoke* invoke) {
  DCHECK_EQ(invoke->GetNumberOfArguments(), 3U);
  DCHECK(DataType::IsFloatingPointType(invoke->InputAt(0)->GetType()));
  DCHECK(DataType::IsFloatingPointType(invoke->InputAt(1)->GetType()));
  DCHECK(DataType::IsFloatingPointType(invoke->InputAt(2)->GetType()));
  DCHECK(DataType::IsFloatingPointType(invoke->GetType()));

  LocationSummary* const locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);

  locations->SetInAt(0, Location::RequiresFpuRegister());
  locations->SetInAt(1, Location::RequiresFpuRegister());
  locations->SetInAt(2, Location::RequiresFpuRegister());
  locations->SetOut(Location::RequiresFpuRegister(), Location::kNoOutputOverlap);
}

static void CreateFPToFPLocations(ArenaAllocator* allocator, HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::RequiresFpuRegister());
  locations->SetOut(Location::RequiresFpuRegister());
}

void IntrinsicLocationsBuilderRISCV64::VisitDoubleDoubleToRawLongBits(HInvoke* invoke) {
  CreateFPToIntLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitDoubleDoubleToRawLongBits(HInvoke* invoke) {
  LocationSummary* locations = invoke->GetLocations();
  Riscv64Assembler* assembler = GetAssembler();
  __ FMvXD(locations->Out().AsRegister<XRegister>(), locations->InAt(0).AsFpuRegister<FRegister>());
}

void IntrinsicLocationsBuilderRISCV64::VisitDoubleLongBitsToDouble(HInvoke* invoke) {
  CreateIntToFPLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitDoubleLongBitsToDouble(HInvoke* invoke) {
  LocationSummary* locations = invoke->GetLocations();
  Riscv64Assembler* assembler = GetAssembler();
  __ FMvDX(locations->Out().AsFpuRegister<FRegister>(), locations->InAt(0).AsRegister<XRegister>());
}

void IntrinsicLocationsBuilderRISCV64::VisitFloatFloatToRawIntBits(HInvoke* invoke) {
  CreateFPToIntLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitFloatFloatToRawIntBits(HInvoke* invoke) {
  LocationSummary* locations = invoke->GetLocations();
  Riscv64Assembler* assembler = GetAssembler();
  __ FMvXW(locations->Out().AsRegister<XRegister>(), locations->InAt(0).AsFpuRegister<FRegister>());
}

void IntrinsicLocationsBuilderRISCV64::VisitFloatIntBitsToFloat(HInvoke* invoke) {
  CreateIntToFPLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitFloatIntBitsToFloat(HInvoke* invoke) {
  LocationSummary* locations = invoke->GetLocations();
  Riscv64Assembler* assembler = GetAssembler();
  __ FMvWX(locations->Out().AsFpuRegister<FRegister>(), locations->InAt(0).AsRegister<XRegister>());
}

void IntrinsicLocationsBuilderRISCV64::VisitDoubleIsInfinite(HInvoke* invoke) {
  CreateFPToIntLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitDoubleIsInfinite(HInvoke* invoke) {
  LocationSummary* locations = invoke->GetLocations();
  Riscv64Assembler* assembler = GetAssembler();
  XRegister out = locations->Out().AsRegister<XRegister>();
  __ FClassD(out, locations->InAt(0).AsFpuRegister<FRegister>());
  __ Andi(out, out, kPositiveInfinity | kNegativeInfinity);
  __ Snez(out, out);
}

void IntrinsicLocationsBuilderRISCV64::VisitFloatIsInfinite(HInvoke* invoke) {
  CreateFPToIntLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitFloatIsInfinite(HInvoke* invoke) {
  LocationSummary* locations = invoke->GetLocations();
  Riscv64Assembler* assembler = GetAssembler();
  XRegister out = locations->Out().AsRegister<XRegister>();
  __ FClassS(out, locations->InAt(0).AsFpuRegister<FRegister>());
  __ Andi(out, out, kPositiveInfinity | kNegativeInfinity);
  __ Snez(out, out);
}

static void CreateIntToIntNoOverlapLocations(ArenaAllocator* allocator, HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::RequiresRegister());
  locations->SetOut(Location::RequiresRegister(), Location::kNoOutputOverlap);
}

template <typename EmitOp>
void EmitMemoryPeek(HInvoke* invoke, EmitOp&& emit_op) {
  LocationSummary* locations = invoke->GetLocations();
  emit_op(locations->Out().AsRegister<XRegister>(), locations->InAt(0).AsRegister<XRegister>());
}

void IntrinsicLocationsBuilderRISCV64::VisitMemoryPeekByte(HInvoke* invoke) {
  CreateIntToIntNoOverlapLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitMemoryPeekByte(HInvoke* invoke) {
  Riscv64Assembler* assembler = GetAssembler();
  EmitMemoryPeek(invoke, [&](XRegister rd, XRegister rs1) { __ Lb(rd, rs1, 0); });
}

void IntrinsicLocationsBuilderRISCV64::VisitMemoryPeekIntNative(HInvoke* invoke) {
  CreateIntToIntNoOverlapLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitMemoryPeekIntNative(HInvoke* invoke) {
  Riscv64Assembler* assembler = GetAssembler();
  EmitMemoryPeek(invoke, [&](XRegister rd, XRegister rs1) { __ Lw(rd, rs1, 0); });
}

void IntrinsicLocationsBuilderRISCV64::VisitMemoryPeekLongNative(HInvoke* invoke) {
  CreateIntToIntNoOverlapLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitMemoryPeekLongNative(HInvoke* invoke) {
  Riscv64Assembler* assembler = GetAssembler();
  EmitMemoryPeek(invoke, [&](XRegister rd, XRegister rs1) { __ Ld(rd, rs1, 0); });
}

void IntrinsicLocationsBuilderRISCV64::VisitMemoryPeekShortNative(HInvoke* invoke) {
  CreateIntToIntNoOverlapLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitMemoryPeekShortNative(HInvoke* invoke) {
  Riscv64Assembler* assembler = GetAssembler();
  EmitMemoryPeek(invoke, [&](XRegister rd, XRegister rs1) { __ Lh(rd, rs1, 0); });
}

static void CreateIntIntToVoidLocations(ArenaAllocator* allocator, HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::RequiresRegister());
  locations->SetInAt(1, Location::RequiresRegister());
}

static void CreateIntIntToIntSlowPathCallLocations(ArenaAllocator* allocator, HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kCallOnSlowPath, kIntrinsified);
  locations->SetInAt(0, Location::RequiresRegister());
  locations->SetInAt(1, Location::RequiresRegister());
  // Force kOutputOverlap; see comments in IntrinsicSlowPath::EmitNativeCode.
  locations->SetOut(Location::RequiresRegister(), Location::kOutputOverlap);
}

template <typename EmitOp>
void EmitMemoryPoke(HInvoke* invoke, EmitOp&& emit_op) {
  LocationSummary* locations = invoke->GetLocations();
  emit_op(locations->InAt(1).AsRegister<XRegister>(), locations->InAt(0).AsRegister<XRegister>());
}

void IntrinsicLocationsBuilderRISCV64::VisitMemoryPokeByte(HInvoke* invoke) {
  CreateIntIntToVoidLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitMemoryPokeByte(HInvoke* invoke) {
  Riscv64Assembler* assembler = GetAssembler();
  EmitMemoryPoke(invoke, [&](XRegister rs2, XRegister rs1) { __ Sb(rs2, rs1, 0); });
}

void IntrinsicLocationsBuilderRISCV64::VisitMemoryPokeIntNative(HInvoke* invoke) {
  CreateIntIntToVoidLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitMemoryPokeIntNative(HInvoke* invoke) {
  Riscv64Assembler* assembler = GetAssembler();
  EmitMemoryPoke(invoke, [&](XRegister rs2, XRegister rs1) { __ Sw(rs2, rs1, 0); });
}

void IntrinsicLocationsBuilderRISCV64::VisitMemoryPokeLongNative(HInvoke* invoke) {
  CreateIntIntToVoidLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitMemoryPokeLongNative(HInvoke* invoke) {
  Riscv64Assembler* assembler = GetAssembler();
  EmitMemoryPoke(invoke, [&](XRegister rs2, XRegister rs1) { __ Sd(rs2, rs1, 0); });
}

void IntrinsicLocationsBuilderRISCV64::VisitMemoryPokeShortNative(HInvoke* invoke) {
  CreateIntIntToVoidLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitMemoryPokeShortNative(HInvoke* invoke) {
  Riscv64Assembler* assembler = GetAssembler();
  EmitMemoryPoke(invoke, [&](XRegister rs2, XRegister rs1) { __ Sh(rs2, rs1, 0); });
}

static void GenerateReverseBytes(CodeGeneratorRISCV64* codegen,
                                 Location rd,
                                 XRegister rs1,
                                 DataType::Type type) {
  Riscv64Assembler* assembler = codegen->GetAssembler();
  switch (type) {
    case DataType::Type::kUint16:
      // There is no 16-bit reverse bytes instruction.
      __ Rev8(rd.AsRegister<XRegister>(), rs1);
      __ Srli(rd.AsRegister<XRegister>(), rd.AsRegister<XRegister>(), 48);
      break;
    case DataType::Type::kInt16:
      // There is no 16-bit reverse bytes instruction.
      __ Rev8(rd.AsRegister<XRegister>(), rs1);
      __ Srai(rd.AsRegister<XRegister>(), rd.AsRegister<XRegister>(), 48);
      break;
    case DataType::Type::kInt32:
      // There is no 32-bit reverse bytes instruction.
      __ Rev8(rd.AsRegister<XRegister>(), rs1);
      __ Srai(rd.AsRegister<XRegister>(), rd.AsRegister<XRegister>(), 32);
      break;
    case DataType::Type::kInt64:
      __ Rev8(rd.AsRegister<XRegister>(), rs1);
      break;
    case DataType::Type::kFloat32:
      // There is no 32-bit reverse bytes instruction.
      __ Rev8(rs1, rs1);  // Note: Clobbers `rs1`.
      __ Srai(rs1, rs1, 32);
      __ FMvWX(rd.AsFpuRegister<FRegister>(), rs1);
      break;
    case DataType::Type::kFloat64:
      __ Rev8(rs1, rs1);  // Note: Clobbers `rs1`.
      __ FMvDX(rd.AsFpuRegister<FRegister>(), rs1);
      break;
    default:
      LOG(FATAL) << "Unexpected type: " << type;
      UNREACHABLE();
  }
}

static void GenerateReverseBytes(CodeGeneratorRISCV64* codegen,
                                 HInvoke* invoke,
                                 DataType::Type type) {
  DCHECK_EQ(type, invoke->GetType());
  LocationSummary* locations = invoke->GetLocations();
  GenerateReverseBytes(codegen, locations->Out(), locations->InAt(0).AsRegister<XRegister>(), type);
}

static void GenerateReverse(CodeGeneratorRISCV64* codegen, HInvoke* invoke, DataType::Type type) {
  DCHECK_EQ(type, invoke->GetType());
  Riscv64Assembler* assembler = codegen->GetAssembler();
  LocationSummary* locations = invoke->GetLocations();
  XRegister in = locations->InAt(0).AsRegister<XRegister>();
  XRegister out = locations->Out().AsRegister<XRegister>();
  ScratchRegisterScope srs(assembler);
  XRegister temp1 = srs.AllocateXRegister();
  XRegister temp2 = srs.AllocateXRegister();

  auto maybe_extend_mask = [type, assembler](XRegister mask, XRegister temp) {
    if (type == DataType::Type::kInt64) {
      __ Slli(temp, mask, 32);
      __ Add(mask, mask, temp);
    }
  };

  // Swap bits in bit pairs.
  __ Li(temp1, 0x55555555);
  maybe_extend_mask(temp1, temp2);
  __ Srli(temp2, in, 1);
  __ And(out, in, temp1);
  __ And(temp2, temp2, temp1);
  __ Sh1Add(out, out, temp2);

  // Swap bit pairs in 4-bit groups.
  __ Li(temp1, 0x33333333);
  maybe_extend_mask(temp1, temp2);
  __ Srli(temp2, out, 2);
  __ And(out, out, temp1);
  __ And(temp2, temp2, temp1);
  __ Sh2Add(out, out, temp2);

  // Swap 4-bit groups in 8-bit groups.
  __ Li(temp1, 0x0f0f0f0f);
  maybe_extend_mask(temp1, temp2);
  __ Srli(temp2, out, 4);
  __ And(out, out, temp1);
  __ And(temp2, temp2, temp1);
  __ Slli(out, out, 4);
  __ Add(out, out, temp2);

  GenerateReverseBytes(codegen, Location::RegisterLocation(out), out, type);
}

void IntrinsicLocationsBuilderRISCV64::VisitIntegerReverse(HInvoke* invoke) {
  CreateIntToIntNoOverlapLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitIntegerReverse(HInvoke* invoke) {
  GenerateReverse(codegen_, invoke, DataType::Type::kInt32);
}

void IntrinsicLocationsBuilderRISCV64::VisitLongReverse(HInvoke* invoke) {
  CreateIntToIntNoOverlapLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitLongReverse(HInvoke* invoke) {
  GenerateReverse(codegen_, invoke, DataType::Type::kInt64);
}

void IntrinsicLocationsBuilderRISCV64::VisitIntegerReverseBytes(HInvoke* invoke) {
  CreateIntToIntNoOverlapLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitIntegerReverseBytes(HInvoke* invoke) {
  GenerateReverseBytes(codegen_, invoke, DataType::Type::kInt32);
}

void IntrinsicLocationsBuilderRISCV64::VisitLongReverseBytes(HInvoke* invoke) {
  CreateIntToIntNoOverlapLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitLongReverseBytes(HInvoke* invoke) {
  GenerateReverseBytes(codegen_, invoke, DataType::Type::kInt64);
}

void IntrinsicLocationsBuilderRISCV64::VisitShortReverseBytes(HInvoke* invoke) {
  CreateIntToIntNoOverlapLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitShortReverseBytes(HInvoke* invoke) {
  GenerateReverseBytes(codegen_, invoke, DataType::Type::kInt16);
}

template <typename EmitOp>
void EmitIntegralUnOp(HInvoke* invoke, EmitOp&& emit_op) {
  LocationSummary* locations = invoke->GetLocations();
  emit_op(locations->Out().AsRegister<XRegister>(), locations->InAt(0).AsRegister<XRegister>());
}

void IntrinsicLocationsBuilderRISCV64::VisitIntegerBitCount(HInvoke* invoke) {
  CreateIntToIntNoOverlapLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitIntegerBitCount(HInvoke* invoke) {
  Riscv64Assembler* assembler = GetAssembler();
  EmitIntegralUnOp(invoke, [&](XRegister rd, XRegister rs1) { __ Cpopw(rd, rs1); });
}

void IntrinsicLocationsBuilderRISCV64::VisitLongBitCount(HInvoke* invoke) {
  CreateIntToIntNoOverlapLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitLongBitCount(HInvoke* invoke) {
  Riscv64Assembler* assembler = GetAssembler();
  EmitIntegralUnOp(invoke, [&](XRegister rd, XRegister rs1) { __ Cpop(rd, rs1); });
}

void IntrinsicLocationsBuilderRISCV64::VisitIntegerHighestOneBit(HInvoke* invoke) {
  CreateIntToIntNoOverlapLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitIntegerHighestOneBit(HInvoke* invoke) {
  Riscv64Assembler* assembler = GetAssembler();
  EmitIntegralUnOp(invoke, [&](XRegister rd, XRegister rs1) {
    ScratchRegisterScope srs(assembler);
    XRegister tmp = srs.AllocateXRegister();
    XRegister tmp2 = srs.AllocateXRegister();
    __ Clzw(tmp, rs1);
    __ Li(tmp2, INT64_C(-0x80000000));
    __ Srlw(tmp2, tmp2, tmp);
    __ And(rd, rs1, tmp2);  // Make sure the result is zero if the input is zero.
  });
}

void IntrinsicLocationsBuilderRISCV64::VisitLongHighestOneBit(HInvoke* invoke) {
  CreateIntToIntNoOverlapLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitLongHighestOneBit(HInvoke* invoke) {
  Riscv64Assembler* assembler = GetAssembler();
  EmitIntegralUnOp(invoke, [&](XRegister rd, XRegister rs1) {
    ScratchRegisterScope srs(assembler);
    XRegister tmp = srs.AllocateXRegister();
    XRegister tmp2 = srs.AllocateXRegister();
    __ Clz(tmp, rs1);
    __ Li(tmp2, INT64_C(-0x8000000000000000));
    __ Srl(tmp2, tmp2, tmp);
    __ And(rd, rs1, tmp2);  // Make sure the result is zero if the input is zero.
  });
}

void IntrinsicLocationsBuilderRISCV64::VisitIntegerLowestOneBit(HInvoke* invoke) {
  CreateIntToIntNoOverlapLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitIntegerLowestOneBit(HInvoke* invoke) {
  Riscv64Assembler* assembler = GetAssembler();
  EmitIntegralUnOp(invoke, [&](XRegister rd, XRegister rs1) {
    ScratchRegisterScope srs(assembler);
    XRegister tmp = srs.AllocateXRegister();
    __ NegW(tmp, rs1);
    __ And(rd, rs1, tmp);
  });
}

void IntrinsicLocationsBuilderRISCV64::VisitLongLowestOneBit(HInvoke* invoke) {
  CreateIntToIntNoOverlapLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitLongLowestOneBit(HInvoke* invoke) {
  Riscv64Assembler* assembler = GetAssembler();
  EmitIntegralUnOp(invoke, [&](XRegister rd, XRegister rs1) {
    ScratchRegisterScope srs(assembler);
    XRegister tmp = srs.AllocateXRegister();
    __ Neg(tmp, rs1);
    __ And(rd, rs1, tmp);
  });
}

void IntrinsicLocationsBuilderRISCV64::VisitIntegerNumberOfLeadingZeros(HInvoke* invoke) {
  CreateIntToIntNoOverlapLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitIntegerNumberOfLeadingZeros(HInvoke* invoke) {
  Riscv64Assembler* assembler = GetAssembler();
  EmitIntegralUnOp(invoke, [&](XRegister rd, XRegister rs1) { __ Clzw(rd, rs1); });
}

void IntrinsicLocationsBuilderRISCV64::VisitLongNumberOfLeadingZeros(HInvoke* invoke) {
  CreateIntToIntNoOverlapLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitLongNumberOfLeadingZeros(HInvoke* invoke) {
  Riscv64Assembler* assembler = GetAssembler();
  EmitIntegralUnOp(invoke, [&](XRegister rd, XRegister rs1) { __ Clz(rd, rs1); });
}

void IntrinsicLocationsBuilderRISCV64::VisitIntegerNumberOfTrailingZeros(HInvoke* invoke) {
  CreateIntToIntNoOverlapLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitIntegerNumberOfTrailingZeros(HInvoke* invoke) {
  Riscv64Assembler* assembler = GetAssembler();
  EmitIntegralUnOp(invoke, [&](XRegister rd, XRegister rs1) { __ Ctzw(rd, rs1); });
}

void IntrinsicLocationsBuilderRISCV64::VisitLongNumberOfTrailingZeros(HInvoke* invoke) {
  CreateIntToIntNoOverlapLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitLongNumberOfTrailingZeros(HInvoke* invoke) {
  Riscv64Assembler* assembler = GetAssembler();
  EmitIntegralUnOp(invoke, [&](XRegister rd, XRegister rs1) { __ Ctz(rd, rs1); });
}

static void GenerateDivideUnsigned(HInvoke* invoke, CodeGeneratorRISCV64* codegen) {
  LocationSummary* locations = invoke->GetLocations();
  Riscv64Assembler* assembler = codegen->GetAssembler();
  DataType::Type type = invoke->GetType();
  DCHECK(type == DataType::Type::kInt32 || type == DataType::Type::kInt64);

  XRegister dividend = locations->InAt(0).AsRegister<XRegister>();
  XRegister divisor = locations->InAt(1).AsRegister<XRegister>();
  XRegister out = locations->Out().AsRegister<XRegister>();

  // Check if divisor is zero, bail to managed implementation to handle.
  SlowPathCodeRISCV64* slow_path =
      new (codegen->GetScopedAllocator()) IntrinsicSlowPathRISCV64(invoke);
  codegen->AddSlowPath(slow_path);
  __ Beqz(divisor, slow_path->GetEntryLabel());

  if (type == DataType::Type::kInt32) {
    __ Divuw(out, dividend, divisor);
  } else {
    __ Divu(out, dividend, divisor);
  }

  __ Bind(slow_path->GetExitLabel());
}

void IntrinsicLocationsBuilderRISCV64::VisitIntegerDivideUnsigned(HInvoke* invoke) {
  CreateIntIntToIntSlowPathCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitIntegerDivideUnsigned(HInvoke* invoke) {
  GenerateDivideUnsigned(invoke, codegen_);
}

void IntrinsicLocationsBuilderRISCV64::VisitLongDivideUnsigned(HInvoke* invoke) {
  CreateIntIntToIntSlowPathCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitLongDivideUnsigned(HInvoke* invoke) {
  GenerateDivideUnsigned(invoke, codegen_);
}

#define VISIT_INTRINSIC(name, low, high, type, start_index) \
  void IntrinsicLocationsBuilderRISCV64::Visit ##name ##ValueOf(HInvoke* invoke) { \
    InvokeRuntimeCallingConvention calling_convention; \
    IntrinsicVisitor::ComputeValueOfLocations( \
        invoke, \
        codegen_, \
        low, \
        high - low + 1, \
        calling_convention.GetReturnLocation(DataType::Type::kReference), \
        Location::RegisterLocation(calling_convention.GetRegisterAt(0))); \
  } \
  void IntrinsicCodeGeneratorRISCV64::Visit ##name ##ValueOf(HInvoke* invoke) { \
    IntrinsicVisitor::ValueOfInfo info = \
        IntrinsicVisitor::ComputeValueOfInfo( \
            invoke, \
            codegen_->GetCompilerOptions(), \
            WellKnownClasses::java_lang_ ##name ##_value, \
            low, \
            high - low + 1, \
            start_index); \
    HandleValueOf(invoke, info, type); \
  }
  BOXED_TYPES(VISIT_INTRINSIC)
#undef VISIT_INTRINSIC

void IntrinsicCodeGeneratorRISCV64::HandleValueOf(HInvoke* invoke,
                                                  const IntrinsicVisitor::ValueOfInfo& info,
                                                  DataType::Type type) {
  Riscv64Assembler* assembler = codegen_->GetAssembler();
  LocationSummary* locations = invoke->GetLocations();
  XRegister out = locations->Out().AsRegister<XRegister>();
  ScratchRegisterScope srs(assembler);
  XRegister temp = srs.AllocateXRegister();
  auto allocate_instance = [&]() {
    DCHECK_EQ(out, InvokeRuntimeCallingConvention().GetRegisterAt(0));
    codegen_->LoadIntrinsicDeclaringClass(out, invoke);
    codegen_->InvokeRuntime(kQuickAllocObjectInitialized, invoke, invoke->GetDexPc());
    CheckEntrypointTypes<kQuickAllocObjectWithChecks, void*, mirror::Class*>();
  };
  if (invoke->InputAt(0)->IsIntConstant()) {
    int32_t value = invoke->InputAt(0)->AsIntConstant()->GetValue();
    if (static_cast<uint32_t>(value - info.low) < info.length) {
      // Just embed the object in the code.
      DCHECK_NE(info.value_boot_image_reference, ValueOfInfo::kInvalidReference);
      codegen_->LoadBootImageAddress(out, info.value_boot_image_reference);
    } else {
      DCHECK(locations->CanCall());
      // Allocate and initialize a new object.
      // TODO: If we JIT, we could allocate the object now, and store it in the
      // JIT object table.
      allocate_instance();
      __ Li(temp, value);
      codegen_->GetInstructionVisitor()->Store(
          Location::RegisterLocation(temp), out, info.value_offset, type);
      // Class pointer and `value` final field stores require a barrier before publication.
      codegen_->GenerateMemoryBarrier(MemBarrierKind::kStoreStore);
    }
  } else {
    DCHECK(locations->CanCall());
    XRegister in = locations->InAt(0).AsRegister<XRegister>();
    Riscv64Label allocate, done;
    // Check bounds of our cache.
    __ AddConst32(out, in, -info.low);
    __ Li(temp, info.length);
    __ Bgeu(out, temp, &allocate);
    // If the value is within the bounds, load the object directly from the array.
    codegen_->LoadBootImageAddress(temp, info.array_data_boot_image_reference);
    __ Sh2Add(temp, out, temp);
    __ Loadwu(out, temp, 0);
    codegen_->MaybeUnpoisonHeapReference(out);
    __ J(&done);
    __ Bind(&allocate);
    // Otherwise allocate and initialize a new object.
    allocate_instance();
    codegen_->GetInstructionVisitor()->Store(
        Location::RegisterLocation(in), out, info.value_offset, type);
    // Class pointer and `value` final field stores require a barrier before publication.
    codegen_->GenerateMemoryBarrier(MemBarrierKind::kStoreStore);
    __ Bind(&done);
  }
}

void IntrinsicLocationsBuilderRISCV64::VisitReferenceGetReferent(HInvoke* invoke) {
  IntrinsicVisitor::CreateReferenceGetReferentLocations(invoke, codegen_);

  if (codegen_->EmitBakerReadBarrier() && invoke->GetLocations() != nullptr) {
    invoke->GetLocations()->AddTemp(Location::RequiresRegister());
  }
}

void IntrinsicCodeGeneratorRISCV64::VisitReferenceGetReferent(HInvoke* invoke) {
  Riscv64Assembler* assembler = GetAssembler();
  LocationSummary* locations = invoke->GetLocations();
  Location obj = locations->InAt(0);
  Location out = locations->Out();

  SlowPathCodeRISCV64* slow_path =
      new (codegen_->GetScopedAllocator()) IntrinsicSlowPathRISCV64(invoke);
  codegen_->AddSlowPath(slow_path);

  if (codegen_->EmitReadBarrier()) {
    // Check self->GetWeakRefAccessEnabled().
    ScratchRegisterScope srs(assembler);
    XRegister temp = srs.AllocateXRegister();
    __ Loadwu(temp, TR, Thread::WeakRefAccessEnabledOffset<kRiscv64PointerSize>().Int32Value());
    static_assert(enum_cast<int32_t>(WeakRefAccessState::kVisiblyEnabled) == 0);
    __ Bnez(temp, slow_path->GetEntryLabel());
  }

  {
    // Load the java.lang.ref.Reference class.
    ScratchRegisterScope srs(assembler);
    XRegister temp = srs.AllocateXRegister();
    codegen_->LoadIntrinsicDeclaringClass(temp, invoke);

    // Check static fields java.lang.ref.Reference.{disableIntrinsic,slowPathEnabled} together.
    MemberOffset disable_intrinsic_offset = IntrinsicVisitor::GetReferenceDisableIntrinsicOffset();
    DCHECK_ALIGNED(disable_intrinsic_offset.Uint32Value(), 2u);
    DCHECK_EQ(disable_intrinsic_offset.Uint32Value() + 1u,
              IntrinsicVisitor::GetReferenceSlowPathEnabledOffset().Uint32Value());
    __ Loadhu(temp, temp, disable_intrinsic_offset.Int32Value());
    __ Bnez(temp, slow_path->GetEntryLabel());
  }

  // Load the value from the field.
  uint32_t referent_offset = mirror::Reference::ReferentOffset().Uint32Value();
  if (codegen_->EmitBakerReadBarrier()) {
    codegen_->GenerateFieldLoadWithBakerReadBarrier(invoke,
                                                    out,
                                                    obj.AsRegister<XRegister>(),
                                                    referent_offset,
                                                    /*maybe_temp=*/ locations->GetTemp(0),
                                                    /*needs_null_check=*/ false);
  } else {
    codegen_->GetInstructionVisitor()->Load(
        out, obj.AsRegister<XRegister>(), referent_offset, DataType::Type::kReference);
    codegen_->MaybeGenerateReadBarrierSlow(invoke, out, out, obj, referent_offset);
  }
  // Emit memory barrier for load-acquire.
  codegen_->GenerateMemoryBarrier(MemBarrierKind::kLoadAny);
  __ Bind(slow_path->GetExitLabel());
}

void IntrinsicLocationsBuilderRISCV64::VisitReferenceRefersTo(HInvoke* invoke) {
  IntrinsicVisitor::CreateReferenceRefersToLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorRISCV64::VisitReferenceRefersTo(HInvoke* invoke) {
  Riscv64Assembler* assembler = GetAssembler();
  LocationSummary* locations = invoke->GetLocations();
  XRegister obj = locations->InAt(0).AsRegister<XRegister>();
  XRegister other = locations->InAt(1).AsRegister<XRegister>();
  XRegister out = locations->Out().AsRegister<XRegister>();

  uint32_t referent_offset = mirror::Reference::ReferentOffset().Uint32Value();
  uint32_t monitor_offset = mirror::Object::MonitorOffset().Int32Value();

  codegen_->GetInstructionVisitor()->Load(
      Location::RegisterLocation(out), obj, referent_offset, DataType::Type::kReference);
  codegen_->MaybeRecordImplicitNullCheck(invoke);
  codegen_->MaybeUnpoisonHeapReference(out);

  // Emit memory barrier for load-acquire.
  codegen_->GenerateMemoryBarrier(MemBarrierKind::kLoadAny);

  if (codegen_->EmitReadBarrier()) {
    DCHECK(kUseBakerReadBarrier);

    Riscv64Label calculate_result;

    // If equal to `other`, the loaded reference is final (it cannot be a from-space reference).
    __ Beq(out, other, &calculate_result);

    // If the GC is not marking, the loaded reference is final.
    ScratchRegisterScope srs(assembler);
    XRegister tmp = srs.AllocateXRegister();
    __ Loadwu(tmp, TR, Thread::IsGcMarkingOffset<kRiscv64PointerSize>().Int32Value());
    __ Beqz(tmp, &calculate_result);

    // Check if the loaded reference is null.
    __ Beqz(out, &calculate_result);

    // For correct memory visibility, we need a barrier before loading the lock word to
    // synchronize with the publishing of `other` by the CC GC. However, as long as the
    // load-acquire above is implemented as a plain load followed by a barrier (rather
    // than an atomic load-acquire instruction which synchronizes only with other
    // instructions on the same memory location), that barrier is sufficient.

    // Load the lockword and check if it is a forwarding address.
    static_assert(LockWord::kStateShift == 30u);
    static_assert(LockWord::kStateForwardingAddress == 3u);
    // Load the lock word sign-extended. Comparing it to the sign-extended forwarding
    // address bits as unsigned is the same as comparing both zero-extended.
    __ Loadw(tmp, out, monitor_offset);
    // Materialize sign-extended forwarding address bits. This is a single LUI instruction.
    XRegister tmp2 = srs.AllocateXRegister();
    __ Li(tmp2, INT64_C(-1) & ~static_cast<int64_t>((1 << LockWord::kStateShift) - 1));
    // If we do not have a forwarding address, the loaded reference cannot be the same as `other`,
    // so we proceed to calculate the result with `out != other`.
    __ Bltu(tmp, tmp2, &calculate_result);

    // Extract the forwarding address for comparison with `other`.
    // Note that the high 32 bits shall not be used for the result calculation.
    __ Slliw(out, tmp, LockWord::kForwardingAddressShift);

    __ Bind(&calculate_result);
  }

  // Calculate the result `out == other`.
  __ Subw(out, out, other);
  __ Seqz(out, out);
}

static void GenerateVisitStringIndexOf(HInvoke* invoke,
                                       Riscv64Assembler* assembler,
                                       CodeGeneratorRISCV64* codegen,
                                       bool start_at_zero) {
  LocationSummary* locations = invoke->GetLocations();

  // Note that the null check must have been done earlier.
  DCHECK(!invoke->CanDoImplicitNullCheckOn(invoke->InputAt(0)));

  // Check for code points > 0xFFFF. Either a slow-path check when we don't know statically,
  // or directly dispatch for a large constant, or omit slow-path for a small constant or a char.
  SlowPathCodeRISCV64* slow_path = nullptr;
  HInstruction* code_point = invoke->InputAt(1);
  if (code_point->IsIntConstant()) {
    if (static_cast<uint32_t>(code_point->AsIntConstant()->GetValue()) > 0xFFFFU) {
      // Always needs the slow-path. We could directly dispatch to it, but this case should be
      // rare, so for simplicity just put the full slow-path down and branch unconditionally.
      slow_path = new (codegen->GetScopedAllocator()) IntrinsicSlowPathRISCV64(invoke);
      codegen->AddSlowPath(slow_path);
      __ J(slow_path->GetEntryLabel());
      __ Bind(slow_path->GetExitLabel());
      return;
    }
  } else if (code_point->GetType() != DataType::Type::kUint16) {
    slow_path = new (codegen->GetScopedAllocator()) IntrinsicSlowPathRISCV64(invoke);
    codegen->AddSlowPath(slow_path);
    ScratchRegisterScope srs(assembler);
    XRegister tmp = srs.AllocateXRegister();
    __ Srliw(tmp, locations->InAt(1).AsRegister<XRegister>(), 16);
    __ Bnez(tmp, slow_path->GetEntryLabel());
  }

  if (start_at_zero) {
    // Start-index = 0.
    XRegister tmp_reg = locations->GetTemp(0).AsRegister<XRegister>();
    __ Li(tmp_reg, 0);
  }

  codegen->InvokeRuntime(kQuickIndexOf, invoke, invoke->GetDexPc(), slow_path);
  CheckEntrypointTypes<kQuickIndexOf, int32_t, void*, uint32_t, uint32_t>();

  if (slow_path != nullptr) {
    __ Bind(slow_path->GetExitLabel());
  }
}

void IntrinsicLocationsBuilderRISCV64::VisitStringIndexOf(HInvoke* invoke) {
  LocationSummary* locations = new (allocator_) LocationSummary(
      invoke, LocationSummary::kCallOnMainAndSlowPath, kIntrinsified);
  // We have a hand-crafted assembly stub that follows the runtime calling convention. So it's
  // best to align the inputs accordingly.
  InvokeRuntimeCallingConvention calling_convention;
  locations->SetInAt(0, Location::RegisterLocation(calling_convention.GetRegisterAt(0)));
  locations->SetInAt(1, Location::RegisterLocation(calling_convention.GetRegisterAt(1)));
  locations->SetOut(calling_convention.GetReturnLocation(DataType::Type::kInt32));

  // Need to send start_index=0.
  locations->AddTemp(Location::RegisterLocation(calling_convention.GetRegisterAt(2)));
}

void IntrinsicCodeGeneratorRISCV64::VisitStringIndexOf(HInvoke* invoke) {
  GenerateVisitStringIndexOf(invoke, GetAssembler(), codegen_, /* start_at_zero= */ true);
}

void IntrinsicLocationsBuilderRISCV64::VisitStringIndexOfAfter(HInvoke* invoke) {
  LocationSummary* locations = new (allocator_) LocationSummary(
      invoke, LocationSummary::kCallOnMainAndSlowPath, kIntrinsified);
  // We have a hand-crafted assembly stub that follows the runtime calling convention. So it's
  // best to align the inputs accordingly.
  InvokeRuntimeCallingConvention calling_convention;
  locations->SetInAt(0, Location::RegisterLocation(calling_convention.GetRegisterAt(0)));
  locations->SetInAt(1, Location::RegisterLocation(calling_convention.GetRegisterAt(1)));
  locations->SetInAt(2, Location::RegisterLocation(calling_convention.GetRegisterAt(2)));
  locations->SetOut(calling_convention.GetReturnLocation(DataType::Type::kInt32));
}

void IntrinsicCodeGeneratorRISCV64::VisitStringIndexOfAfter(HInvoke* invoke) {
  GenerateVisitStringIndexOf(invoke, GetAssembler(), codegen_, /* start_at_zero= */ false);
}

void IntrinsicLocationsBuilderRISCV64::VisitStringNewStringFromBytes(HInvoke* invoke) {
  LocationSummary* locations = new (allocator_) LocationSummary(
      invoke, LocationSummary::kCallOnMainAndSlowPath, kIntrinsified);
  InvokeRuntimeCallingConvention calling_convention;
  locations->SetInAt(0, Location::RegisterLocation(calling_convention.GetRegisterAt(0)));
  locations->SetInAt(1, Location::RegisterLocation(calling_convention.GetRegisterAt(1)));
  locations->SetInAt(2, Location::RegisterLocation(calling_convention.GetRegisterAt(2)));
  locations->SetInAt(3, Location::RegisterLocation(calling_convention.GetRegisterAt(3)));
  locations->SetOut(calling_convention.GetReturnLocation(DataType::Type::kReference));
}

void IntrinsicCodeGeneratorRISCV64::VisitStringNewStringFromBytes(HInvoke* invoke) {
  Riscv64Assembler* assembler = GetAssembler();
  LocationSummary* locations = invoke->GetLocations();
  XRegister byte_array = locations->InAt(0).AsRegister<XRegister>();

  SlowPathCodeRISCV64* slow_path =
      new (codegen_->GetScopedAllocator()) IntrinsicSlowPathRISCV64(invoke);
  codegen_->AddSlowPath(slow_path);
  __ Beqz(byte_array, slow_path->GetEntryLabel());

  codegen_->InvokeRuntime(kQuickAllocStringFromBytes, invoke, invoke->GetDexPc(), slow_path);
  CheckEntrypointTypes<kQuickAllocStringFromBytes, void*, void*, int32_t, int32_t, int32_t>();
  __ Bind(slow_path->GetExitLabel());
}

void IntrinsicLocationsBuilderRISCV64::VisitStringNewStringFromChars(HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator_) LocationSummary(invoke, LocationSummary::kCallOnMainOnly, kIntrinsified);
  InvokeRuntimeCallingConvention calling_convention;
  locations->SetInAt(0, Location::RegisterLocation(calling_convention.GetRegisterAt(0)));
  locations->SetInAt(1, Location::RegisterLocation(calling_convention.GetRegisterAt(1)));
  locations->SetInAt(2, Location::RegisterLocation(calling_convention.GetRegisterAt(2)));
  locations->SetOut(calling_convention.GetReturnLocation(DataType::Type::kReference));
}

void IntrinsicCodeGeneratorRISCV64::VisitStringNewStringFromChars(HInvoke* invoke) {
  // No need to emit code checking whether `locations->InAt(2)` is a null
  // pointer, as callers of the native method
  //
  //   java.lang.StringFactory.newStringFromChars(int offset, int charCount, char[] data)
  //
  // all include a null check on `data` before calling that method.
  codegen_->InvokeRuntime(kQuickAllocStringFromChars, invoke, invoke->GetDexPc());
  CheckEntrypointTypes<kQuickAllocStringFromChars, void*, int32_t, int32_t, void*>();
}

void IntrinsicLocationsBuilderRISCV64::VisitStringNewStringFromString(HInvoke* invoke) {
  LocationSummary* locations = new (allocator_) LocationSummary(
      invoke, LocationSummary::kCallOnMainAndSlowPath, kIntrinsified);
  InvokeRuntimeCallingConvention calling_convention;
  locations->SetInAt(0, Location::RegisterLocation(calling_convention.GetRegisterAt(0)));
  locations->SetOut(calling_convention.GetReturnLocation(DataType::Type::kReference));
}

void IntrinsicCodeGeneratorRISCV64::VisitStringNewStringFromString(HInvoke* invoke) {
  Riscv64Assembler* assembler = GetAssembler();
  LocationSummary* locations = invoke->GetLocations();
  XRegister string_to_copy = locations->InAt(0).AsRegister<XRegister>();

  SlowPathCodeRISCV64* slow_path =
      new (codegen_->GetScopedAllocator()) IntrinsicSlowPathRISCV64(invoke);
  codegen_->AddSlowPath(slow_path);
  __ Beqz(string_to_copy, slow_path->GetEntryLabel());

  codegen_->InvokeRuntime(kQuickAllocStringFromString, invoke, invoke->GetDexPc(), slow_path);
  CheckEntrypointTypes<kQuickAllocStringFromString, void*, void*>();
  __ Bind(slow_path->GetExitLabel());
}

static void GenerateSet(CodeGeneratorRISCV64* codegen,
                        std::memory_order order,
                        Location value,
                        XRegister rs1,
                        int32_t offset,
                        DataType::Type type) {
  if (order == std::memory_order_seq_cst) {
    codegen->GetInstructionVisitor()->StoreSeqCst(value, rs1, offset, type);
  } else {
    if (order == std::memory_order_release) {
      codegen->GenerateMemoryBarrier(MemBarrierKind::kAnyStore);
    } else {
      DCHECK(order == std::memory_order_relaxed);
    }
    codegen->GetInstructionVisitor()->Store(value, rs1, offset, type);
  }
}

std::pair<AqRl, AqRl> GetLrScAqRl(std::memory_order order) {
  AqRl load_aqrl = AqRl::kNone;
  AqRl store_aqrl = AqRl::kNone;
  if (order == std::memory_order_acquire) {
    load_aqrl = AqRl::kAcquire;
  } else if (order == std::memory_order_release) {
    store_aqrl = AqRl::kRelease;
  } else if (order == std::memory_order_seq_cst) {
    load_aqrl = AqRl::kAqRl;
    store_aqrl = AqRl::kRelease;
  } else {
    DCHECK(order == std::memory_order_relaxed);
  }
  return {load_aqrl, store_aqrl};
}

AqRl GetAmoAqRl(std::memory_order order) {
  AqRl amo_aqrl = AqRl::kNone;
  if (order == std::memory_order_acquire) {
    amo_aqrl = AqRl::kAcquire;
  } else if (order == std::memory_order_release) {
    amo_aqrl = AqRl::kRelease;
  } else {
    DCHECK(order == std::memory_order_seq_cst);
    amo_aqrl = AqRl::kAqRl;
  }
  return amo_aqrl;
}

static void EmitLoadReserved(Riscv64Assembler* assembler,
                             DataType::Type type,
                             XRegister ptr,
                             XRegister old_value,
                             AqRl aqrl) {
  switch (type) {
    case DataType::Type::kInt32:
      __ LrW(old_value, ptr, aqrl);
      break;
    case DataType::Type::kReference:
      __ LrW(old_value, ptr, aqrl);
      // TODO(riscv64): The `ZextW()` macro currently emits `SLLI+SRLI` which are from the
      // base "I" instruction set. When the assembler is updated to use a single-instruction
      // `ZextW()` macro, either the ADD.UW, or the C.ZEXT.W (16-bit encoding), we need to
      // rewrite this to avoid these non-"I" instructions. We could, for example, sign-extend
      // the reference and do the CAS as `Int32`.
      __ ZextW(old_value, old_value);
      break;
    case DataType::Type::kInt64:
      __ LrD(old_value, ptr, aqrl);
      break;
    default:
      LOG(FATAL) << "Unexpected type: " << type;
      UNREACHABLE();
  }
}

static void EmitStoreConditional(Riscv64Assembler* assembler,
                                 DataType::Type type,
                                 XRegister ptr,
                                 XRegister store_result,
                                 XRegister to_store,
                                 AqRl aqrl) {
  switch (type) {
    case DataType::Type::kInt32:
    case DataType::Type::kReference:
      __ ScW(store_result, to_store, ptr, aqrl);
      break;
    case DataType::Type::kInt64:
      __ ScD(store_result, to_store, ptr, aqrl);
      break;
    default:
      LOG(FATAL) << "Unexpected type: " << type;
      UNREACHABLE();
  }
}

static void GenerateCompareAndSet(Riscv64Assembler* assembler,
                                  DataType::Type type,
                                  std::memory_order order,
                                  bool strong,
                                  Riscv64Label* cmp_failure,
                                  XRegister ptr,
                                  XRegister new_value,
                                  XRegister old_value,
                                  XRegister mask,
                                  XRegister masked,
                                  XRegister store_result,
                                  XRegister expected,
                                  XRegister expected2 = kNoXRegister) {
  DCHECK(!DataType::IsFloatingPointType(type));
  DCHECK_GE(DataType::Size(type), 4u);

  // The `expected2` is valid only for reference slow path and represents the unmarked old value
  // from the main path attempt to emit CAS when the marked old value matched `expected`.
  DCHECK_IMPLIES(expected2 != kNoXRegister, type == DataType::Type::kReference);

  auto [load_aqrl, store_aqrl] = GetLrScAqRl(order);

  // repeat: {
  //   old_value = [ptr];  // Load exclusive.
  //   cmp_value = old_value & mask;  // Extract relevant bits if applicable.
  //   if (cmp_value != expected && cmp_value != expected2) goto cmp_failure;
  //   store_result = failed([ptr] <- new_value);  // Store exclusive.
  // }
  // if (strong) {
  //   if (store_result) goto repeat;  // Repeat until compare fails or store exclusive succeeds.
  // } else {
  //   store_result = store_result ^ 1;  // Report success as 1, failure as 0.
  // }
  //
  // (If `mask` is not valid, `expected` is compared with `old_value` instead of `cmp_value`.)
  // (If `expected2` is not valid, the `cmp_value == expected2` part is not emitted.)

  // Note: We're using "bare" local branches to enforce that they shall not be expanded
  // and the scrach register `TMP` shall not be clobbered if taken. Taking the branch to
  // `cmp_failure` can theoretically clobber `TMP` (if outside the 1 MiB range).
  Riscv64Label loop;
  if (strong) {
    __ Bind(&loop);
  }
  EmitLoadReserved(assembler, type, ptr, old_value, load_aqrl);
  XRegister to_store = new_value;
  {
    ScopedLrScExtensionsRestriction slser(assembler);
    if (mask != kNoXRegister) {
      DCHECK_EQ(expected2, kNoXRegister);
      DCHECK_NE(masked, kNoXRegister);
      __ And(masked, old_value, mask);
      __ Bne(masked, expected, cmp_failure);
      // The `old_value` does not need to be preserved as the caller shall use `masked`
      // to return the old value if needed.
      to_store = old_value;
      // TODO(riscv64): We could XOR the old and new value before the loop and use a single XOR here
      // instead of the XOR+OR. (The `new_value` is either Zero or a temporary we can clobber.)
      __ Xor(to_store, old_value, masked);
      __ Or(to_store, to_store, new_value);
    } else if (expected2 != kNoXRegister) {
      Riscv64Label match2;
      __ Beq(old_value, expected2, &match2, /*is_bare=*/ true);
      __ Bne(old_value, expected, cmp_failure);
      __ Bind(&match2);
    } else {
      __ Bne(old_value, expected, cmp_failure);
    }
  }
  EmitStoreConditional(assembler, type, ptr, store_result, to_store, store_aqrl);
  if (strong) {
    __ Bnez(store_result, &loop, /*is_bare=*/ true);
  } else {
    // Flip the `store_result` register to indicate success by 1 and failure by 0.
    __ Xori(store_result, store_result, 1);
  }
}

class ReadBarrierCasSlowPathRISCV64 : public SlowPathCodeRISCV64 {
 public:
  ReadBarrierCasSlowPathRISCV64(HInvoke* invoke,
                                std::memory_order order,
                                bool strong,
                                XRegister base,
                                XRegister offset,
                                XRegister expected,
                                XRegister new_value,
                                XRegister old_value,
                                XRegister old_value_temp,
                                XRegister store_result,
                                bool update_old_value,
                                CodeGeneratorRISCV64* riscv64_codegen)
      : SlowPathCodeRISCV64(invoke),
        order_(order),
        strong_(strong),
        base_(base),
        offset_(offset),
        expected_(expected),
        new_value_(new_value),
        old_value_(old_value),
        old_value_temp_(old_value_temp),
        store_result_(store_result),
        update_old_value_(update_old_value),
        mark_old_value_slow_path_(nullptr),
        update_old_value_slow_path_(nullptr) {
    // We need to add slow paths now, it is too late when emitting slow path code.
    Location old_value_loc = Location::RegisterLocation(old_value);
    Location old_value_temp_loc = Location::RegisterLocation(old_value_temp);
    if (kUseBakerReadBarrier) {
      mark_old_value_slow_path_ = riscv64_codegen->AddGcRootBakerBarrierBarrierSlowPath(
          invoke, old_value_temp_loc, kBakerReadBarrierTemp);
      if (update_old_value_) {
        update_old_value_slow_path_ = riscv64_codegen->AddGcRootBakerBarrierBarrierSlowPath(
            invoke, old_value_loc, kBakerReadBarrierTemp);
      }
    } else {
      Location base_loc = Location::RegisterLocation(base);
      Location index = Location::RegisterLocation(offset);
      mark_old_value_slow_path_ = riscv64_codegen->AddReadBarrierSlowPath(
          invoke, old_value_temp_loc, old_value_loc, base_loc, /*offset=*/ 0u, index);
      if (update_old_value_) {
        update_old_value_slow_path_ = riscv64_codegen->AddReadBarrierSlowPath(
            invoke, old_value_loc, old_value_temp_loc, base_loc, /*offset=*/ 0u, index);
      }
    }
  }

  const char* GetDescription() const override { return "ReadBarrierCasSlowPathRISCV64"; }

  // We return to a different label on success for a strong CAS that does not return old value.
  Riscv64Label* GetSuccessExitLabel() {
    return &success_exit_label_;
  }

  void EmitNativeCode(CodeGenerator* codegen) override {
    CodeGeneratorRISCV64* riscv64_codegen = down_cast<CodeGeneratorRISCV64*>(codegen);
    Riscv64Assembler* assembler = riscv64_codegen->GetAssembler();
    __ Bind(GetEntryLabel());

    // Mark the `old_value_` from the main path and compare with `expected_`.
    DCHECK(mark_old_value_slow_path_ != nullptr);
    if (kUseBakerReadBarrier) {
      __ Mv(old_value_temp_, old_value_);
      riscv64_codegen->EmitBakerReadBarierMarkingCheck(mark_old_value_slow_path_,
                                                       Location::RegisterLocation(old_value_temp_),
                                                       kBakerReadBarrierTemp);
    } else {
      __ J(mark_old_value_slow_path_->GetEntryLabel());
      __ Bind(mark_old_value_slow_path_->GetExitLabel());
    }
    Riscv64Label move_marked_old_value;
    __ Bne(old_value_temp_, expected_, update_old_value_ ? &move_marked_old_value : GetExitLabel());

    // The `old_value` we have read did not match `expected` (which is always a to-space
    // reference) but after the read barrier the marked to-space value matched, so the
    // `old_value` must be a from-space reference to the same object. Do the same CAS loop
    // as the main path but check for both `expected` and the unmarked old value
    // representing the to-space and from-space references for the same object.

    ScratchRegisterScope srs(assembler);
    XRegister tmp_ptr = srs.AllocateXRegister();
    XRegister store_result =
        store_result_ != kNoXRegister ? store_result_ : srs.AllocateXRegister();

    // Recalculate the `tmp_ptr` from main path potentially clobbered by the read barrier above
    // or by an expanded conditional branch (clobbers `TMP` if beyond 1MiB).
    __ Add(tmp_ptr, base_, offset_);

    Riscv64Label mark_old_value;
    GenerateCompareAndSet(riscv64_codegen->GetAssembler(),
                          DataType::Type::kReference,
                          order_,
                          strong_,
                          /*cmp_failure=*/ update_old_value_ ? &mark_old_value : GetExitLabel(),
                          tmp_ptr,
                          new_value_,
                          /*old_value=*/ old_value_temp_,
                          /*mask=*/ kNoXRegister,
                          /*masked=*/ kNoXRegister,
                          store_result,
                          expected_,
                          /*expected2=*/ old_value_);
    if (update_old_value_) {
      // To reach this point, the `old_value_temp_` must be either a from-space or a to-space
      // reference of the `expected_` object. Update the `old_value_` to the to-space reference.
      __ Mv(old_value_, expected_);
    }
    if (!update_old_value_ && strong_) {
      // Load success value to the result register.
      // We must jump to the instruction that loads the success value in the main path.
      // Note that a SC failure in the CAS loop sets the `store_result` to 1, so the main
      // path must not use the `store_result` as an indication of success.
      __ J(GetSuccessExitLabel());
    } else {
      __ J(GetExitLabel());
    }

    if (update_old_value_) {
      // TODO(riscv64): If we initially saw a from-space reference and then saw
      // a different reference, can the latter be also a from-space reference?
      // (Shouldn't every reference write store a to-space reference?)
      DCHECK(update_old_value_slow_path_ != nullptr);
      __ Bind(&mark_old_value);
      if (kUseBakerReadBarrier) {
        DCHECK(update_old_value_slow_path_ == nullptr);
        __ Mv(old_value_, old_value_temp_);
        riscv64_codegen->EmitBakerReadBarierMarkingCheck(update_old_value_slow_path_,
                                                         Location::RegisterLocation(old_value_),
                                                         kBakerReadBarrierTemp);
      } else {
        // Note: We could redirect the `failure` above directly to the entry label and bind
        // the exit label in the main path, but the main path would need to access the
        // `update_old_value_slow_path_`. To keep the code simple, keep the extra jumps.
        __ J(update_old_value_slow_path_->GetEntryLabel());
        __ Bind(update_old_value_slow_path_->GetExitLabel());
      }
      __ J(GetExitLabel());

      __ Bind(&move_marked_old_value);
      __ Mv(old_value_, old_value_temp_);
      __ J(GetExitLabel());
    }
  }

 private:
  // Use RA as temp. It is clobbered in the slow path anyway.
  static constexpr Location kBakerReadBarrierTemp = Location::RegisterLocation(RA);

  std::memory_order order_;
  bool strong_;
  XRegister base_;
  XRegister offset_;
  XRegister expected_;
  XRegister new_value_;
  XRegister old_value_;
  XRegister old_value_temp_;
  XRegister store_result_;
  bool update_old_value_;
  SlowPathCodeRISCV64* mark_old_value_slow_path_;
  SlowPathCodeRISCV64* update_old_value_slow_path_;
  Riscv64Label success_exit_label_;
};

static void EmitBlt32(Riscv64Assembler* assembler,
                      XRegister rs1,
                      Location rs2,
                      Riscv64Label* label,
                      XRegister temp) {
  if (rs2.IsConstant()) {
    __ Li(temp, rs2.GetConstant()->AsIntConstant()->GetValue());
    __ Blt(rs1, temp, label);
  } else {
    __ Blt(rs1, rs2.AsRegister<XRegister>(), label);
  }
}

static void CheckSystemArrayCopyPosition(Riscv64Assembler* assembler,
                                         XRegister array,
                                         Location pos,
                                         Location length,
                                         SlowPathCodeRISCV64* slow_path,
                                         XRegister temp1,
                                         XRegister temp2,
                                         bool length_is_array_length,
                                         bool position_sign_checked) {
  const int32_t length_offset = mirror::Array::LengthOffset().Int32Value();
  if (pos.IsConstant()) {
    int32_t pos_const = pos.GetConstant()->AsIntConstant()->GetValue();
    DCHECK_GE(pos_const, 0);  // Checked in location builder.
    if (pos_const == 0) {
      if (!length_is_array_length) {
        // Check that length(array) >= length.
        __ Loadw(temp1, array, length_offset);
        EmitBlt32(assembler, temp1, length, slow_path->GetEntryLabel(), temp2);
      }
    } else {
      // Calculate length(array) - pos.
      // Both operands are known to be non-negative `int32_t`, so the difference cannot underflow
      // as `int32_t`. If the result is negative, the BLT below shall go to the slow path.
      __ Loadw(temp1, array, length_offset);
      __ AddConst32(temp1, temp1, -pos_const);

      // Check that (length(array) - pos) >= length.
      EmitBlt32(assembler, temp1, length, slow_path->GetEntryLabel(), temp2);
    }
  } else if (length_is_array_length) {
    // The only way the copy can succeed is if pos is zero.
    __ Bnez(pos.AsRegister<XRegister>(), slow_path->GetEntryLabel());
  } else {
    // Check that pos >= 0.
    XRegister pos_reg = pos.AsRegister<XRegister>();
    if (!position_sign_checked) {
      __ Bltz(pos_reg, slow_path->GetEntryLabel());
    }

    // Calculate length(array) - pos.
    // Both operands are known to be non-negative `int32_t`, so the difference cannot underflow
    // as `int32_t`. If the result is negative, the BLT below shall go to the slow path.
    __ Loadw(temp1, array, length_offset);
    __ Sub(temp1, temp1, pos_reg);

    // Check that (length(array) - pos) >= length.
    EmitBlt32(assembler, temp1, length, slow_path->GetEntryLabel(), temp2);
  }
}

static void GenArrayAddress(CodeGeneratorRISCV64* codegen,
                            XRegister dest,
                            XRegister base,
                            Location pos,
                            DataType::Type type,
                            int32_t data_offset) {
  Riscv64Assembler* assembler = codegen->GetAssembler();
  if (pos.IsConstant()) {
    int32_t constant = pos.GetConstant()->AsIntConstant()->GetValue();
    __ AddConst64(dest, base, DataType::Size(type) * constant + data_offset);
  } else {
    codegen->GetInstructionVisitor()->ShNAdd(dest, pos.AsRegister<XRegister>(), base, type);
    if (data_offset != 0) {
      __ AddConst64(dest, dest, data_offset);
    }
  }
}

// Compute base source address, base destination address, and end
// source address for System.arraycopy* intrinsics in `src_base`,
// `dst_base` and `src_end` respectively.
static void GenSystemArrayCopyAddresses(CodeGeneratorRISCV64* codegen,
                                        DataType::Type type,
                                        XRegister src,
                                        Location src_pos,
                                        XRegister dst,
                                        Location dst_pos,
                                        Location copy_length,
                                        XRegister src_base,
                                        XRegister dst_base,
                                        XRegister src_end) {
  // This routine is used by the SystemArrayCopy and the SystemArrayCopyChar intrinsics.
  DCHECK(type == DataType::Type::kReference || type == DataType::Type::kUint16)
      << "Unexpected element type: " << type;
  const int32_t element_size = DataType::Size(type);
  const uint32_t data_offset = mirror::Array::DataOffset(element_size).Uint32Value();

  GenArrayAddress(codegen, src_base, src, src_pos, type, data_offset);
  GenArrayAddress(codegen, dst_base, dst, dst_pos, type, data_offset);
  GenArrayAddress(codegen, src_end, src_base, copy_length, type, /*data_offset=*/ 0);
}

static Location LocationForSystemArrayCopyInput(HInstruction* input) {
  HIntConstant* const_input = input->AsIntConstantOrNull();
  if (const_input != nullptr && IsInt<12>(const_input->GetValue())) {
    return Location::ConstantLocation(const_input);
  } else {
    return Location::RequiresRegister();
  }
}

// We can choose to use the native implementation there for longer copy lengths.
static constexpr int32_t kSystemArrayCopyThreshold = 128;

void IntrinsicLocationsBuilderRISCV64::VisitSystemArrayCopy(HInvoke* invoke) {
  // The only read barrier implementation supporting the
  // SystemArrayCopy intrinsic is the Baker-style read barriers.
  if (codegen_->EmitNonBakerReadBarrier()) {
    return;
  }

  size_t num_temps = codegen_->EmitBakerReadBarrier() ? 4u : 2u;
  LocationSummary* locations = CodeGenerator::CreateSystemArrayCopyLocationSummary(
      invoke, kSystemArrayCopyThreshold, num_temps);
  if (locations != nullptr) {
    // We request position and length as constants only for small integral values.
    locations->SetInAt(1, LocationForSystemArrayCopyInput(invoke->InputAt(1)));
    locations->SetInAt(3, LocationForSystemArrayCopyInput(invoke->InputAt(3)));
    locations->SetInAt(4, LocationForSystemArrayCopyInput(invoke->InputAt(4)));
  }
}

void IntrinsicCodeGeneratorRISCV64::VisitSystemArrayCopy(HInvoke* invoke) {
  // The only read barrier implementation supporting the
  // SystemArrayCopy intrinsic is the Baker-style read barriers.
  DCHECK_IMPLIES(codegen_->EmitReadBarrier(), kUseBakerReadBarrier);

  Riscv64Assembler* assembler = GetAssembler();
  LocationSummary* locations = invoke->GetLocations();

  uint32_t class_offset = mirror::Object::ClassOffset().Int32Value();
  uint32_t super_offset = mirror::Class::SuperClassOffset().Int32Value();
  uint32_t component_offset = mirror::Class::ComponentTypeOffset().Int32Value();
  uint32_t primitive_offset = mirror::Class::PrimitiveTypeOffset().Int32Value();
  uint32_t monitor_offset = mirror::Object::MonitorOffset().Int32Value();

  XRegister src = locations->InAt(0).AsRegister<XRegister>();
  Location src_pos = locations->InAt(1);
  XRegister dest = locations->InAt(2).AsRegister<XRegister>();
  Location dest_pos = locations->InAt(3);
  Location length = locations->InAt(4);
  XRegister temp1 = locations->GetTemp(0).AsRegister<XRegister>();
  XRegister temp2 = locations->GetTemp(1).AsRegister<XRegister>();

  SlowPathCodeRISCV64* intrinsic_slow_path =
      new (codegen_->GetScopedAllocator()) IntrinsicSlowPathRISCV64(invoke);
  codegen_->AddSlowPath(intrinsic_slow_path);

  Riscv64Label conditions_on_positions_validated;
  SystemArrayCopyOptimizations optimizations(invoke);

  // If source and destination are the same, we go to slow path if we need to do forward copying.
  // We do not need to do this check if the source and destination positions are the same.
  if (!optimizations.GetSourcePositionIsDestinationPosition()) {
    if (src_pos.IsConstant()) {
      int32_t src_pos_constant = src_pos.GetConstant()->AsIntConstant()->GetValue();
      if (dest_pos.IsConstant()) {
        int32_t dest_pos_constant = dest_pos.GetConstant()->AsIntConstant()->GetValue();
        if (optimizations.GetDestinationIsSource()) {
          // Checked when building locations.
          DCHECK_GE(src_pos_constant, dest_pos_constant);
        } else if (src_pos_constant < dest_pos_constant) {
          __ Beq(src, dest, intrinsic_slow_path->GetEntryLabel());
        }
      } else {
        if (!optimizations.GetDestinationIsSource()) {
          __ Bne(src, dest, &conditions_on_positions_validated);
        }
        __ Li(temp1, src_pos_constant);
        __ Bgt(dest_pos.AsRegister<XRegister>(), temp1, intrinsic_slow_path->GetEntryLabel());
      }
    } else {
      if (!optimizations.GetDestinationIsSource()) {
        __ Bne(src, dest, &conditions_on_positions_validated);
      }
      XRegister src_pos_reg = src_pos.AsRegister<XRegister>();
      EmitBlt32(assembler, src_pos_reg, dest_pos, intrinsic_slow_path->GetEntryLabel(), temp2);
    }
  }

  __ Bind(&conditions_on_positions_validated);

  if (!optimizations.GetSourceIsNotNull()) {
    // Bail out if the source is null.
    __ Beqz(src, intrinsic_slow_path->GetEntryLabel());
  }

  if (!optimizations.GetDestinationIsNotNull() && !optimizations.GetDestinationIsSource()) {
    // Bail out if the destination is null.
    __ Beqz(dest, intrinsic_slow_path->GetEntryLabel());
  }

  // We have already checked in the LocationsBuilder for the constant case.
  if (!length.IsConstant()) {
    // Merge the following two comparisons into one:
    //   If the length is negative, bail out (delegate to libcore's native implementation).
    //   If the length >= 128 then (currently) prefer native implementation.
    __ Li(temp1, kSystemArrayCopyThreshold);
    __ Bgeu(length.AsRegister<XRegister>(), temp1, intrinsic_slow_path->GetEntryLabel());
  }
  // Validity checks: source.
  CheckSystemArrayCopyPosition(assembler,
                               src,
                               src_pos,
                               length,
                               intrinsic_slow_path,
                               temp1,
                               temp2,
                               optimizations.GetCountIsSourceLength(),
                               /*position_sign_checked=*/ false);

  // Validity checks: dest.
  bool dest_position_sign_checked = optimizations.GetSourcePositionIsDestinationPosition();
  CheckSystemArrayCopyPosition(assembler,
                               dest,
                               dest_pos,
                               length,
                               intrinsic_slow_path,
                               temp1,
                               temp2,
                               optimizations.GetCountIsDestinationLength(),
                               dest_position_sign_checked);

  auto check_non_primitive_array_class = [&](XRegister klass, XRegister temp) {
    // No read barrier is needed for reading a chain of constant references for comparing
    // with null, or for reading a constant primitive value, see `ReadBarrierOption`.
    // /* HeapReference<Class> */ temp = klass->component_type_
    __ Loadwu(temp, klass, component_offset);
    codegen_->MaybeUnpoisonHeapReference(temp);
    // Check that the component type is not null.
    __ Beqz(temp, intrinsic_slow_path->GetEntryLabel());
    // Check that the component type is not a primitive.
    // /* uint16_t */ temp = static_cast<uint16>(klass->primitive_type_);
    __ Loadhu(temp, temp, primitive_offset);
    static_assert(Primitive::kPrimNot == 0, "Expected 0 for kPrimNot");
    __ Bnez(temp, intrinsic_slow_path->GetEntryLabel());
  };

  if (!optimizations.GetDoesNotNeedTypeCheck()) {
    // Check whether all elements of the source array are assignable to the component
    // type of the destination array. We do two checks: the classes are the same,
    // or the destination is Object[]. If none of these checks succeed, we go to the
    // slow path.

    if (codegen_->EmitBakerReadBarrier()) {
      XRegister temp3 = locations->GetTemp(2).AsRegister<XRegister>();
      // /* HeapReference<Class> */ temp1 = dest->klass_
      codegen_->GenerateFieldLoadWithBakerReadBarrier(invoke,
                                                      Location::RegisterLocation(temp1),
                                                      dest,
                                                      class_offset,
                                                      Location::RegisterLocation(temp3),
                                                      /* needs_null_check= */ false);
      // /* HeapReference<Class> */ temp2 = src->klass_
      codegen_->GenerateFieldLoadWithBakerReadBarrier(invoke,
                                                      Location::RegisterLocation(temp2),
                                                      src,
                                                      class_offset,
                                                      Location::RegisterLocation(temp3),
                                                      /* needs_null_check= */ false);
    } else {
      // /* HeapReference<Class> */ temp1 = dest->klass_
      __ Loadwu(temp1, dest, class_offset);
      codegen_->MaybeUnpoisonHeapReference(temp1);
      // /* HeapReference<Class> */ temp2 = src->klass_
      __ Loadwu(temp2, src, class_offset);
      codegen_->MaybeUnpoisonHeapReference(temp2);
    }

    if (optimizations.GetDestinationIsTypedObjectArray()) {
      DCHECK(optimizations.GetDestinationIsNonPrimitiveArray());
      Riscv64Label do_copy;
      // For class match, we can skip the source type check regardless of the optimization flag.
      __ Beq(temp1, temp2, &do_copy);
      // No read barrier is needed for reading a chain of constant references
      // for comparing with null, see `ReadBarrierOption`.
      // /* HeapReference<Class> */ temp1 = temp1->component_type_
      __ Loadwu(temp1, temp1, component_offset);
      codegen_->MaybeUnpoisonHeapReference(temp1);
      // /* HeapReference<Class> */ temp1 = temp1->super_class_
      __ Loadwu(temp1, temp1, super_offset);
      // No need to unpoison the result, we're comparing against null.
      __ Bnez(temp1, intrinsic_slow_path->GetEntryLabel());
      // Bail out if the source is not a non primitive array.
      if (!optimizations.GetSourceIsNonPrimitiveArray()) {
        check_non_primitive_array_class(temp2, temp2);
      }
      __ Bind(&do_copy);
    } else {
      DCHECK(!optimizations.GetDestinationIsTypedObjectArray());
      // For class match, we can skip the array type check completely if at least one of source
      // and destination is known to be a non primitive array, otherwise one check is enough.
      __ Bne(temp1, temp2, intrinsic_slow_path->GetEntryLabel());
      if (!optimizations.GetDestinationIsNonPrimitiveArray() &&
          !optimizations.GetSourceIsNonPrimitiveArray()) {
        check_non_primitive_array_class(temp2, temp2);
      }
    }
  } else if (!optimizations.GetSourceIsNonPrimitiveArray()) {
    DCHECK(optimizations.GetDestinationIsNonPrimitiveArray());
    // Bail out if the source is not a non primitive array.
    // No read barrier is needed for reading a chain of constant references for comparing
    // with null, or for reading a constant primitive value, see `ReadBarrierOption`.
    // /* HeapReference<Class> */ temp2 = src->klass_
    __ Loadwu(temp2, src, class_offset);
    codegen_->MaybeUnpoisonHeapReference(temp2);
    check_non_primitive_array_class(temp2, temp2);
  }

  if (length.IsConstant() && length.GetConstant()->AsIntConstant()->GetValue() == 0) {
    // Null constant length: not need to emit the loop code at all.
  } else {
    Riscv64Label skip_copy_and_write_barrier;
    if (length.IsRegister()) {
      // Don't enter the copy loop if the length is null.
      __ Beqz(length.AsRegister<XRegister>(), &skip_copy_and_write_barrier);
    }

    {
      // We use a block to end the scratch scope before the write barrier, thus
      // freeing the scratch registers so they can be used in `MarkGCCard`.
      ScratchRegisterScope srs(assembler);
      bool emit_rb = codegen_->EmitBakerReadBarrier();
      XRegister temp3 =
          emit_rb ? locations->GetTemp(2).AsRegister<XRegister>() : srs.AllocateXRegister();

      XRegister src_curr_addr = temp1;
      XRegister dst_curr_addr = temp2;
      XRegister src_stop_addr = temp3;
      const DataType::Type type = DataType::Type::kReference;
      const int32_t element_size = DataType::Size(type);

      XRegister tmp = kNoXRegister;
      SlowPathCodeRISCV64* read_barrier_slow_path = nullptr;
      if (emit_rb) {
        // TODO: Also convert this intrinsic to the IsGcMarking strategy?

        // SystemArrayCopy implementation for Baker read barriers (see
        // also CodeGeneratorRISCV64::GenerateReferenceLoadWithBakerReadBarrier):
        //
        //   uint32_t rb_state = Lockword(src->monitor_).ReadBarrierState();
        //   lfence;  // Load fence or artificial data dependency to prevent load-load reordering
        //   bool is_gray = (rb_state == ReadBarrier::GrayState());
        //   if (is_gray) {
        //     // Slow-path copy.
        //     do {
        //       *dest_ptr++ = MaybePoison(ReadBarrier::Mark(MaybeUnpoison(*src_ptr++)));
        //     } while (src_ptr != end_ptr)
        //   } else {
        //     // Fast-path copy.
        //     do {
        //       *dest_ptr++ = *src_ptr++;
        //     } while (src_ptr != end_ptr)
        //   }

        // /* uint32_t */ monitor = src->monitor_
        tmp = locations->GetTemp(3).AsRegister<XRegister>();
        __ Loadwu(tmp, src, monitor_offset);
        // /* LockWord */ lock_word = LockWord(monitor)
        static_assert(sizeof(LockWord) == sizeof(int32_t),
                      "art::LockWord and int32_t have different sizes.");

        // Shift the RB state bit to the sign bit while also clearing the low 32 bits
        // for the fake dependency below.
        static_assert(LockWord::kReadBarrierStateShift < 31);
        __ Slli(tmp, tmp, 63 - LockWord::kReadBarrierStateShift);

        // Introduce a dependency on the lock_word including rb_state, to prevent load-load
        // reordering, and without using a memory barrier (which would be more expensive).
        // `src` is unchanged by this operation (since Adduw adds low 32 bits
        // which are zero after left shift), but its value now depends on `tmp`.
        __ AddUw(src, tmp, src);

        // Slow path used to copy array when `src` is gray.
        read_barrier_slow_path = new (codegen_->GetScopedAllocator())
            ReadBarrierSystemArrayCopySlowPathRISCV64(invoke, Location::RegisterLocation(tmp));
        codegen_->AddSlowPath(read_barrier_slow_path);
      }

      // Compute base source address, base destination address, and end source address for
      // System.arraycopy* intrinsics in `src_base`, `dst_base` and `src_end` respectively.
      // Note that `src_curr_addr` is computed from from `src` (and `src_pos`) here, and
      // thus honors the artificial dependency of `src` on `tmp` for read barriers.
      GenSystemArrayCopyAddresses(codegen_,
                                  type,
                                  src,
                                  src_pos,
                                  dest,
                                  dest_pos,
                                  length,
                                  src_curr_addr,
                                  dst_curr_addr,
                                  src_stop_addr);

      if (emit_rb) {
        // Given the numeric representation, it's enough to check the low bit of the RB state.
        static_assert(ReadBarrier::NonGrayState() == 0, "Expecting non-gray to have value 0");
        static_assert(ReadBarrier::GrayState() == 1, "Expecting gray to have value 1");
        DCHECK_NE(tmp, kNoXRegister);
        __ Bltz(tmp, read_barrier_slow_path->GetEntryLabel());
      } else {
        // After allocating the last scrach register, we cannot use macro load/store instructions
        // such as `Loadwu()` and need to use raw instructions. However, all offsets below are 0.
        DCHECK_EQ(tmp, kNoXRegister);
        tmp = srs.AllocateXRegister();
      }

      // Iterate over the arrays and do a raw copy of the objects. We don't need to
      // poison/unpoison.
      Riscv64Label loop;
      __ Bind(&loop);
      __ Lwu(tmp, src_curr_addr, 0);
      __ Sw(tmp, dst_curr_addr, 0);
      __ Addi(src_curr_addr, src_curr_addr, element_size);
      __ Addi(dst_curr_addr, dst_curr_addr, element_size);
      // Bare: `TMP` shall not be clobbered.
      __ Bne(src_curr_addr, src_stop_addr, &loop, /*is_bare=*/ true);

      if (emit_rb) {
        DCHECK(read_barrier_slow_path != nullptr);
        __ Bind(read_barrier_slow_path->GetExitLabel());
      }
    }

    // We only need one card marking on the destination array.
    codegen_->MarkGCCard(dest);

    __ Bind(&skip_copy_and_write_barrier);
  }

  __ Bind(intrinsic_slow_path->GetExitLabel());
}

enum class GetAndUpdateOp {
  kSet,
  kAdd,
  kAnd,
  kOr,
  kXor
};

// Generate a GetAndUpdate operation.
//
// Only 32-bit and 64-bit atomics are currently supported, therefore smaller types need
// special handling. The caller emits code to prepare aligned `ptr` and adjusted `arg`
// and extract the needed bits from `old_value`. For bitwise operations, no extra
// handling is needed here. For `GetAndUpdateOp::kSet` and `GetAndUpdateOp::kAdd` we
// also use a special LR/SC sequence that uses a `mask` to update only the desired bits.
// Note: The `mask` must contain the bits to keep for `GetAndUpdateOp::kSet` and
// the bits to replace for `GetAndUpdateOp::kAdd`.
static void GenerateGetAndUpdate(CodeGeneratorRISCV64* codegen,
                                 GetAndUpdateOp get_and_update_op,
                                 DataType::Type type,
                                 std::memory_order order,
                                 XRegister ptr,
                                 XRegister arg,
                                 XRegister old_value,
                                 XRegister mask,
                                 XRegister temp) {
  DCHECK_EQ(mask != kNoXRegister, temp != kNoXRegister);
  DCHECK_IMPLIES(mask != kNoXRegister, type == DataType::Type::kInt32);
  DCHECK_IMPLIES(
      mask != kNoXRegister,
      (get_and_update_op == GetAndUpdateOp::kSet) || (get_and_update_op == GetAndUpdateOp::kAdd));
  Riscv64Assembler* assembler = codegen->GetAssembler();
  AqRl amo_aqrl = GetAmoAqRl(order);
  switch (get_and_update_op) {
    case GetAndUpdateOp::kSet:
      if (type == DataType::Type::kInt64) {
        __ AmoSwapD(old_value, arg, ptr, amo_aqrl);
      } else if (mask == kNoXRegister) {
        DCHECK_EQ(type, DataType::Type::kInt32);
        __ AmoSwapW(old_value, arg, ptr, amo_aqrl);
      } else {
        DCHECK_EQ(type, DataType::Type::kInt32);
        DCHECK_NE(temp, kNoXRegister);
        auto [load_aqrl, store_aqrl] = GetLrScAqRl(order);
        Riscv64Label retry;
        __ Bind(&retry);
        __ LrW(old_value, ptr, load_aqrl);
        {
          ScopedLrScExtensionsRestriction slser(assembler);
          __ And(temp, old_value, mask);
          __ Or(temp, temp, arg);
        }
        __ ScW(temp, temp, ptr, store_aqrl);
        __ Bnez(temp, &retry, /*is_bare=*/ true);  // Bare: `TMP` shall not be clobbered.
      }
      break;
    case GetAndUpdateOp::kAdd:
      if (type == DataType::Type::kInt64) {
        __ AmoAddD(old_value, arg, ptr, amo_aqrl);
      } else if (mask == kNoXRegister) {
        DCHECK_EQ(type, DataType::Type::kInt32);
         __ AmoAddW(old_value, arg, ptr, amo_aqrl);
      } else {
        DCHECK_EQ(type, DataType::Type::kInt32);
        DCHECK_NE(temp, kNoXRegister);
        auto [load_aqrl, store_aqrl] = GetLrScAqRl(order);
        Riscv64Label retry;
        __ Bind(&retry);
        __ LrW(old_value, ptr, load_aqrl);
        {
          ScopedLrScExtensionsRestriction slser(assembler);
          __ Add(temp, old_value, arg);
          // We use `(A ^ B) ^ A == B` and with the masking `((A ^ B) & mask) ^ A`, the result
          // contains bits from `B` for bits specified in `mask` and bits from `A` elsewhere.
          // Note: These instructions directly depend on each other, so it's not necessarily the
          // fastest approach but for `(A ^ ~mask) | (B & mask)` we would need an extra register
          // for `~mask` because ANDN is not in the "I" instruction set as required for a LR/SC
          // sequence.
          __ Xor(temp, temp, old_value);
          __ And(temp, temp, mask);
          __ Xor(temp, temp, old_value);
        }
        __ ScW(temp, temp, ptr, store_aqrl);
        __ Bnez(temp, &retry, /*is_bare=*/ true);  // Bare: `TMP` shall not be clobbered.
      }
      break;
    case GetAndUpdateOp::kAnd:
      if (type == DataType::Type::kInt64) {
        __ AmoAndD(old_value, arg, ptr, amo_aqrl);
      } else {
        DCHECK_EQ(type, DataType::Type::kInt32);
        __ AmoAndW(old_value, arg, ptr, amo_aqrl);
      }
      break;
    case GetAndUpdateOp::kOr:
      if (type == DataType::Type::kInt64) {
        __ AmoOrD(old_value, arg, ptr, amo_aqrl);
      } else {
        DCHECK_EQ(type, DataType::Type::kInt32);
        __ AmoOrW(old_value, arg, ptr, amo_aqrl);
      }
      break;
    case GetAndUpdateOp::kXor:
      if (type == DataType::Type::kInt64) {
        __ AmoXorD(old_value, arg, ptr, amo_aqrl);
      } else {
        DCHECK_EQ(type, DataType::Type::kInt32);
        __ AmoXorW(old_value, arg, ptr, amo_aqrl);
      }
      break;
  }
}

static void CreateUnsafeGetLocations(ArenaAllocator* allocator,
                                     HInvoke* invoke,
                                     CodeGeneratorRISCV64* codegen) {
  bool can_call = codegen->EmitReadBarrier() && IsUnsafeGetReference(invoke);
  LocationSummary* locations = new (allocator) LocationSummary(
      invoke,
      can_call ? LocationSummary::kCallOnSlowPath : LocationSummary::kNoCall,
      kIntrinsified);
  if (can_call && kUseBakerReadBarrier) {
    locations->SetCustomSlowPathCallerSaves(RegisterSet::Empty());  // No caller-save registers.
  }
  locations->SetInAt(0, Location::NoLocation());        // Unused receiver.
  locations->SetInAt(1, Location::RequiresRegister());
  locations->SetInAt(2, Location::RequiresRegister());
  locations->SetOut(Location::RequiresRegister(),
                    (can_call ? Location::kOutputOverlap : Location::kNoOutputOverlap));
}

static void GenUnsafeGet(HInvoke* invoke,
                         CodeGeneratorRISCV64* codegen,
                         std::memory_order order,
                         DataType::Type type) {
  DCHECK((type == DataType::Type::kInt8) ||
         (type == DataType::Type::kInt32) ||
         (type == DataType::Type::kInt64) ||
         (type == DataType::Type::kReference));
  LocationSummary* locations = invoke->GetLocations();
  Location object_loc = locations->InAt(1);
  XRegister object = object_loc.AsRegister<XRegister>();  // Object pointer.
  Location offset_loc = locations->InAt(2);
  XRegister offset = offset_loc.AsRegister<XRegister>();  // Long offset.
  Location out_loc = locations->Out();
  XRegister out = out_loc.AsRegister<XRegister>();

  bool seq_cst_barrier = (order == std::memory_order_seq_cst);
  bool acquire_barrier = seq_cst_barrier || (order == std::memory_order_acquire);
  DCHECK(acquire_barrier || order == std::memory_order_relaxed);

  if (seq_cst_barrier) {
    codegen->GenerateMemoryBarrier(MemBarrierKind::kAnyAny);
  }

  if (type == DataType::Type::kReference && codegen->EmitBakerReadBarrier()) {
    // JdkUnsafeGetReference/JdkUnsafeGetReferenceVolatile with Baker's read barrier case.
    // TODO(riscv64): Revisit when we add checking if the holder is black.
    Location temp = Location::NoLocation();
    codegen->GenerateReferenceLoadWithBakerReadBarrier(invoke,
                                                       out_loc,
                                                       object,
                                                       /*offset=*/ 0,
                                                       /*index=*/ offset_loc,
                                                       temp,
                                                       /*needs_null_check=*/ false);
  } else {
    // Other cases.
    Riscv64Assembler* assembler = codegen->GetAssembler();
    __ Add(out, object, offset);
    codegen->GetInstructionVisitor()->Load(out_loc, out, /*offset=*/ 0, type);

    if (type == DataType::Type::kReference) {
      codegen->MaybeGenerateReadBarrierSlow(
          invoke, out_loc, out_loc, object_loc, /*offset=*/ 0u, /*index=*/ offset_loc);
    }
  }

  if (acquire_barrier) {
    codegen->GenerateMemoryBarrier(MemBarrierKind::kLoadAny);
  }
}

void IntrinsicLocationsBuilderRISCV64::VisitUnsafeGet(HInvoke* invoke) {
  VisitJdkUnsafeGet(invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitUnsafeGet(HInvoke* invoke) {
  VisitJdkUnsafeGet(invoke);
}

void IntrinsicLocationsBuilderRISCV64::VisitUnsafeGetVolatile(HInvoke* invoke) {
  VisitJdkUnsafeGetVolatile(invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitUnsafeGetVolatile(HInvoke* invoke) {
  VisitJdkUnsafeGetVolatile(invoke);
}

void IntrinsicLocationsBuilderRISCV64::VisitUnsafeGetObject(HInvoke* invoke) {
  VisitJdkUnsafeGetReference(invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitUnsafeGetObject(HInvoke* invoke) {
  VisitJdkUnsafeGetReference(invoke);
}

void IntrinsicLocationsBuilderRISCV64::VisitUnsafeGetObjectVolatile(HInvoke* invoke) {
  VisitJdkUnsafeGetReferenceVolatile(invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitUnsafeGetObjectVolatile(HInvoke* invoke) {
  VisitJdkUnsafeGetReferenceVolatile(invoke);
}

void IntrinsicLocationsBuilderRISCV64::VisitUnsafeGetLong(HInvoke* invoke) {
  VisitJdkUnsafeGetLong(invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitUnsafeGetLong(HInvoke* invoke) {
  VisitJdkUnsafeGetLong(invoke);
}

void IntrinsicLocationsBuilderRISCV64::VisitUnsafeGetLongVolatile(HInvoke* invoke) {
  VisitJdkUnsafeGetLongVolatile(invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitUnsafeGetLongVolatile(HInvoke* invoke) {
  VisitJdkUnsafeGetLongVolatile(invoke);
}

void IntrinsicLocationsBuilderRISCV64::VisitUnsafeGetByte(HInvoke* invoke) {
  VisitJdkUnsafeGetByte(invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitUnsafeGetByte(HInvoke* invoke) {
  VisitJdkUnsafeGetByte(invoke);
}

void IntrinsicLocationsBuilderRISCV64::VisitJdkUnsafeGet(HInvoke* invoke) {
  CreateUnsafeGetLocations(allocator_, invoke, codegen_);
}

void IntrinsicCodeGeneratorRISCV64::VisitJdkUnsafeGet(HInvoke* invoke) {
  GenUnsafeGet(invoke, codegen_, std::memory_order_relaxed, DataType::Type::kInt32);
}

void IntrinsicLocationsBuilderRISCV64::VisitJdkUnsafeGetAcquire(HInvoke* invoke) {
  CreateUnsafeGetLocations(allocator_, invoke, codegen_);
}

void IntrinsicCodeGeneratorRISCV64::VisitJdkUnsafeGetAcquire(HInvoke* invoke) {
  GenUnsafeGet(invoke, codegen_, std::memory_order_acquire, DataType::Type::kInt32);
}

void IntrinsicLocationsBuilderRISCV64::VisitJdkUnsafeGetVolatile(HInvoke* invoke) {
  CreateUnsafeGetLocations(allocator_, invoke, codegen_);
}

void IntrinsicCodeGeneratorRISCV64::VisitJdkUnsafeGetVolatile(HInvoke* invoke) {
  GenUnsafeGet(invoke, codegen_, std::memory_order_seq_cst, DataType::Type::kInt32);
}

void IntrinsicLocationsBuilderRISCV64::VisitJdkUnsafeGetReference(HInvoke* invoke) {
  CreateUnsafeGetLocations(allocator_, invoke, codegen_);
}

void IntrinsicCodeGeneratorRISCV64::VisitJdkUnsafeGetReference(HInvoke* invoke) {
  GenUnsafeGet(invoke, codegen_, std::memory_order_relaxed, DataType::Type::kReference);
}

void IntrinsicLocationsBuilderRISCV64::VisitJdkUnsafeGetReferenceAcquire(HInvoke* invoke) {
  CreateUnsafeGetLocations(allocator_, invoke, codegen_);
}

void IntrinsicCodeGeneratorRISCV64::VisitJdkUnsafeGetReferenceAcquire(HInvoke* invoke) {
  GenUnsafeGet(invoke, codegen_, std::memory_order_acquire, DataType::Type::kReference);
}

void IntrinsicLocationsBuilderRISCV64::VisitJdkUnsafeGetReferenceVolatile(HInvoke* invoke) {
  CreateUnsafeGetLocations(allocator_, invoke, codegen_);
}

void IntrinsicCodeGeneratorRISCV64::VisitJdkUnsafeGetReferenceVolatile(HInvoke* invoke) {
  GenUnsafeGet(invoke, codegen_, std::memory_order_seq_cst, DataType::Type::kReference);
}

void IntrinsicLocationsBuilderRISCV64::VisitJdkUnsafeGetLong(HInvoke* invoke) {
  CreateUnsafeGetLocations(allocator_, invoke, codegen_);
}

void IntrinsicCodeGeneratorRISCV64::VisitJdkUnsafeGetLong(HInvoke* invoke) {
  GenUnsafeGet(invoke, codegen_, std::memory_order_relaxed, DataType::Type::kInt64);
}

void IntrinsicLocationsBuilderRISCV64::VisitJdkUnsafeGetLongAcquire(HInvoke* invoke) {
  CreateUnsafeGetLocations(allocator_, invoke, codegen_);
}

void IntrinsicCodeGeneratorRISCV64::VisitJdkUnsafeGetLongAcquire(HInvoke* invoke) {
  GenUnsafeGet(invoke, codegen_, std::memory_order_acquire, DataType::Type::kInt64);
}

void IntrinsicLocationsBuilderRISCV64::VisitJdkUnsafeGetLongVolatile(HInvoke* invoke) {
  CreateUnsafeGetLocations(allocator_, invoke, codegen_);
}

void IntrinsicCodeGeneratorRISCV64::VisitJdkUnsafeGetLongVolatile(HInvoke* invoke) {
  GenUnsafeGet(invoke, codegen_, std::memory_order_seq_cst, DataType::Type::kInt64);
}

void IntrinsicLocationsBuilderRISCV64::VisitJdkUnsafeGetByte(HInvoke* invoke) {
  CreateUnsafeGetLocations(allocator_, invoke, codegen_);
}

void IntrinsicCodeGeneratorRISCV64::VisitJdkUnsafeGetByte(HInvoke* invoke) {
  GenUnsafeGet(invoke, codegen_, std::memory_order_relaxed, DataType::Type::kInt8);
}

static void CreateUnsafePutLocations(ArenaAllocator* allocator, HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::NoLocation());        // Unused receiver.
  locations->SetInAt(1, Location::RequiresRegister());
  locations->SetInAt(2, Location::RequiresRegister());
  locations->SetInAt(3, Location::RequiresRegister());
  if (kPoisonHeapReferences && invoke->InputAt(3)->GetType() == DataType::Type::kReference) {
    locations->AddTemp(Location::RequiresRegister());
  }
}

static void GenUnsafePut(HInvoke* invoke,
                         CodeGeneratorRISCV64* codegen,
                         std::memory_order order,
                         DataType::Type type) {
  Riscv64Assembler* assembler = codegen->GetAssembler();
  LocationSummary* locations = invoke->GetLocations();
  XRegister base = locations->InAt(1).AsRegister<XRegister>();    // Object pointer.
  XRegister offset = locations->InAt(2).AsRegister<XRegister>();  // Long offset.
  Location value = locations->InAt(3);

  {
    // We use a block to end the scratch scope before the write barrier, thus
    // freeing the temporary registers so they can be used in `MarkGCCard()`.
    ScratchRegisterScope srs(assembler);
    // Heap poisoning needs two scratch registers in `Store()`.
    XRegister address = (kPoisonHeapReferences && type == DataType::Type::kReference)
        ? locations->GetTemp(0).AsRegister<XRegister>()
        : srs.AllocateXRegister();
    __ Add(address, base, offset);
    GenerateSet(codegen, order, value, address, /*offset=*/ 0, type);
  }

  if (type == DataType::Type::kReference) {
    bool value_can_be_null = true;  // TODO: Worth finding out this information?
    codegen->MaybeMarkGCCard(base, value.AsRegister<XRegister>(), value_can_be_null);
  }
}

void IntrinsicLocationsBuilderRISCV64::VisitUnsafePut(HInvoke* invoke) {
  VisitJdkUnsafePut(invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitUnsafePut(HInvoke* invoke) {
  VisitJdkUnsafePut(invoke);
}

void IntrinsicLocationsBuilderRISCV64::VisitUnsafePutOrdered(HInvoke* invoke) {
  VisitJdkUnsafePutOrdered(invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitUnsafePutOrdered(HInvoke* invoke) {
  VisitJdkUnsafePutOrdered(invoke);
}

void IntrinsicLocationsBuilderRISCV64::VisitUnsafePutVolatile(HInvoke* invoke) {
  VisitJdkUnsafePutVolatile(invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitUnsafePutVolatile(HInvoke* invoke) {
  VisitJdkUnsafePutVolatile(invoke);
}

void IntrinsicLocationsBuilderRISCV64::VisitUnsafePutObject(HInvoke* invoke) {
  VisitJdkUnsafePutReference(invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitUnsafePutObject(HInvoke* invoke) {
  VisitJdkUnsafePutReference(invoke);
}

void IntrinsicLocationsBuilderRISCV64::VisitUnsafePutObjectOrdered(HInvoke* invoke) {
  VisitJdkUnsafePutObjectOrdered(invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitUnsafePutObjectOrdered(HInvoke* invoke) {
  VisitJdkUnsafePutObjectOrdered(invoke);
}

void IntrinsicLocationsBuilderRISCV64::VisitUnsafePutObjectVolatile(HInvoke* invoke) {
  VisitJdkUnsafePutReferenceVolatile(invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitUnsafePutObjectVolatile(HInvoke* invoke) {
  VisitJdkUnsafePutReferenceVolatile(invoke);
}

void IntrinsicLocationsBuilderRISCV64::VisitUnsafePutLong(HInvoke* invoke) {
  VisitJdkUnsafePutLong(invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitUnsafePutLong(HInvoke* invoke) {
  VisitJdkUnsafePutLong(invoke);
}

void IntrinsicLocationsBuilderRISCV64::VisitUnsafePutLongOrdered(HInvoke* invoke) {
  VisitJdkUnsafePutLongOrdered(invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitUnsafePutLongOrdered(HInvoke* invoke) {
  VisitJdkUnsafePutLongOrdered(invoke);
}

void IntrinsicLocationsBuilderRISCV64::VisitUnsafePutLongVolatile(HInvoke* invoke) {
  VisitJdkUnsafePutLongVolatile(invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitUnsafePutLongVolatile(HInvoke* invoke) {
  VisitJdkUnsafePutLongVolatile(invoke);
}

void IntrinsicLocationsBuilderRISCV64::VisitUnsafePutByte(HInvoke* invoke) {
  VisitJdkUnsafePutByte(invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitUnsafePutByte(HInvoke* invoke) {
  VisitJdkUnsafePutByte(invoke);
}

void IntrinsicLocationsBuilderRISCV64::VisitJdkUnsafePut(HInvoke* invoke) {
  CreateUnsafePutLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitJdkUnsafePut(HInvoke* invoke) {
  GenUnsafePut(invoke, codegen_, std::memory_order_relaxed, DataType::Type::kInt32);
}

void IntrinsicLocationsBuilderRISCV64::VisitJdkUnsafePutOrdered(HInvoke* invoke) {
  CreateUnsafePutLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitJdkUnsafePutOrdered(HInvoke* invoke) {
  GenUnsafePut(invoke, codegen_, std::memory_order_release, DataType::Type::kInt32);
}

void IntrinsicLocationsBuilderRISCV64::VisitJdkUnsafePutRelease(HInvoke* invoke) {
  CreateUnsafePutLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitJdkUnsafePutRelease(HInvoke* invoke) {
  GenUnsafePut(invoke, codegen_, std::memory_order_release, DataType::Type::kInt32);
}

void IntrinsicLocationsBuilderRISCV64::VisitJdkUnsafePutVolatile(HInvoke* invoke) {
  CreateUnsafePutLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitJdkUnsafePutVolatile(HInvoke* invoke) {
  GenUnsafePut(invoke, codegen_, std::memory_order_seq_cst, DataType::Type::kInt32);
}

void IntrinsicLocationsBuilderRISCV64::VisitJdkUnsafePutReference(HInvoke* invoke) {
  CreateUnsafePutLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitJdkUnsafePutReference(HInvoke* invoke) {
  GenUnsafePut(invoke, codegen_, std::memory_order_relaxed, DataType::Type::kReference);
}

void IntrinsicLocationsBuilderRISCV64::VisitJdkUnsafePutObjectOrdered(HInvoke* invoke) {
  CreateUnsafePutLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitJdkUnsafePutObjectOrdered(HInvoke* invoke) {
  GenUnsafePut(invoke, codegen_, std::memory_order_release, DataType::Type::kReference);
}

void IntrinsicLocationsBuilderRISCV64::VisitJdkUnsafePutReferenceRelease(HInvoke* invoke) {
  CreateUnsafePutLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitJdkUnsafePutReferenceRelease(HInvoke* invoke) {
  GenUnsafePut(invoke, codegen_, std::memory_order_release, DataType::Type::kReference);
}

void IntrinsicLocationsBuilderRISCV64::VisitJdkUnsafePutReferenceVolatile(HInvoke* invoke) {
  CreateUnsafePutLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitJdkUnsafePutReferenceVolatile(HInvoke* invoke) {
  GenUnsafePut(invoke, codegen_, std::memory_order_seq_cst, DataType::Type::kReference);
}

void IntrinsicLocationsBuilderRISCV64::VisitJdkUnsafePutLong(HInvoke* invoke) {
  CreateUnsafePutLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitJdkUnsafePutLong(HInvoke* invoke) {
  GenUnsafePut(invoke, codegen_, std::memory_order_relaxed, DataType::Type::kInt64);
}

void IntrinsicLocationsBuilderRISCV64::VisitJdkUnsafePutLongOrdered(HInvoke* invoke) {
  CreateUnsafePutLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitJdkUnsafePutLongOrdered(HInvoke* invoke) {
  GenUnsafePut(invoke, codegen_, std::memory_order_release, DataType::Type::kInt64);
}

void IntrinsicLocationsBuilderRISCV64::VisitJdkUnsafePutLongRelease(HInvoke* invoke) {
  CreateUnsafePutLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitJdkUnsafePutLongRelease(HInvoke* invoke) {
  GenUnsafePut(invoke, codegen_, std::memory_order_release, DataType::Type::kInt64);
}

void IntrinsicLocationsBuilderRISCV64::VisitJdkUnsafePutLongVolatile(HInvoke* invoke) {
  CreateUnsafePutLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitJdkUnsafePutLongVolatile(HInvoke* invoke) {
  GenUnsafePut(invoke, codegen_, std::memory_order_seq_cst, DataType::Type::kInt64);
}

void IntrinsicLocationsBuilderRISCV64::VisitJdkUnsafePutByte(HInvoke* invoke) {
  CreateUnsafePutLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitJdkUnsafePutByte(HInvoke* invoke) {
  GenUnsafePut(invoke, codegen_, std::memory_order_relaxed, DataType::Type::kInt8);
}

static void CreateUnsafeCASLocations(ArenaAllocator* allocator,
                                     HInvoke* invoke,
                                     CodeGeneratorRISCV64* codegen) {
  const bool can_call = codegen->EmitReadBarrier() && IsUnsafeCASReference(invoke);
  LocationSummary* locations = new (allocator) LocationSummary(
      invoke,
      can_call ? LocationSummary::kCallOnSlowPath : LocationSummary::kNoCall,
      kIntrinsified);
  if (can_call && kUseBakerReadBarrier) {
    locations->SetCustomSlowPathCallerSaves(RegisterSet::Empty());  // No caller-save registers.
  }
  locations->SetInAt(0, Location::NoLocation());        // Unused receiver.
  locations->SetInAt(1, Location::RequiresRegister());
  locations->SetInAt(2, Location::RequiresRegister());
  locations->SetInAt(3, Location::RequiresRegister());
  locations->SetInAt(4, Location::RequiresRegister());

  locations->SetOut(Location::RequiresRegister());
}

static void GenUnsafeCas(HInvoke* invoke, CodeGeneratorRISCV64* codegen, DataType::Type type) {
  Riscv64Assembler* assembler = codegen->GetAssembler();
  LocationSummary* locations = invoke->GetLocations();
  XRegister out = locations->Out().AsRegister<XRegister>();            // Boolean result.
  XRegister object = locations->InAt(1).AsRegister<XRegister>();       // Object pointer.
  XRegister offset = locations->InAt(2).AsRegister<XRegister>();       // Long offset.
  XRegister expected = locations->InAt(3).AsRegister<XRegister>();     // Expected.
  XRegister new_value = locations->InAt(4).AsRegister<XRegister>();    // New value.

  // This needs to be before the temp registers, as MarkGCCard also uses scratch registers.
  if (type == DataType::Type::kReference) {
    // Mark card for object assuming new value is stored.
    bool new_value_can_be_null = true;  // TODO: Worth finding out this information?
    codegen->MaybeMarkGCCard(object, new_value, new_value_can_be_null);
  }

  ScratchRegisterScope srs(assembler);
  XRegister tmp_ptr = srs.AllocateXRegister();                         // Pointer to actual memory.
  XRegister old_value;                                                 // Value in memory.

  Riscv64Label exit_loop_label;
  Riscv64Label* exit_loop = &exit_loop_label;
  Riscv64Label* cmp_failure = &exit_loop_label;

  ReadBarrierCasSlowPathRISCV64* slow_path = nullptr;
  if (type == DataType::Type::kReference && codegen->EmitReadBarrier()) {
    // We need to store the `old_value` in a non-scratch register to make sure
    // the read barrier in the slow path does not clobber it.
    old_value = locations->GetTemp(0).AsRegister<XRegister>();  // The old value from main path.
    // The `old_value_temp` is used first for marking the `old_value` and then for the unmarked
    // reloaded old value for subsequent CAS in the slow path. We make this a scratch register
    // as we do have marking entrypoints on riscv64 even for scratch registers.
    XRegister old_value_temp = srs.AllocateXRegister();
    slow_path = new (codegen->GetScopedAllocator()) ReadBarrierCasSlowPathRISCV64(
        invoke,
        std::memory_order_seq_cst,
        /*strong=*/ true,
        object,
        offset,
        expected,
        new_value,
        old_value,
        old_value_temp,
        /*store_result=*/ old_value_temp,  // Let the SC result clobber the reloaded old_value.
        /*update_old_value=*/ false,
        codegen);
    codegen->AddSlowPath(slow_path);
    exit_loop = slow_path->GetExitLabel();
    cmp_failure = slow_path->GetEntryLabel();
  } else {
    old_value = srs.AllocateXRegister();
  }

  __ Add(tmp_ptr, object, offset);

  // Pre-populate the result register with failure.
  __ Li(out, 0);

  GenerateCompareAndSet(assembler,
                        type,
                        std::memory_order_seq_cst,
                        /*strong=*/ true,
                        cmp_failure,
                        tmp_ptr,
                        new_value,
                        old_value,
                        /*mask=*/ kNoXRegister,
                        /*masked=*/ kNoXRegister,
                        /*store_result=*/ old_value,  // Let the SC result clobber the `old_value`.
                        expected);

  DCHECK_EQ(slow_path != nullptr, type == DataType::Type::kReference && codegen->EmitReadBarrier());
  if (slow_path != nullptr) {
    __ Bind(slow_path->GetSuccessExitLabel());
  }

  // Indicate success if we successfully execute the SC.
  __ Li(out, 1);

  __ Bind(exit_loop);
}

void IntrinsicLocationsBuilderRISCV64::VisitUnsafeCASInt(HInvoke* invoke) {
  VisitJdkUnsafeCASInt(invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitUnsafeCASInt(HInvoke* invoke) {
  VisitJdkUnsafeCASInt(invoke);
}

void IntrinsicLocationsBuilderRISCV64::VisitUnsafeCASLong(HInvoke* invoke) {
  VisitJdkUnsafeCASLong(invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitUnsafeCASLong(HInvoke* invoke) {
  VisitJdkUnsafeCASLong(invoke);
}

void IntrinsicLocationsBuilderRISCV64::VisitUnsafeCASObject(HInvoke* invoke) {
  VisitJdkUnsafeCASObject(invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitUnsafeCASObject(HInvoke* invoke) {
  VisitJdkUnsafeCASObject(invoke);
}

void IntrinsicLocationsBuilderRISCV64::VisitJdkUnsafeCASInt(HInvoke* invoke) {
  // `jdk.internal.misc.Unsafe.compareAndSwapInt` has compare-and-set semantics (see javadoc).
  VisitJdkUnsafeCompareAndSetInt(invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitJdkUnsafeCASInt(HInvoke* invoke) {
  // `jdk.internal.misc.Unsafe.compareAndSwapInt` has compare-and-set semantics (see javadoc).
  VisitJdkUnsafeCompareAndSetInt(invoke);
}

void IntrinsicLocationsBuilderRISCV64::VisitJdkUnsafeCASLong(HInvoke* invoke) {
  // `jdk.internal.misc.Unsafe.compareAndSwapLong` has compare-and-set semantics (see javadoc).
  VisitJdkUnsafeCompareAndSetLong(invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitJdkUnsafeCASLong(HInvoke* invoke) {
  // `jdk.internal.misc.Unsafe.compareAndSwapLong` has compare-and-set semantics (see javadoc).
  VisitJdkUnsafeCompareAndSetLong(invoke);
}

void IntrinsicLocationsBuilderRISCV64::VisitJdkUnsafeCASObject(HInvoke* invoke) {
  // `jdk.internal.misc.Unsafe.compareAndSwapObject` has compare-and-set semantics (see javadoc).
  VisitJdkUnsafeCompareAndSetReference(invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitJdkUnsafeCASObject(HInvoke* invoke) {
  // `jdk.internal.misc.Unsafe.compareAndSwapObject` has compare-and-set semantics (see javadoc).
  VisitJdkUnsafeCompareAndSetReference(invoke);
}

void IntrinsicLocationsBuilderRISCV64::VisitJdkUnsafeCompareAndSetInt(HInvoke* invoke) {
  CreateUnsafeCASLocations(allocator_, invoke, codegen_);
}

void IntrinsicCodeGeneratorRISCV64::VisitJdkUnsafeCompareAndSetInt(HInvoke* invoke) {
  GenUnsafeCas(invoke, codegen_, DataType::Type::kInt32);
}

void IntrinsicLocationsBuilderRISCV64::VisitJdkUnsafeCompareAndSetLong(HInvoke* invoke) {
  CreateUnsafeCASLocations(allocator_, invoke, codegen_);
}

void IntrinsicCodeGeneratorRISCV64::VisitJdkUnsafeCompareAndSetLong(HInvoke* invoke) {
  GenUnsafeCas(invoke, codegen_, DataType::Type::kInt64);
}

void IntrinsicLocationsBuilderRISCV64::VisitJdkUnsafeCompareAndSetReference(HInvoke* invoke) {
  // The only supported read barrier implementation is the Baker-style read barriers.
  if (codegen_->EmitNonBakerReadBarrier()) {
    return;
  }

  // TODO(riscv64): Fix this intrinsic for heap poisoning configuration.
  if (kPoisonHeapReferences) {
    return;
  }

  CreateUnsafeCASLocations(allocator_, invoke, codegen_);
  if (codegen_->EmitReadBarrier()) {
    DCHECK(kUseBakerReadBarrier);
    // We need one non-scratch temporary register for read barrier.
    LocationSummary* locations = invoke->GetLocations();
    locations->AddTemp(Location::RequiresRegister());
  }
}

void IntrinsicCodeGeneratorRISCV64::VisitJdkUnsafeCompareAndSetReference(HInvoke* invoke) {
  GenUnsafeCas(invoke, codegen_, DataType::Type::kReference);
}

static void CreateUnsafeGetAndUpdateLocations(ArenaAllocator* allocator,
                                              HInvoke* invoke,
                                              CodeGeneratorRISCV64* codegen) {
  const bool can_call = codegen->EmitReadBarrier() && IsUnsafeGetAndSetReference(invoke);
  LocationSummary* locations = new (allocator) LocationSummary(
      invoke,
      can_call ? LocationSummary::kCallOnSlowPath : LocationSummary::kNoCall,
      kIntrinsified);
  if (can_call && kUseBakerReadBarrier) {
    locations->SetCustomSlowPathCallerSaves(RegisterSet::Empty());  // No caller-save registers.
  }
  locations->SetInAt(0, Location::NoLocation());        // Unused receiver.
  locations->SetInAt(1, Location::RequiresRegister());
  locations->SetInAt(2, Location::RequiresRegister());
  locations->SetInAt(3, Location::RequiresRegister());

  locations->SetOut(Location::RequiresRegister(), Location::kOutputOverlap);
}

static void GenUnsafeGetAndUpdate(HInvoke* invoke,
                                  DataType::Type type,
                                  CodeGeneratorRISCV64* codegen,
                                  GetAndUpdateOp get_and_update_op) {
  Riscv64Assembler* assembler = codegen->GetAssembler();
  LocationSummary* locations = invoke->GetLocations();
  Location out_loc = locations->Out();
  XRegister out = out_loc.AsRegister<XRegister>();                    // Result.
  XRegister base = locations->InAt(1).AsRegister<XRegister>();        // Object pointer.
  XRegister offset = locations->InAt(2).AsRegister<XRegister>();      // Long offset.
  XRegister arg = locations->InAt(3).AsRegister<XRegister>();         // New value or addend.

  // This needs to be before the temp registers, as MarkGCCard also uses scratch registers.
  if (type == DataType::Type::kReference) {
    DCHECK(get_and_update_op == GetAndUpdateOp::kSet);
    // Mark card for object as a new value shall be stored.
    bool new_value_can_be_null = true;  // TODO: Worth finding out this information?
    codegen->MaybeMarkGCCard(base, /*value=*/arg, new_value_can_be_null);
  }

  ScratchRegisterScope srs(assembler);
  XRegister tmp_ptr = srs.AllocateXRegister();                        // Pointer to actual memory.
  __ Add(tmp_ptr, base, offset);
  GenerateGetAndUpdate(codegen,
                       get_and_update_op,
                       (type == DataType::Type::kReference) ? DataType::Type::kInt32 : type,
                       std::memory_order_seq_cst,
                       tmp_ptr,
                       arg,
                       /*old_value=*/ out,
                       /*mask=*/ kNoXRegister,
                       /*temp=*/ kNoXRegister);

  if (type == DataType::Type::kReference) {
    __ ZextW(out, out);
    if (codegen->EmitReadBarrier()) {
      DCHECK(get_and_update_op == GetAndUpdateOp::kSet);
      if (kUseBakerReadBarrier) {
        // Use RA as temp. It is clobbered in the slow path anyway.
        static constexpr Location kBakerReadBarrierTemp = Location::RegisterLocation(RA);
        SlowPathCodeRISCV64* rb_slow_path =
            codegen->AddGcRootBakerBarrierBarrierSlowPath(invoke, out_loc, kBakerReadBarrierTemp);
        codegen->EmitBakerReadBarierMarkingCheck(rb_slow_path, out_loc, kBakerReadBarrierTemp);
      } else {
        codegen->GenerateReadBarrierSlow(
            invoke,
            out_loc,
            out_loc,
            Location::RegisterLocation(base),
            /*offset=*/ 0u,
            /*index=*/ Location::RegisterLocation(offset));
      }
    }
  }
}

void IntrinsicLocationsBuilderRISCV64::VisitUnsafeGetAndAddInt(HInvoke* invoke) {
  VisitJdkUnsafeGetAndAddInt(invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitUnsafeGetAndAddInt(HInvoke* invoke) {
  VisitJdkUnsafeGetAndAddInt(invoke);
}

void IntrinsicLocationsBuilderRISCV64::VisitUnsafeGetAndAddLong(HInvoke* invoke) {
  VisitJdkUnsafeGetAndAddLong(invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitUnsafeGetAndAddLong(HInvoke* invoke) {
  VisitJdkUnsafeGetAndAddLong(invoke);
}

void IntrinsicLocationsBuilderRISCV64::VisitUnsafeGetAndSetInt(HInvoke* invoke) {
  VisitJdkUnsafeGetAndSetInt(invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitUnsafeGetAndSetInt(HInvoke* invoke) {
  VisitJdkUnsafeGetAndSetInt(invoke);
}

void IntrinsicLocationsBuilderRISCV64::VisitUnsafeGetAndSetLong(HInvoke* invoke) {
  VisitJdkUnsafeGetAndSetLong(invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitUnsafeGetAndSetLong(HInvoke* invoke) {
  VisitJdkUnsafeGetAndSetLong(invoke);
}

void IntrinsicLocationsBuilderRISCV64::VisitUnsafeGetAndSetObject(HInvoke* invoke) {
  VisitJdkUnsafeGetAndSetReference(invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitUnsafeGetAndSetObject(HInvoke* invoke) {
  VisitJdkUnsafeGetAndSetReference(invoke);
}

void IntrinsicLocationsBuilderRISCV64::VisitJdkUnsafeGetAndAddInt(HInvoke* invoke) {
  CreateUnsafeGetAndUpdateLocations(allocator_, invoke, codegen_);
}

void IntrinsicCodeGeneratorRISCV64::VisitJdkUnsafeGetAndAddInt(HInvoke* invoke) {
  GenUnsafeGetAndUpdate(invoke, DataType::Type::kInt32, codegen_, GetAndUpdateOp::kAdd);
}

void IntrinsicLocationsBuilderRISCV64::VisitJdkUnsafeGetAndAddLong(HInvoke* invoke) {
  CreateUnsafeGetAndUpdateLocations(allocator_, invoke, codegen_);
}

void IntrinsicCodeGeneratorRISCV64::VisitJdkUnsafeGetAndAddLong(HInvoke* invoke) {
  GenUnsafeGetAndUpdate(invoke, DataType::Type::kInt64, codegen_, GetAndUpdateOp::kAdd);
}

void IntrinsicLocationsBuilderRISCV64::VisitJdkUnsafeGetAndSetInt(HInvoke* invoke) {
  CreateUnsafeGetAndUpdateLocations(allocator_, invoke, codegen_);
}

void IntrinsicCodeGeneratorRISCV64::VisitJdkUnsafeGetAndSetInt(HInvoke* invoke) {
  GenUnsafeGetAndUpdate(invoke, DataType::Type::kInt32, codegen_, GetAndUpdateOp::kSet);
}

void IntrinsicLocationsBuilderRISCV64::VisitJdkUnsafeGetAndSetLong(HInvoke* invoke) {
  CreateUnsafeGetAndUpdateLocations(allocator_, invoke, codegen_);
}

void IntrinsicCodeGeneratorRISCV64::VisitJdkUnsafeGetAndSetLong(HInvoke* invoke) {
  GenUnsafeGetAndUpdate(invoke, DataType::Type::kInt64, codegen_, GetAndUpdateOp::kSet);
}

void IntrinsicLocationsBuilderRISCV64::VisitJdkUnsafeGetAndSetReference(HInvoke* invoke) {
  // TODO(riscv64): Fix this intrinsic for heap poisoning configuration.
  if (kPoisonHeapReferences) {
    return;
  }

  CreateUnsafeGetAndUpdateLocations(allocator_, invoke, codegen_);
}

void IntrinsicCodeGeneratorRISCV64::VisitJdkUnsafeGetAndSetReference(HInvoke* invoke) {
  GenUnsafeGetAndUpdate(invoke, DataType::Type::kReference, codegen_, GetAndUpdateOp::kSet);
}

class VarHandleSlowPathRISCV64 : public IntrinsicSlowPathRISCV64 {
 public:
  VarHandleSlowPathRISCV64(HInvoke* invoke, std::memory_order order)
      : IntrinsicSlowPathRISCV64(invoke),
        order_(order),
        return_success_(false),
        strong_(false),
        get_and_update_op_(GetAndUpdateOp::kAdd) {
  }

  Riscv64Label* GetByteArrayViewCheckLabel() {
    return &byte_array_view_check_label_;
  }

  Riscv64Label* GetNativeByteOrderLabel() {
    return &native_byte_order_label_;
  }

  void SetCompareAndSetOrExchangeArgs(bool return_success, bool strong) {
    if (return_success) {
      DCHECK(GetAccessModeTemplate() == mirror::VarHandle::AccessModeTemplate::kCompareAndSet);
    } else {
      DCHECK(GetAccessModeTemplate() == mirror::VarHandle::AccessModeTemplate::kCompareAndExchange);
    }
    return_success_ = return_success;
    strong_ = strong;
  }

  void SetGetAndUpdateOp(GetAndUpdateOp get_and_update_op) {
    DCHECK(GetAccessModeTemplate() == mirror::VarHandle::AccessModeTemplate::kGetAndUpdate);
    get_and_update_op_ = get_and_update_op;
  }

  void EmitNativeCode(CodeGenerator* codegen_in) override {
    if (GetByteArrayViewCheckLabel()->IsLinked()) {
      EmitByteArrayViewCode(codegen_in);
    }
    IntrinsicSlowPathRISCV64::EmitNativeCode(codegen_in);
  }

 private:
  HInvoke* GetInvoke() const {
    return GetInstruction()->AsInvoke();
  }

  mirror::VarHandle::AccessModeTemplate GetAccessModeTemplate() const {
    return mirror::VarHandle::GetAccessModeTemplateByIntrinsic(GetInvoke()->GetIntrinsic());
  }

  void EmitByteArrayViewCode(CodeGenerator* codegen_in);

  Riscv64Label byte_array_view_check_label_;
  Riscv64Label native_byte_order_label_;
  // Shared parameter for all VarHandle intrinsics.
  std::memory_order order_;
  // Extra arguments for GenerateVarHandleCompareAndSetOrExchange().
  bool return_success_;
  bool strong_;
  // Extra argument for GenerateVarHandleGetAndUpdate().
  GetAndUpdateOp get_and_update_op_;
};

// Generate subtype check without read barriers.
static void GenerateSubTypeObjectCheckNoReadBarrier(CodeGeneratorRISCV64* codegen,
                                                    SlowPathCodeRISCV64* slow_path,
                                                    XRegister object,
                                                    XRegister type,
                                                    bool object_can_be_null = true) {
  Riscv64Assembler* assembler = codegen->GetAssembler();

  const MemberOffset class_offset = mirror::Object::ClassOffset();
  const MemberOffset super_class_offset = mirror::Class::SuperClassOffset();

  Riscv64Label success;
  if (object_can_be_null) {
    __ Beqz(object, &success);
  }

  ScratchRegisterScope srs(assembler);
  XRegister temp = srs.AllocateXRegister();

  // Note: The `type` can be `TMP`. We're using "bare" local branches to enforce that they shall
  // not be expanded and the scrach register `TMP` shall not be clobbered if taken. Taking the
  // branch to the slow path can theoretically clobber `TMP` (if outside the 1 MiB range).
  __ Loadwu(temp, object, class_offset.Int32Value());
  codegen->MaybeUnpoisonHeapReference(temp);
  Riscv64Label loop;
  __ Bind(&loop);
  __ Beq(type, temp, &success, /*is_bare=*/ true);
  // We may not have another scratch register for `Loadwu()`. Use `Lwu()` directly.
  DCHECK(IsInt<12>(super_class_offset.Int32Value()));
  __ Lwu(temp, temp, super_class_offset.Int32Value());
  codegen->MaybeUnpoisonHeapReference(temp);
  __ Beqz(temp, slow_path->GetEntryLabel());
  __ J(&loop, /*is_bare=*/ true);
  __ Bind(&success);
}

// Check access mode and the primitive type from VarHandle.varType.
// Check reference arguments against the VarHandle.varType; for references this is a subclass
// check without read barrier, so it can have false negatives which we handle in the slow path.
static void GenerateVarHandleAccessModeAndVarTypeChecks(HInvoke* invoke,
                                                        CodeGeneratorRISCV64* codegen,
                                                        SlowPathCodeRISCV64* slow_path,
                                                        DataType::Type type) {
  mirror::VarHandle::AccessMode access_mode =
      mirror::VarHandle::GetAccessModeByIntrinsic(invoke->GetIntrinsic());
  Primitive::Type primitive_type = DataTypeToPrimitive(type);

  Riscv64Assembler* assembler = codegen->GetAssembler();
  LocationSummary* locations = invoke->GetLocations();
  XRegister varhandle = locations->InAt(0).AsRegister<XRegister>();

  const MemberOffset var_type_offset = mirror::VarHandle::VarTypeOffset();
  const MemberOffset access_mode_bit_mask_offset = mirror::VarHandle::AccessModesBitMaskOffset();
  const MemberOffset primitive_type_offset = mirror::Class::PrimitiveTypeOffset();

  ScratchRegisterScope srs(assembler);
  XRegister temp = srs.AllocateXRegister();
  XRegister temp2 = srs.AllocateXRegister();

  // Check that the operation is permitted.
  __ Loadw(temp, varhandle, access_mode_bit_mask_offset.Int32Value());
  DCHECK_LT(enum_cast<uint32_t>(access_mode), 31u);  // We cannot avoid the shift below.
  __ Slliw(temp, temp, 31 - enum_cast<uint32_t>(access_mode));  // Shift tested bit to sign bit.
  __ Bgez(temp, slow_path->GetEntryLabel());  // If not permitted, go to slow path.

  // For primitive types, we do not need a read barrier when loading a reference only for loading
  // constant field through the reference. For reference types, we deliberately avoid the read
  // barrier, letting the slow path handle the false negatives.
  __ Loadwu(temp, varhandle, var_type_offset.Int32Value());
  codegen->MaybeUnpoisonHeapReference(temp);

  // Check the varType.primitiveType field against the type we're trying to use.
  __ Loadhu(temp2, temp, primitive_type_offset.Int32Value());
  if (primitive_type == Primitive::kPrimNot) {
    static_assert(Primitive::kPrimNot == 0);
    __ Bnez(temp2, slow_path->GetEntryLabel());
  } else {
    __ Li(temp, enum_cast<int32_t>(primitive_type));  // `temp` can be clobbered.
    __ Bne(temp2, temp, slow_path->GetEntryLabel());
  }

  srs.FreeXRegister(temp2);

  if (type == DataType::Type::kReference) {
    // Check reference arguments against the varType.
    // False negatives due to varType being an interface or array type
    // or due to the missing read barrier are handled by the slow path.
    size_t expected_coordinates_count = GetExpectedVarHandleCoordinatesCount(invoke);
    uint32_t arguments_start = /* VarHandle object */ 1u + expected_coordinates_count;
    uint32_t number_of_arguments = invoke->GetNumberOfArguments();
    for (size_t arg_index = arguments_start; arg_index != number_of_arguments; ++arg_index) {
      HInstruction* arg = invoke->InputAt(arg_index);
      DCHECK_EQ(arg->GetType(), DataType::Type::kReference);
      if (!arg->IsNullConstant()) {
        XRegister arg_reg = locations->InAt(arg_index).AsRegister<XRegister>();
        GenerateSubTypeObjectCheckNoReadBarrier(codegen, slow_path, arg_reg, temp);
      }
    }
  }
}

static void GenerateVarHandleStaticFieldCheck(HInvoke* invoke,
                                              CodeGeneratorRISCV64* codegen,
                                              SlowPathCodeRISCV64* slow_path) {
  Riscv64Assembler* assembler = codegen->GetAssembler();
  XRegister varhandle = invoke->GetLocations()->InAt(0).AsRegister<XRegister>();

  const MemberOffset coordinate_type0_offset = mirror::VarHandle::CoordinateType0Offset();

  ScratchRegisterScope srs(assembler);
  XRegister temp = srs.AllocateXRegister();

  // Check that the VarHandle references a static field by checking that coordinateType0 == null.
  // Do not emit read barrier (or unpoison the reference) for comparing to null.
  __ Loadwu(temp, varhandle, coordinate_type0_offset.Int32Value());
  __ Bnez(temp, slow_path->GetEntryLabel());
}

static void GenerateVarHandleInstanceFieldChecks(HInvoke* invoke,
                                                 CodeGeneratorRISCV64* codegen,
                                                 SlowPathCodeRISCV64* slow_path) {
  VarHandleOptimizations optimizations(invoke);
  Riscv64Assembler* assembler = codegen->GetAssembler();
  LocationSummary* locations = invoke->GetLocations();
  XRegister varhandle = locations->InAt(0).AsRegister<XRegister>();
  XRegister object = locations->InAt(1).AsRegister<XRegister>();

  const MemberOffset coordinate_type0_offset = mirror::VarHandle::CoordinateType0Offset();
  const MemberOffset coordinate_type1_offset = mirror::VarHandle::CoordinateType1Offset();

  // Null-check the object.
  if (!optimizations.GetSkipObjectNullCheck()) {
    __ Beqz(object, slow_path->GetEntryLabel());
  }

  if (!optimizations.GetUseKnownBootImageVarHandle()) {
    ScratchRegisterScope srs(assembler);
    XRegister temp = srs.AllocateXRegister();

    // Check that the VarHandle references an instance field by checking that
    // coordinateType1 == null. coordinateType0 should not be null, but this is handled by the
    // type compatibility check with the source object's type, which will fail for null.
    __ Loadwu(temp, varhandle, coordinate_type1_offset.Int32Value());
    // No need for read barrier or unpoisoning of coordinateType1 for comparison with null.
    __ Bnez(temp, slow_path->GetEntryLabel());

    // Check that the object has the correct type.
    // We deliberately avoid the read barrier, letting the slow path handle the false negatives.
    __ Loadwu(temp, varhandle, coordinate_type0_offset.Int32Value());
    codegen->MaybeUnpoisonHeapReference(temp);
    GenerateSubTypeObjectCheckNoReadBarrier(
        codegen, slow_path, object, temp, /*object_can_be_null=*/ false);
  }
}

static void GenerateVarHandleArrayChecks(HInvoke* invoke,
                                         CodeGeneratorRISCV64* codegen,
                                         VarHandleSlowPathRISCV64* slow_path) {
  VarHandleOptimizations optimizations(invoke);
  Riscv64Assembler* assembler = codegen->GetAssembler();
  LocationSummary* locations = invoke->GetLocations();
  XRegister varhandle = locations->InAt(0).AsRegister<XRegister>();
  XRegister object = locations->InAt(1).AsRegister<XRegister>();
  XRegister index = locations->InAt(2).AsRegister<XRegister>();
  DataType::Type value_type =
      GetVarHandleExpectedValueType(invoke, /*expected_coordinates_count=*/ 2u);
  Primitive::Type primitive_type = DataTypeToPrimitive(value_type);

  const MemberOffset coordinate_type0_offset = mirror::VarHandle::CoordinateType0Offset();
  const MemberOffset coordinate_type1_offset = mirror::VarHandle::CoordinateType1Offset();
  const MemberOffset component_type_offset = mirror::Class::ComponentTypeOffset();
  const MemberOffset primitive_type_offset = mirror::Class::PrimitiveTypeOffset();
  const MemberOffset class_offset = mirror::Object::ClassOffset();
  const MemberOffset array_length_offset = mirror::Array::LengthOffset();

  // Null-check the object.
  if (!optimizations.GetSkipObjectNullCheck()) {
    __ Beqz(object, slow_path->GetEntryLabel());
  }

  ScratchRegisterScope srs(assembler);
  XRegister temp = srs.AllocateXRegister();
  XRegister temp2 = srs.AllocateXRegister();

  // Check that the VarHandle references an array, byte array view or ByteBuffer by checking
  // that coordinateType1 != null. If that's true, coordinateType1 shall be int.class and
  // coordinateType0 shall not be null but we do not explicitly verify that.
  __ Loadwu(temp, varhandle, coordinate_type1_offset.Int32Value());
  // No need for read barrier or unpoisoning of coordinateType1 for comparison with null.
  __ Beqz(temp, slow_path->GetEntryLabel());

  // Check object class against componentType0.
  //
  // This is an exact check and we defer other cases to the runtime. This includes
  // conversion to array of superclass references, which is valid but subsequently
  // requires all update operations to check that the value can indeed be stored.
  // We do not want to perform such extra checks in the intrinsified code.
  //
  // We do this check without read barrier, so there can be false negatives which we
  // defer to the slow path. There shall be no false negatives for array classes in the
  // boot image (including Object[] and primitive arrays) because they are non-movable.
  __ Loadwu(temp, varhandle, coordinate_type0_offset.Int32Value());
  __ Loadwu(temp2, object, class_offset.Int32Value());
  __ Bne(temp, temp2, slow_path->GetEntryLabel());

  // Check that the coordinateType0 is an array type. We do not need a read barrier
  // for loading constant reference fields (or chains of them) for comparison with null,
  // nor for finally loading a constant primitive field (primitive type) below.
  codegen->MaybeUnpoisonHeapReference(temp);
  __ Loadwu(temp2, temp, component_type_offset.Int32Value());
  codegen->MaybeUnpoisonHeapReference(temp2);
  __ Beqz(temp2, slow_path->GetEntryLabel());

  // Check that the array component type matches the primitive type.
  __ Loadhu(temp, temp2, primitive_type_offset.Int32Value());
  if (primitive_type == Primitive::kPrimNot) {
    static_assert(Primitive::kPrimNot == 0);
    __ Bnez(temp, slow_path->GetEntryLabel());
  } else {
    // With the exception of `kPrimNot` (handled above), `kPrimByte` and `kPrimBoolean`,
    // we shall check for a byte array view in the slow path.
    // The check requires the ByteArrayViewVarHandle.class to be in the boot image,
    // so we cannot emit that if we're JITting without boot image.
    bool boot_image_available =
        codegen->GetCompilerOptions().IsBootImage() ||
        !Runtime::Current()->GetHeap()->GetBootImageSpaces().empty();
    bool can_be_view = (DataType::Size(value_type) != 1u) && boot_image_available;
    Riscv64Label* slow_path_label =
        can_be_view ? slow_path->GetByteArrayViewCheckLabel() : slow_path->GetEntryLabel();
    __ Li(temp2, enum_cast<int32_t>(primitive_type));
    __ Bne(temp, temp2, slow_path_label);
  }

  // Check for array index out of bounds.
  __ Loadw(temp, object, array_length_offset.Int32Value());
  __ Bgeu(index, temp, slow_path->GetEntryLabel());
}

static void GenerateVarHandleCoordinateChecks(HInvoke* invoke,
                                              CodeGeneratorRISCV64* codegen,
                                              VarHandleSlowPathRISCV64* slow_path) {
  size_t expected_coordinates_count = GetExpectedVarHandleCoordinatesCount(invoke);
  if (expected_coordinates_count == 0u) {
    GenerateVarHandleStaticFieldCheck(invoke, codegen, slow_path);
  } else if (expected_coordinates_count == 1u) {
    GenerateVarHandleInstanceFieldChecks(invoke, codegen, slow_path);
  } else {
    DCHECK_EQ(expected_coordinates_count, 2u);
    GenerateVarHandleArrayChecks(invoke, codegen, slow_path);
  }
}

static VarHandleSlowPathRISCV64* GenerateVarHandleChecks(HInvoke* invoke,
                                                         CodeGeneratorRISCV64* codegen,
                                                         std::memory_order order,
                                                         DataType::Type type) {
  size_t expected_coordinates_count = GetExpectedVarHandleCoordinatesCount(invoke);
  VarHandleOptimizations optimizations(invoke);
  if (optimizations.GetUseKnownBootImageVarHandle()) {
    DCHECK_NE(expected_coordinates_count, 2u);
    if (expected_coordinates_count == 0u || optimizations.GetSkipObjectNullCheck()) {
      return nullptr;
    }
  }

  VarHandleSlowPathRISCV64* slow_path =
      new (codegen->GetScopedAllocator()) VarHandleSlowPathRISCV64(invoke, order);
  codegen->AddSlowPath(slow_path);

  if (!optimizations.GetUseKnownBootImageVarHandle()) {
    GenerateVarHandleAccessModeAndVarTypeChecks(invoke, codegen, slow_path, type);
  }
  GenerateVarHandleCoordinateChecks(invoke, codegen, slow_path);

  return slow_path;
}

struct VarHandleTarget {
  XRegister object;  // The object holding the value to operate on.
  XRegister offset;  // The offset of the value to operate on.
};

static VarHandleTarget GetVarHandleTarget(HInvoke* invoke) {
  size_t expected_coordinates_count = GetExpectedVarHandleCoordinatesCount(invoke);
  LocationSummary* locations = invoke->GetLocations();

  VarHandleTarget target;
  // The temporary allocated for loading the offset.
  target.offset = locations->GetTemp(0u).AsRegister<XRegister>();
  // The reference to the object that holds the value to operate on.
  target.object = (expected_coordinates_count == 0u)
      ? locations->GetTemp(1u).AsRegister<XRegister>()
      : locations->InAt(1).AsRegister<XRegister>();
  return target;
}

static void GenerateVarHandleTarget(HInvoke* invoke,
                                    const VarHandleTarget& target,
                                    CodeGeneratorRISCV64* codegen) {
  Riscv64Assembler* assembler = codegen->GetAssembler();
  LocationSummary* locations = invoke->GetLocations();
  XRegister varhandle = locations->InAt(0).AsRegister<XRegister>();
  size_t expected_coordinates_count = GetExpectedVarHandleCoordinatesCount(invoke);

  if (expected_coordinates_count <= 1u) {
    if (VarHandleOptimizations(invoke).GetUseKnownBootImageVarHandle()) {
      ScopedObjectAccess soa(Thread::Current());
      ArtField* target_field = GetBootImageVarHandleField(invoke);
      if (expected_coordinates_count == 0u) {
        ObjPtr<mirror::Class> declaring_class = target_field->GetDeclaringClass();
        if (Runtime::Current()->GetHeap()->ObjectIsInBootImageSpace(declaring_class)) {
          uint32_t boot_image_offset = CodeGenerator::GetBootImageOffset(declaring_class);
          codegen->LoadBootImageRelRoEntry(target.object, boot_image_offset);
        } else {
          codegen->LoadTypeForBootImageIntrinsic(
              target.object,
              TypeReference(&declaring_class->GetDexFile(), declaring_class->GetDexTypeIndex()));
        }
      }
      __ Li(target.offset, target_field->GetOffset().Uint32Value());
    } else {
      // For static fields, we need to fill the `target.object` with the declaring class,
      // so we can use `target.object` as temporary for the `ArtField*`. For instance fields,
      // we do not need the declaring class, so we can forget the `ArtField*` when
      // we load the `target.offset`, so use the `target.offset` to hold the `ArtField*`.
      XRegister field = (expected_coordinates_count == 0) ? target.object : target.offset;

      const MemberOffset art_field_offset = mirror::FieldVarHandle::ArtFieldOffset();
      const MemberOffset offset_offset = ArtField::OffsetOffset();

      // Load the ArtField*, the offset and, if needed, declaring class.
      __ Loadd(field, varhandle, art_field_offset.Int32Value());
      __ Loadwu(target.offset, field, offset_offset.Int32Value());
      if (expected_coordinates_count == 0u) {
        codegen->GenerateGcRootFieldLoad(
            invoke,
            Location::RegisterLocation(target.object),
            field,
            ArtField::DeclaringClassOffset().Int32Value(),
            codegen->GetCompilerReadBarrierOption());
      }
    }
  } else {
    DCHECK_EQ(expected_coordinates_count, 2u);
    DataType::Type value_type =
        GetVarHandleExpectedValueType(invoke, /*expected_coordinates_count=*/ 2u);
    MemberOffset data_offset = mirror::Array::DataOffset(DataType::Size(value_type));

    XRegister index = locations->InAt(2).AsRegister<XRegister>();
    __ Li(target.offset, data_offset.Int32Value());
    codegen->GetInstructionVisitor()->ShNAdd(target.offset, index, target.offset, value_type);
  }
}

static LocationSummary* CreateVarHandleCommonLocations(HInvoke* invoke,
                                                       CodeGeneratorRISCV64* codegen) {
  size_t expected_coordinates_count = GetExpectedVarHandleCoordinatesCount(invoke);
  DataType::Type return_type = invoke->GetType();

  ArenaAllocator* allocator = invoke->GetBlock()->GetGraph()->GetAllocator();
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kCallOnSlowPath, kIntrinsified);
  locations->SetInAt(0, Location::RequiresRegister());
  // Require coordinates in registers. These are the object holding the value
  // to operate on (except for static fields) and index (for arrays and views).
  for (size_t i = 0; i != expected_coordinates_count; ++i) {
    locations->SetInAt(/* VarHandle object */ 1u + i, Location::RequiresRegister());
  }
  if (return_type != DataType::Type::kVoid) {
    if (DataType::IsFloatingPointType(return_type)) {
      locations->SetOut(Location::RequiresFpuRegister());
    } else {
      locations->SetOut(Location::RequiresRegister());
    }
  }
  uint32_t arguments_start = /* VarHandle object */ 1u + expected_coordinates_count;
  uint32_t number_of_arguments = invoke->GetNumberOfArguments();
  for (size_t arg_index = arguments_start; arg_index != number_of_arguments; ++arg_index) {
    HInstruction* arg = invoke->InputAt(arg_index);
    if (IsZeroBitPattern(arg)) {
      locations->SetInAt(arg_index, Location::ConstantLocation(arg));
    } else if (DataType::IsFloatingPointType(arg->GetType())) {
      locations->SetInAt(arg_index, Location::RequiresFpuRegister());
    } else {
      locations->SetInAt(arg_index, Location::RequiresRegister());
    }
  }

  // Add a temporary for offset.
  if (codegen->EmitNonBakerReadBarrier() &&
      GetExpectedVarHandleCoordinatesCount(invoke) == 0u) {  // For static fields.
    // To preserve the offset value across the non-Baker read barrier slow path
    // for loading the declaring class, use a fixed callee-save register.
    constexpr int first_callee_save = CTZ(kRiscv64CalleeSaveRefSpills);
    locations->AddTemp(Location::RegisterLocation(first_callee_save));
  } else {
    locations->AddTemp(Location::RequiresRegister());
  }
  if (expected_coordinates_count == 0u) {
    // Add a temporary to hold the declaring class.
    locations->AddTemp(Location::RequiresRegister());
  }

  return locations;
}

static void CreateVarHandleGetLocations(HInvoke* invoke, CodeGeneratorRISCV64* codegen) {
  VarHandleOptimizations optimizations(invoke);
  if (optimizations.GetDoNotIntrinsify()) {
    return;
  }

  if (codegen->EmitNonBakerReadBarrier() &&
      invoke->GetType() == DataType::Type::kReference &&
      invoke->GetIntrinsic() != Intrinsics::kVarHandleGet &&
      invoke->GetIntrinsic() != Intrinsics::kVarHandleGetOpaque) {
    // Unsupported for non-Baker read barrier because the artReadBarrierSlow() ignores
    // the passed reference and reloads it from the field. This gets the memory visibility
    // wrong for Acquire/Volatile operations. b/173104084
    return;
  }

  CreateVarHandleCommonLocations(invoke, codegen);
}

DataType::Type IntTypeForFloatingPointType(DataType::Type fp_type) {
  DCHECK(DataType::IsFloatingPointType(fp_type));
  return (fp_type == DataType::Type::kFloat32) ? DataType::Type::kInt32 : DataType::Type::kInt64;
}

static void GenerateVarHandleGet(HInvoke* invoke,
                                 CodeGeneratorRISCV64* codegen,
                                 std::memory_order order,
                                 bool byte_swap = false) {
  DataType::Type type = invoke->GetType();
  DCHECK_NE(type, DataType::Type::kVoid);

  LocationSummary* locations = invoke->GetLocations();
  Riscv64Assembler* assembler = codegen->GetAssembler();
  Location out = locations->Out();

  VarHandleTarget target = GetVarHandleTarget(invoke);
  VarHandleSlowPathRISCV64* slow_path = nullptr;
  if (!byte_swap) {
    slow_path = GenerateVarHandleChecks(invoke, codegen, order, type);
    GenerateVarHandleTarget(invoke, target, codegen);
    if (slow_path != nullptr) {
      __ Bind(slow_path->GetNativeByteOrderLabel());
    }
  }

  bool seq_cst_barrier = (order == std::memory_order_seq_cst);
  bool acquire_barrier = seq_cst_barrier || (order == std::memory_order_acquire);
  DCHECK(acquire_barrier || order == std::memory_order_relaxed);

  if (seq_cst_barrier) {
    codegen->GenerateMemoryBarrier(MemBarrierKind::kAnyAny);
  }

  // Load the value from the target location.
  if (type == DataType::Type::kReference && codegen->EmitBakerReadBarrier()) {
    Location index = Location::RegisterLocation(target.offset);
    // TODO(riscv64): Revisit when we add checking if the holder is black.
    Location temp = Location::NoLocation();
    codegen->GenerateReferenceLoadWithBakerReadBarrier(invoke,
                                                       out,
                                                       target.object,
                                                       /*offset=*/ 0,
                                                       index,
                                                       temp,
                                                       /*needs_null_check=*/ false);
    DCHECK(!byte_swap);
  } else {
    ScratchRegisterScope srs(assembler);
    XRegister address = srs.AllocateXRegister();
    __ Add(address, target.object, target.offset);
    Location load_loc = out;
    DataType::Type load_type = type;
    if (byte_swap && DataType::IsFloatingPointType(type)) {
      load_loc = Location::RegisterLocation(target.offset);  // Load to the offset temporary.
      load_type = IntTypeForFloatingPointType(type);
    }
    codegen->GetInstructionVisitor()->Load(load_loc, address, /*offset=*/ 0, load_type);
    if (type == DataType::Type::kReference) {
      DCHECK(!byte_swap);
      Location object_loc = Location::RegisterLocation(target.object);
      Location offset_loc = Location::RegisterLocation(target.offset);
      codegen->MaybeGenerateReadBarrierSlow(
          invoke, out, out, object_loc, /*offset=*/ 0u, /*index=*/ offset_loc);
    } else if (byte_swap) {
      GenerateReverseBytes(codegen, out, load_loc.AsRegister<XRegister>(), type);
    }
  }

  if (acquire_barrier) {
    codegen->GenerateMemoryBarrier(MemBarrierKind::kLoadAny);
  }

  if (slow_path != nullptr) {
    DCHECK(!byte_swap);
    __ Bind(slow_path->GetExitLabel());
  }
}

void IntrinsicLocationsBuilderRISCV64::VisitVarHandleGet(HInvoke* invoke) {
  CreateVarHandleGetLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorRISCV64::VisitVarHandleGet(HInvoke* invoke) {
  GenerateVarHandleGet(invoke, codegen_, std::memory_order_relaxed);
}

void IntrinsicLocationsBuilderRISCV64::VisitVarHandleGetOpaque(HInvoke* invoke) {
  CreateVarHandleGetLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorRISCV64::VisitVarHandleGetOpaque(HInvoke* invoke) {
  GenerateVarHandleGet(invoke, codegen_, std::memory_order_relaxed);
}

void IntrinsicLocationsBuilderRISCV64::VisitVarHandleGetAcquire(HInvoke* invoke) {
  CreateVarHandleGetLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorRISCV64::VisitVarHandleGetAcquire(HInvoke* invoke) {
  GenerateVarHandleGet(invoke, codegen_, std::memory_order_acquire);
}

void IntrinsicLocationsBuilderRISCV64::VisitVarHandleGetVolatile(HInvoke* invoke) {
  CreateVarHandleGetLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorRISCV64::VisitVarHandleGetVolatile(HInvoke* invoke) {
  GenerateVarHandleGet(invoke, codegen_, std::memory_order_seq_cst);
}

static void CreateVarHandleSetLocations(HInvoke* invoke, CodeGeneratorRISCV64* codegen) {
  VarHandleOptimizations optimizations(invoke);
  if (optimizations.GetDoNotIntrinsify()) {
    return;
  }

  CreateVarHandleCommonLocations(invoke, codegen);
  if (kPoisonHeapReferences && invoke->GetLocations() != nullptr) {
    LocationSummary* locations = invoke->GetLocations();
    uint32_t value_index = invoke->GetNumberOfArguments() - 1;
    DataType::Type value_type = GetDataTypeFromShorty(invoke, value_index);
    if (value_type == DataType::Type::kReference && !locations->InAt(value_index).IsConstant()) {
      locations->AddTemp(Location::RequiresRegister());
    }
  }
}

static void GenerateVarHandleSet(HInvoke* invoke,
                                 CodeGeneratorRISCV64* codegen,
                                 std::memory_order order,
                                 bool byte_swap = false) {
  uint32_t value_index = invoke->GetNumberOfArguments() - 1;
  DataType::Type value_type = GetDataTypeFromShorty(invoke, value_index);

  Riscv64Assembler* assembler = codegen->GetAssembler();
  Location value = invoke->GetLocations()->InAt(value_index);

  VarHandleTarget target = GetVarHandleTarget(invoke);
  VarHandleSlowPathRISCV64* slow_path = nullptr;
  if (!byte_swap) {
    slow_path = GenerateVarHandleChecks(invoke, codegen, order, value_type);
    GenerateVarHandleTarget(invoke, target, codegen);
    if (slow_path != nullptr) {
      __ Bind(slow_path->GetNativeByteOrderLabel());
    }
  }

  {
    ScratchRegisterScope srs(assembler);
    // Heap poisoning needs two scratch registers in `Store()`, except for null constants.
    XRegister address =
        (kPoisonHeapReferences && value_type == DataType::Type::kReference && !value.IsConstant())
            ? invoke->GetLocations()->GetTemp(0).AsRegister<XRegister>()
            : srs.AllocateXRegister();
    __ Add(address, target.object, target.offset);

    if (byte_swap) {
      DCHECK(!value.IsConstant());  // Zero uses the main path as it does not need a byte swap.
      // The offset is no longer needed, so reuse the offset temporary for the byte-swapped value.
      Location new_value = Location::RegisterLocation(target.offset);
      if (DataType::IsFloatingPointType(value_type)) {
        value_type = IntTypeForFloatingPointType(value_type);
        codegen->MoveLocation(new_value, value, value_type);
        value = new_value;
      }
      GenerateReverseBytes(codegen, new_value, value.AsRegister<XRegister>(), value_type);
      value = new_value;
    }

    GenerateSet(codegen, order, value, address, /*offset=*/ 0, value_type);
  }

  if (CodeGenerator::StoreNeedsWriteBarrier(value_type, invoke->InputAt(value_index))) {
    codegen->MaybeMarkGCCard(
        target.object, value.AsRegister<XRegister>(), /* emit_null_check= */ true);
  }

  if (slow_path != nullptr) {
    DCHECK(!byte_swap);
    __ Bind(slow_path->GetExitLabel());
  }
}

void IntrinsicLocationsBuilderRISCV64::VisitVarHandleSet(HInvoke* invoke) {
  CreateVarHandleSetLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorRISCV64::VisitVarHandleSet(HInvoke* invoke) {
  GenerateVarHandleSet(invoke, codegen_, std::memory_order_relaxed);
}

void IntrinsicLocationsBuilderRISCV64::VisitVarHandleSetOpaque(HInvoke* invoke) {
  CreateVarHandleSetLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorRISCV64::VisitVarHandleSetOpaque(HInvoke* invoke) {
  GenerateVarHandleSet(invoke, codegen_, std::memory_order_relaxed);
}

void IntrinsicLocationsBuilderRISCV64::VisitVarHandleSetRelease(HInvoke* invoke) {
  CreateVarHandleSetLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorRISCV64::VisitVarHandleSetRelease(HInvoke* invoke) {
  GenerateVarHandleSet(invoke, codegen_, std::memory_order_release);
}

void IntrinsicLocationsBuilderRISCV64::VisitVarHandleSetVolatile(HInvoke* invoke) {
  CreateVarHandleSetLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorRISCV64::VisitVarHandleSetVolatile(HInvoke* invoke) {
  GenerateVarHandleSet(invoke, codegen_, std::memory_order_seq_cst);
}

static bool ScratchXRegisterNeeded(Location loc, DataType::Type type, bool byte_swap) {
  if (loc.IsConstant()) {
    DCHECK(loc.GetConstant()->IsZeroBitPattern());
    return false;
  }
  return DataType::IsFloatingPointType(type) || DataType::Size(type) < 4u || byte_swap;
}

static void CreateVarHandleCompareAndSetOrExchangeLocations(HInvoke* invoke,
                                                            CodeGeneratorRISCV64* codegen,
                                                            bool return_success) {
  VarHandleOptimizations optimizations(invoke);
  if (optimizations.GetDoNotIntrinsify()) {
    return;
  }

  uint32_t expected_index = invoke->GetNumberOfArguments() - 2;
  uint32_t new_value_index = invoke->GetNumberOfArguments() - 1;
  DataType::Type value_type = GetDataTypeFromShorty(invoke, new_value_index);
  DCHECK_EQ(value_type, GetDataTypeFromShorty(invoke, expected_index));

  bool is_reference = (value_type == DataType::Type::kReference);
  if (is_reference && codegen->EmitNonBakerReadBarrier()) {
    // Unsupported for non-Baker read barrier because the artReadBarrierSlow() ignores
    // the passed reference and reloads it from the field. This breaks the read barriers
    // in slow path in different ways. The marked old value may not actually be a to-space
    // reference to the same object as `old_value`, breaking slow path assumptions. And
    // for CompareAndExchange, marking the old value after comparison failure may actually
    // return the reference to `expected`, erroneously indicating success even though we
    // did not set the new value. (And it also gets the memory visibility wrong.) b/173104084
    return;
  }

  // TODO(riscv64): Fix this intrinsic for heap poisoning configuration.
  if (kPoisonHeapReferences && value_type == DataType::Type::kReference) {
    return;
  }

  LocationSummary* locations = CreateVarHandleCommonLocations(invoke, codegen);
  DCHECK_EQ(expected_index, 1u + GetExpectedVarHandleCoordinatesCount(invoke));

  if (codegen->EmitNonBakerReadBarrier()) {
    // We need callee-save registers for both the class object and offset instead of
    // the temporaries reserved in CreateVarHandleCommonLocations().
    static_assert(POPCOUNT(kRiscv64CalleeSaveRefSpills) >= 2u);
    uint32_t first_callee_save = CTZ(kRiscv64CalleeSaveRefSpills);
    uint32_t second_callee_save = CTZ(kRiscv64CalleeSaveRefSpills ^ (1u << first_callee_save));
    if (expected_index == 1u) {  // For static fields.
      DCHECK_EQ(locations->GetTempCount(), 2u);
      DCHECK(locations->GetTemp(0u).Equals(Location::RequiresRegister()));
      DCHECK(locations->GetTemp(1u).Equals(Location::RegisterLocation(first_callee_save)));
      locations->SetTempAt(0u, Location::RegisterLocation(second_callee_save));
    } else {
      DCHECK_EQ(locations->GetTempCount(), 1u);
      DCHECK(locations->GetTemp(0u).Equals(Location::RequiresRegister()));
      locations->SetTempAt(0u, Location::RegisterLocation(first_callee_save));
    }
  }

  size_t old_temp_count = locations->GetTempCount();
  DCHECK_EQ(old_temp_count, (expected_index == 1u) ? 2u : 1u);
  Location expected = locations->InAt(expected_index);
  Location new_value = locations->InAt(new_value_index);
  size_t data_size = DataType::Size(value_type);
  bool is_small = (data_size < 4u);
  bool can_byte_swap =
      (expected_index == 3u) && (value_type != DataType::Type::kReference && data_size != 1u);
  bool is_fp = DataType::IsFloatingPointType(value_type);
  size_t temps_needed =
      // The offset temp is used for the `tmp_ptr`, except for the read barrier case. For read
      // barrier we must preserve the offset and class pointer (if any) for the slow path and
      // use a separate temp for `tmp_ptr` and we also need another temp for `old_value_temp`.
      ((is_reference && codegen->EmitReadBarrier()) ? old_temp_count + 2u : 1u) +
      // For small values, we need a temp for the `mask`, `masked` and maybe also for the `shift`.
      (is_small ? (return_success ? 2u : 3u) : 0u) +
      // Some cases need modified copies of `new_value` and `expected`.
      (ScratchXRegisterNeeded(expected, value_type, can_byte_swap) ? 1u : 0u) +
      (ScratchXRegisterNeeded(new_value, value_type, can_byte_swap) ? 1u : 0u) +
      // We need a scratch register either for the old value or for the result of SC.
      // If we need to return a floating point old value, we need a temp for each.
      ((!return_success && is_fp) ? 2u : 1u);
  size_t scratch_registers_available = 2u;
  DCHECK_EQ(scratch_registers_available,
            ScratchRegisterScope(codegen->GetAssembler()).AvailableXRegisters());
  if (temps_needed > old_temp_count + scratch_registers_available) {
    locations->AddRegisterTemps(temps_needed - (old_temp_count + scratch_registers_available));
  }
}

static XRegister PrepareXRegister(CodeGeneratorRISCV64* codegen,
                                  Location loc,
                                  DataType::Type type,
                                  XRegister shift,
                                  XRegister mask,
                                  bool byte_swap,
                                  ScratchRegisterScope* srs) {
  DCHECK_IMPLIES(mask != kNoXRegister, shift != kNoXRegister);
  DCHECK_EQ(shift == kNoXRegister, DataType::Size(type) >= 4u);
  if (loc.IsConstant()) {
    // The `shift`/`mask` and `byte_swap` are irrelevant for zero input.
    DCHECK(loc.GetConstant()->IsZeroBitPattern());
    return Zero;
  }

  Location result = loc;
  if (DataType::IsFloatingPointType(type)) {
    type = IntTypeForFloatingPointType(type);
    result = Location::RegisterLocation(srs->AllocateXRegister());
    codegen->MoveLocation(result, loc, type);
    loc = result;
  } else if (byte_swap || shift != kNoXRegister) {
    result = Location::RegisterLocation(srs->AllocateXRegister());
  }
  if (byte_swap) {
    if (type == DataType::Type::kInt16) {
      type = DataType::Type::kUint16;  // Do the masking as part of the byte swap.
    }
    GenerateReverseBytes(codegen, result, loc.AsRegister<XRegister>(), type);
    loc = result;
  }
  if (shift != kNoXRegister) {
    Riscv64Assembler* assembler = codegen->GetAssembler();
    __ Sllw(result.AsRegister<XRegister>(), loc.AsRegister<XRegister>(), shift);
    DCHECK_NE(type, DataType::Type::kUint8);
    if (mask != kNoXRegister && type != DataType::Type::kUint16 && type != DataType::Type::kBool) {
      __ And(result.AsRegister<XRegister>(), result.AsRegister<XRegister>(), mask);
    }
  }
  return result.AsRegister<XRegister>();
}

static void GenerateByteSwapAndExtract(CodeGeneratorRISCV64* codegen,
                                       Location rd,
                                       XRegister rs1,
                                       XRegister shift,
                                       DataType::Type type) {
  // Apply shift before `GenerateReverseBytes()` for small types.
  DCHECK_EQ(shift != kNoXRegister, DataType::Size(type) < 4u);
  if (shift != kNoXRegister) {
    Riscv64Assembler* assembler = codegen->GetAssembler();
    __ Srlw(rd.AsRegister<XRegister>(), rs1, shift);
    rs1 = rd.AsRegister<XRegister>();
  }
  // Also handles moving to FP registers.
  GenerateReverseBytes(codegen, rd, rs1, type);
}

static void GenerateVarHandleCompareAndSetOrExchange(HInvoke* invoke,
                                                     CodeGeneratorRISCV64* codegen,
                                                     std::memory_order order,
                                                     bool return_success,
                                                     bool strong,
                                                     bool byte_swap = false) {
  DCHECK(return_success || strong);

  uint32_t expected_index = invoke->GetNumberOfArguments() - 2;
  uint32_t new_value_index = invoke->GetNumberOfArguments() - 1;
  DataType::Type value_type = GetDataTypeFromShorty(invoke, new_value_index);
  DCHECK_EQ(value_type, GetDataTypeFromShorty(invoke, expected_index));

  Riscv64Assembler* assembler = codegen->GetAssembler();
  LocationSummary* locations = invoke->GetLocations();
  Location expected = locations->InAt(expected_index);
  Location new_value = locations->InAt(new_value_index);
  Location out = locations->Out();

  VarHandleTarget target = GetVarHandleTarget(invoke);
  VarHandleSlowPathRISCV64* slow_path = nullptr;
  if (!byte_swap) {
    slow_path = GenerateVarHandleChecks(invoke, codegen, order, value_type);
    GenerateVarHandleTarget(invoke, target, codegen);
    if (slow_path != nullptr) {
      slow_path->SetCompareAndSetOrExchangeArgs(return_success, strong);
      __ Bind(slow_path->GetNativeByteOrderLabel());
    }
  }

  // This needs to be before we allocate the scratch registers, as MarkGCCard also uses them.
  if (CodeGenerator::StoreNeedsWriteBarrier(value_type, invoke->InputAt(new_value_index))) {
    // Mark card for object assuming new value is stored.
    bool new_value_can_be_null = true;  // TODO: Worth finding out this information?
    codegen->MaybeMarkGCCard(
        target.object, new_value.AsRegister<XRegister>(), new_value_can_be_null);
  }

  // Scratch registers may be needed for `new_value` and `expected`.
  ScratchRegisterScope srs(assembler);
  DCHECK_EQ(srs.AvailableXRegisters(), 2u);
  size_t available_scratch_registers =
      (ScratchXRegisterNeeded(expected, value_type, byte_swap) ? 0u : 1u) +
      (ScratchXRegisterNeeded(new_value, value_type, byte_swap) ? 0u : 1u);

  // Reuse the `offset` temporary for the pointer to the target location,
  // except for references that need the offset for the read barrier.
  DCHECK_EQ(target.offset, locations->GetTemp(0u).AsRegister<XRegister>());
  size_t next_temp = 1u;
  XRegister tmp_ptr = target.offset;
  bool is_reference = (value_type == DataType::Type::kReference);
  if (is_reference && codegen->EmitReadBarrier()) {
    // Reserve scratch registers for `tmp_ptr` and `old_value_temp`.
    DCHECK_EQ(available_scratch_registers, 2u);
    available_scratch_registers = 0u;
    DCHECK_EQ(expected_index, 1u + GetExpectedVarHandleCoordinatesCount(invoke));
    next_temp = expected_index == 1u ? 2u : 1u;  // Preserve the class register for static field.
    tmp_ptr = srs.AllocateXRegister();
  }
  __ Add(tmp_ptr, target.object, target.offset);

  auto get_temp = [&]() {
    if (available_scratch_registers != 0u) {
      available_scratch_registers -= 1u;
      return srs.AllocateXRegister();
    } else {
      XRegister temp = locations->GetTemp(next_temp).AsRegister<XRegister>();
      next_temp += 1u;
      return temp;
    }
  };

  XRegister shift = kNoXRegister;
  XRegister mask = kNoXRegister;
  XRegister masked = kNoXRegister;
  size_t data_size = DataType::Size(value_type);
  bool is_small = (data_size < 4u);
  if (is_small) {
    // When returning "success" and not the old value, we shall not need the `shift` after
    // the raw CAS operation, so use the output register as a temporary here.
    shift = return_success ? locations->Out().AsRegister<XRegister>() : get_temp();
    mask = get_temp();
    masked = get_temp();
    // Upper bits of the shift are not used, so we do not need to clear them.
    __ Slli(shift, tmp_ptr, WhichPowerOf2(kBitsPerByte));
    __ Andi(tmp_ptr, tmp_ptr, -4);
    __ Li(mask, (1 << (data_size * kBitsPerByte)) - 1);
    __ Sllw(mask, mask, shift);
  }

  // Move floating point values to scratch registers and apply shift, mask and byte swap if needed.
  // Note that float/double CAS uses bitwise comparison, rather than the operator==.
  XRegister expected_reg =
      PrepareXRegister(codegen, expected, value_type, shift, mask, byte_swap, &srs);
  XRegister new_value_reg =
      PrepareXRegister(codegen, new_value, value_type, shift, mask, byte_swap, &srs);
  bool is_fp = DataType::IsFloatingPointType(value_type);
  DataType::Type cas_type = is_fp
      ? IntTypeForFloatingPointType(value_type)
      : (is_small ? DataType::Type::kInt32 : value_type);

  // Prepare registers for old value and the result of the store conditional.
  XRegister old_value;
  XRegister store_result;
  if (return_success) {
    // Use a temp for the old value.
    old_value = get_temp();
    // For strong CAS, use the `old_value` temp also for the SC result.
    // For weak CAS, put the SC result directly to `out`.
    store_result = strong ? old_value : out.AsRegister<XRegister>();
  } else if (is_fp) {
    // We need two temporary registers.
    old_value = get_temp();
    store_result = get_temp();
  } else {
    // Use the output register for the old value and a temp for the store conditional result.
    old_value = out.AsRegister<XRegister>();
    store_result = get_temp();
  }

  Riscv64Label exit_loop_label;
  Riscv64Label* exit_loop = &exit_loop_label;
  Riscv64Label* cmp_failure = &exit_loop_label;

  ReadBarrierCasSlowPathRISCV64* rb_slow_path = nullptr;
  if (is_reference && codegen->EmitReadBarrier()) {
    // The `old_value_temp` is used first for marking the `old_value` and then for the unmarked
    // reloaded old value for subsequent CAS in the slow path. We make this a scratch register
    // as we do have marking entrypoints on riscv64 even for scratch registers.
    XRegister old_value_temp = srs.AllocateXRegister();
    // For strong CAS, use the `old_value_temp` also for the SC result as the reloaded old value
    // is no longer needed after the comparison. For weak CAS, store the SC result in the same
    // result register as the main path.
    // Note that for a strong CAS, a SC failure in the slow path can set the register to 1, so
    // we cannot use that register to indicate success without resetting it to 0 at the start of
    // the retry loop. Instead, we return to the success indicating instruction in the main path.
    XRegister slow_path_store_result = strong ? old_value_temp : store_result;
    rb_slow_path = new (codegen->GetScopedAllocator()) ReadBarrierCasSlowPathRISCV64(
        invoke,
        order,
        strong,
        target.object,
        target.offset,
        expected_reg,
        new_value_reg,
        old_value,
        old_value_temp,
        slow_path_store_result,
        /*update_old_value=*/ !return_success,
        codegen);
    codegen->AddSlowPath(rb_slow_path);
    exit_loop = rb_slow_path->GetExitLabel();
    cmp_failure = rb_slow_path->GetEntryLabel();
  }

  if (return_success) {
    // Pre-populate the output register with failure for the case when the old value
    // differs and we do not execute the store conditional.
    __ Li(out.AsRegister<XRegister>(), 0);
  }
  GenerateCompareAndSet(codegen->GetAssembler(),
                        cas_type,
                        order,
                        strong,
                        cmp_failure,
                        tmp_ptr,
                        new_value_reg,
                        old_value,
                        mask,
                        masked,
                        store_result,
                        expected_reg);
  if (return_success && strong) {
    if (rb_slow_path != nullptr) {
      // Slow path returns here on success.
      __ Bind(rb_slow_path->GetSuccessExitLabel());
    }
    // Load success value to the output register.
    // `GenerateCompareAndSet()` does not emit code to indicate success for a strong CAS.
    __ Li(out.AsRegister<XRegister>(), 1);
  } else if (rb_slow_path != nullptr) {
    DCHECK(!rb_slow_path->GetSuccessExitLabel()->IsLinked());
  }
  __ Bind(exit_loop);

  if (return_success) {
    // Nothing to do, the result register already contains 1 on success and 0 on failure.
  } else if (byte_swap) {
    DCHECK_IMPLIES(is_small, out.AsRegister<XRegister>() == old_value)
        << " " << value_type << " " << out.AsRegister<XRegister>() << "!=" << old_value;
    GenerateByteSwapAndExtract(codegen, out, old_value, shift, value_type);
  } else if (is_fp) {
    codegen->MoveLocation(out, Location::RegisterLocation(old_value), value_type);
  } else if (is_small) {
    __ Srlw(old_value, masked, shift);
    if (value_type == DataType::Type::kInt8) {
      __ SextB(old_value, old_value);
    } else if (value_type == DataType::Type::kInt16) {
      __ SextH(old_value, old_value);
    }
  }

  if (slow_path != nullptr) {
    DCHECK(!byte_swap);
    __ Bind(slow_path->GetExitLabel());
  }

  // Check that we have allocated the right number of temps. We may need more registers
  // for byte swapped CAS in the slow path, so skip this check for the main path in that case.
  bool has_byte_swap = (expected_index == 3u) && (!is_reference && data_size != 1u);
  if ((!has_byte_swap || byte_swap) && next_temp != locations->GetTempCount()) {
    // We allocate a temporary register for the class object for a static field `VarHandle` but
    // we do not update the `next_temp` if it's otherwise unused after the address calculation.
    CHECK_EQ(expected_index, 1u);
    CHECK_EQ(next_temp, 1u);
    CHECK_EQ(locations->GetTempCount(), 2u);
  }
}

void IntrinsicLocationsBuilderRISCV64::VisitVarHandleCompareAndExchange(HInvoke* invoke) {
  CreateVarHandleCompareAndSetOrExchangeLocations(invoke, codegen_, /*return_success=*/ false);
}

void IntrinsicCodeGeneratorRISCV64::VisitVarHandleCompareAndExchange(HInvoke* invoke) {
  GenerateVarHandleCompareAndSetOrExchange(
      invoke, codegen_, std::memory_order_seq_cst, /*return_success=*/ false, /*strong=*/ true);
}

void IntrinsicLocationsBuilderRISCV64::VisitVarHandleCompareAndExchangeAcquire(HInvoke* invoke) {
  CreateVarHandleCompareAndSetOrExchangeLocations(invoke, codegen_, /*return_success=*/ false);
}

void IntrinsicCodeGeneratorRISCV64::VisitVarHandleCompareAndExchangeAcquire(HInvoke* invoke) {
  GenerateVarHandleCompareAndSetOrExchange(
      invoke, codegen_, std::memory_order_acquire, /*return_success=*/ false, /*strong=*/ true);
}

void IntrinsicLocationsBuilderRISCV64::VisitVarHandleCompareAndExchangeRelease(HInvoke* invoke) {
  CreateVarHandleCompareAndSetOrExchangeLocations(invoke, codegen_, /*return_success=*/ false);
}

void IntrinsicCodeGeneratorRISCV64::VisitVarHandleCompareAndExchangeRelease(HInvoke* invoke) {
  GenerateVarHandleCompareAndSetOrExchange(
      invoke, codegen_, std::memory_order_release, /*return_success=*/ false, /*strong=*/ true);
}

void IntrinsicLocationsBuilderRISCV64::VisitVarHandleCompareAndSet(HInvoke* invoke) {
  CreateVarHandleCompareAndSetOrExchangeLocations(invoke, codegen_, /*return_success=*/ true);
}

void IntrinsicCodeGeneratorRISCV64::VisitVarHandleCompareAndSet(HInvoke* invoke) {
  GenerateVarHandleCompareAndSetOrExchange(
      invoke, codegen_, std::memory_order_seq_cst, /*return_success=*/ true, /*strong=*/ true);
}

void IntrinsicLocationsBuilderRISCV64::VisitVarHandleWeakCompareAndSet(HInvoke* invoke) {
  CreateVarHandleCompareAndSetOrExchangeLocations(invoke, codegen_, /*return_success=*/ true);
}

void IntrinsicCodeGeneratorRISCV64::VisitVarHandleWeakCompareAndSet(HInvoke* invoke) {
  GenerateVarHandleCompareAndSetOrExchange(
      invoke, codegen_, std::memory_order_seq_cst, /*return_success=*/ true, /*strong=*/ false);
}

void IntrinsicLocationsBuilderRISCV64::VisitVarHandleWeakCompareAndSetAcquire(HInvoke* invoke) {
  CreateVarHandleCompareAndSetOrExchangeLocations(invoke, codegen_, /*return_success=*/ true);
}

void IntrinsicCodeGeneratorRISCV64::VisitVarHandleWeakCompareAndSetAcquire(HInvoke* invoke) {
  GenerateVarHandleCompareAndSetOrExchange(
      invoke, codegen_, std::memory_order_acquire, /*return_success=*/ true, /*strong=*/ false);
}

void IntrinsicLocationsBuilderRISCV64::VisitVarHandleWeakCompareAndSetPlain(HInvoke* invoke) {
  CreateVarHandleCompareAndSetOrExchangeLocations(invoke, codegen_, /*return_success=*/ true);
}

void IntrinsicCodeGeneratorRISCV64::VisitVarHandleWeakCompareAndSetPlain(HInvoke* invoke) {
  GenerateVarHandleCompareAndSetOrExchange(
      invoke, codegen_, std::memory_order_relaxed, /*return_success=*/ true, /*strong=*/ false);
}

void IntrinsicLocationsBuilderRISCV64::VisitVarHandleWeakCompareAndSetRelease(HInvoke* invoke) {
  CreateVarHandleCompareAndSetOrExchangeLocations(invoke, codegen_, /*return_success=*/ true);
}

void IntrinsicCodeGeneratorRISCV64::VisitVarHandleWeakCompareAndSetRelease(HInvoke* invoke) {
  GenerateVarHandleCompareAndSetOrExchange(
      invoke, codegen_, std::memory_order_release, /*return_success=*/ true, /*strong=*/ false);
}

static void CreateVarHandleGetAndUpdateLocations(HInvoke* invoke,
                                                 CodeGeneratorRISCV64* codegen,
                                                 GetAndUpdateOp get_and_update_op) {
  VarHandleOptimizations optimizations(invoke);
  if (optimizations.GetDoNotIntrinsify()) {
    return;
  }

  if (invoke->GetType() == DataType::Type::kReference && codegen->EmitNonBakerReadBarrier()) {
    // Unsupported for non-Baker read barrier because the artReadBarrierSlow() ignores
    // the passed reference and reloads it from the field, thus seeing the new value
    // that we have just stored. (And it also gets the memory visibility wrong.) b/173104084
    return;
  }

  // TODO(riscv64): Fix this intrinsic for heap poisoning configuration.
  if (kPoisonHeapReferences && invoke->GetType() == DataType::Type::kReference) {
    return;
  }

  LocationSummary* locations = CreateVarHandleCommonLocations(invoke, codegen);
  uint32_t arg_index = invoke->GetNumberOfArguments() - 1;
  DCHECK_EQ(arg_index, 1u + GetExpectedVarHandleCoordinatesCount(invoke));
  DataType::Type value_type = invoke->GetType();
  DCHECK_EQ(value_type, GetDataTypeFromShorty(invoke, arg_index));
  Location arg = locations->InAt(arg_index);

  bool is_fp = DataType::IsFloatingPointType(value_type);
  if (is_fp) {
    if (get_and_update_op == GetAndUpdateOp::kAdd) {
      // For ADD, do not use ZR for zero bit pattern (+0.0f or +0.0).
      locations->SetInAt(invoke->GetNumberOfArguments() - 1u, Location::RequiresFpuRegister());
    } else {
      DCHECK(get_and_update_op == GetAndUpdateOp::kSet);
    }
  }

  size_t data_size = DataType::Size(value_type);
  bool can_byte_swap =
      (arg_index == 3u) && (value_type != DataType::Type::kReference && data_size != 1u);
  bool can_use_cas = (get_and_update_op == GetAndUpdateOp::kAdd) && (can_byte_swap || is_fp);
  bool is_small = (data_size < 4u);
  bool is_small_and = is_small && (get_and_update_op == GetAndUpdateOp::kAnd);
  bool is_bitwise =
      (get_and_update_op != GetAndUpdateOp::kSet && get_and_update_op != GetAndUpdateOp::kAdd);

  size_t temps_needed =
      // The offset temp is used for the `tmp_ptr`.
      1u +
      // For small values, we need temps for `shift` and maybe also `mask` and `temp`.
      (is_small ? (is_bitwise ? 1u : 3u) : 0u) +
      // Some cases need modified copies of `arg`.
      (is_small_and || ScratchXRegisterNeeded(arg, value_type, can_byte_swap) ? 1u : 0u) +
      // For FP types, we need a temp for `old_value` which cannot be loaded directly to `out`.
      (is_fp ? 1u : 0u);
  if (can_use_cas) {
    size_t cas_temps_needed =
        // The offset temp is used for the `tmp_ptr`.
        1u +
        // For small values, we need a temp for `shift`.
        (is_small ? 1u : 0u) +
        // And we always need temps for `old_value`, `new_value` and `reloaded_old_value`.
        3u;
    DCHECK_GE(cas_temps_needed, temps_needed);
    temps_needed = cas_temps_needed;
  }

  size_t scratch_registers_available = 2u;
  DCHECK_EQ(scratch_registers_available,
            ScratchRegisterScope(codegen->GetAssembler()).AvailableXRegisters());
  size_t old_temp_count = locations->GetTempCount();
  DCHECK_EQ(old_temp_count, (arg_index == 1u) ? 2u : 1u);
  if (temps_needed > old_temp_count + scratch_registers_available) {
    locations->AddRegisterTemps(temps_needed - (old_temp_count + scratch_registers_available));
  }
}

static void GenerateVarHandleGetAndUpdate(HInvoke* invoke,
                                          CodeGeneratorRISCV64* codegen,
                                          GetAndUpdateOp get_and_update_op,
                                          std::memory_order order,
                                          bool byte_swap = false) {
  uint32_t arg_index = invoke->GetNumberOfArguments() - 1;
  DCHECK_EQ(arg_index, 1u + GetExpectedVarHandleCoordinatesCount(invoke));
  DataType::Type value_type = invoke->GetType();
  DCHECK_EQ(value_type, GetDataTypeFromShorty(invoke, arg_index));

  Riscv64Assembler* assembler = codegen->GetAssembler();
  LocationSummary* locations = invoke->GetLocations();
  Location arg = locations->InAt(arg_index);
  DCHECK_IMPLIES(arg.IsConstant(), arg.GetConstant()->IsZeroBitPattern());
  Location out = locations->Out();

  VarHandleTarget target = GetVarHandleTarget(invoke);
  VarHandleSlowPathRISCV64* slow_path = nullptr;
  if (!byte_swap) {
    slow_path = GenerateVarHandleChecks(invoke, codegen, order, value_type);
    GenerateVarHandleTarget(invoke, target, codegen);
    if (slow_path != nullptr) {
      slow_path->SetGetAndUpdateOp(get_and_update_op);
      __ Bind(slow_path->GetNativeByteOrderLabel());
    }
  }

  // This needs to be before the temp registers, as MarkGCCard also uses scratch registers.
  if (CodeGenerator::StoreNeedsWriteBarrier(value_type, invoke->InputAt(arg_index))) {
    DCHECK(get_and_update_op == GetAndUpdateOp::kSet);
    // Mark card for object, the new value shall be stored.
    bool new_value_can_be_null = true;  // TODO: Worth finding out this information?
    codegen->MaybeMarkGCCard(target.object, arg.AsRegister<XRegister>(), new_value_can_be_null);
  }

  size_t data_size = DataType::Size(value_type);
  bool is_fp = DataType::IsFloatingPointType(value_type);
  bool use_cas = (get_and_update_op == GetAndUpdateOp::kAdd) && (byte_swap || is_fp);
  bool is_small = (data_size < 4u);
  bool is_small_and = is_small && (get_and_update_op == GetAndUpdateOp::kAnd);
  bool is_reference = (value_type == DataType::Type::kReference);
  DataType::Type op_type = is_fp
      ? IntTypeForFloatingPointType(value_type)
      : (is_small || is_reference ? DataType::Type::kInt32 : value_type);

  ScratchRegisterScope srs(assembler);
  DCHECK_EQ(srs.AvailableXRegisters(), 2u);
  size_t available_scratch_registers = use_cas
      // We use scratch registers differently for the CAS path.
      ? 0u
      // Reserve one scratch register for `PrepareXRegister()` or similar `arg_reg` allocation.
      : (is_small_and || ScratchXRegisterNeeded(arg, value_type, byte_swap) ? 1u : 2u);

  // Reuse the `target.offset` temporary for the pointer to the target location,
  // except for references that need the offset for the non-Baker read barrier.
  DCHECK_EQ(target.offset, locations->GetTemp(0u).AsRegister<XRegister>());
  size_t next_temp = 1u;
  XRegister tmp_ptr = target.offset;
  if (is_reference && codegen->EmitNonBakerReadBarrier()) {
    DCHECK_EQ(available_scratch_registers, 2u);
    available_scratch_registers -= 1u;
    tmp_ptr = srs.AllocateXRegister();
  }
  __ Add(tmp_ptr, target.object, target.offset);

  auto get_temp = [&]() {
    if (available_scratch_registers != 0u) {
      available_scratch_registers -= 1u;
      return srs.AllocateXRegister();
    } else {
      XRegister temp = locations->GetTemp(next_temp).AsRegister<XRegister>();
      next_temp += 1u;
      return temp;
    }
  };

  XRegister shift = kNoXRegister;
  XRegister mask = kNoXRegister;
  XRegister prepare_mask = kNoXRegister;
  XRegister temp = kNoXRegister;
  XRegister arg_reg = kNoXRegister;
  if (is_small) {
    shift = get_temp();
    // Upper bits of the shift are not used, so we do not need to clear them.
    __ Slli(shift, tmp_ptr, WhichPowerOf2(kBitsPerByte));
    __ Andi(tmp_ptr, tmp_ptr, -4);
    switch (get_and_update_op) {
      case GetAndUpdateOp::kAdd:
        if (byte_swap) {
          // The mask is not needed in the CAS path.
          DCHECK(use_cas);
          break;
        }
        FALLTHROUGH_INTENDED;
      case GetAndUpdateOp::kSet:
        mask = get_temp();
        temp = get_temp();
        __ Li(mask, (1 << (data_size * kBitsPerByte)) - 1);
        __ Sllw(mask, mask, shift);
        // The argument does not need to be masked for `GetAndUpdateOp::kAdd`,
        // the mask shall be applied after the ADD instruction.
        prepare_mask = (get_and_update_op == GetAndUpdateOp::kSet) ? mask : kNoXRegister;
        break;
      case GetAndUpdateOp::kAnd:
        // We need to set all other bits, so we always need a temp.
        arg_reg = srs.AllocateXRegister();
        if (data_size == 1u) {
          __ Ori(arg_reg, InputXRegisterOrZero(arg), ~0xff);
          DCHECK(!byte_swap);
        } else {
          DCHECK_EQ(data_size, 2u);
          __ Li(arg_reg, ~0xffff);
          __ Or(arg_reg, InputXRegisterOrZero(arg), arg_reg);
          if (byte_swap) {
            __ Rev8(arg_reg, arg_reg);
            __ Rori(arg_reg, arg_reg, 48);
          }
        }
        __ Rolw(arg_reg, arg_reg, shift);
        break;
      case GetAndUpdateOp::kOr:
      case GetAndUpdateOp::kXor:
        // Signed values need to be truncated but we're keeping `prepare_mask == kNoXRegister`.
        if (value_type == DataType::Type::kInt8 && !arg.IsConstant()) {
          DCHECK(!byte_swap);
          arg_reg = srs.AllocateXRegister();
          __ ZextB(arg_reg, arg.AsRegister<XRegister>());
          __ Sllw(arg_reg, arg_reg, shift);
        } else if (value_type == DataType::Type::kInt16 && !arg.IsConstant() && !byte_swap) {
          arg_reg = srs.AllocateXRegister();
          __ ZextH(arg_reg, arg.AsRegister<XRegister>());
          __ Sllw(arg_reg, arg_reg, shift);
        }  // else handled by `PrepareXRegister()` below.
        break;
    }
  }
  if (arg_reg == kNoXRegister && !use_cas) {
    arg_reg = PrepareXRegister(codegen, arg, value_type, shift, prepare_mask, byte_swap, &srs);
  }
  if (mask != kNoXRegister && get_and_update_op == GetAndUpdateOp::kSet) {
    __ Not(mask, mask);  // We need to flip the mask for `kSet`, see `GenerateGetAndUpdate()`.
  }

  if (use_cas) {
    // Allocate scratch registers for temps that can theoretically be clobbered on retry.
    // (Even though the `retry` label shall never be far enough for `TMP` to be clobbered.)
    DCHECK_EQ(available_scratch_registers, 0u);  // Reserved for the two uses below.
    XRegister old_value = srs.AllocateXRegister();
    XRegister new_value = srs.AllocateXRegister();
    // Allocate other needed temporaries.
    XRegister reloaded_old_value = get_temp();
    XRegister store_result = reloaded_old_value;  // Clobber reloaded old value by store result.
    FRegister ftmp = is_fp ? srs.AllocateFRegister() : kNoFRegister;

    Riscv64Label retry;
    __ Bind(&retry);
    codegen->GetInstructionVisitor()->Load(
        Location::RegisterLocation(old_value), tmp_ptr, /*offset=*/ 0, op_type);
    if (byte_swap) {
      GenerateByteSwapAndExtract(codegen, out, old_value, shift, value_type);
    } else {
      DCHECK(is_fp);
      codegen->MoveLocation(out, Location::RegisterLocation(old_value), value_type);
    }
    if (is_fp) {
      codegen->GetInstructionVisitor()->FAdd(
          ftmp, out.AsFpuRegister<FRegister>(), arg.AsFpuRegister<FRegister>(), value_type);
      codegen->MoveLocation(
          Location::RegisterLocation(new_value), Location::FpuRegisterLocation(ftmp), op_type);
    } else if (value_type == DataType::Type::kInt64) {
      __ Add(new_value, out.AsRegister<XRegister>(), arg.AsRegister<XRegister>());
    } else {
      DCHECK_EQ(op_type, DataType::Type::kInt32);
      __ Addw(new_value, out.AsRegister<XRegister>(), arg.AsRegister<XRegister>());
    }
    if (byte_swap) {
      DataType::Type swap_type = op_type;
      if (is_small) {
        DCHECK_EQ(data_size, 2u);
        // We want to update only 16 bits of the 32-bit location. The 16 bits we want to replace
        // are present in both `old_value` and `out` but in different bits and byte order.
        // To update the 16 bits, we can XOR the new value with the `out`, byte swap as Uint16
        // (extracting only the bits we want to update), shift and XOR with the old value.
        swap_type = DataType::Type::kUint16;
        __ Xor(new_value, new_value, out.AsRegister<XRegister>());
      }
      GenerateReverseBytes(codegen, Location::RegisterLocation(new_value), new_value, swap_type);
      if (is_small) {
        __ Sllw(new_value, new_value, shift);
        __ Xor(new_value, new_value, old_value);
      }
    }
    GenerateCompareAndSet(assembler,
                          op_type,
                          order,
                          /*strong=*/ true,
                          /*cmp_failure=*/ &retry,
                          tmp_ptr,
                          new_value,
                          /*old_value=*/ reloaded_old_value,
                          /*mask=*/ kNoXRegister,
                          /*masked=*/ kNoXRegister,
                          store_result,
                          /*expected=*/ old_value);
  } else {
    XRegister old_value = is_fp ? get_temp() : out.AsRegister<XRegister>();
    GenerateGetAndUpdate(
        codegen, get_and_update_op, op_type, order, tmp_ptr, arg_reg, old_value, mask, temp);
    if (byte_swap) {
      DCHECK_IMPLIES(is_small, out.AsRegister<XRegister>() == old_value)
          << " " << value_type << " " << out.AsRegister<XRegister>() << "!=" << old_value;
      GenerateByteSwapAndExtract(codegen, out, old_value, shift, value_type);
    } else if (is_fp) {
      codegen->MoveLocation(out, Location::RegisterLocation(old_value), value_type);
    } else if (is_small) {
      __ Srlw(old_value, old_value, shift);
      DCHECK_NE(value_type, DataType::Type::kUint8);
      if (value_type == DataType::Type::kInt8) {
        __ SextB(old_value, old_value);
      } else if (value_type == DataType::Type::kBool) {
        __ ZextB(old_value, old_value);
      } else if (value_type == DataType::Type::kInt16) {
        __ SextH(old_value, old_value);
      } else {
        DCHECK_EQ(value_type, DataType::Type::kUint16);
        __ ZextH(old_value, old_value);
      }
    } else if (is_reference) {
      __ ZextW(old_value, old_value);
      if (codegen->EmitBakerReadBarrier()) {
        // Use RA as temp. It is clobbered in the slow path anyway.
        static constexpr Location kBakerReadBarrierTemp = Location::RegisterLocation(RA);
        SlowPathCodeRISCV64* rb_slow_path =
            codegen->AddGcRootBakerBarrierBarrierSlowPath(invoke, out, kBakerReadBarrierTemp);
        codegen->EmitBakerReadBarierMarkingCheck(rb_slow_path, out, kBakerReadBarrierTemp);
      } else if (codegen->EmitNonBakerReadBarrier()) {
        Location base_loc = Location::RegisterLocation(target.object);
        Location index = Location::RegisterLocation(target.offset);
        SlowPathCodeRISCV64* rb_slow_path = codegen->AddReadBarrierSlowPath(
            invoke, out, out, base_loc, /*offset=*/ 0u, index);
        __ J(rb_slow_path->GetEntryLabel());
        __ Bind(rb_slow_path->GetExitLabel());
      }
    }
  }

  if (slow_path != nullptr) {
    DCHECK(!byte_swap);
    __ Bind(slow_path->GetExitLabel());
  }

  // Check that we have allocated the right number of temps. We may need more registers
  // for byte swapped CAS in the slow path, so skip this check for the main path in that case.
  bool has_byte_swap = (arg_index == 3u) && (!is_reference && data_size != 1u);
  if ((!has_byte_swap || byte_swap) && next_temp != locations->GetTempCount()) {
    // We allocate a temporary register for the class object for a static field `VarHandle` but
    // we do not update the `next_temp` if it's otherwise unused after the address calculation.
    CHECK_EQ(arg_index, 1u);
    CHECK_EQ(next_temp, 1u);
    CHECK_EQ(locations->GetTempCount(), 2u);
  }
}

void IntrinsicLocationsBuilderRISCV64::VisitVarHandleGetAndSet(HInvoke* invoke) {
  CreateVarHandleGetAndUpdateLocations(invoke, codegen_, GetAndUpdateOp::kSet);
}

void IntrinsicCodeGeneratorRISCV64::VisitVarHandleGetAndSet(HInvoke* invoke) {
  GenerateVarHandleGetAndUpdate(invoke, codegen_, GetAndUpdateOp::kSet, std::memory_order_seq_cst);
}

void IntrinsicLocationsBuilderRISCV64::VisitVarHandleGetAndSetAcquire(HInvoke* invoke) {
  CreateVarHandleGetAndUpdateLocations(invoke, codegen_, GetAndUpdateOp::kSet);
}

void IntrinsicCodeGeneratorRISCV64::VisitVarHandleGetAndSetAcquire(HInvoke* invoke) {
  GenerateVarHandleGetAndUpdate(invoke, codegen_, GetAndUpdateOp::kSet, std::memory_order_acquire);
}

void IntrinsicLocationsBuilderRISCV64::VisitVarHandleGetAndSetRelease(HInvoke* invoke) {
  CreateVarHandleGetAndUpdateLocations(invoke, codegen_, GetAndUpdateOp::kSet);
}

void IntrinsicCodeGeneratorRISCV64::VisitVarHandleGetAndSetRelease(HInvoke* invoke) {
  GenerateVarHandleGetAndUpdate(invoke, codegen_, GetAndUpdateOp::kSet, std::memory_order_release);
}

void IntrinsicLocationsBuilderRISCV64::VisitVarHandleGetAndAdd(HInvoke* invoke) {
  CreateVarHandleGetAndUpdateLocations(invoke, codegen_, GetAndUpdateOp::kAdd);
}

void IntrinsicCodeGeneratorRISCV64::VisitVarHandleGetAndAdd(HInvoke* invoke) {
  GenerateVarHandleGetAndUpdate(invoke, codegen_, GetAndUpdateOp::kAdd, std::memory_order_seq_cst);
}

void IntrinsicLocationsBuilderRISCV64::VisitVarHandleGetAndAddAcquire(HInvoke* invoke) {
  CreateVarHandleGetAndUpdateLocations(invoke, codegen_, GetAndUpdateOp::kAdd);
}

void IntrinsicCodeGeneratorRISCV64::VisitVarHandleGetAndAddAcquire(HInvoke* invoke) {
  GenerateVarHandleGetAndUpdate(invoke, codegen_, GetAndUpdateOp::kAdd, std::memory_order_acquire);
}

void IntrinsicLocationsBuilderRISCV64::VisitVarHandleGetAndAddRelease(HInvoke* invoke) {
  CreateVarHandleGetAndUpdateLocations(invoke, codegen_, GetAndUpdateOp::kAdd);
}

void IntrinsicCodeGeneratorRISCV64::VisitVarHandleGetAndAddRelease(HInvoke* invoke) {
  GenerateVarHandleGetAndUpdate(invoke, codegen_, GetAndUpdateOp::kAdd, std::memory_order_release);
}

void IntrinsicLocationsBuilderRISCV64::VisitVarHandleGetAndBitwiseAnd(HInvoke* invoke) {
  CreateVarHandleGetAndUpdateLocations(invoke, codegen_, GetAndUpdateOp::kAnd);
}

void IntrinsicCodeGeneratorRISCV64::VisitVarHandleGetAndBitwiseAnd(HInvoke* invoke) {
  GenerateVarHandleGetAndUpdate(invoke, codegen_, GetAndUpdateOp::kAnd, std::memory_order_seq_cst);
}

void IntrinsicLocationsBuilderRISCV64::VisitVarHandleGetAndBitwiseAndAcquire(HInvoke* invoke) {
  CreateVarHandleGetAndUpdateLocations(invoke, codegen_, GetAndUpdateOp::kAnd);
}

void IntrinsicCodeGeneratorRISCV64::VisitVarHandleGetAndBitwiseAndAcquire(HInvoke* invoke) {
  GenerateVarHandleGetAndUpdate(invoke, codegen_, GetAndUpdateOp::kAnd, std::memory_order_acquire);
}

void IntrinsicLocationsBuilderRISCV64::VisitVarHandleGetAndBitwiseAndRelease(HInvoke* invoke) {
  CreateVarHandleGetAndUpdateLocations(invoke, codegen_, GetAndUpdateOp::kAnd);
}

void IntrinsicCodeGeneratorRISCV64::VisitVarHandleGetAndBitwiseAndRelease(HInvoke* invoke) {
  GenerateVarHandleGetAndUpdate(invoke, codegen_, GetAndUpdateOp::kAnd, std::memory_order_release);
}

void IntrinsicLocationsBuilderRISCV64::VisitVarHandleGetAndBitwiseOr(HInvoke* invoke) {
  CreateVarHandleGetAndUpdateLocations(invoke, codegen_, GetAndUpdateOp::kOr);
}

void IntrinsicCodeGeneratorRISCV64::VisitVarHandleGetAndBitwiseOr(HInvoke* invoke) {
  GenerateVarHandleGetAndUpdate(invoke, codegen_, GetAndUpdateOp::kOr, std::memory_order_seq_cst);
}

void IntrinsicLocationsBuilderRISCV64::VisitVarHandleGetAndBitwiseOrAcquire(HInvoke* invoke) {
  CreateVarHandleGetAndUpdateLocations(invoke, codegen_, GetAndUpdateOp::kOr);
}

void IntrinsicCodeGeneratorRISCV64::VisitVarHandleGetAndBitwiseOrAcquire(HInvoke* invoke) {
  GenerateVarHandleGetAndUpdate(invoke, codegen_, GetAndUpdateOp::kOr, std::memory_order_acquire);
}

void IntrinsicLocationsBuilderRISCV64::VisitVarHandleGetAndBitwiseOrRelease(HInvoke* invoke) {
  CreateVarHandleGetAndUpdateLocations(invoke, codegen_, GetAndUpdateOp::kOr);
}

void IntrinsicCodeGeneratorRISCV64::VisitVarHandleGetAndBitwiseOrRelease(HInvoke* invoke) {
  GenerateVarHandleGetAndUpdate(invoke, codegen_, GetAndUpdateOp::kOr, std::memory_order_release);
}

void IntrinsicLocationsBuilderRISCV64::VisitVarHandleGetAndBitwiseXor(HInvoke* invoke) {
  CreateVarHandleGetAndUpdateLocations(invoke, codegen_, GetAndUpdateOp::kXor);
}

void IntrinsicCodeGeneratorRISCV64::VisitVarHandleGetAndBitwiseXor(HInvoke* invoke) {
  GenerateVarHandleGetAndUpdate(invoke, codegen_, GetAndUpdateOp::kXor, std::memory_order_seq_cst);
}

void IntrinsicLocationsBuilderRISCV64::VisitVarHandleGetAndBitwiseXorAcquire(HInvoke* invoke) {
  CreateVarHandleGetAndUpdateLocations(invoke, codegen_, GetAndUpdateOp::kXor);
}

void IntrinsicCodeGeneratorRISCV64::VisitVarHandleGetAndBitwiseXorAcquire(HInvoke* invoke) {
  GenerateVarHandleGetAndUpdate(invoke, codegen_, GetAndUpdateOp::kXor, std::memory_order_acquire);
}

void IntrinsicLocationsBuilderRISCV64::VisitVarHandleGetAndBitwiseXorRelease(HInvoke* invoke) {
  CreateVarHandleGetAndUpdateLocations(invoke, codegen_, GetAndUpdateOp::kXor);
}

void IntrinsicCodeGeneratorRISCV64::VisitVarHandleGetAndBitwiseXorRelease(HInvoke* invoke) {
  GenerateVarHandleGetAndUpdate(invoke, codegen_, GetAndUpdateOp::kXor, std::memory_order_release);
}

void VarHandleSlowPathRISCV64::EmitByteArrayViewCode(CodeGenerator* codegen_in) {
  DCHECK(GetByteArrayViewCheckLabel()->IsLinked());
  CodeGeneratorRISCV64* codegen = down_cast<CodeGeneratorRISCV64*>(codegen_in);
  Riscv64Assembler* assembler = codegen->GetAssembler();
  HInvoke* invoke = GetInvoke();
  mirror::VarHandle::AccessModeTemplate access_mode_template = GetAccessModeTemplate();
  DataType::Type value_type =
      GetVarHandleExpectedValueType(invoke, /*expected_coordinates_count=*/ 2u);
  DCHECK_NE(value_type, DataType::Type::kReference);
  size_t size = DataType::Size(value_type);
  DCHECK_GT(size, 1u);
  LocationSummary* locations = invoke->GetLocations();
  XRegister varhandle = locations->InAt(0).AsRegister<XRegister>();
  XRegister object = locations->InAt(1).AsRegister<XRegister>();
  XRegister index = locations->InAt(2).AsRegister<XRegister>();

  MemberOffset class_offset = mirror::Object::ClassOffset();
  MemberOffset array_length_offset = mirror::Array::LengthOffset();
  MemberOffset data_offset = mirror::Array::DataOffset(Primitive::kPrimByte);
  MemberOffset native_byte_order_offset = mirror::ByteArrayViewVarHandle::NativeByteOrderOffset();

  __ Bind(GetByteArrayViewCheckLabel());

  VarHandleTarget target = GetVarHandleTarget(invoke);
  {
    ScratchRegisterScope srs(assembler);
    XRegister temp = srs.AllocateXRegister();
    XRegister temp2 = srs.AllocateXRegister();

    // The main path checked that the coordinateType0 is an array class that matches
    // the class of the actual coordinate argument but it does not match the value type.
    // Check if the `varhandle` references a ByteArrayViewVarHandle instance.
    __ Loadwu(temp, varhandle, class_offset.Int32Value());
    codegen->MaybeUnpoisonHeapReference(temp);
    codegen->LoadClassRootForIntrinsic(temp2, ClassRoot::kJavaLangInvokeByteArrayViewVarHandle);
    __ Bne(temp, temp2, GetEntryLabel());

    // Check for array index out of bounds.
    __ Loadw(temp, object, array_length_offset.Int32Value());
    __ Bgeu(index, temp, GetEntryLabel());
    __ Addi(temp2, index, size - 1u);
    __ Bgeu(temp2, temp, GetEntryLabel());

    // Construct the target.
    __ Addi(target.offset, index, data_offset.Int32Value());

    // Alignment check. For unaligned access, go to the runtime.
    DCHECK(IsPowerOfTwo(size));
    __ Andi(temp, target.offset, size - 1u);
    __ Bnez(temp, GetEntryLabel());

    // Byte order check. For native byte order return to the main path.
    if (access_mode_template == mirror::VarHandle::AccessModeTemplate::kSet &&
        IsZeroBitPattern(invoke->InputAt(invoke->GetNumberOfArguments() - 1u))) {
      // There is no reason to differentiate between native byte order and byte-swap
      // for setting a zero bit pattern. Just return to the main path.
      __ J(GetNativeByteOrderLabel());
      return;
    }
    __ Loadbu(temp, varhandle, native_byte_order_offset.Int32Value());
    __ Bnez(temp, GetNativeByteOrderLabel());
  }

  switch (access_mode_template) {
    case mirror::VarHandle::AccessModeTemplate::kGet:
      GenerateVarHandleGet(invoke, codegen, order_, /*byte_swap=*/ true);
      break;
    case mirror::VarHandle::AccessModeTemplate::kSet:
      GenerateVarHandleSet(invoke, codegen, order_, /*byte_swap=*/ true);
      break;
    case mirror::VarHandle::AccessModeTemplate::kCompareAndSet:
    case mirror::VarHandle::AccessModeTemplate::kCompareAndExchange:
      GenerateVarHandleCompareAndSetOrExchange(
          invoke, codegen, order_, return_success_, strong_, /*byte_swap=*/ true);
      break;
    case mirror::VarHandle::AccessModeTemplate::kGetAndUpdate:
      GenerateVarHandleGetAndUpdate(
          invoke, codegen, get_and_update_op_, order_, /*byte_swap=*/ true);
      break;
  }
  __ J(GetExitLabel());
}

void IntrinsicLocationsBuilderRISCV64::VisitThreadCurrentThread(HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator_) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetOut(Location::RequiresRegister());
}

void IntrinsicCodeGeneratorRISCV64::VisitThreadCurrentThread(HInvoke* invoke) {
  Riscv64Assembler* assembler = GetAssembler();
  XRegister out = invoke->GetLocations()->Out().AsRegister<XRegister>();
  __ Loadwu(out, TR, Thread::PeerOffset<kRiscv64PointerSize>().Int32Value());
}

void IntrinsicLocationsBuilderRISCV64::VisitThreadInterrupted(HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator_) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetOut(Location::RequiresRegister());
}

void IntrinsicCodeGeneratorRISCV64::VisitThreadInterrupted(HInvoke* invoke) {
  LocationSummary* locations = invoke->GetLocations();
  Riscv64Assembler* assembler = GetAssembler();
  XRegister out = locations->Out().AsRegister<XRegister>();
  Riscv64Label done;

  codegen_->GenerateMemoryBarrier(MemBarrierKind::kAnyAny);
  __ Loadw(out, TR, Thread::InterruptedOffset<kRiscv64PointerSize>().Int32Value());
  __ Beqz(out, &done);
  __ Storew(Zero, TR, Thread::InterruptedOffset<kRiscv64PointerSize>().Int32Value());
  codegen_->GenerateMemoryBarrier(MemBarrierKind::kAnyAny);
  __ Bind(&done);
}

void IntrinsicLocationsBuilderRISCV64::VisitReachabilityFence(HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator_) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::Any());
}

void IntrinsicCodeGeneratorRISCV64::VisitReachabilityFence([[maybe_unused]] HInvoke* invoke) {}

void IntrinsicLocationsBuilderRISCV64::VisitMathFmaDouble(HInvoke* invoke) {
  CreateFpFpFpToFpNoOverlapLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitMathFmaDouble(HInvoke* invoke) {
  LocationSummary* locations = invoke->GetLocations();
  Riscv64Assembler* assembler = GetAssembler();
  FRegister n = locations->InAt(0).AsFpuRegister<FRegister>();
  FRegister m = locations->InAt(1).AsFpuRegister<FRegister>();
  FRegister a = locations->InAt(2).AsFpuRegister<FRegister>();
  FRegister out = locations->Out().AsFpuRegister<FRegister>();

  __ FMAddD(out, n, m, a);
}

void IntrinsicLocationsBuilderRISCV64::VisitMathFmaFloat(HInvoke* invoke) {
  CreateFpFpFpToFpNoOverlapLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitMathFmaFloat(HInvoke* invoke) {
  LocationSummary* locations = invoke->GetLocations();
  Riscv64Assembler* assembler = GetAssembler();
  FRegister n = locations->InAt(0).AsFpuRegister<FRegister>();
  FRegister m = locations->InAt(1).AsFpuRegister<FRegister>();
  FRegister a = locations->InAt(2).AsFpuRegister<FRegister>();
  FRegister out = locations->Out().AsFpuRegister<FRegister>();

  __ FMAddS(out, n, m, a);
}


void IntrinsicLocationsBuilderRISCV64::VisitMathCos(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitMathCos(HInvoke* invoke) {
  codegen_->InvokeRuntime(kQuickCos, invoke, invoke->GetDexPc());
}

void IntrinsicLocationsBuilderRISCV64::VisitMathSin(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitMathSin(HInvoke* invoke) {
  codegen_->InvokeRuntime(kQuickSin, invoke, invoke->GetDexPc());
}

void IntrinsicLocationsBuilderRISCV64::VisitMathAcos(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitMathAcos(HInvoke* invoke) {
  codegen_->InvokeRuntime(kQuickAcos, invoke, invoke->GetDexPc());
}

void IntrinsicLocationsBuilderRISCV64::VisitMathAsin(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitMathAsin(HInvoke* invoke) {
  codegen_->InvokeRuntime(kQuickAsin, invoke, invoke->GetDexPc());
}

void IntrinsicLocationsBuilderRISCV64::VisitMathAtan(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitMathAtan(HInvoke* invoke) {
  codegen_->InvokeRuntime(kQuickAtan, invoke, invoke->GetDexPc());
}

void IntrinsicLocationsBuilderRISCV64::VisitMathAtan2(HInvoke* invoke) {
  CreateFPFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitMathAtan2(HInvoke* invoke) {
  codegen_->InvokeRuntime(kQuickAtan2, invoke, invoke->GetDexPc());
}

void IntrinsicLocationsBuilderRISCV64::VisitMathPow(HInvoke* invoke) {
  CreateFPFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitMathPow(HInvoke* invoke) {
  codegen_->InvokeRuntime(kQuickPow, invoke, invoke->GetDexPc());
}

void IntrinsicLocationsBuilderRISCV64::VisitMathCbrt(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitMathCbrt(HInvoke* invoke) {
  codegen_->InvokeRuntime(kQuickCbrt, invoke, invoke->GetDexPc());
}

void IntrinsicLocationsBuilderRISCV64::VisitMathCosh(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitMathCosh(HInvoke* invoke) {
  codegen_->InvokeRuntime(kQuickCosh, invoke, invoke->GetDexPc());
}

void IntrinsicLocationsBuilderRISCV64::VisitMathExp(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitMathExp(HInvoke* invoke) {
  codegen_->InvokeRuntime(kQuickExp, invoke, invoke->GetDexPc());
}

void IntrinsicLocationsBuilderRISCV64::VisitMathExpm1(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitMathExpm1(HInvoke* invoke) {
  codegen_->InvokeRuntime(kQuickExpm1, invoke, invoke->GetDexPc());
}

void IntrinsicLocationsBuilderRISCV64::VisitMathHypot(HInvoke* invoke) {
  CreateFPFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitMathHypot(HInvoke* invoke) {
  codegen_->InvokeRuntime(kQuickHypot, invoke, invoke->GetDexPc());
}

void IntrinsicLocationsBuilderRISCV64::VisitMathLog(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitMathLog(HInvoke* invoke) {
  codegen_->InvokeRuntime(kQuickLog, invoke, invoke->GetDexPc());
}

void IntrinsicLocationsBuilderRISCV64::VisitMathLog10(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitMathLog10(HInvoke* invoke) {
  codegen_->InvokeRuntime(kQuickLog10, invoke, invoke->GetDexPc());
}

void IntrinsicLocationsBuilderRISCV64::VisitMathNextAfter(HInvoke* invoke) {
  CreateFPFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitMathNextAfter(HInvoke* invoke) {
  codegen_->InvokeRuntime(kQuickNextAfter, invoke, invoke->GetDexPc());
}

void IntrinsicLocationsBuilderRISCV64::VisitMathSinh(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitMathSinh(HInvoke* invoke) {
  codegen_->InvokeRuntime(kQuickSinh, invoke, invoke->GetDexPc());
}

void IntrinsicLocationsBuilderRISCV64::VisitMathTan(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitMathTan(HInvoke* invoke) {
  codegen_->InvokeRuntime(kQuickTan, invoke, invoke->GetDexPc());
}

void IntrinsicLocationsBuilderRISCV64::VisitMathTanh(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitMathTanh(HInvoke* invoke) {
  codegen_->InvokeRuntime(kQuickTanh, invoke, invoke->GetDexPc());
}

void IntrinsicLocationsBuilderRISCV64::VisitMathSqrt(HInvoke* invoke) {
  CreateFPToFPLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitMathSqrt(HInvoke* invoke) {
  DCHECK_EQ(invoke->InputAt(0)->GetType(), DataType::Type::kFloat64);
  DCHECK_EQ(invoke->GetType(), DataType::Type::kFloat64);

  LocationSummary* locations = invoke->GetLocations();
  Riscv64Assembler* assembler = GetAssembler();
  FRegister in = locations->InAt(0).AsFpuRegister<FRegister>();
  FRegister out = locations->Out().AsFpuRegister<FRegister>();

  __ FSqrtD(out, in);
}

static void GenDoubleRound(Riscv64Assembler* assembler, HInvoke* invoke, FPRoundingMode mode) {
  LocationSummary* locations = invoke->GetLocations();
  FRegister in = locations->InAt(0).AsFpuRegister<FRegister>();
  FRegister out = locations->Out().AsFpuRegister<FRegister>();
  ScratchRegisterScope srs(assembler);
  XRegister tmp = srs.AllocateXRegister();
  FRegister ftmp = srs.AllocateFRegister();
  Riscv64Label done;

  // Load 2^52
  __ LoadConst64(tmp, 0x4330000000000000L);
  __ FMvDX(ftmp, tmp);
  __ FAbsD(out, in);
  __ FLtD(tmp, out, ftmp);

  // Set output as the input if input greater than the max
  __ FMvD(out, in);
  __ Beqz(tmp, &done);

  // Convert with rounding mode
  __ FCvtLD(tmp, in, mode);
  __ FCvtDL(ftmp, tmp, mode);

  // Set the signed bit
  __ FSgnjD(out, ftmp, in);
  __ Bind(&done);
}

void IntrinsicLocationsBuilderRISCV64::VisitMathFloor(HInvoke* invoke) {
  CreateFPToFPLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitMathFloor(HInvoke* invoke) {
  GenDoubleRound(GetAssembler(), invoke, FPRoundingMode::kRDN);
}

void IntrinsicLocationsBuilderRISCV64::VisitMathCeil(HInvoke* invoke) {
  CreateFPToFPLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitMathCeil(HInvoke* invoke) {
  GenDoubleRound(GetAssembler(), invoke, FPRoundingMode::kRUP);
}

void IntrinsicLocationsBuilderRISCV64::VisitMathRint(HInvoke* invoke) {
  CreateFPToFPLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitMathRint(HInvoke* invoke) {
  GenDoubleRound(GetAssembler(), invoke, FPRoundingMode::kRNE);
}

void GenMathRound(CodeGeneratorRISCV64* codegen, HInvoke* invoke, DataType::Type type) {
  Riscv64Assembler* assembler = codegen->GetAssembler();
  LocationSummary* locations = invoke->GetLocations();
  FRegister in = locations->InAt(0).AsFpuRegister<FRegister>();
  XRegister out = locations->Out().AsRegister<XRegister>();
  ScratchRegisterScope srs(assembler);
  FRegister ftmp = srs.AllocateFRegister();
  Riscv64Label done;

  // Check NaN
  codegen->GetInstructionVisitor()->FClass(out, in, type);
  __ Slti(out, out, kFClassNaNMinValue);
  __ Beqz(out, &done);

  if (type == DataType::Type::kFloat64) {
    // Add 0.5 (0x3fe0000000000000), rounding down (towards negative infinity).
    __ LoadConst64(out, 0x3fe0000000000000L);
    __ FMvDX(ftmp, out);
    __ FAddD(ftmp, ftmp, in, FPRoundingMode::kRDN);

    // Convert to managed `long`, rounding down (towards negative infinity).
    __ FCvtLD(out, ftmp, FPRoundingMode::kRDN);
  } else {
    // Add 0.5 (0x3f000000), rounding down (towards negative infinity).
    __ LoadConst32(out, 0x3f000000);
    __ FMvWX(ftmp, out);
    __ FAddS(ftmp, ftmp, in, FPRoundingMode::kRDN);

    // Convert to managed `int`, rounding down (towards negative infinity).
    __ FCvtWS(out, ftmp, FPRoundingMode::kRDN);
  }

  __ Bind(&done);
}

void IntrinsicLocationsBuilderRISCV64::VisitMathRoundDouble(HInvoke* invoke) {
  CreateFPToIntLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitMathRoundDouble(HInvoke* invoke) {
  GenMathRound(codegen_, invoke, DataType::Type::kFloat64);
}

void IntrinsicLocationsBuilderRISCV64::VisitMathRoundFloat(HInvoke* invoke) {
  CreateFPToIntLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorRISCV64::VisitMathRoundFloat(HInvoke* invoke) {
  GenMathRound(codegen_, invoke, DataType::Type::kFloat32);
}

void IntrinsicLocationsBuilderRISCV64::VisitMathMultiplyHigh(HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator_) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::RequiresRegister());
  locations->SetInAt(1, Location::RequiresRegister());
  locations->SetOut(Location::RequiresRegister());
}

void IntrinsicCodeGeneratorRISCV64::VisitMathMultiplyHigh(HInvoke* invoke) {
  LocationSummary* locations = invoke->GetLocations();
  Riscv64Assembler* assembler = GetAssembler();
  DCHECK(invoke->GetType() == DataType::Type::kInt64);

  XRegister x = locations->InAt(0).AsRegister<XRegister>();
  XRegister y = locations->InAt(1).AsRegister<XRegister>();
  XRegister out = locations->Out().AsRegister<XRegister>();

  // Get high 64 of the multiply
  __ Mulh(out, x, y);
}

#define MARK_UNIMPLEMENTED(Name) UNIMPLEMENTED_INTRINSIC(RISCV64, Name)
UNIMPLEMENTED_INTRINSIC_LIST_RISCV64(MARK_UNIMPLEMENTED);
#undef MARK_UNIMPLEMENTED

UNREACHABLE_INTRINSICS(RISCV64)

}  // namespace riscv64
}  // namespace art
