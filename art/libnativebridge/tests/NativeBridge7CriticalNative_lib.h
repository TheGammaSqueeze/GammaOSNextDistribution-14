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

#ifndef ART_LIBNATIVEBRIDGE_TESTS_NATIVEBRIDGE7CRITICALNATIVE_LIB_H_
#define ART_LIBNATIVEBRIDGE_TESTS_NATIVEBRIDGE7CRITICALNATIVE_LIB_H_

#include "nativebridge/native_bridge.h"

namespace android {

void ResetTrampolineCalledState();

void SetLegacyGetTrampolineCalled();
bool IsLegacyGetTrampolineCalled();

void SetGetTrampoline2Called(JNICallType jni_call_type);
bool IsGetTrampoline2Called();
JNICallType GetTrampoline2JNICallType();

}  // namespace android

#endif  // ART_LIBNATIVEBRIDGE_TESTS_NATIVEBRIDGE7CRITICALNATIVE_LIB_H_
