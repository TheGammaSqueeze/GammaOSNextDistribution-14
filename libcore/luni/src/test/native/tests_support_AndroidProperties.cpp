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

#include <jni.h>

#include "android-base/properties.h"
#include "nativehelper/ScopedUtfChars.h"

extern "C"
JNIEXPORT jstring JNICALL Java_tests_support_AndroidProperties_getString(
    JNIEnv* env, jclass, jstring jprop_name, jstring jdef) {

  ScopedUtfChars prop_name(env, jprop_name);
  ScopedUtfChars def(env, jdef);
  std::string prop = android::base::GetProperty(prop_name.c_str(), def.c_str());
  return env->NewStringUTF(prop.c_str());
}

extern "C"
JNIEXPORT jint JNICALL Java_tests_support_AndroidProperties_getInt(
    JNIEnv* env, jclass, jstring jprop_name, jint jdef) {

  ScopedUtfChars prop_name(env, jprop_name);
  return android::base::GetIntProperty(prop_name.c_str(), jdef);
}

