/*
 * Copyright (C) 2023 The Android Open Source Project
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

#define LOG_TAG "EmulatedCameraDeviceInfo"
#define ATRACE_TAG ATRACE_TAG_CAMERA

#include "EmulatedCameraDeviceInfo.h"

#include <inttypes.h>
#include <log/log.h>

namespace android {

const std::set<uint8_t> EmulatedCameraDeviceInfo::kSupportedCapabilites = {
    ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE,
    ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR,
    ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING,
    ANDROID_REQUEST_AVAILABLE_CAPABILITIES_RAW,
    ANDROID_REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS,
    ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE,
    ANDROID_REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT,
    ANDROID_REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING,
    ANDROID_REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING,
    ANDROID_REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA,
    ANDROID_REQUEST_AVAILABLE_CAPABILITIES_REMOSAIC_REPROCESSING,
    ANDROID_REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR,
    ANDROID_REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT,
    ANDROID_REQUEST_AVAILABLE_CAPABILITIES_STREAM_USE_CASE,
    ANDROID_REQUEST_AVAILABLE_CAPABILITIES_COLOR_SPACE_PROFILES};

const std::set<uint8_t> EmulatedCameraDeviceInfo::kSupportedHWLevels = {
    ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
    ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_FULL,
    ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_3,
};

const std::vector<int64_t> EmulatedCameraDeviceInfo::kSupportedUseCases = {
    ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_DEFAULT,
    ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_PREVIEW,
    ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_STILL_CAPTURE,
    ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_VIDEO_RECORD,
    ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_PREVIEW_VIDEO_STILL,
    ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_VIDEO_CALL};

std::unique_ptr<EmulatedCameraDeviceInfo> EmulatedCameraDeviceInfo::Create(
    std::unique_ptr<HalCameraMetadata> static_metadata) {
  auto deviceInfo = std::make_unique<EmulatedCameraDeviceInfo>();
  if (deviceInfo == nullptr) {
    ALOGE("%s: Creating EmulatedCameraDeviceInfo failed.", __FUNCTION__);
    return nullptr;
  }

  status_t res = deviceInfo->Initialize(std::move(static_metadata));
  if (res != OK) {
    ALOGE("%s: Failed to initialize EmulatedCameraDeviceInfo: %s(%d)",
          __FUNCTION__, strerror(-res), res);
    return nullptr;
  }

  return deviceInfo;
}

std::unique_ptr<EmulatedCameraDeviceInfo> EmulatedCameraDeviceInfo::Clone(
    const EmulatedCameraDeviceInfo& other) {
  std::unique_ptr<HalCameraMetadata> static_metadata =
      HalCameraMetadata::Clone(other.static_metadata_.get());

  return EmulatedCameraDeviceInfo::Create(std::move(static_metadata));
}

status_t EmulatedCameraDeviceInfo::InitializeSensorDefaults() {
  camera_metadata_ro_entry_t entry;
  auto ret =
      static_metadata_->Get(ANDROID_SENSOR_INFO_SENSITIVITY_RANGE, &entry);
  if ((ret == OK) && (entry.count == 2)) {
    sensor_sensitivity_range_ =
        std::make_pair(entry.data.i32[0], entry.data.i32[1]);
  } else if (!supports_manual_sensor_) {
    sensor_sensitivity_range_ =
        std::make_pair(EmulatedSensor::kSupportedSensitivityRange[0],
                       EmulatedSensor::kSupportedSensitivityRange[1]);
  } else {
    ALOGE("%s: Manual sensor devices must advertise sensor sensitivity range!",
          __FUNCTION__);
    return BAD_VALUE;
  }

  ret = static_metadata_->Get(ANDROID_SENSOR_INFO_EXPOSURE_TIME_RANGE, &entry);
  if ((ret == OK) && (entry.count == 2)) {
    sensor_exposure_time_range_ =
        std::make_pair(entry.data.i64[0], entry.data.i64[1]);
  } else if (!supports_manual_sensor_) {
    sensor_exposure_time_range_ =
        std::make_pair(EmulatedSensor::kSupportedExposureTimeRange[0],
                       EmulatedSensor::kSupportedExposureTimeRange[1]);
  } else {
    ALOGE(
        "%s: Manual sensor devices must advertise sensor exposure time range!",
        __FUNCTION__);
    return BAD_VALUE;
  }

  ret = static_metadata_->Get(ANDROID_SENSOR_INFO_MAX_FRAME_DURATION, &entry);
  if ((ret == OK) && (entry.count == 1)) {
    sensor_max_frame_duration_ = entry.data.i64[0];
  } else if (!supports_manual_sensor_) {
    sensor_max_frame_duration_ = EmulatedSensor::kSupportedFrameDurationRange[1];
  } else {
    ALOGE("%s: Manual sensor devices must advertise sensor max frame duration!",
          __FUNCTION__);
    return BAD_VALUE;
  }

  if (supports_manual_sensor_) {
    if (available_requests_.find(ANDROID_SENSOR_SENSITIVITY) ==
        available_requests_.end()) {
      ALOGE(
          "%s: Sensor sensitivity must be configurable on manual sensor "
          "devices!",
          __FUNCTION__);
      return BAD_VALUE;
    }

    if (available_requests_.find(ANDROID_SENSOR_EXPOSURE_TIME) ==
        available_requests_.end()) {
      ALOGE(
          "%s: Sensor exposure time must be configurable on manual sensor "
          "devices!",
          __FUNCTION__);
      return BAD_VALUE;
    }

    if (available_requests_.find(ANDROID_SENSOR_FRAME_DURATION) ==
        available_requests_.end()) {
      ALOGE(
          "%s: Sensor frame duration must be configurable on manual sensor "
          "devices!",
          __FUNCTION__);
      return BAD_VALUE;
    }
  }

  report_rolling_shutter_skew_ =
      available_results_.find(ANDROID_SENSOR_ROLLING_SHUTTER_SKEW) !=
      available_results_.end();
  report_sensitivity_ = available_results_.find(ANDROID_SENSOR_SENSITIVITY) !=
                        available_results_.end();
  report_exposure_time_ =
      available_results_.find(ANDROID_SENSOR_EXPOSURE_TIME) !=
      available_results_.end();
  report_frame_duration_ =
      available_results_.find(ANDROID_SENSOR_FRAME_DURATION) !=
      available_results_.end();
  report_neutral_color_point_ =
      available_results_.find(ANDROID_SENSOR_NEUTRAL_COLOR_POINT) !=
      available_results_.end();
  report_green_split_ = available_results_.find(ANDROID_SENSOR_GREEN_SPLIT) !=
                        available_results_.end();
  report_noise_profile_ =
      available_results_.find(ANDROID_SENSOR_NOISE_PROFILE) !=
      available_results_.end();

  if (is_raw_capable_ && !report_green_split_) {
    ALOGE("%s: RAW capable devices must be able to report the noise profile!",
          __FUNCTION__);
    return BAD_VALUE;
  }

  if (is_raw_capable_ && !report_neutral_color_point_) {
    ALOGE(
        "%s: RAW capable devices must be able to report the neutral color "
        "point!",
        __FUNCTION__);
    return BAD_VALUE;
  }

  if (is_raw_capable_ && !report_green_split_) {
    ALOGE("%s: RAW capable devices must be able to report the green split!",
          __FUNCTION__);
    return BAD_VALUE;
  }
  if (available_results_.find(ANDROID_SENSOR_TIMESTAMP) ==
      available_results_.end()) {
    ALOGE("%s: Sensor timestamp must always be part of the results!",
          __FUNCTION__);
    return BAD_VALUE;
  }

  ret = static_metadata_->Get(ANDROID_SENSOR_AVAILABLE_TEST_PATTERN_MODES,
                              &entry);
  if (ret == OK) {
    available_test_pattern_modes_.insert(entry.data.i32,
                                         entry.data.i32 + entry.count);
  } else {
    ALOGE("%s: No available test pattern modes!", __FUNCTION__);
    return BAD_VALUE;
  }

  sensor_exposure_time_ = GetClosestValue(EmulatedSensor::kDefaultExposureTime,
                                          sensor_exposure_time_range_.first,
                                          sensor_exposure_time_range_.second);
  sensor_frame_duration_ =
      GetClosestValue(EmulatedSensor::kDefaultFrameDuration,
                      EmulatedSensor::kSupportedFrameDurationRange[0],
                      sensor_max_frame_duration_);
  sensor_sensitivity_ = GetClosestValue(EmulatedSensor::kDefaultSensitivity,
                                        sensor_sensitivity_range_.first,
                                        sensor_sensitivity_range_.second);

  bool off_test_pattern_mode_supported =
      available_test_pattern_modes_.find(ANDROID_SENSOR_TEST_PATTERN_MODE_OFF) !=
      available_test_pattern_modes_.end();
  int32_t test_pattern_mode = (off_test_pattern_mode_supported)
                                  ? ANDROID_SENSOR_TEST_PATTERN_MODE_OFF
                                  : *available_test_pattern_modes_.begin();
  int32_t test_pattern_data[4] = {0, 0, 0, 0};

  for (size_t idx = 0; idx < kTemplateCount; idx++) {
    if (default_requests_[idx].get() == nullptr) {
      continue;
    }

    default_requests_[idx]->Set(ANDROID_SENSOR_EXPOSURE_TIME,
                                &sensor_exposure_time_, 1);
    default_requests_[idx]->Set(ANDROID_SENSOR_FRAME_DURATION,
                                &sensor_frame_duration_, 1);
    default_requests_[idx]->Set(ANDROID_SENSOR_SENSITIVITY,
                                &sensor_sensitivity_, 1);
    default_requests_[idx]->Set(ANDROID_SENSOR_TEST_PATTERN_MODE,
                                &test_pattern_mode, 1);
    default_requests_[idx]->Set(ANDROID_SENSOR_TEST_PATTERN_DATA,
                                test_pattern_data, 4);
  }

  return OK;
}

status_t EmulatedCameraDeviceInfo::InitializeStatisticsDefaults() {
  camera_metadata_ro_entry_t entry;
  auto ret = static_metadata_->Get(
      ANDROID_STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES, &entry);
  if (ret == OK) {
    available_face_detect_modes_.insert(entry.data.u8,
                                        entry.data.u8 + entry.count);
  } else {
    ALOGE("%s: No available face detect modes!", __FUNCTION__);
    return BAD_VALUE;
  }

  ret = static_metadata_->Get(
      ANDROID_STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES, &entry);
  if (ret == OK) {
    available_lens_shading_map_modes_.insert(entry.data.u8,
                                             entry.data.u8 + entry.count);
  } else {
    ALOGE("%s: No available lens shading modes!", __FUNCTION__);
    return BAD_VALUE;
  }

  ret = static_metadata_->Get(
      ANDROID_STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES, &entry);
  if (ret == OK) {
    available_hot_pixel_map_modes_.insert(entry.data.u8,
                                          entry.data.u8 + entry.count);
  } else if (is_raw_capable_) {
    ALOGE("%s: RAW capable device must support hot pixel map modes!",
          __FUNCTION__);
    return BAD_VALUE;
  } else {
    available_hot_pixel_map_modes_.emplace(
        ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE_OFF);
  }

  bool hot_pixel_mode_off_supported =
      available_hot_pixel_map_modes_.find(
          ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE_OFF) !=
      available_hot_pixel_map_modes_.end();
  bool face_detect_mode_off_supported =
      available_face_detect_modes_.find(
          ANDROID_STATISTICS_FACE_DETECT_MODE_OFF) !=
      available_face_detect_modes_.end();
  bool lens_shading_map_mode_off_supported =
      available_lens_shading_map_modes_.find(
          ANDROID_STATISTICS_LENS_SHADING_MAP_MODE_ON) !=
      available_lens_shading_map_modes_.end();
  bool lens_shading_map_mode_on_supported =
      available_lens_shading_map_modes_.find(
          ANDROID_STATISTICS_LENS_SHADING_MAP_MODE_ON) !=
      available_lens_shading_map_modes_.end();
  if (is_raw_capable_ && !lens_shading_map_mode_on_supported) {
    ALOGE("%s: RAW capable device must support lens shading map reporting!",
          __FUNCTION__);
    return BAD_VALUE;
  }

  if (lens_shading_map_mode_on_supported &&
      (available_results_.find(ANDROID_STATISTICS_LENS_SHADING_MAP) ==
       available_results_.end())) {
    ALOGE(
        "%s: Lens shading map reporting available but corresponding result key "
        "is absent!",
        __FUNCTION__);
    return BAD_VALUE;
  }

  if (lens_shading_map_mode_on_supported &&
      ((shading_map_size_[0] == 0) || (shading_map_size_[1] == 0))) {
    ALOGE(
        "%s: Lens shading map reporting available but without valid shading "
        "map size!",
        __FUNCTION__);
    return BAD_VALUE;
  }

  report_lens_intrinsics_samples_ =
      (available_results_.find(ANDROID_STATISTICS_LENS_INTRINSIC_SAMPLES) !=
       available_results_.end()) &&
      (available_results_.find(ANDROID_STATISTICS_LENS_INTRINSIC_TIMESTAMPS) !=
       available_results_.end());

  report_scene_flicker_ =
      available_results_.find(ANDROID_STATISTICS_SCENE_FLICKER) !=
      available_results_.end();

  uint8_t face_detect_mode = face_detect_mode_off_supported
                                 ? ANDROID_STATISTICS_FACE_DETECT_MODE_OFF
                                 : *available_face_detect_modes_.begin();
  uint8_t hot_pixel_map_mode = hot_pixel_mode_off_supported
                                   ? ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE_OFF
                                   : *available_hot_pixel_map_modes_.begin();
  uint8_t lens_shading_map_mode =
      lens_shading_map_mode_off_supported
          ? ANDROID_STATISTICS_LENS_SHADING_MAP_MODE_OFF
          : *available_lens_shading_map_modes_.begin();
  for (size_t idx = 0; idx < kTemplateCount; idx++) {
    if (default_requests_[idx].get() == nullptr) {
      continue;
    }

    if ((static_cast<RequestTemplate>(idx) == RequestTemplate::kStillCapture) &&
        is_raw_capable_ && lens_shading_map_mode_on_supported) {
      uint8_t lens_shading_map_on = ANDROID_STATISTICS_LENS_SHADING_MAP_MODE_ON;
      default_requests_[idx]->Set(ANDROID_STATISTICS_LENS_SHADING_MAP_MODE,
                                  &lens_shading_map_on, 1);
    } else {
      default_requests_[idx]->Set(ANDROID_STATISTICS_LENS_SHADING_MAP_MODE,
                                  &lens_shading_map_mode, 1);
    }

    default_requests_[idx]->Set(ANDROID_STATISTICS_FACE_DETECT_MODE,
                                &face_detect_mode, 1);
    default_requests_[idx]->Set(ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE,
                                &hot_pixel_map_mode, 1);
  }

  return InitializeBlackLevelDefaults();
}

status_t EmulatedCameraDeviceInfo::InitializeControlSceneDefaults() {
  camera_metadata_ro_entry_t entry;
  auto ret =
      static_metadata_->Get(ANDROID_CONTROL_AVAILABLE_SCENE_MODES, &entry);
  if (ret == OK) {
    available_scenes_.insert(entry.data.u8, entry.data.u8 + entry.count);
  } else {
    ALOGE("%s: No available scene modes!", __FUNCTION__);
    return BAD_VALUE;
  }

  if ((entry.count == 1) &&
      (entry.data.u8[0] == ANDROID_CONTROL_SCENE_MODE_DISABLED)) {
    scenes_supported_ = false;
    return OK;
  } else {
    scenes_supported_ = true;
  }

  if (available_requests_.find(ANDROID_CONTROL_SCENE_MODE) ==
      available_requests_.end()) {
    ALOGE("%s: Scene mode cannot be set!", __FUNCTION__);
    return BAD_VALUE;
  }

  if (available_results_.find(ANDROID_CONTROL_SCENE_MODE) ==
      available_results_.end()) {
    ALOGE("%s: Scene mode cannot be reported!", __FUNCTION__);
    return BAD_VALUE;
  }

  camera_metadata_ro_entry_t overrides_entry;
  ret = static_metadata_->Get(ANDROID_CONTROL_SCENE_MODE_OVERRIDES,
                              &overrides_entry);
  if ((ret == OK) && ((overrides_entry.count / 3) == available_scenes_.size()) &&
      ((overrides_entry.count % 3) == 0)) {
    for (size_t i = 0; i < entry.count; i++) {
      SceneOverride scene(overrides_entry.data.u8[i * 3],
                          overrides_entry.data.u8[i * 3 + 1],
                          overrides_entry.data.u8[i * 3 + 2]);
      if (available_ae_modes_.find(scene.ae_mode) == available_ae_modes_.end()) {
        ALOGE("%s: AE scene mode override: %d not supported!", __FUNCTION__,
              scene.ae_mode);
        return BAD_VALUE;
      }
      if (available_awb_modes_.find(scene.awb_mode) ==
          available_awb_modes_.end()) {
        ALOGE("%s: AWB scene mode override: %d not supported!", __FUNCTION__,
              scene.awb_mode);
        return BAD_VALUE;
      }
      if (available_af_modes_.find(scene.af_mode) == available_af_modes_.end()) {
        ALOGE("%s: AF scene mode override: %d not supported!", __FUNCTION__,
              scene.af_mode);
        return BAD_VALUE;
      }
      scene_overrides_.emplace(entry.data.u8[i], scene);
    }
  } else {
    ALOGE("%s: No available scene overrides!", __FUNCTION__);
    return BAD_VALUE;
  }

  return OK;
}

status_t EmulatedCameraDeviceInfo::InitializeControlAFDefaults() {
  camera_metadata_ro_entry_t entry;
  auto ret = static_metadata_->Get(ANDROID_CONTROL_AF_AVAILABLE_MODES, &entry);
  if (ret == OK) {
    available_af_modes_.insert(entry.data.u8, entry.data.u8 + entry.count);
  } else {
    ALOGE("%s: No available AF modes!", __FUNCTION__);
    return BAD_VALUE;
  }
  // Off mode must always be present
  if (available_af_modes_.find(ANDROID_CONTROL_AF_MODE_OFF) ==
      available_af_modes_.end()) {
    ALOGE("%s: AF off control mode must always be present!", __FUNCTION__);
    return BAD_VALUE;
  }

  if (available_requests_.find(ANDROID_CONTROL_AF_MODE) ==
      available_requests_.end()) {
    ALOGE("%s: Clients must be able to set AF mode!", __FUNCTION__);
    return BAD_VALUE;
  }

  if (available_requests_.find(ANDROID_CONTROL_AF_TRIGGER) ==
      available_requests_.end()) {
    ALOGE("%s: Clients must be able to set AF trigger!", __FUNCTION__);
    return BAD_VALUE;
  }
  if (available_results_.find(ANDROID_CONTROL_AF_TRIGGER) ==
      available_results_.end()) {
    ALOGE("%s: AF trigger must be reported!", __FUNCTION__);
    return BAD_VALUE;
  }

  if (available_results_.find(ANDROID_CONTROL_AF_MODE) ==
      available_results_.end()) {
    ALOGE("%s: AF mode must be reported!", __FUNCTION__);
    return BAD_VALUE;
  }

  if (available_results_.find(ANDROID_CONTROL_AF_STATE) ==
      available_results_.end()) {
    ALOGE("%s: AF state must be reported!", __FUNCTION__);
    return BAD_VALUE;
  }

  bool auto_mode_present =
      available_af_modes_.find(ANDROID_CONTROL_AF_MODE_AUTO) !=
      available_af_modes_.end();
  bool picture_caf_mode_present =
      available_af_modes_.find(ANDROID_CONTROL_AF_MODE_CONTINUOUS_PICTURE) !=
      available_af_modes_.end();
  bool video_caf_mode_present =
      available_af_modes_.find(ANDROID_CONTROL_AF_MODE_CONTINUOUS_VIDEO) !=
      available_af_modes_.end();
  af_supported_ = auto_mode_present && (minimum_focus_distance_ > .0f);
  picture_caf_supported_ =
      picture_caf_mode_present && (minimum_focus_distance_ > .0f);
  video_caf_supported_ =
      video_caf_mode_present && (minimum_focus_distance_ > .0f);

  return OK;
}

status_t EmulatedCameraDeviceInfo::InitializeControlAWBDefaults() {
  camera_metadata_ro_entry_t entry;
  auto ret = static_metadata_->Get(ANDROID_CONTROL_AWB_AVAILABLE_MODES, &entry);
  if (ret == OK) {
    available_awb_modes_.insert(entry.data.u8, entry.data.u8 + entry.count);
  } else {
    ALOGE("%s: No available AWB modes!", __FUNCTION__);
    return BAD_VALUE;
  }
  // Auto mode must always be present
  if (available_awb_modes_.find(ANDROID_CONTROL_AWB_MODE_AUTO) ==
      available_awb_modes_.end()) {
    ALOGE("%s: AWB auto control mode must always be present!", __FUNCTION__);
    return BAD_VALUE;
  }

  if (available_results_.find(ANDROID_CONTROL_AWB_MODE) ==
      available_results_.end()) {
    ALOGE("%s: AWB mode must be reported!", __FUNCTION__);
    return BAD_VALUE;
  }

  if (available_results_.find(ANDROID_CONTROL_AWB_STATE) ==
      available_results_.end()) {
    ALOGE("%s: AWB state must be reported!", __FUNCTION__);
    return BAD_VALUE;
  }

  ret = static_metadata_->Get(ANDROID_CONTROL_AWB_LOCK_AVAILABLE, &entry);
  if ((ret == OK) && (entry.count == 1)) {
    awb_lock_available_ =
        entry.data.u8[0] == ANDROID_CONTROL_AWB_LOCK_AVAILABLE_TRUE;
  } else {
    ALOGV("%s: No available AWB lock!", __FUNCTION__);
    awb_lock_available_ = false;
  }
  report_awb_lock_ = available_results_.find(ANDROID_CONTROL_AWB_LOCK) !=
                     available_results_.end();

  return OK;
}

status_t EmulatedCameraDeviceInfo::InitializeBlackLevelDefaults() {
  if (is_level_full_or_higher_) {
    if (available_requests_.find(ANDROID_BLACK_LEVEL_LOCK) ==
        available_requests_.end()) {
      ALOGE(
          "%s: Full or above capable devices must be able to set the black "
          "level lock!",
          __FUNCTION__);
      return BAD_VALUE;
    }

    if (available_results_.find(ANDROID_BLACK_LEVEL_LOCK) ==
        available_results_.end()) {
      ALOGE(
          "%s: Full or above capable devices must be able to report the black "
          "level lock!",
          __FUNCTION__);
      return BAD_VALUE;
    }

    report_black_level_lock_ = true;
    uint8_t blackLevelLock = ANDROID_BLACK_LEVEL_LOCK_OFF;
    for (size_t idx = 0; idx < kTemplateCount; idx++) {
      if (default_requests_[idx].get() == nullptr) {
        continue;
      }

      default_requests_[idx]->Set(ANDROID_BLACK_LEVEL_LOCK, &blackLevelLock, 1);
    }
  }

  return InitializeEdgeDefaults();
}

status_t EmulatedCameraDeviceInfo::InitializeControlAEDefaults() {
  camera_metadata_ro_entry_t entry;
  auto ret = static_metadata_->Get(ANDROID_CONTROL_AE_AVAILABLE_MODES, &entry);
  if (ret == OK) {
    available_ae_modes_.insert(entry.data.u8, entry.data.u8 + entry.count);
  } else {
    ALOGE("%s: No available AE modes!", __FUNCTION__);
    return BAD_VALUE;
  }
  // On mode must always be present
  if (available_ae_modes_.find(ANDROID_CONTROL_AE_MODE_ON) ==
      available_ae_modes_.end()) {
    ALOGE("%s: AE on control mode must always be present!", __FUNCTION__);
    return BAD_VALUE;
  }

  if (available_results_.find(ANDROID_CONTROL_AE_MODE) ==
      available_results_.end()) {
    ALOGE("%s: AE mode must be reported!", __FUNCTION__);
    return BAD_VALUE;
  }

  if (available_results_.find(ANDROID_CONTROL_AE_STATE) ==
      available_results_.end()) {
    ALOGE("%s: AE state must be reported!", __FUNCTION__);
    return BAD_VALUE;
  }

  ret = static_metadata_->Get(ANDROID_CONTROL_AE_LOCK_AVAILABLE, &entry);
  if ((ret == OK) && (entry.count == 1)) {
    ae_lock_available_ =
        entry.data.u8[0] == ANDROID_CONTROL_AE_LOCK_AVAILABLE_TRUE;
  } else {
    ALOGV("%s: No available AE lock!", __FUNCTION__);
    ae_lock_available_ = false;
  }
  report_ae_lock_ = available_results_.find(ANDROID_CONTROL_AE_LOCK) !=
                    available_results_.end();

  if (supports_manual_sensor_) {
    if (!ae_lock_available_) {
      ALOGE("%s: AE lock must always be available for manual sensors!",
            __FUNCTION__);
      return BAD_VALUE;
    }
    auto off_mode = available_control_modes_.find(ANDROID_CONTROL_MODE_OFF);
    if (off_mode == available_control_modes_.end()) {
      ALOGE("%s: Off control mode must always be present for manual sensors!",
            __FUNCTION__);
      return BAD_VALUE;
    }

    off_mode = available_ae_modes_.find(ANDROID_CONTROL_AE_MODE_OFF);
    if (off_mode == available_ae_modes_.end()) {
      ALOGE(
          "%s: AE off control mode must always be present for manual sensors!",
          __FUNCTION__);
      return BAD_VALUE;
    }
  }

  if (available_requests_.find(ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER) ==
      available_requests_.end()) {
    ALOGE("%s: Clients must be able to set AE pre-capture trigger!",
          __FUNCTION__);
    return BAD_VALUE;
  }

  if (available_results_.find(ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER) ==
      available_results_.end()) {
    ALOGE("%s: AE pre-capture trigger must be reported!", __FUNCTION__);
    return BAD_VALUE;
  }

  ret = static_metadata_->Get(ANDROID_CONTROL_AE_AVAILABLE_ANTIBANDING_MODES,
                              &entry);
  if (ret == OK) {
    available_antibanding_modes_.insert(entry.data.u8,
                                        entry.data.u8 + entry.count);
  } else {
    ALOGE("%s: No available antibanding modes!", __FUNCTION__);
    return BAD_VALUE;
  }

  ret = static_metadata_->Get(ANDROID_CONTROL_AE_COMPENSATION_RANGE, &entry);
  if ((ret == OK) && (entry.count == 2)) {
    exposure_compensation_range_[0] = entry.data.i32[0];
    exposure_compensation_range_[1] = entry.data.i32[1];
  } else {
    ALOGE("%s: No available exposure compensation range!", __FUNCTION__);
    return BAD_VALUE;
  }

  ret = static_metadata_->Get(ANDROID_CONTROL_AE_COMPENSATION_STEP, &entry);
  if ((ret == OK) && (entry.count == 1)) {
    exposure_compensation_step_ = entry.data.r[0];
  } else {
    ALOGE("%s: No available exposure compensation step!", __FUNCTION__);
    return BAD_VALUE;
  }

  bool ae_comp_requests =
      available_requests_.find(ANDROID_CONTROL_AE_EXPOSURE_COMPENSATION) !=
      available_requests_.end();
  bool ae_comp_results =
      available_results_.find(ANDROID_CONTROL_AE_EXPOSURE_COMPENSATION) !=
      available_results_.end();
  exposure_compensation_supported_ =
      ((exposure_compensation_range_[0] < 0) &&
       (exposure_compensation_range_[1] > 0) &&
       (exposure_compensation_step_.denominator > 0) &&
       (exposure_compensation_step_.numerator > 0)) &&
      ae_comp_results && ae_comp_requests;

  return OK;
}

status_t EmulatedCameraDeviceInfo::InitializeMeteringRegionDefault(
    uint32_t tag, int32_t* region /*out*/) {
  if (region == nullptr) {
    return BAD_VALUE;
  }
  if (available_requests_.find(tag) == available_requests_.end()) {
    ALOGE("%s: %d metering region configuration must be supported!",
          __FUNCTION__, tag);
    return BAD_VALUE;
  }
  if (available_results_.find(tag) == available_results_.end()) {
    ALOGE("%s: %d metering region must be reported!", __FUNCTION__, tag);
    return BAD_VALUE;
  }

  region[0] = scaler_crop_region_default_[0];
  region[1] = scaler_crop_region_default_[1];
  region[2] = scaler_crop_region_default_[2];
  region[3] = scaler_crop_region_default_[3];
  region[4] = 0;

  return OK;
}

