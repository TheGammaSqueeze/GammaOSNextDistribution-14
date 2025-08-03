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

#ifndef ART_RUNTIME_ART_METHOD_INL_H_
#define ART_RUNTIME_ART_METHOD_INL_H_

#include "art_method.h"

#include "base/callee_save_type.h"
#include "class_linker-inl.h"
#include "common_throws.h"
#include "dex/code_item_accessors-inl.h"
#include "dex/dex_file-inl.h"
#include "dex/dex_file_annotations.h"
#include "dex/dex_file_types.h"
#include "dex/invoke_type.h"
#include "dex/primitive.h"
#include "dex/signature.h"
#include "gc_root-inl.h"
#include "imtable-inl.h"
#include "jit/jit_options.h"
#include "mirror/class-inl.h"
#include "mirror/dex_cache-inl.h"
#include "mirror/object-inl.h"
#include "mirror/object_array.h"
#include "mirror/string.h"
#include "obj_ptr-inl.h"
#include "quick/quick_method_frame_info.h"
#include "read_barrier-inl.h"
#include "runtime-inl.h"
#include "thread-current-inl.h"

namespace art HIDDEN {

namespace detail {

template <> struct ShortyTraits<'V'> {
  using Type = void;
  static Type Get([[maybe_unused]] const JValue& value) {}
  // `kVRegCount` and `Set()` are not defined.
};

template <> struct ShortyTraits<'Z'> {
  // Despite using `uint8_t` for `boolean` in `JValue`, we shall use `bool` here.
  using Type = bool;
  static Type Get(const JValue& value) { return value.GetZ() != 0u; }
  static constexpr size_t kVRegCount = 1u;
  static void Set(uint32_t* args, Type value) { args[0] = static_cast<uint32_t>(value ? 1u : 0u); }
};

template <> struct ShortyTraits<'B'> {
  using Type = int8_t;
  static Type Get(const JValue& value) { return value.GetB(); }
  static constexpr size_t kVRegCount = 1u;
  static void Set(uint32_t* args, Type value) { args[0] = static_cast<uint32_t>(value); }
};

template <> struct ShortyTraits<'C'> {
  using Type = uint16_t;
  static Type Get(const JValue& value) { return value.GetC(); }
  static constexpr size_t kVRegCount = 1u;
  static void Set(uint32_t* args, Type value) { args[0] = static_cast<uint32_t>(value); }
};

template <> struct ShortyTraits<'S'> {
  using Type = int16_t;
  static Type Get(const JValue& value) { return value.GetS(); }
  static constexpr size_t kVRegCount = 1u;
  static void Set(uint32_t* args, Type value) { args[0] = static_cast<uint32_t>(value); }
};

template <> struct ShortyTraits<'I'> {
  using Type = int32_t;
  static Type Get(const JValue& value) { return value.GetI(); }
  static constexpr size_t kVRegCount = 1u;
  static void Set(uint32_t* args, Type value) { args[0] = static_cast<uint32_t>(value); }
};

template <> struct ShortyTraits<'J'> {
  using Type = int64_t;
  static Type Get(const JValue& value) { return value.GetJ(); }
  static constexpr size_t kVRegCount = 2u;
  static void Set(uint32_t* args, Type value) {
    // Little-endian representation.
    args[0] = static_cast<uint32_t>(value);
    args[1] = static_cast<uint32_t>(static_cast<uint64_t>(value) >> 32);
  }
};

template <> struct ShortyTraits<'F'> {
  using Type = float;
  static Type Get(const JValue& value) { return value.GetF(); }
  static constexpr size_t kVRegCount = 1u;
  static void Set(uint32_t* args, Type value) { args[0] = bit_cast<uint32_t>(value); }
};

template <> struct ShortyTraits<'D'> {
  using Type = double;
  static Type Get(const JValue& value) { return value.GetD(); }
  static constexpr size_t kVRegCount = 2u;
  static void Set(uint32_t* args, Type value) {
    // Little-endian representation.
    uint64_t v = bit_cast<uint64_t>(value);
    args[0] = static_cast<uint32_t>(v);
    args[1] = static_cast<uint32_t>(v >> 32);
  }
};

template <> struct ShortyTraits<'L'> {
  using Type = ObjPtr<mirror::Object>;
  static Type Get(const JValue& value) REQUIRES_SHARED(Locks::mutator_lock_) {
      return value.GetL();
  }
  static constexpr size_t kVRegCount = 1u;
  static void Set(uint32_t* args, Type value) REQUIRES_SHARED(Locks::mutator_lock_) {
    args[0] = StackReference<mirror::Object>::FromMirrorPtr(value.Ptr()).AsVRegValue();
  }
};

template <char... Shorty>
constexpr auto MaterializeShorty() {
  constexpr size_t kSize = std::size({Shorty...}) + 1u;
  return std::array<char, kSize>{Shorty..., '\0'};
}

template <char... ArgType>
constexpr size_t NumberOfVRegs() {
  constexpr size_t kArgVRegCount[] = {
    ShortyTraits<ArgType>::kVRegCount...
  };
  size_t sum = 0u;
  for (size_t count : kArgVRegCount) {
    sum += count;
  }
  return sum;
}

template <char... ArgType>
inline ALWAYS_INLINE void FillVRegs([[maybe_unused]] uint32_t* vregs,
                                    [[maybe_unused]] typename ShortyTraits<ArgType>::Type... args)
    REQUIRES_SHARED(Locks::mutator_lock_) {}

template <char FirstArgType, char... ArgType>
inline ALWAYS_INLINE void FillVRegs(uint32_t* vregs,
                                    typename ShortyTraits<FirstArgType>::Type first_arg,
                                    typename ShortyTraits<ArgType>::Type... args)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ShortyTraits<FirstArgType>::Set(vregs, first_arg);
  FillVRegs<ArgType...>(vregs + ShortyTraits<FirstArgType>::kVRegCount, args...);
}

template <char... ArgType>
inline ALWAYS_INLINE auto MaterializeVRegs(typename ShortyTraits<ArgType>::Type... args)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  constexpr size_t kNumVRegs = NumberOfVRegs<ArgType...>();
  std::array<uint32_t, kNumVRegs> vregs;
  FillVRegs<ArgType...>(vregs.data(), args...);
  return vregs;
}

}  // namespace detail

