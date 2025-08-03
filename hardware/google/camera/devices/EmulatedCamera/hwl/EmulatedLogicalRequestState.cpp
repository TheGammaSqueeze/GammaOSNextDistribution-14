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

#define LOG_TAG "EmulatedLogicalState"
#define ATRACE_TAG ATRACE_TAG_CAMERA
//#define LOG_NDEBUG 0

#include "EmulatedLogicalRequestState.h"

#include <log/log.h>

#include "vendor_tag_defs.h"

namespace android {

EmulatedLogicalRequestState::EmulatedLogicalRequestState(uint32_t camera_id)
    : logical_camera_id_(camera_id),
      logical_request_state_(std::make_unique<EmulatedRequestState>(camera_id)) {
}

EmulatedLogicalRequestState::~EmulatedLogicalRequestState() {
}

status_t EmulatedLogicalRequestState::Initialize(
    std::unique_ptr<EmulatedCameraDeviceInfo> device_info,
    PhysicalDeviceMapPtr physical_devices) {
  if ((physical_devices.get() != nullptr) && (!physical_devices->empty())) {
    zoom_ratio_physical_camera_info_ = GetZoomRatioPhysicalCameraInfo(
        device_info->static_metadata_.get(), physical_devices.get());

    physical_device_map_ = std::move(physical_devices);

    static const float ZOOM_RATIO_THRESHOLD = 0.001f;
    for (const auto& one_zoom_range : zoom_ratio_physical_camera_info_) {
      ALOGV("%s: cameraId %d, focalLength %f, zoomRatioRange [%f, %f]",
            __FUNCTION__, one_zoom_range.physical_camera_id,
            one_zoom_range.focal_length, one_zoom_range.min_zoom_ratio,
            one_zoom_range.max_zoom_ratio);
      if (std::abs(one_zoom_range.min_zoom_ratio - 1.0f) < ZOOM_RATIO_THRESHOLD) {
        current_physical_camera_ = one_zoom_range.physical_camera_id;
      }
    }

    if (zoom_ratio_physical_camera_info_.size() > 1) {
      is_logical_device_ = true;
      for (const auto& it : *physical_device_map_) {
        std::unique_ptr<EmulatedRequestState> physical_request_state =
            std::make_unique<EmulatedRequestState>(it.first);
        auto ret =
            physical_request_state->Initialize(EmulatedCameraDeviceInfo::Create(
                HalCameraMetadata::Clone(it.second.second.get())));
        if (ret != OK) {
          ALOGE("%s: Physical device: %u request state initialization failed!",
                __FUNCTION__, it.first);
          return ret;
        }
        physical_request_states_.emplace(it.first,
                                         std::move(physical_request_state));
      }
    }
  }

  return logical_request_state_->Initialize(std::move(device_info));
}

status_t EmulatedLogicalRequestState::GetDefaultRequest(
    RequestTemplate type,
    std::unique_ptr<HalCameraMetadata>* default_settings /*out*/) {
  return logical_request_state_->GetDefaultRequest(type, default_settings);
};

void EmulatedLogicalRequestState::UpdateActivePhysicalId(
    HalCameraMetadata* result_metadata, uint32_t device_id) {
  if (result_metadata == nullptr) {
    return;
  }

  auto device_id_str = std::to_string(device_id);
  std::vector<uint8_t> result;
  result.reserve(device_id_str.size() + 1);
  result.insert(result.end(), device_id_str.begin(), device_id_str.end());
  result.push_back('\0');

  result_metadata->Set(ANDROID_LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID,
                       result.data(), result.size());
}

std::unique_ptr<HwlPipelineResult>
EmulatedLogicalRequestState::InitializeLogicalResult(uint32_t pipeline_id,
                                                     uint32_t frame_number,
                                                     bool is_partial_result) {
  auto ret =
      is_partial_result
          ? logical_request_state_->InitializePartialResult(pipeline_id,
                                                            frame_number)
          : logical_request_state_->InitializeResult(pipeline_id, frame_number);

  if (is_logical_device_ && !is_partial_result) {
    if ((physical_camera_output_ids_.get() != nullptr) &&
        (!physical_camera_output_ids_->empty())) {
      ret->physical_camera_results.reserve(physical_camera_output_ids_->size());
      for (const auto& it : *physical_camera_output_ids_) {
        ret->physical_camera_results[it] =
            std::move(physical_request_states_[it]
                          ->InitializeResult(pipeline_id, frame_number)
                          ->result_metadata);

        UpdateActivePhysicalId(ret->physical_camera_results[it].get(), it);
      }
    }

    UpdateActivePhysicalId(ret->result_metadata.get(), current_physical_camera_);
  }

  return ret;
}

status_t EmulatedLogicalRequestState::InitializeLogicalSettings(
    std::unique_ptr<HalCameraMetadata> request_settings,
    std::unique_ptr<std::set<uint32_t>> physical_camera_output_ids,
    uint32_t override_frame_number,
    EmulatedSensor::LogicalCameraSettings* logical_settings /*out*/) {
  if (logical_settings == nullptr) {
    return BAD_VALUE;
  }

  // All logical and physical devices can potentially receive individual client
  // requests (Currently this is not the case due to HWL API limitations).
  // The emulated sensor can adapt its characteristics and apply most of them
  // independently however the frame duration needs to be the same across all
  // settings.
  // Track the maximum frame duration and override this value at the end for all
  // logical settings.
  nsecs_t max_frame_duration = 0;
  if (is_logical_device_) {
    std::swap(physical_camera_output_ids_, physical_camera_output_ids);

    for (const auto& physical_request_state : physical_request_states_) {
      // All physical devices will receive requests and will keep
      // updating their respective request state.
      // However only physical devices referenced by client need to propagate
      // and apply their settings.
      EmulatedSensor::SensorSettings physical_sensor_settings;
      auto ret = physical_request_state.second->InitializeSensorSettings(
          HalCameraMetadata::Clone(request_settings.get()),
          override_frame_number, &physical_sensor_settings);
      if (ret != OK) {
        ALOGE(
            "%s: Initialization of physical sensor settings for device id: %u  "
            "failed!",
            __FUNCTION__, physical_request_state.first);
        return ret;
      }

      if (physical_camera_output_ids_->find(physical_request_state.first) !=
          physical_camera_output_ids_->end()) {
        logical_settings->emplace(physical_request_state.first,
                                  physical_sensor_settings);
        if (max_frame_duration < physical_sensor_settings.exposure_time) {
          max_frame_duration = physical_sensor_settings.exposure_time;
        }
      }
    }
  }

  EmulatedSensor::SensorSettings sensor_settings;
  auto ret = logical_request_state_->InitializeSensorSettings(
      std::move(request_settings), override_frame_number, &sensor_settings);
  logical_settings->emplace(logical_camera_id_, sensor_settings);
  if (max_frame_duration < sensor_settings.exposure_time) {
    max_frame_duration = sensor_settings.exposure_time;
  }

  for (auto it : *logical_settings) {
    it.second.frame_duration = max_frame_duration;
  }

  return ret;
}

std::unique_ptr<HalCameraMetadata>
EmulatedLogicalRequestState::AdaptLogicalCharacteristics(
    std::unique_ptr<HalCameraMetadata> logical_chars,
    PhysicalDeviceMapPtr physical_devices) {
  if ((logical_chars.get() == nullptr) || (physical_devices.get() == nullptr)) {
    return nullptr;
  }

  // Update 'android.logicalMultiCamera.physicalIds' according to the newly
  // assigned physical ids.
  // Additionally if possible try to emulate a logical camera device backed by
  // physical devices with different focal lengths. Usually real logical
  // cameras like that will have device specific logic to switch between
  // physical sensors. Unfortunately we cannot infer this behavior using only
  // static camera characteristics. Use a simplistic approach of inferring
  // physical camera based on zoom ratio.
  std::vector<ZoomRatioPhysicalCameraInfo> zoom_ratio_physical_camera_info =
      GetZoomRatioPhysicalCameraInfo(logical_chars.get(),
                                     physical_devices.get());

  std::vector<uint8_t> physical_ids;
  for (const auto& physical_device : *physical_devices) {
    auto physical_id = std::to_string(physical_device.first);
    physical_ids.insert(physical_ids.end(), physical_id.begin(),
                        physical_id.end());
    physical_ids.push_back('\0');
  }

  if (zoom_ratio_physical_camera_info.size() > 1) {
    float zoom_range[2];
    zoom_range[0] = zoom_ratio_physical_camera_info[0].min_zoom_ratio;
    zoom_range[1] =
        zoom_ratio_physical_camera_info[zoom_ratio_physical_camera_info.size() - 1]
            .max_zoom_ratio;
    logical_chars->Set(ANDROID_CONTROL_ZOOM_RATIO_RANGE, zoom_range, 2);

    logical_chars->Set(ANDROID_SCALER_AVAILABLE_MAX_DIGITAL_ZOOM,
                       &zoom_range[1], 1);

    logical_chars->Set(ANDROID_LOGICAL_MULTI_CAMERA_PHYSICAL_IDS,
                       physical_ids.data(), physical_ids.size());

    // Possibly needs to be removed at some later point:
    int32_t default_physical_id = physical_devices->begin()->first;
    logical_chars->Set(google_camera_hal::kLogicalCamDefaultPhysicalId,
                       &default_physical_id, 1);

    camera_metadata_ro_entry entry;
    logical_chars->Get(ANDROID_REQUEST_AVAILABLE_RESULT_KEYS, &entry);
    std::set<int32_t> keys(entry.data.i32, entry.data.i32 + entry.count);
    keys.emplace(ANDROID_LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID);
    std::vector<int32_t> keys_buffer(keys.begin(), keys.end());
    logical_chars->Set(ANDROID_REQUEST_AVAILABLE_RESULT_KEYS,
                       keys_buffer.data(), keys_buffer.size());

    keys.clear();
    keys_buffer.clear();
    logical_chars->Get(ANDROID_REQUEST_AVAILABLE_CHARACTERISTICS_KEYS, &entry);
    keys.insert(entry.data.i32, entry.data.i32 + entry.count);
    // Due to API limitations we currently don't support individual physical requests
    logical_chars->Erase(ANDROID_REQUEST_AVAILABLE_PHYSICAL_CAMERA_REQUEST_KEYS);
    keys.erase(ANDROID_REQUEST_AVAILABLE_PHYSICAL_CAMERA_REQUEST_KEYS);
    keys.emplace(ANDROID_LOGICAL_MULTI_CAMERA_PHYSICAL_IDS);
    keys_buffer.insert(keys_buffer.end(), keys.begin(), keys.end());
    logical_chars->Set(ANDROID_REQUEST_AVAILABLE_CHARACTERISTICS_KEYS,
                       keys_buffer.data(), keys_buffer.size());
  } else {
    ALOGW(
        "%s: The logical camera doesn't support combined zoom ratio ranges. "
        "Emulation "
        "could be"
        " very limited in this case!",
        __FUNCTION__);
  }

  return logical_chars;
}

status_t EmulatedLogicalRequestState::UpdateRequestForDynamicStreams(
    HwlPipelineRequest* request, const std::vector<EmulatedPipeline>& pipelines,
    const DynamicStreamIdMapType& dynamic_stream_id_map,
    bool use_default_physical_camera) {
  if (request == nullptr) {
    ALOGE("%s: Request must not be null!", __FUNCTION__);
    return BAD_VALUE;
  }

  uint32_t pipeline_id = request->pipeline_id;
  if (pipeline_id >= pipelines.size()) {
    ALOGE("%s: Invalid pipeline id %d", __FUNCTION__, pipeline_id);
    return BAD_VALUE;
  }

  // Only logical camera support dynamic size streams.
  if (!is_logical_device_) return OK;

  if (request->settings != nullptr) {
    camera_metadata_ro_entry entry;
    auto stat = request->settings->Get(ANDROID_CONTROL_ZOOM_RATIO, &entry);
    if (stat != OK || entry.count != 1) {
      ALOGW("%s: Zoom ratio absent from request, re-using older value!",
            __FUNCTION__);
      return BAD_VALUE;
    }
    if (!use_default_physical_camera) {
      float zoom_ratio = entry.data.f[0];
      for (const auto& one_range : zoom_ratio_physical_camera_info_) {
        if (zoom_ratio >= one_range.min_zoom_ratio &&
            zoom_ratio <= one_range.max_zoom_ratio) {
          current_physical_camera_ = one_range.physical_camera_id;
          break;
        }
      }
    }
  }

  const auto& current_pipeline = pipelines[pipeline_id];
  for (auto& output_buffer : request->output_buffers) {
    auto& current_stream = current_pipeline.streams.at(output_buffer.stream_id);
    if (current_stream.group_id == -1) continue;

    const auto& stream_ids_for_camera =
        dynamic_stream_id_map.find(current_physical_camera_);
    if (stream_ids_for_camera == dynamic_stream_id_map.end()) {
      ALOGW(
          "%s: Failed to find physical camera id %d in dynamic stream id map!",
          __FUNCTION__, current_physical_camera_);
      continue;
    }
    const auto& stream_id =
        stream_ids_for_camera->second.find(current_stream.group_id);
    if (stream_id == stream_ids_for_camera->second.end()) {
      ALOGW(
          "%s: Failed to find group id %d in dynamic stream id map for camera "
          "%d",
          __FUNCTION__, current_stream.group_id, current_physical_camera_);
      continue;
    }

    output_buffer.stream_id = stream_id->second;
  }
  return OK;
}

std::vector<ZoomRatioPhysicalCameraInfo>
EmulatedLogicalRequestState::GetZoomRatioPhysicalCameraInfo(
    const HalCameraMetadata* logical_chars,
    const PhysicalDeviceMap* physical_devices) {
  std::vector<ZoomRatioPhysicalCameraInfo> zoom_ratio_physical_camera_info;
  if ((logical_chars == nullptr) || (physical_devices == nullptr)) {
    return zoom_ratio_physical_camera_info;
  }

  // Get the logical camera's focal length and sensor size
  camera_metadata_ro_entry_t entry;
  auto ret =
      logical_chars->Get(ANDROID_LENS_INFO_AVAILABLE_FOCAL_LENGTHS, &entry);
  if ((ret != OK) || (entry.count == 0)) {
    return zoom_ratio_physical_camera_info;
  }
  float logical_focal_length = entry.data.f[0];
  ret = logical_chars->Get(ANDROID_SENSOR_INFO_PHYSICAL_SIZE, &entry);
  if ((ret != OK) || (entry.count == 0)) {
    return zoom_ratio_physical_camera_info;
  }
  float logical_sensor_width = entry.data.f[0];

  // Derive the zoom ratio boundary values for each physical camera id, based on
  // focal lengths and camera sensor physical size.
  for (const auto& physical_device : *physical_devices) {
    ret = physical_device.second.second->Get(
        ANDROID_LENS_INFO_AVAILABLE_FOCAL_LENGTHS, &entry);
    if ((ret == OK) && (entry.count > 0)) {
      float focal_length = entry.data.f[0];
      ret = physical_device.second.second->Get(
          ANDROID_SENSOR_INFO_PHYSICAL_SIZE, &entry);
      if ((ret == OK) && (entry.count > 0)) {
        float sensor_width = entry.data.f[0];
        ret = physical_device.second.second->Get(
            ANDROID_SCALER_AVAILABLE_MAX_DIGITAL_ZOOM, &entry);
        if ((ret == OK) && (entry.count > 0)) {
          float max_digital_zoom = entry.data.f[0];
          // focal length of ultrawide lens
          float min_zoom_ratio = focal_length * logical_sensor_width /
                                 (logical_focal_length * sensor_width);
          float max_zoom_ratio = max_digital_zoom * min_zoom_ratio;
          zoom_ratio_physical_camera_info.push_back(
              {focal_length, min_zoom_ratio, max_zoom_ratio,
               physical_device.first});
        }
      }
    }
  }

  // Sort the mapping by ascending focal length
  std::sort(zoom_ratio_physical_camera_info.begin(),
            zoom_ratio_physical_camera_info.end(),
            [](const ZoomRatioPhysicalCameraInfo& a,
               const ZoomRatioPhysicalCameraInfo& b) {
              return a.focal_length < b.focal_length;
            });

  // Modify the zoom ratio range for each focal length so that they don't
  // overlap
  for (size_t i = 0; i < zoom_ratio_physical_camera_info.size() - 1; i++) {
    auto& current = zoom_ratio_physical_camera_info[i];
    auto& next = zoom_ratio_physical_camera_info[i + 1];
    if (current.max_zoom_ratio > next.min_zoom_ratio) {
      current.max_zoom_ratio = next.min_zoom_ratio;
    }
  }

  return zoom_ratio_physical_camera_info;
}

}  // namespace android
