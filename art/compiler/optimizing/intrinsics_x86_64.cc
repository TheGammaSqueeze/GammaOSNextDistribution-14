/*
 * Copyright (C) 2015 The Android Open Source Project
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

#include "intrinsics_x86_64.h"

#include <limits>

#include "arch/x86_64/instruction_set_features_x86_64.h"
#include "art_method.h"
#include "base/bit_utils.h"
#include "code_generator_x86_64.h"
#include "entrypoints/quick/quick_entrypoints.h"
#include "heap_poisoning.h"
#include "intrinsics.h"
#include "intrinsic_objects.h"
#include "intrinsics_utils.h"
#include "lock_word.h"
#include "mirror/array-inl.h"
#include "mirror/object_array-inl.h"
#include "mirror/reference.h"
#include "mirror/string.h"
#include "scoped_thread_state_change-inl.h"
#include "thread-current-inl.h"
#include "utils/x86_64/assembler_x86_64.h"
#include "utils/x86_64/constants_x86_64.h"
#include "well_known_classes.h"

namespace art HIDDEN {

namespace x86_64 {

IntrinsicLocationsBuilderX86_64::IntrinsicLocationsBuilderX86_64(CodeGeneratorX86_64* codegen)
  : allocator_(codegen->GetGraph()->GetAllocator()), codegen_(codegen) {
}

X86_64Assembler* IntrinsicCodeGeneratorX86_64::GetAssembler() {
  return down_cast<X86_64Assembler*>(codegen_->GetAssembler());
}

ArenaAllocator* IntrinsicCodeGeneratorX86_64::GetAllocator() {
  return codegen_->GetGraph()->GetAllocator();
}

bool IntrinsicLocationsBuilderX86_64::TryDispatch(HInvoke* invoke) {
  Dispatch(invoke);
  LocationSummary* res = invoke->GetLocations();
  if (res == nullptr) {
    return false;
  }
  return res->Intrinsified();
}

using IntrinsicSlowPathX86_64 = IntrinsicSlowPath<InvokeDexCallingConventionVisitorX86_64>;

#define __ assembler->

static void GenArrayAddress(X86_64Assembler* assembler,
                            CpuRegister dest,
                            CpuRegister base,
                            Location pos,
                            DataType::Type type,
                            uint32_t data_offset) {
  // Note: The heap is in low 4GiB, so we're using LEAL rather than LEAQ to save on code size.
  if (pos.IsConstant()) {
    int32_t constant = pos.GetConstant()->AsIntConstant()->GetValue();
    __ leal(dest, Address(base, DataType::Size(type) * constant + data_offset));
  } else {
    const ScaleFactor scale_factor = static_cast<ScaleFactor>(DataType::SizeShift(type));
    __ leal(dest, Address(base, pos.AsRegister<CpuRegister>(), scale_factor, data_offset));
  }
}

// Slow path implementing the SystemArrayCopy intrinsic copy loop with read barriers.
class ReadBarrierSystemArrayCopySlowPathX86_64 : public SlowPathCode {
 public:
  explicit ReadBarrierSystemArrayCopySlowPathX86_64(HInstruction* instruction)
      : SlowPathCode(instruction) {
  }

  void EmitNativeCode(CodeGenerator* codegen) override {
    DCHECK(codegen->EmitBakerReadBarrier());
    CodeGeneratorX86_64* x86_64_codegen = down_cast<CodeGeneratorX86_64*>(codegen);
    X86_64Assembler* assembler = x86_64_codegen->GetAssembler();
    LocationSummary* locations = instruction_->GetLocations();
    DCHECK(locations->CanCall());
    DCHECK(instruction_->IsInvokeStaticOrDirect())
        << "Unexpected instruction in read barrier arraycopy slow path: "
        << instruction_->DebugName();
    DCHECK(instruction_->GetLocations()->Intrinsified());
    DCHECK_EQ(instruction_->AsInvoke()->GetIntrinsic(), Intrinsics::kSystemArrayCopy);
    Location length = locations->InAt(4);

    const DataType::Type type = DataType::Type::kReference;
    const int32_t element_size = DataType::Size(type);

    CpuRegister src_curr_addr = locations->GetTemp(0).AsRegister<CpuRegister>();
    CpuRegister dst_curr_addr = locations->GetTemp(1).AsRegister<CpuRegister>();
    CpuRegister src_stop_addr = locations->GetTemp(2).AsRegister<CpuRegister>();

    __ Bind(GetEntryLabel());
    // The `src_curr_addr` and `dst_curr_addr` were initialized before entering the slow-path.
    GenArrayAddress(assembler, src_stop_addr, src_curr_addr, length, type, /*data_offset=*/ 0u);

    NearLabel loop;
    __ Bind(&loop);
    __ movl(CpuRegister(TMP), Address(src_curr_addr, 0));
    __ MaybeUnpoisonHeapReference(CpuRegister(TMP));
    // TODO: Inline the mark bit check before calling the runtime?
    // TMP = ReadBarrier::Mark(TMP);
    // No need to save live registers; it's taken care of by the
    // entrypoint. Also, there is no need to update the stack mask,
    // as this runtime call will not trigger a garbage collection.
    int32_t entry_point_offset = Thread::ReadBarrierMarkEntryPointsOffset<kX86_64PointerSize>(TMP);
    // This runtime call does not require a stack map.
    x86_64_codegen->InvokeRuntimeWithoutRecordingPcInfo(entry_point_offset, instruction_, this);
    __ MaybePoisonHeapReference(CpuRegister(TMP));
    __ movl(Address(dst_curr_addr, 0), CpuRegister(TMP));
    __ addl(src_curr_addr, Immediate(element_size));
    __ addl(dst_curr_addr, Immediate(element_size));
    __ cmpl(src_curr_addr, src_stop_addr);
    __ j(kNotEqual, &loop);
    __ jmp(GetExitLabel());
  }

  const char* GetDescription() const override { return "ReadBarrierSystemArrayCopySlowPathX86_64"; }

 private:
  DISALLOW_COPY_AND_ASSIGN(ReadBarrierSystemArrayCopySlowPathX86_64);
};

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

static void MoveFPToInt(LocationSummary* locations, bool is64bit, X86_64Assembler* assembler) {
  Location input = locations->InAt(0);
  Location output = locations->Out();
  __ movd(output.AsRegister<CpuRegister>(), input.AsFpuRegister<XmmRegister>(), is64bit);
}

static void MoveIntToFP(LocationSummary* locations, bool is64bit, X86_64Assembler* assembler) {
  Location input = locations->InAt(0);
  Location output = locations->Out();
  __ movd(output.AsFpuRegister<XmmRegister>(), input.AsRegister<CpuRegister>(), is64bit);
}

