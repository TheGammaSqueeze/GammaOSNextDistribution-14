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

#ifndef BERBERIS_ANDROID_API_JNI_JNI_TRAMPOLINES_H_
#define BERBERIS_ANDROID_API_JNI_JNI_TRAMPOLINES_H_

#include <jni.h>

#include "berberis/guest_abi/guest_type.h"
#include "berberis/guest_state/guest_addr.h"
#include "berberis/runtime_primitives/host_code.h"

namespace berberis {

JNINativeMethod* ConvertJNINativeMethods(const JNINativeMethod* methods, int count);

HostCode WrapGuestJNIFunction(GuestAddr pc,
                              const char* shorty,
                              const char* name,
                              bool has_jnienv_and_jobject);
HostCode WrapGuestJNIOnLoad(GuestAddr pc);

GuestType<JNIEnv*> ToGuestJNIEnv(void* host_jni_env);
JNIEnv* ToHostJNIEnv(GuestType<JNIEnv*> guest_jni_env);

GuestType<JavaVM*> ToGuestJavaVM(void* host_java_vm);
JavaVM* ToHostJavaVM(GuestType<JavaVM*> guest_java_vm);

}  // namespace berberis

#endif  // BERBERIS_ANDROID_API_JNI_JNI_TRAMPOLINES_H_
