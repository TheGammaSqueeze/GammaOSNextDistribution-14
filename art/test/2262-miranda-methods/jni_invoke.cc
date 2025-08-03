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

#include <jni.h>

#include "jni/java_vm_ext.h"
#include "runtime.h"

namespace art {

extern "C" JNIEXPORT void JNICALL
Java_Main_CallNonvirtual(JNIEnv* env, [[maybe_unused]] jclass k, jobject o, jclass c, jmethodID m) {
  env->CallNonvirtualVoidMethod(o, c, m);
}

}  // namespace art
