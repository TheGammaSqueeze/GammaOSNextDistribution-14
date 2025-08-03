/*
 * Copyright (C) 2008 The Android Open Source Project
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

#include "java_lang_Class.h"

#include <iostream>

#include "art_field-inl.h"
#include "art_method-alloc-inl.h"
#include "base/enums.h"
#include "class_linker-inl.h"
#include "class_root-inl.h"
#include "common_throws.h"
#include "compat_framework.h"
#include "dex/descriptors_names.h"
#include "dex/dex_file-inl.h"
#include "dex/dex_file_annotations.h"
#include "dex/utf.h"
#include "hidden_api.h"
#include "jni/jni_internal.h"
#include "mirror/class-alloc-inl.h"
#include "mirror/class_ext.h"
#include "mirror/class-inl.h"
#include "mirror/class_loader.h"
#include "mirror/field.h"
#include "mirror/method.h"
#include "mirror/method_handles_lookup.h"
#include "mirror/object-inl.h"
#include "mirror/object_array-alloc-inl.h"
#include "mirror/object_array-inl.h"
#include "mirror/proxy.h"
#include "mirror/string-alloc-inl.h"
#include "mirror/string-inl.h"
#include "native_util.h"
#include "nativehelper/jni_macros.h"
#include "nativehelper/scoped_local_ref.h"
#include "nativehelper/scoped_utf_chars.h"
#include "nth_caller_visitor.h"
#include "obj_ptr-inl.h"
#include "reflection.h"
#include "reflective_handle_scope-inl.h"
#include "scoped_fast_native_object_access-inl.h"
#include "scoped_thread_state_change-inl.h"
#include "well_known_classes-inl.h"

namespace art HIDDEN {

static std::function<hiddenapi::AccessContext()> GetHiddenapiAccessContextFunction(Thread* self) {
  return [=]() REQUIRES_SHARED(Locks::mutator_lock_) {
    return hiddenapi::GetReflectionCallerAccessContext(self);
  };
}

// Returns true if the first non-ClassClass caller up the stack should not be
// allowed access to `member`.
template<typename T>
ALWAYS_INLINE static bool ShouldDenyAccessToMember(T* member, Thread* self)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  return hiddenapi::ShouldDenyAccessToMember(member,
                                             GetHiddenapiAccessContextFunction(self),
                                             hiddenapi::AccessMethod::kReflection);
}

ALWAYS_INLINE static inline ObjPtr<mirror::Class> DecodeClass(
    const ScopedFastNativeObjectAccess& soa, jobject java_class)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ObjPtr<mirror::Class> c = soa.Decode<mirror::Class>(java_class);
  DCHECK(c != nullptr);
  DCHECK(c->IsClass());
  // TODO: we could EnsureInitialized here, rather than on every reflective get/set or invoke .
  // For now, we conservatively preserve the old dalvik behavior. A quick "IsInitialized" check
  // every time probably doesn't make much difference to reflection performance anyway.
  return c;
}

// "name" is in "binary name" format, e.g. "dalvik.system.Debug$1".
static jclass Class_classForName(JNIEnv* env, jclass, jstring javaName, jboolean initialize,
                                 jobject javaLoader) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<3> hs(soa.Self());
  Handle<mirror::String> mirror_name = hs.NewHandle(soa.Decode<mirror::String>(javaName));
  if (mirror_name == nullptr) {
    soa.Self()->ThrowNewWrappedException("Ljava/lang/NullPointerException;", /*msg=*/ nullptr);
    return nullptr;
  }

  // We need to validate and convert the name (from x.y.z to x/y/z).  This
  // is especially handy for array types, since we want to avoid
  // auto-generating bogus array classes.
  std::string name = mirror_name->ToModifiedUtf8();
  if (!IsValidBinaryClassName(name.c_str())) {
    soa.Self()->ThrowNewExceptionF("Ljava/lang/ClassNotFoundException;",
                                   "Invalid name: %s", name.c_str());
    return nullptr;
  }

  std::string descriptor(DotToDescriptor(name.c_str()));
  Handle<mirror::ClassLoader> class_loader(
      hs.NewHandle(soa.Decode<mirror::ClassLoader>(javaLoader)));
  ClassLinker* class_linker = Runtime::Current()->GetClassLinker();
  Handle<mirror::Class> c(
      hs.NewHandle(class_linker->FindClass(soa.Self(), descriptor.c_str(), class_loader)));
  if (UNLIKELY(c == nullptr)) {
    StackHandleScope<2> hs2(soa.Self());
    Handle<mirror::Object> cause = hs2.NewHandle(soa.Self()->GetException());
    soa.Self()->ClearException();
    Handle<mirror::Object> cnfe =
        WellKnownClasses::java_lang_ClassNotFoundException_init->NewObject<'L', 'L'>(
            hs2, soa.Self(), mirror_name, cause);
    if (cnfe != nullptr) {
      // Make sure allocation didn't fail with an OOME.
      soa.Self()->SetException(ObjPtr<mirror::Throwable>::DownCast(cnfe.Get()));
    }
    return nullptr;
  }
  if (initialize) {
    class_linker->EnsureInitialized(soa.Self(), c, true, true);
  }

  // java.lang.ClassValue was added in Android U, and proguarding tools
  // used that as justification to remove computeValue method implementation.
  // Usual pattern was to check that Class.forName("java.lang.ClassValue")
  // call does not throw and use ClassValue-based implementation or fallback
  // to other solution if it does throw.
  // So far ClassValue is the only class with such a problem and hence this
  // ad-hoc check.
  // See b/259501764.
  if (!c->CheckIsVisibleWithTargetSdk(soa.Self())) {
    DCHECK(soa.Self()->IsExceptionPending());
    return nullptr;
  }

  return soa.AddLocalReference<jclass>(c.Get());
}

