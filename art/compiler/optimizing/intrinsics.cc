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

#include "intrinsics.h"

#include "art_field-inl.h"
#include "art_method-inl.h"
#include "base/utils.h"
#include "class_linker.h"
#include "class_root-inl.h"
#include "code_generator.h"
#include "dex/invoke_type.h"
#include "driver/compiler_options.h"
#include "gc/space/image_space.h"
#include "intrinsic_objects.h"
#include "intrinsics_list.h"
#include "nodes.h"
#include "oat/image-inl.h"
#include "obj_ptr-inl.h"
#include "scoped_thread_state_change-inl.h"
#include "thread-current-inl.h"
#include "well_known_classes-inl.h"

namespace art HIDDEN {

std::ostream& operator<<(std::ostream& os, const Intrinsics& intrinsic) {
  switch (intrinsic) {
    case Intrinsics::kNone:
      os << "None";
      break;
#define OPTIMIZING_INTRINSICS(Name, IsStatic, NeedsEnvironmentOrCache, SideEffects, Exceptions, ...) \
    case Intrinsics::k ## Name: \
      os << # Name; \
      break;
      ART_INTRINSICS_LIST(OPTIMIZING_INTRINSICS)
#undef OPTIMIZING_INTRINSICS
  }
  return os;
}

static ObjPtr<mirror::ObjectArray<mirror::Object>> GetBootImageLiveObjects()
    REQUIRES_SHARED(Locks::mutator_lock_) {
  gc::Heap* heap = Runtime::Current()->GetHeap();
  const std::vector<gc::space::ImageSpace*>& boot_image_spaces = heap->GetBootImageSpaces();
  DCHECK(!boot_image_spaces.empty());
  const ImageHeader& main_header = boot_image_spaces[0]->GetImageHeader();
  ObjPtr<mirror::ObjectArray<mirror::Object>> boot_image_live_objects =
      ObjPtr<mirror::ObjectArray<mirror::Object>>::DownCast(
          main_header.GetImageRoot<kWithoutReadBarrier>(ImageHeader::kBootImageLiveObjects));
  DCHECK(boot_image_live_objects != nullptr);
  DCHECK(heap->ObjectIsInBootImageSpace(boot_image_live_objects));
  return boot_image_live_objects;
}

static bool CanReferenceBootImageObjects(HInvoke* invoke, const CompilerOptions& compiler_options) {
  // Piggyback on the method load kind to determine whether we can use PC-relative addressing
  // for AOT. This should cover both the testing config (non-PIC boot image) and codegens that
  // reject PC-relative load kinds and fall back to the runtime call.
  if (compiler_options.IsAotCompiler() &&
      !invoke->AsInvokeStaticOrDirect()->HasPcRelativeMethodLoadKind()) {
    return false;
  }
  if (!compiler_options.IsBootImage() &&
      Runtime::Current()->GetHeap()->GetBootImageSpaces().empty()) {
    return false;  // Running without boot image, cannot use required boot image objects.
  }
  return true;
}

void IntrinsicVisitor::ComputeValueOfLocations(HInvoke* invoke,
                                               CodeGenerator* codegen,
                                               int32_t low,
                                               int32_t length,
                                               Location return_location,
                                               Location first_argument_location) {
  // The intrinsic will call if it needs to allocate a boxed object.
  LocationSummary::CallKind call_kind = LocationSummary::kCallOnMainOnly;
  const CompilerOptions& compiler_options = codegen->GetCompilerOptions();
  if (!CanReferenceBootImageObjects(invoke, compiler_options)) {
    return;
  }
  HInstruction* const input = invoke->InputAt(0);
  if (input->IsIntConstant()) {
    int32_t value = input->AsIntConstant()->GetValue();
    if (static_cast<uint32_t>(value) - static_cast<uint32_t>(low) < static_cast<uint32_t>(length)) {
      // No call, we shall use direct pointer to the boxed object.
      call_kind = LocationSummary::kNoCall;
    }
  }

  ArenaAllocator* allocator = codegen->GetGraph()->GetAllocator();
  LocationSummary* locations = new (allocator) LocationSummary(invoke, call_kind, kIntrinsified);
  if (call_kind == LocationSummary::kCallOnMainOnly) {
    locations->SetInAt(0, Location::RegisterOrConstant(input));
    locations->AddTemp(first_argument_location);
    locations->SetOut(return_location);
  } else {
    locations->SetInAt(0, Location::ConstantLocation(input));
    locations->SetOut(Location::RequiresRegister());
  }
}

inline IntrinsicVisitor::ValueOfInfo::ValueOfInfo()
    : value_offset(0),
      low(0),
      length(0u),
      value_boot_image_reference(kInvalidReference) {}

IntrinsicVisitor::ValueOfInfo IntrinsicVisitor::ComputeValueOfInfo(
    HInvoke* invoke,
    const CompilerOptions& compiler_options,
    ArtField* value_field,
    int32_t low,
    int32_t length,
    size_t base) {
  ValueOfInfo info;
  info.low = low;
  info.length = length;
  info.value_offset = value_field->GetOffset().Uint32Value();
  if (compiler_options.IsBootImage()) {
    if (invoke->InputAt(0)->IsIntConstant()) {
      int32_t input_value = invoke->InputAt(0)->AsIntConstant()->GetValue();
      uint32_t index = static_cast<uint32_t>(input_value) - static_cast<uint32_t>(info.low);
      if (index < static_cast<uint32_t>(info.length)) {
        info.value_boot_image_reference = IntrinsicObjects::EncodePatch(
            IntrinsicObjects::PatchType::kValueOfObject, index + base);
      } else {
        // Not in the cache.
        info.value_boot_image_reference = ValueOfInfo::kInvalidReference;
      }
    } else {
      info.array_data_boot_image_reference =
          IntrinsicObjects::EncodePatch(IntrinsicObjects::PatchType::kValueOfArray, base);
    }
  } else {
    ScopedObjectAccess soa(Thread::Current());
    ObjPtr<mirror::ObjectArray<mirror::Object>> boot_image_live_objects = GetBootImageLiveObjects();

    if (invoke->InputAt(0)->IsIntConstant()) {
      int32_t input_value = invoke->InputAt(0)->AsIntConstant()->GetValue();
      uint32_t index = static_cast<uint32_t>(input_value) - static_cast<uint32_t>(info.low);
      if (index < static_cast<uint32_t>(info.length)) {
        ObjPtr<mirror::Object> object =
            IntrinsicObjects::GetValueOfObject(boot_image_live_objects, base, index);
        info.value_boot_image_reference = CodeGenerator::GetBootImageOffset(object);
      } else {
        // Not in the cache.
        info.value_boot_image_reference = ValueOfInfo::kInvalidReference;
      }
    } else {
      info.array_data_boot_image_reference =
          CodeGenerator::GetBootImageOffset(boot_image_live_objects) +
          IntrinsicObjects::GetValueOfArrayDataOffset(
              boot_image_live_objects, base).Uint32Value();
    }
  }

  return info;
}

MemberOffset IntrinsicVisitor::GetReferenceDisableIntrinsicOffset() {
  ScopedObjectAccess soa(Thread::Current());
  // The "disableIntrinsic" is the first static field.
  ArtField* field = GetClassRoot<mirror::Reference>()->GetStaticField(0);
  DCHECK_STREQ(field->GetName(), "disableIntrinsic");
  return field->GetOffset();
}

MemberOffset IntrinsicVisitor::GetReferenceSlowPathEnabledOffset() {
  ScopedObjectAccess soa(Thread::Current());
  // The "slowPathEnabled" is the second static field.
  ArtField* field = GetClassRoot<mirror::Reference>()->GetStaticField(1);
  DCHECK_STREQ(field->GetName(), "slowPathEnabled");
  return field->GetOffset();
}

void IntrinsicVisitor::CreateReferenceGetReferentLocations(HInvoke* invoke,
                                                           CodeGenerator* codegen) {
  if (!CanReferenceBootImageObjects(invoke, codegen->GetCompilerOptions())) {
    return;
  }

  ArenaAllocator* allocator = codegen->GetGraph()->GetAllocator();
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kCallOnSlowPath, kIntrinsified);
  locations->SetInAt(0, Location::RequiresRegister());
  locations->SetOut(Location::RequiresRegister());
}

