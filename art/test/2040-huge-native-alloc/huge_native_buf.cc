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

#include "base/utils.h"
#include "gc/heap.h"
#include "jni.h"
#include "runtime.h"
#include <stddef.h>

namespace art {
namespace HugeNativeBuf {

static constexpr size_t HUGE_SIZE = 10'000'000;

extern "C" JNIEXPORT jobject JNICALL Java_Main_getHugeNativeBuffer(
    JNIEnv* env, [[maybe_unused]] jclass klass) {
  char* buffer = new char[HUGE_SIZE];
  return env->NewDirectByteBuffer(buffer, HUGE_SIZE);
}

extern "C" JNIEXPORT void JNICALL Java_Main_deleteHugeNativeBuffer(
    JNIEnv* env, [[maybe_unused]] jclass klass, jobject jbuffer) {
  delete [] static_cast<char*>(env->GetDirectBufferAddress(jbuffer));
}

extern "C" JNIEXPORT jint JNICALL Java_Main_getGcNum(
    [[maybe_unused]] JNIEnv* env, [[maybe_unused]] jclass klass) {
  return Runtime::Current()->GetHeap()->GetCurrentGcNum();
}

}  // namespace HugeNativeBuf
}  // namespace art
