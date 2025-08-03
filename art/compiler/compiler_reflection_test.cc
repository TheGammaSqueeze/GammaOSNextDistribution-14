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

#include "reflection.h"

#include "base/macros.h"
#include "class_linker.h"
#include "common_compiler_test.h"
#include "handle_scope-inl.h"
#include "jni/jni_internal.h"
#include "mirror/class.h"
#include "mirror/class_loader.h"

namespace art HIDDEN {

class CompilerReflectionTest : public CommonCompilerTest {};

TEST_F(CompilerReflectionTest, StaticMainMethod) {
  ScopedObjectAccess soa(Thread::Current());
  jobject jclass_loader = LoadDex("Main");
  StackHandleScope<1> hs(soa.Self());
  Handle<mirror::ClassLoader> class_loader(
      hs.NewHandle(soa.Decode<mirror::ClassLoader>(jclass_loader)));

  ObjPtr<mirror::Class> klass = class_linker_->FindClass(soa.Self(), "LMain;", class_loader);
  ASSERT_TRUE(klass != nullptr);

  ArtMethod* method = klass->FindClassMethod("main",
                                             "([Ljava/lang/String;)V",
                                             kRuntimePointerSize);
  ASSERT_TRUE(method != nullptr);
  ASSERT_TRUE(method->IsStatic());

  CompileMethod(method);

  // Start runtime.
  bool started = runtime_->Start();
  CHECK(started);
  soa.Self()->TransitionFromSuspendedToRunnable();

  jvalue args[1];
  args[0].l = nullptr;
  InvokeWithJValues(soa, nullptr, jni::EncodeArtMethod(method), args);
}

}  // namespace art