status_t EmulatedCameraDeviceInfo::InitializeControlDefaults() {
  camera_metadata_ro_entry_t entry;
  int32_t metering_area[5] = {0};  // (top, left, width, height, wight)
  auto ret = static_metadata_->Get(ANDROID_CONTROL_AVAILABLE_MODES, &entry);
  if (ret == OK) {
    available_control_modes_.insert(entry.data.u8, entry.data.u8 + entry.count);
  } else {
    ALOGE("%s: No available control modes!", __FUNCTION__);
    return BAD_VALUE;
  }

  available_sensor_pixel_modes_.insert(ANDROID_SENSOR_PIXEL_MODE_DEFAULT);

  if (SupportsCapability(
          ANDROID_REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR)) {
    available_sensor_pixel_modes_.insert(
        ANDROID_SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION);
  }

  // Auto mode must always be present
  if (available_control_modes_.find(ANDROID_CONTROL_MODE_AUTO) ==
      available_control_modes_.end()) {
    ALOGE("%s: Auto control modes must always be present!", __FUNCTION__);
    return BAD_VALUE;
  }

  // Capture intent must always be user configurable
  if (available_requests_.find(ANDROID_CONTROL_CAPTURE_INTENT) ==
      available_requests_.end()) {
    ALOGE("%s: Clients must be able to set the capture intent!", __FUNCTION__);
    return BAD_VALUE;
  }

  ret = static_metadata_->Get(ANDROID_CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES,
                              &entry);
  if ((ret == OK) && ((entry.count % 2) == 0)) {
    available_fps_ranges_.reserve(entry.count / 2);
    for (size_t i = 0; i < entry.count; i += 2) {
      FPSRange range(entry.data.i32[i], entry.data.i32[i + 1]);
      if (range.min_fps > range.max_fps) {
        ALOGE("%s: Minimum framerate: %d bigger than maximum framerate: %d",
              __FUNCTION__, range.min_fps, range.max_fps);
        return BAD_VALUE;
      }
      if ((range.max_fps >= kMinimumStreamingFPS) &&
          (range.max_fps == range.min_fps) && (ae_target_fps_.max_fps == 0)) {
        ae_target_fps_ = range;
      }
      available_fps_ranges_.push_back(range);
    }
  } else {
    ALOGE("%s: No available framerate ranges!", __FUNCTION__);
    return BAD_VALUE;
  }

  if (ae_target_fps_.max_fps == 0) {
    ALOGE("%s: No minimum streaming capable framerate range available!",
          __FUNCTION__);
    return BAD_VALUE;
  }

  if (available_requests_.find(ANDROID_CONTROL_AE_TARGET_FPS_RANGE) ==
      available_requests_.end()) {
    ALOGE("%s: Clients must be able to set the target framerate range!",
          __FUNCTION__);
    return BAD_VALUE;
  }

  if (available_results_.find(ANDROID_CONTROL_AE_TARGET_FPS_RANGE) ==
      available_results_.end()) {
    ALOGE("%s: Target framerate must be reported!", __FUNCTION__);
    return BAD_VALUE;
  }

  report_extended_scene_mode_ =
      available_results_.find(ANDROID_CONTROL_EXTENDED_SCENE_MODE) !=
      available_results_.end();

  if (is_backward_compatible_) {
    ret = static_metadata_->Get(ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST,
                                &entry);
    if (ret == OK) {
      post_raw_boost_ = entry.data.i32[0];
    } else {
      ALOGW("%s: No available post RAW boost! Setting default!", __FUNCTION__);
      post_raw_boost_ = 100;
    }
    report_post_raw_boost_ =
        available_results_.find(ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST) !=
        available_results_.end();

    ret = static_metadata_->Get(ANDROID_CONTROL_AVAILABLE_EFFECTS, &entry);
    if ((ret == OK) && (entry.count > 0)) {
      available_effects_.insert(entry.data.u8, entry.data.u8 + entry.count);
      if (available_effects_.find(ANDROID_CONTROL_EFFECT_MODE_OFF) ==
          available_effects_.end()) {
        ALOGE("%s: Off color effect mode not supported!", __FUNCTION__);
        return BAD_VALUE;
      }
    } else {
      ALOGE("%s: No available effects!", __FUNCTION__);
      return BAD_VALUE;
    }

    ret = static_metadata_->Get(
        ANDROID_CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES, &entry);
    if ((ret == OK) && (entry.count > 0)) {
      available_vstab_modes_.insert(entry.data.u8, entry.data.u8 + entry.count);
      if (available_vstab_modes_.find(
              ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_OFF) ==
          available_vstab_modes_.end()) {
        ALOGE("%s: Off video stabilization mode not supported!", __FUNCTION__);
        return BAD_VALUE;
      }
      if (available_vstab_modes_.find(
              ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_ON) !=
          available_vstab_modes_.end()) {
        vstab_available_ = true;
      }
    } else {
      ALOGE("%s: No available video stabilization modes!", __FUNCTION__);
      return BAD_VALUE;
    }

    ret = static_metadata_->Get(ANDROID_SCALER_AVAILABLE_MAX_DIGITAL_ZOOM,
                                &entry);
    if ((ret == OK) && (entry.count > 0)) {
      if (entry.count != 1) {
        ALOGE("%s: Invalid max digital zoom capability!", __FUNCTION__);
        return BAD_VALUE;
      }
      max_zoom_ = entry.data.f[0];
    } else {
      ALOGE("%s: No available max digital zoom", __FUNCTION__);
      return BAD_VALUE;
    }

    ret = static_metadata_->Get(ANDROID_CONTROL_ZOOM_RATIO_RANGE, &entry);
    if ((ret == OK) && (entry.count > 0)) {
      if (entry.count != 2) {
        ALOGE("%s: Invalid zoom ratio range capability!", __FUNCTION__);
        return BAD_VALUE;
      }

      if (entry.data.f[1] != max_zoom_) {
        ALOGE("%s: Max zoom ratio must be equal to max digital zoom",
              __FUNCTION__);
        return BAD_VALUE;
      }

      if (entry.data.f[1] < entry.data.f[0]) {
        ALOGE("%s: Max zoom ratio must be larger than min zoom ratio",
              __FUNCTION__);
        return BAD_VALUE;
      }

      // Validity check request and result keys
      if (available_requests_.find(ANDROID_CONTROL_ZOOM_RATIO) ==
          available_requests_.end()) {
        ALOGE("%s: Zoom ratio tag must be available in available request keys",
              __FUNCTION__);
        return BAD_VALUE;
      }
      if (available_results_.find(ANDROID_CONTROL_ZOOM_RATIO) ==
          available_results_.end()) {
        ALOGE("%s: Zoom ratio tag must be available in available result keys",
              __FUNCTION__);
        return BAD_VALUE;
      }

      zoom_ratio_supported_ = true;
      min_zoom_ = entry.data.f[0];
    }

    ret = static_metadata_->Get(
        ANDROID_CONTROL_AVAILABLE_EXTENDED_SCENE_MODE_MAX_SIZES, &entry);
    if ((ret == OK) && (entry.count > 0)) {
      if (entry.count % 3 != 0) {
        ALOGE("%s: Invalid bokeh capabilities!", __FUNCTION__);
        return BAD_VALUE;
      }

      camera_metadata_ro_entry_t zoom_ratio_ranges_entry;
      ret = static_metadata_->Get(
          ANDROID_CONTROL_AVAILABLE_EXTENDED_SCENE_MODE_ZOOM_RATIO_RANGES,
          &zoom_ratio_ranges_entry);
      if (ret != OK ||
          zoom_ratio_ranges_entry.count / 2 != entry.count / 3 - 1) {
        ALOGE("%s: Invalid bokeh mode zoom ratio ranges.", __FUNCTION__);
        return BAD_VALUE;
      }

      // Validity check request and characteristics keys
      if (available_requests_.find(ANDROID_CONTROL_EXTENDED_SCENE_MODE) ==
          available_requests_.end()) {
        ALOGE("%s: Extended scene mode must be configurable for this device",
              __FUNCTION__);
        return BAD_VALUE;
      }
      if (available_characteristics_.find(
              ANDROID_CONTROL_AVAILABLE_EXTENDED_SCENE_MODE_MAX_SIZES) ==
              available_characteristics_.end() ||
          available_characteristics_.find(
              ANDROID_CONTROL_AVAILABLE_EXTENDED_SCENE_MODE_ZOOM_RATIO_RANGES) ==
              available_characteristics_.end()) {
        ALOGE(
            "%s: ExtendedSceneMode maxSizes and zoomRatioRanges "
            "characteristics keys must "
            "be available",
            __FUNCTION__);
        return BAD_VALUE;
      }

      // Derive available bokeh caps.
      StreamConfigurationMap stream_configuration_map(*static_metadata_);
      std::set<StreamSize> yuv_sizes = stream_configuration_map.GetOutputSizes(
          HAL_PIXEL_FORMAT_YCBCR_420_888);
      bool has_extended_scene_mode_off = false;
      for (size_t i = 0, j = 0; i < entry.count; i += 3) {
        int32_t mode = entry.data.i32[i];
        int32_t max_width = entry.data.i32[i + 1];
        int32_t max_height = entry.data.i32[i + 2];
        float min_zoom_ratio, max_zoom_ratio;

        if (mode < ANDROID_CONTROL_EXTENDED_SCENE_MODE_DISABLED ||
            (mode > ANDROID_CONTROL_EXTENDED_SCENE_MODE_BOKEH_CONTINUOUS &&
             mode < ANDROID_CONTROL_EXTENDED_SCENE_MODE_VENDOR_START)) {
          ALOGE("%s: Invalid extended scene mode %d", __FUNCTION__, mode);
          return BAD_VALUE;
        }

        if (mode == ANDROID_CONTROL_EXTENDED_SCENE_MODE_DISABLED) {
          has_extended_scene_mode_off = true;
          if (max_width != 0 || max_height != 0) {
            ALOGE(
                "%s: Invalid max width or height for "
                "EXTENDED_SCENE_MODE_DISABLED",
                __FUNCTION__);
            return BAD_VALUE;
          }
          min_zoom_ratio = min_zoom_;
          max_zoom_ratio = max_zoom_;
        } else if (yuv_sizes.find({max_width, max_height}) == yuv_sizes.end()) {
          ALOGE("%s: Invalid max width or height for extended scene mode %d",
                __FUNCTION__, mode);
          return BAD_VALUE;
        } else {
          min_zoom_ratio = zoom_ratio_ranges_entry.data.f[j];
          max_zoom_ratio = zoom_ratio_ranges_entry.data.f[j + 1];
          j += 2;
        }

        ExtendedSceneModeCapability cap(mode, max_width, max_height,
                                        min_zoom_ratio, max_zoom_ratio);
        available_extended_scene_mode_caps_.push_back(cap);
      }
      if (!has_extended_scene_mode_off) {
        ALOGE("%s: Off extended scene mode not supported!", __FUNCTION__);
        return BAD_VALUE;
      }
    }

    ret = static_metadata_->Get(ANDROID_CONTROL_MAX_REGIONS, &entry);
    if ((ret == OK) && (entry.count == 3)) {
      max_ae_regions_ = entry.data.i32[0];
      max_awb_regions_ = entry.data.i32[1];
      max_af_regions_ = entry.data.i32[2];
    } else {
      ALOGE(
          "%s: Metering regions must be available for backward compatible "
          "devices!",
          __FUNCTION__);
      return BAD_VALUE;
    }

    if ((is_level_full_or_higher_) &&
        ((max_ae_regions_ == 0) || (max_af_regions_ == 0))) {
      ALOGE(
          "%s: Full and higher level cameras must support at AF and AE "
          "metering regions",
          __FUNCTION__);
      return BAD_VALUE;
    }

    if (max_ae_regions_ > 0) {
      ret = InitializeMeteringRegionDefault(ANDROID_CONTROL_AE_REGIONS,
                                            ae_metering_region_);
      if (ret != OK) {
        return ret;
      }
    }

    if (max_awb_regions_ > 0) {
      ret = InitializeMeteringRegionDefault(ANDROID_CONTROL_AWB_REGIONS,
                                            awb_metering_region_);
      if (ret != OK) {
        return ret;
      }
    }

    if (max_af_regions_ > 0) {
      ret = InitializeMeteringRegionDefault(ANDROID_CONTROL_AF_REGIONS,
                                            af_metering_region_);
      if (ret != OK) {
        return ret;
      }
    }

    ret = InitializeControlAEDefaults();
    if (ret != OK) {
      return ret;
    }

    ret = InitializeControlAWBDefaults();
    if (ret != OK) {
      return ret;
    }

    ret = InitializeControlAFDefaults();
    if (ret != OK) {
      return ret;
    }

    ret = InitializeControlSceneDefaults();
    if (ret != OK) {
      return ret;
    }
  }

  for (size_t idx = 0; idx < kTemplateCount; idx++) {
    auto template_idx = static_cast<RequestTemplate>(idx);
    if (default_requests_[idx].get() == nullptr) {
      continue;
    }

    uint8_t intent = ANDROID_CONTROL_CAPTURE_INTENT_CUSTOM;
    uint8_t control_mode, ae_mode, awb_mode, af_mode, scene_mode, vstab_mode;
    control_mode = ANDROID_CONTROL_MODE_AUTO;
    ae_mode = ANDROID_CONTROL_AE_MODE_ON;
    awb_mode = ANDROID_CONTROL_AWB_MODE_AUTO;
    af_mode = af_supported_ ? ANDROID_CONTROL_AF_MODE_AUTO
                            : ANDROID_CONTROL_AF_MODE_OFF;
    scene_mode = ANDROID_CONTROL_SCENE_MODE_DISABLED;
    vstab_mode = ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_OFF;
    uint8_t effect_mode = ANDROID_CONTROL_EFFECT_MODE_OFF;
    uint8_t ae_lock = ANDROID_CONTROL_AE_LOCK_OFF;
    uint8_t awb_lock = ANDROID_CONTROL_AWB_LOCK_OFF;
    int32_t ae_target_fps[] = {ae_target_fps_.min_fps, ae_target_fps_.max_fps};
    float zoom_ratio = 1.0f;
    switch (template_idx) {
      case RequestTemplate::kManual:
        intent = ANDROID_CONTROL_CAPTURE_INTENT_MANUAL;
        control_mode = ANDROID_CONTROL_MODE_OFF;
        ae_mode = ANDROID_CONTROL_AE_MODE_OFF;
        awb_mode = ANDROID_CONTROL_AWB_MODE_OFF;
        af_mode = ANDROID_CONTROL_AF_MODE_OFF;
        break;
      case RequestTemplate::kZeroShutterLag:
        intent = ANDROID_CONTROL_CAPTURE_INTENT_ZERO_SHUTTER_LAG;
        if (picture_caf_supported_) {
          af_mode = ANDROID_CONTROL_AF_MODE_CONTINUOUS_PICTURE;
        }
        break;
      case RequestTemplate::kPreview:
        intent = ANDROID_CONTROL_CAPTURE_INTENT_PREVIEW;
        if (picture_caf_supported_) {
          af_mode = ANDROID_CONTROL_AF_MODE_CONTINUOUS_PICTURE;
        }
        break;
      case RequestTemplate::kStillCapture:
        intent = ANDROID_CONTROL_CAPTURE_INTENT_STILL_CAPTURE;
        if (picture_caf_supported_) {
          af_mode = ANDROID_CONTROL_AF_MODE_CONTINUOUS_PICTURE;
        }
        break;
      case RequestTemplate::kVideoRecord:
        intent = ANDROID_CONTROL_CAPTURE_INTENT_VIDEO_RECORD;
        if (video_caf_supported_) {
          af_mode = ANDROID_CONTROL_AF_MODE_CONTINUOUS_VIDEO;
        }
        if (vstab_available_) {
          vstab_mode = ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_ON;
        }
        break;
      case RequestTemplate::kVideoSnapshot:
        intent = ANDROID_CONTROL_CAPTURE_INTENT_VIDEO_SNAPSHOT;
        if (video_caf_supported_) {
          af_mode = ANDROID_CONTROL_AF_MODE_CONTINUOUS_VIDEO;
        }
        if (vstab_available_) {
          vstab_mode = ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_ON;
        }
        break;
      default:
        // Noop
        break;
    }

    if (intent != ANDROID_CONTROL_CAPTURE_INTENT_CUSTOM) {
      default_requests_[idx]->Set(ANDROID_CONTROL_CAPTURE_INTENT, &intent, 1);
      default_requests_[idx]->Set(ANDROID_CONTROL_MODE, &control_mode, 1);
      default_requests_[idx]->Set(ANDROID_CONTROL_AE_MODE, &ae_mode, 1);
      default_requests_[idx]->Set(ANDROID_CONTROL_AE_TARGET_FPS_RANGE,
                                  ae_target_fps, ARRAY_SIZE(ae_target_fps));
      default_requests_[idx]->Set(ANDROID_CONTROL_AWB_MODE, &awb_mode, 1);
      default_requests_[idx]->Set(ANDROID_CONTROL_AF_MODE, &af_mode, 1);
      if (is_backward_compatible_) {
        default_requests_[idx]->Set(ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST,
                                    &post_raw_boost_, 1);
        if (vstab_available_) {
          default_requests_[idx]->Set(ANDROID_CONTROL_VIDEO_STABILIZATION_MODE,
                                      &vstab_mode, 1);
        }
        if (ae_lock_available_) {
          default_requests_[idx]->Set(ANDROID_CONTROL_AE_LOCK, &ae_lock, 1);
        }
        if (awb_lock_available_) {
          default_requests_[idx]->Set(ANDROID_CONTROL_AWB_LOCK, &awb_lock, 1);
        }
        if (scenes_supported_) {
          default_requests_[idx]->Set(ANDROID_CONTROL_SCENE_MODE, &scene_mode,
                                      1);
        }
        if (max_ae_regions_ > 0) {
          default_requests_[idx]->Set(ANDROID_CONTROL_AE_REGIONS, metering_area,
                                      ARRAY_SIZE(metering_area));
        }
        if (max_awb_regions_ > 0) {
          default_requests_[idx]->Set(ANDROID_CONTROL_AWB_REGIONS,
                                      metering_area, ARRAY_SIZE(metering_area));
        }
        if (max_af_regions_ > 0) {
          default_requests_[idx]->Set(ANDROID_CONTROL_AF_REGIONS, metering_area,
                                      ARRAY_SIZE(metering_area));
        }
        if (exposure_compensation_supported_) {
          default_requests_[idx]->Set(ANDROID_CONTROL_AE_EXPOSURE_COMPENSATION,
                                      &exposure_compensation_, 1);
        }
        if (zoom_ratio_supported_) {
          default_requests_[idx]->Set(ANDROID_CONTROL_ZOOM_RATIO, &zoom_ratio,
                                      1);
        }
        bool is_auto_antbanding_supported =
            available_antibanding_modes_.find(
                ANDROID_CONTROL_AE_ANTIBANDING_MODE_AUTO) !=
            available_antibanding_modes_.end();
        uint8_t antibanding_mode = is_auto_antbanding_supported
                                       ? ANDROID_CONTROL_AE_ANTIBANDING_MODE_AUTO
                                       : *available_antibanding_modes_.begin();
        default_requests_[idx]->Set(ANDROID_CONTROL_AE_ANTIBANDING_MODE,
                                    &antibanding_mode, 1);
        default_requests_[idx]->Set(ANDROID_CONTROL_EFFECT_MODE, &effect_mode,
                                    1);
        uint8_t ae_trigger = ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER_IDLE;
        default_requests_[idx]->Set(ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER,
                                    &ae_trigger, 1);
        uint8_t af_trigger = ANDROID_CONTROL_AF_TRIGGER_IDLE;
        default_requests_[idx]->Set(ANDROID_CONTROL_AF_TRIGGER, &af_trigger, 1);
      }
    }

    int32_t settings_override = ANDROID_CONTROL_SETTINGS_OVERRIDE_OFF;
    default_requests_[idx]->Set(ANDROID_CONTROL_SETTINGS_OVERRIDE,
                                &settings_override, 1);
  }

  return InitializeHotPixelDefaults();
}

