/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <jni.h>
#include <jvmti.h>

#include <string_view>

namespace ddms_agent {

using DisablePolicy = jvmtiError (*)(jvmtiEnv*);
static constexpr std::string_view kDisablePolicyName =
    "com.android.art.misc.disable_hidden_api_enforcement_policy";

static jvmtiEnv* jvmti_env;
DisablePolicy disable_ext;

template <typename T>
static void Dealloc(T* t) {
  jvmti_env->Deallocate(reinterpret_cast<unsigned char*>(t));
}

template <typename T, typename... Rest>
static void Dealloc(T* t, Rest... rs) {
  Dealloc(t);
  Dealloc(rs...);
}

static void DeallocParams(jvmtiParamInfo* params, jint n_params) {
  for (jint i = 0; i < n_params; i++) {
    Dealloc(params[i].name);
  }
}

void* GetExtension(const std::string_view& name) {
  // Get the extensions.
  jint n_ext = 0;
  void* result = nullptr;
  jvmtiExtensionFunctionInfo* infos = nullptr;
  if (jvmti_env->GetExtensionFunctions(&n_ext, &infos) != JVMTI_ERROR_NONE) {
    return nullptr;
  }
  for (jint i = 0; i < n_ext; i++) {
    jvmtiExtensionFunctionInfo* cur_info = &infos[i];
    if (name == std::string_view(cur_info->id)) {
      result = reinterpret_cast<void*>(cur_info->func);
    }
    // Cleanup the cur_info
    DeallocParams(cur_info->params, cur_info->param_count);
    Dealloc(cur_info->id, cur_info->short_description, cur_info->params, cur_info->errors);
  }
  // Cleanup the array.
  Dealloc(infos);
  if (result == nullptr) {
    return nullptr;
  }
  return result;
}

extern "C" JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM* vm, char*, void*) {
  vm->GetEnv(reinterpret_cast<void**>(&jvmti_env), JVMTI_VERSION_1_0);
  disable_ext = reinterpret_cast<DisablePolicy>(GetExtension(kDisablePolicyName));
  if (disable_ext == nullptr) {
    return JNI_ERR;
  }
  return JNI_OK;
}

extern "C" JNIEXPORT jint JNICALL
Java_android_jdwptunnel_sampleapp_ddms_DdmsSampleDeviceActivity_ForceNoHiddenapi(JNIEnv*, jclass) {
  return disable_ext(jvmti_env);
}

}  // namespace ddms_agent