static jclass Class_getPrimitiveClass(JNIEnv* env, jclass, jstring name) {
  ScopedFastNativeObjectAccess soa(env);
  ObjPtr<mirror::Class> klass = mirror::Class::GetPrimitiveClass(soa.Decode<mirror::String>(name));
  return soa.AddLocalReference<jclass>(klass);
}

static jstring Class_getNameNative(JNIEnv* env, jobject javaThis) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<1> hs(soa.Self());
  ObjPtr<mirror::Class> c = DecodeClass(soa, javaThis);
  return soa.AddLocalReference<jstring>(mirror::Class::ComputeName(hs.NewHandle(c)));
}

static jobjectArray Class_getInterfacesInternal(JNIEnv* env, jobject javaThis) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<1> hs(soa.Self());
  Handle<mirror::Class> klass = hs.NewHandle(DecodeClass(soa, javaThis));
  if (klass->IsObsoleteObject()) {
    ThrowRuntimeException("Obsolete Object!");
    return nullptr;
  }

  if (klass->IsProxyClass()) {
    StackHandleScope<1> hs2(soa.Self());
    Handle<mirror::ObjectArray<mirror::Class>> interfaces =
        hs2.NewHandle(klass->GetProxyInterfaces());
    return soa.AddLocalReference<jobjectArray>(
        mirror::ObjectArray<mirror::Class>::Clone(interfaces, soa.Self()));
  }

  const dex::TypeList* iface_list = klass->GetInterfaceTypeList();
  if (iface_list == nullptr) {
    return nullptr;
  }

  ClassLinker* linker = Runtime::Current()->GetClassLinker();
  const uint32_t num_ifaces = iface_list->Size();
  ObjPtr<mirror::Class> class_array_class =
      GetClassRoot<mirror::ObjectArray<mirror::Class>>(linker);
  ObjPtr<mirror::ObjectArray<mirror::Class>> ifaces =
      mirror::ObjectArray<mirror::Class>::Alloc(soa.Self(), class_array_class, num_ifaces);
  if (ifaces.IsNull()) {
    DCHECK(soa.Self()->IsExceptionPending());
    return nullptr;
  }

  // Check that we aren't in an active transaction, we call SetWithoutChecks
  // with kActiveTransaction == false.
  DCHECK(!Runtime::Current()->IsActiveTransaction());

  for (uint32_t i = 0; i < num_ifaces; ++i) {
    const dex::TypeIndex type_idx = iface_list->GetTypeItem(i).type_idx_;
    ObjPtr<mirror::Class> interface = linker->LookupResolvedType(type_idx, klass.Get());
    DCHECK(interface != nullptr);
    ifaces->SetWithoutChecks<false>(i, interface);
  }

  return soa.AddLocalReference<jobjectArray>(ifaces);
}

static jobjectArray Class_getDeclaredFieldsUnchecked(JNIEnv* env, jobject javaThis,
                                                     jboolean publicOnly) {
  ScopedFastNativeObjectAccess soa(env);
  ObjPtr<mirror::Class> klass = DecodeClass(soa, javaThis);
  return soa.AddLocalReference<jobjectArray>(
      klass->GetDeclaredFields(soa.Self(),
                               publicOnly != JNI_FALSE,
                               /*force_resolve=*/ false));
}

static jobjectArray Class_getDeclaredFields(JNIEnv* env, jobject javaThis) {
  ScopedFastNativeObjectAccess soa(env);
  ObjPtr<mirror::Class> klass = DecodeClass(soa, javaThis);
  return soa.AddLocalReference<jobjectArray>(
      klass->GetDeclaredFields(soa.Self(),
                               /*public_only=*/ false,
                               /*force_resolve=*/ true));
}

static jobjectArray Class_getPublicDeclaredFields(JNIEnv* env, jobject javaThis) {
  ScopedFastNativeObjectAccess soa(env);
  ObjPtr<mirror::Class> klass = DecodeClass(soa, javaThis);
  return soa.AddLocalReference<jobjectArray>(
      klass->GetDeclaredFields(soa.Self(),
                               /*public_only=*/ true,
                               /*force_resolve=*/ true));
}

// Performs a binary search through an array of fields, TODO: Is this fast enough if we don't use
// the dex cache for lookups? I think CompareModifiedUtf8ToUtf16AsCodePointValues should be fairly
// fast.
ALWAYS_INLINE static inline ArtField* FindFieldByName(ObjPtr<mirror::String> name,
                                                      LengthPrefixedArray<ArtField>* fields)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  if (fields == nullptr) {
    return nullptr;
  }
  size_t low = 0;
  size_t high = fields->size();
  const bool is_name_compressed = name->IsCompressed();
  const uint16_t* const data = (is_name_compressed) ? nullptr : name->GetValue();
  const uint8_t* const data_compressed = (is_name_compressed) ? name->GetValueCompressed()
                                                              : nullptr;
  const size_t length = name->GetLength();
  while (low < high) {
    auto mid = (low + high) / 2;
    ArtField& field = fields->At(mid);
    int result = 0;
    if (is_name_compressed) {
      size_t field_length = strlen(field.GetName());
      size_t min_size = (length < field_length) ? length : field_length;
      result = memcmp(field.GetName(), data_compressed, min_size);
      if (result == 0) {
        result = field_length - length;
      }
    } else {
      result = CompareModifiedUtf8ToUtf16AsCodePointValues(field.GetName(), data, length);
    }
    // Alternate approach, only a few % faster at the cost of more allocations.
    // int result = field->GetStringName(self, true)->CompareTo(name);
    if (result < 0) {
      low = mid + 1;
    } else if (result > 0) {
      high = mid;
    } else {
      return &field;
    }
  }
  if (kIsDebugBuild) {
    for (ArtField& field : MakeIterationRangeFromLengthPrefixedArray(fields)) {
      CHECK_NE(field.GetName(), name->ToModifiedUtf8());
    }
  }
  return nullptr;
}

