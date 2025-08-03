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

#pragma once

#include <jni.h>

#include "fcp/base/monitoring.h"
#include "fcp/jni/jni_util.h"
#include "fcp/protos/federatedcompute/common.pb.h"

class MoreJniUtil {
 public:
  static jmethodID GetMethodIdOrAbort(JNIEnv *env, jclass clazz,
                                      fcp::jni::JavaMethodSig method) {
    jmethodID id = env->GetMethodID(clazz, method.name, method.signature);
    FCP_CHECK(!CheckForJniException(env));
    FCP_CHECK(id != nullptr);
    return id;
  }

  static bool CheckForJniException(JNIEnv *env) {
    if (env->ExceptionCheck()) {
      env->ExceptionDescribe();
      env->ExceptionClear();
      return true;
    }
    return false;
  }

  static absl::Status GetExceptionStatus(JNIEnv *env, const char *msg) {
    if (env->ExceptionCheck()) {
      FCP_LOG(WARNING) << "GetExceptionStatus [" << msg
                       << "] Java exception follows";
      env->ExceptionDescribe();
      env->ExceptionClear();
      // We gracefully return AbortedError to c++ code instead of crash app. The
      // underlying c++ code only checks if status is ok, if not it will return
      // error status code to java.
      return absl::AbortedError(absl::StrCat("Got Exception when ", msg));
    } else {
      return absl::OkStatus();
    }
  }

  static std::string JStringToString(JNIEnv *env, jstring jstr) {
    if (jstr == nullptr) {
      return std::string();
    }
    const char *cstring = env->GetStringUTFChars(jstr, nullptr);
    FCP_CHECK(!env->ExceptionCheck());
    std::string result(cstring);
    env->ReleaseStringUTFChars(jstr, cstring);
    return result;
  }

  static std::string JByteArrayToString(JNIEnv *env, jbyteArray array) {
    FCP_CHECK(array != nullptr);
    std::string result;
    int array_length = env->GetArrayLength(array);
    FCP_CHECK(!env->ExceptionCheck());

    result.resize(array_length);
    env->GetByteArrayRegion(
        array, 0, array_length,
        reinterpret_cast<jbyte *>(const_cast<char *>(result.data())));
    FCP_CHECK(!env->ExceptionCheck());

    return result;
  }

  static JavaVM *getJavaVm(JNIEnv *env) {
    JavaVM *jvm = nullptr;
    env->GetJavaVM(&jvm);
    FCP_CHECK(jvm != nullptr);
    return jvm;
  }
};
