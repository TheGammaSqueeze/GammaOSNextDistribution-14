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

#include "intrinsics_x86.h"

#include <limits>

#include "arch/x86/instruction_set_features_x86.h"
#include "art_method.h"
#include "base/bit_utils.h"
#include "code_generator_x86.h"
#include "data_type-inl.h"
#include "entrypoints/quick/quick_entrypoints.h"
#include "heap_poisoning.h"
#include "intrinsic_objects.h"
#include "intrinsics.h"
#include "intrinsics_utils.h"
#include "lock_word.h"
#include "mirror/array-inl.h"
#include "mirror/object_array-inl.h"
#include "mirror/reference.h"
#include "mirror/string.h"
#include "mirror/var_handle.h"
#include "scoped_thread_state_change-inl.h"
#include "thread-current-inl.h"
#include "utils/x86/assembler_x86.h"
#include "utils/x86/constants_x86.h"
#include "well_known_classes.h"

namespace art HIDDEN {

namespace x86 {

IntrinsicLocationsBuilderX86::IntrinsicLocationsBuilderX86(CodeGeneratorX86* codegen)
  : allocator_(codegen->GetGraph()->GetAllocator()),
    codegen_(codegen) {
}


X86Assembler* IntrinsicCodeGeneratorX86::GetAssembler() {
  return down_cast<X86Assembler*>(codegen_->GetAssembler());
}

ArenaAllocator* IntrinsicCodeGeneratorX86::GetAllocator() {
  return codegen_->GetGraph()->GetAllocator();
}

bool IntrinsicLocationsBuilderX86::TryDispatch(HInvoke* invoke) {
  Dispatch(invoke);
  LocationSummary* res = invoke->GetLocations();
  if (res == nullptr) {
    return false;
  }
  return res->Intrinsified();
}

using IntrinsicSlowPathX86 = IntrinsicSlowPath<InvokeDexCallingConventionVisitorX86>;

#define __ assembler->

static void GenArrayAddress(X86Assembler* assembler,
                            Register dest,
                            Register base,
                            Location pos,
                            DataType::Type type,
                            uint32_t data_offset) {
  if (pos.IsConstant()) {
    int32_t constant = pos.GetConstant()->AsIntConstant()->GetValue();
    __ leal(dest, Address(base, DataType::Size(type) * constant + data_offset));
  } else {
    const ScaleFactor scale_factor = static_cast<ScaleFactor>(DataType::SizeShift(type));
    __ leal(dest, Address(base, pos.AsRegister<Register>(), scale_factor, data_offset));
  }
}

// Slow path implementing the SystemArrayCopy intrinsic copy loop with read barriers.
class ReadBarrierSystemArrayCopySlowPathX86 : public SlowPathCode {
 public:
  explicit ReadBarrierSystemArrayCopySlowPathX86(HInstruction* instruction)
      : SlowPathCode(instruction) {
  }

  void EmitNativeCode(CodeGenerator* codegen) override {
    DCHECK(codegen->EmitBakerReadBarrier());
    CodeGeneratorX86* x86_codegen = down_cast<CodeGeneratorX86*>(codegen);
    X86Assembler* assembler = x86_codegen->GetAssembler();
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

    Register src_curr_addr = locations->GetTemp(0).AsRegister<Register>();
    Register dst_curr_addr = locations->GetTemp(1).AsRegister<Register>();
    Register src_stop_addr = locations->GetTemp(2).AsRegister<Register>();
    Register value = locations->GetTemp(3).AsRegister<Register>();

    __ Bind(GetEntryLabel());
    // The `src_curr_addr` and `dst_curr_addr` were initialized before entering the slow-path.
    GenArrayAddress(assembler, src_stop_addr, src_curr_addr, length, type, /*data_offset=*/ 0u);

    NearLabel loop;
    __ Bind(&loop);
    __ movl(value, Address(src_curr_addr, 0));
    __ MaybeUnpoisonHeapReference(value);
    // TODO: Inline the mark bit check before calling the runtime?
    // value = ReadBarrier::Mark(value)
    // No need to save live registers; it's taken care of by the
    // entrypoint. Also, there is no need to update the stack mask,
    // as this runtime call will not trigger a garbage collection.
    // (See ReadBarrierMarkSlowPathX86::EmitNativeCode for more
    // explanations.)
    int32_t entry_point_offset = Thread::ReadBarrierMarkEntryPointsOffset<kX86PointerSize>(value);
    // This runtime call does not require a stack map.
    x86_codegen->InvokeRuntimeWithoutRecordingPcInfo(entry_point_offset, instruction_, this);
    __ MaybePoisonHeapReference(value);
    __ movl(Address(dst_curr_addr, 0), value);
    __ addl(src_curr_addr, Immediate(element_size));
    __ addl(dst_curr_addr, Immediate(element_size));
    __ cmpl(src_curr_addr, src_stop_addr);
    __ j(kNotEqual, &loop);
    __ jmp(GetExitLabel());
  }

  const char* GetDescription() const override { return "ReadBarrierSystemArrayCopySlowPathX86"; }

 private:
  DISALLOW_COPY_AND_ASSIGN(ReadBarrierSystemArrayCopySlowPathX86);
};

static void CreateFPToIntLocations(ArenaAllocator* allocator, HInvoke* invoke, bool is64bit) {
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::RequiresFpuRegister());
  locations->SetOut(Location::RequiresRegister());
  if (is64bit) {
    locations->AddTemp(Location::RequiresFpuRegister());
  }
}

static void CreateIntToFPLocations(ArenaAllocator* allocator, HInvoke* invoke, bool is64bit) {
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::RequiresRegister());
  locations->SetOut(Location::RequiresFpuRegister());
  if (is64bit) {
    locations->AddTemp(Location::RequiresFpuRegister());
    locations->AddTemp(Location::RequiresFpuRegister());
  }
}

static void MoveFPToInt(LocationSummary* locations, bool is64bit, X86Assembler* assembler) {
  Location input = locations->InAt(0);
  Location output = locations->Out();
  if (is64bit) {
    // Need to use the temporary.
    XmmRegister temp = locations->GetTemp(0).AsFpuRegister<XmmRegister>();
    __ movsd(temp, input.AsFpuRegister<XmmRegister>());
    __ movd(output.AsRegisterPairLow<Register>(), temp);
    __ psrlq(temp, Immediate(32));
    __ movd(output.AsRegisterPairHigh<Register>(), temp);
  } else {
    __ movd(output.AsRegister<Register>(), input.AsFpuRegister<XmmRegister>());
  }
}

static void MoveIntToFP(LocationSummary* locations, bool is64bit, X86Assembler* assembler) {
  Location input = locations->InAt(0);
  Location output = locations->Out();
  if (is64bit) {
    // Need to use the temporary.
    XmmRegister temp1 = locations->GetTemp(0).AsFpuRegister<XmmRegister>();
    XmmRegister temp2 = locations->GetTemp(1).AsFpuRegister<XmmRegister>();
    __ movd(temp1, input.AsRegisterPairLow<Register>());
    __ movd(temp2, input.AsRegisterPairHigh<Register>());
    __ punpckldq(temp1, temp2);
    __ movsd(output.AsFpuRegister<XmmRegister>(), temp1);
  } else {
    __ movd(output.AsFpuRegister<XmmRegister>(), input.AsRegister<Register>());
  }
}

void IntrinsicLocationsBuilderX86::VisitDoubleDoubleToRawLongBits(HInvoke* invoke) {
  CreateFPToIntLocations(allocator_, invoke, /* is64bit= */ true);
}
void IntrinsicLocationsBuilderX86::VisitDoubleLongBitsToDouble(HInvoke* invoke) {
  CreateIntToFPLocations(allocator_, invoke, /* is64bit= */ true);
}

void IntrinsicCodeGeneratorX86::VisitDoubleDoubleToRawLongBits(HInvoke* invoke) {
  MoveFPToInt(invoke->GetLocations(), /* is64bit= */ true, GetAssembler());
}
void IntrinsicCodeGeneratorX86::VisitDoubleLongBitsToDouble(HInvoke* invoke) {
  MoveIntToFP(invoke->GetLocations(), /* is64bit= */ true, GetAssembler());
}

void IntrinsicLocationsBuilderX86::VisitFloatFloatToRawIntBits(HInvoke* invoke) {
  CreateFPToIntLocations(allocator_, invoke, /* is64bit= */ false);
}
void IntrinsicLocationsBuilderX86::VisitFloatIntBitsToFloat(HInvoke* invoke) {
  CreateIntToFPLocations(allocator_, invoke, /* is64bit= */ false);
}

void IntrinsicCodeGeneratorX86::VisitFloatFloatToRawIntBits(HInvoke* invoke) {
  MoveFPToInt(invoke->GetLocations(), /* is64bit= */ false, GetAssembler());
}
void IntrinsicCodeGeneratorX86::VisitFloatIntBitsToFloat(HInvoke* invoke) {
  MoveIntToFP(invoke->GetLocations(), /* is64bit= */ false, GetAssembler());
}

static void CreateIntToIntLocations(ArenaAllocator* allocator, HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::RequiresRegister());
  locations->SetOut(Location::SameAsFirstInput());
}

static void CreateLongToIntLocations(ArenaAllocator* allocator, HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::RequiresRegister());
  locations->SetOut(Location::RequiresRegister());
}

static void CreateLongToLongLocations(ArenaAllocator* allocator, HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::RequiresRegister());
  locations->SetOut(Location::RequiresRegister(), Location::kOutputOverlap);
}

static void GenReverseBytes(LocationSummary* locations,
                            DataType::Type size,
                            X86Assembler* assembler) {
  Register out = locations->Out().AsRegister<Register>();

  switch (size) {
    case DataType::Type::kInt16:
      // TODO: Can be done with an xchg of 8b registers. This is straight from Quick.
      __ bswapl(out);
      __ sarl(out, Immediate(16));
      break;
    case DataType::Type::kInt32:
      __ bswapl(out);
      break;
    default:
      LOG(FATAL) << "Unexpected size for reverse-bytes: " << size;
      UNREACHABLE();
  }
}

