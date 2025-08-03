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

#ifndef ART_COMPILER_OPTIMIZING_INTRINSICS_H_
#define ART_COMPILER_OPTIMIZING_INTRINSICS_H_

#include "base/macros.h"
#include "code_generator.h"
#include "intrinsics_list.h"
#include "nodes.h"
#include "optimization.h"
#include "parallel_move_resolver.h"

namespace art HIDDEN {

class DexFile;

// Positive floating-point infinities.
static constexpr uint32_t kPositiveInfinityFloat = 0x7f800000U;
static constexpr uint64_t kPositiveInfinityDouble = UINT64_C(0x7ff0000000000000);

static constexpr uint32_t kNanFloat = 0x7fc00000U;
static constexpr uint64_t kNanDouble = 0x7ff8000000000000;

class IntrinsicVisitor : public ValueObject {
 public:
  virtual ~IntrinsicVisitor() {}

  // Dispatch logic.

  void Dispatch(HInvoke* invoke) {
    switch (invoke->GetIntrinsic()) {
      case Intrinsics::kNone:
        return;

#define OPTIMIZING_INTRINSICS_WITH_SPECIALIZED_HIR(Name, ...) \
      case Intrinsics::k ## Name:
        ART_INTRINSICS_WITH_SPECIALIZED_HIR_LIST(OPTIMIZING_INTRINSICS_WITH_SPECIALIZED_HIR)
#undef OPTIMIZING_INTRINSICS_WITH_SPECIALIZED_HIR
        // Note: clang++ can optimize this `switch` to a range check and a virtual dispatch
        // with indexed load from the vtable using an adjusted `invoke->GetIntrinsic()`
        // as the index. However, a non-empty `case` causes clang++ to produce much worse
        // code, so we want to limit this check to debug builds only.
        DCHECK(false) << "Unexpected intrinsic with HIR: " << invoke->GetIntrinsic();
        return;

#define OPTIMIZING_INTRINSICS(Name, ...) \
      case Intrinsics::k ## Name: \
        Visit ## Name(invoke);    \
        return;
        ART_INTRINSICS_WITH_HINVOKE_LIST(OPTIMIZING_INTRINSICS)
#undef OPTIMIZING_INTRINSICS

      // Do not put a default case. That way the compiler will complain if we missed a case.
    }
  }

  // Define visitor methods.

#define DECLARE_VISIT_INTRINSIC(Name, ...) \
  virtual void Visit##Name([[maybe_unused]] HInvoke* invoke) = 0;
  ART_INTRINSICS_WITH_HINVOKE_LIST(DECLARE_VISIT_INTRINSIC)
#undef DECLARE_VISIT_INTRINSIC

  static void MoveArguments(HInvoke* invoke,
                            CodeGenerator* codegen,
                            InvokeDexCallingConventionVisitor* calling_convention_visitor) {
    if (kIsDebugBuild && invoke->IsInvokeStaticOrDirect()) {
      HInvokeStaticOrDirect* invoke_static_or_direct = invoke->AsInvokeStaticOrDirect();
      // Explicit clinit checks triggered by static invokes must have been
      // pruned by art::PrepareForRegisterAllocation.
      DCHECK(!invoke_static_or_direct->IsStaticWithExplicitClinitCheck());
    }

    if (invoke->GetNumberOfArguments() == 0) {
      // No argument to move.
      return;
    }

    LocationSummary* locations = invoke->GetLocations();

    // We're moving potentially two or more locations to locations that could overlap, so we need
    // a parallel move resolver.
    HParallelMove parallel_move(codegen->GetGraph()->GetAllocator());

    for (size_t i = 0; i < invoke->GetNumberOfArguments(); i++) {
      HInstruction* input = invoke->InputAt(i);
      Location cc_loc = calling_convention_visitor->GetNextLocation(input->GetType());
      Location actual_loc = locations->InAt(i);

      parallel_move.AddMove(actual_loc, cc_loc, input->GetType(), nullptr);
    }

    codegen->GetMoveResolver()->EmitNativeCode(&parallel_move);
  }

  static void ComputeValueOfLocations(HInvoke* invoke,
                                      CodeGenerator* codegen,
                                      int32_t low,
                                      int32_t length,
                                      Location return_location,
                                      Location first_argument_location);

  // Temporary data structure for holding BoxedType.valueOf data for generating code.
  struct ValueOfInfo {
    static constexpr uint32_t kInvalidReference = static_cast<uint32_t>(-1);

    ValueOfInfo();

    // Offset of the value field of the boxed object for initializing a newly allocated instance.
    uint32_t value_offset;
    // The low value in the cache.
    int32_t low;
    // The length of the cache array.
    uint32_t length;

    // This union contains references to the boot image. For app AOT or JIT compilation,
    // these are the boot image offsets of the target. For boot image compilation, the
    // location shall be known only at link time, so we encode a symbolic reference using
    // IntrinsicObjects::EncodePatch().
    union {
      // The target value for a constant input in the cache range. If the constant input
      // is out of range (use `low` and `length` to check), this value is bogus (set to
      // kInvalidReference) and the code must allocate a new Integer.
      uint32_t value_boot_image_reference;