status_t EmulatedCameraDeviceInfo::InitializeTonemapDefaults() {
  if (is_backward_compatible_) {
    camera_metadata_ro_entry_t entry;
    auto ret =
        static_metadata_->Get(ANDROID_TONEMAP_AVAILABLE_TONE_MAP_MODES, &entry);
    if (ret == OK) {
      available_tonemap_modes_.insert(entry.data.u8,
                                      entry.data.u8 + entry.count);
    } else {
      ALOGE("%s: No available tonemap modes!", __FUNCTION__);
      return BAD_VALUE;
    }

    if ((is_level_full_or_higher_) && (available_tonemap_modes_.size() < 3)) {
      ALOGE(
          "%s: Full and higher level cameras must support at least three or "
          "more tonemap modes",
          __FUNCTION__);
      return BAD_VALUE;
    }

    bool fast_mode_supported =
        available_tonemap_modes_.find(ANDROID_TONEMAP_MODE_FAST) !=
        available_tonemap_modes_.end();
    bool hq_mode_supported =
        available_tonemap_modes_.find(ANDROID_TONEMAP_MODE_HIGH_QUALITY) !=
        available_tonemap_modes_.end();
    uint8_t tonemap_mode = *available_tonemap_modes_.begin();
    for (size_t idx = 0; idx < kTemplateCount; idx++) {
      if (default_requests_[idx].get() == nullptr) {
        continue;
      }

      switch (static_cast<RequestTemplate>(idx)) {
        case RequestTemplate::kVideoRecord:  // Pass-through
        case RequestTemplate::kPreview:
          if (fast_mode_supported) {
            tonemap_mode = ANDROID_TONEMAP_MODE_FAST;
          }
          break;
        case RequestTemplate::kVideoSnapshot:  // Pass-through
        case RequestTemplate::kStillCapture:
          if (hq_mode_supported) {
            tonemap_mode = ANDROID_TONEMAP_MODE_HIGH_QUALITY;
          }
          break;
        default:
          // Noop
          break;
      }

      default_requests_[idx]->Set(ANDROID_TONEMAP_MODE, &tonemap_mode, 1);
      default_requests_[idx]->Set(
          ANDROID_TONEMAP_CURVE_RED, EmulatedSensor::kDefaultToneMapCurveRed,
          ARRAY_SIZE(EmulatedSensor::kDefaultToneMapCurveRed));
      default_requests_[idx]->Set(
          ANDROID_TONEMAP_CURVE_GREEN, EmulatedSensor::kDefaultToneMapCurveGreen,
          ARRAY_SIZE(EmulatedSensor::kDefaultToneMapCurveGreen));
      default_requests_[idx]->Set(
          ANDROID_TONEMAP_CURVE_BLUE, EmulatedSensor::kDefaultToneMapCurveBlue,
          ARRAY_SIZE(EmulatedSensor::kDefaultToneMapCurveBlue));
    }
  }

  return InitializeStatisticsDefaults();
}

