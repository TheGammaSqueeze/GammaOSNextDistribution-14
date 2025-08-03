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

#include "NativeBridge7CriticalNative_lib.h"

namespace android {

static bool g_legacy_get_trampoline_called = false;
static bool g_get_trampoline2_called = false;
static JNICallType g_jni_call_type = kJNICallTypeRegular;

void ResetTrampolineCalledState() {
  g_legacy_get_trampoline_called = false;
  g_get_trampoline2_called = false;
  g_jni_call_type = kJNICallTypeRegular;
}

void SetLegacyGetTrampolineCalled() { g_legacy_get_trampoline_called = true; }

bool IsLegacyGetTrampolineCalled() { return g_legacy_get_trampoline_called; }

void SetGetTrampoline2Called(JNICallType jni_call_type) {
  g_get_trampoline2_called = true;
  g_jni_call_type = jni_call_type;
}

bool IsGetTrampoline2Called() { return g_get_trampoline2_called; }

JNICallType GetTrampoline2JNICallType() { return g_jni_call_type; }

}  // namespace android