template <char ReturnType, char... ArgType>
inline typename detail::ShortyTraits<ReturnType>::Type
ArtMethod::InvokeStatic(Thread* self, typename detail::ShortyTraits<ArgType>::Type... args) {
  DCHECK(IsStatic());
  DCHECK(GetDeclaringClass()->IsInitialized());  // Used only for initialized well-known classes.
  JValue result;
  constexpr auto shorty = detail::MaterializeShorty<ReturnType, ArgType...>();
  auto vregs = detail::MaterializeVRegs<ArgType...>(args...);
  Invoke(self,
         vregs.empty() ? nullptr : vregs.data(),
         vregs.size() * sizeof(typename decltype(vregs)::value_type),
         &result,
         shorty.data());
  return detail::ShortyTraits<ReturnType>::Get(result);
}

template <char ReturnType, char... ArgType>
typename detail::ShortyTraits<ReturnType>::Type
ArtMethod::InvokeInstance(Thread* self,
                          ObjPtr<mirror::Object> receiver,
                          typename detail::ShortyTraits<ArgType>::Type... args) {
  DCHECK(!GetDeclaringClass()->IsInterface());
  DCHECK(!IsStatic());
  JValue result;
  constexpr auto shorty = detail::MaterializeShorty<ReturnType, ArgType...>();
  auto vregs = detail::MaterializeVRegs<'L', ArgType...>(receiver, args...);
  Invoke(self,
         vregs.data(),
         vregs.size() * sizeof(typename decltype(vregs)::value_type),
         &result,
         shorty.data());
  return detail::ShortyTraits<ReturnType>::Get(result);
}

template <char ReturnType, char... ArgType>
typename detail::ShortyTraits<ReturnType>::Type
ArtMethod::InvokeFinal(Thread* self,
                       ObjPtr<mirror::Object> receiver,
                       typename detail::ShortyTraits<ArgType>::Type... args) {
  DCHECK(!GetDeclaringClass()->IsInterface());
  DCHECK(!IsStatic());
  DCHECK(IsFinal() || GetDeclaringClass()->IsFinal());
  DCHECK(receiver != nullptr);
  return InvokeInstance<ReturnType, ArgType...>(self, receiver, args...);
}

