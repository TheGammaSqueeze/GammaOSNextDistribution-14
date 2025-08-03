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

#include <android_runtime/AndroidRuntime.h>

#include "berberis/guest_abi/function_wrappers.h"
#include "berberis/guest_abi/guest_params.h"
#include "berberis/jni/jni_trampolines.h"
#include "berberis/proxy_loader/proxy_library_builder.h"

namespace berberis {

namespace {

// At the moment this function simply calls jniRegisterNativeMethods.
// However, this can change in the future, at least this function might start
// doing some additional stuff - so merging these 2 symbols seems wrong.
void DoCustomTrampoline__ZN7android14AndroidRuntime21registerNativeMethodsEP7_JNIEnvPKcPK15JNINativeMethodi(  // NOLINT(whitespace/line_length)
    HostCode /* callee */,
    ProcessState* state) {
  using PFN_callee = decltype(&android::AndroidRuntime::registerNativeMethods);
  auto [arg_env, arg_class_name, arg_methods, arg_n] = GuestParamsValues<PFN_callee>(state);

  JNINativeMethod* host_methods = ConvertJNINativeMethods(arg_methods, arg_n);

  auto&& [ret] = GuestReturnReference<PFN_callee>(state);

  ret =
      android::AndroidRuntime::registerNativeMethods(arg_env, arg_class_name, host_methods, arg_n);

  delete[] host_methods;
}

// TODO(b/278625630): This is not a standard library and will be deprecated.

#if defined(NATIVE_BRIDGE_GUEST_ARCH_ARM) && defined(__i386__)

#include "trampolines_arm_to_x86-inl.h"  // generated file NOLINT [build/include]

#elif defined(NATIVE_BRIDGE_GUEST_ARCH_ARM64) && defined(__x86_64__)

#include "trampolines_arm64_to_x86_64-inl.h"  // generated file NOLINT [build/include]

#elif defined(NATIVE_BRIDGE_GUEST_ARCH_RISCV64) && defined(__x86_64__)

#include "trampolines_riscv64_to_x86_64-inl.h"  // generated file NOLINT [build/include]

#else

#error "Unknown guest/host arch combination"

#endif

DEFINE_INIT_PROXY_LIBRARY("libandroid_runtime.so")

}  // namespace

}  // namespace berberis
