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

#include "berberis/base/struct_check.h"

#if defined(__arm__) || defined(NATIVE_BRIDGE_GUEST_ARCH_ARM)

// This is layout we expect from the ACameraManager_AvailabilityCallbacks
// TODO: Note that we do not really have a way to test that guest structure layout is
// the same (see http://b/78329188 for details)
CHECK_STRUCT_LAYOUT(ACameraManager_AvailabilityCallbacks, 96, 32);
CHECK_FIELD_LAYOUT(ACameraManager_AvailabilityCallbacks, context, 0, 32);
CHECK_FIELD_LAYOUT(ACameraManager_AvailabilityCallbacks, onCameraAvailable, 32, 32);
CHECK_FIELD_LAYOUT(ACameraManager_AvailabilityCallbacks, onCameraUnavailable, 64, 32);

CHECK_STRUCT_LAYOUT(ACameraManager_ExtendedAvailabilityCallbacks, 320, 32);
CHECK_FIELD_LAYOUT(ACameraManager_ExtendedAvailabilityCallbacks, availabilityCallbacks, 0, 96);
CHECK_FIELD_LAYOUT(ACameraManager_ExtendedAvailabilityCallbacks,
                   onCameraAccessPrioritiesChanged,
                   96,
                   32);
CHECK_FIELD_LAYOUT(ACameraManager_ExtendedAvailabilityCallbacks,
                   onPhysicalCameraAvailable,
                   128,
                   32);
CHECK_FIELD_LAYOUT(ACameraManager_ExtendedAvailabilityCallbacks,
                   onPhysicalCameraUnavailable,
                   160,
                   32);
CHECK_FIELD_LAYOUT(ACameraManager_ExtendedAvailabilityCallbacks, reserved, 192, 4 * 32);

CHECK_STRUCT_LAYOUT(ACameraCaptureSession_captureCallbacks, 256, 32);
CHECK_FIELD_LAYOUT(ACameraCaptureSession_captureCallbacks, context, 0, 32);
CHECK_FIELD_LAYOUT(ACameraCaptureSession_captureCallbacks, onCaptureStarted, 32, 32);
CHECK_FIELD_LAYOUT(ACameraCaptureSession_captureCallbacks, onCaptureProgressed, 64, 32);
CHECK_FIELD_LAYOUT(ACameraCaptureSession_captureCallbacks, onCaptureCompleted, 96, 32);
CHECK_FIELD_LAYOUT(ACameraCaptureSession_captureCallbacks, onCaptureFailed, 128, 32);
CHECK_FIELD_LAYOUT(ACameraCaptureSession_captureCallbacks, onCaptureSequenceCompleted, 160, 32);
CHECK_FIELD_LAYOUT(ACameraCaptureSession_captureCallbacks, onCaptureSequenceAborted, 192, 32);
CHECK_FIELD_LAYOUT(ACameraCaptureSession_captureCallbacks, onCaptureBufferLost, 224, 32);

CHECK_STRUCT_LAYOUT(ACameraCaptureSession_stateCallbacks, 128, 32);
CHECK_FIELD_LAYOUT(ACameraCaptureSession_stateCallbacks, context, 0, 32);
CHECK_FIELD_LAYOUT(ACameraCaptureSession_stateCallbacks, onClosed, 32, 32);
CHECK_FIELD_LAYOUT(ACameraCaptureSession_stateCallbacks, onReady, 64, 32);
CHECK_FIELD_LAYOUT(ACameraCaptureSession_stateCallbacks, onActive, 96, 32);

CHECK_STRUCT_LAYOUT(ACameraDevice_StateCallbacks, 96, 32);
CHECK_FIELD_LAYOUT(ACameraDevice_StateCallbacks, context, 0, 32);
CHECK_FIELD_LAYOUT(ACameraDevice_StateCallbacks, onDisconnected, 32, 32);
CHECK_FIELD_LAYOUT(ACameraDevice_StateCallbacks, onError, 64, 32);