      // The cache array data used for a non-constant input in the cache range.
      // If the input is out of range, the code must allocate a new Integer.
      uint32_t array_data_boot_image_reference;
    };
  };

  static ValueOfInfo ComputeValueOfInfo(
      HInvoke* invoke,
      const CompilerOptions& compiler_options,
      ArtField* value_field,
      int32_t low,
      int32_t length,
      size_t base);

  static MemberOffset GetReferenceDisableIntrinsicOffset();
  static MemberOffset GetReferenceSlowPathEnabledOffset();
  static void CreateReferenceGetReferentLocations(HInvoke* invoke, CodeGenerator* codegen);
  static void CreateReferenceRefersToLocations(HInvoke* invoke, CodeGenerator* codegen);

 protected:
  IntrinsicVisitor() {}

  static void AssertNonMovableStringClass();

 private:
  DISALLOW_COPY_AND_ASSIGN(IntrinsicVisitor);
};

static inline bool IsIntrinsicWithSpecializedHir(Intrinsics intrinsic) {
  switch (intrinsic) {
#define OPTIMIZING_INTRINSICS_WITH_SPECIALIZED_HIR(Name, ...) \
    case Intrinsics::k ## Name:
      ART_INTRINSICS_WITH_SPECIALIZED_HIR_LIST(OPTIMIZING_INTRINSICS_WITH_SPECIALIZED_HIR)
#undef OPTIMIZING_INTRINSICS_WITH_SPECIALIZED_HIR
      return true;
    default:
      return false;
  }
}

static inline bool IsValidIntrinsicAfterBuilder(Intrinsics intrinsic) {
  return !IsIntrinsicWithSpecializedHir(intrinsic) ||
         // FIXME: The inliner can currently create graphs with any of the intrinsics with HIR.
         // However, we are able to compensate for `StringCharAt` and `StringLength` in the
         // `HInstructionSimplifier`, so we're allowing these two intrinsics for now, preserving
         // the old behavior. Besides fixing the bug, we should also clean up the simplifier
         // and remove `SimplifyStringCharAt` and `SimplifyStringLength`. Bug: 319045458
         intrinsic == Intrinsics::kStringCharAt ||
         intrinsic == Intrinsics::kStringLength;
}

#define GENERIC_OPTIMIZATION(name, bit)                \
public:                                                \
void Set##name() { SetBit(k##name); }                  \
bool Get##name() const { return IsBitSet(k##name); }   \
private:                                               \
static constexpr size_t k##name = bit

class IntrinsicOptimizations : public ValueObject {
 public:
  explicit IntrinsicOptimizations(HInvoke* invoke)
      : value_(invoke->GetIntrinsicOptimizations()) {}
  explicit IntrinsicOptimizations(const HInvoke& invoke)
      : value_(invoke.GetIntrinsicOptimizations()) {}

  static constexpr int kNumberOfGenericOptimizations = 1;
  GENERIC_OPTIMIZATION(DoesNotNeedEnvironment, 0);

 protected:
  bool IsBitSet(uint32_t bit) const {
    DCHECK_LT(bit, sizeof(uint32_t) * kBitsPerByte);
    return (*value_ & (1 << bit)) != 0u;
  }

  void SetBit(uint32_t bit) {
    DCHECK_LT(bit, sizeof(uint32_t) * kBitsPerByte);
    *(const_cast<uint32_t* const>(value_)) |= (1 << bit);
  }

 private:
  const uint32_t* const value_;

  DISALLOW_COPY_AND_ASSIGN(IntrinsicOptimizations);
};

#undef GENERIC_OPTIMIZATION

#define INTRINSIC_OPTIMIZATION(name, bit)                             \
public:                                                               \
void Set##name() { SetBit(k##name); }                                 \
bool Get##name() const { return IsBitSet(k##name); }                  \
private:                                                              \
static constexpr size_t k##name = (bit) + kNumberOfGenericOptimizations

class StringEqualsOptimizations : public IntrinsicOptimizations {
 public:
  explicit StringEqualsOptimizations(HInvoke* invoke) : IntrinsicOptimizations(invoke) {}

  INTRINSIC_OPTIMIZATION(ArgumentNotNull, 0);
  INTRINSIC_OPTIMIZATION(ArgumentIsString, 1);

 private:
  DISALLOW_COPY_AND_ASSIGN(StringEqualsOptimizations);
};

class SystemArrayCopyOptimizations : public IntrinsicOptimizations {
 public:
  explicit SystemArrayCopyOptimizations(HInvoke* invoke) : IntrinsicOptimizations(invoke) {}