void IntrinsicLocationsBuilderX86::VisitIntegerReverseBytes(HInvoke* invoke) {
  CreateIntToIntLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86::VisitIntegerReverseBytes(HInvoke* invoke) {
  GenReverseBytes(invoke->GetLocations(), DataType::Type::kInt32, GetAssembler());
}

void IntrinsicLocationsBuilderX86::VisitLongReverseBytes(HInvoke* invoke) {
  CreateLongToLongLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86::VisitLongReverseBytes(HInvoke* invoke) {
  LocationSummary* locations = invoke->GetLocations();
  Location input = locations->InAt(0);
  Register input_lo = input.AsRegisterPairLow<Register>();
  Register input_hi = input.AsRegisterPairHigh<Register>();
  Location output = locations->Out();
  Register output_lo = output.AsRegisterPairLow<Register>();
  Register output_hi = output.AsRegisterPairHigh<Register>();

  X86Assembler* assembler = GetAssembler();
  // Assign the inputs to the outputs, mixing low/high.
  __ movl(output_lo, input_hi);
  __ movl(output_hi, input_lo);
  __ bswapl(output_lo);
  __ bswapl(output_hi);
}

void IntrinsicLocationsBuilderX86::VisitShortReverseBytes(HInvoke* invoke) {
  CreateIntToIntLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86::VisitShortReverseBytes(HInvoke* invoke) {
  GenReverseBytes(invoke->GetLocations(), DataType::Type::kInt16, GetAssembler());
}

static void CreateFPToFPLocations(ArenaAllocator* allocator, HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::RequiresFpuRegister());
  locations->SetOut(Location::RequiresFpuRegister());
}

void IntrinsicLocationsBuilderX86::VisitMathSqrt(HInvoke* invoke) {
  CreateFPToFPLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86::VisitMathSqrt(HInvoke* invoke) {
  LocationSummary* locations = invoke->GetLocations();
  XmmRegister in = locations->InAt(0).AsFpuRegister<XmmRegister>();
  XmmRegister out = locations->Out().AsFpuRegister<XmmRegister>();

  GetAssembler()->sqrtsd(out, in);
}

static void CreateSSE41FPToFPLocations(ArenaAllocator* allocator,
                                       HInvoke* invoke,
                                       CodeGeneratorX86* codegen) {
  // Do we have instruction support?
  if (!codegen->GetInstructionSetFeatures().HasSSE4_1()) {
    return;
  }

  CreateFPToFPLocations(allocator, invoke);
}

static void GenSSE41FPToFPIntrinsic(HInvoke* invoke, X86Assembler* assembler, int round_mode) {
  LocationSummary* locations = invoke->GetLocations();
  DCHECK(!locations->WillCall());
  XmmRegister in = locations->InAt(0).AsFpuRegister<XmmRegister>();
  XmmRegister out = locations->Out().AsFpuRegister<XmmRegister>();
  __ roundsd(out, in, Immediate(round_mode));
}

void IntrinsicLocationsBuilderX86::VisitMathCeil(HInvoke* invoke) {
  CreateSSE41FPToFPLocations(allocator_, invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitMathCeil(HInvoke* invoke) {
  GenSSE41FPToFPIntrinsic(invoke, GetAssembler(), 2);
}

void IntrinsicLocationsBuilderX86::VisitMathFloor(HInvoke* invoke) {
  CreateSSE41FPToFPLocations(allocator_, invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitMathFloor(HInvoke* invoke) {
  GenSSE41FPToFPIntrinsic(invoke, GetAssembler(), 1);
}

void IntrinsicLocationsBuilderX86::VisitMathRint(HInvoke* invoke) {
  CreateSSE41FPToFPLocations(allocator_, invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitMathRint(HInvoke* invoke) {
  GenSSE41FPToFPIntrinsic(invoke, GetAssembler(), 0);
}

void IntrinsicLocationsBuilderX86::VisitMathRoundFloat(HInvoke* invoke) {
  // Do we have instruction support?
  if (!codegen_->GetInstructionSetFeatures().HasSSE4_1()) {
    return;
  }

  HInvokeStaticOrDirect* static_or_direct = invoke->AsInvokeStaticOrDirect();
  LocationSummary* locations =
      new (allocator_) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::RequiresFpuRegister());
  if (static_or_direct->HasSpecialInput() &&
      invoke->InputAt(
          static_or_direct->GetSpecialInputIndex())->IsX86ComputeBaseMethodAddress()) {
    locations->SetInAt(1, Location::RequiresRegister());
  }
  locations->SetOut(Location::RequiresRegister());
  locations->AddTemp(Location::RequiresFpuRegister());
  locations->AddTemp(Location::RequiresFpuRegister());
}

void IntrinsicCodeGeneratorX86::VisitMathRoundFloat(HInvoke* invoke) {
  LocationSummary* locations = invoke->GetLocations();
  DCHECK(!locations->WillCall());

  XmmRegister in = locations->InAt(0).AsFpuRegister<XmmRegister>();
  XmmRegister t1 = locations->GetTemp(0).AsFpuRegister<XmmRegister>();
  XmmRegister t2 = locations->GetTemp(1).AsFpuRegister<XmmRegister>();
  Register out = locations->Out().AsRegister<Register>();
  NearLabel skip_incr, done;
  X86Assembler* assembler = GetAssembler();

  // Since no direct x86 rounding instruction matches the required semantics,
  // this intrinsic is implemented as follows:
  //  result = floor(in);
  //  if (in - result >= 0.5f)
  //    result = result + 1.0f;
  __ movss(t2, in);
  __ roundss(t1, in, Immediate(1));
  __ subss(t2, t1);
  if (locations->GetInputCount() == 2 && locations->InAt(1).IsValid()) {
    // Direct constant area available.
    HX86ComputeBaseMethodAddress* method_address =
        invoke->InputAt(1)->AsX86ComputeBaseMethodAddress();
    Register constant_area = locations->InAt(1).AsRegister<Register>();
    __ comiss(t2, codegen_->LiteralInt32Address(bit_cast<int32_t, float>(0.5f),
                                                method_address,
                                                constant_area));
    __ j(kBelow, &skip_incr);
    __ addss(t1, codegen_->LiteralInt32Address(bit_cast<int32_t, float>(1.0f),
                                               method_address,
                                               constant_area));
    __ Bind(&skip_incr);
  } else {
    // No constant area: go through stack.
    __ pushl(Immediate(bit_cast<int32_t, float>(0.5f)));
    __ pushl(Immediate(bit_cast<int32_t, float>(1.0f)));
    __ comiss(t2, Address(ESP, 4));
    __ j(kBelow, &skip_incr);
    __ addss(t1, Address(ESP, 0));
    __ Bind(&skip_incr);
    __ addl(ESP, Immediate(8));
  }

  // Final conversion to an integer. Unfortunately this also does not have a
  // direct x86 instruction, since NaN should map to 0 and large positive
  // values need to be clipped to the extreme value.
  __ movl(out, Immediate(kPrimIntMax));
  __ cvtsi2ss(t2, out);
  __ comiss(t1, t2);
  __ j(kAboveEqual, &done);  // clipped to max (already in out), does not jump on unordered
  __ movl(out, Immediate(0));  // does not change flags
  __ j(kUnordered, &done);  // NaN mapped to 0 (just moved in out)
  __ cvttss2si(out, t1);
  __ Bind(&done);
}

static void CreateFPToFPCallLocations(ArenaAllocator* allocator, HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kCallOnMainOnly, kIntrinsified);
  InvokeRuntimeCallingConvention calling_convention;
  locations->SetInAt(0, Location::FpuRegisterLocation(calling_convention.GetFpuRegisterAt(0)));
  locations->SetOut(Location::FpuRegisterLocation(XMM0));
}

static void GenFPToFPCall(HInvoke* invoke, CodeGeneratorX86* codegen, QuickEntrypointEnum entry) {
  LocationSummary* locations = invoke->GetLocations();
  DCHECK(locations->WillCall());
  DCHECK(invoke->IsInvokeStaticOrDirect());
  X86Assembler* assembler = codegen->GetAssembler();

  // We need some place to pass the parameters.
  __ subl(ESP, Immediate(16));
  __ cfi().AdjustCFAOffset(16);

  // Pass the parameters at the bottom of the stack.
  __ movsd(Address(ESP, 0), XMM0);

  // If we have a second parameter, pass it next.
  if (invoke->GetNumberOfArguments() == 2) {
    __ movsd(Address(ESP, 8), XMM1);
  }

  // Now do the actual call.
  codegen->InvokeRuntime(entry, invoke, invoke->GetDexPc());

  // Extract the return value from the FP stack.
  __ fstpl(Address(ESP, 0));
  __ movsd(XMM0, Address(ESP, 0));

  // And clean up the stack.
  __ addl(ESP, Immediate(16));
  __ cfi().AdjustCFAOffset(-16);
}

static void CreateLowestOneBitLocations(ArenaAllocator* allocator, bool is_long, HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  if (is_long) {
    locations->SetInAt(0, Location::RequiresRegister());
  } else {
    locations->SetInAt(0, Location::Any());
  }
  locations->SetOut(Location::RequiresRegister(), Location::kOutputOverlap);
}

static void GenLowestOneBit(X86Assembler* assembler,
                      CodeGeneratorX86* codegen,
                      bool is_long,
                      HInvoke* invoke) {
  LocationSummary* locations = invoke->GetLocations();
  Location src = locations->InAt(0);
  Location out_loc = locations->Out();

  if (invoke->InputAt(0)->IsConstant()) {
    // Evaluate this at compile time.
    int64_t value = Int64FromConstant(invoke->InputAt(0)->AsConstant());
    if (value == 0) {
      if (is_long) {
        __ xorl(out_loc.AsRegisterPairLow<Register>(), out_loc.AsRegisterPairLow<Register>());
        __ xorl(out_loc.AsRegisterPairHigh<Register>(), out_loc.AsRegisterPairHigh<Register>());
      } else {
        __ xorl(out_loc.AsRegister<Register>(), out_loc.AsRegister<Register>());
      }
      return;
    }
    // Nonzero value.
    value = is_long ? CTZ(static_cast<uint64_t>(value))
                    : CTZ(static_cast<uint32_t>(value));
    if (is_long) {
      if (value >= 32) {
        int shift = value-32;
        codegen->Load32BitValue(out_loc.AsRegisterPairLow<Register>(), 0);
        codegen->Load32BitValue(out_loc.AsRegisterPairHigh<Register>(), 1 << shift);
      } else {
        codegen->Load32BitValue(out_loc.AsRegisterPairLow<Register>(), 1 << value);
        codegen->Load32BitValue(out_loc.AsRegisterPairHigh<Register>(), 0);
      }
    } else {
      codegen->Load32BitValue(out_loc.AsRegister<Register>(), 1 << value);
    }
    return;
  }
  // Handle non constant case
  if (is_long) {
    DCHECK(src.IsRegisterPair());
    Register src_lo = src.AsRegisterPairLow<Register>();
    Register src_hi = src.AsRegisterPairHigh<Register>();

    Register out_lo = out_loc.AsRegisterPairLow<Register>();
    Register out_hi = out_loc.AsRegisterPairHigh<Register>();

    __ movl(out_lo, src_lo);
    __ movl(out_hi, src_hi);

    __ negl(out_lo);
    __ adcl(out_hi, Immediate(0));
    __ negl(out_hi);

    __ andl(out_lo, src_lo);
    __ andl(out_hi, src_hi);
  } else {
    if (codegen->GetInstructionSetFeatures().HasAVX2() && src.IsRegister()) {
      Register out = out_loc.AsRegister<Register>();
      __ blsi(out, src.AsRegister<Register>());
    } else {
      Register out = out_loc.AsRegister<Register>();
      // Do tmp & -tmp
      if (src.IsRegister()) {
        __ movl(out, src.AsRegister<Register>());
      } else {
        DCHECK(src.IsStackSlot());
        __ movl(out, Address(ESP, src.GetStackIndex()));
      }
      __ negl(out);

      if (src.IsRegister()) {
        __ andl(out, src.AsRegister<Register>());
      } else {
        __ andl(out, Address(ESP, src.GetStackIndex()));
      }
    }
  }
}

void IntrinsicLocationsBuilderX86::VisitMathCos(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86::VisitMathCos(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickCos);
}

void IntrinsicLocationsBuilderX86::VisitMathSin(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86::VisitMathSin(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickSin);
}

void IntrinsicLocationsBuilderX86::VisitMathAcos(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86::VisitMathAcos(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickAcos);
}

void IntrinsicLocationsBuilderX86::VisitMathAsin(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86::VisitMathAsin(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickAsin);
}

void IntrinsicLocationsBuilderX86::VisitMathAtan(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86::VisitMathAtan(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickAtan);
}

void IntrinsicLocationsBuilderX86::VisitMathCbrt(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86::VisitMathCbrt(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickCbrt);
}

void IntrinsicLocationsBuilderX86::VisitMathCosh(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86::VisitMathCosh(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickCosh);
}

void IntrinsicLocationsBuilderX86::VisitMathExp(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86::VisitMathExp(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickExp);
}

void IntrinsicLocationsBuilderX86::VisitMathExpm1(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86::VisitMathExpm1(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickExpm1);
}

void IntrinsicLocationsBuilderX86::VisitMathLog(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86::VisitMathLog(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickLog);
}

void IntrinsicLocationsBuilderX86::VisitMathLog10(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86::VisitMathLog10(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickLog10);
}

void IntrinsicLocationsBuilderX86::VisitMathSinh(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86::VisitMathSinh(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickSinh);
}

void IntrinsicLocationsBuilderX86::VisitMathTan(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86::VisitMathTan(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickTan);
}

void IntrinsicLocationsBuilderX86::VisitMathTanh(HInvoke* invoke) {
  CreateFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86::VisitMathTanh(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickTanh);
}

void IntrinsicLocationsBuilderX86::VisitIntegerLowestOneBit(HInvoke* invoke) {
  CreateLowestOneBitLocations(allocator_, /*is_long=*/ false, invoke);
}
void IntrinsicCodeGeneratorX86::VisitIntegerLowestOneBit(HInvoke* invoke) {
  GenLowestOneBit(GetAssembler(), codegen_, /*is_long=*/ false, invoke);
}

void IntrinsicLocationsBuilderX86::VisitLongLowestOneBit(HInvoke* invoke) {
  CreateLowestOneBitLocations(allocator_, /*is_long=*/ true, invoke);
}

void IntrinsicCodeGeneratorX86::VisitLongLowestOneBit(HInvoke* invoke) {
  GenLowestOneBit(GetAssembler(), codegen_, /*is_long=*/ true, invoke);
}

static void CreateFPFPToFPCallLocations(ArenaAllocator* allocator, HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kCallOnMainOnly, kIntrinsified);
  InvokeRuntimeCallingConvention calling_convention;
  locations->SetInAt(0, Location::FpuRegisterLocation(calling_convention.GetFpuRegisterAt(0)));
  locations->SetInAt(1, Location::FpuRegisterLocation(calling_convention.GetFpuRegisterAt(1)));
  locations->SetOut(Location::FpuRegisterLocation(XMM0));
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

void IntrinsicLocationsBuilderX86::VisitMathAtan2(HInvoke* invoke) {
  CreateFPFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86::VisitMathAtan2(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickAtan2);
}

void IntrinsicLocationsBuilderX86::VisitMathPow(HInvoke* invoke) {
  CreateFPFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86::VisitMathPow(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickPow);
}

void IntrinsicLocationsBuilderX86::VisitMathHypot(HInvoke* invoke) {
  CreateFPFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86::VisitMathHypot(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickHypot);
}

void IntrinsicLocationsBuilderX86::VisitMathNextAfter(HInvoke* invoke) {
  CreateFPFPToFPCallLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86::VisitMathNextAfter(HInvoke* invoke) {
  GenFPToFPCall(invoke, codegen_, kQuickNextAfter);
}

static void CreateSystemArrayCopyLocations(HInvoke* invoke) {
  // We need at least two of the positions or length to be an integer constant,
  // or else we won't have enough free registers.
  HIntConstant* src_pos = invoke->InputAt(1)->AsIntConstantOrNull();
  HIntConstant* dest_pos = invoke->InputAt(3)->AsIntConstantOrNull();
  HIntConstant* length = invoke->InputAt(4)->AsIntConstantOrNull();

  int num_constants =
      ((src_pos != nullptr) ? 1 : 0)
      + ((dest_pos != nullptr) ? 1 : 0)
      + ((length != nullptr) ? 1 : 0);

  if (num_constants < 2) {
    // Not enough free registers.
    return;
  }

  // As long as we are checking, we might as well check to see if the src and dest
  // positions are >= 0.
  if ((src_pos != nullptr && src_pos->GetValue() < 0) ||
      (dest_pos != nullptr && dest_pos->GetValue() < 0)) {
    // We will have to fail anyways.
    return;
  }

  // And since we are already checking, check the length too.
  if (length != nullptr) {
    int32_t len = length->GetValue();
    if (len < 0) {
      // Just call as normal.
      return;
    }
  }

  // Okay, it is safe to generate inline code.
  LocationSummary* locations =
      new (invoke->GetBlock()->GetGraph()->GetAllocator())
      LocationSummary(invoke, LocationSummary::kCallOnSlowPath, kIntrinsified);
  // arraycopy(Object src, int srcPos, Object dest, int destPos, int length).
  locations->SetInAt(0, Location::RequiresRegister());
  locations->SetInAt(1, Location::RegisterOrConstant(invoke->InputAt(1)));
  locations->SetInAt(2, Location::RequiresRegister());
  locations->SetInAt(3, Location::RegisterOrConstant(invoke->InputAt(3)));
  locations->SetInAt(4, Location::RegisterOrConstant(invoke->InputAt(4)));

  // And we need some temporaries.  We will use REP MOVS{B,W,L}, so we need fixed registers.
  locations->AddTemp(Location::RegisterLocation(ESI));
  locations->AddTemp(Location::RegisterLocation(EDI));
  locations->AddTemp(Location::RegisterLocation(ECX));
}

template <typename LhsType>
static void EmitCmplJLess(X86Assembler* assembler,
                          LhsType lhs,
                          Location rhs,
                          Label* label) {
  static_assert(std::is_same_v<LhsType, Register> || std::is_same_v<LhsType, Address>);
  if (rhs.IsConstant()) {
    int32_t rhs_constant = rhs.GetConstant()->AsIntConstant()->GetValue();
    __ cmpl(lhs, Immediate(rhs_constant));
  } else {
    __ cmpl(lhs, rhs.AsRegister<Register>());
  }
  __ j(kLess, label);
}

static void CheckSystemArrayCopyPosition(X86Assembler* assembler,
                                         Register array,
                                         Location pos,
                                         Location length,
                                         SlowPathCode* slow_path,
                                         Register temp,
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
    Register pos_reg = pos.AsRegister<Register>();
    __ testl(pos_reg, pos_reg);
    __ j(kNotEqual, slow_path->GetEntryLabel());
  } else {
    // Check that pos >= 0.
    Register pos_reg = pos.AsRegister<Register>();
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
                                     X86Assembler* assembler,
                                     CodeGeneratorX86* codegen,
                                     DataType::Type type) {
  LocationSummary* locations = invoke->GetLocations();
  Register src = locations->InAt(0).AsRegister<Register>();
  Location src_pos = locations->InAt(1);
  Register dest = locations->InAt(2).AsRegister<Register>();
  Location dest_pos = locations->InAt(3);
  Location length = locations->InAt(4);

  // Temporaries that we need for MOVSB/W/L.
  Register src_base = locations->GetTemp(0).AsRegister<Register>();
  DCHECK_EQ(src_base, ESI);
  Register dest_base = locations->GetTemp(1).AsRegister<Register>();
  DCHECK_EQ(dest_base, EDI);
  Register count = locations->GetTemp(2).AsRegister<Register>();
  DCHECK_EQ(count, ECX);

  SlowPathCode* slow_path = new (codegen->GetScopedAllocator()) IntrinsicSlowPathX86(invoke);
  codegen->AddSlowPath(slow_path);

  // Bail out if the source and destination are the same (to handle overlap).
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
    __ cmpl(length.AsRegister<Register>(), length.AsRegister<Register>());
    __ j(kLess, slow_path->GetEntryLabel());
  }

  // We need the count in ECX.
  if (length.IsConstant()) {
    __ movl(count, Immediate(length.GetConstant()->AsIntConstant()->GetValue()));
  } else {
    __ movl(count, length.AsRegister<Register>());
  }

  // Validity checks: source. Use src_base as a temporary register.
  CheckSystemArrayCopyPosition(assembler,
                               src,
                               src_pos,
                               Location::RegisterLocation(count),
                               slow_path,
                               src_base,
                               /*length_is_array_length=*/ false,
                               /*position_sign_checked=*/ false);

  // Validity checks: dest. Use src_base as a temporary register.
  CheckSystemArrayCopyPosition(assembler,
                               dest,
                               dest_pos,
                               Location::RegisterLocation(count),
                               slow_path,
                               src_base,
                               /*length_is_array_length=*/ false,
                               /*position_sign_checked=*/ false);

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

void IntrinsicLocationsBuilderX86::VisitSystemArrayCopyChar(HInvoke* invoke) {
  CreateSystemArrayCopyLocations(invoke);
}

void IntrinsicCodeGeneratorX86::VisitSystemArrayCopyChar(HInvoke* invoke) {
  X86Assembler* assembler = GetAssembler();
  SystemArrayCopyPrimitive(invoke, assembler, codegen_, DataType::Type::kUint16);
}

void IntrinsicCodeGeneratorX86::VisitSystemArrayCopyByte(HInvoke* invoke) {
  X86Assembler* assembler = GetAssembler();
  SystemArrayCopyPrimitive(invoke, assembler, codegen_, DataType::Type::kInt8);
}

void IntrinsicLocationsBuilderX86::VisitSystemArrayCopyByte(HInvoke* invoke) {
  CreateSystemArrayCopyLocations(invoke);
}

void IntrinsicCodeGeneratorX86::VisitSystemArrayCopyInt(HInvoke* invoke) {
  X86Assembler* assembler = GetAssembler();
  SystemArrayCopyPrimitive(invoke, assembler, codegen_, DataType::Type::kInt32);
}

void IntrinsicLocationsBuilderX86::VisitSystemArrayCopyInt(HInvoke* invoke) {
  CreateSystemArrayCopyLocations(invoke);
}

void IntrinsicLocationsBuilderX86::VisitStringCompareTo(HInvoke* invoke) {
  // The inputs plus one temp.
  LocationSummary* locations = new (allocator_) LocationSummary(
      invoke, LocationSummary::kCallOnMainAndSlowPath, kIntrinsified);
  InvokeRuntimeCallingConvention calling_convention;
  locations->SetInAt(0, Location::RegisterLocation(calling_convention.GetRegisterAt(0)));
  locations->SetInAt(1, Location::RegisterLocation(calling_convention.GetRegisterAt(1)));
  locations->SetOut(Location::RegisterLocation(EAX));
}

void IntrinsicCodeGeneratorX86::VisitStringCompareTo(HInvoke* invoke) {
  X86Assembler* assembler = GetAssembler();
  LocationSummary* locations = invoke->GetLocations();

  // Note that the null check must have been done earlier.
  DCHECK(!invoke->CanDoImplicitNullCheckOn(invoke->InputAt(0)));

  Register argument = locations->InAt(1).AsRegister<Register>();
  __ testl(argument, argument);
  SlowPathCode* slow_path = new (codegen_->GetScopedAllocator()) IntrinsicSlowPathX86(invoke);
  codegen_->AddSlowPath(slow_path);
  __ j(kEqual, slow_path->GetEntryLabel());

  codegen_->InvokeRuntime(kQuickStringCompareTo, invoke, invoke->GetDexPc(), slow_path);
  __ Bind(slow_path->GetExitLabel());
}

void IntrinsicLocationsBuilderX86::VisitStringEquals(HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator_) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::RequiresRegister());
  locations->SetInAt(1, Location::RequiresRegister());

  // Request temporary registers, ECX and EDI needed for repe_cmpsl instruction.
  locations->AddTemp(Location::RegisterLocation(ECX));
  locations->AddTemp(Location::RegisterLocation(EDI));

  // Set output, ESI needed for repe_cmpsl instruction anyways.
  locations->SetOut(Location::RegisterLocation(ESI), Location::kOutputOverlap);
}

void IntrinsicCodeGeneratorX86::VisitStringEquals(HInvoke* invoke) {
  X86Assembler* assembler = GetAssembler();
  LocationSummary* locations = invoke->GetLocations();

  Register str = locations->InAt(0).AsRegister<Register>();
  Register arg = locations->InAt(1).AsRegister<Register>();
  Register ecx = locations->GetTemp(0).AsRegister<Register>();
  Register edi = locations->GetTemp(1).AsRegister<Register>();
  Register esi = locations->Out().AsRegister<Register>();

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
    // /* HeapReference<Class> */ ecx = str->klass_
    __ movl(ecx, Address(str, class_offset));
    // if (ecx != /* HeapReference<Class> */ arg->klass_) return false
    __ cmpl(ecx, Address(arg, class_offset));
    __ j(kNotEqual, &return_false);
  }

  // Reference equality check, return true if same reference.
  __ cmpl(str, arg);
  __ j(kEqual, &return_true);

  // Load length and compression flag of receiver string.
  __ movl(ecx, Address(str, count_offset));
  // Check if lengths and compression flags are equal, return false if they're not.
  // Two identical strings will always have same compression style since
  // compression style is decided on alloc.
  __ cmpl(ecx, Address(arg, count_offset));
  __ j(kNotEqual, &return_false);
  // Return true if strings are empty. Even with string compression `count == 0` means empty.
  static_assert(static_cast<uint32_t>(mirror::StringCompressionFlag::kCompressed) == 0u,
                "Expecting 0=compressed, 1=uncompressed");
  __ jecxz(&return_true);

  if (mirror::kUseStringCompression) {
    NearLabel string_uncompressed;
    // Extract length and differentiate between both compressed or both uncompressed.
    // Different compression style is cut above.
    __ shrl(ecx, Immediate(1));
    __ j(kCarrySet, &string_uncompressed);
    // Divide string length by 2, rounding up, and continue as if uncompressed.
    __ addl(ecx, Immediate(1));
    __ shrl(ecx, Immediate(1));
    __ Bind(&string_uncompressed);
  }
  // Load starting addresses of string values into ESI/EDI as required for repe_cmpsl instruction.
  __ leal(esi, Address(str, value_offset));
  __ leal(edi, Address(arg, value_offset));

  // Divide string length by 2 to compare characters 2 at a time and adjust for lengths not
  // divisible by 2.
  __ addl(ecx, Immediate(1));
  __ shrl(ecx, Immediate(1));

  // Assertions that must hold in order to compare strings 2 characters (uncompressed)
  // or 4 characters (compressed) at a time.
  DCHECK_ALIGNED(value_offset, 4);
  static_assert(IsAligned<4>(kObjectAlignment), "String of odd length is not zero padded");

  // Loop to compare strings two characters at a time starting at the beginning of the string.
  __ repe_cmpsl();
  // If strings are not equal, zero flag will be cleared.
  __ j(kNotEqual, &return_false);

  // Return true and exit the function.
  // If loop does not result in returning false, we return true.
  __ Bind(&return_true);
  __ movl(esi, Immediate(1));
  __ jmp(&end);

  // Return false and exit the function.
  __ Bind(&return_false);
  __ xorl(esi, esi);
  __ Bind(&end);
}

static void CreateStringIndexOfLocations(HInvoke* invoke,
                                         ArenaAllocator* allocator,
                                         bool start_at_zero) {
  LocationSummary* locations = new (allocator) LocationSummary(invoke,
                                                               LocationSummary::kCallOnSlowPath,
                                                               kIntrinsified);
  // The data needs to be in EDI for scasw. So request that the string is there, anyways.
  locations->SetInAt(0, Location::RegisterLocation(EDI));
  // If we look for a constant char, we'll still have to copy it into EAX. So just request the
  // allocator to do that, anyways. We can still do the constant check by checking the parameter
  // of the instruction explicitly.
  // Note: This works as we don't clobber EAX anywhere.
  locations->SetInAt(1, Location::RegisterLocation(EAX));
  if (!start_at_zero) {
    locations->SetInAt(2, Location::RequiresRegister());          // The starting index.
  }
  // As we clobber EDI during execution anyways, also use it as the output.
  locations->SetOut(Location::SameAsFirstInput());

  // repne scasw uses ECX as the counter.
  locations->AddTemp(Location::RegisterLocation(ECX));
  // Need another temporary to be able to compute the result.
  locations->AddTemp(Location::RequiresRegister());
  if (mirror::kUseStringCompression) {
    // Need another temporary to be able to save unflagged string length.
    locations->AddTemp(Location::RequiresRegister());
  }
}

static void GenerateStringIndexOf(HInvoke* invoke,
                                  X86Assembler* assembler,
                                  CodeGeneratorX86* codegen,
                                  bool start_at_zero) {
  LocationSummary* locations = invoke->GetLocations();

  // Note that the null check must have been done earlier.
  DCHECK(!invoke->CanDoImplicitNullCheckOn(invoke->InputAt(0)));

  Register string_obj = locations->InAt(0).AsRegister<Register>();
  Register search_value = locations->InAt(1).AsRegister<Register>();
  Register counter = locations->GetTemp(0).AsRegister<Register>();
  Register string_length = locations->GetTemp(1).AsRegister<Register>();
  Register out = locations->Out().AsRegister<Register>();
  // Only used when string compression feature is on.
  Register string_length_flagged;

  // Check our assumptions for registers.
  DCHECK_EQ(string_obj, EDI);
  DCHECK_EQ(search_value, EAX);
  DCHECK_EQ(counter, ECX);
  DCHECK_EQ(out, EDI);

  // Check for code points > 0xFFFF. Either a slow-path check when we don't know statically,
  // or directly dispatch for a large constant, or omit slow-path for a small constant or a char.
  SlowPathCode* slow_path = nullptr;
  HInstruction* code_point = invoke->InputAt(1);
  if (code_point->IsIntConstant()) {
    if (static_cast<uint32_t>(code_point->AsIntConstant()->GetValue()) >
        std::numeric_limits<uint16_t>::max()) {
      // Always needs the slow-path. We could directly dispatch to it, but this case should be
      // rare, so for simplicity just put the full slow-path down and branch unconditionally.
      slow_path = new (codegen->GetScopedAllocator()) IntrinsicSlowPathX86(invoke);
      codegen->AddSlowPath(slow_path);
      __ jmp(slow_path->GetEntryLabel());
      __ Bind(slow_path->GetExitLabel());
      return;
    }
  } else if (code_point->GetType() != DataType::Type::kUint16) {
    __ cmpl(search_value, Immediate(std::numeric_limits<uint16_t>::max()));
    slow_path = new (codegen->GetScopedAllocator()) IntrinsicSlowPathX86(invoke);
    codegen->AddSlowPath(slow_path);
    __ j(kAbove, slow_path->GetEntryLabel());
  }

  // From here down, we know that we are looking for a char that fits in 16 bits.
  // Location of reference to data array within the String object.
  int32_t value_offset = mirror::String::ValueOffset().Int32Value();
  // Location of count within the String object.
  int32_t count_offset = mirror::String::CountOffset().Int32Value();

  // Load the count field of the string containing the length and compression flag.
  __ movl(string_length, Address(string_obj, count_offset));

  // Do a zero-length check. Even with string compression `count == 0` means empty.
  static_assert(static_cast<uint32_t>(mirror::StringCompressionFlag::kCompressed) == 0u,
                "Expecting 0=compressed, 1=uncompressed");
  // TODO: Support jecxz.
  NearLabel not_found_label;
  __ testl(string_length, string_length);
  __ j(kEqual, &not_found_label);

  if (mirror::kUseStringCompression) {
    string_length_flagged = locations->GetTemp(2).AsRegister<Register>();
    __ movl(string_length_flagged, string_length);
    // Extract the length and shift out the least significant bit used as compression flag.
    __ shrl(string_length, Immediate(1));
  }

  if (start_at_zero) {
    // Number of chars to scan is the same as the string length.
    __ movl(counter, string_length);

    // Move to the start of the string.
    __ addl(string_obj, Immediate(value_offset));
  } else {
    Register start_index = locations->InAt(2).AsRegister<Register>();

    // Do a start_index check.
    __ cmpl(start_index, string_length);
    __ j(kGreaterEqual, &not_found_label);

    // Ensure we have a start index >= 0;
    __ xorl(counter, counter);
    __ cmpl(start_index, Immediate(0));
    __ cmovl(kGreater, counter, start_index);

    if (mirror::kUseStringCompression) {
      NearLabel modify_counter, offset_uncompressed_label;
      __ testl(string_length_flagged, Immediate(1));
      __ j(kNotZero, &offset_uncompressed_label);
      // Move to the start of the string: string_obj + value_offset + start_index.
      __ leal(string_obj, Address(string_obj, counter, ScaleFactor::TIMES_1, value_offset));
      __ jmp(&modify_counter);

      // Move to the start of the string: string_obj + value_offset + 2 * start_index.
      __ Bind(&offset_uncompressed_label);
      __ leal(string_obj, Address(string_obj, counter, ScaleFactor::TIMES_2, value_offset));

      // Now update ecx (the repne scasw work counter). We have string.length - start_index left to
      // compare.
      __ Bind(&modify_counter);
    } else {
      __ leal(string_obj, Address(string_obj, counter, ScaleFactor::TIMES_2, value_offset));
    }
    __ negl(counter);
    __ leal(counter, Address(string_length, counter, ScaleFactor::TIMES_1, 0));
  }

  if (mirror::kUseStringCompression) {
    NearLabel uncompressed_string_comparison;
    NearLabel comparison_done;
    __ testl(string_length_flagged, Immediate(1));
    __ j(kNotZero, &uncompressed_string_comparison);

    // Check if EAX (search_value) is ASCII.
    __ cmpl(search_value, Immediate(127));
    __ j(kGreater, &not_found_label);
    // Comparing byte-per-byte.
    __ repne_scasb();
    __ jmp(&comparison_done);

    // Everything is set up for repne scasw:
    //   * Comparison address in EDI.
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

void IntrinsicLocationsBuilderX86::VisitStringIndexOf(HInvoke* invoke) {
  CreateStringIndexOfLocations(invoke, allocator_, /* start_at_zero= */ true);
}

void IntrinsicCodeGeneratorX86::VisitStringIndexOf(HInvoke* invoke) {
  GenerateStringIndexOf(invoke, GetAssembler(), codegen_, /* start_at_zero= */ true);
}

void IntrinsicLocationsBuilderX86::VisitStringIndexOfAfter(HInvoke* invoke) {
  CreateStringIndexOfLocations(invoke, allocator_, /* start_at_zero= */ false);
}

void IntrinsicCodeGeneratorX86::VisitStringIndexOfAfter(HInvoke* invoke) {
  GenerateStringIndexOf(invoke, GetAssembler(), codegen_, /* start_at_zero= */ false);
}

void IntrinsicLocationsBuilderX86::VisitStringNewStringFromBytes(HInvoke* invoke) {
  LocationSummary* locations = new (allocator_) LocationSummary(
      invoke, LocationSummary::kCallOnMainAndSlowPath, kIntrinsified);
  InvokeRuntimeCallingConvention calling_convention;
  locations->SetInAt(0, Location::RegisterLocation(calling_convention.GetRegisterAt(0)));
  locations->SetInAt(1, Location::RegisterLocation(calling_convention.GetRegisterAt(1)));
  locations->SetInAt(2, Location::RegisterLocation(calling_convention.GetRegisterAt(2)));
  locations->SetInAt(3, Location::RegisterLocation(calling_convention.GetRegisterAt(3)));
  locations->SetOut(Location::RegisterLocation(EAX));
}

void IntrinsicCodeGeneratorX86::VisitStringNewStringFromBytes(HInvoke* invoke) {
  X86Assembler* assembler = GetAssembler();
  LocationSummary* locations = invoke->GetLocations();

  Register byte_array = locations->InAt(0).AsRegister<Register>();
  __ testl(byte_array, byte_array);
  SlowPathCode* slow_path = new (codegen_->GetScopedAllocator()) IntrinsicSlowPathX86(invoke);
  codegen_->AddSlowPath(slow_path);
  __ j(kEqual, slow_path->GetEntryLabel());

  codegen_->InvokeRuntime(kQuickAllocStringFromBytes, invoke, invoke->GetDexPc());
  CheckEntrypointTypes<kQuickAllocStringFromBytes, void*, void*, int32_t, int32_t, int32_t>();
  __ Bind(slow_path->GetExitLabel());
}

void IntrinsicLocationsBuilderX86::VisitStringNewStringFromChars(HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator_) LocationSummary(invoke, LocationSummary::kCallOnMainOnly, kIntrinsified);
  InvokeRuntimeCallingConvention calling_convention;
  locations->SetInAt(0, Location::RegisterLocation(calling_convention.GetRegisterAt(0)));
  locations->SetInAt(1, Location::RegisterLocation(calling_convention.GetRegisterAt(1)));
  locations->SetInAt(2, Location::RegisterLocation(calling_convention.GetRegisterAt(2)));
  locations->SetOut(Location::RegisterLocation(EAX));
}

void IntrinsicCodeGeneratorX86::VisitStringNewStringFromChars(HInvoke* invoke) {
  // No need to emit code checking whether `locations->InAt(2)` is a null
  // pointer, as callers of the native method
  //
  //   java.lang.StringFactory.newStringFromChars(int offset, int charCount, char[] data)
  //
  // all include a null check on `data` before calling that method.
  codegen_->InvokeRuntime(kQuickAllocStringFromChars, invoke, invoke->GetDexPc());
  CheckEntrypointTypes<kQuickAllocStringFromChars, void*, int32_t, int32_t, void*>();
}

void IntrinsicLocationsBuilderX86::VisitStringNewStringFromString(HInvoke* invoke) {
  LocationSummary* locations = new (allocator_) LocationSummary(
      invoke, LocationSummary::kCallOnMainAndSlowPath, kIntrinsified);
  InvokeRuntimeCallingConvention calling_convention;
  locations->SetInAt(0, Location::RegisterLocation(calling_convention.GetRegisterAt(0)));
  locations->SetOut(Location::RegisterLocation(EAX));
}

void IntrinsicCodeGeneratorX86::VisitStringNewStringFromString(HInvoke* invoke) {
  X86Assembler* assembler = GetAssembler();
  LocationSummary* locations = invoke->GetLocations();

  Register string_to_copy = locations->InAt(0).AsRegister<Register>();
  __ testl(string_to_copy, string_to_copy);
  SlowPathCode* slow_path = new (codegen_->GetScopedAllocator()) IntrinsicSlowPathX86(invoke);
  codegen_->AddSlowPath(slow_path);
  __ j(kEqual, slow_path->GetEntryLabel());

  codegen_->InvokeRuntime(kQuickAllocStringFromString, invoke, invoke->GetDexPc());
  CheckEntrypointTypes<kQuickAllocStringFromString, void*, void*>();
  __ Bind(slow_path->GetExitLabel());
}

void IntrinsicLocationsBuilderX86::VisitStringGetCharsNoCheck(HInvoke* invoke) {
  // public void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin);
  LocationSummary* locations =
      new (allocator_) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::RequiresRegister());
  locations->SetInAt(1, Location::RegisterOrConstant(invoke->InputAt(1)));
  // Place srcEnd in ECX to save a move below.
  locations->SetInAt(2, Location::RegisterLocation(ECX));
  locations->SetInAt(3, Location::RequiresRegister());
  locations->SetInAt(4, Location::RequiresRegister());

  // And we need some temporaries.  We will use REP MOVSW, so we need fixed registers.
  // We don't have enough registers to also grab ECX, so handle below.
  locations->AddTemp(Location::RegisterLocation(ESI));
  locations->AddTemp(Location::RegisterLocation(EDI));
}

void IntrinsicCodeGeneratorX86::VisitStringGetCharsNoCheck(HInvoke* invoke) {
  X86Assembler* assembler = GetAssembler();
  LocationSummary* locations = invoke->GetLocations();

  size_t char_component_size = DataType::Size(DataType::Type::kUint16);
  // Location of data in char array buffer.
  const uint32_t data_offset = mirror::Array::DataOffset(char_component_size).Uint32Value();
  // Location of char array data in string.
  const uint32_t value_offset = mirror::String::ValueOffset().Uint32Value();

  // public void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin);
  Register obj = locations->InAt(0).AsRegister<Register>();
  Location srcBegin = locations->InAt(1);
  int srcBegin_value =
      srcBegin.IsConstant() ? srcBegin.GetConstant()->AsIntConstant()->GetValue() : 0;
  Register srcEnd = locations->InAt(2).AsRegister<Register>();
  Register dst = locations->InAt(3).AsRegister<Register>();
  Register dstBegin = locations->InAt(4).AsRegister<Register>();

  // Check assumption that sizeof(Char) is 2 (used in scaling below).
  const size_t char_size = DataType::Size(DataType::Type::kUint16);
  DCHECK_EQ(char_size, 2u);

  // Compute the number of chars (words) to move.
  // Save ECX, since we don't know if it will be used later.
  __ pushl(ECX);
  int stack_adjust = kX86WordSize;
  __ cfi().AdjustCFAOffset(stack_adjust);
  DCHECK_EQ(srcEnd, ECX);
  if (srcBegin.IsConstant()) {
    __ subl(ECX, Immediate(srcBegin_value));
  } else {
    DCHECK(srcBegin.IsRegister());
    __ subl(ECX, srcBegin.AsRegister<Register>());
  }

  NearLabel done;
  if (mirror::kUseStringCompression) {
    // Location of count in string
    const uint32_t count_offset = mirror::String::CountOffset().Uint32Value();
    const size_t c_char_size = DataType::Size(DataType::Type::kInt8);
    DCHECK_EQ(c_char_size, 1u);
    __ pushl(EAX);
    __ cfi().AdjustCFAOffset(stack_adjust);

    NearLabel copy_loop, copy_uncompressed;
    __ testl(Address(obj, count_offset), Immediate(1));
    static_assert(static_cast<uint32_t>(mirror::StringCompressionFlag::kCompressed) == 0u,
                  "Expecting 0=compressed, 1=uncompressed");
    __ j(kNotZero, &copy_uncompressed);
    // Compute the address of the source string by adding the number of chars from
    // the source beginning to the value offset of a string.
    __ leal(ESI, CodeGeneratorX86::ArrayAddress(obj, srcBegin, TIMES_1, value_offset));

    // Start the loop to copy String's value to Array of Char.
    __ leal(EDI, Address(dst, dstBegin, ScaleFactor::TIMES_2, data_offset));
    __ Bind(&copy_loop);
    __ jecxz(&done);
    // Use EAX temporary (convert byte from ESI to word).
    // TODO: Use LODSB/STOSW (not supported by X86Assembler) with AH initialized to 0.
    __ movzxb(EAX, Address(ESI, 0));
    __ movw(Address(EDI, 0), EAX);
    __ leal(EDI, Address(EDI, char_size));
    __ leal(ESI, Address(ESI, c_char_size));
    // TODO: Add support for LOOP to X86Assembler.
    __ subl(ECX, Immediate(1));
    __ jmp(&copy_loop);
    __ Bind(&copy_uncompressed);
  }

  // Do the copy for uncompressed string.
  // Compute the address of the destination buffer.
  __ leal(EDI, Address(dst, dstBegin, ScaleFactor::TIMES_2, data_offset));
  __ leal(ESI, CodeGeneratorX86::ArrayAddress(obj, srcBegin, TIMES_2, value_offset));
  __ rep_movsw();

  __ Bind(&done);
  if (mirror::kUseStringCompression) {
    // Restore EAX.
    __ popl(EAX);
    __ cfi().AdjustCFAOffset(-stack_adjust);
  }
  // Restore ECX.
  __ popl(ECX);
  __ cfi().AdjustCFAOffset(-stack_adjust);
}

static void GenPeek(LocationSummary* locations, DataType::Type size, X86Assembler* assembler) {
  Register address = locations->InAt(0).AsRegisterPairLow<Register>();
  Location out_loc = locations->Out();
  // x86 allows unaligned access. We do not have to check the input or use specific instructions
  // to avoid a SIGBUS.
  switch (size) {
    case DataType::Type::kInt8:
      __ movsxb(out_loc.AsRegister<Register>(), Address(address, 0));
      break;
    case DataType::Type::kInt16:
      __ movsxw(out_loc.AsRegister<Register>(), Address(address, 0));
      break;
    case DataType::Type::kInt32:
      __ movl(out_loc.AsRegister<Register>(), Address(address, 0));
      break;
    case DataType::Type::kInt64:
      __ movl(out_loc.AsRegisterPairLow<Register>(), Address(address, 0));
      __ movl(out_loc.AsRegisterPairHigh<Register>(), Address(address, 4));
      break;
    default:
      LOG(FATAL) << "Type not recognized for peek: " << size;
      UNREACHABLE();
  }
}

void IntrinsicLocationsBuilderX86::VisitMemoryPeekByte(HInvoke* invoke) {
  CreateLongToIntLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86::VisitMemoryPeekByte(HInvoke* invoke) {
  GenPeek(invoke->GetLocations(), DataType::Type::kInt8, GetAssembler());
}

void IntrinsicLocationsBuilderX86::VisitMemoryPeekIntNative(HInvoke* invoke) {
  CreateLongToIntLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86::VisitMemoryPeekIntNative(HInvoke* invoke) {
  GenPeek(invoke->GetLocations(), DataType::Type::kInt32, GetAssembler());
}

void IntrinsicLocationsBuilderX86::VisitMemoryPeekLongNative(HInvoke* invoke) {
  CreateLongToLongLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86::VisitMemoryPeekLongNative(HInvoke* invoke) {
  GenPeek(invoke->GetLocations(), DataType::Type::kInt64, GetAssembler());
}

void IntrinsicLocationsBuilderX86::VisitMemoryPeekShortNative(HInvoke* invoke) {
  CreateLongToIntLocations(allocator_, invoke);
}

void IntrinsicCodeGeneratorX86::VisitMemoryPeekShortNative(HInvoke* invoke) {
  GenPeek(invoke->GetLocations(), DataType::Type::kInt16, GetAssembler());
}

static void CreateLongIntToVoidLocations(ArenaAllocator* allocator,
                                         DataType::Type size,
                                         HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::RequiresRegister());
  HInstruction* value = invoke->InputAt(1);
  if (size == DataType::Type::kInt8) {
    locations->SetInAt(1, Location::ByteRegisterOrConstant(EDX, value));
  } else {
    locations->SetInAt(1, Location::RegisterOrConstant(value));
  }
}

static void GenPoke(LocationSummary* locations, DataType::Type size, X86Assembler* assembler) {
  Register address = locations->InAt(0).AsRegisterPairLow<Register>();
  Location value_loc = locations->InAt(1);
  // x86 allows unaligned access. We do not have to check the input or use specific instructions
  // to avoid a SIGBUS.
  switch (size) {
    case DataType::Type::kInt8:
      if (value_loc.IsConstant()) {
        __ movb(Address(address, 0),
                Immediate(value_loc.GetConstant()->AsIntConstant()->GetValue()));
      } else {
        __ movb(Address(address, 0), value_loc.AsRegister<ByteRegister>());
      }
      break;
    case DataType::Type::kInt16:
      if (value_loc.IsConstant()) {
        __ movw(Address(address, 0),
                Immediate(value_loc.GetConstant()->AsIntConstant()->GetValue()));
      } else {
        __ movw(Address(address, 0), value_loc.AsRegister<Register>());
      }
      break;
    case DataType::Type::kInt32:
      if (value_loc.IsConstant()) {
        __ movl(Address(address, 0),
                Immediate(value_loc.GetConstant()->AsIntConstant()->GetValue()));
      } else {
        __ movl(Address(address, 0), value_loc.AsRegister<Register>());
      }
      break;
    case DataType::Type::kInt64:
      if (value_loc.IsConstant()) {
        int64_t value = value_loc.GetConstant()->AsLongConstant()->GetValue();
        __ movl(Address(address, 0), Immediate(Low32Bits(value)));
        __ movl(Address(address, 4), Immediate(High32Bits(value)));
      } else {
        __ movl(Address(address, 0), value_loc.AsRegisterPairLow<Register>());
        __ movl(Address(address, 4), value_loc.AsRegisterPairHigh<Register>());
      }
      break;
    default:
      LOG(FATAL) << "Type not recognized for poke: " << size;
      UNREACHABLE();
  }
}

void IntrinsicLocationsBuilderX86::VisitMemoryPokeByte(HInvoke* invoke) {
  CreateLongIntToVoidLocations(allocator_, DataType::Type::kInt8, invoke);
}

void IntrinsicCodeGeneratorX86::VisitMemoryPokeByte(HInvoke* invoke) {
  GenPoke(invoke->GetLocations(), DataType::Type::kInt8, GetAssembler());
}

void IntrinsicLocationsBuilderX86::VisitMemoryPokeIntNative(HInvoke* invoke) {
  CreateLongIntToVoidLocations(allocator_, DataType::Type::kInt32, invoke);
}

void IntrinsicCodeGeneratorX86::VisitMemoryPokeIntNative(HInvoke* invoke) {
  GenPoke(invoke->GetLocations(), DataType::Type::kInt32, GetAssembler());
}

void IntrinsicLocationsBuilderX86::VisitMemoryPokeLongNative(HInvoke* invoke) {
  CreateLongIntToVoidLocations(allocator_, DataType::Type::kInt64, invoke);
}

void IntrinsicCodeGeneratorX86::VisitMemoryPokeLongNative(HInvoke* invoke) {
  GenPoke(invoke->GetLocations(), DataType::Type::kInt64, GetAssembler());
}

void IntrinsicLocationsBuilderX86::VisitMemoryPokeShortNative(HInvoke* invoke) {
  CreateLongIntToVoidLocations(allocator_, DataType::Type::kInt16, invoke);
}

void IntrinsicCodeGeneratorX86::VisitMemoryPokeShortNative(HInvoke* invoke) {
  GenPoke(invoke->GetLocations(), DataType::Type::kInt16, GetAssembler());
}

void IntrinsicLocationsBuilderX86::VisitThreadCurrentThread(HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator_) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetOut(Location::RequiresRegister());
}

void IntrinsicCodeGeneratorX86::VisitThreadCurrentThread(HInvoke* invoke) {
  Register out = invoke->GetLocations()->Out().AsRegister<Register>();
  GetAssembler()->fs()->movl(out, Address::Absolute(Thread::PeerOffset<kX86PointerSize>()));
}

static void GenUnsafeGet(HInvoke* invoke,
                         DataType::Type type,
                         bool is_volatile,
                         CodeGeneratorX86* codegen) {
  X86Assembler* assembler = down_cast<X86Assembler*>(codegen->GetAssembler());
  LocationSummary* locations = invoke->GetLocations();
  Location base_loc = locations->InAt(1);
  Register base = base_loc.AsRegister<Register>();
  Location offset_loc = locations->InAt(2);
  Register offset = offset_loc.AsRegisterPairLow<Register>();
  Location output_loc = locations->Out();

  switch (type) {
    case DataType::Type::kInt8: {
      Register output = output_loc.AsRegister<Register>();
      __ movsxb(output, Address(base, offset, ScaleFactor::TIMES_1, 0));
      break;
    }

    case DataType::Type::kInt32: {
      Register output = output_loc.AsRegister<Register>();
      __ movl(output, Address(base, offset, ScaleFactor::TIMES_1, 0));
      break;
    }

    case DataType::Type::kReference: {
      Register output = output_loc.AsRegister<Register>();
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

    case DataType::Type::kInt64: {
        Register output_lo = output_loc.AsRegisterPairLow<Register>();
        Register output_hi = output_loc.AsRegisterPairHigh<Register>();
        if (is_volatile) {
          // Need to use a XMM to read atomically.
          XmmRegister temp = locations->GetTemp(0).AsFpuRegister<XmmRegister>();
          __ movsd(temp, Address(base, offset, ScaleFactor::TIMES_1, 0));
          __ movd(output_lo, temp);
          __ psrlq(temp, Immediate(32));
          __ movd(output_hi, temp);
        } else {
          __ movl(output_lo, Address(base, offset, ScaleFactor::TIMES_1, 0));
          __ movl(output_hi, Address(base, offset, ScaleFactor::TIMES_1, 4));
        }
      }
      break;

    default:
      LOG(FATAL) << "Unsupported op size " << type;
      UNREACHABLE();
  }
}

static void CreateIntIntIntToIntLocations(ArenaAllocator* allocator,
                                          HInvoke* invoke,
                                          CodeGeneratorX86* codegen,
                                          DataType::Type type,
                                          bool is_volatile) {
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
  if (type == DataType::Type::kInt64) {
    if (is_volatile) {
      // Need to use XMM to read volatile.
      locations->AddTemp(Location::RequiresFpuRegister());
      locations->SetOut(Location::RequiresRegister(), Location::kNoOutputOverlap);
    } else {
      locations->SetOut(Location::RequiresRegister(), Location::kOutputOverlap);
    }
  } else {
    locations->SetOut(Location::RequiresRegister(),
                      (can_call ? Location::kOutputOverlap : Location::kNoOutputOverlap));
  }
}

void IntrinsicLocationsBuilderX86::VisitUnsafeGet(HInvoke* invoke) {
  VisitJdkUnsafeGet(invoke);
}
void IntrinsicLocationsBuilderX86::VisitUnsafeGetVolatile(HInvoke* invoke) {
  VisitJdkUnsafeGetVolatile(invoke);
}
void IntrinsicLocationsBuilderX86::VisitUnsafeGetLong(HInvoke* invoke) {
  VisitJdkUnsafeGetLong(invoke);
}
void IntrinsicLocationsBuilderX86::VisitUnsafeGetLongVolatile(HInvoke* invoke) {
  VisitJdkUnsafeGetLongVolatile(invoke);
}
void IntrinsicLocationsBuilderX86::VisitUnsafeGetObject(HInvoke* invoke) {
  VisitJdkUnsafeGetReference(invoke);
}
void IntrinsicLocationsBuilderX86::VisitUnsafeGetObjectVolatile(HInvoke* invoke) {
  VisitJdkUnsafeGetReferenceVolatile(invoke);
}
void IntrinsicLocationsBuilderX86::VisitUnsafeGetByte(HInvoke* invoke) {
  VisitJdkUnsafeGetByte(invoke);
}

void IntrinsicCodeGeneratorX86::VisitUnsafeGet(HInvoke* invoke) {
  VisitJdkUnsafeGet(invoke);
}
void IntrinsicCodeGeneratorX86::VisitUnsafeGetVolatile(HInvoke* invoke) {
  VisitJdkUnsafeGetVolatile(invoke);
}
void IntrinsicCodeGeneratorX86::VisitUnsafeGetLong(HInvoke* invoke) {
  VisitJdkUnsafeGetLong(invoke);
}
void IntrinsicCodeGeneratorX86::VisitUnsafeGetLongVolatile(HInvoke* invoke) {
  VisitJdkUnsafeGetLongVolatile(invoke);
}
void IntrinsicCodeGeneratorX86::VisitUnsafeGetObject(HInvoke* invoke) {
  VisitJdkUnsafeGetReference(invoke);
}
void IntrinsicCodeGeneratorX86::VisitUnsafeGetObjectVolatile(HInvoke* invoke) {
  VisitJdkUnsafeGetReferenceVolatile(invoke);
}
void IntrinsicCodeGeneratorX86::VisitUnsafeGetByte(HInvoke* invoke) {
  VisitJdkUnsafeGetByte(invoke);
}

void IntrinsicLocationsBuilderX86::VisitJdkUnsafeGet(HInvoke* invoke) {
  CreateIntIntIntToIntLocations(
      allocator_, invoke, codegen_, DataType::Type::kInt32, /*is_volatile=*/ false);
}
void IntrinsicLocationsBuilderX86::VisitJdkUnsafeGetVolatile(HInvoke* invoke) {
  CreateIntIntIntToIntLocations(
      allocator_, invoke, codegen_, DataType::Type::kInt32, /*is_volatile=*/ true);
}
void IntrinsicLocationsBuilderX86::VisitJdkUnsafeGetAcquire(HInvoke* invoke) {
  CreateIntIntIntToIntLocations(
      allocator_, invoke, codegen_, DataType::Type::kInt32, /*is_volatile=*/ true);
}
void IntrinsicLocationsBuilderX86::VisitJdkUnsafeGetLong(HInvoke* invoke) {
  CreateIntIntIntToIntLocations(
      allocator_, invoke, codegen_, DataType::Type::kInt64, /*is_volatile=*/ false);
}
void IntrinsicLocationsBuilderX86::VisitJdkUnsafeGetLongVolatile(HInvoke* invoke) {
  CreateIntIntIntToIntLocations(
      allocator_, invoke, codegen_, DataType::Type::kInt64, /*is_volatile=*/ true);
}
void IntrinsicLocationsBuilderX86::VisitJdkUnsafeGetLongAcquire(HInvoke* invoke) {
  CreateIntIntIntToIntLocations(
      allocator_, invoke, codegen_, DataType::Type::kInt64, /*is_volatile=*/ true);
}
void IntrinsicLocationsBuilderX86::VisitJdkUnsafeGetReference(HInvoke* invoke) {
  CreateIntIntIntToIntLocations(
      allocator_, invoke, codegen_, DataType::Type::kReference, /*is_volatile=*/ false);
}
void IntrinsicLocationsBuilderX86::VisitJdkUnsafeGetReferenceVolatile(HInvoke* invoke) {
  CreateIntIntIntToIntLocations(
      allocator_, invoke, codegen_, DataType::Type::kReference, /*is_volatile=*/ true);
}
void IntrinsicLocationsBuilderX86::VisitJdkUnsafeGetReferenceAcquire(HInvoke* invoke) {
  CreateIntIntIntToIntLocations(
      allocator_, invoke, codegen_, DataType::Type::kReference, /*is_volatile=*/ true);
}
void IntrinsicLocationsBuilderX86::VisitJdkUnsafeGetByte(HInvoke* invoke) {
  CreateIntIntIntToIntLocations(
      allocator_, invoke, codegen_, DataType::Type::kInt8, /*is_volatile=*/ false);
}

void IntrinsicCodeGeneratorX86::VisitJdkUnsafeGet(HInvoke* invoke) {
  GenUnsafeGet(invoke, DataType::Type::kInt32, /*is_volatile=*/ false, codegen_);
}
void IntrinsicCodeGeneratorX86::VisitJdkUnsafeGetVolatile(HInvoke* invoke) {
  GenUnsafeGet(invoke, DataType::Type::kInt32, /*is_volatile=*/ true, codegen_);
}
void IntrinsicCodeGeneratorX86::VisitJdkUnsafeGetAcquire(HInvoke* invoke) {
  GenUnsafeGet(invoke, DataType::Type::kInt32, /*is_volatile=*/ true, codegen_);
}
void IntrinsicCodeGeneratorX86::VisitJdkUnsafeGetLong(HInvoke* invoke) {
  GenUnsafeGet(invoke, DataType::Type::kInt64, /*is_volatile=*/ false, codegen_);
}
void IntrinsicCodeGeneratorX86::VisitJdkUnsafeGetLongVolatile(HInvoke* invoke) {
  GenUnsafeGet(invoke, DataType::Type::kInt64, /*is_volatile=*/ true, codegen_);
}
void IntrinsicCodeGeneratorX86::VisitJdkUnsafeGetLongAcquire(HInvoke* invoke) {
  GenUnsafeGet(invoke, DataType::Type::kInt64, /*is_volatile=*/ true, codegen_);
}
void IntrinsicCodeGeneratorX86::VisitJdkUnsafeGetReference(HInvoke* invoke) {
  GenUnsafeGet(invoke, DataType::Type::kReference, /*is_volatile=*/ false, codegen_);
}
void IntrinsicCodeGeneratorX86::VisitJdkUnsafeGetReferenceVolatile(HInvoke* invoke) {
  GenUnsafeGet(invoke, DataType::Type::kReference, /*is_volatile=*/ true, codegen_);
}
void IntrinsicCodeGeneratorX86::VisitJdkUnsafeGetReferenceAcquire(HInvoke* invoke) {
  GenUnsafeGet(invoke, DataType::Type::kReference, /*is_volatile=*/ true, codegen_);
}
void IntrinsicCodeGeneratorX86::VisitJdkUnsafeGetByte(HInvoke* invoke) {
  GenUnsafeGet(invoke, DataType::Type::kInt8, /*is_volatile=*/ false, codegen_);
}

static void CreateIntIntIntIntToVoidPlusTempsLocations(ArenaAllocator* allocator,
                                                       DataType::Type type,
                                                       HInvoke* invoke,
                                                       bool is_volatile) {
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::NoLocation());        // Unused receiver.
  locations->SetInAt(1, Location::RequiresRegister());
  locations->SetInAt(2, Location::RequiresRegister());
  locations->SetInAt(3, Location::RequiresRegister());
  if (type == DataType::Type::kReference) {
    // Need temp registers for card-marking.
    locations->AddTemp(Location::RequiresRegister());  // Possibly used for reference poisoning too.
    // Ensure the value is in a byte register.
    locations->AddTemp(Location::RegisterLocation(ECX));
  } else if (type == DataType::Type::kInt64 && is_volatile) {
    locations->AddTemp(Location::RequiresFpuRegister());
    locations->AddTemp(Location::RequiresFpuRegister());
  }
}

void IntrinsicLocationsBuilderX86::VisitUnsafePut(HInvoke* invoke) {
  VisitJdkUnsafePut(invoke);
}
void IntrinsicLocationsBuilderX86::VisitUnsafePutOrdered(HInvoke* invoke) {
  VisitJdkUnsafePutOrdered(invoke);
}
void IntrinsicLocationsBuilderX86::VisitUnsafePutVolatile(HInvoke* invoke) {
  VisitJdkUnsafePutVolatile(invoke);
}
void IntrinsicLocationsBuilderX86::VisitUnsafePutObject(HInvoke* invoke) {
  VisitJdkUnsafePutReference(invoke);
}
void IntrinsicLocationsBuilderX86::VisitUnsafePutObjectOrdered(HInvoke* invoke) {
  VisitJdkUnsafePutObjectOrdered(invoke);
}
void IntrinsicLocationsBuilderX86::VisitUnsafePutObjectVolatile(HInvoke* invoke) {
  VisitJdkUnsafePutReferenceVolatile(invoke);
}
void IntrinsicLocationsBuilderX86::VisitUnsafePutLong(HInvoke* invoke) {
  VisitJdkUnsafePutLong(invoke);
}
void IntrinsicLocationsBuilderX86::VisitUnsafePutLongOrdered(HInvoke* invoke) {
  VisitJdkUnsafePutLongOrdered(invoke);
}
void IntrinsicLocationsBuilderX86::VisitUnsafePutLongVolatile(HInvoke* invoke) {
  VisitJdkUnsafePutLongVolatile(invoke);
}
void IntrinsicLocationsBuilderX86::VisitUnsafePutByte(HInvoke* invoke) {
  VisitJdkUnsafePutByte(invoke);
}

void IntrinsicLocationsBuilderX86::VisitJdkUnsafePut(HInvoke* invoke) {
  CreateIntIntIntIntToVoidPlusTempsLocations(
      allocator_, DataType::Type::kInt32, invoke, /*is_volatile=*/ false);
}
void IntrinsicLocationsBuilderX86::VisitJdkUnsafePutOrdered(HInvoke* invoke) {
  CreateIntIntIntIntToVoidPlusTempsLocations(
      allocator_, DataType::Type::kInt32, invoke, /*is_volatile=*/ false);
}
void IntrinsicLocationsBuilderX86::VisitJdkUnsafePutVolatile(HInvoke* invoke) {
  CreateIntIntIntIntToVoidPlusTempsLocations(
      allocator_, DataType::Type::kInt32, invoke, /*is_volatile=*/ true);
}
void IntrinsicLocationsBuilderX86::VisitJdkUnsafePutRelease(HInvoke* invoke) {
  CreateIntIntIntIntToVoidPlusTempsLocations(
      allocator_, DataType::Type::kInt32, invoke, /*is_volatile=*/ true);
}
void IntrinsicLocationsBuilderX86::VisitJdkUnsafePutReference(HInvoke* invoke) {
  CreateIntIntIntIntToVoidPlusTempsLocations(
      allocator_, DataType::Type::kReference, invoke, /*is_volatile=*/ false);
}
void IntrinsicLocationsBuilderX86::VisitJdkUnsafePutObjectOrdered(HInvoke* invoke) {
  CreateIntIntIntIntToVoidPlusTempsLocations(
      allocator_, DataType::Type::kReference, invoke, /*is_volatile=*/ false);
}
void IntrinsicLocationsBuilderX86::VisitJdkUnsafePutReferenceVolatile(HInvoke* invoke) {
  CreateIntIntIntIntToVoidPlusTempsLocations(
      allocator_, DataType::Type::kReference, invoke, /*is_volatile=*/ true);
}
void IntrinsicLocationsBuilderX86::VisitJdkUnsafePutReferenceRelease(HInvoke* invoke) {
  CreateIntIntIntIntToVoidPlusTempsLocations(
      allocator_, DataType::Type::kReference, invoke, /*is_volatile=*/ true);
}
void IntrinsicLocationsBuilderX86::VisitJdkUnsafePutLong(HInvoke* invoke) {
  CreateIntIntIntIntToVoidPlusTempsLocations(
      allocator_, DataType::Type::kInt64, invoke, /*is_volatile=*/ false);
}
void IntrinsicLocationsBuilderX86::VisitJdkUnsafePutLongOrdered(HInvoke* invoke) {
  CreateIntIntIntIntToVoidPlusTempsLocations(
      allocator_, DataType::Type::kInt64, invoke, /*is_volatile=*/ false);
}
void IntrinsicLocationsBuilderX86::VisitJdkUnsafePutLongVolatile(HInvoke* invoke) {
  CreateIntIntIntIntToVoidPlusTempsLocations(
      allocator_, DataType::Type::kInt64, invoke, /*is_volatile=*/ true);
}
void IntrinsicLocationsBuilderX86::VisitJdkUnsafePutLongRelease(HInvoke* invoke) {
  CreateIntIntIntIntToVoidPlusTempsLocations(
      allocator_, DataType::Type::kInt64, invoke, /*is_volatile=*/ true);
}
void IntrinsicLocationsBuilderX86::VisitJdkUnsafePutByte(HInvoke* invoke) {
  CreateIntIntIntIntToVoidPlusTempsLocations(
      allocator_, DataType::Type::kInt8, invoke, /*is_volatile=*/ false);
}

// We don't care for ordered: it requires an AnyStore barrier, which is already given by the x86
// memory model.
static void GenUnsafePut(LocationSummary* locations,
                         DataType::Type type,
                         bool is_volatile,
                         CodeGeneratorX86* codegen) {
  X86Assembler* assembler = down_cast<X86Assembler*>(codegen->GetAssembler());
  Register base = locations->InAt(1).AsRegister<Register>();
  Register offset = locations->InAt(2).AsRegisterPairLow<Register>();
  Location value_loc = locations->InAt(3);

  if (type == DataType::Type::kInt64) {
    Register value_lo = value_loc.AsRegisterPairLow<Register>();
    Register value_hi = value_loc.AsRegisterPairHigh<Register>();
    if (is_volatile) {
      XmmRegister temp1 = locations->GetTemp(0).AsFpuRegister<XmmRegister>();
      XmmRegister temp2 = locations->GetTemp(1).AsFpuRegister<XmmRegister>();
      __ movd(temp1, value_lo);
      __ movd(temp2, value_hi);
      __ punpckldq(temp1, temp2);
      __ movsd(Address(base, offset, ScaleFactor::TIMES_1, 0), temp1);
    } else {
      __ movl(Address(base, offset, ScaleFactor::TIMES_1, 0), value_lo);
      __ movl(Address(base, offset, ScaleFactor::TIMES_1, 4), value_hi);
    }
  } else if (kPoisonHeapReferences && type == DataType::Type::kReference) {
    Register temp = locations->GetTemp(0).AsRegister<Register>();
    __ movl(temp, value_loc.AsRegister<Register>());
    __ PoisonHeapReference(temp);
    __ movl(Address(base, offset, ScaleFactor::TIMES_1, 0), temp);
  } else {
    __ movl(Address(base, offset, ScaleFactor::TIMES_1, 0), value_loc.AsRegister<Register>());
  }

  if (is_volatile) {
    codegen->MemoryFence();
  }

  if (type == DataType::Type::kReference) {
    bool value_can_be_null = true;  // TODO: Worth finding out this information?
    codegen->MaybeMarkGCCard(locations->GetTemp(0).AsRegister<Register>(),
                             locations->GetTemp(1).AsRegister<Register>(),
                             base,
                             value_loc.AsRegister<Register>(),
                             value_can_be_null);
  }
}

void IntrinsicCodeGeneratorX86::VisitUnsafePut(HInvoke* invoke) {
  VisitJdkUnsafePut(invoke);
}
void IntrinsicCodeGeneratorX86::VisitUnsafePutOrdered(HInvoke* invoke) {
  VisitJdkUnsafePutOrdered(invoke);
}
void IntrinsicCodeGeneratorX86::VisitUnsafePutVolatile(HInvoke* invoke) {
  VisitJdkUnsafePutVolatile(invoke);
}
void IntrinsicCodeGeneratorX86::VisitUnsafePutObject(HInvoke* invoke) {
  VisitJdkUnsafePutReference(invoke);
}
void IntrinsicCodeGeneratorX86::VisitUnsafePutObjectOrdered(HInvoke* invoke) {
  VisitJdkUnsafePutObjectOrdered(invoke);
}
void IntrinsicCodeGeneratorX86::VisitUnsafePutObjectVolatile(HInvoke* invoke) {
  VisitJdkUnsafePutReferenceVolatile(invoke);
}
void IntrinsicCodeGeneratorX86::VisitUnsafePutLong(HInvoke* invoke) {
  VisitJdkUnsafePutLong(invoke);
}
void IntrinsicCodeGeneratorX86::VisitUnsafePutLongOrdered(HInvoke* invoke) {
  VisitJdkUnsafePutLongOrdered(invoke);
}
void IntrinsicCodeGeneratorX86::VisitUnsafePutLongVolatile(HInvoke* invoke) {
  VisitJdkUnsafePutLongVolatile(invoke);
}
void IntrinsicCodeGeneratorX86::VisitUnsafePutByte(HInvoke* invoke) {
  VisitJdkUnsafePutByte(invoke);
}

void IntrinsicCodeGeneratorX86::VisitJdkUnsafePut(HInvoke* invoke) {
  GenUnsafePut(invoke->GetLocations(), DataType::Type::kInt32, /*is_volatile=*/ false, codegen_);
}
void IntrinsicCodeGeneratorX86::VisitJdkUnsafePutOrdered(HInvoke* invoke) {
  GenUnsafePut(invoke->GetLocations(), DataType::Type::kInt32, /*is_volatile=*/ false, codegen_);
}
void IntrinsicCodeGeneratorX86::VisitJdkUnsafePutVolatile(HInvoke* invoke) {
  GenUnsafePut(invoke->GetLocations(), DataType::Type::kInt32, /*is_volatile=*/ true, codegen_);
}
void IntrinsicCodeGeneratorX86::VisitJdkUnsafePutRelease(HInvoke* invoke) {
  GenUnsafePut(invoke->GetLocations(), DataType::Type::kInt32, /*is_volatile=*/ true, codegen_);
}
void IntrinsicCodeGeneratorX86::VisitJdkUnsafePutReference(HInvoke* invoke) {
  GenUnsafePut(
      invoke->GetLocations(), DataType::Type::kReference, /*is_volatile=*/ false, codegen_);
}
void IntrinsicCodeGeneratorX86::VisitJdkUnsafePutObjectOrdered(HInvoke* invoke) {
  GenUnsafePut(
      invoke->GetLocations(), DataType::Type::kReference, /*is_volatile=*/ false, codegen_);
}
void IntrinsicCodeGeneratorX86::VisitJdkUnsafePutReferenceVolatile(HInvoke* invoke) {
  GenUnsafePut(
      invoke->GetLocations(), DataType::Type::kReference, /*is_volatile=*/ true, codegen_);
}
void IntrinsicCodeGeneratorX86::VisitJdkUnsafePutReferenceRelease(HInvoke* invoke) {
  GenUnsafePut(
      invoke->GetLocations(), DataType::Type::kReference, /*is_volatile=*/ true, codegen_);
}
void IntrinsicCodeGeneratorX86::VisitJdkUnsafePutLong(HInvoke* invoke) {
  GenUnsafePut(invoke->GetLocations(), DataType::Type::kInt64, /*is_volatile=*/ false, codegen_);
}
void IntrinsicCodeGeneratorX86::VisitJdkUnsafePutLongOrdered(HInvoke* invoke) {
  GenUnsafePut(invoke->GetLocations(), DataType::Type::kInt64, /*is_volatile=*/ false, codegen_);
}
void IntrinsicCodeGeneratorX86::VisitJdkUnsafePutLongVolatile(HInvoke* invoke) {
  GenUnsafePut(invoke->GetLocations(), DataType::Type::kInt64, /*is_volatile=*/ true, codegen_);
}
void IntrinsicCodeGeneratorX86::VisitJdkUnsafePutLongRelease(HInvoke* invoke) {
  GenUnsafePut(invoke->GetLocations(), DataType::Type::kInt64, /*is_volatile=*/ true, codegen_);
}
void IntrinsicCodeGeneratorX86::VisitJdkUnsafePutByte(HInvoke* invoke) {
  GenUnsafePut(invoke->GetLocations(), DataType::Type::kInt8, /*is_volatile=*/ false, codegen_);
}

static void CreateIntIntIntIntIntToInt(ArenaAllocator* allocator,
                                       CodeGeneratorX86* codegen,
                                       DataType::Type type,
                                       HInvoke* invoke) {
  const bool can_call = codegen->EmitBakerReadBarrier() && IsUnsafeCASReference(invoke);
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke,
                                      can_call
                                          ? LocationSummary::kCallOnSlowPath
                                          : LocationSummary::kNoCall,
                                      kIntrinsified);
  locations->SetInAt(0, Location::NoLocation());        // Unused receiver.
  locations->SetInAt(1, Location::RequiresRegister());
  // Offset is a long, but in 32 bit mode, we only need the low word.
  // Can we update the invoke here to remove a TypeConvert to Long?
  locations->SetInAt(2, Location::RequiresRegister());
  // Expected value must be in EAX or EDX:EAX.
  // For long, new value must be in ECX:EBX.
  if (type == DataType::Type::kInt64) {
    locations->SetInAt(3, Location::RegisterPairLocation(EAX, EDX));
    locations->SetInAt(4, Location::RegisterPairLocation(EBX, ECX));
  } else {
    locations->SetInAt(3, Location::RegisterLocation(EAX));
    locations->SetInAt(4, Location::RequiresRegister());
  }

  // Force a byte register for the output.
  locations->SetOut(Location::RegisterLocation(EAX));
  if (type == DataType::Type::kReference) {
    // Need temporary registers for card-marking, and possibly for
    // (Baker) read barrier.
    locations->AddTemp(Location::RequiresRegister());  // Possibly used for reference poisoning too.
    // Need a byte register for marking.
    locations->AddTemp(Location::RegisterLocation(ECX));
  }
}

void IntrinsicLocationsBuilderX86::VisitUnsafeCASInt(HInvoke* invoke) {
  VisitJdkUnsafeCASInt(invoke);
}

void IntrinsicLocationsBuilderX86::VisitUnsafeCASLong(HInvoke* invoke) {
  VisitJdkUnsafeCASLong(invoke);
}

void IntrinsicLocationsBuilderX86::VisitUnsafeCASObject(HInvoke* invoke) {
  VisitJdkUnsafeCASObject(invoke);
}

void IntrinsicLocationsBuilderX86::VisitJdkUnsafeCASInt(HInvoke* invoke) {
  // `jdk.internal.misc.Unsafe.compareAndSwapInt` has compare-and-set semantics (see javadoc).
  VisitJdkUnsafeCompareAndSetInt(invoke);
}

void IntrinsicLocationsBuilderX86::VisitJdkUnsafeCASLong(HInvoke* invoke) {
  // `jdk.internal.misc.Unsafe.compareAndSwapLong` has compare-and-set semantics (see javadoc).
  VisitJdkUnsafeCompareAndSetLong(invoke);
}

void IntrinsicLocationsBuilderX86::VisitJdkUnsafeCASObject(HInvoke* invoke) {
  // `jdk.internal.misc.Unsafe.compareAndSwapObject` has compare-and-set semantics (see javadoc).
  VisitJdkUnsafeCompareAndSetReference(invoke);
}

void IntrinsicLocationsBuilderX86::VisitJdkUnsafeCompareAndSetInt(HInvoke* invoke) {
  CreateIntIntIntIntIntToInt(allocator_, codegen_, DataType::Type::kInt32, invoke);
}

void IntrinsicLocationsBuilderX86::VisitJdkUnsafeCompareAndSetLong(HInvoke* invoke) {
  CreateIntIntIntIntIntToInt(allocator_, codegen_, DataType::Type::kInt64, invoke);
}

void IntrinsicLocationsBuilderX86::VisitJdkUnsafeCompareAndSetReference(HInvoke* invoke) {
  // The only supported read barrier implementation is the Baker-style read barriers.
  if (codegen_->EmitNonBakerReadBarrier()) {
    return;
  }

  CreateIntIntIntIntIntToInt(allocator_, codegen_, DataType::Type::kReference, invoke);
}

static void GenPrimitiveLockedCmpxchg(DataType::Type type,
                                      CodeGeneratorX86* codegen,
                                      Location expected_value,
                                      Location new_value,
                                      Register base,
                                      Register offset,
                                      // Only necessary for floating point
                                      Register temp = Register::kNoRegister) {
  X86Assembler* assembler = down_cast<X86Assembler*>(codegen->GetAssembler());

  if (DataType::Kind(type) == DataType::Type::kInt32) {
    DCHECK_EQ(expected_value.AsRegister<Register>(), EAX);
  }

  // The address of the field within the holding object.
  Address field_addr(base, offset, TIMES_1, 0);

  switch (type) {
    case DataType::Type::kBool:
    case DataType::Type::kInt8:
      __ LockCmpxchgb(field_addr, new_value.AsRegister<ByteRegister>());
      break;
    case DataType::Type::kInt16:
    case DataType::Type::kUint16:
      __ LockCmpxchgw(field_addr, new_value.AsRegister<Register>());
      break;
    case DataType::Type::kInt32:
      __ LockCmpxchgl(field_addr, new_value.AsRegister<Register>());
      break;
    case DataType::Type::kFloat32: {
      // cmpxchg requires the expected value to be in EAX so the new value must be elsewhere.
      DCHECK_NE(temp, EAX);
      // EAX is both an input and an output for cmpxchg
      codegen->Move32(Location::RegisterLocation(EAX), expected_value);
      codegen->Move32(Location::RegisterLocation(temp), new_value);
      __ LockCmpxchgl(field_addr, temp);
      break;
    }
    case DataType::Type::kInt64:
      // Ensure the expected value is in EAX:EDX and that the new
      // value is in EBX:ECX (required by the CMPXCHG8B instruction).
      DCHECK_EQ(expected_value.AsRegisterPairLow<Register>(), EAX);
      DCHECK_EQ(expected_value.AsRegisterPairHigh<Register>(), EDX);
      DCHECK_EQ(new_value.AsRegisterPairLow<Register>(), EBX);
      DCHECK_EQ(new_value.AsRegisterPairHigh<Register>(), ECX);
      __ LockCmpxchg8b(field_addr);
      break;
    default:
      LOG(FATAL) << "Unexpected CAS type " << type;
  }
  // LOCK CMPXCHG/LOCK CMPXCHG8B have full barrier semantics, and we
  // don't need scheduling barriers at this time.
}

static void GenPrimitiveCAS(DataType::Type type,
                            CodeGeneratorX86* codegen,
                            Location expected_value,
                            Location new_value,
                            Register base,
                            Register offset,
                            Location out,
                            // Only necessary for floating point
                            Register temp = Register::kNoRegister,
                            bool is_cmpxchg = false) {
  X86Assembler* assembler = down_cast<X86Assembler*>(codegen->GetAssembler());

  if (!is_cmpxchg || DataType::Kind(type) == DataType::Type::kInt32) {
    DCHECK_EQ(out.AsRegister<Register>(), EAX);
  }

  GenPrimitiveLockedCmpxchg(type, codegen, expected_value, new_value, base, offset, temp);

  if (is_cmpxchg) {
    // Sign-extend, zero-extend or move the result if necessary
    switch (type) {
      case DataType::Type::kBool:
        __ movzxb(out.AsRegister<Register>(), out.AsRegister<ByteRegister>());
        break;
      case DataType::Type::kInt8:
        __ movsxb(out.AsRegister<Register>(), out.AsRegister<ByteRegister>());
        break;
      case DataType::Type::kInt16:
        __ movsxw(out.AsRegister<Register>(), out.AsRegister<Register>());
        break;
      case DataType::Type::kUint16:
        __ movzxw(out.AsRegister<Register>(), out.AsRegister<Register>());
        break;
      case DataType::Type::kFloat32:
        __ movd(out.AsFpuRegister<XmmRegister>(), EAX);
        break;
      default:
        // Nothing to do
        break;
    }
  } else {
    // Convert ZF into the Boolean result.
    __ setb(kZero, out.AsRegister<Register>());
    __ movzxb(out.AsRegister<Register>(), out.AsRegister<ByteRegister>());
  }
}

static void GenReferenceCAS(HInvoke* invoke,
                            CodeGeneratorX86* codegen,
                            Location expected_value,
                            Location new_value,
                            Register base,
                            Register offset,
                            Register temp,
                            Register temp2,
                            bool is_cmpxchg = false) {
  X86Assembler* assembler = down_cast<X86Assembler*>(codegen->GetAssembler());
  LocationSummary* locations = invoke->GetLocations();
  Location out = locations->Out();

  // The address of the field within the holding object.
  Address field_addr(base, offset, TIMES_1, 0);

  Register value = new_value.AsRegister<Register>();
  Register expected = expected_value.AsRegister<Register>();
  DCHECK_EQ(expected, EAX);
  DCHECK_NE(temp, temp2);

  if (codegen->EmitBakerReadBarrier()) {
    // Need to make sure the reference stored in the field is a to-space
    // one before attempting the CAS or the CAS could fail incorrectly.
    codegen->GenerateReferenceLoadWithBakerReadBarrier(
        invoke,
        // Unused, used only as a "temporary" within the read barrier.
        Location::RegisterLocation(temp),
        base,
        field_addr,
        /* needs_null_check= */ false,
        /* always_update_field= */ true,
        &temp2);
  }
  bool base_equals_value = (base == value);
  if (kPoisonHeapReferences) {
    if (base_equals_value) {
      // If `base` and `value` are the same register location, move
      // `value` to a temporary register.  This way, poisoning
      // `value` won't invalidate `base`.
      value = temp;
      __ movl(value, base);
    }

    // Check that the register allocator did not assign the location
    // of `expected` (EAX) to `value` nor to `base`, so that heap
    // poisoning (when enabled) works as intended below.
    // - If `value` were equal to `expected`, both references would
    //   be poisoned twice, meaning they would not be poisoned at
    //   all, as heap poisoning uses address negation.
    // - If `base` were equal to `expected`, poisoning `expected`
    //   would invalidate `base`.
    DCHECK_NE(value, expected);
    DCHECK_NE(base, expected);
    __ PoisonHeapReference(expected);
    __ PoisonHeapReference(value);
  }
  __ LockCmpxchgl(field_addr, value);

  // LOCK CMPXCHG has full barrier semantics, and we don't need
  // scheduling barriers at this time.

  if (is_cmpxchg) {
    DCHECK_EQ(out.AsRegister<Register>(), EAX);
    __ MaybeUnpoisonHeapReference(out.AsRegister<Register>());
  } else {
    // Convert ZF into the Boolean result.
    __ setb(kZero, out.AsRegister<Register>());
    __ movzxb(out.AsRegister<Register>(), out.AsRegister<ByteRegister>());
  }

  // Mark card for object if the new value is stored.
  bool value_can_be_null = true;  // TODO: Worth finding out this information?
  NearLabel skip_mark_gc_card;
  __ j(kNotZero, &skip_mark_gc_card);
  codegen->MaybeMarkGCCard(temp, temp2, base, value, value_can_be_null);
  __ Bind(&skip_mark_gc_card);

  // If heap poisoning is enabled, we need to unpoison the values
  // that were poisoned earlier.
  if (kPoisonHeapReferences) {
    if (base_equals_value) {
      // `value` has been moved to a temporary register, no need to
      // unpoison it.
    } else {
      // Ensure `value` is different from `out`, so that unpoisoning
      // the former does not invalidate the latter.
      DCHECK_NE(value, out.AsRegister<Register>());
      __ UnpoisonHeapReference(value);
    }
  }
  // Do not unpoison the reference contained in register
  // `expected`, as it is the same as register `out` (EAX).
}

static void GenCAS(DataType::Type type, HInvoke* invoke, CodeGeneratorX86* codegen) {
  LocationSummary* locations = invoke->GetLocations();

  Register base = locations->InAt(1).AsRegister<Register>();
  Register offset = locations->InAt(2).AsRegisterPairLow<Register>();
  Location expected_value = locations->InAt(3);
  Location new_value = locations->InAt(4);
  Location out = locations->Out();
  DCHECK_EQ(out.AsRegister<Register>(), EAX);

  if (type == DataType::Type::kReference) {
    // The only read barrier implementation supporting the
    // UnsafeCASObject intrinsic is the Baker-style read barriers.
    DCHECK_IMPLIES(codegen->EmitReadBarrier(), kUseBakerReadBarrier);

    Register temp = locations->GetTemp(0).AsRegister<Register>();
    Register temp2 = locations->GetTemp(1).AsRegister<Register>();
    GenReferenceCAS(invoke, codegen, expected_value, new_value, base, offset, temp, temp2);
  } else {
    DCHECK(!DataType::IsFloatingPointType(type));
    GenPrimitiveCAS(type, codegen, expected_value, new_value, base, offset, out);
  }
}

void IntrinsicCodeGeneratorX86::VisitUnsafeCASInt(HInvoke* invoke) {
  VisitJdkUnsafeCASInt(invoke);
}

void IntrinsicCodeGeneratorX86::VisitUnsafeCASLong(HInvoke* invoke) {
  VisitJdkUnsafeCASLong(invoke);
}

void IntrinsicCodeGeneratorX86::VisitUnsafeCASObject(HInvoke* invoke) {
  // The only read barrier implementation supporting the
  // UnsafeCASObject intrinsic is the Baker-style read barriers.
  DCHECK_IMPLIES(codegen_->EmitReadBarrier(), kUseBakerReadBarrier);

  GenCAS(DataType::Type::kReference, invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitJdkUnsafeCASInt(HInvoke* invoke) {
  // `jdk.internal.misc.Unsafe.compareAndSwapInt` has compare-and-set semantics (see javadoc).
  VisitJdkUnsafeCompareAndSetInt(invoke);
}

void IntrinsicCodeGeneratorX86::VisitJdkUnsafeCASLong(HInvoke* invoke) {
  // `jdk.internal.misc.Unsafe.compareAndSwapLong` has compare-and-set semantics (see javadoc).
  VisitJdkUnsafeCompareAndSetLong(invoke);
}

void IntrinsicCodeGeneratorX86::VisitJdkUnsafeCASObject(HInvoke* invoke) {
  // `jdk.internal.misc.Unsafe.compareAndSwapObject` has compare-and-set semantics (see javadoc).
  VisitJdkUnsafeCompareAndSetReference(invoke);
}

void IntrinsicCodeGeneratorX86::VisitJdkUnsafeCompareAndSetInt(HInvoke* invoke) {
  GenCAS(DataType::Type::kInt32, invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitJdkUnsafeCompareAndSetLong(HInvoke* invoke) {
  GenCAS(DataType::Type::kInt64, invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitJdkUnsafeCompareAndSetReference(HInvoke* invoke) {
  // The only supported read barrier implementation is the Baker-style read barriers.
  DCHECK_IMPLIES(codegen_->EmitReadBarrier(), kUseBakerReadBarrier);

  GenCAS(DataType::Type::kReference, invoke, codegen_);
}

// Note: Unlike other architectures that use corresponding enums for the `VarHandle`
// implementation, x86 is currently using it only for `Unsafe`.
enum class GetAndUpdateOp {
  kSet,
  kAdd,
};

void CreateUnsafeGetAndUpdateLocations(ArenaAllocator* allocator,
                                       HInvoke* invoke,
                                       CodeGeneratorX86* codegen,
                                       DataType::Type type,
                                       GetAndUpdateOp get_and_unsafe_op) {
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
  if (type == DataType::Type::kInt64) {
    // Explicitly allocate all registers.
    locations->SetInAt(1, Location::RegisterLocation(EBP));
    if (get_and_unsafe_op == GetAndUpdateOp::kAdd) {
      locations->AddTemp(Location::RegisterLocation(EBP));  // We shall clobber EBP.
      locations->SetInAt(2, Location::Any());  // Offset shall be on the stack.
      locations->SetInAt(3, Location::RegisterPairLocation(ESI, EDI));
      locations->AddTemp(Location::RegisterLocation(EBX));
      locations->AddTemp(Location::RegisterLocation(ECX));
    } else {
      locations->SetInAt(2, Location::RegisterPairLocation(ESI, EDI));
      locations->SetInAt(3, Location::RegisterPairLocation(EBX, ECX));
    }
    locations->SetOut(Location::RegisterPairLocation(EAX, EDX), Location::kOutputOverlap);
  } else {
    locations->SetInAt(1, Location::RequiresRegister());
    locations->SetInAt(2, Location::RequiresRegister());
    // Use the same register for both the output and the new value or addend
    // to take advantage of XCHG or XADD. Arbitrarily pick EAX.
    locations->SetInAt(3, Location::RegisterLocation(EAX));
    locations->SetOut(Location::RegisterLocation(EAX));
  }
}

void IntrinsicLocationsBuilderX86::VisitUnsafeGetAndAddInt(HInvoke* invoke) {
  VisitJdkUnsafeGetAndAddInt(invoke);
}

void IntrinsicLocationsBuilderX86::VisitUnsafeGetAndAddLong(HInvoke* invoke) {
  VisitJdkUnsafeGetAndAddLong(invoke);
}

void IntrinsicLocationsBuilderX86::VisitUnsafeGetAndSetInt(HInvoke* invoke) {
  VisitJdkUnsafeGetAndSetInt(invoke);
}

void IntrinsicLocationsBuilderX86::VisitUnsafeGetAndSetLong(HInvoke* invoke) {
  VisitJdkUnsafeGetAndSetLong(invoke);
}

void IntrinsicLocationsBuilderX86::VisitUnsafeGetAndSetObject(HInvoke* invoke) {
  VisitJdkUnsafeGetAndSetReference(invoke);
}

void IntrinsicLocationsBuilderX86::VisitJdkUnsafeGetAndAddInt(HInvoke* invoke) {
  CreateUnsafeGetAndUpdateLocations(
      allocator_, invoke, codegen_, DataType::Type::kInt32, GetAndUpdateOp::kAdd);
}

void IntrinsicLocationsBuilderX86::VisitJdkUnsafeGetAndAddLong(HInvoke* invoke) {
  CreateUnsafeGetAndUpdateLocations(
      allocator_, invoke, codegen_, DataType::Type::kInt64, GetAndUpdateOp::kAdd);
}

void IntrinsicLocationsBuilderX86::VisitJdkUnsafeGetAndSetInt(HInvoke* invoke) {
  CreateUnsafeGetAndUpdateLocations(
      allocator_, invoke, codegen_, DataType::Type::kInt32, GetAndUpdateOp::kSet);
}

void IntrinsicLocationsBuilderX86::VisitJdkUnsafeGetAndSetLong(HInvoke* invoke) {
  CreateUnsafeGetAndUpdateLocations(
      allocator_, invoke, codegen_, DataType::Type::kInt64, GetAndUpdateOp::kSet);
}

void IntrinsicLocationsBuilderX86::VisitJdkUnsafeGetAndSetReference(HInvoke* invoke) {
  // The only supported read barrier implementation is the Baker-style read barriers.
  if (codegen_->EmitNonBakerReadBarrier()) {
    return;
  }

  CreateUnsafeGetAndUpdateLocations(
      allocator_, invoke, codegen_, DataType::Type::kReference, GetAndUpdateOp::kSet);
  LocationSummary* locations = invoke->GetLocations();
  locations->AddTemp(Location::RequiresRegister());
  locations->AddTemp(Location::RegisterLocation(ECX));  // Byte register for `MarkGCCard()`.
}

static void GenUnsafeGetAndUpdate(HInvoke* invoke,
                                  DataType::Type type,
                                  CodeGeneratorX86* codegen,
                                  GetAndUpdateOp get_and_update_op) {
  X86Assembler* assembler = down_cast<X86Assembler*>(codegen->GetAssembler());
  LocationSummary* locations = invoke->GetLocations();

  Location out = locations->Out();                            // Result.
  Register base = locations->InAt(1).AsRegister<Register>();  // Object pointer.
  Location offset = locations->InAt(2);                       // Long offset.
  Location arg = locations->InAt(3);                          // New value or addend.

  if (type == DataType::Type::kInt32) {
    DCHECK(out.Equals(arg));
    Register out_reg = out.AsRegister<Register>();
    Address field_address(base, offset.AsRegisterPairLow<Register>(), TIMES_1, 0);
    if (get_and_update_op == GetAndUpdateOp::kAdd) {
      __ LockXaddl(field_address, out_reg);
    } else {
      DCHECK(get_and_update_op == GetAndUpdateOp::kSet);
      __ xchgl(out_reg, field_address);
    }
  } else if (type == DataType::Type::kInt64) {
    // Prepare the field address. Ignore the high 32 bits of the `offset`.
    Address field_address_low(kNoRegister, 0), field_address_high(kNoRegister, 0);
    if (get_and_update_op == GetAndUpdateOp::kAdd) {
      DCHECK(offset.IsDoubleStackSlot());
      __ addl(base, Address(ESP, offset.GetStackIndex()));  // Clobbers `base`.
      DCHECK(Location::RegisterLocation(base).Equals(locations->GetTemp(0)));
      field_address_low = Address(base, 0);
      field_address_high = Address(base, 4);
    } else {
      field_address_low = Address(base, offset.AsRegisterPairLow<Register>(), TIMES_1, 0);
      field_address_high = Address(base, offset.AsRegisterPairLow<Register>(), TIMES_1, 4);
    }
    // Load the old value to EDX:EAX and use LOCK CMPXCHG8B to set the new value.
    NearLabel loop;
    __ Bind(&loop);
    __ movl(EAX, field_address_low);
    __ movl(EDX, field_address_high);
    if (get_and_update_op == GetAndUpdateOp::kAdd) {
      DCHECK(Location::RegisterPairLocation(ESI, EDI).Equals(arg));
      __ movl(EBX, EAX);
      __ movl(ECX, EDX);
      __ addl(EBX, ESI);
      __ adcl(ECX, EDI);
    } else {
      DCHECK(get_and_update_op == GetAndUpdateOp::kSet);
      DCHECK(Location::RegisterPairLocation(EBX, ECX).Equals(arg));
    }
    __ LockCmpxchg8b(field_address_low);
    __ j(kNotEqual, &loop);  // Repeat on failure.
  } else {
    DCHECK_EQ(type, DataType::Type::kReference);
    DCHECK(get_and_update_op == GetAndUpdateOp::kSet);
    Register out_reg = out.AsRegister<Register>();
    Address field_address(base, offset.AsRegisterPairLow<Register>(), TIMES_1, 0);
    Register temp1 = locations->GetTemp(0).AsRegister<Register>();
    Register temp2 = locations->GetTemp(1).AsRegister<Register>();

    if (codegen->EmitReadBarrier()) {
      DCHECK(kUseBakerReadBarrier);
      // Ensure that the field contains a to-space reference.
      codegen->GenerateReferenceLoadWithBakerReadBarrier(
          invoke,
          Location::RegisterLocation(temp2),
          base,
          field_address,
          /*needs_null_check=*/ false,
          /*always_update_field=*/ true,
          &temp1);
    }

    // Mark card for object as a new value shall be stored.
    bool new_value_can_be_null = true;  // TODO: Worth finding out this information?
    DCHECK_EQ(temp2, ECX);  // Byte register for `MarkGCCard()`.
    codegen->MaybeMarkGCCard(temp1, temp2, base, /*value=*/out_reg, new_value_can_be_null);

    if (kPoisonHeapReferences) {
      // Use a temp to avoid poisoning base of the field address, which might happen if `out`
      // is the same as `base` (for code like `unsafe.getAndSet(obj, offset, obj)`).
      __ movl(temp1, out_reg);
      __ PoisonHeapReference(temp1);
      __ xchgl(temp1, field_address);
      __ UnpoisonHeapReference(temp1);
      __ movl(out_reg, temp1);
    } else {
      __ xchgl(out_reg, field_address);
    }
  }
}

void IntrinsicCodeGeneratorX86::VisitUnsafeGetAndAddInt(HInvoke* invoke) {
  VisitJdkUnsafeGetAndAddInt(invoke);
}

void IntrinsicCodeGeneratorX86::VisitUnsafeGetAndAddLong(HInvoke* invoke) {
  VisitJdkUnsafeGetAndAddLong(invoke);
}

void IntrinsicCodeGeneratorX86::VisitUnsafeGetAndSetInt(HInvoke* invoke) {
  VisitJdkUnsafeGetAndSetInt(invoke);
}

void IntrinsicCodeGeneratorX86::VisitUnsafeGetAndSetLong(HInvoke* invoke) {
  VisitJdkUnsafeGetAndSetLong(invoke);
}

void IntrinsicCodeGeneratorX86::VisitUnsafeGetAndSetObject(HInvoke* invoke) {
  VisitJdkUnsafeGetAndSetReference(invoke);
}

void IntrinsicCodeGeneratorX86::VisitJdkUnsafeGetAndAddInt(HInvoke* invoke) {
  GenUnsafeGetAndUpdate(invoke, DataType::Type::kInt32, codegen_, GetAndUpdateOp::kAdd);
}

void IntrinsicCodeGeneratorX86::VisitJdkUnsafeGetAndAddLong(HInvoke* invoke) {
  GenUnsafeGetAndUpdate(invoke, DataType::Type::kInt64, codegen_, GetAndUpdateOp::kAdd);
}

void IntrinsicCodeGeneratorX86::VisitJdkUnsafeGetAndSetInt(HInvoke* invoke) {
  GenUnsafeGetAndUpdate(invoke, DataType::Type::kInt32, codegen_, GetAndUpdateOp::kSet);
}

void IntrinsicCodeGeneratorX86::VisitJdkUnsafeGetAndSetLong(HInvoke* invoke) {
  GenUnsafeGetAndUpdate(invoke, DataType::Type::kInt64, codegen_, GetAndUpdateOp::kSet);
}

void IntrinsicCodeGeneratorX86::VisitJdkUnsafeGetAndSetReference(HInvoke* invoke) {
  GenUnsafeGetAndUpdate(invoke, DataType::Type::kReference, codegen_, GetAndUpdateOp::kSet);
}

void IntrinsicLocationsBuilderX86::VisitIntegerReverse(HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator_) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::RequiresRegister());
  locations->SetOut(Location::SameAsFirstInput());
  locations->AddTemp(Location::RequiresRegister());
}

static void SwapBits(Register reg, Register temp, int32_t shift, int32_t mask,
                     X86Assembler* assembler) {
  Immediate imm_shift(shift);
  Immediate imm_mask(mask);
  __ movl(temp, reg);
  __ shrl(reg, imm_shift);
  __ andl(temp, imm_mask);
  __ andl(reg, imm_mask);
  __ shll(temp, imm_shift);
  __ orl(reg, temp);
}

void IntrinsicCodeGeneratorX86::VisitIntegerReverse(HInvoke* invoke) {
  X86Assembler* assembler = GetAssembler();
  LocationSummary* locations = invoke->GetLocations();

  Register reg = locations->InAt(0).AsRegister<Register>();
  Register temp = locations->GetTemp(0).AsRegister<Register>();

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

void IntrinsicLocationsBuilderX86::VisitLongReverse(HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator_) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::RequiresRegister());
  locations->SetOut(Location::SameAsFirstInput());
  locations->AddTemp(Location::RequiresRegister());
}

void IntrinsicCodeGeneratorX86::VisitLongReverse(HInvoke* invoke) {
  X86Assembler* assembler = GetAssembler();
  LocationSummary* locations = invoke->GetLocations();

  Register reg_low = locations->InAt(0).AsRegisterPairLow<Register>();
  Register reg_high = locations->InAt(0).AsRegisterPairHigh<Register>();
  Register temp = locations->GetTemp(0).AsRegister<Register>();

  // We want to swap high/low, then bswap each one, and then do the same
  // as a 32 bit reverse.
  // Exchange high and low.
  __ movl(temp, reg_low);
  __ movl(reg_low, reg_high);
  __ movl(reg_high, temp);

  // bit-reverse low
  __ bswapl(reg_low);
  SwapBits(reg_low, temp, 1, 0x55555555, assembler);
  SwapBits(reg_low, temp, 2, 0x33333333, assembler);
  SwapBits(reg_low, temp, 4, 0x0f0f0f0f, assembler);

  // bit-reverse high
  __ bswapl(reg_high);
  SwapBits(reg_high, temp, 1, 0x55555555, assembler);
  SwapBits(reg_high, temp, 2, 0x33333333, assembler);
  SwapBits(reg_high, temp, 4, 0x0f0f0f0f, assembler);
}

static void CreateBitCountLocations(
    ArenaAllocator* allocator, CodeGeneratorX86* codegen, HInvoke* invoke, bool is_long) {
  if (!codegen->GetInstructionSetFeatures().HasPopCnt()) {
    // Do nothing if there is no popcnt support. This results in generating
    // a call for the intrinsic rather than direct code.
    return;
  }
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  if (is_long) {
    locations->AddTemp(Location::RequiresRegister());
  }
  locations->SetInAt(0, Location::Any());
  locations->SetOut(Location::RequiresRegister());
}

static void GenBitCount(X86Assembler* assembler,
                        CodeGeneratorX86* codegen,
                        HInvoke* invoke, bool is_long) {
  LocationSummary* locations = invoke->GetLocations();
  Location src = locations->InAt(0);
  Register out = locations->Out().AsRegister<Register>();

  if (invoke->InputAt(0)->IsConstant()) {
    // Evaluate this at compile time.
    int64_t value = Int64FromConstant(invoke->InputAt(0)->AsConstant());
    int32_t result = is_long
        ? POPCOUNT(static_cast<uint64_t>(value))
        : POPCOUNT(static_cast<uint32_t>(value));
    codegen->Load32BitValue(out, result);
    return;
  }

  // Handle the non-constant cases.
  if (!is_long) {
    if (src.IsRegister()) {
      __ popcntl(out, src.AsRegister<Register>());
    } else {
      DCHECK(src.IsStackSlot());
      __ popcntl(out, Address(ESP, src.GetStackIndex()));
    }
  } else {
    // The 64-bit case needs to worry about two parts.
    Register temp = locations->GetTemp(0).AsRegister<Register>();
    if (src.IsRegisterPair()) {
      __ popcntl(temp, src.AsRegisterPairLow<Register>());
      __ popcntl(out, src.AsRegisterPairHigh<Register>());
    } else {
      DCHECK(src.IsDoubleStackSlot());
      __ popcntl(temp, Address(ESP, src.GetStackIndex()));
      __ popcntl(out, Address(ESP, src.GetHighStackIndex(kX86WordSize)));
    }
    __ addl(out, temp);
  }
}

void IntrinsicLocationsBuilderX86::VisitIntegerBitCount(HInvoke* invoke) {
  CreateBitCountLocations(allocator_, codegen_, invoke, /* is_long= */ false);
}

void IntrinsicCodeGeneratorX86::VisitIntegerBitCount(HInvoke* invoke) {
  GenBitCount(GetAssembler(), codegen_, invoke, /* is_long= */ false);
}

void IntrinsicLocationsBuilderX86::VisitLongBitCount(HInvoke* invoke) {
  CreateBitCountLocations(allocator_, codegen_, invoke, /* is_long= */ true);
}

void IntrinsicCodeGeneratorX86::VisitLongBitCount(HInvoke* invoke) {
  GenBitCount(GetAssembler(), codegen_, invoke, /* is_long= */ true);
}

static void CreateLeadingZeroLocations(ArenaAllocator* allocator, HInvoke* invoke, bool is_long) {
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  if (is_long) {
    locations->SetInAt(0, Location::RequiresRegister());
  } else {
    locations->SetInAt(0, Location::Any());
  }
  locations->SetOut(Location::RequiresRegister());
}

static void GenLeadingZeros(X86Assembler* assembler,
                            CodeGeneratorX86* codegen,
                            HInvoke* invoke, bool is_long) {
  LocationSummary* locations = invoke->GetLocations();
  Location src = locations->InAt(0);
  Register out = locations->Out().AsRegister<Register>();

  if (invoke->InputAt(0)->IsConstant()) {
    // Evaluate this at compile time.
    int64_t value = Int64FromConstant(invoke->InputAt(0)->AsConstant());
    if (value == 0) {
      value = is_long ? 64 : 32;
    } else {
      value = is_long ? CLZ(static_cast<uint64_t>(value)) : CLZ(static_cast<uint32_t>(value));
    }
    codegen->Load32BitValue(out, value);
    return;
  }

  // Handle the non-constant cases.
  if (!is_long) {
    if (src.IsRegister()) {
      __ bsrl(out, src.AsRegister<Register>());
    } else {
      DCHECK(src.IsStackSlot());
      __ bsrl(out, Address(ESP, src.GetStackIndex()));
    }

    // BSR sets ZF if the input was zero, and the output is undefined.
    NearLabel all_zeroes, done;
    __ j(kEqual, &all_zeroes);

    // Correct the result from BSR to get the final CLZ result.
    __ xorl(out, Immediate(31));
    __ jmp(&done);

    // Fix the zero case with the expected result.
    __ Bind(&all_zeroes);
    __ movl(out, Immediate(32));

    __ Bind(&done);
    return;
  }

  // 64 bit case needs to worry about both parts of the register.
  DCHECK(src.IsRegisterPair());
  Register src_lo = src.AsRegisterPairLow<Register>();
  Register src_hi = src.AsRegisterPairHigh<Register>();
  NearLabel handle_low, done, all_zeroes;

  // Is the high word zero?
  __ testl(src_hi, src_hi);
  __ j(kEqual, &handle_low);

  // High word is not zero. We know that the BSR result is defined in this case.
  __ bsrl(out, src_hi);

  // Correct the result from BSR to get the final CLZ result.
  __ xorl(out, Immediate(31));
  __ jmp(&done);

  // High word was zero.  We have to compute the low word count and add 32.
  __ Bind(&handle_low);
  __ bsrl(out, src_lo);
  __ j(kEqual, &all_zeroes);

  // We had a valid result.  Use an XOR to both correct the result and add 32.
  __ xorl(out, Immediate(63));
  __ jmp(&done);

  // All zero case.
  __ Bind(&all_zeroes);
  __ movl(out, Immediate(64));

  __ Bind(&done);
}

void IntrinsicLocationsBuilderX86::VisitIntegerNumberOfLeadingZeros(HInvoke* invoke) {
  CreateLeadingZeroLocations(allocator_, invoke, /* is_long= */ false);
}

void IntrinsicCodeGeneratorX86::VisitIntegerNumberOfLeadingZeros(HInvoke* invoke) {
  GenLeadingZeros(GetAssembler(), codegen_, invoke, /* is_long= */ false);
}

void IntrinsicLocationsBuilderX86::VisitLongNumberOfLeadingZeros(HInvoke* invoke) {
  CreateLeadingZeroLocations(allocator_, invoke, /* is_long= */ true);
}

void IntrinsicCodeGeneratorX86::VisitLongNumberOfLeadingZeros(HInvoke* invoke) {
  GenLeadingZeros(GetAssembler(), codegen_, invoke, /* is_long= */ true);
}

static void CreateTrailingZeroLocations(ArenaAllocator* allocator, HInvoke* invoke, bool is_long) {
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  if (is_long) {
    locations->SetInAt(0, Location::RequiresRegister());
  } else {
    locations->SetInAt(0, Location::Any());
  }
  locations->SetOut(Location::RequiresRegister());
}

static void GenTrailingZeros(X86Assembler* assembler,
                             CodeGeneratorX86* codegen,
                             HInvoke* invoke, bool is_long) {
  LocationSummary* locations = invoke->GetLocations();
  Location src = locations->InAt(0);
  Register out = locations->Out().AsRegister<Register>();

  if (invoke->InputAt(0)->IsConstant()) {
    // Evaluate this at compile time.
    int64_t value = Int64FromConstant(invoke->InputAt(0)->AsConstant());
    if (value == 0) {
      value = is_long ? 64 : 32;
    } else {
      value = is_long ? CTZ(static_cast<uint64_t>(value)) : CTZ(static_cast<uint32_t>(value));
    }
    codegen->Load32BitValue(out, value);
    return;
  }

  // Handle the non-constant cases.
  if (!is_long) {
    if (src.IsRegister()) {
      __ bsfl(out, src.AsRegister<Register>());
    } else {
      DCHECK(src.IsStackSlot());
      __ bsfl(out, Address(ESP, src.GetStackIndex()));
    }

    // BSF sets ZF if the input was zero, and the output is undefined.
    NearLabel done;
    __ j(kNotEqual, &done);

    // Fix the zero case with the expected result.
    __ movl(out, Immediate(32));

    __ Bind(&done);
    return;
  }

  // 64 bit case needs to worry about both parts of the register.
  DCHECK(src.IsRegisterPair());
  Register src_lo = src.AsRegisterPairLow<Register>();
  Register src_hi = src.AsRegisterPairHigh<Register>();
  NearLabel done, all_zeroes;

  // If the low word is zero, then ZF will be set.  If not, we have the answer.
  __ bsfl(out, src_lo);
  __ j(kNotEqual, &done);

  // Low word was zero.  We have to compute the high word count and add 32.
  __ bsfl(out, src_hi);
  __ j(kEqual, &all_zeroes);

  // We had a valid result.  Add 32 to account for the low word being zero.
  __ addl(out, Immediate(32));
  __ jmp(&done);

  // All zero case.
  __ Bind(&all_zeroes);
  __ movl(out, Immediate(64));

  __ Bind(&done);
}

void IntrinsicLocationsBuilderX86::VisitIntegerNumberOfTrailingZeros(HInvoke* invoke) {
  CreateTrailingZeroLocations(allocator_, invoke, /* is_long= */ false);
}

void IntrinsicCodeGeneratorX86::VisitIntegerNumberOfTrailingZeros(HInvoke* invoke) {
  GenTrailingZeros(GetAssembler(), codegen_, invoke, /* is_long= */ false);
}

void IntrinsicLocationsBuilderX86::VisitLongNumberOfTrailingZeros(HInvoke* invoke) {
  CreateTrailingZeroLocations(allocator_, invoke, /* is_long= */ true);
}

void IntrinsicCodeGeneratorX86::VisitLongNumberOfTrailingZeros(HInvoke* invoke) {
  GenTrailingZeros(GetAssembler(), codegen_, invoke, /* is_long= */ true);
}

static bool IsSameInput(HInstruction* instruction, size_t input0, size_t input1) {
  return instruction->InputAt(input0) == instruction->InputAt(input1);
}

void IntrinsicLocationsBuilderX86::VisitSystemArrayCopy(HInvoke* invoke) {
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
    locations->AddTemp(Location::RegisterLocation(ESI));
    locations->AddTemp(Location::RegisterLocation(EDI));
    locations->AddTemp(Location::RegisterLocation(ECX));  // Byte reg also used for write barrier.

    static constexpr size_t kSrc = 0;
    static constexpr size_t kSrcPos = 1;
    static constexpr size_t kDest = 2;
    static constexpr size_t kDestPos = 3;
    static constexpr size_t kLength = 4;

    if (!locations->InAt(kLength).IsConstant()) {
      // We may not have enough registers for all inputs and temps, so put the
      // non-const length explicitly to the same register as one of the temps.
      locations->SetInAt(kLength, Location::RegisterLocation(ECX));
    }

    if (codegen_->EmitBakerReadBarrier()) {
      // We need an additional temp in the slow path for holding the reference.
      if (locations->InAt(kSrcPos).IsConstant() ||
          locations->InAt(kDestPos).IsConstant() ||
          IsSameInput(invoke, kSrc, kDest) ||
          IsSameInput(invoke, kSrcPos, kDestPos)) {
        // We can allocate another temp register.
        locations->AddTemp(Location::RequiresRegister());
      } else {
        // Use the same fixed register for the non-const `src_pos` and the additional temp.
        // The `src_pos` is no longer needed when we reach the slow path.
        locations->SetInAt(kSrcPos, Location::RegisterLocation(EDX));
        locations->AddTemp(Location::RegisterLocation(EDX));
      }
    }
  }
}

void IntrinsicCodeGeneratorX86::VisitSystemArrayCopy(HInvoke* invoke) {
  // The only read barrier implementation supporting the
  // SystemArrayCopy intrinsic is the Baker-style read barriers.
  DCHECK_IMPLIES(codegen_->EmitReadBarrier(), kUseBakerReadBarrier);

  X86Assembler* assembler = GetAssembler();
  LocationSummary* locations = invoke->GetLocations();

  uint32_t class_offset = mirror::Object::ClassOffset().Int32Value();
  uint32_t super_offset = mirror::Class::SuperClassOffset().Int32Value();
  uint32_t component_offset = mirror::Class::ComponentTypeOffset().Int32Value();
  uint32_t primitive_offset = mirror::Class::PrimitiveTypeOffset().Int32Value();
  uint32_t monitor_offset = mirror::Object::MonitorOffset().Int32Value();

  Register src = locations->InAt(0).AsRegister<Register>();
  Location src_pos = locations->InAt(1);
  Register dest = locations->InAt(2).AsRegister<Register>();
  Location dest_pos = locations->InAt(3);
  Location length = locations->InAt(4);
  Location temp1_loc = locations->GetTemp(0);
  Register temp1 = temp1_loc.AsRegister<Register>();
  Location temp2_loc = locations->GetTemp(1);
  Register temp2 = temp2_loc.AsRegister<Register>();

  SlowPathCode* intrinsic_slow_path =
      new (codegen_->GetScopedAllocator()) IntrinsicSlowPathX86(invoke);
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
        __ cmpl(dest_pos.AsRegister<Register>(), Immediate(src_pos_constant));
        __ j(kGreater, intrinsic_slow_path->GetEntryLabel());
      }
    } else {
      if (!optimizations.GetDestinationIsSource()) {
        __ cmpl(src, dest);
        __ j(kNotEqual, &conditions_on_positions_validated);
      }
      Register src_pos_reg = src_pos.AsRegister<Register>();
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
    __ testl(length.AsRegister<Register>(), length.AsRegister<Register>());
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

  auto check_non_primitive_array_class = [&](Register klass, Register temp) {
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
        check_non_primitive_array_class(temp2, temp2);
      }
      __ Bind(&do_copy);
    } else {
      DCHECK(!optimizations.GetDestinationIsTypedObjectArray());
      // For class match, we can skip the array type check completely if at least one of source
      // and destination is known to be a non primitive array, otherwise one check is enough.
      __ j(kNotEqual, intrinsic_slow_path->GetEntryLabel());
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
    // /* HeapReference<Class> */ temp1 = src->klass_
    __ movl(temp1, Address(src, class_offset));
    __ MaybeUnpoisonHeapReference(temp1);
    check_non_primitive_array_class(temp1, temp1);
  }

  if (length.IsConstant() && length.GetConstant()->AsIntConstant()->GetValue() == 0) {
    // Null constant length: not need to emit the loop code at all.
  } else {
    const DataType::Type type = DataType::Type::kReference;
    const size_t data_size = DataType::Size(type);
    const uint32_t data_offset = mirror::Array::DataOffset(data_size).Uint32Value();

    // Don't enter copy loop if `length == 0`.
    NearLabel skip_copy_and_write_barrier;
    if (!length.IsConstant()) {
      __ testl(length.AsRegister<Register>(), length.AsRegister<Register>());
      __ j(kEqual, &skip_copy_and_write_barrier);
    }

    // Compute the base source address in `temp1`.
    GenArrayAddress(assembler, temp1, src, src_pos, type, data_offset);
    // Compute the base destination address in `temp2`.
    GenArrayAddress(assembler, temp2, dest, dest_pos, type, data_offset);

    SlowPathCode* read_barrier_slow_path = nullptr;
    if (codegen_->EmitBakerReadBarrier()) {
      // SystemArrayCopy implementation for Baker read barriers (see
      // also CodeGeneratorX86::GenerateReferenceLoadWithBakerReadBarrier):
      //
      //   if (src_ptr != end_ptr) {
      //     uint32_t rb_state = Lockword(src->monitor_).ReadBarrierState();
      //     lfence;  // Load fence or artificial data dependency to prevent load-load reordering
      //     bool is_gray = (rb_state == ReadBarrier::GrayState());
      //     if (is_gray) {
      //       // Slow-path copy.
      //       for (size_t i = 0; i != length; ++i) {
      //         dest_array[dest_pos + i] =
      //             MaybePoison(ReadBarrier::Mark(MaybeUnpoison(src_array[src_pos + i])));
      //       }
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
      // Note that this is a no-op, thanks to the x86 memory model.
      codegen_->GenerateMemoryBarrier(MemBarrierKind::kLoadAny);

      // Slow path used to copy array when `src` is gray.
      read_barrier_slow_path =
          new (codegen_->GetScopedAllocator()) ReadBarrierSystemArrayCopySlowPathX86(invoke);
      codegen_->AddSlowPath(read_barrier_slow_path);

      // We have done the "if" of the gray bit check above, now branch based on the flags.
      __ j(kNotZero, read_barrier_slow_path->GetEntryLabel());
    }

    Register temp3 = locations->GetTemp(2).AsRegister<Register>();
    if (length.IsConstant()) {
      __ movl(temp3, Immediate(length.GetConstant()->AsIntConstant()->GetValue()));
    } else {
      DCHECK_EQ(temp3, length.AsRegister<Register>());
    }

    // Iterate over the arrays and do a raw copy of the objects. We don't need to poison/unpoison.
    DCHECK_EQ(temp1, ESI);
    DCHECK_EQ(temp2, EDI);
    DCHECK_EQ(temp3, ECX);
    __ rep_movsl();

    if (read_barrier_slow_path != nullptr) {
      DCHECK(codegen_->EmitBakerReadBarrier());
      __ Bind(read_barrier_slow_path->GetExitLabel());
    }

    // We only need one card marking on the destination array.
    codegen_->MarkGCCard(temp1, temp3, dest);

    __ Bind(&skip_copy_and_write_barrier);
  }

  __ Bind(intrinsic_slow_path->GetExitLabel());
}

static void RequestBaseMethodAddressInRegister(HInvoke* invoke) {
  LocationSummary* locations = invoke->GetLocations();
  if (locations != nullptr) {
    HInvokeStaticOrDirect* invoke_static_or_direct = invoke->AsInvokeStaticOrDirect();
    // Note: The base method address is not present yet when this is called from the
    // PCRelativeHandlerVisitor via IsCallFreeIntrinsic() to determine whether to insert it.
    if (invoke_static_or_direct->HasSpecialInput()) {
      DCHECK(invoke_static_or_direct->InputAt(invoke_static_or_direct->GetSpecialInputIndex())
                 ->IsX86ComputeBaseMethodAddress());
      locations->SetInAt(invoke_static_or_direct->GetSpecialInputIndex(),
                         Location::RequiresRegister());
    }
  }
}

#define VISIT_INTRINSIC(name, low, high, type, start_index) \
  void IntrinsicLocationsBuilderX86::Visit ##name ##ValueOf(HInvoke* invoke) { \
    InvokeRuntimeCallingConvention calling_convention; \
    IntrinsicVisitor::ComputeValueOfLocations( \
        invoke, \
        codegen_, \
        low, \
        high - low + 1, \
        Location::RegisterLocation(EAX), \
        Location::RegisterLocation(calling_convention.GetRegisterAt(0))); \
    RequestBaseMethodAddressInRegister(invoke); \
  } \
  void IntrinsicCodeGeneratorX86::Visit ##name ##ValueOf(HInvoke* invoke) { \
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

void IntrinsicCodeGeneratorX86::HandleValueOf(HInvoke* invoke,
                                              const IntrinsicVisitor::ValueOfInfo& info,
                                              DataType::Type type) {
  DCHECK(invoke->IsInvokeStaticOrDirect());
  LocationSummary* locations = invoke->GetLocations();
  X86Assembler* assembler = GetAssembler();

  Register out = locations->Out().AsRegister<Register>();
  auto allocate_instance = [&]() {
    DCHECK_EQ(out, InvokeRuntimeCallingConvention().GetRegisterAt(0));
    codegen_->LoadIntrinsicDeclaringClass(out, invoke->AsInvokeStaticOrDirect());
    codegen_->InvokeRuntime(kQuickAllocObjectInitialized, invoke, invoke->GetDexPc());
    CheckEntrypointTypes<kQuickAllocObjectWithChecks, void*, mirror::Class*>();
  };
  if (invoke->InputAt(0)->IsIntConstant()) {
    int32_t value = invoke->InputAt(0)->AsIntConstant()->GetValue();
    if (static_cast<uint32_t>(value - info.low) < info.length) {
      // Just embed the object in the code.
      DCHECK_NE(info.value_boot_image_reference, ValueOfInfo::kInvalidReference);
      codegen_->LoadBootImageAddress(
          out, info.value_boot_image_reference, invoke->AsInvokeStaticOrDirect());
    } else {
      DCHECK(locations->CanCall());
      // Allocate and initialize a new j.l.Integer.
      // TODO: If we JIT, we could allocate the object now, and store it in the
      // JIT object table.
      allocate_instance();
      codegen_->MoveToMemory(type,
                             Location::ConstantLocation(invoke->InputAt(0)->AsIntConstant()),
                             out,
                             /* dst_index= */ Register::kNoRegister,
                             /* dst_scale= */ TIMES_1,
                             /* dst_disp= */ info.value_offset);
    }
  } else {
    DCHECK(locations->CanCall());
    Register in = locations->InAt(0).AsRegister<Register>();
    // Check bounds of our cache.
    __ leal(out, Address(in, -info.low));
    __ cmpl(out, Immediate(info.length));
    NearLabel allocate, done;
    __ j(kAboveEqual, &allocate);
    // If the value is within the bounds, load the object directly from the array.
    constexpr size_t kElementSize = sizeof(mirror::HeapReference<mirror::Object>);
    static_assert((1u << TIMES_4) == sizeof(mirror::HeapReference<mirror::Object>),
                  "Check heap reference size.");
    if (codegen_->GetCompilerOptions().IsBootImage()) {
      DCHECK_EQ(invoke->InputCount(), invoke->GetNumberOfArguments() + 1u);
      size_t method_address_index = invoke->AsInvokeStaticOrDirect()->GetSpecialInputIndex();
      HX86ComputeBaseMethodAddress* method_address =
          invoke->InputAt(method_address_index)->AsX86ComputeBaseMethodAddress();
      DCHECK(method_address != nullptr);
      Register method_address_reg =
          invoke->GetLocations()->InAt(method_address_index).AsRegister<Register>();
      __ movl(out,
              Address(method_address_reg, out, TIMES_4, CodeGeneratorX86::kPlaceholder32BitOffset));
      codegen_->RecordBootImageIntrinsicPatch(method_address, info.array_data_boot_image_reference);
    } else {
      // Note: We're about to clobber the index in `out`, so we need to use `in` and
      // adjust the offset accordingly.
      uint32_t mid_array_boot_image_offset =
              info.array_data_boot_image_reference - info.low * kElementSize;
      codegen_->LoadBootImageAddress(
          out, mid_array_boot_image_offset, invoke->AsInvokeStaticOrDirect());
      DCHECK_NE(out, in);
      __ movl(out, Address(out, in, TIMES_4, 0));
    }
    __ MaybeUnpoisonHeapReference(out);
    __ jmp(&done);
    __ Bind(&allocate);
    // Otherwise allocate and initialize a new object.
    allocate_instance();
    codegen_->MoveToMemory(type,
                           Location::RegisterLocation(in),
                           out,
                           /* dst_index= */ Register::kNoRegister,
                           /* dst_scale= */ TIMES_1,
                           /* dst_disp= */ info.value_offset);
    __ Bind(&done);
  }
}

void IntrinsicLocationsBuilderX86::VisitReferenceGetReferent(HInvoke* invoke) {
  IntrinsicVisitor::CreateReferenceGetReferentLocations(invoke, codegen_);
  RequestBaseMethodAddressInRegister(invoke);
}

void IntrinsicCodeGeneratorX86::VisitReferenceGetReferent(HInvoke* invoke) {
  X86Assembler* assembler = GetAssembler();
  LocationSummary* locations = invoke->GetLocations();

  Location obj = locations->InAt(0);
  Location out = locations->Out();

  SlowPathCode* slow_path = new (GetAllocator()) IntrinsicSlowPathX86(invoke);
  codegen_->AddSlowPath(slow_path);

  if (codegen_->EmitReadBarrier()) {
    // Check self->GetWeakRefAccessEnabled().
    ThreadOffset32 offset = Thread::WeakRefAccessEnabledOffset<kX86PointerSize>();
    __ fs()->cmpl(Address::Absolute(offset),
                  Immediate(enum_cast<int32_t>(WeakRefAccessState::kVisiblyEnabled)));
    __ j(kNotEqual, slow_path->GetEntryLabel());
  }

  // Load the java.lang.ref.Reference class, use the output register as a temporary.
  codegen_->LoadIntrinsicDeclaringClass(out.AsRegister<Register>(),
                                        invoke->AsInvokeStaticOrDirect());

  // Check static fields java.lang.ref.Reference.{disableIntrinsic,slowPathEnabled} together.
  MemberOffset disable_intrinsic_offset = IntrinsicVisitor::GetReferenceDisableIntrinsicOffset();
  DCHECK_ALIGNED(disable_intrinsic_offset.Uint32Value(), 2u);
  DCHECK_EQ(disable_intrinsic_offset.Uint32Value() + 1u,
            IntrinsicVisitor::GetReferenceSlowPathEnabledOffset().Uint32Value());
  __ cmpw(Address(out.AsRegister<Register>(), disable_intrinsic_offset.Uint32Value()),
          Immediate(0));
  __ j(kNotEqual, slow_path->GetEntryLabel());

  // Load the value from the field.
  uint32_t referent_offset = mirror::Reference::ReferentOffset().Uint32Value();
  if (codegen_->EmitBakerReadBarrier()) {
    codegen_->GenerateFieldLoadWithBakerReadBarrier(invoke,
                                                    out,
                                                    obj.AsRegister<Register>(),
                                                    referent_offset,
                                                    /*needs_null_check=*/ true);
    // Note that the fence is a no-op, thanks to the x86 memory model.
    codegen_->GenerateMemoryBarrier(MemBarrierKind::kLoadAny);  // `referent` is volatile.
  } else {
    __ movl(out.AsRegister<Register>(), Address(obj.AsRegister<Register>(), referent_offset));
    codegen_->MaybeRecordImplicitNullCheck(invoke);
    // Note that the fence is a no-op, thanks to the x86 memory model.
    codegen_->GenerateMemoryBarrier(MemBarrierKind::kLoadAny);  // `referent` is volatile.
    codegen_->MaybeGenerateReadBarrierSlow(invoke, out, out, obj, referent_offset);
  }
  __ Bind(slow_path->GetExitLabel());
}

void IntrinsicLocationsBuilderX86::VisitReferenceRefersTo(HInvoke* invoke) {
  IntrinsicVisitor::CreateReferenceRefersToLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitReferenceRefersTo(HInvoke* invoke) {
  X86Assembler* assembler = GetAssembler();
  LocationSummary* locations = invoke->GetLocations();

  Register obj = locations->InAt(0).AsRegister<Register>();
  Register other = locations->InAt(1).AsRegister<Register>();
  Register out = locations->Out().AsRegister<Register>();

  uint32_t referent_offset = mirror::Reference::ReferentOffset().Uint32Value();
  uint32_t monitor_offset = mirror::Object::MonitorOffset().Int32Value();

  __ movl(out, Address(obj, referent_offset));
  codegen_->MaybeRecordImplicitNullCheck(invoke);
  __ MaybeUnpoisonHeapReference(out);
  // Note that the fence is a no-op, thanks to the x86 memory model.
  codegen_->GenerateMemoryBarrier(MemBarrierKind::kLoadAny);  // `referent` is volatile.

  NearLabel end, return_true, return_false;
  __ cmpl(out, other);

  if (codegen_->EmitReadBarrier()) {
    DCHECK(kUseBakerReadBarrier);

    __ j(kEqual, &return_true);

    // Check if the loaded reference is null.
    __ testl(out, out);
    __ j(kZero, &return_false);

    // For correct memory visibility, we need a barrier before loading the lock word
    // but we already have the barrier emitted for volatile load above which is sufficient.

    // Load the lockword and check if it is a forwarding address.
    static_assert(LockWord::kStateShift == 30u);
    static_assert(LockWord::kStateForwardingAddress == 3u);
    __ movl(out, Address(out, monitor_offset));
    __ cmpl(out, Immediate(static_cast<int32_t>(0xc0000000)));
    __ j(kBelow, &return_false);

    // Extract the forwarding address and compare with `other`.
    __ shll(out, Immediate(LockWord::kForwardingAddressShift));
    __ cmpl(out, other);
  }

  __ j(kNotEqual, &return_false);

  // Return true and exit the function.
  __ Bind(&return_true);
  __ movl(out, Immediate(1));
  __ jmp(&end);

  // Return false and exit the function.
  __ Bind(&return_false);
  __ xorl(out, out);
  __ Bind(&end);
}

void IntrinsicLocationsBuilderX86::VisitThreadInterrupted(HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator_) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetOut(Location::RequiresRegister());
}

void IntrinsicCodeGeneratorX86::VisitThreadInterrupted(HInvoke* invoke) {
  X86Assembler* assembler = GetAssembler();
  Register out = invoke->GetLocations()->Out().AsRegister<Register>();
  Address address = Address::Absolute(Thread::InterruptedOffset<kX86PointerSize>().Int32Value());
  NearLabel done;
  __ fs()->movl(out, address);
  __ testl(out, out);
  __ j(kEqual, &done);
  __ fs()->movl(address, Immediate(0));
  codegen_->MemoryFence();
  __ Bind(&done);
}

void IntrinsicLocationsBuilderX86::VisitReachabilityFence(HInvoke* invoke) {
  LocationSummary* locations =
      new (allocator_) LocationSummary(invoke, LocationSummary::kNoCall, kIntrinsified);
  locations->SetInAt(0, Location::Any());
}

void IntrinsicCodeGeneratorX86::VisitReachabilityFence([[maybe_unused]] HInvoke* invoke) {}

void IntrinsicLocationsBuilderX86::VisitIntegerDivideUnsigned(HInvoke* invoke) {
  LocationSummary* locations = new (allocator_) LocationSummary(invoke,
                                                                LocationSummary::kCallOnSlowPath,
                                                                kIntrinsified);
  locations->SetInAt(0, Location::RegisterLocation(EAX));
  locations->SetInAt(1, Location::RequiresRegister());
  locations->SetOut(Location::SameAsFirstInput());
  // Intel uses edx:eax as the dividend.
  locations->AddTemp(Location::RegisterLocation(EDX));
}

void IntrinsicCodeGeneratorX86::VisitIntegerDivideUnsigned(HInvoke* invoke) {
  X86Assembler* assembler = GetAssembler();
  LocationSummary* locations = invoke->GetLocations();
  Location out = locations->Out();
  Location first = locations->InAt(0);
  Location second = locations->InAt(1);
  Register edx = locations->GetTemp(0).AsRegister<Register>();
  Register second_reg = second.AsRegister<Register>();

  DCHECK_EQ(EAX, first.AsRegister<Register>());
  DCHECK_EQ(EAX, out.AsRegister<Register>());
  DCHECK_EQ(EDX, edx);

  // Check if divisor is zero, bail to managed implementation to handle.
  __ testl(second_reg, second_reg);
  SlowPathCode* slow_path = new (codegen_->GetScopedAllocator()) IntrinsicSlowPathX86(invoke);
  codegen_->AddSlowPath(slow_path);
  __ j(kEqual, slow_path->GetEntryLabel());

  __ xorl(edx, edx);
  __ divl(second_reg);

  __ Bind(slow_path->GetExitLabel());
}

static bool HasVarHandleIntrinsicImplementation(HInvoke* invoke) {
  VarHandleOptimizations optimizations(invoke);
  if (optimizations.GetDoNotIntrinsify()) {
    return false;
  }

  size_t expected_coordinates_count = GetExpectedVarHandleCoordinatesCount(invoke);
  DCHECK_LE(expected_coordinates_count, 2u);  // Filtered by the `DoNotIntrinsify` flag above.
  if (expected_coordinates_count > 1u) {
    // Only static and instance fields VarHandle are supported now.
    // TODO: add support for arrays and views.
    return false;
  }

  return true;
}

static void GenerateVarHandleAccessModeCheck(Register varhandle_object,
                                             mirror::VarHandle::AccessMode access_mode,
                                             SlowPathCode* slow_path,
                                             X86Assembler* assembler) {
  const uint32_t access_modes_bitmask_offset =
      mirror::VarHandle::AccessModesBitMaskOffset().Uint32Value();
  const uint32_t access_mode_bit = 1u << static_cast<uint32_t>(access_mode);

  // If the access mode is not supported, bail to runtime implementation to handle
  __ testl(Address(varhandle_object, access_modes_bitmask_offset), Immediate(access_mode_bit));
  __ j(kZero, slow_path->GetEntryLabel());
}

static void GenerateVarHandleStaticFieldCheck(Register varhandle_object,
                                              SlowPathCode* slow_path,
                                              X86Assembler* assembler) {
  const uint32_t coordtype0_offset = mirror::VarHandle::CoordinateType0Offset().Uint32Value();

  // Check that the VarHandle references a static field by checking that coordinateType0 == null.
  // Do not emit read barrier (or unpoison the reference) for comparing to null.
  __ cmpl(Address(varhandle_object, coordtype0_offset), Immediate(0));
  __ j(kNotEqual, slow_path->GetEntryLabel());
}

static void GenerateSubTypeObjectCheck(Register object,
                                       Register temp,
                                       Address type_address,
                                       SlowPathCode* slow_path,
                                       X86Assembler* assembler,
                                       bool object_can_be_null = true) {
  const uint32_t class_offset = mirror::Object::ClassOffset().Uint32Value();
  const uint32_t super_class_offset = mirror::Class::SuperClassOffset().Uint32Value();
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

static void GenerateVarHandleInstanceFieldChecks(HInvoke* invoke,
                                                 Register temp,
                                                 SlowPathCode* slow_path,
                                                 X86Assembler* assembler) {
  VarHandleOptimizations optimizations(invoke);
  LocationSummary* locations = invoke->GetLocations();
  Register varhandle_object = locations->InAt(0).AsRegister<Register>();
  Register object = locations->InAt(1).AsRegister<Register>();

  const uint32_t coordtype0_offset = mirror::VarHandle::CoordinateType0Offset().Uint32Value();
  const uint32_t coordtype1_offset = mirror::VarHandle::CoordinateType1Offset().Uint32Value();

  // Check that the VarHandle references an instance field by checking that
  // coordinateType1 == null. coordinateType0 should be not null, but this is handled by the
  // type compatibility check with the source object's type, which will fail for null.
  __ cmpl(Address(varhandle_object, coordtype1_offset), Immediate(0));
  __ j(kNotEqual, slow_path->GetEntryLabel());

  // Check if the object is null
  if (!optimizations.GetSkipObjectNullCheck()) {
    __ testl(object, object);
    __ j(kZero, slow_path->GetEntryLabel());
  }

  // Check the object's class against coordinateType0.
  GenerateSubTypeObjectCheck(object,
                             temp,
                             Address(varhandle_object, coordtype0_offset),
                             slow_path,
                             assembler,
                             /* object_can_be_null= */ false);
}

static void GenerateVarTypePrimitiveTypeCheck(Register varhandle_object,
                                              Register temp,
                                              DataType::Type type,
                                              SlowPathCode* slow_path,
                                              X86Assembler* assembler) {
  const uint32_t var_type_offset = mirror::VarHandle::VarTypeOffset().Uint32Value();
  const uint32_t primitive_type_offset = mirror::Class::PrimitiveTypeOffset().Uint32Value();
  const uint32_t primitive_type = static_cast<uint32_t>(DataTypeToPrimitive(type));

  // We do not need a read barrier when loading a reference only for loading a constant field
  // through the reference.
  __ movl(temp, Address(varhandle_object, var_type_offset));
  __ MaybeUnpoisonHeapReference(temp);
  __ cmpw(Address(temp, primitive_type_offset), Immediate(primitive_type));
  __ j(kNotEqual, slow_path->GetEntryLabel());
}

static void GenerateVarHandleCommonChecks(HInvoke *invoke,
                                          Register temp,
                                          SlowPathCode* slow_path,
                                          X86Assembler* assembler) {
  LocationSummary* locations = invoke->GetLocations();
  Register vh_object = locations->InAt(0).AsRegister<Register>();
  mirror::VarHandle::AccessMode access_mode =
      mirror::VarHandle::GetAccessModeByIntrinsic(invoke->GetIntrinsic());

  GenerateVarHandleAccessModeCheck(vh_object,
                                   access_mode,
                                   slow_path,
                                   assembler);

  size_t expected_coordinates_count = GetExpectedVarHandleCoordinatesCount(invoke);
  switch (expected_coordinates_count) {
    case 0u:
      GenerateVarHandleStaticFieldCheck(vh_object, slow_path, assembler);
      break;
    case 1u: {
      GenerateVarHandleInstanceFieldChecks(invoke, temp, slow_path, assembler);
      break;
    }
    default:
      // Unimplemented
      UNREACHABLE();
  }

  // Check the return type and varType parameters.
  mirror::VarHandle::AccessModeTemplate access_mode_template =
      mirror::VarHandle::GetAccessModeTemplate(access_mode);
  DataType::Type type = invoke->GetType();

  switch (access_mode_template) {
    case mirror::VarHandle::AccessModeTemplate::kGet:
      // Check the varType.primitiveType against the type we're trying to retrieve. Reference types
      // are also checked later by a HCheckCast node as an additional check.
      GenerateVarTypePrimitiveTypeCheck(vh_object, temp, type, slow_path, assembler);
      break;
    case mirror::VarHandle::AccessModeTemplate::kSet:
    case mirror::VarHandle::AccessModeTemplate::kGetAndUpdate: {
      uint32_t value_index = invoke->GetNumberOfArguments() - 1;
      DataType::Type value_type = GetDataTypeFromShorty(invoke, value_index);

      // Check the varType.primitiveType against the type of the value we're trying to set.
      GenerateVarTypePrimitiveTypeCheck(vh_object, temp, value_type, slow_path, assembler);
      if (value_type == DataType::Type::kReference) {
        const uint32_t var_type_offset = mirror::VarHandle::VarTypeOffset().Uint32Value();

        // If the value type is a reference, check it against the varType.
        GenerateSubTypeObjectCheck(locations->InAt(value_index).AsRegister<Register>(),
                                   temp,
                                   Address(vh_object, var_type_offset),
                                   slow_path,
                                   assembler);
      }
      break;
    }
    case mirror::VarHandle::AccessModeTemplate::kCompareAndSet:
    case mirror::VarHandle::AccessModeTemplate::kCompareAndExchange: {
      uint32_t new_value_index = invoke->GetNumberOfArguments() - 1;
      uint32_t expected_value_index = invoke->GetNumberOfArguments() - 2;
      DataType::Type value_type = GetDataTypeFromShorty(invoke, new_value_index);
      DCHECK_EQ(value_type, GetDataTypeFromShorty(invoke, expected_value_index));

      // Check the varType.primitiveType against the type of the expected value.
      GenerateVarTypePrimitiveTypeCheck(vh_object, temp, value_type, slow_path, assembler);
      if (value_type == DataType::Type::kReference) {
        const uint32_t var_type_offset = mirror::VarHandle::VarTypeOffset().Uint32Value();

        // If the value type is a reference, check both the expected and the new value against
        // the varType.
        GenerateSubTypeObjectCheck(locations->InAt(new_value_index).AsRegister<Register>(),
                                   temp,
                                   Address(vh_object, var_type_offset),
                                   slow_path,
                                   assembler);
        GenerateSubTypeObjectCheck(locations->InAt(expected_value_index).AsRegister<Register>(),
                                   temp,
                                   Address(vh_object, var_type_offset),
                                   slow_path,
                                   assembler);
      }
      break;
    }
  }
}

// This method loads the field's address referred by a field VarHandle (base + offset).
// The return value is the register containing object's reference (in case of an instance field)
// or the declaring class (in case of a static field). The declaring class is stored in temp
// register. Field's offset is loaded to the `offset` register.
static Register GenerateVarHandleFieldReference(HInvoke* invoke,
                                                CodeGeneratorX86* codegen,
                                                Register temp,
                                                /*out*/ Register offset) {
  X86Assembler* assembler = codegen->GetAssembler();
  LocationSummary* locations = invoke->GetLocations();
  const uint32_t artfield_offset = mirror::FieldVarHandle::ArtFieldOffset().Uint32Value();
  const uint32_t offset_offset = ArtField::OffsetOffset().Uint32Value();
  const uint32_t declaring_class_offset = ArtField::DeclaringClassOffset().Uint32Value();
  Register varhandle_object = locations->InAt(0).AsRegister<Register>();

  // Load the ArtField* and the offset.
  __ movl(temp, Address(varhandle_object, artfield_offset));
  __ movl(offset, Address(temp, offset_offset));
  size_t expected_coordinates_count = GetExpectedVarHandleCoordinatesCount(invoke);
  if (expected_coordinates_count == 0) {
    // For static fields, load the declaring class
    InstructionCodeGeneratorX86* instr_codegen =
        down_cast<InstructionCodeGeneratorX86*>(codegen->GetInstructionVisitor());
    instr_codegen->GenerateGcRootFieldLoad(invoke,
                                           Location::RegisterLocation(temp),
                                           Address(temp, declaring_class_offset),
                                           /* fixup_label= */ nullptr,
                                           codegen->GetCompilerReadBarrierOption());
    return temp;
  }

  // For instance fields, return the register containing the object.
  DCHECK_EQ(expected_coordinates_count, 1u);

  return locations->InAt(1).AsRegister<Register>();
}

static void CreateVarHandleGetLocations(HInvoke* invoke, CodeGeneratorX86* codegen) {
  // The only read barrier implementation supporting the
  // VarHandleGet intrinsic is the Baker-style read barriers.
  if (codegen->EmitNonBakerReadBarrier()) {
    return;
  }

  if (!HasVarHandleIntrinsicImplementation(invoke)) {
    return;
  }

  ArenaAllocator* allocator = invoke->GetBlock()->GetGraph()->GetAllocator();
  LocationSummary* locations = new (allocator) LocationSummary(
      invoke, LocationSummary::kCallOnSlowPath, kIntrinsified);
  locations->SetInAt(0, Location::RequiresRegister());
  size_t expected_coordinates_count = GetExpectedVarHandleCoordinatesCount(invoke);
  if (expected_coordinates_count == 1u) {
    // For instance fields, this is the source object.
    locations->SetInAt(1, Location::RequiresRegister());
  }
  locations->AddTemp(Location::RequiresRegister());

  DataType::Type type = invoke->GetType();
  switch (DataType::Kind(type)) {
    case DataType::Type::kInt64:
      locations->AddTemp(Location::RequiresRegister());
      if (invoke->GetIntrinsic() != Intrinsics::kVarHandleGet) {
        // We need an XmmRegister for Int64 to ensure an atomic load
        locations->AddTemp(Location::RequiresFpuRegister());
      }
      FALLTHROUGH_INTENDED;
    case DataType::Type::kInt32:
    case DataType::Type::kReference:
      locations->SetOut(Location::RequiresRegister());
      break;
    default:
      DCHECK(DataType::IsFloatingPointType(type));
      locations->AddTemp(Location::RequiresRegister());
      locations->SetOut(Location::RequiresFpuRegister());
  }
}

static void GenerateVarHandleGet(HInvoke* invoke, CodeGeneratorX86* codegen) {
  // The only read barrier implementation supporting the
  // VarHandleGet intrinsic is the Baker-style read barriers.
  DCHECK_IMPLIES(codegen->EmitReadBarrier(), kUseBakerReadBarrier);

  X86Assembler* assembler = codegen->GetAssembler();
  LocationSummary* locations = invoke->GetLocations();
  DataType::Type type = invoke->GetType();
  DCHECK_NE(type, DataType::Type::kVoid);
  Register temp = locations->GetTemp(0).AsRegister<Register>();
  SlowPathCode* slow_path = new (codegen->GetScopedAllocator()) IntrinsicSlowPathX86(invoke);
  codegen->AddSlowPath(slow_path);

  GenerateVarHandleCommonChecks(invoke, temp, slow_path, assembler);

  Location out = locations->Out();
  // Use 'out' as a temporary register if it's a core register
  Register offset =
      out.IsRegister() ? out.AsRegister<Register>() : locations->GetTemp(1).AsRegister<Register>();

  // Get the field referred by the VarHandle. The returned register contains the object reference
  // or the declaring class. The field offset will be placed in 'offset'. For static fields, the
  // declaring class will be placed in 'temp' register.
  Register ref = GenerateVarHandleFieldReference(invoke, codegen, temp, offset);
  Address field_addr(ref, offset, TIMES_1, 0);

  // Load the value from the field
  if (type == DataType::Type::kReference && codegen->EmitReadBarrier()) {
    codegen->GenerateReferenceLoadWithBakerReadBarrier(
        invoke, out, ref, field_addr, /* needs_null_check= */ false);
  } else if (type == DataType::Type::kInt64 &&
             invoke->GetIntrinsic() != Intrinsics::kVarHandleGet) {
    XmmRegister xmm_temp = locations->GetTemp(2).AsFpuRegister<XmmRegister>();
    codegen->LoadFromMemoryNoBarrier(
        type, out, field_addr, /* instr= */ nullptr, xmm_temp, /* is_atomic_load= */ true);
  } else {
    codegen->LoadFromMemoryNoBarrier(type, out, field_addr);
  }

  if (invoke->GetIntrinsic() == Intrinsics::kVarHandleGetVolatile ||
      invoke->GetIntrinsic() == Intrinsics::kVarHandleGetAcquire) {
    // Load fence to prevent load-load reordering.
    // Note that this is a no-op, thanks to the x86 memory model.
    codegen->GenerateMemoryBarrier(MemBarrierKind::kLoadAny);
  }

  __ Bind(slow_path->GetExitLabel());
}

void IntrinsicLocationsBuilderX86::VisitVarHandleGet(HInvoke* invoke) {
  CreateVarHandleGetLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitVarHandleGet(HInvoke* invoke) {
  GenerateVarHandleGet(invoke, codegen_);
}

void IntrinsicLocationsBuilderX86::VisitVarHandleGetVolatile(HInvoke* invoke) {
  CreateVarHandleGetLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitVarHandleGetVolatile(HInvoke* invoke) {
  GenerateVarHandleGet(invoke, codegen_);
}

void IntrinsicLocationsBuilderX86::VisitVarHandleGetAcquire(HInvoke* invoke) {
  CreateVarHandleGetLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitVarHandleGetAcquire(HInvoke* invoke) {
  GenerateVarHandleGet(invoke, codegen_);
}

void IntrinsicLocationsBuilderX86::VisitVarHandleGetOpaque(HInvoke* invoke) {
  CreateVarHandleGetLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitVarHandleGetOpaque(HInvoke* invoke) {
  GenerateVarHandleGet(invoke, codegen_);
}

static void CreateVarHandleSetLocations(HInvoke* invoke, CodeGeneratorX86* codegen) {
  // The only read barrier implementation supporting the
  // VarHandleGet intrinsic is the Baker-style read barriers.
  if (codegen->EmitNonBakerReadBarrier()) {
    return;
  }

  if (!HasVarHandleIntrinsicImplementation(invoke)) {
    return;
  }

  // The last argument should be the value we intend to set.
  uint32_t value_index = invoke->GetNumberOfArguments() - 1;
  HInstruction* value = invoke->InputAt(value_index);
  DataType::Type value_type = GetDataTypeFromShorty(invoke, value_index);
  bool needs_atomicity = invoke->GetIntrinsic() != Intrinsics::kVarHandleSet;
  if (value_type == DataType::Type::kInt64 && (!value->IsConstant() || needs_atomicity)) {
    // We avoid the case of a non-constant (or volatile) Int64 value because we would need to
    // place it in a register pair. If the slow path is taken, the ParallelMove might fail to move
    // the pair according to the X86DexCallingConvention in case of an overlap (e.g., move the
    // int64 value from <EAX, EBX> to <EBX, ECX>). (Bug: b/168687887)
    return;
  }

  ArenaAllocator* allocator = invoke->GetBlock()->GetGraph()->GetAllocator();
  LocationSummary* locations = new (allocator) LocationSummary(
      invoke, LocationSummary::kCallOnSlowPath, kIntrinsified);
  locations->SetInAt(0, Location::RequiresRegister());
  size_t expected_coordinates_count = GetExpectedVarHandleCoordinatesCount(invoke);
  if (expected_coordinates_count == 1u) {
    // For instance fields, this is the source object
    locations->SetInAt(1, Location::RequiresRegister());
  }

  switch (value_type) {
    case DataType::Type::kBool:
    case DataType::Type::kInt8:
    case DataType::Type::kUint8:
      // Ensure the value is in a byte register
      locations->SetInAt(value_index, Location::ByteRegisterOrConstant(EBX, value));
      break;
    case DataType::Type::kInt16:
    case DataType::Type::kUint16:
    case DataType::Type::kInt32:
      locations->SetInAt(value_index, Location::RegisterOrConstant(value));
      break;
    case DataType::Type::kInt64:
      // We only handle constant non-atomic int64 values.
      DCHECK(value->IsConstant());
      locations->SetInAt(value_index, Location::ConstantLocation(value));
      break;
    case DataType::Type::kReference:
      locations->SetInAt(value_index, Location::RequiresRegister());
      break;
    default:
      DCHECK(DataType::IsFloatingPointType(value_type));
      if (needs_atomicity && value_type == DataType::Type::kFloat64) {
        locations->SetInAt(value_index, Location::RequiresFpuRegister());
      } else {
        locations->SetInAt(value_index, Location::FpuRegisterOrConstant(value));
      }
  }

  locations->AddTemp(Location::RequiresRegister());
  // This temporary register is also used for card for MarkGCCard. Make sure it's a byte register
  locations->AddTemp(Location::RegisterLocation(EAX));
  if (expected_coordinates_count == 0 && value_type == DataType::Type::kReference) {
    // For static reference fields, we need another temporary for the declaring class. We set it
    // last because we want to make sure that the first 2 temps are reserved for HandleFieldSet.
    locations->AddTemp(Location::RequiresRegister());
  }
}

static void GenerateVarHandleSet(HInvoke* invoke, CodeGeneratorX86* codegen) {
  // The only read barrier implementation supporting the
  // VarHandleGet intrinsic is the Baker-style read barriers.
  DCHECK_IMPLIES(codegen->EmitReadBarrier(), kUseBakerReadBarrier);

  X86Assembler* assembler = codegen->GetAssembler();
  LocationSummary* locations = invoke->GetLocations();
  // The value we want to set is the last argument
  uint32_t value_index = invoke->GetNumberOfArguments() - 1;
  DataType::Type value_type = GetDataTypeFromShorty(invoke, value_index);
  Register temp = locations->GetTemp(0).AsRegister<Register>();
  Register temp2 = locations->GetTemp(1).AsRegister<Register>();
  SlowPathCode* slow_path = new (codegen->GetScopedAllocator()) IntrinsicSlowPathX86(invoke);
  codegen->AddSlowPath(slow_path);

  GenerateVarHandleCommonChecks(invoke, temp, slow_path, assembler);

  // For static reference fields, we need another temporary for the declaring class. But since
  // for instance fields the object is in a separate register, it is safe to use the first
  // temporary register for GenerateVarHandleFieldReference.
  size_t expected_coordinates_count = GetExpectedVarHandleCoordinatesCount(invoke);
  if (value_type == DataType::Type::kReference && expected_coordinates_count == 0) {
    temp = locations->GetTemp(2).AsRegister<Register>();
  }

  Register offset = temp2;
  // Get the field referred by the VarHandle. The returned register contains the object reference
  // or the declaring class. The field offset will be placed in 'offset'. For static fields, the
  // declaring class will be placed in 'temp' register.
  Register reference = GenerateVarHandleFieldReference(invoke, codegen, temp, offset);

  bool is_volatile = false;
  switch (invoke->GetIntrinsic()) {
    case Intrinsics::kVarHandleSet:
    case Intrinsics::kVarHandleSetOpaque:
      // The only constraint for setOpaque is to ensure bitwise atomicity (atomically set 64 bit
      // values), but we don't treat Int64 values because we would need to place it in a register
      // pair. If the slow path is taken, the Parallel move might fail to move the register pair
      // in case of an overlap (e.g., move from <EAX, EBX> to <EBX, ECX>). (Bug: b/168687887)
      break;
    case Intrinsics::kVarHandleSetRelease:
      // setRelease needs to ensure atomicity too. See the above comment.
      codegen->GenerateMemoryBarrier(MemBarrierKind::kAnyStore);
      break;
    case Intrinsics::kVarHandleSetVolatile:
      is_volatile = true;
      break;
    default:
      LOG(FATAL) << "GenerateVarHandleSet received non-set intrinsic " << invoke->GetIntrinsic();
  }

  InstructionCodeGeneratorX86* instr_codegen =
        down_cast<InstructionCodeGeneratorX86*>(codegen->GetInstructionVisitor());
  // Store the value to the field
  instr_codegen->HandleFieldSet(
      invoke,
      value_index,
      value_type,
      Address(reference, offset, TIMES_1, 0),
      reference,
      is_volatile,
      /* value_can_be_null */ true,
      // Value can be null, and this write barrier is not being relied on for other sets.
      value_type == DataType::Type::kReference ? WriteBarrierKind::kEmitNotBeingReliedOn :
                                                 WriteBarrierKind::kDontEmit);

  __ Bind(slow_path->GetExitLabel());
}

void IntrinsicLocationsBuilderX86::VisitVarHandleSet(HInvoke* invoke) {
  CreateVarHandleSetLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitVarHandleSet(HInvoke* invoke) {
  GenerateVarHandleSet(invoke, codegen_);
}

void IntrinsicLocationsBuilderX86::VisitVarHandleSetVolatile(HInvoke* invoke) {
  CreateVarHandleSetLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitVarHandleSetVolatile(HInvoke* invoke) {
  GenerateVarHandleSet(invoke, codegen_);
}

void IntrinsicLocationsBuilderX86::VisitVarHandleSetRelease(HInvoke* invoke) {
  CreateVarHandleSetLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitVarHandleSetRelease(HInvoke* invoke) {
  GenerateVarHandleSet(invoke, codegen_);
}

void IntrinsicLocationsBuilderX86::VisitVarHandleSetOpaque(HInvoke* invoke) {
  CreateVarHandleSetLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitVarHandleSetOpaque(HInvoke* invoke) {
  GenerateVarHandleSet(invoke, codegen_);
}

static void CreateVarHandleGetAndSetLocations(HInvoke* invoke, CodeGeneratorX86* codegen) {
  // The only read barrier implementation supporting the
  // VarHandleGet intrinsic is the Baker-style read barriers.
  if (codegen->EmitNonBakerReadBarrier()) {
    return;
  }

  if (!HasVarHandleIntrinsicImplementation(invoke)) {
    return;
  }

  uint32_t number_of_arguments = invoke->GetNumberOfArguments();
  uint32_t value_index = number_of_arguments - 1;
  DataType::Type value_type = GetDataTypeFromShorty(invoke, value_index);

  if (DataType::Is64BitType(value_type)) {
    // We avoid the case of an Int64/Float64 value because we would need to place it in a register
    // pair. If the slow path is taken, the ParallelMove might fail to move the pair according to
    // the X86DexCallingConvention in case of an overlap (e.g., move the 64 bit value from
    // <EAX, EBX> to <EBX, ECX>).
    return;
  }

  ArenaAllocator* allocator = invoke->GetBlock()->GetGraph()->GetAllocator();
  LocationSummary* locations = new (allocator) LocationSummary(
      invoke, LocationSummary::kCallOnSlowPath, kIntrinsified);
  locations->AddTemp(Location::RequiresRegister());
  locations->AddTemp(Location::RequiresRegister());
  // We use this temporary for the card, so we need a byte register
  locations->AddTemp(Location::RegisterLocation(EBX));
  locations->SetInAt(0, Location::RequiresRegister());
  if (GetExpectedVarHandleCoordinatesCount(invoke) == 1u) {
    // For instance fields, this is the source object
    locations->SetInAt(1, Location::RequiresRegister());
  } else {
    // For static fields, we need another temp because one will be busy with the declaring class.
    locations->AddTemp(Location::RequiresRegister());
  }
  if (value_type == DataType::Type::kFloat32) {
    locations->AddTemp(Location::RegisterLocation(EAX));
    locations->SetInAt(value_index, Location::FpuRegisterOrConstant(invoke->InputAt(value_index)));
    locations->SetOut(Location::RequiresFpuRegister());
  } else {
    locations->SetInAt(value_index, Location::RegisterLocation(EAX));
    locations->SetOut(Location::RegisterLocation(EAX));
  }
}

static void GenerateVarHandleGetAndSet(HInvoke* invoke, CodeGeneratorX86* codegen) {
  // The only read barrier implementation supporting the
  // VarHandleGet intrinsic is the Baker-style read barriers.
  DCHECK_IMPLIES(codegen->EmitReadBarrier(), kUseBakerReadBarrier);

  X86Assembler* assembler = codegen->GetAssembler();
  LocationSummary* locations = invoke->GetLocations();
  // The value we want to set is the last argument
  uint32_t value_index = invoke->GetNumberOfArguments() - 1;
  Location value = locations->InAt(value_index);
  DataType::Type value_type = GetDataTypeFromShorty(invoke, value_index);
  Register temp = locations->GetTemp(1).AsRegister<Register>();
  Register temp2 = locations->GetTemp(2).AsRegister<Register>();
  SlowPathCode* slow_path = new (codegen->GetScopedAllocator()) IntrinsicSlowPathX86(invoke);
  codegen->AddSlowPath(slow_path);

  GenerateVarHandleCommonChecks(invoke, temp, slow_path, assembler);

  Register offset = locations->GetTemp(0).AsRegister<Register>();
  // Get the field referred by the VarHandle. The returned register contains the object reference
  // or the declaring class. The field offset will be placed in 'offset'. For static fields, the
  // declaring class will be placed in 'temp' register.
  Register reference = GenerateVarHandleFieldReference(invoke, codegen, temp, offset);
  Address field_addr(reference, offset, TIMES_1, 0);

  if (invoke->GetIntrinsic() == Intrinsics::kVarHandleGetAndSetRelease) {
    codegen->GenerateMemoryBarrier(MemBarrierKind::kAnyStore);
  }

  size_t expected_coordinates_count = GetExpectedVarHandleCoordinatesCount(invoke);
  // For static fields, we need another temporary for the declaring class. But since for instance
  // fields the object is in a separate register, it is safe to use the first temporary register.
  temp = expected_coordinates_count == 1u ? temp : locations->GetTemp(3).AsRegister<Register>();
  // No need for a lock prefix. `xchg` has an implicit lock when it is used with an address.
  switch (value_type) {
    case DataType::Type::kBool:
      __ xchgb(value.AsRegister<ByteRegister>(), field_addr);
      __ movzxb(locations->Out().AsRegister<Register>(),
                locations->Out().AsRegister<ByteRegister>());
      break;
    case DataType::Type::kInt8:
      __ xchgb(value.AsRegister<ByteRegister>(), field_addr);
      __ movsxb(locations->Out().AsRegister<Register>(),
                locations->Out().AsRegister<ByteRegister>());
      break;
    case DataType::Type::kUint16:
      __ xchgw(value.AsRegister<Register>(), field_addr);
      __ movzxw(locations->Out().AsRegister<Register>(), locations->Out().AsRegister<Register>());
      break;
    case DataType::Type::kInt16:
      __ xchgw(value.AsRegister<Register>(), field_addr);
      __ movsxw(locations->Out().AsRegister<Register>(), locations->Out().AsRegister<Register>());
      break;
    case DataType::Type::kInt32:
      __ xchgl(value.AsRegister<Register>(), field_addr);
      break;
    case DataType::Type::kFloat32:
      codegen->Move32(Location::RegisterLocation(EAX), value);
      __ xchgl(EAX, field_addr);
      __ movd(locations->Out().AsFpuRegister<XmmRegister>(), EAX);
      break;
    case DataType::Type::kReference: {
      if (codegen->EmitBakerReadBarrier()) {
        // Need to make sure the reference stored in the field is a to-space
        // one before attempting the CAS or the CAS could fail incorrectly.
        codegen->GenerateReferenceLoadWithBakerReadBarrier(
            invoke,
            // Unused, used only as a "temporary" within the read barrier.
            Location::RegisterLocation(temp),
            reference,
            field_addr,
            /* needs_null_check= */ false,
            /* always_update_field= */ true,
            &temp2);
      }
      codegen->MarkGCCard(temp, temp2, reference);
      if (kPoisonHeapReferences) {
        __ movl(temp, value.AsRegister<Register>());
        __ PoisonHeapReference(temp);
        __ xchgl(temp, field_addr);
        __ UnpoisonHeapReference(temp);
        __ movl(locations->Out().AsRegister<Register>(), temp);
      } else {
        __ xchgl(locations->Out().AsRegister<Register>(), field_addr);
      }
      break;
    }
    default:
      UNREACHABLE();
  }

  if (invoke->GetIntrinsic() == Intrinsics::kVarHandleGetAndSetAcquire) {
    codegen->GenerateMemoryBarrier(MemBarrierKind::kLoadAny);
  }

  __ Bind(slow_path->GetExitLabel());
}

void IntrinsicLocationsBuilderX86::VisitVarHandleGetAndSet(HInvoke* invoke) {
  CreateVarHandleGetAndSetLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitVarHandleGetAndSet(HInvoke* invoke) {
  GenerateVarHandleGetAndSet(invoke, codegen_);
}

void IntrinsicLocationsBuilderX86::VisitVarHandleGetAndSetAcquire(HInvoke* invoke) {
  CreateVarHandleGetAndSetLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitVarHandleGetAndSetAcquire(HInvoke* invoke) {
  GenerateVarHandleGetAndSet(invoke, codegen_);
}

void IntrinsicLocationsBuilderX86::VisitVarHandleGetAndSetRelease(HInvoke* invoke) {
  CreateVarHandleGetAndSetLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitVarHandleGetAndSetRelease(HInvoke* invoke) {
  GenerateVarHandleGetAndSet(invoke, codegen_);
}

static void CreateVarHandleCompareAndSetOrExchangeLocations(HInvoke* invoke,
                                                            CodeGeneratorX86* codegen) {
  // The only read barrier implementation supporting the
  // VarHandleGet intrinsic is the Baker-style read barriers.
  if (codegen->EmitNonBakerReadBarrier()) {
    return;
  }

  if (!HasVarHandleIntrinsicImplementation(invoke)) {
    return;
  }

  uint32_t number_of_arguments = invoke->GetNumberOfArguments();
  uint32_t expected_value_index = number_of_arguments - 2;
  uint32_t new_value_index = number_of_arguments - 1;
  DataType::Type value_type = GetDataTypeFromShorty(invoke, expected_value_index);
  DCHECK_EQ(value_type, GetDataTypeFromShorty(invoke, new_value_index));

  if (DataType::Is64BitType(value_type)) {
    // We avoid the case of an Int64/Float64 value because we would need to place it in a register
    // pair. If the slow path is taken, the ParallelMove might fail to move the pair according to
    // the X86DexCallingConvention in case of an overlap (e.g., move the 64 bit value from
    // <EAX, EBX> to <EBX, ECX>).
    return;
  }

  ArenaAllocator* allocator = invoke->GetBlock()->GetGraph()->GetAllocator();
  LocationSummary* locations = new (allocator) LocationSummary(
      invoke, LocationSummary::kCallOnSlowPath, kIntrinsified);
  locations->AddTemp(Location::RequiresRegister());
  locations->AddTemp(Location::RequiresRegister());
  // We use this temporary for the card, so we need a byte register
  locations->AddTemp(Location::RegisterLocation(EBX));
  locations->SetInAt(0, Location::RequiresRegister());
  if (GetExpectedVarHandleCoordinatesCount(invoke) == 1u) {
    // For instance fields, this is the source object
    locations->SetInAt(1, Location::RequiresRegister());
  } else {
    // For static fields, we need another temp because one will be busy with the declaring class.
    locations->AddTemp(Location::RequiresRegister());
  }
  if (DataType::IsFloatingPointType(value_type)) {
    // We need EAX for placing the expected value
    locations->AddTemp(Location::RegisterLocation(EAX));
    locations->SetInAt(new_value_index,
                       Location::FpuRegisterOrConstant(invoke->InputAt(new_value_index)));
    locations->SetInAt(expected_value_index,
                       Location::FpuRegisterOrConstant(invoke->InputAt(expected_value_index)));
  } else {
    // Ensure it's in a byte register
    locations->SetInAt(new_value_index, Location::RegisterLocation(ECX));
    locations->SetInAt(expected_value_index, Location::RegisterLocation(EAX));
  }

  mirror::VarHandle::AccessModeTemplate access_mode_template =
      mirror::VarHandle::GetAccessModeTemplateByIntrinsic(invoke->GetIntrinsic());

  if (access_mode_template == mirror::VarHandle::AccessModeTemplate::kCompareAndExchange &&
      value_type == DataType::Type::kFloat32) {
    locations->SetOut(Location::RequiresFpuRegister());
  } else {
    locations->SetOut(Location::RegisterLocation(EAX));
  }
}

static void GenerateVarHandleCompareAndSetOrExchange(HInvoke* invoke, CodeGeneratorX86* codegen) {
  // The only read barrier implementation supporting the
  // VarHandleGet intrinsic is the Baker-style read barriers.
  DCHECK_IMPLIES(codegen->EmitReadBarrier(), kUseBakerReadBarrier);

  X86Assembler* assembler = codegen->GetAssembler();
  LocationSummary* locations = invoke->GetLocations();
  uint32_t number_of_arguments = invoke->GetNumberOfArguments();
  uint32_t expected_value_index = number_of_arguments - 2;
  uint32_t new_value_index = number_of_arguments - 1;
  DataType::Type type = GetDataTypeFromShorty(invoke, expected_value_index);
  DCHECK_EQ(type, GetDataTypeFromShorty(invoke, new_value_index));
  Location expected_value = locations->InAt(expected_value_index);
  Location new_value = locations->InAt(new_value_index);
  Register offset = locations->GetTemp(0).AsRegister<Register>();
  Register temp = locations->GetTemp(1).AsRegister<Register>();
  Register temp2 = locations->GetTemp(2).AsRegister<Register>();
  SlowPathCode* slow_path = new (codegen->GetScopedAllocator()) IntrinsicSlowPathX86(invoke);
  codegen->AddSlowPath(slow_path);

  GenerateVarHandleCommonChecks(invoke, temp, slow_path, assembler);

  // Get the field referred by the VarHandle. The returned register contains the object reference
  // or the declaring class. The field offset will be placed in 'offset'. For static fields, the
  // declaring class will be placed in 'temp' register.
  Register reference = GenerateVarHandleFieldReference(invoke, codegen, temp, offset);

  uint32_t expected_coordinates_count = GetExpectedVarHandleCoordinatesCount(invoke);
  // For generating the compare and exchange, we need 2 temporaries. In case of a static field, the
  // first temporary contains the declaring class so we need another temporary. In case of an
  // instance field, the object comes in a separate register so it's safe to use the first temp.
  temp = (expected_coordinates_count == 1u) ? temp : locations->GetTemp(3).AsRegister<Register>();
  DCHECK_NE(temp, reference);

  // We are using `lock cmpxchg` in all cases because there is no CAS equivalent that has weak
  // failure semantics. `lock cmpxchg` has full barrier semantics, and we don't need scheduling
  // barriers at this time.

  mirror::VarHandle::AccessModeTemplate access_mode_template =
      mirror::VarHandle::GetAccessModeTemplateByIntrinsic(invoke->GetIntrinsic());
  bool is_cmpxchg =
      access_mode_template == mirror::VarHandle::AccessModeTemplate::kCompareAndExchange;

  if (type == DataType::Type::kReference) {
    GenReferenceCAS(
        invoke, codegen, expected_value, new_value, reference, offset, temp, temp2, is_cmpxchg);
  } else {
    Location out = locations->Out();
    GenPrimitiveCAS(
        type, codegen, expected_value, new_value, reference, offset, out, temp, is_cmpxchg);
  }

  __ Bind(slow_path->GetExitLabel());
}

void IntrinsicLocationsBuilderX86::VisitVarHandleCompareAndSet(HInvoke* invoke) {
  CreateVarHandleCompareAndSetOrExchangeLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitVarHandleCompareAndSet(HInvoke* invoke) {
  GenerateVarHandleCompareAndSetOrExchange(invoke, codegen_);
}

void IntrinsicLocationsBuilderX86::VisitVarHandleWeakCompareAndSet(HInvoke* invoke) {
  CreateVarHandleCompareAndSetOrExchangeLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitVarHandleWeakCompareAndSet(HInvoke* invoke) {
  GenerateVarHandleCompareAndSetOrExchange(invoke, codegen_);
}

void IntrinsicLocationsBuilderX86::VisitVarHandleWeakCompareAndSetPlain(HInvoke* invoke) {
  CreateVarHandleCompareAndSetOrExchangeLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitVarHandleWeakCompareAndSetPlain(HInvoke* invoke) {
  GenerateVarHandleCompareAndSetOrExchange(invoke, codegen_);
}

void IntrinsicLocationsBuilderX86::VisitVarHandleWeakCompareAndSetAcquire(HInvoke* invoke) {
  CreateVarHandleCompareAndSetOrExchangeLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitVarHandleWeakCompareAndSetAcquire(HInvoke* invoke) {
  GenerateVarHandleCompareAndSetOrExchange(invoke, codegen_);
}

void IntrinsicLocationsBuilderX86::VisitVarHandleWeakCompareAndSetRelease(HInvoke* invoke) {
  CreateVarHandleCompareAndSetOrExchangeLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitVarHandleWeakCompareAndSetRelease(HInvoke* invoke) {
  GenerateVarHandleCompareAndSetOrExchange(invoke, codegen_);
}

void IntrinsicLocationsBuilderX86::VisitVarHandleCompareAndExchange(HInvoke* invoke) {
  CreateVarHandleCompareAndSetOrExchangeLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitVarHandleCompareAndExchange(HInvoke* invoke) {
  GenerateVarHandleCompareAndSetOrExchange(invoke, codegen_);
}

void IntrinsicLocationsBuilderX86::VisitVarHandleCompareAndExchangeAcquire(HInvoke* invoke) {
  CreateVarHandleCompareAndSetOrExchangeLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitVarHandleCompareAndExchangeAcquire(HInvoke* invoke) {
  GenerateVarHandleCompareAndSetOrExchange(invoke, codegen_);
}

void IntrinsicLocationsBuilderX86::VisitVarHandleCompareAndExchangeRelease(HInvoke* invoke) {
  CreateVarHandleCompareAndSetOrExchangeLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitVarHandleCompareAndExchangeRelease(HInvoke* invoke) {
  GenerateVarHandleCompareAndSetOrExchange(invoke, codegen_);
}

static void CreateVarHandleGetAndAddLocations(HInvoke* invoke, CodeGeneratorX86* codegen) {
  // The only read barrier implementation supporting the
  // VarHandleGet intrinsic is the Baker-style read barriers.
  if (codegen->EmitNonBakerReadBarrier()) {
    return;
  }

  if (!HasVarHandleIntrinsicImplementation(invoke)) {
    return;
  }

  // The last argument should be the value we intend to set.
  uint32_t value_index = invoke->GetNumberOfArguments() - 1;
  DataType::Type value_type = GetDataTypeFromShorty(invoke, value_index);
  if (DataType::Is64BitType(value_type)) {
    // We avoid the case of an Int64/Float64 value because we would need to place it in a register
    // pair. If the slow path is taken, the ParallelMove might fail to move the pair according to
    // the X86DexCallingConvention in case of an overlap (e.g., move the 64 bit value from
    // <EAX, EBX> to <EBX, ECX>). (Bug: b/168687887)
    return;
  }

  ArenaAllocator* allocator = invoke->GetBlock()->GetGraph()->GetAllocator();
  LocationSummary* locations = new (allocator) LocationSummary(
      invoke, LocationSummary::kCallOnSlowPath, kIntrinsified);
  locations->AddTemp(Location::RequiresRegister());
  locations->AddTemp(Location::RequiresRegister());
  locations->SetInAt(0, Location::RequiresRegister());
  size_t expected_coordinates_count = GetExpectedVarHandleCoordinatesCount(invoke);
  if (expected_coordinates_count == 1u) {
    // For instance fields, this is the source object
    locations->SetInAt(1, Location::RequiresRegister());
  } else {
    // For static fields, we need another temp because one will be busy with the declaring class.
    locations->AddTemp(Location::RequiresRegister());
  }

  if (DataType::IsFloatingPointType(value_type)) {
    locations->AddTemp(Location::RequiresFpuRegister());
    locations->AddTemp(Location::RegisterLocation(EAX));
    locations->SetInAt(value_index, Location::RequiresFpuRegister());
    locations->SetOut(Location::RequiresFpuRegister());
  } else {
    // xadd updates the register argument with the old value. ByteRegister required for xaddb.
    locations->SetInAt(value_index, Location::RegisterLocation(EAX));
    locations->SetOut(Location::RegisterLocation(EAX));
  }
}

static void GenerateVarHandleGetAndAdd(HInvoke* invoke, CodeGeneratorX86* codegen) {
  // The only read barrier implementation supporting the
  // VarHandleGet intrinsic is the Baker-style read barriers.
  DCHECK_IMPLIES(codegen->EmitReadBarrier(), kUseBakerReadBarrier);

  X86Assembler* assembler = codegen->GetAssembler();
  LocationSummary* locations = invoke->GetLocations();
  uint32_t number_of_arguments = invoke->GetNumberOfArguments();
  uint32_t value_index = number_of_arguments - 1;
  DataType::Type type = GetDataTypeFromShorty(invoke, value_index);
  DCHECK_EQ(type, invoke->GetType());
  Location value_loc = locations->InAt(value_index);
  Register temp = locations->GetTemp(0).AsRegister<Register>();
  SlowPathCode* slow_path = new (codegen->GetScopedAllocator()) IntrinsicSlowPathX86(invoke);
  codegen->AddSlowPath(slow_path);

  GenerateVarHandleCommonChecks(invoke, temp, slow_path, assembler);

  Register offset = locations->GetTemp(1).AsRegister<Register>();
  // Get the field referred by the VarHandle. The returned register contains the object reference
  // or the declaring class. The field offset will be placed in 'offset'. For static fields, the
  // declaring class will be placed in 'temp' register.
  Register reference = GenerateVarHandleFieldReference(invoke, codegen, temp, offset);

  size_t expected_coordinates_count = GetExpectedVarHandleCoordinatesCount(invoke);
  temp = (expected_coordinates_count == 1u) ? temp : locations->GetTemp(2).AsRegister<Register>();
  DCHECK_NE(temp, reference);
  Address field_addr(reference, offset, TIMES_1, 0);

  switch (type) {
    case DataType::Type::kInt8:
      __ LockXaddb(field_addr, value_loc.AsRegister<ByteRegister>());
      __ movsxb(locations->Out().AsRegister<Register>(),
                locations->Out().AsRegister<ByteRegister>());
      break;
    case DataType::Type::kInt16:
      __ LockXaddw(field_addr, value_loc.AsRegister<Register>());
      __ movsxw(locations->Out().AsRegister<Register>(), locations->Out().AsRegister<Register>());
      break;
    case DataType::Type::kUint16:
      __ LockXaddw(field_addr, value_loc.AsRegister<Register>());
      __ movzxw(locations->Out().AsRegister<Register>(), locations->Out().AsRegister<Register>());
      break;
    case DataType::Type::kInt32:
      __ LockXaddl(field_addr, value_loc.AsRegister<Register>());
      break;
    case DataType::Type::kFloat32: {
      Location temp_float =
          (expected_coordinates_count == 1u) ? locations->GetTemp(2) : locations->GetTemp(3);
      DCHECK(temp_float.IsFpuRegister());
      Location eax = Location::RegisterLocation(EAX);
      NearLabel try_again;
      __ Bind(&try_again);
      __ movss(temp_float.AsFpuRegister<XmmRegister>(), field_addr);
      __ movd(EAX, temp_float.AsFpuRegister<XmmRegister>());
      __ addss(temp_float.AsFpuRegister<XmmRegister>(),
               value_loc.AsFpuRegister<XmmRegister>());
      GenPrimitiveLockedCmpxchg(type,
                                codegen,
                                /* expected_value= */ eax,
                                /* new_value= */ temp_float,
                                reference,
                                offset,
                                temp);
      __ j(kNotZero, &try_again);

      // The old value is present in EAX.
      codegen->Move32(locations->Out(), eax);
      break;
    }
    default:
      UNREACHABLE();
  }

  __ Bind(slow_path->GetExitLabel());
}

void IntrinsicLocationsBuilderX86::VisitVarHandleGetAndAdd(HInvoke* invoke) {
  CreateVarHandleGetAndAddLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitVarHandleGetAndAdd(HInvoke* invoke) {
  GenerateVarHandleGetAndAdd(invoke, codegen_);
}

void IntrinsicLocationsBuilderX86::VisitVarHandleGetAndAddAcquire(HInvoke* invoke) {
  CreateVarHandleGetAndAddLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitVarHandleGetAndAddAcquire(HInvoke* invoke) {
  GenerateVarHandleGetAndAdd(invoke, codegen_);
}

void IntrinsicLocationsBuilderX86::VisitVarHandleGetAndAddRelease(HInvoke* invoke) {
  CreateVarHandleGetAndAddLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitVarHandleGetAndAddRelease(HInvoke* invoke) {
  GenerateVarHandleGetAndAdd(invoke, codegen_);
}

static void CreateVarHandleGetAndBitwiseOpLocations(HInvoke* invoke, CodeGeneratorX86* codegen) {
  // The only read barrier implementation supporting the
  // VarHandleGet intrinsic is the Baker-style read barriers.
  if (codegen->EmitNonBakerReadBarrier()) {
    return;
  }

  if (!HasVarHandleIntrinsicImplementation(invoke)) {
    return;
  }

  // The last argument should be the value we intend to set.
  uint32_t value_index = invoke->GetNumberOfArguments() - 1;
  if (DataType::Is64BitType(GetDataTypeFromShorty(invoke, value_index))) {
    // We avoid the case of an Int64 value because we would need to place it in a register pair.
    // If the slow path is taken, the ParallelMove might fail to move the pair according to the
    // X86DexCallingConvention in case of an overlap (e.g., move the 64 bit value from
    // <EAX, EBX> to <EBX, ECX>). (Bug: b/168687887)
    return;
  }

  ArenaAllocator* allocator = invoke->GetBlock()->GetGraph()->GetAllocator();
  LocationSummary* locations = new (allocator) LocationSummary(
      invoke, LocationSummary::kCallOnSlowPath, kIntrinsified);
  // We need a byte register temp to store the result of the bitwise operation
  locations->AddTemp(Location::RegisterLocation(EBX));
  locations->AddTemp(Location::RequiresRegister());
  locations->SetInAt(0, Location::RequiresRegister());
  size_t expected_coordinates_count = GetExpectedVarHandleCoordinatesCount(invoke);
  if (expected_coordinates_count == 1u) {
    // For instance fields, this is the source object
    locations->SetInAt(1, Location::RequiresRegister());
  } else {
    // For static fields, we need another temp because one will be busy with the declaring class.
    locations->AddTemp(Location::RequiresRegister());
  }

  locations->SetInAt(value_index, Location::RegisterOrConstant(invoke->InputAt(value_index)));
  locations->SetOut(Location::RegisterLocation(EAX));
}

static void GenerateBitwiseOp(HInvoke* invoke,
                              CodeGeneratorX86* codegen,
                              Register left,
                              Register right) {
  X86Assembler* assembler = codegen->GetAssembler();

  switch (invoke->GetIntrinsic()) {
    case Intrinsics::kVarHandleGetAndBitwiseOr:
    case Intrinsics::kVarHandleGetAndBitwiseOrAcquire:
    case Intrinsics::kVarHandleGetAndBitwiseOrRelease:
      __ orl(left, right);
      break;
    case Intrinsics::kVarHandleGetAndBitwiseXor:
    case Intrinsics::kVarHandleGetAndBitwiseXorAcquire:
    case Intrinsics::kVarHandleGetAndBitwiseXorRelease:
      __ xorl(left, right);
      break;
    case Intrinsics::kVarHandleGetAndBitwiseAnd:
    case Intrinsics::kVarHandleGetAndBitwiseAndAcquire:
    case Intrinsics::kVarHandleGetAndBitwiseAndRelease:
      __ andl(left, right);
      break;
    default:
      UNREACHABLE();
  }
}

static void GenerateVarHandleGetAndBitwiseOp(HInvoke* invoke, CodeGeneratorX86* codegen) {
  // The only read barrier implementation supporting the
  // VarHandleGet intrinsic is the Baker-style read barriers.
  DCHECK_IMPLIES(codegen->EmitReadBarrier(), kUseBakerReadBarrier);

  X86Assembler* assembler = codegen->GetAssembler();
  LocationSummary* locations = invoke->GetLocations();
  uint32_t value_index = invoke->GetNumberOfArguments() - 1;
  DataType::Type type = GetDataTypeFromShorty(invoke, value_index);
  DCHECK_EQ(type, invoke->GetType());
  Register temp = locations->GetTemp(0).AsRegister<Register>();
  SlowPathCode* slow_path = new (codegen->GetScopedAllocator()) IntrinsicSlowPathX86(invoke);
  codegen->AddSlowPath(slow_path);

  GenerateVarHandleCommonChecks(invoke, temp, slow_path, assembler);

  Register offset = locations->GetTemp(1).AsRegister<Register>();
  size_t expected_coordinates_count = GetExpectedVarHandleCoordinatesCount(invoke);
  // For static field, we need another temporary because the first one contains the declaring class
  Register reference =
      (expected_coordinates_count == 1u) ? temp : locations->GetTemp(2).AsRegister<Register>();
  // Get the field referred by the VarHandle. The returned register contains the object reference
  // or the declaring class. The field offset will be placed in 'offset'. For static fields, the
  // declaring class will be placed in 'reference' register.
  reference = GenerateVarHandleFieldReference(invoke, codegen, reference, offset);
  DCHECK_NE(temp, reference);
  Address field_addr(reference, offset, TIMES_1, 0);

  Register out = locations->Out().AsRegister<Register>();
  DCHECK_EQ(out, EAX);

  if (invoke->GetIntrinsic() == Intrinsics::kVarHandleGetAndBitwiseOrRelease ||
      invoke->GetIntrinsic() == Intrinsics::kVarHandleGetAndBitwiseXorRelease ||
      invoke->GetIntrinsic() == Intrinsics::kVarHandleGetAndBitwiseAndRelease) {
    codegen->GenerateMemoryBarrier(MemBarrierKind::kAnyStore);
  }

  NearLabel try_again;
  __ Bind(&try_again);
  // Place the expected value in EAX for cmpxchg
  codegen->LoadFromMemoryNoBarrier(type, locations->Out(), field_addr);
  codegen->Move32(locations->GetTemp(0), locations->InAt(value_index));
  GenerateBitwiseOp(invoke, codegen, temp, out);
  GenPrimitiveLockedCmpxchg(type,
                            codegen,
                            /* expected_value= */ locations->Out(),
                            /* new_value= */ locations->GetTemp(0),
                            reference,
                            offset);
  // If the cmpxchg failed, another thread changed the value so try again.
  __ j(kNotZero, &try_again);

  // The old value is present in EAX.

  if (invoke->GetIntrinsic() == Intrinsics::kVarHandleGetAndBitwiseOrAcquire ||
      invoke->GetIntrinsic() == Intrinsics::kVarHandleGetAndBitwiseXorAcquire ||
      invoke->GetIntrinsic() == Intrinsics::kVarHandleGetAndBitwiseAndAcquire) {
    codegen->GenerateMemoryBarrier(MemBarrierKind::kLoadAny);
  }

  __ Bind(slow_path->GetExitLabel());
}

void IntrinsicLocationsBuilderX86::VisitVarHandleGetAndBitwiseOr(HInvoke* invoke) {
  CreateVarHandleGetAndBitwiseOpLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitVarHandleGetAndBitwiseOr(HInvoke* invoke) {
  GenerateVarHandleGetAndBitwiseOp(invoke, codegen_);
}

void IntrinsicLocationsBuilderX86::VisitVarHandleGetAndBitwiseOrAcquire(HInvoke* invoke) {
  CreateVarHandleGetAndBitwiseOpLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitVarHandleGetAndBitwiseOrAcquire(HInvoke* invoke) {
  GenerateVarHandleGetAndBitwiseOp(invoke, codegen_);
}

void IntrinsicLocationsBuilderX86::VisitVarHandleGetAndBitwiseOrRelease(HInvoke* invoke) {
  CreateVarHandleGetAndBitwiseOpLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitVarHandleGetAndBitwiseOrRelease(HInvoke* invoke) {
  GenerateVarHandleGetAndBitwiseOp(invoke, codegen_);
}

void IntrinsicLocationsBuilderX86::VisitVarHandleGetAndBitwiseXor(HInvoke* invoke) {
  CreateVarHandleGetAndBitwiseOpLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitVarHandleGetAndBitwiseXor(HInvoke* invoke) {
  GenerateVarHandleGetAndBitwiseOp(invoke, codegen_);
}

void IntrinsicLocationsBuilderX86::VisitVarHandleGetAndBitwiseXorAcquire(HInvoke* invoke) {
  CreateVarHandleGetAndBitwiseOpLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitVarHandleGetAndBitwiseXorAcquire(HInvoke* invoke) {
  GenerateVarHandleGetAndBitwiseOp(invoke, codegen_);
}

void IntrinsicLocationsBuilderX86::VisitVarHandleGetAndBitwiseXorRelease(HInvoke* invoke) {
  CreateVarHandleGetAndBitwiseOpLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitVarHandleGetAndBitwiseXorRelease(HInvoke* invoke) {
  GenerateVarHandleGetAndBitwiseOp(invoke, codegen_);
}

void IntrinsicLocationsBuilderX86::VisitVarHandleGetAndBitwiseAnd(HInvoke* invoke) {
  CreateVarHandleGetAndBitwiseOpLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitVarHandleGetAndBitwiseAnd(HInvoke* invoke) {
  GenerateVarHandleGetAndBitwiseOp(invoke, codegen_);
}

void IntrinsicLocationsBuilderX86::VisitVarHandleGetAndBitwiseAndAcquire(HInvoke* invoke) {
  CreateVarHandleGetAndBitwiseOpLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitVarHandleGetAndBitwiseAndAcquire(HInvoke* invoke) {
  GenerateVarHandleGetAndBitwiseOp(invoke, codegen_);
}

void IntrinsicLocationsBuilderX86::VisitVarHandleGetAndBitwiseAndRelease(HInvoke* invoke) {
  CreateVarHandleGetAndBitwiseOpLocations(invoke, codegen_);
}

void IntrinsicCodeGeneratorX86::VisitVarHandleGetAndBitwiseAndRelease(HInvoke* invoke) {
  GenerateVarHandleGetAndBitwiseOp(invoke, codegen_);
}

static void GenerateMathFma(HInvoke* invoke, CodeGeneratorX86* codegen) {
  DCHECK(DataType::IsFloatingPointType(invoke->GetType()));
  LocationSummary* locations = invoke->GetLocations();
  DCHECK(locations->InAt(0).Equals(locations->Out()));
  X86Assembler* assembler = codegen->GetAssembler();
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

void IntrinsicCodeGeneratorX86::VisitMathFmaDouble(HInvoke* invoke) {
  DCHECK(codegen_->GetInstructionSetFeatures().HasAVX2());
  GenerateMathFma(invoke, codegen_);
}

void IntrinsicLocationsBuilderX86::VisitMathFmaDouble(HInvoke* invoke) {
  if (codegen_->GetInstructionSetFeatures().HasAVX2()) {
    CreateFPFPFPToFPCallLocations(allocator_, invoke);
  }
}

void IntrinsicCodeGeneratorX86::VisitMathFmaFloat(HInvoke* invoke) {
  DCHECK(codegen_->GetInstructionSetFeatures().HasAVX2());
  GenerateMathFma(invoke, codegen_);
}

void IntrinsicLocationsBuilderX86::VisitMathFmaFloat(HInvoke* invoke) {
  if (codegen_->GetInstructionSetFeatures().HasAVX2()) {
    CreateFPFPFPToFPCallLocations(allocator_, invoke);
  }
}

#define MARK_UNIMPLEMENTED(Name) UNIMPLEMENTED_INTRINSIC(X86, Name)
UNIMPLEMENTED_INTRINSIC_LIST_X86(MARK_UNIMPLEMENTED);
#undef MARK_UNIMPLEMENTED

UNREACHABLE_INTRINSICS(X86)

#undef __

}  // namespace x86
}  // namespace art
