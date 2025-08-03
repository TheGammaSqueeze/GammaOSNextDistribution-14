/*
 * Copyright (C) 2014 The Android Open Source Project
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

#ifndef ART_RUNTIME_NATIVE_DALVIK_SYSTEM_VMRUNTIME_H_
#define ART_RUNTIME_NATIVE_DALVIK_SYSTEM_VMRUNTIME_H_

#include <jni.h>

#include "base/macros.h"

namespace art HIDDEN {

// TODO(260881207): register_dalvik_system_VMRuntime should be HIDDEN,
// but some apps fail to launch (e.g. b/319255249).
// The function is still exported for now, but it does a targetSdk check
// and aborts for SdkVersion after U. Libart code should use
// `real_register...` until exported function is removed.
EXPORT void register_dalvik_system_VMRuntime(JNIEnv* env);
void real_register_dalvik_system_VMRuntime(JNIEnv* env);

}  // namespace art

#endif  // ART_RUNTIME_NATIVE_DALVIK_SYSTEM_VMRUNTIME_H_