  INTRINSIC_OPTIMIZATION(SourceIsNotNull, 0);
  INTRINSIC_OPTIMIZATION(DestinationIsNotNull, 1);
  INTRINSIC_OPTIMIZATION(DestinationIsSource, 2);
  INTRINSIC_OPTIMIZATION(CountIsSourceLength, 3);
  INTRINSIC_OPTIMIZATION(CountIsDestinationLength, 4);
  INTRINSIC_OPTIMIZATION(DoesNotNeedTypeCheck, 5);
  INTRINSIC_OPTIMIZATION(DestinationIsTypedObjectArray, 6);
  INTRINSIC_OPTIMIZATION(DestinationIsNonPrimitiveArray, 7);
  INTRINSIC_OPTIMIZATION(DestinationIsPrimitiveArray, 8);
  INTRINSIC_OPTIMIZATION(SourceIsNonPrimitiveArray, 9);
  INTRINSIC_OPTIMIZATION(SourceIsPrimitiveArray, 10);
  INTRINSIC_OPTIMIZATION(SourcePositionIsDestinationPosition, 11);

 private:
  DISALLOW_COPY_AND_ASSIGN(SystemArrayCopyOptimizations);
};

class VarHandleOptimizations : public IntrinsicOptimizations {
 public:
  explicit VarHandleOptimizations(HInvoke* invoke) : IntrinsicOptimizations(invoke) {}

  INTRINSIC_OPTIMIZATION(DoNotIntrinsify, 0);  // One of the checks is statically known to fail.
  INTRINSIC_OPTIMIZATION(SkipObjectNullCheck, 1);  // Not applicable for static fields.

  // Use known `VarHandle` from the boot image. To apply this optimization, the following
  // `VarHandle` checks must pass based on static analysis:
  //   - `VarHandle` type check (must match the coordinate count),
  //   - access mode check,
  //   - var type check (including assignability for reference types),
  //   - object type check (except for static field VarHandles that do not take an object).
  // Note that the object null check is controlled by the above flag `SkipObjectNullCheck`
  // and arrays and byte array views (which always need a range check and sometimes also
  // array type check) are currently unsupported.
  INTRINSIC_OPTIMIZATION(UseKnownBootImageVarHandle, 2);
};

#undef INTRISIC_OPTIMIZATION

//
// Macros for use in the intrinsics code generators.
//

// Defines an unimplemented intrinsic: that is, a method call that is recognized as an
// intrinsic to exploit e.g. no side-effects or exceptions, but otherwise not handled
// by this architecture-specific intrinsics code generator. Eventually it is implemented
// as a true method call.
#define UNIMPLEMENTED_INTRINSIC(Arch, Name)                                              \
  void IntrinsicLocationsBuilder##Arch::Visit##Name([[maybe_unused]] HInvoke* invoke) {} \
  void IntrinsicCodeGenerator##Arch::Visit##Name([[maybe_unused]] HInvoke* invoke) {}

// Defines a list of unreached intrinsics: that is, method calls that are recognized as
// an intrinsic, and then always converted into HIR instructions before they reach any
// architecture-specific intrinsics code generator. This only applies to non-baseline
// compilation.
#define UNREACHABLE_INTRINSIC(Arch, Name)                                \
void IntrinsicLocationsBuilder ## Arch::Visit ## Name(HInvoke* invoke) { \
  if (Runtime::Current()->IsAotCompiler() &&                             \
      !codegen_->GetCompilerOptions().IsBaseline()) {                    \
    LOG(FATAL) << "Unreachable: intrinsic " << invoke->GetIntrinsic()    \
               << " should have been converted to HIR";                  \
  }                                                                      \
}                                                                        \
void IntrinsicCodeGenerator ## Arch::Visit ## Name(HInvoke* invoke) {    \
  LOG(FATAL) << "Unreachable: intrinsic " << invoke->GetIntrinsic()      \
             << " should have been converted to HIR";                    \
}
#define UNREACHABLE_INTRINSICS(Arch)                            \
UNREACHABLE_INTRINSIC(Arch, FloatFloatToIntBits)                \
UNREACHABLE_INTRINSIC(Arch, DoubleDoubleToLongBits)

template <typename IntrinsicLocationsBuilder, typename Codegenerator>
bool IsCallFreeIntrinsic(HInvoke* invoke, Codegenerator* codegen) {
  if (invoke->GetIntrinsic() != Intrinsics::kNone) {
    // This invoke may have intrinsic code generation defined. However, we must
    // now also determine if this code generation is truly there and call-free
    // (not unimplemented, no bail on instruction features, or call on slow path).
    // This is done by actually calling the locations builder on the instruction
    // and clearing out the locations once result is known. We assume this
    // call only has creating locations as side effects!
    // TODO: Avoid wasting Arena memory.
    IntrinsicLocationsBuilder builder(codegen);
    bool success = builder.TryDispatch(invoke) && !invoke->GetLocations()->CanCall();
    invoke->SetLocations(nullptr);
    return success;
  }
  return false;
}

// Insert a `Float.floatToRawIntBits()` or `Double.doubleToRawLongBits()` intrinsic for a
// given input. These fake calls are needed on arm and riscv64 to satisfy type consistency
// checks while passing certain FP args in core registers for direct @CriticalNative calls.
void InsertFpToIntegralIntrinsic(HInvokeStaticOrDirect* invoke, size_t input_index);

}  // namespace art

#endif  // ART_COMPILER_OPTIMIZING_INTRINSICS_H_
