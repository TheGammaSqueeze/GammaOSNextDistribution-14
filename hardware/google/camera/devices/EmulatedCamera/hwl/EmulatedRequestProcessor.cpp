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

#define LOG_TAG "EmulatedRequestProcessor"
#define ATRACE_TAG ATRACE_TAG_CAMERA

#include "EmulatedRequestProcessor.h"

#include <HandleImporter.h>
#include <hardware/gralloc.h>
#include <log/log.h>
#include <sync/sync.h>
#include <utils/Timers.h>
#include <utils/Trace.h>

#include <memory>

#include "GrallocSensorBuffer.h"

namespace android {

using ::android::frameworks::sensorservice::V1_0::ISensorManager;
using ::android::frameworks::sensorservice::V1_0::Result;
using android::hardware::camera::common::V1_0::helper::HandleImporter;
using ::android::hardware::sensors::V1_0::SensorInfo;
using ::android::hardware::sensors::V1_0::SensorType;
using google_camera_hal::ErrorCode;
using google_camera_hal::HwlPipelineResult;
using google_camera_hal::MessageType;
using google_camera_hal::NotifyMessage;

EmulatedRequestProcessor::EmulatedRequestProcessor(
    uint32_t camera_id, sp<EmulatedSensor> sensor,
    const HwlSessionCallback& session_callback)
    : camera_id_(camera_id),
      sensor_(sensor),
      session_callback_(session_callback),
      request_state_(std::make_unique<EmulatedLogicalRequestState>(camera_id)) {
  ATRACE_CALL();
  request_thread_ = std::thread([this] { this->RequestProcessorLoop(); });
  importer_ = std::make_shared<HandleImporter>();
}

EmulatedRequestProcessor::~EmulatedRequestProcessor() {
  ATRACE_CALL();
  processor_done_ = true;
  request_thread_.join();

  auto ret = sensor_->ShutDown();
  if (ret != OK) {
    ALOGE("%s: Failed during sensor shutdown %s (%d)", __FUNCTION__,
          strerror(-ret), ret);
  }

  if (sensor_event_queue_.get() != nullptr) {
    sensor_event_queue_->disableSensor(sensor_handle_);
    sensor_event_queue_.clear();
    sensor_event_queue_ = nullptr;
  }
}

status_t EmulatedRequestProcessor::ProcessPipelineRequests(
    uint32_t frame_number, std::vector<HwlPipelineRequest>& requests,
    const std::vector<EmulatedPipeline>& pipelines,
    const DynamicStreamIdMapType& dynamic_stream_id_map,
    bool use_default_physical_camera) {
  ATRACE_CALL();
  status_t res = OK;

  std::unique_lock<std::mutex> lock(process_mutex_);

  for (auto& request : requests) {
    if (request.pipeline_id >= pipelines.size()) {
      ALOGE("%s: Pipeline request with invalid pipeline id: %u", __FUNCTION__,
            request.pipeline_id);
      return BAD_VALUE;
    }

    while (pending_requests_.size() > EmulatedSensor::kPipelineDepth) {
      auto result = request_condition_.wait_for(
          lock, std::chrono::nanoseconds(
                    EmulatedSensor::kSupportedFrameDurationRange[1]));
      if (result == std::cv_status::timeout) {
        ALOGE("%s: Timed out waiting for a pending request slot", __FUNCTION__);
        return TIMED_OUT;
      }
    }

    res = request_state_->UpdateRequestForDynamicStreams(
        &request, pipelines, dynamic_stream_id_map, use_default_physical_camera);
    if (res != OK) {
      ALOGE("%s: Failed to update request for dynamic streams: %s(%d)",
            __FUNCTION__, strerror(-res), res);
      return res;
    }

    auto output_buffers = CreateSensorBuffers(
        frame_number, request.output_buffers,
        pipelines[request.pipeline_id].streams, request.pipeline_id,
        pipelines[request.pipeline_id].cb, /*override_width*/ 0,
        /*override_height*/ 0);
    if (output_buffers == nullptr) {
      return NO_MEMORY;
    }

    auto input_buffers = CreateSensorBuffers(
        frame_number, request.input_buffers,
        pipelines[request.pipeline_id].streams, request.pipeline_id,
        pipelines[request.pipeline_id].cb, request.input_width,
        request.input_height);

    // Check if there are any settings that need to be overridden.
    camera_metadata_ro_entry_t entry;
    if (request.settings.get() != nullptr) {
      auto ret = request.settings.get()->Get(ANDROID_CONTROL_SETTINGS_OVERRIDE,
                                             &entry);
      if ((ret == OK) && (entry.count == 1)) {
        std::unique_ptr<HalCameraMetadata> override_setting =
            HalCameraMetadata::Clone(request.settings.get());
        override_settings_.push({.settings = std::move(override_setting),
                                 .frame_number = frame_number});
      }
    } else {
      override_settings_.push(
          {.settings = nullptr, .frame_number = frame_number});
    }
    pending_requests_.push(
        {.frame_number = frame_number,
         .pipeline_id = request.pipeline_id,
         .callback = pipelines[request.pipeline_id].cb,
         .settings = HalCameraMetadata::Clone(request.settings.get()),
         .input_buffers = std::move(input_buffers),
         .output_buffers = std::move(output_buffers)});
  }

  return OK;
}

std::unique_ptr<Buffers> EmulatedRequestProcessor::CreateSensorBuffers(
    uint32_t frame_number, const std::vector<StreamBuffer>& buffers,
    const std::unordered_map<uint32_t, EmulatedStream>& streams,
    uint32_t pipeline_id, HwlPipelineCallback cb, int32_t override_width,
    int32_t override_height) {
  if (buffers.empty()) {
    return nullptr;
  }

  std::vector<StreamBuffer> requested_buffers;
  for (auto& buffer : buffers) {
    if (buffer.buffer != nullptr) {
      requested_buffers.push_back(buffer);
      continue;
    }

    if (session_callback_.request_stream_buffers != nullptr) {
      std::vector<StreamBuffer> one_requested_buffer;
      status_t res = session_callback_.request_stream_buffers(
          buffer.stream_id, 1, &one_requested_buffer, frame_number);
      if (res != OK) {
        ALOGE("%s: request_stream_buffers failed: %s(%d)", __FUNCTION__,
              strerror(-res), res);
        continue;
      }
      if (one_requested_buffer.size() != 1 ||
          one_requested_buffer[0].buffer == nullptr) {
        ALOGE("%s: request_stream_buffers failed to return a valid buffer",
              __FUNCTION__);
        continue;
      }
      requested_buffers.push_back(one_requested_buffer[0]);
    }
  }

  if (requested_buffers.size() < buffers.size()) {
    ALOGE(
        "%s: Failed to acquire all sensor buffers: %zu acquired, %zu requested",
        __FUNCTION__, requested_buffers.size(), buffers.size());
    // This only happens for HAL buffer manager use case.
    if (session_callback_.return_stream_buffers != nullptr) {
      session_callback_.return_stream_buffers(requested_buffers);
    }
    requested_buffers.clear();
  }

  auto sensor_buffers = std::make_unique<Buffers>();
  sensor_buffers->reserve(requested_buffers.size());
  for (auto& buffer : requested_buffers) {
    auto sensor_buffer = CreateSensorBuffer(
        frame_number, streams.at(buffer.stream_id), pipeline_id, cb, buffer,
        override_width, override_height);
    if (sensor_buffer.get() != nullptr) {
      sensor_buffers->push_back(std::move(sensor_buffer));
    }
  }

  return sensor_buffers;
}

void EmulatedRequestProcessor::NotifyFailedRequest(const PendingRequest& request) {
  if (request.output_buffers != nullptr) {
    // Mark all output buffers for this request in order not to send
    // ERROR_BUFFER for them.
    for (auto& output_buffer : *(request.output_buffers)) {
      output_buffer->is_failed_request = true;
    }
  }

  NotifyMessage msg = {
      .type = MessageType::kError,
      .message.error = {.frame_number = request.frame_number,
                        .error_stream_id = -1,
                        .error_code = ErrorCode::kErrorRequest}};
  request.callback.notify(request.pipeline_id, msg);
}

status_t EmulatedRequestProcessor::Flush() {
  std::lock_guard<std::mutex> lock(process_mutex_);
  // First flush in-flight requests
  auto ret = sensor_->Flush();

  // Then the rest of the pending requests
  while (!pending_requests_.empty()) {
    const auto& request = pending_requests_.front();
    NotifyFailedRequest(request);
    pending_requests_.pop();
  }

  return ret;
}

status_t EmulatedRequestProcessor::GetBufferSizeAndStride(
    const EmulatedStream& stream, buffer_handle_t buffer,
    uint32_t* size /*out*/, uint32_t* stride /*out*/) {
  if (size == nullptr) {
    return BAD_VALUE;
  }

  switch (stream.override_format) {
    case HAL_PIXEL_FORMAT_RGB_888:
      *stride = stream.width * 3;
      *size = (*stride) * stream.height;
      break;
    case HAL_PIXEL_FORMAT_RGBA_8888:
      *stride = stream.width * 4;
      *size = (*stride) * stream.height;
      break;
    case HAL_PIXEL_FORMAT_Y16:
      if (stream.override_data_space == HAL_DATASPACE_DEPTH) {
        *stride = AlignTo(AlignTo(stream.width, 2) * 2, 16);
        *size = (*stride) * AlignTo(stream.height, 2);
      } else {
        return BAD_VALUE;
      }
      break;
    case HAL_PIXEL_FORMAT_BLOB:
      if (stream.override_data_space == HAL_DATASPACE_V0_JFIF) {
        *size = stream.buffer_size;
        *stride = *size;
      } else {
        return BAD_VALUE;
      }
      break;
    case HAL_PIXEL_FORMAT_RAW16:
      if (importer_->getMonoPlanarStrideBytes(buffer, stride) != NO_ERROR) {
        *stride = stream.width * 2;
      }
      *size = (*stride) * stream.height;
      break;
    default:
      return BAD_VALUE;
  }

  return OK;
}

status_t EmulatedRequestProcessor::LockSensorBuffer(
    const EmulatedStream& stream, buffer_handle_t buffer, int32_t width,
    int32_t height, SensorBuffer* sensor_buffer /*out*/) {
  if (sensor_buffer == nullptr) {
    return BAD_VALUE;
  }

  auto usage = GRALLOC_USAGE_SW_WRITE_OFTEN;
  bool isYUV_420_888 = stream.override_format == HAL_PIXEL_FORMAT_YCBCR_420_888;
  bool isP010 = static_cast<android_pixel_format_v1_1_t>(
                    stream.override_format) == HAL_PIXEL_FORMAT_YCBCR_P010;
  if ((isYUV_420_888) || (isP010)) {
    android::Rect map_rect = {0, 0, width, height};
    auto yuv_layout = importer_->lockYCbCr(buffer, usage, map_rect);
    if ((yuv_layout.y != nullptr) && (yuv_layout.cb != nullptr) &&
        (yuv_layout.cr != nullptr)) {
      sensor_buffer->plane.img_y_crcb.img_y =
          static_cast<uint8_t*>(yuv_layout.y);
      sensor_buffer->plane.img_y_crcb.img_cb =
          static_cast<uint8_t*>(yuv_layout.cb);
      sensor_buffer->plane.img_y_crcb.img_cr =
          static_cast<uint8_t*>(yuv_layout.cr);
      sensor_buffer->plane.img_y_crcb.y_stride = yuv_layout.ystride;
      sensor_buffer->plane.img_y_crcb.cbcr_stride = yuv_layout.cstride;
      sensor_buffer->plane.img_y_crcb.cbcr_step = yuv_layout.chroma_step;
      if (isYUV_420_888 && (yuv_layout.chroma_step == 2) &&
          std::abs(sensor_buffer->plane.img_y_crcb.img_cb -
                   sensor_buffer->plane.img_y_crcb.img_cr) != 1) {
        ALOGE(
            "%s: Unsupported YUV layout, chroma step: %zu U/V plane delta: %u",
            __FUNCTION__, yuv_layout.chroma_step,
            static_cast<unsigned>(
                std::abs(sensor_buffer->plane.img_y_crcb.img_cb -
                         sensor_buffer->plane.img_y_crcb.img_cr)));
        return BAD_VALUE;
      }
      sensor_buffer->plane.img_y_crcb.bytesPerPixel = isP010 ? 2 : 1;
    } else {
      ALOGE("%s: Failed to lock output buffer for stream id %d !", __FUNCTION__,
            stream.id);
      return BAD_VALUE;
    }
  } else {
    uint32_t buffer_size = 0, stride = 0;
    auto ret = GetBufferSizeAndStride(stream, buffer, &buffer_size, &stride);
    if (ret != OK) {
      ALOGE("%s: Unsupported pixel format: 0x%x", __FUNCTION__,
            stream.override_format);
      return BAD_VALUE;
    }
    if (stream.override_format == HAL_PIXEL_FORMAT_BLOB) {
      sensor_buffer->plane.img.img =
          static_cast<uint8_t*>(importer_->lock(buffer, usage, buffer_size));
    } else {
      android::Rect region{0, 0, width, height};
      sensor_buffer->plane.img.img =
          static_cast<uint8_t*>(importer_->lock(buffer, usage, region));
    }
    if (sensor_buffer->plane.img.img == nullptr) {
      ALOGE("%s: Failed to lock output buffer!", __FUNCTION__);
      return BAD_VALUE;
    }
    sensor_buffer->plane.img.stride_in_bytes = stride;
    sensor_buffer->plane.img.buffer_size = buffer_size;
  }

  return OK;
}

std::unique_ptr<SensorBuffer> EmulatedRequestProcessor::CreateSensorBuffer(
    uint32_t frame_number, const EmulatedStream& emulated_stream,
    uint32_t pipeline_id, HwlPipelineCallback callback,
    StreamBuffer stream_buffer, int32_t override_width,
    int32_t override_height) {
  auto buffer = std::make_unique<GrallocSensorBuffer>(importer_);

  auto stream = emulated_stream;
  // Make sure input stream formats are correctly mapped here
  if (stream.is_input) {
    stream.override_format = EmulatedSensor::OverrideFormat(
        stream.override_format,
        ANDROID_REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_STANDARD);
  }
  if (override_width > 0 && override_height > 0) {
    buffer->width = override_width;
    buffer->height = override_height;
  } else {
    buffer->width = stream.width;
    buffer->height = stream.height;
  }
  buffer->format = static_cast<PixelFormat>(stream.override_format);
  buffer->dataSpace = stream.override_data_space;
  buffer->color_space = stream.color_space;
  buffer->use_case = stream.use_case;
  buffer->stream_buffer = stream_buffer;
  buffer->pipeline_id = pipeline_id;
  buffer->callback = callback;
  buffer->frame_number = frame_number;
  buffer->camera_id = emulated_stream.is_physical_camera_stream
                          ? emulated_stream.physical_camera_id
                          : camera_id_;
  buffer->is_input = stream.is_input;
  // In case buffer processing is successful, flip this flag accordingly
  buffer->stream_buffer.status = BufferStatus::kError;

  if (buffer->stream_buffer.buffer != nullptr) {
    auto ret = LockSensorBuffer(stream, buffer->stream_buffer.buffer,
                                buffer->width, buffer->height, buffer.get());
    if (ret != OK) {
      buffer->is_failed_request = true;
      buffer = nullptr;
    }
  }

  if ((buffer.get() != nullptr) && (stream_buffer.acquire_fence != nullptr)) {
    auto fence_status = importer_->importFence(stream_buffer.acquire_fence,
                                               buffer->acquire_fence_fd);
    if (!fence_status) {
      ALOGE("%s: Failed importing acquire fence!", __FUNCTION__);
      buffer->is_failed_request = true;
      buffer = nullptr;
    }
  }

  return buffer;
}

std::unique_ptr<Buffers> EmulatedRequestProcessor::AcquireBuffers(
    Buffers* buffers) {
  if ((buffers == nullptr) || (buffers->empty())) {
    return nullptr;
  }

  auto acquired_buffers = std::make_unique<Buffers>();
  acquired_buffers->reserve(buffers->size());
  auto output_buffer = buffers->begin();
  while (output_buffer != buffers->end()) {
    status_t ret = OK;
    if ((*output_buffer)->acquire_fence_fd >= 0) {
      ret = sync_wait((*output_buffer)->acquire_fence_fd,
                      ns2ms(EmulatedSensor::kSupportedFrameDurationRange[1]));
      if (ret != OK) {
        ALOGE("%s: Fence sync failed: %s, (%d)", __FUNCTION__, strerror(-ret),
              ret);
      }
    }

    if (ret == OK) {
      acquired_buffers->push_back(std::move(*output_buffer));
    }

    output_buffer = buffers->erase(output_buffer);
  }

  return acquired_buffers;
}

void EmulatedRequestProcessor::RequestProcessorLoop() {
  ATRACE_CALL();

  bool vsync_status_ = true;
  while (!processor_done_ && vsync_status_) {
    {
      std::lock_guard<std::mutex> lock(process_mutex_);
      if (!pending_requests_.empty()) {
        status_t ret;
        const auto& request = pending_requests_.front();
        auto frame_number = request.frame_number;
        auto notify_callback = request.callback;
        auto pipeline_id = request.pipeline_id;

        auto output_buffers = AcquireBuffers(request.output_buffers.get());
        auto input_buffers = AcquireBuffers(request.input_buffers.get());
        if ((output_buffers != nullptr) && !output_buffers->empty()) {
          std::unique_ptr<EmulatedSensor::LogicalCameraSettings> logical_settings =
              std::make_unique<EmulatedSensor::LogicalCameraSettings>();

          std::unique_ptr<std::set<uint32_t>> physical_camera_output_ids =
              std::make_unique<std::set<uint32_t>>();
          for (const auto& it : *output_buffers) {
            if (it->camera_id != camera_id_) {
              physical_camera_output_ids->emplace(it->camera_id);
            }
          }

          // Repeating requests usually include valid settings only during the
          // initial call. Afterwards an invalid settings pointer means that
          // there are no changes in the parameters and Hal should re-use the
          // last valid values.
          // TODO: Add support for individual physical camera requests.
          if (request.settings.get() != nullptr) {
            auto override_frame_number =
                ApplyOverrideSettings(frame_number, request.settings);
            ret = request_state_->InitializeLogicalSettings(
                HalCameraMetadata::Clone(request.settings.get()),
                std::move(physical_camera_output_ids), override_frame_number,
                logical_settings.get());
            last_settings_ = HalCameraMetadata::Clone(request.settings.get());
          } else {
            auto override_frame_number =
                ApplyOverrideSettings(frame_number, last_settings_);
            ret = request_state_->InitializeLogicalSettings(
                HalCameraMetadata::Clone(last_settings_.get()),
                std::move(physical_camera_output_ids), override_frame_number,
                logical_settings.get());
          }

          if (ret == OK) {
            auto partial_result = request_state_->InitializeLogicalResult(
                pipeline_id, frame_number,
                /*partial result*/ true);
            auto result = request_state_->InitializeLogicalResult(
                pipeline_id, frame_number,
                /*partial result*/ false);
            // The screen rotation will be the same for all logical and physical devices
            uint32_t screen_rotation = screen_rotation_;
            for (auto it = logical_settings->begin();
                 it != logical_settings->end(); it++) {
              it->second.screen_rotation = screen_rotation;
            }

            sensor_->SetCurrentRequest(
                std::move(logical_settings), std::move(result),
                std::move(partial_result), std::move(input_buffers),
                std::move(output_buffers));
          } else {
            NotifyMessage msg{.type = MessageType::kError,
                              .message.error = {
                                  .frame_number = frame_number,
                                  .error_stream_id = -1,
                                  .error_code = ErrorCode::kErrorResult,
                              }};

            notify_callback.notify(pipeline_id, msg);
          }
        } else {
          // No further processing is needed, just fail the result which will
          // complete this request.
          NotifyMessage msg{.type = MessageType::kError,
                            .message.error = {
                                .frame_number = frame_number,
                                .error_stream_id = -1,
                                .error_code = ErrorCode::kErrorResult,
                            }};

          notify_callback.notify(pipeline_id, msg);
        }

        pending_requests_.pop();
        request_condition_.notify_one();
      }
    }

    vsync_status_ =
        sensor_->WaitForVSync(EmulatedSensor::kSupportedFrameDurationRange[1]);
  }
}

status_t EmulatedRequestProcessor::Initialize(
    std::unique_ptr<EmulatedCameraDeviceInfo> device_info,
    PhysicalDeviceMapPtr physical_devices) {
  std::lock_guard<std::mutex> lock(process_mutex_);
  return request_state_->Initialize(std::move(device_info),
                                    std::move(physical_devices));
}

void EmulatedRequestProcessor::SetSessionCallback(
    const HwlSessionCallback& hwl_session_callback) {
  std::lock_guard<std::mutex> lock(process_mutex_);
  session_callback_ = hwl_session_callback;
}

status_t EmulatedRequestProcessor::GetDefaultRequest(
    RequestTemplate type, std::unique_ptr<HalCameraMetadata>* default_settings) {
  std::lock_guard<std::mutex> lock(process_mutex_);
  return request_state_->GetDefaultRequest(type, default_settings);
}

uint32_t EmulatedRequestProcessor::ApplyOverrideSettings(
    uint32_t frame_number,
    const std::unique_ptr<HalCameraMetadata>& request_settings) {
  while (!override_settings_.empty() && request_settings.get() != nullptr) {
    auto override_frame_number = override_settings_.front().frame_number;
    bool repeatingOverride = (override_settings_.front().settings == nullptr);
    const auto& override_setting = repeatingOverride
                                       ? last_override_settings_
                                       : override_settings_.front().settings;

    camera_metadata_ro_entry_t entry;
    status_t ret =
        override_setting->Get(ANDROID_CONTROL_SETTINGS_OVERRIDE, &entry);
    bool overriding = false;
    if ((ret == OK) && (entry.count == 1) &&
        (entry.data.i32[0] == ANDROID_CONTROL_SETTINGS_OVERRIDE_ZOOM)) {
      ApplyOverrideZoom(override_setting, request_settings,
                        ANDROID_CONTROL_SETTINGS_OVERRIDE);
      ApplyOverrideZoom(override_setting, request_settings,
                        ANDROID_CONTROL_ZOOM_RATIO);
      ApplyOverrideZoom(override_setting, request_settings,
                        ANDROID_SCALER_CROP_REGION);
      ApplyOverrideZoom(override_setting, request_settings,
                        ANDROID_CONTROL_AE_REGIONS);
      ApplyOverrideZoom(override_setting, request_settings,
                        ANDROID_CONTROL_AWB_REGIONS);
      ApplyOverrideZoom(override_setting, request_settings,
                        ANDROID_CONTROL_AF_REGIONS);
      overriding = true;
    }
    if (!repeatingOverride) {
      last_override_settings_ = HalCameraMetadata::Clone(override_setting.get());
    }

    override_settings_.pop();
    // If there are multiple queued override settings, skip until the speed-up
    // is at least 2 frames.
    if (override_frame_number - frame_number >= kZoomSpeedup) {
      // If the request's settings override isn't ON, do not return
      // override_frame_number. Return 0 to indicate there is no
      // override happening.
      return overriding ? override_frame_number : 0;
    }
  }
  return 0;
}

void EmulatedRequestProcessor::ApplyOverrideZoom(
    const std::unique_ptr<HalCameraMetadata>& override_setting,
    const std::unique_ptr<HalCameraMetadata>& request_settings,
    camera_metadata_tag tag) {
  status_t ret;
  camera_metadata_ro_entry_t entry;
  ret = override_setting->Get(tag, &entry);
  if (ret == OK) {
    if (entry.type == TYPE_INT32) {
      request_settings->Set(tag, entry.data.i32, entry.count);
    } else if (entry.type == TYPE_FLOAT) {
      request_settings->Set(tag, entry.data.f, entry.count);
    } else {
      ALOGE("%s: Unsupported override key %d", __FUNCTION__, tag);
    }
  } else {
    auto missing_tag = get_camera_metadata_tag_name(tag);
    ALOGE("%s: %s needs to be specified for overriding zoom", __func__,
          missing_tag);
  }
}

Return<void> EmulatedRequestProcessor::SensorHandler::onEvent(const Event& e) {
  auto processor = processor_.lock();
  if (processor.get() == nullptr) {
    return Void();
  }

  if (e.sensorType == SensorType::ACCELEROMETER) {
    // Heuristic approach for deducing the screen
    // rotation depending on the reported
    // accelerometer readings. We switch
    // the screen rotation when one of the
    // x/y axis gets close enough to the earth
    // acceleration.
    const uint32_t earth_accel = 9;  // Switch threshold [m/s^2]
    uint32_t x_accel = e.u.vec3.x;
    uint32_t y_accel = e.u.vec3.y;
    uint32_t z_accel = abs(e.u.vec3.z);
    if (z_accel == earth_accel) {
      return Void();
    }

    if (x_accel == earth_accel) {
      processor->screen_rotation_ = 270;
    } else if (x_accel == -earth_accel) {
      processor->screen_rotation_ = 90;
    } else if (y_accel == -earth_accel) {
      processor->screen_rotation_ = 180;
    } else {
      processor->screen_rotation_ = 0;
    }
  } else {
    ALOGE("%s: unexpected event received type: %d", __func__, e.sensorType);
  }
  return Void();
}

void EmulatedRequestProcessor::InitializeSensorQueue(
    std::weak_ptr<EmulatedRequestProcessor> processor) {
  if (sensor_event_queue_.get() != nullptr) {
    return;
  }

  sp<ISensorManager> manager = ISensorManager::getService();
  if (manager == nullptr) {
    ALOGE("%s: Cannot get ISensorManager", __func__);
  } else {
    bool sensor_found = false;
    manager->getSensorList([&](const auto& list, auto result) {
      if (result != Result::OK) {
        ALOGE("%s: Failed to retrieve sensor list!", __func__);
      } else {
        for (const SensorInfo& it : list) {
          if (it.type == SensorType::ACCELEROMETER) {
            sensor_found = true;
            sensor_handle_ = it.sensorHandle;
          }
        }
      }
    });
    if (sensor_found) {
      manager->createEventQueue(
          new SensorHandler(processor), [&](const auto& q, auto result) {
            if (result != Result::OK) {
              ALOGE("%s: Cannot create event queue", __func__);
              return;
            }
            sensor_event_queue_ = q;
          });

      if (sensor_event_queue_.get() != nullptr) {
        auto res = sensor_event_queue_->enableSensor(
            sensor_handle_,
            ns2us(EmulatedSensor::kSupportedFrameDurationRange[0]),
            0 /*maxBatchReportLatencyUs*/);
        if (res.isOk()) {
        } else {
          ALOGE("%s: Failed to enable sensor", __func__);
        }
      } else {
        ALOGE("%s: Failed to create event queue", __func__);
      }
    }
  }
}

}  // namespace android