template <char ReturnType, char... ArgType>
typename detail::ShortyTraits<ReturnType>::Type
ArtMethod::InvokeVirtual(Thread* self,
                         ObjPtr<mirror::Object> receiver,
                         typename detail::ShortyTraits<ArgType>::Type... args) {
  DCHECK(!GetDeclaringClass()->IsInterface());
  DCHECK(!IsStatic());
  DCHECK(!IsFinal());
  DCHECK(receiver != nullptr);
  ArtMethod* target_method =
      receiver->GetClass()->FindVirtualMethodForVirtual(this, kRuntimePointerSize);
  DCHECK(target_method != nullptr);
  return target_method->InvokeInstance<ReturnType, ArgType...>(self, receiver, args...);
}

template <char ReturnType, char... ArgType>
typename detail::ShortyTraits<ReturnType>::Type
ArtMethod::InvokeInterface(Thread* self,
                           ObjPtr<mirror::Object> receiver,
                           typename detail::ShortyTraits<ArgType>::Type... args) {
  DCHECK(GetDeclaringClass()->IsInterface());
  DCHECK(!IsStatic());
  DCHECK(receiver != nullptr);
  ArtMethod* target_method =
      receiver->GetClass()->FindVirtualMethodForInterface(this, kRuntimePointerSize);
  DCHECK(target_method != nullptr);
  return target_method->InvokeInstance<ReturnType, ArgType...>(self, receiver, args...);
}

template <ReadBarrierOption kReadBarrierOption>
inline ObjPtr<mirror::Class> ArtMethod::GetDeclaringClassUnchecked() {
  GcRootSource gc_root_source(this);
  return declaring_class_.Read<kReadBarrierOption>(&gc_root_source);
}

template <ReadBarrierOption kReadBarrierOption>
inline ObjPtr<mirror::Class> ArtMethod::GetDeclaringClass() {
  ObjPtr<mirror::Class> result = GetDeclaringClassUnchecked<kReadBarrierOption>();
  if (kIsDebugBuild) {
    if (!IsRuntimeMethod()) {
      CHECK(result != nullptr) << this;
    } else {
      CHECK(result == nullptr) << this;
    }
  }
  return result;
}

inline void ArtMethod::SetDeclaringClass(ObjPtr<mirror::Class> new_declaring_class) {
  declaring_class_ = GcRoot<mirror::Class>(new_declaring_class);
}

inline bool ArtMethod::CASDeclaringClass(ObjPtr<mirror::Class> expected_class,
                                         ObjPtr<mirror::Class> desired_class) {
  GcRoot<mirror::Class> expected_root(expected_class);
  GcRoot<mirror::Class> desired_root(desired_class);
  auto atomic_root_class = reinterpret_cast<Atomic<GcRoot<mirror::Class>>*>(&declaring_class_);
  return atomic_root_class->CompareAndSetStrongSequentiallyConsistent(expected_root, desired_root);
}

inline uint16_t ArtMethod::GetMethodIndex() {
  DCHECK(IsRuntimeMethod() || GetDeclaringClass()->IsResolved());
  return method_index_;
}

inline uint16_t ArtMethod::GetMethodIndexDuringLinking() {
  return method_index_;
}

inline ObjPtr<mirror::Class> ArtMethod::LookupResolvedClassFromTypeIndex(dex::TypeIndex type_idx) {
  ScopedAssertNoThreadSuspension ants(__FUNCTION__);
  ObjPtr<mirror::Class> type =
      Runtime::Current()->GetClassLinker()->LookupResolvedType(type_idx, this);
  DCHECK(!Thread::Current()->IsExceptionPending());
  return type;
}

inline ObjPtr<mirror::Class> ArtMethod::ResolveClassFromTypeIndex(dex::TypeIndex type_idx) {
  ObjPtr<mirror::Class> type = Runtime::Current()->GetClassLinker()->ResolveType(type_idx, this);
  DCHECK_EQ(type == nullptr, Thread::Current()->IsExceptionPending());
  return type;
}

inline bool ArtMethod::IsStringConstructor() {
  uint32_t access_flags = GetAccessFlags();
  DCHECK(!IsClassInitializer(access_flags));
  return IsConstructor(access_flags) &&
         // No read barrier needed for reading a constant reference only to read
         // a constant string class flag. See `ReadBarrierOption`.
         GetDeclaringClass<kWithoutReadBarrier>()->IsStringClass();
}

