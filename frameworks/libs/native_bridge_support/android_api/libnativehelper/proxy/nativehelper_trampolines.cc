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

#include <android/file_descriptor_jni.h>
#include <jni.h>

#include "berberis/base/logging.h"
#include "berberis/guest_abi/function_wrappers.h"
#include "berberis/guest_abi/guest_params.h"
#include "berberis/jni/jni_trampolines.h"
#include "berberis/proxy_loader/proxy_library_builder.h"

namespace berberis {

namespace {

// jobject AFileDescriptor_create(JNIEnv* env);
void DoCustomTrampoline_AFileDescriptor_create(HostCode /* callee */, ProcessState* state) {
  auto [guest_env] = GuestParamsValues<decltype(AFileDescriptor_create)>(state);
  auto&& [ret] = GuestReturnReference<decltype(AFileDescriptor_create)>(state);
  ret = AFileDescriptor_create(ToHostJNIEnv(guest_env));
}

// int AFileDescriptor_getFd(JNIEnv* env, jobject fileDescriptor);
void DoCustomTrampoline_AFileDescriptor_getFd(HostCode /* callee */, ProcessState* state) {
  auto [guest_env, fileDescriptor] = GuestParamsValues<decltype(AFileDescriptor_getFd)>(state);
  auto&& [ret] = GuestReturnReference<decltype(AFileDescriptor_getFd)>(state);
  ret = AFileDescriptor_getFd(ToHostJNIEnv(guest_env), fileDescriptor);
}

// void AFileDescriptor_setFd(JNIEnv* env, jobject fileDescriptor, int fd);
void DoCustomTrampoline_AFileDescriptor_setFd(HostCode /* callee */, ProcessState* state) {
  auto [guest_env, fileDescriptor, fd] = GuestParamsValues<decltype(AFileDescriptor_setFd)>(state);
  AFileDescriptor_setFd(ToHostJNIEnv(guest_env), fileDescriptor, fd);
}

// jint JNI_CreateJavaVM(JavaVM**, JNIEnv**, void*);
void DoCustomTrampoline_JNI_CreateJavaVM(HostCode /* callee */, ProcessState* state) {
  auto [guest_vm, guest_env, init_info] = GuestParamsValues<decltype(JNI_CreateJavaVM)>(state);
  auto&& [ret] = GuestReturnReference<decltype(JNI_CreateJavaVM)>(state);
  JavaVM* host_vm;
  JNIEnv* host_env;

  ret = JNI_CreateJavaVM(&host_vm, &host_env, init_info);
  // Android only supports a single runtime, this is already running so no more
  // can be created. Thus we don't need to convert the returned objects to guest.
  CHECK(ret == JNI_ERR);
}

// jint JNI_GetCreatedJavaVMs(JavaVM**, jsize, jsize*);
void DoCustomTrampoline_JNI_GetCreatedJavaVMs(HostCode /* callee */, ProcessState* state) {
  auto [guest_vm, input_size, output_size] =
      GuestParamsValues<decltype(JNI_GetCreatedJavaVMs)>(state);
  auto&& [ret] = GuestReturnReference<decltype(JNI_GetCreatedJavaVMs)>(state);

  // There could be only one VM in Android.
  JavaVM* host_vm;
  ret = JNI_GetCreatedJavaVMs(&host_vm, 1, output_size);

  if (ret == JNI_ERR) {
    return;
  }

  CHECK(*output_size == 1u);
  *ToHostAddr<GuestType<JavaVM*>>(ToGuestAddr(guest_vm)) = ToGuestJavaVM(host_vm);
}

#if defined(NATIVE_BRIDGE_GUEST_ARCH_ARM) && defined(__i386__)

#include "trampolines_arm_to_x86-inl.h"  // generated file NOLINT [build/include]

#elif defined(NATIVE_BRIDGE_GUEST_ARCH_ARM64) && defined(__x86_64__)

#include "trampolines_arm64_to_x86_64-inl.h"  // generated file NOLINT [build/include]

#elif defined(NATIVE_BRIDGE_GUEST_ARCH_RISCV64) && defined(__x86_64__)

#include "trampolines_riscv64_to_x86_64-inl.h"  // generated file NOLINT [build/include]

#else

#error "Unknown guest/host arch combination"

#endif

DEFINE_INIT_PROXY_LIBRARY("libnativehelper.so")

}  // namespace

}  // namespace berberis
