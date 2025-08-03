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

#ifndef ART_RUNTIME_MIRROR_OBJECT_REFVISITOR_INL_H_
#define ART_RUNTIME_MIRROR_OBJECT_REFVISITOR_INL_H_

#include "object-inl.h"

#include "class-refvisitor-inl.h"
#include "class_loader-inl.h"
#include "dex_cache-inl.h"

namespace art HIDDEN {
namespace mirror {

template <VerifyObjectFlags kVerifyFlags,
          ReadBarrierOption kReadBarrierOption>
static void CheckNoReferenceField(ObjPtr<mirror::Class> klass)
    REQUIRES_SHARED(art::Locks::mutator_lock_) {
  if (!kIsDebugBuild) {
    return;
  }
  CHECK(!klass->IsClassClass<kVerifyFlags>());
  CHECK((!klass->IsObjectArrayClass<kVerifyFlags, kReadBarrierOption>()));
  // String still has instance fields for reflection purposes but these don't exist in
  // actual string instances.
  if (!klass->IsStringClass<kVerifyFlags>()) {
    size_t total_reference_instance_fields = 0;
    ObjPtr<Class> super_class = klass;
    do {
      total_reference_instance_fields +=
          super_class->NumReferenceInstanceFields<kVerifyFlags>();
      super_class = super_class->GetSuperClass<kVerifyFlags, kReadBarrierOption>();
    } while (super_class != nullptr);
    // The only reference field should be the object's class.
    CHECK_EQ(total_reference_instance_fields, 1u);
  }
}

template <VerifyObjectFlags kVerifyFlags>
static void CheckNormalClass(ObjPtr<mirror::Class> klass)
    REQUIRES_SHARED(art::Locks::mutator_lock_) {
  DCHECK(!klass->IsVariableSize<kVerifyFlags>());
  DCHECK(!klass->IsClassClass<kVerifyFlags>());
  DCHECK(!klass->IsStringClass<kVerifyFlags>());
  DCHECK(!klass->IsClassLoaderClass<kVerifyFlags>());
  DCHECK(!klass->IsArrayClass<kVerifyFlags>());
}

template <bool kVisitNativeRoots,
          VerifyObjectFlags kVerifyFlags,
          ReadBarrierOption kReadBarrierOption,
          typename Visitor,
          typename JavaLangRefVisitor>
inline void Object::VisitReferences(const Visitor& visitor,
                                    const JavaLangRefVisitor& ref_visitor) {
  visitor(this, ClassOffset(), /* is_static= */ false);
  ObjPtr<Class> klass = GetClass<kVerifyFlags, kReadBarrierOption>();
  const uint32_t class_flags = klass->GetClassFlags<kVerifyNone>();
  if (LIKELY(class_flags == kClassFlagNormal) || class_flags == kClassFlagRecord) {
    CheckNormalClass<kVerifyFlags>(klass);
    VisitInstanceFieldsReferences<kVerifyFlags, kReadBarrierOption>(klass, visitor);
    return;
  }

  if ((class_flags & kClassFlagNoReferenceFields) != 0) {
    CheckNoReferenceField<kVerifyFlags, kReadBarrierOption>(klass);
    return;
  }

  DCHECK(!klass->IsStringClass<kVerifyFlags>());
  if (class_flags == kClassFlagClass) {
    DCHECK(klass->IsClassClass<kVerifyFlags>());
    ObjPtr<Class> as_klass = AsClass<kVerifyNone>();
    as_klass->VisitReferences<kVisitNativeRoots, kVerifyFlags, kReadBarrierOption>(klass, visitor);
    return;
  }

  if (class_flags == kClassFlagObjectArray) {
    DCHECK((klass->IsObjectArrayClass<kVerifyFlags, kReadBarrierOption>()));
    AsObjectArray<mirror::Object, kVerifyNone>()->VisitReferences(visitor);
    return;
  }

  if ((class_flags & kClassFlagReference) != 0) {
    VisitInstanceFieldsReferences<kVerifyFlags, kReadBarrierOption>(klass, visitor);
    ref_visitor(klass, AsReference<kVerifyFlags, kReadBarrierOption>());
    return;
  }

  if (class_flags == kClassFlagDexCache) {
    DCHECK(klass->IsDexCacheClass<kVerifyFlags>());
    ObjPtr<mirror::DexCache> const dex_cache = AsDexCache<kVerifyFlags, kReadBarrierOption>();
    dex_cache->VisitReferences<kVisitNativeRoots,
                               kVerifyFlags,
                               kReadBarrierOption>(klass, visitor);
    return;
  }

  if (class_flags == kClassFlagClassLoader) {
    DCHECK(klass->IsClassLoaderClass<kVerifyFlags>());
    ObjPtr<mirror::ClassLoader> const class_loader =
        AsClassLoader<kVerifyFlags, kReadBarrierOption>();
    class_loader->VisitReferences<kVisitNativeRoots,
                                  kVerifyFlags,
                                  kReadBarrierOption>(klass, visitor);
    return;
  }

  LOG(FATAL) << "Unexpected class flags: " << std::hex << class_flags
            << " for " << klass->PrettyClass();
}

// Could be called with from-space address of the object as we access klass and
// length (in case of arrays/strings) and we don't want to cause cascading faults.
template <bool kFetchObjSize,
          bool kVisitNativeRoots,
          VerifyObjectFlags kVerifyFlags,
          ReadBarrierOption kReadBarrierOption,
          typename Visitor>
inline size_t Object::VisitRefsForCompaction(const Visitor& visitor,
                                             MemberOffset begin,
                                             MemberOffset end) {
  constexpr VerifyObjectFlags kSizeOfFlags = RemoveThisFlags(kVerifyFlags);
  size_t size;
  // We want to continue using pre-compact klass to avoid cascading faults.
  ObjPtr<Class> klass = GetClass<kVerifyFlags, kReadBarrierOption>();
  DCHECK(klass != nullptr) << "obj=" << this;
  const uint32_t class_flags = klass->GetClassFlags<kVerifyNone>();
  if (LIKELY(class_flags == kClassFlagNormal) || class_flags == kClassFlagRecord) {
    CheckNormalClass<kVerifyFlags>(klass);
    VisitInstanceFieldsReferences<kVerifyFlags, kReadBarrierOption>(klass, visitor);
    size = kFetchObjSize ? klass->GetObjectSize<kSizeOfFlags>() : 0;
  } else if ((class_flags & kClassFlagNoReferenceFields) != 0) {
    if ((class_flags & kClassFlagString) != 0) {
      size = kFetchObjSize ? static_cast<String*>(this)->SizeOf<kSizeOfFlags>() : 0;
    } else if (klass->IsArrayClass<kVerifyFlags>()) {
      // TODO: We can optimize this by implementing a SizeOf() version which takes
      // component-size-shift as an argument, thereby avoiding multiple loads of
      // component_type.
      size = kFetchObjSize
             ? static_cast<Array*>(this)->SizeOf<kSizeOfFlags, kReadBarrierOption>()
             : 0;
    } else {
      DCHECK_EQ(class_flags, kClassFlagNoReferenceFields)
          << "class_flags: " << std::hex << class_flags;
      // Only possibility left is of a normal klass instance with no references.
      size = kFetchObjSize ? klass->GetObjectSize<kSizeOfFlags>() : 0;
    }
  } else if (class_flags == kClassFlagClass) {
    DCHECK(klass->IsClassClass<kVerifyFlags>());
    ObjPtr<Class> as_klass = ObjPtr<Class>::DownCast(this);
    as_klass->VisitReferences<kVisitNativeRoots, kVerifyFlags, kReadBarrierOption>(klass,
                                                                                   visitor);
    size = kFetchObjSize ? as_klass->SizeOf<kSizeOfFlags>() : 0;
  } else if (class_flags == kClassFlagObjectArray) {
    ObjPtr<ObjectArray<Object>> obj_arr = ObjPtr<ObjectArray<Object>>::DownCast(this);
    obj_arr->VisitReferences(visitor, begin, end);
    size = kFetchObjSize ?
               obj_arr->SizeOf<kSizeOfFlags, kReadBarrierOption, /*kIsObjArray*/ true>() :
               0;
  } else if ((class_flags & kClassFlagReference) != 0) {
    VisitInstanceFieldsReferences<kVerifyFlags, kReadBarrierOption>(klass, visitor);
    // Visit referent also as this is about updating the reference only.
    // There is no reference processing happening here.
    visitor(this, mirror::Reference::ReferentOffset(), /* is_static= */ false);
    size = kFetchObjSize ? klass->GetObjectSize<kSizeOfFlags>() : 0;
  } else if (class_flags == kClassFlagDexCache) {
    DCHECK(klass->IsDexCacheClass<kVerifyFlags>());
    ObjPtr<DexCache> const dex_cache = ObjPtr<DexCache>::DownCast(this);
    dex_cache->VisitReferences<kVisitNativeRoots,
                               kVerifyFlags,
                               kReadBarrierOption>(klass, visitor);
    size = kFetchObjSize ? klass->GetObjectSize<kSizeOfFlags>() : 0;
  } else if (class_flags == kClassFlagClassLoader) {
    DCHECK(klass->IsClassLoaderClass<kVerifyFlags>());
    ObjPtr<ClassLoader> const class_loader = ObjPtr<ClassLoader>::DownCast(this);
    class_loader->VisitReferences<kVisitNativeRoots,
                                  kVerifyFlags,
                                  kReadBarrierOption>(klass, visitor);
    size = kFetchObjSize ? klass->GetObjectSize<kSizeOfFlags>() : 0;
  } else {
    LOG(FATAL) << "Unexpected class flags: " << std::hex << class_flags
               << " for " << klass->PrettyClass();
    size = -1;
  }
  visitor(this, ClassOffset(), /* is_static= */ false);
  return size;
}

}  // namespace mirror
}  // namespace art

#endif  // ART_RUNTIME_MIRROR_OBJECT_REFVISITOR_INL_H_