inline bool ArtMethod::IsOverridableByDefaultMethod() {
  // It is safe to avoid the read barrier here since the constant interface flag
  // in the `Class` object is stored before creating the `ArtMethod` and storing
  // the declaring class reference. See `ReadBarrierOption`.
  return GetDeclaringClass<kWithoutReadBarrier>()->IsInterface();
}

inline bool ArtMethod::CheckIncompatibleClassChange(InvokeType type) {
  switch (type) {
    case kStatic:
      return !IsStatic();
    case kDirect:
      return !IsDirect() || IsStatic();
    case kVirtual: {
      // We have an error if we are direct or a non-copied (i.e. not part of a real class) interface
      // method.
      ObjPtr<mirror::Class> methods_class = GetDeclaringClass();
      return IsDirect() || (methods_class->IsInterface() && !IsCopied());
    }
    case kSuper:
      // Constructors and static methods are called with invoke-direct.
      return IsConstructor() || IsStatic();
    case kInterface: {
      ObjPtr<mirror::Class> methods_class = GetDeclaringClass();
      return IsDirect() || !(methods_class->IsInterface() || methods_class->IsObjectClass());
    }
    case kPolymorphic:
      return !IsSignaturePolymorphic();
    default:
      LOG(FATAL) << "Unreachable - invocation type: " << type;
      UNREACHABLE();
  }
}

inline bool ArtMethod::IsCalleeSaveMethod() {
  if (!IsRuntimeMethod()) {
    return false;
  }
  Runtime* runtime = Runtime::Current();
  bool result = false;
  for (uint32_t i = 0; i < static_cast<uint32_t>(CalleeSaveType::kLastCalleeSaveType); i++) {
    if (this == runtime->GetCalleeSaveMethod(CalleeSaveType(i))) {
      result = true;
      break;
    }
  }
  return result;
}

inline bool ArtMethod::IsResolutionMethod() {
  bool result = this == Runtime::Current()->GetResolutionMethod();
  // Check that if we do think it is phony it looks like the resolution method.
  DCHECK_IMPLIES(result, IsRuntimeMethod());
  return result;
}

inline bool ArtMethod::IsImtUnimplementedMethod() {
  bool result = this == Runtime::Current()->GetImtUnimplementedMethod();
  // Check that if we do think it is phony it looks like the imt unimplemented method.
  DCHECK_IMPLIES(result, IsRuntimeMethod());
  return result;
}

inline const DexFile* ArtMethod::GetDexFile() {
  // It is safe to avoid the read barrier here since the dex file is constant, so if we read the
  // from-space dex file pointer it will be equal to the to-space copy.
  return GetDexCache<kWithoutReadBarrier>()->GetDexFile();
}

inline const char* ArtMethod::GetDeclaringClassDescriptor() {
  uint32_t dex_method_idx = GetDexMethodIndex();
  if (UNLIKELY(dex_method_idx == dex::kDexNoIndex)) {
    return "<runtime method>";
  }
  DCHECK(!IsProxyMethod());
  const DexFile* dex_file = GetDexFile();
  return dex_file->GetMethodDeclaringClassDescriptor(dex_file->GetMethodId(dex_method_idx));
}

inline const char* ArtMethod::GetShorty() {
  uint32_t unused_length;
  return GetShorty(&unused_length);
}

inline const char* ArtMethod::GetShorty(uint32_t* out_length) {
  DCHECK(!IsProxyMethod());
  const DexFile* dex_file = GetDexFile();
  return dex_file->GetMethodShorty(dex_file->GetMethodId(GetDexMethodIndex()), out_length);
}

inline std::string_view ArtMethod::GetShortyView() {
  DCHECK(!IsProxyMethod());
  const DexFile* dex_file = GetDexFile();
  return dex_file->GetMethodShortyView(dex_file->GetMethodId(GetDexMethodIndex()));
}

inline const Signature ArtMethod::GetSignature() {
  uint32_t dex_method_idx = GetDexMethodIndex();
  if (dex_method_idx != dex::kDexNoIndex) {
    DCHECK(!IsProxyMethod());
    const DexFile* dex_file = GetDexFile();
    return dex_file->GetMethodSignature(dex_file->GetMethodId(dex_method_idx));
  }
  return Signature::NoSignature();
}

