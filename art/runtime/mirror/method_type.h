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

#ifndef ART_RUNTIME_MIRROR_METHOD_TYPE_H_
#define ART_RUNTIME_MIRROR_METHOD_TYPE_H_

#include "base/macros.h"
#include "object_array.h"
#include "object.h"
#include "string.h"

namespace art HIDDEN {

struct MethodTypeOffsets;
class VariableSizedHandleScope;

namespace mirror {

// We use a wrapped `VariableSizedHandleScope` as a raw method type without allocating a managed
// object.  It must contain the return type followed by argument types and no other handles.
// The data is filled by calling `SetRType()` followed by `AddPType()` for each argument.
class RawMethodType {
 public:
  explicit RawMethodType(VariableSizedHandleScope* hs);

  bool IsValid() const;

  void SetRType(ObjPtr<mirror::Class> rtype) REQUIRES_SHARED(Locks::mutator_lock_);
  void AddPType(ObjPtr<mirror::Class> ptype) REQUIRES_SHARED(Locks::mutator_lock_);

  int32_t GetNumberOfPTypes() const REQUIRES_SHARED(Locks::mutator_lock_);
  ObjPtr<mirror::Class> GetPType(int32_t i) const REQUIRES_SHARED(Locks::mutator_lock_);
  ObjPtr<mirror::Class> GetRType() const REQUIRES_SHARED(Locks::mutator_lock_);
  Handle<mirror::Class> GetRTypeHandle() const REQUIRES_SHARED(Locks::mutator_lock_);

 private:
  VariableSizedHandleScope* hs_;
};

// C++ mirror of java.lang.invoke.MethodType
class MANAGED MethodType : public Object {
 public:
  MIRROR_CLASS("Ljava/lang/invoke/MethodType;");

  EXPORT static ObjPtr<MethodType> Create(Thread* self,
                                          Handle<Class> return_type,
                                          Handle<ObjectArray<Class>> param_types)
      REQUIRES_SHARED(Locks::mutator_lock_) REQUIRES(!Roles::uninterruptible_);

  // Create a `MethodType` from a `RawMethodType`.
  static ObjPtr<MethodType> Create(Thread* self, RawMethodType method_type)
      REQUIRES_SHARED(Locks::mutator_lock_) REQUIRES(!Roles::uninterruptible_);

  static ObjPtr<MethodType> CloneWithoutLeadingParameter(Thread* self,
                                                         ObjPtr<MethodType> method_type)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Collects trailing parameter types into an array. Assumes caller
  // has checked trailing arguments are all of the same type.
  static ObjPtr<MethodType> CollectTrailingArguments(Thread* self,
                                                     ObjPtr<MethodType> method_type,
                                                     ObjPtr<Class> collector_array_class,
                                                     int32_t start_index)
      REQUIRES_SHARED(Locks::mutator_lock_);

  ObjPtr<ObjectArray<Class>> GetPTypes() REQUIRES_SHARED(Locks::mutator_lock_);

  int GetNumberOfPTypes() REQUIRES_SHARED(Locks::mutator_lock_);

  // Number of virtual registers required to hold the parameters for
  // this method type.
  size_t NumberOfVRegs() REQUIRES_SHARED(Locks::mutator_lock_);

  ObjPtr<Class> GetRType() REQUIRES_SHARED(Locks::mutator_lock_);

  // Returns true iff. |this| is an exact match for method type |target|, i.e
  // iff. they have the same return types and parameter types.
  bool IsExactMatch(ObjPtr<MethodType> target) REQUIRES_SHARED(Locks::mutator_lock_);

  // Returns true iff. |this| can be converted to match |target| method type, i.e
  // iff. they have convertible return types and parameter types.
  bool IsConvertible(ObjPtr<MethodType> target) REQUIRES_SHARED(Locks::mutator_lock_);

  // Returns true iff. |this| can be converted to match |target| method type within the
  // current frame of the current MethodType. This limits conversions to assignability check
  // for references and between scalar 32-bit types.
  bool IsInPlaceConvertible(ObjPtr<MethodType> target) REQUIRES_SHARED(Locks::mutator_lock_);

  // Returns the pretty descriptor for this method type, suitable for display in
  // exception messages and the like.
  std::string PrettyDescriptor() REQUIRES_SHARED(Locks::mutator_lock_);

  // The `PTypesType` is either `ObjPtr<>` or `Handle<>`.
  template <typename PTypesType>
  class PTypesAccessor {
   public:
    explicit PTypesAccessor(PTypesType p_types) REQUIRES_SHARED(Locks::mutator_lock_);

