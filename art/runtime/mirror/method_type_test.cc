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

#include "method_type.h"

#include <string>
#include <vector>

#include "class-inl.h"
#include "class_linker-inl.h"
#include "class_loader.h"
#include "class_root-inl.h"
#include "common_runtime_test.h"
#include "handle_scope-inl.h"
#include "object_array-alloc-inl.h"
#include "object_array-inl.h"
#include "scoped_thread_state_change-inl.h"

namespace art HIDDEN {
namespace mirror {

class MethodTypeTest : public CommonRuntimeTest {};

static std::string FullyQualifiedType(const std::string& shorthand) {
  return "Ljava/lang/" + shorthand + ";";
}

ObjPtr<mirror::Class> FindClass(Thread* self, ClassLinker* const cl, const std::string& shorthand)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  StackHandleScope<1> hs(self);
  Handle<mirror::ClassLoader> boot_class_loader = hs.NewHandle<mirror::ClassLoader>(nullptr);
  if (shorthand.size() == 1) {
    return cl->FindSystemClass(self, shorthand.c_str());
  } else if (shorthand.find('/') == std::string::npos) {
    return cl->FindClass(self, FullyQualifiedType(shorthand).c_str(), boot_class_loader);
  } else {
    return cl->FindClass(self, shorthand.c_str(), boot_class_loader);
  }
}

static ObjPtr<mirror::MethodType> CreateMethodType(const std::string& return_type,
                                                   const std::vector<std::string>& param_types) {
  Runtime* const runtime = Runtime::Current();
  ClassLinker* const class_linker = runtime->GetClassLinker();
  Thread* const self = Thread::Current();

  ScopedObjectAccess soa(self);
  StackHandleScope<2> hs(soa.Self());

  Handle<mirror::Class> return_clazz = hs.NewHandle(FindClass(self, class_linker, return_type));
  CHECK(return_clazz != nullptr);

  ObjPtr<mirror::Class> class_array_type =
      GetClassRoot<mirror::ObjectArray<mirror::Class>>(class_linker);
  Handle<mirror::ObjectArray<mirror::Class>> param_classes = hs.NewHandle(
      mirror::ObjectArray<mirror::Class>::Alloc(self, class_array_type, param_types.size()));
  for (uint32_t i = 0; i < param_types.size(); ++i) {
    ObjPtr<mirror::Class> param = FindClass(self, class_linker, param_types[i]);
    CHECK(!param.IsNull());
    param_classes->Set(i, param);
  }

  return mirror::MethodType::Create(self, return_clazz, param_classes);
}


TEST_F(MethodTypeTest, IsExactMatch) {
  ScopedObjectAccess soa(Thread::Current());
  {
    StackHandleScope<2> hs(soa.Self());
    Handle<mirror::MethodType> mt1 = hs.NewHandle(CreateMethodType("String", { "Integer" }));
    Handle<mirror::MethodType> mt2 = hs.NewHandle(CreateMethodType("String", { "Integer" }));
    ASSERT_TRUE(mt1->IsExactMatch(mt2.Get()));
  }

  // Mismatched return type.
  {
    StackHandleScope<2> hs(soa.Self());
    Handle<mirror::MethodType> mt1 = hs.NewHandle(CreateMethodType("String", { "Integer" }));
    Handle<mirror::MethodType> mt2 = hs.NewHandle(CreateMethodType("Integer", { "Integer" }));
    ASSERT_FALSE(mt1->IsExactMatch(mt2.Get()));
  }

  // Mismatched param types.
  {
    StackHandleScope<2> hs(soa.Self());
    Handle<mirror::MethodType> mt1 = hs.NewHandle(CreateMethodType("String", { "Integer" }));
    Handle<mirror::MethodType> mt2 = hs.NewHandle(CreateMethodType("String", { "String" }));
    ASSERT_FALSE(mt1->IsExactMatch(mt2.Get()));
  }

  // Wrong number of param types.
  {
    StackHandleScope<2> hs(soa.Self());
    Handle<mirror::MethodType> mt1 = hs.NewHandle(
        CreateMethodType("String", { "String", "String" }));
    Handle<mirror::MethodType> mt2 = hs.NewHandle(CreateMethodType("String", { "String" }));
    ASSERT_FALSE(mt1->IsExactMatch(mt2.Get()));
  }
}

TEST_F(MethodTypeTest, IsInPlaceConvertible) {
  ScopedObjectAccess soa(Thread::Current());

  // Call site has void return type, value is discarded.
  {
    StackHandleScope<2> hs(soa.Self());
    Handle<mirror::MethodType> cs = hs.NewHandle(CreateMethodType("V", { "Integer" }));
    Handle<mirror::MethodType> mh = hs.NewHandle(CreateMethodType("String", { "Integer" }));
    ASSERT_TRUE(cs->IsInPlaceConvertible(mh.Get()));
  }

  // MethodHandle has void return type, value is required
  {
    StackHandleScope<2> hs(soa.Self());
    Handle<mirror::MethodType> cs = hs.NewHandle(CreateMethodType("String", { "Integer" }));
    Handle<mirror::MethodType> mh = hs.NewHandle(CreateMethodType("V", { "Integer" }));
    ASSERT_FALSE(cs->IsInPlaceConvertible(mh.Get()));
  }

  // Assignable Reference Types
  {
    StackHandleScope<2> hs(soa.Self());
    Handle<mirror::MethodType> cs = hs.NewHandle(CreateMethodType("Object", { "Integer" }));
    Handle<mirror::MethodType> mh = hs.NewHandle(CreateMethodType("String", { "Object" }));
    ASSERT_TRUE(cs->IsInPlaceConvertible(mh.Get()));
  }

  // Not assignable Reference Types
  {
    StackHandleScope<2> hs(soa.Self());
    Handle<mirror::MethodType> cs = hs.NewHandle(CreateMethodType("Integer", { "Object" }));
    Handle<mirror::MethodType> mh = hs.NewHandle(CreateMethodType("Object", { "String" }));
    ASSERT_FALSE(cs->IsInPlaceConvertible(mh.Get()));
  }

  // Widenable primitives
  {
    StackHandleScope<2> hs(soa.Self());
    Handle<mirror::MethodType> cs = hs.NewHandle(CreateMethodType("I", { "B", "C", "S" }));
    Handle<mirror::MethodType> mh = hs.NewHandle(CreateMethodType("S", { "I", "I", "I" }));
    ASSERT_TRUE(cs->IsInPlaceConvertible(mh.Get()));
  }

  // Non-widenable primitives
  {
    StackHandleScope<2> hs(soa.Self());
    Handle<mirror::MethodType> cs = hs.NewHandle(CreateMethodType("V", { "Z" }));
    Handle<mirror::MethodType> mh = hs.NewHandle(CreateMethodType("V", { "I" }));
    ASSERT_FALSE(cs->IsInPlaceConvertible(mh.Get()));
  }
  {
    StackHandleScope<2> hs(soa.Self());
    Handle<mirror::MethodType> cs = hs.NewHandle(CreateMethodType("V", { "I" }));
    Handle<mirror::MethodType> mh = hs.NewHandle(CreateMethodType("V", { "Z" }));
    ASSERT_FALSE(cs->IsInPlaceConvertible(mh.Get()));
  }
  {
    StackHandleScope<2> hs(soa.Self());
    Handle<mirror::MethodType> cs = hs.NewHandle(CreateMethodType("V", { "S" }));
    Handle<mirror::MethodType> mh = hs.NewHandle(CreateMethodType("V", { "C" }));
    ASSERT_FALSE(cs->IsInPlaceConvertible(mh.Get()));
    ASSERT_FALSE(mh->IsInPlaceConvertible(cs.Get()));
  }
  {
    StackHandleScope<2> hs(soa.Self());
    Handle<mirror::MethodType> cs = hs.NewHandle(CreateMethodType("V", { "C" }));
    Handle<mirror::MethodType> mh = hs.NewHandle(CreateMethodType("V", { "S" }));
    ASSERT_FALSE(cs->IsInPlaceConvertible(mh.Get()));
  }
  {
    StackHandleScope<2> hs(soa.Self());
    Handle<mirror::MethodType> cs = hs.NewHandle(CreateMethodType("V", { "I" }));
    Handle<mirror::MethodType> mh = hs.NewHandle(CreateMethodType("V", { "J" }));
    ASSERT_FALSE(cs->IsInPlaceConvertible(mh.Get()));
  }
  {
    StackHandleScope<2> hs(soa.Self());
    Handle<mirror::MethodType> cs = hs.NewHandle(CreateMethodType("V", { "F" }));
    Handle<mirror::MethodType> mh = hs.NewHandle(CreateMethodType("V", { "D" }));
    ASSERT_FALSE(cs->IsInPlaceConvertible(mh.Get()));
  }
  {
    StackHandleScope<2> hs(soa.Self());
    Handle<mirror::MethodType> cs = hs.NewHandle(CreateMethodType("V", { "D" }));
    Handle<mirror::MethodType> mh = hs.NewHandle(CreateMethodType("V", { "F" }));
    ASSERT_FALSE(cs->IsInPlaceConvertible(mh.Get()));
  }
  {
    StackHandleScope<2> hs(soa.Self());
    Handle<mirror::MethodType> cs = hs.NewHandle(CreateMethodType("I", {}));
    Handle<mirror::MethodType> mh = hs.NewHandle(CreateMethodType("Z", {}));
    ASSERT_FALSE(cs->IsInPlaceConvertible(mh.Get()));
  }
}

}  // namespace mirror
}  // namespace art
