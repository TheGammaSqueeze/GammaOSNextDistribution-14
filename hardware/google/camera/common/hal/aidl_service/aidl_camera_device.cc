/*
 * Copyright (C) 2022 The Android Open Source Project
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

#define LOG_TAG "GCH_AidlCameraDevice"
//#define LOG_NDEBUG 0
#include "aidl_camera_device.h"

#include <log/log.h>

#include "aidl_camera_device_session.h"
#include "aidl_profiler.h"
#include "aidl_utils.h"

namespace android {
namespace hardware {
namespace camera {
namespace device {
namespace implementation {

namespace aidl_utils = ::android::hardware::camera::implementation::aidl_utils;

using aidl::android::hardware::camera::common::Status;
using ::android::google_camera_hal::HalCameraMetadata;

const std::string AidlCameraDevice::kDeviceVersion = "1.1";

std::shared_ptr<AidlCameraDevice> AidlCameraDevice::Create(
    std::unique_ptr<CameraDevice> google_camera_device) {
  auto device = ndk::SharedRefBase::make<AidlCameraDevice>();
  if (device == nullptr) {
    ALOGE("%s: Cannot create a AidlCameraDevice.", __FUNCTION__);
    return nullptr;
  }

  status_t res = device->Initialize(std::move(google_camera_device));
  if (res != OK) {
    ALOGE("%s: Initializing AidlCameraDevice failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return nullptr;
  }

  return device;
}

status_t AidlCameraDevice::Initialize(
    std::unique_ptr<CameraDevice> google_camera_device) {
  if (google_camera_device == nullptr) {
    ALOGE("%s: google_camera_device is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  camera_id_ = google_camera_device->GetPublicCameraId();
  google_camera_device_ = std::move(google_camera_device);
  aidl_profiler_ = AidlProfiler::Create(camera_id_);
  if (aidl_profiler_ == nullptr) {
    ALOGE("%s: Failed to create AidlProfiler.", __FUNCTION__);
    return UNKNOWN_ERROR;
  }
  return OK;
}

ScopedAStatus AidlCameraDevice::getResourceCost(
    CameraResourceCost* resource_cost) {
  google_camera_hal::CameraResourceCost hal_cost;
  if (resource_cost == nullptr) {
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::ILLEGAL_ARGUMENT));
  }
  status_t res = google_camera_device_->GetResourceCost(&hal_cost);
  if (res != OK) {
    ALOGE("%s: Getting resource cost failed for camera %u: %s(%d)",
          __FUNCTION__, camera_id_, strerror(-res), res);
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::INTERNAL_ERROR));
  }

  res = aidl_utils::ConvertToAidlResourceCost(hal_cost, resource_cost);
  if (res != OK) {
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::INTERNAL_ERROR));
  }

  return ScopedAStatus::ok();
}

ScopedAStatus AidlCameraDevice::getCameraCharacteristics(
    CameraMetadata* characteristics_ret) {
  if (characteristics_ret == nullptr) {
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::ILLEGAL_ARGUMENT));
  }
  characteristics_ret->metadata.clear();
  std::unique_ptr<HalCameraMetadata> characteristics;
  status_t res =
      google_camera_device_->GetCameraCharacteristics(&characteristics);
  if (res != OK) {
    ALOGE("%s: Getting camera characteristics for camera %u failed: %s(%d)",
          __FUNCTION__, camera_id_, strerror(-res), res);
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::INTERNAL_ERROR));
  }

  if (characteristics == nullptr) {
    ALOGE("%s: Camera characteristics for camera %u is nullptr.", __FUNCTION__,
          camera_id_);
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::INTERNAL_ERROR));
  }

  uint32_t metadata_size = characteristics->GetCameraMetadataSize();
  uint8_t* chars_p = (uint8_t*)characteristics->GetRawCameraMetadata();
  characteristics_ret->metadata.assign(chars_p, chars_p + metadata_size);

  return ScopedAStatus::ok();
}

ScopedAStatus AidlCameraDevice::setTorchMode(bool on) {
  google_camera_hal::TorchMode hal_torch_mode;
  hal_torch_mode = on ? google_camera_hal::TorchMode::kOn
                      : google_camera_hal::TorchMode::kOff;
  auto res = google_camera_device_->SetTorchMode(hal_torch_mode);
  return aidl_utils::ConvertToAidlReturn(res);
}

ScopedAStatus AidlCameraDevice::turnOnTorchWithStrengthLevel(
    int32_t torch_strength) {
  status_t res =
      google_camera_device_->TurnOnTorchWithStrengthLevel(torch_strength);
  return aidl_utils::ConvertToAidlReturn(res);
}

ScopedAStatus AidlCameraDevice::getTorchStrengthLevel(int32_t* strength_level) {
  if (strength_level == nullptr) {
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::ILLEGAL_ARGUMENT));
  }
  *strength_level = 0;
  int32_t torch_strength;
  status_t res = google_camera_device_->GetTorchStrengthLevel(torch_strength);
  if (res != OK) {
    ALOGE(
        "%s: Getting camera flash unit torch strength level for camera %u "
        "failed: %s(%d)",
        __FUNCTION__, camera_id_, strerror(-res), res);
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::INTERNAL_ERROR));
  }
  *strength_level = torch_strength;
  return ScopedAStatus::ok();
}

ScopedAStatus AidlCameraDevice::constructDefaultRequestSettings(
    RequestTemplate type, CameraMetadata* request_settings) {
  if (request_settings == nullptr) {
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::ILLEGAL_ARGUMENT));
  }

  request_settings->metadata.clear();

  google_camera_hal::RequestTemplate hal_type;
  status_t res = aidl_utils::ConvertToHalTemplateType(type, &hal_type);
  if (res != OK) {
    return ndk::ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::ILLEGAL_ARGUMENT));
  }

  std::unique_ptr<HalCameraMetadata> settings;
  res = google_camera_device_->ConstructDefaultRequestSettings(hal_type,
                                                               &settings);
  if (res != OK) {
    ALOGE("%s: Getting camera characteristics for camera %u failed: %s(%d)",
          __FUNCTION__, camera_id_, strerror(-res), res);
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::INTERNAL_ERROR));
  }
  if (settings == nullptr) {
    ALOGE("%s: Default request settings for camera %u is nullptr.",
          __FUNCTION__, camera_id_);
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::INTERNAL_ERROR));
  }

  uint32_t metadata_size = settings->GetCameraMetadataSize();
  uint8_t* chars_p = (uint8_t*)settings->GetRawCameraMetadata();
  request_settings->metadata.assign(chars_p, chars_p + metadata_size);

  return ScopedAStatus::ok();
}

ScopedAStatus AidlCameraDevice::isStreamCombinationWithSettingsSupported(
    const StreamConfiguration& streamConfiguration, bool* supported) {
  return isStreamCombinationSupportedInternal(streamConfiguration, supported,
                                              /*checkSettings*/ true);
}

