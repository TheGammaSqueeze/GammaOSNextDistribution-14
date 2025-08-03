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

#define LOG_TAG "EmulatedRequestState"
#define ATRACE_TAG ATRACE_TAG_CAMERA

#include "EmulatedRequestState.h"

#include <inttypes.h>
#include <log/log.h>
#include <utils/HWLUtils.h>

#include "EmulatedRequestProcessor.h"

namespace android {

using google_camera_hal::HwlPipelineResult;

status_t EmulatedRequestState::Update3AMeteringRegion(
    uint32_t tag, const HalCameraMetadata& settings, int32_t* region /*out*/) {
  if ((region == nullptr) || ((tag != ANDROID_CONTROL_AE_REGIONS) &&
                              (tag != ANDROID_CONTROL_AF_REGIONS) &&
                              (tag != ANDROID_CONTROL_AWB_REGIONS))) {
    return BAD_VALUE;
  }

  camera_metadata_ro_entry_t entry;
  auto ret = settings.Get(ANDROID_SCALER_CROP_REGION, &entry);
  if ((ret == OK) && (entry.count > 0)) {
    int32_t crop_region[4];
    crop_region[0] = entry.data.i32[0];
    crop_region[1] = entry.data.i32[1];
    crop_region[2] = entry.data.i32[2] + crop_region[0];
    crop_region[3] = entry.data.i32[3] + crop_region[1];
    ret = settings.Get(tag, &entry);
    if ((ret == OK) && (entry.count > 0)) {
      const int32_t* a_region = entry.data.i32;
      // calculate the intersection of 3A and CROP regions
      if (a_region[0] < crop_region[2] && crop_region[0] < a_region[2] &&
          a_region[1] < crop_region[3] && crop_region[1] < a_region[3]) {
        region[0] = std::max(a_region[0], crop_region[0]);
        region[1] = std::max(a_region[1], crop_region[1]);
        region[2] = std::min(a_region[2], crop_region[2]);
        region[3] = std::min(a_region[3], crop_region[3]);
        region[4] = entry.data.i32[4];
      }
    }
  }

  return OK;
}

status_t EmulatedRequestState::CompensateAE() {
  auto& info = *device_info_;

  if (!info.exposure_compensation_supported_) {
    info.sensor_exposure_time_ = current_exposure_time_;
    return OK;
  }

  camera_metadata_ro_entry_t entry;
  auto ret =
      request_settings_->Get(ANDROID_CONTROL_AE_EXPOSURE_COMPENSATION, &entry);
  if ((ret == OK) && (entry.count == 1)) {
    info.exposure_compensation_ = entry.data.i32[0];
  } else {
    ALOGW("%s: AE compensation absent from request,  re-using previous value!",
          __FUNCTION__);
  }

  float ae_compensation = ::powf(
      2, info.exposure_compensation_ *
             ((static_cast<float>(info.exposure_compensation_step_.numerator) /
               info.exposure_compensation_step_.denominator)));

  info.sensor_exposure_time_ = GetClosestValue(
      static_cast<nsecs_t>(ae_compensation * current_exposure_time_),
      info.sensor_exposure_time_range_.first,
      info.sensor_exposure_time_range_.second);

  return OK;
}

status_t EmulatedRequestState::DoFakeAE() {
  auto& info = *device_info_;

  camera_metadata_ro_entry_t entry;
  auto ret = request_settings_->Get(ANDROID_CONTROL_AE_LOCK, &entry);
  if ((ret == OK) && (entry.count == 1)) {
    info.ae_lock_ = entry.data.u8[0];
  } else {
    info.ae_lock_ = ANDROID_CONTROL_AE_LOCK_OFF;
  }

  if (info.ae_lock_ == ANDROID_CONTROL_AE_LOCK_ON) {
    info.ae_state_ = ANDROID_CONTROL_AE_STATE_LOCKED;
    return OK;
  }

  EmulatedCameraDeviceInfo::FPSRange fps_range;
  ret = request_settings_->Get(ANDROID_CONTROL_AE_TARGET_FPS_RANGE, &entry);
  if ((ret == OK) && (entry.count == 2)) {
    for (const auto& it : info.available_fps_ranges_) {
      if ((it.min_fps == entry.data.i32[0]) &&
          (it.max_fps == entry.data.i32[1])) {
        fps_range = {entry.data.i32[0], entry.data.i32[1]};
        break;
      }
    }
    if (fps_range.max_fps == 0) {
      ALOGE("%s: Unsupported framerate range [%d, %d]", __FUNCTION__,
            entry.data.i32[0], entry.data.i32[1]);
      return BAD_VALUE;
    }
  } else {
    fps_range = *info.available_fps_ranges_.begin();
  }

  ret = request_settings_->Get(ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER, &entry);
  if ((ret == OK) && (entry.count == 1)) {
    info.ae_trigger_ = entry.data.u8[0];
  } else {
    info.ae_trigger_ = ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER_IDLE;
  }

  nsecs_t min_frame_duration =
      GetClosestValue(ms2ns(1000 / fps_range.max_fps),
                      EmulatedSensor::kSupportedFrameDurationRange[0],
                      info.sensor_max_frame_duration_);
  nsecs_t max_frame_duration =
      GetClosestValue(ms2ns(1000 / fps_range.min_fps),
                      EmulatedSensor::kSupportedFrameDurationRange[0],
                      info.sensor_max_frame_duration_);
  info.sensor_frame_duration_ = (max_frame_duration + min_frame_duration) / 2;

  // Face priority mode usually changes the AE algorithm behavior by
  // using the regions of interest associated with detected faces.
  // Try to emulate this behavior by slightly increasing the target exposure
  // time compared to normal operation.
  if (info.exposure_compensation_supported_) {
    float max_ae_compensation = ::powf(
        2, info.exposure_compensation_range_[1] *
               ((static_cast<float>(info.exposure_compensation_step_.numerator) /
                 info.exposure_compensation_step_.denominator)));
    ae_target_exposure_time_ = GetClosestValue(
        static_cast<nsecs_t>(info.sensor_frame_duration_ / max_ae_compensation),
        info.sensor_exposure_time_range_.first,
        info.sensor_exposure_time_range_.second);
  } else if (info.scene_mode_ == ANDROID_CONTROL_SCENE_MODE_FACE_PRIORITY) {
    ae_target_exposure_time_ = GetClosestValue(
        info.sensor_frame_duration_ / 4, info.sensor_exposure_time_range_.first,
        info.sensor_exposure_time_range_.second);
  } else {
    ae_target_exposure_time_ = GetClosestValue(
        info.sensor_frame_duration_ / 5, info.sensor_exposure_time_range_.first,
        info.sensor_exposure_time_range_.second);
  }

  if ((info.ae_trigger_ == ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER_START) ||
      (info.ae_state_ == ANDROID_CONTROL_AE_STATE_PRECAPTURE)) {
    if (info.ae_state_ != ANDROID_CONTROL_AE_STATE_PRECAPTURE) {
      ae_frame_counter_ = 0;
    }

    if (info.ae_trigger_ == ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL) {
      // Done with precapture
      ae_frame_counter_ = 0;
      info.ae_state_ = ANDROID_CONTROL_AE_STATE_CONVERGED;
      info.ae_trigger_ = ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL;
    } else if ((ae_frame_counter_ > kAEPrecaptureMinFrames) &&
               (abs(ae_target_exposure_time_ - current_exposure_time_) <
                ae_target_exposure_time_ / kAETargetThreshold)) {
      // Done with precapture
      ae_frame_counter_ = 0;
      info.ae_state_ = ANDROID_CONTROL_AE_STATE_CONVERGED;
      info.ae_trigger_ = ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER_IDLE;
    } else {
      // Converge some more
      current_exposure_time_ +=
          (ae_target_exposure_time_ - current_exposure_time_) *
          kExposureTrackRate;
      ae_frame_counter_++;
      info.ae_state_ = ANDROID_CONTROL_AE_STATE_PRECAPTURE;
    }
  } else {
    switch (info.ae_state_) {
      case ANDROID_CONTROL_AE_STATE_INACTIVE:
        info.ae_state_ = ANDROID_CONTROL_AE_STATE_SEARCHING;
        break;
      case ANDROID_CONTROL_AE_STATE_CONVERGED:
        ae_frame_counter_++;
        if (ae_frame_counter_ > kStableAeMaxFrames) {
          float exposure_step = ((double)rand_r(&rand_seed_) / RAND_MAX) *
                                    (kExposureWanderMax - kExposureWanderMin) +
                                kExposureWanderMin;
          ae_target_exposure_time_ =
              GetClosestValue(static_cast<nsecs_t>(ae_target_exposure_time_ *
                                                   std::pow(2, exposure_step)),
                              info.sensor_exposure_time_range_.first,
                              info.sensor_exposure_time_range_.second);
          info.ae_state_ = ANDROID_CONTROL_AE_STATE_SEARCHING;
        }
        break;
      case ANDROID_CONTROL_AE_STATE_SEARCHING:
        current_exposure_time_ +=
            (ae_target_exposure_time_ - current_exposure_time_) *
            kExposureTrackRate;
        if (abs(ae_target_exposure_time_ - current_exposure_time_) <
            ae_target_exposure_time_ / kAETargetThreshold) {
          // Close enough
          info.ae_state_ = ANDROID_CONTROL_AE_STATE_CONVERGED;
          ae_frame_counter_ = 0;
        }
        break;
      case ANDROID_CONTROL_AE_STATE_LOCKED:
        info.ae_state_ = ANDROID_CONTROL_AE_STATE_CONVERGED;
        ae_frame_counter_ = 0;
        break;
      default:
        ALOGE("%s: Unexpected AE state %d!", __FUNCTION__, info.ae_state_);
        return INVALID_OPERATION;
    }
  }

  return OK;
}

status_t EmulatedRequestState::ProcessAWB() {
  auto& info = *device_info_;

  if (info.max_awb_regions_ > 0) {
    auto ret =
        Update3AMeteringRegion(ANDROID_CONTROL_AWB_REGIONS, *request_settings_,
                               info.awb_metering_region_);
    if (ret != OK) {
      return ret;
    }
  }
  if (((info.awb_mode_ == ANDROID_CONTROL_AWB_MODE_OFF) ||
       (info.control_mode_ == ANDROID_CONTROL_MODE_OFF)) &&
      info.supports_manual_post_processing_) {
    // TODO: Add actual manual support
  } else if (info.is_backward_compatible_) {
    camera_metadata_ro_entry_t entry;
    auto ret = request_settings_->Get(ANDROID_CONTROL_AWB_LOCK, &entry);
    if ((ret == OK) && (entry.count == 1)) {
      info.awb_lock_ = entry.data.u8[0];
    } else {
      info.awb_lock_ = ANDROID_CONTROL_AWB_LOCK_OFF;
    }

    if (info.awb_lock_ == ANDROID_CONTROL_AWB_LOCK_ON) {
      info.awb_state_ = ANDROID_CONTROL_AWB_STATE_LOCKED;
    } else {
      info.awb_state_ = ANDROID_CONTROL_AWB_STATE_CONVERGED;
    }
  } else {
    // No color output support no need for AWB
  }

  return OK;
}

status_t EmulatedRequestState::ProcessAF() {
  auto& info = *device_info_;
  camera_metadata_ro_entry entry;

  if (info.max_af_regions_ > 0) {
    auto ret =
        Update3AMeteringRegion(ANDROID_CONTROL_AF_REGIONS, *request_settings_,
                               info.af_metering_region_);
    if (ret != OK) {
      return ret;
    }
  }
  if (info.af_mode_ == ANDROID_CONTROL_AF_MODE_OFF) {
    camera_metadata_ro_entry_t entry;
    auto ret = request_settings_->Get(ANDROID_LENS_FOCUS_DISTANCE, &entry);
    if ((ret == OK) && (entry.count == 1)) {
      if ((entry.data.f[0] >= 0.f) &&
          (entry.data.f[0] <= info.minimum_focus_distance_)) {
        info.focus_distance_ = entry.data.f[0];
      } else {
        ALOGE(
            "%s: Unsupported focus distance, It should be within "
            "[%5.2f, %5.2f]",
            __FUNCTION__, 0.f, info.minimum_focus_distance_);
      }
    }

    info.af_state_ = ANDROID_CONTROL_AF_STATE_INACTIVE;
    return OK;
  }

  auto ret = request_settings_->Get(ANDROID_CONTROL_AF_TRIGGER, &entry);
  if ((ret == OK) && (entry.count == 1)) {
    info.af_trigger_ = entry.data.u8[0];
  } else {
    info.af_trigger_ = ANDROID_CONTROL_AF_TRIGGER_IDLE;
  }

  /**
   * Simulate AF triggers. Transition at most 1 state per frame.
   * - Focusing always succeeds (goes into locked, or PASSIVE_SCAN).
   */

  bool af_trigger_start = false;
  switch (info.af_trigger_) {
    case ANDROID_CONTROL_AF_TRIGGER_IDLE:
      break;
    case ANDROID_CONTROL_AF_TRIGGER_START:
      af_trigger_start = true;
      break;
    case ANDROID_CONTROL_AF_TRIGGER_CANCEL:
      // Cancel trigger always transitions into INACTIVE
      info.af_state_ = ANDROID_CONTROL_AF_STATE_INACTIVE;

      // Stay in 'inactive' until at least next frame
      return OK;
    default:
      ALOGE("%s: Unknown AF trigger value", __FUNCTION__);
      return BAD_VALUE;
  }

  // If we get down here, we're either in ANDROID_CONTROL_AF_MODE_AUTO,
  // ANDROID_CONTROL_AF_MODE_MACRO, ANDROID_CONTROL_AF_MODE_CONTINUOUS_VIDEO,
  // ANDROID_CONTROL_AF_MODE_CONTINUOUS_PICTURE and no other modes like
  // ANDROID_CONTROL_AF_MODE_OFF or ANDROID_CONTROL_AF_MODE_EDOF
  switch (info.af_state_) {
    case ANDROID_CONTROL_AF_STATE_INACTIVE:
      if (af_trigger_start) {
        switch (info.af_mode_) {
          case ANDROID_CONTROL_AF_MODE_AUTO:
            // fall-through
          case ANDROID_CONTROL_AF_MODE_MACRO:
            info.af_state_ = ANDROID_CONTROL_AF_STATE_ACTIVE_SCAN;
            break;
          case ANDROID_CONTROL_AF_MODE_CONTINUOUS_VIDEO:
            // fall-through
          case ANDROID_CONTROL_AF_MODE_CONTINUOUS_PICTURE:
            info.af_state_ = ANDROID_CONTROL_AF_STATE_NOT_FOCUSED_LOCKED;
            break;
        }
      } else {
        // At least one frame stays in INACTIVE
        if (!af_mode_changed_) {
          switch (info.af_mode_) {
            case ANDROID_CONTROL_AF_MODE_CONTINUOUS_VIDEO:
              // fall-through
            case ANDROID_CONTROL_AF_MODE_CONTINUOUS_PICTURE:
              info.af_state_ = ANDROID_CONTROL_AF_STATE_PASSIVE_SCAN;
              break;
          }
        }
      }
      break;
    case ANDROID_CONTROL_AF_STATE_PASSIVE_SCAN:
      /**
       * When the AF trigger is activated, the algorithm should finish
       * its PASSIVE_SCAN if active, and then transition into AF_FOCUSED
       * or AF_NOT_FOCUSED as appropriate
       */
      if (af_trigger_start) {
        // Randomly transition to focused or not focused
        if (rand_r(&rand_seed_) % 3) {
          info.af_state_ = ANDROID_CONTROL_AF_STATE_FOCUSED_LOCKED;
        } else {
          info.af_state_ = ANDROID_CONTROL_AF_STATE_NOT_FOCUSED_LOCKED;
        }
      }
      /**
       * When the AF trigger is not involved, the AF algorithm should
       * start in INACTIVE state, and then transition into PASSIVE_SCAN
       * and PASSIVE_FOCUSED states
       */
      else {
        // Randomly transition to passive focus
        if (rand_r(&rand_seed_) % 3 == 0) {
          info.af_state_ = ANDROID_CONTROL_AF_STATE_PASSIVE_FOCUSED;
        }
      }

      break;
    case ANDROID_CONTROL_AF_STATE_PASSIVE_FOCUSED:
      if (af_trigger_start) {
        // Randomly transition to focused or not focused
        if (rand_r(&rand_seed_) % 3) {
          info.af_state_ = ANDROID_CONTROL_AF_STATE_FOCUSED_LOCKED;
        } else {
          info.af_state_ = ANDROID_CONTROL_AF_STATE_NOT_FOCUSED_LOCKED;
        }
      }
      // TODO: initiate passive scan (PASSIVE_SCAN)
      break;
    case ANDROID_CONTROL_AF_STATE_ACTIVE_SCAN:
      // Simulate AF sweep completing instantaneously

      // Randomly transition to focused or not focused
      if (rand_r(&rand_seed_) % 3) {
        info.af_state_ = ANDROID_CONTROL_AF_STATE_FOCUSED_LOCKED;
      } else {
        info.af_state_ = ANDROID_CONTROL_AF_STATE_NOT_FOCUSED_LOCKED;
      }
      break;
    case ANDROID_CONTROL_AF_STATE_FOCUSED_LOCKED:
      if (af_trigger_start) {
        switch (info.af_mode_) {
          case ANDROID_CONTROL_AF_MODE_AUTO:
            // fall-through
          case ANDROID_CONTROL_AF_MODE_MACRO:
            info.af_state_ = ANDROID_CONTROL_AF_STATE_ACTIVE_SCAN;
            break;
          case ANDROID_CONTROL_AF_MODE_CONTINUOUS_VIDEO:
            // fall-through
          case ANDROID_CONTROL_AF_MODE_CONTINUOUS_PICTURE:
            // continuous autofocus => trigger start has no effect
            break;
        }
      }
      break;
    case ANDROID_CONTROL_AF_STATE_NOT_FOCUSED_LOCKED:
      if (af_trigger_start) {
        switch (info.af_mode_) {
          case ANDROID_CONTROL_AF_MODE_AUTO:
            // fall-through
          case ANDROID_CONTROL_AF_MODE_MACRO:
            info.af_state_ = ANDROID_CONTROL_AF_STATE_ACTIVE_SCAN;
            break;
          case ANDROID_CONTROL_AF_MODE_CONTINUOUS_VIDEO:
            // fall-through
          case ANDROID_CONTROL_AF_MODE_CONTINUOUS_PICTURE:
            // continuous autofocus => trigger start has no effect
            break;
        }
      }
      break;
    default:
      ALOGE("%s: Bad af state %d", __FUNCTION__, info.af_state_);
  }

  return OK;
}

status_t EmulatedRequestState::ProcessAE() {
  auto& info = *device_info_;
  if (info.max_ae_regions_ > 0) {
    auto ret =
        Update3AMeteringRegion(ANDROID_CONTROL_AE_REGIONS, *request_settings_,
                               info.ae_metering_region_);
    if (ret != OK) {
      ALOGE("%s: Failed updating the 3A metering regions: %d, (%s)",
            __FUNCTION__, ret, strerror(-ret));
    }
  }

  camera_metadata_ro_entry_t entry;
  bool auto_ae_mode = false;
  bool auto_ae_flash_mode = false;
  switch (info.ae_mode_) {
    case ANDROID_CONTROL_AE_MODE_ON_AUTO_FLASH:
    case ANDROID_CONTROL_AE_MODE_ON_ALWAYS_FLASH:
    case ANDROID_CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE:
      auto_ae_flash_mode = true;
      [[fallthrough]];
    case ANDROID_CONTROL_AE_MODE_ON:
      auto_ae_mode = true;
  };
  if (((info.ae_mode_ == ANDROID_CONTROL_AE_MODE_OFF) ||
       (info.control_mode_ == ANDROID_CONTROL_MODE_OFF)) &&
      info.supports_manual_sensor_) {
    auto ret = request_settings_->Get(ANDROID_SENSOR_EXPOSURE_TIME, &entry);
    if ((ret == OK) && (entry.count == 1)) {
      if ((entry.data.i64[0] >= info.sensor_exposure_time_range_.first) &&
          (entry.data.i64[0] <= info.sensor_exposure_time_range_.second)) {
        info.sensor_exposure_time_ = entry.data.i64[0];
      } else {
        ALOGE("%s: Sensor exposure time %" PRId64
              " not within supported range[%" PRId64 ", %" PRId64 "]",
              __FUNCTION__, entry.data.i64[0],
              info.sensor_exposure_time_range_.first,
              info.sensor_exposure_time_range_.second);
        // Use last valid value
      }
    }

    ret = request_settings_->Get(ANDROID_SENSOR_FRAME_DURATION, &entry);
    if ((ret == OK) && (entry.count == 1)) {
      if ((entry.data.i64[0] >=
           EmulatedSensor::kSupportedFrameDurationRange[0]) &&
          (entry.data.i64[0] <= info.sensor_max_frame_duration_)) {
        info.sensor_frame_duration_ = entry.data.i64[0];
      } else {
        ALOGE("%s: Sensor frame duration %" PRId64
              " not within supported range[%" PRId64 ", %" PRId64 "]",
              __FUNCTION__, entry.data.i64[0],
              EmulatedSensor::kSupportedFrameDurationRange[0],
              info.sensor_max_frame_duration_);
        // Use last valid value
      }
    }

    if (info.sensor_frame_duration_ < info.sensor_exposure_time_) {
      info.sensor_frame_duration_ = info.sensor_exposure_time_;
    }

    ret = request_settings_->Get(ANDROID_SENSOR_SENSITIVITY, &entry);
    if ((ret == OK) && (entry.count == 1)) {
      if ((entry.data.i32[0] >= info.sensor_sensitivity_range_.first) &&
          (entry.data.i32[0] <= info.sensor_sensitivity_range_.second)) {
        info.sensor_sensitivity_ = entry.data.i32[0];
      } else {
        ALOGE("%s: Sensor sensitivity %d not within supported range[%d, %d]",
              __FUNCTION__, entry.data.i32[0],
              info.sensor_sensitivity_range_.first,
              info.sensor_sensitivity_range_.second);
        // Use last valid value
      }
    }
    info.ae_state_ = ANDROID_CONTROL_AE_STATE_INACTIVE;
  } else if (info.is_backward_compatible_ && auto_ae_mode) {
    auto ret = DoFakeAE();
    if (ret != OK) {
      ALOGE("%s: Failed fake AE: %d, (%s)", __FUNCTION__, ret, strerror(-ret));
    }

    // Do AE compensation on the results of the AE
    ret = CompensateAE();
    if (ret != OK) {
      ALOGE("%s: Failed during AE compensation: %d, (%s)", __FUNCTION__, ret,
            strerror(-ret));
    }
  } else {
    ALOGI(
        "%s: No emulation for current AE mode using previous sensor settings!",
        __FUNCTION__);
  }

  if (info.is_flash_supported_) {
    info.flash_state_ = ANDROID_FLASH_STATE_READY;
    // Flash fires only if the request manually enables it (SINGLE/TORCH)
    // and the appropriate AE mode is set or during still capture with auto
    // flash AE modes.
    bool manual_flash_mode = false;
    auto ret = request_settings_->Get(ANDROID_FLASH_MODE, &entry);
    if ((ret == OK) && (entry.count == 1)) {
      if ((entry.data.u8[0] == ANDROID_FLASH_MODE_SINGLE) ||
          (entry.data.u8[0] == ANDROID_FLASH_MODE_TORCH)) {
        manual_flash_mode = true;
      }
    }
    if (manual_flash_mode && !auto_ae_flash_mode) {
      info.flash_state_ = ANDROID_FLASH_STATE_FIRED;
    } else {
      bool is_still_capture = false;
      ret = request_settings_->Get(ANDROID_CONTROL_CAPTURE_INTENT, &entry);
      if ((ret == OK) && (entry.count == 1)) {
        if (entry.data.u8[0] == ANDROID_CONTROL_CAPTURE_INTENT_STILL_CAPTURE) {
          is_still_capture = true;
        }
      }
      if (is_still_capture && auto_ae_flash_mode) {
        info.flash_state_ = ANDROID_FLASH_STATE_FIRED;
      }
    }
  } else {
    info.flash_state_ = ANDROID_FLASH_STATE_UNAVAILABLE;
  }

  return OK;
}

status_t EmulatedRequestState::InitializeSensorSettings(
    std::unique_ptr<HalCameraMetadata> request_settings,
    uint32_t override_frame_number,
    EmulatedSensor::SensorSettings* sensor_settings /*out*/) {
  auto& info = *device_info_;
  if ((sensor_settings == nullptr) || (request_settings.get() == nullptr)) {
    return BAD_VALUE;
  }

  std::lock_guard<std::mutex> lock(request_state_mutex_);
  request_settings_ = std::move(request_settings);
  camera_metadata_ro_entry_t entry;
  auto ret = request_settings_->Get(ANDROID_CONTROL_MODE, &entry);
  if ((ret == OK) && (entry.count == 1)) {
    if (info.available_control_modes_.find(entry.data.u8[0]) !=
        info.available_control_modes_.end()) {
      info.control_mode_ = entry.data.u8[0];
    } else {
      ALOGE("%s: Unsupported control mode!", __FUNCTION__);
      return BAD_VALUE;
    }
  }

  ret = request_settings_->Get(ANDROID_SENSOR_PIXEL_MODE, &entry);
  if ((ret == OK) && (entry.count == 1)) {
    if (info.available_sensor_pixel_modes_.find(entry.data.u8[0]) !=
        info.available_sensor_pixel_modes_.end()) {
      info.sensor_pixel_mode_ = entry.data.u8[0];
    } else {
      ALOGE("%s: Unsupported control sensor pixel  mode!", __FUNCTION__);
      return BAD_VALUE;
    }
  }

  ret = request_settings_->Get(ANDROID_CONTROL_SCENE_MODE, &entry);
  if ((ret == OK) && (entry.count == 1)) {
    // Disabled scene is not expected to be among the available scene list
    if ((entry.data.u8[0] == ANDROID_CONTROL_SCENE_MODE_DISABLED) ||
        (info.available_scenes_.find(entry.data.u8[0]) !=
         info.available_scenes_.end())) {
      info.scene_mode_ = entry.data.u8[0];
    } else {
      ALOGE("%s: Unsupported scene mode!", __FUNCTION__);
      return BAD_VALUE;
    }
  }

  float min_zoom = info.min_zoom_, max_zoom = info.max_zoom_;
  ret = request_settings_->Get(ANDROID_CONTROL_EXTENDED_SCENE_MODE, &entry);
  if ((ret == OK) && (entry.count == 1)) {
    bool extended_scene_mode_valid = false;
    for (const auto& cap : info.available_extended_scene_mode_caps_) {
      if (cap.mode == entry.data.u8[0]) {
        info.extended_scene_mode_ = entry.data.u8[0];
        min_zoom = cap.min_zoom;
        max_zoom = cap.max_zoom;
        extended_scene_mode_valid = true;
        break;
      }
    }
    if (!extended_scene_mode_valid) {
      ALOGE("%s: Unsupported extended scene mode %d!", __FUNCTION__,
            entry.data.u8[0]);
      return BAD_VALUE;
    }
    if (info.extended_scene_mode_ !=
        ANDROID_CONTROL_EXTENDED_SCENE_MODE_DISABLED) {
      info.scene_mode_ = ANDROID_CONTROL_SCENE_MODE_FACE_PRIORITY;
    }
  }

  // Check zoom ratio range and override to supported range
  ret = request_settings_->Get(ANDROID_CONTROL_ZOOM_RATIO, &entry);
  if ((ret == OK) && (entry.count == 1)) {
    info.zoom_ratio_ = std::min(std::max(entry.data.f[0], min_zoom), max_zoom);
  }

  // Check settings override
  ret = request_settings_->Get(ANDROID_CONTROL_SETTINGS_OVERRIDE, &entry);
  if ((ret == OK) && (entry.count == 1)) {
    info.settings_override_ = entry.data.i32[0];
  }

  // Store settings override frame number
  if (override_frame_number != 0) {
    settings_overriding_frame_number_ = override_frame_number;
  }

  // Check rotate_and_crop setting
  ret = request_settings_->Get(ANDROID_SCALER_ROTATE_AND_CROP, &entry);
  if ((ret == OK) && (entry.count == 1)) {
    if (info.available_rotate_crop_modes_.find(entry.data.u8[0]) !=
        info.available_rotate_crop_modes_.end()) {
      info.rotate_and_crop_ = entry.data.u8[0];
    } else {
      ALOGE("%s: Unsupported rotate and crop mode: %u", __FUNCTION__, entry.data.u8[0]);
      return BAD_VALUE;
    }
  }

  // Check video stabilization parameter
  uint8_t vstab_mode = ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_OFF;
  ret = request_settings_->Get(ANDROID_CONTROL_VIDEO_STABILIZATION_MODE, &entry);
  if ((ret == OK) && (entry.count == 1)) {
    if (info.available_vstab_modes_.find(entry.data.u8[0]) !=
        info.available_vstab_modes_.end()) {
      vstab_mode = entry.data.u8[0];
    } else {
      ALOGE("%s: Unsupported video stabilization mode: %u! Video stabilization will be disabled!",
            __FUNCTION__, entry.data.u8[0]);
    }
  }

  // Check autoframing
  ret = request_settings_->Get(ANDROID_CONTROL_AUTOFRAMING, &entry);
  if ((ret == OK) && (entry.count == 1)) {
    info.autoframing_ = entry.data.i32[0];
    if (info.autoframing_ == ANDROID_CONTROL_AUTOFRAMING_ON) {
      // Set zoom_ratio to be a hard-coded value to test autoframing.
      info.zoom_ratio_ = 1.7f;
      vstab_mode = ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_OFF;
    }
  }

  // Check manual flash strength level
  ret = request_settings_->Get(ANDROID_FLASH_STRENGTH_LEVEL, &entry);
  if ((ret == OK) && (entry.count == 1)) {
    info.flash_strength_level_ = entry.data.i32[0];
    if (ANDROID_FLASH_SINGLE_STRENGTH_MAX_LEVEL > 1 &&
        ANDROID_FLASH_TORCH_STRENGTH_MAX_LEVEL > 1 && info.is_flash_supported_) {
      ALOGI("%s: Device supports manual flash strength control", __FUNCTION__);
      info.flash_strength_level_ = entry.data.i32[0];
    } else {
      ALOGI("%s: Device does not support manual flash strength control",
            __FUNCTION__);
      return BAD_VALUE;
    }
  }

  // Check video stabilization parameter
  uint8_t edge_mode = ANDROID_EDGE_MODE_OFF;
  ret = request_settings_->Get(ANDROID_EDGE_MODE, &entry);
  if ((ret == OK) && (entry.count == 1)) {
    if (info.available_edge_modes_.find(entry.data.u8[0]) !=
        info.available_edge_modes_.end()) {
      edge_mode = entry.data.u8[0];
    } else {
      ALOGE("%s: Unsupported edge mode: %u", __FUNCTION__, entry.data.u8[0]);
      return BAD_VALUE;
    }
  }

  // Check test pattern parameter
  uint8_t test_pattern_mode = ANDROID_SENSOR_TEST_PATTERN_MODE_OFF;
  ret = request_settings_->Get(ANDROID_SENSOR_TEST_PATTERN_MODE, &entry);
  if ((ret == OK) && (entry.count == 1)) {
    if (info.available_test_pattern_modes_.find(entry.data.u8[0]) !=
        info.available_test_pattern_modes_.end()) {
      test_pattern_mode = entry.data.u8[0];
    } else {
      ALOGE("%s: Unsupported test pattern mode: %u", __FUNCTION__,
            entry.data.u8[0]);
      return BAD_VALUE;
    }
  }
  uint32_t test_pattern_data[4] = {0, 0, 0, 0};
  if (test_pattern_mode == ANDROID_SENSOR_TEST_PATTERN_MODE_SOLID_COLOR) {
    ret = request_settings_->Get(ANDROID_SENSOR_TEST_PATTERN_DATA, &entry);
    if ((ret == OK) && (entry.count == 4)) {
      // 'Convert' from i32 to u32 here
      memcpy(test_pattern_data, entry.data.i32, sizeof(test_pattern_data));
    }
  }
  // BLACK is just SOLID_COLOR with all-zero data
  if (test_pattern_mode == ANDROID_SENSOR_TEST_PATTERN_MODE_BLACK) {
    test_pattern_mode = ANDROID_SENSOR_TEST_PATTERN_MODE_SOLID_COLOR;
  }

  // 3A modes are active in case the scene is disabled or set to face priority
  // or the control mode is not using scenes
  if ((info.scene_mode_ == ANDROID_CONTROL_SCENE_MODE_DISABLED) ||
      (info.scene_mode_ == ANDROID_CONTROL_SCENE_MODE_FACE_PRIORITY) ||
      (info.control_mode_ != ANDROID_CONTROL_MODE_USE_SCENE_MODE)) {
    ret = request_settings_->Get(ANDROID_CONTROL_AE_MODE, &entry);
    if ((ret == OK) && (entry.count == 1)) {
      if (info.available_ae_modes_.find(entry.data.u8[0]) !=
          info.available_ae_modes_.end()) {
        info.ae_mode_ = entry.data.u8[0];
      } else {
        ALOGE("%s: Unsupported AE mode! Using last valid mode!", __FUNCTION__);
      }
    }

    ret = request_settings_->Get(ANDROID_CONTROL_AWB_MODE, &entry);
    if ((ret == OK) && (entry.count == 1)) {
      if (info.available_awb_modes_.find(entry.data.u8[0]) !=
          info.available_awb_modes_.end()) {
        info.awb_mode_ = entry.data.u8[0];
      } else {
        ALOGE("%s: Unsupported AWB mode! Using last valid mode!", __FUNCTION__);
      }
    }

    ret = request_settings_->Get(ANDROID_CONTROL_AF_MODE, &entry);
    if ((ret == OK) && (entry.count == 1)) {
      if (info.available_af_modes_.find(entry.data.u8[0]) !=
          info.available_af_modes_.end()) {
        af_mode_changed_ = info.af_mode_ != entry.data.u8[0];
        info.af_mode_ = entry.data.u8[0];
      } else {
        ALOGE("%s: Unsupported AF mode! Using last valid mode!", __FUNCTION__);
      }
    }
  } else {
    auto it = info.scene_overrides_.find(info.scene_mode_);
    if (it != info.scene_overrides_.end()) {
      info.ae_mode_ = it->second.ae_mode;
      info.awb_mode_ = it->second.awb_mode;
      af_mode_changed_ = info.af_mode_ != entry.data.u8[0];
      info.af_mode_ = it->second.af_mode;
    } else {
      ALOGW(
          "%s: Current scene has no overrides! Using the currently active 3A "
          "modes!",
          __FUNCTION__);
    }
  }

  ret = ProcessAE();
  if (ret != OK) {
    return ret;
  }

  ret = ProcessAWB();
  if (ret != OK) {
    return ret;
  }

  ret = ProcessAF();
  if (ret != OK) {
    return ret;
  }

  ret = request_settings_->Get(ANDROID_STATISTICS_LENS_SHADING_MAP_MODE, &entry);
  if ((ret == OK) && (entry.count == 1)) {
    if (info.available_lens_shading_map_modes_.find(entry.data.u8[0]) !=
        info.available_lens_shading_map_modes_.end()) {
      sensor_settings->lens_shading_map_mode = entry.data.u8[0];
    } else {
      ALOGE("%s: Unsupported lens shading map mode!", __FUNCTION__);
    }
  }

  ret = info.static_metadata_->Get(ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE, &entry);
  if ((ret == OK) && (entry.count == 1)) {
    if (entry.data.u8[0] == ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME) {
      info.timestamp_source_ = ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME;
    } else if (entry.data.u8[0] != ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN) {
      ALOGE("%s: Unsupported timestamp source", __FUNCTION__);
    }
  }

  sensor_settings->exposure_time = info.sensor_exposure_time_;
  sensor_settings->frame_duration = info.sensor_frame_duration_;
  sensor_settings->gain = info.sensor_sensitivity_;
  sensor_settings->report_neutral_color_point = info.report_neutral_color_point_;
  sensor_settings->report_green_split = info.report_green_split_;
  sensor_settings->report_noise_profile = info.report_noise_profile_;
  sensor_settings->zoom_ratio = info.zoom_ratio_;
  sensor_settings->report_rotate_and_crop = info.report_rotate_and_crop_;
  sensor_settings->rotate_and_crop = info.rotate_and_crop_;
  sensor_settings->report_video_stab = !info.available_vstab_modes_.empty();
  sensor_settings->video_stab = vstab_mode;
  sensor_settings->report_edge_mode = info.report_edge_mode_;
  sensor_settings->edge_mode = edge_mode;
  sensor_settings->sensor_pixel_mode = info.sensor_pixel_mode_;
  sensor_settings->test_pattern_mode = test_pattern_mode;
  sensor_settings->timestamp_source = info.timestamp_source_;
  memcpy(sensor_settings->test_pattern_data, test_pattern_data,
         sizeof(sensor_settings->test_pattern_data));

  return OK;
}

uint32_t EmulatedRequestState::GetPartialResultCount(bool is_partial_result) {
  uint32_t res = 0;
  auto& info = *device_info_;

  if (is_partial_result) {
    res = 1;
  } else {
    res = info.partial_result_count_ ? info.partial_result_count_ : 1;
  }

  return res;
}

std::unique_ptr<HwlPipelineResult> EmulatedRequestState::InitializePartialResult(
    uint32_t pipeline_id, uint32_t frame_number) {
  auto& info = *device_info_;
  std::lock_guard<std::mutex> lock(request_state_mutex_);
  auto result = std::make_unique<HwlPipelineResult>();

  if (info.partial_result_count_ > 1) {
    result->camera_id = camera_id_;
    result->pipeline_id = pipeline_id;
    result->frame_number = frame_number;
    result->result_metadata = HalCameraMetadata::Create(0, 0);
    result->partial_result = GetPartialResultCount(/*is partial result*/ true);
  }

  return result;
}

std::unique_ptr<HwlPipelineResult> EmulatedRequestState::InitializeResult(
    uint32_t pipeline_id, uint32_t frame_number) {
  auto& info = *device_info_;
  std::lock_guard<std::mutex> lock(request_state_mutex_);
  auto result = std::make_unique<HwlPipelineResult>();
  result->camera_id = camera_id_;
  result->pipeline_id = pipeline_id;
  result->frame_number = frame_number;
  result->result_metadata = HalCameraMetadata::Clone(request_settings_.get());
  result->partial_result = GetPartialResultCount(/*is partial result*/ false);

  // Results supported on all emulated devices
  result->result_metadata->Set(ANDROID_REQUEST_PIPELINE_DEPTH,
                               &info.max_pipeline_depth_, 1);
  result->result_metadata->Set(ANDROID_CONTROL_MODE, &info.control_mode_, 1);
  result->result_metadata->Set(ANDROID_SENSOR_PIXEL_MODE,
                               &info.sensor_pixel_mode_, 1);

  result->result_metadata->Set(ANDROID_CONTROL_AF_MODE, &info.af_mode_, 1);
  result->result_metadata->Set(ANDROID_CONTROL_AF_STATE, &info.af_state_, 1);
  result->result_metadata->Set(ANDROID_CONTROL_AWB_MODE, &info.awb_mode_, 1);
  result->result_metadata->Set(ANDROID_CONTROL_AWB_STATE, &info.awb_state_, 1);
  result->result_metadata->Set(ANDROID_CONTROL_AE_MODE, &info.ae_mode_, 1);
  result->result_metadata->Set(ANDROID_CONTROL_AE_STATE, &info.ae_state_, 1);
  // If the overriding frame number isn't larger than current frame number,
  // use 0.
  int32_t settings_override = info.settings_override_;
  uint32_t overriding_frame_number = settings_overriding_frame_number_;
  if (overriding_frame_number <= frame_number) {
    overriding_frame_number = frame_number;
    settings_override = ANDROID_CONTROL_SETTINGS_OVERRIDE_OFF;
  }
  result->result_metadata->Set(ANDROID_CONTROL_SETTINGS_OVERRIDE,
                               &settings_override, 1);
  result->result_metadata->Set(ANDROID_CONTROL_SETTINGS_OVERRIDING_FRAME_NUMBER,
                               (int32_t*)&overriding_frame_number, 1);
  result->result_metadata->Set(ANDROID_CONTROL_AUTOFRAMING, &info.autoframing_,
                               1);
  uint8_t autoframing_state = ANDROID_CONTROL_AUTOFRAMING_STATE_INACTIVE;
  if (info.autoframing_ == ANDROID_CONTROL_AUTOFRAMING_ON) {
    autoframing_state = ANDROID_CONTROL_AUTOFRAMING_STATE_CONVERGED;
  }
  result->result_metadata->Set(ANDROID_CONTROL_AUTOFRAMING_STATE,
                               &autoframing_state, 1);

  int32_t fps_range[] = {info.ae_target_fps_.min_fps,
                         info.ae_target_fps_.max_fps};
  result->result_metadata->Set(ANDROID_CONTROL_AE_TARGET_FPS_RANGE, fps_range,
                               ARRAY_SIZE(fps_range));
  result->result_metadata->Set(ANDROID_FLASH_STATE, &info.flash_state_, 1);
  result->result_metadata->Set(ANDROID_LENS_STATE, &info.lens_state_, 1);

  // Results depending on device capability and features
  if (info.is_backward_compatible_) {
    result->result_metadata->Set(ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER,
                                 &info.ae_trigger_, 1);
    result->result_metadata->Set(ANDROID_CONTROL_AF_TRIGGER, &info.af_trigger_,
                                 1);
    uint8_t vstab_mode = ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_OFF;
    result->result_metadata->Set(ANDROID_CONTROL_VIDEO_STABILIZATION_MODE,
                                 &vstab_mode, 1);
    if (info.exposure_compensation_supported_) {
      result->result_metadata->Set(ANDROID_CONTROL_AE_EXPOSURE_COMPENSATION,
                                   &info.exposure_compensation_, 1);
    }
  }
  if (info.ae_lock_available_ && info.report_ae_lock_) {
    result->result_metadata->Set(ANDROID_CONTROL_AE_LOCK, &info.ae_lock_, 1);
  }
  if (info.awb_lock_available_ && info.report_awb_lock_) {
    result->result_metadata->Set(ANDROID_CONTROL_AWB_LOCK, &info.awb_lock_, 1);
  }
  if (info.scenes_supported_) {
    result->result_metadata->Set(ANDROID_CONTROL_SCENE_MODE, &info.scene_mode_,
                                 1);
  }
  if (info.max_ae_regions_ > 0) {
    result->result_metadata->Set(ANDROID_CONTROL_AE_REGIONS,
                                 info.ae_metering_region_,
                                 ARRAY_SIZE(info.ae_metering_region_));
  }
  if (info.max_awb_regions_ > 0) {
    result->result_metadata->Set(ANDROID_CONTROL_AWB_REGIONS,
                                 info.awb_metering_region_,
                                 ARRAY_SIZE(info.awb_metering_region_));
  }
  if (info.max_af_regions_ > 0) {
    result->result_metadata->Set(ANDROID_CONTROL_AF_REGIONS,
                                 info.af_metering_region_,
                                 ARRAY_SIZE(info.af_metering_region_));
  }
  if (info.report_exposure_time_) {
    result->result_metadata->Set(ANDROID_SENSOR_EXPOSURE_TIME,
                                 &info.sensor_exposure_time_, 1);
  } else {
    result->result_metadata->Erase(ANDROID_SENSOR_EXPOSURE_TIME);
  }
  if (info.report_frame_duration_) {
    result->result_metadata->Set(ANDROID_SENSOR_FRAME_DURATION,
                                 &info.sensor_frame_duration_, 1);
  } else {
    result->result_metadata->Erase(ANDROID_SENSOR_FRAME_DURATION);
  }
  if (info.report_sensitivity_) {
    result->result_metadata->Set(ANDROID_SENSOR_SENSITIVITY,
                                 &info.sensor_sensitivity_, 1);
  } else {
    result->result_metadata->Erase(ANDROID_SENSOR_SENSITIVITY);
  }
  if (info.report_rolling_shutter_skew_) {
    result->result_metadata->Set(
        ANDROID_SENSOR_ROLLING_SHUTTER_SKEW,
        &EmulatedSensor::kSupportedFrameDurationRange[0], 1);
  }
  if (info.report_post_raw_boost_) {
    result->result_metadata->Set(ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST,
                                 &info.post_raw_boost_, 1);
  }
  if (info.report_focus_distance_) {
    result->result_metadata->Set(ANDROID_LENS_FOCUS_DISTANCE,
                                 &info.focus_distance_, 1);
  }
  if (info.report_focus_range_) {
    float focus_range[2] = {};
    focus_range[0] = info.focus_distance_;
    result->result_metadata->Set(ANDROID_LENS_FOCUS_RANGE, focus_range, ARRAY_SIZE(focus_range));
  }
  if (info.report_filter_density_) {
    result->result_metadata->Set(ANDROID_LENS_FILTER_DENSITY,
                                 &info.filter_density_, 1);
  }
  if (info.report_ois_mode_) {
    result->result_metadata->Set(ANDROID_LENS_OPTICAL_STABILIZATION_MODE,
                                 &info.ois_mode_, 1);
  }
  if (info.report_pose_rotation_) {
    result->result_metadata->Set(ANDROID_LENS_POSE_ROTATION, info.pose_rotation_,
                                 ARRAY_SIZE(info.pose_rotation_));
  }
  if (info.report_pose_translation_) {
    result->result_metadata->Set(ANDROID_LENS_POSE_TRANSLATION,
                                 info.pose_translation_,
                                 ARRAY_SIZE(info.pose_translation_));
  }
  if (info.report_intrinsic_calibration_) {
    result->result_metadata->Set(ANDROID_LENS_INTRINSIC_CALIBRATION,
                                 info.intrinsic_calibration_,
                                 ARRAY_SIZE(info.intrinsic_calibration_));
  }
  if (info.report_lens_intrinsics_samples_) {
    result->result_metadata->Set(ANDROID_STATISTICS_LENS_INTRINSIC_SAMPLES,
                                 info.intrinsic_calibration_,
                                 ARRAY_SIZE(info.intrinsic_calibration_));
  }
  if (info.report_distortion_) {
    result->result_metadata->Set(ANDROID_LENS_DISTORTION, info.distortion_,
                                 ARRAY_SIZE(info.distortion_));
  }
  if (info.report_black_level_lock_) {
    result->result_metadata->Set(ANDROID_BLACK_LEVEL_LOCK,
                                 &info.black_level_lock_, 1);
  }
  if (info.report_scene_flicker_) {
    result->result_metadata->Set(ANDROID_STATISTICS_SCENE_FLICKER,
                                 &info.current_scene_flicker_, 1);
  }
  if (info.zoom_ratio_supported_) {
    result->result_metadata->Set(ANDROID_CONTROL_ZOOM_RATIO, &info.zoom_ratio_,
                                 1);
    int32_t* chosen_crop_region = info.scaler_crop_region_default_;
    if (info.sensor_pixel_mode_ == ANDROID_SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION) {
      chosen_crop_region = info.scaler_crop_region_max_resolution_;
    }
    result->result_metadata->Set(ANDROID_SCALER_CROP_REGION, chosen_crop_region,
                                 ARRAY_SIZE(info.scaler_crop_region_default_));
    if (info.report_active_sensor_crop_) {
      int32_t active_crop_region[4];
      // width
      active_crop_region[2] =
          (info.scaler_crop_region_default_[2] / info.zoom_ratio_);
      // height
      active_crop_region[3] =
          (info.scaler_crop_region_default_[3] / info.zoom_ratio_);
      // left
      active_crop_region[0] =
          (info.scaler_crop_region_default_[2] - active_crop_region[2]) / 2;
      // top
      active_crop_region[1] =
          (info.scaler_crop_region_default_[3] - active_crop_region[3]) / 2;
      result->result_metadata->Set(
          ANDROID_LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_SENSOR_CROP_REGION,
          active_crop_region, ARRAY_SIZE(info.scaler_crop_region_default_));
    }
  }
  if (info.report_extended_scene_mode_) {
    result->result_metadata->Set(ANDROID_CONTROL_EXTENDED_SCENE_MODE,
                                 &info.extended_scene_mode_, 1);
  }
  return result;
}

status_t EmulatedRequestState::Initialize(
    std::unique_ptr<EmulatedCameraDeviceInfo> deviceInfo) {
  std::lock_guard<std::mutex> lock(request_state_mutex_);
  device_info_ = std::move(deviceInfo);

  return OK;
}

status_t EmulatedRequestState::GetDefaultRequest(
    RequestTemplate type, std::unique_ptr<HalCameraMetadata>* default_settings) {
  if (default_settings == nullptr) {
    ALOGE("%s default_settings is nullptr", __FUNCTION__);
    return BAD_VALUE;
  }

  std::lock_guard<std::mutex> lock(request_state_mutex_);
  auto idx = static_cast<size_t>(type);
  if (idx >= kTemplateCount) {
    ALOGE("%s: Unexpected request type: %d", __FUNCTION__, type);
    return BAD_VALUE;
  }

  if (device_info_->default_requests_[idx].get() == nullptr) {
    ALOGE("%s: Unsupported request type: %d", __FUNCTION__, type);
    return BAD_VALUE;
  }

  *default_settings = HalCameraMetadata::Clone(
      device_info_->default_requests_[idx]->GetRawCameraMetadata());

  return OK;
}

}  // namespace android
