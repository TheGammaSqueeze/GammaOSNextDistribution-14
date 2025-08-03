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

#include "jni.h"

#include <cstdint>
#include <stdio.h>
#include <unistd.h>

namespace art {

static int nativeObj;

static void BadNativeFinalizer(void *p) {
  if (p != &nativeObj) {
    printf("Finalizer was passed unexpected argument: %p, not %p\n", p, &nativeObj);
  }
  printf("Native finalizer looping\n");
  volatile bool always_true = true;
  while (always_true) { sleep(1); }
}

extern "C" JNIEXPORT jlong JNICALL Java_Main_getBadFreeFunction(JNIEnv*, jclass) {
  printf("Returning bad finalizer: %p\n", &BadNativeFinalizer);  // Delete for comparison.
  return reinterpret_cast<uintptr_t>(&BadNativeFinalizer);
}

extern "C" JNIEXPORT jlong JNICALL Java_Main_getNativeObj(JNIEnv*, jclass) {
  printf("Returning placeholder object: %p\n", &nativeObj);
  return reinterpret_cast<uintptr_t>(&nativeObj);
}

}  // namespace art