inline const char* ArtMethod::GetName() {
  uint32_t dex_method_idx = GetDexMethodIndex();
  if (LIKELY(dex_method_idx != dex::kDexNoIndex)) {
    DCHECK(!IsProxyMethod());
    const DexFile* dex_file = GetDexFile();
    return dex_file->GetMethodName(dex_file->GetMethodId(dex_method_idx));
  }
  return GetRuntimeMethodName();
}

inline std::string_view ArtMethod::GetNameView() {
  uint32_t dex_method_idx = GetDexMethodIndex();
  if (LIKELY(dex_method_idx != dex::kDexNoIndex)) {
    DCHECK(!IsProxyMethod());
    const DexFile* dex_file = GetDexFile();
    return dex_file->GetMethodNameView(dex_method_idx);
  }
  return GetRuntimeMethodName();
}

inline ObjPtr<mirror::String> ArtMethod::ResolveNameString() {
  DCHECK(!IsProxyMethod());
  const dex::MethodId& method_id = GetDexFile()->GetMethodId(GetDexMethodIndex());
  return Runtime::Current()->GetClassLinker()->ResolveString(method_id.name_idx_, this);
}

inline bool ArtMethod::NameEquals(ObjPtr<mirror::String> name) {
  DCHECK(!IsProxyMethod());
  const DexFile* dex_file = GetDexFile();
  const dex::MethodId& method_id = dex_file->GetMethodId(GetDexMethodIndex());
  const dex::StringIndex name_idx = method_id.name_idx_;
  uint32_t utf16_length;
  const char* utf8_name = dex_file->StringDataAndUtf16LengthByIdx(name_idx, &utf16_length);
  return dchecked_integral_cast<uint32_t>(name->GetLength()) == utf16_length &&
         name->Equals(utf8_name);
}

inline const dex::CodeItem* ArtMethod::GetCodeItem() {
  if (!HasCodeItem()) {
    return nullptr;
  }
  Runtime* runtime = Runtime::Current();
  PointerSize pointer_size = runtime->GetClassLinker()->GetImagePointerSize();
  return runtime->IsAotCompiler()
      ? GetDexFile()->GetCodeItem(reinterpret_cast32<uint32_t>(GetDataPtrSize(pointer_size)))
      : reinterpret_cast<const dex::CodeItem*>(
          reinterpret_cast<uintptr_t>(GetDataPtrSize(pointer_size)) & ~1);
}

inline bool ArtMethod::IsResolvedTypeIdx(dex::TypeIndex type_idx) {
  DCHECK(!IsProxyMethod());
  return LookupResolvedClassFromTypeIndex(type_idx) != nullptr;
}

inline int32_t ArtMethod::GetLineNumFromDexPC(uint32_t dex_pc) {
  DCHECK(!IsProxyMethod());
  if (dex_pc == dex::kDexNoIndex) {
    return IsNative() ? -2 : -1;
  }
  return annotations::GetLineNumFromPC(GetDexFile(), this, dex_pc);
}

inline const dex::ProtoId& ArtMethod::GetPrototype() {
  DCHECK(!IsProxyMethod());
  const DexFile* dex_file = GetDexFile();
  return dex_file->GetMethodPrototype(dex_file->GetMethodId(GetDexMethodIndex()));
}

inline const dex::TypeList* ArtMethod::GetParameterTypeList() {
  DCHECK(!IsProxyMethod());
  const DexFile* dex_file = GetDexFile();
  const dex::ProtoId& proto = dex_file->GetMethodPrototype(
      dex_file->GetMethodId(GetDexMethodIndex()));
  return dex_file->GetProtoParameters(proto);
}

inline const char* ArtMethod::GetDeclaringClassSourceFile() {
  DCHECK(!IsProxyMethod());
  return GetDeclaringClass()->GetSourceFile();
}

inline uint16_t ArtMethod::GetClassDefIndex() {
  DCHECK(!IsProxyMethod());
  if (LIKELY(!IsObsolete())) {
    return GetDeclaringClass()->GetDexClassDefIndex();
  } else {
    return FindObsoleteDexClassDefIndex();
  }
}

inline const dex::ClassDef& ArtMethod::GetClassDef() {
  DCHECK(!IsProxyMethod());
  return GetDexFile()->GetClassDef(GetClassDefIndex());
}

inline size_t ArtMethod::GetNumberOfParameters() {
  constexpr size_t return_type_count = 1u;
  uint32_t shorty_length;
  GetShorty(&shorty_length);
  return shorty_length - return_type_count;
}

