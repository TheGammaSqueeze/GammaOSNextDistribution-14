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

#include "capture_session_utils.h"

#include "zsl_snapshot_capture_session.h"

namespace android {
namespace google_camera_hal {

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
    ProcessBatchCaptureResultFunc process_batch_capture_result) {
  // first pass: check predefined wrapper capture session
  for (auto sessionEntry : wrapper_capture_session_entries) {
    if (sessionEntry.IsStreamConfigurationSupported(camera_device_session_hwl,
                                                    stream_config)) {
      return sessionEntry.CreateSession(
          stream_config, external_capture_session_entries,
          capture_session_entries, hwl_session_callback,
          camera_buffer_allocator_hwl, camera_device_session_hwl, hal_config,
          process_capture_result, notify);
    }
  }

  // second pass: check loaded external capture sessions
  for (auto externalSession : external_capture_session_entries) {
    if (externalSession->IsStreamConfigurationSupported(
            camera_device_session_hwl, stream_config)) {
      return externalSession->CreateSession(
          camera_device_session_hwl, stream_config, process_capture_result,
          notify, hwl_session_callback, hal_config, camera_buffer_allocator_hwl);
    }
  }

  // third pass: check predefined capture sessions
  for (auto sessionEntry : capture_session_entries) {
    if (sessionEntry.IsStreamConfigurationSupported(camera_device_session_hwl,
                                                    stream_config)) {
      return sessionEntry.CreateSession(
          camera_device_session_hwl, stream_config, process_capture_result,
          process_batch_capture_result, notify, hwl_session_callback,
          hal_config, camera_buffer_allocator_hwl);
    }
  }
  return nullptr;
}

}  // namespace google_camera_hal
}  // namespace android