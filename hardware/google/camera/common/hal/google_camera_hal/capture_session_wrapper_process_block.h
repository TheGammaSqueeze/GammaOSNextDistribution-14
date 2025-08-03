/*
 * Copyright (C) 2021 The Android Open Source Project
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

#ifndef HARDWARE_GOOGLE_CAMERA_HAL_GOOGLE_CAMERA_HAL_CAPTURE_SESSION_WRAPPER_PROCESS_BLOCK_H_
#define HARDWARE_GOOGLE_CAMERA_HAL_GOOGLE_CAMERA_HAL_CAPTURE_SESSION_WRAPPER_PROCESS_BLOCK_H_

#include <shared_mutex>

#include "camera_device_session.h"
#include "capture_session.h"
#include "process_block.h"

namespace android {
namespace google_camera_hal {

// CaptureSessionWrapperProcessBlock implements a real-time ProcessBlock.
// It can process real-time capture requests for a single physical camera or a
// logical camera. It creates an embedded CaptureSession and the coming requests
// will be processed by the embedded CaptureSession.
class CaptureSessionWrapperProcessBlock : public ProcessBlock {
 public:
  // Create a CaptureSessionWrapperProcessBlock.
  // device_session_hwl and capture_session are owned by the caller and must be
  // valid during the lifetime of this CaptureSessionWrapperProcessBlock.
  static std::unique_ptr<CaptureSessionWrapperProcessBlock> Create(
      const std::vector<ExternalCaptureSessionFactory*>&
          external_capture_session_entries,
      const std::vector<CaptureSessionEntryFuncs>& capture_session_entries,
      HwlSessionCallback hwl_session_callback,
      CameraBufferAllocatorHwl* camera_buffer_allocator_hwl,
      CameraDeviceSessionHwl* camera_device_session_hwl,
      std::vector<HalStream>* hal_config);

  virtual ~CaptureSessionWrapperProcessBlock() = default;

  // Override functions of ProcessBlock start.
  status_t ConfigureStreams(const StreamConfiguration& stream_config,
                            const StreamConfiguration& overall_config) override;

  status_t SetResultProcessor(
      std::unique_ptr<ResultProcessor> result_processor) override;

  status_t GetConfiguredHalStreams(
      std::vector<HalStream>* hal_streams) const override;

  status_t ProcessRequests(
      const std::vector<ProcessBlockRequest>& process_block_requests,
      const CaptureRequest& remaining_session_request) override;

  status_t Flush() override;
  // Override functions of ProcessBlock end.

 protected:
  CaptureSessionWrapperProcessBlock(
      const std::vector<ExternalCaptureSessionFactory*>&
          external_capture_session_entries,
      const std::vector<CaptureSessionEntryFuncs>& capture_session_entries,
      HwlSessionCallback hwl_session_callback,
      CameraBufferAllocatorHwl* camera_buffer_allocator_hwl,
      CameraDeviceSessionHwl* camera_device_session_hwl,
      std::vector<HalStream>* hal_config);

 private:
  // Camera ID of this process block.
  const uint32_t kCameraId;

  // If the real-time process block supports the device session.
  static bool IsSupported(CameraDeviceSessionHwl* device_session_hwl);

  // Invoked when the HWL pipeline sends a result.
  void NotifyHwlPipelineResult(std::unique_ptr<HwlPipelineResult> hwl_result);

  // Invoked when the HWL pipeline sends a message.
  void NotifyHwlPipelineMessage(uint32_t pipeline_id,
                                const NotifyMessage& message);

  mutable std::shared_mutex configure_shared_mutex_;

  // If streams are configured. Must be protected by configure_shared_mutex_.
  bool is_configured_ = false;

  // HWL pipeline ID. Must be protected by configure_shared_mutex_.
  uint32_t pipeline_id_ = 0;

  std::mutex result_processor_lock_;

  // Result processor. Must be protected by result_processor_lock_.
  std::unique_ptr<ResultProcessor> result_processor_;

  std::unique_ptr<CaptureSession> embedded_capture_session_;

  ProcessCaptureResultFunc process_capture_result_;
  NotifyFunc notify_;

  const std::vector<ExternalCaptureSessionFactory*>&
      external_capture_session_entries_;
  const std::vector<CaptureSessionEntryFuncs>& capture_session_entries_;
  HwlSessionCallback hwl_session_callback_;
  CameraBufferAllocatorHwl* camera_buffer_allocator_hwl_ = nullptr;
  CameraDeviceSessionHwl* camera_device_session_hwl_ = nullptr;

  std::vector<HalStream>* hal_config_;
};

}  // namespace google_camera_hal
}  // namespace android

#endif  // HARDWARE_GOOGLE_CAMERA_HAL_GOOGLE_CAMERA_HAL_CAPTURE_SESSION_WRAPPER_PROCESS_BLOCK_H_