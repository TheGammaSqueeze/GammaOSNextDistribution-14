/*
 * Copyright (C) 2023 The Android Open Source Project
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

#define LOG_TAG "derive_sdk"

#include <sstream>
#include "android-base/logging.h"
#include "derive_sdk.h"
#include "jni.h"

namespace android {
namespace derivesdk {

constexpr const char* DERIVE_SDK_CLASS_NAME = "com/android/os/ext/testing/DeriveSdk";

jstring com_android_os_ext_testing_DeriveSdk_dump(JNIEnv* env) {
  std::stringstream stream;
  PrintDump("/apex", stream);

  return env->NewStringUTF(stream.str().c_str());
}

const JNINativeMethod methods[] = {
    {"native_dump", "()Ljava/lang/String;",
     reinterpret_cast<void*>(com_android_os_ext_testing_DeriveSdk_dump)},
};

void register_com_android_os_ext_testing_DeriveSdk(JNIEnv* env) {
  auto gDeriveSdkClass =
      static_cast<jclass>(env->NewGlobalRef(env->FindClass(DERIVE_SDK_CLASS_NAME)));

  if (gDeriveSdkClass == nullptr) {
    LOG(FATAL) << "Unable to find class : " << DERIVE_SDK_CLASS_NAME;
  }

  if (env->RegisterNatives(gDeriveSdkClass, methods, sizeof(methods) / sizeof(methods[0])) < 0) {
    LOG(FATAL) << "Unable to register native methods";
  }
}
}  // namespace derivesdk
}  // namespace android

extern "C" jint JNI_OnLoad(JavaVM* vm, void* /* reserved */) {
  JNIEnv* env;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
    LOG(ERROR) << "ERROR: GetEnv failed";
    return JNI_ERR;
  }

  android::derivesdk::register_com_android_os_ext_testing_DeriveSdk(env);

  return JNI_VERSION_1_6;
}
