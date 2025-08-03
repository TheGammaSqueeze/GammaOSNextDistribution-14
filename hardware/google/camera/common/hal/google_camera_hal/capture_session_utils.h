/*
 * Copyright (C) 2019 The Android Open Source Project
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

#ifndef HARDWARE_GOOGLE_CAMERA_HAL_GOOGLE_CAMERA_HAL_CAPTURE_SESSION_UTILS_H_
#define HARDWARE_GOOGLE_CAMERA_HAL_GOOGLE_CAMERA_HAL_CAPTURE_SESSION_UTILS_H_

#include <utils/Errors.h>

#include "camera_buffer_allocator_hwl.h"
#include "camera_device_session_hwl.h"
#include "capture_session.h"
#include "hal_types.h"
#include "hwl_types.h"

namespace android {
namespace google_camera_hal {

// Session function invoked to query if particular stream config supported
using StreamConfigSupportedFunc =
    std::function<bool(CameraDeviceSessionHwl* device_session_hwl,
                       const StreamConfiguration& stream_config)>;

// Session function invoked to create session instance
using CaptureSessionCreateFunc = std::function<std::unique_ptr<CaptureSession>(
    CameraDeviceSessionHwl* device_session_hwl,
    const StreamConfiguration& stream_config,
    ProcessCaptureResultFunc process_capture_result,
    ProcessBatchCaptureResultFunc process_capture_batch_result,
    NotifyFunc notify, HwlSessionCallback session_callback,
    std::vector<HalStream>* hal_configured_streams,
    CameraBufferAllocatorHwl* camera_allocator_hwl)>;

// define entry points to capture session
struct CaptureSessionEntryFuncs {
  StreamConfigSupportedFunc IsStreamConfigurationSupported;
  CaptureSessionCreateFunc CreateSession;
};

// Session function invoked to create wrapper capture session instance
using WrapperCaptureSessionCreateFunc =
    std::function<std::unique_ptr<CaptureSession>(
        const StreamConfiguration& stream_config,
        const std::vector<ExternalCaptureSessionFactory*>&
            external_capture_session_entries,
        const std::vector<CaptureSessionEntryFuncs>& capture_session_entries,
        HwlSessionCallback hwl_session_callback,
        CameraBufferAllocatorHwl* camera_buffer_allocator_hwl,
        CameraDeviceSessionHwl* camera_device_session_hwl,
        std::vector<HalStream>* hal_configured_streams,
        ProcessCaptureResultFunc process_capture_result, NotifyFunc notify)>;

// define entry points to capture session
struct WrapperCaptureSessionEntryFuncs {
  StreamConfigSupportedFunc IsStreamConfigurationSupported;
  WrapperCaptureSessionCreateFunc CreateSession;
};

// Select and create capture session.
// When consider_zsl_capture_session is enabled, we will first consider using
// ZslCaptureSession as a wrapper capture session when it supports the given
// configurations.
std::unique_ptr<CaptureSession> CreateCaptureSession(
    const StreamConfiguration& stream_config,
    const std::vector<WrapperCaptureSessionEntryFuncs>&
        wrapper_capture_session_entries,
    const std::vector<ExternalCaptureSessionFactory*>&
        external_capture_session_entries,
    const std::vector<CaptureSessionEntryFuncs>& capture_session_entries,
    HwlSessionCallback hwl_session_callback,
    CameraBufferAllocatorHwl* camera_buffer_allocator_hwl,
    CameraDeviceSessionHwl* camera_device_session_hwl,
    std::vector<HalStream>* hal_config,
    ProcessCaptureResultFunc process_capture_result, NotifyFunc notify,
    ProcessBatchCaptureResultFunc process_batch_capture_result = nullptr);

}  // namespace google_camera_hal
}  // namespace android

#endif  // HARDWARE_GOOGLE_CAMERA_HAL_GOOGLE_CAMERA_HAL_CAPTURE_SESSION_UTILS_H_