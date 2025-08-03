/*
 _* Copyright (C) 2013-2019 The Android Open Source Project
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

#include <unordered_set>
#define LOG_TAG "HWLUtils"
#include "HWLUtils.h"
#include <log/log.h>
#include "utils.h"

#include <map>

namespace android {

using google_camera_hal::ColorSpaceProfile;
using google_camera_hal::DynamicRangeProfile;
using google_camera_hal::utils::HasCapability;

static int64_t GetLastStreamUseCase(const HalCameraMetadata* metadata) {
  status_t ret = OK;
  camera_metadata_ro_entry_t entry;
  int64_t cropped_raw_use_case =
      ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_CROPPED_RAW;
  int64_t video_call_use_case =
      ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_VIDEO_CALL;
  ret = metadata->Get(ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES, &entry);
  if (ret != OK) {
    return ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_DEFAULT;
  }
  if (std::find(entry.data.i64, entry.data.i64 + entry.count,
                cropped_raw_use_case) != entry.data.i64 + entry.count) {
    return cropped_raw_use_case;
  }
  return video_call_use_case;
}
status_t SupportsSessionHalBufManager(const HalCameraMetadata* metadata,
                                      bool* result /*out*/) {
  if ((metadata == nullptr) || (result == nullptr)) {
    return BAD_VALUE;
  }

  status_t ret = OK;
  camera_metadata_ro_entry_t entry;
  *result = false;
  ret = metadata->Get(ANDROID_INFO_SUPPORTED_BUFFER_MANAGEMENT_VERSION, &entry);
  if (ret != OK) {
    return OK;
  }
  if ((ret == OK) && (entry.count != 1)) {
    ALOGE("%s: Invalid ANDROID_INFO_SUPPORTED_BUFFER_MANAGEMENT_VERSION!",
          __FUNCTION__);
    return BAD_VALUE;
  }
  *result =
      (entry.data.u8[0] ==
       ANDROID_INFO_SUPPORTED_BUFFER_MANAGEMENT_VERSION_SESSION_CONFIGURABLE);
  return OK;
}

