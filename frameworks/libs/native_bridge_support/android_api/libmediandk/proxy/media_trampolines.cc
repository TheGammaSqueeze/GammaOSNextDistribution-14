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

#include <dlfcn.h>

#include <media/NdkImageReader.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaDataSource.h>
#include <media/NdkMediaDrm.h>

#include "berberis/base/logging.h"
#include "berberis/guest_abi/function_wrappers.h"
#include "berberis/guest_abi/guest_params.h"
#include "berberis/proxy_loader/proxy_library_builder.h"

namespace berberis {

namespace {

// media_status_t AImageReader_setBufferRemovedListener(
//         AImageReader* reader, AImageReader_BufferRemovedListener* listener);
void DoCustomTrampoline_AImageReader_setBufferRemovedListener(HostCode /* callee */,
                                                              ProcessState* state) {
  // The documentation says that "Note that calling this method will replace previously
  // registered listeners."
  static AImageReader_BufferRemovedListener host_listener;
  auto [reader, listener] =
      GuestParamsValues<decltype(AImageReader_setBufferRemovedListener)>(state);
  if (listener != nullptr) {
    host_listener.context = listener->context;
    // typedef void (*AImageReader_BufferRemovedCallback)(void* context,
    //                                                    AImageReader* reader,
    //                                                    AHardwareBuffer* buffer);
    host_listener.onBufferRemoved = WrapGuestFunction(
        GuestType(listener->onBufferRemoved), "AImageReader_setBufferRemovedListener-callback");
    listener = &host_listener;
  }

  auto&& [ret] = GuestReturnReference<decltype(AImageReader_setBufferRemovedListener)>(state);
  ret = AImageReader_setBufferRemovedListener(reader, listener);
}

// media_status_t AImageReader_setImageListener(
//         AImageReader* reader, AImageReader_ImageListener* listener);
void DoCustomTrampoline_AImageReader_setImageListener(HostCode /* callee */, ProcessState* state) {
  // The documentation says that "Note that calling this method will replace previously
  // registered listeners."
  static AImageReader_ImageListener host_listener;
  auto [reader, listener] = GuestParamsValues<decltype(AImageReader_setImageListener)>(state);
  if (listener != nullptr) {
    host_listener.context = listener->context;

    // typedef void(* AImageReader_ImageCallback) (void *context, AImageReader *reader)
    host_listener.onImageAvailable = WrapGuestFunction(GuestType(listener->onImageAvailable),
                                                       "AImageReader_setImageListener-callback");
    listener = &host_listener;
  }

  auto&& [ret] = GuestReturnReference<decltype(AImageReader_setImageListener)>(state);
  ret = AImageReader_setImageListener(reader, listener);
}

// typedef void (*AMediaCodecOnAsyncInputAvailable)(AMediaCodec* codec,
//                                                  void *userdata,
//                                                  int32_t index);
//
// typedef void (*AMediaCodecOnAsyncOutputAvailable)(AMediaCodec* codec,
//                                                   void *userdata,
//                                                   int32_t index,
//                                                   AMediaCodecBufferInfo* bufferInfo);
//
// typedef void (*AMediaCodecOnAsyncFormatChanged)(AMediaCodec* codec,
//                                                 void *userdata,
//                                                 AMediaFormat* format);
//
// typedef void (*AMediaCodecOnAsyncError)(AMediaCodec* codec,
//                                         void *userdata,
//                                         media_status_t error,
//                                         int32_t actionCode,
//                                         const char *detail);
//
//
// struct AMediaCodecOnAsyncNotifyCallback {
//   AMediaCodecOnAsyncInputAvailable  onAsyncInputAvailable;
//   AMediaCodecOnAsyncOutputAvailable onAsyncOutputAvailable;
//   AMediaCodecOnAsyncFormatChanged   onAsyncFormatChanged;
//   AMediaCodecOnAsyncError           onAsyncError;
// };
//
// media_status_t AMediaCodec_setAsyncNotifyCallback(AMediaCodec*,
//                                                   AMediaCodecOnAsyncNotifyCallback callback,
//                                                   void *userdata);
#if defined(NATIVE_BRIDGE_GUEST_ARCH_ARM)
// Note: passing struct (not pointer) as a parameter is quite complicated.
// And currently not supported by GuestParams on ARM.
//
// There AMediaCodecOnAsyncNotifyCallback struct argument is cut in two parts - first three
// pointers are passed in r1, r2, r3 and fourth pointer is passed on stack.  To handle that
// case correctly we need to know how structure is organized internally (e.g. floating point
// arguments would go to VFP registers if aapcs-vfp is used).
//
// Treat four pointers as four arguments till GuestParams could handle that case on ARM.
void DoCustomTrampoline_AMediaCodec_setAsyncNotifyCallback(HostCode /* callee */,
                                                           ProcessState* state) {
  using PFN_callback = media_status_t (*)(AMediaCodec*,
                                          AMediaCodecOnAsyncInputAvailable,
                                          AMediaCodecOnAsyncOutputAvailable,
                                          AMediaCodecOnAsyncFormatChanged,
                                          AMediaCodecOnAsyncError,
                                          void*);
  auto [codec,
        guest_cb_onAsyncInputAvailable,
        guest_cb_onAsyncOutputAvailable,
        guest_cb_onAsyncFormatChanged,
        guest_cb_onAsyncError,
        userdata] = GuestParamsValues<PFN_callback>(state);

  AMediaCodecOnAsyncNotifyCallback host_cb;
  host_cb.onAsyncInputAvailable = WrapGuestFunction(guest_cb_onAsyncInputAvailable,
                                                    "AMediaCodecOnAsyncInputAvailable-callback");
  host_cb.onAsyncOutputAvailable = WrapGuestFunction(guest_cb_onAsyncOutputAvailable,
                                                     "AMediaCodecOnAsyncOutputAvailable-callback");
  host_cb.onAsyncFormatChanged =
      WrapGuestFunction(guest_cb_onAsyncFormatChanged, "AMediaCodecOnAsyncFormatChanged-callback");
  host_cb.onAsyncError =
      WrapGuestFunction(guest_cb_onAsyncError, "AMediaCodecOnAsyncError-callback");

  auto&& [ret] = GuestReturnReference<PFN_callback>(state);
  ret = AMediaCodec_setAsyncNotifyCallback(codec, host_cb, userdata);
}

#else /* defined(NATIVE_BRIDGE_GUEST_ARCH_ARM) */

void DoCustomTrampoline_AMediaCodec_setAsyncNotifyCallback(HostCode /* callee */,
                                                           ProcessState* state) {
  auto [codec, cb, userdata] =
      GuestParamsValues<decltype(AMediaCodec_setAsyncNotifyCallback)>(state);

  AMediaCodecOnAsyncNotifyCallback host_cb;
  host_cb.onAsyncInputAvailable = WrapGuestFunction(
      GuestType(static_cast<AMediaCodecOnAsyncNotifyCallback>(cb).onAsyncInputAvailable),
      "AMediaCodecOnAsyncInputAvailable-callback");
  host_cb.onAsyncOutputAvailable = WrapGuestFunction(
      GuestType(static_cast<AMediaCodecOnAsyncNotifyCallback>(cb).onAsyncOutputAvailable),
      "AMediaCodecOnAsyncOutputAvailable-callback");
  host_cb.onAsyncFormatChanged = WrapGuestFunction(
      GuestType(static_cast<AMediaCodecOnAsyncNotifyCallback>(cb).onAsyncFormatChanged),
      "AMediaCodecOnAsyncFormatChanged-callback");
  host_cb.onAsyncError =
      WrapGuestFunction(GuestType(static_cast<AMediaCodecOnAsyncNotifyCallback>(cb).onAsyncError),
                        "AMediaCodecOnAsyncError-callback");

  auto&& [ret] = GuestReturnReference<decltype(AMediaCodec_setAsyncNotifyCallback)>(state);
  ret = AMediaCodec_setAsyncNotifyCallback(codec, host_cb, userdata);
}

#endif /* defined(NATIVE_BRIDGE_GUEST_ARCH_ARM) */

// typedef void (*AMediaDataSourceClose)(void *userdata);
// void AMediaDataSource_setClose(AMediaDataSource*, AMediaDataSourceClose);
void DoCustomTrampoline_AMediaDataSource_setClose(HostCode /* callee */, ProcessState* state) {
  auto [datasource, guest_callback] = GuestParamsValues<decltype(AMediaDataSource_setClose)>(state);
  AMediaDataSourceClose host_callback =
      WrapGuestFunction(guest_callback, "AMediaDataSource_setClose-callback");
  AMediaDataSource_setClose(datasource, host_callback);
}

// typedef ssize_t (*AMediaDataSourceGetSize)(void *userdata);
// void AMediaDataSource_setGetSize(AMediaDataSource*, AMediaDataSourceGetSize);
void DoCustomTrampoline_AMediaDataSource_setGetSize(HostCode /* callee */, ProcessState* state) {
  auto [datasource, guest_callback] =
      GuestParamsValues<decltype(AMediaDataSource_setGetSize)>(state);
  AMediaDataSourceGetSize host_callback =
      WrapGuestFunction(guest_callback, "AMediaDataSource_setGetSize-callback");
  AMediaDataSource_setGetSize(datasource, host_callback);
}

// typedef ssize_t (*AMediaDataSourceReadAt)(
//    void *userdata, off64_t offset, void * buffer, size_t size);
// void AMediaDataSource_setReadAt(AMediaDataSource*, AMediaDataSourceReadAt);
void DoCustomTrampoline_AMediaDataSource_setReadAt(HostCode /* callee */, ProcessState* state) {
  auto [datasource, guest_callback] =
      GuestParamsValues<decltype(AMediaDataSource_setReadAt)>(state);
  AMediaDataSourceReadAt host_callback =
      WrapGuestFunction(guest_callback, "AMediaDataSource_setReadAt-callback");
  AMediaDataSource_setReadAt(datasource, host_callback);
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

DEFINE_INIT_PROXY_LIBRARY("libmediandk.so")

}  // namespace

}  // namespace berberis