ALWAYS_INLINE static inline ObjPtr<mirror::Field> GetDeclaredField(Thread* self,
                                                                   ObjPtr<mirror::Class> c,
                                                                   ObjPtr<mirror::String> name)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  if (UNLIKELY(c->IsObsoleteObject())) {
    ThrowRuntimeException("Obsolete Object!");
    return nullptr;
  }
  ArtField* art_field = FindFieldByName(name, c->GetIFieldsPtr());
  if (art_field != nullptr) {
    return mirror::Field::CreateFromArtField(self, art_field, true);
  }
  art_field = FindFieldByName(name, c->GetSFieldsPtr());
  if (art_field != nullptr) {
    return mirror::Field::CreateFromArtField(self, art_field, true);
  }
  return nullptr;
}

static ObjPtr<mirror::Field> GetPublicFieldRecursive(
    Thread* self, ObjPtr<mirror::Class> clazz, ObjPtr<mirror::String> name)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  DCHECK(clazz != nullptr);
  DCHECK(name != nullptr);
  DCHECK(self != nullptr);

  if (UNLIKELY(clazz->IsObsoleteObject())) {
    ThrowRuntimeException("Obsolete Object!");
    return nullptr;
  }
  StackHandleScope<2> hs(self);
  MutableHandle<mirror::Class> h_clazz(hs.NewHandle(clazz));
  Handle<mirror::String> h_name(hs.NewHandle(name));

  // We search the current class, its direct interfaces then its superclass.
  while (h_clazz != nullptr) {
    ObjPtr<mirror::Field> result = GetDeclaredField(self, h_clazz.Get(), h_name.Get());
    if ((result != nullptr) && (result->GetAccessFlags() & kAccPublic)) {
      return result;
    } else if (UNLIKELY(self->IsExceptionPending())) {
      // Something went wrong. Bail out.
      return nullptr;
    }

    uint32_t num_direct_interfaces = h_clazz->NumDirectInterfaces();
    for (uint32_t i = 0; i < num_direct_interfaces; i++) {
      ObjPtr<mirror::Class> iface = mirror::Class::ResolveDirectInterface(self, h_clazz, i);
      if (UNLIKELY(iface == nullptr)) {
        self->AssertPendingException();
        return nullptr;
      }
      result = GetPublicFieldRecursive(self, iface, h_name.Get());
      if (result != nullptr) {
        DCHECK(result->GetAccessFlags() & kAccPublic);
        return result;
      } else if (UNLIKELY(self->IsExceptionPending())) {
        // Something went wrong. Bail out.
        return nullptr;
      }
    }

    // We don't try the superclass if we are an interface.
    if (h_clazz->IsInterface()) {
      break;
    }

    // Get the next class.
    h_clazz.Assign(h_clazz->GetSuperClass());
  }
  return nullptr;
}

static jobject Class_getPublicFieldRecursive(JNIEnv* env, jobject javaThis, jstring name) {
  ScopedFastNativeObjectAccess soa(env);
  auto name_string = soa.Decode<mirror::String>(name);
  if (UNLIKELY(name_string == nullptr)) {
    ThrowNullPointerException("name == null");
    return nullptr;
  }

  StackHandleScope<1> hs(soa.Self());
  Handle<mirror::Field> field = hs.NewHandle(GetPublicFieldRecursive(
      soa.Self(), DecodeClass(soa, javaThis), name_string));
  if (field.Get() == nullptr || ShouldDenyAccessToMember(field->GetArtField(), soa.Self())) {
    return nullptr;
  }
  return soa.AddLocalReference<jobject>(field.Get());
}

static jobject Class_getDeclaredField(JNIEnv* env, jobject javaThis, jstring name) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<3> hs(soa.Self());
  Handle<mirror::String> h_string = hs.NewHandle(soa.Decode<mirror::String>(name));
  if (h_string == nullptr) {
    ThrowNullPointerException("name == null");
    return nullptr;
  }
  Handle<mirror::Class> h_klass = hs.NewHandle(DecodeClass(soa, javaThis));
  Handle<mirror::Field> result =
      hs.NewHandle(GetDeclaredField(soa.Self(), h_klass.Get(), h_string.Get()));
  if (result == nullptr || ShouldDenyAccessToMember(result->GetArtField(), soa.Self())) {
    std::string name_str = h_string->ToModifiedUtf8();
    if (name_str == "value" && h_klass->IsStringClass()) {
      // We log the error for this specific case, as the user might just swallow the exception.
      // This helps diagnose crashes when applications rely on the String#value field being
      // there.
      // Also print on the error stream to test it through run-test.
      std::string message("The String#value field is not present on Android versions >= 6.0");
      LOG(ERROR) << message;
      std::cerr << message << std::endl;
    }
    // We may have a pending exception if we failed to resolve.
    if (!soa.Self()->IsExceptionPending()) {
      ThrowNoSuchFieldException(h_klass.Get(), name_str);
    }
    return nullptr;
  }
  return soa.AddLocalReference<jobject>(result.Get());
}

static jobject Class_getDeclaredConstructorInternal(
    JNIEnv* env, jobject javaThis, jobjectArray args) {
  ScopedFastNativeObjectAccess soa(env);
  DCHECK_EQ(Runtime::Current()->GetClassLinker()->GetImagePointerSize(), kRuntimePointerSize);
  DCHECK(!Runtime::Current()->IsActiveTransaction());

  StackHandleScope<1> hs(soa.Self());
  ObjPtr<mirror::Class> klass = DecodeClass(soa, javaThis);
  if (UNLIKELY(klass->IsObsoleteObject())) {
    ThrowRuntimeException("Obsolete Object!");
    return nullptr;
  }
  Handle<mirror::Constructor> result = hs.NewHandle(
      mirror::Class::GetDeclaredConstructorInternal<kRuntimePointerSize>(
          soa.Self(),
          klass,
          soa.Decode<mirror::ObjectArray<mirror::Class>>(args)));
  if (result == nullptr || ShouldDenyAccessToMember(result->GetArtMethod(), soa.Self())) {
    return nullptr;
  }
  return soa.AddLocalReference<jobject>(result.Get());
}