inline const char* ArtMethod::GetReturnTypeDescriptor() {
  DCHECK(!IsProxyMethod());
  const DexFile* dex_file = GetDexFile();
  return dex_file->GetTypeDescriptor(dex_file->GetTypeId(GetReturnTypeIndex()));
}

inline Primitive::Type ArtMethod::GetReturnTypePrimitive() {
  return Primitive::GetType(GetReturnTypeDescriptor()[0]);
}

inline const char* ArtMethod::GetTypeDescriptorFromTypeIdx(dex::TypeIndex type_idx) {
  DCHECK(!IsProxyMethod());
  const DexFile* dex_file = GetDexFile();
  return dex_file->GetTypeDescriptor(dex_file->GetTypeId(type_idx));
}

inline ObjPtr<mirror::ClassLoader> ArtMethod::GetClassLoader() {
  DCHECK(!IsProxyMethod());
  return GetDeclaringClass()->GetClassLoader();
}

template <ReadBarrierOption kReadBarrierOption>
inline ObjPtr<mirror::DexCache> ArtMethod::GetDexCache() {
  if (LIKELY(!IsObsolete())) {
    ObjPtr<mirror::Class> klass = GetDeclaringClass<kReadBarrierOption>();
    return klass->GetDexCache<kDefaultVerifyFlags, kReadBarrierOption>();
  } else {
    DCHECK(!IsProxyMethod());
    return GetObsoleteDexCache<kReadBarrierOption>();
  }
}

inline bool ArtMethod::IsProxyMethod() {
  DCHECK(!IsRuntimeMethod()) << "ArtMethod::IsProxyMethod called on a runtime method";
  // No read barrier needed, we're reading the constant declaring class only to read
  // the constant proxy flag. See ReadBarrierOption.
  return GetDeclaringClass<kWithoutReadBarrier>()->IsProxyClass();
}

inline ArtMethod* ArtMethod::GetInterfaceMethodForProxyUnchecked(PointerSize pointer_size) {
  DCHECK(IsProxyMethod());
  // Do not check IsAssignableFrom() here as it relies on raw reference comparison
  // which may give false negatives while visiting references for a non-CC moving GC.
  return reinterpret_cast<ArtMethod*>(GetDataPtrSize(pointer_size));
}

inline ArtMethod* ArtMethod::GetInterfaceMethodIfProxy(PointerSize pointer_size) {
  if (LIKELY(!IsProxyMethod())) {
    return this;
  }
  ArtMethod* interface_method = GetInterfaceMethodForProxyUnchecked(pointer_size);
  // We can check that the proxy class implements the interface only if the proxy class
  // is resolved, otherwise the interface table is not yet initialized.
  DCHECK_IMPLIES(GetDeclaringClass()->IsResolved(),
                 interface_method->GetDeclaringClass()->IsAssignableFrom(GetDeclaringClass()));
  return interface_method;
}

inline dex::TypeIndex ArtMethod::GetReturnTypeIndex() {
  DCHECK(!IsProxyMethod());
  const DexFile* dex_file = GetDexFile();
  const dex::MethodId& method_id = dex_file->GetMethodId(GetDexMethodIndex());
  const dex::ProtoId& proto_id = dex_file->GetMethodPrototype(method_id);
  return proto_id.return_type_idx_;
}

inline ObjPtr<mirror::Class> ArtMethod::LookupResolvedReturnType() {
  return LookupResolvedClassFromTypeIndex(GetReturnTypeIndex());
}

inline ObjPtr<mirror::Class> ArtMethod::ResolveReturnType() {
  return ResolveClassFromTypeIndex(GetReturnTypeIndex());
}

inline bool ArtMethod::HasSingleImplementation() {
  // No read barrier needed for reading a constant reference only to read
  // a constant final class flag. See `ReadBarrierOption`.
  if (IsFinal() || GetDeclaringClass<kWithoutReadBarrier>()->IsFinal()) {
    // We don't set kAccSingleImplementation for these cases since intrinsic
    // can use the flag also.
    return true;
  }
  return (GetAccessFlags() & kAccSingleImplementation) != 0;
}

