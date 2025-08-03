/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <stdio.h>

#include <vector>

#include "android-base/logging.h"
#include "android-base/macros.h"
#include "jni.h"
#include "jvmti.h"

// Test infrastructure
#include "jvmti_helper.h"
#include "scoped_local_ref.h"
#include "test_env.h"

namespace art {
namespace Test2005PauseAllRedefineMultithreaded {

static constexpr jlong kRedefinedObjectTag = 0xDEADBEEF;

extern "C" JNIEXPORT void JNICALL
Java_art_Test2005_UpdateFieldValuesAndResumeThreads(JNIEnv* env,
                                                    [[maybe_unused]] jclass klass,
                                                    jobjectArray threads_arr,
                                                    jclass redefined_class,
                                                    jobjectArray new_fields,
                                                    jstring default_val) {
  std::vector<jthread> threads;
  threads.reserve(env->GetArrayLength(threads_arr));
  for (jint i = 0; i < env->GetArrayLength(threads_arr); i++) {
    threads.push_back(env->GetObjectArrayElement(threads_arr, i));
  }
  std::vector<jfieldID> fields;
  fields.reserve(env->GetArrayLength(new_fields));
  for (jint i = 0; i < env->GetArrayLength(new_fields); i++) {
    fields.push_back(env->FromReflectedField(env->GetObjectArrayElement(new_fields, i)));
  }
  // Tag every instance of the redefined class with kRedefinedObjectTag
  CHECK_EQ(jvmti_env->IterateOverInstancesOfClass(
               redefined_class,
               JVMTI_HEAP_OBJECT_EITHER,
               []([[maybe_unused]] jlong class_tag,
                  [[maybe_unused]] jlong size,
                  jlong* tag_ptr,
                  [[maybe_unused]] void* user_data) -> jvmtiIterationControl {
                 *tag_ptr = kRedefinedObjectTag;
                 return JVMTI_ITERATION_CONTINUE;
               },
               nullptr),
           JVMTI_ERROR_NONE);
  jobject* objs;
  jint cnt;
  // Get the objects.
  CHECK_EQ(jvmti_env->GetObjectsWithTags(1, &kRedefinedObjectTag, &cnt, &objs, nullptr),
           JVMTI_ERROR_NONE);
  // Set every field that's null
  for (jint i = 0; i < cnt; i++) {
    jobject obj = objs[i];
    for (jfieldID field : fields) {
      if (ScopedLocalRef<jobject>(env, env->GetObjectField(obj, field)).get() == nullptr) {
        env->SetObjectField(obj, field, default_val);
      }
    }
  }
  LOG(INFO) << "Setting " << cnt << " objects with default values";
  if (!threads.empty()) {
    std::vector<jvmtiError> errs(threads.size(), JVMTI_ERROR_NONE);
    CHECK_EQ(jvmti_env->ResumeThreadList(threads.size(), threads.data(), errs.data()),
             JVMTI_ERROR_NONE);
  }
  jvmti_env->Deallocate(reinterpret_cast<unsigned char*>(objs));
}

extern "C" JNIEXPORT jobject JNICALL
Java_Main_fastNativeSleepAndReturnInteger42(JNIEnv* env, [[maybe_unused]] jclass klass) {
  jclass integer_class = env->FindClass("java/lang/Integer");
  CHECK(integer_class != nullptr);
  jmethodID integer_value_of =
      env->GetStaticMethodID(integer_class, "valueOf", "(I)Ljava/lang/Integer;");
  CHECK(integer_value_of != nullptr);
  jobject value = env->CallStaticObjectMethod(integer_class, integer_value_of, 42);
  CHECK(value != nullptr);
  // Sleep for 500ms, blocking thread suspension (this method is @FastNative).
  // Except for some odd thread timing, this should ensure that the suspend
  // request from the redefinition thread is seen by the suspend check in the
  // JNI stub when we exit this function and then processed with the JNI stub
  // still on the stack. The instrumentation previously erroneously
  // intercepted returning to the JNI stub and the "instrumentation exit"
  // handler treated the return value `jobject` as `mirror::Object*`.
  usleep(500000);
  return value;
}

}  // namespace Test2005PauseAllRedefineMultithreaded
}  // namespace art