#elif defined(__aarch64__) || defined(NATIVE_BRIDGE_GUEST_ARCH_ARM64) || defined(NATIVE_BRIDGE_GUEST_ARCH_RISCV64)

// This is layout we expect from the ACameraManager_AvailabilityCallbacks
// TODO: Note that we do not really have a way to test that guest structure layout is
// the same (see http://b/78329188 for details)
CHECK_STRUCT_LAYOUT(ACameraManager_AvailabilityCallbacks, 192, 64);
CHECK_FIELD_LAYOUT(ACameraManager_AvailabilityCallbacks, context, 0, 64);
CHECK_FIELD_LAYOUT(ACameraManager_AvailabilityCallbacks, onCameraAvailable, 64, 64);
CHECK_FIELD_LAYOUT(ACameraManager_AvailabilityCallbacks, onCameraUnavailable, 128, 64);

CHECK_STRUCT_LAYOUT(ACameraManager_ExtendedAvailabilityCallbacks, 640, 64);
CHECK_FIELD_LAYOUT(ACameraManager_ExtendedAvailabilityCallbacks, availabilityCallbacks, 0, 192);
CHECK_FIELD_LAYOUT(ACameraManager_ExtendedAvailabilityCallbacks,
                   onCameraAccessPrioritiesChanged,
                   192,
                   64);
CHECK_FIELD_LAYOUT(ACameraManager_ExtendedAvailabilityCallbacks,
                   onPhysicalCameraAvailable,
                   256,
                   64);
CHECK_FIELD_LAYOUT(ACameraManager_ExtendedAvailabilityCallbacks,
                   onPhysicalCameraUnavailable,
                   320,
                   64);
CHECK_FIELD_LAYOUT(ACameraManager_ExtendedAvailabilityCallbacks, reserved, 384, 4 * 64);

CHECK_STRUCT_LAYOUT(ACameraCaptureSession_captureCallbacks, 512, 64);
CHECK_FIELD_LAYOUT(ACameraCaptureSession_captureCallbacks, context, 0, 64);
CHECK_FIELD_LAYOUT(ACameraCaptureSession_captureCallbacks, onCaptureStarted, 64, 64);
CHECK_FIELD_LAYOUT(ACameraCaptureSession_captureCallbacks, onCaptureProgressed, 128, 64);
CHECK_FIELD_LAYOUT(ACameraCaptureSession_captureCallbacks, onCaptureCompleted, 192, 64);
CHECK_FIELD_LAYOUT(ACameraCaptureSession_captureCallbacks, onCaptureFailed, 256, 64);
CHECK_FIELD_LAYOUT(ACameraCaptureSession_captureCallbacks, onCaptureSequenceCompleted, 320, 64);
CHECK_FIELD_LAYOUT(ACameraCaptureSession_captureCallbacks, onCaptureSequenceAborted, 384, 64);
CHECK_FIELD_LAYOUT(ACameraCaptureSession_captureCallbacks, onCaptureBufferLost, 448, 64);

CHECK_STRUCT_LAYOUT(ACameraCaptureSession_stateCallbacks, 256, 64);
CHECK_FIELD_LAYOUT(ACameraCaptureSession_stateCallbacks, context, 0, 64);
CHECK_FIELD_LAYOUT(ACameraCaptureSession_stateCallbacks, onClosed, 64, 64);
CHECK_FIELD_LAYOUT(ACameraCaptureSession_stateCallbacks, onReady, 128, 64);
CHECK_FIELD_LAYOUT(ACameraCaptureSession_stateCallbacks, onActive, 192, 64);

CHECK_STRUCT_LAYOUT(ACameraDevice_StateCallbacks, 192, 64);
CHECK_FIELD_LAYOUT(ACameraDevice_StateCallbacks, context, 0, 64);
CHECK_FIELD_LAYOUT(ACameraDevice_StateCallbacks, onDisconnected, 64, 64);
CHECK_FIELD_LAYOUT(ACameraDevice_StateCallbacks, onError, 128, 64);

#else

#error "Unknown guest/host arch combination"

#endif

CHECK_STRUCT_LAYOUT(camera_status_t, 32, 32);
