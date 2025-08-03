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

// TODO(http://b/73019835) needed by NdkCameraCaptureSession.h
#include <camera/NdkCaptureRequest.h>

#include <camera/NdkCameraCaptureSession.h>
#include <camera/NdkCameraError.h>
#include <camera/NdkCameraManager.h>

#include "berberis/base/logging.h"
#include "berberis/guest_abi/function_wrappers.h"
#include "berberis/proxy_loader/proxy_library_builder.h"

#include "android_camera_checks.h"

namespace berberis {

namespace {

ACameraManager_AvailabilityCallbacks* ToHostACameraManager_AvailabilityCallbacks(
    const ACameraManager_AvailabilityCallbacks* guest_callback,
    ACameraManager_AvailabilityCallbacks* host_callback) {
  if (guest_callback == nullptr) {
    return nullptr;
  }
  *host_callback = {
      guest_callback->context,
      // typedef void (*ACameraManager_AvailabilityCallback)(void* context, const char* cameraId);
      WrapGuestFunction(GuestType(guest_callback->onCameraAvailable), "onCameraAvailable-callback"),
      WrapGuestFunction(GuestType(guest_callback->onCameraUnavailable),
                        "onCameraUnavailable-callback")};

  return host_callback;
}

ACameraManager_ExtendedAvailabilityCallbacks* ToHostACameraManager_ExtendedAvailabilityCallbacks(
    const ACameraManager_ExtendedAvailabilityCallbacks* guest_callback,
    ACameraManager_ExtendedAvailabilityCallbacks* host_callback) {
  if (guest_callback == nullptr) {
    return nullptr;
  }

  auto* base_callbacks = ToHostACameraManager_AvailabilityCallbacks(
      &(guest_callback->availabilityCallbacks), &(host_callback->availabilityCallbacks));
  CHECK_EQ(base_callbacks, &(host_callback->availabilityCallbacks));

  // typedef void (*ACameraManager_AccessPrioritiesChangedCallback)(void* context);
  host_callback->onCameraAccessPrioritiesChanged =
      WrapGuestFunction(GuestType(guest_callback->onCameraAccessPrioritiesChanged),
                        "onCameraAccessPrioritiesChanged-callback");

  memset(&(host_callback->reserved), 0, sizeof(host_callback->reserved));

  return host_callback;
}

ACameraCaptureSession_captureCallbacks* ToHostACameraCaptureSession_captureCallbacks(
    const ACameraCaptureSession_captureCallbacks* guest_callbacks,
    ACameraCaptureSession_captureCallbacks* host_callbacks) {
  if (guest_callbacks == nullptr) {
    return nullptr;
  }

  *host_callbacks = {
      guest_callbacks->context,
      // typedef void (*ACameraCaptureSession_captureCallback_start)(void* context,
      //                                                             ACameraCaptureSession* session,
      //                                                             const ACaptureRequest* request,
      //                                                             int64_t timestamp);
      WrapGuestFunction(GuestType(guest_callbacks->onCaptureStarted), "onCaptureStarted-callback"),
      // typedef void (*ACameraCaptureSession_captureCallback_result)(void* context,
      //                                                              ACameraCaptureSession*
      //                                                              session, ACaptureRequest*
      //                                                              request, const
      //                                                              ACameraMetadata* result);
      WrapGuestFunction(GuestType(guest_callbacks->onCaptureProgressed),
                        "onCaptureProgressed-callback"),
      WrapGuestFunction(GuestType(guest_callbacks->onCaptureCompleted),
                        "onCaptureCompleted-callback"),
      // typedef void (*ACameraCaptureSession_captureCallback_failed)(void* context,
      //                                                              ACameraCaptureSession*
      //                                                              session, ACaptureRequest*
      //                                                              request,
      //                                                              ACameraCaptureFailure*
      //                                                              failure);
      WrapGuestFunction(GuestType(guest_callbacks->onCaptureFailed), "onCaptureFailed-callback"),
      // typedef void (*ACameraCaptureSession_captureCallback_sequenceEnd)(
      //     void* context, ACameraCaptureSession* session, int sequenceId, int64_t frameNumber);
      WrapGuestFunction(GuestType(guest_callbacks->onCaptureSequenceCompleted),
                        "onCaptureSequenceCompleted-callback"),
      // typedef void (*ACameraCaptureSession_captureCallback_sequenceAbort)(
      //     void* context, ACameraCaptureSession* session, int sequenceId);
      WrapGuestFunction(GuestType(guest_callbacks->onCaptureSequenceAborted),
                        "onCaptureSequenceAborted-callback"),
      // typedef void (*ACameraCaptureSession_captureCallback_bufferLost)(
      //     void* context,
      //     ACameraCaptureSession* session,
      //     ACaptureRequest* request,
      //     ANativeWindow* window,
      //     int64_t frameNumber);
      WrapGuestFunction(GuestType(guest_callbacks->onCaptureBufferLost),
                        "onCaptureBufferLost-callback"),

  };

  return host_callbacks;
}

ACameraCaptureSession_captureCallbacksV2* ToHostACameraCaptureSession_captureCallbacksV2(
    const ACameraCaptureSession_captureCallbacksV2* guest_callbacks,
    ACameraCaptureSession_captureCallbacksV2* host_callbacks) {
  if (guest_callbacks == nullptr) {
    return nullptr;
  }

  *host_callbacks = {
      guest_callbacks->context,
      WrapGuestFunction(GuestType(guest_callbacks->onCaptureStarted), "onCaptureStarted-callback"),
      WrapGuestFunction(GuestType(guest_callbacks->onCaptureProgressed),
                        "onCaptureProgressed-callback"),
      WrapGuestFunction(GuestType(guest_callbacks->onCaptureCompleted),
                        "onCaptureCompleted-callback"),
      WrapGuestFunction(GuestType(guest_callbacks->onCaptureFailed), "onCaptureFailed-callback"),
      WrapGuestFunction(GuestType(guest_callbacks->onCaptureSequenceCompleted),
                        "onCaptureSequenceCompleted-callback"),
      WrapGuestFunction(GuestType(guest_callbacks->onCaptureSequenceAborted),
                        "onCaptureSequenceAborted-callback"),
      WrapGuestFunction(GuestType(guest_callbacks->onCaptureBufferLost),
                        "onCaptureBufferLost-callback"),

  };

  return host_callbacks;
}

ACameraCaptureSession_logicalCamera_captureCallbacks*
ToHostACameraCaptureSession_logicalCamera_captureCallbacks(
    const ACameraCaptureSession_logicalCamera_captureCallbacks* guest_callbacks,
    ACameraCaptureSession_logicalCamera_captureCallbacks* host_callbacks) {
  if (guest_callbacks == nullptr) {
    return nullptr;
  }

  *host_callbacks = {
      guest_callbacks->context,
      WrapGuestFunction(GuestType(guest_callbacks->onCaptureStarted), "onCaptureStarted-callback"),
      WrapGuestFunction(GuestType(guest_callbacks->onCaptureProgressed),
                        "onCaptureProgressed-callback"),
      WrapGuestFunction(GuestType(guest_callbacks->onLogicalCameraCaptureCompleted),
                        "onLogicalCameraCaptureCompleted-callback"),
      WrapGuestFunction(GuestType(guest_callbacks->onLogicalCameraCaptureFailed),
                        "onLogicalCameraCaptureFailed-callback"),
      WrapGuestFunction(GuestType(guest_callbacks->onCaptureSequenceCompleted),
                        "onCaptureSequenceCompleted-callback"),
      WrapGuestFunction(GuestType(guest_callbacks->onCaptureSequenceAborted),
                        "onCaptureSequenceAborted-callback"),
      WrapGuestFunction(GuestType(guest_callbacks->onCaptureBufferLost),
                        "onCaptureBufferLost-callback"),
  };

  return host_callbacks;
}

ACameraCaptureSession_logicalCamera_captureCallbacksV2*
ToHostACameraCaptureSession_logicalCamera_captureCallbacksV2(
    const ACameraCaptureSession_logicalCamera_captureCallbacksV2* guest_callbacks,
    ACameraCaptureSession_logicalCamera_captureCallbacksV2* host_callbacks) {
  if (guest_callbacks == nullptr) {
    return nullptr;
  }

  *host_callbacks = {
      guest_callbacks->context,
      WrapGuestFunction(GuestType(guest_callbacks->onCaptureStarted), "onCaptureStarted-callback"),
      WrapGuestFunction(GuestType(guest_callbacks->onCaptureProgressed),
                        "onCaptureProgressed-callback"),
      WrapGuestFunction(GuestType(guest_callbacks->onLogicalCameraCaptureCompleted),
                        "onLogicalCameraCaptureCompleted-callback"),
      WrapGuestFunction(GuestType(guest_callbacks->onLogicalCameraCaptureFailed),
                        "onLogicalCameraCaptureFailed-callback"),
      WrapGuestFunction(GuestType(guest_callbacks->onCaptureSequenceCompleted),
                        "onCaptureSequenceCompleted-callback"),
      WrapGuestFunction(GuestType(guest_callbacks->onCaptureSequenceAborted),
                        "onCaptureSequenceAborted-callback"),
      WrapGuestFunction(GuestType(guest_callbacks->onCaptureBufferLost),
                        "onCaptureBufferLost-callback"),
  };

  return host_callbacks;
}

ACameraCaptureSession_stateCallbacks* ToHostACameraCaptureSession_stateCallbacks(
    const ACameraCaptureSession_stateCallbacks* guest_callbacks,
    ACameraCaptureSession_stateCallbacks* host_callbacks) {
  if (guest_callbacks == nullptr) {
    return nullptr;
  }

  *host_callbacks = {
      guest_callbacks->context,
      // typedef void (*ACameraCaptureSession_stateCallback)(void* context,
      //                                                     ACameraCaptureSession* session);
      WrapGuestFunction(GuestType(guest_callbacks->onClosed), "onClosed-callback"),
      WrapGuestFunction(GuestType(guest_callbacks->onReady), "onReady-callback"),
      WrapGuestFunction(GuestType(guest_callbacks->onActive), "onActive-callback"),
  };

  return host_callbacks;
}

ACameraDevice_StateCallbacks* ToHostACameraDevice_StateCallbacks(
    const ACameraDevice_StateCallbacks* guest_callbacks,
    ACameraDevice_StateCallbacks* host_callbacks) {
  if (guest_callbacks == nullptr) {
    return nullptr;
  }

  *host_callbacks = {
      guest_callbacks->context,
      // typedef void (*ACameraDevice_StateCallback)(void* context, ACameraDevice* device);
      WrapGuestFunction(GuestType(guest_callbacks->onDisconnected), "onDisconnected-callback"),
      // typedef void (*ACameraDevice_ErrorStateCallback)(void* context,
      //                                                  ACameraDevice* device,
      //                                                  int error);
      WrapGuestFunction(GuestType(guest_callbacks->onError), "onError-callback"),

  };

  return host_callbacks;
}

camera_status_t DoThunk_ACameraCaptureSession_capture(
    ACameraCaptureSession* session,
    ACameraCaptureSession_captureCallbacks* callbacks,
    int numRequests,
    ACaptureRequest** requests,
    int* captureSequenceId) {
  ACameraCaptureSession_captureCallbacks host_callbacks_holder;
  auto host_callbacks =
      ToHostACameraCaptureSession_captureCallbacks(callbacks, &host_callbacks_holder);
  return ACameraCaptureSession_capture(
      session, host_callbacks, numRequests, requests, captureSequenceId);
}

camera_status_t DoThunk_ACameraCaptureSession_captureV2(
    ACameraCaptureSession* session,
    ACameraCaptureSession_captureCallbacksV2* callbacks,
    int numRequests,
    ACaptureRequest** requests,
    int* captureSequenceId) {
  ACameraCaptureSession_captureCallbacksV2 host_callbacks_holder;
  auto host_callbacks =
      ToHostACameraCaptureSession_captureCallbacksV2(callbacks, &host_callbacks_holder);
  return ACameraCaptureSession_captureV2(
      session, host_callbacks, numRequests, requests, captureSequenceId);
}

camera_status_t DoThunk_ACameraCaptureSession_setRepeatingRequest(
    ACameraCaptureSession* session,
    ACameraCaptureSession_captureCallbacks* callbacks,
    int numRequests,
    ACaptureRequest** requests,
    int* captureSequenceId) {
  ACameraCaptureSession_captureCallbacks host_callbacks_holder;
  auto host_callbacks =
      ToHostACameraCaptureSession_captureCallbacks(callbacks, &host_callbacks_holder);
  return ACameraCaptureSession_setRepeatingRequest(
      session, host_callbacks, numRequests, requests, captureSequenceId);
}

camera_status_t DoThunk_ACameraCaptureSession_setRepeatingRequestV2(
    ACameraCaptureSession* session,
    ACameraCaptureSession_captureCallbacksV2* callbacks,
    int numRequests,
    ACaptureRequest** requests,
    int* captureSequenceId) {
  ACameraCaptureSession_captureCallbacksV2 host_callbacks_holder;
  auto host_callbacks =
      ToHostACameraCaptureSession_captureCallbacksV2(callbacks, &host_callbacks_holder);
  return ACameraCaptureSession_setRepeatingRequestV2(
      session, host_callbacks, numRequests, requests, captureSequenceId);
}

camera_status_t DoThunk_ACameraCaptureSession_logicalCamera_capture(
    ACameraCaptureSession* session,
    ACameraCaptureSession_logicalCamera_captureCallbacks* callbacks,
    int numRequests,
    ACaptureRequest** requests,
    int* captureSequenceId) {
  ACameraCaptureSession_logicalCamera_captureCallbacks host_callbacks_holder;
  auto host_callbacks =
      ToHostACameraCaptureSession_logicalCamera_captureCallbacks(callbacks, &host_callbacks_holder);
  return ACameraCaptureSession_logicalCamera_capture(
      session, host_callbacks, numRequests, requests, captureSequenceId);
}

camera_status_t DoThunk_ACameraCaptureSession_logicalCamera_captureV2(
    ACameraCaptureSession* session,
    ACameraCaptureSession_logicalCamera_captureCallbacksV2* callbacks,
    int numRequests,
    ACaptureRequest** requests,
    int* captureSequenceId) {
  ACameraCaptureSession_logicalCamera_captureCallbacksV2 host_callbacks_holder;
  auto host_callbacks = ToHostACameraCaptureSession_logicalCamera_captureCallbacksV2(
      callbacks, &host_callbacks_holder);
  return ACameraCaptureSession_logicalCamera_captureV2(
      session, host_callbacks, numRequests, requests, captureSequenceId);
}

camera_status_t DoThunk_ACameraCaptureSession_logicalCamera_setRepeatingRequest(
    ACameraCaptureSession* session,
    ACameraCaptureSession_logicalCamera_captureCallbacks* callbacks,
    int numRequests,
    ACaptureRequest** requests,
    int* captureSequenceId) {
  ACameraCaptureSession_logicalCamera_captureCallbacks host_callbacks_holder;
  auto host_callbacks =
      ToHostACameraCaptureSession_logicalCamera_captureCallbacks(callbacks, &host_callbacks_holder);
  return ACameraCaptureSession_logicalCamera_setRepeatingRequest(
      session, host_callbacks, numRequests, requests, captureSequenceId);
}

camera_status_t DoThunk_ACameraCaptureSession_logicalCamera_setRepeatingRequestV2(
    ACameraCaptureSession* session,
    ACameraCaptureSession_logicalCamera_captureCallbacksV2* callbacks,
    int numRequests,
    ACaptureRequest** requests,
    int* captureSequenceId) {
  ACameraCaptureSession_logicalCamera_captureCallbacksV2 host_callbacks_holder;
  auto host_callbacks = ToHostACameraCaptureSession_logicalCamera_captureCallbacksV2(
      callbacks, &host_callbacks_holder);
  return ACameraCaptureSession_logicalCamera_setRepeatingRequestV2(
      session, host_callbacks, numRequests, requests, captureSequenceId);
}

camera_status_t DoThunk_ACameraDevice_createCaptureSession(
    ACameraDevice* device,
    const ACaptureSessionOutputContainer* outputs,
    const ACameraCaptureSession_stateCallbacks* callbacks,
    ACameraCaptureSession** session) {
  ACameraCaptureSession_stateCallbacks host_callbacks_holder;
  auto host_callbacks =
      ToHostACameraCaptureSession_stateCallbacks(callbacks, &host_callbacks_holder);
  return ACameraDevice_createCaptureSession(device, outputs, host_callbacks, session);
}

camera_status_t DoThunk_ACameraDevice_createCaptureSessionWithSessionParameters(
    ACameraDevice* device,
    const ACaptureSessionOutputContainer* outputs,
    const ACaptureRequest* sessionParameters,
    const ACameraCaptureSession_stateCallbacks* callbacks,
    ACameraCaptureSession** session) {
  ACameraCaptureSession_stateCallbacks host_callbacks_holder;
  auto host_callbacks =
      ToHostACameraCaptureSession_stateCallbacks(callbacks, &host_callbacks_holder);
  return ACameraDevice_createCaptureSessionWithSessionParameters(
      device, outputs, sessionParameters, host_callbacks, session);
}

camera_status_t DoThunk_ACameraManager_openCamera(ACameraManager* manager,
                                                  const char* cameraId,
                                                  ACameraDevice_StateCallbacks* callbacks,
                                                  ACameraDevice** device) {
  ACameraDevice_StateCallbacks host_callbacks_holder;
  auto host_callbacks = ToHostACameraDevice_StateCallbacks(callbacks, &host_callbacks_holder);
  return ACameraManager_openCamera(manager, cameraId, host_callbacks, device);
}

camera_status_t DoThunk_ACameraManager_registerAvailabilityCallback(
    ACameraManager* opaque_manager,
    const ACameraManager_AvailabilityCallbacks* guest_callback) {
  ACameraManager_AvailabilityCallbacks host_callback_holder;
  const auto host_callback =
      ToHostACameraManager_AvailabilityCallbacks(guest_callback, &host_callback_holder);
  return ACameraManager_registerAvailabilityCallback(opaque_manager, host_callback);
}

camera_status_t DoThunk_ACameraManager_unregisterAvailabilityCallback(
    ACameraManager* opaque_manager,
    const ACameraManager_AvailabilityCallbacks* guest_callback) {
  // Note, if guest callbacks are the same as registered, we will find them
  // in wrapper cache. If not, we'll wrap what we have and let host
  // unregisterator decide how to interpret this invalid input (it currently
  // ignores unregistered callbacks).
  ACameraManager_AvailabilityCallbacks host_callback_holder;
  const auto host_callback =
      ToHostACameraManager_AvailabilityCallbacks(guest_callback, &host_callback_holder);
  return ACameraManager_unregisterAvailabilityCallback(opaque_manager, host_callback);
}

camera_status_t DoThunk_ACameraManager_registerExtendedAvailabilityCallback(
    ACameraManager* opaque_manager,
    const ACameraManager_ExtendedAvailabilityCallbacks* guest_callback) {
  ACameraManager_ExtendedAvailabilityCallbacks host_callback_holder;
  const auto host_callback =
      ToHostACameraManager_ExtendedAvailabilityCallbacks(guest_callback, &host_callback_holder);
  return ACameraManager_registerExtendedAvailabilityCallback(opaque_manager, host_callback);
}

camera_status_t DoThunk_ACameraManager_unregisterExtendedAvailabilityCallback(
    ACameraManager* opaque_manager,
    const ACameraManager_ExtendedAvailabilityCallbacks* guest_callback) {
  // See comment inside DoThunk_ACameraManager_unregisterAvailabilityCallback.
  ACameraManager_ExtendedAvailabilityCallbacks host_callback_holder;
  const auto host_callback =
      ToHostACameraManager_ExtendedAvailabilityCallbacks(guest_callback, &host_callback_holder);
  return ACameraManager_unregisterExtendedAvailabilityCallback(opaque_manager, host_callback);
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

DEFINE_INIT_PROXY_LIBRARY("libcamera2ndk.so")

}  // namespace

}  // namespace berberis
