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

#include "method_type-inl.h"

#include "class-alloc-inl.h"
#include "class_root-inl.h"
#include "handle_scope-inl.h"
#include "method_handles.h"
#include "obj_ptr-inl.h"
#include "object_array-alloc-inl.h"
#include "object_array-inl.h"
#include "well_known_classes.h"

namespace art HIDDEN {
namespace mirror {

namespace {

ObjPtr<ObjectArray<Class>> AllocatePTypesArray(Thread* self, int count)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ObjPtr<Class> class_array_type = GetClassRoot<mirror::ObjectArray<mirror::Class>>();
  return ObjectArray<Class>::Alloc(self, class_array_type, count);
}

}  // namespace

ObjPtr<MethodType> MethodType::Create(Thread* self,
                                      Handle<Class> return_type,
                                      Handle<ObjectArray<Class>> parameter_types) {
  ArtMethod* make_impl = WellKnownClasses::java_lang_invoke_MethodType_makeImpl;

  const bool is_trusted = true;
  ObjPtr<MethodType> mt = ObjPtr<MethodType>::DownCast(make_impl->InvokeStatic<'L', 'L', 'L', 'Z'>(
      self, return_type.Get(), parameter_types.Get(), is_trusted));

  if (self->IsExceptionPending()) {
    return nullptr;
  }

  return mt;
}

ObjPtr<MethodType> MethodType::Create(Thread* self, RawMethodType method_type) {
  Handle<mirror::Class> return_type = method_type.GetRTypeHandle();
  RawPTypesAccessor p_types(method_type);
  int32_t num_method_args = p_types.GetLength();

  // Create the argument types array.
  StackHandleScope<1u> hs(self);
  Handle<mirror::ObjectArray<mirror::Class>> method_params = hs.NewHandle(
      mirror::ObjectArray<mirror::Class>::Alloc(
          self, GetClassRoot<mirror::ObjectArray<mirror::Class>>(), num_method_args));
  if (method_params == nullptr) {
    DCHECK(self->IsExceptionPending());
    return nullptr;
  }

  for (int32_t i = 0; i != num_method_args; ++i) {
    method_params->Set(i, p_types.Get(i));
  }

  return Create(self, return_type, method_params);
}

ObjPtr<MethodType> MethodType::CloneWithoutLeadingParameter(Thread* self,
                                                            ObjPtr<MethodType> method_type) {
  StackHandleScope<3> hs(self);
  Handle<ObjectArray<Class>> src_ptypes = hs.NewHandle(method_type->GetPTypes());
  Handle<Class> dst_rtype = hs.NewHandle(method_type->GetRType());
  const int32_t dst_ptypes_count = method_type->GetNumberOfPTypes() - 1;
  Handle<ObjectArray<Class>> dst_ptypes = hs.NewHandle(AllocatePTypesArray(self, dst_ptypes_count));
  if (dst_ptypes.IsNull()) {
    return nullptr;
  }
  for (int32_t i = 0; i < dst_ptypes_count; ++i) {
    dst_ptypes->Set(i, src_ptypes->Get(i + 1));
  }
  return Create(self, dst_rtype, dst_ptypes);
}

ObjPtr<MethodType> MethodType::CollectTrailingArguments(Thread* self,
                                                        ObjPtr<MethodType> method_type,
                                                        ObjPtr<Class> collector_array_class,
                                                        int32_t start_index) {
  int32_t ptypes_length = method_type->GetNumberOfPTypes();
  if (start_index > ptypes_length) {
    return method_type;
  }

  StackHandleScope<4> hs(self);
  Handle<Class> collector_class = hs.NewHandle(collector_array_class);
  Handle<Class> dst_rtype = hs.NewHandle(method_type->GetRType());
  Handle<ObjectArray<Class>> src_ptypes = hs.NewHandle(method_type->GetPTypes());
  Handle<ObjectArray<Class>> dst_ptypes = hs.NewHandle(AllocatePTypesArray(self, start_index + 1));
  if (dst_ptypes.IsNull()) {
    return nullptr;
  }
  for (int32_t i = 0; i < start_index; ++i) {
    dst_ptypes->Set(i, src_ptypes->Get(i));
  }
  dst_ptypes->Set(start_index, collector_class.Get());
  return Create(self, dst_rtype, dst_ptypes);
}

template <typename MethodTypeType>
size_t NumberOfVRegsImpl(MethodTypeType method_type) REQUIRES_SHARED(Locks::mutator_lock_) {
  auto p_types = MethodType::GetPTypes(method_type);
  const int32_t p_types_length = p_types.GetLength();

  // Initialize |num_vregs| with number of parameters and only increment it for
  // types requiring a second vreg.
  size_t num_vregs = static_cast<size_t>(p_types_length);
  for (int32_t i = 0; i < p_types_length; ++i) {
    ObjPtr<Class> klass = p_types.Get(i);
    if (klass->IsPrimitiveLong() || klass->IsPrimitiveDouble()) {
      ++num_vregs;
    }
  }
  return num_vregs;
}

size_t MethodType::NumberOfVRegs() {
  return NumberOfVRegs(this);
}

size_t MethodType::NumberOfVRegs(ObjPtr<mirror::MethodType> method_type) {
  DCHECK(method_type != nullptr);
  return NumberOfVRegsImpl(method_type);
}

size_t MethodType::NumberOfVRegs(Handle<mirror::MethodType> method_type) {
  return NumberOfVRegs(method_type.Get());
}

size_t MethodType::NumberOfVRegs(RawMethodType method_type) {
  DCHECK(method_type.IsValid());
  return NumberOfVRegsImpl(method_type);
}

bool MethodType::IsExactMatch(ObjPtr<MethodType> target) {
  const ObjPtr<ObjectArray<Class>> p_types = GetPTypes();
  const int32_t params_length = p_types->GetLength();

  const ObjPtr<ObjectArray<Class>> target_p_types = target->GetPTypes();
  if (params_length != target_p_types->GetLength()) {
    return false;
  }
  for (int32_t i = 0; i < params_length; ++i) {
    if (p_types->GetWithoutChecks(i) != target_p_types->GetWithoutChecks(i)) {
      return false;
    }
  }
  return GetRType() == target->GetRType();
}

bool MethodType::IsConvertible(ObjPtr<MethodType> target) {
  const ObjPtr<ObjectArray<Class>> p_types = GetPTypes();
  const int32_t params_length = p_types->GetLength();

  const ObjPtr<ObjectArray<Class>> target_p_types = target->GetPTypes();
  if (params_length != target_p_types->GetLength()) {
    return false;
  }

  // Perform return check before invoking method handle otherwise side
  // effects from the invocation may be observable before
  // WrongMethodTypeException is raised.
  if (!IsReturnTypeConvertible(target->GetRType(), GetRType())) {
    return false;
  }

  for (int32_t i = 0; i < params_length; ++i) {
    if (!IsParameterTypeConvertible(p_types->GetWithoutChecks(i),
                                    target_p_types->GetWithoutChecks(i))) {
      return false;
    }
  }
  return true;
}

static bool IsParameterInPlaceConvertible(ObjPtr<Class> from, ObjPtr<Class> to)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  if (from == to) {
    return true;
  }