static ALWAYS_INLINE inline bool MethodMatchesConstructor(
    ArtMethod* m,
    bool public_only,
    const hiddenapi::AccessContext& hiddenapi_context) REQUIRES_SHARED(Locks::mutator_lock_) {
  DCHECK(m != nullptr);
  return m->IsConstructor() &&
         !m->IsStatic() &&
         mirror::Class::IsDiscoverable(public_only, hiddenapi_context, m);
}

static jobjectArray Class_getDeclaredConstructorsInternal(
    JNIEnv* env, jobject javaThis, jboolean publicOnly) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<2> hs(soa.Self());
  bool public_only = (publicOnly != JNI_FALSE);
  auto hiddenapi_context = hiddenapi::GetReflectionCallerAccessContext(soa.Self());
  Handle<mirror::Class> h_klass = hs.NewHandle(DecodeClass(soa, javaThis));
  if (UNLIKELY(h_klass->IsObsoleteObject())) {
    ThrowRuntimeException("Obsolete Object!");
    return nullptr;
  }
  size_t constructor_count = 0;
  // Two pass approach for speed.
  for (auto& m : h_klass->GetDirectMethods(kRuntimePointerSize)) {
    constructor_count += MethodMatchesConstructor(&m, public_only, hiddenapi_context) ? 1u : 0u;
  }
  auto h_constructors = hs.NewHandle(mirror::ObjectArray<mirror::Constructor>::Alloc(
      soa.Self(), GetClassRoot<mirror::ObjectArray<mirror::Constructor>>(), constructor_count));
  if (UNLIKELY(h_constructors == nullptr)) {
    soa.Self()->AssertPendingException();
    return nullptr;
  }
  constructor_count = 0;
  for (auto& m : h_klass->GetDirectMethods(kRuntimePointerSize)) {
    if (MethodMatchesConstructor(&m, public_only, hiddenapi_context)) {
      DCHECK_EQ(Runtime::Current()->GetClassLinker()->GetImagePointerSize(), kRuntimePointerSize);
      DCHECK(!Runtime::Current()->IsActiveTransaction());
      ObjPtr<mirror::Constructor> constructor =
          mirror::Constructor::CreateFromArtMethod<kRuntimePointerSize>(soa.Self(), &m);
      if (UNLIKELY(constructor == nullptr)) {
        soa.Self()->AssertPendingOOMException();
        return nullptr;
      }
      h_constructors->SetWithoutChecks<false>(constructor_count++, constructor);
    }
  }
  return soa.AddLocalReference<jobjectArray>(h_constructors.Get());
}

static jobject Class_getDeclaredMethodInternal(JNIEnv* env, jobject javaThis,
                                               jstring name, jobjectArray args) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<1> hs(soa.Self());
  DCHECK_EQ(Runtime::Current()->GetClassLinker()->GetImagePointerSize(), kRuntimePointerSize);
  DCHECK(!Runtime::Current()->IsActiveTransaction());
  ObjPtr<mirror::Class> klass = DecodeClass(soa, javaThis);
  if (UNLIKELY(klass->IsObsoleteObject())) {
    ThrowRuntimeException("Obsolete Object!");
    return nullptr;
  }
  Handle<mirror::Method> result = hs.NewHandle(
      mirror::Class::GetDeclaredMethodInternal<kRuntimePointerSize>(
          soa.Self(),
          klass,
          soa.Decode<mirror::String>(name),
          soa.Decode<mirror::ObjectArray<mirror::Class>>(args),
          GetHiddenapiAccessContextFunction(soa.Self())));
  if (result == nullptr || ShouldDenyAccessToMember(result->GetArtMethod(), soa.Self())) {
    return nullptr;
  }
  return soa.AddLocalReference<jobject>(result.Get());
}

static jobjectArray Class_getDeclaredMethodsUnchecked(JNIEnv* env, jobject javaThis,
                                                      jboolean publicOnly) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<2> hs(soa.Self());

  auto hiddenapi_context = hiddenapi::GetReflectionCallerAccessContext(soa.Self());
  bool public_only = (publicOnly != JNI_FALSE);

  Handle<mirror::Class> klass = hs.NewHandle(DecodeClass(soa, javaThis));
  if (klass->IsObsoleteObject()) {
    ThrowRuntimeException("Obsolete Object!");
    return nullptr;
  }
  size_t num_methods = 0;
  for (ArtMethod& m : klass->GetDeclaredMethods(kRuntimePointerSize)) {
    uint32_t modifiers = m.GetAccessFlags();
    // Add non-constructor declared methods.
    if ((modifiers & kAccConstructor) == 0 &&
        mirror::Class::IsDiscoverable(public_only, hiddenapi_context, &m)) {
      ++num_methods;
    }
  }
  auto ret = hs.NewHandle(mirror::ObjectArray<mirror::Method>::Alloc(
      soa.Self(), GetClassRoot<mirror::ObjectArray<mirror::Method>>(), num_methods));
  if (ret == nullptr) {
    soa.Self()->AssertPendingOOMException();
    return nullptr;
  }
  num_methods = 0;
  for (ArtMethod& m : klass->GetDeclaredMethods(kRuntimePointerSize)) {
    uint32_t modifiers = m.GetAccessFlags();
    if ((modifiers & kAccConstructor) == 0 &&
        mirror::Class::IsDiscoverable(public_only, hiddenapi_context, &m)) {
      DCHECK_EQ(Runtime::Current()->GetClassLinker()->GetImagePointerSize(), kRuntimePointerSize);
      DCHECK(!Runtime::Current()->IsActiveTransaction());
      ObjPtr<mirror::Method> method =
          mirror::Method::CreateFromArtMethod<kRuntimePointerSize>(soa.Self(), &m);
      if (method == nullptr) {
        soa.Self()->AssertPendingException();
        return nullptr;
      }
      ret->SetWithoutChecks<false>(num_methods++, method);
    }
  }
  return soa.AddLocalReference<jobjectArray>(ret.Get());
}