status_t EmulatedCameraDeviceInfo::InitializeEdgeDefaults() {
  if (is_backward_compatible_) {
    camera_metadata_ro_entry_t entry;
    auto ret = static_metadata_->Get(ANDROID_EDGE_AVAILABLE_EDGE_MODES, &entry);
    if (ret == OK) {
      available_edge_modes_.insert(entry.data.u8, entry.data.u8 + entry.count);
    } else {
      ALOGE("%s: No available edge modes!", __FUNCTION__);
      return BAD_VALUE;
    }

    report_edge_mode_ =
        available_results_.find(ANDROID_EDGE_MODE) != available_results_.end();
    bool is_fast_mode_supported =
        available_edge_modes_.find(ANDROID_EDGE_MODE_FAST) !=
        available_edge_modes_.end();
    bool is_hq_mode_supported =
        available_edge_modes_.find(ANDROID_EDGE_MODE_HIGH_QUALITY) !=
        available_edge_modes_.end();
    bool is_zsl_mode_supported =
        available_edge_modes_.find(ANDROID_EDGE_MODE_ZERO_SHUTTER_LAG) !=
        available_edge_modes_.end();
    uint8_t edge_mode = *available_ae_modes_.begin();
    for (size_t idx = 0; idx < kTemplateCount; idx++) {
      if (default_requests_[idx].get() == nullptr) {
        continue;
      }

      switch (static_cast<RequestTemplate>(idx)) {
        case RequestTemplate::kVideoRecord:  // Pass-through
        case RequestTemplate::kPreview:
          if (is_fast_mode_supported) {
            edge_mode = ANDROID_EDGE_MODE_FAST;
          }
          break;
        case RequestTemplate::kVideoSnapshot:  // Pass-through
        case RequestTemplate::kStillCapture:
          if (is_hq_mode_supported) {
            edge_mode = ANDROID_EDGE_MODE_HIGH_QUALITY;
          }
          break;
        case RequestTemplate::kZeroShutterLag:
          if (is_zsl_mode_supported) {
            edge_mode = ANDROID_EDGE_MODE_ZERO_SHUTTER_LAG;
          }
          break;
        default:
          // Noop
          break;
      }

      default_requests_[idx]->Set(ANDROID_EDGE_MODE, &edge_mode, 1);
    }
  }

  return InitializeShadingDefaults();
}

