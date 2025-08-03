// Copyright (C) 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#define LOG_TAG "netjniutils"

#include "netjniutils/netjniutils.h"
#include <android-modules-utils/sdk_level.h>

#include <dlfcn.h>
#include <stdbool.h>
#include <string.h>
#include <sys/system_properties.h>

#include <android/api-level.h>
#include <android/log.h>

namespace android {
namespace netjniutils {

namespace {


int GetNativeFileDescriptorWithoutNdk(JNIEnv* env, jobject javaFd) {
  // Prior to Android S, we need to find the descriptor field in the FileDescriptor class. The
  // symbol name has been stable in libcore, but is a private implementation detail.
  // Older libnativehelper_compat_c++ versions had a jniGetFdFromFileDescriptor method, but this
  // was removed in S to replace it with the NDK API in libnativehelper.
  // The code is copied here instead. This code can be removed once R is not supported anymore.
  static const jfieldID descriptorFieldID = [env]() -> jfieldID {
    jclass cls = env->FindClass("java/io/FileDescriptor");
    jfieldID fieldID = env->GetFieldID(cls, "descriptor", "I");
    env->DeleteLocalRef(cls);
    if (fieldID == nullptr) {
      __android_log_print(ANDROID_LOG_FATAL, LOG_TAG, "Failed to get descriptor field.");
    }
    return fieldID;
  }();

  return javaFd != nullptr ? env->GetIntField(javaFd, descriptorFieldID) : -1;
}

int GetNativeFileDescriptorWithNdk(JNIEnv* env, jobject javaFd) {
  // Since Android S, there is an NDK API to get a file descriptor present in libnativehelper.so.
  // libnativehelper is loaded into all processes by the zygote since the zygote uses it
  // to load the Android Runtime and is also a public library (because of the NDK API).
  typedef int (*ndkGetFd_t)(JNIEnv*, jobject);
  static const ndkGetFd_t ndkGetFd = []() -> ndkGetFd_t {
    void* handle = dlopen("libnativehelper.so", RTLD_NOLOAD | RTLD_NODELETE);
    auto ndkGetFd = reinterpret_cast<ndkGetFd_t>(dlsym(handle, "AFileDescriptor_getFd"));
    if (ndkGetFd == nullptr) {
      __android_log_print(ANDROID_LOG_FATAL, LOG_TAG,
                          "Failed to dlsym(AFileDescriptor_getFd): %s", dlerror());
      dlclose(handle);
    }
    return ndkGetFd;
  }();

  return javaFd != nullptr ? ndkGetFd(env, javaFd) : -1;
}

}  //  namespace

int GetNativeFileDescriptor(JNIEnv* env, jobject javaFd) {
  static const bool preferNdkFileDescriptorApi = []() -> bool
   { return android::modules::sdklevel::IsAtLeastS(); }();
  if (preferNdkFileDescriptorApi) {
    return GetNativeFileDescriptorWithNdk(env, javaFd);
  } else {
    return GetNativeFileDescriptorWithoutNdk(env, javaFd);
  }
}

}  // namespace netjniutils
}  // namespace android