static jobject Class_getDeclaredAnnotation(JNIEnv* env, jobject javaThis, jclass annotationClass) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<2> hs(soa.Self());
  Handle<mirror::Class> klass(hs.NewHandle(DecodeClass(soa, javaThis)));
  if (klass->IsObsoleteObject()) {
    ThrowRuntimeException("Obsolete Object!");
    return nullptr;
  }

  // Handle public contract to throw NPE if the "annotationClass" argument was null.
  if (UNLIKELY(annotationClass == nullptr)) {
    ThrowNullPointerException("annotationClass");
    return nullptr;
  }

  if (klass->IsProxyClass() || klass->GetDexCache() == nullptr) {
    return nullptr;
  }
  Handle<mirror::Class> annotation_class(hs.NewHandle(soa.Decode<mirror::Class>(annotationClass)));
  return soa.AddLocalReference<jobject>(
      annotations::GetAnnotationForClass(klass, annotation_class));
}

static jobjectArray Class_getDeclaredAnnotations(JNIEnv* env, jobject javaThis) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<1> hs(soa.Self());
  Handle<mirror::Class> klass(hs.NewHandle(DecodeClass(soa, javaThis)));
  if (klass->IsObsoleteObject()) {
    ThrowRuntimeException("Obsolete Object!");
    return nullptr;
  }
  if (klass->IsProxyClass() || klass->GetDexCache() == nullptr) {
    // Return an empty array instead of a null pointer.
    ObjPtr<mirror::Class>  annotation_array_class =
        WellKnownClasses::ToClass(WellKnownClasses::java_lang_annotation_Annotation__array);
    ObjPtr<mirror::ObjectArray<mirror::Object>> empty_array =
        mirror::ObjectArray<mirror::Object>::Alloc(soa.Self(),
                                                   annotation_array_class,
                                                   /* length= */ 0);
    return soa.AddLocalReference<jobjectArray>(empty_array);
  }
  return soa.AddLocalReference<jobjectArray>(annotations::GetAnnotationsForClass(klass));
}

static jobjectArray Class_getDeclaredClasses(JNIEnv* env, jobject javaThis) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<1> hs(soa.Self());
  Handle<mirror::Class> klass(hs.NewHandle(DecodeClass(soa, javaThis)));
  if (klass->IsObsoleteObject()) {
    ThrowRuntimeException("Obsolete Object!");
    return nullptr;
  }
  ObjPtr<mirror::ObjectArray<mirror::Class>> classes = nullptr;
  if (!klass->IsProxyClass() && klass->GetDexCache() != nullptr) {
    classes = annotations::GetDeclaredClasses(klass);
  }
  if (classes == nullptr) {
    // Return an empty array instead of a null pointer.
    if (soa.Self()->IsExceptionPending()) {
      // Pending exception from GetDeclaredClasses.
      return nullptr;
    }
    ObjPtr<mirror::Class> class_array_class = GetClassRoot<mirror::ObjectArray<mirror::Class>>();
    DCHECK(class_array_class != nullptr);
    ObjPtr<mirror::ObjectArray<mirror::Class>> empty_array =
        mirror::ObjectArray<mirror::Class>::Alloc(soa.Self(), class_array_class, 0);
    return soa.AddLocalReference<jobjectArray>(empty_array);
  }
  return soa.AddLocalReference<jobjectArray>(classes);
}

static jclass Class_getEnclosingClass(JNIEnv* env, jobject javaThis) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<1> hs(soa.Self());
  Handle<mirror::Class> klass(hs.NewHandle(DecodeClass(soa, javaThis)));
  if (klass->IsObsoleteObject()) {
    ThrowRuntimeException("Obsolete Object!");
    return nullptr;
  }
  if (klass->IsProxyClass() || klass->GetDexCache() == nullptr) {
    return nullptr;
  }
  return soa.AddLocalReference<jclass>(annotations::GetEnclosingClass(klass));
}

static jobject Class_getEnclosingConstructorNative(JNIEnv* env, jobject javaThis) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<1> hs(soa.Self());
  Handle<mirror::Class> klass(hs.NewHandle(DecodeClass(soa, javaThis)));
  if (klass->IsObsoleteObject()) {
    ThrowRuntimeException("Obsolete Object!");
    return nullptr;
  }
  if (klass->IsProxyClass() || klass->GetDexCache() == nullptr) {
    return nullptr;
  }
  ObjPtr<mirror::Object> method = annotations::GetEnclosingMethod(klass);
  if (method != nullptr) {
    if (GetClassRoot<mirror::Constructor>() == method->GetClass()) {
      return soa.AddLocalReference<jobject>(method);
    }
  }
  return nullptr;
}

static jobject Class_getEnclosingMethodNative(JNIEnv* env, jobject javaThis) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<1> hs(soa.Self());
  Handle<mirror::Class> klass(hs.NewHandle(DecodeClass(soa, javaThis)));
  if (klass->IsObsoleteObject()) {
    ThrowRuntimeException("Obsolete Object!");
    return nullptr;
  }
  if (klass->IsProxyClass() || klass->GetDexCache() == nullptr) {
    return nullptr;
  }
  ObjPtr<mirror::Object> method = annotations::GetEnclosingMethod(klass);
  if (method != nullptr) {
    if (GetClassRoot<mirror::Method>() == method->GetClass()) {
      return soa.AddLocalReference<jobject>(method);
    }
  }
  return nullptr;
}