status_t EmulatedCameraDeviceInfo::InitializeColorCorrectionDefaults() {
  camera_metadata_ro_entry_t entry;
  auto ret = static_metadata_->Get(
      ANDROID_COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES, &entry);
  if (ret == OK) {
    available_color_aberration_modes_.insert(entry.data.u8,
                                             entry.data.u8 + entry.count);
  } else if (supports_manual_post_processing_) {
    ALOGE(
        "%s: Devices capable of manual post-processing must support color "
        "abberation!",
        __FUNCTION__);
    return BAD_VALUE;
  }

  if (!available_color_aberration_modes_.empty()) {
    bool is_fast_mode_supported =
        available_color_aberration_modes_.find(
            ANDROID_COLOR_CORRECTION_ABERRATION_MODE_FAST) !=
        available_color_aberration_modes_.end();
    bool is_hq_mode_supported =
        available_color_aberration_modes_.find(
            ANDROID_COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY) !=
        available_color_aberration_modes_.end();
    uint8_t color_aberration = *available_color_aberration_modes_.begin();
    uint8_t color_correction_mode = ANDROID_COLOR_CORRECTION_MODE_FAST;
    for (size_t idx = 0; idx < kTemplateCount; idx++) {
      if (default_requests_[idx].get() == nullptr) {
        continue;
      }

      switch (static_cast<RequestTemplate>(idx)) {
        case RequestTemplate::kVideoRecord:  // Pass-through
        case RequestTemplate::kPreview:
          if (is_fast_mode_supported) {
            color_aberration = ANDROID_COLOR_CORRECTION_ABERRATION_MODE_FAST;
          }
          break;
        case RequestTemplate::kVideoSnapshot:  // Pass-through
        case RequestTemplate::kStillCapture:
          if (is_hq_mode_supported) {
            color_aberration =
                ANDROID_COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY;
          }
          break;
        default:
          // Noop
          break;
      }

      default_requests_[idx]->Set(ANDROID_COLOR_CORRECTION_ABERRATION_MODE,
                                  &color_aberration, 1);
      if (is_backward_compatible_) {
        default_requests_[idx]->Set(ANDROID_COLOR_CORRECTION_MODE,
                                    &color_correction_mode, 1);
        default_requests_[idx]->Set(
            ANDROID_COLOR_CORRECTION_TRANSFORM,
            EmulatedSensor::kDefaultColorTransform,
            ARRAY_SIZE(EmulatedSensor::kDefaultColorTransform));
        default_requests_[idx]->Set(
            ANDROID_COLOR_CORRECTION_GAINS,
            EmulatedSensor::kDefaultColorCorrectionGains,
            ARRAY_SIZE(EmulatedSensor::kDefaultColorCorrectionGains));
      }
    }
  }

  return InitializeSensorDefaults();
}

