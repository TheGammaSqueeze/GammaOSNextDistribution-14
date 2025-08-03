/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include "dalvik_system_DexFile.h"

#include <memory>

#include "class_loader_context.h"
#include "class_root-inl.h"
#include "mirror/object_array-alloc-inl.h"
#include "native_util.h"
#include "nativehelper/jni_macros.h"
#include "thread-inl.h"

namespace art HIDDEN {

static bool append_string(Thread* self,
                          Handle<mirror::ObjectArray<mirror::String>> array,
                          uint32_t& i,
                          const std::string& string) REQUIRES_SHARED(Locks::mutator_lock_) {
  ObjPtr<mirror::String> ostring = mirror::String::AllocFromModifiedUtf8(self, string.c_str());
  if (ostring == nullptr) {
    DCHECK(self->IsExceptionPending());
    return false;
  }
  // We're initializing a newly allocated array object, so we do not need to record that under
  // a transaction. If the transaction is aborted, the whole object shall be unreachable.
  array->SetWithoutChecks</*kTransactionActive=*/ false, /*kCheckTransaction=*/ false>(i, ostring);
  ++i;
  return true;
}

static jobjectArray BaseDexClassLoader_computeClassLoaderContextsNative(JNIEnv* env,
                                                                        jobject class_loader) {
  CHECK(class_loader != nullptr);
  std::map<std::string, std::string> context_map =
      ClassLoaderContext::EncodeClassPathContextsForClassLoader(class_loader);
  Thread* self = Thread::ForEnv(env);
  ScopedObjectAccess soa(self);
  StackHandleScope<1u> hs(self);
  Handle<mirror::ObjectArray<mirror::String>> array = hs.NewHandle(
      mirror::ObjectArray<mirror::String>::Alloc(
          self, GetClassRoot<mirror::ObjectArray<mirror::String>>(), 2 * context_map.size()));
  if (array == nullptr) {
    DCHECK(self->IsExceptionPending());
    return nullptr;
  }
  uint32_t i = 0;
  for (const auto& classpath_to_context : context_map) {
    const std::string& classpath = classpath_to_context.first;
    const std::string& context = classpath_to_context.second;
    if (!append_string(self, array, i, classpath) || !append_string(self, array, i, context)) {
      return nullptr;
    }
  }
  return soa.AddLocalReference<jobjectArray>(array.Get());
}

static JNINativeMethod gMethods[] = {
  NATIVE_METHOD(BaseDexClassLoader, computeClassLoaderContextsNative,
                "()[Ljava/lang/String;"),
};

void register_dalvik_system_BaseDexClassLoader(JNIEnv* env) {
  REGISTER_NATIVE_METHODS("dalvik/system/BaseDexClassLoader");
}

}  // namespace art
