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

//#define LOG_NDEBUG 0
#define LOG_TAG "GCH_RealtimeZslRequestProcessor"
#define ATRACE_TAG ATRACE_TAG_CAMERA
#include "realtime_zsl_request_processor.h"

#include <log/log.h>
#include <utils/Trace.h>

#include <cstdint>
#include <shared_mutex>

#include "hal_types.h"
#include "hal_utils.h"
#include "system/graphics-base-v1.0.h"
#include "utils/Errors.h"
#include "vendor_tag_defs.h"

namespace android {
namespace google_camera_hal {

namespace {
// The width and height will be selected according to the following priority.
// 1. select the JPEG size if it is in the supported output YUV list
// 2. select the smallest output YUV size that
//    1) has width/height ratio between the width/height ratio of JPEG and the
//       max available output size
//    2) is larger than JPEG size
status_t SelectWidthAndHeight(uint32_t jpeg_width, uint32_t jpeg_height,
                              CameraDeviceSessionHwl& device_session_hwl,
                              uint32_t& selected_width,
                              uint32_t& selected_height) {
  std::unique_ptr<HalCameraMetadata> characteristics;
  status_t res = device_session_hwl.GetCameraCharacteristics(&characteristics);
  if (res != OK) {
    ALOGE("%s: GetCameraCharacteristics failed.", __FUNCTION__);
    return BAD_VALUE;
  }

  camera_metadata_ro_entry entry;
  res = characteristics->Get(ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS,
                             &entry);
  if (res != OK) {
    ALOGE("%s: Not able to get ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS",
          __FUNCTION__);
    return BAD_VALUE;
  }
  int available_size = entry.count / 4;

  uint32_t max_width = 0, max_height = 0;
  for (int i = 0; i < available_size; ++i) {
    if (entry.data.i32[4 * i] ==
            android_pixel_format_t::HAL_PIXEL_FORMAT_YCBCR_420_888 &&
        entry.data.i32[4 * i + 3] == static_cast<int32_t>(StreamType::kOutput)) {
      uint32_t width = static_cast<uint32_t>(entry.data.i32[1 + 4 * i]);
      uint32_t height = static_cast<uint32_t>(entry.data.i32[2 + 4 * i]);

      // The request JPEG size is a supported output YUV size. It is our top
      // choice, return directly if found.
      if (jpeg_width == width && jpeg_height == height) {
        selected_height = jpeg_height;
        selected_width = jpeg_width;
        return OK;
      }
      max_width = std::max(max_width, width);
      max_height = std::max(max_height, height);
    }
  }

  bool selected = false;
  for (int i = 0; i < available_size; i++) {
    if (entry.data.i32[4 * i] ==
            android_pixel_format_t::HAL_PIXEL_FORMAT_YCBCR_420_888 &&
        entry.data.i32[4 * i + 3] == static_cast<int32_t>(StreamType::kOutput)) {
      uint32_t width = static_cast<uint32_t>(entry.data.i32[1 + 4 * i]);
      uint32_t height = static_cast<uint32_t>(entry.data.i32[2 + 4 * i]);
      if (width < jpeg_width || height < jpeg_height) {
        // YUV size is smaller than JPEG size
        continue;
      }
      if (selected && width > selected_width) {
        // Already found a smaller YUV size that fulfill the request
        continue;
      }

      // Select the smallest supported YUV size with width/height ratio between
      // JPEG and the max available output size. It is our second choice.
      if ((jpeg_height * width - jpeg_width * height) *
              (height * max_width - width * max_height) >=
          0) {
        selected = true;
        selected_height = height;
        selected_width = width;
      }
    }
  }
  if (!selected) {
    // Select the largest supported YUV size. That is our last choice.
    selected_height = max_height;
    selected_width = max_width;
  }
  return OK;
}
}  // namespace

std::unique_ptr<RealtimeZslRequestProcessor> RealtimeZslRequestProcessor::Create(
    CameraDeviceSessionHwl* device_session_hwl,
    android_pixel_format_t pixel_format) {
  ATRACE_CALL();
  if (device_session_hwl == nullptr) {
    ALOGE("%s: device_session_hwl is nullptr", __FUNCTION__);
    return nullptr;
  }

  auto request_processor = std::unique_ptr<RealtimeZslRequestProcessor>(
      new RealtimeZslRequestProcessor(pixel_format, device_session_hwl));
  if (request_processor == nullptr) {
    ALOGE("%s: Creating RealtimeZslRequestProcessor failed.", __FUNCTION__);
    return nullptr;
  }

  status_t res = request_processor->Initialize(device_session_hwl);
  if (res != OK) {
    ALOGE("%s: Initializing RealtimeZslRequestProcessor failed: %s (%d).",
          __FUNCTION__, strerror(-res), res);
    return nullptr;
  }

  return request_processor;
}

status_t RealtimeZslRequestProcessor::Initialize(
    CameraDeviceSessionHwl* device_session_hwl) {
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
  if (pixel_format_ == android_pixel_format_t::HAL_PIXEL_FORMAT_RAW10) {
    res = characteristics->Get(VendorTagIds::kHdrUsageMode, &entry);
    if (res == OK) {
      hdr_mode_ = static_cast<HdrMode>(entry.data.u8[0]);
    }
  }

  return OK;
}

status_t RealtimeZslRequestProcessor::ConfigureStreams(
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

  // For YUV ZSL, we will use the JPEG size for ZSL buffer size. We already
  // checked the size is supported in capture session.
  if (pixel_format_ == android_pixel_format_t::HAL_PIXEL_FORMAT_YCBCR_420_888) {
    for (const auto& stream : stream_config.streams) {
      if (utils::IsSoftwareDenoiseEligibleSnapshotStream(stream)) {
        if (SelectWidthAndHeight(stream.width, stream.height,
                                 *device_session_hwl_, active_array_width_,
                                 active_array_height_) != OK) {
          ALOGE("%s: failed to select ZSL YUV buffer width and height",
                __FUNCTION__);
          return BAD_VALUE;
        }
        ALOGI("%s, Snapshot size is (%d x %d), selected size is (%d x %d)",
              __FUNCTION__, stream.width, stream.height, active_array_width_,
              active_array_height_);
        break;
      }
    }
  }

  // Register internal stream
  Stream stream_to_add;
  stream_to_add.stream_type = StreamType::kOutput;
  stream_to_add.width = active_array_width_;
  stream_to_add.height = active_array_height_;
  stream_to_add.format = pixel_format_;
  stream_to_add.usage = 0;
  stream_to_add.rotation = StreamRotation::kRotation0;
  stream_to_add.data_space = HAL_DATASPACE_ARBITRARY;
  // For YUV ZSL buffer, if the stream configuration constains physical stream,
  // we will add the new stream as physical stream. As we support physical
  // streams only or logical streams only combination. We can check the stream
  // type of the first stream in the list.
  if (pixel_format_ == android_pixel_format_t::HAL_PIXEL_FORMAT_YCBCR_420_888 &&
      stream_config.streams[0].is_physical_camera_stream) {
    stream_to_add.is_physical_camera_stream = true;
    stream_to_add.physical_camera_id =
        stream_config.streams[0].physical_camera_id;
  }

  status_t result = internal_stream_manager->RegisterNewInternalStream(
      stream_to_add, &stream_id_);
  if (result != OK) {
    ALOGE("%s: RegisterNewInternalStream failed.", __FUNCTION__);
    return UNKNOWN_ERROR;
  }

  internal_stream_manager_ = internal_stream_manager;
  // Set id back to stream and then HWL can get correct HAL stream ID
  stream_to_add.id = stream_id_;

  process_block_stream_config->streams = stream_config.streams;
  // Add internal stream
  process_block_stream_config->streams.push_back(stream_to_add);
  process_block_stream_config->operation_mode = stream_config.operation_mode;
  process_block_stream_config->session_params =
      HalCameraMetadata::Clone(stream_config.session_params.get());
  process_block_stream_config->stream_config_counter =
      stream_config.stream_config_counter;
  process_block_stream_config->log_id = stream_config.log_id;

  return OK;
}

status_t RealtimeZslRequestProcessor::SetProcessBlock(
    std::unique_ptr<ProcessBlock> process_block) {
  ATRACE_CALL();
  if (process_block == nullptr) {
    ALOGE("%s: process_block is nullptr", __FUNCTION__);
    return BAD_VALUE;
  }

  std::lock_guard lock(process_block_lock_);
  if (process_block_ != nullptr) {
    ALOGE("%s: Already configured.", __FUNCTION__);
    return ALREADY_EXISTS;
  }

  process_block_ = std::move(process_block);
  return OK;
}

status_t RealtimeZslRequestProcessor::ProcessRequest(
    const CaptureRequest& request) {
  ATRACE_CALL();
  std::shared_lock lock(process_block_lock_);
  if (process_block_ == nullptr) {
    ALOGE("%s: Not configured yet.", __FUNCTION__);
    return NO_INIT;
  }

  if (is_hdrplus_zsl_enabled_ && request.settings != nullptr) {
    camera_metadata_ro_entry entry = {};
    status_t res =
        request.settings->Get(VendorTagIds::kThermalThrottling, &entry);
    if (res != OK || entry.count != 1) {
      ALOGW("%s: Getting thermal throttling entry failed: %s(%d)", __FUNCTION__,
            strerror(-res), res);
    } else if (entry.data.u8[0] == true) {
      // Disable HDR+ ZSL once thermal throttles.
      is_hdrplus_zsl_enabled_ = false;
      ALOGI("%s: HDR+ ZSL disabled due to thermal throttling", __FUNCTION__);
    }
  }

  // Update if preview intent has been requested.
  camera_metadata_ro_entry entry;
  if (!preview_intent_seen_ && request.settings != nullptr &&
      request.settings->Get(ANDROID_CONTROL_CAPTURE_INTENT, &entry) == OK) {
    if (entry.count == 1 &&
        *entry.data.u8 == ANDROID_CONTROL_CAPTURE_INTENT_PREVIEW) {
      preview_intent_seen_ = true;
      ALOGI("%s: First request with preview intent. ZSL starts.", __FUNCTION__);
    }
  }

  CaptureRequest block_request;

  block_request.frame_number = request.frame_number;
  block_request.settings = HalCameraMetadata::Clone(request.settings.get());
  block_request.input_buffers = request.input_buffers;
  block_request.output_buffers = request.output_buffers;

  for (auto& metadata : request.input_buffer_metadata) {
    block_request.input_buffer_metadata.push_back(
        HalCameraMetadata::Clone(metadata.get()));
  }

  for (auto& [camera_id, physical_metadata] : request.physical_camera_settings) {
    block_request.physical_camera_settings[camera_id] =
        HalCameraMetadata::Clone(physical_metadata.get());
  }

  if (is_hdrplus_zsl_enabled_ ||
      pixel_format_ == android_pixel_format_t::HAL_PIXEL_FORMAT_YCBCR_420_888) {
    // Get one bffer from internal stream manager
    StreamBuffer buffer = {};
    status_t result;
    if (preview_intent_seen_) {
      result = internal_stream_manager_->GetStreamBuffer(stream_id_, &buffer);
      if (result != OK) {
        ALOGE("%s: frame:%d GetStreamBuffer failed.", __FUNCTION__,
              request.frame_number);
        return UNKNOWN_ERROR;
      }
    }

    // Add output to capture request
    if (preview_intent_seen_) {
      block_request.output_buffers.push_back(buffer);
    }

    if (block_request.settings != nullptr && is_hdrplus_zsl_enabled_) {
      bool enable_hybrid_ae =
          (hdr_mode_ == HdrMode::kNonHdrplusMode ? false : true);
      result = hal_utils::ModifyRealtimeRequestForHdrplus(
          block_request.settings.get(), enable_hybrid_ae);
      if (result != OK) {
        ALOGE("%s: ModifyRealtimeRequestForHdrplus (%d) fail", __FUNCTION__,
              request.frame_number);
        return UNKNOWN_ERROR;
      }

      if (hdr_mode_ != HdrMode::kHdrplusMode) {
        uint8_t processing_mode =
            static_cast<uint8_t>(ProcessingMode::kIntermediateProcessing);
        block_request.settings->Set(VendorTagIds::kProcessingMode,
                                    &processing_mode,
                                    /*data_count=*/1);
      }
    }
  }

  std::vector<ProcessBlockRequest> block_requests(1);
  block_requests[0].request = std::move(block_request);
  return process_block_->ProcessRequests(block_requests, request);
}

status_t RealtimeZslRequestProcessor::Flush() {
  ATRACE_CALL();
  std::shared_lock lock(process_block_lock_);
  if (process_block_ == nullptr) {
    return OK;
  }

  return process_block_->Flush();
}

}  // namespace google_camera_hal
}  // namespace android