status_t EmulatedCameraDeviceInfo::InitializeScalerDefaults() {
  if (is_backward_compatible_) {
    camera_metadata_ro_entry_t entry;
    auto ret =
        static_metadata_->Get(ANDROID_SENSOR_INFO_ACTIVE_ARRAY_SIZE, &entry);
    if ((ret == OK) && (entry.count == 4)) {
      scaler_crop_region_default_[0] = entry.data.i32[0];
      scaler_crop_region_default_[1] = entry.data.i32[1];
      scaler_crop_region_default_[2] = entry.data.i32[2];
      scaler_crop_region_default_[3] = entry.data.i32[3];
    } else {
      ALOGE("%s: Sensor pixel array size is not available!", __FUNCTION__);
      return BAD_VALUE;
    }

    if (SupportsCapability(
            ANDROID_REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR)) {
      ret = static_metadata_->Get(
          ANDROID_SENSOR_INFO_ACTIVE_ARRAY_SIZE_MAXIMUM_RESOLUTION, &entry);
      if ((ret == OK) && (entry.count == 4)) {
        scaler_crop_region_max_resolution_[0] = entry.data.i32[0];
        scaler_crop_region_max_resolution_[1] = entry.data.i32[1];
        scaler_crop_region_max_resolution_[2] = entry.data.i32[2];
        scaler_crop_region_max_resolution_[3] = entry.data.i32[3];
      } else {
        ALOGE(
            "%s: Sensor pixel array size maximum resolution is not available!",
            __FUNCTION__);
        return BAD_VALUE;
      }
    }

    if (available_requests_.find(ANDROID_SCALER_CROP_REGION) ==
        available_requests_.end()) {
      ALOGE(
          "%s: Backward compatible devices must support scaler crop "
          "configuration!",
          __FUNCTION__);
      return BAD_VALUE;
    }
    if (available_results_.find(ANDROID_SCALER_CROP_REGION) ==
        available_results_.end()) {
      ALOGE("%s: Scaler crop must reported on backward compatible devices!",
            __FUNCTION__);
      return BAD_VALUE;
    }
    if (available_results_.find(
            ANDROID_LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_SENSOR_CROP_REGION) !=
        available_results_.end()) {
      report_active_sensor_crop_ = true;
    }
    ret = static_metadata_->Get(ANDROID_SCALER_AVAILABLE_ROTATE_AND_CROP_MODES,
                                &entry);
    if ((ret == OK) && (entry.count > 0)) {
      // Listing rotate and crop, so need to make sure it's consistently reported
      if (available_requests_.find(ANDROID_SCALER_ROTATE_AND_CROP) ==
          available_requests_.end()) {
        ALOGE(
            "%s: Rotate and crop must be listed in request keys if supported!",
            __FUNCTION__);
        return BAD_VALUE;
      }
      if (available_results_.find(ANDROID_SCALER_ROTATE_AND_CROP) ==
          available_results_.end()) {
        ALOGE("%s: Rotate and crop must be listed in result keys if supported!",
              __FUNCTION__);
        return BAD_VALUE;
      }
      if (available_characteristics_.find(
              ANDROID_SCALER_AVAILABLE_ROTATE_AND_CROP_MODES) ==
          available_characteristics_.end()) {
        ALOGE(
            "%s: Rotate and crop must be listed in characteristics keys if "
            "supported!",
            __FUNCTION__);
        return BAD_VALUE;
      }
      report_rotate_and_crop_ = true;
      for (size_t i = 0; i < entry.count; i++) {
        if (entry.data.u8[i] == ANDROID_SCALER_ROTATE_AND_CROP_AUTO) {
          rotate_and_crop_ = ANDROID_SCALER_ROTATE_AND_CROP_AUTO;
        }
        available_rotate_crop_modes_.insert(entry.data.u8[i]);
      }
    }

    for (size_t idx = 0; idx < kTemplateCount; idx++) {
      if (default_requests_[idx].get() == nullptr) {
        continue;
      }

      default_requests_[idx]->Set(ANDROID_SCALER_CROP_REGION,
                                  scaler_crop_region_default_,
                                  ARRAY_SIZE(scaler_crop_region_default_));
      if (report_rotate_and_crop_) {
        default_requests_[idx]->Set(ANDROID_SCALER_ROTATE_AND_CROP,
                                    &rotate_and_crop_, 1);
      }
    }
  }

  return InitializeControlDefaults();
}