status_t GetSensorCharacteristics(const HalCameraMetadata* metadata,
                                  SensorCharacteristics* sensor_chars /*out*/) {
  if ((metadata == nullptr) || (sensor_chars == nullptr)) {
    return BAD_VALUE;
  }

  status_t ret = OK;
  camera_metadata_ro_entry_t entry;
  ret = metadata->Get(ANDROID_SENSOR_INFO_PIXEL_ARRAY_SIZE, &entry);
  if ((ret != OK) || (entry.count != 2)) {
    ALOGE("%s: Invalid ANDROID_SENSOR_INFO_PIXEL_ARRAY_SIZE!", __FUNCTION__);
    return BAD_VALUE;
  }
  sensor_chars->width = entry.data.i32[0];
  sensor_chars->height = entry.data.i32[1];
  sensor_chars->full_res_width = sensor_chars->width;
  sensor_chars->full_res_height = sensor_chars->height;

  ret = metadata->Get(ANDROID_SENSOR_INFO_PIXEL_ARRAY_SIZE_MAXIMUM_RESOLUTION,
                      &entry);
  if ((ret == OK) && (entry.count == 2)) {
    sensor_chars->full_res_width = entry.data.i32[0];
    sensor_chars->full_res_height = entry.data.i32[1];
    sensor_chars->quad_bayer_sensor = true;
  }

  if (sensor_chars->quad_bayer_sensor) {
    ret = metadata->Get(ANDROID_SENSOR_INFO_ACTIVE_ARRAY_SIZE, &entry);
    if ((ret == OK) && (entry.count == 4)) {
      google_camera_hal::Rect rect;
      if (google_camera_hal::utils::GetSensorActiveArraySize(metadata, &rect) !=
          OK) {
        return BAD_VALUE;
      }
      sensor_chars->raw_crop_region_unzoomed[0] = rect.left;    // left
      sensor_chars->raw_crop_region_unzoomed[1] = rect.top;     // top
      sensor_chars->raw_crop_region_unzoomed[2] = rect.right;   // right
      sensor_chars->raw_crop_region_unzoomed[3] = rect.bottom;  // bottom

      // 2x zoom , raw crop width / height = 1/2 sensor width / height. top /
      // left edge = 1/4 sensor width. bottom / right edge = 1/2 + 1 /4 * sensor
      // width / height: Refer to case 1 in
      // https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#SCALER_CROP_REGION
      // for a visual representation.
      sensor_chars->raw_crop_region_zoomed[0] =
          rect.left + (rect.right - rect.left) / 4;  // left
      sensor_chars->raw_crop_region_zoomed[1] =
          rect.top + (rect.bottom - rect.top) / 4;  // top
      sensor_chars->raw_crop_region_zoomed[2] =
          sensor_chars->raw_crop_region_zoomed[0] +
          (rect.right - rect.left) / 2;  // right
      sensor_chars->raw_crop_region_zoomed[3] =
          sensor_chars->raw_crop_region_zoomed[1] +
          (rect.bottom - rect.top) / 2;  // bottom
    }
  }

  ret = metadata->Get(ANDROID_REQUEST_MAX_NUM_OUTPUT_STREAMS, &entry);
  if ((ret != OK) || (entry.count != 3)) {
    ALOGE("%s: Invalid ANDROID_REQUEST_MAX_NUM_OUTPUT_STREAMS!", __FUNCTION__);
    return BAD_VALUE;
  }

  sensor_chars->max_raw_streams = entry.data.i32[0];
  sensor_chars->max_processed_streams = entry.data.i32[1];
  sensor_chars->max_stalling_streams = entry.data.i32[2];

  if (HasCapability(
          metadata,
          ANDROID_REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT)) {
    ret = metadata->Get(ANDROID_REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP,
                        &entry);
    if ((ret != OK) || ((entry.count % 3) != 0)) {
      ALOGE("%s: Invalid ANDROID_REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP!",
            __FUNCTION__);
      return BAD_VALUE;
    }

    for (size_t i = 0; i < entry.count; i += 3) {
      sensor_chars->dynamic_range_profiles.emplace(
          static_cast<DynamicRangeProfile>(entry.data.i64[i]),
          std::unordered_set<DynamicRangeProfile>());
      const auto profile_end =
          ANDROID_REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_DOLBY_VISION_8B_HDR_OEM_PO
          << 1;
      uint64_t current_profile =
          ANDROID_REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_STANDARD;
      for (; current_profile != profile_end; current_profile <<= 1) {
        if (entry.data.i64[i + 1] & current_profile) {
          sensor_chars->dynamic_range_profiles
              .at(static_cast<DynamicRangeProfile>(entry.data.i64[i]))
              .emplace(static_cast<DynamicRangeProfile>(current_profile));
        }
      }
    }

    sensor_chars->is_10bit_dynamic_range_capable = true;
  }

  if (HasCapability(
          metadata,
          ANDROID_REQUEST_AVAILABLE_CAPABILITIES_COLOR_SPACE_PROFILES)) {
    ret = metadata->Get(ANDROID_REQUEST_AVAILABLE_COLOR_SPACE_PROFILES_MAP,
                        &entry);
    if ((ret != OK) || ((entry.count % 3) != 0)) {
      ALOGE("%s: Invalid ANDROID_REQUEST_AVAILABLE_COLOR_SPACE_PROFILES_MAP!",
            __FUNCTION__);
      return BAD_VALUE;
    }

    for (size_t i = 0; i < entry.count; i += 3) {
      ColorSpaceProfile color_space =
          static_cast<ColorSpaceProfile>(entry.data.i64[i]);
      int image_format = static_cast<int>(entry.data.i64[i + 1]);

      if (sensor_chars->color_space_profiles.find(color_space) ==
          sensor_chars->color_space_profiles.end()) {
        sensor_chars->color_space_profiles.emplace(
            color_space,
            std::unordered_map<int, std::unordered_set<DynamicRangeProfile>>());
      }

      std::unordered_map<int, std::unordered_set<DynamicRangeProfile>>&
          image_format_map = sensor_chars->color_space_profiles.at(color_space);

      if (image_format_map.find(image_format) == image_format_map.end()) {
        image_format_map.emplace(image_format,
                                 std::unordered_set<DynamicRangeProfile>());
      }

      const auto profile_end =
          ANDROID_REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_DOLBY_VISION_8B_HDR_OEM_PO
          << 1;
      uint64_t current_profile =
          ANDROID_REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_STANDARD;
      for (; current_profile != profile_end; current_profile <<= 1) {
        if (entry.data.i64[i + 2] & current_profile) {
          image_format_map.at(image_format)
              .emplace(static_cast<DynamicRangeProfile>(current_profile));
        }
      }
    }

    sensor_chars->support_color_space_profiles = true;
  }

  if (HasCapability(metadata,
                    ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)) {
    ret = metadata->Get(ANDROID_SENSOR_INFO_EXPOSURE_TIME_RANGE, &entry);
    if ((ret != OK) ||
        (entry.count != ARRAY_SIZE(sensor_chars->exposure_time_range))) {
      ALOGE("%s: Invalid ANDROID_SENSOR_INFO_EXPOSURE_TIME_RANGE!",
            __FUNCTION__);
      return BAD_VALUE;
    }
    memcpy(sensor_chars->exposure_time_range, entry.data.i64,
           sizeof(sensor_chars->exposure_time_range));

    ret = metadata->Get(ANDROID_SENSOR_INFO_MAX_FRAME_DURATION, &entry);
    if ((ret != OK) || (entry.count != 1)) {
      ALOGE("%s: Invalid ANDROID_SENSOR_INFO_MAX_FRAME_DURATION!", __FUNCTION__);
      return BAD_VALUE;
    }
    sensor_chars->frame_duration_range[1] = entry.data.i64[0];
    sensor_chars->frame_duration_range[0] =
        EmulatedSensor::kSupportedFrameDurationRange[0];

    ret = metadata->Get(ANDROID_SENSOR_INFO_SENSITIVITY_RANGE, &entry);
    if ((ret != OK) ||
        (entry.count != ARRAY_SIZE(sensor_chars->sensitivity_range))) {
      ALOGE("%s: Invalid ANDROID_SENSOR_INFO_SENSITIVITY_RANGE!", __FUNCTION__);
      return BAD_VALUE;
    }
    memcpy(sensor_chars->sensitivity_range, entry.data.i64,
           sizeof(sensor_chars->sensitivity_range));
  } else {
    memcpy(sensor_chars->exposure_time_range,
           EmulatedSensor::kSupportedExposureTimeRange,
           sizeof(sensor_chars->exposure_time_range));
    memcpy(sensor_chars->frame_duration_range,
           EmulatedSensor::kSupportedFrameDurationRange,
           sizeof(sensor_chars->frame_duration_range));
    memcpy(sensor_chars->sensitivity_range,
           EmulatedSensor::kSupportedSensitivityRange,
           sizeof(sensor_chars->sensitivity_range));
  }

  if (HasCapability(metadata, ANDROID_REQUEST_AVAILABLE_CAPABILITIES_RAW)) {
    ret = metadata->Get(ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT, &entry);
    if ((ret != OK) || (entry.count != 1)) {
      ALOGE("%s: Invalid ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT!",
            __FUNCTION__);
      return BAD_VALUE;
    }

    sensor_chars->color_arangement = static_cast<
        camera_metadata_enum_android_sensor_info_color_filter_arrangement>(
        entry.data.u8[0]);

    ret = metadata->Get(ANDROID_SENSOR_INFO_WHITE_LEVEL, &entry);
    if ((ret != OK) || (entry.count != 1)) {
      ALOGE("%s: Invalid ANDROID_SENSOR_INFO_WHITE_LEVEL!", __FUNCTION__);
      return BAD_VALUE;
    }
    sensor_chars->max_raw_value = entry.data.i32[0];

    ret = metadata->Get(ANDROID_SENSOR_BLACK_LEVEL_PATTERN, &entry);
    if ((ret != OK) ||
        (entry.count != ARRAY_SIZE(sensor_chars->black_level_pattern))) {
      ALOGE("%s: Invalid ANDROID_SENSOR_BLACK_LEVEL_PATTERN!", __FUNCTION__);
      return BAD_VALUE;
    }

    memcpy(sensor_chars->black_level_pattern, entry.data.i32,
           sizeof(sensor_chars->black_level_pattern));

    ret = metadata->Get(ANDROID_LENS_INFO_SHADING_MAP_SIZE, &entry);
    if ((ret == OK) && (entry.count == 2)) {
      sensor_chars->lens_shading_map_size[0] = entry.data.i32[0];
      sensor_chars->lens_shading_map_size[1] = entry.data.i32[1];
    } else {
      ALOGE("%s: No available shading map size!", __FUNCTION__);
      return BAD_VALUE;
    }

    ret = metadata->Get(ANDROID_SENSOR_COLOR_TRANSFORM1, &entry);
    if ((ret != OK) || (entry.count != (3 * 3))) {  // 3x3 rational matrix
      ALOGE("%s: Invalid ANDROID_SENSOR_COLOR_TRANSFORM1!", __FUNCTION__);
      return BAD_VALUE;
    }

    sensor_chars->color_filter.rX = RAT_TO_FLOAT(entry.data.r[0]);
    sensor_chars->color_filter.rY = RAT_TO_FLOAT(entry.data.r[1]);
    sensor_chars->color_filter.rZ = RAT_TO_FLOAT(entry.data.r[2]);
    sensor_chars->color_filter.grX = RAT_TO_FLOAT(entry.data.r[3]);
    sensor_chars->color_filter.grY = RAT_TO_FLOAT(entry.data.r[4]);
    sensor_chars->color_filter.grZ = RAT_TO_FLOAT(entry.data.r[5]);
    sensor_chars->color_filter.gbX = RAT_TO_FLOAT(entry.data.r[3]);
    sensor_chars->color_filter.gbY = RAT_TO_FLOAT(entry.data.r[4]);
    sensor_chars->color_filter.gbZ = RAT_TO_FLOAT(entry.data.r[5]);
    sensor_chars->color_filter.bX = RAT_TO_FLOAT(entry.data.r[6]);
    sensor_chars->color_filter.bY = RAT_TO_FLOAT(entry.data.r[7]);
    sensor_chars->color_filter.bZ = RAT_TO_FLOAT(entry.data.r[8]);

    ret = metadata->Get(ANDROID_SENSOR_FORWARD_MATRIX1, &entry);
    if ((ret != OK) || (entry.count != (3 * 3))) {
      ALOGE("%s: Invalid ANDROID_SENSOR_FORWARD_MATRIX1!", __FUNCTION__);
      return BAD_VALUE;
    }

    sensor_chars->forward_matrix.rX = RAT_TO_FLOAT(entry.data.r[0]);
    sensor_chars->forward_matrix.gX = RAT_TO_FLOAT(entry.data.r[1]);
    sensor_chars->forward_matrix.bX = RAT_TO_FLOAT(entry.data.r[2]);
    sensor_chars->forward_matrix.rY = RAT_TO_FLOAT(entry.data.r[3]);
    sensor_chars->forward_matrix.gY = RAT_TO_FLOAT(entry.data.r[4]);
    sensor_chars->forward_matrix.bY = RAT_TO_FLOAT(entry.data.r[5]);
    sensor_chars->forward_matrix.rZ = RAT_TO_FLOAT(entry.data.r[6]);
    sensor_chars->forward_matrix.gZ = RAT_TO_FLOAT(entry.data.r[7]);
    sensor_chars->forward_matrix.bZ = RAT_TO_FLOAT(entry.data.r[8]);
  } else {
    sensor_chars->color_arangement = static_cast<
        camera_metadata_enum_android_sensor_info_color_filter_arrangement>(
        EmulatedSensor::kSupportedColorFilterArrangement);
    sensor_chars->max_raw_value = EmulatedSensor::kDefaultMaxRawValue;
    memcpy(sensor_chars->black_level_pattern,
           EmulatedSensor::kDefaultBlackLevelPattern,
           sizeof(sensor_chars->black_level_pattern));
  }

  if (HasCapability(
          metadata,
          ANDROID_REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING) ||
      HasCapability(metadata,
                    ANDROID_REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING)) {
    ret = metadata->Get(ANDROID_REQUEST_MAX_NUM_INPUT_STREAMS, &entry);
    if ((ret != OK) || (entry.count != 1)) {
      ALOGE("%s: Invalid ANDROID_REQUEST_MAX_NUM_INPUT_STREAMS!", __FUNCTION__);
      return BAD_VALUE;
    }

    sensor_chars->max_input_streams = entry.data.i32[0];
  }

  ret = metadata->Get(ANDROID_REQUEST_PIPELINE_MAX_DEPTH, &entry);
  if ((ret == OK) && (entry.count == 1)) {
    if (entry.data.u8[0] == 0) {
      ALOGE("%s: Maximum request pipeline must have a non zero value!",
            __FUNCTION__);
      return BAD_VALUE;
    }
    sensor_chars->max_pipeline_depth = entry.data.u8[0];
  } else {
    ALOGE("%s: Maximum request pipeline depth absent!", __FUNCTION__);
    return BAD_VALUE;
  }

  ret = metadata->Get(ANDROID_SENSOR_ORIENTATION, &entry);
  if ((ret == OK) && (entry.count == 1)) {
    sensor_chars->orientation = entry.data.i32[0];
  } else {
    ALOGE("%s: Sensor orientation absent!", __FUNCTION__);
    return BAD_VALUE;
  }

  ret = metadata->Get(ANDROID_LENS_FACING, &entry);
  if ((ret == OK) && (entry.count == 1)) {
    sensor_chars->is_front_facing = false;
    if (ANDROID_LENS_FACING_FRONT == entry.data.u8[0]) {
      sensor_chars->is_front_facing = true;
    }
  } else {
    ALOGE("%s: Lens facing absent!", __FUNCTION__);
    return BAD_VALUE;
  }

  if (HasCapability(metadata,
                    ANDROID_REQUEST_AVAILABLE_CAPABILITIES_STREAM_USE_CASE)) {
    sensor_chars->support_stream_use_case = true;
    sensor_chars->end_valid_stream_use_case = GetLastStreamUseCase(metadata);

  } else {
    sensor_chars->support_stream_use_case = false;
  }

  return ret;
}

PhysicalDeviceMapPtr ClonePhysicalDeviceMap(const PhysicalDeviceMapPtr& src) {
  auto ret = std::make_unique<PhysicalDeviceMap>();
  for (const auto& it : *src) {
    ret->emplace(it.first, std::make_pair(it.second.first,
        HalCameraMetadata::Clone(it.second.second.get())));
  }
  return ret;
}

}  // namespace android