static jint Class_getInnerClassFlags(JNIEnv* env, jobject javaThis, jint defaultValue) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<1> hs(soa.Self());
  Handle<mirror::Class> klass(hs.NewHandle(DecodeClass(soa, javaThis)));
  if (klass->IsObsoleteObject()) {
    ThrowRuntimeException("Obsolete Object!");
    return 0;
  }
  return mirror::Class::GetInnerClassFlags(klass, defaultValue);
}

static jstring Class_getSimpleNameNative(JNIEnv* env, jobject javaThis) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<3> hs(soa.Self());
  Handle<mirror::Class> klass(hs.NewHandle(DecodeClass(soa, javaThis)));
  if (klass->IsObsoleteObject()) {
    ThrowRuntimeException("Obsolete Object!");
    return nullptr;
  }
  if (!klass->IsProxyClass() && klass->GetDexCache() != nullptr) {
    ObjPtr<mirror::String> class_name = nullptr;
    if (annotations::GetInnerClass(klass, &class_name)) {
      if (class_name == nullptr) {  // Anonymous class
        ObjPtr<mirror::Class> j_l_String =
            WellKnownClasses::java_lang_String_EMPTY->GetDeclaringClass();
        ObjPtr<mirror::Object> empty_string =
            WellKnownClasses::java_lang_String_EMPTY->GetObject(j_l_String);
        DCHECK(empty_string != nullptr);
        return soa.AddLocalReference<jstring>(empty_string);
      }
      Handle<mirror::String> h_inner_name(hs.NewHandle<mirror::String>(class_name));
      if (annotations::GetDeclaringClass(klass) != nullptr ||   // member class
          annotations::GetEnclosingMethod(klass) != nullptr) {  // local class
        return soa.AddLocalReference<jstring>(h_inner_name.Get());
      }
    }
  }

  Handle<mirror::String> h_name(hs.NewHandle<mirror::String>(mirror::Class::ComputeName(klass)));
  if (h_name == nullptr) {
    return nullptr;
  }
  int32_t dot_index = h_name->LastIndexOf('.');
  if (dot_index < 0) {
    return soa.AddLocalReference<jstring>(h_name.Get());
  }
  int32_t start_index = dot_index + 1;
  int32_t length = h_name->GetLength() - start_index;
  gc::AllocatorType allocator_type = Runtime::Current()->GetHeap()->GetCurrentAllocator();
  return soa.AddLocalReference<jstring>(
      mirror::String::AllocFromString(soa.Self(), length, h_name, start_index, allocator_type));
}

static jobjectArray Class_getSignatureAnnotation(JNIEnv* env, jobject javaThis) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<1> hs(soa.Self());
  Handle<mirror::Class> klass(hs.NewHandle(DecodeClass(soa, javaThis)));
  if (klass->IsObsoleteObject()) {
    ThrowRuntimeException("Obsolete Object!");
    return nullptr;
  }
  if (klass->IsProxyClass() || klass->GetDexCache() == nullptr) {
    return nullptr;
  }
  return soa.AddLocalReference<jobjectArray>(
      annotations::GetSignatureAnnotationForClass(klass));
}

static jboolean Class_isAnonymousClass(JNIEnv* env, jobject javaThis) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<1> hs(soa.Self());
  Handle<mirror::Class> klass(hs.NewHandle(DecodeClass(soa, javaThis)));
  if (klass->IsObsoleteObject()) {
    ThrowRuntimeException("Obsolete Object!");
    return 0;
  }
  if (klass->IsProxyClass() || klass->GetDexCache() == nullptr) {
    return false;
  }
  ObjPtr<mirror::String> class_name = nullptr;
  if (!annotations::GetInnerClass(klass, &class_name)) {
    return false;
  }
  return class_name == nullptr;
}

static jboolean Class_isRecord0(JNIEnv* env, jobject javaThis) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<1> hs(soa.Self());
  Handle<mirror::Class> klass(hs.NewHandle(DecodeClass(soa, javaThis)));
  return klass->IsRecordClass();
}

static jboolean Class_isDeclaredAnnotationPresent(JNIEnv* env, jobject javaThis,
                                                  jclass annotationType) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<2> hs(soa.Self());
  Handle<mirror::Class> klass(hs.NewHandle(DecodeClass(soa, javaThis)));
  if (klass->IsObsoleteObject()) {
    ThrowRuntimeException("Obsolete Object!");
    return false;
  }
  if (klass->IsProxyClass() || klass->GetDexCache() == nullptr) {
    return false;
  }
  Handle<mirror::Class> annotation_class(hs.NewHandle(soa.Decode<mirror::Class>(annotationType)));
  return annotations::IsClassAnnotationPresent(klass, annotation_class);
}

static jclass Class_getDeclaringClass(JNIEnv* env, jobject javaThis) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<1> hs(soa.Self());
  Handle<mirror::Class> klass(hs.NewHandle(DecodeClass(soa, javaThis)));
  if (klass->IsObsoleteObject()) {
    ThrowRuntimeException("Obsolete Object!");
    return nullptr;
  }
  if (klass->IsProxyClass() || klass->GetDexCache() == nullptr) {
    return nullptr;
  }
  // Return null for anonymous classes.
  if (Class_isAnonymousClass(env, javaThis)) {
    return nullptr;
  }
  return soa.AddLocalReference<jclass>(annotations::GetDeclaringClass(klass));
}

static jclass Class_getNestHostFromAnnotation(JNIEnv* env, jobject javaThis) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<1> hs(soa.Self());
  Handle<mirror::Class> klass(hs.NewHandle(DecodeClass(soa, javaThis)));
  if (klass->IsObsoleteObject()) {
    ThrowRuntimeException("Obsolete Object!");
    return nullptr;
  }
  if (klass->IsProxyClass() || klass->GetDexCache() == nullptr) {
    return nullptr;
  }
  ObjPtr<mirror::Class> hostClass = annotations::GetNestHost(klass);
  if (hostClass == nullptr) {
    return nullptr;
  }
  return soa.AddLocalReference<jclass>(hostClass);
}