status_t EmulatedCameraDeviceInfo::InitializeShadingDefaults() {
  camera_metadata_ro_entry_t entry;
  auto ret = static_metadata_->Get(ANDROID_SHADING_AVAILABLE_MODES, &entry);
  if (ret == OK) {
    available_shading_modes_.insert(entry.data.u8, entry.data.u8 + entry.count);
  } else {
    ALOGE("%s: No available lens shading modes!", __FUNCTION__);
    return BAD_VALUE;
  }

  if (supports_manual_post_processing_ &&
      (available_shading_modes_.size() < 2)) {
    ALOGE(
        "%s: Devices capable of manual post-processing need to support at "
        "least "
        "two"
        " lens shading modes!",
        __FUNCTION__);
    return BAD_VALUE;
  }

  bool is_fast_mode_supported =
      available_shading_modes_.find(ANDROID_SHADING_MODE_FAST) !=
      available_shading_modes_.end();
  bool is_hq_mode_supported =
      available_shading_modes_.find(ANDROID_SHADING_MODE_HIGH_QUALITY) !=
      available_shading_modes_.end();
  uint8_t shading_mode = *available_shading_modes_.begin();
  for (size_t idx = 0; idx < kTemplateCount; idx++) {
    if (default_requests_[idx].get() == nullptr) {
      continue;
    }

    switch (static_cast<RequestTemplate>(idx)) {
      case RequestTemplate::kVideoRecord:  // Pass-through
      case RequestTemplate::kPreview:
        if (is_fast_mode_supported) {
          shading_mode = ANDROID_SHADING_MODE_FAST;
        }
        break;
      case RequestTemplate::kVideoSnapshot:  // Pass-through
      case RequestTemplate::kStillCapture:
        if (is_hq_mode_supported) {
          shading_mode = ANDROID_SHADING_MODE_HIGH_QUALITY;
        }
        break;
      default:
        // Noop
        break;
    }

    default_requests_[idx]->Set(ANDROID_SHADING_MODE, &shading_mode, 1);
  }

  return InitializeNoiseReductionDefaults();
}

status_t EmulatedCameraDeviceInfo::InitializeNoiseReductionDefaults() {
  camera_metadata_ro_entry_t entry;
  auto ret = static_metadata_->Get(
      ANDROID_NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES, &entry);
  if (ret == OK) {
    available_noise_reduction_modes_.insert(entry.data.u8,
                                            entry.data.u8 + entry.count);
  } else {
    ALOGE("%s: No available noise reduction modes!", __FUNCTION__);
    return BAD_VALUE;
  }

  if ((is_level_full_or_higher_) &&
      (available_noise_reduction_modes_.size() < 2)) {
    ALOGE(
        "%s: Full and above device must support at least two noise reduction "
        "modes!",
        __FUNCTION__);
    return BAD_VALUE;
  }

  bool is_fast_mode_supported =
      available_noise_reduction_modes_.find(ANDROID_NOISE_REDUCTION_MODE_FAST) !=
      available_noise_reduction_modes_.end();
  bool is_hq_mode_supported = available_noise_reduction_modes_.find(
                                  ANDROID_NOISE_REDUCTION_MODE_HIGH_QUALITY) !=
                              available_noise_reduction_modes_.end();
  bool is_zsl_mode_supported =
      available_noise_reduction_modes_.find(
          ANDROID_NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG) !=
      available_noise_reduction_modes_.end();
  uint8_t noise_reduction_mode = *available_noise_reduction_modes_.begin();
  for (size_t idx = 0; idx < kTemplateCount; idx++) {
    if (default_requests_[idx].get() == nullptr) {
      continue;
    }

    switch (static_cast<RequestTemplate>(idx)) {
      case RequestTemplate::kVideoRecord:    // Pass-through
      case RequestTemplate::kVideoSnapshot:  // Pass-through
      case RequestTemplate::kPreview:
        if (is_fast_mode_supported) {
          noise_reduction_mode = ANDROID_NOISE_REDUCTION_MODE_FAST;
        }
        break;
      case RequestTemplate::kStillCapture:
        if (is_hq_mode_supported) {
          noise_reduction_mode = ANDROID_NOISE_REDUCTION_MODE_HIGH_QUALITY;
        }
        break;
      case RequestTemplate::kZeroShutterLag:
        if (is_zsl_mode_supported) {
          noise_reduction_mode = ANDROID_NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG;
        }
        break;
      default:
        // Noop
        break;
    }

    default_requests_[idx]->Set(ANDROID_NOISE_REDUCTION_MODE,
                                &noise_reduction_mode, 1);
  }

  return InitializeColorCorrectionDefaults();
}

status_t EmulatedCameraDeviceInfo::InitializeHotPixelDefaults() {
  camera_metadata_ro_entry_t entry;
  auto ret = static_metadata_->Get(ANDROID_HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES,
                                   &entry);
  if (ret == OK) {
    available_hot_pixel_modes_.insert(entry.data.u8,
                                      entry.data.u8 + entry.count);
  } else {
    ALOGE("%s: No available hotpixel modes!", __FUNCTION__);
    return BAD_VALUE;
  }

  if ((is_level_full_or_higher_) && (available_hot_pixel_modes_.size() < 2)) {
    ALOGE(
        "%s: Full and higher level cameras must support at least fast and hq "
        "hotpixel modes",
        __FUNCTION__);
    return BAD_VALUE;
  }

  bool fast_mode_supported =
      available_hot_pixel_modes_.find(ANDROID_HOT_PIXEL_MODE_FAST) !=
      available_hot_pixel_modes_.end();
  bool hq_mode_supported =
      available_hot_pixel_modes_.find(ANDROID_HOT_PIXEL_MODE_HIGH_QUALITY) !=
      available_hot_pixel_modes_.end();
  uint8_t hotpixel_mode = *available_hot_pixel_modes_.begin();
  for (size_t idx = 0; idx < kTemplateCount; idx++) {
    if (default_requests_[idx].get() == nullptr) {
      continue;
    }

    switch (static_cast<RequestTemplate>(idx)) {
      case RequestTemplate::kVideoRecord:  // Pass-through
      case RequestTemplate::kPreview:
        if (fast_mode_supported) {
          hotpixel_mode = ANDROID_HOT_PIXEL_MODE_FAST;
        }
        break;
      case RequestTemplate::kVideoSnapshot:  // Pass-through
      case RequestTemplate::kStillCapture:
        if (hq_mode_supported) {
          hotpixel_mode = ANDROID_HOT_PIXEL_MODE_HIGH_QUALITY;
        }
        break;
      default:
        // Noop
        break;
    }

    default_requests_[idx]->Set(ANDROID_HOT_PIXEL_MODE, &hotpixel_mode, 1);
  }

  return InitializeTonemapDefaults();
}

status_t EmulatedCameraDeviceInfo::InitializeFlashDefaults() {
  camera_metadata_ro_entry_t entry;
  auto ret = static_metadata_->Get(ANDROID_FLASH_INFO_AVAILABLE, &entry);
  if ((ret == OK) && (entry.count == 1)) {
    is_flash_supported_ = entry.data.u8[0];
  } else {
    ALOGE("%s: No available flash info!", __FUNCTION__);
    return BAD_VALUE;
  }

  if (is_flash_supported_) {
    flash_state_ = ANDROID_FLASH_STATE_READY;
  } else {
    flash_state_ = ANDROID_FLASH_STATE_UNAVAILABLE;
  }

  uint8_t flash_mode = ANDROID_FLASH_MODE_OFF;
  for (size_t idx = 0; idx < kTemplateCount; idx++) {
    if (default_requests_[idx].get() == nullptr) {
      continue;
    }

    default_requests_[idx]->Set(ANDROID_FLASH_MODE, &flash_mode, 1);
  }

  return InitializeScalerDefaults();
}

status_t EmulatedCameraDeviceInfo::InitializeLensDefaults() {
  camera_metadata_ro_entry_t entry;
  auto ret =
      static_metadata_->Get(ANDROID_LENS_INFO_MINIMUM_FOCUS_DISTANCE, &entry);
  if ((ret == OK) && (entry.count == 1)) {
    minimum_focus_distance_ = entry.data.f[0];
  } else {
    ALOGW("%s: No available minimum focus distance assuming fixed focus!",
          __FUNCTION__);
    minimum_focus_distance_ = .0f;
  }

  ret = static_metadata_->Get(ANDROID_LENS_INFO_AVAILABLE_APERTURES, &entry);
  if ((ret == OK) && (entry.count > 0)) {
    // TODO: add support for multiple apertures
    aperture_ = entry.data.f[0];
  } else {
    ALOGE("%s: No available aperture!", __FUNCTION__);
    return BAD_VALUE;
  }

  ret = static_metadata_->Get(ANDROID_LENS_INFO_AVAILABLE_FOCAL_LENGTHS, &entry);
  if ((ret == OK) && (entry.count > 0)) {
    focal_length_ = entry.data.f[0];
  } else {
    ALOGE("%s: No available focal length!", __FUNCTION__);
    return BAD_VALUE;
  }

  ret = static_metadata_->Get(ANDROID_LENS_INFO_SHADING_MAP_SIZE, &entry);
  if ((ret == OK) && (entry.count == 2)) {
    shading_map_size_[0] = entry.data.i32[0];
    shading_map_size_[1] = entry.data.i32[1];
  } else if (is_raw_capable_) {
    ALOGE("%s: No available shading map size!", __FUNCTION__);
    return BAD_VALUE;
  }

  ret = static_metadata_->Get(ANDROID_LENS_INFO_AVAILABLE_FILTER_DENSITIES,
                              &entry);
  if ((ret == OK) && (entry.count > 0)) {
    // TODO: add support for multiple filter densities
    filter_density_ = entry.data.f[0];
  } else {
    ALOGE("%s: No available filter density!", __FUNCTION__);
    return BAD_VALUE;
  }

  ret = static_metadata_->Get(ANDROID_LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION,
                              &entry);
  if ((ret == OK) && (entry.count > 0)) {
    // TODO: add support for multiple OIS modes
    available_ois_modes_.insert(entry.data.u8, entry.data.u8 + entry.count);
    if (available_ois_modes_.find(ANDROID_LENS_OPTICAL_STABILIZATION_MODE_OFF) ==
        available_ois_modes_.end()) {
      ALOGE("%s: OIS off mode not supported!", __FUNCTION__);
      return BAD_VALUE;
    }
  } else {
    ALOGE("%s: No available OIS modes!", __FUNCTION__);
    return BAD_VALUE;
  }

  ret = static_metadata_->Get(ANDROID_LENS_POSE_ROTATION, &entry);
  if ((ret == OK) && (entry.count == ARRAY_SIZE(pose_rotation_))) {
    memcpy(pose_rotation_, entry.data.f, sizeof(pose_rotation_));
  }
  ret = static_metadata_->Get(ANDROID_LENS_POSE_TRANSLATION, &entry);
  if ((ret == OK) && (entry.count == ARRAY_SIZE(pose_translation_))) {
    memcpy(pose_translation_, entry.data.f, sizeof(pose_translation_));
  }
  ret = static_metadata_->Get(ANDROID_LENS_INTRINSIC_CALIBRATION, &entry);
  if ((ret == OK) && (entry.count == ARRAY_SIZE(intrinsic_calibration_))) {
    memcpy(intrinsic_calibration_, entry.data.f, sizeof(intrinsic_calibration_));
  }

  ret = static_metadata_->Get(ANDROID_LENS_DISTORTION, &entry);
  if ((ret == OK) && (entry.count == ARRAY_SIZE(distortion_))) {
    memcpy(distortion_, entry.data.f, sizeof(distortion_));
  }

  report_focus_distance_ =
      available_results_.find(ANDROID_LENS_FOCUS_DISTANCE) !=
      available_results_.end();
  report_focus_range_ = available_results_.find(ANDROID_LENS_FOCUS_RANGE) !=
                        available_results_.end();
  report_filter_density_ =
      available_results_.find(ANDROID_LENS_FILTER_DENSITY) !=
      available_results_.end();
  report_ois_mode_ =
      available_results_.find(ANDROID_LENS_OPTICAL_STABILIZATION_MODE) !=
      available_results_.end();
  report_pose_rotation_ = available_results_.find(ANDROID_LENS_POSE_ROTATION) !=
                          available_results_.end();
  report_pose_translation_ =
      available_results_.find(ANDROID_LENS_POSE_TRANSLATION) !=
      available_results_.end();
  report_intrinsic_calibration_ =
      available_results_.find(ANDROID_LENS_INTRINSIC_CALIBRATION) !=
      available_results_.end();
  report_distortion_ = available_results_.find(ANDROID_LENS_DISTORTION) !=
                       available_results_.end();

  focus_distance_ = minimum_focus_distance_;
  for (size_t idx = 0; idx < kTemplateCount; idx++) {
    if (default_requests_[idx].get() == nullptr) {
      continue;
    }

    default_requests_[idx]->Set(ANDROID_LENS_APERTURE, &aperture_, 1);
    default_requests_[idx]->Set(ANDROID_LENS_FOCAL_LENGTH, &focal_length_, 1);
    default_requests_[idx]->Set(ANDROID_LENS_FOCUS_DISTANCE, &focus_distance_,
                                1);
    default_requests_[idx]->Set(ANDROID_LENS_OPTICAL_STABILIZATION_MODE,
                                &ois_mode_, 1);
  }

  return InitializeFlashDefaults();
}

