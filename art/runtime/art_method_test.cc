/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <type_traits>

#include "art_method-alloc-inl.h"

#include "base/casts.h"
#include "common_runtime_test.h"
#include "mirror/class-alloc-inl.h"
#include "well_known_classes.h"

namespace art HIDDEN {

namespace {
// Helper function to avoid `ASSERT_EQ` with floating point types.
int32_t ToIntegralType(float value) { return bit_cast<int32_t>(value); }
int64_t ToIntegralType(double value) { return bit_cast<int64_t>(value); }
template <typename T> T ToIntegralType(T value) { return value; }
}

class ArtMethodTest : public CommonRuntimeTest {
 protected:
  ArtMethodTest() {
    use_boot_image_ = true;  // Make the Runtime creation cheaper.
  }

  // Test primitive type boxing and unboxing.
  //
  // This provides basic checks that the translation of the compile-time shorty
  // to argument types and return type are correct and that values are passed
  // correctly for these single-argument calls (`ArtMethod::InvokeStatic()` with
  // primitive args and `ArtMethod::InvokeInstance()` with a reference arg).
  template <typename Type, char kPrimitive>
  void TestBoxUnbox(ArtMethod* value_of, const char* unbox_name, Type value) {
    Thread* self = Thread::Current();
    ScopedObjectAccess soa(self);
    ASSERT_STREQ(value_of->GetName(), "valueOf");
    std::string unbox_signature = std::string("()") + kPrimitive;
    ArtMethod* unbox_method = value_of->GetDeclaringClass()->FindClassMethod(
        unbox_name, unbox_signature, kRuntimePointerSize);
    ASSERT_TRUE(unbox_method != nullptr);
    ASSERT_FALSE(unbox_method->IsStatic());
    ASSERT_TRUE(value_of->GetDeclaringClass()->IsFinal());

    static_assert(std::is_same_v<ObjPtr<mirror::Object> (ArtMethod::*)(Thread*, Type),
                                 decltype(&ArtMethod::InvokeStatic<'L', kPrimitive>)>);
    StackHandleScope<1u> hs(self);
    Handle<mirror::Object> boxed =
        hs.NewHandle(value_of->InvokeStatic<'L', kPrimitive>(self, value));
    ASSERT_TRUE(boxed != nullptr);
    ASSERT_OBJ_PTR_EQ(boxed->GetClass(), value_of->GetDeclaringClass());
    static_assert(std::is_same_v<Type (ArtMethod::*)(Thread*, ObjPtr<mirror::Object>),
                                 decltype(&ArtMethod::InvokeInstance<kPrimitive>)>);
    // Exercise both `InvokeInstance()` and `InvokeFinal()` (boxing classes are final).
    Type unboxed1 = unbox_method->InvokeInstance<kPrimitive>(self, boxed.Get());
    ASSERT_EQ(ToIntegralType(value), ToIntegralType(unboxed1));
    Type unboxed2 = unbox_method->InvokeFinal<kPrimitive>(self, boxed.Get());
    ASSERT_EQ(ToIntegralType(value), ToIntegralType(unboxed2));
  }
};

TEST_F(ArtMethodTest, BoxUnboxBoolean) {
  TestBoxUnbox<bool, 'Z'>(WellKnownClasses::java_lang_Boolean_valueOf, "booleanValue", true);
}

TEST_F(ArtMethodTest, BoxUnboxByte) {
  TestBoxUnbox<int8_t, 'B'>(WellKnownClasses::java_lang_Byte_valueOf, "byteValue", -12);
}

TEST_F(ArtMethodTest, BoxUnboxChar) {
  TestBoxUnbox<uint16_t, 'C'>(WellKnownClasses::java_lang_Character_valueOf, "charValue", 0xffaa);
}

TEST_F(ArtMethodTest, BoxUnboxShort) {
  TestBoxUnbox<int16_t, 'S'>(WellKnownClasses::java_lang_Short_valueOf, "shortValue", -0x1234);
}

TEST_F(ArtMethodTest, BoxUnboxInt) {
  TestBoxUnbox<int32_t, 'I'>(WellKnownClasses::java_lang_Integer_valueOf, "intValue", -0x12345678);
}

TEST_F(ArtMethodTest, BoxUnboxLong) {
  TestBoxUnbox<int64_t, 'J'>(
      WellKnownClasses::java_lang_Long_valueOf, "longValue", UINT64_C(-0x1234567887654321));
}

TEST_F(ArtMethodTest, BoxUnboxFloat) {
  TestBoxUnbox<float, 'F'>(WellKnownClasses::java_lang_Float_valueOf, "floatValue", -2.0f);
}

TEST_F(ArtMethodTest, BoxUnboxDouble) {
  TestBoxUnbox<double, 'D'>(WellKnownClasses::java_lang_Double_valueOf, "doubleValue", 8.0);
}

TEST_F(ArtMethodTest, ArrayList) {
  Thread* self = Thread::Current();
  ClassLinker* class_linker = Runtime::Current()->GetClassLinker();
  ScopedObjectAccess soa(self);
  StackHandleScope<4u> hs(self);
  Handle<mirror::Class> list_class =
      hs.NewHandle(class_linker->FindSystemClass(self, "Ljava/util/List;"));
  ASSERT_TRUE(list_class != nullptr);
  Handle<mirror::Class> abstract_list_class =
      hs.NewHandle(class_linker->FindSystemClass(self, "Ljava/util/AbstractList;"));
  ASSERT_TRUE(abstract_list_class != nullptr);
  Handle<mirror::Class> array_list_class =
      hs.NewHandle(class_linker->FindSystemClass(self, "Ljava/util/ArrayList;"));
  ASSERT_TRUE(array_list_class != nullptr);
  ASSERT_TRUE(abstract_list_class->Implements(list_class.Get()));
  ASSERT_TRUE(array_list_class->IsSubClass(abstract_list_class.Get()));

  ArtMethod* init = array_list_class->FindClassMethod("<init>", "()V", kRuntimePointerSize);
  ASSERT_TRUE(init != nullptr);
  ArtMethod* array_list_size_method =
      array_list_class->FindClassMethod("size", "()I", kRuntimePointerSize);
  DCHECK(array_list_size_method != nullptr);
  ArtMethod* abstract_list_size_method =
      abstract_list_class->FindClassMethod("size", "()I", kRuntimePointerSize);
  DCHECK(abstract_list_size_method != nullptr);
  ArtMethod* list_size_method =
      list_class->FindInterfaceMethod("size", "()I", kRuntimePointerSize);
  DCHECK(list_size_method != nullptr);

  Handle<mirror::Object> array_list = init->NewObject<>(hs, self);
  ASSERT_FALSE(self->IsExceptionPending());
  ASSERT_TRUE(array_list != nullptr);

  // Invoke `ArrayList.size()` directly, with virtual dispatch from
  // `AbstractList.size()` and with interface dispatch from `List.size()`.
  int32_t size = array_list_size_method->InvokeInstance<'I'>(self, array_list.Get());
  ASSERT_FALSE(self->IsExceptionPending());
  ASSERT_EQ(0, size);
  size = abstract_list_size_method->InvokeVirtual<'I'>(self, array_list.Get());
  ASSERT_FALSE(self->IsExceptionPending());
  ASSERT_EQ(0, size);
  size = list_size_method->InvokeInterface<'I'>(self, array_list.Get());
  ASSERT_FALSE(self->IsExceptionPending());
  ASSERT_EQ(0, size);

  // Try to invoke abstract method `AbstractList.size()` directly.
  abstract_list_size_method->InvokeInstance<'I'>(self, array_list.Get());
  ASSERT_TRUE(self->IsExceptionPending());
  self->ClearException();
}

}  // namespace art
