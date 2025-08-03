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
#define LOG_TAG "EmulatedCameraDevSession"
#define ATRACE_TAG ATRACE_TAG_CAMERA

#include "EmulatedCameraDeviceSessionHWLImpl.h"

#include <android/hardware/graphics/common/1.2/types.h>
#include <hardware/gralloc.h>
#include <inttypes.h>
#include <log/log.h>
#include <utils/Trace.h>

#include <memory>

#include "EmulatedSensor.h"
#include "utils.h"
#include "utils/HWLUtils.h"

namespace android {

using google_camera_hal::utils::GetSensorActiveArraySize;
using google_camera_hal::utils::HasCapability;

using android::hardware::graphics::common::V1_2::Dataspace;
std::unique_ptr<EmulatedCameraZoomRatioMapperHwlImpl>
EmulatedCameraZoomRatioMapperHwlImpl::Create(
    const std::unordered_map<uint32_t, std::pair<Dimension, Dimension>>& dims) {
  auto zoom_ratio_mapper_hwl_impl =
      std::make_unique<EmulatedCameraZoomRatioMapperHwlImpl>(dims);
  return zoom_ratio_mapper_hwl_impl;
}

EmulatedCameraZoomRatioMapperHwlImpl::EmulatedCameraZoomRatioMapperHwlImpl(
    const std::unordered_map<uint32_t, std::pair<Dimension, Dimension>>& dims)
    : camera_ids_to_dimensions_(dims) {
}

bool EmulatedCameraZoomRatioMapperHwlImpl::GetActiveArrayDimensionToBeUsed(
    uint32_t camera_id, const HalCameraMetadata* settings,
    Dimension* active_array_dimension) const {
  auto dim_it = camera_ids_to_dimensions_.find(camera_id);
  if (settings == nullptr || active_array_dimension == nullptr ||
      dim_it == camera_ids_to_dimensions_.end()) {
    // default request / camera id not found
    return false;
  }
  camera_metadata_ro_entry entry = {};
  status_t res = settings->Get(ANDROID_SENSOR_PIXEL_MODE, &entry);
  if (res != OK) {
    // return default res dimension
    *active_array_dimension = dim_it->second.second;
    return true;
  }
  if (entry.data.u8[0] == ANDROID_SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION) {
    // return max res mode dimension
    *active_array_dimension = dim_it->second.first;
  } else {
    *active_array_dimension = dim_it->second.second;
  }
  return true;
}

std::unique_ptr<EmulatedCameraDeviceSessionHwlImpl>
EmulatedCameraDeviceSessionHwlImpl::Create(
    uint32_t camera_id, std::unique_ptr<EmulatedCameraDeviceInfo> device_info,
    PhysicalDeviceMapPtr physical_devices,
    std::shared_ptr<EmulatedTorchState> torch_state) {
  ATRACE_CALL();
  if (device_info.get() == nullptr) {
    return nullptr;
  }

  auto session = std::unique_ptr<EmulatedCameraDeviceSessionHwlImpl>(
      new EmulatedCameraDeviceSessionHwlImpl(std::move(physical_devices),
                                             torch_state));
  if (session == nullptr) {
    ALOGE("%s: Creating EmulatedCameraDeviceSessionHwlImpl failed",
          __FUNCTION__);
    return nullptr;
  }

  status_t res = session->Initialize(camera_id, std::move(device_info));
  if (res != OK) {
    ALOGE("%s: Initializing EmulatedCameraDeviceSessionHwlImpl failed: %s(%d)",
          __FUNCTION__, strerror(-res), res);
    return nullptr;
  }

  return session;
}

static std::pair<Dimension, Dimension> GetArrayDimensions(
    uint32_t camera_id, const HalCameraMetadata* metadata) {
  google_camera_hal::Rect active_array_size;
  Dimension active_array_size_dimension;
  Dimension active_array_size_dimension_maximum_resolution;
  status_t ret = GetSensorActiveArraySize(metadata, &active_array_size);
  if (ret != OK) {
    ALOGE("%s: Failed to get sensor active array size for camera id %d",
          __FUNCTION__, (int)camera_id);
    return std::pair<Dimension, Dimension>();
  }
  active_array_size_dimension = {
      active_array_size.right - active_array_size.left + 1,
      active_array_size.bottom - active_array_size.top + 1};

  active_array_size_dimension_maximum_resolution = active_array_size_dimension;
  if (HasCapability(
          metadata,
          ANDROID_REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR)) {
    status_t ret = GetSensorActiveArraySize(metadata, &active_array_size,
                                            /*maximum_resolution=*/true);
    if (ret != OK) {
      ALOGE(
          "%s: Failed to get max resolution sensor active array size for "
          "camera id %d",
          __FUNCTION__, (int)camera_id);
      return std::pair<Dimension, Dimension>();
    }
    active_array_size_dimension_maximum_resolution = {
        active_array_size.right - active_array_size.left + 1,
        active_array_size.bottom - active_array_size.top + 1};
  }
  return std::make_pair(active_array_size_dimension_maximum_resolution,
                        active_array_size_dimension);
}

status_t EmulatedCameraDeviceSessionHwlImpl::Initialize(
    uint32_t camera_id, std::unique_ptr<EmulatedCameraDeviceInfo> device_info) {
  camera_id_ = camera_id;
  device_info_ = std::move(device_info);
  stream_configuration_map_ = std::make_unique<StreamConfigurationMap>(
      *(device_info_->static_metadata_));
  stream_configuration_map_max_resolution_ =
      std::make_unique<StreamConfigurationMap>(
          *(device_info_->static_metadata_), true);
  camera_metadata_ro_entry_t entry;
  auto ret = device_info_->static_metadata_->Get(
      ANDROID_REQUEST_PIPELINE_MAX_DEPTH, &entry);
  if (ret != OK) {
    ALOGE("%s: Unable to extract ANDROID_REQUEST_PIPELINE_MAX_DEPTH, %s (%d)",
          __FUNCTION__, strerror(-ret), ret);
    return ret;
  }

  max_pipeline_depth_ = entry.data.u8[0];

  std::unordered_map<uint32_t, std::pair<Dimension, Dimension>>
      camera_ids_to_dimensions;
  camera_ids_to_dimensions[camera_id] =
      GetArrayDimensions(camera_id, device_info_->static_metadata_.get());

  ret = GetSensorCharacteristics(device_info_->static_metadata_.get(),
                                 &sensor_chars_);
  if (ret != OK) {
    ALOGE("%s: Unable to extract sensor characteristics %s (%d)", __FUNCTION__,
          strerror(-ret), ret);
    return ret;
  }

  ret = SupportsSessionHalBufManager(device_info_->static_metadata_.get(),
                                     &supports_session_hal_buf_manager_);
  if (ret != OK) {
    ALOGE("%s: Unable to get sensor hal buffer manager support %s (%d)",
          __FUNCTION__, strerror(-ret), ret);
    return ret;
  }

  logical_chars_.emplace(camera_id_, sensor_chars_);
  for (const auto& it : *physical_device_map_) {
    SensorCharacteristics physical_chars;
    auto stat = GetSensorCharacteristics(it.second.second.get(), &physical_chars);
    if (stat == OK) {
      logical_chars_.emplace(it.first, physical_chars);
    } else {
      ALOGE("%s: Unable to extract physical device: %u characteristics %s (%d)",
            __FUNCTION__, it.first, strerror(-ret), ret);
      return ret;
    }
    camera_ids_to_dimensions[it.first] =
        GetArrayDimensions(it.first, it.second.second.get());
    physical_stream_configuration_map_.emplace(
        it.first,
        std::make_unique<StreamConfigurationMap>(*it.second.second.get()));
    physical_stream_configuration_map_max_resolution_.emplace(
        it.first, std::make_unique<StreamConfigurationMap>(
                      *it.second.second.get(), true));
  }

  zoom_ratio_mapper_hwl_impl_ = std::move(
      EmulatedCameraZoomRatioMapperHwlImpl::Create(camera_ids_to_dimensions));
  return InitializeRequestProcessor();
}

status_t EmulatedCameraDeviceSessionHwlImpl::InitializeRequestProcessor() {
  sp<EmulatedSensor> emulated_sensor = new EmulatedSensor();
  auto logical_chars = std::make_unique<LogicalCharacteristics>(logical_chars_);
  auto ret = emulated_sensor->StartUp(camera_id_, std::move(logical_chars));
  if (ret != OK) {
    ALOGE("%s: Failed on sensor start up %s (%d)", __FUNCTION__, strerror(-ret),
          ret);
    return ret;
  }

  request_processor_ = std::make_shared<EmulatedRequestProcessor>(
      camera_id_, emulated_sensor, session_callback_);

  request_processor_->InitializeSensorQueue(request_processor_);

  return request_processor_->Initialize(
      EmulatedCameraDeviceInfo::Clone(*device_info_),
      ClonePhysicalDeviceMap(physical_device_map_));
}

EmulatedCameraDeviceSessionHwlImpl::~EmulatedCameraDeviceSessionHwlImpl() {
  if (torch_state_.get() != nullptr) {
    torch_state_->ReleaseFlashHw();
  }
}

status_t EmulatedCameraDeviceSessionHwlImpl::ConstructDefaultRequestSettings(
    RequestTemplate type, std::unique_ptr<HalCameraMetadata>* default_settings) {
  ATRACE_CALL();
  std::lock_guard<std::mutex> lock(api_mutex_);

  return request_processor_->GetDefaultRequest(type, default_settings);
}

status_t EmulatedCameraDeviceSessionHwlImpl::ConfigurePipeline(
    uint32_t physical_camera_id, HwlPipelineCallback hwl_pipeline_callback,
    const StreamConfiguration& request_config,
    const StreamConfiguration& /*overall_config*/, uint32_t* pipeline_id) {
  ATRACE_CALL();
  std::lock_guard<std::mutex> lock(api_mutex_);
  if (pipeline_id == nullptr) {
    ALOGE("%s pipeline_id is nullptr", __FUNCTION__);
    return BAD_VALUE;
  }

  if (pipelines_built_) {
    ALOGE("%s Cannot configure pipelines after calling BuildPipelines()",
          __FUNCTION__);
    return ALREADY_EXISTS;
  }

  if (!EmulatedSensor::IsStreamCombinationSupported(
          physical_camera_id, request_config, *stream_configuration_map_,
          *stream_configuration_map_max_resolution_,
          physical_stream_configuration_map_,
          physical_stream_configuration_map_max_resolution_, logical_chars_)) {
    ALOGE("%s: Stream combination not supported!", __FUNCTION__);
    return BAD_VALUE;
  }

  if ((physical_camera_id != camera_id_) &&
      (physical_device_map_.get() != nullptr)) {
    if (physical_device_map_->find(physical_camera_id) ==
        physical_device_map_->end()) {
      ALOGE("%s: Camera: %d doesn't include physical device with id: %u",
            __FUNCTION__, camera_id_, physical_camera_id);
      return BAD_VALUE;
    }
  }

  *pipeline_id = pipelines_.size();
  EmulatedPipeline emulated_pipeline{.cb = hwl_pipeline_callback,
                                     .physical_camera_id = physical_camera_id,
                                     .pipeline_id = *pipeline_id,};

  emulated_pipeline.streams.reserve(request_config.streams.size());
  for (const auto& stream : request_config.streams) {
    bool is_input = stream.stream_type == google_camera_hal::StreamType::kInput;
    emulated_pipeline.streams.emplace(
        stream.id,
        EmulatedStream(
            {{.id = stream.id,
              .override_format =
                  is_input ? stream.format
                           : EmulatedSensor::OverrideFormat(
                                 stream.format, stream.dynamic_profile),
              .producer_usage = is_input ? 0
                                         : GRALLOC_USAGE_SW_WRITE_OFTEN |
                                               GRALLOC_USAGE_HW_CAMERA_WRITE |
                                               GRALLOC_USAGE_HW_CAMERA_READ,
              .consumer_usage = 0,
              .max_buffers = max_pipeline_depth_,
              .override_data_space =
                  (stream.dynamic_profile ==
                   ANDROID_REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_HLG10) &&
                          (stream.format ==
                           HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED)
                      ? static_cast<android_dataspace_t>(
                            Dataspace::BT2020_ITU_HLG)
                      : stream.data_space,
              .is_physical_camera_stream = stream.is_physical_camera_stream,
              .physical_camera_id = stream.physical_camera_id},
             .width = stream.width,
             .height = stream.height,
             .buffer_size = stream.buffer_size,
             .is_input = is_input,
             .group_id = stream.group_id,
             .use_case = stream.use_case,
             .color_space = stream.color_space}));

    if (stream.group_id != -1 && stream.is_physical_camera_stream) {
      // TODO: For quad bayer camera, the logical camera id should be used if
      // this is not a physical camera.
      dynamic_stream_id_map_[stream.physical_camera_id][stream.group_id] =
          stream.id;
    }
    if (!stream.is_physical_camera_stream &&
        (stream.format == HAL_PIXEL_FORMAT_RAW16)) {
      has_raw_stream_ = true;
    }
  }

  pipelines_.push_back(emulated_pipeline);

  return OK;
}

status_t EmulatedCameraDeviceSessionHwlImpl::GetConfiguredHalStream(
    uint32_t pipeline_id, std::vector<HalStream>* hal_streams) const {
  ATRACE_CALL();
  std::lock_guard<std::mutex> lock(api_mutex_);
  if (hal_streams == nullptr) {
    ALOGE("%s hal_streams is nullptr", __FUNCTION__);
    return BAD_VALUE;
  }

  if (!pipelines_built_) {
    ALOGE("%s No pipeline was built.", __FUNCTION__);
    return NO_INIT;
  }

  if (pipeline_id >= pipelines_.size()) {
    ALOGE("%s: Unknown pipeline ID: %u", __FUNCTION__, pipeline_id);
    return NAME_NOT_FOUND;
  }

  const auto& streams = pipelines_[pipeline_id].streams;
  hal_streams->reserve(streams.size());
  for (const auto& it : streams) {
    hal_streams->push_back(it.second);
  }

  return OK;
}

status_t EmulatedCameraDeviceSessionHwlImpl::BuildPipelines() {
  ATRACE_CALL();
  std::lock_guard<std::mutex> lock(api_mutex_);
  if (pipelines_built_) {
    ALOGE("%s Pipelines have already been built!", __FUNCTION__);
    return ALREADY_EXISTS;
  } else if (pipelines_.size() == 0) {
    ALOGE("%s No pipelines have been configured yet!", __FUNCTION__);
    return NO_INIT;
  }

  status_t ret = OK;
  if (request_processor_ == nullptr) {
    ret = InitializeRequestProcessor();
  }

  if (ret == OK) {
    pipelines_built_ = true;
  }

  return OK;
}

std::set<int32_t> EmulatedCameraDeviceSessionHwlImpl::GetHalBufferManagedStreams(
    const StreamConfiguration& config) {
  std::set<int32_t> ret;
  for (const auto& stream : config.streams) {
    if (stream.stream_type == google_camera_hal::StreamType::kInput) {
      continue;
    }
    // Just a heuristic - odd numbered stream ids are hal buffer managed.
    if ((stream.group_id != -1) || (stream.id % 2)) {
      ret.insert(stream.id);
    }
  }
  return ret;
}

void EmulatedCameraDeviceSessionHwlImpl::DestroyPipelines() {
  ATRACE_CALL();
  std::lock_guard<std::mutex> lock(api_mutex_);
  if (!pipelines_built_) {
    // Not an error - nothing to destroy
    ALOGV("%s nothing to destroy", __FUNCTION__);
    return;
  }

  pipelines_built_ = false;
  has_raw_stream_ = false;
  pipelines_.clear();
  request_processor_ = nullptr;
}

status_t EmulatedCameraDeviceSessionHwlImpl::CheckOutputFormatsForInput(
    const HwlPipelineRequest& request,
    const std::unordered_map<uint32_t, EmulatedStream>& streams,
    const std::unique_ptr<StreamConfigurationMap>& stream_configuration_map,
    android_pixel_format_t input_format) {
  auto output_formats =
      stream_configuration_map->GetValidOutputFormatsForInput(input_format);
  for (const auto& output_buffer : request.output_buffers) {
    auto output_stream = streams.at(output_buffer.stream_id);
    if (output_formats.find(output_stream.override_format) ==
        output_formats.end()) {
      ALOGE(
          "%s: Reprocess request with input format: 0x%x to output "
          "format: 0x%x"
          " not supported!",
          __FUNCTION__, input_format, output_stream.override_format);
      return BAD_VALUE;
    }
  }
  return OK;
}

status_t EmulatedCameraDeviceSessionHwlImpl::SubmitRequests(
    uint32_t frame_number, std::vector<HwlPipelineRequest>& requests) {
  ATRACE_CALL();
  std::lock_guard<std::mutex> lock(api_mutex_);

  // Check whether reprocess request has valid/supported outputs.
  for (const auto& request : requests) {
    if (!request.input_buffers.empty()) {
      for (const auto& input_buffer : request.input_buffers) {
        const auto& streams = pipelines_[request.pipeline_id].streams;
        auto input_stream = streams.at(input_buffer.stream_id);
        if ((CheckOutputFormatsForInput(request, streams,
                                        stream_configuration_map_,
                                        input_stream.override_format) != OK) &&
            (CheckOutputFormatsForInput(
                 request, streams, stream_configuration_map_max_resolution_,
                 input_stream.override_format) != OK)) {
          return BAD_VALUE;
        }
      }
    }
  }

  if (error_state_) {
    ALOGE("%s session is in error state and cannot process further requests",
          __FUNCTION__);
    return INVALID_OPERATION;
  }

  return request_processor_->ProcessPipelineRequests(
      frame_number, requests, pipelines_, dynamic_stream_id_map_,
      has_raw_stream_);
}

status_t EmulatedCameraDeviceSessionHwlImpl::Flush() {
  ATRACE_CALL();
  std::lock_guard<std::mutex> lock(api_mutex_);
  return request_processor_->Flush();
}

uint32_t EmulatedCameraDeviceSessionHwlImpl::GetCameraId() const {
  return camera_id_;
}

std::vector<uint32_t> EmulatedCameraDeviceSessionHwlImpl::GetPhysicalCameraIds()
    const {
  if ((physical_device_map_.get() == nullptr) ||
      (physical_device_map_->empty())) {
    return std::vector<uint32_t>{};
  }

  std::vector<uint32_t> ret;
  ret.reserve(physical_device_map_->size());
  for (const auto& it : *physical_device_map_) {
    ret.push_back(it.first);
  }

  return ret;
}

status_t EmulatedCameraDeviceSessionHwlImpl::GetCameraCharacteristics(
    std::unique_ptr<HalCameraMetadata>* characteristics) const {
  ATRACE_CALL();
  if (characteristics == nullptr) {
    return BAD_VALUE;
  }

  (*characteristics) =
      HalCameraMetadata::Clone(device_info_->static_metadata_.get());

  if (*characteristics == nullptr) {
    ALOGE("%s metadata clone failed", __FUNCTION__);
    return NO_MEMORY;
  }

  return OK;
}

status_t EmulatedCameraDeviceSessionHwlImpl::GetPhysicalCameraCharacteristics(
    uint32_t physical_camera_id,
    std::unique_ptr<HalCameraMetadata>* characteristics) const {
  ATRACE_CALL();
  if (characteristics == nullptr) {
    return BAD_VALUE;
  }

  if (physical_device_map_.get() == nullptr) {
    ALOGE("%s: Camera: %d doesn't have physical device support!", __FUNCTION__,
          camera_id_);
    return BAD_VALUE;
  }

  if (physical_device_map_->find(physical_camera_id) ==
      physical_device_map_->end()) {
    ALOGE("%s: Camera: %d doesn't include physical device with id: %u",
          __FUNCTION__, camera_id_, physical_camera_id);
    return BAD_VALUE;
  }

  (*characteristics) = HalCameraMetadata::Clone(
      physical_device_map_->at(physical_camera_id).second.get());

  return OK;
}

void EmulatedCameraDeviceSessionHwlImpl::SetSessionCallback(
    const HwlSessionCallback& hwl_session_callback) {
  ATRACE_CALL();
  std::lock_guard<std::mutex> lock(api_mutex_);
  session_callback_ = hwl_session_callback;
  if (request_processor_ != nullptr) {
    request_processor_->SetSessionCallback(hwl_session_callback);
  }
}

}  // namespace android