status_t EmulatedCameraDeviceInfo::InitializeInfoDefaults() {
  camera_metadata_ro_entry_t entry;
  auto ret =
      static_metadata_->Get(ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL, &entry);
  if ((ret == OK) && (entry.count == 1)) {
    if (kSupportedHWLevels.find(entry.data.u8[0]) ==
        kSupportedCapabilites.end()) {
      ALOGE("%s: HW Level: %u not supported", __FUNCTION__, entry.data.u8[0]);
      return BAD_VALUE;
    }
  } else {
    ALOGE("%s: No available hardware level!", __FUNCTION__);
    return BAD_VALUE;
  }

  supported_hw_level_ = entry.data.u8[0];
  is_level_full_or_higher_ =
      (supported_hw_level_ == ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_FULL) ||
      (supported_hw_level_ == ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_3);

  return InitializeReprocessDefaults();
}

status_t EmulatedCameraDeviceInfo::InitializeReprocessDefaults() {
  if (supports_private_reprocessing_ || supports_yuv_reprocessing_ ||
      supports_remosaic_reprocessing_) {
    StreamConfigurationMap config_map(*static_metadata_);
    if (!config_map.SupportsReprocessing()) {
      ALOGE(
          "%s: Reprocess capability present but InputOutput format map is "
          "absent!",
          __FUNCTION__);
      return BAD_VALUE;
    }

    auto input_formats = config_map.GetInputFormats();
    for (const auto& input_format : input_formats) {
      auto output_formats =
          config_map.GetValidOutputFormatsForInput(input_format);
      for (const auto& output_format : output_formats) {
        if (!EmulatedSensor::IsReprocessPathSupported(
                EmulatedSensor::OverrideFormat(
                    input_format,
                    ANDROID_REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_STANDARD),
                EmulatedSensor::OverrideFormat(
                    output_format,
                    ANDROID_REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_STANDARD))) {
          ALOGE(
              "%s: Input format: 0x%x to output format: 0x%x reprocess is"
              " currently not supported!",
              __FUNCTION__, input_format, output_format);
          return BAD_VALUE;
        }
      }
    }
  }

  return InitializeLensDefaults();
}

status_t EmulatedCameraDeviceInfo::InitializeRequestDefaults() {
  camera_metadata_ro_entry_t entry;
  auto ret =
      static_metadata_->Get(ANDROID_REQUEST_AVAILABLE_CAPABILITIES, &entry);
  if ((ret == OK) && (entry.count > 0)) {
    for (size_t i = 0; i < entry.count; i++) {
      if (kSupportedCapabilites.find(entry.data.u8[i]) ==
          kSupportedCapabilites.end()) {
        ALOGE("%s: Capability: %u not supported", __FUNCTION__,
              entry.data.u8[i]);
        return BAD_VALUE;
      }
    }
  } else {
    ALOGE("%s: No available capabilities!", __FUNCTION__);
    return BAD_VALUE;
  }
  available_capabilities_.insert(entry.data.u8, entry.data.u8 + entry.count);

  ret = static_metadata_->Get(ANDROID_REQUEST_PIPELINE_MAX_DEPTH, &entry);
  if ((ret == OK) && (entry.count == 1)) {
    if (entry.data.u8[0] == 0) {
      ALOGE("%s: Maximum request pipeline depth must have a non zero value!",
            __FUNCTION__);
      return BAD_VALUE;
    }
  } else {
    ALOGE("%s: Maximum request pipeline depth absent!", __FUNCTION__);
    return BAD_VALUE;
  }
  max_pipeline_depth_ = entry.data.u8[0];

  ret = static_metadata_->Get(ANDROID_REQUEST_PARTIAL_RESULT_COUNT, &entry);
  if ((ret == OK) && (entry.count == 1)) {
    if (entry.data.i32[0] > 2) {
      ALOGW("%s: Partial result count greater than 2 not supported!",
            __FUNCTION__);
    }
    partial_result_count_ = entry.data.i32[0];
  }

  ret = static_metadata_->Get(ANDROID_REQUEST_AVAILABLE_CHARACTERISTICS_KEYS,
                              &entry);
  if ((ret != OK) || (entry.count == 0)) {
    ALOGE("%s: No available characteristic keys!", __FUNCTION__);
    return BAD_VALUE;
  }
  available_characteristics_.insert(entry.data.i32,
                                    entry.data.i32 + entry.count);

  ret = static_metadata_->Get(ANDROID_REQUEST_AVAILABLE_RESULT_KEYS, &entry);
  if ((ret != OK) || (entry.count == 0)) {
    ALOGE("%s: No available result keys!", __FUNCTION__);
    return BAD_VALUE;
  }
  available_results_.insert(entry.data.i32, entry.data.i32 + entry.count);

  ret = static_metadata_->Get(ANDROID_REQUEST_AVAILABLE_REQUEST_KEYS, &entry);
  if ((ret != OK) || (entry.count == 0)) {
    ALOGE("%s: No available request keys!", __FUNCTION__);
    return BAD_VALUE;
  }
  available_requests_.insert(entry.data.i32, entry.data.i32 + entry.count);

  supports_manual_sensor_ =
      SupportsCapability(ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR);
  supports_manual_post_processing_ = SupportsCapability(
      ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING);
  supports_private_reprocessing_ = SupportsCapability(
      ANDROID_REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING);
  supports_yuv_reprocessing_ = SupportsCapability(
      ANDROID_REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING);
  supports_remosaic_reprocessing_ = SupportsCapability(
      ANDROID_REQUEST_AVAILABLE_CAPABILITIES_REMOSAIC_REPROCESSING);
  is_backward_compatible_ = SupportsCapability(
      ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE);
  is_raw_capable_ =
      SupportsCapability(ANDROID_REQUEST_AVAILABLE_CAPABILITIES_RAW);
  supports_stream_use_case_ =
      SupportsCapability(ANDROID_REQUEST_AVAILABLE_CAPABILITIES_STREAM_USE_CASE);

  if (supports_manual_sensor_) {
    auto templateIdx = static_cast<size_t>(RequestTemplate::kManual);
    default_requests_[templateIdx] = HalCameraMetadata::Create(1, 10);
  }

  if (supports_stream_use_case_) {
    ret = static_metadata_->Get(ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES,
                                &entry);
    if (ret != OK) {
      ALOGE("%s: No available stream use cases!", __FUNCTION__);
      return BAD_VALUE;
    }
    for (int64_t useCase : kSupportedUseCases) {
      if (std::find(entry.data.i64, entry.data.i64 + entry.count, useCase) ==
          entry.data.i64 + entry.count) {
        ALOGE("%s: Mandatory stream use case %" PRId64 " not found!",
              __FUNCTION__, useCase);
        return BAD_VALUE;
      }
    }
  }

  for (size_t templateIdx = 0; templateIdx < kTemplateCount; templateIdx++) {
    switch (static_cast<RequestTemplate>(templateIdx)) {
      case RequestTemplate::kPreview:
      case RequestTemplate::kStillCapture:
      case RequestTemplate::kVideoRecord:
      case RequestTemplate::kVideoSnapshot:
        default_requests_[templateIdx] = HalCameraMetadata::Create(1, 10);
        break;
      default:
        // Noop
        break;
    }
  }

  if (supports_yuv_reprocessing_ || supports_private_reprocessing_) {
    auto templateIdx = static_cast<size_t>(RequestTemplate::kZeroShutterLag);
    default_requests_[templateIdx] = HalCameraMetadata::Create(1, 10);
  }

  return InitializeInfoDefaults();
}

bool EmulatedCameraDeviceInfo::SupportsCapability(uint8_t cap) {
  return available_capabilities_.find(cap) != available_capabilities_.end();
}

}  // namespace android