template<ReadBarrierOption kReadBarrierOption, bool kVisitProxyMethod, typename RootVisitorType>
void ArtMethod::VisitRoots(RootVisitorType& visitor, PointerSize pointer_size) {
  if (LIKELY(!declaring_class_.IsNull())) {
    visitor.VisitRoot(declaring_class_.AddressWithoutBarrier());
    if (kVisitProxyMethod) {
      ObjPtr<mirror::Class> klass = declaring_class_.Read<kReadBarrierOption>();
      if (UNLIKELY(klass->IsProxyClass())) {
        // For normal methods, dex cache shortcuts will be visited through the declaring class.
        // However, for proxies we need to keep the interface method alive, so we visit its roots.
        ArtMethod* interface_method = GetInterfaceMethodForProxyUnchecked(pointer_size);
        DCHECK(interface_method != nullptr);
        interface_method->VisitRoots<kReadBarrierOption, kVisitProxyMethod>(visitor, pointer_size);
      }
    }
  }
}

template<typename RootVisitorType>
void ArtMethod::VisitRoots(RootVisitorType& visitor,
                           uint8_t* start_boundary,
                           uint8_t* end_boundary,
                           ArtMethod* method) {
  mirror::CompressedReference<mirror::Object>* cls_ptr =
      reinterpret_cast<mirror::CompressedReference<mirror::Object>*>(
          reinterpret_cast<uint8_t*>(method) + DeclaringClassOffset().Int32Value());
  if (reinterpret_cast<uint8_t*>(cls_ptr) >= start_boundary
      && reinterpret_cast<uint8_t*>(cls_ptr) < end_boundary) {
    visitor.VisitRootIfNonNull(cls_ptr);
  }
}

template<PointerSize kPointerSize, typename RootVisitorType>
void ArtMethod::VisitArrayRoots(RootVisitorType& visitor,
                                uint8_t* start_boundary,
                                uint8_t* end_boundary,
                                LengthPrefixedArray<ArtMethod>* array) {
  DCHECK_LE(start_boundary, end_boundary);
  DCHECK_NE(array->size(), 0u);
  static constexpr size_t kMethodSize = ArtMethod::Size(kPointerSize);
  ArtMethod* first_method = &array->At(0, kMethodSize, ArtMethod::Alignment(kPointerSize));
  DCHECK_LE(static_cast<void*>(end_boundary),
            static_cast<void*>(reinterpret_cast<uint8_t*>(first_method)
                               + array->size() * kMethodSize));
  uint8_t* declaring_class =
      reinterpret_cast<uint8_t*>(first_method) + DeclaringClassOffset().Int32Value();
  // Jump to the first class to visit.
  if (declaring_class < start_boundary) {
    size_t remainder = (start_boundary - declaring_class) % kMethodSize;
    declaring_class = start_boundary;
    if (remainder > 0) {
      declaring_class += kMethodSize - remainder;
    }
  }
  while (declaring_class < end_boundary) {
    visitor.VisitRootIfNonNull(
        reinterpret_cast<mirror::CompressedReference<mirror::Object>*>(declaring_class));
    declaring_class += kMethodSize;
  }
}

template <typename Visitor>
inline void ArtMethod::UpdateEntrypoints(const Visitor& visitor, PointerSize pointer_size) {
  if (IsNative()) {
    const void* old_native_code = GetEntryPointFromJniPtrSize(pointer_size);
    const void* new_native_code = visitor(old_native_code);
    if (old_native_code != new_native_code) {
      SetEntryPointFromJniPtrSize(new_native_code, pointer_size);
    }
  }
  const void* old_code = GetEntryPointFromQuickCompiledCodePtrSize(pointer_size);
  const void* new_code = visitor(old_code);
  if (old_code != new_code) {
    SetEntryPointFromQuickCompiledCodePtrSize(new_code, pointer_size);
  }
}

template <ReadBarrierOption kReadBarrierOption>
inline bool ArtMethod::StillNeedsClinitCheck() {
  if (!NeedsClinitCheckBeforeCall()) {
    return false;
  }
  ObjPtr<mirror::Class> klass = GetDeclaringClass<kReadBarrierOption>();
  return !klass->IsVisiblyInitialized();
}

inline bool ArtMethod::StillNeedsClinitCheckMayBeDead() {
  if (!NeedsClinitCheckBeforeCall()) {
    return false;
  }
  ObjPtr<mirror::Class> klass = GetDeclaringClassMayBeDead();
  return !klass->IsVisiblyInitialized();
}

