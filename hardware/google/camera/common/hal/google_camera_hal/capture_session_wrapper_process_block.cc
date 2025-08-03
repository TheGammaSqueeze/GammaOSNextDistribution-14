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

//#define LOG_NDEBUG 0
#include <cstddef>
#include <memory>

#define LOG_TAG "GCH_CaptureSessionWrapperProcessBlock"
#define ATRACE_TAG ATRACE_TAG_CAMERA
#include <log/log.h>
#include <utils/Trace.h>

#include "capture_session_utils.h"
#include "capture_session_wrapper_process_block.h"
#include "hal_types.h"
#include "hal_utils.h"
#include "process_block.h"
#include "result_processor.h"
#include "utils/Errors.h"

namespace android {
namespace google_camera_hal {

std::unique_ptr<CaptureSessionWrapperProcessBlock>
CaptureSessionWrapperProcessBlock::Create(
    const std::vector<ExternalCaptureSessionFactory*>&
        external_capture_session_entries,
    const std::vector<CaptureSessionEntryFuncs>& capture_session_entries,
    HwlSessionCallback hwl_session_callback,
    CameraBufferAllocatorHwl* camera_buffer_allocator_hwl,
    CameraDeviceSessionHwl* device_session_hwl,
    std::vector<HalStream>* hal_config) {
  ATRACE_CALL();
  if (!IsSupported(device_session_hwl)) {
    ALOGE("%s: Not supported.", __FUNCTION__);
    return nullptr;
  }

  auto block = std::unique_ptr<CaptureSessionWrapperProcessBlock>(
      new CaptureSessionWrapperProcessBlock(
          external_capture_session_entries, capture_session_entries,
          hwl_session_callback, camera_buffer_allocator_hwl, device_session_hwl,
          hal_config));
  if (block == nullptr) {
    ALOGE("%s: Creating CaptureSessionWrapperProcessBlock failed.",
          __FUNCTION__);
    return nullptr;
  }

  return block;
}

bool CaptureSessionWrapperProcessBlock::IsSupported(
    CameraDeviceSessionHwl* device_session_hwl) {
  if (device_session_hwl == nullptr) {
    ALOGE("%s: device_session_hwl is nullptr", __FUNCTION__);
    return false;
  }

  return true;
}

CaptureSessionWrapperProcessBlock::CaptureSessionWrapperProcessBlock(
    const std::vector<ExternalCaptureSessionFactory*>&
        external_capture_session_entries,
    const std::vector<CaptureSessionEntryFuncs>& capture_session_entries,
    HwlSessionCallback hwl_session_callback,
    CameraBufferAllocatorHwl* camera_buffer_allocator_hwl,
    CameraDeviceSessionHwl* camera_device_session_hwl,
    std::vector<HalStream>* hal_config)
    : kCameraId(camera_device_session_hwl->GetCameraId()),
      external_capture_session_entries_(external_capture_session_entries),
      capture_session_entries_(capture_session_entries),
      hwl_session_callback_(hwl_session_callback),
      camera_buffer_allocator_hwl_(camera_buffer_allocator_hwl),
      camera_device_session_hwl_(camera_device_session_hwl),
      hal_config_(hal_config) {
}

status_t CaptureSessionWrapperProcessBlock::SetResultProcessor(
    std::unique_ptr<ResultProcessor> result_processor) {
  ATRACE_CALL();
  std::lock_guard<std::mutex> lock(result_processor_lock_);

  result_processor_ = std::move(result_processor);
  return OK;
}

status_t CaptureSessionWrapperProcessBlock::ConfigureStreams(
    const StreamConfiguration& stream_config,
    const StreamConfiguration& /*overall_config*/) {
  ATRACE_CALL();
  std::lock_guard lock(configure_shared_mutex_);
  if (is_configured_) {
    ALOGE("%s: Already configured.", __FUNCTION__);
    return ALREADY_EXISTS;
  }

  if (result_processor_ == nullptr) {
    ALOGE(
        "%s: result processor is not set yet. Not able to set the callback "
        "function.",
        __FUNCTION__);
    return BAD_VALUE;
  }

  process_capture_result_ =
      ProcessCaptureResultFunc([this](std::unique_ptr<CaptureResult> result) {
        ProcessBlockResult process_block_result;
        process_block_result.result = std::move(result);
        result_processor_->ProcessResult(std::move(process_block_result));
      });
  notify_ = NotifyFunc([this](const NotifyMessage& message) {
    ProcessBlockNotifyMessage process_block_message{.message = message};
    result_processor_->Notify(std::move(process_block_message));
  });

  // TODO(mhtan): Add one more stream here
  embedded_capture_session_ = CreateCaptureSession(
      stream_config, /*wrapper_capture_session_entries=*/{},
      external_capture_session_entries_, capture_session_entries_,
      hwl_session_callback_, camera_buffer_allocator_hwl_,
      camera_device_session_hwl_, hal_config_, process_capture_result_, notify_);
  if (embedded_capture_session_ == nullptr) {
    ALOGE("%s: Not able to create embedded capture session.", __FUNCTION__);
    return BAD_VALUE;
  }

  is_configured_ = true;
  return OK;
}

status_t CaptureSessionWrapperProcessBlock::GetConfiguredHalStreams(
    std::vector<HalStream>* hal_streams) const {
  ATRACE_CALL();
  std::lock_guard lock(configure_shared_mutex_);
  if (hal_streams == nullptr) {
    ALOGE("%s: hal_streams is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  if (!is_configured_) {
    ALOGE("%s: Not configured yet.", __FUNCTION__);
    return NO_INIT;
  }

  return camera_device_session_hwl_->GetConfiguredHalStream(pipeline_id_,
                                                            hal_streams);
}

status_t CaptureSessionWrapperProcessBlock::ProcessRequests(
    const std::vector<ProcessBlockRequest>& process_block_requests,
    const CaptureRequest& remaining_session_request) {
  ATRACE_CALL();
  CaptureRequest request;
  request.frame_number = remaining_session_request.frame_number;
  for (auto& metadata : request.input_buffer_metadata) {
    request.input_buffer_metadata.push_back(
        HalCameraMetadata::Clone(metadata.get()));
  }
  request.input_buffers = remaining_session_request.input_buffers;
  request.input_height = remaining_session_request.input_height;
  request.input_width = remaining_session_request.input_width;
  for (auto& [camera_id, physical_metadata] :
       remaining_session_request.physical_camera_settings) {
    request.physical_camera_settings[camera_id] =
        HalCameraMetadata::Clone(physical_metadata.get());
  }
  request.settings =
      HalCameraMetadata::Clone(remaining_session_request.settings.get());

  request.output_buffers = process_block_requests[0].request.output_buffers;
  for (auto& buffer : request.output_buffers) {
    if (buffer.buffer != nullptr) {
      buffer.buffer_id = buffer.stream_id;
    }
  }

  return embedded_capture_session_->ProcessRequest(request);
}

status_t CaptureSessionWrapperProcessBlock::Flush() {
  ATRACE_CALL();
  std::shared_lock lock(configure_shared_mutex_);
  if (!is_configured_) {
    return OK;
  }

  return camera_device_session_hwl_->Flush();
}

}  // namespace google_camera_hal
}  // namespace android