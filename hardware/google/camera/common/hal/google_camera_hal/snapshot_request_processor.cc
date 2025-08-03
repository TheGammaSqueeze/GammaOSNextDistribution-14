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
#include "system/graphics-base-v1.0.h"
#define LOG_TAG "GCH_SnapshotRequestProcessor"
#define ATRACE_TAG ATRACE_TAG_CAMERA
#include <log/log.h>
#include <utils/Trace.h>

#include "snapshot_request_processor.h"
#include "vendor_tag_defs.h"

namespace android {
namespace google_camera_hal {

std::unique_ptr<SnapshotRequestProcessor> SnapshotRequestProcessor::Create(
    CameraDeviceSessionHwl* device_session_hwl,
    HwlSessionCallback session_callback, int32_t yuv_stream_id) {
  ATRACE_CALL();
  if (device_session_hwl == nullptr) {
    ALOGE("%s: device_session_hwl (%p) is nullptr", __FUNCTION__,
          device_session_hwl);
    return nullptr;
  }

  auto request_processor = std::unique_ptr<SnapshotRequestProcessor>(
      new SnapshotRequestProcessor(session_callback));
  if (request_processor == nullptr) {
    ALOGE("%s: Creating SnapshotRequestProcessor failed.", __FUNCTION__);
    return nullptr;
  }

  status_t res =
      request_processor->Initialize(device_session_hwl, yuv_stream_id);
  if (res != OK) {
    ALOGE("%s: Initializing SnapshotRequestProcessor failed: %s (%d).",
          __FUNCTION__, strerror(-res), res);
    return nullptr;
  }

  return request_processor;
}

status_t SnapshotRequestProcessor::Initialize(
    CameraDeviceSessionHwl* device_session_hwl, int32_t yuv_stream_id) {
  ATRACE_CALL();
  std::unique_ptr<HalCameraMetadata> characteristics;
  status_t res = device_session_hwl->GetCameraCharacteristics(&characteristics);
  if (res != OK) {
    ALOGE("%s: GetCameraCharacteristics failed.", __FUNCTION__);
    return BAD_VALUE;
  }

  camera_metadata_ro_entry entry;
  res = characteristics->Get(
      ANDROID_SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE, &entry);
  if (res == OK) {
    active_array_width_ = entry.data.i32[2];
    active_array_height_ = entry.data.i32[3];
    ALOGI("%s Active size (%d x %d).", __FUNCTION__, active_array_width_,
          active_array_height_);
  } else {
    ALOGE("%s Get active size failed: %s (%d).", __FUNCTION__, strerror(-res),
          res);
    return res;
  }

  yuv_stream_id_ = yuv_stream_id;

  return OK;
}

status_t SnapshotRequestProcessor::ConfigureStreams(
    InternalStreamManager* internal_stream_manager,
    const StreamConfiguration& stream_config,
    StreamConfiguration* process_block_stream_config) {
  ATRACE_CALL();
  if (process_block_stream_config == nullptr ||
      internal_stream_manager == nullptr) {
    ALOGE(
        "%s: process_block_stream_config (%p) is nullptr or "
        "internal_stream_manager (%p) is nullptr",
        __FUNCTION__, process_block_stream_config, internal_stream_manager);
    return BAD_VALUE;
  }

  internal_stream_manager_ = internal_stream_manager;

  Stream yuv_stream;
  yuv_stream.stream_type = StreamType::kInput;
  yuv_stream.width = active_array_width_;
  yuv_stream.height = active_array_height_;
  yuv_stream.format = HAL_PIXEL_FORMAT_YCBCR_420_888;
  yuv_stream.usage = 0;
  yuv_stream.rotation = StreamRotation::kRotation0;
  yuv_stream.data_space = HAL_DATASPACE_ARBITRARY;
  // Set id back to yuv_stream and then HWL can get correct HAL stream ID
  yuv_stream.id = yuv_stream_id_;

  process_block_stream_config->streams = stream_config.streams;
  // Add internal yuv stream
  process_block_stream_config->streams.push_back(yuv_stream);
  process_block_stream_config->operation_mode = stream_config.operation_mode;
  process_block_stream_config->session_params =
      HalCameraMetadata::Clone(stream_config.session_params.get());
  process_block_stream_config->stream_config_counter =
      stream_config.stream_config_counter;
  process_block_stream_config->log_id = stream_config.log_id;

  return OK;
}

status_t SnapshotRequestProcessor::SetProcessBlock(
    std::unique_ptr<ProcessBlock> process_block) {
  ATRACE_CALL();
  if (process_block == nullptr) {
    ALOGE("%s: process_block is nullptr", __FUNCTION__);
    return BAD_VALUE;
  }

  std::lock_guard<std::mutex> lock(process_block_lock_);
  if (process_block_ != nullptr) {
    ALOGE("%s: Already configured.", __FUNCTION__);
    return ALREADY_EXISTS;
  }

  process_block_ = std::move(process_block);
  return OK;
}

bool SnapshotRequestProcessor::IsReadyForNextRequest() {
  ATRACE_CALL();
  if (internal_stream_manager_ == nullptr) {
    ALOGW("%s: internal_stream_manager_ nullptr", __FUNCTION__);
    return false;
  }
  if (internal_stream_manager_->IsPendingBufferEmpty(yuv_stream_id_) == false) {
    return false;
  }
  return true;
}

status_t SnapshotRequestProcessor::ProcessRequest(const CaptureRequest& request) {
  ATRACE_CALL();
  std::lock_guard<std::mutex> lock(process_block_lock_);
  if (process_block_ == nullptr) {
    ALOGE("%s: Not configured yet.", __FUNCTION__);
    return NO_INIT;
  }

  if (IsReadyForNextRequest() == false) {
    return BAD_VALUE;
  }

  CaptureRequest block_request;
  block_request.frame_number = request.frame_number;
  block_request.settings = HalCameraMetadata::Clone(request.settings.get());

  for (const auto& output_buffer : request.output_buffers) {
    session_callback_.request_stream_buffers(
        output_buffer.stream_id, /*buffer_sizes=*/1,
        &block_request.output_buffers, request.frame_number);
  }

  for (auto& [camera_id, physical_metadata] : request.physical_camera_settings) {
    block_request.physical_camera_settings[camera_id] =
        HalCameraMetadata::Clone(physical_metadata.get());
  }

  // Get multiple yuv buffer and metadata from internal stream as input
  status_t result = internal_stream_manager_->GetMostRecentStreamBuffer(
      yuv_stream_id_, &(block_request.input_buffers),
      &(block_request.input_buffer_metadata), /*payload_frames=*/kZslBufferSize);
  if (result != OK) {
    ALOGE("%s: frame:%d GetStreamBuffer failed.", __FUNCTION__,
          request.frame_number);
    session_callback_.return_stream_buffers(block_request.output_buffers);
    return UNKNOWN_ERROR;
  }

  // TODO(mhtan): may need to remove some metadata here.
  std::vector<ProcessBlockRequest> block_requests(1);
  block_requests[0].request = std::move(block_request);
  ALOGD("%s: frame number %u is a snapshot request.", __FUNCTION__,
        request.frame_number);

  result = process_block_->ProcessRequests(block_requests, request);
  if (result != OK) {
    session_callback_.return_stream_buffers(
        block_requests[0].request.output_buffers);
  }

  return result;
}

status_t SnapshotRequestProcessor::Flush() {
  ATRACE_CALL();
  std::lock_guard<std::mutex> lock(process_block_lock_);
  if (process_block_ == nullptr) {
    return OK;
  }

  return process_block_->Flush();
}

}  // namespace google_camera_hal
}  // namespace android