inline bool ArtMethod::IsDeclaringClassVerifiedMayBeDead() {
  ObjPtr<mirror::Class> klass = GetDeclaringClassMayBeDead();
  return klass->IsVerified();
}

inline ObjPtr<mirror::Class> ArtMethod::GetDeclaringClassMayBeDead() {
  // Helper method for checking the status of the declaring class which may be dead.
  //
  // To avoid resurrecting an unreachable object, or crashing the GC in some GC phases,
  // we must not use a full read barrier. Therefore we read the declaring class without
  // a read barrier and check if it's already marked. If yes, we check the status of the
  // to-space class object as intended. Otherwise, there is no to-space object and the
  // from-space class object contains the most recent value of the status field; even if
  // this races with another thread doing a read barrier and updating the status, that's
  // no different from a race with a thread that just updates the status.
  ObjPtr<mirror::Class> klass = GetDeclaringClass<kWithoutReadBarrier>();
  ObjPtr<mirror::Class> marked = ReadBarrier::IsMarked(klass.Ptr());
  return (marked != nullptr) ? marked : klass;
}

inline CodeItemInstructionAccessor ArtMethod::DexInstructions() {
  return CodeItemInstructionAccessor(*GetDexFile(), GetCodeItem());
}

inline CodeItemDataAccessor ArtMethod::DexInstructionData() {
  return CodeItemDataAccessor(*GetDexFile(), GetCodeItem());
}

inline CodeItemDebugInfoAccessor ArtMethod::DexInstructionDebugInfo() {
  return CodeItemDebugInfoAccessor(*GetDexFile(), GetCodeItem(), GetDexMethodIndex());
}

inline bool ArtMethod::CounterHasChanged(uint16_t threshold) {
  DCHECK(!IsAbstract());
  DCHECK_EQ(threshold, Runtime::Current()->GetJITOptions()->GetWarmupThreshold());
  return hotness_count_ != threshold;
}

inline void ArtMethod::ResetCounter(uint16_t new_value) {
  if (IsAbstract()) {
    return;
  }
  if (IsMemorySharedMethod()) {
    return;
  }
  DCHECK_EQ(new_value, Runtime::Current()->GetJITOptions()->GetWarmupThreshold());
  // Avoid dirtying the value if possible.
  if (hotness_count_ != new_value) {
    hotness_count_ = new_value;
  }
}

inline void ArtMethod::SetHotCounter() {
  DCHECK(!IsAbstract());
  // Avoid dirtying the value if possible.
  if (hotness_count_ != 0) {
    hotness_count_ = 0;
  }
}

inline void ArtMethod::UpdateCounter(int32_t new_samples) {
  DCHECK(!IsAbstract());
  DCHECK_GT(new_samples, 0);
  DCHECK_LE(new_samples, std::numeric_limits<uint16_t>::max());
  if (IsMemorySharedMethod()) {
    return;
  }
  uint16_t old_hotness_count = hotness_count_;
  uint16_t new_count = (old_hotness_count <= new_samples) ? 0u : old_hotness_count - new_samples;
  // Avoid dirtying the value if possible.
  if (old_hotness_count != new_count) {
    hotness_count_ = new_count;
  }
}

inline bool ArtMethod::CounterIsHot() {
  DCHECK(!IsAbstract());
  return hotness_count_ == 0;
}

inline bool ArtMethod::CounterHasReached(uint16_t samples, uint16_t threshold) {
  DCHECK(!IsAbstract());
  DCHECK_EQ(threshold, Runtime::Current()->GetJITOptions()->GetWarmupThreshold());
  DCHECK_LE(samples, threshold);
  return hotness_count_ <= (threshold - samples);
}

inline uint16_t ArtMethod::GetCounter() {
  DCHECK(!IsAbstract());
  return hotness_count_;
}

inline uint32_t ArtMethod::GetImtIndex() {
  if (LIKELY(IsAbstract())) {
    return imt_index_;
  } else {
    return ImTable::GetImtIndex(this);
  }
}

inline void ArtMethod::CalculateAndSetImtIndex() {
  DCHECK(IsAbstract()) << PrettyMethod();
  imt_index_ = ImTable::GetImtIndex(this);
}

}  // namespace art

#endif  // ART_RUNTIME_ART_METHOD_INL_H_