    int32_t GetLength() const REQUIRES_SHARED(Locks::mutator_lock_);
    ObjPtr<mirror::Class> Get(int32_t i) const REQUIRES_SHARED(Locks::mutator_lock_);

   private:
    static_assert(std::is_same_v<PTypesType, ObjPtr<ObjectArray<Class>>> ||
                  std::is_same_v<PTypesType, Handle<ObjectArray<Class>>>);

    const PTypesType p_types_;
  };

  using ObjPtrPTypesAccessor = PTypesAccessor<ObjPtr<ObjectArray<Class>>>;
  using HandlePTypesAccessor = PTypesAccessor<Handle<ObjectArray<Class>>>;

  class RawPTypesAccessor {
   public:
    explicit RawPTypesAccessor(RawMethodType method_type);

    int32_t GetLength() const REQUIRES_SHARED(Locks::mutator_lock_);
    ObjPtr<mirror::Class> Get(int32_t i) const REQUIRES_SHARED(Locks::mutator_lock_);

   private:
    RawMethodType method_type_;
  };

  template <typename HandleScopeType>
  static HandlePTypesAccessor NewHandlePTypes(Handle<MethodType> method_type, HandleScopeType* hs)
      REQUIRES_SHARED(Locks::mutator_lock_);
  template <typename HandleScopeType>
  static RawPTypesAccessor NewHandlePTypes(RawMethodType method_type, HandleScopeType* hs)
      REQUIRES_SHARED(Locks::mutator_lock_);

  static ObjPtrPTypesAccessor GetPTypes(ObjPtr<MethodType> method_type)
      REQUIRES_SHARED(Locks::mutator_lock_);
  static ObjPtrPTypesAccessor GetPTypes(Handle<MethodType> method_type)
      REQUIRES_SHARED(Locks::mutator_lock_);
  static RawPTypesAccessor GetPTypes(RawMethodType method_type)
      REQUIRES_SHARED(Locks::mutator_lock_);

  static ObjPtr<mirror::Class> GetRType(ObjPtr<MethodType> method_type)
      REQUIRES_SHARED(Locks::mutator_lock_);
  static ObjPtr<mirror::Class> GetRType(Handle<MethodType> method_type)
      REQUIRES_SHARED(Locks::mutator_lock_);
  static ObjPtr<mirror::Class> GetRType(RawMethodType method_type)
      REQUIRES_SHARED(Locks::mutator_lock_);

  static size_t NumberOfVRegs(ObjPtr<mirror::MethodType> method_type)
      REQUIRES_SHARED(Locks::mutator_lock_);
  static size_t NumberOfVRegs(Handle<mirror::MethodType> method_type)
      REQUIRES_SHARED(Locks::mutator_lock_);
  static size_t NumberOfVRegs(RawMethodType method_type)
      REQUIRES_SHARED(Locks::mutator_lock_);

  static std::string PrettyDescriptor(ObjPtr<MethodType> method_type)
      REQUIRES_SHARED(Locks::mutator_lock_);
  static std::string PrettyDescriptor(Handle<MethodType> method_type)
      REQUIRES_SHARED(Locks::mutator_lock_);
  static std::string PrettyDescriptor(RawMethodType method_type)
      REQUIRES_SHARED(Locks::mutator_lock_);

 private:
  static MemberOffset FormOffset() {
    return MemberOffset(OFFSETOF_MEMBER(MethodType, form_));
  }

  static MemberOffset MethodDescriptorOffset() {
    return MemberOffset(OFFSETOF_MEMBER(MethodType, method_descriptor_));
  }

  static MemberOffset PTypesOffset() {
    return MemberOffset(OFFSETOF_MEMBER(MethodType, p_types_));
  }

  static MemberOffset RTypeOffset() {
    return MemberOffset(OFFSETOF_MEMBER(MethodType, r_type_));
  }

  static MemberOffset WrapAltOffset() {
    return MemberOffset(OFFSETOF_MEMBER(MethodType, wrap_alt_));
  }

  HeapReference<Object> form_;  // Unused in the runtime
  HeapReference<String> method_descriptor_;  // Unused in the runtime
  HeapReference<ObjectArray<Class>> p_types_;
  HeapReference<Class> r_type_;
  HeapReference<Object> wrap_alt_;  // Unused in the runtime

  friend struct art::MethodTypeOffsets;  // for verifying offset information
  DISALLOW_IMPLICIT_CONSTRUCTORS(MethodType);
};

}  // namespace mirror
}  // namespace art

#endif  // ART_RUNTIME_MIRROR_METHOD_TYPE_H_