static jobjectArray Class_getNestMembersFromAnnotation(JNIEnv* env, jobject javaThis) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<1> hs(soa.Self());
  Handle<mirror::Class> klass(hs.NewHandle(DecodeClass(soa, javaThis)));
  if (klass->IsObsoleteObject()) {
    ThrowRuntimeException("Obsolete Object!");
    return nullptr;
  }
  if (klass->IsProxyClass() || klass->GetDexCache() == nullptr) {
    return nullptr;
  }
  ObjPtr<mirror::ObjectArray<mirror::Class>> classes = annotations::GetNestMembers(klass);
  if (classes == nullptr) {
    return nullptr;
  }
  return soa.AddLocalReference<jobjectArray>(classes);
}

static jobjectArray Class_getRecordAnnotationElement(JNIEnv* env,
                                                     jobject javaThis,
                                                     jstring element_name,
                                                     jclass array_class) {
  ScopedFastNativeObjectAccess soa(env);
  ScopedUtfChars name(env, element_name);
  StackHandleScope<2> hs(soa.Self());
  Handle<mirror::Class> klass(hs.NewHandle(DecodeClass(soa, javaThis)));
  if (!(klass->IsRecordClass())) {
    return nullptr;
  }

  Handle<mirror::Class> a_class(hs.NewHandle(DecodeClass(soa, array_class)));
  ObjPtr<mirror::Object> element_array =
      annotations::getRecordAnnotationElement(klass, a_class, name.c_str());
  if (element_array == nullptr || !(element_array->IsObjectArray())) {
    return nullptr;
  }
  return soa.AddLocalReference<jobjectArray>(element_array);
}

static jobjectArray Class_getPermittedSubclassesFromAnnotation(JNIEnv* env, jobject javaThis) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<1> hs(soa.Self());
  Handle<mirror::Class> klass(hs.NewHandle(DecodeClass(soa, javaThis)));
  if (klass->IsObsoleteObject()) {
    ThrowRuntimeException("Obsolete Object!");
    return nullptr;
  }
  if (klass->IsProxyClass() || klass->GetDexCache() == nullptr) {
    return nullptr;
  }
  ObjPtr<mirror::ObjectArray<mirror::Class>> classes = annotations::GetPermittedSubclasses(klass);
  if (classes == nullptr) {
    return nullptr;
  }
  return soa.AddLocalReference<jobjectArray>(classes);
}

static jobject Class_ensureExtDataPresent(JNIEnv* env, jobject javaThis) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<2> hs(soa.Self());
  Handle<mirror::Class> klass = hs.NewHandle(DecodeClass(soa, javaThis));

  ObjPtr<mirror::Object> extDataPtr =
    mirror::Class::EnsureExtDataPresent(klass, Thread::Current());

  return soa.AddLocalReference<jobject>(extDataPtr);
}

static jobject Class_newInstance(JNIEnv* env, jobject javaThis) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<4> hs(soa.Self());
  Handle<mirror::Class> klass = hs.NewHandle(DecodeClass(soa, javaThis));
  if (klass->IsObsoleteObject()) {
    ThrowRuntimeException("Obsolete Object!");
    return nullptr;
  }
  if (UNLIKELY(klass->GetPrimitiveType() != 0 || klass->IsInterface() || klass->IsArrayClass() ||
               klass->IsAbstract())) {
    soa.Self()->ThrowNewExceptionF("Ljava/lang/InstantiationException;",
                                   "%s cannot be instantiated",
                                   klass->PrettyClass().c_str());
    return nullptr;
  }
  auto caller = hs.NewHandle<mirror::Class>(nullptr);
  // Verify that we can access the class.
  if (!klass->IsPublic()) {
    caller.Assign(GetCallingClass(soa.Self(), 1));
    if (caller != nullptr && !caller->CanAccess(klass.Get())) {
      soa.Self()->ThrowNewExceptionF(
          "Ljava/lang/IllegalAccessException;", "%s is not accessible from %s",
          klass->PrettyClass().c_str(), caller->PrettyClass().c_str());
      return nullptr;
    }
  }
  StackArtMethodHandleScope<1> mhs(soa.Self());
  ReflectiveHandle<ArtMethod> constructor(mhs.NewMethodHandle(klass->GetDeclaredConstructor(
      soa.Self(), ScopedNullHandle<mirror::ObjectArray<mirror::Class>>(), kRuntimePointerSize)));
  if (UNLIKELY(constructor == nullptr) || ShouldDenyAccessToMember(constructor.Get(), soa.Self())) {
    soa.Self()->ThrowNewExceptionF("Ljava/lang/InstantiationException;",
                                   "%s has no zero argument constructor",
                                   klass->PrettyClass().c_str());
    return nullptr;
  }
  // Invoke the string allocator to return an empty string for the string class.
  if (klass->IsStringClass()) {
    gc::AllocatorType allocator_type = Runtime::Current()->GetHeap()->GetCurrentAllocator();
    ObjPtr<mirror::Object> obj = mirror::String::AllocEmptyString(soa.Self(), allocator_type);
    if (UNLIKELY(soa.Self()->IsExceptionPending())) {
      return nullptr;
    } else {
      return soa.AddLocalReference<jobject>(obj);
    }
  }
  auto receiver = hs.NewHandle(klass->AllocObject(soa.Self()));
  if (UNLIKELY(receiver == nullptr)) {
    soa.Self()->AssertPendingOOMException();
    return nullptr;
  }
  // Verify that we can access the constructor.
  ObjPtr<mirror::Class> declaring_class = constructor->GetDeclaringClass();
  if (!constructor->IsPublic()) {
    if (caller == nullptr) {
      caller.Assign(GetCallingClass(soa.Self(), 1));
    }
    if (UNLIKELY(caller != nullptr && !VerifyAccess(receiver.Get(),
                                                    declaring_class,
                                                    constructor->GetAccessFlags(),
                                                    caller.Get()))) {
      soa.Self()->ThrowNewExceptionF(
          "Ljava/lang/IllegalAccessException;", "%s is not accessible from %s",
          constructor->PrettyMethod().c_str(), caller->PrettyClass().c_str());
      return nullptr;
    }
  }
  // Ensure that we are initialized.
  if (UNLIKELY(!declaring_class->IsVisiblyInitialized())) {
    Thread* self = soa.Self();
    Handle<mirror::Class> h_class = hs.NewHandle(declaring_class);
    if (UNLIKELY(!Runtime::Current()->GetClassLinker()->EnsureInitialized(
                      self, h_class, /*can_init_fields=*/ true, /*can_init_parents=*/ true))) {
      DCHECK(self->IsExceptionPending());
      return nullptr;
    }
    DCHECK(h_class->IsInitializing());
  }
  // Invoke the constructor.
  JValue result;
  uint32_t args[1] = { static_cast<uint32_t>(reinterpret_cast<uintptr_t>(receiver.Get())) };
  constructor->Invoke(soa.Self(), args, sizeof(args), &result, "V");
  if (UNLIKELY(soa.Self()->IsExceptionPending())) {
    return nullptr;
  }
  // Constructors are ()V methods, so we shouldn't touch the result of InvokeMethod.
  return soa.AddLocalReference<jobject>(receiver.Get());
}