void IntrinsicVisitor::CreateReferenceRefersToLocations(HInvoke* invoke, CodeGenerator* codegen) {
  if (codegen->EmitNonBakerReadBarrier()) {
    // Unimplemented for non-Baker read barrier.
    return;
  }

  ArenaAllocator* allocator = invoke->GetBlock()->GetGraph()->GetAllocator();
  LocationSummary* locations =
      new (allocator) LocationSummary(invoke, LocationSummary::kCallOnSlowPath, kIntrinsified);
  locations->SetInAt(0, Location::RequiresRegister());
  locations->SetInAt(1, Location::RequiresRegister());
  locations->SetOut(Location::RequiresRegister());
}

void IntrinsicVisitor::AssertNonMovableStringClass() {
  if (kIsDebugBuild) {
    ScopedObjectAccess soa(Thread::Current());
    ObjPtr<mirror::Class> string_class = GetClassRoot<mirror::String>();
    CHECK(!art::Runtime::Current()->GetHeap()->IsMovableObject(string_class));
  }
}

void InsertFpToIntegralIntrinsic(HInvokeStaticOrDirect* invoke, size_t input_index) {
  DCHECK_EQ(invoke->GetCodePtrLocation(), CodePtrLocation::kCallCriticalNative);
  DCHECK(!invoke->GetBlock()->GetGraph()->IsDebuggable())
      << "Unexpected direct @CriticalNative call in a debuggable graph!";
  DCHECK_LT(input_index, invoke->GetNumberOfArguments());
  HInstruction* input = invoke->InputAt(input_index);
  DataType::Type input_type = input->GetType();
  DCHECK(DataType::IsFloatingPointType(input_type));
  bool is_double = (input_type == DataType::Type::kFloat64);
  DataType::Type converted_type = is_double ? DataType::Type::kInt64 : DataType::Type::kInt32;
  ArtMethod* resolved_method = is_double
      ? WellKnownClasses::java_lang_Double_doubleToRawLongBits
      : WellKnownClasses::java_lang_Float_floatToRawIntBits;
  DCHECK(resolved_method != nullptr);
  DCHECK(resolved_method->IsIntrinsic());
  MethodReference target_method(nullptr, 0);
  {
    ScopedObjectAccess soa(Thread::Current());
    target_method =
        MethodReference(resolved_method->GetDexFile(), resolved_method->GetDexMethodIndex());
  }
  // Use arbitrary dispatch info that does not require the method argument.
  HInvokeStaticOrDirect::DispatchInfo dispatch_info = {
      MethodLoadKind::kBssEntry,
      CodePtrLocation::kCallArtMethod,
      /*method_load_data=*/ 0u
  };
  HBasicBlock* block = invoke->GetBlock();
  ArenaAllocator* allocator = block->GetGraph()->GetAllocator();
  HInvokeStaticOrDirect* new_input = new (allocator) HInvokeStaticOrDirect(
      allocator,
      /*number_of_arguments=*/ 1u,
      converted_type,
      invoke->GetDexPc(),
      /*method_reference=*/ MethodReference(nullptr, dex::kDexNoIndex),
      resolved_method,
      dispatch_info,
      kStatic,
      target_method,
      HInvokeStaticOrDirect::ClinitCheckRequirement::kNone,
      /*enable_intrinsic_opt=*/ true);
  // The intrinsic has no side effects and does not need the environment.
  new_input->SetSideEffects(SideEffects::None());
  IntrinsicOptimizations opt(new_input);
  opt.SetDoesNotNeedEnvironment();
  new_input->SetRawInputAt(0u, input);
  block->InsertInstructionBefore(new_input, invoke);
  invoke->ReplaceInput(new_input, input_index);
}

}  // namespace art
