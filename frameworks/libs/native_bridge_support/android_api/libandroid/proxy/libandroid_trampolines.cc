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

#include <stdint.h>
// This is for off_t and similar.
// They are also used in asset_manager.h without an include.
#include <sys/types.h>

#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/choreographer.h>
#include <android/hardware_buffer_jni.h>
#include <android/input.h>
#include <android/looper.h>
#include <android/native_activity.h>
#include <android/native_window_jni.h>
#include <android/sensor.h>
#include <android/sharedmem_jni.h>
#include <android/storage_manager.h>
#include <android/surface_texture_jni.h>
#include <jni.h>

#include "berberis/guest_abi/function_wrappers.h"
#include "berberis/guest_abi/guest_params.h"
#include "berberis/guest_state/guest_addr.h"
#include "berberis/guest_state/guest_state.h"
#include "berberis/native_activity/native_activity.h"
#include "berberis/proxy_loader/proxy_library_builder.h"

namespace berberis {

template <>
struct GuestAbi::GuestArgumentInfo<ANativeActivity*> : GuestAbi::GuestArgumentInfo<void*> {
  using GuestType = berberis::Guest_ANativeActivity*;
  using HostType = ANativeActivity*;
};

}  // namespace berberis

namespace berberis {

namespace {

// Note: on host (glibc-based systems) in some cases we have 64-bit off_t while 32-bit Android
// always uses 32-bit off_t.  We no don't support use of these libraries with GlibC thus we could
// just assert that size of long and off_t are the same.
//
// The following functions are potentially affected: AAsset_getLength, AAsset_getRemainingLength,
// AAsset_seek, and AAsset_openFileDescriptor.
static_assert(sizeof(long) == sizeof(off_t));

typedef int (*ALooper_callbackFunc)(int fd, int events, void* data);

template <typename ResultType, typename... ArgumentType>
ALooper_callbackFunc WrapLooperCallback(GuestType<ResultType (*)(ArgumentType...)> callback) {
  if (ToGuestAddr(callback) == 0) {
    return nullptr;
  }
  return WrapGuestFunction(callback, "ALooper_callbackFunc");
}

void DoCustomTrampoline_ALooper_addFd(HostCode /* callee */, ProcessState* state) {
  auto [looper, fd, ident, events, guest_callback, data] =
      GuestParamsValues<decltype(ALooper_addFd)>(state);
  ALooper_callbackFunc host_callback = WrapLooperCallback(guest_callback);
  auto&& [ret] = GuestReturnReference<decltype(ALooper_addFd)>(state);
  ret = ALooper_addFd(looper, fd, ident, events, host_callback, data);
}

void DoCustomTrampoline_ASensorManager_createEventQueue(HostCode /* callee */,
                                                        ProcessState* state) {
  auto [manager, looper, ident, guest_callback, data] =
      GuestParamsValues<decltype(ASensorManager_createEventQueue)>(state);
  ALooper_callbackFunc host_callback = WrapLooperCallback(guest_callback);
  auto&& [ret] = GuestReturnReference<decltype(ASensorManager_createEventQueue)>(state);
  ret = ASensorManager_createEventQueue(manager, looper, ident, host_callback, data);
}

void DoCustomTrampoline_AInputQueue_attachLooper(HostCode /* callee */, ProcessState* state) {
  auto [queue, looper, ident, guest_callback, data] =
      GuestParamsValues<decltype(AInputQueue_attachLooper)>(state);
  ALooper_callbackFunc host_callback = WrapLooperCallback(guest_callback);
  AInputQueue_attachLooper(queue, looper, ident, host_callback, data);
}

void DoCustomTrampoline_ANativeActivity_finish(HostCode /* callee */, ProcessState* state) {
  auto [guest_activity] = GuestParamsValues<decltype(ANativeActivity_finish)>(state);
  ANativeActivity_finish(guest_activity->host_native_activity);
}

void DoCustomTrampoline_ANativeActivity_setWindowFormat(HostCode /* callee */,
                                                        ProcessState* state) {
  auto [guest_activity, format] =
      GuestParamsValues<decltype(ANativeActivity_setWindowFormat)>(state);
  ANativeActivity_setWindowFormat(guest_activity->host_native_activity, format);
}

void DoCustomTrampoline_ANativeActivity_setWindowFlags(HostCode /* callee */, ProcessState* state) {
  auto [guest_activity, addFlags, removeFlags] =
      GuestParamsValues<decltype(ANativeActivity_setWindowFlags)>(state);
  ANativeActivity_setWindowFlags(guest_activity->host_native_activity, addFlags, removeFlags);
}

void DoCustomTrampoline_ANativeActivity_showSoftInput(HostCode /* callee */, ProcessState* state) {
  auto [guest_activity, flags] = GuestParamsValues<decltype(ANativeActivity_showSoftInput)>(state);
  ANativeActivity_showSoftInput(guest_activity->host_native_activity, flags);
}

void DoCustomTrampoline_ANativeActivity_hideSoftInput(HostCode /* callee */, ProcessState* state) {
  auto [guest_activity, flags] = GuestParamsValues<decltype(ANativeActivity_hideSoftInput)>(state);
  ANativeActivity_hideSoftInput(guest_activity->host_native_activity, flags);
}

// Note, AChoreographer is opaque (from frameworks/native/include/android/choreographer.h):
//
// struct AChoreographer;
// typedef struct AChoreographer AChoreographer;
//
// typedef void (*AChoreographer_frameCallback)(long frameTimeNanos, void* data);
//
// void AChoreographer_postFrameCallback(AChoreographer* choreographer,
//                 AChoreographer_frameCallback callback, void* data);
//
// void AChoreographer_postFrameCallbackDelayed(AChoreographer* choreographer,
//                 AChoreographer_frameCallback callback, void* data, long
//                 delayMillis);

void DoCustomTrampoline_AChoreographer_postFrameCallback(HostCode /* callee */,
                                                         ProcessState* state) {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
  auto [choreographer, guest_callback, data] =
      GuestParamsValues<decltype(AChoreographer_postFrameCallback)>(state);
  AChoreographer_frameCallback host_callback =
      WrapGuestFunction(guest_callback, "AChoreographer_frameCallback");
  AChoreographer_postFrameCallback(choreographer, host_callback, data);
#pragma clang diagnostic pop
}

void DoCustomTrampoline_AChoreographer_postFrameCallbackDelayed(HostCode /* callee */,
                                                                ProcessState* state) {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
  auto [choreographer, guest_callback, data, delay] =
      GuestParamsValues<decltype(AChoreographer_postFrameCallbackDelayed)>(state);
  AChoreographer_frameCallback host_callback =
      WrapGuestFunction(guest_callback, "AChoreographer_frameCallback");
  AChoreographer_postFrameCallbackDelayed(choreographer, host_callback, data, delay);
#pragma clang diagnostic pop
}

void DoCustomTrampoline_AStorageManager_mountObb(HostCode /* callee */, ProcessState* state) {
  auto [mgr, filename, key, guest_callback, data] =
      GuestParamsValues<decltype(AStorageManager_mountObb)>(state);
  AStorageManager_obbCallbackFunc host_callback =
      WrapGuestFunction(guest_callback, "AStorageManager_obbCallbackFunc");
  AStorageManager_mountObb(mgr, filename, key, host_callback, data);
}

void DoCustomTrampoline_AStorageManager_unmountObb(HostCode /* callee */, ProcessState* state) {
  auto [mgr, filename, force, guest_callback, data] =
      GuestParamsValues<decltype(AStorageManager_unmountObb)>(state);
  AStorageManager_obbCallbackFunc host_callback =
      WrapGuestFunction(guest_callback, "AStorageManager_obbCallbackFunc");
  AStorageManager_unmountObb(mgr, filename, force, host_callback, data);
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

DEFINE_INIT_PROXY_LIBRARY("libandroid.so")

}  // namespace

}  // namespace berberis