  if (from->IsPrimitive() != to->IsPrimitive()) {
    return false;  // No in-place conversion from place conversion for box/unboxing.
  }

  if (from->IsPrimitive()) {
    // `from` and `to` are both primitives. The supported in-place conversions use a 32-bit
    // interpreter representation and are a subset of permitted conversions for MethodHandles.
    // Conversions are documented in JLS 11 S5.1.2 "Widening Primitive Conversion".
    Primitive::Type src = from->GetPrimitiveType();
    Primitive::Type dst = to->GetPrimitiveType();
    switch (src) {
      case Primitive::Type::kPrimByte:
        return dst == Primitive::Type::kPrimShort || dst == Primitive::Type::kPrimInt;
      case Primitive::Type::kPrimChar:
        FALLTHROUGH_INTENDED;
      case Primitive::Type::kPrimShort:
        return dst == Primitive::Type::kPrimInt;
      default:
        return false;
    }
  }

  // `from` and `to` are both references, apply an assignability check.
  return to->IsAssignableFrom(from);
}

bool MethodType::IsInPlaceConvertible(ObjPtr<MethodType> target) {
  const ObjPtr<ObjectArray<Class>> ptypes = GetPTypes();
  const ObjPtr<ObjectArray<Class>> target_ptypes = target->GetPTypes();
  const int32_t ptypes_length = ptypes->GetLength();
  if (ptypes_length != target_ptypes->GetLength()) {
    return false;
  }

  for (int32_t i = 0; i < ptypes_length; ++i) {
    if (!IsParameterInPlaceConvertible(ptypes->GetWithoutChecks(i),
                                       target_ptypes->GetWithoutChecks(i))) {
      return false;
    }
  }

  return GetRType()->IsPrimitiveVoid() ||
         IsParameterInPlaceConvertible(target->GetRType(), GetRType());
}

template <typename MethodTypeType>
std::string PrettyDescriptorImpl(MethodTypeType method_type)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  auto p_types = MethodType::GetPTypes(method_type);
  ObjPtr<mirror::Class> r_type = MethodType::GetRType(method_type);

  std::ostringstream ss;
  ss << "(";

  const int32_t params_length = p_types.GetLength();
  for (int32_t i = 0; i < params_length; ++i) {
    ss << p_types.Get(i)->PrettyDescriptor();
    if (i != (params_length - 1)) {
      ss << ", ";
    }
  }

  ss << ")";
  ss << r_type->PrettyDescriptor();

  return ss.str();
}

std::string MethodType::PrettyDescriptor() {
  return PrettyDescriptor(this);
}

std::string MethodType::PrettyDescriptor(ObjPtr<mirror::MethodType> method_type) {
  DCHECK(method_type != nullptr);
  return PrettyDescriptorImpl(method_type);
}

std::string MethodType::PrettyDescriptor(Handle<MethodType> method_type) {
  return PrettyDescriptor(method_type.Get());
}

std::string MethodType::PrettyDescriptor(RawMethodType method_type) {
  DCHECK(method_type.IsValid());
  return PrettyDescriptorImpl(method_type);
}

}  // namespace mirror
}  // namespace art
