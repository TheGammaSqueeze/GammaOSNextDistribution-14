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

#include <android/set_abort_message.h>
#include <dlfcn.h>
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>

extern "C" const char* __progname;

extern "C" jstring Java_com_android_bionic_1app_BionicAppTest_progname(JNIEnv* env) {
  return env->NewStringUTF(__progname);
}

extern "C" int main(int argc, char* argv[]);

extern "C" jint Java_com_android_bionic_1app_BionicAppTest_callMainDirect(JNIEnv* env __unused) {
  return main(0, nullptr);
}

// Having a variant of the test that uses dlsym is helpful to ensure that main isn't accidentally
// hidden in the app's DSO.
extern "C" jint Java_com_android_bionic_1app_BionicAppTest_callMainDlsym(JNIEnv* env __unused) {
  auto pmain = reinterpret_cast<int(*)(int, char**)>(dlsym(RTLD_DEFAULT, "main"));
  if (pmain == nullptr) {
    char msg[256];
    snprintf(msg, sizeof(msg), "dlsym(RTLD_DEFAULT, \"main\") was nullptr: %s", dlerror());
    android_set_abort_message(msg);
    abort();
  }
  return pmain(0, nullptr);
}