ScopedAStatus AidlCameraDevice::getSessionCharacteristics(
    const StreamConfiguration& session_config,
    CameraMetadata* characteristics_ret) {
  google_camera_hal::StreamConfiguration stream_config;
  status_t res =
      aidl_utils::ConvertToHalStreamConfig(session_config, &stream_config);
  if (res != OK) {
    ALOGE("%s: ConvertToHalStreamConfig fail", __FUNCTION__);
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::INTERNAL_ERROR));
  }

  if (characteristics_ret == nullptr) {
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::ILLEGAL_ARGUMENT));
  }
  characteristics_ret->metadata.clear();
  std::unique_ptr<HalCameraMetadata> session_characteristics;
  res =
      google_camera_device_->GetSessionCharacteristics(&session_characteristics);
  if (res != OK) {
    ALOGE("%s: Getting session characteristics for camera %u failed: %s(%d)",
          __FUNCTION__, camera_id_, strerror(-res), res);
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::INTERNAL_ERROR));
  }

  if (session_characteristics == nullptr) {
    ALOGE("%s: Session characteristics for camera %u is nullptr.", __FUNCTION__,
          camera_id_);
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::INTERNAL_ERROR));
  }

  uint32_t metadata_size = session_characteristics->GetCameraMetadataSize();
  uint8_t* chars_p = (uint8_t*)session_characteristics->GetRawCameraMetadata();
  characteristics_ret->metadata.assign(chars_p, chars_p + metadata_size);

  return ScopedAStatus::ok();
}