static JNINativeMethod gMethods[] = {
  FAST_NATIVE_METHOD(Class, classForName,
                "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;"),
  FAST_NATIVE_METHOD(Class, ensureExtDataPresent, "()Ldalvik/system/ClassExt;"),
  FAST_NATIVE_METHOD(Class, getDeclaredAnnotation,
                "(Ljava/lang/Class;)Ljava/lang/annotation/Annotation;"),
  FAST_NATIVE_METHOD(Class, getDeclaredAnnotations, "()[Ljava/lang/annotation/Annotation;"),
  FAST_NATIVE_METHOD(Class, getDeclaredClasses, "()[Ljava/lang/Class;"),
  FAST_NATIVE_METHOD(Class, getDeclaredConstructorInternal,
                "([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;"),
  FAST_NATIVE_METHOD(Class, getDeclaredConstructorsInternal, "(Z)[Ljava/lang/reflect/Constructor;"),
  FAST_NATIVE_METHOD(Class, getDeclaredField, "(Ljava/lang/String;)Ljava/lang/reflect/Field;"),
  FAST_NATIVE_METHOD(Class, getPublicFieldRecursive, "(Ljava/lang/String;)Ljava/lang/reflect/Field;"),
  FAST_NATIVE_METHOD(Class, getDeclaredFields, "()[Ljava/lang/reflect/Field;"),
  FAST_NATIVE_METHOD(Class, getDeclaredFieldsUnchecked, "(Z)[Ljava/lang/reflect/Field;"),
  FAST_NATIVE_METHOD(Class, getDeclaredMethodInternal,
                "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;"),
  FAST_NATIVE_METHOD(Class, getDeclaredMethodsUnchecked,
                "(Z)[Ljava/lang/reflect/Method;"),
  FAST_NATIVE_METHOD(Class, getDeclaringClass, "()Ljava/lang/Class;"),
  FAST_NATIVE_METHOD(Class, getEnclosingClass, "()Ljava/lang/Class;"),
  FAST_NATIVE_METHOD(Class, getEnclosingConstructorNative, "()Ljava/lang/reflect/Constructor;"),
  FAST_NATIVE_METHOD(Class, getEnclosingMethodNative, "()Ljava/lang/reflect/Method;"),
  FAST_NATIVE_METHOD(Class, getInnerClassFlags, "(I)I"),
  FAST_NATIVE_METHOD(Class, getInterfacesInternal, "()[Ljava/lang/Class;"),
  FAST_NATIVE_METHOD(Class, getPrimitiveClass, "(Ljava/lang/String;)Ljava/lang/Class;"),
  FAST_NATIVE_METHOD(Class, getNameNative, "()Ljava/lang/String;"),
  FAST_NATIVE_METHOD(Class, getNestHostFromAnnotation, "()Ljava/lang/Class;"),
  FAST_NATIVE_METHOD(Class, getNestMembersFromAnnotation, "()[Ljava/lang/Class;"),
  FAST_NATIVE_METHOD(Class, getPermittedSubclassesFromAnnotation, "()[Ljava/lang/Class;"),
  FAST_NATIVE_METHOD(Class, getPublicDeclaredFields, "()[Ljava/lang/reflect/Field;"),
  FAST_NATIVE_METHOD(Class, getRecordAnnotationElement, "(Ljava/lang/String;Ljava/lang/Class;)[Ljava/lang/Object;"),
  FAST_NATIVE_METHOD(Class, getSignatureAnnotation, "()[Ljava/lang/String;"),
  FAST_NATIVE_METHOD(Class, getSimpleNameNative, "()Ljava/lang/String;"),
  FAST_NATIVE_METHOD(Class, isAnonymousClass, "()Z"),
  FAST_NATIVE_METHOD(Class, isDeclaredAnnotationPresent, "(Ljava/lang/Class;)Z"),
  FAST_NATIVE_METHOD(Class, isRecord0, "()Z"),
  FAST_NATIVE_METHOD(Class, newInstance, "()Ljava/lang/Object;"),
};

void register_java_lang_Class(JNIEnv* env) {
  REGISTER_NATIVE_METHODS("java/lang/Class");
}

}  // namespace art