void IntrinsicLocationsBuilderX86_64::VisitDoubleDoubleToRawLongBits(HInvoke* invoke) {
  CreateFPToIntLocations(allocator_, invoke);
}
void IntrinsicLocationsBuilderX86_64::VisitDoubleLongBitsToDouble(HInvoke* invoke) {
  CreateIntToFPLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitDoubleDoubleToRawLongBits(HInvoke* invoke) {
  MoveFPToInt(invoke->GetLocations(), /* is64bit= */ true, GetAssembler());
}
void IntrinsicCodeGeneratorX86_64::VisitDoubleLongBitsToDouble(HInvoke* invoke) {
  MoveIntToFP(invoke->GetLocations(), /* is64bit= */ true, GetAssembler());
}

void IntrinsicLocationsBuilderX86_64::VisitFloatFloatToRawIntBits(HInvoke* invoke) {
  CreateFPToIntLocations(allocator_, invoke);
}
void IntrinsicLocationsBuilderX86_64::VisitFloatIntBitsToFloat(HInvoke* invoke) {
  CreateIntToFPLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitFloatFloatToRawIntBits(HInvoke* invoke) {
  MoveFPToInt(invoke->GetLocations(), /* is64bit= */ false, GetAssembler());
}
void IntrinsicCodeGeneratorX86_64::VisitFloatIntBitsToFloat(HInvoke* invoke) {
  MoveIntToFP(invoke->GetLocations(), /* is64bit= */ false, GetAssembler());
}

static void CreateIntToIntLocations(ArenaAllocator* allocator, HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::RequiresRegister());
  locations->SetOut(Location::SameAsFirstInput());
}

void IntrinsicLocationsBuilderX86_64::VisitIntegerReverseBytes(HInvoke* invoke) {
  CreateIntToIntLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitIntegerReverseBytes(HInvoke* invoke) {
  codegen_->GetInstructionCodegen()->Bswap(invoke->GetLocations()->Out(), DataType::Type::kInt32);
}

void IntrinsicLocationsBuilderX86_64::VisitLongReverseBytes(HInvoke* invoke) {
  CreateIntToIntLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitLongReverseBytes(HInvoke* invoke) {
  codegen_->GetInstructionCodegen()->Bswap(invoke->GetLocations()->Out(), DataType::Type::kInt64);
}

void IntrinsicLocationsBuilderX86_64::VisitShortReverseBytes(HInvoke* invoke) {
  CreateIntToIntLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitShortReverseBytes(HInvoke* invoke) {
  codegen_->GetInstructionCodegen()->Bswap(invoke->GetLocations()->Out(), DataType::Type::kInt16);
}

static void GenIsInfinite(LocationSummary* locations,
                          bool is64bit,
                          CodeGeneratorX86_64* codegen) {
  X86_64Assembler* assembler = codegen->GetAssembler();

  XmmRegister input = locations->InAt(0).AsFpuRegister<XmmRegister>();
  CpuRegister output = locations->Out().AsRegister<CpuRegister>();

  NearLabel done1, done2;

  if (is64bit) {
    double kPositiveInfinity = std::numeric_limits<double>::infinity();
    double kNegativeInfinity = -1 * kPositiveInfinity;

    __ xorq(output, output);
    __ comisd(input, codegen->LiteralDoubleAddress(kPositiveInfinity));
    __ j(kNotEqual, &done1);
    __ j(kParityEven, &done2);
    __ movq(output, Immediate(1));
    __ jmp(&done2);
    __ Bind(&done1);
    __ comisd(input, codegen->LiteralDoubleAddress(kNegativeInfinity));
    __ j(kNotEqual, &done2);
    __ j(kParityEven, &done2);
    __ movq(output, Immediate(1));
    __ Bind(&done2);
  } else {
    float kPositiveInfinity = std::numeric_limits<float>::infinity();
    float kNegativeInfinity = -1 * kPositiveInfinity;

    __ xorl(output, output);
    __ comiss(input, codegen->LiteralFloatAddress(kPositiveInfinity));
    __ j(kNotEqual, &done1);
    __ j(kParityEven, &done2);
    __ movl(output, Immediate(1));
    __ jmp(&done2);
    __ Bind(&done1);
    __ comiss(input, codegen->LiteralFloatAddress(kNegativeInfinity));
    __ j(kNotEqual, &done2);
    __ j(kParityEven, &done2);
    __ movl(output, Immediate(1));
    __ Bind(&done2);
  }
}

void IntrinsicLocationsBuilderX86_64::VisitFloatIsInfinite(HInvoke* invoke) {
  CreateFPToIntLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitFloatIsInfinite(HInvoke* invoke) {
  GenIsInfinite(invoke->GetLocations(), /* is64bit=*/  false, codegen_);
}

void IntrinsicLocationsBuilderX86_64::VisitDoubleIsInfinite(HInvoke* invoke) {
  CreateFPToIntLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitDoubleIsInfinite(HInvoke* invoke) {
  GenIsInfinite(invoke->GetLocations(), /* is64bit=*/  true, codegen_);
}

static void CreateFPToFPLocations(ArenaAllocator* allocator, HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::RequiresFpuRegister());
  locations->SetOut(Location::RequiresFpuRegister());
}

void IntrinsicLocationsBuilderX86_64::VisitMathSqrt(HInvoke* invoke) {
  CreateFPToFPLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitMathSqrt(HInvoke* invoke) {
  LocationSummary* locations = invoke->GetLocations();
  XmmRegister in = locations->InAt(0).AsFpuRegister<XmmRegister>();
  XmmRegister out = locations->Out().AsFpuRegister<XmmRegister>();

  GetAssembler()->sqrtsd(out, in);
}

static void CreateSSE41FPToFPLocations(ArenaAllocator* allocator,
                                       HInvoke* invoke,
                                       CodeGeneratorX86_64* codegen) {
  // Do we have instruction support?
  if (!codegen->GetInstructionSetFeatures().HasSSE4_1()) {
    return;
  }

  CreateFPToFPLocations(allocator, invoke);
}

static void GenSSE41FPToFPIntrinsic(HInvoke* invoke, X86_64Assembler* assembler, int round_mode) {
  LocationSummary* locations = invoke->GetLocations();
  DCHECK(!locations->WillCall());
  XmmRegister in = locations->InAt(0).AsFpuRegister<XmmRegister>();
  XmmRegister out = locations->Out().AsFpuRegister<XmmRegister>();
  __ roundsd(out, in, Immediate(round_mode));
}

void IntrinsicLocationsBuilderX86_64::VisitMathCeil(HInvoke* invoke) {
  CreateSSE41FPToFPLocations(allocator_, invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitMathCeil(HInvoke* invoke) {
  GenSSE41FPToFPIntrinsic(invoke, GetAssembler(), 2);
}

void IntrinsicLocationsBuilderX86_64::VisitMathFloor(HInvoke* invoke) {
  CreateSSE41FPToFPLocations(allocator_, invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitMathFloor(HInvoke* invoke) {
  GenSSE41FPToFPIntrinsic(invoke, GetAssembler(), 1);
}

void IntrinsicLocationsBuilderX86_64::VisitMathRint(HInvoke* invoke) {
  CreateSSE41FPToFPLocations(allocator_, invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitMathRint(HInvoke* invoke) {
  GenSSE41FPToFPIntrinsic(invoke, GetAssembler(), 0);
}

static void CreateSSE41FPToIntLocations(ArenaAllocator* allocator,
                                        HInvoke* invoke,
                                        CodeGeneratorX86_64* codegen) {
  // Do we have instruction support?
  if (!codegen->GetInstructionSetFeatures().HasSSE4_1()) {
    return;
  }

  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::RequiresFpuRegister());
  locations->SetOut(Location::RequiresRegister());
  locations->AddTemp(Location::RequiresFpuRegister());
  locations->AddTemp(Location::RequiresFpuRegister());
}

void IntrinsicLocationsBuilderX86_64::VisitMathRoundFloat(HInvoke* invoke) {
  CreateSSE41FPToIntLocations(allocator_, invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitMathRoundFloat(HInvoke* invoke) {
  LocationSummary* locations = invoke->GetLocations();
  DCHECK(!locations->WillCall());

  XmmRegister in = locations->InAt(0).AsFpuRegister<XmmRegister>();
  CpuRegister out = locations->Out().AsRegister<CpuRegister>();
  XmmRegister t1 = locations->GetTemp(0).AsFpuRegister<XmmRegister>();
  XmmRegister t2 = locations->GetTemp(1).AsFpuRegister<XmmRegister>();
  NearLabel skip_incr, done;
  X86_64Assembler* assembler = GetAssembler();

  // Since no direct x86 rounding instruction matches the required semantics,
  // this intrinsic is implemented as follows:
  //  result = floor(in);
  //  if (in - result >= 0.5f)
  //    result = result + 1.0f;
  __ movss(t2, in);
  __ roundss(t1, in, Immediate(1));
  __ subss(t2, t1);
  __ comiss(t2, codegen_->LiteralFloatAddress(0.5f));
  __ j(kBelow, &skip_incr);
  __ addss(t1, codegen_->LiteralFloatAddress(1.0f));
  __ Bind(&skip_incr);

  // Final conversion to an integer. Unfortunately this also does not have a
  // direct x86 instruction, since NaN should map to 0 and large positive
  // values need to be clipped to the extreme value.
  codegen_->Load32BitValue(out, kPrimIntMax);
  __ cvtsi2ss(t2, out);
  __ comiss(t1, t2);
  __ j(kAboveEqual, &done);  // clipped to max (already in out), does not jump on unordered
  __ movl(out, Immediate(0));  // does not change flags
  __ j(kUnordered, &done);  // NaN mapped to 0 (just moved in out)
  __ cvttss2si(out, t1);
  __ Bind(&done);
}

void IntrinsicLocationsBuilderX86_64::VisitMathRoundDouble(HInvoke* invoke) {
  CreateSSE41FPToIntLocations(allocator_, invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitMathRoundDouble(HInvoke* invoke) {
  LocationSummary* locations = invoke->GetLocations();
  DCHECK(!locations->WillCall());

  XmmRegister in = locations->InAt(0).AsFpuRegister<XmmRegister>();
  CpuRegister out = locations->Out().AsRegister<CpuRegister>();
  XmmRegister t1 = locations->GetTemp(0).AsFpuRegister<XmmRegister>();
  XmmRegister t2 = locations->GetTemp(1).AsFpuRegister<XmmRegister>();
  NearLabel skip_incr, done;
  X86_64Assembler* assembler = GetAssembler();

  // Since no direct x86 rounding instruction matches the required semantics,
  // this intrinsic is implemented as follows:
  //  result = floor(in);
  //  if (in - result >= 0.5)
  //    result = result + 1.0f;
  __ movsd(t2, in);
  __ roundsd(t1, in, Immediate(1));
  __ subsd(t2, t1);
  __ comisd(t2, codegen_->LiteralDoubleAddress(0.5));
  __ j(kBelow, &skip_incr);
  __ addsd(t1, codegen_->LiteralDoubleAddress(1.0f));
  __ Bind(&skip_incr);

  // Final conversion to an integer. Unfortunately this also does not have a
  // direct x86 instruction, since NaN should map to 0 and large positive
  // values need to be clipped to the extreme value.
  codegen_->Load64BitValue(out, kPrimLongMax);
  __ cvtsi2sd(t2, out, /* is64bit= */ true);
  __ comisd(t1, t2);
  __ j(kAboveEqual, &done);  // clipped to max (already in out), does not jump on unordered
  __ movl(out, Immediate(0));  // does not change flags, implicit zero extension to 64-bit
  __ j(kUnordered, &done);  // NaN mapped to 0 (just moved in out)
  __ cvttsd2si(out, t1, /* is64bit= */ true);
  __ Bind(&done);
}

static void CreateFPToFPCallLocations(ArenaAllocator* allocator, HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kCallOnMainOnly, kIntrinsified);
  InvokeRuntimeCallingConvention calling_convention;
  locations->SetInAt(0, Location::FpuRegisterLocation(calling_convention.GetFpuRegisterAt(0)));
  locations->SetOut(Location::FpuRegisterLocation(XMM0));

  CodeGeneratorX86_64::BlockNonVolatileXmmRegisters(locations);
}

static void GenFPToFPCall(HInvoke* invoke, CodeGeneratorX86_64* codegen,
                          QuickEntrypointEnum entry) {
  LocationSummary* locations = invoke->GetLocations();
  DCHECK(locations->WillCall());
  DCHECK(invoke->IsInvokeStaticOrDirect());

  codegen->InvokeRuntime(entry, invoke, invoke->GetDexPc());
}

void IntrinsicLocationsBuilderX86_64::VisitMathCos(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitMathCos(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickCos);
}

void IntrinsicLocationsBuilderX86_64::VisitMathSin(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitMathSin(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickSin);
}

void IntrinsicLocationsBuilderX86_64::VisitMathAcos(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitMathAcos(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickAcos);
}

void IntrinsicLocationsBuilderX86_64::VisitMathAsin(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitMathAsin(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickAsin);
}

void IntrinsicLocationsBuilderX86_64::VisitMathAtan(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitMathAtan(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickAtan);
}

void IntrinsicLocationsBuilderX86_64::VisitMathCbrt(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitMathCbrt(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickCbrt);
}

void IntrinsicLocationsBuilderX86_64::VisitMathCosh(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitMathCosh(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickCosh);
}

void IntrinsicLocationsBuilderX86_64::VisitMathExp(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitMathExp(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickExp);
}

void IntrinsicLocationsBuilderX86_64::VisitMathExpm1(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitMathExpm1(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickExpm1);
}

void IntrinsicLocationsBuilderX86_64::VisitMathLog(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitMathLog(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickLog);
}

void IntrinsicLocationsBuilderX86_64::VisitMathLog10(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitMathLog10(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickLog10);
}

void IntrinsicLocationsBuilderX86_64::VisitMathSinh(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitMathSinh(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickSinh);
}

void IntrinsicLocationsBuilderX86_64::VisitMathTan(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitMathTan(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickTan);
}

void IntrinsicLocationsBuilderX86_64::VisitMathTanh(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitMathTanh(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickTanh);
}

static void CreateFPFPToFPCallLocations(ArenaAllocator* allocator, HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kCallOnMainOnly, kIntrinsified);
  InvokeRuntimeCallingConvention calling_convention;
  locations->SetInAt(0, Location::FpuRegisterLocation(calling_convention.GetFpuRegisterAt(0)));
  locations->SetInAt(1, Location::FpuRegisterLocation(calling_convention.GetFpuRegisterAt(1)));
  locations->SetOut(Location::FpuRegisterLocation(XMM0));

  CodeGeneratorX86_64::BlockNonVolatileXmmRegisters(locations);
}

static void CreateFPFPFPToFPCallLocations(ArenaAllocator* allocator, HInvoke* invoke) {
  DCHECK_EQ(invoke->GetNumberOfArguments(), 3U);
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  InvokeRuntimeCallingConvention calling_convention;
  locations->SetInAt(0, Location::RequiresFpuRegister());
  locations->SetInAt(1, Location::RequiresFpuRegister());
  locations->SetInAt(2, Location::RequiresFpuRegister());
  locations->SetOut(Location::SameAsFirstInput());
}

void IntrinsicLocationsBuilderX86_64::VisitMathAtan2(HInvoke* invoke) {
  CreateFPFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitMathAtan2(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickAtan2);
}

void IntrinsicLocationsBuilderX86_64::VisitMathPow(HInvoke* invoke) {
  CreateFPFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitMathPow(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickPow);
}

void IntrinsicLocationsBuilderX86_64::VisitMathHypot(HInvoke* invoke) {
  CreateFPFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitMathHypot(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickHypot);
}

void IntrinsicLocationsBuilderX86_64::VisitMathNextAfter(HInvoke* invoke) {
  CreateFPFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitMathNextAfter(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickNextAfter);
}

static void CreateSystemArrayCopyLocations(HInvoke* invoke) {
  // Check to see if we have known failures that will cause us to have to bail out
  // to the runtime, and just generate the runtime call directly.
  HIntConstant* src_pos = invoke->InputAt(1)->AsIntConstantOrNull();
  HIntConstant* dest_pos = invoke->InputAt(3)->AsIntConstantOrNull();

  // The positions must be non-negative.
  if ((src_pos != nullptr && src_pos->GetValue() < 0) ||
      (dest_pos != nullptr && dest_pos->GetValue() < 0)) {
    // We will have to fail anyways.
    return;
  }

  // The length must be > 0.
  HIntConstant* length = invoke->InputAt(4)->AsIntConstantOrNull();
  if (length != nullptr) {
    int32_t len = length->GetValue();
    if (len < 0) {
      // Just call as normal.
      return;
    }
  }
  LocationSummary* locations =
      new (invoke->GetBlock()->GetGraph()->GetAllocator()) LocationSummary
      (invoke, LocationSummary::kCallOnSlowPath, kIntrinsified);
  // arraycopy(Object src, int src_pos, Object dest, int dest_pos, int length).
  locations->SetInAt(0, Location::RequiresRegister());
  locations->SetInAt(1, Location::RegisterOrConstant(invoke->InputAt(1)));
  locations->SetInAt(2, Location::RequiresRegister());
  locations->SetInAt(3, Location::RegisterOrConstant(invoke->InputAt(3)));
  locations->SetInAt(4, Location::RegisterOrConstant(invoke->InputAt(4)));

  // And we need some temporaries.  We will use REP MOVS{B,W,L}, so we need fixed registers.
  locations->AddTemp(Location::RegisterLocation(RSI));
  locations->AddTemp(Location::RegisterLocation(RDI));
  locations->AddTemp(Location::RegisterLocation(RCX));
}

template <typename LhsType>
static void EmitCmplJLess(X86_64Assembler* assembler,
                          LhsType lhs,
                          Location rhs,
                          Label* label) {
  static_assert(std::is_same_v<LhsType, CpuRegister> || std::is_same_v<LhsType, Address>);
  if (rhs.IsConstant()) {
    int32_t rhs_constant = rhs.GetConstant()->AsIntConstant()->GetValue();
    __ cmpl(lhs, Immediate(rhs_constant));
  } else {
    __ cmpl(lhs, rhs.AsRegister<CpuRegister>());
  }
  __ j(kLess, label);
}

static void CheckSystemArrayCopyPosition(X86_64Assembler* assembler,
                                         CpuRegister array,
                                         Location pos,
                                         Location length,
                                         SlowPathCode* slow_path,
                                         CpuRegister temp,
                                         bool length_is_array_length,
                                         bool position_sign_checked) {
  // Where is the length in the Array?
  const uint32_t length_offset = mirror::Array::LengthOffset().Uint32Value();

  if (pos.IsConstant()) {
    int32_t pos_const = pos.GetConstant()->AsIntConstant()->GetValue();
    if (pos_const == 0) {
      if (!length_is_array_length) {
        // Check that length(array) >= length.
        EmitCmplJLess(assembler, Address(array, length_offset), length, slow_path->GetEntryLabel());
      }
    } else {
      // Calculate length(array) - pos.
      // Both operands are known to be non-negative `int32_t`, so the difference cannot underflow
      // as `int32_t`. If the result is negative, the JL below shall go to the slow path.
      __ movl(temp, Address(array, length_offset));
      __ subl(temp, Immediate(pos_const));

      // Check that (length(array) - pos) >= length.
      EmitCmplJLess(assembler, temp, length, slow_path->GetEntryLabel());
    }
  } else if (length_is_array_length) {
    // The only way the copy can succeed is if pos is zero.
    CpuRegister pos_reg = pos.AsRegister<CpuRegister>();
    __ testl(pos_reg, pos_reg);
    __ j(kNotEqual, slow_path->GetEntryLabel());
  } else {
    // Check that pos >= 0.
    CpuRegister pos_reg = pos.AsRegister<CpuRegister>();
    if (!position_sign_checked) {
      __ testl(pos_reg, pos_reg);
      __ j(kLess, slow_path->GetEntryLabel());
    }

    // Calculate length(array) - pos.
    // Both operands are known to be non-negative `int32_t`, so the difference cannot underflow
    // as `int32_t`. If the result is negative, the JL below shall go to the slow path.
    __ movl(temp, Address(array, length_offset));
    __ subl(temp, pos_reg);

    // Check that (length(array) - pos) >= length.
    EmitCmplJLess(assembler, temp, length, slow_path->GetEntryLabel());
  }
}

static void SystemArrayCopyPrimitive(HInvoke* invoke,
                                     X86_64Assembler* assembler,
                                     CodeGeneratorX86_64* codegen,
                                     DataType::Type type) {
  LocationSummary* locations = invoke->GetLocations();
  CpuRegister src = locations->InAt(0).AsRegister<CpuRegister>();
  Location src_pos = locations->InAt(1);
  CpuRegister dest = locations->InAt(2).AsRegister<CpuRegister>();
  Location dest_pos = locations->InAt(3);
  Location length = locations->InAt(4);

  // Temporaries that we need for MOVSB/W/L.
  CpuRegister src_base = locations->GetTemp(0).AsRegister<CpuRegister>();
  DCHECK_EQ(src_base.AsRegister(), RSI);
  CpuRegister dest_base = locations->GetTemp(1).AsRegister<CpuRegister>();
  DCHECK_EQ(dest_base.AsRegister(), RDI);
  CpuRegister count = locations->GetTemp(2).AsRegister<CpuRegister>();
  DCHECK_EQ(count.AsRegister(), RCX);

  SlowPathCode* slow_path = new (codegen->GetScopedAllocator()) IntrinsicSlowPathX86_64(invoke);
  codegen->AddSlowPath(slow_path);

  // Bail out if the source and destination are the same.
  __ cmpl(src, dest);
  __ j(kEqual, slow_path->GetEntryLabel());

  // Bail out if the source is null.
  __ testl(src, src);
  __ j(kEqual, slow_path->GetEntryLabel());

  // Bail out if the destination is null.
  __ testl(dest, dest);
  __ j(kEqual, slow_path->GetEntryLabel());

  // If the length is negative, bail out.
  // We have already checked in the LocationsBuilder for the constant case.
  if (!length.IsConstant()) {
    __ testl(length.AsRegister<CpuRegister>(), length.AsRegister<CpuRegister>());
    __ j(kLess, slow_path->GetEntryLabel());
  }

  // Validity checks: source. Use src_base as a temporary register.
  CheckSystemArrayCopyPosition(assembler,
                               src,
                               src_pos,
                               length,
                               slow_path,
                               src_base,
                               /*length_is_array_length=*/ false,
                               /*position_sign_checked=*/ false);

  // Validity checks: dest. Use src_base as a temporary register.
  CheckSystemArrayCopyPosition(assembler,
                               dest,
                               dest_pos,
                               length,
                               slow_path,
                               src_base,
                               /*length_is_array_length=*/ false,
                               /*position_sign_checked=*/ false);

  // We need the count in RCX.
  if (length.IsConstant()) {
    __ movl(count, Immediate(length.GetConstant()->AsIntConstant()->GetValue()));
  } else {
    __ movl(count, length.AsRegister<CpuRegister>());
  }

  // Okay, everything checks out.  Finally time to do the copy.
  // Check assumption that sizeof(Char) is 2 (used in scaling below).
  const size_t data_size = DataType::Size(type);
  const uint32_t data_offset = mirror::Array::DataOffset(data_size).Uint32Value();

  GenArrayAddress(assembler, src_base, src, src_pos, type, data_offset);
  GenArrayAddress(assembler, dest_base, dest, dest_pos, type, data_offset);

  // Do the move.
  switch (type) {
    case DataType::Type::kInt8:
       __ rep_movsb();
       break;
    case DataType::Type::kUint16:
       __ rep_movsw();
       break;
    case DataType::Type::kInt32:
       __ rep_movsl();
       break;
    default:
       LOG(FATAL) << "Unexpected data type for intrinsic";
  }
  __ Bind(slow_path->GetExitLabel());
}

void IntrinsicLocationsBuilderX86_64::VisitSystemArrayCopyChar(HInvoke* invoke) {
  CreateSystemArrayCopyLocations(invoke);
}
void IntrinsicCodeGeneratorX86_64::VisitSystemArrayCopyChar(HInvoke* invoke) {
  X86_64Assembler* assembler = GetAssembler();
  SystemArrayCopyPrimitive(invoke, assembler, codegen_, DataType::Type::kUint16);
}

void IntrinsicCodeGeneratorX86_64::VisitSystemArrayCopyByte(HInvoke* invoke) {
  X86_64Assembler* assembler = GetAssembler();
  SystemArrayCopyPrimitive(invoke, assembler, codegen_, DataType::Type::kInt8);
}

void IntrinsicLocationsBuilderX86_64::VisitSystemArrayCopyByte(HInvoke* invoke) {
  CreateSystemArrayCopyLocations(invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitSystemArrayCopyInt(HInvoke* invoke) {
  X86_64Assembler* assembler = GetAssembler();
  SystemArrayCopyPrimitive(invoke, assembler, codegen_, DataType::Type::kInt32);
}

void IntrinsicLocationsBuilderX86_64::VisitSystemArrayCopyInt(HInvoke* invoke) {
  CreateSystemArrayCopyLocations(invoke);
}

void IntrinsicLocationsBuilderX86_64::VisitSystemArrayCopy(HInvoke* invoke) {
  // The only read barrier implementation supporting the
  // SystemArrayCopy intrinsic is the Baker-style read barriers.
  if (codegen_->EmitNonBakerReadBarrier()) {
    return;
  }

  constexpr int32_t kLengthThreshold = -1;  // No cut-off - handle large arrays in intrinsic code.
  constexpr size_t kInitialNumTemps = 0u;  // We shall allocate temps explicitly.
  LocationSummary* locations = CodeGenerator::CreateSystemArrayCopyLocationSummary(
      invoke, kLengthThreshold, kInitialNumTemps);
  if (locations != nullptr) {
    // Add temporaries.  We will use REP MOVSL, so we need fixed registers.
    DCHECK_EQ(locations->GetTempCount(), kInitialNumTemps);
    locations->AddTemp(Location::RegisterLocation(RSI));
    locations->AddTemp(Location::RegisterLocation(RDI));
    locations->AddTemp(Location::RegisterLocation(RCX));
  }
}

void IntrinsicCodeGeneratorX86_64::VisitSystemArrayCopy(HInvoke* invoke) {
  // The only read barrier implementation supporting the
  // SystemArrayCopy intrinsic is the Baker-style read barriers.
  DCHECK_IMPLIES(codegen_->EmitReadBarrier(), kUseBakerReadBarrier);

  X86_64Assembler* assembler = GetAssembler();
  LocationSummary* locations = invoke->GetLocations();

  uint32_t class_offset = mirror::Object::ClassOffset().Int32Value();
  uint32_t super_offset = mirror::Class::SuperClassOffset().Int32Value();
  uint32_t component_offset = mirror::Class::ComponentTypeOffset().Int32Value();
  uint32_t primitive_offset = mirror::Class::PrimitiveTypeOffset().Int32Value();
  uint32_t monitor_offset = mirror::Object::MonitorOffset().Int32Value();

  CpuRegister src = locations->InAt(0).AsRegister<CpuRegister>();
  Location src_pos = locations->InAt(1);
  CpuRegister dest = locations->InAt(2).AsRegister<CpuRegister>();
  Location dest_pos = locations->InAt(3);
  Location length = locations->InAt(4);
  Location temp1_loc = locations->GetTemp(0);
  CpuRegister temp1 = temp1_loc.AsRegister<CpuRegister>();
  Location temp2_loc = locations->GetTemp(1);
  CpuRegister temp2 = temp2_loc.AsRegister<CpuRegister>();
  Location temp3_loc = locations->GetTemp(2);
  CpuRegister temp3 = temp3_loc.AsRegister<CpuRegister>();

  SlowPathCode* intrinsic_slow_path =
      new (codegen_->GetScopedAllocator()) IntrinsicSlowPathX86_64(invoke);
  codegen_->AddSlowPath(intrinsic_slow_path);

  NearLabel conditions_on_positions_validated;
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
          __ cmpl(src, dest);
          __ j(kEqual, intrinsic_slow_path->GetEntryLabel());
        }
      } else {
        if (!optimizations.GetDestinationIsSource()) {
          __ cmpl(src, dest);
          __ j(kNotEqual, &conditions_on_positions_validated);
        }
        __ cmpl(dest_pos.AsRegister<CpuRegister>(), Immediate(src_pos_constant));
        __ j(kGreater, intrinsic_slow_path->GetEntryLabel());
      }
    } else {
      if (!optimizations.GetDestinationIsSource()) {
        __ cmpl(src, dest);
        __ j(kNotEqual, &conditions_on_positions_validated);
      }
      CpuRegister src_pos_reg = src_pos.AsRegister<CpuRegister>();
      EmitCmplJLess(assembler, src_pos_reg, dest_pos, intrinsic_slow_path->GetEntryLabel());
    }
  }

  __ Bind(&conditions_on_positions_validated);

  if (!optimizations.GetSourceIsNotNull()) {
    // Bail out if the source is null.
    __ testl(src, src);
    __ j(kEqual, intrinsic_slow_path->GetEntryLabel());
  }

  if (!optimizations.GetDestinationIsNotNull() && !optimizations.GetDestinationIsSource()) {
    // Bail out if the destination is null.
    __ testl(dest, dest);
    __ j(kEqual, intrinsic_slow_path->GetEntryLabel());
  }

  // If the length is negative, bail out.
  // We have already checked in the LocationsBuilder for the constant case.
  if (!length.IsConstant() &&
      !optimizations.GetCountIsSourceLength() &&
      !optimizations.GetCountIsDestinationLength()) {
    __ testl(length.AsRegister<CpuRegister>(), length.AsRegister<CpuRegister>());
    __ j(kLess, intrinsic_slow_path->GetEntryLabel());
  }

  // Validity checks: source.
  CheckSystemArrayCopyPosition(assembler,
                               src,
                               src_pos,
                               length,
                               intrinsic_slow_path,
                               temp1,
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
                               optimizations.GetCountIsDestinationLength(),
                               dest_position_sign_checked);

  auto check_non_primitive_array_class = [&](CpuRegister klass, CpuRegister temp) {
    // No read barrier is needed for reading a chain of constant references for comparing
    // with null, or for reading a constant primitive value, see `ReadBarrierOption`.
    // /* HeapReference<Class> */ temp = klass->component_type_
    __ movl(temp, Address(klass, component_offset));
    __ MaybeUnpoisonHeapReference(temp);
    // Check that the component type is not null.
    __ testl(temp, temp);
    __ j(kEqual, intrinsic_slow_path->GetEntryLabel());
    // Check that the component type is not a primitive.
    __ cmpw(Address(temp, primitive_offset), Immediate(Primitive::kPrimNot));
    __ j(kNotEqual, intrinsic_slow_path->GetEntryLabel());
  };

  if (!optimizations.GetDoesNotNeedTypeCheck()) {
    // Check whether all elements of the source array are assignable to the component
    // type of the destination array. We do two checks: the classes are the same,
    // or the destination is Object[]. If none of these checks succeed, we go to the
    // slow path.

    if (codegen_->EmitBakerReadBarrier()) {
      // /* HeapReference<Class> */ temp1 = dest->klass_
      codegen_->GenerateFieldLoadWithBakerReadBarrier(
          invoke, temp1_loc, dest, class_offset, /* needs_null_check= */ false);
      // Register `temp1` is not trashed by the read barrier emitted
      // by GenerateFieldLoadWithBakerReadBarrier below, as that
      // method produces a call to a ReadBarrierMarkRegX entry point,
      // which saves all potentially live registers, including
      // temporaries such a `temp1`.
      // /* HeapReference<Class> */ temp2 = src->klass_
      codegen_->GenerateFieldLoadWithBakerReadBarrier(
          invoke, temp2_loc, src, class_offset, /* needs_null_check= */ false);
      // If heap poisoning is enabled, `temp1` and `temp2` have been unpoisoned
      // by the previous calls to GenerateFieldLoadWithBakerReadBarrier.
    } else {
      // /* HeapReference<Class> */ temp1 = dest->klass_
      __ movl(temp1, Address(dest, class_offset));
      __ MaybeUnpoisonHeapReference(temp1);
      // /* HeapReference<Class> */ temp2 = src->klass_
      __ movl(temp2, Address(src, class_offset));
      __ MaybeUnpoisonHeapReference(temp2);
    }

    __ cmpl(temp1, temp2);
    if (optimizations.GetDestinationIsTypedObjectArray()) {
      DCHECK(optimizations.GetDestinationIsNonPrimitiveArray());
      NearLabel do_copy;
      // For class match, we can skip the source type check regardless of the optimization flag.
      __ j(kEqual, &do_copy);
      // No read barrier is needed for reading a chain of constant references
      // for comparing with null, see `ReadBarrierOption`.
      // /* HeapReference<Class> */ temp1 = temp1->component_type_
      __ movl(temp1, Address(temp1, component_offset));
      __ MaybeUnpoisonHeapReference(temp1);
      // No need to unpoison the following heap reference load, as
      // we're comparing against null.
      __ cmpl(Address(temp1, super_offset), Immediate(0));
      __ j(kNotEqual, intrinsic_slow_path->GetEntryLabel());
      // Bail out if the source is not a non primitive array.
      if (!optimizations.GetSourceIsNonPrimitiveArray()) {
        check_non_primitive_array_class(temp2, CpuRegister(TMP));
      }
      __ Bind(&do_copy);
    } else {
      DCHECK(!optimizations.GetDestinationIsTypedObjectArray());
      // For class match, we can skip the array type check completely if at least one of source
      // and destination is known to be a non primitive array, otherwise one check is enough.
      __ j(kNotEqual, intrinsic_slow_path->GetEntryLabel());
      if (!optimizations.GetDestinationIsNonPrimitiveArray() &&
          !optimizations.GetSourceIsNonPrimitiveArray()) {
        check_non_primitive_array_class(temp2, CpuRegister(TMP));
      }
    }
  } else if (!optimizations.GetSourceIsNonPrimitiveArray()) {
    DCHECK(optimizations.GetDestinationIsNonPrimitiveArray());
    // Bail out if the source is not a non primitive array.
    // No read barrier is needed for reading a chain of constant references for comparing
    // with null, or for reading a constant primitive value, see `ReadBarrierOption`.
    // /* HeapReference<Class> */ temp1 = src->klass_
    __ movl(temp1, Address(src, class_offset));
    __ MaybeUnpoisonHeapReference(temp1);
    check_non_primitive_array_class(temp1, CpuRegister(TMP));
  }

  if (length.IsConstant() && length.GetConstant()->AsIntConstant()->GetValue() == 0) {
    // Null constant length: not need to emit the loop code at all.
  } else {
    const DataType::Type type = DataType::Type::kReference;
    const int32_t element_size = DataType::Size(type);
    const uint32_t data_offset = mirror::Array::DataOffset(element_size).Uint32Value();

    // Don't enter copy loop if `length == 0`.
    NearLabel skip_copy_and_write_barrier;
    if (!length.IsConstant()) {
      __ testl(length.AsRegister<CpuRegister>(), length.AsRegister<CpuRegister>());
      __ j(kEqual, &skip_copy_and_write_barrier);
    }

    // Compute base source address, base destination address, and end
    // source address in `temp1`, `temp2` and `temp3` respectively.
    GenArrayAddress(assembler, temp1, src, src_pos, type, data_offset);
    GenArrayAddress(assembler, temp2, dest, dest_pos, type, data_offset);

    SlowPathCode* read_barrier_slow_path = nullptr;
    if (codegen_->EmitBakerReadBarrier()) {
      // SystemArrayCopy implementation for Baker read barriers (see
      // also CodeGeneratorX86_64::GenerateReferenceLoadWithBakerReadBarrier):
      //
      //   if (src_ptr != end_ptr) {
      //     uint32_t rb_state = Lockword(src->monitor_).ReadBarrierState();
      //     lfence;  // Load fence or artificial data dependency to prevent load-load reordering
      //     bool is_gray = (rb_state == ReadBarrier::GrayState());
      //     if (is_gray) {
      //       // Slow-path copy.
      //       do {
      //         *dest_ptr++ = MaybePoison(ReadBarrier::Mark(MaybeUnpoison(*src_ptr++)));
      //       } while (src_ptr != end_ptr)
      //     } else {
      //       // Fast-path copy.
      //       do {
      //         *dest_ptr++ = *src_ptr++;
      //       } while (src_ptr != end_ptr)
      //     }
      //   }

      // Given the numeric representation, it's enough to check the low bit of the rb_state.
      static_assert(ReadBarrier::NonGrayState() == 0, "Expecting non-gray to have value 0");
      static_assert(ReadBarrier::GrayState() == 1, "Expecting gray to have value 1");
      constexpr uint32_t gray_byte_position = LockWord::kReadBarrierStateShift / kBitsPerByte;
      constexpr uint32_t gray_bit_position = LockWord::kReadBarrierStateShift % kBitsPerByte;
      constexpr int32_t test_value = static_cast<int8_t>(1 << gray_bit_position);

      // if (rb_state == ReadBarrier::GrayState())
      //   goto slow_path;
      // At this point, just do the "if" and make sure that flags are preserved until the branch.
      __ testb(Address(src, monitor_offset + gray_byte_position), Immediate(test_value));

      // Load fence to prevent load-load reordering.
      // Note that this is a no-op, thanks to the x86-64 memory model.
      codegen_->GenerateMemoryBarrier(MemBarrierKind::kLoadAny);

      // Slow path used to copy array when `src` is gray.
      read_barrier_slow_path =
          new (codegen_->GetScopedAllocator()) ReadBarrierSystemArrayCopySlowPathX86_64(invoke);
      codegen_->AddSlowPath(read_barrier_slow_path);

      // We have done the "if" of the gray bit check above, now branch based on the flags.
      __ j(kNotZero, read_barrier_slow_path->GetEntryLabel());
    }

    if (length.IsConstant()) {
      __ movl(temp3, Immediate(length.GetConstant()->AsIntConstant()->GetValue()));
    } else {
      __ movl(temp3, length.AsRegister<CpuRegister>());
    }

    // Iterate over the arrays and do a raw copy of the objects. We don't need to poison/unpoison.
    DCHECK_EQ(temp1.AsRegister(), RSI);
    DCHECK_EQ(temp2.AsRegister(), RDI);
    DCHECK_EQ(temp3.AsRegister(), RCX);
    __ rep_movsl();

    if (read_barrier_slow_path != nullptr) {
      DCHECK(codegen_->EmitBakerReadBarrier());
      __ Bind(read_barrier_slow_path->GetExitLabel());
    }

    // We only need one card marking on the destination array.
    codegen_->MarkGCCard(temp1, temp2, dest);

    __ Bind(&skip_copy_and_write_barrier);
  }

  __ Bind(intrinsic_slow_path->GetExitLabel());
}

void IntrinsicLocationsBuilderX86_64::VisitStringCompareTo(HInvoke* invoke) {
  LocationSummary* locations = new (allocator_) LocationSummary(
      invoke, LocationSummary::kCallOnMainAndSlowPath, kIntrinsified);
  InvokeRuntimeCallingConvention calling_convention;
  locations->SetInAt(0, Location::RegisterLocation(calling_convention.GetRegisterAt(0)));
  locations->SetInAt(1, Location::RegisterLocation(calling_convention.GetRegisterAt(1)));
  locations->SetOut(Location::RegisterLocation(RAX));
}

void IntrinsicCodeGeneratorX86_64::VisitStringCompareTo(HInvoke* invoke) {
  X86_64Assembler* assembler = GetAssembler();
  LocationSummary* locations = invoke->GetLocations();

  // Note that the null check must have been done earlier.
  DCHECK(!invoke->CanDoImplicitNullCheckOn(invoke->InputAt(0)));

  CpuRegister argument = locations->InAt(1).AsRegister<CpuRegister>();
  __ testl(argument, argument);
  SlowPathCode* slow_path = new (codegen_->GetScopedAllocator()) IntrinsicSlowPathX86_64(invoke);
  codegen_->AddSlowPath(slow_path);
  __ j(kEqual, slow_path->GetEntryLabel());

  codegen_->InvokeRuntime(kQuickStringCompareTo, invoke, invoke->GetDexPc(), slow_path);
  __ Bind(slow_path->GetExitLabel());
}

void IntrinsicLocationsBuilderX86_64::VisitStringEquals(HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator_) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::RequiresRegister());
  locations->SetInAt(1, Location::RequiresRegister());

  // Request temporary registers, RCX and RDI needed for repe_cmpsq instruction.
  locations->AddTemp(Location::RegisterLocation(RCX));
  locations->AddTemp(Location::RegisterLocation(RDI));

  // Set output, RSI needed for repe_cmpsq instruction anyways.
  locations->SetOut(Location::RegisterLocation(RSI), Location::kOutputOverlap);
}

void IntrinsicCodeGeneratorX86_64::VisitStringEquals(HInvoke* invoke) {
  X86_64Assembler* assembler = GetAssembler();
  LocationSummary* locations = invoke->GetLocations();

  CpuRegister str = locations->InAt(0).AsRegister<CpuRegister>();
  CpuRegister arg = locations->InAt(1).AsRegister<CpuRegister>();
  CpuRegister rcx = locations->GetTemp(0).AsRegister<CpuRegister>();
  CpuRegister rdi = locations->GetTemp(1).AsRegister<CpuRegister>();
  CpuRegister rsi = locations->Out().AsRegister<CpuRegister>();

  NearLabel end, return_true, return_false;

  // Get offsets of count, value, and class fields within a string object.
  const uint32_t count_offset = mirror::String::CountOffset().Uint32Value();
  const uint32_t value_offset = mirror::String::ValueOffset().Uint32Value();
  const uint32_t class_offset = mirror::Object::ClassOffset().Uint32Value();

  // Note that the null check must have been done earlier.
  DCHECK(!invoke->CanDoImplicitNullCheckOn(invoke->InputAt(0)));

  StringEqualsOptimizations optimizations(invoke);
  if (!optimizations.GetArgumentNotNull()) {
    // Check if input is null, return false if it is.
    __ testl(arg, arg);
    __ j(kEqual, &return_false);
  }

  if (!optimizations.GetArgumentIsString()) {
    // Instanceof check for the argument by comparing class fields.
    // All string objects must have the same type since String cannot be subclassed.
    // Receiver must be a string object, so its class field is equal to all strings' class fields.
    // If the argument is a string object, its class field must be equal to receiver's class field.
    //
    // As the String class is expected to be non-movable, we can read the class
    // field from String.equals' arguments without read barriers.
    AssertNonMovableStringClass();
    // Also, because we use the loaded class references only to compare them, we
    // don't need to unpoison them.
    // /* HeapReference<Class> */ rcx = str->klass_
    __ movl(rcx, Address(str, class_offset));
    // if (rcx != /* HeapReference<Class> */ arg->klass_) return false
    __ cmpl(rcx, Address(arg, class_offset));
    __ j(kNotEqual, &return_false);
  }

  // Reference equality check, return true if same reference.
  __ cmpl(str, arg);
  __ j(kEqual, &return_true);

  // Load length and compression flag of receiver string.
  __ movl(rcx, Address(str, count_offset));
  // Check if lengths and compressiond flags are equal, return false if they're not.
  // Two identical strings will always have same compression style since
  // compression style is decided on alloc.
  __ cmpl(rcx, Address(arg, count_offset));
  __ j(kNotEqual, &return_false);
  // Return true if both strings are empty. Even with string compression `count == 0` means empty.
  static_assert(static_cast<uint32_t>(mirror::StringCompressionFlag::kCompressed) == 0u,
                "Expecting 0=compressed, 1=uncompressed");
  __ jrcxz(&return_true);

  if (mirror::kUseStringCompression) {
    NearLabel string_uncompressed;
    // Extract length and differentiate between both compressed or both uncompressed.
    // Different compression style is cut above.
    __ shrl(rcx, Immediate(1));
    __ j(kCarrySet, &string_uncompressed);
    // Divide string length by 2, rounding up, and continue as if uncompressed.
    // Merge clearing the compression flag with +1 for rounding.
    __ addl(rcx, Immediate(1));
    __ shrl(rcx, Immediate(1));
    __ Bind(&string_uncompressed);
  }
  // Load starting addresses of string values into RSI/RDI as required for repe_cmpsq instruction.
  __ leal(rsi, Address(str, value_offset));
  __ leal(rdi, Address(arg, value_offset));

  // Divide string length by 4 and adjust for lengths not divisible by 4.
  __ addl(rcx, Immediate(3));
  __ shrl(rcx, Immediate(2));

  // Assertions that must hold in order to compare strings 4 characters (uncompressed)
  // or 8 characters (compressed) at a time.
  DCHECK_ALIGNED(value_offset, 8);
  static_assert(IsAligned<8>(kObjectAlignment), "String is not zero padded");

  // Loop to compare strings four characters at a time starting at the beginning of the string.
  __ repe_cmpsq();
  // If strings are not equal, zero flag will be cleared.
  __ j(kNotEqual, &return_false);

  // Return true and exit the function.
  // If loop does not result in returning false, we return true.
  __ Bind(&return_true);
  __ movl(rsi, Immediate(1));
  __ jmp(&end);

  // Return false and exit the function.
  __ Bind(&return_false);
  __ xorl(rsi, rsi);
  __ Bind(&end);
}

static void CreateStringIndexOfLocations(HInvoke* invoke,
                                         ArenaAllocator* allocator,
                                         bool start_at_zero) {
  LocationSummary* locations = new (allocator) LocationSummary(invoke,
                                                               LocationSummary::kCallOnSlowPath,
                                                               kIntrinsified);
  // The data needs to be in RDI for scasw. So request that the string is there, anyways.
  locations->SetInAt(0, Location::RegisterLocation(RDI));
  // If we look for a constant char, we'll still have to copy it into RAX. So just request the
  // allocator to do that, anyways. We can still do the constant check by checking the parameter
  // of the instruction explicitly.
  // Note: This works as we don't clobber RAX anywhere.
  locations->SetInAt(1, Location::RegisterLocation(RAX));
  if (!start_at_zero) {
    locations->SetInAt(2, Location::RequiresRegister());          // The starting index.
  }
  // As we clobber RDI during execution anyways, also use it as the output.
  locations->SetOut(Location::SameAsFirstInput());

  // repne scasw uses RCX as the counter.
  locations->AddTemp(Location::RegisterLocation(RCX));
  // Need another temporary to be able to compute the result.
  locations->AddTemp(Location::RequiresRegister());
}

static void GenerateStringIndexOf(HInvoke* invoke,
                                  X86_64Assembler* assembler,
                                  CodeGeneratorX86_64* codegen,
                                  bool start_at_zero) {
  LocationSummary* locations = invoke->GetLocations();

  // Note that the null check must have been done earlier.
  DCHECK(!invoke->CanDoImplicitNullCheckOn(invoke->InputAt(0)));

  CpuRegister string_obj = locations->InAt(0).AsRegister<CpuRegister>();
  CpuRegister search_value = locations->InAt(1).AsRegister<CpuRegister>();
  CpuRegister counter = locations->GetTemp(0).AsRegister<CpuRegister>();
  CpuRegister string_length = locations->GetTemp(1).AsRegister<CpuRegister>();
  CpuRegister out = locations->Out().AsRegister<CpuRegister>();

  // Check our assumptions for registers.
  DCHECK_EQ(string_obj.AsRegister(), RDI);
  DCHECK_EQ(search_value.AsRegister(), RAX);
  DCHECK_EQ(counter.AsRegister(), RCX);
  DCHECK_EQ(out.AsRegister(), RDI);

  // Check for code points > 0xFFFF. Either a slow-path check when we don't know statically,
  // or directly dispatch for a large constant, or omit slow-path for a small constant or a char.
  SlowPathCode* slow_path = nullptr;
  HInstruction* code_point = invoke->InputAt(1);
  if (code_point->IsIntConstant()) {
    if (static_cast<uint32_t>(code_point->AsIntConstant()->GetValue()) >
        std::numeric_limits<uint16_t>::max()) {
      // Always needs the slow-path. We could directly dispatch to it, but this case should be
      // rare, so for simplicity just put the full slow-path down and branch unconditionally.
      slow_path = new (codegen->GetScopedAllocator()) IntrinsicSlowPathX86_64(invoke);
      codegen->AddSlowPath(slow_path);
      __ jmp(slow_path->GetEntryLabel());
      __ Bind(slow_path->GetExitLabel());
      return;
    }
  } else if (code_point->GetType() != DataType::Type::kUint16) {
    __ cmpl(search_value, Immediate(std::numeric_limits<uint16_t>::max()));
    slow_path = new (codegen->GetScopedAllocator()) IntrinsicSlowPathX86_64(invoke);
    codegen->AddSlowPath(slow_path);
    __ j(kAbove, slow_path->GetEntryLabel());
  }

  // From here down, we know that we are looking for a char that fits in
  // 16 bits (uncompressed) or 8 bits (compressed).
  // Location of reference to data array within the String object.
  int32_t value_offset = mirror::String::ValueOffset().Int32Value();
  // Location of count within the String object.
  int32_t count_offset = mirror::String::CountOffset().Int32Value();

  // Load the count field of the string containing the length and compression flag.
  __ movl(string_length, Address(string_obj, count_offset));

  // Do a zero-length check. Even with string compression `count == 0` means empty.
  // TODO: Support jecxz.
  NearLabel not_found_label;
  __ testl(string_length, string_length);
  __ j(kEqual, &not_found_label);

  if (mirror::kUseStringCompression) {
    // Use TMP to keep string_length_flagged.
    __ movl(CpuRegister(TMP), string_length);
    // Mask out first bit used as compression flag.
    __ shrl(string_length, Immediate(1));
  }

  if (start_at_zero) {
    // Number of chars to scan is the same as the string length.
    __ movl(counter, string_length);
    // Move to the start of the string.
    __ addq(string_obj, Immediate(value_offset));
  } else {
    CpuRegister start_index = locations->InAt(2).AsRegister<CpuRegister>();

    // Do a start_index check.
    __ cmpl(start_index, string_length);
    __ j(kGreaterEqual, &not_found_label);

    // Ensure we have a start index >= 0;
    __ xorl(counter, counter);
    __ cmpl(start_index, Immediate(0));
    __ cmov(kGreater, counter, start_index, /* is64bit= */ false);  // 32-bit copy is enough.

    if (mirror::kUseStringCompression) {
      NearLabel modify_counter, offset_uncompressed_label;
      __ testl(CpuRegister(TMP), Immediate(1));
      __ j(kNotZero, &offset_uncompressed_label);
      __ leaq(string_obj, Address(string_obj, counter, ScaleFactor::TIMES_1, value_offset));
      __ jmp(&modify_counter);
      // Move to the start of the string: string_obj + value_offset + 2 * start_index.
      __ Bind(&offset_uncompressed_label);
      __ leaq(string_obj, Address(string_obj, counter, ScaleFactor::TIMES_2, value_offset));
      __ Bind(&modify_counter);
    } else {
      __ leaq(string_obj, Address(string_obj, counter, ScaleFactor::TIMES_2, value_offset));
    }
    // Now update ecx, the work counter: it's gonna be string.length - start_index.
    __ negq(counter);  // Needs to be 64-bit negation, as the address computation is 64-bit.
    __ leaq(counter, Address(string_length, counter, ScaleFactor::TIMES_1, 0));
  }

  if (mirror::kUseStringCompression) {
    NearLabel uncompressed_string_comparison;
    NearLabel comparison_done;
    __ testl(CpuRegister(TMP), Immediate(1));
    __ j(kNotZero, &uncompressed_string_comparison);
    // Check if RAX (search_value) is ASCII.
    __ cmpl(search_value, Immediate(127));
    __ j(kGreater, &not_found_label);
    // Comparing byte-per-byte.
    __ repne_scasb();
    __ jmp(&comparison_done);
    // Everything is set up for repne scasw:
    //   * Comparison address in RDI.
    //   * Counter in ECX.
    __ Bind(&uncompressed_string_comparison);
    __ repne_scasw();
    __ Bind(&comparison_done);
  } else {
    __ repne_scasw();
  }
  // Did we find a match?
  __ j(kNotEqual, &not_found_label);

  // Yes, we matched.  Compute the index of the result.
  __ subl(string_length, counter);
  __ leal(out, Address(string_length, -1));

  NearLabel done;
  __ jmp(&done);

  // Failed to match; return -1.
  __ Bind(&not_found_label);
  __ movl(out, Immediate(-1));

  // And join up at the end.
  __ Bind(&done);
  if (slow_path != nullptr) {
    __ Bind(slow_path->GetExitLabel());
  }
}

void IntrinsicLocationsBuilderX86_64::VisitStringIndexOf(HInvoke* invoke) {
  CreateStringIndexOfLocations(invoke, allocator_, /* start_at_zero= */ true);
}

void IntrinsicCodeGeneratorX86_64::VisitStringIndexOf(HInvoke* invoke) {
  GenerateStringIndexOf(invoke, GetAssembler(), codegen_, /* start_at_zero= */ true);
}

void IntrinsicLocationsBuilderX86_64::VisitStringIndexOfAfter(HInvoke* invoke) {
  CreateStringIndexOfLocations(invoke, allocator_, /* start_at_zero= */ false);
}

void IntrinsicCodeGeneratorX86_64::VisitStringIndexOfAfter(HInvoke* invoke) {
  GenerateStringIndexOf(invoke, GetAssembler(), codegen_, /* start_at_zero= */ false);
}

void IntrinsicLocationsBuilderX86_64::VisitStringNewStringFromBytes(HInvoke* invoke) {
  LocationSummary* locations = new (allocator_) LocationSummary(
      invoke, LocationSummary::kCallOnMainAndSlowPath, kIntrinsified);
  InvokeRuntimeCallingConvention calling_convention;
  locations->SetInAt(0, Location::RegisterLocation(calling_convention.GetRegisterAt(0)));
  locations->SetInAt(1, Location::RegisterLocation(calling_convention.GetRegisterAt(1)));
  locations->SetInAt(2, Location::RegisterLocation(calling_convention.GetRegisterAt(2)));
  locations->SetInAt(3, Location::RegisterLocation(calling_convention.GetRegisterAt(3)));
  locations->SetOut(Location::RegisterLocation(RAX));
}

void IntrinsicCodeGeneratorX86_64::VisitStringNewStringFromBytes(HInvoke* invoke) {
  X86_64Assembler* assembler = GetAssembler();
  LocationSummary* locations = invoke->GetLocations();

  CpuRegister byte_array = locations->InAt(0).AsRegister<CpuRegister>();
  __ testl(byte_array, byte_array);
  SlowPathCode* slow_path = new (codegen_->GetScopedAllocator()) IntrinsicSlowPathX86_64(invoke);
  codegen_->AddSlowPath(slow_path);
  __ j(kEqual, slow_path->GetEntryLabel());

  codegen_->InvokeRuntime(kQuickAllocStringFromBytes, invoke, invoke->GetDexPc());
  CheckEntrypointTypes<kQuickAllocStringFromBytes, void*, void*, int32_t, int32_t, int32_t>();
  __ Bind(slow_path->GetExitLabel());
}

void IntrinsicLocationsBuilderX86_64::VisitStringNewStringFromChars(HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator_) LocationSummary(invoke, LocationSummary::kCallOnMainOnly, kIntrinsified);
  InvokeRuntimeCallingConvention calling_convention;
  locations->SetInAt(0, Location::RegisterLocation(calling_convention.GetRegisterAt(0)));
  locations->SetInAt(1, Location::RegisterLocation(calling_convention.GetRegisterAt(1)));
  locations->SetInAt(2, Location::RegisterLocation(calling_convention.GetRegisterAt(2)));
  locations->SetOut(Location::RegisterLocation(RAX));
}

void IntrinsicCodeGeneratorX86_64::VisitStringNewStringFromChars(HInvoke* invoke) {
  // No need to emit code checking whether `locations->InAt(2)` is a null
  // pointer, as callers of the native method
  //
  //   java.lang.StringFactory.newStringFromChars(int offset, int charCount, char[] data)
  //
  // all include a null check on `data` before calling that method.
  codegen_->InvokeRuntime(kQuickAllocStringFromChars, invoke, invoke->GetDexPc());
  CheckEntrypointTypes<kQuickAllocStringFromChars, void*, int32_t, int32_t, void*>();
}

void IntrinsicLocationsBuilderX86_64::VisitStringNewStringFromString(HInvoke* invoke) {
  LocationSummary* locations = new (allocator_) LocationSummary(
      invoke, LocationSummary::kCallOnMainAndSlowPath, kIntrinsified);
  InvokeRuntimeCallingConvention calling_convention;
  locations->SetInAt(0, Location::RegisterLocation(calling_convention.GetRegisterAt(0)));
  locations->SetOut(Location::RegisterLocation(RAX));
}

void IntrinsicCodeGeneratorX86_64::VisitStringNewStringFromString(HInvoke* invoke) {
  X86_64Assembler* assembler = GetAssembler();
  LocationSummary* locations = invoke->GetLocations();

  CpuRegister string_to_copy = locations->InAt(0).AsRegister<CpuRegister>();
  __ testl(string_to_copy, string_to_copy);
  SlowPathCode* slow_path = new (codegen_->GetScopedAllocator()) IntrinsicSlowPathX86_64(invoke);
  codegen_->AddSlowPath(slow_path);
  __ j(kEqual, slow_path->GetEntryLabel());

  codegen_->InvokeRuntime(kQuickAllocStringFromString, invoke, invoke->GetDexPc());
  CheckEntrypointTypes<kQuickAllocStringFromString, void*, void*>();
  __ Bind(slow_path->GetExitLabel());
}

void IntrinsicLocationsBuilderX86_64::VisitStringGetCharsNoCheck(HInvoke* invoke) {
  // public void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin);
  LocationSummary* locations =
      new (allocator_) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::RequiresRegister());
  locations->SetInAt(1, Location::RegisterOrConstant(invoke->InputAt(1)));
  locations->SetInAt(2, Location::RequiresRegister());
  locations->SetInAt(3, Location::RequiresRegister());
  locations->SetInAt(4, Location::RequiresRegister());

  // And we need some temporaries.  We will use REP MOVSW, so we need fixed registers.
  locations->AddTemp(Location::RegisterLocation(RSI));
  locations->AddTemp(Location::RegisterLocation(RDI));
  locations->AddTemp(Location::RegisterLocation(RCX));
}

void IntrinsicCodeGeneratorX86_64::VisitStringGetCharsNoCheck(HInvoke* invoke) {
  X86_64Assembler* assembler = GetAssembler();
  LocationSummary* locations = invoke->GetLocations();

  size_t char_component_size = DataType::Size(DataType::Type::kUint16);
  // Location of data in char array buffer.
  const uint32_t data_offset = mirror::Array::DataOffset(char_component_size).Uint32Value();
  // Location of char array data in string.
  const uint32_t value_offset = mirror::String::ValueOffset().Uint32Value();

  // public void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin);
  CpuRegister obj = locations->InAt(0).AsRegister<CpuRegister>();
  Location srcBegin = locations->InAt(1);
  int srcBegin_value =
      srcBegin.IsConstant() ? srcBegin.GetConstant()->AsIntConstant()->GetValue() : 0;
  CpuRegister srcEnd = locations->InAt(2).AsRegister<CpuRegister>();
  CpuRegister dst = locations->InAt(3).AsRegister<CpuRegister>();
  CpuRegister dstBegin = locations->InAt(4).AsRegister<CpuRegister>();

  // Check assumption that sizeof(Char) is 2 (used in scaling below).
  const size_t char_size = DataType::Size(DataType::Type::kUint16);
  DCHECK_EQ(char_size, 2u);

  NearLabel done;
  // Compute the number of chars (words) to move.
  __ movl(CpuRegister(RCX), srcEnd);
  if (srcBegin.IsConstant()) {
    __ subl(CpuRegister(RCX), Immediate(srcBegin_value));
  } else {
    DCHECK(srcBegin.IsRegister());
    __ subl(CpuRegister(RCX), srcBegin.AsRegister<CpuRegister>());
  }
  if (mirror::kUseStringCompression) {
    NearLabel copy_uncompressed, copy_loop;
    const size_t c_char_size = DataType::Size(DataType::Type::kInt8);
    DCHECK_EQ(c_char_size, 1u);
    // Location of count in string.
    const uint32_t count_offset = mirror::String::CountOffset().Uint32Value();

    __ testl(Address(obj, count_offset), Immediate(1));
    static_assert(static_cast<uint32_t>(mirror::StringCompressionFlag::kCompressed) == 0u,
                  "Expecting 0=compressed, 1=uncompressed");
    __ j(kNotZero, &copy_uncompressed);
    // Compute the address of the source string by adding the number of chars from
    // the source beginning to the value offset of a string.
    __ leaq(CpuRegister(RSI),
            CodeGeneratorX86_64::ArrayAddress(obj, srcBegin, TIMES_1, value_offset));
    // Start the loop to copy String's value to Array of Char.
    __ leaq(CpuRegister(RDI), Address(dst, dstBegin, ScaleFactor::TIMES_2, data_offset));

    __ Bind(&copy_loop);
    __ jrcxz(&done);
    // Use TMP as temporary (convert byte from RSI to word).
    // TODO: Selecting RAX as the temporary and using LODSB/STOSW.
    __ movzxb(CpuRegister(TMP), Address(CpuRegister(RSI), 0));
    __ movw(Address(CpuRegister(RDI), 0), CpuRegister(TMP));
    __ leaq(CpuRegister(RDI), Address(CpuRegister(RDI), char_size));
    __ leaq(CpuRegister(RSI), Address(CpuRegister(RSI), c_char_size));
    // TODO: Add support for LOOP to X86_64Assembler.
    __ subl(CpuRegister(RCX), Immediate(1));
    __ jmp(&copy_loop);

    __ Bind(&copy_uncompressed);
  }

  __ leaq(CpuRegister(RSI),
          CodeGeneratorX86_64::ArrayAddress(obj, srcBegin, TIMES_2, value_offset));
  // Compute the address of the destination buffer.
  __ leaq(CpuRegister(RDI), Address(dst, dstBegin, ScaleFactor::TIMES_2, data_offset));
  // Do the move.
  __ rep_movsw();

  __ Bind(&done);
}

static void GenPeek(LocationSummary* locations, DataType::Type size, X86_64Assembler* assembler) {
  CpuRegister address = locations->InAt(0).AsRegister<CpuRegister>();
  CpuRegister out = locations->Out().AsRegister<CpuRegister>();  // == address, here for clarity.
  // x86 allows unaligned access. We do not have to check the input or use specific instructions
  // to avoid a SIGBUS.
  switch (size) {
    case DataType::Type::kInt8:
      __ movsxb(out, Address(address, 0));
      break;
    case DataType::Type::kInt16:
      __ movsxw(out, Address(address, 0));
      break;
    case DataType::Type::kInt32:
      __ movl(out, Address(address, 0));
      break;
    case DataType::Type::kInt64:
      __ movq(out, Address(address, 0));
      break;
    default:
      LOG(FATAL) << "Type not recognized for peek: " << size;
      UNREACHABLE();
  }
}

void IntrinsicLocationsBuilderX86_64::VisitMemoryPeekByte(HInvoke* invoke) {
  CreateIntToIntLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitMemoryPeekByte(HInvoke* invoke) {
  GenPeek(invoke->GetLocations(), DataType::Type::kInt8, GetAssembler());
}

void IntrinsicLocationsBuilderX86_64::VisitMemoryPeekIntNative(HInvoke* invoke) {
  CreateIntToIntLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitMemoryPeekIntNative(HInvoke* invoke) {
  GenPeek(invoke->GetLocations(), DataType::Type::kInt32, GetAssembler());
}

void IntrinsicLocationsBuilderX86_64::VisitMemoryPeekLongNative(HInvoke* invoke) {
  CreateIntToIntLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitMemoryPeekLongNative(HInvoke* invoke) {
  GenPeek(invoke->GetLocations(), DataType::Type::kInt64, GetAssembler());
}

void IntrinsicLocationsBuilderX86_64::VisitMemoryPeekShortNative(HInvoke* invoke) {
  CreateIntToIntLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitMemoryPeekShortNative(HInvoke* invoke) {
  GenPeek(invoke->GetLocations(), DataType::Type::kInt16, GetAssembler());
}

static void CreateIntIntToVoidLocations(ArenaAllocator* allocator, HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::RequiresRegister());
  locations->SetInAt(1, Location::RegisterOrInt32Constant(invoke->InputAt(1)));
}

static void GenPoke(LocationSummary* locations, DataType::Type size, X86_64Assembler* assembler) {
  CpuRegister address = locations->InAt(0).AsRegister<CpuRegister>();
  Location value = locations->InAt(1);
  // x86 allows unaligned access. We do not have to check the input or use specific instructions
  // to avoid a SIGBUS.
  switch (size) {
    case DataType::Type::kInt8:
      if (value.IsConstant()) {
        __ movb(Address(address, 0),
                Immediate(CodeGenerator::GetInt32ValueOf(value.GetConstant())));
      } else {
        __ movb(Address(address, 0), value.AsRegister<CpuRegister>());
      }
      break;
    case DataType::Type::kInt16:
      if (value.IsConstant()) {
        __ movw(Address(address, 0),
                Immediate(CodeGenerator::GetInt32ValueOf(value.GetConstant())));
      } else {
        __ movw(Address(address, 0), value.AsRegister<CpuRegister>());
      }
      break;
    case DataType::Type::kInt32:
      if (value.IsConstant()) {
        __ movl(Address(address, 0),
                Immediate(CodeGenerator::GetInt32ValueOf(value.GetConstant())));
      } else {
        __ movl(Address(address, 0), value.AsRegister<CpuRegister>());
      }
      break;
    case DataType::Type::kInt64:
      if (value.IsConstant()) {
        int64_t v = value.GetConstant()->AsLongConstant()->GetValue();
        DCHECK(IsInt<32>(v));
        int32_t v_32 = v;
        __ movq(Address(address, 0), Immediate(v_32));
      } else {
        __ movq(Address(address, 0), value.AsRegister<CpuRegister>());
      }
      break;
    default:
      LOG(FATAL) << "Type not recognized for poke: " << size;
      UNREACHABLE();
  }
}

void IntrinsicLocationsBuilderX86_64::VisitMemoryPokeByte(HInvoke* invoke) {
  CreateIntIntToVoidLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitMemoryPokeByte(HInvoke* invoke) {
  GenPoke(invoke->GetLocations(), DataType::Type::kInt8, GetAssembler());
}

void IntrinsicLocationsBuilderX86_64::VisitMemoryPokeIntNative(HInvoke* invoke) {
  CreateIntIntToVoidLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitMemoryPokeIntNative(HInvoke* invoke) {
  GenPoke(invoke->GetLocations(), DataType::Type::kInt32, GetAssembler());
}

void IntrinsicLocationsBuilderX86_64::VisitMemoryPokeLongNative(HInvoke* invoke) {
  CreateIntIntToVoidLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitMemoryPokeLongNative(HInvoke* invoke) {
  GenPoke(invoke->GetLocations(), DataType::Type::kInt64, GetAssembler());
}

void IntrinsicLocationsBuilderX86_64::VisitMemoryPokeShortNative(HInvoke* invoke) {
  CreateIntIntToVoidLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitMemoryPokeShortNative(HInvoke* invoke) {
  GenPoke(invoke->GetLocations(), DataType::Type::kInt16, GetAssembler());
}

void IntrinsicLocationsBuilderX86_64::VisitThreadCurrentThread(HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator_) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetOut(Location::RequiresRegister());
}

void IntrinsicCodeGeneratorX86_64::VisitThreadCurrentThread(HInvoke* invoke) {
  CpuRegister out = invoke->GetLocations()->Out().AsRegister<CpuRegister>();
  GetAssembler()->gs()->movl(out, Address::Absolute(Thread::PeerOffset<kX86_64PointerSize>(),
                                                    /* no_rip= */ true));
}

static void GenUnsafeGet(HInvoke* invoke,
                         DataType::Type type,
                         [[maybe_unused]] bool is_volatile,
                         CodeGeneratorX86_64* codegen) {
  X86_64Assembler* assembler = down_cast<X86_64Assembler*>(codegen->GetAssembler());
  LocationSummary* locations = invoke->GetLocations();
  Location base_loc = locations->InAt(1);
  CpuRegister base = base_loc.AsRegister<CpuRegister>();
  Location offset_loc = locations->InAt(2);
  CpuRegister offset = offset_loc.AsRegister<CpuRegister>();
  Location output_loc = locations->Out();
  CpuRegister output = output_loc.AsRegister<CpuRegister>();

  switch (type) {
    case DataType::Type::kInt8:
      __ movsxb(output, Address(base, offset, ScaleFactor::TIMES_1, 0));
      break;

    case DataType::Type::kInt32:
      __ movl(output, Address(base, offset, ScaleFactor::TIMES_1, 0));
      break;

    case DataType::Type::kReference: {
      if (codegen->EmitReadBarrier()) {
        if (kUseBakerReadBarrier) {
          Address src(base, offset, ScaleFactor::TIMES_1, 0);
          codegen->GenerateReferenceLoadWithBakerReadBarrier(
              invoke, output_loc, base, src, /* needs_null_check= */ false);
        } else {
          __ movl(output, Address(base, offset, ScaleFactor::TIMES_1, 0));
          codegen->GenerateReadBarrierSlow(
              invoke, output_loc, output_loc, base_loc, 0U, offset_loc);
        }
      } else {
        __ movl(output, Address(base, offset, ScaleFactor::TIMES_1, 0));
        __ MaybeUnpoisonHeapReference(output);
      }
      break;
    }

    case DataType::Type::kInt64:
      __ movq(output, Address(base, offset, ScaleFactor::TIMES_1, 0));
      break;

    default:
      LOG(FATAL) << "Unsupported op size " << type;
      UNREACHABLE();
  }
}

static void CreateIntIntIntToIntLocations(ArenaAllocator* allocator,
                                          HInvoke* invoke,
                                          CodeGeneratorX86_64* codegen) {
  bool can_call = codegen->EmitReadBarrier() && IsUnsafeGetReference(invoke);
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke,
                                      can_call
                                          ? LocationSummary::kCallOnSlowPath
                                          : LocationSummary::kNoCall,
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

void IntrinsicLocationsBuilderX86_64::VisitUnsafeGet(HInvoke* invoke) {
  VisitJdkUnsafeGet(invoke);
}
void IntrinsicLocationsBuilderX86_64::VisitUnsafeGetVolatile(HInvoke* invoke) {
  VisitJdkUnsafeGetVolatile(invoke);
}
void IntrinsicLocationsBuilderX86_64::VisitUnsafeGetLong(HInvoke* invoke) {
  VisitJdkUnsafeGetLong(invoke);
}
void IntrinsicLocationsBuilderX86_64::VisitUnsafeGetLongVolatile(HInvoke* invoke) {
  VisitJdkUnsafeGetLongVolatile(invoke);
}
void IntrinsicLocationsBuilderX86_64::VisitUnsafeGetObject(HInvoke* invoke) {
  VisitJdkUnsafeGetReference(invoke);
}
void IntrinsicLocationsBuilderX86_64::VisitUnsafeGetObjectVolatile(HInvoke* invoke) {
  VisitJdkUnsafeGetReferenceVolatile(invoke);
}
void IntrinsicLocationsBuilderX86_64::VisitUnsafeGetByte(HInvoke* invoke) {
  VisitJdkUnsafeGetByte(invoke);
}

void IntrinsicLocationsBuilderX86_64::VisitJdkUnsafeGet(HInvoke* invoke) {
  CreateIntIntIntToIntLocations(allocator_, invoke, codegen_);
}
void IntrinsicLocationsBuilderX86_64::VisitJdkUnsafeGetVolatile(HInvoke* invoke) {
  CreateIntIntIntToIntLocations(allocator_, invoke, codegen_);
}
void IntrinsicLocationsBuilderX86_64::VisitJdkUnsafeGetAcquire(HInvoke* invoke) {
  CreateIntIntIntToIntLocations(allocator_, invoke, codegen_);
}
void IntrinsicLocationsBuilderX86_64::VisitJdkUnsafeGetLong(HInvoke* invoke) {
  CreateIntIntIntToIntLocations(allocator_, invoke, codegen_);
}
void IntrinsicLocationsBuilderX86_64::VisitJdkUnsafeGetLongVolatile(HInvoke* invoke) {
  CreateIntIntIntToIntLocations(allocator_, invoke, codegen_);
}
void IntrinsicLocationsBuilderX86_64::VisitJdkUnsafeGetLongAcquire(HInvoke* invoke) {
  CreateIntIntIntToIntLocations(allocator_, invoke, codegen_);
}
void IntrinsicLocationsBuilderX86_64::VisitJdkUnsafeGetReference(HInvoke* invoke) {
  CreateIntIntIntToIntLocations(allocator_, invoke, codegen_);
}
void IntrinsicLocationsBuilderX86_64::VisitJdkUnsafeGetReferenceVolatile(HInvoke* invoke) {
  CreateIntIntIntToIntLocations(allocator_, invoke, codegen_);
}
void IntrinsicLocationsBuilderX86_64::VisitJdkUnsafeGetReferenceAcquire(HInvoke* invoke) {
  CreateIntIntIntToIntLocations(allocator_, invoke, codegen_);
}
void IntrinsicLocationsBuilderX86_64::VisitJdkUnsafeGetByte(HInvoke* invoke) {
  CreateIntIntIntToIntLocations(allocator_, invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitUnsafeGet(HInvoke* invoke) {
  VisitJdkUnsafeGet(invoke);
}
void IntrinsicCodeGeneratorX86_64::VisitUnsafeGetVolatile(HInvoke* invoke) {
  VisitJdkUnsafeGetVolatile(invoke);
}
void IntrinsicCodeGeneratorX86_64::VisitUnsafeGetLong(HInvoke* invoke) {
  VisitJdkUnsafeGetLong(invoke);
}
void IntrinsicCodeGeneratorX86_64::VisitUnsafeGetLongVolatile(HInvoke* invoke) {
  VisitJdkUnsafeGetLongVolatile(invoke);
}
void IntrinsicCodeGeneratorX86_64::VisitUnsafeGetObject(HInvoke* invoke) {
  VisitJdkUnsafeGetReference(invoke);
}
void IntrinsicCodeGeneratorX86_64::VisitUnsafeGetObjectVolatile(HInvoke* invoke) {
  VisitJdkUnsafeGetReferenceVolatile(invoke);
}
void IntrinsicCodeGeneratorX86_64::VisitUnsafeGetByte(HInvoke* invoke) {
  VisitJdkUnsafeGetByte(invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitJdkUnsafeGet(HInvoke* invoke) {
  GenUnsafeGet(invoke, DataType::Type::kInt32, /*is_volatile=*/ false, codegen_);
}
void IntrinsicCodeGeneratorX86_64::VisitJdkUnsafeGetVolatile(HInvoke* invoke) {
  GenUnsafeGet(invoke, DataType::Type::kInt32, /*is_volatile=*/ true, codegen_);
}
void IntrinsicCodeGeneratorX86_64::VisitJdkUnsafeGetAcquire(HInvoke* invoke) {
  GenUnsafeGet(invoke, DataType::Type::kInt32, /*is_volatile=*/ true, codegen_);
}
void IntrinsicCodeGeneratorX86_64::VisitJdkUnsafeGetLong(HInvoke* invoke) {
  GenUnsafeGet(invoke, DataType::Type::kInt64, /*is_volatile=*/ false, codegen_);
}
void IntrinsicCodeGeneratorX86_64::VisitJdkUnsafeGetLongVolatile(HInvoke* invoke) {
  GenUnsafeGet(invoke, DataType::Type::kInt64, /*is_volatile=*/ true, codegen_);
}
void IntrinsicCodeGeneratorX86_64::VisitJdkUnsafeGetLongAcquire(HInvoke* invoke) {
  GenUnsafeGet(invoke, DataType::Type::kInt64, /*is_volatile=*/ true, codegen_);
}
void IntrinsicCodeGeneratorX86_64::VisitJdkUnsafeGetReference(HInvoke* invoke) {
  GenUnsafeGet(invoke, DataType::Type::kReference, /*is_volatile=*/ false, codegen_);
}
void IntrinsicCodeGeneratorX86_64::VisitJdkUnsafeGetReferenceVolatile(HInvoke* invoke) {
  GenUnsafeGet(invoke, DataType::Type::kReference, /*is_volatile=*/ true, codegen_);
}
void IntrinsicCodeGeneratorX86_64::VisitJdkUnsafeGetReferenceAcquire(HInvoke* invoke) {
  GenUnsafeGet(invoke, DataType::Type::kReference, /*is_volatile=*/ true, codegen_);
}
void IntrinsicCodeGeneratorX86_64::VisitJdkUnsafeGetByte(HInvoke* invoke) {
  GenUnsafeGet(invoke, DataType::Type::kInt8, /*is_volatile=*/false, codegen_);
}

static void CreateIntIntIntIntToVoidPlusTempsLocations(ArenaAllocator* allocator,
                                                       DataType::Type type,
                                                       HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::NoLocation());        // Unused receiver.
  locations->SetInAt(1, Location::RequiresRegister());
  locations->SetInAt(2, Location::RequiresRegister());
  locations->SetInAt(3, Location::RequiresRegister());
  if (type == DataType::Type::kReference) {
    // Need temp registers for card-marking.
    locations->AddTemp(Location::RequiresRegister());  // Possibly used for reference poisoning too.
    locations->AddTemp(Location::RequiresRegister());
  }
}

void IntrinsicLocationsBuilderX86_64::VisitUnsafePut(HInvoke* invoke) {
  VisitJdkUnsafePut(invoke);
}
void IntrinsicLocationsBuilderX86_64::VisitUnsafePutOrdered(HInvoke* invoke) {
  VisitJdkUnsafePutOrdered(invoke);
}
void IntrinsicLocationsBuilderX86_64::VisitUnsafePutVolatile(HInvoke* invoke) {
  VisitJdkUnsafePutVolatile(invoke);
}
void IntrinsicLocationsBuilderX86_64::VisitUnsafePutObject(HInvoke* invoke) {
  VisitJdkUnsafePutReference(invoke);
}
void IntrinsicLocationsBuilderX86_64::VisitUnsafePutObjectOrdered(HInvoke* invoke) {
  VisitJdkUnsafePutObjectOrdered(invoke);
}
void IntrinsicLocationsBuilderX86_64::VisitUnsafePutObjectVolatile(HInvoke* invoke) {
  VisitJdkUnsafePutReferenceVolatile(invoke);
}
void IntrinsicLocationsBuilderX86_64::VisitUnsafePutLong(HInvoke* invoke) {
  VisitJdkUnsafePutLong(invoke);
}
void IntrinsicLocationsBuilderX86_64::VisitUnsafePutLongOrdered(HInvoke* invoke) {
  VisitJdkUnsafePutLongOrdered(invoke);
}
void IntrinsicLocationsBuilderX86_64::VisitUnsafePutLongVolatile(HInvoke* invoke) {
  VisitJdkUnsafePutLongVolatile(invoke);
}
void IntrinsicLocationsBuilderX86_64::VisitUnsafePutByte(HInvoke* invoke) {
  VisitJdkUnsafePut(invoke);
}

void IntrinsicLocationsBuilderX86_64::VisitJdkUnsafePut(HInvoke* invoke) {
  CreateIntIntIntIntToVoidPlusTempsLocations(allocator_, DataType::Type::kInt32, invoke);
}
void IntrinsicLocationsBuilderX86_64::VisitJdkUnsafePutOrdered(HInvoke* invoke) {
  CreateIntIntIntIntToVoidPlusTempsLocations(allocator_, DataType::Type::kInt32, invoke);
}
void IntrinsicLocationsBuilderX86_64::VisitJdkUnsafePutVolatile(HInvoke* invoke) {
  CreateIntIntIntIntToVoidPlusTempsLocations(allocator_, DataType::Type::kInt32, invoke);
}
void IntrinsicLocationsBuilderX86_64::VisitJdkUnsafePutRelease(HInvoke* invoke) {
  CreateIntIntIntIntToVoidPlusTempsLocations(allocator_, DataType::Type::kInt32, invoke);
}
void IntrinsicLocationsBuilderX86_64::VisitJdkUnsafePutReference(HInvoke* invoke) {
  CreateIntIntIntIntToVoidPlusTempsLocations(allocator_, DataType::Type::kReference, invoke);
}
void IntrinsicLocationsBuilderX86_64::VisitJdkUnsafePutObjectOrdered(HInvoke* invoke) {
  CreateIntIntIntIntToVoidPlusTempsLocations(allocator_, DataType::Type::kReference, invoke);
}
void IntrinsicLocationsBuilderX86_64::VisitJdkUnsafePutReferenceVolatile(HInvoke* invoke) {
  CreateIntIntIntIntToVoidPlusTempsLocations(allocator_, DataType::Type::kReference, invoke);
}
void IntrinsicLocationsBuilderX86_64::VisitJdkUnsafePutReferenceRelease(HInvoke* invoke) {
  CreateIntIntIntIntToVoidPlusTempsLocations(allocator_, DataType::Type::kReference, invoke);
}
void IntrinsicLocationsBuilderX86_64::VisitJdkUnsafePutLong(HInvoke* invoke) {
  CreateIntIntIntIntToVoidPlusTempsLocations(allocator_, DataType::Type::kInt64, invoke);
}
void IntrinsicLocationsBuilderX86_64::VisitJdkUnsafePutLongOrdered(HInvoke* invoke) {
  CreateIntIntIntIntToVoidPlusTempsLocations(allocator_, DataType::Type::kInt64, invoke);
}
void IntrinsicLocationsBuilderX86_64::VisitJdkUnsafePutLongVolatile(HInvoke* invoke) {
  CreateIntIntIntIntToVoidPlusTempsLocations(allocator_, DataType::Type::kInt64, invoke);
}
void IntrinsicLocationsBuilderX86_64::VisitJdkUnsafePutLongRelease(HInvoke* invoke) {
  CreateIntIntIntIntToVoidPlusTempsLocations(allocator_, DataType::Type::kInt64, invoke);
}
void IntrinsicLocationsBuilderX86_64::VisitJdkUnsafePutByte(HInvoke* invoke) {
  CreateIntIntIntIntToVoidPlusTempsLocations(allocator_, DataType::Type::kUint8, invoke);
}

// We don't care for ordered: it requires an AnyStore barrier, which is already given by the x86
// memory model.
static void GenUnsafePut(LocationSummary* locations, DataType::Type type, bool is_volatile,
                         CodeGeneratorX86_64* codegen) {
  X86_64Assembler* assembler = down_cast<X86_64Assembler*>(codegen->GetAssembler());
  CpuRegister base = locations->InAt(1).AsRegister<CpuRegister>();
  CpuRegister offset = locations->InAt(2).AsRegister<CpuRegister>();
  CpuRegister value = locations->InAt(3).AsRegister<CpuRegister>();

  if (type == DataType::Type::kInt64) {
    __ movq(Address(base, offset, ScaleFactor::TIMES_1, 0), value);
  } else if (kPoisonHeapReferences && type == DataType::Type::kReference) {
    CpuRegister temp = locations->GetTemp(0).AsRegister<CpuRegister>();
    __ movl(temp, value);
    __ PoisonHeapReference(temp);
    __ movl(Address(base, offset, ScaleFactor::TIMES_1, 0), temp);
  } else {
    __ movl(Address(base, offset, ScaleFactor::TIMES_1, 0), value);
  }

  if (is_volatile) {
    codegen->MemoryFence();
  }

  if (type == DataType::Type::kReference) {
    bool value_can_be_null = true;  // TODO: Worth finding out this information?
    codegen->MaybeMarkGCCard(locations->GetTemp(0).AsRegister<CpuRegister>(),
                             locations->GetTemp(1).AsRegister<CpuRegister>(),
                             base,
                             value,
                             value_can_be_null);
  }
}

void IntrinsicCodeGeneratorX86_64::VisitUnsafePut(HInvoke* invoke) {
  VisitJdkUnsafePut(invoke);
}
void IntrinsicCodeGeneratorX86_64::VisitUnsafePutOrdered(HInvoke* invoke) {
  VisitJdkUnsafePutOrdered(invoke);
}
void IntrinsicCodeGeneratorX86_64::VisitUnsafePutVolatile(HInvoke* invoke) {
  VisitJdkUnsafePutVolatile(invoke);
}
void IntrinsicCodeGeneratorX86_64::VisitUnsafePutObject(HInvoke* invoke) {
  VisitJdkUnsafePutReference(invoke);
}
void IntrinsicCodeGeneratorX86_64::VisitUnsafePutObjectOrdered(HInvoke* invoke) {
  VisitJdkUnsafePutObjectOrdered(invoke);
}
void IntrinsicCodeGeneratorX86_64::VisitUnsafePutObjectVolatile(HInvoke* invoke) {
  VisitJdkUnsafePutReferenceVolatile(invoke);
}
void IntrinsicCodeGeneratorX86_64::VisitUnsafePutLong(HInvoke* invoke) {
  VisitJdkUnsafePutLong(invoke);
}
void IntrinsicCodeGeneratorX86_64::VisitUnsafePutLongOrdered(HInvoke* invoke) {
  VisitJdkUnsafePutLongOrdered(invoke);
}
void IntrinsicCodeGeneratorX86_64::VisitUnsafePutLongVolatile(HInvoke* invoke) {
  VisitJdkUnsafePutLongVolatile(invoke);
}
void IntrinsicCodeGeneratorX86_64::VisitUnsafePutByte(HInvoke* invoke) {
  VisitJdkUnsafePutByte(invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitJdkUnsafePut(HInvoke* invoke) {
  GenUnsafePut(invoke->GetLocations(), DataType::Type::kInt32, /*is_volatile=*/ false, codegen_);
}
void IntrinsicCodeGeneratorX86_64::VisitJdkUnsafePutOrdered(HInvoke* invoke) {
  GenUnsafePut(invoke->GetLocations(), DataType::Type::kInt32, /*is_volatile=*/ false, codegen_);
}
void IntrinsicCodeGeneratorX86_64::VisitJdkUnsafePutVolatile(HInvoke* invoke) {
  GenUnsafePut(invoke->GetLocations(), DataType::Type::kInt32, /*is_volatile=*/ true, codegen_);
}
void IntrinsicCodeGeneratorX86_64::VisitJdkUnsafePutRelease(HInvoke* invoke) {
  GenUnsafePut(invoke->GetLocations(), DataType::Type::kInt32, /* is_volatile= */ true, codegen_);
}
void IntrinsicCodeGeneratorX86_64::VisitJdkUnsafePutReference(HInvoke* invoke) {
  GenUnsafePut(
      invoke->GetLocations(), DataType::Type::kReference, /*is_volatile=*/ false, codegen_);
}
void IntrinsicCodeGeneratorX86_64::VisitJdkUnsafePutObjectOrdered(HInvoke* invoke) {
  GenUnsafePut(
      invoke->GetLocations(), DataType::Type::kReference, /*is_volatile=*/ false, codegen_);
}
void IntrinsicCodeGeneratorX86_64::VisitJdkUnsafePutReferenceVolatile(HInvoke* invoke) {
  GenUnsafePut(
      invoke->GetLocations(), DataType::Type::kReference, /*is_volatile=*/ true, codegen_);
}
void IntrinsicCodeGeneratorX86_64::VisitJdkUnsafePutReferenceRelease(HInvoke* invoke) {
  GenUnsafePut(
      invoke->GetLocations(), DataType::Type::kReference, /*is_volatile=*/ true, codegen_);
}
void IntrinsicCodeGeneratorX86_64::VisitJdkUnsafePutLong(HInvoke* invoke) {
  GenUnsafePut(invoke->GetLocations(), DataType::Type::kInt64, /*is_volatile=*/ false, codegen_);
}
void IntrinsicCodeGeneratorX86_64::VisitJdkUnsafePutLongOrdered(HInvoke* invoke) {
  GenUnsafePut(invoke->GetLocations(), DataType::Type::kInt64, /*is_volatile=*/ false, codegen_);
}
void IntrinsicCodeGeneratorX86_64::VisitJdkUnsafePutLongVolatile(HInvoke* invoke) {
  GenUnsafePut(invoke->GetLocations(), DataType::Type::kInt64, /*is_volatile=*/ true, codegen_);
}
void IntrinsicCodeGeneratorX86_64::VisitJdkUnsafePutLongRelease(HInvoke* invoke) {
  GenUnsafePut(invoke->GetLocations(), DataType::Type::kInt64, /*is_volatile=*/ true, codegen_);
}
void IntrinsicCodeGeneratorX86_64::VisitJdkUnsafePutByte(HInvoke* invoke) {
  GenUnsafePut(invoke->GetLocations(), DataType::Type::kInt8, /*is_volatile=*/false, codegen_);
}

static void CreateUnsafeCASLocations(ArenaAllocator* allocator,
                                     HInvoke* invoke,
                                     CodeGeneratorX86_64* codegen,
                                     DataType::Type type) {
  const bool can_call = codegen->EmitBakerReadBarrier() && IsUnsafeCASReference(invoke);
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke,
                                      can_call
                                          ? LocationSummary::kCallOnSlowPath
                                          : LocationSummary::kNoCall,
                                      kIntrinsified);
  locations->SetInAt(0, Location::NoLocation());        // Unused receiver.
  locations->SetInAt(1, Location::RequiresRegister());
  locations->SetInAt(2, Location::RequiresRegister());
  // expected value must be in EAX/RAX.
  locations->SetInAt(3, Location::RegisterLocation(RAX));
  locations->SetInAt(4, Location::RequiresRegister());

  // RAX is clobbered in CMPXCHG, but we set it as out so no need to add it as temporary.
  locations->SetOut(Location::RegisterLocation(RAX));

  if (type == DataType::Type::kReference) {
    // Need two temporaries for MarkGCCard.
    locations->AddTemp(Location::RequiresRegister());  // Possibly used for reference poisoning too.
    locations->AddTemp(Location::RequiresRegister());
    if (codegen->EmitReadBarrier()) {
      // Need three temporaries for GenerateReferenceLoadWithBakerReadBarrier.
      DCHECK(kUseBakerReadBarrier);
      locations->AddTemp(Location::RequiresRegister());
    }
  }
}

void IntrinsicLocationsBuilderX86_64::VisitUnsafeCASInt(HInvoke* invoke) {
  VisitJdkUnsafeCASInt(invoke);
}

void IntrinsicLocationsBuilderX86_64::VisitUnsafeCASLong(HInvoke* invoke) {
  VisitJdkUnsafeCASLong(invoke);
}

void IntrinsicLocationsBuilderX86_64::VisitUnsafeCASObject(HInvoke* invoke) {
  VisitJdkUnsafeCASObject(invoke);
}

void IntrinsicLocationsBuilderX86_64::VisitJdkUnsafeCASInt(HInvoke* invoke) {
  // `jdk.internal.misc.Unsafe.compareAndSwapInt` has compare-and-set semantics (see javadoc).
  VisitJdkUnsafeCompareAndSetInt(invoke);
}

void IntrinsicLocationsBuilderX86_64::VisitJdkUnsafeCASLong(HInvoke* invoke) {
  // `jdk.internal.misc.Unsafe.compareAndSwapLong` has compare-and-set semantics (see javadoc).
  VisitJdkUnsafeCompareAndSetLong(invoke);
}

void IntrinsicLocationsBuilderX86_64::VisitJdkUnsafeCASObject(HInvoke* invoke) {
  // `jdk.internal.misc.Unsafe.compareAndSwapObject` has compare-and-set semantics (see javadoc).
  VisitJdkUnsafeCompareAndSetReference(invoke);
}

void IntrinsicLocationsBuilderX86_64::VisitJdkUnsafeCompareAndSetInt(HInvoke* invoke) {
  CreateUnsafeCASLocations(allocator_, invoke, codegen_, DataType::Type::kInt32);
}

void IntrinsicLocationsBuilderX86_64::VisitJdkUnsafeCompareAndSetLong(HInvoke* invoke) {
  CreateUnsafeCASLocations(allocator_, invoke, codegen_, DataType::Type::kInt64);
}

void IntrinsicLocationsBuilderX86_64::VisitJdkUnsafeCompareAndSetReference(HInvoke* invoke) {
  // The only supported read barrier implementation is the Baker-style read barriers.
  if (codegen_->EmitNonBakerReadBarrier()) {
    return;
  }

  CreateUnsafeCASLocations(allocator_, invoke, codegen_, DataType::Type::kReference);
}

// Convert ZF into the Boolean result.
static inline void GenZFlagToResult(X86_64Assembler* assembler, CpuRegister out) {
  __ setcc(kZero, out);
  __ movzxb(out, out);
}

// This function assumes that expected value for CMPXCHG and output are in RAX.
static void GenCompareAndSetOrExchangeInt(CodeGeneratorX86_64* codegen,
                                          DataType::Type type,
                                          Address field_addr,
                                          Location value,
                                          bool is_cmpxchg,
                                          bool byte_swap) {
  X86_64Assembler* assembler = down_cast<X86_64Assembler*>(codegen->GetAssembler());
  InstructionCodeGeneratorX86_64* instr_codegen = codegen->GetInstructionCodegen();

  if (byte_swap) {
    instr_codegen->Bswap(Location::RegisterLocation(RAX), type);
    instr_codegen->Bswap(value, type);
  }

  switch (type) {
    case DataType::Type::kBool:
    case DataType::Type::kInt8:
      __ LockCmpxchgb(field_addr, value.AsRegister<CpuRegister>());
      break;
    case DataType::Type::kInt16:
    case DataType::Type::kUint16:
      __ LockCmpxchgw(field_addr, value.AsRegister<CpuRegister>());
      break;
    case DataType::Type::kInt32:
    case DataType::Type::kUint32:
      __ LockCmpxchgl(field_addr, value.AsRegister<CpuRegister>());
      break;
    case DataType::Type::kInt64:
    case DataType::Type::kUint64:
      __ LockCmpxchgq(field_addr, value.AsRegister<CpuRegister>());
      break;
    default:
      LOG(FATAL) << "Unexpected non-integral CAS type " << type;
  }
  // LOCK CMPXCHG has full barrier semantics, so we don't need barriers here.

  if (byte_swap) {
    // Restore byte order for value.
    instr_codegen->Bswap(value, type);
  }

  CpuRegister rax(RAX);
  if (is_cmpxchg) {
    if (byte_swap) {
      instr_codegen->Bswap(Location::RegisterLocation(RAX), type);
    }
    // Sign-extend or zero-extend the result as necessary.
    switch (type) {
      case DataType::Type::kBool:
        __ movzxb(rax, rax);
        break;
      case DataType::Type::kInt8:
        __ movsxb(rax, rax);
        break;
      case DataType::Type::kInt16:
        __ movsxw(rax, rax);
        break;
      case DataType::Type::kUint16:
        __ movzxw(rax, rax);
        break;
      default:
        break;  // No need to do anything.
    }
  } else {
    GenZFlagToResult(assembler, rax);
  }
}

static void GenCompareAndSetOrExchangeFP(CodeGeneratorX86_64* codegen,
                                         Address field_addr,
                                         CpuRegister temp,
                                         Location value,
                                         Location expected,
                                         Location out,
                                         bool is64bit,
                                         bool is_cmpxchg,
                                         bool byte_swap) {
  X86_64Assembler* assembler = down_cast<X86_64Assembler*>(codegen->GetAssembler());
  InstructionCodeGeneratorX86_64* instr_codegen = codegen->GetInstructionCodegen();

  Location rax_loc = Location::RegisterLocation(RAX);
  Location temp_loc = Location::RegisterLocation(temp.AsRegister());

  DataType::Type type = is64bit ? DataType::Type::kUint64 : DataType::Type::kUint32;

  // Copy `expected` to RAX (required by the CMPXCHG instruction).
  codegen->Move(rax_loc, expected);

  // Copy value to some other register (ensure it's not RAX).
  DCHECK_NE(temp.AsRegister(), RAX);
  codegen->Move(temp_loc, value);

  if (byte_swap) {
    instr_codegen->Bswap(rax_loc, type);
    instr_codegen->Bswap(temp_loc, type);
  }

  if (is64bit) {
    __ LockCmpxchgq(field_addr, temp);
  } else {
    __ LockCmpxchgl(field_addr, temp);
  }
  // LOCK CMPXCHG has full barrier semantics, so we don't need barriers here.
  // No need to restore byte order for temporary register.

  if (is_cmpxchg) {
    if (byte_swap) {
      instr_codegen->Bswap(rax_loc, type);
    }
    __ movd(out.AsFpuRegister<XmmRegister>(), CpuRegister(RAX), is64bit);
  } else {
    GenZFlagToResult(assembler, out.AsRegister<CpuRegister>());
  }
}

// This function assumes that expected value for CMPXCHG and output are in RAX.
static void GenCompareAndSetOrExchangeRef(CodeGeneratorX86_64* codegen,
                                          HInvoke* invoke,
                                          CpuRegister base,
                                          CpuRegister offset,
                                          CpuRegister value,
                                          CpuRegister temp1,
                                          CpuRegister temp2,
                                          CpuRegister temp3,
                                          bool is_cmpxchg) {
  // The only supported read barrier implementation is the Baker-style read barriers.
  DCHECK_IMPLIES(codegen->EmitReadBarrier(), kUseBakerReadBarrier);

  X86_64Assembler* assembler = down_cast<X86_64Assembler*>(codegen->GetAssembler());

  // Mark card for object assuming new value is stored.
  bool value_can_be_null = true;  // TODO: Worth finding out this information?
  codegen->MaybeMarkGCCard(temp1, temp2, base, value, value_can_be_null);

  Address field_addr(base, offset, TIMES_1, 0);
  if (codegen->EmitBakerReadBarrier()) {
    // Need to make sure the reference stored in the field is a to-space
    // one before attempting the CAS or the CAS could fail incorrectly.
    codegen->GenerateReferenceLoadWithBakerReadBarrier(
        invoke,
        Location::RegisterLocation(temp3.AsRegister()),
        base,
        field_addr,
        /* needs_null_check= */ false,
        /* always_update_field= */ true,
        &temp1,
        &temp2);
  } else {
    // Nothing to do, the value will be loaded into the out register by CMPXCHG.
  }

  bool base_equals_value = (base.AsRegister() == value.AsRegister());
  Register value_reg = value.AsRegister();
  if (kPoisonHeapReferences) {
    if (base_equals_value) {
      // If `base` and `value` are the same register location, move `value_reg` to a temporary
      // register.  This way, poisoning `value_reg` won't invalidate `base`.
      value_reg = temp1.AsRegister();
      __ movl(CpuRegister(value_reg), base);
    }

    // Check that the register allocator did not assign the location of expected value (RAX) to
    // `value` nor to `base`, so that heap poisoning (when enabled) works as intended below.
    // - If `value` were equal to RAX, both references would be poisoned twice, meaning they would
    //   not be poisoned at all, as heap poisoning uses address negation.
    // - If `base` were equal to RAX, poisoning RAX would invalidate `base`.
    DCHECK_NE(RAX, value_reg);
    DCHECK_NE(RAX, base.AsRegister());

    __ PoisonHeapReference(CpuRegister(RAX));
    __ PoisonHeapReference(CpuRegister(value_reg));
  }

  __ LockCmpxchgl(field_addr, CpuRegister(value_reg));
  // LOCK CMPXCHG has full barrier semantics, so we don't need barriers.

  if (is_cmpxchg) {
    // Output is in RAX, so we can rely on CMPXCHG and do nothing.
    __ MaybeUnpoisonHeapReference(CpuRegister(RAX));
  } else {
    GenZFlagToResult(assembler, CpuRegister(RAX));
  }

  // If heap poisoning is enabled, we need to unpoison the values that were poisoned earlier.
  if (kPoisonHeapReferences) {
    if (base_equals_value) {
      // `value_reg` has been moved to a temporary register, no need to unpoison it.
    } else {
      // Ensure `value` is not RAX, so that unpoisoning the former does not invalidate the latter.
      DCHECK_NE(RAX, value_reg);
      __ UnpoisonHeapReference(CpuRegister(value_reg));
    }
  }
}

// In debug mode, return true if all registers are pairwise different. In release mode, do nothing
// and always return true.
static bool RegsAreAllDifferent(const std::vector<CpuRegister>& regs) {
  if (kIsDebugBuild) {
    for (size_t i = 0; i < regs.size(); ++i) {
      for (size_t j = 0; j < i; ++j) {
        if (regs[i].AsRegister() == regs[j].AsRegister()) {
          return false;
        }
      }
    }
  }
  return true;
}

// GenCompareAndSetOrExchange handles all value types and therefore accepts generic locations and
// temporary indices that may not correspond to real registers for code paths that do not use them.
static void GenCompareAndSetOrExchange(CodeGeneratorX86_64* codegen,
                                       HInvoke* invoke,
                                       DataType::Type type,
                                       CpuRegister base,
                                       CpuRegister offset,
                                       uint32_t temp1_index,
                                       uint32_t temp2_index,
                                       uint32_t temp3_index,
                                       Location new_value,
                                       Location expected,
                                       Location out,
                                       bool is_cmpxchg,
                                       bool byte_swap) {
  LocationSummary* locations = invoke->GetLocations();
  Address field_address(base, offset, TIMES_1, 0);

  if (DataType::IsFloatingPointType(type)) {
    bool is64bit = (type == DataType::Type::kFloat64);
    CpuRegister temp = locations->GetTemp(temp1_index).AsRegister<CpuRegister>();
    DCHECK(RegsAreAllDifferent({base, offset, temp, CpuRegister(RAX)}));

    GenCompareAndSetOrExchangeFP(
        codegen, field_address, temp, new_value, expected, out, is64bit, is_cmpxchg, byte_swap);
  } else {
    // Both the expected value for CMPXCHG and the output are in RAX.
    DCHECK_EQ(RAX, expected.AsRegister<Register>());
    DCHECK_EQ(RAX, out.AsRegister<Register>());

    if (type == DataType::Type::kReference) {
      CpuRegister new_value_reg = new_value.AsRegister<CpuRegister>();
      CpuRegister temp1 = locations->GetTemp(temp1_index).AsRegister<CpuRegister>();
      CpuRegister temp2 = locations->GetTemp(temp2_index).AsRegister<CpuRegister>();
      CpuRegister temp3 = codegen->EmitReadBarrier()
          ? locations->GetTemp(temp3_index).AsRegister<CpuRegister>()
          : CpuRegister(kNoRegister);
      DCHECK(RegsAreAllDifferent({base, offset, temp1, temp2, temp3}));

      DCHECK(!byte_swap);
      GenCompareAndSetOrExchangeRef(
          codegen, invoke, base, offset, new_value_reg, temp1, temp2, temp3, is_cmpxchg);
    } else {
      GenCompareAndSetOrExchangeInt(codegen, type, field_address, new_value, is_cmpxchg, byte_swap);
    }
  }
}

static void GenCAS(DataType::Type type, HInvoke* invoke, CodeGeneratorX86_64* codegen) {
  LocationSummary* locations = invoke->GetLocations();
  GenCompareAndSetOrExchange(codegen,
                             invoke,
                             type,
                             /*base=*/ locations->InAt(1).AsRegister<CpuRegister>(),
                             /*offset=*/ locations->InAt(2).AsRegister<CpuRegister>(),
                             /*temp1_index=*/ 0,
                             /*temp2_index=*/ 1,
                             /*temp3_index=*/ 2,
                             /*new_value=*/ locations->InAt(4),
                             /*expected=*/ locations->InAt(3),
                             locations->Out(),
                             /*is_cmpxchg=*/ false,
                             /*byte_swap=*/ false);
}

void IntrinsicCodeGeneratorX86_64::VisitUnsafeCASInt(HInvoke* invoke) {
  VisitJdkUnsafeCASInt(invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitUnsafeCASLong(HInvoke* invoke) {
  VisitJdkUnsafeCASLong(invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitUnsafeCASObject(HInvoke* invoke) {
  VisitJdkUnsafeCASObject(invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitJdkUnsafeCASInt(HInvoke* invoke) {
  // `jdk.internal.misc.Unsafe.compareAndSwapInt` has compare-and-set semantics (see javadoc).
  VisitJdkUnsafeCompareAndSetInt(invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitJdkUnsafeCASLong(HInvoke* invoke) {
  // `jdk.internal.misc.Unsafe.compareAndSwapLong` has compare-and-set semantics (see javadoc).
  VisitJdkUnsafeCompareAndSetLong(invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitJdkUnsafeCASObject(HInvoke* invoke) {
  // `jdk.internal.misc.Unsafe.compareAndSwapObject` has compare-and-set semantics (see javadoc).
  VisitJdkUnsafeCompareAndSetReference(invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitJdkUnsafeCompareAndSetInt(HInvoke* invoke) {
  GenCAS(DataType::Type::kInt32, invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitJdkUnsafeCompareAndSetLong(HInvoke* invoke) {
  GenCAS(DataType::Type::kInt64, invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitJdkUnsafeCompareAndSetReference(HInvoke* invoke) {
  // The only supported read barrier implementation is the Baker-style read barriers.
  DCHECK_IMPLIES(codegen_->EmitReadBarrier(), kUseBakerReadBarrier);

  GenCAS(DataType::Type::kReference, invoke, codegen_);
}

static void CreateUnsafeGetAndUpdateLocations(ArenaAllocator* allocator,
                                              HInvoke* invoke,
                                              CodeGeneratorX86_64* codegen) {
  const bool can_call = codegen->EmitReadBarrier() && IsUnsafeGetAndSetReference(invoke);
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke,
                                      can_call
                                          ? LocationSummary::kCallOnSlowPath
                                          : LocationSummary::kNoCall,
                                      kIntrinsified);
  if (can_call && kUseBakerReadBarrier) {
    locations->SetCustomSlowPathCallerSaves(RegisterSet::Empty());  // No caller-save registers.
  }
  locations->SetInAt(0, Location::NoLocation());        // Unused receiver.
  locations->SetInAt(1, Location::RequiresRegister());
  locations->SetInAt(2, Location::RequiresRegister());
  // Use the same register for both the output and the new value or addend
  // to take advantage of XCHG or XADD. Arbitrarily pick RAX.
  locations->SetInAt(3, Location::RegisterLocation(RAX));
  locations->SetOut(Location::RegisterLocation(RAX));
}

void IntrinsicLocationsBuilderX86_64::VisitUnsafeGetAndAddInt(HInvoke* invoke) {
  VisitJdkUnsafeGetAndAddInt(invoke);
}

void IntrinsicLocationsBuilderX86_64::VisitUnsafeGetAndAddLong(HInvoke* invoke) {
  VisitJdkUnsafeGetAndAddLong(invoke);
}

void IntrinsicLocationsBuilderX86_64::VisitUnsafeGetAndSetInt(HInvoke* invoke) {
  VisitJdkUnsafeGetAndSetInt(invoke);
}

void IntrinsicLocationsBuilderX86_64::VisitUnsafeGetAndSetLong(HInvoke* invoke) {
  VisitJdkUnsafeGetAndSetLong(invoke);
}

void IntrinsicLocationsBuilderX86_64::VisitUnsafeGetAndSetObject(HInvoke* invoke) {
  VisitJdkUnsafeGetAndSetReference(invoke);
}

void IntrinsicLocationsBuilderX86_64::VisitJdkUnsafeGetAndAddInt(HInvoke* invoke) {
  CreateUnsafeGetAndUpdateLocations(allocator_, invoke, codegen_);
}

void IntrinsicLocationsBuilderX86_64::VisitJdkUnsafeGetAndAddLong(HInvoke* invoke) {
  CreateUnsafeGetAndUpdateLocations(allocator_, invoke, codegen_);
}

void IntrinsicLocationsBuilderX86_64::VisitJdkUnsafeGetAndSetInt(HInvoke* invoke) {
  CreateUnsafeGetAndUpdateLocations(allocator_, invoke, codegen_);
}

void IntrinsicLocationsBuilderX86_64::VisitJdkUnsafeGetAndSetLong(HInvoke* invoke) {
  CreateUnsafeGetAndUpdateLocations(allocator_, invoke, codegen_);
}

void IntrinsicLocationsBuilderX86_64::VisitJdkUnsafeGetAndSetReference(HInvoke* invoke) {
  // The only supported read barrier implementation is the Baker-style read barriers.
  if (codegen_->EmitNonBakerReadBarrier()) {
    return;
  }

  CreateUnsafeGetAndUpdateLocations(allocator_, invoke, codegen_);
  invoke->GetLocations()->AddRegisterTemps(3);
}

enum class GetAndUpdateOp {
  kSet,
  kAdd,
  kBitwiseAnd,
  kBitwiseOr,
  kBitwiseXor
};

static void GenUnsafeGetAndUpdate(HInvoke* invoke,
                                  DataType::Type type,
                                  CodeGeneratorX86_64* codegen,
                                  GetAndUpdateOp get_and_update_op) {
  X86_64Assembler* assembler = down_cast<X86_64Assembler*>(codegen->GetAssembler());
  LocationSummary* locations = invoke->GetLocations();

  CpuRegister out = locations->Out().AsRegister<CpuRegister>();       // Result.
  CpuRegister base = locations->InAt(1).AsRegister<CpuRegister>();    // Object pointer.
  CpuRegister offset = locations->InAt(2).AsRegister<CpuRegister>();  // Long offset.
  DCHECK_EQ(out, locations->InAt(3).AsRegister<CpuRegister>());       // New value or addend.
  Address field_address(base, offset, TIMES_1, 0);

  if (type == DataType::Type::kInt32) {
    if (get_and_update_op == GetAndUpdateOp::kAdd) {
      __ LockXaddl(field_address, out);
    } else {
      DCHECK(get_and_update_op == GetAndUpdateOp::kSet);
      __ xchgl(out, field_address);
    }
  } else if (type == DataType::Type::kInt64) {
    if (get_and_update_op == GetAndUpdateOp::kAdd) {
      __ LockXaddq(field_address, out);
    } else {
      DCHECK(get_and_update_op == GetAndUpdateOp::kSet);
      __ xchgq(out, field_address);
    }
  } else {
    DCHECK_EQ(type, DataType::Type::kReference);
    DCHECK(get_and_update_op == GetAndUpdateOp::kSet);
    CpuRegister temp1 = locations->GetTemp(0).AsRegister<CpuRegister>();
    CpuRegister temp2 = locations->GetTemp(1).AsRegister<CpuRegister>();
    CpuRegister temp3 = locations->GetTemp(2).AsRegister<CpuRegister>();

    if (codegen->EmitReadBarrier()) {
      DCHECK(kUseBakerReadBarrier);
      // Ensure that the field contains a to-space reference.
      codegen->GenerateReferenceLoadWithBakerReadBarrier(
          invoke,
          Location::RegisterLocation(temp3.AsRegister()),
          base,
          field_address,
          /*needs_null_check=*/ false,
          /*always_update_field=*/ true,
          &temp1,
          &temp2);
    }

    // Mark card for object as a new value shall be stored.
    bool new_value_can_be_null = true;  // TODO: Worth finding out this information?
    codegen->MaybeMarkGCCard(temp1, temp2, base, /*value=*/out, new_value_can_be_null);

    if (kPoisonHeapReferences) {
      // Use a temp to avoid poisoning base of the field address, which might happen if `out`
      // is the same as `base` (for code like `unsafe.getAndSet(obj, offset, obj)`).
      __ movl(temp1, out);
      __ PoisonHeapReference(temp1);
      __ xchgl(temp1, field_address);
      __ UnpoisonHeapReference(temp1);
      __ movl(out, temp1);
    } else {
      __ xchgl(out, field_address);
    }
  }
}

void IntrinsicCodeGeneratorX86_64::VisitUnsafeGetAndAddInt(HInvoke* invoke) {
  VisitJdkUnsafeGetAndAddInt(invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitUnsafeGetAndAddLong(HInvoke* invoke) {
  VisitJdkUnsafeGetAndAddLong(invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitUnsafeGetAndSetInt(HInvoke* invoke) {
  VisitJdkUnsafeGetAndSetInt(invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitUnsafeGetAndSetLong(HInvoke* invoke) {
  VisitJdkUnsafeGetAndSetLong(invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitUnsafeGetAndSetObject(HInvoke* invoke) {
  VisitJdkUnsafeGetAndSetReference(invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitJdkUnsafeGetAndAddInt(HInvoke* invoke) {
  GenUnsafeGetAndUpdate(invoke, DataType::Type::kInt32, codegen_, GetAndUpdateOp::kAdd);
}

void IntrinsicCodeGeneratorX86_64::VisitJdkUnsafeGetAndAddLong(HInvoke* invoke) {
  GenUnsafeGetAndUpdate(invoke, DataType::Type::kInt64, codegen_, GetAndUpdateOp::kAdd);
}

void IntrinsicCodeGeneratorX86_64::VisitJdkUnsafeGetAndSetInt(HInvoke* invoke) {
  GenUnsafeGetAndUpdate(invoke, DataType::Type::kInt32, codegen_, GetAndUpdateOp::kSet);
}

void IntrinsicCodeGeneratorX86_64::VisitJdkUnsafeGetAndSetLong(HInvoke* invoke) {
  GenUnsafeGetAndUpdate(invoke, DataType::Type::kInt64, codegen_, GetAndUpdateOp::kSet);
}

void IntrinsicCodeGeneratorX86_64::VisitJdkUnsafeGetAndSetReference(HInvoke* invoke) {
  GenUnsafeGetAndUpdate(invoke, DataType::Type::kReference, codegen_, GetAndUpdateOp::kSet);
}

void IntrinsicLocationsBuilderX86_64::VisitIntegerReverse(HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator_) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::RequiresRegister());
  locations->SetOut(Location::SameAsFirstInput());
  locations->AddTemp(Location::RequiresRegister());
}

static void SwapBits(CpuRegister reg, CpuRegister temp, int32_t shift, int32_t mask,
                     X86_64Assembler* assembler) {
  Immediate imm_shift(shift);
  Immediate imm_mask(mask);
  __ movl(temp, reg);
  __ shrl(reg, imm_shift);
  __ andl(temp, imm_mask);
  __ andl(reg, imm_mask);
  __ shll(temp, imm_shift);
  __ orl(reg, temp);
}

void IntrinsicCodeGeneratorX86_64::VisitIntegerReverse(HInvoke* invoke) {
  X86_64Assembler* assembler = GetAssembler();
  LocationSummary* locations = invoke->GetLocations();

  CpuRegister reg = locations->InAt(0).AsRegister<CpuRegister>();
  CpuRegister temp = locations->GetTemp(0).AsRegister<CpuRegister>();

  /*
   * Use one bswap instruction to reverse byte order first and then use 3 rounds of
   * swapping bits to reverse bits in a number x. Using bswap to save instructions
   * compared to generic luni implementation which has 5 rounds of swapping bits.
   * x = bswap x
   * x = (x & 0x55555555) << 1 | (x >> 1) & 0x55555555;
   * x = (x & 0x33333333) << 2 | (x >> 2) & 0x33333333;
   * x = (x & 0x0F0F0F0F) << 4 | (x >> 4) & 0x0F0F0F0F;
   */
  __ bswapl(reg);
  SwapBits(reg, temp, 1, 0x55555555, assembler);
  SwapBits(reg, temp, 2, 0x33333333, assembler);
  SwapBits(reg, temp, 4, 0x0f0f0f0f, assembler);
}

void IntrinsicLocationsBuilderX86_64::VisitLongReverse(HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator_) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::RequiresRegister());
  locations->SetOut(Location::SameAsFirstInput());
  locations->AddTemp(Location::RequiresRegister());
  locations->AddTemp(Location::RequiresRegister());
}

static void SwapBits64(CpuRegister reg, CpuRegister temp, CpuRegister temp_mask,
                       int32_t shift, int64_t mask, X86_64Assembler* assembler) {
  Immediate imm_shift(shift);
  __ movq(temp_mask, Immediate(mask));
  __ movq(temp, reg);
  __ shrq(reg, imm_shift);
  __ andq(temp, temp_mask);
  __ andq(reg, temp_mask);
  __ shlq(temp, imm_shift);
  __ orq(reg, temp);
}

void IntrinsicCodeGeneratorX86_64::VisitLongReverse(HInvoke* invoke) {
  X86_64Assembler* assembler = GetAssembler();
  LocationSummary* locations = invoke->GetLocations();

  CpuRegister reg = locations->InAt(0).AsRegister<CpuRegister>();
  CpuRegister temp1 = locations->GetTemp(0).AsRegister<CpuRegister>();
  CpuRegister temp2 = locations->GetTemp(1).AsRegister<CpuRegister>();

  /*
   * Use one bswap instruction to reverse byte order first and then use 3 rounds of
   * swapping bits to reverse bits in a long number x. Using bswap to save instructions
   * compared to generic luni implementation which has 5 rounds of swapping bits.
   * x = bswap x
   * x = (x & 0x5555555555555555) << 1 | (x >> 1) & 0x5555555555555555;
   * x = (x & 0x3333333333333333) << 2 | (x >> 2) & 0x3333333333333333;
   * x = (x & 0x0F0F0F0F0F0F0F0F) << 4 | (x >> 4) & 0x0F0F0F0F0F0F0F0F;
   */
  __ bswapq(reg);
  SwapBits64(reg, temp1, temp2, 1, INT64_C(0x5555555555555555), assembler);
  SwapBits64(reg, temp1, temp2, 2, INT64_C(0x3333333333333333), assembler);
  SwapBits64(reg, temp1, temp2, 4, INT64_C(0x0f0f0f0f0f0f0f0f), assembler);
}

static void CreateBitCountLocations(
    ArenaAllocator* allocator, CodeGeneratorX86_64* codegen, HInvoke* invoke) {
  if (!codegen->GetInstructionSetFeatures().HasPopCnt()) {
    // Do nothing if there is no popcnt support. This results in generating
    // a call for the intrinsic rather than direct code.
    return;
  }
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::Any());
  locations->SetOut(Location::RequiresRegister());
}

static void GenBitCount(X86_64Assembler* assembler,
                        CodeGeneratorX86_64* codegen,
                        HInvoke* invoke,
                        bool is_long) {
  LocationSummary* locations = invoke->GetLocations();
  Location src = locations->InAt(0);
  CpuRegister out = locations->Out().AsRegister<CpuRegister>();

  if (invoke->InputAt(0)->IsConstant()) {
    // Evaluate this at compile time.
    int64_t value = Int64FromConstant(invoke->InputAt(0)->AsConstant());
    int32_t result = is_long
        ? POPCOUNT(static_cast<uint64_t>(value))
        : POPCOUNT(static_cast<uint32_t>(value));
    codegen->Load32BitValue(out, result);
    return;
  }

  if (src.IsRegister()) {
    if (is_long) {
      __ popcntq(out, src.AsRegister<CpuRegister>());
    } else {
      __ popcntl(out, src.AsRegister<CpuRegister>());
    }
  } else if (is_long) {
    DCHECK(src.IsDoubleStackSlot());
    __ popcntq(out, Address(CpuRegister(RSP), src.GetStackIndex()));
  } else {
    DCHECK(src.IsStackSlot());
    __ popcntl(out, Address(CpuRegister(RSP), src.GetStackIndex()));
  }
}

void IntrinsicLocationsBuilderX86_64::VisitIntegerBitCount(HInvoke* invoke) {
  CreateBitCountLocations(allocator_, codegen_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitIntegerBitCount(HInvoke* invoke) {
  GenBitCount(GetAssembler(), codegen_, invoke, /* is_long= */ false);
}

void IntrinsicLocationsBuilderX86_64::VisitLongBitCount(HInvoke* invoke) {
  CreateBitCountLocations(allocator_, codegen_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitLongBitCount(HInvoke* invoke) {
  GenBitCount(GetAssembler(), codegen_, invoke, /* is_long= */ true);
}

static void CreateOneBitLocations(ArenaAllocator* allocator, HInvoke* invoke, bool is_high) {
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::Any());
  locations->SetOut(Location::RequiresRegister());
  locations->AddTemp(is_high ? Location::RegisterLocation(RCX)  // needs CL
                             : Location::RequiresRegister());  // any will do
}

static void GenOneBit(X86_64Assembler* assembler,
                      CodeGeneratorX86_64* codegen,
                      HInvoke* invoke,
                      bool is_high, bool is_long) {
  LocationSummary* locations = invoke->GetLocations();
  Location src = locations->InAt(0);
  CpuRegister out = locations->Out().AsRegister<CpuRegister>();

  if (invoke->InputAt(0)->IsConstant()) {
    // Evaluate this at compile time.
    int64_t value = Int64FromConstant(invoke->InputAt(0)->AsConstant());
    if (value == 0) {
      __ xorl(out, out);  // Clears upper bits too.
      return;
    }
    // Nonzero value.
    if (is_high) {
      value = is_long ? 63 - CLZ(static_cast<uint64_t>(value))
                      : 31 - CLZ(static_cast<uint32_t>(value));
    } else {
      value = is_long ? CTZ(static_cast<uint64_t>(value))
                      : CTZ(static_cast<uint32_t>(value));
    }
    if (is_long) {
      codegen->Load64BitValue(out, 1ULL << value);
    } else {
      codegen->Load32BitValue(out, 1 << value);
    }
    return;
  }

  // Handle the non-constant cases.
  if (!is_high && codegen->GetInstructionSetFeatures().HasAVX2() &&
      src.IsRegister()) {
      __ blsi(out, src.AsRegister<CpuRegister>());
  } else {
    CpuRegister tmp = locations->GetTemp(0).AsRegister<CpuRegister>();
    if (is_high) {
      // Use architectural support: basically 1 << bsr.
      if (src.IsRegister()) {
        if (is_long) {
          __ bsrq(tmp, src.AsRegister<CpuRegister>());
        } else {
          __ bsrl(tmp, src.AsRegister<CpuRegister>());
        }
      } else if (is_long) {
        DCHECK(src.IsDoubleStackSlot());
        __ bsrq(tmp, Address(CpuRegister(RSP), src.GetStackIndex()));
      } else {
        DCHECK(src.IsStackSlot());
        __ bsrl(tmp, Address(CpuRegister(RSP), src.GetStackIndex()));
      }
      // BSR sets ZF if the input was zero.
      NearLabel is_zero, done;
      __ j(kEqual, &is_zero);
      __ movl(out, Immediate(1));  // Clears upper bits too.
      if (is_long) {
        __ shlq(out, tmp);
      } else {
        __ shll(out, tmp);
      }
      __ jmp(&done);
      __ Bind(&is_zero);
      __ xorl(out, out);  // Clears upper bits too.
      __ Bind(&done);
    } else  {
      // Copy input into temporary.
      if (src.IsRegister()) {
        if (is_long) {
          __ movq(tmp, src.AsRegister<CpuRegister>());
        } else {
          __ movl(tmp, src.AsRegister<CpuRegister>());
        }
      } else if (is_long) {
        DCHECK(src.IsDoubleStackSlot());
        __ movq(tmp, Address(CpuRegister(RSP), src.GetStackIndex()));
      } else {
        DCHECK(src.IsStackSlot());
        __ movl(tmp, Address(CpuRegister(RSP), src.GetStackIndex()));
      }
      // Do the bit twiddling: basically tmp & -tmp;
      if (is_long) {
        __ movq(out, tmp);
        __ negq(tmp);
        __ andq(out, tmp);
      } else {
        __ movl(out, tmp);
        __ negl(tmp);
        __ andl(out, tmp);
      }
    }
  }
}

void IntrinsicLocationsBuilderX86_64::VisitIntegerHighestOneBit(HInvoke* invoke) {
  CreateOneBitLocations(allocator_, invoke, /* is_high= */ true);
}

void IntrinsicCodeGeneratorX86_64::VisitIntegerHighestOneBit(HInvoke* invoke) {
  GenOneBit(GetAssembler(), codegen_, invoke, /* is_high= */ true, /* is_long= */ false);
}

void IntrinsicLocationsBuilderX86_64::VisitLongHighestOneBit(HInvoke* invoke) {
  CreateOneBitLocations(allocator_, invoke, /* is_high= */ true);
}

void IntrinsicCodeGeneratorX86_64::VisitLongHighestOneBit(HInvoke* invoke) {
  GenOneBit(GetAssembler(), codegen_, invoke, /* is_high= */ true, /* is_long= */ true);
}

void IntrinsicLocationsBuilderX86_64::VisitIntegerLowestOneBit(HInvoke* invoke) {
  CreateOneBitLocations(allocator_, invoke, /* is_high= */ false);
}

void IntrinsicCodeGeneratorX86_64::VisitIntegerLowestOneBit(HInvoke* invoke) {
  GenOneBit(GetAssembler(), codegen_, invoke, /* is_high= */ false, /* is_long= */ false);
}

void IntrinsicLocationsBuilderX86_64::VisitLongLowestOneBit(HInvoke* invoke) {
  CreateOneBitLocations(allocator_, invoke, /* is_high= */ false);
}

void IntrinsicCodeGeneratorX86_64::VisitLongLowestOneBit(HInvoke* invoke) {
  GenOneBit(GetAssembler(), codegen_, invoke, /* is_high= */ false, /* is_long= */ true);
}

static void CreateLeadingZeroLocations(ArenaAllocator* allocator, HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::Any());
  locations->SetOut(Location::RequiresRegister());
}

static void GenLeadingZeros(X86_64Assembler* assembler,
                            CodeGeneratorX86_64* codegen,
                            HInvoke* invoke, bool is_long) {
  LocationSummary* locations = invoke->GetLocations();
  Location src = locations->InAt(0);
  CpuRegister out = locations->Out().AsRegister<CpuRegister>();

  int zero_value_result = is_long ? 64 : 32;
  if (invoke->InputAt(0)->IsConstant()) {
    // Evaluate this at compile time.
    int64_t value = Int64FromConstant(invoke->InputAt(0)->AsConstant());
    if (value == 0) {
      value = zero_value_result;
    } else {
      value = is_long ? CLZ(static_cast<uint64_t>(value)) : CLZ(static_cast<uint32_t>(value));
    }
    codegen->Load32BitValue(out, value);
    return;
  }

  // Handle the non-constant cases.
  if (src.IsRegister()) {
    if (is_long) {
      __ bsrq(out, src.AsRegister<CpuRegister>());
    } else {
      __ bsrl(out, src.AsRegister<CpuRegister>());
    }
  } else if (is_long) {
    DCHECK(src.IsDoubleStackSlot());
    __ bsrq(out, Address(CpuRegister(RSP), src.GetStackIndex()));
  } else {
    DCHECK(src.IsStackSlot());
    __ bsrl(out, Address(CpuRegister(RSP), src.GetStackIndex()));
  }

  // BSR sets ZF if the input was zero, and the output is undefined.
  NearLabel is_zero, done;
  __ j(kEqual, &is_zero);

  // Correct the result from BSR to get the CLZ result.
  __ xorl(out, Immediate(zero_value_result - 1));
  __ jmp(&done);

  // Fix the zero case with the expected result.
  __ Bind(&is_zero);
  __ movl(out, Immediate(zero_value_result));

  __ Bind(&done);
}

void IntrinsicLocationsBuilderX86_64::VisitIntegerNumberOfLeadingZeros(HInvoke* invoke) {
  CreateLeadingZeroLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitIntegerNumberOfLeadingZeros(HInvoke* invoke) {
  GenLeadingZeros(GetAssembler(), codegen_, invoke, /* is_long= */ false);
}

void IntrinsicLocationsBuilderX86_64::VisitLongNumberOfLeadingZeros(HInvoke* invoke) {
  CreateLeadingZeroLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitLongNumberOfLeadingZeros(HInvoke* invoke) {
  GenLeadingZeros(GetAssembler(), codegen_, invoke, /* is_long= */ true);
}

static void CreateTrailingZeroLocations(ArenaAllocator* allocator, HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::Any());
  locations->SetOut(Location::RequiresRegister());
}

static void GenTrailingZeros(X86_64Assembler* assembler,
                             CodeGeneratorX86_64* codegen,
                             HInvoke* invoke, bool is_long) {
  LocationSummary* locations = invoke->GetLocations();
  Location src = locations->InAt(0);
  CpuRegister out = locations->Out().AsRegister<CpuRegister>();

  int zero_value_result = is_long ? 64 : 32;
  if (invoke->InputAt(0)->IsConstant()) {
    // Evaluate this at compile time.
    int64_t value = Int64FromConstant(invoke->InputAt(0)->AsConstant());
    if (value == 0) {
      value = zero_value_result;
    } else {
      value = is_long ? CTZ(static_cast<uint64_t>(value)) : CTZ(static_cast<uint32_t>(value));
    }
    codegen->Load32BitValue(out, value);
    return;
  }

  // Handle the non-constant cases.
  if (src.IsRegister()) {
    if (is_long) {
      __ bsfq(out, src.AsRegister<CpuRegister>());
    } else {
      __ bsfl(out, src.AsRegister<CpuRegister>());
    }
  } else if (is_long) {
    DCHECK(src.IsDoubleStackSlot());
    __ bsfq(out, Address(CpuRegister(RSP), src.GetStackIndex()));
  } else {
    DCHECK(src.IsStackSlot());
    __ bsfl(out, Address(CpuRegister(RSP), src.GetStackIndex()));
  }

  // BSF sets ZF if the input was zero, and the output is undefined.
  NearLabel done;
  __ j(kNotEqual, &done);

  // Fix the zero case with the expected result.
  __ movl(out, Immediate(zero_value_result));

  __ Bind(&done);
}

void IntrinsicLocationsBuilderX86_64::VisitIntegerNumberOfTrailingZeros(HInvoke* invoke) {
  CreateTrailingZeroLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitIntegerNumberOfTrailingZeros(HInvoke* invoke) {
  GenTrailingZeros(GetAssembler(), codegen_, invoke, /* is_long= */ false);
}

void IntrinsicLocationsBuilderX86_64::VisitLongNumberOfTrailingZeros(HInvoke* invoke) {
  CreateTrailingZeroLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86_64::VisitLongNumberOfTrailingZeros(HInvoke* invoke) {
  GenTrailingZeros(GetAssembler(), codegen_, invoke, /* is_long= */ true);
}

#define VISIT_INTRINSIC(name, low, high, type, start_index) \
  void IntrinsicLocationsBuilderX86_64::Visit ##name ##ValueOf(HInvoke* invoke) { \
    InvokeRuntimeCallingConvention calling_convention; \
    IntrinsicVisitor::ComputeValueOfLocations( \
        invoke, \
        codegen_, \
        low, \
        high - low + 1, \
        Location::RegisterLocation(RAX), \
        Location::RegisterLocation(calling_convention.GetRegisterAt(0))); \
  } \
  void IntrinsicCodeGeneratorX86_64::Visit ##name ##ValueOf(HInvoke* invoke) { \
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

template <typename T>
static void Store(X86_64Assembler* assembler,
                  DataType::Type primitive_type,
                  const Address& address,
                  const T& operand) {
  switch (primitive_type) {
    case DataType::Type::kInt8:
    case DataType::Type::kUint8: {
      __ movb(address, operand);
      break;
    }
    case DataType::Type::kInt16:
    case DataType::Type::kUint16: {
      __ movw(address, operand);
      break;
    }
    case DataType::Type::kInt32: {
      __ movl(address, operand);
      break;
    }
    default: {
      LOG(FATAL) << "Unrecognized ValueOf type " << primitive_type;
    }
  }
}

void IntrinsicCodeGeneratorX86_64::HandleValueOf(HInvoke* invoke,
                                                 const IntrinsicVisitor::ValueOfInfo& info,
                                                 DataType::Type type) {
  LocationSummary* locations = invoke->GetLocations();
  X86_64Assembler* assembler = GetAssembler();

  CpuRegister out = locations->Out().AsRegister<CpuRegister>();
  InvokeRuntimeCallingConvention calling_convention;
  CpuRegister argument = CpuRegister(calling_convention.GetRegisterAt(0));
  auto allocate_instance = [&]() {
    codegen_->LoadIntrinsicDeclaringClass(argument, invoke);
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
      // TODO: If we JIT, we could allocate the boxed value now, and store it in the
      // JIT object table.
      allocate_instance();
      Store(assembler, type, Address(out, info.value_offset), Immediate(value));
    }
  } else {
    DCHECK(locations->CanCall());
    CpuRegister in = locations->InAt(0).AsRegister<CpuRegister>();
    // Check bounds of our cache.
    __ leal(out, Address(in, -info.low));
    __ cmpl(out, Immediate(info.length));
    NearLabel allocate, done;
    __ j(kAboveEqual, &allocate);
    // If the value is within the bounds, load the boxed value directly from the array.
    DCHECK_NE(out.AsRegister(), argument.AsRegister());
    codegen_->LoadBootImageAddress(argument, info.array_data_boot_image_reference);
    static_assert((1u << TIMES_4) == sizeof(mirror::HeapReference<mirror::Object>),
                  "Check heap reference size.");
    __ movl(out, Address(argument, out, TIMES_4, 0));
    __ MaybeUnpoisonHeapReference(out);
    __ jmp(&done);
    __ Bind(&allocate);
    // Otherwise allocate and initialize a new object.
    allocate_instance();
    Store(assembler, type, Address(out, info.value_offset), in);
    __ Bind(&done);
  }
}

void IntrinsicLocationsBuilderX86_64::VisitReferenceGetReferent(HInvoke* invoke) {
  IntrinsicVisitor::CreateReferenceGetReferentLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitReferenceGetReferent(HInvoke* invoke) {
  X86_64Assembler* assembler = GetAssembler();
  LocationSummary* locations = invoke->GetLocations();

  Location obj = locations->InAt(0);
  Location out = locations->Out();

  SlowPathCode* slow_path = new (GetAllocator()) IntrinsicSlowPathX86_64(invoke);
  codegen_->AddSlowPath(slow_path);

  if (codegen_->EmitReadBarrier()) {
    // Check self->GetWeakRefAccessEnabled().
    ThreadOffset64 offset = Thread::WeakRefAccessEnabledOffset<kX86_64PointerSize>();
    __ gs()->cmpl(Address::Absolute(offset, /* no_rip= */ true),
                  Immediate(enum_cast<int32_t>(WeakRefAccessState::kVisiblyEnabled)));
    __ j(kNotEqual, slow_path->GetEntryLabel());
  }

  // Load the java.lang.ref.Reference class, use the output register as a temporary.
  codegen_->LoadIntrinsicDeclaringClass(out.AsRegister<CpuRegister>(), invoke);

  // Check static fields java.lang.ref.Reference.{disableIntrinsic,slowPathEnabled} together.
  MemberOffset disable_intrinsic_offset = IntrinsicVisitor::GetReferenceDisableIntrinsicOffset();
  DCHECK_ALIGNED(disable_intrinsic_offset.Uint32Value(), 2u);
  DCHECK_EQ(disable_intrinsic_offset.Uint32Value() + 1u,
            IntrinsicVisitor::GetReferenceSlowPathEnabledOffset().Uint32Value());
  __ cmpw(Address(out.AsRegister<CpuRegister>(), disable_intrinsic_offset.Uint32Value()),
          Immediate(0));
  __ j(kNotEqual, slow_path->GetEntryLabel());

  // Load the value from the field.
  uint32_t referent_offset = mirror::Reference::ReferentOffset().Uint32Value();
  if (codegen_->EmitBakerReadBarrier()) {
    codegen_->GenerateFieldLoadWithBakerReadBarrier(invoke,
                                                    out,
                                                    obj.AsRegister<CpuRegister>(),
                                                    referent_offset,
                                                    /*needs_null_check=*/ true);
    // Note that the fence is a no-op, thanks to the x86-64 memory model.
    codegen_->GenerateMemoryBarrier(MemBarrierKind::kLoadAny);  // `referent` is volatile.
  } else {
    __ movl(out.AsRegister<CpuRegister>(), Address(obj.AsRegister<CpuRegister>(), referent_offset));
    codegen_->MaybeRecordImplicitNullCheck(invoke);
    // Note that the fence is a no-op, thanks to the x86-64 memory model.
    codegen_->GenerateMemoryBarrier(MemBarrierKind::kLoadAny);  // `referent` is volatile.
    codegen_->MaybeGenerateReadBarrierSlow(invoke, out, out, obj, referent_offset);
  }
  __ Bind(slow_path->GetExitLabel());
}

void IntrinsicLocationsBuilderX86_64::VisitReferenceRefersTo(HInvoke* invoke) {
  IntrinsicVisitor::CreateReferenceRefersToLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitReferenceRefersTo(HInvoke* invoke) {
  X86_64Assembler* assembler = GetAssembler();
  LocationSummary* locations = invoke->GetLocations();

  CpuRegister obj = locations->InAt(0).AsRegister<CpuRegister>();
  CpuRegister other = locations->InAt(1).AsRegister<CpuRegister>();
  CpuRegister out = locations->Out().AsRegister<CpuRegister>();

  uint32_t referent_offset = mirror::Reference::ReferentOffset().Uint32Value();
  uint32_t monitor_offset = mirror::Object::MonitorOffset().Int32Value();

  __ movl(out, Address(obj, referent_offset));
  codegen_->MaybeRecordImplicitNullCheck(invoke);
  __ MaybeUnpoisonHeapReference(out);
  // Note that the fence is a no-op, thanks to the x86-64 memory model.
  codegen_->GenerateMemoryBarrier(MemBarrierKind::kLoadAny);  // `referent` is volatile.

  __ cmpl(out, other);

  if (codegen_->EmitReadBarrier()) {
    DCHECK(kUseBakerReadBarrier);

    NearLabel calculate_result;
    __ j(kEqual, &calculate_result);  // ZF set if taken.

    // Check if the loaded reference is null in a way that leaves ZF clear for null.
    __ cmpl(out, Immediate(1));
    __ j(kBelow, &calculate_result);  // ZF clear if taken.

    // For correct memory visibility, we need a barrier before loading the lock word
    // but we already have the barrier emitted for volatile load above which is sufficient.

    // Load the lockword and check if it is a forwarding address.
    static_assert(LockWord::kStateShift == 30u);
    static_assert(LockWord::kStateForwardingAddress == 3u);
    __ movl(out, Address(out, monitor_offset));
    __ cmpl(out, Immediate(static_cast<int32_t>(0xc0000000)));
    __ j(kBelow, &calculate_result);   // ZF clear if taken.

    // Extract the forwarding address and compare with `other`.
    __ shll(out, Immediate(LockWord::kForwardingAddressShift));
    __ cmpl(out, other);

    __ Bind(&calculate_result);
  }

  // Convert ZF into the Boolean result.
  __ setcc(kEqual, out);
  __ movzxb(out, out);
}

void IntrinsicLocationsBuilderX86_64::VisitThreadInterrupted(HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator_) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetOut(Location::RequiresRegister());
}

void IntrinsicCodeGeneratorX86_64::VisitThreadInterrupted(HInvoke* invoke) {
  X86_64Assembler* assembler = GetAssembler();
  CpuRegister out = invoke->GetLocations()->Out().AsRegister<CpuRegister>();
  Address address = Address::Absolute
      (Thread::InterruptedOffset<kX86_64PointerSize>().Int32Value(), /* no_rip= */ true);
  NearLabel done;
  __ gs()->movl(out, address);
  __ testl(out, out);
  __ j(kEqual, &done);
  __ gs()->movl(address, Immediate(0));
  codegen_->MemoryFence();
  __ Bind(&done);
}

void IntrinsicLocationsBuilderX86_64::VisitReachabilityFence(HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator_) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::Any());
}

void IntrinsicCodeGeneratorX86_64::VisitReachabilityFence([[maybe_unused]] HInvoke* invoke) {}

static void CreateDivideUnsignedLocations(HInvoke* invoke, ArenaAllocator* allocator) {
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kCallOnSlowPath, kIntrinsified);
  locations->SetInAt(0, Location::RegisterLocation(RAX));
  locations->SetInAt(1, Location::RequiresRegister());
  locations->SetOut(Location::SameAsFirstInput());
  // Intel uses edx:eax as the dividend.
  locations->AddTemp(Location::RegisterLocation(RDX));
}

static void GenerateDivideUnsigned(HInvoke* invoke,
                                   CodeGeneratorX86_64* codegen,
                                   DataType::Type data_type) {
  LocationSummary* locations = invoke->GetLocations();
  Location out = locations->Out();
  Location first = locations->InAt(0);
  Location second = locations->InAt(1);
  CpuRegister rdx = locations->GetTemp(0).AsRegister<CpuRegister>();
  CpuRegister second_reg = second.AsRegister<CpuRegister>();

  DCHECK_EQ(RAX, first.AsRegister<Register>());
  DCHECK_EQ(RAX, out.AsRegister<Register>());
  DCHECK_EQ(RDX, rdx.AsRegister());

  // We check if the divisor is zero and bail to the slow path to handle if so.
  auto* slow_path = new (codegen->GetScopedAllocator()) IntrinsicSlowPathX86_64(invoke);
  codegen->AddSlowPath(slow_path);

  X86_64Assembler* assembler = codegen->GetAssembler();
  if (data_type == DataType::Type::kInt32) {
    __ testl(second_reg, second_reg);
    __ j(kEqual, slow_path->GetEntryLabel());
    __ xorl(rdx, rdx);
    __ divl(second_reg);
  } else {
    DCHECK(data_type == DataType::Type::kInt64);
    __ testq(second_reg, second_reg);
    __ j(kEqual, slow_path->GetEntryLabel());
    __ xorq(rdx, rdx);
    __ divq(second_reg);
  }
  __ Bind(slow_path->GetExitLabel());
}

void IntrinsicLocationsBuilderX86_64::VisitIntegerDivideUnsigned(HInvoke* invoke) {
  CreateDivideUnsignedLocations(invoke, allocator_);
}

void IntrinsicCodeGeneratorX86_64::VisitIntegerDivideUnsigned(HInvoke* invoke) {
  GenerateDivideUnsigned(invoke, codegen_, DataType::Type::kInt32);
}

void IntrinsicLocationsBuilderX86_64::VisitLongDivideUnsigned(HInvoke* invoke) {
  CreateDivideUnsignedLocations(invoke, allocator_);
}

void IntrinsicCodeGeneratorX86_64::VisitLongDivideUnsigned(HInvoke* invoke) {
  GenerateDivideUnsigned(invoke, codegen_, DataType::Type::kInt64);
}

void IntrinsicLocationsBuilderX86_64::VisitMathMultiplyHigh(HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator_) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::RegisterLocation(RAX));
  locations->SetInAt(1, Location::RequiresRegister());
  locations->SetOut(Location::RegisterLocation(RDX));
  locations->AddTemp(Location::RegisterLocation(RAX));
}

void IntrinsicCodeGeneratorX86_64::VisitMathMultiplyHigh(HInvoke* invoke) {
  X86_64Assembler* assembler = GetAssembler();
  LocationSummary* locations = invoke->GetLocations();

  CpuRegister y = locations->InAt(1).AsRegister<CpuRegister>();

  DCHECK_EQ(locations->InAt(0).AsRegister<Register>(), RAX);
  DCHECK_EQ(locations->Out().AsRegister<Register>(), RDX);

  __ imulq(y);
}

class VarHandleSlowPathX86_64 : public IntrinsicSlowPathX86_64 {
 public:
  explicit VarHandleSlowPathX86_64(HInvoke* invoke)
      : IntrinsicSlowPathX86_64(invoke) {
  }

  void SetVolatile(bool is_volatile) {
    is_volatile_ = is_volatile;
  }

  void SetAtomic(bool is_atomic) {
    is_atomic_ = is_atomic;
  }

  void SetNeedAnyStoreBarrier(bool need_any_store_barrier) {
    need_any_store_barrier_ = need_any_store_barrier;
  }

  void SetNeedAnyAnyBarrier(bool need_any_any_barrier) {
    need_any_any_barrier_ = need_any_any_barrier;
  }

  void SetGetAndUpdateOp(GetAndUpdateOp get_and_update_op) {
    get_and_update_op_ = get_and_update_op;
  }

  Label* GetByteArrayViewCheckLabel() {
    return &byte_array_view_check_label_;
  }

  Label* GetNativeByteOrderLabel() {
    return &native_byte_order_label_;
  }

  void EmitNativeCode(CodeGenerator* codegen) override {
    if (GetByteArrayViewCheckLabel()->IsLinked()) {
      EmitByteArrayViewCode(down_cast<CodeGeneratorX86_64*>(codegen));
    }
    IntrinsicSlowPathX86_64::EmitNativeCode(codegen);
  }

 private:
  HInvoke* GetInvoke() const {
    return GetInstruction()->AsInvoke();
  }

  mirror::VarHandle::AccessModeTemplate GetAccessModeTemplate() const {
    return mirror::VarHandle::GetAccessModeTemplateByIntrinsic(GetInvoke()->GetIntrinsic());
  }

  void EmitByteArrayViewCode(CodeGeneratorX86_64* codegen);

  Label byte_array_view_check_label_;
  Label native_byte_order_label_;

  // Arguments forwarded to specific methods.
  bool is_volatile_;
  bool is_atomic_;
  bool need_any_store_barrier_;
  bool need_any_any_barrier_;
  GetAndUpdateOp get_and_update_op_;
};

static void GenerateMathFma(HInvoke* invoke, CodeGeneratorX86_64* codegen) {
  DCHECK(DataType::IsFloatingPointType(invoke->GetType()));
  X86_64Assembler* assembler = codegen->GetAssembler();
  LocationSummary* locations = invoke->GetLocations();
  DCHECK(locations->InAt(0).Equals(locations->Out()));
  XmmRegister left = locations->InAt(0).AsFpuRegister<XmmRegister>();
  XmmRegister right = locations->InAt(1).AsFpuRegister<XmmRegister>();
  XmmRegister accumulator = locations->InAt(2).AsFpuRegister<XmmRegister>();
  if (invoke->GetType() == DataType::Type::kFloat32) {
    __ vfmadd213ss(left, right, accumulator);
  } else {
    DCHECK_EQ(invoke->GetType(), DataType::Type::kFloat64);
    __ vfmadd213sd(left, right, accumulator);
  }
}

void IntrinsicCodeGeneratorX86_64::VisitMathFmaDouble(HInvoke* invoke) {
  DCHECK(codegen_->GetInstructionSetFeatures().HasAVX2());
  GenerateMathFma(invoke, codegen_);
}

void IntrinsicLocationsBuilderX86_64::VisitMathFmaDouble(HInvoke* invoke) {
  if (codegen_->GetInstructionSetFeatures().HasAVX2()) {
    CreateFPFPFPToFPCallLocations(allocator_, invoke);
  }
}

void IntrinsicCodeGeneratorX86_64::VisitMathFmaFloat(HInvoke* invoke) {
  DCHECK(codegen_->GetInstructionSetFeatures().HasAVX2());
  GenerateMathFma(invoke, codegen_);
}

void IntrinsicLocationsBuilderX86_64::VisitMathFmaFloat(HInvoke* invoke) {
  if (codegen_->GetInstructionSetFeatures().HasAVX2()) {
    CreateFPFPFPToFPCallLocations(allocator_, invoke);
  }
}

// Generate subtype check without read barriers.
static void GenerateSubTypeObjectCheckNoReadBarrier(CodeGeneratorX86_64* codegen,
                                                    VarHandleSlowPathX86_64* slow_path,
                                                    CpuRegister object,
                                                    CpuRegister temp,
                                                    Address type_address,
                                                    bool object_can_be_null = true) {
  X86_64Assembler* assembler = codegen->GetAssembler();

  const MemberOffset class_offset = mirror::Object::ClassOffset();
  const MemberOffset super_class_offset = mirror::Class::SuperClassOffset();

  NearLabel check_type_compatibility, type_matched;

  // If the object is null, there is no need to check the type
  if (object_can_be_null) {
    __ testl(object, object);
    __ j(kZero, &type_matched);
  }

  // Do not unpoison for in-memory comparison.
  // We deliberately avoid the read barrier, letting the slow path handle the false negatives.
  __ movl(temp, Address(object, class_offset));
  __ Bind(&check_type_compatibility);
  __ cmpl(temp, type_address);
  __ j(kEqual, &type_matched);
  // Load the super class.
  __ MaybeUnpoisonHeapReference(temp);
  __ movl(temp, Address(temp, super_class_offset));
  // If the super class is null, we reached the root of the hierarchy without a match.
  // We let the slow path handle uncovered cases (e.g. interfaces).
  __ testl(temp, temp);
  __ j(kEqual, slow_path->GetEntryLabel());
  __ jmp(&check_type_compatibility);
  __ Bind(&type_matched);
}

// Check access mode and the primitive type from VarHandle.varType.
// Check reference arguments against the VarHandle.varType; for references this is a subclass
// check without read barrier, so it can have false negatives which we handle in the slow path.
static void GenerateVarHandleAccessModeAndVarTypeChecks(HInvoke* invoke,
                                                        CodeGeneratorX86_64* codegen,
                                                        VarHandleSlowPathX86_64* slow_path,
                                                        DataType::Type type) {
  X86_64Assembler* assembler = codegen->GetAssembler();

  LocationSummary* locations = invoke->GetLocations();
  CpuRegister varhandle = locations->InAt(0).AsRegister<CpuRegister>();
  CpuRegister temp = locations->GetTemp(0).AsRegister<CpuRegister>();

  mirror::VarHandle::AccessMode access_mode =
      mirror::VarHandle::GetAccessModeByIntrinsic(invoke->GetIntrinsic());
  Primitive::Type primitive_type = DataTypeToPrimitive(type);

  const MemberOffset var_type_offset = mirror::VarHandle::VarTypeOffset();
  const MemberOffset access_mode_bit_mask_offset = mirror::VarHandle::AccessModesBitMaskOffset();
  const MemberOffset primitive_type_offset = mirror::Class::PrimitiveTypeOffset();

  // Check that the operation is permitted.
  __ testl(Address(varhandle, access_mode_bit_mask_offset),
           Immediate(1u << static_cast<uint32_t>(access_mode)));
  __ j(kZero, slow_path->GetEntryLabel());

  // For primitive types, we do not need a read barrier when loading a reference only for loading
  // constant field through the reference. For reference types, we deliberately avoid the read
  // barrier, letting the slow path handle the false negatives.
  __ movl(temp, Address(varhandle, var_type_offset));
  __ MaybeUnpoisonHeapReference(temp);

  // Check the varType.primitiveType field against the type we're trying to use.
  __ cmpw(Address(temp, primitive_type_offset), Immediate(static_cast<uint16_t>(primitive_type)));
  __ j(kNotEqual, slow_path->GetEntryLabel());

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
        CpuRegister arg_reg = invoke->GetLocations()->InAt(arg_index).AsRegister<CpuRegister>();
        Address type_addr(varhandle, var_type_offset);
        GenerateSubTypeObjectCheckNoReadBarrier(codegen, slow_path, arg_reg, temp, type_addr);
      }
    }
  }
}

static void GenerateVarHandleStaticFieldCheck(HInvoke* invoke,
                                              CodeGeneratorX86_64* codegen,
                                              VarHandleSlowPathX86_64* slow_path) {
  X86_64Assembler* assembler = codegen->GetAssembler();

  LocationSummary* locations = invoke->GetLocations();
  CpuRegister varhandle = locations->InAt(0).AsRegister<CpuRegister>();

  const MemberOffset coordinate_type0_offset = mirror::VarHandle::CoordinateType0Offset();

  // Check that the VarHandle references a static field by checking that coordinateType0 == null.
  // Do not emit read barrier (or unpoison the reference) for comparing to null.
  __ cmpl(Address(varhandle, coordinate_type0_offset), Immediate(0));
  __ j(kNotEqual, slow_path->GetEntryLabel());
}

static void GenerateVarHandleInstanceFieldChecks(HInvoke* invoke,
                                                 CodeGeneratorX86_64* codegen,
                                                 VarHandleSlowPathX86_64* slow_path) {
  VarHandleOptimizations optimizations(invoke);
  X86_64Assembler* assembler = codegen->GetAssembler();

  LocationSummary* locations = invoke->GetLocations();
  CpuRegister varhandle = locations->InAt(0).AsRegister<CpuRegister>();
  CpuRegister object = locations->InAt(1).AsRegister<CpuRegister>();
  CpuRegister temp = locations->GetTemp(0).AsRegister<CpuRegister>();

  const MemberOffset coordinate_type0_offset = mirror::VarHandle::CoordinateType0Offset();
  const MemberOffset coordinate_type1_offset = mirror::VarHandle::CoordinateType1Offset();

  // Null-check the object.
  if (!optimizations.GetSkipObjectNullCheck()) {
    __ testl(object, object);
    __ j(kZero, slow_path->GetEntryLabel());
  }

  if (!optimizations.GetUseKnownBootImageVarHandle()) {
    // Check that the VarHandle references an instance field by checking that
    // coordinateType1 == null. coordinateType0 should be not null, but this is handled by the
    // type compatibility check with the source object's type, which will fail for null.
    __ cmpl(Address(varhandle, coordinate_type1_offset), Immediate(0));
    __ j(kNotEqual, slow_path->GetEntryLabel());

    // Check that the object has the correct type.
    // We deliberately avoid the read barrier, letting the slow path handle the false negatives.
    GenerateSubTypeObjectCheckNoReadBarrier(codegen,
                                            slow_path,
                                            object,
                                            temp,
                                            Address(varhandle, coordinate_type0_offset),
                                            /*object_can_be_null=*/ false);
  }
}

static void GenerateVarHandleArrayChecks(HInvoke* invoke,
                                         CodeGeneratorX86_64* codegen,
                                         VarHandleSlowPathX86_64* slow_path) {
  VarHandleOptimizations optimizations(invoke);
  X86_64Assembler* assembler = codegen->GetAssembler();
  LocationSummary* locations = invoke->GetLocations();

  CpuRegister varhandle = locations->InAt(0).AsRegister<CpuRegister>();
  CpuRegister object = locations->InAt(1).AsRegister<CpuRegister>();
  CpuRegister index = locations->InAt(2).AsRegister<CpuRegister>();
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
    __ testl(object, object);
    __ j(kZero, slow_path->GetEntryLabel());
  }

  CpuRegister temp = locations->GetTemp(0).AsRegister<CpuRegister>();

  // Check that the VarHandle references an array, byte array view or ByteBuffer by checking
  // that coordinateType1 != null. If that's true, coordinateType1 shall be int.class and
  // coordinateType0 shall not be null but we do not explicitly verify that.
  // No need for read barrier or unpoisoning of coordinateType1 for comparison with null.
  __ cmpl(Address(varhandle, coordinate_type1_offset.Int32Value()), Immediate(0));
  __ j(kEqual, slow_path->GetEntryLabel());

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
  __ movl(temp, Address(object, class_offset.Int32Value()));
  __ cmpl(temp, Address(varhandle, coordinate_type0_offset.Int32Value()));
  __ j(kNotEqual, slow_path->GetEntryLabel());

  // Check that the coordinateType0 is an array type. We do not need a read barrier
  // for loading constant reference fields (or chains of them) for comparison with null,
  // nor for finally loading a constant primitive field (primitive type) below.
  codegen->GetAssembler()->MaybeUnpoisonHeapReference(temp);
  __ movl(temp, Address(temp, component_type_offset.Int32Value()));
  codegen->GetAssembler()->MaybeUnpoisonHeapReference(temp);
  __ testl(temp, temp);
  __ j(kZero, slow_path->GetEntryLabel());

  // Check that the array component type matches the primitive type.
  Label* slow_path_label;
  if (primitive_type == Primitive::kPrimNot) {
    slow_path_label = slow_path->GetEntryLabel();
  } else {
    // With the exception of `kPrimNot` (handled above), `kPrimByte` and `kPrimBoolean`,
    // we shall check for a byte array view in the slow path.
    // The check requires the ByteArrayViewVarHandle.class to be in the boot image,
    // so we cannot emit that if we're JITting without boot image.
    bool boot_image_available =
        codegen->GetCompilerOptions().IsBootImage() ||
        !Runtime::Current()->GetHeap()->GetBootImageSpaces().empty();
    bool can_be_view = (DataType::Size(value_type) != 1u) && boot_image_available;
    slow_path_label =
        can_be_view ? slow_path->GetByteArrayViewCheckLabel() : slow_path->GetEntryLabel();
  }
  __ cmpw(Address(temp, primitive_type_offset), Immediate(static_cast<uint16_t>(primitive_type)));
  __ j(kNotEqual, slow_path_label);

  // Check for array index out of bounds.
  __ cmpl(index, Address(object, array_length_offset.Int32Value()));
  __ j(kAboveEqual, slow_path->GetEntryLabel());
}

static void GenerateVarHandleCoordinateChecks(HInvoke* invoke,
                                              CodeGeneratorX86_64* codegen,
                                              VarHandleSlowPathX86_64* slow_path) {
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

static VarHandleSlowPathX86_64* GenerateVarHandleChecks(HInvoke* invoke,
                                                        CodeGeneratorX86_64* codegen,
                                                        DataType::Type type) {
  size_t expected_coordinates_count = GetExpectedVarHandleCoordinatesCount(invoke);
  VarHandleOptimizations optimizations(invoke);
  if (optimizations.GetUseKnownBootImageVarHandle()) {
    DCHECK_NE(expected_coordinates_count, 2u);
    if (expected_coordinates_count == 0u || optimizations.GetSkipObjectNullCheck()) {
      return nullptr;
    }
  }

  VarHandleSlowPathX86_64* slow_path =
      new (codegen->GetScopedAllocator()) VarHandleSlowPathX86_64(invoke);
  codegen->AddSlowPath(slow_path);

  if (!optimizations.GetUseKnownBootImageVarHandle()) {
    GenerateVarHandleAccessModeAndVarTypeChecks(invoke, codegen, slow_path, type);
  }
  GenerateVarHandleCoordinateChecks(invoke, codegen, slow_path);

  return slow_path;
}

struct VarHandleTarget {
  Register object;  // The object holding the value to operate on.
  Register offset;  // The offset of the value to operate on.
};

static VarHandleTarget GetVarHandleTarget(HInvoke* invoke) {
  size_t expected_coordinates_count = GetExpectedVarHandleCoordinatesCount(invoke);
  LocationSummary* locations = invoke->GetLocations();

  VarHandleTarget target;
  // The temporary allocated for loading the offset.
  target.offset = locations->GetTemp(0).AsRegister<CpuRegister>().AsRegister();
  // The reference to the object that holds the value to operate on.
  target.object = (expected_coordinates_count == 0u)
      ? locations->GetTemp(1).AsRegister<CpuRegister>().AsRegister()
      : locations->InAt(1).AsRegister<CpuRegister>().AsRegister();
  return target;
}

static void GenerateVarHandleTarget(HInvoke* invoke,
                                    const VarHandleTarget& target,
                                    CodeGeneratorX86_64* codegen) {
  LocationSummary* locations = invoke->GetLocations();
  X86_64Assembler* assembler = codegen->GetAssembler();
  size_t expected_coordinates_count = GetExpectedVarHandleCoordinatesCount(invoke);

  CpuRegister varhandle = locations->InAt(0).AsRegister<CpuRegister>();

  if (expected_coordinates_count <= 1u) {
    if (VarHandleOptimizations(invoke).GetUseKnownBootImageVarHandle()) {
      ScopedObjectAccess soa(Thread::Current());
      ArtField* target_field = GetBootImageVarHandleField(invoke);
      if (expected_coordinates_count == 0u) {
        ObjPtr<mirror::Class> declaring_class = target_field->GetDeclaringClass();
        __ movl(CpuRegister(target.object),
                Address::Absolute(CodeGeneratorX86_64::kPlaceholder32BitOffset, /*no_rip=*/ false));
        if (Runtime::Current()->GetHeap()->ObjectIsInBootImageSpace(declaring_class)) {
          codegen->RecordBootImageRelRoPatch(CodeGenerator::GetBootImageOffset(declaring_class));
        } else {
          codegen->RecordBootImageTypePatch(declaring_class->GetDexFile(),
                                            declaring_class->GetDexTypeIndex());
        }
      }
      __ movl(CpuRegister(target.offset), Immediate(target_field->GetOffset().Uint32Value()));
    } else {
      // For static fields, we need to fill the `target.object` with the declaring class,
      // so we can use `target.object` as temporary for the `ArtField*`. For instance fields,
      // we do not need the declaring class, so we can forget the `ArtField*` when
      // we load the `target.offset`, so use the `target.offset` to hold the `ArtField*`.
      CpuRegister field((expected_coordinates_count == 0) ? target.object : target.offset);

      const MemberOffset art_field_offset = mirror::FieldVarHandle::ArtFieldOffset();
      const MemberOffset offset_offset = ArtField::OffsetOffset();

      // Load the ArtField*, the offset and, if needed, declaring class.
      __ movq(field, Address(varhandle, art_field_offset));
      __ movl(CpuRegister(target.offset), Address(field, offset_offset));
      if (expected_coordinates_count == 0u) {
        InstructionCodeGeneratorX86_64* instr_codegen = codegen->GetInstructionCodegen();
        instr_codegen->GenerateGcRootFieldLoad(invoke,
                                               Location::RegisterLocation(target.object),
                                               Address(field, ArtField::DeclaringClassOffset()),
                                               /*fixup_label=*/nullptr,
                                               codegen->GetCompilerReadBarrierOption());
      }
    }
  } else {
    DCHECK_EQ(expected_coordinates_count, 2u);

    DataType::Type value_type =
        GetVarHandleExpectedValueType(invoke, /*expected_coordinates_count=*/ 2u);
    ScaleFactor scale = CodeGenerator::ScaleFactorForType(value_type);
    MemberOffset data_offset = mirror::Array::DataOffset(DataType::Size(value_type));
    CpuRegister index = locations->InAt(2).AsRegister<CpuRegister>();

    // The effect of LEA is `target.offset = index * scale + data_offset`.
    __ leal(CpuRegister(target.offset), Address(index, scale, data_offset.Int32Value()));
  }
}

static bool HasVarHandleIntrinsicImplementation(HInvoke* invoke, CodeGeneratorX86_64* codegen) {
  // The only supported read barrier implementation is the Baker-style read barriers.
  if (codegen->EmitNonBakerReadBarrier()) {
    return false;
  }

  VarHandleOptimizations optimizations(invoke);
  if (optimizations.GetDoNotIntrinsify()) {
    return false;
  }

  size_t expected_coordinates_count = GetExpectedVarHandleCoordinatesCount(invoke);
  DCHECK_LE(expected_coordinates_count, 2u);  // Filtered by the `DoNotIntrinsify` flag above.
  return true;
}

static LocationSummary* CreateVarHandleCommonLocations(HInvoke* invoke) {
  size_t expected_coordinates_count = GetExpectedVarHandleCoordinatesCount(invoke);
  ArenaAllocator* allocator = invoke->GetBlock()->GetGraph()->GetAllocator();
  LocationSummary* locations = new (allocator) LocationSummary(
      invoke, LocationSummary::kCallOnSlowPath, kIntrinsified);

  locations->SetInAt(0, Location::RequiresRegister());
  // Require coordinates in registers. These are the object holding the value
  // to operate on (except for static fields) and index (for arrays and views).
  for (size_t i = 0; i != expected_coordinates_count; ++i) {
    locations->SetInAt(/* VarHandle object */ 1u + i, Location::RequiresRegister());
  }

  uint32_t arguments_start = /* VarHandle object */ 1u + expected_coordinates_count;
  uint32_t number_of_arguments = invoke->GetNumberOfArguments();
  for (size_t arg_index = arguments_start; arg_index != number_of_arguments; ++arg_index) {
    HInstruction* arg = invoke->InputAt(arg_index);
    if (DataType::IsFloatingPointType(arg->GetType())) {
      locations->SetInAt(arg_index, Location::FpuRegisterOrConstant(arg));
    } else {
      locations->SetInAt(arg_index, Location::RegisterOrConstant(arg));
    }
  }

  // Add a temporary for offset.
  locations->AddTemp(Location::RequiresRegister());

  if (expected_coordinates_count == 0u) {
    // Add a temporary to hold the declaring class.
    locations->AddTemp(Location::RequiresRegister());
  }

  return locations;
}

static void CreateVarHandleGetLocations(HInvoke* invoke, CodeGeneratorX86_64* codegen) {
  if (!HasVarHandleIntrinsicImplementation(invoke, codegen)) {
    return;
  }

  LocationSummary* locations = CreateVarHandleCommonLocations(invoke);
  if (DataType::IsFloatingPointType(invoke->GetType())) {
    locations->SetOut(Location::RequiresFpuRegister());
  } else {
    locations->SetOut(Location::RequiresRegister());
  }
}

static void GenerateVarHandleGet(HInvoke* invoke,
                                 CodeGeneratorX86_64* codegen,
                                 bool byte_swap = false) {
  DataType::Type type = invoke->GetType();
  DCHECK_NE(type, DataType::Type::kVoid);

  LocationSummary* locations = invoke->GetLocations();
  X86_64Assembler* assembler = codegen->GetAssembler();

  VarHandleTarget target = GetVarHandleTarget(invoke);
  VarHandleSlowPathX86_64* slow_path = nullptr;
  if (!byte_swap) {
    slow_path = GenerateVarHandleChecks(invoke, codegen, type);
    GenerateVarHandleTarget(invoke, target, codegen);
    if (slow_path != nullptr) {
      __ Bind(slow_path->GetNativeByteOrderLabel());
    }
  }

  // Load the value from the field
  Address src(CpuRegister(target.object), CpuRegister(target.offset), TIMES_1, 0);
  Location out = locations->Out();

  if (type == DataType::Type::kReference) {
    if (codegen->EmitReadBarrier()) {
      DCHECK(kUseBakerReadBarrier);
      codegen->GenerateReferenceLoadWithBakerReadBarrier(
          invoke, out, CpuRegister(target.object), src, /* needs_null_check= */ false);
    } else {
      __ movl(out.AsRegister<CpuRegister>(), src);
      __ MaybeUnpoisonHeapReference(out.AsRegister<CpuRegister>());
    }
    DCHECK(!byte_swap);
  } else {
    codegen->LoadFromMemoryNoReference(type, out, src);
    if (byte_swap) {
      CpuRegister temp = locations->GetTemp(0).AsRegister<CpuRegister>();
      codegen->GetInstructionCodegen()->Bswap(out, type, &temp);
    }
  }

  if (slow_path != nullptr) {
    DCHECK(!byte_swap);
    __ Bind(slow_path->GetExitLabel());
  }
}

void IntrinsicLocationsBuilderX86_64::VisitVarHandleGet(HInvoke* invoke) {
  CreateVarHandleGetLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitVarHandleGet(HInvoke* invoke) {
  GenerateVarHandleGet(invoke, codegen_);
}

void IntrinsicLocationsBuilderX86_64::VisitVarHandleGetAcquire(HInvoke* invoke) {
  CreateVarHandleGetLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitVarHandleGetAcquire(HInvoke* invoke) {
  // VarHandleGetAcquire is the same as VarHandleGet on x86-64 due to the x86 memory model.
  GenerateVarHandleGet(invoke, codegen_);
}

void IntrinsicLocationsBuilderX86_64::VisitVarHandleGetOpaque(HInvoke* invoke) {
  CreateVarHandleGetLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitVarHandleGetOpaque(HInvoke* invoke) {
  // VarHandleGetOpaque is the same as VarHandleGet on x86-64 due to the x86 memory model.
  GenerateVarHandleGet(invoke, codegen_);
}

void IntrinsicLocationsBuilderX86_64::VisitVarHandleGetVolatile(HInvoke* invoke) {
  CreateVarHandleGetLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitVarHandleGetVolatile(HInvoke* invoke) {
  // VarHandleGetVolatile is the same as VarHandleGet on x86-64 due to the x86 memory model.
  GenerateVarHandleGet(invoke, codegen_);
}

static void CreateVarHandleSetLocations(HInvoke* invoke, CodeGeneratorX86_64* codegen) {
  if (!HasVarHandleIntrinsicImplementation(invoke, codegen)) {
    return;
  }

  LocationSummary* locations = CreateVarHandleCommonLocations(invoke);

  // Extra temporary is used for card in MarkGCCard and to move 64-bit constants to memory.
  locations->AddTemp(Location::RequiresRegister());
}

static void GenerateVarHandleSet(HInvoke* invoke,
                                 CodeGeneratorX86_64* codegen,
                                 bool is_volatile,
                                 bool is_atomic,
                                 bool byte_swap = false) {
  X86_64Assembler* assembler = codegen->GetAssembler();

  LocationSummary* locations = invoke->GetLocations();
  const uint32_t last_temp_index = locations->GetTempCount() - 1;

  uint32_t value_index = invoke->GetNumberOfArguments() - 1;
  DataType::Type value_type = GetDataTypeFromShorty(invoke, value_index);

  VarHandleTarget target = GetVarHandleTarget(invoke);
  VarHandleSlowPathX86_64* slow_path = nullptr;
  if (!byte_swap) {
    slow_path = GenerateVarHandleChecks(invoke, codegen, value_type);
    GenerateVarHandleTarget(invoke, target, codegen);
    if (slow_path != nullptr) {
      slow_path->SetVolatile(is_volatile);
      slow_path->SetAtomic(is_atomic);
      __ Bind(slow_path->GetNativeByteOrderLabel());
    }
  }

  switch (invoke->GetIntrinsic()) {
    case Intrinsics::kVarHandleSetRelease:
      codegen->GenerateMemoryBarrier(MemBarrierKind::kAnyStore);
      break;
    case Intrinsics::kVarHandleSetVolatile:
      // setVolatile needs kAnyStore barrier, but HandleFieldSet takes care of that.
      break;
    default:
      // Other intrinsics don't need a barrier.
      break;
  }

  Address dst(CpuRegister(target.object), CpuRegister(target.offset), TIMES_1, 0);

  // Store the value to the field.
  codegen->GetInstructionCodegen()->HandleFieldSet(
      invoke,
      value_index,
      last_temp_index,
      value_type,
      dst,
      CpuRegister(target.object),
      is_volatile,
      is_atomic,
      /*value_can_be_null=*/true,
      byte_swap,
      // Value can be null, and this write barrier is not being relied on for other sets.
      value_type == DataType::Type::kReference ? WriteBarrierKind::kEmitNotBeingReliedOn :
                                                 WriteBarrierKind::kDontEmit);

  // setVolatile needs kAnyAny barrier, but HandleFieldSet takes care of that.

  if (slow_path != nullptr) {
    DCHECK(!byte_swap);
    __ Bind(slow_path->GetExitLabel());
  }
}

void IntrinsicLocationsBuilderX86_64::VisitVarHandleSet(HInvoke* invoke) {
  CreateVarHandleSetLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitVarHandleSet(HInvoke* invoke) {
  GenerateVarHandleSet(invoke, codegen_, /*is_volatile=*/ false, /*is_atomic=*/ true);
}

void IntrinsicLocationsBuilderX86_64::VisitVarHandleSetOpaque(HInvoke* invoke) {
  CreateVarHandleSetLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitVarHandleSetOpaque(HInvoke* invoke) {
  GenerateVarHandleSet(invoke, codegen_, /*is_volatile=*/ false, /*is_atomic=*/ true);
}

void IntrinsicLocationsBuilderX86_64::VisitVarHandleSetRelease(HInvoke* invoke) {
  CreateVarHandleSetLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitVarHandleSetRelease(HInvoke* invoke) {
  GenerateVarHandleSet(invoke, codegen_, /*is_volatile=*/ false, /*is_atomic=*/ true);
}

void IntrinsicLocationsBuilderX86_64::VisitVarHandleSetVolatile(HInvoke* invoke) {
  CreateVarHandleSetLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitVarHandleSetVolatile(HInvoke* invoke) {
  GenerateVarHandleSet(invoke, codegen_, /*is_volatile=*/ true, /*is_atomic=*/ true);
}

static void CreateVarHandleCompareAndSetOrExchangeLocations(HInvoke* invoke,
                                                            CodeGeneratorX86_64* codegen) {
  if (!HasVarHandleIntrinsicImplementation(invoke, codegen)) {
    return;
  }

  uint32_t number_of_arguments = invoke->GetNumberOfArguments();
  uint32_t expected_value_index = number_of_arguments - 2;
  uint32_t new_value_index = number_of_arguments - 1;
  DataType::Type return_type = invoke->GetType();
  DataType::Type expected_type = GetDataTypeFromShorty(invoke, expected_value_index);
  DCHECK_EQ(expected_type, GetDataTypeFromShorty(invoke, new_value_index));

  LocationSummary* locations = CreateVarHandleCommonLocations(invoke);

  if (DataType::IsFloatingPointType(return_type)) {
    locations->SetOut(Location::RequiresFpuRegister());
  } else {
    // Take advantage of the fact that CMPXCHG writes result to RAX.
    locations->SetOut(Location::RegisterLocation(RAX));
  }

  if (DataType::IsFloatingPointType(expected_type)) {
    // RAX is needed to load the expected floating-point value into a register for CMPXCHG.
    locations->AddTemp(Location::RegisterLocation(RAX));
    // Another temporary is needed to load the new floating-point value into a register for CMPXCHG.
    locations->AddTemp(Location::RequiresRegister());
  } else {
    // Ensure that expected value is in RAX, as required by CMPXCHG.
    locations->SetInAt(expected_value_index, Location::RegisterLocation(RAX));
    locations->SetInAt(new_value_index, Location::RequiresRegister());
    if (expected_type == DataType::Type::kReference) {
      // Need two temporaries for MarkGCCard.
      locations->AddTemp(Location::RequiresRegister());
      locations->AddTemp(Location::RequiresRegister());
      if (codegen->EmitReadBarrier()) {
        // Need three temporaries for GenerateReferenceLoadWithBakerReadBarrier.
        DCHECK(kUseBakerReadBarrier);
        locations->AddTemp(Location::RequiresRegister());
      }
    }
    // RAX is clobbered in CMPXCHG, but no need to mark it as temporary as it's the output register.
    DCHECK_EQ(RAX, locations->Out().AsRegister<Register>());
  }
}

static void GenerateVarHandleCompareAndSetOrExchange(HInvoke* invoke,
                                                     CodeGeneratorX86_64* codegen,
                                                     bool is_cmpxchg,
                                                     bool byte_swap = false) {
  DCHECK_IMPLIES(codegen->EmitReadBarrier(), kUseBakerReadBarrier);

  X86_64Assembler* assembler = codegen->GetAssembler();
  LocationSummary* locations = invoke->GetLocations();

  uint32_t number_of_arguments = invoke->GetNumberOfArguments();
  uint32_t expected_value_index = number_of_arguments - 2;
  uint32_t new_value_index = number_of_arguments - 1;
  DataType::Type type = GetDataTypeFromShorty(invoke, expected_value_index);

  VarHandleSlowPathX86_64* slow_path = nullptr;
  VarHandleTarget target = GetVarHandleTarget(invoke);
  if (!byte_swap) {
    slow_path = GenerateVarHandleChecks(invoke, codegen, type);
    GenerateVarHandleTarget(invoke, target, codegen);
    if (slow_path != nullptr) {
      __ Bind(slow_path->GetNativeByteOrderLabel());
    }
  }

  uint32_t temp_count = locations->GetTempCount();
  GenCompareAndSetOrExchange(codegen,
                             invoke,
                             type,
                             CpuRegister(target.object),
                             CpuRegister(target.offset),
                             /*temp1_index=*/ temp_count - 1,
                             /*temp2_index=*/ temp_count - 2,
                             /*temp3_index=*/ temp_count - 3,
                             locations->InAt(new_value_index),
                             locations->InAt(expected_value_index),
                             locations->Out(),
                             is_cmpxchg,
                             byte_swap);

  // We are using LOCK CMPXCHG in all cases because there is no CAS equivalent that has weak
  // failure semantics. LOCK CMPXCHG has full barrier semantics, so we don't need barriers.

  if (slow_path != nullptr) {
    DCHECK(!byte_swap);
    __ Bind(slow_path->GetExitLabel());
  }
}

void IntrinsicLocationsBuilderX86_64::VisitVarHandleCompareAndSet(HInvoke* invoke) {
  CreateVarHandleCompareAndSetOrExchangeLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitVarHandleCompareAndSet(HInvoke* invoke) {
  GenerateVarHandleCompareAndSetOrExchange(invoke, codegen_, /*is_cmpxchg=*/ false);
}

void IntrinsicLocationsBuilderX86_64::VisitVarHandleWeakCompareAndSet(HInvoke* invoke) {
  CreateVarHandleCompareAndSetOrExchangeLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitVarHandleWeakCompareAndSet(HInvoke* invoke) {
  GenerateVarHandleCompareAndSetOrExchange(invoke, codegen_, /*is_cmpxchg=*/ false);
}

void IntrinsicLocationsBuilderX86_64::VisitVarHandleWeakCompareAndSetPlain(HInvoke* invoke) {
  CreateVarHandleCompareAndSetOrExchangeLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitVarHandleWeakCompareAndSetPlain(HInvoke* invoke) {
  GenerateVarHandleCompareAndSetOrExchange(invoke, codegen_, /*is_cmpxchg=*/ false);
}

void IntrinsicLocationsBuilderX86_64::VisitVarHandleWeakCompareAndSetAcquire(HInvoke* invoke) {
  CreateVarHandleCompareAndSetOrExchangeLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitVarHandleWeakCompareAndSetAcquire(HInvoke* invoke) {
  GenerateVarHandleCompareAndSetOrExchange(invoke, codegen_, /*is_cmpxchg=*/ false);
}

void IntrinsicLocationsBuilderX86_64::VisitVarHandleWeakCompareAndSetRelease(HInvoke* invoke) {
  CreateVarHandleCompareAndSetOrExchangeLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitVarHandleWeakCompareAndSetRelease(HInvoke* invoke) {
  GenerateVarHandleCompareAndSetOrExchange(invoke, codegen_, /*is_cmpxchg=*/ false);
}

void IntrinsicLocationsBuilderX86_64::VisitVarHandleCompareAndExchange(HInvoke* invoke) {
  CreateVarHandleCompareAndSetOrExchangeLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitVarHandleCompareAndExchange(HInvoke* invoke) {
  GenerateVarHandleCompareAndSetOrExchange(invoke, codegen_, /*is_cmpxchg=*/ true);
}

void IntrinsicLocationsBuilderX86_64::VisitVarHandleCompareAndExchangeAcquire(HInvoke* invoke) {
  CreateVarHandleCompareAndSetOrExchangeLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitVarHandleCompareAndExchangeAcquire(HInvoke* invoke) {
  GenerateVarHandleCompareAndSetOrExchange(invoke, codegen_, /*is_cmpxchg=*/ true);
}

void IntrinsicLocationsBuilderX86_64::VisitVarHandleCompareAndExchangeRelease(HInvoke* invoke) {
  CreateVarHandleCompareAndSetOrExchangeLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitVarHandleCompareAndExchangeRelease(HInvoke* invoke) {
  GenerateVarHandleCompareAndSetOrExchange(invoke, codegen_, /*is_cmpxchg=*/ true);
}

static void CreateVarHandleGetAndSetLocations(HInvoke* invoke, CodeGeneratorX86_64* codegen) {
  if (!HasVarHandleIntrinsicImplementation(invoke, codegen)) {
    return;
  }

  uint32_t number_of_arguments = invoke->GetNumberOfArguments();
  uint32_t new_value_index = number_of_arguments - 1;
  DataType::Type type = invoke->GetType();
  DCHECK_EQ(type, GetDataTypeFromShorty(invoke, new_value_index));

  LocationSummary* locations = CreateVarHandleCommonLocations(invoke);

  if (DataType::IsFloatingPointType(type)) {
    locations->SetOut(Location::RequiresFpuRegister());
    // A temporary is needed to load the new floating-point value into a register for XCHG.
    locations->AddTemp(Location::RequiresRegister());
  } else {
    // Use the same register for both the new value and output to take advantage of XCHG.
    // It doesn't have to be RAX, but we need to choose some to make sure it's the same.
    locations->SetOut(Location::RegisterLocation(RAX));
    locations->SetInAt(new_value_index, Location::RegisterLocation(RAX));
    if (type == DataType::Type::kReference) {
      // Need two temporaries for MarkGCCard.
      locations->AddTemp(Location::RequiresRegister());
      locations->AddTemp(Location::RequiresRegister());
      if (codegen->EmitReadBarrier()) {
        // Need a third temporary for GenerateReferenceLoadWithBakerReadBarrier.
        DCHECK(kUseBakerReadBarrier);
        locations->AddTemp(Location::RequiresRegister());
      }
    }
  }
}

static void GenerateVarHandleGetAndSet(HInvoke* invoke,
                                       CodeGeneratorX86_64* codegen,
                                       Location value,
                                       DataType::Type type,
                                       Address field_addr,
                                       CpuRegister ref,
                                       bool byte_swap) {
  X86_64Assembler* assembler = codegen->GetAssembler();
  LocationSummary* locations = invoke->GetLocations();
  Location out = locations->Out();
  uint32_t temp_count = locations->GetTempCount();

  if (DataType::IsFloatingPointType(type)) {
    // `getAndSet` for floating-point types: move the new FP value into a register, atomically
    // exchange it with the field, and move the old value into the output FP register.
    Location temp = locations->GetTemp(temp_count - 1);
    codegen->Move(temp, value);
    bool is64bit = (type == DataType::Type::kFloat64);
    DataType::Type bswap_type = is64bit ? DataType::Type::kUint64 : DataType::Type::kUint32;
    if (byte_swap) {
      codegen->GetInstructionCodegen()->Bswap(temp, bswap_type);
    }
    if (is64bit) {
      __ xchgq(temp.AsRegister<CpuRegister>(), field_addr);
    } else {
      __ xchgl(temp.AsRegister<CpuRegister>(), field_addr);
    }
    if (byte_swap) {
      codegen->GetInstructionCodegen()->Bswap(temp, bswap_type);
    }
    __ movd(out.AsFpuRegister<XmmRegister>(), temp.AsRegister<CpuRegister>(), is64bit);
  } else if (type == DataType::Type::kReference) {
    // `getAndSet` for references: load reference and atomically exchange it with the field.
    // Output register is the same as the one holding new value, so no need to move the result.
    DCHECK(!byte_swap);

    CpuRegister temp1 = locations->GetTemp(temp_count - 1).AsRegister<CpuRegister>();
    CpuRegister temp2 = locations->GetTemp(temp_count - 2).AsRegister<CpuRegister>();
    CpuRegister valreg = value.AsRegister<CpuRegister>();

    if (codegen->EmitBakerReadBarrier()) {
      codegen->GenerateReferenceLoadWithBakerReadBarrier(
          invoke,
          locations->GetTemp(temp_count - 3),
          ref,
          field_addr,
          /*needs_null_check=*/ false,
          /*always_update_field=*/ true,
          &temp1,
          &temp2);
    }
    codegen->MarkGCCard(temp1, temp2, ref);

    DCHECK_EQ(valreg, out.AsRegister<CpuRegister>());
    if (kPoisonHeapReferences) {
      // Use a temp to avoid poisoning base of the field address, which might happen if `valreg` is
      // the same as `target.object` (for code like `vh.getAndSet(obj, obj)`).
      __ movl(temp1, valreg);
      __ PoisonHeapReference(temp1);
      __ xchgl(temp1, field_addr);
      __ UnpoisonHeapReference(temp1);
      __ movl(valreg, temp1);
    } else {
      __ xchgl(valreg, field_addr);
    }
  } else {
    // `getAndSet` for integral types: atomically exchange the new value with the field. Output
    // register is the same as the one holding new value. Do sign extend / zero extend as needed.
    if (byte_swap) {
      codegen->GetInstructionCodegen()->Bswap(value, type);
    }
    CpuRegister valreg = value.AsRegister<CpuRegister>();
    DCHECK_EQ(valreg, out.AsRegister<CpuRegister>());
    switch (type) {
      case DataType::Type::kBool:
      case DataType::Type::kUint8:
        __ xchgb(valreg, field_addr);
        __ movzxb(valreg, valreg);
        break;
      case DataType::Type::kInt8:
        __ xchgb(valreg, field_addr);
        __ movsxb(valreg, valreg);
        break;
      case DataType::Type::kUint16:
        __ xchgw(valreg, field_addr);
        __ movzxw(valreg, valreg);
        break;
      case DataType::Type::kInt16:
        __ xchgw(valreg, field_addr);
        __ movsxw(valreg, valreg);
        break;
      case DataType::Type::kInt32:
      case DataType::Type::kUint32:
        __ xchgl(valreg, field_addr);
        break;
      case DataType::Type::kInt64:
      case DataType::Type::kUint64:
        __ xchgq(valreg, field_addr);
        break;
      default:
        DCHECK(false) << "unexpected type in getAndSet intrinsic";
        UNREACHABLE();
    }
    if (byte_swap) {
      codegen->GetInstructionCodegen()->Bswap(value, type);
    }
  }
}

static void CreateVarHandleGetAndBitwiseOpLocations(HInvoke* invoke, CodeGeneratorX86_64* codegen) {
  if (!HasVarHandleIntrinsicImplementation(invoke, codegen)) {
    return;
  }

  uint32_t number_of_arguments = invoke->GetNumberOfArguments();
  uint32_t new_value_index = number_of_arguments - 1;
  DataType::Type type = invoke->GetType();
  DCHECK_EQ(type, GetDataTypeFromShorty(invoke, new_value_index));

  LocationSummary* locations = CreateVarHandleCommonLocations(invoke);

  DCHECK_NE(DataType::Type::kReference, type);
  DCHECK(!DataType::IsFloatingPointType(type));

  // A temporary to compute the bitwise operation on the old and the new values.
  locations->AddTemp(Location::RequiresRegister());
  // We need value to be either in a register, or a 32-bit constant (as there are no arithmetic
  // instructions that accept 64-bit immediate on x86_64).
  locations->SetInAt(new_value_index, DataType::Is64BitType(type)
      ? Location::RequiresRegister()
      : Location::RegisterOrConstant(invoke->InputAt(new_value_index)));
  // Output is in RAX to accommodate CMPXCHG. It is also used as a temporary.
  locations->SetOut(Location::RegisterLocation(RAX));
}

static void GenerateVarHandleGetAndOp(HInvoke* invoke,
                                      CodeGeneratorX86_64* codegen,
                                      Location value,
                                      DataType::Type type,
                                      Address field_addr,
                                      GetAndUpdateOp get_and_update_op,
                                      bool byte_swap) {
  X86_64Assembler* assembler = codegen->GetAssembler();
  LocationSummary* locations = invoke->GetLocations();
  Location temp_loc = locations->GetTemp(locations->GetTempCount() - 1);
  Location rax_loc = locations->Out();
  CpuRegister temp = temp_loc.AsRegister<CpuRegister>();
  CpuRegister rax = rax_loc.AsRegister<CpuRegister>();
  DCHECK_EQ(rax.AsRegister(), RAX);
  bool is64Bit = DataType::Is64BitType(type);

  NearLabel retry;
  __ Bind(&retry);

  // Load field value into RAX and copy it into a temporary register for the operation.
  codegen->LoadFromMemoryNoReference(type, Location::RegisterLocation(RAX), field_addr);
  codegen->Move(temp_loc, rax_loc);
  if (byte_swap) {
    // Byte swap the temporary, since we need to perform operation in native endianness.
    codegen->GetInstructionCodegen()->Bswap(temp_loc, type);
  }

  DCHECK_IMPLIES(value.IsConstant(), !is64Bit);
  int32_t const_value = value.IsConstant()
      ? CodeGenerator::GetInt32ValueOf(value.GetConstant())
      : 0;

  // Use 32-bit registers for 8/16/32-bit types to save on the REX prefix.
  switch (get_and_update_op) {
    case GetAndUpdateOp::kAdd:
      DCHECK(byte_swap);  // The non-byte-swapping path should use a faster XADD instruction.
      if (is64Bit) {
        __ addq(temp, value.AsRegister<CpuRegister>());
      } else if (value.IsConstant()) {
        __ addl(temp, Immediate(const_value));
      } else {
        __ addl(temp, value.AsRegister<CpuRegister>());
      }
      break;
    case GetAndUpdateOp::kBitwiseAnd:
      if (is64Bit) {
        __ andq(temp, value.AsRegister<CpuRegister>());
      } else if (value.IsConstant()) {
        __ andl(temp, Immediate(const_value));
      } else {
        __ andl(temp, value.AsRegister<CpuRegister>());
      }
      break;
    case GetAndUpdateOp::kBitwiseOr:
      if (is64Bit) {
        __ orq(temp, value.AsRegister<CpuRegister>());
      } else if (value.IsConstant()) {
        __ orl(temp, Immediate(const_value));
      } else {
        __ orl(temp, value.AsRegister<CpuRegister>());
      }
      break;
    case GetAndUpdateOp::kBitwiseXor:
      if (is64Bit) {
        __ xorq(temp, value.AsRegister<CpuRegister>());
      } else if (value.IsConstant()) {
        __ xorl(temp, Immediate(const_value));
      } else {
        __ xorl(temp, value.AsRegister<CpuRegister>());
      }
      break;
    default:
      DCHECK(false) <<  "unexpected operation";
      UNREACHABLE();
  }

  if (byte_swap) {
    // RAX still contains the original value, but we need to byte swap the temporary back.
    codegen->GetInstructionCodegen()->Bswap(temp_loc, type);
  }

  switch (type) {
    case DataType::Type::kBool:
    case DataType::Type::kUint8:
    case DataType::Type::kInt8:
      __ LockCmpxchgb(field_addr, temp);
      break;
    case DataType::Type::kUint16:
    case DataType::Type::kInt16:
      __ LockCmpxchgw(field_addr, temp);
      break;
    case DataType::Type::kInt32:
    case DataType::Type::kUint32:
      __ LockCmpxchgl(field_addr, temp);
      break;
    case DataType::Type::kInt64:
    case DataType::Type::kUint64:
      __ LockCmpxchgq(field_addr, temp);
      break;
    default:
      DCHECK(false) << "unexpected type in getAndBitwiseOp intrinsic";
      UNREACHABLE();
  }

  __ j(kNotZero, &retry);

  // The result is in RAX after CMPXCHG. Byte swap if necessary, but do not sign/zero extend,
  // as it has already been done by `LoadFromMemoryNoReference` above (and not altered by CMPXCHG).
  if (byte_swap) {
    codegen->GetInstructionCodegen()->Bswap(rax_loc, type);
  }
}

static void CreateVarHandleGetAndAddLocations(HInvoke* invoke, CodeGeneratorX86_64* codegen) {
  if (!HasVarHandleIntrinsicImplementation(invoke, codegen)) {
    return;
  }

  uint32_t number_of_arguments = invoke->GetNumberOfArguments();
  uint32_t new_value_index = number_of_arguments - 1;
  DataType::Type type = invoke->GetType();
  DCHECK_EQ(type, GetDataTypeFromShorty(invoke, new_value_index));

  LocationSummary* locations = CreateVarHandleCommonLocations(invoke);

  if (DataType::IsFloatingPointType(type)) {
    locations->SetOut(Location::RequiresFpuRegister());
    // Require that the new FP value is in a register (and not a constant) for ADDSS/ADDSD.
    locations->SetInAt(new_value_index, Location::RequiresFpuRegister());
    // CMPXCHG clobbers RAX.
    locations->AddTemp(Location::RegisterLocation(RAX));
    // An FP temporary to load the old value from the field and perform FP addition.
    locations->AddTemp(Location::RequiresFpuRegister());
    // A temporary to hold the new value for CMPXCHG.
    locations->AddTemp(Location::RequiresRegister());
  } else {
    DCHECK_NE(type, DataType::Type::kReference);
    // Use the same register for both the new value and output to take advantage of XADD.
    // It should be RAX, because the byte-swapping path of GenerateVarHandleGetAndAdd falls
    // back to GenerateVarHandleGetAndOp that expects out in RAX.
    locations->SetOut(Location::RegisterLocation(RAX));
    locations->SetInAt(new_value_index, Location::RegisterLocation(RAX));
    if (GetExpectedVarHandleCoordinatesCount(invoke) == 2) {
      // For byte array views with non-native endianness we need extra BSWAP operations, so we
      // cannot use XADD and have to fallback to a generic implementation based on CMPXCH. In that
      // case we need two temporary registers: one to hold value instead of RAX (which may get
      // clobbered by repeated CMPXCHG) and one for performing the operation. At compile time we
      // cannot distinguish this case from arrays or native-endian byte array views.
      locations->AddTemp(Location::RequiresRegister());
      locations->AddTemp(Location::RequiresRegister());
    }
  }
}

static void GenerateVarHandleGetAndAdd(HInvoke* invoke,
                                       CodeGeneratorX86_64* codegen,
                                       Location value,
                                       DataType::Type type,
                                       Address field_addr,
                                       bool byte_swap) {
  X86_64Assembler* assembler = codegen->GetAssembler();
  LocationSummary* locations = invoke->GetLocations();
  Location out = locations->Out();
  uint32_t temp_count = locations->GetTempCount();

  if (DataType::IsFloatingPointType(type)) {
    if (byte_swap) {
      // This code should never be executed: it is the case of a byte array view (since it requires
      // a byte swap), and varhandles for byte array views support numeric atomic update access mode
      // only for int and long, but not for floating-point types (see javadoc comments for
      // java.lang.invoke.MethodHandles.byteArrayViewVarHandle()). But ART varhandle implementation
      // for byte array views treats floating-point types them as numeric types in
      // ByteArrayViewVarHandle::Access(). Terefore we do generate intrinsic code, but it always
      // fails access mode check at runtime prior to reaching this point. Illegal instruction UD2
      // ensures that if control flow gets here by mistake, we will notice.
      __ ud2();
    }

    // `getAndAdd` for floating-point types: load the old FP value into a temporary FP register and
    // in RAX for CMPXCHG, add the new FP value to the old one, move it to a non-FP temporary for
    // CMPXCHG and loop until CMPXCHG succeeds. Move the result from RAX to the output FP register.
    bool is64bit = (type == DataType::Type::kFloat64);
    DataType::Type bswap_type = is64bit ? DataType::Type::kUint64 : DataType::Type::kUint32;
    XmmRegister fptemp = locations->GetTemp(temp_count - 2).AsFpuRegister<XmmRegister>();
    Location rax_loc = Location::RegisterLocation(RAX);
    Location temp_loc = locations->GetTemp(temp_count - 1);
    CpuRegister temp = temp_loc.AsRegister<CpuRegister>();

    NearLabel retry;
    __ Bind(&retry);

    // Read value from memory into an FP register and copy in into RAX.
    if (is64bit) {
      __ movsd(fptemp, field_addr);
    } else {
      __ movss(fptemp, field_addr);
    }
    __ movd(CpuRegister(RAX), fptemp, is64bit);
    // If necessary, byte swap RAX and update the value in FP register to also be byte-swapped.
    if (byte_swap) {
      codegen->GetInstructionCodegen()->Bswap(rax_loc, bswap_type);
      __ movd(fptemp, CpuRegister(RAX), is64bit);
    }
    // Perform the FP addition and move it to a temporary register to prepare for CMPXCHG.
    if (is64bit) {
      __ addsd(fptemp, value.AsFpuRegister<XmmRegister>());
    } else {
      __ addss(fptemp, value.AsFpuRegister<XmmRegister>());
    }
    __ movd(temp, fptemp, is64bit);
    // If necessary, byte swap RAX before CMPXCHG and the temporary before copying to FP register.
    if (byte_swap) {
      codegen->GetInstructionCodegen()->Bswap(temp_loc, bswap_type);
      codegen->GetInstructionCodegen()->Bswap(rax_loc, bswap_type);
    }
    if (is64bit) {
      __ LockCmpxchgq(field_addr, temp);
    } else {
      __ LockCmpxchgl(field_addr, temp);
    }

    __ j(kNotZero, &retry);

    // The old value is in RAX, byte swap if necessary.
    if (byte_swap) {
      codegen->GetInstructionCodegen()->Bswap(rax_loc, bswap_type);
    }
    __ movd(out.AsFpuRegister<XmmRegister>(), CpuRegister(RAX), is64bit);
  } else {
    if (byte_swap) {
      // We cannot use XADD since we need to byte-swap the old value when reading it from memory,
      // and then byte-swap the sum before writing it to memory. So fallback to the slower generic
      // implementation that is also used for bitwise operations.
      // Move value from RAX to a temporary register, as RAX may get clobbered by repeated CMPXCHG.
      DCHECK_EQ(GetExpectedVarHandleCoordinatesCount(invoke), 2u);
      Location temp = locations->GetTemp(temp_count - 2);
      codegen->Move(temp, value);
      GenerateVarHandleGetAndOp(
          invoke, codegen, temp, type, field_addr, GetAndUpdateOp::kAdd, byte_swap);
    } else {
      // `getAndAdd` for integral types: atomically exchange the new value with the field and add
      // the old value to the field. Output register is the same as the one holding new value. Do
      // sign extend / zero extend as needed.
      CpuRegister valreg = value.AsRegister<CpuRegister>();
      DCHECK_EQ(valreg, out.AsRegister<CpuRegister>());
      switch (type) {
        case DataType::Type::kBool:
        case DataType::Type::kUint8:
          __ LockXaddb(field_addr, valreg);
          __ movzxb(valreg, valreg);
          break;
        case DataType::Type::kInt8:
          __ LockXaddb(field_addr, valreg);
          __ movsxb(valreg, valreg);
          break;
        case DataType::Type::kUint16:
          __ LockXaddw(field_addr, valreg);
          __ movzxw(valreg, valreg);
          break;
        case DataType::Type::kInt16:
          __ LockXaddw(field_addr, valreg);
          __ movsxw(valreg, valreg);
          break;
        case DataType::Type::kInt32:
        case DataType::Type::kUint32:
          __ LockXaddl(field_addr, valreg);
          break;
        case DataType::Type::kInt64:
        case DataType::Type::kUint64:
          __ LockXaddq(field_addr, valreg);
          break;
        default:
          DCHECK(false) << "unexpected type in getAndAdd intrinsic";
          UNREACHABLE();
      }
    }
  }
}

static void GenerateVarHandleGetAndUpdate(HInvoke* invoke,
                                          CodeGeneratorX86_64* codegen,
                                          GetAndUpdateOp get_and_update_op,
                                          bool need_any_store_barrier,
                                          bool need_any_any_barrier,
                                          bool byte_swap = false) {
  DCHECK_IMPLIES(codegen->EmitReadBarrier(), kUseBakerReadBarrier);

  X86_64Assembler* assembler = codegen->GetAssembler();
  LocationSummary* locations = invoke->GetLocations();

  uint32_t number_of_arguments = invoke->GetNumberOfArguments();
  Location value = locations->InAt(number_of_arguments - 1);
  DataType::Type type = invoke->GetType();

  VarHandleSlowPathX86_64* slow_path = nullptr;
  VarHandleTarget target = GetVarHandleTarget(invoke);
  if (!byte_swap) {
    slow_path = GenerateVarHandleChecks(invoke, codegen, type);
    GenerateVarHandleTarget(invoke, target, codegen);
    if (slow_path != nullptr) {
      slow_path->SetGetAndUpdateOp(get_and_update_op);
      slow_path->SetNeedAnyStoreBarrier(need_any_store_barrier);
      slow_path->SetNeedAnyAnyBarrier(need_any_any_barrier);
      __ Bind(slow_path->GetNativeByteOrderLabel());
    }
  }

  CpuRegister ref(target.object);
  Address field_addr(ref, CpuRegister(target.offset), TIMES_1, 0);

  if (need_any_store_barrier) {
    codegen->GenerateMemoryBarrier(MemBarrierKind::kAnyStore);
  }

  switch (get_and_update_op) {
    case GetAndUpdateOp::kSet:
      GenerateVarHandleGetAndSet(invoke, codegen, value, type, field_addr, ref, byte_swap);
      break;
    case GetAndUpdateOp::kAdd:
      GenerateVarHandleGetAndAdd(invoke, codegen, value, type, field_addr, byte_swap);
      break;
    case GetAndUpdateOp::kBitwiseAnd:
    case GetAndUpdateOp::kBitwiseOr:
    case GetAndUpdateOp::kBitwiseXor:
      GenerateVarHandleGetAndOp(
          invoke, codegen, value, type, field_addr, get_and_update_op, byte_swap);
      break;
  }

  if (need_any_any_barrier) {
    codegen->GenerateMemoryBarrier(MemBarrierKind::kAnyAny);
  }

  if (slow_path != nullptr) {
    DCHECK(!byte_swap);
    __ Bind(slow_path->GetExitLabel());
  }
}

void IntrinsicLocationsBuilderX86_64::VisitVarHandleGetAndSet(HInvoke* invoke) {
  CreateVarHandleGetAndSetLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitVarHandleGetAndSet(HInvoke* invoke) {
  // `getAndSet` has `getVolatile` + `setVolatile` semantics, so it needs both barriers.
  GenerateVarHandleGetAndUpdate(invoke,
                                codegen_,
                                GetAndUpdateOp::kSet,
                                /*need_any_store_barrier=*/ true,
                                /*need_any_any_barrier=*/ true);
}

void IntrinsicLocationsBuilderX86_64::VisitVarHandleGetAndSetAcquire(HInvoke* invoke) {
  CreateVarHandleGetAndSetLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitVarHandleGetAndSetAcquire(HInvoke* invoke) {
  // `getAndSetAcquire` has `getAcquire` + `set` semantics, so it doesn't need any barriers.
  GenerateVarHandleGetAndUpdate(invoke,
                                codegen_,
                                GetAndUpdateOp::kSet,
                                /*need_any_store_barrier=*/ false,
                                /*need_any_any_barrier=*/ false);
}

void IntrinsicLocationsBuilderX86_64::VisitVarHandleGetAndSetRelease(HInvoke* invoke) {
  CreateVarHandleGetAndSetLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitVarHandleGetAndSetRelease(HInvoke* invoke) {
  // `getAndSetRelease` has `get` + `setRelease` semantics, so it needs `kAnyStore` barrier.
  GenerateVarHandleGetAndUpdate(invoke,
                                codegen_,
                                GetAndUpdateOp::kSet,
                                /*need_any_store_barrier=*/ true,
                                /*need_any_any_barrier=*/ false);
}

void IntrinsicLocationsBuilderX86_64::VisitVarHandleGetAndAdd(HInvoke* invoke) {
  CreateVarHandleGetAndAddLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitVarHandleGetAndAdd(HInvoke* invoke) {
  // `getAndAdd` has `getVolatile` + `setVolatile` semantics, so it needs both barriers.
  GenerateVarHandleGetAndUpdate(invoke,
                                codegen_,
                                GetAndUpdateOp::kAdd,
                                /*need_any_store_barrier=*/ true,
                                /*need_any_any_barrier=*/ true);
}

void IntrinsicLocationsBuilderX86_64::VisitVarHandleGetAndAddAcquire(HInvoke* invoke) {
  CreateVarHandleGetAndAddLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitVarHandleGetAndAddAcquire(HInvoke* invoke) {
  // `getAndAddAcquire` has `getAcquire` + `set` semantics, so it doesn't need any barriers.
  GenerateVarHandleGetAndUpdate(invoke,
                                codegen_,
                                GetAndUpdateOp::kAdd,
                                /*need_any_store_barrier=*/ false,
                                /*need_any_any_barrier=*/ false);
}

void IntrinsicLocationsBuilderX86_64::VisitVarHandleGetAndAddRelease(HInvoke* invoke) {
  CreateVarHandleGetAndAddLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitVarHandleGetAndAddRelease(HInvoke* invoke) {
  // `getAndAddRelease` has `get` + `setRelease` semantics, so it needs `kAnyStore` barrier.
  GenerateVarHandleGetAndUpdate(invoke,
                                codegen_,
                                GetAndUpdateOp::kAdd,
                                /*need_any_store_barrier=*/ true,
                                /*need_any_any_barrier=*/ false);
}

void IntrinsicLocationsBuilderX86_64::VisitVarHandleGetAndBitwiseAnd(HInvoke* invoke) {
  CreateVarHandleGetAndBitwiseOpLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitVarHandleGetAndBitwiseAnd(HInvoke* invoke) {
  // `getAndBitwiseAnd` has `getVolatile` + `setVolatile` semantics, so it needs both barriers.
  GenerateVarHandleGetAndUpdate(invoke,
                                codegen_,
                                GetAndUpdateOp::kBitwiseAnd,
                                /*need_any_store_barrier=*/ true,
                                /*need_any_any_barrier=*/ true);
}

void IntrinsicLocationsBuilderX86_64::VisitVarHandleGetAndBitwiseAndAcquire(HInvoke* invoke) {
  CreateVarHandleGetAndBitwiseOpLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitVarHandleGetAndBitwiseAndAcquire(HInvoke* invoke) {
  // `getAndBitwiseAndAcquire` has `getAcquire` + `set` semantics, so it doesn't need any barriers.
  GenerateVarHandleGetAndUpdate(invoke,
                                codegen_,
                                GetAndUpdateOp::kBitwiseAnd,
                                /*need_any_store_barrier=*/ false,
                                /*need_any_any_barrier=*/ false);
}

void IntrinsicLocationsBuilderX86_64::VisitVarHandleGetAndBitwiseAndRelease(HInvoke* invoke) {
  CreateVarHandleGetAndBitwiseOpLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitVarHandleGetAndBitwiseAndRelease(HInvoke* invoke) {
  // `getAndBitwiseAndRelease` has `get` + `setRelease` semantics, so it needs `kAnyStore` barrier.
  GenerateVarHandleGetAndUpdate(invoke,
                                codegen_,
                                GetAndUpdateOp::kBitwiseAnd,
                                /*need_any_store_barrier=*/ true,
                                /*need_any_any_barrier=*/ false);
}

void IntrinsicLocationsBuilderX86_64::VisitVarHandleGetAndBitwiseOr(HInvoke* invoke) {
  CreateVarHandleGetAndBitwiseOpLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitVarHandleGetAndBitwiseOr(HInvoke* invoke) {
  // `getAndBitwiseOr` has `getVolatile` + `setVolatile` semantics, so it needs both barriers.
  GenerateVarHandleGetAndUpdate(invoke,
                                codegen_,
                                GetAndUpdateOp::kBitwiseOr,
                                /*need_any_store_barrier=*/ true,
                                /*need_any_any_barrier=*/ true);
}

void IntrinsicLocationsBuilderX86_64::VisitVarHandleGetAndBitwiseOrAcquire(HInvoke* invoke) {
  CreateVarHandleGetAndBitwiseOpLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitVarHandleGetAndBitwiseOrAcquire(HInvoke* invoke) {
  // `getAndBitwiseOrAcquire` has `getAcquire` + `set` semantics, so it doesn't need any barriers.
  GenerateVarHandleGetAndUpdate(invoke,
                                codegen_,
                                GetAndUpdateOp::kBitwiseOr,
                                /*need_any_store_barrier=*/ false,
                                /*need_any_any_barrier=*/ false);
}

void IntrinsicLocationsBuilderX86_64::VisitVarHandleGetAndBitwiseOrRelease(HInvoke* invoke) {
  CreateVarHandleGetAndBitwiseOpLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitVarHandleGetAndBitwiseOrRelease(HInvoke* invoke) {
  // `getAndBitwiseOrRelease` has `get` + `setRelease` semantics, so it needs `kAnyStore` barrier.
  GenerateVarHandleGetAndUpdate(invoke,
                                codegen_,
                                GetAndUpdateOp::kBitwiseOr,
                                /*need_any_store_barrier=*/ true,
                                /*need_any_any_barrier=*/ false);
}

void IntrinsicLocationsBuilderX86_64::VisitVarHandleGetAndBitwiseXor(HInvoke* invoke) {
  CreateVarHandleGetAndBitwiseOpLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitVarHandleGetAndBitwiseXor(HInvoke* invoke) {
  // `getAndBitwiseXor` has `getVolatile` + `setVolatile` semantics, so it needs both barriers.
  GenerateVarHandleGetAndUpdate(invoke,
                                codegen_,
                                GetAndUpdateOp::kBitwiseXor,
                                /*need_any_store_barrier=*/ true,
                                /*need_any_any_barrier=*/ true);
}

void IntrinsicLocationsBuilderX86_64::VisitVarHandleGetAndBitwiseXorAcquire(HInvoke* invoke) {
  CreateVarHandleGetAndBitwiseOpLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitVarHandleGetAndBitwiseXorAcquire(HInvoke* invoke) {
  // `getAndBitwiseXorAcquire` has `getAcquire` + `set` semantics, so it doesn't need any barriers.
  GenerateVarHandleGetAndUpdate(invoke,
                                codegen_,
                                GetAndUpdateOp::kBitwiseXor,
                                /*need_any_store_barrier=*/ false,
                                /*need_any_any_barrier=*/ false);
}

void IntrinsicLocationsBuilderX86_64::VisitVarHandleGetAndBitwiseXorRelease(HInvoke* invoke) {
  CreateVarHandleGetAndBitwiseOpLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86_64::VisitVarHandleGetAndBitwiseXorRelease(HInvoke* invoke) {
  // `getAndBitwiseXorRelease` has `get` + `setRelease` semantics, so it needs `kAnyStore` barrier.
  GenerateVarHandleGetAndUpdate(invoke,
                                codegen_,
                                GetAndUpdateOp::kBitwiseXor,
                                /*need_any_store_barrier=*/ true,
                                /*need_any_any_barrier=*/ false);
}

void VarHandleSlowPathX86_64::EmitByteArrayViewCode(CodeGeneratorX86_64* codegen) {
  DCHECK(GetByteArrayViewCheckLabel()->IsLinked());
  X86_64Assembler* assembler = codegen->GetAssembler();

  HInvoke* invoke = GetInvoke();
  LocationSummary* locations = invoke->GetLocations();
  mirror::VarHandle::AccessModeTemplate access_mode_template = GetAccessModeTemplate();
  DataType::Type value_type =
      GetVarHandleExpectedValueType(invoke, /*expected_coordinates_count=*/ 2u);
  DCHECK_NE(value_type, DataType::Type::kReference);
  size_t size = DataType::Size(value_type);
  DCHECK_GT(size, 1u);

  CpuRegister varhandle = locations->InAt(0).AsRegister<CpuRegister>();
  CpuRegister object = locations->InAt(1).AsRegister<CpuRegister>();
  CpuRegister index = locations->InAt(2).AsRegister<CpuRegister>();
  CpuRegister temp = locations->GetTemp(locations->GetTempCount() - 1).AsRegister<CpuRegister>();

  MemberOffset class_offset = mirror::Object::ClassOffset();
  MemberOffset array_length_offset = mirror::Array::LengthOffset();
  MemberOffset data_offset = mirror::Array::DataOffset(Primitive::kPrimByte);
  MemberOffset native_byte_order_offset = mirror::ByteArrayViewVarHandle::NativeByteOrderOffset();

  VarHandleTarget target = GetVarHandleTarget(invoke);

  __ Bind(GetByteArrayViewCheckLabel());

  // The main path checked that the coordinateType0 is an array class that matches
  // the class of the actual coordinate argument but it does not match the value type.
  // Check if the `varhandle` references a ByteArrayViewVarHandle instance.
  codegen->LoadClassRootForIntrinsic(temp, ClassRoot::kJavaLangInvokeByteArrayViewVarHandle);
  assembler->MaybePoisonHeapReference(temp);
  __ cmpl(temp, Address(varhandle, class_offset.Int32Value()));
  __ j(kNotEqual, GetEntryLabel());

  // Check for array index out of bounds.
  __ movl(temp, Address(object, array_length_offset.Int32Value()));
  // SUB sets flags in the same way as CMP.
  __ subl(temp, index);
  __ j(kBelowEqual, GetEntryLabel());
  // The difference between index and array length must be enough for the `value_type` size.
  __ cmpl(temp, Immediate(size));
  __ j(kBelow, GetEntryLabel());

  // Construct the target.
  __ leal(CpuRegister(target.offset), Address(index, TIMES_1, data_offset.Int32Value()));

  // Alignment check. For unaligned access, go to the runtime.
  DCHECK(IsPowerOfTwo(size));
  __ testl(CpuRegister(target.offset), Immediate(size - 1u));
  __ j(kNotZero, GetEntryLabel());

  // Byte order check. For native byte order return to the main path.
  if (access_mode_template == mirror::VarHandle::AccessModeTemplate::kSet &&
      IsZeroBitPattern(invoke->InputAt(invoke->GetNumberOfArguments() - 1u))) {
    // There is no reason to differentiate between native byte order and byte-swap
    // for setting a zero bit pattern. Just return to the main path.
    __ jmp(GetNativeByteOrderLabel());
    return;
  }
  __ cmpl(Address(varhandle, native_byte_order_offset.Int32Value()), Immediate(0));
  __ j(kNotEqual, GetNativeByteOrderLabel());

  switch (access_mode_template) {
    case mirror::VarHandle::AccessModeTemplate::kGet:
      GenerateVarHandleGet(invoke, codegen, /*byte_swap=*/ true);
      break;
    case mirror::VarHandle::AccessModeTemplate::kSet:
      GenerateVarHandleSet(invoke, codegen, is_volatile_, is_atomic_, /*byte_swap=*/ true);
      break;
    case mirror::VarHandle::AccessModeTemplate::kCompareAndSet:
      GenerateVarHandleCompareAndSetOrExchange(
          invoke, codegen, /*is_cmpxchg=*/ false, /*byte_swap=*/ true);
      break;
    case mirror::VarHandle::AccessModeTemplate::kCompareAndExchange:
      GenerateVarHandleCompareAndSetOrExchange(
          invoke, codegen, /*is_cmpxchg=*/ true, /*byte_swap=*/ true);
      break;
    case mirror::VarHandle::AccessModeTemplate::kGetAndUpdate:
      GenerateVarHandleGetAndUpdate(invoke,
                                    codegen,
                                    get_and_update_op_,
                                    need_any_store_barrier_,
                                    need_any_any_barrier_,
                                    /*byte_swap=*/ true);
      break;
  }

  __ jmp(GetExitLabel());
}

#define MARK_UNIMPLEMENTED(Name) UNIMPLEMENTED_INTRINSIC(X86_64, Name)
UNIMPLEMENTED_INTRINSIC_LIST_X86_64(MARK_UNIMPLEMENTED);
#undef MARK_UNIMPLEMENTED

UNREACHABLE_INTRINSICS(X86_64)

#undef __

}  // namespace x86_64
}  // namespace art