ScopedAStatus AidlCameraDevice::getPhysicalCameraCharacteristics(
    const std::string& physicalCameraId, CameraMetadata* characteristics_ret) {
  if (characteristics_ret == nullptr) {
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::ILLEGAL_ARGUMENT));
  }
  characteristics_ret->metadata.clear();
  std::unique_ptr<HalCameraMetadata> physical_characteristics;
  uint32_t physical_camera_id = atoi(physicalCameraId.c_str());
  status_t res = google_camera_device_->GetPhysicalCameraCharacteristics(
      physical_camera_id, &physical_characteristics);
  if (res != OK) {
    ALOGE("%s: Getting physical characteristics for camera %u failed: %s(%d)",
          __FUNCTION__, camera_id_, strerror(-res), res);
    return aidl_utils::ConvertToAidlReturn(res);
  }

  if (physical_characteristics == nullptr) {
    ALOGE("%s: Physical characteristics for camera %u is nullptr.",
          __FUNCTION__, physical_camera_id);
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::INTERNAL_ERROR));
  }

  uint32_t metadata_size = physical_characteristics->GetCameraMetadataSize();
  uint8_t* physical_characteristics_p =
      (uint8_t*)physical_characteristics->GetRawCameraMetadata();
  characteristics_ret->metadata.assign(
      physical_characteristics_p, physical_characteristics_p + metadata_size);
  return ScopedAStatus::ok();
}

ScopedAStatus AidlCameraDevice::open(
    const std::shared_ptr<ICameraDeviceCallback>& callback,
    std::shared_ptr<ICameraDeviceSession>* session_ret) {
  if (session_ret == nullptr) {
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::ILLEGAL_ARGUMENT));
  }
  *session_ret = nullptr;
  auto profiler = aidl_profiler_->MakeScopedProfiler(
      AidlProfiler::ScopedType::kOpen,
      google_camera_device_->GetProfiler(camera_id_,
                                         aidl_profiler_->GetLatencyFlag()),
      google_camera_device_->GetProfiler(camera_id_,
                                         aidl_profiler_->GetFpsFlag()));

  std::unique_ptr<google_camera_hal::CameraDeviceSession> session;
  status_t res = google_camera_device_->CreateCameraDeviceSession(&session);
  if (res != OK || session == nullptr) {
    ALOGE("%s: Creating CameraDeviceSession failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return aidl_utils::ConvertToAidlReturn(res);
  }

  auto aidl_session = AidlCameraDeviceSession::Create(
      callback, std::move(session), aidl_profiler_);
  if (aidl_session == nullptr) {
    ALOGE("%s: Creating AidlCameraDeviceSession failed.", __FUNCTION__);
    return aidl_utils::ConvertToAidlReturn(res);
  }
  *session_ret = aidl_session;
  return ScopedAStatus::ok();
}

ScopedAStatus AidlCameraDevice::openInjectionSession(
    const std::shared_ptr<ICameraDeviceCallback>&,
    std::shared_ptr<ICameraInjectionSession>* session) {
  if (session == nullptr) {
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::ILLEGAL_ARGUMENT));
  }
  *session = nullptr;
  return ScopedAStatus::fromServiceSpecificError(
      static_cast<int32_t>(Status::OPERATION_NOT_SUPPORTED));
}

binder_status_t AidlCameraDevice::dump(int fd, const char** /*args*/,
                                       uint32_t /*numArgs*/) {
  google_camera_device_->DumpState(fd);
  return OK;
}

ScopedAStatus AidlCameraDevice::isStreamCombinationSupported(
    const StreamConfiguration& streams, bool* supported) {
  return isStreamCombinationSupportedInternal(streams, supported,
                                              /*checkSettings*/ false);
}

ScopedAStatus AidlCameraDevice::isStreamCombinationSupportedInternal(
    const StreamConfiguration& streams, bool* supported, bool checkSettings) {
  if (supported == nullptr) {
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::ILLEGAL_ARGUMENT));
  }
  *supported = false;
  google_camera_hal::StreamConfiguration stream_config;
  status_t res = aidl_utils::ConvertToHalStreamConfig(streams, &stream_config);
  if (res != OK) {
    ALOGE("%s: ConvertToHalStreamConfig fail", __FUNCTION__);
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::INTERNAL_ERROR));
  }
  *supported = google_camera_device_->IsStreamCombinationSupported(
      stream_config, checkSettings);
  return ScopedAStatus::ok();
}

}  // namespace implementation
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android
